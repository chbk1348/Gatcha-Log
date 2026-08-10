package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlin.math.ceil

/**
 * 통합 게임 일정 — 패치(픽업 페이즈)·진행 이벤트·정기 콘텐츠를 하나의 모델로 합쳐 날짜순 정렬.
 *
 * 원래 GL_Android(ui/game/GameSchedule.kt)와 GL_IOS(Screens/GameInfo/GameInfoView.swift)에
 * 같은 로직이 각각 구현돼 있었다(전반/후반 페이즈 판정·버전 묶음 포함). 판정 규칙이 갈리면
 * 두 플랫폼이 서로 다른 일정을 보여주므로 계산은 여기 commonMain 하나로 둔다.
 *
 * 색은 Compose Color / SwiftUI Color 를 쓸 수 없어 ARGB Long 으로만 전달하고,
 * 각 UI 레이어가 toColor() / Color(argb64:) 로 변환한다.
 */

/** 일정 한 줄. [target] 은 정렬 기준(밀리초) — 패치=페이즈 종료, 이벤트·콘텐츠=종료. */
data class ScheduleEntry(
    val gameKey: String,
    val gameShort: String,
    /** 게임 대표색 ARGB(0xAARRGGBB). */
    val colorArgb: Long,
    val kind: String,        // "패치" | "이벤트" | "콘텐츠"
    val title: String,
    val sub: String,
    val target: Long,
    /** 패치 시작 여부(라벨·날짜 접두 분기). */
    val isStart: Boolean,
    /** 이 줄이 픽업 페이즈 종료일 때 그 페이즈의 픽업들(타임라인 칩용). 그 외엔 빈 목록. */
    val pickups: List<GachaBanner> = emptyList(),
) {
    /** 남은 일수(올림). 음수면 지남. */
    fun dDay(nowMillis: Long = currentTimeMillis()): Int =
        ceil((target - nowMillis) / (1000.0 * 60 * 60 * 24)).toInt()
}

/**
 * 타임라인의 하루 — 같은 날 끝나는 일정을 묶는다.
 * 상세 페이지가 '버전 축'이 아니라 '마감 날짜 축'으로 읽히도록 하는 단위.
 */
data class ScheduleDay(
    val month: Int,
    val day: Int,
    val weekdayKo: String,
    val dDay: Int,
    val entries: List<ScheduleEntry>,
) {
    /** 마감 임박 강조(빨강) — D-3 이내. 나머지는 눌러서 임박한 것만 튀게 한다. */
    val urgent: Boolean get() = dDay in 0..3
}

/** 섹션 진입 카드의 게임 한 줄 — "원신  v6.7 · 콜롬비나 외 1   D-15". */
data class GameScheduleLine(
    val gameKey: String,
    val shortName: String,
    val colorArgb: Long,
    /** "v6.7 · 콜롬비나 외 1" (버전 없으면 이름만). */
    val summary: String,
    /** "D-15" / "종료 미정". */
    val remainLabel: String,
    val urgent: Boolean,
    /** 콜라보 픽업이 진행 중이면 true — 한 줄에 콜라보 뱃지를 붙인다. */
    val hasCollab: Boolean,
)

/** 상세 페이지 상단 요약 3칸. */
data class ScheduleSummary(
    val weekDeadlines: Int,
    val activePickups: Int,
    val extras: Int,
)

/** 버전 단위 묶음 — (게임, 버전) 하나당 카드 한 장. */
data class VersionGroup(
    val game: Game,
    val version: String,
    val pickups: List<GachaBanner>,
    /** 카드 정렬 기준 = 픽업 종료 중 가장 이른 값. */
    val nearestEnd: Long,
    /** 버전 시작일 = 픽업 시작 중 가장 이른 값(0 제외). */
    val start: Long,
    /** 버전 종료일 = 픽업 종료 중 가장 늦은 값. */
    val end: Long,
) {
    /** 종료일이 있는 픽업이 하나도 없음(전부 종료 미공지) — D-day·진행바를 못 그린다. */
    val isEndUnknown: Boolean get() = nearestEnd <= 0L

    /** 카드 헤더의 남은 시간 표기 — 전부 종료 미정이면 "종료 미정". */
    fun remainLabel(nowMillis: Long = currentTimeMillis()): String =
        if (isEndUnknown) "종료 미정" else dhLabel(nearestEnd, nowMillis)
}

