package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 절약 챌린지 — 테스트가 하나도 없던 곳이다. 먼저 지금 동작(스트릭 · 배지 판정)을 고정하고,
 * 27.52.0 에서 바꾼 것(예산 배지는 예산을 정한 달부터)을 그 위에 얹는다.
 */
class SavingsChallengeTest {

    private val day = 86_400_000L
    /** 2026-10-12 12:00 (기기 로컬) — 날짜 계산이 시간대에 덜 흔들리게 한낮으로 둔다. */
    private val now = DateUtil.startOfDay(1_791_800_000_000L) + 12 * 3_600_000L

    private fun sp(daysAgo: Int, amount: Long, game: String = "원신") =
        Spending(id = "s$daysAgo-$amount", gameName = game, amount = amount, dateMillis = now - daysAgo * day)

    private fun earned(s: ChallengeSummary) = SavingsChallenge.earnedIds(s)

    private fun eval(list: List<Spending>, budget: Long = 0, since: Long = 0, stored: Set<String> = emptySet(), best: Int = 0) =
        SavingsChallenge.evaluate(list, budget, best, stored, nowMillis = now, budgetSinceMillis = since)

    @Test
    fun 기록이_없으면_스트릭은_0이다() {
        val s = eval(emptyList())
        assertEquals(0, s.noSpendStreak)
        assertTrue(earned(s).isEmpty())
    }

    @Test
    fun 무지출_스트릭은_오늘부터_거꾸로_센다() {
        // 3일 전에 마지막으로 썼다 → 오늘 · 어제 · 그제 = 3일.
        val s = eval(listOf(sp(3, 1_000), sp(10, 1_000)))
        assertEquals(3, s.noSpendStreak)
        assertEquals(3, s.bestStreak)
        // 오늘 썼으면 0.
        assertEquals(0, eval(listOf(sp(0, 1_000))).noSpendStreak)
        // 저장된 최고 기록은 줄지 않는다.
        assertEquals(40, eval(listOf(sp(3, 1_000)), best = 40).bestStreak)
    }

    @Test
    fun 얻은_배지는_사라지지_않는다() {
        val stored = setOf(SavingsChallenge.B_NOSPEND_7)
        assertTrue(SavingsChallenge.B_NOSPEND_7 in earned(eval(listOf(sp(0, 1_000)), stored = stored)))
    }

    @Test
    fun 예산을_정한_시각을_모르면_지난_달_전부를_센다() {
        // 넉 달 전부터 매달 10,000원씩 — 예산 50,000원이면 지난 세 달이 모두 예산 안이다.
        val list = listOf(sp(125, 10_000), sp(95, 10_000), sp(65, 10_000), sp(35, 10_000))
        val s = eval(list, budget = 50_000)
        assertTrue(SavingsChallenge.B_BUDGET in earned(s))
        assertTrue(SavingsChallenge.B_BUDGET_3MO in earned(s))
    }

    @Test
    fun 오늘_처음_정한_예산으로_지난_달_배지를_주지_않는다() {
        val list = listOf(sp(125, 10_000), sp(95, 10_000), sp(65, 10_000), sp(35, 10_000))
        val s = eval(list, budget = 50_000, since = now)
        assertTrue(SavingsChallenge.B_BUDGET !in earned(s), "예산을 정하기 전의 달로 배지를 줬다")
        assertTrue(SavingsChallenge.B_BUDGET_3MO !in earned(s), "예산을 정하기 전의 달로 3개월 배지를 줬다")
    }

    @Test
    fun 예산을_정한_달부터는_센다() {
        val list = listOf(sp(125, 10_000), sp(95, 10_000), sp(65, 10_000), sp(35, 10_000))
        // 석 달쯤 전에 정했다 — 그 달과 그 뒤의 지난 달들이 대상이다.
        val s = eval(list, budget = 50_000, since = now - 100 * day)
        assertTrue(SavingsChallenge.B_BUDGET in earned(s))
    }

    @Test
    fun 이번_달_예산_챌린지는_정한_시각과_무관하게_선다() {
        val s = eval(listOf(sp(1, 40_000)), budget = 50_000, since = now)
        val c = s.challenges.first { it.id == "month_budget" }
        assertEquals(40_000, c.current)
        assertTrue(c.reached)
        assertTrue(c.warn, "80% 를 넘으면 경고다")
    }
}
