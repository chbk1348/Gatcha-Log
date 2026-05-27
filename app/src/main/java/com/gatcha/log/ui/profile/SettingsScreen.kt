package com.gatcha.log.ui.profile

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.gatcha.log.BuildConfig
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.ProfileAvatar
import com.gatcha.log.ui.game.HoyolabLinkScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary

@Composable
fun SettingsScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val account by viewModel.account.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val accentIndex by viewModel.accentIndex.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()
    val autoCheckIn by viewModel.autoCheckIn.collectAsState()
    val notifyBudget by viewModel.notifyBudget.collectAsState()
    val notifyAttendance by viewModel.notifyAttendance.collectAsState()
    val notifyResin by viewModel.notifyResin.collectAsState()
    val notifyWish by viewModel.notifyWish.collectAsState()
    val gachaStats by viewModel.gachaStats.collectAsState()
    val spendings by viewModel.spendings.collectAsState()
    val versionName = remember { com.gatcha.log.data.api.UpdateChecker.currentVersionName(context) }
    // 상태 메시지 토스트는 상위 HomeScreen 의 전역 GlgStatusToast 가 처리

    // 백업 파일 내보내기/가져오기 (SAF)
    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackupToUri(it) }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackupFromUri(it) }
    }
    // 알림 권한(Android 13+) — 알림 토글 켤 때 요청
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ensureNotifPerm: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val showBudget = remember { mutableStateOf(false) }
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
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
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
                        GlgButton("Google로 로그인", onClick = { (context as? android.app.Activity)?.let { viewModel.signIn(it) } }, modifier = Modifier.fillMaxWidth())
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
                    SettingsItem("월 예산", Icons.Default.Savings, value = if (budget > 0) "₩%,d".format(budget) else "미설정") { showBudget.value = true }
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
                        GlgSwitch(autoCheckIn) { on ->
                            if (on) ensureNotifPerm()
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
                    SettingsItem("앱 버전", Icons.Default.Info, value = "v$versionName", trailing = { BuildVariantChip() }) {}
                }
            }
        }
        }
    }

    if (showBudget.value) {
        BudgetSettingDialog(budget, onDismiss = { showBudget.value = false }) { viewModel.setBudget(it); showBudget.value = false }
    }
    if (showUplog.value) {
        UplogDialog(versionName) { showUplog.value = false }
    }
    if (showCredits.value) {
        CreditsDialog { showCredits.value = false }
    }
    if (showClearGacha.value) {
        GlgDialog(
            title = "가챠 기록 초기화",
            onDismiss = { showClearGacha.value = false },
            confirmText = "초기화",
            onConfirm = { viewModel.clearGachaRecords(); showClearGacha.value = false },
        ) {
            Text("가져온 모든 가챠 기록을 삭제할까요? 이 작업은 되돌릴 수 없어요.", fontSize = 13.sp, color = TextSecondary)
        }
    }
    if (showClearSpend.value) {
        GlgDialog(
            title = "지출 전체 삭제",
            onDismiss = { showClearSpend.value = false },
            confirmText = "삭제",
            onConfirm = { viewModel.clearSpendings(); showClearSpend.value = false },
        ) {
            Text("모든 지출 기록(${spendings.size}건)을 삭제할까요? 이 작업은 되돌릴 수 없어요.", fontSize = 13.sp, color = TextSecondary)
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

@Composable
private fun BudgetSettingDialog(current: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    GlgDialog(
        title = "월 예산 설정",
        onDismiss = onDismiss,
        confirmText = "저장",
        onConfirm = { onConfirm(text.toLongOrNull() ?: 0L) },
    ) {
        com.gatcha.log.ui.components.GlgTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() } },
            label = "예산 (원)",
            placeholder = "0",
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
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

@Composable
private fun UplogDialog(versionName: String, onDismiss: () -> Unit) {
    GlgDialog(
        title = "업데이트 로그",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UplogEntry(
                "v${versionName.ifBlank { "27.6.0" }}",
                listOf(
                    "혹시 자동 출석이 안 되고 계셨다면 — 자동 출석 토글을 한 번 껐다 다시 켜주세요 (알림 권한 안내가 떠요)",
                    "자동 출석 토글을 켜면 결과를 바로 알려드려요 — 완료·이미 완료·재연동 필요 등",
                    "자동 출석 실패 시 사유별 안내 — 재연동 필요·네트워크·기타로 구분",
                    "원신 창세의 결정·스타레일 오래된 꿈 아이콘을 정확한 이미지로 교체",
                    "알림 아이콘이 일부 단말(픽셀 등)에서 안 보이던 문제 수정",
                    "출처·저작권 안내에 명조(Kuro Games)·엔드필드(Hypergryph / Yostar) 권리자 추가",
                ),
            )
            UplogEntry(
                "v27.5.6",
                listOf(
                    "HoYoLAB 토큰을 단말기 내 암호화 저장소로 이관 — 클라우드 평문 보관 제거",
                    "비공식 앱 안내·토큰 로컬 보관 안내 등 면책 고지 보강",
                    "게임 재화 아이콘을 권리자 CDN에서 불러오도록 변경 — 앱 용량 축소",
                    "내부 안정성 보강(앱 실행 단계 회귀 방지)",
                ),
            )
            UplogEntry(
                "v27.5.3",
                listOf(
                    "설정 ▸ 앱 버전에 빌드 종류(DEBUG/RELEASE) 표시 칩 추가",
                ),
            )
            UplogEntry(
                "v27.5.2",
                listOf(
                    "연간 리포트·알림 상세 등 전체 화면 페이지에서 하단바와 + 버튼 자동 숨김",
                    "하단바가 차지하던 빈 여백 정리 — 콘텐츠를 더 넓게",
                ),
            )
            UplogEntry(
                "v27.5.1",
                listOf(
                    "[핫픽스] 리딤코드 교환 시 '쿠키 인증 필요' 오류 수정 — HoYoLAB 재연동(이메일 로그인) 후 정상 교환",
                ),
            )
            UplogEntry(
                "v27.5.0",
                listOf(
                    "HoYoLAB 로그인 한 번으로 토큰·게임 UID 자동 입력 (수동 복사 불필요)",
                    "HoYoLAB 리딤코드 자동 수집 + 한 번에 교환",
                    "HoYoLAB 계정 연동을 전용 페이지로 개편 (전환 애니메이션·연동 안내)",
                    "스타레일 등 UID가 부계정으로 잘못 채워지던 문제 수정 (대표 계정 우선)",
                ),
            )
            UplogEntry(
                "v27.4.2",
                listOf(
                    "[핫픽스] 지출 저장·삭제·수정 직후 당겨서 새로고침하면 변경이 사라지던 문제 수정",
                ),
            )
            UplogEntry(
                "v27.4.1",
                listOf(
                    "업데이트 확인이 잘 되지 않던 문제 수정 (버전 정보 캐시 우회)",
                    "지출 추가 모달 글씨 크기·가독성 개선",
                    "빠른 상품 선택에 분류 칩(월정액·패스·재화) 추가",
                    "명조·엔드필드·이환 정확한 가격 반영 + 게임별 재화 아이콘 (이환 신규 추가)",
                ),
            )
            UplogEntry(
                "v27.4.0",
                listOf(
                    "재설치·기기 변경 후 데이터 복원 안정화 — 같은 구글 계정으로 로그인하면 가챠 기록까지 복원",
                    "백업 파일 내보내기/복원 추가 (설정 ▸ 백업·복원) — 로그인 없이도 전체 데이터 보관",
                    "로그인 방식 개선 (Credential Manager) — 더 매끄러운 계정 선택",
                    "오프라인에서 앱이 로딩 화면에 멈추던 문제 수정",
                    "버전 호환성 개선으로 클라우드 데이터 보호 강화",
                ),
            )
            UplogEntry(
                "v27.3.1",
                listOf(
                    "[핫픽스] 화면이 짧은 단말에서 연동·예산 등 다이얼로그가 잘려 취소·저장 버튼이 안 보이던 문제 수정 (본문 스크롤)",
                ),
            )
            UplogEntry(
                "v27.3.0",
                listOf(
                    "지출 내역 실제 인게임 재화 아이콘 + 상세 버튼/페이지",
                    "HoYoLAB 연동 정보 구글 계정 동기화 안정화 (다른 기기 토큰 복원·자가 복구)",
                    "출석 기준 시간 베이징 표준시(UTC+8)로 정정 — 자정 직후 오출석 방지",
                    "출석 최근 7일 스트립 + 월간 달력",
                    "인앱 자동 업데이트 (다운로드→설치→자동 삭제)",
                    "HoYoLAB 리딤코드 교환, 젠레스 픽업 배너",
                    "데일리 인게임 용어·재화 명칭 정정 (선계 화폐 등)",
                    "하단 탭 다시 누르면 최상단 이동, 출처·저작권 고지",
                ),
            )
            UplogEntry(
                "v27.2.0",
                listOf(
                    "마이페이지 프로필 대시보드로 대개편",
                    "지출 상세 · 연간 리포트 · 알림 상세 페이지 추가",
                    "알림 읽음 처리 + 바로가기(액션형 알림)",
                    "프로필 쇼케이스 캐릭터 아이콘·원소 표시",
                    "화면 전환 애니메이션, 버튼·뒤로가기 디자인 통일",
                ),
            )
        }
    }
}

/** 출처·저작권 고지 — 비상업·비공식 팬 프로젝트, 게임 자료의 권리자 명시 + 권리자 요청 시 즉시 삭제. */
@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    GlgDialog(
        title = "출처 · 저작권",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "본 앱은 개인이 만든 비상업·비공식 팬 프로젝트로 HoYoverse와 무관하며 공식 서비스가 아닙니다.",
                fontSize = 13.sp, color = TextSecondary,
            )
            CreditRow(
                "게임 콘텐츠 · 아이콘 저작권",
                "© HoYoverse (miHoYo / Cognosphere) — 원신 · 붕괴: 스타레일 · 젠레스 존 제로\n" +
                    "© Kuro Games — 명조: 워더링 웨이브\n" +
                    "© Hypergryph / Yostar — 명일방주: 엔드필드",
            )
            CreditRow(
                "데이터 · 에셋 출처",
                "enka.network · Project Amber (yatta.moe)\nHoYoLAB · ennead.cc",
            )
            Text(
                "모든 게임 콘텐츠의 권리는 각 권리자에게 있으며, 권리자의 요청이 있을 경우 즉시 해당 자료를 삭제합니다.",
                fontSize = 12.sp, color = TextSecondary,
            )
        }
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    val accent = LocalAccent.current
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun UplogEntry(version: String, items: List<String>) {
    val accent = LocalAccent.current
    Column {
        Text(version, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(6.dp))
        items.forEach {
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text("· ", fontSize = 13.sp, color = TextSecondary)
                Text(it, fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}
