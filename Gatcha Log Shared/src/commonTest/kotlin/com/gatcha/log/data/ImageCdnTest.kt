package com.gatcha.log.data

import com.gatcha.log.data.api.ImageCdn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 썸네일 축소 URL 규칙 — **어디에 붙이고 어디에 안 붙이는지**를 고정한다.
 *
 * 화이트리스트가 무너지면 조용히 손해만 본다: 명조는 파라미터를 무시해 원본을 그대로 주면서
 * 쿼리 때문에 캐시 키만 갈라지고, act-webstatic 은 되레 커진다. 눈에 띄는 증상이 없어서
 * 코드 리뷰로는 잡히지 않는 자리라 테스트로 박아 둔다.
 */
class ImageCdnTest {

    private val HOYO = "https://upload-os-bbs.hoyolab.com/upload/2026/08/15/0/a_1.jpeg"

    @Test
    fun 축소가_확인된_호스트만_바꾼다() {
        assertTrue(ImageCdn.thumb(HOYO, 156)!!.startsWith("$HOYO?x-oss-process=image/resize,"))
        assertTrue(ImageCdn.thumb("https://web-static.hg-cdn.com/upload/image/a.png", 156) != null)
    }

    @Test
    fun 이득이_없는_호스트는_건드리지_않는다() {
        // 명조 — 파라미터를 무시한다(쿼리만 붙어 캐시 키가 갈라진다).
        assertNull(ImageCdn.thumb("https://aki-gm-resources-back.aki-game.net/a.png", 156))
        // 호요버스 정적 리소스 — 되레 커졌다(2026-08-19 실측).
        assertNull(ImageCdn.thumb("https://act-webstatic.hoyoverse.com/a.png", 156))
    }

    @Test
    fun 폭은_단계로_스냅한다() {
        // 기기 배율마다 URL 이 갈라지면 CDN 엣지·앱 디스크 캐시가 둘 다 파편화된다.
        assertEquals(ImageCdn.thumb(HOYO, 150), ImageCdn.thumb(HOYO, 200))
        assertTrue(ImageCdn.thumb(HOYO, 150)!!.contains("w_200"))
        assertTrue(ImageCdn.thumb(HOYO, 201)!!.contains("w_400"))
    }

    @Test
    fun 축소가_손해인_경우엔_원본을_쓴다() {
        // 가장 큰 단계보다 넓게 그리는 자리 = 축소할 이유가 없다.
        assertNull(ImageCdn.thumb(HOYO, 2000))
        assertNull(ImageCdn.thumb(HOYO, 0))
    }

    @Test
    fun 이미_쿼리가_있으면_손대지_않는다() {
        // 서명·처리 파라미터가 있을 수 있고, 뒤에 덧붙이면 원래 의미까지 깨진다.
        assertNull(ImageCdn.thumb("$HOYO?sig=abc", 156))
    }

    /**
     * 높이를 주면 **그 상자에 맞춰 채우고 자른다**(`m_fill`).
     *
     * 폭만 맞추면 초와이드 원본이 뭉개진다 — 엔드필드 공지 본문 머리 이미지는 1650×300(5.5:1)이라
     * `w_200` 이면 **200×36** 이 온다. 52×36dp 자리를 Crop 으로 채우느라 세로를 3배 늘려 그렸다
     * (2026-08-26 실측: `m_fill,w_200,h_140` 은 200×140 · 6.2KB).
     */
    @Test
    fun 높이를_주면_상자에_맞춰_자른다() {
        val u = ImageCdn.thumb(HOYO, 156, 108)!!
        assertTrue(u.contains("m_fill"), u)
        assertTrue(u.contains("w_200"), u)
        assertTrue(u.contains("h_138"), u) // 108 * 200 / 156
    }

    @Test
    fun 높이가_없으면_폭만_맞춘다() {
        // 상세 본문처럼 높이가 정해지지 않은 자리 — 잘라내면 안 된다.
        val u = ImageCdn.thumb(HOYO, 156, 0)!!
        assertTrue(u.contains("w_200"), u)
        assertTrue(!u.contains("m_fill"), u)
        assertEquals(ImageCdn.thumb(HOYO, 156), u)
    }

    @Test
    fun 표시_비율이_같으면_기기_배율이_달라도_같은_URL() {
        // 2배(104×72)와 3배(156×108)가 같은 자리를 그린다 — 캐시가 갈라지면 같은 그림을 두 번 받는다.
        assertEquals(ImageCdn.thumb(HOYO, 104, 72), ImageCdn.thumb(HOYO, 156, 108))
    }
}
