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
 * 지출 캘린더의 타임라인 — [SpendingCalendarLogic].
 *
 * 틀려도 앱은 안 죽는다. 지출이 옆 달에 한 건 섞이거나 요일이 하루 밀려도 "대충 맞아 보이는" 화면이라
 * 눈으로는 못 잡는다. Android·iOS 가 따로 적어 두었던 조립을 하나로 합치며 규칙을 여기 고정한다(27.52.0).
 *
 * 기준 달은 2026년 10월 — 1일이 목요일, 31일이 토요일이라 앞뒤 달과 **한 주를 나눠 쓴다**(9/30 수 · 11/1 일).
 */
@OptIn(ExperimentalTime::class)
class SpendingCalendarLogicTest {

    /** 로컬 시간대의 그 날 [hour]시 — 날 나누기가 로컬 기준이라 시간대가 달라도 같은 날로 떨어진다. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime(year, month, day, hour, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    private val now = at(2026, 10, 12)

    private fun spend(day: Int, amount: Long = 6_500, month: Int = 10, year: Int = 2026, item: String = "", hour: Int = 12) =
        Spending(gameName = "원신", amount = amount, dateMillis = at(year, month, day, hour), itemName = item)

    private fun banner(name: String, start: Long, end: Long) =
        GachaBanner(game = "원신", name = name, startMillis = start, endMillis = end)

    private fun october(spendings: List<Spending> = emptyList(), banners: List<GachaBanner> = emptyList(), nowMillis: Long = now) =
        SpendingCalendarLogic.buildMonth(spendings, banners, 2026, 10, nowMillis)

    private fun SpendingCalendarMonth.days(): List<SpendingCalendarDay> = entries.mapNotNull { it.active }

    // ---------------------------------------------------------------- 빈 달

    @Test
    fun 활동이_없는_달은_빈_타임라인이다() {
        val empty = october()
        assertTrue(empty.entries.isEmpty())
        assertEquals(0L, empty.total)

        // 다른 달에만 기록이 있어도 마찬가지.
        val otherMonth = october(listOf(spend(day = 20, month = 9)))
        assertTrue(otherMonth.entries.isEmpty())
        assertEquals(0L, otherMonth.total)
    }

    // ---------------------------------------------------------------- 달의 경계

    @Test
    fun 달이_바뀌는_주의_지출은_제_달에만_들어간다() {
        // 9/30(수) · 10/1(목)은 같은 주, 10/31(토) · 11/1(일)은 하룻밤 차이다.
        val list = listOf(
            spend(day = 30, month = 9, amount = 1_000),
            spend(day = 1, amount = 2_000),
            spend(day = 31, amount = 4_000, hour = 23),
            spend(day = 1, month = 11, amount = 8_000, hour = 3),
        )
        val oct = SpendingCalendarLogic.buildMonth(list, emptyList(), 2026, 10, now)
        assertEquals(listOf(31, 1), oct.days().map { it.day })
        assertEquals(6_000L, oct.total)

        val sep = SpendingCalendarLogic.buildMonth(list, emptyList(), 2026, 9, now)
        assertEquals(listOf(30), sep.days().map { it.day })
        assertEquals(1_000L, sep.total)

        val nov = SpendingCalendarLogic.buildMonth(list, emptyList(), 2026, 11, now)
        assertEquals(listOf(1), nov.days().map { it.day })
        assertEquals(8_000L, nov.total)
    }

    @Test
    fun 같은_달이라도_해가_다르면_들어오지_않는다() {
        val r = october(listOf(spend(day = 12, year = 2025)))
        assertTrue(r.entries.isEmpty())
    }

    @Test
    fun 요일은_일요일이_0이다() {
        val list = listOf(spend(day = 1), spend(day = 4), spend(day = 31))
        val byDay = october(list).days().associate { it.day to it.weekdayIndex }
        assertEquals(4, byDay[1], "10/1 은 목요일")
        assertEquals(0, byDay[4], "10/4 는 일요일")
        assertEquals(6, byDay[31], "10/31 은 토요일")

        // 앞뒤 달로 넘어가도 이어진다 — 9/30 수요일, 11/1 일요일.
        val sep = SpendingCalendarLogic.buildMonth(listOf(spend(day = 30, month = 9)), emptyList(), 2026, 9, now)
        assertEquals(3, sep.days().single().weekdayIndex)
        val nov = SpendingCalendarLogic.buildMonth(listOf(spend(day = 1, month = 11)), emptyList(), 2026, 11, now)
        assertEquals(0, nov.days().single().weekdayIndex)
    }

    // ---------------------------------------------------------------- 순서와 「활동 없음」 구간

    @Test
    fun 최신순으로_놓고_빈_날들은_한_줄로_묶는다() {
        val r = october(listOf(spend(day = 5), spend(day = 12), spend(day = 10)))
        assertEquals(listOf("a12", "g11", "a10", "g9", "a5"), r.entries.map { it.key })

        // 하루짜리 구간 — 첫날과 끝날이 같다.
        val oneDay = r.entries[1]
        assertNull(oneDay.active)
        assertEquals(11, oneDay.gapLowDay)
        assertEquals(11, oneDay.gapHighDay)

        // 여러 날 구간 — 활동일 바로 다음 날부터 바로 앞 날까지.
        val several = r.entries[3]
        assertNull(several.active)
        assertEquals(6, several.gapLowDay)
        assertEquals(9, several.gapHighDay)
    }

    @Test
    fun 붙어_있는_날_사이에는_구간을_두지_않는다() {
        val r = october(listOf(spend(day = 11), spend(day = 12)))
        assertEquals(listOf("a12", "a11"), r.entries.map { it.key })
    }

    @Test
    fun 첫_활동일_앞과_마지막_활동일_뒤는_구간을_만들지_않는다() {
        // 달 한가운데 하루만 쓴 달이 「1일–14일」 · 「16일–31일」로 채워지면 정작 그 하루가 묻힌다.
        val r = october(listOf(spend(day = 15)))
        assertEquals(listOf("a15"), r.entries.map { it.key })
    }

    // ---------------------------------------------------------------- 하루 안

    @Test
    fun 그날_지출은_금액이_큰_순_같으면_받은_순서다() {
        val a = spend(day = 12, amount = 1_000, item = "A")
        val b = spend(day = 12, amount = 5_000, item = "B")
        val c = spend(day = 12, amount = 1_000, item = "C")
        val day = october(listOf(a, b, c)).days().single()
        assertEquals(listOf("B", "A", "C"), day.spendings.map { it.itemName })
        assertEquals(3, day.spendings.size)
        assertEquals(7_000L, day.spendTotal)
    }

    @Test
    fun 월_합계는_날짜별_합계를_더한_값이다() {
        val r = october(
            listOf(
                spend(day = 3, amount = 1_200),
                spend(day = 3, amount = 6_500),
                spend(day = 20, amount = 37_000),
                spend(day = 20, month = 9, amount = 999_999),   // 지난달 — 세지 않는다
            ),
        )
        assertEquals(listOf(37_000L, 7_700L), r.days().map { it.spendTotal })
        assertEquals(44_700L, r.total)
    }

    // ---------------------------------------------------------------- 픽업

    @Test
    fun 픽업은_시작하는_날과_끝나는_날에_올린다() {
        val x = banner("푸리나", start = at(2026, 10, 3), end = at(2026, 10, 24))
        val y = banner("나히다", start = at(2026, 9, 12), end = at(2026, 10, 3))   // 지난달에 시작
        val r = october(banners = listOf(x, y))

        // 지출이 없어도 픽업만으로 활동일이 된다.
        assertEquals(listOf("a24", "g23", "a3"), r.entries.map { it.key })
        assertEquals(0L, r.total)

        val (d24, d3) = r.days()
        assertTrue(d24.spendings.isEmpty())
        assertTrue(d24.bannerStart.isEmpty())
        assertEquals(listOf("푸리나"), d24.bannerEnd.map { it.name })
        assertEquals(listOf("푸리나"), d3.bannerStart.map { it.name })
        assertEquals(listOf("나히다"), d3.bannerEnd.map { it.name })
    }

    @Test
    fun 시각을_모르는_픽업은_어느_날에도_올리지_않는다() {
        // 시작을 안 준 옛 캐시(0) — 끝나는 날에만 보인다.
        val r = october(banners = listOf(banner("모름", start = 0L, end = at(2026, 10, 24))))
        val day = r.days().single()
        assertEquals(24, day.day)
        assertTrue(day.bannerStart.isEmpty())
        assertEquals(1, day.bannerEnd.size)
    }

    @Test
    fun 날짜가_0_이하인_지출은_1970년에도_올리지_않는다() {
        val broken = listOf(
            Spending(gameName = "원신", amount = 1_000, dateMillis = 0L),
            Spending(gameName = "원신", amount = 1_000, dateMillis = -1L),
        )
        // 0 은 시간대에 따라 1970-01-01 이거나 1969-12-31 이다 — 둘 다 비어 있어야 한다.
        assertTrue(SpendingCalendarLogic.buildMonth(broken, emptyList(), 1970, 1, now).entries.isEmpty())
        assertTrue(SpendingCalendarLogic.buildMonth(broken, emptyList(), 1969, 12, now).entries.isEmpty())
    }

    // ---------------------------------------------------------------- 오늘

    @Test
    fun 오늘은_하루만_표시된다() {
        val r = october(listOf(spend(day = 11), spend(day = 12), spend(day = 13)))
        assertEquals(listOf(12), r.days().filter { it.isToday }.map { it.day })
    }

    @Test
    fun 날이_넘어가면_오늘도_넘어간다() {
        val list = listOf(spend(day = 12), spend(day = 13))
        val nextMorning = october(list, nowMillis = at(2026, 10, 13, hour = 3))
        assertEquals(listOf(13), nextMorning.days().filter { it.isToday }.map { it.day })
    }

    @Test
    fun 다른_달을_보고_있으면_오늘이_없다() {
        // 같은 12일이라도 지난달 · 지난해의 12일은 오늘이 아니다.
        val sep = SpendingCalendarLogic.buildMonth(listOf(spend(day = 12, month = 9)), emptyList(), 2026, 9, now)
        assertFalse(sep.days().single().isToday)
        val lastYear = SpendingCalendarLogic.buildMonth(listOf(spend(day = 12, year = 2025)), emptyList(), 2025, 10, now)
        assertFalse(lastYear.days().single().isToday)
    }

    // ---------------------------------------------------------------- 달 넘기기

    @Test
    fun 다음_달과_이전_달() {
        assertEquals(SpendingCalendarYearMonth(2026, 11), SpendingCalendarLogic.shiftMonth(2026, 10, 1))
        assertEquals(SpendingCalendarYearMonth(2026, 9), SpendingCalendarLogic.shiftMonth(2026, 10, -1))
        assertEquals(SpendingCalendarYearMonth(2026, 10), SpendingCalendarLogic.shiftMonth(2026, 10, 0))
    }

    @Test
    fun 해를_넘어간다() {
        assertEquals(SpendingCalendarYearMonth(2027, 1), SpendingCalendarLogic.shiftMonth(2026, 12, 1))
        assertEquals(SpendingCalendarYearMonth(2025, 12), SpendingCalendarLogic.shiftMonth(2026, 1, -1))
    }

    @Test
    fun 여러_달을_한_번에_옮겨도_맞다() {
        assertEquals(SpendingCalendarYearMonth(2028, 3), SpendingCalendarLogic.shiftMonth(2026, 3, 24))
        assertEquals(SpendingCalendarYearMonth(2024, 12), SpendingCalendarLogic.shiftMonth(2026, 3, -15))
    }
}
