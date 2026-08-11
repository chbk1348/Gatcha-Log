package com.gatcha.log.ui.profile

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
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.util.SafIO
import kotlinx.coroutines.launch
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
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

    // 설정 하위 페이지 스택: 0=메인, 1=알림 설정, 2=데이터 관리, 3=HoYoLAB 연동, 4=업데이트 로그. 깊어지면 우→좌 슬라이드 push/pop.
    val subPage = when {
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
            showUplog.value -> showUplog.value = false
            showHoyolab.value -> showHoyolab.value = false
            showData.value -> showData.value = false
            showNotif.value -> showNotif.value = false
        }
    }
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
        if (page == 4) {
            UpdateLogScreen(onBack = { showUplog.value = false })
        } else if (page == 3) {
            HoyolabLinkScreen(
                config = hoyolab,
                onSave = { viewModel.updateHoyolabConfig(it); showHoyolab.value = false },
                onBack = { showHoyolab.value = false },
            )
        } else if (page == 2) {
            DataManagementScreen(viewModel, onBack = { showData.value = false })
        } else if (page == 1) {
            NotificationSettingsScreen(viewModel, onBack = { showNotif.value = false })
        } else Box(Modifier.fillMaxSize()) {
        // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
        val listState = rememberLazyListState()
        val scrolled by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        LazyColumn(
            state = listState,
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = glgDetailContentTop(), bottom = 24.dp),
        ) {

        // 1) 알림 — 항목별 알림·방해금지·데일리 요약을 모은 하위 페이지로 진입
        item { SectionTitle("알림") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("알림 설정", Icons.Default.Notifications, value = "방해금지 · 요약 · 항목별") { showNotif.value = true }
                }
            }
        }

        // 2) UI — 표시(컴팩트) + 테마 색상을 한 섹션으로 통합
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("UI") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsToggleRow(
                        Icons.Default.ViewAgenda,
                        "지출 내역 컴팩트 보기",
                        "지출 목록을 한 줄로 빽빽하게 표시해요 (태그·결제수단 숨김)",
                        spendingCompact,
                    ) { viewModel.setSpendingCompact(it) }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(
                        Icons.Default.AutoAwesome,
                        "홈 히어로 글로우",
                        "홈 상단에서 은은하게 떠다니는 빛 효과예요. 끄면 그라데이션만 남아요",
                        heroGlow,
                    ) { viewModel.setHeroGlow(it) }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Palette, null, tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("테마 색상", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    ThemeColorGrid(accentIndex) { viewModel.setAccentIndex(it) }
                }
            }
        }

        // 3) 예산·연동 (계정은 마이페이지 히어로로 일원화 — 중복 카드 제거)
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("예산·연동") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("월 예산", Icons.Default.Savings, value = if (budget > 0) won(budget) else "미설정") { showBudget.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(
                        Icons.Default.Psychology,
                        "과소비 예방 넛지",
                        "지출 추가 시 예산·평소치를 넘으면 한 번 더 확인해요",
                        nudgeOverspend,
                    ) { viewModel.setNudgeOverspend(it) }
                    if (nudgeOverspend) {
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem("넛지 기준 금액", Icons.Default.PriceCheck, value = won(nudgeThreshold)) { showNudgeThreshold.value = true }
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("HoYoLAB 계정 연동", Icons.Default.Link, value = if (hoyolab.isLinked) "연동됨" else "미연동") { showHoyolab.value = true }
                }
            }
        }

        // 4) 데이터 관리 — 백업·복원/내보내기/위험 구역을 모은 하위 페이지로 진입
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("데이터 관리") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("데이터 관리", Icons.Default.Storage, value = "백업·복원 · 초기화") { showData.value = true }
                }
            }
        }

        // 5) 나머지 — 자동화
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("자동화") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.EventAvailable, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("자동 출석체크", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (hoyolab.isLinked) "켜두면 매일 잊지 않고 자동으로 출석을 챙겨드려요 (지금 한 번 바로 시도)"
                            else "HoYoLAB을 연동하면 사용할 수 있어요",
                            fontSize = 11.sp, color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (hoyolab.isLinked) {
                        // 자동 출석 실패 시 알림으로 안내하려면 POST_NOTIFICATIONS(API33+) 권한 필요.
                        // 도즈/스탠바이로 워커가 며칠 누락되는 케이스 회복을 위해 배터리 최적화 화이트리스트도 요청.
                        GlgSwitch(autoCheckIn) { on ->
                            if (on) {
                                ensureNotifPerm()
                                (context as? android.app.Activity)?.let {
                                    com.gatcha.log.data.BatteryOptimization.request(it)
                                }
                            }
                            viewModel.setAutoCheckIn(on)
                        }
                    } else {
                        GlgSwitch(false) { showHoyolab.value = true }
                    }
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
                Spacer(Modifier.height(8.dp))
                GlassCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.BatteryAlert, null, tint = Color(0xFFFB8C00), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("배터리 최적화로 자동 출석이 막힐 수 있어요", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFB8C00))
                            Text(
                                "절전 정책 때문에 며칠씩 자동 출석이 안 되는 분들은 이 앱을 \"제한 안함\"으로 등록해주세요.",
                                fontSize = 11.sp, color = TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        GlgButton(
                            "허용",
                            onClick = {
                                (context as? android.app.Activity)?.let {
                                    com.gatcha.log.data.BatteryOptimization.request(it)
                                }
                            },
                            modifier = Modifier.width(72.dp),
                            height = 36.dp,
                        )
                    }
                }
            }
        }

        // 앱 정보
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("앱 정보") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("업데이트 확인", Icons.Default.SystemUpdate) { viewModel.checkForUpdate(manual = true) }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("업데이트 로그", Icons.Default.NewReleases) { showUplog.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("출처 · 저작권", Icons.Default.Copyright) { showCredits.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("GitHub", ImageVector.vectorResource(R.drawable.ic_github)) {
                        openExternalLink(ctx, GITHUB_REPO_URL)
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("앱 버전", Icons.Default.Info, value = "v$versionName", trailing = { BuildVariantChip() }) {}
                }
            }
        }
        }
        GlgDetailHeaderOverlay("설정", onBack, scrolled)
        }
    }

    if (showBudget.value) {
        BudgetDialog(
            overall = budget,
            gameBudgets = gameBudgets,
            monthlyTotals = monthlyTotalsByGame,
            onDismiss = { showBudget.value = false },
            onConfirm = { o, perGame -> viewModel.setBudgets(o, perGame); showBudget.value = false },
        )
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

        // 백업·복원 — 데이터 보호가 가장 중요하므로 맨 위에 (재설치·기기 변경 대비)
        item { SectionTitle("백업·복원") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("백업 파일 내보내기", Icons.Default.Backup, value = "전체 데이터") {
                        val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                        exportBackupLauncher.launch("gatchalog-backup-$date.json")
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("백업 파일에서 복원", Icons.Default.Restore) { showImportBackup.value = true }
                }
            }
            Text(
                "구글 로그인 없이도 전체 데이터(가챠 기록 포함)를 파일로 저장해 두면, 앱을 재설치하거나 기기를 바꿔도 복원할 수 있어요.",
                fontSize = 11.sp, color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }

        // 내보내기
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("내보내기") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("지출 내역 내보내기 (CSV)", Icons.Default.Download) { shareCsvFile(context, viewModel.buildCsv()) }
                }
            }
        }

        // 위험 구역 — 되돌릴 수 없는 파괴 작업은 빨간 톤으로 시각 분리
        item { Spacer(Modifier.height(20.dp)) }
        item { DangerSectionTitle("위험 구역") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    DangerItem(
                        "가챠 기록 초기화",
                        Icons.Default.DeleteSweep,
                        value = gachaStats?.let { "${it.total}건" } ?: "없음",
                    ) { if (gachaStats != null) showClearGacha.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    DangerItem(
                        "지출 전체 삭제",
                        Icons.Default.DeleteForever,
                        value = "${spendings.size}건",
                    ) { if (spendings.isNotEmpty()) showClearSpend.value = true }
                }
            }
            Text(
                "되돌릴 수 없는 작업이에요. 먼저 위 ‘백업 파일 내보내기’로 백업을 권장해요.",
                fontSize = 11.sp, color = DangerRed,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
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

/** 위험 구역 섹션 제목 — 빨간 톤(SectionTitle 의 파괴 작업용 변형). */
@Composable
private fun DangerSectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DangerRed, modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
}

