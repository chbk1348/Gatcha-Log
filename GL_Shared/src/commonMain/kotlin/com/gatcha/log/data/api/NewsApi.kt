package com.gatcha.log.data.api

import com.gatcha.log.data.Game
import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

/**
 * 공지 출처 — 게임사마다 API 가 따로라 **목록·본문을 가져오는 방법이 다르다**.
 * 어느 경로로 받아온 항목인지 [NewsItem] 이 들고 다녀야 상세를 열 때 되짚어 갈 수 있다.
 */
enum class NewsSource {
    /** 호요버스 3게임 — ennead 목록 + HoYoLab 본문(Quill delta) */
    ENNEAD,
    /** 명조 — Kuro 공식 공지 CDN(다국어 사전 + HTML 본문) */
    WUWA,
    /** 엔드필드 — Gryphline 응답의 커뮤니티 아카이브(HTML 본문 동봉) */
    ENDFIELD,
}

/**
 * 게임 공지·뉴스 항목.
 * @param game 게임 표시명(displayName)
 * @param id 출처별 항목 식별자(호요=HoYoLab 아티클 id, 명조=공지 id, 엔드필드=cid)
 * @param createdAtMillis 게시 시각(밀리초)
 * @param bannerUrl 배너 이미지 URL(없을 수 있음)
 * @param url 웹 퍼머링크. **호요버스만 있다** — 명조·엔드필드는 게임 내 공지라 공개 링크가 없다.
 * @param summary 목록 API 가 주는 본문 평문. **줄바꿈이 전부 날아가 있어** 그대로 뿌리면 통짜 문단이 된다.
 *                본문은 [NewsApi.article] 로 받고, 실패했을 때의 폴백으로만 쓴다.
 * @param source 출처. [NewsApi.article] 이 이 값으로 본문 경로를 고른다.
 * @param bodyRef 본문을 되짚는 키 — 출처마다 의미가 다르다(명조=본문 URL 템플릿, 엔드필드=cid).
 *                호요버스는 [id] 로 충분해 비어 있다.
 */
data class NewsItem(
    val game: String,
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val bannerUrl: String,
    val url: String,
    val summary: String,
    val source: NewsSource = NewsSource.ENNEAD,
    val bodyRef: String = "",
)

/**
 * 공지 제목 한 줄로 정리 — 목록 행은 2줄까지만 보여 주므로 원문 줄바꿈이 그대로 오면 잘린다.
 *
 * 명조·엔드필드는 제목에 줄바꿈을 넣어 부제를 다는데, **엔드필드는 그게 실제 개행이 아니라
 * 백슬래시+n 두 글자로 들어오는 항목이 섞여 있다**(같은 응답 안에서도 제각각이다).
 * 그대로 두면 목록에 `전쟁의 메아리\n섬망의 회상` 처럼 이스케이프가 노출된다.
 */
internal fun oneLineTitle(raw: String): String =
    raw.replace("\\n", " ").replace('\n', ' ').replace('\r', ' ')
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ")

/**
 * 한국어 공지인지 — **제목에 한글이 한 글자라도 있으면** 한국어로 본다.
 *
 * 게임사가 ko 로 달라고 해도 번역이 안 끝난 항목은 원문(영/중)이 그대로 섞여 온다
 * (엔드필드 `Known Issues Notice`, 명조의 지역 한정 공지 등). 읽을 수 없는 글이
 * 목록 자리를 차지하면 정작 볼 공지가 밀리므로 받아올 때 걸러 낸다.
 *
 * 판정을 '한글 포함'으로 느슨하게 둔 이유: 한국어 공지도 제목 절반이 영문 고유명사인 경우가
 * 흔하다(`Discord 게임 통계 위젯 지원!`). 비율로 재면 이런 정상 공지가 같이 떨어진다.
 */
