package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 홈 파생 계산 — Android/iOS 가 각각 구현하던 로직을 공유로 옮긴 뒤,
 * 문구·우선순위·알림 키가 다시 갈리지 않도록 고정한다.
 */
class HomeLogicTest {

    private val now = 1_800_000_000_000L   // 고정 기준 시각(테스트 결정성)
    private val day = 86_400_000L

    private fun banner(name: String, endInDays: Long, game: String = Game.GENSHIN.displayName, version: String = "6.6", startInDays: Long = -10) =
        GachaBanner(game = game, name = name, endMillis = now + endInDays * day, startMillis = now + startInDays * day, version = version)

    // ── 게임별 한도 초과 ──────────────────────────────────────────────────────

    @Test
    fun gameOverBudgetIsEmptyWhenNoLimitsSet() {
        assertEquals(emptyList(), HomeLogic.gameOverBudget(emptyMap(), mapOf(Game.GENSHIN.key to 999_999L)))
    }

    @Test
    fun gameOverBudgetListsOnlyExceededGames() {
        val gi = Game.GENSHIN
        val budgets = mapOf(gi.key to 10_000L)
        assertEquals(listOf(gi.shortName), HomeLogic.gameOverBudget(budgets, mapOf(gi.key to 10_001L)))
        // 같으면 초과 아님(> 비교)
        assertEquals(emptyList(), HomeLogic.gameOverBudget(budgets, mapOf(gi.key to 10_000L)))
    }

    // ── 절약 팁 ─────────────────────────────────────────────────────────────

    @Test
    fun savingTipPrioritizesOverallBudgetOverPerGame() {
        val tip = HomeLogic.savingTip(budget = 10_000, monthlyTotal = 20_000, gameOverBudget = listOf("원신"))
        assertTrue(tip.startsWith("이번 달은 예산을 넘겼어요"))
    }

    @Test
    fun savingTipFallsBackByBudgetState() {
        assertTrue(HomeLogic.savingTip(10_000, 1_000, listOf("원신")).startsWith("원신 한도를"))
        assertTrue(HomeLogic.savingTip(0, 1_000, emptyList()).startsWith("월 예산을 정하면"))
        assertTrue(HomeLogic.savingTip(10_000, 1_000, emptyList()).startsWith("천장이 가까운"))
    }

    // ── 게임별 지출 · 임박 배너 ───────────────────────────────────────────────

    @Test
    fun perGameSpendSkipsGamesWithNeitherSpendNorLimit() {
        val gi = Game.GENSHIN
        val rows = HomeLogic.perGameSpend(mapOf(gi.key to 5_000L), emptyMap())
        assertEquals(listOf(gi.key), rows.map { it.game.key })
        assertEquals(5_000L, rows.first().spent)
        assertEquals(0L, rows.first().limit)
    }

    @Test
    fun perGameSpendKeepsGameWithLimitButNoSpend() {
        val gi = Game.GENSHIN
        val rows = HomeLogic.perGameSpend(emptyMap(), mapOf(gi.key to 30_000L))
        assertEquals(1, rows.size)
        assertEquals(30_000L, rows.first().limit)
    }

    @Test
    fun soonBannersKeepsWeekWindowSortedAndCappedAtFour() {
        val banners = listOf(
            banner("f", 9), banner("e", 8), banner("d", 7), banner("c", 3),
            banner("b", 1), banner("a", 0), banner("past", -1),
        )
        val soon = HomeLogic.soonBanners(banners, now)
        assertEquals(listOf("a", "b", "c", "d"), soon.map { it.name })   // 8·9일은 창 밖, 종료분 제외, 최대 4
    }

    // ── 오늘 할 일 ──────────────────────────────────────────────────────────

