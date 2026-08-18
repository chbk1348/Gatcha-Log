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

// 최선/최악 시나리오(`computeScenario`)는 `hardPity * 0.83`·`* 0.6` 같은 상수 곱셈이라
// 바로 옆의 정확한 확률 모델(`rateAt`)과 따로 놀았고, "최선 45회"에 확률이 붙지 않아
// 그 수가 얼마나 그럴듯한지 알 수 없었다.
// → `GachaCalcContext.kt` 의 누적분포 기반 분위수(`pullsAtQuantile`)로 대체했다.
