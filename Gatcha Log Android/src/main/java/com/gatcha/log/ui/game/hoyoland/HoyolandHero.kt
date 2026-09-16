package com.gatcha.log.ui.game.hoyoland

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEntry
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandPhase
import com.gatcha.log.data.StageSlot
import com.gatcha.log.data.HoyolandTicketStatus
import com.gatcha.log.ui.game.LiveRed
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor


/**
 * 호요랜드 히어로 — **Game HUD** 패널.
 *
 * ## 왜 흰 카드가 아닌가
 *
 * 이 페이지의 다른 섹션과 같은 흰 카드에 담으면, 담긴 것이 행사든 설정이든 화면이 똑같아 보인다.
 * 정보는 정확한데 "행사"가 아니라 "명세서"로 읽혔다. 히어로만 **옅은 강조 틴트 면**으로 띄워
 * 상단에 무게를 준다 — 앱 전체 테마는 그대로 두고(명세 §33) 호요랜드 안에서만 층위를 세운다(§3).
 *
 * 면은 `LocalAccentTint` 다. 명도 96.8 · 채도 20 으로 고정된 색이라 어떤 테마를 골라도
 * 흰 카드 옆에서 **같은 만큼만** 도드라진다(테마마다 진하기가 달라지지 않는다).
 *
 * ## HUD 로 읽히게 하는 것들
 *
 * - 큰 고정폭 숫자 — 자릿수가 줄어도 폭이 안 흔들린다
 * - 칸으로 끊은 게이지 — 연속 막대보다 "얼마나 왔나"가 눈금으로 읽힌다
 * - 영문 소캡스 라벨 — 값이 한국어라 라벨은 짧은 영문이 덜 시끄럽다
 *
 * @param onTicket 예매처로. 살 수 없는 상태면 눌리지 않는다.
 * @param onMap 지도 앱으로.
 * @param onOfficial 공식 안내로. 주소가 없으면 버튼이 서지 않는다.
 */
