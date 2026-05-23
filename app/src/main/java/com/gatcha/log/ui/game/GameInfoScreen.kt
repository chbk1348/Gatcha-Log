package com.gatcha.log.ui.game

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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GameEvent
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.PatchInfo
import com.gatcha.log.data.PityState
import com.gatcha.log.ui.components.BannerSkeleton
import com.gatcha.log.ui.components.ListSkeleton
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameInfoScreen(viewModel: SpendingViewModel) {
    val accent = LocalAccent.current
    val banners by viewModel.activeBanners.collectAsState()
    val events by viewModel.gameEvents.collectAsState()
    val notes by viewModel.liveNotes.collectAsState()
    val attendanceToday by viewModel.attendanceToday.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val challenges by viewModel.challenges.collectAsState()
    val wishlist by viewModel.wishlist.collectAsState()
    val pity by viewModel.pity.collectAsState()
    val eventChecks by viewModel.eventChecks.collectAsState()
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
    val showRateDialog = remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshGameInfo() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
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
                        GlgCircleIconButton(Icons.Default.Refresh, "새로고침", loading = isRefreshing, enabled = !isRefreshing) {
                            viewModel.refreshGameInfo()
                        }
                        GlgCircleIconButton(Icons.Default.Settings, "HoYoLAB 설정") {
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
                    hoyolab = hoyolab,
                    checkingIn = checkingIn,
                    streak = attendanceStreak,
                    onCheckIn = { viewModel.attemptCheckIn(it) },
                    onConfigClick = { showHoyolabDialog.value = true },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
            if (banners.isEmpty() && isRefreshing) {
                item { BannerSkeleton() }
                item { Spacer(Modifier.height(20.dp)) }
                item { ListSkeleton(rows = 2, titleWidth = 80.dp) }
                item { Spacer(Modifier.height(20.dp)) }
            } else {
                item { BannerSection(banners) }
                item { Spacer(Modifier.height(20.dp)) }
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
                    item { Spacer(Modifier.height(20.dp)) }
                    item { EventChecklistSection(events, eventChecks) { viewModel.toggleEventCheck(it) } }
                }
            }
        }
    }

    if (showHoyolabDialog.value) {
        HoyolabConfigDialog(
            config = hoyolab,
            onDismiss = { showHoyolabDialog.value = false },
            onSave = {
                viewModel.updateHoyolabConfig(it)
                showHoyolabDialog.value = false
                viewModel.refreshGameInfo()
            },
        )
    }

    if (showRateDialog.value) {
        GachaRateDialog(onDismiss = { showRateDialog.value = false })
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
    val accent = LocalAccent.current
    Text("진행 중인 이벤트", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            events.take(8).forEachIndexed { index, event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(8.dp).background(event.gameColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
                            if (event.reward.isNotBlank()) {
                                Text(event.reward, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(event.dDayLabel(), color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (index < events.take(8).lastIndex) HorizontalDivider(color = DividerColor)
            }
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
    hoyolab: HoyolabConfig,
    checkingIn: String?,
    streak: Int,
    onCheckIn: (String) -> Unit,
    onConfigClick: () -> Unit,
) {
    val accent = LocalAccent.current

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onConfigClick() }.padding(4.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("연동됨", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                }
            }
            Spacer(Modifier.height(8.dp))
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
    }
}

@Composable
fun BannerSection(banners: List<GachaBanner>) {
    Text("픽업 배너 D-Day", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    val byGame = banners.groupBy { it.game }
    // 게임별 카드 하나로 통합 (원신 → 스타레일 순)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GameData.games.forEach { game ->
            val list = byGame[game.displayName].orEmpty()
            if (list.isNotEmpty()) GameBannerCard(game, list)
        }
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

@Composable
fun HoyolabConfigDialog(config: HoyolabConfig, onDismiss: () -> Unit, onSave: (HoyolabConfig) -> Unit) {
    var ltuid by remember { mutableStateOf(config.ltuid) }
    var ltoken by remember { mutableStateOf(config.ltoken) }
    var gi by remember { mutableStateOf(config.genshinUid) }
    var hsr by remember { mutableStateOf(config.hsrUid) }
    var zzz by remember { mutableStateOf(config.zzzUid) }

    GlgDialog(
        title = "HoYoLAB 계정 연동",
        onDismiss = onDismiss,
        confirmText = "저장",
        onConfirm = { onSave(HoyolabConfig(ltuid.trim(), ltoken.trim(), gi.trim(), hsr.trim(), zzz.trim())) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("쿠키(ltuid·ltoken)는 개인 정보입니다. 타인과 공유하지 마세요.", fontSize = 11.sp, color = TextSecondary)
            GlgTextField(ltuid, { ltuid = it }, label = "ltuid", modifier = Modifier.fillMaxWidth())
            GlgTextField(ltoken, { ltoken = it }, label = "ltoken", modifier = Modifier.fillMaxWidth())
            GlgTextField(gi, { gi = it }, label = "원신 UID", modifier = Modifier.fillMaxWidth())
            GlgTextField(hsr, { hsr = it }, label = "스타레일 UID", modifier = Modifier.fillMaxWidth())
            GlgTextField(zzz, { zzz = it }, label = "젠레스 UID", modifier = Modifier.fillMaxWidth())
        }
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
    val accent = LocalAccent.current
    Text("정기 콘텐츠", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val list = challenges.take(8)
            list.forEachIndexed { i, ch ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(8.dp).background(ch.gameColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Text(
                                listOfNotNull(
                                    GameData.byName(ch.game).shortName,
                                    ch.typeName.removePrefix("ChallengeType").removePrefix("ActType").trim().ifBlank { null },
                                    ch.reward.ifBlank { null },
                                ).joinToString(" · "),
                                fontSize = 11.sp, color = TextSecondary, maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(ch.dDayLabel(), color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (i < list.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

// ============================================================ 이벤트 체크리스트
@Composable
fun EventChecklistSection(events: List<GameEvent>, checks: Set<String>, onToggle: (String) -> Unit) {
    val accent = LocalAccent.current
    val list = events.take(12)
    val done = list.count { "${it.name}_${it.endMillis}" in checks }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("이벤트 체크리스트", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("$done / ${list.size}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
    }
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            list.forEachIndexed { i, ev ->
                val key = "${ev.name}_${ev.endMillis}"
                val checked = key in checks
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(key) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (checked) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = if (checked) accent else Color.LightGray,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        ev.name,
                        fontSize = 14.sp,
                        color = if (checked) TextSecondary else TextPrimary,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(ev.dDayLabel(), fontSize = 11.sp, color = TextSecondary)
                }
                if (i < list.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}