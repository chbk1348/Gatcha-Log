package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis

/**
 * 일일·주간 숙제 완주율 — **결정형 룰**(AI 없음).
 *
 * HoYoLAB 실시간 노트는 '지금 상태'만 준다(오늘 일일 임무 3/4). 완주율을 내려면 앱이
 * 노트를 받아올 때마다 그날 다 했는지를 로컬에 적어 두는 수밖에 없다. 그래서 이 파일은
 * **관측 기록(TaskLog)** 과 **그 기록에서 파생되는 통계(TaskStats)** 두 가지만 다룬다.
 *
 * 정직한 분모: 앱을 안 켠 날은 관측이 없으므로 **기록이 있는 날만** 분모에 넣는다.
 * (안 켠 날을 미완주로 치면 앱 사용 빈도가 완주율을 좌우해 지표가 망가진다.)
 *
 * 완료 판정은 한 번 true 면 유지(단조) — 다 하고 나서 리셋 전에 다시 열어도 뒤집히지 않는다.
 */

/** 게임 하나의 숙제 관측 기록. 키는 [DateUtil.gameDayKey] / [DateUtil.gameWeekKey]. */
data class GameTaskLog(
    val daily: Map<String, Boolean> = emptyMap(),
    val weekly: Map<String, Boolean> = emptyMap(),
)

/** 게임 하나의 완주율 통계(표시용). */
data class TaskStats(
    val gameKey: String,
    val gameShort: String,
    /** 게임 대표색 ARGB(0xAARRGGBB). */
    val colorArgb: Long,
    /** 오늘 일일 숙제를 끝냈나(관측 기준). */
    val todayDone: Boolean,
    /** 이번 주 주간 숙제를 끝냈나. 주간 데이터를 안 주는 게임이면 항상 false. */
    val weekDone: Boolean,
    /** 최근 [TaskCompletion.WINDOW_DAYS]일 중 기록이 있는 날 기준 완주율(0~100). */
    val dailyRate: Int,
    /** 위 완주율의 분모 = 기록이 있는 날 수. 0이면 아직 판단할 근거가 없다. */
    val dailyDays: Int,
    /** 오늘(또는 아직 리셋 전인 어제)부터 거슬러 올라간 연속 완주 일수. */
    val dailyStreak: Int,
    /** 관측 이래 최고 연속 완주 일수. */
    val dailyBest: Int,
    /** 최근 [TaskCompletion.WEEK_WINDOW]주 중 기록이 있는 주 기준 완주율(0~100). */
    val weeklyRate: Int,
    /** 위 완주율의 분모 = 기록이 있는 주 수. */
    val weeklyWeeks: Int,
) {
    /** 아직 통계를 낼 만큼 쌓이지 않음 — UI 는 완주율 대신 안내 문구를 보여준다. */
    val isEmpty: Boolean get() = dailyDays == 0 && weeklyWeeks == 0
}

object TaskCompletion {

    /** 일일 완주율 집계 구간(일). */
    const val WINDOW_DAYS = 30

    /** 주간 완주율 집계 구간(주). */
    const val WEEK_WINDOW = 12

    /** 기록 보관 한도 — 이보다 오래된 날짜/주는 버린다(prefs 무한 증식 방지). */
    private const val KEEP_DAYS = 120
    private const val KEEP_WEEKS = 30

    /**
     * 미완(false)을 실패로 인정하기 시작하는 시점 — 게임 하루가 이만큼 지난 뒤.
     *
     * 18 = 리셋 6시간 전. 그 전의 미완은 "아직 안 함"이 아니라 **하는 중**이다.
     */
    private const val LATE_HOUR = 18

    /**
     * 실시간 노트 한 건을 기록에 반영한다. 순수 함수 — 새 로그를 돌려준다.
     *
     * 완료(4/4)면 true 로 남기고 **true 는 절대 덮어쓰지 않는다**(리셋 전에 다시 열어도 안 뒤집힘).
     *
     * 미완은 **하루의 늦은 시간대([LATE_HOUR] 이후)에 본 것만** false 로 남긴다.
     * 예전엔 그날 첫 관측이면 시각과 무관하게 false 를 박았는데, 아침에 앱을 열어 0/4 가 찍히고
     * 저녁에 다 하고서 앱을 다시 안 열면 그날이 영영 미완주로 굳었다 — **앱을 일찍 열수록
     * 완주율이 떨어지는** 지표였다. 이른 관측은 아무것도 안 남기고, 그 상태로 하루가 끝나면
     * 그날은 '판단 근거 없음'으로 분모에서 빠진다(안 켠 날을 빼는 기존 원칙과 같다).
     */
    fun record(log: GameTaskLog, note: LiveNote, nowMillis: Long = currentTimeMillis()): GameTaskLog {
        var daily = log.daily
        var weekly = log.weekly
        val late = DateUtil.gameDayHour(nowMillis) >= LATE_HOUR

        if (note.maxDailyTaskCount > 0) {
            val key = DateUtil.gameDayKey(nowMillis)
            val done = note.dailyTaskCount >= note.maxDailyTaskCount
            if (done) daily = daily + (key to true)
            else if (late && daily[key] != true) daily = daily + (key to false)
        }
        // 주간은 리셋까지 남은 시간이 길어 '이른 관측' 문제가 하루 단위만큼 크지 않다. 다만 같은 이유로
        // 주 초반의 미완을 실패로 적으면 안 되므로, **주간도 마지막 날의 늦은 시간대에만** 미완을 남긴다.
        if (note.weeklyTotal > 0) {
            val key = DateUtil.gameWeekKey(nowMillis)
            val done = note.weeklyDone >= note.weeklyTotal
            if (done) weekly = weekly + (key to true)
            else if (late && DateUtil.gameWeekKey(nowMillis + 86_400_000L) != key && weekly[key] != true) {
                weekly = weekly + (key to false)
            }
        }
        return prune(GameTaskLog(daily, weekly), nowMillis)
    }

