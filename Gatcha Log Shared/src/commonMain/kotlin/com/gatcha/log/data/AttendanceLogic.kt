package com.gatcha.log.data

/**
 * 출석 집계 — 상세 페이지가 읽는 숫자를 한곳에서 만든다.
 *
 * 두 플랫폼이 각자 세면 "이번 달 18일"과 "이번 달 17일"이 나란히 나올 수 있다. 특히 달 경계는
 * 시간대를 어떻게 잡느냐로 하루가 갈린다 — 출석은 베이징(UTC+8) 자정 기준이라 기기 시간대로
 * 세면 한국 새벽 1시에 어제 것이 오늘로 잡힌다.
 *
 * 그 함정을 피하려고 **날짜 계산을 아예 하지 않는다.** 기록 키가 이미 베이징 기준
 * "yyyy-MM-dd"([DateUtil.hoyoDayKey])이므로, 이번 달 판정은 앞 7글자("yyyy-MM") 비교로 끝난다.
 * 시간대도 달력도 개입할 자리가 없다.
 */
object AttendanceLogic {

    /**
     * 오늘 상태 + 이번 달 누계.
     *
     * @param history 날짜 키("yyyy-MM-dd") → 그날 출석한 게임 키 집합
     * @param today 오늘 출석을 마친 게임 키 집합
     * @param streak 연속 기록(일). 계산은 [SpendingViewModel] 이 하고 여기선 실어 나르기만 한다
     * @param todayKey 오늘 날짜 키. 테스트가 시각을 고정할 수 있도록 인자로 받는다
     * @param games 출석을 세는 게임([SpendingViewModel.trackedAttendanceGames]) — 안 하는 게임은 분모에서 뺀다
     */
    fun summary(
        history: Map<String, Set<String>>,
        today: Set<String>,
        streak: Int,
        todayKey: String = DateUtil.hoyoDayKey(),
        games: List<Game> = GameData.attendanceGames,
    ): AttendanceSummary {
        val monthPrefix = todayKey.take(7)
        val monthEntries = history.filterKeys { it.startsWith(monthPrefix) }
        val stats = games.map { game ->
            AttendanceGameStat(
                gameKey = game.key,
                gameShort = game.shortName,
                colorArgb = game.color,
                checkedToday = game.key in today,
                monthCount = monthEntries.count { (_, set) -> game.key in set },
            )
        }
        return AttendanceSummary(
            todayDone = stats.count { it.checkedToday },
            todayTotal = stats.size,
            streak = streak,
            // '기록이 있는 날'이 아니라 **전체 출석한 날**만 센다. 한 게임만 한 날을 출석일로
            // 세면 달력의 진한 원 개수와 숫자가 어긋나 보인다.
            monthFullDays = monthEntries.count { (_, set) -> dayLevel(set, games) == AttendanceDayLevel.FULL },
            // 분모는 이번 달 전체가 아니라 **오늘까지** — 아직 오지 않은 날을 못 지킨 날로 세면 안 된다.
            monthElapsedDays = todayKey.takeLast(2).toIntOrNull() ?: 0,
            games = stats,
        )
    }

    /**
     * 그날의 출석 정도 — **달력 · 7일 스트립 · 마이페이지 30칸 · 이번 달 누계가 전부 이 판정을 쓴다**(27.52.0).
     *
     * 예전엔 달력이 "그날 출석한 게임 **수**가 세는 게임 수 이상인가"로, 누계와 마이페이지가 "세는 게임이
     * **전부** 들어 있는가"로 따로 판정했다. 세는 게임이 바뀐 사용자(젠레스를 「내 게임」에서 뺌)의 지난 날짜가
     * 달력에서는 '전체', 같은 화면의 누계에서는 '일부'로 갈렸다.
     *
     * @param done 그날 출석한 게임 키
     * @param games 출석을 세는 게임([summary] 와 같은 목록)
     */
    fun dayLevel(done: Set<String>, games: List<Game>): AttendanceDayLevel = when {
        done.isEmpty() -> AttendanceDayLevel.NONE
        games.isNotEmpty() && games.all { it.key in done } -> AttendanceDayLevel.FULL
        else -> AttendanceDayLevel.PARTIAL
    }
}

/** 하루의 출석 정도 — 기록 없음 · 일부 · 세는 게임 전부. */
enum class AttendanceDayLevel { NONE, PARTIAL, FULL }

/** 출석 상세 페이지 한 화면분. */
data class AttendanceSummary(
    val todayDone: Int,
    val todayTotal: Int,
    val streak: Int,
    /** 이번 달 **전체 출석**을 마친 날 수. */
    val monthFullDays: Int,
    /** 이번 달 오늘까지 지난 날 수(= 오늘 일자). 비율의 분모. */
    val monthElapsedDays: Int,
    val games: List<AttendanceGameStat>,
) {
    /** 오늘 남은 게임 수. 0 이면 다 했다. */
    val pending: Int get() = todayTotal - todayDone
    val allDone: Boolean get() = pending == 0
}

/** 게임 하나의 출석 상태. */
data class AttendanceGameStat(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val checkedToday: Boolean,
    /** 이번 달 이 게임이 출석된 날 수. */
    val monthCount: Int,
)
