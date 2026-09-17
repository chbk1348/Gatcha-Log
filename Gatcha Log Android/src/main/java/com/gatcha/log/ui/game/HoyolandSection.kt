package com.gatcha.log.ui.game

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.gatcha.log.ui.theme.toColor
import androidx.compose.runtime.collectAsState
import com.gatcha.log.data.HoyolandCart
import com.gatcha.log.data.HoyolandCartLine
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.data.GameData
import com.gatcha.log.data.StageSlot
import com.gatcha.log.data.StageState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.gatcha.log.data.HoyolandBooth
import com.gatcha.log.data.HoyolandGoods
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import com.gatcha.log.data.HoyolandLineup
import com.gatcha.log.data.HoyolandPhase
import com.gatcha.log.data.HoyolandProgram
import com.gatcha.log.data.api.HoyolandApi
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.components.ChipIdleBorder
import com.gatcha.log.ui.components.ChipIdleText
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.openExternalLink
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.game.hoyoland.HoyolandEntrySheet
import com.gatcha.log.ui.game.hoyoland.HoyolandMapContent
import com.gatcha.log.ui.game.hoyoland.HoyolandHero
import com.gatcha.log.ui.game.hoyoland.HoyolandLineupSection
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.components.GlgSegmentedTabs
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
 */
@Composable
private fun rememberHoyolandRefresher(): Triple<HoyolandEvent, Boolean, () -> Unit> {
    var event by remember { mutableStateOf(HoyolandApi.current) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { event = HoyolandApi.load() }
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
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    val e = rememberHoyolandEvent()
    val phase = e.phase()
    val status = e.statusLabel()
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("호요랜드", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            "전체 보기", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen(HoyolandSub.None) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
    if (phase == HoyolandPhase.ENDED) {
        GlassCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(HoyolandSub.None) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Celebration, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("${e.edition} · 종료", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                Text("지난 행사 보기", fontSize = 12.sp, color = TextSecondary)
            }
        }
        return
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // ── 위 — 남은 날짜가 주인공이다. 배지 크기로 두면 D-60 이든 D-1 이든 똑같아 보인다.
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(HoyolandSub.None) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 홈 배너와 같은 톤 — 강조색을 슬레이트로 가라앉혀 흰 글자가 읽힌다.
                val tileTop = lerp(accent, Color(0xFF2E3440), 0.35f)
                val tileBottom = lerp(accent, Color(0xFF2E3440), 0.50f)
                Column(
                    Modifier.size(62.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(tileTop, tileBottom))),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (phase.isEventLive) "진행 중" else "개막까지",
                        fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f),
                    )
                    Text(
                        status, fontSize = if (status.length <= 4) 20.sp else 13.sp,
                        fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, lineHeight = 22.sp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.edition, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text("${e.periodLabel} · ${e.venueShort}", fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                    if (e.lineup.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            e.lineup.take(3).forEach { l ->
                                val raw = e.stageColor(l.game)
                                val c = if (raw == 0L) TextSecondary else raw.toColor()
                                HoyolandMiniChip(e.stageLabel(l.game), c)
                            }
                            if (e.lineup.size > 3) HoyolandMiniChip("+${e.lineup.size - 3}", TextSecondary)
                        }
                    }
                }
            }
            // ── 정보 줄 — 줄마다 누르면 할 수 있는 일이 있다.
            HoyolandActionRow(Icons.Outlined.ConfirmationNumber, "예매", hoyolandTicketSummary(e), accent) {
                // 예매 주소가 있으면 곧장 예매처로 — 상세 페이지의 「예매하기」와 같은 동작(앱이 깔려 있으면 앱 먼저).
                // 주소가 아직 없으면(예매 미정) 상세로 간다.
                if (e.ticket.url.isNotBlank()) {
                    openExternalLink(ctx, e.ticket.url, preferPackage = e.ticket.appPackage.ifBlank { null })
                } else {
                    onOpen(HoyolandSub.None)
                }
            }
            if (e.mapUrl.isNotBlank()) {
                HoyolandActionRow(Icons.Outlined.Place, "장소", "${e.venueShort} · 지도", accent) {
                    openExternalLink(ctx, e.mapUrl)
                }
            }
            // 행사 중에만 — 지금 무대가 이 카드가 답할 첫 질문이 된다.
            if (phase.isEventLive && e.hasTimetable) {
                HoyolandActionRow(Icons.Outlined.PlayCircle, "무대", e.stageEntryLine(), Color(0xFFE5484D)) {
                    onOpen(HoyolandSub.Stage)
                }
            }
            // ── 바로가기 4칸 — 상세를 한 번 거치지 않고 곧장.
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val slots = e.days.sumOf { it.slots.size }
                HoyolandQuickTile(Icons.Outlined.CalendarMonth, "시간표", if (slots > 0) "${slots}편" else "공개 전", Modifier.weight(1f)) {
                    onOpen(HoyolandSub.Stage)
                }
                HoyolandQuickTile(Icons.Outlined.ShoppingBag, "굿즈", e.visibleGoods.size.let { if (it > 0) "${it}종" else "공개 전" }, Modifier.weight(1f)) {
                    onOpen(HoyolandSub.Goods)
                }
                HoyolandQuickTile(Icons.Outlined.Storefront, "부스", e.booths.size.let { if (it > 0) "${it}곳" else "공개 전" }, Modifier.weight(1f)) {
                    onOpen(HoyolandSub.Booth)
                }
                HoyolandQuickTile(Icons.Outlined.Restaurant, "푸드", e.foodPrograms.size.let { if (it > 0) "${it}곳" else "공개 전" }, Modifier.weight(1f)) {
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
        text, fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 정보 줄 — 아이콘 · 라벨 · 값 · 셰브론. 줄 전체가 누르는 자리다. */
@Composable
private fun HoyolandActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(DividerColor))
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 12.5.sp, color = TextSecondary, modifier = Modifier.width(34.dp))
            Text(
                value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextThird, modifier = Modifier.size(16.dp))
        }
    }
}

/** 바로가기 한 칸 — 아이콘 · 이름 · 규모(몇 편 · 몇 종). */
@Composable
private fun HoyolandQuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(CartRowBg).clickable { onClick() }.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(sub, fontSize = 10.sp, color = TextThird, maxLines = 1)
    }
}

/**
 * 호요랜드 상세 페이지.
 *
 * 구성 순서는 **지금 알아야 하는 것부터**다: 언제·어디서(히어로) → 어떻게 가나(예매) →
 * 뭘 보나(참여 게임·프로그램) → 곁다리(G-STAR) → 참고(지난 행사).
 * 예전에는 장소 카드가 맨 위였고 일정이 그 아래 따로 있어서, 가장 먼저 궁금한 날짜가 두 번째였다.
 */
