package com.gatcha.log.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatcha.log.data.Account
import com.gatcha.log.data.AuthManager
import com.gatcha.log.data.SignInOutcome
import com.gatcha.log.data.CloudSync
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GachaRecord
import com.gatcha.log.data.GachaReport
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.GachaStats
import com.gatcha.log.data.GachaDashboard
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.work.AutoCheckInRunner
import com.gatcha.log.data.work.NativeScheduler
import com.gatcha.log.data.work.ScheduledAlerts
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.PityState
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.MonthlyLedger
import com.gatcha.log.data.Spending
import com.gatcha.log.data.Subscription
import com.gatcha.log.data.UserProfile
import com.gatcha.log.data.api.EnkaApi
import com.gatcha.log.data.api.NanokaApi
import com.gatcha.log.data.api.WeaponRefinement
import com.gatcha.log.data.api.EnkaResult
import com.gatcha.log.data.api.EnneadApi
import com.gatcha.log.data.api.NewsApi
import com.gatcha.log.data.api.NewsArticle
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.data.api.CodeResult
import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.data.api.GiftCodeApi
import com.gatcha.log.data.api.UpdateChecker
import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.util.currentTimeMillis
import com.gatcha.log.util.won
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 선물코드 교환 UI 상태 */
sealed interface RedeemState {
    data object Idle : RedeemState
    data object Loading : RedeemState
    data class Done(val success: Boolean, val message: String) : RedeemState
}

/**
 * 모든 화면이 공유하는 단일 ViewModel.
 * 로그인 계정(AuthManager)별로 분리된 로컬 저장소(GatchaRepository)와 동기화된다.
 */
class SpendingViewModel : ViewModel() {

    private val authManager = AuthManager()
    /** 현재 로그인 계정 (게스트 = 비로그인 로컬) */
    val account: StateFlow<Account> = authManager.account

    // 계정별로 분리되는 저장소. 계정 전환 시 교체된다.
    private var repo: GatchaRepository = GatchaRepository(account.value.id)

    // ----------------------------------------------------------------- 상태 (계정별 로드)
    private val _spendings = MutableStateFlow<List<Spending>>(emptyList())
    val spendings: StateFlow<List<Spending>> = _spendings.asStateFlow()

    private val _budget = MutableStateFlow(0L) // 0 = 미설정
    val budget: StateFlow<Long> = _budget.asStateFlow()

    /** 게임별 월 한도(gameKey → 금액). 한도 없는 게임은 키 없음. 전체 예산[budget]과 별개. */
    private val _gameBudgets = MutableStateFlow<Map<String, Long>>(emptyMap())
    val gameBudgets: StateFlow<Map<String, Long>> = _gameBudgets.asStateFlow()

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _hoyolabConfig = MutableStateFlow(HoyolabConfig())
    val hoyolabConfig: StateFlow<HoyolabConfig> = _hoyolabConfig.asStateFlow()

    // 네이티브 설정(자동 출석체크 등) — 기기 단위, 계정 무관
    private val appSettings = AppSettings()

    /** HoYoLAB 토큰 만료 감지 플래그. 자동 출석 AUTH 실패 시 set, 재연동·재성공 시 clear. */
    private val _hoyoTokenExpired = MutableStateFlow(appSettings.hoyoTokenExpired)
    val hoyoTokenExpired: StateFlow<Boolean> = _hoyoTokenExpired.asStateFlow()
    /** 워커가 백그라운드에서 플래그를 바꿨을 수 있어 화면 진입 시 다시 읽는다. */
    fun refreshHoyoTokenExpired() { _hoyoTokenExpired.value = appSettings.hoyoTokenExpired }

    /** 홈 배너 CTA → 마이페이지·설정·HoYoLAB 연동까지 자동 진입시키기 위한 1회성 신호. */
    private val _pendingOpenHoyolabLink = MutableStateFlow(false)
    val pendingOpenHoyolabLink: StateFlow<Boolean> = _pendingOpenHoyolabLink.asStateFlow()
    fun requestOpenHoyolabLink() { _pendingOpenHoyolabLink.value = true }
    fun consumePendingOpenHoyolabLink() { _pendingOpenHoyolabLink.value = false }

    /** 오늘 할 일 → 게임 정보 탭 진입 시 해당 섹션으로 스크롤 앵커링하기 위한 1회성 신호. */
    private val _pendingGameInfoAnchor = MutableStateFlow<GameInfoAnchor?>(null)
    val pendingGameInfoAnchor: StateFlow<GameInfoAnchor?> = _pendingGameInfoAnchor.asStateFlow()
    fun requestGameInfoAnchor(anchor: GameInfoAnchor) { _pendingGameInfoAnchor.value = anchor }
    fun consumeGameInfoAnchor() { _pendingGameInfoAnchor.value = null }

    // ── 알림 딥링크 ─────────────────────────────────────────────────────────
    // 알림을 탭하면 앱만 열리고 끝이라 "무슨 공지인지" 다시 찾아 들어가야 했다.
    // 알림에 실어 보낸 링크를 여기로 넘기면 탭 전환 + 상세 진입까지 이어진다.

    /** 이동해야 할 탭 인덱스(0홈·1지출·2게임정보·3마이). UI 가 소비 후 [consumePendingTab]. */
    private val _pendingTab = MutableStateFlow<Int?>(null)
    val pendingTab: StateFlow<Int?> = _pendingTab.asStateFlow()
    fun consumePendingTab() { _pendingTab.value = null }

    /** 열어야 할 공지 id. 목록이 아직 없으면 UI 가 로드를 기다렸다가 연다. */
    private val _pendingNewsId = MutableStateFlow<String?>(null)
    val pendingNewsId: StateFlow<String?> = _pendingNewsId.asStateFlow()
    fun consumePendingNews() { _pendingNewsId.value = null }

    /**
     * 알림 페이로드의 딥링크 처리. 형식은 `"news:<공지 id>"` 처럼 `종류:인자`.
     * 모르는 형식이면 무시한다(구버전 알림이 남아 있어도 안전).
     */
    fun handleNotificationLink(link: String) {
        val kind = link.substringBefore(':')
        val arg = link.substringAfter(':', "")
        when (kind) {
            "news" -> {
                if (arg.isBlank()) return
                _pendingNewsId.value = arg
                _pendingGameInfoAnchor.value = GameInfoAnchor.NEWS
                _pendingTab.value = 2
                refreshGameInfo()   // 목록이 비어 있으면 채운다 — 상세를 열려면 원본 항목이 필요하다
            }
        }
    }
    private val _autoCheckIn = MutableStateFlow(appSettings.autoCheckIn)
    val autoCheckIn: StateFlow<Boolean> = _autoCheckIn.asStateFlow()
    fun setAutoCheckIn(enabled: Boolean) {
        appSettings.autoCheckIn = enabled
        _autoCheckIn.value = enabled
        NativeScheduler.apply()
        if (!enabled) {
            emitStatus("자동 출석체크를 껐어요")
            return
        }
        emitStatus("자동 출석체크를 켰어요 — 지금 한 번 시도할게요")
        // 결과를 토스트로 즉시 피드백(완료/이미 완료/재연동 필요 등). 워커도 같은 Runner 를 쓰지만
        // 여기선 알림 중복을 피하려고 postFailureNotification=false 로 끄고 토스트만 띄운다.
        viewModelScope.launch {
            val outcome = AutoCheckInRunner.run(
                settings = appSettings,
                repo = repo,
                cfg = repo.loadHoyolab(),
                postFailureNotification = false,
            )
            emitStatus(outcome?.toToastMessage() ?: "HoYoLAB 연동이 안 돼 있어요")
        }
    }

    // 로컬 알림 토글 (예산·출석·재화)
    private val _notifyBudget = MutableStateFlow(appSettings.notifyBudget)
    val notifyBudget: StateFlow<Boolean> = _notifyBudget.asStateFlow()
    private val _notifyAttendance = MutableStateFlow(appSettings.notifyAttendance)
    val notifyAttendance: StateFlow<Boolean> = _notifyAttendance.asStateFlow()
    private val _notifyResin = MutableStateFlow(appSettings.notifyResin)
    val notifyResin: StateFlow<Boolean> = _notifyResin.asStateFlow()
    private val _notifyPickup = MutableStateFlow(appSettings.notifyPickup)
    val notifyPickup: StateFlow<Boolean> = _notifyPickup.asStateFlow()
    private val _notifySubscription = MutableStateFlow(appSettings.notifySubscription)
    val notifySubscription: StateFlow<Boolean> = _notifySubscription.asStateFlow()
    private val _notifyNews = MutableStateFlow(appSettings.notifyNews)
    val notifyNews: StateFlow<Boolean> = _notifyNews.asStateFlow()
    private val _notifyCombat = MutableStateFlow(appSettings.notifyCombat)
    val notifyCombat: StateFlow<Boolean> = _notifyCombat.asStateFlow()

    // 방해금지(DnD) — 조용한 시간대 알림 보류
    private val _notifyDndEnabled = MutableStateFlow(appSettings.notifyDndEnabled)
    val notifyDndEnabled: StateFlow<Boolean> = _notifyDndEnabled.asStateFlow()
    private val _notifyDndStartHour = MutableStateFlow(appSettings.notifyDndStartHour)
    val notifyDndStartHour: StateFlow<Int> = _notifyDndStartHour.asStateFlow()
    private val _notifyDndEndHour = MutableStateFlow(appSettings.notifyDndEndHour)
    val notifyDndEndHour: StateFlow<Int> = _notifyDndEndHour.asStateFlow()

    // 데일리 요약 — 정한 시각에 1건 통합
    private val _notifyDailySummary = MutableStateFlow(appSettings.notifyDailySummary)
    val notifyDailySummary: StateFlow<Boolean> = _notifyDailySummary.asStateFlow()
    private val _notifyDailySummaryHour = MutableStateFlow(appSettings.notifyDailySummaryHour)
    val notifyDailySummaryHour: StateFlow<Int> = _notifyDailySummaryHour.asStateFlow()

    // 과소비 리플렉션 넛지(지출 추가 시점) — 토글 + 평소치 기준액
    private val _nudgeOverspend = MutableStateFlow(appSettings.nudgeOverspend)
    val nudgeOverspend: StateFlow<Boolean> = _nudgeOverspend.asStateFlow()
    private val _nudgeThreshold = MutableStateFlow(appSettings.nudgeThreshold)
    val nudgeThreshold: StateFlow<Long> = _nudgeThreshold.asStateFlow()
    fun setNudgeOverspend(v: Boolean) { appSettings.nudgeOverspend = v; _nudgeOverspend.value = v }
    fun setNudgeThreshold(v: Long) { appSettings.nudgeThreshold = v; _nudgeThreshold.value = v }

    // 지출 내역 컴팩트(한 줄) 표시 토글. 기본 false(기존).
    private val _spendingCompact = MutableStateFlow(appSettings.spendingCompact)
    val spendingCompact: StateFlow<Boolean> = _spendingCompact.asStateFlow()
    fun setSpendingCompact(v: Boolean) { appSettings.spendingCompact = v; _spendingCompact.value = v }

    /** 홈 히어로 글로우 애니메이션 사용 여부 — 끄면 그라데이션은 그대로, 움직이는 글로우만 사라진다. */
    private val _heroGlow = MutableStateFlow(appSettings.heroGlow)
    val heroGlow: StateFlow<Boolean> = _heroGlow.asStateFlow()
    fun setHeroGlow(v: Boolean) { appSettings.heroGlow = v; _heroGlow.value = v }

    /**
     * N6 과소비 넛지 판정 — 지출 저장 직전 호출. 경고가 필요하면 메시지, 아니면 null.
     * 우선순위: 게임별 한도 초과 예상 → 전체 예산 초과 예상 → 단건 큰 금액(평소치 초과).
     * 수정 시엔 같은 달의 기존 금액을 빼서 순증분으로 예측(이중 합산 방지).
     */
    fun overspendNudge(game: Game, amount: Long, editingId: String? = null): String? {
        if (!appSettings.nudgeOverspend || amount <= 0) return null
        val editRecord = editingId?.let { id -> _spendings.value.firstOrNull { it.id == id } }
        // 이번 달에 이미 집계된 기존 금액만 차감(다른 달 기록 수정 시 이중차감 방지).
        val prevInMonth = editRecord != null && DateUtil.isSameMonth(editRecord.dateMillis, currentYear, currentMonth)
        val prev = if (prevInMonth) editRecord.amount else 0L
        val prevSameGame = prevInMonth && editRecord.gameName == game.displayName

        // 이미 계산해 둔 파생값을 쓴다 — 예전엔 여기서 지출 전체를 두 번 더 훑었다(저장 버튼 경로).
        val gameNow = _currentMonthTotalsByGame.value[game.key] ?: 0L
        val monthNow = _currentMonthTotal.value
        val projectedGame = gameNow - (if (prevSameGame) prev else 0L) + amount
        val projectedMonth = monthNow - prev + amount

        val gameLimit = _gameBudgets.value[game.key] ?: 0L
        val overall = _budget.value
        val threshold = appSettings.nudgeThreshold
        return when {
            gameLimit > 0 && projectedGame > gameLimit ->
                "${game.shortName}에 이번 달 이미 ${won(gameNow)} 썼어요.\n추가하면 한도 ${won(gameLimit)}을 넘어요."
            overall > 0 && projectedMonth > overall ->
                "이번 달 이미 ${won(monthNow)} 썼어요.\n추가하면 예산 ${won(overall)}을 넘어요."
            threshold > 0 && amount >= threshold ->
                "${won(amount)}은 평소보다 큰 지출이에요.\n정말 추가할까요?"
            else -> null
        }
    }

