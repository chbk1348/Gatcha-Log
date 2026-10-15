package com.gatcha.log.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * 지출 캘린더의 **타임라인 계산**(27.52.0) — Android·iOS 가 이 한 소스를 공유한다.
 *
 * 캘린더는 월 그리드가 아니라 타임라인이다. 고른 달에서 활동(지출 · 픽업 시작/종료)이 있는 날은 한 줄씩,
 * 그 사이 아무 일 없는 날들은 「활동 없음」 한 줄로 묶어 **최신순**으로 늘어놓는다.
 *
 * 두 화면이 같은 조립을 각자 적어 두고 있었는데 **날짜를 세는 달력이 달랐다.** 지출을 날에 나누는 것은
 * 둘 다 공용 [DateUtil] 이었지만, 요일 · 「오늘」 · 달 넘기기는 Android 가 `java.util.Calendar`,
 * iOS 가 `Calendar.current`(기기 달력 설정)였다. 한 화면 안에서 두 달력이 섞이면 날짜 숫자와 요일이
 * 따로 놀 수 있다 — 여기서는 전부 [DateUtil] 과 같은 달력(그레고리력 · 기기 시간대)으로 센다.
 *
 * 지금 시각은 인자로 받는다 — 자정을 넘기는 순간의 「오늘」을 테스트로 고정할 수 있어야 한다.
 */

/** 활동이 있는 하루. */
data class SpendingCalendarDay(
    /** 일(1~31). */
    val day: Int,
    /** 요일 — 일=0 … 토=6. */
    val weekdayIndex: Int,
    val isToday: Boolean,
    /** 그날 지출 — 금액이 큰 순. 같은 금액이면 받은 목록의 순서 그대로. */
    val spendings: List<Spending>,
    val spendTotal: Long,
    /** 그날 시작하는 픽업 — 받은 순서 그대로. */
    val bannerStart: List<GachaBanner>,
    /** 그날 끝나는 픽업 — 받은 순서 그대로. */
    val bannerEnd: List<GachaBanner>,
)

/**
 * 타임라인 한 줄 — 활동이 있는 날([active])이거나, 활동 없는 연속 구간([gapLowDay]..[gapHighDay])이다.
 *
 * 봉인 클래스로 나누지 않고 한 모양으로 둔다 — Swift 가 형 변환 없이 `active` 가 있는지만 보면 된다.
 */
data class SpendingCalendarEntry(
    /** 활동이 있는 날. 「활동 없음」 구간이면 null. */
    val active: SpendingCalendarDay?,
    /** 구간의 첫날. 활동일 줄에서는 0. */
    val gapLowDay: Int,
    /** 구간의 끝날. 활동일 줄에서는 0. */
    val gapHighDay: Int,
) {
    /** 목록 키 — 한 달 안에서 겹치지 않는다(활동일은 날짜, 구간은 끝날). */
    val key: String get() = if (active != null) "a${active.day}" else "g$gapHighDay"
}

/** 한 달 치 타임라인. */
data class SpendingCalendarMonth(
    /** 최신순(큰 날짜가 위). 활동이 하나도 없는 달은 빈 목록 — 화면이 빈 상태를 그린다. */
    val entries: List<SpendingCalendarEntry>,
    /** 그 달 지출 합계. */
    val total: Long,
)

/** 연 · 월(1~12). */
data class SpendingCalendarYearMonth(val year: Int, val month: Int)

object SpendingCalendarLogic {

