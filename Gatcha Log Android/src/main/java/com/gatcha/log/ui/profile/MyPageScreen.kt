package com.gatcha.log.ui.profile

import com.gatcha.log.ui.components.GldsSection
import com.gatcha.log.ui.components.GldsHairline
import com.gatcha.log.ui.components.GldsBand
import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsSize
import com.gatcha.log.ui.components.GldsVariant
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import com.gatcha.log.data.ChallengeSummary
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.Spending
import com.gatcha.log.data.TaskStats
import com.gatcha.log.ui.components.GlgTabHeaderHeight
import com.gatcha.log.ui.components.glgTabContentBottom
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgHeaderTitlePill
import com.gatcha.log.ui.components.GlgTabHeader
import com.gatcha.log.ui.components.GlgTopScrimFadeExtra as ScrimFadeExtra
import com.gatcha.log.ui.components.ProfileAvatar
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.theme.*
import com.gatcha.log.util.percentShares
import com.gatcha.log.util.won
import com.gatcha.log.ui.savings.SavingsChallengeScreen

/**
 * 마이페이지의 하위 페이지 갈래 — 홈의 `HomeSub` 와 같은 방식이다.
 *
 * 예전엔 설정 하나뿐이라 `Boolean` 으로 갈랐는데, 절약 챌린지가 이관되며 둘이 됐다.
 * Boolean 두 개로 두면 `onSubPageChange` 에 서로 다른 값을 밀어 하단바·FAB 표시가
 * 순서에 좌우된다(홈에서 겪은 문제다) — 파생값 하나를 단일 진실로 둔다.
 */
private enum class MyPageSub { Main, Settings, Challenge }

/**
 * 마이페이지 3.0 — 카드를 걷고 화면 폭 전체 섹션 + 회색 띠 구분(목업 A안).
 * 게임 정보(가챠·천장·UID)는 게임정보 탭 몫이라 여기 두지 않는다.
 */
