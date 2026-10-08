package com.gatcha.log.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 원격에서 온 링크는 https 만, 업데이트 주소는 이 저장소의 릴리즈만 통과해야 한다(27.51.1 보안 점검). */
class SafeUrlTest {

    @Test
    fun `https 웹 주소만 통과한다`() {
        assertTrue(SafeUrl.isHttps("https://www.ticketlink.co.kr/product/52578"))
        assertTrue(SafeUrl.isHttps("  https://map.naver.com/p/search/%ED%82%A8  "))
        assertEquals("https://sites.google.com/view/2024hoyoland/", SafeUrl.https(" https://sites.google.com/view/2024hoyoland/ "))
        for (bad in listOf(
            "", "http://example.com", "tel:01012345678", "sms:1", "itms-services://?action=download-manifest&url=https://x.y/z.plist",
            "intent://scan/#Intent;scheme=zxing;end", "javascript:alert(1)", "file:///data/data/com.gatcha.log/shared_prefs/a.xml",
            "https://", "https:///path", "https://localhost/x", "https://github.com@evil.example/x", "https://exa mple.com/",
            "https://example.com\\@evil.example", "//example.com/x", "example.com",
        )) {
            assertFalse(SafeUrl.isHttps(bad), bad)
            assertEquals("", SafeUrl.https(bad), bad)
        }
    }

    @Test
    fun `호스트는 점 경계로 견준다`() {
        assertEquals("www.hoyolab.com", SafeUrl.host("https://WWW.HoYoLAB.com:443/home?x=1"))
        assertTrue(SafeUrl.hostIn("www.hoyolab.com", "hoyolab.com"))
        assertTrue(SafeUrl.hostIn("hoyolab.com", "hoyolab.com"))
        assertFalse(SafeUrl.hostIn("evilhoyolab.com", "hoyolab.com"))
        assertFalse(SafeUrl.hostIn("hoyolab.com.evil.example", "hoyolab.com"))
    }

    @Test
    fun `앱 스킴은 아는 것만`() {
        assertEquals("ticketlink://product/52578", SafeUrl.appScheme(" ticketlink://product/52578 "))
        for (bad in listOf("", "tel://1", "itms-services://x", "https://www.ticketlink.co.kr", "ticketlink:", "ticket link://x")) {
            assertEquals("", SafeUrl.appScheme(bad), bad)
        }
    }

    @Test
    fun `업데이트 주소는 이 저장소의 릴리즈만`() {
        val apk = "https://github.com/chbk1348/Gatcha-Log/releases/download/v27.51.0/Gatcha-Log-27.51.0.apk"
        assertTrue(SafeUrl.isReleaseApk(apk))
        assertFalse(SafeUrl.isReleaseApk("https://github.com/someone/Gatcha-Log/releases/download/v1/a.apk"))
        assertFalse(SafeUrl.isReleaseApk("https://github.com/chbk1348/Gatcha-Log-evil/releases/download/v1/a.apk"))
        assertFalse(SafeUrl.isReleaseApk("https://github.com/chbk1348/Gatcha-Log/releases/download/../../../x/y/a.apk"))
        assertFalse(SafeUrl.isReleaseApk("https://evil.example/a.apk"))
        assertFalse(SafeUrl.isReleaseApk(""))

        assertEquals(SafeUrl.RELEASES_URL, SafeUrl.releasePage(""))
        assertEquals(SafeUrl.RELEASES_URL, SafeUrl.releasePage("https://evil.example/ipa"))
        assertEquals(SafeUrl.RELEASES_URL, SafeUrl.releasePage("itms-services://?action=download-manifest"))
        assertEquals("https://github.com/chbk1348/Gatcha-Log/releases/tag/v27.51.1",
            SafeUrl.releasePage("https://github.com/chbk1348/Gatcha-Log/releases/tag/v27.51.1"))

        assertTrue(SafeUrl.isReleaseDownloadHost("https://github.com/chbk1348/Gatcha-Log/releases/download/v1/a.apk"))
        assertTrue(SafeUrl.isReleaseDownloadHost("https://release-assets.githubusercontent.com/github-production-release-asset/1?sig=x"))
        assertTrue(SafeUrl.isReleaseDownloadHost("https://objects.githubusercontent.com/x"))
        assertFalse(SafeUrl.isReleaseDownloadHost("https://githubusercontent.com.evil.example/x"))
        assertFalse(SafeUrl.isReleaseDownloadHost("http://github.com/x"))
    }

    @Test
    fun `sha256 은 hex 64자`() {
        assertTrue(SafeUrl.isSha256("a".repeat(64)))
        assertTrue(SafeUrl.isSha256("0123456789abcdefABCDEF".padEnd(64, '0')))
        assertFalse(SafeUrl.isSha256(""))
        assertFalse(SafeUrl.isSha256("a".repeat(63)))
        assertFalse(SafeUrl.isSha256("g".repeat(64)))
    }
}
