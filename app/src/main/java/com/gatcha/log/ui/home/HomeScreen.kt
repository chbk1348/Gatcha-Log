package com.gatcha.log.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.Spending
import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.ui.game.GameInfoScreen
import com.gatcha.log.ui.profile.MyPageScreen
import com.gatcha.log.ui.spending.AddSpendingModal
import com.gatcha.log.ui.spending.SpendingScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.components.GlassBackground
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.components.LocalHazeState
import com.gatcha.log.ui.components.NoteSkeletonRow
import com.gatcha.log.ui.theme.*
import dev.chrisbanes.haze.HazeState

@Composable
fun HomeScreen(viewModel: SpendingViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val showAddSpendingSheet = remember { mutableStateOf(false) }
    val spendingToEdit = remember { mutableStateOf<Spending?>(null) }
    val accent = LocalAccent.current

    // 배경 Haze 소스(정적 그라데이션). 카드/내비는 더 이상 라이브 블러를 쓰지 않아 스크롤이 가볍다.
    val bgHaze = remember { HazeState() }

    val openEditor: (Spending?) -> Unit = { target ->
        spendingToEdit.value = target
        showAddSpendingSheet.value = true
    }

    // 앱 시작 시 1회 API 새로고침 (ennead 배너·이벤트 + HoYoLAB 노트) + 업데이트 확인.
    // ViewModel init 에서 호출하면 프로퍼티 초기화 순서 문제로 NPE 가 나므로 UI 에서 트리거.
    LaunchedEffect(Unit) {
        viewModel.refreshGameInfo()
        viewModel.checkForUpdate()
    }
    val updateInfo by viewModel.updateInfo.collectAsState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onAddClick = { openEditor(null) },
                accent = accent,
                showFab = selectedTab <= 1, // 홈·지출 탭에서만 FAB 노출
            )
        },
    ) { paddingValues ->
        CompositionLocalProvider(LocalHazeState provides bgHaze) {
            GlassBackground(hazeState = bgHaze, modifier = Modifier.fillMaxSize()) {
                // 콘텐츠는 하단바 아래까지 확장(상단 인셋만 적용) → 글래스 내비가 실제 콘텐츠를 블러
                Box(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())) {
                    when (selectedTab) {
                        0 -> HomeContent(viewModel, onNavigateToGameInfo = { selectedTab = 2 })
                        1 -> SpendingScreen(viewModel, onEditSpending = { openEditor(it) })
                        2 -> GameInfoScreen(viewModel)
                        3 -> MyPageScreen(viewModel)
                    }

                    if (showAddSpendingSheet.value) {
                        AddSpendingModal(
                            spendingToEdit = spendingToEdit.value,
                            onDismiss = {
                                showAddSpendingSheet.value = false
                                spendingToEdit.value = null
                            },
                            onSave = { spending ->
                                if (spendingToEdit.value == null) viewModel.addSpending(spending)
                                else viewModel.updateSpending(spending)
                                showAddSpendingSheet.value = false
                                spendingToEdit.value = null
                            },
                        )
                    }

                    updateInfo?.let { info ->
                        UpdateDialog(
                            info = info,
                            onDownload = { uriHandler.openUri(info.url); viewModel.dismissUpdate() },
                            onDismiss = { viewModel.dismissUpdate() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeContent(viewModel: SpendingViewModel, onNavigateToGameInfo: () -> Unit) {
    val spendings by viewModel.spendings.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val attendanceToday by viewModel.attendanceToday.collectAsState()
    val banners by viewModel.activeBanners.collectAsState()
    val liveNotes by viewModel.liveNotes.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()
    val checkingIn by viewModel.checkingIn.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val attendanceStreak by viewModel.attendanceStreak.collectAsState()

    val monthlyTotal = remember(spendings) { viewModel.monthlyTotal() }
    val gachaCount = remember(spendings) {
        spendings.count { DateUtil.isSameMonth(it.dateMillis, viewModel.displayYear, viewModel.displayMonth) }
    }
    val topGame = remember(spendings) { viewModel.topGameThisMonth() }

    // 알림 계산
    val alerts = buildAlerts(monthlyTotal, budget, banners.map { it.dDay() to it.name }, attendanceToday)

    val showNotifications = remember { mutableStateOf(false) }
    val showBudgetDialog = remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { TopHeader(profile.name, alerts.size) { showNotifications.value = true } }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            GameStatusSection(
                hoyolab = hoyolab,
                attendanceToday = attendanceToday,
                liveNotes = liveNotes,
                checkingIn = checkingIn,
                isRefreshing = isRefreshing,
                streak = attendanceStreak,
                onCheckIn = viewModel::attemptCheckIn,
                onConfigClick = onNavigateToGameInfo,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            SpendingSection(
                monthlyTotal = monthlyTotal,
                budget = budget,
                gachaCount = gachaCount,
                topGame = topGame,
                onEditBudget = { showBudgetDialog.value = true },
            )
        }
        item { Spacer(Modifier.height(120.dp)) }
    }

    if (showNotifications.value) {
        NotificationsDialog(alerts) { showNotifications.value = false }
    }
    if (showBudgetDialog.value) {
        BudgetDialog(
            current = budget,
            onDismiss = { showBudgetDialog.value = false },
            onConfirm = { viewModel.setBudget(it); showBudgetDialog.value = false },
        )
    }
}

private fun buildAlerts(
    monthlyTotal: Long,
    budget: Long,
    bannerDDays: List<Pair<Int, String>>,
    attendanceToday: Set<String>,
): List<String> = buildList {
    if (budget > 0) {
        val pct = (monthlyTotal * 100 / budget).toInt()
        if (monthlyTotal > budget) add("이번 달 예산을 초과했어요 (${pct}%)")
        else if (pct >= 90) add("이번 달 예산의 ${pct}%를 사용했어요")
    }
    bannerDDays.filter { it.first in 0..3 }.forEach { (d, name) ->
        add("$name 픽업 배너 종료 ${if (d == 0) "D-DAY" else "D-$d"}")
    }
    val pending = GameData.attendanceGames.count { it.key !in attendanceToday }
    if (pending > 0) add("오늘 출석체크가 ${pending}개 남아있어요")
}

@Composable
fun TopHeader(userName: String, alertCount: Int, onBellClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Gatcha LOG", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Text("가챠 지출 트래커", fontSize = 12.sp, color = TextSecondary)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            GlgCircleIconButton(
                Icons.Default.NotificationsNone,
                contentDescription = "알림",
                badgeCount = alertCount,
                onClick = onBellClick,
            )
            Spacer(Modifier.width(8.dp))
            UserProfileCard(userName)
        }
    }
}

@Composable
fun UserProfileCard(userName: String) {
    val accent = LocalAccent.current
    Surface(color = Color.White.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp), modifier = Modifier.height(48.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = accent, shape = CircleShape, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.padding(4.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(userName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row {
                    TagIndicator(GIColor, "GI")
                    TagIndicator(HSRColor, "HSR")
                    TagIndicator(ZZZColor, "ZZZ")
                }
            }
        }
    }
}

@Composable
fun TagIndicator(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(2.dp))
        Text(label, fontSize = 8.sp, color = TextSecondary)
    }
}

