package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.GldsSection
import com.gatcha.log.ui.components.GldsHairline
import com.gatcha.log.ui.game.hoyoland.HoyolandListText
import com.gatcha.log.ui.game.hoyoland.HoyolandTicketKicker
import com.gatcha.log.ui.game.hoyoland.HoyolandTicketRule
import com.gatcha.log.ui.game.hoyoland.HoyolandTicketShape
import com.gatcha.log.ui.game.hoyoland.HoyolandTicketStub
import com.gatcha.log.ui.game.hoyoland.TicketChevron
import com.gatcha.log.ui.game.hoyoland.TicketLabelText
import com.gatcha.log.ui.game.hoyoland.TicketRowDivider
import com.gatcha.log.ui.game.hoyoland.TicketSubText
import com.gatcha.log.ui.game.hoyoland.TicketTileBg
import com.gatcha.log.ui.game.hoyoland.hoyolandTicketDeep
import com.gatcha.log.ui.game.hoyoland.ticketCountdown
import androidx.compose.foundation.rememberScrollState
import com.gatcha.log.ui.theme.toColor
import androidx.compose.runtime.collectAsState
import com.gatcha.log.data.HoyolandCart
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.data.GameData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.gatcha.log.data.HoyolandGoods
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEntry
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandFact
import com.gatcha.log.data.HoyolandPhase
import com.gatcha.log.data.HoyolandProgram
import com.gatcha.log.data.api.HoyolandApi
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgHeaderElementHeight
import com.gatcha.log.ui.components.glgHeaderSurface
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.components.ChipIdleBorder
import com.gatcha.log.ui.components.ChipIdleText
import com.gatcha.log.ui.components.openExternalLink
import com.gatcha.log.ui.game.hoyoland.HoyolandEntrySheet
import com.gatcha.log.ui.game.hoyoland.HoyolandMapContent
import com.gatcha.log.ui.game.hoyoland.HoyolandHero
import com.gatcha.log.ui.game.hoyoland.HoyolandLineupSection
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.glgShortSpec
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
 *
 * **ON_RESUME 마다 다시 읽는다.** `LaunchedEffect(Unit)` 하나로 두면 최초 1회만 묻고 끝인데,
 * 홈 배너는 앱을 켜 두는 내내 composition 에 남아 있어 어드민에서 값을 고쳐도 재실행 전까지
 * 옛 값을 보여줬다(2026-09-13 확인 — 장소의 '(실내)' 표기가 그랬다). 홈의 당겨서 새로고침도
 * `refreshGameInfo` 만 불러 이 배너를 비켜간다.
 *
 * 매번 네트워크를 타지는 않는다 — [HoyolandApi.load] 가 15초 캐시로 막는다. 화면에 돌아올
 * 때마다 값을 다시 **묻기만** 하는 것이고, 이건 그 API 주석이 처음부터 전제한 동작이다.
 */
@Composable
private fun rememberHoyolandEvent(): HoyolandEvent {
    var event by remember { mutableStateOf(HoyolandApi.current) }
    // 화면에 돌아올 때(ON_RESUME) 다시 읽는다 — 설정 화면의 배터리·권한 배너가 쓰는 방식과 같다.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) { event = HoyolandApi.load() }
    return event
}

/**
 * 호요랜드 값 + **다시 읽기** — 상세 페이지의 당겨서 새로고침이 쓴다.
 *
 * `force` 로 읽으므로 캐시 나이와 무관하게 라이브(어드민)부터 다시 훑는다.
 * 개발자 목업이 얹혀 있었다면 여기서 걷힌다.
 *
 * 상세의 **본문도 이 값을 쓴다**(27.52.0). 예전엔 본문이 [rememberHoyolandEvent] 로 값을 따로 들고 있어서,
 * 당겨서 새로고침해도 히어로 · 예매 · 둘러보기는 옛 값 그대로였다(헤더와 하위 페이지만 바뀌었다).
 * 그래서 본문이 하던 ON_RESUME 다시 읽기도 여기서 한다 — 빼면 화면에 돌아왔을 때 본문이 안 갱신된다.
 */
@Composable
private fun rememberHoyolandRefresher(): Triple<HoyolandEvent, Boolean, () -> Unit> {
    var event by remember { mutableStateOf(HoyolandApi.current) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) { event = HoyolandApi.load() }
    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                event = HoyolandApi.load(force = true)
                refreshing = false
            }
        }
    }
    return Triple(event, refreshing, refresh)
}

/**
 * 홈·일정 탭용 — 지금 띄울 값어치가 있는 행사만 돌려준다(없으면 null).
 *
 * 게임정보 탭과 **같은 로더를 통과시키는 게 요점**이다. 홈이 캐시만 읽고 말면
 * 홈을 먼저 켠 사람은 원격 갱신 전 번들값을 보고, 게임정보 탭을 다녀온 뒤에야 값이 바뀐다.
 */
@Composable
fun rememberFeaturedHoyoland(): HoyolandEvent? = rememberHoyolandEvent().takeIf { it.isFeatured() }

/**
 * 게임정보 탭 요약 섹션 — 「오늘 할 일」 바로 밑(목업 `design_gameinfo_hoyoland_section_mockup.html` A · B 합본).
 *
 * - 위: D-day 타일 + 행사명 · 기간/장소 · 참여 게임 칩(A)
 * - 가운데: **행동이 붙은** 정보 줄 — 예매 → 상세, 장소 → 지도. 행사 중엔 「무대」 줄이 끼어든다(B)
 * - 아래: 시간표 · 굿즈 · 부스 · 푸드로 상세를 거치지 않고 **곧장** 들어가는 바로가기 4칸(A)
 *
 * 예전 카드는 일정 · 장소 · 예매 상태 세 줄뿐이라 열어 봐야 뭘 할 수 있는지 보였다. 폐막 뒤에는 한 줄로 줄어든다.
 * 아이콘은 이모지가 아니라 아웃라인 머티리얼 아이콘이다. iOS `HoyolandSection` 과 파리티.
 *
 * @param onOpen 열 하위 페이지. [HoyolandSub.None] 이면 상세 페이지.
 */
