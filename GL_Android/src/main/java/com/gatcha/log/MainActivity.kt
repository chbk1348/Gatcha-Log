package com.gatcha.log

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gatcha.log.ui.auth.AccountLoadingScreen
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.auth.LoginScreen
import com.gatcha.log.ui.home.HomeScreen
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.theme.GatchaLogTheme
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.work.AndroidWorkScheduler

class MainActivity : ComponentActivity() {

    /** 첫 실행 시 알림 권한(POST_NOTIFICATIONS, Android 13+) 1회 요청용 런처. */
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        runCatching { AndroidWorkScheduler.apply(applicationContext) }
        // 앱 첫 실행 시 알림 권한 1회 자동 요청(Android 13+). 이후엔 설정에서만 유도.
        runCatching {
            val settings = AppSettings()
            if (!settings.notifPermAsked) {
                settings.notifPermAsked = true
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        // 6시간 주기 워커가 도즈모드·배터리 절약으로 며칠씩 안 돌 수 있어, 앱 실행 시
        // 오늘 미출석 게임이 남아있으면 즉시 1회 트리거(자동 출석 토글이 켜진 경우만).
        runCatching {
            val ctx = applicationContext
            if (AppSettings().autoCheckIn) {
                val repo = GatchaRepository(AppSettings.currentAccountId())
                val today = DateUtil.hoyoDayKey()
                val done = repo.loadAttendance()[today].orEmpty()
                if (done.size < GameData.attendanceGames.size) {
                    AndroidWorkScheduler.runNow(ctx)
                }
            }
        }
        setContent {
            val viewModel: SpendingViewModel = viewModel()
            val accentIndex by viewModel.accentIndex.collectAsState()
            val account by viewModel.account.collectAsState()
            val initialSyncing by viewModel.initialSyncing.collectAsState()
            val networkAlert by viewModel.networkAlert.collectAsState()
            var loadingDone by rememberSaveable { mutableStateOf(false) }
            GatchaLogTheme(accentIndex = accentIndex) {
                when {
                    // 미로그인 → 로그인 화면(게스트 모드 없음, 구글 로그인 필수)
                    account.isGuest -> LoginScreen(viewModel)
                    // 로그인 유저 → 계정 데이터 불러오는 중(0~100% 프로그레스)
                    !account.isGuest && !loadingDone ->
                        AccountLoadingScreen(loading = initialSyncing, onFinished = { loadingDone = true })
                    else -> HomeScreen(viewModel)
                }
                // 네트워크 미연결 — 앱 진입·로딩·새로고침 공통 얼럿 모달(앱 루트에 한 번만).
                networkAlert?.let { msg ->
                    GlgDialog(
                        title = "인터넷 연결 없음",
                        onDismiss = { viewModel.clearNetworkAlert() },
                        confirmText = "확인",
                        onConfirm = { viewModel.clearNetworkAlert() },
                        dismissText = null,
                    ) {
                        Text(msg)
                    }
                }
            }
        }
    }
}