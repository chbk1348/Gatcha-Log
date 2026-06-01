package com.gatcha.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.work.NativeScheduler
import com.gatcha.log.ui.auth.AccountLoadingScreen
import com.gatcha.log.ui.auth.LoginScreen
import com.gatcha.log.ui.home.HomeScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.GatchaLogTheme

/**
 * KMP 공유 앱 진입점 — :app 의 MainActivity.setContent 내용을 이식.
 * Android 는 MainActivity(5단계에서 composeApp 기반으로 전환 시), iOS 는 MainViewController 가 이걸 띄운다.
 */
@Composable
fun App() {
    // :app MainActivity.onCreate 의 시작 로직 — 주기 작업 동기화 + 미출석 보충 실행
    LaunchedEffect(Unit) {
        // 자동 출석·알림 주기 작업을 설정 상태에 맞춰 동기화(재부팅·재설치 후 복구 포함)
        runCatching { NativeScheduler.apply() }
        // 주기 작업이 도즈모드 등으로 며칠씩 안 돌 수 있어, 앱 실행 시
        // 오늘 미출석 게임이 남아있으면 즉시 1회 트리거(자동 출석 토글이 켜진 경우만).
        runCatching {
            if (AppSettings().autoCheckIn) {
                val repo = GatchaRepository(AppSettings.currentAccountId())
                val today = DateUtil.hoyoDayKey()
                val done = repo.loadAttendance()[today].orEmpty()
                if (done.size < GameData.attendanceGames.size) {
                    NativeScheduler.runNow()
                }
            }
        }
    }

    // KMP viewModel(): iOS 에는 리플렉션이 없어 initializer 람다로 생성
    val viewModel: SpendingViewModel = viewModel { SpendingViewModel() }
    val accentIndex by viewModel.accentIndex.collectAsState()
    val account by viewModel.account.collectAsState()
    val guestChosen by viewModel.guestChosen.collectAsState()
    val initialSyncing by viewModel.initialSyncing.collectAsState()
    var loadingDone by rememberSaveable { mutableStateOf(false) }

    GatchaLogTheme(accentIndex = accentIndex) {
        when {
            // 첫 진입(로그인/게스트 미선택) → 온보딩에서 사용자가 직접 선택(자동 로그인 안 함)
            account.isGuest && !guestChosen -> LoginScreen(viewModel)
            // 로그인 유저 → 계정 데이터 불러오는 중(0~100% 프로그레스)
            !account.isGuest && !loadingDone ->
                AccountLoadingScreen(loading = initialSyncing, onFinished = { loadingDone = true })
            else -> HomeScreen(viewModel)
        }
    }
}
