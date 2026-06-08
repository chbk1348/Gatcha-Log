package com.gatcha.log.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.gatcha.log.ui.components.GlgPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GameEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.gatcha.log.ui.components.ListSkeleton
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBackButton
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgTabHeader
import com.gatcha.log.ui.spending.GameInfoAnchor
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.*

/** 게임정보 탭의 풀스크린 하위 페이지 (열리면 하단바·FAB 숨김) */
private enum class GiSub { Main, HoyoLink, Dashboard, Rate, Calc, Profile, Report, Gift, Schedule }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameInfoScreen(
    viewModel: SpendingViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onSubPageChange: (Boolean) -> Unit = {},
) {
    val accent = LocalAccent.current
    val banners by viewModel.activeBanners.collectAsState()
    val events by viewModel.gameEvents.collectAsState()
    val notes by viewModel.liveNotes.collectAsState()
    val ledgers by viewModel.ledgers.collectAsState()
    val combat by viewModel.combat.collectAsState()
    val attendanceToday by viewModel.attendanceToday.collectAsState()
    val attendanceHistory by viewModel.attendanceHistory.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val challenges by viewModel.challenges.collectAsState()
    val pity by viewModel.pity.collectAsState()
    val checkingIn by viewModel.checkingIn.collectAsState()
    val attendanceStreak by viewModel.attendanceStreak.collectAsState()
    // statusMessage 토스트는 상위 HomeScreen 의 전역 GlgStatusToast 가 처리
    val enkaGiUid by viewModel.enkaGiUid.collectAsState()
    val enkaHsrUid by viewModel.enkaHsrUid.collectAsState()
    val enkaResult by viewModel.enkaResult.collectAsState()
    val enkaLoading by viewModel.enkaLoading.collectAsState()
    val gachaStats by viewModel.gachaStats.collectAsState()
    val gachaDashboard by viewModel.gachaDashboard.collectAsState()
    val spendings by viewModel.spendings.collectAsState()
    val gachaSpendByGame = remember(spendings) {
        val m = mutableMapOf<String, Long>()
        spendings.filter { !it.isSubscription }.forEach { sp ->
            val key = when (sp.gameName) {
                "원신" -> "genshin"
                "붕괴: 스타레일" -> "starrail"
                "젠레스 존 제로" -> "zzz"
                else -> null
            }
            if (key != null) m[key] = (m[key] ?: 0L) + sp.amount
        }
        m
    }
    // Segmented 레이아웃 — 상단 게임 세그먼트 선택값("all" | game.key). 하위 섹션들이 이 값으로 필터된다.
    var gameFilter by remember { mutableStateOf("all") }
    // 통합 게임 일정(패치·이벤트·콘텐츠 병합) — 데일리 아래 첫 섹션.
    val schedule = remember(banners, events, challenges) { buildSchedule(banners, events, challenges) }
    // 게임정보 하위 풀스크린 페이지(연동 / 가챠 통계) — 열리면 상위(Scaffold)에 알려 하단바·FAB 숨김
    var subPage by remember { mutableStateOf(GiSub.Main) }
    LaunchedEffect(subPage) { onSubPageChange(subPage != GiSub.Main) }
    val redeemState by viewModel.redeemState.collectAsState()
    val activeCodes by viewModel.activeCodes.collectAsState()
    val codesLoading by viewModel.codesLoading.collectAsState()
    val redeemedCodes by viewModel.redeemedCodes.collectAsState()

    // 오늘 할 일/요약에서 넘어온 경우 해당 섹션으로 스크롤 앵커링(1회성).
    // 아이템 순서: 0 헤더 · 1 실시간노트(NOTES) · 2 spacer · 3 배너(BANNER) · 4 spacer ·
    // (패치 표시 시 5·6) · 위시 · spacer · 천장(PITY).
    val pendingAnchor by viewModel.pendingGameInfoAnchor.collectAsState()
    LaunchedEffect(pendingAnchor) {
        val anchor = pendingAnchor ?: return@LaunchedEffect
        val patchVisible = !(banners.isEmpty() && isRefreshing)
        val index = when (anchor) {
            GameInfoAnchor.NOTES -> 1
            GameInfoAnchor.BANNER -> 3
            GameInfoAnchor.PITY -> if (patchVisible) 9 else 7
        }
        listState.animateScrollToItem(index)
        viewModel.consumeGameInfoAnchor()
    }

    // HoYoLAB 연동 페이지 — 화면 스왑(게임정보 ↔ 연동) 슬라이드 push/pop
    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            if (targetState != GiSub.Main) {
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(220)))
            }
        },
        label = "giSubPage",
    ) { page ->
        when (page) {
            GiSub.HoyoLink -> HoyolabLinkScreen(
                config = hoyolab,
                onSave = {
                    viewModel.updateHoyolabConfig(it)
                    subPage = GiSub.Main
                    viewModel.refreshGameInfo(force = true)
                },
                onBack = { subPage = GiSub.Main },
            )
            GiSub.Dashboard -> GachaDashboardScreen(
                dashboard = gachaDashboard,
                spendByGameKey = gachaSpendByGame,
                onBack = { subPage = GiSub.Main },
            )
            GiSub.Rate -> GachaRatePage(onBack = { subPage = GiSub.Main })
            GiSub.Calc -> SectionPage(onBack = { subPage = GiSub.Main }) { GachaCalculatorSection(pity) }
            GiSub.Profile -> SectionPage(onBack = { subPage = GiSub.Main }) {
                ProfileShowcaseSection(
                    giUid = enkaGiUid,
                    hsrUid = enkaHsrUid,
                    hoyoGiUid = hoyolab.genshinUid,
                    hoyoHsrUid = hoyolab.hsrUid,
                    result = enkaResult,
                    loading = enkaLoading,
                    onLoad = { game, uid -> viewModel.loadEnkaProfile(game, uid) },
                    onGameChange = { viewModel.clearEnkaResult() },
                    onOpenHoyolab = { subPage = GiSub.HoyoLink },
                )
            }
            GiSub.Report -> SectionPage(onBack = { subPage = GiSub.Main }) {
                GachaReportSection(
                    stats = gachaStats,
                    spendByGameKey = gachaSpendByGame,
                    onImport = { uris -> viewModel.importGachaFromUris(uris) },
                    onClear = { viewModel.clearGachaRecords() },
                    onOpenDashboard = { subPage = GiSub.Dashboard },
                )
            }
            GiSub.Gift -> GiftCodePage(
                hoyolab = hoyolab,
                state = redeemState,
                activeCodes = activeCodes,
                codesLoading = codesLoading,
                redeemedCodes = redeemedCodes,
                onLoadCodes = { key -> viewModel.loadActiveCodes(key) },
                onRedeem = { key, c -> viewModel.redeemGiftCode(key, c) },
                onRedeemAll = { key -> viewModel.redeemAllCodes(key) },
                onBack = { subPage = GiSub.Main; viewModel.resetRedeem() },
            )
            GiSub.Schedule -> SectionPage(onBack = { subPage = GiSub.Main }) {
                GameScheduleFullContent(banners, events, challenges, gameFilter)
            }
            GiSub.Main -> GlgPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshGameInfo(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
            // 헤더 — 좌측 게임 드롭다운 + 우측 액션 버튼들 (게임 세그먼트를 헤더로 이관)
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameFilterDropdown(selected = gameFilter, onSelect = { gameFilter = it })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GachaRateButton { subPage = GiSub.Rate }
                        if (hoyolab.isLinked) {
                            GlgCircleIconButton(Icons.Default.Redeem, "리딤코드", outlined = true) { subPage = GiSub.Gift }
                        }
                        GlgCircleIconButton(Icons.Default.Refresh, "새로고침", enabled = !isRefreshing, outlined = true) {
                            viewModel.refreshGameInfo(force = true)
                        }
                        GlgCircleIconButton(Icons.Default.Settings, "HoYoLAB 설정", outlined = true) {
                            subPage = GiSub.HoyoLink
                        }
                    }
                }
            }
            // 최상단 히어로 — 실시간 노트 + 출석체크 통합
            item {
                DailyHeroSection(
                    notes = notes,
                    attendanceToday = attendanceToday,
                    attendanceHistory = attendanceHistory,
                    hoyolab = hoyolab,
                    checkingIn = checkingIn,
                    streak = attendanceStreak,
                    filter = gameFilter,
                    onCheckIn = { viewModel.attemptCheckIn(it) },
                    onCheckInAll = { viewModel.checkInAll() },
                    onConfigClick = { subPage = GiSub.HoyoLink },
                )
            }
            // 통합 게임 일정 — 데일리 바로 아래. 헤더 드롭다운(gameFilter) 연동.
            if (schedule.isNotEmpty()) {
                item { Spacer(Modifier.height(20.dp)) }
                item { GameScheduleSection(schedule, banners, gameFilter) { subPage = GiSub.Schedule } }
            }
            // 전투 진행도·수입 일지(게임 필터 연동). 픽업 배너는 게임 일정으로 통합돼 제외.
            item { Spacer(Modifier.height(20.dp)) }
            item {
                GameTabbedSection(
                    banners = banners,
                    combat = combat,
                    ledgers = ledgers,
                    isRefreshing = isRefreshing,
                    filter = gameFilter,
                )
            }
            // 페이지로 분류된 섹션(계산기·프로필·리포트) — 진입 카드
            item { Spacer(Modifier.height(20.dp)) }
            item { NavEntryCard(Icons.Default.Calculate, "가챠 계산기", "재화 환산 · 확률 · 시뮬레이터 · 플래너") { subPage = GiSub.Calc } }
            item { Spacer(Modifier.height(12.dp)) }
            item { NavEntryCard(Icons.Default.AccountBox, "프로필 쇼케이스", "Enka.Network UID로 캐릭터 조회") { subPage = GiSub.Profile } }
            item { Spacer(Modifier.height(12.dp)) }
            item { NavEntryCard(Icons.Default.BarChart, "가챠 효율 리포트", "UIGF/SRGF 분석 · 단가 · 천장 분포") { subPage = GiSub.Report } }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
        }
    }

}

