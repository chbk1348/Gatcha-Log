package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * nanoka 매니페스트 파싱 — [NanokaApi].
 *
 * 여기서 미끄러지면 화면이 조용히 빈다(404 를 받아도 앱은 안 죽는다). 실제 응답에서 그대로
 * 떠온 형태로 고정한다.
 */
class NanokaApiTest {

    /**
     * 2026-08-12 실제 응답을 줄인 것. `live`≠`latest` 인 경우가 섞여 있다.
     *
     * 앱이 안 읽는 키(`new`·`available`)도 그대로 둔다 — 응답에는 계속 실려 오므로,
     * 모르는 키가 늘어도 파싱이 깨지지 않는다는 걸 같이 고정한다.
     */
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
    fun `게임 다섯 종을 모두 읽는다`() {
        val m = NanokaApi.parseManifest(manifestJson)!!
        assertEquals(setOf("gi", "zzz", "nte"), m.games.keys)
    }

    @Test
    fun `형식이 어긋나면 null 이다`() {
        assertNull(NanokaApi.parseManifest("not json"))
        assertNull(NanokaApi.parseManifest("{}"), "게임이 하나도 없으면 쓸 데가 없다")
    }

    // ------------------------------------------------------------------ 정련 효과

    /** 원신 — 단계가 곧 키이고, 설명에 수치가 이미 박혀 있다. */
    private val giWeaponJson = """
        {"name":"서풍검","refinement":{
          "1":{"name":"서풍 매의 투쟁","desc":"공격력이 <color=#99FFFFFF>20%</color> 증가한다"},
          "5":{"name":"서풍 매의 투쟁","desc":"공격력이 <color=#99FFFFFF>40%</color> 증가한다"}}}
    """.trimIndent()

    /** 스타레일 — 단계가 `level` 안에 있고, 설명은 자리표시자가 남은 틀이다. */
    private val hsrLightConeJson = """
        {"name":"야경 속에서","refinements":{
          "name":"꽃과 나비",
          "desc":"치명타 확률이 <color=#f29e38ff><unbreak>#1[i]%</unbreak></color> 증가하고 속도가 <unbreak>#2[f1]</unbreak> 오른다",
          "level":{
            "1":{"param_list":[0.18,10.5]},
            "5":{"param_list":[0.3,12.34]}}}}
    """.trimIndent()

    @Test
    fun `원신 무기는 단계 키에서 설명을 꺼낸다`() {
        val r = NanokaApi.parseRefinement(JSONObject(giWeaponJson), 5)!!
        assertEquals(5, r.level)
        assertEquals("서풍 매의 투쟁", r.name)
        assertEquals("공격력이 40% 증가한다", r.desc)
    }

    @Test
    fun `스타레일 광추는 틀에 단계별 수치를 채운다`() {
        // 이 모양을 몰라 정수 키를 하나도 못 찾고 통째로 null 이 됐다 — 광추만 특성 칸이 비었다.
        val r = NanokaApi.parseRefinement(JSONObject(hsrLightConeJson), 1)!!
        assertEquals(1, r.level)
        assertEquals("꽃과 나비", r.name)
        assertEquals("치명타 확률이 18% 증가하고 속도가 10.5 오른다", r.desc)
    }

    @Test
    fun `백분율 자리표시자는 100 을 곱한다`() {
        // 0.3 을 그대로 쓰면 "0%" 가 된다 — 실제로 화면에 뜨는 문장이 무의미해진다.
        val r = NanokaApi.parseRefinement(JSONObject(hsrLightConeJson), 5)!!
        assertTrue(r.desc.contains("30%"), r.desc)
        // `[f1]` 은 소수 한 자리다 — 12.34 는 12.3 으로 잘린다(자릿수는 상류가 정한다).
        assertTrue(r.desc.contains("12.3"), r.desc)
    }

    @Test
    fun `범위를 벗어난 단계는 가장 가까운 단계로 붙인다`() {
        // 화면이 6 을 물어도 빈칸이 되면 안 된다(상류는 5 까지만 준다).
        assertEquals(5, NanokaApi.parseRefinement(JSONObject(hsrLightConeJson), 9)!!.level)
        assertEquals(1, NanokaApi.parseRefinement(JSONObject(hsrLightConeJson), 0)!!.level)
    }

    @Test
    fun `수치가 모자라면 자리표시자를 지우지 않는다`() {
        // 지우면 문장에 구멍이 뚫린다 — 틀린 값을 지어내는 것보다도 눈에 띄어야 한다.
        val json = """{"name":"x","refinements":{"name":"y","desc":"#9[i] 증가","level":{"1":{"param_list":[0.1]}}}}"""
        assertEquals("#9[i] 증가", NanokaApi.parseRefinement(JSONObject(json), 1)!!.desc)
    }

    @Test
    fun `정련 정보가 없으면 null 이다`() {
        assertNull(NanokaApi.parseRefinement(JSONObject("""{"name":"x"}"""), 1))
        assertNull(NanokaApi.parseRefinement(JSONObject("""{"name":"x","refinement":{}}"""), 1))
    }
}
