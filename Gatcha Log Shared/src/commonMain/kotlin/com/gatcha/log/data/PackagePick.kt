package com.gatcha.log.data

import com.gatcha.log.util.num
import com.gatcha.log.util.won

/** 「재화 선택」 묶음의 모양 — 화면이 이 값으로 칸을 고른다. */
enum class PickGroupKind {
    /** 같은 이름이 여러 줄인 재화(창세의 결정 60 · 300 · …) — 칸 첫 줄이 **개수**다. */
    TIERS,

    /** 이름이 제각각인 재화(오리지늄 조각 · 세트 · …) · 개수 없는 상품 — 칸 첫 줄이 **이름**이다. */
    NAMED,

    /** 월정액 · 패스 — 한 줄에 하나씩 놓는다. */
    PLAN,
}

/**
 * 「재화 선택」의 한 칸에 적는 글자. 서식을 여기서 끝내 두 플랫폼이 같은 글자를 그린다.
 *
 * @property headline 첫 줄 — 이름 묶음은 개수("300"), 「재화」 묶음 · 월정액 · 패스는 이름.
 * @property sub 둘째 줄 — "덤 +30" · "26개" · "월정액". 없으면 빈 글자(칸 높이를 맞추려고 자리는 남긴다).
 * @property price "6,500원".
 * @property pulls "약 2뽑" · "1뽑 미만". 개수를 모르는 상품(월정액 · 패스 · 개수 없는 재화)은 null.
 */
data class PackagePickItem(
    val pkg: GamePackage,
    val headline: String,
    val sub: String,
    val price: String,
    val pulls: String?,
)

/**
 * 「재화 선택」의 한 묶음 — 머리(이름 · 환산)와 칸들.
 *
 * @property title "창세의 결정" · "재화" · "월정액 · 패스".
 * @property hint 머리 오른쪽의 환산 글자("160개 = 1뽑"). **첫 재화 묶음에만** 붙는다 — 환산은 게임에 하나라 한 번만 말한다.
 */
data class PackagePickGroup(
    val kind: PickGroupKind,
    val title: String,
    val hint: String?,
    val items: List<PackagePickItem>,
)

/**
 * 고른 상품의 요약 면 — 「무엇을 몇 번 사서 얼마 · 재화 얼마」.
 *
 * @property title "창세의 결정 300 × 2". 한 번이면 "창세의 결정 300".
 * @property sub "660개 · 약 4뽑 가능". 월정액 · 패스는 종류("월정액"). 개수를 모르면 빈 글자.
 * @property unitLine "6,500원 × 2". 한 번이면 횟수 안내 문구.
 * @property total "13,000원" — **지금 금액 칸의 값**이다(저장되는 값과 어긋나지 않게).
 */
data class PackagePickSummary(
    val title: String,
    val sub: String,
    val unitLine: String,
    val total: String,
)

/**
 * 지출 추가 · 수정의 「재화 선택」 영역(10/13 개편, 시안 C)에 올릴 묶음과 글자를 만든다 — Android · iOS 가 이 한 소스를 쓴다.
 *
 * 묶는 규칙:
 *  - 재화는 **이름으로** 묶는다. 같은 이름이 여러 줄이면(창세의 결정 60 · 300 · …) 그 이름이 묶음 머리가 되고,
 *    이름이 한 줄뿐인 재화 · 개수 없는 상품은 한데 모아 「재화」 묶음이 된다.
 *  - 묶음 순서는 목록에 **처음 나온 순서**다(어드민 「재화 목록」의 줄 순서).
 *  - 월정액 · 패스는 한 묶음으로 **맨 뒤**에 둔다. 머리는 있는 종류만 — "월정액 · 패스" · "월정액" · "패스".
 */
object PackagePick {

    private const val NAMED_TITLE = "재화"

    /** 같은 이름이 여러 줄이라 이름 뒤에 개수를 붙여 가른 상품인가([PackageCatalog.build]). */
    private val GamePackage.isTier: Boolean get() = kind == PkgCategory.CURRENCY && count > 0 && currency != name

    private val GamePackage.isPlan: Boolean get() = kind == PkgCategory.MONTHLY || kind == PkgCategory.PASS

