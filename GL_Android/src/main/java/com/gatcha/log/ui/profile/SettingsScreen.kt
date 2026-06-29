package com.gatcha.log.ui.profile

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.gatcha.log.BuildConfig
import com.gatcha.log.ui.components.BudgetDialog
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.gatcha.log.ui.game.HoyolabLinkScreen
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.util.SafIO
import kotlinx.coroutines.launch
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.glgShortSpec
import com.gatcha.log.ui.theme.glgStandardSpec
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

@Composable
fun SettingsScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val budget by viewModel.budget.collectAsState()
    val gameBudgets by viewModel.gameBudgets.collectAsState()
    val accentIndex by viewModel.accentIndex.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()
    val autoCheckIn by viewModel.autoCheckIn.collectAsState()
    val notifyBudget by viewModel.notifyBudget.collectAsState()
    val notifyAttendance by viewModel.notifyAttendance.collectAsState()
    val notifyResin by viewModel.notifyResin.collectAsState()
    val notifyPickup by viewModel.notifyPickup.collectAsState()
    val nudgeOverspend by viewModel.nudgeOverspend.collectAsState()
    val nudgeThreshold by viewModel.nudgeThreshold.collectAsState()
    val spendingCompact by viewModel.spendingCompact.collectAsState()
    val gachaStats by viewModel.gachaStats.collectAsState()
    val spendings by viewModel.spendings.collectAsState()
    val versionName = remember { com.gatcha.log.data.api.UpdateChecker.currentVersionName() }
    // 상태 메시지 토스트는 상위 HomeScreen 의 전역 GlgStatusToast 가 처리

    // 백업 파일 내보내기/가져오기 (SAF)
    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { u -> scope.launch { viewModel.exportBackupContent()?.let { json -> SafIO.writeText(context, u, json) } } }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> scope.launch { SafIO.readText(context, u)?.let { viewModel.importBackupFromContent(it) } } }
    }
    // 알림 권한(Android 13+) — 알림 토글 켤 때 요청
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ensureNotifPerm: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val showBudget = remember { mutableStateOf(false) }
    val showNudgeThreshold = remember { mutableStateOf(false) }
    val showHoyolab = remember { mutableStateOf(false) }

    // 홈 만료 배너 CTA → 마이페이지 → 설정 → HoYoLAB 연동까지 자동 진입(C4 흐름).
    val pendingOpenHoyolab by viewModel.pendingOpenHoyolabLink.collectAsState()
    LaunchedEffect(pendingOpenHoyolab) {
        if (pendingOpenHoyolab) {
            showHoyolab.value = true
            viewModel.consumePendingOpenHoyolabLink()
        }
    }
    val showUplog = remember { mutableStateOf(false) }
    // 파괴작업은 2단계 확인: 1차(백업 권장 안내) → 2차(최종 확인)
    val showClearGacha = remember { mutableStateOf(false) }
    val showClearGacha2 = remember { mutableStateOf(false) }
    val showClearSpend = remember { mutableStateOf(false) }
    val showClearSpend2 = remember { mutableStateOf(false) }
    val showImportBackup = remember { mutableStateOf(false) }
    val showCredits = remember { mutableStateOf(false) }

    // HoYoLAB 연동 페이지 — 화면 스왑(설정 ↔ 연동) 슬라이드 push/pop
    AnimatedContent(
        targetState = showHoyolab.value,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(glgStandardSpec()) { it } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { -it / 4 } + fadeOut(glgShortSpec()))
            } else {
                (slideInHorizontally(glgStandardSpec()) { -it / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { it } + fadeOut(glgShortSpec()))
            }
        },
        label = "hoyoLink",
    ) { link ->
        if (link) {
            HoyolabLinkScreen(
                config = hoyolab,
                onSave = { viewModel.updateHoyolabConfig(it); showHoyolab.value = false },
                onBack = { showHoyolab.value = false },
            )
        } else LazyColumn(
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { GlgScreenHeader("설정", onBack) }

        // ── 1층: 자주 쓰는 설정 ── (계정은 마이페이지 히어로로 일원화 — 중복 카드 제거)
        item { TierTitle("자주 쓰는 설정") }
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

        // 표시
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("표시") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsToggleRow(
                        Icons.Default.ViewAgenda,
                        "지출 내역 컴팩트 보기",
                        "지출 목록을 한 줄로 빽빽하게 표시해요 (태그·결제수단 숨김)",
                        spendingCompact,
                    ) { viewModel.setSpendingCompact(it) }
                }
            }
        }

        // 자동화
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
            val ignoring = remember(autoCheckIn) {
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

        // 알림
        item { Spacer(Modifier.height(20.dp)) }
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
                    SettingsToggleRow(Icons.Default.Bolt, "재화 가득참 알림", "레진·개척력·배터리가 가득 차면 알려줘요", notifyResin) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyResin(on)
                    }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(Icons.Default.Event, "픽업 마감 알림", "진행 중인 픽업이 끝나기 전에 알려줘요", notifyPickup) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyPickup(on)
                    }
                }
            }
            // 토글은 켰는데 시스템 알림 권한이 꺼져 있으면 안내(영구 거부 시 시스템 다이얼로그가 안 떠서 사용자가 인지 못 함).
            val notifOn = notifyBudget || notifyAttendance || notifyResin || notifyPickup
            val notifEnabled = remember(notifyBudget, notifyAttendance, notifyResin) {
                com.gatcha.log.data.Notifier.notificationsEnabled()
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
                            Text("권한이 막혀 있어 알림이 표시되지 않아요. 시스템 설정에서 알림을 켜주세요.", fontSize = 11.sp, color = TextSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        GlgButton(
                            "설정",
                            onClick = { openAppNotificationSettings(context) },
                            modifier = Modifier.width(72.dp),
                            height = 36.dp,
                        )
                    }
                }
            }
        }

        // 화면(테마) — 1층 하단으로 이동
        item { Spacer(Modifier.height(20.dp)) }
        item { ThemeSection(accentIndex) { viewModel.setAccentIndex(it) } }

        // ── 2층: 데이터·계정 ──
        item { Spacer(Modifier.height(28.dp)) }
        item { TierTitle("데이터·계정") }
        item { SectionTitle("데이터") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("지출 내역 내보내기 (CSV)", Icons.Default.Download) { shareCsvFile(context, viewModel.buildCsv()) }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        "가챠 기록 초기화",
                        Icons.Default.DeleteSweep,
                        value = gachaStats?.let { "${it.total}건" } ?: "없음",
                    ) { if (gachaStats != null) showClearGacha.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        "지출 전체 삭제",
                        Icons.Default.DeleteForever,
                        value = "${spendings.size}건",
                    ) { if (spendings.isNotEmpty()) showClearSpend.value = true }
                }
            }
        }

        // 백업·복원 (재설치·기기 변경 대비)
        item { Spacer(Modifier.height(20.dp)) }
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
        }
        item {
            Text(
                "구글 로그인 없이도 전체 데이터(가챠 기록 포함)를 파일로 저장해 두면, 앱을 재설치하거나 기기를 바꿔도 복원할 수 있어요.",
                fontSize = 11.sp, color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }

        // ── 3층: 앱 정보 ──
        item { Spacer(Modifier.height(28.dp)) }
        item { TierTitle("앱 정보") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("업데이트 확인", Icons.Default.SystemUpdate) { viewModel.checkForUpdate(manual = true) }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("업데이트 로그", Icons.Default.NewReleases) { showUplog.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("출처 · 저작권", Icons.Default.Copyright) { showCredits.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("앱 버전", Icons.Default.Info, value = "v$versionName", trailing = { BuildVariantChip() }) {}
                }
            }
        }
        }
    }

    if (showBudget.value) {
        BudgetDialog(
            overall = budget,
            gameBudgets = gameBudgets,
            monthlyTotals = viewModel.monthlyTotalsByGame(),
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
    if (showUplog.value) {
        UpdateLogScreen(onBack = { showUplog.value = false })
    }
    if (showCredits.value) {
        CreditsDialog { showCredits.value = false }
    }
    // 가챠 기록 초기화 — 1단계(백업 권장 안내)
    if (showClearGacha.value) {
        GlgDialog(
            title = "가챠 기록 초기화",
            onDismiss = { showClearGacha.value = false },
            confirmText = "계속",
            onConfirm = { showClearGacha.value = false; showClearGacha2.value = true },
        ) {
            Text("가져온 모든 가챠 기록을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘데이터·계정 → 백업 파일 내보내기’로 백업을 권장해요.", fontSize = 13.sp, color = TextSecondary)
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
            Text("모든 지출 기록(${spendings.size}건)을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘데이터·계정 → 백업 파일 내보내기’로 백업을 권장해요.", fontSize = 13.sp, color = TextSecondary)
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

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
}

/** 빈도별 묶음(자주 쓰는 설정 / 데이터·계정 / 앱 정보) 상단 헤더 — SectionTitle 보다 큰 1차 그룹 제목. */
@Composable
private fun TierTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 2.dp))
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

private fun shareCsvFile(context: Context, csv: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Gatcha LOG 지출 내역")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(Intent.createChooser(intent, "지출 내역 내보내기"))
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

/** 빌드 타입(디버그/릴리스) 구분칩 — 어떤 빌드가 설치됐는지 한눈에. */
@Composable
private fun BuildVariantChip() {
    val isDebug = BuildConfig.DEBUG
    val label = if (isDebug) "DEBUG" else "RELEASE"
    val color = if (isDebug) Color(0xFFFF7A45) else LocalAccent.current
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
