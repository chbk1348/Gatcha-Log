package com.gatcha.log.ui.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.game.HoyolabConfigDialog
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.*

@Composable
fun MyPageScreen(viewModel: SpendingViewModel) {
    val spendings by viewModel.spendings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val account by viewModel.account.collectAsState()

    val showNameDialog = remember { mutableStateOf(false) }
    val showSettings = remember { mutableStateOf(false) }

    if (showSettings.value) {
        SettingsScreen(viewModel) { showSettings.value = false }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("마이페이지", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    GlgCircleIconButton(Icons.Default.Settings, "설정") { showSettings.value = true }
                }
            }
            item {
                UserProfileHeader(
                    name = if (account.isGuest) "게스트" else profile.name,
                    email = if (account.isGuest) "로그인하면 계정별로 분리 저장돼요 (설정 ⚙️)" else profile.email,
                    showEdit = !account.isGuest,
                ) { showNameDialog.value = true }
            }
            item { Spacer(Modifier.height(24.dp)) }
            item { SummaryStatsSection(spendings) }
        }
    }

    if (showNameDialog.value) {
        NameDialog(profile.name, onDismiss = { showNameDialog.value = false }) {
            viewModel.setProfileName(it); showNameDialog.value = false
        }
    }
}

private fun shareCsv(context: android.content.Context, csv: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Gatcha LOG 지출 내역")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(Intent.createChooser(intent, "지출 내역 내보내기"))
}

@Composable
fun UserProfileHeader(name: String, email: String, showEdit: Boolean = true, onEdit: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(email, fontSize = 12.sp, color = TextSecondary)
            }
            if (showEdit) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "이름 변경", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SummaryStatsSection(spendings: List<Spending>) {
    val total = spendings.sumOf { it.amount }
    val count = spendings.size
    val games = spendings.map { it.gameName }.distinct().size

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("총 지출", "₩%,d".format(total), Modifier.weight(1f))
        StatCard("기록 횟수", "${count}회", Modifier.weight(1f))
        StatCard("게임 수", "${games}개", Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier) {
    val accent = LocalAccent.current
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
        }
    }
}

@Composable
fun ThemeSection(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Text("테마 색상", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccentPalette.forEachIndexed { index, option ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(index) }) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(option.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == selectedIndex) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(option.label, fontSize = 10.sp, color = if (index == selectedIndex) option.color else TextSecondary)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    budget: Long,
    hoyolabLinked: Boolean,
    onBudget: () -> Unit,
    onHoyolab: () -> Unit,
    onExport: () -> Unit,
) {
    Text("설정", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SettingsItem("월 예산", Icons.Default.Savings, value = "₩%,d".format(budget), onClick = onBudget)
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem("HoYoLAB 계정 연동", Icons.Default.Link, value = if (hoyolabLinked) "연동됨" else "미연동", onClick = onHoyolab)
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem("데이터 내보내기 (CSV)", Icons.Default.Download, onClick = onExport)
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem("앱 정보", Icons.Default.Info, value = "v1.0", onClick = {})
        }
    }
}

@Composable
fun SettingsItem(label: String, icon: ImageVector, value: String? = null, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            value?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    GlgDialog(
        title = "이름 변경",
        onDismiss = onDismiss,
        confirmText = "저장",
        onConfirm = { if (text.isNotBlank()) onConfirm(text.trim()) },
        confirmEnabled = text.isNotBlank(),
    ) {
        GlgTextField(
            value = text,
            onValueChange = { text = it },
            label = "닉네임",
            placeholder = "닉네임을 입력하세요",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BudgetDialog(current: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    GlgDialog(
        title = "월 예산 설정",
        onDismiss = onDismiss,
        confirmText = "저장",
        onConfirm = { onConfirm(text.toLongOrNull() ?: 0L) },
    ) {
        GlgTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() } },
            label = "예산 (원)",
            placeholder = "0",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}