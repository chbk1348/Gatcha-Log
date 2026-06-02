package com.gatcha.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.auth.AccountLoadingScreen
import com.gatcha.log.ui.auth.LoginScreen
import com.gatcha.log.ui.components.GlassBackground
import com.gatcha.log.ui.components.GlgStatusToast
import com.gatcha.log.ui.components.dismissKeyboardOnTapOutside
import com.gatcha.log.ui.game.GameInfoScreen
import com.gatcha.log.ui.home.HomeContent
import com.gatcha.log.ui.profile.MyPageScreen
import com.gatcha.log.ui.spending.AddSpendingModal
import com.gatcha.log.ui.spending.SpendingScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.AccentPalette
import com.gatcha.log.ui.theme.GatchaLogTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * (폴백) 전체 Compose 앱 — Compose 리퀴드 글래스 하단바 버전.
 * 네이티브 탭바 모드(아래)가 기본이지만, 비교/디버깅용으로 유지한다.
 */
@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController { App() }

// ════════════════════════════════════════════════════════════════════════════
// 네이티브 탭바(SwiftUI TabView) 모드 — iOS 26 시스템 리퀴드 글래스 탭바 사용
//
// Swift 의 TabView 가 탭 전환·탭바 렌더링을 담당하고, 각 탭의 콘텐츠만
// Compose(공유 코드)로 채운다. 탭바 자체가 진짜 네이티브이므로 iOS 26 의
// 시스템 리퀴드 글래스(스크롤 시 축소·블러·모핑)가 자동 적용된다.
// ════════════════════════════════════════════════════════════════════════════

/** 모든 탭 VC 가 공유하는 앱 상태 (앱 수명과 동일) */
object IosAppState {
    val viewModel: SpendingViewModel by lazy { SpendingViewModel() }

    /** 지출 추가/수정 모달 대상 — 수정이면 해당 Spending, 추가면 null. (Swift 와 객체를 주고받지 않기 위한 공유 상태) */
    val spendingToEdit = MutableStateFlow<Spending?>(null)

    /**
     * 초기 동기화 로딩 화면 완료 여부 (프로세스 수명 동안 유지 — Compose 경로의 loadingDone 와 동일).
     * 게이트 활성 = !account.isGuest && !syncLoadingDone — 4개 탭(TabPage)과 Swift(탭바/추가버튼 숨김)가 공유.
     */
    val syncLoadingDone = MutableStateFlow(false)

    /**
     * 현재 선택된 탭 (Swift TabView 의 selectedTab 과 동기화).
     * 토스트는 보이는 탭에서만 컴포즈한다 — 숨겨진 탭의 컴포지션은 프레임 클럭이 멈춰
     * 토스트 상태가 지연 처리되므로, 탭 전환 시 유령 토스트가 보였다 사라지는 문제 방지.
     */
    val selectedTab = MutableStateFlow(0)
}

/** Swift 가 호출 — 탭 전환 시 현재 탭 인덱스 동기화 */
@Suppress("unused")
fun setSelectedTab(tab: Int) {
    IosAppState.selectedTab.value = tab
}

/** 초기 동기화 게이트 활성 여부 — Swift 가 탭바·추가 버튼 숨김 초기값으로 사용 */
@Suppress("unused")
fun isSyncGateActive(): Boolean =
    !IosAppState.viewModel.account.value.isGuest && !IosAppState.syncLoadingDone.value

/**
 * Swift 가 호출 — 초기 동기화 게이트 상태 변경 구독 (메인 스레드).
 * 게이트가 활성인 동안 Swift 는 네이티브 탭바와 지출 추가 버튼을 숨긴다.
 */
@Suppress("unused")
fun observeSyncGate(onChange: (Boolean) -> Unit) {
    CoroutineScope(Dispatchers.Main).launch {
        combine(IosAppState.viewModel.account, IosAppState.syncLoadingDone) { account, done ->
            !account.isGuest && !done
        }.collect { onChange(it) }
    }
}

/** Swift 가 시작 화면을 결정 — true 면 온보딩(로그인/게스트 선택)부터 */
@Suppress("unused")
fun needsOnboarding(): Boolean =
    IosAppState.viewModel.account.value.isGuest && !IosAppState.viewModel.guestChosen.value

/**
 * Swift '+' (지출 추가) 버튼이 모달을 열기 전 호출 — 이전 수정 대상이 남아있지 않게 초기화.
 * (수정 흐름은 SpendingTabViewController.onEditSpending 이 대상을 설정한 뒤 모달을 열므로 이 함수를 거치지 않는다)
 */
