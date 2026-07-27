package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 일일·주간 숙제 완주율 — 관측 기록에서 파생되는 규칙을 고정한다.
 *
 * 핵심 전제: HoYoLAB 은 '지금 상태'만 주므로 **앱이 노트를 받은 날만** 기록이 생긴다.
 * 안 켠 날을 미완주로 치면 앱 사용 빈도가 완주율을 좌우해 지표가 망가지므로 분모에서 뺀다.
 */
class TaskCompletionTest {

    private val base = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun note(daily: Int, dailyMax: Int, weekly: Int = 0, weeklyMax: Int = 0) =
        LiveNote(
            game = Game.GENSHIN.displayName,
            dailyTaskCount = daily, maxDailyTaskCount = dailyMax,
            weeklyDone = weekly, weeklyTotal = weeklyMax,
        )

    /** [daysAgo] 일 전에 완료(true)/미완료(false)로 관측된 로그. */
    private fun log(vararg days: Pair<Int, Boolean>): GameTaskLog =
        GameTaskLog(daily = days.associate { (ago, done) -> DateUtil.gameDayKeyAgo(ago, base) to done })

    // ── 기록 ────────────────────────────────────────────────────────────────

    @Test
    fun recordMarksTodayDoneOnlyWhenAllTasksFinished() {
        val partial = TaskCompletion.record(GameTaskLog(), note(3, 4), base)
        assertEquals(false, partial.daily[DateUtil.gameDayKey(base)])

        val full = TaskCompletion.record(partial, note(4, 4), base)
        assertEquals(true, full.daily[DateUtil.gameDayKey(base)])
    }

    @Test
    fun recordNeverFlipsDoneBackToNotDone() {
        // 다 하고 나서 리셋 전에 다시 열면 카운트가 0/4 로 보일 수 있다 — 완료는 유지돼야 한다.
        val done = TaskCompletion.record(GameTaskLog(), note(4, 4), base)
        val reopened = TaskCompletion.record(done, note(0, 4), base)
        assertEquals(true, reopened.daily[DateUtil.gameDayKey(base)])
    }

    @Test
    fun recordIgnoresGamesWithoutTaskData() {
        // maxDailyTaskCount 가 0 이면 그 게임은 데이터를 안 준 것 — 기록을 만들지 않는다.
        val out = TaskCompletion.record(GameTaskLog(), note(0, 0), base)
        assertTrue(out.daily.isEmpty())
        assertTrue(out.weekly.isEmpty())
    }

    @Test
    fun recordTracksWeeklyProgressSeparately() {
        val out = TaskCompletion.record(GameTaskLog(), note(4, 4, weekly = 3, weeklyMax = 3), base)
        assertEquals(true, out.weekly[DateUtil.gameWeekKey(base)])

        val partial = TaskCompletion.record(GameTaskLog(), note(4, 4, weekly = 1, weeklyMax = 3), base)
        assertEquals(false, partial.weekly[DateUtil.gameWeekKey(base)])
    }

    // ── 완주율 ──────────────────────────────────────────────────────────────

    @Test
    fun rateCountsOnlyObservedDays() {
        // 4일 관측 중 3일 완료 → 75%. 관측 안 된 나머지 26일은 분모에 넣지 않는다.
        val stats = TaskCompletion.stats("genshin", log(0 to true, 1 to true, 2 to false, 3 to true), base)
        assertEquals(75, stats.dailyRate)
        assertEquals(4, stats.dailyDays)
    }

    @Test
    fun emptyLogIsMarkedEmptyInsteadOfZeroPercent() {
        // 기록이 없으면 '0% 달성'이 아니라 '아직 판단 불가'로 다뤄야 한다.
        val stats = TaskCompletion.stats("genshin", GameTaskLog(), base)
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.dailyDays)
    }

    // ── 스트릭 ──────────────────────────────────────────────────────────────

    @Test
    fun streakCountsConsecutiveDoneDaysFromToday() {
        val stats = TaskCompletion.stats("genshin", log(0 to true, 1 to true, 2 to true, 3 to false), base)
        assertEquals(3, stats.dailyStreak)
        assertTrue(stats.todayDone)
    }

    @Test
    fun streakSurvivesTodayNotDoneYet() {
        // 오늘 아직 안 했다고 어제까지의 연속이 끊긴 것처럼 보이면 안 된다(하루가 아직 안 끝났다).
        val stats = TaskCompletion.stats("genshin", log(1 to true, 2 to true), base)
        assertEquals(2, stats.dailyStreak)
        assertTrue(!stats.todayDone)
    }

    @Test
    fun streakBreaksOnAnObservedMiss() {
        val stats = TaskCompletion.stats("genshin", log(0 to true, 1 to false, 2 to true), base)
        assertEquals(1, stats.dailyStreak)
    }

    @Test
    fun bestStreakKeepsThePastRecord() {
        val stats = TaskCompletion.stats(
            "genshin",
            log(0 to true, 1 to false, 2 to true, 3 to true, 4 to true, 5 to true),
            base,
        )
        assertEquals(1, stats.dailyStreak)
        assertEquals(4, stats.dailyBest)
    }

    // ── 보관 한도 ───────────────────────────────────────────────────────────

    @Test
    fun pruneDropsRecordsOlderThanRetention() {
        val old = GameTaskLog(daily = mapOf(DateUtil.gameDayKeyAgo(200, base) to true))
        val fresh = GameTaskLog(daily = mapOf(DateUtil.gameDayKeyAgo(10, base) to true))
        assertTrue(TaskCompletion.prune(old, base).daily.isEmpty())
        assertEquals(1, TaskCompletion.prune(fresh, base).daily.size)
    }

    // ── 게임 메타 ───────────────────────────────────────────────────────────

    @Test
    fun statsCarryGameNameAndColor() {
        val stats = TaskCompletion.stats("hsr", log(0 to true), base)
        assertEquals("스타레일", stats.gameShort)
        assertEquals(Game.HSR.color, stats.colorArgb)
    }

    @Test
    fun allStatsSkipsGamesWithoutAnyRecord() {
        val logs = mapOf("genshin" to log(0 to true), "hsr" to GameTaskLog())
        assertEquals(listOf("원신"), TaskCompletion.allStats(logs, base).map { it.gameShort })
    }
}
