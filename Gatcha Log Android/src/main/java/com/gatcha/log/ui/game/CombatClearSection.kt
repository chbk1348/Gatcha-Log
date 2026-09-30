package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gatcha.log.data.ClearSummary
import com.gatcha.log.data.CombatAvatar
import com.gatcha.log.data.CombatClear
import com.gatcha.log.data.CombatClearLogic
import com.gatcha.log.data.CombatModeClears
import com.gatcha.log.data.CombatRoom
import com.gatcha.log.data.GameData
import com.gatcha.log.ui.components.GldsTabs
import com.gatcha.log.ui.components.GldsTabsVariant
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBadgeText
import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsSize
import com.gatcha.log.ui.components.GldsVariant
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

// ============================================================
// 클리어 편성 — 엔드 콘텐츠를 어떤 캐릭터로 깼는지.
//
// 데이터는 나선 비경·혼돈의 기억 응답에 원래 들어 있던 층별 투입 캐릭터다(GL_Shared CombatClear).
// **모드 하나 = 카드 하나.** 이번/지난 시즌은 카드 머리의 세그먼트로 바꿔 본다 —
// 시즌마다 카드를 내면 같은 모드가 두 번 나와 목록이 두 배가 되고 지난 기록이 과대 표시된다.
//
// 1안「요약 먼저 · 층 접기」(2026-09-29) — 층을 전부 펼쳐 두면 12층 × 8명이 한 화면을 넘겨
// 정작 "몇 별 받았나"가 스크롤 밑에 묻혔다. 별 요약을 맨 위에 두고, 맨 위 층만 펼친다.
// (iOS CombatClearSection 패리티)
// ============================================================

private val StarGold = Color(0xFFF2B233)
private val StarGoldSoft = Color(0xFFF8DE9C)
private val AvatarSize = 46.dp
private val AvatarCell = 50.dp

/** 층별 편성용 아이콘·칸 폭 — 4명이 한 줄에 이름까지 들어가야 한다. */
private val RoomAvatarSize = 44.dp
private val RoomAvatarCell = 56.dp
private val MiniAvatarSize = 22.dp

/** 전반/후반 색 — 접힌 줄의 막대와 펼친 판의 칩이 같은 색이라야 둘이 이어져 읽힌다. */
private val FirstHalfColor = Color(0xFF2F5BBF)
private val FirstHalfChipBg = Color(0xFFE4ECFB)
private val SecondHalfBar = Color(0xFFC46A1F)
private val SecondHalfText = Color(0xFFA8561A)
private val SecondHalfChipBg = Color(0xFFFBEBDC)

private val RowDivider = Color(0xFFF0F0F0)
private val PanelBg = Color(0xFFF8F8F8)
private val PanelDivider = Color(0xFFECECEC)

/** 처음부터 펼쳐 둘 층 수(맨 위 1층 펼침 + 3층 접힘) — 나머지는 '더 보기' 뒤로. */
private const val VisibleFloors = 4