    /**
     * [year]년 [month]월(1~12)의 타임라인.
     *
     * - 시각이 0 이하인 것은 **날짜를 모르는 것**이라 어느 날에도 올리지 않는다(시작을 안 준 옛 캐시의 픽업 등).
     * - 「활동 없음」 구간은 **활동일 사이에만** 둔다. 첫 활동일 앞 · 마지막 활동일 뒤는 줄을 만들지 않는다 —
     *   달 초에 한 번 쓴 달이 「2일–31일 활동 없음」으로 채워지면 정작 그 하루가 묻힌다.
     *
     * @param nowMillis 「오늘」 표시의 기준 시각
     */
    fun buildMonth(
        spendings: List<Spending>,
        banners: List<GachaBanner>,
        year: Int,
        month: Int,
        nowMillis: Long,
    ): SpendingCalendarMonth {
        val monthKey = year * 100 + month

        // 시각→로컬 변환 **1회**로 연 · 월 · 일을 한꺼번에 얻는다(yyyyMMdd). 지출 전체를 훑는 자리라 그대로 비용이다.
        fun dayOf(millis: Long): Int? {
            if (millis <= 0L) return null
            val ymd = DateUtil.ymd(millis)
            return if (ymd / 100 == monthKey) ymd % 100 else null
        }

        val spendByDay = mutableMapOf<Int, MutableList<Spending>>()
        spendings.forEach { s -> dayOf(s.dateMillis)?.let { d -> spendByDay.getOrPut(d) { mutableListOf() }.add(s) } }
        val startByDay = mutableMapOf<Int, MutableList<GachaBanner>>()
        val endByDay = mutableMapOf<Int, MutableList<GachaBanner>>()
        banners.forEach { b ->
            dayOf(b.startMillis)?.let { d -> startByDay.getOrPut(d) { mutableListOf() }.add(b) }
            dayOf(b.endMillis)?.let { d -> endByDay.getOrPut(d) { mutableListOf() }.add(b) }
        }

        val activeDays = (spendByDay.keys + startByDay.keys + endByDay.keys).sortedDescending()
        if (activeDays.isEmpty()) return SpendingCalendarMonth(entries = emptyList(), total = 0L)

        val todayYmd = DateUtil.ymd(nowMillis)
        var total = 0L
        val entries = buildList<SpendingCalendarEntry> {
            activeDays.forEachIndexed { i, d ->
                // sortedByDescending 은 안정 정렬 — 같은 금액끼리는 받은 순서가 남는다.
                val daySpendings = spendByDay[d].orEmpty().sortedByDescending { it.amount }
                val dayTotal = daySpendings.sumOf { it.amount }
                total += dayTotal
                add(
                    SpendingCalendarEntry(
                        active = SpendingCalendarDay(
                            day = d,
                            weekdayIndex = LocalDate(year, month, d).dayOfWeek.sundayBased,
                            isToday = monthKey * 100 + d == todayYmd,
                            spendings = daySpendings,
                            spendTotal = dayTotal,
                            bannerStart = startByDay[d].orEmpty(),
                            bannerEnd = endByDay[d].orEmpty(),
                        ),
                        gapLowDay = 0,
                        gapHighDay = 0,
                    ),
                )
                // 다음 활동일(d 보다 작다)과 하루 넘게 떨어져 있으면 그 사이를 한 줄로 묶는다.
                val next = activeDays.getOrNull(i + 1)
                if (next != null && d - next > 1) {
                    add(SpendingCalendarEntry(active = null, gapLowDay = next + 1, gapHighDay = d - 1))
                }
            }
        }
        return SpendingCalendarMonth(entries = entries, total = total)
    }

    /**
     * [year]년 [month]월에서 [delta]달 옮긴 연 · 월 — 12월 다음은 이듬해 1월, 1월 앞은 지난해 12월.
     *
     * 날짜 객체를 거치지 않고 달 번호로만 센다. 「그 달 1일」을 만들어 옮기는 방식은 달력 종류와 시간대를 타는데,
     * 연 · 월만 넘기는 데에는 둘 다 필요 없다.
     */
    fun shiftMonth(year: Int, month: Int, delta: Int): SpendingCalendarYearMonth {
        val index = year * 12 + (month - 1) + delta
        return SpendingCalendarYearMonth(year = index.floorDiv(12), month = index.mod(12) + 1)
    }

    /** 일요일을 0 으로 두는 요일 번호 — 화면의 요일 글자 배열(일 … 토)과 같은 순서. */
    private val DayOfWeek.sundayBased: Int
        get() = when (this) {
            DayOfWeek.SUNDAY -> 0; DayOfWeek.MONDAY -> 1; DayOfWeek.TUESDAY -> 2; DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4; DayOfWeek.FRIDAY -> 5; DayOfWeek.SATURDAY -> 6
        }
}