    fun setNotifyBudget(v: Boolean) { appSettings.notifyBudget = v; _notifyBudget.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyAttendance(v: Boolean) { appSettings.notifyAttendance = v; _notifyAttendance.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyResin(v: Boolean) { appSettings.notifyResin = v; _notifyResin.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyPickup(v: Boolean) { appSettings.notifyPickup = v; _notifyPickup.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifySubscription(v: Boolean) { appSettings.notifySubscription = v; _notifySubscription.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyNews(v: Boolean) { appSettings.notifyNews = v; _notifyNews.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyCombat(v: Boolean) { appSettings.notifyCombat = v; _notifyCombat.value = v; applyNativeAfterNotifyChange(v) }

    fun setNotifyDndEnabled(v: Boolean) { appSettings.notifyDndEnabled = v; _notifyDndEnabled.value = v; NativeScheduler.apply() }
    fun setNotifyDndStartHour(v: Int) { appSettings.notifyDndStartHour = v; _notifyDndStartHour.value = appSettings.notifyDndStartHour }
    fun setNotifyDndEndHour(v: Int) { appSettings.notifyDndEndHour = v; _notifyDndEndHour.value = appSettings.notifyDndEndHour }
    fun setNotifyDailySummary(v: Boolean) { appSettings.notifyDailySummary = v; _notifyDailySummary.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyDailySummaryHour(v: Int) { appSettings.notifyDailySummaryHour = v; _notifyDailySummaryHour.value = appSettings.notifyDailySummaryHour }

    private fun applyNativeAfterNotifyChange(enabled: Boolean) {
        NativeScheduler.apply()
        rescheduleTimedAlerts()
        if (enabled) NativeScheduler.runNow()
    }

    /**
     * 확정 시각 알림(픽업·시즌 마감·정기결제·재화 가득참·데일리 요약) 사전 예약 갱신.
     * iOS 는 이 예약 덕분에 앱을 안 열어도 정시에 알림이 오고, Android 는 주기 워커가 이미
     * 커버하므로 no-op 이다([AlertScheduler]).
     *
     * 예약 등록이 끝날 때까지 중단하는 suspend 라 스코프에 띄운다(호출부는 전부 non-suspend).
     * 여기는 앱이 떠 있는 포그라운드라 결과를 기다릴 필요가 없다 — 기다려야 하는 쪽은 iOS BGTask 다.
     * 로컬 캐시 4종을 읽어 목록을 만드므로 IO 로 보낸다.
     */
    private fun rescheduleTimedAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ScheduledAlerts.reschedule(appSettings, repo) }
        }
    }

    /**
     * 받아온 실시간 노트를 숙제 관측 기록에 반영하고 완주율을 다시 계산한다.
     * HoYoLAB 은 '지금 상태'만 주므로, 노트를 받는 이 순간이 유일한 관측 기회다.
     */
    private fun recordTaskProgress(notes: List<LiveNote>) {
        runCatching {
            // 기록 자체는 백그라운드 워커와 같은 진입점을 쓴다([TaskCompletion.recordAll]).
            // 바뀐 게 없으면 null 이 오고, 그때는 통계도 다시 만들 필요가 없다.
            TaskCompletion.recordAll(repo, notes)?.let { _taskStats.value = TaskCompletion.allStats(it) }
        }
    }

    /** 저장된 기록으로 완주율 복원(앱 시작 — 노트를 받기 전에도 지난 통계는 보여준다). */
    private fun loadTaskStats() {
        runCatching { _taskStats.value = TaskCompletion.allStats(repo.loadTaskLogs()) }
    }

    /** 직전 포그라운드 시점의 호요 기준 날짜 키 — 날짜가 넘어갔는지 판정용. */
    private var lastForegroundDayKey = ""

    /** 실시간 노트(행동력)를 마지막으로 받은 시각. 0 = 받은 적 없음. */
    private var lastLiveNoteAt = 0L

    /** 클라우드 스냅샷을 마지막으로 당겨온 시각. */
    private var lastCloudPullAt = 0L

    /**
     * 앱이 백그라운드로 내려갈 때.
     *
     * 예전엔 내려간 시각을 적어 두고 '얼마나 자리를 비웠는가'로 갱신을 갈랐는데, 이제
     * [onAppForeground] 가 자료 나이로 판단하므로 적어 둘 게 없다. 호출부(양 플랫폼 생명주기)는
     * 그대로 두고 여기만 비운다 — 다시 필요해질 자리다.
     */
    fun onAppBackground() = Unit

    /**
     * 앱이 포그라운드로 돌아왔을 때 ① 화면 데이터를 최신화하고 ② 밀린 알림을 1회 점검한다.
     *
     * ## ① 갱신 판단은 '자료 나이'로 한다 — 자리를 비운 시간이 아니라
     *
     * 예전에는 백그라운드 **체류 시간**으로 갈랐다(30분 이상이면 전체 갱신, 그 아래면 클라우드만).
     * 구멍이 둘 있었다.
     *
     * - **앱을 켜 둔 채 오래 쓰면 아무것도 안 늙는다.** 지출 탭에서 한 시간을 보내다 1분 내려갔다
     *   오면 체류 시간은 1분이라 갱신이 안 걸린다. 정작 행동력은 한 시간 묵은 값이다.
     * - **자료마다 상하는 속도가 다른데 한 문턱으로 묶었다.** 행동력은 분 단위로 차오르고
     *   캘린더·공지는 하루 단위로 바뀐다. 30분은 전자에겐 너무 길고 후자에겐 짧다.
     *
     * 그래서 **각 자료를 마지막으로 받은 시각**을 보고 늙은 것만 다시 받는다.
     *
     * | 자료 | 최대 나이 |
     * |---|---|
     * | 행동력(실시간 노트) | [LIVE_NOTE_MAX_AGE_MS] |
     * | 캘린더·공지·원장·전투 | [GAME_INFO_MAX_AGE_MS] |
     * | 내 캐릭터(Enka) | 조회 함수 내부 TTL |
     * | 클라우드 스냅샷 | [CLOUD_MAX_AGE_MS] |
     *
     * 날짜가 넘어갔으면 나이와 무관하게 전부 다시 받는다 — 출석·일일 숙제가 통째로 리셋된다.
     * 탭·스크롤 위치는 건드리지 않고 값만 조용히 갈아끼운다(로딩 게이트·얼럿 없음).
     *
     * ## ② 알림 점검
     *
     * 주기 작업만으로는 구멍이 크다. iOS BGAppRefreshTask 는 실행 시점이 OS 재량이고 앱이 강제
     * 종료돼 있으면 아예 안 돌며, Android 도 Doze 에서 늦어진다. 그래서 알림이 사실상 '토글을 켜는
     * 순간'에만 오는 것처럼 보였다. 앱을 여는 순간을 보조 트리거로 쓰되, 전환할 때마다 HoYoLAB 을
     * 두드리면 안 되므로 [FOREGROUND_CHECK_MIN_INTERVAL_MS] 간격을 둔다.
     */
    fun onAppForeground() {
        DateUtil.refreshTimeZone()    // 캐시된 로컬 타임존 갱신(여행·자동 시간대 변경) — 알림 조건과 무관하게 항상
        recomputeSpendingDerived()    // 앱을 켜 둔 채 달이 바뀌면 지출은 그대로여서 '이번 달'이 안 갱신된다

        val now = currentTimeMillis()
        val today = todayKey()
        // 첫 호출 = 콜드 스타트. 방금 init 이 로드했고 화면 진입이 곧 조회를 건다 — 여기서 또
        // 부르면 같은 요청이 두 벌 나가고, 오프라인일 때 화면 쪽이 띄울 안내까지 silent 로 삼킨다.
        val firstForeground = lastForegroundDayKey.isEmpty()
        val dayRolled = !firstForeground && lastForegroundDayKey != today
        lastForegroundDayKey = today

        if (!firstForeground) {
            if (dayRolled) {
                // 출석·스트릭은 [loadAll] 때 굳은 값이라 자정을 넘겨도 어제 상태로 남는다.
                _attendanceToday.value = attendanceMap[today] ?: emptySet()
                _attendanceStreak.value = computeAttendanceStreak()
            }
            // 게임 정보 전체(캘린더·공지·원장·전투). 노트도 이 안에서 함께 받으므로 아래는 건너뛴다.
            if (dayRolled || now - lastGameInfoLoadAt >= GAME_INFO_MAX_AGE_MS) {
                refreshGameInfo(force = true, silent = true)
            } else if (now - lastLiveNoteAt >= LIVE_NOTE_MAX_AGE_MS) {
                refreshLiveNotesQuiet()
            }
            // 내 캐릭터 — 호출은 매번 하되 실제 조회 여부는 내부 TTL·진행 중 판정이 가른다.
            // 예전엔 복귀 갱신에 아예 빠져 있어, 화면이 떠 있는 동안에는 탭을 새로 들어가지 않는 한
            // 영영 낡은 로스터가 보였다.
            autoLoadEnkaSection(ENKA_GAMES)
            if (now - lastCloudPullAt >= CLOUD_MAX_AGE_MS) cloudSyncQuiet()
        }

        if (!appSettings.needsPeriodicWork()) return
        if (now - appSettings.lastForegroundCheckMillis < FOREGROUND_CHECK_MIN_INTERVAL_MS) return
        appSettings.lastForegroundCheckMillis = now
        NativeScheduler.apply()   // 예약이 끊겨 있었다면 여기서 되살린다
        NativeScheduler.runNow()
    }

    /**
     * **행동력(실시간 노트)만** 다시 받는다 — HoYoLAB 3건.
     *
     * 전체 갱신([refreshGameInfo])은 캘린더·공지까지 20여 건을 부르는데 그것들은 몇 분 만에
     * 바뀌지 않는다. 반대로 행동력은 계속 차오르므로 앱을 다시 열 때마다 맞아야 한다.
     * 인디케이터는 켜지 않는다 — 사용자가 부른 갱신이 아니다.
     */
    private fun refreshLiveNotesQuiet() {
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) return
        if (_gameInfoRefreshing) return   // 전체 갱신이 이미 돌고 있으면 같은 요청을 두 번 쏘지 않는다
        val uids = mapOf(
            "genshin" to cfg.genshinUid,
            "hsr" to cfg.hsrUid,
            "zzz" to cfg.zzzUid,
        ).filterValues { it.isNotBlank() }
        if (uids.isEmpty()) return
        viewModelScope.launch {
            if (!NetworkMonitor.isOnline()) return@launch
            val notes = coroutineScope {
                uids.map { (key, uid) ->
                    async(Dispatchers.IO) { HoyolabApi.getLiveNote(cfg.ltuid, cfg.ltoken, key, uid).note }
                }.awaitAll()
            }.filterNotNull()
            if (notes.isEmpty()) return@launch
            lastLiveNoteAt = currentTimeMillis()
            _liveNotes.value = mergeByGame(_liveNotes.value, notes, notes.map { it.game }.toSet()) { it.game }
                .sortedByGameOrder { it.game }
            withContext(Dispatchers.IO) { runCatching { repo.saveLiveNotes(_liveNotes.value) } }
            recordTaskProgress(notes)
            rescheduleTimedAlerts()   // 행동력이 바뀌면 완충 알림 예약 시각도 바뀐다
        }
    }

    /**
     * 클라우드 스냅샷만 조용히 다시 당겨온다(화면 전환·로딩 게이트·얼럿 없음).
     *
     * [onAppForeground] 가 [CLOUD_MAX_AGE_MS] 를 넘겼을 때 부른다.
     * 병합은 id 기준 합집합이라 여러 번 돌아도 중복이 생기지 않는다.
     */
    private fun cloudSyncQuiet() {
        if (cloudConfigured && CloudSync.currentUid() != null) {
            lastCloudPullAt = currentTimeMillis()
            // ⚠️ **IO 로 띄운다.** 이 경로는 클라우드 스냅샷 JSON 파싱([GatchaRepository.importSnapshotJson])과
            // 저장소 20여 키를 다시 읽는 [loadAll] 을 포함한다. viewModelScope 기본값(Main)에 두면
            // 그 전부가 UI 스레드에서 돌아, 앱으로 돌아온 직후 화면이 눈에 띄게 멎었다 — 지출이
            // 많을수록 길어진다. 상태(StateFlow) 대입은 어느 스레드에서 해도 되고, 구독하는 쪽이
            // 각자 메인으로 올린다(Compose collectAsStateWithLifecycle · iOS bind 는 @MainActor).
            viewModelScope.launch(Dispatchers.IO) { cloudSyncPullOrSeed(quiet = true) }
        }
    }

    private val _accentIndex = MutableStateFlow(0)
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    private var attendanceMap: Map<String, Set<String>> = emptyMap()
    private val _attendanceToday = MutableStateFlow<Set<String>>(emptySet())
    val attendanceToday: StateFlow<Set<String>> = _attendanceToday.asStateFlow()

    /** 날짜별 출석 이력(dayKey "yyyy-MM-dd" → 출석한 게임키 집합) — 7일 스트립·월간 달력용. */
    private val _attendanceHistory = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val attendanceHistory: StateFlow<Map<String, Set<String>>> = _attendanceHistory.asStateFlow()

    /** 연속 출석 일수(오늘 미출석이면 어제 기준으로 유지). */
    private val _attendanceStreak = MutableStateFlow(0)
    val attendanceStreak: StateFlow<Int> = _attendanceStreak.asStateFlow()

    private fun computeAttendanceStreak(): Int {
        var offset = 0
        if (attendanceMap[DateUtil.hoyoDayKeyAgo(0)].isNullOrEmpty()) offset = 1
        var streak = 0
        while (!attendanceMap[DateUtil.hoyoDayKeyAgo(offset + streak)].isNullOrEmpty()) streak++
        return streak
    }

    /** 현재 repo(계정)의 모든 데이터를 상태로 로드. */
    private fun loadAll() {
        _spendings.value = repo.loadSpendings()
        _budget.value = repo.loadBudget()
        _gameBudgets.value = repo.loadGameBudgets()
        _profile.value = repo.loadProfile()
        _hoyolabConfig.value = repo.loadHoyolab()
        _accentIndex.value = repo.loadAccentIndex()
        attendanceMap = repo.loadAttendance()
        _attendanceHistory.value = attendanceMap
        _attendanceToday.value = attendanceMap[todayKey()] ?: emptySet()
        _attendanceStreak.value = computeAttendanceStreak()
        _pity.value = repo.loadPity()
        _eventChecks.value = repo.loadEventChecks()
        _readAlerts.value = repo.loadReadAlerts()
        _dismissedAlerts.value = repo.loadDismissedAlerts()
        _enkaGiUid.value = repo.loadEnkaGiUid()
        _enkaHsrUid.value = repo.loadEnkaHsrUid()
        _enkaResult.value = null
        _enkaResults.value = emptyMap()
        seedEnkaDiskCache()   // 재시작/계정전환 시 디스크 캐시를 메모리로 — '내 캐릭터' 즉시 표시
        seedGameInfoDiskCache()
        loadGachaDeferred()
        _subscriptions.value = repo.loadSubscriptions()
        _redeemedCodes.value = repo.loadRedeemedCodes()
        _savingsHeld.value = repo.loadSavingsHeld()
        _savingsHidden.value = repo.loadSavingsHidden()
        refreshSavings()
        recomputeSpendingDerived()   // 첫 프레임이 0원으로 그려지지 않게 동기로 한 번
    }

    /**
     * 배너·실시간 노트·전투 진행도의 **로컬 캐시를 시작 시 읽어** 홈을 즉시 채운다.
     *
     * 이 세 가지는 백그라운드 알림 판정용으로 이미 디스크에 저장하고 있었는데(saveActiveBanners 등),
     * 정작 앱을 켤 때는 읽지 않아서 **네트워크 응답 18건이 다 올 때까지 홈이 스켈레톤**이었다.
     * 저장해 둔 걸 그대로 쓰면 그 대기가 사라진다.
     *
     * [_gameInfoReady] 도 함께 올린다 — 값만 채우고 이 플래그가 false 면 화면은 계속 스켈레톤을 그린다.
     * 갱신은 [refreshGameInfo] 가 곧바로 이어서 하므로(lastGameInfoLoadAt 이 0 이라 신선도 검사에 걸리지 않는다)
     * 잠깐 지난 값이 보였다가 실제 값으로 바뀌는, '내 캐릭터'와 같은 방식이 된다.
     */
    private fun seedGameInfoDiskCache() {
        runCatching {
            val banners = repo.loadActiveBanners()
            val notes = repo.loadLiveNotes()
            val combats = repo.loadCombatModes()
            // 클리어 편성은 전용 페이지에서만 쓰지만, 여기서 미리 채워야 페이지가 빈 화면으로 열리지 않는다.
            runCatching { repo.loadCombatClears() }.getOrNull()
                ?.takeIf { it.isNotEmpty() }?.let { _combatClears.value = it }
            // 일정·소식도 함께 — 예전엔 이 둘만 캐시가 없어서, 배너·오늘 할 일은 캐시로 즉시 차는데
            // 홈의 '이번주 일정'·'게임 소식' 카드만 네트워크가 올 때까지 빈 채로 남았다.
            val events = repo.loadGameEvents()
            val challenges = repo.loadChallenges()
            val news = repo.loadGameNews()
            if (banners.isNotEmpty()) {
                _activeBanners.value = banners.sortedWith(compareBy({ it.isEndUnknown }, { it.dDay() }))
            }
            if (notes.isNotEmpty()) _liveNotes.value = notes
            if (combats.isNotEmpty()) _combat.value = combats
            // 이미 끝난 항목은 되살리지 않는다 — 캐시가 하루 이상 묵었을 때 지난 이벤트가 잠깐 보이는 걸 막는다.
            val now = currentTimeMillis()
            if (events.isNotEmpty() || challenges.isNotEmpty()) {
                _gameEvents.value = events.filter { it.endMillis > now }.sortedBy { it.endMillis }
                _challenges.value = challenges.filter { it.endMillis > now }.sortedBy { it.endMillis }
                _scheduleReady.value = true   // 캐시가 있으면 스켈레톤 없이 바로 그린다
            }
            if (news.isNotEmpty()) {
                _gameNews.value = news.sortedByDescending { it.createdAtMillis }
                _newsReady.value = true
            }
            // 보여줄 게 하나라도 있으면 스켈레톤 대신 캐시를 그린다.
            if (banners.isNotEmpty() || notes.isNotEmpty()) _gameInfoReady.value = true
        }
    }

    /**
     * 가챠 기록 로드 + 통계 산출을 **한 프레임 뒤로** 미룬다.
     *
     * 기록이 수만 건이면 파싱 한 번에 전수 순회가 붙는데, 이게 앱 시작 경로에서 첫 화면이 그려지기 전에
     * 동기로 돌고 있었다. 데이터가 늘수록 시작이 선형으로 느려지는 유일한 구간이었다.
     * (순회는 [GachaReport.computeAll] 로 통계·대시보드가 한 번에 나눠 쓴다 — 예전엔 각자 두 번이었다.)
     *
     * 홈은 이 값을 읽지 않는다 — 소비처는 마이페이지·가챠 리포트·데이터 관리뿐이고,
     * 두 StateFlow 모두 초기값이 null 이라 늦게 도착해도 화면이 깨지지 않는다.
     *
     * 매번 저장소에서 다시 읽으므로 그 사이에 가져오기/초기화가 일어나도 결과가 어긋나지 않는다.
     */
    private fun loadGachaDeferred() {
        viewModelScope.launch {
            yield()   // Main.immediate 라 양보하지 않으면 그대로 이어서 실행된다
            gachaRecords = repo.loadGachaRecords()
            val (stats, dash) = GachaReport.computeAll(gachaRecords)
            _gachaStats.value = stats
            _gachaDashboard.value = dash
        }
    }

    // ----------------------------------------------------------------- 계정 (구글 로그인 — Credential Manager)
    /**
     * 구글 로그인(원탭). UI 에서 **Activity 컨텍스트**로 호출한다.
     * 계정 선택 시트를 띄워 한 번 탭하면 로그인 → Firebase 인증 → 클라우드 복원까지 진행.
     */
    fun signIn() {
        viewModelScope.launch {
            if (cloudConfigured) _initialSyncing.value = true
            when (val outcome = authManager.signIn(autoSelectOnly = false)) {
                is SignInOutcome.Success -> {
                    if (!completeSignIn(outcome.account)) {
                        _initialSyncing.value = false
                        emitStatus("네트워크 오류로 로그인에 실패했어요")
                    }
                }
                SignInOutcome.NoCredential -> { _initialSyncing.value = false; emitStatus("로그인이 취소되었거나 완료되지 못했어요") }
                is SignInOutcome.Error -> { _initialSyncing.value = false; emitStatus(outcome.message) }
            }
        }
    }

    /**
     * 로그인 성공 공통 처리: Firebase 인증 → uid 로 계정 식별자 통일 → 계정 전환 → 클라우드 복원.
     *
     * E13 방어: Firebase 설정 환경에서 인증이 실패(오프라인 등으로 uid 못 받음)하면 **email 키 계정으로
     * 전환하지 않고** 게스트로 롤백 후 false 를 반환한다. (email 키 ↔ uid 키 불일치로 "로그인됐는데
     * 동기화 안 됨" 상태가 영속되는 것을 방지.) 반환값: 로그인 확정 성공 여부.
     */
    private suspend fun completeSignIn(acc: Account): Boolean {
        val finalAcc = if (cloudConfigured) {
            val uid = authManager.lastIdToken?.let { CloudSync.signInWithGoogle(it, authManager.lastAccessToken) }
            if (uid == null) {
                // Firebase 인증 실패 → 방금 영속된 email 계정을 롤백(게스트로 복귀)
                authManager.signOut()
                return false
            }
            acc.copy(id = uid)
        } else acc
        authManager.setAccount(finalAcc)
        switchAccount(finalAcc)
        cloudSyncPullOrSeed()
        emitStatus("${finalAcc.name}님으로 로그인되었어요")
        return true
    }

    /**
     * 로그아웃 — Firebase signOut·플랫폼 인증 정리는 네트워크를 타서 수 초가 걸릴 수 있다.
     * 그 동안 UI 가 아무 반응이 없으면 "눌린 건지" 알 수 없으므로 [signingOut] 으로 진행 상태를 알린다.
     *
     * 느린/끊긴 망에서 로딩이 영원히 걸리지 않도록 [SYNC_TIMEOUT_MS] 로 감싼다 — 타임아웃돼도
     * 로컬 계정 상태는 게스트로 내려 로그아웃은 성립시킨다(서버 세션은 다음 온라인에 정리).
     */
    fun signOut() {
        if (_signingOut.value) return          // 연타 방지 — 이미 진행 중이면 무시
        viewModelScope.launch {
            _signingOut.value = true
            try {
                // ★ 인증을 끊기 전에 대기 중인 클라우드 push 를 먼저 밀어낸다(아래 flush 주석 참고).
                flushPendingCloudSync()
                val done = withTimeoutOrNull(SYNC_TIMEOUT_MS) { authManager.signOut() } != null
                switchAccount(Account.GUEST)
                emitStatus(if (done) "로그아웃되었어요" else "로그아웃했어요 (서버 정리는 나중에 완료돼요)")
            } finally {
                _signingOut.value = false
            }
        }
    }

    private fun switchAccount(acc: Account) {
        repo = GatchaRepository(acc.id)
        repo.onChange = { scheduleCloudSync() }
        loadAll()
        // 로그인 계정이면 프로필을 구글 계정 정보로 맞춤
        if (!acc.isGuest) {
            val p = UserProfile(name = acc.name, email = acc.email)
            _profile.value = p
            repo.saveProfile(p)
        }
        refreshGameInfo(force = true)
    }

    // ----------------------------------------------------------------- 지출
    fun addSpending(spending: Spending) {
        // 상태 갱신과 저장을 분리한다 — update {} 블록은 CAS 재시도 시 통째로 다시 실행되므로
        // 그 안에 저장을 넣으면 디스크 쓰기와 클라우드 푸시 예약이 한 번 더 일어날 수 있다.
        val next = (listOf(spending) + _spendings.value).sortedByDescending { it.dateMillis }
        _spendings.value = next
        repo.saveSpendings(next)
        autoLinkSubscription(spending)
        refreshChallenge()
        emitStatus("지출이 저장되었어요")
    }

    fun updateSpending(updated: Spending) {
        val next = _spendings.value.map { if (it.id == updated.id) updated else it }
            .sortedByDescending { it.dateMillis }
        _spendings.value = next
        repo.saveSpendings(next)
        autoLinkSubscription(updated)
        refreshChallenge()
        emitStatus("지출이 수정되었어요")
    }

    /** 정기결제용 표시명 — 규칙은 [SpendingDerived.subscriptionName] 단일 소스. */
    private fun subscriptionName(s: Spending): String = SpendingDerived.subscriptionName(s)

    /** 같은 구독이 이미 등록돼 있는지(이름·게임·금액 기준). */
    private fun List<Subscription>.hasMatch(name: String, s: Spending): Boolean =
        any { it.name == name && it.gameName == s.gameName && it.amount == s.amount }

    /**
     * A안: '구독으로 기록'한 지출을 정기결제(Subscription)로 자동 등록.
     * 결제일=지출 날짜의 일. 동일 구독이 이미 있으면 중복 등록하지 않는다.
     */
    private fun autoLinkSubscription(spending: Spending) {
        if (!spending.isSubscription) return
        val name = subscriptionName(spending)
        if (_subscriptions.value.hasMatch(name, spending)) return
        addSubscription(
            Subscription(
                name = name,
                gameName = spending.gameName,
                amount = spending.amount,
                billingDay = DateUtil.dayOfMonth(spending.dateMillis).coerceIn(1, 31),
            ),
        )
    }

    /** 지출 내역의 '구독' 표시 항목 중 아직 정기결제로 등록되지 않은 건수(중복 이름·게임·금액 제외). */
    fun unlinkedSubscriptionSpendingCount(): Int = collectUnlinkedSubscriptions().size

    /** 지출 내역의 '구독' 표시 항목을 정기결제로 일괄 등록(중복 제외). 새로 등록한 건수 반환. */
    fun importSubscriptionsFromSpendings(): Int {
        val toAdd = collectUnlinkedSubscriptions()
        if (toAdd.isEmpty()) return 0
        _subscriptions.value = (_subscriptions.value + toAdd).sortedBy { it.billingDay }
        repo.saveSubscriptions(_subscriptions.value)
        emitStatus("정기결제 ${toAdd.size}건을 가져왔어요")
        return toAdd.size
    }

    /** 미등록 구독표시 지출 → Subscription 후보(이름·게임·금액 중복 제거, 최신 결제일 우선). */
    private fun collectUnlinkedSubscriptions(): List<Subscription> =
        SpendingDerived.unlinkedSubscriptions(
            _spendings.value.filter { it.isSubscription },
            _subscriptions.value,
        )

    fun deleteSpending(id: String) {
        repo.addDeletedSpendingIds(setOf(id)) // tombstone — 삭제를 다른 기기에 전파(합집합 병합 방어)
        val (removed, next) = _spendings.value.partition { it.id == id }
        _spendings.value = next
        repo.saveSpendings(next)
        unlinkOrphanedSubscriptions(removed)
        refreshChallenge()
    }

    fun deleteSpendings(ids: Set<String>) {
        repo.addDeletedSpendingIds(ids) // tombstone — 삭제 전파
        // partition 1회 — 예전엔 같은 목록을 filter 로 두 번 훑었다.
        val (removed, next) = _spendings.value.partition { it.id in ids }
        _spendings.value = next
        repo.saveSpendings(next)
        unlinkOrphanedSubscriptions(removed)
        refreshChallenge()
    }

    /** 구독 매칭 키 — 이름·게임·금액이 같으면 같은 구독으로 본다(등록·해제 판정 공통). */
    private fun subKey(s: Spending): Triple<String, String, Long> =
        Triple(subscriptionName(s), s.gameName, s.amount)

    /**
     * A안 삭제 연동: '구독으로 기록'한 지출이 삭제됐을 때, 그 구독을 백업하는 다른 구독표시 지출이
     * 더 없으면 매칭되는 정기결제(Subscription)도 함께 제거. (매달 기록한 구독은 마지막 1건 삭제 시에만 제거)
     *
     * 남은 지출의 구독 키를 **한 번의 순회**로 모아 두고 대조한다. 예전엔 삭제 항목마다 지출 전체를
     * 다시 훑어서, 구독표시 지출 50건을 일괄 삭제하면 50 × 전체였다.
     */
    private fun unlinkOrphanedSubscriptions(removed: List<Spending>) {
        val removedKeys = removed.asSequence().filter { it.isSubscription }.map { subKey(it) }.toSet()
        if (removedKeys.isEmpty()) return
        val stillBacked = _spendings.value.asSequence()
            .filter { it.isSubscription }.map { subKey(it) }.toSet()
        removedKeys.forEach { key ->
            if (key in stillBacked) return@forEach
            val match = _subscriptions.value.firstOrNull {
                it.name == key.first && it.gameName == key.second && it.amount == key.third
            } ?: return@forEach
            deleteSubscription(match.id)
        }
    }

    /**
     * 선택한 지출들의 일부 필드를 일괄 변경. null/빈 인자는 해당 필드 미변경.
     * 게임 변경 시 게임색(gameColor)도 함께 보정. 태그는 기존에 추가(중복 제거).
     */
    fun bulkEditSpendings(ids: Set<String>, gameName: String?, dateMillis: Long?, addTags: List<String>) {
        if (ids.isEmpty()) return
        val next = _spendings.value.map { s ->
            if (s.id !in ids) s else s.copy(
                gameName = gameName ?: s.gameName,
                gameColor = gameName?.let { GameData.colorFor(it) } ?: s.gameColor,
                dateMillis = dateMillis ?: s.dateMillis,
                tags = if (addTags.isEmpty()) s.tags else (s.tags + addTags).distinct(),
            )
        }.sortedByDescending { it.dateMillis }
        _spendings.value = next
        repo.saveSpendings(next)
        refreshChallenge()
        emitStatus("${ids.size}건 일괄 수정했어요")
    }

    /** 모든 지출 기록 삭제. */
    fun clearSpendings() {
        _spendings.value = emptyList()
        repo.saveSpendings(emptyList())
        refreshChallenge()
    }

    // ----------------------------------------------------------------- 예산
    fun setBudget(value: Long) {
        _budget.value = value
        repo.saveBudget(value)
        refreshChallenge()
    }

    /** 게임별 한도 설정. value 0 이면 해당 게임 한도 해제. */
    fun setGameBudget(gameKey: String, value: Long) {
        val updated = _gameBudgets.value.toMutableMap()
        if (value > 0) updated[gameKey] = value else updated.remove(gameKey)
        _gameBudgets.value = updated
        repo.saveGameBudgets(updated)
    }

    /** 전체 예산 + 게임별 한도를 한 번에 저장(예산 관리 시트용). */
    fun setBudgets(overall: Long, perGame: Map<String, Long>) {
        _budget.value = overall
        repo.saveBudget(overall)
        val cleaned = perGame.filterValues { it > 0 }
        _gameBudgets.value = cleaned
        repo.saveGameBudgets(cleaned)
        refreshChallenge()
    }

    /** 이번 달 게임별 지출 합계(gameKey → 금액). */
    fun monthlyTotalsByGame(year: Int = currentYear, month: Int = currentMonth): Map<String, Long> =
        _spendings.value
            .filter { DateUtil.isSameMonth(it.dateMillis, year, month) }
            .groupBy { GameData.byNameOrNull(it.gameName)?.key ?: it.gameName }
            .mapValues { e -> e.value.sumOf { it.amount } }

    // ----------------------------------------------------------------- 프로필
    fun setProfileName(name: String) {
        val next = _profile.value.copy(name = name)
        _profile.value = next
        repo.saveProfile(next)
    }

    // ----------------------------------------------------------------- HoYoLAB
    fun updateHoyolabConfig(config: HoyolabConfig) {
        _hoyolabConfig.value = config
        // 보안 저장소를 못 쓰면 토큰은 저장되지 않는다(평문 폴백 금지) — 조용히 넘기지 않고 알린다.
        if (!repo.saveHoyolab(config)) {
            emitStatus("보안 저장소를 쓸 수 없어 토큰을 저장하지 못했어요 — 앱을 재설치하거나 기기를 재시작해주세요")
            return
        }
        // 새 토큰이 들어왔으면 만료 플래그 자동 클리어 — 홈 상단 배너 즉시 사라짐.
        if (config.isLinked && config.ltoken.isNotBlank()) {
            appSettings.hoyoTokenExpired = false
            _hoyoTokenExpired.value = false
        }
        // 연동 성공/실패 넛징(전역 토스트). 토큰이 있으면 실제 유효성 검증 후 안내.
        if (!config.isLinked) {
            emitStatus("연동되지 않았어요 — ltuid·ltoken을 입력하거나 로그인으로 가져오세요")
            return
        }
        viewModelScope.launch {
            val uids = withContext(Dispatchers.IO) {
                HoyolabApi.fetchGameUids(config.ltuid, config.ltoken)
            }
            // 연동 계정의 게임 UID 를 「내 캐릭터」 섹션이 쓰도록 반영 — 별도 입력 불필요
            if (uids.isNotEmpty()) {
                applyGameUids(uids)
                enkaUidsSynced = true
                // 연동 직후 '내 캐릭터' 로스터를 강제 새로고침 — 토큰이 이제 있으므로,
                // 토큰 없이 받아 캐시에 박혀 있던 쇼케이스(공개)만 결과를 전체 보유 로스터로 즉시 교체한다.
                // (재설치 후 연동 1회로 '저장 다시' 없이 바로 전체 노출 — force=true 로 5분 TTL 무시.)
                autoLoadEnkaSection(listOf("genshin", "hsr", "zzz"), force = true)
            }
            emitStatus(
                if (uids.isNotEmpty()) "HoYoLAB 계정이 연동되었어요 ✓ (캐릭터 UID 자동 설정)"
                else "연동 실패 — 토큰이 만료됐을 수 있어요. 다시 로그인해 가져와주세요",
            )
        }
    }

    // fetchGameUids 결과를 config(genshin/hsr/zzz) + Enka 섹션 UID 로 반영(공통).
    private fun applyGameUids(uids: Map<String, String>) {
        if (uids.isEmpty()) return
        val cur = _hoyolabConfig.value
        val merged = cur.copy(
            genshinUid = uids["genshin"]?.ifBlank { null } ?: cur.genshinUid,
            hsrUid = uids["hsr"]?.ifBlank { null } ?: cur.hsrUid,
            zzzUid = uids["zzz"]?.ifBlank { null } ?: cur.zzzUid,
        )
        _hoyolabConfig.value = merged
        repo.saveHoyolab(merged)
        uids["genshin"]?.takeIf { it.isNotBlank() }?.let { _enkaGiUid.value = it }
        uids["hsr"]?.takeIf { it.isNotBlank() }?.let { _enkaHsrUid.value = it }
        repo.saveEnkaUids(_enkaGiUid.value, _enkaHsrUid.value)
    }

    // 기존 연동 사용자 대비: 연동됐는데 게임 UID 가 비어있으면 1회 자동 동기화(재연동 불필요).
    private var enkaUidsSynced = false
    private suspend fun ensureEnkaUids() {
        if (enkaUidsSynced) return
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) return
        if (_enkaGiUid.value.isNotBlank() && _enkaHsrUid.value.isNotBlank() && cfg.zzzUid.isNotBlank()) {
            enkaUidsSynced = true
            return
        }
        val uids = withContext(Dispatchers.IO) { HoyolabApi.fetchGameUids(cfg.ltuid, cfg.ltoken) }
        if (uids.isNotEmpty()) {
            applyGameUids(uids)
            enkaUidsSynced = true
        }
    }

    // ----------------------------------------------------------------- 테마 강조색
    fun setAccentIndex(index: Int) {
        _accentIndex.value = index
        repo.saveAccentIndex(index)
    }

    // ----------------------------------------------------------------- 출석
    fun toggleAttendance(gameKey: String) {
        val today = todayKey()
        val current = attendanceMap[today]?.toMutableSet() ?: mutableSetOf()
        if (gameKey in current) current.remove(gameKey) else current.add(gameKey)
        attendanceMap = attendanceMap.toMutableMap().apply { put(today, current) }
        repo.saveAttendance(attendanceMap)
        _attendanceHistory.value = attendanceMap
        _attendanceToday.value = current
        _attendanceStreak.value = computeAttendanceStreak()
    }

    fun isCheckedIn(gameKey: String): Boolean = gameKey in _attendanceToday.value

    // ----------------------------------------------------------------- 배너 / 실시간 노트
    // 더미 없음 — 실제 ennead.cc API(refreshGameInfo)로만 채워진다.
    private val _activeBanners = MutableStateFlow<List<GachaBanner>>(emptyList())
    val activeBanners: StateFlow<List<GachaBanner>> = _activeBanners.asStateFlow()

    // 실시간 노트는 HoYoLAB 연동 시에만 실제 API 로 채워진다(미연동이면 비어 있음).
    private val _liveNotes = MutableStateFlow<List<LiveNote>>(emptyList())
    val liveNotes: StateFlow<List<LiveNote>> = _liveNotes.asStateFlow()

    /**
     * 캐릭터별 유효옵션 사용자 설정(키=keyStatOverrideKey). 앱 룰보다 우선한다.
     * 룰은 추정일 뿐이라 틀릴 수 있고, 그 오차가 유효 점수로 바로 드러나기 때문이다.
     */
    private val _keyStatOverrides = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val keyStatOverrides: StateFlow<Map<String, Set<String>>> = _keyStatOverrides.asStateFlow()

    /** 유효옵션 설정 저장. 빈 집합이면 설정 해제(앱 룰 추정으로 되돌아간다). */
    fun setKeyStatOverride(key: String, stats: Set<String>) {
        val next = _keyStatOverrides.value.toMutableMap()
        if (stats.isEmpty()) next.remove(key) else next[key] = stats
        _keyStatOverrides.value = next
        runCatching { repo.saveKeyStatOverrides(next) }
    }

    /** 게임별 일일·주간 숙제 완주율(관측 기록 파생). 기록이 없는 게임은 목록에 없다. */
    private val _taskStats = MutableStateFlow<List<TaskStats>>(emptyList())
    val taskStats: StateFlow<List<TaskStats>> = _taskStats.asStateFlow()

    // 월간 수입 일지(여행자의 일지·개척의 길). HoYoLAB 연동 시에만 채워진다.
    private val _ledgers = MutableStateFlow<List<MonthlyLedger>>(emptyList())
    val ledgers: StateFlow<List<MonthlyLedger>> = _ledgers.asStateFlow()

    // 전투 콘텐츠 진행도(나선 비경·현실 속 환상극·혼돈의 기억·허구 이야기·종말의 환영).
    private val _combat = MutableStateFlow<List<CombatMode>>(emptyList())
    val combat: StateFlow<List<CombatMode>> = _combat.asStateFlow()

    // ── 엔드 콘텐츠 클리어 편성(층·간별로 어떤 캐릭터를 썼는지) ──
    // 시즌 2개치 × 모드 4~6개라 호출이 무겁다. 전용 페이지에 들어갈 때만 받고, 화면은 캐시부터 그린다.
    private val _combatClears = MutableStateFlow<List<CombatClear>>(emptyList())
    val combatClears: StateFlow<List<CombatClear>> = _combatClears.asStateFlow()

    private val _combatClearsLoading = MutableStateFlow(false)
    val combatClearsLoading: StateFlow<Boolean> = _combatClearsLoading.asStateFlow()

    /** 마지막으로 클리어 편성을 받아온 시각 — 페이지 재진입마다 다시 받지 않게. */
    private var lastCombatClearAt = 0L

    /**
     * 엔드 콘텐츠 클리어 편성 조회. 캐시가 신선하면(30분) 건너뛴다.
     *
     * 이름 채우기: HoYoLAB 전투 응답에는 캐릭터 **이름이 없다**(id·아이콘뿐). 이미 받아 둔
     * 보유 캐릭터([enkaResults])가 같은 id 체계를 쓰므로 거기서 이름을 끌어온다.
     */
    fun refreshCombatClears(force: Boolean = false) {
        if (_combatClearsLoading.value) return
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) return
        val now = currentTimeMillis()
        if (!force && _combatClears.value.isNotEmpty() && now - lastCombatClearAt < COMBAT_CLEAR_FRESH_MS) return
        viewModelScope.launch {
            _combatClearsLoading.value = true
            try {
                val uids = mapOf("genshin" to cfg.genshinUid, "hsr" to cfg.hsrUid).filterValues { it.isNotBlank() }
                val fetched = coroutineScope {
                    uids.map { (key, uid) ->
                        async(Dispatchers.IO) {
                            runCatching { HoyolabApi.getCombatClears(cfg.ltuid, cfg.ltoken, key, uid) }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll().flatten()
                }
                // 한 게임이라도 응답이 비면 옛 결과를 지우지 않는다 — 화면이 통째로 비는 것보다 낫다.
                if (fetched.isEmpty()) return@launch
                // 이름 출처는 두 겹이다. ①전체 캐릭터 메타(yatta) — 쇼케이스에 없는 캐릭터까지 덮는다.
                // ②보유 캐릭터 캐시 — 메타에 아직 없는 신규 캐릭터를 보완한다(우선순위가 더 높다).
                //
                // ⚠️ 두 겹 모두 **게임별로** 유지한다. 캐릭터 id 공간이 게임마다 독립이라
                // 하나로 합치면 스타레일 1xxx 와 젠레스 1xxx 가 충돌한다(달리아 → 이블린 사고).
                val metaNames = runCatching {
                    coroutineScope {
                        val gi = async(Dispatchers.IO) { EnkaApi.characterNames(hsr = false) }
                        val hsr = async(Dispatchers.IO) { EnkaApi.characterNames(hsr = true) }
                        mapOf(Game.GENSHIN.key to gi.await(), Game.HSR.key to hsr.await())
                    }
                }.getOrDefault(emptyMap())
                val ownedNames = characterNamesByGame()
                // 게임별로 메타 위에 보유 캐시를 덮는다(신규 캐릭터가 메타보다 먼저 들어온다).
                val namesByGame = (metaNames.keys + ownedNames.keys).associateWith { key ->
                    metaNames[key].orEmpty() + ownedNames[key].orEmpty()
                }
                val named = CombatClearLogic.withNames(fetched, namesByGame)
                val grouped = CombatClearLogic.grouped(named)
                _combatClears.value = grouped
                lastCombatClearAt = currentTimeMillis()
                runCatching { repo.saveCombatClears(grouped) }
            } finally {
                _combatClearsLoading.value = false
            }
        }
    }

    /** 보유 캐릭터 캐시에서 id → 이름 맵을 만든다(게임 구분 없이 — id 가 게임별로 겹치지 않는다). */
    /**
     * 보유 캐릭터 캐시의 이름 — **게임 키별로** 나눠 준다.
     *
     * 예전엔 세 게임을 한 맵에 합쳤는데, 캐릭터 id 는 게임마다 독립이라 스타레일과 젠레스가
     * 정면으로 겹친다. 합친 맵을 클리어 편성에 넘기는 바람에 스타레일 캐릭터 자리에
     * 젠레스 이름이 찍혔다.
     */
    private fun characterNamesByGame(): Map<String, Map<Int, String>> =
        _enkaResults.value.mapValues { (_, r) ->
            r.profile?.chars?.associate { it.id to it.name }.orEmpty()
        }

    private val _gameEvents = MutableStateFlow<List<GameEvent>>(emptyList())
    val gameEvents: StateFlow<List<GameEvent>> = _gameEvents.asStateFlow()

    private val _challenges = MutableStateFlow<List<GameChallenge>>(emptyList())
    val challenges: StateFlow<List<GameChallenge>> = _challenges.asStateFlow()

    // 게임 공지·뉴스(ennead news, 공개·한국어). 게임정보 공지 섹션용.
    private val _gameNews = MutableStateFlow<List<NewsItem>>(emptyList())
    val gameNews: StateFlow<List<NewsItem>> = _gameNews.asStateFlow()

    /**
     * 공지에서 확인된 **확정** 방송. 비어 있으면 화면은 역산 예상값을 쓴다.
     *
     * 캐시하지 않는다 — 방송은 지나가면 값이 없고, 다음 회차는 어차피 새 공지로 온다.
     */
    private val _confirmedBroadcasts = MutableStateFlow<List<ConfirmedBroadcast>>(emptyList())
    val confirmedBroadcasts: StateFlow<List<ConfirmedBroadcast>> = _confirmedBroadcasts.asStateFlow()

    // ── 공지 본문(상세 페이지) ──────────────────────────────────────────────
    // 목록에서 공지를 열면 HoYoLab 아티클 API 로 본문을 받아 온다. 실패해도 화면은 비우지 않는다 —
    // 목록이 이미 갖고 있는 summary(줄바꿈 없는 평문)로 폴백하고, '브라우저에서 보기'를 함께 제공한다.
    private val _newsArticle = MutableStateFlow<NewsArticle?>(null)
    val newsArticle: StateFlow<NewsArticle?> = _newsArticle.asStateFlow()

    private val _newsArticleLoading = MutableStateFlow(false)
    val newsArticleLoading: StateFlow<Boolean> = _newsArticleLoading.asStateFlow()

    /** 본문 로드 실패(네트워크·파싱) — UI 는 summary 폴백을 보여준다. */
    private val _newsArticleFailed = MutableStateFlow(false)
    val newsArticleFailed: StateFlow<Boolean> = _newsArticleFailed.asStateFlow()

    /** 현재 로드했거나 로드 중인 공지 id — 같은 글 재진입(탭 전환 복귀 포함) 시 재요청을 막는 기준. */
    private var newsArticleItemId: String? = null

    /**
     * 공지 상세 진입 — 본문을 받아 온다.
     * 같은 글을 다시 열면(탭 전환 후 복귀, 화면 재구성 등) 재요청하지 않는다 — 그러지 않으면
     * loading 이 다시 켜지며 스켈레톤이 깜빡이고 스크롤이 튄다. 단, 이전에 실패했으면 재시도한다.
     */
    fun loadNewsArticle(item: NewsItem) {
        if (newsArticleItemId == item.id && !_newsArticleFailed.value) return
        newsArticleItemId = item.id
        // 다른 글로 이동했으면 이전 본문이 잠깐 비치면 안 되므로 먼저 비운다.
        if (_newsArticle.value?.let { it.title != item.title } != false) _newsArticle.value = null
        _newsArticleFailed.value = false
        if (item.id.isBlank()) { _newsArticleFailed.value = true; return }
        _newsArticleLoading.value = true
        viewModelScope.launch {
            val article = NewsApi.article(item)
            _newsArticle.value = article
            _newsArticleFailed.value = article == null
            _newsArticleLoading.value = false
        }
    }

    /** 공지 상세 이탈 — 다음 진입 때 이전 글이 비치지 않도록 정리. */
    fun clearNewsArticle() {
        newsArticleItemId = null
        _newsArticle.value = null
        _newsArticleLoading.value = false
        _newsArticleFailed.value = false
    }


    // 천장(gameKey -> PityState), 이벤트 체크
    private val _pity = MutableStateFlow<Map<String, PityState>>(emptyMap())
    val pity: StateFlow<Map<String, PityState>> = _pity.asStateFlow()

    private val _eventChecks = MutableStateFlow<Set<String>>(emptySet())
    val eventChecks: StateFlow<Set<String>> = _eventChecks.asStateFlow()

    // ----- 천장 카운터 -----
    fun adjustPity(gameKey: String, delta: Int) = updatePity(gameKey) { it.copy(count = (it.count + delta).coerceAtLeast(0)) }
    fun setPityCount(gameKey: String, value: Int) = updatePity(gameKey) { it.copy(count = value.coerceAtLeast(0)) }
    fun resetPity(gameKey: String) = updatePity(gameKey) { it.copy(count = 0, guaranteed = false) }
    fun setPityGuaranteed(gameKey: String, g: Boolean) = updatePity(gameKey) { it.copy(guaranteed = g) }

    private fun updatePity(gameKey: String, transform: (PityState) -> PityState) {
        val cur = _pity.value[gameKey] ?: PityState()
        val next = transform(cur)
        val updated = _pity.value + (gameKey to next)
        _pity.value = updated
        repo.savePity(updated)
        refreshPlans()
        // 임박 단계 상승 시 1회 토스트(리셋·후퇴는 무시).
        val banner = com.gatcha.log.data.GachaRateData.byKey(gameKey)?.character
        if (banner != null) {
            val before = com.gatcha.log.data.pityTierOf(cur.count, banner)
            val after = com.gatcha.log.data.pityTierOf(next.count, banner)
            if (after.ordinal > before.ordinal) {
                val game = com.gatcha.log.data.GameData.byNameOrNull(gameKey)
                val name = game?.shortName ?: gameKey
                val grade = com.gatcha.log.data.GachaRateData.byKey(gameKey)?.grade ?: "5★"
                val msg = when (after) {
                    com.gatcha.log.data.PityTier.Caution -> "$name 천장 ${banner.softPity - 10}연 진입 — 슬슬 모아두실 시간이에요"
                    com.gatcha.log.data.PityTier.Imminent -> "$name 소프트 천장 진입 — ${banner.hardPity - next.count}연 이내 $grade 보장"
                    com.gatcha.log.data.PityTier.Reached -> "$name 하드 천장 도달 — 다음 $grade 100% 확정"
                    com.gatcha.log.data.PityTier.Safe -> null
                }
                if (msg != null) emitStatus(msg)
            }
        }
    }

    // ----------------------------------------------------------------- 저축 플래너 · 절약 챌린지 (27.35)
    /** 게임별 보유 재화(gameKey → 재화량) — 저축 플래너 필요분 차감용. */
    private val _savingsHeld = MutableStateFlow<Map<String, Int>>(emptyMap())
    val savingsHeld: StateFlow<Map<String, Int>> = _savingsHeld.asStateFlow()

    /** "안 뽑는" 픽업 목표로 숨긴 키 집합(SavingsPlan.key) — 미노출 처리. */
    private val _savingsHidden = MutableStateFlow<Set<String>>(emptySet())
    val savingsHidden: StateFlow<Set<String>> = _savingsHidden.asStateFlow()

    /** 진행 중 픽업별 저축 계획(임박 순, 숨긴 목표 제외). activeBanners·pity·보유재화에서 파생. */
    private val _savingsPlans = MutableStateFlow<List<SavingsPlan>>(emptyList())
    val savingsPlans: StateFlow<List<SavingsPlan>> = _savingsPlans.asStateFlow()

    /** 숨김 처리된(안 뽑는) 픽업 목표 — 헤더 버튼으로 펼쳐 보고 다시 표시할 수 있게. */
    private val _hiddenSavingsPlans = MutableStateFlow<List<SavingsPlan>>(emptyList())
    val hiddenSavingsPlans: StateFlow<List<SavingsPlan>> = _hiddenSavingsPlans.asStateFlow()

    /** 절약 챌린지·스트릭·배지 상태. 지출·예산에서 파생(결정형). */
    private val _challenge = MutableStateFlow(SavingsChallenge.evaluate(emptyList(), 0L, 0, emptySet()))
    val challenge: StateFlow<ChallengeSummary> = _challenge.asStateFlow()

    /** 보유 재화 입력/해제(0 = 해제). */
    fun setHeldCurrency(gameKey: String, value: Int) {
        val updated = _savingsHeld.value.toMutableMap()
        if (value > 0) updated[gameKey] = value else updated.remove(gameKey)
        _savingsHeld.value = updated
        repo.saveSavingsHeld(updated)
        refreshPlans()
    }

    /** 픽업 목표 숨김/해제 토글(안 뽑는 목표 미노출). */
    fun setSavingsHidden(key: String, hidden: Boolean) {
        val updated = _savingsHidden.value.toMutableSet()
        if (hidden) updated.add(key) else updated.remove(key)
        _savingsHidden.value = updated
        repo.saveSavingsHidden(updated)
        refreshPlans()
    }

    private fun refreshPlans() {
        val all = SavingsPlanner.build(_activeBanners.value, _pity.value, _savingsHeld.value)
        val hidden = _savingsHidden.value
        _savingsPlans.value = all.filterNot { it.key in hidden }
        _hiddenSavingsPlans.value = all.filter { it.key in hidden }
    }

    /** 지출·예산 변경 시 챌린지 재평가 + 최고 스트릭·배지 단조 영속. */
    private fun refreshChallenge() {
        val prevBest = repo.loadBestNoSpend()
        val prevBadges = repo.loadEarnedBadges()
        val summary = SavingsChallenge.evaluate(_spendings.value, _budget.value, prevBest, prevBadges)
        if (summary.bestStreak > prevBest) repo.saveBestNoSpend(summary.bestStreak)
        val earned = SavingsChallenge.earnedIds(summary)
        if (earned != prevBadges) repo.saveEarnedBadges(earned)
        _challenge.value = summary
    }

    private fun refreshSavings() { refreshPlans(); refreshChallenge() }

    // ----- Enka 프로필 쇼케이스 -----
    private val _enkaGiUid = MutableStateFlow("")
    val enkaGiUid: StateFlow<String> = _enkaGiUid.asStateFlow()
    private val _enkaHsrUid = MutableStateFlow("")
    val enkaHsrUid: StateFlow<String> = _enkaHsrUid.asStateFlow()
    private val _enkaResult = MutableStateFlow<EnkaResult?>(null)
    val enkaResult: StateFlow<EnkaResult?> = _enkaResult.asStateFlow()
    private val _enkaLoading = MutableStateFlow(false)
    val enkaLoading: StateFlow<Boolean> = _enkaLoading.asStateFlow()

    // 상시 섹션용 TTL 캐시 ("game:uid" → (시각, 결과)). Enka 429 방지 위해 5분 내 재요청 생략.
    private val enkaCache = mutableMapOf<String, Pair<Long, EnkaResult>>()
    private val enkaTtlMs = 5 * 60 * 1000L
    // 디스크 캐시 유효기간 — 이보다 오래된 디스크 항목은 시드에서 제외(앱 재시작 즉시표시 한계).
    private val enkaDiskTtlMs = 24 * 60 * 60 * 1000L

    /** 디스크 캐시 → 메모리 캐시 시드(계정별). loadAll 에서 호출. */
    private fun seedEnkaDiskCache() {
        enkaCache.clear()
        enkaCache.putAll(repo.loadEnkaCache(enkaDiskTtlMs))
    }

    /** Enka UID 로 프로필 조회 + UID 계정별 영속(클라우드 동기화 포함). */
    fun loadEnkaProfile(game: String, uid: String) {
        val u = uid.trim()
        if (game == "genshin") _enkaGiUid.value = u else _enkaHsrUid.value = u
        repo.saveEnkaUids(_enkaGiUid.value, _enkaHsrUid.value)
        viewModelScope.launch {
            _enkaLoading.value = true
            val cfg = _hoyolabConfig.value
            val r = withContext(Dispatchers.IO) { EnkaApi.fetchProfile(game, u, cfg.ltuid, cfg.ltoken) }
            val cached = enkaCache["$game:$u"]
            when {
                r.profile != null -> { enkaCache["$game:$u"] = currentTimeMillis() to r; repo.saveEnkaCache(enkaCache); _enkaResult.value = r }
                cached != null -> _enkaResult.value = cached.second   // 실패(토큰만료 등) 시 마지막 정상 로스터 유지 — stale-while-revalidate
                else -> _enkaResult.value = r                          // 표시할 캐시 없을 때만 에러 표시
            }
            _enkaLoading.value = false
        }
    }

    /**
     * 게임정보 탭 상시 섹션 — 선택 게임의 저장 UID 로 자동 로드. 5분 캐시 적중 시 네트워크 생략.
     * UID 미설정이면 결과 비움(섹션 미표시). [force] 면 캐시 무시하고 새로고침.
     */
    private fun enkaUidFor(game: String): String = when (game) {
        "genshin" -> _enkaGiUid.value
        "hsr" -> _enkaHsrUid.value
        else -> _hoyolabConfig.value.zzzUid // 젠레스: 연동 계정 UID
    }.trim()

    /**
     * 단일 화면(로스터·스탯)용 조회. 결과는 [_enkaResult] 와 **게임별 맵([_enkaResults]) 양쪽에** 싣는다.
     *
     * 맵에도 싣는 이유: 단일 슬롯은 게임 키가 없어 "지금 값이 어느 게임 것인지" 알 수 없다.
     * 로스터를 원신으로 열었는데 슬롯에 스타레일 결과(혹은 null)가 들어 있으면 화면이 비어 보였다.
     * 게다가 같은 조회가 이미 진행 중이면([enkaInFlight]) 이 호출은 **아무것도 싣지 않고 빠져나가**,
     * 그 요청이 끝나도 슬롯은 갱신되지 않았다 — 뒤로 갔다 다시 들어오면(캐시 적중) 그제야 보이던 증상.
     * 화면은 맵을 게임 키로 읽으면 되고, 진행 중이면 그 요청의 주인이 맵에 결과를 넣어 준다.
     */
    fun autoLoadEnka(game: String, force: Boolean = false) {
        // 캐시 적중 시 동기 반영(탭 전환 즉시) — UID 가 이미 있는 경우만
        val uidNow = enkaUidFor(game)
        if (!force && uidNow.isNotBlank()) {
            val cached = enkaCache["$game:$uidNow"]
            if (cached != null && currentTimeMillis() - cached.first < enkaTtlMs) {
                publishEnka(game, cached.second)
                return
            }
        }
        viewModelScope.launch {
            ensureEnkaUids() // 연동됐는데 UID 비면 1회 동기화 → 기존 사용자도 자동 로드
            val uid = enkaUidFor(game)
            if (uid.isBlank()) {
                _enkaResult.value = null
                _enkaResults.update { it + (game to EnkaResult(profile = null, error = null)) }
                return@launch
            }
            val key = "$game:$uid"
            val cached = enkaCache[key]
            if (cached != null) publishEnka(game, cached.second)   // 있으면 먼저 보여준다(stale-while-revalidate)
            if (!force && cached != null && currentTimeMillis() - cached.first < enkaTtlMs) return@launch
            // 같은 조회가 진행 중이면 그 요청이 맵에 결과를 실어 준다 — 여기서 더 할 일이 없다.
            if (!enkaInFlight.add(key)) return@launch
            _enkaLoading.value = true
            if (cached == null) _enkaLoadingGames.update { it + game }   // 보여줄 게 없을 때만 스피너
            try {
                val cfg = _hoyolabConfig.value
                val r = withContext(Dispatchers.IO) { EnkaApi.fetchProfile(game, uid, cfg.ltuid, cfg.ltoken) }
                when {
                    r.profile != null -> {
                        enkaCache[key] = currentTimeMillis() to r; repo.saveEnkaCache(enkaCache); publishEnka(game, r)
                    }
                    // 실패 시 기존 캐시(신선/오래됨 무관) 유지 — 목록 사라짐 방지. 캐시 없을 때만 에러 표시.
                    cached == null -> publishEnka(game, r)
                }
            } finally {
                enkaInFlight.remove(key)
                _enkaLoading.value = false
                _enkaLoadingGames.update { it - game }
            }
        }
    }

    /** 조회 결과를 단일 슬롯과 게임별 맵에 함께 싣는다(둘이 갈리면 화면마다 다른 걸 본다). */
    private fun publishEnka(game: String, result: EnkaResult) {
        _enkaResult.value = result
        _enkaResults.update { it + (game to result) }
    }

    /** 게임 탭 전환 시 이전 결과 정리 */
    fun clearEnkaResult() { _enkaResult.value = null }

    // ----- '내 캐릭터' 섹션 — 헤더 게임필터 연동(전체=3게임 동시 표시) -----
    // 단일 _enkaResult 와 별개로 게임별 결과를 동시에 보관. enkaCache(5분 TTL)를 공유한다.
    // 값은 non-null(iOS SKIE 브리징 단순화) — 미설정/빈 게임은 EnkaResult(null,null)로 채워 '빈 표시'.
    private val _enkaResults = MutableStateFlow<Map<String, EnkaResult>>(emptyMap())
    val enkaResults: StateFlow<Map<String, EnkaResult>> = _enkaResults.asStateFlow()
    private val _enkaLoadingGames = MutableStateFlow<Set<String>>(emptySet())
    val enkaLoadingGames: StateFlow<Set<String>> = _enkaLoadingGames.asStateFlow()

    /**
     * 네트워크 조회가 진행 중인 게임(키 = "게임:uid").
     *
     * '내 캐릭터' 섹션은 게임 필터가 바뀔 때마다 다시 요청한다. 캐시가 신선하면 그 전에 빠져나가지만,
     * 캐시가 없거나 만료된 상태에서 필터를 몇 번 건드리면 같은 조회가 통째로 겹친다.
     * 특히 젠레스는 서버가 다건 조회(id_list)를 거부해 **에이전트 1명당 1요청**이라, 보유 50명이면
     * 한 세트가 51건이다. 겹치면 그대로 배가 된다.
     */
    private val enkaInFlight = mutableSetOf<String>()

    /**
     * '내 캐릭터' 섹션용 — 지정 게임들의 로스터를 동시 보관. 캐시 적중분은 즉시 반영.
     * 게임마다 호스트가 달라(enka.network / mihomo / HoYoLAB) 동시 호출해도 429와 무관하므로
     * 미적중분을 **병렬**로 가져온다(총 지연 = 합 → 최댓값). 네트워크는 IO 디스패처로 분리해 메인 스레드 미점유.
     * UID 미설정 게임은 빈 결과(섹션서 빈 표시).
     */
    fun autoLoadEnkaSection(games: List<String>, force: Boolean = false) {
        viewModelScope.launch {
            ensureEnkaUids() // 연동됐는데 UID 비면 1회 동기화
            games.map { game ->
                async {
                    val uid = enkaUidFor(game)
                    if (uid.isBlank()) {
                        _enkaResults.update { it + (game to EnkaResult(profile = null, error = null)) }
                        return@async
                    }
                    val key = "$game:$uid"
                    val cached = enkaCache[key]   // 디스크 시드 포함
                    val fresh = cached != null && currentTimeMillis() - cached.first < enkaTtlMs
                    // 캐시(신선/오래됨 무관)가 있으면 즉시 표시 — stale-while-revalidate
                    if (cached != null) {
                        _enkaResults.update { it + (game to cached.second) }
                        if (fresh && !force) return@async   // 신선하면 네트워크 생략
                    } else {
                        _enkaLoadingGames.update { it + game }   // 보여줄 캐시가 없을 때만 스피너
                    }
                    if (!enkaInFlight.add(key)) return@async   // 같은 조회가 이미 진행 중
                    try {
                    val cfg = _hoyolabConfig.value
                    val r = withContext(Dispatchers.IO) { EnkaApi.fetchProfile(game, uid, cfg.ltuid, cfg.ltoken) }
                    if (r.profile != null) {
                        enkaCache[key] = currentTimeMillis() to r
                        _enkaResults.update { it + (game to r) }   // 갱신분 반영
                    } else if (cached == null) {
                        _enkaResults.update { it + (game to r) }   // 캐시 없고 실패 → 에러 표시(캐시 있으면 기존 유지)
                    }
                    } finally {
                        enkaInFlight.remove(key)
                        _enkaLoadingGames.update { it - game }
                    }
                }
            }.awaitAll()
            repo.saveEnkaCache(enkaCache)   // 갱신된 캐시 디스크 영속(1회)
        }
    }

    // ----------------------------------------------------------------- 현재 게임 버전(nanoka)

    private val _gameVersions = MutableStateFlow<List<GameVersionLine>>(emptyList())
    /** 지금 돌고 있는 게임 버전(출석 3게임) — 데일리 타일 아래 한 줄. */
    val gameVersions: StateFlow<List<GameVersionLine>> = _gameVersions.asStateFlow()

    private var gameVersionsLoaded = false

    /**
     * 현재 버전 로드. 화면 진입 시 부르며 **한 번만** 실제로 돈다.
     *
     * 매니페스트 1건이면 끝나지만 버전이 바뀌는 주기는 몇 주라 매번 받을 이유가 없다.
     * 앱을 껐다 켜면 다시 받는다(메모리 플래그).
     *
     * 빈 결과는 **'없음'으로 굳히지 않는다** — 네트워크가 죽었을 뿐일 수 있어 다음 진입에 다시 본다.
     */
    fun loadGameVersions(force: Boolean = false) {
        if (gameVersionsLoaded && !force) return
        gameVersionsLoaded = true
        viewModelScope.launch {
            val versions = withContext(Dispatchers.IO) { GameVersions.live() }
            if (versions.isEmpty()) gameVersionsLoaded = false else _gameVersions.value = versions
        }
    }

    private val _weaponRefinement = MutableStateFlow<Map<String, WeaponRefinement>>(emptyMap())
    /** 무기·광추 정련 효과(키 "gameKey:weaponId:level"). 캐릭터 상세를 열 때만 채운다. */
    val weaponRefinement: StateFlow<Map<String, WeaponRefinement>> = _weaponRefinement.asStateFlow()

    /**
     * 장착 무기의 정련 효과 조회.
     *
     * 캐릭터 상세는 무기 이름과 수치만 보여 주는데, 정작 "이 무기가 무슨 일을 하는가"가 빠져 있었다.
     * 젠레스는 부르지 않는다 — 상류 한국어 W-엔진 데이터가 미번역 자리표시자로 오는 게 흔하다.
     */
    fun loadWeaponRefinement(gameKey: String, weaponId: Int, level: Int) {
        if (weaponId <= 0) return
        val key = "$gameKey:$weaponId:$level"
        if (_weaponRefinement.value.containsKey(key)) return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { NanokaApi.refinement(gameKey, weaponId, level) } ?: return@launch
            _weaponRefinement.update { it + (key to r) }
        }
    }

    // ----- 가챠 효율 리포트 (UIGF/SRGF) -----
    private var gachaRecords: List<GachaRecord> = emptyList()
    private val _gachaStats = MutableStateFlow<GachaStats?>(null)
    val gachaStats: StateFlow<GachaStats?> = _gachaStats.asStateFlow()
    private val _gachaDashboard = MutableStateFlow<GachaDashboard?>(null)
    val gachaDashboard: StateFlow<GachaDashboard?> = _gachaDashboard.asStateFlow()

    /** 선택한 JSON 파일들(UIGF/SRGF)의 내용을 읽어 파싱·중복제거·병합 후 저장. */
    fun importGachaFromContents(contents: List<String>) {
        if (contents.isEmpty()) return
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                contents.flatMap { text ->
                    GachaReport.normalize(text)
                }
            }
            if (parsed.isEmpty()) {
                emitStatus("가챠 기록을 찾지 못했어요 (UIGF/SRGF JSON 확인)")
                return@launch
            }
            val existingIds = gachaRecords.mapTo(HashSet()) { it.id }
            var added = 0
            var skipped = 0
            val merged = gachaRecords.toMutableList()
            for (r in parsed) {
                if (r.id.isBlank() || r.id in existingIds) { skipped++; continue }
                existingIds.add(r.id); merged.add(r); added++
            }
            gachaRecords = merged
            withContext(Dispatchers.IO) { repo.saveGachaRecords(merged) }
            val (stats, dash) = GachaReport.computeAll(merged)
            _gachaStats.value = stats
            _gachaDashboard.value = dash
            emitStatus("가챠 기록 ${added}건 추가 (중복 ${skipped} 제외)")
        }
    }

