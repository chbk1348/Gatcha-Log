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
import com.gatcha.log.data.BadgeState
import com.gatcha.log.data.ChallengeProgress
import com.gatcha.log.data.ChallengeSummary
import com.gatcha.log.data.SavingsPlan
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.util.won

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
    com.gatcha.log.data.SavingsChallenge.B_KING -> Icons.Default.WorkspacePremium
    else -> Icons.Default.Savings
}

// ══════════════════════════════════════════════════════════════════ A. 저축 플래너

/**
 * 픽업 대비 저축 플래너 — 진행 중 픽업까지 필요 재화·원화를 역산해 하루 저축 목표를 제시.
 * 목업: design_savings_planner_mockup.html. 계산은 전부 결정형(SavingsPlanner, AI 없음).
 */
@Composable
fun SavingsPlannerScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val plans by viewModel.savingsPlans.collectAsState()
    val hiddenPlans by viewModel.hiddenSavingsPlans.collectAsState()
    var editTarget by remember { mutableStateOf<SavingsPlan?>(null) }
    var showHidden by remember { mutableStateOf(false) }

    val hero = plans.firstOrNull { !it.secured } ?: plans.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        GlgScreenHeader("저축 플래너", onBack, Modifier.padding(horizontal = 16.dp)) {
            if (hiddenPlans.isNotEmpty()) {
                GlgCircleIconButton(
                    icon = if (showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showHidden) "숨긴 목표 접기" else "숨긴 목표 보기",
                    badgeCount = if (showHidden) 0 else hiddenPlans.size,
                    outlined = showHidden,
                ) { showHidden = !showHidden }
            }
        }
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (plans.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("진행 중인 픽업 목표가 없어요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "새 픽업이 시작되면 천장·재화를 바탕으로 '하루 얼마 모으면 확보'인지 계산해 드려요.",
                            fontSize = 12.sp, color = TextSecondary,
                        )
                    }
                }
            } else {
                // ① HERO — 가장 임박한(미확보) 목표의 하루 저축액
                if (hero != null) HeroCard(hero, accent) { editTarget = hero }

                // ② 다가오는 픽업 목표
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("다가오는 픽업 목표", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        plans.forEachIndexed { i, p ->
                            PlanRow(p, accent) { editTarget = p }
                            if (i < plans.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                        }
                    }
                }

                Text(
                    "필요 뽑기는 현재 천장·50/50을 반영한 최악 기준이에요. 확률·요율 기반 계산(AI 아님).",
                    fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            // ③ 숨긴(안 뽑는) 목표 — 헤더 버튼으로 펼침. 다시 표시 가능.
            if (showHidden && hiddenPlans.isNotEmpty()) {
                HiddenPlansCard(hiddenPlans, accent) { viewModel.setSavingsHidden(it.key, false) }
            }
        }
    }

    editTarget?.let { plan ->
        PlanInputDialog(
            plan = plan,
            onDismiss = { editTarget = null },
            onConfirm = { pity, guaranteed, held ->
                viewModel.setPityCount(plan.gameKey, pity)
                viewModel.setPityGuaranteed(plan.gameKey, guaranteed)
                viewModel.setHeldCurrency(plan.gameKey, held)
                editTarget = null
            },
            onHide = {
                viewModel.setSavingsHidden(plan.key, true)
                editTarget = null
            },
        )
    }
}

