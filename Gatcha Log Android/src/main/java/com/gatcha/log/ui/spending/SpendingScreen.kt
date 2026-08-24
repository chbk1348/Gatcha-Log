package com.gatcha.log.ui.spending

import com.gatcha.log.data.SpendingViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import com.gatcha.log.ui.components.GlgDropdownItem
import com.gatcha.log.ui.components.GlgDropdownMenu
import com.gatcha.log.ui.components.GlgHeaderPillChip
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDatePickerDialog
import com.gatcha.log.ui.components.GlgPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgTabHeader
import com.gatcha.log.ui.components.GlgTabHeaderHeight
import com.gatcha.log.ui.components.glgTabContentBottom
import com.gatcha.log.ui.components.GlgTopScrimFadeExtra as ScrimFadeExtra
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.theme.*
import com.gatcha.log.util.won
import java.util.Calendar

private enum class PeriodFilter(val label: String) { ALL("전체"), THIS_MONTH("이번 달"), LAST_MONTH("지난 달"), THIS_YEAR("올해"), CUSTOM("기간 지정") }

/** 하루의 시작(00:00) 밀리초 — 기간 지정 경계 계산용. */
private fun startOfDay(millis: Long): Long = DateUtil.localTimeOnDay(millis, 0)
private const val DAY_MS = 86_400_000L
private enum class TypeFilter(val label: String) { ALL("전체"), NORMAL("일반"), SUBSCRIPTION("구독") }
private enum class SortOrder(val label: String) { DATE_DESC("최신순"), DATE_ASC("오래된순"), AMOUNT_DESC("금액 높은순") }

