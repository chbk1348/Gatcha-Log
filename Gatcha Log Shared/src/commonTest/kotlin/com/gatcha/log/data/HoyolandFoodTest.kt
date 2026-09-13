package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 푸드존 — 프로그램 목록에서 **제목으로** 갈라낸다(별도 config 필드를 늘리지 않는다).
 *
 * 가격대는 **메뉴 줄에서만** 긁는다. 설명 글 전체를 훑으면 "(우유·펄 추가 시 1,000원)" ·
 * "따로 사면 13,000원" 같은 문장 속 숫자가 섞여 최저가가 1,000원으로 떨어졌다.
 */
class HoyolandFoodTest {

    private fun event(vararg programs: HoyolandProgram) =
        HoyolandDefaults.event.copy(programs = programs.toList())

    private val 스타레일 = HoyolandProgram(
        "푸드트럭 — 붕괴: 스타레일",
        "빽! 낙원 핫플 리스트: 푸드트럭 편\n" +
            "\n" +
            "· 행운의 황금 레몬 만두 — 7,000원\n" +
            "   효 사장님 추천 음식 먹고 만사 형통\n" +
            "· 개척 여정의 커피 — 6,000원\n" +
            "\n" +
            "팬케이크와 커피를 세트로 사면 12,000원입니다(따로 사면 13,000원).",
    )
    private val 젠레스 = HoyolandProgram(
        "푸드존 — 젠레스 존 제로",
        "· 이아스 쿠키 — 3,000원\n" +
            "· '한 잔의 여유' 세트 — 10,000원\n" +
            "   홍차 또는 밀크티 + 스콘 (우유·펄 추가 시 1,000원)",
    )

    @Test
    fun 제목이_푸드로_시작하는_것만_푸드존이다() {
        val e = event(
            HoyolandProgram("웰컴 키트 — 공통", "전원 지급"),
            스타레일,
            HoyolandProgram("2차 창작물 전시존", "팬아트 전시"),
            젠레스,
        )
        assertEquals(listOf("푸드트럭 — 붕괴: 스타레일", "푸드존 — 젠레스 존 제로"), e.foodPrograms.map { it.title })
        assertEquals(listOf("웰컴 키트 — 공통", "2차 창작물 전시존"), e.otherPrograms.map { it.title })
    }

    @Test
    fun 가격대는_메뉴_줄만_센다() {
        // 문장 속 1,000원·12,000원·13,000원 은 세지 않는다 → 3,000원 ~ 10,000원.
        assertEquals("2곳 · 3,000원 ~ 10,000원", event(스타레일, 젠레스).foodEntryLine())
    }

    @Test
    fun 메뉴가_없으면_공개_전이다() {
        assertEquals("푸드존 정보 공개 전", event(HoyolandProgram("웰컴 키트 — 공통", "전원 지급")).foodEntryLine())
    }

    /**
     * 순서 뒤집기의 방아쇠 — 개막일 **0시**부터 현장에서 섹션이 예매 위로 올라간다.
     * 화면의 if/else 는 이 판정 하나만 본다.
     */
    @Test
    fun 개막일_0시에_진행중으로_바뀐다() {
        val e = HoyolandDefaults.event.copy(startYmd = "2026-10-02", endYmd = "2026-10-05")
        assertEquals(HoyolandPhase.BEFORE, e.phase(ymdMillis("2026-10-01", 23, 59)))
        assertEquals(HoyolandPhase.ONGOING, e.phase(ymdMillis("2026-10-02", 0, 0)))
    }

    /** 홈 배너 — 폐막일까지는 뜨고, **다음 날 0시에 스스로 빠진다.** */
    @Test
    fun 종료_다음날_0시부터_홈_배너가_빠진다() {
        val e = HoyolandDefaults.event.copy(startYmd = "2026-10-02", endYmd = "2026-10-05")
        assertEquals(true, e.isFeatured(ymdMillis("2026-10-05", 23, 59)))
        assertEquals(false, e.isFeatured(ymdMillis("2026-10-06", 0, 0)))
    }

    /** 로컬 자정 기준 밀리초 — [HoyolandEvent.phase] 가 기기 시간대의 날짜로 판정한다. */
    private fun ymdMillis(ymd: String, hour: Int, minute: Int): Long {
        val (y, m, d) = ymd.split("-").map { it.toInt() }
        return LocalDateTime(y, m, d, hour, minute)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }
}
