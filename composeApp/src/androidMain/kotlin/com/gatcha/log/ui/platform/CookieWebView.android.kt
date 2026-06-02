package com.gatcha.log.ui.platform

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Android WebView + CookieManager — :app 의 HoyolabLoginDialog 내부 구현과 동일 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun CookieCollectingWebView(
    url: String,
    cookieDomains: List<String>,
    onPageLoaded: (cookies: Map<String, String>) -> Unit,
    modifier: Modifier,
) {
    val callback = rememberUpdatedState(onPageLoaded)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            // 재연동: 기존 세션 쿠키 제거 → 항상 새로 로그인하게 함
            cm.removeAllCookies(null)
            cm.flush()
            WebView(ctx).apply {
                cm.setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        // 여러 도메인 쿠키를 병합 수집(같은 키는 먼저 발견된 값 유지)
                        val merged = LinkedHashMap<String, String>()
                        cookieDomains.forEach { domain ->
                            cm.getCookie(domain)?.split(";")?.forEach { c ->
                                val p = c.trim().split("=", limit = 2)
                                if (p.size == 2 && p[1].isNotBlank() && !merged.containsKey(p[0])) merged[p[0]] = p[1]
                            }
                        }
                        callback.value(merged)
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}