/**
 * 호요랜드 상세 **페이지** — 헤더(뒤로가기 + 「G-STAR」 버튼)까지 여기서 소유한다.
 *
 * 예전엔 지스타가 이 페이지 본문 중간에 그냥 얹혀 있었다. 별개 행사인데다 참가사 명단이
 * 순차 공개돼 내용이 계속 자라는 자리라, 호요랜드를 보러 온 사람의 스크롤을 가로막았다.
 * 헤더 버튼으로 빼서 **볼 사람만** 들어가게 한다.
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
) {
    val (e, refreshing, refresh) = rememberHoyolandRefresher()
    val cart by viewModel.hoyolandCart.collectAsState()
    val entry by viewModel.hoyolandEntry.collectAsState()
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
    // 하위 페이지를 **상태 하나로** 모은다. 예전엔 지스타만 AnimatedContent 에 있고 굿즈·부스는
    // `if … return` 으로 컴포지션을 갈아끼워, 같은 페이지에서 나가는데 어떤 건 밀려 나가고
    // 어떤 건 0프레임으로 튀었다(홈 `HomeSub` 와 같은 이유로 하나로 합쳤다).
    var page by remember { mutableStateOf(initialPage) }
    // 상세 본문 스크롤 — **하위 페이지를 다녀와도 보던 자리에 남아야 한다.** 페이지가
    // `AnimatedContent` 로 갈아 끼워지므로 상태를 바깥에 둬야 살아남는다(안에서 만들면 매번
    // 새로 생겨 맨 위로 튄다 — 굿즈 한 번 보고 나올 때마다 다시 내려야 했다).
    val detailScroll = rememberScrollState()
    /** 하위 페이지로 **들어간다** — 전환 방향까지 같이 남긴다. */
    val goSub: (HoyolandSub) -> Unit = { navBack = false; page = it }
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
                    isRefreshing = refreshing,
                    onRefresh = refresh,
                    stickyTop = if (e.dayYmds.size > 1) {
                        { HoyolandDayTabs(e, stageDay) { stageDay = it } }
                    } else null,
                ) {
                    HoyolandTimetableSection(e, entry, stageDay)
                }
            HoyolandSub.Goods -> {
                SectionPage(
                    "굿즈 목록",
                    onBack = backFromSub,
                    bottomBar = { HoyolandGoodsBar(e, cart) { goSub(HoyolandSub.Cart) } },
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
                                // 다른 헤더 원형 버튼과 같은 규격 — 아웃라인 + 불투명 면.
                                Icons.Outlined.Info, "굿즈존 이용 안내", outlined = true, solidBackground = true,
                            ) { goodsGuideOpen = true }
                        }
                    },
                    lazyContent = {
                        hoyolandGoodsItems(
                            e, cart, goodsFilter,
                            onQuantity = { name, n -> viewModel.setGoodsQuantity(name, n) },
                            onImage = { goodsViewing = it },
                        )
                    },
                )
                goodsViewing?.let { v ->
                    HoyolandGoodsImageSheet(e, v, cart, { name, n -> viewModel.setGoodsQuantity(name, n) }) { goodsViewing = null }
                }
                if (goodsGuideOpen) HoyolandGuideSheet(e.goodsGuide, onDismiss = { goodsGuideOpen = false })
            }
            HoyolandSub.Cart ->
                SectionPage(
                    "장바구니",
                    onBack = { navBack = true; page = HoyolandSub.Goods },
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
                    HoyolandCartContent(e, cart) { name, n -> viewModel.setGoodsQuantity(name, n) }
                }
            HoyolandSub.Booth ->
                SectionPage("부스 체험", onBack = backFromSub) {
                    HoyolandBoothContent(e, boothFilter) { boothFilter = it }
                }
            HoyolandSub.Food ->
                SectionPage("푸드존", onBack = backFromSub) { HoyolandFoodContent(e) }
            HoyolandSub.Map ->
                SectionPage(e.map.title.ifBlank { "행사장 배치도" }, onBack = backFromSub) {
                    // 구역을 누르면 그 존의 목록으로 간다 — 지도가 목록의 입구가 된다.
                    HoyolandMapContent(e) { z ->
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
                                boothFilter = z.game.takeIf { it.isNotBlank() && it in e.boothGames }
                            }
                            if (target == HoyolandSub.Goods) {
                                goodsFilter = z.game.takeIf { it.isNotBlank() }
                            }
                            returnTo = HoyolandSub.Map
                            goSub(target)
                        }
                    }
                }
            HoyolandSub.None ->
                SectionPage(
                    "호요랜드",
                    onBack,
                    isRefreshing = refreshing,
                    onRefresh = refresh,
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
                        // 놀지 않는다(흰 배경 · 1.5dp 테두리 · 44dp).
                        if (e.hasEntryGroups) {
                            val goingLabel = if (entry.isEmpty) "내 입장권" else "${entry.dayCount}일"
                            Row(
                                Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.White)
                                    .border(
                                        1.5.dp,
                                        if (entry.isEmpty) Color.Black.copy(alpha = 0.12f) else accent.copy(alpha = 0.55f),
                                        RoundedCornerShape(22.dp),
                                    )
                                    .clickable { entrySheetOpen = true }
                                    .padding(horizontal = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = if (entry.isEmpty) TextSecondary else accentDeep,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    goingLabel,
                                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                                    color = if (entry.isEmpty) TextPrimary else accentDeep,
                                )
                            }
                        }
                    },
                ) {
                    HoyolandDetailContent(onOpenSub = goSub, entry = entry, cart = cart)
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

/** 호요랜드 상세의 하위 페이지 — 진입 카드로 연다. */
enum class HoyolandSub { None, Stage, Goods, Cart, Booth, Food, Map }

@Composable
fun HoyolandDetailContent(
    onOpenSub: (HoyolandSub) -> Unit = {},
    /** 내 입장권 — 히어로의 `MY PASS` 줄이 이 값을 읽는다. 고르는 자리는 헤더 시트다. */
    entry: HoyolandEntry = HoyolandEntry(),
    /** 굿즈 장바구니 — 「현장에서」 굿즈 칸이 담은 종수·금액을 답한다. */
    cart: HoyolandCart = HoyolandCart(),
) {
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    val e = rememberHoyolandEvent()
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
    val lineupSection: @Composable () -> Unit = {
        if (e.lineup.isNotEmpty()) {
            HoyolandLineupSection(e) { url -> openExternalLink(ctx, url) }
        }
    }

    // ── 예매 — **이 페이지에서 유일하게 안 정해진 항목**이라 단독 카드로 세운다.
    // 다른 정보와 같은 목록에 섞어 두면 "미정" 한 줄이 확정 정보들 사이에 묻힌다.
    val ticketSection: @Composable () -> Unit = {
    Text("예매", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
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
                    fontSize = 11.sp, color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            // ── 언제까지 파는가 — 안내문 첫 줄이 늘 이 말이라 그 한 줄만 꺼내 둔다.
            // 나머지(고르는 순서 · 조별 시각)는 아래 「전체 보기」와 헤더 「내 입장권」이 맡는다.
            val firstNoteLine = e.ticket.note.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
            if (firstNoteLine.isNotBlank() || e.ticket.openLabel.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
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
        Text("둘러보기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            if (phase.isEventLive) "행사 중에는 여기가 먼저예요" else "개막하면 맨 위로 올라와요",
            fontSize = 11.5.sp, color = TextSecondary,
        )
    }
    Spacer(Modifier.height(10.dp))
    // **넷이 같은 크기의 네 칸.** 예전엔 시간표·푸드존만 전체 폭이고 굿즈·부스가 반 폭이라
    // 크기가 곧 중요도로 읽혔는데, 현장에서 넷 중 무엇을 먼저 여는지는 그날 그때마다 다르다.
    // 같은 칸으로 두면 한 화면에 넷이 다 들어와 고르는 눈이 위아래로 움직이지 않는다.
    // 한 줄에 선 두 칸은 **높이를 맞춘다**([IntrinsicSize.Min] + `fillMaxHeight`). 부제가 한 줄인
    // 칸과 두 줄인 칸이 나란히 서면 카드 아래가 서로 다른 자리에서 끝나 격자가 어긋나 보인다.
    // 높이를 맞춘 뒤 글자는 칸 안에서 **세로 가운데**에 둔다([HoyolandOnsiteTile]).
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HoyolandOnsiteTile(
            Icons.Default.Schedule, "시간표", e.onsiteStageLine(),
            Modifier.weight(1f).fillMaxHeight(),
            // 지금 무대가 돌고 있으면 이 칸만 빨갛다 — 넷 중 **지금 열어야 하는 칸**이다.
            subColor = if (e.isStageLiveNow()) LiveRed else null,
        ) { onOpenSub(HoyolandSub.Stage) }
        HoyolandOnsiteTile(
            Icons.Default.ShoppingBag, "굿즈", e.onsiteGoodsLine(cart),
            Modifier.weight(1f).fillMaxHeight(),
        ) { onOpenSub(HoyolandSub.Goods) }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HoyolandOnsiteTile(
            Icons.Default.Storefront, "부스", e.onsiteBoothLine(),
            Modifier.weight(1f).fillMaxHeight(),
        ) { onOpenSub(HoyolandSub.Booth) }
        // 푸드존은 **프로그램 목록에서 빼내 여기로** 옮겼다. 성격이 "현장에서 골라 사는 것"이라
        // 굿즈·부스와 같은 줄이 맞다. 메뉴가 비면 빈 칸을 세워 넷의 격자를 지킨다.
        if (e.foodPrograms.isNotEmpty()) {
            HoyolandOnsiteTile(
                Icons.Default.Restaurant, "푸드존", e.onsiteFoodLine(),
                Modifier.weight(1f).fillMaxHeight(),
            ) { onOpenSub(HoyolandSub.Food) }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
    // ── 맵스 — 배치도가 공개돼야 선다. 넷과 성격이 달라(고르는 게 아니라 **찾아가는** 것)
    // 한 줄을 통째로 준다 — 지도는 폭이 넓을수록 구역 이름이 안 잘린다.
    if (e.hasMap) {
        Spacer(Modifier.height(8.dp))
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
    Spacer(Modifier.height(22.dp))
    if (phase.isEventLive) {
        onsiteSection()
        Spacer(Modifier.height(22.dp))
        lineupSection()
        // 예매 섹션은 **내린다.** 개막한 뒤 이 페이지를 여는 사람은 표를 이미 들고 있다.
        // 가격·오픈 일시는 지나간 값이고, 그걸 매번 지나쳐 스크롤하게 둘 이유가 없다.
        // (현장 발권을 받지 않는 행사라 "지금 사는 길" 도 없다.)
    } else {
        lineupSection()
        Spacer(Modifier.height(22.dp))
        onsiteSection()
        Spacer(Modifier.height(22.dp))
        ticketSection()
    }

    // ── 프로그램 — 본편과 별개로 **참여 마감이 따로 있는** 것들이라 날짜를 눈에 띄게 둔다.
    //
    // 한 장짜리 카드에 구분선으로 쌓다가 **항목당 카드**로 갈아탔다. 웰컴 키트가 들어오면서
    // 항목이 다섯으로 늘고 본문이 여러 줄이 되자, 구분선 하나로는 어디서 끊기는지 안 보여
    // 글자 벽이 됐다. 굿즈·부스가 이미 카드 목록이라 규격도 그쪽에 맞춘다.
    //
    // 게임 배지는 [HoyolandEvent.programGame] 이 제목에서 가려낸다 — 웰컴 키트 넷이 나란히
    // 서기 때문에 색이 없으면 내 것을 찾으려고 매번 제목을 읽어야 한다.
    if (e.otherPrograms.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        // 제목이 "프로그램" 이었을 때는 시간표·부스·푸드존까지 다 프로그램이라 위 「현장에서」와
        // 경계가 없었다. 푸드존이 빠져나간 지금 이 섹션에 남은 건 **미리 신청하거나(전시존)
        // 받는 것(웰컴 키트)** 뿐이라, 하는 일로 부른다.
        Text("응모 · 특전", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        e.otherPrograms.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            val pg = e.programGame(p.title)
            val pc = e.stageColor(pg).let { if (it == 0L) TextSecondary else it.toColor() }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
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
                        Text(p.desc, fontSize = 13.sp, color = TextSecondary, lineHeight = 21.sp)
                    }
                    if (p.deadline.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        HoyolandInfoBadge(p.deadline, accent)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))

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
 * 날짜 탭 한 칸의 높이 — **양 플랫폼 고정값**.
 *
 * 내용에 맡기면 Pretendard 의 큰 줄 상자 탓에 iOS 세그먼트 컨트롤(32pt)보다 두꺼워진다
 * ([GlgChip] 이 같은 이유로 폰트 패딩을 끈다). 바깥 높이를 못 박고 안을 가운데 정렬한다.
 */

/**
 * 일자별 시간표 — 날짜 탭 + 그날 프로그램.
 *
 * 기본 선택은 **행사 중이면 오늘**이다([HoyolandEvent.defaultDayIndex]). 현장에서 꺼냈을 때
 * 첫날이 선택돼 있으면 매번 한 번 더 눌러야 한다.
 */
@Composable
fun HoyolandTimetableSection(
    e: HoyolandEvent,
    entry: HoyolandEntry = HoyolandEntry(),
    /**
     * 고른 날짜 칸 — 날짜 탭이 [SectionPage] 의 붙박이 줄로 올라가 본문 바깥에 서므로,
     * 상태도 바깥이 든다(굿즈 게임 필터와 같은 이유).
     */
    selectedDay: Int = 0,
) {
    val accent = LocalAccent.current
    val ymds = e.dayYmds
    if (ymds.isEmpty()) return
    // 원격 갱신으로 기간이 줄면 인덱스가 범위를 벗어날 수 있다 — 첫날로 떨어뜨린다.
    val ymd = ymds.getOrElse(selectedDay) { ymds.first() }
    val stage = e.stageSlots(ymd)
    val games = e.stageGames(ymd)
    // 게임 필터 — 날짜를 바꾸면 푼다(그날 없는 게임이 걸린 채 빈 목록이 되지 않게).
    var gameFilter by remember(ymd) { mutableStateOf<String?>(null) }
    // 내 입장 시각(분) — 「내 입장권」에서 그날 조를 정했을 때만 0 보다 크다.
    val entryMin = e.entryMinutesOn(ymd, entry)
    val entryNote = e.entryStageNote(ymd, entry)

    // 제목 · 부제 · 날짜 탭은 여기 없다 — 제목은 헤더가, 탭은 붙박이 줄([HoyolandDayTabs])이
    // 맡는다. 본문에 제목을 한 번 더 쓰던 때는 헤더와 같은 말이 화면에 두 번 서 있었다.
    // ── 그날 내 입장 시각 — 고른 날에만 선다.
    //
    // 목록을 흐리게 칠하기만 하면 "왜 흐린가" 를 화면이 답하지 않는다. 이 줄이 그 답이라
    // 흐린 줄과 같은 화면에 있어야 한다(다른 날 탭으로 옮기면 같이 사라진다).
    if (entryNote.isNotBlank()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ConfirmationNumber, null,
                tint = LocalAccentDeep.current, modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(entryNote, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = LocalAccentDeep.current)
        }
    }
    Spacer(Modifier.height(10.dp))

    if (stage.isEmpty()) {
        StageEmptyCard(e)
        return
    }

    // ── 라이브 카드 — 지금 무대에서 하는 것과 바로 다음.
    //
    // **필터에 걸리지 않는다.** 지금 무대에서 벌어지는 일은 내가 고른 게임과 상관없이 알아야
    // 한다(그래서 필터 칩도 이 카드 **아래**에 둔다 — 거는 대상이 목록뿐임이 눈에 보이게).
    val live = stage.firstOrNull { it.state == StageState.LIVE }
    val next = stage.firstOrNull { it.state == StageState.UPCOMING }
    if (live != null) {
        StageLiveCard(e, live, next)
        Spacer(Modifier.height(12.dp))
    }

    // ── 게임 필터 — 그날 무대에 오르는 게임만. 한 게임뿐이면 고를 것이 없으니 줄을 안 그린다.
    //
    // 배타 선택은 앱 전체가 세그먼트 탭 규격이다(날짜 탭·일정/주년과 같은 것). 칩을 나란히
    // 두면 서로 독립된 버튼처럼 보여 "이 중 하나가 지금 보고 있는 것"이 약하게 읽힌다.
    if (games.size > 1) {
        GlgSegmentedTabs(
            labels = listOf("전체") + games.map { e.stageLabel(it) },
            // 고른 칸이 **그 게임 색**으로 찬다 — 목록의 색 띠와 같은 색이라 규칙이 안 어긋난다.
            // '전체'는 게임색이 없다 — 앱 강조색을 쓴다(먹색으로 두면 이 칸만 딴 물건이 된다).
            selectedColors = listOf(accent) + games.map {
                e.stageColor(it).let { c -> if (c == 0L) TextSecondary else c.toColor() }
            },
            selected = games.indexOf(gameFilter) + 1,
            onSelect = { i -> gameFilter = if (i == 0) null else games.getOrNull(i - 1) },
        )
        Spacer(Modifier.height(10.dp))
    }

    val shown = stage.filter { gameFilter == null || it.slot.game == gameFilter }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            shown.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                StageRow(e, item, live?.slot === item.slot, item.isBeforeEntry(entryMin))
            }
        }
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

@Composable
private fun HoyolandSubEntryWide(
    title: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(sub, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 상세 하단 진입 카드 — 굿즈 목록·부스 체험 두 장을 나란히. */
@Composable
private fun HoyolandSubEntry(
    title: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    GlassCard(modifier = modifier.clickable { onClick() }) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(3.dp))
            Text(sub, fontSize = 11.sp, color = TextSecondary, maxLines = 2, lineHeight = 14.sp)
        }
    }
}

/**
 * 굿즈 목록 — 품목과 **가격**.
 *
 * 이 앱은 지출을 다루는 앱이라, 굿즈 목록의 본론은 "얼마 들고 가야 하나"다. 그래서
 * ① 맨 위에 가격대를 세우고 ② 행을 눌러 담으면 ③ 하단 고정 바가 합계를 계속 말한다.
 *
 * **싣는 굿즈는 앱이 다루는 세 게임 + 행사 공용뿐이다**([HoyolandEvent.visibleGoods]).
 * 목업: `Gatcha Log MD/design_hoyoland_goods_mockup.html` A 안 — 다만 목록은 한 장에 줄을
 * 쌓지 않고 **품목당 카드**로 낸다([HoyolandGoodsCard] 참고).
 */
/**
 * 시간표의 날짜 탭 — 헤더 밑에 붙박이로 선다([SectionPage] 의 `stickyTop`).
 *
 * 본문과 같이 밀려 올라가면 나흘짜리 편성을 훑는 동안 **지금 어느 날을 보고 있는지**도,
 * 다른 날로 옮기는 방법도 화면에서 사라진다(굿즈 게임 탭과 같은 이유).
 */
@Composable
fun HoyolandDayTabs(e: HoyolandEvent, selected: Int, onSelect: (Int) -> Unit) {
    val ymds = e.dayYmds
    if (ymds.isEmpty()) return
    Column {
        Spacer(Modifier.height(6.dp))
        GlgSegmentedTabs(
            labels = ymds.map { e.dayTabDate(it) },
            subLabels = ymds.map { e.dayTabWeekday(it) },
            selected = selected.coerceIn(0, ymds.lastIndex),
            onSelect = onSelect,
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** 굿즈 목록의 게임 탭 — 헤더 밑에 붙박이로 선다(SectionPage.stickyTop). */
@Composable
fun HoyolandGoodsTabs(e: HoyolandEvent, selected: String?, onSelect: (String?) -> Unit) {
    val accent = LocalAccent.current
    val games = e.goodsGames
    Column {
        Spacer(Modifier.height(6.dp))
        GlgSegmentedTabs(
            labels = listOf("전체") + games.map { e.stageLabel(it) },
            selectedColors = listOf(accent) + games.map {
                e.stageColor(it).let { c -> if (c == 0L) TextSecondary else c.toColor() }
            },
            selected = games.indexOf(selected) + 1,
            onSelect = { i -> onSelect(if (i == 0) null else games.getOrNull(i - 1)) },
        )
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * 굿즈 목록 — [SectionPage] 의 게으른 목록 모드에 얹는 항목들.
 *
 * 예전엔 Column 에 105장을 한꺼번에 쌓는 컴포저블이었다. 들어갈 때와 첫 스크롤에서 버벅여서
 * (2026-09-15 갤럭시) 화면에 보이는 카드만 만드는 게으른 목록으로 바꿨다.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.hoyolandGoodsItems(
    e: HoyolandEvent,
    cart: HoyolandCart,
    gameFilter: String?,
    onQuantity: (String, Int) -> Unit,
    onImage: (HoyolandGoods) -> Unit,
) {
    val all = e.visibleGoods

    if (all.isEmpty()) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("판매 목록은 아직 공개 전이에요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "품목과 가격이 나오면 이 자리에 채워져요.\n지난 행사는 개막 1~2주 전에 나왔어요.",
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp,
                    )
                }
            }
        }
        return
    }

    // ── 가격대 — 목록보다 먼저. 얼마를 들고 갈지가 첫 질문이다.
    item(key = "priceRange") {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("가격대", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        e.goodsPriceRange().substringBefore(" · "),
                        fontSize = 16.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    )
                }
                Text(
                    e.goodsPriceRange().substringAfter(" · ", ""),
                    fontSize = 11.sp, color = TextThird,
                )
            }
        }
    }

    // 게임 탭은 여기 없다 — SectionPage 의 stickyTop 으로 올라가 헤더 밑에 붙박이로 선다
    // ([HoyolandGoodsTabs]). 100줄짜리 목록에서 같이 밀려 올라가면 안 되는 값이라서다.
    val shown = all.filter { gameFilter == null || it.game == gameFilter }
    // 키는 게임 + 이름 — 장패드처럼 두 IP 에 같은 이름이 있다.
    items(shown, key = { "${it.game}|${it.name}" }) { item ->
        Column {
            Spacer(Modifier.height(10.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                HoyolandGoodsCard(
                    e, item, cart.quantityOf(item.name), onQuantity,
                    onImage = if (item.imageUrl.isNotBlank()) ({ onImage(item) }) else null,
                )
            }
        }
    }
    // 하단 고정 바에 가리지 않게 비워 둔다. 이 바는 콘텐츠를 밀지 않고 **위에 겹치므로**
    // (SectionPage 가 Box.align(BottomCenter) 로 얹는다) 여기서 비운 만큼만 안전해진다.
    //
    // **제스처 바 높이는 여기서 더하지 않는다** — 게으른 목록의 아래 여백이 이미 [24dp + 제스처 바] 다.
    // 여기엔 바 몸통(위 12 + 알약 ≈ 46 + 아래 8 = 66)에서 그 24 를 뺀 42 에, 바와 마지막 카드 사이 12 를 더한다.
    // (예전엔 84 + 제스처 바를 또 더해 바 위가 한 뼘 넘게 비었다 — 3버튼 내비 기기에서 특히 컸다)
    // **담은 게 없으면 바도 없다** — 그땐 더 비우지 않는다.
    item(key = "bottomSpace") {
        Spacer(Modifier.height(if (cart.isEmpty) 0.dp else 54.dp))
    }
}

/**
 * 굿즈 한 장 — [썸네일 48 · 이름·갈래 · 가격/수량] + 구매 제한 띠.
 *
 * 한 장짜리 카드에 줄을 divider 로 쌓다가 **품목당 카드**로 갈아탔다. 줄 목록은 훑기엔 좋지만
 * 구매 제한("1인 5개 한정")을 놓을 자리가 없다 — 갈래 옆 회색 줄에 묻으면 현장에서 못 보고
 * 계산대에서 되돌아온다. 카드 아래를 띠 한 줄로 비워 그 조건만 세운다.
 *
 * 수량은 **목록에서 바로** 정한다 — 같은 키링을 두 개 사는 일이 흔한데 담기 토글만 있으면
 * 장바구니까지 들어가야 했다. 담기 전에는 「담기」 버튼, 담은 뒤에는 스테퍼로 바뀐다.
 */
@Composable
private fun HoyolandGoodsCard(
    e: HoyolandEvent,
    item: HoyolandGoods,
    quantity: Int,
    onQuantity: (String, Int) -> Unit,
    /** 사진을 크게 보기. null 이면 사진이 없는 품목 — 칸은 게임 자리표시로 그린다. */
    onImage: (() -> Unit)? = null,
) = Column {
    val accent = LocalAccent.current
    val raw = e.stageColor(item.game)
    val c = if (raw == 0L) TextSecondary else raw.toColor()
    val label = if (item.game.isBlank()) "공용" else e.stageLabel(item.game)
    // 가운데 정렬이다. 가격이 왼쪽으로 내려가면서 오른쪽에는 담기 버튼 하나만 남았으므로,
    // 위로 붙이면 왼쪽 덩이(이름+가격+비고)보다 훨씬 짧은 버튼이 카드 꼭대기에 홀로 뜬다.
    // (가격이 오른쪽에 있던 동안에는 위 정렬이 맞았다 — 그때는 그 열도 세 줄이었다.)
    // 사진이 있는 카드는 **카드 어디를 눌러도** 크게 보기다(담기·스테퍼는 자기 클릭을 먼저 먹는다).
    val hasImage = onImage != null
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (hasImage) Modifier.clickable { onImage?.invoke() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 썸네일 — 사진이 있으면 사진(흰 바탕 + 확대 표시), 없으면 게임 자리표시.
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (hasImage) Color.White else c.copy(alpha = 0.12f))
                .then(if (hasImage) Modifier.border(1.dp, DividerColor, RoundedCornerShape(12.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (hasImage) {
                coil.compose.AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    // 기본값(Low)은 늘리거나 줄여 그릴 때 계단이 진다 — 원본이 192~300px 이라 iOS 보다 거칠게 보였다.
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                )
                // 확대 표시 — 누르면 크게 볼 수 있다는 것만 알린다.
                // 글자(⤢)로 그렸더니 기기 글꼴에 그 기호가 없어 **세모로 깨져** 보였다(갤럭시). 아이콘으로 그린다.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x8C1A1C1E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
                }
            } else {
                Text(
                    label,
                    fontSize = 9.5.sp, fontWeight = FontWeight.Black,
                    color = c,
                    textAlign = TextAlign.Center, lineHeight = 11.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 18.sp)
            Spacer(Modifier.height(5.dp))
            // 가격은 **이름 바로 아래 왼쪽**이다. 예전엔 오른쪽 담기 버튼 위에 얹혀 있었는데,
            // 그 열은 버튼 폭(≈26dp 높이의 알약)에 갇혀 있어 값을 키울 자리가 없었고 버튼과
            // 시선을 나눠 가졌다. 이름 밑으로 내리면 폭 제약이 사라져 한 단계 더 키울 수 있고,
            // 값이 **그 이름의 값**이라는 것도 붙어 있어야 읽힌다.
            // 수량을 바꾸면 값이 **굴러간다**. 숫자가 툭 갈아 끼워지면 방금 내가 만든 변화인지
            // 원래 그랬는지 알기 어렵다 — 24,000 에서 48,000 으로 흘러가는 동안 눈이 그 변화를
            // 따라간다. 첫 합성에서는 애니메이션이 없다(animateIntAsState 가 목표값에서 시작한다)
            // — 스크롤로 카드가 들어올 때마다 0 부터 세면 목록 전체가 요동친다.
            //
            // 색도 같이 흐른다: 담는 순간 먹색 → 강조색. tnum 고정폭이라 굴러가는 동안에도
            // 자릿수가 흔들리지 않는다.
            val shownPrice by animateIntAsState(
                targetValue = if (quantity > 0) item.price * quantity else item.price,
                animationSpec = glgStandardSpec(),
                label = "goodsPrice",
            )
            val priceColor by animateColorAsState(
                targetValue = when {
                    item.price <= 0 -> TextThird
                    quantity > 0 -> accent
                    else -> TextPrimary
                },
                animationSpec = glgStandardSpec(),
                label = "goodsPriceColor",
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (item.price <= 0) "미정" else e.wonLabel(shownPrice),
                    fontSize = if (item.price > 0) 16.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    color = priceColor,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                )
                // 담은 뒤에만 단가×수량을 뒤에 받친다 — 소계가 어떻게 나온 값인지 보여준다.
                if (quantity > 0 && item.price > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${e.wonLabel(item.price)} × $quantity",
                        fontSize = 11.sp, color = TextThird,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    )
                }
            }
            // 게임 라벨은 왼쪽 48 칸이 이미 말하고 있다 — 여기 칩까지 두면 한 줄에 같은
            // 글자가 두 번 나온다. 갈래(분류)도 싣지 않는다 — '아크릴 스탠드' 처럼 이름과 거의
            // 같은 말이 한 줄 아래 또 나오고, 고를 때 실제로 쓰이는 값은 가격과 한정 여부다.
            // 시리즈·구매 제한은 아래 띠에 배지로 빠진다.
            if (item.noteRest.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                // 구성품이 긴 품목(테마 패키지)은 note 안에 줄바꿈이 들어 있어 두 줄이 된다.
                Text(item.noteRest, fontSize = 11.sp, color = c, lineHeight = 16.sp)
            }
        }
        // 오른쪽 열에는 **담기만** 남는다 — 가격이 이름 밑으로 내려가면서 이 열은 누르는
        // 것 하나만 갖는다. 값과 버튼이 좁은 한 열에서 시선을 나눠 갖던 것이 풀린다.
        Column(
            Modifier.padding(start = 11.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 「담기」 ↔ 스테퍼 전환. 값만 갈아 끼우면 버튼이 있던 자리에 스테퍼가 **툭 나타나서**
            // 내가 누른 것이 반영된 것인지, 원래 그랬던 것인지 순간 헷갈린다. 담을 때도 뺄 때도
            // 같은 전환을 태워 "이게 방금 내가 만든 변화" 라는 것을 보이게 한다.
            // 두 상태의 높이가 26dp 로 같아 전환 중에도 줄이 흔들리지 않는다.
            AnimatedContent(
                targetState = quantity > 0,
                transitionSpec = {
                    (fadeIn(glgStandardSpec()) + scaleIn(glgStandardSpec(), initialScale = 0.9f))
                        .togetherWith(fadeOut(glgShortSpec()) + scaleOut(glgShortSpec(), targetScale = 0.9f))
                },
                label = "goodsQuantity",
            ) { added ->
                if (!added) {
                    GoodsAddButton("담기") { onQuantity(item.name, 1) }
                } else {
                    Row(
                        Modifier.clip(RoundedCornerShape(9.dp)).border(1.dp, DividerColor, RoundedCornerShape(9.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GoodsStepButton("−") { onQuantity(item.name, quantity - 1) }
                        Text(
                            "$quantity",
                            fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                            textAlign = TextAlign.Center, modifier = Modifier.width(30.dp),
                        )
                        GoodsStepButton("+") { onQuantity(item.name, quantity + 1) }
                    }
                }
            }
        }
    }
    // 행사 한정 조건 띠 — 카드 폭을 꽉 채운 한 줄. 사기 전에 걸리는 값이라 '가격 미정' 안내와
    // 같은 경고색을 쓴다. 둘 다 없는 품목은 띠 자체를 세우지 않는다(전부 붙이면 눈이 거른다).
    //
    // '호요랜드2026 시리즈' 는 **이 행사에서만 파는 물건**이라는 뜻이라 배지로 뺀다. 상설 굿즈는
    // 다음에 사면 되지만 이건 놓치면 끝이고, 그 판단이 갈래 옆 회색 줄에 묻혀 있었다.
    if (item.limitLabel.isNotBlank() || item.seriesLabel.isNotBlank()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(WarnBg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.seriesLabel.isNotBlank()) {
                Text(
                    item.seriesLabel,
                    fontSize = 10.sp, fontWeight = FontWeight.Black, color = WarnText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(WarnText.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
                if (item.limitLabel.isNotBlank()) Spacer(Modifier.width(7.dp))
            }
            if (item.limitLabel.isNotBlank()) {
                Text(
                    item.limitLabel,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WarnText,
                )
            }
        }
    }
}

/**
 * 굿즈 크게 보기 — 바텀시트(목업 `design_hoyoland_goods_image_mockup.html` A안).
 *
 * 사진 + 이름·가격·배지 + 담기까지 한 장에서 끝낸다. 누른 품목 한 장만 보여 준다 — 좌우로 디자인
 * 형제를 넘기던 스와이프는 걷어냈다(2026-09-15 요청). 사진은 두 손가락으로 확대한다.
 * iOS `HoyolandGoodsImageSheet` 와 파리티.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HoyolandGoodsImageSheet(
    e: HoyolandEvent,
    item: HoyolandGoods,
    cart: HoyolandCart,
    onQuantity: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccent.current
    val raw = e.stageColor(item.game)
    val c = if (raw == 0L) TextSecondary else raw.toColor()
    val quantity = cart.quantityOf(item.name)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(18.dp)).background(CartRowBg),
            ) {
                HoyolandZoomableImage(item.imageUrl, item.name)
            }
            Spacer(Modifier.height(12.dp))
            HoyolandSheetBadge(if (item.game.isBlank()) "공용" else e.stageLabel(item.game), c)
            Spacer(Modifier.height(7.dp))
            Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (item.price > 0) e.wonLabel(item.price) else "가격 미정",
                fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            if (item.noteRest.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.noteRest, fontSize = 12.sp, color = c, lineHeight = 17.sp)
            }
            val badges = listOf(item.seriesLabel, item.limitLabel).filter { it.isNotBlank() }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach {
                        Text(
                            it, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WarnText,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(WarnBg)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.gatcha.log.ui.components.GlgOutlineButton("닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
                if (quantity <= 0) {
                    com.gatcha.log.ui.components.GlgButton(
                        "담기", onClick = { onQuantity(item.name, 1) }, modifier = Modifier.weight(1f),
                    )
                } else {
                    // 담은 뒤에는 스테퍼 — 목록 카드와 같은 동작을 시트에서도 한다.
                    Row(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(16.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(16.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable { onQuantity(item.name, quantity - 1) },
                            contentAlignment = Alignment.Center,
                        ) { Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary) }
                        Text(
                            "$quantity", fontSize = 15.sp, fontWeight = FontWeight.Black, color = accent,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        )
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable { onQuantity(item.name, quantity + 1) },
                            contentAlignment = Alignment.Center,
                        ) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary) }
                    }
                }
            }
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
 * 굿즈존 안내 본문 — **묶음 카드 + [항목 이름 | 값] 줄**.
 *
 * 처음엔 예매 안내용 [HoyolandRichText] 를 빌려 썼는데, 그 렌더러는 값이 가격 · 시각처럼 짧다고 보고
 * **값을 오른쪽에 한 줄로** 붙인다. 안내 문장이 값으로 들어오자 값이 폭을 먹어 항목 이름이 글자 단위로
 * 접혔고, 들여쓴 부연은 연회색이라 읽히지 않았다(2026-09-15 지적). 안내 전용으로 따로 둔다.
 *
 * 줄 규칙: 글머리 없는 줄 = 묶음 제목 · `· 이름 — 값` = 줄 · 들여쓴 줄 = 위 줄의 부연 · 빈 줄 = 묶음 사이.
 * iOS `HoyolandGuideContent` 와 파리티.
 */
@Composable
private fun HoyolandGuideContent(text: String) {
    val groups = remember(text) { parseHoyolandGuide(text) }
    val accent = LocalAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.forEach { g ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CartRowBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (g.title.isNotBlank()) {
                    Text(g.title, fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = accent)
                    Spacer(Modifier.height(8.dp))
                }
                g.rows.forEachIndexed { i, r ->
                    if (i > 0) Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            r.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                            lineHeight = 19.sp, modifier = Modifier.width(78.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            if (r.value.isNotBlank()) {
                                Text(r.value, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = TextPrimary, lineHeight = 19.sp)
                            }
                            r.subs.forEach {
                                Spacer(Modifier.height(2.dp))
                                Text(it, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HoyolandGuideRow(val label: String, val value: String, val subs: List<String>)
private data class HoyolandGuideGroup(val title: String, val rows: List<HoyolandGuideRow>)

private fun parseHoyolandGuide(text: String): List<HoyolandGuideGroup> {
    val groups = mutableListOf<HoyolandGuideGroup>()
    var title = ""
    val rows = mutableListOf<HoyolandGuideRow>()
    fun flush() {
        if (title.isNotBlank() || rows.isNotEmpty()) groups.add(HoyolandGuideGroup(title, rows.toList()))
        title = ""; rows.clear()
    }
    text.split("\n").forEach { raw ->
        val indented = raw.isNotBlank() && (raw.startsWith("  ") || raw.startsWith("\t"))
        val body = raw.trim()
        when {
            body.isEmpty() -> flush()
            indented && rows.isNotEmpty() -> {
                val last = rows.removeAt(rows.lastIndex)
                rows.add(last.copy(subs = last.subs + body.removePrefix("· ")))
            }
            body.startsWith("· ") -> {
                val item = body.removePrefix("· ")
                if (" — " in item) rows.add(HoyolandGuideRow(item.substringBefore(" — "), item.substringAfter(" — "), emptyList()))
                else rows.add(HoyolandGuideRow(item, "", emptyList()))
            }
            else -> { if (rows.isNotEmpty()) flush(); title = body }
        }
    }
    flush()
    return groups
}

/** 굿즈존 공통 이용 안내 — 굿즈 목록 헤더의 인포 버튼이 연다. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HoyolandGuideSheet(
    text: String,
    onDismiss: () -> Unit,
    title: String = "굿즈존 이용 안내",
    subtitle: String = "모든 게임 굿즈존 공통 · 공식 공지 기준",
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            // 본문만 스크롤한다 — 닫기는 **늘 아래에 보인다**(2026-09-15 요청). 안내가 길어 시트를 꽉 채우면
            // 닫기가 스크롤 끝에 숨어 끌어내리는 것 말고는 닫을 방법이 안 보였다.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(14.dp))
                HoyolandGuideContent(text)
                Spacer(Modifier.height(12.dp))
            }
            com.gatcha.log.ui.components.GlgOutlineButton(
                "닫기", onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp),
            )
        }
    }
}

/** 푸드 메뉴 사진 크게 보기 — 담기가 없는 [HoyolandGoodsImageSheet]. 음식은 장바구니에 담지 않는다. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HoyolandPhotoSheet(label: String, color: Color, title: String, price: String, url: String, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp)).background(CartRowBg)) {
                HoyolandZoomableImage(url, title)
            }
            Spacer(Modifier.height(12.dp))
            if (label.isNotBlank()) {
                HoyolandSheetBadge(label, color)
                Spacer(Modifier.height(7.dp))
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 24.sp)
            if (price.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(price, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
            }
            Spacer(Modifier.height(18.dp))
            com.gatcha.log.ui.components.GlgOutlineButton("닫기", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HoyolandSheetBadge(text: String, color: Color) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Black, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * 두 손가락 확대 — 떼면 제자리로 돌아온다.
 *
 * **두 손가락일 때만** 입력을 먹는다. 한 손가락 끌기까지 가져가면 시트의 페이저가 좌우로 넘어가지 않는다.
 */
@Composable
private fun HoyolandZoomableImage(url: String, desc: String) {
    val zoom = androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            zoom.floatValue = (zoom.floatValue * event.calculateZoom()).coerceIn(1f, 4f)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    zoom.floatValue = 1f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        coil.compose.AsyncImage(
            model = url,
            contentDescription = desc,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            // 시트에서는 원본(≈300px)을 3배 가까이 늘려 그린다 — 고품질 보간이 아니면 뭉개진다.
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            modifier = Modifier.fillMaxSize().padding(20.dp)
                .graphicsLayer { scaleX = zoom.floatValue; scaleY = zoom.floatValue },
        )
    }
}

/** 담기 버튼 — 스테퍼와 같은 높이라 담기 전후로 줄 높이가 흔들리지 않는다. */
@Composable
private fun GoodsAddButton(label: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, accent, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun GoodsStepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 28.dp, height = 26.dp)
            .background(CartRowBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

/**
 * 굿즈 목록 하단 고정 바 — 담은 종수·개수와 **합계**, 탭하면 장바구니.
 * 스크롤과 무관하게 늘 보여야 한다. 지금까지 고른 결과가 곧 이 화면의 답이다.
 */
@Composable
fun HoyolandGoodsBar(e: HoyolandEvent, cart: HoyolandCart, onOpenCart: () -> Unit) {
    if (cart.isEmpty) return
    // 알약 뒤에 흰 판을 깔지 않는다. 판이 있으면 화면 아래 한 뼘이 통째로 막힌 것처럼 보여
    // 목록이 거기서 끝난 줄 알게 된다 — 실제로는 계속 스크롤된다. 알약 자체가 불투명해
    // 합계는 그대로 읽히고, iOS 도 알약만 띄운다(SystemGlassBar).
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TextPrimary)
                .clickable { onOpenCart() }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "담은 ${cart.kindCount}종 · ${cart.totalCount}개",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.weight(1f))
            // 합계도 카드 가격과 **같이 굴러간다**. 카드만 흘러가고 여기만 툭 바뀌면 두 값이
            // 서로 다른 시점을 말하는 것처럼 보인다.
            val shownTotal by animateIntAsState(
                targetValue = e.cartTotal(cart),
                animationSpec = glgStandardSpec(),
                label = "cartTotal",
            )
            Text(
                e.wonLabel(shownTotal),
                fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.width(10.dp))
            Text("장바구니 ›", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

/**
 * 장바구니 — **게임별 묶음**.
 *
 * 현장에서는 게임 부스를 하나씩 돈다. 게임별로 묶고 소계를 붙이면 "원신 부스에서 얼마" 가
 * 보인다. 줄이 촘촘해 수량 스테퍼를 늘 띄우지 않고, **줄을 누르면 그 줄에서 펼친다.**
 *
 * 결제 버튼은 두지 않는다 — 현장 판매라 앱이 낄 자리가 없다. 여기서 하는 일은 예산 가늠이다.
 * 목업: `Gatcha Log MD/design_hoyoland_goods_mockup.html` D 안.
 */
@Composable
fun HoyolandCartContent(
    e: HoyolandEvent,
    cart: HoyolandCart,
    onQuantity: (String, Int) -> Unit,
) {
    val groups = e.cartGroups(cart)
    if (groups.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("담은 굿즈가 없어요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text(
                "굿즈 목록에서 사고 싶은 것을 담으면\n여기서 예상 지출을 볼 수 있어요.",
                fontSize = 12.sp, color = TextSecondary, lineHeight = 19.sp, textAlign = TextAlign.Center,
            )
        }
        return
    }

    // ── 합계 — 이 페이지의 답이라 맨 위에 둔다.
    val total = e.cartTotal(cart)
    val unpriced = e.cartUnpricedCount(cart)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TextPrimary)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("담은 굿즈", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.72f))
            Spacer(Modifier.weight(1f))
            Text(
                "${cart.kindCount}종 · ${cart.totalCount}개",
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
        }
        if (unpriced > 0) {
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("가격 미정", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.72f))
                Spacer(Modifier.weight(1f))
                Text("${unpriced}종", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(Modifier.height(11.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.18f)))
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("예상 지출", fontSize = 13.sp, color = Color.White.copy(alpha = 0.80f))
            Spacer(Modifier.weight(1f))
            Text(
                e.wonLabel(total),
                fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
        }
    }

    groups.forEach { g ->
        val c = e.stageColor(g.game).let { if (it == 0L) TextSecondary else it.toColor() }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (g.game.isBlank()) "공용" else e.stageLabel(g.game),
                fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(7.dp))
            Box(Modifier.weight(1f).height(1.dp).background(DividerColor))
            Spacer(Modifier.width(7.dp))
            // 게임별 소계가 **부스에서 꺼낼 금액**이다.
            Text(
                if (g.allUnpriced) "미정" else e.wonLabel(g.subtotal),
                fontSize = 11.5.sp, fontWeight = FontWeight.Black,
                color = if (g.allUnpriced) TextThird else TextSecondary,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
        }
        g.lines.forEach { line -> HoyolandCartRow(e, line, onQuantity) }
    }

    if (unpriced > 0) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WarnBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("⚠️", fontSize = 11.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                "가격 미정 ${unpriced}종은 합계에 없어요. 값이 공개되면 자동으로 더해져요.",
                fontSize = 11.sp, color = WarnText, lineHeight = 17.sp,
            )
        }
    }
}

/**
 * 장바구니 한 줄 — 접힌 기본 모습은 [이름 · ×수량 · 소계].
 * 누르면 그 줄에서 수량 스테퍼가 펼쳐진다(줄이 촘촘해 늘 띄우면 목록이 읽히지 않는다).
 */
@Composable
private fun HoyolandCartRow(e: HoyolandEvent, line: HoyolandCartLine, onQuantity: (String, Int) -> Unit) {
    var expanded by remember(line.goods.name) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CartRowBg)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                line.goods.name,
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                modifier = Modifier.weight(1f), lineHeight = 17.sp,
            )
            Text(
                "×${line.quantity}",
                fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextSecondary,
                modifier = Modifier.padding(end = 9.dp),
            )
            Text(
                if (line.goods.price > 0) e.wonLabel(line.subtotal) else "—",
                fontSize = 12.5.sp, fontWeight = FontWeight.Black,
                color = if (line.goods.price > 0) TextPrimary else TextThird,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CartStepButton("−") { onQuantity(line.goods.name, line.quantity - 1) }
                Text(
                    "${line.quantity}",
                    fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                    textAlign = TextAlign.Center, modifier = Modifier.width(40.dp),
                )
                CartStepButton("+") { onQuantity(line.goods.name, line.quantity + 1) }
                Spacer(Modifier.weight(1f))
                Text(
                    "빼기",
                    fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DangerText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onQuantity(line.goods.name, 0) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun CartStepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 30.dp, height = 26.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

/** 목업 색 — 장바구니 줄 바탕과 '가격 미정' 안내. */
private val CartRowBg = Color(0xFFF7F8FA)
private val WarnBg = Color(0xFFFFF6E0)
private val WarnText = Color(0xFF8A6A1E)
private val TextThird = Color(0xFF98A0AB)

/**
 * 보상 면 — **게임색이 아니라 한 가지 색으로 통일한다.** 부스 목록을 훑을 때 "받는 게 있는 곳" 이
 * 한눈에 걸려야 하는데, 게임색을 쓰면 그 줄이 게임 배지와 섞여 보상인지 소속인지 흐려진다.
 */
private val GiftText = Color(0xFFE0557B)
private val GiftBg = Color(0x14E0557B)

/**
 * 게임별 부스 체험.
 *
 * 무대와 달리 **시각이 없다** — 상시 운영이라 시간표에 얹을 것이 없다. 그래서
 * 시간표가 아니라 게임별 카드로 그린다.
 *
 * 예약제도 정원 · 회차도 다루지 않는다 — 공지된 정보를 그대로 보여줄 뿐이다. 그래서 카드에
 * 카드가 내는 값은 **참가비 · 보상 · 설명** 셋이고, 그중 **보상을 주인공으로 세운다**(목업 C안):
 * 예약도 정원도 없는 마당에 부스를 고르는 기준은 결국 받는 것이라서다.
 */
@Composable
fun HoyolandBoothContent(
    e: HoyolandEvent,
    /** 게임 필터 — 배치도에서 그 게임 부스를 눌러 들어오면 미리 걸려 있다. null 이면 전체. */
    gameFilter: String? = null,
    onGameFilter: (String?) -> Unit = {},
) {
    if (e.booths.isEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("부스 정보는 아직 공개 전이에요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    "게임별 체험존과 위치가 나오면 이 자리에 채워져요.\n부스 배치도는 보통 개막 직전에 나와요.",
                    fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp,
                )
            }
        }
        return
    }
    // 굿즈 목록과 같은 게임 탭 — 두 화면을 오갈 때 거르는 방법이 달라지면 손이 헷갈린다.
    val accent = LocalAccent.current
    val games = e.boothGames
    if (games.size > 1) {
        GlgSegmentedTabs(
            labels = listOf("전체") + games.map { e.stageLabel(it) },
            selectedColors = listOf(accent) + games.map {
                e.stageColor(it).let { c -> if (c == 0L) TextSecondary else c.toColor() }
            },
            selected = games.indexOf(gameFilter) + 1,
            onSelect = { i -> onGameFilter(if (i == 0) null else games.getOrNull(i - 1)) },
        )
        Spacer(Modifier.height(12.dp))
    }
    e.booths.filter { gameFilter == null || it.game == gameFilter }
        .forEachIndexed { i, b ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            HoyolandBoothCard(e, b)
        }
}

/**
 * 푸드존 — 게임별 메뉴판.
 *
 * 값은 프로그램 목록에 있던 것 그대로다([HoyolandEvent.foodPrograms] 가 제목으로 갈라낸다).
 * 어드민에서 이미 관리되고 있어 config 스키마도 입력 화면도 건드리지 않는다.
 *
 * 게임 탭은 달지 않는다 — 굿즈(100종)·부스(24곳)와 달리 카드가 게임당 하나라 목록 전체가
 * 세 장이다. 거를 것이 없는 자리에 탭을 세우면 화면 위 한 줄을 늘 먹는다.
 */
@Composable
fun HoyolandFoodContent(e: HoyolandEvent) {
    val list = e.foodPrograms
    if (list.isEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("메뉴는 아직 공개 전이에요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    "게임별 푸드존·푸드트럭 메뉴가 나오면 이 자리에 채워져요.",
                    fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp,
                )
            }
        }
        return
    }
    list.forEachIndexed { i, p ->
        if (i > 0) Spacer(Modifier.height(12.dp))
        HoyolandFoodCard(e, p)
    }
    Spacer(Modifier.height(14.dp))
    // 넛지 — 이 화면의 숫자는 **공지 기준**이라는 것만 분명히 한다. 현장 메뉴판과 다를 때
    // "앱이 틀렸다"가 아니라 "바뀌었구나"로 읽히게 하는 한 줄이다.
    Text(
        "가격·구성은 공식 공지 기준이에요. 현장 사정으로 바뀔 수 있어요.",
        fontSize = 11.sp, color = TextThird, lineHeight = 16.sp,
    )
}

