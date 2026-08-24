package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.NewsLogic
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.components.GlgDropdownItem
import com.gatcha.log.ui.components.GlgDropdownMenu
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import com.gatcha.log.ui.components.GlgHeaderDropdownPill
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/** 게임 칩 규칙: "all"=전체, 그 외는 게임 키 → 해당 게임 displayName 매칭. */
private fun filterNews(news: List<NewsItem>, gameKey: String): List<NewsItem> =
    if (gameKey == "all") news
    else GameData.games.firstOrNull { it.key == gameKey }?.let { g -> news.filter { it.game == g.displayName } } ?: news

/** 공지·뉴스 섹션 — 게임별 최신 공지(상위 [max]), 탭하면 앱 안에서 본문 열기. 더 있으면 '더보기'로 전체 페이지. */
@Composable
fun NewsSection(news: List<NewsItem>, onSeeAll: () -> Unit, onOpen: (NewsItem) -> Unit, max: Int = 5) {
    val accent = LocalAccent.current
    if (news.isEmpty()) return
    // 그냥 take 하면 공지를 많이 올리는 게임(엔드필드)이 5칸을 다 먹는다 — 게임을 돌아가며 뽑는다.
    val items = remember(news, max) { NewsLogic.previewTop(news, max) }
    // 더보기는 **타이틀 줄 우측**. 카드 맨 아래에 두면 목록 다섯 줄을 다 지나야 보이는데,
    // "전체를 보겠다"는 판단은 목록을 읽기 **전에** 서는 쪽이 많다. 제목 옆이면 섹션에
    // 눈이 닿는 순간 같이 읽힌다. 카드 안 마지막 줄도 하나 줄어든다.
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("공지·뉴스", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (news.size > max) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { onSeeAll() }
                    // 글자만 두면 손가락이 닿는 자리가 너무 작다.
                    .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("더보기 (${news.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = accent, modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            items.forEachIndexed { i, n ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                NewsRow(n, onOpen)
            }
        }
    }
}

/**
 * 공지·뉴스 페이지의 **게임 필터** — 헤더 우측 드롭다운.
 *
 * 본문 위 칩 줄이었다가 고정줄로 옮겼는데, 고정하려면 뒤에 배경판이 필요하고 그 판이 목록 위에
 * 얹힌 표면처럼 읽혔다(2026-08-18). 필터를 **헤더로 올리면** 본문에서 자리를 아예 안 쓰면서도
 * 목록을 어디까지 내리든 늘 손 닿는 곳에 있다 — 고정줄이 풀려던 문제가 그대로 풀린다.
 *
 * 게임이 6개로 늘면서 한 화면에 여러 게임 공지가 섞여, 목록을 훑어 원하는 게임만 보기가 어려워졌다.
 *
 * 선택 중인 게임을 버튼 라벨로 보여 준다 — 아이콘만 두면 지금 걸린 필터를 열어 봐야 안다.
 */
@Composable
fun NewsFilterAction(news: List<NewsItem>, chip: String, onChip: (String) -> Unit) {
    // 칩은 실제로 공지가 있는 게임만 — 소식이 없는 게임을 골라 빈 화면을 보게 두지 않는다.
    val chipGames = remember(news) {
        GameData.games.filter { g -> news.any { it.game == g.displayName } }
    }
    if (chipGames.size <= 1) return
    val selected = chipGames.firstOrNull { it.key == chip }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        // 헤더 표준 부품 — 옆의 뒤로가기·액션 버튼과 같은 44dp 규격이다.
        // (지출 화면 퀵필터 알약은 본문 필터 줄용이라 헤더에 놓으면 혼자 작아 보인다.)
        GlgHeaderDropdownPill(
            label = selected?.shortName ?: "전체",
            selected = selected != null,
            color = selected?.color?.toColor() ?: LocalAccent.current,
        ) { menuOpen = true }
        GlgDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, alignEnd = true) {
            GlgDropdownItem(
                text = "전체", selected = chip == "all",
                onClick = { menuOpen = false; onChip("all") },
            )
            chipGames.forEach { g ->
                GlgDropdownItem(
                    text = g.shortName, selected = chip == g.key,
                    onClick = { menuOpen = false; onChip(g.key) },
                )
            }
        }
    }
}

/** 공지·뉴스 전체 페이지 내용(SectionPage 안에 배치 — 자체 스크롤 없음). 필터 칩은 [NewsFilterChips]. */
@Composable
fun NewsFullContent(news: List<NewsItem>, chip: String, onOpen: (NewsItem) -> Unit) {
    // 공지 목록은 게임 수 × 30건까지 커진다(디스크 캐시 상한은 저장에만 걸린다).
    // 필터는 입력이 바뀔 때만 — 재구성마다 전체를 다시 훑을 이유가 없다.
    val all = remember(news, chip) { filterNews(news, chip) }
    if (all.isEmpty()) {
        Text("공지가 없어요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 24.dp))
        return
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            all.forEachIndexed { i, n ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                NewsRow(n, onOpen)
            }
        }
    }
}

@Composable
private fun NewsRow(n: NewsItem, onOpen: (NewsItem) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onOpen(n) }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlgGameTag(n.game, size = GameTagSize.Small)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 2)
            Text(DateUtil.shortDate(n.createdAtMillis), fontSize = 11.sp, color = TextSecondary)
        }
        // 썸네일 — 목록에서 **글을 고르는 단서**로 쓴다. 상류가 `tabBanner`/`banner` 로 이미
        // 내려주는데(NewsItem.bannerUrl) 지금까지는 상세 페이지의 **본문 로드 실패 폴백**에서만
        // 그려서, 받아 놓고 안 쓰는 값이었다.
        //
        // 없는 항목이 섞여 온다(배너를 안 붙인 공지) — 그때는 자리도 비운다. 빈 회색 상자를
        // 대신 세우면 목록에 구멍이 뚫린 것처럼 보인다.
        if (n.bannerUrl.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            AsyncImage(
                model = n.bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 52.dp, height = 36.dp).clip(RoundedCornerShape(7.dp)),
            )
        }
        Spacer(Modifier.width(8.dp))
        // 앱 안에서 열리므로 외부링크(OpenInNew) 아이콘이 아니라 상세로 들어가는 셰브론.
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}
