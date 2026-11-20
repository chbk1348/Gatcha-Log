package com.gatcha.log.data.api

import com.gatcha.log.data.StatCell
import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 계정 통계(index) — 게임마다 키 이름이 다르다. 응답 모양은 genshin.py 모델 기준이고 실응답이 아니다.
 */
class AccountStatsTest {

    @Test
    fun genshinKeys() {
        val s = HoyolabApi.accountStats("원신", JSONObject("""{"stats":{"active_day_number":1512,"achievement_number":1034,"avatar_number":87}}"""))!!
        assertEquals(listOf(StatCell("1,512일", "활동 일수"), StatCell("1,034", "업적"), StatCell("87", "캐릭터")), s.cells())
    }

    @Test
    fun starRailAndZzzKeys() {
        val hsr = HoyolabApi.accountStats("붕괴: 스타레일", JSONObject("""{"stats":{"active_days":921,"achievement_num":612,"avatar_num":52}}"""))!!
        assertEquals(Triple(921, 612, 52), Triple(hsr.activeDays, hsr.achievements, hsr.characters))

        val zzz = HoyolabApi.accountStats("젠레스 존 제로", JSONObject("""{"stats":{"active_days":498,"achievement_count":341,"avatar_num":38}}"""))!!
        assertEquals(StatCell("38", "에이전트"), zzz.cells().last())
        assertEquals(341, zzz.achievements)
    }

    @Test
    fun noStatsIsNull() {
        assertNull(HoyolabApi.accountStats("원신", JSONObject("{}")))
    }
}
