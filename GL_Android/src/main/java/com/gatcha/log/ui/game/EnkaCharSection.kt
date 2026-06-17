package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
) {
    val accent = LocalAccent.current
    var game by remember { mutableStateOf("genshin") }
    val result by viewModel.enkaResult.collectAsState()
    val loading by viewModel.enkaLoading.collectAsState()
    val giUid by viewModel.enkaGiUid.collectAsState()
    val hsrUid by viewModel.enkaHsrUid.collectAsState()

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

    val uidSet = (if (game == "genshin") giUid else hsrUid).isNotBlank()
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
        }
        Spacer(Modifier.height(11.dp))

        when {
            !uidSet -> Hint("「프로필 쇼케이스」에서 UID를 먼저 등록하면 캐릭터가 상시 표시돼요")
            // 전환 직후·로드 전(result null)·로딩 중엔 로딩 표시, 로드 완료 후에만 빈/에러 표시
            chars.isEmpty() -> Hint(
                if (result == null || loading) "불러오는 중…"
                else result?.error ?: "표시할 캐릭터가 없어요 (인게임 쇼케이스 공개 확인)",
            )
            else -> {
                // 2열 그리드
                chars.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            Box(Modifier.weight(1f)) { RosterCard(c, game) { onOpenStats(c, game) } }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
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

/** 로스터 카드 — 초상 + 이름 + Lv·우정/원소·명좌. 탭 가능. */
@Composable
private fun RosterCard(c: EnkaChar, game: String, onClick: () -> Unit) {
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    GlassCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable { onClick() }) {
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

private fun rankLabelFor(c: EnkaChar, game: String): String? = if (game == "genshin") {
    // 원신: C0=명함, CN=N돌 (기존 앱 표기와 통일 — '명좌'는 한자 음독이라 미사용)
    when { c.rank < 0 -> null; c.rank == 0 -> "명함"; else -> "${c.rank}돌" }
} else {
    if (c.rank > 0) "${c.rank}성혼" else null
}

/**
 * 풀 스탯 페이지(랜딩) — 캐릭터 헤더 + 무기/광추 + 핵심 스탯 + 성유물/유물.
 * 자체 뒤로가기 헤더 포함.
 */
@Composable
fun EnkaStatPage(c: EnkaChar, game: String, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val wepLabel = if (game == "genshin") "무기" else "광추"
    val artLabel = if (game == "genshin") "성유물" else "유물"
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
                        rankLabelFor(c, game)?.let { Badge(it, Color(0xFF9C6F12)) }
                        Badge("Lv. ${c.level}", TextSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 무기 / 광추
        c.weapon?.let { w ->
            SecLabel(wepLabel)
            WeaponCard(w, accent)
            Spacer(Modifier.height(16.dp))
        }

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
        if (c.artifacts.isNotEmpty()) {
            SecLabel(artLabel)
            c.artifacts.forEachIndexed { i, a ->
                ArtifactCard(a, accent)
                if (i < c.artifacts.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
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
