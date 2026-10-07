package com.gatcha.log.data.api

import kotlin.test.Test
import kotlin.test.assertEquals

/** 개발자 화면 「API Ping 조회」의 결과 줄 — 닿았는지와 시간이 한눈에 갈려야 한다. */
class ApiPingTest {

    @Test
    fun `요약은 닿은 곳 수와 중앙값 최대를 적는다`() {
        val lines = ApiPing.report(listOf(
            ApiPing.Hit("가", 120, 200), ApiPing.Hit("나", 300, 200), ApiPing.Hit("다", 80, 404), ApiPing.Hit("라", 8000, -1),
        ))
        assertEquals("4곳 중 3곳 응답 · 중앙값 120ms · 최대 300ms", lines.first())
        assertEquals(5, lines.size)
    }

    /** 로그인 없이 찔러 본 호스트의 4xx 는 「닿았다」다 — 못 닿은 것(시간 초과 · 연결 실패)만 실패로 적는다. */
    @Test
    fun `4xx 는 닿은 것이고 못 닿은 것만 실패다`() {
        val lines = ApiPing.report(listOf(
            ApiPing.Hit("정상", 120, 200), ApiPing.Hit("루트", 90, 404),
            ApiPing.Hit("느림", 8003, -1), ApiPing.Hit("끊김", 15, -1), ApiPing.Hit("라이브", 40, -1, "문서를 읽지 못함"),
        ))
        assertEquals("○ 정상 — 120ms", lines[1])
        assertEquals("○ 루트 — 90ms · HTTP 404", lines[2])
        assertEquals("✕ 느림 — 시간 초과 (8003ms)", lines[3])
        assertEquals("✕ 끊김 — 닿지 못함 (15ms)", lines[4])
        assertEquals("✕ 라이브 — 문서를 읽지 못함 (40ms)", lines[5])
    }

    @Test
    fun `한 곳도 못 닿으면 연결부터 보라고 한다`() {
        val lines = ApiPing.report(listOf(ApiPing.Hit("가", 10, -1), ApiPing.Hit("나", 12, -1)))
        assertEquals("2곳 모두 닿지 못했습니다 — 기기의 연결을 확인하세요", lines.first())
    }
}