/** 지출 탭 내 하위 페이지 네비게이션 상태 (List=목록, Insight=인사이트(연간 리포트 포함), Detail=지출 상세). */
private sealed interface SpendingScreenNav {
    data object List : SpendingScreenNav
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
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val compact by viewModel.spendingCompact.collectAsStateWithLifecycle()
    // 게임 필터 — 다중 선택(빈 Set = 전체). 필터 바텀시트에서 토글.
    var selectedGames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var period by remember { mutableStateOf(PeriodFilter.ALL) }
    // 기간 지정(직접 범위) — 기본값 '최근 한 달'. 시작>끝이면 판정에서 뒤집어 쓴다.
    var customStart by remember { mutableStateOf(System.currentTimeMillis() - 30 * DAY_MS) }
    var customEnd by remember { mutableStateOf(System.currentTimeMillis()) }
    // 퀵필터에서 연 날짜 선택 대상 — null=닫힘, 0=시작, 1=종료.
    var quickPickTarget by remember { mutableStateOf<Int?>(null) }
    var paymentFilter by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    val showFilterSheet = remember { mutableStateOf(false) }
    // 선택 모드(다중 선택) — 일괄 편집/삭제용.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val showBulkEdit = remember { mutableStateOf(false) }
    fun exitSelection() { selectionMode = false; selectedIds = emptySet() }
    // 지출 추가/수정 에디터는 루트 페이지 전환이라 이 화면이 컴포지션에서 빠진다 → 상세 진입 후
    // 에디터를 그냥 닫으면 nav 가 List 로 초기화돼 리스트로 튕기던 문제. 열린 상세 id 를
    // rememberSaveable 로 보존해, 재진입(에디터 닫힘·탭 복귀) 시 상세로 복원한다(iOS 내비 스택과 동일).
    var savedDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var nav by remember {
        val restored: SpendingScreenNav = savedDetailId
            ?.let { id -> spendings.firstOrNull { it.id == id } }
            ?.let { sp -> SpendingScreenNav.Detail(sp) }
            ?: SpendingScreenNav.List
        mutableStateOf(restored)
    }
    // 하위 페이지(연간 리포트·지출 상세)에서 시스템 뒤로가기 시 앱 종료가 아니라 목록으로 복귀
    BackHandler(enabled = nav != SpendingScreenNav.List) { nav = SpendingScreenNav.List }
    BackHandler(enabled = selectionMode) { exitSelection() }
    // 하위 페이지가 열리면 상위(Scaffold)에 알려 하단바·FAB를 숨김 + 상세 id 보존. (선택 모드는 탭바 유지 — 그 위에 선택 바 노출)
    LaunchedEffect(nav) {
        onSubPageChange(nav != SpendingScreenNav.List)
        savedDetailId = (nav as? SpendingScreenNav.Detail)?.spending?.id
    }


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
                    // 상대값(비중·평소·같은 항목 이력)의 모집단 — 상세 화면은 계산하지 않고 받기만 한다.
                    all = spendings,
                    onBack = { nav = SpendingScreenNav.List },
                    onEdit = { onEditSpending(live) },
                    onDelete = { viewModel.deleteSpending(live.id); nav = SpendingScreenNav.List },
                )
                return@AnimatedContent
            }
            SpendingScreenNav.List -> Unit
        }

    // 헤더(액션 바)를 리스트 위 오버레이로 고정하고, 리스트 첫 항목에 '헤더 자리'((상태바+헤더) 높이)를 둔다.
    // 다른 탭(게임정보/마이페이지)과 동일한 고정 인셋 방식 — 예전엔 월지출 히어로(가변 높이)라 측정했지만
    // 이제 고정 헤더뿐이라 측정 폐기(측정값이 인셋과 얽혀 탭마다 간격이 어긋나던 문제 해결).
    val statusBarDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val heroSpacerDp = GlgTabHeaderHeight + statusBarDp
    // 로드인 스태거 — 앱 진입 후 1회만 등장(인덱스=정렬 리스트 내 위치). 탭 재진입 재생 방지(세션 영속).

    // 성능: 필터/정렬/그룹은 입력이 바뀔 때만 재계산(remember). 스크롤 콜랩스로 화면이 매 프레임
    // 재구성돼도 리스트 전체를 다시 훑지 않는다 — 지출 항목이 많을수록 스크롤 버벅임을 크게 줄인다.
    val filtered = remember(spendings, selectedGames, paymentFilter, typeFilter, period, customStart, customEnd, viewModel.displayYear, viewModel.displayMonth, lastY, lastM) {
        // 기간 지정 경계는 항목 밖에서 한 번만 — 종료일은 **그날 전체를 포함**한다(자정 경계에서 하루가 빠지지 않게).
        val rangeLo = minOf(startOfDay(customStart), startOfDay(customEnd))
        val rangeHi = maxOf(startOfDay(customStart), startOfDay(customEnd)) + DAY_MS
        spendings.filter { s ->
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
                    PeriodFilter.CUSTOM -> s.dateMillis in rangeLo until rangeHi
                }
        }
    }
    // 표시 그룹 — 금액순=항목별 단일 그룹, 날짜순=같은 날짜 묶음. 정렬/필터 바뀔 때만 재계산.
    val amountMode = sortOrder == SortOrder.AMOUNT_DESC
    val dayGroups: List<List<Spending>> = remember(filtered, sortOrder) {
        when (sortOrder) {
            SortOrder.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }.map { listOf(it) }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.dateMillis }.groupBy { it.dayKey }.values.toList()
            else -> filtered.sortedByDescending { it.dateMillis }.groupBy { it.dayKey }.values.toList()
        }
    }

    // 상단 스크림 — 리스트가 액션 바 아래로 스크롤될 때만 배경색 그라데이션으로 페이드(최상단에선 숨김).
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val topScrimAlpha by animateFloatAsState(if (scrolled) 0.88f else 0f, label = "topScrim")

    Box(Modifier.fillMaxSize()) {
        GlgPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshSpending() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = glgTabContentBottom()),
            ) {
                // 히어로 자리(고정) — 위에 히어로 오버레이가 뜬다.
                item { Spacer(Modifier.height(heroSpacerDp)) }

                // 퀵필터 — 시트를 열지 않고 기간을 바꾸고, 걸린 필터를 바로 뗄 수 있게.
                // 리스트와 함께 스크롤된다(고정하면 상단 두 줄을 영구히 먹는다).
                // 선택 모드에서도 치우지 않는다 — 사라지면 리스트가 위로 밀려 올라가 화면이 튄다(iOS 파리티).
                run {
                    item {
                        SpendingQuickFilters(
                            period = period,
                            onPeriod = { period = it },
                            customStart = customStart,
                            customEnd = customEnd,
                            onPickStart = { quickPickTarget = 0 },
                            onPickEnd = { quickPickTarget = 1 },
                            selectedGames = selectedGames,
                            onGameToggle = { g -> selectedGames = if (g in selectedGames) selectedGames - g else selectedGames + g },
                            onGamesClear = { selectedGames = emptySet() },
                            sortOrder = sortOrder,
                            onSort = { sortOrder = it },
                            paymentFilter = paymentFilter,
                            onPaymentClear = { paymentFilter = null },
                            typeFilter = typeFilter,
                            onTypeClear = { typeFilter = TypeFilter.ALL },
                        )
                    }
                }

            val onItemClick: (Spending) -> Unit = { sp ->
                if (selectionMode) {
                    selectedIds = if (sp.id in selectedIds) selectedIds - sp.id else selectedIds + sp.id
                } else nav = SpendingScreenNav.Detail(sp)
            }
            if (filtered.isEmpty()) {
                item { EmptyState() }
            } else {
                // 미리 계산된 dayGroups 를 순회만 한다(매 프레임 재정렬·재그룹 없음).
                dayGroups.forEachIndexed { gi, dayItems ->
                    item(key = if (amountMode) dayItems.first().id else dayItems.first().dayKey) {
                        Box {
                            SpendingDayCard(
                                dateLabel = if (amountMode) null else dayItems.first().dateLabel,
                                dayTotal = if (amountMode) 0L else dayItems.sumOf { it.amount },
                                items = dayItems,
                                selectionMode = selectionMode, selectedIds = selectedIds,
                                compact = compact, onItemClick = onItemClick,
                            )
                        }
                    }
                }
            }
        }
    }
        // 상단 스크림 — **상태바 영역만** 덮는다. 스크롤될 때만 나타나 상태바 글자와 콘텐츠가 겹쳐 읽히는 걸 막는다.
        // (헤더 버튼 줄은 덮지 않는다 — 콘텐츠가 버튼 아래로 지나가는 연출을 유지)
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(statusBarDp + ScrimFadeExtra)
                .graphicsLayer { alpha = topScrimAlpha }
                .background(
                    Brush.verticalGradient(
                        0f to Color.White,
                        0.35f to Color.White,
                        1f to Color.Transparent,
                    ),
                ),
        )
        // 헤더 오버레이 — 액션 바(보기 전환/목록 조작)만. 헤더만 불투명(버튼), 좌우·하단 투명 →
        // 리스트가 버튼 아래로 자연스럽게 슬라이드되며 비친다. 높이 측정 → 리스트 첫 항목 자리(spacer).
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            // 헤더 바 배경 없음(투명) — 리스트가 버튼 아래로 지나가고, 버튼만 불투명(solidBackground).
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // 좌측 = 보기 전환(캘린더·인사이트), 우측 = 목록 조작(선택·필터).
                GlgTabHeader(
                    "",
                    leading = {
                        GlgCircleIconButton(Icons.Default.CalendarMonth, "캘린더", outlined = true, solidBackground = true) { nav = SpendingScreenNav.Calendar }
                        GlgCircleIconButton(Icons.Default.Insights, "인사이트", outlined = true, solidBackground = true) { nav = SpendingScreenNav.Insight }
                    },
                ) {
                    GlgCircleIconButton(Icons.Default.Checklist, "선택", outlined = true, solidBackground = true) { selectionMode = true; selectedIds = emptySet() }
                    GlgCircleIconButton(Icons.Default.Tune, "필터", outlined = true, badgeCount = activeFilterCount, solidBackground = true) { showFilterSheet.value = true }
                }
            }
        }
        // 맨 위로 — 한참 내려간 뒤에만 뜬다. 우하단(탭바 FAB 바로 위).
        // 지출은 날짜 카드가 길어 아래로 많이 내려가는데, 되돌아오려면 계속 쓸어올려야 했다.
        val scrollScope = rememberCoroutineScope()
        // 히어로 자리(0) · 퀵필터(1) 를 지나 실제 내역까지 내려왔을 때 뜬다.
        // 예전엔 `>= 5` 였는데, 그건 날짜 카드가 5장 이상 있어야 한다는 뜻이라 **기록이 적으면
        // 끝까지 내려도 안 떴다**. 되돌아갈 거리가 생겼는지로 판단해야 한다.
        val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
        AnimatedVisibility(
            visible = showScrollTop && !selectionMode,
            enter = fadeIn(glgStandardSpec()) + scaleIn(glgStandardSpec(), initialScale = 0.8f),
            exit = fadeOut(glgShortSpec()) + scaleOut(glgShortSpec(), targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = glgTabContentBottom()),
        ) {
            GlgCircleIconButton(
                Icons.Default.KeyboardArrowUp,
                "맨 위로",
                outlined = true,
                solidBackground = true,
            ) {
                scrollScope.launch { listState.animateScrollToItem(0) }
            }
        }
        AnimatedVisibility(
            visible = selectionMode,
            enter = slideInVertically(glgStandardSpec()) { it } + fadeIn(glgStandardSpec()),
            exit = slideOutVertically(glgShortSpec()) { it } + fadeOut(glgShortSpec()),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            SelectionActionBar(
                count = selectedIds.size,
                onEdit = { if (selectedIds.isNotEmpty()) showBulkEdit.value = true else viewModel.showStatus("선택된 항목이 없어요") },
                onDelete = {
                    if (selectedIds.isNotEmpty()) { viewModel.deleteSpendings(selectedIds); exitSelection() }
                    else viewModel.showStatus("선택된 항목이 없어요")
                },
                onCancel = { exitSelection() },
            )
        }
    }
    }

    // 퀵필터의 기간 지정 날짜 선택 — 시트를 거치지 않고 리스트에서 바로 연다.
    quickPickTarget?.let { target ->
        GlgDatePickerDialog(
            initialMillis = if (target == 0) customStart else customEnd,
            onDismiss = { quickPickTarget = null },
            onConfirm = { m ->
                if (target == 0) customStart = m else customEnd = m
                quickPickTarget = null
            },
        )
    }

    if (showFilterSheet.value) {
        SpendingFilterSheet(
            selectedGames = selectedGames,
            onGameToggle = { g -> selectedGames = if (g in selectedGames) selectedGames - g else selectedGames + g },
            onGamesClear = { selectedGames = emptySet() },
            period = period, onPeriod = { period = it },
            customStart = customStart, onCustomStart = { customStart = it },
            customEnd = customEnd, onCustomEnd = { customEnd = it },
            paymentFilter = paymentFilter, onPayment = { paymentFilter = it },
            typeFilter = typeFilter, onType = { typeFilter = it },
            sortOrder = sortOrder, onSort = { sortOrder = it },
            onReset = {
                selectedGames = emptySet(); period = PeriodFilter.ALL
                paymentFilter = null; typeFilter = TypeFilter.ALL; sortOrder = SortOrder.DATE_DESC
                customStart = System.currentTimeMillis() - 30 * DAY_MS
                customEnd = System.currentTimeMillis()
            },
            onDismiss = { showFilterSheet.value = false },
        )
    }

    if (showBulkEdit.value) {
        BulkEditSheet(
            count = selectedIds.size,
            onApply = { game, dateMillis, tags ->
                viewModel.bulkEditSpendings(selectedIds, game, dateMillis, tags)
                showBulkEdit.value = false
                exitSelection()
            },
            onDismiss = { showBulkEdit.value = false },
        )
    }
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
internal fun FilterPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    GlgChip(label = label, selected = selected, color = accent, onClick = onClick)
}

