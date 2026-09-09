package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 무대 편성의 '지금/다음' 판정 — 현장에서 꺼내는 화면의 본론이라 경계에서 틀리면 안 된다.
 * 표본은 목업과 같은 하루(10.3 토, 메인 무대 6편).
 */
class HoyolandStageTest {

    private val ymd = "2026-10-03"

    private val event = HoyolandDefaults.event.copy(
        lineup = listOf(
            HoyolandLineup("원신", "무대"),
            HoyolandLineup("붕괴: 스타레일", "무대"),
            HoyolandLineup("붕괴3rd", "무대", abbr = "HI3", colorArgb = 0xFF30C6E8L),
        ),
        days = listOf(
            HoyolandDay(
                ymd,
                listOf(
                    HoyolandSlot("11:00", "달빛에 전하는 세레나데", "메인 무대", game = "원신", minutes = 40),
                    HoyolandSlot("12:30", "환야의 숨바꼭질", "메인 무대", game = "붕괴3rd", minutes = 30),
                    HoyolandSlot("14:00", "환락, 상상 그 이상으로", "메인 무대", game = "붕괴: 스타레일", minutes = 40),
                    HoyolandSlot("18:30", "합동 피날레 스테이지", "메인 무대"),
                ),
            ),
        ),
    )

    private fun at(h: Int, m: Int): Long =
        LocalDateTime(2026, 10, 3, h, m).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    @Test
    fun 공연_중이면_LIVE_와_남은_시간() {
        val slots = event.stageSlots(ymd, at(14, 12))
        assertEquals(StageState.DONE, slots[0].state)
        assertEquals(StageState.DONE, slots[1].state)
        assertEquals(StageState.LIVE, slots[2].state)
        assertEquals(28, slots[2].remainMin)
        assertTrue(slots[2].progress > 0.29f && slots[2].progress < 0.31f, "진행률=${slots[2].progress}")
        assertEquals(StageState.UPCOMING, slots[3].state)
    }

    @Test
    fun 시작_시각은_LIVE_끝나는_시각은_DONE() {
        assertEquals(StageState.LIVE, event.stageSlots(ymd, at(14, 0))[2].state)
        assertEquals(StageState.DONE, event.stageSlots(ymd, at(14, 40))[2].state)
    }

    @Test
    fun 범위_라벨은_길이가_있을_때만_끝시각을_붙인다() {
        val slots = event.stageSlots(ymd, at(9, 0))
        assertEquals("14:00 ~ 14:40", slots[2].rangeLabel)
        assertEquals("18:30", slots[3].rangeLabel)   // minutes 없음 = 마지막 편
    }

    @Test
    fun 길이가_없으면_다음_편_시작_전까지() {
        val noMinutes = event.copy(
            days = listOf(
                HoyolandDay(
                    ymd,
                    listOf(
                        HoyolandSlot("14:00", "A", game = "원신"),
                        HoyolandSlot("15:30", "B", game = "붕괴3rd"),
                    ),
                ),
            ),
        )
        assertEquals(StageState.LIVE, noMinutes.stageSlots(ymd, at(15, 29))[0].state)
        assertEquals(StageState.DONE, noMinutes.stageSlots(ymd, at(15, 30))[0].state)
    }

    @Test
    fun 오늘이_아닌_날은_전부_UPCOMING() {
        // 10.4 를 보는 중 — 10.3 은 지났지만 회색으로 칠하지 않는다.
        val slots = event.stageSlots(ymd, at(23, 59) + 86_400_000L)
        assertTrue(slots.all { it.state == StageState.UPCOMING })
    }

    @Test
    fun 못_읽는_시각은_라이브_판정에서_빠진다() {
        val odd = event.copy(days = listOf(HoyolandDay(ymd, listOf(HoyolandSlot("종일", "포토존")))))
        val slots = odd.stageSlots(ymd, at(14, 0))
        assertEquals(StageState.UPCOMING, slots[0].state)
        assertEquals("종일", slots[0].rangeLabel)
    }

    @Test
    fun 배지_색과_글자는_참가_목록에서_온다() {
        assertEquals(0xFF30C6E8L, event.stageColor("붕괴3rd"))          // 앱 밖 IP → 참가 목록 색
        assertEquals(GameData.colorFor("원신"), event.stageColor("원신")) // 앱이 아는 게임 → 대표색
        assertEquals("HI3", event.stageLabel("붕괴3rd"))         // GameData 밖 → 참가 목록 abbr
        assertEquals("원신", event.stageLabel("원신"))            // GameData 약칭
        assertEquals("스타레일", event.stageLabel("붕괴: 스타레일")) // 긴 이름은 약칭으로 줄인다
        assertEquals("전 IP", event.stageLabel(""))
        assertEquals(0L, event.stageColor(""))
    }

    @Test
    fun 그날_무대에_오르는_게임만_칩이_된다() {
        assertEquals(listOf("원신", "붕괴3rd", "붕괴: 스타레일"), event.stageGames(ymd))
    }

    @Test
    fun 시각_파싱() {
        assertEquals(840, HoyolandEvent.minutesOfDay("14:00"))
        assertEquals(9 * 60 + 5, HoyolandEvent.minutesOfDay("9:05"))
        assertEquals(null, HoyolandEvent.minutesOfDay("종일"))
        assertEquals(null, HoyolandEvent.minutesOfDay("25:99"))
        assertEquals("14:40", HoyolandEvent.hhmm(880))
        assertEquals("09:05", HoyolandEvent.hhmm(545))
    }
}
