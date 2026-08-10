package com.gatcha.log.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.gatcha.log.data.BannerPlan
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
/** 카드 '바깥' 위에 놓는 큰 섹션 제목(+옵션 카운트/전체보기). 홈 재구성 공통. */
@Composable
fun HomeSectionHeader(title: String, count: Int? = null, actionTitle: String? = null, onAction: (() -> Unit)? = null) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
            HeroPill("예산 설정하기", onBudget)
        }
    }
}

@Composable
private fun HeroPill(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f)),
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { onClick() },
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
    }
}

// ── 히어로 고정 그라데이션 배경 + 은은한 글로우(느린 좌우 드리프트) ───────────
/** 스크롤과 무관하게 상단에 '고정'되는 그라데이션 + 천천히 떠다니는 글로우. 하단은 완전 투명 페이드. */
@Composable
fun HeroGradientBackground(modifier: Modifier = Modifier, glow: Boolean = true) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    // 모션 감속(저RAM·절전·접근성 애니 끄기)이면 글로우를 고정한다 — 스켈레톤 시머는 이미 이 토큰을
    // 지키는데 글로우만 안 지키고 있었다. 5초 주기 무한 애니메이션이라 저사양 단말에 그대로 부담이 된다.
    // (탭을 벗어나면 호출부가 `selectedTab == 0` 으로 이 컴포저블을 통째로 폐기하므로,
    //  '안 보일 때 멈춘다'는 iOS AmbientHeroGradient 의 onScreen 처리에 이미 대응된다)
    // ⚠️ 애니메이션 값을 **State 인 채로** 들고 다닌다(`by` 로 풀지 않는다).
    // 예전엔 여기서 값을 꺼내 `Modifier.offset(x = drift.dp)` 에 넘겼는데, 그러면 5초 주기
    // 애니메이션이 **컴포지션 단계**에서 읽혀 홈 탭 전체가 초당 60번 재구성됐다.
    // 아래 `offset { }`(람다 버전)은 **레이아웃 단계**에서 읽으므로 재구성이 아예 없다.
    // 결과 화면은 완전히 동일하다.
    val driftState = if (LocalReduceMotion.current || !glow) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "heroGlow")
        transition.animateFloat(
            initialValue = -84f, targetValue = 84f,
            animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "drift",
        )
    }
    // 브러시는 강조색이 바뀔 때만 다시 만든다 — 재구성마다 새로 만들 이유가 없다.
    val baseBrush = remember(accent2) {
        Brush.verticalGradient(
            0.0f to accent2.copy(alpha = 0.45f),
            0.62f to accent2.copy(alpha = 0.14f),
            1.0f to accent2.copy(alpha = 0f),
        )
    }
    val glowBrush = remember(accent) {
        Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent))
    }
    Box(modifier.clipToBounds()) {
        Box(Modifier.fillMaxSize().background(baseBrush))
        // 은은한 글로우 — 흐릿한 원(radial)이 상단에서 좌우로 드리프트.
        // 하단 클립(선) 방지: 글로우가 박스 하단 훨씬 위에서 완전 투명이 되도록 작게·위로 배치.
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset { IntOffset((driftState?.value ?: 0f).dp.roundToPx(), (-10).dp.roundToPx()) }
                .size(240.dp)
                .background(glowBrush),
        )
    }
}

