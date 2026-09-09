package com.gatcha.log.data

/**
 * 지출에서 나오는 파생값을 **한 번의 순회**로 만드는 순수 계산.
 *
 * 왜 따로 뺐나 — [SpendingViewModel.recomputeSpendingDerived] 가 지출 전체를 **8번** 훑고 있었다.
 * 이번 달 합계 · 전월 합계 · 게임별 합계 · 최근 N개월 · 미등록 구독 건수를 각자 계산했고,
 * 그 하나하나가 다시 `isSameMonth`(시각→로컬 변환)를 항목마다 불렀다. 지출 1,000건이면 저장 한 번에
 * 날짜 변환이 8,000회 근처였고, 이게 **지출·정기결제가 바뀔 때마다** 돌았다.
 *
 * `Challenge.evaluate` 가 같은 문제를 [DateUtil.yearMonthKey] 한 번의 순회로 이미 해결해 뒀다.
 * 여기서도 같은 방식을 쓴다.
 *
 * ViewModel 이 아니라 이 자리에 두는 이유는 하나 더 있다 — [SpendingViewModel] 은 플랫폼 저장소에
 * 의존해서 commonTest 로 검증할 수 없다. 계산만 순수 함수로 떼어 두면 회귀를 테스트로 잡을 수 있다.
 */
object SpendingDerived {

    /** [compute] 결과 묶음. 필드는 전부 [SpendingViewModel] 의 동명 StateFlow 에 그대로 들어간다. */
    data class Derived(
        /** 이번 달 총 지출. */
        val currentMonthTotal: Long,
        /** 전월 총 지출(MoM 비교용). 1월이면 전년 12월. */
        val previousMonthTotal: Long,
        /** 이번 달 게임별 합계(gameKey → 금액). 미등록 게임명은 이름 그대로 키가 된다. */
        val currentMonthTotalsByGame: Map<String, Long>,
        /** 최근 N개월 총 지출 — **오래된 달 → 이번 달** 순. */
        val recentMonthlyTotals: List<Long>,
        )

    /**
     * 파생값 전량을 산출한다.
     *
     * [year]·[month] 는 '오늘' 의존이라 호출부에서 주입한다(테스트 가능성 + 자정 경계 흔들림 방지).
     * [recentMonths] 는 [recentMonthlyTotals] 의 길이.
     */
    fun compute(
        spendings: List<Spending>,
        year: Int,
        month: Int,
        recentMonths: Int,
    ): Derived {
        val curKey = year * 100 + month
        val prevKey = if (month == 1) (year - 1) * 100 + 12 else year * 100 + (month - 1)

        // 최근 N개월의 연월 키 → 결과 배열 인덱스. 오래된 달이 0번.
        // (예전엔 지출 1건마다 months.indexOfFirst 로 N칸을 선형 탐색했다)
        val recentIndex = HashMap<Int, Int>(recentMonths)
        for (i in 0 until recentMonths) {
            val back = recentMonths - 1 - i
            var y = year
            var m = month - back
            while (m <= 0) { m += 12; y -= 1 }
            recentIndex[y * 100 + m] = i
        }

        var currentTotal = 0L
        var previousTotal = 0L
        val byGame = LinkedHashMap<String, Long>()
        val recent = LongArray(recentMonths)

        // ── 단 한 번의 순회. 항목당 날짜 변환도 1회(yearMonthKey).
        spendings.forEach { s ->
            val k = DateUtil.yearMonthKey(s.dateMillis)
            if (k == curKey) {
                currentTotal += s.amount
                val gameKey = GameData.byNameOrNull(s.gameName)?.key ?: s.gameName
                byGame[gameKey] = (byGame[gameKey] ?: 0L) + s.amount
            }
            if (k == prevKey) previousTotal += s.amount
            recentIndex[k]?.let { recent[it] += s.amount }
        }

        return Derived(
            currentMonthTotal = currentTotal,
            previousMonthTotal = previousTotal,
            currentMonthTotalsByGame = byGame,
            recentMonthlyTotals = recent.toList(),
        )
    }

    // '구독 등록 후보'(unlinkedSubscriptions·subscriptionName·hasMatch)는 여기 있었다 —
    // 정기결제를 기능째 걷어내며(2026-09-09) 함께 지웠다.

}