/**
 * 푸드존 한 칸 — 게임 배지 + 유형(푸드존/푸드트럭) + 메뉴 수, 그리고 메뉴판.
 *
 * 제목("푸드트럭 — 붕괴: 스타레일")을 통째로 쓰지 않는다. 게임은 이미 배지로 서 있어
 * 같은 말이 두 번 나오고, 남는 폭이 그만큼 줄어든다 — 앞쪽 유형만 제목으로 쓴다.
 */
@Composable
private fun HoyolandFoodCard(e: HoyolandEvent, p: HoyolandProgram) {
    val game = e.programGame(p.title)
    val c = e.stageColor(game).let { if (it == 0L) TextSecondary else it.toColor() }
    // 메뉴 줄 세기 — 카드를 열기 전에 "몇 가지나 파나"가 보이게. 들여쓴 부연은 빼고 센다.
    val menuCount = p.desc.split("\n").count { it.startsWith("· ") && " — " in it }
    // 메뉴 사진 크게 보기 — (이름, 가격, 주소).
    val viewingFood = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Triple<String, String, String>?>(null) }
    viewingFood.value?.let { (name, price, url) ->
        HoyolandPhotoSheet(
            label = if (game.isBlank()) "" else e.stageLabel(game), color = c,
            title = name, price = price, url = url,
            onDismiss = { viewingFood.value = null },
        )
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game.isNotBlank()) {
                    Text(
                        e.stageLabel(game),
                        fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    p.title.substringBefore(" — "),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                if (menuCount > 0) HoyolandInfoBadge("${menuCount}종", TextSecondary)
            }
            val blocks = parseFoodBlocks(p.desc)
            blocks.forEachIndexed { bi, block ->
                Spacer(Modifier.height(12.dp))
                when (block) {
                    // 메뉴 **바로 앞**에 오는 문장은 그 메뉴를 파는 곳의 이름이다("오렐리아 아카데미
                    // 카페테리아" · "CuppaMoment"). 뒤에 오는 문장은 그 메뉴에 붙는 안내다
                    // ("코스 A·B 를 주문하면 …"). 같은 회색 문단으로 두면 한 카드 안에 카운터가
                    // 둘이라는 사실이 안 보여, 아래 메뉴가 어느 가게 것인지 흐려진다.
                    is FoodBlock.Para -> if (blocks.getOrNull(bi + 1) is FoodBlock.Menu) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(2.dp)).background(c))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                block.text,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                                lineHeight = 18.sp,
                            )
                        }
                    } else {
                        Text(block.text, fontSize = 12.5.sp, color = TextSecondary, lineHeight = 19.sp)
                    }
                    // 메뉴는 **면 위의 목록**으로 묶는다. 본문과 같은 바닥에 줄만 세우면
                    // 소제목·안내 문장과 경계가 없어 "어디까지가 파는 것인가"가 안 보였다.
                    is FoodBlock.Menu -> Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CartRowBg),
                    ) {
                        block.rows.forEachIndexed { i, row ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                            // 메뉴 사진 — 있으면 줄 왼쪽 52칸(누르면 크게 보기). 없으면 지금처럼 글만.
                            val photo = p.menuImageUrl(row.name)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (photo.isNotBlank()) {
                                coil.compose.AsyncImage(
                                    model = photo,
                                    contentDescription = row.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                    // 위아래 10 — 줄 여백이 글자 칸에만 있으면 사진이 줄 경계에 붙는다.
                                    modifier = Modifier
                                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp)
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                        .clickable { viewingFood.value = Triple(row.name, row.price, photo) },
                                )
                            }
                            Column(
                                Modifier.weight(1f).padding(
                                    start = if (photo.isNotBlank()) 12.dp else 13.dp, end = 13.dp,
                                    top = 11.dp, bottom = 11.dp,
                                ),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        row.name,
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                                        lineHeight = 18.sp, modifier = Modifier.weight(1f),
                                    )
                                    if (row.price.isNotBlank()) {
                                        Spacer(Modifier.width(10.dp))
                                        Text(row.price, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c)
                                    }
                                }
                                if (row.sub.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(row.sub, fontSize = 11.5.sp, color = TextThird, lineHeight = 16.sp)
                                }
                            }
                            }
                        }
                    }
                }
            }
            if (p.deadline.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HoyolandInfoBadge(p.deadline, c)
            }
        }
    }
}

