package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis

/**
 * 오늘 해야 할 일 한 건.
 *
 * @param kind 정렬·표시 분기용. "재화" | "일일" | "주간" | "출석"
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
)

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
 * 재화 넘침만 시각이 정확하다 — 상류가 '남은 초'를 주므로 [LiveNote.resinFullAtMillis] 는 실측값이다.
 */
object DailyLogic {

    /** 재화가 이 시간 안에 가득 차면 급한 것으로 본다. */
    const val RESIN_SOON_HOURS = 6

    private const val HOUR_MS = 60L * 60 * 1000

    /**
     * 오늘 할 일 — 급한 순.
     *
     * 정렬: 급함(urgent) 먼저 → 종류 우선순위(재화 > 일일 > 주간 > 출석) → 게임 정의 순.
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

            // ① 재화 — 유일하게 시각이 정확한 항목
            if (note != null && note.maxResin > 0) {
                val full = note.currentResin >= note.maxResin
                val fullAt = note.resinFullAtMillis
                val soon = !full && fullAt > 0 && fullAt - nowMillis <= RESIN_SOON_HOURS * HOUR_MS
                if (full || soon) {
                    out += DailyTask(
                        gameKey = game.key,
                        gameShort = game.shortName,
                        colorArgb = game.color,
                        kind = "재화",
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
        val kindOrder = listOf("재화", "일일", "주간", "출석")
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
     * 히어로에 올릴 한 건 — 목록의 첫 항목.
     *
     * 급한 게 없으면 **null**. 그때 화면은 히어로 대신 조용한 요약을 쓴다 —
     * 급하지 않은 일을 크게 띄우면 다음에 진짜 급할 때 그 자리가 안 읽힌다.
     */
    fun hero(tasks: List<DailyTask>): DailyTask? = tasks.firstOrNull { it.urgent }

    /** 오늘 남은 할 일 수 — 히어로가 없을 때 요약 문구에 쓴다. */
    fun remaining(tasks: List<DailyTask>): Int = tasks.size

    /**
     * 게임별 한 줄 요약 — 히어로 아래 현황 목록.
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
            resinRatio = note?.resinRatio ?: 0f,
            resinFull = note != null && note.maxResin > 0 && note.currentResin >= note.maxResin,
            pendingCount = mine.size,
            checkedIn = game.key in attendanceToday,
            hasNote = note != null && note.maxResin > 0,
        )
    }
}

/** 게임 하나의 오늘 상태 — 히어로 아래 한 줄. */
data class DailyGameSummary(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val resin: String,
    val resinRatio: Float,
    val resinFull: Boolean,
    val pendingCount: Int,
    val checkedIn: Boolean,
    val hasNote: Boolean,
)