@Composable
fun HoyolandSection(onOpen: (HoyolandSub) -> Unit) {
    val ctx = LocalContext.current
    val e = rememberHoyolandEvent()
    val phase = e.phase()
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("호요랜드", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            "전체 보기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TicketSubText,
            // 누르는 자리(좌우 6 · 위아래 4)가 글자를 안쪽으로 밀지 않게 6 을 민다 — 글자 끝이 섹션 콘텐츠 끝(좌우 20)에 닿는다.
            // iPhone 과 같은 자리이고, 「공지·뉴스」 머리의 「더보기」도 여기에 맞춘다(10/7).
            modifier = Modifier.offset(x = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpen(HoyolandSub.None) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
    if (phase.isOffSeason) {
        // 종료 · 일정 미정 — 한 줄로 줄인다. 들어가는 문은 헤더 「전체 보기」 하나다
        // (「지난 행사 보기」가 같은 곳으로 가는 버튼이라 둘이 겹쳤다).
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Celebration, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${e.edition} · ${e.statusLabel()}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                // 상단 한 줄 — 어드민 공지 문구, 없으면 일정 미정의 자동 문구. 상세 히어로와 같은 문장([HoyolandEvent.topNotice]).
                val notice = e.topNotice()
                if (notice.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    HoyolandListText(notice, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
                }
            }
        }
        return
    }
    // ── 입장권 카드 — 홈 배너와 같은 부품(`HoyolandTicket.kt`). 디자인 정본은 아티팩트
    // 「호요랜드 배너 시안」 C안(2026-09-28 확정): 머리는 입장권(흰 면 + 짙은 조각), 절취선 아래는
    // 누르면 할 일이 있는 줄(예매 · 장소)과 바로가기 4칸.
    val deep = hoyolandTicketDeep()
    val (cap, big) = e.ticketCountdown()
    // 머리 104 · 줄 44 · 칸 68 — 시안(116 · 48 · 88)보다 한 단씩 낮췄다. 폰에서 카드 하나가
    // 한 화면 절반을 넘게 먹었다(2026-09-28 지적).
    val headHeight = 104.dp
    // 반원 홈은 머리와 본문이 만나는 **절취선 한가운데** 하나 — 카드 안쪽에 뚫린 구멍이다.
    // 화면 폭 섹션이라 둥근 모서리 없이(10/1) — 절취선 홈만 남긴다.
    val shape = remember { HoyolandTicketShape(corner = 0.dp) { listOf(headHeight.toPx()) } }
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White)) {
        // ── 머리 — 남은 날짜가 주인공이다.
        Row(Modifier.fillMaxWidth().height(headHeight).clickable { onOpen(HoyolandSub.None) }) {
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                HoyolandTicketKicker(e.editionLabel, deep)
                Spacer(Modifier.height(6.dp))
                Text(
                    e.edition, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (e.lineup.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        e.lineup.take(3).forEach { l ->
                            val raw = e.stageColor(l.game)
                            val c = if (raw == 0L) TicketSubText else raw.toColor()
                            HoyolandMiniChip(e.stageLabel(l.game), c)
                        }
                        if (e.lineup.size > 3) HoyolandMiniChip("+${e.lineup.size - 3}", TicketSubText)
                    }
                }
            }
            HoyolandTicketStub(cap, big, deep, sub = e.periodDotsLabel)
        }
        HoyolandTicketRule()
        // 본문 — 정보 줄은 **구분선으로** 가른다. 간격만으로 띄우면 줄과 줄의 경계가 흐려지고
        // 세로가 헐거워졌다(2026-09-28 지적). 줄 높이는 44(터치 최소), 사이 간격은 0.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
            // ── 정보 줄 — 줄마다 누르면 할 수 있는 일이 있다.
            HoyolandActionRow("예매", hoyolandTicketSummary(e)) {
                // 예매 주소가 있으면 곧장 예매처로 — 상세 페이지의 「예매하기」와 같은 동작(앱이 깔려 있으면 앱 먼저).
                // 주소가 아직 없으면(예매 미정) 상세로 간다.
                if (e.ticket.url.isNotBlank()) {
                    openExternalLink(ctx, e.ticket.url, preferPackage = e.ticket.appPackage.ifBlank { null })
                } else {
                    onOpen(HoyolandSub.None)
                }
            }
            if (e.mapUrl.isNotBlank()) {
                HoyolandRowDivider()
                HoyolandActionRow("장소", "${e.venueRowLabel} · 지도") {
                    openExternalLink(ctx, e.mapUrl)
                }
            }
            // 행사 중에만 — 지금 무대가 이 카드가 답할 첫 질문이 된다.
            if (phase.isEventLive && e.hasTimetable) {
                HoyolandRowDivider()
                HoyolandActionRow("무대", e.stageEntryLine()) { onOpen(HoyolandSub.Stage) }
            }
            // ── 바로가기 4칸 — 상세를 한 번 거치지 않고 곧장.
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val slots = e.days.sumOf { it.slots.size }
                HoyolandQuickTile(Icons.Outlined.CalendarMonth, "시간표", if (slots > 0) "${slots}편" else "공개 전", deep, Modifier.weight(1f)) {
                    onOpen(HoyolandSub.Stage)
                }
                HoyolandQuickTile(Icons.Outlined.ShoppingBag, "굿즈", e.visibleGoods.size.let { if (it > 0) "${it}종" else "공개 전" }, deep, Modifier.weight(1f)) {
                    onOpen(HoyolandSub.Goods)
                }
                HoyolandQuickTile(Icons.Outlined.Storefront, "부스", e.booths.size.let { if (it > 0) "${it}곳" else "공개 전" }, deep, Modifier.weight(1f)) {
                    onOpen(HoyolandSub.Booth)
                }
                HoyolandQuickTile(Icons.Outlined.Restaurant, "푸드", e.foodPrograms.size.let { if (it > 0) "${it}곳" else "공개 전" }, deep, Modifier.weight(1f)) {
                    onOpen(HoyolandSub.Food)
                }
            }
        }
    }
}

/** 예매 줄 한 마디 — "판매 중 · 티켓링크" / "9월 14일(월) 19:00 오픈 · 티켓링크" / "매진". */
private fun hoyolandTicketSummary(e: HoyolandEvent): String {
    val t = e.ticket
    val vendor = t.vendor.ifBlank { null }
    return when (t.status) {
        com.gatcha.log.data.HoyolandTicketStatus.ON_SALE -> listOfNotNull("판매 중", vendor).joinToString(" · ")
        com.gatcha.log.data.HoyolandTicketStatus.ANNOUNCED ->
            listOfNotNull(t.openLabel.ifBlank { null }?.let { "$it 오픈" } ?: "오픈 예정", vendor).joinToString(" · ")
        com.gatcha.log.data.HoyolandTicketStatus.SOLD_OUT -> "매진"
        com.gatcha.log.data.HoyolandTicketStatus.UNDECIDED -> "예매 일정 미정"
    }
}

@Composable
private fun HoyolandMiniChip(text: String, color: Color) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 정보 줄 사이 구분선 — 카드 흰 면 위에서 보이는 한 단 짙은 회색. */
@Composable
private fun HoyolandRowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TicketRowDivider))
}

/** 정보 줄 — 라벨 · 값 · 셰브론. 줄 전체가 누르는 자리다(44dp). */
@Composable
private fun HoyolandActionRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = TicketLabelText, modifier = Modifier.width(40.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TicketChevron, modifier = Modifier.size(18.dp))
    }
}

/** 바로가기 한 칸 — 아이콘 · 이름 · 규모(몇 편 · 몇 종). */
@Composable
private fun HoyolandQuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.heightIn(min = 68.dp).clip(RoundedCornerShape(14.dp)).background(TicketTileBg)
            .clickable { onClick() }.padding(top = 10.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(sub, fontSize = 12.sp, color = TicketLabelText, maxLines = 1)
    }
}

/**
 * 호요랜드 상세 페이지.
 *
 * 구성 순서는 **지금 알아야 하는 것부터**다: 언제·어디서(히어로) → 어떻게 가나(예매) →
 * 뭘 보나(참여 게임·프로그램) → 참고(지난 행사).
 * 예전에는 장소 카드가 맨 위였고 일정이 그 아래 따로 있어서, 가장 먼저 궁금한 날짜가 두 번째였다.
 */
/**
 * 호요랜드 상세 **페이지** — 헤더(뒤로가기)까지 여기서 소유한다.
 *
 * 페이지를 통째로 내보내는 이유: 호요랜드 상세는 게임정보 탭과 홈, 두 곳에서 열린다.
 * 헤더 액션과 하위 페이지 상태를 호출부마다 따로 두면 두 곳이 어긋난다.
 */
