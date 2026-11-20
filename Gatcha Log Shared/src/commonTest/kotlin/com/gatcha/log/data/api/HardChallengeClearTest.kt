package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 원신 지맥 제압전(hard_challenge) 파싱. 응답 모양은 genshin.py `HardChallenge` 모델 기준이고
 * 실응답이 아니다 — 실제 응답을 받으면 그걸로 바꾼다.
 */
class HardChallengeClearTest {

    private fun member(id: Int) = """{"avatar_id":$id,"image":"https://x/$id.png","level":90,"rarity":5}"""

    private fun season(name: String, valid: Boolean, difficulty: Int, second: Int, firstTeam: String) = """
        {"schedule":{"schedule_id":1,"name":"$name","end_time":"1767225600","is_valid":$valid},
         "single":{"has_data":true,"best":{"difficulty":$difficulty,"second":$second},
           "challenge":[
             {"name":"보스 1","second":40,"teams":[$firstTeam]},
             {"name":"보스 2","second":0,"teams":[]}
           ]},
         "mp":{"has_data":false,"challenge":[]}}
    """

    @Test
    fun validSeasonIsCurrentAndNextIsPast() {
        val d = JSONObject("""{"data":[
            ${season("지난", false, 4, 200, member(1))},
            ${season("이번", true, 5, 120, member(2))}
        ]}""")
        val clears = HoyolabApi.hardChallengeClears("원신", d)

        assertEquals(listOf("이번" to true, "지난" to false), clears.map { it.season to it.current })
        val cur = clears.first()
        assertEquals("지맥 제압전", cur.mode)
        assertEquals("난이도 5 · 120초", cur.scoreLabel)
        // 편성이 빈 보스 2 는 빠지고, 칸에는 별 대신 소요 시간이 실린다.
        assertEquals(listOf("보스 1" to "40초"), cur.rooms.map { it.name to it.rating })
        assertEquals(2, cur.rooms.single().firstHalf.single().id)
    }

    @Test
    fun noDataGivesNothing() {
        assertTrue(HoyolabApi.hardChallengeClears("원신", JSONObject("{}")).isEmpty())
    }
}
