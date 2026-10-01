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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.data.SpendingInsightStats
import com.gatcha.log.ui.components.GldsTabs
import com.gatcha.log.ui.components.GldsTabsVariant
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won
import java.util.Calendar

private val EtcColor = Color(0xFFB8BDC6)

/**
 * 지출 인사이트 2.0 — 카드 없이 화면 폭 섹션 + 10 띠(마이페이지 · 지출 상세와 같은 규격).
 *
 * 월간: 이번 달 요약(옛 「N월 지출」 + 「전월 대비」 합침) → 예산 페이스 → 결제 통계 → 게임별 월 추이
 *      → 「전체 기간」 묶음(결제수단 · 충전 플랫폼 · 태그). 월간 값과 전체 기간 값이 섞여 읽히지 않게 묶음 머리를 둔다.
 * 연간: [AnnualReportContent].
 */
@Composable
fun SpendingInsightScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val year = viewModel.displayYear
    val month = viewModel.displayMonth
    // 이번 달 합계도 VM 값을 받는다 — remember(spendings) 는 달이 바뀌어도 지난달 값을 붙들었다.
    val monthTotal by viewModel.currentMonthTotal.collectAsStateWithLifecycle()

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scrollState)
                .padding(top = glgDetailContentTop(), bottom = 16.dp),
        ) {
            if (spendings.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "지출 기록이 쌓이면\n예산 페이스·게임별 추이·카테고리 비중을 분석해 드려요.",
                    fontSize = 14.sp, color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                return@Column
            }

            var tab by remember { mutableStateOf(0) }
            Box(Modifier.padding(horizontal = 16.dp)) { InsightTabToggle(tab, { tab = it }) }
            if (tab == 0) {
                val mom = remember(spendings, year, month) { SpendingInsightStats.momComparison(spendings, year, month) }
                val stats = remember(spendings, year, month) { SpendingInsightStats.paymentStats(spendings, year, month) }
                val trend = remember(spendings, year) { SpendingInsightStats.monthlyTrend(spendings, year) }
                val payRows = remember(spendings) { SpendingInsightStats.paymentBreakdown(spendings) }
                val platRows = remember(spendings) { SpendingInsightStats.platformBreakdown(spendings) }
                val tagRows = remember(spendings) { SpendingInsightStats.tagBreakdown(spendings) }

                // 조건부 섹션 사이에만 띠가 들어가게 — 빠진 섹션 자리에 띠가 겹치지 않는다.
                val sections = buildList<@Composable () -> Unit> {
                    add { MonthSummarySection(month, mom, accent) }
                    add { BudgetPaceSection(monthTotal, budget, accent) }
                    if (stats.count > 0) add { PaymentStatsSection(stats, month) }
                    if (trend != null) add { MonthlyTrendSection(trend, year) }
                    // 「전체 기간」 묶음 — 첫 섹션 위에 묶음 머리를 단다.
                    var groupHead = true
                    listOf(
                        Triple("결제수단별", null as String?, payRows.map { Triple(it.name, it.amount, if (it.total > 0) it.amount.toFloat() / it.total else 0f) }),
                        Triple("충전 플랫폼별", null, platRows.map { Triple(it.name, it.amount, if (it.total > 0) it.amount.toFloat() / it.total else 0f) }),
                        // 태그는 중복 집계라 합계 비율이 100%를 넘을 수 있어, 막대 분모는 최대 태그 금액(total).
                        Triple("태그별", "태그가 여럿이면 중복 집계", tagRows.map { Triple("#${it.name}", it.amount, it.amount.toFloat() / it.total) }),
                    ).forEach { (title, sub, rows) ->
                        if (rows.isEmpty()) return@forEach
                        val head = groupHead
                        groupHead = false
                        add { BreakdownSection(title, sub, rows, accent, groupHead = head) }
                    }
                }
                sections.forEachIndexed { i, section ->
                    if (i > 0) InsightBand()
                    section()
                }
            } else {
                AnnualReportContent(viewModel)
            }
        }
        GlgDetailHeaderOverlay("지출 인사이트", onBack, scrollState = scrollState)
    }
}

// ---------------------------------------------------------------- 공통 규격
private val InsightHair = Color(0xFFEEF0F2)
private val BarTrack = Color(0xFFEDEFF3)

@Composable
internal fun InsightBand() {
    Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFFF2F4F6)))
}

/** 섹션 — 좌우 20 · 위 22 · 아래 20. 제목 17 굵게 + 오른쪽 보조 12. */
@Composable
internal fun InsightSection(
    title: String?,
    sub: String? = null,
    top: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top, bottom = 20.dp)) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                if (sub != null) Text(sub, fontSize = 12.sp, color = TextSecondary)
            }
        }
        content()
    }
}

