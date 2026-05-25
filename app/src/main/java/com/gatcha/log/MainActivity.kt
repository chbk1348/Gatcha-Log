package com.gatcha.log

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gatcha.log.ui.auth.AccountLoadingScreen
import com.gatcha.log.ui.auth.LoginScreen
import com.gatcha.log.ui.home.HomeScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.GatchaLogTheme
import com.gatcha.log.data.work.NativeScheduler

class MainActivity : ComponentActivity() {

    /**
     * 기기 글꼴 크기(접근성 폰트 스케일)와 무관하게 앱 전체를 고정 크기로 렌더한다.
     * base context 의 fontScale 을 1.0 으로 고정하면, 메인 화면은 물론 거기서 파생되는
     * 모든 다이얼로그·바텀시트·팝업(별도 윈도우)까지 시스템 글꼴 크기 영향을 받지 않는다.
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { fontScale = 1.0f }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 자동 출석·알림 주기 작업을 설정 상태에 맞춰 동기화(재부팅·재설치 후 복구 포함)
        runCatching { NativeScheduler.apply(applicationContext) }
        setContent {
            val viewModel: SpendingViewModel = viewModel()
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
    }
}