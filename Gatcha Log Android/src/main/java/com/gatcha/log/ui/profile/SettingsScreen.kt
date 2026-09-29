package com.gatcha.log.ui.profile

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
import com.gatcha.log.data.GameData
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.gatcha.log.BuildConfig
import com.gatcha.log.R
import com.gatcha.log.ui.components.BudgetDialog
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.openExternalLink
import com.gatcha.log.ui.components.GlgButton
import androidx.compose.foundation.lazy.rememberLazyListState
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.ui.game.HoyolabLinkScreen
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.NotificationCatalog
import com.gatcha.log.data.NotifyKey
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.util.SafIO
import kotlinx.coroutines.launch
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.AccentPalette
import com.gatcha.log.ui.theme.ACCENT_VIVID_COUNT
import com.gatcha.log.ui.theme.DEFAULT_ACCENT_INDEX
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.theme.glgShortSpec
import com.gatcha.log.ui.theme.glgStandardSpec
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

/** 프로젝트 저장소 홈. 업데이트 체크(UpdateChecker)·OTA 는 같은 저장소의 raw/releases 경로를 쓴다. */
private const val GITHUB_REPO_URL = "https://github.com/chbk1348/Gatcha-Log"

@Composable
fun SettingsScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val ctx = LocalContext.current
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val gameBudgets by viewModel.gameBudgets.collectAsStateWithLifecycle()
    // 예산 다이얼로그의 게임별 이번 달 합계 — VM 의 파생값을 쓴다.
    // monthlyTotalsByGame() 을 직접 부르면 재구성마다 지출 전체 스캔 + groupBy 가 다시 돈다.
    val monthlyTotalsByGame by viewModel.currentMonthTotalsByGame.collectAsStateWithLifecycle()
    val accentIndex by viewModel.accentIndex.collectAsStateWithLifecycle()
    val hoyolab by viewModel.hoyolabConfig.collectAsStateWithLifecycle()
    val autoCheckIn by viewModel.autoCheckIn.collectAsStateWithLifecycle()
    val nudgeOverspend by viewModel.nudgeOverspend.collectAsStateWithLifecycle()
    val nudgeThreshold by viewModel.nudgeThreshold.collectAsStateWithLifecycle()
    val spendingCompact by viewModel.spendingCompact.collectAsStateWithLifecycle()
    val heroGlow by viewModel.heroGlow.collectAsStateWithLifecycle()
    val charElementFx by viewModel.charElementFx.collectAsStateWithLifecycle()
    val myGames by viewModel.myGames.collectAsStateWithLifecycle()
    // 알림 설정 줄 옆 「7개 중 N개 켜짐」
    val nBudget by viewModel.notifyBudget.collectAsStateWithLifecycle()
    val nResin by viewModel.notifyResin.collectAsStateWithLifecycle()
    val nAttend by viewModel.notifyAttendance.collectAsStateWithLifecycle()
    val nPickup by viewModel.notifyPickup.collectAsStateWithLifecycle()
    val nCombat by viewModel.notifyCombat.collectAsStateWithLifecycle()
    val nNews by viewModel.notifyNews.collectAsStateWithLifecycle()
    val nHoyoland by viewModel.notifyHoyoland.collectAsStateWithLifecycle()
    val notifyOnCount = NotificationCatalog.items.count {
        when (it.key) {
            NotifyKey.BUDGET -> nBudget; NotifyKey.RESIN -> nResin; NotifyKey.ATTENDANCE -> nAttend; NotifyKey.PICKUP -> nPickup
            NotifyKey.COMBAT -> nCombat; NotifyKey.NEWS -> nNews; NotifyKey.HOYOLAND -> nHoyoland
        }
    }
    val versionName = remember { com.gatcha.log.data.api.UpdateChecker.currentVersionName() }
    // 상태 메시지 토스트는 상위 HomeScreen 의 전역 GlgStatusToast 가 처리

    // 알림 권한(Android 13+) — 자동 출석 ON 등 알림을 동반하는 토글을 켤 때 요청
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ensureNotifPerm: () -> Unit = { requestNotifPermIfNeeded(context, notifPermLauncher::launch) }

    // 배터리 최적화 화이트리스트는 **OS 상태**라 Compose 가 변화를 알 수 없다. 예전엔 자동 출석 토글이
    // 바뀔 때만 다시 읽어서, 배너의 '허용'을 눌러 시스템 다이얼로그에서 허용하고 돌아와도
    // "배터리 최적화로 자동 출석이 막힐 수 있어요" 가 그대로 남았다(이미 허용된 사람도 마찬가지).
    // 화면에 돌아올 때(ON_RESUME) 다시 읽는다 — 알림 권한 배너가 쓰는 방식과 같다.
    var batteryRefresh by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) batteryRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showBudget = remember { mutableStateOf(false) }
    val showNudgeThreshold = remember { mutableStateOf(false) }
    val showHoyolab = remember { mutableStateOf(false) }
    val showNotif = remember { mutableStateOf(false) }
    val showData = remember { mutableStateOf(false) }

    // 홈 만료 배너 CTA → 마이페이지 → 설정 → HoYoLAB 연동까지 자동 진입(C4 흐름).
    val pendingOpenHoyolab by viewModel.pendingOpenHoyolabLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenHoyolab) {
        if (pendingOpenHoyolab) {
            showHoyolab.value = true
            viewModel.consumePendingOpenHoyolabLink()
        }
    }
    val showUplog = remember { mutableStateOf(false) }
    val showCredits = remember { mutableStateOf(false) }
    // 개발자 메뉴 — 디버그 빌드에서만 진입점이 그려진다.
    val showDev = remember { mutableStateOf(false) }
    val showTheme = remember { mutableStateOf(false) }
    val showMyGames = remember { mutableStateOf(false) }

    // 설정 하위 페이지 스택: 0=메인, 1=알림 설정, 2=데이터 관리, 3=HoYoLAB 연동, 4=업데이트 로그, 5=개발자 메뉴, 6=테마, 7=내 게임, 8=예산 관리.
    // 깊어지면 우→좌 슬라이드 push/pop.
    val subPage = when {
        showBudget.value -> 8
        showMyGames.value -> 7
        showTheme.value -> 6
        showDev.value -> 5
        showUplog.value -> 4
        showHoyolab.value -> 3
        showData.value -> 2
        showNotif.value -> 1
        else -> 0
    }
    // 하위 페이지가 열려 있으면 시스템 back 제스쳐를 이 계층에서 pop 처리 —
    // 자체 핸들러가 없으면 MyPageScreen 의 BackHandler 로 새어 마이페이지로 튕긴다.
    BackHandler(enabled = subPage > 0) {
        when {
            showBudget.value -> showBudget.value = false
            showMyGames.value -> showMyGames.value = false
            showTheme.value -> showTheme.value = false
            showDev.value -> showDev.value = false
            showUplog.value -> showUplog.value = false
            showHoyolab.value -> showHoyolab.value = false
            showData.value -> showData.value = false
            showNotif.value -> showNotif.value = false
        }
    }
    // 설정 메인 목록의 스크롤 — 하위 페이지를 다녀와도 보던 자리에 남도록 전환 바깥에 둔다.
    val settingsListState = rememberLazyListState()
    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally(glgStandardSpec()) { it } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { -it / 4 } + fadeOut(glgShortSpec()))
            } else {
                (slideInHorizontally(glgStandardSpec()) { -it / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { it } + fadeOut(glgShortSpec()))
            }
        },
        label = "settingsPage",
    ) { page ->
        if (page == 8) {
            BudgetScreen(
                overall = budget, gameBudgets = gameBudgets, monthlyTotals = monthlyTotalsByGame, myGames = myGames,
                onSave = { o, perGame -> viewModel.setBudgets(o, perGame); showBudget.value = false },
                onBack = { showBudget.value = false },
            )
        } else if (page == 7) {
            MyGamesScreen(myGames, onToggle = { k -> viewModel.setMyGames(if (k in myGames) myGames - k else myGames + k) }, onBack = { showMyGames.value = false })
        } else if (page == 6) {
            ThemeScreen(accentIndex, onSelect = { viewModel.setAccentIndex(it) }, onBack = { showTheme.value = false })
        } else if (page == 5) {
            DeveloperScreen(viewModel, onBack = { showDev.value = false })
        } else if (page == 4) {
            UpdateLogScreen(onBack = { showUplog.value = false })
        } else if (page == 3) {
            HoyolabLinkScreen(
                config = hoyolab,
                // 실패하면(안내는 VM 이 띄운다) 폼을 그대로 둔다.
                onSave = { if (viewModel.updateHoyolabConfig(it)) showHoyolab.value = false },
                onBack = { showHoyolab.value = false },
            )
        } else if (page == 2) {
            DataManagementScreen(viewModel, onBack = { showData.value = false })
        } else if (page == 1) {
            NotificationSettingsScreen(viewModel, onBack = { showNotif.value = false })
        } else Box(Modifier.fillMaxSize()) {
        // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
        // 스크롤 상태는 페이지 전환 **바깥**(settingsListState)에 있다 — 여기서 만들면 하위 페이지를 열 때마다
        // 버려져, 뒤로 돌아오면 맨 위에서 다시 시작했다(2026-09-28 지적).
        val listState = settingsListState
        val scrolled by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        LazyColumn(
            state = listState,
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = glgDetailContentTop(), bottom = 24.dp),
        ) {

        // 설정 메인 개편(아티팩트 S0) — 알림 설정과 같은 결: 묶음 제목 + 흰 카드 + 색 아이콘 줄.
        item { NotifyGroupTitle("알림", "받을 알림 · 방해 금지") }
        item {
            NotifyCard {
                SettingsNavRow(Icons.Default.Notifications, Tint.teal, "알림 설정", NotificationCatalog.enabledLabel(notifyOnCount)) { showNotif.value = true }
            }
        }

        item { NotifyGroupTitle("내 게임 · 예산", "온보딩에서 고른 값과 같아요") }
        item {
            NotifyCard {
                SettingsNavRow(Icons.Default.SportsEsports, Tint.purple, "내 게임", myGamesLabel(myGames)) { showMyGames.value = true }
                HorizontalDivider(color = RowDivider)
                SettingsNavRow(Icons.Default.Savings, Tint.orange, "월 예산", if (budget > 0) won(budget) else "미설정") { showBudget.value = true }
                HorizontalDivider(color = RowDivider)
                NotifyRow(Icons.Default.Psychology, Tint.amber.first, Tint.amber.second, "과소비 예방 넛지", "예산 · 평소치를 넘으면 저장 전에 한 번 더 확인", nudgeOverspend) {
                    viewModel.setNudgeOverspend(it)
                }
                if (nudgeOverspend) {
                    HorizontalDivider(color = RowDivider)
                    SettingsNavRow(Icons.Default.PriceCheck, Tint.amber, "넛지 기준 금액", won(nudgeThreshold)) { showNudgeThreshold.value = true }
                }
            }
        }

        item { NotifyGroupTitle("연동 · 자동화", "HoYoLAB") }
        item {
            NotifyCard {
                SettingsNavRow(Icons.Default.Link, Tint.navy, "HoYoLAB 계정 연동", if (hoyolab.isLinked) "연동됨" else "미연동") { showHoyolab.value = true }
                HorizontalDivider(color = RowDivider)
                NotifyRow(
                    Icons.Default.EventAvailable, Tint.teal.first, Tint.teal.second, "자동 출석체크",
                    if (hoyolab.isLinked) "매일 자동으로 출석을 챙겨요 (켜면 지금 한 번 바로 시도)" else "HoYoLAB을 연동하면 사용할 수 있어요",
                    hoyolab.isLinked && autoCheckIn,
                ) { on ->
                    if (!hoyolab.isLinked) { showHoyolab.value = true; return@NotifyRow }
                    // 자동 출석 실패 시 알림으로 안내하려면 POST_NOTIFICATIONS(API33+) 권한 필요.
                    // 도즈/스탠바이로 워커가 며칠 누락되는 케이스 회복을 위해 배터리 최적화 화이트리스트도 요청.
                    if (on) {
                        ensureNotifPerm()
                        (context as? android.app.Activity)?.let { com.gatcha.log.data.BatteryOptimization.request(it) }
                    }
                    viewModel.setAutoCheckIn(on)
                }
            }
        }
        // 배터리 최적화 상태 진단(자동 출석 ON 인데 화이트리스트 미등록이면 안내 + CTA)
        item {
            // batteryRefresh 를 키에 넣어야 허용하고 돌아왔을 때 다시 읽는다(위 DisposableEffect 참고).
            val ignoring = remember(autoCheckIn, batteryRefresh) {
                com.gatcha.log.data.BatteryOptimization.isIgnoring(context)
            }
            if (autoCheckIn && hoyolab.isLinked && !ignoring) {
                WarnBanner(
                    "배터리 최적화로 자동 출석이 막힐 수 있어요. 이 앱을 「제한 안함」으로 등록해 주세요.", "허용",
                ) { (context as? android.app.Activity)?.let { com.gatcha.log.data.BatteryOptimization.request(it) } }
            }
        }

        item { NotifyGroupTitle("화면", "표시 · 테마") }
        item {
            NotifyCard {
                NotifyRow(Icons.Default.ViewAgenda, Tint.slate.first, Tint.slate.second, "지출 내역 컴팩트 보기", "지출 목록을 한 줄로 빽빽하게 (태그 · 결제수단 숨김)", spendingCompact) {
                    viewModel.setSpendingCompact(it)
                }
                HorizontalDivider(color = RowDivider)
                NotifyRow(Icons.Default.Bolt, Tint.pink.first, Tint.pink.second, "캐릭터 속성 연출", "캐릭터 상세에 들어갈 때 속성 효과를 한 번 재생", charElementFx) {
                    viewModel.setCharElementFx(it)
                }
                HorizontalDivider(color = RowDivider)
                NotifyRow(Icons.Default.AutoAwesome, Tint.blue.first, Tint.blue.second, "홈 히어로 글로우", "홈 상단에서 은은하게 떠다니는 빛 효과", heroGlow) {
                    viewModel.setHeroGlow(it)
                }
                HorizontalDivider(color = RowDivider)
                // 20색이 되어 카드 안 그리드로는 길어져 전용 페이지로 옮겼다.
                SettingsNavRow(
                    Icons.Default.Palette, Tint.purple, "테마",
                    AccentPalette.getOrElse(accentIndex) { AccentPalette[DEFAULT_ACCENT_INDEX] }.label,
                ) { showTheme.value = true }
            }
        }

        item { NotifyGroupTitle("데이터", "백업 · 복원 · 초기화") }
        item {
            NotifyCard {
                SettingsNavRow(Icons.Default.Storage, Tint.slate, "데이터 관리", "백업 · 복원 · 초기화") { showData.value = true }
            }
        }

        // 개발자 메뉴 — **디버그 빌드에서만**. 릴리스에서는 이 블록 자체가 그려지지 않는다.
        if (BuildConfig.DEBUG) {
            item { NotifyGroupTitle("개발자", "디버그 빌드 전용") }
            item {
                NotifyCard {
                    SettingsNavRow(Icons.Default.BugReport, Tint.red, "개발자 메뉴", "상태 만들기 · 진단") { showDev.value = true }
                }
            }
        }

        item { NotifyGroupTitle("앱 정보", "v$versionName") }
        item {
            NotifyCard {
                SettingsNavRow(Icons.Default.SystemUpdate, Tint.teal, "업데이트 확인", null) { viewModel.checkForUpdate(manual = true) }
                HorizontalDivider(color = RowDivider)
                SettingsNavRow(Icons.Default.NewReleases, Tint.blue, "업데이트 로그", null) { showUplog.value = true }
                HorizontalDivider(color = RowDivider)
                SettingsNavRow(Icons.Default.Copyright, Tint.slate, "출처 · 저작권", null) { showCredits.value = true }
                HorizontalDivider(color = RowDivider)
                SettingsNavRow(ImageVector.vectorResource(R.drawable.ic_github), Tint.navy, "GitHub", null) { openExternalLink(ctx, GITHUB_REPO_URL) }
                HorizontalDivider(color = RowDivider)
                SettingsNavRow(Icons.Default.Info, Tint.slate, "앱 버전", "v$versionName", trailing = { BuildVariantChip() }, chevron = false) {}
            }
        }
        }
        GlgDetailHeaderOverlay("설정", onBack, scrolled)
        }
    }

    if (showNudgeThreshold.value) {
        NudgeThresholdDialog(
            current = nudgeThreshold,
            onDismiss = { showNudgeThreshold.value = false },
            onConfirm = { viewModel.setNudgeThreshold(it); showNudgeThreshold.value = false },
        )
    }
    if (showCredits.value) {
        CreditsDialog { showCredits.value = false }
    }
}

