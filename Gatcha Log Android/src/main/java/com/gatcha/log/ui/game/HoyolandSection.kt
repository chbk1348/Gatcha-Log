package com.gatcha.log.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandFact
import com.gatcha.log.data.HoyolandLineup
import com.gatcha.log.data.HoyolandPhase
import com.gatcha.log.data.api.HoyolandApi
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.components.ChipIdleBorder
import com.gatcha.log.ui.components.ChipIdleText
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.openExternalLink
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.glgStandardSpec

// ── 호요랜드(호요버스 한국 오프라인 행사) ─────────────────────────────────────
// 일정·장소·참여 게임·프로그램이 모두 확정됐고 **예매만 미공개**다.
// 내용은 전부 shared 의 HoyolandEvent 에서 온다(원격 hoyoland.json → 실패 시 번들 폴백) —
// 이 파일에는 표시 규격만 둔다. iOS 대응 = HoyolandSection.swift.

/**
 * 화면이 쓸 행사 정보 — 첫 프레임은 캐시/번들값으로 즉시 그리고, 원격 갱신되면 갈아 끼운다.
 *
 * 로딩 스켈레톤을 두지 않는 이유: 폴백이 **항상 유효한 확정 정보**라 빈 상태가 존재하지 않는다.
 * 스켈레톤을 깔면 이미 맞는 내용을 일부러 감췄다가 같은 내용을 다시 보여주는 꼴이 된다.
 */
@Composable
private fun rememberHoyolandEvent(): HoyolandEvent {
    var event by remember { mutableStateOf(HoyolandApi.current) }
    LaunchedEffect(Unit) { event = HoyolandApi.load() }
    return event
}

/**
 * 홈·일정 탭용 — 지금 띄울 값어치가 있는 행사만 돌려준다(없으면 null).
 *
 * 게임정보 탭과 **같은 로더를 통과시키는 게 요점**이다. 홈이 캐시만 읽고 말면
 * 홈을 먼저 켠 사람은 원격 갱신 전 번들값을 보고, 게임정보 탭을 다녀온 뒤에야 값이 바뀐다.
 */
@Composable
fun rememberFeaturedHoyoland(): HoyolandEvent? = rememberHoyolandEvent().takeIf { it.isFeatured() }

