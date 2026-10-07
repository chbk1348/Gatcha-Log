package com.gatcha.log.ui.onboarding

import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.Person
import com.gatcha.log.data.AppSettings
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsSize
import com.gatcha.log.ui.components.GldsVariant
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

/** 이 프로세스의 표식 — 저장된 온보딩 상태가 죽기 전 프로세스의 것인지 가린다(값 자체는 뜻이 없다). */
private val ONBOARDING_PROCESS: String = System.nanoTime().toString()

/** 온보딩을 띄운 채 이만큼 떠나 있었으면 돌아왔을 때 처음부터 다시 시작한다. */
private const val ONBOARDING_TIMEOUT_MS = 10L * 60 * 1000
private val PRESETS = listOf(50_000L to "5만원", 100_000L to "10만원", 150_000L to "15만원", 300_000L to "30만원")
private const val WARN_BUDGET = 500_000L

private fun won(n: Long) = "%,d".format(n)

/** 완료 요약 한 줄 — 짧은 이름으로, 넷 이상이면 「앞 셋 외 N」(오른쪽 정렬 값이 여러 줄로 꺾이지 않게, 9/29). */
private fun shortList(names: List<String>, empty: String = "—"): String = when {
    names.isEmpty() -> empty
    names.size <= 3 -> names.joinToString(" · ")
    else -> names.take(3).joinToString(" · ") + " 외 ${names.size - 3}"
}

@Composable
/**
 * @param loginOnly 온보딩을 마친 뒤 로그아웃 등으로 로그인이 필요할 때 — 옛 로그인 화면(온보딩 1.0) 대신
 *   ⑥ 복원 화면(「로그인하면 불러와요」 + 구글 로그인 버튼)만 띄운다(9/29).
 */