/**
 * 데이터 관리 하위 페이지 — 백업·복원(안전 우선)을 맨 위, 내보내기 중간, 파괴 작업은 '위험 구역'으로 분리했다.
 * (설정 메인에서 슬라이드 진입 · iOS DataManagementView 파리티)
 */
@Composable
private fun DataManagementScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gachaStats by viewModel.gachaStats.collectAsStateWithLifecycle()
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()

    // 백업 파일 내보내기/가져오기 (SAF)
    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { u -> scope.launch { viewModel.exportBackupContent()?.let { json -> SafIO.writeText(context, u, json) } } }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> scope.launch { SafIO.readText(context, u)?.let { viewModel.importBackupFromContent(it) } } }
    }

    // 파괴작업은 2단계 확인: 1차(백업 권장 안내) → 2차(최종 확인)
    val showClearGacha = remember { mutableStateOf(false) }
    val showClearGacha2 = remember { mutableStateOf(false) }
    val showClearSpend = remember { mutableStateOf(false) }
    val showClearSpend2 = remember { mutableStateOf(false) }
    val showImportBackup = remember { mutableStateOf(false) }

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = glgDetailContentTop(), bottom = 24.dp),
    ) {

        // 설정 메인과 같은 결(9/29) — 묶음 제목 + 흰 카드 + 색 아이콘 줄.
        // 백업·복원 — 데이터 보호가 가장 중요하므로 맨 위에 (재설치·기기 변경 대비)
        item { NotifyGroupTitle("백업 · 복원", "재설치 · 기기 변경 대비") }
        item {
            NotifyCard {
                SettingsNavRow(Icons.Default.Backup, Tint.teal, "백업 파일 내보내기", "전체 데이터") {
                    val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                    exportBackupLauncher.launch("gatchalog-backup-$date.json")
                }
                HorizontalDivider(color = RowDivider)
                SettingsNavRow(Icons.Default.Restore, Tint.blue, "백업 파일에서 복원", null) { showImportBackup.value = true }
            }
            Text(
                "구글 로그인 없이도 전체 데이터(가챠 기록 포함)를 파일로 저장해 두면, 앱을 재설치하거나 기기를 바꿔도 복원할 수 있어요.",
                fontSize = 11.5.sp, lineHeight = 17.sp, color = Color(0xFF7A8784),
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
            )
        }

        item { NotifyGroupTitle("내보내기", "CSV") }
        item {
            NotifyCard {
                SettingsNavRow(Icons.Default.Download, Tint.slate, "지출 내역 내보내기", "CSV") { shareCsvFile(context, viewModel.buildCsv()) }
            }
        }

        // 위험 구역 — 되돌릴 수 없는 파괴 작업은 빨간 톤으로 시각 분리
        item { NotifyGroupTitle("위험 구역", "되돌릴 수 없어요") }
        item {
            NotifyCard {
                SettingsNavRow(Icons.Default.DeleteSweep, Tint.red, "가챠 기록 초기화", gachaStats?.let { "${it.total}건" } ?: "없음", titleColor = DangerRed) {
                    if (gachaStats != null) showClearGacha.value = true
                }
                HorizontalDivider(color = RowDivider)
                SettingsNavRow(Icons.Default.DeleteForever, Tint.red, "지출 전체 삭제", "${spendings.size}건", titleColor = DangerRed) {
                    if (spendings.isNotEmpty()) showClearSpend.value = true
                }
            }
            Text(
                "되돌릴 수 없는 작업이에요. 먼저 위 ‘백업 파일 내보내기’로 백업을 권장해요.",
                fontSize = 11.5.sp, lineHeight = 17.sp, color = DangerRed,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
    GlgDetailHeaderOverlay("데이터 관리", onBack, scrolled)
    }

    // 가챠 기록 초기화 — 1단계(백업 권장 안내)
    if (showClearGacha.value) {
        GlgDialog(
            title = "가챠 기록 초기화",
            onDismiss = { showClearGacha.value = false },
            confirmText = "계속",
            onConfirm = { showClearGacha.value = false; showClearGacha2.value = true },
        ) {
            Text("가져온 모든 가챠 기록을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘백업 파일 내보내기’로 백업을 권장해요.", fontSize = 13.sp, color = TextSecondary)
        }
    }
    // 가챠 기록 초기화 — 2단계(최종 확인)
    if (showClearGacha2.value) {
        GlgDialog(
            title = "정말 초기화할까요?",
            onDismiss = { showClearGacha2.value = false },
            confirmText = "초기화",
            onConfirm = { viewModel.clearGachaRecords(); showClearGacha2.value = false },
        ) {
            Text("이 작업은 되돌릴 수 없어요. 가챠 기록을 모두 삭제합니다.", fontSize = 13.sp, color = TextSecondary)
        }
    }
    // 지출 전체 삭제 — 1단계(백업 권장 안내)
    if (showClearSpend.value) {
        GlgDialog(
            title = "지출 전체 삭제",
            onDismiss = { showClearSpend.value = false },
            confirmText = "계속",
            onConfirm = { showClearSpend.value = false; showClearSpend2.value = true },
        ) {
            Text("모든 지출 기록(${spendings.size}건)을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘백업 파일 내보내기’로 백업을 권장해요.", fontSize = 13.sp, color = TextSecondary)
        }
    }
    // 지출 전체 삭제 — 2단계(최종 확인)
    if (showClearSpend2.value) {
        GlgDialog(
            title = "정말 삭제할까요?",
            onDismiss = { showClearSpend2.value = false },
            confirmText = "삭제",
            onConfirm = { viewModel.clearSpendings(); showClearSpend2.value = false },
        ) {
            Text("이 작업은 되돌릴 수 없어요. 지출 기록(${spendings.size}건)을 모두 삭제합니다.", fontSize = 13.sp, color = TextSecondary)
        }
    }
    if (showImportBackup.value) {
        GlgDialog(
            title = "백업 파일에서 복원",
            onDismiss = { showImportBackup.value = false },
            confirmText = "파일 선택",
            onConfirm = {
                showImportBackup.value = false
                importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            },
        ) {
            Text(
                "백업 파일을 선택해 복원할까요? 백업에 들어 있는 항목은 현재 데이터를 덮어씁니다.",
                fontSize = 13.sp, color = TextSecondary,
            )
        }
    }
}

/** 위험 구역 강조용 빨강. */
private val DangerRed = Color(0xFFD32F2F)

/**
 * 알림 설정 하위 페이지 — 항목별 알림·방해금지·데일리 요약을 한 곳에 모았다.
 * (설정 메인에서 슬라이드 진입 · iOS NotificationSettingsView 파리티)
 */
@Composable
private fun NotificationSettingsScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val notifyBudget by viewModel.notifyBudget.collectAsStateWithLifecycle()
    val notifyAttendance by viewModel.notifyAttendance.collectAsStateWithLifecycle()
    val notifyResin by viewModel.notifyResin.collectAsStateWithLifecycle()
    val notifyPickup by viewModel.notifyPickup.collectAsStateWithLifecycle()
    val notifyHoyoland by viewModel.notifyHoyoland.collectAsStateWithLifecycle()
    val notifyNews by viewModel.notifyNews.collectAsStateWithLifecycle()
    val notifyCombat by viewModel.notifyCombat.collectAsStateWithLifecycle()
    val notifyDndEnabled by viewModel.notifyDndEnabled.collectAsStateWithLifecycle()
    val notifyDndStartHour by viewModel.notifyDndStartHour.collectAsStateWithLifecycle()
    val notifyDndEndHour by viewModel.notifyDndEndHour.collectAsStateWithLifecycle()

    // 알림 권한(Android 13+) — 알림 토글 켤 때 요청.
    // permRefresh: 권한 요청/화면 복귀 후 권한 상태를 다시 읽게 하는 트리거(권한은 Compose 상태가 아니라 OS 상태).
    var permRefresh by remember { mutableIntStateOf(0) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permRefresh++
    }
    val ensureNotifPerm: () -> Unit = { requestNotifPermIfNeeded(context, notifPermLauncher::launch) }

    // 아래 안내 배너의 '허용' 전용 런처 — 여기서 처음 허용하면 항목 일곱 개를 한꺼번에 켠다.
    // 위 런처와 공유하면 안 된다: 개별 토글도 같은 런처를 쓰는데, 그쪽은 '새 공지'만 켜려던 것이라
    // 전부 켜면 의도와 어긋난다. 콜백만 보고는 어느 쪽에서 왔는지 알 수 없어 런처를 따로 둔다.
    val bulkNotifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permRefresh++
        if (granted) viewModel.enableAllNotifyItems()
    }
    val ensureNotifPermForAll: () -> Unit = { requestNotifPermIfNeeded(context, bulkNotifPermLauncher::launch) }
    val activity = remember(context) { context.findActivity() }

    // 시스템 설정에서 알림을 켜고 돌아오면 배너가 사라져야 한다. 권한은 Compose 상태가 아니라 OS 상태라
    // 화면에 돌아올 때(ON_RESUME) 다시 읽어야 한다 — 안 그러면 켜고 와도 "알림 권한이 꺼져 있어요"가 남는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 알림 시각 피커(0~23시) — 방해금지 시작/종료
    val showDndStartPicker = remember { mutableStateOf(false) }
    val showDndEndPicker = remember { mutableStateOf(false) }

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = glgDetailContentTop(), bottom = 24.dp),
    ) {

        // 알림 — 항목별 토글.
        //
        // 일곱 개를 한 카드에 늘어놓던 것을 **성격별 세 묶음**으로 갈랐다(돈·플레이·소식).
        // 켜고 끄는 판단 기준이 서로 달라서, 한 덩어리로는 훑어지지 않았다.
        // 항목 정의(제목·설명·묶음)는 공유 소스 NotificationCatalog 하나뿐이다.
        val notifyState: Map<NotifyKey, Boolean> = mapOf(
            NotifyKey.BUDGET to notifyBudget,
            NotifyKey.RESIN to notifyResin,
            NotifyKey.ATTENDANCE to notifyAttendance,
            NotifyKey.PICKUP to notifyPickup,
            NotifyKey.COMBAT to notifyCombat,
            NotifyKey.NEWS to notifyNews,
            NotifyKey.HOYOLAND to notifyHoyoland,
        )
        val setNotify: (NotifyKey, Boolean) -> Unit = { key, on ->
            if (on) ensureNotifPerm()
            when (key) {
                NotifyKey.BUDGET -> viewModel.setNotifyBudget(on)
                NotifyKey.RESIN -> viewModel.setNotifyResin(on)
                NotifyKey.ATTENDANCE -> viewModel.setNotifyAttendance(on)
                NotifyKey.PICKUP -> viewModel.setNotifyPickup(on)
                NotifyKey.COMBAT -> viewModel.setNotifyCombat(on)
                NotifyKey.NEWS -> viewModel.setNotifyNews(on)
                NotifyKey.HOYOLAND -> viewModel.setNotifyHoyoland(on)
            }
        }

        // 설정 ▸ 알림 설정 개편(아티팩트 S1) — 온보딩 ⑤와 같은 결: 미리보기 알림 + 묶음별 색 아이콘 줄 + 보내는 방식.
        // 보이는 항목만 센다 — 행사가 끝난 호요랜드는 목록에서 빠진다(NotificationCatalog.hoyolandAlertsActive).
        item { NotifyPreviewCard(NotificationCatalog.enabledLabel(NotificationCatalog.items.count { notifyState[it.key] == true })) }
        item {
            // 토글은 켰는데 시스템 알림 권한이 꺼져 있으면 안내.
            // **일곱 개 전부**를 본다 — 예전엔 앞 네 개만 봐서, 픽업·전투·정기결제·공지만 켠 사람에겐
            // 권한이 막혀 있어도 배너가 뜨지 않았다.
            val notifOn = notifyState.any { it.value }
            val notifEnabled = remember(notifyBudget, notifyAttendance, notifyResin, permRefresh) {
                com.gatcha.log.data.Notifier.notificationsEnabled()
            }
            // OS 프롬프트를 아직 띄울 수 있는가 — 띄울 수 있으면 시스템 설정으로 보내지 말고 바로 권한을 요청한다.
            //
            // shouldShowRequestPermissionRationale 은 "한 번도 안 물어봄"과 "두 번 거부해서 영구 차단"을
            // 똑같이 false 로 답한다. notifPermAsked(프롬프트를 실제로 띄운 적 있는지)로 둘을 가른다.
            // 영구 거부·앱 알림 자체가 꺼진 경우엔 프롬프트가 아예 안 뜨므로 시스템 설정 말고는 방법이 없다.
            val canPromptNotifPerm = remember(permRefresh) {
                Build.VERSION.SDK_INT >= 33 && (
                    !AppSettings().notifPermAsked ||
                        (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                            activity, android.Manifest.permission.POST_NOTIFICATIONS,
                        ))
                    )
            }
            if (notifOn && !notifEnabled) {
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF4E8))
                        .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.WarningAmber, null, tint = NotifyWarn, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (canPromptNotifPerm) "알림 권한이 꺼져 있어요. 허용해야 알림이 와요."
                        else "권한이 막혀 있어 알림이 표시되지 않아요. 시스템 설정에서 켜 주세요.",
                        fontSize = 12.5.sp, lineHeight = 18.sp, color = Color(0xFF9A3412), modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (canPromptNotifPerm) "허용" else "설정", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(NotifyWarn)
                            .clickable { if (canPromptNotifPerm) ensureNotifPermForAll() else openAppNotificationSettings(context) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        NotificationCatalog.groups.forEach { group ->
            val groupItems = NotificationCatalog.itemsIn(group)
            item { NotifyGroupTitle(group.title, group.caption) }
            item {
                NotifyCard {
                    groupItems.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider(color = Color(0xFFF0F3F2))
                        val (fg, bg) = notifyTint(entry.key)
                        NotifyRow(notifyIcon(entry.key), fg, bg, entry.title, entry.desc, notifyState[entry.key] == true) { on -> setNotify(entry.key, on) }
                    }
                }
            }
        }

        // 보내는 방식 — 방해금지(시간대 억제). 데일리 요약은 9/29 제거.
        item { NotifyGroupTitle("보내는 방식", "언제 · 어떻게") }
        item {
            NotifyCard {
                NotifyRow(Icons.Default.Bedtime, Color(0xFF4F5C59), Color(0xFFF5F8F8), "방해 금지 시간", "이 시간대엔 알림을 보내지 않아요", notifyDndEnabled) {
                    viewModel.setNotifyDndEnabled(it)
                }
                if (notifyDndEnabled) {
                    Row(Modifier.padding(start = 60.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        TimePill(hourLabel(notifyDndStartHour)) { showDndStartPicker.value = true }
                        Text("~", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A8784), modifier = Modifier.padding(horizontal = 8.dp))
                        TimePill(hourLabel(notifyDndEndHour)) { showDndEndPicker.value = true }
                        Text("기기 시각", fontSize = 11.5.sp, color = Color(0xFF7A8784), modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
    }
    GlgDetailHeaderOverlay("알림 설정", onBack, scrolled)
    }

    if (showDndStartPicker.value) {
        HourPickerDialog("방해금지 시작 시각", notifyDndStartHour, { showDndStartPicker.value = false }) {
            viewModel.setNotifyDndStartHour(it); showDndStartPicker.value = false
        }
    }
    if (showDndEndPicker.value) {
        HourPickerDialog("방해금지 종료 시각", notifyDndEndHour, { showDndEndPicker.value = false }) {
            viewModel.setNotifyDndEndHour(it); showDndEndPicker.value = false
        }
    }
}

private val NotifyWarn = Color(0xFFC2410C)
private val RowDivider = Color(0xFFF0F3F2)

/** 설정 줄 아이콘 색 짝(글자색, 옅은 바탕) — 아티팩트 S0 · S1. */
private object Tint {
    val teal = Color(0xFF177881) to Color(0xFFE3F2F1)
    val purple = Color(0xFF9350F0) to Color(0xFFF1E8FD)
    val orange = Color(0xFFC2410C) to Color(0xFFFFF1E6)
    val amber = Color(0xFFB45309) to Color(0xFFFEF3C7)
    val navy = Color(0xFF0F1A33) to Color(0xFFECEFF4)
    val slate = Color(0xFF475569) to Color(0xFFEEF1F5)
    val pink = Color(0xFFDB2777) to Color(0xFFFCE7F3)
    val blue = Color(0xFF3E76E0) to Color(0xFFE8F0FD)
    val red = Color(0xFFB91C1C) to Color(0xFFFEE2E2)
}

/** 「원신 · 스타레일 외 1」 — 비어 있으면 전체. */
private fun myGamesLabel(keys: Set<String>): String {
    val names = GameData.games.filter { it.key in keys }.map { it.shortName }
    return when {
        names.isEmpty() -> "전체"
        names.size <= 2 -> names.joinToString(" · ")
        else -> "${names.take(2).joinToString(" · ")} 외 ${names.size - 2}"
    }
}

/** 색 아이콘 + 제목 + 값 + 화살표(하위 페이지 · 다이얼로그로 가는 줄). */
@Composable
private fun SettingsNavRow(
    icon: ImageVector, tint: Pair<Color, Color>, title: String, value: String?,
    trailing: (@Composable () -> Unit)? = null, chevron: Boolean = true, titleColor: Color = TextPrimary, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.second), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint.first, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, modifier = Modifier.weight(1f))
        value?.let { Text(it, fontSize = 12.5.sp, color = TextSecondary) }
        trailing?.let { Spacer(Modifier.width(6.dp)); it() }
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB8C4C1), modifier = Modifier.size(18.dp))
        }
    }
}

