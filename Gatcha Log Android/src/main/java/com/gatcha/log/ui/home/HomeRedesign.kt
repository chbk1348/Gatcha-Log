package com.gatcha.log.ui.home

import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsSize
import com.gatcha.log.ui.components.GldsVariant
import com.gatcha.log.ui.game.hoyoland.HoyolandTicketKicker
import com.gatcha.log.ui.game.hoyoland.HoyolandTicketShape
import com.gatcha.log.ui.game.hoyoland.HoyolandTicketStub
import com.gatcha.log.ui.game.hoyoland.TicketSubText
import com.gatcha.log.ui.game.hoyoland.hoyolandTicketDeep
import com.gatcha.log.ui.game.hoyoland.ticketCountdown
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.graphics.vector.ImageVector
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandPhase
import com.gatcha.log.ui.game.HoyolandFeature
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Remove
import com.gatcha.log.data.Spending
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.dhLabel
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.NewsLogic
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.AnniversaryInfo
import com.gatcha.log.data.api.NewsItem
import androidx.compose.material.icons.filled.Celebration
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.PityTier
import com.gatcha.log.data.GameSpend
import com.gatcha.log.data.PityHighlight
import com.gatcha.log.data.ResinAlert
import com.gatcha.log.data.TodayTask
import com.gatcha.log.data.TodayTaskKind
import com.gatcha.log.ui.components.GlgDropdownMenu
import com.gatcha.log.ui.components.GlgDropdownItem
import com.gatcha.log.ui.components.GlgTabHeaderHeight
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.ProfileAvatar
import com.gatcha.log.ui.components.SkeletonBox
import com.gatcha.log.ui.theme.DangerBackground
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary
import com.gatcha.log.ui.theme.LocalReduceMotion
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.WarningText
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.util.won
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// 홈 2.0 합본 — M(AI 요약) + K(M3 Expressive 토널) + D(게임별 예산)
// Material3 1.4 Expressive API(ButtonGroup·motionScheme)는 BOM 의존성 충돌 위험으로
// 도입하지 않고, 비주얼 언어(비대칭 코너·토널 컨테이너·연결 알약)만 기존 프리미티브로 구현.
// 데이터는 전부 기존 ViewModel 재사용 — 회귀 최소.
// ─────────────────────────────────────────────────────────────────────────────

// 표시 모델(PityHighlight·GameSpend·BannerPlan·ResinAlert)과 파생 계산은 GL_Shared
// (data/HomeLogic.kt)로 이관 — iOS 와 단일 소스 공유. 여기엔 Compose 표현만 남긴다.

