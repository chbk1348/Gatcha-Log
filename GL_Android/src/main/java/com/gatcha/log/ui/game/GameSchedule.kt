package com.gatcha.log.ui.game

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.gatcha.log.data.collabTitle
import com.gatcha.log.data.dhLabel
import com.gatcha.log.data.isCollabBanner
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
private val CollabBadge = Color(0xFF6D5AE6)

// 콜라보 배너 표식 — 이름 옆 작은 알약. (스타레일 × Fate 등)
@Composable
private fun CollabChip() {
    Surface(color = CollabBadge, shape = RoundedCornerShape(999.dp)) {
        Text("콜라보", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(banner.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (isCollabBanner(banner)) CollabChip()
                    }
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

// D-day 링 — 진행률만큼 채운 원형 스트로크 + 중앙 잔여(일/시간). (design_version_card_mockup.html ④)
@Composable
private fun DdayRing(days: Int, hours: Int, frac: Float, color: Color) {
    Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(70.dp)) {
            val sw = 8.dp.toPx()
            val topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2)
            val arc = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            drawArc(Track, 0f, 360f, false, topLeft, arc, style = Stroke(sw))
            drawArc(color, -90f, frac.coerceIn(0f, 1f) * 360f, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (days > 0) "${days}일" else "${hours}시간", fontSize = 15.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1)
            if (days > 0) Text("${hours}시간", fontSize = 8.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

// 피처드 버전 카드 — 섹션 최상단, 가장 임박한 버전. D-day 링 + 대표 캐릭터(+동반무기) + 경과. (④ 섹션 요약)
@Composable
private fun FeaturedVersionCard(vg: VersionGroup) {
    val now = System.currentTimeMillis()
    val d = ((vg.nearestEnd - now) / 86_400_000L).toInt()
    val urgent = d in 0..3
    val c = vg.game.color.toColor()
    val ddColor = if (urgent) Urgent else c
    val totalH = ((vg.nearestEnd - now) / 3_600_000L).coerceAtLeast(0L)
    val days = (totalH / 24).toInt(); val hours = (totalH % 24).toInt()
    val lead = vg.pickups.minByOrNull { it.endMillis }
    val frac = if (lead != null && lead.startMillis > 0 && lead.endMillis > lead.startMillis)
        ((now - lead.startMillis).toFloat() / (lead.endMillis - lead.startMillis)).coerceIn(0f, 1f) else 0f
    val chars = vg.pickups.filter { it.type != "weapon" }
    val items = chars.ifEmpty { vg.pickups }
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(color = c, shape = RoundedCornerShape(8.dp)) {
                    Text(vg.game.abbr, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                }
                Text(if (vg.version.isNotBlank()) "버전 ${vg.version}" else vg.game.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (urgent) Surface(color = Urgent, shape = RoundedCornerShape(999.dp)) {
                    Text("막바지", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DdayRing(days, hours, frac, ddColor)
                Column(Modifier.weight(1f)) {
                    items.take(2).forEachIndexed { i, it ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        val isW = it.type == "weapon"
                        val comp = if (isW) null else companionWeapons(it, vg.pickups).firstOrNull()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(if (isW) RoundedCornerShape(10.dp) else CircleShape).background(c),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isW) Icon(SwordIcon, null, tint = Color.White, modifier = Modifier.size(17.dp))
                                else Text(it.name.take(1), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(it.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (isCollabBanner(it)) CollabChip()
                                }
                                if (comp != null) Text("＋ ${comp.name}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WeapBadge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (items.size > 2) {
                        Spacer(Modifier.height(6.dp))
                        Text("외 ${items.size - 2}", fontSize = 10.sp, color = TextSecondary)
                    }
                    if (vg.start > 0) {
                        val vFrac = if (vg.end > vg.start) ((now - vg.start).toFloat() / (vg.end - vg.start)).coerceIn(0f, 1f) else 0f
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${DateUtil.shortDate(vg.start)} ~ ${DateUtil.shortDate(vg.end)} · ${(vFrac * 100).roundToInt()}% 경과",
                            fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// 슬림 버전 행 — 피처드 아래 나머지 버전(요약 1줄). abbr + "vN · 이름들" + 잔여. (④ 세컨더리)
@Composable
private fun SlimVersionRow(vg: VersionGroup) {
    val d = ((vg.nearestEnd - System.currentTimeMillis()) / 86_400_000L).toInt()
    val c = vg.game.color.toColor()
    val ddColor = if (d in 0..3) Urgent else c
    val chars = vg.pickups.filter { it.type != "weapon" }
    val weaps = unpairedWeapons(vg.pickups)
    val names = chars.joinToString(" · ") { it.name }.ifEmpty { weaps.joinToString(" · ") { it.name } }
    val title = if (vg.version.isNotBlank()) "v${vg.version} · $names" else names
    val counts = buildList {
        if (chars.isNotEmpty()) add("캐릭터 ${chars.size}")
        if (weaps.isNotEmpty()) add("무기 ${weaps.size}")
    }.joinToString(" · ")
    GlassCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(color = c, shape = RoundedCornerShape(8.dp)) {
                Text(vg.game.abbr, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
            }
            if (vg.pickups.any { isCollabBanner(it) }) CollabChip()
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${vg.game.displayName} · $counts", fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(dhLabel(vg.nearestEnd), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ddColor, maxLines = 1)
                if (vg.start > 0) Text("${DateUtil.shortDate(vg.start)}~${DateUtil.shortDate(vg.end)}", fontSize = 9.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

// 컴팩트 버전 섹션(전체 페이지) — 버전 소제목(좌측 틱) + 촘촘한 픽업 행. (③ 전체 페이지)
@Composable
private fun CompactVersionSection(vg: VersionGroup) {
    val d = ((vg.nearestEnd - System.currentTimeMillis()) / 86_400_000L).toInt()
    val c = vg.game.color.toColor()
    val ddColor = if (d in 0..3) Urgent else c
    val chars = vg.pickups.filter { it.type != "weapon" }
    val weaps = unpairedWeapons(vg.pickups)
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(3.dp, 14.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Text(
                    if (vg.version.isNotBlank()) "${vg.game.abbr} · v${vg.version}" else "${vg.game.abbr} · ${vg.game.displayName}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(dhLabel(vg.nearestEnd), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ddColor, maxLines = 1)
                    if (vg.start > 0) Text("${DateUtil.shortDate(vg.start)}~${DateUtil.shortDate(vg.end)}", fontSize = 9.sp, color = TextSecondary, maxLines = 1)
                }
            }
            chars.forEach { CompactPickupRow(it, companionWeapons(it, vg.pickups)) }
            weaps.forEach { CompactPickupRow(it, emptyList()) }
        }
    }
}

// 컴팩트 픽업 행 — 상단 구분선 + 아바타 30 + 이름(+동반무기) + 잔여. 카드 여백 없이 밀도↑. (③ 전체 페이지)
@Composable
private fun CompactPickupRow(banner: GachaBanner, companions: List<GachaBanner>, showCollab: Boolean = true) {
    val c = banner.gameColor.toColor()
    val isWeapon = banner.type == "weapon"
    val ddColor = if (banner.dDay() <= 3) Urgent else c
    val short = GameData.byNameOrNull(banner.game)?.shortName ?: banner.game
    Column {
        HorizontalDivider(color = DividerColor)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(30.dp).clip(if (isWeapon) RoundedCornerShape(9.dp) else CircleShape)
                    .background(if (isWeapon) WeapBadge.copy(alpha = 0.16f) else c),
                contentAlignment = Alignment.Center,
            ) {
                if (isWeapon) Icon(SwordIcon, null, tint = WeapBadge, modifier = Modifier.size(14.dp))
                else Text(banner.name.take(1), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(banner.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (showCollab && isCollabBanner(banner)) { Spacer(Modifier.width(5.dp)); CollabChip() }
                    companions.firstOrNull()?.let {
                        Spacer(Modifier.width(4.dp))
                        Text("＋${it.name}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WeapBadge, maxLines = 1)
                    }
                }
                Text("$short · ${if (isWeapon) "무기" else "캐릭터"}", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(dhLabel(banner.endMillis), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ddColor, maxLines = 1)
                Text("~" + DateUtil.shortDate(banner.endMillis), fontSize = 9.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

// 콜라보 강조 카드 — 게임 일정 최상단. 활성 콜라보(스타레일×Fate 등)를 일반 버전과 분리해 부각(보라 accent).
@Composable
private fun CollabScheduleCard(groups: List<VersionGroup>) {
    val title = groups.firstNotNullOfOrNull { g -> g.pickups.firstNotNullOfOrNull { collabTitle(it) } } ?: "콜라보 픽업"
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollabChip()
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            groups.forEach { vg ->
                Spacer(Modifier.height(12.dp))
                val chars = vg.pickups.filter { it.type != "weapon" }
                val weaps = unpairedWeapons(vg.pickups)
                val d = ((vg.nearestEnd - System.currentTimeMillis()) / 86_400_000L).toInt()
                val ddColor = if (d in 0..3) Urgent else CollabBadge
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(3.dp, 14.dp).clip(RoundedCornerShape(2.dp)).background(CollabBadge))
                    Text(
                        if (vg.version.isNotBlank()) "${vg.game.abbr} · v${vg.version}" else "${vg.game.abbr} · ${vg.game.displayName}",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(dhLabel(vg.nearestEnd), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ddColor, maxLines = 1)
                        if (vg.start > 0) Text("${DateUtil.shortDate(vg.start)}~${DateUtil.shortDate(vg.end)}", fontSize = 9.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                chars.forEach { CompactPickupRow(it, companionWeapons(it, vg.pickups), showCollab = false) }
                weaps.forEach { CompactPickupRow(it, emptyList(), showCollab = false) }
            }
        }
    }
}

// 통합 게임 일정 섹션 — 픽업을 버전 카드로 요약(임박 3개) + 이벤트·콘텐츠 미리보기, 초과 시 전체 페이지로.
@Composable
fun GameScheduleSection(
    entries: List<ScheduleEntry>,
    banners: List<GachaBanner>,
    filter: String,
    onSeeAll: () -> Unit,
    onSeePickups: () -> Unit,
) {
    val accent = LocalAccent.current
    val allGroups = buildVersionGroups(banners, filter)
    val collabGroups = allGroups.filter { g -> g.pickups.any { isCollabBanner(it) } }
    val groups = allGroups.filterNot { g -> g.pickups.any { isCollabBanner(it) } }
    val extras = filteredEntries(entries, filter).filter { it.kind != "패치" }.sortedBy { it.target }
    val topGroups = groups.take(3)
    val topExtras = extras.take(2)
    Text("게임 일정", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    Text("픽업 배너를 버전별로 모았어요. 이벤트·정기 콘텐츠도 함께.", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
    if (allGroups.isEmpty() && extras.isEmpty()) {
        GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Text("예정된 일정이 없어요.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(16.dp))
        }
    } else {
        if (collabGroups.isNotEmpty()) {
            CollabScheduleCard(collabGroups)
            if (topGroups.isNotEmpty() || topExtras.isNotEmpty()) Spacer(Modifier.height(12.dp))
        }
        if (topGroups.isNotEmpty()) {
            FeaturedVersionCard(topGroups.first())
            topGroups.drop(1).forEach { Spacer(Modifier.height(9.dp)); SlimVersionRow(it) }
        }
        if (topExtras.isNotEmpty()) {
            if (topGroups.isNotEmpty()) Spacer(Modifier.height(12.dp))
            GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("이벤트 · 정기 콘텐츠", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 2.dp))
                    topExtras.forEachIndexed { i, e -> ScheduleEntryRow(e); if (i < topExtras.lastIndex) HorizontalDivider(color = DividerColor) }
                }
            }
        }
        val hiddenMore = (groups.size - topGroups.size) + (extras.size - topExtras.size)
        if (hiddenMore > 0) {
            Spacer(Modifier.height(12.dp))
            GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { onSeeAll() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("전체 일정 보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("+$hiddenMore", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent.copy(alpha = 0.6f))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = accent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// 버전 단위 묶음 — (게임, 버전) 하나당 카드 한 장. 픽업 종료 임박순으로 카드 정렬.
data class VersionGroup(
    val game: Game,
    val version: String,
    val pickups: List<GachaBanner>,
    val nearestEnd: Long,
    val start: Long,   // 버전 시작일 = 픽업 시작 중 가장 이른 값(0 제외)
    val end: Long,     // 버전 종료일 = 픽업 종료 중 가장 늦은 값
)

// 필터 적용 픽업을 (게임, 버전)으로 묶어 임박순 정렬. 버전이 비면 게임명만.
fun buildVersionGroups(banners: List<GachaBanner>, filter: String): List<VersionGroup> =
    filteredPickups(banners, filter)
        .groupBy { it.game to it.version }
        .mapNotNull { (key, list) ->
            val game = GameData.byNameOrNull(key.first) ?: return@mapNotNull null
            VersionGroup(
                game, key.second, list.sortedBy { it.endMillis }, list.minOf { it.endMillis },
                start = list.filter { it.startMillis > 0 }.minOfOrNull { it.startMillis } ?: 0L,
                end = list.maxOf { it.endMillis },
            )
        }
        .sortedBy { it.nearestEnd }

// 전체 게임 일정 페이지 콘텐츠 (SectionPage 안에서 호스팅 — 헤더/스크롤은 SectionPage 제공). 버전별 카드 + 이벤트·콘텐츠 카드.
@Composable
fun GameScheduleFullContent(
    banners: List<GachaBanner>,
    events: List<GameEvent>,
    challenges: List<GameChallenge>,
    filter: String,
) {
    val allGroups = buildVersionGroups(banners, filter)
    val collabGroups = allGroups.filter { g -> g.pickups.any { isCollabBanner(it) } }
    val groups = allGroups.filterNot { g -> g.pickups.any { isCollabBanner(it) } }
    // 버전 없는 일정(이벤트·정기 콘텐츠)은 하단 별도 카드로. 패치 종료는 버전 카드가 대신하므로 제외.
    val extras = filteredEntries(buildSchedule(banners, events, challenges), filter)
        .filter { it.kind != "패치" }.sortedBy { it.target }
    Text("게임 일정", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    Text("픽업 배너를 버전별로 모으고, 이벤트·정기 콘텐츠를 아래에 정리했어요.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
    if (allGroups.isEmpty() && extras.isEmpty()) {
        Text("예정된 일정이 없어요.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
    } else {
        if (collabGroups.isNotEmpty()) {
            CollabScheduleCard(collabGroups)
            if (groups.isNotEmpty()) Spacer(Modifier.height(12.dp))
        }
        groups.forEachIndexed { i, vg -> if (i > 0) Spacer(Modifier.height(12.dp)); CompactVersionSection(vg) }
        if (extras.isNotEmpty()) {
            Row(
                Modifier.padding(top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("이벤트 · 정기 콘텐츠", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("${extras.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
            GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    extras.forEachIndexed { i, e -> ScheduleEntryRow(e); if (i < extras.lastIndex) HorizontalDivider(color = DividerColor) }
                }
            }
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