@Composable
fun HoyolandDetailPage(
    viewModel: SpendingViewModel,
    onBack: () -> Unit,
    /** 게임정보 탭 바로가기가 곧장 열 하위 페이지. 거기서 뒤로 가면 상세로 온다. */
    initialPage: HoyolandSub = HoyolandSub.None,
    /**
     * 지난 회차 보관본 — 주면 **그 회차를 읽기 전용으로** 그린다(「지난 행사 ▸ 상세 보기」, 10/6).
     * 같은 페이지 · 같은 하위 페이지를 그대로 쓰되 원격 갱신 · 당겨서 새로고침 · 내 입장권 · 굿즈 담기가 없고,
     * 제목은 그 회차 이름이다.
     */
    archive: HoyolandEvent? = null,
) {
    val readOnly = archive != null
    val noRefresh: () -> Unit = {}
    val (e, refreshing, refresh) = if (archive != null) Triple(archive, false, noRefresh) else rememberHoyolandRefresher()
    val liveCart by viewModel.hoyolandCart.collectAsState()
    val liveEntry by viewModel.hoyolandEntry.collectAsState()
    // 장바구니 · 내 입장권은 **지금 회차**의 것이다 — 지난 회차 화면에 비치지 않게 빈 값으로 둔다.
    val cart = if (readOnly) HoyolandCart() else liveCart
    val entry = if (readOnly) HoyolandEntry() else liveEntry
    // 「지난 행사 ▸ 상세 보기」로 열 회차 키("2026").
    var archiveKey by remember { mutableStateOf("") }
    val accent = LocalAccent.current
    val accentDeep = LocalAccentDeep.current
    // 내 입장권 시트 — 헤더 버튼으로만 열린다. 하위 페이지와 달리 화면을 갈아 끼우지 않으므로
    // `page` 상태에 섞지 않는다(시트를 닫으면 보고 있던 자리에 그대로 남아야 한다).
    var entrySheetOpen by remember { mutableStateOf(false) }
    // 하위 페이지에서 **어디로 돌아갈지**. 배치도에서 들어오면 배치도로 돌아와야 한다 —
    // 기본값(상세)으로 두면 지도에서 한 곳 보고 나올 때마다 지도가 닫혀 다시 찾아 들어가야 한다.
    var returnTo by remember { mutableStateOf(HoyolandSub.None) }
    // 전환 방향 — **깊이로는 못 가른다.** 배치도와 부스는 둘 다 하위 1단계라, 깊이만 보면
    // 배치도로 돌아올 때도 "들어간다" 로 읽혀 밀려 나가는 애니메이션이 거꾸로 났다
    // (2026-09-16 지적). 어디로 가는지는 누른 쪽이 알고 있으니 그걸 그대로 들고 간다.
    var navBack by remember { mutableStateOf(false) }
    var boothFilter by remember { mutableStateOf<String?>(null) }
    // 하위 페이지를 **상태 하나로** 모은다. 예전엔 일부만 AnimatedContent 에 있고 나머지는
    // `if … return` 으로 컴포지션을 갈아끼워, 같은 페이지에서 나가는데 어떤 건 밀려 나가고
    // 어떤 건 0프레임으로 튀었다(홈 `HomeSub` 와 같은 이유로 하나로 합쳤다).
    var page by remember { mutableStateOf(initialPage) }
    // 상세 본문 스크롤 — **하위 페이지를 다녀와도 보던 자리에 남아야 한다.** 페이지가
    // `AnimatedContent` 로 갈아 끼워지므로 상태를 바깥에 둬야 살아남는다(안에서 만들면 매번
    // 새로 생겨 맨 위로 튄다 — 굿즈 한 번 보고 나올 때마다 다시 내려야 했다).
    val detailScroll = rememberScrollState()
    /** 하위 페이지로 **들어간다** — 전환 방향까지 같이 남긴다. */
    // 배치도 가로 보기 — 구역 목록을 다녀와도 돌린 상태가 남도록 페이지 바깥에 둔다.
    // 배치도에 **새로 들어갈 때**는 처음 상태(세로)로 되돌린다(goSub) — 돌린 채 나갔다 다시 들어오면
    // 가로로 열려 헷갈렸다(2026-09-28 지적).
    var mapRotated by remember { mutableStateOf(false) }
    // 돌리기 애니메이션은 **버튼을 누른 그 한 번만** — 구역 목록에서 돌아와 판이 다시 그려질 때마다
    // 또 돌았다(2026-09-28 지적). 누를 때 세우고, 애니메이션이 끝나면 내린다.
    var mapSpinOnce by remember { mutableStateOf(false) }
    val goSub: (HoyolandSub) -> Unit = {
        navBack = false
        if (it == HoyolandSub.Map) mapRotated = false
        page = it
    }
    // 바로가기로 곧장 들어온 하위 페이지에서 뒤로 가면 **상세가 아니라 들어온 곳(게임정보 탭)** 으로 간다.
    // 상세를 거치지 않고 들어왔는데 뒤로가기에 상세가 끼어들면 한 번 더 눌러야 했다(2026-09-15 지적).
    val backFromSub: () -> Unit = {
        val back = returnTo
        navBack = true
        when {
            // 배치도에서 들어온 자리 — 거기로 돌려보낸다.
            back != HoyolandSub.None -> { returnTo = HoyolandSub.None; page = back }
            initialPage != HoyolandSub.None -> onBack()
            else -> page = HoyolandSub.None
        }
    }
    // 굿즈 게임 필터는 페이지 바깥에 둔다 — 탭이 SectionPage 의 붙박이 줄(stickyTop)로 올라가
    // 본문과 분리되므로, 상태를 본문 안에 두면 둘이 서로를 못 본다.
    var goodsFilter by remember { mutableStateOf<String?>(null) }
    // 시간표 날짜도 같다. 기본은 **행사 중이면 오늘** — 현장에서 첫날이 선택돼 있으면 매번
    // 한 번 더 눌러야 한다. 원격 갱신으로 기간이 바뀌면 목록을 키로 다시 잡는다.
    var stageDay by remember(e.dayYmds) { mutableStateOf(e.defaultDayIndex()) }
    // 굿즈 크게 보기 · 굿즈존 안내 시트 — 목록이 게으른 목록이라 상태를 목록 바깥에 둔다.
    var goodsViewing by remember { mutableStateOf<HoyolandGoods?>(null) }
    var goodsGuideOpen by remember { mutableStateOf(false) }
    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            // **계층 깊이로 방향을 정한다** — "상세가 아니면 push" 로 두면 굿즈 목록 ↔ 장바구니처럼
            // 둘 다 하위인 전환에서 돌아올 때도 밀려 나갔다(게임정보 탭 `subDepth` 와 같은 규칙).
            if (!navBack) {
                (slideInHorizontally(glgStandardSpec()) { w -> w } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeOut(glgShortSpec()))
            } else {
                (slideInHorizontally(glgStandardSpec()) { w -> -w / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { w -> w } + fadeOut(glgShortSpec()))
            }
        },
        label = "hoyolandSub",
    ) { p ->
        when (p) {
            HoyolandSub.Stage ->
                SectionPage(
                    "일자별 시간표",
                    onBack = backFromSub,
                    flat = true,   // 카드 없는 페이지(10/1) — 흰 바탕, 콘텐츠가 좌우 20 을 둔다
                    isRefreshing = refreshing,
                    onRefresh = if (readOnly) null else refresh,
                    stickyTop = if (e.dayYmds.size > 1) {
                        { HoyolandDayTabs(e, stageDay) { stageDay = it } }
                    } else null,
                ) {
                    // 날짜 탭(붙박이)이 아래 10 을 이미 둔다 — 위 여백을 더 얹지 않는다(10/6, 12 → 0).
                    // 날짜 탭과 게임 탭 사이가 10 + 12 + 10 으로 쌓여 두 탭이 따로 노는 것처럼 벌어져 있었다.
                    HoyolandSectionBox(top = if (e.dayYmds.size > 1) 0.dp else 22.dp) {
                        HoyolandTimetableSection(e, entry, stageDay)
                    }
                }
            HoyolandSub.Goods -> {
                SectionPage(
                    "굿즈 목록",
                    onBack = backFromSub,
                    flat = true,   // (10/1) 굿즈 한 장 한 장은 타일로 남기고 바탕만 흰색
                    // 지난 회차는 살 수 없다 — 합계 바 · 담기 없이 목록만.
                    bottomBar = if (readOnly) null else {
                        { HoyolandGoodsBar(e, cart) { goSub(HoyolandSub.Cart) } }
                    },
                    // 게임 탭은 붙박이다 — 100줄짜리 목록에서 같이 밀려 올라가면 지금 무엇으로
                    // 거르고 있는지도, 바꾸는 방법도 화면에서 사라진다.
                    stickyTop = if (e.goodsGames.size > 1) {
                        { HoyolandGoodsTabs(e, goodsFilter) { goodsFilter = it } }
                    } else null,
                    // 굿즈존 공통 안내 — 목록 위 카드였다가 헤더 인포 버튼으로 옮겼다. 사기 전에 한 번 보면
                    // 되는 값이라 목록 첫 화면을 차지할 이유가 없다.
                    actions = {
                        if (e.goodsGuide.isNotBlank()) {
                            com.gatcha.log.ui.components.GlgCircleIconButton(
                                com.gatcha.log.ui.components.GlgLineIcons.Info, "굿즈존 이용 안내",
                            ) { goodsGuideOpen = true }
                        }
                    },
                    lazyContent = {
                        hoyolandGoodsItems(
                            e, cart, goodsFilter,
                            onQuantity = { name, n -> viewModel.setGoodsQuantity(name, n) },
                            onImage = { goodsViewing = it },
                            readOnly = readOnly,
                        )
                    },
                )
                goodsViewing?.let { v ->
                    HoyolandGoodsImageSheet(e, v, cart, { name, n -> viewModel.setGoodsQuantity(name, n) }, readOnly = readOnly) { goodsViewing = null }
                }
                if (goodsGuideOpen) HoyolandGuideSheet(e.goodsGuide, onDismiss = { goodsGuideOpen = false })
            }
            HoyolandSub.Cart ->
                SectionPage(
                    "장바구니",
                    onBack = { navBack = true; page = HoyolandSub.Goods },
                    flat = true,   // (10/1)
                    actions = {
                        // 비우기는 헤더 우측 — 실수로 누르기 어려운 자리다.
                        if (!cart.isEmpty) {
                            Text(
                                "비우기",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerText,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.clearGoodsCart() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    },
                ) {
                    HoyolandSectionBox { HoyolandCartContent(e, cart) { name, n -> viewModel.setGoodsQuantity(name, n) } }
                }
            HoyolandSub.Booth ->
                SectionPage("부스 체험", onBack = backFromSub, flat = true) {
                    HoyolandSectionBox { HoyolandBoothContent(e, boothFilter) { boothFilter = it } }
                }
            HoyolandSub.Food ->
                SectionPage("푸드존", onBack = backFromSub, flat = true) { HoyolandFoodContent(e) }
            HoyolandSub.Map ->
                SectionPage(
                    e.map.title.ifBlank { "행사장 배치도" },
                    onBack = backFromSub,
                    flat = true,   // (10/1) 판은 객체로 남긴다 — 좌우 20 은 아래에서 준다
                    // 화면 돌리기 — 가로로 긴 도면을 눕혀 페이지를 꽉 채운다. 돌린 동안은 스크롤하지 않는다.
                    actions = {
                        GlgCircleIconButton(
                            com.gatcha.log.ui.components.GlgLineIcons.Rotate,
                            if (mapRotated) "세로로 보기" else "가로로 보기",
                        ) { mapSpinOnce = true; mapRotated = !mapRotated }
                    },
                    scrollable = !mapRotated,
                ) {
                    // 구역을 누르면 그 존의 목록으로 간다 — 지도가 목록의 입구가 된다.
                    // flat 페이지라 좌우 20 · 위 22 · 아래 20 을 여기서 준다(10/1). 가로 보기는 화면을 꽉 채우므로 위아래 여백 없이.
                    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = if (mapRotated) 0.dp else 22.dp, bottom = if (mapRotated) 0.dp else 20.dp)) {
                    HoyolandMapContent(
                        e,
                        rotated = mapRotated,
                        animateTransition = mapSpinOnce,
                        onTransitionDone = { mapSpinOnce = false },
                    ) { z ->
                        // 게임 부스 칸은 **그 게임으로 걸러진** 부스 목록으로 보낸다 — 지도에서
                        // 원신 부스를 눌렀는데 24곳 전체가 나오면 다시 찾아야 한다.
                        val target = when (z.kind) {
                            "goods" -> HoyolandSub.Goods
                            "stage" -> HoyolandSub.Stage
                            "food" -> HoyolandSub.Food
                            "booth", "game" -> HoyolandSub.Booth
                            else -> HoyolandSub.None   // 입장 동선은 지나는 길이라 갈 데가 없다
                        }
                        if (target != HoyolandSub.None) {
                            if (target == HoyolandSub.Booth) {
                                // DIY존 칸은 DIY 탭으로 곧장.
                                boothFilter = when {
                                    z.label.contains("DIY") && e.hasDiy -> HOYOLAND_BOOTH_DIY
                                    e.partnerForZone(z.label) != null -> HOYOLAND_BOOTH_PARTNER
                                    else -> z.game.takeIf { it.isNotBlank() && it in e.boothGames }
                                }
                            }
                            if (target == HoyolandSub.Goods) {
                                goodsFilter = z.game.takeIf { it.isNotBlank() }
                            }
                            returnTo = HoyolandSub.Map
                            goSub(target)
                        }
                    }
                    }
                }
            // 지난 회차 상세 — 같은 페이지를 그 회차 보관본으로 한 번 더 그린다(읽기 전용).
            HoyolandSub.Archive -> HoyolandArchivePage(viewModel, archiveKey, onBack = backFromSub)
            HoyolandSub.None ->
                SectionPage(
                    if (readOnly) e.edition else "호요랜드",
                    onBack,
                    flat = true,   // (10/1) 히어로는 화면 폭, 아래 섹션은 띠로 나눈다
                    isRefreshing = refreshing,
                    onRefresh = if (readOnly) null else refresh,
                    // 상세 페이지만 — 붙박이 줄이 없어 바탕판이 필요 없다. 다른 상세처럼 콘텐츠가 헤더 밑으로 지나간다.
                    showBackdrop = false,
                    scrollState = detailScroll,
                    actions = {
                        // ── 내 입장권 — 고르는 일은 **표를 살 때 한 번**이고 그 뒤로는 읽기만 한다.
                        // 나흘 × 여섯 조를 본문에 늘 펼쳐 두면 다 고른 사람에게는 스크롤을 먹는
                        // 격자일 뿐이라, 정해 둔 값은 히어로 `MY ENTRY` 줄이 답하고 고치는 자리만
                        // 여기 둔다. 조 편성이 공개되기 전에는 버튼부터 서지 않는다.
                        //
                        // 글자를 쓰는 이유는 아이콘 하나로 "내 입장권"이 안 읽히기 때문이다. 면·테두리·
                        // 높이는 헤더 원형 버튼([GlgCircleIconButton])과 같은 값이라 같은 줄에서 따로
                        // 놀지 않는다(헤더 면 [glgHeaderSurface] — 흰 바탕 · 테두리 1 · 들뜸 그림자 · [GlgHeaderElementHeight], 10/15).
                        // 날짜를 고른 뒤에는 테두리만 강조색 55% 1.5 로 바꾼다 — 골랐다는 사실을 색이 싣는다.
                        // 종료 · 일정 미정이면 고를 날이 없다 — 버튼째 걷는다.
                        val entryDays = e.entryDayCount(entry)   // 기간 안 날짜만
                        if (e.hasEntryGroups && !e.phase().isOffSeason) {
                            val goingLabel = if (entryDays == 0) "내 입장권" else "${entryDays}일"
                            Row(
                                Modifier
                                    .height(GlgHeaderElementHeight)
                                    .glgHeaderSurface()
                                    .then(
                                        if (entryDays == 0) Modifier
                                        else Modifier.border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                                    )
                                    .clickable { entrySheetOpen = true }
                                    .padding(horizontal = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    com.gatcha.log.ui.components.GlgLineIcons.Ticket,
                                    contentDescription = null,
                                    tint = if (entryDays == 0) TextPrimary else accentDeep,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    goingLabel,
                                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                                    color = if (entryDays == 0) TextPrimary else accentDeep,
                                )
                            }
                        }
                    },
                ) {
                    HoyolandDetailContent(
                        onOpenSub = goSub, entry = entry, cart = cart,
                        // 지금 회차도 페이지가 들고 있는 값을 넘긴다(27.52.0) — 본문이 따로 읽으면 당겨서 새로고침이 본문에 닿지 않는다.
                        event = e, archived = readOnly,
                        // 지난 회차 안에서 또 지난 회차로 들어가지는 않는다 — 한 단계만.
                        onOpenArchive = if (readOnly) null else { key -> archiveKey = key; goSub(HoyolandSub.Archive) },
                    )
                }
        }
    }

    // 내 입장권 시트 — `AnimatedContent` 바깥이라 하위 페이지 전환에 휩쓸리지 않는다.
    if (entrySheetOpen) {
        HoyolandEntrySheet(
            e = e,
            entry = entry,
            onPick = { ymd, group -> viewModel.setEntryGroup(ymd, group) },
            onDismiss = { entrySheetOpen = false },
        )
    }
}