/** 헤더 "가챠 확률표" 알약 버튼 (웹앱 gi-rate-btn 이식) */
@Composable
private fun GachaRateButton(onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(11.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Percent, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("확률표", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

/** 페이지로 분류된 섹션 진입 카드 (아이콘 + 제목 + 설명 + 셰브론). */
@Composable
private fun NavEntryCard(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(sub, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** 페이지로 분류된 섹션 래퍼 — 뒤로가기 + 섹션 자체 콘텐츠 스크롤. (섹션 내부 헤더 사용) */
@Composable
private fun SectionPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlgBackButton(onBack)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun EventSection(events: List<GameEvent>) {
    Text("진행 중인 이벤트", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    val byGame = events.groupBy { it.game }
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val gamesWithData = GameData.games.filter { byGame.containsKey(it.displayName) }
            gamesWithData.forEachIndexed { gi, game ->
                if (gi > 0) { Spacer(Modifier.height(14.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(10.dp)) }
                GameSubHeader(game)
                byGame[game.displayName].orEmpty().sortedBy { it.endMillis }.take(6).forEach { ev ->
                    ScheduleRow(ev.name, ev.reward, ev.endMillis, ev.dDayLabel())
                }
            }
        }
    }
}

/** 게임별 그룹 헤더 (컬러 점 + 게임 약칭). 이벤트·정기콘텐츠 섹션 공용. */
@Composable
private fun GameSubHeader(game: Game) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        Box(Modifier.size(8.dp).background(game.color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(game.shortName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = game.color)
    }
}

/** 이벤트·정기콘텐츠 한 줄 (이름 + 보조설명 / 종료일 ~M/d + D-day). */
@Composable
private fun ScheduleRow(name: String, sub: String, endMillis: Long, dDayLabel: String) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
            if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("~ ${DateUtil.shortDate(endMillis)}", fontSize = 11.sp, color = TextSecondary)
            Text(dDayLabel, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// ============================================================ 정기 콘텐츠
@Composable
fun ChallengeSection(challenges: List<GameChallenge>) {
    Text("정기 콘텐츠", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    val byGame = challenges.groupBy { it.game }
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val gamesWithData = GameData.games.filter { byGame.containsKey(it.displayName) }
            gamesWithData.forEachIndexed { gi, game ->
                if (gi > 0) { Spacer(Modifier.height(14.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(10.dp)) }
                GameSubHeader(game)
                byGame[game.displayName].orEmpty().sortedBy { it.endMillis }.take(6).forEach { ch ->
                    // 보조설명은 보상만 — ennead type_name(RoleCombat/Tower 등 영문 코드)은 가독성 떨어져 제외
                    ScheduleRow(ch.name, ch.reward, ch.endMillis, ch.dDayLabel())
                }
            }
        }
    }
}