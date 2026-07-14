package com.gatcha.log.data.api

import com.gatcha.log.data.Game
import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

/**
 * 게임 공지·뉴스 항목 — ennead news API.
 * @param game 게임 표시명(displayName)
 * @param id HoYoLab 아티클 id — 본문([NewsApi.article])을 가져오는 키
 * @param createdAtMillis 게시 시각(밀리초)
 * @param bannerUrl 배너 이미지 URL(없을 수 있음)
 * @param url HoYoLab 아티클 링크(웹)
 * @param summary 목록 API 가 주는 본문 평문. **줄바꿈이 전부 날아가 있어** 그대로 뿌리면 통짜 문단이 된다.
 *                본문은 [NewsApi.article] 로 받고, 실패했을 때의 폴백으로만 쓴다.
 */
data class NewsItem(
    val game: String,
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val bannerUrl: String,
    val url: String,
    val summary: String,
)

/** 공지 본문 블록 — 문단과 이미지가 원문 순서대로 섞여 있다. */
sealed interface NewsBlock {
    data class Text(val text: String) : NewsBlock
    data class Image(val url: String) : NewsBlock
}

/** 공지 본문 — [NewsApi.article] 결과. */
data class NewsArticle(
    val title: String,
    val blocks: List<NewsBlock>,
)

/**
 * ennead news API — 게임별 공지(notices). ko-kr 번역 제공(원신·스타레일·젠레스 모두 한국어).
 * 엔드포인트: `https://api.ennead.cc/mihoyo/{slug}/news/notices?lang=ko-kr` (top-level 배열).
 */
object NewsApi {
    /** @return 성공 시 공지 목록, **네트워크·파싱 실패 시 null**(빈 목록과 구분 — 호출부가 기존 값을 유지할 수 있게). */
    suspend fun notices(game: Game): List<NewsItem>? {
        val slug = game.newsSlug ?: return emptyList()
        val res = Net.get("https://api.ennead.cc/mihoyo/$slug/news/notices?lang=ko-kr")
        if (!res.isOk) return null
        return runCatching {
            val arr = JSONArray(res.body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title")
                if (title.isBlank()) return@mapNotNull null
                NewsItem(
                    game = game.displayName,
                    id = o.optString("id"),
                    title = title,
                    createdAtMillis = o.optLong("created_at") * 1000,
                    bannerUrl = o.optString("banner"),
                    url = o.optString("url"),
                    summary = o.optString("description"),
                )
            }
        }.getOrNull()
    }

    /**
     * 공지 본문 — HoYoLab 공개 아티클 API.
     *
     * 목록 API(ennead)의 `description` 도 본문 전문이긴 한데 **줄바꿈이 전부 제거된 통짜 평문**이라
     * 1만 자짜리 공지가 한 문단으로 쏟아지고 본문 이미지도 없다. 그래서 본문은 여기서 따로 받는다.
     *
     * `structured_content` 는 Quill delta(= `[{"insert": "텍스트"}, {"insert": {"image": "url"}}, …]`)라
     * 문단과 이미지가 원문 순서대로 들어 있다. 텍스트/이미지만 취하고 그 외(video·lottery 등)는 건너뛴다.
     *
     * 인증 불필요(공개 엔드포인트). 실패 시 null → 호출부가 [NewsItem.summary] 로 폴백한다.
     */
    suspend fun article(postId: String): NewsArticle? {
        if (postId.isBlank()) return null
        val res = Net.get(
            "https://bbs-api-os.hoyolab.com/community/post/wapi/getPostFull?post_id=$postId",
            headers = mapOf("x-rpc-language" to "ko-kr"), // 없으면 영문 원문이 온다
        )
        if (!res.isOk) return null
        return runCatching {
            val post = JSONObject(res.body)
                .optJSONObject("data")?.optJSONObject("post")?.optJSONObject("post") ?: return null
            val rows = JSONArray(post.optString("structured_content"))

            val blocks = mutableListOf<NewsBlock>()
            val buffer = StringBuilder()
            fun flushText() {
                // nbsp( )가 섞여 오는데 그대로 두면 줄바꿈이 안 먹는다.
                val text = buffer.toString().replace(' ', ' ').trim()
                if (text.isNotEmpty()) blocks += NewsBlock.Text(text)
                buffer.clear()
            }
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                when (val insert = row.opt("insert")) {
                    is String -> buffer.append(insert)
                    else -> {
                        val image = row.optJSONObject("insert")?.optString("image").orEmpty()
                        if (image.isNotBlank()) {
                            flushText() // 이미지 앞의 문단을 먼저 끊어야 원문 순서가 유지된다
                            blocks += NewsBlock.Image(image)
                        }
                    }
                }
            }
            flushText()

            if (blocks.isEmpty()) return null
            NewsArticle(title = post.optString("subject"), blocks = blocks)
        }.getOrNull()
    }
}
