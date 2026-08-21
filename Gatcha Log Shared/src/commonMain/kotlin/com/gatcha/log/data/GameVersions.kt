package com.gatcha.log.data

import com.gatcha.log.data.api.NanokaApi

/**
 * 지금 게임에서 돌고 있는 버전 — 데일리 타일 아래 한 줄.
 *
 * 원래 '새로 나온 것'(`NewContent`)에 얹혀 있었다. 그 기능이 통째로 걷힐 때 같이 사라질 뻔했는데,
 * 이건 신규 목록과 답하는 질문이 다르다 — "이번 버전에 뭐가 늘었나"가 아니라 **"지금 몇 버전이지"** 다.
 * 매니페스트 한 건이면 되는 값이라 남길 값이 있어 따로 옮겼다.
 */
object GameVersions {

    /** nanoka 게임 키 → 앱의 [Game]. 엔드필드는 nanoka 에 없다. */
    private val gameOf = mapOf(
        "gi" to Game.GENSHIN,
        "hsr" to Game.HSR,
        "zzz" to Game.ZZZ,
        "ww" to Game.WUWA,
        "nte" to Game.NTE,
    )

    /**
     * nanoka 가 버전을 주는 **다섯 게임 전부**(원신·스타레일·젠레스·명조·이환).
     *
     * 예전엔 출석 대상 3게임만 냈다 — 타일이 그 세 게임 이야기라는 이유였는데, "지금 몇 버전이지"는
     * 출석과 무관한 질문이고 명조·이환도 가챠 도구·일정에서 함께 다루는 게임이다.
     * 약칭이 GI·HSR·ZZZ·WW·NTE 로 전부 3글자 이하라 다섯이어도 한 줄에 들어간다.
     *
     * **엔드필드는 빠진다** — nanoka 에 데이터가 없다([gameOf] 에도 없어 자동으로 걸러진다).
     * 공지 제목에서 버전을 긁는 우회는 형식이 바뀌면 조용히 깨져서 쓰지 않는다.
     *
     * `live` 를 쓴다(`latest` 아님) — 사용자가 게임을 켜서 보는 숫자여야 한다. 데이터 최신
     * 버전은 아직 출시 전일 수 있다.
     */
    suspend fun live(): List<GameVersionLine> {
        val manifest = NanokaApi.manifest() ?: return emptyList()
        return GameData.games.mapNotNull { game ->
            val key = gameOf.entries.firstOrNull { it.value == game }?.key ?: return@mapNotNull null
            val v = manifest.games[key]?.displayVersion?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GameVersionLine(gameKey = game.key, gameShort = game.shortName, colorArgb = game.color, version = v)
        }
    }
}

/** 게임 하나의 현재 버전 — "원신 7.0". */
data class GameVersionLine(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    /** 인게임 버전 표기("7.0"). */
    val version: String,
)
