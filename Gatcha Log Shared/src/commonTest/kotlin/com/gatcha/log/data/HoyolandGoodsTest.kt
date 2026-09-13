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

    // ── 구매 제한은 note 에서 떼어 카드 아래 띠로 나간다(HoyolandGoods.limitLabel).

    @Test
    fun 한정_표기를_비고에서_떼어낸다() {
        val g = HoyolandGoods("아크릴 스탠드", 24000, note = "호요랜드2026 시리즈 · 디자인 2종 · 1인 5개 한정")
        assertEquals("1인 5개 한정", g.limitLabel)
        // 시리즈도 배지로 빠지므로 본문에는 나머지만 남는다.
        assertEquals("디자인 2종", g.noteRest)
    }

    @Test
    fun 한정이_없으면_비고를_그대로_둔다() {
        val g = HoyolandGoods("봉제인형 키링", 26000, note = "눈속의 즐거움")
        assertEquals("", g.limitLabel)
        assertEquals("눈속의 즐거움", g.noteRest)
    }

    @Test
    fun 한정만_있으면_나머지_비고는_빈다() {
        val g = HoyolandGoods("여권 케이스", 24000, note = "1인 5개 한정")
        assertEquals("1인 5개 한정", g.limitLabel)
        assertEquals("", g.noteRest)
    }

    @Test
    fun 수량_단위가_달라도_한정으로_읽는다() {
        assertEquals("1인 2매 한정", HoyolandGoods("색지", 0, note = "랜덤 · 1인 2매 한정").limitLabel)
    }

    @Test
    fun 한정_수량을_숫자로_읽는다() {
        // 숫자를 통째로 긁으면 '1인' 의 1 이 앞에 붙어 15 가 된다 — 다섯 개 제한이 열다섯이 된다.
        assertEquals(5, HoyolandGoods("여권 케이스", 0, note = "1인 5개 한정").limitPerPerson)
        assertEquals(2, HoyolandGoods("색지", 0, note = "랜덤 · 1인 2매 한정").limitPerPerson)
        assertEquals(0, HoyolandGoods("장우산", 0, note = "잡화").limitPerPerson)
    }

    @Test
    fun 행사_한정_시리즈는_배지로_뗀다() {
        val g = HoyolandGoods("아크릴 스탠드", 24000, note = "호요랜드2026 시리즈 · 디자인 2종 · 1인 5개 한정")
        assertEquals("호요랜드2026 시리즈", g.seriesLabel)
        assertEquals("1인 5개 한정", g.limitLabel)
        assertEquals("디자인 2종", g.noteRest)   // 배지로 뺀 둘은 본문에 다시 나오지 않는다
    }

    @Test
    fun 상품_라인업_이름은_행사_한정이_아니다() {
        // '신월의 축복' 은 상품 시리즈명이지 이 행사 한정이 아니다 — 배지로 빼면 뜻이 달라진다.
        val g = HoyolandGoods("SD캔배지", 5000, note = "신월의 축복 · 랜덤")
        assertEquals("", g.seriesLabel)
        assertEquals("신월의 축복 · 랜덤", g.noteRest)
    }

    @Test
    fun 한정이_아닌_문구는_떼지_않는다() {
        // '한정' 으로 끝나지 않거나 '1인' 으로 시작하지 않으면 그냥 비고다.
        val g = HoyolandGoods("테마 패키지", 39000, note = "한정판 · 구성: 키링 · 캔배지")
        assertEquals("", g.limitLabel)
        assertEquals("한정판 · 구성: 키링 · 캔배지", g.noteRest)
    }
}