object ScheduleLogic {

    /** 일정 종류별 배지 색(ARGB) — 양 플랫폼 동일 팔레트. */
    fun kindColorArgb(kind: String): Long = when (kind) {
        "패치" -> 0xFF6C8AE4
        "이벤트" -> 0xFFE0A93B
        else -> 0xFF2BB673
    }

    /**
     * 픽업 페이즈·이벤트·정기 콘텐츠를 합쳐 임박순 정렬.
     *
     * ennead 가 버전 종료 시각을 주지 않아 '버전' 대신 '픽업 페이즈'(종료일 그룹) 기준으로 끊고,
     * 한 버전에 페이즈가 2개 이상이면 순서대로 전반/후반, 1개뿐이면 최신 버전=전반(후반 미게시)
     * / 이전 버전=후반(전반 종료됨)으로 판정한다.
     */
    fun buildSchedule(
        banners: List<GachaBanner>,
        events: List<GameEvent>,
        challenges: List<GameChallenge>,
    ): List<ScheduleEntry> {
        val out = mutableListOf<ScheduleEntry>()
        // ① 픽업 페이즈
        for (game in GameData.games) {
            // ⚠️ `enneadKey` 로 거르지 않는다. 젠존제는 전용 경로([EnneadApi.fetchZzz])로 받아서
            // 그 키가 없는데, 여기서 걸러지는 바람에 **배너가 들어와도 픽업 줄이 안 생겼다.**
            // 배너 유무는 바로 아래에서 판단하므로 이 조건은 이중 방어였고, 실제로는 누락만 만들었다.
            // 종료 미정 픽업은 '픽업 종료' 일정 줄을 만들 수 없다(날짜가 없음) → 페이즈 계산에서 제외.
            val gb = banners.filter { it.game == game.displayName && !it.isEndUnknown }
            if (gb.isEmpty()) continue
            val phases = gb.groupBy { it.endMillis }.entries.sortedBy { it.key } // 종료일 오름차순 페이즈
            val versions = phases.map { it.value.firstOrNull()?.version ?: "" }
            val lastVersion = versions.lastOrNull()
            val totalByVer = versions.groupingBy { it }.eachCount()
            val seen = mutableMapOf<String, Int>()
            phases.forEachIndexed { idx, ph ->
                val v = versions[idx]
                val pos = seen[v] ?: 0; seen[v] = pos + 1
                val phaseLabel = when {
                    (totalByVer[v] ?: 1) >= 2 -> if (pos == 0) "전반" else if (pos == 1) "후반" else "${pos + 1}페이즈"
                    v == lastVersion -> "전반"
                    else -> "후반"
                }
                val title = if (v.isBlank()) "$phaseLabel 픽업 종료" else "v$v $phaseLabel 픽업 종료"
                // 타임라인이 이 줄 아래에 캐릭터 칩을 붙일 수 있도록 해당 페이즈 픽업을 함께 싣는다.
                out += ScheduleEntry(game.key, game.shortName, game.color, "패치", title, "", ph.key, false, ph.value)
            }
        }
        // ② 진행 중인 이벤트
        for (ev in events) {
            val g = GameData.byNameOrNull(ev.game)
            out += ScheduleEntry(g?.key ?: ev.game, g?.shortName ?: ev.game, ev.gameColor, "이벤트", ev.name, ev.reward, ev.endMillis, false)
        }
        // ③ 정기 콘텐츠
        for (ch in challenges) {
            val g = GameData.byNameOrNull(ch.game)
            out += ScheduleEntry(g?.key ?: ch.game, g?.shortName ?: ch.game, ch.gameColor, "콘텐츠", ch.name, ch.reward, ch.endMillis, false)
        }
        // 버전 특별 방송은 **여기 섞지 않는다.** 타임라인은 '언제 끝나나'를 읽는 자리인데
        // 방송은 시작하는 일정인 데다 역산한 예상값이라, 섞으면 확정된 마감들 사이에서
        // 혼자 성격이 다르다. 별도 탭([BroadcastSchedule])으로 뺐다.
        return out.sortedBy { it.target }
    }

