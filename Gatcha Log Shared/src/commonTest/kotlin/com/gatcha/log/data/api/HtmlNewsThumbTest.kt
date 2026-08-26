package com.gatcha.log.data.api

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 본문 첫 이미지 추출 — 목록 썸네일이 여기 달려 있다.
 *
 * 엔드필드 공지는 `displayType` 에 따라 배너 필드와 본문이 **상호배타적**이다(2026-08-26 실측 34건):
 * `picture` 8건만 `data.url` 을 주고, `rich_text` 26건은 url 없이 본문 첫머리 `<img>` 가 전부다.
 * 그래서 이 함수가 죽으면 목록의 4분의 3이 조용히 썸네일 없는 행이 된다 — 크래시도 로그도 없어
 * 리뷰로는 안 잡히는 자리라 고정해 둔다.
 */
class HtmlNewsThumbTest {

    @Test
    fun 첫_이미지를_뽑는다() {
        val html = """<img src="https://web-static.hg-cdn.com/upload/image/a.jpg" data-width="1650"><p>본문</p>"""
        assertEquals("https://web-static.hg-cdn.com/upload/image/a.jpg", HtmlNews.firstImageSrc(html))
    }

    @Test
    fun 이미지가_여럿이면_첫_장만() {
        val html = """<p>머리말</p><img src="https://x/1.jpg"><img src="https://x/2.jpg">"""
        assertEquals("https://x/1.jpg", HtmlNews.firstImageSrc(html))
    }

    @Test
    fun 이미지가_없으면_빈_문자열() {
        assertEquals("", HtmlNews.firstImageSrc("<p>글만 있는 공지</p>"))
        assertEquals("", HtmlNews.firstImageSrc(""))
    }

    /** 상류 HTML 은 `&amp;` 로 이스케이프해 온다 — 그대로 요청하면 404 다. */
    @Test
    fun 엔티티를_풀어서_준다() {
        val html = """<img src="https://x/a.jpg?w=1&amp;h=2">"""
        assertEquals("https://x/a.jpg?w=1&h=2", HtmlNews.firstImageSrc(html))
    }

    /** 홑따옴표·속성 순서는 상류 편집기마다 다르다. */
    @Test
    fun 홑따옴표와_속성_순서를_가리지_않는다() {
        assertEquals("https://x/a.jpg", HtmlNews.firstImageSrc("<img alt='x' src='https://x/a.jpg'>"))
    }
}
