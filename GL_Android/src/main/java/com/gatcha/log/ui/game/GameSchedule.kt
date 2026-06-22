package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.companionWeapons
import com.gatcha.log.data.dhLabel
import com.gatcha.log.data.unpairedWeapons
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

// ============================================================
// 통합 게임 일정 — 패치(게임별 다음 시작/종료)·진행 이벤트·정기 콘텐츠를 하나의 모델로 합쳐 날짜순 정렬.
// iOS(SwiftUI) GameScheduleSection/GameSchedulePage 패리티. (design_gameinfo_schedule_mockup.html 기준)
// ============================================================

data class ScheduleEntry(
    val gameKey: String,
    val gameShort: String,
    val color: Color,
    val kind: String,        // "패치" | "이벤트" | "콘텐츠"
    val title: String,
    val sub: String,
    val target: Long,
    val isStart: Boolean,
)

fun buildSchedule(banners: List<GachaBanner>, events: List<GameEvent>, challenges: List<GameChallenge>): List<ScheduleEntry> {
    val out = mutableListOf<ScheduleEntry>()
    // ① 픽업 페이즈 — 게임별로 종료일 기준 페이즈(전반/후반) 분리해 각각 'v6.6 전반 픽업 종료'처럼 표기.
    // (ennead가 버전 종료 시각을 안 줘서 '버전' 대신 '픽업 페이즈' 기준. 전반/후반 판별은 구 GameBannerCard 로직 이식)
    for (game in GameData.games) {
        if (game.enneadKey == null) continue
        val gb = banners.filter { it.game == game.displayName }
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
                v == lastVersion -> "전반"  // 최신 버전 단일 페이즈 = 전반(후반 미게시)
                else -> "후반"               // 이전 버전 단일 페이즈 = 후반(전반 종료됨)
            }
            val title = if (v.isBlank()) "$phaseLabel 픽업 종료" else "v$v $phaseLabel 픽업 종료"
            out += ScheduleEntry(game.key, game.shortName, game.color.toColor(), "패치", title, "", ph.key, false)
        }
    }
    // ② 진행 중인 이벤트
    for (ev in events) {
        val g = GameData.byNameOrNull(ev.game)
        out += ScheduleEntry(g?.key ?: ev.game, g?.shortName ?: ev.game, ev.gameColor.toColor(), "이벤트", ev.name, ev.reward, ev.endMillis, false)
    }
    // ③ 정기 콘텐츠
    for (ch in challenges) {
        val g = GameData.byNameOrNull(ch.game)
        out += ScheduleEntry(g?.key ?: ch.game, g?.shortName ?: ch.game, ch.gameColor.toColor(), "콘텐츠", ch.name, ch.reward, ch.endMillis, false)
    }
    return out.sortedBy { it.target }
}

fun filteredEntries(entries: List<ScheduleEntry>, filter: String): List<ScheduleEntry> =
    if (filter == "all") entries else entries.filter { it.gameKey == filter }

fun filteredPickups(banners: List<GachaBanner>, filter: String): List<GachaBanner> {
    val list = if (filter == "all") banners
    else GameData.games.firstOrNull { it.key == filter }?.let { g -> banners.filter { it.game == g.displayName } } ?: emptyList()
    return list.sortedBy { it.endMillis }
}

private fun scheduleKindColor(kind: String): Color = when (kind) {
    "패치" -> Color(0xFF6C8AE4)
    "이벤트" -> Color(0xFFE0A93B)
    else -> Color(0xFF2BB673)
}

private val Urgent = Color(0xFFE8634A)

// 무기(검) 아이콘 — Material/SF Symbols에 검 심볼이 없어 커스텀 벡터로 정의(iOS SwordShape와 동일 형상).
val SwordIcon: ImageVector = ImageVector.Builder("Sword", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 2f); lineTo(13.2f, 5f); lineTo(13.2f, 14f); lineTo(10.8f, 14f); lineTo(10.8f, 5f); close()   // 칼날
        moveTo(8f, 14f); lineTo(16f, 14f); lineTo(16f, 16f); lineTo(8f, 16f); close()                             // 코등이
        moveTo(11.1f, 16f); lineTo(12.9f, 16f); lineTo(12.9f, 20f); lineTo(11.1f, 20f); close()                   // 손잡이
        moveTo(10.4f, 20f); lineTo(13.6f, 20f); lineTo(13.6f, 22f); lineTo(10.4f, 22f); close()                   // 폼멜
    }
}.build()

private val Track = Color(0xFFEDEFF3)
private val CharBadge = Color(0xFF5B8DEF)
private val WeapBadge = Color(0xFFE0883B)

