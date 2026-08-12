package com.gatcha.log.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * nanoka 매니페스트·방부 인덱스 파싱 — [NanokaApi].
 *
 * 여기서 미끄러지면 화면이 조용히 빈다(404 를 받아도 앱은 안 죽는다). 실제 응답에서 그대로
 * 떠온 형태로 고정한다.
 */
class NanokaApiTest {

    /** 2026-08-12 실제 응답을 줄인 것. `live`≠`latest`, id 가 숫자와 문자열로 섞여 온다. */
    private val manifestJson = """
        {
          "gi": { "latest": "7.0", "available": ["7.0"], "live": "7.0",
                  "new": { "character": [10000148, 10000150, "10000007-5"], "weapon": [11435] } },
          "zzz": { "latest": "3.2.2+18097913", "live": "3.1",
                   "new": { "character": [1611], "bangboo": [53101] } },
          "nte": { "latest": "1.2.17+518234", "live": "1.2", "new": { "character": [1036] } }
        }
    """.trimIndent()

    @Test
    fun `버전은 latest 를 먼저 시도한다`() {
        // live 만 쓰면 방금 나온 항목이 404 로 빈다 — 젠레스가 live=3.1·latest=3.2.2 이던 시점에 실제로 그랬다.
        val zzz = NanokaApi.parseManifest(manifestJson)!!.games["zzz"]!!
        assertEquals(listOf("3.2.2+18097913", "3.1"), zzz.versionsToTry)
    }

    @Test
    fun `두 버전이 같으면 한 번만 시도한다`() {
        val gi = NanokaApi.parseManifest(manifestJson)!!.games["gi"]!!
        assertEquals(listOf("7.0"), gi.versionsToTry)
    }

    @Test
    fun `화면 표기는 라이브 버전에서 빌드 번호를 뗀다`() {
        // 사용자가 게임에서 보는 숫자여야 한다 — "3.2.2+18097913" 을 그대로 띄우면 무슨 말인지 모른다.
        val m = NanokaApi.parseManifest(manifestJson)!!
        assertEquals("3.1", m.games["zzz"]!!.displayVersion)
        assertEquals("1.2", m.games["nte"]!!.displayVersion)
    }

    @Test
    fun `신규 id 는 숫자든 문자열이든 문자열로 모은다`() {
        // 여행자 변형이 "10000007-5" 처럼 문자열로 온다 — 숫자만 받으면 조용히 빠진다.
        val gi = NanokaApi.parseManifest(manifestJson)!!.games["gi"]!!
        assertEquals(listOf("10000148", "10000150", "10000007-5"), gi.new["character"])
        assertEquals(listOf("11435"), gi.new["weapon"])
    }

    @Test
    fun `게임 다섯 종을 모두 읽는다`() {
        val m = NanokaApi.parseManifest(manifestJson)!!
        assertEquals(setOf("gi", "zzz", "nte"), m.games.keys)
    }

    @Test
    fun `형식이 어긋나면 null 이다`() {
        assertNull(NanokaApi.parseManifest("not json"))
        assertNull(NanokaApi.parseManifest("{}"), "게임이 하나도 없으면 쓸 데가 없다")
    }

    // ── 방부 인덱스 ──────────────────────────────────────────────────────────

    private val bangbooJson = """
        {
          "53001": { "rank": 3, "codename": "Penguinboo", "EN": "Penguinboo", "KO": "펭귄부" },
          "53101": { "rank": 4, "codename": "Amillion", "EN": "Amillion", "KO": "아밀리온" },
          "53999": { "rank": 4, "codename": "Newboo", "EN": "Newboo", "KO": "" }
        }
    """.trimIndent()

    @Test
    fun `방부는 등급 높은 순으로 정렬한다`() {
        val list = NanokaApi.parseBangbooIndex(bangbooJson)
        assertEquals(listOf(4, 4, 3), list.map { it.rank })
        assertEquals("S", list.first().rankLabel)
        assertEquals("A", list.last().rankLabel)
    }

    @Test
    fun `한국어 이름이 비면 코드명으로 대신한다`() {
        // 갓 추가된 항목은 번역이 늦는다 — 빈 줄을 그리느니 영문 코드명이라도 보여준다.
        val newboo = NanokaApi.parseBangbooIndex(bangbooJson).single { it.id == "53999" }
        assertEquals("Newboo", newboo.name)
    }

    @Test
    fun `깨진 응답은 빈 목록이다`() {
        assertTrue(NanokaApi.parseBangbooIndex("<html>404</html>").isEmpty())
    }
}
