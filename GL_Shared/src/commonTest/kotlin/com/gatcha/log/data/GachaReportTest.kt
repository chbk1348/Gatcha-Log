package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 가챠 통계·대시보드 — **현재 동작을 고정하는 특성화 테스트.**
 *
 * 천장·평균·운 분포는 유저가 수치로 직접 확인하는 값이라, 리팩터로 조용히 1씩 어긋나면
 * 앱을 열자마자 알아챈다. 그런데 이 두 함수는 지금까지 테스트가 0건이었다.
 * 정렬 키 선계산·중복 순회 제거를 하기 전에 기대값을 먼저 못 박는다.
 */
class GachaReportTest {

    private fun rec(
        id: String,
        rarity: Int,
        pool: String = "character",
        game: String = "genshin",
        name: String = "이름",
        time: String = "2026-07-01 12:00:00",
    ) = GachaRecord(
        game = game, pool = pool, gachaType = "301", itemId = "", name = name,
        itemType = "캐릭터", rarity = rarity, time = time, uid = "800000000", id = id,
    )

    /** 3성 n개 → 편의 생성기. id 는 1부터 증가. */
    private fun run3(from: Int, count: Int, pool: String = "character"): List<GachaRecord> =
        (from until from + count).map { rec(id = it.toString(), rarity = 3, pool = pool) }

    // ── 기본 ────────────────────────────────────────────────────────────────

    @Test
    fun emptyRecordsYieldNull() {
        assertNull(GachaReport.computeStats(emptyList()))
        assertNull(GachaReport.computeDashboard(emptyList()))
    }

    // ── 천장(pity) 계산 ──────────────────────────────────────────────────────

    @Test
    fun pityCountsSinceLastFiveStarAndResets() {
        // 3성 9개 → 5성(10뽑째) → 3성 4개 → 5성(5뽑째) → 3성 2개(현재 천장 2)
        val recs = run3(1, 9) + rec("10", 5) + run3(11, 4) + rec("15", 5) + run3(16, 2)
        val gi = assertNotNull(GachaReport.computeStats(recs)).byGame.getValue("genshin")
        val pool = gi.pools.getValue("character")

        assertEquals(17, pool.total)
        assertEquals(2, pool.five)
        assertEquals(2, pool.pity)   // 마지막 5성 이후 2뽑
        assertEquals(8, pool.avgPity) // 천장 10·5 → 평균 7.5 → roundToInt = 8 (정수 나눗셈 7 아님)
    }

    @Test
    fun averagePityRoundsHalfUp() {
        // 천장 10, 5 → 평균 7.5 → roundToInt = 8
        val recs = run3(1, 9) + rec("10", 5) + run3(11, 4) + rec("15", 5)
        val pool = assertNotNull(GachaReport.computeStats(recs)).byGame.getValue("genshin").pools.getValue("character")
        assertEquals(8, pool.avgPity)
    }

    @Test
    fun poolsHaveIndependentPity() {
        // 캐릭터 풀과 무기 풀은 천장이 별개다 — 한 풀의 5성이 다른 풀 카운터를 건드리면 안 된다.
        val recs = run3(1, 5, "character") + rec("6", 5, "character") +
            run3(7, 3, "weapon") + rec("10", 5, "weapon") + run3(11, 4, "weapon")
        val gi = assertNotNull(GachaReport.computeStats(recs)).byGame.getValue("genshin")
        assertEquals(0, gi.pools.getValue("character").pity)  // 5성이 마지막 → 0
        assertEquals(4, gi.pools.getValue("weapon").pity)
        assertEquals(6, gi.pools.getValue("character").avgPity)
        assertEquals(4, gi.pools.getValue("weapon").avgPity)
    }

    // ── 스노우플레이크 id 정렬 ───────────────────────────────────────────────