    fun clearGachaRecords() {
        gachaRecords = emptyList()
        repo.saveGachaRecords(emptyList())
        _gachaStats.value = null
        _gachaDashboard.value = null
        emitStatus("가챠 기록을 초기화했어요")
    }

    // ----------------------------------------------------------------- 백업 파일 내보내기/가져오기 (SAF)
    /**
     * 전체 데이터(가챠 포함) 스냅샷 JSON 을 반환한다(파일 쓰기는 UI 레이어가 담당).
     * 게스트·로그인 무관하게 동작하는 기기 독립 백업 — 재설치·기기 변경 후 [importBackupFromContent] 로 복원.
     */
    fun exportBackupContent(): String? {
        return repo.exportSnapshotJson()
    }

    /**
     * 백업 파일의 스냅샷 JSON([json])을 읽어 현재 계정에 복원한다.
     * 스냅샷에 있는 키만 덮어쓰며(로컬 전용 값 보존), 로그인 상태면 복원 결과를 클라우드에도 반영한다.
     */
    fun importBackupFromContent(json: String) {
        viewModelScope.launch {
            if (json.isBlank() || runCatching { com.gatcha.log.json.JSONObject(json) }.isFailure) {
                emitStatus("백업 파일을 읽지 못했어요 (형식 확인)")
                return@launch
            }
            repo.importSnapshotJson(json)
            loadAll()
            // 로그인 상태면 복원 결과를 클라우드에도 업로드(다른 기기와 일치)
            if (cloudConfigured) CloudSync.currentUid()?.let { uid ->
                withContext(Dispatchers.IO) { cloudPush(uid) }
            }
            emitStatus("백업을 복원했어요")
        }
    }

