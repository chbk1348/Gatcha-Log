package com.gatcha.log.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.ChangeEntry
import com.gatcha.log.data.ChangeKind
import com.gatcha.log.data.ChangeLog
import com.gatcha.log.ui.components.GlgBackButton
import com.gatcha.log.ui.components.GlgChip

// 목업(06_ChangeLog.html) 색 토큰 — 분류 의미색은 디자인 고정값을 그대로 사용(패리티).
private val CAccent = Color(0xFF15C7A8)
private val CAccentSoft = Color(0xFFE5F8F4)
private val CCard = Color(0xFFF6F7F9)
private val CLine = Color(0xFFE3E5EA)
private val CText = Color(0xFF15181C)
private val CTextSub = Color(0xFF7A828C)
private val CItemText = Color(0xFF2A2E34)

private data class KindStyle(val dot: Color, val badgeBg: Color, val badgeFg: Color)

private fun styleOf(k: ChangeKind): KindStyle = when (k) {
    ChangeKind.NEW -> KindStyle(Color(0xFF15C7A8), Color(0xFFE5F8F4), Color(0xFF0E9C84))
    ChangeKind.IMP -> KindStyle(Color(0xFF3B82F6), Color(0xFFE8F0FE), Color(0xFF2563EB))
    ChangeKind.FIX -> KindStyle(Color(0xFFF59E0B), Color(0xFFFEF3DD), Color(0xFFB45309))
    ChangeKind.SEC -> KindStyle(Color(0xFFEF4444), Color(0xFFFDECEC), Color(0xFFD43A3A))
}

/**
 * 업데이트 로그 풀스크린 페이지 — 06_ChangeLog.html 목업 디자인.
 * 히어로 헤더 + 스티키 필터칩(전체·신규·개선·수정·보안) + 최신 featured 카드 + 마일스톤(★) 타임라인.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UpdateLogScreen(onBack: () -> Unit) {
    var filter by remember { mutableStateOf<ChangeKind?>(null) } // null = 전체
    val entries = ChangeLog.entries
    val shown = remember(filter) {
        if (filter == null) entries else entries.filter { filter in it.kinds }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        // ── 뒤로 + 히어로 ──
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(8.dp).statusBarsPadding())
                GlgBackButton(onBack)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("업데이트 ", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = CText)
                    Text("기록", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = CAccent)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "사용자 관점으로 정리한 전체 변경 이력",
                    fontSize = 13.sp, color = CTextSub, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MetaCol("${entries.size}개", "전체 버전")
                    MetaCol(ChangeLog.periodLabel, "업데이트 기간")
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── 스티키 필터칩 ──
        stickyHeader {
            Row(
                Modifier.fillMaxWidth().background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlgChip("전체", selected = filter == null) { filter = null }
                GlgChip("신규", selected = filter == ChangeKind.NEW, color = styleOf(ChangeKind.NEW).dot) { filter = ChangeKind.NEW }
                GlgChip("개선", selected = filter == ChangeKind.IMP, color = styleOf(ChangeKind.IMP).dot) { filter = ChangeKind.IMP }
                GlgChip("수정", selected = filter == ChangeKind.FIX, color = styleOf(ChangeKind.FIX).dot) { filter = ChangeKind.FIX }
                GlgChip("보안", selected = filter == ChangeKind.SEC, color = styleOf(ChangeKind.SEC).dot) { filter = ChangeKind.SEC }
            }
        }

        item { Spacer(Modifier.height(14.dp)) }

        if (shown.isEmpty()) {
            item {
                Text(
                    "해당 분류의 변경 사항이 없어요",
                    fontSize = 14.sp, color = CTextSub,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        items(shown, key = { it.version }) { entry ->
            ReleaseCard(entry, filter)
        }
    }
}

@Composable
private fun MetaCol(value: String, label: String) {
    Column {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CText)
        Text(label, fontSize = 13.sp, color = CTextSub)
    }
}

@Composable
private fun ReleaseCard(entry: ChangeEntry, filter: ChangeKind?) {
    val items = if (filter == null) entry.items else entry.items.filter { it.kind == filter }
    if (items.isEmpty()) return

    val shape = RoundedCornerShape(24.dp)
    val base = when {
        entry.featured -> Modifier.background(Brush.linearGradient(listOf(Color(0xFFF1FBF9), Color.White)), shape)
            .border(1.dp, CAccentSoft, shape)
        entry.milestone -> Modifier.background(CCard, shape)
        else -> Modifier.background(Color.White, shape).border(1.dp, CLine, shape)
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp)
            .then(base).clip(shape).padding(if (entry.featured) 22.dp else 18.dp),
    ) {
        if (entry.featured) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(CAccent)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("최신 버전", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.milestone && !entry.featured) {
                Text("★ ", color = CAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text("v${entry.version}", fontSize = if (entry.featured) 24.sp else 18.sp, fontWeight = FontWeight.ExtraBold, color = CText)
            Spacer(Modifier.width(10.dp))
            Text(entry.date, fontSize = 12.5.sp, color = CTextSub, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            entry.pill?.let { Pill(it, false) }
            if (entry.securityPill) Pill("보안 필수", true)
        }
        Spacer(Modifier.height(10.dp))
        items.forEach { item ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                val st = styleOf(item.kind)
                Box(
                    Modifier.clip(RoundedCornerShape(7.dp)).background(st.badgeBg)
                        .widthIn(min = 34.dp).padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(item.kind.label, color = st.badgeFg, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                Spacer(Modifier.width(10.dp))
                Text(item.text, fontSize = 14.sp, color = CItemText, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Pill(text: String, security: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (security) Color(0xFFFDECEC) else CAccentSoft)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) { Text(text, color = if (security) Color(0xFFD43A3A) else Color(0xFF0E9C84), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}
