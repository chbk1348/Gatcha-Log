package com.gatcha.log.data

/**
 * 지출 추가 · 수정 화면의 **입력 규칙** — 「×N」 꼬리와 태그(27.52.0). Android·iOS 가 이 한 소스를 공유한다.
 *
 * 두 화면이 같은 규칙을 각자 적어 두고 있었고, 조용히 갈라져 있었다.
 * - 태그를 쪼갠 뒤 다듬는 범위가 달랐다 — Android 는 줄바꿈까지, iOS 는 공백 · 탭만 떼어 냈다.
 * - 수정 진입 때 빈 태그를 Android 만 걸렀다 — iOS 는 직접 입력 칸에 구분자만 남았다.
 * - 목록에 없는 게임명의 지출을 iOS 만 원신 상품에 맞춰 봤다.
 * - 「자세히」 요약이 공백뿐인 메모를 iOS 만 「메모」로 셌다.
 *
 * 저장되는 값(재화명 · 태그)을 만드는 규칙이라, 갈리면 **같은 계정의 기록이 기기마다 다르게** 쌓인다.
 * 화면 상태(무엇을 골랐는지 · 펼쳤는지)는 여기 두지 않는다 — 입력을 받아 값을 돌려주는 계산만 둔다.
 */

/** 구매 횟수를 반영한 값 — 범위 안으로 고친 횟수 · 재화명 · 금액. */
data class SpendingQuantity(
    val quantity: Int,
    /** 한 번이면 상품명 그대로, 두 번부터 `"상품명 ×N"`. */
    val itemName: String,
    val amount: Long,
)

/** 수정 진입 때 저장된 재화명에서 되읽은 값. */
data class SpendingItemRestore(
    /** 그 게임의 상품과 맞으면 그 상품명. 직접 적은 이름이거나 목록에 없는 게임이면 null. */
    val packageName: String?,
    /** 끝의 「×N」. 없으면 1. */
    val quantity: Int,
)

/** 저장된 태그를 화면의 두 자리로 가른 결과. */
data class SpendingTagSplit(
    /** 추천 태그 칩으로 켜 둘 것. */
    val selected: List<String>,
    /** 직접 입력 칸에 채울 글자. */
    val custom: String,
)

/** 접힌 「자세히」 줄. */
data class SpendingDetailSummary(
    /** "카카오페이 · 앱스토어 · 태그 2 · 메모" */
    val text: String,
    /** 기본값에서 벗어난 값이 있는가 — 화면이 요약을 강조색으로 칠할지 정한다. */
    val isCustom: Boolean,
)

/**
 * 끝의 「×N」 = 구매 횟수. 재화 총량을 곱하는 쪽(GameData.kt 의 `currencyCalc`)이 읽는 것과 같은 모양이라,
 * 스텝퍼에 되살린 횟수와 「재화 총 …」 줄이 같은 숫자를 본다. 파일 레벨에서 한 번만 컴파일한다.
 */
private val RE_QUANTITY_TAIL = Regex("×\\s*(\\d+)\\s*$")

object SpendingEditLogic {

    /** 구매 횟수 하한 — 한 번은 꼬리를 붙이지 않는다. */
    const val MIN_QUANTITY = 1

    /** 구매 횟수 상한. 스텝퍼의 + 버튼도 이 값에서 멈춘다. */
    const val MAX_QUANTITY = 99

    /** 직접 입력 칸을 다시 채울 때 태그 사이에 넣는 글자 — [mergeTags] 가 쪼개는 구분자(쉼표 · 공백)로만 이뤄져야 한다. */
    private const val TAG_JOINER = ", "

    // ---------------------------------------------------------------- 「×N」

    /** 횟수를 [MIN_QUANTITY]~[MAX_QUANTITY] 안으로. */
    fun clampQuantity(quantity: Int): Int = quantity.coerceIn(MIN_QUANTITY, MAX_QUANTITY)

    /**
     * 상품 [baseName] 을 [quantity] 번 산 것으로 — 재화명에 「×N」을 붙이고 금액을 곱한다.
     *
     * [baseName] 에 이미 꼬리가 있으면 **갈아 끼운다**(`"… ×2"` 에 3 → `"… ×3"`). 겹쳐 붙으면
     * 재화 환산이 마지막 꼬리만 횟수로 읽어, 앞 꼬리가 상품명에 섞인 채 계산이 깨진다.
     */
    fun applyQuantity(baseName: String, unitPrice: Long, quantity: Int): SpendingQuantity {
        val qty = clampQuantity(quantity)
        val base = SpendingDefaults.baseItemName(baseName)
        return SpendingQuantity(
            quantity = qty,
            itemName = if (qty > MIN_QUANTITY) "$base ×$qty" else base,
            amount = unitPrice * qty,
        )
    }

