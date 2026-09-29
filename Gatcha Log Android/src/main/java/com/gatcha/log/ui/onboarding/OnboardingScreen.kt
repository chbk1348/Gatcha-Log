package com.gatcha.log.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.R
import com.gatcha.log.data.GameData
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.ui.game.HoyolabLoginDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ════════════════════════════════════════════════════════════════════════════
// 첫 실행 온보딩 B안(개인화 설정) — 로그인보다 앞. 아티팩트 「온보딩 2.0」 B① ~ B⑥ + 구현 명세가 정본.
//
//   ① 환영 → ② 게임 → ③ 예산 → ④ HoYoLAB(호요버스 게임을 골랐을 때만) → ⑤ 알림 → ⑥ 완료
//
// 고른 값은 **설정 화면과 같은 저장소**에 쓴다([SpendingViewModel.applyOnboarding]) — 내 게임 · 월 예산 ·
// 알림 4종 · 방해 금지. HoYoLAB 은 설정 ▸ HoYoLAB 연동과 같은 로그인 창 · 같은 저장([updateHoyolabConfig]).
// (SwiftUI 패리티: GL_IOS/Screens/Onboarding/OnboardingView.swift)
// ════════════════════════════════════════════════════════════════════════════

private val Ink = Color(0xFF0F1A33)
private val Sub = Color(0xFF5E6B68)
private val Teal = Color(0xFF177881)
private val TealBright = Color(0xFF1B8E99)
private val TealTint = Color(0xFFEEF8F8)
private val TealSoft = Color(0xFFE3F2F1)
private val Line = Color(0xFFE3E8E6)
private val Ground = Color(0xFFF5F8F8)
private val Warn = Color(0xFFC2410C)

private const val HOYO_KEYS = "genshin,hsr,zzz"
private val PRESETS = listOf(50_000L to "5만원", 100_000L to "10만원", 150_000L to "15만원", 300_000L to "30만원")
private const val WARN_BUDGET = 500_000L

private fun won(n: Long) = "%,d".format(n)

