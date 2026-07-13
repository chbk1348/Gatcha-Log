package com.gatcha.log.data

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 통합 계산기의 순수 파생 계산 — **Android(Compose)·iOS(SwiftUI) 가 이 한 소스를 공유한다.**
 *
 * 예전에는 GL_Android 의 `GachaCalcLogic.kt` 와 GL_IOS 의 `GachaCalculatorSection.swift` 에
 * 같은 계산이 상수(0.7·0.83·0.6)까지 복붙된 두 벌로 존재해, 한쪽을 튜닝하면 반대 플랫폼이
 * 조용히 어긋났다. 앱의 핵심 계산이므로 commonMain 단일 소스로 통합했다.
 */

/** 재화 환산(보유 → 뽑기) 결과 — 표시값은 호출부에서 num()/won() 포맷. */
data class CurrencyCalc(
    val pity: Int,
    val possiblePulls: Int,
    val leftCurrency: Int,
    val pullsToHard: Int,
    val currencyToHard: Int,
    val additionalNeeded: Int,
    val additionalPulls: Int,
    val estCost: Int,
    val pct: Int,
)

/** 보유 재화·현재 천장으로 가능 뽑기 수·하드 천장까지 추가 비용·진행도(%)를 산출. */
fun computeCurrencyCalc(currency: Int, pityRaw: Int, banner: GachaBannerRate): CurrencyCalc {
    val pity = pityRaw.coerceIn(0, banner.hardPity - 1)
    val possiblePulls = if (banner.perPull > 0) currency / banner.perPull else 0
    val leftCurrency = currency - possiblePulls * banner.perPull
    val pullsToHard = (banner.hardPity - pity).coerceAtLeast(0)
    val currencyToHard = pullsToHard * banner.perPull
    val additionalNeeded = (currencyToHard - currency).coerceAtLeast(0)
    val additionalPulls =
        if (banner.perPull > 0) ceil(additionalNeeded.toDouble() / banner.perPull).toInt() else 0
    val estCost = additionalPulls * banner.wonPerPull
    val pct =
        if (currencyToHard > 0) (currency.toDouble() / currencyToHard * 100).roundToInt().coerceIn(0, 100) else 0
    return CurrencyCalc(
        pity = pity,
        possiblePulls = possiblePulls,
        leftCurrency = leftCurrency,
        pullsToHard = pullsToHard,
        currencyToHard = currencyToHard,
        additionalNeeded = additionalNeeded,
        additionalPulls = additionalPulls,
        estCost = estCost,
        pct = pct,
    )
}

/** 환산 시나리오(최선/최악 뽑기 수·설명) — 목표 개수 qty 배수 반영. */
data class GachaScenario(
    val bestPulls: Int,
    val worstPulls: Int,
    val bestSub: String,
    val worstSub: String,
)

/** 천장·50/50·보장 상태로 최선/최악 뽑기 수를 추정. */
fun computeScenario(banner: GachaBannerRate, pity: Int, guaranteed: Boolean, qty: Int): GachaScenario {
    val noPickup = banner.no5050 || !banner.has5050
    if (noPickup) {
        return GachaScenario(
            bestPulls = (banner.softPity * 0.7).roundToInt() * qty,
            worstPulls = banner.hardPity * qty,
            bestSub = "조기 획득",
            worstSub = "천장 도달",
        )
    }
    val avgSingle = (banner.hardPity * 0.83).roundToInt()
    val bestSingle =
        if (guaranteed) maxOf(1, avgSingle - pity) else maxOf(1, (avgSingle * 0.6).roundToInt() - pity)
    val worstSingle =
        if (guaranteed) banner.hardPity - pity else (banner.hardPity - pity) + banner.hardPity
    return GachaScenario(
        bestPulls = maxOf(1, bestSingle) * qty,
        worstPulls = maxOf(1, worstSingle) * qty,
        bestSub = if (guaranteed) "보장 + 빠른 획득" else "50/50 성공",
        worstSub = "50/50 실패 → 천장",
    )
}
