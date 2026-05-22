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
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.*

@Composable
fun GameInfoScreen(viewModel: SpendingViewModel) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val banners by viewModel.activeBanners.collectAsState()
    val events by viewModel.gameEvents.collectAsState()
    val notes by viewModel.liveNotes.collectAsState()
    val attendanceToday by viewModel.attendanceToday.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val challenges by viewModel.challenges.collectAsState()
    val wishlist by viewModel.wishlist.collectAsState()
    val pity by viewModel.pity.collectAsState()
    val eventChecks by viewModel.eventChecks.collectAsState()
    val showHoyolabDialog = remember { mutableStateOf(false) }

    // 일회성 상태 메시지 → 토스트
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.refreshGameInfo() }, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                            }
                        }
                        IconButton(onClick = { showHoyolabDialog.value = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "HoYoLAB 설정")
                        }
                    }
                }
            }

            item { HoyolabStatusBadge(hoyolab) { showHoyolabDialog.value = true } }
            item { Spacer(Modifier.height(20.dp)) }
            item { BannerSection(banners) }
            item { Spacer(Modifier.height(20.dp)) }
            item { PatchSection(banners) }
            item { Spacer(Modifier.height(20.dp)) }
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
            item { LiveNoteSection(notes, hoyolab.isLinked) }
            item { Spacer(Modifier.height(20.dp)) }
            item { AttendanceSection(attendanceToday) { viewModel.attemptCheckIn(it) } }
            item { Spacer(Modifier.height(20.dp)) }
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

@Composable
private fun HoyolabStatusBadge(config: HoyolabConfig, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val linked = config.isLinked
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (linked) accent.copy(alpha = 0.08f) else WarningBackground,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (linked) Icons.Default.CheckCircle else Icons.Default.Link,
                null, tint = if (linked) accent else WarningText, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (linked) "HoYoLAB 연동됨" else "HoYoLAB 연동 필요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (linked) accent else WarningText)
                Text(if (linked) "실시간 노트·출석 동기화 가능" else "탭하여 쿠키·UID를 등록하세요", fontSize = 11.sp, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
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
fun LiveNoteSection(notes: List<LiveNote>, linked: Boolean) {
    Text("실시간 노트", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    if (notes.isEmpty()) {
        GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (linked) "실시간 노트를 불러오는 중이거나 UID를 확인해주세요"
                    else "HoYoLAB 연동 시 레진·개척력·배터리가 표시됩니다",
                    fontSize = 12.sp, color = TextSecondary,
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            notes.forEach { note -> NoteCard(note) }
        }
    }
}

@Composable
fun NoteCard(note: LiveNote) {
    val accent = LocalAccent.current
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = note.gameColor.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(GameData.byName(note.game).abbr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = note.gameColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${note.resinLabel} ${note.currentResin}/${note.maxResin}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(note.resinRecoveryTime, fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { note.resinRatio },
                    color = accent,
                    trackColor = ProgressEmpty,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                )
            }
        }
    }
}

@Composable
fun AttendanceSection(attendanceToday: Set<String>, onToggle: (String) -> Unit) {
    Text("출석체크", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            GameData.attendanceGames.forEachIndexed { index, game ->
                AttendanceItem(game, game.key in attendanceToday) { onToggle(game.key) }
                if (index < GameData.attendanceGames.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

@Composable
fun AttendanceItem(game: Game, checked: Boolean, onToggle: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(game.displayName, fontWeight = FontWeight.Medium)
            Text(game.attendanceReward, fontSize = 11.sp, color = TextSecondary)
        }
        if (checked) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onToggle() }) {
                Icon(Icons.Default.CheckCircle, contentDescription = "완료", tint = accent)
                Spacer(Modifier.width(4.dp))
                Text("완료", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("출석하기", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        }
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