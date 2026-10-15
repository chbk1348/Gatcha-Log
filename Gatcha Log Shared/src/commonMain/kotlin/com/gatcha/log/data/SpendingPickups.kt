package com.gatcha.log.data

/**
 * 지출의 **픽업 태그**(10/13) — 「누구를 뽑으려고 썼는지」를 남기는 장식 태그.
 *
 * 지출의 태그는 글자 목록 하나다([Spending.tags]). 픽업 태그는 그 안에 **앞 표식([MARK])을 붙여** 함께 들어간다 —
 * 저장 꼴을 바꾸지 않으므로 클라우드 동기화 · 백업이 그대로 돌고, 이 표식을 모르는 옛 버전에서는 「#★푸리나」로 보인다.
 * 화면은 표식이 붙은 태그를 일반 태그(#)와 다른 모양으로 그린다.
 */
object PickupTag {

    /** 픽업 태그의 앞 표식. */
    const val MARK = "★"

    /** 이름 → 저장되는 태그("★푸리나"). */
    fun of(name: String): String = MARK + name.trim()

    /** 픽업 태그인가 — 표식 뒤에 이름이 있어야 한다. */
    fun isPickup(tag: String): Boolean = tag.startsWith(MARK) && tag.removePrefix(MARK).isNotBlank()

    /** 저장된 태그 → 이름("푸리나"). 픽업 태그가 아니면 그대로. */
    fun nameOf(tag: String): String = if (isPickup(tag)) tag.removePrefix(MARK).trim() else tag
}

/**
 * 지출 입력의 「지금 픽업 중」 한 칸.
 *
 * @property iconUrl 초상 · 무기 그림. 없으면 빈 글자(화면이 대체 아이콘을 그린다).
 * @property weapon 무기 · 광추 · W-엔진인가.
 * @property active 지금 픽업 중인가 — false 면 이 지출에 이미 붙어 있던 지난 픽업이다(끌 수 있게 남겨 둔다).
 */
data class PickupOption(
    val name: String,
    val iconUrl: String,
    val weapon: Boolean,
    val active: Boolean = true,
)

object SpendingPickups {

    /**
     * [game] 에서 **지금 픽업 중인** 최고 등급 캐릭터 · 무기. 캐릭터가 앞, 무기가 뒤이고 각각 받은 순서 그대로다.
     *
     * 시작 전(예고) · 끝난 배너는 뺀다. 시각을 모르는 배너(0)는 넣는다 — 캐시가 「지금 열린 배너」만 들고 있다.
     * 배너 데이터가 없는 게임은 빈 목록이다(화면이 섹션을 그리지 않는다).
     */
    fun active(banners: List<GachaBanner>, game: Game, nowMillis: Long): List<PickupOption> =
        banners.asSequence()
            .filter { GameData.byNameOrNull(it.game)?.key == game.key }
            .filter { it.name.isNotBlank() }
            .filter { it.startMillis <= 0L || it.startMillis <= nowMillis }
            .filter { it.endMillis <= 0L || it.endMillis > nowMillis }
            .map { PickupOption(it.name.trim(), it.iconUrl, it.type == "weapon") }
            .distinctBy { it.name }
            .sortedBy { it.weapon }   // 안정 정렬 — 캐릭터 → 무기, 그 안의 순서는 그대로
            .toList()

    /**
     * 화면에 올릴 칸 — 지금 픽업 중인 것 + **이미 골라 둔 이름 중 지금은 픽업이 아닌 것**.
     * 지난 픽업에 쓴 지출을 고칠 때도 그 태그가 보이고, 눌러서 뗄 수 있어야 한다.
     */
    fun options(banners: List<GachaBanner>, game: Game, nowMillis: Long, selected: List<String>): List<PickupOption> {
        val now = active(banners, game, nowMillis)
        val known = now.map { it.name }.toSet()
        val past = selected.map { it.trim() }.filter { it.isNotEmpty() && it !in known }.distinct()
            .map { PickupOption(it, "", weapon = false, active = false) }
        return now + past
    }
}