/**
 * 지난 회차 상세 — 보관본을 받아 [HoyolandDetailPage] 를 읽기 전용으로 그린다.
 *
 * 보관본은 번들에 없다(회차 하나가 수십 KB) — 받는 동안과 못 받았을 때의 자리를 여기서 맡는다.
 * 한 번 받으면 [HoyolandApi] 가 들고 있어 다시 열 때는 바로 선다.
 */
@Composable
private fun HoyolandArchivePage(viewModel: SpendingViewModel, key: String, onBack: () -> Unit) {
    var event by remember(key) { mutableStateOf<HoyolandEvent?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, attempt) {
        failed = false
        val loaded = HoyolandApi.loadArchive(key)
        if (loaded != null) event = loaded else failed = true
    }
    val loaded = event
    if (loaded != null) {
        HoyolandDetailPage(viewModel, onBack, archive = loaded)
        return
    }
    SectionPage("호요랜드 $key", onBack = onBack, flat = true) {
        // 받는 중 · 실패 — 클리어 편성의 실패 안내와 같은 모양(글 13 + 다시 시도).
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (failed) "불러오지 못했어요" else "불러오는 중이에요",
                fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center,
            )
            if (failed) {
                Spacer(Modifier.height(10.dp))
                com.gatcha.log.ui.components.GldsButton(
                    "다시 시도", onClick = { attempt++ },
                    variant = com.gatcha.log.ui.components.GldsVariant.Secondary, size = com.gatcha.log.ui.components.GldsSize.S,
                )
            }
        }
    }
}

