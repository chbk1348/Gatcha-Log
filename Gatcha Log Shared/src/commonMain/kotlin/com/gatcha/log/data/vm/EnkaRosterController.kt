package com.gatcha.log.data.vm

import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.api.EnkaApi
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.EnkaResult
import com.gatcha.log.data.api.mergePartialRoster
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * '내 캐릭터' 로스터(Enka · mihomo · HoYoLAB) — 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고).
 *
 * @param repo 지금 계정의 저장소 — 계정을 바꾸면 뷰모델이 갈아끼우므로 **부를 때마다** 받아 온다.
 * @param ensureUids 연동됐는데 게임 UID 가 비어 있으면 1회 동기화(뷰모델이 HoYoLAB 설정까지 고친다).
 * @param persistDispatcher 저장을 한 줄로 세우는 디스패처 — 뷰모델의 지출 저장과 같은 줄을 쓴다.
 * @param prefetchCharCamps 로스터가 들어오면 소속을 미리 받아 둔다([CharMetaController.prefetchCharCamps]).
 */
internal class EnkaRosterController(
    private val scope: CoroutineScope,
    private val repo: () -> GatchaRepository,
    private val hoyolab: () -> HoyolabConfig,
    private val ensureUids: suspend () -> Unit,
    private val persistDispatcher: CoroutineDispatcher,
    private val prefetchCharCamps: (String, List<EnkaChar>) -> Unit,
) {
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
    private val enkaTtlMs = TTL_MS
    // 디스크 캐시 유효기간 — 이보다 오래된 디스크 항목은 시드에서 제외(앱 재시작 즉시표시 한계).
    private val enkaDiskTtlMs = 24 * 60 * 60 * 1000L

    /** 디스크 캐시 → 메모리 캐시 시드(계정별). loadAll 에서 호출. */
    /**
     * '내 캐릭터' 디스크 캐시를 메모리로 — 그리고 **화면 상태까지** 채운다.
     *
     * ⚠️ 예전엔 [enkaCache](내부 TTL 캐시)만 채우고 [_enkaResults](화면이 읽는 것)는 손대지 않았다.
     * 그런데 이 함수를 부르는 [loadAll] 은 바로 앞에서 `_enkaResults.value = emptyMap()` 으로
     * 화면 상태를 비운다 — 지우고 안 채운 셈이라 **섹션이 스켈레톤으로 남았다**
     * (`result == null` 이 스켈레톤 조건이다).
     *
     * [loadAll] 은 앱 시작만이 아니라 **클라우드 동기화가 끝날 때마다** 돈다. 백그라운드에서
     * 돌아오면 [onAppForeground] 가 '내 캐릭터 갱신 → 클라우드 동기화' 순으로 부르는데, 갱신이
     * 캐시 적중으로 빨리 끝나면 그 결과를 뒤이어 오는 동기화가 지워 버린다. 조회는 이미 '진행 중'
     * 판정([enkaInFlight])이나 신선도로 걸러져 다시 돌지 않으니, 다음 트리거까지 빈 채로 남았다.
     * 캐시는 멀쩡히 있는데 화면만 비는 상태 — 지우는 쪽이 아니라 **채우는 쪽을 고친다.**
     */
    private fun seedEnkaDiskCache() {
        enkaCache.clear()
        enkaCache.putAll(repo().loadEnkaCache(enkaDiskTtlMs))
        if (enkaCache.isEmpty()) return
        // 지금 UID 의 것만 싣는다 — 계정을 바꿨는데 남의 로스터가 잠깐 보이면 안 된다.
        val seeded = ENKA_GAMES.mapNotNull { game ->
            val uid = enkaUidFor(game).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            enkaCache["$game:$uid"]?.let { game to it.second }
        }
        if (seeded.isNotEmpty()) {
            _enkaResults.update { it + seeded }
            seeded.forEach { (g, r) -> prefetchCharCamps(g, r.profile?.chars.orEmpty()) }
        }
    }

    /** Enka UID 로 프로필 조회 + UID 계정별 영속(클라우드 동기화 포함). */
    fun loadEnkaProfile(game: String, uid: String) {
        val u = uid.trim()
        if (game == "genshin") _enkaGiUid.value = u else _enkaHsrUid.value = u
        repo().saveEnkaUids(_enkaGiUid.value, _enkaHsrUid.value)
        scope.launch {
            _enkaLoading.value = true
            val cfg = hoyolab()
            val r = withContext(Dispatchers.IO) { EnkaApi.fetchProfile(game, u, cfg.ltuid, cfg.ltoken) }
            val cached = enkaCache["$game:$u"]
            when {
                r.profile != null -> { enkaCache["$game:$u"] = currentTimeMillis() to r; persistEnkaCache(); _enkaResult.value = r }
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
        else -> hoyolab().zzzUid // 젠레스: 연동 계정 UID
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
        scope.launch {
            ensureUids() // 연동됐는데 UID 비면 1회 동기화 → 기존 사용자도 자동 로드
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
                val cfg = hoyolab()
                // 보여 줄 캐시가 없을 때만 **먼저 온 목록**을 받는다(27.51.1) — 섹션 쪽(autoLoadEnkaSection)과 같은 규칙이다.
                var partialShown = false
                val onPartial: ((EnkaResult) -> Unit)? = if (cached != null) null else { partial ->
                    if (enkaUidFor(game) == uid) {
                        // IO 스레드에서 불린다 — 상태 흐름만 건드린다(진영 미리 받기는 최종 결과에서 한다).
                        partialShown = true
                        _enkaResult.value = partial
                        _enkaResults.update { it + (game to partial) }
                        _enkaLoadingGames.update { it - game }
                    }
                }
                val got = withContext(Dispatchers.IO) { EnkaApi.fetchProfile(game, uid, cfg.ltuid, cfg.ltoken, onPartial) }
                if (enkaUidFor(game) != uid) return@launch   // 그 사이 계정이 바뀌었다 — 옛 로스터를 싣지 않는다
                val r = settle(game, cached?.second, got, partialShown)
                when {
                    r.profile != null -> {
                        enkaCache[key] = cacheStamp(r) to r; persistEnkaCache(); publishEnka(game, r)
                    }
                    // 실패 시 기존 캐시(신선/오래됨 무관) 유지 — 목록 사라짐 방지. 캐시도 먼저 그린 목록도 없을 때만 에러 표시.
                    cached == null && !partialShown -> publishEnka(game, r)
                }
            } finally {
                enkaInFlight.remove(key)
                _enkaLoading.value = false
                _enkaLoadingGames.update { it - game }
            }
        }
    }

    /**
     * 덜 받은 결과([EnkaResult.partial])는 **가진 목록 위에 얹는다**(27.52.0) — 캐시가 있으면 캐시, 없으면 방금 먼저 그린 목록.
     * HoYoLAB 이 잠깐 실패한 한 번으로 전체 로스터가 쇼케이스 8~12명으로 줄고 캐시까지 덮이던 일을 막는다.
     */
    private fun settle(game: String, cached: EnkaResult?, got: EnkaResult, partialShown: Boolean): EnkaResult =
        mergePartialRoster(cached ?: _enkaResults.value[game].takeIf { partialShown }, got)

    /** 캐시에 적을 시각. 덜 받은 결과는 [PARTIAL_RETRY_MS] 뒤면 낡은 것으로 쳐서 곧 다시 받는다. */
    private fun cacheStamp(r: EnkaResult): Long =
        if (r.partial) currentTimeMillis() - enkaTtlMs + PARTIAL_RETRY_MS else currentTimeMillis()

    /**
     * Enka 캐시 디스크 저장 — **메인 밖에서.** 3게임 풀스탯이라 수백 KB 를 직렬화하는데, 예전엔 복귀 때마다
     * 메인에서 돌아 화면이 멎었다. 사본을 떠서 넘긴다(메인이 계속 고치는 맵을 IO 에서 읽으면 안 된다).
     */
    private fun persistEnkaCache() {
        val snapshot = enkaCache.toMap()
        val target = repo()
        scope.launch(persistDispatcher) { target.saveEnkaCache(snapshot) }   // 한 줄로 — 늦게 뜬 사본이 먼저 끝나 새 캐시를 덮지 않게
    }

    /** 조회 결과를 단일 슬롯과 게임별 맵에 함께 싣는다(둘이 갈리면 화면마다 다른 걸 본다). */
    private fun publishEnka(game: String, result: EnkaResult) {
        _enkaResult.value = result
        _enkaResults.update { it + (game to result) }
        prefetchCharCamps(game, result.profile?.chars.orEmpty())
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
    fun autoLoadEnkaSection(games: List<String>, force: Boolean = false, ttlMs: Long = enkaTtlMs) {
        scope.launch {
            // 연동됐는데 UID 가 비어 있으면 1회 동기화한다. **UID 가 이미 있는 게임은 그것을 기다리지 않는다**(27.51.1) —
            // 예전엔 세 게임 중 하나만 UID 가 비어도(그 게임을 안 하는 사용자) 실행할 때마다 HoYoLAB 1~2건을 먼저 받고 나서야
            // 나머지 게임의 캐릭터 목록 요청이 나갔다. 빈 게임만 동기화가 끝나기를 기다린다.
            val uidSync = async(start = CoroutineStart.LAZY) { ensureUids() }
            games.map { game ->
                async {
                    if (enkaUidFor(game).isBlank()) uidSync.await()
                    val uid = enkaUidFor(game)
                    if (uid.isBlank()) {
                        _enkaResults.update { it + (game to EnkaResult(profile = null, error = null)) }
                        return@async
                    }
                    val key = "$game:$uid"
                    val cached = enkaCache[key]   // 디스크 시드 포함
                    val fresh = cached != null && currentTimeMillis() - cached.first < ttlMs
                    // 캐시(신선/오래됨 무관)가 있으면 즉시 표시 — stale-while-revalidate
                    if (cached != null) {
                        _enkaResults.update { it + (game to cached.second) }
                        prefetchCharCamps(game, cached.second.profile?.chars.orEmpty())
                        if (fresh && !force) return@async   // 신선하면 네트워크 생략
                    } else {
                        _enkaLoadingGames.update { it + game }   // 보여줄 캐시가 없을 때만 스피너
                    }
                    if (!enkaInFlight.add(key)) return@async   // 같은 조회가 이미 진행 중
                    try {
                    val cfg = hoyolab()
                    // 보여 줄 캐시가 없을 때만 **먼저 온 부분 결과**를 받는다 — 스타레일은 느린 mihomo 를, 원신은 전원 상세를,
                    // 젠레스는 1명당 1요청인 상세를 기다리지 않고 HoYoLAB 보유 목록으로 먼저 그린다.
                    // 캐시가 있으면 이미 그려져 있고, 부분 결과로 갈아끼우면 닉네임 · 스탯이 잠깐 빠진다.
                    var partialShown = false
                    val onPartial: ((EnkaResult) -> Unit)? = if (cached != null) null else { partial ->
                        if (enkaUidFor(game) == uid) {
                            partialShown = true
                            _enkaResults.update { it + (game to partial) }
                            _enkaLoadingGames.update { it - game }
                        }
                    }
                    val got = withContext(Dispatchers.IO) { EnkaApi.fetchProfile(game, uid, cfg.ltuid, cfg.ltoken, onPartial) }
                    if (enkaUidFor(game) != uid) return@async   // 그 사이 계정이 바뀌었다 — 옛 로스터를 싣지 않는다
                    val r = settle(game, cached?.second, got, partialShown)
                    if (r.profile != null) {
                        enkaCache[key] = cacheStamp(r) to r
                        _enkaResults.update { it + (game to r) }   // 갱신분 반영
                        prefetchCharCamps(game, r.profile?.chars.orEmpty())
                    } else if (cached == null && !partialShown) {
                        // 캐시도 먼저 그린 목록도 없고 실패 → 에러 표시. 먼저 그린 목록이 있으면 그대로 둔다(상세만 못 받은 것이다).
                        _enkaResults.update { it + (game to r) }
                    }
                    } finally {
                        enkaInFlight.remove(key)
                        _enkaLoadingGames.update { it - game }
                    }
                }
            }.awaitAll()
            uidSync.cancel()   // 아무도 기다리지 않았으면 시작도 안 했다 — 남겨 두면 이 코루틴이 끝나지 않는다
            persistEnkaCache()   // 갱신된 캐시 디스크 영속(1회)
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
    fun characterNamesByGame(): Map<String, Map<Int, String>> =
        _enkaResults.value.mapValues { (_, r) ->
            r.profile?.chars?.associate { it.id to it.name }.orEmpty()
        }

    /** 저장소를 다시 읽을 때(시작 · 계정 전환 · 동기화 뒤) — UID 를 그 계정 것으로 맞추고, 화면 상태를 비운 뒤 디스크 캐시로 다시 채운다. */
    fun reload() {
        reloadUids()
        _enkaResult.value = null
        _enkaResults.value = emptyMap()
        seedEnkaDiskCache()   // 재시작/계정전환 시 디스크 캐시를 메모리로 — '내 캐릭터' 즉시 표시
    }

    /** 게임별 UID 한 벌 — 동기화 앞뒤를 견주는 데 쓴다([reloadAfterSync]). */
    fun uidSnapshot(): Map<String, String> = ENKA_GAMES.associateWith { enkaUidFor(it) }

    /**
     * 동기화 뒤 — UID 만 다시 읽고, **UID 가 실제로 바뀐 게임만** 화면 상태를 갈아끼운다(27.52.0).
     *
     * 예전엔 동기화가 끝날 때마다 [reload] 로 결과를 통째로 비우고 디스크 캐시가 있는 게임만 다시 채웠다.
     * 그래서 UID 가 없는 게임의 "게임 UID 가 없어요" 표시와 조회 실패 표시가 지워져, 그 칸이 탭을 나갔다 들어올
     * 때까지 스켈레톤으로 흘렀다. 방금 받은 로스터도 캐시에 실리기 전이면 함께 지워졌다.
     */
    fun reloadAfterSync(before: Map<String, String>) {
        reloadUids()
        ENKA_GAMES.forEach { game ->
            val uid = enkaUidFor(game)
            if (uid == before[game]) return@forEach
            val cached = enkaCache["$game:$uid"]?.second
            _enkaResults.update { if (cached != null) it + (game to cached) else it - game }
        }
    }

    /** 저장소의 UID 만 다시 읽는다 — 게스트의 연동을 계정으로 옮긴 직후. */
    fun reloadUids() {
        _enkaGiUid.value = repo().loadEnkaGiUid()
        _enkaHsrUid.value = repo().loadEnkaHsrUid()
    }

    /** HoYoLAB 이 알려 준 게임 UID 를 싣고 저장한다(빈 값은 건드리지 않는다). */
    fun applyUids(genshin: String?, hsr: String?) {
        genshin?.takeIf { it.isNotBlank() }?.let { _enkaGiUid.value = it }
        hsr?.takeIf { it.isNotBlank() }?.let { _enkaHsrUid.value = it }
        repo().saveEnkaUids(_enkaGiUid.value, _enkaHsrUid.value)
    }

    companion object {
        /** 로스터 신선도 — Enka 429 방지 위해 5분 내 재요청 생략. */
        const val TTL_MS = 5 * 60 * 1000L
        /** 덜 받은 로스터([EnkaResult.partial])를 다시 받기까지 — 5분을 다 채우지 않는다. */
        const val PARTIAL_RETRY_MS = 60 * 1000L

        /** Enka 가 보유 캐릭터를 주는 게임 — 나머지는 상류가 주지 않는다. */
        val ENKA_GAMES = listOf("genshin", "hsr", "zzz")
    }
}
