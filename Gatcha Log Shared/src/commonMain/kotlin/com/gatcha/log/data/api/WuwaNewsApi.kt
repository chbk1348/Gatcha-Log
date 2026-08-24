package com.gatcha.log.data.api

import com.gatcha.log.data.Game
import com.gatcha.log.data.GameEvent
import com.gatcha.log.util.currentTimeMillis
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

    /**
     * 명조 **게임 일정** — 공지 피드에서 기간이 있는 게임 내 항목만 추린다.
     *
     * 명조는 ennead 가 안 다루는 게임이라 일정 소스가 없었다. 그런데 이 공지 피드는 항목마다
     * `startTimeMs`·`endTimeMs` 를 들고 있어서, 그 자체가 곧 일정표다 — 따로 API 를 찾을 필요가 없다.
     *
     * 무엇을 올릴지는 `category` 로 가른다.
     *  - 1 = 운영 공지(피드백 경로·점검 안내·확인된 문제) → **일정 아님**. 마감이 있어도 할 일이 아니다.
     *  - 2 = 게임 내 이벤트·콘텐츠(수집 이벤트·출석·도전·한정 판매) → **이것만 올린다.**
     *  - 4 = 버전 이벤트 노트(activity) → 개별 일정이 아니라 묶음 안내라 뺀다.
     *
     * `tag`(3·4·5·6)로 가르지 않는 이유: 같은 tag 에 성격이 다른 게 섞여 온다(tag 4 에 한정 판매와
     * 방송국 안내가 함께). category 가 훨씬 안정적인 축이다.
     *
     * @return 성공 시 목록, **실패 시 null**([notices] 와 같은 규약 — 호출부가 직전 값을 유지).
     */
    suspend fun events(nowMillis: Long = currentTimeMillis()): List<GameEvent>? {
        val root = fetchNotice() ?: return null
        return runCatching { parseEvents(root, nowMillis) }.getOrNull()
    }

    /** 응답 본문 → 일정 목록. 네트워크와 떼어 놓아 규칙을 그대로 시험할 수 있게 한다. */
    internal fun parseEvents(root: JSONObject, nowMillis: Long): List<GameEvent> =
        listOf("game", "activity").flatMap { section ->
            val arr = root.optJSONArray(section) ?: return@flatMap emptyList()
            (0 until arr.length()).mapNotNull { i -> parseEvent(arr.optJSONObject(i), nowMillis) }
        }.sortedBy { it.endMillis }

    /** 일정에 올릴 분류 — 게임 내 이벤트·콘텐츠([events] 참고). */
    private const val CATEGORY_INGAME = 2

    /**
     * 이보다 긴 항목은 '일정'이 아니라 **상시 시스템**으로 본다.
     *
     * 실데이터에 8개월(`혜택 시스템`)·6개월(`선행 체험 기능 안내`)짜리가 섞여 온다. 이런 건
     * 마감이 있어도 오늘 할 일이 아니라서, 주간 보드에 올리면 진짜 마감을 밀어낸다.
     */
    private const val MAX_EVENT_SPAN_MS = 150L * 24 * 60 * 60 * 1000

    /** 공지 한 건 → 일정. 일정으로 볼 수 없는 항목이면 null. */
    private fun parseEvent(o: JSONObject?, nowMillis: Long): GameEvent? {
        if (o == null) return null
        if (o.optInt("category") != CATEGORY_INGAME) return null
        // 상시 고정(permanent)은 마감이 4년 뒤로 박혀 있다 — 날짜가 있다고 일정인 건 아니다.
        if (o.optInt("permanent") == 1) return null
        val start = o.optLong("startTimeMs")
        val end = o.optLong("endTimeMs")
        if (end <= nowMillis) return null
        if (start > 0 && end - start > MAX_EVENT_SPAN_MS) return null
        // 제목에 줄바꿈이 들어 있다(`[이름] \n부제`) — oneLineTitle 이 한 줄로 편다.
        val name = oneLineTitle(localized(o.optJSONObject("tabTitle")))
        // 한국어 번역이 없어 폴백(en/zh)으로 채워진 항목은 뺀다 — 목록과 같은 규칙.
        if (name.isBlank() || !isKoreanNotice(name)) return null
        return GameEvent(
            game = Game.WUWA.displayName,
            name = name,
            endMillis = end,
            startMillis = start,
        )
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
                val body = HtmlNews.toBlocks(o.optString("textContent"))
                if (body.isEmpty()) null
                else {
                    // 본문 배너는 **`banner` 필드에 따로 온다** — `textContent` HTML 에는 <img> 가
                    // 하나도 없다(실측: 15KB 본문에 0개). 그것만 파싱하던 탓에 배너가 있는 공지도
                    // 상세에서는 글만 나왔다.
                    //
                    // 목록 썸네일(`tabBanner`)과는 다른 값이다 — `tabBanner` 가 비어 있는 공지에도
                    // 이 배너는 붙어 있다. 원문에서 맨 위에 놓이는 그림이라 블록 앞에 세운다.
                    val banner = o.optString("banner").trim()
                    val blocks = if (banner.isEmpty()) body else listOf(NewsBlock.Image(banner)) + body
                    NewsArticle(title = oneLineTitle(o.optString("textTitle")).ifBlank { item.title }, blocks = blocks)
                }
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
