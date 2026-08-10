package com.gatcha.log.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.gatcha.log.ui.components.GlgTabHeaderHeight
import com.gatcha.log.ui.components.glgTabContentBottom
import com.gatcha.log.ui.components.GlgTopScrimFadeExtra as ScrimFadeExtra
import com.gatcha.log.ui.components.GlgPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.ui.components.ListSkeleton
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.shareText
import com.gatcha.log.ui.components.openExternalLink
import com.gatcha.log.ui.components.GlgBackButton
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgHeaderTitlePill
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgTabHeader
import com.gatcha.log.data.GameInfoAnchor
import com.gatcha.log.data.ScheduleLogic
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.util.SafIO
import com.gatcha.log.ui.theme.*
import kotlinx.coroutines.launch

/** 게임정보 탭의 풀스크린 하위 페이지 (열리면 하단바·FAB 숨김) */
private enum class GiSub { Main, HoyoLink, Dashboard, Calc, Report, Gift, Schedule, News, NewsDetail, CharStats, CharRoster, Hoyoland, GameContent, CombatClear }

/** 화면 전환 push/pop 방향용 계층 깊이. Main=0, 하위 페이지=1, 상세(목록서 진입)=2. */
private fun subDepth(s: GiSub): Int = when (s) {
    GiSub.Main -> 0
    GiSub.CharStats, GiSub.NewsDetail, GiSub.CombatClear -> 2
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
    val banners by viewModel.activeBanners.collectAsStateWithLifecycle()
    val events by viewModel.gameEvents.collectAsStateWithLifecycle()
    val notes by viewModel.liveNotes.collectAsStateWithLifecycle()
    val gameNews by viewModel.gameNews.collectAsStateWithLifecycle()
    val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
    val combat by viewModel.combat.collectAsStateWithLifecycle()
    val combatClears by viewModel.combatClears.collectAsStateWithLifecycle()
    val combatClearsLoading by viewModel.combatClearsLoading.collectAsStateWithLifecycle()
    val attendanceToday by viewModel.attendanceToday.collectAsStateWithLifecycle()
    val attendanceHistory by viewModel.attendanceHistory.collectAsStateWithLifecycle()
    val hoyolab by viewModel.hoyolabConfig.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val challenges by viewModel.challenges.collectAsStateWithLifecycle()
    val pity by viewModel.pity.collectAsStateWithLifecycle()
    val checkingIn by viewModel.checkingIn.collectAsStateWithLifecycle()
    val attendanceStreak by viewModel.attendanceStreak.collectAsStateWithLifecycle()
    // statusMessage 토스트는 상위 HomeScreen 의 전역 GlgStatusToast 가 처리
    val enkaGiUid by viewModel.enkaGiUid.collectAsStateWithLifecycle()
    val enkaHsrUid by viewModel.enkaHsrUid.collectAsStateWithLifecycle()
    val enkaResult by viewModel.enkaResult.collectAsStateWithLifecycle()
    val enkaLoading by viewModel.enkaLoading.collectAsStateWithLifecycle()
    val gachaStats by viewModel.gachaStats.collectAsStateWithLifecycle()
    val gachaDashboard by viewModel.gachaDashboard.collectAsStateWithLifecycle()
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()
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
    val schedule = remember(banners, events, challenges) { ScheduleLogic.buildSchedule(banners, events, challenges) }
    val taskStats by viewModel.taskStats.collectAsStateWithLifecycle()
    val keyStatOverrides by viewModel.keyStatOverrides.collectAsStateWithLifecycle()
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
    // 클리어 편성 — 메인의 진입 카드와 '전투 · 수입 일지' 양쪽에서 들어온다. 뒤로 갈 위치를 기억해야
    // 메인에서 들어왔는데 일지로 튀어나오는 일이 없다.
    var clearsReturn by remember { mutableStateOf(GiSub.Main) }
    val openNews: (NewsItem, GiSub) -> Unit = { n, from ->
        newsItem = n
        newsReturn = from
        subPage = GiSub.NewsDetail
    }
    LaunchedEffect(subPage) { onSubPageChange(subPage != GiSub.Main) }

    // 공지 알림 딥링크 — 알림에 실린 id 로 목록에서 글을 찾아 상세를 연다.
    // 알림을 탭한 직후엔 목록이 아직 비어 있을 수 있어(콜드 스타트) news 가 도착할 때까지 기다렸다 연다.
    val pendingNewsId by viewModel.pendingNewsId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNewsId, gameNews) {
        val id = pendingNewsId ?: return@LaunchedEffect
        val target = gameNews.firstOrNull { it.id == id } ?: return@LaunchedEffect
        viewModel.consumePendingNews()
        openNews(target, GiSub.Main)
    }
    val redeemState by viewModel.redeemState.collectAsStateWithLifecycle()
    val activeCodes by viewModel.activeCodes.collectAsStateWithLifecycle()
    val codesLoading by viewModel.codesLoading.collectAsStateWithLifecycle()
    val codesFailed by viewModel.codesFailed.collectAsStateWithLifecycle()
    val redeemedCodes by viewModel.redeemedCodes.collectAsStateWithLifecycle()

    // 홈 대시보드 카드에서 넘어온 경우 해당 섹션으로 스크롤 앵커링(1회성).
    //
    // 섹션은 아래 LazyColumn 에 [스페이서, 본문] 2칸씩 쌓이고 일부는 조건부(미연동·일정 없음)라,
    // 앞 섹션이 빠지면 뒤 인덱스가 통째로 당겨진다. 그래서 하드코딩하지 않고 같은 순서로 누적 계산한다.
    // (예전엔 하드코딩이라 호요랜드 섹션이 끼어든 뒤 NEWS 앵커가 호요랜드로 어긋나 있었다)
    val pendingAnchor by viewModel.pendingGameInfoAnchor.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAnchor) {
        val anchor = pendingAnchor ?: return@LaunchedEffect
        val linked = hoyolab.isLinked
        val scheduleShown = schedule.isNotEmpty()
        var cursor = 1                                       // 0 헤더 스페이서 · 1 데일리
        val notesIdx = cursor                                // 섹션이 없을 때의 공통 폴백
        if (linked) cursor += 2                              // 내 캐릭터
        val scheduleIdx = if (scheduleShown) cursor + 2 else notesIdx
        if (scheduleShown) cursor += 2                       // 게임 일정
        cursor += 2                                          // 주년
        cursor += 2                                          // 호요랜드
        val newsIdx = cursor + 2
        cursor += 2                                          // 공지
        // 전투 진행도는 본문 섹션이 아니라 데일리에서 들어가는 상세 페이지로 옮겼다 → 스크롤 대신 페이지 진입.
        if (anchor == GameInfoAnchor.COMBAT) {
            subPage = GiSub.GameContent
            viewModel.consumeGameInfoAnchor()
            return@LaunchedEffect
        }
        val index = when (anchor) {
            GameInfoAnchor.SCHEDULE -> scheduleIdx
            GameInfoAnchor.NEWS -> newsIdx
            else -> notesIdx
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
                if (c != null) {
                    EnkaStatPage(
                        c, statCharGame,
                        overrides = keyStatOverrides,
                        onSetOverride = { k, v -> viewModel.setKeyStatOverride(k, v) },
                    ) { subPage = statReturn }
                }
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
            GiSub.GameContent -> SectionPage("전투 · 수입 일지", onBack = { subPage = GiSub.Main }) {
                GameTabbedSection(
                    banners = banners,
                    combat = combat,
                    ledgers = ledgers,
                    isRefreshing = isRefreshing,
                    filter = gameFilter,
                    linked = hoyolab.isLinked,
                    onOpenClears = { clearsReturn = GiSub.GameContent; subPage = GiSub.CombatClear },
                )
            }
            GiSub.CombatClear -> SectionPage("클리어 편성", onBack = { subPage = clearsReturn }) {
                // 진입할 때 받는다 — 시즌 2개치라 무거워서 게임정보 새로고침에 얹지 않았다.
                LaunchedEffect(Unit) { viewModel.refreshCombatClears() }
                CombatClearContent(
                    clears = combatClears,
                    loading = combatClearsLoading,
                    linked = hoyolab.isLinked,
                )
            }
            GiSub.Schedule -> SectionPage("게임 일정", onBack = { subPage = GiSub.Main }) {
                GameScheduleFullContent(banners, events, challenges, gameFilter)
            }
            GiSub.NewsDetail -> SectionPage(
                "공지",
                onBack = { subPage = newsReturn; viewModel.clearNewsArticle() },
                actions = {
                    val n = newsItem
                    if (n != null && n.url.isNotBlank()) {
                        // 공유 — **링크만** 보낸다. 본문은 앱이 재구성한 것이라 그대로 보낼 수 없고,
                        // 제목을 붙이면 받는 쪽 미리보기와 중복돼 지저분해진다.
                        // solidBackground — 본문(공지 이미지)이 헤더 아래를 지나가므로 버튼이 비치면 안 된다.
                        GlgCircleIconButton(Icons.Default.Share, "공유", outlined = true, solidBackground = true) {
                            shareText(context, n.url)
                        }
                        // 브라우저 — 표·동영상처럼 앱이 못 살리는 요소는 원문에서 봐야 한다.
                        GlgCircleIconButton(Icons.AutoMirrored.Filled.OpenInNew, "브라우저에서 보기", outlined = true, solidBackground = true) {
                            openExternalLink(context, n.url)
                        }
                    }
                },
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
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // 상단 스크림 — 콘텐츠가 헤더(버튼) 아래로 스크롤될 때만 배경색 그라데이션으로 살짝 흐린다.
            // 최상단에선 숨겨 화면을 넓게 쓰고, 스크롤 중에는 버튼 뒤로 지나가는 글자가 겹쳐 읽히지 않게 한다.
            // (지출 탭과 같은 규격)
            val scrolled by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            }
            val topScrimAlpha by animateFloatAsState(if (scrolled) 0.88f else 0f, label = "topScrim")
            GlgPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshGameInfo(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = glgTabContentBottom()),
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
                    onOpenGameContent = { subPage = GiSub.GameContent },
                    onOpenClears = { clearsReturn = GiSub.Main; subPage = GiSub.CombatClear },
                )
            }
            // 숙제 완주율 — 데일리 바로 아래(같은 '오늘 뭐 했나' 맥락). 기록이 없으면 섹션 자체가 안 뜬다.
            if (taskStats.isNotEmpty()) {
                item { Spacer(Modifier.height(20.dp)) }
                item { TaskCompletionSection(taskStats) }
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
                item { GameScheduleSection(schedule, banners, gameFilter, onSeeAll = { subPage = GiSub.Schedule }) }
            }
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
            // 이환 캐릭터 도감 — 이 게임만 공지 API 가 없어 '공지·뉴스'에 못 낀다(NteCharSection KDoc).
            // 상단 여백은 섹션이 직접 낸다 — 데이터가 없으면 통째로 사라져야 빈 여백이 안 남는다.
            item { NteCharSection(viewModel, gameFilter) }
            item { Spacer(Modifier.height(20.dp)) }
            item { NavEntryCard(Icons.Default.Calculate, "가챠 계산기", "재화 환산 · 확률 · 시나리오") { subPage = GiSub.Calc } }
            item { Spacer(Modifier.height(12.dp)) }
            item { NavEntryCard(Icons.Default.BarChart, "가챠 효율 리포트", "UIGF/SRGF 분석 · 단가 · 천장 분포") { subPage = GiSub.Report } }
            // 목록 끝에는 여백을 두지 않는다 — 탭바까지의 간격은 contentPadding 이 전담(예전엔 32dp).
        }
    }
            // 상단 스크림 — **상태바 영역만** 덮는다(헤더 버튼 줄은 그대로 투명).
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(topInset + ScrimFadeExtra)
                    .graphicsLayer { alpha = topScrimAlpha }
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White,
                            0.35f to Color.White,
                            1f to Color.Transparent,
                        ),
                    ),
            )
            // 헤더 오버레이 — 투명 바, 버튼만 불투명. 콘텐츠가 버튼 아래로 지나간다. 상태바 인셋 적용.
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
                GlgTabHeader(
                    "",
                    leading = { GameFilterDropdown(selected = gameFilter, onSelect = { gameFilter = it }) },
                ) {
                    // 순서: 새로고침 → 리딤코드 → 설정.
                    GlgCircleIconButton(Icons.Default.Refresh, "새로고침", enabled = !isRefreshing, outlined = true, solidBackground = true) {
                        viewModel.refreshGameInfo(force = true)
                    }
                    if (hoyolab.isLinked) {
                        GlgCircleIconButton(Icons.Default.Redeem, "리딤코드", outlined = true, solidBackground = true) { subPage = GiSub.Gift }
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
private fun SectionPage(
    title: String,
    onBack: () -> Unit,
    /** 헤더 우측 액션(공유·브라우저 등). 없으면 제목만. */
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    BackHandler { onBack() }
    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = glgDetailContentTop()),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
        GlgDetailHeaderOverlay(title, onBack, scrollState = scrollState, actions = actions)
    }
}
