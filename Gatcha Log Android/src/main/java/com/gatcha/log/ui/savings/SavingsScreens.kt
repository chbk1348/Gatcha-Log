package com.gatcha.log.ui.savings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.data.BadgeState
import com.gatcha.log.data.ChallengeProgress
import com.gatcha.log.data.ChallengeSummary
import com.gatcha.log.data.GameData
import com.gatcha.log.data.SavingsChallenge
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

private val WarnAmber = Color(0xFFF59E0B)
private val GoldEarn = Color(0xFFF2B441)
private val SpentRed = Color(0xFFEF6A6A)
private val BandColor = Color(0xFFF2F4F6)
private val HairColor = Color(0xFFEEF0F2)
private val BarTrack = Color(0xFFEDEFF3)
private val LockedIcon = Color(0xFFB8BDC6)

/** 배지 id → 모던 아이콘(Material). 앱 전역 아이콘 톤과 통일(이모지 대신). iOS badgeSymbol 과 짝. */
private fun badgeIcon(id: String): ImageVector = when (id) {
    SavingsChallenge.B_FIRST -> Icons.Default.EnergySavingsLeaf
    SavingsChallenge.B_NOSPEND_7 -> Icons.Default.LocalFireDepartment
    SavingsChallenge.B_BUDGET -> Icons.Default.TrackChanges
    SavingsChallenge.B_NOSPEND_30 -> Icons.Default.Diamond
    SavingsChallenge.B_BUDGET_3MO -> Icons.Default.EmojiEvents
    SavingsChallenge.B_NOSPEND_MONTH -> Icons.Default.AcUnit
    SavingsChallenge.B_SAVE_3MO -> Icons.AutoMirrored.Filled.TrendingDown
    SavingsChallenge.B_GAME_BUDGET -> Icons.Default.SportsEsports
    SavingsChallenge.B_KING -> Icons.Default.WorkspacePremium
    else -> Icons.Default.Savings
}

/** 연속 무지출 일수로 판정하는 배지 — 설명 모달에 지금 기록을 함께 보여 준다. */
private val StreakBadges = setOf(SavingsChallenge.B_FIRST, SavingsChallenge.B_NOSPEND_7, SavingsChallenge.B_NOSPEND_30)

/**
 * 절약 챌린지 2.0 — 무지출 스트릭 · 이번 달 챌린지 · 배지 컬렉션.
 *
 * 카드 없이 화면 폭 섹션 + 10 띠(마이페이지 · 지출 화면과 같은 규격). 전부 결정형 룰(SavingsChallenge, AI 없음).
 * 배지를 누르면 얻는 방법 모달이 뜬다 — 문구는 공유 [BadgeState.howTo](판정 규칙과 한 곳).
 */
@Composable
fun SavingsChallengeScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val summary by viewModel.challenge.collectAsStateWithLifecycle()
    var openBadge by remember { mutableStateOf<BadgeState?>(null) }

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scrollState)
                .padding(top = glgDetailContentTop()),
        ) {
            StreakSection(viewModel, summary, accent)
            if (summary.challenges.isNotEmpty()) {
                Band()
                ChallengeSection(summary, accent)
            }
            Band()
            BadgeSection(summary) { openBadge = it }
            Band()
            Text(
                "무지출 스트릭·예산 달성은 지출 기록에서 자동 판정돼요. 배지는 한번 얻으면 유지돼요.",
                fontSize = 12.sp, color = TextSecondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            )
        }
        GlgDetailHeaderOverlay("절약 챌린지", onBack, scrollState = scrollState)
    }

    openBadge?.let { b -> BadgeInfoDialog(b, summary) { openBadge = null } }
}

@Composable
private fun Band() {
    Box(Modifier.fillMaxWidth().height(10.dp).background(BandColor))
}

/** 섹션 — 좌우 20 · 위 22 · 아래 20. 제목 17 굵게 + 오른쪽 보조 12. */
@Composable
private fun Section(title: String?, trailing: String? = null, top: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top, bottom = 20.dp)) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                if (trailing != null) Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
        content()
    }
}

