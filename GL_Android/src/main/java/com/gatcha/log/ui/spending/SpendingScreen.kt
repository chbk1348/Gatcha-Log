package com.gatcha.log.ui.spending

import com.gatcha.log.data.SpendingViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.components.ChipIdleBorder
import com.gatcha.log.ui.components.ChipIdleText
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgChipVariant
import com.gatcha.log.ui.components.GlgPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.CurrencyIcon
import com.gatcha.log.ui.components.GameCurrency
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgTabHeader
import com.gatcha.log.ui.components.GlgOutlineButton
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
    data object Calendar : SpendingScreenNav
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
    // 게임 필터 — 다중 선택(빈 Set = 전체). 필터 바텀시트에서 토글.
    var selectedGames by remember { mutableStateOf<Set<String>>(emptySet()) }
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
        selectedGames.isNotEmpty(),
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
                (slideInHorizontally(glgStandardSpec()) { w -> w } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeOut(glgShortSpec()))
            } else {
                // 목록으로 복귀: 오른쪽으로 슬라이드 아웃 (pop)
                (slideInHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> w } + fadeOut(glgShortSpec()))
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
            is SpendingScreenNav.Calendar -> {
                CalendarScreen(viewModel, onBack = { nav = SpendingScreenNav.List })
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

    // 월 지출 히어로 스크롤 축소(iOS 패리티) — 히어로를 리스트 위 오버레이로 두고, 리스트 첫 항목에
    // '히어로 자리'(고정 높이)를 둬서 콜랩스해도 콘텐츠 maxOffset 이 바뀌지 않게 한다(최하단 떨림 방지).
    // 콜랩스는 첫 항목(자리) 스크롤 오프셋 기준(자리가 충분히 높아 매끄럽게 보간).
    val density = LocalDensity.current
    val maxCollapsePx = with(density) { 64.dp.toPx() }
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / maxCollapsePx).coerceIn(0f, 1f)
        }
    }
    // 펼친 히어로(헤더+카드) 높이 — 콜랩스 0일 때 측정해 '히어로 자리' spacer 로 사용. 측정 전 추정 기본값.
    var heroOverlayPx by remember { mutableIntStateOf(0) }
    val heroSpacerDp = if (heroOverlayPx > 0) with(density) { heroOverlayPx.toDp() } else 196.dp

    Box(Modifier.fillMaxSize()) {
        GlgPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshSpending() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // 히어로 자리(고정) — 위에 히어로 오버레이가 뜬다.
                item { Spacer(Modifier.height(heroSpacerDp)) }

            val filtered = spendings.filter { s ->
                (selectedGames.isEmpty() || s.gameName in selectedGames) &&
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
        // 히어로 오버레이 — 헤더(액션+필터) + 월 지출 카드(축소). 흰 배경으로 아래 콘텐츠 가림. 콜랩스 0일 때 높이 측정 → 자리(spacer).
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp)
                .onSizeChanged { if (collapse == 0f) heroOverlayPx = it.height },
        ) {
            GlgTabHeader("") {
                CalendarButton { nav = SpendingScreenNav.Calendar }
                InsightButton { nav = SpendingScreenNav.Insight }
                AnnualReportButton { nav = SpendingScreenNav.Annual }
                FilterButton(activeFilterCount) { showFilterSheet.value = true }
            }
            MonthlySummaryCard(viewModel.displayMonth, monthlyTotal, prevMonthTotal, collapse)
        }
    }
    }

    if (showFilterSheet.value) {
        SpendingFilterSheet(
            selectedGames = selectedGames,
            onGameToggle = { g -> selectedGames = if (g in selectedGames) selectedGames - g else selectedGames + g },
            onGamesClear = { selectedGames = emptySet() },
            period = period, onPeriod = { period = it },
            paymentFilter = paymentFilter, onPayment = { paymentFilter = it },
            typeFilter = typeFilter, onType = { typeFilter = it },
            sortOrder = sortOrder, onSort = { sortOrder = it },
            onReset = {
                selectedGames = emptySet(); period = PeriodFilter.ALL
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
fun MonthlySummaryCard(month: Int, total: Long, prevTotal: Long, collapse: Float = 0f) {
    val accent = LocalAccent.current
    val diff = total - prevTotal
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 히어로 섹션 — 이번 달 총 지출을 큰 숫자로 강조(좌측 정렬) + 지난달 대비. [collapse] 로 스크롤 축소.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = (18 - 7 * collapse).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("${month}월 지출", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(won(total), fontSize = (34 - 14 * collapse).sp, fontWeight = FontWeight.Black, color = TextPrimary, maxLines = 1)
            if (total > 0 || prevTotal > 0) {
                Spacer(Modifier.height((6 * (1f - collapse)).dp))
                Text(
                    "지난달 " + (if (diff == 0L) "동일" else (if (diff > 0) "+" else "-") + won(kotlin.math.abs(diff))),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.height((18 * (1f - collapse)).dp).graphicsLayer { alpha = 1f - collapse; clip = true },
                    color = if (diff > 0) DangerText else if (diff < 0) accent else TextSecondary,
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

/**
 * 지출 분석 헤더 우측 진입 알약 버튼 (캘린더·인사이트·연간 리포트 공용).
 * 세 버튼의 높이·아이콘 크기를 강제로 통일한다(고정 높이 + 동일 아이콘 14dp).
 * [label] 이 null 이면 아이콘 전용(캘린더)으로 폭만 줄인다.
 */
@Composable
private fun HeaderPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    // 게임 정보 탭의 헤더 알약(GachaRateButton)과 100% 동일한 스펙(shape 11·color 0.10·border 1.5/0.30·
    // padding h12 v7·아이콘 14dp·텍스트 12sp). 아이콘 전용(캘린더)은 0폭 텍스트로 높이만 동일하게 맞춘다.
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
            Icon(icon, contentDescription, tint = accent, modifier = Modifier.size(14.dp))
            if (label != null) {
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            } else {
                Text("", fontSize = 12.sp, fontWeight = FontWeight.Bold) // 높이 동일화용 0폭 텍스트
            }
        }
    }
}

/** 연간 리포트 진입 버튼. */
@Composable
private fun AnnualReportButton(onClick: () -> Unit) =
    HeaderPillButton(Icons.Default.Assessment, "연간 리포트", null, onClick)

/** 캘린더 진입 버튼 — 공간 절약을 위해 아이콘 전용(높이는 동일). */
@Composable
private fun CalendarButton(onClick: () -> Unit) =
    HeaderPillButton(Icons.Default.CalendarMonth, null, "캘린더", onClick)

/** 인사이트 진입 버튼. */
@Composable
private fun InsightButton(onClick: () -> Unit) =
    HeaderPillButton(Icons.Default.Insights, "인사이트", null, onClick)

@Composable
fun GameFilterRow(selectedGame: String?, modifier: Modifier = Modifier, onGameSelected: (String?) -> Unit) {
    val accent = LocalAccent.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.padding(vertical = 8.dp)) {
        item { FilterPill("전체", selectedGame == null, accent) { onGameSelected(null) } }
        items(GameData.games) { game ->
            // 게임 칩은 단일 규격 유지, 선택됨 색만 게임별 대표색으로.
            FilterPill(game.shortName, selectedGame == game.displayName, game.color.toColor()) { onGameSelected(game.displayName) }
        }
    }
}

@Composable
internal fun FilterPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    GlgChip(label = label, selected = selected, color = accent, onClick = onClick)
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
        // 재화 아이콘 미지원 게임(zzz·명조·엔드필드·이환)은 원형 폴백 대신 카드 좌측 게임색 세로 막대.
        val hasCurrencyIcon = GameCurrency.forGame(spending.gameName)?.iconUrl != null
        val barPx = with(LocalDensity.current) { 5.dp.toPx() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!hasCurrencyIcon) {
                        Modifier.drawBehind { drawRect(spending.gameColor.toColor(), size = Size(barPx, size.height)) }
                    } else Modifier,
                )
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasCurrencyIcon) {
                CurrencyIcon(spending.gameName, size = 30.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spending.gameName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (spending.isSubscription) {
                        Spacer(Modifier.width(6.dp))
                        GlgBadge("정기", spending.gameColor.toColor())
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
            Spacer(Modifier.width(4.dp))
            // 상세 진입은 행 전체 클릭(.clickable)으로 처리 — iOS 처럼 chevron 인디케이터만 표시.
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** 지출 내역 태그 칩 — 강조색 옅은 배경 + 강조색 글자로 가독성 확보. */
@Composable
internal fun TagChip(tag: String) {
    GlgChip(label = tag, variant = GlgChipVariant.Tag)
}

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val active = activeCount > 0
    // 공통 칩(GlgChip)과 동일 규격 — 14dp 라운드·흰 배경+옅은 아웃라인, 선택(필터 활성)=accent 채움. Tune 아이콘 유지.
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) accent else Color.White,
        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, ChipIdleBorder),
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Tune, null, tint = if (active) Color.White else ChipIdleText, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                if (active) "필터 $activeCount" else "필터",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (active) Color.White else ChipIdleText,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SpendingFilterSheet(
    selectedGames: Set<String>, onGameToggle: (String) -> Unit, onGamesClear: () -> Unit,
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
            // 게임 — 다중 선택(선택됨 색은 게임별 대표색). '전체'는 선택 해제.
            FilterGroup("게임") {
                FilterPill("전체", selectedGames.isEmpty(), accent) { onGamesClear() }
                GameData.games.forEach { g ->
                    FilterPill(g.shortName, g.displayName in selectedGames, g.color.toColor()) { onGameToggle(g.displayName) }
                }
            }
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
    // 섹션 카드형 — 제목(카드 위) + 연회색 카드 안에 칩 FlowRow. 지출 추가 모달 SectionCard 와 동일 규격.
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(start = 2.dp))
    Spacer(Modifier.height(8.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
    Spacer(Modifier.height(14.dp))
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
