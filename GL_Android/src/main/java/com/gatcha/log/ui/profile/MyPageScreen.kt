package com.gatcha.log.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Calendar
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgTabHeader
import com.gatcha.log.ui.components.ProfileAvatar
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.theme.*
import com.gatcha.log.util.won

@Composable
fun MyPageScreen(
    viewModel: SpendingViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onSubPageChange: (Boolean) -> Unit = {},
) {
    val spendings by viewModel.spendings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val account by viewModel.account.collectAsState()
    val attendanceStreak by viewModel.attendanceStreak.collectAsState()
    val gachaStats by viewModel.gachaStats.collectAsState()

    val showSettings = remember { mutableStateOf(false) }

    // 설정 페이지에서 시스템/제스처 뒤로가기 시 홈이 아니라 마이페이지로 복귀
    BackHandler(enabled = showSettings.value) { showSettings.value = false }
    // 설정 페이지가 열리면 상위(Scaffold)에 알려 하단바·FAB를 숨김
    LaunchedEffect(showSettings.value) { onSubPageChange(showSettings.value) }

    // 홈 만료 배너 CTA 가 마이페이지 → 설정으로 자동 진입시키도록(C4 흐름).
    val pendingOpenHoyolab by viewModel.pendingOpenHoyolabLink.collectAsState()
    LaunchedEffect(pendingOpenHoyolab) {
        if (pendingOpenHoyolab) showSettings.value = true
    }

    val monthlyTotal = remember(spendings) { viewModel.monthlyTotal() }
    val total = remember(spendings) { spendings.sumOf { it.amount } }
    val games = remember(spendings) { spendings.map { it.gameName }.distinct().size }
    val gachaTotal = gachaStats?.total ?: 0
    // ── 대시보드 파생 지표 (전부 기존 보유 데이터에서 계산) ──
    val dailyAvg = remember(monthlyTotal) { monthlyTotal / currentDayOfMonth().coerceAtLeast(1) }
    val prevMonthly = remember(spendings) { val (y, m) = prevYearMonth(); viewModel.monthlyTotal(y, m) }
    val monthlyTrend = remember(spendings) {
        recentYearMonths(6).map { (y, m) -> MonthPoint(m, viewModel.monthlyTotal(y, m)) }
    }
    val fiveStars = gachaStats?.byGame?.values?.sumOf { it.five } ?: 0
    val spendCount = spendings.size

    AnimatedContent(
        targetState = showSettings.value,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState) {
                // 설정 열기: 오른쪽에서 슬라이드 인 (push)
                (slideInHorizontally(glgStandardSpec()) { w -> w } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeOut(glgShortSpec()))
            } else {
                // 마이페이지 복귀: 오른쪽으로 슬라이드 아웃 (pop)
                (slideInHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> w } + fadeOut(glgShortSpec()))
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
            GlgTabHeader("마이페이지") {
                GlgCircleIconButton(Icons.Default.Settings, "설정", outlined = true) { showSettings.value = true }
            }
        }
        // ① 프로필 헤더 (흰 카드)
        item {
            ProfileHeader(
                name = if (account.isGuest) "게스트" else profile.name,
                photoUrl = if (account.isGuest) null else account.photoUrl,
                isGuest = account.isGuest,
                onLogin = { viewModel.signIn() },
                onLogout = { viewModel.signOut() },
            )
        }
        item { Spacer(Modifier.height(13.dp)) }

        // ② 이번 달 지출 KPI
        item {
            MonthlyKpiCard(
                monthly = monthlyTotal,
                total = total,
                dailyAvg = dailyAvg,
                gameCount = games,
                prevMonthly = prevMonthly,
            )
        }
        item { Spacer(Modifier.height(13.dp)) }

        // ③ 월별 지출 추이 (관리 섹션 대체)
        item { SectionLabel("월별 지출 추이") }
        item { MonthlyTrendCard(monthlyTrend) }
        item { Spacer(Modifier.height(11.dp)) }

        // ④ 활동 메트릭 2×2
        item { SectionLabel("활동") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    MetricTile(Icons.Default.LocalFireDepartment, "${attendanceStreak}일", "연속 출석", Modifier.weight(1f), tint = Color(0xFFFF7A45))
                    MetricTile(Icons.Default.Casino, "${gachaTotal}회", "가챠 기록", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    MetricTile(Icons.Default.Star, "${fiveStars}회", "5★ 획득", Modifier.weight(1f), tint = Color(0xFFE0A93B))
                    MetricTile(Icons.AutoMirrored.Filled.ReceiptLong, "${spendCount}건", "지출 기록", Modifier.weight(1f), tint = Color(0xFF16A34A))
                }
            }
        }
        item { Spacer(Modifier.height(13.dp)) }

        // ⑤ 게임별 지출 (도넛)
        item { SectionLabel("게임별 지출") }
        item { GameDonutCard(spendings) }
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
}

