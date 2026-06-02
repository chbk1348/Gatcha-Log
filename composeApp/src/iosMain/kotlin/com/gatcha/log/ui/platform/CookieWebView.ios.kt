package com.gatcha.log.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 *
 * 수집 트리거 2중화:
 *  1. 페이지 로드 완료(didFinishNavigation) — 일반 내비게이션
 *  2. 1.5초 주기 폴링 — HoYoLAB 같은 SPA(JS 내비게이션)는 로그인 후 페이지 로드 이벤트가
 *     발생하지 않아 폴링 없이는 쿠키 수집이 트리거되지 않는다.
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

    // 쿠키 스토어 참조 — UIKitView factory 에서 설정됨
    val storeRef = remember { mutableStateOf<WKHTTPCookieStore?>(null) }

    // 마지막으로 콜백에 전달한 쿠키 맵 — 폴링이 같은 결과를 반복 발화하지 않도록 기억
    val lastEmitted = remember { mutableStateOf<Map<String, String>?>(null) }

    // 공통 쿠키 수집 — 도메인 매칭 + 병합 후 콜백.
    //
    // [onlyIfChanged]=true(폴링 경로)면 직전 발화 결과와 동일할 때 콜백을 생략한다.
    // 콜백 소비자(HoyolabLoginDialog)는 "페이지 로드 횟수" 기반 재시도 예산(ctRetries<4)으로
    // cookie_token_v2 를 기다리는데, 변화 없는 1.5초 폴링 틱이 매번 콜백을 발화하면
    // 예산이 ~6초 만에 소진되어 cookie_token_v2 없이 조기 확정(리딤코드 교환 불가)된다.
    // 변화가 있을 때만 발화하면 Android 의 onPageFinished(실제 내비게이션) 의미론과 같아진다.
    val collectCookies: (Boolean) -> Unit = remember(hosts) {
        { onlyIfChanged ->
            storeRef.value?.getAllCookies { cookies ->
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
                if (!onlyIfChanged || merged != lastEmitted.value) {
                    lastEmitted.value = merged
                    // 진단용 — 시스템 로그에서 "GatchaWebView" 로 검색
                    if (merged.isNotEmpty()) {
                        println("GatchaWebView: 쿠키 ${merged.size}개 수집 — ${merged.keys.joinToString(",")}")
                    }
                    callback.value(merged)
                }
            }
        }
    }

    // 네비게이션 델리게이트 — 페이지 로드 완료 시 쿠키 수집 (일반 내비게이션 대응, 항상 발화)
    val delegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                collectCookies(false)
            }
        }
    }

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration()
            val webView = WKWebView(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config)
            val store = config.websiteDataStore.httpCookieStore
            storeRef.value = store
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

    // SPA 대응: 1.5초 주기 쿠키 폴링 — 페이지 로드 이벤트 없이도 로그인 쿠키를 잡아낸다.
    // 쿠키 셋이 직전과 달라졌을 때만 콜백 발화 (위 collectCookies 주석 참고)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1500)
            collectCookies(true)
        }
    }
}
