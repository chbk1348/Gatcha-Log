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
            val title = oneLineTitle(o.optString("title"))
            // 번역이 안 끝난 원문(영/중) 공지는 뺀다 — ko-kr 로 요청해도 섞여 온다.
            if (cid.isBlank() || title.isBlank() || !isKoreanNotice(title)) return@mapNotNull null
            NewsItem(
                game = Game.ENDFIELD.displayName,
                id = cid,
                title = title,
                createdAtMillis = o.optLong("startAt") * 1000, // 초 단위로 온다
                bannerUrl = bannerOf(o.optJSONObject("data")),
                url = externalLink(o.optJSONObject("data")),
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

        // displayType=picture 인 항목(31건 중 8건)은 **본문 HTML 이 아예 없다** — 게임 안에서도
        // 배너 이미지 한 장으로 끝나는 공지다. 이미지 + 머리말을 본문 삼아 보여준다.
        // 머리말은 [item] 이 아니라 원본에서 읽는다 — 캐시를 거쳐 온 항목은 summary 가 잘려 있다.
        val html = data.optString("html")
        val blocks = if (html.isNotBlank()) HtmlNews.toBlocks(html) else buildList {
            val image = data.optString("url")
            if (image.isNotBlank()) add(NewsBlock.Image(image))
            val header = target.optString("header").trim().ifEmpty { item.summary.trim() }
            if (header.isNotEmpty()) add(NewsBlock.Text(header))
            // 8건 중 7건은 머리말조차 없다. 이미지 한 장만 덜렁 있으면 '본문이 안 나온 것'처럼 보이므로
            // 원래 그런 공지라고 알려 준다(상세 헤더의 브라우저 버튼이 [externalLink] 로 살아 있다).
            if (header.isEmpty()) add(NewsBlock.Text("이 공지는 이미지로만 제공돼요."))
        }
        if (blocks.isEmpty()) return null
        return NewsArticle(title = oneLineTitle(target.optString("title")).ifBlank { item.title }, blocks = blocks)
    }

    /**
     * 목록 썸네일 URL.
     *
     * `displayType` 에 따라 **두 필드가 상호배타적**이다(2026-08-26 실측, 34건):
     * `picture` 8건은 `data.url` 에 배너가 있고 `html` 이 없다. `rich_text` 26건은 그 반대라
     * **url 이 아예 없다** — url 만 읽으면 목록의 4분의 3이 썸네일 없이 남는다.
     *
     * rich_text 26건은 **전부** 본문 첫머리가 `<img>` 이고 호스트도 배너와 같은
     * `web-static.hg-cdn.com` 이라, 그 한 장을 썸네일로 쓴다([ImageCdn] 축소도 그대로 먹는다).
     * 본문 HTML 자체는 여전히 [NewsItem] 에 담지 않는다 — URL 한 줄만 가져온다.
     */
    private fun bannerOf(data: JSONObject?): String {
        val url = data?.optString("url").orEmpty().trim()
        if (url.isNotBlank()) return url
        return HtmlNews.firstImageSrc(data?.optString("html").orEmpty())
    }

    /**
     * 항목의 바깥 링크 — 없으면 빈 문자열(그러면 상세 헤더의 공유·브라우저 버튼이 안 뜬다).
     *
     * `picture` 항목은 본문이 없고 이 링크로 넘기는 게 전부라, 이걸 살려야 사용자가 갈 곳이 생긴다.
     * 다만 `?u8_token={u8_token}&server={server}` 처럼 **게임 클라이언트가 채워 넣는 자리표시자**가
     * 박힌 링크는 그대로 열어도 로그인 오류 페이지만 나오므로 버린다.
     */
    private fun externalLink(data: JSONObject?): String {
        val link = data?.optString("link").orEmpty().trim()
        return if (link.startsWith("http") && !link.contains("{")) link else ""
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
