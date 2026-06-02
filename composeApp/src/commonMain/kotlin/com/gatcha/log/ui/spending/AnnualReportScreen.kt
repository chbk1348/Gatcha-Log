package com.gatcha.log.ui.spending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.InfoColumn
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

/** 연간 리포트 전체 페이지 — 뒤로가기 헤더 + 연도 선택 + 월별/게임별 분석. */
@Composable
fun AnnualReportScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val spendings by viewModel.spendings.collectAsState()

    val years = remember(spendings) {
        (spendings.map { DateUtil.year(it.dateMillis) } + viewModel.displayYear).distinct().sortedDescending()
    }
    var selectedYear by remember(years) { mutableStateOf(years.firstOrNull() ?: viewModel.displayYear) }
    val yearItems = remember(spendings, selectedYear) { spendings.filter { DateUtil.isSameYear(it.dateMillis, selectedYear) } }
    val total = remember(yearItems) { yearItems.sumOf { it.amount } }
    val monthly = remember(yearItems) {
        LongArray(12).also { arr -> yearItems.forEach { arr[DateUtil.month(it.dateMillis) - 1] += it.amount } }
    }
    val byGame = remember(yearItems) {
        yearItems.groupBy { it.gameName }.map { it.key to it.value.sumOf { s -> s.amount } }.sortedByDescending { it.second }
    }
    val months = if (selectedYear == viewModel.displayYear) viewModel.displayMonth else monthly.count { it > 0 }.coerceAtLeast(1)
    val avg = if (months > 0) total / months else 0L

    Column(Modifier.fillMaxSize()) {
        GlgScreenHeader("연간 리포트", onBack, Modifier.padding(horizontal = 16.dp))
        Column(
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (years.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(years) { y -> FilterPill("${y}년", y == selectedYear, accent) { selectedYear = y } }
                }
                Spacer(Modifier.height(14.dp))
            }
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoColumn(won(total), "총 지출", Modifier.weight(1f))
                        InfoColumn(won(avg), "월 평균", Modifier.weight(1f))
                        InfoColumn("${yearItems.size}회", "총 기록", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("월별 지출", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    MonthlyBars(monthly, viewModel.displayMonth.takeIf { selectedYear == viewModel.displayYear })
                    if (byGame.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text("게임별 지출", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(Modifier.height(10.dp))
                        byGame.forEach { (game, amt) ->
                            GameBreakdownRow(game, amt, if (total > 0) (amt.toFloat() / total) else 0f)
                        }
                    }
                    if (yearItems.isEmpty()) {
                        Text("이 해의 지출 기록이 없어요", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonthlyBars(monthly: LongArray, currentMonth: Int?) {
    val accent = LocalAccent.current
    val maxM = (monthly.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (m in 0 until 12) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.BottomCenter) {
                    val frac = (monthly[m].toFloat() / maxM).coerceIn(0f, 1f)
                    val h = if (monthly[m] > 0) frac.coerceAtLeast(0.05f) else 0f
                    val isCur = currentMonth != null && (m + 1) == currentMonth
                    if (h > 0f) {
                        Box(
                            Modifier.fillMaxWidth(0.7f).fillMaxHeight(h).clip(RoundedCornerShape(3.dp))
                                .background(if (isCur) accent else accent.copy(alpha = 0.45f)),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text("${m + 1}", fontSize = 8.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun GameBreakdownRow(game: String, amount: Long, frac: Float) {
    val color = GameData.colorFor(game)
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(game, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Text(won(amount), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${(frac * 100).toInt()}%", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(ProgressEmpty)) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}
