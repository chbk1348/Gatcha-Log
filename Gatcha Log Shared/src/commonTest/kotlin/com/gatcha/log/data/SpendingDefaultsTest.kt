package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 지출 추가의 스마트 기본값 — **"기록이 없으면 추론하지 않는다"** 를 테스트로 굳힌다.
 * 잘못 추론한 기본값은 사용자가 알아채기 어렵게 틀린 기록을 만든다.
 */
class SpendingDefaultsTest {

    private val day = 86_400_000L

    private fun s(
        game: String,
        amount: Long = 6_500,
        daysAgo: Long = 0,
        payment: String = "카드",
        platform: String = "",
        item: String = "",
    ) = Spending(
        gameName = game,
        amount = amount,
        dateMillis = 1_800_000_000_000L - daysAgo * day,
        paymentMethod = payment,
        chargePlatform = platform,
        itemName = item,
    )

    // ---------------------------------------------------------------- 빈 기록

    @Test
    fun 기록이_없으면_아무것도_추론하지_않는다() {
        assertNull(SpendingDefaults.lastGame(emptyList()))
        assertNull(SpendingDefaults.topPaymentMethod(emptyList()))
        assertNull(SpendingDefaults.lastPlatform(emptyList(), "원신"))
        assertTrue(SpendingDefaults.frequentItems(emptyList(), "원신").isEmpty())
    }

    // ---------------------------------------------------------------- 게임

    @Test
    fun 마지막으로_기록한_게임을_고른다() {
        val list = listOf(s("원신", daysAgo = 5), s("붕괴: 스타레일", daysAgo = 1), s("원신", daysAgo = 9))
        assertEquals("붕괴: 스타레일", SpendingDefaults.lastGame(list))
    }

    // ---------------------------------------------------------------- 결제수단

    @Test
    fun 최근_창에서_가장_많이_쓴_결제수단() {
        val list = listOf(
            s("원신", payment = "카카오페이", daysAgo = 1),
            s("원신", payment = "카카오페이", daysAgo = 2),
            s("원신", payment = "카드", daysAgo = 3),
        )
        assertEquals("카카오페이", SpendingDefaults.topPaymentMethod(list))
    }

    @Test
    fun 동률이면_더_최근에_쓴_쪽() {
        // 습관이 바뀌는 중일 때 새 쪽을 따라간다.
        val list = listOf(
            s("원신", payment = "토스", daysAgo = 1),
            s("원신", payment = "카드", daysAgo = 2),
        )
        assertEquals("토스", SpendingDefaults.topPaymentMethod(list))
    }

    @Test
    fun 창_밖의_오래된_습관은_세지_않는다() {
        // 최근 2건만 보면 토스. 창이 없으면 카드가 이긴다.
        val list = listOf(
            s("원신", payment = "토스", daysAgo = 1),
            s("원신", payment = "토스", daysAgo = 2),
        ) + (3..10).map { s("원신", payment = "카드", daysAgo = it.toLong()) }
        assertEquals("토스", SpendingDefaults.topPaymentMethod(list, window = 2))
        assertEquals("카드", SpendingDefaults.topPaymentMethod(list, window = 20))
    }

    // ---------------------------------------------------------------- 충전 플랫폼

    @Test
    fun 플랫폼은_그_게임에서_마지막에_쓴_값() {
        val list = listOf(
            s("원신", platform = "구글플레이", daysAgo = 3),
            s("원신", platform = "공식 충전소", daysAgo = 1),
            s("붕괴: 스타레일", platform = "앱스토어", daysAgo = 0),
        )
        assertEquals("공식 충전소", SpendingDefaults.lastPlatform(list, "원신"))
        assertEquals("앱스토어", SpendingDefaults.lastPlatform(list, "붕괴: 스타레일"))
        assertNull(SpendingDefaults.lastPlatform(list, "젠레스 존 제로"))
    }

    @Test
    fun 미선택_플랫폼은_값으로_세지_않는다() {
        val list = listOf(
            s("원신", platform = "구글플레이", daysAgo = 3),
            s("원신", platform = "", daysAgo = 1),   // 더 최근이지만 빈 값
        )
        assertEquals("구글플레이", SpendingDefaults.lastPlatform(list, "원신"))
    }

    // ---------------------------------------------------------------- 자주 사는 것

    @Test
    fun 한_번만_산_것은_자주_사는_것이_아니다() {
        val list = listOf(s("원신", item = "창세의 결정 300"))
        assertTrue(SpendingDefaults.frequentItems(list, "원신").isEmpty())
    }

    @Test
    fun 많이_산_순으로_최근_결제액과_함께() {
        val list = listOf(
            s("원신", item = "창세의 결정 300", amount = 6_500, daysAgo = 9),
            s("원신", item = "창세의 결정 300", amount = 6_900, daysAgo = 1), // 가격 인상 — 최근 값을 쓴다
            s("원신", item = "공월의 축복", amount = 5_900, daysAgo = 5),
            s("원신", item = "공월의 축복", amount = 5_900, daysAgo = 3),
            s("원신", item = "공월의 축복", amount = 5_900, daysAgo = 2),
        )
        val top = SpendingDefaults.frequentItems(list, "원신")
        assertEquals(2, top.size)
        assertEquals("공월의 축복", top[0].itemName)
        assertEquals(3, top[0].count)
        assertEquals("창세의 결정 300", top[1].itemName)
        assertEquals(6_900, top[1].amount)   // 평균이 아니라 마지막 값
    }

    @Test
    fun 다른_게임_기록은_섞이지_않는다() {
        val list = listOf(
            s("원신", item = "창세의 결정 300", daysAgo = 1),
            s("붕괴: 스타레일", item = "창세의 결정 300", daysAgo = 2),
        )
        assertTrue(SpendingDefaults.frequentItems(list, "원신").isEmpty())
    }

    @Test
    fun 수량_꼬리는_떼고_같은_상품으로_묶는다() {
        // 수량 스텝퍼가 "창세의 결정 300 ×2" 로 저장한다 → 그대로 세면 같은 상품이 갈라진다.
        val list = listOf(
            s("원신", item = "창세의 결정 300", daysAgo = 3),
            s("원신", item = "창세의 결정 300 ×2", daysAgo = 1),
        )
        val top = SpendingDefaults.frequentItems(list, "원신")
        assertEquals(1, top.size)
        assertEquals("창세의 결정 300", top[0].itemName)
        assertEquals(2, top[0].count)
    }

    @Test
    fun 상품명_안의_곱셈기호는_꼬리가_아니다() {
        assertEquals("창세의 결정 300", SpendingDefaults.baseItemName("창세의 결정 300 ×2"))
        assertEquals("스타×라이트", SpendingDefaults.baseItemName("스타×라이트"))
        assertEquals("팩 ×A", SpendingDefaults.baseItemName("팩 ×A"))
    }

    @Test
    fun limit_을_지킨다() {
        val list = (1..5).flatMap { i ->
            listOf(s("원신", item = "상품$i", daysAgo = i.toLong()), s("원신", item = "상품$i", daysAgo = i + 10L))
        }
        assertEquals(3, SpendingDefaults.frequentItems(list, "원신", limit = 3).size)
    }
}