    @Test
    fun resolveTodayTasksOrdersByTimeSensitivity() {
        val resins = listOf(ResinAlert("원신", "레진", 160, 160, "", full = true))
        val tasks = HomeLogic.resolveTodayTasks(
            pendingAttendance = 2, resins = resins, urgentBanner = banner("한정", 1),
            budget = 10_000, monthlyTotal = 20_000,
            combats = listOf(CombatDeadline("원신", "나선 비경", 27, 36, 2)),
            nowMillis = now,
        )
        // 전투 시즌은 만회에 플레이 시간이 필요해 픽업(결제 판단)보다 앞선다.
        assertEquals(
            listOf(TodayTaskKind.ATTENDANCE, TodayTaskKind.RESIN, TodayTaskKind.COMBAT, TodayTaskKind.BANNER, TodayTaskKind.BUDGET),
            tasks.map { it.kind },
        )
        assertEquals("원신 나선 비경 27/36 · D-2", tasks[2].message)
        assertEquals("출석 안 한 게임 2개", tasks[0].message)
        assertEquals("원신 레진 가득 참", tasks[1].message)
        assertEquals("예산 100% 초과", tasks[4].message)
        assertTrue(tasks[0].busyable)          // 전체출석만 진행 스피너 대상
        assertTrue(tasks.drop(1).none { it.busyable })
    }

