package com.gatcha.log.data

import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.data.api.GiftReward
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 리딤코드 보상 항목 — 「보상 목록 두 열」 이 읽는 값([GiftCode.items]). */
class GiftRewardParseTest {

    @Test
    fun `보상 글자를 항목으로 나눈다`() {
        val c = GiftCode("A", "원석 ×100, 모라 ×50000, 영웅의 경험 ×5, 외 2종")
        assertEquals(
            listOf(GiftReward("원석", "100", true), GiftReward("모라", "50,000", false), GiftReward("영웅의 경험", "5", false)),
            c.items,
        )
        assertEquals(2, c.moreCount)
    }

    @Test
    fun `수량 안의 쉼표에서는 자르지 않는다`() {
        assertEquals(listOf(GiftReward("데니", "2,222", false)), GiftCode("B", "데니 ×2,222").items)
    }

    @Test
    fun `아는 재화는 아이콘이 붙고 모르는 이름은 비운다`() {
        val items = GiftCode("D", "원석 ×60, 성옥 ×50, 폴리크롬 ×30, 낯선 보상 ×1").items
        assertEquals("https://gi.yatta.moe/assets/UI/UI_ItemIcon_201.png", items[0].iconUrl)
        assertEquals("https://sr.yatta.moe/hsr/assets/UI/item/900001.png", items[1].iconUrl)
        assertEquals("", items[2].iconUrl)
        assertEquals("", items[3].iconUrl)
    }

    @Test
    fun `보상이 비면 확인 중`() {
        val c = GiftCode("C", "")
        assertTrue(c.rewardsPending)
        assertTrue(c.items.isEmpty())
    }
}
