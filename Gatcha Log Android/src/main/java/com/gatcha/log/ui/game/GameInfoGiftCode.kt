package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.GldsHairline
import com.gatcha.log.ui.components.GlgLineIcons
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDialog
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsSize
import com.gatcha.log.ui.components.GldsTextField
import com.gatcha.log.ui.components.GldsVariant
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GldsTabs
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.data.RedeemState
import com.gatcha.log.ui.theme.*

/** HoYoLAB 리딤코드 — 페이지 형식. 활성 코드 자동 수집 + 교환(단건/모두). 직접 입력은 헤더 + 버튼 → 가운데 모달(10/15). */
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
    /** 직접 입력 모달의 교환 — 코드가 틀리면 토스트 대신 [RedeemState.Done.inputError] 로 돌아온다. */
    onRedeemTyped: (String, String) -> Unit,
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
    var showInput by remember { mutableStateOf(false) }
    var showRedeemed by remember { mutableStateOf(false) }
    val loading = state is RedeemState.Loading
    // 선택 게임 바뀌면(최초 포함) 활성 코드 자동 수집
    LaunchedEffect(selected) { if (games.isNotEmpty()) onLoadCodes(selected, false) }
    val pending = activeCodes.count { it.code !in redeemedCodes }

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    // 카드는 걷었다(10/1) — 흰 바탕 · 화면 폭 섹션(좌우 20 · 위 22 · 아래 20), 섹션 사이는 GiBand.
    val scrollState = rememberScrollState()
    val section = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp)
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scrollState)
                .padding(top = glgDetailContentTop()),
        ) {
            if (games.isEmpty()) {
                Text("HoYoLAB 연동 후 UID가 있어야 코드를 교환할 수 있어요", fontSize = 13.sp, color = TextSecondary, modifier = section)
            } else {
                // 활성 코드 섹션 — 게임 탭 · 코드 목록 · 모두 교환
                // 아래 여백 = 20 − 마지막 요소 자체 여백 — 띠까지 눈에 20(10/1). 마지막이 무엇인지는 목록 상태로 정한다.
                val tail = when {
                    pending > 0 -> 0                                   // 모두 교환 버튼
                    codesLoading && activeCodes.isEmpty() -> 0         // 뼈대
                    activeCodes.isEmpty() -> 6                         // 실패 · 빈 문구(위아래 6)
                    activeCodes.all { it.code in redeemedCodes } && !showRedeemed -> 8 // 「이미 받은 코드」 줄
                    else -> 12                                         // 받은 코드 줄(펼침)
                }
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = (20 - tail).dp)) {
                    // 게임 탭 — 호요랜드 일자 탭과 **같은 세그먼트 규격**이다([GldsTabs]).
                    //
                    // 칩 셋을 나란히 두면 서로 독립된 버튼처럼 보여, 지금 어느 게임의 코드를 보고
                    // 있는지가 약하게 읽혔다. 트랙 하나에 담으면 배타 선택이라는 게 모양에서 나온다.
                    GldsTabs(
                        labels = games.map { it.second },
                        selected = games.indexOfFirst { it.first == selected }.coerceAtLeast(0),
                        onSelect = { selected = games[it].first },
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("활성 코드 (자동 수집)", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
                                fontSize = 13.sp, color = TextSecondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            GldsButton("되살리기", onClick = { onRestoreUnusable(selected) }, variant = GldsVariant.Secondary, size = GldsSize.XS)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    when {
                        codesLoading && activeCodes.isEmpty() -> GiftCodeSkeleton()
                        // 수집 실패는 '코드 없음'과 다르다 — 사유를 밝히고 재시도를 준다.
                        codesFailed && activeCodes.isEmpty() -> Column(Modifier.padding(vertical = 6.dp)) {
                            Text("코드를 불러오지 못했어요", fontSize = 13.sp, color = TextSecondary)
                            Spacer(Modifier.height(6.dp))
                            GldsButton("다시 시도", onClick = { onLoadCodes(selected, true) }, variant = GldsVariant.Secondary, size = GldsSize.S)
                        }
                        activeCodes.isEmpty() -> Text("지금은 활성 코드가 없어요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                        else -> {
                            val unredeemed = activeCodes.filter { it.code !in redeemedCodes }.sortedByDescending { it.highlight }
                            val redeemed = activeCodes.filter { it.code in redeemedCodes }
                            if (unredeemed.isEmpty()) {
                                Text("받을 수 있는 새 코드가 없어요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                            } else {
                                unredeemed.forEachIndexed { i, c ->
                                    // 줄 사이는 헤어라인(10/1). 공방 강조 상자 둘레엔 긋지 않는다 — 상자 테두리가 이미 가른다.
                                    if (i > 0 && !unredeemed[i - 1].highlight && !c.highlight) GiftHairline()
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
                                    Text("이미 받은 코드 ${redeemed.size}개", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                }
                                if (showRedeemed) redeemed.forEachIndexed { i, c ->
                                    if (i > 0) GiftHairline()
                                    CodeRow(c, redeemed = true, accent = accent, enabled = false) {}
                                }
                            }
                        }
                    }
                    // 모두 교환
                    if (pending > 0) {
                        Spacer(Modifier.height(14.dp))
                        GldsButton(
                            if (loading) "교환 중…" else "모두 교환 ($pending)",
                            onClick = { onRedeemAll(selected) },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            // 맨 아래 여분 없음 — 마지막 섹션이 아래 20 을 둔다(GLDS 2.0, 10/1).
        }
        GlgDetailHeaderOverlay("리딤코드", onBack, scrollState = scrollState) {
            // 직접 입력 — 페이지 아래 섹션에서 헤더 + 버튼으로 옮겼다(10/15). UID 있는 게임이 없으면 교환할 곳이 없어 숨긴다.
            if (games.isNotEmpty()) GlgCircleIconButton(GlgLineIcons.Plus, "코드 직접 입력") { showInput = true }
        }
    }
    if (showInput) {
        GiftCodeInputDialog(
            games = games,
            initialGame = selected,
            needsRelink = hoyolab.cookieToken.isBlank() && hoyolab.webCookie.isBlank(),
            state = state,
            onDismiss = { showInput = false },
            onRedeem = onRedeemTyped,
        )
    }
}

/**
 * 리딤코드 직접 입력 — **가운데 모달**(10/15, 넛지 기준 금액과 같은 [GlgDialog] 틀). 설명 12 → 게임 탭 → 입력필드 → 취소 1 : 교환 1.4.
 * 게임은 모달 안에서 고른다(기본은 페이지에서 보던 탭). UID 있는 게임이 하나뿐이면 고를 게 없어 탭을 두지 않는다.
 * 교환을 누르면 모달을 연 채 결과를 기다린다(버튼 「교환 중…」). 성공이면 닫고 토스트, 코드 자체가 틀렸으면
 * (유효하지 않음 · 만료 · 이미 사용) **입력필드 오류**로 보여 주고 연 채 둔다(10/15). 그 밖의 실패(쿠키 · 네트워크 ·
 * 너무 잦음)는 코드를 고칠 일이 아니라 토스트로 뜨고 모달은 그대로다. 코드나 게임을 바꾸면 오류를 지운다.
 * iOS `GiftCodeInputDialog` 와 같은 모양 · 같은 값.
 */
@Composable
private fun GiftCodeInputDialog(
    games: List<Pair<String, String>>,
    initialGame: String,
    needsRelink: Boolean,
    state: RedeemState,
    onDismiss: () -> Unit,
    onRedeem: (String, String) -> Unit,
) {
    var game by remember { mutableStateOf(initialGame) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // 이 모달에서 누른 교환의 결과를 기다리는 중 — 모두 교환 등 다른 교환의 결과와 섞이지 않게 한다.
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (submitted && state is RedeemState.Done) {
            submitted = false
            when {
                state.success -> onDismiss()
                state.inputError -> error = state.message
            }
        }
    }
    val busy = submitted || state is RedeemState.Loading
    GlgDialog(
        title = "코드 직접 입력",
        onDismiss = onDismiss,
        confirmText = if (busy) "교환 중…" else "교환",
        onConfirm = { error = null; submitted = true; onRedeem(game, code.trim()) },
        confirmEnabled = !busy && code.isNotBlank() && error == null,
    ) {
        Column {
            Text(
                if (needsRelink) "교환하려면 HoYoLAB 재연동(이메일 로그인)이 필요해요. 보상은 게임 우편함으로 와요."
                else "보상은 게임 우편함으로 와요.",
                fontSize = 12.sp, color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            if (games.size > 1) {
                GldsTabs(
                    labels = games.map { it.second },
                    selected = games.indexOfFirst { it.first == game }.coerceAtLeast(0),
                    onSelect = { game = games[it].first; error = null },
                )
                Spacer(Modifier.height(12.dp))
            }
            GldsTextField(
                value = code,
                onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }; error = null },
                placeholder = "예: GENSHINGIFT",
                error = error,
                bold = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 코드 줄 사이 헤어라인(10/1) — 마이페이지 · 지출과 같은 색. */
@Composable
private fun GiftHairline() = GldsHairline()

/**
 * 활성 코드 한 줄 — 위 줄에 코드 · 복사 · 교환, 아래에 보상 목록 두 열(10/15 리딤코드 시안 C, GLDS 「보상 목록」 꼴).
 * 공방(공식방송) 코드는 강조 상자로 꾸민다. iOS `codeRow` 와 같은 값.
 */
@Composable
private fun CodeRow(c: GiftCode, redeemed: Boolean, accent: Color, enabled: Boolean, onRedeem: () -> Unit) {
    val highlight = c.highlight && !redeemed
    val inner: @Composable () -> Unit = {
        Column(
            // 보통 줄은 헤어라인 목록이라 위아래를 넉넉히(10/1, 5 → 12). 공방 상자 안은 사방 10(시안 C).
            modifier = Modifier.fillMaxWidth().padding(vertical = if (highlight) 10.dp else 12.dp, horizontal = if (highlight) 10.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (highlight) {
                        // 공방 뱃지 — 9 는 GLDS 보조 글자 하한(12)보다 작아 12 로 올렸다(10/15).
                        Box(
                            Modifier.height(20.dp).clip(RoundedCornerShape(6.dp)).background(accent).padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("공방", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        c.code,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        color = if (redeemed) TextSecondary else TextPrimary,
                        textDecoration = if (redeemed) TextDecoration.LineThrough else null,
                    )
                }
                Spacer(Modifier.width(8.dp))
                CopyCodeButton(c.code)
                Spacer(Modifier.width(8.dp))
                if (redeemed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("받음", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                } else {
                    GldsButton(
                        "교환", onClick = onRedeem, enabled = enabled, size = GldsSize.XS,
                        variant = if (highlight) GldsVariant.Primary else GldsVariant.Secondary,
                    )
                }
            }
            GiftRewardList(c)
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

/** 프리미엄 재화(원석 · 성옥 · 폴리크롬) 박스 색 — 5성 등급과 같은 금색(GLDS grade5). */
private val GiftGem = Color(0xFFD8A12E)
private val GiftBox = Color(0xFFF7F8FA)
private val GiftPendingLine = Color(0xFFC8CBD3)

/**
 * 리딤코드 보상 목록 — 두 열 · 열 사이 12 · 줄 사이 6. 칸 = 박스 28(반경 8) → 8 띄우고 이름 12(한 줄) → 수량 12 Bold.
 * 프리미엄은 금색 박스(16%), 나머지는 #F7F8FA. 박스 안은 인게임 아이콘 22 — 아이콘을 모르면 마름모(프리미엄) · 선물 16. 이름 모를 보상은 아래 「외 N종」,
 * 보상을 아직 모르는 새 코드는 점선 박스 + 「보상 확인 중」(10/15 시안 C). iOS `GiftRewardList` 와 같은 값.
 */
@Composable
private fun GiftRewardList(c: GiftCode) {
    val items = c.items
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.rewardsPending) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).border(1.dp, GiftPendingLine, RoundedCornerShape(8.dp)))
                Text("보상 확인 중", fontSize = 12.sp, color = TextSecondary)
            }
        } else if (items.isNotEmpty()) {
            items.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { GiftRewardCell(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (c.moreCount > 0) Text("외 ${c.moreCount}종", fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun GiftRewardCell(it: com.gatcha.log.data.api.GiftReward, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (it.premium) GiftGem.copy(alpha = 0.16f) else GiftBox),
            contentAlignment = Alignment.Center,
        ) {
            if (it.iconUrl.isNotEmpty()) {
                // 인게임 재화 아이콘 — 게임 일정 보상(`RewardCell`)과 같은 22(10/15).
                AsyncImage(it.iconUrl, null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
            } else if (it.premium) {
                // 마름모 — 24 칸 (12,2) (21,12) (12,22) (3,12).
                Canvas(Modifier.size(14.dp)) {
                    val u = size.width / 24f
                    drawPath(
                        Path().apply { moveTo(12 * u, 2 * u); lineTo(21 * u, 12 * u); lineTo(12 * u, 22 * u); lineTo(3 * u, 12 * u); close() },
                        GiftGem,
                    )
                }
            } else {
                Icon(GlgLineIcons.Gift, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(it.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (it.qty.isNotEmpty()) Text("×${it.qty}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

/**
 * 리딤코드 복사 버튼 — ‘교환’ 버튼과 같은 GLDS XS Secondary. 탭하면 클립보드 저장 +
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
    // '교환' 버튼과 같은 GLDS XS Secondary.
    GldsButton(
        if (copied) "복사됨" else "복사",
        onClick = {
            scope.launch {
                val clip = android.content.ClipData.newPlainText("선물코드", code)
                clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(clip))
            }
            copied = true
            android.widget.Toast.makeText(context, "코드를 복사했어요", android.widget.Toast.LENGTH_SHORT).show()
        },
        variant = GldsVariant.Secondary, size = GldsSize.XS,
    )
}