    @Test
    fun todayTaskKeysAreUniquePerRow() {
        // key 는 목록 식별자다. 수지·전투 콘텐츠는 해당되는 게임마다 한 줄씩 나오므로
        // 종류(kind)를 키로 쓰면 서로 충돌해 목록에 중복 표시·누락이 생긴다.
        val tasks = HomeLogic.resolveTodayTasks(
            pendingAttendance = 3,
            resins = listOf(
                ResinAlert("원신", "레진", 160, 160, "", full = true),
                ResinAlert("스타레일", "개척력", 230, 240, "", full = false),
                ResinAlert("젠레스", "배터리", 240, 240, "", full = true),
            ),
            urgentBanner = banner("한정", 1),
            budget = 10_000, monthlyTotal = 20_000,
            combats = listOf(
                CombatDeadline("원신", "나선 비경", 27, 36, 2),
                CombatDeadline("원신", "환상극", 0, 8, 1),      // 같은 게임의 다른 모드도 구분돼야 한다
                CombatDeadline("스타레일", "혼돈의 기억", 30, 36, 3),
            ),
            nowMillis = now,
        )
        val keys = tasks.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "중복 key: $keys")
        assertEquals(9, tasks.size)   // 출석1 + 수지3 + 전투3 + 픽업1 + 예산1
    }

    @Test
    fun todayTaskKeysSurviveValueChanges() {
        // key 에 수치·D-day 가 섞이면 갱신될 때마다 키가 바뀌어 목록이 통째로 다시 그려진다.
        fun keysWith(cur: Int, dDay: Int, pending: Int) = HomeLogic.resolveTodayTasks(
            pendingAttendance = pending,
            resins = listOf(ResinAlert("원신", "레진", cur, 160, "", full = false)),
            urgentBanner = null, budget = 10_000, monthlyTotal = 20_000,
            combats = listOf(CombatDeadline("원신", "나선 비경", 27, 36, dDay)),
            nowMillis = now,
        ).map { it.key }

        assertEquals(keysWith(120, 3, 2), keysWith(151, 1, 1))
    }

    @Test
    fun resolveTodayTasksShowsNearBudgetWarningInsteadOfOver() {
        val tasks = HomeLogic.resolveTodayTasks(0, emptyList(), null, budget = 10_000, monthlyTotal = 9_500, nowMillis = now)
        assertEquals(listOf(TodayTaskKind.BUDGET), tasks.map { it.kind })
        assertEquals("예산 95% 사용", tasks.single().message)
    }

    @Test
    fun resolveTodayTasksIsEmptyWhenNothingPending() {
        // 예산 89% 는 경고 문턱(90%) 미만 → 할 일 없음
        assertTrue(HomeLogic.resolveTodayTasks(0, emptyList(), null, 10_000, 8_900, nowMillis = now).isEmpty())
    }

    @Test
    fun resolveTodayTasksSkipsBudgetWhenUnset() {
        assertTrue(HomeLogic.resolveTodayTasks(0, emptyList(), null, budget = 0, monthlyTotal = 99_999, nowMillis = now).isEmpty())
    }

    @Test
    fun resinAlertsKeepOnlyNearFullSortedByFullness() {
        val notes = listOf(
            LiveNote(game = Game.GENSHIN.displayName, currentResin = 100, maxResin = 160),  // 62% → 제외
            LiveNote(game = Game.GENSHIN.displayName, currentResin = 140, maxResin = 160),  // 87%
            LiveNote(game = Game.GENSHIN.displayName, currentResin = 160, maxResin = 160),  // 100%
        )
        val alerts = HomeLogic.resinAlerts(notes)
        assertEquals(listOf(160, 140), alerts.map { it.cur })
        assertTrue(alerts.first().full)
        assertTrue(!alerts.last().full)
    }

    @Test
    fun resinAlertsIgnoreNotesWithoutMaxResin() {
        assertTrue(HomeLogic.resinAlerts(listOf(LiveNote(game = Game.GENSHIN.displayName, currentResin = 5, maxResin = 0))).isEmpty())
    }

    // ── 충전 문구는 '가득 차는 시각'에서 파생된다 ────────────────────────────────
    //
    // 예전엔 응답을 파싱할 때 만든 문자열을 필드로 들고 다녔다. 디스크 캐시에는 시각만 실려서,
    // 앱을 켜면 행동력 숫자는 캐시로 바로 뜨는데 이 줄만 네트워크가 올 때까지 비어 있었다.
    // 이제 시각에서 매번 만든다 — 캐시에서 복원해도 즉시 나오고, 시간이 지나면 값이 줄어든다.

    private fun noteAt(fullAtMillis: Long, max: Int = 200) =
        LiveNote(game = Game.GENSHIN.displayName, currentResin = 172, maxResin = max, resinFullAtMillis = fullAtMillis)

    @Test
    fun 충전문구는_가득차는시각에서_만든다() {
        // **올림이다.** 남은 시간이 (2시간, 3시간] 이면 "약 3시간" — 3시간 정각을 조금이라도
        // 넘기면 "약 4시간"이 된다. 경계에 딱 붙이면 테스트가 실행 시각에 따라 흔들리므로
        // 5초를 빼서 구간 안쪽에 둔다.
        val inThreeHours = com.gatcha.log.util.currentTimeMillis() + 3 * 3_600_000L - 5_000L
        assertEquals("약 3시간 후 충전", noteAt(inThreeHours).resinRecoveryTime)
    }

    @Test
    fun 충전문구_가득이거나_지난_시각이면_완료() {
        assertEquals("충전 완료", noteAt(0L).resinRecoveryTime, "0 = 이미 가득")
        assertEquals("충전 완료", noteAt(1L).resinRecoveryTime, "한참 지난 시각 = 이미 가득")
    }

    @Test
    fun 충전문구_재화정보가_없으면_아무말도_안한다() {
        // 모르는 것과 다 찬 것은 다르다 — 여기서 "충전 완료"라고 하면 없는 사실을 지어내는 것.
        assertEquals("", noteAt(0L, max = 0).resinRecoveryTime)
    }

    // ── 알림센터 ────────────────────────────────────────────────────────────

    @Test
    fun buildAlertsUsesStableKeysNotMessages() {
        // 키에 %·D-day 같은 가변값이 들어가면 한 번 읽어도 다시 뜬다 → 종류+기간만 담아야 한다.
        val alerts = HomeLogic.buildAlerts(
            monthlyTotal = 20_000, budget = 10_000, gameOverBudget = listOf("원신"),
            banners = listOf(banner("한정", 2)), attendanceToday = GameData.attendanceGames.map { it.key }.toSet(),
            monthKey = "2026-7", nowMillis = now,
        )
        assertEquals(
            listOf("budget_over:2026-7", "budget_game_over:원신:2026-7", "banner:한정"),
            alerts.map { it.key },
        )
        assertEquals(
            listOf(HomeAlertKind.BUDGET_OVER, HomeAlertKind.BUDGET_GAME_OVER, HomeAlertKind.BANNER),
            alerts.map { it.kind },
        )
        assertEquals("이번 달 예산을 초과했어요 (200%)", alerts.first().message)
    }

    @Test
    fun buildAlertsEmitsNearBudgetAndAttendance() {
        val alerts = HomeLogic.buildAlerts(9_000, 10_000, emptyList(), emptyList(), emptySet(), "2026-7", now)
        assertEquals(HomeAlertKind.BUDGET_NEAR, alerts.first().kind)
        assertEquals("이번 달 예산의 90%를 사용했어요", alerts.first().message)
        val attendance = alerts.last()
        assertEquals(HomeAlertKind.ATTENDANCE, attendance.kind)
        assertTrue(attendance.key.startsWith("attendance:"))
        assertEquals("오늘 출석체크가 ${GameData.attendanceGames.size}개 남아있어요", attendance.message)
    }

    @Test
    fun buildAlertsOnlyIncludesBannersEndingWithinThreeDays() {
        val banners = listOf(banner("이번", 3), banner("다음", 4), banner("지남", -1))
        val alerts = HomeLogic.buildAlerts(0, 0, emptyList(), banners, GameData.attendanceGames.map { it.key }.toSet(), "2026-7", now)
        assertEquals(listOf("banner:이번"), alerts.map { it.key })
        assertEquals("이번 픽업 배너 종료 D-3", alerts.single().message)
    }

    @Test
    fun buildAlertsLabelsDDayForBannerEndingToday() {
        val alerts = HomeLogic.buildAlerts(0, 0, emptyList(), listOf(banner("오늘", 0)), GameData.attendanceGames.map { it.key }.toSet(), "2026-7", now)
        assertEquals("오늘 픽업 배너 종료 D-DAY", alerts.single().message)
    }

    // ── 전투 콘텐츠 시즌 마감 ────────────────────────────────────────────────

    private fun combat(mode: String, stars: Int, maxStars: Int, endDays: Long, hasData: Boolean = true) =
        CombatMode(game = Game.GENSHIN.displayName, name = mode, stars = stars, maxStars = maxStars,
            endMillis = now + endDays * day, hasData = hasData)

    @Test
    fun combatDeadlinesKeepsOnlyUnclearedWithinWarnWindow() {
        val list = listOf(
            combat("만점", 36, 36, 1),      // 이미 만점 → 제외
            combat("여유", 0, 36, 5),       // D-5 → 창 밖
            combat("임박", 27, 36, 2),      // 대상
            combat("오늘", 12, 36, 0),      // 대상(D-DAY)
            combat("지남", 0, 36, -1),      // 종료 → 제외
        )
        val out = HomeLogic.combatDeadlines(list, now)
        assertEquals(listOf("오늘", "임박"), out.map { it.mode })   // 마감 빠른 순
        assertEquals(listOf(0, 2), out.map { it.dDay })
    }

    @Test
    fun combatDeadlinesIgnoresModesWithoutDataOrMaxScore() {
        // hasData=false 는 조회 실패/미연동 — '미클리어'로 오해하면 안 된다.
        assertTrue(HomeLogic.combatDeadlines(listOf(combat("실패", 0, 36, 1, hasData = false)), now).isEmpty())
        // maxStars=0 은 만점 개념이 없는 점수형 모드.
        assertTrue(HomeLogic.combatDeadlines(listOf(combat("점수형", 0, 0, 1)), now).isEmpty())
    }

    @Test
    fun combatDeadlinesIgnoresSeasonlessModes() {
        // endMillis=0 → 시즌 종료 개념 없음(dDay null)
        val noSeason = CombatMode(game = Game.GENSHIN.displayName, name = "상시", stars = 0, maxStars = 36, endMillis = 0)
        assertTrue(HomeLogic.combatDeadlines(listOf(noSeason), now).isEmpty())
    }

    @Test
    fun resolveTodayTasksLabelsCombatDueToday() {
        val tasks = HomeLogic.resolveTodayTasks(
            0, emptyList(), null, 0, 0,
            combats = listOf(CombatDeadline("스타레일", "혼돈의 기억", 30, 36, 0)), nowMillis = now,
        )
        assertEquals("스타레일 혼돈의 기억 30/36 · 오늘 마감", tasks.single().message)
        assertEquals("전투 콘텐츠", tasks.single().ctaLabel)
        assertTrue(tasks.single().urgent)
    }

    @Test
    fun pendingAttendanceCountIgnoresCheckedGames() {
        val all = GameData.attendanceGames.map { it.key }.toSet()
        assertEquals(0, HomeLogic.pendingAttendanceCount(all))
        assertEquals(GameData.attendanceGames.size, HomeLogic.pendingAttendanceCount(emptySet()))
    }

    // ── 천장 하이라이트 ──────────────────────────────────────────────────────

    @Test
    fun topPityIgnoresZeroCounts() {
        assertNull(HomeLogic.topPity(mapOf(Game.GENSHIN.key to PityState(count = 0))))
        assertNull(HomeLogic.topPity(emptyMap()))
    }

    @Test
    fun topPityPicksHighestTierThenCount() {
        val gi = Game.GENSHIN
        val picked = HomeLogic.topPity(mapOf(gi.key to PityState(count = 70)))
        assertEquals(gi.key, picked?.game?.key)
        assertEquals(70, picked?.count)
    }
}
