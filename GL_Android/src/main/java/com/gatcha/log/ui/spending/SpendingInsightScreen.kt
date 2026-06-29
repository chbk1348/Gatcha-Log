package com.gatcha.log.ui.spending

import com.gatcha.log.data.SpendingViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.data.Subscription
import com.gatcha.log.data.SpendingInsightStats
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.StatTile
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won
import java.util.Calendar

private val EtcColor = Color(0xFFB8BDC6)

/** 지출 인사이트 — 예산 페이스 예측 + 게임별 월 추이 + 카테고리(결제수단·태그) 비중. */
@Composable
fun SpendingInsightScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val spendings by viewModel.spendings.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
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

            var tab by remember { mutableStateOf(0) }
            InsightTabToggle(tab, { tab = it }, accent)
            if (tab == 0) {
                BudgetPaceCard(monthTotal, budget, month, accent)
                MoMCard(spendings, year, month, accent)
                PaymentStatsCard(spendings, year, month)
                MonthlyTrendCard(spendings, year, accent)
                PaymentBreakdownCard(spendings, accent)
                PlatformBreakdownCard(spendings, accent)
                TagBreakdownCard(spendings, accent)
                SubscriptionSummaryCard(subscriptions, accent)
            } else {
                AnnualReportContent(viewModel)
            }
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
    val pace = computeBudgetPace(monthTotal, dayOfMonth, daysInMonth)
    val projected = pace.projected

    DashCard {
        CardTitle("${month}월 예산 페이스", "${dayOfMonth}일 경과 · ${pace.remainingDays}일 남음")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("월말 예상", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text(won(projected), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(won(monthTotal), "현재 지출", Modifier.weight(1f), valueFontSize = 14.sp)
            StatTile(won(pace.dailyAvg), "하루 평균", Modifier.weight(1f), valueFontSize = 14.sp)
            StatTile(if (budget > 0) won(budget) else "—", "이번 달 예산", Modifier.weight(1f), valueFontSize = 14.sp)
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

// ---------------------------------------------------------------- 신규) 전월 대비 (MoM)
@Composable
private fun MoMCard(spendings: List<Spending>, year: Int, month: Int, accent: Color) {
    val mom = remember(spendings, year, month) { SpendingInsightStats.momComparison(spendings, year, month) }
    val warn = Color(0xFFF59E0B)
    val up = mom.delta > 0
    DashCard {
        CardTitle("전월 대비", "이번 달 vs 지난 달 지출")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(won(mom.thisMonth), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(10.dp))
            if (mom.percent >= 0) {
                val clr = if (up) warn else accent
                Surface(color = clr.copy(alpha = 0.12f), shape = RoundedCornerShape(9.dp)) {
                    Text(
                        "${if (up) "▲" else "▼"} ${kotlin.math.abs(mom.percent)}% · ${if (up) "+" else "-"}${won(kotlin.math.abs(mom.delta))}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = clr,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            } else {
                Text("지난달 기록 없음", fontSize = 12.sp, color = TextSecondary)
            }
        }
        if (mom.topGame.isNotBlank() && mom.topGameDelta != 0L) {
            Spacer(Modifier.height(12.dp))
            Text(
                "증감 가장 큰 게임 · ${mom.topGame} ${if (mom.topGameDelta > 0) "+" else "-"}${won(kotlin.math.abs(mom.topGameDelta))}",
                fontSize = 12.sp, color = TextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------- 신규) 결제 통계
@Composable
private fun PaymentStatsCard(spendings: List<Spending>, year: Int, month: Int) {
    val stats = remember(spendings, year, month) { SpendingInsightStats.paymentStats(spendings, year, month) }
    if (stats.count == 0) return
    DashCard {
        CardTitle("결제 통계", "${month}월 기준")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${stats.count}건", "결제 건수", Modifier.weight(1f), valueFontSize = 14.sp)
            StatTile(won(stats.average), "평균 결제액", Modifier.weight(1f), valueFontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(won(stats.maxAmount), "최고 단건", Modifier.weight(1f), valueFontSize = 14.sp)
            StatTile(if (stats.topWeekday.isNotBlank()) "${stats.topWeekday}요일" else "—", "최다 결제", Modifier.weight(1f), valueFontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------- 2) 게임별 월 추이 (올해, 누적 막대)
@Composable
private fun MonthlyTrendCard(spendings: List<Spending>, year: Int, accent: Color) {
    val trend = remember(spendings, year) { computeMonthlyTrend(spendings, year) } ?: return
    val monthGame = trend.monthGame
    val maxMonth = trend.maxMonth
    val legend = trend.legend
    fun colorOf(g: String) = if (g == "기타") EtcColor else GameData.colorFor(g).toColor()

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
    val rows = remember(spendings) { computePaymentBreakdown(spendings) }
    if (rows.isEmpty()) return
    DashCard {
        CardTitle("결제수단별 비중")
        Spacer(Modifier.height(12.dp))
        rows.forEach { (name, amt, total) ->
            BreakdownRow(name, amt, if (total > 0) amt.toFloat() / total else 0f, accent)
        }
    }
}

// ---------------------------------------------------------------- 신규) 충전 플랫폼별 비중
@Composable
private fun PlatformBreakdownCard(spendings: List<Spending>, accent: Color) {
    val rows = remember(spendings) { SpendingInsightStats.platformBreakdown(spendings) }
    if (rows.isEmpty()) return
    DashCard {
        CardTitle("충전 플랫폼별 비중")
        Spacer(Modifier.height(12.dp))
        rows.forEach { r ->
            BreakdownRow(r.name, r.amount, if (r.total > 0) r.amount.toFloat() / r.total else 0f, accent)
        }
    }
}

// ---------------------------------------------------------------- 신규) 정기결제 요약
@Composable
private fun SubscriptionSummaryCard(subs: List<Subscription>, accent: Color) {
    if (subs.isEmpty()) return
    val total = subs.sumOf { it.amount }
    DashCard {
        CardTitle("정기결제 요약")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("월 정기결제 ${subs.size}건", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("${won(total)} / 월", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        subs.take(5).forEach { s ->
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(GameData.colorFor(s.gameName).toColor()))
                Spacer(Modifier.width(9.dp))
                Text(s.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                Text(won(s.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("D-${s.dDay()}", fontSize = 11.sp, color = TextSecondary)
            }
        }
        if (subs.size > 5) {
            Spacer(Modifier.height(8.dp))
            Text("+${subs.size - 5}건", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

// ---------------------------------------------------------------- 4) 태그별 지출
@Composable
private fun TagBreakdownCard(spendings: List<Spending>, accent: Color) {
    val rows = remember(spendings) { computeTagBreakdown(spendings) }
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
/** 월간 인사이트 / 연간 리포트 세그먼트 토글. */
@Composable
private fun InsightTabToggle(tab: Int, onTab: (Int) -> Unit, accent: Color) {
    val labels = listOf("월간 인사이트", "연간 리포트")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFFF1F1F4)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == tab
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Color.White else Color.Transparent)
                    .clickable { onTab(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sel) TextPrimary else TextSecondary)
            }
        }
    }
}

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
