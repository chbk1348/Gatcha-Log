package com.gatcha.log.data.api

import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.RewardItem
import com.gatcha.log.data.ScheduleLogic
import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 게임 일정 보상 박스 — ennead `rewards[]` 를 항목으로 받는 규칙(2026-10-06 실응답 모양).
 * 원신은 대표 보상(원석)이 `rewards` 에 없어 맨 앞에 붙이고, 스타레일은 이미 있어 중복하지 않는다.
 */
class EnneadRewardTest {

    private fun item(name: String, amount: Int) = """{"name":"$name","icon":"https://x/$amount.png","rarity":"5","amount":$amount}"""

    @Test
    fun specialRewardGoesFirstWhenMissingFromRewards() {
        val o = JSONObject("""{"special_reward":${item("원석", 450)},"rewards":[${item("깨달음의 가루", 3)},${item("찬란한 형상", 0)}]}""")
        val items = EnneadApi.rewardItemsOf(o)
        assertEquals(
            listOf(
                RewardItem("원석", "https://x/450.png", 450),
                RewardItem("깨달음의 가루", "https://x/3.png", 3),
                RewardItem("찬란한 형상", "https://x/0.png", 0),
            ),
            items,
        )
    }

    @Test
    fun specialRewardNotDuplicatedAndNbspNormalized() {
        val o = JSONObject("""{"special_reward":${item("성옥", 500)},"rewards":[${item("성옥", 500)},${item("트렌디 토이", 18)}]}""")
        assertEquals(listOf("성옥", "트렌디 토이"), EnneadApi.rewardItemsOf(o).map { it.name })
    }

    @Test
    fun polychromeOnlyHasNoItems() {
        assertTrue(EnneadApi.rewardItemsOf(JSONObject("""{"polychrome":800}""")).isEmpty())
    }

    @Test
    fun scheduleEntryCarriesRewardItems() {
        val items = listOf(RewardItem("원석", "u", 450))
        val ev = GameEvent("원신", "이벤트", Long.MAX_VALUE / 2, reward = "원석 ×450", rewardItems = items)
        val entry = ScheduleLogic.buildSchedule(emptyList(), listOf(ev), emptyList()).single()
        assertEquals(items, entry.rewardItems)
        assertEquals("원석 ×450", entry.sub)
    }
}
