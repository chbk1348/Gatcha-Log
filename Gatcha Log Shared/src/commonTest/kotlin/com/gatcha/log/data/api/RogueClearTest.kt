package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 스타레일 시뮬레이션 우주(rogue) 파싱. 응답 모양은 genshin.py `StarRailRogue` 모델 기준이고
 * 실응답이 아니다 — 실제 응답을 받으면 그걸로 바꾼다.
 */
class RogueClearTest {

    private fun time(m: Int, d: Int) = """{"year":2026,"month":$m,"day":$d,"hour":4,"minute":0}"""

    @Test
    fun currentAndLastRecords() {
        val d = JSONObject("""{
            "current_record":{"has_data":true,
              "basic":{"id":1,"finish_cnt":2,"schedule_begin":${time(11, 17)},"schedule_end":${time(11, 24)}},
              "records":[
                {"name":"세계 9","difficulty":5,"score":12000,"final_lineup":[{"id":1309,"icon":"https://x/1309.png","level":80,"rarity":5}]},
                {"name":"세계 8","difficulty":4,"score":9000,"final_lineup":[]}
              ]},
            "last_record":{"has_data":false,"basic":{"finish_cnt":0},"records":[]}
        }""")
        val clears = HoyolabApi.rogueClears("붕괴: 스타레일", d)

        assertEquals(listOf(true, false), clears.map { it.current })
        val cur = clears.first()
        assertEquals("시뮬레이션 우주", cur.mode)
        assertEquals("11/17~11/24", cur.season)
        // 최종 편성이 빈 판은 빠진다.
        assertEquals(listOf(Triple("세계 9", "난이도 5", "12,000점")), cur.rooms.map { Triple(it.name, it.rating, it.detail) })
        assertEquals(1309, cur.rooms.single().firstHalf.single().id)
    }
}
