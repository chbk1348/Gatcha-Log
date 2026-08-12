package com.gatcha.log.data

import com.gatcha.log.data.api.NanokaApi
import com.gatcha.log.data.api.NanokaGame

/**
 * "이번 버전에 새로 나온 것" — 게임별 신규 캐릭터·무기·방부 등.
 *
 * 출처는 nanoka 매니페스트([NanokaApi.manifest])의 `new` 목록 하나뿐이다. 이름은 id 로 단건을
 * 받아 채운다 — 목록 API 가 없어 그 방법밖에 없다.
 *
 * ## 픽업 배너와 다른 것
 *
 * **여기엔 기간이 없다.** "7.0에 알료샤가 추가됐다"는 알 수 있어도 "언제부터 언제까지 픽업"은
 * 알 수 없다. 그건 [ScheduleLogic](ennead) 몫이고, 두 화면이 답하는 질문이 다르다.
 * 기간을 모르는 걸 일정처럼 그리면 안 된다.
 */
object NewContent {

    /** nanoka 게임 키 → 앱의 [Game]. 엔드필드는 nanoka 에 없다. */
    private val gameOf = mapOf(
        "gi" to Game.GENSHIN,
        "hsr" to Game.HSR,
        "zzz" to Game.ZZZ,
        "ww" to Game.WUWA,
        "nte" to Game.NTE,
    )

    /**
     * 화면에 올릴 타입과 한국어 라벨.
     *
     * monster·item·furniture·gcg 는 뺀다 — 버전마다 수십 건씩 쏟아지는데 트래커 사용자가
     * 찾는 정보가 아니다. 목록이 길어지면 정작 신규 캐릭터가 묻힌다.
     */
    private val typeLabels = mapOf(
        "character" to "캐릭터",
        "weapon" to "무기",
        "lightcone" to "광추",
        "bangboo" to "방부",
        "echo" to "에코",
        "artifact" to "성유물",
        "relicset" to "유물",
    )

    /** 타입 하나에서 이름을 받아올 최대 개수. 원신 7.0 무기가 31건이라 그대로 받으면 요청이 폭발한다. */
    private const val NAMES_PER_TYPE = 8

    /**
     * 게임별 신규 목록. 네트워크를 타므로 화면 진입 시 1회만 부른다.
     *
     * 이름을 못 받은 항목은 **버리지 않고 개수로 남긴다** — 비호요 게임은 한국어가 비어 있을 때가
     * 있는데(이환 캐릭터가 그렇다), 그렇다고 "신규 없음"으로 보이면 사실과 다르다.
     */
    suspend fun load(): List<NewContentGame> {
        val manifest = NanokaApi.manifest() ?: return emptyList()
        return gameOf.entries.mapNotNull { (nanokaKey, game) ->
            val g = manifest.games[nanokaKey] ?: return@mapNotNull null
            val groups = buildGroups(nanokaKey, g)
            if (groups.isEmpty()) return@mapNotNull null
            NewContentGame(
                gameKey = game.key,
                gameShort = game.shortName,
                colorArgb = game.color,
                version = g.displayVersion,
                groups = groups,
            )
        }
    }

    private suspend fun buildGroups(nanokaKey: String, g: NanokaGame): List<NewContentGroup> =
        typeLabels.mapNotNull { (type, label) ->
            val ids = g.new[type].orEmpty()
            if (ids.isEmpty()) return@mapNotNull null
            val named = ids.take(NAMES_PER_TYPE).mapNotNull { id ->
                NanokaApi.nameOf(nanokaKey, type, id)?.let { NewContentItem(id, it) }
            }
            NewContentGroup(type = type, label = label, total = ids.size, items = named)
        }

    /**
     * 지금 게임에서 돌고 있는 버전 — 데일리 타일 아래 한 줄.
     *
     * **출석 대상 3게임만** 낸다. 타일(출석·전투 진행도·클리어 편성)이 그 세 게임의 이야기라
     * 맥락이 맞고, 다섯 게임을 다 적으면 한 줄에 안 들어가 두 줄로 접힌다.
     *
     * `live` 를 쓴다(`latest` 아님) — 사용자가 게임을 켜서 보는 숫자여야 한다. 데이터 최신
     * 버전은 아직 출시 전일 수 있다.
     */
    suspend fun liveVersions(): List<GameVersionLine> {
        val manifest = NanokaApi.manifest() ?: return emptyList()
        return GameData.attendanceGames.mapNotNull { game ->
            val key = gameOf.entries.firstOrNull { it.value == game }?.key ?: return@mapNotNull null
            val v = manifest.games[key]?.displayVersion?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GameVersionLine(gameKey = game.key, gameShort = game.shortName, colorArgb = game.color, version = v)
        }
    }

    /**
     * 아직 보지 않은 항목이 있는가 — 진입 카드의 점 표시용.
     *
     * 판정은 **id 집합 비교**다. 버전 문자열로 하면 같은 버전 안에서 항목이 추가될 때(핫픽스로
     * 캐릭터가 늘어난다) 놓친다.
     */
    fun hasUnseen(games: List<NewContentGame>, seenIds: Set<String>): Boolean =
        games.any { g -> g.groups.any { grp -> grp.items.any { "${g.gameKey}:${it.id}" !in seenIds } } }

    /** 지금 보이는 항목 전부를 '봤음'으로 만들 키 집합. */
    fun seenKeys(games: List<NewContentGame>): Set<String> =
        games.flatMap { g -> g.groups.flatMap { grp -> grp.items.map { "${g.gameKey}:${it.id}" } } }.toSet()
}

/** 게임 하나의 신규 묶음. */
data class NewContentGame(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    /** 인게임 버전 표기("7.0"). */
    val version: String,
    val groups: List<NewContentGroup>,
)

/** 타입 하나("캐릭터")의 신규 항목. */
data class NewContentGroup(
    val type: String,
    val label: String,
    /** 이번 버전 신규 **전체** 개수. [items] 보다 클 수 있다(이름 조회를 제한한다). */
    val total: Int,
    val items: List<NewContentItem>,
) {
    /** 이름을 못 실은 나머지 개수. 0 이면 전부 실었다. */
    val hidden: Int get() = (total - items.size).coerceAtLeast(0)
}

/** 신규 항목 하나. */
data class NewContentItem(val id: String, val name: String)

/** 게임 하나의 현재 버전 — "원신 7.0". */
data class GameVersionLine(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val version: String,
)
