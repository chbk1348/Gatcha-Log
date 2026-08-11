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

    // ── 종료 미정 픽업(상류 end_time 미공지) ──────────────────────────────────
    // 실제 사례: 스타레일 4.4 Fate 콜라보 — 시작만 공지되고 종료가 0 이었다.

    @Test
    fun endUnknownPickupSortsLastNotFirst() {
        // endMillis=0 을 그냥 정렬하면 '가장 임박한' 것으로 잡혀 맨 위로 올라온다.
        val unknown = banner("콜라보", 0, "4.4").copy(endMillis = 0L)
        val list = listOf(unknown, banner("늦게", 9, "4.4"), banner("빨리", 2, "4.4"))
        assertEquals(listOf("빨리", "늦게", "콜라보"), ScheduleLogic.filteredPickups(list, "all").map { it.name })
    }

    @Test
    fun endUnknownPickupIsExcludedFromVersionGroupDates() {
        val unknown = banner("콜라보", 0, "4.4").copy(endMillis = 0L)
        val g = ScheduleLogic.buildVersionGroups(listOf(unknown, banner("일반", 5, "4.4")), "all").single()
        assertEquals(2, g.pickups.size)                  // 카드에는 남는다
        assertEquals(base + 5 * day, g.nearestEnd)       // 날짜 집계에선 빠진다
        assertEquals(base + 5 * day, g.end)
        assertTrue(!g.isEndUnknown)
    }

    @Test
    fun versionGroupWithOnlyUnknownEndsShowsUnknownLabel() {
        val unknown = banner("콜라보", 0, "4.4").copy(endMillis = 0L)
        val g = ScheduleLogic.buildVersionGroups(listOf(unknown), "all").single()
        assertTrue(g.isEndUnknown)
        assertEquals("종료 미정", g.remainLabel(base))
    }

    @Test
    fun endUnknownPickupMakesNoScheduleRow() {
        // 날짜가 없으니 '픽업 종료' 일정 줄을 만들 수 없다.
        val unknown = banner("콜라보", 0, "4.4").copy(endMillis = 0L)
        val rows = ScheduleLogic.buildSchedule(listOf(unknown), emptyList(), emptyList())
        assertTrue(rows.isEmpty())
    }

    @Test
    fun endUnknownBannerLabelsAndUrgencyAreSafe() {
        val unknown = banner("콜라보", 0, "4.4").copy(endMillis = 0L)
        assertTrue(unknown.isEndUnknown)
        assertEquals("종료 미정", unknown.remainLabel(base))
        assertEquals("종료 미정", unknown.dDayLabel(base))
        assertEquals("종료 미정", unknown.endShortLabel(base))
        assertEquals("", unknown.endDateLabel())          // 날짜 줄은 숨긴다
        assertTrue(!unknown.isUrgent(base))               // 임박 강조(빨강) 오표시 방지
        assertTrue(!unknown.hasProgress)                  // 진행바도 못 그린다
        assertEquals(0f, unknown.progress(base))
    }

    @Test
    fun endUnknownBannerIsSkippedBySavingsPlanner() {
        // 남은 일수를 모르면 하루 저축 목표를 역산할 수 없다.
        val unknown = banner("콜라보", 0, "4.4", game = Game.HSR).copy(endMillis = 0L)
        assertTrue(SavingsPlanner.build(listOf(unknown), emptyMap(), emptyMap(), base).isEmpty())
    }

    // ── 상세: 마감 날짜 타임라인 ─────────────────────────────────────────────

    @Test
    fun buildDaysGroupsEntriesEndingOnSameDate() {
        val gi = listOf(banner("A", 5, "6.7"), banner("B", 5, "6.7"))     // 같은 날 종료 → 한 페이즈
        val ev = GameEvent(Game.GENSHIN.displayName, "이벤트", base + 5 * day)
        val days = ScheduleLogic.buildDays(
            ScheduleLogic.buildSchedule(gi, listOf(ev), emptyList()), base,
        )
        assertEquals(1, days.size)
        assertEquals(2, days.first().entries.size)          // 픽업 종료 + 이벤트가 같은 날짜 노드에
        assertEquals("패치", days.first().entries.first().kind)  // 픽업을 먼저 보여준다
        assertEquals(5, days.first().dDay)
    }

    @Test
    fun buildDaysSortsByRemainingDaysAndDropsPast() {
        val ev1 = GameEvent(Game.GENSHIN.displayName, "늦게", base + 9 * day)
        val ev2 = GameEvent(Game.GENSHIN.displayName, "빨리", base + 2 * day)
        val past = GameEvent(Game.GENSHIN.displayName, "지남", base - 2 * day)
        val days = ScheduleLogic.buildDays(
            ScheduleLogic.buildSchedule(emptyList(), listOf(ev1, ev2, past), emptyList()), base,
        )
        assertEquals(listOf("빨리", "늦게"), days.map { it.entries.first().title })
    }

    @Test
    fun scheduleDayMarksOnlyNearDeadlinesUrgent() {
        val near = GameEvent(Game.GENSHIN.displayName, "임박", base + 3 * day)
        val far = GameEvent(Game.GENSHIN.displayName, "여유", base + 4 * day)
        val days = ScheduleLogic.buildDays(
            ScheduleLogic.buildSchedule(emptyList(), listOf(near, far), emptyList()), base,
        )
        assertTrue(days.first().urgent)
        assertTrue(!days.last().urgent)
    }

    @Test
    fun patchEntryCarriesItsPickupsForTimelineChips() {
        val entries = ScheduleLogic.buildSchedule(listOf(banner("콜롬비나", 5, "6.7"), banner("라이덴 쇼군", 5, "6.7")), emptyList(), emptyList())
        val patch = entries.single { it.kind == "패치" }
        assertEquals(listOf("콜롬비나", "라이덴 쇼군"), patch.pickups.map { it.name })
    }

    @Test
    fun undatedPickupsAreExcludedFromTimelineAndPinnedInstead() {
        val unknown = banner("토오사카 린", 0, "4.4", game = Game.HSR).copy(endMillis = 0L)
        val list = listOf(unknown, banner("스파키", 9, "4.4", game = Game.HSR))
        val days = ScheduleLogic.buildDays(ScheduleLogic.buildSchedule(list, emptyList(), emptyList()), base)
        assertEquals(1, days.size)                                   // 종료 미정은 타임라인에 없다
        assertEquals(listOf("토오사카 린"), ScheduleLogic.undatedPickups(list).map { it.name })
    }

    @Test
    fun summarizeCountsWeekDeadlinesPickupsAndExtras() {
        val banners = listOf(banner("A", 5, "6.7"), banner("B", 20, "6.8"))
        val events = listOf(
            GameEvent(Game.GENSHIN.displayName, "이번주", base + 3 * day),
            GameEvent(Game.GENSHIN.displayName, "나중", base + 30 * day),
        )
        val s = ScheduleLogic.summarize(
            banners, ScheduleLogic.buildSchedule(banners, events, emptyList()), base,
        )
        assertEquals(2, s.weekDeadlines)   // 5일 뒤 픽업 종료 + 3일 뒤 이벤트
        assertEquals(2, s.activePickups)
        assertEquals(2, s.extras)
    }

    // ── 섹션 진입 카드: 게임 한 줄 ───────────────────────────────────────────

    @Test
    fun gameLineSummarizesNearestVersionWithLeadCharacter() {
        val list = listOf(
            banner("콜롬비나", 15, "6.7"), banner("라이덴 쇼군", 15, "6.7"),
            banner("무기", 15, "6.7", type = "weapon"),
        )
        val line = ScheduleLogic.gameLines(list, emptyList(), base).single()
        assertEquals("원신", line.shortName)
        assertEquals("v6.7 · 콜롬비나 외 1", line.summary)   // 무기는 이름 요약에서 제외
        assertEquals("D-15", line.remainLabel)
        assertTrue(!line.urgent)
        assertTrue(!line.hasCollab)
    }

    @Test
    fun gameLineFlagsCollabAndUrgency() {
        val list = collabAndRegular()
        val line = ScheduleLogic.gameLines(list, emptyList(), base).single()
        assertEquals("스타레일", line.shortName)
        assertTrue(line.hasCollab)
        assertEquals("v4.4 · 스파키 외 1", line.summary)   // 임박한 일반 그룹 기준(콜라보는 종료 미정이라 뒤)
    }

    @Test
    fun gameLineSkipsGamesWithoutPickups() {
        // 젠레스는 상류에 픽업 데이터가 없다 → 줄 자체를 만들지 않는다.
        val lines = ScheduleLogic.gameLines(listOf(banner("A", 5, "6.7")), emptyList(), base)
        assertEquals(listOf("원신"), lines.map { it.shortName })
    }

    @Test
    fun gameLineShowsUnknownEndWhenAllPickupsUndated() {
        val unknown = banner("토오사카 린", 0, "4.4", game = Game.HSR).copy(endMillis = 0L)
        val line = ScheduleLogic.gameLines(listOf(unknown), emptyList(), base).single()
        assertEquals("종료 미정", line.remainLabel)
        assertTrue(!line.urgent)
    }

    // ── 콜라보 분리 ──────────────────────────────────────────────────────────
    // 스타레일 4.4 = Fate 콜라보 + 일반 픽업이 같은 버전. 카드가 버전 전체 목록이 되면 안 된다.

    private fun hsr(name: String, endDays: Long, startDays: Long, type: String = "character") =
        banner(name, endDays, "4.4", type = type, startDays = startDays, game = Game.HSR)

    private fun collabAndRegular(): List<GachaBanner> = listOf(
        hsr("토오사카 린", 0, 2).copy(endMillis = 0L),       // 콜라보(종료 미정)
        hsr("길가메시", 0, 2).copy(endMillis = 0L),
        hsr("고요히 빛나는 불티", 0, 2, "weapon").copy(endMillis = 0L),  // 콜라보 전용 광추(이름엔 단서 없음)
        hsr("스파키", 9, -7),                                  // 같은 버전 일반 픽업
        hsr("히메코•노바", 9, -7),
    )

    @Test
    fun collabGroupKeepsOnlyCollabPickups() {
        val groups = ScheduleLogic.buildVersionGroups(collabAndRegular(), "all")
        val collab = ScheduleLogic.collabGroups(groups).single()
        assertEquals(
            listOf("토오사카 린", "길가메시", "고요히 빛나는 불티"),
            collab.pickups.map { it.name },
        )
    }

    @Test
    fun collabPhaseWeaponIsIncludedEvenWithoutNameMatch() {
        // 전용 광추는 이름 화이트리스트에 안 걸리지만 같은 시작·종료 페이즈라 함께 묶인다.
        val groups = ScheduleLogic.buildVersionGroups(collabAndRegular(), "all")
        val collab = ScheduleLogic.collabGroups(groups).single()
        assertTrue(collab.pickups.any { it.name == "고요히 빛나는 불티" })
        assertTrue(!isCollabBanner(collab.pickups.first { it.name == "고요히 빛나는 불티" }))
    }

    @Test
    fun regularGroupKeepsNonCollabPickupsOfSameVersion() {
        // 예전엔 콜라보가 낀 버전 그룹을 통째로 빼서 스파키·히메코가 일정에서 사라졌다.
        val groups = ScheduleLogic.buildVersionGroups(collabAndRegular(), "all")
        val regular = ScheduleLogic.regularGroups(groups).single()
        assertEquals(listOf("스파키", "히메코•노바"), regular.pickups.map { it.name })
        assertEquals(base + 9 * day, regular.nearestEnd)   // 날짜도 남은 픽업 기준으로 다시 잡힌다
    }

    @Test
    fun collabGroupDatesComeFromCollabPickupsOnly() {
        val groups = ScheduleLogic.buildVersionGroups(collabAndRegular(), "all")
        val collab = ScheduleLogic.collabGroups(groups).single()
        assertTrue(collab.isEndUnknown)                    // 콜라보 3종이 전부 종료 미정
        assertEquals("종료 미정", collab.remainLabel(base))
        assertEquals(base + 2 * day, collab.start)         // 일반 픽업의 이른 시작(-7일)을 끌어오지 않는다
    }

    @Test
    fun groupWithoutCollabIsUntouched() {
        val groups = ScheduleLogic.buildVersionGroups(listOf(banner("A", 5, "6.6"), banner("B", 2, "6.6")), "all")
        assertTrue(ScheduleLogic.collabGroups(groups).isEmpty())
        assertEquals(2, ScheduleLogic.regularGroups(groups).single().pickups.size)
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
    //
    // 아래 검사들은 **픽업 페이즈 라벨**만 본다. buildSchedule 은 방송 줄(BroadcastSchedule)도
    // 함께 내놓는데, 그건 배너 종료일에서 역산한 값이라 여기 관심사가 아니다 — kind 로 거른다.

    @Test
    fun buildScheduleLabelsTwoPhasesOfSameVersion() {
        // 한 버전에 페이즈 2개 → 종료 이른 쪽이 전반, 늦은 쪽이 후반.
        val banners = listOf(banner("전", 3, "6.6"), banner("후", 24, "6.6"))
        val titles = ScheduleLogic.buildSchedule(banners, emptyList(), emptyList()).filter { it.kind == "패치" }.map { it.title }
        assertEquals(listOf("v6.6 전반 픽업 종료", "v6.6 후반 픽업 종료"), titles)
    }

    @Test
    fun buildScheduleLabelsThirdPhaseNumerically() {
        val banners = listOf(banner("1", 3, "6.6"), banner("2", 24, "6.6"), banner("3", 45, "6.6"))
        val titles = ScheduleLogic.buildSchedule(banners, emptyList(), emptyList()).filter { it.kind == "패치" }.map { it.title }
        assertEquals(listOf("v6.6 전반 픽업 종료", "v6.6 후반 픽업 종료", "v6.6 3페이즈 픽업 종료"), titles)
    }

    @Test
    fun buildScheduleTreatsLatestSinglePhaseAsFirstHalf() {
        // 버전당 페이즈가 1개씩일 때: 최신 버전 = 전반(후반 미게시), 이전 버전 = 후반(전반 종료됨).
        val banners = listOf(banner("구", 3, "6.5"), banner("신", 24, "6.6"))
        val titles = ScheduleLogic.buildSchedule(banners, emptyList(), emptyList()).filter { it.kind == "패치" }.map { it.title }
        assertEquals(listOf("v6.5 후반 픽업 종료", "v6.6 전반 픽업 종료"), titles)
    }

    @Test
    fun buildScheduleOmitsVersionPrefixWhenBlank() {
        val titles = ScheduleLogic.buildSchedule(listOf(banner("무버전", 3, "")), emptyList(), emptyList()).filter { it.kind == "패치" }.map { it.title }
        assertEquals(listOf("전반 픽업 종료"), titles)
    }

    @Test
    fun buildScheduleMergesEventsAndChallengesSortedByTarget() {
        val entries = ScheduleLogic.buildSchedule(
            banners = listOf(banner("픽업", 5, "6.6")),
            events = listOf(GameEvent(game = gi.displayName, name = "이벤트", reward = "원석", endMillis = base + 2 * day)),
            challenges = listOf(GameChallenge(game = gi.displayName, name = "심경", typeName = "심연", reward = "원석", endMillis = base + 9 * day)),
        )
        // 방송 줄은 이 검사의 관심사가 아니다(배너 종료일에서 역산한 별개 값) — 걸러 낸다.
        val merged = entries.filter { it.kind != "방송" }
        assertEquals(listOf("이벤트", "패치", "콘텐츠"), merged.map { it.kind })
        assertEquals(listOf("이벤트", "v6.6 전반 픽업 종료", "심경"), merged.map { it.title })
        assertEquals(gi.key, merged.first().gameKey)
        assertEquals(gi.color, merged.first().colorArgb)   // 색은 ARGB Long 으로만 전달(플랫폼이 변환)
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
