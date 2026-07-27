package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gatcha.log.ui.components.GlgDropdownMenu
import com.gatcha.log.ui.components.GlgDropdownItem
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.ArtifactGrade
import com.gatcha.log.data.api.ArtifactScore
import com.gatcha.log.data.api.ArtifactScoring
import com.gatcha.log.data.api.CharArtifactScore
import com.gatcha.log.data.api.CharEffect
import com.gatcha.log.data.api.CharEffectsApi
import com.gatcha.log.data.api.EnkaArtifact
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.EnkaSet
import com.gatcha.log.data.api.EnkaStatLine
import com.gatcha.log.data.api.EnkaWeapon
import com.gatcha.log.data.api.KeyStatRules
import com.gatcha.log.data.api.KeyStatSource
import com.gatcha.log.data.api.KeyStatVerdict
import com.gatcha.log.data.api.keyStatOverrideKey
import com.gatcha.log.data.api.resolveKeyStats
import com.gatcha.log.data.api.statLabel
import com.gatcha.log.data.api.StatTok
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBackButton
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.components.RosterSkeleton
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.WarningText

private val CardOutline = Color.Black.copy(alpha = 0.08f)
private val CritColor = Color(0xFFE0533D)
private val Gold = Color(0xFFD8A12E)   // 5★
private val Purple = Color(0xFF9B6BD6) // 4★

private fun elementColor(el: String): Color = when (el) {
    "불", "화염" -> Color(0xFFE0533D)
    "물" -> Color(0xFF3A8DDE)
    "번개" -> Color(0xFF9B5BD6)
    "얼음" -> Color(0xFF4EA8C4)
    "바람" -> Color(0xFF3FB6A0)
    "바위" -> Color(0xFFC79A3B)
    "풀" -> Color(0xFF5AA83C)
    "물리" -> Color(0xFF8A9099)
    "양자" -> Color(0xFF6C5CE7)
    "허수" -> Color(0xFFE0A93B)
    "전기" -> Color(0xFFE6C13A)
    "에테르" -> Color(0xFFE05CAE)
    else -> Color(0xFF8A9099)
}

private fun gameLabel(game: String): String = when (game) {
    "genshin" -> "원신"
    "hsr" -> "스타레일"
    "zzz" -> "젠레스"
    else -> game
}

/**
 * 게임정보 탭 섹션 — Enka 쇼케이스 캐릭터 로스터(게임당 한 줄). 헤더 게임필터([gameFilter])에 연동.
 * "all"=원신·스타레일·젠레스를 게임별 블록으로 모두 표시, 특정 게임=해당 게임만. 캐릭터 탭 → [onOpenStats].
 */
@Composable
fun EnkaCharSection(
    viewModel: SpendingViewModel,
    gameFilter: String,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit = {},
    onOpenHoyolab: () -> Unit = {},
) {
    val accent = LocalAccent.current
    val results by viewModel.enkaResults.collectAsState()
    val loadingGames by viewModel.enkaLoadingGames.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()

    // 표시 대상 게임 — 전체면 3게임, 아니면 헤더가 고른 게임 1개(Enka 미지원 게임이면 비표시).
    val games = remember(gameFilter) {
        if (gameFilter == "all") listOf("genshin", "hsr", "zzz")
        else listOf(gameFilter).filter { it in setOf("genshin", "hsr", "zzz") }
    }

    // 미연동(=HoYoLAB 연동 프롬프트가 뜰 상황)이면 '내 캐릭터' 영역 전체를 숨긴다(헤더 포함).
    // 연동 유도는 데일리/프로필 섹션의 프롬프트가 담당하며, 연동되면 자동으로 로스터가 나타난다.
    if (!hoyolab.isLinked) return

    // 필터 변경 시 해당 게임들 로드(캐시 적중분 즉시 반영, 미적중분 순차 호출).
    LaunchedEffect(games) { if (games.isNotEmpty()) viewModel.autoLoadEnkaSection(games) }

    Column {
        Text("내 캐릭터", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(11.dp))

        // 게임별로 한 카드씩 — 각 게임 로스터를 카드로 묶고 게임 라벨을 카드 헤더로 표시.
        games.forEachIndexed { i, g ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            GameRosterBlock(
                game = g,
                showLabel = true,
                result = results[g],
                loading = g in loadingGames,
                accent = accent,
                onOpenStats = onOpenStats,
                onOpenAll = onOpenAll,
            )
        }
    }
}

