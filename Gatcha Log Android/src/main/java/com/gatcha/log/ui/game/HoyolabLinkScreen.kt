package com.gatcha.log.ui.game

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.gatcha.log.ui.profile.NotifyCard
import com.gatcha.log.ui.profile.NotifyGroupTitle
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
import com.gatcha.log.data.api.HoyolabApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.clip
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgBackButton
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.OdsButton
import com.gatcha.log.ui.components.OdsSize
import com.gatcha.log.ui.components.OdsTextField
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
    var webCookie by remember { mutableStateOf(config.webCookie) }
    var gi by remember { mutableStateOf(config.genshinUid) }
    var hsr by remember { mutableStateOf(config.hsrUid) }
    var zzz by remember { mutableStateOf(config.zzzUid) }
    var showLogin by remember { mutableStateOf(false) }
    var showEmailGuide by remember { mutableStateOf(false) }
    var collectedMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            // 스크롤 영역은 **저장 바 위에서 끝난다**(bottom 76) — 바 밑까지 두면 포커스된 입력칸이 스크롤로 보이는
            // 자리에 와도 바에 가렸다(9/30). 키보드가 뜨면 루트 imePadding 으로 영역이 줄고 입력칸이 스스로 스크롤돼 온다.
            Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 76.dp).verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = glgDetailContentTop()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 주의 문구는 맨 위 배너 하나로(9/30) — 흩어져 있던 세 문구를 합쳤다. iOS 와 같은 문구.
            HoyolabNotice(Modifier.padding(top = 4.dp))
            // 설정 하위 페이지 다듬기(9/29) — 온보딩 ④와 같은 남색 로그인 카드 + 묶음 제목 + 흰 카드.
            // 로그인으로 자동 가져오기 (WebView → 쿠키 추출)
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0F1A33), Color(0xFF23345C))))
                    .clickable { showEmailGuide = true }.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (config.isLinked) "연동됨 · 다시 가져오기" else "로그인으로 자동 가져오기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8FE3DA))
                    Text("HoYoLAB 로그인", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("ltuid · ltoken · cookie_token · UID 를 자동 입력해요", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.7f))
                }
                Icon(Icons.AutoMirrored.Filled.Login, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            collectedMsg?.let {
                Text(
                    it, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF177881),
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEF8F8)).padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }

            NotifyGroupTitle("계정 토큰", "직접 입력해도 돼요")
            NotifyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OdsTextField(ltuid, { ltuid = it }, label = "ltuid", modifier = Modifier.fillMaxWidth())
                    OdsTextField(ltoken, { ltoken = it }, label = "ltoken", modifier = Modifier.fillMaxWidth())
                    OdsTextField(cookieToken, { cookieToken = it }, label = "cookie_token (리딤코드 교환용·선택)", modifier = Modifier.fillMaxWidth())
                }
            }
            NotifyGroupTitle("게임 UID", "로그인하면 자동으로 채워져요")
            NotifyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OdsTextField(gi, { gi = it }, label = "원신 UID", modifier = Modifier.fillMaxWidth())
                    OdsTextField(hsr, { hsr = it }, label = "스타레일 UID", modifier = Modifier.fillMaxWidth())
                    OdsTextField(zzz, { zzz = it }, label = "젠레스 UID", modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // 「저장」은 하단에 상시 고정 — 예산 관리와 같은 바(스크롤해도 늘 보인다). iOS 와 같은 자리.
        OdsButton(
            "저장",
            onClick = {
                onSave(
                    HoyolabConfig(
                        ltuid = ltuid.trim(), ltoken = ltoken.trim(),
                        genshinUid = gi.trim(), hsrUid = hsr.trim(), zzzUid = zzz.trim(),
                        cookieToken = cookieToken.trim(), webCookie = webCookie,
                    ),
                )
            },
            size = OdsSize.L,
            // 키보드 여백은 앱 루트(MainActivity)가 준다 — 여기서 더하면 두 배로 떴다(9/30 S23).
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .shadow(8.dp, RectangleShape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
                .background(Color.White).navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        )
        GlgDetailHeaderOverlay("HoYoLAB 계정 연동", onBack, scrollState = scrollState)
    }

    if (showLogin) {
        HoyolabLoginDialog(
            onCollected = { u, t, c, raw ->
                ltuid = u; ltoken = t
                if (c.isNotBlank()) cookieToken = c
                webCookie = raw // 전체 쿠키 보존 → 교환 인증에 그대로 사용
                showLogin = false
                collectedMsg = "토큰을 가져왔어요. 게임 UID 확인 중…"
                // 토큰으로 게임 UID 자동 조회(getGameRecordCard)
                scope.launch {
                    val ck = if (c.isBlank()) " · cookie_token 없음(교환은 수동)" else " · cookie_token 포함"
                    // null = 네트워크 실패 — 계정에 UID 가 없는 것과 구분해 안내한다.
                    val uids = withContext(Dispatchers.IO) { HoyolabApi.fetchGameUids(u, t) }
                    if (uids == null) {
                        collectedMsg = "토큰 가져옴 (네트워크 오류로 UID 조회 못 함 — 수동 입력)$ck"
                        return@launch
                    }
                    uids["genshin"]?.let { gi = it }
                    uids["hsr"]?.let { hsr = it }
                    uids["zzz"]?.let { zzz = it }
                    collectedMsg = if (uids.isNotEmpty()) "토큰 + UID ${uids.size}개 자동 입력 완료$ck"
                    else "토큰 가져옴 (UID 자동조회 실패 — 수동 입력)$ck"
                }
            },
            onDismiss = { showLogin = false },
        )
    }

    // 이메일 로그인 강제 안내 — 소셜 로그인은 cookie_token 등 일부 토큰을 못 가져올 수 있음
    if (showEmailGuide) {
        GlgDialog(
            title = "이메일 로그인 필수",
            onDismiss = { showEmailGuide = false },
            confirmText = "이메일로 로그인",
            onConfirm = { showEmailGuide = false; showLogin = true },
            dismissText = "취소",
        ) {
            Text(
                "토큰을 정상적으로 가져오려면 다음 화면에서 반드시 ‘이메일(비밀번호) 로그인’ 을 사용하세요.\n\n" +
                    "구글·애플 등 소셜 로그인은 cookie_token 등 일부 정보를 가져오지 못해 리딤코드 교환이 안 될 수 있어요.",
                fontSize = 13.sp, color = TextSecondary,
            )
        }
    }
}

private val HoyolabNoticeLines = listOf(
    "비공식 연동이에요. 토큰은 이 기기에만 저장되고 백업 · 동기화되지 않아요.",
    "게임 UID 만 계정에 동기화돼요. 새 기기에서는 다시 로그인해 토큰을 가져와 주세요.",
    "토큰은 개인 정보예요. 다른 사람과 공유하지 마세요."
)

/** 주의 배너 — 설정 경고 띠와 같은 주황 톤. */
@Composable
private fun HoyolabNotice(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Color(0xFFFFF4E8)).border(1.dp, Color(0xFFFED7AA), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFC2410C), modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("연동 전에 확인해 주세요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
            HoyolabNoticeLines.forEach { Text("· $it", fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF7C2D12)) }
        }
    }
}