@Composable
fun HoyolandHero(
    e: HoyolandEvent,
    /** 내 입장권 — 다음에 가는 날의 조가 패널 맨 아래 한 줄로 선다. 비면 그 줄이 없다. */
    entry: HoyolandEntry = HoyolandEntry(),
    onTicket: () -> Unit,
    onMap: () -> Unit,
    onOfficial: () -> Unit,
    /** 오늘 시간표로. 행사 기간에는 주 버튼이 예매 대신 이걸 연다. */
    onStage: () -> Unit = {},
) {
    // 패널 면 — `LocalAccentTint` 는 명도 96.8 이라 이 페이지의 회색 배경 위에서는 면이
    // **거의 안 보였다.** 면이 안 보이면 18dp 안쪽 패딩만 남아 히어로 글자가 다른 섹션보다
    // 안쪽으로 밀린 것처럼 읽힌다(2026-09-16 지적). 강조색을 옅게 깔아 면을 세운다.
    val panel = LocalAccent.current.copy(alpha = 0.10f)
    val deep = LocalAccentDeep.current
    val phase = e.phase()
    val ended = phase == HoyolandPhase.ENDED

    // ── 규격은 **캐릭터 상세 히어로와 같다**(`EnkaCharSection.CharHero`).
    //
    // 화면 폭을 꽉 채우고 **아래 모서리만** 30dp 로 깎는다. 상세 페이지의 첫 덩이는 이 앱에서
    // 카드가 아니라 **머리판**이고, 좌우 여백 안에 든 카드로 두면 헤더와 본문 사이에 뜬 조각처럼
    // 보인다. 본문 좌우 패딩(16dp)을 [heroBleed] 로 되물려 가장자리까지 나간다.
    // 헤더·상태바 **뒤까지** 면이 올라간다. 헤더는 이 면 위에 떠 있고, 글자만 그 아래에서
    // 시작한다(`glgDetailContentTop()` = 상태바 + 헤더 높이). 캐릭터 상세와 같은 규칙이다.
    val topInset = glgDetailContentTop()
    Box(
        Modifier
            .heroBleed(top = topInset)
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(panel),
    ) {
        // 대표 이미지가 오면 패널 **배경**으로 깔린다(위에 그라데이션 스크림이 얹혀 글자가 산다).
        HeroKeyImage(e)
        // 좌우 20dp · 아래 26dp 도 캐릭터 히어로와 같은 값이다.
        // 위쪽 20dp — 캐릭터 상세는 12dp 지만 거긴 첫 요소가 가운데 정렬 초상이라 헤더 버튼과
        // 겹칠 일이 없다. 여긴 첫 줄이 좌우로 뻗는 글자라 12dp 면 오른쪽 배지가 헤더 버튼에
        // 닿아 보였다(2026-09-16 지적).
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = topInset + 20.dp, bottom = 20.dp)) {
            HudLabelRow(e, phase, ended)
            // ── 행사 중에 무대가 올라 있으면 히어로는 **그 무대**가 된다.
            //
            // 카운트다운(`2일차`)은 현장에 선 사람에게 아무것도 답하지 않는다. 그 자리가 답할
            // 질문은 "지금 뭐 하고 있나 · 언제 끝나나 · 다음은 뭔가" 하나로 바뀐다. 기간·장소는
            // 이미 와 있는 사람에게 필요 없는 값이라 같이 내린다(편성이 없으면 옛 화면 그대로).
            val live = if (phase.isEventLive) e.liveStageSlot() else null
            if (live != null) {
                Spacer(Modifier.height(14.dp))
                HeroLiveStage(e, live)
            } else {
                Spacer(Modifier.height(10.dp))
                HeroCountdown(e, phase, ended)
                if (phase.isBeforeEvent) {
                    Spacer(Modifier.height(16.dp))
                    HeroGauge(e)
                }
                // ── 사실 묶음은 **흰 박스**에 담는다.
                //
                // 틴트 면 위에 글자만 늘어놓았을 때는 카운트다운·게이지와 같은 층에 있어서,
                // 어디까지가 "지금 상태" 고 어디부터가 "행사 정보" 인지 경계가 없었다. 면을
                // 하나 올리면 그 경계가 선 하나 없이 생긴다(구분선도 같이 걷힌다).
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.72f))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    HudField("DATE", e.periodLongLabel)
                    Spacer(Modifier.height(11.dp))
                    HudField("PLACE", e.venueShort)
                    // ── 내 입장권 — **정해 둔 사람에게만** 뜬다. 기간·장소는 누구에게나 같은
                    // 값이지만 이 줄만 내 값이라, 있으면 제일 먼저 찾게 되는 줄이 된다.
                    // 고른 날을 **전부** 건다(나흘을 한눈에 봐야 하는 값이다).
                    val entryLines = e.entryLines(entry)
                    if (entryLines.isNotEmpty()) {
                        Spacer(Modifier.height(11.dp))
                        HudField("MY PASS", entryLines.joinToString("\n"), valueColor = deep)
                    }
                }
            }
            // ── 액션은 패널 **안쪽**이다. 밖에 두면 패널이 끝난 자리에 버튼 줄이 따로 떠서
            // 머리판과 본문 사이에 층이 하나 더 생겼다. 안에 두면 "이 행사로 무엇을 하는가"가
            // 머리판 한 덩이 안에서 끝난다.
            if (!ended) {
                Spacer(Modifier.height(18.dp))
                HeroActions(e, phase, onTicket, onMap, onOfficial, onStage)
            }
        }
    }
}

/**
 * 본문 좌우 패딩을 **되물려** 화면 가장자리까지 나가게 한다.
 *
 * 상세 본문은 좌우 16dp 안에서 그려지는데 머리판만 그 밖으로 나가야 한다. Compose 에는 음수
 * 패딩이 없어 폭을 그만큼 늘려 측정하고 왼쪽으로 당겨 놓는다(`offset` 만 쓰면 폭이 그대로라
 * 오른쪽이 잘린다).
 */
private fun Modifier.heroBleed(
    inset: androidx.compose.ui.unit.Dp = 16.dp,
    top: androidx.compose.ui.unit.Dp = 0.dp,
): Modifier = this.layout { measurable, constraints ->
    val extra = inset.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val topPx = top.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    // 차지하는 높이에서도 `topPx` 를 빼야 **아래 콘텐츠가 따라 올라온다** — 위치만 당기면
    // 히어로가 올라간 자리에 그만큼 빈 띠가 남는다.
    layout(constraints.maxWidth, (placeable.height - topPx).coerceAtLeast(0)) {
        placeable.place(-inset.roundToPx(), -topPx)
    }
}

/**
 * 히어로 패널 안 구분선 — **양 플랫폼 고정값.**
 *
 * 앱 공용 [DividerColor](#F0F0F0) 는 흰 카드 위를 전제한 값이라 강조 틴트 면 위에서는 거의
 * 사라진다. iOS 는 반대로 시스템 `Divider`(separator)가 기본이라 더 진했고, 그래서 같은
 * 패널인데 두 플랫폼의 선 굵기가 달라 보였다(2026-09-16 지적).
 *
 * 시스템 색은 기기·다크모드에 따라 또 갈라지므로 **검정 12%** 로 못 박는다.
 */
