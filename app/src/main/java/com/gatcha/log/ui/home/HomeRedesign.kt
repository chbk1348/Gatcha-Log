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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Savings
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
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.PityTier
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.ProfileAvatar
import com.gatcha.log.ui.theme.DangerBackground
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.WarningText
import com.gatcha.log.util.won
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// 홈 2.0 합본 — M(AI 요약) + K(M3 Expressive 토널) + D(게임별 예산)
// Material3 1.4 Expressive API(ButtonGroup·motionScheme)는 BOM 의존성 충돌 위험으로
// 도입하지 않고, 비주얼 언어(비대칭 코너·토널 컨테이너·연결 알약)만 기존 프리미티브로 구현.
// 데이터는 전부 기존 ViewModel 재사용 — 회귀 최소.
// ─────────────────────────────────────────────────────────────────────────────

/** M3 Expressive 토널 컨테이너 색 (accent 단색 팔레트를 톤으로 확장). */
private val SecCont = Color(0xFFE5E1F2); private val SecOnCont = Color(0xFF2C2746)
private val TerCont = Color(0xFFFCE4D6); private val TerOnCont = Color(0xFF5A3216)

/** 천장 하이라이트 — 가장 임박한 게임 1종(요약·토널 타일에 공유). */
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
    userName: String,
    isGuest: Boolean,
    photoUrl: String?,
    streak: Int,
    monthlyTotal: Long,
    alertCount: Int,
    onBellClick: () -> Unit,
) {
    val greeting = remember { greetingForNow() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 2.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(photoUrl = photoUrl, size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$greeting 👋", fontSize = 12.sp, color = TextSecondary)
            Text(
                if (isGuest) "게스트" else "$userName 님",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
            )
            if (streak > 0) {
                Text("🔥 ${streak}일 연속 · ${won(monthlyTotal)}", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
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
    budget: Long,
    topGame: String?,
    topPity: PityHighlight?,
    onBudget: () -> Unit,
    onPity: () -> Unit,
    onTip: () -> Unit,
) {
    val accent2 = LocalAccentSecondary.current
    // 하루 단위로 고정되는 시드 — 매일 다른 문구, 같은 날 리컴포지션엔 안 흔들림
    val daySeed = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }
    val summary = remember(monthlyTotal, budget, topGame, topPity, daySeed) {
        buildMonthlySummary(monthlyTotal, budget, topGame, topPity, accent2, daySeed)
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(Color(0xFF11352F), Color(0xFF0E2A45)))
            ).padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = accent2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("이번 달 한눈에", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent2)
                }
                Spacer(Modifier.height(10.dp))
                Text(summary, fontSize = 14.sp, color = Color(0xFFEAFFF9), lineHeight = 21.sp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryChip("예산 점검", onBudget)
                    SummaryChip("천장 보기", onPity)
                    SummaryChip("절약 팁", onTip)
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String, onClick: () -> Unit) {
    val accent2 = LocalAccentSecondary.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent2.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accent2.copy(alpha = 0.35f)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = accent2,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
        )
    }
}

// ── 50종 랜덤 요약 엔진 ──────────────────────────────────────────────────────
// 토큰 렌더러: {M}=월, {AMT}=금액, {S}=예산상태절 을 스타일된 조각으로 치환.
private fun render(template: String, tokens: Map<String, AnnotatedString>): AnnotatedString =
    buildAnnotatedString {
        val rx = Regex("\\{[A-Z]+\\}")
        var last = 0
        rx.findAll(template).forEach { m ->
            append(template.substring(last, m.range.first))
            tokens[m.value]?.let { append(it) }
            last = m.range.last + 1
        }
        append(template.substring(last))
    }

