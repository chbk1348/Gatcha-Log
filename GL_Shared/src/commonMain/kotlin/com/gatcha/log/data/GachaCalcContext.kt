package com.gatcha.log.data

import com.gatcha.log.util.fixed
import kotlin.math.ceil

/**
 * 계산기 2.0 개선 — "이번 픽업 뽑을 수 있나"를 답하기 위한 파생 계산.
 * **Android(Compose)·iOS(SwiftUI) 가 이 한 소스를 공유한다.** 화면은 그리기만 한다.
 *
 * 예전 계산기는 두 가지가 문제였다.
 *  1. 앱이 이미 아는 천장·확정을 사용자에게 다시 물었다(Android 는 `pity` 를 받아놓고 쓰지 않았다).
 *  2. 최선/최악 시나리오가 `hardPity * 0.83` 같은 상수 곱셈이라, 바로 옆의 정확한 확률 모델
 *     (`rateAt`)과 따로 놀았다.
 *
 * 여기서는 확률을 **누적분포(CDF)로 한 번만** 만들고, 게이지·분위수·판정이 전부 그 한 배열에서
 * 나온다. 서로 어긋날 수 없다.
 */

// ============================================================ 프리필 (앱 기록 → 입력)

/** 계산기 진입 시 앱 기록으로 채우는 초기값. */
data class CalcPrefill(
    val pity: Int,
    val guaranteed: Boolean,
    /** 저축 플래너와 공유하는 보유 재화(0 = 미입력). */
    val held: Int,
    /**
     * 앱에 천장 기록이 **있는지**. 없으면 화면은 0 대신 빈 칸 + 안내를 보여준다 —
     * 기록이 없는데 0 을 채우면 "천장 0"이라는 틀린 사실을 앱이 주장하게 된다.
     */
    val hasPityRecord: Boolean,
)

/** 게임 키로 천장·보유 재화 기록을 모아 입력 초기값을 만든다. */
fun calcPrefill(
    gameKey: String,
    pity: Map<String, PityState>,
    held: Map<String, Int>,
): CalcPrefill {
    val st = pity[gameKey]
    return CalcPrefill(
        pity = st?.count ?: 0,
        guaranteed = st?.guaranteed ?: false,
        held = held[gameKey]?.coerceAtLeast(0) ?: 0,
        hasPityRecord = st != null,
    )
}

// ============================================================ 마감까지 무료 수급

/** 무료 수급 한 줄. [optional] 은 월정액처럼 켜야 합계에 들어가는 항목. */
data class FreeIncomeLine(val label: String, val amount: Int, val optional: Boolean = false)

/** 남은 기간 동안 과금 없이 모을 수 있는 재화. */
data class FreeIncome(
    val lines: List<FreeIncomeLine>,
    /** 켜진 항목만 더한 합계. */
    val total: Int,
    /** 합계의 뽑기 환산 라벨(예: "4.9뽑"). */
    val pullsLabel: String,
)

/**
 * 데일리·위클리·월정액으로 [days] 일 동안 모이는 재화.
 *
 * 위클리는 `days / 7` 회로 센다(리셋 시각을 실측하지 않았으므로 올림하지 않는다 —
 * 없는 한 회차를 더하면 "확보 가능" 판정이 낙관 쪽으로 틀어진다).
 * 월정액([includePass])은 **꺼진 것이 기본**이다. 미가입자가 다수라고 보고, 켠 만큼만 더한다.
 */
fun freeIncome(
    game: GachaGameRate,
    banner: GachaBannerRate,
    days: Int,
    includePass: Boolean,
): FreeIncome {
    val d = days.coerceAtLeast(0)
    val lines = buildList {
        if (game.dailyFree > 0) add(FreeIncomeLine("데일리 ${game.dailyFree} × ${d}일", game.dailyFree * d))
        val weeks = d / 7
        if (game.weeklyFree > 0 && weeks > 0) {
            add(FreeIncomeLine("위클리 ${game.weeklyFree} × ${weeks}회", game.weeklyFree * weeks))
        }
        game.pass?.let { p ->
            if (p.dailyCrystal > 0 && d > 0) {
                add(FreeIncomeLine("${p.name} ${p.dailyCrystal} × ${d}일", p.dailyCrystal * d, optional = true))
            }
        }
    }
    val total = lines.filter { includePass || !it.optional }.sumOf { it.amount }
    val perPull = banner.perPull.coerceAtLeast(1)
    return FreeIncome(lines, total, "${fixed(total.toDouble() / perPull, 1)}뽑")
}

// ============================================================ 픽업 확보 누적확률

