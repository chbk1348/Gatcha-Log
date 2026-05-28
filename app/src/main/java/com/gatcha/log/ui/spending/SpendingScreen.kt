package com.gatcha.log.ui.spending

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.gatcha.log.ui.components.GlgPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.CurrencyIcon
import com.gatcha.log.ui.components.GameCurrency
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.InfoColumn
import com.gatcha.log.ui.theme.*
import com.gatcha.log.util.won
import java.util.Calendar

private enum class PeriodFilter(val label: String) { ALL("전체"), THIS_MONTH("이번 달"), LAST_MONTH("지난 달"), THIS_YEAR("올해") }
private enum class TypeFilter(val label: String) { ALL("전체"), NORMAL("일반"), SUBSCRIPTION("구독") }
private enum class SortOrder(val label: String) { DATE_DESC("최신순"), DATE_ASC("오래된순"), AMOUNT_DESC("금액 높은순") }

/** 지출 탭 내 하위 페이지 네비게이션 상태 (List=목록, Annual=연간 리포트, Detail=지출 상세). */
private sealed interface SpendingScreenNav {
    data object List : SpendingScreenNav
    data object Annual : SpendingScreenNav
    data object Insight : SpendingScreenNav
    data class Detail(val spending: Spending) : SpendingScreenNav
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingScreen(
    viewModel: SpendingViewModel,
    onEditSpending: (Spending) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onSubPageChange: (Boolean) -> Unit = {},
) {
    val spendings by viewModel.spendings.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedGameFilter by remember { mutableStateOf<String?>(null) }
    var period by remember { mutableStateOf(PeriodFilter.ALL) }
    var paymentFilter by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    val showFilterSheet = remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf<SpendingScreenNav>(SpendingScreenNav.List) }
    // 하위 페이지(연간 리포트·지출 상세)에서 시스템 뒤로가기 시 앱 종료가 아니라 목록으로 복귀
    BackHandler(enabled = nav != SpendingScreenNav.List) { nav = SpendingScreenNav.List }
    // 하위 페이지가 열리면 상위(Scaffold)에 알려 하단바·FAB를 숨김
    LaunchedEffect(nav) { onSubPageChange(nav != SpendingScreenNav.List) }

    val monthlyTotal = remember(spendings) { viewModel.monthlyTotal() }
    val prevMonthTotal = remember(spendings) { previousMonthTotal(spendings) }

    // 지난 달 연/월 계산
    val (lastY, lastM) = remember {
        val c = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
    }
    val activeFilterCount = listOf(
        selectedGameFilter != null,
        period != PeriodFilter.ALL,
        paymentFilter != null,
        typeFilter != TypeFilter.ALL,
        sortOrder != SortOrder.DATE_DESC,
    ).count { it }

