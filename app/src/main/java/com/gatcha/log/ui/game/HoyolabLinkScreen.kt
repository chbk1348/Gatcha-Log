package com.gatcha.log.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary

/**
 * HoYoLAB 계정 연동 **페이지**(모달 대체). 상단 "로그인으로 자동 가져오기"(WebView 쿠키 추출) + 수동 입력 필드.
 */
@Composable
fun HoyolabLinkScreen(config: HoyolabConfig, onSave: (HoyolabConfig) -> Unit, onBack: () -> Unit) {
    val accent = LocalAccent.current
    var ltuid by remember { mutableStateOf(config.ltuid) }
    var ltoken by remember { mutableStateOf(config.ltoken) }
    var cookieToken by remember { mutableStateOf(config.cookieToken) }
    var gi by remember { mutableStateOf(config.genshinUid) }
    var hsr by remember { mutableStateOf(config.hsrUid) }
    var zzz by remember { mutableStateOf(config.zzzUid) }
    var showLogin by remember { mutableStateOf(false) }
    var collectedMsg by remember { mutableStateOf<String?>(null) }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GlgScreenHeader("HoYoLAB 계정 연동", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 로그인으로 자동 가져오기 (WebView → 쿠키 추출)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showLogin = true },
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Login, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("HoYoLAB 로그인으로 자동 가져오기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
                        Text("로그인하면 ltuid·ltoken·cookie_token을 자동 입력해요", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
            collectedMsg?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = accent) }

            Text("쿠키(ltuid·ltoken)는 개인 정보입니다. 타인과 공유하지 마세요. 수동 입력도 가능해요.", fontSize = 11.sp, color = TextSecondary)
            GlgTextField(ltuid, { ltuid = it }, label = "ltuid", modifier = Modifier.fillMaxWidth())
            GlgTextField(ltoken, { ltoken = it }, label = "ltoken", modifier = Modifier.fillMaxWidth())
            GlgTextField(cookieToken, { cookieToken = it }, label = "cookie_token (선물코드 교환용·선택)", modifier = Modifier.fillMaxWidth())
            GlgTextField(gi, { gi = it }, label = "원신 UID", modifier = Modifier.fillMaxWidth())
            GlgTextField(hsr, { hsr = it }, label = "스타레일 UID", modifier = Modifier.fillMaxWidth())
            GlgTextField(zzz, { zzz = it }, label = "젠레스 UID", modifier = Modifier.fillMaxWidth())
            Text("구글 로그인 시 연동 정보가 계정에 함께 동기화돼 다른 기기에서도 그대로 사용돼요.", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
        }
        GlgButton(
            "저장",
            onClick = {
                onSave(
                    HoyolabConfig(
                        ltuid = ltuid.trim(), ltoken = ltoken.trim(),
                        genshinUid = gi.trim(), hsrUid = hsr.trim(), zzzUid = zzz.trim(),
                        cookieToken = cookieToken.trim(),
                    ),
                )
            },
            // 하단 내비바(시스템 네비 + 알약 ~88dp) 위로 띄워 겹침 방지
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 12.dp, bottom = 96.dp),
            height = 54.dp,
        )
    }

    if (showLogin) {
        HoyolabLoginDialog(
            onCollected = { u, t, c ->
                ltuid = u; ltoken = t
                if (c.isNotBlank()) cookieToken = c
                showLogin = false
                collectedMsg = "토큰을 가져왔어요" + if (c.isBlank()) " (cookie_token 없음 — 교환은 수동 입력 필요)" else " (cookie_token 포함)"
            },
            onDismiss = { showLogin = false },
        )
    }
}
