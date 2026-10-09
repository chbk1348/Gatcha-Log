package com.gatcha.log.ui.home

import com.gatcha.log.ui.components.GldsSection
import com.gatcha.log.ui.components.GldsHairline
import com.gatcha.log.ui.components.GldsBand
import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsSize
import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.gatcha.log.ui.components.GlgTabHeaderHeight
import com.gatcha.log.ui.components.glgTabContentBottom
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.gatcha.log.data.HomeAlert
import com.gatcha.log.data.HomeAlertKind
import com.gatcha.log.data.HomeLogic
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaReport
import com.gatcha.log.data.GachaStats
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.game.GameInfoScreen
import com.gatcha.log.ui.game.GiHairline
import com.gatcha.log.ui.game.HoyolandDetailPage
import com.gatcha.log.ui.game.rememberFeaturedHoyoland
import com.gatcha.log.ui.profile.MyPageScreen
import com.gatcha.log.ui.spending.AddSpendingModal
import com.gatcha.log.data.GameInfoAnchor
import com.gatcha.log.ui.spending.SpendingScreen
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.util.SafIO
import com.gatcha.log.util.openUrl
import com.gatcha.log.data.api.AppNotice
import com.gatcha.log.data.api.AppNoticeApi
import com.gatcha.log.data.api.AppNoticeLevel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gatcha.log.ui.components.GlassBackground
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgStatusToast
import com.gatcha.log.ui.components.NoteSkeletonRow
import com.gatcha.log.ui.components.InfoColumn
import com.gatcha.log.ui.profile.BudgetScreen
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

