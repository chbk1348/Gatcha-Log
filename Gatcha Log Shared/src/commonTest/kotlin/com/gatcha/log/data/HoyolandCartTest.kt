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
    fun 담은_종수와_개수는_지금_목록_기준이다() {
        // 목록에서 빠진 이름만 남으면 원본은 「1종 · 5개」지만 화면에는 아무것도 없다 — 유령 「담은 n종 · 0원」.
        val ghost = HoyolandCart().withQuantity("이제는 없는 굿즈", 5)
        assertEquals(1, ghost.kindCount)
        assertEquals(0, event.cartKindCount(ghost))
        assertEquals(0, event.cartItemCount(ghost))
        val cart = ghost.withQuantity("행사 아트북", 2).withQuantity("피노코니 머그컵", 1)
        assertEquals(2, event.cartKindCount(cart))
        assertEquals(3, event.cartItemCount(cart))
    }

    @Test
    fun 같은_회차면_장바구니를_그대로_둔다() {
        val dated = event.copy(startYmd = "2026-10-02", endYmd = "2026-10-05")
        val cart = HoyolandCart().withQuantity("행사 아트북", 1)
        assertEquals(cart, dated.cartForEdition(cart, savedEdition = "2026-10-02"))
    }

    @Test
    fun 회차가_바뀌면_장바구니를_비운다() {
        val cart = HoyolandCart().withQuantity("행사 아트북", 1)
        // 2026 → 2027(날짜 미정) 전환. 회차 식별자는 개막일, 없으면 행사명이다.
        val next = HoyolandDefaults.event
        assertEquals("호요랜드 2027", next.editionKey)
        assertTrue(next.cartForEdition(cart, savedEdition = "2026-10-02").isEmpty)
        // 회차를 적기 전(이 기능 전)의 저장값도 지난 회차로 본다.
        assertTrue(next.cartForEdition(cart, savedEdition = "").isEmpty)
        // 다음 회차 날짜가 나와도 식별자가 다시 바뀌므로 2027 미정 시절 장바구니도 비워진다.
        val dated2027 = next.copy(startYmd = "2027-10-01", endYmd = "2027-10-04")
        assertEquals("2027-10-01", dated2027.editionKey)
        assertTrue(dated2027.cartForEdition(cart, savedEdition = next.editionKey).isEmpty)
    }

    @Test
    fun 깨진_저장값은_버리고_읽을_수_있는_줄만_남긴다() {
        val raw = "정상\t2\n망가진줄\n또다른줄\t숫자아님\n\t3"
        assertEquals(mapOf("정상" to 2), HoyolandCart.parse(raw).items)
    }
}
