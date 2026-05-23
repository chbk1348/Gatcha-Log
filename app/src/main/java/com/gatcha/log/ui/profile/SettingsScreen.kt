package com.gatcha.log.ui.profile

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.game.HoyolabConfigDialog
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
    val gachaStats by viewModel.gachaStats.collectAsState()
    val spendings by viewModel.spendings.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    // 업데이트 확인 등 일회성 메시지 → 토스트
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onGoogleSignInResult(it.data)
    }

    val showBudget = remember { mutableStateOf(false) }
    val showHoyolab = remember { mutableStateOf(false) }
    val showUplog = remember { mutableStateOf(false) }
    val showClearGacha = remember { mutableStateOf(false) }
    val showClearSpend = remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF2F2F6)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("설정", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 계정
        item { SectionTitle("계정") }
        item {
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
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
                        GlgButton("Google로 로그인", onClick = { signInLauncher.launch(viewModel.googleSignInIntent(context)) }, modifier = Modifier.fillMaxWidth())
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
                    SettingsItem("앱 버전", Icons.Default.Info, value = "v1.0") {}
                }
            }
        }
    }

    if (showBudget.value) {
        BudgetSettingDialog(budget, onDismiss = { showBudget.value = false }) { viewModel.setBudget(it); showBudget.value = false }
    }
    if (showHoyolab.value) {
        HoyolabConfigDialog(hoyolab, onDismiss = { showHoyolab.value = false }) { viewModel.updateHoyolabConfig(it); showHoyolab.value = false }
    }
    if (showUplog.value) {
        UplogDialog { showUplog.value = false }
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
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

@Composable
private fun UplogDialog(onDismiss: () -> Unit) {
    GlgDialog(
        title = "업데이트 로그",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UplogEntry(
                "v1.0",
                listOf(
                    "지출·예산·연간 리포트, 구독 관리",
                    "게임 정보: 배너·이벤트·실시간 노트·출석",
                    "가챠 확률표·통합 계산기·천장 카운터",
                    "프로필 쇼케이스(Enka)·가챠 효율 리포트(UIGF/SRGF)",
                    "구글 계정 클라우드 동기화",
                ),
            )
        }
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