@Composable
fun GameStatusSection(
    hoyolab: HoyolabConfig,
    attendanceToday: Set<String>,
    liveNotes: List<LiveNote>,
    checkingIn: String?,
    isRefreshing: Boolean,
    streak: Int,
    onCheckIn: (String) -> Unit,
    onConfigClick: () -> Unit,
) {
    val accent = LocalAccent.current
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Gamepad, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("게임 현황", fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(16.dp))

            if (!hoyolab.isLinked) {
                // 미연동 — 출석/노트 숨기고 연동 유도
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Link, null, tint = accent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("HoYoLAB 연동이 필요해요", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("실시간 노트·출석체크를 보려면 연동하세요", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(14.dp))
                    GlgButton("HoYoLAB 연동하러 가기", onClick = onConfigClick, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("오늘의 출석", fontSize = 12.sp, color = TextSecondary)
                        if (streak > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                Text("🔥 ${streak}일 연속", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(DateUtil.shortLabelWithWeekday(System.currentTimeMillis()), fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                GameData.attendanceGames.forEach { game ->
                    AttendanceRow(
                        game = game,
                        done = game.key in attendanceToday,
                        inProgress = checkingIn == game.key,
                        onClick = { onCheckIn(game.key) },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text("실시간 노트", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                when {
                    liveNotes.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(liveNotes) { note -> HomeNoteCard(note) }
                    }
                    isRefreshing -> NoteSkeletonRow()
                    else -> Text("UID를 확인해주세요", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun AttendanceRow(game: Game, done: Boolean, inProgress: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (done) accent.copy(alpha = 0.06f) else Color.White,
        border = BorderStroke(1.dp, if (done) accent.copy(alpha = 0.4f) else DividerColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !done && !inProgress) { onClick() },
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = game.color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(game.abbr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = game.color)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(game.displayName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        inProgress -> "출석 처리 중…"
                        done -> "✓ ${game.attendanceReward}"
                        else -> "탭하여 출석체크"
                    },
                    fontSize = 11.sp,
                    color = if (done) accent else TextSecondary,
                )
            }
            when {
                inProgress -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                done -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                else -> Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun HomeNoteCard(note: LiveNote) {
    val accent = LocalAccent.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DividerColor),
        modifier = Modifier.width(130.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(GameData.byName(note.game).shortName, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${note.currentResin}/${note.maxResin}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(note.resinLabel, fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { note.resinRatio },
                color = accent,
                trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
    }
}

@Composable
fun SpendingSection(
    monthlyTotal: Long,
    budget: Long,
    gachaCount: Int,
    topGame: String?,
    onEditBudget: () -> Unit,
) {
    val accent = LocalAccent.current
    val ratio = if (budget > 0) (monthlyTotal.toFloat() / budget).coerceIn(0f, 1f) else 0f
    val pct = if (budget > 0) (monthlyTotal * 100 / budget).toInt() else 0
    val remaining = budget - monthlyTotal
    val over = remaining < 0

    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text("이번 달 지출", fontSize = 12.sp, color = TextSecondary)
                    Text("%d년 %d월".format(DateUtil.year(System.currentTimeMillis()), DateUtil.month(System.currentTimeMillis())), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = onEditBudget, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "예산 설정", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("₩%,d".format(monthlyTotal), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            if (budget > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(ProgressEmpty)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                if (over) Brush.horizontalGradient(listOf(Color(0xFFFF6B6B), DangerText))
                                else Brush.horizontalGradient(listOf(LocalAccentSecondary.current, accent))
                            ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (over) "₩%,d 초과".format(-remaining) else "₩%,d 남음".format(remaining),
                        fontSize = 11.sp, color = if (over) DangerText else TextSecondary,
                    )
                    Text("예산 ${pct}% 사용", fontSize = 11.sp, color = TextSecondary)
                }

                if (pct >= 90) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = if (over) DangerBackground else WarningBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = if (over) DangerText else Color(0xFFFFA500), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (over) "이번 달 예산을 초과했어요" else "이번 달 예산의 ${pct}%를 사용했어요",
                                color = if (over) DangerText else WarningText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            } else {
                // 예산 미설정 — 사용자가 지정하기 전까지 사용률/초과 표시 안 함
                Surface(
                    color = accent.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onEditBudget() },
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Savings, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("월 예산 미설정 — 탭하여 설정하면 사용률이 표시돼요", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoColumn("₩%,d".format(monthlyTotal), "총 지출", Modifier.weight(1f))
                InfoColumn("${gachaCount}회", "총 가챠", Modifier.weight(1f))
                InfoColumn(topGame ?: "-", "최다 지출", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun InfoColumn(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun NotificationsDialog(alerts: List<String>, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    GlgDialog(
        title = "알림 센터",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        if (alerts.isEmpty()) {
            Text("새로운 알림이 없어요 🎉", color = TextSecondary, fontSize = 14.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                alerts.forEach { msg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, fontSize = 13.sp)
                    }
                }
            }
        }
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

@Composable
private fun UpdateDialog(info: UpdateInfo, onDownload: () -> Unit, onDismiss: () -> Unit) {
    GlgDialog(
        title = "업데이트 있어요" + if (info.versionName.isNotBlank()) " (v${info.versionName})" else "",
        onDismiss = onDismiss,
        confirmText = "다운로드",
        onConfirm = onDownload,
        dismissText = "나중에",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("새 버전이 출시되었어요. 다운로드 후 설치하세요.", fontSize = 13.sp, color = TextSecondary)
            if (info.notes.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                info.notes.forEach { n ->
                    Row {
                        Text("· ", fontSize = 13.sp, color = TextSecondary)
                        Text(n, fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onAddClick: () -> Unit, accent: Color, showFab: Boolean) {
    // 단일 진행값으로 FAB 와 하단바(알약)를 함께 확장/축소 애니메이션
    val fab by animateFloatAsState(
        targetValue = if (showFab) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "fab",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((12 * fab).dp),
        ) {
            Surface(
                color = Color(0xF7FFFFFF),
                shape = RoundedCornerShape(40.dp),
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, DividerColor),
                modifier = Modifier.weight(1f), // FAB 폭이 줄면 가중치로 자연스럽게 확장
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavItem(Icons.Default.Home, "홈", selectedTab == 0, accent) { onTabSelected(0) }
                    NavItem(Icons.Default.AccountBalanceWallet, "지출", selectedTab == 1, accent) { onTabSelected(1) }
                    NavItem(Icons.Default.Games, "게임 정보", selectedTab == 2, accent) { onTabSelected(2) }
                    NavItem(Icons.Default.Person, "마이페이지", selectedTab == 3, accent) { onTabSelected(3) }
                }
            }

            // FAB: 폭(64*fab)·스케일·투명도를 같은 진행값으로 줄여 하단바와 동시에 사라짐/등장
            Box(
                modifier = Modifier
                    .width((64 * fab).dp)
                    .graphicsLayer { alpha = fab; scaleX = fab; scaleY = fab },
                contentAlignment = Alignment.Center,
            ) {
                if (fab > 0.01f) {
                    FloatingActionButton(
                        onClick = onAddClick,
                        containerColor = accent,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.requiredSize(64.dp),
                        // 그림자 제거 — 애니메이션 중 그림자 깜빡임 방지 + 플랫 일관성
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                        ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "지출 추가", modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, isSelected: Boolean, accent: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.clickable { onClick() }.padding(vertical = 4.dp),
    ) {
        // 선택 시 아이콘 뒤에 알약(캡슐) 인디케이터
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (isSelected) accent else NavUnselected, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) accent else NavUnselected,
        )
    }
}