    // ----- 구독 관리 -----
    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    fun addSubscription(sub: Subscription) {
        _subscriptions.value = (_subscriptions.value + sub).sortedBy { it.billingDay }
        repo.saveSubscriptions(_subscriptions.value)
    }

    fun updateSubscription(sub: Subscription) {
        _subscriptions.value = _subscriptions.value.map { if (it.id == sub.id) sub else it }.sortedBy { it.billingDay }
        repo.saveSubscriptions(_subscriptions.value)
    }

    fun deleteSubscription(id: String) {
        val removed = _subscriptions.value.firstOrNull { it.id == id }
        _subscriptions.value = _subscriptions.value.filterNot { it.id == id }
        repo.saveSubscriptions(_subscriptions.value)
        // A안 연동: 이 정기결제를 백업하던 '구독으로 기록' 지출도 함께 삭제.
        // (raw 삭제 — deleteSpendings 경유 금지: unlinkOrphanedSubscriptions 재호출 루프 방지)
        removed?.let { sub ->
            val ids = _spendings.value.filter {
                it.isSubscription && subscriptionName(it) == sub.name && it.gameName == sub.gameName && it.amount == sub.amount
            }.map { it.id }.toSet()
            if (ids.isNotEmpty()) {
                val next = _spendings.value.filter { it.id !in ids }
                _spendings.value = next
                repo.saveSpendings(next)
            }
        }
    }

