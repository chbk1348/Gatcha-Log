package com.gatcha.log.ui.spending

import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Spending

/**
 * 지출 인사이트(SpendingInsightScreen)의 순수 파생 계산 모음 — @Composable 본문에서 분리해 단위 테스트 가능하게 함.
 *
 * Compose·색 의존 없는 순수 집계/산술만 담는다(색 매핑·막대 렌더는 화면에 유지).
 * 기존 inline remember 블록을 동작·결과 동일하게 옮긴 것이라 표시값 변화 없음.
 */

/** 예산 페이스 예측 결과. dayOfMonth/daysInMonth 는 '오늘' 의존이라 호출부에서 주입. */
internal data class BudgetPace(
    val projected: Long,
    val dailyAvg: Long,
    val remainingDays: Int,
)

/** 예상 지출 산정 시 신뢰할 최소 경과일(워밍업). 월초엔 표본이 적어 run-rate가 크게 증폭되므로 분모에 하한을 둔다. */
private const val PACE_WARMUP_DAYS = 7

/**
 * 경과 일수 기준 선형 추정(이번 달 예상 지출·하루 평균·남은 일수).
 * 월말 예상은 워밍업(7일) 완화 적용: 경과일이 7일 미만이면 분모를 7로 하한 처리해 월초 과대추정을 억제한다.
 * (배율 = daysInMonth / max(dayOfMonth, 7) ≥ 1 이므로 예상값은 항상 현재 지출 이상. 7일 경과 후엔 순수 run-rate와 동일)
 */
internal fun computeBudgetPace(monthTotal: Long, dayOfMonth: Int, daysInMonth: Int): BudgetPace {
    val effectiveDays = maxOf(dayOfMonth, minOf(daysInMonth, PACE_WARMUP_DAYS))
    val projected = if (dayOfMonth > 0) monthTotal * daysInMonth / effectiveDays else monthTotal
    val dailyAvg = if (dayOfMonth > 0) monthTotal / dayOfMonth else 0L
    val remainingDays = (daysInMonth - dayOfMonth).coerceAtLeast(0)
    return BudgetPace(projected, dailyAvg, remainingDays)
}

/** 게임별 월 추이(올해, 누적 막대) — 색/렌더 제외 데이터. */
internal class MonthlyTrend(
    /** 올해 지출 상위 5개 게임명. */
    val topGames: List<String>,
    /** month(0..11) -> 게임명/"기타" -> 금액 (삽입 순서 유지). */
    val monthGame: Array<LinkedHashMap<String, Long>>,
    /** 가장 큰 달의 합계(막대 스케일 분모, 최소 1). */
    val maxMonth: Long,
    /** 범례·막대 적층 순서(상위 게임 + 필요 시 "기타"). */
    val legend: List<String>,
)

/** 올해 지출을 게임 상위 5 + "기타"로 묶어 월별 누적 막대 데이터를 만든다. 올해 기록이 없으면 null. */
internal fun computeMonthlyTrend(spendings: List<Spending>, year: Int): MonthlyTrend? {
    val yearItems = spendings.filter { DateUtil.isSameYear(it.dateMillis, year) }
    if (yearItems.isEmpty()) return null
    val topGames = yearItems.groupBy { it.gameName }.mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }.take(5).map { it.key }
    val hasEtc = yearItems.any { it.gameName !in topGames }
    // month(0..11) -> 게임키 -> 금액
    val monthGame = Array(12) { LinkedHashMap<String, Long>() }.also { arr ->
        yearItems.forEach { s ->
            val m = DateUtil.month(s.dateMillis) - 1
            val key = if (s.gameName in topGames) s.gameName else "기타"
            arr[m][key] = (arr[m][key] ?: 0L) + s.amount
        }
    }
    val monthTotals = LongArray(12) { monthGame[it].values.sum() }
    val maxMonth = (monthTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val legend = topGames + if (hasEtc) listOf("기타") else emptyList()
    return MonthlyTrend(topGames, monthGame, maxMonth, legend)
}

/** 결제수단별 (이름, 합계, 전체합) — 합계 내림차순. 빈 결제수단은 "기타"로 묶음. */
internal fun computePaymentBreakdown(spendings: List<Spending>): List<Triple<String, Long, Long>> {
    val total = spendings.sumOf { it.amount }
    return spendings.groupBy { it.paymentMethod.ifBlank { "기타" } }
        .map { Triple(it.key, it.value.sumOf { s -> s.amount }, total) }
        .sortedByDescending { it.second }
}

/** 태그별 (태그, 합계) — 상위 8, 합계 내림차순. 여러 태그가 달린 지출은 중복 집계. */
internal fun computeTagBreakdown(spendings: List<Spending>): List<Pair<String, Long>> {
    val m = LinkedHashMap<String, Long>()
    spendings.forEach { s -> s.tags.forEach { t -> m[t] = (m[t] ?: 0L) + s.amount } }
    return m.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
}