/** 메뉴판 한 덩이 — 파는 것들의 목록이거나, 그 앞뒤의 문장(소제목·세트 안내)이다. */
private sealed interface FoodBlock {
    data class Menu(val rows: List<FoodRow>) : FoodBlock
    data class Para(val text: String) : FoodBlock
}

private data class FoodRow(val name: String, val price: String, val sub: String)

/**
 * 메뉴 설명글을 **파는 줄 / 읽는 줄**로 가른다 — [HoyolandRichText] 와 같은 줄 규칙을 쓴다.
 *
 * `· 이름 — 7,000원` 이 이어지는 구간만 하나의 목록으로 묶는다. 그 사이에 낀 문장
 * ("코스 A·B 를 주문하면 …")에서 목록이 끊기는 건 의도다 — 그 문장은 앞 목록에만 걸린다.
 */
private fun parseFoodBlocks(desc: String): List<FoodBlock> {
    val blocks = mutableListOf<FoodBlock>()
    val buffer = mutableListOf<FoodRow>()
    fun flush() {
        if (buffer.isNotEmpty()) {
            blocks.add(FoodBlock.Menu(buffer.toList()))
            buffer.clear()
        }
    }
    desc.split("\n").forEach { raw ->
        val indented = raw.isNotBlank() && (raw.startsWith("  ") || raw.startsWith("\t"))
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            // 들여쓴 줄은 **바로 위 메뉴의 부연**이다(리딤코드처럼 `·` 가 붙어 있어도 마찬가지).
            indented && buffer.isNotEmpty() -> {
                val last = buffer.removeAt(buffer.lastIndex)
                val sub = line.removePrefix("· ")
                buffer.add(last.copy(sub = if (last.sub.isBlank()) sub else "${last.sub} $sub"))
            }
            line.startsWith("· ") -> {
                val item = line.removePrefix("· ")
                val hasValue = " — " in item
                buffer.add(
                    FoodRow(
                        name = if (hasValue) item.substringBeforeLast(" — ") else item,
                        price = if (hasValue) item.substringAfterLast(" — ") else "",
                        sub = "",
                    ),
                )
            }
            else -> {
                flush()
                blocks.add(FoodBlock.Para(line))
            }
        }
    }
    flush()
    return blocks
}

