package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 굿즈 장바구니 — 예산을 가늠하는 계산기다(주문이 아니다).
 * 저장·복원과 "지금 목록에 없는 굿즈는 조용히 빠진다" 를 지킨다.
 */
class HoyolandCartTest {

    private val goods = listOf(
        HoyolandGoods("아크릴 스탠드 (푸리나)", 18000, "원신", "아크릴"),
        HoyolandGoods("아크릴 키링 랜덤", 9000, "원신", "아크릴"),
        HoyolandGoods("피노코니 머그컵", 22000, "붕괴: 스타레일", "생활"),
        HoyolandGoods("한정 뱃지 세트", 0, "젠레스 존 제로", "아크릴"),
        HoyolandGoods("행사 아트북", 35000),
    )
    private val event = HoyolandDefaults.event.copy(goods = goods)

    @Test
    fun 담기는_수량_1_로_시작하고_다시_누르면_빠진다() {
        var cart = HoyolandCart().toggle("아크릴 키링 랜덤")
        assertEquals(1, cart.quantityOf("아크릴 키링 랜덤"))
        cart = cart.toggle("아크릴 키링 랜덤")
        assertTrue(cart.isEmpty)
    }

    @Test
    fun 수량_0_이하는_목록에서_아예_뺀다() {
        val cart = HoyolandCart().withQuantity("행사 아트북", 3).withQuantity("행사 아트북", 0)
        assertTrue(cart.isEmpty, "수량 0 인 항목이 남으면 비었는데도 비어 보이지 않는다")
    }

    @Test
    fun 수량은_상한을_넘지_않는다() {
        val cart = HoyolandCart().withQuantity("행사 아트북", 9999)
        assertEquals(HoyolandCart.MAX_QUANTITY, cart.quantityOf("행사 아트북"))
    }

    @Test
    fun 합계는_가격_미정을_빼고_센다() {
        val cart = HoyolandCart()
            .withQuantity("아크릴 스탠드 (푸리나)", 1)
            .withQuantity("아크릴 키링 랜덤", 2)
            .withQuantity("한정 뱃지 세트", 1)   // 미정
        assertEquals(18000 + 18000, event.cartTotal(cart))
        assertEquals(1, event.cartUnpricedCount(cart))
        assertEquals(3, cart.kindCount)
        assertEquals(4, cart.totalCount)
    }

    @Test
    fun 목록에_없는_굿즈는_조용히_빠진다() {
        // 어드민에서 이름을 고치면 옛 이름으로 담아 둔 줄이 남는다 — 합계에 넣으면 안 된다.
        val cart = HoyolandCart().withQuantity("이제는 없는 굿즈", 5).withQuantity("행사 아트북", 1)
        assertEquals(listOf("행사 아트북"), event.cartLines(cart).map { it.goods.name })
        assertEquals(35000, event.cartTotal(cart))
    }

    @Test
    fun 게임별_묶음은_목록_순서를_따르고_공용이_맨_뒤() {
        val cart = HoyolandCart()
            .withQuantity("행사 아트북", 1)
            .withQuantity("피노코니 머그컵", 1)
            .withQuantity("아크릴 스탠드 (푸리나)", 2)
        val groups = event.cartGroups(cart)
        assertEquals(listOf("원신", "붕괴: 스타레일", ""), groups.map { it.game })
        assertEquals(36000, groups[0].subtotal)
        assertEquals(22000, groups[1].subtotal)
        assertEquals(35000, groups[2].subtotal)
    }

    @Test
    fun 값을_하나도_모르는_묶음은_소계를_쓸_수_없다() {
        val cart = HoyolandCart().withQuantity("한정 뱃지 세트", 2)
        val g = event.cartGroups(cart).single()
        assertTrue(g.allUnpriced)
        assertEquals(1, g.unpriced)
    }

    @Test
    fun 저장하고_다시_읽어도_같다() {
        val cart = HoyolandCart()
            .withQuantity("아크릴 스탠드 (푸리나)", 2)   // 괄호가 든 이름
            .withQuantity("행사 아트북", 1)
        assertEquals(cart.items, HoyolandCart.parse(cart.serialize()).items)
    }

    @Test
    fun 깨진_저장값은_버리고_읽을_수_있는_줄만_남긴다() {
        val raw = "정상\t2\n망가진줄\n또다른줄\t숫자아님\n\t3"
        assertEquals(mapOf("정상" to 2), HoyolandCart.parse(raw).items)
    }
}