/** 값 15 굵게 · 라벨 12 — 타일 면 없이. [cols] 칸씩 줄바꿈. */
@Composable
internal fun InsightStatGrid(cells: List<Pair<String, String>>, cols: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        cells.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (value, label) ->
                    Column(Modifier.weight(1f)) {
                        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                        Text(label, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 비중 한 줄 — 이름 14 · 금액 15 굵게 · 비율 12, 아래 막대 6. */
@Composable
internal fun InsightShareRow(name: String, amount: Long, frac: Float, color: Color, dot: Color? = null) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
            }
            Text(name, fontSize = 14.sp, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
            Text(won(amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "${(frac * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary,
                modifier = Modifier.width(40.dp).wrapContentWidth(Alignment.End),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(BarTrack)) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

// ---------------------------------------------------------------- 1) 이번 달 요약 (옛 「N월 지출」 + 「전월 대비」)
@Composable
private fun MonthSummarySection(month: Int, mom: com.gatcha.log.data.MoMComparison, accent: Color) {
    val warn = Color(0xFFF59E0B)
    val up = mom.delta > 0
    InsightSection(null) {
        Text("${month}월 지출", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Text(won(mom.thisMonth), fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        // 지난달 기록 유무는 금액으로 가른다 — 줄어든 달은 증감률이 음수라 `percent >= 0` 으로는 "기록 없음" 이 떴다.
        if (mom.lastMonth > 0) {
            Text(
                "지난달보다 ${if (up) "▲" else "▼"} ${kotlin.math.abs(mom.percent)}% · ${if (up) "+" else "-"}${won(kotlin.math.abs(mom.delta))}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (up) warn else accent,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Text("지난달 기록 없음", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        if (mom.topGame.isNotBlank() && mom.topGameDelta != 0L) {
            Text(
                "증감 가장 큰 게임 · ${mom.topGame} ${if (mom.topGameDelta > 0) "+" else "-"}${won(kotlin.math.abs(mom.topGameDelta))}",
                fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 2) 예산 페이스 예측
@Composable
private fun BudgetPaceSection(monthTotal: Long, budget: Long, accent: Color) {
    val cal = remember { Calendar.getInstance() }
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val pace = SpendingInsightStats.budgetPace(monthTotal, dayOfMonth, daysInMonth)
    val projected = pace.projected

    InsightSection("예산 페이스", "${dayOfMonth}일 경과 · ${pace.remainingDays}일 남음") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("월말 예상", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(8.dp))
            Text(won(projected), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.alignByBaseline())
        }
        if (budget > 0) {
            val over = projected > budget
            val frac = (projected.toFloat() / budget).coerceIn(0f, 1f)
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(BarTrack)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(if (over) DangerText else accent))
            }
            val diff = kotlin.math.abs(projected - budget)
            Text(
                if (over) "이 페이스면 예산을 ${won(diff)} 초과할 것 같아요"
                else "이 페이스면 예산 안에서 ${won(diff)} 여유가 생겨요",
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (over) DangerText else accent,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text("예산을 설정하면 초과 여부를 예측해 드려요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(18.dp))
        InsightStatGrid(
            listOf(
                won(monthTotal) to "현재 지출",
                won(pace.dailyAvg) to "하루 평균",
                (if (budget > 0) won(budget) else "—") to "이번 달 예산",
            ),
            cols = 3,
        )
    }
}

// ---------------------------------------------------------------- 3) 결제 통계
@Composable
private fun PaymentStatsSection(stats: com.gatcha.log.data.PaymentStats, month: Int) {
    InsightSection("결제 통계", "${month}월 기준") {
        InsightStatGrid(
            listOf(
                "${stats.count}건" to "결제 건수",
                won(stats.average) to "평균 결제액",
                won(stats.maxAmount) to "최고 단건",
                (if (stats.topWeekday.isNotBlank()) "${stats.topWeekday}요일" else "—") to "최다 결제",
            ),
            cols = 2,
        )
    }
}

// ---------------------------------------------------------------- 4) 게임별 월 추이 (올해, 누적 막대)
@Composable
private fun MonthlyTrendSection(trend: com.gatcha.log.data.MonthlyTrend, year: Int) {
    val monthGame = trend.monthGame
    val maxMonth = trend.maxMonth
    val legend = trend.legend
    val curMonth = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 }
    fun colorOf(g: String) = if (g == "기타") EtcColor else GameData.colorFor(g).toColor()

    InsightSection("게임별 월 추이", "${year}년 · 누적") {
        val barAreaH = 120f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
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
                    Spacer(Modifier.height(4.dp))
                    val isCur = year == Calendar.getInstance().get(Calendar.YEAR) && m + 1 == curMonth
                    Text(
                        "${m + 1}", fontSize = 11.sp,
                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCur) TextPrimary else TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowLegend(legend.map { it to colorOf(it) })
    }
}

// ---------------------------------------------------------------- 5) 전체 기간 비중 (결제수단 · 충전 플랫폼 · 태그)
@Composable
private fun BreakdownSection(title: String, sub: String?, rows: List<Triple<String, Long, Float>>, accent: Color, groupHead: Boolean) {
    if (groupHead) {
        // 이 아래는 **전체 기간** 값이다 — 위쪽 월간 섹션과 기준이 달라 섞여 읽혔다.
        Text(
            "전체 기간 · 지금까지 쓴 돈의 구성",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp),
        )
    }
    InsightSection(title, sub, top = if (groupHead) 14.dp else 22.dp) {
        rows.forEach { (name, amount, frac) -> InsightShareRow(name, amount, frac, accent) }
    }
}

// ---------------------------------------------------------------- 공통 UI
/** 월간 인사이트 / 연간 리포트 세그먼트 토글. */
@Composable
private fun InsightTabToggle(tab: Int, onTab: (Int) -> Unit) {
    // GLDS 탭 Neutral — 같은 데이터의 보기 방식 전환(9/30).
    GldsTabs(listOf("월간 인사이트", "연간 리포트"), tab, variant = GldsTabsVariant.Neutral, onSelect = onTab)
}

/** 색 점 + 라벨 — 줄바꿈 범례. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowLegend(items: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}
