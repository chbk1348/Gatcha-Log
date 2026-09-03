package com.gatcha.log.ui.game

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.hmsLabel
import com.gatcha.log.data.isImminent
import com.gatcha.log.data.dhLabel
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.delay
import com.gatcha.log.data.GameScheduleLine
import com.gatcha.log.data.GameData
import com.gatcha.log.data.ScheduleEntry
import com.gatcha.log.data.ScheduleLogic
import com.gatcha.log.data.ScheduleMark
import com.gatcha.log.data.ScheduleWeek
import com.gatcha.log.data.WeekDay
import com.gatcha.log.data.buildStartEntries
import com.gatcha.log.data.buildWeeks
import com.gatcha.log.data.mark
import com.gatcha.log.data.ScheduleSummary
import com.gatcha.log.data.collabTitle
import com.gatcha.log.data.isCollabBanner
import com.gatcha.log.ui.components.GlgBadgeText
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import coil.compose.SubcomposeAsyncImage
import com.gatcha.log.data.weaponLabelOf
import androidx.compose.material.icons.outlined.Info
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipBox
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.ExperimentalMaterial3Api
import com.gatcha.log.data.Game

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

private val Urgent = Color(0xFFE8634A)
private val CollabBadge = Color(0xFF6D5AE6)
/** 콜라보 배너 그라데이션 끝색 — 한 색 평면보다 배너가 앞으로 나와 보인다. */
private val CollabGradientEnd = Color(0xFF9B5DE5)

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
/** 주간 보드 칸 배경 — 흰 카드보다 한 단계 눌러 그리드가 배경처럼 읽히게. */
private val CardSurfaceLight = androidx.compose.ui.graphics.Color(0xFFF6F7F9)

