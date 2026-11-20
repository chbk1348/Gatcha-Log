package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 스타레일 화폐전쟁(grid_fight) 파싱 — 2026-11-20 실응답 모양(값 일부 · 아이콘 주소는 줄였다).
 * 숫자가 전부 문자열로 온다.
 */
class GridFightClearTest {

    private fun role(id: String, rarity: String) = """{"role_id":"${id}02","avatar_id":"$id","icon":"https://x/$id.png","star":"3","rarity":"$rarity","is_trial":false}"""

    @Test
    fun archivesBecomeRoomsWithFrontAndBackLineups() {
        val d = JSONObject("""{
          "grid_fight_brief":{"division":{"level":"9","name_with_num":"재물의 창조주 8","name":"재물의 창조주"},
            "season_level":"70","weekly_score_cur":"12000","weekly_score_max":"18000","unlocked":true,"has_played":true},
          "grid_fight_archive_list":[
            {"archive_type":"ArchiveTypeNormal",
             "brief":{"archive_time":"2026-11-20 00:41:22","archive_time_ts":"1795106482","archive_rank":"GridFightArchiveRankA","remain_hp":"50"},
             "lineup":{"front_roles":[${role("1510", "4")}],"back_roles":[${role("1309", "5")},${role("1212", "5")}]}},
            {"archive_type":"ArchiveTypeNormal","brief":{"archive_time_ts":"0","archive_rank":"GridFightArchiveRankB"},
             "lineup":{"front_roles":[],"back_roles":[]}}
          ]}""")
        val c = HoyolabApi.gridFightClear("붕괴: 스타레일", d)

        assertEquals("화폐전쟁", c.mode)
        assertEquals("재물의 창조주 8 · 주간 12,000 / 18,000", c.scoreLabel)
        // 편성이 빈 판은 빠지고, 평가는 접두어를 떼어 "A".
        val room = c.rooms.single()
        assertEquals("A", room.rating)
        assertEquals(listOf(1510), room.firstHalf.map { it.id })
        assertEquals(listOf(1309, 1212), room.secondHalf.map { it.id })
        assertEquals(5, room.secondHalf.first().rarity)
        assertEquals("앞줄" to "뒷줄", room.firstLabel to room.secondLabel)
    }
}