internal fun isKoreanNotice(title: String): Boolean = title.any { it in '가'..'힣' }

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
 * 게임 공지 진입점 — **출처가 게임마다 다르다**([NewsSource]). 호출부는 게임만 넘기고,
 * 어느 API 를 두드릴지는 여기서 [Game.newsSource] 로 고른다.
 *
 * - 호요버스 3게임: ennead 목록(`api.ennead.cc/mihoyo/{slug}/news/notices?lang=ko-kr`) + HoYoLab 본문
 * - 명조: [WuwaNewsApi] · 엔드필드: [EndfieldNewsApi]
 */
object NewsApi {
    /** @return 성공 시 공지 목록, **네트워크·파싱 실패 시 null**(빈 목록과 구분 — 호출부가 기존 값을 유지할 수 있게). */
    suspend fun notices(game: Game): List<NewsItem>? = when (game.newsSource) {
        NewsSource.ENNEAD -> enneadNotices(game)
        NewsSource.WUWA -> WuwaNewsApi.notices()
        NewsSource.ENDFIELD -> EndfieldNewsApi.notices()
        null -> emptyList()
    }

    /**
     * ennead 의 `info` 카테고리 — **버전 특별 방송 공지가 여기 올라온다**(`notices` 가 아니다).
     *
     * 목록에 합치지는 않는다. 공지 탭은 점검·업데이트 안내를 보는 자리인데 여기엔 캐릭터
     * 트레일러·배경화면 같은 홍보 글이 대부분이라, 섞으면 정작 볼 것이 묻힌다.
     * [BroadcastSchedule.parseConfirmed] 가 방송 글만 골라 쓰는 용도다.
     *
     * @return 실패 시 null — 호출부가 기존 값을 유지할 수 있게 빈 목록과 구분한다.
     */
    suspend fun info(game: Game): List<NewsItem>? {
        if (game.newsSource != NewsSource.ENNEAD) return emptyList()
        return enneadNotices(game, category = "info")
    }

    private suspend fun enneadNotices(game: Game, category: String = "notices"): List<NewsItem>? {
        val slug = game.newsSlug ?: return emptyList()
        val res = Net.get("https://api.ennead.cc/mihoyo/$slug/news/$category?lang=ko-kr")
        if (!res.isOk) return null
        return runCatching {
            val arr = JSONArray(res.body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title")
                // ko-kr 로 요청해도 번역 전 원문이 섞여 오는 건 게임사 공통이다(엔드필드·명조와 같은 규칙).
                if (title.isBlank() || !isKoreanNotice(title)) return@mapNotNull null
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
     * 공지 본문 — 항목의 출처([NewsItem.source])에 맞는 경로로 받는다.
     *
     * 실패 시 null → 호출부가 [NewsItem.summary] 로 폴백한다(출처 공통 규약).
     */
    suspend fun article(item: NewsItem): NewsArticle? = when (item.source) {
        NewsSource.ENNEAD -> hoyolabArticle(item.id)
        NewsSource.WUWA -> WuwaNewsApi.article(item)
        NewsSource.ENDFIELD -> EndfieldNewsApi.article(item)
    }

    /**
     * 호요버스 공지 본문 — HoYoLab 공개 아티클 API.
     *
     * 목록 API(ennead)의 `description` 도 본문 전문이긴 한데 **줄바꿈이 전부 제거된 통짜 평문**이라
     * 1만 자짜리 공지가 한 문단으로 쏟아지고 본문 이미지도 없다. 그래서 본문은 여기서 따로 받는다.
     *
     * `structured_content` 는 Quill delta(= `[{"insert": "텍스트"}, {"insert": {"image": "url"}}, …]`)라
     * 문단과 이미지가 원문 순서대로 들어 있다. 텍스트/이미지만 취하고 그 외(video·lottery 등)는 건너뛴다.
     *
     * 인증 불필요(공개 엔드포인트).
     */
    private suspend fun hoyolabArticle(postId: String): NewsArticle? {
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
