package com.gatcha.log.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 쿠키 수집용 인앱 WebView (expect/actual) — HoYoLAB 로그인 토큰 자동 추출에 사용.
 * - Android: WebView + CookieManager
 * - iOS: WKWebView + WKHTTPCookieStore
 *
 * [url] 을 로드하고, 페이지 로드가 끝날 때마다 [cookieDomains] 도메인들의 쿠키를
 * 병합(같은 키는 먼저 발견된 값 유지)해 [onPageLoaded] 로 전달한다.
 * 생성 시 기존 쿠키를 모두 제거(재연동 시 항상 새로 로그인).
 */
@Composable
expect fun CookieCollectingWebView(
    url: String,
    cookieDomains: List<String>,
    onPageLoaded: (cookies: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
)
