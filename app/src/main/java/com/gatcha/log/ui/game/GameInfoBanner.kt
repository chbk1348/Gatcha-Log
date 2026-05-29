package com.gatcha.log.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.MonthlyLedger
import com.gatcha.log.data.PatchInfo
import com.gatcha.log.ui.components.BannerSkeleton
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary

// ============================================================ 통합 게임 탭 (배너·전투·일지)
/** 게임 칩으로 선택 게임의 픽업 배너·전투 진행도·수입 일지를 번갈아 표시 (세로 스크롤 단축). */
@Composable
fun GameTabbedSection(
    banners: List<GachaBanner>,
    combat: List<CombatMode>,
    ledgers: List<MonthlyLedger>,
    isRefreshing: Boolean,
) {
    val games = GameData.attendanceGames // 원신·스타레일·젠레스
    var selectedKey by remember { mutableStateOf("genshin") }
    val sel = games.firstOrNull { it.key == selectedKey } ?: games.first()
    val selBanners = banners.filter { it.game == sel.displayName }
    val selCombat = combat.filter { it.game == sel.displayName }
    val selLedger = ledgers.firstOrNull { it.game == sel.displayName }
    val empty = selBanners.isEmpty() && selCombat.isEmpty() && selLedger == null

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            games.forEach { g -> GameTabChip(g, g.key == selectedKey) { selectedKey = g.key } }
        }
        Spacer(Modifier.height(16.dp))
        when {
            empty && isRefreshing -> BannerSkeleton()
            empty -> GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Text("${sel.shortName} 정보가 아직 없어요", fontSize = 13.sp, color = TextSecondary)
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selBanners.isNotEmpty()) GameContentBlock("픽업 배너 D-Day") { GameBannerCard(sel, selBanners) }
                if (selCombat.isNotEmpty()) GameContentBlock("전투 콘텐츠 진행도") { CombatGameCard(sel, selCombat) }
                if (selLedger != null) GameContentBlock("이번 달 수입 일지") { LedgerCard(selLedger) }
            }
        }
    }
}

@Composable
private fun GameContentBlock(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun GameTabChip(game: Game, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) game.color else Color.White,
        border = BorderStroke(1.5.dp, if (selected) game.color else DividerColor),
    ) {
        Text(
            game.shortName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else game.color,
        )
    }
}

@Composable
private fun GameBannerCard(game: Game, banners: List<GachaBanner>) {
    // 종료일 빠른 순으로 페이즈 정렬 후, 버전 기준으로 전반/후반 판별
    val phases = banners.groupBy { it.endMillis }.toSortedMap().values.toList()
    val labels = phaseLabels(phases)

    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // 게임 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Box(Modifier.size(10.dp).background(game.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(game.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            phases.forEachIndexed { i, phaseBanners ->
                PhaseBlock(labels[i], phaseBanners, game.color)
                if (i < phases.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

/**
 * 버전 기준 전반/후반 판별.
 * - 한 버전에 페이즈가 2개 이상: 시작 순서대로 전반 · 후반 · 3페이즈…
 * - 한 버전에 페이즈가 1개뿐: 뒤에 다른(이후) 버전이 있으면 그 버전의 전반은 이미 끝난 것 → "후반",
 *   가장 마지막(최신) 버전이면 → "전반".
 */
private fun phaseLabels(phasesByEndAsc: List<List<GachaBanner>>): List<String> {
    val versions = phasesByEndAsc.map { it.firstOrNull()?.version.orEmpty() }
    val lastVersion = versions.lastOrNull()
    val total = versions.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return versions.map { v ->
        val pos = seen.getOrDefault(v, 0)
        seen[v] = pos + 1
        when {
            (total[v] ?: 1) >= 2 -> phaseLabel(pos)
            v == lastVersion -> "전반"
            else -> "후반"
        }
    }
}

private fun phaseLabel(index: Int): String = when (index) {
    0 -> "전반"
    1 -> "후반"
    else -> "${index + 1}페이즈"
}

@Composable
private fun PhaseBlock(phase: String, banners: List<GachaBanner>, gameColor: Color) {
    val first = banners.first()
    val version = banners.firstOrNull { it.version.isNotBlank() }?.version.orEmpty()
    val start = banners.minOf { it.startMillis }
    val charNames = banners.filter { it.type != "weapon" }.map { it.name }
    val hasWeapon = banners.any { it.type == "weapon" }
    val title = (charNames + if (hasWeapon) listOf("무기 기원") else emptyList()).joinToString(" · ")
    val period = if (start > 0) {
        "${DateUtil.shortDateTime(start)} ~ ${DateUtil.shortDateTime(first.endMillis)}"
    } else {
        DateUtil.shortDateTime(first.endMillis)
    }

    Column(Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = gameColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(phase, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gameColor, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
                if (version.isNotBlank()) {
                    Text("v$version", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
            Surface(color = gameColor, shape = RoundedCornerShape(8.dp)) {
                Text(
                    first.dDayLabel(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(period, fontSize = 11.sp, color = TextSecondary)
    }
}

// ============================================================ 패치 일정
private fun computePatchInfo(banners: List<GachaBanner>): List<PatchInfo> {
    val now = System.currentTimeMillis()
    return GameData.games.filter { it.enneadKey != null }.mapNotNull { game ->
        val gb = banners.filter { it.game == game.displayName }
        if (gb.isEmpty()) return@mapNotNull null
        val futureStart = gb.mapNotNull { it.startMillis.takeIf { s -> s > now } }.minOrNull()
        if (futureStart != null) {
            val v = gb.firstOrNull { it.startMillis == futureStart }?.version ?: ""
            PatchInfo(game.displayName, v, futureStart, isStart = true)
        } else {
            val end = gb.maxOf { it.endMillis }
            val v = gb.firstOrNull { it.endMillis == end }?.version ?: ""
            PatchInfo(game.displayName, v, end, isStart = false)
        }
    }
}

@Composable
fun PatchSection(banners: List<GachaBanner>) {
    val accent = LocalAccent.current
    val patches = computePatchInfo(banners)
    if (patches.isEmpty()) return
    Text("패치 일정", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    Text(
        "게임 버전 업데이트의 시작·종료까지 남은 기간이에요.",
        fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp),
    )
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            patches.forEachIndexed { i, p ->
                val d = p.dDay()
                val v = if (p.version.isNotBlank()) "v${p.version} " else ""
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(8.dp).background(p.gameColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            // "원신 v5.4 새 버전 시작" / "원신 v5.3 버전 종료" — 무슨 일이 일어나는지 명시
                            Text(
                                GameData.byName(p.game).shortName + " " +
                                    if (p.isStart) "${v}새 버전 시작" else "${v}버전 종료",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            )
                            // 실제 날짜로 모호함 제거
                            Text(DateUtil.shortLabelWithWeekday(p.targetMillis), fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (d > 0) "D-$d" else if (d == 0) "D-DAY" else "—", color = accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(if (p.isStart) "시작까지" else "종료까지", fontSize = 10.sp, color = TextSecondary)
                    }
                }
                if (i < patches.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}
