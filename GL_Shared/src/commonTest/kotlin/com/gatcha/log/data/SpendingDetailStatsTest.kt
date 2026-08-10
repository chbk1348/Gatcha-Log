package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * 지출 상세 2.0 의 상대값 — [SpendingDetailStats].
 *
 * 이 값들은 화면에 **문장으로** 나간다("평소보다 3.2배", "3번째 · 누적 714,000원").
 * 틀려도 앱은 멀쩡히 돌고 숫자만 조용히 어긋나므로, 경계 조건을 여기 고정한다.
 * 특히 '평소'의 정의(평균 아닌 중앙값)와 표본 하한은 판단이 들어간 선택이라 테스트로 못 박는다.
 */
@OptIn(ExperimentalTime::class)
class SpendingDetailStatsTest {

    /** 로컬 타임존 정오 기준 epoch millis — 월 판정이 로컬 tz 라 경계에서 안전한 시각. */
    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDateTime(year, month, day, 12, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun spend(
        id: String,
        amount: Long,
        millis: Long,
        game: String = "원신",
        item: String = "",
        sub: Boolean = false,
    ) = Spending(id = id, gameName = game, amount = amount, dateMillis = millis, itemName = item, isSubscription = sub)

    // ── share ────────────────────────────────────────────────────────────────

    @Test
    fun `비중은 같은 달만 센다`() {
        val target = spend("t", 30_000, at(2026, 7, 15))
        val all = listOf(
            target,
            spend("a", 70_000, at(2026, 7, 2)),      // 같은 달
            spend("b", 900_000, at(2026, 6, 30)),    // 지난달 — 섞이면 안 된다
        )
        val s = SpendingDetailStats.share(target, all)
        assertEquals(100_000, s.monthTotal)
        assertEquals(30, s.monthPercent)
    }

    @Test
    fun `게임 비중은 같은 게임끼리만 본다`() {
        val target = spend("t", 30_000, at(2026, 7, 15), game = "원신")
        val all = listOf(
            target,
            spend("a", 10_000, at(2026, 7, 3), game = "원신"),
            spend("b", 60_000, at(2026, 7, 5), game = "붕괴: 스타레일"),
        )
        val s = SpendingDetailStats.share(target, all)
        assertEquals(100_000, s.monthTotal)
        assertEquals(40_000, s.gameMonthTotal)
        assertEquals(30, s.monthPercent)
        assertEquals(75, s.gamePercent)   // 30,000 / 40,000
    }

    @Test
    fun `그 달에 이 지출뿐이면 100퍼센트`() {
        val target = spend("t", 5_000, at(2026, 7, 15))
        val s = SpendingDetailStats.share(target, listOf(target))
        assertEquals(100, s.monthPercent)
    }

    // ── vsTypical ────────────────────────────────────────────────────────────

    @Test
    fun `표본이 셋 미만이면 평소를 말하지 않는다`() {
        val now = at(2026, 7, 20)
        val target = spend("t", 200_000, at(2026, 7, 15))
        val all = listOf(target, spend("a", 10_000, at(2026, 7, 1)), spend("b", 20_000, at(2026, 7, 2)))
        // 대상 제외하면 표본 2건 → null
        assertNull(SpendingDetailStats.vsTypical(target, all, nowMillis = now))
    }

    @Test
    fun `평소는 평균이 아니라 중앙값이다`() {
        val now = at(2026, 7, 20)
        val target = spend("t", 100_000, at(2026, 7, 15))
        // 10,000 / 10,000 / 10,000 / 1,000,000 → 평균 257,500 vs 중앙값 10,000
        val all = listOf(
            target,
            spend("a", 10_000, at(2026, 7, 1)),
            spend("b", 10_000, at(2026, 7, 2)),
            spend("c", 10_000, at(2026, 7, 3)),
            spend("d", 1_000_000, at(2026, 7, 4)),
        )
        val r = assertNotNull(SpendingDetailStats.vsTypical(target, all, nowMillis = now))
        assertEquals(10_000, r.median)
        // 평균(257,500)을 썼다면 0.39배로 "평소보다 작다"가 됐을 것 — 중앙값이라 10배다.
        assertEquals(10.0, r.ratio)
        assertTrue(r.isNotable)
    }

    @Test
    fun `정기결제는 평소와 견주지 않는다`() {
        val now = at(2026, 7, 20)
        val target = spend("t", 4_900, at(2026, 7, 15), sub = true)
        val all = listOf(target) + (1..5).map { spend("a$it", 10_000, at(2026, 7, it)) }
        assertNull(SpendingDetailStats.vsTypical(target, all, nowMillis = now))
    }

    @Test
    fun `다른 게임 지출은 표본에 들어가지 않는다`() {
        val now = at(2026, 7, 20)
        val target = spend("t", 50_000, at(2026, 7, 15), game = "원신")
        val all = listOf(target) + (1..5).map { spend("h$it", 10_000, at(2026, 7, it), game = "붕괴: 스타레일") }
        assertNull(SpendingDetailStats.vsTypical(target, all, nowMillis = now))
    }

    // ── sameItemHistory ──────────────────────────────────────────────────────

    @Test
    fun `같은 항목 이력은 횟수와 누적을 센다`() {
        val target = spend("t", 238_000, at(2026, 7, 15), item = "오래된 꿈 6480 ×2")
        val all = listOf(
            target,
            spend("a", 238_000, at(2026, 5, 2), item = "오래된 꿈 6480 ×2"),
            spend("b", 238_000, at(2026, 3, 18), item = "오래된 꿈 6480 ×2"),
            spend("c", 10_000, at(2026, 4, 1), item = "창세의 결정 330"),   // 다른 항목
        )
        val h = assertNotNull(SpendingDetailStats.sameItemHistory(target, all))
        assertEquals(3, h.count)
        assertEquals(714_000, h.totalAmount)
        assertEquals(3, h.ordinal)                      // 오래된 것부터 세어 이번이 3번째
        assertEquals(at(2026, 7, 15), h.entries.first().dateMillis)  // 최신순
    }

    @Test
    fun `항목명이 다르면 묶지 않는다`() {
        // "창세의 결정 990" 과 "330" 은 다른 상품이다 — 느슨하게 묶으면 누적이 엉킨다.
        val target = spend("t", 30_000, at(2026, 7, 15), item = "창세의 결정 990")
        val all = listOf(target, spend("a", 10_000, at(2026, 6, 1), item = "창세의 결정 330"))
        val h = assertNotNull(SpendingDetailStats.sameItemHistory(target, all))
        assertEquals(1, h.count)
    }

    @Test
    fun `항목명이 없으면 이력을 만들지 않는다`() {
        val target = spend("t", 30_000, at(2026, 7, 15), item = "")
        assertNull(SpendingDetailStats.sameItemHistory(target, listOf(target)))
    }

    @Test
    fun `한 건뿐이면 평균 간격이 없다`() {
        val target = spend("t", 30_000, at(2026, 7, 15), item = "월정액")
        val h = assertNotNull(SpendingDetailStats.sameItemHistory(target, listOf(target)))
        assertEquals(1, h.count)
        assertNull(h.averageIntervalDays)
    }

    @Test
    fun `평균 간격은 처음과 마지막 사이를 구매 사이 수로 나눈다`() {
        val target = spend("t", 10_000, at(2026, 7, 11), item = "월정액")
        val all = listOf(
            target,
            spend("a", 10_000, at(2026, 6, 11), item = "월정액"),
            spend("b", 10_000, at(2026, 5, 12), item = "월정액"),
        )
        val h = assertNotNull(SpendingDetailStats.sameItemHistory(target, all))
        // 5/12 → 7/11 = 60일, 구매 사이 2회 → 30일
        assertEquals(30, h.averageIntervalDays)
    }
}