fun OnboardingScreen(viewModel: SpendingViewModel, loginOnly: Boolean = false, onFinish: (requestNotification: Boolean, signIn: Boolean) -> Unit) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val hoyo by viewModel.hoyolabConfig.collectAsStateWithLifecycle()
    val dndStart by viewModel.notifyDndStartHour.collectAsStateWithLifecycle()
    val dndEnd by viewModel.notifyDndEndHour.collectAsStateWithLifecycle()

    var step by rememberSaveable { mutableIntStateOf(if (loginOnly) 5 else 0) }
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
    var restored by rememberSaveable { mutableStateOf(loginOnly) }
    var settled by rememberSaveable { mutableStateOf(false) }   // ② 첫 진입 차례 등장은 한 번만
    var busy by remember { mutableStateOf(false) }

    val noHoyo = games.none { it in HOYO_KEYS.split(',') }
    var applied by rememberSaveable { mutableStateOf(false) }   // 로그인 재시도로 다시 눌러도 설정은 한 번만
    var finished by remember { mutableStateOf(false) }
    var loginRequested by remember { mutableStateOf(false) }
    // 완료 화면에서 띄운 구글 로그인이 끝나면 온보딩을 마친다(로그인 화면을 거치지 않는다).
    LaunchedEffect(account.isGuest) {
        // 첫 화면 「구글 로그인 하기」(복원)는 ⑤ 알림 단계를 건너뛰므로, 끝날 때 OS 알림 권한을 묻는다(10/6 —
        // 로그인부터 하면 권한 창이 끝내 안 떴다). ⑤를 거친 경로는 이미 물었으니 다시 묻지 않는다.
        if (!account.isGuest && (step == 5 || loginRequested) && !finished) {
            // 첫 화면에서 **바로 로그인한 경우에도 ② 게임 선택부터 첫 사용자와 같은 순서를 밟는다**(10/7) —
            // 게임 → 예산 → HoYoLAB → 알림 → 완료. 예전엔 로그인하자마자 홈으로 갔는데, HoYoLAB 토큰은 기기에만 두고
            // 클라우드에 올리지 않아 연동은 기기마다 한 번 해야 하고, 알림 권한도 이 흐름에서 묻는다.
            // 계정에서 복원된 값(내 게임 · 예산)은 아래에서 미리 채워 둔다. ⑥ 완료 화면은 이미 로그인돼 있으니 바로 홈으로 간다.
            if (loginRequested && step == 0) { settled = false; restored = false; forward = true; step = 1 }
            else { finished = true; onFinish(loginRequested, false) }
        }
    }
    /**
     * 이미 로그인된 채로 밟는 온보딩인가 — 첫 화면에서 바로 로그인했거나, 로그인한 뒤 중간에 끊겨 다시 시작한 경우다.
     * 계정 값(내 게임 · 예산)을 미리 채우고, 끝에서 로그인을 다시 묻지 않는다.
     */
    val afterLogin = !loginOnly && !account.isGuest
    // 계정에서 복원된 「내 게임」 · 예산을 미리 골라 둔다 — 클라우드 복원은 로그인 뒤 조금 있다 오므로 도착하는 대로,
    // 직접 고르기 전까지만 따라간다. (예산은 [applyOnboarding] 이 계정 값이 비었을 때만 쓰지만, 화면에 보이는 값도 맞춘다.)
    val accountGames by viewModel.myGames.collectAsStateWithLifecycle()
    val accountBudget by viewModel.budget.collectAsStateWithLifecycle()
    var gamesTouched by rememberSaveable { mutableStateOf(false) }
    var budgetTouched by rememberSaveable { mutableStateOf(false) }
    // ② 에 적는 한 줄 — 고르지도 않은 게임이 골라져 있는 까닭을 밝힌다(10/7). 계정에 저장된 것이 없으면(새 계정) 적지 않는다.
    val accountSyncing by viewModel.initialSyncing.collectAsStateWithLifecycle()
    val accountNote = when {
        !afterLogin -> null
        accountSyncing -> "이전 설정을 불러오는 중이에요…"
        accountGames.any { k -> GameData.onboardingGames.any { it.key == k } } -> "이전 설정을 불러왔어요"
        else -> null
    }
    LaunchedEffect(afterLogin, accountGames) {
        if (afterLogin && !gamesTouched && step <= 1) {
            val mine = accountGames.filter { k -> GameData.onboardingGames.any { it.key == k } }
            if (mine.isNotEmpty()) gamesRaw = mine.joinToString(",")
        }
    }
    LaunchedEffect(afterLogin, accountBudget) {
        if (afterLogin && !budgetTouched && step <= 2 && accountBudget > 0L) {
            budget = accountBudget
            custom = PRESETS.none { it.first == accountBudget }
        }
    }

    // ── 중간에 끊기면 처음부터 다시 시작한다(10/7) ──
    // 앱이 닫혔다 다시 켜졌거나(시스템이 백그라운드에서 정리한 경우 포함), 온보딩을 띄운 채 오래 떠나 있었으면
    // 하다 만 단계로 돌아오지 않고 ① 부터 다시 밟는다. 이미 저장된 것(로그인 · HoYoLAB 연동)은 그대로 남고,
    // 로그인돼 있으면 위 [afterLogin] 이 계정 값을 다시 채운다. 로그인 화면만 띄운 경우(loginOnly)는 해당 없다.
    fun restart() {
        step = 0; forward = false; restored = false; settled = false; applied = false; loginRequested = false
        gamesTouched = false; budgetTouched = false
        gamesRaw = viewModel.myGames.value.filter { k -> GameData.onboardingGames.any { it.key == k } }.joinToString(",")
        budget = 150_000L; custom = false
        attend = true; resin = true; pickup = true; budgetAlert = false; alerts = true
    }
    // 저장된 화면 상태가 **다른 프로세스의 것**이면(회전 같은 재구성이 아니라 앱이 죽었다 살아난 것) 버린다.
    var session by rememberSaveable { mutableStateOf(ONBOARDING_PROCESS) }
    var leftAt by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        if (loginOnly) return@LaunchedEffect
        // 「아직 안 마쳤다」를 적어 둔다 — 안 적으면 도중에 로그인한 사용자가 다음 실행에서 홈으로 건너뛴다.
        runCatching { AppSettings().markOnboardingStarted() }
        if (session != ONBOARDING_PROCESS) { session = ONBOARDING_PROCESS; leftAt = 0L; restart() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (loginOnly) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> leftAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val away = if (leftAt > 0L) System.currentTimeMillis() - leftAt else 0L
                    leftAt = 0L
                    if (away >= ONBOARDING_TIMEOUT_MS && !finished) restart()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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

    // ⑤ 「알림 켜고 시작하기」 — OS 알림 권한을 그 자리에서 묻고, 답하면 ⑥ 완료로(9/29). 완료 화면은 구글 로그인만.
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { go(5) }
    fun askPermissionThenDone() {
        val needPrompt = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needPrompt) {
            AppSettings().notifPermAsked = true   // 실제로 띄울 때만 — '영구 거부' 판별 근거
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else go(5)
    }

    val total = if (noHoyo) 3 else 4
    val shown = if (noHoyo && step == 4) 3 else step

    // 태블릿 · 폴더블 대응: 폭은 폰 크기(480)로 가운데에 모은다.
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.widthIn(max = 480.dp).fillMaxSize().systemBarsPadding().imePadding(),
    ) {
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
            // 스크롤 없이(9/29) — 버튼 틀은 하단에 상시 고정, 화면이 낮으면(작은 폰 · 가로 · 폴더블) 각 단계가
            // 이 높이를 보고 촘촘한 배치로 바꿔 한 화면에 넣는다(게임 줄 높이 · HoYoLAB 3열 · 알림 미리보기 생략).
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                when (s) {
                    // **온보딩은 구글 로그인부터 한다 — 첫 사용자도, 쓰던 사용자도 같은 한 갈래다**(10/7). 예전엔 「시작하기」(끝에서 로그인)와
                    // 「구글 로그인 하기」(먼저 로그인) 두 갈래라, 갈래마다 계정 값을 채울지 · 덮을지 · 끊겼을 때 어디로 갈지가 달랐다.
                    0 -> WelcomeStep(
                        // 이미 로그인돼 있으면(로그인한 뒤 끊겨 다시 시작한 경우) 다시 묻지 않고 ② 로 간다.
                        loggedIn = afterLogin,
                        onStart = { leave ->
                            if (!busy) { busy = true; scope.launch { leave(); busy = false; settled = false; restored = false; go(1) } }
                        },
                        // 성공하면 위 LaunchedEffect 가 ② 게임 선택으로 넘긴다. 취소하면 이 화면에 남는다.
                        onLogin = { loginRequested = true; viewModel.signIn() },
                    )
                    1 -> GamesStep(games, viewport, stagger = !settled, note = accountNote, noteLoading = accountSyncing, onToggle = { k -> gamesTouched = true; gamesRaw = (if (k in games) games - k else games + k).joinToString(",") }) {
                        settled = true; go(2)
                    }
                    2 -> BudgetStep(budget, custom, onBudget = { v, c -> budgetTouched = true; budget = v; custom = c }, onNext = { go(3) }, onSkip = { budgetTouched = true; budget = -1L; go(3) })
                    3 -> HoyolabStep(viewModel, hoyo, games, viewport, onNext = { go(4) })
                    4 -> NotifyStep(
                        noHoyo, attend, resin, pickup, budgetAlert, dndStart, dndEnd, viewport,
                        onToggle = { k ->
                            when (k) { 0 -> attend = !attend; 1 -> resin = !resin; 2 -> pickup = !pickup; else -> budgetAlert = !budgetAlert }
                        },
                        onFinish = { a -> alerts = a; restored = false; if (a) askPermissionThenDone() else go(5) },
                    )
                    else -> DoneStep(
                        restored = restored,
                        // 로그인 전이면(첫 사용자 · 복원) 완료 버튼이 곧 구글 로그인 — 로그인 화면을 따로 거치지 않는다(9/29).
                        signInNext = account.isGuest,
                        // 로그인 유도 화면(restored) — 로그인하면 좋은 점 세 줄(9/29 목적 변경).
                        summary = if (restored) listOf(
                            Triple(Icons.Outlined.Cloud, "기기를 바꿔도 그대로", "지출 · 예산 · 설정이 구글 계정에 저장돼요"),
                            Triple(Icons.Outlined.History, "쓰던 계정이면 기록 복원", "로그인만 하면 이전 기록이 돌아와요"),
                            Triple(Icons.Outlined.Person, "가입 없이 구글 계정 하나로", "따로 만들 계정도, 비밀번호도 없어요"),
                        ) else buildList {
                            add(Triple(Icons.Outlined.SportsEsports, "게임", shortList(GameData.onboardingGames.filter { it.key in games }.map { it.shortName })))
                            add(Triple(Icons.Outlined.AccountBalanceWallet, "월 예산", if (budget > 0) "${won(budget)}원" else "없음"))
                            if (!noHoyo) add(Triple(Icons.Outlined.Link, "HoYoLAB", if (hoyo.isLinked) "연결됨" else "나중에"))
                            val on = buildList {
                                if (!noHoyo && attend) add("출석"); if (!noHoyo && resin) add("행동력 가득")
                                if (pickup) add("픽업 마감"); if (budgetAlert) add("예산 초과")
                            }
                            add(Triple(Icons.Outlined.Notifications, "알림", if (alerts) shortList(on, empty = "모두 끔") else "받지 않음"))
                        },
                    ) {
                        if (finished) return@DoneStep
                        // 설정값은 누르는 순간 저장한다(게스트여도 — 예산은 로그인 직후 계정에 적용된다).
                        if (!restored && !applied) {
                            applied = true
                            // 호요버스 게임이 없으면 숨긴 두 알림은 켜지 않는다(쓸 데가 없다).
                            // 먼저 로그인한 경로에서 **손대지 않은 칸은 계정 값을 그대로 둔다** — 복원이 늦게 와서 화면에 기본값이
                            // 보였더라도, 고르지 않은 기본값이 계정의 이전 설정을 덮지 않게 한다. 고친 칸은 고친 값이 새 설정이다.
                            val mine = accountGames.filter { k -> GameData.onboardingGames.any { it.key == k } }
                            viewModel.applyOnboarding(
                                games = if (afterLogin && !gamesTouched && mine.isNotEmpty()) mine else games.toList(),
                                budget = if (afterLogin && !budgetTouched && accountBudget > 0L) -1L else if (budget > 0) budget else -1L,
                                alerts = alerts,
                                attendance = !noHoyo && attend,
                                resin = !noHoyo && resin,
                                pickup = pickup,
                                budgetAlert = budgetAlert,
                            )
                        }
                        // 로그인 전이면 온보딩을 **띄운 채로** 구글 로그인만 띄운다 — 먼저 끝내면 뒤에 옛 로그인 화면이
                        // 깔려 보였다(9/29). 로그인이 끝나면 아래 LaunchedEffect 가 마친다. 취소하면 이 화면에 남는다.
                        if (account.isGuest) { viewModel.signIn(); return@DoneStep }
                        finished = true
                        // 퇴장 연출은 MainActivity 루트 전환이 맡는다 — 여기서 먼저 지우면 빈 화면이 스쳤다(9/29).
                        onFinish(false, false)   // 알림 권한은 ⑤에서 이미 물었다
                    }
                }
            }
            }
        }
    }
    }
}