    /**
     * 받아온 노트들을 저장소의 기록에 반영한다 — **포그라운드·백그라운드 공용 진입점.**
     *
     * 예전엔 이 일을 `SpendingViewModel` 만 했다. 그래서 관측이 "앱을 연 순간" 에만 생겼고,
     * 백그라운드 워커는 같은 노트를 받아 캐시에까지 넣으면서도 숙제 기록은 남기지 않았다.
     * 관측이 드물면 완주율은 그만큼 근거가 얇아진다.
     */
    fun recordAll(
        repo: GatchaRepository,
        notes: List<LiveNote>,
        nowMillis: Long = currentTimeMillis(),
    ): Map<String, GameTaskLog>? {
        if (notes.isEmpty()) return null
        val logs = repo.loadTaskLogs().toMutableMap()
        var changed = false
        notes.forEach { n ->
            val key = GameData.byNameOrNull(n.game)?.key ?: return@forEach
            val before = logs[key] ?: GameTaskLog()
            val after = record(before, n, nowMillis)
            if (after != before) { logs[key] = after; changed = true }
        }
        if (!changed) return null
        repo.saveTaskLogs(logs)
        return logs
    }

    /** 보관 한도를 넘은 오래된 기록 제거. */
    fun prune(log: GameTaskLog, nowMillis: Long = currentTimeMillis()): GameTaskLog {
        val keepDays = (0 until KEEP_DAYS).map { DateUtil.gameDayKeyAgo(it, nowMillis) }.toSet()
        val keepWeeks = (0 until KEEP_WEEKS).map { DateUtil.gameWeekKeyAgo(it, nowMillis) }.toSet()
        return GameTaskLog(
            daily = log.daily.filterKeys { it in keepDays },
            weekly = log.weekly.filterKeys { it in keepWeeks },
        )
    }

    /** 기록에서 완주율·스트릭을 파생. 게임 메타(이름·색)는 [GameData] 에서 채운다. */
    fun stats(gameKey: String, log: GameTaskLog, nowMillis: Long = currentTimeMillis()): TaskStats {
        val game = GameData.games.firstOrNull { it.key == gameKey }
        val todayKey = DateUtil.gameDayKey(nowMillis)
        val weekKey = DateUtil.gameWeekKey(nowMillis)

        // ── 최근 30일(기록 있는 날만 분모)
        val window = (0 until WINDOW_DAYS).map { DateUtil.gameDayKeyAgo(it, nowMillis) }
        val seen = window.mapNotNull { log.daily[it] }
        val rate = if (seen.isEmpty()) 0 else seen.count { it } * 100 / seen.size

        // ── 스트릭: 오늘이 아직 미완이면 어제부터 세어 '오늘 안 했다고 끊긴 것처럼' 보이지 않게 한다.
        val start = if (log.daily[todayKey] == true) 0 else 1
        var streak = 0
        var i = start
        while (i < KEEP_DAYS && log.daily[DateUtil.gameDayKeyAgo(i, nowMillis)] == true) { streak++; i++ }

        // ── 최고 스트릭: 기록 전체에서 연속 true 최대 길이(날짜 순서대로).
        var best = 0
        var run = 0
        var d = KEEP_DAYS - 1
        while (d >= 0) {
            if (log.daily[DateUtil.gameDayKeyAgo(d, nowMillis)] == true) { run++; if (run > best) best = run } else run = 0
            d--
        }

        // ── 최근 12주(기록 있는 주만 분모)
        val weeks = (0 until WEEK_WINDOW).map { DateUtil.gameWeekKeyAgo(it, nowMillis) }
        val seenWeeks = weeks.mapNotNull { log.weekly[it] }
        val weeklyRate = if (seenWeeks.isEmpty()) 0 else seenWeeks.count { it } * 100 / seenWeeks.size

        return TaskStats(
            gameKey = gameKey,
            gameShort = game?.shortName ?: gameKey,
            colorArgb = game?.color ?: 0xFF9AA0A6,
            todayDone = log.daily[todayKey] == true,
            weekDone = log.weekly[weekKey] == true,
            dailyRate = rate,
            dailyDays = seen.size,
            dailyStreak = streak,
            dailyBest = maxOf(best, streak),
            weeklyRate = weeklyRate,
            weeklyWeeks = seenWeeks.size,
        )
    }

    /** 연동된 게임들의 통계 — 기록이 하나도 없는 게임은 뺀다. */
    fun allStats(logs: Map<String, GameTaskLog>, nowMillis: Long = currentTimeMillis()): List<TaskStats> =
        GameData.games.mapNotNull { g ->
            val log = logs[g.key] ?: return@mapNotNull null
            if (log.daily.isEmpty() && log.weekly.isEmpty()) return@mapNotNull null
            stats(g.key, log, nowMillis)
        }
}