    /** 헤더 드롭다운(filter)에 맞춘 일정 — "all"이면 전체, 특정 게임이면 그 게임만. */
    fun filteredEntries(entries: List<ScheduleEntry>, filter: String): List<ScheduleEntry> =
        if (filter == "all") entries else entries.filter { it.gameKey == filter }

    // ── 상세 페이지: 마감 날짜 타임라인 ──────────────────────────────────────
    // 버전 카드를 쌓던 기존 구성은 같은 정보를 '버전'과 '종류' 두 축으로 훑게 만들었다.
    // 마감일 하나로 묶으면 "다음에 뭐가 끝나지?"에 한 번에 답할 수 있다.

    /**
     * 일정을 **끝나는 날짜별**로 묶어 임박순 정렬. 이미 지난 항목은 버린다.
     * 종료 미정 픽업은 날짜가 없어 여기 못 들어간다 — [undatedPickups] 로 따로 뽑아 상단에 고정한다.
     */
    fun buildDays(entries: List<ScheduleEntry>, nowMillis: Long = currentTimeMillis()): List<ScheduleDay> =
        entries.filter { it.target > 0 }
            .groupBy { DateUtil.dayKey(it.target) }
            .map { (_, list) ->
                val head = list.minByOrNull { it.target }!!
                ScheduleDay(
                    month = DateUtil.month(head.target),
                    day = DateUtil.dayOfMonth(head.target),
                    weekdayKo = DateUtil.weekdayKo(head.target),
                    dDay = head.dDay(nowMillis),
                    entries = list.sortedWith(compareBy({ it.kind != "패치" }, { it.target })), // 픽업 먼저
                )
            }
            .filter { it.dDay >= 0 }
            .sortedWith(compareBy({ it.dDay }, { it.month }, { it.day }))

    /** 종료 시각이 미공지라 타임라인에 올릴 수 없는 픽업(콜라보 등). 상단 고정 카드용. */
    fun undatedPickups(banners: List<GachaBanner>, filter: String): List<GachaBanner> =
        filteredPickups(banners, filter).filter { it.isEndUnknown }

    /** 상세 페이지 상단 요약 3칸 — 이번 주 마감 / 진행 중 픽업 / 이벤트·콘텐츠. */
    fun summarize(
        banners: List<GachaBanner>,
        entries: List<ScheduleEntry>,
        filter: String,
        nowMillis: Long = currentTimeMillis(),
    ): ScheduleSummary {
        val ent = filteredEntries(entries, filter)
        return ScheduleSummary(
            weekDeadlines = ent.count { it.dDay(nowMillis) in 0..7 },
            activePickups = filteredPickups(banners, filter).size,
            extras = ent.count { it.kind != "패치" },
        )
    }

    // ── 섹션 진입 카드: 게임 한 줄 ──────────────────────────────────────────

