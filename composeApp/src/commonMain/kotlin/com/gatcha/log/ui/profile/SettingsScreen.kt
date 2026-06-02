package com.gatcha.log.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.ui.components.BudgetDialog
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgAlert
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.components.subPageBottomInset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.gatcha.log.ui.components.ProfileAvatar
import com.gatcha.log.ui.game.HoyolabLinkScreen
import com.gatcha.log.ui.platform.rememberFileOpenLauncher
import com.gatcha.log.ui.platform.rememberFileSaveLauncher
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.currentTimeMillis
import com.gatcha.log.util.won

@Composable
fun SettingsScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val account by viewModel.account.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val gameBudgets by viewModel.gameBudgets.collectAsState()
    val accentIndex by viewModel.accentIndex.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()
    val autoCheckIn by viewModel.autoCheckIn.collectAsState()
    val notifyBudget by viewModel.notifyBudget.collectAsState()
    val notifyAttendance by viewModel.notifyAttendance.collectAsState()
    val notifyResin by viewModel.notifyResin.collectAsState()
    val notifyWish by viewModel.notifyWish.collectAsState()
    val nudgeOverspend by viewModel.nudgeOverspend.collectAsState()
    val nudgeThreshold by viewModel.nudgeThreshold.collectAsState()
    val gachaStats by viewModel.gachaStats.collectAsState()
    val spendings by viewModel.spendings.collectAsState()
    val versionName = remember { com.gatcha.log.data.api.UpdateChecker.currentVersionName() }
    // 상태 메시지 토스트는 상위 HomeScreen 의 전역 GlgStatusToast 가 처리

    // 백업 파일 내보내기/가져오기 — KMP 파일 런처(SAF/문서 피커의 commonMain 추상화).
    // 내보내기: 저장 시점에 ViewModel 이 백업 JSON 문자열을 만들어 파일로 기록.
    val exportBackupLauncher = rememberFileSaveLauncher(
        defaultName = "gatchalog-backup-${DateUtil.dayKey(currentTimeMillis())}.json",
    ) { viewModel.exportBackupContent() }
    // 가져오기: 선택한 파일 내용(첫 번째)을 JSON 문자열로 받아 복원.
    val importBackupLauncher = rememberFileOpenLauncher { contents ->
        contents.firstOrNull()?.let { viewModel.importBackupFromContent(it) }
    }
    // 지출 내역 CSV 내보내기 — 원본은 ACTION_SEND 공유였으나 KMP commonMain 에선 파일 저장으로 대체.
    val exportCsvLauncher = rememberFileSaveLauncher(
        defaultName = "gatchalog-spending-${DateUtil.dayKey(currentTimeMillis())}.csv",
    ) { viewModel.buildCsv() }
    // 알림 권한(Android 13+) 요청은 알림 토글 ON 시 ViewModel 의 네이티브 연동에서 처리.
    val ensureNotifPerm: () -> Unit = { }

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
    val showClearGacha = remember { mutableStateOf(false) }
    val showClearSpend = remember { mutableStateOf(false) }
    val showImportBackup = remember { mutableStateOf(false) }
    val showCredits = remember { mutableStateOf(false) }

    // HoYoLAB 연동 페이지 — 화면 스왑(설정 ↔ 연동) 슬라이드 push/pop
    AnimatedContent(
        targetState = showHoyolab.value,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(220)))
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
            // 하단바 미노출 페이지 — Android 는 네비 인셋, iOS 는 인셋 없이 화면 끝까지
            modifier = Modifier.fillMaxSize().subPageBottomInset().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { GlgScreenHeader("설정", onBack) }

        // 계정
        item { SectionTitle("계정") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(photoUrl = if (account.isGuest) null else account.photoUrl, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (account.isGuest) "게스트" else account.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (account.isGuest) "게스트 — 동기화 꺼짐" else "구글 계정 동기화 켜짐",
                                fontSize = 12.sp,
                                color = if (account.isGuest) TextSecondary else accent,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (account.isGuest) {
                        GlgButton("Google로 로그인", onClick = { viewModel.signIn() }, modifier = Modifier.fillMaxWidth())
                    } else {
                        GlgOutlineButton("로그아웃", onClick = { viewModel.signOut() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // 화면(테마)
        item { Spacer(Modifier.height(20.dp)) }
        item { ThemeSection(accentIndex) { viewModel.setAccentIndex(it) } }

        // 예산·연동
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
                        // (알림 권한·배터리 최적화 화이트리스트는 플랫폼 측 setAutoCheckIn 처리에 위임)
                        GlgSwitch(autoCheckIn) { on ->
                            viewModel.setAutoCheckIn(on)
                        }
                    } else {
                        GlgSwitch(false) { showHoyolab.value = true }
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
                    SettingsToggleRow(Icons.Default.Star, "위시 픽업 알림", "위시리스트 캐릭터가 픽업 배너에 등장하면 알려줘요", notifyWish) { on ->
                        if (on) ensureNotifPerm(); viewModel.setNotifyWish(on)
                    }
                }
            }
        }

        // 데이터
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("데이터") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("지출 내역 내보내기 (CSV)", Icons.Default.Download) { exportCsvLauncher() }
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
                        exportBackupLauncher()
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

        // 정보
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("정보") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsItem("업데이트 확인", Icons.Default.SystemUpdate) { viewModel.checkForUpdate(manual = true) }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("업데이트 로그", Icons.Default.NewReleases) { showUplog.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("출처 · 저작권", Icons.Default.Copyright) { showCredits.value = true }
                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem("앱 버전", Icons.Default.Info, value = "v$versionName") {}
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
        UplogDialog(versionName) { showUplog.value = false }
    }
    if (showCredits.value) {
        CreditsDialog { showCredits.value = false }
    }
    if (showClearGacha.value) {
        GlgAlert(
            title = "가챠 기록 초기화",
            message = "가져온 모든 가챠 기록을 삭제할까요? 이 작업은 되돌릴 수 없어요.",
            onDismiss = { showClearGacha.value = false },
            confirmText = "초기화",
            onConfirm = { viewModel.clearGachaRecords(); showClearGacha.value = false },
            destructive = true,
        )
    }
    if (showClearSpend.value) {
        GlgAlert(
            title = "지출 전체 삭제",
            message = "모든 지출 기록(${spendings.size}건)을 삭제할까요? 이 작업은 되돌릴 수 없어요.",
            onDismiss = { showClearSpend.value = false },
            confirmText = "삭제",
            onConfirm = { viewModel.clearSpendings(); showClearSpend.value = false },
            destructive = true,
        )
    }
    if (showImportBackup.value) {
        GlgAlert(
            title = "백업 파일에서 복원",
            message = "백업 파일을 선택해 복원할까요? 백업에 들어 있는 항목은 현재 데이터를 덮어씁니다.",
            onDismiss = { showImportBackup.value = false },
            confirmText = "파일 선택",
            onConfirm = {
                showImportBackup.value = false
                importBackupLauncher()
            },
        )
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
