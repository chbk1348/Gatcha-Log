package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * 지출 인사이트 공유 집계 — 양 플랫폼이 같은 수치를 보여야 하므로 경계 규칙을 고정한다.
 * (원래 Android/iOS 가 각각 구현하던 계산으로, 재구현 시 어긋나기 쉬웠던 지점 위주)
 */
@OptIn(ExperimentalTime::class)
class SpendingInsightStatsTest {

    /** 로컬 타임존 정오 기준 epoch millis — 집계가 로컬 tz 로 월/연을 판정하므로 경계에서 안전한 시각. */
    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDateTime(year, month, day, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    private fun spend(game: String, amount: Long, month: Int, day: Int = 10, tags: List<String> = emptyList(), method: String = "카드") =
        Spending(gameName = game, amount = amount, dateMillis = at(2026, month, day), paymentMethod = method, tags = tags)

    // ── 예산 페이스 ──────────────────────────────────────────────────────────

    @Test
    fun budgetPaceClampsDenominatorDuringWarmup() {
        // 3일 경과 · 10만원 → 순수 run-rate 라면 100만(31/3배)이지만, 워밍업 하한(7일)으로 31/7배까지만.
        val pace = SpendingInsightStats.budgetPace(monthTotal = 100_000, dayOfMonth = 3, daysInMonth = 31)
        assertEquals(100_000L * 31 / 7, pace.projected)
        assertEquals(100_000L / 3, pace.dailyAvg)   // 하루 평균은 실제 경과일 기준(하한 적용 안 함)
        assertEquals(28, pace.remainingDays)
    }

    @Test
    fun budgetPaceUsesPlainRunRateAfterWarmup() {
        // 7일 경과부터는 하한이 무의미 → 순수 run-rate 와 동일해야 한다.
        val pace = SpendingInsightStats.budgetPace(monthTotal = 210_000, dayOfMonth = 7, daysInMonth = 30)
        assertEquals(210_000L * 30 / 7, pace.projected)
        assertEquals(30_000L, pace.dailyAvg)
        assertEquals(23, pace.remainingDays)
    }

    @Test
    fun budgetPaceNeverProjectsBelowCurrentSpend() {
        // 배율 = daysInMonth / max(dayOfMonth, 7) ≥ 1 이므로 예상은 항상 현재 지출 이상.
        for (day in 1..31) {
            val pace = SpendingInsightStats.budgetPace(monthTotal = 50_000, dayOfMonth = day, daysInMonth = 31)
            assertTrue(pace.projected >= 50_000L, "day=$day projected=${pace.projected}")
        }
    }

    @Test
    fun budgetPaceHandlesZeroDay() {
        val pace = SpendingInsightStats.budgetPace(monthTotal = 7_000, dayOfMonth = 0, daysInMonth = 31)
        assertEquals(7_000L, pace.projected)
        assertEquals(0L, pace.dailyAvg)
        assertEquals(31, pace.remainingDays)
    }

    // ── 월 추이 ─────────────────────────────────────────────────────────────

    @Test
    fun monthlyTrendReturnsNullWithoutRecordsInYear() {
        assertNull(SpendingInsightStats.monthlyTrend(listOf(spend("원신", 1000, 3)), 2025))
    }

    @Test
    fun monthlyTrendGroupsBeyondTopFiveIntoEtc() {
        // 6개 게임 → 상위 5 + "기타". 가장 적게 쓴 F 가 기타로 밀린다.
        val items = listOf(
            spend("A", 6000, 1), spend("B", 5000, 1), spend("C", 4000, 1),
            spend("D", 3000, 1), spend("E", 2000, 1), spend("F", 1000, 1),
        )
        val trend = SpendingInsightStats.monthlyTrend(items, 2026)!!
        assertEquals(listOf("A", "B", "C", "D", "E"), trend.topGames)
        assertEquals(listOf("A", "B", "C", "D", "E", "기타"), trend.legend)
        assertEquals(1000L, trend.monthGame[0]["기타"])
        assertEquals(21_000L, trend.maxMonth)   // 1월 합계 = 막대 분모
    }

    @Test
    fun monthlyTrendOmitsEtcWhenFiveOrFewerGames() {
        val trend = SpendingInsightStats.monthlyTrend(listOf(spend("A", 100, 2), spend("B", 200, 5)), 2026)!!
        assertEquals(listOf("B", "A"), trend.legend)   // 기타 없음, 금액 내림차순
        assertEquals(12, trend.monthGame.size)
        assertEquals(200L, trend.monthGame[4]["B"])    // 5월 = index 4
        assertEquals(200L, trend.maxMonth)
    }

    @Test
    fun monthlyTrendMaxMonthIsAtLeastOne() {
        // 0원 기록만 있어도 막대 분모로 나눗셈을 하므로 0 이 되면 안 된다.
        val trend = SpendingInsightStats.monthlyTrend(listOf(spend("A", 0, 6)), 2026)!!
        assertEquals(1L, trend.maxMonth)
    }

    // ── 결제수단 · 태그 ──────────────────────────────────────────────────────

    @Test
    fun paymentBreakdownGroupsBlankAsEtcAndSortsDesc() {
        val items = listOf(
            spend("A", 1000, 1, method = "카드"),
            spend("A", 3000, 1, method = ""),
            spend("A", 2000, 1, method = "카드"),
        )
        val rows = SpendingInsightStats.paymentBreakdown(items)
        assertEquals(listOf("카드", "기타"), rows.map { it.name })
        assertEquals(listOf(3000L, 3000L), rows.map { it.amount })
        assertTrue(rows.all { it.total == 6000L })   // 결제수단 total = 전체합
    }

    @Test
    fun tagBreakdownCountsDuplicatesAndUsesMaxAsDenominator() {
        // 태그는 한 지출에 여러 개 → 중복 집계. 합계 비율이 100%를 넘을 수 있어 분모는 최대 태그 금액.
        val items = listOf(
            spend("A", 1000, 1, tags = listOf("픽업", "천장")),
            spend("A", 500, 1, tags = listOf("픽업")),
        )
        val rows = SpendingInsightStats.tagBreakdown(items)
        assertEquals(listOf("픽업", "천장"), rows.map { it.name })
        assertEquals(listOf(1500L, 1000L), rows.map { it.amount })
        assertTrue(rows.all { it.total == 1500L })   // 태그 total = 최대 태그 금액(막대 분모)
        assertEquals("픽업", rows.first().name)      // 이름에 "#" 는 붙이지 않는다(표시 단계에서 부착)
    }

    @Test
    fun tagBreakdownKeepsTopEightOnly() {
        val items = (1..10).map { spend("A", it * 100L, 1, tags = listOf("t$it")) }
        val rows = SpendingInsightStats.tagBreakdown(items)
        assertEquals(8, rows.size)
        assertEquals("t10", rows.first().name)
        assertEquals("t3", rows.last().name)
    }
}
