package com.gatcha.log.ui.game

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileUpload
import com.gatcha.log.data.GachaGameStat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GachaReport
import com.gatcha.log.data.GachaStats
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.num
import com.gatcha.log.util.won

@Composable
fun GachaReportSection(
    stats: GachaStats?,
    spendByGameKey: Map<String, Long>,
    onImport: (List<Uri>) -> Unit,
    onClear: () -> Unit,
    onOpenDashboard: () -> Unit = {},
) {
    val accent = LocalAccent.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }
    // MIME 필터를 좁히면 일부 제공자(삼성 SAF·클라우드)가 JSON 을 회색 처리해 선택 불가 → */* 로 전부 허용
    val openPicker = { picker.launch(arrayOf("*/*")) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가챠 효율 리포트", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                Text("Beta", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
        if (stats != null) {
            Text("초기화", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onClear() }.padding(4.dp))
        }
    }

    if (stats == null) {
        GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { EmptyState(onImport = openPicker) }
        }
    } else {
        ReportContent(stats, spendByGameKey, onImport = openPicker, onOpenDashboard = onOpenDashboard)
    }
}

// 운 분포색
private val Lucky = Color(0xFF2BB673)
private val Avg = Color(0xFFE0A93B)
private val Unlucky = Color(0xFFE8634A)
private fun reportAbbr(gk: String) = when (gk) { "genshin" -> "GI"; "hsr", "starrail" -> "HSR"; "zzz" -> "ZZZ"; else -> gk.uppercase() }
private fun wonShort(v: Long): String = if (v >= 10000) "%.1f만".format(v / 10000.0) else won(v)

@Composable
private fun EmptyState(onImport: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(LocalAccent.current.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FileUpload, null, tint = LocalAccent.current, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("아직 가챠 기록이 없어요", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "UIGF(원신·젠레스) / SRGF·UIGF(스타레일) 표준 JSON을 가져오면\n5성 단가 · 평균 천장 · 획득 히스토리를 분석해 드려요.",
            fontSize = 12.sp, color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
        GlgButton("가챠 기록 JSON 가져오기", onClick = onImport, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            "UIGF/SRGF가 뭔가요?",
            fontSize = 12.sp, color = LocalAccent.current, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { uriHandler.openUri("https://uigf.org/") },
        )
    }
}

@Composable
private fun ReportContent(stats: GachaStats, spendByGameKey: Map<String, Long>, onImport: () -> Unit, onOpenDashboard: () -> Unit) {
    val games = stats.byGame.keys.sortedBy { GachaReport.gameOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        games.forEachIndexed { idx, gk ->
            val g = stats.byGame[gk] ?: return@forEachIndexed
            GameCard(gk, g, spendByGameKey[gk] ?: 0L, showDash = idx == 0, onOpenDashboard)
        }
        GlgButton("기록 추가 가져오기", onClick = onImport, modifier = Modifier.fillMaxWidth(), height = 46.dp)
    }
}

// design_gachareport_mockup.html(B) — 게임 카드: 배지+4통계+운분포 바+최근5성, 첫 카드에 대시보드 진입.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameCard(gk: String, g: GachaGameStat, spend: Long, showDash: Boolean, onOpenDashboard: () -> Unit) {
    val accent = LocalAccent.current
    val (shortName, _, color) = GachaReport.gameInfo[gk] ?: Triple(gk, gk, 0xFF888888L)
    val cost = if (spend > 0 && g.five > 0) spend / g.five else 0L
    val dist = g.luckDist
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // 헤더 — 배지 + 게임명
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = color.toColor(), shape = RoundedCornerShape(7.dp)) {
                    Text(reportAbbr(gk), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(shortName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            // 4 통계
            Row(Modifier.fillMaxWidth()) {
                StatCol(num(g.total), "총 뽑기", Modifier.weight(1f))
                StatCol(num(g.five), "5성", Modifier.weight(1f))
                StatCol(if (g.avgPity > 0) "${g.avgPity}" else "—", "평균 천장", Modifier.weight(1f), accent)
                StatCol(if (cost > 0) wonShort(cost) else "—", "5성 단가", Modifier.weight(1f), accent)
            }
            Spacer(Modifier.height(12.dp))
            // 운 분포 바
            if (g.five > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("운 분포 (천장 구간)", fontSize = 11.sp, color = TextSecondary)
                    Text("5성 ${num(g.five)}개", fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                    if (dist[0] > 0) Box(Modifier.weight(dist[0].toFloat()).fillMaxHeight().background(Lucky))
                    if (dist[1] > 0) Box(Modifier.weight(dist[1].toFloat()).fillMaxHeight().background(Avg))
                    if (dist[2] > 0) Box(Modifier.weight(dist[2].toFloat()).fillMaxHeight().background(Unlucky))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(Lucky, "~40 행운"); Legend(Avg, "41~74 평균"); Legend(Unlucky, "75+ 불운")
                }
            }
            // 최근 5성
            if (g.recentFive.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("최근 5성", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.recentFive.forEach { f -> ReportChip(f.name, f.pity) }
                }
            }
            // 대시보드 진입 (첫 카드)
            if (showDash) {
                Spacer(Modifier.height(13.dp)); HorizontalDivider(color = DividerColor)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenDashboard() }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("상세 대시보드 (월별·풀별 추이)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = accent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCol(value: String, label: String, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
private fun Legend(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
private fun ReportChip(name: String, pity: Int) {
    val c = if (pity <= 40) Lucky else if (pity >= 75) Unlucky else TextPrimary
    Surface(color = Color(0xFFF3F4F8), shape = CircleShape) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(5.dp))
            Text("$pity", fontSize = 10.sp, fontWeight = FontWeight.Black, color = c)
        }
    }
}