// ── 슬림 헤더 ────────────────────────────────────────────────────────────────
@Composable
fun HomeHeader(
    photoUrl: String?,
    nickname: String,
    isGuest: Boolean,
    alertCount: Int,
    onBellClick: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
) {
    // 프로필(아바타+닉네임) 탭 → 로그아웃 드롭다운. 우측 알림벨.
    // 프로필은 다른 헤더 버튼(알림벨)과 동일 톤의 알약 버튼 — accent 10% 배경 + accent 30% 아웃라인.
    val accent = LocalAccent.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        // 다른 탭(GlgTabHeader)과 동일한 높이·세로 여백. 가로만 홈 고유(알약이 좌측 끝에 붙는 레이아웃).
        modifier = Modifier
            .fillMaxWidth()
            .height(GlgTabHeaderHeight)
            .padding(start = 4.dp, end = 2.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)                     // 불투명 베이스(콘텐츠 비침 방지)
                    .background(accent.copy(alpha = 0.10f))      // 위에 accent 틴트
                    .border(1.5.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                    .height(44.dp)                               // 헤더 원형 버튼(44dp)과 높이 통일
                    .clickable { menuOpen = true }
                    .padding(start = 5.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(photoUrl = photoUrl, size = 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(nickname, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
            }
            GlgDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                GlgDropdownItem(
                    text = if (isGuest) "로그인" else "로그아웃",
                    icon = if (isGuest) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                    danger = !isGuest,
                    onClick = { menuOpen = false; if (isGuest) onSignIn() else onSignOut() },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        GlgCircleIconButton(
            Icons.Default.NotificationsNone,
            contentDescription = "알림",
            badgeCount = alertCount,
            outlined = true,
            solidBackground = true,
            onClick = onBellClick,
        )
    }
}

// ── 섹션 헤더 (카드 바깥 큰 제목) ────────────────────────────────────────────
/** 섹션 위 큰 제목(+옵션 카운트/전체보기). 카드 없는 화면 폭 섹션의 머리 — 다른 탭 섹션 제목과 같은 17. */
@Composable
fun HomeSectionHeader(title: String, count: Int? = null, actionTitle: String? = null, onAction: (() -> Unit)? = null) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (count != null) {
            Spacer(Modifier.width(7.dp))
            Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (actionTitle != null && onAction != null) {
            Text(
                actionTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAction() }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// ── 히어로: 이번 달 지출 / 예산 캐러셀 (Figma Make 참고) ───────────────────────
@Composable
fun HeroBalanceCard(monthlyTotal: Long, prevTotal: Long, budget: Long, onBudget: () -> Unit) {
    val accent = LocalAccent.current
    val month = remember { DateUtil.month(System.currentTimeMillis()) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(176.dp)) { page ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (page == 0) HeroSpendPage(month, monthlyTotal, prevTotal, budget, onBudget, accent)
                else HeroBudgetPage(monthlyTotal, budget, onBudget, accent)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { i ->
                val active = pagerState.currentPage == i
                Box(
                    Modifier.height(6.dp).width(if (active) 18.dp else 6.dp).clip(CircleShape)
                        .background(if (active) accent else accent.copy(alpha = 0.24f)),
                )
            }
        }
    }
}

@Composable
private fun HeroSpendPage(month: Int, monthlyTotal: Long, prevTotal: Long, budget: Long, onBudget: () -> Unit, accent: Color) {
    val diff = monthlyTotal - prevTotal
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("${month}월 지출", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        Text(won(monthlyTotal), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
        if (monthlyTotal > 0 || prevTotal > 0) {
            val col = if (diff > 0) DangerText else if (diff < 0) accent else TextSecondary
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(
                    if (diff > 0) Icons.Default.ArrowUpward else if (diff < 0) Icons.Default.ArrowDownward else Icons.Default.Remove,
                    null, tint = col, modifier = Modifier.size(12.dp),
                )
                Text(
                    if (diff == 0L) "지난달과 동일" else "지난달 대비 ${if (diff > 0) "+" else "-"}${won(kotlin.math.abs(diff))}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = col,
                )
            }
        }
    }
}

@Composable
private fun HeroBudgetPage(monthlyTotal: Long, budget: Long, onBudget: () -> Unit, accent: Color) {
    val over = budget > 0 && monthlyTotal > budget
    val pct = if (budget > 0) (monthlyTotal * 100 / budget).toInt() else 0
    val frac = if (budget > 0) (monthlyTotal.toFloat() / budget).coerceIn(0f, 1f) else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("이번 달 예산", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        if (budget > 0) {
            Text(
                if (over) "${won(monthlyTotal - budget)} 초과" else "${won(budget - monthlyTotal)} 남음",
                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (over) DangerText else TextPrimary, maxLines = 1,
            )
            Box(
                Modifier.padding(horizontal = 44.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.65f)),
            ) {
                Box(Modifier.fillMaxWidth(if (over) 1f else frac).fillMaxHeight().clip(CircleShape).background(if (over) DangerText else accent))
            }
            Text(
                if (over) "예산 ${pct - 100}% 초과" else "예산의 ${pct}% 사용",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (over) DangerText else accent,
            )
        } else {
            Text("미설정", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            GldsButton("예산 설정하기", onBudget, variant = GldsVariant.OnTint, size = GldsSize.S)
        }
    }
}

// ── 최근 지출 (목업 Transaction 리스트) ──────────────────────────────────────
// ── 최근 지출 ──────────────────────────────────────────────────────────────
/** 최근 지출 — 카드 없이 헤어라인 목록(10/1). */
@Composable
fun RecentSpendCard(spendings: List<Spending>, onSeeAll: () -> Unit) {
    // VM 이 지출을 날짜 내림차순으로 들고 있어 앞 4건이 곧 최근이다 — 정렬할 필요가 없다.
    val recent = remember(spendings) { spendings.take(4) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HomeSectionHeader("최근 지출", actionTitle = if (recent.isEmpty()) null else "전체보기", onAction = onSeeAll)
        Column(Modifier.fillMaxWidth()) {
            if (recent.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Description, null, tint = Color.LightGray, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("아직 기록된 지출이 없어요", fontSize = 14.sp, color = TextSecondary)
                    Text("+ 지출 추가로 첫 기록을 남겨보세요", fontSize = 12.sp, color = TextSecondary)
                }
            } else {
                recent.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = HomeHair, modifier = Modifier.padding(start = 48.dp))
                    RecentSpendRow(s)
                }
            }
        }
    }
}

/** 홈 목록 헤어라인 — 다른 화면 목록 구분선과 같은 색. */
private val HomeHair = Color(0xFFEEF0F2)

@Composable
private fun RecentSpendRow(s: Spending) {
    val color = s.gameColor.toColor()
    val abbr = GameData.byNameOrNull(s.gameName)?.abbr ?: s.gameName.take(2)
    val subtitle = listOfNotNull(s.dateLabel, s.itemName.ifBlank { null }).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(abbr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.gameName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            }
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(won(s.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
    }
}

@Composable
private fun SummaryChip(text: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun BudgetBar(ratio: Float, over: Boolean) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(ProgressEmpty)) {
        Box(
            Modifier.fillMaxWidth(if (over) 1f else ratio).fillMaxHeight().clip(CircleShape).background(
                if (over) Brush.horizontalGradient(listOf(Color(0xFFFF7A7A), DangerText))
                else Brush.horizontalGradient(listOf(accent2, accent))
            ),
        )
    }
}


/** 실시간 노트 캡슐 (O) — 레진/배터리 등. 가득 차면 경고색. */
@Composable
fun NoteCapsule(note: LiveNote) {
    val accent = LocalAccent.current
    val full = note.maxResin > 0 && note.currentResin >= note.maxResin
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (full) DangerBackground else Color.White,
        border = BorderStroke(1.dp, if (full) DangerBackground else DividerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Bolt, null, tint = if (full) DangerText else accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(GameData.byName(note.game).shortName, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(note.resinLabel, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${note.currentResin}/${note.maxResin}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (full) DangerText else TextPrimary,
            )
            if (full) {
                Spacer(Modifier.width(8.dp))
                Surface(color = DangerText.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                    Text("가득참", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DangerText, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun GameBudgetRow(gs: GameSpend, accent: Color, accent2: Color) {
    val hasLimit = gs.limit > 0
    val gameOver = hasLimit && gs.spent > gs.limit
    val ratio = if (hasLimit) (gs.spent.toFloat() / gs.limit).coerceIn(0f, 1f) else 0f
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlgGameTag(gs.game.displayName, size = GameTagSize.Small)
                Spacer(Modifier.width(8.dp))
                Text(gs.game.shortName, fontSize = 13.sp)
            }
            Text(
                if (hasLimit) "${won(gs.spent)} / ${won(gs.limit)}" else "${won(gs.spent)} · 한도 없음",
                fontSize = 12.sp,
                fontWeight = if (gameOver) FontWeight.Bold else FontWeight.Normal,
                color = if (gameOver) DangerText else TextSecondary,
            )
        }
        Spacer(Modifier.height(5.dp))
        if (hasLimit) {
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(ProgressEmpty)) {
                Box(
                    Modifier.fillMaxWidth(if (gameOver) 1f else ratio).fillMaxHeight().clip(CircleShape).background(
                        if (gameOver) Brush.horizontalGradient(listOf(Color(0xFFFF7A7A), DangerText))
                        else Brush.horizontalGradient(listOf(accent2, accent))
                    ),
                )
            }
        } else {
            // 한도 미설정 — 점선 느낌의 옅은 트랙
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(ProgressEmpty.copy(alpha = 0.5f)))
        }
    }
}

// ── 오늘 할 일 (상태 기반 스마트 액션) ────────────────────────────────────────
// 항목 산출(우선순위·문구)은 GL_Shared HomeLogic.resolveTodayTasks 가 단일 소스.
// 여기서는 종류(TodayTaskKind)에 아이콘과 탭 이동 동작만 붙인다.

/** 오늘 할 일 한 줄. busyable=전체출석처럼 진행 중 스피너가 필요한 항목. */
data class TodayItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val message: String,
    val ctaLabel: String,
    val urgent: Boolean,
    val busyable: Boolean,
    val onAction: () -> Unit,
)

/** shared [TodayTask] → Compose 표시 모델. 종류별 아이콘·클릭 동작 매핑. */
fun List<TodayTask>.toTodayItems(
    onCheckInAll: () -> Unit,
    onResin: () -> Unit,
    onCombat: () -> Unit,
    onBanner: () -> Unit,
    onBudget: () -> Unit,
): List<TodayItem> = map { t ->
    val icon = when (t.kind) {
        TodayTaskKind.ATTENDANCE -> Icons.Default.DoneAll
        TodayTaskKind.RESIN -> Icons.Default.Bolt
        TodayTaskKind.COMBAT -> Icons.Default.MilitaryTech
        TodayTaskKind.BANNER -> Icons.Default.Casino
        TodayTaskKind.BUDGET -> Icons.Default.Savings
    }
    val action = when (t.kind) {
        TodayTaskKind.ATTENDANCE -> onCheckInAll
        TodayTaskKind.RESIN -> onResin
        TodayTaskKind.COMBAT -> onCombat
        TodayTaskKind.BANNER -> onBanner
        TodayTaskKind.BUDGET -> onBudget
    }
    TodayItem(icon, t.message, t.ctaLabel, t.urgent, t.busyable, action)
}

/** 오늘 할 일 — 활성 항목을 전부 리스트로. 카드 없이 섹션 머리 + 헤어라인 목록(10/1). */
@Composable
fun TodayTaskCard(tasks: List<TodayItem>, inProgress: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader("오늘 할 일", count = if (tasks.isEmpty()) null else tasks.size)
        Column { TodayTaskBody(tasks, inProgress) }
    }
}

