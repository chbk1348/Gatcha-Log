package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gatcha.log.ui.platform.CookieCollectingWebView
import com.gatcha.log.ui.theme.FixedFontScale
import com.gatcha.log.ui.theme.TextSecondary

/**
 * HoYoLAB 로그인 WebView — 로그인하면 인증 쿠키(ltoken_v2 / ltuid_v2 / cookie_token_v2 + 전체 쿠키)를 자동 추출해
 * [onCollected](ltuid, ltoken, cookieToken, webCookie) 로 전달한다.
 * 추출된 토큰은 이 기기의 암호화 저장소(EncryptedSharedPreferences)에만 보관되며,
 * 클라우드(Firestore)·백업 스냅샷에는 포함되지 않는다(기기 밖으로 전송하지 않음).
 * (수동으로 토큰을 복사·붙여넣을 필요 없이 로그인만 하면 됨)
 */
@Composable
fun HoyolabLoginDialog(onCollected: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    val collectedCb = rememberUpdatedState(onCollected)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // 다이얼로그는 별도 윈도우라 시스템 폰트 스케일을 다시 가져옴 → 여기서 다시 1.0 고정
        FixedFontScale {
        // 풀스크린 다이얼로그는 자체 윈도우에 그려져 화면의 상단 인셋을 상속받지 않는다 —
        // statusBarsPadding 이 없으면 iOS 노치/다이나믹 아일랜드가 제목·닫기 버튼을 가린다.
        // (배경 White 는 패딩보다 먼저 적용해 상태바 뒤까지 채운다)
        Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("HoYoLAB 로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("로그인하면 연동 토큰을 자동으로 가져옵니다", fontSize = 11.sp, color = TextSecondary)
                }
                // iOS 시스템 클로즈 버튼 스타일 (회색 원형 + X) — 지출 추가 모달의 닫기 버튼과 동일한 토큰
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE6E6EB)).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", modifier = Modifier.size(16.dp), tint = TextSecondary)
                }
            }
            // 쿠키 수집은 플랫폼별 WebView(expect/actual)가 담당. 페이지 로드가 끝날 때마다
            // 여러 도메인 쿠키를 병합한 맵이 onPageLoaded 로 전달된다 — 추출 로직은 원본 onPageFinished 와 동일.
            var collected by remember { mutableStateOf(false) }
            var ctRetries by remember { mutableIntStateOf(0) }
            CookieCollectingWebView(
                url = "https://www.hoyolab.com/home",
                // cdkey 교환(webExchangeCdkey)은 cookie_token_v2 로 인증한다. 이 값은 ltoken_v2 보다
                // 늦게, www 가 아닌 account/.hoyolab.com 도메인에 발행되므로 여러 도메인 쿠키를 병합 수집한다.
                cookieDomains = listOf(
                    "https://www.hoyolab.com",
                    "https://account.hoyolab.com",
                    "https://act.hoyolab.com",
                    "https://api-account-os.hoyolab.com",
                ),
                onPageLoaded = { merged ->
                    if (collected) return@CookieCollectingWebView
                    val ltoken = merged["ltoken_v2"].orEmpty()
                    val ltuid = merged["ltuid_v2"] ?: merged["account_id_v2"] ?: merged["account_id"].orEmpty()
                    val cookieToken = merged["cookie_token_v2"] ?: merged["cookie_token"].orEmpty()
                    if (ltoken.isNotBlank() && ltuid.isNotBlank()) {
                        // cookie_token_v2 가 아직 안 들어왔으면 다음 페이지 로드까지 잠깐 대기(최대 4회). 끝내 없으면 폴백 수집.
                        if (cookieToken.isBlank() && ctRetries < 4) { ctRetries++; return@CookieCollectingWebView }
                        collected = true
                        // 병합된 전체 쿠키 문자열(account_mid_v2·cookie_token_v2 등 포함) → 교환 인증에 그대로 사용
                        val raw = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
                        collectedCb.value(ltuid, ltoken, cookieToken, raw)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        } // FixedFontScale
    }
}
