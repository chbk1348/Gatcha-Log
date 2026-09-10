package com.gatcha.log.ui.savings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.data.BadgeState
import com.gatcha.log.data.ChallengeProgress
import com.gatcha.log.data.ChallengeSummary
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.util.won
import com.gatcha.log.data.GameData
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.foundation.shape.CircleShape

private val WarnAmber = Color(0xFFF59E0B)
private val GoldEarn = Color(0xFFF2B441)

private fun Int.commaStr(): String {
    val s = this.toString(); val sb = StringBuilder(); val n = s.length
    for (i in 0 until n) { if (i > 0 && (n - i) % 3 == 0) sb.append(','); sb.append(s[i]) }
    return sb.toString()
}

private fun ddLabel(d: Int): String = when { d > 0 -> "D-$d"; d == 0 -> "D-DAY"; else -> "종료" }

/** 배지 id → 모던 아이콘(Material). 앱 전역 아이콘 톤과 통일(이모지 대신). */
private fun badgeIcon(id: String): ImageVector = when (id) {
    com.gatcha.log.data.SavingsChallenge.B_FIRST -> Icons.Default.EnergySavingsLeaf
    com.gatcha.log.data.SavingsChallenge.B_NOSPEND_7 -> Icons.Default.LocalFireDepartment
    com.gatcha.log.data.SavingsChallenge.B_BUDGET -> Icons.Default.TrackChanges
    com.gatcha.log.data.SavingsChallenge.B_NOSPEND_30 -> Icons.Default.Diamond
    com.gatcha.log.data.SavingsChallenge.B_BUDGET_3MO -> Icons.Default.EmojiEvents
    com.gatcha.log.data.SavingsChallenge.B_NOSPEND_MONTH -> Icons.Default.AcUnit
    com.gatcha.log.data.SavingsChallenge.B_SAVE_3MO -> Icons.AutoMirrored.Filled.TrendingDown
    com.gatcha.log.data.SavingsChallenge.B_GAME_BUDGET -> Icons.Default.SportsEsports
    com.gatcha.log.data.SavingsChallenge.B_KING -> Icons.Default.WorkspacePremium
    else -> Icons.Default.Savings
}

// ══════════════════════════════════════════════════════════════════ A. 저축 플래너

/**
 * 절약 챌린지·스트릭 — 무지출 스트릭·이번 달 챌린지·배지 컬렉션.
 * 목업: design_savings_challenge_mockup.html. 전부 결정형 룰(SavingsChallenge, AI 없음).
 */
