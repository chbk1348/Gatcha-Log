package com.gatcha.log.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.CarryoverKind
import com.gatcha.log.data.GachaBannerRate
import com.gatcha.log.data.GachaGameRate
import com.gatcha.log.data.GachaRateData
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

private fun pct(v: Double, digits: Int): String = "%.${digits}f%%".format(v * 100)

/** 가챠 확률표 — 페이지 형식(서브 페이지). 천장&확률 글래스 카드 + 빠른 비교 테이블. */
@Composable
fun GachaRatePage(onBack: () -> Unit) {
    BackHandler { onBack() }
    var bannerType by remember { mutableStateOf("character") }
    var sortCol by remember { mutableStateOf<String?>(null) }
    var sortAsc by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GlgScreenHeader("가챠 확률표", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            BannerTypeTabs(bannerType) { bannerType = it }
            GachaRateData.games.forEach { game -> GameRateCard(game, bannerType) }
            Spacer(Modifier.height(2.dp))
            SectionLabel("빠른 비교")
            CompareTable(
                bannerType = bannerType,
                sortCol = sortCol,
                sortAsc = sortAsc,
                onSort = { col -> if (sortCol == col) sortAsc = !sortAsc else { sortCol = col; sortAsc = true } },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(start = 2.dp))
}

@Composable
private fun BannerTypeTabs(selected: String, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    // 공통 칩 단일 규격(시스템 세그먼트 폐기) — 배너타입 선택.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GachaRateData.bannerTypes.forEach { (key, label) ->
            GlgChip(label = label, selected = key == selected, color = accent) { onSelect(key) }
        }
    }
}

@Composable
private fun GameRateCard(game: GachaGameRate, bannerType: String) {
    val accent = LocalAccent.current
    val banner = game.banner(bannerType)
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(game.color.toColor()))
                Spacer(Modifier.width(8.dp))
                Text(game.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Surface(color = game.color.toColor(), shape = CircleShape) {
                    Text(
                        game.grade,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (banner != null) CarryoverBadge(banner)
            }
            if (banner != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("기본 확률", pct(banner.base, 2), "${game.grade} 기준", game.color.toColor(), Modifier.weight(1f))
                    StatBox("소프트 천장", "${banner.softPity}회", "이후 확률 상승", game.color.toColor(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("하드 천장", "${banner.hardPity}회", "100% ${game.grade} 보장", game.color.toColor(), Modifier.weight(1f))
                    StatBox("뽑기 단위", "${banner.currency} ${banner.perPull}", "= 1회 소환", game.color.toColor(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                val g = GachaRateData.guaranteeInfo(game.grade, banner)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.06f))
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                ) {
                    Text(g.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    if (g.detail.isNotBlank()) {
                        Text(g.detail, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text("이 배너 타입이 없습니다.", fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "기준: 버전 ${game.version}",
                fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun CarryoverBadge(banner: GachaBannerRate) {
    val accent = LocalAccent.current
    val badge = GachaRateData.carryoverBadge(banner) ?: return
    val (label, kind) = badge
    val (bg, fg) = when (kind) {
        CarryoverKind.YES -> Color(0x2622C55E) to Color(0xFF16A34A)
        CarryoverKind.NO -> Color(0x1ADC2626) to Color(0xFFDC2626)
        CarryoverKind.EPITOMIZED -> accent.copy(alpha = 0.12f) to accent
        CarryoverKind.NONE -> Color(0x0F000000) to TextSecondary
    }
    Surface(color = bg, shape = CircleShape) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg,
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, sub: String, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.06f))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 3.dp))
        Text(sub, fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.8f), modifier = Modifier.padding(top = 1.dp))
    }
}

// ============================================================ 빠른 비교 테이블
private data class CompareRow(
    val shortName: String, val color: Color, val grade: String,
    val base: Double?, val soft: Int?, val hard: Int?, val guarantee: String,
)

@Composable
private fun CompareTable(bannerType: String, sortCol: String?, sortAsc: Boolean, onSort: (String) -> Unit) {
    var rows = GachaRateData.games.map { g ->
        val b = g.banner(bannerType)
        CompareRow(g.shortName, g.color.toColor(), g.grade, b?.base, b?.softPity, b?.hardPity, b?.guaranteeShort ?: "—")
    }
    if (sortCol != null) {
        rows = when (sortCol) {
            "name" -> rows.sortedBy { it.shortName }
            "grade" -> rows.sortedBy { it.grade }
            "base" -> rows.sortedWith(compareBy(nullsLast()) { it.base })
            "soft" -> rows.sortedWith(compareBy(nullsLast()) { it.soft })
            "hard" -> rows.sortedWith(compareBy(nullsLast()) { it.hard })
            "guarantee" -> rows.sortedBy { it.guarantee }
            else -> rows
        }
        if (!sortAsc) rows = rows.reversed()
    }

    GlassCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderCell("게임", "name", sortCol, sortAsc, 1.5f, onSort)
                HeaderCell("등급", "grade", sortCol, sortAsc, 0.9f, onSort)
                HeaderCell("기본", "base", sortCol, sortAsc, 1.1f, onSort)
                HeaderCell("소프트", "soft", sortCol, sortAsc, 0.9f, onSort)
                HeaderCell("하드", "hard", sortCol, sortAsc, 0.9f, onSort)
                HeaderCell("보장", "guarantee", sortCol, sortAsc, 1.3f, onSort)
            }
            rows.forEach { r ->
                HorizontalDivider(color = DividerColor)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(r.color))
                        Spacer(Modifier.width(5.dp))
                        Text(r.shortName, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
                    }
                    DataCell(r.grade, 0.9f)
                    DataCell(r.base?.let { pct(it, 3) } ?: "—", 1.1f)
                    DataCell(r.soft?.let { "$it" } ?: "—", 0.9f)
                    DataCell(r.hard?.let { "$it" } ?: "—", 0.9f)
                    DataCell(r.guarantee, 1.3f)
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(
    label: String, col: String, sortCol: String?, sortAsc: Boolean, weight: Float, onSort: (String) -> Unit,
) {
    val accent = LocalAccent.current
    val active = sortCol == col
    val arrow = if (active) (if (sortAsc) " ↑" else " ↓") else ""
    Text(
        label + arrow,
        modifier = Modifier.weight(weight).clickable { onSort(col) },
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (active) accent else TextSecondary,
    )
}

@Composable
private fun RowScope.DataCell(text: String, weight: Float) {
    Text(text, modifier = Modifier.weight(weight), fontSize = 11.sp, color = TextSecondary, maxLines = 1)
}