/** 주황 안내 띠 + 버튼 — 권한 · 배터리 최적화 경고 공용. */
@Composable
private fun WarnBanner(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF4E8))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.WarningAmber, null, tint = NotifyWarn, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.5.sp, lineHeight = 18.sp, color = Color(0xFF9A3412), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            action, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(NotifyWarn).clickable(onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * 설정 ▸ 예산 관리(아티팩트 S3) — 팝업에서 페이지로. 위는 온보딩 ③과 같은 금액 카드([BudgetAmountEditor]),
 * 아래는 게임별 한도(비우면 한도 없음 · 이번 달 사용액 · 넘으면 주황). 내 게임이 위.
 * 「월 예산 끄기」는 월 예산만 0 으로 저장하고 게임별 한도는 그대로 둔다.
 */
@Composable
private fun BudgetScreen(
    overall: Long,
    gameBudgets: Map<String, Long>,
    monthlyTotals: Map<String, Long>,
    myGames: Set<String>,
    onSave: (Long, Map<String, Long>) -> Unit,
    onBack: () -> Unit,
) {
    var amount by remember { mutableLongStateOf(overall) }
    var custom by remember { mutableStateOf(overall > 0 && listOf(50_000L, 100_000L, 150_000L, 300_000L).none { it == overall }) }
    val limits = remember { mutableStateMapOf<String, Long>().apply { putAll(gameBudgets.filterValues { it > 0 }) } }
    val order = remember(myGames) { GameData.pickerGames(myGames).let { it.first + it.second } }
    val perGame: () -> Map<String, Long> = { limits.filterValues { it > 0 } }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
            // 아래 고정 버튼 두 개(50 + 8 + 42 + 위아래 18) 만큼 비워 둔다.
            contentPadding = PaddingValues(top = glgDetailContentTop(), bottom = 140.dp),
        ) {
            item { com.gatcha.log.ui.onboarding.BudgetAmountEditor(amount, custom) { v: Long, c: Boolean -> amount = v; custom = c } }
            item { NotifyGroupTitle("게임별 한도", "선택 · 비워 두면 한도 없음") }
            item {
                NotifyCard {
                    order.forEachIndexed { i, g ->
                        if (i > 0) HorizontalDivider(color = RowDivider)
                        val spent = monthlyTotals[g.key] ?: 0L
                        val limit = limits[g.key] ?: 0L
                        val over = limit > 0 && spent > limit
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(g.color)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    "이번 달 ${won(spent)}" + if (over) " · 한도 초과" else "",
                                    fontSize = 12.sp, color = if (over) NotifyWarn else TextSecondary,
                                    fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = if (limit > 0) "%,d".format(limit) else "",
                                onValueChange = { raw -> limits[g.key] = raw.filter { it.isDigit() }.take(9).toLongOrNull() ?: 0L },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.End),
                                modifier = Modifier.width(118.dp).height(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F8F8))
                                    .border(1.5.dp, if (over) Color(0xFFFED7AA) else Color.Transparent, RoundedCornerShape(12.dp)),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
                                        if (limit <= 0) Text("한도 없음", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA7B1AE))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.width(IntrinsicSize.Min)) { inner() }
                                            if (limit > 0) Text("원", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                Text(
                    "내 게임이 위에 와요. 이번 달 사용액이 한도를 넘으면 주황으로 표시돼요.",
                    fontSize = 11.5.sp, lineHeight = 17.sp, color = Color(0xFF7A8784), modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
        // 「저장」 · 「월 예산 끄기」는 하단에 상시 고정(9/29) — 스크롤해도 늘 보이고, 목록은 그 위에서 끝난다.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .shadow(8.dp, RectangleShape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
                .background(Color.White).navigationBarsPadding().imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        ) {
            com.gatcha.log.ui.onboarding.CtaButton("저장", primary = true, onClick = { onSave(amount.coerceAtLeast(0L), perGame()) })
            com.gatcha.log.ui.onboarding.CtaButton("월 예산 끄기", primary = false, onClick = { onSave(0L, perGame()) })
        }
        GlgDetailHeaderOverlay("예산 관리", onBack, scrolled)
    }
}