    // ----- 이벤트 체크리스트 -----
    fun toggleEventCheck(key: String) {
        val cur = _eventChecks.value.toMutableSet()
        if (key in cur) cur.remove(key) else cur.add(key)
        _eventChecks.value = cur
        repo.saveEventChecks(cur)
    }

    // ----------------------------------------------------------------- API 연동 상태
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 홈/오늘 할 일 표출 준비 완료(배너+실시간 노트 로드 후 true). 초기 로딩 스켈레톤 게이트. */
    private val _gameInfoReady = MutableStateFlow(false)
    val gameInfoReady: StateFlow<Boolean> = _gameInfoReady.asStateFlow()

    /**
     * 일정·공지 카드 **각각의** 표출 준비 상태 — 홈의 '이번 주 일정'·'게임 소식' 스켈레톤 게이트.
     *
     * [gameInfoReady] 하나로 두 카드를 같이 묶었더니, 배너·노트가 디스크 캐시로 즉시 차면서
     * 스켈레톤이 바로 걷히는데 정작 이 두 카드는 데이터가 없어 **아무것도 안 그리다가**
     * 응답이 온 뒤에야 튀어나왔다(= '느리게 노출'). 출처가 다르니 게이트도 따로 둔다.
     *
     * 한 번 true 가 되면 되돌리지 않는다 — 새로고침 때마다 스켈레톤이 깜빡이면 안 되고,
     * 그때는 이미 있는 값을 그대로 보여주다 교체하는 게 맞다.
     */
    private val _scheduleReady = MutableStateFlow(false)
    val scheduleReady: StateFlow<Boolean> = _scheduleReady.asStateFlow()
    private val _newsReady = MutableStateFlow(false)
    val newsReady: StateFlow<Boolean> = _newsReady.asStateFlow()
    /** 마지막 게임정보 성공 로드 시각 — freshness 캐시(재진입 시 불필요한 재요청 생략). */
    private var lastGameInfoLoadAt = 0L
    /** 이번 회차에 실시간 노트가 **일시적으로** 실패했는가 — 신선도를 짧게 잡아 곧 다시 받는다. */
    private var noteRetry = false
    private val gameInfoFreshMs = 5 * 60 * 1000L
    /** 일부 게임이 실패한 회차의 캐시 수명 — 짧게 잡아 다음 진입에 곧바로 다시 받는다(연타 폭주는 막는다). */
    private val gameInfoRetryMs = 30 * 1000L

