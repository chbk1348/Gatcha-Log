package com.gatcha.log.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.BroadcastSchedule
import com.gatcha.log.data.ConfirmedBroadcast
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.LiveBroadcast
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.hmsLabel
import com.gatcha.log.data.isImminent
import com.gatcha.log.data.dhLabel
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.delay
import com.gatcha.log.data.GameScheduleLine
import com.gatcha.log.data.ScheduleDay
import com.gatcha.log.data.ScheduleEntry
import com.gatcha.log.data.ScheduleLogic
import com.gatcha.log.data.ScheduleSummary
import com.gatcha.log.data.collabTitle
import com.gatcha.log.data.isCollabBanner
import com.gatcha.log.ui.components.GlgBadgeText
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

// ============================================================
// 통합 게임 일정 — 섹션은 호요랜드형 진입 카드(게임당 한 줄), 상세는 마감 날짜 타임라인.
// iOS(SwiftUI) GameScheduleSection/GameSchedulePage 패리티. (design_gameinfo_schedule_v2_mockup.html 기준)
//
// 예전 구성(버전 카드 나열 + 하단 이벤트 카드)은 같은 정보를 '버전'과 '종류' 두 축으로 훑게 만들어
// 스크롤이 길었다. 마감일 하나로 묶으면 "다음에 뭐가 끝나지?"에 한 화면에서 답할 수 있다.
//
// 모델·산출 로직(buildSchedule·buildDays·gameLines·summarize)은 GL_Shared ScheduleLogic 단일 소스.
// 여기엔 Compose 렌더링과 ARGB→Color 변환만 남는다.
// ============================================================

/**
 * 마감 임박 표시의 '사이렌' — 알파를 천천히 오가게 해 시선을 끈다.
 *
 * 색을 번갈아 칠하거나 크기를 키우는 방법도 있지만, 매초 숫자가 바뀌는 글자에 그걸 얹으면
 * 흔들려 읽기 어렵다. 알파만 움직이면 글자 위치·폭이 그대로라 카운트다운을 읽는 데 방해가 없다.
 * 0.45 아래로는 내리지 않는다 — 사라졌다 나타나는 것처럼 보이면 경고가 아니라 결함처럼 읽힌다.
 */
@Composable
private fun Modifier.sirenPulse(): Modifier {
    val transition = rememberInfiniteTransition(label = "siren")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "sirenAlpha",
    )
    return this.alpha(alpha)
}

private fun scheduleKindColor(kind: String): Color = ScheduleLogic.kindColorArgb(kind).toColor()

private val Urgent = Color(0xFFE8634A)
/** 확정 배지 — 예상(회색)과 확실히 갈라야 해서 채운 색을 쓴다. */
private val ConfirmedGreen = Color(0xFF2BB673)
private val Track = Color(0xFFEDEFF3)
private val WeapBadge = Color(0xFFE0883B)
private val CollabBadge = Color(0xFF6D5AE6)

// 무기(검) 아이콘 — Material/SF Symbols에 검 심볼이 없어 커스텀 벡터로 정의(iOS SwordShape와 동일 형상).
val SwordIcon: ImageVector = ImageVector.Builder("Sword", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 2f); lineTo(13.2f, 5f); lineTo(13.2f, 14f); lineTo(10.8f, 14f); lineTo(10.8f, 5f); close()   // 칼날
        moveTo(8f, 14f); lineTo(16f, 14f); lineTo(16f, 16f); lineTo(8f, 16f); close()                             // 코등이
        moveTo(11.1f, 16f); lineTo(12.9f, 16f); lineTo(12.9f, 20f); lineTo(11.1f, 20f); close()                   // 손잡이
        moveTo(10.4f, 20f); lineTo(13.6f, 20f); lineTo(13.6f, 22f); lineTo(10.4f, 22f); close()                   // 폼멜
    }
}.build()