// Scaffold 의 안쪽 여백(paddingValues)은 일부러 쓰지 않는다 — 전 화면 edge-to-edge 라 각 화면이 자기 인셋을 갖고,
// 하단바는 콘텐츠 위에 떠 있다. 마지막 쓰임(홈 히어로 높이)은 10/1 흰 바탕 개편 때 사라졌다.
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
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

    // 앱 시작 시 1회 API 새로고침 (ennead 배너·이벤트 + HoYoLAB 노트). 업데이트 확인은 앱 루트(MainActivity)가 한다.
    // ViewModel init 에서 호출하면 프로퍼티 초기화 순서 문제로 NPE 가 나므로 UI 에서 트리거.
    LaunchedEffect(Unit) {
        viewModel.refreshGameInfo()
    }
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

    // 지출 추가 화면의 스마트 기본값·'자주 사는 것' 근거. 편집 페이지에서만 쓰인다.
    val editorSpendings by viewModel.spendings.collectAsStateWithLifecycle()

    // 루트 레벨 페이지 스왑 — 지출 추가/수정은 별도 페이지로 운영(바텀시트가 아닌 실제 페이지 전환).
    //
    // 전환은 **앱 표준 하위 페이지 전환**(우→좌 슬라이드)을 쓴다. 예전엔 FAB 에서 스케일·모서리가
    // 펴지는 컨테이너 트랜스폼 morph 였는데, 설정·게임정보 등 다른 하위 페이지와 달라
    // 같은 앱 안에서 페이지 여는 방식이 두 가지로 보였다.
    AnimatedContent(
        targetState = spendingEditor.value,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(glgStandardSpec()) { it } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { -it / 4 } + fadeOut(glgShortSpec()))
            } else {
                (slideInHorizontally(glgStandardSpec()) { -it / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { it } + fadeOut(glgShortSpec()))
            }
        },
        label = "rootPage",
    ) { editorTarget ->
        if (editorTarget != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 퇴장 중인 페이지는 아래로 내린다 — AnimatedContent 는 전환 동안 두 화면을 함께
                    // 합성하므로, 닫히는 편집 페이지가 위에 남아 있으면 그 사이 눌린 '+' 탭을 가로챈다
                    // (지출 추가 버튼이 간헐적으로 안 먹던 원인 중 하나).
                    .zIndex(if (editorTarget == spendingEditor.value) 1f else 0f),
            ) {
                // 이 페이지의 대상은 editorTarget 으로 고정 — 상태(spendingEditor)를 다시 읽지 않는다.
                // 다시 읽으면 닫히는 순간 null 이 되어 퇴장 애니메이션 중에 '추가' 폼으로 뒤바뀐다.
                val editing = editorTarget.spending
                AddSpendingModal(
                    spendingToEdit = editing,
                    // 스마트 기본값·'자주 사는 것'의 근거.
                    recentSpendings = editorSpendings,
                    nudgeMessage = { game, amount -> viewModel.overspendNudge(game, amount, editing?.id) },
                    myGames = viewModel.myGames.collectAsStateWithLifecycle().value,
                    onDismiss = { spendingEditor.value = null },
                    onSave = { spending ->
                        // 저장이 거절되면(금액 상한 등) 닫지 않는다 — 예전엔 닫혀서 입력이 통째로 사라졌다.
                        val ok = if (editing == null) viewModel.addSpending(spending)
                        else viewModel.updateSpending(spending)
                        if (ok) spendingEditor.value = null
                        ok
                    },
                )
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    // 하위 페이지(연간 리포트·알림 상세 등)에서는 하단바·FAB를 아래로 슬라이드해 숨김.
                    //
                    // **돌아올 때만 물방울처럼 튄다** — 아래에서 올라와 살짝 넘쳤다가 자리를 잡는다.
                    // 곧은 tween 은 목표에 닿으면 그냥 멈춰서 "제자리로 돌아왔다"가 잘 안 읽힌다.
                    // 퇴장은 스프링을 쓰지 않는다(나가면서 튀면 화면이 산만하다).
                    AnimatedVisibility(
                        visible = !subPageActive,
                        enter = slideInVertically(glgReturnSpring()) { it } +
                            scaleIn(glgReturnSpring(), initialScale = 0.90f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                            fadeIn(glgStandardSpec()),
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
                    // 홈 탭은 흰 바탕(10/1) — 히어로 뒤 강조색 그라데이션을 걷었다. 하위 페이지는 각자 바탕을 둔다.
                    if (selectedTab == 0 && !subPageActive) {
                        Box(Modifier.matchParentSize().background(Color.White))
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

/**
 * 홈이 띄우는 하위 화면. 전환은 [HomeContent] 의 `AnimatedContent` 한 곳이 맡는다.
 *
 * 게임정보 탭의 `GiSub` 처럼 깊이를 나누지 않는다 — 셋 다 홈 바로 아래 한 층이고, 서로
 * 오갈 수 없다(하위에서 나가는 길은 홈뿐). push/pop 판정에 `Home` 인지만 보면 된다.
 */
/** 홈 하위 페이지 — [depth] 로 들어가기(push) · 나가기(pop) 방향을 가른다. 예산은 알림에서 열면 알림의 하위(깊이 2). */
private enum class HomeSub(val depth: Int) { Home(0), Notifications(1), Hoyoland(1), Budget(2) }

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
    val myGames by viewModel.myGames.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val attendanceToday by viewModel.attendanceToday.collectAsStateWithLifecycle()
    // 출석을 세는 게임 — UID 없는 게임까지 세면 매일 '1개 남음' 알림이 남는다.
    val attendanceGames by viewModel.trackedAttendanceGames.collectAsStateWithLifecycle()
    val banners by viewModel.activeBanners.collectAsStateWithLifecycle()
    val liveNotes by viewModel.liveNotes.collectAsStateWithLifecycle()
    val hoyolab by viewModel.hoyolabConfig.collectAsStateWithLifecycle()
    val checkingIn by viewModel.checkingIn.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val attendanceStreak by viewModel.attendanceStreak.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val gachaStats by viewModel.gachaStats.collectAsStateWithLifecycle()
    val gameInfoReady by viewModel.gameInfoReady.collectAsStateWithLifecycle()
    // 일정·소식은 출처가 달라 게이트도 따로다 — 배너·노트가 캐시로 즉시 차도 이 둘은 아직 로딩일 수 있다.
    val scheduleReady by viewModel.scheduleReady.collectAsStateWithLifecycle()
    val newsReady by viewModel.newsReady.collectAsStateWithLifecycle()
    // 호요랜드 — 개막이 가까울 때만 값이 있다(평소 null → 카드 자체가 안 그려진다).
    // 게임정보 탭과 같은 로더를 타므로 어느 탭을 먼저 켜든 같은 값을 본다.
    val featuredHoyoland = rememberFeaturedHoyoland()
    val notices = rememberAppNotices()
    val hoyoTokenExpired by viewModel.hoyoTokenExpired.collectAsStateWithLifecycle()
    val gameEvents by viewModel.gameEvents.collectAsStateWithLifecycle()
    val gameChallenges by viewModel.challenges.collectAsStateWithLifecycle()
    val gameNews by viewModel.gameNews.collectAsStateWithLifecycle()
    val anniversaries = remember { com.gatcha.log.data.GameAnniversary.upcoming() }
    // 홈 진입·복귀 시 워커가 백그라운드에서 바꾼 플래그를 다시 읽어 배너에 반영.
    LaunchedEffect(Unit) { viewModel.refreshHoyoTokenExpired() }

    // VM 이 지출 변경·포그라운드 복귀(달 바뀜)마다 한 번 계산해 둔 값을 받는다 — remember(spendings) 로
    // 직접 계산하면 지출이 안 바뀐 채 달이 넘어가도 지난달 합계가 남았고, 그릴 때마다 전체를 훑었다.
    val monthlyTotal by viewModel.currentMonthTotal.collectAsStateWithLifecycle()
    val prevTotal by viewModel.previousMonthTotal.collectAsStateWithLifecycle()
    // 헤더 닉네임 — 게스트/빈 값 폴백
    val nickname = if (account.isGuest) "게스트" else profile.name.ifBlank { "회원" }

    // 파생 계산은 GL_Shared HomeLogic 의 순수 함수(iOS 와 공유). remember 로 재계산 캐싱.
    //
    // 게임별 이번 달 합계는 **공유 VM 이 이미 한 번의 순회로 만들어 둔다**(SpendingDerived.compute).
    // 예전엔 여기서 `viewModel.monthlyTotalsByGame()` 을 두 번 불러(아래 perGameSpend 까지)
    // 지출이 바뀔 때마다 전체를 두 번 더 훑었다. 값은 완전히 동일하다 — 같은 게임키 파생·같은 월 필터.
    val monthlyTotalsByGame by viewModel.currentMonthTotalsByGame.collectAsStateWithLifecycle()

    // 게임별 한도 초과 게임(이번 달) — 알림센터 표시용
    val gameOverBudget = remember(monthlyTotalsByGame, gameBudgets) {
        HomeLogic.gameOverBudget(gameBudgets, monthlyTotalsByGame)
    }

    // 절약 팁 — 상황별 실제 조언(M 카드 '절약 팁' 칩이 토스트로 노출)
    val savingTip = remember(budget, monthlyTotal, gameOverBudget) {
        HomeLogic.savingTip(budget, monthlyTotal, gameOverBudget)
    }

    // 게임별 이번 달 지출/한도 (D 섹션) — 지출 있거나 한도 설정된 게임만, 지출 내림차순
    val perGameSpend = remember(monthlyTotalsByGame, gameBudgets) {
        HomeLogic.perGameSpend(monthlyTotalsByGame, gameBudgets)
    }

    // 재화 임박 경보 — 85% 이상(가득 직전)인 게임 '전부'(원신뿐 아니라 스타레일·젠레스 등). 가장 찬 순.
    val resinAlerts = remember(liveNotes) { HomeLogic.resinAlerts(liveNotes) }

    // 전투 콘텐츠 시즌 마감 임박(미클리어만) — '오늘 할 일'에 편입
    val combatModes by viewModel.combat.collectAsStateWithLifecycle()
    val combatDeadlines = remember(combatModes) { HomeLogic.combatDeadlines(combatModes) }

    // 알림 계산 + 읽음(넛징)/삭제(dismiss) 상태 — 사용자가 지운 알림은 제외하고 노출
    val readAlerts by viewModel.readAlerts.collectAsStateWithLifecycle()
    val dismissedAlerts by viewModel.dismissedAlerts.collectAsStateWithLifecycle()
    // 이웃한 파생값은 전부 remember 로 감싸여 있는데 이 둘만 빠져 있었다 —
    // 홈은 스크롤·애니메이션·플로우 방출로 재구성이 잦아 그때마다 알림 전량을 다시 만들었다.
    // monthKey 는 게터 2개(시계 읽기 + 날짜 변환)라 람다 밖으로 뺀다.
    val monthKey = "${viewModel.displayYear}-${viewModel.displayMonth}"
    val alerts = remember(monthlyTotal, budget, gameOverBudget, banners, attendanceToday, attendanceGames, monthKey, dismissedAlerts) {
        HomeLogic.buildAlerts(monthlyTotal, budget, gameOverBudget, banners, attendanceToday, monthKey, attendanceGames = attendanceGames)
            .filter { it.key !in dismissedAlerts }
    }
    val unreadCount = remember(alerts, readAlerts) { alerts.count { it.key !in readAlerts } }

    val showNotifications = remember { mutableStateOf(false) }
    val showBudget = remember { mutableStateOf(false) }

    // 호요랜드 상세 — **홈에서 바로 연다.** 예전엔 게임정보 탭으로 옮긴 뒤 그 탭의 앵커가
    // 상세를 열어, 한 번 탭에 화면이 두 번 바뀌었다(탭 전환이 눈에 보였다).
    var showHoyoland by remember { mutableStateOf(false) }

    /**
     * 홈의 하위 화면은 **하나의 [AnimatedContent] 가 전부 맡는다.**
     *
     * 알림 상세만 애니메이션 컨테이너에 들어 있고, 저축 플래너·절약 챌린지는 `if … return` 으로
     * 컴포지션을 갈아끼워 0프레임 컷으로 튀었다. 다른 탭(게임정보 `GiSub`·지출·설정·마이페이지)이
     * 전부 같은 push/pop 슬라이드를 쓰는데 홈의 이 두 장만 결이 달랐다.
     *
     * 상태를 하나로 모은 건 [onSubPageChange] 때문이기도 하다. 예전엔 `savingsScreen` 과
     * `showNotifications` 가 각자 `LaunchedEffect` 로 같은 콜백에 서로 다른 값을 밀어 하단바·FAB
     * 표시가 순서에 좌우됐다. 지금은 파생값 하나가 단일 진실이다.
     */
    val homeSub = when {
        showHoyoland -> HomeSub.Hoyoland
        showBudget.value -> HomeSub.Budget
        showNotifications.value -> HomeSub.Notifications
        else -> HomeSub.Home
    }

    // 오늘 할 일 목록(대시보드 KPI '오늘 할 일' 카운트 + 카드 공용)
    //
    // 계산(공유 로직)과 UI 매핑을 나눈다. 계산은 입력이 바뀔 때만 — 예전엔 재구성마다 돌았다.
    // 매핑(toTodayItems)은 remember 에 넣지 않는다: 콜백이 상위에서 새로 만들어질 수 있어
    // 캐시하면 낡은 람다를 붙들게 된다. 항목이 몇 개뿐이라 매핑 자체는 싸다.
    val todayRaw = remember(gameInfoReady, attendanceToday, attendanceGames, resinAlerts, budget, monthlyTotal, combatDeadlines) {
        if (gameInfoReady) {
            HomeLogic.resolveTodayTasks(
                pendingAttendance = HomeLogic.pendingAttendanceCount(attendanceToday, attendanceGames),
                resins = resinAlerts,
                urgentBanner = null,  // 픽업은 '이번주 일정' 카드·게임 정보 페이지에서 확인(중복 제거)
                budget = budget,
                monthlyTotal = monthlyTotal,
                combats = combatDeadlines,
            )
        } else {
            emptyList()
        }
    }
    val todayTasks = todayRaw.toTodayItems(
        onCheckInAll = { viewModel.checkInAll() },
        onResin = { viewModel.requestGameInfoAnchor(GameInfoAnchor.NOTES); onNavigateToGameInfo() },
        onCombat = { viewModel.requestGameInfoAnchor(GameInfoAnchor.COMBAT); onNavigateToGameInfo() },
        onBanner = { viewModel.requestGameInfoAnchor(GameInfoAnchor.SCHEDULE); onNavigateToGameInfo() },
        onBudget = { showBudget.value = true },
    )

    // 알림 상세 페이지에서 시스템 뒤로가기 시 홈으로 복귀
    BackHandler(enabled = showNotifications.value) { showNotifications.value = false }
    BackHandler(enabled = showBudget.value) { showBudget.value = false }
    // 하위 화면이 열리면 상위(Scaffold)에 알려 하단바·FAB를 숨김 — 저축·챌린지·알림 공통.
    LaunchedEffect(homeSub) { onSubPageChange(homeSub != HomeSub.Home) }

    AnimatedContent(
        targetState = homeSub,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState.depth > initialState.depth) {
                // 더 깊은 화면 열기: 오른쪽에서 슬라이드 인 (push)
                (slideInHorizontally(glgStandardSpec()) { w -> w } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeOut(glgShortSpec()))
            } else {
                // 상위로 복귀: 오른쪽으로 슬라이드 아웃 (pop) — 예산 → 알림 · 알림 → 홈
                (slideInHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> w } + fadeOut(glgShortSpec()))
            }
        },
        label = "homeSub",
    ) { sub ->
        when (sub) {
            HomeSub.Hoyoland -> {
                HoyolandDetailPage(viewModel, onBack = { showHoyoland = false })
                return@AnimatedContent
            }
            HomeSub.Notifications -> {
                NotificationDetailScreen(
                    alerts = alerts,
                    onBack = { showNotifications.value = false },
                    // 알림을 닫지 않는다 — 예산은 알림의 하위 페이지, 뒤로 가면 알림으로 돌아온다.
                    onBudget = { showBudget.value = true },
                    onGameInfo = { showNotifications.value = false; onNavigateToGameInfo() },
                    onDismiss = { viewModel.dismissAlert(it.key) },
                    onDismissAll = { viewModel.dismissAlerts(alerts.map { a -> a.key }) },
                )
                return@AnimatedContent
            }
            // 예산 — 설정 ▸ 예산 관리와 같은 페이지(9/30). 옛 BudgetDialog 는 폐기.
            HomeSub.Budget -> {
                // 홈 · 알림 어디서 열었든 닫으면 한 단계 위로(알림에서 열었으면 알림이 그대로 남아 있다).
                BudgetScreen(
                    overall = budget, gameBudgets = gameBudgets, monthlyTotals = monthlyTotalsByGame, myGames = myGames,
                    onSave = { o, perGame -> viewModel.setBudgets(o, perGame); showBudget.value = false },
                    onBack = { showBudget.value = false },
                )
                return@AnimatedContent
            }
            HomeSub.Home -> Unit
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
        // 카드 없이 화면 폭 섹션 — 묶음 사이 10 띠 · 섹션 사이 헤어라인(홈 3.0). 좌우 여백은 섹션마다 준다.
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = GlgTabHeaderHeight + topInset, bottom = glgTabContentBottom()),
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
        // 운영 공지 — 어드민이 올린 것. 기간이 지나면 스스로 빠진다(AppNoticeApi).
        notices.forEach { notice ->
            glgCardItem() { AppNoticeBanner(notice) }
        }
        // 홈 3.0(10/1) — 「지출」 묶음 → 10 띠 → 「게임」 묶음. 묶음 안 섹션 사이는 좌우 20 헤어라인.
        // ── 지출 ──
        glgCardItem() {
            HomeGroupHeader("지출", "${DateUtil.month(System.currentTimeMillis())}월", top = 4.dp)
            HomeSection { MonthSpendSection(monthlyTotal, prevTotal, budget) { showBudget.value = true } }
            HomeSectionLine()
            HomeSection(bottom = 8.dp) { RecentSpendCard(spendings) { onNavigateToSpending() } }
            HomeBand()
        }
        // ── 게임 ──
        glgCardItem() { HomeGroupHeader("게임", "오늘 · 이번 주", top = 26.dp) }
        // 호요랜드 — 개막 D-60 이내에만 끼어드는 한시 카드(끝나면 스스로 빠진다). 위 18 · 양옆 12 · 아래 4.
        featuredHoyoland?.let { hoyoland ->
            glgCardItem() {
                Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 4.dp)) { DashHoyolandCard(hoyoland) { showHoyoland = true } }
            }
        }
        // 헤어라인은 앞에 섹션이 있을 때만 — 묶음 머리 · 입장권 바로 다음엔 긋지 않는다(빠진 섹션이 선을 남기지 않게).
        val showToday = !gameInfoReady || todayTasks.isNotEmpty()
        if (showToday) {
            glgCardItem() {
                HomeSection(bottom = 8.dp) {
                    if (!gameInfoReady) TodayTaskSkeleton()
                    else TodayTaskCard(tasks = todayTasks, inProgress = checkingIn != null)
                }
            }
        }
        // 카드마다 자기 데이터가 올 때까지 스켈레톤 — 예전엔 gameInfoReady 하나로 묶여 있어서, 배너·노트가
        // 캐시로 즉시 차면 스켈레톤이 걷히고 이 두 카드만 한동안 자리를 비웠다가 뒤늦게 튀어나왔다.
        glgCardItem() {
            if (showToday) HomeSectionLine()
            HomeSection(bottom = 8.dp) {
                if (!scheduleReady) DashCardSkeleton(rows = 3)
                else DashScheduleCard(gameEvents, gameChallenges) { viewModel.requestGameInfoAnchor(GameInfoAnchor.SCHEDULE); onNavigateToGameInfo() }
            }
        }
        // 절약 챌린지는 **마이페이지**로 옮겼다(27.50.0). 그래서 **소식이 마지막**이다 —
        // 탭바까지의 간격은 contentPadding(glgTabContentBottom)이 전담한다.
        // 소식이 하나도 없으면 DashNewsCard 가 아무것도 안 그리므로 섹션 · 헤어라인째 뺀다.
        if (!newsReady || gameNews.isNotEmpty() || anniversaries.any { it.daysUntil <= 60 }) {
            glgCardItem() {
                HomeSectionLine()
                HomeSection(bottom = 8.dp) {
                    if (!newsReady) DashCardSkeleton(rows = 2)
                    else DashNewsCard(gameNews, anniversaries) { viewModel.requestGameInfoAnchor(GameInfoAnchor.NEWS); onNavigateToGameInfo() }
                }
            }
        }
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


}