@Composable
fun CombatClearContent(
    clears: List<CombatClear>,
    loading: Boolean,
    linked: Boolean,
    /** 마지막 조회가 전부 실패했다 — 빈 목록이어도 '기록 없음'이 아니다. */
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    if (!linked) {
        EmptyNote("HoYoLAB을 연동하면 클리어 편성을 볼 수 있어요")
        return
    }
    val modes = remember(clears) { CombatClearLogic.byMode(clears) }
    if (modes.isEmpty()) {
        // 불러오는 동안은 **스피너** — 「불러오는 중이에요」 글자만으로는 멈춘 건지 도는 건지 안 보였다(2026-09-28 지적).
        // 로딩 중이 아닌데 비었다면 정말로 기록이 없는 것 — 둘을 구분해서 안내한다.
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = LocalAccent.current, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp),
                )
            }
        } else if (failed) {
            // 조회 실패를 '기록 없음'으로 보이면 깬 층이 날아간 줄 안다 — 사유를 밝히고 재시도를 준다.
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("불러오지 못했어요", fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                GldsButton("다시 시도", onClick = onRetry, variant = GldsVariant.Secondary, size = GldsSize.S)
            }
        } else {
            EmptyNote("아직 클리어 기록이 없어요")
        }
        return
    }
    val games = remember(modes) { CombatClearLogic.games(modes) }
    // null = 전체. 새로고침 뒤 그 게임 기록이 사라졌으면 전체로 돌아간다(빈 화면이 남지 않게).
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = filter?.takeIf { it in games }
    val shown = if (selected == null) modes else modes.filter { it.game == selected }
    // ⚠️ LazyColumn 금지 — [SectionPage] 가 이미 세로 스크롤을 걸어 놨다. 그 안에 지연 목록을 넣으면
    // 높이 제약이 무한이 되어 "Vertically scrollable component was measured with an infinity maximum
    // height" 로 **크래시**한다(2026-08-05 실기기). 항목이 십여 개뿐이라 지연 로딩도 불필요하다.
    //
    // ⚠️ 좌우 패딩도 주지 않는다 — [SectionPage] 가 이미 16dp 를 준다. 여기서 또 주면 32dp 가 되어
    // 다른 페이지들보다 눈에 띄게 좁아 보인다(2026-08-05 지적).
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 게임이 하나뿐이면 '전체'와 그 게임이 같은 목록이라 칩이 할 일이 없다.
        if (games.size >= 2) GameFilter(games, selected) { filter = it }
        shown.forEachIndexed { i, m ->
            // 첫 카드만 펼쳐 둔다 — 모드마다 12층씩 펼치면 두 번째 카드는 몇 화면 아래로 밀린다.
            key(m.game, m.mode) { ModeCard(m, initiallyExpanded = i == 0) }
        }
    }
}

/** 게임 필터 — '전체' + 게임별. 하나만 고르는 배타 선택이라 **GLDS 탭**(9/30), 고른 칸은 게임색(전체는 강조색). */
@Composable
private fun GameFilter(games: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val accent = LocalAccent.current
    GldsTabs(
        labels = listOf("전체") + games.map { GameData.byNameOrNull(it)?.shortName ?: it },
        selected = selected?.let { games.indexOf(it) + 1 } ?: 0,
        selectedColors = listOf(accent) + games.map { GameData.byNameOrNull(it)?.color?.toColor() ?: accent },
    ) { i -> onSelect(if (i == 0) null else games[i - 1]) }
}


@Composable
private fun ModeCard(m: CombatModeClears, initiallyExpanded: Boolean) {
    var expanded by rememberSaveable(m.game, m.mode) { mutableStateOf(initiallyExpanded) }
    // 지역 변수로 받아야 스마트 캐스트가 된다(모듈이 달라 프로퍼티 직접 참조로는 안 된다).
    val current = m.current
    val previous = m.previous
    var showPrevious by rememberSaveable(m.game, m.mode) { mutableStateOf(false) }
    // 이번 시즌 미도전이면 지난 시즌을 바로 보여 준다 — 빈 카드에 토글만 남으면 고장 난 것처럼 보인다.
    val clear = if (current == null || (showPrevious && previous != null)) previous else current
    clear ?: return
    val summary = remember(clear) { CombatClearLogic.summary(clear) }

    GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        if (!expanded) {
            // 접힌 카드 = 요약 한 줄. 누르면 펼친다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "펼치기") { expanded = true }
                    .semantics { stateDescription = "접힘" }
                    .padding(16.dp),
            ) {
                GameTag(m)
                Spacer(Modifier.width(8.dp))
                ModeTitle(m.mode, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                if (clear.scoreLabel.isNotBlank()) ScoreLabel(clear.scoreLabel, 14.sp) else StarTotal(summary, big = 14.sp, small = 12.sp)
                Spacer(Modifier.width(8.dp))
                Chevron(up = false)
            }
            return@GlassCard
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameTag(m)
                Spacer(Modifier.width(8.dp))
                // ⚠️ weight(fill = false) + Spacer(weight) 조합 금지 — 남는 폭을 **절반씩 나눠 가져서**
                // 우측 요소가 오른쪽 끝이 아니라 한가운데에 선다(2026-08-05 "왼쪽으로 치우쳤다" 지적).
                // 제목이 남는 폭을 전부 먹어야 뒤따르는 것이 오른쪽 끝으로 밀린다.
                // 제목 줄 자체를 누르면 카드를 접는다 — 펼친 카드엔 따로 접기 버튼을 두지 않는다.
                ModeTitle(
                    m.mode,
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClickLabel = "접기") { expanded = false },
                )
                if (current != null && previous != null) {
                    Spacer(Modifier.width(8.dp))
                    SeasonSegment(showPrevious) { showPrevious = it }
                }
            }
            SummaryBlock(clear, summary, isPrevious = !clear.current)
            // 점수 모드(시유 방어전)는 별 칸 막대가 뜻이 없다 — 요약 문구만.
            if (summary.maxStars > 0 && clear.scoreLabel.isBlank()) {
                ProgressBar(remember(clear) { CombatClearLogic.displayRooms(clear) })
            }
            // 시즌마다 펼침 상태를 따로 둔다 — 지난 시즌으로 바꿨을 때 이번 시즌 층 이름이 섞이지 않게.
            key(clear.season, clear.current) { SeasonBody(clear) }
        }
    }
}

