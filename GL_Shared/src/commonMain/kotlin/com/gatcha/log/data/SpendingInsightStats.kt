package com.gatcha.log.data

import kotlin.math.abs

/**
 * 지출 인사이트 신규 카드(v27.32.0)의 공유 순수 집계 — 양 플랫폼(Compose·SwiftUI) 공유로 수치 드리프트 방지.
 * 기존 결제수단·태그·월추이 집계는 GL_Android 전용(SpendingInsightLogic)이라, 신규분만 여기 commonMain 에 둔다.
 */

/** 결제수단·플랫폼 비중 한 줄 (이름, 합계, 전체합). 비율은 UI 에서 amount/total. */
data class BreakdownSlice(val name: String, val amount: Long, val total: Long)

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

object SpendingInsightStats {

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

        var topGame = ""
        var topDelta = 0L
        (thisItems.map { it.gameName } + lastItems.map { it.gameName }).toSet().forEach { g ->
            val d = thisItems.filter { it.gameName == g }.sumOf { it.amount } -
                lastItems.filter { it.gameName == g }.sumOf { it.amount }
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
