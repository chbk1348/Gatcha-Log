package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * 연간 리포트 집계 — [AnnualReportLogic] (27.52.0).
 *
 * Compose 와 SwiftUI 가 따로 갖고 있던 계산을 하나로 모으며 **그때의 동작을 여기 고정한다**.
 * 틀리면 오류가 아니라 "화면 숫자가 조용히 다른" 꼴로만 드러나는 값들이라, 경계(0건 · 한 달 · 연말연시 · 동률)를 적어 둔다.
 */
@OptIn(ExperimentalTime::class)
class AnnualReportLogicTest {

    /** 로컬 타임존 정오 기준 epoch millis — 연 · 월 판정이 로컬 tz 라 경계에서 안전한 시각. */
    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDateTime(year, month, day, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    private fun spend(game: String = "원신", amount: Long, year: Int = 2026, month: Int, day: Int = 10) =
        Spending(gameName = game, amount = amount, dateMillis = at(year, month, day))

    /** 오늘 = 2026년 7월. */
    private fun report(spendings: List<Spending>, selectedYear: Int = 0, displayYear: Int = 2026, displayMonth: Int = 7) =
        AnnualReportLogic.report(spendings, selectedYear, displayYear, displayMonth)

    // ── 연도 목록 · 집계할 해 ────────────────────────────────────────────────

    @Test
    fun 지출이_0건이면_올해_한_해만_있고_전부_0이다() {
        val r = report(emptyList())
        assertEquals(listOf(2026), r.years)
        assertEquals(2026, r.year)
        assertEquals(0, r.count)
        assertEquals(0L, r.total)
        assertEquals(0L, r.monthlyAverage)
        assertTrue(r.byGame.isEmpty())
        // 기록이 없어도 12칸은 그대로다 — 화면이 칸 수를 믿고 그린다.
        assertEquals((1..12).toList(), r.months.map { it.month })
        assertTrue(r.months.all { it.amount == 0L && it.barRatio == 0.0 })
    }

    @Test
    fun 연도_목록은_기록이_있는_해에_올해를_더해_최근_해부터다() {
        val items = listOf(
            spend(amount = 1_000, year = 2024, month = 3),
            spend(amount = 1_000, year = 2025, month = 3),
            spend(amount = 1_000, year = 2024, month = 9),   // 같은 해는 한 번만
        )
        // 올해(2026) 기록은 없지만 목록에는 들어간다.
        assertEquals(listOf(2026, 2025, 2024), AnnualReportLogic.years(items, 2026))
        assertEquals(listOf(2026, 2025, 2024), report(items).years)
    }

    @Test
    fun 아직_고르지_않았으면_가장_최근_해를_집계한다() {
        val r = report(listOf(spend(amount = 5_000, year = 2025, month = 3)), selectedYear = 0)
        // 가장 최근 해는 기록이 없는 올해다 — 0건으로 나온다.
        assertEquals(2026, r.year)
        assertEquals(0, r.count)
    }

    @Test
    fun 고른_해의_기록이_다_사라지면_가장_최근_해로_돌아간다() {
        // 2023년을 보고 있었는데 그 해 기록을 전부 지웠다 — 칩에 없는 해를 0원으로 들고 있지 않는다.
        val r = report(listOf(spend(amount = 5_000, year = 2025, month = 3)), selectedYear = 2023)
        assertEquals(listOf(2026, 2025), r.years)
        assertEquals(2026, r.year)
    }

    @Test
    fun 섣달그믐과_새해_첫날은_다른_해로_갈린다() {
        val items = listOf(
            spend(amount = 30_000, year = 2025, month = 12, day = 31),
            spend(amount = 7_000, year = 2026, month = 1, day = 1),
        )
        val last = report(items, selectedYear = 2025)
        assertEquals(30_000L, last.total)
        assertEquals(1, last.count)
        assertEquals(30_000L, last.months[11].amount)

        val now = report(items, selectedYear = 2026)
        assertEquals(7_000L, now.total)
        assertEquals(1, now.count)
        assertEquals(7_000L, now.months[0].amount)
        assertEquals(0L, now.months[11].amount, "작년 12월이 올해 12월 칸에 들어가면 안 된다")
    }

    // ── 월별 합계 · 막대 ─────────────────────────────────────────────────────

    @Test
    fun 월별_합계는_그_달_칸에_쌓인다() {
        val r = report(
            listOf(
                spend(amount = 10_000, month = 1),
                spend(amount = 5_000, month = 1, day = 28),
                spend(amount = 3_000, month = 7),
            ),
            selectedYear = 2026,
        )
        assertEquals(15_000L, r.months[0].amount)
        assertEquals(3_000L, r.months[6].amount)
        assertEquals(18_000L, r.months.sumOf { it.amount })
        assertEquals(r.total, r.months.sumOf { it.amount })
    }

    @Test
    fun 막대는_가장_큰_달이_1이고_아주_작은_달도_최소_높이는_나온다() {
        val r = report(
            listOf(
                spend(amount = 1_000_000, month = 2),
                spend(amount = 500_000, month = 3),
                spend(amount = 1_000, month = 4),     // 가장 큰 달의 0.1% — 그대로면 안 보인다
            ),
            selectedYear = 2026,
        )
        assertEquals(1.0, r.months[1].barRatio, 1e-9)
        assertEquals(0.5, r.months[2].barRatio, 1e-9)
        assertEquals(AnnualReportLogic.MIN_BAR_RATIO, r.months[3].barRatio, 1e-9)
        // 안 쓴 달은 최소 높이도 주지 않는다 — 막대가 없어야 한다.
        assertEquals(0.0, r.months[0].barRatio, 0.0)
    }

    // ── 월 평균 ─────────────────────────────────────────────────────────────

    @Test
    fun 올해의_월_평균은_지나온_달_수로_나눈다() {
        // 4월에 본 올해 — 1월에만 12만 원. 기록이 있는 달(1)이 아니라 지나온 달(4)로 나눈다.
        val r = report(listOf(spend(amount = 120_000, month = 1)), selectedYear = 2026, displayMonth = 4)
        assertEquals(30_000L, r.monthlyAverage)
    }

    @Test
    fun 지난_해의_월_평균은_기록이_있는_달_수로_나눈다() {
        val r = report(
            listOf(
                spend(amount = 10_000, year = 2025, month = 3),
                spend(amount = 20_000, year = 2025, month = 3, day = 20),   // 같은 달
                spend(amount = 30_000, year = 2025, month = 7),
            ),
            selectedYear = 2025,
        )
        // 6만 원 ÷ 2달(3월 · 7월). 12 로도, 올해의 달 수(7)로도 나누지 않는다.
        assertEquals(30_000L, r.monthlyAverage)
    }

    @Test
    fun 한_달만_기록이_있는_해는_평균이_합계와_같다() {
        val r = report(listOf(spend(amount = 45_000, year = 2025, month = 11)), selectedYear = 2025)
        assertEquals(45_000L, r.total)
        assertEquals(45_000L, r.monthlyAverage)
        assertEquals(1.0, r.months[10].barRatio, 1e-9)
        assertEquals(1, r.months.count { it.barRatio > 0.0 })
    }

    // ── 게임별 ──────────────────────────────────────────────────────────────

    @Test
    fun 게임은_금액이_큰_순서고_같으면_먼저_기록된_게임이_위다() {
        val r = report(
            listOf(
                spend(game = "젠레스 존 제로", amount = 5_000, month = 2),
                spend(game = "원신", amount = 5_000, month = 3),
                spend(game = "붕괴: 스타레일", amount = 9_000, month = 4),
            ),
            selectedYear = 2026,
        )
        // iOS 는 예전에 Dictionary 를 돌려 정렬해 동률의 순서가 실행마다 달랐다.
        assertEquals(listOf("붕괴: 스타레일", "젠레스 존 제로", "원신"), r.byGame.map { it.game })
    }

    @Test
    fun 게임_비율은_그_해_합계_대비고_다_더하면_1이다() {
        val r = report(
            listOf(
                spend(game = "원신", amount = 60_000, month = 1),
                spend(game = "원신", amount = 15_000, month = 5),
                spend(game = "붕괴: 스타레일", amount = 25_000, month = 2),
                spend(game = "원신", amount = 999_999, year = 2025, month = 1),   // 다른 해 → 분모에 안 들어간다
            ),
            selectedYear = 2026,
        )
        assertEquals(listOf(75_000L, 25_000L), r.byGame.map { it.amount })
        assertEquals(0.75, r.byGame[0].ratio, 1e-9)
        assertEquals(0.25, r.byGame[1].ratio, 1e-9)
        assertEquals(1.0, r.byGame.sumOf { it.ratio }, 1e-9)
    }
}