/**
 * 지출 리스트 상단 퀵필터 — **드롭다운 3개**(기간·게임·정렬) + 걸린 나머지 필터 해제 줄.
 *
 * 칩을 축마다 늘어놓으면 기간만 5개라 줄이 금방 넘쳤다. 축 하나당 알약 하나로 접고,
 * 열면 목록에서 고른다. 알약 라벨은 **기본값이면 축 이름, 아니면 고른 값** — 접힌 상태에서도
 * 지금 뭐가 걸렸는지 읽힌다.
 *
 * 결제 수단·구분은 드롭다운으로 두지 않는다(자주 안 바뀐다). 대신 걸려 있으면 아랫줄에
 * **해제 칩**으로 뜬다 — 무엇을 걸지는 필터 시트, 뗄 때는 여기.
 */
@Composable
private fun SpendingQuickFilters(
    period: PeriodFilter,
    onPeriod: (PeriodFilter) -> Unit,
    customStart: Long,
    customEnd: Long,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    selectedGames: Set<String>,
    onGameToggle: (String) -> Unit,
    onGamesClear: () -> Unit,
    sortOrder: SortOrder,
    onSort: (SortOrder) -> Unit,
    paymentFilter: String?,
    onPaymentClear: () -> Unit,
    typeFilter: TypeFilter,
    onTypeClear: () -> Unit,
) {
    val accent = LocalAccent.current
    val hasOthers = paymentFilter != null || typeFilter != TypeFilter.ALL
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        // ① 축별 드롭다운 — 가로 스크롤(알약이 넘치면 잘리지 않고 밀린다).
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 기간 — 단일 선택.
            QuickFilterMenu(
                label = if (period == PeriodFilter.ALL) "기간" else period.label,
                active = period != PeriodFilter.ALL,
            ) { close ->
                PeriodFilter.entries.forEach { p ->
                    GlgDropdownItem(text = p.label, selected = period == p, onClick = { onPeriod(p); close() })
                }
            }
            // 날짜 두 칸은 이 줄에 두지 않는다 — '기간 지정'일 때만 아랫줄로 펼친다.
            // 게임 — 다중 선택이라 **고를 때마다 닫지 않는다**(여러 개를 연달아 켜는 게 정상 사용).
            QuickFilterMenu(
                label = when {
                    selectedGames.isEmpty() -> "게임"
                    selectedGames.size == 1 -> GameData.byNameOrNull(selectedGames.first())?.shortName ?: selectedGames.first()
                    else -> "게임 ${selectedGames.size}"
                },
                active = selectedGames.isNotEmpty(),
            ) { close ->
                GlgDropdownItem(text = "전체", selected = selectedGames.isEmpty(), onClick = { onGamesClear(); close() })
                GameData.games.forEach { g ->
                    GlgDropdownItem(
                        text = g.shortName,
                        selected = g.displayName in selectedGames,
                        onClick = { onGameToggle(g.displayName) },
                    )
                }
            }
            // 정렬 — 거르는 게 아니라 늘어놓는 방식이지만, 기간만큼 자주 바꾼다.
            QuickFilterMenu(
                label = if (sortOrder == SortOrder.DATE_DESC) "정렬" else sortOrder.label,
                active = sortOrder != SortOrder.DATE_DESC,
            ) { close ->
                SortOrder.entries.forEach { s ->
                    GlgDropdownItem(text = s.label, selected = sortOrder == s, onClick = { onSort(s); close() })
                }
            }
        }
        // ② '기간 지정'을 고르면 아래로 스르륵 펼쳐진다 — 평소엔 줄 자체가 없다.
        AnimatedVisibility(
            visible = period == PeriodFilter.CUSTOM,
            enter = expandVertically(glgStandardSpec()) + fadeIn(glgStandardSpec()),
            exit = shrinkVertically(glgShortSpec()) + fadeOut(glgShortSpec()),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlgHeaderPillChip(label = DateUtil.dayKey(customStart), selected = true, color = accent, onClick = onPickStart)
                Text("~", fontSize = 12.sp, color = TextSecondary)
                GlgHeaderPillChip(label = DateUtil.dayKey(customEnd), selected = true, color = accent, onClick = onPickEnd)
            }
        }
        // ③ 드롭다운에 없는 필터가 걸려 있으면 해제 칩. 없으면 줄 자체가 사라져 공간을 안 먹는다.
        if (hasOthers) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                paymentFilter?.let { m ->
                    GlgHeaderPillChip(label = "$m  ✕", selected = true, color = accent) { onPaymentClear() }
                }
                if (typeFilter != TypeFilter.ALL) {
                    GlgHeaderPillChip(label = "${typeFilter.label}  ✕", selected = true, color = accent) { onTypeClear() }
                }
            }
        }
    }
}

