package com.gatcha.log.data.vm

import com.gatcha.log.data.GameVersionLine
import com.gatcha.log.data.GameVersions
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.NanokaApi
import com.gatcha.log.data.api.WeaponRefinement
import kotlinx.coroutines.CoroutineScope
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
 * 도감(nanoka)에서 받는 곁가지 자료 — 현재 게임 버전 · 무기 정련 효과 · 캐릭터 소속.
 * 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고).
 */
internal class CharMetaController(
    private val scope: CoroutineScope,
    private val repo: () -> GatchaRepository,
) {
    private val _gameVersions = MutableStateFlow<List<GameVersionLine>>(emptyList())
    /** 지금 돌고 있는 게임 버전(출석 3게임) — 데일리 타일 아래 한 줄. */
    val gameVersions: StateFlow<List<GameVersionLine>> = _gameVersions.asStateFlow()

    private var gameVersionsLoaded = false

    private val _gameVersionsLoading = MutableStateFlow(false)
    /**
     * 현재 버전을 **처음 받아오는 중**인가 — 화면이 그동안 스켈레톤을 세운다.
     *
     * 값이 없을 때만 켠다. 이미 한 줄이 서 있는데 갱신마다 스켈레톤으로 되돌리면 카드가
     * 껌뻑인다 — 이 값은 몇 주에 한 번 바뀌므로 옛 값을 그대로 둬도 틀리지 않는다.
     */
    val gameVersionsLoading: StateFlow<Boolean> = _gameVersionsLoading.asStateFlow()

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
        if (_gameVersions.value.isEmpty()) _gameVersionsLoading.value = true
        scope.launch {
            val versions = withContext(Dispatchers.IO) { GameVersions.live() }
            if (versions.isEmpty()) gameVersionsLoaded = false else _gameVersions.value = versions
            // 실패해도 끈다 — 다음 진입에서 다시 받으므로, 여기서 켜 둔 채로 두면 네트워크가
            // 죽은 동안 스켈레톤이 영원히 흐른다.
            _gameVersionsLoading.value = false
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
    /** 정련 효과 조회가 진행 중인 키. 결과 맵과 별개다 — 맵은 응답이 온 뒤에야 채워진다. */
    private val refinementInFlight = mutableSetOf<String>()

    // 저장해 둔 값으로 시작한다 — 화면이 뜨는 순간부터 배지가 보이게. 도감 응답은 그 뒤에 채운다.
    private val _charCamp = MutableStateFlow(repo().loadCharCamps())

    /** 캐릭터 소속(진영) — 키는 "게임키:캐릭터id". 젠레스는 응답이 직접 주므로 여기 없다. */
    val charCamp: StateFlow<Map<String, String>> = _charCamp.asStateFlow()
    private val charCampInFlight = mutableSetOf<String>()

    /**
     * 로스터가 들어오면 **소속을 미리 받아 둔다.**
     *
     * 상세에 들어선 뒤에 부르면 도감 응답을 기다리는 1초 남짓 동안 배지 자리가 비었다가
     * 뒤늦게 튀어나온다(2026-09-09 제보). 목록을 받는 시점에 미리 채워 두면 상세는 이미
     * 손에 든 값을 그린다.
     *
     * 캐시에 있는 것은 건너뛰므로 캐릭터당 **한 번만** 나간다. 도감은 정적 파일이라
     * 부담이 적지만, 그래도 한꺼번에 쏟지 않게 넷씩 끊어 보낸다.
     */
    fun prefetchCharCamps(gameKey: String, chars: List<EnkaChar>) {
        if (gameKey != "genshin" && gameKey != "hsr" && gameKey != "starrail") return
        val need = chars.map { it.id }.filter { it > 0 && !_charCamp.value.containsKey("$gameKey:$it") }
        if (need.isEmpty()) return
        scope.launch {
            need.chunked(4).forEach { batch ->
                val found = withContext(Dispatchers.IO) {
                    batch.map { id -> async { id to NanokaApi.charCamp(gameKey, id) } }.awaitAll()
                }
                // ⚠️ **소속이 없는 캐릭터도 기록한다.** 값이 있는 것만 넣었더니, 도감에 없거나
                // (404) 지역 코드를 모르는 캐릭터는 맵에 영영 안 들어가서 로스터가 갱신될 때마다
                // 같은 요청이 다시 나갔다 — 실측으로 같은 id 를 여섯 번까지 불렀다.
                // 빈 문자열이 "물어봤고 없더라"는 뜻이다. 화면은 빈 값을 그리지 않는다.
                val add = found.map { (id, camp) -> "$gameKey:$id" to camp.orEmpty() }
                _charCamp.update { it + add }
                repo().saveCharCamps(_charCamp.value)
            }
        }
    }

    /**
     * 캐릭터 소속을 도감에서 받아 온다 — 원신·스타레일만.
     *
     * 상세 화면에 들어설 때 한 캐릭터만 부른다. 목록에서 부르면 캐릭터 수만큼 요청이 나간다.
     * 정련([loadWeaponRefinement])과 같은 규칙으로 중복 요청을 막는다.
     */
    fun loadCharCamp(gameKey: String, charId: Int) {
        if (charId <= 0) return
        val key = "$gameKey:$charId"
        if (_charCamp.value.containsKey(key)) return
        if (!charCampInFlight.add(key)) return
        scope.launch {
            try {
                // 없어도 기록한다 — 재요청을 막는다([prefetchCharCamps] 주석 참고).
                val camp = withContext(Dispatchers.IO) { NanokaApi.charCamp(gameKey, charId) }
                _charCamp.update { it + (key to camp.orEmpty()) }
                repo().saveCharCamps(_charCamp.value)
            } finally {
                charCampInFlight.remove(key)
            }
        }
    }

    fun loadWeaponRefinement(gameKey: String, weaponId: Int, level: Int) {
        if (weaponId <= 0) return
        val key = "$gameKey:$weaponId:$level"
        if (_weaponRefinement.value.containsKey(key)) return
        // 결과 맵만 보면 **응답이 오기 전까지는 계속 '없음'** 이라, 그 사이 같은 키가 다시 들어오면
        // (화면 재구성·상세 재진입) 같은 요청이 겹친다. [enkaInFlight] 와 같은 방식으로 막는다.
        if (!refinementInFlight.add(key)) return
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { NanokaApi.refinement(gameKey, weaponId, level) } ?: return@launch
                _weaponRefinement.update { it + (key to r) }
            } finally {
                refinementInFlight.remove(key)
            }
        }
    }
}