@Composable
fun MyPageScreen(
    viewModel: SpendingViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onSubPageChange: (Boolean) -> Unit = {},
) {
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val attendanceStreak by viewModel.attendanceStreak.collectAsStateWithLifecycle()
    val attendanceHistory by viewModel.attendanceHistory.collectAsStateWithLifecycle()
    val trackedGames by viewModel.trackedAttendanceGames.collectAsStateWithLifecycle()
    val taskStats by viewModel.taskStats.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val challenge by viewModel.challenge.collectAsStateWithLifecycle()

    val showSettings = remember { mutableStateOf(false) }
    // 절약 챌린지 — 27.50.0 에서 홈에서 이관했다.
    var showChallenge by remember { mutableStateOf(false) }

    /**
     * 하위 페이지 갈래. 둘 다 마이페이지 바로 아래 한 층이고 서로 오갈 수 없다
     * (하위에서 나가는 길은 마이페이지뿐) — push/pop 판정에 [MyPageSub.Main] 인지만 보면 된다.
     */
    val sub = when {
        showSettings.value -> MyPageSub.Settings
        showChallenge -> MyPageSub.Challenge
        else -> MyPageSub.Main
    }
    // 하위 페이지에서 시스템/제스처 뒤로가기 시 홈이 아니라 마이페이지로 복귀
    BackHandler(enabled = sub != MyPageSub.Main) {
        showSettings.value = false
        showChallenge = false
    }
    // 하위 페이지가 열리면 상위(Scaffold)에 알려 하단바·FAB를 숨김
    LaunchedEffect(sub) { onSubPageChange(sub != MyPageSub.Main) }

    // 홈 만료 배너 CTA 가 마이페이지 → 설정으로 자동 진입시키도록(C4 흐름).
    val pendingOpenHoyolab by viewModel.pendingOpenHoyolabLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenHoyolab) {
        if (pendingOpenHoyolab) showSettings.value = true
    }

    // 이번 달·전월·최근 6개월 합계는 **공유 VM 이 지출을 한 번만 훑어 만들어 둔 값**을 그대로 쓴다.
    val monthlyTotal by viewModel.currentMonthTotal.collectAsStateWithLifecycle()
    val prevMonthly by viewModel.previousMonthTotal.collectAsStateWithLifecycle()
    val recentTotals by viewModel.recentMonthlyTotals.collectAsStateWithLifecycle()
    // 한 번의 순회로 누적 총액·이번 달 건수를 같이 낸다.
    val (total, monthCount) = remember(spendings) {
        val ym = DateUtil.yearMonthKey(System.currentTimeMillis())
        var sum = 0L
        var n = 0
        spendings.forEach {
            sum += it.amount
            if (DateUtil.yearMonthKey(it.dateMillis) == ym) n++
        }
        sum to n
    }
    val dailyAvg = remember(monthlyTotal) { monthlyTotal / currentDayOfMonth().coerceAtLeast(1) }
    val monthlyTrend = remember(recentTotals) {
        // recentMonthlyTotals 는 오래된 달 → 이번 달 순. 표시용 월 번호만 붙인다.
        val months = recentYearMonths(recentTotals.size)
        recentTotals.mapIndexed { i, v -> MonthPoint(months[i].second, v) }
    }

    AnimatedContent(
        targetState = sub,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState != MyPageSub.Main) {
                // 하위 열기: 오른쪽에서 슬라이드 인 (push)
                (slideInHorizontally(glgStandardSpec()) { w -> w } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeOut(glgShortSpec()))
            } else {
                // 마이페이지 복귀: 오른쪽으로 슬라이드 아웃 (pop)
                (slideInHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> w } + fadeOut(glgShortSpec()))
            }
        },
        label = "mypageSub",
    ) { target ->
        when (target) {
            MyPageSub.Settings -> {
                SettingsScreen(viewModel) { showSettings.value = false }
                return@AnimatedContent
            }
            MyPageSub.Challenge -> {
                SavingsChallengeScreen(viewModel) { showChallenge = false }
                return@AnimatedContent
            }
            MyPageSub.Main -> Unit
        }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 상단 스크림 — 다른 탭(홈·지출·게임정보)과 동일 규격. 리스트가 헤더 아래로 스크롤될 때만 나타난다.
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val topScrimAlpha by animateFloatAsState(if (scrolled) 0.88f else 0f, label = "topScrim")
    Box(Modifier.fillMaxSize().background(Color.White)) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = GlgTabHeaderHeight + topInset, bottom = glgTabContentBottom()),
    ) {
        item {
            ProfileSection(
                name = if (account.isGuest) "게스트" else profile.name,
                photoUrl = if (account.isGuest) null else account.photoUrl,
                isGuest = account.isGuest,
                onLogin = { viewModel.signIn() },
                onLogout = { viewModel.signOut() },
            )
        }
        band()
        item { MonthSection(monthlyTotal, prevMonthly, budget, dailyAvg, total, monthCount, monthlyTrend) }
        band()
        item { GameSpendSection(spendings) }
        band()
        item { RecordSection(monthlyTrend) }
        band()
        item { ActivitySection(attendanceHistory, trackedGames, attendanceStreak, taskStats, spendings.size) }
        band()
        item { ChallengeSection(challenge) { showChallenge = true } }
    }
    // 상단 스크림 — **상태바 영역만** 덮는다(헤더 버튼 줄은 덮지 않아 콘텐츠가 버튼 아래로 지나가는 연출 유지).
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(topInset + ScrimFadeExtra)
            .graphicsLayer { alpha = topScrimAlpha }
            .background(
                Brush.verticalGradient(
                    0f to Color.White,
                    0.35f to Color.White,
                    1f to Color.Transparent,
                ),
            ),
    )
    Box(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
        GlgTabHeader(
            "",
            // 제목도 헤더 버튼 톤의 불투명 알약(콘텐츠 비침 방지) — 하위 페이지 헤더와 같은 컴포넌트.
            leading = { GlgHeaderTitlePill("마이페이지") },
        ) {
            GlgCircleIconButton(Icons.Default.Settings, "설정", outlined = true, solidBackground = true) { showSettings.value = true }
        }
    }
    }
    }
}

// ============================================================
//  마이페이지 3.0 섹션 — 목업 A안 규격
//  섹션: 좌우 20 · 위 22 · 아래 20 / 섹션 사이 10dp 회색 띠 / 줄 사이 1dp 헤어라인
// ============================================================

private val HairColor = Color(0xFFEEF0F2)
private val UpColor = Color(0xFFDC2626)
private val DownColor = Color(0xFF15803D)

private fun LazyListScope.band() = item { GldsBand() }

@Composable
private fun Section(
    modifier: Modifier = Modifier,
    top: androidx.compose.ui.unit.Dp = 22.dp,
    // 20 − 마지막 요소의 자체 아래 여백 — 목록 줄(ListRow vertical 12)로 끝나면 8. 눈에 보이는 끝 → 띠 = 20.
    // 페이지 맨 아래 섹션은 줄이지 않는다(안전 영역 위 숨 쉴 여백).
    bottom: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GldsSection(modifier, top = top, bottom = bottom, content = content)
}