// ============================================================
//  마이페이지 대시보드 컴포넌트 — 흰 카드 + 아웃라인 (iOS glgGlass 파리티)
// ============================================================

/** 흰 배경 + 옅은 아웃라인 카드 (iOS glgGlass 대응 · 글래스 제거). */
@Composable
private fun OutlineCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f)),
        shadowElevation = 0.dp,
        content = content,
    )
}

/** ① 프로필 헤더 — 아바타 + 이름 + 동기화 칩 + 로그아웃/로그인. */
@Composable
private fun ProfileHeader(
    name: String,
    photoUrl: String?,
    isGuest: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val accent = LocalAccent.current
    OutlineCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    ProfileAvatar(photoUrl = photoUrl, size = 52.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    val chipColor = if (isGuest) TextSecondary else Color(0xFF15803D)
                    val chipBg = chipColor.copy(alpha = 0.13f)
                    Surface(color = chipBg, shape = RoundedCornerShape(20.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isGuest) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                null, tint = chipColor, modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isGuest) "게스트 · 동기화 꺼짐" else "구글 계정 동기화",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chipColor,
                            )
                        }
                    }
                }
                if (!isGuest) {
                    // 계정 단일화: 로그아웃을 마이페이지 헤더로 일원화 (설정의 중복 계정 카드 제거)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f)), RoundedCornerShape(11.dp))
                            .clickable { onLogout() }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    ) {
                        Text("로그아웃", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                }
            }
            if (isGuest) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent)
                        .clickable { onLogin() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Google로 로그인", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** ② 이번 달 지출 KPI — 큰 강조색 숫자 + 추세 + 총지출/일평균/게임수 스트립. */
@Composable
private fun MonthlyKpiCard(monthly: Long, total: Long, dailyAvg: Long, gameCount: Int, prevMonthly: Long) {
    val accent = LocalAccent.current
    OutlineCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("이번 달 지출", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                TrendPill(monthly, prevMonthly)
            }
            Spacer(Modifier.height(6.dp))
            Text(won(monthly), fontSize = 34.sp, fontWeight = FontWeight.Black, color = accent, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.06f)))
            Spacer(Modifier.height(13.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KpiCell(won(total), "총 지출", Modifier.weight(1f))
                KpiDivider()
                KpiCell(won(dailyAvg), "일 평균", Modifier.weight(1f))
                KpiDivider()
                KpiCell("${gameCount}개", "플레이 게임", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KpiCell(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
    }
}

@Composable
private fun KpiDivider() {
    Box(Modifier.width(1.dp).height(26.dp).background(Color.Black.copy(alpha = 0.06f)))
}

/** 지난달 대비 추세 칩 (지출 감소=초록, 증가=빨강). 이전 달 0이면 미표시. */
@Composable
private fun TrendPill(monthly: Long, prevMonthly: Long) {
    if (prevMonthly <= 0L) return
    val deltaPct = ((monthly - prevMonthly).toFloat() / prevMonthly * 100f).toInt()
    val down = deltaPct <= 0
    val color = if (down) Color(0xFF15803D) else Color(0xFFDC2626)
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp)) {
        Text(
            "${if (down) "▼" else "▲"} ${kotlin.math.abs(deltaPct)}% · 지난달",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color,
        )
    }
}

