package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis

/** 진행 중 챌린지 한 건의 상태(결정형 룰로 지출·예산에서 파생). */
data class ChallengeProgress(
    val id: String,
    val title: String,
    val desc: String,
    val current: Long,
    val target: Long,
    val ratio: Float,       // 0..1 (진행바)
    val reached: Boolean,   // 현재 달성/온트랙
    val warn: Boolean = false, // 예산 임박·초과 등 주의
)

/** 배지 한 개(획득 여부). 아이콘은 emoji 로 표기 — UI에서 실제 에셋으로 교체 가능. */
data class BadgeState(
    val id: String,
    val emoji: String,
    val title: String,
    val earned: Boolean,
)

/** 절약 챌린지 화면 전체 상태. */
data class ChallengeSummary(
    val noSpendStreak: Int,
    val bestStreak: Int,
    val challenges: List<ChallengeProgress>,
    val badges: List<BadgeState>,
) {
    val earnedBadgeCount: Int get() = badges.count { it.earned }
    val totalBadgeCount: Int get() = badges.size
}

/**
 * 절약 챌린지·스트릭·배지 판정 엔진. 전부 지출 기록·예산에서 파생하는 **결정형 룰**(AI 없음).
 * - 무지출 스트릭: 오늘부터 연속으로 지출이 없는 로컬 날짜 수(오늘 지출 시 0).
 * - 배지: 조건 충족 시 획득. 획득 상태는 [earnedStored] 합집합으로 **단조 증가**(끊겨도 유지) — VM에서 영속.
 */
object SavingsChallenge {

    // 배지 정의 (id, emoji, 제목)
    const val B_FIRST = "first_save"
    const val B_NOSPEND_7 = "nospend_7"
    const val B_BUDGET = "budget_hit"
    const val B_NOSPEND_30 = "nospend_30"
    const val B_BUDGET_3MO = "budget_3mo"
    const val B_NOSPEND_MONTH = "nospend_month"
    const val B_SAVE_3MO = "save_3mo"
    const val B_KING = "king"

    private data class BadgeDef(val id: String, val emoji: String, val title: String)
    private val catalog = listOf(
        BadgeDef(B_FIRST, "🌱", "첫 절약"),
        BadgeDef(B_NOSPEND_7, "🔥", "무지출 7일"),
        BadgeDef(B_BUDGET, "🎯", "예산 달성"),
        BadgeDef(B_NOSPEND_30, "💎", "무지출 30일"),
        BadgeDef(B_BUDGET_3MO, "🏆", "3개월 예산"),
        BadgeDef(B_NOSPEND_MONTH, "🧊", "한 달 무지출"),
        BadgeDef(B_SAVE_3MO, "📉", "3개월 절약"),
        BadgeDef(B_KING, "👑", "절약왕"),
    )

    private const val STREAK_CAP = 366

