package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis

/**
 * 오늘 해야 할 일 한 건.
 *
 * @param kind 정렬·표시 분기용. "행동력" | "일일" | "주간" | "출석"
 * @param label 화면에 그대로 나가는 문구 — "레진 가득참" · "일일 의뢰 3/4"
 * @param dueMillis 마감 시각. **0 이면 마감을 모른다**(리셋 시각 미확정 항목).
 *   0 인 항목에 카운트다운을 그리면 안 된다.
 * @param urgent 지금 처리해야 하는가. 히어로로 올릴지 판단하는 기준
 * @param actionable 앱이 대신 해 줄 수 있는가. 출석만 true — 나머지는 게임에서 해야 한다
 */
data class DailyTask(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val kind: String,
    val label: String,
    val detail: String = "",
    val dueMillis: Long = 0L,
    val urgent: Boolean = false,
    val actionable: Boolean = false,
) {
    /** 게임별로 묶어 한 줄에 이어 붙일 때 쓰는 짧은 이름 — "출석 안 함" → "출석". */
    val shortLabel: String get() = when (kind) {
        "출석" -> "출석"
        "일일" -> "일일"
        "주간" -> "주간"
        else -> label
    }
}

/**
 * 데일리 섹션이 "지금 뭘 해야 하나"에 답하기 위한 판단.
 *
 * 화면은 그리기만 하고 규칙은 전부 여기 있다 — Compose·SwiftUI 가 각자 판단하면
 * 두 플랫폼이 서로 다른 우선순위를 보여준다.
 *
 * ## 지금 다루지 않는 것
 *
 * **일일·주간 리셋까지 남은 시간은 계산하지 않는다.** 게임·서버 지역마다 리셋 시각이
 * 다른데 그 값을 아직 실측하지 않았다. 추측해서 "4시간 뒤 리셋"을 띄우면 화면상 그럴듯해
 * 틀린 걸 알아채지 못한다(방송 역산이 하루 어긋난 채 나갔던 것과 같은 함정).
 * 리셋 시각이 확정되면 [DailyTask.dueMillis] 를 채우고 화면이 카운트다운을 그린다.
 *
 * 행동력 넘침만 시각이 정확하다 — 상류가 '남은 초'를 주므로 [LiveNote.resinFullAtMillis] 는 실측값이다.
 */
object DailyLogic {

    /** 행동력(레진·개척력·배터리)이 이 시간 안에 가득 차면 급한 것으로 본다. */
    const val RESIN_SOON_HOURS = 6

    private const val HOUR_MS = 60L * 60 * 1000

    /**
     * 오늘 할 일 — 급한 순.
     *
     * 정렬: 급함(urgent) 먼저 → 종류 우선순위(행동력 > 일일 > 주간 > 출석) → 게임 정의 순.
     * 종류 우선순위를 게임보다 앞에 두는 이유는, 사용자가 찾는 게 "어느 게임"이 아니라
     * "무엇을 해야 하나"이기 때문이다.
     */
    fun tasks(
        notes: List<LiveNote>,
        attendanceToday: Set<String>,
        nowMillis: Long = currentTimeMillis(),
    ): List<DailyTask> {
        val out = mutableListOf<DailyTask>()
        for (game in GameData.attendanceGames) {
            val note = notes.firstOrNull { GameData.byNameOrNull(it.game)?.key == game.key }

            // ① 행동력 — 유일하게 시각이 정확한 항목
            if (note != null && note.maxResin > 0) {
                val full = note.currentResin >= note.maxResin
                val fullAt = note.resinFullAtMillis
                val soon = !full && fullAt > 0 && fullAt - nowMillis <= RESIN_SOON_HOURS * HOUR_MS
                if (full || soon) {
                    out += DailyTask(
                        gameKey = game.key,
                        gameShort = game.shortName,
                        colorArgb = game.color,
                        kind = "행동력",
                        label = if (full) "${note.resinLabel} 가득참" else "${note.resinLabel} 곧 가득",
                        detail = "${note.currentResin}/${note.maxResin}",
                        dueMillis = if (full) 0L else fullAt,
                        urgent = full,
                    )
                }
            }

            // ② 일일 숙제 — 남은 개수는 정확하지만 **언제까지인지는 모른다**(리셋 미확정)
            if (note != null && note.maxDailyTaskCount > 0 && note.dailyTaskCount < note.maxDailyTaskCount) {
                out += DailyTask(
                    gameKey = game.key,
                    gameShort = game.shortName,
                    colorArgb = game.color,
                    kind = "일일",
                    label = "일일 임무",
                    detail = "${note.dailyTaskCount}/${note.maxDailyTaskCount}",
                )
            }

            // ③ 주간 숙제 — weeklyTotal 이 0 인 게임은 주간 데이터를 안 준다(LiveNote 주석)
            if (note != null && note.weeklyTotal > 0 && note.weeklyDone < note.weeklyTotal) {
                out += DailyTask(
                    gameKey = game.key,
                    gameShort = game.shortName,
                    colorArgb = game.color,
                    kind = "주간",
                    label = "주간 숙제",
                    detail = "${note.weeklyDone}/${note.weeklyTotal}",
                )
            }

            // ④ 출석 — 앱이 대신 할 수 있는 유일한 항목
            if (game.key !in attendanceToday) {
                out += DailyTask(
                    gameKey = game.key,
                    gameShort = game.shortName,
                    colorArgb = game.color,
                    kind = "출석",
                    label = "출석 안 함",
                    actionable = true,
                )
            }
        }
        val kindOrder = listOf("행동력", "일일", "주간", "출석")
        val gameOrder = GameData.attendanceGames.map { it.key }
        return out.sortedWith(
            compareBy(
                { !it.urgent },
                { kindOrder.indexOf(it.kind).takeIf { i -> i >= 0 } ?: kindOrder.size },
                { gameOrder.indexOf(it.gameKey).takeIf { i -> i >= 0 } ?: gameOrder.size },
            ),
        )
    }

