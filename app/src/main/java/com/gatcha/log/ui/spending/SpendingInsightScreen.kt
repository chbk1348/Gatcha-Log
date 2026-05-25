package com.gatcha.log.ui.spending

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import java.util.Calendar

private fun won(v: Long): String = "₩%,d".format(v)
private val EtcColor = Color(0xFFB8BDC6)

/** 지출 인사이트 — 예산 페이스 예측 + 게임별 월 추이 + 카테고리(결제수단·태그) 비중. */
@Composable
fun SpendingInsightScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val spendings by viewModel.spendings.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val year = viewModel.displayYear
    val month = viewModel.displayMonth
    val monthTotal = remember(spendings) { viewModel.monthlyTotal() }

    Column(Modifier.fillMaxSize()) {
        GlgScreenHeader("지출 인사이트", onBack, Modifier.padding(horizontal = 16.dp))
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (spendings.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "지출 기록이 쌓이면\n예산 페이스·게임별 추이·카테고리 비중을 분석해 드려요.",
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                return@Column
            }

            BudgetPaceCard(monthTotal, budget, month, accent)
            MonthlyTrendCard(spendings, year, accent)
            PaymentBreakdownCard(spendings, accent)
            TagBreakdownCard(spendings, accent)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- 1) 예산 페이스 예측
@Composable
private fun BudgetPaceCard(monthTotal: Long, budget: Long, month: Int, accent: Color) {
    val cal = remember { Calendar.getInstance() }
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val projected = if (dayOfMonth > 0) monthTotal * daysInMonth / dayOfMonth else monthTotal
    val dailyAvg = if (dayOfMonth > 0) monthTotal / dayOfMonth else 0L
    val remainingDays = (daysInMonth - dayOfMonth).coerceAtLeast(0)

    DashCard {
        CardTitle("${month}월 예산 페이스", "${dayOfMonth}일 경과 · ${remainingDays}일 남음")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("월말 예상", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text(won(projected), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(won(monthTotal), "현재 지출", Modifier.weight(1f))
            StatTile(won(dailyAvg), "하루 평균", Modifier.weight(1f))
            StatTile(if (budget > 0) won(budget) else "—", "이번 달 예산", Modifier.weight(1f))
        }
        if (budget > 0) {
            Spacer(Modifier.height(14.dp))
            val over = projected > budget
            val frac = (projected.toFloat() / budget).coerceIn(0f, 1f)
            val barColor = if (over) DangerText else accent
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(ProgressEmpty)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(barColor))
            }
            Spacer(Modifier.height(8.dp))
            val diff = kotlin.math.abs(projected - budget)
            Text(
                if (over) "이 페이스면 예산을 ${won(diff)} 초과할 것 같아요"
                else "이 페이스면 예산 안에서 ${won(diff)} 여유가 생겨요",
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = if (over) DangerText else accent,
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Text("예산을 설정하면 초과 여부를 예측해 드려요", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

// ---------------------------------------------------------------- 2) 게임별 월 추이 (올해, 누적 막대)
@Composable
private fun MonthlyTrendCard(spendings: List<Spending>, year: Int, accent: Color) {
    val yearItems = remember(spendings, year) { spendings.filter { DateUtil.isSameYear(it.dateMillis, year) } }
    if (yearItems.isEmpty()) return
    val topGames = remember(yearItems) {
        yearItems.groupBy { it.gameName }.mapValues { e -> e.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(5).map { it.key }
    }
    val hasEtc = remember(yearItems, topGames) { yearItems.any { it.gameName !in topGames } }
    // month(0..11) -> 게임키 -> 금액
    val monthGame = remember(yearItems, topGames) {
        Array(12) { LinkedHashMap<String, Long>() }.also { arr ->
            yearItems.forEach { s ->
                val m = DateUtil.month(s.dateMillis) - 1
                val key = if (s.gameName in topGames) s.gameName else "기타"
                arr[m][key] = (arr[m][key] ?: 0L) + s.amount
            }
        }
    }
    val monthTotals = LongArray(12) { monthGame[it].values.sum() }
    val maxMonth = (monthTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val legend = topGames + if (hasEtc) listOf("기타") else emptyList()
    fun colorOf(g: String) = if (g == "기타") EtcColor else GameData.colorFor(g)

    DashCard {
        CardTitle("게임별 월 추이", "${year}년 · 누적 막대")
        Spacer(Modifier.height(14.dp))
        val barAreaH = 110f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            for (m in 0 until 12) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(barAreaH.dp), contentAlignment = Alignment.BottomCenter) {
                        Column(Modifier.fillMaxWidth(0.7f).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))) {
                            legend.forEach { g ->
                                val amt = monthGame[m][g] ?: 0L
                                if (amt > 0L) {
                                    val h: Dp = (barAreaH * (amt.toFloat() / maxMonth)).dp
                                    Box(Modifier.fillMaxWidth().height(h).background(colorOf(g)))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text("${m + 1}", fontSize = 8.sp, color = TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // 범례
        FlowLegend(legend.map { it to colorOf(it) })
    }
}

// ---------------------------------------------------------------- 3) 결제수단별 비중
@Composable
private fun PaymentBreakdownCard(spendings: List<Spending>, accent: Color) {
    val rows = remember(spendings) {
        val total = spendings.sumOf { it.amount }
        spendings.groupBy { it.paymentMethod.ifBlank { "기타" } }
            .map { Triple(it.key, it.value.sumOf { s -> s.amount }, total) }
            .sortedByDescending { it.second }
    }
    if (rows.isEmpty()) return
    DashCard {
        CardTitle("결제수단별 비중")
        Spacer(Modifier.height(12.dp))
        rows.forEach { (name, amt, total) ->
            BreakdownRow(name, amt, if (total > 0) amt.toFloat() / total else 0f, accent)
        }
    }
}

// ---------------------------------------------------------------- 4) 태그별 지출
@Composable
private fun TagBreakdownCard(spendings: List<Spending>, accent: Color) {
    val rows = remember(spendings) {
        val m = LinkedHashMap<String, Long>()
        spendings.forEach { s -> s.tags.forEach { t -> m[t] = (m[t] ?: 0L) + s.amount } }
        m.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
    }
    if (rows.isEmpty()) return
    val maxTag = (rows.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
    DashCard {
        CardTitle("태그별 지출", "여러 태그가 달린 지출은 중복 집계돼요")
        Spacer(Modifier.height(12.dp))
        rows.forEach { (tag, amt) ->
            BreakdownRow("#$tag", amt, amt.toFloat() / maxTag, accent)
        }
    }
}

// ---------------------------------------------------------------- 공통 UI
@Composable
private fun DashCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun CardTitle(title: String, sub: String? = null) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    if (sub != null) {
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x08000000)).padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
    }
}

@Composable
private fun BreakdownRow(name: String, amount: Long, frac: Float, accent: Color) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
            Text(won(amount), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${(frac * 100).toInt()}%", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(ProgressEmpty)) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(accent))
        }
    }
}

/** 색 점 + 라벨 칩들을 줄바꿈으로 배치한 범례. */
@Composable
private fun FlowLegend(items: List<Pair<String, Color>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(5.dp))
                        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}