/**
 * 설정 ▸ 내 게임(아티팩트 S2) — 온보딩 ②와 같은 값([SpendingViewModel.setMyGames]).
 * 6게임 전부(이환 포함). 비우면 전부로 본다.
 */
@Composable
private fun MyGamesScreen(myGames: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = glgDetailContentTop(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "고른 게임이 지출 입력 맨 위에 오고, 출석도 고른 게임만 챙겨요. 기록은 거르지 않아요.",
                    fontSize = 13.5.sp, lineHeight = 20.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                )
            }
            items(GameData.games.size) { i ->
                val g = GameData.games[i]
                val on = g.key in myGames
                Row(
                    Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) Color(0xFFEEF8F8) else Color.White)
                        .border(if (on) 2.dp else 1.dp, if (on) Color(0xFF1B8E99) else Color(0xFFE3E8E6), RoundedCornerShape(16.dp))
                        .clickable { onToggle(g.key) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(g.color)))
                    Spacer(Modifier.width(12.dp))
                    Text(g.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (on) Color(0xFF1B8E99) else Color(0xFFE3E8E6)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
            item {
                Text(
                    "하나도 고르지 않으면 전부 보여요. 온보딩에서 고른 게임과 같은 값이에요.",
                    fontSize = 11.5.sp, lineHeight = 17.sp, color = Color(0xFF7A8784), modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                )
            }
        }
        GlgDetailHeaderOverlay("내 게임", onBack, scrolled)
    }
}