/** 50종 프레임 — 모두 {AMT}(금액) + {S}(예산상태절)을 자연스럽게 배치. */
private val SUMMARY_FRAMES: List<String> = listOf(
    "{M}월 지출은 {AMT}, {S}.", "이번 달 {AMT} 썼어요. {S}.", "벌써 {AMT}네요. {S}.",
    "{M}월 들어 {AMT}. {S}.", "지금까지 {AMT} 지출했어요. {S}.", "이번 달 결제 {AMT}. {S}.",
    "{M}월 한 달 {AMT} 사용. {S}.", "현재 {AMT}까지 왔어요. {S}.", "오늘까지 {AMT} 썼네요. {S}.",
    "{M}월 가챠·결제 합계 {AMT}. {S}.", "음, 이번 달 {AMT}. {S}.", "이번 달도 달렸네요 — {AMT}, {S}.",
    "{AMT} 지출 중이에요. {S}.", "집계해보니 {AMT}. {S}.", "{M}월 누적 {AMT}. {S}.",
    "이번 달 씀씀이 {AMT}. {S}.", "지갑 점검! {AMT} 썼어요. {S}.", "{M}월 현재 {AMT}. {S}.",
    "여기까지 {AMT}. {S}.", "이번 달 {AMT} 기록 중이에요. {S}.", "총 {AMT} 나갔어요. {S}.",
    "{M}월 소비 {AMT}. {S}.", "체크해보면 {AMT}. {S}.", "이번 달 {AMT} 사용했어요. {S}.",
    "지출 현황 {AMT}. {S}.", "{M}월 {AMT} 썼습니다. {S}.", "슬쩍 보니 {AMT}. {S}.",
    "이달 누계 {AMT}. {S}.", "{AMT} 사용 중. {S}.", "이번 달 합계 {AMT}. {S}.",
    "{M}월 지출 집계 {AMT}. {S}.", "현재 지출 {AMT}. {S}.", "이번 달 {AMT} 떠났네요. {S}.",
    "결산하면 {AMT}. {S}.", "{M}월에 {AMT} 썼어요. {S}.", "지금 {AMT}. {S}.",
    "이번 달 쓴 돈 {AMT}. {S}.", "{AMT} 지출 기록. {S}.", "한눈에 {AMT}. {S}.",
    "{M}월 페이스 {AMT}. {S}.", "이번 달 {AMT}, 어떠세요? {S}.", "지출 합산 {AMT}. {S}.",
    "{M}월 동안 {AMT}. {S}.", "톡 까보면 {AMT}. {S}.", "이번 달 가챠 포함 {AMT}. {S}.",
    "{AMT} 사용했네요. {S}.", "여태 {AMT} 썼어요. {S}.", "{M}월 결제 합 {AMT}. {S}.",
    "이번 달 {AMT}로 집계돼요. {S}.", "현재까지 총 {AMT}. {S}.",
)

private val OVER_STATUS = listOf("예산을 {V} 넘겼어요", "예산보다 {V} 더 썼네요", "예산 초과 {V}", "예산을 {V} 초과했어요")
private val NEAR_STATUS = listOf("예산의 {V}까지 왔어요", "예산 {V} 소진했어요", "예산을 거의 다 썼어요 ({V})", "예산 사용률 {V}")
private val UNDER_STATUS = listOf("예산의 {V} 수준이에요", "아직 예산 {V}", "예산 내 잘 쓰고 있어요 ({V})", "예산 {V} 사용 중")
private val NONE_STATUS = listOf("예산을 정하면 페이스를 알려드릴게요", "예산은 아직 미설정이에요", "예산을 설정해 보세요")

/**
 * 온디바이스 규칙 기반 월간 요약(LLM 미사용). 예산(이하/임박/초과)·최다게임·천장 단계를 반영하되
 * **일자 시드로 50종 프레임 중 하나를 골라** 매일 다른 문구로 표시. 강조 단어만 색 span.
 */
private fun buildMonthlySummary(
    monthlyTotal: Long,
    budget: Long,
    topGame: String?,
    topPity: PityHighlight?,
    accent2: Color,
    seed: Int,
): AnnotatedString {
    val month = DateUtil.month(System.currentTimeMillis())
    val white = SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)
    val warn = SpanStyle(color = Color(0xFFFF9B9B), fontWeight = FontWeight.Bold)
    val mint = SpanStyle(color = accent2, fontWeight = FontWeight.Bold)
    val rnd = Random(seed)
    fun <T> List<T>.pick(): T = this[rnd.nextInt(size)]

    val amt = buildAnnotatedString { withStyle(white) { append(won(monthlyTotal)) } }

    // 예산 상태절 — {V}(퍼센트/초과율)를 스타일된 조각으로 치환
    val pct = if (budget > 0) (monthlyTotal * 100 / budget).toInt() else 0
    val status: AnnotatedString = when {
        budget <= 0 -> AnnotatedString(NONE_STATUS.pick())
        monthlyTotal > budget -> render(OVER_STATUS.pick(), mapOf("{V}" to buildAnnotatedString { withStyle(warn) { append("${pct - 100}%") } }))
        pct >= 90 -> render(NEAR_STATUS.pick(), mapOf("{V}" to buildAnnotatedString { withStyle(warn) { append("${pct}%") } }))
        else -> render(UNDER_STATUS.pick(), mapOf("{V}" to buildAnnotatedString { withStyle(mint) { append("${pct}%") } }))
    }

    val frame = SUMMARY_FRAMES.pick()
    val core = render(frame, mapOf("{M}" to AnnotatedString("$month"), "{AMT}" to amt, "{S}" to status))

    // 꼬리 — 최다 지출 + 천장 임박(있을 때만)
    return buildAnnotatedString {
        append(core)
        val tg = topGame?.let { GameData.byNameOrNull(it)?.shortName ?: it }
        if (tg != null) {
            append(listOf(" 최다 지출은 ", " 가장 많이 쓴 곳은 ", " 지출 1위는 ").pick())
            withStyle(white) { append(tg) }
            append("입니다.")
        }
        if (topPity != null && topPity.tier != PityTier.Safe) {
            append(" ")
            withStyle(mint) { append("${topPity.game.shortName} 천장 ${topPity.count}/${topPity.hard}") }
            append(if (topPity.tier == PityTier.Reached) ", 다음 보장 확정이에요." else ", 곧 보장이에요.")
        }
    }
}