/** '내 캐릭터' 단일 게임 블록 — (라벨) + 한 줄 로스터. 로딩 시 스켈레톤. */
@Composable
private fun GameRosterBlock(
    game: String,
    showLabel: Boolean,
    result: com.gatcha.log.data.api.EnkaResult?,
    loading: Boolean,
    accent: Color,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit,
) {
    val chars = result?.profile?.chars.orEmpty()
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (showLabel) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    // 게임 태그 — 예전엔 닷이 앱 강조색이라 세 게임이 전부 같은 색이었다(구분 불가).
                    GlgGameTag(game, size = GameTagSize.Small)
                    Spacer(Modifier.width(8.dp))
                    Text(gameLabel(game), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (chars.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text("${chars.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                }
            }
            when {
                // 로드 전(result null)·로딩 중엔 스켈레톤, 로드 완료 후에만 빈/에러 표시
                chars.isEmpty() && (result == null || loading) -> RosterSkeleton()
                chars.isEmpty() -> Hint(
                    result?.error ?: "표시할 캐릭터가 없어요 (인게임 쇼케이스 공개 확인)",
                )
                else -> RosterRow(chars, game, accent, onOpenStats, onOpenAll)
            }
        }
    }
}

@Composable
fun EnkaRosterPage(
    viewModel: SpendingViewModel,
    game: String,
    onBack: () -> Unit,
    onOpenStats: (EnkaChar, String) -> Unit,
) {
    BackHandler { onBack() }
    // 전체 보기/탭 왕복 어떤 경로로 진입해도 해당 게임 결과를 보장(캐시 적중 시 즉시 반영).
    LaunchedEffect(game) { viewModel.autoLoadEnka(game) }
    val result by viewModel.enkaResult.collectAsState()
    var rarityFilter by rememberSaveable { mutableStateOf(0) } // 0=전체, 5, 4
    var elementFilter by rememberSaveable { mutableStateOf("") } // ""=전체
    var pathFilter by rememberSaveable { mutableStateOf("") } // ""=전체 (HSR)
    var query by rememberSaveable { mutableStateOf("") }
    val all = result?.profile?.chars.orEmpty()
    val elements = all.mapNotNull { it.element.ifBlank { null } }.distinct()
    val paths = all.mapNotNull { it.path.ifBlank { null } }.distinct()
    val q = query.trim()
    val chars = all.filter {
        (rarityFilter == 0 || it.rarity == rarityFilter) &&
            (elementFilter.isBlank() || it.element == elementFilter) &&
            (pathFilter.isBlank() || it.path == pathFilter) &&
            (q.isBlank() || it.name.contains(q, ignoreCase = true))
    }
    val title = "보유 캐릭터 · " + if (game == "genshin") "원신" else if (game == "zzz") "젠레스" else "스타레일"
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp),
    ) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 뒤로가기는 앱 공통 규격(GlgBackButton)으로 — 이 화면만 자체 구현이라 크기가 달랐다.
            GlgBackButton(onBack)
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        // 이름 검색
        GlgTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "캐릭터 이름 검색",
            trailingIcon = Icons.Default.Search,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        // 필터 칩 — 등급 · 속성 · 운명의길(스타레일)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("등급", if (rarityFilter == 0) "전체" else "${rarityFilter}성", listOf<Pair<String, () -> Unit>>("전체" to { rarityFilter = 0 }, "5성" to { rarityFilter = 5 }, "4성" to { rarityFilter = 4 }))
            FilterChip("속성", elementFilter.ifBlank { "전체" }, listOf<Pair<String, () -> Unit>>("전체" to { elementFilter = "" }) + elements.map { e -> e to { elementFilter = e } })
            if (game == "hsr" && paths.isNotEmpty()) {
                FilterChip("운명의길", pathFilter.ifBlank { "전체" }, listOf<Pair<String, () -> Unit>>("전체" to { pathFilter = "" }) + paths.map { p -> p to { pathFilter = p } })
            }
        }
        if (chars.isEmpty() && all.isNotEmpty()) {
            Hint(if (q.isNotBlank()) "‘$q’ 검색 결과가 없어요" else "조건에 맞는 캐릭터가 없어요")
        }
        chars.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    Box(Modifier.weight(1f).fillMaxHeight()) { RosterCard(c, game, Modifier.fillMaxHeight()) { onOpenStats(c, game) } }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FilterChip(label: String, current: String, items: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = Color.White, shape = RoundedCornerShape(999.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline),
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$label·$current", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(3.dp))
                Text("▾", fontSize = 10.sp, color = TextSecondary)
            }
        }
        GlgDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (disp, act) ->
                GlgDropdownItem(text = disp, onClick = { act(); expanded = false })
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
}