// 콜라보 배너 표식 — 이름 옆 작은 알약. (스타레일 × Fate 등)
@Composable
private fun CollabChip() {
    Surface(color = CollabBadge, shape = RoundedCornerShape(999.dp)) {
        Text("콜라보", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

// ── 섹션 진입 카드 ──────────────────────────────────────────────────────────

/**
 * 게임 정보 탭의 '게임 일정' 섹션 — 호요랜드 카드와 같은 규격의 진입 카드 한 장.
 * 게임당 한 줄만 남기고(색 바 + 게임명 + 요약 + 잔여), 자세한 목록은 탭해서 상세로 간다.
 */
@Composable
fun GameScheduleSection(
    entries: List<ScheduleEntry>,
    banners: List<GachaBanner>,
    filter: String,
    onSeeAll: () -> Unit,
) {
    val accent = LocalAccent.current
    val lines = ScheduleLogic.gameLines(banners, entries, filter)
    val summary = ScheduleLogic.summarize(banners, entries, filter)
    Text("게임 일정", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
    Text("픽업 배너와 이벤트 마감을 한곳에서.", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 12.dp))
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onSeeAll() }) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("픽업 · 이벤트 · 정기 콘텐츠", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text(summaryLabel(summary), fontSize = 11.5.sp, color = TextSecondary, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            if (lines.isEmpty()) {
                Text(
                    "진행 중인 픽업이 없어요.",
                    fontSize = 11.5.sp, color = TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 15.dp),
                )
            } else {
                Spacer(Modifier.height(13.dp))
                lines.forEachIndexed { i, line ->
                    if (i > 0) HorizontalDivider(color = DividerColor.copy(alpha = 0.6f))
                    else HorizontalDivider(color = DividerColor)
                    GameLineRow(line)
                }
            }
        }
    }
}

/** 진입 카드 부제 — "이번 주 마감 2건 · 진행 중 픽업 6". */
private fun summaryLabel(s: ScheduleSummary): String =
    "이번 주 마감 ${s.weekDeadlines}건 · 진행 중 픽업 ${s.activePickups}"

// 게임 한 줄 — 색 바 + 게임명(+콜라보) + 요약 + 잔여.
@Composable
private fun GameLineRow(line: GameScheduleLine) {
    val c = line.colorArgb.toColor()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(3.dp, 26.dp).clip(RoundedCornerShape(2.dp)).background(c))
        Text(line.shortName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1)
        if (line.hasCollab) CollabChip()
        Text(
            line.summary, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            line.remainLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (line.urgent) Urgent else TextPrimary,
        )
    }
}

// ── 상세 페이지: 마감 날짜 타임라인 ─────────────────────────────────────────

/**
 * 전체 게임 일정 페이지 콘텐츠 (SectionPage 안에서 호스팅 — 헤더/스크롤은 SectionPage 제공).
 * [일정 | 주년] 탭 — 일정=요약 3칸 + 종료 미정 카드 + 마감일 타임라인, 주년=다가오는 게임 주년.
 *
 * 주년은 원래 게임 정보 탭 본문의 독립 섹션이었다. 1년에 몇 번 볼 정보가 상시 자리를 차지하고 있었고,
 * 성격도 '언제 뭐가 있나'라 일정과 같아서 여기 탭으로 합쳤다.
 */