/**
 * 퀵필터 알약 하나 + 그 아래 펼쳐지는 드롭다운.
 *
 * [content] 는 닫기 콜백을 받는다 — 단일 선택 축은 고르면 닫고, 다중 선택(게임)은 열어 둔다.
 */
@Composable
private fun QuickFilterMenu(
    label: String,
    active: Boolean,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GlgHeaderPillChip(label = "$label  ▾", selected = active) { expanded = true }
        GlgDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

/**
 * 같은 날짜 지출을 한 카드로 묶은 그룹 카드 — 상단에 날짜·합계 헤더(first-end), 아래로 지출 행들(구분선 분리).
 * dateLabel 이 null 이면 헤더 없이 행만(금액순 평면 리스트의 단일 항목 카드).
 */
@Composable
private fun SpendingDayCard(
    dateLabel: String?,
    dayTotal: Long,
    items: List<Spending>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    compact: Boolean,
    onItemClick: (Spending) -> Unit,
) {
    val accent = LocalAccent.current
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 6.dp),
    ) {
        Column {
            if (dateLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dateLabel, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text(won(dayTotal), fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = DividerColor)
            }
            items.forEachIndexed { idx, sp ->
                if (idx > 0) HorizontalDivider(color = DividerColor)
                SpendingRow(sp, selectionMode, sp.id in selectedIds, compact) { onItemClick(sp) }
            }
        }
    }
}