// ── K: 토널 2-stat (남은 예산·출석) ──────────────────────────────────────────
@Composable
fun TonalStatRow(
    monthlyTotal: Long,
    budget: Long,
    attendanceDone: Int,
    attendanceTotal: Int,
    streak: Int,
    onBudgetClick: () -> Unit,
) {
    // 두 타일 높이를 콘텐츠가 많은 쪽에 맞춰 통일 (IntrinsicSize.Min + fillMaxHeight)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 남은 예산 · 페이스 타일 (secondary container, 비대칭 코너) — 탭하면 예산 관리
        Surface(
            shape = RoundedCornerShape(28.dp, 28.dp, 10.dp, 28.dp),
            color = SecCont,
            modifier = Modifier.weight(1f).fillMaxHeight().clickable { onBudgetClick() },
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("남은 예산", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SecOnCont.copy(alpha = 0.8f), maxLines = 1)
                Spacer(Modifier.height(2.dp))
                if (budget > 0) {
                    val remaining = budget - monthlyTotal
                    val over = remaining < 0
                    val pct = (monthlyTotal * 100 / budget).toInt()
                    Text(
                        if (over) "−${won(-remaining)}" else won(remaining),
                        fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        color = if (over) DangerText else SecOnCont, maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    val chipColor = if (over) DangerText else if (pct >= 90) WarningText else SecOnCont
                    Surface(color = chipColor.copy(alpha = if (over) 0.14f else 0.12f), shape = RoundedCornerShape(999.dp)) {
                        Text(
                            if (over) "예산 초과" else "${pct}% 소진",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chipColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                    // 페이스 — 이 속도면 월말 예상 지출
                    val cal = java.util.Calendar.getInstance()
                    val passed = cal.get(java.util.Calendar.DAY_OF_MONTH)
                    val total = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    val projected = monthlyTotal * total / passed
                    if (projected >= 10000) {
                        Spacer(Modifier.height(5.dp))
                        Text("이 페이스 약 ${projected / 10000}만원", fontSize = 10.sp, color = SecOnCont.copy(alpha = 0.7f), maxLines = 1)
                    }
                } else {
                    Text("미설정", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SecOnCont)
                    Spacer(Modifier.height(7.dp))
                    Surface(color = SecOnCont.copy(alpha = 0.10f), shape = RoundedCornerShape(999.dp)) {
                        Text("예산 설정 ›", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SecOnCont, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                    }
                }
            }
        }
        // 출석 타일 (tertiary container)
        Surface(
            shape = RoundedCornerShape(28.dp, 28.dp, 28.dp, 10.dp),
            color = TerCont,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFB8C00), modifier = Modifier.size(26.dp))
                Text(if (streak > 0) "${streak}일" else "—", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TerOnCont)
                Text("연속 출석 · $attendanceDone/$attendanceTotal", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TerOnCont.copy(alpha = 0.8f), maxLines = 1)
            }
        }
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

// ── 게임 현황 2.0 — 토널 출석 타일 + 캡슐 노트 ───────────────────────────────
/** 출석 타일 — 게임색 토널. 완료=체크, 진행=스피너, 대기=약자(탭하여 출석). */
@Composable
fun AttendanceTile(
    game: Game,
    done: Boolean,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (done) game.color.copy(alpha = 0.10f) else Color.White,
        border = BorderStroke(1.dp, if (done) game.color.copy(alpha = 0.35f) else DividerColor),
        modifier = modifier.clickable(enabled = !done && !inProgress) { onClick() },
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(game.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    inProgress -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = game.color)
                    done -> Icon(Icons.Default.CheckCircle, null, tint = game.color, modifier = Modifier.size(24.dp))
                    else -> Text(game.abbr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = game.color)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(game.shortName, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                if (done) "출석 완료" else if (inProgress) "처리 중" else "탭하여 출석",
                fontSize = 10.sp,
                color = if (done) game.color else TextSecondary,
                maxLines = 1,
            )
        }
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
                Box(Modifier.size(8.dp).clip(CircleShape).background(gs.game.color))
                Spacer(Modifier.width(7.dp))
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
    val color = banner.gameColor
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
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(banner.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${g?.shortName ?: banner.game} · 픽업", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Surface(color = chipColor.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                Text(
                    if (d == 0) "D-DAY" else "D-$d",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chipColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
    }
}