private val HeroDivider = Color.Black.copy(alpha = 0.12f)

/** 히어로 액션 줄 높이 — 아이콘 + 글자가 같이 서는 줄이라 본문 버튼(44dp)보다 한 단 키운다. */
private val HERO_ACTION_HEIGHT = 48.dp

/** 행사명 줄 — 왼쪽은 이름, 오른쪽은 지금 어느 단계인지. */
@Composable
private fun HudLabelRow(e: HoyolandEvent, phase: HoyolandPhase, ended: Boolean) {
    val accent = LocalAccent.current
    val deep = LocalAccentDeep.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            // 머리줄은 **영문 대문자**다(`HOYOLAND 2026`). 바로 아래 큰 숫자·영문 단계 배지와
            // 같은 결로 서야 패널이 한 덩이로 읽힌다 — 한글 행사명은 그 사이에서 혼자 튀었다.
            // 행사 중에는 며칠째인지가 붙는데, 그것도 영문(`DAY 1`)으로 맞춘다.
            if (phase.isEventLive && e.dayOrdinal() > 0) {
                "${e.editionLabel} · DAY ${e.dayOrdinal()}"
            } else {
                e.editionLabel
            },
            // 머리줄에 **크기와 색**을 준다 — 패널에서 제일 먼저 읽히는 줄이 되어야 아래 숫자가
            // 무엇의 D-day 인지 바로 붙는다. 색은 글자용 `LocalAccentDeep`(대비 5.2).
            fontSize = 17.sp, fontWeight = FontWeight.Black, color = deep,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        val stage = when (phase) {
            HoyolandPhase.UPCOMING -> "UPCOMING"
            HoyolandPhase.TOMORROW -> "TOMORROW"
            HoyolandPhase.TODAY -> "TODAY"
            HoyolandPhase.ONGOING -> "ONGOING"
            HoyolandPhase.ENDED -> "ENDED"
        }
        Text(
            stage,
            fontSize = 9.5.sp, fontWeight = FontWeight.Black,
            color = when {
                phase.isEventLive -> Color.White
                ended -> TextSecondary
                else -> deep
            },
            letterSpacing = 1.2.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    when {
                        // 진행 중만 **면이 찬 빨강**이다 — 다른 단계와 같은 옅은 배지로 두면
                        // "지금 열리고 있다" 가 배지에서 안 읽힌다.
                        phase.isEventLive -> LiveRed
                        ended -> DividerColor
                        else -> accent.copy(alpha = 0.16f)
                    },
                )
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/**
 * 지금 무대 — 행사 중 히어로의 주인공.
 *
 * 제목이 가장 크고, 그 아래로 시각 · 게임 · 남은 시간이 붙는다. 진행 바가 빨강인 이유는
 * 이 줄만 **지금 이 순간에 묶인 값**이라서다(다른 값은 하루 종일 그대로다).
 */
@Composable
private fun HeroLiveStage(e: HoyolandEvent, live: StageSlot) {
    val next = e.nextStageSlot()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(LiveRed))
        Spacer(Modifier.width(5.dp))
        Text(
            if (live.slot.desc.isNotBlank()) "LIVE · ${live.slot.desc.lineSequence().first().trim()}" else "LIVE",
            fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = LiveRed, letterSpacing = 0.7.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        live.slot.title,
        fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary,
        lineHeight = 27.sp, letterSpacing = (-0.4).sp,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        listOf(live.rangeLabel, live.slot.game).filter { it.isNotBlank() }.joinToString(" · "),
        fontSize = 13.sp, color = TextSecondary,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
    )
    Spacer(Modifier.height(13.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DividerColor)) {
        Box(
            Modifier
                .fillMaxWidth(live.progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(LiveRed),
        )
    }
    if (live.remainMin > 0) {
        Spacer(Modifier.height(7.dp))
        Text("${live.remainMin}분 남음", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LiveRed)
    }
    if (next != null) {
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(HeroDivider))
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("다음 ", fontSize = 12.sp, color = TextSecondary)
            Text(
                "${next.slot.time} ${next.slot.title}",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (next.slot.desc.isNotBlank()) {
                Text(
                    " · ${next.slot.desc.lineSequence().first().trim()}",
                    fontSize = 12.sp, color = TextSecondary, maxLines = 1,
                )
            }
        }
    }
}