/**
 * "n뽑 안에 픽업을 확보할" 누적확률. `cdf[n]` 형태이고 `cdf[0] = 0`, 마지막 원소는 1.0 이다.
 * 배열 길이 = [GachaRateData.maxPullsToSecure] + 1 — 최악 지점에서 정확히 1.0 이 되므로
 * 분위수·게이지·판정이 같은 상한을 공유한다.
 *
 * 모델:
 *  - 첫 최고등급까지의 분포는 [GachaRateData.rateAt] 에서 그대로 만든다(소프트 천장 상승곡선 반영).
 *    하드 천장 직전에서 확률이 정확히 1.0 이 되므로 분포의 합은 1 이다.
 *  - 50/50 이 있고 보장이 없으면 절반은 첫 최고등급이 픽업이고, 절반은 픽뚫 후 **두 번째**
 *    최고등급이 확정 픽업이다(이월 보장). 두 번째는 천장 0 에서 다시 뽑는 분포라 두 분포의
 *    합성곱으로 낸다.
 *  - 보장 보유·픽뚫 없음(`no5050`)·50/50 없는 배너는 첫 최고등급이 곧 확보다.
 *
 * 픽뚫 후 이월 보장이 없는 배너(`has5050 && !carryover`)도 2사이클로 본다 —
 * [GachaRateData.maxPullsToSecure] 가 쓰는 가정과 같게 맞춘 것이다.
 */
fun pickupCdf(banner: GachaBannerRate, startPity: Int, guaranteed: Boolean): DoubleArray {
    val hard = banner.hardPity.coerceAtLeast(1)
    val pity = startPity.coerceIn(0, hard - 1)
    val first = fiveStarPmf(banner, pity)
    val singleCycle = guaranteed || banner.no5050 || !banner.has5050

    if (singleCycle) return cumulative(first)

    val second = fiveStarPmf(banner, 0)
    val lost = convolve(first, second)          // 픽뚫 후 두 번째 최고등급까지
    val cumFirst = cumulative(first)
    val cumLost = cumulative(lost)
    // 길이는 긴 쪽(= 최악 = 첫 사이클 + 한 사이클)에 맞추고, 짧은 쪽은 마지막 값(1.0)으로 채운다.
    return DoubleArray(cumLost.size) { n ->
        val a = cumFirst[if (n < cumFirst.size) n else cumFirst.size - 1]
        0.5 * a + 0.5 * cumLost[n]
    }
}

/** [startPity] 에서 시작해 k번째 뽑기에 최고등급이 나올 확률(1-based, index 0 은 0). */
private fun fiveStarPmf(banner: GachaBannerRate, startPity: Int): DoubleArray {
    val hard = banner.hardPity.coerceAtLeast(1)
    val n = (hard - startPity).coerceAtLeast(1)
    val pmf = DoubleArray(n + 1)
    var surv = 1.0
    for (k in 1..n) {
        val p = GachaRateData.rateAt(startPity + k - 1, banner).coerceIn(0.0, 1.0)
        pmf[k] = surv * p
        surv *= (1 - p)
    }
    // 부동소수 잔차는 마지막(하드 천장)으로 몰아 합을 1 로 맞춘다.
    if (surv > 0) pmf[n] += surv
    return pmf
}

/** 두 뽑기수 분포의 합성곱 — "첫 최고등급 뒤 두 번째 최고등급까지" 총 뽑기수 분포. */
private fun convolve(a: DoubleArray, b: DoubleArray): DoubleArray {
    val out = DoubleArray(a.size + b.size - 1)
    for (i in a.indices) {
        val ai = a[i]
        if (ai <= 0.0) continue
        for (j in b.indices) {
            if (b[j] <= 0.0) continue
            out[i + j] += ai * b[j]
        }
    }
    return out
}

private fun cumulative(pmf: DoubleArray): DoubleArray {
    val out = DoubleArray(pmf.size)
    var acc = 0.0
    for (i in pmf.indices) {
        acc += pmf[i]
        out[i] = if (acc > 1.0) 1.0 else acc
    }
    if (out.isNotEmpty()) out[out.size - 1] = 1.0
    return out
}

/** [n]뽑 시점의 확보 확률. n 이 최악 지점을 넘으면 1.0. */
fun pickupProbAt(cdf: DoubleArray, n: Int): Double = when {
    cdf.isEmpty() -> 0.0
    n <= 0 -> 0.0
    n >= cdf.size -> 1.0
    else -> cdf[n]
}

/** 확보 확률이 [p] 이상이 되는 **첫** 뽑기 수(분위수). 도달하지 못하면 최악 지점. */
fun pullsAtQuantile(cdf: DoubleArray, p: Double): Int {
    for (n in cdf.indices) if (cdf[n] >= p) return n
    return (cdf.size - 1).coerceAtLeast(0)
}