    AnimatedContent(
        targetState = nav,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState !is SpendingScreenNav.List) {
                // 하위 페이지 열기: 오른쪽에서 슬라이드 인 (push)
                (slideInHorizontally(tween(300)) { w -> w } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> -w / 4 } + fadeOut(tween(220)))
            } else {
                // 목록으로 복귀: 오른쪽으로 슬라이드 아웃 (pop)
                (slideInHorizontally(tween(300)) { w -> -w / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> w } + fadeOut(tween(220)))
            }
        },
        label = "spendingNav",
    ) { navState ->
        when (navState) {
            is SpendingScreenNav.Annual -> {
                AnnualReportScreen(viewModel, onBack = { nav = SpendingScreenNav.List })
                return@AnimatedContent
            }
            is SpendingScreenNav.Insight -> {
                SpendingInsightScreen(viewModel, onBack = { nav = SpendingScreenNav.List })
                return@AnimatedContent
            }
            is SpendingScreenNav.Detail -> {
                // 편집 반영을 위해 라이브 목록에서 재조회(삭제됐으면 스냅샷으로 폴백 → 종료 애니 동안 표시 유지)
                val live = spendings.firstOrNull { it.id == navState.spending.id } ?: navState.spending
                SpendingDetailScreen(
                    spending = live,
                    onBack = { nav = SpendingScreenNav.List },
                    onEdit = { onEditSpending(live) },
                    onDelete = { viewModel.deleteSpending(live.id); nav = SpendingScreenNav.List },
                )
                return@AnimatedContent
            }
            SpendingScreenNav.List -> Unit
        }

    GlgPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshSpending() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("지출 분석", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        InsightButton { nav = SpendingScreenNav.Insight }
                        AnnualReportButton { nav = SpendingScreenNav.Annual }
                    }
                }
            }
            item { MonthlySummaryCard(viewModel.displayMonth, monthlyTotal, prevMonthTotal) }
            item { Spacer(Modifier.height(10.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("지출 내역", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    FilterButton(activeFilterCount) { showFilterSheet.value = true }
                }
            }
            item { GameFilterRow(selectedGameFilter) { selectedGameFilter = it } }

            val filtered = spendings.filter { s ->
                (selectedGameFilter == null || s.gameName == selectedGameFilter) &&
                    (paymentFilter == null || s.paymentMethod == paymentFilter) &&
                    when (typeFilter) {
                        TypeFilter.ALL -> true
                        TypeFilter.NORMAL -> !s.isSubscription
                        TypeFilter.SUBSCRIPTION -> s.isSubscription
                    } &&
                    when (period) {
                        PeriodFilter.ALL -> true
                        PeriodFilter.THIS_MONTH -> DateUtil.isSameMonth(s.dateMillis, viewModel.displayYear, viewModel.displayMonth)
                        PeriodFilter.LAST_MONTH -> DateUtil.isSameMonth(s.dateMillis, lastY, lastM)
                        PeriodFilter.THIS_YEAR -> DateUtil.isSameYear(s.dateMillis, viewModel.displayYear)
                    }
            }

            if (filtered.isEmpty()) {
                item { EmptyState() }
            } else if (sortOrder == SortOrder.AMOUNT_DESC) {
                // 금액순 — 날짜 그룹 없이 평면 리스트
                items(filtered.sortedByDescending { it.amount }, key = { it.id }) { spending ->
                    HistoryItem(
                        spending = spending,
                        onClick = { nav = SpendingScreenNav.Detail(spending) },
                    )
                }
            } else {
                val sorted = if (sortOrder == SortOrder.DATE_ASC) filtered.sortedBy { it.dateMillis }
                else filtered.sortedByDescending { it.dateMillis }
                val grouped = sorted.groupBy { it.dayKey }
                grouped.forEach { (_, items) ->
                    item { DateHeader(items.first().dateLabel, items.sumOf { it.amount }) }
                    items(items, key = { it.id }) { spending ->
                        HistoryItem(
                            spending = spending,
                            onClick = { nav = SpendingScreenNav.Detail(spending) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
    }

    if (showFilterSheet.value) {
        SpendingFilterSheet(
            period = period, onPeriod = { period = it },
            paymentFilter = paymentFilter, onPayment = { paymentFilter = it },
            typeFilter = typeFilter, onType = { typeFilter = it },
            sortOrder = sortOrder, onSort = { sortOrder = it },
            onReset = {
                selectedGameFilter = null; period = PeriodFilter.ALL
                paymentFilter = null; typeFilter = TypeFilter.ALL; sortOrder = SortOrder.DATE_DESC
            },
            onDismiss = { showFilterSheet.value = false },
        )
    }
}

private fun previousMonthTotal(spendings: List<Spending>): Long {
    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    return spendings.filter { DateUtil.isSameMonth(it.dateMillis, y, m) }.sumOf { it.amount }
}

@Composable
fun MonthlySummaryCard(month: Int, total: Long, prevTotal: Long) {
    val accent = LocalAccent.current
    val diff = total - prevTotal
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 좌: 이번 달 총 지출 (1줄 컴팩트, 가운데 정렬)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${month}월", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Text(won(total), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            // 우: 지난달 대비 증감
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("지난달", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.width(6.dp))
                Text(
                    (if (diff >= 0) "+" else "-") + won(kotlin.math.abs(diff)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (diff > 0) DangerText else accent,
                )
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/** 지출 분석 헤더 우측의 연간 리포트 진입 버튼 — 강조색 옅은 알약. */
@Composable
private fun AnnualReportButton(onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = accent.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(alpha = 0.30f)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Assessment, null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("연간 리포트", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

/** 지출 분석 헤더 우측의 인사이트 진입 버튼 — 강조색 옅은 알약. */
@Composable
private fun InsightButton(onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = accent.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(alpha = 0.30f)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Insights, null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("인사이트", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

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

/** 지출 상세 페이지 — 전체 정보 + 수정/삭제(확인 다이얼로그). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingDetailScreen(
    spending: Spending,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        GlgScreenHeader("지출 상세", onBack, Modifier.padding(horizontal = 16.dp))
        Column(
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 요약 카드 (게임·금액·날짜)
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CurrencyIcon(spending.gameName, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(spending.gameName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (spending.isSubscription) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = spending.gameColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                        Text("정기", fontSize = 10.sp, color = spending.gameColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            GameCurrency.forGame(spending.gameName)?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(it.label, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(won(spending.amount), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(spending.dateLabel, fontSize = 13.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            // 상세 정보 카드
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    DetailRow("항목", spending.itemName.ifBlank { "—" })
                    HorizontalDivider(color = DividerColor)
                    DetailRow("결제 수단", spending.paymentMethod.ifBlank { "—" })
                    HorizontalDivider(color = DividerColor)
                    DetailRow("구분", if (spending.isSubscription) "정기 결제" else "일반")
                    if (spending.memo.isNotBlank()) {
                        HorizontalDivider(color = DividerColor)
                        DetailRow("메모", spending.memo)
                    }
                    if (spending.tags.isNotEmpty()) {
                        HorizontalDivider(color = DividerColor)
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text("태그", fontSize = 13.sp, color = TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                spending.tags.forEach { tag -> TagChip(tag) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // 액션 (삭제 / 수정)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlgOutlineButton("삭제", onClick = { confirmDelete = true }, modifier = Modifier.weight(1f))
                GlgButton("수정", onClick = onEdit, modifier = Modifier.weight(1.4f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        GlgDialog(
            title = "이 지출을 삭제할까요?",
            onDismiss = { confirmDelete = false },
            confirmText = "삭제",
            onConfirm = { confirmDelete = false; onDelete() },
            dismissText = "취소",
        ) {
            Text("삭제하면 되돌릴 수 없어요.", fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(80.dp))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
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

@Composable
fun GameFilterRow(selectedGame: String?, onGameSelected: (String?) -> Unit) {
    val accent = LocalAccent.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        item { FilterPill("전체", selectedGame == null, accent) { onGameSelected(null) } }
        items(GameData.games) { game ->
            FilterPill(game.shortName, selectedGame == game.displayName, accent) { onGameSelected(game.displayName) }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accent else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accent else DividerColor),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else Color.DarkGray,
        )
    }
}

@Composable
fun DateHeader(date: String, total: Long) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(date, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
        Text(won(total), fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryItem(spending: Spending, onClick: () -> Unit) {
    val accent = LocalAccent.current

    GlassCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrencyIcon(spending.gameName, size = 30.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spending.gameName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (spending.isSubscription) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = spending.gameColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                            Text("정기", fontSize = 9.sp, color = spending.gameColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(
                    listOfNotNull(spending.itemName.ifBlank { null }, spending.paymentMethod).joinToString(" · "),
                    fontSize = 11.sp, color = TextSecondary,
                )
                if (spending.tags.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        spending.tags.forEach { tag -> TagChip(tag) }
                    }
                }
            }
            Text(won(spending.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            // 3점 메뉴 대신 명시적 [상세] 버튼 (수정·삭제는 상세 페이지에서)
            Surface(
                color = accent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text("상세", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** 지출 내역 태그 칩 — 강조색 옅은 배경 + 강조색 글자로 가독성 확보. */
@Composable
private fun TagChip(tag: String) {
    val accent = LocalAccent.current
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(7.dp),
    ) {
        Text(
            "#$tag",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val active = activeCount > 0
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) accent else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) accent else DividerColor),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Tune, null, tint = if (active) Color.White else TextSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                if (active) "필터 $activeCount" else "필터",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (active) Color.White else Color.DarkGray,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SpendingFilterSheet(
    period: PeriodFilter, onPeriod: (PeriodFilter) -> Unit,
    paymentFilter: String?, onPayment: (String?) -> Unit,
    typeFilter: TypeFilter, onType: (TypeFilter) -> Unit,
    sortOrder: SortOrder, onSort: (SortOrder) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccent.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text("상세 필터", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            FilterGroup("기간") {
                PeriodFilter.entries.forEach { p -> FilterPill(p.label, period == p, accent) { onPeriod(p) } }
            }
            FilterGroup("결제 수단") {
                FilterPill("전체", paymentFilter == null, accent) { onPayment(null) }
                GameData.paymentMethods.forEach { m -> FilterPill(m, paymentFilter == m, accent) { onPayment(m) } }
            }
            FilterGroup("구분") {
                TypeFilter.entries.forEach { t -> FilterPill(t.label, typeFilter == t, accent) { onType(t) } }
            }
            FilterGroup("정렬") {
                SortOrder.entries.forEach { s -> FilterPill(s.label, sortOrder == s, accent) { onSort(s) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlgOutlineButton("초기화", onReset, Modifier.weight(1f))
                GlgButton("적용", onDismiss, Modifier.weight(1.4f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("아직 기록된 지출이 없어요", color = TextSecondary, fontSize = 14.sp)
        Text("+ 버튼으로 첫 지출을 기록해보세요", color = Color.LightGray, fontSize = 12.sp)
    }
}
