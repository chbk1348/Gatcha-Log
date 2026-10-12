package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * 마이페이지 통계 — [MyPageStats] 와, 같이 쓰는 [SpendingInsightStats.momPercent] (27.52.0).
 *
 * Compose 와 SwiftUI 가 따로 갖고 있던 계산을 하나로 모으며 그때의 동작을 여기 고정한다.
 * 두 화면이 실제로 갈렸던 곳(지난달 대비 % · 동률 게임의 순서)은 그 값을 그대로 적어 둔다.
 */
@OptIn(ExperimentalTime::class)
class MyPageStatsTest {

    /** 로컬 타임존 정오 기준 epoch millis — 월 판정이 로컬 tz 라 경계에서 안전한 시각. */
    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDateTime(year, month, day, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    private fun spend(game: String = "원신", amount: Long, year: Int = 2026, month: Int = 7, day: Int = 10) =
        Spending(gameName = game, amount = amount, dateMillis = at(year, month, day))

    private fun task(key: String, rate: Int, days: Int) = TaskStats(
        gameKey = key, gameShort = key, colorArgb = 0xFF9AA0A6, todayDone = false, weekDone = false,
        dailyRate = rate, dailyDays = days, dailyStreak = 0, dailyBest = 0, weeklyRate = 0, weeklyWeeks = 0,
    )

    // ── 누적 지출 · 이번 달 기록 수 ──────────────────────────────────────────

    @Test
    fun 지출이_0건이면_누적도_기록_수도_0이다() {
        assertEquals(MyPageTotals(0L, 0), MyPageStats.totals(emptyList(), at(2026, 7, 15)))
    }

    @Test
    fun 누적은_전체_기간이고_기록_수는_이번_달만_센다() {
        val t = MyPageStats.totals(
            listOf(
                spend(amount = 10_000, month = 7, day = 1),
                spend(amount = 5_000, month = 7, day = 31),
                spend(amount = 3_000, month = 6, day = 30),
                spend(amount = 2_000, year = 2024, month = 2),
            ),
            at(2026, 7, 15),
        )
        assertEquals(20_000L, t.total)
        assertEquals(2, t.monthCount)
    }

    @Test
    fun 작년_같은_달과_지난달_말일은_이번_달_기록이_아니다() {
        val t = MyPageStats.totals(
            listOf(
                spend(amount = 1_000, year = 2026, month = 1, day = 1),
                spend(amount = 1_000, year = 2025, month = 12, day = 31),   // 하루 전이지만 지난달이고 작년이다
                spend(amount = 1_000, year = 2025, month = 1, day = 10),    // 달 숫자만 같다
            ),
            at(2026, 1, 15),
        )
        assertEquals(3_000L, t.total)
        assertEquals(1, t.monthCount)
    }

    // ── 일 평균 ─────────────────────────────────────────────────────────────

    @Test
    fun 일_평균은_오늘까지_지난_날_수로_나눈다() {
        // 10일에 본 10만 원 → 1만 원. 그 달의 31일로 나누지 않는다.
        assertEquals(10_000L, MyPageStats.dailyAverage(100_000, at(2026, 7, 10)))
        assertEquals(100_000L / 31, MyPageStats.dailyAverage(100_000, at(2026, 7, 31)))
    }

    @Test
    fun 초하루의_일_평균은_그날_쓴_금액_그대로다() {
        assertEquals(33_000L, MyPageStats.dailyAverage(33_000, at(2026, 7, 1)))
        assertEquals(0L, MyPageStats.dailyAverage(0, at(2026, 7, 1)))
    }

    // ── 지난달보다 ──────────────────────────────────────────────────────────

    @Test
    fun 지난달_대비는_정수로_나눠_0_쪽으로_버린다() {
        // Double 로 나누면 0.29 × 100 = 28.999… 라 28 이 됐고(iOS), Float 로는 29 였다(Android).
        assertEquals(29, SpendingInsightStats.momPercent(129, 100))
        assertEquals(-29, SpendingInsightStats.momPercent(71, 100))
        // Float 로는 58 이 나오던 값 — 정확히는 59.
        assertEquals(59, SpendingInsightStats.momPercent(159, 100))
        // 0.5% 는 0, -0.5% 도 0.
        assertEquals(0, SpendingInsightStats.momPercent(1_005, 1_000))
        assertEquals(0, SpendingInsightStats.momPercent(995, 1_000))
        assertEquals(0, SpendingInsightStats.momPercent(50_000, 50_000))
        assertEquals(-100, SpendingInsightStats.momPercent(0, 50_000))
    }

    @Test
    fun 지난달이_0이면_비교하지_않는다() {
        assertEquals(-1, SpendingInsightStats.momPercent(10_000, 0))
        assertEquals(-1, SpendingInsightStats.momPercent(0, 0))
    }

    // ── 예산 ────────────────────────────────────────────────────────────────

    @Test
    fun 예산을_정하지_않았으면_전부_0이다() {
        assertEquals(MyPageBudget(over = false, percent = 0, ratio = 0.0, gap = 0L), MyPageStats.budget(50_000, 0))
    }

    @Test
    fun 예산_안이면_남은_금액을_낸다() {
        val b = MyPageStats.budget(monthTotal = 30_000, budget = 120_000)
        assertFalse(b.over)
        assertEquals(25, b.percent)
        assertEquals(0.25, b.ratio, 1e-9)
        assertEquals(90_000L, b.gap)
    }

    @Test
    fun 딱_예산만큼_썼으면_넘긴_것이_아니다() {
        val b = MyPageStats.budget(monthTotal = 100_000, budget = 100_000)
        assertFalse(b.over)
        assertEquals(100, b.percent)
        assertEquals(1.0, b.ratio, 1e-9)
        assertEquals(0L, b.gap)
    }

    @Test
    fun 예산을_넘기면_막대는_가득이고_넘긴_금액을_낸다() {
        val b = MyPageStats.budget(monthTotal = 150_001, budget = 100_000)
        assertTrue(b.over)
        assertEquals(150, b.percent)
        assertEquals(1.0, b.ratio, 0.0)
        assertEquals(50_001L, b.gap)
    }

    @Test
    fun 예산_퍼센트는_올리지_않는다() {
        // 99.9% 를 100% 로 올리면 아직 남았는데 다 쓴 것처럼 읽힌다.
        assertEquals(99, MyPageStats.budget(99_999, 100_000).percent)
    }

    // ── 최근 N개월 추이 ──────────────────────────────────────────────────────

    @Test
    fun 추이가_비면_평균은_0이고_가장_많이_쓴_달은_없다() {
        val t = MyPageStats.trend(emptyList(), at(2026, 7, 15))
        assertTrue(t.points.isEmpty())
        assertEquals(0L, t.monthlyAverage)
        assertNull(t.peak)
    }

    @Test
    fun 달_이름은_이번_달에서_거슬러_붙이고_1월_앞은_전년_12월이다() {
        // 2026-02 기준 6개월 = 2025-09 … 2026-02 (오래된 달이 0번)
        val t = MyPageStats.trend(listOf(1L, 2L, 3L, 4L, 5L, 6L), at(2026, 2, 15))
        assertEquals(listOf(9, 10, 11, 12, 1, 2), t.points.map { it.month })
        assertEquals(listOf(2025, 2025, 2025, 2025, 2026, 2026), t.points.map { it.year })
        assertEquals(listOf(202509, 202510, 202511, 202512, 202601, 202602), t.points.map { it.yearMonth })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), t.points.map { it.amount })
    }

    @Test
    fun 월_평균은_기록이_없는_달도_세어_나눈다() {
        // 6개월 중 두 달만 썼다 — 9만 원 ÷ 6.
        val t = MyPageStats.trend(listOf(0L, 0L, 0L, 0L, 30_000L, 60_000L), at(2026, 7, 15))
        assertEquals(15_000L, t.monthlyAverage)
    }

    @Test
    fun 한_달만_기록이_있어도_그_달이_가장_많이_쓴_달이다() {
        val t = MyPageStats.trend(listOf(0L, 0L, 40_000L, 0L, 0L, 0L), at(2026, 7, 15))
        assertEquals(4, t.peak?.month)          // 2 · 3 · 4 · 5 · 6 · 7월 중 세 번째
        assertEquals(40_000L, t.peak?.amount)
        assertEquals(listOf(0.0, 0.0, 1.0, 0.0, 0.0, 0.0), t.points.map { it.barRatio })
    }

    @Test
    fun 가장_많이_쓴_달이_둘이면_더_오래된_달이다() {
        val t = MyPageStats.trend(listOf(0L, 50_000L, 10_000L, 50_000L, 0L, 0L), at(2026, 7, 15))
        assertEquals(3, t.peak?.month)
        assertEquals(0.2, t.points[2].barRatio, 1e-9)
    }

    @Test
    fun 여섯_달이_전부_0이면_가장_많이_쓴_달은_없고_막대도_0이다() {
        val t = MyPageStats.trend(List(6) { 0L }, at(2026, 7, 15))
        assertNull(t.peak)
        assertEquals(0L, t.monthlyAverage)
        assertTrue(t.points.all { it.barRatio == 0.0 })
        assertEquals(6, t.points.size)
    }

    // ── 게임별 지출 ──────────────────────────────────────────────────────────

    @Test
    fun 지출이_0건이면_조각이_없다() {
        val s = MyPageStats.gameShare(emptyList())
        assertTrue(s.slices.isEmpty())
        assertEquals(0L, s.total)
    }

    @Test
    fun 게임이_다섯_이하면_기타로_묶지_않는다() {
        val s = MyPageStats.gameShare((1..5).map { spend(game = "게임$it", amount = it * 1_000L) })
        assertEquals(listOf("게임5", "게임4", "게임3", "게임2", "게임1"), s.slices.map { it.game })
        assertTrue(s.slices.none { it.isEtc })
        assertEquals(15_000L, s.total)
    }

    @Test
    fun 게임이_여섯_이상이면_상위_다섯과_기타_하나다() {
        val s = MyPageStats.gameShare((1..8).map { spend(game = "게임$it", amount = it * 1_000L) })
        assertEquals(listOf("게임8", "게임7", "게임6", "게임5", "게임4", MyPageStats.ETC_LABEL), s.slices.map { it.game })
        // 기타 = 게임1 + 게임2 + 게임3. 상위보다 커지더라도 자리는 맨 끝이다.
        assertEquals(6_000L, s.slices.last().amount)
        assertTrue(s.slices.last().isEtc)
        assertEquals(0L, s.slices.last().color)
        assertEquals(1, s.slices.count { it.isEtc })
        assertEquals(36_000L, s.total)
        assertEquals(s.total, s.slices.sumOf { it.amount })
    }

    @Test
    fun 비중은_다_더하면_정확히_100이다() {
        // 셋이 똑같이 3분의 1 — 따로 내림하면 33 + 33 + 33 = 99 다.
        val even = MyPageStats.gameShare(listOf("가", "나", "다").map { spend(game = it, amount = 1_000) })
        assertEquals(listOf(34, 33, 33), even.slices.map { it.percent })

        val many = MyPageStats.gameShare((1..9).map { spend(game = "게임$it", amount = it * 1_117L) })
        assertEquals(100, many.slices.sumOf { it.percent })

        val one = MyPageStats.gameShare(listOf(spend(amount = 1)))
        assertEquals(listOf(100), one.slices.map { it.percent })
    }

    @Test
    fun 금액이_같은_게임은_먼저_기록된_쪽이_위다() {
        val s = MyPageStats.gameShare(
            listOf(
                spend(game = "나중", amount = 9_000),
                spend(game = "둘째", amount = 5_000),
                spend(game = "셋째", amount = 2_000),
                spend(game = "셋째", amount = 3_000),   // 합쳐서 5,000 — 둘째와 동률, 처음 나온 순서는 둘째가 앞
            ),
        )
        // iOS 는 예전에 Dictionary 를 돌려 정렬해 동률의 순서가 실행마다 달랐다.
        assertEquals(listOf("나중", "둘째", "셋째"), s.slices.map { it.game })
    }

    @Test
    fun 조각_색은_그_게임의_첫_기록에_저장된_색이다() {
        val s = MyPageStats.gameShare(
            listOf(
                Spending(gameName = "원신", amount = 1_000, dateMillis = at(2026, 7, 1), gameColor = 0xFF111111),
                Spending(gameName = "원신", amount = 1_000, dateMillis = at(2026, 7, 2), gameColor = 0xFF222222),
            ),
        )
        assertEquals(0xFF111111, s.slices.single().color)
        assertEquals(2_000L, s.slices.single().amount)
    }

    // ── 일일 숙제 완주율 ─────────────────────────────────────────────────────

    @Test
    fun 숙제_기록이_있는_게임이_없으면_완주율을_말하지_않는다() {
        assertNull(MyPageStats.dailyTaskRate(emptyList()))
        assertNull(MyPageStats.dailyTaskRate(listOf(task("genshin", rate = 0, days = 0))))
    }

    @Test
    fun 완주율은_기록이_있는_게임끼리만_평균_낸다() {
        val rate = MyPageStats.dailyTaskRate(
            listOf(
                task("genshin", rate = 90, days = 20),
                task("hsr", rate = 75, days = 3),
                task("zzz", rate = 0, days = 0),      // 막 연동한 게임 — 같이 나누면 55 로 떨어진다
            ),
        )
        // (90 + 75) / 2 = 82.5 → 내림.
        assertEquals(82, rate)
    }
}
