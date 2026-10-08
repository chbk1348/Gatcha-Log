package com.gatcha.log.data.api

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 원신 · 젠레스 캐릭터 목록 — 무거운 상세를 기다리지 않고 **보유 목록만으로 먼저** 만든다(27.51.1).
 * 원신 상세는 전원의 스탯 · 무기 · 성유물을 한 번에 받고, 젠레스 상세는 1명당 1요청이라 다 끝나야 목록이 섰다.
 */
class EnkaListFirstTest {

    @Test
    fun `원신은 보유 목록만으로 이름 레벨 명좌 속성이 선다`() {
        val list = JSONArray(
            """[{"id":10000002,"name":"카미사토 아야카","element":"Cryo","level":90,"rarity":5,"actived_constellation_num":2,
                 "icon":"https://example.com/a.png","weapon":{"id":11509,"name":"안개를 가르는 회광","level":90,"rarity":5}},
                {"id":10000021,"name":"엠버","element":"Pyro","level":20,"rarity":4,"actived_constellation_num":0,"icon":""}]""",
        )
        val p = EnkaApi.giListResult(list)!!.profile!!
        assertEquals(listOf("카미사토 아야카", "엠버"), p.chars.map { it.name })
        assertEquals(90, p.chars[0].level)
        assertEquals(2, p.chars[0].rank)
        assertEquals(5, p.chars[0].rarity)
        assertEquals("얼음", p.chars[0].element)
        // 스탯 · 성유물은 상세에서 온다 — 목록 단계에서는 '상세 없음' 이다.
        assertFalse(p.chars.any { it.detailed })
        assertEquals(emptyList(), p.chars[0].stats)
        // 닉네임 · 모험 등급은 Enka 에만 있다.
        assertEquals("", p.nickname)
    }

    @Test
    fun `젠레스는 보유 목록만으로 이름 레벨 등급이 선다`() {
        val basic = listOf(
            JSONObject("""{"id":1011,"name_mi18n":"엔비","level":60,"rank":6,"rarity":"A","element_type":203,"avatar_profession":2,
                           "camp_name_mi18n":"교활한 토끼굴","role_square_url":"https://example.com/anby.png"}"""),
            JSONObject("""{"id":1041,"name_mi18n":"11호","level":50,"rank":0,"rarity":"S","element_type":201,"avatar_profession":1}"""),
        )
        val p = EnkaApi.zzzListResult(basic)!!.profile!!
        assertEquals(listOf("엔비", "11호"), p.chars.map { it.name })
        assertEquals(listOf(4, 5), p.chars.map { it.rarity })
        assertEquals("전기", p.chars[0].element)
        assertEquals("교활한 토끼굴", p.chars[0].camp)
        assertEquals("https://example.com/anby.png", p.chars[0].iconUrl)
        assertFalse(p.chars.any { it.detailed })
    }

    @Test
    fun `빈 목록이면 먼저 그릴 것이 없다`() {
        assertNull(EnkaApi.giListResult(JSONArray("[]")))
        assertNull(EnkaApi.zzzListResult(emptyList()))
    }
}
