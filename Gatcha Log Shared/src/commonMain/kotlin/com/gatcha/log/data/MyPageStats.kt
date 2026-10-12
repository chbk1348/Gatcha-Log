package com.gatcha.log.data

import com.gatcha.log.util.percentShares
import kotlin.math.abs

/** 누적 지출과 이번 달 기록 수 — 지출 전체를 한 번 훑어 같이 낸다. */
data class MyPageTotals(
    val total: Long,
    val monthCount: Int,
)

/**
 * 예산 진행.
 *
 * @param percent 예산 대비 쓴 비율(%). 내림 — 99.9% 를 100% 로 올려 "다 썼다"고 말하지 않는다
 * @param ratio 진행 막대 채움(0~1). 넘겼으면 1
 * @param gap 예산과의 차이(항상 0 이상) — [over] 면 넘긴 금액, 아니면 남은 금액
 */
data class MyPageBudget(
    val over: Boolean,
    val percent: Int,
    val ratio: Double,
    val gap: Long,
)

/**
 * 최근 N개월 막대의 한 칸.
 *
 * @param month 1~12
 * @param barRatio 막대 높이 비율(0~1) — 구간에서 가장 많이 쓴 달이 1. 막대의 최소 높이는 화면이 정한다
 */
data class MyPageMonthPoint(
    val year: Int,
    val month: Int,
    val amount: Long,
    val barRatio: Double,
) {
    /** 연·월을 한 값으로(202607) — 목록에서 칸을 가리는 고유값. 월만 쓰면 13개월 이상일 때 겹친다. */
    val yearMonth: Int get() = year * 100 + month
}

/**
 * 최근 N개월 추이 — 막대와 「지출 기록」 섹션이 같은 값을 쓴다.
 *
 * @param points 오래된 달 → 이번 달
 * @param monthlyAverage 구간 합계 ÷ **구간의 달 수**(기록이 없는 달도 센다). 구간이 비면 0
 * @param peak 가장 많이 쓴 달. 구간 전체가 0 이면 null
 */
data class MyPageTrend(
    val points: List<MyPageMonthPoint>,
    val monthlyAverage: Long,
    val peak: MyPageMonthPoint?,
)

/**
 * 게임별 지출의 한 조각.
 *
 * @param color 게임 대표색 ARGB(0xAARRGGBB). [isEtc] 면 0 — 「기타」 색은 화면이 정한다
 * @param percent 전체 대비 비중(%). 조각들의 합이 정확히 100 이다
 * @param isEtc 상위 [MyPageStats.TOP_GAMES]개 밖을 묶은 조각인가
 */
data class MyPageGameSlice(
    val game: String,
    val amount: Long,
    val color: Long,
    val percent: Int,
    val isEtc: Boolean,
)

/** 게임별 지출 — [slices] 는 금액 내림차순(「기타」는 늘 맨 끝), [total] 은 전체 기간 합계. */
data class MyPageGameShare(
    val slices: List<MyPageGameSlice>,
    val total: Long,
)

/**
 * 마이페이지의 지출 · 활동 통계 — Android · iOS 가 이 한 소스를 쓴다(27.52.0).
 *
 * 그 전엔 Compose `MyPageScreen` 과 SwiftUI `MyPageView` 가 같은 계산을 따로 갖고 있었고, 두 군데가 실제로 갈렸다.
 * - 「지난달보다 N%」 — Android 는 Float, iOS 는 Double 로 나눠 버려 1% 씩 달랐다 → [SpendingInsightStats.momPercent].
 * - 금액이 같은 게임의 순서 — iOS 는 Dictionary 를 돌려 정렬해 실행마다 달라질 수 있었다 → [gameShare].
 *
 * 이번 달 · 전월 · 최근 N개월 **합계**는 여기서 다시 세지 않는다 — [SpendingDerived] 가 지출을 한 번 훑어
 * 만들어 둔 값을 호출부가 그대로 넘긴다.
 *
 * 출석 칸의 「전체 출석」 판정은 [AttendanceLogic.dayLevel] 몫이다.
 */
object MyPageStats {

    /** 게임별 지출에서 따로 보여 주는 게임 수. 그 밖은 「기타」 한 조각으로 묶는다. */
    const val TOP_GAMES = 5

    /** 묶은 조각의 이름 — 게임별 월 추이([SpendingInsightStats.monthlyTrend])와 같은 말을 쓴다. */
    const val ETC_LABEL = "기타"

    /** 누적 지출과 이번 달([nowMillis] 가 속한 달) 기록 수. */
    fun totals(spendings: List<Spending>, nowMillis: Long): MyPageTotals {
        val ym = DateUtil.yearMonthKey(nowMillis)
        var sum = 0L
        var n = 0
        for (s in spendings) {
            sum += s.amount
            if (DateUtil.yearMonthKey(s.dateMillis) == ym) n++
        }
        return MyPageTotals(sum, n)
    }