/** 호요랜드 상세의 하위 페이지 — 진입 카드로 연다. [Archive] 는 지난 회차 상세(「지난 행사 ▸ 상세 보기」). */
enum class HoyolandSub { None, Stage, Goods, Cart, Booth, Food, Map, Archive }

@Composable
fun HoyolandDetailContent(
    onOpenSub: (HoyolandSub) -> Unit = {},
    /** 내 입장권 — 히어로의 `MY PASS` 줄이 이 값을 읽는다. 고르는 자리는 헤더 시트다. */
    entry: HoyolandEntry = HoyolandEntry(),
    /** 굿즈 장바구니 — 「현장에서」 굿즈 칸이 담은 종수·금액을 답한다. */
    cart: HoyolandCart = HoyolandCart(),
    /** 그릴 회차 — 페이지가 들고 있는 값(지금 회차 · 지난 회차 보관본). null 이면 지금 회차를 여기서 읽는다. */
    event: HoyolandEvent? = null,
    /** 지난 회차(보관본)인가 — 자료가 있는 칸만 세우고 「지난 행사」를 뺀다. [event] 로 지금 회차도 넘기게 되어 따로 받는다. */
    archived: Boolean = false,
    /** 「지난 행사 ▸ 상세 보기」 — 회차 키를 넘긴다. null 이면 진입 줄을 세우지 않는다(지난 회차 화면 안). */
    onOpenArchive: ((String) -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    val e = event ?: rememberHoyolandEvent()
    // 보관본이 있는 지난 회차만 「상세 보기」가 선다. 목록은 원격에서 다시 읽고(받기 전엔 내장값), 받으면 다시 그린다.
    var archiveTick by remember { mutableIntStateOf(0) }
    if (onOpenArchive != null) LaunchedEffect(Unit) { HoyolandApi.loadArchiveIndex(); archiveTick++ }
    var pastExpanded by remember { mutableStateOf(false) }
    val pastArrow by animateFloatAsState(if (pastExpanded) 180f else 0f, glgStandardSpec(), label = "pastArrow")

    val phase = e.phase()
    val accentDeep = LocalAccentDeep.current
    // 예매 안내 전문 — 열 줄이 넘어 카드에 펼치지 않고 시트로 연다.
    var ticketNoteOpen by remember { mutableStateOf(false) }

    // ── 히어로 — 명세 §4 · §6. 구현은 `hoyoland/HoyolandHero.kt` 로 분리했다.
    // 행사명 · 남은 날짜 · 기간 · 장소 · 대표 이미지 · 예매가 거기 모여 있다.
    HoyolandHero(
        e = e,
        entry = entry,
        onTicket = {
            openExternalLink(ctx, e.ticket.url, preferPackage = e.ticket.appPackage.ifBlank { null })
        },
        onMap = { openExternalLink(ctx, e.mapUrl, e.mapFallbackUrl) },
        onOfficial = { openExternalLink(ctx, e.officialUrl) },
        onStage = { onOpenSub(HoyolandSub.Stage) },
    )

    // ── 참여 게임 — 명세 §4 LINEUP. 히어로 안 구분선 아래 부록처럼 붙어 있던 것을
    // 독립 섹션으로 뺐다. "어느 게임이 오나" 는 이 행사를 볼지 말지를 가르는 정보다.
    // 비었는지는 아래 섹션 목록이 거른다(10/1) — 빈 섹션에 띠만 남지 않게.
    val lineupSection: @Composable () -> Unit = {
        HoyolandLineupSection(e) { url -> openExternalLink(ctx, url) }
    }

    // ── 예매 — **이 페이지에서 유일하게 안 정해진 항목**이라 단독 카드로 세운다.
    // 다른 정보와 같은 목록에 섞어 두면 "미정" 한 줄이 확정 정보들 사이에 묻힌다.
    val ticketSection: @Composable () -> Unit = {
    Text("예매", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    // 감싸던 카드를 걷었다(10/1) — 섹션 자체가 화면 폭 흰 면이다.
        Column(Modifier.fillMaxWidth()) {
            // 미정일 때 강조색을 쓰면 정해진 것처럼 보인다 — 회색으로 낮춘다.
            val tc = if (e.ticket.isUndecided) TextSecondary else accent
            // ── 머리 한 줄 — **상태 · 예매처 · 결제 금액.**
            //
            // 셋을 한 줄에 세우는 이유: 예매 버튼이 히어로로 올라간 뒤 이 카드가 답할 것은
            // "얼마를 내는가" 하나로 좁아졌다. 아이콘 박스와 큰 오픈 일시가 그 답보다 컸다.
            //
            // config 가격 값은 "29,000원 · 수수료 1,000원 (결제 30,000원)" 한 줄이다. 괄호 안이
            // 실제로 내는 돈이라 그쪽을 크게 올리고 내역은 작게 내린다(괄호가 없어도 줄 전체가 머리로 간다).
            val paid = e.ticket.priceLabel.substringAfter('(', "").substringBefore(')').trim()
            val breakdown = e.ticket.priceLabel.substringBefore('(').trim()
            // 배지는 **단계가 먼저**다. 폐막한 행사에 "판매 중" 이 남아 있으면 그게 곧 오보인데,
            // config 의 예매 상태는 어드민이 손으로 바꿔야 해서 늘 늦는다(2026-09-16 지적).
            val ticketLabel = when {
                phase == HoyolandPhase.ENDED -> "종료"
                phase.isEventLive -> "진행 중"
                else -> e.ticket.statusLabel
            }
            val ticketTone = if (phase.isEventLive || phase == HoyolandPhase.ENDED) TextSecondary else tc
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HoyolandInfoBadge(ticketLabel, ticketTone)
                if (e.ticket.vendor.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    // 예매처는 배지 옆 부제 — 라벨 줄 하나를 쓰기엔 값이 한 낱말이다.
                    HoyolandInfoBadge(e.ticket.vendor, TextSecondary)
                }
                if (e.ticket.priceLabel.isNotBlank()) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        paid.ifBlank { breakdown },
                        fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        // 더 이상 살 수 없는 값은 먹색을 내린다 — 읽는 값이지 누를 값이 아니다.
                        color = if (ticketTone == TextSecondary) TextSecondary else TextPrimary,
                        lineHeight = 23.sp,
                    )
                }
            }
            if (paid.isNotBlank() && breakdown.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    breakdown,
                    fontSize = 12.sp, color = TextSecondary,   // 11 → 12(10/1)
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            // ── 언제까지 파는가 — 안내문 첫 줄이 늘 이 말이라 그 한 줄만 꺼내 둔다.
            // 나머지(고르는 순서 · 조별 시각)는 아래 「전체 보기」와 헤더 「내 입장권」이 맡는다.
            val firstNoteLine = e.ticket.note.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
            if (firstNoteLine.isNotBlank() || e.ticket.openLabel.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(HoyolandHairline))
                Spacer(Modifier.height(13.dp))
                Text(
                    firstNoteLine.ifBlank { "${e.ticket.openLabel} 오픈" },
                    fontSize = 12.5.sp, color = TextSecondary, lineHeight = 19.sp,
                )
            }
            // ── 전체 보기 — 고르는 순서·조별 시각은 열 줄이 넘어 카드에 펼치면 이 카드가
            // 페이지에서 제일 큰 덩이가 된다. 읽을 사람만 시트로 연다([HoyolandGuideSheet] 규칙).
            if (e.ticket.note.isNotBlank()) {
                Spacer(Modifier.height(11.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { ticketNoteOpen = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("예매 안내 전체 보기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentDeep)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = accentDeep,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            // 예매 버튼은 히어로 하나로 모았다(명세 §6 · §7) — 여기와 히어로에 같은 버튼이
            // 두 번 서면 화면 한 장 안에서 같은 걸 두 번 권하는 셈이라 중복으로 읽힌다.
        }
    }

    // ── 현장에서 — 시간표 · 굿즈 목록 · 부스 체험 · 푸드존.
    //
    // 넷 다 본문에 펼치면 이 페이지의 본론(언제·어디서)이 스크롤 저 아래로 밀린다.
    // 시간표만 **전체 폭**을 주는 이유: 나머지와 달리 "지금 무대에서 무엇을 하는가"는
    // 이 페이지가 답해야 하는 질문에 가장 가깝다. 카드 한 줄이 그 답을 미리 말한다.
    //
    // 제목을 붙이는 이유: 다른 섹션은 전부 [여백 20 + 제목 + 10] 인데 여기만 제목이 없어
    // **예매 카드에 딸린 것처럼** 보였다. 넷의 공통점이 "현장에서 쓰는 것" 이라 그렇게 부른다.
    val onsiteSection: @Composable () -> Unit = {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        // 「현장에서」 였던 자리 — 개막 전에도 보이는 섹션이라 행사 중에만 맞는 말이었다.
        // 넷의 공통점은 "이 행사에서 볼 수 있는 것" 이고, 미리 보든 실제로 돌든 같은 말이다.
        Text("둘러보기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        // 종료 · 일정 미정이면 올라올 개막이 없다 — 부제를 뺀다.
        if (!phase.isOffSeason) {
            Text(
                if (phase.isEventLive) "행사 중에는 여기가 먼저예요" else "개막하면 맨 위로 올라와요",
                fontSize = 12.5.sp, color = TextSecondary,   // 11.5 → 12.5(10/1)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    // **넷이 같은 크기의 네 칸.** 예전엔 시간표·푸드존만 전체 폭이고 굿즈·부스가 반 폭이라
    // 크기가 곧 중요도로 읽혔는데, 현장에서 넷 중 무엇을 먼저 여는지는 그날 그때마다 다르다.
    // 같은 칸으로 두면 한 화면에 넷이 다 들어와 고르는 눈이 위아래로 움직이지 않는다.
    // 한 줄에 선 두 칸은 **높이를 맞춘다**([IntrinsicSize.Min] + `fillMaxHeight`). 부제가 한 줄인
    // 칸과 두 줄인 칸이 나란히 서면 카드 아래가 서로 다른 자리에서 끝나 격자가 어긋나 보인다.
    // 높이를 맞춘 뒤 글자는 칸 안에서 **세로 가운데**에 둔다([HoyolandOnsiteTile]).
    //
    // 푸드존은 **프로그램 목록에서 빼내 여기로** 옮겼다. 성격이 "현장에서 골라 사는 것"이라
    // 굿즈·부스와 같은 줄이 맞다. 메뉴가 비면 칸을 세우지 않고 그 자리를 비워 격자를 지킨다.
    //
    // **지난 회차(보관본)는 자료가 있는 칸만 세운다**(10/6) — 끝난 행사에 「공개 전」 칸이 서면 틀린 말이다.
    // 지금 회차는 예전 그대로다(시간표 · 굿즈 · 부스는 비어도 「공개 전」으로 선다).
    val tiles = buildList {
        if (!archived || e.hasTimetable) add(HoyolandSub.Stage)
        if (!archived || e.visibleGoods.isNotEmpty()) add(HoyolandSub.Goods)
        if (!archived || e.booths.isNotEmpty()) add(HoyolandSub.Booth)
        if (e.foodPrograms.isNotEmpty()) add(HoyolandSub.Food)
    }
    tiles.chunked(2).forEachIndexed { r, row ->
        if (r > 0) Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { sub ->
                val m = Modifier.weight(1f).fillMaxHeight()
                when (sub) {
                    HoyolandSub.Stage -> HoyolandOnsiteTile(
                        Icons.Default.Schedule, "시간표", e.onsiteStageLine(), m,
                        // 지금 무대가 돌고 있으면 이 칸만 빨갛다 — 넷 중 **지금 열어야 하는 칸**이다.
                        subColor = if (e.isStageLiveNow()) LiveRed else null,
                    ) { onOpenSub(sub) }
                    HoyolandSub.Goods -> HoyolandOnsiteTile(Icons.Default.ShoppingBag, "굿즈", e.onsiteGoodsLine(cart), m) { onOpenSub(sub) }
                    HoyolandSub.Booth -> HoyolandOnsiteTile(Icons.Default.Storefront, "부스", e.onsiteBoothLine(), m) { onOpenSub(sub) }
                    else -> HoyolandOnsiteTile(Icons.Default.Restaurant, "푸드존", e.onsiteFoodLine(), m) { onOpenSub(sub) }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    // ── 맵스 — 배치도가 공개돼야 선다. 넷과 성격이 달라(고르는 게 아니라 **찾아가는** 것)
    // 한 줄을 통째로 준다 — 지도는 폭이 넓을수록 구역 이름이 안 잘린다.
    if (e.hasMap) {
        if (tiles.isNotEmpty()) Spacer(Modifier.height(8.dp))
        HoyolandOnsiteWideTile(Icons.Default.Map, "맵스", e.onsiteMapLine()) { onOpenSub(HoyolandSub.Map) }
    }
    }

    // ── 두 섹션의 순서는 **개막일에 뒤집힌다.**
    //
    // 개막 전에는 이 페이지를 여는 이유가 "표를 어떻게 사나" 하나뿐이라 예매가 위다.
    // 개막일 0시부터는 반대다 — 표는 이미 있고, 현장에서 꺼내 드는 건 시간표·굿즈·부스·푸드존
    // 이다. 그때도 예매가 위에 있으면 매번 지나쳐 스크롤해야 하는 덩이가 된다.
    // ([HoyolandEvent.phase] 가 기기 시간의 날짜로 판정하므로 10.2 00:00 에 그대로 바뀐다.)
    // ── 세 섹션의 순서는 **개막일에 통째로 뒤집힌다.**
    //
    // 개막 전에는 "어느 게임이 오나(라인업) → 뭘 볼 수 있나(현장에서) → 표는 어떻게 사나(예매)"
    // 순으로 읽는다. 개막하면 첫 질문이 사라진다 — 표는 이미 있고 라인업도 외웠고, 손에 들고
    // 다니며 여는 건 **현장에서** 하나뿐이라 히어로 바로 아래로 올라온다.
    //
    // 카드를 걷은 뒤(10/1)로는 섹션을 **목록으로 모아** 화면 폭 섹션 + 사이 띠로 그린다. 빈 섹션은 목록에
    // 들지 않아 띠만 남지 않는다. 히어로 바로 아래 첫 섹션 위에는 띠를 두지 않는다(머리판이 끝을 이미 긋는다).
    val sections = mutableListOf<@Composable () -> Unit>()
    if (phase.isEventLive) {
        sections += onsiteSection
        if (e.lineup.isNotEmpty()) sections += lineupSection
        // 예매 섹션은 **내린다.** 개막한 뒤 이 페이지를 여는 사람은 표를 이미 들고 있다.
        // 가격·오픈 일시는 지나간 값이고, 그걸 매번 지나쳐 스크롤하게 둘 이유가 없다.
        // (현장 발권을 받지 않는 행사라 "지금 사는 길" 도 없다.)
    } else {
        if (e.lineup.isNotEmpty()) sections += lineupSection
        // 일정 미정(TBA)은 채울 게 없으면 둘러보기째 뺀다(빈 「공개되면…」 칸 넷만 남는다).
        // 지난 회차(보관본)도 자료가 하나도 없으면 섹션째 뺀다.
        if ((phase != HoyolandPhase.TBA && !archived) || e.hasOnsiteContent) sections += onsiteSection
        // 종료 · 일정 미정이면 예매는 지나갔거나 아직 없다 — 「예매 안내 전체 보기」까지 섹션째 뺀다.
        if (!phase.isOffSeason) sections += ticketSection
    }

    // ── 입장 특전 — 웰컴 키트(10/6, 「응모 · 특전」 섹션에서 갈라 섰다). 표에 딸린 것이라 예매 바로 아래다.
    // 리딤코드 교환 기한이 따로 있어 마감을 배지로 눈에 띄게 둔다.
    //
    // 게임 배지는 [HoyolandEvent.programGame] 이 제목에서 가려낸다 — 웰컴 키트 넷이 나란히
    // 서기 때문에 색이 없으면 내 것을 찾으려고 매번 제목을 읽어야 한다.
    //
    // **헤어라인 목록**(10/1) — 글 위주로 반복되는 줄이라 카드 대신 구분선이 끊는 자리를 맡는다
    // (줄 위아래 14 로 덩이를 띄운다).
    @Composable
    fun programList(title: String, list: List<com.gatcha.log.data.HoyolandProgram>) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
        list.forEachIndexed { i, p ->
            if (i > 0) HoyolandHairlineDivider()
            // 지난 회차 화면은 줄 제목에 게임 이름이 이미 있어 배지를 세우지 않는다([HoyolandEvent.programGameTags]).
            val pg = if (e.programGameTags) e.programGame(p.title) else ""
            val pc = e.stageColor(pg).let { if (it == 0L) TextSecondary else it.toColor() }
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pg.isNotBlank()) {
                            Text(
                                e.stageLabel(pg),
                                fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = pc,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(pc.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    if (p.desc.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        // 웰컴 키트처럼 구성품을 줄바꿈으로 늘어놓는 값이 있어 줄간을 넉넉히 준다.
                        HoyolandListText(p.desc, fontSize = 13.sp, color = TextSecondary, lineHeight = 21.sp)
                    }
                    if (p.deadline.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        // 이미 지난 마감은 회색 — 강조색이면 아직 응모할 수 있는 것처럼 읽힌다.
                        HoyolandInfoBadge(p.deadline, if (e.isDeadlinePast(p.deadline)) TextSecondary else accent)
                    }
                }
        }
    }
    // 「프로그램」 섹션은 뺐다(10/6) — 무대 시간표와 같은 말을 되풀이했다. 남은 줄(전시존 · 게임별 구성)은
    // 문서에 그대로 있어 옛 빌드는 지금처럼 그린다.
    val perkSection: @Composable () -> Unit = { programList("입장 특전", e.perkPrograms) }
    if (e.perkPrograms.isNotEmpty()) sections += perkSection

    // ── 지난 행사 참고 — 실제 개최 이력(최신순). 다음 행사 규모 가늠용.
    // 지나간 정보라 기본은 접어 둔다 — 이 페이지의 본론은 위의 이번 회차 정보다.
    // **종료 · 일정 미정이면 접지 않는다**(10/6) — 본론이 비어 이 목록이 페이지에서 읽을 전부라,
    // 접기 · 펼치기는 한 번 더 누르게 할 뿐이다. 머리줄은 누를 수 없는 제목이 된다.
    // 공지 한 줄은 여기 끝에 붙이지 않는다 — 상단(히어로)에만 선다(10/6, [HoyolandEvent.topNotice]).
    val pastAlwaysOpen = phase.isOffSeason
    val pastSection: @Composable () -> Unit = {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (pastAlwaysOpen) Modifier else Modifier.clickable { pastExpanded = !pastExpanded })
            .padding(vertical = 2.dp),
    ) {
        Text("지난 행사", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (!pastAlwaysOpen) {
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
    }
    AnimatedVisibility(visible = pastAlwaysOpen || pastExpanded) {
        // 행사마다 카드였던 것을 헤어라인 목록으로(10/1).
        Column {
            Spacer(Modifier.height(2.dp))
            e.past.forEachIndexed { i, p ->
                if (i > 0) HoyolandHairlineDivider()
                val key = if (onOpenArchive == null) "" else remember(archiveTick, p.title) { HoyolandApi.archiveKeyOf(p) }
                HoyolandPastEventCard(p.title, p.facts, onOpen = if (key.isEmpty()) null else ({ onOpenArchive?.invoke(key) }))
            }
        }
    }
    }
    // 지난 회차 화면(보관본)에는 「지난 행사」를 세우지 않는다(10/6) — 그 목록에서 골라 들어온 화면이라
    // 같은 목록이 또 나오면 어디에 있는지 헷갈린다.
    if (!archived) sections += pastSection

    sections.forEachIndexed { i, section ->
        if (i > 0) GiBand()
        // 라인업은 줄의 지금 무대 면이 화면 끝까지 깔려야 해서 좌우 20 을 스스로 둔다.
        // 마지막 줄이 스스로 아래 여백을 가진 섹션은 그만큼 덜어 띠까지 눈에 20(10/1) — 라인업 줄 12 → 8,
        // 입장 특전 줄 14 → 6. 지난 행사(늘 맨 아래)는 20.
        HoyolandSectionBox(
            horizontal = if (section === lineupSection) 0.dp else 20.dp,
            bottom = when {
                i == sections.lastIndex -> 20.dp
                section === lineupSection -> 8.dp
                section === perkSection -> 6.dp
                else -> 20.dp
            },
        ) { section() }
    }

    if (ticketNoteOpen) {
        HoyolandGuideSheet(
            e.ticket.note,
            onDismiss = { ticketNoteOpen = false },
            title = "예매 안내",
            subtitle = listOf(e.ticket.vendor, e.ticket.openLabel).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }
}

/**
 * NOW LIVE 배지 색 — 게임색 위에서도 읽히는 단 하나의 고정색(앱의 '임박' 주황과 같은 계열).
 *
 * 라인업 목록(`hoyoland/HoyolandHero.kt`)도 같은 배지를 쓴다 — 한 화면에 LIVE 가 두 군데
 * 뜨는데 색이 다르면 다른 뜻으로 읽힌다.
 */
internal val LiveRed = Color(0xFFE8634A)

/**
 * 전체 폭 진입 카드 — 아이콘 + 제목 + 요약 + 셰브론.
 * 두 칸 카드보다 요약을 길게 쓸 수 있어, 들어가기 전에 볼 값이 있는지 알 수 있다.
 */
/**
 * 「현장에서」 한 칸 — 아이콘 · 제목 · 한 줄 요약.
 *
 * 넷이 같은 크기라 크기가 중요도로 읽히지 않는다. 세로로 쌓는 이유는 두 칸 폭(약 180dp)에
 * 가로로 늘어놓으면 요약이 한 낱말 만에 잘리기 때문이다 — 요약은 **들어가기 전에 볼 값이
 * 있는지** 알려 주는 줄이라 잘리면 칸이 제목만 남는다.
 */
@Composable
private fun HoyolandOnsiteTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    /** 부제 색 — null 이면 보조 글자색. 지금 볼 값이 있는 칸만 색으로 부른다. */
    subColor: Color? = null,
    onClick: () -> Unit,
) {
    val deep = LocalAccentDeep.current
    GlassCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clickable { onClick() }
                .padding(14.dp)
                .heightIn(min = 68.dp),
            // 칸 안에서 **왼쪽 · 세로 가운데**. 한 줄짜리 칸이 두 줄짜리 옆에서 위로 붙으면
            // 아이콘 높이가 칸마다 달라져 줄이 삐뚤어 보인다(맵스처럼 혼자 서는 칸은 높이가
            // 내용에 딱 맞아 가운데 정렬이 아무것도 바꾸지 않는다).
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = deep, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(9.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                fontSize = 11.5.sp,
                fontWeight = if (subColor != null) FontWeight.Bold else FontWeight.Normal,
                color = subColor ?: TextSecondary,
                maxLines = 2, lineHeight = 15.sp,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 한 줄을 통째로 쓰는 「둘러보기」 칸 — 지금은 맵스 하나.
 *
 * 네 칸짜리 격자([HoyolandOnsiteTile])와 달리 **가로 한 줄**로 눕히고 높이를 낮춘다. 폭이
 * 두 배인데 같은 세로 배치를 쓰면 아이콘 아래 글자 두 줄만 왼쪽에 몰리고 오른쪽 절반이 통째로
 * 비어, 칸 하나가 격자보다 크게 자리를 먹는다. 오른쪽 끝 쉐브론은 **이 줄이 어디로 간다**는
 * 표시다 — 네 칸은 격자 모양만으로 눌리는 게 읽히지만 한 줄짜리는 그 단서가 없다.
 */
@Composable
private fun HoyolandOnsiteWideTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    onClick: () -> Unit,
) {
    val deep = LocalAccentDeep.current
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                // 세로 16 — 네 칸(68dp)보다 확실히 낮으면서도(52dp) 한 줄짜리가 너무 납작해
                // 눌리는 면으로 안 읽히는 선은 넘지 않는 값이다.
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = deep, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                sub,
                fontSize = 11.5.sp, color = TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 호요랜드 정보 넛지 배지 — 앱 공용 [com.gatcha.log.ui.components.GlgBadge] 보다 **한 단계 세게** 쓴다.
 *
 * 이 페이지의 배지(D-day · 예매 상태 · 마감 · 메뉴 수)는 장식이 아니라 "지금 챙길 것" 이라 본문에 묻히면 안 된다
 * (2026-09-15 요청). 공용 배지를 고치면 앱 전체가 같이 굵어지므로 여기서만 따로 둔다. iOS `hoyoBadge` 와 같은 값.
 */
@Composable
internal fun HoyolandInfoBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        label,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, lineHeight = 14.sp,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * 줄 사이 헤어라인 — 카드를 걷은 페이지(10/1)의 공용 구분선 색. 마이페이지 · 지출 · 게임정보 첫 화면과 같은 값.
 * 라인업 목록(`hoyoland/HoyolandHero.kt`)도 같이 쓴다.
 */
internal val HoyolandHairline = Color(0xFFEEF0F2)

@Composable
internal fun HoyolandHairlineDivider() = GldsHairline()

/**
 * 카드 없는 섹션(10/1) — 화면 폭 · 좌우 20 · 위 22 · 아래 20. 섹션 사이는 [GiBand].
 * 게임정보 첫 화면의 `GiSection` 과 같은 규격이다(그건 private 이라 여기 한 벌 둔다).
 * 붙박이 탭 바로 아래 섹션은 탭이 아래 10 을 이미 두므로 [top] 을 줄인다.
 */
@Composable
internal fun HoyolandSectionBox(
    top: androidx.compose.ui.unit.Dp = 22.dp,
    horizontal: androidx.compose.ui.unit.Dp = 20.dp,
    /** 마지막 내용 끝 → 띠가 눈에 20 이 되게, 20 − (마지막 줄이 스스로 가진 아래 여백)(10/1). 맨 아래 섹션은 20. */
    bottom: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    GldsSection(top = top, bottom = bottom, horizontal = horizontal, content = content)
}

/**
 * 지난 행사 1건 — 제목 + "종료" 배지 + 팩트 목록. 카드를 걷고 헤어라인 목록의 한 줄이 됐다(10/1).
 * [onOpen] 이 있으면(보관본이 있는 회차) 오른쪽에 「상세 보기」가 선다 — 섹션 머리의 오른쪽 액션과 같은 규격(13 SemiBold 강조색).
 */
@Composable
private fun HoyolandPastEventCard(title: String, facts: List<HoyolandFact>, onOpen: (() -> Unit)? = null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                HoyolandInfoBadge("종료", TextSecondary)
                if (onOpen != null) {
                    Spacer(Modifier.weight(1f))
                    val accent = LocalAccent.current
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("상세 보기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            facts.forEachIndexed { i, f ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                HoyolandFactRow(f.label, f.value)
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

/**
 * 홈·일정 탭이 쓰는 노출 판정 + 요약 문구.
 *
 * 두 화면이 각자 `HoyolandApi.current` 를 읽고 각자 조건을 쓰면, 한쪽만 D-60 이고
 * 다른 쪽은 D-30 인 식으로 갈라진다 — 판정은 [HoyolandEvent.isFeatured] 하나로 모은다.
 */
object HoyolandFeature {
    /** 홈 카드 한 줄 요약 — "10.2(금) 개막 · 일산 킨텍스 제2전시장 7·8홀". */
    fun summaryLine(e: HoyolandEvent): String {
        val head = if (e.phase().isEventLive) "진행 중"
        else e.periodLabel.substringBefore(" ~ ").substringAfter('.').let { "$it 개막" }
        return "$head · ${e.venueShort}"
    }
}
