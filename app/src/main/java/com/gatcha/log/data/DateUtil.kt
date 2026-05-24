package com.gatcha.log.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 날짜 포맷/그룹핑 유틸. minSdk 24 호환을 위해 java.time 대신 Calendar/SimpleDateFormat 사용.
 */
object DateUtil {
    private val ko = Locale.KOREA

    /** "2026년 5월 20일" */
    fun label(millis: Long): String =
        SimpleDateFormat("yyyy년 M월 d일", ko).format(Date(millis))

    /** "2026년 5월 22일 (금)" */
    fun labelWithWeekday(millis: Long): String =
        SimpleDateFormat("yyyy년 M월 d일 (E)", ko).format(Date(millis))

    /** "5월 22일 (금)" */
    fun shortLabelWithWeekday(millis: Long): String =
        SimpleDateFormat("M월 d일 (E)", ko).format(Date(millis))

    /** 그룹핑 키 "2026-05-20" */
    fun dayKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", ko).format(Date(millis))

    /** "5/20 09:00" (배너 기간 표시용) */
    fun shortDateTime(millis: Long): String =
        SimpleDateFormat("M/dd HH:mm", ko).format(Date(millis))

    /** "6/8" (이벤트·정기콘텐츠 종료일 표시용) */
    fun shortDate(millis: Long): String =
        SimpleDateFormat("M/d", ko).format(Date(millis))

    fun year(millis: Long): Int = cal(millis).get(Calendar.YEAR)

    /** Calendar.MONTH 는 0-base 이므로 +1 */
    fun month(millis: Long): Int = cal(millis).get(Calendar.MONTH) + 1

    fun isSameMonth(millis: Long, year: Int, month: Int): Boolean =
        year(millis) == year && month(millis) == month

    fun isSameYear(millis: Long, year: Int): Boolean = year(millis) == year

    private fun cal(millis: Long): Calendar =
        Calendar.getInstance(ko).apply { timeInMillis = millis }
}