/** 섹션 머리 — 제목 17 + 오른쪽 보조 문구(없으면 생략). */
@Composable
private fun SectionHead(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        trailing()
    }
}

@Composable
private fun MoreText(text: String) {
    Text(text, fontSize = 13.sp, color = TextSecondary)
}

@Composable
private fun Hair(modifier: Modifier = Modifier) = GldsHairline(modifier)

@Composable
private fun SubText(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary, bold: Boolean = false) {
    Text(text, modifier = modifier, fontSize = 12.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
}

@Composable
private fun NumText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
}

/** 목록 한 줄 — 위아래 12(GLDS 기본 줄) · 요소 사이 12. */
@Composable
private fun ListRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 세 칸 지표 줄 — 값 15 · 라벨 12, 왼쪽 정렬. */
@Composable
private fun StatTriple(vararg cells: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        cells.forEach { (value, label) ->
            Column(Modifier.weight(1f)) {
                NumText(value)
                SubText(label)
            }
        }
    }
}

/** ① 프로필 — 아바타 56 + 이름 + 동기화 상태 + 로그아웃/로그인. */
@Composable
private fun ProfileSection(
    name: String,
    photoUrl: String?,
    isGuest: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Section(top = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(photoUrl = photoUrl, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                SubText(
                    if (isGuest) "게스트 · 동기화 꺼짐" else "구글 계정 동기화 중",
                    color = if (isGuest) TextSecondary else DownColor,
                    bold = true,
                )
            }
            if (!isGuest) {
                // 계정 단일화: 로그아웃을 마이페이지 헤더로 일원화 (설정의 중복 계정 카드 제거)
                GldsButton("로그아웃", onLogout, variant = GldsVariant.Neutral, size = GldsSize.XS)
            }
        }
        if (isGuest) {
            Spacer(Modifier.height(14.dp))
            GldsButton("Google로 로그인", onLogin, Modifier.fillMaxWidth())
        }
    }
}

/** ② 이번 달 지출 — 금액 + 지난달 대비 + 예산 진행 + 지표 3칸 + 최근 6개월 막대. */
@Composable
private fun MonthSection(
    monthly: Long,
    prevMonthly: Long,
    budget: Long,
    dailyAvg: Long,
    total: Long,
    monthCount: Int,
    trend: List<MonthPoint>,
) {
    val accent = LocalAccent.current
    Section {
        SectionHead("이번 달 지출") {
            // 지난달 0 이면 비교하지 않는다(예전 TrendPill 과 같은 규칙).
            if (prevMonthly > 0L) {
                val deltaPct = ((monthly - prevMonthly).toFloat() / prevMonthly * 100f).toInt()
                val down = deltaPct <= 0
                Text(
                    "${if (down) "▼" else "▲"} ${kotlin.math.abs(deltaPct)}% 지난달보다",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (down) DownColor else Urgent,
                )
            }
        }
        Text(won(monthly), fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary, maxLines = 1)
        // 예산은 설정했을 때만 — 초과 문구는 홈 예산 카드와 같은 규칙.
        if (budget > 0) {
            val over = monthly > budget
            val pct = (monthly * 100 / budget).toInt()
            val frac = if (over) 1f else monthly.toFloat() / budget
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(HairColor),
            ) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(if (over) Urgent else accent))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                SubText("예산 ${won(budget)} 중 ${pct}%")
                if (over) SubText("${won(monthly - budget)} 초과", color = Urgent)
                else SubText("${won(budget - monthly)} 남음")
            }
        }
        StatTriple(
            won(dailyAvg) to "일 평균",
            won(total) to "누적 지출",
            "${monthCount}건" to "이번 달 기록",
        )
        MonthBars(trend, accent)
    }
}

/** 최근 6개월 막대 — 막대 최대 70 · 라벨 12, 이번 달만 강조색. */
@Composable
private fun MonthBars(trend: List<MonthPoint>, accent: Color) {
    val maxAmt = remember(trend) { (trend.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1L) }
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp).height(96.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        trend.forEachIndexed { i, p ->
            val isCurrent = i == trend.lastIndex
            val barH = (70f * (p.amount.toFloat() / maxAmt).coerceIn(0f, 1f)).coerceAtLeast(3f).dp
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barH)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(if (isCurrent) accent else accent.copy(alpha = 0.2f)),
                )
                Spacer(Modifier.height(6.dp))
                SubText("${p.month}월", color = if (isCurrent) TextPrimary else TextSecondary, bold = isCurrent)
            }
        }
    }
}

