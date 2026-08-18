package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 지출 한 건이 그 달에서 차지하는 비중.
 *
 * @param monthTotal 그 지출이 속한 달의 전체 지출
 * @param monthRatio 전체 대비 비율(0~1). 그 달 합계가 0이면 0
 * @param gameRatio 같은 달 **같은 게임** 지출 대비 비율(0~1)
 */
data class SpendingShare(
    val monthTotal: Long,
    val gameMonthTotal: Long,
    val monthRatio: Double,
    val gameRatio: Double,
) {
    val monthPercent: Int get() = (monthRatio * 100).roundToInt()
    val gamePercent: Int get() = (gameRatio * 100).roundToInt()
}

/**
 * '평소'와 견준 결과.
 *
 * @param median 비교 기준이 된 단건 **중앙값**
 * @param ratio 이 지출 / 중앙값. 1.0 이면 평소와 같다
 * @param sampleSize 중앙값을 낸 표본 수
 */
data class SpendingVsTypical(
    val median: Long,
    val ratio: Double,
    val sampleSize: Int,
) {
    /** "3.2배" 처럼 소수 한 자리. 1.0 근처(±5%)면 "비슷" 으로 부를 수 있게 [isTypical] 도 함께 본다. */
    val ratioLabel: String get() = "${(ratio * 10).roundToInt() / 10.0}배"
    val isTypical: Boolean get() = abs(ratio - 1.0) <= 0.05
    /** 평소보다 큰가 — 강조 여부 판단용(1.5배 이상). */
    val isNotable: Boolean get() = ratio >= 1.5
}

/**
 * 같은 항목을 산 이력.
 *
 * @param entries 최신순. 현재 보고 있는 지출도 포함한다(화면에서 '이번 건'을 표시하려면 필요).
 * @param ordinal 현재 지출이 **몇 번째** 구매인가(오래된 것부터 1). 1건뿐이면 1
 * @param averageIntervalDays 구매 간 평균 간격(일). 2건 미만이면 null
 */
data class SameItemHistory(
    val entries: List<Spending>,
    val totalAmount: Long,
    val ordinal: Int,
    val averageIntervalDays: Int?,
) {
    val count: Int get() = entries.size
}

/**
 * 지출 **한 건**을 놓고 재는 값들 — 상세 화면(2.0) 전용.
 *
 * [SpendingInsightStats]·[SpendingDerived] 와 역할이 다르다. 그쪽은 달 단위로 묶은 집계고,
 * 여기는 **지출 하나를 나머지와 견주는** 상대값이다.
 *
 * 화면은 그리기만 하고 판단은 전부 여기서 한다 — 같은 계산을 Compose·SwiftUI 두 번 쓰지 않는다.
 */
object SpendingDetailStats {

    /** '평소'를 낼 때 거슬러 보는 개월 수. */
    const val TYPICAL_MONTHS = 6

    /**
     * 중앙값을 말할 수 있는 최소 표본.
     *
     * 2건으로 "평소보다 3배"라고 하면 그 '평소'가 사실상 다른 한 건이다 — 비교가 아니라 착시다.
     */
    const val MIN_SAMPLE = 3

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 같은 달 지출 대비 비중. 같은 게임 안에서의 비중도 함께 낸다. */
    fun share(target: Spending, all: List<Spending>): SpendingShare {
        val key = DateUtil.yearMonthKey(target.dateMillis)
        var monthTotal = 0L
        var gameTotal = 0L
        for (s in all) {
            if (DateUtil.yearMonthKey(s.dateMillis) != key) continue
            monthTotal += s.amount
            if (s.gameName == target.gameName) gameTotal += s.amount
        }
        return SpendingShare(
            monthTotal = monthTotal,
            gameMonthTotal = gameTotal,
            monthRatio = if (monthTotal > 0) target.amount.toDouble() / monthTotal else 0.0,
            gameRatio = if (gameTotal > 0) target.amount.toDouble() / gameTotal else 0.0,
        )
    }

    /**
     * '평소 단건'과 견준다 — **평균이 아니라 중앙값**을 쓴다.
     *
     * 가챠 지출은 월정액 몇천 원과 천장 20만 원이 한 목록에 섞인다. 평균을 쓰면 큰 결제 한 번이
     * 기준선을 끌어올려, 정작 그 큰 결제가 "평소와 비슷"으로 나온다.
     *
     * 정기결제는 매달 같은 금액이라 견줄 이유가 없어 제외한다(호출부도 문구를 다르게 쓴다).
     *
     * @return 표본이 [MIN_SAMPLE] 미만이면 **null** — 화면은 이때 문구 자체를 감춘다.
     */
    fun vsTypical(
        target: Spending,
        all: List<Spending>,
        nowMillis: Long = currentTimeMillis(),
        months: Int = TYPICAL_MONTHS,
    ): SpendingVsTypical? {
        if (target.isSubscription) return null
        val since = nowMillis - months * 30L * DAY_MS
        val amounts = all.asSequence()
            .filter { it.id != target.id && !it.isSubscription }
            .filter { it.gameName == target.gameName }
            .filter { it.dateMillis >= since }
            .map { it.amount }
            .sorted()
            .toList()
        if (amounts.size < MIN_SAMPLE) return null
        val median = medianOf(amounts)
        if (median <= 0L) return null
        return SpendingVsTypical(
            median = median,
            ratio = target.amount.toDouble() / median,
            sampleSize = amounts.size,
        )
    }

    /**
     * 같은 게임·같은 항목을 산 이력.
     *
     * 항목명은 **앞뒤 공백만 정리해 그대로** 비교한다. "창세의 결정 990"과 "창세의 결정 330"은
     * 다른 상품이므로 느슨하게 묶으면 안 된다.
     *
     * @return 항목명이 비어 있으면 null(묶을 키가 없다). 1건뿐이어도 결과는 돌려주고,
     *   감출지는 화면이 [SameItemHistory.count] 로 판단한다.
     */
    fun sameItemHistory(target: Spending, all: List<Spending>): SameItemHistory? {
        val key = target.itemName.trim()
        if (key.isEmpty()) return null
        val mine = all.filter { it.gameName == target.gameName && it.itemName.trim() == key }
            .sortedByDescending { it.dateMillis }
        if (mine.isEmpty()) return null

        val oldestFirst = mine.asReversed()
        val ordinal = oldestFirst.indexOfFirst { it.id == target.id }.let { if (it < 0) mine.size else it + 1 }

        // 평균 간격 — 처음과 마지막 사이를 구매 횟수-1 로 나눈다(간격의 평균과 같고 계산이 짧다).
        val avgDays = if (mine.size >= 2) {
            val span = oldestFirst.last().dateMillis - oldestFirst.first().dateMillis
            ((span / DAY_MS) / (mine.size - 1)).toInt().takeIf { it > 0 }
        } else null

        return SameItemHistory(
            entries = mine,
            totalAmount = mine.sumOf { it.amount },
            ordinal = ordinal,
            averageIntervalDays = avgDays,
        )
    }

    /** 정렬된 목록의 중앙값. 짝수면 가운데 둘의 평균. */
    private fun medianOf(sorted: List<Long>): Long {
        if (sorted.isEmpty()) return 0L
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