/**
 * 유효옵션 편집 카드 — 점수의 기준을 밝히고 직접 고칠 수 있게 한다.
 *
 * 앱 룰은 추정이다. 원신은 예외 목록에 없으면 '치명+공격%+원소피해'가 기본값이고,
 * 스타레일·젠레스는 운명의 길·직업을 못 읽으면 판정 자체가 불가하다. 그 오차가 유효 점수로
 * 그대로 드러나므로, 무엇을 기준으로 쟀는지 보여주고 사용자가 덮어쓸 수 있어야 한다.
 */
@Composable
private fun KeyStatEditor(
    game: String,
    char: EnkaChar,
    verdict: KeyStatVerdict,
    accent: Color,
    onSet: (Set<StatTok>) -> Unit,
) {
    var editing by remember(char.id) { mutableStateOf(false) }
    val selectable = remember(game) { KeyStatRules.selectableStats(game) }
    // 편집 중 선택 상태 — 판정 불가면 빈 집합에서 시작.
    var picked by remember(char.id, verdict) { mutableStateOf(verdict.stats) }

    SecLabel("유효옵션")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (verdict.source) {
                        KeyStatSource.USER -> "직접 설정함"
                        KeyStatSource.RULE -> "앱이 추정한 값"
                        KeyStatSource.NONE -> "판정할 수 없어요"
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (verdict.source == KeyStatSource.USER) accent else TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (editing) "취소" else "바꾸기",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { if (editing) { picked = verdict.stats; editing = false } else editing = true }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when (verdict.source) {
                    KeyStatSource.USER -> "이 캐릭터는 아래 옵션만 점수에 넣어요."
                    KeyStatSource.RULE -> "역할을 추정한 값이에요. 다르면 바꿔 주세요."
                    KeyStatSource.NONE -> "이 캐릭터의 역할 정보가 없어 점수를 낼 수 없어요. 직접 골라 주세요."
                },
                fontSize = 11.sp, color = TextSecondary,
            )

            if (editing) {
                Spacer(Modifier.height(10.dp))
                selectable.chunked(3).forEach { row ->
                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { tok ->
                            GlgChip(
                                statLabel(tok),
                                selected = tok in picked,
                                color = accent,
                            ) { picked = if (tok in picked) picked - tok else picked + tok }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlgChip("저장", selected = true, color = accent) { onSet(picked); editing = false }
                    // 설정 해제 = 빈 집합 저장 → 앱 룰 추정으로 되돌아간다.
                    if (verdict.source == KeyStatSource.USER) {
                        GlgChip("기본값으로") { onSet(emptySet()); editing = false }
                    }
                }
            } else if (verdict.stats.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                verdict.stats.toList().chunked(3).forEach { row ->
                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { tok -> GlgChip(statLabel(tok), selected = true, color = CritColor) }
                    }
                }
            }
        }
    }
}

/**
 * 로스터 한 줄 — 초상 + 이름만, 한 행에 최대 [ROSTER_SLOTS] 칸. **가로 스크롤 없음.**
 *
 * 예전엔 게임마다 2×2 큰 카드였다. 게임이 3개면 그것만으로 화면 세 개 분량이라
 * 아래 섹션(게임 일정·공지)이 한참 밀렸다. 한 줄로 눌러 스크롤을 3분의 1로 줄인다.
 * 인원이 칸보다 많으면 마지막 칸을 "+N"으로 바꿔 전체 페이지로 보낸다 —
 * 좌우로 밀어서 찾게 하지 않는다(밀 수 있다는 걸 알아채기 어렵고, 몇 명인지도 안 보인다).
 */
private const val ROSTER_SLOTS = 6

