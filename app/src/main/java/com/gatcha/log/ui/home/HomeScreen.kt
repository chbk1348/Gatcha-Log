package com.gatcha.log.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.gatcha.log.ui.components.GlgPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GachaReport
import com.gatcha.log.data.GachaStats
import com.gatcha.log.data.HomeCards
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.game.GameInfoScreen
import com.gatcha.log.ui.profile.MyPageScreen
import com.gatcha.log.ui.spending.AddSpendingModal
import com.gatcha.log.ui.spending.SpendingScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.components.GlassBackground
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgStatusToast
import com.gatcha.log.ui.components.NoteSkeletonRow
import com.gatcha.log.ui.components.InfoColumn
import com.gatcha.log.ui.components.BudgetDialog
import com.gatcha.log.ui.components.BottomNavBar
import com.gatcha.log.ui.theme.*
import com.gatcha.log.util.num
import com.gatcha.log.util.won

@Composable
fun HomeScreen(viewModel: SpendingViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val showAddSpendingSheet = remember { mutableStateOf(false) }
    val spendingToEdit = remember { mutableStateOf<Spending?>(null) }
    val accent = LocalAccent.current

    // 풀스크린 하위 페이지(알림 상세·연간 리포트·지출 상세·HoYoLAB 연동·설정)가 열렸는지.
    // 열려 있으면 하단바와 FAB를 숨긴다. 각 탭 콘텐츠가 자신의 하위 페이지 상태를 보고한다.
    var subPageActive by remember { mutableStateOf(false) }

    // 탭별 스크롤 상태를 끌어올려, 하단바 탭 클릭 시 해당 페이지를 최상단으로 이동.
    val tabListStates = listOf(
        rememberLazyListState(), rememberLazyListState(),
        rememberLazyListState(), rememberLazyListState(),
    )
    val tabScope = rememberCoroutineScope()
    val onTabClick: (Int) -> Unit = { tab ->
        val sameTab = tab == selectedTab
        selectedTab = tab
        tabScope.launch {
            // 같은 탭 재탭 = 애니메이션 스크롤, 탭 전환 = 즉시 최상단
            if (sameTab) tabListStates[tab].animateScrollToItem(0)
            else tabListStates[tab].scrollToItem(0)
        }
    }


    val openEditor: (Spending?) -> Unit = { target ->
        spendingToEdit.value = target
        showAddSpendingSheet.value = true
    }

    // 앱 시작 시 1회 API 새로고침 (ennead 배너·이벤트 + HoYoLAB 노트) + 업데이트 확인.
    // ViewModel init 에서 호출하면 프로퍼티 초기화 순서 문제로 NPE 가 나므로 UI 에서 트리거.
    LaunchedEffect(Unit) {
        viewModel.refreshGameInfo()
        viewModel.checkForUpdate()
    }
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    // 루트 뒤로가기 방어 로직 (시스템/제스처 back):
    //  ① 하위 페이지(알림·연간리포트·지출상세)는 각자의 BackHandler 가 더 깊게 구성돼 먼저 처리
    //  ② 홈이 아닌 탭에서는 홈 탭으로 복귀
    //  ③ 홈에서는 2초 내 한 번 더 눌러야 종료(오발 종료 방지)
    val context = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler {
        when {
            selectedTab != 0 -> selectedTab = 0
            System.currentTimeMillis() - lastBackAt < 2000L -> (context as? Activity)?.finish()
            else -> {
                lastBackAt = System.currentTimeMillis()
                viewModel.showStatus("한 번 더 누르면 종료돼요")
            }
        }
    }

    // 루트 레벨 페이지 스왑 — 지출 추가/수정은 별도 페이지로 운영(바텀시트가 아닌 실제 페이지 전환).
    // 컨테이너 트랜스폼 morph: FAB(우하단 accent 원)에서 페이지가 스케일 + 모서리 라운드가 풀리며 펼쳐지고,
    // 닫을 땐 같은 위치로 줄며 라운드가 다시 차올라 FAB 로 흡수되는 느낌.
    AnimatedContent(
        targetState = showAddSpendingSheet.value,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(260)) },
        label = "rootPage",
    ) { addPage ->
        if (addPage) {
            // 등장 0→1 / 퇴장 1→0. 스케일(0.35→1, FAB 우하단 피벗) + 모서리 라운드(32→0dp)로 morph.
            val morph by transition.animateFloat(
                transitionSpec = { tween(durationMillis = 360, easing = FastOutSlowInEasing) },
                label = "fabMorph",
            ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 0.35f + 0.65f * morph
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.9f, 0.95f)
                        clip = true
                        shape = RoundedCornerShape((32f * (1f - morph)).dp)
                    },
            ) {
                AddSpendingModal(
                    spendingToEdit = spendingToEdit.value,
                    nudgeMessage = { game, amount -> viewModel.overspendNudge(game, amount, spendingToEdit.value?.id) },
                    onDismiss = {
                        showAddSpendingSheet.value = false
                        spendingToEdit.value = null
                    },
                    onSave = { spending ->
                        if (spendingToEdit.value == null) viewModel.addSpending(spending)
                        else viewModel.updateSpending(spending)
                        showAddSpendingSheet.value = false
                        spendingToEdit.value = null
                    },
                )
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    // 하위 페이지(연간 리포트·알림 상세 등)에서는 하단바·FAB를 아래로 슬라이드해 숨김
                    AnimatedVisibility(
                        visible = !subPageActive,
                        enter = slideInVertically(tween(280)) { it } + fadeIn(tween(280)),
                        exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(220)),
                    ) {
                        BottomNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = onTabClick,
                            onAddClick = { openEditor(null) },
                            accent = accent,
                            showFab = selectedTab <= 1, // 홈·지출 탭에서만 FAB 노출
                        )
                    }
                },
            ) { paddingValues ->
                GlassBackground(modifier = Modifier.fillMaxSize()) {
                    // 콘텐츠는 하단바 아래까지 확장(상단 인셋만 적용)
                    Box(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())) {
                        AnimatedContent(
                            targetState = selectedTab,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                // 탭 인덱스 방향에 따라 좌/우로 슬라이드 + 페이드
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(tween(260)) { w -> dir * w / 4 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(260)) { w -> -dir * w / 4 } + fadeOut(tween(180)))
                            },
                            label = "tab",
                        ) { tab ->
                            when (tab) {
                                0 -> HomeContent(
                                    viewModel,
                                    onNavigateToGameInfo = { onTabClick(2) },
                                    onNavigateToMyPage = { onTabClick(3) },
                                    listState = tabListStates[0],
                                    onSubPageChange = { subPageActive = it },
                                )
                                1 -> SpendingScreen(viewModel, onEditSpending = { openEditor(it) }, listState = tabListStates[1], onSubPageChange = { subPageActive = it })
                                2 -> GameInfoScreen(viewModel, listState = tabListStates[2], onSubPageChange = { subPageActive = it })
                                3 -> MyPageScreen(viewModel, listState = tabListStates[3], onSubPageChange = { subPageActive = it })
                            }
                        }

                        updateInfo?.let { info ->
                            UpdateDialog(
                                info = info,
                                onDownload = { viewModel.startInAppUpdate() },
                                onDismiss = { viewModel.dismissUpdate() },
                            )
                        }

                        // 인앱 업데이트 다운로드 진행 오버레이
                        updateProgress?.let { p -> UpdateProgressOverlay(p) }

                        // 전역 커스텀 토스트 (모든 탭 위에 표시)
                        // 하단바가 있을 땐 바 높이(100dp)만큼 띄우고, 하단바 없는 하위 페이지에선 24dp만 띄움
                        GlgStatusToast(
                            message = statusMessage,
                            onConsumed = { viewModel.clearStatus() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = if (subPageActive) 24.dp else 100.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    viewModel: SpendingViewModel,
    onNavigateToGameInfo: () -> Unit,
    onNavigateToMyPage: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onSubPageChange: (Boolean) -> Unit = {},
) {
    val spendings by viewModel.spendings.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val gameBudgets by viewModel.gameBudgets.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val attendanceToday by viewModel.attendanceToday.collectAsState()
    val banners by viewModel.activeBanners.collectAsState()
    val liveNotes by viewModel.liveNotes.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()
    val checkingIn by viewModel.checkingIn.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val attendanceStreak by viewModel.attendanceStreak.collectAsState()
    val account by viewModel.account.collectAsState()
    val gachaStats by viewModel.gachaStats.collectAsState()
    val homeCards by viewModel.homeCards.collectAsState()
    val pity by viewModel.pity.collectAsState()
    val gameInfoReady by viewModel.gameInfoReady.collectAsState()
    val hoyoTokenExpired by viewModel.hoyoTokenExpired.collectAsState()
    // 홈 진입·복귀 시 워커가 백그라운드에서 바꾼 플래그를 다시 읽어 배너에 반영.
    LaunchedEffect(Unit) { viewModel.refreshHoyoTokenExpired() }

    val monthlyTotal = remember(spendings) { viewModel.monthlyTotal() }
    val prevTotal = remember(spendings) { viewModel.prevMonthTotal() }

    // 가챠 기록 가져오기(홈 빠른 액션) — 가챠 효율 리포트와 동일 picker 패턴(*/* 로 SAF 회색처리 회피)
    val gachaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importGachaFromUris(uris)
    }

    // 게임별 한도 초과 게임(이번 달) — 알림센터 표시용
    val gameOverBudget = remember(spendings, gameBudgets) {
        if (gameBudgets.isEmpty()) emptyList()
        else {
            val totals = viewModel.monthlyTotalsByGame()
            GameData.games.mapNotNull { g ->
                val limit = gameBudgets[g.key] ?: 0L
                if (limit > 0 && (totals[g.key] ?: 0L) > limit) g.shortName else null
            }
        }
    }

    // 절약 팁 — 상황별 실제 조언(M 카드 '절약 팁' 칩이 토스트로 노출)
    val savingTip = remember(budget, monthlyTotal, gameOverBudget) {
        when {
            budget > 0 && monthlyTotal > budget -> "이번 달은 예산을 넘겼어요. 다음 픽업까지 무·저과금으로 천장을 모아보세요."
            gameOverBudget.isNotEmpty() -> "${gameOverBudget.first()} 한도를 넘었어요. 게임별 예산을 점검해보세요."
            budget <= 0 -> "월 예산을 정하면 페이스를 알려드려요. 보통 한 달 결제액의 80% 선이 적당해요."
            else -> "천장이 가까운 게임부터 모으면 50/50 손해를 줄일 수 있어요."
        }
    }

    // 천장 하이라이트 — 가장 임박한(티어 높고 카운트 큰) 게임 1종 (M 요약·K 토널 공유)
    val topPity = remember(pity) {
        GameData.games.mapNotNull { g ->
            val st = pity[g.key] ?: return@mapNotNull null
            if (st.count <= 0) return@mapNotNull null
            val banner = com.gatcha.log.data.GachaRateData.byKey(g.key)?.character ?: return@mapNotNull null
            PityHighlight(g, st.count, banner.softPity, banner.hardPity, com.gatcha.log.data.pityTierOf(st.count, banner))
        }.maxWithOrNull(compareBy({ it.tier.ordinal }, { it.count }))
    }

    // 게임별 이번 달 지출/한도 (D 섹션) — 지출 있거나 한도 설정된 게임만, 지출 내림차순
    val perGameSpend = remember(spendings, gameBudgets) {
        val totals = viewModel.monthlyTotalsByGame()
        GameData.games.mapNotNull { g ->
            val spent = totals[g.key] ?: 0L
            val limit = gameBudgets[g.key] ?: 0L
            if (spent <= 0L && limit <= 0L) null else GameSpend(g, spent, limit)
        }.sortedByDescending { it.spent }
    }

    // 임박 픽업 배너 — 7일 이내 종료, D-day 오름차순, 최대 4 (홈 캡슐 노출)
    // (상시/종료 배너는 endMillis 가 0 또는 과거라 dDay 가 0..7 범위 밖으로 자연 제외됨)
    val soonBanners = remember(banners) {
        banners.filter { it.dDay() in 0..7 }.sortedBy { it.dDay() }.take(4)
    }
    val nextBanner = soonBanners.firstOrNull()

    // 다음 픽업 확정 비용(가챠×지출) — 천장 누적·확률·1뽑 단가로 산출
    val nextBannerPlan = remember(nextBanner, pity) {
        nextBanner?.let { b ->
            val g = GameData.byNameOrNull(b.game) ?: return@let null
            val rate = com.gatcha.log.data.GachaRateData.byKey(g.key)?.character ?: return@let null
            val st = pity[g.key]
            val pulls = com.gatcha.log.data.GachaRateData.maxPullsToSecure(st?.count ?: 0, st?.guaranteed ?: false, rate)
            BannerPlan(pulls, pulls.toLong() * rate.wonPerPull)
        }
    }

    // 재화 임박 경보 — 85% 이상(가득 직전)인 게임 '전부'(원신뿐 아니라 스타레일·젠레스 등). 가장 찬 순.
    val resinAlerts = remember(liveNotes) {
        liveNotes.filter { it.maxResin > 0 && it.resinRatio >= 0.85f }
            .sortedByDescending { it.resinRatio }
            .map {
                ResinAlert(
                    GameData.byName(it.game).shortName, it.resinLabel,
                    it.currentResin, it.maxResin, it.resinRecoveryTime,
                    it.currentResin >= it.maxResin,
                )
            }
    }

    // 알림 계산 + 읽음(넛징) 상태
    val alerts = buildAlerts(monthlyTotal, budget, gameOverBudget, banners.map { it.dDay() to it.name }, attendanceToday, "${viewModel.displayYear}-${viewModel.displayMonth}")
    val readAlerts by viewModel.readAlerts.collectAsState()
    val unreadCount = alerts.count { it.key !in readAlerts }

    val showNotifications = remember { mutableStateOf(false) }
    val showBudgetDialog = remember { mutableStateOf(false) }
    val showHomeEdit = remember { mutableStateOf(false) }

    // 알림 상세 페이지에서 시스템 뒤로가기 시 홈으로 복귀
    BackHandler(enabled = showNotifications.value) { showNotifications.value = false }
    // 알림 상세가 열리면 상위(Scaffold)에 알려 하단바·FAB를 숨김
    LaunchedEffect(showNotifications.value) { onSubPageChange(showNotifications.value) }

    AnimatedContent(
        targetState = showNotifications.value,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState) {
                // 알림 열기: 오른쪽에서 슬라이드 인 (push)
                (slideInHorizontally(tween(300)) { w -> w } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> -w / 4 } + fadeOut(tween(220)))
            } else {
                // 홈 복귀: 오른쪽으로 슬라이드 아웃 (pop)
                (slideInHorizontally(tween(300)) { w -> -w / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> w } + fadeOut(tween(220)))
            }
        },
        label = "notif",
    ) { showNotif ->
        if (showNotif) {
            NotificationDetailScreen(
                alerts = alerts,
                onBack = { showNotifications.value = false },
                onBudget = { showNotifications.value = false; showBudgetDialog.value = true },
                onGameInfo = { showNotifications.value = false; onNavigateToGameInfo() },
            )
            return@AnimatedContent
        }

    GlgPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshGameInfo(force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // HoYoLAB 토큰 만료 감지 시 최상단 배너 — 자동 출석에서 AUTH 실패가 누적되어
        // 사용자가 출석을 며칠씩 못 챙기는 사례 방지. CTA 한 번으로 재연동 진입.
        if (hoyoTokenExpired) {
            item {
                TokenExpiredBanner(onReconnect = {
                    viewModel.requestOpenHoyolabLink()
                    onNavigateToMyPage()
                })
            }
        }
        // 슬림 헤더 (프로필·인사·연속·벨)
        item {
            HomeHeader(
                photoUrl = account.photoUrl,
                alertCount = unreadCount,
                onBellClick = { showNotifications.value = true; viewModel.markAlertsRead(alerts.map { it.key }) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
        // M — 이번 달 한눈에 (인사이트 요약). 대표 지시로 헤더 바로 밑 최상단 배치.
        item {
            MonthlySummaryCard(
                monthlyTotal = monthlyTotal,
                prevTotal = prevTotal,
                budget = budget,
                topPity = topPity,
                nextBanner = nextBanner,
                gameOverCount = gameOverBudget.size,
                onBudget = { showBudgetDialog.value = true },
                onPity = { viewModel.requestGameInfoAnchor(com.gatcha.log.ui.spending.GameInfoAnchor.PITY); onNavigateToGameInfo() },
                onTip = { viewModel.showStatus(savingTip) },
            )
            Spacer(Modifier.height(16.dp))
        }
        // 오늘 할 일 — 상태 기반 리스트(출석·재화·픽업·예산·천장). 각 항목 탭 시 해당 섹션으로 앵커링.
        item {
            // 로딩 중엔 스켈레톤, 배너·노트 로드 완료 시 전체 리스트를 한 번에 표출
            if (!gameInfoReady) {
                TodayTaskSkeleton()
            } else {
                TodayTaskCard(
                    tasks = resolveTodayTasks(
                        pendingAttendance = GameData.attendanceGames.count { it.key !in attendanceToday },
                        resins = resinAlerts,
                        urgentBanner = soonBanners.firstOrNull { it.dDay() <= 3 },
                        budget = budget,
                        monthlyTotal = monthlyTotal,
                        topPity = topPity,
                        onCheckInAll = { viewModel.checkInAll() },
                        onResin = { viewModel.requestGameInfoAnchor(com.gatcha.log.ui.spending.GameInfoAnchor.NOTES); onNavigateToGameInfo() },
                        onBanner = { viewModel.requestGameInfoAnchor(com.gatcha.log.ui.spending.GameInfoAnchor.BANNER); onNavigateToGameInfo() },
                        onPity = { viewModel.requestGameInfoAnchor(com.gatcha.log.ui.spending.GameInfoAnchor.PITY); onNavigateToGameInfo() },
                        onBudget = { showBudgetDialog.value = true },
                    ),
                    inProgress = checkingIn != null,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        // G — 가챠 현황 미니카드 (천장 + 다음 픽업, 읽기전용). 가챠 정체성 고정 노출.
        item {
            GachaStatusCard(
                topPity = topPity,
                nextBanner = nextBanner,
                nextBannerPlan = nextBannerPlan,
                onOpen = onNavigateToGameInfo,
                onImport = { gachaPicker.launch(arrayOf("*/*")) },
            )
            Spacer(Modifier.height(16.dp))
        }
        // 실시간 노트 (레진/배터리 등) — 출석은 '오늘 할 일'이 담당하므로 노트만 표시(중복 제거)
        item {
            GameStatusSection(
                hoyolab = hoyolab,
                liveNotes = liveNotes,
                isRefreshing = isRefreshing,
                onConfigClick = onNavigateToGameInfo,
            )
            Spacer(Modifier.height(16.dp))
        }
        // 픽업 배너 캡슐 (임박 종료) — 가장 임박 1건은 G 카드가 표시하므로 그 다음부터 노출
        val restBanners = soonBanners.drop(1)
        if (restBanners.isNotEmpty()) {
            item {
                Column {
                    Text("픽업 배너", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
                    restBanners.forEachIndexed { i, b ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        BannerCapsule(b)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        // 사용자 구성(표시·순서)대로 본문 카드 렌더
        homeCards.filter { it.visible }.forEach { card ->
            item(key = card.id) {
                when (card.id) {
                    // D — 지출 + 게임별 예산(N5)
                    HomeCards.SPENDING -> SpendingBudgetSection(
                        monthlyTotal = monthlyTotal,
                        budget = budget,
                        perGame = perGameSpend,
                        onEditBudget = { showBudgetDialog.value = true },
                    )
                    HomeCards.GACHA -> GachaSummarySection(stats = gachaStats, onOpen = onNavigateToGameInfo)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item { HomeEditButton { showHomeEdit.value = true } }
        item { Spacer(Modifier.height(120.dp)) }
    }
    }
    }

    if (showBudgetDialog.value) {
        BudgetDialog(
            overall = budget,
            gameBudgets = gameBudgets,
            monthlyTotals = remember(spendings) { viewModel.monthlyTotalsByGame() },
            onDismiss = { showBudgetDialog.value = false },
            onConfirm = { o, perGame -> viewModel.setBudgets(o, perGame); showBudgetDialog.value = false },
        )
    }

    if (showHomeEdit.value) {
        HomeCardEditDialog(
            cards = homeCards,
            onDismiss = { showHomeEdit.value = false },
            onSave = { viewModel.setHomeCards(it); showHomeEdit.value = false },
        )
    }

}

/** 알림 종류 — 카드 아이콘/색/이동 동작을 결정 */
private enum class AlertKind { BUDGET_OVER, BUDGET_NEAR, BUDGET_GAME_OVER, BANNER, ATTENDANCE }

/** 구조화된 홈 알림 (종류 + 메시지). message 가 읽음 처리 키로도 쓰임. */
private data class HomeAlert(val kind: AlertKind, val message: String, val key: String)

private fun buildAlerts(
    monthlyTotal: Long,
    budget: Long,
    gameOverBudget: List<String>,
    bannerDDays: List<Pair<Int, String>>,
    attendanceToday: Set<String>,
    monthKey: String,
): List<HomeAlert> = buildList {
    // 읽음(넛징) 키는 메시지가 아니라 안정적 식별자로 — 메시지에 든 가변값(%·D-day·남은 개수)이
    // 바뀌어도 한 번 확인하면 다시 안 뜨도록. 영구 저장돼도 자연 만료되게 기간을 키에 포함:
    // 예산=종류+월, 출석=오늘 날짜, 배너=배너명(1회성).
    if (budget > 0) {
        val pct = (monthlyTotal * 100 / budget).toInt()
        if (monthlyTotal > budget) add(HomeAlert(AlertKind.BUDGET_OVER, "이번 달 예산을 초과했어요 (${pct}%)", "budget_over:$monthKey"))
        else if (pct >= 90) add(HomeAlert(AlertKind.BUDGET_NEAR, "이번 달 예산의 ${pct}%를 사용했어요", "budget_near:$monthKey"))
    }
    gameOverBudget.forEach { name ->
        add(HomeAlert(AlertKind.BUDGET_GAME_OVER, "$name 이번 달 한도를 초과했어요", "budget_game_over:$name:$monthKey"))
    }
    bannerDDays.filter { it.first in 0..3 }.forEach { (d, name) ->
        add(HomeAlert(AlertKind.BANNER, "$name 픽업 배너 종료 ${if (d == 0) "D-DAY" else "D-$d"}", "banner:$name"))
    }
    val pending = GameData.attendanceGames.count { it.key !in attendanceToday }
    if (pending > 0) add(HomeAlert(AlertKind.ATTENDANCE, "오늘 출석체크가 ${pending}개 남아있어요", "attendance:${DateUtil.hoyoDayKey()}"))
}

/** 시간대별 인사말 */
internal fun greetingForNow(): String =
    when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..10 -> "좋은 아침이에요"
        in 11..16 -> "좋은 오후예요"
        in 17..21 -> "좋은 저녁이에요"
        else -> "오늘도 수고했어요"
    }

/** 실시간 노트 (HoYoLAB 레진/개척력/배터리 등). 출석·전체출석은 '오늘 할 일'이 담당하므로 여기선 노트만(중복 제거). */
@Composable
fun GameStatusSection(
    hoyolab: HoyolabConfig,
    liveNotes: List<LiveNote>,
    isRefreshing: Boolean,
    onConfigClick: () -> Unit,
) {
    val accent = LocalAccent.current
    GlassCard(shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            if (!hoyolab.isLinked) {
                // 미연동 — 노트 숨기고 연동 유도
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Link, null, tint = accent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("HoYoLAB 연동이 필요해요", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("실시간 노트를 보려면 연동하세요", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(14.dp))
                    GlgButton("HoYoLAB 연동하러 가기", onClick = onConfigClick, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Text("실시간 노트", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                when {
                    liveNotes.isNotEmpty() -> {
                        Spacer(Modifier.height(12.dp))
                        liveNotes.forEachIndexed { i, note ->
                            if (i > 0) Spacer(Modifier.height(8.dp))
                            NoteCapsule(note)
                        }
                    }
                    isRefreshing -> {
                        Spacer(Modifier.height(14.dp))
                        NoteSkeletonRow()
                    }
                    else -> {
                        Spacer(Modifier.height(8.dp))
                        Text("표시할 노트가 없어요", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

/** 홈 가챠 요약 카드 — 탭하면 게임 정보(가챠 통계 대시보드)로 이동. */
@Composable
fun GachaSummarySection(stats: GachaStats?, onOpen: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Casino, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("가챠 요약", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ChevronRight, "가챠 통계 보기", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(16.dp))
            if (stats == null) {
                Text("가챠 기록을 가져오면 요약이 표시돼요", fontSize = 12.sp, color = TextSecondary)
            } else {
                val totalFive = stats.byGame.values.sumOf { it.five }
                Row(Modifier.fillMaxWidth()) {
                    InfoColumn(num(stats.total), "총 뽑기", Modifier.weight(1f))
                    InfoColumn(num(totalFive), "획득 5성", Modifier.weight(1f))
                    InfoColumn("${stats.byGame.size}", "게임", Modifier.weight(1f))
                }
                val games = stats.byGame.keys.sortedBy { GachaReport.gameOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }
                games.forEach { gk ->
                    val g = stats.byGame[gk] ?: return@forEach
                    val (shortName, _, color) = GachaReport.gameInfo[gk] ?: Triple(gk, gk, accent)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(shortName, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            "${num(g.total)}뽑 · 5성 ${g.five}" + if (g.avgPity > 0) " · 평균천장 ${g.avgPity}" else "",
                            fontSize = 11.sp, color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** 홈 카드 편집 진입 — 리스트 하단의 잔잔한 텍스트 버튼. */
@Composable
private fun HomeEditButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Tune, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("홈 카드 편집", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
    }
}

/** 알림 상세 페이지 (홈) — 액션형: 알림 탭 시 관련 화면으로 이동. */
@Composable
private fun NotificationDetailScreen(
    alerts: List<HomeAlert>,
    onBack: () -> Unit,
    onBudget: () -> Unit,
    onGameInfo: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        GlgScreenHeader("알림", onBack, Modifier.padding(horizontal = 16.dp))
        if (alerts.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.NotificationsNone, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("새로운 알림이 없어요 🎉", color = TextSecondary, fontSize = 14.sp)
                Text("예산·픽업 배너·출석 알림이 여기에 모여요", color = Color.LightGray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                // 하단바 미노출 페이지 — 시스템 네비 인셋만 확보
                modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(alerts) { alert ->
                    NotificationCard(alert) {
                        when (alert.kind) {
                            AlertKind.BUDGET_OVER, AlertKind.BUDGET_NEAR, AlertKind.BUDGET_GAME_OVER -> onBudget()
                            AlertKind.BANNER, AlertKind.ATTENDANCE -> onGameInfo()
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationCard(alert: HomeAlert, onClick: () -> Unit) {
    val accent = LocalAccent.current
    // 종류별 아이콘·색·이동 안내문
    val icon: ImageVector; val tint: Color; val hint: String
    when (alert.kind) {
        AlertKind.BUDGET_OVER -> { icon = Icons.Default.Savings; tint = DangerText; hint = "예산 설정하기" }
        AlertKind.BUDGET_NEAR -> { icon = Icons.Default.Savings; tint = WarningText; hint = "예산 설정하기" }
        AlertKind.BUDGET_GAME_OVER -> { icon = Icons.Default.Savings; tint = DangerText; hint = "예산 설정하기" }
        AlertKind.BANNER -> { icon = Icons.Default.Bolt; tint = accent; hint = "게임 정보 보기" }
        AlertKind.ATTENDANCE -> { icon = Icons.Default.CheckCircleOutline; tint = accent; hint = "출석하러 가기" }
    }
    GlassCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.message, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(hint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 홈 최상단 만료 배너 — 자동 출석에서 HoYoLAB 쿠키 만료(AUTH 실패)가 감지되면 노출.
 * CTA "재연동" 클릭 → 마이페이지 ▸ 설정 ▸ HoYoLAB 연동까지 자동 진입(ViewModel 1회성 신호로).
 */
@Composable
private fun TokenExpiredBanner(onReconnect: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(accent.copy(alpha = 0.10f)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("HoYoLAB 토큰이 만료된 것 같아요", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("재연동하지 않으면 자동 출석이 안 돼요", fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            GlgButton("재연동", onClick = onReconnect, height = 36.dp, modifier = Modifier.width(80.dp))
        }
    }
}