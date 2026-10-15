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
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.GachaStats
import com.gatcha.log.data.GachaDashboard
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.work.AppVisibility
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
import com.gatcha.log.data.UserProfile
import com.gatcha.log.util.fixed
import kotlin.math.roundToInt
import com.gatcha.log.data.api.usesArtifactScore
import com.gatcha.log.data.api.RosterStanding
import com.gatcha.log.data.api.metricOf
import com.gatcha.log.data.api.resolveKeyStats
import com.gatcha.log.data.api.ArtifactScoring
import com.gatcha.log.data.api.WeaponRefinement
import com.gatcha.log.data.api.EnkaResult
import com.gatcha.log.data.api.EnneadApi
import com.gatcha.log.data.api.NewsApi
import com.gatcha.log.data.api.NewsArticle
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.data.api.HoyolandApi
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.data.vm.AppUpdateController
import com.gatcha.log.data.vm.AttendanceController
import com.gatcha.log.data.vm.CharMetaController
import com.gatcha.log.data.vm.CloudSyncController
import com.gatcha.log.data.vm.CombatClearsController
import com.gatcha.log.data.vm.EnkaRosterController
import com.gatcha.log.data.vm.FeedbackController
import com.gatcha.log.data.vm.GachaRecordsController
import com.gatcha.log.data.vm.GiftCodeController
import com.gatcha.log.data.vm.HoyolandPlanController
import com.gatcha.log.data.vm.NewsArticleController
import com.gatcha.log.data.vm.NotifySettingsController
import com.gatcha.log.util.currentTimeMillis
import com.gatcha.log.util.won
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    /** 토스트 · 오류 얼럿. 다른 덩어리들이 안내 문구를 여기로 보내므로 **맨 먼저** 만든다. */
    private val feedback = FeedbackController(viewModelScope)

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

    // 계산기 → 저축 플래너 진입 요청(pendingSavingsPlanner)은 여기 있었다.
    // 저축 플래너를 기능째 걷어내며 함께 지웠다(2026-09-09).

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
                // 상세를 열려면 원본 항목이 필요하다. **목록에 그 글이 없으면 신선도와 무관하게 다시 받는다**(27.52.0) —
                // 예전엔 5~30분 전에 갱신한 목록이면 다시 받지 않아 게임 정보 탭만 열렸고, 남은 요청이 한참 뒤
                // 다른 갱신 때 갑자기 상세를 열었다.
                refreshGameInfo(force = _gameNews.value.none { it.id == arg })
                viewModelScope.launch {
                    gameInfoJob?.join()
                    // 다시 받아도 없는 글이다(내려갔거나 다른 언어권) — 요청을 비운다.
                    if (_pendingNewsId.value == arg && _gameNews.value.none { it.id == arg }) _pendingNewsId.value = null
                }
            }
            // 새 버전 알림 — 업데이트 창을 다시 띄운다(이미 떠 있던 앱은 시작 시 확인이 다시 돌지 않는다).
            "update" -> checkForUpdate()
            // 탭만 여는 알림(예산 → 지출, 출석 → 게임 정보). 표는 [NotificationRoutes].
            "tab" -> arg.toIntOrNull()?.takeIf { it in 0..3 }?.let { _pendingTab.value = it }
            // 게임 정보 탭의 그 자리로(행동력 · 일정 · 전투).
            "gameinfo" -> {
                _pendingGameInfoAnchor.value = when (arg) {
                    "notes" -> GameInfoAnchor.NOTES
                    "schedule" -> GameInfoAnchor.SCHEDULE
                    "combat" -> GameInfoAnchor.COMBAT
                    else -> null
                }
                _pendingTab.value = 2
            }
            // 호요랜드 알림 — 게임 정보 탭의 호요랜드 자리로 간다(예매 버튼이 거기 있다).
            // 27.51 까지의 「예매 1시간 전」 예약은 예매 주소(https…)를 그대로 실었다 — 남아 있는 것도 같은 자리로 보낸다.
            ScheduledAlerts.HOYOLAND_LINK, "http", "https" -> {
                _pendingGameInfoAnchor.value = GameInfoAnchor.HOYOLAND
                _pendingTab.value = 2
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
            emitSuccess("자동 출석체크를 껐어요")
            return
        }
        emitSuccess("자동 출석체크를 켰어요 — 지금 한 번 시도할게요")
        // 결과를 토스트로 즉시 피드백(완료/이미 완료/재연동 필요 등). 워커도 같은 Runner 를 쓰지만
        // 여기선 알림 중복을 피하려고 postFailureNotification=false 로 끄고 토스트만 띄운다.
        accountScope.launch {
            val outcome = AutoCheckInRunner.run(
                settings = appSettings,
                repo = repo,
                cfg = repo.loadHoyolab(),
                postFailureNotification = false,
            )
            emitStatus(
                outcome?.toToastMessage()
                    ?: if (repo.loadHoyolab().isLinked) NO_UID_CHECK_IN else "HoYoLAB 연동이 안 돼 있어요",
                when {
                    outcome == null -> ToastKind.INFO
                    outcome.hasAnyFail && outcome.newSuccess.isEmpty() -> ToastKind.ERROR
                    else -> ToastKind.SUCCESS
                },
            )
        }
    }

    // 로컬 알림 토글(예산·출석·재화 …) · 방해금지 — 값을 쓰고 예약을 다시 거는 일은 [NotifySettingsController]
    private val notify = NotifySettingsController(appSettings) { rescheduleTimedAlerts() }
    val notifyBudget: StateFlow<Boolean> = notify.notifyBudget
    val notifyAttendance: StateFlow<Boolean> = notify.notifyAttendance
    val notifyResin: StateFlow<Boolean> = notify.notifyResin
    val notifyPickup: StateFlow<Boolean> = notify.notifyPickup
    val notifyNews: StateFlow<Boolean> = notify.notifyNews
    val notifyCombat: StateFlow<Boolean> = notify.notifyCombat
    val notifyHoyoland: StateFlow<Boolean> = notify.notifyHoyoland
    val notifyAppUpdate: StateFlow<Boolean> = notify.notifyAppUpdate
    val notifyDndEnabled: StateFlow<Boolean> = notify.notifyDndEnabled
    val notifyDndStartHour: StateFlow<Int> = notify.notifyDndStartHour
    val notifyDndEndHour: StateFlow<Int> = notify.notifyDndEndHour

    // 내 게임 — 온보딩 ②와 설정 ▸ 내 게임이 같은 값을 쓴다(비어 있으면 전부). 계정 데이터라 기기 간 동기화된다.
    private val _myGames = MutableStateFlow(repo.loadMyGames())
    val myGames: StateFlow<Set<String>> = _myGames.asStateFlow()
    fun setMyGames(keys: Set<String>) { repo.saveMyGames(keys); _myGames.value = keys; rescheduleTimedAlerts() }
    /** Swift 용 — SKIE 가 Set 을 NSSet 으로 넘겨 다루기 번거롭다. */
    fun setMyGamesList(keys: List<String>) = setMyGames(keys.toSet())

    // 데일리 요약 — 정한 시각에 1건 통합

    // 과소비 리플렉션 넛지(지출 추가 시점) — 토글 + 평소치 기준액
    private val _nudgeOverspend = MutableStateFlow(appSettings.nudgeOverspend)
    val nudgeOverspend: StateFlow<Boolean> = _nudgeOverspend.asStateFlow()
    private val _nudgeThreshold = MutableStateFlow(appSettings.nudgeThreshold)
    val nudgeThreshold: StateFlow<Long> = _nudgeThreshold.asStateFlow()
    fun setNudgeOverspend(v: Boolean) { appSettings.nudgeOverspend = v; _nudgeOverspend.value = v }
    fun setNudgeThreshold(v: Long) { appSettings.nudgeThreshold = v; _nudgeThreshold.value = v }

    /** 캐릭터 상세 속성 연출 ON/OFF. 기본 ON — 장식이라 취향을 타서 끌 수 있게 둔다. */
    private val _charElementFx = MutableStateFlow(appSettings.charElementFx)
    val charElementFx: StateFlow<Boolean> = _charElementFx.asStateFlow()
    fun setCharElementFx(v: Boolean) { appSettings.charElementFx = v; _charElementFx.value = v }

    // 지출 내역 컴팩트(한 줄) 표시 토글. 기본 false(기존).
    private val _spendingCompact = MutableStateFlow(appSettings.spendingCompact)
    val spendingCompact: StateFlow<Boolean> = _spendingCompact.asStateFlow()
    fun setSpendingCompact(v: Boolean) { appSettings.spendingCompact = v; _spendingCompact.value = v }

    /** 호요랜드 굿즈 장바구니 · 내 입장권 — 저장과 회차 정리는 [HoyolandPlanController]. */
    private val hoyolandPlan = HoyolandPlanController(appSettings) { rescheduleTimedAlerts() }
    val hoyolandCart: StateFlow<HoyolandCart> = hoyolandPlan.hoyolandCart

    /** 담기/빼기 — 담을 때 수량 1 로 시작한다. */
    fun toggleGoods(name: String) { hoyolandPlan.toggleGoods(name) }

    /** 굿즈 담기 수량 — 품목별 '1인 N개 한정' 에서 끊는다. */
    fun setGoodsQuantity(name: String, quantity: Int) { hoyolandPlan.setGoodsQuantity(name, quantity) }

    fun clearGoodsCart() { hoyolandPlan.clearGoodsCart() }

    val hoyolandEntry: StateFlow<HoyolandEntry> = hoyolandPlan.hoyolandEntry

    /** 그날의 조를 정한다. 같은 조를 다시 누르면 해제되어 안 가는 날로 돌아간다. */
    fun setEntryGroup(ymd: String, group: String) { hoyolandPlan.setEntryGroup(ymd, group) }

    fun clearEntry() { hoyolandPlan.clearEntry() }

    private val _collabBannerExpanded = MutableStateFlow(appSettings.collabBannerExpanded)
    val collabBannerExpanded: StateFlow<Boolean> = _collabBannerExpanded.asStateFlow()
    fun setCollabBannerExpanded(v: Boolean) { appSettings.collabBannerExpanded = v; _collabBannerExpanded.value = v }


    /**
     * N6 과소비 넛지 판정 — 지출 저장 직전 호출. 경고가 필요하면 메시지, 아니면 null.
     * 우선순위: 게임별 한도 초과 예상 → 전체 예산 초과 예상 → 단건 큰 금액(평소치 초과).
     * 수정 시엔 같은 달의 기존 금액을 빼서 순증분으로 예측(이중 합산 방지).
     */
    fun overspendNudge(game: Game, amount: Long, editingId: String? = null): String? =
        overspendNudge(game, amount, editingId, currentTimeMillis())

    /**
     * @param dateMillis 입력 중인 지출의 날짜. **이번 달이 아니면 예산 · 한도 예측을 하지 않는다**(27.52.0) —
     *   지난달 날짜로 추가해도 「이번 달 이미 N원 썼어요」가 떴다. 단건 큰 금액 경고는 날짜와 무관하게 본다.
     */
    fun overspendNudge(game: Game, amount: Long, editingId: String?, dateMillis: Long): String? {
        val hit = nudgeHit(game, amount, editingId, dateMillis) ?: return null
        return when (hit.kind) {
            NudgeKind.GAME_LIMIT -> "${game.shortName}에 이번 달 이미 ${won(hit.gameNow)} 썼어요.\n추가하면 한도 ${won(hit.gameLimit)}을 넘어요."
            NudgeKind.BUDGET -> "이번 달 이미 ${won(hit.monthNow)} 썼어요.\n추가하면 예산 ${won(hit.overall)}을 넘어요."
            NudgeKind.LARGE -> "${won(amount)}은 평소보다 큰 지출이에요.\n정말 추가할까요?"
        }
    }

    /**
     * 같은 판정의 **간결한 한 줄**(10/13) — 지출 입력 화면의 게임 칩 옆에 늘 서 있는 표시가 쓴다.
     * 경고가 필요하면 "원신 한도 초과" · "이번 달 예산 초과" · "평소보다 큰 지출", 아니면 null(화면이 강조 없는 문구를 세운다).
     * 저장 직전의 확인창은 자세한 문구([overspendNudge])를 그대로 쓴다.
     */
    fun overspendBadge(game: Game, amount: Long, editingId: String?, dateMillis: Long): String? {
        val hit = nudgeHit(game, amount, editingId, dateMillis) ?: return null
        return when (hit.kind) {
            NudgeKind.GAME_LIMIT -> "${game.shortName} 한도 초과"
            NudgeKind.BUDGET -> "이번 달 예산 초과"
            NudgeKind.LARGE -> "평소보다 큰 지출"
        }
    }

    private enum class NudgeKind { GAME_LIMIT, BUDGET, LARGE }

    private class NudgeHit(val kind: NudgeKind, val gameNow: Long, val gameLimit: Long, val monthNow: Long, val overall: Long)

    /** 과소비 판정 — [overspendNudge] · [overspendBadge] 가 같은 결과를 쓴다. 경고할 것이 없으면 null. */
    private fun nudgeHit(game: Game, amount: Long, editingId: String?, dateMillis: Long): NudgeHit? {
        if (!appSettings.nudgeOverspend || amount <= 0) return null
        val inThisMonth = DateUtil.isSameMonth(dateMillis, currentYear, currentMonth)
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
        val kind = when {
            inThisMonth && gameLimit > 0 && projectedGame > gameLimit -> NudgeKind.GAME_LIMIT
            inThisMonth && overall > 0 && projectedMonth > overall -> NudgeKind.BUDGET
            threshold > 0 && amount >= threshold -> NudgeKind.LARGE
            else -> return null
        }
        return NudgeHit(kind, gameNow, gameLimit, monthNow, overall)
    }

    fun setNotifyBudget(v: Boolean) { notify.setNotifyBudget(v) }
    fun setNotifyAttendance(v: Boolean) { notify.setNotifyAttendance(v) }
    fun setNotifyResin(v: Boolean) { notify.setNotifyResin(v) }
    fun setNotifyPickup(v: Boolean) { notify.setNotifyPickup(v) }
    fun setNotifyNews(v: Boolean) { notify.setNotifyNews(v) }
    fun setNotifyCombat(v: Boolean) { notify.setNotifyCombat(v) }
    fun setNotifyHoyoland(v: Boolean) { notify.setNotifyHoyoland(v) }
    fun setNotifyAppUpdate(v: Boolean) { notify.setNotifyAppUpdate(v) }

    fun setNotifyDndEnabled(v: Boolean) { notify.setNotifyDndEnabled(v) }
    fun setNotifyDndStartHour(v: Int) { notify.setNotifyDndStartHour(v) }
    fun setNotifyDndEndHour(v: Int) { notify.setNotifyDndEndHour(v) }

    /** OS 알림 권한을 **처음 허용**했을 때 — 항목별 알림을 전부 한꺼번에 켠다(데일리 요약·방해금지는 건드리지 않는다). */
    fun enableAllNotifyItems() { notify.enableAllNotifyItems() }

    /**
     * 확정 시각 알림(픽업·시즌 마감·정기결제·재화 가득참·데일리 요약) 사전 예약 갱신.
     * 두 플랫폼 다 OS 예약으로 건다(iOS 캘린더 트리거 · Android AlarmManager — [AlertScheduler]).
     * 그래서 앱을 안 열어도 정시에 온다.
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
    /** 행동력 지름길([refreshLiveNotesQuiet])을 마지막으로 **시도한** 시각 — 성공 여부와 무관하게 간격을 건다. */
    private var lastLiveNoteTryAt = 0L

    /** 클라우드 스냅샷을 마지막으로 당겨온 시각. */
    private var lastCloudPullAt = 0L

    /**
     * 앱이 백그라운드로 내려갈 때.
     *
     * 예전엔 내려간 시각을 적어 두고 '얼마나 자리를 비웠는가'로 갱신을 갈랐는데, 이제
     * [onAppForeground] 가 자료 나이로 판단하므로 적어 둘 게 없다. 호출부(양 플랫폼 생명주기)는
     * 그대로 두고 여기만 비운다 — 다시 필요해질 자리다.
     */
    fun onAppBackground() {
        AppVisibility.onBackground()
        // 디바운스(1.5초) 중인 변경을 지금 올린다 — 저장 직후 앱을 내리면 OS 가 프로세스를 멈춰
        // 다른 기기에 안 보였고, 그대로 앱을 지우면 사라졌다.
        viewModelScope.launch { cloud.flushPending() }
    }

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
    /** 루트 화면이 홈이면 true — 그 전(온보딩 · 로그인 · 불러오기)에는 알림을 붙잡는다([NotifyHold]). */
    fun setRootReady(ready: Boolean) {
        if (!ready) NotifyHold.hold()
        else if (NotifyHold.held) viewModelScope.launch { NotifyHold.release() }
    }

    fun onAppForeground() {
        AppVisibility.onForeground()  // 알림을 쏠지 말지의 판정 근거 — 아래 ② 점검보다 **먼저** 세운다
        DateUtil.refreshTimeZone()    // 캐시된 로컬 타임존 갱신(여행·자동 시간대 변경) — 알림 조건과 무관하게 항상
        recomputeSpendingDerived()    // 앱을 켜 둔 채 달이 바뀌면 지출은 그대로여서 '이번 달'이 안 갱신된다
        // 백그라운드 자동 출석은 설정값만 고친다 — 화면이 읽는 흐름은 여기서 맞춘다. 예전엔 홈 진입 때만 읽어
        // (iOS 는 프로세스당 한 번) 앱이 떠 있는 동안에는 만료 배너가 뜨지도 내려가지도 않았다(27.52.0).
        refreshHoyoTokenExpired()

        val now = currentTimeMillis()
        val today = todayKey()
        // 첫 호출 = 콜드 스타트. 방금 init 이 로드했고 화면 진입이 곧 조회를 건다 — 여기서 또
        // 부르면 같은 요청이 두 벌 나가고, 오프라인일 때 화면 쪽이 띄울 안내까지 silent 로 삼킨다.
        val firstForeground = lastForegroundDayKey.isEmpty()
        val dayRolled = !firstForeground && lastForegroundDayKey != today
        lastForegroundDayKey = today

        if (!firstForeground) {
            // 출석·스트릭은 [loadAll] 때 굳은 값이다. 자정을 넘겼거나, 백그라운드 자동 출석이
            // 앱이 잠든 사이에 저장소만 고쳐 놨을 수 있다 — **메모리가 아니라 저장소에서** 읽는다.
            reloadAttendance()
            // 게임 정보 전체(캘린더·공지·원장·전투). 노트도 이 안에서 함께 받으므로 아래는 건너뛴다.
            if (dayRolled || now - lastGameInfoLoadAt >= GAME_INFO_MAX_AGE_MS) {
                refreshGameInfo(force = true, silent = true)
            } else if (now - lastLiveNoteAt >= LIVE_NOTE_MAX_AGE_MS) {
                refreshLiveNotesQuiet()
            }
            // 내 캐릭터 — 호출은 매번 하되 실제 조회 여부는 내부 TTL·진행 중 판정이 가른다.
            // 예전엔 복귀 갱신에 아예 빠져 있어, 화면이 떠 있는 동안에는 탭을 새로 들어가지 않는 한
            // 영영 낡은 로스터가 보였다.
            // 복귀 때는 로스터 TTL 을 길게 잡는다 — 5분만 지나도 다시 받으면 젠레스는 에이전트마다 1건이라
            // 복귀 한 번에 50건 가까이 나가고, 같은 호스트의 노트 · 원장까지 줄을 섰다. 직접 새로고침은 그대로 5분.
            autoLoadEnkaSection(ENKA_GAMES, ttlMs = ENKA_FOREGROUND_TTL_MS)
            if (now - lastCloudPullAt >= CLOUD_MAX_AGE_MS) cloudSyncQuiet()
        }

        if (!appSettings.needsPeriodicWork()) return
        // **오늘 아직 자동 출석을 시도하지 않았다면 간격 제한을 건너뛴다.**
        //
        // iOS 는 백그라운드 실행 시각을 OS 가 정하고 앱을 강제 종료해 두면 아예 돌지 않는다.
        // 그쪽에서 확실한 기회는 **앱을 여는 순간**뿐인데, 15분 간격에 걸려 그 한 번마저
        // 건너뛰면 그날은 수동으로 눌러야 한다. 하루 한 번은 무조건 시도한다.
        // (출석은 베이징 자정에 초기화되므로 날짜 키도 베이징 기준이다.)
        val checkInDay = DateUtil.hoyoDayKey()
        val firstTryToday =
            appSettings.autoCheckIn && appSettings.lastNotified(AUTO_CHECKIN_TRY_KEY) != checkInDay
        if (!firstTryToday && now - appSettings.lastForegroundCheckMillis < FOREGROUND_CHECK_MIN_INTERVAL_MS) return
        if (firstTryToday) appSettings.setLastNotified(AUTO_CHECKIN_TRY_KEY, checkInDay)
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
    private var liveNotesJob: Job? = null

    /**
     * **지금 계정(과 지금 HoYoLAB 연동)에 딸린 작업의 스코프**(27.52.0) — 계정을 바꾸거나 연동을 풀면 통째로 끊고 새로 만든다
     * ([cancelAccountWork]). 뷰모델 스코프의 자식이라 뷰모델이 끝나면 같이 끝난다.
     *
     * 여기에 띄우는 것은 「결과를 지금 계정의 화면 · 저장소에 싣는」 네트워크 작업이다. **지출 저장([persistSpendings])과
     * 클라우드 올리기는 여기에 띄우지 않는다** — 끊기면 그 변경을 잃는다. 그쪽은 시작할 때 잡아 둔 저장소에만 쓴다.
     */
    private var accountScope: CoroutineScope = newAccountScope()
    private fun newAccountScope(): CoroutineScope =
        CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))

    private fun refreshLiveNotesQuiet() {
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) return
        if (_gameInfoRefreshing) return   // 전체 갱신이 이미 돌고 있으면 같은 요청을 두 번 쏘지 않는다
        // **진행 중이면 건너뛰고, 실패했어도 시도 간격은 지킨다**(27.52.0). 성공했을 때만 시각을 적어서, 토큰이 만료된
        // 사용자는 탭에 들어올 때마다 HoYoLAB 에 3건을 보냈다. 탭을 빠르게 오가면 같은 요청이 겹치기도 했다.
        if (liveNotesJob?.isActive == true) return
        val now = currentTimeMillis()
        if (now - lastLiveNoteTryAt < LIVE_NOTE_RETRY_MIN_MS) return
        lastLiveNoteTryAt = now
        val uids = cfg.linkedUids
        if (uids.isEmpty()) return
        liveNotesJob = accountScope.launch {
            if (!NetworkMonitor.isOnline()) return@launch
            // 전체 갱신 쪽과 같은 이유로 **도착하는 대로** 싣는다 — `awaitAll` 은 제일 느린 게임에
            // 나머지를 묶는다. 원자적 갱신([update])이어야 늦게 끝난 쪽이 남의 결과를 지우지 않는다.
            val results = uids.map { (key, uid) ->
                async(Dispatchers.IO) { key to HoyolabApi.getLiveNote(cfg.ltuid, cfg.ltoken, key, uid) }
            }
            val deferred = results.map { r -> async { r.await().second.note } }
            coroutineScope {
                results.forEach { d ->
                    launch {
                        val (key, r) = d.await()
                        recordNoteError(key, r)
                        val note = r.note ?: return@launch
                        _liveNotes.update { prev ->
                            mergeByGame(prev, listOf(note), setOf(note.game)) { it.game }
                                .sortedByGameOrder { it.game }
                        }
                    }
                }
            }
            val notes = deferred.mapNotNull { it.await() }   // 이미 완료 — 집계용
            if (notes.isEmpty()) return@launch
            lastLiveNoteAt = currentTimeMillis()
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
        if (syncUid() != null) {
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

    // 출석 상태(오늘 · 날짜별 이력 · 연속 일수) — 읽고 고치는 일은 [AttendanceController]. 출석을 보내는 일은 아래 「출석」 절.
    private val attendance = AttendanceController({ repo }) {
        rescheduleTimedAlerts()   // 출석 리마인더는 남은 출석을 보고 걸린다
    }
    val attendanceToday: StateFlow<Set<String>> = attendance.today
    /** 날짜별 출석 이력(dayKey "yyyy-MM-dd" → 출석한 게임키 집합) — 7일 스트립·월간 달력용. */
    val attendanceHistory: StateFlow<Map<String, Set<String>>> = attendance.history
    /** 연속 출석 일수(오늘 미출석이면 어제 기준으로 유지). */
    val attendanceStreak: StateFlow<Int> = attendance.streak

    /**
     * 현재 repo(계정)의 모든 데이터를 상태로 로드 — **앱 시작과 계정 전환에만** 쓴다.
     *
     * 예전엔 클라우드 동기화가 끝날 때마다 이 함수를 통째로 돌렸다(27.52.0 에서 가름). 그러면 로컬 전용 캐시까지
     * 디스크 값으로 다시 깔려서, 그사이 도착한 새로고침 결과(행동력 · 일정 · 소식)가 지난 실행 때 값으로 되돌아가고
     * '내 캐릭터' 의 빈 표시 · 오류 표시가 지워져 스켈레톤으로 남았다. 동기화 뒤에는 [reloadSyncedState] 만 돈다.
     */
    private fun loadAll() {
        reloadSyncedState()
        seedLocalCaches()
    }

    /** 스냅샷(클라우드 · 백업)이 바꿀 수 있는 값만 저장소에서 다시 읽는다. */
    private fun reloadSyncedState() {
        val enkaUidsBefore = enka.uidSnapshot()
        // 날짜 내림차순을 보장한다 — 클라우드 병합 결과는 순서가 섞여 와서, 화면마다 전체를 다시 정렬했다.
        _spendings.value = repo.loadSpendings().sortedByDescending { it.dateMillis }
        _budget.value = repo.loadBudget()
        _gameBudgets.value = repo.loadGameBudgets()
        _myGames.value = repo.loadMyGames()
        _profile.value = repo.loadProfile()
        val canCheckInBefore = _hoyolabConfig.value.canCheckIn
        _hoyolabConfig.value = repo.loadHoyolab()
        // 스냅샷은 게임 UID 를 실어 온다 — 출석을 챙길 수 있게(없게) 바뀌었으면 출석 리마인더 예약도 고쳐 건다.
        if (_hoyolabConfig.value.canCheckIn != canCheckInBefore) rescheduleTimedAlerts()
        _accentIndex.value = repo.loadAccentIndex()
        attendance.reload()
        _pity.value = repo.loadPity()
        enka.reloadAfterSync(enkaUidsBefore)   // UID 가 실제로 바뀐 게임만 화면 상태를 갈아끼운다
        gacha.loadGachaDeferred()
        giftCodes.reloadSaved()
        refreshChallenge()
        recomputeSpendingDerived()   // 첫 프레임이 0원으로 그려지지 않게 동기로 한 번
    }

    /**
     * 이 기기에만 있는 값(스냅샷에 실리지 않는 것)과 디스크 캐시를 화면 상태로 올린다.
     *
     * 유효옵션 직접 설정과 완주율도 여기서 읽는다 — 예전엔 `init` 에서만 읽어, 계정을 바꾼 뒤 캐릭터 하나를 고치면
     * 앞 계정의 지도가 새 계정 저장분을 통째로 덮었다(27.52.0).
     */
    private fun seedLocalCaches() {
        _readAlerts.value = repo.loadReadAlerts()
        _dismissedAlerts.value = repo.loadDismissedAlerts()
        _keyStatOverrides.value = runCatching { repo.loadKeyStatOverrides() }.getOrDefault(emptyMap())
        _taskStats.value = emptyList()
        loadTaskStats()   // 노트를 받기 전에도 지난 완주율은 보여준다
        enka.reload()   // UID 를 맞추고 화면 상태를 비운 뒤, 디스크 캐시로 '내 캐릭터' 를 즉시 채운다
        seedGameInfoDiskCache()
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
            // 행동력 · 전투 · 편성은 HoYoLAB 계정에 딸린 값이다 — **연동돼 있을 때만** 싣는다(27.52.0).
            // 연동 해제는 메모리만 비웠고 이 캐시는 남아서, 앱을 다시 켜면 해제한 계정의 행동력이 홈에 되살아났다.
            val linked = _hoyolabConfig.value.isLinked
            val notes = if (linked) repo.loadLiveNotes() else emptyList()
            val combats = if (linked) repo.loadCombatModes() else emptyList()
            // 클리어 편성은 전용 페이지에서만 쓰지만, 여기서 미리 채워야 페이지가 빈 화면으로 열리지 않는다.
            if (linked) runCatching { repo.loadCombatClears() }.getOrNull()
                ?.takeIf { it.isNotEmpty() }?.let { clears.seed(it) }
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

    // ----------------------------------------------------------------- 계정 (구글 로그인 — Credential Manager)
    /**
     * 구글 로그인(원탭). UI 에서 **Activity 컨텍스트**로 호출한다.
     * 계정 선택 시트를 띄워 한 번 탭하면 로그인 → Firebase 인증 → 클라우드 복원까지 진행.
     */
    fun signIn() {
        if (_onboardingPreview.value) return   // 테스트용 온보딩 — 로그인 · 클라우드 불러오기 안 함
        viewModelScope.launch {
            if (cloudConfigured) _initialSyncing.value = true
            signInInFlight = true
            try { when (val outcome = authManager.signIn(autoSelectOnly = false)) {
                is SignInOutcome.Success -> {
                    if (!completeSignIn(outcome.account)) {
                        _initialSyncing.value = false
                        emitError("네트워크 오류로 로그인에 실패했어요")
                    }
                }
                SignInOutcome.NoCredential -> { _initialSyncing.value = false; emitStatus("로그인이 취소되었거나 완료되지 못했어요") }
                is SignInOutcome.Error -> { _initialSyncing.value = false; emitError(outcome.message) }
            } } finally { signInInFlight = false }
        }
    }

    /**
     * 로그인 진행 중 — 계정은 이미 바뀌었는데 저장소는 아직 게스트 것인 구간이 있다(email 계정 → uid 계정 사이).
     * 그때 [hasLocalData] 가 게스트 데이터를 보고 게이트를 꺼서 홈 → 로딩 순으로 뒤바뀌었다(iOS, 9/29 로그로 확인).
     */
    private var signInInFlight = false

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
        applyPendingOnboarding()
        emitSuccess("${finalAcc.name}님으로 로그인되었어요")
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
                val flushed = cloud.flushPending()
                val done = withTimeoutOrNull(SYNC_TIMEOUT_MS) { authManager.signOut() } != null
                switchAccount(Account.GUEST)
                emitSuccess(
                    when {
                        // 못 올린 변경은 지워지지 않는다 — 계정 저장소에 남아 있다가 다시 로그인하면 합쳐져 올라간다.
                        !flushed -> "로그아웃했어요 — 올리지 못한 변경은 이 기기에 남아 있고, 다시 로그인하면 올라가요"
                        done -> "로그아웃되었어요"
                        else -> "로그아웃했어요 (서버 정리는 나중에 완료돼요)"
                    },
                )
            } finally {
                _signingOut.value = false
            }
        }
    }

    private fun switchAccount(acc: Account) {
        cancelAccountWork()
        // 대기 중이던 올리기도 끊는다 — 옛 계정 것이 새 계정 저장소를 올리지 않게. 올릴 것은 로그아웃 쪽이 먼저 밀어냈다.
        cloud.cancel()
        repo = GatchaRepository(acc.id)
        cloud = newCloudSync(repo)   // 올리기 상태(실패 횟수 · 알림 표시 · 마지막 스냅샷)도 계정과 함께 새로 선다
        repo.onChange = { scheduleCloudSync() }
        // 만료 배너는 기기 단위 표시다 — 계정이 바뀌면 내린다(새 계정이 만료면 다음 시도에서 다시 선다).
        clearHoyoTokenExpired()
        loadAll()
        rescheduleTimedAlerts()   // 예약 · 출석 리마인더는 계정의 캐시 · 연동으로 만든다 — 앞 계정 것을 걷는다
        // 로그인 계정이면 프로필을 구글 계정 정보로 맞춤
        if (!acc.isGuest) {
            val p = UserProfile(name = acc.name, email = acc.email)
            _profile.value = p
            repo.saveProfile(p)
        }
        refreshGameInfo(force = true)
    }

    /**
     * 계정 전환·연동 해제 — 옛 계정의 요청을 끊고 계정에 딸린 화면 상태를 비운다.
     *
     * [loadAll] 의 디스크 시드는 **새 계정 캐시가 있을 때만** 값을 갈아끼워서, 캐시가 없는 계정으로
     * 바꾸면 옛 계정의 행동력·원장·전투가 그대로 남았다. 게다가 진행 중인 새로고침이 가드에 걸려
     * force 새로고침이 버려졌고, 그 회차가 옛 토큰으로 받은 값을 새 저장소에 썼다.
     */
    private fun cancelAccountWork() {
        // 계정에 딸린 작업을 **통째로** 끊는다([accountScope]) — 게임 정보 갱신 · 행동력 · 출석 · 연동 직후의 UID 조회.
        // 예전엔 끊을 작업을 하나씩 적어 두어, 목록에 없는 작업은 계속 돌며 새 계정 저장소에 썼다.
        accountScope.cancel()
        accountScope = newAccountScope()
        clears.cancel()
        giftCodes.cancelForAccountChange()
        clearHoyolabState()
        enkaUidsSynced = false
    }

    /**
     * HoYoLAB 계정에 딸린 **디스크 캐시**를 비운다 — 연동을 해제했거나 다른 계정으로 바꿨을 때.
     * 화면 상태([clearHoyolabState])만 비우면 다음 실행의 캐시 시드가 옛 계정 값을 되살린다.
     */
    private fun clearHoyolabDiskCaches(target: GatchaRepository) {
        runCatching {
            target.saveLiveNotes(emptyList())
            target.saveCombatModes(emptyList())
            target.saveCombatClears(emptyList())
        }
    }

    /** 토큰 만료 배너를 내린다 — 연동 해제 · 계정 전환처럼 "다시 연동하라"는 말이 더는 맞지 않을 때. */
    private fun clearHoyoTokenExpired() {
        appSettings.hoyoTokenExpired = false
        _hoyoTokenExpired.value = false
    }

    /** HoYoLAB 계정에 딸린 화면 상태 — 연동이 바뀌면 다른 사람 것이 된다. */
    private fun clearHoyolabState() {
        _liveNotes.value = emptyList()
        _noteErrors.value = emptyMap()
        _ledgers.value = emptyList()
        _combat.value = emptyList()
        clears.reset()
        lastLiveNoteAt = 0L
    }

    // ----------------------------------------------------------------- 지출
    /** 저장 전 검증 — 금액 1원 ~ [Spending.MAX_AMOUNT], 게임명 필수. 어긋나면 안내하고 false. */
    private fun validSpending(s: Spending): Boolean {
        val msg = when {
            s.amount <= 0 -> "금액을 입력해 주세요"
            s.amount > Spending.MAX_AMOUNT -> "금액이 너무 커요"
            s.gameName.isBlank() -> "게임을 골라 주세요"
            else -> return true
        }
        emitError(msg)
        return false
    }

    /** 저장했으면(또는 이미 저장돼 있으면) true — 화면은 false 일 때 입력을 닫지 않는다(검증 실패로 입력이 날아가지 않게). */
    fun addSpending(spending: Spending): Boolean {
        if (!validSpending(spending)) return false
        // 같은 id 가 이미 있으면 추가하지 않는다 — 저장 버튼 연타 · 재시도로 같은 기록이 두 번 들어가 합계가 부풀었다.
        if (_spendings.value.any { it.id == spending.id }) return true
        // 전체 삭제 시각보다 반드시 뒤로 찍는다 — 시계가 앞선 기기에서 전체 삭제를 했어도 새 지출은 살아남는다.
        val stamped = spending.copy(updatedAt = maxOf(currentTimeMillis(), repo.loadSpendingsClearedAt() + 1))
        // 상태 갱신과 저장을 분리한다 — update {} 블록은 CAS 재시도 시 통째로 다시 실행되므로
        // 그 안에 저장을 넣으면 디스크 쓰기와 클라우드 푸시 예약이 한 번 더 일어날 수 있다.
        val next = (listOf(stamped) + _spendings.value).sortedByDescending { it.dateMillis }
        _spendings.value = next
        persistSpendings(next)
        emitSuccess("지출이 저장되었어요")
        return true
    }

    /** 저장했으면 true. 이미 삭제된 지출이면 true 로 닫는다(되살릴 수 없으니 붙잡아 둘 이유가 없다). */
    fun updateSpending(updated: Spending): Boolean {
        if (!validSpending(updated)) return false
        // 그사이 지워진 지출(다른 기기 삭제 · 동기화)이면 수정하지 않는다 — 예전엔 조용히 아무것도 안 하면서
        // "수정되었어요" 를 띄웠다.
        val before = _spendings.value.firstOrNull { it.id == updated.id }
        if (before == null) { emitStatus("이미 삭제된 지출이에요"); return true }
        val stamped = updated.copy(updatedAt = stampAfter(before.updatedAt))
        val next = _spendings.value.map { if (it.id == stamped.id) stamped else it }
            .sortedByDescending { it.dateMillis }
        _spendings.value = next
        persistSpendings(next)
        emitSuccess("지출이 수정되었어요")
        return true
    }

    // 지출 ↔ 정기결제 자동 연결(autoLinkSubscription·collectUnlinkedSubscriptions 등)은
    // 여기 있었다 — 정기결제를 기능째 걷어냈다(2026-09-09).

    /**
     * 수정 · 삭제 시각 — **이미 찍혀 있는 시각([seen])보다 반드시 뒤**(27.52.0).
     * 지금 시각만 찍으면, 시계가 빠른 기기에서 고친 지출을 다른 기기가 나중에 고치거나 지워도 「더 최신」인 옛 값이 이겼다.
     */
    private fun stampAfter(seen: Long): Long = maxOf(currentTimeMillis(), seen + 1)

    fun deleteSpending(id: String) {
        val (removed, next) = _spendings.value.partition { it.id == id }
        // tombstone — 삭제를 다른 기기에 전파(합집합 병합 방어). 시각은 지운 지출의 수정 시각보다 뒤로.
        repo.addDeletedSpendingIds(setOf(id), stampAfter(removed.maxOfOrNull { it.updatedAt } ?: 0L))
        _spendings.value = next
        persistSpendings(next)
    }

    fun deleteSpendings(ids: Set<String>) {
        // partition 1회 — 예전엔 같은 목록을 filter 로 두 번 훑었다.
        val (removed, next) = _spendings.value.partition { it.id in ids }
        repo.addDeletedSpendingIds(ids, stampAfter(removed.maxOfOrNull { it.updatedAt } ?: 0L)) // tombstone — 삭제 전파
        _spendings.value = next
        persistSpendings(next)
    }

    /** 구독 매칭 키 — 이름·게임·금액이 같으면 같은 구독으로 본다(등록·해제 판정 공통). */

    /**
     * 선택한 지출들의 일부 필드를 일괄 변경. null/빈 인자는 해당 필드 미변경.
     * 게임 변경 시 게임색(gameColor)도 함께 보정. 태그는 기존에 추가(중복 제거).
     */
    fun bulkEditSpendings(ids: Set<String>, gameName: String?, dateMillis: Long?, addTags: List<String>) {
        if (ids.isEmpty()) return
        val next = _spendings.value.map { s ->
            // 빈 게임명도 "변경 없음" — null 만 그렇게 봐서, 빈 문자열이 오면 선택한 기록의 게임이 전부 비었다.
            val newGame = gameName?.takeIf { it.isNotBlank() }
            if (s.id !in ids) s else s.copy(
                gameName = newGame ?: s.gameName,
                gameColor = newGame?.let { GameData.colorFor(it) } ?: s.gameColor,
                // 날짜만 바꾸고 **시각은 그대로** — 예전엔 고른 날의 시각으로 전부 덮여 하루 안의 순서가 뭉개졌다.
                dateMillis = dateMillis?.let { DateUtil.startOfDay(it) + (s.dateMillis - DateUtil.startOfDay(s.dateMillis)) }
                    ?: s.dateMillis,
                tags = if (addTags.isEmpty()) s.tags else (s.tags + addTags).distinct(),
                updatedAt = stampAfter(s.updatedAt),
            )
        }.sortedByDescending { it.dateMillis }
        _spendings.value = next
        persistSpendings(next)
        emitSuccess("${ids.size}건 일괄 수정했어요")
    }

    /** 모든 지출 기록 삭제. */
    fun clearSpendings() {
        // 전체 삭제는 id 마다 tombstone 이 아니라 **시각 하나**로 남긴다 — tombstone 은 상한(2000)이라
        // 그보다 많이 지우면 잘린 만큼 다른 기기에서 되살아났다([GatchaRepository.markSpendingsCleared]).
        // 지우는 지출들의 수정 시각보다 뒤로 — 시계가 빠른 기기가 찍은 지출도 함께 지워지게.
        repo.markSpendingsCleared(stampAfter((_spendings.value.maxOfOrNull { it.updatedAt } ?: 0L) - 1))
        _spendings.value = emptyList()
        persistSpendings(emptyList())
    }

    // ----------------------------------------------------------------- 예산
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
    /** 저장했으면 true — 폼은 false 일 때 닫지 않는다(검증 실패로 입력이 날아가지 않게). */
    fun updateHoyolabConfig(input: HoyolabConfig): Boolean {
        if (_onboardingPreview.value) return true   // 테스트용 온보딩 — 연동 정보를 저장하지 않는다
        val prev = _hoyolabConfig.value
        val config = input.trimmed().let { c ->
            // 계정(ltuid)이 바뀌었는데 교환 쿠키는 그대로면 옛 계정 것이다 — 남기면 선물코드가 다른
            // 계정으로 교환된다. 새 로그인에서 함께 들어온 쿠키(값이 바뀐 것)는 그대로 둔다.
            // **게임 UID 도 같다**(27.52.0) — 화면은 칸에 남아 있던 옛 계정 UID 를 그대로 실어 보내는데, 조회는 새 계정에
            // 있는 게임만 덮어쓴다. 새 계정에 없는 게임에 옛 계정 UID 가 남아 남의 행동력 · 전투를 조회했다.
            if (c.ltuid == prev.ltuid || !prev.isLinked) c
            else c.copy(
                webCookie = c.webCookie.takeIf { it != prev.webCookie }.orEmpty(),
                cookieToken = c.cookieToken.takeIf { it != prev.cookieToken }.orEmpty(),
                genshinUid = c.genshinUid.takeIf { it != prev.genshinUid }.orEmpty(),
                hsrUid = c.hsrUid.takeIf { it != prev.hsrUid }.orEmpty(),
                zzzUid = c.zzzUid.takeIf { it != prev.zzzUid }.orEmpty(),
            )
        }
        hoyolabInputError(config)?.let { emitError(it); return false }
        if (prev.isLinked && (!config.isLinked || config.ltuid != prev.ltuid)) {
            cancelAccountWork()
            clearHoyolabDiskCaches(repo)
        }
        _hoyolabConfig.value = config
        // **출석은 HoYoLAB 계정마다 따로다**(27.52.0) — 다른 계정을 연동하면(해제 뒤 다시 연동 포함) 오늘 기록을 비운다.
        // 비우지 않으면 앞 계정으로 한 오늘 출석 때문에 새 계정은 그날 출석 · 자동 출석 · 리마인더가 모두 건너뛰어졌다.
        if (config.isLinked && config.ltuid != prev.ltuid) {
            attendance.forgetToday()
            AutoCheckInRunner.forgetTodayNotices(appSettings)
        }
        // 보안 저장소를 못 쓰면 토큰은 저장되지 않는다(평문 폴백 금지) — 조용히 넘기지 않고 알린다.
        if (!repo.saveHoyolab(config)) {
            emitError("보안 저장소를 쓸 수 없어 토큰을 저장하지 못했어요 — 앱을 재설치하거나 기기를 재시작해주세요")
            return false
        }
        // 연동이 바뀌면 걸려 있던 예약도 다시 건다 — 해제했는데 완충 알림 · 출석 리마인더가 그대로 울리지 않게.
        if (config.isLinked != prev.isLinked || config.ltuid != prev.ltuid || config.canCheckIn != prev.canCheckIn) rescheduleTimedAlerts()
        // 새 토큰이 들어왔으면 만료 플래그 자동 클리어 — 홈 상단 배너 즉시 사라짐.
        if (config.isLinked && config.ltoken.isNotBlank()) {
            appSettings.hoyoTokenExpired = false
            _hoyoTokenExpired.value = false
        }
        // 연동 성공/실패 넛징(전역 토스트). 토큰이 있으면 실제 유효성 검증 후 안내.
        if (!config.isLinked) {
            // 연동이 없으면 만료 배너도 내린다 — 예전엔 해제한 뒤에도 "재연동" 배너가 남고 내릴 길이 없었다(27.52.0).
            clearHoyoTokenExpired()
            // 연동돼 있던 것을 푼 경우와, 처음부터 빈 값을 저장한 경우의 안내를 가른다 — 토스트가 앱 루트로 올라오면서
            // 온보딩의 「연결 해제」에서도 이 문구가 보이게 됐다(27.52.0).
            if (prev.isLinked) emitSuccess("HoYoLAB 연동을 해제했어요")
            else emitStatus("연동되지 않았어요 — ltuid·ltoken을 입력하거나 로그인으로 가져오세요")
            return true
        }
        val linkRepo = repo
        accountScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                HoyolabApi.fetchGameUids(config.ltuid, config.ltoken)
            }
            // 그사이 계정이나 연동이 바뀌었으면 버린다 — 옛 계정의 UID 를 새 저장소에 쓰지 않는다.
            if (repo !== linkRepo || _hoyolabConfig.value.ltuid != config.ltuid) return@launch
            // 서버에 못 닿았다 — 토큰 탓이 아니다. "만료됐을 수 있어요" 로 안내하면 멀쩡한 토큰을 다시 뽑는다.
            if (fetched == null) {
                emitError("연결을 확인해 주세요 — 토큰은 저장했고, 연결되면 게임 UID 를 다시 가져올게요")
                return@launch
            }
            val uids = fetched
            // 연동 계정의 게임 UID 를 「내 캐릭터」 섹션이 쓰도록 반영 — 별도 입력 불필요
            if (uids.isNotEmpty()) {
                applyGameUids(uids)
                enkaUidsSynced = true
                // 연동 직후 '내 캐릭터' 로스터를 강제 새로고침 — 토큰이 이제 있으므로,
                // 토큰 없이 받아 캐시에 박혀 있던 쇼케이스(공개)만 결과를 전체 보유 로스터로 즉시 교체한다.
                // (재설치 후 연동 1회로 '저장 다시' 없이 바로 전체 노출 — force=true 로 5분 TTL 무시.)
                autoLoadEnkaSection(listOf("genshin", "hsr", "zzz"), force = true)
            }
            // **행동력 · 원장 · 전투도 여기서 바로 다시 받는다**(27.52.0). 예전엔 화면이 저장 뒤에 강제 갱신을 불러야 했는데
            // 경로마다 빠져서(Android 설정의 연동 저장), 연동해 놓고도 신선도가 낡을 때까지 5~30분 비어 있었다.
            refreshGameInfo(force = true, silent = true)
            when {
                uids.isEmpty() -> emitError("연동 실패 — 토큰이 만료됐을 수 있어요. 다시 로그인해 가져와주세요")
                // 교환용 쿠키가 없으면 선물코드 자동 교환이 안 된다 — 이메일 로그인이 아니면 받지 못한다.
                config.cookieToken.isBlank() && config.webCookie.isBlank() ->
                    emitSuccess("HoYoLAB 계정이 연동되었어요 — 선물코드 교환은 이메일 로그인으로 연동해야 돼요")
                else -> emitSuccess("HoYoLAB 계정이 연동되었어요 — 캐릭터 UID 를 자동으로 맞췄어요")
            }
        }
        return true
    }

    /** 토큰 폼 검증 — 저장하면 안 되는 입력이면 안내 문구. 빈 칸 전부는 '연동 해제'라 통과. */
    private fun hoyolabInputError(c: HoyolabConfig): String? = when {
        c.ltuid.isBlank() && c.ltoken.isBlank() -> null
        c.ltuid.isBlank() || c.ltoken.isBlank() -> "ltuid 와 ltoken 을 둘 다 넣어 주세요"
        !c.ltuid.all { it.isDigit() } -> "ltuid 는 숫자만 들어가요"
        listOf(c.genshinUid, c.hsrUid, c.zzzUid).any { it.isNotEmpty() && !it.all { ch -> ch.isDigit() } } -> "게임 UID 는 숫자만 들어가요"
        else -> null
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
        // **바뀐 것이 없으면 저장하지 않는다**(27.52.0). 세 게임 중 하나를 안 하는 사용자는 UID 한 칸이 늘 비어 있어 실행마다
        // 이 길을 탔는데, 값이 같아도 저장하면서 수정 시각을 「지금」으로 찍어 올리기가 돌았고 — 다른 기기에서 진짜로 바꾼
        // 값을 이 기기의 옛 값이 이겼다.
        if (merged != cur) {
            _hoyolabConfig.value = merged
            repo.saveHoyolab(merged)
            // 첫 UID 가 들어오면 출석 리마인더를 걸 수 있게 된다([HoyolabConfig.canCheckIn]).
            if (merged.canCheckIn != cur.canCheckIn) rescheduleTimedAlerts()
        }
        enka.applyUids(genshin = uids["genshin"], hsr = uids["hsr"])
    }

    // 기존 연동 사용자 대비: 연동됐는데 게임 UID 가 비어있으면 1회 자동 동기화(재연동 불필요).
    private var enkaUidsSynced = false
    private suspend fun ensureEnkaUids() {
        if (enkaUidsSynced) return
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) return
        if (enka.enkaGiUid.value.isNotBlank() && enka.enkaHsrUid.value.isNotBlank() && cfg.zzzUid.isNotBlank()) {
            enkaUidsSynced = true
            return
        }
        val startRepo = repo
        val uids = withContext(Dispatchers.IO) { HoyolabApi.fetchGameUids(cfg.ltuid, cfg.ltoken) }.orEmpty()
        // 조회하는 사이 로그아웃 · 다른 계정 로그인 · 연결 해제가 끝났으면 버린다(27.52.0) — 이 조회는 Enka 쪽 작업 안에서
        // 돌아 계정 전환이 끊지 못한다. 그대로 쓰면 A 의 UID 가 B 의 저장소에 저장되고 클라우드로 올라갔다.
        if (repo !== startRepo || _hoyolabConfig.value.ltuid != cfg.ltuid) return
        if (uids.isNotEmpty()) {
            applyGameUids(uids)
            // 서버가 답했으면 이번 실행에서는 다시 묻지 않는다 — 계정에 없는 게임의 빈 칸은 물어도 채워지지 않는다.
            enkaUidsSynced = true
        }
    }

    // ----------------------------------------------------------------- 테마 강조색
    fun setAccentIndex(index: Int) {
        _accentIndex.value = index
        repo.saveAccentIndex(index)
    }

    // ----------------------------------------------------------------- 출석
    fun toggleAttendance(gameKey: String) = attendance.toggle(gameKey)

    /**
     * 출석을 세는 게임 — 연동 계정에 UID 가 있는 게임만. 안 하는 게임까지 분모에 넣으면
     * 매일 "2/3" 으로 남고, 전체 출석이 없는 계정에 체크인을 쏴서 실패 알림까지 냈다.
     * 미연동이거나 UID 를 하나도 모르면 전부 센다(판단할 근거가 없다).
     */
    val trackedAttendanceGames: StateFlow<List<Game>> = combine(_hoyolabConfig, _myGames) { cfg, mine ->
        GameData.trackedAttendanceGames(cfg, mine)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GameData.trackedAttendanceGames(_hoyolabConfig.value, _myGames.value))

    // ----------------------------------------------------------------- 배너 / 실시간 노트
    // 더미 없음 — 실제 ennead.cc API(refreshGameInfo)로만 채워진다.
    private val _activeBanners = MutableStateFlow<List<GachaBanner>>(emptyList())
    val activeBanners: StateFlow<List<GachaBanner>> = _activeBanners.asStateFlow()

    /**
     * 게임별 실시간 노트 실패 사유(gameKey → 문구). **사용자가 할 일이 있는 실패만** 싣는다 — 전적 비공개 ·
     * 보안 인증 · 계정에 없는 게임 등. 예전엔 노트가 실패하면 카드가 문구 없이 사라져서, 공개 설정 하나만
     * 켜면 되는 사람이 알 길이 없었다. 일시 실패(네트워크)는 곧 다시 받으므로 싣지 않는다.
     */
    private val _noteErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val noteErrors: StateFlow<Map<String, String>> = _noteErrors.asStateFlow()

    private fun recordNoteError(gameKey: String, r: com.gatcha.log.data.api.NoteResult) {
        _noteErrors.update { prev ->
            if (r.note == null && !r.transient && r.error != null) prev + (gameKey to r.error) else prev - gameKey
        }
    }

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

    // ── 엔드 콘텐츠 클리어 편성(층·간별로 어떤 캐릭터를 썼는지) — [CombatClearsController] ──
    private val clears = CombatClearsController(viewModelScope, { repo }, { _hoyolabConfig.value }) { enka.characterNamesByGame() }
    val combatClears: StateFlow<List<CombatClear>> = clears.combatClears
    val combatClearsLoading: StateFlow<Boolean> = clears.combatClearsLoading

    /** 마지막 조회가 전부 실패했다 — 화면은 '기록 없음'이 아니라 '불러오지 못함'을 보여야 한다. */
    val combatClearsFailed: StateFlow<Boolean> = clears.combatClearsFailed

    /** 엔드 콘텐츠 클리어 편성 조회. 캐시가 신선하면(30분) 건너뛴다. */
    fun refreshCombatClears(force: Boolean = false) { clears.refreshCombatClears(force) }


    private val _gameEvents = MutableStateFlow<List<GameEvent>>(emptyList())
    val gameEvents: StateFlow<List<GameEvent>> = _gameEvents.asStateFlow()

    private val _challenges = MutableStateFlow<List<GameChallenge>>(emptyList())
    val challenges: StateFlow<List<GameChallenge>> = _challenges.asStateFlow()

    // 게임 공지·뉴스(ennead news, 공개·한국어). 게임정보 공지 섹션용.
    private val _gameNews = MutableStateFlow<List<NewsItem>>(emptyList())
    val gameNews: StateFlow<List<NewsItem>> = _gameNews.asStateFlow()

    // ── 공지 본문(상세 페이지) — 받아 오는 규칙은 [NewsArticleController] ──
    private val newsDetail = NewsArticleController(viewModelScope)
    val newsArticle: StateFlow<NewsArticle?> = newsDetail.newsArticle
    val newsArticleLoading: StateFlow<Boolean> = newsDetail.newsArticleLoading

    /** 본문 로드 실패(네트워크·파싱) — UI 는 summary 폴백을 보여준다. */
    val newsArticleFailed: StateFlow<Boolean> = newsDetail.newsArticleFailed

    /** 공지 상세 진입 — 본문을 받아 온다(같은 글을 다시 열면 다시 받지 않는다). */
    fun loadNewsArticle(item: NewsItem) { newsDetail.loadNewsArticle(item) }

    /** 공지 상세 이탈 — 다음 진입 때 이전 글이 비치지 않도록 정리. */
    fun clearNewsArticle() { newsDetail.clearNewsArticle() }


    // 천장(gameKey -> PityState), 이벤트 체크
    private val _pity = MutableStateFlow<Map<String, PityState>>(emptyMap())
    val pity: StateFlow<Map<String, PityState>> = _pity.asStateFlow()

    // ----- 천장 카운터 -----
    fun adjustPity(gameKey: String, delta: Int) = updatePity(gameKey) { it.copy(count = (it.count + delta).coerceAtLeast(0)) }
    // 천장 직접 수정(setPityCount)은 여기 있었다. 유일한 화면이 저축 플래너였고,
    // 그것을 걷어내며 함께 지웠다 — 이제 천장은 **가챠 기록 동기화로만** 채워진다.
    fun resetPity(gameKey: String) = updatePity(gameKey) { it.copy(count = 0, guaranteed = false) }
    fun setPityGuaranteed(gameKey: String, g: Boolean) = updatePity(gameKey) { it.copy(guaranteed = g) }

    private fun updatePity(gameKey: String, transform: (PityState) -> PityState) {
        val cur = _pity.value[gameKey] ?: PityState()
        val next = transform(cur)
        val updated = _pity.value + (gameKey to next)
        _pity.value = updated
        repo.savePity(updated)
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

    // ----------------------------------------------------------------- 절약 챌린지
    // 보유 재화(savingsHeld)는 여기 있었다 — 가챠 계산기가 유일한 소비처였고,
    // 계산기를 기능째 걷어내며(2026-09-09) 함께 지웠다.

    // 저축 계획 파생 상태(savingsPlans·hiddenSavingsPlans·savingsHidden)는 여기 있었다.
    // 보유 재화([savingsHeld])는 **가챠 계산기가 계속 쓰므로** 남긴다.

    /** 절약 챌린지·스트릭·배지 상태. 지출·예산에서 파생(결정형). */
    private val _challenge = MutableStateFlow(SavingsChallenge.evaluate(emptyList(), 0L, 0, emptySet()))
    val challenge: StateFlow<ChallengeSummary> = _challenge.asStateFlow()

    // ----------------------------------------------------------------- 개발자 메뉴 (디버그 빌드 전용)
    //
    // 어떤 화면은 **특정 상태에서만** 나타난다 — 3게임 모두 행동력 가득일 때의 비상벨,
    // 하드 천장 직전의 경고색처럼. 그 상태가 실제로 오길 기다리면 눈으로 확인할 방법이 없다.
    // 아래 함수들은 그 상태를 앱 안에서 만들어 준다.
    //
    // **호출부는 디버그 빌드에서만 그려진다**(Android `BuildConfig.DEBUG` · iOS `#if DEBUG`).
    // 값은 메모리에만 쓰고 저장하지 않는다 — 다음 새로고침이 서버 값으로 덮어쓰는 게 맞다.

    /**
     * 보유 로스터의 **유물 점수 분포**를 로그로 덤프한다 — 등급 밴드를 감이 아니라 값으로 정하려고.
     *
     * 지금 밴드(CV 40/30/20/10 · 유효 롤 6/4.5/3/1.5)와 링 분모는 근거 없이 정해진 상수다.
     * 그 탓에 **1장 기준 밴드가 캐릭터 평균에도 그대로 걸려** 링이 구조적으로 절반에서 멈춘다.
     * 실제 분포를 봐야 문턱을 다시 정할 수 있다.
     *
     * 세 축을 함께 찍는다 — 장당 점수 · 캐릭터 평균 · 캐릭터 합계. 지금은 등급이 평균이고
     * 순위가 합계라 축이 둘이므로, 둘의 분포를 나란히 봐야 한다.
     * 출력은 "GatchaScore" 로 검색한다.
     */
    fun debugDumpScoreDistribution() {
        viewModelScope.launch {
            val out = StringBuilder()
            ENKA_GAMES.filter { usesArtifactScore(it) }.forEach { game ->
                val chars = enka.enkaResults.value[game]?.profile?.chars.orEmpty()
                    .filter { it.detailed && it.artifacts.isNotEmpty() }
                if (chars.isEmpty()) {
                    out.appendLine("GatchaScore: [$game] 표본 없음")
                    return@forEach
                }
                val perArtifact = mutableListOf<Double>()
                val perCharAvg = mutableListOf<Double>()
                val perCharTotal = mutableListOf<Double>()
                var slotSum = 0
                chars.forEach { c ->
                    val keys = resolveKeyStats(game, c, _keyStatOverrides.value).stats
                    val sc = ArtifactScoring.scoreChar(c.artifacts, keys, game)
                    sc.ranked.forEach { perArtifact += it.score.value }
                    perCharAvg += sc.average
                    perCharTotal += sc.total
                    slotSum += c.artifacts.size
                }
                val metric = metricOf(game)
                out.appendLine(
                    "GatchaScore: [$game] 캐릭터=${chars.size} 유물=${perArtifact.size} " +
                        "평균장수=${fixed(slotSum.toDouble() / chars.size, 2)} 지표=${metric.name}",
                )
                // 착용 칸 분포 — "덜 낀 것"과 "응답이 빠진 것"을 가르는 근거를 모은다.
                // 슬롯 이름별 착용 수까지 찍는다. 특정 슬롯만 통째로 비면 그건 결손 신호다.
                // ⚠️ `toSortedMap()` 은 JVM 전용이다 — commonMain 이라 iOS 빌드가 깨진다.
                val bySlotCount = chars.groupingBy { it.artifacts.size }.eachCount()
                    .entries.sortedBy { it.key }
                val slotNames = chars.flatMap { c -> c.artifacts.map { it.slot.ifBlank { "(무명)" } } }
                    .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
                val lvLow = chars.count { it.level < RosterStanding.MIN_LEVEL }
                out.appendLine(
                    "GatchaScore: [$game] 착용칸 " +
                        bySlotCount.joinToString(" ") { "${it.key}장=${it.value}명" } +
                        " · 레벨${RosterStanding.MIN_LEVEL}미만=${lvLow}명",
                )
                out.appendLine(
                    "GatchaScore: [$game] 슬롯별 " + slotNames.joinToString(" ") { "${it.first}=${it.second}" },
                )
                out.appendLine("GatchaScore: [$game] 장당      ${percentileLine(perArtifact)}")
                out.appendLine("GatchaScore: [$game] 캐릭터평균 ${percentileLine(perCharAvg)}")
                out.appendLine("GatchaScore: [$game] 캐릭터합계 ${percentileLine(perCharTotal)}")
            }
            println(out.toString().trimEnd())
            emitSuccess("점수 분포를 로그로 남겼어요 — GatchaScore 로 검색")
        }
    }

    /** 백분위 한 줄 — 밴드 문턱은 이 숫자들 사이에서 고른다. */
    private fun percentileLine(values: List<Double>): String {
        if (values.isEmpty()) return "표본 없음"
        val v = values.sorted()
        fun q(p: Double): String {
            val idx = ((v.size - 1) * p).roundToInt().coerceIn(0, v.size - 1)
            return fixed(v[idx], 1)
        }
        val mean = v.sum() / v.size
        return "n=${v.size} min=${fixed(v.first(), 1)} p10=${q(0.10)} p25=${q(0.25)} " +
            "p50=${q(0.50)} p75=${q(0.75)} p90=${q(0.90)} max=${fixed(v.last(), 1)} mean=${fixed(mean, 1)}"
    }

    /** 3게임 행동력을 가득으로. 노트가 없으면(미연동) 확인용 노트를 만들어 넣는다. */
    /**
     * 호요랜드 무대 시간표 목업 — 실제 편성이 공개되기 전에 라이브 카드·게임 레인·필터를 본다.
     * 기간이 **오늘부터 4일**로 옮겨지고, 오늘 편성은 지금 시각 기준이라 늘 진행 중인 무대가 있다.
     */
    fun debugStageMock(on: Boolean) {
        if (on) HoyolandApi.debugInjectStageMock() else HoyolandApi.debugClearStageMock()
        emitSuccess(if (on) "무대 시간표 목업을 켰어요 — 호요랜드 상세에서 확인하세요" else "무대 시간표 목업을 껐어요")
    }

    /** 지금 목업이 얹혀 있는지 — 개발자 화면 토글 표시. */
    fun debugStageMockOn(): Boolean = HoyolandApi.isStageMock

    /**
     * 호요랜드 **행사 단계** 목업 — 개막 전 → 진행 중 → 종료 → 끔을 돌아가며 얹는다.
     *
     * 이 화면은 단계마다 답하는 말이 통째로 바뀌는데(카운트다운 → 일차 · 게이지 → 없음 ·
     * 예매와 「현장에서」 순서 뒤집힘 · 라인업 부제가 테마 → 무대 상태) 실제 개막일까지는
     * 개막 전 하나만 볼 수 있었다. 날짜만 옮긴 이벤트를 얹어 셋을 바로 확인한다.
     *
     * @return 바뀐 뒤의 키(`""` 면 껐다는 뜻) — 화면이 그대로 표시에 쓴다.
     */
    fun debugCycleHoyolandPhase(): String {
        val next = when (HoyolandApi.debugPhaseMockKey) {
            HoyolandMocks.PHASE_MOCK_BEFORE -> HoyolandMocks.PHASE_MOCK_LIVE
            HoyolandMocks.PHASE_MOCK_LIVE -> HoyolandMocks.PHASE_MOCK_ENDED
            HoyolandMocks.PHASE_MOCK_ENDED -> ""
            else -> HoyolandMocks.PHASE_MOCK_BEFORE
        }
        HoyolandApi.debugInjectPhaseMock(next)
        emitSuccess(
            when (next) {
                HoyolandMocks.PHASE_MOCK_BEFORE -> "호요랜드를 개막 전(D-16)으로 뒀어요"
                HoyolandMocks.PHASE_MOCK_LIVE -> "호요랜드를 진행 중으로 뒀어요 — 무대도 같이 돕니다"
                HoyolandMocks.PHASE_MOCK_ENDED -> "호요랜드를 종료 상태로 뒀어요"
                else -> "호요랜드 단계 목업을 껐어요"
            },
        )
        return next
    }

    /** 지금 얹힌 단계 목업 키(`""` = 없음) — 개발자 화면 표시. */
    fun debugHoyolandPhaseKey(): String = HoyolandApi.debugPhaseMockKey

    /** 단계 목업 키를 사람이 읽는 말로. 개발자 화면 부제에 쓴다. */
    fun debugHoyolandPhaseLabel(key: String): String = when (key) {
        HoyolandMocks.PHASE_MOCK_BEFORE -> "개막 전"
        HoyolandMocks.PHASE_MOCK_LIVE -> "진행 중"
        HoyolandMocks.PHASE_MOCK_ENDED -> "종료"
        else -> ""
    }

    fun debugFillAllResin() {
        val prev = _liveNotes.value
        _liveNotes.value = GameData.attendanceGames.map { g ->
            val cur = prev.firstOrNull { GameData.byNameOrNull(it.game)?.key == g.key }
            val max = cur?.maxResin?.takeIf { it > 0 } ?: 160
            (cur ?: LiveNote(game = g.displayName)).copy(
                currentResin = max,
                maxResin = max,
                resinFullAtMillis = 0L,
            )
        }
        emitSuccess("행동력을 3게임 모두 가득으로 채웠어요")
    }

    /** 모든 게임의 천장을 한 번에 설정. 계산기의 경계값 확인용. */
    fun debugSetPityAll(count: Int, guaranteed: Boolean) {
        GameData.games.forEach { g -> updatePity(g.key) { it.copy(count = count.coerceAtLeast(0), guaranteed = guaranteed) } }
        emitSuccess("천장을 ${count}${if (guaranteed) " · 확정 보유" else ""}로 맞췄어요")
    }

    /**
     * 온보딩을 **테스트용으로** 지금 띄운다(개발자 메뉴 · 디버그 빌드). 화면은 [onboardingReplay] 가 바뀌면 온보딩으로 넘어간다.
     *
     * 테스트 모드([onboardingPreview])에서는 **아무것도 저장하지 않는다**(9/30 사용자 지정 — "테스트 용, 클라우드 복원 X"):
     * 고른 값 적용([applyOnboarding]) · HoYoLAB 연동 저장 · 구글 로그인(=클라우드 불러오기)을 모두 건너뛰고,
     * 「온보딩 완료」 기록도 건드리지 않아 도중에 앱이 꺼져도 다음 실행에 실제 온보딩이 뜨지 않는다.
     * 끝나면 화면이 [finishOnboardingPreview] 로 모드를 풀고 권한 요청 · 로그인 없이 홈으로 돌아간다.
     */
    fun debugResetOnboarding() {
        _onboardingPreview.value = true
        _onboardingReplay.value += 1
    }

    private val _onboardingPreview = MutableStateFlow(false)
    /** 개발자 메뉴에서 연 테스트용 온보딩인가 — 저장 · 로그인 · 복원을 모두 건너뛴다. */
    val onboardingPreview: StateFlow<Boolean> = _onboardingPreview.asStateFlow()

    /** 테스트용 온보딩이었으면 모드를 풀고 true — 화면은 권한 요청 · 로그인 · 완료 기록 없이 홈으로만 돌아간다. */
    fun finishOnboardingPreview(): Boolean {
        if (!_onboardingPreview.value) return false
        _onboardingPreview.value = false
        return true
    }

    /**
     * 온보딩 B안에서 고른 값을 **설정과 같은 저장소**에 쓴다(설정 화면에 그대로 보인다).
     *
     * @param games 내 게임(설정 ▸ 내 게임)
     * @param budget 월 예산(설정 ▸ 예산). -1 = 「예산 없이」 — 건드리지 않는다.
     *   게스트(로그인 전)면 보관했다가 로그인 직후 [applyPendingOnboarding] 이 적용한다.
     *   이미 로그인돼 있으면(먼저 로그인한 경로) 바로 적용한다 — 그 경로는 예산 칸을 계정 값으로 미리 채워 보여 주므로,
     *   고친 값이 곧 새 예산이다(10/7. 예전엔 계정 예산이 비어 있을 때만 적용해 고친 값이 버려졌다).
     * @param alerts 「알림 켜고 시작하기」면 true — 네 항목을 스위치 값 그대로 쓰고 방해 금지를 켠다.
     *   false(「알림 없이 시작」)면 알림 설정은 건드리지 않는다.
     */
    fun applyOnboarding(
        games: List<String>,
        budget: Long,
        alerts: Boolean,
        attendance: Boolean,
        resin: Boolean,
        pickup: Boolean,
        budgetAlert: Boolean,
    ) {
        if (_onboardingPreview.value) return   // 테스트용 온보딩 — 설정에 쓰지 않는다
        setMyGames(games.toSet())
        appSettings.pendingOnboardingGames = if (account.value.isGuest) games.toSet() else emptySet()
        if (budget >= 0) {
            if (account.value.isGuest) appSettings.pendingOnboardingBudget = budget
            else if (_budget.value != budget) setBudgets(budget, _gameBudgets.value)
        } else appSettings.pendingOnboardingBudget = -1L
        if (alerts) notify.applyOnboarding(attendance, resin, pickup, budgetAlert)
    }

    /**
     * 로그인 · 클라우드 복원 직후 — 로그인 **전에** 온보딩에서 고른 예산 · 내 게임을 계정에 적용하고 비운다.
     *
     * **지금 온보딩은 구글 로그인부터 하므로(2026-10-07) 이 보관함은 새로 쌓이지 않는다** — 고른 값은 로그인된 채로
     * [applyOnboarding] 이 바로 쓴다. 여기는 그 전 빌드에서 로그인 없이 고르고 닫은 기기에 남은 값을 위한 처리다.
     * 남아 있던 값은 계정의 이전 값을 대신한다(고른 것이 말없이 버려지지 않게). 「예산 없이」는 아무것도 적지 않는다.
     */
    private fun applyPendingOnboarding() {
        val pending = appSettings.pendingOnboardingBudget
        if (pending >= 0) {
            appSettings.pendingOnboardingBudget = -1L
            if (_budget.value != pending) setBudgets(pending, _gameBudgets.value)
        }
        val games = appSettings.pendingOnboardingGames
        if (games.isNotEmpty()) {
            appSettings.pendingOnboardingGames = emptySet()
            if (_myGames.value != games) setMyGames(games)
        }
    }

    private val _onboardingReplay = MutableStateFlow(0)
    /** 0 보다 크면 화면이 온보딩을 다시 띄운다(개발자 메뉴 「온보딩 초기화」를 누를 때마다 1 씩 오른다). */
    val onboardingReplay: StateFlow<Int> = _onboardingReplay.asStateFlow()

    /** 지금 로그인된 계정과 동기화 상태를 한 줄로. 계정이 갈렸는지 확인할 때 쓴다. */
    fun debugAccountSummary(): String {
        val acc = account.value
        return buildString {
            append(if (acc.isGuest) "게스트" else acc.email.ifEmpty { "로그인됨" })
            append(" · 지출 ${_spendings.value.size}건")
            append(" · 노트 ${_liveNotes.value.size}개")
            append(" · 픽업 ${_activeBanners.value.size}개")
        }
    }

    /**
     * 지금 조건으로 **예약될** 알림 목록. 재화 완충 알림이 "앱을 켜야 온다"던 문제처럼,
     * 예약이 실제로 잡히는지는 시각으로 확인할 방법이 없어 눈에 보이게 만든 것이다.
     * 실제 예약을 건드리지 않고 [ScheduledAlerts.plan] 을 그대로 한 번 돌려 본다.
     */
    fun debugScheduledAlerts(): List<String> {
        val plan = ScheduledAlerts.plan(appSettings, repo)
        if (plan.scheduled.isEmpty()) return listOf("예약될 알림이 없습니다")
        return plan.scheduled
            .sortedBy { it.whenMillis }
            .take(12)
            .map { a ->
                val whenText = if (a.repeatsDaily) "매일" else DateUtil.shortDateTime(a.whenMillis)
                "$whenText · ${a.title}"
            }
    }

    /** 젠레스 전투 콘텐츠 API 확인(개발자 화면) — 결과는 [onResult] 로 돌려준다(메인에서 호출). */
    fun debugProbeZzzCombat(onResult: (List<String>) -> Unit) {
        val cfg = _hoyolabConfig.value
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { HoyolabApi.debugProbeZzzCombat(cfg.ltuid, cfg.ltoken, cfg.zzzUid) })
        }
    }

    /** 외부 API 에 한 번씩 닿아 보고 왕복 시간을 잰다(개발자 화면 「API Ping 조회」) — 결과는 [onResult] 로(메인에서 호출). */
    fun debugPingApis(onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            // Enka · Mihomo 는 UID 가 든 경로만 있다 — 연동된 내 UID 로 잰다(없으면 ApiPing 이 없는 UID 로 부른다).
            val gi = enka.enkaGiUid.value
            val hsr = enka.enkaHsrUid.value
            onResult(withContext(Dispatchers.IO) { com.gatcha.log.data.api.ApiPing.run(gi, hsr) })
        }
    }

    /** 개발자 전용 실행 인자(`gl_ping` · `-glPing`)가 부른다 — [times] 회 잇달아 재고 로그 · 기록에 남긴다. */
    fun debugPingRepeat(times: Int) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(4_000)   // 앱이 뜨며 나가는 요청들이 지나간 뒤에 잰다
            repeat(times.coerceIn(1, 10)) { i ->
                val gi = enka.enkaGiUid.value
                val hsr = enka.enkaHsrUid.value
                withContext(Dispatchers.IO) { com.gatcha.log.data.api.ApiPing.run(gi, hsr) }
                println("GatchaPing: == run ${i + 1}/$times done")
                kotlinx.coroutines.delay(1_500)
            }
        }
    }

    /** 기기에 남은 Ping 기록(개발자 화면 「Ping 기록 보기」) — 출처별 평균과 회차별 요약. */
    fun debugPingHistory(): List<String> = com.gatcha.log.data.api.ApiPing.history { DateUtil.shortDateTime(it) }

    /** 로딩 게이트 상태 — "왜 스켈레톤이 안 걷히나"를 볼 때. */
    fun debugReadyStates(): String =
        "게임정보 ${_gameInfoReady.value.mark()} · 일정 ${_scheduleReady.value.mark()} · " +
            "소식 ${_newsReady.value.mark()} · 새로고침중 ${_isRefreshing.value.mark()}"

    private fun Boolean.mark(): String = if (this) "O" else "X"

    /** 게임별 데이터 도착 여부 — 부분 실패(한 게임만 비어 있음)를 잡아낸다. */
    fun debugPerGameData(): List<String> = GameData.games.map { g ->
        val banners = _activeBanners.value.count { GameData.byNameOrNull(it.game)?.key == g.key }
        val events = _gameEvents.value.count { GameData.byNameOrNull(it.game)?.key == g.key }
        val news = _gameNews.value.count { GameData.byNameOrNull(it.game)?.key == g.key }
        val note = _liveNotes.value.any { GameData.byNameOrNull(it.game)?.key == g.key }
        "${g.shortName} — 픽업 $banners · 이벤트 $events · 공지 $news · 노트 ${if (note) "O" else "X"}"
    }


    /** 지출·예산 변경 시 챌린지 재평가 + 최고 스트릭·배지 단조 영속. */
    private fun refreshChallenge() {
        val prevBest = repo.loadBestNoSpend()
        val prevBadges = repo.loadEarnedBadges()
        applyChallenge(
            SavingsChallenge.evaluate(
                _spendings.value, _budget.value, prevBest, prevBadges, _gameBudgets.value,
                budgetSinceMillis = repo.budgetSetAt(),
            ),
            prevBest, prevBadges,
        )
    }

    private fun applyChallenge(summary: ChallengeSummary, prevBest: Int, prevBadges: Set<String>) {
        if (summary.bestStreak > prevBest) repo.saveBestNoSpend(summary.bestStreak)
        val earned = SavingsChallenge.earnedIds(summary)
        if (earned != prevBadges) repo.saveEarnedBadges(earned)
        _challenge.value = summary
    }

    /**
     * 지출 저장 — 직렬화 · 디스크 쓰기 · 챌린지 평가를 **메인 밖에서** 한 줄로 세워 처리한다.
     *
     * 예전엔 추가 · 수정 · 삭제 한 번마다 메인에서 전체 목록(수천 건)을 JSON 으로 만들고 1MB 급 문자열을
     * 저장한 뒤 챌린지를 전수 평가해서, 편집 페이지가 닫히는 애니메이션이 눈에 띄게 멎었다.
     * 화면 상태([_spendings])는 호출부가 이미 바꿔 뒀으므로 사용자는 기다리지 않는다.
     *
     * 병렬 1 짜리 디스패처라 저장 순서가 호출 순서와 같다. 저장소를 읽는 쪽(pull 병합 · push 스냅샷)은
     * [persistJob] 을 먼저 기다린다 — 안 그러면 병합 결과를 늦게 온 옛 목록 저장이 덮는다.
     */
    private fun persistSpendings(list: List<Spending>) {
        val target = repo
        val budget = _budget.value
        val gameBudgets = _gameBudgets.value
        persistJob = viewModelScope.launch(persistDispatcher) {
            target.saveSpendings(list, notify = false)
            val prevBest = target.loadBestNoSpend()
            val prevBadges = target.loadEarnedBadges()
            val summary = SavingsChallenge.evaluate(
                list, budget, prevBest, prevBadges, gameBudgets, budgetSinceMillis = target.budgetSetAt(),
            )
            withContext(Dispatchers.Main) {
                if (repo !== target) return@withContext   // 그 사이 계정이 바뀌었다
                applyChallenge(summary, prevBest, prevBadges)
                scheduleCloudSync()
            }
        }
    }

    private val persistDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var persistJob: Job? = null

    // ----- Enka 프로필 쇼케이스 · '내 캐릭터' 로스터 — [EnkaRosterController] -----
    private val enka = EnkaRosterController(
        scope = viewModelScope,
        repo = { repo },
        hoyolab = { _hoyolabConfig.value },
        ensureUids = { ensureEnkaUids() },
        prefetchCharCamps = { game, chars -> charMeta.prefetchCharCamps(game, chars) },
    )
    val enkaGiUid: StateFlow<String> = enka.enkaGiUid
    val enkaHsrUid: StateFlow<String> = enka.enkaHsrUid
    val enkaResult: StateFlow<EnkaResult?> = enka.enkaResult
    val enkaLoading: StateFlow<Boolean> = enka.enkaLoading

    /** Enka UID 로 프로필 조회 + UID 계정별 영속(클라우드 동기화 포함). */
    fun loadEnkaProfile(game: String, uid: String) { enka.loadEnkaProfile(game, uid) }

    /** 단일 화면(로스터·스탯)용 조회 — 결과는 [enkaResult] 와 게임별 맵([enkaResults]) 양쪽에 싣는다. */
    fun autoLoadEnka(game: String, force: Boolean = false) { enka.autoLoadEnka(game, force) }

    /** 게임 탭 전환 시 이전 결과 정리 */
    fun clearEnkaResult() { enka.clearEnkaResult() }

    /** '내 캐릭터' 섹션 — 게임별 결과. 값은 non-null(미설정/빈 게임은 EnkaResult(null,null)로 채워 '빈 표시'). */
    val enkaResults: StateFlow<Map<String, EnkaResult>> = enka.enkaResults
    val enkaLoadingGames: StateFlow<Set<String>> = enka.enkaLoadingGames

    /** '내 캐릭터' 섹션용 — 지정 게임들의 로스터를 동시에 받는다. 캐시 적중분은 즉시 반영. */
    fun autoLoadEnkaSection(games: List<String>, force: Boolean = false, ttlMs: Long = EnkaRosterController.TTL_MS) {
        enka.autoLoadEnkaSection(games, force, ttlMs)
    }


    // ----------------------------------------------------------------- 현재 게임 버전 · 정련 효과 · 소속(nanoka) — [CharMetaController]
    private val charMeta = CharMetaController(viewModelScope) { repo }

    /** 지금 돌고 있는 게임 버전(출석 3게임) — 데일리 타일 아래 한 줄. */
    val gameVersions: StateFlow<List<GameVersionLine>> = charMeta.gameVersions

    /** 현재 버전을 **처음 받아오는 중**인가 — 화면이 그동안 스켈레톤을 세운다. */
    val gameVersionsLoading: StateFlow<Boolean> = charMeta.gameVersionsLoading

    /** 현재 버전 로드. 화면 진입 시 부르며 **한 번만** 실제로 돈다. */
    fun loadGameVersions(force: Boolean = false) { charMeta.loadGameVersions(force) }

    /** 무기·광추 정련 효과(키 "gameKey:weaponId:level"). 캐릭터 상세를 열 때만 채운다. */
    val weaponRefinement: StateFlow<Map<String, WeaponRefinement>> = charMeta.weaponRefinement

    /** 캐릭터 소속(진영) — 키는 "게임키:캐릭터id". 젠레스는 응답이 직접 주므로 여기 없다. */
    val charCamp: StateFlow<Map<String, String>> = charMeta.charCamp

    /** 캐릭터 소속을 도감에서 받아 온다 — 원신·스타레일만. */
    fun loadCharCamp(gameKey: String, charId: Int) { charMeta.loadCharCamp(gameKey, charId) }

    /** 장착 무기의 정련 효과 조회. */
    fun loadWeaponRefinement(gameKey: String, weaponId: Int, level: Int) { charMeta.loadWeaponRefinement(gameKey, weaponId, level) }


    // ----- 가챠 효율 리포트 (UIGF/SRGF) — [GachaRecordsController] -----
    private val gacha = GachaRecordsController(viewModelScope, { repo }) { msg, kind -> emitStatus(msg, kind) }
    val gachaStats: StateFlow<GachaStats?> = gacha.gachaStats
    val gachaDashboard: StateFlow<GachaDashboard?> = gacha.gachaDashboard

    /** 선택한 JSON 파일들(UIGF/SRGF)의 내용을 읽어 파싱·중복제거·병합 후 저장. */
    fun importGachaFromContents(contents: List<String>) { gacha.importGachaFromContents(contents) }

    fun clearGachaRecords() { gacha.clearGachaRecords() }


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
                emitError("백업 파일을 읽지 못했어요 — 형식을 확인해 주세요")
                return@launch
            }
            persistJob?.join()
            if (!repo.importBackupJson(json)) {
                emitError("백업 파일이 아니에요 — 이 앱에서 내보낸 백업 파일을 골라 주세요")
                return@launch
            }
            reloadSyncedState()
            // 로그인 상태면 복원 결과를 클라우드에도 업로드(다른 기기와 일치)
            syncUid()?.let { uid ->
                withContext(Dispatchers.IO) { cloud.push(uid) }
            }
            emitSuccess("백업을 복원했어요")
        }
    }

    // ----- 구독 관리 -----
    // 정기결제 CRUD(subscriptions·addSubscription·updateSubscription·deleteSubscription)는
    // 여기 있었다 — 기능째 걷어냈다(2026-09-09).

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

    /** 공지를 한 게임도 못 받았다 — 화면은 '소식 없음'이 아니라 실패 + 다시 시도를 보여야 한다. */
    private val _newsFailed = MutableStateFlow(false)
    val newsFailed: StateFlow<Boolean> = _newsFailed.asStateFlow()
    val newsReady: StateFlow<Boolean> = _newsReady.asStateFlow()
    /** 마지막 게임정보 성공 로드 시각 — freshness 캐시(재진입 시 불필요한 재요청 생략). */
    private var lastGameInfoLoadAt = 0L
    /** 이번 회차에 실시간 노트가 **일시적으로** 실패했는가 — 신선도를 짧게 잡아 곧 다시 받는다. */
    private var noteRetry = false
    /** 직전 전체 갱신에서 빠진 것이 있었는가 — 있으면 다음 진입은 「행동력만」 지름길을 타지 않고 전체를 다시 받는다. */
    private var lastGameInfoPartial = false
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
    private val _gameInfoRefreshing: Boolean get() = gameInfoJob?.isActive == true

    /** 진행 중인 게임 정보 새로고침. 계정을 바꿀 때 끊는다 — 옛 계정 토큰으로 받은 노트가 새 계정에 실리면 안 된다. */
    private var gameInfoJob: Job? = null
    /** 새로고침 회차. 끊긴 회차의 finally 가 새 회차의 스피너·게이트를 건드리지 않게 가른다. */
    private var gameInfoGen = 0
    /**
     * 지금 도는 회차가 스피너([_isRefreshing])를 켰는가. 조용히(silent) 시작한 회차라도
     * 도는 중에 사용자가 새로고침을 부르면 true 로 올린다([refreshGameInfo] 머리).
     */
    private var gameInfoSpinning = false

    /** 지출 탭 당겨서 새로고침 진행 여부 — 연타로 pull/push 가 겹치지 않게. */
    private var _spendingRefreshing = false

    /** 일회성 토스트 메시지 (UI 가 소비 후 clearStatus 호출) */
    val statusMessage: StateFlow<String?> = feedback.statusMessage

    fun clearStatus() { feedback.clearStatus() }
    /** 토스트 — 기본은 안내. 끝낸 일은 [emitSuccess], 실패는 [emitError](GLDS 2.1 토스트의 아이콘 원 색이 갈린다). */
    private fun emitStatus(msg: String, kind: ToastKind = ToastKind.INFO) { feedback.status(msg, kind) }
    private fun emitSuccess(msg: String) = emitStatus(msg, ToastKind.SUCCESS)
    private fun emitError(msg: String) = emitStatus(msg, ToastKind.ERROR)
    /** UI 에서 직접 토스트를 띄울 때 (예: 뒤로가기 종료 안내) */
    fun showStatus(msg: String) = emitStatus(msg)

    /** 화면이 상태를 정해 띄우는 토스트 — 저장 성공 · 실패처럼 결과가 분명한 것. */
    fun showToast(msg: String, kind: ToastKind) = emitStatus(msg, kind)

    /** 지금 띄울 토스트 — 상태 · 제목 · 설명. 화면은 [statusMessage] 대신 이것을 그린다. */
    val statusToast: StateFlow<StatusToast?> = feedback.statusToast

    /** 오류 얼럿 모달 — null 이 아니면 노출. UI 가 확인 후 [clearErrorAlert]. 세우는 규칙은 [FeedbackController]. */
    val errorAlert: StateFlow<ErrorAlert?> = feedback.errorAlert
    fun clearErrorAlert() { feedback.clearErrorAlert() }

    private fun emitNetworkAlert() = feedback.networkAlert()

    /**
     * 앱 밖(백그라운드 작업·알람)에서 출석이 바뀌면 저장소에서 다시 읽는다.
     *
     * 자동 출석은 [GatchaRepository] 를 새로 만들어 저장소에 직접 쓰므로, 여기서 다시 읽지
     * 않으면 화면의 출석 상태([AttendanceController])가 낡은 채로 남는다.
     */
    private fun observeAttendance() {
        // 앱을 켜 둔 채 베이징 자정(출석 초기화)을 넘기면 '오늘 출석'이 어제 값으로 남았다 —
        // 복귀 갱신([onAppForeground])은 백그라운드를 거쳐야만 돈다. 자정마다 저장소에서 다시 읽는다.
        viewModelScope.launch {
            while (true) {
                val now = currentTimeMillis()
                delay(((now + HOYO_TZ_SHIFT_MS) / DAY_MS + 1) * DAY_MS - HOYO_TZ_SHIFT_MS - now + 1_000)
                reloadAttendance()
            }
        }
        // 같은 까닭으로 **로컬 자정**에는 「이번 달」과 절약 챌린지를 다시 계산한다(27.52.0) — 앱을 켠 채 달이 넘어가면
        // 1일 00:10 에 넣는 지출에 지난달 합계로 「이번 달 이미 N원 썼어요」가 떴고, 무지출 연속 일수도 어제 값이었다.
        viewModelScope.launch {
            while (true) {
                val now = currentTimeMillis()
                delay(DateUtil.startOfDayPlus(now, 1) - now + 1_000)
                recomputeSpendingDerived()
                refreshChallenge()
            }
        }
        viewModelScope.launch {
            AttendanceBus.changed.collect {
                reloadAttendance()
                refreshHoyoTokenExpired()   // 그 출석이 토큰 만료를 찍었거나 풀었을 수 있다
                rescheduleTimedAlerts()     // 출석 리마인더를 남은 출석에 맞춰 다시 건다
                // 자동 출석은 따로 만든 저장소에 써서 onChange 가 안 걸린다 — 여기서 올리기를 예약하지
                // 않으면 다음 변경 전까지 클라우드는 출석 전 값이고, 그 사이 pull 이 오늘 출석을 지운다.
                scheduleCloudSync()
            }
        }
    }

    /** 출석 기록을 저장소에서 다시 읽어 화면 상태를 맞춘다. */
    private fun reloadAttendance() = attendance.reload()

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
    private val appUpdate = AppUpdateController(viewModelScope, ::emitStatus)
    val updateInfo: StateFlow<UpdateInfo?> = appUpdate.info

    /** 강제 업데이트 여부 — true 면 UI 는 닫을 수 없는 업데이트 화면을 띄운다. 판정은 [AppUpdateController]. */
    val forceUpdate: StateFlow<Boolean> = appUpdate.force

    /** 원격 version.json 과 현재 버전 비교. [manual] 이면 최신일 때 토스트로 알림. */
    fun checkForUpdate(manual: Boolean = false) { appUpdate.check(manual) }

    /** 강제 업데이트일 땐 닫히지 않는다(구버전 계속 사용 차단). */
    fun dismissUpdate() { appUpdate.dismiss() }

    /** 인앱 업데이트 다운로드 진행률(0~1). null = 진행 중 아님. */
    val updateProgress: StateFlow<Float?> = appUpdate.progress

    /** 인앱 업데이트 실행(Android: APK 다운로드+설치 / iOS: 릴리스 페이지 URL 열기). */
    fun startInAppUpdate() { appUpdate.start() }


    /** 진행 중인 체크인 — 계정을 바꾸면 끊는다(옛 계정 결과가 새 계정 출석으로 찍히지 않게). */
    private var checkInJob: Job? = null

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
        if (_gameInfoRefreshing) {
            // 동시 새로고침 차단 — 같은 요청 세트를 겹쳐 쏘지 않는다.
            //
            // 다만 도는 것이 **조용한 자동 갱신**(앱 복귀)이고 지금 부른 쪽이 사용자(당겨서 새로고침 · 버튼)라면
            // 그 회차를 '보이는' 새로고침으로 올린다. 예전엔 여기서 그냥 버려서, 앱에 돌아오자마자 당기면
            // 스피너가 바로 걷히고 아무 일도 없다가 몇 초 뒤에야 값이 바뀌었다(10/6 「즉시 새로고침되지 않을 때가 있다」).
            // 방금 나간 요청이라 새로 쏘는 것과 받는 값은 같다.
            if (!silent && !gameInfoSpinning) {
                gameInfoSpinning = true
                _isRefreshing.value = true
            }
            return
        }
        if (!force && _gameInfoReady.value) {
            val age = currentTimeMillis() - lastGameInfoLoadAt
            if (age < gameInfoFreshMs) return
            // **5분은 넘었지만 30분은 안 된 재진입은 행동력만 다시 받는다**(27.51.1). 일정 · 소식 · 원장 · 전투는 몇 분 만에
            // 바뀌지 않는데, 탭에 다시 들어올 때마다 20여 건을 통째로 다시 불렀다(iPhone 게임 정보 탭은 들어올 때마다 이 길이다).
            // 앱 복귀([onAppForeground])가 이미 쓰는 기준과 같다 — 전체는 [GAME_INFO_MAX_AGE_MS], 행동력은 [LIVE_NOTE_MAX_AGE_MS].
            // 직전 회차가 일부 실패했으면(짧게 잡은 신선도) 이 지름길을 타지 않고 전체를 다시 받는다.
            if (!lastGameInfoPartial && age < GAME_INFO_MAX_AGE_MS) {
                if (currentTimeMillis() - lastLiveNoteAt >= LIVE_NOTE_MAX_AGE_MS) refreshLiveNotesQuiet()
                return
            }
        }
        val gen = ++gameInfoGen
        gameInfoSpinning = !silent
        gameInfoJob = accountScope.launch {
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
                    if (_gameNews.value.isEmpty()) _newsFailed.value = true
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
                    val uids = if (cfg.isLinked) cfg.linkedUids else emptyMap()

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
                    // 원장 · 전투도 **지금** 쏜다(수확은 아래). 예전엔 캘린더 · 공지를 다 받은 뒤에야 나가서, 게임 정보
                    // 탭의 원장 · 전투와 당겨서-새로고침 스피너가 ennead 왕복만큼(타임아웃이면 12초) 늦었다.
                    // 게임 안에서도 둘을 따로 쏜다 — 호스트가 달라(원장 act 계열 · 전투 bbs 계열) 줄 설 이유가 없다.
                    val ledgerDeferred = uids.map { (key, uid) ->
                        async(Dispatchers.IO) { HoyolabApi.getMonthlyLedger(cfg.ltuid, cfg.ltoken, cfg.webCookie, key, uid)?.takeIf { it.hasData } }
                    }
                    val combatDeferred = uids.map { (key, uid) ->
                        async(Dispatchers.IO) { HoyolabApi.getCombat(cfg.ltuid, cfg.ltoken, key, uid) }
                    }
                    // 행동력은 **캘린더를 기다리지 않는다.** 예전엔 ennead 6게임을 전부 await 하고
                    // 배너·이벤트·도전을 저장한 뒤에야 노트를 대입해서, 데일리 최상단 카드가
                    // 자기 응답이 진작 도착한 뒤에도 몇 초를 더 기다렸다. 요청은 어차피 위에서
                    // 다 쏴 뒀으니 수확만 따로 떼어 낸다.
                    val notesJob = launch {
                        // **게임마다 도착하는 대로 싣는다.** 예전엔 `noteDeferred.map { it.await() }` 로 3게임을
                        // 전부 받은 뒤에 한 번에 대입해서, 하나가 느리면 나머지 둘이 그대로 묶여 기다렸다 —
                        // 그 하나가 타임아웃이면 12초다. 원신 행동력이 300ms 에 왔는데도 "약 N시간 후 충전"이
                        // 한참 뒤에 뜨던 원인. [mergeByGame] 이 애초에 게임 단위 부분 갱신용이라 그대로 쓴다.
                        //
                        // ⚠️ 세 코루틴이 같은 흐름을 고치므로 `.value =`(읽고-쓰기)가 아니라 `update`(원자적)여야
                        // 한다. 대입으로 두면 늦게 끝난 쪽이 자기가 읽은 옛 목록으로 남의 결과를 지운다.
                        coroutineScope {
                            noteDeferred.forEachIndexed { i, d ->
                                launch {
                                    val r = d.await()
                                    if (r.note == null && r.transient) noteRetry = true
                                    recordNoteError(uids.keys.elementAt(i), r)
                                    val note = r.note ?: return@launch
                                    _liveNotes.update { prev ->
                                        mergeByGame(prev, listOf(note), setOf(note.game)) { it.game }
                                            .sortedByGameOrder { it.game }
                                    }
                                    // ★ **첫 노트가 곧 '오늘 할 일'의 준비 완료다.** 예전엔 이 게이트를 캘린더
                                    // 수확이 끝난 뒤에 열어서, 행동력이 진작 도착했는데도 홈 카드가 스켈레톤인 채로
                                    // ennead 왕복을 기다렸다 — `resolveTodayTasks` 는 배너를 아예 안 쓴다
                                    // (`urgentBanner = null`, 픽업은 '이번주 일정' 카드가 맡는다). 기다릴 이유가 없었다.
                                    // 게다가 ennead 는 한 호스트에 **9건**(캘린더 2 + 젠레스 1 + 공지 3 + info 3)이
                                    // 몰리고 한 건이 1.2초쯤 걸린다(실측). Android 는 OkHttp Dispatcher 가
                                    // 호스트당 동시 5건으로 끊어 뒤쪽이 줄까지 선다(HTTP/2 여도 이 한도는 적용된다).
                                    // StateFlow 는 같은 값 재대입을 흘려보내므로 게임마다 세워도 무해하다.
                                    _gameInfoReady.value = true
                                }
                            }
                        }
                        // 위에서 이미 다 끝난 Deferred 라 즉시 반환된다(집계용으로만 다시 훑는다).
                        val notes = noteDeferred.mapNotNull { it.await().note }
                        // 노트를 하나도 못 받았어도 연다 — 값이 없는 것과 아직 모르는 것은 다르고,
                        // 전자는 화면이 답할 수 있다(미연동이면 아래 요청 자체가 없어 여기로 바로 온다).
                        _gameInfoReady.value = true
                        if (notes.isNotEmpty()) {
                            lastLiveNoteAt = currentTimeMillis()
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
                    // 명조 일정 — ennead 가 안 다루는 게임이라 공지 피드에서 따로 뽑는다.
                    // 여기서 같이 쏜다: 달력 응답을 기다렸다 보내면 일정 하나 때문에 한 왕복이 더 붙는다.
                    val wuwaEventsDeferred = async(Dispatchers.IO) { NewsApi.events(Game.WUWA) }

                    // 캘린더 · 공지 · 원장/전투는 **서로 기다리지 않는다** — 각자 도착하는 대로 싣는다. 예전엔 한 줄로
                    // 수확해서, 느린 ennead 캘린더 하나가 공지와 원장까지 붙잡았다(타임아웃이면 12초).
                    launch {
                        // **출처마다 도착하는 대로 싣는다**(27.51.1). 예전엔 원신 · 스타레일 · 젠레스 · 명조 네 출처를 차례로
                        // await 한 뒤 한 번에 대입해서, 느린 출처 하나(다른 CDN 의 명조 · 타임아웃 난 ennead)가 나머지 셋의
                        // 일정까지 붙잡았다. 노트 수확과 같은 꼴로 바꾼다 — 받은 게임만 갈아끼우고([mergeByGame]), 실패한
                        // 게임은 직전 값을 유지한다. 여러 코루틴이 같은 흐름을 고치므로 대입이 아니라 `update`(원자적)다.
                        var anyLoaded = false
                        fun apply(game: String, banners: List<GachaBanner>, events: List<GameEvent>, challenges: List<GameChallenge>) {
                            val loaded = setOf(game)
                            // 종료 미정(end_time 미공지)은 임박도를 알 수 없으니 맨 뒤로 — dDay 가 큰 음수라 앞으로 튄다.
                            _activeBanners.update { prev ->
                                mergeByGame(prev, banners, loaded) { it.game }
                                    .filter { it.game in SCHEDULE_GAMES }
                                    .sortedWith(compareBy({ it.isEndUnknown }, { it.dDay() }))
                            }
                            _gameEvents.update { prev ->
                                mergeByGame(prev, events, loaded) { it.game }.filter { it.game in SCHEDULE_GAMES }.sortedBy { it.endMillis }
                            }
                            _challenges.update { prev ->
                                mergeByGame(prev, challenges, loaded) { it.game }.filter { it.game in SCHEDULE_GAMES }.sortedBy { it.endMillis }
                            }
                            anyLoaded = true
                            // 첫 출처가 곧 「이번 주 일정」의 준비 완료다 — 나머지는 도착하는 대로 끼어든다.
                            _scheduleReady.value = true
                        }
                        coroutineScope {
                            calendarGames.forEachIndexed { i, game ->
                                launch {
                                    val r = enneadDeferred[i].await()
                                    if (r == null) partial = true else apply(game.displayName, r.banners, r.events, r.challenges)
                                }
                            }
                            launch {
                                val zzz = zzzDeferred.await()
                                if (zzz == null) partial = true else apply(Game.ZZZ.displayName, zzz.banners, zzz.events, zzz.challenges)
                            }
                            launch {
                                // 명조는 이벤트만 있다(픽업·정기 콘텐츠를 주는 소스가 없다). 실패와 빈 목록은 갈라야 한다 —
                                // 실패면 싣지 않아 직전 값이 남는다.
                                val wuwaEvents = wuwaEventsDeferred.await()
                                if (wuwaEvents == null) partial = true else apply(Game.WUWA.displayName, emptyList(), wuwaEvents, emptyList())
                            }
                        }
                        if (anyLoaded) {
                            // 로컬 캐시는 다 모인 뒤 **한 번만** 쓴다 — 백그라운드 픽업 마감 알림 점검과, 다음 실행 때
                            // 홈 「이번 주 일정」을 네트워크 없이 바로 그리는 데 쓴다.
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    repo.saveActiveBanners(_activeBanners.value)
                                    repo.saveGameEvents(_gameEvents.value); repo.saveChallenges(_challenges.value)
                                }
                            }
                        }
                        // 전부 실패해 값이 없더라도 스켈레톤은 걷는다 — 안 그러면 영원히 로딩처럼 보인다.
                        _scheduleReady.value = true
                    }

                    // 게이트는 [notesJob] 이 스스로 연다(위 ★). 여기서 join 하지 않는다 — 하는 순간
                    // 아래 공지 수확이 노트를 기다리게 되고, 둘은 아무 관계가 없다.
                    // (자식 코루틴이라 coroutineScope 가 끝날 때 어차피 함께 완료된다.)

                    launch {
                        // 게임 공지·뉴스(공개 API·인증 불필요) — 위에서 이미 쏴 둔 요청을 여기서 수확한다.
                        // **게임마다 도착하는 대로 싣는다**(27.51.1) — 예전엔 다섯 게임을 차례로 await 한 뒤 한 번에 대입해서,
                        // 느린 출처 하나(280KB 짜리 엔드필드 · 명조)가 ennead 세 게임의 소식까지 붙잡았다.
                        var anyLoaded = false
                        coroutineScope {
                            newsGames.forEachIndexed { i, game ->
                                launch {
                                    // 실패(null)한 게임은 직전 공지를 유지한다 — 빈 목록으로 합쳐지면 '공지 없음'처럼 보인다.
                                    val list = newsDeferred[i].await()
                                    if (list == null) { partial = true; return@launch }
                                    _gameNews.update { prev ->
                                        mergeByGame(prev, list, setOf(game.displayName)) { it.game }
                                            .sortedByDescending { it.createdAtMillis }
                                    }
                                    anyLoaded = true
                                    _newsFailed.value = false
                                    _newsReady.value = true
                                }
                            }
                        }
                        _newsFailed.value = !anyLoaded
                        if (anyLoaded) {
                            // 다음 실행 때 홈 '게임 소식'을 바로 그리기 위한 캐시(최신 N건·요약 절단 — 저장부 참고).
                            withContext(Dispatchers.IO) { runCatching { repo.saveGameNews(_gameNews.value) } }
                        }
                        _newsReady.value = true
                    }

                    // 2) 게임 정보 탭 전용 — 월간 원장 + 전투 진행도(위에서 이미 쏴 둔 요청)
                    // **원장과 전투는 서로 기다리지 않고, 게임마다 도착하는 대로 싣는다**(27.51.1). 예전엔 원장 3건을 다 받고
                    // 전투 3게임(게임 안에서 2~3건을 차례로 부른다)까지 다 받은 뒤에야 둘을 같이 대입했다 — 한 번이면 오는
                    // 원장이 제일 느린 전투 묶음만큼 늦게 떴다.
                    if (uids.isNotEmpty()) {
                        ledgerDeferred.forEach { d ->
                            launch {
                                val ledger = d.await() ?: return@launch
                                _ledgers.update { prev ->
                                    mergeByGame(prev, listOf(ledger), setOf(ledger.game)) { it.game }.sortedByGameOrder { it.game }
                                }
                            }
                        }
                        launch {
                            var anyCombat = false
                            coroutineScope {
                                combatDeferred.forEach { d ->
                                    launch {
                                        val combats = d.await()
                                        if (combats.isEmpty()) return@launch
                                        // 모드 단위로 갈아끼운다 — 이번에 못 받은 모드는 직전 값을 남긴다([mergeCombat]).
                                        _combat.update { prev ->
                                            mergeCombat(prev, combats, currentTimeMillis()).sortedByGameOrder { it.game }
                                        }
                                        anyCombat = true
                                    }
                                }
                            }
                            if (anyCombat) {
                                // 백그라운드 시즌 마감 알림이 네트워크 없이 판정하도록 로컬 캐시(배너 캐시와 동일 패턴) — 다 모인 뒤 한 번.
                                val modes = _combat.value
                                withContext(Dispatchers.IO) { runCatching { repo.saveCombatModes(modes) } }
                            }
                        }
                    }
                }
                // 전부 성공했을 때만 5분간 재요청을 생략한다. 일부라도 빠졌으면 짧게 잡아 다음 진입에 다시 받는다 —
                // 예전엔 실패해도 5분을 캐시해서, 빠진 정보가 그 시간 동안 수동 새로고침 전까지 안 채워졌다.
                lastGameInfoPartial = partial || noteRetry
                lastGameInfoLoadAt = currentTimeMillis() -
                    if (partial || noteRetry) gameInfoFreshMs - gameInfoRetryMs else 0L
            } finally {
                // 계정 전환으로 끊긴 회차면 뒤를 새 회차에 맡긴다(아래를 돌면 새 회차의 스피너를 끈다).
                if (gen == gameInfoGen) {
                    // 조용히 시작했어도 도중에 사용자가 이어받았으면 스피너가 켜져 있다 — 그것도 끈다.
                    if (gameInfoSpinning) _isRefreshing.value = false
                    gameInfoSpinning = false
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
            var reloaded = false   // 합치기가 이미 다시 읽었으면 아래에서 또 읽지 않는다
            if (cloudConfigured && !NetworkMonitor.isOnline()) {
                // 오프라인 — 클라우드 동기화는 건너뛰고 로컬만 갱신하며 안내.
                emitNetworkAlert()
            } else if (cloudConfigured) {
                syncUid()?.let { uid ->
                    val sync = cloud   // 시작할 때의 계정 것을 잡아 둔다 — 받는 사이 계정이 바뀌면 이 컨트롤러는 아무것도 쓰지 않는다
                    // 디바운스 푸시 대기 또는 못 올린 표시 = 미반영 로컬 변경
                    val hasPendingLocal = sync.hasPendingLocal
                    sync.cancel()
                    if (hasPendingLocal) {
                        // 로컬 변경을 먼저 클라우드에 반영(PTR 이 옛 클라우드로 덮어쓰지 않게)
                        sync.pushNow(uid)
                    } else {
                        // pull 실패(네트워크)면 push 금지 — 빈/구 로컬로 클라우드 덮어쓰기 방지.
                        when (val outcome = sync.pull(uid)) {
                            // 받는 사이(최대 8초) 로그아웃 · 계정 전환이 끝났으면 버린다 — 시작 동기화와 같은 함수를 쓴다.
                            is CloudSync.PullOutcome.Loaded -> reloaded = sync.mergePulledAndPush(uid, outcome.json)
                            else -> emitNetworkAlert()
                        }
                    }
                }
            }
            // 어느 분기로 왔든 **한 번만** 다시 읽는다 — 예전엔 분기 안에서도 불러 PTR 1회에 다시 읽기가 두 번 돌았다.
            // 저장이 덜 끝난 목록을 읽지 않게 기다린 뒤, 스냅샷이 바꿀 수 있는 값만 다시 읽는다([reloadSyncedState]).
            if (!reloaded) {
                persistJob?.join()
                reloadSyncedState()
            }
            } finally {
                // 예외로 빠져나가도 가드를 반드시 푼다 — 안 그러면 새로고침이 영구히 막힌다.
                _isRefreshing.value = false
                _spendingRefreshing = false
            }
        }
    }

    /** 출석체크 시도 — HoYoLAB 연동 시에만 실제 API 를 부른다. 미연동이면 출석하지 않는다. */
    fun attemptCheckIn(gameKey: String) {
        // 한 번에 한 건만 — 다른 게임이 진행 중이어도 막는다. 예전엔 가드가 없어 연타가 체크인을
        // 두 번 쐈고, 먼저 끝난 쪽 finally 가 `_checkingIn` 을 풀어 [checkInAll] 가드까지 풀렸다.
        if (_checkingIn.value != null) return
        val cfg = _hoyolabConfig.value
        // 미연동이면 출석으로 기록하지 않는다 — 예전엔 수동 출석으로 완료 처리해, HoYoLAB 계정도 UID 도 없는데
        // 출석이 찍혔다(27.52.0). 화면도 미연동이면 출석 버튼을 내지 않는다.
        if (!cfg.isLinked) {
            emitStatus(UNLINKED_CHECK_IN)
            return
        }
        // 연동했어도 게임 UID 가 없으면 보내지 않는다 — 화면도 출석 버튼을 내지 않는다([GameData.trackedAttendanceGames]).
        if (!cfg.canCheckIn) {
            emitStatus(NO_UID_CHECK_IN)
            return
        }
        _checkingIn.value = gameKey   // launch 전에 세운다 — 같은 프레임의 두 번째 탭도 위 가드에 걸리게
        checkInJob = accountScope.launch {
            try {
                val r = HoyolabApi.checkIn(cfg.ltuid, cfg.ltoken, gameKey)
                if (r.success) markCheckedIn(gameKey)
                emitStatus(r.message, if (r.success) ToastKind.SUCCESS else ToastKind.ERROR)
            } finally {
                _checkingIn.value = null
            }
        }
    }

    /** 전체 출석 한번에 — 오늘 미출석 게임을 동시에 체크인. 미연동이면 출석하지 않는다([attemptCheckIn]). */
    fun checkInAll() {
        if (_checkingIn.value != null) return // 이미 진행 중
        // 메모리 값이 아니라 저장소에서 — 자정을 넘겼거나 자동 출석이 방금 저장했을 수 있다.
        val done = attendance.todayDone()
        val pending = trackedAttendanceGames.value.filter { it.key !in done }
        if (pending.isEmpty()) {
            emitSuccess("오늘 출석을 모두 완료했어요")
            return
        }
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) {
            emitStatus(UNLINKED_CHECK_IN)
            return
        }
        if (!cfg.canCheckIn) {
            emitStatus(NO_UID_CHECK_IN)
            return
        }
        _checkingIn.value = pending.first().key
        checkInJob = accountScope.launch {
            // 게임마다 호스트가 달라 동시에 보낸다 — 예전엔 한 게임씩 기다려 3게임이 3배 걸렸다.
            // 결과는 도착하는 대로 싣는다(markCheckedIn 은 메인에서 돈다 — 이 launch 가 메인이다).
            val results = try {
                coroutineScope {
                    pending.map { g ->
                        async {
                            val r = HoyolabApi.checkIn(cfg.ltuid, cfg.ltoken, g.key)
                            if (r.success) markCheckedIn(g.key)
                            g to r
                        }
                    }.awaitAll()
                }
            } finally {
                _checkingIn.value = null
            }
            val ok = results.count { it.second.success }
            // 실패 사유를 한 줄 붙인다 — "(일부 실패)" 만으로는 재연동인지 보안 인증인지 알 수 없었다.
            val fail = results.firstOrNull { !it.second.success }
            if (fail == null) emitSuccess("전체 출석 완료 — ${pending.joinToString(" · ") { it.shortName }}")
            else emitError("출석 ${ok}/${pending.size} 완료 — ${fail.first.shortName}: ${fail.second.message}")
        }
    }

    // ----------------------------------------------------------------- 선물코드 (자동 수집 + 교환)
    private val giftCodes = GiftCodeController(viewModelScope, { repo }, { _hoyolabConfig.value }) { msg, kind -> emitStatus(msg, kind) }
    val redeemState: StateFlow<RedeemState> = giftCodes.redeemState

    /** 자동 수집된 활성 코드. */
    val activeCodes: StateFlow<List<GiftCode>> = giftCodes.activeCodes
    val codesLoading: StateFlow<Boolean> = giftCodes.codesLoading
    /** 이미 교환한 코드(목록에서 사용 표시). */
    val redeemedCodes: StateFlow<Set<String>> = giftCodes.redeemedCodes

    /** 가려진 코드 — 0 이 아닐 때만 화면이 '되살리기'를 제공한다. */
    val unusableCodes: StateFlow<Set<String>> = giftCodes.unusableCodes

    /** 코드 수집 실패(네트워크·파싱). true 면 '코드 없음'이 아니라 '못 불러옴' — 화면은 재시도를 제공한다. */
    val codesFailed: StateFlow<Boolean> = giftCodes.codesFailed

    /** 게임의 현재 활성 선물코드를 자동 수집해 [activeCodes] 로 노출. */
    fun loadActiveCodes(gameKey: String, force: Boolean = false) { giftCodes.loadActiveCodes(gameKey, force) }

    /** 가려 둔 코드를 전부 되살린다 — 유일한 복구 수단이다. */
    fun restoreUnusableCodes(gameKey: String) { giftCodes.restoreUnusableCodes(gameKey) }

    /** HoYoLAB 선물코드 교환(단건). 결과는 [redeemState] 로 노출. */
    fun redeemGiftCode(gameKey: String, code: String) { giftCodes.redeemGiftCode(gameKey, code) }

    /** 수집된 활성 코드 중 미교환분을 순차 교환. 같은 사유로 연달아 실패하면 중단한다. */
    fun redeemAllCodes(gameKey: String) { giftCodes.redeemAllCodes(gameKey) }

    /** 화면 이탈 시 결과 표시를 걷는다. 교환이 도는 중이면 그대로 둔다. */
    fun resetRedeem() { giftCodes.resetRedeem() }


    private fun markCheckedIn(gameKey: String) = attendance.markCheckedIn(gameKey)

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
            year = currentYear,
            month = currentMonth,
            recentMonths = RECENT_MONTHS,
        )
        _currentMonthTotal.value = d.currentMonthTotal
        _previousMonthTotal.value = d.previousMonthTotal
        _currentMonthTotalsByGame.value = d.currentMonthTotalsByGame
        _recentMonthlyTotals.value = d.recentMonthlyTotals
    }

    // ----------------------------------------------------------------- 파생 통계
    fun monthlyTotal(year: Int = currentYear, month: Int = currentMonth): Long =
        _spendings.value.filter { DateUtil.isSameMonth(it.dateMillis, year, month) }.sumOf { it.amount }

    /** 전월 총 지출(MoM 비교용). 1월이면 전년 12월. */
    fun prevMonthTotal(): Long =
        if (currentMonth == 1) monthlyTotal(currentYear - 1, 12) else monthlyTotal(currentYear, currentMonth - 1)

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
        // BOM — 없으면 엑셀이 UTF-8 을 못 알아봐 한글이 깨진다.
        return "\uFEFF" + (listOf(header) + rows).joinToString("\n")
    }

    private fun csvCell(raw: String): String {
        // 수식 주입 차단 — 메모가 `=` `+` `-` `@` 로 시작하면 엑셀이 수식으로 실행한다. 앞에 ' 를 붙여 글자로 둔다.
        val value = if (raw.firstOrNull() in CSV_FORMULA_LEADS) "'$raw" else raw
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }

    private val CSV_FORMULA_LEADS = setOf('=', '+', '-', '@', '\t', '\r')

    // 출석 "오늘" = HoYoLAB 초기화 기준(베이징 UTC+8). 로컬 자정~01시(KST) 사이엔 아직 전날로 취급되어 오출석 방지.
    private fun todayKey() = DateUtil.hoyoDayKey()

    private val currentYear get() = DateUtil.year(currentTimeMillis())
    private val currentMonth get() = DateUtil.month(currentTimeMillis())

    val displayYear: Int get() = currentYear
    val displayMonth: Int get() = currentMonth

    // ----------------------------------------------------------------- 클라우드 동기화 (Firebase Firestore)
    private val cloudConfigured: Boolean get() = CloudSync.isConfigured()

    /**
     * 지금 클라우드에 닿아도 되는 uid — **로그인한 계정일 때만**(27.52.0).
     *
     * 계정은 게스트인데 Firebase 세션만 살아 있는 상태가 있다(재설치 뒤 Keychain 에 남은 세션 · 로그인 도중 종료).
     * 올리기 예약([scheduleCloudSync])에는 게스트 가드가 있었지만 시작 동기화 · 조용한 받기 · 당겨서 새로고침에는 없어서,
     * 이전 계정의 스냅샷이 게스트 저장소에 합쳐지고 게스트 값이 그 계정 문서로 올라갈 수 있었다.
     */
    private fun syncUid(): String? =
        if (!cloudConfigured || account.value.isGuest) null else CloudSync.currentUid()

    /** 기존 로그인 유저의 최초 클라우드 동기화(데이터 불러오는 중) 여부. 시작 시 로그인 상태면 true. */
    private val _initialSyncing = MutableStateFlow(syncUid() != null)
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
        get() = _spendings.value.isNotEmpty() || gacha.hasRecords

    /**
     * 로딩 게이트(계정 데이터 불러오는 중)가 필요한가 — 로그인했고, 이 기기에서 이 계정을 아직 한 번도
     * 동기화하지 않았을 때만. [AppSettings.syncedAccounts] 참고(앱을 켤 때 홈 → 로딩 순으로 뒤바뀌던 것, 9/29).
     * 로컬에 이미 기록이 있으면(이 플래그가 생기기 전부터 써 온 기기) 건너뛴다.
     */
    val needsSyncGate: Boolean
        get() {
            val acc = account.value
            return !acc.isGuest && acc.id !in appSettings.syncedAccounts && (signInInFlight || !hasLocalData)
        }

    /** 로딩 게이트를 마쳤다(또는 첫 동기화가 끝났다) — 이 계정은 다음부터 게이트 없이 바로 연다. */
    fun markAccountSynced() {
        val acc = account.value
        if (acc.isGuest) return
        appSettings.syncedAccounts = appSettings.syncedAccounts + acc.id
    }

    /**
     * **지금 계정의 클라우드 동기화**([CloudSyncController]) — 올리기 예약 · 밀어내기 · 「받은 것 합치기 → 올리기」와 그 상태.
     * 계정이 바뀌면 앞의 것을 끊고 새 저장소로 다시 만든다([switchAccount]) — 올리기 상태가 계정을 넘어가지 않는다.
     * 로딩 게이트 · 오프라인 안내 · 언제 받을지([cloudSyncPullOrSeed] · [refreshSpending])는 여기 남아 있다.
     */
    private var cloud = newCloudSync(repo)

    private fun newCloudSync(target: GatchaRepository) = CloudSyncController(
        scope = viewModelScope,
        repo = target,
        syncUid = { syncUid() },
        isCurrent = { repo === target },
        awaitPersist = { persistJob?.join() },
        beforeReload = { carryOverGuestHoyolab() },
        reloadSynced = { reloadSyncedState() },
        emitStatus = { msg, kind -> emitStatus(msg, kind) },
    )

    /** 데이터가 바뀌었다 — 디바운스 뒤 올린다. 저장소의 변경 알림과 화면의 편집이 부른다. */
    private fun scheduleCloudSync() = cloud.schedule()

    /**
     * 게스트 상태에서 연동해둔 HoYoLAB 정보를 계정으로 **옮긴다.**
     * 계정에 연동이 없을 때만 채우고(기존 계정 데이터를 덮어쓰지 않는다), enka 프로필 UID 도 함께 옮긴다.
     *
     * **게스트 쪽은 반드시 비운다**(27.51.1). 예전엔 복사만 하고 남겨 둬서, 같은 기기에서 다른 구글 계정으로
     * 로그인하면 그 계정이 첫 사용자의 HoYoLAB 세션을 물려받았고, 연동을 해제해도 다음 동기화 때 되살아났다.
     * 계정에 이미 연동이 있어 옮길 필요가 없을 때도 남은 게스트 토큰은 지운다.
     */
    private fun carryOverGuestHoyolab() {
        if (account.value.isGuest) return   // 게스트 자신에게서 자신으로 — 옮길 것도 비울 것도 없다
        val guest = GatchaRepository(Account.GUEST.id)
        val guestCfg = guest.loadHoyolab()
        if (!guestCfg.isLinked) return
        if (repo.loadHoyolab().isLinked) { guest.saveHoyolab(HoyolabConfig()); clearHoyolabDiskCaches(guest); return }
        if (!repo.saveHoyolab(guestCfg)) return          // 계정 쪽 저장이 실패했으면 게스트 것을 남겨 다음에 다시 시도한다
        guest.saveHoyolab(HoyolabConfig())
        clearHoyolabDiskCaches(guest)   // 토큰만 비우면 나중에 로그아웃했을 때 몇 달 전 행동력이 게스트 화면에 뜬다
        _hoyolabConfig.value = guestCfg
        // 연동이 생겼다 — 출석 리마인더 · 행동력 예약을 이 연동으로 다시 건다([HoyolabConfig.canCheckIn]).
        rescheduleTimedAlerts()
        val gi = guest.loadEnkaGiUid()
        val hsr = guest.loadEnkaHsrUid()
        if (gi.isNotBlank() || hsr.isNotBlank()) {
            repo.saveEnkaUids(gi.ifBlank { repo.loadEnkaGiUid() }, hsr.ifBlank { repo.loadEnkaHsrUid() })
            enka.reloadUids()
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
        val uid = syncUid() ?: run { if (!quiet) _initialSyncing.value = false; return }
        // 로딩 페이지 오프라인 분기: 8초 타임아웃을 기다리지 않고 즉시 로컬로 진행 + 얼럿 안내.
        // quiet(백그라운드 복귀 갱신)은 로딩 게이트도 얼럿도 띄우지 않는다 — 화면을 그대로 두고 값만 바꾼다.
        if (!NetworkMonitor.isOnline()) {
            if (quiet) return
            emitNetworkAlert()
            reloadSyncedState()
            _initialSyncing.value = false
            return
        }
        if (!quiet) _initialSyncing.value = true
        // 대기 중인 로컬 변경이 있었는지 기억해 둔다 — 아래에서 디바운스 push 를 끊는데, pull 이 실패하면
        // 그 변경은 다음 편집 전까지 아무도 올리지 않았다(저장 직후 앱을 내리면 다른 기기에 안 보임).
        // pull 은 최대 8초 — 그 사이 로그아웃 · 계정 전환이 끝나면 [repo] · [cloud] 는 다른 계정 것이 된다.
        // 시작할 때의 것을 잡아 두고, 합치기 직전에 여전히 같은 계정일 때만 반영한다.
        val sync = cloud
        val targetRepo = sync.repo
        val hadPendingPush = sync.hasPendingLocal
        sync.cancel()
        try {
            // pull 은 성공(문서 없음 포함)과 실패(네트워크/타임아웃)를 구분한다. **실패면 push 를 생략**해
            // 멀쩡한 클라우드를 빈/구 로컬로 덮어쓰지 않는다(이번 유실 사고 재발 방지).
            when (val outcome = sync.pull(uid)) {
                is CloudSync.PullOutcome.Loaded -> {
                    // 저장소를 읽고-합치고-쓰는 구간은 **메인에서** — 지출 추가 · 수정 · 삭제가 메인에서 저장소를
                    // 쓰므로 한 줄로 세워야 서로 끼어들지 않는다. 예전엔 IO 에서 돌아 그사이 추가한 지출이
                    // 병합 결과에서 빠지고 loadAll 이 그 목록으로 덮었다(2026-09-28 점검).
                    // ponytail: 스냅샷 파싱까지 메인에서 돈다 — 지출이 수천 건이 되어 복귀가 버벅이면 파싱만 IO 로 떼고 쓰기만 메인에 둔다.
                    if (repo !== targetRepo || syncUid() != uid) return   // 계정이 바뀌었다 — A 의 데이터를 B 에 넣지 않는다
                    // 첫 동기화 성공이면 다음 실행부터 로딩 게이트가 없다([markAccountSynced]).
                    sync.mergePulledAndPush(uid, outcome.json) { markAccountSynced() }
                }
                else -> {
                    // Failed 또는 타임아웃(null) → 상태 불명이므로 push 금지, 로컬 유지 + 안내.
                    if (!quiet) { emitNetworkAlert(); reloadSyncedState() }
                    // 다만 끊어 둔 로컬 변경은 다시 예약한다 — 원래 올라갈 참이던 것이다.
                    if ((hadPendingPush || targetRepo.hasUnsynced) && repo === targetRepo) scheduleCloudSync()
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
            GameData.games
                // 명조는 공지 피드(notice.json)의 startTimeMs·endTimeMs 가 곧 일정표다([NewsApi.events]).
                .filter { it.enneadKey != null || it == Game.ZZZ || it == Game.WUWA }
                .map { it.displayName }.toSet()

        /** 미연동 상태에서 출석을 부른 경우의 안내 — 출석으로 기록하지 않는다. */
        const val UNLINKED_CHECK_IN = "HoYoLAB 을 연동하면 출석할 수 있어요"

        /** 연동했지만 게임 UID 가 없는 계정에서 출석을 부른 경우의 안내. */
        const val NO_UID_CHECK_IN = "이 HoYoLAB 계정에서 게임 UID 를 찾지 못했어요"

        /** 클라우드 · 인증 요청의 최대 대기(ms) — 값은 동기화 컨트롤러와 같이 쓴다. */
        const val SYNC_TIMEOUT_MS = CloudSyncController.SYNC_TIMEOUT_MS
        /** 포그라운드 알림 점검 최소 간격(ms) — 탭 전환마다 HoYoLAB 을 두드리지 않도록. */
        const val FOREGROUND_CHECK_MIN_INTERVAL_MS = 15L * 60 * 1000

        /** '오늘 자동 출석을 이미 시도했는가' 표식(베이징 날짜 키). */
        private const val AUTO_CHECKIN_TRY_KEY = "auto_checkin_try" 

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
        /** 행동력 지름길을 다시 시도하기까지의 최소 간격 — 실패가 이어져도 이보다 자주 보내지 않는다. */
        const val LIVE_NOTE_RETRY_MIN_MS = 60L * 1000

        /**
         * 클라우드 스냅샷의 최대 나이 — 게임 자료와 **따로 잡는다.**
         *
         * 폰 두 대를 번갈아 쓰면 한쪽에서 추가한 지출이 다른 쪽에 안 보인다는 지적이 반복됐다.
         * 기기를 바꿔 드는 데는 1분도 안 걸린다. 클라우드 pull 은 문서 1건이라 게임 API 20여 건과
         * 비용이 다르므로 문턱도 달라야 한다.
         */
        const val CLOUD_MAX_AGE_MS = 60L * 1000


        /** 포그라운드 복귀 때의 '내 캐릭터' 재조회 간격 — 로스터는 하루에 몇 번 바뀌지 않는다. */
        const val ENKA_FOREGROUND_TTL_MS = 30L * 60 * 1000

        /** Enka 가 보유 캐릭터를 주는 게임 — 나머지는 상류가 주지 않는다. */
        val ENKA_GAMES = EnkaRosterController.ENKA_GAMES

        const val DAY_MS = 86_400_000L
        /** 출석 초기화 기준(베이징 UTC+8) — DST 가 없어 고정 오프셋으로 충분하다. */
        const val HOYO_TZ_SHIFT_MS = 8L * 60 * 60 * 1000
        /** 월별 지출 추이 차트가 보여주는 개월 수. */
        const val RECENT_MONTHS = 6
    }

    /**
     * 콜드 스타트 부트스트랩: 이미 Firebase 세션이 살아있으면(앱 재실행) 바로 클라우드 동기화.
     * 세션이 없으면(재설치/데이터삭제/로그아웃) 자동 로그인하지 않고 온보딩의 로그인 화면에서
     * 사용자가 직접 'Google 로그인' 또는 '게스트'를 선택한다. 로그인 시 [signIn] → [completeSignIn] 으로 복원.
     */
    private fun bootstrapAuthAndSync() {
        if (!cloudConfigured || CloudSync.currentUid() == null) return
        if (account.value.isGuest) {
            // 계정은 게스트인데 Firebase 세션만 남아 있다 — 세션을 끊는다(27.52.0). 그대로 두면 로그인 화면이 떠 있는
            // 뒤에서 이전 계정의 스냅샷이 게스트 저장소로 내려온다. 다시 로그인하면 세션은 새로 선다.
            viewModelScope.launch { runCatching { CloudSync.signOut() } }
            return
        }
        viewModelScope.launch { cloudSyncPullOrSeed() }
    }

    // 모든 프로퍼티 초기화 후 최초 로드 (init 순서 의존성 회피)
    init {
        repo.onChange = { scheduleCloudSync() }
        loadAll()
        // 지출·정기결제가 바뀌면 파생값을 자동으로 다시 계산 — 갱신 지점(13곳)마다 따로 부르지 않는다.
        viewModelScope.launch {
            _spendings.collect { recomputeSpendingDerived() }
        }
        feedback.observeErrors()   // 데이터 계층이 삼키던 실패를 화면으로 올린다
        observeAttendance()   // 백그라운드 자동 출석의 결과를 화면에 반영한다
        bootstrapAuthAndSync()
    }
}

/** 오늘 할 일 → 게임 정보 탭 진입 시 스크롤할 섹션. NOTES=실시간 노트, BANNER=배너, PITY=천장. */
// 홈 대시보드 카드 → 게임 정보 탭의 해당 섹션으로 스크롤 앵커링.
// 레이아웃 개편(v27.32.0)으로 단독 배너/천장 섹션이 통합 '게임 일정'으로 합쳐짐 →
// NOTES(실시간 노트/출석) · SCHEDULE(통합 게임 일정=픽업+패치/이벤트) · NEWS(공지·주년).
enum class GameInfoAnchor { NOTES, SCHEDULE, NEWS, COMBAT, HOYOLAND }
