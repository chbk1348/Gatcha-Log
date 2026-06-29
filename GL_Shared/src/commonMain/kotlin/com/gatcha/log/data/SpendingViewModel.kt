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
import com.gatcha.log.data.HomeCardItem
import com.gatcha.log.data.HomeCards
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.work.AutoCheckInRunner
import com.gatcha.log.data.work.NativeScheduler
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
import com.gatcha.log.data.api.EnkaResult
import com.gatcha.log.data.api.EnneadApi
import com.gatcha.log.data.api.NewsApi
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _homeCards = MutableStateFlow(HomeCards.default)
    val homeCards: StateFlow<List<HomeCardItem>> = _homeCards.asStateFlow()
    fun setHomeCards(list: List<HomeCardItem>) { _homeCards.value = list; repo.saveHomeCards(list) }

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

        val gameNow = monthlyTotalsByGame()[game.key] ?: 0L
        val monthNow = monthlyTotal()
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

    fun setNotifyDndEnabled(v: Boolean) { appSettings.notifyDndEnabled = v; _notifyDndEnabled.value = v; NativeScheduler.apply() }
    fun setNotifyDndStartHour(v: Int) { appSettings.notifyDndStartHour = v; _notifyDndStartHour.value = appSettings.notifyDndStartHour }
    fun setNotifyDndEndHour(v: Int) { appSettings.notifyDndEndHour = v; _notifyDndEndHour.value = appSettings.notifyDndEndHour }
    fun setNotifyDailySummary(v: Boolean) { appSettings.notifyDailySummary = v; _notifyDailySummary.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyDailySummaryHour(v: Int) { appSettings.notifyDailySummaryHour = v; _notifyDailySummaryHour.value = appSettings.notifyDailySummaryHour }

    private fun applyNativeAfterNotifyChange(enabled: Boolean) {
        NativeScheduler.apply()
        if (enabled) NativeScheduler.runNow()
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
        _enkaGiUid.value = repo.loadEnkaGiUid()
        _enkaHsrUid.value = repo.loadEnkaHsrUid()
        _enkaResult.value = null
        _enkaResults.value = emptyMap()
        seedEnkaDiskCache()   // 재시작/계정전환 시 디스크 캐시를 메모리로 — '내 캐릭터' 즉시 표시
        gachaRecords = repo.loadGachaRecords()
        _gachaStats.value = GachaReport.computeStats(gachaRecords)
        _gachaDashboard.value = GachaReport.computeDashboard(gachaRecords)
        _subscriptions.value = repo.loadSubscriptions()
        _redeemedCodes.value = repo.loadRedeemedCodes()
        _homeCards.value = repo.loadHomeCards()
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

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            switchAccount(Account.GUEST)
            emitStatus("로그아웃되었어요")
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
        _spendings.update { current ->
            (listOf(spending) + current).sortedByDescending { it.dateMillis }.also(repo::saveSpendings)
        }
        autoLinkSubscription(spending)
        emitStatus("지출이 저장되었어요")
    }

    fun updateSpending(updated: Spending) {
        _spendings.update { current ->
            current.map { if (it.id == updated.id) updated else it }
                .sortedByDescending { it.dateMillis }
                .also(repo::saveSpendings)
        }
        autoLinkSubscription(updated)
        emitStatus("지출이 수정되었어요")
    }

    /** 정기결제용 표시명 — 아이템명 우선, 없으면 "<게임> 정기결제". */
    private fun subscriptionName(s: Spending): String =
        s.itemName.ifBlank { "${GameData.byNameOrNull(s.gameName)?.shortName ?: s.gameName} 정기결제" }

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
    private fun collectUnlinkedSubscriptions(): List<Subscription> {
        val existing = _subscriptions.value
        val result = mutableListOf<Subscription>()
        _spendings.value.filter { it.isSubscription }
            .sortedByDescending { it.dateMillis }
            .forEach { s ->
                val name = subscriptionName(s)
                if (existing.hasMatch(name, s) || result.hasMatch(name, s)) return@forEach
                result += Subscription(
                    name = name,
                    gameName = s.gameName,
                    amount = s.amount,
                    billingDay = DateUtil.dayOfMonth(s.dateMillis).coerceIn(1, 31),
                )
            }
        return result
    }

    fun deleteSpending(id: String) = _spendings.update { current ->
        current.filter { it.id != id }.also(repo::saveSpendings)
    }

    fun deleteSpendings(ids: Set<String>) = _spendings.update { current ->
        current.filter { it.id !in ids }.also(repo::saveSpendings)
    }

    /**
     * 선택한 지출들의 일부 필드를 일괄 변경. null/빈 인자는 해당 필드 미변경.
     * 게임 변경 시 게임색(gameColor)도 함께 보정. 태그는 기존에 추가(중복 제거).
     */
    fun bulkEditSpendings(ids: Set<String>, gameName: String?, dateMillis: Long?, addTags: List<String>) {
        if (ids.isEmpty()) return
        _spendings.update { current ->
            current.map { s ->
                if (s.id !in ids) s else s.copy(
                    gameName = gameName ?: s.gameName,
                    gameColor = gameName?.let { GameData.colorFor(it) } ?: s.gameColor,
                    dateMillis = dateMillis ?: s.dateMillis,
                    tags = if (addTags.isEmpty()) s.tags else (s.tags + addTags).distinct(),
                )
            }.sortedByDescending { it.dateMillis }.also(repo::saveSpendings)
        }
        emitStatus("${ids.size}건 일괄 수정했어요")
    }

    /** 모든 지출 기록 삭제. */
    fun clearSpendings() {
        _spendings.value = emptyList()
        repo.saveSpendings(emptyList())
    }

    // ----------------------------------------------------------------- 예산
    fun setBudget(value: Long) {
        _budget.value = value
        repo.saveBudget(value)
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
    }

    /** 이번 달 게임별 지출 합계(gameKey → 금액). */
    fun monthlyTotalsByGame(year: Int = currentYear, month: Int = currentMonth): Map<String, Long> =
        _spendings.value
            .filter { DateUtil.isSameMonth(it.dateMillis, year, month) }
            .groupBy { GameData.byNameOrNull(it.gameName)?.key ?: it.gameName }
            .mapValues { e -> e.value.sumOf { it.amount } }

    // ----------------------------------------------------------------- 프로필
    fun setProfileName(name: String) {
        _profile.update { it.copy(name = name).also(repo::saveProfile) }
    }

    // ----------------------------------------------------------------- HoYoLAB
    fun updateHoyolabConfig(config: HoyolabConfig) {
        _hoyolabConfig.value = config
        repo.saveHoyolab(config)
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

    // 월간 수입 일지(여행자의 일지·개척의 길). HoYoLAB 연동 시에만 채워진다.
    private val _ledgers = MutableStateFlow<List<MonthlyLedger>>(emptyList())
    val ledgers: StateFlow<List<MonthlyLedger>> = _ledgers.asStateFlow()

    // 전투 콘텐츠 진행도(나선 비경·현실 속 환상극·혼돈의 기억·허구 이야기·종말의 환영).
    private val _combat = MutableStateFlow<List<CombatMode>>(emptyList())
    val combat: StateFlow<List<CombatMode>> = _combat.asStateFlow()

    private val _gameEvents = MutableStateFlow<List<GameEvent>>(emptyList())
    val gameEvents: StateFlow<List<GameEvent>> = _gameEvents.asStateFlow()

    private val _challenges = MutableStateFlow<List<GameChallenge>>(emptyList())
    val challenges: StateFlow<List<GameChallenge>> = _challenges.asStateFlow()

    // 게임 공지·뉴스(ennead news, 공개·한국어). 게임정보 공지 섹션용.
    private val _gameNews = MutableStateFlow<List<NewsItem>>(emptyList())
    val gameNews: StateFlow<List<NewsItem>> = _gameNews.asStateFlow()

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
            if (r.profile != null) { enkaCache["$game:$u"] = currentTimeMillis() to r; repo.saveEnkaCache(enkaCache) }
            _enkaResult.value = r
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

    fun autoLoadEnka(game: String, force: Boolean = false) {
        // 캐시 적중 시 동기 반영(탭 전환 즉시) — UID 가 이미 있는 경우만
        val uidNow = enkaUidFor(game)
        if (!force && uidNow.isNotBlank()) {
            val cached = enkaCache["$game:$uidNow"]
            if (cached != null && currentTimeMillis() - cached.first < enkaTtlMs) {
                _enkaResult.value = cached.second
                return
            }
        }
        viewModelScope.launch {
            ensureEnkaUids() // 연동됐는데 UID 비면 1회 동기화 → 기존 사용자도 자동 로드
            val uid = enkaUidFor(game)
            if (uid.isBlank()) { _enkaResult.value = null; return@launch }
            val key = "$game:$uid"
            val cached = enkaCache[key]
            if (!force && cached != null && currentTimeMillis() - cached.first < enkaTtlMs) {
                _enkaResult.value = cached.second
                return@launch
            }
            _enkaLoading.value = true
            val cfg = _hoyolabConfig.value
            val r = withContext(Dispatchers.IO) { EnkaApi.fetchProfile(game, uid, cfg.ltuid, cfg.ltoken) }
            if (r.profile != null) { enkaCache[key] = currentTimeMillis() to r; repo.saveEnkaCache(enkaCache) }
            _enkaResult.value = r
            _enkaLoading.value = false
        }
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
                    val cfg = _hoyolabConfig.value
                    val r = withContext(Dispatchers.IO) { EnkaApi.fetchProfile(game, uid, cfg.ltuid, cfg.ltoken) }
                    if (r.profile != null) {
                        enkaCache[key] = currentTimeMillis() to r
                        _enkaResults.update { it + (game to r) }   // 갱신분 반영
                    } else if (cached == null) {
                        _enkaResults.update { it + (game to r) }   // 캐시 없고 실패 → 에러 표시(캐시 있으면 기존 유지)
                    }
                    _enkaLoadingGames.update { it - game }
                }
            }.awaitAll()
            repo.saveEnkaCache(enkaCache)   // 갱신된 캐시 디스크 영속(1회)
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
            _gachaStats.value = GachaReport.computeStats(merged)
            _gachaDashboard.value = GachaReport.computeDashboard(merged)
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
        _subscriptions.value = _subscriptions.value.filterNot { it.id == id }
        repo.saveSubscriptions(_subscriptions.value)
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
    /** 마지막 게임정보 성공 로드 시각 — freshness 캐시(재진입 시 불필요한 재요청 생략). */
    private var lastGameInfoLoadAt = 0L
    private val gameInfoFreshMs = 5 * 60 * 1000L

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

    // ----------------------------------------------------------------- 인앱 업데이트 확인
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /** 원격 version.json 과 현재 버전 비교. [manual] 이면 최신일 때 토스트로 알림. */
    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            val info = UpdateChecker.check()
            if (info != null) _updateInfo.value = info
            else if (manual) emitStatus("이미 최신 버전이에요")
        }
    }

    fun dismissUpdate() { _updateInfo.value = null }

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
     */
    fun refreshGameInfo(force: Boolean = false) {
        if (_isRefreshing.value) return // 동시 새로고침 차단
        if (!force && _gameInfoReady.value && currentTimeMillis() - lastGameInfoLoadAt < gameInfoFreshMs) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 오프라인이면 12초 타임아웃을 기다리지 않고 즉시 안내(앱 진입·새로고침 공통).
                if (!NetworkMonitor.isOnline()) {
                    emitNetworkAlert()
                    return@launch
                }
                coroutineScope {
                    val cfg = _hoyolabConfig.value
                    val uids = if (cfg.isLinked) mapOf(
                        "genshin" to cfg.genshinUid,
                        "hsr" to cfg.hsrUid,
                        "zzz" to cfg.zzzUid,
                    ).filterValues { it.isNotBlank() } else emptyMap()

                    // 1) 홈/오늘 할 일 의존 최소셋 — 배너(ennead 2게임 + ZZZ) + 실시간 노트를 모두 동시에
                    val enneadDeferred = GameData.games.filter { it.enneadKey != null }
                        .map { game -> async { EnneadApi.fetch(game) } }
                    // ZZZ 픽업·일정 — ennead zenless 캘린더에서 배너+이벤트+도전 자동(수동 JSON 폐기, 에이전트명 한국어 매핑).
                    val zzzDeferred = async { EnneadApi.fetchZzz() }
                    val noteDeferred = uids.map { (key, uid) ->
                        async { HoyolabApi.getLiveNote(cfg.ltuid, cfg.ltoken, key, uid).note }
                    }

                    val banners = mutableListOf<GachaBanner>()
                    val events = mutableListOf<GameEvent>()
                    val challenges = mutableListOf<GameChallenge>()
                    enneadDeferred.forEach { d ->
                        val r = d.await()
                        banners += r.banners
                        events += r.events
                        challenges += r.challenges
                    }
                    zzzDeferred.await().let { banners += it.banners; events += it.events; challenges += it.challenges }
                    if (banners.isNotEmpty()) {
                        _activeBanners.value = banners.sortedBy { it.dDay() }
                        // 백그라운드 픽업 마감 알림 점검용 로컬 캐시(네트워크 없이 판정).
                        runCatching { repo.saveActiveBanners(banners) }
                    }
                    _gameEvents.value = events.sortedBy { it.endMillis }
                    _challenges.value = challenges.sortedBy { it.endMillis }

                    val notes = noteDeferred.mapNotNull { it.await() }
                    if (notes.isNotEmpty()) _liveNotes.value = notes

                    // ★ 배너+노트까지면 홈/오늘 할 일 준비 완료 — 즉시 표출(원장·전투는 뒤이어)
                    _gameInfoReady.value = true
                    lastGameInfoLoadAt = currentTimeMillis()

                    // 게임 공지·뉴스(공개 API·인증 불필요) — 지원 게임 병렬, 최신순. 부가 콘텐츠라 준비 완료 뒤 로드.
                    val newsDeferred = GameData.games.filter { it.newsSlug != null }
                        .map { g -> async { NewsApi.notices(g) } }
                    val news = newsDeferred.flatMap { it.await() }.sortedByDescending { it.createdAtMillis }
                    if (news.isNotEmpty()) _gameNews.value = news

                    // 2) 게임 정보 탭 전용 — 월간 원장 + 전투 진행도(게임 간 병렬, 게임 내 순차로 단일 호스트 보호)
                    if (uids.isNotEmpty()) {
                        val rest = uids.map { (key, uid) ->
                            async {
                                val ledger = HoyolabApi.getMonthlyLedger(cfg.ltuid, cfg.ltoken, key, uid)?.takeIf { it.hasData }
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
                        if (ledgers.isNotEmpty()) _ledgers.value = ledgers
                        if (combats.isNotEmpty()) _combat.value = combats
                    }
                }
            } finally {
                _isRefreshing.value = false
                _gameInfoReady.value = true // 예외로 1)단계 못 미쳐도 스켈레톤 영구 고착 방지
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
        viewModelScope.launch {
            _isRefreshing.value = true
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
                        val remote = withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.pull(uid) }
                        if (remote != null) repo.importSnapshotJson(remote)
                        carryOverGuestHoyolab()
                        loadAll()
                        withTimeoutOrNull(SYNC_TIMEOUT_MS) { cloudPush(uid) }
                    }
                }
            }
            loadAll()
            _isRefreshing.value = false
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

    /** 게임의 현재 활성 선물코드를 자동 수집해 [activeCodes] 로 노출. */
    fun loadActiveCodes(gameKey: String) {
        viewModelScope.launch {
            _codesLoading.value = true
            _activeCodes.value = withContext(Dispatchers.IO) { GiftCodeApi.activeCodes(gameKey) }
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

    // ----------------------------------------------------------------- 파생 통계
    fun monthlyTotal(year: Int = currentYear, month: Int = currentMonth): Long =
        _spendings.value.filter { DateUtil.isSameMonth(it.dateMillis, year, month) }.sumOf { it.amount }

    fun yearlyTotal(year: Int = currentYear): Long =
        _spendings.value.filter { DateUtil.isSameYear(it.dateMillis, year) }.sumOf { it.amount }

    /** 전월 총 지출(MoM 비교용). 1월이면 전년 12월. */
    fun prevMonthTotal(): Long =
        if (currentMonth == 1) monthlyTotal(currentYear - 1, 12) else monthlyTotal(currentYear, currentMonth - 1)

    fun topGameThisMonth(): String? =
        _spendings.value
            .filter { DateUtil.isSameMonth(it.dateMillis, currentYear, currentMonth) }
            .groupBy { it.gameName }
            .maxByOrNull { entry -> entry.value.sumOf { it.amount } }
            ?.key

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

    /** 데이터 변경 시 디바운스(1.5s) 후 Firestore 에 전체 스냅샷 푸시. */
    private fun scheduleCloudSync() {
        if (!cloudConfigured) return
        val uid = CloudSync.currentUid() ?: return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(1500)
            cloudPush(uid)
        }
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
        val json = repo.exportSnapshotJson()
        if (json == lastPushedSnapshot) return true   // 변경 없음 → write 생략
        if (json.length > CLOUD_DOC_WARN_BYTES) {
            emitStatus("클라우드 백업 용량이 한계에 근접했어요 (${json.length / 1024}KB / 1MB) — 오래된 뽑기 기록 정리를 권장해요")
        }
        val s = repo.exportCloudSections()
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
    private suspend fun cloudSyncPullOrSeed() {
        if (!cloudConfigured) { _initialSyncing.value = false; return }
        val uid = CloudSync.currentUid() ?: run { _initialSyncing.value = false; return }
        // 로딩 페이지 오프라인 분기: 8초 타임아웃을 기다리지 않고 즉시 로컬로 진행 + 얼럿 안내.
        if (!NetworkMonitor.isOnline()) {
            emitNetworkAlert()
            loadAll()
            _initialSyncing.value = false
            return
        }
        _initialSyncing.value = true
        syncJob?.cancel()
        try {
            // 오프라인 안전장치: 응답 없으면 타임아웃 후 로컬로 진행(로딩 90% 갇힘 방지)
            val remote = withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.pull(uid) }
            if (remote != null) repo.importSnapshotJson(remote)
            // 원격/계정에 호요랩 연동이 없고 게스트에 있으면 계정으로 승계(귀속 누락 복구)
            carryOverGuestHoyolab()
            loadAll()
            // 병합 결과를 다시 업로드 → 유실됐던 호요랩 토큰 등을 클라우드에 자가 복구
            withTimeoutOrNull(SYNC_TIMEOUT_MS) { cloudPush(uid) }
        } finally {
            _initialSyncing.value = false
        }
    }

    private companion object {
        /** 클라우드 pull/push 최대 대기(ms). 오프라인 등으로 응답 없을 때 로딩 화면 갇힘 방지. */
        const val SYNC_TIMEOUT_MS = 8_000L
        /** Firestore 문서 1MB 한도 근접 경고 임계치(바이트). 초과 시 set 이 실패해 백업이 조용히 멈추므로 미리 안내. */
        const val CLOUD_DOC_WARN_BYTES = 900_000
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
        bootstrapAuthAndSync()
    }
}

/** 오늘 할 일 → 게임 정보 탭 진입 시 스크롤할 섹션. NOTES=실시간 노트, BANNER=배너, PITY=천장. */
// 홈 대시보드 카드 → 게임 정보 탭의 해당 섹션으로 스크롤 앵커링.
// 레이아웃 개편(v27.32.0)으로 단독 배너/천장 섹션이 통합 '게임 일정'으로 합쳐짐 →
// NOTES(실시간 노트/출석) · SCHEDULE(통합 게임 일정=픽업+패치/이벤트) · NEWS(공지·주년).
enum class GameInfoAnchor { NOTES, SCHEDULE, NEWS }
