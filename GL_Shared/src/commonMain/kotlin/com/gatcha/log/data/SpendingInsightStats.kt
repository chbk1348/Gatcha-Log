package com.gatcha.log.data

import kotlin.math.abs

/**
 * 지출 인사이트의 공유 순수 집계 — 양 플랫폼(Compose·SwiftUI) 공유로 수치 드리프트 방지.
 *
 * v27.32.0 에는 신규 카드분만 여기 있었고 결제수단·태그·월추이는 GL_Android 전용
 * (ui/spending/SpendingInsightLogic.kt)이라 iOS 가 같은 계산을 Swift 로 따로 갖고 있었다.
 * 이제 전부 이 파일 하나로 모아 두 플랫폼이 동일 함수를 호출한다.
 */

/** 결제수단·플랫폼 비중 한 줄 (이름, 합계, 막대 분모). 비율은 UI 에서 amount/total. */
data class BreakdownSlice(val name: String, val amount: Long, val total: Long)

/** 예산 페이스 예측 결과. dayOfMonth/daysInMonth 는 '오늘' 의존이라 호출부에서 주입. */
data class BudgetPace(
    val projected: Long,
    val dailyAvg: Long,
    val remainingDays: Int,
)

/** 게임별 월 추이(올해, 누적 막대) — 색/렌더 제외 데이터. */
data class MonthlyTrend(
    /** 올해 지출 상위 5개 게임명. */
    val topGames: List<String>,
    /** index=month(0..11), 값 = 게임명/"기타" -> 금액 (삽입 순서 유지). */
    val monthGame: List<Map<String, Long>>,
    /** 가장 큰 달의 합계(막대 스케일 분모, 최소 1). */
    val maxMonth: Long,
    /** 범례·막대 적층 순서(상위 게임 + 필요 시 "기타"). */
    val legend: List<String>,
)

/** 전월 대비. [percent] = -1 이면 지난달 0(신규, 률 표시 생략). [delta]<0 = 지출 감소(좋음). */
data class MoMComparison(
    val thisMonth: Long,
    val lastMonth: Long,
    val delta: Long,
    val percent: Int,
    val topGame: String,       // 증감 절대값 최대 게임(없으면 "")
    val topGameDelta: Long,
)

/** 결제 통계(특정 월). [topWeekday] 빈 문자열이면 데이터 없음. */
data class PaymentStats(
    val count: Int,
    val average: Long,
    val maxAmount: Long,
    val topWeekday: String,
)

/** 예상 지출 산정 시 신뢰할 최소 경과일(워밍업). 월초엔 표본이 적어 run-rate가 크게 증폭되므로 분모에 하한을 둔다. */
private const val PACE_WARMUP_DAYS = 7

object SpendingInsightStats {

    /**
     * 경과 일수 기준 선형 추정(이번 달 예상 지출·하루 평균·남은 일수).
     * 월말 예상은 워밍업(7일) 완화 적용: 경과일이 7일 미만이면 분모를 7로 하한 처리해 월초 과대추정을 억제한다.
     * (배율 = daysInMonth / max(dayOfMonth, 7) ≥ 1 이므로 예상값은 항상 현재 지출 이상. 7일 경과 후엔 순수 run-rate와 동일)
     */
    fun budgetPace(monthTotal: Long, dayOfMonth: Int, daysInMonth: Int): BudgetPace {
        val effectiveDays = maxOf(dayOfMonth, minOf(daysInMonth, PACE_WARMUP_DAYS))
        val projected = if (dayOfMonth > 0) monthTotal * daysInMonth / effectiveDays else monthTotal
        val dailyAvg = if (dayOfMonth > 0) monthTotal / dayOfMonth else 0L
        val remainingDays = (daysInMonth - dayOfMonth).coerceAtLeast(0)
        return BudgetPace(projected, dailyAvg, remainingDays)
    }

