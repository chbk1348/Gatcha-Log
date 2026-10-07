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

    /** 4xx 는 「닿기는 했다」(△ + 상태 코드)이고, 못 닿은 것(시간 초과 · 연결 실패)만 실패(✕)다. 없는 UID 의 404 처럼 그 자리의 정상 응답은 ○ 다. */
    @Test
    fun `4xx 는 닿은 것이고 못 닿은 것만 실패다`() {
        val lines = ApiPing.report(listOf(
            ApiPing.Hit("정상", 120, 200), ApiPing.Hit("루트", 90, 404),
            ApiPing.Hit("느림", 8003, -1), ApiPing.Hit("끊김", 15, -1), ApiPing.Hit("라이브", 40, -1, "문서를 읽지 못함"),
        ))
        assertEquals("○ 정상 — 120ms", lines[1])
        assertEquals("△ 루트 — 90ms · HTTP 404", lines[2])
        assertEquals("✕ 느림 — 시간 초과 (8003ms)", lines[3])
        assertEquals("✕ 끊김 — 닿지 못함 (15ms)", lines[4])
        assertEquals("✕ 라이브 — 문서를 읽지 못함 (40ms)", lines[5])
    }

    /** 이어 보낸 두 번째 요청은 연결을 다시 쓴다 — 첫 요청과 나란히 적어 연결에 드는 시간을 가늠하게 한다. */
    @Test
    fun `연결을 다시 쓴 시간을 나란히 적는다`() {
        val lines = ApiPing.report(listOf(
            ApiPing.Hit("느린 연결", 1070, 200, warmMs = 480), ApiPing.Hit("가까운 곳", 310, 404, warmMs = 30, ok = true),
            ApiPing.Hit("끊김", 15, -1), ApiPing.Hit("한 번만", 120, 200),
        ))
        assertEquals("첫 요청 → 연결 재사용 · 재사용 중앙값 480ms (차이가 연결을 맺는 시간)", lines[1])
        assertEquals("○ 느린 연결 — 1070ms → 480ms", lines[2])
        assertEquals("○ 가까운 곳 — 310ms → 30ms", lines[3])
        assertEquals("✕ 끊김 — 닿지 못함 (15ms)", lines[4])
        assertEquals("○ 한 번만 — 120ms", lines[5])
    }

    /** 한 회차는 한 줄로 남고, 그대로 되읽힌다 — 못 읽는 줄은 버린다. */
    @Test
    fun `기록은 한 줄로 남고 되읽힌다`() {
        val line = ApiPing.encode(1_000L, listOf(ApiPing.Hit("Ennead", 1070, 200, warmMs = 480), ApiPing.Hit("끊김", 8001, -1)))
        assertEquals("1000\tEnnead=1070/480/200;끊김=8001/-1/-1", line)
        val (at, hits) = ApiPing.decode(line)!!
        assertEquals(1_000L, at)
        assertEquals(listOf("Ennead", "끊김"), hits.map { it.name })
        assertEquals(480L, hits[0].warmMs)
        assertEquals(false, hits[1].reached)
        assertEquals(null, ApiPing.decode("깨진 줄"))
    }

    /** 기록 보기 — 맨 위는 출처별 평균(느린 순), 아래는 회차별 요약(새 것부터). */
    @Test
    fun `기록 보기는 출처별 평균과 회차별 요약을 적는다`() {
        val runs = listOf(
            1L to listOf(ApiPing.Hit("빠름", 100, 200, warmMs = 20), ApiPing.Hit("느림", 1000, 200, warmMs = 400)),
            2L to listOf(ApiPing.Hit("빠름", 200, 200, warmMs = 40), ApiPing.Hit("느림", 8000, -1)),
        )
        val lines = ApiPing.historyLines(runs) { "t$it" }
        assertEquals("최근 2회 · 출처별 평균(느린 순, 첫 요청 → 연결 재사용)", lines[0])
        assertEquals("느림 — 평균 1000ms → 400ms · 실패 1/2", lines[1])
        assertEquals("빠름 — 평균 150ms → 30ms", lines[2])
        assertEquals("── 회차별(새 것부터)", lines[3])
        assertEquals("t2 · 중앙값 200ms → 40ms · 최대 200ms(빠름) · 실패 1", lines[4])
        assertEquals("t1 · 중앙값 1000ms → 400ms · 최대 1000ms(느림)", lines[5])
        assertEquals(1, ApiPing.historyLines(emptyList()) { "" }.size)
    }

    @Test
    fun `한 곳도 못 닿으면 연결부터 보라고 한다`() {
        val lines = ApiPing.report(listOf(ApiPing.Hit("가", 10, -1), ApiPing.Hit("나", 12, -1)))
        assertEquals("2곳 모두 닿지 못했습니다 — 기기의 연결을 확인하세요", lines.first())
    }
}
