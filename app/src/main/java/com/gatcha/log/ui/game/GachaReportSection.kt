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
import androidx.compose.material.icons.filled.FileUpload
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
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

private fun won(n: Long): String = "₩%,d".format(n)
private fun num(n: Int): String = "%,d".format(n)

@Composable
fun GachaReportSection(
    stats: GachaStats?,
    spendByGameKey: Map<String, Long>,
    onImport: (List<Uri>) -> Unit,
    onClear: () -> Unit,
) {
    val accent = LocalAccent.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }
    val openPicker = { picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }

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

    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (stats == null) {
                EmptyState(onImport = openPicker)
            } else {
                ReportContent(stats, spendByGameKey, onImport = openPicker)
            }
        }
    }
}

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
private fun ReportContent(stats: GachaStats, spendByGameKey: Map<String, Long>, onImport: () -> Unit) {
    val accent = LocalAccent.current
    var totalPulls = 0; var totalFive = 0; var totalFour = 0
    var totalSpend = 0L; var totalFiveForCost = 0
    stats.byGame.forEach { (gk, g) ->
        totalPulls += g.total; totalFive += g.five; totalFour += g.four
        val s = spendByGameKey[gk] ?: 0L
        if (s > 0 && g.five > 0) { totalSpend += s; totalFiveForCost += g.five }
    }
    val overallCost = if (totalFiveForCost > 0) (totalSpend / totalFiveForCost) else 0L

    // 요약
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryStat(num(totalPulls), "총 뽑기", null, Modifier.weight(1f))
        SummaryStat(num(totalFive), "획득 5성", "4성 ${num(totalFour)}", Modifier.weight(1f))
        SummaryStat(if (overallCost > 0) won(overallCost) else "—", "5성 평균 단가", "월정액 제외", Modifier.weight(1f), valueColor = accent)
    }
    Spacer(Modifier.height(14.dp))

    val games = stats.byGame.keys.sortedBy { GachaReport.gameOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }
    games.forEachIndexed { idx, gk ->
        val g = stats.byGame[gk] ?: return@forEachIndexed
        val (shortName, _, color) = GachaReport.gameInfo[gk] ?: Triple(gk, gk, Color(0xFF888888))
        val labels = GachaReport.poolLabels[gk] ?: emptyMap()
        val pOrder = GachaReport.poolOrder[gk] ?: g.pools.keys.toList()
        if (idx > 0) { Spacer(Modifier.height(12.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(12.dp)) }

        // 게임 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(shortName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("${num(g.total)}뽑 · 5성 ${num(g.five)}", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))

        // 단가/출현율/평균천장
        val s = spendByGameKey[gk] ?: 0L
        val cost = if (s > 0 && g.five > 0) s / g.five else 0L
        val fiveRate = if (g.total > 0) g.five * 100.0 / g.total else 0.0
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (cost > 0) MetaItem("5성 단가", won(cost))
            MetaItem("5성 출현율", "%.2f%%".format(fiveRate))
            if (g.avgPity > 0) MetaItem("평균 천장", "${g.avgPity}")
        }
        Spacer(Modifier.height(10.dp))

        // 풀별
        val pools = g.pools.keys.sortedBy { pOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }
        pools.forEach { pk ->
            val p = g.pools[pk] ?: return@forEach
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(labels[pk] ?: pk, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(
                    "${p.total}뽑 · 5성 ${p.five}" + if (p.avgPity > 0) " · 평균 ${p.avgPity}" else "",
                    fontSize = 11.sp, color = TextSecondary,
                )
                Spacer(Modifier.width(8.dp))
                Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text("천장 ${p.pity}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }

        // 최근 5성
        if (g.recentFive.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("최근 5성", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowChips(g.recentFive.map { "${it.name} (${labels[it.pool] ?: ""} ${it.pity})" })
        }
    }

    Spacer(Modifier.height(14.dp))
    GlgButton("기록 추가 가져오기", onClick = onImport, modifier = Modifier.fillMaxWidth(), height = 46.dp)
}

@Composable
private fun SummaryStat(value: String, label: String, sub: String?, modifier: Modifier = Modifier, valueColor: Color = TextPrimary) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x08000000)).padding(vertical = 11.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        Text(label, fontSize = 10.sp, color = TextSecondary)
        if (sub != null) Text(sub, fontSize = 9.sp, color = Color.LightGray)
    }
}

@Composable
private fun MetaItem(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

/** 간단한 줄바꿈 칩 묶음 (최근 5성). */
@Composable
private fun FlowChips(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t ->
                    Surface(color = Color(0x08000000), shape = RoundedCornerShape(8.dp)) {
                        Text(t, fontSize = 11.sp, color = TextPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1)
                    }
                }
            }
        }
    }
}