    fun evaluate(
        spendings: List<Spending>,
        budget: Long,
        bestStreakStored: Int,
        earnedStored: Set<String>,
        nowMillis: Long = currentTimeMillis(),
    ): ChallengeSummary {
        val spentDays: Set<String> = spendings.map { it.dayKey }.toSet()

        // ── 무지출 스트릭(오늘부터 역방향, 지출 없는 로컬 날짜 연속). 기록이 아예 없으면 0.
        val streak = if (spendings.isEmpty()) 0 else {
            var s = 0
            while (s < STREAK_CAP && DateUtil.localDayKeyAgo(s, nowMillis) !in spentDays) s++
            s
        }
        val best = maxOf(bestStreakStored, streak)

        // ── 월별 합계를 **한 번의 순회**로 만든다.
        //
        // 예전엔 추적 월마다 지출 전체를 다시 필터했다(monthTotal·prevTotal 까지 하면 월 수 + 2회).
        // isSameMonth 한 번이 시각→로컬 변환을 두 번 하므로, 12개월치를 보는 것만으로도
        // 지출 500건 기준 변환이 만 번 단위로 불어났다. 지출을 저장할 때마다 이게 돌고 있었다.
        val totalsByYm = HashMap<Int, Long>()
        spendings.forEach { s ->
            val k = DateUtil.yearMonthKey(s.dateMillis)
            totalsByYm[k] = (totalsByYm[k] ?: 0L) + s.amount
        }
        fun totalOf(year: Int, month: Int): Long = totalsByYm[year * 100 + month] ?: 0L

        // ── 이번 달/전월 지출
        val y = DateUtil.year(nowMillis); val m = DateUtil.month(nowMillis)
        val monthTotal = totalOf(y, m)
        val (py, pm) = if (m == 1) (y - 1) to 12 else y to (m - 1)
        val prevTotal = totalOf(py, pm)

        // ── 완료된(지난) 추적 월들 — 첫 지출 월 ~ 전월. 예산·절약 배지 판정용.
        val months = trackedMonths(spendings, y, m)
        val monthTotals = months.map { totalOf(it.first, it.second) }

        val budgetAchievedAny = budget > 0 && monthTotals.isNotEmpty() &&
            months.indices.any { budget > 0 && monthTotals[it] <= budget }
        val budgetRun = if (budget <= 0) 0 else maxConsecutive(monthTotals.map { it <= budget })
        val zeroMonthAny = monthTotals.any { it == 0L } && months.isNotEmpty()
        val savingRun = maxDecreasingRun(monthTotals)

        // ── 활성 챌린지(이번 달 진행)
        val challenges = mutableListOf<ChallengeProgress>()
        // 1) 이번 주 무지출 N일 (최근 7일 중)
        val weekNoSpend = (0..6).count { DateUtil.localDayKeyAgo(it, nowMillis) !in spentDays }
        challenges += ChallengeProgress(
            id = "week_nospend",
            title = "이번 주 무지출 5일",
            desc = "최근 7일 중 5일 이상 안 쓰기",
            current = weekNoSpend.toLong(), target = 5,
            ratio = (weekNoSpend / 5f).coerceIn(0f, 1f),
            reached = weekNoSpend >= 5,
        )
        // 2) 이번 달 예산 안에서 (예산 설정 시에만)
        if (budget > 0) {
            challenges += ChallengeProgress(
                id = "month_budget",
                title = "이번 달 예산 안에서",
                desc = "월 예산 ₩${comma(budget)} 넘지 않기",
                current = monthTotal, target = budget,
                ratio = (monthTotal.toFloat() / budget).coerceIn(0f, 1f),
                reached = monthTotal <= budget,
                warn = monthTotal >= budget * 8 / 10,
            )
        }
        // 3) 지난달보다 덜 쓰기 (전월 지출 있을 때만)
        if (prevTotal > 0) {
            challenges += ChallengeProgress(
                id = "less_than_prev",
                title = "지난달보다 덜 쓰기",
                desc = "지난달 ₩${comma(prevTotal)} 대비 절약",
                current = monthTotal, target = prevTotal,
                ratio = (monthTotal.toFloat() / prevTotal).coerceIn(0f, 1f),
                reached = monthTotal < prevTotal,
            )
        }

        // ── 배지 판정(현재 충족) → 저장된 획득과 합집합(단조 증가)
        val freshly = buildSet {
            if (best >= 1) add(B_FIRST)
            if (best >= 7) add(B_NOSPEND_7)
            if (best >= 30) add(B_NOSPEND_30)
            if (budgetAchievedAny) add(B_BUDGET)
            if (budgetRun >= 3) add(B_BUDGET_3MO)
            if (zeroMonthAny) add(B_NOSPEND_MONTH)
            if (savingRun >= 3) add(B_SAVE_3MO)
        }
        val earned = (earnedStored + freshly).toMutableSet()
        // 절약왕 = 나머지 7종 모두 획득
        val others = catalog.map { it.id }.filter { it != B_KING }
        if (others.all { it in earned }) earned += B_KING

        val badges = catalog.map { BadgeState(it.id, it.emoji, it.title, it.id in earned) }

        return ChallengeSummary(streak, best, challenges, badges)
    }

    /** 획득한 배지 id 집합만 뽑기(영속용). */
    fun earnedIds(summary: ChallengeSummary): Set<String> =
        summary.badges.filter { it.earned }.map { it.id }.toSet()

    // ── 첫 지출 월부터 전월(exclusive current)까지 (year,month) 목록. 기록 없으면 빈 목록.
    private fun trackedMonths(spendings: List<Spending>, curY: Int, curM: Int): List<Pair<Int, Int>> {
        if (spendings.isEmpty()) return emptyList()
        val firstMillis = spendings.minOf { it.dateMillis }
        var yy = DateUtil.year(firstMillis); var mm = DateUtil.month(firstMillis)
        val out = mutableListOf<Pair<Int, Int>>()
        while ((yy < curY || (yy == curY && mm < curM)) && out.size < 600) {
            out += yy to mm
            mm++; if (mm > 12) { mm = 1; yy++ }
        }
        return out
    }

    /** 불리언 리스트에서 연속 true 최대 길이. */
    private fun maxConsecutive(flags: List<Boolean>): Int {
        var run = 0; var best = 0
        for (f in flags) { run = if (f) run + 1 else 0; if (run > best) best = run }
        return best
    }

    /** 월 합계 리스트에서 "각 달이 직전 달보다 작은" 연속 최대 길이(월 수 기준). */
    private fun maxDecreasingRun(totals: List<Long>): Int {
        if (totals.isEmpty()) return 0
        var run = 0; var best = 0; var prev: Long? = null
        for (t in totals) {
            run = if (prev != null && t < prev) run + 1 else 1
            if (run > best) best = run
            prev = t
        }
        return best
    }

    private fun comma(v: Long): String {
        val s = v.toString()
        val sb = StringBuilder()
        val n = s.length
        for (i in 0 until n) {
            if (i > 0 && (n - i) % 3 == 0) sb.append(',')
            sb.append(s[i])
        }
        return sb.toString()
    }
}
