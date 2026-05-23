package com.gatcha.log

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SpendingViewModel = viewModel()
            val accentIndex by viewModel.accentIndex.collectAsState()
            val account by viewModel.account.collectAsState()
            val guestChosen by viewModel.guestChosen.collectAsState()
            val initialSyncing by viewModel.initialSyncing.collectAsState()
            var loadingDone by rememberSaveable { mutableStateOf(false) }
            GatchaLogTheme(accentIndex = accentIndex) {
                when {
                    // 첫 진입(로그인/게스트 미선택) → 온보딩
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