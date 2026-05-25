package com.gatcha.log.ui.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.ProfileAvatar
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.*

@Composable
fun MyPageScreen(
    viewModel: SpendingViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val spendings by viewModel.spendings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val account by viewModel.account.collectAsState()
    val attendanceStreak by viewModel.attendanceStreak.collectAsState()
    val gachaStats by viewModel.gachaStats.collectAsState()
    val context = LocalContext.current

    val showSettings = remember { mutableStateOf(false) }

    // 설정 페이지에서 시스템/제스처 뒤로가기 시 홈이 아니라 마이페이지로 복귀
    BackHandler(enabled = showSettings.value) { showSettings.value = false }

    val monthlyTotal = remember(spendings) { viewModel.monthlyTotal() }
    val total = remember(spendings) { spendings.sumOf { it.amount } }
    val games = remember(spendings) { spendings.map { it.gameName }.distinct().size }
    val gachaTotal = gachaStats?.total ?: 0

    AnimatedContent(
        targetState = showSettings.value,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState) {
                // 설정 열기: 오른쪽에서 슬라이드 인 (push)
                (slideInHorizontally(tween(300)) { w -> w } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> -w / 4 } + fadeOut(tween(220)))
            } else {
                // 마이페이지 복귀: 오른쪽으로 슬라이드 아웃 (pop)
                (slideInHorizontally(tween(300)) { w -> -w / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> w } + fadeOut(tween(220)))
            }
        },
        label = "mypageSettings",
    ) { settings ->
        if (settings) {
            SettingsScreen(viewModel) { showSettings.value = false }
            return@AnimatedContent
        }

    LazyColumn(
        state = listState,
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
                GlgCircleIconButton(Icons.Default.Settings, "설정", outlined = true) { showSettings.value = true }
            }
        }
        item {
            ProfileHeroCard(
                name = if (account.isGuest) "게스트" else profile.name,
                email = if (account.isGuest) "" else profile.email,
                photoUrl = if (account.isGuest) null else account.photoUrl,
                isGuest = account.isGuest,
                onLogin = { (context as? android.app.Activity)?.let { viewModel.signIn(it) } },
            )
        }
        item { Spacer(Modifier.height(22.dp)) }

        item { SectionLabel("활동 통계") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Default.LocalFireDepartment, "${attendanceStreak}일", "연속 출석", Modifier.weight(1f), tint = Color(0xFFFF7A45))
                StatTile(Icons.Default.CalendarMonth, "₩%,d".format(monthlyTotal), "이번 달 지출", Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Default.Payments, "₩%,d".format(total), "총 지출", Modifier.weight(1f))
                StatTile(Icons.Default.Casino, "${gachaTotal}회", "가챠 기록", Modifier.weight(1f))
                StatTile(Icons.Default.Games, "${games}개", "게임 수", Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }

        item { SectionLabel("게임별 지출 TOP") }
        item { TopGamesCard(spendings) }
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
}

/** 강조색 그라데이션 프로필 히어로 — 아바타·이름·동기화 상태(+게스트 로그인). */
@Composable
private fun ProfileHeroCard(
    name: String,
    email: String,
    photoUrl: String?,
    isGuest: Boolean,
    onLogin: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.22f))))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 흰색 링 안에 아바타
                Box(
                    Modifier.size(70.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    ProfileAvatar(photoUrl = photoUrl, size = 64.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Surface(color = Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(20.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isGuest) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                null, tint = Color.White, modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isGuest) "게스트 · 동기화 꺼짐" else "구글 계정 동기화",
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            )
                        }
                    }
                }
            }
            if (isGuest) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable { onLogin() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Google로 로그인", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
                }
            } else if (email.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(email, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
        }
    }
}

/** 아이콘 + 값 + 라벨 통계 타일. */
@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier, tint: Color? = null) {
    val accent = LocalAccent.current
    val c = tint ?: accent
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

/** 게임별 지출 랭킹 (상위 5) — 색 점 + 금액 + % 바. */
@Composable
private fun TopGamesCard(spendings: List<Spending>) {
    val byGame = remember(spendings) {
        spendings.groupBy { it.gameName }
            .map { (g, list) -> Triple(g, list.sumOf { s -> s.amount }, list.first().gameColor) }
            .sortedByDescending { it.second }
            .take(5)
    }
    val total = remember(spendings) { spendings.sumOf { it.amount }.coerceAtLeast(1L) }
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            if (byGame.isEmpty()) {
                Text("아직 지출 기록이 없어요", fontSize = 13.sp, color = TextSecondary)
            } else {
                byGame.forEachIndexed { i, (game, amt, color) ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    val frac = (amt.toFloat() / total).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(game, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("₩%,d".format(amt), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("${(frac * 100).toInt()}%", fontSize = 11.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(ProgressEmpty)) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(color))
                    }
                }
            }
        }
    }
}

// ============================================================
//  설정 화면(SettingsScreen)에서도 재사용하는 공용 컴포넌트 — 유지
// ============================================================

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