/** 남은 날짜 — **숫자 그 자체**가 패널의 주인공이다. */
@Composable
private fun HeroCountdown(e: HoyolandEvent, phase: HoyolandPhase, ended: Boolean) {
    val deep = LocalAccentDeep.current
    if (ended) {
        Text("EVENT ENDED", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextSecondary, letterSpacing = 1.sp)
        return
    }
    val number = if (phase.isEventLive) "${e.dayOrdinal()}" else "${e.daysUntilStart()}"
    val unit = if (phase.isEventLive) "일차" else "일 남음"
    // **밑선으로 맞춘다.** `Alignment.Bottom` 은 글자 상자의 아래를 맞추는 거라, 64sp 와 13sp
    // 처럼 크기가 크게 벌어지면 큰 쪽 상자 아래 여백만큼 작은 글자가 내려앉아 어긋나 보인다
    // (2026-09-16 지적). `alignByBaseline` 은 글자가 실제로 앉는 선을 맞춘다.
    Row {
        Text(
            number,
            fontSize = 64.sp, fontWeight = FontWeight.Black, color = deep,
            // 자릿수가 줄어도(D-10 → D-9) 숫자 폭이 흔들리지 않게 고정폭 숫자를 쓴다.
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            letterSpacing = (-2).sp,
            // 64sp 짜리 글자는 **앞쪽 사이드베어링**(글자 앞에 붙는 폰트 자체의 여백)이 커서,
            // 왼쪽 기준선이 위 머리줄·아래 박스보다 안쪽으로 밀려 보인다. 그만큼 당겨 세운다
            // (고정폭 숫자라 자릿수가 바뀌어도 이 값이 흔들리지 않는다).
            modifier = Modifier.alignByBaseline().offset(x = (-5).dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            unit,
            fontSize = 13.sp, color = TextSecondary,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * 진행 게이지 — 발표에서 개막까지 **칸으로 끊어** 보여 준다.
 *
 * 연속 막대였을 때는 "조금 찼다" 말고는 안 읽혔다. 칸을 나누면 몇 칸 남았는지가 세어지고,
 * 가운데 눈금(예매)이 **미정이라는 사실도 빈 칸처럼** 전달된다.
 */
@Composable
private fun HeroGauge(e: HoyolandEvent) {
    val accent = LocalAccent.current
    val deep = LocalAccentDeep.current
    val cells = 14
    val filled = (e.progress().coerceIn(0f, 1f) * cells).toInt().coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(cells) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (i < filled) accent else DividerColor),
            )
        }
    }
    Spacer(Modifier.height(7.dp))
    fun tick(ymd: String): String {
        val p = ymd.split("-")
        return if (p.size < 3) ymd else "${p[1].trimStart('0')}.${p[2].trimStart('0')}"
    }
    Row(Modifier.fillMaxWidth()) {
        Text("발표 ${tick(e.announceYmd)}", fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            e.ticket.openLabel.ifBlank { "예매 ${e.ticket.statusLabel}" },
            fontSize = 10.sp,
            color = if (e.ticket.isUndecided) TextSecondary else deep,
        )
        Spacer(Modifier.weight(1f))
        Text("개막 ${tick(e.startYmd)}", fontSize = 10.sp, color = TextSecondary)
    }
}

/** 라벨 한 줄 — 영문 소캡스 라벨 + 한국어 값. 라벨은 작고 흐리게 둬 값이 먼저 읽힌다. */
@Composable
private fun HudField(label: String, value: String, valueColor: Color = TextPrimary) {
    // 영문 소캡스 라벨. PERIOD · VENUE 에서 **한 낱말 더 흔한 말**로 바꿨다 — 값이 한국어라
    // 라벨은 눈이 스치듯 지나가는 자리고, 거기서 굳이 사전에서 찾을 단어를 쓸 이유가 없다.
    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, color = TextSecondary, letterSpacing = 1.1.sp)
    Spacer(Modifier.height(3.dp))
    Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor, lineHeight = 18.sp)
}

/**
 * 대표 이미지 — 패널 배경.
 *
 * **비어 있는 게 정상인 기간이 길다.** 행사 키아트는 개막 몇 주 전에야 나온다. 그때는 아무것도
 * 그리지 않는다 — 빈 띠를 세워 두면 "아직 안 받아온 것" 처럼 보여 로딩으로 오해된다.
 */