@Composable
private fun TodayTaskBody(tasks: List<TodayItem>, inProgress: Boolean) {
    if (tasks.isEmpty()) {
        Text("오늘 챙길 건 다 끝냈어요 🎉 여유롭게 즐기세요", fontSize = 14.sp, color = TextPrimary)
    } else {
        tasks.forEachIndexed { i, t ->
            if (i > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(10.dp))
            }
            TodayRow(t, inProgress)
        }
    }
}

@Composable
private fun TodayRow(t: TodayItem, inProgress: Boolean) {
    val tint = if (t.urgent) WarningText else LocalAccent.current
    val busy = t.busyable && inProgress
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { t.onAction() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(t.icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(t.message, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(8.dp))
        if (busy) {
            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = tint)
        } else {
            Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                Row(Modifier.padding(start = 10.dp, end = 7.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.ctaLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
                    Icon(Icons.Default.ChevronRight, null, tint = tint, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

/** 오늘 할 일 로딩 스켈레톤 — 섹션 머리 + 시머 행 N개(카드 없음). */
@Composable
fun TodayTaskSkeleton(rows: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader("오늘 할 일")
        Column { TodaySkeletonRows(rows) }
    }
}

@Composable
private fun TodaySkeletonRows(rows: Int) {
    repeat(rows) { i ->
        if (i > 0) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(18.dp), CircleShape)
            Spacer(Modifier.width(10.dp))
            SkeletonBox(Modifier.weight(1f).height(13.dp))
            Spacer(Modifier.width(8.dp))
            SkeletonBox(Modifier.width(56.dp).height(22.dp), RoundedCornerShape(999.dp))
        }
    }
}

/** 대시보드 목록 로딩 스켈레톤 — 머리 + 행 N개. '이번 주 일정'·'게임 소식' 섹션과 같은 형태(카드 없음). */
@Composable
fun DashCardSkeleton(rows: Int = 3) {
    Column {
        SkeletonBox(Modifier.width(90.dp).height(17.dp))
        repeat(rows) {
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(28.dp), RoundedCornerShape(9.dp))
                Spacer(Modifier.width(9.dp))
                SkeletonBox(Modifier.weight(1f).height(13.dp))
                Spacer(Modifier.width(8.dp))
                SkeletonBox(Modifier.width(34.dp).height(12.dp))
            }
        }
    }
}

// ── 가챠 현황 미니카드 (천장 + 다음 픽업, 읽기전용) ───────────────────────────
/** 천장 단계별 강조색. Safe 는 옅은 회색(평온). */
private fun PityTier.accentColor(): Color = when (this) {
    PityTier.Reached -> Color(0xFFE53935)
    PityTier.Imminent -> Color(0xFFFB8C00)
    PityTier.Caution -> Color(0xFFF59E0B)
    PityTier.Safe -> Color(0xFF9AA0A6)
}

private fun PityTier.shortLabel(): String = when (this) {
    PityTier.Reached -> "보장 확정"
    PityTier.Imminent -> "곧 보장"
    PityTier.Caution -> "주의"
    PityTier.Safe -> "모으는 중"
}

// ════════════════════════════════════════════════════════════════════════════
// 홈 대시보드 개편(27.32.0) — 깔끔한 KPI 중심 레이아웃
// ════════════════════════════════════════════════════════════════════════════

/** 이번 주 게임 일정 — 카드 없이 섹션 머리 + 목록. 목록 전체가 눌려 게임 정보 일정으로 간다(기능 그대로). */
@Composable
fun DashScheduleCard(events: List<GameEvent>, challenges: List<GameChallenge>, onTap: () -> Unit) {
    val accent = LocalAccent.current
    val now = System.currentTimeMillis()
    val items = (events.map { Triple(it.game, it.name, it.endMillis to it.dDayLabel()) } +
        challenges.map { Triple(it.game, it.name, it.endMillis to it.dDayLabel()) })
        .filter { it.third.first > now }.sortedBy { it.third.first }.take(3)
    // 일정이 없어도 **섹션은 남긴다.** 빠지면 "이번 주가 한가하다"와 "아직 못 불러왔다"가
    // 화면에서 똑같아 보인다(로딩 스켈레톤도 같은 자리에 뜬다). 비었다는 것도 알려야 할 상태다.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader("이번 주 일정", actionTitle = "전체", onAction = onTap)
        Column(Modifier.fillMaxWidth().clickable { onTap() }) { ScheduleRows(items, accent) }
    }
}