@Suppress("unused")
fun prepareAddSpending() {
    IosAppState.spendingToEdit.value = null
}

// ── 테마(액센트) 색상 브리지 — 네이티브 탭바 틴트를 앱 테마와 연동 ──────────────

private var accentObserver: ((Long) -> Unit)? = null
private var accentCollectorStarted = false

/** 현재 액센트 색상 → ARGB Long */
private fun currentAccentArgb(): Long {
    val idx = IosAppState.viewModel.accentIndex.value
    val accent = AccentPalette.getOrElse(idx) { AccentPalette[0] }.color
    return accent.toArgb().toLong() and 0xFFFFFFFFL
}

/**
 * Swift 가 호출 — 테마(액센트) 색상 변경 구독.
 * 등록 즉시 현재 값으로 1회 호출되고, 이후 마이페이지에서 테마를 바꿀 때마다 호출된다(메인 스레드).
 */
@Suppress("unused")
fun observeAccentColor(onChange: (Long) -> Unit) {
    accentObserver = onChange
    onChange(currentAccentArgb())
    if (!accentCollectorStarted) {
        accentCollectorStarted = true
        CoroutineScope(Dispatchers.Main).launch {
            IosAppState.viewModel.accentIndex.collect {
                accentObserver?.invoke(currentAccentArgb())
            }
        }
    }
}

/**
 * 탭 공통 래퍼: 테마 + 글래스 배경 + 상태 토스트 + 초기 동기화 게이트.
 *
 * 로그인 유저는 초기 클라우드 pull 이 끝날 때까지 [AccountLoadingScreen] 으로 UI 를 막는다
 * (Compose 경로의 App.kt 와 동일한 게이트) — pull 완료 전 로컬 편집이 디바운스 push 로
 * 아직 받지 않은 클라우드 스냅샷을 덮어쓰는 것을 방지.
 *
 * [tabIndex] 는 이 탭의 인덱스 — 토스트는 현재 보이는 탭에서만 컴포즈한다.
 */
@Composable
private fun TabPage(tabIndex: Int, content: @Composable () -> Unit) {
    val vm = IosAppState.viewModel
    val accentIndex by vm.accentIndex.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val account by vm.account.collectAsState()
    val initialSyncing by vm.initialSyncing.collectAsState()
    val loadingDone by IosAppState.syncLoadingDone.collectAsState()
    val selectedTab by IosAppState.selectedTab.collectAsState()

    GatchaLogTheme(accentIndex = accentIndex) {
        if (!account.isGuest && !loadingDone) {
            // 초기 클라우드 동기화 게이트 (0~100% 프로그레스) — 완료 상태는 IosAppState 로 공유
            // (Swift 도 이 상태를 구독해 게이트 동안 네이티브 탭바·추가 버튼을 숨긴다)
            AccountLoadingScreen(loading = initialSyncing, onFinished = { IosAppState.syncLoadingDone.value = true })
            return@GatchaLogTheme
        }

        GlassBackground(modifier = Modifier.fillMaxSize()) {
            // 콘텐츠는 상태바 아래부터 시작 (원래 Scaffold 가 주던 상단 인셋을 직접 적용).
            // 하단은 패딩 없이 — 콘텐츠가 반투명 네이티브 탭바 밑으로 비쳐 보이는 게 iOS 26 표준.
            // 입력 필드 밖 탭 → 키보드 숨김 (iOS 는 시스템 차원의 키보드 닫기 수단이 없음)
            Box(Modifier.fillMaxSize().statusBarsPadding().dismissKeyboardOnTapOutside()) {
                content()
            }

            // 상태 토스트 (저장됨·출석 완료 등) — 탭바 위에 표시.
            // 현재 보이는 탭에서만 컴포즈 — 숨겨진 탭(프레임 클럭 정지)에 토스트 상태가 남아
            // 탭 전환 시 유령 토스트가 보였다 사라지는 문제 방지.
            if (selectedTab == tabIndex) {
                GlgStatusToast(
                    message = statusMessage,
                    onConsumed = { vm.clearStatus() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 66.dp),
                )
            }
        }
    }
}

/** 온보딩(로그인/게스트 선택) 화면. 선택이 끝나면 [onComplete] 호출 → Swift 가 탭 화면으로 전환 */
@Suppress("unused", "FunctionName")
fun LoginViewController(onComplete: () -> Unit): UIViewController = ComposeUIViewController {
    val vm = IosAppState.viewModel
    val account by vm.account.collectAsState()
    val guestChosen by vm.guestChosen.collectAsState()
    val accentIndex by vm.accentIndex.collectAsState()

    // 게스트 선택 또는 로그인 완료 → 탭 화면으로
    LaunchedEffect(account, guestChosen) {
        if (!account.isGuest || guestChosen) onComplete()
    }
    GatchaLogTheme(accentIndex = accentIndex) { LoginScreen(vm) }
}

