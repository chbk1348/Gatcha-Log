package com.gatcha.log.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * HoYoLAB 출석 기준(베이징 UTC+8) [Calendar] 생성 — Android 전용.
 *
 * 출석 날짜 가감·캘린더 그리드 계산에 쓰던 구 `DateUtil.hoyoCalendar()` 를 분리한 것.
 * java.util.Calendar 는 KMP 공통화가 불가해 Shared DateUtil 로 옮길 수 없고, 같은 FQN 으로
 * 두면 dex 병합 충돌(NoSuchMethodError)이 나므로 별도 헬퍼로 둔다. 나머지 날짜 포맷은
 * Shared 정본 [DateUtil] 을 그대로 쓴다.
 */
object HoyoCalendar {
    private val hoyoTz: TimeZone = TimeZone.getTimeZone("GMT+8")

    /** 출석 기준(베이징 UTC+8) Calendar (전/후 일자 가감용) */
    fun instance(): Calendar = Calendar.getInstance(hoyoTz, Locale.KOREA)
}