@Composable
private fun HeroKeyImage(e: HoyolandEvent) {
    val url = e.keyImageUrl
    if (url.isEmpty()) return
    Box(Modifier.matchParentSizeOrFull()) {
        coil.compose.AsyncImage(
            model = url,
            contentDescription = "${e.edition} 대표 이미지",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
        )
        // 스크림 — 이미지 위에서도 본문이 읽혀야 한다. 틴트 면 쪽으로 자연스럽게 넘어가게
        // **아래로 갈수록 희게** 덮는다(위는 이미지가 그대로 보인다).
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.92f)),
                ),
            ),
        )
    }
}

/** 부모 크기를 채우되 부모가 크기를 모를 때도 안전하게 — 패널은 내용이 높이를 정한다. */
private fun Modifier.matchParentSizeOrFull(): Modifier = this.fillMaxSize()

/**
 * 핵심 액션 셋 — 예매 · 지도 · 공식 안내(명세 §7).
 *
 * 패널 **바깥**에 둔다. 어두운 면 위에 강조색 버튼을 얹으면 둘 다 같은 밝기로 경쟁해
 * 무엇을 눌러야 하는지가 흐려졌다. 흰 바탕으로 내려오면 버튼이 혼자 밝다.
 *
 * 예매만 채움이다. 셋을 같은 무게로 두면 "그래서 뭘 해야 하나"가 사라진다 —
 * 개막 전 이 페이지를 여는 이유는 사실상 예매 하나다.
 */
@Composable
private fun HeroActions(
    e: HoyolandEvent,
    phase: HoyolandPhase,
    onTicket: () -> Unit,
    onMap: () -> Unit,
    onOfficial: () -> Unit,
    onStage: () -> Unit,
) {
    val soldOut = e.ticket.status == HoyolandTicketStatus.SOLD_OUT
    val canBuy = e.ticket.url.isNotBlank() && !soldOut
    // ── 개막하면 주 버튼이 **예매에서 오늘 시간표로** 바뀐다.
    //
    // 행사 기간에 이 화면을 여는 이유는 "표를 어떻게 사나" 가 아니다 — 표는 이미 손에 있고,
    // 현장에서 찾는 건 지금 무대다. 그때까지 예매 버튼이 주 자리를 잡고 있으면, 예매가 미정인
    // 행사에서는 **누를 수도 없는 「예매 미정」이 주 버튼으로 남는다**(2026-09-16 제보).
    val live = phase.isEventLive && e.hasTimetable
    val hasMap = e.mapUrl.isNotBlank()
    val hasOfficial = e.officialUrl.isNotBlank()
    // **한 줄에 셋.** 예매가 두 칸, 지도·공식이 한 칸씩이라 폭이 곧 무게다.
    //
    // 글자는 아이콘과 함께 서므로 짧게 간다("예매하기 · 티켓링크" 는 두 칸에도 안 들어간다) —
    // 예매처는 아래 「예매」 카드의 배지가 말한다. 보조 둘의 면이 **흰색**인 이유는 이 줄이
    // 강조 틴트 패널 바로 아래에 붙어서다: 틴트 면 위에 틴트 버튼을 놓으면 면이 사라진다.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlgButton(
            text = when {
                live -> "오늘 시간표"
                soldOut -> "매진"
                canBuy -> "예매"
                // 아직 열리지 않았으면 **언제 열리는지**가 답이다 — 누를 수 없는 버튼이라도
                // "예매 미정" 보다 "9월 14일(월) 19:00 오픈" 이 묻는 말에 답한다.
                e.ticket.openLabel.isNotBlank() -> e.ticket.openLabel
                else -> "예매 ${e.ticket.statusLabel}"
            },
            onClick = { if (live) onStage() else if (canBuy) onTicket() },
            enabled = live || canBuy,
            modifier = Modifier.weight(if (hasMap || hasOfficial) 2f else 1f),
            height = HERO_ACTION_HEIGHT,
            icon = when {
                live -> Icons.Outlined.CalendarMonth
                canBuy || soldOut -> Icons.Outlined.ConfirmationNumber
                else -> null
            },
        )
        if (hasMap) {
            GlgOutlineButton(
                "지도", onMap, Modifier.weight(1f),
                height = HERO_ACTION_HEIGHT, icon = Icons.Outlined.Place, onTint = true,
            )
        }
        if (hasOfficial) {
            GlgOutlineButton(
                "공식", onOfficial, Modifier.weight(1f),
                height = HERO_ACTION_HEIGHT, icon = Icons.Outlined.Language, onTint = true,
            )
        }
    }
}

