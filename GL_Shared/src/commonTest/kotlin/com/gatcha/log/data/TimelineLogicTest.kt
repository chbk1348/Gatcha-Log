package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 간트 타임라인 — [TimelineLogic]. **이벤트 하나가 한 행**, 게임별 그룹.
 *
 * 좌표가 틀려도 앱은 안 죽는다. 막대가 하루 밀리거나 창 밖으로 새도 "대충 저쯤"으로 읽히고
 * 넘어간다 — 그래서 화면으로는 못 잡는다. 규칙을 여기 고정한다.
 */
class TimelineLogicTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val gi = Game.GENSHIN

    /** [startDays] 를 [UNKNOWN] 으로 주면 상류가 시작을 안 준 경우(옛 캐시). */
    private fun ev(name: String, startDays: Int, endDays: Int, game: Game = gi, reward: String = "") =
        GameEvent(
            game = game.displayName, name = name,
            endMillis = now + endDays * day, reward = reward,
            startMillis = if (startDays == UNKNOWN) 0L else now + startDays * day,
        )

    private fun ch(name: String, startDays: Int, endDays: Int, game: Game = gi) =
        GameChallenge(
            game = game.displayName, name = name, typeName = "정기",
            endMillis = now + endDays * day,
            startMillis = if (startDays == UNKNOWN) 0L else now + startDays * day,
        )

    private fun build(events: List<GameEvent> = emptyList(), challenges: List<GameChallenge> = emptyList()) =
        TimelineLogic.build(events, challenges, now)

    private companion object { const val UNKNOWN = Int.MIN_VALUE }

    @Test
    fun `막대는 이벤트 기간을 그대로 옮긴다`() {
        // 창은 어제(-1일)부터 → 오늘 시작해 10일 뒤 끝나는 이벤트는 창의 앞쪽 일부.
        val t = build(listOf(ev("설원 탐험", 0, 10)))
        val row = t.groups.single().rows.single()
        assertTrue(row.startFraction < row.endFraction)
        assertEquals(1f / t.days, row.startFraction, 0.01f)
        assertEquals(11f / t.days, row.endFraction, 0.01f)
    }

    @Test
    fun `이벤트마다 한 행이다`() {
        // 예전엔 게임이 행이라 이벤트 셋이 한 줄에 겹쳐 들어갔다 — 각자의 기간을 읽을 수 없었다.
        val t = build(listOf(ev("A", 0, 5), ev("B", 2, 9), ev("C", 4, 6)))
        val g = t.groups.single()
        assertEquals(3, g.rows.size)
        assertEquals(listOf("A", "B", "C"), g.rows.map { it.title }, "행은 시작이 이른 순")
    }

    @Test
    fun `정기 콘텐츠도 같은 그룹에 행으로 들어간다`() {
        val t = build(listOf(ev("이벤트", 0, 5)), listOf(ch("나선 비경", 1, 8)))
        val g = t.groups.single()
        assertEquals(listOf(TimelineLogic.KIND_EVENT, TimelineLogic.KIND_CHALLENGE), g.rows.map { it.kind })
    }

    @Test
    fun `게임별로 묶고 그룹 순서는 정의 순이다`() {
        // 새로고침할 때마다 그룹 순서가 바뀌면 안 된다.
        val t = build(listOf(ev("스레것", 0, 7, game = Game.HSR), ev("원신것", 0, 5)))
        assertEquals(listOf(gi.key, Game.HSR.key), t.groups.map { it.gameKey })
        assertEquals(1, t.groups.first().rows.size)
    }

    @Test
    fun `지금 진행 중인 것만 ongoing 이다`() {
        val t = build(listOf(ev("지금", -3, 5), ev("나중", 10, 20)))
        val rows = t.groups.single().rows.associateBy { it.title }
        assertTrue(rows.getValue("지금").ongoing)
        assertTrue(!rows.getValue("나중").ongoing, "아직 시작 안 한 것은 ongoing 이 아니다")
    }

    @Test
    fun `창보다 먼저 시작한 이벤트는 왼쪽이 잘린다`() {
        val t = build(listOf(ev("오래전", -20, 5)))
        val row = t.groups.single().rows.single()
        assertEquals(0f, row.startFraction)
        assertTrue(row.startClipped, "잘렸다는 표시가 없으면 '어제 시작한 것'과 구분되지 않는다")
        assertTrue(!row.startUnknown, "시작 시각은 알고 있다 — 창 밖일 뿐이다")
    }

    @Test
    fun `시작을 모르는 것과 잘린 것은 다르다`() {
        // 옛 캐시엔 startMillis 가 없어 0 으로 온다. 왼쪽 끝에 붙이되 '모른다'로 표시한다.
        val t = build(listOf(ev("미상", UNKNOWN, 5)))
        val row = t.groups.single().rows.single()
        assertTrue(row.startUnknown)
        assertTrue(!row.startClipped)
        assertTrue(!row.ongoing, "시작을 모르면 진행 중이라고 단정할 수 없다")
    }

    @Test
    fun `이미 끝난 것은 싣지 않는다`() {
        val t = build(listOf(ev("어제 끝", -10, -2), ev("진행 중", -1, 5)))
        assertEquals(listOf("진행 중"), t.groups.single().rows.map { it.title })
    }

    @Test
    fun `창은 최소 길이를 지킨다`() {
        val t = build(listOf(ev("내일", 0, 1)))
        assertTrue(t.days >= 14, "창이 ${t.days}일")
    }

    @Test
    fun `창은 최대 길이를 넘지 않는다`() {
        val t = build(listOf(ev("먼미래", 0, 200)))
        assertTrue(t.days <= 60, "창이 ${t.days}일")
        // 창을 넘는 막대는 오른쪽 끝에서 멈춘다(비율이 1을 넘어 화면 밖으로 새면 안 된다).
        assertEquals(1f, t.groups.single().rows.single().endFraction)
    }

    @Test
    fun `오늘 선은 창 안에 있다`() {
        val t = build(listOf(ev("A", 0, 10)))
        assertTrue(t.nowFraction > 0f && t.nowFraction < 1f)
    }

    @Test
    fun `눈금은 창이 길어져도 몇 개로 유지된다`() {
        val short = build(listOf(ev("A", 0, 10)))
        val long = build(listOf(ev("A", 0, 200)))
        assertTrue(short.ticks.size in 4..8, "짧은 창 ${short.ticks.size}개")
        assertTrue(long.ticks.size in 4..8, "긴 창 ${long.ticks.size}개")
    }

    @Test
    fun `일정이 하나도 없으면 빈 타임라인이다`() {
        val t = build()
        assertTrue(t.isEmpty)
        assertEquals(0, t.rowCount)
    }

    @Test
    fun `행 수를 합쳐서 센다`() {
        val t = build(listOf(ev("A", 0, 5), ev("B", 0, 6, game = Game.HSR)), listOf(ch("C", 0, 7)))
        assertEquals(3, t.rowCount)
        assertEquals(2, t.groups.size)
    }
}