// ── 공통 조각 ──────────────────────────────────────────────────────────────

/** GLDS 하단 버튼 자리 — 아래에서 올라오는 등장 + 좌우 8 들여씀, 보조는 위 8. */
@Composable
private fun Modifier.cta(primary: Boolean, delayMs: Int = if (primary) 140 else 200): Modifier =
    enterUp(delayMs).padding(horizontal = 8.dp).padding(top = if (primary) 0.dp else 8.dp).fillMaxWidth()

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
private fun WelcomeStep(loggedIn: Boolean, onStart: (leave: suspend () -> Unit) -> Unit, onLogin: () -> Unit) {
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
        if (loggedIn) {
            // 이미 로그인된 채 다시 시작한 경우 — 로그인을 다시 묻지 않는다.
            GldsButton("시작하기", size = GldsSize.L, modifier = Modifier.cta(true), onClick = {
                onStart {
                    // 타일이 차례로 위로 흩어진 뒤 넘어간다(0.32초)
                    leaves.forEachIndexed { i, a -> scope.launch { delay(i * 30L); a.animateTo(1f, tween(300)) } }
                    delay(320)
                }
            })
        } else {
            // 구글 로그인이 곧 시작이다 — ⑥ 완료 화면에 있던 버튼 · 문구 그대로다.
            Box(Modifier.enterUp(140).padding(horizontal = 8.dp)) {
                GoogleSignInButton("Google로 로그인하고 시작하기", onClick = onLogin)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── ② 게임 ─────────────────────────────────────────────────────────────────

@Composable
private fun GamesStep(
    games: Set<String>, viewport: Dp, stagger: Boolean,
    /** 바로 로그인해서 온 경우의 안내 한 줄(없으면 null). [noteLoading] 이면 앞에 로딩 원이 돈다. */
    note: String? = null, noteLoading: Boolean = false,
    onToggle: (String) -> Unit, onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(if (stagger) Modifier.enterUp(40, 16.dp) else Modifier) {
            Title("어떤 게임을 하세요?", "고른 게임이 지출 입력 맨 위에 오고, 출석도 고른 게임만 챙겨요.")
        }
        Spacer(Modifier.height(if (viewport < 640.dp) 16.dp else 24.dp))
        // 바로 로그인해서 온 경우 — 골라져 있는 것이 계정에서 불러온 이전 설정이라는 한 줄(④ 의 「연결됐어요」 줄과 같은 모양).
        // 불러오는 동안에는 로딩 원이 돌고, 끝나면 「이전 설정을 불러왔어요」로 바뀐다(10/7).
        AnimatedVisibility(note != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            Row(
                Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TealTint).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (noteLoading) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = Teal)
                else Icon(Icons.Filled.Check, null, tint = Teal, modifier = Modifier.size(16.dp))
                Text(note.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teal)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameData.onboardingGames.forEachIndexed { i, g ->
                val on = g.key in games
                val border by animateColorAsState(if (on) TealBright else Line, label = "gb")
                Row(
                    (if (stagger) Modifier.enterUp(160 + i * 60, 16.dp) else Modifier)
                        .fillMaxWidth()
                        .height(if (viewport < 640.dp) 46.dp else 58.dp)
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
        GldsButton(
            if (n > 0) "${n}개 선택 · 다음" else "게임을 하나 이상 골라 주세요", onNext,
            Modifier.cta(true, if (stagger) 460 else 140), size = GldsSize.L, enabled = n > 0,
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
        GldsButton("다음", onNext, Modifier.cta(true), size = GldsSize.L)
        GldsButton("예산 없이 쓸게요", onSkip, Modifier.cta(false), variant = GldsVariant.Secondary)
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
                    textStyle = LocalTextStyle.current.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = amtColor, letterSpacing = (-0.5).sp),
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
private fun HoyolabStep(viewModel: SpendingViewModel, hoyo: HoyolabConfig, games: Set<String>, viewport: Dp, onNext: () -> Unit) {
    val compact = viewport < 750.dp   // 낮은 화면: 히어로 얇게 · 타일 3열 · 설명 생략
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
                .background(Brush.linearGradient(listOf(Ink, Color(0xFF23345C)))).padding(horizontal = 20.dp, vertical = if (compact) 12.dp else 18.dp),
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
        perks.chunked(if (compact) 3 else 2).forEach { row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (ic, t, d) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Ground).padding(if (compact) 10.dp else 14.dp)) {
                        Box(Modifier.size(34.dp).shadow(1.dp, RoundedCornerShape(11.dp)).clip(RoundedCornerShape(11.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(ic, null, tint = Teal, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
                        Text(t, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                        if (!compact) Text(d, fontSize = 12.sp, color = Sub)
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
            GldsButton("다음", onNext, Modifier.cta(true), size = GldsSize.L)
            GldsButton("연결 해제", { viewModel.updateHoyolabConfig(HoyolabConfig()) }, Modifier.cta(false), variant = GldsVariant.Neutral)
        } else {
            GldsButton(if (working) "UID 확인 중…" else "HoYoLAB 로그인", { showLogin = true }, Modifier.cta(true), variant = GldsVariant.Inverse, size = GldsSize.L, enabled = !working)
            GldsButton("나중에 연결할게요", onNext, Modifier.cta(false), variant = GldsVariant.Neutral)
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
    dndStart: Int, dndEnd: Int, viewport: Dp, onToggle: (Int) -> Unit, onFinish: (Boolean) -> Unit,
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
        // 미리보기 알림 — 호요버스 게임이 없으면 예산 알림으로. 화면이 낮으면 건너뛴다(스위치가 먼저).
        if (viewport >= 680.dp) Column(
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
        GldsButton("알림 켜고 시작하기", { onFinish(true) }, Modifier.cta(true), size = GldsSize.L)
        GldsButton("알림 없이 시작", { onFinish(false) }, Modifier.cta(false), variant = GldsVariant.Secondary)
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
private fun DoneStep(restored: Boolean, signInNext: Boolean, summary: List<Triple<ImageVector, String, String>>, onDone: () -> Unit) {
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
                ) { Icon(if (restored) Icons.Filled.Person else Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (restored) "로그인하고 시작해요" else "준비 끝!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    restored -> "Gatcha Log 는 기록을 구글 계정에 안전하게 저장해요"
                    signInNext -> "Google 계정으로 로그인하면 바로 시작해요"
                    else -> "이제 홈에서 내 기록을 볼 수 있어요"
                },
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
                        if (restored) {
                            // 좋은 점 — 제목(굵게) + 설명 두 줄
                            Column(Modifier.weight(1f)) {
                                Text(k, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text(v, fontSize = 12.sp, color = Sub)
                            }
                        } else {
                            Text(k, fontSize = 13.sp, color = Sub)
                            Spacer(Modifier.width(12.dp))
                            Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.End, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                    if (i < summary.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE6ECEA)))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                when {
                    restored -> "구글 로그인만 써요 · 게스트 모드는 없어요"
                    signInNext -> "고른 설정은 구글 계정에 저장돼 기기를 바꿔도 그대로예요"
                    else -> "설정 ▸ 내 설정에서 언제든 바꿀 수 있어요"
                },
                fontSize = 12.sp, color = Color(0xFF7A8784), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (signInNext) {
            // 로그인 전(첫 사용자 · 복원) — 구글 로그인 버튼(9/29). 누르면 로그인 화면을 거치지 않고 바로 구글 계정 선택이 뜬다.
            Box(Modifier.enterUp(140).padding(horizontal = 8.dp)) {
                GoogleSignInButton(if (restored) "Google로 로그인하기" else "Google로 로그인하고 시작하기", onClick = onDone)
            }
        } else {
            GldsButton("홈으로 이동하기", onDone, Modifier.cta(true), size = GldsSize.L)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 구글 로그인 버튼 — 구글 로그인 브랜딩 가이드의 밝은 스타일(흰 바탕 · #747775 테두리 · 4색 G 로고 · #1F1F1F 글자).
 * 로그인 화면과 온보딩 복원 화면이 같이 쓴다(9/29).
 */
@Composable
internal fun GoogleSignInButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, Color(0xFF747775), RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_google_g), null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
    }
}