    @Test
    fun recordsSortNumericallyNotLexicallyByIdLength() {
        // 사전순이면 "10" < "9" 라 순서가 뒤집혀 천장이 통째로 틀어진다.
        // 실제 UIGF id 는 자릿수가 섞인 스노우플레이크라 이게 핵심 케이스다.
        val recs = listOf(
            rec("100", 3), rec("9", 3), rec("10", 5), rec("99", 3),
        )
        val pool = assertNotNull(GachaReport.computeStats(recs)).byGame.getValue("genshin").pools.getValue("character")
        // 숫자순: 9(1) → 10(2, 5성) → 99 → 100 ⇒ 천장 2, 이후 2뽑
        assertEquals(2, pool.avgPity)
        assertEquals(2, pool.pity)
    }

    @Test
    fun idsWithLeadingZerosAndNonDigitsAreNormalized() {
        // trimStart('0') + 숫자만 추출 — 옛 데이터에 하이픈·0패딩이 섞여 있어도 순서가 유지돼야 한다.
        val recs = listOf(rec("0009", 3), rec("1-0", 5), rec("0099", 3))
        val pool = assertNotNull(GachaReport.computeStats(recs)).byGame.getValue("genshin").pools.getValue("character")
        // 9 → 10(5성) → 99 ⇒ 천장 2
        assertEquals(2, pool.avgPity)
        assertEquals(1, pool.pity)
    }

    @Test
    fun recentFiveIsNewestFirstAndCappedAtEight() {
        // 5성 10개 → 최근 8개만, id 내림차순(최신 먼저)
        val recs = (1..10).map { rec(id = (it * 10).toString(), rarity = 5, name = "5성$it") }
        val gi = assertNotNull(GachaReport.computeStats(recs)).byGame.getValue("genshin")
        assertEquals(8, gi.recentFive.size)
        assertEquals("5성10", gi.recentFive.first().name)
        assertEquals("5성3", gi.recentFive.last().name)
    }

    // ── 운 분포 ─────────────────────────────────────────────────────────────

    @Test
    fun luckDistributionSplitsAtFortyAndSeventyFive() {
        // 천장 40(행운 경계 포함) · 41(평균) · 75(불운 경계 포함)
        val recs = run3(1, 39) + rec("40", 5) +          // 천장 40
            run3(41, 40) + rec("81", 5) +                 // 천장 41
            run3(82, 74) + rec("156", 5)                  // 천장 75
        val gi = assertNotNull(GachaReport.computeStats(recs)).byGame.getValue("genshin")
        assertEquals(listOf(1, 1, 1), gi.luckDist)
    }

    // ── 대시보드 ────────────────────────────────────────────────────────────

    @Test
    fun dashboardBucketsPityByTens() {
        // 천장 1 → 0번칸(1-10), 10 → 0번칸, 11 → 1번칸, 90 → 8번칸
        val recs = listOf(rec("1", 5)) +       // 천장 1
            run3(2, 9) + rec("11", 5) +        // 천장 10
            run3(12, 10) + rec("22", 5)        // 천장 11
        val dash = assertNotNull(GachaReport.computeDashboard(recs)).byGame.getValue("genshin")
        assertEquals(listOf(2, 1, 0, 0, 0, 0, 0, 0, 0), dash.pityBuckets)
    }

    @Test
    fun dashboardSeparatesLimitedFromStandardPools() {
        // permanent·novice 만 상시, 나머지는 전부 한정으로 센다.
        val recs = run3(1, 3, "character") + run3(4, 2, "weapon") +
            run3(6, 4, "permanent") + run3(10, 1, "novice")
        val dash = assertNotNull(GachaReport.computeDashboard(recs)).byGame.getValue("genshin")
        assertEquals(5, dash.limited)
        assertEquals(5, dash.standard)
        assertEquals(10, dash.total)
    }