/** 화면이 그대로 그리는 분위수 묶음. */
data class PullQuantiles(
    /** 절반은 이 안에 끝난다. */
    val p50: Int,
    /** 열에 아홉은 이 안에 끝난다. */
    val p90: Int,
    /** 최악 — [GachaRateData.maxPullsToSecure] 와 같은 지점. */
    val worst: Int,
)

/**
 * 한 번의 [pickupCdf] 로 p50·p90·최악을 함께 낸다.
 * 화면은 이것만 받는다 — 확률 배열을 플랫폼 쪽으로 내보내지 않기 위한 경계다.
 */
fun pullQuantiles(banner: GachaBannerRate, startPity: Int, guaranteed: Boolean): PullQuantiles {
    val cdf = pickupCdf(banner, startPity, guaranteed)
    return PullQuantiles(
        p50 = pullsAtQuantile(cdf, 0.5),
        p90 = pullsAtQuantile(cdf, 0.9),
        worst = (cdf.size - 1).coerceAtLeast(0),
    )
}

// ============================================================ 판정

/** 확보 판정 — 🟢 충분 / 🟡 아슬아슬 / 🔴 부족. */
enum class CalcVerdict { Secured, Tight, Short }

/** 판정 결과. 원화 포맷은 호출부에서 `won()` 으로 한다. */
data class CalcOutcome(
    val verdict: CalcVerdict,
    /** 판정 한 줄(예: "아슬아슬해요"). */
    val headline: String,
    /** 목표 개수까지 **최악** 기준 필요 뽑기. */
    val neededPulls: Int,
    val neededCurrency: Int,
    /** 보유 + (켜진) 무료 수급. */
    val availableCurrency: Int,
    val shortfallCurrency: Int,
    val shortfallPulls: Int,
    val shortfallWon: Long,
    /** 필요분 대비 확보율 0..100. */
    val progressPercent: Int,
)

/**
 * "이 픽업을 확보할 수 있나" 판정.
 *
 * **최악을 기준으로 판정한다.** 50/50 을 실패해도 확보되는지가 사용자가 실제로 알고 싶은 것이고,
 * 저축 플래너([SavingsPlanner])도 같은 [GachaRateData.maxPullsToSecure] 를 쓰므로 두 화면이 어긋나지 않는다.
 *
 * 목표가 2개 이상이면 첫 개만 현재 천장에서 세고, 나머지는 천장 0·보장 없음에서 다시 센다
 * (기존처럼 전체를 qty 배 하면 이미 쌓인 천장을 개수만큼 중복으로 깎게 된다).
 */
fun calcOutcome(
    banner: GachaBannerRate,
    heldCurrency: Int,
    freeCurrency: Int,
    pity: Int,
    guaranteed: Boolean,
    qty: Int,
): CalcOutcome {
    val hard = banner.hardPity.coerceAtLeast(1)
    val perPull = banner.perPull.coerceAtLeast(1)
    val copies = qty.coerceAtLeast(1)
    val p = pity.coerceIn(0, hard - 1)

    val neededPulls = GachaRateData.maxPullsToSecure(p, guaranteed, banner) +
        (copies - 1) * GachaRateData.maxPullsToSecure(0, false, banner)
    val neededCurrency = neededPulls * perPull

    // 픽뚫이 한 번도 없을 때 필요한 양 — 이 선만 넘으면 '아슬아슬'이다.
    val luckyPulls = (hard - p) + (copies - 1) * hard
    val luckyCurrency = luckyPulls * perPull

    val available = (heldCurrency.coerceAtLeast(0) + freeCurrency.coerceAtLeast(0))
    val shortfall = (neededCurrency - available).coerceAtLeast(0)
    val shortfallPulls = ceil(shortfall.toDouble() / perPull).toInt()

    val verdict = when {
        available >= neededCurrency -> CalcVerdict.Secured
        available >= luckyCurrency -> CalcVerdict.Tight
        else -> CalcVerdict.Short
    }
    return CalcOutcome(
        verdict = verdict,
        headline = when (verdict) {
            CalcVerdict.Secured -> "확정까지 충분해요"
            CalcVerdict.Tight -> "아슬아슬해요"
            CalcVerdict.Short -> "충전이 필요해요"
        },
        neededPulls = neededPulls,
        neededCurrency = neededCurrency,
        availableCurrency = available,
        shortfallCurrency = shortfall,
        shortfallPulls = shortfallPulls,
        shortfallWon = shortfallPulls.toLong() * banner.wonPerPull,
        progressPercent = if (neededCurrency > 0) {
            ((available.toLong() * 100) / neededCurrency).toInt().coerceIn(0, 100)
        } else 100,
    )
}
