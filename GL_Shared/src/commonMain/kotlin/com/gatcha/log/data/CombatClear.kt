package com.gatcha.log.data

/**
 * **엔드 콘텐츠를 어떤 캐릭터로 깼는지** — 층·간별 편성 기록.
 *
 * [CombatMode] 가 "몇 별인가"만 다루는 요약이라면, 이쪽은 그 안을 펼친 상세다.
 * 데이터는 새로 받아오는 게 아니다 — 나선 비경·혼돈의 기억 응답에 **이미 층마다 투입 캐릭터가
 * 들어 있는데** 지금까지 별 개수만 뽑고 버렸다([HoyolabApi.getCombat]). 그걸 살려서 쓴다.
 *
 * HoYoLAB 은 캐릭터 **이름을 주지 않는다**(id·아이콘·레벨뿐). 이름은 보유 캐릭터 캐시에서
 * id 로 찾아 채우고, 없으면 아이콘만 보여준다 — 아이콘만으로도 누군지 알아볼 수 있다.
 */

/** 한 판에 투입된 캐릭터 1명. */
data class CombatAvatar(
    val id: Int,
    /** 보유 캐릭터 캐시에서 찾은 이름. 못 찾으면 빈 문자열(아이콘만 표시). */
    val name: String = "",
    val iconUrl: String = "",
    val level: Int = 0,
    val rarity: Int = 0,
)

/**
 * 층(간) 하나의 클리어 기록.
 *
 * 나선 비경·혼돈의 기억은 한 층을 **전반/후반 두 편성**으로 나눠 싸운다([firstHalf]·[secondHalf]).
 * 환상극처럼 한 편성뿐인 모드는 [firstHalf] 만 채운다.
 */
data class CombatRoom(
    val name: String,
    val stars: Int = 0,
    val maxStars: Int = 0,
    /** 클리어 시각·점수 등 보조 표기. 비어 있을 수 있다. */
    val detail: String = "",
    val firstHalf: List<CombatAvatar> = emptyList(),
    val secondHalf: List<CombatAvatar> = emptyList(),
) {
    /** 편성이 하나도 안 잡힌 층 — 목록에서 뺀다(안 깬 층까지 줄줄이 나오면 화면만 길어진다). */
    val isEmpty: Boolean get() = firstHalf.isEmpty() && secondHalf.isEmpty()
}

/** 모드 하나의 한 시즌치 클리어 상세. */
data class CombatClear(
    val game: String,
    /** 모드 공식 KR 명칭 — [CombatMode.name] 과 같은 값을 쓴다(화면에서 짝지어 보여주려고). */
    val mode: String,
    /** 시즌명. API 가 안 주면 빈 문자열. */
    val season: String = "",
    /** 이번 시즌인가(false = 지난 시즌). */
    val current: Boolean = true,
    val rooms: List<CombatRoom> = emptyList(),
) {
    val gameColor: Long get() = GameData.colorFor(game)

    /** 이 시즌에 한 번이라도 투입된 캐릭터 — 등장 횟수 많은 순. 상단 요약용. */
    val roster: List<CombatAvatar>
        get() = rooms
            .flatMap { it.firstHalf + it.secondHalf }
            .groupBy { it.id }
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int, List<CombatAvatar>>> { it.value.size }.thenBy { it.key })
            .map { it.value.first() }

    /** 캐릭터별 등장 횟수(id → 판 수). '이 시즌 주력'을 보여줄 때 쓴다. */
    val usage: Map<Int, Int>
        get() = rooms.flatMap { it.firstHalf + it.secondHalf }.groupingBy { it.id }.eachCount()
}

/**
 * 모드 하나의 이번/지난 시즌 묶음 — **화면은 모드당 카드 하나**로 그린다.
 *
 * 예전엔 시즌마다 카드를 따로 냈다. 그러면 같은 모드가 두 번 나와 목록이 두 배로 길어지고,
 * 지난 기록이 이번 기록과 같은 무게로 보였다. 지난 시즌은 카드 안에 접어 둔다.
 */