@Composable
fun OnboardingScreen(viewModel: SpendingViewModel, onFinish: (requestNotification: Boolean) -> Unit) {
    val hoyo by viewModel.hoyolabConfig.collectAsStateWithLifecycle()
    val dndStart by viewModel.notifyDndStartHour.collectAsStateWithLifecycle()
    val dndEnd by viewModel.notifyDndEndHour.collectAsStateWithLifecycle()

    var step by rememberSaveable { mutableIntStateOf(0) }
    var forward by rememberSaveable { mutableStateOf(true) }
    // Set 은 번들에 못 넣어 쉼표 문자열로 보관한다.
    var gamesRaw by rememberSaveable { mutableStateOf(viewModel.myGames.value.filter { k -> GameData.onboardingGames.any { it.key == k } }.joinToString(",")) }
    val games = gamesRaw.split(',').filter { it.isNotBlank() }.toSet()
    var budget by rememberSaveable { mutableLongStateOf(150_000L) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var attend by rememberSaveable { mutableStateOf(true) }
    var resin by rememberSaveable { mutableStateOf(true) }
    var pickup by rememberSaveable { mutableStateOf(true) }
    var budgetAlert by rememberSaveable { mutableStateOf(false) }
    var alerts by rememberSaveable { mutableStateOf(true) }
    var restored by rememberSaveable { mutableStateOf(false) }
    var settled by rememberSaveable { mutableStateOf(false) }   // ② 첫 진입 차례 등장은 한 번만
    var busy by remember { mutableStateOf(false) }

    val noHoyo = games.none { it in HOYO_KEYS.split(',') }
    val scope = rememberCoroutineScope()

    /** 페이지 이동 — 전환 중엔 입력 무시. ④는 호요버스 게임이 없으면 건너뛴다. */
    fun go(target: Int) {
        if (busy) return
        val t = if (noHoyo && target == 3) (if (step < 3) 4 else 2) else target
        forward = t >= step
        busy = true
        step = t
        scope.launch { delay(260); busy = false }
    }

    val total = if (noHoyo) 3 else 4
    val shown = if (noHoyo && step == 4) 3 else step

    Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding().imePadding()) {
        // 상단: 뒤로 + 진행 막대(②~⑤)
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp)) {
            if (step in 1..4) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).clickable { go(step - 1) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Ink, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(10.dp))
                    val frac by animateFloatAsState(shown.toFloat() / total, tween(400, easing = FastOutSlowInEasing), label = "bar")
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color(0xFFE1EDEA))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(TealBright))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("$shown/$total", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Sub)
                }
            }
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(340)) { dir * it / 10 } + fadeIn(tween(340))) togetherWith
                    (slideOutHorizontally(tween(240)) { -dir * it / 12 } + fadeOut(tween(240)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { s ->
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                when (s) {
                    0 -> WelcomeStep(
                        onStart = { leave ->
                            if (!busy) { busy = true; scope.launch { leave(); busy = false; settled = false; restored = false; go(1) } }
                        },
                        onRestore = { restored = true; go(5) },
                    )
                    1 -> GamesStep(games, stagger = !settled, onToggle = { k -> gamesRaw = (if (k in games) games - k else games + k).joinToString(",") }) {
                        settled = true; go(2)
                    }
                    2 -> BudgetStep(budget, custom, onBudget = { v, c -> budget = v; custom = c }, onNext = { go(3) }, onSkip = { budget = -1L; go(3) })
                    3 -> HoyolabStep(viewModel, hoyo, games, onNext = { go(4) })
                    4 -> NotifyStep(
                        noHoyo, attend, resin, pickup, budgetAlert, dndStart, dndEnd,
                        onToggle = { k ->
                            when (k) { 0 -> attend = !attend; 1 -> resin = !resin; 2 -> pickup = !pickup; else -> budgetAlert = !budgetAlert }
                        },
                        onFinish = { a -> alerts = a; restored = false; go(5) },
                    )
                    else -> DoneStep(
                        restored = restored,
                        summary = if (restored) listOf(
                            Triple(Icons.Outlined.Person, "로그인", "Google 계정"),
                            Triple(Icons.Outlined.Cloud, "설정", "클라우드에서 복원"),
                        ) else buildList {
                            add(Triple(Icons.Outlined.SportsEsports, "게임", GameData.onboardingGames.filter { it.key in games }.joinToString(" · ") { it.displayName }.ifEmpty { "—" }))
                            add(Triple(Icons.Outlined.AccountBalanceWallet, "월 예산", if (budget > 0) "${won(budget)}원" else "없음"))
                            if (!noHoyo) add(Triple(Icons.Outlined.Link, "HoYoLAB", if (hoyo.isLinked) "연결됨" else "나중에"))
                            val on = buildList {
                                if (!noHoyo && attend) add("출석"); if (!noHoyo && resin) add("행동력 가득")
                                if (pickup) add("픽업 마감"); if (budgetAlert) add("예산 초과")
                            }
                            add(Triple(Icons.Outlined.Notifications, "알림", if (alerts) on.joinToString(" · ").ifEmpty { "모두 끔" } else "받지 않음"))
                        },
                    ) {
                        if (restored) { onFinish(false); return@DoneStep }
                        // 호요버스 게임이 없으면 숨긴 두 알림은 켜지 않는다(쓸 데가 없다).
                        viewModel.applyOnboarding(
                            games = games.toList(),
                            budget = if (budget > 0) budget else -1L,
                            alerts = alerts,
                            attendance = !noHoyo && attend,
                            resin = !noHoyo && resin,
                            pickup = pickup,
                            budgetAlert = budgetAlert,
                        )
                        onFinish(alerts)
                    }
                }
            }
        }
    }
}

// ── 공통 조각 ──────────────────────────────────────────────────────────────

/** 눌림(0.96배) + 아래에서 올라오는 등장. 주 버튼 50 · 보조 42, 좌우 8 들여씀. */
@Composable
internal fun CtaButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    bg: Color = if (primary) Teal else TealSoft,
    fg: Color = if (primary) Color.White else Teal,
    delayMs: Int = if (primary) 140 else 200,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "press")
    val bgc by animateColorAsState(if (enabled) bg else Color(0xFFB8C4C1), label = "ctaBg")
    Box(
        Modifier
            .enterUp(delayMs)
            .padding(horizontal = 8.dp)
            .padding(top = if (primary) 0.dp else 8.dp)
            .fillMaxWidth()
            .height(if (primary) 50.dp else 42.dp)
            .scale(press)
            .clip(RoundedCornerShape(16.dp))
            .background(bgc)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = if (primary) 15.sp else 13.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** 처음 그려질 때 아래(18dp)에서 올라오며 나타남. */
@Composable
private fun Modifier.enterUp(delayMs: Int, distance: Dp = 18.dp): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    val px = with(LocalDensity.current) { distance.toPx() }
    return this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * px }
}