// 알림 목록 산출(AlertKind/HomeAlert/buildAlerts)은 GL_Shared HomeLogic 으로 이관 — iOS 와 단일 소스.
// 아이콘·색·이동 동작 매핑만 아래 NotificationRow 에 남는다.
// (시간대별 인사말 greetingForNow 는 양 플랫폼 모두 호출부가 없어 함께 제거)

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
    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    // GLDS 2.0(10/6) — 흰 바탕, 알림 하나 = 헤어라인으로 나눈 한 줄(카드 없음). iOS NotificationDetailView 와 같다.
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().padding(top = glgDetailContentTop())) {
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
                // 글자 끝이 섹션 좌우 20 에 맞게 12(+ 글자 안쪽 8). 헤더 바로 밑이라 위 8.
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
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
                state = listState,
                // 하단바 미노출 페이지 — 시스템 네비 인셋만 확보
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                // 줄이 위아래 12 를 스스로 가져 위 10 · 아래 8 → 눈에 22 · 20(GLDS 2.0 섹션 규격).
                contentPadding = PaddingValues(top = 10.dp, bottom = 8.dp),
            ) {
                itemsIndexed(alerts, key = { _, a -> a.key }) { i, alert ->
                    if (i > 0) GiHairline(Modifier.padding(horizontal = 20.dp))
                    NotificationRow(
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
            }
        }
        }
        GlgDetailHeaderOverlay("알림", onBack, scrolled)
    }
}

