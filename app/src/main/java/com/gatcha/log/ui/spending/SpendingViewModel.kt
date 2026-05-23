package com.gatcha.log.ui.spending

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gatcha.log.data.Account
import com.gatcha.log.data.AuthManager
import com.gatcha.log.data.CloudSync
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GachaRecord
import com.gatcha.log.data.GachaReport
import com.gatcha.log.data.GachaStats
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.PityState
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.Spending
import com.gatcha.log.data.Subscription
import com.gatcha.log.data.UserProfile
import com.gatcha.log.data.api.EnkaApi
import com.gatcha.log.data.api.EnkaResult
import com.gatcha.log.data.api.EnneadApi
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.data.api.UpdateChecker
import com.gatcha.log.data.api.UpdateInfo
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

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

    /** 연속 출석 일수(오늘 미출석이면 어제 기준으로 유지). */
    private val _attendanceStreak = MutableStateFlow(0)
    val attendanceStreak: StateFlow<Int> = _attendanceStreak.asStateFlow()

    private fun computeAttendanceStreak(): Int {
        val cal = Calendar.getInstance()
        // 오늘 아직 출석 전이면 어제부터 카운트(낮 동안 streak 유지)
        if (attendanceMap[DateUtil.dayKey(cal.timeInMillis)].isNullOrEmpty()) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var streak = 0
        while (!attendanceMap[DateUtil.dayKey(cal.timeInMillis)].isNullOrEmpty()) {
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
    }

    // ----------------------------------------------------------------- 계정 (구글 로그인)
    /** UI에서 ActivityResultLauncher로 실행할 로그인 인텐트. */
    fun googleSignInIntent(context: Context): Intent = authManager.signInIntent(context)

    /** 로그인 ActivityResult 처리. */
    fun onGoogleSignInResult(data: Intent?) {
        authManager.handleSignInResult(data)
            .onSuccess { acc ->
                viewModelScope.launch {
                    if (cloudConfigured) _initialSyncing.value = true
                    // Firebase 설정 시: Google ID 토큰으로 Firebase 인증 → uid 를 계정 식별자로 사용
                    val finalAcc = if (cloudConfigured) {
                        val uid = authManager.lastIdToken?.let { CloudSync.signInWithGoogle(it) }
                        if (uid != null) acc.copy(id = uid) else acc
                    } else acc
                    // 로컬/클라우드 키를 uid 로 통일(영속) — 재실행 시에도 동일 계정 파일 사용
                    authManager.setAccount(finalAcc)
                    switchAccount(finalAcc)
                    cloudSyncPullOrSeed()
                    emitStatus("${finalAcc.name}님으로 로그인되었어요")
                }
            }
            .onFailure { e ->
                val code = (e as? ApiException)?.statusCode
                emitStatus(
                    when (code) {
                        12501 -> "로그인이 취소되었어요"          // SIGN_IN_CANCELLED
                        7 -> "네트워크 오류로 로그인에 실패했어요"   // NETWORK_ERROR
                        else -> "로그인 실패" + (code?.let { " (코드 $it)" } ?: "")
                    }
                )
            }
    }

    fun signOut() {
        authManager.signOut()
        switchAccount(Account.GUEST)
        emitStatus("로그아웃되었어요")
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
                uids.filterValues { it.isNotBlank() }.forEach { (key, uid) ->
                    val res = HoyolabApi.getLiveNote(cfg.ltuid, cfg.ltoken, key, uid)
                    res.note?.let { notes += it }
                }
                if (notes.isNotEmpty()) _liveNotes.value = notes
            }

            _isRefreshing.value = false
        }
    }

    /** 지출 탭 당겨서 새로고침: 로그인 상태면 클라우드에서 끌어와 병합, 항상 로컬 재로딩. */
    fun refreshSpending() {
        viewModelScope.launch {
            _isRefreshing.value = true
            if (cloudConfigured) {
                CloudSync.currentUid()?.let { uid ->
                    CloudSync.pull(uid)?.let { repo.importSnapshotJson(it) }
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

    private fun markCheckedIn(gameKey: String) {
        val today = todayKey()
        val current = (attendanceMap[today] ?: emptySet()) + gameKey
        attendanceMap = attendanceMap.toMutableMap().apply { put(today, current) }
        repo.saveAttendance(attendanceMap)
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

    private fun todayKey() = DateUtil.dayKey(System.currentTimeMillis())

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

    /** 로그인/시작 시 클라우드에서 끌어와 로컬에 병합. 원격이 비어 있으면 현재 로컬을 업로드. */
    private suspend fun cloudSyncPullOrSeed() {
        if (!cloudConfigured) { _initialSyncing.value = false; return }
        val uid = CloudSync.currentUid() ?: run { _initialSyncing.value = false; return }
        _initialSyncing.value = true
        try {
            val remote = CloudSync.pull(uid)
            if (remote != null) {
                repo.importSnapshotJson(remote)
                loadAll()
            } else {
                CloudSync.push(uid, repo.exportSnapshotJson())
            }
        } finally {
            _initialSyncing.value = false
        }
    }

    // 모든 프로퍼티 초기화 후 최초 로드 (init 순서 의존성 회피)
    init {
        repo.onChange = { scheduleCloudSync() }
        loadAll()
        // 이미 Firebase 로그인된 상태(앱 재실행)면 시작 시 클라우드 동기화
        if (cloudConfigured && CloudSync.currentUid() != null) {
            viewModelScope.launch { cloudSyncPullOrSeed() }
        }
    }
}