    /**
     * 게임당 한 줄 요약. 진행 중인 픽업이 없는 게임은 줄 자체를 만들지 않는다
     * (젠레스는 상류에 픽업 데이터가 없어 항상 빠진다).
     * 요약은 **가장 임박한 버전 그룹** 기준 — 그 버전의 캐릭터 픽업 이름 + "외 N".
     */
    fun gameLines(
        banners: List<GachaBanner>,
        entries: List<ScheduleEntry>,
        filter: String,
        nowMillis: Long = currentTimeMillis(),
    ): List<GameScheduleLine> =
        GameData.games.mapNotNull { game ->
            if (filter != "all" && filter != game.key) return@mapNotNull null
            val groups = buildVersionGroups(banners, game.key)
            // 요약은 일반 픽업 기준 — 콜라보는 뱃지로 따로 알리므로 이름 수에 섞지 않는다.
            // (콜라보만 진행 중인 게임이면 그거라도 보여준다.)
            val lead = regularGroups(groups).firstOrNull() ?: groups.firstOrNull()
            val named = lead?.pickups?.filter { it.type != "weapon" }?.ifEmpty { lead.pickups }
            val head = named?.firstOrNull()?.name
            // 픽업이 없는 게임은 **일정 건수로 요약한다** — 예전엔 여기서 걸러내 행 자체가 없었다.
            // 젠존제는 픽업 배너 기능을 뺐고(27.30.x), 명조는 애초에 배너 정보를 안 준다.
            // 그런데 둘 다 이벤트·도전은 있어서, 일정이 있는데도 목록에서 게임이 통째로 빠져 보였다.
            if (lead == null || head == null) return@mapNotNull scheduleOnlyLine(game, entries, nowMillis)
            val more = named.size - 1
            val who = if (more > 0) "$head 외 $more" else head
            GameScheduleLine(
                gameKey = game.key,
                shortName = game.shortName,
                colorArgb = game.color,
                summary = if (lead.version.isBlank()) who else "v${lead.version} · $who",
                remainLabel = if (lead.isEndUnknown) "종료 미정" else "D-${maxOf(0, dDayOf(lead.nearestEnd, nowMillis))}",
                urgent = !lead.isEndUnknown && dDayOf(lead.nearestEnd, nowMillis) in 0..3,
                hasCollab = groups.any { g -> g.pickups.any { isCollabBanner(it) } },
            )
        }

    /**
     * 픽업이 없는 게임의 한 줄 — 남은 일정을 종류별로 세어 요약한다("이벤트 3 · 콘텐츠 1").
     * 예정된 게 하나도 없으면 null(빈 줄을 만들지 않는다).
     */
    private fun scheduleOnlyLine(
        game: Game,
        entries: List<ScheduleEntry>,
        nowMillis: Long,
    ): GameScheduleLine? {
        val mine = entries.filter { it.gameKey == game.key && it.target > nowMillis }
        if (mine.isEmpty()) return null
        val counts = mine.groupingBy { it.kind }.eachCount()
        val summary = counts.entries.joinToString(" · ") { (kind, n) -> "$kind $n" }
        val nearest = mine.minOf { it.target }
        val d = maxOf(0, dDayOf(nearest, nowMillis))
        return GameScheduleLine(
            gameKey = game.key,
            shortName = game.shortName,
            colorArgb = game.color,
            summary = summary,
            remainLabel = "D-$d",
            urgent = d in 0..3,
            hasCollab = false,
        )
    }

    private fun dDayOf(target: Long, nowMillis: Long): Int =
        ceil((target - nowMillis) / (1000.0 * 60 * 60 * 24)).toInt()

    /**
     * 헤더 드롭다운(filter)에 맞춘 픽업 배너 — "all"이면 전체, 특정 게임이면 그 게임만. 종료 임박순.
     * 종료 미정(0)은 임박도를 알 수 없으니 맨 뒤로 — 안 그러면 0 이 가장 이른 값이라 제일 위로 올라온다.
     */
    fun filteredPickups(banners: List<GachaBanner>, filter: String): List<GachaBanner> {
        val list = if (filter == "all") banners
        else GameData.games.firstOrNull { it.key == filter }?.let { g -> banners.filter { it.game == g.displayName } } ?: emptyList()
        return list.sortedWith(compareBy({ it.isEndUnknown }, { it.endMillis }))
    }