/** 게임정보 탭에 임베드되는 요약 카드 — 탭하면 상세([HoyolandDetailContent])로 이동. */
@Composable
fun HoyolandSection(onOpen: () -> Unit) {
    val accent = LocalAccent.current
    val e = rememberHoyolandEvent()
    Text("호요랜드", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Celebration, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.edition, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            // 예전엔 "준비 중" 고정 배지였다 — 확정 뒤에도 준비 중이라 적혀 있으면
                            // 카드를 열어 볼 이유가 없어 보인다. 지금은 남은 날짜가 그 자리를 대신한다.
                            GlgBadge(e.statusLabel(), accent)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("호요버스 게임 IP 통합 오프라인 행사", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(14.dp))
                HoyolandInfoRow("일정", e.periodLabel)
                Spacer(Modifier.height(8.dp))
                HoyolandInfoRow("장소", e.venueShort)
                Spacer(Modifier.height(8.dp))
                HoyolandInfoRow("예매", e.ticket.statusLabel)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 호요랜드 상세 페이지.
 *
 * 구성 순서는 **지금 알아야 하는 것부터**다: 언제·어디서(히어로) → 어떻게 가나(예매) →
 * 뭘 보나(참여 게임·프로그램) → 곁다리(G-STAR) → 참고(지난 행사).
 * 예전에는 장소 카드가 맨 위였고 일정이 그 아래 따로 있어서, 가장 먼저 궁금한 날짜가 두 번째였다.
 */
@Composable
fun HoyolandDetailContent() {
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    val e = rememberHoyolandEvent()
    var pastExpanded by remember { mutableStateOf(false) }
    val pastArrow by animateFloatAsState(if (pastExpanded) 180f else 0f, glgStandardSpec(), label = "pastArrow")

    // 카운트다운 문구 — 단계마다 세는 대상이 다르다(남은 날 → 며칠째 → 없음).
    val phase = e.phase()
    val daysLeft = e.daysUntilStart()
    val countCaption = when (phase) {
        HoyolandPhase.BEFORE -> if (daysLeft == 0) "오늘 개막" else "개막까지"
        HoyolandPhase.ONGOING -> "진행 중"
        HoyolandPhase.ENDED -> ""
    }
    val countNumber = if (phase == HoyolandPhase.ONGOING) "${e.dayOrdinal()}" else "$daysLeft"
    val countUnit = when {
        phase == HoyolandPhase.ONGOING -> "일차"
        daysLeft == 0 -> "일 · 오늘"
        else -> "일 남음"
    }
    // 진행 바 눈금 — "발표 8.31" / "개막 10.2". 연도는 뗀다(같은 해 안에서만 도는 구간이다).
    val announceTick = "발표 " + e.announceYmd.split("-").let { p ->
        if (p.size < 3) e.announceYmd else "${p[1].trimStart('0')}.${p[2].trimStart('0')}"
    }
    val openTick = "개막 " + e.startYmd.split("-").let { p ->
        if (p.size < 3) e.startYmd else "${p[1].trimStart('0')}.${p[2].trimStart('0')}"
    }

    // ── 히어로 — 남은 날짜를 **숫자 그 자체로** 세운다.
    // 예전 히어로는 행사명 옆 배지에 "D-29"를 적었는데, 배지는 다른 정보와 같은 크기라
    // 개막이 하루 앞이든 두 달 앞이든 화면이 똑같아 보였다. 이 화면은 D-60 부터 뜨므로
    // 첫 화면이 곧 "얼마 남았나"에 답해야 한다.
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (phase == HoyolandPhase.ENDED) e.edition else "${e.edition} ${countCaption}",
                fontSize = 11.5.sp, color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (phase != HoyolandPhase.ENDED) {
                    // 숫자만 크게 — 단위는 작게 옆에 붙인다. 붙여 쓰면 "29일"이 한 덩어리로 읽혀
                    // 숫자가 눈에 먼저 들어오는 이점이 사라진다.
                    Text(
                        countNumber,
                        fontSize = 44.sp, fontWeight = FontWeight.Bold, color = accent,
                        // 자릿수가 줄어도(D-10 → D-9) 숫자 폭이 흔들리지 않게 고정폭 숫자를 쓴다.
                        // Text 에는 이 인자가 없어 스타일로 준다(다른 인자는 그대로 우선한다).
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        lineHeight = 46.sp,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(countUnit, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                GlgBadge(
                    if (phase == HoyolandPhase.ENDED) "종료" else "${e.periodLabel.substringBefore(" ~ ").substringAfter('.')} 개막",
                    if (phase == HoyolandPhase.ENDED) TextSecondary else accent,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            // 진행 바 — 발표에서 개막까지 얼마나 왔는지. 가운데 눈금이 예매라,
            // **미정이라는 사실이 빈 눈금으로 보인다**(문장을 읽지 않아도 전달된다).
            if (phase == HoyolandPhase.BEFORE) {
                Spacer(Modifier.height(15.dp))
                Box(
                    Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(DividerColor),
                ) {
                    Box(
                        Modifier.fillMaxWidth(e.progress().coerceAtLeast(0.02f)).height(5.dp)
                            .clip(RoundedCornerShape(3.dp)).background(accent),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(announceTick, fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        e.ticket.openLabel.ifBlank { "예매 ${e.ticket.statusLabel}" },
                        fontSize = 10.sp,
                        color = if (e.ticket.isUndecided) TextSecondary else accent,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(openTick, fontSize = 10.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
            Spacer(Modifier.height(14.dp))
            HoyolandFactRow("일정", e.periodLongLabel)
            Spacer(Modifier.height(8.dp))
            HoyolandFactRow("장소", e.venueFull)
            Spacer(Modifier.height(8.dp))
            HoyolandFactRow("주소", e.venueAddress)
            Spacer(Modifier.height(14.dp))
            // 카드 폭을 꽉 채운다 — GlgOutlineButton 은 기본이 내용 크기라, 너비를 안 주면
            // 버튼이 글자 길이만큼만 나온다(다른 호출부는 전부 weight 로 폭을 준다).
            GlgOutlineButton(
                "지도에서 보기",
                onClick = { openExternalLink(ctx, e.mapUrl, e.mapFallbackUrl) },
                modifier = Modifier.fillMaxWidth(),
                height = 46.dp,
                color = accent, // 카드 위라 고스트 테두리는 배경에 묻힌다(iOS 와 동일하게 강조색)
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    // ── 예매 — **이 페이지에서 유일하게 안 정해진 항목**이라 단독 카드로 세운다.
    // 다른 정보와 같은 목록에 섞어 두면 "미정" 한 줄이 확정 정보들 사이에 묻힌다.
    Text("예매", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                        .background((if (e.ticket.isUndecided) TextSecondary else accent).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        tint = if (e.ticket.isUndecided) TextSecondary else accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // 미정일 때 강조색 배지를 쓰면 정해진 것처럼 보인다 — 회색으로 낮춘다.
                    GlgBadge(e.ticket.statusLabel, if (e.ticket.isUndecided) TextSecondary else accent)
                    Spacer(Modifier.height(6.dp))
                    Text(e.ticket.note, fontSize = 12.5.sp, color = TextSecondary)
                }
            }
            // 예매가 공개되면 채워지는 자리 — 값이 없는 줄은 아예 그리지 않는다.
            val ticketFacts = listOfNotNull(
                e.ticket.vendor.takeIf { it.isNotBlank() }?.let { HoyolandFact("예매처", it) },
                e.ticket.openLabel.takeIf { it.isNotBlank() }?.let { HoyolandFact("오픈", it) },
                e.ticket.priceLabel.takeIf { it.isNotBlank() }?.let { HoyolandFact("가격", it) },
            )
            if (ticketFacts.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(14.dp))
                ticketFacts.forEachIndexed { i, f ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    HoyolandFactRow(f.label, f.value)
                }
            }
            if (e.ticket.url.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                GlgOutlineButton(
                    "예매하기",
                    onClick = { openExternalLink(ctx, e.ticket.url) },
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                    color = accent,
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // ── 일자별 시간표 — 현장에서 손에 들고 보는 자리.
    // **날짜 탭은 시간표 유무와 무관하게 선다.** 탭을 기간(개막~폐막)에서 만들기 때문인데,
    // 공식 시간표가 개막 2~3주 전에야 나오는 탓에 그 전까지는 채울 내용이 없다.
    // 그 구간에도 "며칠짜리 행사인지"는 알려 줘야 해서, 빈 채로 숨기지 않고 안내를 놓는다.
    HoyolandTimetableSection(e)

    Spacer(Modifier.height(20.dp))

    // ── 참여 게임 — 게임마다 테마가 따로 붙는다. 공식 키비주얼은 아직 게임별로 안 나와서
    // 썸네일 자리는 게임 대표색 칩으로 둔다(공개되면 이 자리를 이미지로 바꾼다).
    Text("참여 게임", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            e.lineup.forEachIndexed { i, item ->
                if (i > 0) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                    Spacer(Modifier.height(12.dp))
                }
                HoyolandLineupRow(item)
            }
        }
    }

    // ── 프로그램 — 본편과 별개로 **참여 마감이 따로 있는** 것들이라 날짜를 눈에 띄게 둔다.
    if (e.programs.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text("프로그램", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                e.programs.forEachIndexed { i, p ->
                    if (i > 0) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text(p.desc, fontSize = 12.5.sp, color = TextSecondary)
                    if (p.deadline.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        GlgBadge(p.deadline, accent)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // ── 지스타 — 호요랜드와 **별개 행사**지만, 호요버스가 나오는 국내 오프라인 자리라 여기 둔다.
    // 내용은 전부 shared 의 HoyolandGstar 에서 온다(참가사 명단이 순차 공개돼 자주 바뀐다).
    val g = e.gstar
    if (!g.isEmpty) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            Text(g.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (g.badge.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                GlgBadge(g.badge, accent)
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                g.facts.forEachIndexed { i, f ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    HoyolandFactRow(f.label, f.value)
                }
                // 출품작 — 팩트 목록과 같은 카드에 두되 구분선으로 끊는다. 라벨+값이 아니라
                // 게임 태그가 붙는 줄이라 위와 생김새가 다르고, 따로 카드를 세울 만큼 길지도 않다.
                if (g.lineup.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                    Spacer(Modifier.height(14.dp))
                    Text("호요버스 출품작", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    g.lineup.forEachIndexed { i, item ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        HoyolandLineupRow(item)
                    }
                }
                if (g.url.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    GlgOutlineButton(
                        "공식 사이트",
                        onClick = { openExternalLink(ctx, g.url) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 46.dp,
                        color = accent,
                    )
                }
                if (g.notice.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(g.notice, fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    // ── 지난 행사 참고 — 실제 개최 이력(최신순). 다음 행사 규모 가늠용.
    // 지나간 정보라 기본은 접어 둔다 — 이 페이지의 본론은 위의 2026 정보다.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { pastExpanded = !pastExpanded }
            .padding(vertical = 2.dp),
    ) {
        Text("지난 행사", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            if (pastExpanded) "접기" else "펼치기",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp).rotate(pastArrow),
        )
    }
    AnimatedVisibility(visible = pastExpanded) {
        Column {
            e.past.forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                if (i == 0) Spacer(Modifier.height(10.dp))
                HoyolandPastEventCard(p.title, p.facts)
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text(e.notice, fontSize = 11.sp, color = TextSecondary)
}

/**
 * 날짜 탭 한 칸의 높이 — **양 플랫폼 고정값**.
 *
 * 내용에 맡기면 Pretendard 의 큰 줄 상자 탓에 iOS 세그먼트 컨트롤(32pt)보다 두꺼워진다
 * ([GlgChip] 이 같은 이유로 폰트 패딩을 끈다). 바깥 높이를 못 박고 안을 가운데 정렬한다.
 */
private val DayTabHeight = 32.dp

/**
 * 일자별 시간표 — 날짜 탭 + 그날 프로그램.
 *
 * 기본 선택은 **행사 중이면 오늘**이다([HoyolandEvent.defaultDayIndex]). 현장에서 꺼냈을 때
 * 첫날이 선택돼 있으면 매번 한 번 더 눌러야 한다.
 */
@Composable
private fun HoyolandTimetableSection(e: HoyolandEvent) {
    val accent = LocalAccent.current
    val ymds = e.dayYmds
    if (ymds.isEmpty()) return
    // 원격 갱신으로 기간이 바뀌면 선택 인덱스가 범위를 벗어날 수 있어 목록을 키로 준다.
    var sel by remember(ymds) { mutableStateOf(e.defaultDayIndex()) }
    val slots = e.slotsFor(ymds.getOrElse(sel) { ymds.first() })

    Text("일자별 시간표", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    // 날짜 선택은 **한 덩어리 탭**이다. 칩 넷을 나란히 두면 서로 독립된 버튼처럼 보여
    // "이 중 하나가 지금 보고 있는 날"이라는 게 약하게 읽힌다 — 트랙 하나에 담아
    // 선택 칸만 채운다(iOS 는 같은 자리에 세그먼트 컨트롤을 쓴다).
    //
    // 선택 표시는 **미끄러진다.** 색만 즉시 바뀌면 어느 칸에서 어느 칸으로 갔는지가 안 보여
    // 화면이 통째로 갈린 것처럼 느껴진다(iOS 세그먼트 컨트롤도 같은 이유로 움직인다).
    // 움직임 곡선은 앱 공통 규격 glgStandardSpec 을 쓴다.
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, ChipIdleBorder, RoundedCornerShape(14.dp))
            .padding(3.dp),
    ) {
        val cellWidth = maxWidth / ymds.size
        // 칸 폭 × 선택 index 만큼 민다. 폭이 아니라 위치만 애니메이션하므로
        // 회전 화면·태블릿에서 폭이 바뀌어도 계산이 어긋나지 않는다.
        val slide by animateDpAsState(cellWidth * sel, glgStandardSpec(), label = "dayTabSlide")
        Box(
            Modifier
                .offset(x = slide)
                .width(cellWidth)
                .height(DayTabHeight)
                .clip(RoundedCornerShape(11.dp))
                .background(accent),
        )
        Row(Modifier.fillMaxWidth()) {
            ymds.forEachIndexed { i, ymd ->
                val on = i == sel
                // 글자색도 같이 건너간다 — 알약이 미끄러지는 동안 글자만 순간 바뀌면
                // 아직 도착하지 않은 칸의 글자가 먼저 희게 변한다.
                val labelColor by animateColorAsState(
                    if (on) Color.White else ChipIdleText,
                    glgStandardSpec(),
                    label = "dayTabLabel",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(DayTabHeight)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable { sel = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        e.dayTabLabel(ymd),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        // 선택 칸 글자를 흰색으로 두는 건 GlgChip 선택 규격과 같다.
                        color = labelColor,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (slots.isEmpty()) {
                Text(
                    if (e.hasTimetable) "이 날의 프로그램은 아직 공개되지 않았습니다."
                    else "일자별 프로그램은 아직 공개 전입니다.",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "공개되면 이 자리에 채워집니다. 지난 행사는 개막 2~3주 전에 공개됐습니다.",
                    fontSize = 12.sp, color = TextSecondary,
                )
            } else {
                slots.forEachIndexed { i, slot ->
                    if (i > 0) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            slot.time,
                            fontSize = 12.sp, color = TextSecondary,
                            // 시각이 세로로 맞아떨어지게 고정폭 숫자.
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.width(56.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(slot.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (slot.desc.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(slot.desc, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 지난 행사 1건 카드 — 제목 + "종료" 배지 + 팩트 목록. */
@Composable
private fun HoyolandPastEventCard(title: String, facts: List<HoyolandFact>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                GlgBadge("종료", TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            facts.forEachIndexed { i, f ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                HoyolandFactRow(f.label, f.value)
            }
        }
    }
}

/** 참여 게임 1줄 — 게임 태그 + 게임명 + 테마 제목. */
@Composable
private fun HoyolandLineupRow(item: HoyolandLineup) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 빈 문자열·0 은 "지정 안 함"이라는 뜻 — GlgGameTag 의 null 규약으로 옮긴다.
        GlgGameTag(
            item.game,
            abbrOverride = item.abbr.ifBlank { null },
            colorOverride = item.colorArgb.takeIf { it != 0L },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.game, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(item.theme, fontSize = 12.5.sp, color = TextSecondary)
        }
    }
}

/** 라벨(고정폭) + 값(줄바꿈 허용). */
@Composable
private fun HoyolandFactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

/** 요약 카드용 한 줄 — 값이 한 줄에 들어가는 자리라 라벨 칸이 더 좁다. */
@Composable
private fun HoyolandInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(48.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

/**
 * 홈·일정 탭이 쓰는 노출 판정 + 요약 문구.
 *
 * 두 화면이 각자 `HoyolandApi.current` 를 읽고 각자 조건을 쓰면, 한쪽만 D-60 이고
 * 다른 쪽은 D-30 인 식으로 갈라진다 — 판정은 [HoyolandEvent.isFeatured] 하나로 모은다.
 */
object HoyolandFeature {
    /** 홈 카드 한 줄 요약 — "10.2(금) 개막 · 일산 킨텍스 제2전시장 7·8홀". */
    fun summaryLine(e: HoyolandEvent): String {
        val head = when (e.phase()) {
            HoyolandPhase.ONGOING -> "진행 중"
            else -> e.periodLabel.substringBefore(" ~ ").substringAfter('.').let { "$it 개막" }
        }
        return "$head · ${e.venueShort}"
    }
}