@Composable
fun GameScheduleFullContent(
    banners: List<GachaBanner>,
    events: List<GameEvent>,
    challenges: List<GameChallenge>,
    confirmed: List<ConfirmedBroadcast>,
    filter: String,
) {
    var tab by remember { mutableStateOf(0) }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlgChip("일정", selected = tab == 0) { tab = 0 }
        GlgChip("방송", selected = tab == 1) { tab = 1 }
        GlgChip("주년", selected = tab == 2) { tab = 2 }
    }
    if (tab == 1) {
        BroadcastContent(banners, confirmed, filter)
        return
    }
    if (tab == 2) {
        AnniversaryContent()
        return
    }
    // 일정 조립·필터·그룹핑은 입력이 바뀔 때만. GameInfoScreen 은 같은 호출을 이미 remember 로
    // 감싸 두는데(:126) 이 전체 페이지만 빠져 있어, 스크롤·애니메이션 재구성마다 전부 다시 돌았다.
    val all = remember(banners, events, challenges) { ScheduleLogic.buildSchedule(banners, events, challenges) }
    val entries = remember(all, filter) { ScheduleLogic.filteredEntries(all, filter) }
    val days = remember(entries) { ScheduleLogic.buildDays(entries) }
    val undated = remember(banners, filter) { ScheduleLogic.undatedPickups(banners, filter) }
    val summary = remember(banners, all, filter) { ScheduleLogic.summarize(banners, all, filter) }

    Text("마감이 가까운 순서로 정리했어요.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 14.dp))

    if (days.isEmpty() && undated.isEmpty()) {
        Text("예정된 일정이 없어요.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        return
    }

    SummaryStrip(summary)
    Spacer(Modifier.height(16.dp))

    if (undated.isNotEmpty()) {
        UndatedPinCard(undated)
        Spacer(Modifier.height(20.dp))
    }

    if (days.isNotEmpty()) {
        TodayMarker()
        Spacer(Modifier.height(14.dp))
        // 남은 시간 갱신 기준 시각 — 카드마다 타이머를 두지 않고 여기서 한 번만 돌린다.
        // 24시간 안쪽 일정이 하나라도 있으면 초 단위로, 아니면 분 단위로 돈다.
        val hasImminent = remember(days) {
            days.any { d -> d.entries.any { isImminent(it.target) } }
        }
        val now = rememberScheduleNow(fast = hasImminent)
        days.forEachIndexed { i, d -> DayNode(d, isLast = i == days.lastIndex, now = now) }
    }
}

/**
 * 남은 시간 표시용 현재 시각.
 *
 * @param fast 초 단위로 갱신할지. 24시간 안쪽 일정이 있을 때만 켠다 — 며칠 남은 일정에
 *   초를 세어 봐야 화면은 그대로인데 재구성만 60배로 늘어난다.
 *
 * 화면을 벗어나면 코루틴이 취소되므로 백그라운드에서는 돌지 않는다.
 */
@Composable
private fun rememberScheduleNow(fast: Boolean): Long {
    var now by remember { mutableStateOf(currentTimeMillis()) }
    LaunchedEffect(fast) {
        val period = if (fast) 1_000L else 60_000L
        while (true) {
            delay(period)
            now = currentTimeMillis()
        }
    }
    return now
}

// 요약 3칸 — 이번 주 마감 / 진행 중 픽업 / 이벤트·콘텐츠.
@Composable
private fun SummaryStrip(s: ScheduleSummary) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(16.dp)),
    ) {
        SummaryCell(s.weekDeadlines, "이번 주 마감", Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(DividerColor.copy(alpha = 0.7f)))
        SummaryCell(s.activePickups, "진행 중 픽업", Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(DividerColor.copy(alpha = 0.7f)))
        SummaryCell(s.extras, "이벤트 · 콘텐츠", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(1.dp))
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, maxLines = 1)
    }
}

/**
 * 종료 시각이 미공지라 타임라인에 못 올리는 픽업 — 상단 고정.
 * 지금 스타레일 × Fate 콜라보가 정확히 이 상태다(상류 ennead 가 end_time 을 안 채움).
 */
@Composable
private fun UndatedPinCard(pickups: List<GachaBanner>) {
    val title = pickups.firstNotNullOfOrNull { collabTitle(it) } ?: "종료 미정 픽업"
    val started = pickups.filter { it.startMillis > 0 }.minOfOrNull { it.startMillis }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CollabBadge.copy(alpha = 0.06f))
            .border(1.dp, CollabBadge.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (pickups.any { isCollabBanner(it) }) CollabChip()
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (started != null) {
            Spacer(Modifier.height(3.dp))
            Text("${DateUtil.shortDate(started)} 시작", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        PickupChips(pickups)
        Spacer(Modifier.height(9.dp))
        Text(
            "종료 시각 미공지 — 확정되면 타임라인에 올라갑니다",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CollabBadge,
        )
    }
}

// 오늘 마커 — 타임라인 시작점.
@Composable
private fun TodayMarker() {
    val accent = LocalAccent.current
    val now = System.currentTimeMillis()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(19.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(
            "오늘 · ${DateUtil.month(now)}월 ${DateUtil.dayOfMonth(now)}일",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent,
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(accent.copy(alpha = 0.25f)))
    }
}

// 날짜 노드 — 좌측 날짜(일/월·요일/D-N) + 세로 연결선, 우측에 그날 끝나는 항목들.
@Composable
private fun DayNode(d: ScheduleDay, isLast: Boolean, now: Long) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.day}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("${d.month}월 ${d.weekdayKo}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(Modifier.height(5.dp))
            Surface(
                color = if (d.urgent) Urgent.copy(alpha = 0.14f) else Track,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    if (d.dDay == 0) "D-DAY" else "D-${d.dDay}",
                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                    color = if (d.urgent) Urgent else TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                )
            }
            // 다음 날짜로 이어지는 세로선(마지막 노드는 생략).
            if (!isLast) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(1.dp).weight(1f).background(DividerColor))
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.entries.forEach { EntryCard(it, now) }
        }
    }
    Spacer(Modifier.height(18.dp))
}