    /** 올해 지출을 게임 상위 5 + "기타"로 묶어 월별 누적 막대 데이터를 만든다. 올해 기록이 없으면 null. */
    fun monthlyTrend(spendings: List<Spending>, year: Int): MonthlyTrend? {
        val yearItems = spendings.filter { DateUtil.isSameYear(it.dateMillis, year) }
        if (yearItems.isEmpty()) return null
        val topGames = yearItems.groupBy { it.gameName }.mapValues { e -> e.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(5).map { it.key }
        val hasEtc = yearItems.any { it.gameName !in topGames }
        // month(0..11) -> 게임키 -> 금액
        val monthGame = List(12) { LinkedHashMap<String, Long>() }.also { arr ->
            yearItems.forEach { s ->
                val m = DateUtil.month(s.dateMillis) - 1
                if (m in 0..11) {
                    val key = if (s.gameName in topGames) s.gameName else "기타"
                    arr[m][key] = (arr[m][key] ?: 0L) + s.amount
                }
            }
        }
        val maxMonth = (monthGame.maxOfOrNull { m -> m.values.sum() } ?: 0L).coerceAtLeast(1L)
        val legend = topGames + if (hasEtc) listOf("기타") else emptyList()
        return MonthlyTrend(topGames, monthGame, maxMonth, legend)
    }

    /** 결제수단별 비중(전체 기간) — 합계 내림차순, 빈 결제수단은 "기타". total=전체합. */
    fun paymentBreakdown(spendings: List<Spending>): List<BreakdownSlice> {
        val total = spendings.sumOf { it.amount }
        return spendings.groupBy { it.paymentMethod.ifBlank { "기타" } }
            .map { e -> BreakdownSlice(e.key, e.value.sumOf { it.amount }, total) }
            .sortedByDescending { it.amount }
    }

    /**
     * 태그별 지출 — 상위 8, 합계 내림차순. 태그명에 "#" 는 붙이지 않는다(표시 시 각 UI 가 붙임).
     * 여러 태그가 달린 지출은 중복 집계되어 합계 비율이 100%를 넘을 수 있으므로,
     * [BreakdownSlice.total] 은 전체합이 아니라 **최대 태그 금액**(막대 분모)을 담는다.
     */
    fun tagBreakdown(spendings: List<Spending>): List<BreakdownSlice> {
        val m = LinkedHashMap<String, Long>()
        spendings.forEach { s -> s.tags.forEach { t -> m[t] = (m[t] ?: 0L) + s.amount } }
        val top = m.entries.sortedByDescending { it.value }.take(8)
        val maxTag = (top.firstOrNull()?.value ?: 1L).coerceAtLeast(1L)
        return top.map { BreakdownSlice(it.key, it.value, maxTag) }
    }

    /** 충전 플랫폼별 비중(전체 기간) — 합계 내림차순, 빈 값은 "기타". (기존 결제수단 비중과 동일 규칙) */
    fun platformBreakdown(spendings: List<Spending>): List<BreakdownSlice> {
        val total = spendings.sumOf { it.amount }
        return spendings.groupBy { it.chargePlatform.ifBlank { "기타" } }
            .map { e -> BreakdownSlice(e.key, e.value.sumOf { it.amount }, total) }
            .sortedByDescending { it.amount }
    }

    /** 전월 대비 — (year, month) 이번 달 vs 직전 달. */
    fun momComparison(spendings: List<Spending>, year: Int, month: Int): MoMComparison {
        val py = if (month <= 1) year - 1 else year
        val pm = if (month <= 1) 12 else month - 1
        val thisItems = spendings.filter { DateUtil.isSameMonth(it.dateMillis, year, month) }
        val lastItems = spendings.filter { DateUtil.isSameMonth(it.dateMillis, py, pm) }
        val thisTotal = thisItems.sumOf { it.amount }
        val lastTotal = lastItems.sumOf { it.amount }
        val delta = thisTotal - lastTotal
        val percent = if (lastTotal > 0) ((delta * 100) / lastTotal).toInt() else -1

        // 게임별 합계를 각 달에 한 번씩만 만든다. 예전엔 게임 1종마다 두 달치를 통째로 다시
        // 필터해서 O(게임수 × 지출수) 였다(게임 10종·1,000건이면 20,000회 순회).
        // groupingBy 는 LinkedHashMap 이라 첫 등장 순서가 유지된다 — 아래 동점 처리가 그 순서에 기댄다.
        val thisByGame = thisItems.groupingBy { it.gameName }.fold(0L) { acc, s -> acc + s.amount }
        val lastByGame = lastItems.groupingBy { it.gameName }.fold(0L) { acc, s -> acc + s.amount }

        var topGame = ""
        var topDelta = 0L
        // 이번 달 등장 순 → 지난달에만 있던 게임 순. 증감 절대값이 같으면 **먼저 나온 게임**이 이긴다(strict >).
        (thisByGame.keys + lastByGame.keys).forEach { g ->
            val d = (thisByGame[g] ?: 0L) - (lastByGame[g] ?: 0L)
            if (abs(d) > abs(topDelta)) { topDelta = d; topGame = g }
        }
        return MoMComparison(thisTotal, lastTotal, delta, percent, topGame, topDelta)
    }

    /** 결제 통계 — (year, month) 기준 건수·평균·최고 단건·최다 결제 요일. */
    fun paymentStats(spendings: List<Spending>, year: Int, month: Int): PaymentStats {
        val items = spendings.filter { DateUtil.isSameMonth(it.dateMillis, year, month) }
        if (items.isEmpty()) return PaymentStats(0, 0, 0, "")
        val total = items.sumOf { it.amount }
        val topWeekday = items.groupBy { DateUtil.weekdayKo(it.dateMillis) }
            .maxByOrNull { it.value.size }?.key ?: ""
        return PaymentStats(items.size, total / items.size, items.maxOf { it.amount }, topWeekday)
    }
}