// ── ① 연속 무지출 + 최근 7일 ──
@Composable
private fun StreakSection(viewModel: SpendingViewModel, summary: ChallengeSummary, accent: Color) {
    Section(null, top = 12.dp) {
        Text("연속 무지출", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Icon(Icons.Default.LocalFireDepartment, null, tint = accent, modifier = Modifier.size(26.dp).padding(bottom = 4.dp))
            Spacer(Modifier.width(6.dp))
            Text("${summary.noSpendStreak}", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(3.dp))
            Text("일째", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text("최고 기록 ${summary.bestStreak}일", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(16.dp))
        WeekStrip(viewModel, accent)
    }
}

/**
 * 최근 7일 — 칸은 **그날의 결과**(무지출 ✓ · 지출 카트)를 보여 주고, 오늘은 테두리 + 아래 「오늘」로 표시한다.
 * 예전엔 오늘 칸이 「오늘」 글자로 덮여 오늘 지출 여부가 안 보였다. 기호 글자(₩ · ✓) 대신 아이콘.
 */
@Composable
private fun WeekStrip(viewModel: SpendingViewModel, accent: Color) {
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()
    val spentDays = remember(spendings) { spendings.map { it.dayKey }.toSet() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        (6 downTo 0).forEach { ago ->
            val key = com.gatcha.log.data.DateUtil.localDayKeyAgo(ago)
            val spent = key in spentDays
            val isToday = ago == 0
            val shape = RoundedCornerShape(11.dp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(36.dp).clip(shape)
                        .background(if (spent) SpentRed.copy(alpha = 0.14f) else accent.copy(alpha = 0.14f))
                        .then(if (isToday) Modifier.border(2.dp, accent, shape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (spent) Icons.Default.ShoppingCart else Icons.Default.Check,
                        contentDescription = if (spent) "지출 있음" else "무지출",
                        tint = if (spent) SpentRed else accent, modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (isToday) "오늘" else com.gatcha.log.data.DateUtil.weekdayKo(System.currentTimeMillis() - ago * 86_400_000L),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isToday) accent else TextSecondary,
                )
            }
        }
    }
}

// ── ② 이번 달 챌린지 ──
@Composable
private fun ChallengeSection(summary: ChallengeSummary, accent: Color) {
    val done = summary.challenges.count { it.reached }
    Section("이번 달 챌린지", "$done / ${summary.challenges.size} 달성") {
        summary.challenges.forEachIndexed { i, c ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HairColor))
            ChallengeRow(c, accent)
        }
    }
}

@Composable
private fun ChallengeRow(c: ChallengeProgress, accent: Color) {
    // 게임별 챌린지는 **게임색**으로 진행바와 점을 칠한다(27.50.0 고도화).
    // 전부 강조색이면 "이게 어느 게임 것인가" 를 제목 글자로만 읽어야 한다.
    val gameColor = c.game.takeIf { it.isNotBlank() }
        ?.let { GameData.byNameOrNull(it)?.color?.toColor() }
    val tone = gameColor ?: accent
    Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (gameColor != null) {
                Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(gameColor))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(c.desc, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(10.dp))
            if (c.reached) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = tone, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("달성", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tone)
                }
            } else {
                Text("${c.current} / ${c.target}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(c.ratio, if (c.warn) WarnAmber else tone)
    }
}

// ── ③ 배지 컬렉션 ──
@Composable
private fun BadgeSection(summary: ChallengeSummary, onOpen: (BadgeState) -> Unit) {
    Section("획득 배지", "${summary.earnedBadgeCount} / ${summary.totalBadgeCount}") {
        Text(
            "배지를 누르면 얻는 방법을 볼 수 있어요",
            fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 0.dp, bottom = 16.dp).offset(y = (-6).dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            summary.badges.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { b -> BadgeCell(b, Modifier.weight(1f)) { onOpen(b) } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 배지 원형 — 얻은 배지는 금빛, 못 얻은 배지는 **그 배지 아이콘을 흐리게** + 작은 자물쇠(무엇인지 보이게). */
@Composable
private fun BadgeMedal(b: BadgeState, size: Dp) {
    Box(Modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(if (b.earned) GoldEarn.copy(alpha = 0.16f) else BandColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(badgeIcon(b.id), contentDescription = null, tint = if (b.earned) GoldEarn else LockedIcon, modifier = Modifier.size(size * 0.45f))
        }
        if (!b.earned) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.36f).clip(CircleShape).background(Color.White)
                    .border(1.dp, Color(0xFFE3E6EA), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, null, tint = Color(0xFF9AA0A6), modifier = Modifier.size(size * 0.2f))
            }
        }
    }
}

@Composable
private fun BadgeCell(b: BadgeState, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgeMedal(b, 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            b.title, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (b.earned) TextPrimary else TextSecondary, textAlign = TextAlign.Center, maxLines = 1,
        )
    }
}

/** 배지 설명 모달 — 큰 배지 · 이름 · 획득 상태 · 얻는 방법(공유 문구) · 스트릭 배지면 지금 기록. */
@Composable
private fun BadgeInfoDialog(b: BadgeState, summary: ChallengeSummary, onDismiss: () -> Unit) {
    GlgDialog(title = "", onDismiss = onDismiss, confirmText = "확인", onConfirm = onDismiss, dismissText = null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeMedal(b, 76.dp)
            Text(b.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 14.dp))
            Text(
                if (b.earned) "획득했어요" else "아직 못 얻었어요",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (b.earned) Color(0xFFB7791F) else TextSecondary,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (b.earned) GoldEarn.copy(alpha = 0.16f) else BandColor)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                b.howTo, fontSize = 14.sp, color = TextPrimary, textAlign = TextAlign.Center, lineHeight = 21.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (b.id in StreakBadges) {
                Text(
                    "지금 ${summary.noSpendStreak}일째 · 최고 ${summary.bestStreak}일",
                    fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════ 공용 소품

@Composable
private fun ProgressBar(ratio: Float, color: Color, height: Dp = 6.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(BarTrack)) {
        Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}
