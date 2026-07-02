package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.CharEffect
import com.gatcha.log.data.api.CharEffectsApi
import com.gatcha.log.data.api.EnkaArtifact
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.EnkaSet
import com.gatcha.log.data.api.EnkaStatLine
import com.gatcha.log.data.api.EnkaWeapon
import com.gatcha.log.data.api.KeyStatRules
import com.gatcha.log.data.api.StatTok
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.components.RosterSkeleton
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

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
 * 게임정보 탭 상시 섹션 — Enka 쇼케이스 캐릭터 로스터(2열 그리드). 헤더 게임필터([gameFilter])에 연동.
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
    val giUid by viewModel.enkaGiUid.collectAsState()
    val hsrUid by viewModel.enkaHsrUid.collectAsState()

    // 표시 대상 게임 — 전체면 3게임, 아니면 헤더가 고른 게임 1개(Enka 미지원 게임이면 비표시).
    val games = remember(gameFilter) {
        if (gameFilter == "all") listOf("genshin", "hsr", "zzz")
        else listOf(gameFilter).filter { it in setOf("genshin", "hsr", "zzz") }
    }
    // 필터 변경 시 해당 게임들 로드(캐시 적중분 즉시 반영, 미적중분 순차 호출).
    LaunchedEffect(games) { if (games.isNotEmpty()) viewModel.autoLoadEnkaSection(games) }

    val linked = hoyolab.isLinked
    // 클라우드서 복원된 UID 가 있는데 토큰만 없으면 = 재설치/재로그인. 토큰은 보안상 기기에만 저장돼 동기화 안 됨.
    val hadProfile = giUid.isNotBlank() || hsrUid.isNotBlank() || hoyolab.zzzUid.isNotBlank()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("내 캐릭터", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Surface(color = Color(0xFF16A34A).copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                Text("상시", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(11.dp))

        if (!linked) {
            LinkPrompt(accent, hadProfile, onOpenHoyolab)
        } else {
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
}

/** '내 캐릭터' 단일 게임 블록 — (라벨) + 대표 4명 그리드 + 더보기. 로딩 시 스켈레톤. */
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
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(accent))
                    Spacer(Modifier.width(7.dp))
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
                else -> {
                    // 대표 4명만 표시, 그 이상은 더보기로 전체 페이지 진입
                    chars.take(4).chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c ->
                                Box(Modifier.weight(1f).fillMaxHeight()) { RosterCard(c, game, Modifier.fillMaxHeight()) { onOpenStats(c, game) } }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (chars.size > 4) MoreButton(chars.size, accent) { onOpenAll(game) }
                }
            }
        }
    }
}

// 뉴스 섹션과 동일한 '더보기' 스타일 — 카드 내부 구분선 + 가운데 정렬 accent 텍스트.
@Composable
private fun MoreButton(count: Int, accent: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("더보기 ($count)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

/**
 * 보유 캐릭터 전체 목록 페이지 — 더보기 진입. 캐릭터 탭 → 스탯 상세([onOpenStats]).
 */
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp),
    ) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.White, shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline), modifier = Modifier.clickable { onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color(0xFF4B4F57), modifier = Modifier.padding(8.dp).size(18.dp))
            }
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (disp, act) ->
                DropdownMenuItem(text = { Text(disp) }, onClick = { act(); expanded = false })
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun LinkPrompt(accent: Color, reLink: Boolean, onOpenHoyolab: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        if (reLink) {
            // 재설치/재로그인 — 데이터(소비·UID)는 복원됐지만 토큰은 보안상 기기 전용이라 사라짐.
            Text("HoYoLAB 재연동이 필요해요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("보안상 로그인 토큰은 기기에만 저장돼요. 재연동하면 보유 캐릭터가 바로 복원됩니다.", fontSize = 12.sp, color = TextSecondary)
        } else {
            Text("HoYoLAB을 연동하면 보유 캐릭터가 자동으로 표시돼요", fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = accent, shape = RoundedCornerShape(999.dp), modifier = Modifier.clickable { onOpenHoyolab() }) {
            Text(if (reLink) "HoYoLAB 재연동하기" else "HoYoLAB 연동하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}

/** 로스터 카드 — 초상 + 이름 + Lv·우정/원소·명좌. 탭 가능. */
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
fun EnkaStatPage(c: EnkaChar, game: String, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    // 캐릭별 주요 스탯 집합(속성/운명의길/직업/예외맵 기반). 강조 시 치명과 함께 대조.
    val keySet = remember(c.id, game) { KeyStatRules.keyStats(game, c.element, c.path, c.specialty, c.id) }
    val wepLabel = if (game == "genshin") "무기" else if (game == "zzz") "W-엔진" else "광추"
    val artLabel = if (game == "genshin") "성유물" else if (game == "zzz") "드라이브 디스크" else "유물"
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp),
    ) {
        // 뒤로가기 헤더
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.White, shape = RoundedCornerShape(999.dp), border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline), modifier = Modifier.clickable { onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color(0xFF4B4F57), modifier = Modifier.padding(8.dp).size(18.dp))
            }
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

        // 성유물 / 유물
        SecLabel(artLabel)
        if (c.artifacts.isEmpty()) {
            EmptyEquipNote(if (game == "genshin") "성유물이 장착되지 않았습니다." else if (game == "zzz") "드라이브 디스크가 장착되지 않았습니다." else "유물이 장착되지 않았습니다.")
        } else {
            c.artifacts.forEachIndexed { i, a ->
                ArtifactCard(a, accent, keySet)
                if (i < c.artifacts.lastIndex) Spacer(Modifier.height(10.dp))
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

/** 캐릭별 주요 스탯이면(치명 포함) 강조색, 아니면 기본색. */
private fun keyOr(keySet: Set<StatTok>, s: EnkaStatLine, default: Color): Color =
    if (s.crit || KeyStatRules.isKey(keySet, s.label)) CritColor else default

@Composable
private fun StatCell(s: EnkaStatLine, keySet: Set<StatTok>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(s.label, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1)
        Text(s.value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = keyOr(keySet, s, TextPrimary), maxLines = 1)
    }
}

@Composable
private fun ArtifactCard(a: EnkaArtifact, accent: Color, keySet: Set<StatTok>) {
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
                    Text(a.main.label, fontSize = 10.5.sp, color = TextSecondary, maxLines = 1)
                    Text(a.main.value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = keyOr(keySet, a.main, accent), maxLines = 1)
                    if (a.setName.isNotBlank()) {
                        Text(a.setName, fontSize = 9.5.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                Surface(color = Gold.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
                    Text("+${a.level}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
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
                                    Text(s.label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
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