// ── 최근 지출 (목업 Transaction 리스트) ──────────────────────────────────────
@Composable
fun RecentSpendCard(spendings: List<Spending>, onSeeAll: () -> Unit) {
    // 지출이 바뀔 때만 정렬한다. 예전엔 remember 가 없어 이 카드가 재구성될 때마다
    // **지출 전체를 정렬**했다(4건만 쓰는데). 홈은 스크롤·애니메이션으로 재구성이 잦다.
    val recent = remember(spendings) { spendings.sortedByDescending { it.dateMillis }.take(4) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader("최근 지출", actionTitle = if (recent.isEmpty()) null else "전체보기", onAction = onSeeAll)
        GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                if (recent.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Description, null, tint = Color.LightGray, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("아직 기록된 지출이 없어요", fontSize = 13.sp, color = TextSecondary)
                        Text("+ 지출 추가로 첫 기록을 남겨보세요", fontSize = 11.sp, color = Color.LightGray)
                    }
                } else {
                    recent.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 64.dp))
                        RecentSpendRow(s)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSpendRow(s: Spending) {
    val color = s.gameColor.toColor()
    val abbr = GameData.byNameOrNull(s.gameName)?.abbr ?: s.gameName.take(2)
    val subtitle = listOfNotNull(s.dateLabel, s.itemName.ifBlank { null }).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                if (s.isSubscription) {
                    Spacer(Modifier.width(5.dp))
                    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                        Text("정기", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(won(s.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
    }
}

// ── M: 이번 달 한눈에 (AI 요약) ──────────────────────────────────────────────
@Composable
fun MonthlySummaryCard(
    monthlyTotal: Long,
    prevTotal: Long,
    budget: Long,
    nextBanner: GachaBanner?,
    gameOverCount: Int,
    onBudget: () -> Unit,
    onTip: () -> Unit,
) {
    val accent = LocalAccent.current
    // 하루 단위로 고정되는 시드 — 매일 다른 어투, 같은 날 리컴포지션엔 안 흔들림
    val daySeed = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }
    val summary = remember(monthlyTotal, prevTotal, budget, nextBanner, gameOverCount, daySeed) {
        buildMonthlySummary(monthlyTotal, prevTotal, budget, nextBanner, gameOverCount, accent, daySeed)
    }
    // 팔레트 통일 — 다크 그라데이션 히어로를 라이트 글라스 카드로(나머지 카드와 동일 계열)
    GlassCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("이번 달 한눈에", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(summary, fontSize = 14.sp, color = TextPrimary, lineHeight = 21.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip(if (budget > 0) "예산 점검" else "예산 세우기", onBudget)
                SummaryChip("절약 팁", onTip)
            }
        }
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

// ── 인사이트 요약 엔진 ───────────────────────────────────────────────────────
// 단순 금액 재진술(절대금액은 D 카드 담당) 대신 ①전월 대비 ②페이스/예산 ③천장·픽업 을
// 우선순위로 조합해 최대 3문장. 일자 시드로 어투만 매일 변주(같은 날 리컴포지션엔 고정).
/**
 * 온디바이스 규칙 기반 월간 인사이트(LLM 미사용). 강조 단어만 색 span.
 * - prevTotal: 전월 총 지출(MoM). 0이면 비교 생략.
 * - topPity: 가장 임박한 천장 1종. - nextBanner: 가장 임박한 픽업 배너 1종.
 * - gameOverCount: 게임별 한도를 넘긴 게임 수.
 */
private fun buildMonthlySummary(
    monthlyTotal: Long,
    prevTotal: Long,
    budget: Long,
    nextBanner: GachaBanner?,
    gameOverCount: Int,
    accent2: Color,
    seed: Int,
): AnnotatedString {
    // 라이트 글라스 카드 위 — 흰색 대신 본문색, 경고는 진한 빨강, 강조는 강조색
    val white = SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)
    val warn = SpanStyle(color = DangerText, fontWeight = FontWeight.Bold)
    val mint = SpanStyle(color = accent2, fontWeight = FontWeight.Bold)
    val rnd = Random(seed)
    fun <T> List<T>.pick(): T = this[rnd.nextInt(size)]

    val parts = mutableListOf<AnnotatedString>()

    // ① 전월 대비(MoM) — prev 있으면 비교, 없으면 절대금액 1회만(D와 중복 최소화 위해 첫 달만)
    if (monthlyTotal <= 0L) {
        parts += AnnotatedString(listOf("이번 달은 아직 지출이 없어요.", "이번 달 지출 기록이 비어 있어요.").pick())
    } else if (prevTotal > 0L) {
        val diff = ((monthlyTotal - prevTotal) * 100 / prevTotal).toInt()
        parts += when {
            diff >= 5 -> buildAnnotatedString {
                append(listOf("지난달보다 ", "전월 대비 ").pick()); withStyle(warn) { append("${diff}% 더") }
                append(listOf(" 쓰고 있어요.", " 지출 중이에요.").pick())
            }
            diff <= -5 -> buildAnnotatedString {
                append(listOf("지난달보다 ", "전월 대비 ").pick()); withStyle(mint) { append("${-diff}% 덜") }
                append(listOf(" 쓰고 있어요.", " 아꼈어요.").pick())
            }
            else -> AnnotatedString("지난달과 비슷한 페이스예요.")
        }
    } else {
        parts += buildAnnotatedString {
            append("이번 달 "); withStyle(white) { append(won(monthlyTotal)) }; append(" 쓰고 있어요.")
        }
    }

    // ② 페이스/예산
    // buildMonthlySummary 는 @Composable 이 아니라 remember 를 못 쓴다. 대신 Calendar 를 한 번만
    // 만들어 두 값을 같이 꺼낸다(예전엔 그래도 매 호출 1개였지만, 여기선 그 이상 줄일 게 없다).
    val cal = java.util.Calendar.getInstance()
    val passed = cal.get(java.util.Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    val totalDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val projected = monthlyTotal * totalDays / passed
    if (budget > 0) {
        val pct = (monthlyTotal * 100 / budget).toInt()
        parts += when {
            monthlyTotal > budget -> buildAnnotatedString {
                append("예산을 "); withStyle(warn) { append("${pct - 100}%") }; append(" 넘겼어요. 이번 달은 무·저과금을 권해요.")
            }
            pct >= 90 -> buildAnnotatedString {
                append("예산 "); withStyle(warn) { append("${pct}%") }; append(" 소진 — 마무리 조심하세요.")
            }
            projected > budget -> buildAnnotatedString {
                append("이 페이스면 월말 약 "); withStyle(warn) { append("${projected / 10000}만원") }; append(", 예산을 넘길 수 있어요.")
            }
            else -> buildAnnotatedString {
                append("예산의 "); withStyle(mint) { append("${pct}%") }; append(" 선에서 잘 쓰고 있어요.")
            }
        }
    } else if (projected >= 10000L) {
        parts += buildAnnotatedString {
            append("이 페이스면 월말 약 "); withStyle(white) { append("${projected / 10000}만원") }; append(" 예상이에요.")
        }
    }

    // ③ 다음 픽업
    if (nextBanner != null) {
        val urgent = nextBanner.isUrgent()
        parts += buildAnnotatedString {
            withStyle(mint) { append(nextBanner.name) }; append(" 픽업 ")
            withStyle(if (urgent) warn else mint) { append(nextBanner.remainLabel()) }
            append(if (urgent) ", 막바지예요." else " 진행 중이에요.")
        }
    } else if (gameOverCount > 0) {
        parts += buildAnnotatedString {
            withStyle(warn) { append("${gameOverCount}개 게임") }; append("이 한도를 넘었어요. 게임별 예산을 점검해보세요.")
        }
    }

    return buildAnnotatedString {
        parts.take(3).forEachIndexed { i, p -> if (i > 0) append(" "); append(p) }
    }
}

// ── D: 지출 + 게임별 예산 ────────────────────────────────────────────────────
@Composable
fun SpendingBudgetSection(
    monthlyTotal: Long,
    budget: Long,
    perGame: List<GameSpend>,
    onEditBudget: () -> Unit,
) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    val ratio = if (budget > 0) (monthlyTotal.toFloat() / budget).coerceIn(0f, 1f) else 0f
    val pct = if (budget > 0) (monthlyTotal * 100 / budget).toInt() else 0
    val over = budget > 0 && monthlyTotal > budget

    GlassCard(shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("이번 달 지출", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "%d년 %d월".format(DateUtil.year(System.currentTimeMillis()), DateUtil.month(System.currentTimeMillis())),
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    )
                }
                IconButton(onClick = onEditBudget, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "예산 설정", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(won(monthlyTotal), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            if (budget > 0) {
                BudgetBar(ratio, over)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (over) won(monthlyTotal - budget) + " 초과" else won(budget - monthlyTotal) + " 남음",
                        fontSize = 11.sp, color = if (over) DangerText else TextSecondary,
                    )
                    Text("예산 ${pct}% 사용", fontSize = 11.sp, color = TextSecondary)
                }
            } else {
                Surface(
                    color = accent.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onEditBudget() },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Savings, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("월 예산 미설정 — 탭하여 설정하면 사용률이 표시돼요", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }

            // 게임별 예산 막대 (N5)
            if (perGame.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("게임별 예산", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("한도 설정 ›", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.clickable { onEditBudget() })
                }
                Spacer(Modifier.height(12.dp))
                perGame.forEachIndexed { i, gs ->
                    if (i > 0) Spacer(Modifier.height(11.dp))
                    GameBudgetRow(gs, accent, accent2)
                }
            }
        }
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

// ── 픽업 배너 캡슐 (O) ───────────────────────────────────────────────────────
@Composable
fun BannerCapsule(banner: GachaBanner) {
    val accent = LocalAccent.current
    // banner.game 은 displayName(예: "원신") — byNameOrNull 로 매핑
    val g = GameData.byNameOrNull(banner.game)
    val color = banner.gameColor.toColor()
    val urgent = banner.isUrgent()
    val chipColor = if (urgent) WarningText else accent
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        border = BorderStroke(1.dp, DividerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            GlgGameTag(banner.game, size = GameTagSize.Small)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(banner.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${g?.shortName ?: banner.game} · 픽업", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Surface(color = chipColor.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                Text(
                    banner.remainLabel(),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chipColor, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
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

/** 오늘 할 일 카드 — 활성 항목을 전부 리스트로. titleOutside=true 면 제목을 카드 바깥 큰 헤더로. */
@Composable
fun TodayTaskCard(tasks: List<TodayItem>, inProgress: Boolean, titleOutside: Boolean = false) {
    val accent = LocalAccent.current
    if (titleOutside) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSectionHeader("오늘 할 일", count = if (tasks.isEmpty()) null else tasks.size)
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { TodayTaskBody(tasks, inProgress) }
            }
        }
    } else {
        GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TaskAlt, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("오늘 할 일", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                    if (tasks.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                            Text("${tasks.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TodayTaskBody(tasks, inProgress)
            }
        }
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

/** 오늘 할 일 로딩 스켈레톤 — 헤더 + 시머 행 N개. titleOutside=true 면 제목을 카드 바깥으로. */
@Composable
fun TodayTaskSkeleton(rows: Int = 3, titleOutside: Boolean = false) {
    val accent = LocalAccent.current
    if (titleOutside) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSectionHeader("오늘 할 일")
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { TodaySkeletonRows(rows) }
            }
        }
    } else {
        GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TaskAlt, null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("오늘 할 일", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent.copy(alpha = 0.5f))
                }
                Spacer(Modifier.height(12.dp))
                TodaySkeletonRows(rows)
            }
        }
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

/** 대시보드 리스트 카드 로딩 스켈레톤 — 헤더 + 행 N개. '이번 주 일정'·'게임 소식' 카드와 동일 형태. */
@Composable
fun DashCardSkeleton(rows: Int = 3) {
    GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SkeletonBox(Modifier.width(90.dp).height(15.dp))
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

@Composable
private fun PityMini(p: PityHighlight?, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, DividerColor),
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text("천장", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            if (p == null) {
                Text("기록 없음", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            } else {
                val c = p.tier.accentColor()
                Text(p.game.shortName, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${p.count}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c)
                    Text("/${p.hard}", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
                }
                Spacer(Modifier.height(6.dp))
                val ratio = (p.count.toFloat() / p.hard).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(ProgressEmpty)) {
                    Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(CircleShape).background(c))
                }
                Spacer(Modifier.height(6.dp))
                Surface(color = c.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                    Text(p.tier.shortLabel(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun NextBannerMini(b: GachaBanner?, plan: BannerPlan?, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, DividerColor),
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text("다음 픽업", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            if (b == null) {
                Text("예정 없음", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            } else {
                val d = b.dDay()
                val urgent = d <= 3
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlgGameTag(b.game, size = GameTagSize.Small)
                    Spacer(Modifier.width(8.dp))
                    Text(b.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Text(GameData.byNameOrNull(b.game)?.shortName ?: b.game, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                val c = if (urgent) WarningText else LocalAccent.current
                Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                    Text(dhLabel(b.endMillis), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                // 픽업 확정 비용 인텔리전스 — 천장 누적·확률·1뽑 단가로 산출(가챠×지출 결합)
                if (plan != null) {
                    Spacer(Modifier.height(7.dp))
                    Text("확정 최대 ${plan.maxPulls}연", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    Text("약 ${won(plan.wonCost)}", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 홈 대시보드 개편(27.32.0) — 깔끔한 KPI 중심 레이아웃
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun DashSpendCard(monthlyTotal: Long, budget: Long, onTap: () -> Unit) {
    val accent = LocalAccent.current
    // 날짜 값은 재구성마다 다시 만들지 않는다.
    val (day, days, month) = remember {
        val c = java.util.Calendar.getInstance()
        Triple(
            c.get(java.util.Calendar.DAY_OF_MONTH),
            c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH),
            c.get(java.util.Calendar.MONTH) + 1,
        )
    }
    val remain = (days - day).coerceAtLeast(0)
    val pct = if (budget > 0) (monthlyTotal * 100 / budget).toInt() else 0
    val frac = if (budget > 0) (monthlyTotal.toFloat() / budget).coerceIn(0f, 1f) else 0f
    val over = budget > 0 && monthlyTotal > budget
    GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
        Column(Modifier.padding(18.dp)) {
            Text("${month}월 지출", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(won(monthlyTotal), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (budget > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("/ 예산 ${won(budget)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                }
            }
            if (budget > 0) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(ProgressEmpty)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(if (over) DangerText else accent))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (over) "예산 ${pct - 100}% 초과" else "예산의 ${pct}% 사용", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (over) DangerText else accent)
                    Text("남은 ${remain}일", fontSize = 11.5.sp, color = TextSecondary)
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text("예산을 정하면 페이스를 알려드려요", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

/** 이번 주 게임 일정 — titleOutside=true 면 제목을 카드 바깥 큰 헤더로. */
@Composable
fun DashScheduleCard(events: List<GameEvent>, challenges: List<GameChallenge>, titleOutside: Boolean = false, onTap: () -> Unit) {
    val accent = LocalAccent.current
    val now = System.currentTimeMillis()
    val items = (events.map { Triple(it.game, it.name, it.endMillis to it.dDayLabel()) } +
        challenges.map { Triple(it.game, it.name, it.endMillis to it.dDayLabel()) })
        .filter { it.third.first > now }.sortedBy { it.third.first }.take(3)
    if (items.isEmpty()) return
    if (titleOutside) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSectionHeader("이번 주 일정", actionTitle = "전체", onAction = onTap)
            GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
                Column(Modifier.padding(16.dp)) { ScheduleRows(items, accent) }
            }
        }
    } else {
        GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("이번 주 일정", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("전체 ›", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
                }
                Spacer(Modifier.height(11.dp))
                ScheduleRows(items, accent)
            }
        }
    }
}

@Composable
private fun ScheduleRows(items: List<Triple<String, String, Pair<Long, String>>>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        items.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlgGameTag(row.first, size = GameTagSize.Small)
                Spacer(Modifier.width(9.dp))
                Text(row.second, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(row.third.second, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

/** 게임 소식 — titleOutside=true 면 제목을 카드 바깥 큰 헤더로. */
@Composable
fun DashNewsCard(news: List<NewsItem>, anniversaries: List<AnniversaryInfo>, titleOutside: Boolean = false, onTap: () -> Unit) {
    val accent = LocalAccent.current
    val anni = anniversaries.firstOrNull { it.daysUntil <= 60 }
    // 홈은 2건뿐이라 최신순으로 자르면 한 게임이 둘 다 먹기 쉽다 — 게임을 번갈아 뽑는다.
    val topNews = NewsLogic.previewTop(news, 2)
    if (anni == null && topNews.isEmpty()) return
    if (titleOutside) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSectionHeader("게임 소식", actionTitle = "전체", onAction = onTap)
            GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
                Column(Modifier.padding(16.dp)) { NewsBody(anni, topNews) }
            }
        }
    } else {
        GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("게임 소식", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("전체 ›", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
                }
                Spacer(Modifier.height(12.dp))
                NewsBody(anni, topNews)
            }
        }
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