@Composable
private fun RosterRow(
    chars: List<EnkaChar>,
    game: String,
    accent: Color,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit,
) {
    val overflow = chars.size > ROSTER_SLOTS
    // 넘치면 마지막 칸은 "+N" — 앞의 (칸-1)명만 보여준다.
    val shown = if (overflow) chars.take(ROSTER_SLOTS - 1) else chars.take(ROSTER_SLOTS)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { c ->
            Box(Modifier.weight(1f)) { RosterSlot(c, Modifier) { onOpenStats(c, game) } }
        }
        if (overflow) {
            Box(Modifier.weight(1f)) { MoreSlot(chars.size - shown.size, accent) { onOpenAll(game) } }
        }
        // 인원이 칸보다 적어도 칸 폭은 고정 — 두 명뿐인 게임의 초상이 혼자 커지지 않게.
        repeat(ROSTER_SLOTS - shown.size - if (overflow) 1 else 0) { Spacer(Modifier.weight(1f)) }
    }
}

/** 한 칸 — 원형 초상 + 이름 두 줄. 그 외 정보(레벨·돌파)는 상세에서 본다. */
@Composable
private fun RosterSlot(c: EnkaChar, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(rarityColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (c.iconUrl != null) {
                AsyncImage(model = c.iconUrl, contentDescription = c.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(c.name.take(1), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = rarityColor)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            c.name, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 남은 인원 칸 — 누르면 전체 로스터 페이지로. */
@Composable
private fun MoreSlot(rest: Int, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+$rest", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(5.dp))
        Text("전체", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
    }
}

/** 로스터 카드 — 초상 + 이름 + Lv·우정/원소·명좌. 탭 가능. (전체 로스터 페이지 전용) */
@Composable
private fun RosterCard(c: EnkaChar, game: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    // 게임 카드(글래스 회색 표면) 안에서 대비를 주려 흰 배경 타일 — 전체 페이지에서도 떠 보인다.
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, CardOutline, RoundedCornerShape(18.dp))
            .clickable { onClick() },
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(rarityColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (c.iconUrl != null) {
                    AsyncImage(model = c.iconUrl, contentDescription = c.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(c.name.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = rarityColor)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lv.${c.level}", fontSize = 10.5.sp, color = TextSecondary)
                    if (c.element.isNotBlank()) {
                        Spacer(Modifier.width(5.dp))
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(elementColor(c.element)))
                    }
                }
                val rankLabel = rankLabelFor(c, game)
                if (rankLabel != null) {
                    Spacer(Modifier.height(5.dp))
                    Surface(color = Gold.copy(alpha = 0.16f), shape = RoundedCornerShape(999.dp)) {
                        Text(rankLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

private fun rankLabelFor(c: EnkaChar, game: String): String? = when (game) {
    // 원신: C0=명함, CN=N돌 (기존 앱 표기와 통일 — '명좌'는 한자 음독이라 미사용)
    "genshin" -> when { c.rank < 0 -> null; c.rank == 0 -> "명함"; else -> "${c.rank}돌" }
    "zzz" -> if (c.rank > 0) "형상 시네마 ${c.rank}" else null
    else -> if (c.rank > 0) "${c.rank}성혼" else null
}

/**
 * 풀 스탯 페이지(랜딩) — 캐릭터 헤더 + 무기/광추 + 핵심 스탯 + 성유물/유물.
 * 자체 뒤로가기 헤더 포함.
 */
@Composable
fun EnkaStatPage(
    c: EnkaChar,
    game: String,
    /** 캐릭터별 유효옵션 사용자 설정(키=keyStatOverrideKey). 앱 룰보다 우선. */
    overrides: Map<String, Set<String>> = emptyMap(),
    onSetOverride: (String, Set<String>) -> Unit = { _, _ -> },
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    // 유효옵션 — 사용자가 고른 값이 있으면 그것, 없으면 앱 룰 추정, 둘 다 없으면 판정 불가.
    val verdict = remember(c.id, game, overrides) { resolveKeyStats(game, c, overrides) }
    val keySet = verdict.stats
    val wepLabel = if (game == "genshin") "무기" else if (game == "zzz") "W-엔진" else "광추"
    val artLabel = if (game == "genshin") "성유물" else if (game == "zzz") "드라이브 디스크" else "유물"
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp),
    ) {
        // 뒤로가기 헤더
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 뒤로가기는 앱 공통 규격(GlgBackButton)으로 — 이 화면만 자체 구현이라 크기가 달랐다.
            GlgBackButton(onBack)
            Spacer(Modifier.width(10.dp))
            Text(c.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // 캐릭터 헤더
        val ec = elementColor(c.element)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(ec.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            if (c.iconUrl != null) AsyncImage(model = c.iconUrl, contentDescription = c.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text(c.name.take(1), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ec)
                        }
                        // 원소색 링(이미지 위 오버레이)
                        Box(Modifier.size(64.dp).border(1.5.dp, ec.copy(alpha = 0.35f), RoundedCornerShape(18.dp)))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                }
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.06f)))
                Spacer(Modifier.height(11.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("레벨", "Lv. ${c.level}", TextPrimary)
                    if (c.element.isNotBlank()) InfoRow("속성", c.element, ec)
                    if (c.path.isNotBlank()) InfoRow("운명의 길", c.path, TextPrimary)
                    rankLabelFor(c, game)?.let { InfoRow("돌파", it, TextPrimary) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 무기 / 광추
        SecLabel(wepLabel)
        val w = c.weapon
        if (w != null) WeaponCard(w, accent)
        else EmptyEquipNote(if (game == "genshin") "무기가 장착되지 않았습니다." else if (game == "zzz") "W-엔진이 장착되지 않았습니다." else "광추가 장착되지 않았습니다.")
        Spacer(Modifier.height(16.dp))

        // 핵심 스탯
        SecLabel("핵심 스탯")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(4.dp)) {
                c.stats.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { s -> Box(Modifier.weight(1f)) { StatCell(s, keySet) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 유효옵션 — 점수의 기준이라 무엇으로 쟀는지 밝히고, 틀리면 바로 고칠 수 있게 한다.
        KeyStatEditor(
            game = game,
            char = c,
            verdict = verdict,
            accent = accent,
            onSet = { stats -> onSetOverride(keyStatOverrideKey(game, c.id), stats.map { it.name }.toSet()) },
        )
        Spacer(Modifier.height(16.dp))

        // 성유물 / 유물 — 캐릭터 유효옵션 기준 유효 점수 순으로 정렬해 잘 뽑힌 것부터 보여준다.
        val artScore = remember(c.artifacts, keySet, game) { ArtifactScoring.scoreChar(c.artifacts, keySet, game) }
        SecLabel(artLabel)
        if (c.artifacts.isEmpty()) {
            EmptyEquipNote(if (game == "genshin") "성유물이 장착되지 않았습니다." else if (game == "zzz") "드라이브 디스크가 장착되지 않았습니다." else "유물이 장착되지 않았습니다.")
        } else {
            CritScoreSummary(artScore, accent)
            Spacer(Modifier.height(10.dp))
            artScore.ranked.forEachIndexed { i, r ->
                ArtifactCard(r.artifact, r.score, rank = i + 1, accent = accent, keySet = keySet)
                if (i < artScore.ranked.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }

        // 세트 효과 (장비 있으면 항상 표시 — 활성 세트 없으면 "발동 없음")
        if (c.artifacts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SecLabel("세트 효과")
            if (c.sets.isEmpty()) {
                EmptyEquipNote("세트 효과 발동 없음")
            } else {
                c.sets.forEachIndexed { i, s ->
                    SetCard(s, accent)
                    if (i < c.sets.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }

        // 명좌/성혼/의식 단계별 효과 — 외부 메타 API 비동기 로드. 빈 결과면 섹션 자체 숨김.
        CharEffectsSection(c, game)
    }
}

/** 운명의 자리(원신)/성혼(스타레일)/형상 시네마(젠레스) 섹션 제목. */
private fun effectsTitle(game: String): String = when (game) {
    "genshin" -> "운명의 자리"
    "zzz" -> "형상 시네마"
    else -> "성혼"
}

/**
 * 단계별 효과 섹션 — index ≤ rank=활성(게임색 강조), index > rank=비활성(흐림/잠금).
 * 노드 탭 시 효과 설명 펼침(게임 인게임 명좌/성혼 화면 UX). 로딩 중 스피너, 빈 결과면 섹션 숨김.
 */
@Composable
private fun CharEffectsSection(c: EnkaChar, game: String) {
    var effects by remember(c.id, game) { mutableStateOf<List<CharEffect>>(emptyList()) }
    var loading by remember(c.id, game) { mutableStateOf(true) }
    LaunchedEffect(c.id, game) {
        loading = true
        effects = CharEffectsApi.fetch(game, c.id)
        loading = false
    }
    Spacer(Modifier.height(16.dp))
    SecLabel(effectsTitle(game))
    when {
        loading -> GlassCard(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalAccent.current)
            }
        }
        else -> {
            // rank: 원신 명함=0(돌파 없음), 비공개 rank=-1 → 활성 0개 처리.
            val active = c.rank.coerceAtLeast(0)
            val gameColor = gameAccentColor(game)
            // 설명을 못 받아도(예: ZZZ 의식 소스 미도달) rank 기준 1~6 단계 노드는 항상 표시(이름/설명만 빈 값).
            val nodes = if (effects.isNotEmpty()) effects else (1..6).map { CharEffect(it, "", "") }
            var expanded by remember(c.id, game) { mutableStateOf(-1) }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(7.dp)) {
                    nodes.forEachIndexed { i, e ->
                        EffectNode(
                            effect = e,
                            isActive = e.index <= active,
                            gameColor = gameColor,
                            fallbackLabel = effectsTitle(game),
                            expanded = expanded == i,
                            onToggle = { expanded = if (expanded == i) -1 else i }, // 항상 토글(iOS 패리티). desc 없으면 펼친 영역에 안내.
                        )
                        if (i < nodes.lastIndex) Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/** 게임 강조색(인게임 톤): 원신 골드 · 스타레일 퍼플 · 젠레스 옐로. */
private fun gameAccentColor(game: String): Color = when (game) {
    "genshin" -> Color(0xFFD8A12E)
    "zzz" -> Color(0xFFF5A623)
    else -> Color(0xFFB06BFF)
}

/** 단계 노드 1개 — 번호 배지(활성=게임색 채움/비활성=잠금) + 효과명 + 탭 펼침 설명. */
@Composable
private fun EffectNode(
    effect: CharEffect,
    isActive: Boolean,
    gameColor: Color,
    fallbackLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (expanded) Modifier.background(gameColor.copy(alpha = 0.06f)) else Modifier)
            .clickable { onToggle() }
            .padding(horizontal = 7.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).then(
                    if (isActive) Modifier.background(gameColor, RoundedCornerShape(999.dp))
                    else Modifier.border(1.dp, TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(999.dp)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${effect.index}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isActive) Color.White else TextSecondary.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                effect.name.ifBlank { "$fallbackLabel ${effect.index}" },
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) TextPrimary else TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                modifier = Modifier.weight(1f).alpha(if (isActive) 1f else 0.6f),
            )
            if (!isActive) {
                Spacer(Modifier.width(6.dp))
                Text("잠금", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary.copy(alpha = 0.55f))
            }
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "▴" else "▾", fontSize = 11.sp, color = TextSecondary)
        }
        if (expanded) {
            Spacer(Modifier.height(7.dp))
            Text(
                effect.desc.ifBlank { "효과 설명을 불러오지 못했어요" },
                fontSize = 11.5.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 36.dp).alpha(if (effect.desc.isBlank()) 0.5f else if (isActive) 1f else 0.7f),
            )
        }
    }
}

@Composable
private fun SetCard(s: EnkaSet, accent: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (s.kind.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = TextSecondary.copy(alpha = 0.12f), shape = RoundedCornerShape(5.dp)) {
                            Text(s.kind, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                    Text("${s.count}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            s.effects.forEach { e ->
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.alpha(if (e.active) 1f else 0.45f)) {
                    Box(
                        Modifier.size(18.dp).then(
                            if (e.active) Modifier.background(accent, RoundedCornerShape(999.dp))
                            else Modifier.border(1.dp, TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(999.dp)),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${e.pieces}", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (e.active) Color.White else TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(e.text, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EmptyEquipNote(text: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(14.dp))
    }
}

/** 프로필 속성 1줄 — 라벨(보조색, 좌) : 값(굵게, 우). */
@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.5.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun SecLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 9.dp))
}

@Composable
private fun WeaponCard(w: EnkaWeapon, accent: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniPill("Lv.${w.level}")
                    w.main?.let { StatInline(it) }
                    w.sub?.let { StatInline(it) }
                }
            }
            Spacer(Modifier.width(11.dp))
            Surface(color = accent, shape = RoundedCornerShape(8.dp)) {
                Text(if (w.refinement > 0) "R${w.refinement}" else "—", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun MiniPill(text: String) {
    Surface(color = Color(0xFFF1F1F6), shape = RoundedCornerShape(999.dp)) {
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

@Composable
private fun StatInline(s: EnkaStatLine) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(s.label, fontSize = 10.5.sp, color = TextSecondary)
        Text(s.value, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (s.crit) CritColor else TextPrimary)
    }
}

/**
 * 이 캐릭터의 유효옵션이면 빨간색, 아니면 기본색.
 * 판정은 점수 산식과 **같은 함수**([ArtifactScoring.isEffective])를 써서
 * "빨갛게 강조된 옵션 = 점수에 들어간 옵션"이 항상 일치하도록 한다.
 */
private fun keyOr(keySet: Set<StatTok>, s: EnkaStatLine, default: Color): Color =
    if (ArtifactScoring.isEffective(keySet, s.label)) CritColor else default

/** 유효옵션은 값뿐 아니라 라벨까지 빨갛게 — 한 줄이 통째로 눈에 들어오도록. */
private fun keyLabelOr(keySet: Set<StatTok>, s: EnkaStatLine): Color =
    if (ArtifactScoring.isEffective(keySet, s.label)) CritColor.copy(alpha = 0.85f) else TextSecondary

@Composable
private fun StatCell(s: EnkaStatLine, keySet: Set<StatTok>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(s.label, fontSize = 11.5.sp, color = keyLabelOr(keySet, s), maxLines = 1)
        Text(s.value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = keyOr(keySet, s, TextPrimary), maxLines = 1)
    }
}

@Composable
/** 등급 색 — 상위는 강조색, 중간은 보조 텍스트, 하위는 경고색(교체 후보 신호). */
private fun gradeColor(grade: ArtifactGrade, accent: Color): Color = when (grade) {
    ArtifactGrade.EXCELLENT, ArtifactGrade.GOOD -> accent
    ArtifactGrade.FAIR -> TextSecondary
    ArtifactGrade.POOR, ArtifactGrade.BAD -> WarningText
}

/**
 * 유효 점수 요약 — 합계·장당 평균·등급.
 * 서브 옵션 중 **이 캐릭터 유효옵션만** 최대 강화량으로 나눠 '유효 롤'로 환산한 값이다.
 */
@Composable
private fun CritScoreSummary(s: CharArtifactScore, accent: Color) {
    val c = gradeColor(s.grade, accent)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("유효 점수", fontSize = 10.5.sp, color = TextSecondary)
                    Text("유효옵션 강화 횟수 환산(장당 최대 9)", fontSize = 9.5.sp, color = TextSecondary)
                }
                Text(ArtifactScoring.rollLabel(s.totalRolls), fontSize = 18.sp, fontWeight = FontWeight.Black, color = c)
                Spacer(Modifier.width(7.dp))
                Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(7.dp)) {
                    Text(
                        "장당 ${ArtifactScoring.rollLabel(s.averageRolls)} · ${s.grade.label}",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("빨간색 = 이 캐릭터 유효옵션", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = CritColor)
        }
    }
}

@Composable
private fun ArtifactCard(a: EnkaArtifact, score: ArtifactScore, rank: Int, accent: Color, keySet: Set<StatTok>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (a.iconUrl != null) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F1F6)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(model = a.iconUrl, contentDescription = a.slot, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(2.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                }
                Surface(color = Color(0xFFF1F1F6), shape = RoundedCornerShape(8.dp)) {
                    Text(a.slot, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.main.label, fontSize = 10.5.sp, color = keyLabelOr(keySet, a.main), maxLines = 1)
                    Text(a.main.value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = keyOr(keySet, a.main, accent), maxLines = 1)
                    if (a.setName.isNotBlank()) {
                        Text(a.setName, fontSize = 9.5.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = Gold.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
                        Text("+${a.level}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                    // 유효 점수 — 유효옵션이 하나도 안 붙었으면 순위가 무의미하므로 배지를 숨긴다.
                    if (!score.isEmpty) {
                        val gc = gradeColor(score.grade, accent)
                        Surface(color = gc.copy(alpha = 0.14f), shape = RoundedCornerShape(7.dp)) {
                            Text(
                                "${rank}위 · 유효 ${ArtifactScoring.rollLabel(score.rolls)}",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = gc,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            if (a.subs.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                // 부옵션 — 목업(design_enka_statsheet): 배경 박스 없이 상단 점선 구분선 + 2열 그리드.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = CardOutline,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
                            )
                        }
                        .padding(top = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    a.subs.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { s ->
                                Row(
                                    Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(s.label, fontSize = 11.sp, color = keyLabelOr(keySet, s), maxLines = 1)
                                    Text(s.value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = keyOr(keySet, s, TextPrimary), maxLines = 1)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
