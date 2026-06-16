package com.gatcha.log.data

/**
 * 충전 패키지 1종 (게임 내 프리미엄 재화 직접 충전).
 *
 * @param base     기본 지급 재화 (예: 6480)
 * @param bonus    일반 추가 보너스 (예: 1600 — 첫 구매와 무관하게 매번 지급)
 * @param priceKrw 한국 공식 인앱결제가(원, 플랫폼·할인 미반영)
 */
data class RechargePackage(
    val base: Int,
    val bonus: Int,
    val priceKrw: Int,
) {
    /** 일반 구매 시 받는 총 재화. */
    val normalTotal: Int get() = base + bonus

    /**
     * 첫 구매(계정당 패키지별 1회·버전마다 초기화) 시 총 재화.
     * 호요 규칙: 기본 재화가 2배 지급되고, 일반 보너스는 그대로 더해진다.
     */
    val firstBuyTotal: Int get() = base * 2 + bonus

    fun total(firstBuy: Boolean): Int = if (firstBuy) firstBuyTotal else normalTotal

    /** 재화 1개당 원화 단가(낮을수록 이득). */
    fun unitPrice(firstBuy: Boolean): Double = priceKrw.toDouble() / total(firstBuy)

    /** 이 패키지로 가능한 소환 횟수(소수 1자리). [costPerPull] = 1뽑당 재화. */
    fun pulls(firstBuy: Boolean, costPerPull: Int): Double =
        if (costPerPull <= 0) 0.0 else total(firstBuy).toDouble() / costPerPull
}

/**
 * 게임별 충전표. 현재 호요버스 3종(원신·스타레일·젠레스)만 지원 — 한국 공식가가 동일하고
 * 충전 재화(창세의 결정/별옥/모노크롬)가 게임 내 재화(원석/성옥/폴리크롬)로 1:1 전환된다.
 * 재화명·1뽑당 재화는 [GachaRateData] 의 currency·costPerPull 을 재사용한다.
 */
object RechargeData {

    /** 호요 3종 공통 충전표(한국 공식 인앱결제가). 단가 비교의 기준 데이터. */
    private val hoyoPackages: List<RechargePackage> = listOf(
        RechargePackage(base = 60, bonus = 0, priceKrw = 1_200),
        RechargePackage(base = 300, bonus = 30, priceKrw = 6_000),
        RechargePackage(base = 980, bonus = 110, priceKrw = 19_000),
        RechargePackage(base = 1_980, bonus = 260, priceKrw = 39_000),
        RechargePackage(base = 3_280, bonus = 600, priceKrw = 65_000),
        RechargePackage(base = 6_480, bonus = 1_600, priceKrw = 119_000),
    )

    private val supported: Set<String> = setOf("genshin", "hsr", "zzz")

    fun isSupported(gameKey: String): Boolean = gameKey in supported

    fun packagesFor(gameKey: String): List<RechargePackage> =
        if (isSupported(gameKey)) hoyoPackages else emptyList()

    /** 단가(원/개) 오름차순 정렬 — 가장 이득인 패키지가 먼저. */
    fun sortedByValue(gameKey: String, firstBuy: Boolean): List<RechargePackage> =
        packagesFor(gameKey).sortedBy { it.unitPrice(firstBuy) }
}