    /**
     * 이번 달 일 평균 — 이번 달 합계 ÷ **오늘까지 지난 날 수**(오늘 포함).
     *
     * 그 달의 날 수(30 · 31)로 나누지 않는다 — 월초에 평균이 실제보다 훨씬 작게 나온다.
     */
    fun dailyAverage(monthTotal: Long, nowMillis: Long): Long =
        monthTotal / DateUtil.dayOfMonth(nowMillis).coerceAtLeast(1)

    /**
     * 예산 진행 — 초과 판정과 퍼센트는 홈 예산 알림([HomeLogic])과 같은 식이다(넘김 = 합계가 예산보다 **클 때**, 퍼센트는 내림).
     *
     * 예산이 0 이하(미설정)면 전부 0 — 화면은 이때 예산 줄 자체를 그리지 않는다.
     */
    fun budget(monthTotal: Long, budget: Long): MyPageBudget {
        if (budget <= 0L) return MyPageBudget(over = false, percent = 0, ratio = 0.0, gap = 0L)
        val over = monthTotal > budget
        return MyPageBudget(
            over = over,
            percent = (monthTotal * 100 / budget).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            ratio = if (over) 1.0 else (monthTotal.toDouble() / budget).coerceIn(0.0, 1.0),
            gap = abs(budget - monthTotal),
        )
    }

    /**
     * 최근 N개월 추이 — [recentTotals] 는 [SpendingDerived.Derived.recentMonthlyTotals](오래된 달 → 이번 달).
     * 맨 끝 칸이 [nowMillis] 가 속한 달이고, 거기서 한 달씩 거슬러 연 · 월을 붙인다(1월 앞은 전년 12월).
     *
     * 가장 많이 쓴 달이 둘 이상이면 **더 오래된 달**을 고른다.
     */
    fun trend(recentTotals: List<Long>, nowMillis: Long): MyPageTrend {
        val nowKey = DateUtil.yearMonthKey(nowMillis)
        val maxAmount = (recentTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val points = recentTotals.mapIndexed { i, amount ->
            var y = nowKey / 100
            var m = nowKey % 100 - (recentTotals.size - 1 - i)
            while (m <= 0) { m += 12; y -= 1 }
            MyPageMonthPoint(y, m, amount, (amount.toDouble() / maxAmount).coerceIn(0.0, 1.0))
        }
        return MyPageTrend(
            points = points,
            monthlyAverage = if (points.isEmpty()) 0L else recentTotals.sum() / points.size,
            // maxByOrNull 은 같은 값이면 앞선 것을 돌려준다 — 오래된 달이 앞이다.
            peak = points.maxByOrNull { it.amount }?.takeIf { it.amount > 0 },
        )
    }

    /**
     * 게임별 지출(전체 기간) — 금액 내림차순으로 상위 [TOP_GAMES]개, 그보다 많으면 나머지를 [ETC_LABEL] 한 조각으로.
     *
     * - 금액이 같으면 **먼저 기록된 게임**이 위. 상위 5개의 경계에서 동률이면 누가 「기타」로 들어가는지도 이 순서가 정한다.
     * - 색은 그 게임의 **첫 기록**에 저장된 색([Spending.gameColor]).
     * - 퍼센트는 [percentShares](최대 잔여법) — 조각마다 따로 내림하면 합이 97% 처럼 모자란다.
     */
    fun gameShare(spendings: List<Spending>): MyPageGameShare {
        val sums = LinkedHashMap<String, Long>()
        val colors = HashMap<String, Long>()
        for (s in spendings) {
            if (s.gameName !in sums) colors[s.gameName] = s.gameColor
            sums[s.gameName] = (sums[s.gameName] ?: 0L) + s.amount
        }
        // LinkedHashMap 이라 첫 등장 순서가 남고 sortedByDescending 은 안정 정렬이다 — 동률은 먼저 기록된 게임이 위.
        val sorted = sums.entries.sortedByDescending { it.value }
        val names = sorted.take(TOP_GAMES).map { it.key }
        val amounts = sorted.take(TOP_GAMES).map { it.value }.toMutableList()
        val hasEtc = sorted.size > TOP_GAMES
        if (hasEtc) amounts += sorted.drop(TOP_GAMES).sumOf { it.value }

        val percents = percentShares(amounts)
        val slices = amounts.mapIndexed { i, amount ->
            val isEtc = hasEtc && i == amounts.lastIndex
            MyPageGameSlice(
                game = if (isEtc) ETC_LABEL else names[i],
                amount = amount,
                color = if (isEtc) 0L else colors[names[i]] ?: 0L,
                percent = percents[i],
                isEtc = isEtc,
            )
        }
        return MyPageGameShare(slices, amounts.sum())
    }

    /**
     * 일일 숙제 완주율(%) — **기록이 있는 게임들**의 완주율 평균(내림). 그런 게임이 없으면 null.
     *
     * 기록이 없는 게임([TaskStats.dailyDays] = 0)은 완주율이 0 으로 들어 있어, 같이 평균 내면
     * 막 연동한 게임 하나가 전체를 끌어내린다.
     */
    fun dailyTaskRate(taskStats: List<TaskStats>): Int? {
        val counted = taskStats.filter { it.dailyDays > 0 }
        if (counted.isEmpty()) return null
        return counted.sumOf { it.dailyRate } / counted.size
    }
}
