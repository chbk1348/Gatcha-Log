package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

/** 공지·뉴스 — 게임별 최신 공지(제목·날짜), 탭하면 HoYoLab 아티클 열기. 게임 필터 연동. */
@Composable
fun NewsSection(news: List<NewsItem>, gameFilter: String, max: Int = 5) {
    val uriHandler = LocalUriHandler.current
    // 헤더 드롭다운 규칙: "all"=전체, 그 외는 게임 키 → 해당 게임 displayName 매칭(일정 섹션과 동일).
    val items = remember(news, gameFilter) {
        val filtered = if (gameFilter == "all") news
        else GameData.games.firstOrNull { it.key == gameFilter }?.let { g -> news.filter { it.game == g.displayName } } ?: news
        filtered.take(max)
    }
    if (items.isEmpty()) return
    Text("공지·뉴스", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            items.forEachIndexed { i, n ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = n.url.isNotBlank()) { uriHandler.openUri(n.url) }
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(GameData.colorFor(n.game).toColor()))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 2)
                        Text(DateUtil.shortDate(n.createdAtMillis), fontSize = 11.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