/** 알림 항목별 아이콘 색(글자색, 옅은 바탕) — 아티팩트 S1 · 온보딩 ⑤와 같은 짝. */
private fun notifyTint(key: NotifyKey): Pair<Color, Color> = when (key) {
    NotifyKey.BUDGET -> NotifyWarn to Color(0xFFFFF1E6)
    NotifyKey.RESIN -> Color(0xFF3E76E0) to Color(0xFFE8F0FD)
    NotifyKey.ATTENDANCE -> Color(0xFF177881) to Color(0xFFE3F2F1)
    NotifyKey.PICKUP -> Color(0xFF9350F0) to Color(0xFFF1E8FD)
    NotifyKey.COMBAT -> Color(0xFFB45309) to Color(0xFFFEF3C7)
    NotifyKey.NEWS -> Color(0xFF475569) to Color(0xFFEEF1F5)
    NotifyKey.HOYOLAND -> Color(0xFFDB2777) to Color(0xFFFCE7F3)
}

/** 미리보기 알림 카드 — 켜면 어떤 알림이 오는지 먼저 보여 주고, 오른쪽 위에 켜진 개수. */
@Composable
private fun NotifyPreviewCard(status: String) {
    Column(
        Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFEEF8F8), Color(0xFFDCF0EE))))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("이렇게 알려 드려요", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF177881), modifier = Modifier.weight(1f))
            Text(
                status, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF177881),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Color.White.copy(alpha = 0.55f)))
        Row(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x1A0F1A33), spotColor = Color(0x1A0F1A33))
                .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(Color(0xFF1FA0AB), Color(0xFF146E77)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Notifications, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text("Gatcha Log", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F5C59), modifier = Modifier.weight(1f))
                    Text("지금", fontSize = 11.5.sp, color = Color(0xFF7A8784))
                }
                Text("레진이 곧 가득 차요", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("원신 190 / 200 · 20분 뒤 가득", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
internal fun NotifyGroupTitle(title: String, caption: String) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.width(6.dp))
        Text(caption, fontSize = 11.5.sp, color = Color(0xFF7A8784))
    }
}