@Composable
private fun ScheduleRows(items: List<Triple<String, String, Pair<Long, String>>>, accent: Color) {
    if (items.isEmpty()) {
        Text("이번 주 마감 일정이 없어요", fontSize = 14.sp, color = TextSecondary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        items.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlgGameTag(row.first, size = GameTagSize.Small)
                Spacer(Modifier.width(9.dp))
                Text(row.second, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(row.third.second, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

/** 게임 소식 — 카드 없이 섹션 머리 + 목록. 목록 전체가 눌려 게임 정보 소식으로 간다(기능 그대로). */
@Composable
fun DashNewsCard(news: List<NewsItem>, anniversaries: List<AnniversaryInfo>, onTap: () -> Unit) {
    val anni = anniversaries.firstOrNull { it.daysUntil <= 60 }
    // 홈은 2건뿐이라 최신순으로 자르면 한 게임이 둘 다 먹기 쉽다 — 게임을 번갈아 뽑는다.
    val topNews = NewsLogic.previewTop(news, 2)
    if (anni == null && topNews.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader("게임 소식", actionTitle = "전체", onAction = onTap)
        Column(Modifier.fillMaxWidth().clickable { onTap() }) { NewsBody(anni, topNews) }
    }
}

@Composable
private fun NewsBody(anni: AnniversaryInfo?, topNews: List<NewsItem>) {
    val amber = Color(0xFFF59E0B)
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (anni != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(amber.copy(alpha = 0.10f)).padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Celebration, null, tint = amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("${anni.game.shortName} ${anni.ordinal}주년", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(if (anni.daysUntil == 0) "오늘" else "D-${anni.daysUntil}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = amber)
            }
        }
        topNews.forEach { n ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlgGameTag(n.game, size = GameTagSize.Small)
                Spacer(Modifier.width(9.dp))
                Text(n.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
            }
        }
    }
}
/**
 * 홈의 호요랜드 — **입장권 배너**.
 *
 * 1년에 한 번 열리는 행사라 다른 카드와 같은 무게로 늘어놓으면 그냥 지나친다. 입장권 모양으로
 * 세워 "표가 있는 행사" 라는 걸 모양으로 먼저 말한다. 주인공은 오른쪽 조각의 **남은 날짜**다.
 *
 * 디자인 정본은 아티팩트 「호요랜드 배너 시안」 C안(2026-09-28 확정) — 부품은 `HoyolandTicket.kt`.
 * 예전 슬레이트 그라데이션 광고 배너를 대체했다. iOS `HoyolandHomeBanner` 와 파리티.
 */
@Composable
fun DashHoyolandCard(event: HoyolandEvent, onTap: () -> Unit) {
    val deep = hoyolandTicketDeep()
    val (cap, big) = event.ticketCountdown()
    // 절취선 위 · 아래 가장자리에 반원 홈.
    val shape = remember { HoyolandTicketShape { h -> listOf(0f, h) } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .clickable { onTap() },
    ) {
        Column(
            // 흰 홈 바탕(10/1)과 구분되게 강조색을 옅게 깐다 — 흰 면이면 표 몸통이 바탕에 묻힌다.
            Modifier.weight(1f).fillMaxHeight().background(Color.White).background(deep.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            HoyolandTicketKicker(event.editionLabel, deep)
            Spacer(Modifier.height(5.dp))
            Text(
                event.edition, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "${event.periodNoYearLabel} · ${event.venueTicketLabel}",
                fontSize = 13.sp, color = TicketSubText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        HoyolandTicketStub(cap, big, deep)
    }
}
