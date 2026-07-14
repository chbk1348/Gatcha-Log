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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

/** 헤더 드롭다운 규칙: "all"=전체, 그 외는 게임 키 → 해당 게임 displayName 매칭(일정 섹션과 동일). */
private fun filterNews(news: List<NewsItem>, gameFilter: String): List<NewsItem> =
    if (gameFilter == "all") news
    else GameData.games.firstOrNull { it.key == gameFilter }?.let { g -> news.filter { it.game == g.displayName } } ?: news

/** 공지·뉴스 섹션 — 게임별 최신 공지(상위 [max]), 탭하면 앱 안에서 본문 열기. 더 있으면 '더보기'로 전체 페이지. */
@Composable
fun NewsSection(news: List<NewsItem>, gameFilter: String, onSeeAll: () -> Unit, onOpen: (NewsItem) -> Unit, max: Int = 5) {
    val accent = LocalAccent.current
    val all = remember(news, gameFilter) { filterNews(news, gameFilter) }
    if (all.isEmpty()) return
    val items = all.take(max)
    Text("공지·뉴스", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        // 하단 패딩은 더보기 행이 직접 제공(여백 최소화), 더보기 없으면 Spacer 로 기본 여백 유지.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            items.forEachIndexed { i, n ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                NewsRow(n, onOpen)
            }
            if (all.size > max) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSeeAll() }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("더보기 (${all.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 공지·뉴스 전체 페이지 내용(SectionPage 안에 배치 — 자체 스크롤 없음). */
@Composable
fun NewsFullContent(news: List<NewsItem>, gameFilter: String, onOpen: (NewsItem) -> Unit) {
    val all = filterNews(news, gameFilter)
    Text("공지·뉴스", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
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
        Spacer(Modifier.width(8.dp))
        // 앱 안에서 열리므로 외부링크(OpenInNew) 아이콘이 아니라 상세로 들어가는 셰브론.
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}