    /**
     * 게임 정보 새로고침 진행 여부 — **[_isRefreshing] 과 별개로 둔다.**
     *
     * 예전엔 둘이 같은 플래그였는데, 지출 탭 당겨서 새로고침([refreshSpending])이 그 플래그를 내려버려
     * 진행 중이던 게임 정보 새로고침의 중복 차단이 풀렸다. 그러면 18건짜리 요청 세트가 겹쳐 나간다.
     * [_isRefreshing] 은 UI 스피너 표시용으로 그대로 두고, 중복 차단만 이 플래그가 맡는다.
     */
    private var _gameInfoRefreshing = false

    /** 지출 탭 당겨서 새로고침 진행 여부 — 연타로 pull/push 가 겹치지 않게. */
    private var _spendingRefreshing = false

    /** 일회성 토스트 메시지 (UI 가 소비 후 clearStatus 호출) */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatus() { _statusMessage.value = null }
    private fun emitStatus(msg: String) { _statusMessage.value = msg }
    /** UI 에서 직접 토스트를 띄울 때 (예: 뒤로가기 종료 안내) */
    fun showStatus(msg: String) = emitStatus(msg)

    /** 네트워크 미연결 경고 — 토스트가 아닌 **얼럿 모달**로 표시(메시지 != null 이면 노출). UI 가 확인 후 [clearNetworkAlert]. */
    private val _networkAlert = MutableStateFlow<String?>(null)
    val networkAlert: StateFlow<String?> = _networkAlert.asStateFlow()
    fun clearNetworkAlert() { _networkAlert.value = null }
    private fun emitNetworkAlert() { _networkAlert.value = "인터넷에 연결되어 있지 않아요.\n연결 상태를 확인한 뒤 다시 시도해주세요." }

    /** 읽은 알림 키 집합(안정 키 — 가변 메시지 아님). 기기 재진입에도 유지되도록 prefs 영구 저장(로컬 전용). */
    private val _readAlerts = MutableStateFlow<Set<String>>(emptySet())
    val readAlerts: StateFlow<Set<String>> = _readAlerts.asStateFlow()
    fun markAlertsRead(keys: Collection<String>) {
        val next = _readAlerts.value + keys
        if (next != _readAlerts.value) {
            _readAlerts.value = next
            repo.saveReadAlerts(next)
        }
    }

    /** 사용자가 삭제(dismiss)한 홈 알림 키 집합 — 계산형 알림이라 조건이 유지돼도 다시 안 뜨게 영구 저장(로컬 전용). */
    private val _dismissedAlerts = MutableStateFlow<Set<String>>(emptySet())
    val dismissedAlerts: StateFlow<Set<String>> = _dismissedAlerts.asStateFlow()
    /** 알림 1건 삭제 — 키를 dismiss 집합에 추가하고 영구 저장. */
    fun dismissAlert(key: String) {
        val next = _dismissedAlerts.value + key
        if (next != _dismissedAlerts.value) {
            _dismissedAlerts.value = next
            repo.saveDismissedAlerts(next)
        }
    }
    /** 알림 여러 건 삭제(전체 삭제용). */
    fun dismissAlerts(keys: Collection<String>) {
        val next = _dismissedAlerts.value + keys
        if (next != _dismissedAlerts.value) {
            _dismissedAlerts.value = next
            repo.saveDismissedAlerts(next)
        }
    }

    // ----------------------------------------------------------------- 인앱 업데이트 확인
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /**
     * 강제 업데이트 여부 — 현재 버전이 매니페스트의 minVersionCode 미만.
     * true 면 UI 는 닫을 수 없는 업데이트 화면을 띄운다(데이터 꼬임 방지·구버전 유지보수 종료).
     */
    private val _forceUpdate = MutableStateFlow(false)
    val forceUpdate: StateFlow<Boolean> = _forceUpdate.asStateFlow()