/**
 * 참여 게임 — 히어로에 딸려 있던 것을 **독립 섹션**으로 뺐다(명세 §4 LINEUP).
 *
 * 칩 한 줄에서 **세로 목록**으로 바꿨다. 칩은 다섯 칸을 393dp 에 욱여넣느라 글자가 9.5sp 까지
 * 내려갔고, 무엇보다 게임마다 다른 **테마**(`달빛에 전하는 세레나데`)를 걸 자리가 없었다 —
 * config 가 들고 있는데 화면 어디에도 안 뜨던 값이다. "어느 게임이 오나" 다음 질문이
 * "그 게임이 뭘 들고 오나" 인데 거기서 끊겨 있었다.
 *
 * 줄마다 왼쪽 색 바가 게임을 가른다. **행사 중에는 지금 무대를 하는 게임 줄만 남고 나머지는
 * 흐려져** 목록이 곧 현재 상태가 된다(부제도 테마에서 무대 상태로 바뀐다).
 */
@Composable
fun HoyolandLineupSection(e: HoyolandEvent, onOpenGame: (String) -> Unit) {
    if (e.lineup.isEmpty()) return
    val liveGame = e.liveStageGame()
    // 제목 줄은 다른 섹션(「둘러보기」·「예매」)과 **같은 규격**이다 — 제목 16sp + 오른쪽
    // 보조 문구 11.5sp. 여기만 영문 소캡스 제목에 설명이 카드 아래 따로 붙어 있어, 한 화면에서
    // 제목이 두 종류로 갈리고 설명도 딴 자리에서 떠 있었다.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text("라인업", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        if (e.lineup.any { it.url.isNotBlank() }) {
            Text("누르면 게임 공지로 가요", fontSize = 11.5.sp, color = TextSecondary)
        }
    }
    Spacer(Modifier.height(10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth()) {
            e.lineup.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                HoyolandLineupRow(item, e, liveGame) { onOpenGame(item.url) }
            }
        }
    }
}

/**
 * 참여 게임 한 줄 — 색 바 + 게임명 + 부제.
 *
 * 부제는 단계에 따라 갈린다. 행사 전에는 **테마**, 행사 중에는 **오늘 그 게임의 무대 상태**
 * ([HoyolandEvent.lineupStatusOf]). 같은 자리가 그때그때 답할 질문에 답한다.
 */
@Composable
private fun HoyolandLineupRow(
    item: com.gatcha.log.data.HoyolandLineup,
    e: HoyolandEvent,
    liveGame: String,
    onClick: () -> Unit = {},
) {
    val c = lineupColorOf(item)
    val live = liveGame.isNotBlank() && item.game == liveGame
    val dim = liveGame.isNotBlank() && !live
    // 공지 주소가 있는 게임만 눌린다 — 없는 줄까지 눌리는 척하면 눌러 보고 아무 일도 안 일어난다.
    val linked = item.url.isNotBlank()
    val status = e.lineupStatusOf(item.game)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (linked) Modifier.clickable { onClick() } else Modifier)
            .background(if (live) c.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp).height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.copy(alpha = if (dim) 0.45f else 1f)),
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.game,
                fontSize = 13.5.sp,
                fontWeight = if (live) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (dim) TextSecondary else TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // 행사 중이면 무대 상태가 테마를 밀어낸다 — 현장에서 테마는 이미 아는 값이다.
            val caption = status.ifBlank { item.theme }
            if (caption.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    caption,
                    fontSize = 11.5.sp,
                    fontWeight = if (live || status.endsWith("다음 무대")) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        live -> c
                        status.endsWith("다음 무대") -> LocalAccentDeep.current
                        dim -> TextSecondary.copy(alpha = 0.7f)
                        else -> TextSecondary
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        if (live) {
            Spacer(Modifier.width(8.dp))
            Text(
                "LIVE",
                fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = Color.White,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(LiveRed)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        } else if (linked) {
            Spacer(Modifier.width(8.dp))
            Text("\u203A", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary.copy(alpha = 0.55f))
        }
    }
}

/** 게임 색 — config 가 주면 그 값, 없으면 `GameData` 기본값. */
private fun lineupColorOf(item: com.gatcha.log.data.HoyolandLineup): Color =
    if (item.colorArgb != 0L) {
        item.colorArgb.toColor()
    } else {
        com.gatcha.log.data.GameData.colorFor(item.game).toColor()
    }