@Composable
internal fun NotifyCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Color(0xFFE3E8E6), RoundedCornerShape(18.dp)),
        content = content,
    )
}

/** 색 아이콘 칸 + 제목/설명 + 스위치. 줄 전체를 눌러도 토글된다. */
@Composable
private fun NotifyRow(icon: ImageVector, fg: Color, bg: Color, title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(desc, fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        GlgSwitch(checked, onToggle)
    }
}

/** 시각 알약 — 누르면 시각 선택. */
@Composable
private fun TimePill(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
        modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(Color.White).border(1.dp, Color(0xFFE3E8E6), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/**
 * 알림 항목 아이콘 — 아이콘만 플랫폼이 정한다(Material ↔ SF Symbols 는 이름 체계가 달라
 * 공유 카탈로그에 담을 수 없다). 제목·설명·묶음은 [NotificationCatalog] 가 갖는다.
 */
private fun notifyIcon(key: NotifyKey): ImageVector = when (key) {
    NotifyKey.BUDGET -> Icons.Default.Savings
    NotifyKey.RESIN -> Icons.Default.Bolt
    NotifyKey.ATTENDANCE -> Icons.Default.EventAvailable
    NotifyKey.PICKUP -> Icons.Default.Event
    NotifyKey.COMBAT -> Icons.Default.MilitaryTech
    NotifyKey.NEWS -> Icons.Default.Campaign
    NotifyKey.HOYOLAND -> Icons.Default.Celebration
}

/**
 * 테마 하위 페이지 — 맨 위 미리보기 카드가 고른 색으로 바로 바뀌고, 아래에 선명 · 차분 두 벌을 나눠 보여준다.
 * (설정 ▸ UI ▸ 테마에서 슬라이드 진입 · iOS ThemeView 파리티 · 목업 design_theme_page_mockup.html B안)
 */
@Composable
private fun ThemeScreen(accentIndex: Int, onSelect: (Int) -> Unit, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val deep = LocalAccentDeep.current
    val current = AccentPalette.getOrElse(accentIndex) { AccentPalette[DEFAULT_ACCENT_INDEX] }
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = glgDetailContentTop(), bottom = 24.dp),
    ) {
        // 미리보기 — 금액(deep) · 게이지(main) · 칩(옅은 면) · 버튼 쌍. 누르는 곳이 아니라 보여주는 곳이다.
        item {
            NotifyCard {
                Column(Modifier.padding(16.dp)) {
                    Text("미리보기 · ${current.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("428,000원", fontSize = 26.sp, fontWeight = FontWeight.Black, color = deep)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFFEEEFF3))) {
                        Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(accent))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("전체", "원신", "스타레일").forEachIndexed { i, label ->
                            val on = i == 0
                            Text(
                                label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = if (on) deep else TextSecondary,
                                modifier = Modifier.clip(RoundedCornerShape(99.dp))
                                    .background(if (on) accent.copy(alpha = 0.14f) else Color(0xFFF4F5F8))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 문구는 버튼 이름이 아니라 모양 이름이다 — 「취소 · 저장하기」는 테마를 저장·되돌리는
                    // 진짜 버튼으로 읽혔다(2026-09-21 지적).
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlgOutlineButton("보조 버튼", onClick = {}, modifier = Modifier.weight(1f), height = 40.dp)
                        GlgButton("강조 버튼", onClick = {}, modifier = Modifier.weight(1f), height = 40.dp)
                    }
                }
            }
        }
        item { NotifyGroupTitle("선명", "${ACCENT_VIVID_COUNT}색") }
        item {
            NotifyCard {
                ThemeColorGrid(accentIndex, 0 until ACCENT_VIVID_COUNT, onSelect)
            }
        }
        item { NotifyGroupTitle("차분", "${AccentPalette.size - ACCENT_VIVID_COUNT}색") }
        item {
            NotifyCard {
                ThemeColorGrid(accentIndex, ACCENT_VIVID_COUNT until AccentPalette.size, onSelect)
            }
            Text(
                "두 벌은 같은 색조 · 다른 채도예요. 게임별 색상과 속성 연출은 테마와 상관없이 그대로예요.",
                fontSize = 11.sp, color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
    GlgDetailHeaderOverlay("테마", onBack, scrolled)
    }
}


@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
}

/** 과소비 넛지 기준 금액 입력 다이얼로그 — 단건 지출이 이 금액 이상이면 확인. */
@Composable
private fun NudgeThresholdDialog(current: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    GlgDialog(
        title = "넛지 기준 금액",
        onDismiss = onDismiss,
        confirmText = "저장",
        onConfirm = { onConfirm(text.toLongOrNull() ?: 0L) },
    ) {
        Column {
            Text("단건 지출이 이 금액 이상이면 추가 전 한 번 더 확인해요.", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            GlgTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() } },
                label = "기준 금액 (원)",
                placeholder = "100000",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 아이콘 + 제목/설명 + 스위치 한 줄 (설정 토글 항목). */
@Composable
private fun SettingsToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        GlgSwitch(checked, onToggle)
    }
}

/** "HH:00" 형식 라벨 (0~23시). */
private fun hourLabel(hour: Int): String = "${hour.coerceIn(0, 23).toString().padStart(2, '0')}:00"

/** 방해금지 시작/종료용 시각 박스 — 라벨 + 큰 시각, 탭하면 피커. */
@Composable
private fun HourBox(label: String, hour: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF6F7F9))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(hourLabel(hour), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

/** 0~23시 선택 다이얼로그 — 6열 그리드 칩. */
@Composable
private fun HourPickerDialog(title: String, current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val accent = LocalAccent.current
    var selected by remember { mutableStateOf(current.coerceIn(0, 23)) }
    GlgDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = "저장",
        onConfirm = { onConfirm(selected) },
    ) {
        Column(
            modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (0..23).chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { h ->
                        val sel = h == selected
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (sel) accent else Color(0xFFF6F7F9))
                                .clickable { selected = h }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                h.toString().padStart(2, '0'),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else TextPrimary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shareCsvFile(context: Context, csv: String) {
    // 캐시 파일로 써서 URI 로 넘긴다 — EXTRA_TEXT 로 본문을 실으면 기록이 많을 때
    // TransactionTooLargeException 으로 앱이 죽었다. 같은 이름을 덮어써 캐시가 쌓이지 않는다.
    val file = java.io.File(context.cacheDir, "exports").apply { mkdirs() }.resolve("gatcha_log_spending.csv")
    file.writeText(csv)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Gatcha LOG 지출 내역")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "지출 내역 내보내기"))
}

/** Compose 의 LocalContext 는 ContextWrapper 로 감싸여 올 수 있어, 호스트 Activity 를 되짚어 찾는다. */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Android 13+ 에서 아직 권한이 없을 때만 OS 프롬프트를 띄운다(12 이하는 권한 개념 자체가 없음).
 * 실제로 띄웠을 때만 notifPermAsked 를 남긴다 — 이 플래그가 '영구 거부' 판별의 근거다(AppSettings 참고).
 */
private fun requestNotifPermIfNeeded(context: Context, launchPermission: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        AppSettings().notifPermAsked = true
        launchPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** 이 앱의 시스템 알림 설정 화면을 연다(권한 영구 거부 시 사용자가 직접 켜도록). */
private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * 빌드 구분칩 — 어떤 빌드가 설치됐는지 한눈에.
 *
 * **EXPERIMENT(빨강)가 최우선**이다. 실험 빌드는 릴리스 구성으로 말아도 릴리스가 아니므로,
 * RELEASE 로 보이면 배포본과 헷갈린다. 표식은 `BuildConfig.EXPERIMENT`(build.gradle.kts)가 정한다.
 */
@Composable
private fun BuildVariantChip() {
    val isDebug = BuildConfig.DEBUG
    val label = if (BuildConfig.EXPERIMENT) "EXPERIMENT" else if (isDebug) "DEBUG" else "RELEASE"
    val color = when {
        BuildConfig.EXPERIMENT -> Color(0xFFE5342A) // 빨강 — 실험 빌드 경고
        isDebug -> Color(0xFFFF7A45)
        else -> LocalAccent.current
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
