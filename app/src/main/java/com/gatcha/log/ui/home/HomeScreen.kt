package com.gatcha.log.ui.home

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.game.GameInfoScreen
import com.gatcha.log.ui.profile.MyPageScreen
import com.gatcha.log.ui.spending.AddSpendingModal
import com.gatcha.log.ui.spending.SpendingScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.components.GlassBackground
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.components.LocalHazeState
import com.gatcha.log.ui.components.glassPanel
import com.gatcha.log.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun HomeScreen(viewModel: SpendingViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val showAddSpendingSheet = remember { mutableStateOf(false) }
    val spendingToEdit = remember { mutableStateOf<Spending?>(null) }
    val accent = LocalAccent.current

    // 두 개의 Haze 소스: bgHaze = 배경 블롭(카드가 블러), contentHaze = 스크롤 콘텐츠(내비가 블러)
    val bgHaze = remember { HazeState() }
    val contentHaze = remember { HazeState() }

    val openEditor: (Spending?) -> Unit = { target ->
        spendingToEdit.value = target
        showAddSpendingSheet.value = true
    }

    // 앱 시작 시 1회 API 새로고침 (ennead 배너·이벤트 + HoYoLAB 노트).
    // ViewModel init 에서 호출하면 프로퍼티 초기화 순서 문제로 NPE 가 나므로 UI 에서 트리거.
    LaunchedEffect(Unit) { viewModel.refreshGameInfo() }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onAddClick = { openEditor(null) },
                accent = accent,
                hazeState = contentHaze,
            )
        },
    ) { paddingValues ->
        CompositionLocalProvider(LocalHazeState provides bgHaze) {
            GlassBackground(hazeState = bgHaze, modifier = Modifier.fillMaxSize()) {
                // 콘텐츠는 하단바 아래까지 확장(상단 인셋만 적용) → 글래스 내비가 실제 콘텐츠를 블러
                Box(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()).haze(contentHaze)) {
                    when (selectedTab) {
                        0 -> HomeContent(viewModel)
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
                }
            }
        }
    }
}

@Composable
fun HomeContent(viewModel: SpendingViewModel) {
    val spendings by viewModel.spendings.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val attendanceToday by viewModel.attendanceToday.collectAsState()
    val banners by viewModel.activeBanners.collectAsState()
    val liveNotes by viewModel.liveNotes.collectAsState()

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
                attendanceToday = attendanceToday,
                liveNotes = liveNotes,
                onToggleAttendance = viewModel::toggleAttendance,
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
            Box {
                IconButton(onClick = onBellClick) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "알림")
                }
                if (alertCount > 0) {
                    Surface(
                        color = Color(0xFFFFA500),
                        shape = CircleShape,
                        modifier = Modifier.size(16.dp).align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp),
                    ) {
                        Text(
                            alertCount.toString(),
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.wrapContentSize(Alignment.Center),
                        )
                    }
                }
            }
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
    attendanceToday: Set<String>,
    liveNotes: List<LiveNote>,
    onToggleAttendance: (String) -> Unit,
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("오늘의 출석", fontSize = 12.sp, color = TextSecondary)
                Text(DateUtil.shortLabelWithWeekday(System.currentTimeMillis()), fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            GameData.attendanceGames.forEach { game ->
                AttendanceRow(
                    game = game,
                    done = game.key in attendanceToday,
                    onClick = { onToggleAttendance(game.key) },
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("실시간 노트", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(liveNotes) { note -> HomeNoteCard(note) }
            }
        }
    }
}

@Composable
fun AttendanceRow(game: Game, done: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (done) accent.copy(alpha = 0.06f) else Color.White,
        border = BorderStroke(1.dp, if (done) accent.copy(alpha = 0.4f) else DividerColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = true) { onClick() },
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
                    if (done) "✓ ${game.attendanceReward}" else "탭하여 출석체크",
                    fontSize = 11.sp,
                    color = if (done) accent else TextSecondary,
                )
            }
            Icon(
                if (done) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                contentDescription = null,
                tint = if (done) accent else Color.LightGray,
                modifier = Modifier.size(24.dp),
            )
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
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onAddClick: () -> Unit, accent: Color, hazeState: HazeState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(40.dp),
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, DividerColor),
                modifier = Modifier.weight(1f).glassPanel(hazeState, RoundedCornerShape(40.dp), tintAlpha = 0.55f, blur = 28.dp),
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

            FloatingActionButton(
                onClick = onAddClick,
                containerColor = accent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "지출 추가", modifier = Modifier.size(32.dp))
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