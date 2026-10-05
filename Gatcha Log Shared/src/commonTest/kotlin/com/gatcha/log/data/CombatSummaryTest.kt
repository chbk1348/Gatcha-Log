package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 전투 진행도 요약 머리 — [combatSummary]. Android · iOS 가 같은 숫자를 보이게 여기서 못 박는다. */
class CombatSummaryTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000_000_000_000L

    private fun mode(stars: Int, max: Int, dDays: Int? = null, hasData: Boolean = true, badge: String = "") =
        CombatMode("원신", "m", stars, max, endMillis = dDays?.let { now + it * day } ?: 0L, hasData = hasData, badge = badge)

    @Test
    fun `만점 · 남은 수 · 가장 급한 마감`() {
        val s = combatSummary(
            listOf(
                mode(31, 36, dDays = 2),
                mode(12, 12, dDays = 1),          // 만점 — 마감이 더 급해도 셈에서 빠진다
                mode(5, 12, dDays = 16),
                mode(30000, 30000, badge = "S+"), // 평가 모드는 점수/만점
                mode(3, 0, dDays = 0),            // 메달형 — 셈 밖
                mode(0, 36, dDays = 0, hasData = false), // 미도전·조회 실패 — 셈 밖
                mode(1, 12, dDays = -1),          // 지난 마감은 급한 마감 후보 아님
            ),
            now,
        )
        assertEquals(2, s.full)
        assertEquals(5, s.total)
        assertEquals(3, s.remaining)
        assertEquals(2, s.urgentDDay)
    }

    @Test
    fun `다 만점이면 급한 마감 없음`() {
        val s = combatSummary(listOf(mode(36, 36, dDays = 2)), now)
        assertEquals(0, s.remaining)
        assertNull(s.urgentDDay)
    }
}
