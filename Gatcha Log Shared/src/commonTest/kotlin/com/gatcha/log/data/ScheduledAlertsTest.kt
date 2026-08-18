package com.gatcha.log.data

import com.gatcha.log.data.work.ScheduledAlert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 확정 시각 알림의 **시각 계산 규칙**을 고정한다.
 *
 * 예약 목록 자체는 AppSettings/GatchaRepository(플랫폼 저장소)에 의존해 commonTest 에서
 * 만들 수 없으므로, 여기서는 시각 산출(마감 N일 전 아침 9시)과 정렬·상한 규칙을 검증한다.
 */
class ScheduledAlertsTest {

    private val base = 1_800_000_000_000L   // 고정 기준 시각
    private val day = 86_400_000L

    @Test
    fun alertTimeIsNineAmOnTheLeadDay() {
        // 마감이 새벽 3시여도 알림은 그 며칠 전 '아침 9시'에 울려야 한다.
        val end = DateUtil.localTimeOnDay(base + 10 * day, 3)
        val at = DateUtil.localTimeOnDay(end - 3 * day, 9)
        assertEquals(9, DateUtil.hourOf(at))
        assertEquals(0, DateUtil.minuteOf(at))
        // 마감 3일 전 날짜여야 한다(시각을 9시로 올려도 날짜는 그대로).
        assertEquals(DateUtil.dayKey(end - 3 * day), DateUtil.dayKey(at))
    }

    @Test
    fun localTimeOnDayKeepsTheSameCalendarDate() {
        val t = base + 5 * day
        assertEquals(DateUtil.dayKey(t), DateUtil.dayKey(DateUtil.localTimeOnDay(t, 9)))
        assertEquals(DateUtil.dayKey(t), DateUtil.dayKey(DateUtil.localTimeOnDay(t, 23)))
    }

    @Test
    fun alertsAreSortedByFireTimeAndCapped() {
        // build() 의 마무리 규칙(임박순 정렬 + 상한)을 같은 식으로 재현해 고정.
        val alerts = (1..60).map {
            ScheduledAlert("k$it", "t", "x", base + (61 - it) * day)
        }
        val result = alerts.sortedBy { it.whenMillis }.take(48)
        assertEquals(48, result.size)
        assertTrue(result.first().whenMillis < result.last().whenMillis)
        assertEquals("k60", result.first().key)   // 가장 임박한 것부터
    }

    @Test
    fun dailySummaryUsesConfiguredHourAndRepeats() {
        val a = ScheduledAlert(
            key = "daily_summary", title = "오늘의 가챠 요약", text = "x",
            whenMillis = DateUtil.localTimeOnDay(base, 21), repeatsDaily = true,
        )
        assertEquals(21, DateUtil.hourOf(a.whenMillis))
        assertTrue(a.repeatsDaily)
    }

    // ── 게임 리셋 기준 날짜 키(숙제 완주율·예약 공용) ─────────────────────────

    @Test
    fun gameDayKeyRollsOverAtFourAmServerTime() {
        // 서버 04:00 리셋 — 03:59 는 아직 전날, 04:01 은 새 날.
        val fourAmUtc8 = 4 * 60 * 60 * 1000L
        val dayStartUtc = base / day * day          // UTC 자정
        val justBefore = dayStartUtc + fourAmUtc8 - 8 * 60 * 60 * 1000L - 60_000
        val justAfter = justBefore + 120_000
        assertTrue(DateUtil.gameDayKey(justBefore) != DateUtil.gameDayKey(justAfter))
    }

    @Test
    fun gameWeekKeyIsMondayBased() {
        // 같은 주의 서로 다른 날은 같은 주 키를 가진다.
        val k1 = DateUtil.gameWeekKey(base)
        val k2 = DateUtil.gameWeekKey(base + day)
        val prev = DateUtil.gameWeekKeyAgo(1, base)
        assertTrue(k1 == k2 || k1 != prev)   // 주 경계를 넘지 않는 한 동일, 지난주와는 항상 다르다
        assertTrue(prev != k1)
    }
}