    /**
     * 필터 적용 픽업을 (게임, 버전)으로 묶어 임박순 정렬. 버전이 비면 게임명만.
     * 종료 미정 픽업은 nearestEnd/end 집계에서 빼고(0 이 최솟값이 되어 정렬을 흐트러뜨림),
     * 그룹 전체가 미정이면 nearestEnd=0 으로 두고 맨 뒤에 배치한다.
     */
    fun buildVersionGroups(banners: List<GachaBanner>, filter: String): List<VersionGroup> =
        filteredPickups(banners, filter)
            .groupBy { it.game to it.version }
            .mapNotNull { (key, list) ->
                val game = GameData.byNameOrNull(key.first) ?: return@mapNotNull null
                val dated = list.filter { !it.isEndUnknown }
                VersionGroup(
                    game, key.second,
                    list.sortedWith(compareBy({ it.isEndUnknown }, { it.endMillis })),
                    nearestEnd = dated.minOfOrNull { it.endMillis } ?: 0L,
                    start = list.filter { it.startMillis > 0 }.minOfOrNull { it.startMillis } ?: 0L,
                    end = dated.maxOfOrNull { it.endMillis } ?: 0L,
                )
            }
            .sortedWith(compareBy({ it.nearestEnd <= 0L }, { it.nearestEnd }))

    /** 콜라보 픽업이 포함된 버전 그룹(별도 섹션으로 분리 표시). */
    fun collabGroups(groups: List<VersionGroup>): List<VersionGroup> =
        groups.mapNotNull { g -> regroup(g, collabPickupsOf(g)) }

    /**
     * 콜라보를 제외한 일반 버전 그룹.
     * 콜라보가 낀 버전이라도 **나머지 일반 픽업은 그대로 남긴다** — 예전엔 그룹을 통째로 빼서
     * 스타레일 4.4 처럼 콜라보와 일반 픽업이 같은 버전이면 일반 픽업(스파키·히메코 등)이
     * 일정 섹션에서 사라졌다.
     */
    fun regularGroups(groups: List<VersionGroup>): List<VersionGroup> =
        groups.mapNotNull { g ->
            val collab = collabPickupsOf(g).toSet()
            regroup(g, g.pickups.filterNot { it in collab })
        }

    /**
     * 그룹 안에서 콜라보에 속하는 픽업만.
     *
     * 이름 화이트리스트([isCollabBanner])는 캐릭터만 잡으므로, **같은 페이즈(시작·종료가 같은)**
     * 픽업까지 함께 묶는다 — 콜라보 전용 무기/광추는 이름에 캐릭터명이 안 들어가기 때문이다.
     * 예: 스타레일 4.4 Fate 콜라보 — ennead 응답(2026-08-06 확인) 기준 **뽑기 배너는 토오사카 린·길가메시
     * 2명**이고, 전용 광추 2종(고요히 빛나는 불티·보이는 것이 곧 나)이 같은 시작·종료를 공유한다.
     * 세이버·아처는 배너가 아니라 이벤트 보상이라 여기 안 들어온다 — 화이트리스트에 이름이 있어도
     * 매칭할 배너 자체가 없다. 즉 **묶이는 단위는 캐릭터 수가 아니라 페이즈**다.
     */
    private fun collabPickupsOf(g: VersionGroup): List<GachaBanner> {
        val phases = g.pickups.filter { isCollabBanner(it) }
            .map { Triple(it.game, it.startMillis, it.endMillis) }
            .toSet()
        if (phases.isEmpty()) return emptyList()
        return g.pickups.filter { Triple(it.game, it.startMillis, it.endMillis) in phases }
    }

    /** 픽업 부분집합으로 그룹을 다시 만든다(날짜 집계도 그 부분집합 기준). 비면 null. */
    private fun regroup(g: VersionGroup, picks: List<GachaBanner>): VersionGroup? {
        if (picks.isEmpty()) return null
        val dated = picks.filter { !it.isEndUnknown }
        return g.copy(
            pickups = picks,
            nearestEnd = dated.minOfOrNull { it.endMillis } ?: 0L,
            start = picks.filter { it.startMillis > 0 }.minOfOrNull { it.startMillis } ?: 0L,
            end = dated.maxOfOrNull { it.endMillis } ?: 0L,
        )
    }
}