// 픽업 그룹 헤더 — 종류 배지(캐릭터=블루 / 무기=앰버) + 개수.
@Composable
private fun PickupGroupHeader(isWeapon: Boolean, count: Int) {
    Row(
        Modifier.padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(color = if (isWeapon) WeapBadge else CharBadge, shape = RoundedCornerShape(999.dp)) {
            Text(if (isWeapon) "무기" else "캐릭터", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

// "{종류} N개 더보기 ›" 푸터 — 전체 픽업 페이지로.
@Composable
private fun PickupMoreFooter(label: String, more: Int, onMore: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onMore() }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("$label ${more}개 더보기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, tint = accent, modifier = Modifier.size(14.dp))
    }
}

// 픽업 배너를 캐릭터/무기 2그룹으로 분류. limit != null이면 그룹별 상위 N개만 + 더보기 푸터.
@Composable
private fun PickupGroups(pickups: List<GachaBanner>, limit: Int? = null, onMore: (() -> Unit)? = null) {
    val chars = pickups.filter { it.type != "weapon" }
    val weapons = unpairedWeapons(pickups)   // 동반 무기는 캐릭터 카드에 접어 표시 → 독립 목록에선 제외
    if (chars.isNotEmpty()) {
        PickupGroupHeader(isWeapon = false, count = chars.size)
        (if (limit != null) chars.take(limit) else chars).forEach { PickupItem(it, companionWeapons(it, pickups)) }
        if (limit != null && chars.size > limit && onMore != null) PickupMoreFooter("캐릭터", chars.size - limit, onMore)
    }
    if (weapons.isNotEmpty()) {
        if (chars.isNotEmpty()) Spacer(Modifier.height(16.dp))
        PickupGroupHeader(isWeapon = true, count = weapons.size)
        (if (limit != null) weapons.take(limit) else weapons).forEach { PickupItem(it) }
        if (limit != null && weapons.size > limit && onMore != null) PickupMoreFooter("무기", weapons.size - limit, onMore)
    }
}

// 픽업 아이템 — 좌측 게임색 바 + 아바타 + 이름/버전 + 잔여(dhLabel) + 진행바. (design_pickup_list_final_mockup.html)
@Composable
fun PickupItem(banner: GachaBanner, companions: List<GachaBanner> = emptyList()) {
    val c = banner.gameColor.toColor()
    val isWeapon = banner.type == "weapon"
    val urgent = banner.dDay() <= 3
    val ddColor = if (urgent) Urgent else c
    val short = GameData.byNameOrNull(banner.game)?.shortName ?: banner.game
    val sub = if (banner.version.isBlank()) short else "$short · v${banner.version}"
    val hasProg = banner.startMillis > 0 && banner.endMillis > banner.startMillis
    val frac = if (hasProg)
        ((System.currentTimeMillis() - banner.startMillis).toFloat() / (banner.endMillis - banner.startMillis)).coerceIn(0f, 1f)
    else 0f
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp).height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(c.copy(alpha = 0.05f))
            .border(1.dp, DividerColor, RoundedCornerShape(14.dp)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(c))
        Column(Modifier.weight(1f).padding(horizontal = 11.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    Modifier.size(34.dp).clip(if (isWeapon) RoundedCornerShape(10.dp) else CircleShape).background(c),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isWeapon) Icon(SwordIcon, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    else Text(banner.name.take(1), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(banner.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(dhLabel(banner.endMillis), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ddColor, maxLines = 1)
                    Text("~" + DateUtil.shortDate(banner.endMillis), fontSize = 9.sp, color = TextSecondary)
                }
            }
            if (companions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = DividerColor.copy(alpha = 0.6f))
                companions.forEach { w ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(WeapBadge.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(SwordIcon, null, tint = WeapBadge, modifier = Modifier.size(11.dp))
                        }
                        Text("동반 무기 · ${w.name}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (hasProg) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(Track)) {
                    Box(
                        Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(99.dp))
                            .background(if (urgent) Urgent else c.copy(alpha = 0.35f)),
                    )
                }
                if (urgent) {
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(frac * 100).roundToInt()}% 경과 · 막바지", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Urgent)
                        Text("${DateUtil.shortDate(banner.startMillis)} → ${DateUtil.shortDate(banner.endMillis)}", fontSize = 9.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

// 일정 행 — 종류 태그 + 제목 + D-day.
@Composable
fun ScheduleEntryRow(e: ScheduleEntry) {
    val accent = LocalAccent.current
    val d = ((e.target - System.currentTimeMillis()) / 86_400_000L).toInt()
    val ddColor = if (d in 0..3) Urgent else accent
    val kc = scheduleKindColor(e.kind)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(e.color))
        Column(Modifier.weight(1f)) {
            Surface(color = kc.copy(alpha = 0.13f), shape = RoundedCornerShape(999.dp)) {
                Text(e.kind, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = kc, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.sub.isNotBlank()) Text(e.sub, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(dhLabel(e.target), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ddColor, maxLines = 1)
            Text((if (e.isStart) "" else "~") + DateUtil.shortDate(e.target), fontSize = 10.sp, color = TextSecondary)
        }
    }
}

// 통합 게임 일정 섹션 — 헤더 드롭다운(filter)으로 게임 분리. 픽업 알약 전부 + 상위 3개 일정, 초과 시 전체 페이지로.
@Composable
fun GameScheduleSection(
    entries: List<ScheduleEntry>,
    banners: List<GachaBanner>,
    filter: String,
    onSeeAll: () -> Unit,
    onSeePickups: () -> Unit,
) {
    val accent = LocalAccent.current
    val items = filteredEntries(entries, filter)
    val pickups = filteredPickups(banners, filter)
    val top = items.take(3)
    Text("게임 일정", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    Text("픽업 배너와 다가오는 패치·이벤트·콘텐츠를 모았어요.", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (pickups.isNotEmpty()) {
                PickupGroups(pickups, limit = 3, onMore = onSeePickups)
                if (top.isNotEmpty()) { Spacer(Modifier.height(4.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(4.dp)) }
            }
            if (top.isNotEmpty()) {
                top.forEachIndexed { i, e -> ScheduleEntryRow(e); if (i < top.lastIndex) HorizontalDivider(color = DividerColor) }
            } else if (pickups.isEmpty()) {
                Text("예정된 일정이 없어요.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (items.size > 3) {
                HorizontalDivider(color = DividerColor)
                Row(
                    Modifier.fillMaxWidth().clickable { onSeeAll() }.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("전체 일정 보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("${items.size + pickups.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent.copy(alpha = 0.6f))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = accent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// 게임 단위 그룹 — 게임 배지(약어 색칩 + 게임명) 헤더 + 해당 게임의 픽업/일정 카드.
@Composable
private fun GameScheduleGroup(game: Game, entries: List<ScheduleEntry>, pickups: List<GachaBanner>) {
    Row(
        Modifier.padding(top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(color = game.color.toColor(), shape = RoundedCornerShape(8.dp)) {
            Text(game.abbr, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
        }
        Text(game.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (pickups.isNotEmpty()) {
                PickupGroups(pickups)
                if (entries.isNotEmpty()) { Spacer(Modifier.height(4.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(4.dp)) }
            }
            entries.forEachIndexed { i, e -> ScheduleEntryRow(e); if (i < entries.lastIndex) HorizontalDivider(color = DividerColor) }
        }
    }
}

// 전체 게임 일정 페이지 콘텐츠 (SectionPage 안에서 호스팅 — 헤더/스크롤은 SectionPage 제공). 게임별 그룹 분리.
@Composable
fun GameScheduleFullContent(
    banners: List<GachaBanner>,
    events: List<GameEvent>,
    challenges: List<GameChallenge>,
    filter: String,
) {
    val entries = filteredEntries(buildSchedule(banners, events, challenges), filter)
    val pickups = filteredPickups(banners, filter)
    val games = GameData.games.filter { g -> entries.any { it.gameKey == g.key } || pickups.any { it.game == g.displayName } }
    Text("게임 일정", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    Text("픽업 배너와 패치·이벤트·정기 콘텐츠를 게임별로 모았어요.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
    if (games.isEmpty()) {
        Text("예정된 일정이 없어요.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
    } else {
        games.forEach { g ->
            GameScheduleGroup(g, entries.filter { it.gameKey == g.key }, pickups.filter { it.game == g.displayName })
        }
    }
}

// 픽업 전용 전체 페이지 콘텐츠 — 캐릭터/무기 그룹으로만 분리, 종료 임박순. (design_pickup_list_final_mockup.html ②)
@Composable
fun GamePickupFullContent(banners: List<GachaBanner>, filter: String) {
    val pickups = filteredPickups(banners, filter)
    Text("전체 픽업", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    Text("진행 중인 캐릭터·무기 픽업을 종료 임박순으로 모았어요.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 14.dp))
    if (pickups.isEmpty()) {
        Text("진행 중인 픽업이 없어요.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
    } else {
        GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { PickupGroups(pickups) }
        }
    }
}
