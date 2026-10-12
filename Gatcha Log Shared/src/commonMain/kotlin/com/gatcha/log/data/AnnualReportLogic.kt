package com.gatcha.log.data

/**
 * 연간 리포트의 월 한 칸.
 *
 * @param month 1~12
 * @param barRatio 막대 높이 비율(0~1) — 그 해에서 가장 많이 쓴 달이 1. 금액이 있으면 [AnnualReportLogic.MIN_BAR_RATIO]
 *   아래로 내려가지 않는다(가장 큰 달의 1% 만 쓴 달도 "썼다"는 것은 보여야 한다). 금액이 0 이면 0 — 막대를 그리지 않는다.
 */
data class AnnualMonthBar(
    val month: Int,
    val amount: Long,
    val barRatio: Double,
)

/**
 * 연간 리포트의 게임 한 줄.
 *
 * @param ratio 그 해 총 지출 대비 비율(0~1). 총 지출이 0 이면 0
 */
data class AnnualGameShare(
    val game: String,
    val amount: Long,
    val ratio: Double,
)

/**
 * 연간 리포트 한 화면 분량의 집계 — [AnnualReportLogic.report] 결과.
 *
 * @param years 연도 칩 목록(최근 해가 앞). 기록이 있는 해 + 올해
 * @param year 실제로 집계한 해. 고른 해가 [years] 에 없으면 가장 최근 해로 바뀐다
 * @param count 그 해 기록 수
 * @param months 1월 → 12월, 항상 12칸
 * @param monthlyAverage 월 평균 — 나누는 달 수는 [AnnualReportLogic.report] 설명 참고
 * @param byGame 금액 내림차순
 */
data class AnnualReport(
    val years: List<Int>,
    val year: Int,
    val count: Int,
    val total: Long,
    val months: List<AnnualMonthBar>,
    val monthlyAverage: Long,
    val byGame: List<AnnualGameShare>,
)

/**
 * 연간 리포트(지출 인사이트의 「연간」 탭)의 집계 — Android · iOS 가 이 한 소스를 쓴다(27.52.0).
 *
 * 그 전엔 Compose `AnnualReportContent` 와 SwiftUI `AnnualReportContent.compute` 가 같은 계산을 따로 갖고 있었다.
 * 숫자는 같았지만 **금액이 같은 게임의 순서**가 갈렸다 — Android 는 먼저 기록된 게임이 위였고, iOS 는 Dictionary 를
 * 돌려 정렬해서 순서가 실행마다 달라질 수 있었다. 여기서는 먼저 기록된 게임이 위다([report]).
 *
 * [SpendingInsightStats] 와 나눈 이유 — 그쪽은 「월간」 탭의 카드별 집계고, 여기는 연도 칩 하나에 딸린
 * 화면 전체를 한 번에 낸다(연도 목록 · 합계 · 월별 · 게임별이 같은 순회에서 나온다).
 */
object AnnualReportLogic {

    /** 금액이 있는 달의 막대 최소 높이 비율. */
    const val MIN_BAR_RATIO = 0.05

    /**
     * 연도 칩 목록 — 기록이 있는 해에 **올해([displayYear])를 더해** 최근 해부터.
     *
     * 올해를 늘 넣는 이유: 기록이 한 건도 없는 새해 첫날에도 리포트는 올해를 먼저 보여야 한다.
     */
    fun years(spendings: List<Spending>, displayYear: Int): List<Int> =
        yearsOf(yearMonthKeys(spendings), displayYear)

    /**
     * [selectedYear] 한 해의 리포트.
     *
     * - **고른 해가 목록에 없으면 가장 최근 해**로 집계한다 — 아직 고르지 않았거나(0), 그 해 기록을 전부 지운 경우다.
     *   없는 해를 그대로 집계하면 칩 어느 것도 선택되지 않은 채 0원 화면이 남는다.
     * - **월 평균의 분모** — 올해면 지나온 달 수([displayMonth], 이번 달 포함), 다른 해면 **기록이 있는 달 수**(최소 1).
     *   올해를 12로 나누면 3월에 본 평균이 4분의 1로 줄고, 지난 해를 12로 나누면 그 해 중간에 앱을 쓰기 시작한
     *   사람의 평균이 실제보다 작아진다.
     * - **게임 순서** — 금액 내림차순, 같으면 **먼저 기록된 게임**이 위.
     *
     * [displayYear] · [displayMonth] 는 '오늘' 의존이라 호출부가 넘긴다.
     */
    fun report(
        spendings: List<Spending>,
        selectedYear: Int,
        displayYear: Int,
        displayMonth: Int,
    ): AnnualReport {
        // 시각→로컬 변환은 항목당 한 번만 한다. 연도 목록을 먼저 알아야 집계할 해가 정해지므로 키를 들고 두 번 돈다.
        val keys = yearMonthKeys(spendings)
        val years = yearsOf(keys, displayYear)
        val year = if (selectedYear in years) selectedYear else years.first()

        var count = 0
        var total = 0L
        val monthly = LongArray(12)
        val byGame = LinkedHashMap<String, Long>()
        for (i in spendings.indices) {
            if (keys[i] / 100 != year) continue
            val s = spendings[i]
            count++
            total += s.amount
            val m = keys[i] % 100
            if (m in 1..12) monthly[m - 1] += s.amount
            byGame[s.gameName] = (byGame[s.gameName] ?: 0L) + s.amount
        }

        val maxMonth = (monthly.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val divisor = if (year == displayYear) displayMonth else monthly.count { it > 0 }.coerceAtLeast(1)

        return AnnualReport(
            years = years,
            year = year,
            count = count,
            total = total,
            months = monthly.mapIndexed { i, amount ->
                AnnualMonthBar(
                    month = i + 1,
                    amount = amount,
                    barRatio = if (amount > 0) {
                        (amount.toDouble() / maxMonth).coerceIn(0.0, 1.0).coerceAtLeast(MIN_BAR_RATIO)
                    } else 0.0,
                )
            },
            monthlyAverage = if (divisor > 0) total / divisor else 0L,
            // LinkedHashMap 이라 첫 등장 순서가 남고 sortedByDescending 은 안정 정렬이다 — 동률은 먼저 기록된 게임이 위.
            byGame = byGame.entries
                .sortedByDescending { it.value }
                .map { AnnualGameShare(it.key, it.value, if (total > 0) it.value.toDouble() / total else 0.0) },
        )
    }

    /** 지출마다의 연월 키([DateUtil.yearMonthKey], 202607) — 연은 `/ 100`, 월은 `% 100`. */
    private fun yearMonthKeys(spendings: List<Spending>): IntArray =
        IntArray(spendings.size) { DateUtil.yearMonthKey(spendings[it].dateMillis) }

    private fun yearsOf(keys: IntArray, displayYear: Int): List<Int> {
        val set = HashSet<Int>()
        set.add(displayYear)
        for (k in keys) set.add(k / 100)
        return set.sortedDescending()
    }
}