@Composable
private fun NotificationRow(alert: HomeAlert, onClick: () -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    // 종류별 아이콘·색·이동 안내문
    val icon: ImageVector; val tint: Color; val hint: String
    when (alert.kind) {
        HomeAlertKind.BUDGET_OVER -> { icon = Icons.Default.Savings; tint = Urgent; hint = "예산 설정하기" }
        HomeAlertKind.BUDGET_NEAR -> { icon = Icons.Default.Savings; tint = WarningText; hint = "예산 설정하기" }
        HomeAlertKind.BUDGET_GAME_OVER -> { icon = Icons.Default.Savings; tint = Urgent; hint = "예산 설정하기" }
        HomeAlertKind.BANNER -> { icon = Icons.Default.Bolt; tint = accent; hint = "게임 정보 보기" }
        HomeAlertKind.ATTENDANCE -> { icon = Icons.Default.CheckCircleOutline; tint = accent; hint = "출석하러 가기" }
    }
    run {
        Row(
            // 카드 없이 한 줄 — 좌 20 · 위아래 12. 우측은 X 버튼(36) 안쪽 여백이 있어 12.
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
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
                Text(alert.message, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(hint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
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
    HomeTopBanner(
        icon = Icons.Default.Warning, tint = LocalAccent.current,
        title = "HoYoLAB 토큰이 만료된 것 같아요", body = "재연동하지 않으면 자동 출석이 안 돼요",
        cta = "재연동", onCta = onReconnect,
    )
}

/**
 * 운영 공지 한 건 — 만료 배너와 **같은 모양**이고 색만 무게를 따른다(안내 = 강조색 · 주의 = 경고색 · 긴급 = 급한 경고색).
 * 주소가 있으면 버튼이 붙어 브라우저로 연다. iOS `AppNoticeBanner` 와 같은 값.
 */
@Composable
private fun AppNoticeBanner(notice: AppNotice) {
    HomeTopBanner(
        icon = if (notice.level == AppNoticeLevel.INFO) Icons.Default.Info else Icons.Default.Warning,
        tint = when (notice.level) {
            AppNoticeLevel.INFO -> LocalAccent.current
            AppNoticeLevel.WARN -> WarningText
            AppNoticeLevel.URGENT -> Urgent
        },
        title = notice.title, body = notice.body,
        cta = notice.cta.takeIf { notice.url.isNotBlank() }, onCta = { openUrl(notice.url) },
    )
}

/**
 * 홈 맨 위 띠 배너 — 카드 없이 화면 폭 [tint] 10% 면(iOS `HomeTopBanner` 와 같다).
 * 아이콘 24 · 제목 14 Bold · 내용 12 회색 · 버튼은 GLDS S. 아래 8 은 다음 배너 · 묶음 머리와의 간격이다.
 */
@Composable
private fun HomeTopBanner(icon: ImageVector, tint: Color, title: String, body: String, cta: String?, onCta: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).background(tint.copy(alpha = 0.10f)).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (body.isNotBlank()) Text(body, fontSize = 12.sp, color = TextSecondary)
        }
        if (cta != null) {
            Spacer(Modifier.width(8.dp))
            GldsButton(cta, onCta, size = GldsSize.S)
        }
    }
}

/**
 * 지금 띄울 운영 공지 — 첫 프레임은 받아 둔 값으로, 화면에 돌아올 때(ON_RESUME)마다 다시 묻는다.
 *
 * 호요랜드 배너(`rememberHoyolandEvent`)와 같은 이유다. 홈은 앱을 켜 두는 내내 composition 에
 * 남아 있어 한 번만 물으면 어드민에서 공지를 올려도 재실행 전까지 안 뜬다. 매번 네트워크를 타지는
 * 않는다 — [AppNoticeApi.load] 가 15초 캐시로 막는다.
 */
@Composable
private fun rememberAppNotices(): List<AppNotice> {
    var notices by remember { mutableStateOf(AppNoticeApi.current) }
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) { notices = AppNoticeApi.load() }
    return notices
}

/**
 * 홈 섹션(홈 3.0) — 좌우 20 · 위 18 · 아래 [bottom].
 * [bottom] 은 20 − 마지막 요소의 자체 아래 여백(목록 줄은 vertical 12 라 8) — 보이는 끝 → 구분선 간격을 20 으로 맞춘다.
 */
@Composable
private fun HomeSection(bottom: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    GldsSection(Modifier.background(Color.White), top = 18.dp, bottom = bottom, content = content)
}

/** 묶음 머리 — 이름 22 Black + 보조 12 회색. 위 [top](첫 묶음 4 · 띠 다음 26) · 좌우 20. */
@Composable
private fun HomeGroupHeader(title: String, sub: String, top: Dp) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(start = 20.dp, end = 20.dp, top = top),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
        Spacer(Modifier.width(8.dp))
        Text(sub, fontSize = 12.sp, color = TextSecondary)
    }
}

/** 묶음 안 섹션 사이 — 좌우 20 들여 쓴 1 헤어라인. */
@Composable
private fun HomeSectionLine() {
    GldsHairline(Modifier.background(Color.White), inset = 20.dp)
}

/** 묶음 사이 — 10 띠. */
@Composable
private fun HomeBand() = GldsBand()
