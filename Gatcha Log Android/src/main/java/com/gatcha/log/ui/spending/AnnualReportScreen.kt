package com.gatcha.log.ui.spending

import com.gatcha.log.data.SpendingViewModel

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

/** 연간 리포트 — 연도 선택 + 월별/게임별 분석. 지출 인사이트의 '연간' 탭에 임베드되는 콘텐츠(헤더·스크롤은 부모 제공). */
@Composable
fun AnnualReportContent(viewModel: SpendingViewModel) {
    val accent = LocalAccent.current
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()

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

    // 카드 없이 화면 폭 섹션 + 10 띠 — 월간 인사이트와 같은 규격([InsightSection]).
    if (years.size > 1) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.padding(top = 18.dp),
        ) {
            items(years) { y -> FilterPill("${y}년", y == selectedYear, accent) { selectedYear = y } }
        }
    }
    InsightSection("${selectedYear}년 요약") {
        InsightStatGrid(
            listOf(won(total) to "총 지출", won(avg) to "월 평균", "${yearItems.size}회" to "총 기록"),
            cols = 3,
        )
        if (yearItems.isEmpty()) {
            Text("이 해의 지출 기록이 없어요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
        }
    }
    if (yearItems.isEmpty()) return
    InsightBand()
    InsightSection("월별 지출") {
        MonthlyBars(monthly, viewModel.displayMonth.takeIf { selectedYear == viewModel.displayYear })
    }
    if (byGame.isNotEmpty()) {
        InsightBand()
        InsightSection("게임별 지출") {
            byGame.forEach { (game, amt) ->
                val color = GameData.colorFor(game).toColor()
                InsightShareRow(game, amt, if (total > 0) amt.toFloat() / total else 0f, color, dot = color)
            }
        }
    }
}

@Composable
private fun MonthlyBars(monthly: LongArray, currentMonth: Int?) {
    val accent = LocalAccent.current
    val maxM = (monthly.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        for (m in 0 until 12) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.BottomCenter) {
                    val frac = (monthly[m].toFloat() / maxM).coerceIn(0f, 1f)
                    val h = if (monthly[m] > 0) frac.coerceAtLeast(0.05f) else 0f
                    val isCur = currentMonth != null && (m + 1) == currentMonth
                    if (h > 0f) {
                        Box(
                            Modifier.fillMaxWidth(0.7f).fillMaxHeight(h)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (isCur) accent else accent.copy(alpha = 0.3f)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val isCur = currentMonth != null && (m + 1) == currentMonth
                Text(
                    "${m + 1}", fontSize = 11.sp,
                    fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCur) TextPrimary else TextSecondary,
                )
            }
        }
    }
}