// ── 방송 탭 ────────────────────────────────────────────────────────────────

/**
 * 버전 특별 방송 — 게임당 다음 한 회.
 *
 * 일정 타임라인과 나눈 이유: 저쪽은 '언제 끝나나'를 읽는 자리인데 방송은 시작하는 일정이고,
 * 무엇보다 **역산한 예상**이라 확정된 마감들 사이에 섞이면 같은 무게로 읽힌다.
 */
@Composable
private fun BroadcastContent(banners: List<GachaBanner>, confirmed: List<ConfirmedBroadcast>, filter: String) {
    val all = remember(banners, confirmed) { BroadcastSchedule.next(banners, confirmed) }
    val list = remember(all, filter) { if (filter == "all") all else all.filter { it.gameKey == filter } }

    // 안내 문구는 목록 성격에 따라 바꾼다 — 전부 확정인데 '예상'이라고 하면 값을 깎아 읽게 된다.
    val anyEstimate = list.any { it.isEstimate }
    Text(
        if (anyEstimate) {
            "공식 공지가 뜬 방송은 확정 일시로, 아직 안 뜬 방송은 관례(버전 시작 12일 전 금요일)로 계산한 예상이에요."
        } else {
            "공식 공지로 확정된 일시예요."
        },
        fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 14.dp),
    )
    if (list.isEmpty()) {
        // 픽업 배너가 없으면 버전 시작일을 몰라 역산의 근거가 없다 — 추측해서 만들어내지 않는다.
        Text(
            "예상할 수 있는 방송이 없어요.",
            fontSize = 13.sp, color = TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        )
        return
    }
    val hasImminent = remember(list) { list.any { isImminent(it.targetMillis) } }
    val now = rememberScheduleNow(fast = hasImminent)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.forEach { BroadcastCard(it, now) }
    }
}

