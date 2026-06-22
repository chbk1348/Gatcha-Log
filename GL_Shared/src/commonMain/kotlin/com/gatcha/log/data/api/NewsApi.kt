package com.gatcha.log.data.api

import com.gatcha.log.data.Game
import com.gatcha.log.json.JSONArray

/**
 * 게임 공지·뉴스 항목 — ennead news API.
 * @param game 게임 표시명(displayName)
 * @param createdAtMillis 게시 시각(밀리초)
 * @param bannerUrl 배너 이미지 URL(없을 수 있음)
 * @param url HoYoLab 아티클 링크
 */
data class NewsItem(
    val game: String,
    val title: String,
    val createdAtMillis: Long,
    val bannerUrl: String,
    val url: String,
)

/**
 * ennead news API — 게임별 공지(notices). ko-kr 번역 제공(원신·스타레일·젠레스 모두 한국어).
 * 엔드포인트: `https://api.ennead.cc/mihoyo/{slug}/news/notices?lang=ko-kr` (top-level 배열).
 */
object NewsApi {
    suspend fun notices(game: Game): List<NewsItem> {
        val slug = game.newsSlug ?: return emptyList()
        val res = Net.get("https://api.ennead.cc/mihoyo/$slug/news/notices?lang=ko-kr")
        if (!res.isOk) return emptyList()
        return runCatching {
            val arr = JSONArray(res.body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title")
                if (title.isBlank()) return@mapNotNull null
                NewsItem(
                    game = game.displayName,
                    title = title,
                    createdAtMillis = o.optLong("created_at") * 1000,
                    bannerUrl = o.optString("banner"),
                    url = o.optString("url"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
