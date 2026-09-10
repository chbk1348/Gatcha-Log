package com.gatcha.log.ui.game

import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.LaunchedEffect
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
 */
@Composable
private fun rememberHoyolandEvent(): HoyolandEvent {
    var event by remember { mutableStateOf(HoyolandApi.current) }
    LaunchedEffect(Unit) { event = HoyolandApi.load() }
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
fun HoyolandDetailPage(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val (e, refreshing, refresh) = rememberHoyolandRefresher()
    val cart by viewModel.hoyolandCart.collectAsState()
    // 하위 페이지를 **상태 하나로** 모은다. 예전엔 지스타만 AnimatedContent 에 있고 굿즈·부스는
    // `if … return` 으로 컴포지션을 갈아끼워, 같은 페이지에서 나가는데 어떤 건 밀려 나가고
    // 어떤 건 0프레임으로 튀었다(홈 `HomeSub` 와 같은 이유로 하나로 합쳤다).
    var page by remember { mutableStateOf(HoyolandSub.None) }
    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState != HoyolandSub.None) {
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
            HoyolandSub.Gstar ->
                SectionPage(e.gstar.title.ifBlank { "G-STAR" }, onBack = { page = HoyolandSub.None }) {
                    GstarDetailContent()
                }
            HoyolandSub.Stage ->
                SectionPage(
                    "일자별 시간표",
                    onBack = { page = HoyolandSub.None },
                    isRefreshing = refreshing,
                    onRefresh = refresh,
                ) {
                    HoyolandTimetableSection(e)
                }
            HoyolandSub.Goods ->
                SectionPage(
                    "굿즈 목록",
                    onBack = { page = HoyolandSub.None },
                    bottomBar = { HoyolandGoodsBar(e, cart) { page = HoyolandSub.Cart } },
                ) {
                    HoyolandGoodsContent(e, cart) { name, n -> viewModel.setGoodsQuantity(name, n) }
                }
            HoyolandSub.Cart ->
                SectionPage(
                    "장바구니",
                    onBack = { page = HoyolandSub.Goods },
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
                SectionPage("부스 체험", onBack = { page = HoyolandSub.None }) { HoyolandBoothContent(e) }
            HoyolandSub.None ->
                SectionPage(
                    "호요랜드",
                    onBack,
                    isRefreshing = refreshing,
                    onRefresh = refresh,
                    actions = {
                        // 아이콘 하나로는 "지스타"가 읽히지 않아 글자를 쓴다. 대신 **면·테두리·높이는
                        // 헤더 원형 버튼([GlgCircleIconButton])과 같은 값**이라, 같은 줄에서 따로 놀지
                        // 않는다(흰 배경 · 1.5dp 테두리 · 44dp).
                        //
                        // 색만 강조색을 따르지 않는다 — 지스타는 이 앱의 기능이 아니라 **바깥 행사**라,
                        // 테마색을 입히면 앱이 미는 자리처럼 보인다. 먹색 하나로 고정한다.
                        if (!e.gstar.isEmpty) {
                            Row(
                                Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.White)
                                    .border(1.5.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                                    .clickable { page = HoyolandSub.Gstar }
                                    .padding(start = 14.dp, end = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("G-STAR", fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                ) {
                    HoyolandDetailContent(onOpenSub = { page = it })
                }
        }
    }
}

/**
 * 지스타(G-STAR) — 호요랜드와 **별개 행사**지만, 호요버스가 나오는 국내 오프라인 자리라
 * 같은 페이지 묶음에서 다룬다. 내용은 전부 shared 의 `HoyolandGstar` 에서 온다
 * (참가사 명단이 순차 공개돼 자주 바뀐다).
 */
@Composable
fun GstarDetailContent() {
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    val e = rememberHoyolandEvent()
    val g = e.gstar
    if (g.isEmpty) {
        Text("아직 공개된 정보가 없어요.", fontSize = 13.sp, color = TextSecondary)
        return
    }

    // ── 히어로 — 호요랜드 상세와 같은 짜임(남은 날짜를 숫자 그 자체로). 대신 이 페이지는
    // 부속 행사라 한 단계 작다. 강조색을 쓰지 않는 것도 같은 이유다 — 앱이 미는 자리가 아니다.
    val brief = g.homeBrief()
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                if (brief != null) {
                    Text(
                        brief.dday,
                        fontSize = 13.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.Black.copy(alpha = 0.06f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 3칸 — 이 행사에서 먼저 궁금한 것만. 나머지는 아래 목록으로 내린다.
            Row(Modifier.fillMaxWidth()) {
                GstarStat("기간", g.periodShort, g.dayCountLabel, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(34.dp).background(DividerColor))
                GstarStat("장소", g.venueShort, "", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(34.dp).background(DividerColor))
                GstarStat("규모", g.scaleLabel, "", Modifier.weight(1f))
            }
        }
    }

    // ── 호요버스 출품작 — 이 페이지를 여는 이유다. 팩트 목록에 끼워 두지 않고 제 자리를 준다.
    if (g.lineup.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text("호요버스 출품작", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                g.lineup.forEachIndexed { i, item ->
                    if (i > 0) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                        Spacer(Modifier.height(12.dp))
                    }
                    HoyolandLineupRow(item)
                }
            }
        }
    }

    // ── 함께 참가 — 일곱 곳이 "·" 로 이어진 한 줄은 읽히지 않는다. 칩으로 흩어 놓는다.
    if (g.partners.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text("함께 참가", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                g.partners.forEach { name ->
                    Text(
                        name,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.Black.copy(alpha = 0.045f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    // ── 그 밖의 정보 — 원격이 항목을 더해도 여기로 흘러 들어온다(화면이 라벨을 몰라도 안 빠진다).
    if (g.otherFacts.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text("그 밖의 정보", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                g.otherFacts.forEachIndexed { i, f ->
                    if (i > 0) Spacer(Modifier.height(9.dp))
                    HoyolandFactRow(f.label, f.value)
                }
            }
        }
    }

    if (g.url.isNotBlank()) {
        Spacer(Modifier.height(20.dp))
        GlgOutlineButton(
            "공식 사이트",
            onClick = { openExternalLink(ctx, g.url) },
            modifier = Modifier.fillMaxWidth(),
            height = 46.dp,
            color = accent,
        )
    }
    if (g.notice.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(g.notice, fontSize = 11.sp, color = TextSecondary)
    }
}

/** 히어로 3칸 한 칸 — 라벨 위, 값 아래. [sub] 는 값 옆 작은 보조(기간의 "4일"). */
@Composable
private fun GstarStat(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.5.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

/** 호요랜드 상세의 하위 페이지 — 진입 카드로 연다. */
enum class HoyolandSub { None, Gstar, Stage, Goods, Cart, Booth }

@Composable
fun HoyolandDetailContent(onOpenSub: (HoyolandSub) -> Unit = {}) {
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
            // ── 참여 게임 — 아래 독립 섹션이었던 것을 여기로 들였다. "어느 게임이 오나"는
            // 이 행사의 **기본 정보**라 일정·장소와 같은 카드에 있어야 하고, 세로 목록으로
            // 늘어놓으면 다섯 줄이 카드 하나를 통째로 먹었다. 두 칸 그리드로 접는다.
            if (e.lineup.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(12.dp))
                Text("참여 게임", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(9.dp))
                // **한 줄에 전부.** 두 줄 그리드는 카드에서 차지하는 덩이가 커서, 일정·장소와
                // 같은 무게가 됐다. 여기서 필요한 건 "어느 게임이 오나"의 목록 자체지 게임별
                // 설명이 아니다 — 테마는 무대 시간표에서 읽힌다.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.lineup.forEach { item -> HoyolandLineupTile(item, Modifier.weight(1f)) }
                }
            }

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

    // ── 현장에서 — 시간표 · 굿즈 목록 · 부스 체험.
    //
    // 셋 다 본문에 펼치면 이 페이지의 본론(언제·어디서)이 스크롤 저 아래로 밀린다.
    // 시간표만 **전체 폭**을 주는 이유: 나머지 둘과 달리 "지금 무대에서 무엇을 하는가"는
    // 이 페이지가 답해야 하는 질문에 가장 가깝다. 카드 한 줄이 그 답을 미리 말한다.
    //
    // 제목을 붙이는 이유: 다른 섹션은 전부 [여백 20 + 제목 + 10] 인데 여기만 제목이 없어
    // **예매 카드에 딸린 것처럼** 보였다. 셋의 공통점이 "현장에서 쓰는 것" 이라 그렇게 부른다.
    Spacer(Modifier.height(20.dp))
    Text("현장에서", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    HoyolandSubEntryWide(
        "일자별 시간표",
        e.stageEntryLine(),
        Icons.Default.Schedule,
    ) { onOpenSub(HoyolandSub.Stage) }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HoyolandSubEntry(
            "굿즈 목록",
            e.goodsPriceRange().ifBlank { "판매 목록 공개 전" },
            Icons.Default.ShoppingBag,
            Modifier.weight(1f),
        ) { onOpenSub(HoyolandSub.Goods) }
        HoyolandSubEntry(
            "부스 체험",
            if (e.booths.isEmpty()) "부스 정보 공개 전" else "${e.booths.size}곳 · 게임별 체험존",
            Icons.Default.Storefront,
            Modifier.weight(1f),
        ) { onOpenSub(HoyolandSub.Booth) }
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

/**
 * 일자별 시간표 — 날짜 탭 + 그날 프로그램.
 *
 * 기본 선택은 **행사 중이면 오늘**이다([HoyolandEvent.defaultDayIndex]). 현장에서 꺼냈을 때
 * 첫날이 선택돼 있으면 매번 한 번 더 눌러야 한다.
 */
@Composable
fun HoyolandTimetableSection(e: HoyolandEvent) {
    val accent = LocalAccent.current
    val ymds = e.dayYmds
    if (ymds.isEmpty()) return
    // 원격 갱신으로 기간이 바뀌면 선택 인덱스가 범위를 벗어날 수 있어 목록을 키로 준다.
    var sel by remember(ymds) { mutableStateOf(e.defaultDayIndex()) }
    val ymd = ymds.getOrElse(sel) { ymds.first() }
    val stage = e.stageSlots(ymd)
    val games = e.stageGames(ymd)
    // 게임 필터 — 날짜를 바꾸면 푼다(그날 없는 게임이 걸린 채 빈 목록이 되지 않게).
    var gameFilter by remember(ymd) { mutableStateOf<String?>(null) }

    Text("일자별 시간표", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Text("메인 무대 공연 편성", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
    // 날짜 선택 — 앱 공용 세그먼트 탭([GlgSegmentedTabs]). 리딤코드의 게임 탭도 같은 것을 쓴다.
    GlgSegmentedTabs(
        labels = ymds.map { e.dayTabDate(it) },
        subLabels = ymds.map { e.dayTabWeekday(it) },
        selected = sel,
        onSelect = { sel = it },
    )
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
                StageRow(e, item, live?.slot === item.slot)
            }
        }
    }
}

/** NOW LIVE 배지 색 — 게임색 위에서도 읽히는 단 하나의 고정색(앱의 '임박' 주황과 같은 계열). */
private val LiveRed = Color(0xFFE8634A)

/**
 * 전체 폭 진입 카드 — 아이콘 + 제목 + 요약 + 셰브론.
 * 두 칸 카드보다 요약을 길게 쓸 수 있어, 들어가기 전에 볼 값이 있는지 알 수 있다.
 */
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
 * 목업: `Gatcha Log MD/design_hoyoland_goods_mockup.html` A 안.
 */
@Composable
fun HoyolandGoodsContent(e: HoyolandEvent, cart: HoyolandCart, onQuantity: (String, Int) -> Unit) {
    val accent = LocalAccent.current
    val all = e.visibleGoods
    val games = e.goodsGames
    var gameFilter by remember { mutableStateOf<String?>(null) }

    if (all.isEmpty()) {
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
        return
    }

    // ── 가격대 — 목록보다 먼저. 얼마를 들고 갈지가 첫 질문이다.
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

    if (games.size > 1) {
        Spacer(Modifier.height(12.dp))
        GlgSegmentedTabs(
            labels = listOf("전체") + games.map { e.stageLabel(it) },
            selectedColors = listOf(accent) + games.map {
                e.stageColor(it).let { c -> if (c == 0L) TextSecondary else c.toColor() }
            },
            selected = games.indexOf(gameFilter) + 1,
            onSelect = { i -> gameFilter = if (i == 0) null else games.getOrNull(i - 1) },
        )
    }

    Spacer(Modifier.height(12.dp))
    val shown = all.filter { gameFilter == null || it.game == gameFilter }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            shown.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                HoyolandGoodsRow(e, item, cart.quantityOf(item.name), onQuantity)
            }
        }
    }
    // 하단 고정 바에 가리지 않게 — 바 높이(알약 + 위아래 여백)만큼 비워 둔다.
    Spacer(Modifier.height(84.dp))
}

/**
 * 굿즈 한 줄 — [썸네일 48 · 이름·갈래 · 가격/수량].
 *
 * A 안(담기 원)에서 갈아탔다. 훑기는 리스트가 낫고, 수량은 **목록에서 바로** 정하는 편이
 * 자연스럽다 — 같은 키링을 두 개 사는 일이 흔한데 담기 토글만 있으면 장바구니까지 들어가야 했다.
 *
 * 담기 전에는 「담기」 버튼, 담은 뒤에는 스테퍼로 바뀐다. 품절은 흐리게 두고 담기만 막는다 —
 * 가격을 기억하러 오는 사람이 있다.
 */
@Composable
private fun HoyolandGoodsRow(
    e: HoyolandEvent,
    item: HoyolandGoods,
    quantity: Int,
    onQuantity: (String, Int) -> Unit,
) {
    val accent = LocalAccent.current
    val raw = e.stageColor(item.game)
    val c = if (raw == 0L) TextSecondary else raw.toColor()
    val label = if (item.game.isBlank()) "공용" else e.stageLabel(item.game)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .alpha(if (item.soldOut) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 썸네일 자리 — 공식 굿즈 이미지가 나오면 이 칸을 그대로 이미지로 바꾼다.
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (item.soldOut) DividerColor else c.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 9.5.sp, fontWeight = FontWeight.Black,
                color = if (item.soldOut) TextThird else c,
                textAlign = TextAlign.Center, lineHeight = 11.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 18.sp)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                val meta = listOfNotNull(
                    item.category.ifBlank { null },
                    if (item.soldOut) "품절" else null,
                    item.note.ifBlank { null },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.width(5.dp))
                    Text(meta, fontSize = 11.sp, color = if (item.soldOut) TextSecondary else c)
                }
            }
        }
        Column(
            Modifier.padding(start = 11.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                if (item.price > 0) e.wonLabel(item.price) else "미정",
                fontSize = 13.sp,
                fontWeight = if (item.price > 0) FontWeight.Black else FontWeight.Bold,
                color = if (item.price > 0) TextPrimary else TextThird,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.height(6.dp))
            when {
                item.soldOut -> GoodsAddButton("품절", enabled = false) {}
                quantity <= 0 -> GoodsAddButton("담기", enabled = true) { onQuantity(item.name, 1) }
                else -> Row(
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

/** 담기/품절 버튼 — 스테퍼와 같은 높이라 담기 전후로 줄 높이가 흔들리지 않는다. */
@Composable
private fun GoodsAddButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, if (enabled) accent else DividerColor, RoundedCornerShape(9.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) accent else TextThird,
        )
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.94f))
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
            Text(
                e.wonLabel(e.cartTotal(cart)),
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
 * 게임별 부스 체험.
 *
 * 무대와 달리 **시각이 없다** — 상시 운영이고 대신 줄을 서거나 예약을 잡는다. 그래서
 * 시간표가 아니라 게임별 카드로 그리고, 현장에서 먼저 찾는 값(위치)을 제목 옆에 붙인다.
 */
@Composable
fun HoyolandBoothContent(e: HoyolandEvent) {
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
    e.booths.forEachIndexed { i, b ->
        if (i > 0) Spacer(Modifier.height(12.dp))
        HoyolandBoothCard(e, b)
    }
}

@Composable
private fun HoyolandBoothCard(e: HoyolandEvent, b: HoyolandBooth) {
    val c = e.stageColor(b.game).let { if (it == 0L) TextSecondary else it.toColor() }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.stageLabel(b.game),
                    fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(b.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                // 예약이 필요한 곳은 **가서 줄만 서면 되는 곳과 다른 준비**가 든다.
                if (b.needsReservation) GlgBadge("예약 필요", c)
            }
            if (b.desc.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(b.desc, fontSize = 12.5.sp, color = TextSecondary, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
            Spacer(Modifier.height(12.dp))
            if (b.location.isNotBlank()) {
                HoyolandFactRow("위치", b.location)
                Spacer(Modifier.height(8.dp))
            }
            if (b.duration.isNotBlank()) {
                HoyolandFactRow("소요", b.duration)
                Spacer(Modifier.height(8.dp))
            }
            if (b.capacity.isNotBlank()) {
                HoyolandFactRow("정원", b.capacity)
                Spacer(Modifier.height(8.dp))
            }
            // 보상은 줄 설 이유가 되는 값이라 목록 끝이 아니라 **눈에 띄는 자리**에 둔다.
            if (b.reward.isNotBlank()) {
                Text(
                    b.reward,
                    fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
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
private fun StageRow(e: HoyolandEvent, item: StageSlot, isLive: Boolean) {
    val raw = e.stageColor(item.slot.game)
    val c = if (raw == 0L) Color(0xFF98A0AB) else raw.toColor()
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (item.state == StageState.DONE) 0.40f else 1f)
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
            val sub = listOfNotNull(
                item.slot.desc.ifBlank { null },
                if (item.slot.minutes > 0) "${item.slot.minutes}분" else null,
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 11.5.sp, color = TextSecondary)
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
/**
 * 참여 게임 한 칸(그리드) — 게임색 면 + 이름 + 테마.
 *
 * 공식 키비주얼이 게임별로 나오기 전이라 썸네일 자리를 색면으로 대신한다.
 * 공개되면 이 칸의 배경을 이미지로 바꾸면 된다(칸 크기는 그대로 쓸 수 있게 고정 높이).
 */
@Composable
private fun HoyolandLineupTile(item: HoyolandLineup, modifier: Modifier = Modifier) {
    val c = if (item.colorArgb != 0L) item.colorArgb.toColor() else GameData.colorFor(item.game).toColor()
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.copy(alpha = 0.10f))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 다섯 칸이 한 줄에 들어가야 해서 폭이 좁다 — 두 줄까지 접고 글자를 줄인다.
        Text(
            GameData.byNameOrNull(item.game)?.shortName ?: item.game,
            fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