@Composable
fun GameScheduleSection(
    entries: List<ScheduleEntry>,
    banners: List<GachaBanner>,
    onSeeAll: () -> Unit,
) {
    val accent = LocalAccent.current
    val lines = ScheduleLogic.gameLines(banners, entries)
    val summary = ScheduleLogic.summarize(banners, entries)
    Text("게임 일정", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    Text("픽업 배너와 이벤트 마감을 한곳에서.", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
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

/** 진입 카드 한 줄에 세울 얼굴 수 — 게임명·이름·D-day 와 한 줄을 나눠 써야 해서 폭이 빠듯하다. */
private const val LINE_FACE_MAX = 3

/** 진입 카드용 작은 초상 — 상세의 [PickupAvatar](38dp)보다 작고, 여러 장을 겹쳐 세운다. */
private val LineFaceSize = 22.dp

// 게임 한 줄 — 색 바 + 게임명(+콜라보) + 픽업 얼굴·이름 + 잔여.
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
        if (line.faces.isEmpty()) {
            // 픽업이 없는 게임(젠존제·명조)은 얼굴이 없다 — 일정 건수 요약("이벤트 5")이 그 자리를 지킨다.
            Text(
                line.summary, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        } else {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 얼굴끼리는 겹쳐 세운다 — 석 장을 따로 놓으면 이름 자리가 남지 않는다.
                Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
                    line.faces.take(LINE_FACE_MAX).forEach { LineFace(it) }
                }
                Text(
                    line.pickupNames, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Text(
            line.remainLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (line.urgent) Urgent else TextPrimary,
        )
    }
}

/**
 * 진입 카드 줄의 얼굴 한 장.
 *
 * 겹쳐 세우므로 **흰 테두리**를 둘러 뒤 장과 경계를 만든다(테두리가 없으면 어두운 초상끼리
 * 한 덩어리로 뭉쳐 보인다). 초상을 못 받는 경우는 [PickupAvatar] 와 같은 이유로 실루엣을 세운다.
 */
@Composable
private fun LineFace(b: GachaBanner) {
    val isWeapon = b.type == "weapon"
    Box(
        Modifier.size(LineFaceSize).clip(CircleShape)
            .background(PickupGold.copy(alpha = 0.14f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (b.iconUrl.isBlank()) {
            PickupFallbackIcon(isWeapon)
        } else {
            SubcomposeAsyncImage(
                model = b.iconUrl,
                contentDescription = b.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = { PickupFallbackIcon(isWeapon) },
            )
        }
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameScheduleFullPage(
    banners: List<GachaBanner>,
    events: List<GameEvent>,
    challenges: List<GameChallenge>,
    collabExpanded: Boolean,
    onToggleCollab: () -> Unit,
    onOpenHoyoland: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    var tab by remember { mutableStateOf(0) }
    // 홈 카드와 같은 훅 — 노출 시점(D-60)이 한 곳에서 정해진다.
    val hoyoland = rememberFeaturedHoyoland()

    // ── 주간 보드 ──
    //
    // 예전엔 **끝나는 것만** 마감 순으로 늘어놓았다. 다음 픽업이 언제 *시작*하는지가 저축·천장
    // 관리의 기준인데 `startMillis` 를 쓰지 않았다.
    // 이제 시작·마감을 한 축에 얹고 **주 단위**로 끊는다 — 가챠 운영이 주간 리셋·주 단위
    // 이벤트로 돌아가서 "이번 주에 뭘 해야 하나"가 실제 질문이다.
    val entries = remember(banners, events, challenges) {
        (
            ScheduleLogic.buildSchedule(banners, events, challenges) +
                buildStartEntries(banners)
            ).sortedBy { it.target }
    }
    val weeks = remember(entries) { buildWeeks(entries) }
    val undated = remember(banners) { ScheduleLogic.undatedPickups(banners) }
    val summary = remember(banners, entries) { ScheduleLogic.summarize(banners, entries) }
    // 남은 시간 갱신 기준 — 24시간 안쪽 일정이 있을 때만 초 단위.
    val now = rememberScheduleNow(entries.map { it.target })

    // 이 페이지만 `SectionPage`(Column + verticalScroll) 를 쓰지 않는다 — `stickyHeader` 는
    // LazyColumn 에만 있다. 헤더 오버레이는 `LazyListState` 로 좁힌 boolean 을 넘기는 쪽
    // 오버로드를 쓴다(UpdateLogScreen 과 같은 구조).
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }

    /**
     * 지금 고정돼 있는 주차의 키 — **여기 한 곳에서만 잰다.**
     *
     * 처음엔 `stickyHeader` 안에서 주차마다 `derivedStateOf` 를 만들었는데 스크롤이 틱틱 끊겼다.
     * `layoutInfo` 는 스크롤 프레임마다 새 객체라 그 계산이 **매 프레임 × 주차 수**로 돌고,
     * 게다가 sticky header 는 pin/unpin 하며 자리가 바뀌어 그 안의 `remember` 와
     * `animateColorAsState` 까지 재생성됐다.
     *
     * 상위에서 하나로 줄이면 스캔이 프레임당 한 번이고, 값이 바뀌는 건 **주 경계에서뿐**이라
     * 재구성도 그때만 일어난다.
     */
    val pinnedKey by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo.firstOrNull {
                it.offset <= info.viewportStartOffset && (it.key as? String)?.startsWith("week-") == true
            }?.key as? String
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                // **뷰포트 자체를 인셋**한다(contentPadding 이 아니라) — stickyHeader 는
                // contentPadding top 을 무시하고 뷰포트 최상단에 붙으므로, 그러지 않으면
                // 주간 표가 고정 헤더 뒤로 파고든다.
                .padding(top = glgDetailContentTop()),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "tabs") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlgChip("일정", selected = tab == 0) { tab = 0 }
                    GlgChip("주년", selected = tab == 1) { tab = 1 }
                }
            }

            when (tab) {
                1 -> item(key = "anniversary") { AnniversaryContent() }
                else -> {
                    // 오프라인 행사는 **콜라보보다도 위**다. 주간 표는 게임 안에서 벌어지는
                    // 일만 다루는데, 이건 날짜를 비워 두고 움직여야 하는 유일한 일정이다
                    // (게다가 나흘 하고 끝난다). 개막 D-60 안쪽에만 끼어들고 그 밖엔 사라진다.
                    hoyoland?.let { h ->
                        item(key = "hoyoland") {
                            HoyolandScheduleBanner(h, onOpenHoyoland)
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    // 콜라보는 **맨 위**. 종료 시각이 미공지라 시간 축에 못 올리는데, 맨 아래에 두면
                    // 진행 중인 한정 콜라보를 스크롤 끝까지 내려야 본다 — 놓치면 되돌릴 수 없는
                    // 일정이 가장 늦게 읽혔다.
                    if (undated.isNotEmpty()) {
                        item(key = "collab") {
                            CollabPromoBanner(undated, collabExpanded, onToggle = onToggleCollab)
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    item(key = "summary") {
                        Text(
                            "시작 · 종료",
                            fontSize = 12.sp, color = TextSecondary,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                        SummaryStrip(summary)
                        Spacer(Modifier.height(16.dp))
                    }
                    weeks.forEachIndexed { i, w ->
                        val key = "week-$i"
                        // key 는 **전부** 준다 — 일부만 주면 재구성 때 아이템 매칭이 어긋난다.
                        stickyHeader(key = key) { WeekHeaderCard(w, pinned = pinnedKey == key) }
                        item(key = "$key-body") {
                            WeekEntries(w, now)
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
        GlgDetailHeaderOverlay("게임 일정", onBack, scrolled)
    }
}

/**
 * D-day 배지·'예상' 알약의 높이 — **양 플랫폼 고정값**.
 *
 * [WeekCellHeight] 와 같은 이유다. 상하 패딩만 맞춰 두면 Compose 의 줄 상자가 iOS 보다 높아
 * 같은 지정값인데도 알약이 더 두껍게 나온다(2026-08-18 "디데이가 iOS 와 통일감이 없음").
 * 안쪽 글자는 [GlgBadgeText] 로 줄 상자를 글자에 맞춰 실제 중앙에 놓는다.
 * iOS `ScheduleRow` 의 `.frame(height:)` 와 **같이 고쳐야 한다.**
 */
private val DDayBadgeHeight = 20.dp


/**
 * 주간 그리드 한 칸의 높이 — **양 플랫폼 고정값**.
 *
 * 글꼴 줄 상자 높이는 Android 와 iOS 가 서로 다르다(같은 Pretendard·같은 sp 라도). 내용에 높이를
 * 맡기면 두 플랫폼 칸 크기가 어긋난다(2026-08-18 지적). 바깥 높이를 못 박고 안을 가운데 정렬한다.
 * iOS `WeekCell` 의 `.frame(height:)` 와 **같이 고쳐야 한다.**
 */
private val WeekCellHeight = 46.dp

/**
 * 한 주의 **머리** — 라벨·기간·건수 + 일~토 7칸 그리드. 스크롤 중 상단에 고정된다.
 *
 * 고정되면 **카드로 떠 있는다**(흰 배경 + 아웃라인 + 라운드). 헤더바 재질을 따라가는 방식도
 * 있지만 그러면 OS 버전마다 바 재질이 달라 계속 어긋난다 — 앱 카드 규격을 쓰는 게 낫다.
 * [pinned] 동안에만 아웃라인에 강조색을 줘서 붙어 있다는 걸 알린다. (iOS `WeekHeader` 와 파리티)
 */
@Composable
private fun WeekHeaderCard(w: ScheduleWeek, pinned: Boolean) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    val border by animateColorAsState(
        if (pinned) accent else Color.Black.copy(alpha = 0.10f),
        label = "weekPinBorder",
    )
    Column(
        Modifier.fillMaxWidth()
            // 카드 **바깥** 여백 — 고정됐을 때 헤더바에 딱 붙지 않고 한 칸 떨어져 뜬다.
            .padding(top = 10.dp)
            .clip(shape)
            .background(Color.White)
            .border(if (pinned) 1.5.dp else 1.dp, border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(w.label, fontSize = 13.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.width(7.dp))
            Text(
                "${w.rangeLabel} · ${if (w.entries.isEmpty()) "일정 없음" else "${w.entries.size}건"}",
                fontSize = 11.sp, color = TextSecondary,
            )
        }
        // 7칸 그리드 — 칸이 좁아 제목은 못 담는다. 어느 날이 바쁜지만 점으로 알린다.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            w.days.forEach { d -> WeekCell(d, Modifier.weight(1f)) }
        }
    }
}

/** 한 주의 **몸** — 그 주 항목 목록. 헤더가 고정된 채 이쪽만 흐른다. */
@Composable
private fun WeekEntries(w: ScheduleWeek, now: Long) {
    // 빈 주도 **말을 해야 한다.** [buildWeeks] 는 일부러 빈 주를 남기는데(언제 한가한지도 정보다)
    // 본문이 그냥 비어 있으면 헤더 카드 사이 여백처럼 읽혀, 로딩 중인지 진짜 없는지 알 수 없었다.
    // 헤더 줄에도 "일정 없음"이 있지만 11sp 회색이라 눈에 걸리지 않는다.
    // iOS `WeekEntries` 와 **같이 고쳐야 한다.**
    if (w.entries.isEmpty()) {
        Text(
            "예정된 일정이 없어요",
            fontSize = 12.5.sp, color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp),
        )
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        w.entries.forEach { e ->
            ScheduleRow(e, now)
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun WeekCell(d: WeekDay, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(
        modifier
            .height(WeekCellHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(if (d.isToday) accent.copy(alpha = 0.08f) else CardSurfaceLight)
            .border(1.dp, if (d.isToday) accent.copy(alpha = 0.45f) else DividerColor, RoundedCornerShape(10.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val dim = d.isPast && !d.isToday
        // 날짜·요일은 **줄 상자를 글자에 맞춘** GlgBadgeText 로 그린다. 기본 Text 는 Pretendard 의
        // 큰 top/bottom 메트릭에 폰트 패딩까지 더해 한 줄이 글자보다 훨씬 높아지고, 두 줄이 쌓이면
        // 칸이 iOS 보다 5dp 가까이 부풀었다(칩·세트효과와 같은 원인).
        GlgBadgeText(
            "${d.day}", 11.sp,
            if (dim) TextSecondary.copy(alpha = 0.45f) else TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        GlgBadgeText(
            d.weekdayKo, 8.5.sp,
            TextSecondary.copy(alpha = if (dim) 0.35f else 1f),
            fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        // 점 자리는 **항상 잡아 둔다.** 점이 있는 칸만 5dp 가 더 붙어 그 칸이 아래로 튀어나왔다
        // — 7칸이 한 줄로 나란해야 주의 모양이 읽힌다.
        Row(
            Modifier.height(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            d.dotColors.forEach { c ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(c.toColor()))
            }
        }
    }
}

/** 일정 한 줄 — 표식(▲▼) + 제목·부제 + D-day. */
@Composable
private fun ScheduleRow(e: ScheduleEntry, now: Long) {
    val mark = e.mark()
    val markColor = when (mark) {
        ScheduleMark.START -> LocalAccent.current
        ScheduleMark.END -> DangerText
    }
    val d = e.dDay(now)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 리딩 — 게임색 약칭 배지(지출 행과 같은 규격: 라운드 사각 + 게임색 14% 배경 + 약칭).
            // 일정은 여섯 게임이 한 줄기로 섞여 흐르므로, 어느 게임 건지가 **가장 먼저** 읽혀야 한다.
            val gameColor = e.colorArgb.toColor()
            val abbr = GameData.games.firstOrNull { it.key == e.gameKey }?.abbr ?: e.gameShort.take(2)
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(gameColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(abbr, fontSize = 11.sp, fontWeight = FontWeight.Black, color = gameColor)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                // 픽업 줄은 **얼굴로** 보여준다 — 이름 나열보다 먼저 알아본다. 상류가 항목마다
                // `icon` 을 주는데 이름만 읽고 버리고 있었다(3게임 전부 준다).
                // 규격은 '내 캐릭터' 로스터 칸과 같다(원형 초상 + 이름 아래 한 줄, 5성 금색 바탕).
                // 아이콘이 없는 줄(이벤트·콘텐츠)은 부제를 여기 글자로 둔다. 픽업은 카드 아래
                // 별도 단으로 내려간다(아래 참고).
                if (e.pickups.isEmpty() && e.sub.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        e.sub, fontSize = 10.5.sp, color = TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 트레일링 = 남은 시간 + 그 시각의 날짜.
            //
            // D-N 만으로는 **마감 당일에 지금 해야 하는지 판단이 안 된다.** 24시간 안쪽(=D-1)부터는
            // 초까지 세고 사이렌처럼 명멸시킨다. 반대로 며칠 남은 일정에 초를 붙여 봐야 의미가 없어
            // 그때는 D-N 그대로 둔다.
            //
            // 날짜를 아래 붙이는 이유 — "D-3"은 상대값이라 달력을 다시 떠올려야 한다. 픽업 마감일을
            // 실제 날짜로 알아야 저축·천장 계획이 선다.
            val imminent = isImminent(e.target, now)
            Column(horizontalAlignment = Alignment.End) {
                val hot = imminent || d in 0..3
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 종류는 **남은 시간 바로 앞**에 붙인다. 앞서 게임 배지 옆에 따로 세워 봤는데,
                    // 정작 "무엇까지 얼마 남았나"는 한 덩어리로 읽히는 말이라 줄 양끝으로 갈라 놓으면
                    // 눈이 두 번 움직인다. (그 전엔 ▲▼ 였고, 그건 방향만 있고 뜻이 없었다.)
                    GlgBadgeText(
                        when (mark) {
                            ScheduleMark.START -> "시작까지"
                            ScheduleMark.END -> "종료까지"
                        },
                        9.sp, markColor, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .then(if (imminent) Modifier.sirenPulse() else Modifier)
                            .height(DDayBadgeHeight)
                            .clip(RoundedCornerShape(6.dp))
                            // 임박 색은 **종류 색**을 따른다. 예전엔 무조건 빨강이라, 곧 시작하는 픽업이
                        // 마감 임박과 같은 경고색으로 떴다 — 시작은 다급한 일이 아니다. 앞의
                        // "시작까지" 라벨과도 색이 갈려 한 덩어리로 안 읽혔다.
                        .background(if (hot) markColor.copy(alpha = 0.12f) else ProgressEmpty)
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlgBadgeText(
                            when {
                                imminent -> hmsLabel(e.target, now)
                                d <= 0 -> "종료"
                                else -> "D-$d"
                            },
                            10.5.sp,
                            if (hot) markColor else TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                // 날짜만으로는 부족하다 — D-1 에서 초를 세기 시작하면 "그래서 몇 시에 끝나나"가
                // 바로 다음 질문이 된다. 접속 계획은 시각까지 있어야 세울 수 있다.
                GlgBadgeText(
                    DateUtil.shortDateTime(e.target), 9.5.sp,
                    TextSecondary.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // 픽업은 **한 단 아래**로 내린다. 제목 칸 안에 두면 초상 44dp 가 제목·D-day 와 같은
        // 줄에 얹혀 카드가 세로로 눌린 것처럼 보이고, 얼굴이 글자 사이에 끼여 잘 안 읽힌다.
        //
        // 아이콘 **유무로 거르지 않는다.** 예전엔 URL 이 빈 항목을 통째로 빼서, 하나라도
        // 비면 픽업 줄 전체가 글자로 되돌아갔다(아직 초상이 안 올라온 신규 캐릭터·상류
        // 누락이면 그렇게 된다). 못 받은 칸만 사람 아이콘으로 세운다.
        if (e.pickups.isNotEmpty()) {
            // 구분선 — 일정 한 줄과 픽업은 다른 종류의 정보다. 여백만으로 나누면 초상이
            // 그 줄에 딸린 건지 다음 줄로 넘어간 건지 애매하다.
            HorizontalDivider(
                color = DividerColor,
                modifier = Modifier.padding(top = 9.dp, bottom = 9.dp),
            )
            // 캐릭터와 무기는 **다른 줄**로 가른다. 한 줄에 섞으면 어느 쪽이 캐릭터 픽업인지
            // 초상만 보고는 알 수 없다(무기도 같은 원형 초상으로 온다).
            val chars = e.pickups.filter { it.type != "weapon" }
            val weapons = e.pickups.filter { it.type == "weapon" }
            // 줄마다 **무엇을 세운 줄인지** 왼쪽에 적는다. 초상만 보면 캐릭터와 무기가
            // 구분되지 않고, 무기는 게임마다 부르는 이름도 다르다(광추·W-엔진).
            val gameOf = GameData.byNameOrNull(e.pickups.first().game)
            // 젠존제는 W-엔진 픽업을 싣지 않는다([EnneadApi.fetchZzz]) — 상류가 신규 엔진의
            // 이름을 빈 문자열로 주거나 원문 직역으로 보내서, 그대로 띄우면 없는 이름을
            // 앱이 주장하게 된다. **빠졌다는 사실 자체는 알려야** 해서 안내를 단다.
            val zzzHidesWEngine = e.gameKey == Game.ZZZ.key
            if (chars.isNotEmpty()) PickupRow("캐릭터", chars, showInfo = zzzHidesWEngine)
            if (weapons.isNotEmpty()) {
                if (chars.isNotEmpty()) {
                    HorizontalDivider(
                        color = DividerColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 9.dp, bottom = 9.dp),
                    )
                }
                PickupRow(weaponLabelOf(gameOf), weapons)
            }
        }
    }
}

/** 5성 픽업 바탕색 — '내 캐릭터' 로스터와 같은 값. */
private val PickupGold = Color(0xFFD8A12E)

/**
 * 젠존제 픽업 줄 우상단의 안내 — **W-엔진이 왜 안 보이는지**.
 *
 * 상류가 신규 엔진 이름을 빈 문자열로 주거나 원문 직역으로 보내서 싣지 않는데
 * ([EnneadApi.fetchZzz]), 아무 말도 없으면 앱이 빠뜨린 것처럼 보인다.
 *
 * 카드 안에 글로 적지 않고 **툴팁**으로 두는 이유: 이 설명은 한 번 읽으면 그만인데
 * 픽업 줄마다 두 줄씩 붙으면 정작 얼굴과 마감을 밀어낸다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WEngineInfoTip(modifier: Modifier = Modifier) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val position = remember(density) {
        with(density) { ClampedTooltipPosition(TOOLTIP_MARGIN.roundToPx(), TOOLTIP_GAP.roundToPx()) }
    }
    TooltipBox(
        positionProvider = position,
        tooltip = {
            RichTooltip(title = { Text("W-엔진 픽업 미표시", fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }) {
                Text(
                    "제공처가 신규 W-엔진의 이름을 비워서 보내거나 원문 그대로 보내옵니다. " +
                        "실제와 다른 이름이 뜨는 것을 막기 위해 캐릭터 픽업만 싣습니다.",
                    fontSize = 11.5.sp,
                )
            }
        },
        state = state,
        modifier = modifier,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "W-엔진이 안 보이는 이유",
            tint = TextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(15.dp).clip(CircleShape)
                .clickable { scope.launch { state.show() } },
        )
    }
}

/** 툴팁을 화면 가장자리에서 이만큼 띄운다. */
private val TOOLTIP_MARGIN = 12.dp

/** 툴팁과 앵커 사이 간격. */
private val TOOLTIP_GAP = 6.dp

/**
 * 툴팁 위치 — 앵커 위에 띄우되 **화면 밖으로 나가지 않게 가둔다**.
 *
 * material3 기본 제공자를 쓰다가 젠존제 줄에서 툴팁 오른쪽이 잘렸다. 안내 아이콘은 픽업 줄
 * 우상단, 즉 화면 오른쪽 끝에 붙어 서는데 [RichTooltip] 은 그보다 훨씬 넓다. 앵커에 가운데를
 * 맞추면 폭의 절반이 화면 밖으로 밀려난다.
 *
 * 그래서 가운데를 먼저 구하고 **좌우 여백 안으로 밀어 넣는다.** 세로는 위가 기본이지만 위에
 * 자리가 모자라면 아래로 내린다 — 픽업 줄이 카드 맨 위에 오면 위쪽 공간이 없다.
 */
private class ClampedTooltipPosition(
    private val marginPx: Int,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centered = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val maxX = windowSize.width - popupContentSize.width - marginPx
        // 툴팁이 화면보다 넓으면 maxX 가 marginPx 보다 작아진다 — coerceIn 이 터지지 않게
        // 하한을 우선한다(그 경우 왼쪽 가장자리에 맞추고 오른쪽이 잘리는 편이 낫다).
        val x = centered.coerceIn(marginPx, maxOf(marginPx, maxX))

        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (above >= marginPx) above else anchorBounds.bottom + gapPx
        return IntOffset(x, y)
    }
}

/** 한 줄에 세울 수 있는 최대 픽업 수 — 이보다 많으면 뒤는 자른다. */
private const val PICKUP_SLOTS = 5

/**
 * **한 줄**에 세우는 칸 수. 이보다 많으면 줄을 나눈다.
 *
 * 5 였다가 3 으로 줄였다 — 다섯 칸이면 칸 폭이 51dp 라, 캐릭터(3~4자)는 몰라도
 * 광추·W-엔진 이름(실측 7~16자 — "무지개가 영원히 하늘에 머물길")이 3~4줄로 깨져
 * 줄 높이가 들쭉날쭉했다. iOS `pickupLineSlots` 와 같이 고쳐야 한다.
 */
private const val PICKUP_LINE_SLOTS = 3

/**
 * 픽업 한 줄 — 남은 폭을 **인원수만큼 균등하게** 나눈다(최대 [PICKUP_SLOTS] 칸).
 *
 * 칸마다 폭이 같으므로 픽업이 하나든 넷이든 칸 사이 간격이 일정하고, 초상·이름은 각 칸의
 * 가운데에 선다. 인원이 적으면 칸이 그만큼 넓어져 이름도 덜 접힌다.
 */
@Composable
private fun PickupRow(label: String, list: List<GachaBanner>, showInfo: Boolean = false) {
    val shown = list.take(PICKUP_SLOTS)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // 라벨 폭은 **고정**이다("캐릭터"·"광추"·"W-엔진" 길이가 제각각) — 안 그러면
        // 캐릭터 줄과 무기 줄의 초상이 세로로 어긋난다.
        //
        // 라벨도 자르지 않는다. 'W-엔진'처럼 폭에 꽉 차는 말이 있어서 한 줄로 묶으면
        // 게임에 따라 끝이 잘린다 — 무엇을 세운 줄인지 알리는 말이 잘리면 뜻이 없다.
        Text(
            label,
            fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
            modifier = Modifier.width(PickupLabelWidth).padding(top = 12.dp),
        )
        Spacer(Modifier.width(6.dp))
        // 칸 수에 따라 짜임을 바꾼다 — 균등 분할 하나로 1~5 를 다 감당하면 양끝이 다 무너진다.
        //
        //  1개  : 초상 **옆에** 이름(가로). 폭이 통째로 남는데 38dp 상자에 이름을 접을 이유가 없다.
        //  2~4개: 한 줄 균등 분할. 칸이 66dp 이상이라 이름이 1~2줄에 들어온다.
        //  5개  : **3+2 두 줄.** 두 줄 다 3칸 기준으로 놓는다(뒷줄은 빈 칸을 채운다) —
        //         뒷줄만 2등분하면 칸 폭이 달라져 위아래 얼굴이 어긋난다.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                shown.size == 1 -> PickupSlotWide(shown[0])
                shown.size > PICKUP_LINE_SLOTS -> {
                    PickupLine(shown.take(PICKUP_LINE_SLOTS))
                    PickupLine(shown.drop(PICKUP_LINE_SLOTS))
                }
                else -> PickupLine(shown)
            }
        }
        // 안내 버튼은 **자리를 차지한다**(겹쳐 얹지 않는다). 예전엔 줄 위에 오버레이로
        // 올려서 맨 오른쪽 초상과 겹쳤다 — 칸이 넓어질수록 더 파고들었다.
        if (showInfo) {
            Spacer(Modifier.width(6.dp))
            WEngineInfoTip(Modifier.padding(top = 10.dp))
        }
    }
}

/** 픽업 줄 왼쪽 라벨의 폭 — iOS `PickupRow.labelWidth` 와 같이 고쳐야 한다. */
private val PickupLabelWidth = 42.dp

/**
 * 초상 지름 — '내 캐릭터' 로스터(44)보다 한 단계 작다.
 *
 * 로스터는 캐릭터를 **고르는** 화면이라 얼굴이 주인공이지만, 여기서는 일정 한 줄에 딸린
 * 부가 정보다. 같은 크기로 두니 얼굴이 카드의 주인공이 돼 제목·마감이 뒤로 밀렸다.
 * iOS `PickupSlot` 과 같이 고쳐야 한다.
 */
private val PickupAvatarSize = 38.dp

/**
 * 픽업 한 칸 — 원형 초상 + 이름 한 줄. '내 캐릭터' [RosterSlot] 과 같은 형식이되,
 * 일정 줄 안에 들어가야 하므로 초상만 44 → 32 로 줄인다.
 *
 * 이름은 **좌측 정렬**이다. 로스터는 칸 폭이 고정이라 가운데 정렬이 맞지만, 여기서는
 * 칸이 글자 길이만큼만 커져서 가운데로 두면 초상과 이름의 축이 이름마다 어긋난다.
 */
@Composable
private fun PickupSlot(b: GachaBanner, modifier: Modifier = Modifier) {
    // 초상·이름 모두 **칸의 가운데**에 선다. 칸 폭이 균등하므로 이름 상자와 초상의
    // 중심축이 저절로 같아진다 — 따로 맞출 필요가 없다.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PickupAvatar(b)
        Spacer(Modifier.height(5.dp))
        // 이름은 **끝까지** 보여준다. 자르면 "그림자 사냥꾼의…"처럼 무엇인지 특정할 수 없는
        // 조각만 남는다 — 얼굴 옆 이름은 확인용이라 잘리면 있으나 마나다.
        // 줄 수를 묶지 않는다(광추·W-엔진 이름은 캐릭터명보다 길다). 칸끼리는 위를 맞춘다.
        Text(
            b.name, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 원형 초상 — 격자 칸([PickupSlot])과 가로형([PickupSlotWide])이 함께 쓴다. */
@Composable
private fun PickupAvatar(b: GachaBanner) {
    val isWeapon = b.type == "weapon"
    Box(
        Modifier.size(PickupAvatarSize).clip(CircleShape).background(PickupGold.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        // 초상을 못 받는 경우가 여럿이다 — 상류에 아직 이미지가 안 올라온 신규 캐릭터,
        // CDN 오류, 오프라인. 어느 쪽이든 **빈 원**을 남기면 자리만 차지하고 뜻이 없다.
        // 실루엣 아이콘을 세워 "여기 픽업이 하나 있다"까지는 읽히게 한다.
        if (b.iconUrl.isBlank()) {
            PickupFallbackIcon(isWeapon)
        } else {
            SubcomposeAsyncImage(
                model = b.iconUrl,
                contentDescription = b.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = { PickupFallbackIcon(isWeapon) },
            )
        }
    }
}

/**
 * 한 줄 — 언제나 [PICKUP_LINE_SLOTS] 칸으로 나눈다.
 *
 * 모자란 칸은 **빈 자리로 남긴다**(칸을 넓히지 않는다). 두 줄로 나뉜 뒷줄이 제 수만큼만
 * 등분하면 앞줄과 칸 폭이 달라져 위아래 얼굴이 어긋난다.
 */
@Composable
private fun PickupLine(items: List<GachaBanner>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { PickupSlot(it, Modifier.weight(1f)) }
        repeat(PICKUP_LINE_SLOTS - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * 픽업이 **하나뿐일 때** — 초상 오른쪽에 이름을 둔다.
 *
 * 줄 전체가 제 칸인데 세로로 쌓으면 38dp 상자에 긴 이름이 접히면서 폭은 폭대로 남는다.
 * 가로로 두면 16자짜리 광추 이름도 한 줄에 들어간다.
 */
@Composable
private fun PickupSlotWide(b: GachaBanner) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PickupAvatar(b)
        Spacer(Modifier.width(9.dp))
        Text(b.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

/** 초상을 못 받았을 때 세우는 실루엣 — 무기 픽업이면 사람 대신 별. */
@Composable
private fun PickupFallbackIcon(isWeapon: Boolean) {
    Icon(
        if (isWeapon) Icons.Default.Star else Icons.Default.Person,
        contentDescription = null,
        tint = PickupGold,
        modifier = Modifier.size(19.dp),
    )
}

/**
 * 남은 시간 표시용 현재 시각.
 *
 * 24시간 안쪽 일정이 하나라도 있으면 초 단위, 아니면 분 단위로 갱신한다 — 며칠 남은 일정에
 * 초를 세어 봐야 화면은 그대로인데 재구성만 60배로 늘어난다.
 *
 * 임박 판정을 **밖에서 한 번 계산해 넘기지 않는다.** 그러면 페이지를 열어 둔 채 일정이 24시간
 * 안으로 들어와도 시계가 분 단위에 머물러 타이머가 초를 세지 않는다. 여기서 `now` 를 읽어
 * 판정하면 다음 분 틱에 스스로 초 단위로 넘어간다.
 *
 * 화면을 벗어나면 코루틴이 취소되므로 백그라운드에서는 돌지 않는다.
 */
@Composable
private fun rememberScheduleNow(targets: List<Long>): Long {
    var now by remember { mutableStateOf(currentTimeMillis()) }
    val fast = targets.any { isImminent(it, now) }
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
 * 진행 중인 콜라보 — 일정 맨 위의 광고형 배너. **접기/펼치기 두 모드.**
 *
 * 종료 시각이 미공지라 주간 보드(시간 축)에 못 올린다. 예전엔 그래서 맨 아래 옅은 카드로 밀렸는데,
 * 한정 콜라보는 놓치면 되돌릴 수 없는 일정이라 가장 먼저 읽혀야 한다.
 *
 * 기본은 **간략형 한 줄 띠**다. 큰 배너로 세우면 이 페이지의 본론인 주간 보드를 첫 화면에서
 * 밀어낸다 — 눈에 띄어야 하는 것과 자리를 많이 차지하는 것은 다르다. 대신 픽업 목록을 다 보고
 * 싶을 때가 있어 펼치기를 남긴다. 고른 모드는 기기에 남는다([AppSettings.collabBannerExpanded])
 * — 한 번 접은 배너가 페이지를 열 때마다 펼쳐져 있으면 접은 의미가 없다.
 *
 * (지금 스타레일 × Fate 가 이 상태 — 상류 ennead 가 end_time 을 안 채운다.)
 */
@Composable
private fun CollabPromoBanner(pickups: List<GachaBanner>, expanded: Boolean, onToggle: () -> Unit) {
    val title = pickups.firstNotNullOfOrNull { collabTitle(it) } ?: "종료 미정 픽업"
    val started = pickups.filter { it.startMillis > 0 }.minOfOrNull { it.startMillis }
    val names = pickups.map { it.name }.filter { it.isNotBlank() }.distinct()
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(if (expanded) 20.dp else 14.dp))
            .background(Brush.linearGradient(listOf(CollabBadge, CollabGradientEnd)))
            .clickable { onToggle() }
            .animateContentSize()
            .padding(
                horizontal = if (expanded) 16.dp else 12.dp,
                vertical = if (expanded) 15.dp else 10.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 채운 배너 위에서는 배지를 **뒤집는다** — 보라 알약에 흰 글자면 배경에 묻힌다.
            Surface(color = Color.White, shape = RoundedCornerShape(999.dp)) {
                GlgBadgeTextPadded("콜라보", 9.sp, CollabBadge)
            }
            Spacer(Modifier.width(9.dp))
            if (expanded) {
                Text("진행 중", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
            } else {
                // 접힌 상태에선 제목·픽업이 한 줄 띠 안으로 들어간다.
                Column(Modifier.weight(1f)) {
                    Text(
                        title, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    // 이름이 길면 잘리되 **종료 미공지는 항상 남긴다** — 종료일을 아는 것처럼 비우면 안 된다.
                    Text(
                        (names + "종료 미정").joinToString(" · "),
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 접힘/펼침을 **글자로** 알린다. 화살표만 두면 이게 눌리는 것인지, 눌렀을 때 무엇이
            // 열리는지가 전달되지 않는다.
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                GlgBadgeText(if (expanded) "숨기기" else "보기", 9.5.sp, Color.White)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(9.dp))
            Text(
                title, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (names.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { n ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(999.dp))
                                        .background(Color.White.copy(alpha = 0.18f))
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        n, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
            Text(
                (if (started != null) "${DateUtil.shortDate(started)} 시작 · " else "") + "종료 시각 미공지",
                fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/** 알약 안 글자 — 줄 상자를 글자에 맞추고 좌우 여백만 준다. */
@Composable
private fun GlgBadgeTextPadded(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color) {
    Box(Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) {
        GlgBadgeText(text, size, color, fontWeight = FontWeight.Black)
    }
}


/**
 * 일정 페이지 맨 위의 호요랜드 줄 — 주간 표에 못 올라가는 오프라인 행사를 알리는 자리.
 *
 * 카드를 크게 만들지 않는다. 이 페이지의 본론은 픽업·이벤트 마감이고, 행사는 "그날 비워 둬라"
 * 한 마디면 충분하다 — 자세한 건 탭해서 호요랜드 페이지에서 본다.
 */
@Composable
private fun HoyolandScheduleBanner(event: HoyolandEvent, onOpen: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Celebration, null, tint = accent, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(event.edition, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(event.periodLongLabel, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(event.statusLabel(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
