package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 지출의 픽업 태그 — 「지금 픽업 중」 목록과 태그 저장 꼴. */
class SpendingPickupsTest {

    private val now = 1_000_000L

    private fun banner(game: String, name: String, type: String = "character", start: Long = 0, end: Long = 0, icon: String = "") =
        GachaBanner(game = game, name = name, type = type, startMillis = start, endMillis = end, iconUrl = icon)

    @Test
    fun 그_게임에서_지금_열린_것만_캐릭터가_앞() {
        val banners = listOf(
            banner("원신", "예란의 활", type = "weapon"),
            banner("원신", "푸리나", end = now + 10, icon = "u1"),
            banner("붕괴: 스타레일", "아케론"),
            banner("원신", "예고 캐릭터", start = now + 5),      // 아직 시작 전
            banner("원신", "끝난 캐릭터", end = now - 1),        // 끝남
            banner("원신", "푸리나"),                            // 겹침
            banner("원신", ""),                                 // 이름 없음
        )
        val got = SpendingPickups.active(banners, Game.GENSHIN, now)
        assertEquals(listOf("푸리나", "예란의 활"), got.map { it.name })
        assertEquals(listOf(false, true), got.map { it.weapon })
        assertEquals("u1", got[0].iconUrl)
        assertTrue(got.all { it.active })
        // 배너가 없는 게임은 빈 목록.
        assertTrue(SpendingPickups.active(banners, Game.WUWA, now).isEmpty())
    }

    @Test
    fun 이미_붙어_있던_지난_픽업은_뒤에_남긴다() {
        val banners = listOf(banner("원신", "푸리나"))
        val got = SpendingPickups.options(banners, Game.GENSHIN, now, selected = listOf("푸리나", "나히다", " ", "나히다"))
        assertEquals(listOf("푸리나", "나히다"), got.map { it.name })
        assertEquals(listOf(true, false), got.map { it.active })
    }

    @Test
    fun 픽업_태그는_표식을_붙여_저장하고_되읽는다() {
        assertEquals("★푸리나", PickupTag.of(" 푸리나 "))
        assertTrue(PickupTag.isPickup("★푸리나"))
        assertFalse(PickupTag.isPickup("푸리나"))
        assertFalse(PickupTag.isPickup("★"))
        assertEquals("푸리나", PickupTag.nameOf("★푸리나"))
        assertEquals("천장", PickupTag.nameOf("천장"))
    }

    @Test
    fun 태그를_가를_때_픽업은_따로_합칠_때_맨_뒤() {
        val saved = listOf("천장", "★푸리나", "한정", "★예란의 활")
        val split = SpendingEditLogic.splitTags(saved)
        assertEquals(listOf("천장"), split.selected)
        assertEquals("한정", split.custom)
        assertEquals(listOf("푸리나", "예란의 활"), split.pickups)

        val merged = SpendingEditLogic.mergeTags(split.selected, split.custom, split.pickups)
        assertEquals(listOf("천장", "한정", "★푸리나", "★예란의 활"), merged)
        // 고른 픽업이 없으면 예전과 같다.
        assertEquals(listOf("천장", "한정"), SpendingEditLogic.mergeTags(split.selected, split.custom, emptyList()))
    }
}