/** 게임 태그 — 색 점만으로는 무슨 게임인지 알 수 없다(GI·HSR 표기와 동일 체계). */
@Composable
private fun GameTag(m: CombatModeClears) {
    val color = m.gameColor.toColor()
    Text(
        m.gameShort,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun ModeTitle(mode: String, modifier: Modifier) {
    Text(
        mode,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** 이번 시즌 | 지난 시즌. 둘 다 있을 때만 그린다. GLDS 탭 Neutral(보기 방식 전환). */
@Composable
private fun SeasonSegment(showPrevious: Boolean, onChange: (Boolean) -> Unit) {
    GldsTabs(
        listOf("이번 시즌", "지난 시즌"), if (showPrevious) 1 else 0,
        Modifier.width(168.dp), variant = GldsTabsVariant.Neutral,
    ) { onChange(it == 1) }
}

/** "★ 35 / 36" — 만점을 모르면(점수 기반 모드) 분모 없이. */
@Composable
private fun StarTotal(s: ClearSummary, big: TextUnit, small: TextUnit) {
    Text(
        buildAnnotatedString {
            append("★ ${s.stars}")
            if (s.maxStars > 0) {
                withStyle(SpanStyle(fontSize = small, color = TextSecondary, fontWeight = FontWeight.Medium)) {
                    append(" / ${s.maxStars}")
                }
            }
        },
        fontSize = big,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.semantics {
            contentDescription = if (s.maxStars > 0) "별 ${s.stars} / ${s.maxStars}" else "별 ${s.stars}"
        },
    )
}

/** 별 대신 평가·점수 요약("S+ · 120,234 / 150,000") — 시유 방어전. */
@Composable
private fun ScoreLabel(label: String, size: TextUnit) {
    Text(label, fontSize = size, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
}

@Composable
private fun SummaryBlock(clear: CombatClear, s: ClearSummary, isPrevious: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        if (clear.scoreLabel.isNotBlank()) ScoreLabel(clear.scoreLabel, 20.sp) else StarTotal(s, big = 26.sp, small = 15.sp)
        Spacer(Modifier.width(10.dp))
        val parts = listOfNotNull(
            // 이번 시즌이 없어 지난 시즌을 바로 보여 줄 때도 어느 시즌인지는 밝힌다.
            "지난 시즌".takeIf { isPrevious },
            clear.season.takeIf { it.isNotBlank() },
            "${s.rooms}개 층 기록",
        )
        Text(
            parts.joinToString(" · "),
            fontSize = 12.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

/** 층마다 한 칸 — 만점은 진한 금색, 덜 받은 층은 옅은 금색, 0별은 빈 칸. */
@Composable
private fun ProgressBar(rooms: List<CombatRoom>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(rooms.size) { i ->
            val r = rooms[i]
            val color = when {
                r.stars <= 0 -> DividerColor
                r.stars < r.maxStars -> StarGoldSoft
                else -> StarGold
            }
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

/** 시즌 하나 = 주력 스트립 + 층 목록. */
@Composable
private fun SeasonBody(clear: CombatClear) {
    val roster = clear.roster
    if (roster.isNotEmpty()) {
        val usage = clear.usage
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("이 시즌 주력")
            // 6명을 좌우 끝까지 벌린다 — 왼쪽에 몰아두면 오른쪽이 통째로 비어 화면이 치우쳐 보인다.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                roster.take(6).forEach { AvatarChip(it, count = usage[it.id] ?: 0) }
            }
        }
    }
    val rooms = remember(clear) { CombatClearLogic.displayRooms(clear) }
    // 맨 위(가장 높은) 층만 펼쳐 둔다. List 로 둬야 rememberSaveable 이 그대로 저장한다.
    var open by rememberSaveable { mutableStateOf(rooms.take(1).map { it.name }) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visible = if (showAll) rooms else rooms.take(VisibleFloors)
    Column(Modifier.fillMaxWidth()) {
        visible.forEach { room ->
            HorizontalDivider(color = RowDivider)
            val isOpen = room.name in open
            val toggle = { open = if (isOpen) open - room.name else open + room.name }
            if (isOpen) RoomExpanded(room, clear.season, toggle) else RoomCollapsed(room, clear.season, toggle)
        }
        if (visible.size < rooms.size) {
            HorizontalDivider(color = RowDivider)
            Text(
                "아래 ${rooms.size - visible.size}개 층 더 보기",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAccent.current,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button) { showAll = true }
                    .padding(top = 14.dp),
            )
        }
        // 편성 상세가 안 오는 층(시유 방어전 1~3층) 안내 — 목록에서 말없이 빠지면 누락처럼 보인다.
        if (clear.note.isNotBlank()) {
            HorizontalDivider(color = RowDivider)
            Text(clear.note, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** 층 이름 — 표기는 API 원문 그대로. 인게임 용어를 우리가 재구성하지 않는다. */
@Composable
private fun FloorName(room: CombatRoom, season: String, modifier: Modifier) {
    Text(
        CombatClearLogic.roomLabel(room.name, season),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** 접힌 층 — 이름 · 별 · 전반/후반 미니 편성. 누르면 펼친다. */
@Composable
private fun RoomCollapsed(room: CombatRoom, season: String, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = "펼치기", onClick = onToggle)
            .semantics { stateDescription = "접힘" }
            .padding(vertical = 12.dp),
    ) {
        FloorName(room, season, Modifier.width(36.dp))
        Spacer(Modifier.width(8.dp))
        StarChip(room)
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 후반이 없는 모드(환상극)는 막대가 가리킬 짝이 없어 막대 없이 한 묶음만.
            val single = room.secondHalf.isEmpty()
            MiniTeam(room.firstHalf, bar = if (single) null else FirstHalfColor)
            if (!single) MiniTeam(room.secondHalf, bar = SecondHalfBar)
        }
        Spacer(Modifier.width(8.dp))
        Chevron(up = false)
    }
}

/**
 * 미니 편성 — 색 막대 + 22dp 아이콘 4개.
 *
 * 아이콘끼리 살짝 겹친다(흰 테두리로 구분). 목업처럼 3dp 씩 띄우면 8명 + 막대 2개가 폭 360dp 기기의
 * 카드 안에 안 들어가 별 칩을 밀어낸다.
 */
@Composable
private fun MiniTeam(team: List<CombatAvatar>, bar: Color?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        bar?.let {
            Box(Modifier.size(width = 5.dp, height = 18.dp).clip(RoundedCornerShape(3.dp)).background(it))
            Spacer(Modifier.width(3.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
            // 뱅부는 뺀다 — 8명 + 뱅부 2개면 폭 360dp 기기에서 별 칩을 밀어낸다. 펼친 판에서 보인다.
            team.filterNot { it.isBuddy }.forEach { a ->
                Box(
                    Modifier
                        .size(MiniAvatarSize)
                        .clip(CircleShape)
                        .background(DividerColor)
                        .border(1.dp, Color.White, CircleShape),
                ) {
                    if (a.iconUrl.isNotBlank()) {
                        AsyncImage(
                            model = a.iconUrl,
                            // 이름은 펼친 판에서 읽힌다 — 접힌 줄에서 8명을 다 읽으면 한 줄이 너무 길다.
                            contentDescription = null,
                            modifier = Modifier.size(MiniAvatarSize),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

/** 펼친 층 — 머리 줄(누르면 접힘) + 전반/후반 판. */
@Composable
private fun RoomExpanded(room: CombatRoom, season: String, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = "접기", onClick = onToggle)
                .semantics { stateDescription = "펼침" },
        ) {
            FloorName(room, season, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StarChip(room)
            Spacer(Modifier.width(8.dp))
            Chevron(up = true)
        }
        if (room.detail.isNotBlank()) {
            Text(room.detail, fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
        }
        // 전반/후반을 한 덩어리로 묶는다 — 옅은 판 위에 올려야 층 경계가 눈에 잡힌다.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PanelBg)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (room.secondHalf.isEmpty()) {
                // 한 편성뿐인 모드(현실 속 환상극) — '전반' 칩이 붙으면 후반이 빠진 것처럼 읽힌다.
                HalfRow(null, room.firstHalf)
            } else {
                HalfRow(HalfChip("전반", FirstHalfColor, FirstHalfChipBg), room.firstHalf)
                HorizontalDivider(color = PanelDivider)
                HalfRow(HalfChip("후반", SecondHalfText, SecondHalfChipBg), room.secondHalf)
            }
        }
    }
}

private data class HalfChip(val label: String, val text: Color, val bg: Color)

/**
 * 편성 한 줄 — 색 칩 + 아이콘 + 이름.
 *
 * 한때 이름을 빼서 높이를 줄여 봤는데, 정작 "누구로 깼는지"가 이 화면의 전부라 아이콘만으로는
 * 쓸모가 줄었다(2026-08-05 지적). 이름은 두고 아이콘을 조금 줄여 균형을 맞춘다.
 */
@Composable
private fun HalfRow(chip: HalfChip?, team: List<CombatAvatar>) {
    if (team.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        chip?.let {
            Text(
                it.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = it.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(38.dp)
                    .clip(CircleShape)
                    .background(it.bg)
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        // 요원이 남는 폭을 나눠 가지게 한다 — 왼쪽에 붙여 두면 오른쪽 절반이 비어 치우쳐 보인다.
        // 젠레스 뱅부는 **세로 구분선 뒤**에 따로 둔다(9/30) — 요원과 같은 줄에 섞이면 네 번째 요원처럼 읽혔다.
        // 칸은 **모두 같은 폭**(요원 · 뱅부 모두 weight 1) — 구분선은 1dp 라 간격을 흐트러뜨리지 않는다.
        val buddy = team.firstOrNull { it.isBuddy }
        Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
            team.filterNot { it.isBuddy }.forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AvatarChip(it, count = 0, size = RoomAvatarSize, cell = RoomAvatarCell, nameSize = 10.5.sp)
                }
            }
            if (buddy != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 6.dp).background(BuddyDivider))
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AvatarChip(buddy, count = 0, size = RoomAvatarSize, cell = RoomAvatarCell, nameSize = 10.5.sp)
                }
            }
        }
    }
}

/** 층 별 칩 — 층을 구분하는 유일한 수치라 아이콘 더미에 묻히지 않게 칩으로 키운다. */
@Composable
private fun StarChip(room: CombatRoom) {
    // 평가 모드(시유 방어전) — 같은 칩 모양에 별 대신 등급만.
    if (room.rating.isNotBlank()) {
        Text(
            room.rating,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = StarGold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(StarGold.copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
                .semantics { contentDescription = "평가 ${room.rating}" },
        )
        return
    }
    if (room.stars <= 0) return
    // 만점을 아는 모드만 분모를 붙인다(점수 기반은 층마다 만점이 달라 "★4/3" 이 된다).
    val label = if (room.maxStars > 0) "${room.stars}/${room.maxStars}" else "${room.stars}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StarGold.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
            // 아이콘엔 설명을 달지 않는다 — 숫자만 읽히면 무엇의 개수인지 알 수 없어 칩 전체에 하나만 준다.
            .semantics(mergeDescendants = true) {
                contentDescription = if (room.maxStars > 0) "별 ${room.stars} / ${room.maxStars}" else "별 ${room.stars}"
            },
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(11.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StarGold)
    }
}

@Composable
private fun Chevron(up: Boolean) {
    // 설명은 달지 않는다 — 눌리는 줄 전체에 역할·상태가 이미 붙어 있다.
    Icon(
        if (up) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
        tint = TextSecondary,
        modifier = Modifier.size(18.dp),
    )
}

/**
 * 캐릭터 하나 — 아이콘 + (요청 시) 이름.
 *
 * [count] 가 2 이상이면 등장 횟수를 아이콘 우측 상단에 얹는다.
 * [cell] 은 이름이 들어갈 칸 폭 — 층별 편성은 한 줄에 4명이라 주력 스트립보다 넓게 잡는다.
 * HoYoLAB 이 이름을 안 주면(캐시 미보유) 아이콘만 남는다.
 */
@Composable
private fun AvatarChip(
    a: CombatAvatar,
    count: Int,
    size: Dp = AvatarSize,
    cell: Dp = AvatarCell,
    nameSize: TextUnit = 10.sp,
) {
    if (a.isBuddy) {
        BuddyChip(a, size = size, cell = cell, nameSize = nameSize)
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cell)) {
        Box {
            Box(Modifier.size(size).clip(CircleShape).background(DividerColor)) {
                if (a.iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = a.iconUrl,
                        contentDescription = a.name.ifBlank { null },
                        modifier = Modifier.fillMaxWidth().height(size),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (count > 1) {
                // 원은 정사각형 안에 내접한다 → TopEnd 는 원 **바깥** 대각선 빈 공간이라, 그대로 두면
                // 뱃지가 얼굴에서 떨어져 아래로 처진 것처럼 보인다. 원 테두리에 물리게 위·오른쪽으로 민다.
                // 흰 링은 캐릭터 일러스트 위에서 뱃지 경계를 살린다.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(StarGold),
                    contentAlignment = Alignment.Center,
                ) {
                    GlgBadgeText("$count", fontSize = 9.sp, color = Color.White)
                }
            }
        }
        if (a.name.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            // 이름이 이 화면의 요점이라 보조색이 아니라 본문색으로 — 9sp 회색은 흐려서 읽히지 않았다.
            Text(
                a.name,
                fontSize = nameSize,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val BuddyDivider = Color(0xFFE3E5EA)

/**
 * 젠레스 뱅부 — 요원보다 작은 둥근 사각형 + 이름. 이름은 HoYoLAB 이 주지 않아 nanoka 도감에서 받는다(9/30).
 * 요원 아이콘 칸([size]) 가운데에 놓아 이름 줄이 요원 이름과 같은 높이에 온다.
 */
@Composable
private fun BuddyChip(a: CombatAvatar, size: Dp, cell: Dp, nameSize: TextUnit) {
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cell)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            // 요원과 같은 크기([size]) — 모양(둥근 사각형)으로만 구분한다(9/30).
            // 배경을 깔지 않는다 — 뱅부 그림은 바탕이 투명해 회색 면이 그대로 비쳤다.
            Box(Modifier.size(size).clip(shape)) {
                if (a.iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = a.iconUrl,
                        contentDescription = a.name.ifBlank { "뱅부" },
                        modifier = Modifier.size(size),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        if (a.name.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(a.name, fontSize = nameSize, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
}

@Composable
private fun EmptyNote(text: String) {
    // fillMaxSize 금지 — 위 Column 과 같은 이유(스크롤 컨테이너 안에서 높이 제약이 무한이다).
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
    }
}
