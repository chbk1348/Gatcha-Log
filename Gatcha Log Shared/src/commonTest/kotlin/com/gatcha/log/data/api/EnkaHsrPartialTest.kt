package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 스타레일 캐릭터 목록 — 느린 mihomo 를 기다리지 않고 HoYoLAB 보유 목록만으로 먼저 만든다(27.51.1).
 * 그 「먼저 만든 목록」과, mihomo 가 온 뒤의 최종 목록이 어떻게 갈리는지 고정한다.
 */
class EnkaHsrPartialTest {

    private val hoyo = JSONObject(
        """{"avatar_list":[
            {"id":1001,"name":"Mar. 7th","level":80,"rank":6,"rarity":4,"element":"ice","base_type":6,
             "equip":{"id":21000,"name":"광추 이름","level":80,"rank":5}},
            {"id":1002,"name":"단항","level":70,"rank":0,"rarity":4,"element":"wind","base_type":2}
        ]}""",
    )

    @Test
    fun `mihomo 없이도 연동된 계정은 HoYoLAB 목록으로 선다`() {
        val r = EnkaApi.hsrResult(null, hoyo, linked = true)
        val p = r.profile!!
        assertEquals(listOf("Mar. 7th", "단항"), p.chars.map { it.name })
        assertEquals(80, p.chars.first().level)
        assertEquals("광추 이름", p.chars.first().weapon?.name)
        // 닉네임 · 레벨은 mihomo 에만 있다 — 먼저 만든 목록에서는 빈다.
        assertEquals("", p.nickname)
        assertEquals(0, p.level)
    }

    /** 먼저 만들 것이 없으면(미연동 · 빈 목록) 아무것도 건네지 않는다 — 결과에 프로필이 없다. */
    @Test
    fun `먼저 만들 것이 없으면 프로필이 없다`() {
        assertEquals(null, EnkaApi.hsrResult(null, null, linked = false).profile)
        assertEquals(null, EnkaApi.hsrResult(null, JSONObject("""{"avatar_list":[]}"""), linked = true).profile)
    }

    /** mihomo 가 오면 쇼케이스 캐릭터는 그 값으로, 이름은 HoYoLAB 공식 표기로 선다. 닉네임 · 레벨도 채워진다. */
    @Test
    fun `mihomo 가 오면 닉네임과 쇼케이스 값이 채워진다`() {
        val mihomo = NetResult(
            200,
            """{"player":{"uid":"800000001","nickname":"개척자","level":70},
                "characters":[{"id":"1001","name":"Mar7","level":80,"rank":6,"rarity":4,
                               "element":{"name":"얼음"},"path":{"name":"보존"}}]}""",
        )
        val p = EnkaApi.hsrResult(mihomo, hoyo, linked = true).profile!!
        assertEquals("개척자", p.nickname)
        assertEquals(70, p.level)
        assertEquals(listOf("Mar. 7th", "단항"), p.chars.map { it.name })
        assertEquals("얼음", p.chars.first().element)
    }
}
