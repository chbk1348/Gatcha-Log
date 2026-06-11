package com.gatcha.log.ui.game

import com.gatcha.log.data.GachaBannerRate
import com.gatcha.log.data.GachaGameRate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 통합 계산기(GachaCalculatorSection)의 순수 파생 계산 모음 — @Composable 본문에서 분리해 단위 테스트 가능하게 함.
 *
 * 기존 inline 계산 블록을 동작·결과 동일하게 그대로 옮긴 것이라 UI/수치 변화 없음.
 * 시뮬레이터의 RNG roll 은 난수·상태 의존이라 여기서 제외(컴포저블에 유지).
 */

/** 재화 환산(보유 → 뽑기) 결과 — 표시값은 호출부에서 num()/won() 포맷. */
internal data class CurrencyCalcResult(
    val pityVal: Int,
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
internal fun computeCurrencyCalc(cur: Int, pityRaw: Int, banner: GachaBannerRate): CurrencyCalcResult {
    val pityVal = pityRaw.coerceIn(0, banner.hardPity - 1)
    val possiblePulls = cur / banner.perPull
    val leftCurrency = cur - possiblePulls * banner.perPull
    val pullsToHard = (banner.hardPity - pityVal).coerceAtLeast(0)
    val currencyToHard = pullsToHard * banner.perPull
    val additionalNeeded = (currencyToHard - cur).coerceAtLeast(0)
    val additionalPulls = if (banner.perPull > 0) ceil(additionalNeeded.toDouble() / banner.perPull).toInt() else 0
    val estCost = additionalPulls * banner.wonPerPull
    val pct = if (currencyToHard > 0) (cur.toDouble() / currencyToHard * 100).roundToInt().coerceIn(0, 100) else 0
    return CurrencyCalcResult(
        pityVal, possiblePulls, leftCurrency, pullsToHard,
        currencyToHard, additionalNeeded, additionalPulls, estCost, pct,
    )
}

/** 환산 시나리오(최선/최악 뽑기 수·설명) — 목표 개수 qty 배수 반영. */
internal data class ScenarioResult(
    val bestPulls: Int,
    val worstPulls: Int,
    val bestSub: String,
    val worstSub: String,
)

/** 천장·50/50·보장 상태로 최선/최악 뽑기 수를 추정. */
internal fun computeScenario(banner: GachaBannerRate, pityVal: Int, guaranteed: Boolean, qty: Int): ScenarioResult {
    val noPickup = banner.no5050 || !banner.has5050
    val bestPulls: Int
    val worstPulls: Int
    val bestSub: String
    val worstSub: String
    if (noPickup) {
        bestSub = "조기 획득"; worstSub = "천장 도달"
        bestPulls = (banner.softPity * 0.7).roundToInt() * qty
        worstPulls = banner.hardPity * qty
    } else {
        bestSub = if (guaranteed) "보장 + 빠른 획득" else "50/50 성공"
        worstSub = "50/50 실패 → 천장"
        val avgSingle = (banner.hardPity * 0.83).roundToInt()
        val bestSingle = if (guaranteed) maxOf(1, avgSingle - pityVal) else maxOf(1, (avgSingle * 0.6).roundToInt() - pityVal)
        val worstSingle = if (guaranteed) banner.hardPity - pityVal else (banner.hardPity - pityVal) + banner.hardPity
        bestPulls = maxOf(1, bestSingle) * qty
        worstPulls = maxOf(1, worstSingle) * qty
    }
    return ScenarioResult(bestPulls, worstPulls, bestSub, worstSub)
}

/** 뽑기 플래너 결과(무료 누적·보유+무료·필요 뽑기 수). days/weeks 는 표시용 동봉. */
internal data class PlannerResult(
    val days: Int,
    val weeks: Int,
    val freePulls: Int,
    val totalAvailable: Int,
    val totalNeeded: Int,
)

/**
 * 남은 [days]일 동안 데일리/주간 무료 재화로 모을 수 있는 뽑기 수와 필요량을 산출.
 * 날짜 차이(days)는 플랫폼 타임존 의존이라 호출부에서 계산해 주입한다.
 */
internal fun computePlanner(
    days: Int,
    game: GachaGameRate,
    banner: GachaBannerRate,
    currentPulls: Int,
    passOn: Boolean,
    qty: Int,
): PlannerResult {
    val weeks = days / 7
    val dailyPerDay = game.dailyFree.toDouble() / banner.perPull
    val weeklyPerWeek = game.weeklyFree.toDouble() / banner.perPull
    val pass = game.pass
    val passPerDay = if (passOn && pass != null) pass.dailyCrystal.toDouble() / banner.perPull else 0.0
    val freePulls = (days * (dailyPerDay + passPerDay) + weeks * weeklyPerWeek).toInt()
    val totalAvailable = currentPulls + freePulls
    val totalNeeded = banner.hardPity * qty
    return PlannerResult(days, weeks, freePulls, totalAvailable, totalNeeded)
}