/** 지출 한 건 행(first-end) — 좌측: 재화 아이콘/게임색 + 게임명·아이템·태그, 우측: 금액·셰브론. 카드 안에 들어가는 행. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpendingRow(spending: Spending, selectionMode: Boolean, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val iconSize = if (compact) 30.dp else 40.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null, tint = if (selected) accent else TextSecondary, modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        // 리딩 — 게임색 약칭 배지(iOS SpendingRow 와 통일: 라운드 사각 + 게임색 14% 배경 + 약칭).
        val abbr = GameData.byNameOrNull(spending.gameName)?.abbr ?: spending.gameName.take(2)
        Box(
            Modifier.size(iconSize).clip(RoundedCornerShape(if (compact) 9.dp else 12.dp)).background(spending.gameColor.toColor().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(abbr, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.Black, color = spending.gameColor.toColor())
        }
        Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
        if (compact) {
            // 컴팩트: 게임명 · 아이템 한 줄(태그·결제수단·정기뱃지 숨김).
            val sub = spending.itemName.ifBlank { null }
            Text(
                spending.gameName + (if (sub != null) "  ·  $sub" else ""),
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
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
        }
        Spacer(Modifier.width(8.dp))
        Text(won(spending.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (!selectionMode) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SpendingFilterSheet(
    selectedGames: Set<String>, onGameToggle: (String) -> Unit, onGamesClear: () -> Unit,
    period: PeriodFilter, onPeriod: (PeriodFilter) -> Unit,
    customStart: Long, onCustomStart: (Long) -> Unit,
    customEnd: Long, onCustomEnd: (Long) -> Unit,
    paymentFilter: String?, onPayment: (String?) -> Unit,
    typeFilter: TypeFilter, onType: (TypeFilter) -> Unit,
    sortOrder: SortOrder, onSort: (SortOrder) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccent.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // 기간 지정 날짜 선택 대상 — null=닫힘, 0=시작, 1=종료.
    var pickTarget by remember { mutableStateOf<Int?>(null) }
    // '적용'은 onDismiss 를 바로 호출하면 시트가 애니메이션 없이 사라진다 →
    // sheetState.hide() 로 슬라이드다운 애니메이션 후 닫는다(스크림/드래그 닫힘과 동일한 모션).
    val animatedDismiss = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
        Unit
    }
    // 시트가 상태바를 침범하지 않도록 콘텐츠 높이에 상한을 둔다.
    // ModalBottomSheet 의 content 슬롯은 wrap-content(높이 무제한)라, 상한이 없으면
    // skipPartiallyExpanded 로 시트가 화면 끝(상태바 위)까지 자라고 weight 스크롤도 동작하지 않는다.
    // 화면 높이의 90% 로 제한 → 상단에 여백이 남아 상태바를 넘지 않고, 넘치면 내부 스크롤로 처리.
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp)
                .navigationBarsPadding(),
        ) {
            Text("상세 필터", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            // 필터 그룹 — 내용이 시트 최대 높이를 넘으면 이 영역만 내부 스크롤(헤더/하단 버튼은 고정).
            // weight(1f, fill = false): 짧으면 필요한 만큼만, 길면 남은 높이까지 차지 후 스크롤.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 게임 — 다중 선택(선택됨 색은 게임별 대표색). '전체'는 선택 해제.
                FilterGroup("게임") {
                    FilterPill("전체", selectedGames.isEmpty(), accent) { onGamesClear() }
                    GameData.games.forEach { g ->
                        FilterPill(g.shortName, g.displayName in selectedGames, g.color.toColor()) { onGameToggle(g.displayName) }
                    }
                }
                FilterGroup("기간") {
                    PeriodFilter.entries.forEach { p -> FilterPill(p.label, period == p, accent) { onPeriod(p) } }
                    // '기간 지정'을 고른 경우에만 시작·종료 날짜 칩을 편다 — 평소엔 시트가 길어지지 않는다.
                    if (period == PeriodFilter.CUSTOM) {
                        FilterPill("시작 ${DateUtil.dayKey(customStart)}", false, accent) { pickTarget = 0 }
                        FilterPill("종료 ${DateUtil.dayKey(customEnd)}", false, accent) { pickTarget = 1 }
                    }
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
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlgOutlineButton("초기화", onReset, Modifier.weight(1f))
                GlgButton("적용", animatedDismiss, Modifier.weight(1.4f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // 기간 지정 날짜 선택 — 앱 공통 달력 다이얼로그(시트 위에 뜬다).
    pickTarget?.let { target ->
        GlgDatePickerDialog(
            initialMillis = if (target == 0) customStart else customEnd,
            onDismiss = { pickTarget = null },
            onConfirm = { m ->
                if (target == 0) onCustomStart(m) else onCustomEnd(m)
                pickTarget = null
            },
        )
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

/** 선택 모드 하단 액션 바 — 떠 있는 알약(Capsule) 형태. 선택 개수 + 취소/삭제/일괄 편집. */
@Composable
private fun SelectionActionBar(
    count: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // 하단 탭바 바로 위에 붙도록 띄우고, 좌우 16dp 마진의 넓은 알약. 하단바 스타일(그림자 X, 보더 O).
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 80.dp),
        shape = RoundedCornerShape(50),
        color = Color.White,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, DividerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${count}건", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            PillActionButton("취소", filled = false, onClick = onCancel)
            PillActionButton("삭제", filled = false, onClick = onDelete)
            PillActionButton("일괄 편집", filled = true, onClick = onEdit)
        }
    }
}