    /**
     * 재화명 끝의 「×N」에서 구매 횟수를 읽는다. 꼬리가 없으면 1.
     *
     * 재화명은 손으로도 고칠 수 있는 칸이라 범위 밖 숫자가 올 수 있다 → [clampQuantity] 로 맞춘다.
     * Int 에 안 담기는 숫자는 횟수로 보지 않는다(1).
     */
    fun readQuantity(itemName: String): Int {
        val match = RE_QUANTITY_TAIL.find(itemName.trim()) ?: return MIN_QUANTITY
        return match.groupValues[1].toIntOrNull()?.let { clampQuantity(it) } ?: MIN_QUANTITY
    }

    /**
     * 수정 진입 — 저장된 재화명(`"창세의 결정 300 ×3"`)에서 상품과 구매 횟수를 되읽는다.
     * 상품이 잡히면 화면이 스텝퍼를 그대로 다시 내놓는다.
     *
     * 꼬리를 떼는 규칙은 [SpendingDefaults.baseItemName] 하나다 — 「자주 사는 것」이 묶는 이름과 같아야 한다.
     * **목록에 없는 게임명이면 상품을 찾지 않는다** — 원신으로 떨어뜨려 원신 상품에 맞추면,
     * 다른 게임의 지출에 원신 단가로 곱하는 스텝퍼가 붙는다.
     */
    fun restoreItem(gameName: String, itemName: String): SpendingItemRestore {
        val base = SpendingDefaults.baseItemName(itemName)
        val pkg = GameData.byNameOrNull(gameName)
            ?.let { game -> GameData.packagesFor(game).firstOrNull { it.name == base } }
        return SpendingItemRestore(packageName = pkg?.name, quantity = readQuantity(itemName))
    }

    // ---------------------------------------------------------------- 태그

    /** 직접 입력 칸을 태그로 쪼갠다 — 구분자는 쉼표 · 공백. 앞뒤 공백 · 줄바꿈을 떼고 빈 조각은 버린다. */
    private fun customTagTokens(custom: String): List<String> =
        custom.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * 저장할 태그 — 켜 둔 칩([selected]) 뒤에 직접 입력 칸([custom])을 쪼개 잇고, 겹치는 것은 처음 것만 남긴다.
     * 순서는 칩 → 직접 입력, 각각 넣은 순서 그대로다.
     */
    fun mergeTags(selected: List<String>, custom: String): List<String> =
        (selected.map { it.trim() }.filter { it.isNotEmpty() } + customTagTokens(custom)).distinct()

    /**
     * 수정 진입 — 저장된 태그를 **추천 목록에 있는 것은 칩으로, 없는 것은 직접 입력 칸으로** 가른다.
     *
     * 전부 칩 선택에 담으면 직접 적었던 태그는 보이지도 않고 지울 길도 없다(칩은 추천 목록만 그린다).
     * 빈 태그는 칸에 넣지 않는다 — 넣으면 구분자만 남아 아무것도 안 고쳤는데 칸이 `", "` 로 시작한다.
     */
    fun splitTags(tags: List<String>): SpendingTagSplit {
        val suggested = GameData.suggestedTags
        return SpendingTagSplit(
            selected = tags.filter { it in suggested },
            custom = tags.filter { it.isNotBlank() && it !in suggested }.joinToString(TAG_JOINER),
        )
    }

    // ---------------------------------------------------------------- 「자세히」 요약

    /**
     * 접힌 「자세히」 줄 — 접힌 채로도 지금 값이 보여야 확인하려고 매번 펴지 않는다.
     *
     * 값이 있는지는 **공백을 뺀 글자**로 본다 — 스페이스만 친 메모는 메모가 아니다.
     * 태그 수는 칩 수 + 직접 입력 칸의 조각 수다(같은 태그를 두 번 적으면 둘로 센다 — 두 화면이 원래 그랬다).
     */
    fun detailSummary(
        paymentMethod: String,
        chargePlatform: String,
        selectedTags: List<String>,
        customTags: String,
        memo: String,
    ): SpendingDetailSummary {
        val tagCount = selectedTags.size + customTagTokens(customTags).size
        val text = buildList {
            add(paymentMethod.ifBlank { "카드" })
            if (chargePlatform.isNotBlank()) add(chargePlatform)
            add(if (tagCount > 0) "태그 $tagCount" else "태그 없음")
            if (memo.isNotBlank()) add("메모")
        }.joinToString(" · ")
        return SpendingDetailSummary(
            text = text,
            isCustom = chargePlatform.isNotBlank() || selectedTags.isNotEmpty() ||
                customTags.isNotBlank() || memo.isNotBlank(),
        )
    }
}