@Composable
fun SavingsChallengeScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val summary by viewModel.challenge.collectAsStateWithLifecycle()

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = glgDetailContentTop(), bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ① HERO — 무지출 스트릭 + 최근 7일 스트립
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("연속 무지출", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = accent, modifier = Modifier.size(26.dp).padding(bottom = 3.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${summary.noSpendStreak}", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.width(3.dp))
                        Text("일째", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text("최고 기록 ${summary.bestStreak}일", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(Modifier.height(14.dp))
                    WeekStrip(viewModel, accent)
                }
            }

            // ② 이번 달 챌린지
            if (summary.challenges.isNotEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("이번 달 챌린지", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.weight(1f))
                            val done = summary.challenges.count { it.reached }
                            Text("$done / ${summary.challenges.size} 달성", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                        Spacer(Modifier.height(6.dp))
                        summary.challenges.forEachIndexed { i, c ->
                            ChallengeRow(c, accent)
                            if (i < summary.challenges.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                        }
                    }
                }
            }

            // ③ 배지 컬렉션
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("획득 배지", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("${summary.earnedBadgeCount} / ${summary.totalBadgeCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("챌린지·스트릭을 달성하면 배지를 모을 수 있어요", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(14.dp))
                    BadgeGrid(summary.badges)
                }
            }

            Text(
                "무지출 스트릭·예산 달성은 지출 기록에서 자동 판정돼요. 배지는 한번 얻으면 유지됩니다.",
                fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        GlgDetailHeaderOverlay("절약 챌린지", onBack, scrollState = scrollState)
    }
}

@Composable
private fun WeekStrip(viewModel: SpendingViewModel, accent: Color) {
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()
    val spentDays = remember(spendings) { spendings.map { it.dayKey }.toSet() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        // 6일 전 → 오늘 순으로
        (6 downTo 0).forEach { ago ->
            val key = com.gatcha.log.data.DateUtil.localDayKeyAgo(ago)
            val spent = key in spentDays
            val isToday = ago == 0
            val bg = when {
                isToday -> accent
                spent -> Color(0xFFFDECEC)
                else -> accent.copy(alpha = 0.14f)
            }
            val fg = when {
                isToday -> Color.White
                spent -> Color(0xFFEF6A6A)
                else -> accent
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(11.dp)).background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isToday) "오늘" else if (spent) "₩" else "✓", fontSize = if (isToday) 12.sp else 15.sp, fontWeight = FontWeight.Bold, color = fg)
                }
                Spacer(Modifier.height(5.dp))
                Text(com.gatcha.log.data.DateUtil.weekdayKo(System.currentTimeMillis() - ago * 86_400_000L), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ChallengeRow(c: ChallengeProgress, accent: Color) {
    // 게임별 챌린지는 **게임색**으로 진행바와 점을 칠한다(27.50.0 고도화).
    // 전부 강조색이면 "이게 어느 게임 것인가" 를 제목 글자로만 읽어야 한다.
    val gameColor = c.game.takeIf { it.isNotBlank() }
        ?.let { GameData.byNameOrNull(it)?.color?.toColor() }
    Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (gameColor != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(gameColor))
                Spacer(Modifier.width(7.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(c.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(c.desc, fontSize = 11.sp, color = TextSecondary)
            }
            Text(
                if (c.reached) "달성 ✓" else "${c.current} / ${c.target}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (c.reached) (gameColor ?: accent) else TextPrimary,
            )
        }
        Spacer(Modifier.height(9.dp))
        ProgressBar(c.ratio, if (c.warn) WarnAmber else (gameColor ?: accent))
    }
}

@Composable
private fun BadgeGrid(badges: List<BadgeState>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        badges.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { b -> BadgeCell(b, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeCell(b: BadgeState, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                .background(if (b.earned) GoldEarn.copy(alpha = 0.16f) else Color(0xFFF6F7F9))
                .border(1.dp, if (b.earned) GoldEarn.copy(alpha = 0.5f) else Color(0xFFE3E5EA), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (b.earned) badgeIcon(b.id) else Icons.Default.Lock,
                contentDescription = b.title,
                tint = if (b.earned) GoldEarn else ProgressEmpty,
                modifier = Modifier.size(if (b.earned) 26.dp else 18.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(b.title, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (b.earned) TextPrimary else TextSecondary)
    }
}

// ══════════════════════════════════════════════════════════════════ 홈 진입 카드

/** 홈 허브용 컴팩트 진입 카드 — 절약 챌린지. */
@Composable
fun SavingsChallengeHomeCard(summary: ChallengeSummary, onOpen: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocalFireDepartment, null, tint = accent, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(8.dp))
                Text("절약 챌린지", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("열기 ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(5.dp))
                Text("${summary.noSpendStreak}일", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text("연속 무지출", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("배지 ${summary.earnedBadgeCount}/${summary.totalBadgeCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════ 공용 소품

@Composable
private fun ProgressBar(ratio: Float, color: Color, height: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(ProgressEmpty)) {
        Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color) }
}

@Composable
private fun InfoPill(label: String, value: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF6F7F9)).border(1.dp, Color(0xFFE3E5EA), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Column {
            Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
private fun SegChip(text: String, on: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(11.dp))
            .background(if (on) accent.copy(alpha = 0.14f) else Color(0xFFF6F7F9))
            .border(1.dp, if (on) accent.copy(alpha = 0.5f) else Color(0xFFE3E5EA), RoundedCornerShape(11.dp))
            .clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) accent else TextSecondary) }
}