@Composable
private fun Title(text: String, sub: String? = null) {
    Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 8.dp))
    if (sub != null) Text(sub, fontSize = 13.5.sp, lineHeight = 20.sp, color = Sub, modifier = Modifier.padding(top = 8.dp))
}

private data class GlyphTile(val key: String, @DrawableRes val icon: Int, val label: String, val from: Color, val to: Color, val labelColor: Color = Color.White)

private val TILES = listOf(
    GlyphTile("genshin", R.drawable.ic_glyph_genshin, "원신", Color(0xFF6FA5FA), Color(0xFF3E76E0)),
    GlyphTile("hsr", R.drawable.ic_glyph_hsr, "스타레일", Color(0xFFC48CFF), Color(0xFF9350F0)),
    GlyphTile("zzz", R.drawable.ic_glyph_zzz, "젠레스", Color(0xFFFFC15A), Color(0xFFE8931A), Color(0xFF3B2600)),
    GlyphTile("wuwa", R.drawable.ic_glyph_wuwa, "명조", Color(0xFFF0479B), Color(0xFFC8006E)),
    GlyphTile("endfield", R.drawable.ic_glyph_endfield, "엔드필드", Color(0xFF3FD3C4), Color(0xFF139C8F)),
    GlyphTile("nte", R.drawable.ic_glyph_nte, "이환", Color(0xFF8E80F0), Color(0xFF6C5CE7)),
)

