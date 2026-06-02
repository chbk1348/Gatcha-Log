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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.spending.RedeemState
import com.gatcha.log.ui.theme.*

/** HoYoLAB 리딤코드 — 활성 코드 자동 수집 + 교환(단건/모두) + 직접 입력. */
@Composable
internal fun GiftCodeDialog(
    hoyolab: HoyolabConfig,
    state: RedeemState,
    activeCodes: List<GiftCode>,
    codesLoading: Boolean,
    redeemedCodes: Set<String>,
    onLoadCodes: (String) -> Unit,
    onRedeem: (String, String) -> Unit,
    onRedeemAll: (String) -> Unit,
    onDismiss: () -> Unit,
) {
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
    LaunchedEffect(selected) { if (games.isNotEmpty()) onLoadCodes(selected) }
    val pending = activeCodes.count { it.code !in redeemedCodes }

    GlgDialog(
        title = "리딤코드",
        onDismiss = onDismiss,
        confirmText = if (loading) "교환 중…" else "모두 교환",
        confirmEnabled = pending > 0 && !loading && games.isNotEmpty(),
        onConfirm = { onRedeemAll(selected) },
        dismissText = "닫기",
    ) {
        Column {
            if (games.isEmpty()) {
                Text("HoYoLAB 연동 후 UID가 있어야 코드를 교환할 수 있어요", fontSize = 13.sp, color = TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    games.forEach { (key, label) ->
                        val sel = key == selected
                        Surface(
                            modifier = Modifier.clickable { selected = key },
                            shape = RoundedCornerShape(20.dp),
                            color = if (sel) accent else Color(0xFFF2F2F6),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("활성 코드 (자동 수집)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).clickable(enabled = !codesLoading) { onLoadCodes(selected) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (codesLoading) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = accent)
                        else Icon(Icons.Default.Refresh, "새 코드 새로고침", tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                when {
                    codesLoading && activeCodes.isEmpty() -> Text("코드 불러오는 중…", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                    activeCodes.isEmpty() -> Text("지금은 활성 코드가 없어요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                    else -> {
                        // 미수령 코드(공방 우선) 노출 + 이미 받은 코드는 접기
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
                Spacer(Modifier.height(14.dp))
                GlgTextField(
                    value = code,
                    onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() } },
                    label = "직접 입력 (새 코드)",
                    placeholder = "예: GENSHINGIFT",
                )
                if (code.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.clickable(enabled = !loading) { onRedeem(selected, code.trim()); code = "" },
                        shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    ) {
                        Text("이 코드 교환", modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (state) {
                    is RedeemState.Loading -> Text("교환 중…", fontSize = 12.sp, color = TextSecondary)
                    is RedeemState.Done -> Text(
                        state.message,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (state.success) accent else DangerText,
                    )
                    else -> Text(
                        if (hoyolab.cookieToken.isBlank() && hoyolab.webCookie.isBlank()) "교환하려면 HoYoLAB 재연동(이메일 로그인)이 필요해요. 보상은 게임 우편함으로 와요."
                        else "코드를 눌러 교환하거나 '모두 교환'을 누르세요. 보상은 게임 우편함으로 와요.",
                        fontSize = 11.sp, color = TextSecondary,
                    )
                }
            }
        }
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
            if (redeemed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("받음", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    modifier = Modifier.clickable(enabled = enabled) { onRedeem() },
                    shape = RoundedCornerShape(16.dp),
                    color = if (highlight) accent else accent.copy(alpha = 0.12f),
                    border = if (highlight) null else BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                ) {
                    Text("교환", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, color = if (highlight) Color.White else accent, fontWeight = FontWeight.Bold)
                }
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
