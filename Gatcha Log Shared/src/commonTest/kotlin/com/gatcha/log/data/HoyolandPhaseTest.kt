package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 행사 단계가 **실제 날짜에 맞춰** 갈리는지.
 *
 * 화면은 단계마다 답하는 말이 통째로 바뀐다 — 카운트다운이 일차로, 게이지가 사라지고,
 * 섹션 순서가 뒤집히고, 예매가 내려가고, 액션 줄의 주 버튼이 예매에서 시간표로 간다.
 * 그 분기가 전부 [HoyolandEvent.phase] 하나에 달려 있어서, 여기가 어긋나면 개막 당일
 * 아침에 **아무도 모르게** 옛 화면이 뜬다(개발자 목업으로는 그날이 와야 드러난다).
 *
 * 그래서 목업이 아니라 **날짜를 직접 넣어** 돌린다. 2026 실제 기간(10.2~10.5) 기준이다.
 */
class HoyolandPhaseTest {

    private val e = HoyolandDefaults.event.copy(
        startYmd = "2026-10-02",
        endYmd = "2026-10-05",
        announceYmd = "2026-08-31",
    )

    // ── 단계 판정 ──────────────────────────────────────────────────────────

    @Test
    fun 개막_한참_전은_UPCOMING() {
        val t = at("2026-09-16")
        assertEquals(HoyolandPhase.UPCOMING, e.phase(t))
        assertTrue(e.isBeforeEvent(t))
        assertFalse(e.isEventLive(t))
        assertEquals(16, e.daysUntilStart(t), "D-16 이 화면의 큰 숫자가 된다")
    }

    @Test
    fun 개막_하루_전은_TOMORROW() {
        val t = at("2026-10-01")
        assertEquals(HoyolandPhase.TOMORROW, e.phase(t))
        assertTrue(e.isBeforeEvent(t), "아직 개막 전이라 예매가 위에 선다")
        assertFalse(e.isEventLive(t))
        assertEquals(1, e.daysUntilStart(t))
    }

    @Test
    fun 개막_당일은_TODAY_이고_행사_중이다() {
        val t = at("2026-10-02")
        assertEquals(HoyolandPhase.TODAY, e.phase(t))
        assertTrue(e.isEventLive(t), "개막일 0시부터 화면이 뒤집힌다")
        assertFalse(e.isBeforeEvent(t))
        assertEquals(1, e.dayOrdinal(t), "1일차")
    }

    @Test
    fun 둘째날부터_폐막일까지_ONGOING() {
        for ((ymd, ordinal) in listOf("2026-10-03" to 2, "2026-10-04" to 3, "2026-10-05" to 4)) {
            val t = at(ymd)
            assertEquals(HoyolandPhase.ONGOING, e.phase(t), ymd)
            assertTrue(e.isEventLive(t), ymd)
            assertEquals(ordinal, e.dayOrdinal(t), "$ymd 는 ${ordinal}일차")
        }
    }

    @Test
    fun 폐막_다음날부터_ENDED() {
        val t = at("2026-10-06")
        assertEquals(HoyolandPhase.ENDED, e.phase(t))
        assertFalse(e.isEventLive(t))
        assertFalse(e.isBeforeEvent(t))
        assertEquals(0, e.dayOrdinal(t), "끝난 뒤에는 일차가 없다")
    }

    @Test
    fun 폐막일_자정_직전까지는_아직_행사_중이다() {
        // 마지막 날 23:59 에 화면이 미리 종료로 넘어가면, 현장에 있는 사람이 시간표를 잃는다.
        assertTrue(e.isEventLive(at("2026-10-05", 23, 59)))
        assertEquals(HoyolandPhase.ENDED, e.phase(at("2026-10-06", 0, 0)))
    }

    // ── 화면이 이 단계로 무엇을 가르는가 ──────────────────────────────────

    @Test
    fun 개막일에_섹션_순서가_뒤집힌다() {
        // 개막 전: 라인업 → 둘러보기 → 예매 / 행사 중: 둘러보기 → 라인업 (예매는 내려간다)
        assertFalse(e.isEventLive(at("2026-10-01")), "하루 전까지는 예매가 보인다")
        assertTrue(e.isEventLive(at("2026-10-02")), "개막일부터 둘러보기가 맨 위")
    }

    @Test
    fun 게이지는_개막_전에만_선다() {
        assertTrue(e.phase(at("2026-09-16")).isBeforeEvent)
        assertTrue(e.phase(at("2026-10-01")).isBeforeEvent)
        assertFalse(e.phase(at("2026-10-02")).isBeforeEvent, "개막하면 셀 것이 없다")
    }

    @Test
    fun 액션_줄은_종료_뒤에만_사라진다() {
        for (ymd in listOf("2026-09-16", "2026-10-01", "2026-10-02", "2026-10-05")) {
            assertFalse(e.phase(at(ymd)) == HoyolandPhase.ENDED, "$ymd 에는 버튼이 서 있어야 한다")
        }
        assertEquals(HoyolandPhase.ENDED, e.phase(at("2026-10-06")))
    }

    @Test
    fun 진행_중_주_버튼은_편성이_있어야_시간표가_된다() {
        // 화면 조건은 `isEventLive && hasTimetable` 이다 — 편성이 없으면 예매로 남는다.
        val noTimetable = e.copy(days = emptyList())
        assertTrue(noTimetable.isEventLive(at("2026-10-03")))
        assertFalse(noTimetable.hasTimetable, "편성이 없으면 「오늘 시간표」로 못 바꾼다")

        val withTimetable = e.copy(
            days = listOf(HoyolandDay("2026-10-03", listOf(HoyolandSlot("13:00", "무대", minutes = 60)))),
        )
        assertTrue(withTimetable.hasTimetable)
    }

    @Test
    fun 라인업_부제는_행사_중에만_무대_상태로_바뀐다() {
        val withStage = e.copy(
            days = listOf(HoyolandDay("2026-10-03", listOf(HoyolandSlot("13:00", "원신 무대", game = "원신", minutes = 60)))),
        )
        // 행사 전에는 빈 문자열 — 화면이 대신 게임 테마를 쓴다.
        assertEquals("", withStage.lineupStatusOf("원신", at("2026-09-16")))
        // 그날에는 무대 상태가 나온다.
        assertTrue(withStage.lineupStatusOf("원신", at("2026-10-03", 13, 10)).isNotEmpty())
        assertEquals("오늘 무대 없음", withStage.lineupStatusOf("젠레스 존 제로", at("2026-10-03", 13, 10)))
    }

    @Test
    fun 날짜를_못_읽으면_개막_전으로_본다() {
        // 어드민이 날짜를 지우거나 꼴을 깨도 화면은 서야 한다.
        val broken = e.copy(startYmd = "", endYmd = "")
        assertEquals(HoyolandPhase.UPCOMING, broken.phase(at("2026-10-03")))
    }

    /** 그 날짜 그 시각(KST)의 epoch millis. 기본은 한낮 — 날짜 경계에 걸리지 않게. */
    private fun at(ymd: String, hour: Int = 12, minute: Int = 0): Long {
        val p = ymd.split("-").map { it.toInt() }
        return LocalDateTime(p[0], p[1], p[2], hour, minute)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }
}
