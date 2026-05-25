package com.gatcha.log.ui.game

import android.annotation.SuppressLint
import android.webkit.CookieManager
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

/**
 * HoYoLAB 로그인 WebView — 로그인하면 인증 쿠키(ltoken_v2 / ltuid_v2 / cookie_token_v2)를 자동 추출해
 * [onCollected](ltuid, ltoken, cookieToken) 로 전달한다. 쿠키는 기기의 WebView 에만 존재하며 외부로 전송하지 않는다.
 * (수동으로 토큰을 복사·붙여넣을 필요 없이 로그인만 하면 됨)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HoyolabLoginDialog(onCollected: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    val collectedCb = rememberUpdatedState(onCollected)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (collected) return
                                val raw = cm.getCookie("https://www.hoyolab.com") ?: return
                                val map = raw.split(";").mapNotNull {
                                    val p = it.trim().split("=", limit = 2)
                                    if (p.size == 2) p[0] to p[1] else null
                                }.toMap()
                                val ltoken = map["ltoken_v2"].orEmpty()
                                val ltuid = map["ltuid_v2"] ?: map["account_id_v2"] ?: map["account_id"].orEmpty()
                                val cookieToken = map["cookie_token_v2"] ?: map["cookie_token"].orEmpty()
                                if (ltoken.isNotBlank() && ltuid.isNotBlank()) {
                                    collected = true
                                    // raw = 전체 쿠키 문자열(account_mid_v2 등 포함) → 교환 인증에 그대로 사용
                                    collectedCb.value(ltuid, ltoken, cookieToken, raw)
                                }
                            }
                        }
                        loadUrl("https://www.hoyolab.com/home")
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