    /** 원격 version.json 과 현재 버전 비교. [manual] 이면 최신일 때 토스트로 알림. */
    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            val info = UpdateChecker.check()
            if (info != null) {
                _updateInfo.value = info
                _forceUpdate.value = UpdateChecker.currentVersionCode() < info.minVersionCode
            } else if (manual) emitStatus("이미 최신 버전이에요")
        }
    }

    /** 강제 업데이트일 땐 닫히지 않는다(구버전 계속 사용 차단). */
    fun dismissUpdate() { if (!_forceUpdate.value) _updateInfo.value = null }

    /** 인앱 업데이트 다운로드 진행률(0~1). null = 진행 중 아님. */
    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    /**
     * 인앱 업데이트 실행 — 플랫폼별 동작은 [platformStartInAppUpdate] 에 위임한다.
     * (Android: APK 다운로드+설치 / iOS: 릴리스 페이지 URL 열기)
     */
    fun startInAppUpdate() {
        val info = _updateInfo.value ?: return
        _updateInfo.value = null // 다이얼로그 닫기
        platformStartInAppUpdate(
            info,
            onProgress = { _updateProgress.value = it },
            onStatus = { emitStatus(it) },
        )
    }

    /** 현재 출석 처리 중인 게임 키 (버튼 진행 표시용). null 이면 진행 중 아님. */
    private val _checkingIn = MutableStateFlow<String?>(null)
    val checkingIn: StateFlow<String?> = _checkingIn.asStateFlow()

    /**
     * ennead.cc 배너·이벤트 + (연동 시) HoYoLAB 실시간 노트 새로고침.
     *
     * 최적화: ①**in-flight 가드** — 이미 새로고침 중이면 즉시 반환(초기 로드+PTR+수동 버튼 동시 호출 방지).
     * ②**병렬화** — ennead(게임별)·ZZZ·HoYoLAB(게임별 note/ledger/combat 체인)을 `async` 로 동시 실행.
     * HoYoLAB 은 같은 게임 내부 3콜은 순차 유지하되 **게임 간**으로 병렬화(단일 호스트 레이트리밋 보호).
     * ③**try/finally** — 예외가 나도 `_isRefreshing` 가 반드시 해제(과거엔 예외 시 무한 새로고침 갇힐 위험).
     */
    /**
     * 게임 정보(배너·실시간 노트·월간 원장·전투) 새로고침.
     *
     * 최적화: 홈/오늘 할 일이 의존하는 **배너 + 실시간 노트를 먼저 동시에 로드해 즉시 표출(gameInfoReady)**,
     * 게임 정보 탭 전용인 월간 원장·전투 진행도는 뒤이어 로드한다. [force]=false 면 최근(5분 내) 성공 로드가
     * 있을 때 재요청을 생략(재진입 시 즉시 표시). PTR·새로고침 버튼·재연동·계정전환은 force=true.
     * try/finally 로 예외 시에도 _isRefreshing 해제 + 스켈레톤 영구 고착 방지.
     *
     * **부분 실패는 부분만 반영한다** — 요청은 게임마다 따로 나가는데, 예전엔 성공한 응답만 모아 상태에
     * 통째로 대입했다. 그래서 한 게임이 타임아웃 한 번 나면 그 게임의 배너·이벤트·공지가 화면에서
     * 통째로 사라졌고(= 홈·게임 정보 탭 정보 간헐 미노출), 게다가 그 회차가 '성공'으로 기록돼 5분간
     * 재요청도 생략돼서 수동 새로고침을 여러 번 하거나 앱을 재시작해야 다시 보였다.
     * 지금은 응답을 받은 게임만 갈아끼우고([mergeByGame]) 나머지는 직전 값을 유지하며,
     * ennead 가 하나라도 실패한 회차는 신선도를 [gameInfoRetryMs] 로 짧게 잡아 곧 다시 받는다.
     */
    fun refreshGameInfo(force: Boolean = false, silent: Boolean = false) {
        if (_gameInfoRefreshing) return // 동시 새로고침 차단
        if (!force && _gameInfoReady.value && currentTimeMillis() - lastGameInfoLoadAt < gameInfoFreshMs) return
        viewModelScope.launch {
            _gameInfoRefreshing = true
            // silent(백그라운드 복귀 자동 갱신)는 인디케이터를 켜지 않는다.
            // `_isRefreshing` 은 홈·지출·게임정보 세 탭의 당겨서-새로고침 표시와 새로고침 버튼
            // 비활성, 배너 스켈레톤을 한꺼번에 움직인다. 전체 갱신은 HoYoLAB 왕복까지 포함해
            // 수 초가 걸리는데, 사용자가 부른 적도 없는 갱신 때문에 앱으로 돌아올 때마다 그 시간
            // 내내 "새로고침 중"이 보였다 — 복귀가 느린 게 아니라 **느리다고 보여주고 있었다.**
            // 값은 도착하는 대로 갈아끼우므로 화면은 그대로 쓸 수 있다.
            if (!silent) _isRefreshing.value = true
            try {
                // 오프라인이면 12초 타임아웃을 기다리지 않고 즉시 안내(앱 진입·새로고침 공통).
                // 단 사용자가 부른 게 아닌 자동 갱신(silent)은 조용히 물러난다 — 백그라운드에서 돌아올
                // 때마다 오프라인 얼럿이 뜨면 방해만 된다.
                if (!NetworkMonitor.isOnline()) {
                    if (!silent) emitNetworkAlert()
                    return@launch
                }
                // ennead(공개 API·인증 불필요) 가 한 게임이라도 실패하면 신선도 캐시를 짧게 잡아 곧 재시도한다.
                // HoYoLAB 계열(노트·원장·전투)은 여기 넣지 않는다 — 쿠키 만료·미연동처럼 **계속** 실패하는
                // 사유가 흔해서, 그걸로 재시도를 걸면 탭을 오갈 때마다 18건짜리 요청 세트가 계속 나간다.
                // 그쪽은 직전 값 유지([mergeByGame])만으로 화면이 비지 않는다.
                var partial = false
                // 노트(HoYoLAB) 실패는 partial 과 따로 센다 — 아래 주석대로 지속 실패가 흔한 계열이라
                // 통째로 재시도를 걸 수 없지만, **일시적 실패까지 5분을 캐시하면** 행동력이 낡은 값으로
                // 굳어 사용자가 새로고침 버튼을 눌러야만 갱신된다. 일시 실패만 짧게 잡는다.
                noteRetry = false
                coroutineScope {
                    val cfg = _hoyolabConfig.value
                    val uids = if (cfg.isLinked) mapOf(
                        "genshin" to cfg.genshinUid,
                        "hsr" to cfg.hsrUid,
                        "zzz" to cfg.zzzUid,
                    ).filterValues { it.isNotBlank() } else emptyMap()

                    // 1) 홈/오늘 할 일 의존 최소셋 — 배너(ennead 2게임 + ZZZ) + 실시간 노트를 모두 동시에
                    // async(Dispatchers.IO) — 응답 본문 디코딩과 JSON 파싱을 메인 스레드 밖에서 한다.
                    // 예전엔 부모 컨텍스트(Main.immediate)를 그대로 상속해서, 캘린더·공지처럼 수백 KB 짜리
                    // 응답 18건의 파싱이 전부 UI 스레드에서 재개됐다. 상태 대입은 await 뒤라 그대로 메인이다.
                    val calendarGames = GameData.games.filter { it.enneadKey != null }
                    val enneadDeferred = calendarGames
                        .map { game -> async(Dispatchers.IO) { EnneadApi.fetch(game) } }
                    // ZZZ 픽업·일정 — ennead zenless 캘린더에서 배너+이벤트+도전 자동(수동 JSON 폐기, 에이전트명 한국어 매핑).
                    val zzzDeferred = async(Dispatchers.IO) { EnneadApi.fetchZzz() }
                    val noteDeferred = uids.map { (key, uid) ->
                        async(Dispatchers.IO) { HoyolabApi.getLiveNote(cfg.ltuid, cfg.ltoken, key, uid) }
                    }
                    // 행동력은 **캘린더를 기다리지 않는다.** 예전엔 ennead 6게임을 전부 await 하고
                    // 배너·이벤트·도전을 저장한 뒤에야 노트를 대입해서, 데일리 최상단 카드가
                    // 자기 응답이 진작 도착한 뒤에도 몇 초를 더 기다렸다. 요청은 어차피 위에서
                    // 다 쏴 뒀으니 수확만 따로 떼어 낸다.
                    val notesJob = launch {
                        val results = noteDeferred.map { it.await() }
                        if (results.any { it.note == null && it.transient }) noteRetry = true
                        val notes = results.mapNotNull { it.note }
                        if (notes.isNotEmpty()) {
                            lastLiveNoteAt = currentTimeMillis()
                            _liveNotes.value = mergeByGame(_liveNotes.value, notes, notes.map { it.game }.toSet()) { it.game }
                                .sortedByGameOrder { it.game }
                            // 행동력이 가득 차는 시각을 알림 예약에 쓰려면 로컬 캐시가 필요하다(네트워크 없이 계산).
                            // 캐시 쓰기는 IO 로 — 화면에 값은 이미 올라갔고, 여기서 메인을 잡을 이유가 없다.
                            withContext(Dispatchers.IO) { runCatching { repo.saveLiveNotes(_liveNotes.value) } }
                            recordTaskProgress(notes)
                        }
                    }
                    // 공지도 **여기서 같이 쏜다**(await 만 아래에서). 예전엔 배너·노트를 다 받은 뒤에야
                    // 요청이 나가서, 홈의 '게임 소식'만 한 왕복 늦게 채워졌다 — 의존 관계가 전혀 없는데도.
                    val newsGames = GameData.games.filter { it.newsSource != null }
                    val newsDeferred = newsGames.map { g -> async(Dispatchers.IO) { NewsApi.notices(g) } }
                    // 버전 특별 방송 확정 공지는 notices 가 아니라 info 카테고리에 올라온다.
                    // 목록에는 안 섞고 방송 일시만 뽑아 쓴다([BroadcastSchedule.parseConfirmed]).
                    val infoDeferred = newsGames.map { g -> async(Dispatchers.IO) { NewsApi.info(g) } }

                    // 응답을 받은 게임만 새 값으로 갈아끼운다([mergeByGame]) — 실패한 게임은 직전 값 유지.
                    // 예전엔 성공분만 모아 통째로 대입해서, 한 게임이 타임아웃 나면 그 게임 정보가 사라졌다.
                    val calendarLoaded = mutableSetOf<String>()
                    val banners = mutableListOf<GachaBanner>()
                    val events = mutableListOf<GameEvent>()
                    val challenges = mutableListOf<GameChallenge>()
                    calendarGames.forEachIndexed { i, game ->
                        val r = enneadDeferred[i].await()
                        if (r == null) { partial = true; return@forEachIndexed }
                        calendarLoaded += game.displayName
                        banners += r.banners
                        events += r.events
                        challenges += r.challenges
                    }
                    val zzz = zzzDeferred.await()
                    if (zzz == null) partial = true else {
                        calendarLoaded += Game.ZZZ.displayName
                        banners += zzz.banners; events += zzz.events; challenges += zzz.challenges
                    }
                    if (calendarLoaded.isNotEmpty()) {
                        // 종료 미정(end_time 미공지)은 임박도를 알 수 없으니 맨 뒤로 — dDay 가 큰 음수라 앞으로 튄다.
                        _activeBanners.value = mergeByGame(_activeBanners.value, banners, calendarLoaded) { it.game }
                            .filter { it.game in SCHEDULE_GAMES }
                            .sortedWith(compareBy({ it.isEndUnknown }, { it.dDay() }))
                        // 백그라운드 픽업 마감 알림 점검용 로컬 캐시(네트워크 없이 판정).
                        withContext(Dispatchers.IO) { runCatching { repo.saveActiveBanners(_activeBanners.value) } }
                        refreshPlans()   // 새 픽업 목록으로 저축 계획 갱신
                        _gameEvents.value = mergeByGame(_gameEvents.value, events, calendarLoaded) { it.game }
                            .filter { it.game in SCHEDULE_GAMES }
                            .sortedBy { it.endMillis }
                        _challenges.value = mergeByGame(_challenges.value, challenges, calendarLoaded) { it.game }
                            .filter { it.game in SCHEDULE_GAMES }
                            .sortedBy { it.endMillis }
                        // 다음 실행 때 홈 '이번주 일정'을 네트워크 없이 바로 그리기 위한 캐시(배너와 동일).
                        withContext(Dispatchers.IO) {
                            runCatching { repo.saveGameEvents(_gameEvents.value); repo.saveChallenges(_challenges.value) }
                        }
                    }
                    // 전부 실패해 값이 없더라도 스켈레톤은 걷는다 — 안 그러면 영원히 로딩처럼 보인다.
                    _scheduleReady.value = true

                    // ★ 배너+노트까지면 홈/오늘 할 일 준비 완료 — 즉시 표출(원장·전투는 뒤이어)
                    notesJob.join()
                    _gameInfoReady.value = true

                    // 게임 공지·뉴스(공개 API·인증 불필요) — 위에서 이미 쏴 둔 요청을 여기서 수확한다.
                    val newsLoaded = mutableSetOf<String>()
                    val news = mutableListOf<NewsItem>()
                    newsGames.forEachIndexed { i, game ->
                        // 실패(null)한 게임은 직전 공지를 유지한다 — 빈 목록으로 합쳐지면 '공지 없음'처럼 보인다.
                        val list = newsDeferred[i].await()
                        if (list == null) { partial = true; return@forEachIndexed }
                        newsLoaded += game.displayName
                        news += list
                    }
                    if (newsLoaded.isNotEmpty()) {
                        _gameNews.value = mergeByGame(_gameNews.value, news, newsLoaded) { it.game }
                            .sortedByDescending { it.createdAtMillis }
                        // 다음 실행 때 홈 '게임 소식'을 바로 그리기 위한 캐시(최신 N건·요약 절단 — 저장부 참고).
                        withContext(Dispatchers.IO) { runCatching { repo.saveGameNews(_gameNews.value) } }
                    }
                    // 확정 방송 — 못 받아도 조용히 넘어간다. 그때는 역산 예상값이 그대로 쓰인다.
                    val infoItems = infoDeferred.mapNotNull { it.await() }.flatten()
                    if (infoItems.isNotEmpty()) {
                        _confirmedBroadcasts.value = BroadcastSchedule.parseConfirmed(infoItems)
                    }
                    _newsReady.value = true

                    // 2) 게임 정보 탭 전용 — 월간 원장 + 전투 진행도(게임 간 병렬, 게임 내 순차로 단일 호스트 보호)
                    if (uids.isNotEmpty()) {
                        val rest = uids.map { (key, uid) ->
                            async(Dispatchers.IO) {
                                val ledger = HoyolabApi.getMonthlyLedger(cfg.ltuid, cfg.ltoken, cfg.webCookie, key, uid)?.takeIf { it.hasData }
                                val combat = HoyolabApi.getCombat(cfg.ltuid, cfg.ltoken, key, uid)
                                ledger to combat
                            }
                        }
                        val ledgers = mutableListOf<MonthlyLedger>()
                        val combats = mutableListOf<CombatMode>()
                        rest.forEach { d ->
                            val (ledger, combat) = d.await()
                            ledger?.let { ledgers += it }
                            combats += combat
                        }
                        if (ledgers.isNotEmpty()) {
                            _ledgers.value = mergeByGame(_ledgers.value, ledgers, ledgers.map { it.game }.toSet()) { it.game }
                                .sortedByGameOrder { it.game }
                        }
                        if (combats.isNotEmpty()) {
                            _combat.value = mergeByGame(_combat.value, combats, combats.map { it.game }.toSet()) { it.game }
                                .sortedByGameOrder { it.game }
                            // 백그라운드 시즌 마감 알림이 네트워크 없이 판정하도록 로컬 캐시(배너 캐시와 동일 패턴).
                            runCatching { repo.saveCombatModes(_combat.value) }
                        }
                    }
                }
                // 전부 성공했을 때만 5분간 재요청을 생략한다. 일부라도 빠졌으면 짧게 잡아 다음 진입에 다시 받는다 —
                // 예전엔 실패해도 5분을 캐시해서, 빠진 정보가 그 시간 동안 수동 새로고침 전까지 안 채워졌다.
                lastGameInfoLoadAt = currentTimeMillis() -
                    if (partial || noteRetry) gameInfoFreshMs - gameInfoRetryMs else 0L
            } finally {
                _gameInfoRefreshing = false
                if (!silent) _isRefreshing.value = false
                // 예외·오프라인으로 중간에 빠져나가도 스켈레톤이 영구 고착되지 않게 게이트를 모두 연다.
                _gameInfoReady.value = true
                _scheduleReady.value = true
                _newsReady.value = true
                // 예약 알림은 여기서 **한 번만** 갱신한다. 예전엔 배너·노트·전투 각 단계에서 따로 불러
                // 새로고침 1회에 3번 돌았고, 매번 prefs 4키를 읽고 대기 알림을 최대 48건 교체했다.
                rescheduleTimedAlerts()
            }
        }
    }

    /**
     * 지출 탭 당겨서 새로고침.
     *
     * ⚠️ 블로커 수정: 저장/삭제/수정 직후(디바운스 푸시 대기 중) PTR 하면, pull→import 가 아직 클라우드에
     * 반영 안 된 로컬 변경을 옛 스냅샷으로 덮어써 변경이 사라지던 문제. → **미반영 로컬 변경이 있으면
     * pull 로 덮어쓰지 않고 먼저 push(flush)** 하고, 없을 때만 pull+병합(호요랩 토큰 등 자가복구) 후 재업로드.
     * pull/push 는 오프라인 멈춤 방지를 위해 타임아웃으로 감싼다.
     */
    fun refreshSpending() {
        if (_spendingRefreshing) return   // 당겨서 새로고침 연타 = pull/push 동시 실행 + loadAll 중복
        viewModelScope.launch {
            _spendingRefreshing = true
            _isRefreshing.value = true
            try {
            if (cloudConfigured && !NetworkMonitor.isOnline()) {
                // 오프라인 — 클라우드 동기화는 건너뛰고 로컬만 갱신하며 안내.
                emitNetworkAlert()
            } else if (cloudConfigured) {
                CloudSync.currentUid()?.let { uid ->
                    val hasPendingLocal = syncJob?.isActive == true // 디바운스 푸시 대기 = 미반영 로컬 변경
                    syncJob?.cancel()
                    if (hasPendingLocal) {
                        // 로컬 변경을 먼저 클라우드에 반영(PTR 이 옛 클라우드로 덮어쓰지 않게)
                        withTimeoutOrNull(SYNC_TIMEOUT_MS) { cloudPush(uid) }
                    } else {
                        // pull 실패(네트워크)면 push 금지 — 빈/구 로컬로 클라우드 덮어쓰기 방지.
                        when (val outcome = withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.pullOutcome(uid) }) {
                            is CloudSync.PullOutcome.Loaded -> {
                                outcome.json?.let { repo.importSnapshotJson(it) }
                                carryOverGuestHoyolab()
                                // cloudPush 는 repo(prefs)에서 직접 스냅샷을 만들므로 여기서 loadAll() 을
                                // 먼저 돌릴 필요가 없다. 아래에서 어차피 한 번 부른다.
                                withTimeoutOrNull(SYNC_TIMEOUT_MS) { cloudPush(uid) }
                            }
                            else -> emitNetworkAlert()
                        }
                    }
                }
            }
            // 어느 분기로 왔든 여기서 한 번만 — 예전엔 분기 안에서도 불러 PTR 1회에 loadAll() 이 두 번 돌았다.
            // loadAll() 은 prefs 20여 키 파싱 + 저장소 읽기 다수를 포함한다.
            loadAll()
            } finally {
                // 예외로 빠져나가도 가드를 반드시 푼다 — 안 그러면 새로고침이 영구히 막힌다.
                _isRefreshing.value = false
                _spendingRefreshing = false
            }
        }
    }

    /** 출석체크 시도. HoYoLAB 연동 시 실제 API 호출, 미연동 시 로컬 수동 토글. */
    fun attemptCheckIn(gameKey: String) {
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) {
            toggleAttendance(gameKey)
            emitStatus("수동 출석 처리 (HoYoLAB 미연동)")
            return
        }
        viewModelScope.launch {
            _checkingIn.value = gameKey
            try {
                val r = HoyolabApi.checkIn(cfg.ltuid, cfg.ltoken, gameKey)
                if (r.success) markCheckedIn(gameKey)
                emitStatus(r.message)
            } finally {
                _checkingIn.value = null
            }
        }
    }

    /** 전체 출석 한번에 — 오늘 미출석 게임을 순차로 체크인(연동 시 실제 API, 미연동 시 로컬 토글). */
    fun checkInAll() {
        val done = attendanceToday.value
        val pending = GameData.attendanceGames.filter { it.key !in done }
        if (pending.isEmpty()) {
            emitStatus("오늘 출석을 모두 완료했어요")
            return
        }
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) {
            pending.forEach { toggleAttendance(it.key) }
            emitStatus("수동 출석 ${pending.size}건 처리 (HoYoLAB 미연동)")
            return
        }
        if (_checkingIn.value != null) return // 이미 진행 중
        viewModelScope.launch {
            var ok = 0
            try {
                for (g in pending) {
                    _checkingIn.value = g.key
                    val r = HoyolabApi.checkIn(cfg.ltuid, cfg.ltoken, g.key)
                    if (r.success) { markCheckedIn(g.key); ok++ }
                }
            } finally {
                _checkingIn.value = null
            }
            emitStatus(if (ok == pending.size) "전체 출석 완료 — ${ok}개" else "출석 ${ok}/${pending.size} 완료 (일부 실패)")
        }
    }

    // ----------------------------------------------------------------- 선물코드 (자동 수집 + 교환)
    private val _redeemState = MutableStateFlow<RedeemState>(RedeemState.Idle)
    val redeemState: StateFlow<RedeemState> = _redeemState.asStateFlow()

    /** 자동 수집된 활성 코드. */
    private val _activeCodes = MutableStateFlow<List<GiftCode>>(emptyList())
    val activeCodes: StateFlow<List<GiftCode>> = _activeCodes.asStateFlow()
    private val _codesLoading = MutableStateFlow(false)
    val codesLoading: StateFlow<Boolean> = _codesLoading.asStateFlow()
    /** 이미 교환한 코드(목록에서 사용 표시). */
    private val _redeemedCodes = MutableStateFlow<Set<String>>(emptySet())
    val redeemedCodes: StateFlow<Set<String>> = _redeemedCodes.asStateFlow()

    /** 코드 수집 실패(네트워크·파싱). true 면 '코드 없음'이 아니라 '못 불러옴' — 화면은 재시도를 제공한다. */
    private val _codesFailed = MutableStateFlow(false)
    val codesFailed: StateFlow<Boolean> = _codesFailed.asStateFlow()

    /** 게임의 현재 활성 선물코드를 자동 수집해 [activeCodes] 로 노출. */
    fun loadActiveCodes(gameKey: String) {
        viewModelScope.launch {
            _codesLoading.value = true
            val codes = withContext(Dispatchers.IO) { GiftCodeApi.activeCodes(gameKey) }
            // 실패(null)면 기존 목록을 지우지 않고 실패 상태만 세운다 — 빈 목록으로 위장하지 않는다.
            if (codes == null) {
                _codesFailed.value = true
            } else {
                _codesFailed.value = false
                _activeCodes.value = codes
            }
            _codesLoading.value = false
        }
    }

    private fun markRedeemed(code: String) {
        val s = _redeemedCodes.value + code.uppercase()
        _redeemedCodes.value = s
        repo.saveRedeemedCodes(s)
    }

    /** 교환 실행(검증 포함). 성공/이미사용이면 사용 표시. */
    private suspend fun doRedeem(gameKey: String, code: String): CodeResult {
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) return CodeResult(false, "HoYoLAB 연동이 필요해요")
        val uid = when (gameKey) {
            "genshin" -> cfg.genshinUid
            "hsr" -> cfg.hsrUid
            "zzz" -> cfg.zzzUid
            else -> ""
        }
        if (uid.isBlank()) return CodeResult(false, "이 게임 UID가 없어요")
        if (cfg.cookieToken.isBlank() && cfg.webCookie.isBlank()) {
            return CodeResult(false, "HoYoLAB 재연동이 필요해요 (교환 인증 쿠키 없음)")
        }
        val r = HoyolabApi.redeemCode(cfg.ltuid, cfg.ltoken, cfg.cookieToken, cfg.webCookie, gameKey, uid, code)
        // 교환 성공 또는 이미 계정 귀속(retcode -2017/-2018)이면 '받음' 표시 — 메시지 문자열 매칭 금지(확실 분기).
        if (r.success || r.alreadyRedeemed) markRedeemed(code)
        return r
    }

    /** HoYoLAB 선물코드 교환(단건). 결과는 [redeemState] 로 노출. */
    fun redeemGiftCode(gameKey: String, code: String) {
        viewModelScope.launch {
            _redeemState.value = RedeemState.Loading
            val r = doRedeem(gameKey, code.trim().uppercase())
            _redeemState.value = RedeemState.Done(r.success, r.message)
        }
    }

    /** 수집된 활성 코드 중 미교환분을 순차 교환(레이트리밋 대비 지연). */
    fun redeemAllCodes(gameKey: String) {
        val targets = _activeCodes.value.map { it.code }.filter { it !in _redeemedCodes.value }
        if (targets.isEmpty()) { _redeemState.value = RedeemState.Done(true, "교환할 새 코드가 없어요"); return }
        viewModelScope.launch {
            var ok = 0; var fail = 0; var lastFailMsg = ""
            targets.forEachIndexed { i, code ->
                _redeemState.value = RedeemState.Loading
                val r = doRedeem(gameKey, code)
                if (r.success || r.alreadyRedeemed) ok++ else { fail++; lastFailMsg = r.message }
                if (i < targets.lastIndex) delay(5500) // 교환 레이트리밋(-2016) 회피
            }
            // 실패 사유를 그대로 노출(전부 실패 시 원인 파악 — 쿠키/만료/리전 등)
            val detail = when {
                fail == 0 -> "교환 ${ok}건 완료 (우편함 확인)"
                ok == 0 -> "교환 실패 ${fail}건 — $lastFailMsg"
                else -> "교환 ${ok}건 완료 · 실패 ${fail}건 ($lastFailMsg)"
            }
            _redeemState.value = RedeemState.Done(fail == 0, detail)
        }
    }

    fun resetRedeem() { _redeemState.value = RedeemState.Idle }

    private fun markCheckedIn(gameKey: String) {
        val today = todayKey()
        val current = (attendanceMap[today] ?: emptySet()) + gameKey
        attendanceMap = attendanceMap.toMutableMap().apply { put(today, current) }
        repo.saveAttendance(attendanceMap)
        _attendanceHistory.value = attendanceMap
        _attendanceToday.value = current
        _attendanceStreak.value = computeAttendanceStreak()
    }

    // ── 파생 StateFlow(이번 달) ────────────────────────────────────────────────
    //
    // 아래 monthlyTotal()/monthlyTotalsByGame()/prevMonthTotal() 은 부를 때마다 지출 전체를 훑는다.
    // 화면(특히 홈)이 이걸 그리는 도중에 호출하고 있어 한 번 그릴 때마다 전체 스캔이 4~6회 일어났고,
    // iOS 는 그 스캔 하나하나가 KMP 브리지 왕복이라 비용이 더 컸다. 값이 바뀔 때 한 번만 계산해 흘려보낸다.
    //
    // 계산 함수 자체는 그대로 둔다 — 특정 연/월을 지정하는 호출(월별 추이 차트 등)이 남아 있고,
    // 화면 코드가 새 StateFlow 로 옮겨가지 않은 곳도 지금까지처럼 동작해야 한다.
    private val _currentMonthTotal = MutableStateFlow(0L)

    /** 이번 달 총 지출. */
    val currentMonthTotal: StateFlow<Long> = _currentMonthTotal.asStateFlow()

    private val _previousMonthTotal = MutableStateFlow(0L)

    /** 전월 총 지출(MoM 비교용). */
    val previousMonthTotal: StateFlow<Long> = _previousMonthTotal.asStateFlow()

    private val _currentMonthTotalsByGame = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** 이번 달 게임별 지출 합계(gameKey → 금액). */
    val currentMonthTotalsByGame: StateFlow<Map<String, Long>> = _currentMonthTotalsByGame.asStateFlow()

    private val _unlinkedSubCount = MutableStateFlow(0)

    /** 아직 정기결제로 등록되지 않은 '구독 표시' 지출 건수. */
    val unlinkedSubCount: StateFlow<Int> = _unlinkedSubCount.asStateFlow()

    private val _recentMonthlyTotals = MutableStateFlow<List<Long>>(emptyList())

    /** 최근 [RECENT_MONTHS] 개월 총 지출 — 오래된 달 → 이번 달 순. 마이페이지 월별 추이 차트용. */
    val recentMonthlyTotals: StateFlow<List<Long>> = _recentMonthlyTotals.asStateFlow()

    /**
     * '이번 달' 파생값을 다시 계산한다.
     *
     * 호출 지점을 늘리지 않으려고 [init] 에서 _spendings·_subscriptions 를 구독해 자동으로 돌리고,
     * [loadAll] 끝에서 **동기로 한 번 더** 부른다. 구독 콜백은 한 런루프 뒤에 오는데, 그 사이에
     * 화면이 먼저 그려지면 첫 프레임에 0원이 스쳤다가 바뀐다 — 시작 직후엔 그 틈이 보인다.
     *
     * 계산 자체는 [SpendingDerived] 가 **한 번의 순회**로 전부 만든다. 예전엔 이 함수가 다섯 값을
     * 각각 계산해서 지출 전체를 8번 훑었다(항목마다 날짜 변환까지). 순수 함수로 빼 둔 덕에
     * commonTest 로 회귀를 잡을 수 있다 — 이 ViewModel 자체는 플랫폼 저장소 의존이라 테스트가 안 된다.
     */
    private fun recomputeSpendingDerived() {
        val d = SpendingDerived.compute(
            spendings = _spendings.value,
            subscriptions = _subscriptions.value,
            year = currentYear,
            month = currentMonth,
            recentMonths = RECENT_MONTHS,
        )
        _currentMonthTotal.value = d.currentMonthTotal
        _previousMonthTotal.value = d.previousMonthTotal
        _currentMonthTotalsByGame.value = d.currentMonthTotalsByGame
        _unlinkedSubCount.value = d.unlinkedSubCount
        _recentMonthlyTotals.value = d.recentMonthlyTotals
    }

    // ----------------------------------------------------------------- 파생 통계
    fun monthlyTotal(year: Int = currentYear, month: Int = currentMonth): Long =
        _spendings.value.filter { DateUtil.isSameMonth(it.dateMillis, year, month) }.sumOf { it.amount }

    fun yearlyTotal(year: Int = currentYear): Long =
        _spendings.value.filter { DateUtil.isSameYear(it.dateMillis, year) }.sumOf { it.amount }

    /** 전월 총 지출(MoM 비교용). 1월이면 전년 12월. */
    fun prevMonthTotal(): Long =
        if (currentMonth == 1) monthlyTotal(currentYear - 1, 12) else monthlyTotal(currentYear, currentMonth - 1)

    fun topGameThisMonth(): String? {
        // 연/월을 람다 **밖에서** 한 번만 읽는다. currentYear·currentMonth 는 게터라
        // (get() = DateUtil.year(currentTimeMillis())) 람다 안에 두면 항목마다 시계 읽기 + 날짜 변환이
        // 두 번씩 더 붙는다 — isSameMonth 자체 비용에 더해 항목당 4회가 된다.
        val y = currentYear
        val m = currentMonth
        return _spendings.value
            .filter { DateUtil.isSameMonth(it.dateMillis, y, m) }
            .groupBy { it.gameName }
            .maxByOrNull { entry -> entry.value.sumOf { it.amount } }
            ?.key
    }

    /** CSV 내보내기용 문자열 */
    fun buildCsv(): String {
        val header = "날짜,게임,상품,금액,결제수단,태그,메모"
        val rows = _spendings.value.sortedByDescending { it.dateMillis }.map { s ->
            listOf(
                s.dateLabel,
                s.gameName,
                s.itemName,
                s.amount.toString(),
                s.paymentMethod,
                s.tags.joinToString(" "),
                s.memo,
            ).joinToString(",") { csvCell(it) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    // 출석 "오늘" = HoYoLAB 초기화 기준(베이징 UTC+8). 로컬 자정~01시(KST) 사이엔 아직 전날로 취급되어 오출석 방지.
    private fun todayKey() = DateUtil.hoyoDayKey()

    private val currentYear get() = DateUtil.year(currentTimeMillis())
    private val currentMonth get() = DateUtil.month(currentTimeMillis())

    val displayYear: Int get() = currentYear
    val displayMonth: Int get() = currentMonth

    // ----------------------------------------------------------------- 클라우드 동기화 (Firebase Firestore)
    private val cloudConfigured: Boolean get() = CloudSync.isConfigured()
    private var syncJob: Job? = null

    /** 기존 로그인 유저의 최초 클라우드 동기화(데이터 불러오는 중) 여부. 시작 시 로그인 상태면 true. */
    private val _initialSyncing = MutableStateFlow(cloudConfigured && CloudSync.currentUid() != null)
    val initialSyncing: StateFlow<Boolean> = _initialSyncing.asStateFlow()

    /** 로그아웃 진행 중 — 양 플랫폼이 오버레이 스피너를 띄우는 데 쓴다. [signOut] 참고. */
    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    /**
     * 로컬에 이미 사용자 데이터가 있는지 — 재실행 시 초기 동기화 로딩 게이트를 건너뛰는 기준.
     * 지출·구독·가챠 중 하나라도 있으면 '써 오던 기기'로 보고 앱을 즉시 보여주고, 클라우드 동기화는
     * 백그라운드로 돌린다(유실 방지 pull-전-push-금지는 그대로). 첫 로그인·재설치(로컬 없음)에서만 로딩 화면.
     */
    val hasLocalData: Boolean
        get() = _spendings.value.isNotEmpty() || _subscriptions.value.isNotEmpty() || gachaRecords.isNotEmpty()

    /** 데이터 변경 시 디바운스(1.5s) 후 Firestore 에 전체 스냅샷 푸시. */
    private fun scheduleCloudSync() {
        if (!cloudConfigured) return
        // 게스트 데이터는 절대 올리지 않는다. switchAccount(GUEST) 가 게스트 repo 에 onChange 를 다시
        // 붙이는데, 로그아웃이 타임아웃돼 Firebase uid 가 아직 살아있으면 게스트 스냅샷이 직전 계정
        // 문서를 덮어쓸 수 있다. 계정 기준으로 먼저 차단해 그 레이스를 원천 차단.
        if (account.value.isGuest) return
        val uid = CloudSync.currentUid() ?: return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(1500)
            cloudPush(uid)
        }
    }

    /**
     * 대기 중인 디바운스 push 를 **즉시** 밀어낸다 — 로그아웃처럼 인증이 끊기기 직전에 호출한다.
     *
     * [scheduleCloudSync] 는 1.5초 디바운스라, 마지막 편집 직후 로그아웃하면 아직 delay 중인
     * syncJob 이 인증 해제와 함께 무의미해져 **그 1.5초 안의 변경분이 유실**됐다.
     * (2026-07-03 지출 유실 사고와 같은 계열의 구멍)
     *
     * 디바운스 대기는 취소하되 push 자체는 지금 수행한다 — job.join() 으로 기다리면 남은 delay 만큼
     * 로그아웃이 늦어지므로, 대기를 건너뛰고 곧바로 올리는 편이 빠르고 결과도 같다.
     */
    private suspend fun flushPendingCloudSync() {
        val job = syncJob ?: return
        if (!job.isActive) return
        job.cancel()                       // 남은 delay 는 버리고
        syncJob = null
        if (!cloudConfigured) return
        if (account.value.isGuest) return
        val uid = CloudSync.currentUid() ?: return
        withTimeoutOrNull(SYNC_TIMEOUT_MS) { cloudPush(uid) }   // 지금 올린다
    }

    /** 마지막으로 Firestore 에 성공적으로 push 한 스냅샷(중복 쓰기 생략용). */
    private var lastPushedSnapshot: String? = null

    /**
     * 전체 스냅샷을 Firestore 에 push 하는 단일 경로.
     *  - 중복 방지: 직전 성공 push 와 내용이 같으면 쓰기를 생략(테마 변경·재로딩 등 무변화 churn 절감).
     *  - 1MB 한도 경고: Firestore 문서 한도(1MB)에 근접하면 사용자에게 미리 안내(초과 시 백업이 조용히 중단되는 것 예방).
     *  - 실패 시 lastPushedSnapshot 을 갱신하지 않아 다음 변경에서 재시도된다.
     */
    private suspend fun cloudPush(uid: String): Boolean {
        // 스냅샷은 **한 번만** 만들어 전체 문서(문자열)와 섹션 분해(객체)에 함께 쓴다.
        // 예전엔 exportSnapshotJson() 과 exportCloudSections() 가 각자 스냅샷을 만들어,
        // push 1회에 스냅샷 생성 2회 + 전량 재파싱 1회가 일어났다(수백 KB 를 메인 스레드에서).
        //
        // 그리고 **이 작업 전체가 IO 로 간다.** 스냅샷 생성은 저장된 JSON 문자열을 전부 파싱하고
        // 결과를 다시 직렬화하는 일이라 수백 KB 급인데, viewModelScope 가 Main.immediate 라
        // 지출을 저장할 때마다 1.5초 뒤 그게 UI 스레드에서 돌고 있었다.
        // (직렬화 형식 자체는 건드리지 않는다 — lastPushedSnapshot 비교와 Firestore 중복 쓰기
        //  생략이 바이트 동일성에 걸려 있다.)
        val (snapshot, json) = withContext(Dispatchers.IO) {
            val snap = repo.exportSnapshot()
            snap to snap.toString()
        }
        if (json == lastPushedSnapshot) return true   // 변경 없음 → write 생략
        if (json.length > CLOUD_DOC_WARN_BYTES) {
            emitStatus("클라우드 백업 용량이 한계에 근접했어요 (${json.length / 1024}KB / 1MB) — 오래된 뽑기 기록 정리를 권장해요")
        }
        // 섹션 분해도 직렬화라 IO. 위 조기 반환 뒤에 두어 무변화 push 에서는 아예 돌지 않는다.
        val s = withContext(Dispatchers.IO) { repo.exportCloudSections(snapshot) }
        val ok = CloudSync.push(uid, json, s.userInfo, s.spending, s.gameInfo)
        if (ok) lastPushedSnapshot = json
        return ok
    }

    /**
     * 게스트 상태에서 연동해둔 HoYoLAB 정보를 계정으로 승계.
     * 신규 계정(클라우드 비어 있음 + 계정에 연동 없음)일 때만, 게스트 연동이 있으면 채운다.
     * 기존 계정 데이터를 덮어쓰지 않으므로 안전. enka 프로필 UID 도 함께 승계.
     */
    private fun carryOverGuestHoyolab() {
        if (repo.loadHoyolab().isLinked) return
        val guest = GatchaRepository(Account.GUEST.id)
        val guestCfg = guest.loadHoyolab()
        if (!guestCfg.isLinked) return
        repo.saveHoyolab(guestCfg)
        _hoyolabConfig.value = guestCfg
        val gi = guest.loadEnkaGiUid()
        val hsr = guest.loadEnkaHsrUid()
        if (gi.isNotBlank() || hsr.isNotBlank()) {
            repo.saveEnkaUids(gi.ifBlank { repo.loadEnkaGiUid() }, hsr.ifBlank { repo.loadEnkaHsrUid() })
            _enkaGiUid.value = repo.loadEnkaGiUid()
            _enkaHsrUid.value = repo.loadEnkaHsrUid()
        }
    }

    /**
     * 로그인/시작 시 클라우드에서 끌어와 로컬에 병합한 뒤, 병합 결과를 다시 업로드해 일관 상태로 자가 복구.
     *
     * - 레이스 방지: 로그인 직후 예약된 디바운스 푸시가 pull 완료 전에 빈 스냅샷으로 클라우드를 덮어쓰지 않도록 취소.
     * - 자가 복구: import 는 원격에 있는 키만 덮어쓰므로(로컬 전용 키는 보존), 원격에서 빠진 호요랩 토큰이
     *   로컬에 남아 있으면 그대로 보존 → 재업로드 시 클라우드에 복구된다.
     */
    private suspend fun cloudSyncPullOrSeed(quiet: Boolean = false) {
        if (!cloudConfigured) { if (!quiet) _initialSyncing.value = false; return }
        val uid = CloudSync.currentUid() ?: run { if (!quiet) _initialSyncing.value = false; return }
        // 로딩 페이지 오프라인 분기: 8초 타임아웃을 기다리지 않고 즉시 로컬로 진행 + 얼럿 안내.
        // quiet(백그라운드 복귀 갱신)은 로딩 게이트도 얼럿도 띄우지 않는다 — 화면을 그대로 두고 값만 바꾼다.
        if (!NetworkMonitor.isOnline()) {
            if (quiet) return
            emitNetworkAlert()
            loadAll()
            _initialSyncing.value = false
            return
        }
        if (!quiet) _initialSyncing.value = true
        syncJob?.cancel()
        try {
            // pull 은 성공(문서 없음 포함)과 실패(네트워크/타임아웃)를 구분한다. **실패면 push 를 생략**해
            // 멀쩡한 클라우드를 빈/구 로컬로 덮어쓰지 않는다(이번 유실 사고 재발 방지).
            when (val outcome = withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.pullOutcome(uid) }) {
                is CloudSync.PullOutcome.Loaded -> {
                    outcome.json?.let { repo.importSnapshotJson(it) }
                    // 원격/계정에 호요랩 연동이 없고 게스트에 있으면 계정으로 승계(귀속 누락 복구)
                    carryOverGuestHoyolab()
                    loadAll()
                    // 병합 결과를 다시 업로드 → 유실됐던 호요랩 토큰 등을 클라우드에 자가 복구
                    withTimeoutOrNull(SYNC_TIMEOUT_MS) { cloudPush(uid) }
                }
                else -> {
                    // Failed 또는 타임아웃(null) → 상태 불명이므로 push 금지, 로컬 유지 + 안내.
                    if (!quiet) { emitNetworkAlert(); loadAll() }
                }
            }
        } finally {
            if (!quiet) _initialSyncing.value = false
        }
    }

    private companion object {
        /**
         * 게임 일정을 받아오는 게임(표시명) — 그 밖의 항목은 병합 뒤 걸러낸다.
         *
         * [mergeByGame] 은 "응답이 없는 게임은 직전 값 유지"가 원칙이라, 일정 소스를 뗀 게임의
         * 옛 항목이 캐시에 남아 **영원히 사라지지 않는다**(명조 일정을 걷어냈는데도 계속 보였다).
         * 소스가 있는 게임만 통과시켜 캐시도 다음 저장 때 자연히 정리되게 한다.
         */
        val SCHEDULE_GAMES: Set<String> =
            GameData.games.filter { it.enneadKey != null || it == Game.ZZZ }.map { it.displayName }.toSet()

        /** 클라우드 pull/push 최대 대기(ms). 오프라인 등으로 응답 없을 때 로딩 화면 갇힘 방지. */
        const val SYNC_TIMEOUT_MS = 8_000L
        /** Firestore 문서 1MB 한도 근접 경고 임계치(바이트). 초과 시 set 이 실패해 백업이 조용히 멈추므로 미리 안내. */
        const val CLOUD_DOC_WARN_BYTES = 900_000
        /** 포그라운드 알림 점검 최소 간격(ms) — 탭 전환마다 HoYoLAB 을 두드리지 않도록. */
        const val FOREGROUND_CHECK_MIN_INTERVAL_MS = 15L * 60 * 1000

        /**
         * 캘린더·공지·원장·전투의 최대 나이. 이보다 묵었으면 포그라운드 복귀 때 전체를 다시 받는다.
         * 상류가 하루 단위로 바뀌는 자료라, 앱 전환마다 20여 건을 다시 부를 이유가 없다.
         */
        const val GAME_INFO_MAX_AGE_MS = 30L * 60 * 1000

        /**
         * 실시간 노트(행동력)의 최대 나이. 이보다 묵었으면 **노트 3건만** 따로 받는다.
         *
         * 짧게 잡을수록 숫자가 정확해지지만 HoYoLAB 을 자주 두드린다. 5분이면 원신 레진 기준
         * 0.6개 차이라 "가득 찼나"를 오판할 만큼은 아니다.
         */
        const val LIVE_NOTE_MAX_AGE_MS = 5L * 60 * 1000

        /**
         * 클라우드 스냅샷의 최대 나이 — 게임 자료와 **따로 잡는다.**
         *
         * 폰 두 대를 번갈아 쓰면 한쪽에서 추가한 지출이 다른 쪽에 안 보인다는 지적이 반복됐다.
         * 기기를 바꿔 드는 데는 1분도 안 걸린다. 클라우드 pull 은 문서 1건이라 게임 API 20여 건과
         * 비용이 다르므로 문턱도 달라야 한다.
         */
        const val CLOUD_MAX_AGE_MS = 60L * 1000

        /** Enka 가 보유 캐릭터를 주는 게임 — 나머지는 상류가 주지 않는다. */
        val ENKA_GAMES = listOf("genshin", "hsr", "zzz")

        /** 엔드 콘텐츠 클리어 편성 신선도(ms). 시즌 단위로 바뀌는 데이터라 넉넉히 잡는다. */
        const val COMBAT_CLEAR_FRESH_MS = 30L * 60 * 1000
        /** 월별 지출 추이 차트가 보여주는 개월 수. */
        const val RECENT_MONTHS = 6
    }

    /**
     * 콜드 스타트 부트스트랩: 이미 Firebase 세션이 살아있으면(앱 재실행) 바로 클라우드 동기화.
     * 세션이 없으면(재설치/데이터삭제/로그아웃) 자동 로그인하지 않고 온보딩(LoginScreen)에서
     * 사용자가 직접 'Google 로그인' 또는 '게스트'를 선택한다. 로그인 시 [signIn] → [completeSignIn] 으로 복원.
     */
    private fun bootstrapAuthAndSync() {
        if (cloudConfigured && CloudSync.currentUid() != null) {
            viewModelScope.launch { cloudSyncPullOrSeed() }
        }
    }

    // 모든 프로퍼티 초기화 후 최초 로드 (init 순서 의존성 회피)
    init {
        repo.onChange = { scheduleCloudSync() }
        loadAll()
        loadTaskStats()   // 노트를 받기 전에도 지난 완주율은 보여준다
        runCatching { _keyStatOverrides.value = repo.loadKeyStatOverrides() }
        // 지출·정기결제가 바뀌면 파생값을 자동으로 다시 계산 — 갱신 지점(13곳)마다 따로 부르지 않는다.
        viewModelScope.launch {
            combine(_spendings, _subscriptions) { _, _ -> Unit }.collect { recomputeSpendingDerived() }
        }
        bootstrapAuthAndSync()
    }
}

/** 오늘 할 일 → 게임 정보 탭 진입 시 스크롤할 섹션. NOTES=실시간 노트, BANNER=배너, PITY=천장. */
// 홈 대시보드 카드 → 게임 정보 탭의 해당 섹션으로 스크롤 앵커링.
// 레이아웃 개편(v27.32.0)으로 단독 배너/천장 섹션이 통합 '게임 일정'으로 합쳐짐 →
// NOTES(실시간 노트/출석) · SCHEDULE(통합 게임 일정=픽업+패치/이벤트) · NEWS(공지·주년).
enum class GameInfoAnchor { NOTES, SCHEDULE, NEWS, COMBAT }