// ── ① 환영 ─────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(onStart: (leave: suspend () -> Unit) -> Unit, onRestore: () -> Unit) {
    // 타일마다 등장(위에서 떨어짐) · 퇴장(위로 흩어짐) 진행도
    val drops = remember { List(TILES.size) { Animatable(0f) } }
    val leaves = remember { List(TILES.size) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        drops.forEachIndexed { i, a -> launch { delay(i * 80L); a.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) } }
    }
    val scope = rememberCoroutineScope()
    // 6장 부채꼴 — 가운데 두 장이 가장 높고 맨 위(9/29 이환 추가로 72 → 62).
    val rot = listOf(-10f, -6f, -2f, 2f, 6f, 10f)
    val lift = listOf(0, 8, 14, 14, 8, 0)
    val z = listOf(1f, 2f, 3f, 3f, 2f, 1f)
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            // 62 타일 6장을 10씩 겹친다(음수 간격) — 322 라 좁은 폰에서도 한 줄에 들어간다.
            Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy((-10).dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                TILES.forEachIndexed { i, t ->
                    val d = drops[i].value
                    val l = leaves[i].value
                    Box(
                        Modifier
                            .offset(y = (-lift[i]).dp / 2)
                            .zIndex(z[i])
                            .graphicsLayer {
                                rotationZ = rot[i]
                                alpha = d * (1f - l)
                                translationY = with(density) { ((1f - d) * -28f - l * 44f).dp.toPx() }
                                val sc = (0.8f + 0.2f * d) * (1f - 0.2f * l)
                                scaleX = sc; scaleY = sc
                            }
                            .shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = Ink.copy(alpha = 0.14f), spotColor = Ink.copy(alpha = 0.14f))
                            .border(2.5.dp, Color.White, RoundedCornerShape(18.dp))
                            .padding(2.5.dp)
                            .size(57.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(t.from, t.to))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(t.icon), null, Modifier.size(when (i) { 0 -> 29.dp; 5 -> 34.dp; else -> 31.dp }))
                            Text(t.label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = t.labelColor)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "하는 게임에 맞춘\n나만의 게임 가계부", fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "게임을 고르고 예산 · 연동 · 알림을 정하면,\n홈이 첫날부터 내 기록으로 채워져요.", fontSize = 14.sp, lineHeight = 21.sp,
                color = Sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                listOf("게임", "예산", "연동", "알림").forEachIndexed { i, c ->
                    Box(
                        Modifier.clip(CircleShape).background(if (i == 0) Ink else Ground).padding(horizontal = 12.dp, vertical = 7.dp),
                    ) { Text("${i + 1} $c", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (i == 0) Color.White else Sub) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("약 1분 · 게임 고르기 말고는 전부 건너뛸 수 있어요", fontSize = 12.sp, color = Sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        CtaButton("내 앱으로 맞추기", primary = true, onClick = {
            onStart {
                // 타일이 차례로 위로 흩어진 뒤 넘어간다(0.32초)
                leaves.forEachIndexed { i, a -> scope.launch { delay(i * 30L); a.animateTo(1f, tween(300)) } }
                delay(320)
            }
        })
        CtaButton("이미 쓰고 있어요 · 불러오기", primary = false, onClick = onRestore)
        Spacer(Modifier.height(16.dp))
    }
}

// ── ② 게임 ─────────────────────────────────────────────────────────────────

@Composable
private fun GamesStep(games: Set<String>, stagger: Boolean, onToggle: (String) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(if (stagger) Modifier.enterUp(40, 16.dp) else Modifier) {
            Title("어떤 게임을 하세요?", "고른 게임이 지출 입력 맨 위에 오고, 출석도 고른 게임만 챙겨요.")
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameData.onboardingGames.forEachIndexed { i, g ->
                val on = g.key in games
                val border by animateColorAsState(if (on) TealBright else Line, label = "gb")
                Row(
                    (if (stagger) Modifier.enterUp(160 + i * 60, 16.dp) else Modifier)
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) TealTint else Color.White)
                        .border(if (on) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
                        .clickable { onToggle(g.key) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    val sc by animateFloatAsState(if (on) 1f else 0.75f, label = "chk")
                    Box(
                        Modifier.size(22.dp).scale(sc).clip(CircleShape).background(if (on) TealBright else Line),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val n = games.size
        CtaButton(
            if (n > 0) "${n}개 선택 · 다음" else "게임을 하나 이상 골라 주세요", primary = true,
            enabled = n > 0, onClick = onNext, delayMs = if (stagger) 460 else 140,
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ── ③ 예산 ─────────────────────────────────────────────────────────────────

@Composable
private fun BudgetStep(budget: Long, custom: Boolean, onBudget: (Long, Boolean) -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Title("한 달에 얼마까지 쓸까요?", "넘기기 전에 알려 드려요. 게임별 한도는 나중에 정해도 돼요.")
        Spacer(Modifier.height(28.dp))
        BudgetAmountEditor(budget, custom, onBudget)
        Spacer(Modifier.weight(1f))
        CtaButton("다음", primary = true, onClick = onNext)
        CtaButton("예산 없이 쓸게요", primary = false, onClick = onSkip)
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 월 예산 금액 카드 — 직접 입력(숫자만 · 콤마 · 9자리) + 5만~30만원 버튼(숫자가 굴러감) + 직접 입력 버튼 + 50만원 이상 주의.
 * 온보딩 ③과 설정 ▸ 예산 관리(아티팩트 S3)가 같이 쓴다.
 */
@Composable
internal fun BudgetAmountEditor(budget: Long, custom: Boolean, onBudget: (Long, Boolean) -> Unit) {
    val value = budget.coerceAtLeast(0L)
    // 금액 버튼을 누르면 숫자가 0.45초 동안 굴러간다(직접 입력 중에는 바로 반영).
    val rolling = remember { Animatable(value.toFloat()) }
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(value, typing) {
        if (typing) rolling.snapTo(value.toFloat())
        else rolling.animateTo(value.toFloat(), tween(450, easing = FastOutSlowInEasing))
    }
    val shown = if (typing) value else (rolling.value / 100).toLong() * 100
    val warn = value >= WARN_BUDGET
    val focus = remember { FocusRequester() }

    // 50만원 이상: 한 번 흔들림 + 테두리 깜빡임
    val shake = remember { Animatable(0f) }
    LaunchedEffect(warn) {
        if (warn) shake.animateTo(0f, keyframes { durationMillis = 420; -6f at 84; 5f at 168; -3f at 252; 2f at 336 })
    }
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "g")
    val cardBrush = if (warn) Brush.linearGradient(listOf(Color(0xFFFFF4E8), Color(0xFFFFE2C7))) else Brush.linearGradient(listOf(TealTint, Color(0xFFDCF0EE)))
    val labelColor = if (warn) Warn else Teal
    val amtColor = if (warn) Warn else Ink

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shake.value * density }
                .then(if (warn) Modifier.border(BorderStroke((5 * glow).dp, Color(0xFFEA580C).copy(alpha = 0.16f)), RoundedCornerShape(22.dp)) else Modifier)
                .clip(RoundedCornerShape(22.dp))
                .background(cardBrush)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 22.dp),
        ) {
            Text("월 예산", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = labelColor)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = if (shown > 0) won(shown) else "",
                    onValueChange = { raw ->
                        typing = true
                        val digits = raw.filter { it.isDigit() }.take(9)
                        onBudget(digits.toLongOrNull() ?: 0L, true)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = amtColor, letterSpacing = (-0.5).sp),
                    cursorBrush = SolidColor(amtColor),
                    modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 24.dp).focusRequester(focus),
                    decorationBox = { inner ->
                        Box { if (shown <= 0) Text("0", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = amtColor.copy(alpha = 0.3f)); inner() }
                    },
                )
                Text("원", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = amtColor, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            }
        }
        AnimatedVisibility(warn, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 4 }) {
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF4E8))
                    .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Warn, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "한 달 50만원 이상이에요. 무리하지 않는 금액인지 한 번만 더 확인해 주세요. 넘기기 전에 알려 드릴게요.",
                    fontSize = 12.5.sp, lineHeight = 18.sp, color = Color(0xFF9A3412),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { (amt, label) ->
                val on = !custom && value == amt
                AmountChip(label, on, Modifier.weight(1f).height(48.dp)) { typing = false; onBudget(amt, false) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val customOn = custom || (value > 0 && PRESETS.none { it.first == value })
        AmountChip("직접 입력", customOn, Modifier.fillMaxWidth().height(40.dp), icon = Icons.Filled.Edit, fg = Teal) {
            onBudget(value, true); runCatching { focus.requestFocus() }
        }
    }
}

@Composable
private fun AmountChip(label: String, on: Boolean, modifier: Modifier, icon: ImageVector? = null, fg: Color = Ink, onClick: () -> Unit) {
    val border by animateColorAsState(if (on) TealBright else Color.Transparent, tween(180), label = "cb")
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (on) TealTint else Ground)
            .border(2.dp, border, RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Teal, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Teal else fg)
    }
}

