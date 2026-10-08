package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.LightSystemBarsInWindow
import com.gatcha.log.ui.components.SecureWindow
import com.gatcha.log.util.SafeUrl
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.LocalAccentTint

/**
 * HoYoLAB 로그인 WebView — 로그인하면 인증 쿠키(ltoken_v2 / ltuid_v2 / cookie_token_v2 + 전체 쿠키)를 자동 추출해
 * [onCollected](ltuid, ltoken, cookieToken, webCookie) 로 전달한다.
 * 추출된 토큰은 이 기기의 암호화 저장소(EncryptedSharedPreferences)에만 보관되며,
 * 클라우드(Firestore)·백업 스냅샷에는 포함되지 않는다(기기 밖으로 전송하지 않음).
 * (수동으로 토큰을 복사·붙여넣을 필요 없이 로그인만 하면 됨)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HoyolabLoginDialog(onCollected: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    val collectedCb = rememberUpdatedState(onCollected)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LightSystemBarsInWindow()
        SecureWindow()   // 로그인 화면은 캡처 · 최근 앱 미리보기에 남기지 않는다
        Column(Modifier.fillMaxSize().background(LocalAccentTint.current)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("HoYoLAB 로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("로그인하면 연동 토큰을 자동으로 가져옵니다", fontSize = 11.sp, color = TextSecondary)
                }
                Icon(Icons.Default.Close, "닫기", modifier = Modifier.clickable { onDismiss() })
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
                        var collected = false
                        var ctRetries = 0
                        webViewClient = object : WebViewClient() {
                            // 이 창은 주소 표시줄이 없다 — 호요버스 밖으로 나가면 사용자는 그걸 알 길이 없어
                            // 닮은 로그인 페이지에 비밀번호를 넣게 된다. 본 프레임 이동은 호요버스 도메인만 허용한다(27.51.1).
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                if (request == null || !request.isForMainFrame) return false
                                return !isHoyoverseHost(SafeUrl.host(request.url?.toString()))
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (collected) return
                                // cdkey 교환(webExchangeCdkey)은 cookie_token_v2 로 인증한다. 이 값은 ltoken_v2 보다
                                // 늦게, www 가 아닌 account/.hoyolab.com 도메인에 발행되므로 여러 도메인 쿠키를 병합 수집한다.
                                val merged = LinkedHashMap<String, String>()
                                listOf(
                                    "https://www.hoyolab.com",
                                    "https://account.hoyolab.com",
                                    "https://act.hoyolab.com",
                                    "https://api-account-os.hoyolab.com",
                                ).forEach { u ->
                                    cm.getCookie(u)?.split(";")?.forEach { c ->
                                        val p = c.trim().split("=", limit = 2)
                                        if (p.size == 2 && p[1].isNotBlank() && !merged.containsKey(p[0])) merged[p[0]] = p[1]
                                    }
                                }
                                val ltoken = merged["ltoken_v2"].orEmpty()
                                val ltuid = merged["ltuid_v2"] ?: merged["account_id_v2"] ?: merged["account_id"].orEmpty()
                                val cookieToken = merged["cookie_token_v2"] ?: merged["cookie_token"].orEmpty()
                                if (ltoken.isNotBlank() && ltuid.isNotBlank()) {
                                    // cookie_token_v2 가 아직 안 들어왔으면 다음 페이지 로드까지 잠깐 대기(최대 4회). 끝내 없으면 폴백 수집.
                                    if (cookieToken.isBlank() && ctRetries < 4) { ctRetries++; return }
                                    collected = true
                                    // 병합된 전체 쿠키 문자열(account_mid_v2·cookie_token_v2 등 포함) → 교환 인증에 그대로 사용
                                    val raw = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
                                    collectedCb.value(ltuid, ltoken, cookieToken, raw)
                                    // 토큰은 이제 암호화 저장소에 있다 — 웹뷰 쪽 세션은 지운다(평문 쿠키 파일에 남지 않게).
                                    clearHoyolabWebSession()
                                }
                            }
                        }
                        loadUrl("https://www.hoyolab.com/home")
                    }
                },
                onRelease = { it.destroy(); clearHoyolabWebSession() },
            )
        }
    }
}

/** 로그인 창이 본 프레임으로 가도 되는 곳 — 호요버스 계열 도메인과 그 하위. */
private fun isHoyoverseHost(host: String): Boolean =
    listOf("hoyolab.com", "hoyoverse.com", "mihoyo.com").any { SafeUrl.hostIn(host, it) }

/**
 * 웹뷰에 남은 HoYoLAB 세션(쿠키 · 웹 저장소)을 지운다.
 * 이 앱에서 웹뷰는 이 로그인 창 하나뿐이라 통째로 비워도 다른 것이 딸려 나가지 않는다.
 */
internal fun clearHoyolabWebSession() {
    runCatching {
        CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        WebStorage.getInstance().deleteAllData()
    }
}
