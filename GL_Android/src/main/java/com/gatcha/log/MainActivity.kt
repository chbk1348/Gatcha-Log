package com.gatcha.log

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.ui.auth.AccountLoadingScreen
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.auth.LoginScreen
import com.gatcha.log.ui.home.HomeScreen
import com.gatcha.log.ui.onboarding.OnboardingScreen
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.theme.GatchaLogTheme
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.Notifier
import com.gatcha.log.data.work.AndroidWorkScheduler

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

    /** 알림 탭으로 들어온 딥링크(예: "news:123"). onCreate/onNewIntent 양쪽에서 채운다. */
    private var pendingLink by mutableStateOf<String?>(null)

    /** launchMode=singleTask 라 이미 떠 있는 상태에서 알림을 누르면 여기로만 들어온다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_LINK)?.let { pendingLink = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 시스템 스플래시(Theme.GatchaLog.Splash) — 반드시 super.onCreate 앞. 첫 프레임이 그려지면 자동으로 걷힌다.
        // 앱 진입을 붙잡아두지는 않는다: 클라우드 동기화 대기는 AccountLoadingScreen(게이지 링)이 맡는다.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 시스템 바 아이콘은 **항상 어두운 색**(라이트 배경용)으로 고정한다.
        // 인자 없는 enableEdgeToEdge() 는 아이콘 색을 시스템 다크모드 설정에 맡긴다. 이 앱은
        // 라이트 전용이라 기기가 다크모드면 흰 아이콘이 흰 배경 위에 놓여 상태바가 사라진 것처럼
        // 보인다(공유 시트처럼 시스템 창이 겹쳤다 사라질 때 특히 두드러졌다).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        // 자동 출석·알림 주기 작업을 설정 상태에 맞춰 동기화(재부팅·재설치 후 복구 포함)
        runCatching { AndroidWorkScheduler.apply(applicationContext) }
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
        intent?.getStringExtra(Notifier.EXTRA_LINK)?.let { pendingLink = it }
        setContent {
            val viewModel: SpendingViewModel = viewModel()
            // 알림 딥링크 소비 — 탭 전환·상세 진입은 VM 상태(pendingTab/pendingNewsId)를 화면이 구독해 처리.
            LaunchedEffect(pendingLink) {
                pendingLink?.let { viewModel.handleNotificationLink(it); pendingLink = null }
            }
            // 앱으로 돌아올 때마다 ① 오래 떠나 있었으면 데이터 갱신 ② 밀린 알림 1회 점검.
            // (실제 실행 여부·간격 제한은 VM 이 판단한다.)
            // ON_STOP 으로 내려간 시각을 남겨야 VM 이 '얼마나 떠나 있었는지'를 잰다.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> viewModel.onAppForeground()
                        Lifecycle.Event.ON_STOP -> viewModel.onAppBackground()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            // 알림 권한 런처. Compose 런처라 Activity registerForActivityResult lint 회피.
            //
            // 앱 시작 시 자동 요청은 없앴다(v27.38.0) — 켜자마자 맥락 없이 뜨던 팝업이었다.
            // 신규 유저는 온보딩 ④에서 맥락과 함께 요청하고, 기존 유저는 이미 물어본 적이 있으며,
            // 그 외에는 알림 설정 화면의 안내 배너에서 직접 허용할 수 있다.
            val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val accentIndex by viewModel.accentIndex.collectAsStateWithLifecycle()
            val account by viewModel.account.collectAsStateWithLifecycle()
            val initialSyncing by viewModel.initialSyncing.collectAsStateWithLifecycle()
            val networkAlert by viewModel.networkAlert.collectAsStateWithLifecycle()
            // 로컬 데이터가 이미 있으면(재실행) 로딩 게이트를 건너뛰고 즉시 진입 — 동기화는 백그라운드.
            // 첫 로그인·재설치(로컬 없음)에서만 게이지 링 로딩 화면을 보여준다.
            var loadingDone by rememberSaveable { mutableStateOf(viewModel.hasLocalData) }
            var onboardingDone by rememberSaveable { mutableStateOf(AppSettings().onboardingDone) }

            // 로그인 전(온보딩·로그인) 화면은 **사용자 테마를 따르지 않고 브랜드 민트로 고정**(index 0).
            //
            // 테마는 계정에 딸린 설정이라 로그인 이후에 불러오는 게 맞다. 그런데 재설치해도 인증이 남아
            // 자동 로그인되면 클라우드에서 강조색까지 복원되어, 아직 로그인 화면인데 남의 테마 색이 칠해지고,
            // 테마를 읽을 수 없는 시스템 스플래시(항상 아이콘 민트)와도 어긋난다.
            // 로그인 전 구간은 앱 아이콘의 색으로 통일한다.
            val preLogin = !onboardingDone || account.isGuest
            GatchaLogTheme(accentIndex = if (preLogin) 0 else accentIndex) {
                when {
                    // 첫 실행 → 앱 소개 4페이지(로그인보다 앞). 재설치 전까지 다시 뜨지 않는다.
                    !onboardingDone -> OnboardingScreen(onFinish = { requestNotification ->
                        AppSettings().onboardingDone = true
                        if (requestNotification) {
                            // OS 권한만 받고 앱 내부 토글이 꺼져 있으면 알림이 한 건도 오지 않는다.
                            // 온보딩 ④에서 약속한 3종을 함께 켠다(VM 세터가 주기 작업 스케줄까지 갱신).
                            viewModel.setNotifyPickup(true)
                            viewModel.setNotifyBudget(true)
                            viewModel.setNotifyResin(true)
                            requestNotificationPermission(notifPermLauncher::launch)
                        }
                        onboardingDone = true
                    })
                    // 미로그인 → 로그인 화면(게스트 모드 없음, 구글 로그인 필수)
                    account.isGuest -> LoginScreen(viewModel)
                    // 로그인 유저 → 계정 데이터 불러오는 중(게이지 링 0~100%)
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

                // 실험 빌드 경고 — 앱 시작 시 1회(experiment/android-floating-toolbar).
                //
                // `rememberSaveable` 이라 화면 회전·구성 변경에는 다시 안 뜨지만 프로세스가 새로 뜨면
                // 다시 나온다 — "앱 시작 시 1회"가 의도다.
                // 배포본에는 `BuildConfig.EXPERIMENT` 가 false 라 이 블록 자체가 죽는다.
                if (BuildConfig.EXPERIMENT) {
                    var experimentAcked by rememberSaveable { mutableStateOf(false) }
                    if (!experimentAcked) {
                        GlgDialog(
                            title = "⚠️ 실험 빌드",
                            onDismiss = { experimentAcked = true },
                            confirmText = "확인하고 계속",
                            onConfirm = { experimentAcked = true },
                            dismissText = null,
                        ) {
                            Text(
                                "정식 배포본이 아닙니다. 검증되지 않은 UI·라이브러리가 들어 있어 " +
                                    "예기치 않은 동작이나 종료가 발생할 수 있어요.\n\n" +
                                    "설정 > 앱 버전에 빨간 EXPERIMENT 표시가 있으면 이 빌드입니다.",
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Android 13+ 에서 아직 권한이 없을 때만 OS 프롬프트를 띄운다(12 이하는 권한 개념 자체가 없음).
     * 실제로 띄웠을 때만 notifPermAsked 를 남긴다 — 이 플래그가 '영구 거부' 판별의 근거다.
     */
    private fun requestNotificationPermission(launchPermission: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AppSettings().notifPermAsked = true
            launchPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}