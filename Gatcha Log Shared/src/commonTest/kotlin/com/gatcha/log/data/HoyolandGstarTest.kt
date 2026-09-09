package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 홈 배너 밑에 붙는 지스타 한 줄 — 상세용 원본을 그대로 쓰면 폭을 넘긴다.
 * 표본은 번들 기본값([HoyolandDefaults])에 실제로 들어 있는 문자열이다.
 */
class HoyolandGstarTest {

    private fun gstar(
        title: String = "G-STAR 2026",
        badge: String = "호요버스 100부스",
        facts: List<HoyolandFact> = listOf(
            HoyolandFact("기간", "2026.11.19(목) ~ 11.22(일) (4일)"),
            HoyolandFact("장소", "부산 벡스코(BEXCO)"),
        ),
    ) = HoyolandGstar(title, badge, facts, lineup = emptyList(), url = "", notice = "")

    /** 그날 정오 — 자정 경계에서 하루가 흔들리지 않게. */
    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDateTime(year, month, day, 12, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    @Test
    fun 연도_요일_괄호를_떼고_디데이와_함께_한_줄로() {
        assertEquals(
            "G-STAR 2026 · D-71 · 11.19~11.22 · 부산 벡스코",
            gstar().homeLine(at(2026, 9, 9)),
        )
    }

    @Test
    fun 개막일과_기간_중_표기() {
        assertEquals("G-STAR 2026 · 오늘 개막 · 11.19~11.22 · 부산 벡스코", gstar().homeLine(at(2026, 11, 19)))
        assertEquals("G-STAR 2026 · 진행 중 · 11.19~11.22 · 부산 벡스코", gstar().homeLine(at(2026, 11, 21)))
        assertEquals("G-STAR 2026 · 진행 중 · 11.19~11.22 · 부산 벡스코", gstar().homeLine(at(2026, 11, 22)))
    }

    @Test
    fun 끝난_행사는_줄을_그리지_않는다() {
        assertNull(gstar().homeLine(at(2026, 11, 23)))
    }

    @Test
    fun 내용이_없으면_줄을_그리지_않는다() {
        assertNull(gstar(facts = emptyList()).homeLine(at(2026, 9, 9)))
        assertNull(gstar(title = "").homeLine(at(2026, 9, 9)))
    }

    @Test
    fun 기간을_못_읽으면_제목만_남아_줄이_되지_않는다() {
        val noPeriod = gstar(facts = listOf(HoyolandFact("주최", "지스타조직위")))
        assertNull(noPeriod.homeLine(at(2026, 9, 9)))
    }
}
