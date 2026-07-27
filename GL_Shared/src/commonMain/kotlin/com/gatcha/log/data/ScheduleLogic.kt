package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis

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
            if (game.enneadKey == null) continue
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
                out += ScheduleEntry(game.key, game.shortName, game.color, "패치", title, "", ph.key, false)
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
        return out.sortedBy { it.target }
    }

    /** 헤더 드롭다운(filter)에 맞춘 일정 — "all"이면 전체, 특정 게임이면 그 게임만. */
    fun filteredEntries(entries: List<ScheduleEntry>, filter: String): List<ScheduleEntry> =
        if (filter == "all") entries else entries.filter { it.gameKey == filter }

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
        groups.filter { g -> g.pickups.any { isCollabBanner(it) } }

    /** 콜라보를 제외한 일반 버전 그룹. */
    fun regularGroups(groups: List<VersionGroup>): List<VersionGroup> =
        groups.filterNot { g -> g.pickups.any { isCollabBanner(it) } }
}
