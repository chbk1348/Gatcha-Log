package com.gatcha.log.data

/**
 * 굿즈 장바구니 — 현장에서 **얼마 들고 갈지**를 미리 담아 보는 자리.
 *
 * 장바구니라 부르지만 **주문이 아니다.** 호요랜드 굿즈샵은 현장 판매고 앱이 결제에 끼어들
 * 방법이 없다. 여기서 하는 일은 예산 가늠 하나다 — 이 앱이 지출을 다루는 앱이라, "행사에서
 * 얼마 쓸 것 같은가"에 답하는 것이 굿즈 목록의 본론이다.
 *
 * **저장한다.** 처음엔 화면 안에서만 살게 뒀는데, 그러면 목록을 나갔다 오면 비어 있어
 * 며칠에 걸쳐 고르는 물건에 쓸 수 없었다. 담은 것은 [AppSettings] 에 남는다.
 *
 * 담는 단위는 **굿즈 이름**이다. 원격 JSON 에 안정적인 id 가 없어서다 — 어드민에서 이름을
 * 고치면 그 줄은 장바구니에서 빠진다([HoyolandEvent.cartLines] 가 조용히 걸러낸다).
 * 가격표가 바뀌었는데 옛 값으로 합계를 내는 것보다 낫다.
 */
data class HoyolandCart(
    /** 굿즈 이름 → 수량. 수량 0 이하는 담지 않은 것으로 본다. */
    val items: Map<String, Int> = emptyMap(),
) {

    val isEmpty: Boolean get() = items.isEmpty()

    /** 담은 종류 수(수량 합이 아니다) — "3종" 표기에 쓴다. */
    val kindCount: Int get() = items.size

    /** 수량을 모두 더한 개수 — "5개" 표기에 쓴다. */
    val totalCount: Int get() = items.values.sum()

    fun quantityOf(name: String): Int = items[name] ?: 0

    fun contains(name: String): Boolean = quantityOf(name) > 0

    /** 담기/빼기 토글 — 담을 때 수량 1 로 시작한다. */
    fun toggle(name: String): HoyolandCart =
        if (contains(name)) withQuantity(name, 0) else withQuantity(name, 1)

    /**
     * 수량 설정. 0 이하면 목록에서 아예 뺀다 — "수량 0 인 항목"을 남겨 두면 장바구니가
     * 비었는데도 비어 보이지 않는다.
     */
    fun withQuantity(name: String, quantity: Int): HoyolandCart {
        if (name.isBlank()) return this
        val next = items.toMutableMap()
        if (quantity <= 0) next.remove(name) else next[name] = quantity.coerceAtMost(MAX_QUANTITY)
        return copy(items = next)
    }

    fun cleared(): HoyolandCart = HoyolandCart()

    /**
     * 저장 형태 — `이름\tn` 줄바꿈 구분.
     *
     * JSON 을 쓰지 않는 이유: 굿즈 이름에 따옴표·괄호가 흔하고(`"아크릴 스탠드 (푸리나)"`),
     * 이 저장은 오직 이 클래스만 읽고 쓴다. 탭은 굿즈 이름에 나올 수 없는 글자다.
     */
    fun serialize(): String =
        items.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    companion object {
        /** 한 종류를 이보다 많이 담는 일은 없다 — 오타로 999 개가 합계를 망치는 것만 막는다. */
        const val MAX_QUANTITY = 99

        fun parse(raw: String): HoyolandCart {
            if (raw.isBlank()) return HoyolandCart()
            val map = LinkedHashMap<String, Int>()
            raw.split("\n").forEach { line ->
                val i = line.lastIndexOf('\t')
                if (i <= 0) return@forEach
                val name = line.substring(0, i)
                val n = line.substring(i + 1).toIntOrNull() ?: return@forEach
                if (n > 0) map[name] = n.coerceAtMost(MAX_QUANTITY)
            }
            return HoyolandCart(map)
        }
    }
}

/**
 * 장바구니의 게임별 묶음 — 현장에서는 **게임 부스를 하나씩 돈다.**
 * 그래서 "원신 부스에서 얼마" 가 보여야 한다([subtotal] 이 그 값이다).
 *
 * @param game 빈 문자열이면 행사 공용(아트북·에코백).
 * @param unpriced 이 묶음에서 가격을 모르는 종수 — 소계가 "미정" 으로 갈릴지 정한다.
 */
data class HoyolandCartGroup(
    val game: String,
    val lines: List<HoyolandCartLine>,
    val subtotal: Int,
    val unpriced: Int,
) {
    /** 값을 하나도 모르면 소계 자리에 금액을 쓸 수 없다. */
    val allUnpriced: Boolean get() = subtotal <= 0 && unpriced > 0
}

/** 장바구니 한 줄 — 굿즈 + 수량 + 소계. 화면이 이걸 그대로 그린다. */
data class HoyolandCartLine(
    val goods: HoyolandGoods,
    val quantity: Int,
) {
    /** 가격 미정(0)이면 소계도 0 — 합계에서 빠진다. */
    val subtotal: Int get() = goods.price * quantity
}
