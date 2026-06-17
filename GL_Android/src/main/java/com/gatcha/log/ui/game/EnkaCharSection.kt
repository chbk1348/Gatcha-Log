package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.EnkaArtifact
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.EnkaStatLine
import com.gatcha.log.data.api.EnkaWeapon
import com.gatcha.log.ui.components.GlassCard
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

/**
 * 게임정보 탭 상시 섹션 — Enka 쇼케이스 캐릭터 로스터(2열 그리드).
 * 게임 토글(원신/스타레일)로 해당 UID 자동 로드(5분 캐시). 캐릭터 탭 → [onOpenStats].
 */
@Composable
fun EnkaCharSection(
    viewModel: SpendingViewModel,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit = {},
    onOpenHoyolab: () -> Unit = {},
) {
    val accent = LocalAccent.current
    var game by remember { mutableStateOf("genshin") }
    val result by viewModel.enkaResult.collectAsState()
    val loading by viewModel.enkaLoading.collectAsState()
    val hoyolab by viewModel.hoyolabConfig.collectAsState()

    // 진입 시 1회 자동 로드(TTL 캐시). 전환은 switchGame 에서 즉시 처리.
    LaunchedEffect(Unit) { viewModel.autoLoadEnka(game) }

    // 탭 전환: 이전 게임 결과를 즉시 비워 잔류 표시를 막고, 새 게임 로드(캐시 적중 시 동기 반영)
    val switchGame: (String) -> Unit = { g ->
        if (g != game) {
            game = g
            viewModel.clearEnkaResult()
            viewModel.autoLoadEnka(g)
        }
    }

    val linked = hoyolab.isLinked
    val chars = result?.profile?.chars.orEmpty()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("내 캐릭터", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Surface(color = Color(0xFF16A34A).copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                Text("상시", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            GameChip("원신", game == "genshin", accent) { switchGame("genshin") }
            Spacer(Modifier.width(6.dp))
            GameChip("스타레일", game == "hsr", accent) { switchGame("hsr") }
            Spacer(Modifier.width(6.dp))
            GameChip("젠레스", game == "zzz", accent) { switchGame("zzz") }
        }
        Spacer(Modifier.height(11.dp))

        when {
            !linked -> LinkPrompt(accent, onOpenHoyolab)
            // 전환 직후·로드 전(result null)·로딩 중엔 로딩 표시, 로드 완료 후에만 빈/에러 표시
            chars.isEmpty() -> Hint(
                if (result == null || loading) "불러오는 중…"
                else result?.error ?: "표시할 캐릭터가 없어요 (인게임 쇼케이스 공개 확인)",
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

@Composable
private fun MoreButton(count: Int, accent: Color, onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("더보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.width(5.dp))
            Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
        }
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
    val result by viewModel.enkaResult.collectAsState()
    var rarityFilter by rememberSaveable { mutableStateOf(0) } // 0=전체, 5, 4 (상세 왕복 시 보존)
    val all = result?.profile?.chars.orEmpty()
    val chars = if (rarityFilter == 0) all else all.filter { it.rarity == rarityFilter }
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
            Spacer(Modifier.weight(1f))
            RarityFilter(rarityFilter) { rarityFilter = it }
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
private fun RarityFilter(current: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = Color.White, shape = RoundedCornerShape(999.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline),
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (current == 0) "전체" else "${current}성", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(3.dp))
                Text("▾", fontSize = 11.sp, color = TextSecondary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(0 to "전체", 5 to "5성", 4 to "4성").forEach { (v, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(v); expanded = false })
            }
        }
    }
}

@Composable
private fun GameChip(label: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        color = if (on) accent else Color.White,
        shape = RoundedCornerShape(999.dp),
        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, CardOutline),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (on) Color.White else TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun LinkPrompt(accent: Color, onOpenHoyolab: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("HoYoLAB을 연동하면 보유 캐릭터가 자동으로 표시돼요", fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Surface(color = accent, shape = RoundedCornerShape(999.dp), modifier = Modifier.clickable { onOpenHoyolab() }) {
            Text("HoYoLAB 연동하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}

/** 로스터 카드 — 초상 + 이름 + Lv·우정/원소·명좌. 탭 가능. */
@Composable
private fun RosterCard(c: EnkaChar, game: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    GlassCard(shape = RoundedCornerShape(18.dp), modifier = modifier.clickable { onClick() }) {
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
    val wepLabel = if (game == "genshin") "무기" else if (game == "zzz") "음동기" else "광추"
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
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(ec.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        if (c.iconUrl != null) AsyncImage(model = c.iconUrl, contentDescription = c.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text(c.name.take(1), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ec)
                    }
                    // 원소색 링(이미지 위 오버레이)
                    Box(Modifier.size(64.dp).border(1.5.dp, ec.copy(alpha = 0.35f), RoundedCornerShape(18.dp)))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (c.element.isNotBlank()) Badge(c.element, ec)
                        if (c.path.isNotBlank()) Badge(c.path, TextSecondary)
                        rankLabelFor(c, game)?.let { Badge(it, Color(0xFF9C6F12)) }
                        Badge("Lv. ${c.level}", TextSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 무기 / 광추
        SecLabel(wepLabel)
        val w = c.weapon
        if (w != null) WeaponCard(w, accent)
        else EmptyEquipNote(if (game == "genshin") "무기가 장착되지 않았습니다." else if (game == "zzz") "음동기가 장착되지 않았습니다." else "광추가 장착되지 않았습니다.")
        Spacer(Modifier.height(16.dp))

        // 핵심 스탯
        SecLabel("핵심 스탯")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(4.dp)) {
                c.stats.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { s -> Box(Modifier.weight(1f)) { StatCell(s) } }
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
                ArtifactCard(a, accent)
                if (i < c.artifacts.lastIndex) Spacer(Modifier.height(10.dp))
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

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
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

@Composable
private fun StatCell(s: EnkaStatLine) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(s.label, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1)
        Text(s.value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (s.crit) CritColor else TextPrimary, maxLines = 1)
    }
}

@Composable
private fun ArtifactCard(a: EnkaArtifact, accent: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFF1F1F6), shape = RoundedCornerShape(8.dp)) {
                    Text(a.slot, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.main.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    Text(a.main.value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (a.main.crit) CritColor else accent)
                }
                Surface(color = Gold.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
                    Text("+${a.level}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            if (a.subs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.06f)))
                Spacer(Modifier.height(8.dp))
                a.subs.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        row.forEach { s ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.label, fontSize = 10.5.sp, color = TextSecondary, maxLines = 1)
                                Text(s.value, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (s.crit) CritColor else TextPrimary, maxLines = 1)
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
