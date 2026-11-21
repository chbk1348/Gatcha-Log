package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 스타레일 이상 중재(challenge_peak) 파싱. 응답 모양은 genshin.py `AnomalyArbitration` 모델 기준이고
 * 실응답이 아니다 — 실제 응답을 받으면 그걸로 바꾼다.
 */
class PeakClearTest {

    private fun avatar(id: Int) = """{"id":$id,"icon":"https://x/$id.png","level":80,"rarity":5}"""

    private fun record(season: String, status: String, mobStars: Int, bossAvatars: String) = """
        {"group":{"name_mi18n":"$season","status":"$status"},
         "boss_info":{"maze_id":9,"name_mi18n":"보스"},
         "mob_infos":[{"maze_id":1,"name":"잡몹 1"},{"maze_id":2,"name":"잡몹 2"}],
         "has_challenge_record":true,
         "mob_records":[
           {"maze_id":1,"star_num":$mobStars,"avatars":[${avatar(1)}]},
           {"maze_id":2,"star_num":0,"avatars":[]}
         ],
         "boss_record":{"maze_id":9,"star_num":3,"avatars":[$bossAvatars]}}
    """

    @Test
    fun runningSeasonIsCurrentAndNextIsPast() {
        val d = JSONObject("""{"challenge_peak_records":[
            ${record("지난", "Finished", 2, avatar(3))},
            ${record("이번", "Running", 3, avatar(4))},
            ${record("더 지난", "Finished", 1, avatar(5))}
        ]}""")
        val clears = HoyolabApi.peakClears("붕괴: 스타레일", d)

        assertEquals(listOf("이번" to true, "지난" to false), clears.map { it.season to it.current })
        val cur = clears.first()
        assertEquals("이상 중재", cur.mode)
        // 편성이 빈 잡몹 2 는 빠지고, 잡몹 이름은 mob_infos 에서 maze_id 로 찾는다.
        assertEquals(listOf("잡몹 1" to 3, "보스" to 3), cur.rooms.map { it.name to it.stars })
        assertEquals(4, cur.rooms.last().firstHalf.single().id)
    }

    @Test
    fun noRecordsGivesNothing() {
        assertTrue(HoyolabApi.peakClears("붕괴: 스타레일", JSONObject("{}")).isEmpty())
    }
}