    /** [game] 의 상품 [packages] 를 묶음으로. 상품이 없으면 빈 목록. */
    fun groups(game: Game, packages: List<GamePackage>): List<PackagePickGroup> {
        val rate = GachaRateData.pullRate(game.key)

        // 재화 — 처음 나온 순서를 지키며 묶는다. 열쇠는 이름 묶음이면 재화 이름, 아니면 「재화」 하나(null).
        val buckets = LinkedHashMap<String?, MutableList<GamePackage>>()
        packages.filter { !it.isPlan }.forEach { p ->
            buckets.getOrPut(if (p.isTier) p.currency else null) { mutableListOf() }.add(p)
        }
        val currencyGroups = buckets.entries.mapIndexed { i, (key, list) ->
            PackagePickGroup(
                kind = if (key != null) PickGroupKind.TIERS else PickGroupKind.NAMED,
                title = key ?: NAMED_TITLE,
                hint = if (i == 0) rate.hint.takeIf { it.isNotEmpty() } else null,
                items = list.map { p -> currencyItem(p, rate, tier = key != null) },
            )
        }

        val plans = packages.filter { it.isPlan }
        if (plans.isEmpty()) return currencyGroups
        val planTitle = listOf(PkgCategory.MONTHLY, PkgCategory.PASS)
            .filter { k -> plans.any { it.kind == k } }
            .joinToString(" · ") { it.label }
        return currencyGroups + PackagePickGroup(
            kind = PickGroupKind.PLAN,
            title = planTitle,
            hint = null,
            items = plans.map { p -> PackagePickItem(p, p.name, p.kind.label, won(p.price), null) },
        )
    }

    private fun currencyItem(p: GamePackage, rate: PullRate, tier: Boolean): PackagePickItem {
        // 개수는 상품명에 적힌 그대로 적는다("창세의 결정 6480") — 쉼표를 넣지 않는다.
        val headline = if (tier) p.count.toString() else p.name
        val sub = when {
            tier -> if (p.bonusCount > 0) "덤 +${p.bonusCount}" else ""
            p.count > 0 -> "${p.count}개"
            else -> ""
        }
        val pulls = if (p.count > 0) {
            val n = rate.pullsOf(p.count + p.bonusCount)
            if (n >= 1) "약 ${n}뽑" else "1뽑 미만"
        } else null
        return PackagePickItem(p, headline, sub, won(p.price), pulls)
    }

    /**
     * 고른 상품 [pkg] 를 [quantity]번 샀을 때의 요약. [amount] 는 지금 금액 칸의 값이다.
     *
     * 뽑기 1회에 못 미치면 재화량만 적는다(히어로의 환산 줄과 같은 규칙).
     */
    fun summary(game: Game, pkg: GamePackage, quantity: Int, amount: Long): PackagePickSummary {
        val qty = SpendingEditLogic.clampQuantity(quantity)
        val sub = when {
            pkg.isPlan -> pkg.kind.label
            pkg.count > 0 -> {
                val total = (pkg.count + pkg.bonusCount) * qty
                val pulls = GachaRateData.pullRate(game.key).pullsOf(total)
                if (pulls >= 1) "${num(total)}개 · 약 ${pulls}뽑 가능" else "${num(total)}개"
            }
            else -> ""
        }
        return PackagePickSummary(
            title = if (qty > SpendingEditLogic.MIN_QUANTITY) "${pkg.name} × $qty" else pkg.name,
            sub = sub,
            unitLine = if (qty > SpendingEditLogic.MIN_QUANTITY) "${won(pkg.price)} × $qty" else "한 번에 여러 번 샀다면 횟수를 올리세요",
            total = won(amount),
        )
    }

    /**
     * 「자주 사는 것」 칸이 골라진 상태인가 — 고른 상품이 그것이거나, 상품 없이 재화명만 그 이름일 때.
     * (목록에 없는 이름을 자주 샀으면 상품은 못 찾고 재화명만 채워진다.)
     */
    fun isFrequentSelected(itemName: String, selectedPackageName: String?, currentItemName: String): Boolean =
        if (selectedPackageName != null) selectedPackageName == itemName else currentItemName == itemName
}
