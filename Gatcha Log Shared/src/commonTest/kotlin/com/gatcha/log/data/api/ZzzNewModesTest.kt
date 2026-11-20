package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 젠레스 임계 시뮬레이션 · 모의 세계 섬멸전 파싱. 응답 모양은 genshin.py 모델 기준이고
 * 실응답이 아니다 — 실제 응답을 받으면 그걸로 바꾼다.
 */
class ZzzNewModesTest {

    private fun agent(id: Int) = """{"id":$id,"level":60,"rarity":"S","role_square_url":"https://x/$id.png"}"""

    @Test
    fun thresholdPutsBossFirstThenStages() {
        val brief = JSONObject("""{"void_front_id":7,"has_ending_record":true,"total_score":123456,"rank_percent":1559}""")
        val detail = JSONObject("""{
            "boss_challenge_record":{"boss_info":{"name":"보스"},
              "main_challenge_record":{"name":"무시","star":"S","score":30000,"max_score":40000,"avatar_list":[${agent(1)}],
                "buddy":{"id":50,"rarity":"A","level":60,"bangboo_rectangle_url":"https://x/b.png"}}},
            "main_challenge_record_list":[
              {"name":"전투 1","star":"A","score":20000,"max_score":30000,"avatar_list":[${agent(2)}]},
              {"name":"전투 2","star":"","score":0,"max_score":30000,"avatar_list":[]}
            ]}""")
        val c = HoyolabApi.thresholdClear(brief, detail, current = true)

        assertEquals("임계 시뮬레이션", c.mode)
        assertEquals("123,456점 · 상위 15.59%", c.scoreLabel)
        // 편성이 빈 전투 2 는 빠지고, 보스전 이름은 boss_info 에서 온다.
        assertEquals(listOf("보스" to "S", "전투 1" to "A"), c.rooms.map { it.name to it.rating })
        assertEquals("30,000 / 40,000점", c.rooms.first().detail)
        assertTrue(c.rooms.first().firstHalf.last().isBuddy)
    }

    @Test
    fun thresholdWithoutRecordHasNoScore() {
        val c = HoyolabApi.thresholdClear(JSONObject("""{"has_ending_record":false}"""), null, current = false)
        assertEquals("", c.scoreLabel)
        assertTrue(c.rooms.isEmpty())
    }

    @Test
    fun annihilationGivesOneRoomPerBoss() {
        val d = JSONObject("""{
            "start_time":{"year":2026,"month":11,"day":3,"hour":4,"minute":0},
            "end_time":{"year":2026,"month":11,"day":17,"hour":3,"minute":59},
            "list":[
              {"rank":1,"star":3,"boss":{"name":"보스 A"},"avatar_list":[${agent(3)}]},
              {"rank":2,"star":1,"boss":{"name":"보스 B"},"avatar_list":[]}
            ]}""")
        val c = HoyolabApi.annihilationClear(d, current = true)

        assertEquals("모의 세계 섬멸전", c.mode)
        assertEquals("11/3~11/17", c.season)
        assertEquals(listOf("보스 A" to 3), c.rooms.map { it.name to it.stars })
    }
}