/** 선택 바 전용 컴팩트 알약 버튼 — filled=강조 채움, 아니면 흰 배경+아웃라인. */
@Composable
private fun PillActionButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(accent) else Modifier.background(Color.White).border(1.dp, ChipIdleBorder, shape))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) Color.White else ChipIdleText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 일괄 편집 시트 — 게임/날짜 변경 + 태그 추가. 비워둔 항목은 미변경. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BulkEditSheet(count: Int, onApply: (String?, Long?, List<String>) -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var game by remember { mutableStateOf<String?>(null) }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var tags by remember { mutableStateOf<Set<String>>(emptySet()) }
    val showDate = remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp).navigationBarsPadding(),
        ) {
            Text("일괄 편집 · ${count}건", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("선택한 항목만 바뀌고, ‘변경 안 함’으로 둔 항목은 그대로예요.", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            FilterGroup("게임") {
                FilterPill("변경 안 함", game == null, accent) { game = null }
                GameData.games.forEach { g -> FilterPill(g.shortName, game == g.displayName, g.color.toColor()) { game = g.displayName } }
            }
            FilterGroup("날짜") {
                FilterPill(dateMillis?.let { DateUtil.label(it) } ?: "변경 안 함", dateMillis != null, accent) { showDate.value = true }
                if (dateMillis != null) FilterPill("지우기", false, accent) { dateMillis = null }
            }
            FilterGroup("태그 추가") {
                GameData.suggestedTags.forEach { t -> FilterPill(t, t in tags, accent) { tags = if (t in tags) tags - t else tags + t } }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlgOutlineButton("취소", onDismiss, Modifier.weight(1f))
                GlgButton("적용", { onApply(game, dateMillis, tags.toList()) }, Modifier.weight(1.4f))
            }
        }
    }
    if (showDate.value) {
        val dps = rememberDatePickerState(initialSelectedDateMillis = dateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDate.value = false },
            confirmButton = { TextButton({ dateMillis = dps.selectedDateMillis; showDate.value = false }) { Text("확인") } },
            dismissButton = { TextButton({ showDate.value = false }) { Text("취소") } },
        ) { DatePicker(state = dps) }
    }
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
