package com.gatcha.log.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/**
 * iOS WKWebView + WKHTTPCookieStore — Android CookieManager 수집 로직과 동일한 동작.
 * 도메인 매칭: 쿠키의 domain 속성이 요청 도메인의 호스트와 부분 일치하면 수집
 * (HoYoLAB 쿠키는 ".hoyolab.com" 도메인으로 발행되므로 모든 서브도메인 쿠키가 잡힘).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CookieCollectingWebView(
    url: String,
    cookieDomains: List<String>,
    onPageLoaded: (cookies: Map<String, String>) -> Unit,
    modifier: Modifier,
) {
    val callback = rememberUpdatedState(onPageLoaded)

    // 요청 도메인들의 호스트만 추출 ("https://www.hoyolab.com" → "www.hoyolab.com")
    val hosts = remember(cookieDomains) {
        cookieDomains.mapNotNull { NSURL.URLWithString(it)?.host }
    }

    // 네비게이션 델리게이트 — 페이지 로드 완료 시 쿠키 수집
    val delegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            var cookieStore: WKHTTPCookieStore? = null

            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                val store = cookieStore ?: return
                store.getAllCookies { cookies ->
                    val merged = LinkedHashMap<String, String>()
                    @Suppress("UNCHECKED_CAST")
                    (cookies as? List<NSHTTPCookie>)?.forEach { cookie ->
                        val cookieDomain = cookie.domain.removePrefix(".")
                        // ".hoyolab.com" 쿠키 ↔ "www.hoyolab.com" 요청 호스트 양방향 부분 일치
                        val matches = hosts.any { host ->
                            host.endsWith(cookieDomain) || cookieDomain.endsWith(host.substringAfter("."))
                        }
                        if (matches && cookie.value.isNotBlank() && !merged.containsKey(cookie.name)) {
                            merged[cookie.name] = cookie.value
                        }
                    }
                    callback.value(merged)
                }
            }
        }
    }

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration()
            val webView = WKWebView(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config)
            val store = config.websiteDataStore.httpCookieStore
            delegate.cookieStore = store
            webView.navigationDelegate = delegate

            // 재연동: 기존 쿠키 제거 후 로드 → 항상 새로 로그인하게 함
            WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { cookies ->
                @Suppress("UNCHECKED_CAST")
                (cookies as? List<NSHTTPCookie>)?.forEach { store.deleteCookie(it, null) }
            }
            NSURL.URLWithString(url)?.let { webView.loadRequest(NSURLRequest.requestWithURL(it)) }
            webView
        },
        modifier = modifier,
    )
}