@Composable
private fun HeroCard(p: SavingsPlan, accent: Color, onTap: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(p.gameColor.toColor()))
                Spacer(Modifier.width(8.dp))
                Text("${p.pickupName} · ${p.game}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Pill(ddLabel(p.dDay), WarnAmber)
            }
            Spacer(Modifier.height(9.dp))
            if (p.secured) {
                Text("이미 확보 가능해요 🎉", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("보유 재화만으로 천장까지 도달해요.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("하루 ${won(p.dailyGoal)}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.width(3.dp))
                    Text("씩", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text("지금부터 매일 이만큼이면 ${p.neededPulls}뽑까지 안전해요", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(13.dp))
            ProgressBar(p.progressPercent / 100f, accent, height = 9.dp)
            Spacer(Modifier.height(6.dp))
            Row {
                Text("모음 ${p.savedWon.let { won(it) }}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text("목표 ${won(p.neededWonTotal)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("필요 뽑기", "${p.neededPulls}뽑", Modifier.weight(1f))
                InfoPill("남은 ${p.currency}", if (p.secured) "0" else p.remainingCurrency.commaStr(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlanRow(p: SavingsPlan, accent: Color, onTap: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onTap() }.padding(vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(p.gameColor.toColor()))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(p.pickupName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "${p.game} · ${if (p.type == "weapon") "무기" else "캐릭터"} · ${p.neededPulls}뽑 필요",
                    fontSize = 11.sp, color = TextSecondary,
                )
            }
            if (p.secured) Pill("✓ 확보", accent) else Pill(ddLabel(p.dDay), if (p.dDay in 0..7) WarnAmber else TextSecondary)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ProgressEmpty, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("필요 ${won(p.neededWonTotal)}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("천장 ${p.currentPity}${if (p.guaranteed) " (확정)" else ""}", fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(7.dp))
        ProgressBar(p.progressPercent / 100f, accent)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.secured) "추가 저축 불필요" else "하루 ${won(p.dailyGoal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.weight(1f))
            Text("${p.progressPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        }
    }
}

@Composable
private fun PlanInputDialog(
    plan: SavingsPlan,
    onDismiss: () -> Unit,
    onConfirm: (pity: Int, guaranteed: Boolean, held: Int) -> Unit,
    onHide: () -> Unit,
) {
    val accent = LocalAccent.current
    var pity by remember { mutableStateOf(plan.currentPity.toString()) }
    var guaranteed by remember { mutableStateOf(plan.guaranteed) }
    var held by remember { mutableStateOf(if (plan.heldCurrency > 0) plan.heldCurrency.toString() else "") }

    GlgDialog(
        title = "내 상태 입력",
        onDismiss = onDismiss,
        confirmText = "계산",
        onConfirm = { onConfirm(pity.toIntOrNull() ?: 0, guaranteed, held.toIntOrNull() ?: 0) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("${plan.pickupName} · ${plan.game}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                GlgTextField(
                    value = pity, onValueChange = { pity = it.filter(Char::isDigit) },
                    label = "현재 천장", modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                GlgTextField(
                    value = held, onValueChange = { held = it.filter(Char::isDigit) },
                    label = "보유 ${plan.currency}", modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text("50/50 상태", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SegChip("50:50 (미확정)", !guaranteed, accent, Modifier.weight(1f)) { guaranteed = false }
                SegChip("픽업 확정", guaranteed, accent, Modifier.weight(1f)) { guaranteed = true }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onHide() }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.VisibilityOff, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("이 픽업은 안 뽑아요 · 목록에서 숨기기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
    }
}

/** 숨긴(안 뽑는) 픽업 목표 — 헤더 버튼으로 펼침. 각 항목 '다시 표시'로 복귀. */
@Composable
private fun HiddenPlansCard(hidden: List<SavingsPlan>, accent: Color, onUnhide: (SavingsPlan) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VisibilityOff, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("숨긴 목표", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("${hidden.size}개", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            hidden.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(p.gameColor.toColor()))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.pickupName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "${p.game} · ${if (p.type == "weapon") "무기" else "캐릭터"}",
                            fontSize = 11.sp, color = TextSecondary,
                        )
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f))
                            .clickable { onUnhide(p) }.padding(horizontal = 11.dp, vertical = 6.dp),
                    ) { Text("다시 표시", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent) }
                }
                if (i < hidden.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════ H. 절약 챌린지

/**
 * 절약 챌린지·스트릭 — 무지출 스트릭·이번 달 챌린지·배지 컬렉션.
 * 목업: design_savings_challenge_mockup.html. 전부 결정형 룰(SavingsChallenge, AI 없음).
 */
@Composable
fun SavingsChallengeScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val summary by viewModel.challenge.collectAsState()

    Column(Modifier.fillMaxSize()) {
        GlgScreenHeader("절약 챌린지", onBack, Modifier.padding(horizontal = 16.dp))
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
    }
}

@Composable
private fun WeekStrip(viewModel: SpendingViewModel, accent: Color) {
    val spendings by viewModel.spendings.collectAsState()
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
    Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(c.desc, fontSize = 11.sp, color = TextSecondary)
            }
            Text(
                if (c.reached) "달성 ✓" else "${c.current} / ${c.target}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (c.reached) accent else TextPrimary,
            )
        }
        Spacer(Modifier.height(9.dp))
        ProgressBar(c.ratio, if (c.warn) WarnAmber else accent)
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

/** 홈 허브용 컴팩트 진입 카드 — 저축 플래너. */
@Composable
fun PickupPlannerHomeCard(plans: List<SavingsPlan>, onOpen: () -> Unit) {
    val accent = LocalAccent.current
    val top = plans.firstOrNull { !it.secured } ?: plans.firstOrNull()
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Savings, null, tint = accent, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(8.dp))
                Text("픽업 대비 저축 계획", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("열기 ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(11.dp))
            if (top == null) {
                Text("진행 중인 픽업 목표가 없어요", fontSize = 12.sp, color = TextSecondary)
            } else if (top.secured) {
                Text("${top.pickupName} — 이미 확보 가능 🎉", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(top.pickupName, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("하루 ${won(top.dailyGoal)} · ${ddLabel(top.dDay)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(9.dp))
                ProgressBar(top.progressPercent / 100f, accent)
            }
        }
    }
}

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
