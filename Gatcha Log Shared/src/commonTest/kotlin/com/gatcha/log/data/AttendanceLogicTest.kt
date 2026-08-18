package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 출석 집계 — [AttendanceLogic].
 *
 * 여기 숫자가 틀려도 앱은 멀쩡히 돈다. "이번 달 18일"이 17일로 나와도 아무도 신고하지 않고,
 * 두 플랫폼이 다른 값을 보여도 한쪽만 보는 사람은 모른다. 규칙을 고정한다.
 */
class AttendanceLogicTest {

    private val keys = GameData.attendanceGames.map { it.key }
    private val today = "2026-08-11"

    /** 그날 전체 출석한 기록 한 줄. */
    private fun full(day: String) = day to keys.toSet()

    @Test
    fun `오늘 상태는 게임별로 나뉜다`() {
        val s = AttendanceLogic.summary(emptyMap(), setOf(keys.first()), streak = 3, todayKey = today)
        assertEquals(1, s.todayDone)
        assertEquals(keys.size, s.todayTotal)
        assertEquals(keys.size - 1, s.pending)
        assertTrue(!s.allDone)
        assertTrue(s.games.first().checkedToday)
        assertTrue(!s.games.last().checkedToday)
    }

    @Test
    fun `이번 달만 센다 — 지난달 기록은 섞이지 않는다`() {
        val history = mapOf(full("2026-08-01"), full("2026-08-02"), full("2026-07-31"))
        val s = AttendanceLogic.summary(history, emptySet(), streak = 0, todayKey = today)
        assertEquals(2, s.monthFullDays)
    }

    @Test
    fun `일부만 출석한 날은 출석일로 세지 않는다`() {
        // 달력이 그날을 '일부'(연한 원)로 그리는데 숫자만 출석일로 세면 서로 어긋나 보인다.
        val history = mapOf(full("2026-08-01"), "2026-08-02" to setOf(keys.first()))
        val s = AttendanceLogic.summary(history, emptySet(), streak = 0, todayKey = today)
        assertEquals(1, s.monthFullDays)
        // 게임별 누계는 그 게임 기준이라 부분 출석도 들어간다.
        assertEquals(2, s.games.first().monthCount)
        assertEquals(1, s.games.last().monthCount)
    }

    @Test
    fun `분모는 이번 달 전체가 아니라 오늘까지다`() {
        // 8월은 31일이지만 오늘이 11일이면 아직 20일은 오지 않았다 — 못 지킨 날로 세면 안 된다.
        val s = AttendanceLogic.summary(emptyMap(), emptySet(), streak = 0, todayKey = today)
        assertEquals(11, s.monthElapsedDays)
    }

    @Test
    fun `기록이 없어도 게임 줄은 전부 남는다`() {
        // 줄이 빠지면 "이 게임은 왜 없지?"를 확인하러 다른 화면으로 가야 한다.
        val s = AttendanceLogic.summary(emptyMap(), emptySet(), streak = 0, todayKey = today)
        assertEquals(GameData.attendanceGames.size, s.games.size)
        assertTrue(s.games.all { it.monthCount == 0 })
    }

    @Test
    fun `전부 출석하면 남은 게 없다`() {
        val s = AttendanceLogic.summary(emptyMap(), keys.toSet(), streak = 12, todayKey = today)
        assertTrue(s.allDone)
        assertEquals(0, s.pending)
        assertEquals(12, s.streak)
    }
}
