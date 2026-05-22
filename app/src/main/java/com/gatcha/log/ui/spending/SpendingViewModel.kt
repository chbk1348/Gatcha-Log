package com.gatcha.log.ui.spending

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gatcha.log.data.Account
import com.gatcha.log.data.AuthManager
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.PityState
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.Spending
import com.gatcha.log.data.UserProfile
import com.gatcha.log.data.api.EnneadApi
import com.gatcha.log.data.api.HoyolabApi
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 모든 화면이 공유하는 단일 ViewModel.
 * 로그인 계정(AuthManager)별로 분리된 로컬 저장소(GatchaRepository)와 동기화된다.
 */
class SpendingViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager = AuthManager(app)
    /** 현재 로그인 계정 (게스트 = 비로그인 로컬) */
    val account: StateFlow<Account> = authManager.account

    // 계정별로 분리되는 저장소. 계정 전환 시 교체된다.
    private var repo: GatchaRepository = GatchaRepository(app, account.value.id)

    // ----------------------------------------------------------------- 상태 (계정별 로드)
    private val _spendings = MutableStateFlow<List<Spending>>(emptyList())
    val spendings: StateFlow<List<Spending>> = _spendings.asStateFlow()

    private val _budget = MutableStateFlow(300_000L)
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

    /** 현재 repo(계정)의 모든 데이터를 상태로 로드. */
    private fun loadAll() {
        _spendings.value = repo.loadSpendings()
        _budget.value = repo.loadBudget()
        _profile.value = repo.loadProfile()
        _hoyolabConfig.value = repo.loadHoyolab()
        _accentIndex.value = repo.loadAccentIndex()
        attendanceMap = repo.loadAttendance()
        _attendanceToday.value = attendanceMap[todayKey()] ?: emptySet()
        _wishlist.value = repo.loadWishlist()
        _pity.value = repo.loadPity()
        _eventChecks.value = repo.loadEventChecks()
    }

    // ----------------------------------------------------------------- 계정 (구글 로그인)
    /** UI에서 ActivityResultLauncher로 실행할 로그인 인텐트. */
    fun googleSignInIntent(context: Context): Intent = authManager.signInIntent(context)

    /** 로그인 ActivityResult 처리. */
    fun onGoogleSignInResult(data: Intent?) {
        authManager.handleSignInResult(data)
            .onSuccess { acc ->
                switchAccount(acc)
                emitStatus("${acc.name}님으로 로그인되었어요")
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
    fun addSpending(spending: Spending) = _spendings.update { current ->
        (listOf(spending) + current).sortedByDescending { it.dateMillis }.also(repo::saveSpendings)
    }

    fun updateSpending(updated: Spending) = _spendings.update { current ->
        current.map { if (it.id == updated.id) updated else it }
            .sortedByDescending { it.dateMillis }
            .also(repo::saveSpendings)
    }

    fun deleteSpending(id: String) = _spendings.update { current ->
        current.filter { it.id != id }.also(repo::saveSpendings)
    }

    fun deleteSpendings(ids: Set<String>) = _spendings.update { current ->
        current.filter { it.id !in ids }.also(repo::saveSpendings)
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
    }

    fun isCheckedIn(gameKey: String): Boolean = gameKey in _attendanceToday.value

    // ----------------------------------------------------------------- 배너 / 실시간 노트
    private val now = System.currentTimeMillis()
    private val day = 1000L * 60 * 60 * 24

    private val _activeBanners = MutableStateFlow(
        listOf(
            GachaBanner("원신", "아를레키노", "character", now + 5 * day),
            GachaBanner("붕괴: 스타레일", "로빈", "character", now + 12 * day),
        )
    )
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

    /** 출석체크 시도. HoYoLAB 연동 시 실제 API 호출, 미연동 시 로컬 수동 토글. */
    fun attemptCheckIn(gameKey: String) {
        val cfg = _hoyolabConfig.value
        if (!cfg.isLinked) {
            toggleAttendance(gameKey)
            emitStatus("수동 출석 처리 (HoYoLAB 미연동)")
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            val r = HoyolabApi.checkIn(cfg.ltuid, cfg.ltoken, gameKey)
            if (r.success) markCheckedIn(gameKey)
            emitStatus(r.message)
            _isRefreshing.value = false
        }
    }

    private fun markCheckedIn(gameKey: String) {
        val today = todayKey()
        val current = (attendanceMap[today] ?: emptySet()) + gameKey
        attendanceMap = attendanceMap.toMutableMap().apply { put(today, current) }
        repo.saveAttendance(attendanceMap)
        _attendanceToday.value = current
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

    // 모든 프로퍼티 초기화 후 최초 로드 (init 순서 의존성 회피)
    init {
        loadAll()
    }
}
