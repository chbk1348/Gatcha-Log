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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.NewsBlock
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.openExternalLink
import com.gatcha.log.ui.components.GlgImageViewer
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.components.SkeletonBox
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

// ════════════════════════════════════════════════════════════════════════════
// 공지 상세 — 목록에서 공지를 탭하면 외부 브라우저 대신 앱 안에서 본문을 보여준다.
//
// 본문은 HoYoLab 아티클 API(NewsApi.article)에서 받는다. 목록 API 도 본문 평문을 주긴 하지만
// 줄바꿈이 전부 날아가 있어 통짜 문단이 되고 이미지도 없다 — 그래서 본문은 따로 받고,
// 실패했을 때만 그 평문(summary)으로 폴백한다. 어느 쪽이든 '브라우저에서 보기'는 항상 제공한다.
//
// (SwiftUI 패리티: GL_IOS/Screens/GameInfo/NewsDetailView.swift)
// ════════════════════════════════════════════════════════════════════════════

/** SectionPage 안에 배치 — 자체 스크롤 없음(상위가 verticalScroll). */
@Composable
fun NewsDetailContent(viewModel: SpendingViewModel, item: NewsItem) {
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    val article by viewModel.newsArticle.collectAsState()
    val loading by viewModel.newsArticleLoading.collectAsState()
    val failed by viewModel.newsArticleFailed.collectAsState()

    LaunchedEffect(item.id) { viewModel.loadNewsArticle(item) }

    // 본문 이미지 탭 → 전체화면 뷰어(확대·저장). 공지 이미지는 대개 표·수치라 본문 폭에선 안 읽힌다.
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    viewerUrl?.let { url ->
        GlgImageViewer(
            url = url,
            onDismiss = { viewerUrl = null },
            onSaved = { viewModel.showStatus(it) },
        )
    }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        // 머리말 — 게임 배지 · 제목 · 게시일
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlgGameTag(item.game, size = GameTagSize.Small)
            Spacer(Modifier.width(8.dp))
            Text(DateUtil.shortDate(item.createdAtMillis), fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 28.sp)
        Spacer(Modifier.height(16.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                // 본문은 길게 눌러 드래그 선택·복사할 수 있다(공지의 코드·일정·수치를 옮겨 적을 일이 잦다).
                // Compose Text 는 기본적으로 선택이 안 되므로 SelectionContainer 로 감싼다.
                // 감싸는 범위는 본문뿐 — '브라우저에서 보기'까지 넣으면 버튼이 선택 대상이 되어 탭이 무뎌진다.
                SelectionContainer {
                Column {
                when {
                    loading -> NewsBodySkeleton()

                    // 본문 로드 성공 — 문단과 이미지를 원문 순서대로.
                    article != null -> article!!.blocks.forEach { block ->
                        when (block) {
                            is NewsBlock.Text -> Text(
                                block.text,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                lineHeight = 23.sp,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            is NewsBlock.Image -> AsyncImage(
                                model = block.url,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewerUrl = block.url },
                            )
                        }
                    }

                    // 폴백 — 본문을 못 받았을 때. 배너 + 줄바꿈 없는 평문이라도 보여준다(빈 화면보다 낫다).
                    else -> {
                        if (item.bannerUrl.isNotBlank()) {
                            AsyncImage(
                                model = item.bannerUrl,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewerUrl = item.bannerUrl },
                            )
                        }
                        if (item.summary.isNotBlank()) {
                            Text(
                                item.summary,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                lineHeight = 23.sp,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        } else {
                            Text(
                                "본문을 불러오지 못했어요. 브라우저에서 확인해 주세요.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        if (failed && item.summary.isNotBlank()) {
                            Text(
                                "본문 전체는 브라우저에서 볼 수 있어요.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                }
                }
                }

                // 원문 링크·공유는 헤더 버튼으로 옮겼다 — 본문 끝까지 스크롤해야 보이던 걸 항상 닿는 자리로.
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 본문 로딩 — 문단 모양 스켈레톤(문단 끝줄만 짧게 해서 진짜 텍스트처럼 보이게). */
@Composable
private fun NewsBodySkeleton() {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        repeat(3) { paragraph ->
            repeat(3) { line ->
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(if (line == 2) 0.6f else 1f)
                        .height(13.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            if (paragraph < 2) Spacer(Modifier.height(10.dp))
        }
    }
}
