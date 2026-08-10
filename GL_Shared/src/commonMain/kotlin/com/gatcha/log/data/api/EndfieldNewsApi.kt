package com.gatcha.log.data.api

import com.gatcha.log.data.Game
import com.gatcha.log.json.JSONObject

/**
 * 명일방주: 엔드필드 공지 — 커뮤니티 아카이브(GitHub) 경유. 인증 불필요.
 *
 * 원본은 Gryphline 의 `game-hub.gryphline.com/bulletin/v2/aggregate` 인데, appCode·서명 헤더를
 * 요구해서 그대로는 `code:1500` 만 돌아온다. 그래서 **그 응답을 5분 주기로 그대로 받아 적는
 * 아카이브 저장소**를 읽는다. ZZZ 배너를 raw.githubusercontent 에서 받는 것과 같은 방식이다.
 *
 * 아카이브: github.com/daydreamer-json/ak-endfield-api-archive (archive 브랜치)
 * 경로의 `6`=글로벌 채널, `3`=글로벌 서버(NA/EU), `ko-kr`=한국어.
 *
 * ⚠️ 3자 저장소라 원본 API 보다 한 겹 더 끊길 수 있다. 실패는 null 로 올려 직전 공지를 유지한다.
 */
internal object EndfieldNewsApi {

    private const val URL =
        "https://raw.githubusercontent.com/daydreamer-json/ak-endfield-api-archive/archive" +
            "/output/akEndfield/gameHub/bulletin/6/3/game/ko-kr/latest.json"

    /**
     * 활성 공지 목록.
     *
     * 본문(HTML)이 목록 응답에 통째로 동봉돼 있는데(항목당 수십 KB, 전체 280KB) [NewsItem] 에
     * 담으면 공지 캐시가 메가바이트로 불어난다. 그래서 목록엔 `cid` 만 싣고, 상세를 열 때
     * [article] 이 같은 응답을 한 번 더 받아 `cid` 로 찾는다.
     *
     * @return 성공 시 목록, **네트워크·파싱 실패 시 null** ([NewsApi.notices] 와 같은 규약).
     */
    suspend fun notices(): List<NewsItem>? {
        val list = fetchList() ?: return null
        return list.mapNotNull { o ->
            val cid = o.optString("cid").trim()
            val title = o.optString("title").trim()
            if (cid.isBlank() || title.isBlank()) return@mapNotNull null
            NewsItem(
                game = Game.ENDFIELD.displayName,
                id = cid,
                title = title,
                createdAtMillis = o.optLong("startAt") * 1000, // 초 단위로 온다
                bannerUrl = o.optJSONObject("data")?.optString("url").orEmpty(),
                url = "",   // 공개 퍼머링크 없음(게임 내 공지)
                summary = o.optString("header"),
                source = NewsSource.ENDFIELD,
                bodyRef = cid,
            )
        }
    }

    /** 공지 본문 — 목록을 다시 받아 `cid` 로 찾고, `data.html` 을 블록으로 환원한다. */
    suspend fun article(item: NewsItem): NewsArticle? {
        val cid = item.bodyRef.ifBlank { return null }
        val target = fetchList()?.firstOrNull { it.optString("cid") == cid } ?: return null
        val data = target.optJSONObject("data") ?: return null

        // displayType=picture 인 항목은 본문 HTML 이 없고 이미지 한 장이 전부다.
        val html = data.optString("html")
        val blocks = if (html.isNotBlank()) HtmlNews.toBlocks(html) else buildList {
            val image = data.optString("url")
            if (image.isNotBlank()) add(NewsBlock.Image(image))
            val header = item.summary.trim()
            if (header.isNotEmpty()) add(NewsBlock.Text(header))
        }
        if (blocks.isEmpty()) return null
        return NewsArticle(title = target.optString("title").ifBlank { item.title }, blocks = blocks)
    }

    /** 아카이브 응답의 `rsp.data.list`. 네트워크·파싱 실패면 null. */
    private suspend fun fetchList(): List<JSONObject>? {
        val res = Net.get(URL)
        if (!res.isOk) return null
        return runCatching {
            val arr = JSONObject(res.body)
                .optJSONObject("rsp")?.optJSONObject("data")?.optJSONArray("list") ?: return null
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i) }
        }.getOrNull()
    }
}
