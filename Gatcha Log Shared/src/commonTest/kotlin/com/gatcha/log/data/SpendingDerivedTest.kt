package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * 지출 파생값 단일 순회 산출 — [SpendingDerived].
 *
 * 이 계산은 원래 SpendingViewModel 안에 흩어져 있었고(전체를 8번 훑었다) 플랫폼 저장소 의존 때문에
 * 테스트가 한 건도 없었다. 순수 함수로 뺀 김에 **현재 동작을 여기 고정한다** —
 * 홈 히어로 금액·게임별 예산 경고·마이페이지 월별 추이·정기결제 '미등록 N건'이 전부 이 값을 쓰므로,
 * 어긋나면 "화면 숫자가 조용히 틀리는" 형태로만 드러난다.
 */
@OptIn(ExperimentalTime::class)
class SpendingDerivedTest {

    /** 로컬 타임존 정오 기준 epoch millis — 월 판정이 로컬 tz 라 경계에서 안전한 시각. */
    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDateTime(year, month, day, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    private fun spend(
        game: String = "원신",
        amount: Long,
        year: Int = 2026,
        month: Int,
        day: Int = 10,
        isSub: Boolean = false,
        itemName: String = "",
    ) = Spending(
        gameName = game, amount = amount, dateMillis = at(year, month, day),
        itemName = itemName, isSubscription = isSub,
    )

    private fun compute(
        spendings: List<Spending>,
        subscriptions: List<Subscription> = emptyList(),
        year: Int = 2026,
        month: Int = 7,
        recentMonths: Int = 6,
    ) = SpendingDerived.compute(spendings, subscriptions, year, month, recentMonths)

    // ── 이번 달 / 전월 ───────────────────────────────────────────────────────

    @Test
    fun currentAndPreviousMonthTotalsSplitByMonth() {
        val d = compute(
            listOf(
                spend(amount = 10_000, month = 7),
                spend(amount = 5_000, month = 7, day = 28),
                spend(amount = 3_000, month = 6),
                spend(amount = 999, month = 5),      // 둘 다 아님
            ),
        )
        assertEquals(15_000L, d.currentMonthTotal)
        assertEquals(3_000L, d.previousMonthTotal)
    }

    @Test
    fun previousMonthOfJanuaryIsPreviousYearDecember() {
        // 1월의 '전월'은 전년 12월 — 연 경계에서 가장 틀리기 쉬운 곳.
        val d = compute(
            listOf(
                spend(amount = 7_000, year = 2026, month = 1),
                spend(amount = 4_000, year = 2025, month = 12),
                spend(amount = 100, year = 2026, month = 12),  // 같은 해 12월은 전월이 아니다
            ),
            year = 2026, month = 1,
        )
        assertEquals(7_000L, d.currentMonthTotal)
        assertEquals(4_000L, d.previousMonthTotal)
    }

    @Test
    fun emptySpendingsYieldZeros() {
        val d = compute(emptyList())
        assertEquals(0L, d.currentMonthTotal)
        assertEquals(0L, d.previousMonthTotal)
        assertTrue(d.currentMonthTotalsByGame.isEmpty())
        assertEquals(List(6) { 0L }, d.recentMonthlyTotals)
        assertEquals(0, d.unlinkedSubCount)
    }

    // ── 게임별 합계 ──────────────────────────────────────────────────────────

    @Test
    fun gameTotalsUseGameKeyAndCoverCurrentMonthOnly() {
        val d = compute(
            listOf(
                spend(game = "원신", amount = 10_000, month = 7),
                spend(game = "원신", amount = 2_000, month = 7),
                spend(game = "붕괴: 스타레일", amount = 5_000, month = 7),
                spend(game = "원신", amount = 90_000, month = 6),   // 지난달 → 제외
            ),
        )
        // 키는 Game.key — 스타레일은 "hsr" 이다("starrail" 은 enneadKey/newsSlug 쪽 값).
        // 게임별 예산(gameBudgets)도 이 키를 쓰므로 여기가 어긋나면 한도 경고가 통째로 안 뜬다.
        assertEquals(mapOf("genshin" to 12_000L, "hsr" to 5_000L), d.currentMonthTotalsByGame)
    }

    @Test
    fun unknownGameNameFallsBackToTheNameItself() {
        // 게임 목록에 없는 이름(옛 데이터·오타)은 키를 못 만드니 이름 그대로 쓴다 — 집계에서 사라지면 안 된다.
        val d = compute(listOf(spend(game = "없는게임", amount = 1_500, month = 7)))
        assertEquals(mapOf("없는게임" to 1_500L), d.currentMonthTotalsByGame)
    }

    // ── 최근 N개월 ───────────────────────────────────────────────────────────

    @Test
    fun recentMonthlyTotalsAreOldestFirstAndSpanTheYearBoundary() {
        // 2026-02 기준 6개월 = 2025-09 … 2026-02 (오래된 달이 0번)
        val d = compute(
            listOf(
                spend(amount = 900, year = 2025, month = 9),
                spend(amount = 100, year = 2025, month = 12),
                spend(amount = 50, year = 2026, month = 2),
                spend(amount = 7, year = 2025, month = 8),   // 범위 밖
            ),
            year = 2026, month = 2,
        )
        assertEquals(listOf(900L, 0L, 0L, 100L, 0L, 50L), d.recentMonthlyTotals)
    }

    @Test
    fun recentMonthlyTotalsLastEntryMatchesCurrentMonthTotal() {
        // 마지막 칸은 정의상 '이번 달' — 두 값이 따로 계산되던 시절엔 어긋날 수 있었다.
        val d = compute(
            listOf(
                spend(amount = 1_000, month = 7),
                spend(amount = 2_000, month = 7, day = 20),
                spend(amount = 500, month = 4),
            ),
        )
        assertEquals(d.currentMonthTotal, d.recentMonthlyTotals.last())
        assertEquals(6, d.recentMonthlyTotals.size)
    }

    // ── 미등록 구독 ──────────────────────────────────────────────────────────

    @Test
    fun unlinkedCountIgnoresNonSubscriptionSpendings() {
        val d = compute(
            listOf(
                spend(amount = 5_900, month = 7, isSub = true, itemName = "공월의 축복"),
                spend(amount = 12_000, month = 7),   // 구독 표시 아님
            ),
        )
        assertEquals(1, d.unlinkedSubCount)
    }

    @Test
    fun unlinkedCountDeduplicatesSameSubscriptionAcrossMonths() {
        // 매달 기록한 같은 구독(이름·게임·금액 동일)은 후보 1건이어야 한다.
        val d = compute(
            listOf(
                spend(amount = 5_900, month = 7, isSub = true, itemName = "공월의 축복"),
                spend(amount = 5_900, month = 6, isSub = true, itemName = "공월의 축복"),
                spend(amount = 5_900, month = 5, isSub = true, itemName = "공월의 축복"),
            ),
        )
        assertEquals(1, d.unlinkedSubCount)
    }

    @Test
    fun unlinkedCountExcludesAlreadyRegisteredSubscriptions() {
        val already = Subscription(name = "공월의 축복", gameName = "원신", amount = 5_900, billingDay = 10)
        val d = compute(
            listOf(
                spend(amount = 5_900, month = 7, isSub = true, itemName = "공월의 축복"),
                spend(amount = 12_000, month = 7, isSub = true, itemName = "기행"),
            ),
            subscriptions = listOf(already),
        )
        assertEquals(1, d.unlinkedSubCount)   // 기행만 남는다
    }

    @Test
    fun unlinkedCandidateTakesBillingDayFromMostRecentSpending() {
        // 결제일은 최신 기록 기준 — 날짜 내림차순 순회가 깨지면 옛 날짜가 남는다.
        val list = SpendingDerived.unlinkedSubscriptions(
            listOf(
                spend(amount = 5_900, month = 5, day = 3, isSub = true, itemName = "공월의 축복"),
                spend(amount = 5_900, month = 7, day = 21, isSub = true, itemName = "공월의 축복"),
            ),
            existing = emptyList(),
        )
        assertEquals(1, list.size)
        assertEquals(21, list.first().billingDay)
    }

    @Test
    fun subscriptionNameFallsBackToGameShortNameWhenItemNameBlank() {
        assertEquals("공월의 축복", SpendingDerived.subscriptionName(spend(amount = 1, month = 7, itemName = "공월의 축복")))
        assertEquals("원신 정기결제", SpendingDerived.subscriptionName(spend(game = "원신", amount = 1, month = 7)))
        // 목록에 없는 게임은 이름 그대로
        assertEquals("없는게임 정기결제", SpendingDerived.subscriptionName(spend(game = "없는게임", amount = 1, month = 7)))
    }

    // ── 순회가 한 번이어도 결과가 서로 일관적인지 ────────────────────────────

    @Test
    fun gameTotalsSumToCurrentMonthTotal() {
        val d = compute(
            listOf(
                spend(game = "원신", amount = 10_000, month = 7),
                spend(game = "붕괴: 스타레일", amount = 5_000, month = 7),
                spend(game = "젠레스 존 제로", amount = 1_200, month = 7),
                spend(game = "원신", amount = 77_000, month = 6),
            ),
        )
        assertEquals(d.currentMonthTotal, d.currentMonthTotalsByGame.values.sum())
    }
}
