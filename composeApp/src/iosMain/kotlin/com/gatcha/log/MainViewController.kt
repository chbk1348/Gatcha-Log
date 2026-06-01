package com.gatcha.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.auth.LoginScreen
import com.gatcha.log.ui.components.GlassBackground
import com.gatcha.log.ui.components.GlgStatusToast
import com.gatcha.log.ui.game.GameInfoScreen
import com.gatcha.log.ui.home.HomeContent
import com.gatcha.log.ui.profile.MyPageScreen
import com.gatcha.log.ui.spending.AddSpendingModal
import com.gatcha.log.ui.spending.SpendingScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.GatchaLogTheme
import com.gatcha.log.ui.theme.LocalAccent
import kotlinx.coroutines.flow.MutableStateFlow
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
}

/** Swift 가 시작 화면을 결정 — true 면 온보딩(로그인/게스트 선택)부터 */
@Suppress("unused")
fun needsOnboarding(): Boolean =
    IosAppState.viewModel.account.value.isGuest && !IosAppState.viewModel.guestChosen.value

/** 탭 공통 래퍼: 테마 + 글래스 배경 + 상태 토스트 + (옵션) 지출추가 FAB */
@Composable
private fun TabPage(
    showFab: Boolean = false,
    onAddClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val vm = IosAppState.viewModel
    val accentIndex by vm.accentIndex.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()

    GatchaLogTheme(accentIndex = accentIndex) {
        GlassBackground(modifier = Modifier.fillMaxSize()) {
            // 콘텐츠는 상태바 아래부터 시작 (원래 Scaffold 가 주던 상단 인셋을 직접 적용).
            // 하단은 패딩 없이 — 콘텐츠가 반투명 네이티브 탭바 밑으로 비쳐 보이는 게 iOS 26 표준.
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                content()
            }

            // 지출 추가 FAB 는 SwiftUI 네이티브 버튼(ContentView.swift)으로 이동 —
            // iOS 26 시스템 리퀴드 글래스 버튼 + 탭바와 일관된 위치 정렬.
            // showFab/onAddClick 파라미터는 시그니처 호환을 위해 유지하되 여기서는 사용하지 않음.

            // 상태 토스트 (저장됨·출석 완료 등) — 탭바 위에 표시
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
    onAddSpending: () -> Unit,
    onSubPageChange: (Boolean) -> Unit,
): UIViewController = ComposeUIViewController {
    TabPage(showFab = true, onAddClick = { IosAppState.spendingToEdit.value = null; onAddSpending() }) {
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
    TabPage(showFab = true, onAddClick = { IosAppState.spendingToEdit.value = null; onAddSpending() }) {
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
    TabPage {
        GameInfoScreen(viewModel = IosAppState.viewModel, onSubPageChange = onSubPageChange)
    }
}

/** 마이페이지 탭 */
@Suppress("unused", "FunctionName")
fun MyPageTabViewController(onSubPageChange: (Boolean) -> Unit): UIViewController = ComposeUIViewController {
    TabPage {
        MyPageScreen(viewModel = IosAppState.viewModel, onSubPageChange = onSubPageChange)
    }
}

/** 지출 추가/수정 모달 (Swift fullScreenCover 로 표시 — 네이티브 시트 전환) */
@Suppress("unused", "FunctionName")
fun AddSpendingViewController(onClose: () -> Unit): UIViewController = ComposeUIViewController {
    val vm = IosAppState.viewModel
    val accentIndex by vm.accentIndex.collectAsState()
    val target = IosAppState.spendingToEdit.value

    GatchaLogTheme(accentIndex = accentIndex) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
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
