package com.gatcha.log.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
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
import com.gatcha.log.ui.components.GlgTabHeaderHeight
import com.gatcha.log.ui.components.GlgTopScrimFadeExtra as ScrimFadeExtra
import com.gatcha.log.ui.components.GlgPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.gatcha.log.data.HomeAlert
import com.gatcha.log.data.HomeAlertKind
import com.gatcha.log.data.HomeLogic
import com.gatcha.log.ui.savings.PickupPlannerHomeCard
import com.gatcha.log.ui.savings.SavingsChallengeHomeCard
import com.gatcha.log.ui.savings.SavingsPlannerScreen
import com.gatcha.log.ui.savings.SavingsChallengeScreen
import com.gatcha.log.data.GachaReport
import com.gatcha.log.data.GachaStats
import com.gatcha.log.data.HomeCards
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.game.GameInfoScreen
import com.gatcha.log.ui.profile.MyPageScreen
import com.gatcha.log.ui.spending.AddSpendingModal
import com.gatcha.log.data.GameInfoAnchor
import com.gatcha.log.ui.spending.SpendingScreen
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.util.SafIO
import com.gatcha.log.ui.components.GlassBackground
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgStatusToast
import com.gatcha.log.ui.components.NoteSkeletonRow
import com.gatcha.log.ui.components.InfoColumn
import com.gatcha.log.ui.components.BudgetDialog
import com.gatcha.log.ui.components.BottomNavBar
import com.gatcha.log.ui.theme.*
import com.gatcha.log.util.num

/**
 * 지출 에디터 페이지의 대상. 홀더 자체의 존재(null 아님)가 곧 "에디터가 열려 있다"이고,
 * [spending] 이 null 이면 신규 추가, 있으면 그 지출의 수정이다.
 *
 * Spending? 를 그대로 상태로 쓰지 않는 이유: null 이 "닫힘"과 "신규 추가" 두 가지를 뜻하게 되어 구분이 안 된다.
 */
private data class SpendingEditorTarget(val spending: Spending?)

