package com.gatcha.log.data.api

import com.gatcha.log.data.Game
import com.gatcha.log.json.JSONObject

/**
 * 명조(워더링 웨이브) 공지 — **Kuro Games 공식 게임 내 공지 CDN**. 인증 불필요.
 *
 * ennead·HoYoLab 은 호요버스 전용이라 명조는 여기서 따로 받는다. 응답 자체가 다국어 사전
 * (`tabTitle = {"ko": "...", "en": "..."}`) 이라 언어 선택은 클라이언트 몫이다.
 *
 * - 목록: [NOTICE_URL] — `game`(일반 공지·이벤트) + `activity`(버전 이벤트 노트) 두 묶음
 * - 본문: 항목의 `contentPrefix` + `{lang}.json` → `textContent`(HTML) → [HtmlNews]
 *
 * ⚠️ URL 의 해시(`6eb2a235…`)는 글로벌 서버(G153)의 공지 채널 키다. 게임 대규모 개편 때
 * 바뀔 수 있고, 그러면 404 → [notices] 가 null 을 돌려 **직전 공지가 유지**된다(빈 목록 아님).
 */
internal object WuwaNewsApi {

    private const val BASE = "https://aki-gm-resources-back.aki-game.net/gamenotice/G153/6eb2a235b30d05efd77bedb5cf60999e"
    private const val NOTICE_URL = "$BASE/notice.json"

    /** 다국어 사전에서 한국어 우선, 없으면 영어 → 중문 간체 순. 전부 없으면 빈 문자열. */
    private val LANG_FALLBACK = listOf("ko", "en", "zh-Hans")



    /**
     * 활성 공지 목록.
     *
     * @return 성공 시 목록, **네트워크·파싱 실패 시 null** ([NewsApi.notices] 와 같은 규약 —
     *         호출부가 직전 값을 유지할 수 있게 빈 목록과 구분한다).
     */
    suspend fun notices(): List<NewsItem>? {
        val root = fetchNotice() ?: return null
        return runCatching {
            // game·activity 를 한 목록으로 합친다 — 앱의 '게임 소식'은 종류를 나누지 않는다.
            listOf("game", "activity").flatMap { section ->
                val arr = root.optJSONArray(section) ?: return@flatMap emptyList()
                (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i)) }
            }
        }.getOrNull()
    }

    /** 공지 원본. 네트워크·파싱 실패면 null. */
    private suspend fun fetchNotice(): JSONObject? {
        val res = Net.get(NOTICE_URL)
        if (!res.isOk) return null
        return runCatching { JSONObject(res.body) }.getOrNull()
    }

    private fun parse(o: JSONObject?): NewsItem? {
        if (o == null) return null
        val id = o.optString("id").trim()
        val title = oneLineTitle(localized(o.optJSONObject("tabTitle")))
        // 한국어 번역이 없어 폴백(en/zh)으로 채워진 항목은 뺀다 — 읽을 수 없는 글이 자리만 차지한다.
        if (id.isBlank() || title.isBlank() || !isKoreanNotice(title)) return null

        // contentPrefix 는 CDN 3중화(back / back-aws / akamai)라 첫 항목만 쓴다.
        val prefix = o.optJSONArray("contentPrefix")?.optString(0).orEmpty().trimEnd('/')
        return NewsItem(
            game = Game.WUWA.displayName,
            id = id,
            title = title,
            // 게시 시각이 따로 없어 노출 시작(startTimeMs)을 쓴다 — 목록 정렬 기준이 이것뿐이다.
            createdAtMillis = o.optLong("startTimeMs"),
            bannerUrl = localizedFirst(o.optJSONObject("tabBanner")),
            url = "",   // 웹으로 열 공개 퍼머링크가 없다(게임 내 공지) — 상세는 앱에서만 본다
            summary = "",
            source = NewsSource.WUWA,
            // 본문 URL 을 항목마다 통째로 들고 간다 — 상세를 열 때 목록을 다시 받지 않아도 된다.
            bodyRef = if (prefix.isEmpty()) "" else "$prefix/{lang}.json",
        )
    }

    /**
     * 공지 본문 — [NewsItem.bodyRef] 의 `{lang}` 을 언어 코드로 치환해 순서대로 시도한다.
     * 한국어가 없는 공지가 섞여 있어(지역 한정 이벤트 등) 폴백이 필요하다.
     */
    suspend fun article(item: NewsItem): NewsArticle? {
        val template = item.bodyRef.ifBlank { return null }
        for (lang in LANG_FALLBACK) {
            val res = Net.get(template.replace("{lang}", lang))
            if (!res.isOk) continue
            val article = runCatching {
                val o = JSONObject(res.body)
                val blocks = HtmlNews.toBlocks(o.optString("textContent"))
                if (blocks.isEmpty()) null
                else NewsArticle(title = oneLineTitle(o.optString("textTitle")).ifBlank { item.title }, blocks = blocks)
            }.getOrNull()
            if (article != null) return article
        }
        return null
    }

    /** 다국어 사전(`{"ko": "...", …}`)에서 우선순위대로 첫 값. */
    private fun localized(dict: JSONObject?): String {
        if (dict == null) return ""
        for (lang in LANG_FALLBACK) {
            val v = dict.optString(lang).trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    /** 값이 배열인 다국어 사전(`tabBanner`)의 첫 이미지. */
    private fun localizedFirst(dict: JSONObject?): String {
        if (dict == null) return ""
        for (lang in LANG_FALLBACK) {
            val v = dict.optJSONArray(lang)?.optString(0).orEmpty().trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }
}