/** ③ 월별 지출 추이 — 최근 6개월 바차트(이번 달 강조). */
@Composable
private fun MonthlyTrendCard(trend: List<MonthPoint>) {
    val accent = LocalAccent.current
    val maxAmt = remember(trend) { (trend.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1L) }
    OutlineCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            trend.forEachIndexed { i, p ->
                val isCurrent = i == trend.lastIndex
                val frac = (p.amount.toFloat() / maxAmt).coerceIn(0f, 1f)
                val barH = (90f * frac).coerceAtLeast(3f).dp
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.58f)
                            .height(barH)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (isCurrent) accent else accent.copy(alpha = 0.2f)),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "${p.month}월",
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) accent else TextSecondary,
                    )
                }
            }
        }
    }
}

/** ④ 활동 메트릭 타일 (흰 카드 · 아이콘+값+라벨). */
@Composable
private fun MetricTile(icon: ImageVector, value: String, label: String, modifier: Modifier, tint: Color? = null) {
    val accent = LocalAccent.current
    val c = tint ?: accent
    OutlineCard(shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = c, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(9.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

/** ⑤ 게임별 지출 — 도넛 + 범례. */
@Composable
private fun GameDonutCard(spendings: List<Spending>) {
    val byGame = remember(spendings) {
        spendings.groupBy { it.gameName }
            .map { (g, list) -> GameSlice(g, list.sumOf { s -> s.amount }, list.first().gameColor.toColor()) }
            .sortedByDescending { it.amount }
    }
    val total = remember(spendings) { spendings.sumOf { it.amount } }
    OutlineCard(modifier = Modifier.fillMaxWidth()) {
        if (byGame.isEmpty() || total <= 0L) {
            Box(Modifier.padding(20.dp)) {
                Text("아직 지출 기록이 없어요", fontSize = 13.sp, color = TextSecondary)
            }
        } else {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 18.dp.toPx()
                        val inset = stroke / 2f
                        var start = -90f
                        byGame.forEach { slice ->
                            val sweep = (slice.amount.toFloat() / total) * 360f
                            drawArc(
                                color = slice.color,
                                startAngle = start,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Butt),
                            )
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("총 지출", fontSize = 9.sp, color = TextSecondary)
                        Text(won(total), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    byGame.take(5).forEach { slice ->
                        val pct = (slice.amount.toFloat() / total * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(slice.color))
                            Spacer(Modifier.width(8.dp))
                            Text(slice.game, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(won(slice.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Spacer(Modifier.width(8.dp))
                            Text("$pct%", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

private data class MonthPoint(val month: Int, val amount: Long)
private data class GameSlice(val game: String, val amount: Long, val color: Color)

/** 최근 [count]개월 (year, month1-12) — 오래된→최신 순. */
private fun recentYearMonths(count: Int): List<Pair<Int, Int>> =
    (count - 1 downTo 0).map { back ->
        val c = Calendar.getInstance().apply { add(Calendar.MONTH, -back) }
        c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
    }

/** 지난달 (year, month1-12). */
private fun prevYearMonth(): Pair<Int, Int> {
    val c = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
    return c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
}

private fun currentDayOfMonth(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

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
        // 색상이 늘어 한 줄을 넘기므로 5개씩 끊어 2행으로 배치.
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccentPalette.chunked(5).forEachIndexed { rowIdx, rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowOptions.forEachIndexed { colIdx, option ->
                        val index = rowIdx * 5 + colIdx
                        Column(
                            modifier = Modifier.weight(1f).clickable { onSelect(index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(option.color.toColor()),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (index == selectedIndex) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(option.label, fontSize = 10.sp, color = if (index == selectedIndex) option.color.toColor() else TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    label: String,
    icon: ImageVector,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
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
            trailing?.let { Spacer(Modifier.width(6.dp)); it() }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
        }
    }
}
