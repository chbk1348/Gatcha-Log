package com.gatcha.log.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.EnkaResult
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

private val Gold = Color(0xFFD8A12E)   // 5★
private val Purple = Color(0xFF9B6BD6) // 4★

/** 프로필 쇼케이스 — Enka.Network UID 조회 (원신·스타레일). */
@Composable
fun ProfileShowcaseSection(
    giUid: String,
    hsrUid: String,
    hoyoGiUid: String,
    hoyoHsrUid: String,
    result: EnkaResult?,
    loading: Boolean,
    onLoad: (game: String, uid: String) -> Unit,
    onGameChange: () -> Unit,
    onOpenHoyolab: () -> Unit,
) {
    var game by remember { mutableStateOf("genshin") }
    // 저장된 enka 조회 UID(과거 조회) → HoYoLAB 연동 UID 순.
    fun defaultUid(g: String) = (if (g == "genshin") giUid else hsrUid)
        .ifBlank { if (g == "genshin") hoyoGiUid else hoyoHsrUid }
    var uid by remember(game) { mutableStateOf(defaultUid(game)) }
    var retry by remember(game) { mutableStateOf(0) }
    // 진입/탭전환 시: 이전 결과 정리 + UID 있으면 자동 조회.
    LaunchedEffect(game) {
        onGameChange()
        val d = defaultUid(game)
        uid = d
        if (d.isNotBlank()) onLoad(game, d)
    }
    // 조회 타임아웃/네트워크 오류 시 자동 새로고침(최대 2회).
    LaunchedEffect(result) {
        val err = result?.error
        if (err != null && (err.contains("네트워크") || err.contains("요청이 많")) && retry < 2 && uid.isNotBlank()) {
            retry++
            kotlinx.coroutines.delay(1200)
            onLoad(game, uid)
        }
    }
    val hasUid = defaultUid(game).isNotBlank() || uid.isNotBlank()

    Text("프로필 쇼케이스", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameTab("원신", game == "genshin", Color(0xFF4F8EF7)) { game = "genshin" }
                GameTab("스타레일", game == "hsr", Color(0xFFB06BFF)) { game = "hsr" }
            }
            // UID가 없으면 HoYoLAB 연동 진입 CTA 노출(연동하면 UID 자동 확보).
            if (!hasUid) {
                Spacer(Modifier.height(14.dp))
                HoyolabPrompt(onOpenHoyolab)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    GlgTextField(
                        value = uid,
                        onValueChange = { uid = it.filter(Char::isDigit) },
                        placeholder = "UID 입력 (예: 800000000)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                GlgButton("조회", onClick = { retry = 0; onLoad(game, uid) }, modifier = Modifier.width(72.dp), height = 48.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "게임 내 '프로필 표시(쇼케이스)'에 올린 캐릭터만 조회돼요. 조회한 UID는 이 기기에 저장돼 다음엔 자동으로 불러와요.",
                fontSize = 11.sp, color = Color.LightGray,
            )

            when {
                loading -> {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = LocalAccent.current)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                result?.error != null -> {
                    Spacer(Modifier.height(14.dp))
                    Text(result.error, fontSize = 13.sp, color = Color(0xFFDC2626))
                }
                result?.profile != null -> {
                    val p = result.profile
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                    Spacer(Modifier.height(14.dp))
                    Text(p.nickname.ifBlank { "이름 없음" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("Lv.${p.level} · 월드 레벨 ${p.worldLevel}", fontSize = 12.sp, color = TextSecondary)
                    if (p.signature.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(p.signature, fontSize = 12.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (p.chars.isEmpty()) {
                        Text("쇼케이스에 등록된 캐릭터가 없어요", fontSize = 12.sp, color = TextSecondary)
                    } else {
                        p.chars.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { c ->
                                    Box(Modifier.weight(1f)) { CharCard(c, game) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// UID 없음 — HoYoLAB 연동 진입 CTA (iOS hoyolabPrompt 패리티).
@Composable
private fun HoyolabPrompt(onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Link, null, tint = accent, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("HoYoLAB 연동하고 UID 자동 가져오기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("연동하면 UID 입력 없이 바로 조회돼요", fontSize = 11.sp, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GameTab(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) color else Color(0xFFF2F2F6),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TextSecondary,
        )
    }
}

@Composable
private fun CharCard(c: EnkaChar, game: String) {
    val color = if (c.rarity >= 5) Gold else Purple
    // 원신: 0=명함, 1~6=N돌, 음수=상세 비공개(숨김) / 스타레일: N성혼
    val rankLabel = if (game == "genshin") {
        when {
            c.rank < 0 -> null
            c.rank == 0 -> "명함"
            else -> "${c.rank}돌"
        }
    } else {
        if (c.rank > 0) "${c.rank}성혼" else null
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 캐릭터 아이콘 + 레벨/명좌(성혼) 오버레이
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (c.iconUrl != null) {
                AsyncImage(
                    model = c.iconUrl,
                    contentDescription = c.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(c.name.take(1), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            }
            // 명좌/성혼 배지 (우상단)
            if (rankLabel != null) {
                Surface(
                    color = color,
                    shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 12.dp),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(rankLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            // 레벨 (좌하단)
            Surface(
                color = Color(0xCC000000),
                shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 12.dp),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Text("Lv.${c.level}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(c.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
        Spacer(Modifier.height(1.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("★".repeat(c.rarity.coerceIn(1, 5)), fontSize = 9.sp, color = color, maxLines = 1)
            if (c.element.isNotBlank()) {
                Text(" · ${c.element}", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}
