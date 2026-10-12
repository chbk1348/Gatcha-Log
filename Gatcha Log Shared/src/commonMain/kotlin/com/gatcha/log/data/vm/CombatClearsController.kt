package com.gatcha.log.data.vm

import com.gatcha.log.data.CombatClear
import com.gatcha.log.data.CombatClearLogic
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.api.EnkaApi
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.data.api.NanokaApi
import com.gatcha.log.data.mergeByGame
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 엔드 콘텐츠 클리어 편성(층·간별로 어떤 캐릭터를 썼는지) — 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고).
 *
 * 시즌 2개치 × 모드 4~6개라 호출이 무겁다. 전용 페이지에 들어갈 때만 받고, 화면은 캐시부터 그린다.
 *
 * @param ownedNames 보유 캐릭터 캐시의 이름(게임 키별) — [EnkaRosterController.characterNamesByGame].
 */
internal class CombatClearsController(
    private val scope: CoroutineScope,
    private val repo: () -> GatchaRepository,
    private val hoyolab: () -> HoyolabConfig,
    private val ownedNames: () -> Map<String, Map<Int, String>>,
) {
    // 시즌 2개치 × 모드 4~6개라 호출이 무겁다. 전용 페이지에 들어갈 때만 받고, 화면은 캐시부터 그린다.
    private val _combatClears = MutableStateFlow<List<CombatClear>>(emptyList())
    val combatClears: StateFlow<List<CombatClear>> = _combatClears.asStateFlow()

    private val _combatClearsLoading = MutableStateFlow(false)
    val combatClearsLoading: StateFlow<Boolean> = _combatClearsLoading.asStateFlow()

    /** 마지막 조회가 전부 실패했다 — 화면은 '기록 없음'이 아니라 '불러오지 못함'을 보여야 한다. */
    private val _combatClearsFailed = MutableStateFlow(false)
    val combatClearsFailed: StateFlow<Boolean> = _combatClearsFailed.asStateFlow()

    /** 마지막으로 클리어 편성을 받아온 시각 — 페이지 재진입마다 다시 받지 않게. */
    private var lastCombatClearAt = 0L
    private var combatClearsJob: Job? = null

    /**
     * 엔드 콘텐츠 클리어 편성 조회. 캐시가 신선하면(30분) 건너뛴다.
     *
     * 이름 채우기: HoYoLAB 전투 응답에는 캐릭터 **이름이 없다**(id·아이콘뿐). 이미 받아 둔
     * 보유 캐릭터([enkaResults])가 같은 id 체계를 쓰므로 거기서 이름을 끌어온다.
     */
    fun refreshCombatClears(force: Boolean = false) {
        if (_combatClearsLoading.value) return
        val cfg = hoyolab()
        if (!cfg.isLinked) return
        val now = currentTimeMillis()
        if (!force && _combatClears.value.isNotEmpty() && now - lastCombatClearAt < COMBAT_CLEAR_FRESH_MS) return
        combatClearsJob = scope.launch {
            _combatClearsLoading.value = true
            try {
                val uids = cfg.linkedUids
                // 캐릭터 이름 메타(yatta)는 편성 결과와 무관하다 — **편성과 같이 쏜다**(27.51.1). 예전엔 편성을 다 받은 뒤에야
                // 나가서 한 왕복이 더 붙었다. 편성을 못 받아 일찍 빠져나가면 아래에서 끊는다.
                val metaNamesDeferred = async {
                    runCatching {
                        coroutineScope {
                            val gi = async(Dispatchers.IO) { EnkaApi.characterNames(hsr = false) }
                            val hsr = async(Dispatchers.IO) { EnkaApi.characterNames(hsr = true) }
                            mapOf(Game.GENSHIN.key to gi.await(), Game.HSR.key to hsr.await())
                        }
                    }.getOrDefault(emptyMap())
                }
                // 게임별로 받는다 — null 은 실패. 예전엔 실패를 빈 목록으로 뭉개서 '기록 없음'으로 보였고,
                // 한 게임만 실패해도 다른 게임 결과만으로 통째로 갈아끼워 그 게임 편성이 사라졌다.
                val perGame = coroutineScope {
                    uids.map { (key, uid) ->
                        async(Dispatchers.IO) { HoyolabApi.getCombatClears(cfg.ltuid, cfg.ltoken, key, uid) }
                    }.awaitAll()
                }
                val loadedGames = uids.keys.zip(perGame).filter { it.second != null }
                    .map { GameData.byName(it.first).displayName }.toSet()
                _combatClearsFailed.value = uids.isNotEmpty() && loadedGames.isEmpty()
                if (loadedGames.isEmpty()) { metaNamesDeferred.cancel(); return@launch }
                val fetched = perGame.filterNotNull().flatten()
                // 이름 출처는 두 겹이다. ①전체 캐릭터 메타(yatta) — 쇼케이스에 없는 캐릭터까지 덮는다.
                // ②보유 캐릭터 캐시 — 메타에 아직 없는 신규 캐릭터를 보완한다(우선순위가 더 높다).
                //
                // ⚠️ 두 겹 모두 **게임별로** 유지한다. 캐릭터 id 공간이 게임마다 독립이라
                // 하나로 합치면 스타레일 1xxx 와 젠레스 1xxx 가 충돌한다(달리아 → 이블린 사고).
                val metaNames = metaNamesDeferred.await()
                val ownedNames = ownedNames()
                // 게임별로 메타 위에 보유 캐시를 덮는다(신규 캐릭터가 메타보다 먼저 들어온다).
                val namesByGame = (metaNames.keys + ownedNames.keys).associateWith { key ->
                    metaNames[key].orEmpty() + ownedNames[key].orEmpty()
                }
                // 젠레스 뱅부 이름 — 요원 이름 출처(메타 · 보유 캐시)에는 뱅부가 없어 nanoka 도감에서 id 로 받는다(9/30).
                val buddyIds = fetched.flatMap { c -> c.rooms.flatMap { it.firstHalf + it.secondHalf } }
                    .filter { it.isBuddy && it.name.isBlank() }.map { it.id }.distinct()
                val buddyNames = runCatching {
                    coroutineScope {
                        buddyIds.map { id -> async(Dispatchers.IO) { id to NanokaApi.bangbooName(id) } }.awaitAll()
                    }.mapNotNull { (id, n) -> n?.let { id to it } }.toMap()
                }.getOrDefault(emptyMap())
                val named = CombatClearLogic.withNames(fetched, namesByGame).map { c ->
                    if (buddyNames.isEmpty()) c else c.copy(rooms = c.rooms.map { r ->
                        r.copy(
                            firstHalf = r.firstHalf.map { a -> if (a.isBuddy && a.name.isBlank()) buddyNames[a.id]?.let { a.copy(name = it) } ?: a else a },
                            secondHalf = r.secondHalf.map { a -> if (a.isBuddy && a.name.isBlank()) buddyNames[a.id]?.let { a.copy(name = it) } ?: a else a },
                        )
                    })
                }
                val grouped = CombatClearLogic.grouped(
                    mergeByGame(_combatClears.value, named, loadedGames) { it.game },
                )
                _combatClears.value = grouped
                lastCombatClearAt = currentTimeMillis()
                runCatching { repo().saveCombatClears(grouped) }
            } finally {
                _combatClearsLoading.value = false
            }
        }
    }

    /** 디스크 캐시로 먼저 채운다 — 전용 페이지가 빈 화면으로 열리지 않게. */
    fun seed(clears: List<CombatClear>) { _combatClears.value = clears }

    /** 계정 전환 · 연동 해제 — 다른 사람 것이 되므로 비운다. */
    fun reset() {
        _combatClears.value = emptyList()
        _combatClearsFailed.value = false
        lastCombatClearAt = 0L
    }

    /** 도는 조회를 끊는다 — 옛 계정 토큰으로 받은 편성이 새 계정에 실리지 않게. */
    fun cancel() { combatClearsJob?.cancel() }

    private companion object {
        /** 엔드 콘텐츠 클리어 편성 신선도(ms). 시즌 단위로 바뀌는 데이터라 넉넉히 잡는다. */
        const val COMBAT_CLEAR_FRESH_MS = 30L * 60 * 1000
    }
}