@Composable
private fun BroadcastCard(b: LiveBroadcast, now: Long) {
    val gc = b.colorArgb.toColor()
    val uriHandler = LocalUriHandler.current
    val imminent = isImminent(b.targetMillis, now)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, gc.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { runCatching { uriHandler.openUri(b.liveUrl) } }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(color = gc, shape = RoundedCornerShape(6.dp)) {
                Text(
                    b.gameShort, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Text(
                if (b.version.isBlank()) "버전 특별 방송" else "v${b.version} 특별 방송",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            // 예상/확정은 카드마다 붙인다 — 안내 문구를 지나쳐도 여기서 다시 만난다.
            if (b.isEstimate) {
                Surface(color = TextSecondary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "예상", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else {
                Surface(color = ConfirmedGreen, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "확정", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            DateUtil.shortDateTime(b.targetMillis) + " (" + DateUtil.weekdayKo(b.targetMillis) + ")",
            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircleOutline, null, tint = gc, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("공식 채널에서 생중계", fontSize = 10.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(
                if (imminent) hmsLabel(b.targetMillis, now) + " 뒤" else dhLabel(b.targetMillis, now) + " 뒤",
                fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                color = if (imminent) Urgent else TextSecondary,
                maxLines = 1,
                modifier = if (imminent) Modifier.sirenPulse() else Modifier,
            )
        }
    }
}

// 일정 카드 — 종류 배지 + 제목 + 게임 태그, 픽업이면 캐릭터 칩까지.
@Composable
private fun EntryCard(e: ScheduleEntry, now: Long) {
    val gc = e.colorArgb.toColor()
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            // 아웃라인에 게임색 — 타임라인에서 어느 게임 일정인지 배지를 읽기 전에 구분된다.
            .border(1.dp, gc.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(color = scheduleKindColor(e.kind), shape = RoundedCornerShape(6.dp)) {
                Text(
                    if (e.kind == "패치") "픽업" else e.kind,
                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Text(
                e.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Surface(color = gc, shape = RoundedCornerShape(6.dp)) {
                Text(
                    e.gameShort, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        // 남은 시간 — 날짜 노드의 D-N 은 '며칠 남았나'만 알려 주지만, 마감 당일엔 몇 시간이
        // 남았는지가 실제로 필요한 정보다(D-DAY 만으로는 지금 해야 하는지 판단이 안 된다).
        // 24시간 안쪽이면 초까지 세고 사이렌처럼 명멸시킨다.
        val imminent = isImminent(e.target, now)
        val remain = if (imminent) hmsLabel(e.target, now) else dhLabel(e.target, now)
        Spacer(Modifier.height(4.dp))
        // 남은 시간은 **오른쪽 끝 고정**. 예전엔 부제에 weight(fill=false) 를 주고 Spacer 에도
        // weight 를 걸어, 남는 폭이 둘로 갈리면서 부제 길이에 따라 시간 위치가 카드마다 달라졌다.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (e.sub.isNotBlank()) {
                Text(
                    e.sub, fontSize = 10.5.sp, color = TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (remain == "종료") remain else "$remain 남음",
                fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                color = if (imminent) Urgent else TextSecondary,
                maxLines = 1,
                modifier = if (imminent) Modifier.sirenPulse() else Modifier,
            )
        }
        if (e.pickups.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            PickupChips(e.pickups)
        }
    }
}

/**
 * 픽업 칩 — 캐릭터 먼저, 무기(광추·W-엔진)는 그다음. 둘 다 **이름 그대로** 노출한다.
 * ("무기 2종"처럼 개수로 뭉치면 정작 뭐가 픽업인지 알 수 없어 칩의 쓸모가 없다.)
 * 2개씩 줄바꿈 — FlowRow 실험 API 회피.
 */
@Composable
private fun PickupChips(pickups: List<GachaBanner>) {
    val chars = pickups.filter { it.type != "weapon" }
    val weapons = pickups.filter { it.type == "weapon" }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipRows(chars)
        // 캐릭터와 무기는 종류가 다르니 구분선으로 끊는다(둘 다 있을 때만).
        if (chars.isNotEmpty() && weapons.isNotEmpty()) {
            HorizontalDivider(color = DividerColor.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 1.dp))
        }
        ChipRows(weapons)
    }
}

@Composable
private fun ChipRows(list: List<GachaBanner>) {
    list.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { b -> PickupChip(b, Modifier.weight(1f, fill = false)) }
        }
    }
}

@Composable
private fun PickupChip(banner: GachaBanner, modifier: Modifier = Modifier) {
    val isWeapon = banner.type == "weapon"
    Row(
        modifier.clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(999.dp))
            .padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 무기도 캐릭터와 같은 원형 아바타 — 칩이 한 줄에 섞여도 형태가 어긋나지 않는다.
        if (isWeapon) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(WeapBadge.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(SwordIcon, null, tint = WeapBadge, modifier = Modifier.size(12.dp)) }
        } else {
            Box(Modifier.size(20.dp).clip(CircleShape).background(banner.gameColor.toColor()), contentAlignment = Alignment.Center) {
                // 폰트 패딩 때문에 글자가 아래로 처지던 자리 — [GlgBadgeText] 가 글리프를 실제 중앙에 둔다.
                GlgBadgeText(banner.name.take(1), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Text(banner.name, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
