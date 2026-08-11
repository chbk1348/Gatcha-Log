package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 데일리 우선순위 — [DailyLogic].
 *
 * 이 순서가 곧 화면 순서다. 틀려도 앱은 멀쩡히 돌고 "왠지 순서가 이상한" 느낌만 남아
 * 버그로 신고되지 않는다. 규칙을 여기 고정한다.
 */
class DailyLogicTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun note(
        game: Game,
        cur: Int = 50, max: Int = 160, fullAt: Long = now + 20 * hour,
        daily: Int = 4, dailyMax: Int = 4,
        weekly: Int = 3, weeklyTotal: Int = 3,
    ) = LiveNote(
        game = game.displayName,
        currentResin = cur, maxResin = max, resinFullAtMillis = fullAt,
        dailyTaskCount = daily, maxDailyTaskCount = dailyMax,
        weeklyDone = weekly, weeklyTotal = weeklyTotal,
    )

    /** 모든 게임 출석 완료 = 출석 항목이 안 생긴다. */
    private val allChecked = GameData.attendanceGames.map { it.key }.toSet()

    @Test
    fun `재화가 가득 차면 급한 일이다`() {
        val t = DailyLogic.tasks(listOf(note(Game.GENSHIN, cur = 160, max = 160)), allChecked, now)
        val resin = t.single { it.kind == "행동력" }
        assertTrue(resin.urgent)
        assertEquals("레진 가득참", resin.label)
    }

    @Test
    fun `가득 차기 6시간 전부터 목록에 올라온다`() {
        val soon = DailyLogic.tasks(listOf(note(Game.GENSHIN, cur = 150, fullAt = now + 5 * hour)), allChecked, now)
        assertTrue(soon.any { it.kind == "행동력" })
        // 아직 여유가 있으면 목록에 없다 — 매일 보는 화면에 안 급한 걸 쌓지 않는다.
        val later = DailyLogic.tasks(listOf(note(Game.GENSHIN, cur = 100, fullAt = now + 9 * hour)), allChecked, now)
        assertTrue(later.none { it.kind == "행동력" })
    }

    @Test
    fun `곧 가득은 급한 게 아니다`() {
        // '급함'은 히어로로 올릴지의 기준이다. 아직 안 넘쳤으면 크게 띄우지 않는다.
        val t = DailyLogic.tasks(listOf(note(Game.GENSHIN, cur = 150, fullAt = now + 3 * hour)), allChecked, now)
        assertTrue(!t.single { it.kind == "행동력" }.urgent)
    }

    @Test
    fun `리셋 시각을 모르는 항목은 마감을 0 으로 둔다`() {
        // 일일·주간은 리셋 시각이 미확정이다. 0 을 줘서 화면이 카운트다운을 못 그리게 한다 —
        // 추측한 시각을 띄우면 틀린 걸 알아채지 못한다.
        val t = DailyLogic.tasks(listOf(note(Game.GENSHIN, daily = 2, weekly = 1)), allChecked, now)
        assertEquals(0L, t.single { it.kind == "일일" }.dueMillis)
        assertEquals(0L, t.single { it.kind == "주간" }.dueMillis)
    }

    @Test
    fun `급한 일이 종류 우선순위보다 앞선다`() {
        // 젠레스 재화가 넘쳤고 원신은 일일이 남았다 — 게임 순서(원신 먼저)보다 급함이 우선이다.
        val notes = listOf(
            note(Game.GENSHIN, cur = 10, daily = 1),
            note(Game.ZZZ, cur = 240, max = 240),
        )
        val t = DailyLogic.tasks(notes, allChecked, now)
        assertEquals(Game.ZZZ.key, t.first().gameKey)
        assertTrue(t.first().urgent)
    }

    @Test
    fun `같은 급함 안에서는 종류 순서를 따른다`() {
        // 재화 > 일일 > 주간 > 출석. 게임이 아니라 '무엇을 하나'가 먼저다.
        val t = DailyLogic.tasks(
            listOf(note(Game.GENSHIN, cur = 155, fullAt = now + 2 * hour, daily = 1, weekly = 0)),
            emptySet(), now,
        )
        val mine = t.filter { it.gameKey == Game.GENSHIN.key }.map { it.kind }
        assertEquals(listOf("행동력", "일일", "주간", "출석"), mine)
    }

    @Test
    fun `출석만 앱이 대신 할 수 있다`() {
        val t = DailyLogic.tasks(listOf(note(Game.GENSHIN, cur = 160, max = 160, daily = 1)), emptySet(), now)
        assertTrue(t.single { it.kind == "출석" && it.gameKey == Game.GENSHIN.key }.actionable)
        assertTrue(t.none { it.kind != "출석" && it.actionable }, "게임에서 해야 하는 일에 버튼을 달면 안 된다")
    }

    @Test
    fun `주간 데이터를 안 주는 게임은 주간 항목이 없다`() {
        // 젠레스는 weeklyTotal 이 0 으로 온다(LiveNote 주석).
        val t = DailyLogic.tasks(listOf(note(Game.ZZZ, weekly = 0, weeklyTotal = 0)), allChecked, now)
        assertTrue(t.none { it.kind == "주간" })
    }

    @Test
    fun `히어로 문구는 게임에 치우치지 않는다`() {
        // 데일리는 3게임을 함께 관리하는 화면이다. 한 게임이 제목을 차지하면 편향돼 보인다.
        val t = DailyLogic.tasks(listOf(note(Game.GENSHIN, cur = 160, max = 160)), allChecked, now)
        val h = DailyLogic.headline(t)
        assertTrue(h.urgent)
        assertTrue(!h.title.contains("원신"), "제목에 게임명이 들어가면 안 된다: ${h.title}")
        // 어느 게임의 무엇인지는 부제로만 짧게 — 자세한 건 아래 목록이 맡는다.
        assertTrue(h.subtitle.contains("원신"))
    }

    @Test
    fun `급한 게 여럿이면 개수를 말한다`() {
        val notes = listOf(
            note(Game.GENSHIN, cur = 160, max = 160),
            note(Game.HSR, cur = 300, max = 300),
        )
        val h = DailyLogic.headline(DailyLogic.tasks(notes, allChecked, now))
        assertTrue(h.title.contains("2"), h.title)
    }

    @Test
    fun `급한 게 없으면 남은 개수를 말한다`() {
        val t = DailyLogic.tasks(listOf(note(Game.GENSHIN, daily = 1)), allChecked, now)
        val h = DailyLogic.headline(t)
        assertTrue(!h.urgent)
        assertTrue(h.title.contains("${t.size}"), h.title)
    }

    @Test
    fun `할 일이 하나도 없으면 끝났다고 말한다`() {
        val h = DailyLogic.headline(emptyList())
        assertTrue(!h.urgent)
        assertEquals("오늘 할 일 끝났어요", h.title)
    }

    @Test
    fun `할 일 없는 게임도 요약 줄은 남는다`() {
        // 줄이 빠지면 "왜 없지?"를 확인하러 들어가야 한다.
        val s = DailyLogic.summaries(emptyList(), allChecked, emptyList())
        assertEquals(GameData.attendanceGames.size, s.size)
        assertTrue(s.all { it.pendingCount == 0 })
    }

    @Test
    fun `요약은 게임별 남은 개수를 센다`() {
        val notes = listOf(note(Game.GENSHIN, cur = 160, max = 160, daily = 2))
        val t = DailyLogic.tasks(notes, emptySet(), now)
        val gi = DailyLogic.summaries(notes, emptySet(), t).single { it.gameKey == Game.GENSHIN.key }
        // 재화 넘침 + 일일 + 출석 = 3
        assertEquals(3, gi.pendingCount)
        assertTrue(gi.resinFull)
    }
}
