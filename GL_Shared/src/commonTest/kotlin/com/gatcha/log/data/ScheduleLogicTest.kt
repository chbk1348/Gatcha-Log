package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 게임 일정 산출 — 전반/후반 페이즈 판정과 버전 묶음은 Android/iOS 가 따로 구현하던 부분이라
 * 재구현 시 어긋나기 쉬웠다. 규칙을 여기서 고정한다.
 */
class ScheduleLogicTest {

    private val base = 1_800_000_000_000L
    private val day = 86_400_000L
    private val gi = Game.GENSHIN

    private fun banner(name: String, endDays: Long, version: String, type: String = "character", startDays: Long = -7, game: Game = gi) =
        GachaBanner(game = game.displayName, name = name, type = type,
            endMillis = base + endDays * day, startMillis = base + startDays * day, version = version)

    // ── 필터 ────────────────────────────────────────────────────────────────

    @Test
    fun filteredPickupsSortsByEndAndKeepsAllForAll() {
        val list = listOf(banner("늦게", 9, "6.6"), banner("빨리", 2, "6.6"))
        assertEquals(listOf("빨리", "늦게"), ScheduleLogic.filteredPickups(list, "all").map { it.name })
    }

    @Test
    fun filteredPickupsNarrowsToSelectedGame() {
        val list = listOf(banner("원신것", 2, "6.6"), banner("스레것", 1, "3.4", game = Game.HSR))
        assertEquals(listOf("원신것"), ScheduleLogic.filteredPickups(list, gi.key).map { it.name })
    }

    @Test
    fun filteredPickupsReturnsEmptyForUnknownFilter() {
        assertTrue(ScheduleLogic.filteredPickups(listOf(banner("a", 1, "6.6")), "없는게임").isEmpty())
    }

    // ── 버전 묶음 ────────────────────────────────────────────────────────────

    @Test
    fun buildVersionGroupsGroupsByGameAndVersion() {
        val list = listOf(
            banner("A", 5, "6.6", startDays = -3),
            banner("B", 5, "6.6", startDays = -7),   // 같은 버전 → 한 그룹
            banner("C", 2, "6.5", startDays = -20),
        )
        val groups = ScheduleLogic.buildVersionGroups(list, "all")
        assertEquals(listOf("6.5", "6.6"), groups.map { it.version })   // nearestEnd 임박순
        val v66 = groups.last()
        assertEquals(listOf("A", "B"), v66.pickups.map { it.name })     // 그룹 내부는 종료 임박순(동률이면 입력 순)
        assertEquals(base + 5 * day, v66.nearestEnd)
        assertEquals(base - 7 * day, v66.start)                        // 시작 = 가장 이른 픽업 시작
        assertEquals(base + 5 * day, v66.end)                          // 종료 = 가장 늦은 픽업 종료
    }

    @Test
    fun buildVersionGroupsIgnoresZeroStartWhenComputingStart() {
        val list = listOf(banner("A", 5, "6.6", startDays = -3), banner("B", 5, "6.6").copy(startMillis = 0L))
        assertEquals(base - 3 * day, ScheduleLogic.buildVersionGroups(list, "all").single().start)
    }

    @Test
    fun buildVersionGroupsFallsBackToZeroWhenNoStartKnown() {
        val list = listOf(banner("A", 5, "6.6").copy(startMillis = 0L))
        assertEquals(0L, ScheduleLogic.buildVersionGroups(list, "all").single().start)
    }

    // ── 통합 일정 (전반/후반 판정) ────────────────────────────────────────────

    @Test
    fun buildScheduleLabelsTwoPhasesOfSameVersion() {
        // 한 버전에 페이즈 2개 → 종료 이른 쪽이 전반, 늦은 쪽이 후반.
        val banners = listOf(banner("전", 3, "6.6"), banner("후", 24, "6.6"))
        val titles = ScheduleLogic.buildSchedule(banners, emptyList(), emptyList()).map { it.title }
        assertEquals(listOf("v6.6 전반 픽업 종료", "v6.6 후반 픽업 종료"), titles)
    }

    @Test
    fun buildScheduleLabelsThirdPhaseNumerically() {
        val banners = listOf(banner("1", 3, "6.6"), banner("2", 24, "6.6"), banner("3", 45, "6.6"))
        val titles = ScheduleLogic.buildSchedule(banners, emptyList(), emptyList()).map { it.title }
        assertEquals(listOf("v6.6 전반 픽업 종료", "v6.6 후반 픽업 종료", "v6.6 3페이즈 픽업 종료"), titles)
    }

    @Test
    fun buildScheduleTreatsLatestSinglePhaseAsFirstHalf() {
        // 버전당 페이즈가 1개씩일 때: 최신 버전 = 전반(후반 미게시), 이전 버전 = 후반(전반 종료됨).
        val banners = listOf(banner("구", 3, "6.5"), banner("신", 24, "6.6"))
        val titles = ScheduleLogic.buildSchedule(banners, emptyList(), emptyList()).map { it.title }
        assertEquals(listOf("v6.5 후반 픽업 종료", "v6.6 전반 픽업 종료"), titles)
    }

    @Test
    fun buildScheduleOmitsVersionPrefixWhenBlank() {
        val titles = ScheduleLogic.buildSchedule(listOf(banner("무버전", 3, "")), emptyList(), emptyList()).map { it.title }
        assertEquals(listOf("전반 픽업 종료"), titles)
    }

    @Test
    fun buildScheduleMergesEventsAndChallengesSortedByTarget() {
        val entries = ScheduleLogic.buildSchedule(
            banners = listOf(banner("픽업", 5, "6.6")),
            events = listOf(GameEvent(game = gi.displayName, name = "이벤트", reward = "원석", endMillis = base + 2 * day)),
            challenges = listOf(GameChallenge(game = gi.displayName, name = "심경", typeName = "심연", reward = "원석", endMillis = base + 9 * day)),
        )
        assertEquals(listOf("이벤트", "패치", "콘텐츠"), entries.map { it.kind })
        assertEquals(listOf("이벤트", "v6.6 전반 픽업 종료", "심경"), entries.map { it.title })
        assertEquals(gi.key, entries.first().gameKey)
        assertEquals(gi.color, entries.first().colorArgb)   // 색은 ARGB Long 으로만 전달(플랫폼이 변환)
    }

    @Test
    fun filteredEntriesNarrowsByGameKey() {
        val entries = ScheduleLogic.buildSchedule(
            banners = emptyList(),
            events = listOf(
                GameEvent(game = gi.displayName, name = "원신 이벤트", reward = "", endMillis = base + day),
                GameEvent(game = Game.HSR.displayName, name = "스레 이벤트", reward = "", endMillis = base + 2 * day),
            ),
            challenges = emptyList(),
        )
        assertEquals(2, ScheduleLogic.filteredEntries(entries, "all").size)
        assertEquals(listOf("원신 이벤트"), ScheduleLogic.filteredEntries(entries, gi.key).map { it.title })
    }

    // ── 종류 색 ─────────────────────────────────────────────────────────────

    @Test
    fun kindColorArgbIsStablePerKind() {
        assertEquals(0xFF6C8AE4, ScheduleLogic.kindColorArgb("패치"))
        assertEquals(0xFFE0A93B, ScheduleLogic.kindColorArgb("이벤트"))
        assertEquals(0xFF2BB673, ScheduleLogic.kindColorArgb("콘텐츠"))
        assertEquals(0xFF2BB673, ScheduleLogic.kindColorArgb("알수없음"))   // 기본값
    }
}
