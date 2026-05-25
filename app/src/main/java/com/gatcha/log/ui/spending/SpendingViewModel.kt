package com.gatcha.log.ui.spending

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
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
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.data.api.CodeResult
import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.data.api.GiftCodeApi
import com.gatcha.log.data.api.UpdateChecker
import com.gatcha.log.data.api.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

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
class SpendingViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager = AuthManager(app)
    /** 현재 로그인 계정 (게스트 = 비로그인 로컬) */
    val account: StateFlow<Account> = authManager.account

    /** 게스트로 시작 선택 여부(로그인 화면 게이트용) */
    val guestChosen: StateFlow<Boolean> = authManager.guestChosen
    fun continueAsGuest() = authManager.continueAsGuest()

    // 계정별로 분리되는 저장소. 계정 전환 시 교체된다.
    private var repo: GatchaRepository = GatchaRepository(app, account.value.id)

    // ----------------------------------------------------------------- 상태 (계정별 로드)
    private val _spendings = MutableStateFlow<List<Spending>>(emptyList())
    val spendings: StateFlow<List<Spending>> = _spendings.asStateFlow()

    private val _budget = MutableStateFlow(0L) // 0 = 미설정
    val budget: StateFlow<Long> = _budget.asStateFlow()

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _hoyolabConfig = MutableStateFlow(HoyolabConfig())
    val hoyolabConfig: StateFlow<HoyolabConfig> = _hoyolabConfig.asStateFlow()

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
        val cal = DateUtil.hoyoCalendar()
        // 오늘 아직 출석 전이면 어제부터 카운트(낮 동안 streak 유지)
        if (attendanceMap[DateUtil.hoyoDayKey(cal.timeInMillis)].isNullOrEmpty()) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var streak = 0
        while (!attendanceMap[DateUtil.hoyoDayKey(cal.timeInMillis)].isNullOrEmpty()) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    /** 현재 repo(계정)의 모든 데이터를 상태로 로드. */
    private fun loadAll() {
        _spendings.value = repo.loadSpendings()
        _budget.value = repo.loadBudget()
        _profile.value = repo.loadProfile()
        _hoyolabConfig.value = repo.loadHoyolab()
        _accentIndex.value = repo.loadAccentIndex()
        attendanceMap = repo.loadAttendance()
        _attendanceHistory.value = attendanceMap
        _attendanceToday.value = attendanceMap[todayKey()] ?: emptySet()
        _attendanceStreak.value = computeAttendanceStreak()
        _wishlist.value = repo.loadWishlist()
        _pity.value = repo.loadPity()
        _eventChecks.value = repo.loadEventChecks()
        _enkaGiUid.value = repo.loadEnkaGiUid()
        _enkaHsrUid.value = repo.loadEnkaHsrUid()
        _enkaResult.value = null
        gachaRecords = repo.loadGachaRecords()
        _gachaStats.value = GachaReport.computeStats(gachaRecords)
        _subscriptions.value = repo.loadSubscriptions()
        _redeemedCodes.value = repo.loadRedeemedCodes()
    }

    // ----------------------------------------------------------------- 계정 (구글 로그인 — Credential Manager)
    /**
     * 구글 로그인(원탭). UI 에서 **Activity 컨텍스트**로 호출한다.
     * 계정 선택 시트를 띄워 한 번 탭하면 로그인 → Firebase 인증 → 클라우드 복원까지 진행.
     */
    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            if (cloudConfigured) _initialSyncing.value = true
            when (val outcome = authManager.signIn(activityContext, autoSelectOnly = false)) {
                is SignInOutcome.Success -> {
                    if (!completeSignIn(outcome.account)) {
                        _initialSyncing.value = false
                        emitStatus("네트워크 오류로 로그인에 실패했어요")
                    }
                }
                SignInOutcome.NoCredential -> { _initialSyncing.value = false; emitStatus("기기에 로그인된 구글 계정이 없어요") }
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
            val uid = authManager.lastIdToken?.let { CloudSync.signInWithGoogle(it) }
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
        repo = GatchaRepository(getApplication(), acc.id)
        repo.onChange = { scheduleCloudSync() }
        loadAll()
        // 로그인 계정이면 프로필을 구글 계정 정보로 맞춤
        if (!acc.isGuest) {
            val p = UserProfile(name = acc.name, email = acc.email)
            _profile.value = p
            repo.saveProfile(p)
        }
        refreshGameInfo()
    }

    // ----------------------------------------------------------------- 지출
    fun addSpending(spending: Spending) {
        _spendings.update { current ->
            (listOf(spending) + current).sortedByDescending { it.dateMillis }.also(repo::saveSpendings)
        }
        emitStatus("지출이 저장되었어요")
    }

    fun updateSpending(updated: Spending) {
        _spendings.update { current ->
            current.map { if (it.id == updated.id) updated else it }
                .sortedByDescending { it.dateMillis }
                .also(repo::saveSpendings)
        }
        emitStatus("지출이 수정되었어요")
    }

    fun deleteSpending(id: String) = _spendings.update { current ->
        current.filter { it.id != id }.also(repo::saveSpendings)
    }

    fun deleteSpendings(ids: Set<String>) = _spendings.update { current ->
        current.filter { it.id !in ids }.also(repo::saveSpendings)
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

    // ----------------------------------------------------------------- 프로필
    fun setProfileName(name: String) {
        _profile.update { it.copy(name = name).also(repo::saveProfile) }
    }

    // ----------------------------------------------------------------- HoYoLAB
    fun updateHoyolabConfig(config: HoyolabConfig) {
        _hoyolabConfig.value = config
        repo.saveHoyolab(config)
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

    // 위시리스트 (gameKey -> 캐릭터 이름), 천장(gameKey -> PityState), 이벤트 체크
    private val _wishlist = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val wishlist: StateFlow<Map<String, List<String>>> = _wishlist.asStateFlow()

    private val _pity = MutableStateFlow<Map<String, PityState>>(emptyMap())
    val pity: StateFlow<Map<String, PityState>> = _pity.asStateFlow()

    private val _eventChecks = MutableStateFlow<Set<String>>(emptySet())
    val eventChecks: StateFlow<Set<String>> = _eventChecks.asStateFlow()

    // ----- 위시리스트 -----
    fun addWish(gameKey: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val cur = _wishlist.value[gameKey].orEmpty()
        if (cur.any { it.equals(n, ignoreCase = true) }) return
        val updated = _wishlist.value + (gameKey to (cur + n))
        _wishlist.value = updated
        repo.saveWishlist(updated)
    }

    fun removeWish(gameKey: String, name: String) {
        val cur = _wishlist.value[gameKey].orEmpty().filterNot { it == name }
        val updated = _wishlist.value + (gameKey to cur)
        _wishlist.value = updated
        repo.saveWishlist(updated)
    }

    /** 위시 캐릭터가 현재 픽업 배너에 등장 중인지 */
    fun isWishPickedUp(gameKey: String, name: String): Boolean {
        val gameName = Game.entries.firstOrNull { it.key == gameKey }?.displayName ?: return false
        return _activeBanners.value.any {
            it.game == gameName && (it.name.contains(name) || name.contains(it.name))
        }
    }

    // ----- 천장 카운터 -----
    fun adjustPity(gameKey: String, delta: Int) = updatePity(gameKey) { it.copy(count = (it.count + delta).coerceAtLeast(0)) }
    fun setPityCount(gameKey: String, value: Int) = updatePity(gameKey) { it.copy(count = value.coerceAtLeast(0)) }
    fun resetPity(gameKey: String) = updatePity(gameKey) { it.copy(count = 0, guaranteed = false) }
    fun setPityGuaranteed(gameKey: String, g: Boolean) = updatePity(gameKey) { it.copy(guaranteed = g) }

    private fun updatePity(gameKey: String, transform: (PityState) -> PityState) {
        val cur = _pity.value[gameKey] ?: PityState()
        val updated = _pity.value + (gameKey to transform(cur))
        _pity.value = updated
        repo.savePity(updated)
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

    /** Enka UID 로 프로필 조회 + UID 계정별 영속(클라우드 동기화 포함). */
    fun loadEnkaProfile(game: String, uid: String) {
        val u = uid.trim()
        if (game == "genshin") _enkaGiUid.value = u else _enkaHsrUid.value = u
        repo.saveEnkaUids(_enkaGiUid.value, _enkaHsrUid.value)
        viewModelScope.launch {
            _enkaLoading.value = true
            _enkaResult.value = EnkaApi.fetchProfile(game, u)
            _enkaLoading.value = false
        }
    }

    /** 게임 탭 전환 시 이전 결과 정리 */
    fun clearEnkaResult() { _enkaResult.value = null }

    // ----- 가챠 효율 리포트 (UIGF/SRGF) -----
    private var gachaRecords: List<GachaRecord> = emptyList()
    private val _gachaStats = MutableStateFlow<GachaStats?>(null)
    val gachaStats: StateFlow<GachaStats?> = _gachaStats.asStateFlow()

    /** 선택한 JSON 파일들(UIGF/SRGF)을 읽어 파싱·중복제거·병합 후 저장. */
    fun importGachaFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val parsed = withContext(Dispatchers.IO) {
                uris.flatMap { uri ->
                    val text = runCatching {
                        resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    if (text != null) GachaReport.normalize(text) else emptyList()
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
            emitStatus("가챠 기록 ${added}건 추가 (중복 ${skipped} 제외)")
        }
    }

    fun clearGachaRecords() {
        gachaRecords = emptyList()
        repo.saveGachaRecords(emptyList())
        _gachaStats.value = null
        emitStatus("가챠 기록을 초기화했어요")
    }

    // ----------------------------------------------------------------- 백업 파일 내보내기/가져오기 (SAF)
    /**
     * 전체 데이터(가챠 포함) 스냅샷 JSON 을 [uri] 파일로 내보낸다.
     * 게스트·로그인 무관하게 동작하는 기기 독립 백업 — 재설치·기기 변경 후 [importBackupFromUri] 로 복원.
     */
    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val json = repo.exportSnapshotJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("출력 스트림 없음")
                    true
                }.getOrDefault(false)
            }
            emitStatus(if (ok) "백업을 파일로 내보냈어요" else "백업 내보내기에 실패했어요")
        }
    }

    /**
     * 백업 파일([uri])의 스냅샷 JSON 을 읽어 현재 계정에 복원한다.
     * 스냅샷에 있는 키만 덮어쓰며(로컬 전용 값 보존), 로그인 상태면 복원 결과를 클라우드에도 반영한다.
     */
    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (json.isNullOrBlank() || runCatching { org.json.JSONObject(json) }.isFailure) {
                emitStatus("백업 파일을 읽지 못했어요 (형식 확인)")
                return@launch
            }
            repo.importSnapshotJson(json)
            loadAll()
            // 로그인 상태면 복원 결과를 클라우드에도 업로드(다른 기기와 일치)
            if (cloudConfigured) CloudSync.currentUid()?.let { uid ->
                withContext(Dispatchers.IO) { CloudSync.push(uid, repo.exportSnapshotJson()) }
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

    /** 일회성 토스트 메시지 (UI 가 소비 후 clearStatus 호출) */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatus() { _statusMessage.value = null }
    private fun emitStatus(msg: String) { _statusMessage.value = msg }
    /** UI 에서 직접 토스트를 띄울 때 (예: 뒤로가기 종료 안내) */
    fun showStatus(msg: String) = emitStatus(msg)

    /** 읽은 알림 키(메시지) 집합 — 벨 배지(넛징)는 안 읽은 알림 수만 카운트. */
    private val _readAlerts = MutableStateFlow<Set<String>>(emptySet())
    val readAlerts: StateFlow<Set<String>> = _readAlerts.asStateFlow()
    fun markAlertsRead(keys: Collection<String>) {
        if (keys.isNotEmpty()) _readAlerts.value = _readAlerts.value + keys
    }

    // ----------------------------------------------------------------- 인앱 업데이트 확인
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /** 원격 version.json 과 현재 버전 비교. [manual] 이면 최신일 때 토스트로 알림. */
    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            val info = UpdateChecker.check(getApplication())
            if (info != null) _updateInfo.value = info
            else if (manual) emitStatus("이미 최신 버전이에요")
        }
    }

    fun dismissUpdate() { _updateInfo.value = null }

    /** 인앱 업데이트 다운로드 진행률(0~1). null = 진행 중 아님. */
    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    /**
     * 인앱 업데이트: APK 다운로드 → 설치 요청. 다운로드 파일은 자동 삭제(잔여 없음).
     * "이 출처 설치 허용" 미허용 시 설정 화면으로 보내고 중단.
     */
    fun startInAppUpdate() {
        val info = _updateInfo.value ?: return
        val ctx = getApplication<Application>()
        // Android 8.0+ : 알 수 없는 출처 설치 허용 여부 확인
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            emitStatus("'이 출처 설치 허용'을 켠 뒤 다시 시도해주세요")
            runCatching {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        _updateInfo.value = null // 다이얼로그 닫고 진행률 오버레이로 전환
        viewModelScope.launch {
            _updateProgress.value = 0f
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    com.gatcha.log.data.api.AppUpdater.downloadAndInstall(ctx, info.apkUrl) { p ->
                        _updateProgress.value = p
                    }
                }
            }
            _updateProgress.value = null
            if (r.isFailure) emitStatus("업데이트 다운로드 실패 — 잠시 후 다시 시도해주세요")
        }
    }

    /** 현재 출석 처리 중인 게임 키 (버튼 진행 표시용). null 이면 진행 중 아님. */
    private val _checkingIn = MutableStateFlow<String?>(null)
    val checkingIn: StateFlow<String?> = _checkingIn.asStateFlow()

    /** ennead.cc 배너·이벤트 + (연동 시) HoYoLAB 실시간 노트 새로고침 */
    fun refreshGameInfo() {
        viewModelScope.launch {
            _isRefreshing.value = true

            val banners = mutableListOf<GachaBanner>()
            val events = mutableListOf<GameEvent>()
            val challenges = mutableListOf<GameChallenge>()
            GameData.games.filter { it.enneadKey != null }.forEach { game ->
                val r = EnneadApi.fetch(game)
                banners += r.banners
                events += r.events
                challenges += r.challenges
            }
            // ZZZ 픽업 배너는 공개 API 부재 → 레포 수동 JSON(zzz_banners.json)에서 병합
            banners += com.gatcha.log.data.api.ZzzBannerApi.fetch()
            if (banners.isNotEmpty()) _activeBanners.value = banners.sortedBy { it.dDay() }
            _gameEvents.value = events.sortedBy { it.endMillis }
            _challenges.value = challenges.sortedBy { it.endMillis }

            val cfg = _hoyolabConfig.value
            if (cfg.isLinked) {
                val uids = mapOf(
                    "genshin" to cfg.genshinUid,
                    "hsr" to cfg.hsrUid,
                    "zzz" to cfg.zzzUid,
                )
                val notes = mutableListOf<LiveNote>()
                val ledgers = mutableListOf<MonthlyLedger>()
                val combats = mutableListOf<CombatMode>()
                uids.filterValues { it.isNotBlank() }.forEach { (key, uid) ->
                    val res = HoyolabApi.getLiveNote(cfg.ltuid, cfg.ltoken, key, uid)
                    res.note?.let { notes += it }
                    HoyolabApi.getMonthlyLedger(cfg.ltuid, cfg.ltoken, key, uid)
                        ?.takeIf { it.hasData }?.let { ledgers += it }
                    combats += HoyolabApi.getCombat(cfg.ltuid, cfg.ltoken, key, uid)
                }
                if (notes.isNotEmpty()) _liveNotes.value = notes
                if (ledgers.isNotEmpty()) _ledgers.value = ledgers
                if (combats.isNotEmpty()) _combat.value = combats
            }

            _isRefreshing.value = false
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
            if (cloudConfigured) {
                CloudSync.currentUid()?.let { uid ->
                    val hasPendingLocal = syncJob?.isActive == true // 디바운스 푸시 대기 = 미반영 로컬 변경
                    syncJob?.cancel()
                    if (hasPendingLocal) {
                        // 로컬 변경을 먼저 클라우드에 반영(PTR 이 옛 클라우드로 덮어쓰지 않게)
                        withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.push(uid, repo.exportSnapshotJson()) }
                    } else {
                        val remote = withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.pull(uid) }
                        if (remote != null) repo.importSnapshotJson(remote)
                        carryOverGuestHoyolab()
                        loadAll()
                        withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.push(uid, repo.exportSnapshotJson()) }
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
            val r = HoyolabApi.checkIn(cfg.ltuid, cfg.ltoken, gameKey)
            if (r.success) markCheckedIn(gameKey)
            emitStatus(r.message)
            _checkingIn.value = null
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
        if (cfg.cookieToken.isBlank()) return CodeResult(false, "연동 설정에서 cookie_token을 입력해주세요 (교환 전용)")
        val r = HoyolabApi.redeemCode(cfg.ltuid, cfg.ltoken, cfg.cookieToken, gameKey, uid, code)
        if (r.success || r.message.contains("이미 사용")) markRedeemed(code)
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
            var ok = 0; var fail = 0
            targets.forEachIndexed { i, code ->
                _redeemState.value = RedeemState.Loading
                val r = doRedeem(gameKey, code)
                if (r.success || r.message.contains("이미 사용")) ok++ else fail++
                if (i < targets.lastIndex) delay(5500) // 교환 레이트리밋(-2016) 회피
            }
            _redeemState.value = RedeemState.Done(fail == 0, "교환 ${ok}건 완료${if (fail > 0) " · 실패 $fail" else ""} (우편함 확인)")
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

    private val currentYear get() = DateUtil.year(System.currentTimeMillis())
    private val currentMonth get() = DateUtil.month(System.currentTimeMillis())

    val displayYear: Int get() = currentYear
    val displayMonth: Int get() = currentMonth

    // ----------------------------------------------------------------- 클라우드 동기화 (Firebase Firestore)
    private val cloudConfigured: Boolean get() = CloudSync.isConfigured(getApplication())
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
            CloudSync.push(uid, repo.exportSnapshotJson())
        }
    }

    /**
     * 게스트 상태에서 연동해둔 HoYoLAB 정보를 계정으로 승계.
     * 신규 계정(클라우드 비어 있음 + 계정에 연동 없음)일 때만, 게스트 연동이 있으면 채운다.
     * 기존 계정 데이터를 덮어쓰지 않으므로 안전. enka 프로필 UID 도 함께 승계.
     */
    private fun carryOverGuestHoyolab() {
        if (repo.loadHoyolab().isLinked) return
        val guest = GatchaRepository(getApplication(), Account.GUEST.id)
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
            withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.push(uid, repo.exportSnapshotJson()) }
        } finally {
            _initialSyncing.value = false
        }
    }

    private companion object {
        /** 클라우드 pull/push 최대 대기(ms). 오프라인 등으로 응답 없을 때 로딩 화면 갇힘 방지. */
        const val SYNC_TIMEOUT_MS = 8_000L
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