    /**
     * 히어로 문구 — **게임 중립**.
     *
     * 예전엔 가장 급한 한 건(주로 원신 레진)을 히어로에 크게 올렸는데, 데일리는 3게임을
     * 함께 관리하는 화면이라 한 게임이 대표로 뜨면 편향돼 보인다. 지출 상세는 지출 한 건의
     * 상세라 그 게임이 히어로에 오는 게 자연스러웠지만 여기는 다르다.
     *
     * 그래서 히어로는 "오늘 전체"만 말하고, 어느 게임의 무엇인지는 아래 목록이 맡는다.
     */
    fun headline(tasks: List<DailyTask>): DailyHeadline {
        val urgent = tasks.count { it.urgent }
        return when {
            urgent > 0 -> DailyHeadline(
                title = if (urgent == 1) "지금 해야 할 일이 있어요" else "지금 해야 할 일 ${urgent}건",
                // 급한 게 무엇인지는 짧게만 — 자세한 건 바로 아래 목록에 있다.
                subtitle = tasks.filter { it.urgent }.joinToString(" · ") { "${it.gameShort} ${it.label}" },
                urgent = true,
            )
            tasks.isNotEmpty() -> DailyHeadline(
                title = "할 일 ${tasks.size}건 남았어요",
                subtitle = "급한 건 없어요 — 아래에서 하나씩",
                urgent = false,
            )
            else -> DailyHeadline(
                title = "오늘 할 일 끝났어요",
                subtitle = "행동력도 넉넉하고 출석도 다 했어요",
                urgent = false,
            )
        }
    }


    /**
     * 할 일을 **게임별 한 줄**로 묶는다.
     *
     * 항목을 낱개로 늘어놓으면 3게임 × 최대 4종이라 목록이 금세 열 줄을 넘는다.
     * 게임당 한 줄로 묶으면 세 줄로 끝나고, 어느 게임에 뭐가 남았는지가 한눈에 들어온다.
     *
     * **행동력은 뺀다.** 행동력 카드가 이미 3게임을 나란히 보여주고 있어 목록에 또 쓰면 중복이다.
     * 넘침 경고도 그 카드가 색으로 한다.
     */
    fun byGame(tasks: List<DailyTask>, stats: List<TaskStats> = emptyList()): List<DailyGameTasks> {
        val kept = tasks.filter { it.kind != "행동력" }
        return GameData.attendanceGames.mapNotNull { game ->
            val mine = kept.filter { it.gameKey == game.key }
            if (mine.isEmpty()) return@mapNotNull null
            val stat = stats.firstOrNull { it.gameKey == game.key }
            DailyGameTasks(
                gameKey = game.key,
                gameShort = game.shortName,
                colorArgb = game.color,
                // "일일 3/4 · 출석" — 상세가 있으면 붙이고 없으면 라벨만.
                summary = mine.joinToString(" · ") { t ->
                    if (t.detail.isBlank()) t.shortLabel else "${t.shortLabel} ${t.detail}"
                },
                canCheckIn = mine.any { it.actionable },
                // 완주율은 **기록이 있는 날이 있을 때만** 말한다. 분모가 0 이면 판단할 근거가 없다.
                rate = stat?.takeIf { it.dailyDays > 0 }?.dailyRate ?: -1,
                rateDays = stat?.dailyDays ?: 0,
            )
        }
    }

