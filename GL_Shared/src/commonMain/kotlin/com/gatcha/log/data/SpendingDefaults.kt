package com.gatcha.log.data

/**
 * 지출 추가 화면의 **스마트 기본값과 자주 사는 것** — Android·iOS 가 이 한 소스를 공유한다.
 *
 * 지출 추가는 필드가 열한 개인데 매번 바뀌는 건 셋(게임·상품·금액) 정도다. 나머지는 사람마다
 * 거의 고정인데도 화면은 늘 `원신` · `카드` · 빈칸에서 시작했다 — 과거 기록이 `spendings` 에
 * 전부 있는데도 읽지 않았다. 토스만 쓰는 사람은 기록할 때마다 결제수단을 바꿔야 했다.
 *
 * **기록이 없으면 추론하지 않는다.** 전부 null / 빈 목록을 돌려주고, 화면은 기존 기본값을 쓴다.
 * 표본 한두 건으로 만든 "자주 사는 것"은 추천이 아니라 우연이다.
 */

/** 자주 산 상품 한 줄 — 이름 · 최근 결제액 · 산 횟수. */
data class FrequentItem(
    val itemName: String,
    /** **가장 최근** 결제액. 상품 가격은 개편·환율로 바뀌므로 평균이 아니라 마지막 값을 쓴다. */
    val amount: Long,
    val count: Int,
)

object SpendingDefaults {

    /** 최빈 결제수단을 셀 때 보는 최근 기록 수. 오래된 습관이 현재를 덮지 않도록 창을 둔다. */
    const val RECENT_WINDOW = 20

    /** '자주 사는 것'에 올리는 최소 구매 횟수. 1회짜리는 추천이 아니라 우연이다. */
    const val MIN_FREQUENT_COUNT = 2

    /** 마지막으로 기록한 게임. 없으면 null(화면은 기존 기본값 유지). */
    fun lastGame(spendings: List<Spending>): String? =
        spendings.maxByOrNull { it.dateMillis }?.gameName

    /**
     * 최근 [window]건에서 가장 많이 쓴 결제수단.
     * 동률이면 **더 최근에 쓴 쪽**을 고른다 — 습관이 바뀌는 중일 때 새 쪽을 따라간다.
     */
    fun topPaymentMethod(spendings: List<Spending>, window: Int = RECENT_WINDOW): String? {
        val recent = spendings.sortedByDescending { it.dateMillis }.take(window.coerceAtLeast(1))
        if (recent.isEmpty()) return null
        return recent
            .filter { it.paymentMethod.isNotBlank() }
            .groupBy { it.paymentMethod }
            .maxWithOrNull(
                compareBy(
                    { it.value.size },
                    { it.value.maxOf { s -> s.dateMillis } },
                ),
            )?.key
    }

    /**
     * **그 게임에서** 마지막에 쓴 충전 플랫폼. 게임마다 다를 수 있어(모바일/PC) 게임별로 본다.
     * 미선택(빈 문자열)은 값이 아니므로 세지 않는다.
     */
    fun lastPlatform(spendings: List<Spending>, gameName: String): String? =
        spendings
            .filter { it.gameName == gameName && it.chargePlatform.isNotBlank() }
            .maxByOrNull { it.dateMillis }
            ?.chargePlatform

    /**
     * 같은 게임에서 **[MIN_FREQUENT_COUNT]회 이상** 산 상품을 많이 산 순으로.
     * 동률이면 더 최근에 산 쪽이 앞이다. 빈 이름은 제외한다(상품을 안 적고 금액만 넣은 기록).
     *
     * 항목명은 구매 횟수를 담아 `"창세의 결정 300 ×2"` 로 저장될 수 있어(수량 스텝퍼),
     * 그대로 세면 같은 상품이 갈라진다 → **×N 꼬리를 떼고** 묶는다.
     */
    fun frequentItems(spendings: List<Spending>, gameName: String, limit: Int = 3): List<FrequentItem> =
        spendings
            .filter { it.gameName == gameName && baseItemName(it.itemName).isNotBlank() }
            .groupBy { baseItemName(it.itemName) }
            .mapNotNull { (name, list) ->
                if (list.size < MIN_FREQUENT_COUNT) return@mapNotNull null
                val latest = list.maxBy { it.dateMillis }
                FrequentItem(itemName = name, amount = latest.amount, count = list.size)
            }
            .sortedWith(compareByDescending<FrequentItem> { it.count }.thenByDescending { it.itemName })
            .take(limit.coerceAtLeast(0))

    /**
     * 항목명에서 수량 꼬리(`" ×3"`)를 떼어 낸 기본 이름.
     * 수량 스텝퍼가 붙이는 형식이라 같은 상품이 횟수별로 갈라지는 걸 막는다.
     */
    fun baseItemName(itemName: String): String {
        val idx = itemName.lastIndexOf('×')
        if (idx <= 0) return itemName.trim()
        val tail = itemName.substring(idx + 1).trim()
        // '×' 뒤가 숫자일 때만 꼬리로 본다 — 상품명 자체에 × 가 들어간 경우를 지키기 위해.
        return if (tail.isNotEmpty() && tail.all { it.isDigit() }) itemName.substring(0, idx).trim()
        else itemName.trim()
    }
}