/** ③ 게임별 지출 — 누적 비중 띠 + 게임별 줄(상위 5 + 기타). */
@Composable
private fun GameSpendSection(spendings: List<Spending>) {
    // 6개 이상이면 나머지를 '기타'로 묶는다(게임별 월 추이 카드와 같은 규칙).
    val byGame = remember(spendings) {
        val all = spendings.groupBy { it.gameName }
            .map { (g, list) -> GameSlice(g, list.sumOf { s -> s.amount }, list.first().gameColor.toColor()) }
            .sortedByDescending { it.amount }
        if (all.size <= 5) all
        else all.take(5) + GameSlice("기타", all.drop(5).sumOf { it.amount }, EtcSliceColor)
    }
    val total = remember(byGame) { byGame.sumOf { it.amount } }
    // 퍼센트는 **합이 정확히 100이 되도록** 공유 로직으로 배분한다(최대 잔여법).
    val pcts = remember(byGame) { percentShares(byGame.map { it.amount }) }
    Section(bottom = if (byGame.isEmpty() || total <= 0L) 20.dp else 8.dp) {
        SectionHead("게임별 지출") { MoreText("전체 기간") }
        if (byGame.isEmpty() || total <= 0L) {
            SubText("아직 지출 기록이 없어요")
            return@Section
        }
        Row(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            byGame.filter { it.amount > 0 }.forEach { slice ->
                Box(Modifier.weight(slice.amount.toFloat()).fillMaxHeight().background(slice.color))
            }
        }
        Spacer(Modifier.height(6.dp))
        byGame.forEachIndexed { i, slice ->
            if (i > 0) Hair()
            ListRow {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(slice.color))
                Text(slice.game, fontSize = 14.sp, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                NumText(won(slice.amount))
                SubText("${pcts[i]}%", Modifier.width(34.dp).wrapContentWidth(Alignment.End))
            }
        }
    }
}

/** ④ 지출 기록 — 최근 6개월 월 평균 · 가장 많이 쓴 달. */
@Composable
private fun RecordSection(trend: List<MonthPoint>) {
    val avg = remember(trend) { if (trend.isEmpty()) 0L else trend.sumOf { it.amount } / trend.size }
    val peak = remember(trend) { trend.maxByOrNull { it.amount }?.takeIf { it.amount > 0 } }
    Section(bottom = 8.dp) {
        SectionHead("지출 기록")
        ListRow {
            LabelWithPeriod("월 평균", Modifier.weight(1f))
            NumText(won(avg))
        }
        Hair()
        ListRow {
            LabelWithPeriod("가장 많이 쓴 달", Modifier.weight(1f))
            if (peak == null) {
                NumText("—")
            } else {
                NumText("${peak.month}월")
                SubText(won(peak.amount))
            }
        }
    }
}

@Composable
private fun LabelWithPeriod(label: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextPrimary, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        SubText("최근 6개월")
    }
}