    /**
     * 게임별 한 줄 요약 — 행동력 카드용.
     *
     * 할 일이 없는 게임도 한 줄은 남긴다(빠지면 "왜 없지?"를 확인하러 들어가야 한다).
     * 대신 문구를 짧게 줄여 무게를 낮춘다.
     */
    fun summaries(
        notes: List<LiveNote>,
        attendanceToday: Set<String>,
        tasks: List<DailyTask>,
    ): List<DailyGameSummary> = GameData.attendanceGames.map { game ->
        val note = notes.firstOrNull { GameData.byNameOrNull(it.game)?.key == game.key }
        val mine = tasks.filter { it.gameKey == game.key }
        DailyGameSummary(
            gameKey = game.key,
            gameShort = game.shortName,
            colorArgb = game.color,
            resin = if (note != null && note.maxResin > 0) "${note.resinLabel} ${note.currentResin}/${note.maxResin}" else "",
            resinValue = if (note != null && note.maxResin > 0) "${note.currentResin}/${note.maxResin}" else "",
            resinLabel = resinLabelOf(game),
            resinRecovery = note?.resinRecoveryTime.orEmpty(),
            resinRatio = note?.resinRatio ?: 0f,
            resinFull = note != null && note.maxResin > 0 && note.currentResin >= note.maxResin,
            pendingCount = mine.size,
            checkedIn = game.key in attendanceToday,
            hasNote = note != null && note.maxResin > 0,
        )
    }

    /**
     * **모든** 게임의 행동력이 가득인지 — 행동력 카드의 비상 표시 조건.
     *
     * 노트를 못 받은 게임이 하나라도 있으면 false 다. 그 게임이 가득인지 알 수 없는데
     * "모두 가득"이라고 말하면 앱이 확인되지 않은 사실을 주장하게 된다.
     * (연동을 안 한 게임이 있으면 이 표시는 뜨지 않는다 — 의도된 동작이다.)
     */
    fun allResinFull(summaries: List<DailyGameSummary>): Boolean =
        summaries.isNotEmpty() && summaries.all { it.hasNote && it.resinFull }
}

/** 게임 하나에 남은 할 일 — 한 줄로 묶은 것. */
data class DailyGameTasks(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    /** "일일 3/4 · 출석" */
    val summary: String,
    /** 이 줄에 출석 버튼을 달지. */
    val canCheckIn: Boolean,
    /** 최근 일일 숙제 완주율(0~100). **-1 이면 표시하지 않는다**(기록이 없다). */
    val rate: Int = -1,
    /** 완주율의 분모 = 기록이 있는 날 수. */
    val rateDays: Int = 0,
)

/**
 * 히어로 문구 — 게임에 치우치지 않는다.
 *
 * @param urgent 급한 일이 있는가. 화면이 막대·글자 색을 이걸로 정한다
 */
data class DailyHeadline(
    val title: String,
    val subtitle: String,
    val urgent: Boolean,
)

/** 게임 하나의 오늘 상태 — 행동력 카드 한 칸. */
data class DailyGameSummary(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    /** "레진 152/160" — 행동력 이름 포함. */
    val resin: String,
    /** "152/160" — 이름 없이 숫자만(칸이 좁은 카드용). */
    val resinValue: String,
    /** "레진"·"개척력"·"배터리" — 인게임 명칭. 노트가 없어도 채운다([resinLabelOf]). */
    val resinLabel: String,
    /** 상류가 주는 회복 안내("14시간 30분 후" 등). 없으면 빈 문자열. */
    val resinRecovery: String,
    val resinRatio: Float,
    val resinFull: Boolean,
    val pendingCount: Int,
    val checkedIn: Boolean,
    val hasNote: Boolean,
)