@Composable
private fun HoyolandBoothCard(e: HoyolandEvent, b: HoyolandBooth) {
    val c = e.stageColor(b.game).let { if (it == 0L) TextSecondary else it.toColor() }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    e.stageLabel(b.game),
                    fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(b.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                // 참가비는 제목 줄 오른쪽. 유료 체험존은 회차마다 값이 다르고 무료 부스와 섞여
                // 있어서, 설명을 읽기 전에 먼저 갈려야 하는 값이다.
                //
                // **유료 쪽을 더 세게 칠한다.** 예전에는 무료가 분홍 알약이고 유료는 먹색이라,
                // 지출을 다루는 앱에서 정작 돈이 드는 칸이 덜 보였다. 유료는 게임색을 꽉 채우고
                // 흰 글자를 얹어 목록을 훑을 때 값부터 걸리게 하고, 무료는 테두리만 남겨 물러세운다.
                // 숫자는 tnum 으로 고정폭 — 자릿수가 달라도 카드마다 끝자리가 흔들리지 않는다.
                Spacer(Modifier.width(8.dp))
                if (b.isPaid) {
                    Text(
                        e.wonLabel(b.price),
                        fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(c)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                } else {
                    // 무료는 유료와 **같은 알약 규격**(폭·높이·굵기)을 쓰되 색을 뺀다. 테두리만
                    // 남겼더니 옆 카드의 채운 알약과 짝이 안 맞아 덜 만든 것처럼 보였다.
                    // 색을 '돈이 든다' 에만 쓰면 목록을 훑을 때 유채색 칸만 세면 된다.
                    // 분홍은 보상 띠가 가져간다 — 여기서 또 쓰면 카드가 온통 분홍이 된다.
                    Text(
                        "무료",
                        fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(TextThird.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            // 보상은 부스를 고르는 기준이라 카드의 **주인공 자리**를 준다 — 폭을 꽉 채운 한 면.
            if (b.reward.isNotBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(GiftBg)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Redeem, null, tint = GiftText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(b.reward, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = GiftText)
                }
            } else {
                // 빈칸으로 두면 **값이 빠진 것처럼** 읽힌다 — 없다고 적는다.
                Text(
                    "받는 것 없음",
                    fontSize = 12.sp, color = TextThird,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 11.dp),
                )
            }
            // 설명이 카드 아래 한 면을 통째로 쓴다. 예전엔 제목 밑 회색 한 줄이었고 이 자리에는
            // '구분'(무료/유료 체험존)이 있었는데, 무료·유료는 **우상단 배지가 이미 말한다** —
            // 같은 걸 두 번 적느라 정작 무엇을 하는 체험인지가 눌려 있었다. 자리를 맞바꾼다.
            if (b.desc.isNotBlank()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Text(
                    b.desc,
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                )
            }
        }
    }
}

/** 시간표가 아직 없는 날 — 빈 카드가 아니라 **언제 채워지는지**를 말한다. */
@Composable
private fun StageEmptyCard(e: HoyolandEvent) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (e.hasTimetable) "이 날 무대 편성은 아직이에요" else "무대 편성은 아직 공개 전이에요",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "공개되면 게임별 무대 순서와 시각이 이 자리에 채워져요.\n지난 행사는 개막 2~3주 전에 나왔어요.",
                fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp,
            )
        }
    }
}

