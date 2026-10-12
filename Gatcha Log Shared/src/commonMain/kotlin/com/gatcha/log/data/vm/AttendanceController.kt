package com.gatcha.log.data.vm

import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GatchaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 출석 상태 — 오늘 출석 · 날짜별 이력 · 연속 일수. 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고, 27.52.0).
 *
 * 화면이 읽는 흐름과 「저장소에서 다시 읽기 · 오늘 몫 고치기」만 맡는다. HoYoLAB 에 출석을 보내는 일(네트워크 · 진행 표시)은
 * 뷰모델에 남아 있고, 성공하면 여기 [markCheckedIn] 을 부른다.
 *
 * @param repo 지금 계정의 저장소 — 계정이 바뀌면 다른 것을 돌려준다. 그 뒤 [reload] 를 불러야 화면이 따라온다.
 * @param onEdited 화면에서 출석을 고친 뒤 — 출석 리마인더 예약은 남은 출석을 보고 걸리므로 다시 걸어야 한다.
 */
internal class AttendanceController(
    private val repo: () -> GatchaRepository,
    private val onEdited: () -> Unit,
) {
    private var attendanceMap: Map<String, Set<String>> = emptyMap()
    private val _attendanceToday = MutableStateFlow<Set<String>>(emptySet())
    val today: StateFlow<Set<String>> = _attendanceToday.asStateFlow()

    /** 날짜별 출석 이력(dayKey "yyyy-MM-dd" → 출석한 게임키 집합) — 7일 스트립·월간 달력용. */
    private val _attendanceHistory = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val history: StateFlow<Map<String, Set<String>>> = _attendanceHistory.asStateFlow()

    /** 연속 출석 일수(오늘 미출석이면 어제 기준으로 유지). */
    private val _attendanceStreak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _attendanceStreak.asStateFlow()

    private fun todayKey() = DateUtil.hoyoDayKey()

    private fun computeStreak(): Int {
        var offset = 0
        if (attendanceMap[DateUtil.hoyoDayKeyAgo(0)].isNullOrEmpty()) offset = 1
        var streak = 0
        while (!attendanceMap[DateUtil.hoyoDayKeyAgo(offset + streak)].isNullOrEmpty()) streak++
        return streak
    }

    /** 출석 기록을 저장소에서 다시 읽어 화면 상태를 맞춘다. */
    fun reload() {
        attendanceMap = repo().loadAttendance()
        _attendanceHistory.value = attendanceMap
        _attendanceToday.value = attendanceMap[todayKey()] ?: emptySet()
        _attendanceStreak.value = computeStreak()
    }

    /**
     * 저장소에 적힌 **오늘 출석**. 메모리 사본이 아니라 저장소에서 읽는다 — 자동 출석([com.gatcha.log.data.work.AutoCheckInRunner])은
     * 따로 만든 저장소로 쓰고, 자정을 넘겼을 수도 있다.
     */
    fun todayDone(): Set<String> = repo().loadAttendance()[todayKey()].orEmpty()

    fun toggle(gameKey: String) = editToday { if (gameKey in it) it - gameKey else it + gameKey }

    fun markCheckedIn(gameKey: String) = editToday { it + gameKey }

    /**
     * 오늘 몫을 고친다. **고치기 직전에 저장소를 다시 읽는다** — 메모리 사본으로 고치면 자동 출석이 방금 저장한 출석을
     * 덮어 지운다.
     */
    private fun editToday(edit: (Set<String>) -> Set<String>) {
        val today = todayKey()
        val map = repo().loadAttendance()
        val next = edit(map[today].orEmpty())
        attendanceMap = map + (today to next)
        repo().saveAttendance(attendanceMap)
        _attendanceHistory.value = attendanceMap
        _attendanceToday.value = next
        _attendanceStreak.value = computeStreak()
        onEdited()
    }
}
