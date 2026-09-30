package com.gatcha.log.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.gatcha.log.data.GameData
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.ui.components.GiftCodeSkeleton
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.OdsButton
import com.gatcha.log.ui.components.OdsSize
import com.gatcha.log.ui.components.OdsTextField
import com.gatcha.log.ui.components.OdsVariant
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.OdsTabs
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.data.RedeemState
import com.gatcha.log.ui.theme.*

/** HoYoLAB 리딤코드 — 페이지 형식. 활성 코드 자동 수집 + 교환(단건/모두) + 직접 입력. */
@Composable
internal fun GiftCodePage(
    hoyolab: HoyolabConfig,
    state: RedeemState,
    activeCodes: List<GiftCode>,
    codesLoading: Boolean,
    codesFailed: Boolean,
    redeemedCodes: Set<String>,
    unusableCount: Int,
    /** force — 새로고침·다시 시도처럼 사용자가 직접 누를 때만 true(10분 캐시 무시). */
    onLoadCodes: (String, Boolean) -> Unit,
    onRedeem: (String, String) -> Unit,
    onRedeemAll: (String) -> Unit,
    onRestoreUnusable: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val games = remember(hoyolab) {
        buildList {
            if (hoyolab.genshinUid.isNotBlank()) add("genshin" to "원신")
            if (hoyolab.hsrUid.isNotBlank()) add("hsr" to "스타레일")
            if (hoyolab.zzzUid.isNotBlank()) add("zzz" to "젠레스")
        }
    }
    var selected by remember { mutableStateOf(games.firstOrNull()?.first ?: "genshin") }
    var code by remember { mutableStateOf("") }
    var showRedeemed by remember { mutableStateOf(false) }
    val loading = state is RedeemState.Loading
    // 선택 게임 바뀌면(최초 포함) 활성 코드 자동 수집
    LaunchedEffect(selected) { if (games.isNotEmpty()) onLoadCodes(selected, false) }
    val pending = activeCodes.count { it.code !in redeemedCodes }

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = glgDetailContentTop()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (games.isEmpty()) {
                GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("HoYoLAB 연동 후 UID가 있어야 코드를 교환할 수 있어요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(16.dp))
                }
            } else {
                // 게임 탭 — 호요랜드 일자 탭과 **같은 세그먼트 규격**이다([OdsTabs]).
                //
                // 칩 셋을 나란히 두면 서로 독립된 버튼처럼 보여, 지금 어느 게임의 코드를 보고
                // 있는지가 약하게 읽혔다. 트랙 하나에 담으면 배타 선택이라는 게 모양에서 나온다.
                OdsTabs(
                    labels = games.map { it.second },
                    selected = games.indexOfFirst { it.first == selected }.coerceAtLeast(0),
                    modifier = Modifier.padding(top = 4.dp),
                    onSelect = { selected = games[it].first },
                )
                // 활성 코드 카드
                GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("활성 코드 (자동 수집)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).clickable(enabled = !codesLoading) { onLoadCodes(selected, true) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (codesLoading) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = accent)
                                else Icon(Icons.Default.Refresh, "새 코드 새로고침", tint = accent, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // 잘못 가려진 코드를 되살리는 유일한 통로 — 가려진 게 있을 때만 보인다.
                        // (unusable_codes 는 로컬 전용이라 클라우드 복원으로도 안 풀린다)
                        if (unusableCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "가려진 코드 ${unusableCount}개",
                                    fontSize = 12.sp, color = TextSecondary,
                                )
                                Spacer(Modifier.width(8.dp))
                                OdsButton("되살리기", onClick = { onRestoreUnusable(selected) }, variant = OdsVariant.Secondary, size = OdsSize.XS)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        when {
                            codesLoading && activeCodes.isEmpty() -> GiftCodeSkeleton()
                            // 수집 실패는 '코드 없음'과 다르다 — 사유를 밝히고 재시도를 준다.
                            codesFailed && activeCodes.isEmpty() -> Column(Modifier.padding(vertical = 6.dp)) {
                                Text("코드를 불러오지 못했어요", fontSize = 12.sp, color = TextSecondary)
                                Spacer(Modifier.height(6.dp))
                                OdsButton("다시 시도", onClick = { onLoadCodes(selected, true) }, variant = OdsVariant.Secondary, size = OdsSize.S)
                            }
                            activeCodes.isEmpty() -> Text("지금은 활성 코드가 없어요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                            else -> {
                                val unredeemed = activeCodes.filter { it.code !in redeemedCodes }.sortedByDescending { it.highlight }
                                val redeemed = activeCodes.filter { it.code in redeemedCodes }
                                if (unredeemed.isEmpty()) {
                                    Text("받을 수 있는 새 코드가 없어요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                                } else {
                                    unredeemed.forEach { c ->
                                        CodeRow(c, redeemed = false, accent = accent, enabled = !loading) { onRedeem(selected, c.code) }
                                    }
                                }
                                if (redeemed.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showRedeemed = !showRedeemed }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(if (showRedeemed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("이미 받은 코드 ${redeemed.size}개", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                    }
                                    if (showRedeemed) redeemed.forEach { c ->
                                        CodeRow(c, redeemed = true, accent = accent, enabled = false) {}
                                    }
                                }
                            }
                        }
                    }
                }
                // 모두 교환
                if (pending > 0) {
                    OdsButton(
                        if (loading) "교환 중…" else "모두 교환 ($pending)",
                        onClick = { onRedeemAll(selected) },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 직접 입력 카드
                GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OdsTextField(
                            value = code,
                            onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() } },
                            label = "직접 입력 (새 코드)",
                            placeholder = "예: GENSHINGIFT",
                        )
                        if (code.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            OdsButton(
                                "이 코드 교환",
                                onClick = { onRedeem(selected, code.trim()); code = "" },
                                variant = OdsVariant.Secondary, size = OdsSize.XS, enabled = !loading,
                            )
                        }
                    }
                }
                when (state) {
                    is RedeemState.Loading -> Text("교환 중…", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(start = 2.dp))
                    is RedeemState.Done -> Text(
                        state.message,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (state.success) accent else DangerText,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                    else -> Text(
                        if (hoyolab.cookieToken.isBlank() && hoyolab.webCookie.isBlank()) "교환하려면 HoYoLAB 재연동(이메일 로그인)이 필요해요. 보상은 게임 우편함으로 와요."
                        else "코드를 눌러 교환하거나 '모두 교환'을 누르세요. 보상은 게임 우편함으로 와요.",
                        fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(start = 2.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        GlgDetailHeaderOverlay("리딤코드", onBack, scrollState = scrollState)
    }
}

/** 활성 코드 한 줄 — 코드 + 보상 + (교환/받음). 공방(공식방송) 코드는 강조 카드로 꾸민다. */
@Composable
private fun CodeRow(c: GiftCode, redeemed: Boolean, accent: Color, enabled: Boolean, onRedeem: () -> Unit) {
    val highlight = c.highlight && !redeemed
    val inner: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = if (highlight) 8.dp else 5.dp, horizontal = if (highlight) 10.dp else 0.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (highlight) {
                        Surface(color = accent, shape = RoundedCornerShape(6.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("공방", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        c.code,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (redeemed) TextSecondary else TextPrimary,
                        textDecoration = if (redeemed) TextDecoration.LineThrough else null,
                    )
                }
                if (c.rewards.isNotBlank()) Text(c.rewards, fontSize = 11.sp, color = TextSecondary, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            CopyCodeButton(c.code)
            Spacer(Modifier.width(6.dp))
            if (redeemed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("받음", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            } else {
                OdsButton(
                    "교환", onClick = onRedeem, enabled = enabled, size = OdsSize.XS,
                    variant = if (highlight) OdsVariant.Primary else OdsVariant.Secondary,
                )
            }
        }
    }
    if (highlight) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.5.dp, accent.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) { inner() }
    } else {
        inner()
    }
}

/**
 * 리딤코드 복사 버튼 — ‘교환’ 버튼과 같은 ODS XS Secondary. 탭하면 클립보드 저장 +
 * 아이콘이 잠깐 체크로 바뀌고 토스트로 안내.
 */
@Composable
private fun CopyCodeButton(code: String) {
    // LocalClipboardManager 는 deprecated → LocalClipboard(suspend setClipEntry) 사용.
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(1200); copied = false }
    }
    // '교환' 버튼과 같은 ODS XS Secondary.
    OdsButton(
        if (copied) "복사됨" else "복사",
        onClick = {
            scope.launch {
                val clip = android.content.ClipData.newPlainText("선물코드", code)
                clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(clip))
            }
            copied = true
            android.widget.Toast.makeText(context, "코드를 복사했어요", android.widget.Toast.LENGTH_SHORT).show()
        },
        variant = OdsVariant.Secondary, size = OdsSize.XS,
    )
}
