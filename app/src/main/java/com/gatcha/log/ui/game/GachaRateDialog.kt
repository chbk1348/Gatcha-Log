package com.gatcha.log.ui.game

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
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

private val StatBg = Color(0x08000000)       // rgba(0,0,0,0.03)
private val StatLabel = Color(0x59000000)    // rgba(0,0,0,0.35)
private val CardBg = Color(0xFFF8F8FB)

private fun pct(v: Double, digits: Int): String = "%.${digits}f%%".format(v * 100)

/** 가챠 확률표 모달 — 천장&확률 정보 카드 + 빠른 비교 테이블 (웹앱 v27.19.0 이식) */
@Composable
fun GachaRateDialog(onDismiss: () -> Unit) {
    var bannerType by remember { mutableStateOf("character") }
    var sortCol by remember { mutableStateOf<String?>(null) }
    var sortAsc by remember { mutableStateOf(true) }

    GlgDialog(
        title = "가챠 확률표",
        onDismiss = onDismiss,
        confirmText = "닫기",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel("천장 & 확률 정보")
            BannerTypeTabs(bannerType) { bannerType = it }
            GachaRateData.games.forEach { game ->
                GameRateCard(game, bannerType)
            }
            Spacer(Modifier.height(2.dp))
            SectionLabel("빠른 비교")
            CompareTable(
                bannerType = bannerType,
                sortCol = sortCol,
                sortAsc = sortAsc,
                onSort = { col ->
                    if (sortCol == col) sortAsc = !sortAsc else { sortCol = col; sortAsc = true }
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
}

@Composable
private fun BannerTypeTabs(selected: String, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GachaRateData.bannerTypes.forEach { (key, label) ->
            val isSel = key == selected
            Surface(
                modifier = Modifier.clickable { onSelect(key) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSel) accent else Color(0xFFF2F2F6),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) Color.White else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun GameRateCard(game: GachaGameRate, bannerType: String) {
    val accent = LocalAccent.current
    val banner = game.banner(bannerType)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, DividerColor, RoundedCornerShape(16.dp)),
    ) {
        // 게임 색상 바
        Box(Modifier.fillMaxWidth().height(4.dp).background(game.color))
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Surface(color = game.color, shape = CircleShape) {
                    Text(
                        game.grade,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
            }
            if (banner != null) {
                Spacer(Modifier.height(8.dp))
                CarryoverBadge(banner)
                Spacer(Modifier.height(10.dp))
                // 2x2 통계 그리드
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("기본 확률", pct(banner.base, 2), "${game.grade} 기준", Modifier.weight(1f))
                    StatBox("소프트 천장", "${banner.softPity}회", "이후 확률 상승", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("하드 천장", "${banner.hardPity}회", "100% ${game.grade} 보장", Modifier.weight(1f))
                    StatBox("뽑기 단위", "${banner.currency} ${banner.perPull}", "= 1회 소환", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                val g = GachaRateData.guaranteeInfo(game.grade, banner)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.05f))
                        .border(1.dp, accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                ) {
                    Text(g.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (g.detail.isNotBlank()) {
                        Text(g.detail, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("이 배너 타입이 없습니다.", fontSize = 12.sp, color = Color.LightGray)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "기준: 버전 ${game.version}",
                fontSize = 10.sp, color = Color(0x4D000000),
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
        CarryoverKind.EPITOMIZED -> accent.copy(alpha = 0.1f) to accent
        CarryoverKind.NONE -> Color(0x0F000000) to Color(0x73000000)
    }
    Surface(color = bg, shape = CircleShape) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg,
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(StatBg)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = StatLabel)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 3.dp))
        Text(sub, fontSize = 10.sp, color = StatLabel, modifier = Modifier.padding(top = 1.dp))
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
        CompareRow(g.shortName, g.color, g.grade, b?.base, b?.softPity, b?.hardPity, b?.guaranteeShort ?: "—")
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

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DividerColor, RoundedCornerShape(12.dp)),
    ) {
        // 헤더
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF6F6FA)).padding(vertical = 8.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("게임", "name", sortCol, sortAsc, 1.5f, onSort)
            HeaderCell("등급", "grade", sortCol, sortAsc, 0.9f, onSort)
            HeaderCell("기본", "base", sortCol, sortAsc, 1.1f, onSort)
            HeaderCell("소프트", "soft", sortCol, sortAsc, 0.9f, onSort)
            HeaderCell("하드", "hard", sortCol, sortAsc, 0.9f, onSort)
            HeaderCell("보장", "guarantee", sortCol, sortAsc, 1.3f, onSort)
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = DividerColor)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 6.dp),
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

@Composable
private fun RowScope.HeaderCell(
    label: String, col: String, sortCol: String?, sortAsc: Boolean, weight: Float, onSort: (String) -> Unit,
) {
    val accent = LocalAccent.current
    val active = sortCol == col
    val arrow = if (active) (if (sortAsc) " ↑" else " ↓") else " ↕"
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
