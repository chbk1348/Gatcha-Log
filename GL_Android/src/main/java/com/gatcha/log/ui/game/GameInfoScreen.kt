package com.gatcha.log.ui.game

import androidx.compose.animation.AnimatedContent
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
import com.gatcha.log.ui.components.GlgTabHeaderHeight
import com.gatcha.log.ui.components.GlgPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.gatcha.log.data.GameInfoAnchor
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.util.SafIO
import com.gatcha.log.ui.theme.*
import kotlinx.coroutines.launch

/** 게임정보 탭의 풀스크린 하위 페이지 (열리면 하단바·FAB 숨김) */
private enum class GiSub { Main, HoyoLink, Dashboard, Calc, Report, Gift, Schedule, Pickups, News, NewsDetail, CharStats, CharRoster, Hoyoland }

/** 화면 전환 push/pop 방향용 계층 깊이. Main=0, 하위 페이지=1, 상세(목록서 진입)=2. */
private fun subDepth(s: GiSub): Int = when (s) {
    GiSub.Main -> 0
    GiSub.CharStats, GiSub.NewsDetail -> 2
    else -> 1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameInfoScreen(
    viewModel: SpendingViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onSubPageChange: (Boolean) -> Unit = {},
) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val banners by viewModel.activeBanners.collectAsState()
    val events by viewModel.gameEvents.collectAsState()
    val notes by viewModel.liveNotes.collectAsState()
    val gameNews by viewModel.gameNews.collectAsState()
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
    // Enka 캐릭터 스탯 페이지 랜딩 대상
    var statChar by remember { mutableStateOf<EnkaChar?>(null) }
    var statCharGame by remember { mutableStateOf("genshin") }
    var rosterGame by remember { mutableStateOf("genshin") }
    // 스탯 상세에서 뒤로 갈 위치(섹션=Main, 보유목록=CharRoster)
    var statReturn by remember { mutableStateOf(GiSub.Main) }
    // 공지 상세 — 대상 글과, 뒤로 갈 위치(섹션=Main, 전체목록=News)
    var newsItem by remember { mutableStateOf<NewsItem?>(null) }
    var newsReturn by remember { mutableStateOf(GiSub.Main) }
    val openNews: (NewsItem, GiSub) -> Unit = { n, from ->
        newsItem = n
        newsReturn = from
        subPage = GiSub.NewsDetail
    }
    LaunchedEffect(subPage) { onSubPageChange(subPage != GiSub.Main) }
    val redeemState by viewModel.redeemState.collectAsState()
    val activeCodes by viewModel.activeCodes.collectAsState()
    val codesLoading by viewModel.codesLoading.collectAsState()
    val codesFailed by viewModel.codesFailed.collectAsState()
    val redeemedCodes by viewModel.redeemedCodes.collectAsState()

    // 홈 대시보드 카드에서 넘어온 경우 해당 섹션으로 스크롤 앵커링(1회성).
    // LazyColumn item 순서: 0 헤더 · 1 실시간노트(NOTES) · 2 spacer · 3 내캐릭터 ·
    // [일정 있을 때 4 spacer · 5 게임일정(SCHEDULE)] · spacer · 주년 · spacer · 공지(NEWS) · …
    val pendingAnchor by viewModel.pendingGameInfoAnchor.collectAsState()
    LaunchedEffect(pendingAnchor) {
        val anchor = pendingAnchor ?: return@LaunchedEffect
        val scheduleShown = schedule.isNotEmpty()
        val index = when (anchor) {
            GameInfoAnchor.NOTES -> 1
            GameInfoAnchor.SCHEDULE -> if (scheduleShown) 5 else 1  // 일정 미표시 시 상단(노트)로 폴백
            GameInfoAnchor.NEWS -> if (scheduleShown) 9 else 7
        }
        listState.animateScrollToItem(index)
        viewModel.consumeGameInfoAnchor()
    }

    // HoYoLAB 연동 페이지 — 화면 스왑(게임정보 ↔ 연동) 슬라이드 push/pop
    val subPageStateHolder = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            // 계층 깊이로 push/pop 방향 결정 (Main=0 < 하위=1 < 상세(CharStats)=2)
            if (subDepth(targetState) >= subDepth(initialState)) {
                (slideInHorizontally(glgStandardSpec()) { it } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { -it / 4 } + fadeOut(glgShortSpec()))
            } else {
                (slideInHorizontally(glgStandardSpec()) { -it / 4 } + fadeIn(glgStandardSpec())) togetherWith
                    (slideOutHorizontally(glgStandardSpec()) { it } + fadeOut(glgShortSpec()))
            }
        },
        label = "giSubPage",
    ) { page ->
        subPageStateHolder.SaveableStateProvider(page) {
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
            GiSub.CharStats -> {
                val c = statChar
                if (c != null) EnkaStatPage(c, statCharGame) { subPage = statReturn }
            }
            GiSub.CharRoster -> EnkaRosterPage(
                viewModel, rosterGame,
                onBack = { subPage = GiSub.Main },
                onOpenStats = { c, g -> statChar = c; statCharGame = g; statReturn = GiSub.CharRoster; subPage = GiSub.CharStats },
            )
            GiSub.Calc -> SectionPage("가챠 계산기", onBack = { subPage = GiSub.Main }) { GachaCalculatorSection(pity) }
            GiSub.Report -> SectionPage("가챠 효율 리포트", onBack = { subPage = GiSub.Main }) {
                GachaReportSection(
                    stats = gachaStats,
                    spendByGameKey = gachaSpendByGame,
                    onImport = { uris -> scope.launch { viewModel.importGachaFromContents(SafIO.readTexts(context, uris)) } },
                    onClear = { viewModel.clearGachaRecords() },
                    onOpenDashboard = { subPage = GiSub.Dashboard },
                )
            }
            GiSub.Gift -> GiftCodePage(
                hoyolab = hoyolab,
                state = redeemState,
                activeCodes = activeCodes,
                codesLoading = codesLoading,
                codesFailed = codesFailed,
                redeemedCodes = redeemedCodes,
                onLoadCodes = { key -> viewModel.loadActiveCodes(key) },
                onRedeem = { key, c -> viewModel.redeemGiftCode(key, c) },
                onRedeemAll = { key -> viewModel.redeemAllCodes(key) },
                onBack = { subPage = GiSub.Main; viewModel.resetRedeem() },
            )
            GiSub.Schedule -> SectionPage("게임 일정", onBack = { subPage = GiSub.Main }) {
                GameScheduleFullContent(banners, events, challenges, gameFilter)
            }
            GiSub.Pickups -> SectionPage("전체 픽업", onBack = { subPage = GiSub.Main }) {
                GamePickupFullContent(banners, gameFilter)
            }
            GiSub.NewsDetail -> SectionPage(
                "공지",
                onBack = { subPage = newsReturn; viewModel.clearNewsArticle() },
            ) {
                val n = newsItem
                if (n != null) NewsDetailContent(viewModel, n)
            }

            GiSub.News -> SectionPage("공지·뉴스", onBack = { subPage = GiSub.Main }) {
                NewsFullContent(gameNews, gameFilter, onOpen = { openNews(it, GiSub.News) })
            }
            GiSub.Hoyoland -> SectionPage("호요랜드", onBack = { subPage = GiSub.Main }) {
                HoyolandDetailContent()
            }
            GiSub.Main -> Box(Modifier.fillMaxSize()) {
            GlgPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshGameInfo(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
            // 헤더 자리(고정) — item 0 은 앵커 인덱스 유지용 스페이서. 실제 헤더는 아래 오버레이. (상태바+헤더)
            item { Spacer(Modifier.height(GlgTabHeaderHeight + topInset)) }
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
            // 내 캐릭터(보유 전체 로스터) — 데일리 다음. 미연동이면 섹션·상단 여백까지 통째 생략(빈 여백 방지).
            if (hoyolab.isLinked) {
                item { Spacer(Modifier.height(20.dp)) }
                item {
                    EnkaCharSection(
                        viewModel,
                        gameFilter = gameFilter,
                        onOpenStats = { c, g -> statChar = c; statCharGame = g; statReturn = GiSub.Main; subPage = GiSub.CharStats },
                        // 더보기로 새로 진입 시엔 보유목록 상태(스크롤/필터) 초기화 — 상세→뒤로 복귀는 SaveableStateProvider 가 유지
                        onOpenAll = { g -> rosterGame = g; subPageStateHolder.removeState(GiSub.CharRoster); subPage = GiSub.CharRoster },
                        onOpenHoyolab = { subPage = GiSub.HoyoLink },
                    )
                }
            }
            // 통합 게임 일정 — 헤더 드롭다운(gameFilter) 연동.
            if (schedule.isNotEmpty()) {
                item { Spacer(Modifier.height(20.dp)) }
                item { GameScheduleSection(schedule, banners, gameFilter, onSeeAll = { subPage = GiSub.Schedule }, onSeePickups = { subPage = GiSub.Pickups }) }
            }
            // 게임 주년 — 지원 게임의 다가오는 주년(임박 순).
            item { Spacer(Modifier.height(20.dp)) }
            item { AnniversarySection() }
            // 호요랜드 — 호요버스 한국 오프라인 행사(플레이스홀더). 정보 확정 전 "준비 중" 티저.
            item { Spacer(Modifier.height(20.dp)) }
            item { HoyolandSection(onOpen = { subPage = GiSub.Hoyoland }) }
            // 공지·뉴스 — 게임별 최신 공지(탭하면 HoYoLab 열기).
            item { Spacer(Modifier.height(20.dp)) }
            item {
                NewsSection(
                    gameNews, gameFilter,
                    onSeeAll = { subPage = GiSub.News },
                    onOpen = { openNews(it, GiSub.Main) },
                )
            }
            // 전투 진행도·수입 일지. 미연동이면 데이터가 없어 섹션·상단 여백까지 통째 생략.
            if (hoyolab.isLinked) {
                item { Spacer(Modifier.height(20.dp)) }
                item {
                    GameTabbedSection(
                        banners = banners,
                        combat = combat,
                        ledgers = ledgers,
                        isRefreshing = isRefreshing,
                        filter = gameFilter,
                        linked = hoyolab.isLinked,
                    )
                }
            }
            // 페이지로 분류된 섹션(계산기·프로필·리포트) — 진입 카드
            item { Spacer(Modifier.height(20.dp)) }
            item { NavEntryCard(Icons.Default.Calculate, "가챠 계산기", "재화 환산 · 확률 · 시나리오") { subPage = GiSub.Calc } }
            item { Spacer(Modifier.height(12.dp)) }
            item { NavEntryCard(Icons.Default.BarChart, "가챠 효율 리포트", "UIGF/SRGF 분석 · 단가 · 천장 분포") { subPage = GiSub.Report } }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
            // 헤더 오버레이 — 투명 바, 버튼만 불투명. 콘텐츠가 버튼 아래로 지나간다. 상태바 인셋 적용.
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
                GlgTabHeader(
                    "",
                    leading = { GameFilterDropdown(selected = gameFilter, onSelect = { gameFilter = it }) },
                ) {
                    if (hoyolab.isLinked) {
                        GlgCircleIconButton(Icons.Default.Redeem, "리딤코드", outlined = true, solidBackground = true) { subPage = GiSub.Gift }
                    }
                    GlgCircleIconButton(Icons.Default.Refresh, "새로고침", enabled = !isRefreshing, outlined = true, solidBackground = true) {
                        viewModel.refreshGameInfo(force = true)
                    }
                    GlgCircleIconButton(Icons.Default.Settings, "HoYoLAB 설정", outlined = true, solidBackground = true) {
                        subPage = GiSub.HoyoLink
                    }
                }
            }
            }
        }
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
private fun SectionPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        // 뒤로가기 + 타이틀(모든 상세 페이지 공통 노출).
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlgBackButton(onBack)
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}