/** 위험 구역 행 — 빨간 아이콘/제목 + 우측 값·셰브론 (되돌릴 수 없는 파괴 작업 강조). */
@Composable
private fun DangerItem(label: String, icon: ImageVector, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DangerRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DangerRed)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            value?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = DangerRed.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
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
    val notifySubscription by viewModel.notifySubscription.collectAsStateWithLifecycle()
    val notifyNews by viewModel.notifyNews.collectAsStateWithLifecycle()
    val notifyCombat by viewModel.notifyCombat.collectAsStateWithLifecycle()
    val notifyDndEnabled by viewModel.notifyDndEnabled.collectAsStateWithLifecycle()
    val notifyDndStartHour by viewModel.notifyDndStartHour.collectAsStateWithLifecycle()
    val notifyDndEndHour by viewModel.notifyDndEndHour.collectAsStateWithLifecycle()
    val notifyDailySummary by viewModel.notifyDailySummary.collectAsStateWithLifecycle()
    val notifyDailySummaryHour by viewModel.notifyDailySummaryHour.collectAsStateWithLifecycle()

    // 알림 권한(Android 13+) — 알림 토글 켤 때 요청.
    // permRefresh: 권한 요청/화면 복귀 후 권한 상태를 다시 읽게 하는 트리거(권한은 Compose 상태가 아니라 OS 상태).
    var permRefresh by remember { mutableIntStateOf(0) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permRefresh++
    }
    val ensureNotifPerm: () -> Unit = { requestNotifPermIfNeeded(context, notifPermLauncher::launch) }
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

    // 알림 시각 피커(0~23시) — 방해금지 시작/종료, 데일리 요약 시각
    val showDndStartPicker = remember { mutableStateOf(false) }
    val showDndEndPicker = remember { mutableStateOf(false) }
    val showSummaryPicker = remember { mutableStateOf(false) }

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

        // 알림 — 항목별 토글
        item { SectionTitle("알림") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsToggleRow(Icons.Default.Savings, "예산 알림", "이번 달 예산 90%·초과 시 알려줘요", notifyBudget) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyBudget(on)
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(Icons.Default.EventAvailable, "출석 리마인더", "저녁까지 미출석이면 알려줘요", notifyAttendance) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyAttendance(on)
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(Icons.Default.Bolt, "행동력 가득참 알림", "레진·개척력·배터리가 가득 차면 알려줘요", notifyResin) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyResin(on)
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(Icons.Default.Event, "픽업 마감 알림", "진행 중인 픽업이 끝나기 전에 알려줘요", notifyPickup) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyPickup(on)
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(Icons.Default.MilitaryTech, "전투 시즌 마감 알림", "나선 비경·혼돈의 기억 등을 못 깬 채 시즌이 끝나기 전에 알려줘요", notifyCombat) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyCombat(on)
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(Icons.Default.Autorenew, "정기결제 갱신 알림", "구독 결제 하루 전(D-1)에 알려줘요", notifySubscription) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifySubscription(on)
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(Icons.Default.Campaign, "새 공지 알림", "게임에 새 공지가 올라오면 알려줘요", notifyNews) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyNews(on)
                    }
                }
            }
            // 토글은 켰는데 시스템 알림 권한이 꺼져 있으면 안내.
            val notifOn = notifyBudget || notifyAttendance || notifyResin || notifyPickup
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
                Spacer(Modifier.height(8.dp))
                GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.NotificationsOff, null, tint = Color(0xFFFB8C00), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("알림 권한이 꺼져 있어요", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFB8C00))
                            Text(
                                if (canPromptNotifPerm) "알림을 받으려면 권한을 허용해주세요."
                                else "권한이 막혀 있어 알림이 표시되지 않아요. 시스템 설정에서 알림을 켜주세요.",
                                fontSize = 11.sp, color = TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        GlgButton(
                            if (canPromptNotifPerm) "허용" else "설정",
                            onClick = {
                                if (canPromptNotifPerm) ensureNotifPerm() else openAppNotificationSettings(context)
                            },
                            modifier = Modifier.width(72.dp),
                            height = 36.dp,
                        )
                    }
                }
            }
        }

        // 방해금지 — 지정한 시간대엔 알림을 억제
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("방해금지") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsToggleRow(
                        Icons.Default.Bedtime,
                        "방해금지 시간",
                        "이 시간대엔 알림을 보내지 않아요",
                        notifyDndEnabled,
                    ) { viewModel.setNotifyDndEnabled(it) }
                    if (notifyDndEnabled) {
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HourBox("시작", notifyDndStartHour, Modifier.weight(1f)) { showDndStartPicker.value = true }
                            Text("~", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            HourBox("종료", notifyDndEndHour, Modifier.weight(1f)) { showDndEndPicker.value = true }
                        }
                        Text(
                            "자정을 넘는 시간대도 지원 · 기준 기기 로컬 시각",
                            fontSize = 11.sp, color = TextSecondary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        )
                    }
                }
            }
        }

        // 데일리 요약 — 흩어진 알림을 묶어 하루 한 번 발송
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("데일리 요약") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsToggleRow(
                        Icons.Default.MarkEmailRead,
                        "하루 한 번 요약",
                        "흩어진 알림을 묶어 1건으로 보내요",
                        notifyDailySummary,
                    ) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyDailySummary(on)
                    }
                    if (notifyDailySummary) {
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsItem("요약 보낼 시각", Icons.Default.Schedule, value = hourLabel(notifyDailySummaryHour)) { showSummaryPicker.value = true }
                    }
                }
            }
            Text(
                "요약을 켜면 그날 개별 알림은 묶어 한 건으로 발송하고, 끄면 기존처럼 개별 발송해요.",
                fontSize = 11.sp, color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
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
    if (showSummaryPicker.value) {
        HourPickerDialog("요약 보낼 시각", notifyDailySummaryHour, { showSummaryPicker.value = false }) {
            viewModel.setNotifyDailySummaryHour(it); showSummaryPicker.value = false
        }
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
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Gatcha LOG 지출 내역")
        putExtra(Intent.EXTRA_TEXT, csv)
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
