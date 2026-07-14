package com.gatcha.log.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.dhLabel
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GachaRateData
import com.gatcha.log.data.PityState
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.AnniversaryInfo
import com.gatcha.log.data.api.NewsItem
import androidx.compose.material.icons.filled.Celebration
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.PityTier
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

/** 천장 하이라이트 — 가장 임박한 게임 1종(요약·가챠 현황 카드에 공유). */
data class PityHighlight(
    val game: Game,
    val count: Int,
    val soft: Int,
    val hard: Int,
    val tier: PityTier,
)

/** 게임별 이번 달 지출/한도 (D 섹션). */
data class GameSpend(val game: Game, val spent: Long, val limit: Long)

// ── 슬림 헤더 ────────────────────────────────────────────────────────────────
@Composable
fun HomeHeader(
    photoUrl: String?,
    alertCount: Int,
    onBellClick: () -> Unit,
) {
    // 인사말·이름·연속출석 문구 제거 — 아바타와 알림벨만 노출 (대표 지시, Android 전용)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 2.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(photoUrl = photoUrl, size = 46.dp)
        Spacer(Modifier.weight(1f))
        GlgCircleIconButton(
            Icons.Default.NotificationsNone,
            contentDescription = "알림",
            badgeCount = alertCount,
            outlined = true,
            onClick = onBellClick,
        )
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
        val d = nextBanner.dDay()
        parts += buildAnnotatedString {
            withStyle(mint) { append(nextBanner.name) }; append(" 픽업 ")
            withStyle(if (d <= 3) warn else mint) { append(dhLabel(nextBanner.endMillis)) }
            append(if (d <= 3) ", 막바지예요." else " 진행 중이에요.")
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
    val d = banner.dDay()
    val urgent = d <= 3
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
                    dhLabel(banner.endMillis),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chipColor, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// ── 오늘 할 일 (상태 기반 스마트 액션) ────────────────────────────────────────
/** 재화(레진/개척력/배터리) 임박 경보. full=가득, recovery="약 N시간 후 충전". */
data class ResinAlert(val gameShort: String, val label: String, val cur: Int, val max: Int, val recovery: String, val full: Boolean)

/** 픽업 확정 계획 — 최악의 경우 필요한 뽑기 수와 원화 비용(가챠×지출 결합 지표). */
data class BannerPlan(val maxPulls: Int, val wonCost: Long)

/** 오늘 할 일 한 줄. busyable=전체출석처럼 진행 중 스피너가 필요한 항목. */
data class TodayItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val message: String,
    val ctaLabel: String,
    val urgent: Boolean,
    val busyable: Boolean,
    val onAction: () -> Unit,
)

/**
 * 출석·재화·픽업·예산·천장 상태를 우선순위 순으로 훑어 **활성 할 일을 전부** 리스트로 산출.
 * 순서(시간 민감도): 미출석 → 재화 임박 → 픽업 막바지 → 예산 경고 → 천장 임박. 없으면 빈 리스트.
 */
fun resolveTodayTasks(
    pendingAttendance: Int,
    resins: List<ResinAlert>,
    urgentBanner: GachaBanner?,
    budget: Long,
    monthlyTotal: Long,
    onCheckInAll: () -> Unit,
    onResin: () -> Unit,
    onBanner: () -> Unit,
    onBudget: () -> Unit,
): List<TodayItem> = buildList {
    val budgetPct = if (budget > 0) (monthlyTotal * 100 / budget).toInt() else 0
    if (pendingAttendance > 0)
        add(TodayItem(Icons.Default.DoneAll, "출석 안 한 게임 ${pendingAttendance}개", "한 번에 출석", false, true, onCheckInAll))
    // 가득/임박한 게임을 전부 — 원신뿐 아니라 스타레일·젠레스 등 해당되는 모든 게임
    resins.forEach { r ->
        add(TodayItem(
            Icons.Default.Bolt,
            if (r.full) "${r.gameShort} ${r.label} 가득 참" else "${r.gameShort} ${r.label} ${r.cur}/${r.max} 곧 넘침",
            "게임 정보", true, false, onResin,
        ))
    }
    if (urgentBanner != null) {
        add(TodayItem(Icons.Default.Casino, "${urgentBanner.name} 픽업 ${dhLabel(urgentBanner.endMillis)} 막바지", "픽업 계획", true, false, onBanner))
    }
    if (budget > 0 && monthlyTotal > budget)
        add(TodayItem(Icons.Default.Savings, "예산 ${budgetPct - 100}% 초과", "예산 점검", true, false, onBudget))
    else if (budget > 0 && budgetPct >= 90)
        add(TodayItem(Icons.Default.Savings, "예산 ${budgetPct}% 사용", "예산 점검", true, false, onBudget))
}

/** 오늘 할 일 카드 — 활성 항목을 전부 리스트로. 각 행 탭 시 해당 액션. 없으면 격려 한 줄. */
@Composable
fun TodayTaskCard(tasks: List<TodayItem>, inProgress: Boolean) {
    val accent = LocalAccent.current
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

/** 오늘 할 일 로딩 스켈레톤 — 헤더 + 시머 행 N개. 로딩 완료 시 실제 리스트로 한 번에 교체. */
@Composable
fun TodayTaskSkeleton(rows: Int = 3) {
    val accent = LocalAccent.current
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TaskAlt, null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("오늘 할 일", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(12.dp))
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
    val cal = java.util.Calendar.getInstance()
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val days = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val month = cal.get(java.util.Calendar.MONTH) + 1
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

/** 이번 주 게임 일정 — 이벤트·정기콘텐츠 마감 임박(픽업과 별개). */
@Composable
fun DashScheduleCard(events: List<GameEvent>, challenges: List<GameChallenge>, onTap: () -> Unit) {
    val accent = LocalAccent.current
    val now = System.currentTimeMillis()
    val items = (events.map { Triple(it.game, it.name, it.endMillis to it.dDayLabel()) } +
        challenges.map { Triple(it.game, it.name, it.endMillis to it.dDayLabel()) })
        .filter { it.third.first > now }.sortedBy { it.third.first }.take(3)
    if (items.isEmpty()) return
    GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("이번 주 일정", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("전체 ›", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
            }
            items.forEach { row ->
                Spacer(Modifier.height(11.dp))
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
}

/** 게임 소식 — 다가오는 주년 + 최신 공지. */
@Composable
fun DashNewsCard(news: List<NewsItem>, anniversaries: List<AnniversaryInfo>, onTap: () -> Unit) {
    val accent = LocalAccent.current
    val amber = Color(0xFFF59E0B)
    val anni = anniversaries.firstOrNull { it.daysUntil <= 60 }
    val topNews = news.sortedByDescending { it.createdAtMillis }.take(2)
    if (anni == null && topNews.isEmpty()) return
    GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onTap() }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("게임 소식", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("전체 ›", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
            }
            if (anni != null) {
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(11.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlgGameTag(n.game, size = GameTagSize.Small)
                    Spacer(Modifier.width(9.dp))
                    Text(n.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
                }
            }
        }
    }
}