    @Test
    fun dashboardMonthlyIsAscendingAndKeepsLastTwelve() {
        val recs = (1..14).map { i ->
            val month = if (i <= 9) "0$i" else "$i"
            rec(id = i.toString(), rarity = 3, time = "2025-${if (i <= 12) month else "12"}-01 00:00:00")
        }
        val dash = assertNotNull(GachaReport.computeDashboard(recs)).byGame.getValue("genshin")
        assertEquals(12, dash.monthly.size)
        assertEquals("2025-01", dash.monthly.first().first)
        assertEquals("2025-12", dash.monthly.last().first)
        assertEquals(3, dash.monthly.last().second)   // 12·13·14번이 전부 2025-12
    }

    @Test
    fun dashboardFiveStarsAreNewestFirstByTime() {
        val recs = listOf(
            rec("1", 5, time = "2026-01-01 00:00:00", name = "A"),
            rec("2", 5, time = "2026-07-01 00:00:00", name = "B"),
            rec("3", 5, time = "2026-03-01 00:00:00", name = "C"),
        )
        val dash = assertNotNull(GachaReport.computeDashboard(recs)).byGame.getValue("genshin")
        assertEquals(listOf("B", "C", "A"), dash.fiveStars.map { it.name })
    }

    @Test
    fun dashboardThreeStarCountIsRemainder() {
        val recs = run3(1, 7) + rec("8", 4) + rec("9", 5)
        val dash = assertNotNull(GachaReport.computeDashboard(recs)).byGame.getValue("genshin")
        assertEquals(9, dash.total)
        assertEquals(1, dash.five)
        assertEquals(1, dash.four)
        assertEquals(7, dash.three)
        assertEquals(9, dash.minPity)
        assertEquals(9, dash.maxPity)
    }

    // ── 두 함수의 일관성 (순회를 합쳐도 어긋나면 안 된다) ────────────────────

    @Test
    fun statsAndDashboardAgreeOnTotals() {
        val recs = run3(1, 20, "character") + rec("21", 5, "character") +
            run3(22, 8, "weapon") + rec("30", 4, "weapon") + rec("31", 5, "weapon") +
            run3(32, 5, "permanent")
        val stats = assertNotNull(GachaReport.computeStats(recs)).byGame.getValue("genshin")
        val dash = assertNotNull(GachaReport.computeDashboard(recs)).byGame.getValue("genshin")

        assertEquals(stats.total, dash.total)
        assertEquals(stats.five, dash.five)
        assertEquals(stats.four, dash.four)
        assertEquals(stats.avgPity, dash.avgPity)
    }

    @Test
    fun computeAllIsEquivalentToCallingBothSeparately() {
        // 순회를 합친 경로 — 따로 부른 결과와 **완전히 같아야** 한다.
        // 여기가 어긋나면 시작 직후 마이페이지·가챠 리포트 수치만 조용히 달라진다.
        val recs = run3(1, 12, "character") + rec("13", 5, "character") +
            run3(14, 6, "weapon") + rec("20", 4, "weapon") + rec("21", 5, "weapon") +
            run3(22, 3, "permanent") +
            listOf(rec("30", 5, game = "starrail", pool = "lightcone", time = "2026-06-02 09:00:00"))

        val (allStats, allDash) = GachaReport.computeAll(recs)
        assertEquals(GachaReport.computeStats(recs), allStats)
        assertEquals(GachaReport.computeDashboard(recs), allDash)
    }

    @Test
    fun computeAllReturnsNullsForEmptyRecords() {
        val (stats, dash) = GachaReport.computeAll(emptyList())
        assertNull(stats)
        assertNull(dash)
    }

    @Test
    fun multipleGamesAreAggregatedSeparately() {
        val recs = run3(1, 3) + rec("4", 5) +
            listOf(rec("5", 5, game = "starrail", pool = "lightcone"))
        val stats = assertNotNull(GachaReport.computeStats(recs))
        assertEquals(5, stats.total)
        assertEquals(setOf("genshin", "starrail"), stats.byGame.keys)
        assertEquals(4, stats.byGame.getValue("genshin").total)
        assertEquals(1, stats.byGame.getValue("starrail").total)
    }
}
