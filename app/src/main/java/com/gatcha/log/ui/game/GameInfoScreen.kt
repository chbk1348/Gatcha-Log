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
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.MonthlyLedger
import com.gatcha.log.data.NoteStat
import com.gatcha.log.data.PatchInfo
import com.gatcha.log.data.PityState
import com.gatcha.log.ui.components.BannerSkeleton
import com.gatcha.log.ui.components.ListSkeleton
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.ui.spending.RedeemState
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.*

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
    val wishlist by viewModel.wishlist.collectAsState()
    val pity by viewModel.pity.collectAsState()
    val checkingIn by viewModel.checkingIn.collectAsState()
    val attendanceStreak by viewModel.attendanceStreak.collectAsState()
    // statusMessage 토스트는 상위 HomeScreen 의 전역 GlgStatusToast 가 처리
    val enkaGiUid by viewModel.enkaGiUid.collectAsState()
    val enkaHsrUid by viewModel.enkaHsrUid.collectAsState()
    val enkaResult by viewModel.enkaResult.collectAsState()
    val enkaLoading by viewModel.enkaLoading.collectAsState()
    val gachaStats by viewModel.gachaStats.collectAsState()
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
    val showHoyolabDialog = remember { mutableStateOf(false) }
    // HoYoLAB 연동 페이지(풀스크린)가 열리면 상위(Scaffold)에 알려 하단바·FAB를 숨김
    LaunchedEffect(showHoyolabDialog.value) { onSubPageChange(showHoyolabDialog.value) }
    val showRateDialog = remember { mutableStateOf(false) }
    val showGiftDialog = remember { mutableStateOf(false) }
    val redeemState by viewModel.redeemState.collectAsState()
    val activeCodes by viewModel.activeCodes.collectAsState()
    val codesLoading by viewModel.codesLoading.collectAsState()
    val redeemedCodes by viewModel.redeemedCodes.collectAsState()

    // HoYoLAB 연동 페이지 — 화면 스왑(게임정보 ↔ 연동) 슬라이드 push/pop
    AnimatedContent(
        targetState = showHoyolabDialog.value,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(220)))
            }
        },
        label = "hoyoLink",
    ) { link ->
        if (link) {
            HoyolabLinkScreen(
                config = hoyolab,
                onSave = {
                    viewModel.updateHoyolabConfig(it)
                    showHoyolabDialog.value = false
                    viewModel.refreshGameInfo()
                },
                onBack = { showHoyolabDialog.value = false },
            )
        } else GlgPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshGameInfo() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("게임 정보", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GachaRateButton { showRateDialog.value = true }
                        if (hoyolab.isLinked) {
                            GlgCircleIconButton(Icons.Default.Redeem, "선물코드", outlined = true) { showGiftDialog.value = true }
                        }
                        GlgCircleIconButton(Icons.Default.Refresh, "새로고침", loading = isRefreshing, enabled = !isRefreshing, outlined = true) {
                            viewModel.refreshGameInfo()
                        }
                        GlgCircleIconButton(Icons.Default.Settings, "HoYoLAB 설정", outlined = true) {
                            showHoyolabDialog.value = true
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
                    onCheckIn = { viewModel.attemptCheckIn(it) },
                    onConfigClick = { showHoyolabDialog.value = true },
                )
            }
            // 배너·전투 진행도·수입 일지를 게임 칩으로 번갈아 보는 통합 섹션
            item { Spacer(Modifier.height(20.dp)) }
            item {
                GameTabbedSection(
                    banners = banners,
                    combat = combat,
                    ledgers = ledgers,
                    isRefreshing = isRefreshing,
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            if (!(banners.isEmpty() && isRefreshing)) {
                item { PatchSection(banners) }
                item { Spacer(Modifier.height(20.dp)) }
            }
            item {
                WishlistSection(
                    wishlist = wishlist,
                    onAdd = viewModel::addWish,
                    onRemove = viewModel::removeWish,
                    isPickedUp = viewModel::isWishPickedUp,
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                PitySection(
                    pity = pity,
                    onAdjust = viewModel::adjustPity,
                    onReset = viewModel::resetPity,
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            item { GachaCalculatorSection(pity) }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                ProfileShowcaseSection(
                    giUid = enkaGiUid,
                    hsrUid = enkaHsrUid,
                    result = enkaResult,
                    loading = enkaLoading,
                    onLoad = { game, uid -> viewModel.loadEnkaProfile(game, uid) },
                    onGameChange = { viewModel.clearEnkaResult() },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                GachaReportSection(
                    stats = gachaStats,
                    spendByGameKey = gachaSpendByGame,
                    onImport = { uris -> viewModel.importGachaFromUris(uris) },
                    onClear = { viewModel.clearGachaRecords() },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            if (banners.isEmpty() && isRefreshing) {
                item { ListSkeleton(rows = 3) }
                item { Spacer(Modifier.height(20.dp)) }
                item { ListSkeleton(rows = 4) }
            } else {
                if (challenges.isNotEmpty()) {
                    item { ChallengeSection(challenges) }
                    item { Spacer(Modifier.height(20.dp)) }
                }
                if (events.isNotEmpty()) {
                    item { EventSection(events) }
                }
            }
        }
    }
    }

    if (showRateDialog.value) {
        GachaRateDialog(onDismiss = { showRateDialog.value = false })
    }

    if (showGiftDialog.value) {
        GiftCodeDialog(
            hoyolab = hoyolab,
            state = redeemState,
            activeCodes = activeCodes,
            codesLoading = codesLoading,
            redeemedCodes = redeemedCodes,
            onLoadCodes = { key -> viewModel.loadActiveCodes(key) },
            onRedeem = { key, code -> viewModel.redeemGiftCode(key, code) },
            onRedeemAll = { key -> viewModel.redeemAllCodes(key) },
            onDismiss = { showGiftDialog.value = false; viewModel.resetRedeem() },
        )
    }
}

/** HoYoLAB 선물코드 — 활성 코드 자동 수집 + 교환(단건/모두) + 직접 입력. */
@Composable
private fun GiftCodeDialog(
    hoyolab: HoyolabConfig,
    state: RedeemState,
    activeCodes: List<GiftCode>,
    codesLoading: Boolean,
    redeemedCodes: Set<String>,
    onLoadCodes: (String) -> Unit,
    onRedeem: (String, String) -> Unit,
    onRedeemAll: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccent.current
    val games = remember(hoyolab) {
        buildList {
            if (hoyolab.genshinUid.isNotBlank()) add("genshin" to "원신")
            if (hoyolab.hsrUid.isNotBlank()) add("hsr" to "스타레일")
            if (hoyolab.zzzUid.isNotBlank()) add("zzz" to "젠레스")
        }
    }
    var selected by remember { mutableStateOf(games.firstOrNull()?.first ?: "genshin") }
    var code by remember { mutableStateOf("") }
    val loading = state is RedeemState.Loading
    // 선택 게임 바뀌면(최초 포함) 활성 코드 자동 수집
    LaunchedEffect(selected) { if (games.isNotEmpty()) onLoadCodes(selected) }
    val pending = activeCodes.count { it.code !in redeemedCodes }

    GlgDialog(
        title = "선물코드",
        onDismiss = onDismiss,
        confirmText = if (loading) "교환 중…" else "모두 교환",
        confirmEnabled = pending > 0 && !loading && games.isNotEmpty(),
        onConfirm = { onRedeemAll(selected) },
        dismissText = "닫기",
    ) {
        Column {
            if (games.isEmpty()) {
                Text("HoYoLAB 연동 후 UID가 있어야 코드를 교환할 수 있어요", fontSize = 13.sp, color = TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    games.forEach { (key, label) ->
                        val sel = key == selected
                        Surface(
                            modifier = Modifier.clickable { selected = key },
                            shape = RoundedCornerShape(20.dp),
                            color = if (sel) accent else Color(0xFFF2F2F6),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("활성 코드 (자동 수집)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                when {
                    codesLoading -> Text("코드 불러오는 중…", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                    activeCodes.isEmpty() -> Text("지금은 활성 코드가 없어요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                    else -> activeCodes.forEach { c ->
                        CodeRow(c, redeemed = c.code in redeemedCodes, accent = accent, enabled = !loading) { onRedeem(selected, c.code) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlgTextField(
                    value = code,
                    onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() } },
                    label = "직접 입력 (새 코드)",
                    placeholder = "예: GENSHINGIFT",
                )
                if (code.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.clickable(enabled = !loading) { onRedeem(selected, code.trim()); code = "" },
                        shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    ) {
                        Text("이 코드 교환", modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (state) {
                    is RedeemState.Loading -> Text("교환 중…", fontSize = 12.sp, color = TextSecondary)
                    is RedeemState.Done -> Text(
                        state.message,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (state.success) accent else DangerText,
                    )
                    else -> Text(
                        if (hoyolab.cookieToken.isBlank() && hoyolab.webCookie.isBlank()) "교환하려면 HoYoLAB 재연동(이메일 로그인)이 필요해요. 보상은 게임 우편함으로 와요."
                        else "코드를 눌러 교환하거나 '모두 교환'을 누르세요. 보상은 게임 우편함으로 와요.",
                        fontSize = 11.sp, color = TextSecondary,
                    )
                }
            }
        }
    }
}

/** 활성 코드 한 줄 — 코드 + 보상 + (교환/받음). */
@Composable
private fun CodeRow(c: GiftCode, redeemed: Boolean, accent: Color, enabled: Boolean, onRedeem: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                c.code,
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (redeemed) TextSecondary else TextPrimary,
                textDecoration = if (redeemed) TextDecoration.LineThrough else null,
            )
            if (c.rewards.isNotBlank()) Text(c.rewards, fontSize = 11.sp, color = TextSecondary, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        if (redeemed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text("받음", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(
                modifier = Modifier.clickable(enabled = enabled) { onRedeem() },
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
            ) {
                Text("교환", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
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

@Composable
fun EventSection(events: List<GameEvent>) {
    Text("진행 중인 이벤트", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    val byGame = events.groupBy { it.game }
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
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

// ============================================================ 데일리 히어로 (실시간 노트 + 출석체크 통합)
/**
 * 최상단 히어로 카드. HoYoLAB 미연동 시에는 출석·실시간 노트를 숨기고 연동을 유도하고,
 * 연동되면 게임별 실시간 노트(레진/개척력/배터리)와 출석체크를 한 카드에 통합해 보여준다.
 */
@Composable
private fun DailyHeroSection(
    notes: List<LiveNote>,
    attendanceToday: Set<String>,
    attendanceHistory: Map<String, Set<String>>,
    hoyolab: HoyolabConfig,
    checkingIn: String?,
    streak: Int,
    onCheckIn: (String) -> Unit,
    onConfigClick: () -> Unit,
) {
    val accent = LocalAccent.current
    var showCalendar by remember { mutableStateOf(false) }

    if (!hoyolab.isLinked) {
        GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Link, null, tint = accent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("HoYoLAB 연동이 필요해요", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "연동하면 실시간 노트(레진·개척력·배터리)와\n출석체크를 한곳에서 관리할 수 있어요.",
                    fontSize = 12.sp, color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(18.dp))
                GlgButton("HoYoLAB 연동하기", onClick = onConfigClick, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("오늘의 데일리", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (streak > 0) {
                        Spacer(Modifier.width(8.dp))
                        StreakChip(streak)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f))
                            .clickable { showCalendar = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.CalendarMonth, "출석 달력", tint = accent, modifier = Modifier.size(16.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onConfigClick() },
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("연동됨", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
            }
            // 최근 7일 출석 스트립
            Spacer(Modifier.height(14.dp))
            WeekAttendanceStrip(attendanceHistory)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(4.dp))
            GameData.attendanceGames.forEachIndexed { i, game ->
                val note = notes.firstOrNull { GameData.byNameOrNull(it.game)?.key == game.key }
                val uid = when (game.key) {
                    "genshin" -> hoyolab.genshinUid
                    "hsr" -> hoyolab.hsrUid
                    "zzz" -> hoyolab.zzzUid
                    else -> ""
                }
                DailyGameRow(game, note, uid, game.key in attendanceToday, checkingIn == game.key) { onCheckIn(game.key) }
                if (i < GameData.attendanceGames.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }

    if (showCalendar) {
        MonthAttendanceDialog(attendanceHistory) { showCalendar = false }
    }
}

/** 출석 완료도: 모든 게임 출석=full, 일부=partial, 없음=none */
private enum class AttendLevel { NONE, PARTIAL, FULL }

private fun attendLevel(count: Int): AttendLevel = when {
    count <= 0 -> AttendLevel.NONE
    count >= GameData.attendanceGames.size -> AttendLevel.FULL
    else -> AttendLevel.PARTIAL
}

private fun dowKo(cal: java.util.Calendar): String =
    arrayOf("일", "월", "화", "수", "목", "금", "토")[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]

/** 최근 7일 출석 스트립 (오늘 = 맨 오른쪽). */
@Composable
private fun WeekAttendanceStrip(history: Map<String, Set<String>>) {
    val accent = LocalAccent.current
    val days = remember(history) {
        (6 downTo 0).map { offset ->
            val cal = DateUtil.hoyoCalendar().apply { add(java.util.Calendar.DAY_OF_YEAR, -offset) }
            Triple(cal.get(java.util.Calendar.DAY_OF_MONTH), dowKo(cal), history[DateUtil.hoyoDayKey(cal.timeInMillis)]?.size ?: 0)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, (dayNum, dow, count) ->
            val isToday = i == 6
            val level = attendLevel(count)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dow, fontSize = 10.sp, color = if (isToday) accent else TextSecondary, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            when (level) {
                                AttendLevel.FULL -> accent
                                AttendLevel.PARTIAL -> accent.copy(alpha = 0.30f)
                                AttendLevel.NONE -> Color(0xFFF0F0F4)
                            },
                        )
                        .then(if (isToday) Modifier.border(2.dp, accent, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (level == AttendLevel.FULL) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            "$dayNum",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (level == AttendLevel.PARTIAL) accent else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** 월간 출석 달력 다이얼로그. 일자별 출석 완료도 표시 + 이전/이번 달 이동. */
@Composable
private fun MonthAttendanceDialog(history: Map<String, Set<String>>, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    var monthOffset by remember { mutableIntStateOf(0) } // 0 = 이번 달
    val base = remember(monthOffset) {
        DateUtil.hoyoCalendar().apply { add(java.util.Calendar.MONTH, monthOffset); set(java.util.Calendar.DAY_OF_MONTH, 1) }
    }
    val year = base.get(java.util.Calendar.YEAR)
    val month = base.get(java.util.Calendar.MONTH) // 0-based
    val firstDow = base.get(java.util.Calendar.DAY_OF_WEEK) // 1=일
    val daysInMonth = base.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val todayKey = DateUtil.hoyoDayKey()

    GlgDialog(title = "출석 현황", onDismiss = onDismiss, confirmText = "확인", onConfirm = onDismiss, dismissText = null) {
        Column {
            // 월 이동
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { monthOffset-- }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronLeft, "이전 달", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Text("${year}년 ${month + 1}월", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).then(if (monthOffset < 0) Modifier.clickable { monthOffset++ } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ChevronRight, "다음 달", tint = if (monthOffset < 0) TextSecondary else Color.LightGray, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            // 요일 헤더
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(6.dp))
            // 날짜 그리드 (선행 빈칸 + 1..말일)
            val cells: List<Int?> = List(firstDow - 1) { null } + (1..daysInMonth).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val key = "%04d-%02d-%02d".format(year, month + 1, day)
                                val level = attendLevel(history[key]?.size ?: 0)
                                val isToday = key == todayKey
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (level) {
                                                AttendLevel.FULL -> accent
                                                AttendLevel.PARTIAL -> accent.copy(alpha = 0.30f)
                                                AttendLevel.NONE -> Color.Transparent
                                            },
                                        )
                                        .then(if (isToday) Modifier.border(1.5.dp, accent, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$day",
                                        fontSize = 12.sp,
                                        fontWeight = if (level != AttendLevel.NONE || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            level == AttendLevel.FULL -> Color.White
                                            level == AttendLevel.PARTIAL -> accent
                                            isToday -> accent
                                            else -> TextSecondary
                                        },
                                    )
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 범례
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(accent, "전체 출석")
                LegendDot(accent.copy(alpha = 0.30f), "일부")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun StreakChip(streak: Int) {
    val accent = LocalAccent.current
    Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
        Text(
            "🔥 ${streak}일 연속",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyGameRow(game: Game, note: LiveNote?, uid: String, checked: Boolean, inProgress: Boolean, onCheckIn: () -> Unit) {
    val accent = LocalAccent.current
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = game.color.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(game.abbr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = game.color)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(game.shortName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (note != null && note.maxResin > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${note.resinLabel} ${note.currentResin}/${note.maxResin}", fontSize = 12.sp, color = TextSecondary)
                        if (note.resinRecoveryTime.isNotBlank()) {
                            Text(" · ${note.resinRecoveryTime}", fontSize = 11.sp, color = Color.LightGray, maxLines = 1)
                        }
                    }
                } else {
                    Text(
                        if (uid.isBlank()) "UID 미등록 — 설정에서 등록하세요" else "실시간 노트 동기화 중…",
                        fontSize = 11.sp, color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                inProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("처리 중", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                }
                checked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "완료", tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("완료", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                }
                else -> Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .clickable { onCheckIn() }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("출석", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (note != null && note.maxResin > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { note.resinRatio },
                color = accent, trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
        if (note != null && note.extras.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note.extras.forEach { NoteStatChip(it) }
            }
        }
    }
}

/** 실시간 노트 부가 통계 칩 (탐사 파견·주간 보스·세진 등). highlight 항목은 강조색으로 채운다. */
@Composable
private fun NoteStatChip(stat: NoteStat) {
    val accent = LocalAccent.current
    val bg = if (stat.highlight) accent.copy(alpha = 0.14f) else Color(0xFFF2F2F6)
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stat.label, fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.width(4.dp))
            Text(
                stat.value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (stat.highlight) accent else TextPrimary,
            )
        }
    }
}

// ============================================================ 전투 콘텐츠 진행도 (게임별 카드)
/** 게임별 전투 콘텐츠 진행도 카드 (나선 비경·현실 속 환상극 / 혼돈의 기억·허구 이야기·종말의 환영). */
@Composable
private fun CombatGameCard(game: Game, modes: List<CombatMode>) {
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                Box(Modifier.size(10.dp).background(game.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(game.shortName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            modes.forEachIndexed { i, m ->
                CombatRow(m)
                if (i < modes.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

@Composable
private fun CombatRow(m: CombatMode) {
    val accent = LocalAccent.current
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(m.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(m.detail, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                when {
                    m.maxStars > 0 -> Text("⭐ ${m.stars}/${m.maxStars}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = m.gameColor)
                    m.hasData -> Text("메달 ${m.stars}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = m.gameColor)
                }
                val d = m.dDay()
                if (d != null && d >= 0) Text("D-$d", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        }
        if (m.hasData && m.maxStars > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { m.ratio },
                color = m.gameColor, trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
    }
}

// ============================================================ 월간 수입 일지 (게임별 카드)
/** 게임별 이번 달 재화 수입 카드 (여행자의 일지 / 폴리크롬 일지). */
@Composable
private fun LedgerCard(ledger: MonthlyLedger) {
    val accent = LocalAccent.current
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(ledger.gameColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(GameData.byName(ledger.game).shortName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (ledger.month > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("${ledger.month}월", fontSize = 12.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,d".format(ledger.premium), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.width(6.dp))
                Text(ledger.premiumLabel, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                ledger.premiumDelta?.let { d ->
                    Spacer(Modifier.width(10.dp))
                    val up = d >= 0
                    Text(
                        (if (up) "▲ " else "▼ ") + "%,d".format(kotlin.math.abs(d)),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (up) Color(0xFF1FB16B) else Color(0xFFE5484D),
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
            if (ledger.gold > 0) {
                Spacer(Modifier.height(2.dp))
                Text("${ledger.goldLabel} ${"%,d".format(ledger.gold)}", fontSize = 12.sp, color = TextSecondary)
            }
            if (ledger.breakdown.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                ledger.breakdown.take(5).forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.action, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("%,d".format(e.num), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Text("${e.percent}%", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                    }
                    LinearProgressIndicator(
                        progress = { (e.percent / 100f).coerceIn(0f, 1f) },
                        color = ledger.gameColor, trackColor = ProgressEmpty,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    )
                }
            }
        }
    }
}

// ============================================================ 통합 게임 탭 (배너·전투·일지)
/** 게임 칩으로 선택 게임의 픽업 배너·전투 진행도·수입 일지를 번갈아 표시 (세로 스크롤 단축). */
@Composable
fun GameTabbedSection(
    banners: List<GachaBanner>,
    combat: List<CombatMode>,
    ledgers: List<MonthlyLedger>,
    isRefreshing: Boolean,
) {
    val games = GameData.attendanceGames // 원신·스타레일·젠레스
    var selectedKey by remember { mutableStateOf("genshin") }
    val sel = games.firstOrNull { it.key == selectedKey } ?: games.first()
    val selBanners = banners.filter { it.game == sel.displayName }
    val selCombat = combat.filter { it.game == sel.displayName }
    val selLedger = ledgers.firstOrNull { it.game == sel.displayName }
    val empty = selBanners.isEmpty() && selCombat.isEmpty() && selLedger == null

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            games.forEach { g -> GameTabChip(g, g.key == selectedKey) { selectedKey = g.key } }
        }
        Spacer(Modifier.height(16.dp))
        when {
            empty && isRefreshing -> BannerSkeleton()
            empty -> GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Text("${sel.shortName} 정보가 아직 없어요", fontSize = 13.sp, color = TextSecondary)
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selBanners.isNotEmpty()) GameContentBlock("픽업 배너 D-Day") { GameBannerCard(sel, selBanners) }
                if (selCombat.isNotEmpty()) GameContentBlock("전투 콘텐츠 진행도") { CombatGameCard(sel, selCombat) }
                if (selLedger != null) GameContentBlock("이번 달 수입 일지") { LedgerCard(selLedger) }
            }
        }
    }
}

@Composable
private fun GameContentBlock(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun GameTabChip(game: Game, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) game.color else Color.White,
        border = BorderStroke(1.5.dp, if (selected) game.color else DividerColor),
    ) {
        Text(
            game.shortName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else game.color,
        )
    }
}

@Composable
private fun GameBannerCard(game: Game, banners: List<GachaBanner>) {
    // 종료일 빠른 순으로 페이즈 정렬 후, 버전 기준으로 전반/후반 판별
    val phases = banners.groupBy { it.endMillis }.toSortedMap().values.toList()
    val labels = phaseLabels(phases)

    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // 게임 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Box(Modifier.size(10.dp).background(game.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(game.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            phases.forEachIndexed { i, phaseBanners ->
                PhaseBlock(labels[i], phaseBanners, game.color)
                if (i < phases.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

/**
 * 버전 기준 전반/후반 판별.
 * - 한 버전에 페이즈가 2개 이상: 시작 순서대로 전반 · 후반 · 3페이즈…
 * - 한 버전에 페이즈가 1개뿐: 뒤에 다른(이후) 버전이 있으면 그 버전의 전반은 이미 끝난 것 → "후반",
 *   가장 마지막(최신) 버전이면 → "전반".
 */
private fun phaseLabels(phasesByEndAsc: List<List<GachaBanner>>): List<String> {
    val versions = phasesByEndAsc.map { it.firstOrNull()?.version.orEmpty() }
    val lastVersion = versions.lastOrNull()
    val total = versions.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return versions.map { v ->
        val pos = seen.getOrDefault(v, 0)
        seen[v] = pos + 1
        when {
            (total[v] ?: 1) >= 2 -> phaseLabel(pos)
            v == lastVersion -> "전반"
            else -> "후반"
        }
    }
}

private fun phaseLabel(index: Int): String = when (index) {
    0 -> "전반"
    1 -> "후반"
    else -> "${index + 1}페이즈"
}

@Composable
private fun PhaseBlock(phase: String, banners: List<GachaBanner>, gameColor: Color) {
    val first = banners.first()
    val version = banners.firstOrNull { it.version.isNotBlank() }?.version.orEmpty()
    val start = banners.minOf { it.startMillis }
    val charNames = banners.filter { it.type != "weapon" }.map { it.name }
    val hasWeapon = banners.any { it.type == "weapon" }
    val title = (charNames + if (hasWeapon) listOf("무기 기원") else emptyList()).joinToString(" · ")
    val period = if (start > 0) {
        "${DateUtil.shortDateTime(start)} ~ ${DateUtil.shortDateTime(first.endMillis)}"
    } else {
        DateUtil.shortDateTime(first.endMillis)
    }

    Column(Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = gameColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(phase, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gameColor, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
                if (version.isNotBlank()) {
                    Text("v$version", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
            Surface(color = gameColor, shape = RoundedCornerShape(8.dp)) {
                Text(
                    first.dDayLabel(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(period, fontSize = 11.sp, color = TextSecondary)
    }
}

// ============================================================ 패치 일정
private fun computePatchInfo(banners: List<GachaBanner>): List<PatchInfo> {
    val now = System.currentTimeMillis()
    return GameData.games.filter { it.enneadKey != null }.mapNotNull { game ->
        val gb = banners.filter { it.game == game.displayName }
        if (gb.isEmpty()) return@mapNotNull null
        val futureStart = gb.mapNotNull { it.startMillis.takeIf { s -> s > now } }.minOrNull()
        if (futureStart != null) {
            val v = gb.firstOrNull { it.startMillis == futureStart }?.version ?: ""
            PatchInfo(game.displayName, v, futureStart, isStart = true)
        } else {
            val end = gb.maxOf { it.endMillis }
            val v = gb.firstOrNull { it.endMillis == end }?.version ?: ""
            PatchInfo(game.displayName, v, end, isStart = false)
        }
    }
}

@Composable
fun PatchSection(banners: List<GachaBanner>) {
    val accent = LocalAccent.current
    val patches = computePatchInfo(banners)
    if (patches.isEmpty()) return
    Text("패치 일정", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            patches.forEachIndexed { i, p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(p.gameColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                GameData.byName(p.game).shortName + if (p.version.isNotBlank()) " v${p.version}" else "",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            )
                            Text(if (p.isStart) "다음 버전 시작" else "현재 버전 종료", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    val d = p.dDay()
                    Text(if (d > 0) "D-$d" else if (d == 0) "D-DAY" else "—", color = accent, fontWeight = FontWeight.Bold)
                }
                if (i < patches.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

// ============================================================ 위시리스트
@Composable
fun WishlistSection(
    wishlist: Map<String, List<String>>,
    onAdd: (String, String) -> Unit,
    onRemove: (String, String) -> Unit,
    isPickedUp: (String, String) -> Boolean,
) {
    val accent = LocalAccent.current
    var wgame by remember { mutableStateOf("genshin") }
    var input by remember { mutableStateOf("") }
    Text("위시리스트", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WishTab("원신", wgame == "genshin", Color(0xFF4F8EF7)) { wgame = "genshin" }
                WishTab("스타레일", wgame == "hsr", Color(0xFFB06BFF)) { wgame = "hsr" }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    GlgTextField(input, { input = it }, placeholder = "갖고 싶은 캐릭터 이름")
                }
                GlgButton("추가", onClick = { onAdd(wgame, input); input = "" }, modifier = Modifier.width(64.dp), height = 48.dp)
            }
            Spacer(Modifier.height(12.dp))
            val items = wishlist[wgame].orEmpty()
            if (items.isEmpty()) {
                Text("위시 캐릭터를 추가하면 픽업 시 표시돼요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { name ->
                        val picked = isPickedUp(wgame, name)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Star, null, tint = accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (picked) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                        Text("픽업 중", fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            IconButton(onClick = { onRemove(wgame, name) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "삭제", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("픽업 배너에 등장하면 \"픽업 중\"으로 표시돼요 (원신·스타레일)", fontSize = 11.sp, color = Color.LightGray)
        }
    }
}

@Composable
private fun WishTab(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) color else Color.White,
        border = BorderStroke(1.dp, if (selected) color else DividerColor),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else color)
    }
}

// ============================================================ 천장 카운터
@Composable
fun PitySection(pity: Map<String, PityState>, onAdjust: (String, Int) -> Unit, onReset: (String) -> Unit) {
    val accent = LocalAccent.current
    Text("천장 카운터", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            GameData.attendanceGames.forEachIndexed { i, game ->
                val state = pity[game.key] ?: PityState()
                val max = 90
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(game.color, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(game.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PityBtn("−") { onAdjust(game.key, -1) }
                            Text("${state.count} / $max", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp))
                            PityBtn("+") { onAdjust(game.key, 1) }
                            Spacer(Modifier.width(8.dp))
                            Text("리셋", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onReset(game.key) }.padding(4.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (state.count.toFloat() / max).coerceIn(0f, 1f) },
                        color = accent,
                        trackColor = ProgressEmpty,
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("천장까지 ${(max - state.count).coerceAtLeast(0)}연", fontSize = 11.sp, color = TextSecondary)
                }
                if (i < GameData.attendanceGames.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

@Composable
private fun PityBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF2F2F6)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

// ============================================================ 정기 콘텐츠
@Composable
fun ChallengeSection(challenges: List<GameChallenge>) {
    Text("정기 콘텐츠", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    val byGame = challenges.groupBy { it.game }
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
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