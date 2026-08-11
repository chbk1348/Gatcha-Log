package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 간트 타임라인 — [TimelineLogic].
 *
 * 좌표가 틀려도 앱은 안 죽는다. 막대가 하루 밀리거나 창 밖으로 새도 "대충 저쯤"으로 읽히고
 * 넘어간다 — 그래서 화면으로는 못 잡는다. 규칙을 여기 고정한다.
 */
class TimelineLogicTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val gi = Game.GENSHIN

    private fun banner(name: String, startDays: Int, endDays: Int, version: String = "6.7", game: Game = gi) =
        GachaBanner(
            game = game.displayName, name = name, version = version,
            startMillis = if (startDays == Int.MIN_VALUE) 0L else now + startDays * day,
            endMillis = if (endDays == Int.MIN_VALUE) 0L else now + endDays * day,
        )

    private fun build(banners: List<GachaBanner>, events: List<GameEvent> = emptyList()) =
        TimelineLogic.build(
            ScheduleLogic.buildSchedule(banners, events, emptyList()),
            banners,
            now,
        )

    @Test
    fun `막대는 픽업 기간을 그대로 옮긴다`() {
        // 창은 어제(-1일)부터 시작한다 → 오늘 시작해 10일 뒤 끝나는 픽업은 창의 앞쪽 일부.
        val t = build(listOf(banner("콜롬비나", 0, 10)))
        val bar = t.rows.single().bars.single()
        assertTrue(bar.startFraction < bar.endFraction)
        // 시작(오늘)은 창 시작(어제)에서 하루 뒤 = 1/창길이
        assertEquals(1f / t.days, bar.startFraction, 0.01f)
        assertEquals(11f / t.days, bar.endFraction, 0.01f)
    }

    @Test
    fun `지금 진행 중인 픽업만 ongoing 이다`() {
        val t = build(listOf(banner("지금", -3, 5), banner("나중", 10, 20, version = "6.8")))
        val bars = t.rows.single().bars.associateBy { it.title }
        assertTrue(bars.values.any { it.ongoing }, "진행 중인 막대가 하나는 있어야 한다")
        assertTrue(bars.values.any { !it.ongoing }, "아직 시작 안 한 막대는 ongoing 이 아니다")
    }

    @Test
    fun `창보다 먼저 시작한 픽업은 왼쪽이 잘린다`() {
        val t = build(listOf(banner("오래전", -20, 5)))
        val bar = t.rows.single().bars.single()
        assertEquals(0f, bar.startFraction)
        assertTrue(bar.startClipped, "잘렸다는 표시가 없으면 '어제 시작한 것'과 구분되지 않는다")
        assertTrue(!bar.startUnknown, "시작 시각은 알고 있다 — 창 밖일 뿐이다")
    }

    @Test
    fun `시작 시각을 모르는 것과 잘린 것은 다르다`() {
        // 상류가 start_time 을 안 주면 0 으로 온다. 창 끝에 붙여 그리되 '모른다'로 표시한다.
        val t = build(listOf(banner("미상", Int.MIN_VALUE, 5)))
        val bar = t.rows.single().bars.single()
        assertTrue(bar.startUnknown)
        assertTrue(!bar.startClipped)
    }

    @Test
    fun `종료 미정 픽업은 창 끝까지 끌고 간다`() {
        // 스타레일 × Fate 콜라보가 실제로 이랬다 — 시작만 공지되고 종료가 미공지.
        val undated = banner("토오사카 린", -2, Int.MIN_VALUE)
        val t = build(listOf(undated, banner("일반", 0, 8)))
        val bar = t.rows.single().bars.single { it.endUnknown }
        assertEquals(1f, bar.endFraction, "끝을 모르는 것을 오늘 끝난 것처럼 그리면 안 된다")
        assertTrue(bar.ongoing)
    }

    @Test
    fun `이벤트는 막대가 아니라 마감 표식이다`() {
        // 상류가 종료 시각만 준다 — 없는 시작을 지어내 막대를 그리면 기간이 전부 거짓이 된다.
        val t = build(
            listOf(banner("픽업", 0, 10)),
            listOf(GameEvent(gi.displayName, "이벤트", now + 4 * day)),
        )
        val row = t.rows.single()
        assertEquals(1, row.bars.size)
        assertEquals(listOf("이벤트"), row.marks.map { it.label })
        assertEquals(5f / t.days, row.marks.single().fraction, 0.01f)
    }

    @Test
    fun `일정이 없는 게임은 행을 만들지 않는다`() {
        val t = build(listOf(banner("원신것", 0, 5)))
        assertEquals(listOf(gi.key), t.rows.map { it.gameKey })
    }

    @Test
    fun `창은 최소 길이를 지킨다`() {
        // 내일 끝나는 것 하나뿐이어도 이틀짜리 창을 그리지 않는다 — 눈금이 의미를 잃는다.
        val t = build(listOf(banner("내일", 0, 1)))
        assertTrue(t.days >= 14, "창이 ${t.days}일")
    }

    @Test
    fun `창은 최대 길이를 넘지 않는다`() {
        // 반년 뒤 일정 하나 때문에 이번 주가 1px 로 눌리면 안 된다.
        val t = build(listOf(banner("먼미래", 0, 200)))
        assertTrue(t.days <= 60, "창이 ${t.days}일")
        // 창을 넘는 막대는 오른쪽 끝에서 멈춘다(비율이 1을 넘어 화면 밖으로 새면 안 된다).
        assertEquals(1f, t.rows.single().bars.single().endFraction)
    }

    @Test
    fun `오늘 선은 창 안에 있다`() {
        val t = build(listOf(banner("A", 0, 10)))
        assertTrue(t.nowFraction > 0f && t.nowFraction < 1f)
    }

    @Test
    fun `눈금은 창이 길어져도 몇 개로 유지된다`() {
        // 폭은 그대로인데 눈금만 늘면 라벨이 서로 붙는다.
        val short = build(listOf(banner("A", 0, 10)))
        val long = build(listOf(banner("A", 0, 200)))
        assertTrue(short.ticks.size in 4..8, "짧은 창 ${short.ticks.size}개")
        assertTrue(long.ticks.size in 4..8, "긴 창 ${long.ticks.size}개")
    }

    @Test
    fun `일정이 하나도 없으면 빈 타임라인이다`() {
        val t = build(emptyList())
        assertTrue(t.isEmpty)
    }

    @Test
    fun `게임이 여럿이면 행도 여럿이다`() {
        val t = build(listOf(banner("원신것", 0, 5), banner("스레것", 0, 7, game = Game.HSR)))
        assertEquals(2, t.rows.size)
        // 행 순서는 GameData 정의 순 — 새로고침할 때마다 순서가 바뀌면 안 된다.
        assertEquals(GameData.games.filter { it.key in t.rows.map { r -> r.gameKey } }.map { it.key },
            t.rows.map { it.gameKey })
    }
}
