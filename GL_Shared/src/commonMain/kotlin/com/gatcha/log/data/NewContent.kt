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
            // 목록에 낼 게 아니라고 판정한 수. 이름을 못 받은 것과 **구분해서** 센다 —
            // 전자는 총계에서 빼야 하고(애초에 신규가 아니다), 후자는 "N개 (이름 준비 중)"로 남는다.
            var dropped = 0
            val resolved = ids.take(NAMES_PER_TYPE).mapNotNull { id ->
                val o = NanokaApi.entity(nanokaKey, type, id) ?: return@mapNotNull null
                val icon = o.optString("icon")
                if (!isListable(nanokaKey, type, icon)) {
                    dropped++
                    return@mapNotNull null
                }
                val name = NanokaApi.usableName(o.optString("name")) ?: return@mapNotNull null
                NewContentItem(id, name, iconUrl = portraitUrl(nanokaKey, type, icon))
            }
            // 이름이 같은 항목은 하나만. 원신 7.0 이 여행자를 id 두 개(`10000007-5`·`10000135`)로
            // 보내는데, 화면에는 똑같은 "여행자"가 두 줄 서서 고장으로 읽힌다.
            val named = resolved.distinctBy { it.name }
            dropped += resolved.size - named.size
            val total = ids.size - dropped
            if (total <= 0) return@mapNotNull null
            NewContentGroup(type = type, label = label, total = total, items = named)
        }

    /**
     * 목록에 낼 항목인가 — **원신 캐릭터에 캐릭터가 아닌 게 섞여 온다.**
     *
     * 7.0 신규 캐릭터가 5건으로 오는데 실제 신규는 알료샤·오데트 둘뿐이다. 나머지 셋은
     * 여행자 둘(`UI_AvatarIcon_PlayerGirl`, id `10000007-5`·`10000135`)과
     * 별인형(`UI_AvatarIcon_MannequinGirl`) — 앞은 기존 캐릭터의 원소 확장이고 뒤는
     * 전투 훈련용 인형이다. 도감 데이터는 이것들도 '아바타'로 세는데, 사용자가 "이번 버전에
     * 누가 나왔나" 하고 여는 목록의 답은 아니다.
     *
     * 판정은 **아이콘 에셋 이름**으로 한다. id 는 게임마다 체계가 달라 규칙을 세울 수 없지만
     * 에셋 이름은 역할이 그대로 박혀 있다.
     *
     * ⚠️ 이 규칙은 여행자의 **새 원소 형태**도 같이 지운다. 그 자체는 뉴스거리지만 지금 형태로는
     * 알릴 수가 없다 — 이름이 그냥 "여행자"라 목록에서 무엇이 늘었는지 구분되지 않는다.
     * 원소를 이름에 실을 수 있게 되면 그때 다시 볼 것.
     */
    private fun isListable(nanokaKey: String, type: String, icon: String): Boolean {
        if (nanokaKey != "gi" || type != "character") return true
        return !icon.startsWith("UI_AvatarIcon_Player") && !icon.startsWith("UI_AvatarIcon_Mannequin")
    }

    /**
     * 캐릭터 초상 URL.
     *
     * **원신만** 만든다. 도감이 주는 `icon` 은 게임 내부 에셋 이름(`UI_AvatarIcon_Alyosha`)이라
     * 그대로는 못 쓰는데, enka.network 가 그 이름으로 PNG 를 준다 — 앱이 '내 캐릭터'에서
     * 이미 쓰는 호스트다. 다른 게임은 초상 규칙이 달라 확인 전까지 붙이지 않는다(깨진 이미지
     * 자리를 만드느니 글자만 두는 게 낫다).
     */
    private fun portraitUrl(nanokaKey: String, type: String, icon: String): String? {
        if (nanokaKey != "gi" || type != "character") return null
        if (!icon.startsWith("UI_AvatarIcon_")) return null
        return "https://enka.network/ui/$icon.png"
    }

    /**
     * 새 버전 알림 배너 — **지금 버전에 신규 캐릭터가 있으면 상시로.**
     *
     * 처음엔 '안 본 것'만 띄우고 한 번 확인하면 내렸다. 그런데 이 배너가 답하는 질문은
     * "새 소식 있어?"(한 번 읽으면 끝)가 아니라 **"지금 버전에 누가 나왔더라?"** 였다 —
     * 며칠 뒤 다시 보러 오는 정보인데 이미 사라진 뒤라 찾을 데가 없었다. 그래서 '봤음'과
     * 끊고, 버전이 바뀔 때까지 자리를 지킨다. 내리는 버튼도 없앴다(다시 못 부르는 버튼이다).
     *
     * 조건은 그대로 좁게 — 신규 **캐릭터**가 있는 첫 게임만. 무기·유물만 늘어난 버전에는
     * 띄우지 않는다. 지면을 크게 먹는 형식이라 아무 때나 띄우면 곧 배경이 된다.
     */
    fun banner(games: List<NewContentGame>): NewVersionBanner? {
        for (g in games) {
            val chars = g.groups.firstOrNull { it.type == "character" } ?: continue
            if (chars.items.isEmpty()) continue
            return NewVersionBanner(
                gameKey = g.gameKey,
                gameShort = g.gameShort,
                colorArgb = g.colorArgb,
                version = g.version,
                names = chars.items.map { it.name },
                portraits = chars.items.mapNotNull { it.iconUrl }.take(2),
            )
        }
        return null
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
data class NewContentItem(
    val id: String,
    val name: String,
    /** 초상 이미지. 규칙을 아는 게임만 채운다(지금은 원신). */
    val iconUrl: String? = null,
)

/**
 * 새 버전 알림 배너.
 *
 * 픽업 기간은 싣지 않는다 — 도감에는 일정이 없다. "이 버전에 이런 캐릭터가 추가됐다"까지가
 * 사실이고, 그 이상은 지어내는 것이다.
 */
data class NewVersionBanner(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val version: String,
    /** 신규 캐릭터 이름(안 본 것만). */
    val names: List<String>,
    /** 초상 URL — 최대 2장. 없으면 글자만 그린다. */
    val portraits: List<String>,
) {
    /** "원신 7.0 업데이트" */
    val headline: String get() = "$gameShort $version 업데이트"

    /**
     * "알료샤 · 오데트 등장" — 넷 이상이면 "A · B · C 외 2명 등장".
     *
     * '안 본 것만' 이던 시절엔 한둘이었지만 이제 버전 신규 전부라 그대로 이으면 한 줄을 넘겨
     * 뒤가 잘린다. 잘린 이름은 없느니만 못하니 셋에서 끊고 나머지는 수로 말한다.
     */
    val sub: String get() {
        val head = names.take(3).joinToString(" · ")
        val rest = names.size - 3
        return if (rest > 0) "$head 외 ${rest}명 등장" else "$head 등장"
    }
}

/** 게임 하나의 현재 버전 — "원신 7.0". */
data class GameVersionLine(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val version: String,
)