// ── ④ HoYoLAB ─────────────────────────────────────────────────────────────

@Composable
private fun HoyolabStep(viewModel: SpendingViewModel, hoyo: HoyolabConfig, games: Set<String>, onNext: () -> Unit) {
    var showLogin by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val linked = hoyo.isLinked
    val picked = GameData.onboardingGames.filter { it.key in games && it.key in HOYO_KEYS.split(',') }.joinToString(" · ") { it.shortName }

    Column(Modifier.fillMaxSize()) {
        Title("HoYoLAB을 연결할까요?", "비밀번호는 저장하지 않아요. 언제든 연결을 끊을 수 있어요.")
        Spacer(Modifier.height(20.dp))
        // 히어로
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Ink, Color(0xFF23345C)))).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("연결하면", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8FE3DA))
                Text("매일 할 일 6가지를\n앱이 대신 챙겨요", fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                TILES.take(3).forEachIndexed { i, t ->
                    Box(
                        Modifier.zIndex((3 - i).toFloat()).size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A2847)).padding(2.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(t.from, t.to))),
                        contentAlignment = Alignment.Center,
                    ) { Image(painterResource(t.icon), null, Modifier.size(if (i == 0) 24.dp else 26.dp)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val perks = listOf(
            Triple(Icons.Outlined.EventAvailable, "자동 출석", "3게임 출석 보상을 매일"),
            Triple(Icons.Outlined.Schedule, "실시간 재화", "가득 차기 전에 알림"),
            Triple(Icons.Outlined.Checklist, "숙제 현황", "일일 · 주간 · 파견"),
            Triple(Icons.Outlined.BarChart, "재화 수입", "이번 달과 지난달 비교"),
            Triple(Icons.Outlined.ConfirmationNumber, "리딤 코드", "눌러서 바로 교환"),
            Triple(Icons.Outlined.EmojiEvents, "클리어 · 캐릭터", "편성과 육성 현황"),
        )
        perks.chunked(2).forEach { row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (ic, t, d) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Ground).padding(14.dp)) {
                        Box(Modifier.size(34.dp).shadow(1.dp, RoundedCornerShape(11.dp)).clip(RoundedCornerShape(11.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(ic, null, tint = Teal, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(d, fontSize = 12.sp, color = Sub)
                    }
                }
            }
        }
        AnimatedVisibility(linked, enter = fadeIn() + slideInVertically { it / 3 }) {
            Text(
                "연결됐어요 · ${picked.ifEmpty { "게임" }} UID 를 채웠어요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teal,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TealTint).padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (linked) {
            CtaButton("다음", primary = true, onClick = onNext)
            CtaButton("연결 해제", primary = false, bg = Color(0xFFECEFF4), fg = Ink, onClick = { viewModel.updateHoyolabConfig(HoyolabConfig()) })
        } else {
            CtaButton(if (working) "UID 확인 중…" else "HoYoLAB 로그인", primary = true, bg = Ink, enabled = !working, onClick = { showLogin = true })
            CtaButton("나중에 연결할게요", primary = false, bg = Color(0xFFECEFF4), fg = Ink, onClick = onNext)
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showLogin) {
        // 설정 ▸ HoYoLAB 연동과 같은 로그인 창 · 같은 저장 경로([updateHoyolabConfig]).
        HoyolabLoginDialog(
            onCollected = { u, t, c, raw ->
                showLogin = false
                working = true
                scope.launch {
                    val uids = withContext(Dispatchers.IO) { HoyolabApi.fetchGameUids(u, t) }.orEmpty()
                    viewModel.updateHoyolabConfig(
                        HoyolabConfig(
                            ltuid = u, ltoken = t, cookieToken = c, webCookie = raw,
                            genshinUid = uids["genshin"].orEmpty(), hsrUid = uids["hsr"].orEmpty(), zzzUid = uids["zzz"].orEmpty(),
                        ),
                    )
                    working = false
                }
            },
            onDismiss = { showLogin = false },
        )
    }
}

// ── ⑤ 알림 ─────────────────────────────────────────────────────────────────

@Composable
private fun NotifyStep(
    noHoyo: Boolean, attend: Boolean, resin: Boolean, pickup: Boolean, budgetAlert: Boolean,
    dndStart: Int, dndEnd: Int, onToggle: (Int) -> Unit, onFinish: (Boolean) -> Unit,
) {
    data class Row5(val k: Int, val icon: ImageVector, val fg: Color, val bg: Color, val t: String, val d: String, val on: Boolean)
    val rows = buildList {
        if (!noHoyo) {
            add(Row5(0, Icons.Outlined.EventAvailable, Teal, TealSoft, "출석", "자동 출석 결과 · 저녁까지 미출석이면", attend))
            add(Row5(1, Icons.Outlined.Schedule, Color(0xFF3E76E0), Color(0xFFE8F0FD), "행동력 가득", "가득 차기 전 한 번", resin))
        }
        add(Row5(2, Icons.Outlined.StarOutline, Color(0xFF9350F0), Color(0xFFF1E8FD), "픽업 마감", "D-3 · D-1", pickup))
        add(Row5(3, Icons.Outlined.AccountBalanceWallet, Warn, Color(0xFFFFF1E6), "예산 초과", "90% · 100% 넘을 때", budgetAlert))
    }
    Column(Modifier.fillMaxSize()) {
        Title("어떤 알림을 받을까요?", "필요한 것만 켜 두세요. 설정 ▸ 알림에서 언제든 바꿀 수 있어요.")
        Spacer(Modifier.height(20.dp))
        // 미리보기 알림 — 호요버스 게임이 없으면 예산 알림으로
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(TealTint, Color(0xFFDCF0EE))))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
        ) {
            Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Color.White.copy(alpha = 0.55f)))
            Row(
                Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Ink.copy(alpha = 0.1f), spotColor = Ink.copy(alpha = 0.1f))
                    .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(Color(0xFF1FA0AB), Color(0xFF146E77)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Notifications, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row { Text("Gatcha Log", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F5C59), modifier = Modifier.weight(1f)); Text("지금", fontSize = 11.5.sp, color = Color(0xFF7A8784)) }
                    Text(if (noHoyo) "이번 달 예산의 90% 를 썼어요" else "레진이 곧 가득 차요", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(if (noHoyo) "150,000원 중 135,000원 · 남은 15,000원" else "원신 190 / 200 · 20분 뒤 가득", fontSize = 12.sp, color = Sub)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, Line, RoundedCornerShape(18.dp))) {
            rows.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth().background(Color.White).clickable { onToggle(r.k) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(r.bg), contentAlignment = Alignment.Center) {
                        Icon(r.icon, null, tint = r.fg, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(r.d, fontSize = 12.sp, color = Sub)
                    }
                    Toggle(r.on)
                }
                if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F3F2)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ground).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Bedtime, null, tint = Color(0xFF4F5C59), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text("방해 금지 시간", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            Text(
                "%02d:00 ~ %02d:00".format(dndStart, dndEnd), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F5C59),
                modifier = Modifier.clip(CircleShape).background(Color.White).border(1.dp, Line, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        CtaButton("알림 켜고 시작하기", primary = true, onClick = { onFinish(true) })
        CtaButton("알림 없이 시작", primary = false, onClick = { onFinish(false) })
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Toggle(on: Boolean) {
    val knob by animateDpAsState(if (on) 18.dp else 0.dp, tween(180), label = "knob")
    val track by animateColorAsState(if (on) TealBright else Color(0xFFCBD5D3), tween(180), label = "track")
    Box(Modifier.width(44.dp).height(26.dp).clip(CircleShape).background(track).padding(horizontal = 3.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = knob).size(20.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

// ── ⑥ 완료 ─────────────────────────────────────────────────────────────────

@Composable
private fun DoneStep(restored: Boolean, summary: List<Triple<ImageVector, String, String>>, onDone: () -> Unit) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 132.dp, height = 96.dp)) {
                // 게임 색 점 — 축하 장식
                listOf(
                    Triple(8, 22, 10 to Color(0xFF6FA5FA)), Triple(22, 70, 7 to Color(0xFFC48CFF)), Triple(104, 14, 8 to Color(0xFFFFC15A)),
                    Triple(116, 60, 11 to Color(0xFFF0479B)), Triple(94, 84, 6 to Color(0xFF3FD3C4)), Triple(34, 4, 5 to Color(0xFF1FA0AB)),
                ).forEach { (x, y, sc) ->
                    Box(Modifier.offset(x.dp, y.dp).size(sc.first.dp).graphicsLayer { alpha = ((pop.value - 0.4f) / 0.6f).coerceIn(0f, 1f) }.clip(CircleShape).background(sc.second))
                }
                Box(
                    Modifier.offset(30.dp, 12.dp).size(72.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value }
                        .shadow(12.dp, CircleShape, ambientColor = Teal.copy(alpha = 0.3f), spotColor = Teal.copy(alpha = 0.3f))
                        .clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF1FA0AB), Color(0xFF146E77)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (restored) "로그인하면 불러와요" else "준비 끝!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                if (restored) "Google 계정으로 로그인하면 설정과 기록을 모두 가져와요" else "이제 홈에서 내 기록을 볼 수 있어요",
                fontSize = 13.5.sp, color = Sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ground).padding(horizontal = 16.dp, vertical = 6.dp)) {
                summary.forEachIndexed { i, (ic, k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(ic, null, tint = Teal, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(k, fontSize = 13.sp, color = Sub)
                        Spacer(Modifier.width(12.dp))
                        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    if (i < summary.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE6ECEA)))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("설정 ▸ 내 설정에서 언제든 바꿀 수 있어요", fontSize = 12.sp, color = Color(0xFF7A8784), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        CtaButton(if (restored) "로그인하고 불러오기" else "홈으로 이동하기", primary = true, onClick = onDone)
        Spacer(Modifier.height(16.dp))
    }
}