@Composable
fun HomeScreen(viewModel: SpendingViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }

    /**
     * 지출 추가/수정 에디터 — **표시 여부와 대상을 한 상태로 합쳤다**(null = 닫힘).
     *
     * 예전엔 showAddSpendingSheet(Boolean) + spendingToEdit(Spending?) 두 개였다. 닫을 때 둘 다 지우는데,
     * AnimatedContent 는 퇴장 애니메이션 동안 나가는 페이지를 계속 컴포즈한다 — 그 페이지가 null 이 된
     * spendingToEdit 를 읽어 **'수정'에서 '추가'로 뒤바뀌며 빈 폼이 번쩍였다**.
     * 대상을 AnimatedContent 의 targetState 로 올리면, 나가는 페이지는 자기 대상(수정)을 그대로 들고 나간다.
     */
    val spendingEditor = remember { mutableStateOf<SpendingEditorTarget?>(null) }
    val accent = LocalAccent.current

    // 알림 딥링크 — VM 이 요청한 탭으로 이동(예: 공지 알림 탭 → 게임 정보). 상세 진입은 그 탭이 이어받는다.
    val pendingTab by viewModel.pendingTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTab) {
        pendingTab?.let { selectedTab = it; viewModel.consumePendingTab() }
    }

    // 풀스크린 하위 페이지(알림 상세·연간 리포트·지출 상세·HoYoLAB 연동·설정)가 열렸는지.
    // 열려 있으면 하단바와 FAB를 숨긴다. 각 탭 콘텐츠가 자신의 하위 페이지 상태를 보고한다.
    var subPageActive by remember { mutableStateOf(false) }

    // 탭별 스크롤 상태를 끌어올려, 하단바 탭 클릭 시 해당 페이지를 최상단으로 이동.
    val tabListStates = listOf(
        rememberLazyListState(), rememberLazyListState(),
        rememberLazyListState(), rememberLazyListState(),
    )
    val tabScope = rememberCoroutineScope()
    // 지출 편집 에디터는 홈 전체를 스왑(dispose)하므로, 지출 화면의 저장상태(열린 상세 id 등)를
    // 홀더로 붙잡아 에디터 닫힘·탭 복귀 시 상세 페이지가 유지되게 한다. (holder 는 HomeScreen 본문에 존치돼 스왑에도 살아남음)
    val homeStateHolder = rememberSaveableStateHolder()
    val onTabClick: (Int) -> Unit = { tab ->
        val sameTab = tab == selectedTab
        selectedTab = tab
        tabScope.launch {
            // 같은 탭 재탭 = 애니메이션 스크롤, 탭 전환 = 즉시 최상단
            if (sameTab) tabListStates[tab].animateScrollToItem(0)
            else tabListStates[tab].scrollToItem(0)
        }
    }


    // 대상 = 표시 여부. 한 번에 확정된다(null 지출 = 신규 추가).
    val openEditor: (Spending?) -> Unit = { target ->
        spendingEditor.value = SpendingEditorTarget(target)
    }

    // 앱 시작 시 1회 API 새로고침 (ennead 배너·이벤트 + HoYoLAB 노트) + 업데이트 확인.
    // ViewModel init 에서 호출하면 프로퍼티 초기화 순서 문제로 NPE 가 나므로 UI 에서 트리거.
    LaunchedEffect(Unit) {
        viewModel.refreshGameInfo()
        viewModel.checkForUpdate()
    }
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val forceUpdate by viewModel.forceUpdate.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

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
        targetState = spendingEditor.value,
        transitionSpec = { fadeIn(glgStandardSpec()) togetherWith fadeOut(glgShortSpec()) },
        label = "rootPage",
    ) { editorTarget ->
        if (editorTarget != null) {
            // 등장 0→1 / 퇴장 1→0. 스케일(0.35→1, FAB 우하단 피벗) + 모서리 라운드(32→0dp)로 morph.
            val morph by transition.animateFloat(
                transitionSpec = { glgEmphasisSpec() },
                label = "fabMorph",
            ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 퇴장 중인 페이지는 아래로 내린다 — AnimatedContent 는 전환 동안 두 화면을 함께
                    // 합성하므로, 닫히는 편집 페이지가 위에 남아 있으면 그 사이 눌린 '+' 탭을 가로챈다
                    // (지출 추가 버튼이 간헐적으로 안 먹던 원인 중 하나).
                    .zIndex(if (editorTarget == spendingEditor.value) 1f else 0f)
                    .graphicsLayer {
                        val s = 0.35f + 0.65f * morph
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.9f, 0.95f)
                        clip = true
                        shape = RoundedCornerShape((32f * (1f - morph)).dp)
                    },
            ) {
                // 이 페이지의 대상은 editorTarget 으로 고정 — 상태(spendingEditor)를 다시 읽지 않는다.
                // 다시 읽으면 닫히는 순간 null 이 되어 퇴장 애니메이션 중에 '추가' 폼으로 뒤바뀐다.
                val editing = editorTarget.spending
                AddSpendingModal(
                    spendingToEdit = editing,
                    nudgeMessage = { game, amount -> viewModel.overspendNudge(game, amount, editing?.id) },
                    onDismiss = { spendingEditor.value = null },
                    onSave = { spending ->
                        if (editing == null) viewModel.addSpending(spending)
                        else viewModel.updateSpending(spending)
                        spendingEditor.value = null
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
                        enter = slideInVertically(glgStandardSpec()) { it } + fadeIn(glgStandardSpec()),
                        exit = slideOutVertically(glgStandardSpec()) { it } + fadeOut(glgShortSpec()),
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
                    // 홈 탭 히어로 그라데이션 — 상태바 뒤(edge-to-edge)까지 채우는 '고정' 배경. 홈에서만.
                    // 상단 인셋 바깥(패딩 Box 이전)에 그려야 상태바 뒤까지 확장된다.
                    if (selectedTab == 0) {
                        HeroGradientBackground(
                            Modifier.fillMaxWidth()
                                .height(paddingValues.calculateTopPadding() + 262.dp)
                                .align(Alignment.TopCenter),
                        )
                    }
                    // 전 화면 edge-to-edge(상단 인셋 없음). 각 화면이 자기 상단 인셋을 소유한다 —
                    // 메인 탭 헤더/하위 페이지 헤더 모두 statusBarsPadding 으로 직접 처리(전환 중 레이아웃
                    // 점프 방지). 리스트는 상태바 뒤로 스크롤된다.
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = selectedTab,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                // 탭 인덱스 방향에 따라 좌/우로 슬라이드 + 페이드
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(glgStandardSpec()) { w -> dir * w / 4 } + fadeIn(glgStandardSpec())) togetherWith
                                    (slideOutHorizontally(glgStandardSpec()) { w -> -dir * w / 4 } + fadeOut(glgShortSpec()))
                            },
                            label = "tab",
                        ) { tab ->
                            when (tab) {
                                0 -> HomeContent(
                                    viewModel,
                                    onNavigateToGameInfo = { onTabClick(2) },
                                    onNavigateToMyPage = { onTabClick(3) },
                                    onNavigateToSpending = { onTabClick(1) },
                                    listState = tabListStates[0],
                                    onSubPageChange = { subPageActive = it },
                                )
                                1 -> homeStateHolder.SaveableStateProvider("spendingTab") {
                                    SpendingScreen(viewModel, onEditSpending = { openEditor(it) }, listState = tabListStates[1], onSubPageChange = { subPageActive = it })
                                }
                                2 -> GameInfoScreen(viewModel, listState = tabListStates[2], onSubPageChange = { subPageActive = it })
                                3 -> MyPageScreen(viewModel, listState = tabListStates[3], onSubPageChange = { subPageActive = it })
                            }
                        }

                        updateInfo?.let { info ->
                            UpdateDialog(
                                info = info,
                                onDownload = { viewModel.startInAppUpdate() },
                                onDismiss = { viewModel.dismissUpdate() },
                                force = forceUpdate,
                            )
                        }

                        // 인앱 업데이트 다운로드 진행 오버레이
                        updateProgress?.let { p -> UpdateProgressOverlay(p) }

                        // 로그아웃 진행 오버레이 — 네트워크 대기 동안 피드백이 없던 문제
                        if (signingOut) SignOutOverlay()

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
    onNavigateToSpending: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onSubPageChange: (Boolean) -> Unit = {},
) {
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val gameBudgets by viewModel.gameBudgets.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val attendanceToday by viewModel.attendanceToday.collectAsStateWithLifecycle()
    val banners by viewModel.activeBanners.collectAsStateWithLifecycle()
    val liveNotes by viewModel.liveNotes.collectAsStateWithLifecycle()
    val hoyolab by viewModel.hoyolabConfig.collectAsStateWithLifecycle()
    val checkingIn by viewModel.checkingIn.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val attendanceStreak by viewModel.attendanceStreak.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val gachaStats by viewModel.gachaStats.collectAsStateWithLifecycle()
    val homeCards by viewModel.homeCards.collectAsStateWithLifecycle()
    val savingsPlans by viewModel.savingsPlans.collectAsStateWithLifecycle()
    val challenge by viewModel.challenge.collectAsStateWithLifecycle()
    val gameInfoReady by viewModel.gameInfoReady.collectAsStateWithLifecycle()
    val hoyoTokenExpired by viewModel.hoyoTokenExpired.collectAsStateWithLifecycle()
    val gameEvents by viewModel.gameEvents.collectAsStateWithLifecycle()
    val gameChallenges by viewModel.challenges.collectAsStateWithLifecycle()
    val gameNews by viewModel.gameNews.collectAsStateWithLifecycle()
    val anniversaries = remember { com.gatcha.log.data.GameAnniversary.upcoming() }
    // 홈 진입·복귀 시 워커가 백그라운드에서 바꾼 플래그를 다시 읽어 배너에 반영.
    LaunchedEffect(Unit) { viewModel.refreshHoyoTokenExpired() }

    val monthlyTotal = remember(spendings) { viewModel.monthlyTotal() }
    val prevTotal = remember(spendings) { viewModel.prevMonthTotal() }
    // 헤더 닉네임 — 게스트/빈 값 폴백
    val nickname = if (account.isGuest) "게스트" else profile.name.ifBlank { "회원" }

    // 가챠 기록 가져오기(홈 빠른 액션) — 가챠 효율 리포트와 동일 picker 패턴(*/* 로 SAF 회색처리 회피)
    val gachaContext = LocalContext.current
    val gachaScope = rememberCoroutineScope()
    val gachaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) gachaScope.launch { viewModel.importGachaFromContents(SafIO.readTexts(gachaContext, uris)) }
    }

    // 파생 계산은 GL_Shared HomeLogic 의 순수 함수(iOS 와 공유). remember 로 재계산 캐싱.
    // 게임별 한도 초과 게임(이번 달) — 알림센터 표시용
    val gameOverBudget = remember(spendings, gameBudgets) {
        HomeLogic.gameOverBudget(gameBudgets, viewModel.monthlyTotalsByGame())
    }

    // 절약 팁 — 상황별 실제 조언(M 카드 '절약 팁' 칩이 토스트로 노출)
    val savingTip = remember(budget, monthlyTotal, gameOverBudget) {
        HomeLogic.savingTip(budget, monthlyTotal, gameOverBudget)
    }

    // 게임별 이번 달 지출/한도 (D 섹션) — 지출 있거나 한도 설정된 게임만, 지출 내림차순
    val perGameSpend = remember(spendings, gameBudgets) {
        HomeLogic.perGameSpend(viewModel.monthlyTotalsByGame(), gameBudgets)
    }

    // 재화 임박 경보 — 85% 이상(가득 직전)인 게임 '전부'(원신뿐 아니라 스타레일·젠레스 등). 가장 찬 순.
    val resinAlerts = remember(liveNotes) { HomeLogic.resinAlerts(liveNotes) }

    // 전투 콘텐츠 시즌 마감 임박(미클리어만) — '오늘 할 일'에 편입
    val combatModes by viewModel.combat.collectAsStateWithLifecycle()
    val combatDeadlines = remember(combatModes) { HomeLogic.combatDeadlines(combatModes) }

    // 알림 계산 + 읽음(넛징)/삭제(dismiss) 상태 — 사용자가 지운 알림은 제외하고 노출
    val readAlerts by viewModel.readAlerts.collectAsStateWithLifecycle()
    val dismissedAlerts by viewModel.dismissedAlerts.collectAsStateWithLifecycle()
    val alerts = HomeLogic.buildAlerts(monthlyTotal, budget, gameOverBudget, banners, attendanceToday, "${viewModel.displayYear}-${viewModel.displayMonth}")
        .filter { it.key !in dismissedAlerts }
    val unreadCount = alerts.count { it.key !in readAlerts }

    val showNotifications = remember { mutableStateOf(false) }
    val showBudgetDialog = remember { mutableStateOf(false) }
    val showHomeEdit = remember { mutableStateOf(false) }

    // 저축 플래너·절약 챌린지 하위 화면(홈 카드 진입). 0=없음 1=플래너 2=챌린지.
    var savingsScreen by remember { mutableStateOf(0) }
    BackHandler(enabled = savingsScreen != 0) { savingsScreen = 0 }
    LaunchedEffect(savingsScreen) { onSubPageChange(savingsScreen != 0) }
    if (savingsScreen != 0) {
        when (savingsScreen) {
            1 -> SavingsPlannerScreen(viewModel) { savingsScreen = 0 }
            2 -> SavingsChallengeScreen(viewModel) { savingsScreen = 0 }
        }
        return
    }

    // 오늘 할 일 목록(대시보드 KPI '오늘 할 일' 카운트 + 카드 공용)
    val todayTasks = if (gameInfoReady) HomeLogic.resolveTodayTasks(
        pendingAttendance = HomeLogic.pendingAttendanceCount(attendanceToday),
        resins = resinAlerts,
        urgentBanner = null,  // 픽업은 '이번주 일정' 카드·게임 정보 페이지에서 확인(중복 제거)
        budget = budget,
        monthlyTotal = monthlyTotal,
        combats = combatDeadlines,
    ).toTodayItems(
        onCheckInAll = { viewModel.checkInAll() },
        onResin = { viewModel.requestGameInfoAnchor(GameInfoAnchor.NOTES); onNavigateToGameInfo() },
        onCombat = { viewModel.requestGameInfoAnchor(GameInfoAnchor.COMBAT); onNavigateToGameInfo() },
        onBanner = { viewModel.requestGameInfoAnchor(GameInfoAnchor.SCHEDULE); onNavigateToGameInfo() },
        onBudget = { showBudgetDialog.value = true },
    ) else emptyList()

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
                (slideInHorizontally(glgStandardSpec()) { w -> w } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeOut(glgShortSpec()))
            } else {
                // 홈 복귀: 오른쪽으로 슬라이드 아웃 (pop)
                (slideInHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> w } + fadeOut(glgShortSpec()))
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
                onDismiss = { viewModel.dismissAlert(it.key) },
                onDismissAll = { viewModel.dismissAlerts(alerts.map { a -> a.key }) },
            )
            return@AnimatedContent
        }

    GlgPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshGameInfo(force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(Modifier.fillMaxSize()) {
    // 히어로 그라데이션은 HomeScreen(Scaffold 레벨)에서 상태바 뒤까지 그린다.
    // 콘텐츠 로드인 스태거 — 앱 진입 후 1회만 등장(스크롤·탭 재진입 시 재애니메이션 방지). 세션 영속 집합.
    // 헤더는 투명 오버레이(아래) — 콘텐츠가 헤더 버튼 '아래로' 지나가도록 (상태바+헤더)만큼 인셋.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 상단 스크림 — 콘텐츠가 헤더(버튼) 아래로 스크롤될 때만 배경색 그라데이션으로 살짝 흐린다.
    // 최상단에선 숨겨 화면을 넓게 쓴다. (지출·게임 정보 탭과 같은 규격)
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val topScrimAlpha by animateFloatAsState(if (scrolled) 0.88f else 0f, label = "topScrim")
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = GlgTabHeaderHeight + topInset),
    ) {
        // HoYoLAB 토큰 만료 감지 시 최상단 배너.
        if (hoyoTokenExpired) {
            glgCardItem() {
                TokenExpiredBanner(onReconnect = {
                    viewModel.requestOpenHoyolabLink()
                    onNavigateToMyPage()
                })
            }
        }
        // 히어로 — 이번 달 지출 / 예산 캐러셀 (Figma Make 참고)
        glgCardItem() {
            HeroBalanceCard(monthlyTotal, prevTotal, budget) { showBudgetDialog.value = true }
            Spacer(Modifier.height(16.dp))
        }
        if (!gameInfoReady || todayTasks.isNotEmpty()) {
            glgCardItem() {
                if (!gameInfoReady) TodayTaskSkeleton(titleOutside = true)
                else TodayTaskCard(tasks = todayTasks, inProgress = checkingIn != null, titleOutside = true)
                Spacer(Modifier.height(16.dp))
            }
        }
        // 최근 지출
        glgCardItem() {
            RecentSpendCard(spendings) { onNavigateToSpending() }
            Spacer(Modifier.height(16.dp))
        }
        if (!gameInfoReady) {
            glgCardItem() {
                DashCardSkeleton(rows = 3)
                Spacer(Modifier.height(16.dp))
            }
            glgCardItem() {
                DashCardSkeleton(rows = 2)
                Spacer(Modifier.height(16.dp))
            }
        } else {
            glgCardItem() {
                DashScheduleCard(gameEvents, gameChallenges, titleOutside = true) { viewModel.requestGameInfoAnchor(GameInfoAnchor.SCHEDULE); onNavigateToGameInfo() }
                Spacer(Modifier.height(16.dp))
            }
            glgCardItem() {
                DashNewsCard(gameNews, anniversaries, titleOutside = true) { viewModel.requestGameInfoAnchor(GameInfoAnchor.NEWS); onNavigateToGameInfo() }
                Spacer(Modifier.height(16.dp))
            }
        }
        // 나를 위한 — 저축 플래너 · 절약 챌린지
        glgCardItem() {
            HomeSectionHeader("나를 위한")
            Spacer(Modifier.height(10.dp))
            PickupPlannerHomeCard(savingsPlans) { savingsScreen = 1 }
            Spacer(Modifier.height(12.dp))
        }
        glgCardItem() {
            SavingsChallengeHomeCard(challenge) { savingsScreen = 2 }
            Spacer(Modifier.height(12.dp))
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
    // 상단 스크림 — **상태바 영역만** 덮는다(헤더 버튼 줄은 그대로 투명).
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(topInset + ScrimFadeExtra)
            .graphicsLayer { alpha = topScrimAlpha }
            .background(
                Brush.verticalGradient(
                    0f to Color.White,
                    0.35f to Color.White,
                    1f to Color.Transparent,
                ),
            ),
    )
    // 헤더 오버레이 — 박스 배경 없음(투명). 콘텐츠가 이 버튼들 아래로 스크롤되어 지나간다. 상태바 인셋 적용.
    Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp)) {
        HomeHeader(
            photoUrl = account.photoUrl,
            nickname = nickname,
            isGuest = account.isGuest,
            alertCount = unreadCount,
            onBellClick = { showNotifications.value = true; viewModel.markAlertsRead(alerts.map { it.key }) },
            onSignOut = { viewModel.signOut() },
            onSignIn = { viewModel.signIn() },
        )
    }
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

