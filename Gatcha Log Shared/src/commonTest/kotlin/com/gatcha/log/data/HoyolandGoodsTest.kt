package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 굿즈 목록 — 앱이 다루지 않는 IP 의 굿즈는 싣지 않는다.
 * 호요랜드에는 붕괴3rd·미해결사건부도 나오는데, 그 굿즈까지 실으면 목록이 두 배가 된다.
 */
class HoyolandGoodsTest {

    private fun event(vararg goods: HoyolandGoods) =
        HoyolandDefaults.event.copy(goods = goods.toList())

    @Test
    fun 앱이_다루는_세_게임과_공용만_남는다() {
        val e = event(
            HoyolandGoods("원신 키링", 9000, "원신"),
            HoyolandGoods("스타레일 인형", 45000, "붕괴: 스타레일"),
            HoyolandGoods("젠존제 집업", 89000, "젠레스 존 제로"),
            HoyolandGoods("붕괴3rd 뱃지", 7000, "붕괴3rd"),          // 앱 밖 IP
            HoyolandGoods("미해결사건부 엽서", 5000, "미해결사건부"),  // 앱 밖 IP
            HoyolandGoods("행사 아트북", 35000),                      // 공용
        )
        assertEquals(
            listOf("원신 키링", "스타레일 인형", "젠존제 집업", "행사 아트북"),
            e.visibleGoods.map { it.name },
        )
    }

    @Test
    fun 게임_탭은_공용을_칸으로_만들지_않는다() {
        val e = event(
            HoyolandGoods("아트북", 35000),
            HoyolandGoods("키링", 9000, "원신"),
            HoyolandGoods("머그컵", 22000, "붕괴: 스타레일"),
            HoyolandGoods("뱃지", 7000, "붕괴3rd"),
        )
        assertEquals(listOf("원신", "붕괴: 스타레일"), e.goodsGames)
    }

    @Test
    fun 가격대는_걸러낸_목록_기준() {
        val e = event(
            HoyolandGoods("키링", 9000, "원신"),
            HoyolandGoods("집업", 89000, "젠레스 존 제로"),
            HoyolandGoods("앱_밖_비싼_굿즈", 500000, "붕괴3rd"),  // 범위에 들어가면 안 된다
        )
        assertEquals("9,000원 ~ 89,000원 · 2종", e.goodsPriceRange())
    }

    @Test
    fun 가격_미정은_개수에_넣고_범위에서_뺀다() {
        val e = event(
            HoyolandGoods("키링", 9000, "원신"),
            HoyolandGoods("미정 굿즈", 0, "원신"),
        )
        assertEquals("9,000원 · 2종 · 가격 미정 1종", e.goodsPriceRange())
    }

    @Test
    fun 굿즈가_없으면_빈_문자열() {
        assertEquals("", event().goodsPriceRange())
    }
}
