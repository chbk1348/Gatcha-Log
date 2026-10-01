package com.gatcha.log.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.ChangeEntry
import com.gatcha.log.data.ChangeKind
import com.gatcha.log.data.ChangeLog
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgHeaderTitlePill
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GldsTabs

// 목업(06_ChangeLog.html) 색 토큰 — 분류 의미색은 디자인 고정값을 그대로 사용(패리티).
private val CAccent = Color(0xFF15C7A8)
private val CAccentSoft = Color(0xFFE5F8F4)
// GLDS 2.0 — 설정 화면과 같은 띠 · 헤어라인 색.
private val CBand = Color(0xFFF2F4F6)
private val CHair = Color(0xFFEEF0F2)
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
 * 헤더(뒤로+제목) + 스티키 필터칩(전체·신규·개선·수정·보안) + 버전별 섹션(GLDS 2.0 — 카드 없이 흰 바탕 · 버전 사이 10 띠).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UpdateLogScreen(onBack: () -> Unit) {
    var filter by remember { mutableStateOf<ChangeKind?>(null) } // null = 전체
    val entries = ChangeLog.entries
    val shown = remember(filter) {
        if (filter == null) entries else entries.filter { filter in it.kinds }
    }
    // 강제 업데이트 지원 버전(minSupportedVersionCode 이상)은 펼쳐서, 그 미만(지원 종료)은 접기/펼치기.
    val supported = remember(shown) { shown.filter { it.versionCode >= ChangeLog.minSupportedVersionCode } }
    val unsupported = remember(shown) { shown.filter { it.versionCode < ChangeLog.minSupportedVersionCode } }
    var showOld by remember { mutableStateOf(false) }

    // 흰 배경은 바깥 Box 가 상단 끝(상태바 뒤)까지 채우고, 리스트는 statusBarsPadding 으로 내려서
    // 스티키 필터 헤더까지 상태바 아래에 고정한다(stickyHeader 는 contentPadding top 을 무시하고
    // 뷰포트 최상단에 붙으므로 contentPadding 이 아니라 뷰포트 자체를 인셋해야 함).
    // 탭 페이지와 같은 구조 — 헤더는 고정 오버레이, 리스트는 그 아래에서 시작한다.
    // **뷰포트 자체를 인셋**한다(contentPadding 이 아니라) — stickyHeader 는 contentPadding top 을
    // 무시하고 뷰포트 최상단에 붙으므로, 그러지 않으면 필터칩이 고정 헤더와 겹친다.
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = glgDetailContentTop())
            .navigationBarsPadding(),
        // 맨 아래 여백은 마지막 섹션(아래 20)이 가진다 — 예전 카드 12 + 여기 40 = 52 로 떠 보였다(10/1).
    ) {

        // ── 스티키 필터칩 ──
        stickyHeader {
            // 호요랜드 일자 탭·리딤코드 게임 탭과 **같은 세그먼트 규격**([GldsTabs]).
            //
            // 예전엔 분류색으로 칠한 칩 다섯이었다. 배타 선택인데 독립 버튼처럼 보였고,
            // 선택된 칩의 색이 그때그때 달라 "지금 무엇으로 걸러져 있나"가 한눈에 안 들어왔다.
            // 분류색은 아래 항목의 태그가 이미 말해 준다. (iOS 는 같은 자리에 시스템 세그먼트)
            // 배경은 화면과 같은 흰색(불투명) — 스크롤되는 섹션을 가려 준다.
            // GLDS 2.0(10/1) — 좌우 20 · 아래 1 헤어라인(iOS 와 같은 규격). 첫 섹션 위 22 는 섹션이 가진다.
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 10.dp)) {
                    val kinds = listOf(null, ChangeKind.NEW, ChangeKind.IMP, ChangeKind.FIX, ChangeKind.SEC)
                    GldsTabs(
                        labels = listOf("전체", "신규", "개선", "수정", "보안"),
                        selected = kinds.indexOf(filter).coerceAtLeast(0),
                        onSelect = { filter = kinds[it] },
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(CHair))
            }
        }

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

        // 버전 하나 = 섹션 하나, 사이는 10 띠(첫 섹션 위엔 없다).
        itemsIndexed(supported, key = { _, e -> e.version }) { i, entry ->
            if (i > 0) LogBand()
            ReleaseSection(entry, filter)
        }

        // 지원 종료 버전 — 기본 접힘, '펼치기'로 열람.
        if (unsupported.isNotEmpty()) {
            item {
                if (supported.isNotEmpty()) LogBand()
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { showOld = !showOld }
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("지원 종료 버전 ${unsupported.size}개", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CTextSub)
                    Spacer(Modifier.weight(1f))
                    Text(if (showOld) "접기" else "펼치기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CAccent)
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        if (showOld) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null, tint = CAccent, modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (showOld) {
                itemsIndexed(unsupported, key = { _, e -> e.version }) { _, entry ->
                    LogBand()
                    ReleaseSection(entry, filter)
                }
            }
        }
    }
    GlgDetailHeaderOverlay("업데이트 로그", onBack, scrolled)
    }
}

@Composable
private fun LogBand() {
    Box(Modifier.fillMaxWidth().height(10.dp).background(CBand))
}

/** 버전 하나 = 화면 폭 섹션(좌우 20 · 위 22 · 아래 20 — 마지막 항목 글자가 자체 아래 여백이 없어 20 그대로). */
@Composable
private fun ReleaseSection(entry: ChangeEntry, filter: ChangeKind?) {
    if (filter != null && entry.itemsOf(filter).isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 20.dp)) {
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
            Text("v${entry.version}", fontSize = if (entry.featured) 24.sp else 17.sp, fontWeight = FontWeight.ExtraBold, color = CText)
            Spacer(Modifier.width(10.dp))
            Text(entry.date, fontSize = 12.5.sp, color = CTextSub, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            entry.pill?.let { Pill(it, false) }
            if (entry.securityPill) Pill("보안 필수", true)
        }
        // 카드 안을 분류별 묶음으로(9/29 개편) — 신규 기능 · 수정 사항 · 개선 사항. 항목마다 붙던 태그 대신 묶음 제목.
        val kinds = if (filter == null) entry.groupKinds else listOf(filter)
        kinds.forEachIndexed { gi, kind ->
            val list = entry.itemsOf(kind)
            val st = styleOf(kind)
            Spacer(Modifier.height(if (gi == 0) 8.dp else 16.dp))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(st.badgeBg).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(st.dot))
                Spacer(Modifier.width(6.dp))
                Text(kind.groupLabel, color = st.badgeFg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text("${list.size}", color = st.badgeFg.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            list.forEach { item ->
                Row(Modifier.padding(top = 6.dp, start = 2.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 8.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(st.dot))
                    Spacer(Modifier.width(10.dp))
                    Text(item.text, fontSize = 14.sp, lineHeight = 20.sp, color = CItemText, modifier = Modifier.weight(1f))
                }
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