// 알림 목록 산출(AlertKind/HomeAlert/buildAlerts)은 GL_Shared HomeLogic 으로 이관 — iOS 와 단일 소스.
// 아이콘·색·이동 동작 매핑만 아래 NotificationCard 에 남는다.
// (시간대별 인사말 greetingForNow 는 양 플랫폼 모두 호출부가 없어 함께 제거)

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

/** 알림 상세 페이지 (홈) — 액션형: 알림 탭 시 관련 화면으로 이동. 각 알림은 삭제(X) 가능. */
@Composable
private fun NotificationDetailScreen(
    alerts: List<HomeAlert>,
    onBack: () -> Unit,
    onBudget: () -> Unit,
    onGameInfo: () -> Unit,
    onDismiss: (HomeAlert) -> Unit,
    onDismissAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
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
            // 우측 정렬 '모두 지우기' — 한 번에 전체 dismiss
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "모두 지우기",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDismissAll() }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            LazyColumn(
                // 하단바 미노출 페이지 — 시스템 네비 인셋만 확보
                modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(alerts, key = { it.key }) { alert ->
                    NotificationCard(
                        alert,
                        onClick = {
                            when (alert.kind) {
                                HomeAlertKind.BUDGET_OVER, HomeAlertKind.BUDGET_NEAR, HomeAlertKind.BUDGET_GAME_OVER -> onBudget()
                                HomeAlertKind.BANNER, HomeAlertKind.ATTENDANCE -> onGameInfo()
                            }
                        },
                        onDismiss = { onDismiss(alert) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationCard(alert: HomeAlert, onClick: () -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    // 종류별 아이콘·색·이동 안내문
    val icon: ImageVector; val tint: Color; val hint: String
    when (alert.kind) {
        HomeAlertKind.BUDGET_OVER -> { icon = Icons.Default.Savings; tint = DangerText; hint = "예산 설정하기" }
        HomeAlertKind.BUDGET_NEAR -> { icon = Icons.Default.Savings; tint = WarningText; hint = "예산 설정하기" }
        HomeAlertKind.BUDGET_GAME_OVER -> { icon = Icons.Default.Savings; tint = DangerText; hint = "예산 설정하기" }
        HomeAlertKind.BANNER -> { icon = Icons.Default.Bolt; tint = accent; hint = "게임 정보 보기" }
        HomeAlertKind.ATTENDANCE -> { icon = Icons.Default.CheckCircleOutline; tint = accent; hint = "출석하러 가기" }
    }
    GlassCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
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
            // 삭제(X) — 탭하면 이 알림만 지움(다시 안 뜸). 행 클릭(이동)과 분리.
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "알림 삭제", tint = Color.LightGray, modifier = Modifier.size(18.dp))
            }
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