/**
 * 지금 무대에서 — 배경색이 **그 무대의 게임색**을 입는다(공통 무대면 먹색).
 * 현장 화면의 본론이라 화면 위쪽 한 장을 통째로 준다.
 */
@Composable
private fun StageLiveCard(e: HoyolandEvent, live: StageSlot, next: StageSlot?) {
    val raw = e.stageColor(live.slot.game)
    val base = if (raw == 0L) Color(0xFF39204E) else raw.toColor()
    val top = lerp(base, Color.Black, 0.22f)
    val bottom = lerp(base, Color.White, 0.06f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(top, bottom))),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(150.dp)
                .offset(x = 40.dp, y = (-52).dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // NOW LIVE — 카드에서 가장 먼저 읽혀야 하는 한 마디다. 배경이 게임색이라
                // 흰 반투명 배지로는 묻힌다. **붉은 면**으로 채우고 점을 깜빡여 시선을 잡는다.
                val pulse = rememberInfiniteTransition(label = "livePulse")
                val dotAlpha by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
                    label = "liveDot",
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(LiveRed)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = dotAlpha)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "NOW LIVE",
                        fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White,
                        letterSpacing = 0.7.sp,
                    )
                }
                // 게임 배지는 **칸 반대쪽 끝**으로 — 두 배지가 붙어 있으면 어느 쪽이 무엇인지
                // 한 덩이로 뭉쳐 읽힌다. NOW LIVE 와 같은 크기로 양쪽 어깨를 맞춘다.
                Spacer(Modifier.weight(1f))
                if (live.slot.game.isNotBlank()) {
                    Text(
                        // 카드는 한 장뿐이고 폭도 넉넉하다 — 여기서는 온이름을 쓴다.
                        e.stageFullName(live.slot.game),
                        fontSize = 12.sp, fontWeight = FontWeight.Black, color = base,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White.copy(alpha = 0.92f))
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            // 무대명은 자르지 않는다 — 이 카드의 본론이고, 공연명은 길어야 두 줄이다.
            Text(
                live.slot.title,
                fontSize = 17.5.sp, fontWeight = FontWeight.Black, color = Color.White,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    live.rangeLabel.ifBlank { null },
                    if (live.remainMin > 0) "${live.remainMin}분 남음" else null,
                ).joinToString(" · "),
                fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.85f),
            )
            if (live.slot.cast.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "출연 · ${live.slot.cast}",
                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.92f),
                )
            }
            // 진행 막대 — 공연은 길이가 있다. 시작 시각만으로는 놓친 건지 아직인지 모른다.
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.24f))) {
                Box(
                    Modifier
                        .fillMaxWidth(live.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White),
                )
            }
            if (next != null) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.20f)))
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("다음", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.72f))
                    if (next.slot.game.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        val nc = e.stageColor(next.slot.game).let { if (it == 0L) Color(0xFF98A0AB) else it.toColor() }
                        Text(
                            e.stageLabel(next.slot.game),
                            fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(nc)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        next.slot.title,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(next.slot.time, fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/** 무대 한 줄 — 시각 + 게임 정사각 배지 + 제목·설명·출연. 지난 편은 흐리게. */
@Composable
private fun StageRow(
    e: HoyolandEvent,
    item: StageSlot,
    isLive: Boolean,
    /** 내가 들어가기 전에 끝나는 편 — 끝난 편과 같은 무게로 내린다([StageSlot.isBeforeEntry]). */
    beforeEntry: Boolean = false,
) {
    val raw = e.stageColor(item.slot.game)
    val c = if (raw == 0L) Color(0xFF98A0AB) else raw.toColor()
    Row(
        Modifier
            .fillMaxWidth()
            // 지나간 편과 **같은 값으로** 내린다. 둘은 "지금 내가 볼 수 없다" 는 같은 말이고,
            // 단계를 나누면 흐린 줄이 두 종류가 되어 무엇이 더 흐린지 세게 된다.
            .alpha(if (item.state == StageState.DONE || beforeEntry) 0.40f else 1f)
            .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
    ) {
        // 좌측 열 — 시각 **아래**에 게임 이름. 배지 상자에 넣으면 "스타레일"이 안 들어가
        // 두 자로 잘리는데, 그러면 무슨 게임인지가 오히려 흐려진다. 열을 세로로 쓰면
        // 이름을 온전히 쓸 수 있고 시각과 게임이 한 덩이로 읽힌다.
        // 좌측 열은 **자기 칸 정중앙**에 놓는다 — 시각·배지 폭이 게임마다 달라 왼쪽 정렬로 두면
        // 줄마다 들쭉날쭉해 보인다. 세로도 가운데라 오른쪽 본문과 무게가 맞는다.
        Column(
            Modifier.width(58.dp).align(Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 진행 중인 줄의 시각은 **먹색**으로 진하게 — 게임색으로 칠하면 바로 아래 배지와
            // 같은 색이 되어 둘이 한 덩이로 뭉치고, 색이 곧 게임이라는 규칙도 흐려진다.
            Text(
                item.slot.time,
                fontSize = 12.sp,
                fontWeight = if (isLive) FontWeight.Black else FontWeight.Bold,
                color = if (isLive) TextPrimary else TextSecondary,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.height(4.dp))
            // 게임은 **배지**로 — 시각과 같은 무게의 맨글자로 두면 둘이 한 덩이로 뭉쳐
            // "14:00 스타레일"이 한 줄처럼 읽힌다. 면을 깔아 둘을 갈라 둔다.
            Text(
                e.stageLabel(item.slot.game),
                fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c, maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.copy(alpha = 0.14f))
                    .padding(horizontal = 5.dp, vertical = 2.5.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        // 세로 구분선 — 시각·게임(언제·누구)과 공연 내용(무엇)을 가른다. 라이브 카드의
        // 큰 숫자 옆 구분선과 같은 규칙이다.
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(1.dp)
                .height(if (item.slot.cast.isBlank()) 30.dp else 42.dp)
                .background(DividerColor),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(top = 1.dp)) {
            Text(
                item.slot.title,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                lineHeight = 18.sp,
            )
            // 길이를 **설명 앞**에 둔다. 뒤에 붙이면 설명이 여러 줄일 때 "60분" 이 마지막 줄
            // 꼬리에 달라붙는데, 그 줄이 하필 "※ 주의…" 여서 주의 문구가 길이 표기에 먹혔다.
            val sub = listOfNotNull(
                if (item.slot.minutes > 0) "${item.slot.minutes}분" else null,
                item.slot.desc.ifBlank { null },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                // 설명에 줄바꿈이 들어 있다(조별 입장 시각 · ※ 주의) — 줄간을 준다.
                Text(sub, fontSize = 11.5.sp, color = TextSecondary, lineHeight = 17.sp)
            }
            // 출연자 — 무대를 고르는 기준이 공연명보다 출연자일 때가 많다(성우 무대가 특히).
            if (item.slot.cast.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "출연 · ${item.slot.cast}",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c,
                )
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
                HoyolandInfoBadge("종료", TextSecondary)
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

/**
 * 원격 config 의 **여러 줄 설명글**을 줄 단위로 읽어 그린다 — 푸드존 메뉴판과 예매 안내가 쓴다.
 *
 * 둘 다 `Text` 하나에 통째로 넣던 자리였다. 그러면 "· 행운의 황금 레몬 만두 — 7,000원" 이
 * 본문과 같은 무게로 깔려 **가격이 글 속에 묻히고**, 예매 안내의 1~4 순서도 문단처럼 읽혔다.
 * 값은 그대로 두고 표시만 나눈다(config 스키마를 늘리지 않는다 — 옛 빌드에서도 글자는 나온다).
 *
 * 줄 규칙 — 위에서부터 먼저 맞는 것:
 *  - 들여쓴 줄  → 바로 위 항목의 부연(작게·흐리게). 리딤코드 구성처럼 `· ` 가 붙어 있어도 부연이다.
 *  - `· …`      → 항목 줄. ` — ` 가 있으면 **뒤가 값**(가격·시각)이라 오른쪽에 붙여 강조한다.
 *  - `1. …`     → 순서 줄. 번호만 색을 준다.
 *  - 빈 줄      → 문단 사이 간격.
 *  - 그 외      → 문단(소제목 포함 — 짧은 줄은 어차피 한 줄로 선다).
 */
@Composable
private fun HoyolandRichText(text: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        var first = true
        text.split("\n").forEach { raw ->
            val line = raw.trimEnd()
            val indented = line.isNotBlank() && (line.startsWith("  ") || line.startsWith("\t"))
            val body = line.trim()
            if (body.isEmpty()) {
                // 빈 줄은 그 자체가 문단 구분이다 — 간격만 주고 다음 줄에 위 여백을 또 주지 않는다.
                Spacer(Modifier.height(10.dp))
                first = true
                return@forEach
            }
            if (!first) Spacer(Modifier.height(if (indented) 2.dp else 6.dp))
            first = false
            when {
                indented -> Text(
                    body,
                    fontSize = 11.5.sp, color = TextThird, lineHeight = 17.sp,
                    modifier = Modifier.padding(start = 12.dp),
                )
                body.startsWith("· ") -> {
                    val item = body.removePrefix("· ")
                    val hasValue = " — " in item
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("·", fontSize = 13.sp, color = TextThird)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (hasValue) item.substringBeforeLast(" — ") else item,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                            lineHeight = 19.sp, modifier = Modifier.weight(1f),
                        )
                        if (hasValue) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                item.substringAfterLast(" — "),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor,
                            )
                        }
                    }
                }
                StepRegex.matches(body) -> {
                    val no = body.substringBefore(". ")
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            no,
                            fontSize = 12.sp, fontWeight = FontWeight.Black, color = valueColor,
                            textAlign = TextAlign.Center, lineHeight = 19.sp,
                            modifier = Modifier.width(18.dp),
                        )
                        Text(
                            body.substringAfter(". "),
                            fontSize = 13.sp, color = TextPrimary, lineHeight = 19.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> Text(body, fontSize = 12.5.sp, color = TextSecondary, lineHeight = 19.sp)
            }
        }
    }
}

private val StepRegex = Regex("""^\d+\. .*""")

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
        val head = if (e.phase().isEventLive) "진행 중"
        else e.periodLabel.substringBefore(" ~ ").substringAfter('.').let { "$it 개막" }
        return "$head · ${e.venueShort}"
    }
}