/** ⑤ 활동 — 최근 30일 출석 칸 + 지표 3칸 + 게임별 숙제. */
@Composable
private fun ActivitySection(
    history: Map<String, Set<String>>,
    tracked: List<Game>,
    streak: Int,
    taskStats: List<TaskStats>,
    spendCount: Int,
) {
    val accent = LocalAccent.current
    // 오래된 날 → 오늘 순 30칸. 출석한 게임 수로 칸 농도를 가른다(전부 = 진하게 · 일부 = 옅게).
    val days = remember(history, tracked) {
        val keys = tracked.map { it.key }
        (29 downTo 0).map { ago ->
            val done = history[DateUtil.hoyoDayKeyAgo(ago)].orEmpty()
            when {
                done.isEmpty() -> AttendLevel.None
                keys.isNotEmpty() && keys.all { it in done } -> AttendLevel.All
                else -> AttendLevel.Some
            }
        }
    }
    // 일일 숙제 완주 — 기록이 있는 게임들의 30일 완주율 평균.
    val taskRate = remember(taskStats) {
        taskStats.filter { it.dailyDays > 0 }.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.dailyRate } / l.size }
    }
    Section(bottom = if (taskStats.isNotEmpty()) 8.dp else 20.dp) {
        SectionHead("활동") { MoreText("최근 30일") }
        days.chunked(15).forEachIndexed { r, rowDays ->
            if (r > 0) Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowDays.forEachIndexed { c, level ->
                    val isToday = r == 1 && c == rowDays.lastIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .drawWithContent {
                                drawContent()
                                // 오늘 칸 — 1dp 띄운 2dp 테두리(목업 outline).
                                if (isToday) {
                                    val gap = 2.dp.toPx()
                                    drawRoundRect(
                                        color = TextPrimary,
                                        topLeft = Offset(-gap, -gap),
                                        size = Size(size.width + gap * 2, size.height + gap * 2),
                                        cornerRadius = CornerRadius(5.dp.toPx()),
                                        style = Stroke(2.dp.toPx()),
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (level) {
                                    AttendLevel.All -> accent
                                    AttendLevel.Some -> accent.copy(alpha = 0.5f)
                                    AttendLevel.None -> HairColor
                                },
                            ),
                    )
                }
            }
        }
        SubText("진한 칸 = 모든 게임 출석 · 옅은 칸 = 일부", Modifier.padding(top = 8.dp))
        StatTriple(
            "${streak}일" to "연속 출석",
            (taskRate?.let { "$it%" } ?: "—") to "일일 숙제 완주",
            "${spendCount}건" to "지출 기록",
        )
        if (taskStats.isNotEmpty()) {
            Hair(Modifier.padding(top = 18.dp))
            taskStats.forEachIndexed { i, s ->
                if (i > 0) Hair()
                ListRow {
                    Text("${s.gameShort} 숙제", fontSize = 14.sp, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                    // 주간은 주간 기록을 주는 게임만(게임정보 숙제 완주율과 같은 규칙).
                    val week = if (s.weeklyWeeks > 0) " · 주간 ${if (s.weekDone) "완료" else "미완"}" else ""
                    SubText(
                        "오늘 ${if (s.todayDone) "완료" else "미완"}$week",
                        color = if (s.todayDone) TextSecondary else UpColor,
                    )
                    NumText(if (s.isEmpty) "—" else "${s.dailyRate}%", Modifier.width(44.dp).wrapContentWidth(Alignment.End))
                }
            }
        }
    }
}

private enum class AttendLevel { None, Some, All }

/** ⑥ 절약 챌린지 — 섹션 전체가 챌린지 화면 진입. */
@Composable
private fun ChallengeSection(challenge: ChallengeSummary, onOpen: () -> Unit) {
    Section(Modifier.clickable(onClick = onOpen)) {
        SectionHead("절약 챌린지") { MoreText("전체 보기 ›") }
        ListRow {
            Text("무지출 스트릭", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            NumText("${challenge.noSpendStreak}일")
            SubText("최고 ${challenge.bestStreak}일")
        }
        Hair()
        ListRow {
            Text("진행 중 챌린지", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            NumText("${challenge.challenges.size}개")
            SubText("달성 ${challenge.challenges.count { it.reached }}개")
        }
        Hair()
        ListRow {
            Text("획득 배지", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            NumText("${challenge.earnedBadgeCount} / ${challenge.totalBadgeCount}")
        }
    }
}

private data class MonthPoint(val month: Int, val amount: Long)
private data class GameSlice(val game: String, val amount: Long, val color: Color)

/** '기타'(상위 5개 밖) 조각 색 — 게임별 월 추이 카드와 동일 회색. */
private val EtcSliceColor = Color(0xFFB8BDC6)

/** 최근 [count]개월 (year, month1-12) — 오래된→최신 순. */
private fun recentYearMonths(count: Int): List<Pair<Int, Int>> =
    (count - 1 downTo 0).map { back ->
        val c = Calendar.getInstance().apply { add(Calendar.MONTH, -back) }
        c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
    }

private fun currentDayOfMonth(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

// ============================================================
//  설정 화면(SettingsScreen)에서도 재사용하는 공용 컴포넌트 — 유지
// ============================================================

@Composable
fun ThemeColorGrid(selectedIndex: Int, range: IntRange = AccentPalette.indices, onSelect: (Int) -> Unit) {
    // 5개씩 끊어 배치. [range] 로 팔레트 일부(선명 · 차분)만 그린다. (제목·카드는 호출부에서)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        range.chunked(5).forEach { rowIndices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowIndices.forEach { index ->
                    val option = AccentPalette[index]
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
