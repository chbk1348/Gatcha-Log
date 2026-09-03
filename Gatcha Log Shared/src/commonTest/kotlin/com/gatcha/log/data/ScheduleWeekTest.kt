package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 일정 탭 2.0 주간 보드 — **빈 주도 남긴다** 를 테스트로 굳힌다.
 * 눈으로는 잘 안 보이는데 틀리면 화면이 조용히 거짓말을 한다.
 */
class ScheduleWeekTest {

    private val day = 86_400_000L
    /** 기준 시각은 고정값으로 둔다 — currentTimeMillis 에 기대면 주 경계에서 테스트가 흔들린다. */
    private val now = 1_800_000_000_000L

    private fun entry(target: Long, start: Boolean = false, kind: String = "이벤트", sub: String = "") =
        ScheduleEntry(
            gameKey = "genshin", gameShort = "원신", colorArgb = 0xFF4F8EF7L,
            kind = kind, title = "t", sub = sub, target = target, isStart = start,
        )

    // ---------------------------------------------------------------- 주 묶기

    @Test
    fun 요청한_주_수만큼_항상_돌려준다() {
        // 일정이 하나도 없어도 주 자체는 남는다 — "이번 주는 한가하다"도 정보다.
        val weeks = buildWeeks(emptyList(), now, weeks = 4)
        assertEquals(4, weeks.size)
        assertTrue(weeks.all { it.entries.isEmpty() })
    }

    @Test
    fun 각_주는_일곱_칸이다() {
        buildWeeks(emptyList(), now).forEach { assertEquals(7, it.days.size) }
    }

    @Test
    fun 첫_주_라벨은_이번_주_다음은_다음_주() {
        val weeks = buildWeeks(emptyList(), now, weeks = 3)
        assertEquals("이번 주", weeks[0].label)
        assertEquals("다음 주", weeks[1].label)
        assertTrue(weeks[2].label.endsWith("주"))
    }

    @Test
    fun 오늘은_첫_주_안에_있다() {
        val weeks = buildWeeks(emptyList(), now)
        assertEquals(1, weeks[0].days.count { it.isToday })
    }

    @Test
    fun 항목은_속한_주에_한_번만_들어간다() {
        // 주 수를 넉넉히 준다 — 기준일이 무슨 요일이냐에 따라 +9일이 2주 안에 안 들어올 수 있다.
        val a = entry(now + 2 * day)
        val b = entry(now + 9 * day)
        val weeks = buildWeeks(listOf(a, b), now, weeks = 4)
        val all = weeks.flatMap { it.entries }
        assertEquals(2, all.size)
        // 같은 항목이 두 주에 겹쳐 들어가지 않는다.
        assertEquals(all.size, all.distinct().size)
        // 각 항목은 자기 target 이 속한 주에만.
        weeks.forEach { w ->
            val end = w.startMillis + 7 * day
            assertTrue(w.entries.all { it.target in w.startMillis until end })
        }
    }

    @Test
    fun 지난_항목은_버린다() {
        val past = entry(now - 3 * day)
        val weeks = buildWeeks(listOf(past), now)
        assertTrue(weeks.flatMap { it.entries }.isEmpty())
    }

    @Test
    fun 그날_점은_게임색이고_최대_세개() {
        val t = now + day
        val many = listOf(
            entry(t).copy(colorArgb = 0x1L), entry(t).copy(colorArgb = 0x2L),
            entry(t).copy(colorArgb = 0x3L), entry(t).copy(colorArgb = 0x4L),
            entry(t).copy(colorArgb = 0x1L),   // 중복
        )
        val dots = buildWeeks(many, now)[0].days.first { it.dotColors.isNotEmpty() }.dotColors
        assertTrue(dots.size <= 3)
        assertEquals(dots.size, dots.distinct().size)
    }

    // ---------------------------------------------------------------- 표식

    @Test
    fun 시작과_마감이_갈린다() {
        assertEquals(ScheduleMark.END, entry(now + day).mark())
        assertEquals(ScheduleMark.START, entry(now + day, start = true).mark())
    }

    // ---------------------------------------------------------------- 시작 항목

    @Test
    fun 같은_날_시작하는_픽업은_한_줄로_묶는다() {
        // 페이즈마다 캐릭터가 둘씩이면 줄이 두 배가 되어 주간 목록이 넘친다.
        val s = now + 3 * day
        val banners = listOf(
            GachaBanner(game = "원신", name = "에스코피에", startMillis = s, endMillis = s + 20 * day, version = "6.6"),
            GachaBanner(game = "원신", name = "느비예트", startMillis = s, endMillis = s + 20 * day, version = "6.6"),
        )
        val out = buildStartEntries(banners, now)
        assertEquals(1, out.size)
        assertTrue(out[0].isStart)
        assertEquals(ScheduleMark.START, out[0].mark())
        assertTrue(out[0].sub.contains("에스코피에") && out[0].sub.contains("느비예트"))
    }

    @Test
    fun 이미_시작한_픽업은_시작_줄을_만들지_않는다() {
        val banners = listOf(
            GachaBanner(game = "원신", name = "진행중", startMillis = now - day, endMillis = now + 10 * day),
        )
        assertTrue(buildStartEntries(banners, now).isEmpty())
    }

    // ---------------------------------------------------------------- 픽업 캐릭터명

    @Test
    fun 픽업_줄에_캐릭터_이름이_붙는다() {
        val s0 = now + 3 * day
        val banners = listOf(
            GachaBanner(game = "원신", name = "에스코피에", startMillis = s0, endMillis = s0 + 20 * day, version = "6.6"),
            GachaBanner(game = "원신", name = "느비예트", startMillis = s0, endMillis = s0 + 20 * day, version = "6.6"),
        )
        // 시작 줄
        assertEquals("에스코피에 · 느비예트", buildStartEntries(banners, now)[0].sub)
        // 종료 줄 — 예전엔 비어 있어 "무엇이 끝나는지" 알 수 없었다.
        val end = ScheduleLogic.buildSchedule(banners, emptyList(), emptyList())
            .first { it.kind == "패치" && !it.isStart }
        assertTrue(end.sub.contains("에스코피에"), "종료 줄에 캐릭터명이 없다: ${end.sub}")
    }

    @Test
    fun 이름이_넷_이상이면_외_N_으로_접는다() {
        val picks = (1..5).map {
            GachaBanner(game = "원신", name = "캐릭터$it", startMillis = now, endMillis = now + day)
        }
        assertEquals("캐릭터1 · 캐릭터2 · 캐릭터3 외 2", pickupNames(picks))
    }

    @Test
    fun 이름이_중복이면_한_번만() {
        val picks = listOf(
            GachaBanner(game = "원신", name = "같은이름", startMillis = now, endMillis = now + day),
            GachaBanner(game = "원신", name = "같은이름", startMillis = now, endMillis = now + day),
        )
        assertEquals("같은이름", pickupNames(picks))
    }

    @Test
    fun 이름이_없으면_빈_문자열() {
        assertEquals("", pickupNames(emptyList()))
    }
}
