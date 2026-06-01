package com.gatcha.log.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import com.gatcha.log.util.currentTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 날짜 포맷/그룹핑 유틸 — :app 의 DateUtil(Calendar/SimpleDateFormat)과 동일한 API 를
 * kotlinx-datetime 으로 구현 (KMP). 출력 문자열 형식은 원본과 동일하다.
 */
@OptIn(ExperimentalTime::class)
object DateUtil {

    private val localTz: TimeZone get() = TimeZone.currentSystemDefault()

    // HoYoLAB 출석은 베이징 표준시(UTC+8) 자정에 초기화됨 → 출석 날짜는 로컬이 아닌 베이징 기준으로 계산.
    private val hoyoTz: TimeZone = TimeZone.of("UTC+8")

    private fun local(millis: Long, tz: TimeZone = localTz): LocalDateTime =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)

    /** 한국어 요일 (월~일) — SimpleDateFormat "E" (Locale.KOREA) 와 동일 표기 */
    private val DayOfWeek.koLabel: String
        get() = when (this) {
            DayOfWeek.MONDAY -> "월"; DayOfWeek.TUESDAY -> "화"; DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"; DayOfWeek.FRIDAY -> "금"; DayOfWeek.SATURDAY -> "토"
            else -> "일"
        }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"

    /** "2026년 5월 20일" */
    fun label(millis: Long): String =
        local(millis).let { "${it.year}년 ${it.month.number}월 ${it.day}일" }

    /** "2026년 5월 22일 (금)" */
    fun labelWithWeekday(millis: Long): String =
        local(millis).let { "${it.year}년 ${it.month.number}월 ${it.day}일 (${it.dayOfWeek.koLabel})" }

    /** "5월 22일 (금)" */
    fun shortLabelWithWeekday(millis: Long): String =
        local(millis).let { "${it.month.number}월 ${it.day}일 (${it.dayOfWeek.koLabel})" }

    /** 그룹핑 키 "2026-05-20" */
    fun dayKey(millis: Long): String =
        local(millis).let { "${it.year}-${pad2(it.month.number)}-${pad2(it.day)}" }

    /** 출석 기준(베이징 UTC+8) 날짜 키 "2026-05-20" */
    fun hoyoDayKey(millis: Long = currentTimeMillis()): String =
        local(millis, hoyoTz).let { "${it.year}-${pad2(it.month.number)}-${pad2(it.day)}" }

    /**
     * 출석 기준일에서 [daysAgo]일 전의 날짜 키 — 연속 출석(streak) 계산용.
     * :app 의 hoyoCalendar() + Calendar.add(DAY_OF_YEAR, -n) 패턴을 대체.
     * (UTC+8 은 DST 가 없어 millis 산술로 안전)
     */
    fun hoyoDayKeyAgo(daysAgo: Int): String =
        hoyoDayKey(currentTimeMillis() - daysAgo * 86_400_000L)

    /** "5/20 09:00" (배너 기간 표시용) */
    fun shortDateTime(millis: Long): String =
        local(millis).let { "${it.month.number}/${pad2(it.day)} ${pad2(it.hour)}:${pad2(it.minute)}" }

    /** "6/8" (이벤트·정기콘텐츠 종료일 표시용) */
    fun shortDate(millis: Long): String =
        local(millis).let { "${it.month.number}/${it.day}" }

    fun year(millis: Long): Int = local(millis).year

    fun month(millis: Long): Int = local(millis).month.number

    fun isSameMonth(millis: Long, year: Int, month: Int): Boolean =
        year(millis) == year && month(millis) == month

    fun isSameYear(millis: Long, year: Int): Boolean = year(millis) == year
}
