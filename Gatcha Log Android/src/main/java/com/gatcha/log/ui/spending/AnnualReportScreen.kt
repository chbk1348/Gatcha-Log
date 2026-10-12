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
import com.gatcha.log.data.AnnualMonthBar
import com.gatcha.log.data.AnnualReportLogic
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

    // 집계는 전부 공용 [AnnualReportLogic] — iOS 와 같은 함수다(27.52.0). 화면은 그리기만 한다.
    // '오늘'은 그릴 때마다 읽어 키로 건다 — 달이 넘어가면 월 평균의 분모(지나온 달 수)가 바뀐다.
    val displayYear = viewModel.displayYear
    val displayMonth = viewModel.displayMonth
    val years = remember(spendings, displayYear) { AnnualReportLogic.years(spendings, displayYear) }
    var selectedYear by remember(years) { mutableStateOf(years.firstOrNull() ?: displayYear) }
    val report = remember(spendings, selectedYear, displayYear, displayMonth) {
        AnnualReportLogic.report(spendings, selectedYear, displayYear, displayMonth)
    }

    // 카드 없이 화면 폭 섹션 + 10 띠 — 월간 인사이트와 같은 규격([InsightSection]).
    if (years.size > 1) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.padding(top = 18.dp),
        ) {
            items(years) { y -> FilterPill("${y}년", y == report.year, accent) { selectedYear = y } }
        }
    }
    InsightSection("${report.year}년 요약") {
        InsightStatGrid(
            listOf(won(report.total) to "총 지출", won(report.monthlyAverage) to "월 평균", "${report.count}회" to "총 기록"),
            cols = 3,
        )
        if (report.count == 0) {
            Text("이 해의 지출 기록이 없어요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
        }
    }
    if (report.count == 0) return
    InsightBand()
    InsightSection("월별 지출") {
        MonthlyBars(report.months, displayMonth.takeIf { report.year == displayYear })
    }
    if (report.byGame.isNotEmpty()) {
        InsightBand()
        InsightSection("게임별 지출") {
            report.byGame.forEach { share ->
                val color = GameData.colorFor(share.game).toColor()
                InsightShareRow(share.game, share.amount, share.ratio.toFloat(), color, dot = color)
            }
        }
    }
}

/** 월별 막대 12칸 — 높이 비율(최소 높이 포함)은 공용 [AnnualReportLogic] 이 정해 준다. */
@Composable
private fun MonthlyBars(months: List<AnnualMonthBar>, currentMonth: Int?) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        for (bar in months) {
            val m = bar.month - 1
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.BottomCenter) {
                    val h = bar.barRatio.toFloat()
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