data class CombatModeClears(
    val game: String,
    val mode: String,
    val current: CombatClear?,
    val previous: CombatClear?,
) {
    val gameColor: Long get() = GameData.colorFor(game)
    val gameShort: String get() = GameData.byNameOrNull(game)?.shortName ?: game
    /** 펼칠 지난 기록이 있는가. */
    val hasPrevious: Boolean get() = previous?.rooms?.isNotEmpty() == true
}

object CombatClearLogic {

    /**
     * 캐릭터 이름을 보유 캐릭터 캐시에서 채운다.
     *
     * HoYoLAB 전투 응답에는 이름이 없고 [EnkaChar] 쪽에는 있다 — 두 API 는 **같은 캐릭터 id**를
     * 쓰므로 id 로 이어붙일 수 있다. 캐시에 없는 캐릭터(미보유 표시·최근 미갱신)는 그대로 둔다.
     */
    fun withNames(clears: List<CombatClear>, namesById: Map<Int, String>): List<CombatClear> {
        if (namesById.isEmpty()) return clears
        fun fill(list: List<CombatAvatar>) = list.map { a ->
            if (a.name.isNotBlank()) a else namesById[a.id]?.let { a.copy(name = it) } ?: a
        }
        return clears.map { c ->
            c.copy(rooms = c.rooms.map { r -> r.copy(firstHalf = fill(r.firstHalf), secondHalf = fill(r.secondHalf)) })
        }
    }

    /** 게임+모드로 묶어 화면 순서대로 — 같은 모드의 이번/지난 시즌이 붙어 있게 한다. */
    fun grouped(clears: List<CombatClear>): List<CombatClear> =
        clears.filter { it.rooms.any { r -> !r.isEmpty } }
            .sortedWith(compareBy({ it.game }, { it.mode }, { !it.current }))

    /**
     * 모드당 하나로 묶는다 — 카드가 시즌 수만큼 늘어나지 않게.
     *
     * 게임 순서는 [GameData] 정의 순(원신 → 스타레일)을 따른다. 알파벳/가나다 정렬을 쓰면
     * 게임 정보 탭의 다른 목록과 순서가 어긋나 같은 화면인데 배치가 달라 보인다.
     */
    fun byMode(clears: List<CombatClear>): List<CombatModeClears> {
        val usable = clears.filter { it.rooms.any { r -> !r.isEmpty } }
        val gameOrder = GameData.games.map { it.displayName }
        return usable
            .groupBy { it.game to it.mode }
            .map { (key, list) ->
                CombatModeClears(
                    game = key.first,
                    mode = key.second,
                    current = list.firstOrNull { it.current },
                    previous = list.firstOrNull { !it.current },
                )
            }
            .filter { it.current != null || it.previous != null }
            .sortedWith(
                compareBy(
                    { gameOrder.indexOf(it.game).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
                    { it.mode },
                ),
            )
    }

    /**
     * 층 표기 — **API 가 준 이름을 그대로 쓴다.**
     *
     * 한때 시즌명이 앞에 겹쳐 나오는 게 지저분해 떼어냈는데("학원괴담•12스타라이즈 모드" →
     * "12스타라이즈 모드"), 층마다 남는 꼬리가 달라 어떤 줄은 "11" 한 글자만 남았다.
     * 인게임 표기를 우리가 재구성하면 이런 식으로 어긋난다 — 원문을 건드리지 않는다.
     *
     * (원신 나선 비경은 이름이 없어 "12-3" 으로 만드는데, 그것도 HoYoLAB 자신이 `max_floor` 에
     *  쓰는 형식을 그대로 따른 것이다 — [HoyolabApi.abyssClear] 주석 참고.)
     */
    fun roomLabel(name: String, season: String): String = name
}