/** 홈 탭 */
@Suppress("unused", "FunctionName")
fun HomeTabViewController(
    onSwitchTab: (Int) -> Unit,
    onSubPageChange: (Boolean) -> Unit,
): UIViewController = ComposeUIViewController {
    TabPage(tabIndex = 0) {
        // 앱 시작 시 1회 API 새로고침 (ennead 배너·이벤트 + HoYoLAB 노트) —
        // Compose 경로(HomeScreen.kt)의 LaunchedEffect 와 동일한 역할.
        // iOS 네이티브 탭 경로는 HomeScreen 을 거치지 않고 HomeContent 를 직접 쓰므로 여기서 트리거.
        // (초기 동기화 게이트가 있으면 게이트 완료 후 발화 → hoyolabConfig 로드 완료 상태 보장)
        LaunchedEffect(Unit) {
            IosAppState.viewModel.refreshGameInfo()
        }
        HomeContent(
            viewModel = IosAppState.viewModel,
            onNavigateToGameInfo = { onSwitchTab(2) },
            onNavigateToMyPage = { onSwitchTab(3) },
            onSubPageChange = onSubPageChange,
        )
    }
}

/** 지출 탭 */
@Suppress("unused", "FunctionName")
fun SpendingTabViewController(
    onAddSpending: () -> Unit,
    onSubPageChange: (Boolean) -> Unit,
): UIViewController = ComposeUIViewController {
    TabPage(tabIndex = 1) {
        SpendingScreen(
            viewModel = IosAppState.viewModel,
            onEditSpending = { spending ->
                // 수정 대상은 Kotlin 쪽 공유 상태에 두고, Swift 에는 "모달 열기" 신호만 보냄
                IosAppState.spendingToEdit.value = spending
                onAddSpending()
            },
            onSubPageChange = onSubPageChange,
        )
    }
}

/** 게임 정보 탭 */
@Suppress("unused", "FunctionName")
fun GameInfoTabViewController(onSubPageChange: (Boolean) -> Unit): UIViewController = ComposeUIViewController {
    TabPage(tabIndex = 2) {
        GameInfoScreen(viewModel = IosAppState.viewModel, onSubPageChange = onSubPageChange)
    }
}

/** 마이페이지 탭 */
@Suppress("unused", "FunctionName")
fun MyPageTabViewController(onSubPageChange: (Boolean) -> Unit): UIViewController = ComposeUIViewController {
    TabPage(tabIndex = 3) {
        MyPageScreen(viewModel = IosAppState.viewModel, onSubPageChange = onSubPageChange)
    }
}

/** 지출 추가/수정 모달 (Swift fullScreenCover 로 표시 — 네이티브 시트 전환) */
@Suppress("unused", "FunctionName")
fun AddSpendingViewController(onClose: () -> Unit): UIViewController = ComposeUIViewController {
    val vm = IosAppState.viewModel
    val accentIndex by vm.accentIndex.collectAsState()
    val initialSyncing by vm.initialSyncing.collectAsState()
    val account by vm.account.collectAsState()
    val target = IosAppState.spendingToEdit.value

    GatchaLogTheme(accentIndex = accentIndex) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // 입력 필드 밖 탭 → 키보드 숨김 (금액 입력 숫자 키패드는 리턴 키가 없어 필수)
            Box(Modifier.fillMaxSize().dismissKeyboardOnTapOutside()) {
                if (!account.isGuest && initialSyncing) {
                    // 초기 동기화 중에는 편집을 막는다 — 탭 게이트와 동일한 이유 (클라우드 덮어쓰기 방지).
                    // 동기화가 끝나면 자동으로 입력 폼이 나타난다.
                    AccountLoadingScreen(loading = true, onFinished = {})
                } else {
                    AddSpendingModal(
                        spendingToEdit = target,
                        nudgeMessage = { game, amount -> vm.overspendNudge(game, amount, target?.id) },
                        onDismiss = {
                            IosAppState.spendingToEdit.value = null
                            onClose()
                        },
                        onSave = { spending ->
                            if (target == null) vm.addSpending(spending) else vm.updateSpending(spending)
                            IosAppState.spendingToEdit.value = null
                            onClose()
                        },
                    )
                }
            }
        }
    }
}
