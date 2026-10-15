package com.gatcha.log.ui.spending

import com.gatcha.log.ui.components.gldsToastObstacle
import com.gatcha.log.ui.components.GldsSection
import com.gatcha.log.ui.components.GldsBand
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import com.gatcha.log.ui.components.GldsChip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import com.gatcha.log.ui.components.GlgDropdownItem
import com.gatcha.log.ui.components.GlgDropdownMark
import com.gatcha.log.ui.components.GlgDropdownMenu
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import kotlinx.coroutines.launch
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GamePackage
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.PackageCatalog
import com.gatcha.log.data.PickupOption
import com.gatcha.log.data.SpendingPickups
import com.gatcha.log.data.PackagePick
import com.gatcha.log.data.PackagePickItem
import com.gatcha.log.data.PackagePickSummary
import com.gatcha.log.data.PickGroupKind
import com.gatcha.log.data.PkgCategory
import com.gatcha.log.data.api.PackageCatalogApi
import com.gatcha.log.data.FrequentItem
import com.gatcha.log.data.SpendingDefaults
import com.gatcha.log.data.SpendingEditLogic
import com.gatcha.log.data.currencyAmountOrNull
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GldsAmountCard
import com.gatcha.log.ui.components.GldsAmountCardDefaults
import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsVariant
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgChipVariant
import com.gatcha.log.ui.components.GlgDatePickerDialog
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GldsIcon
import com.gatcha.log.ui.components.GldsTextField
import com.gatcha.log.ui.components.ThousandsTransformation
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.glgShortSpec
import com.gatcha.log.ui.theme.glgStandardSpec
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

/** 3차 텍스트 — 화살표·보조 라벨. 다른 화면들과 같은 값. */
private val TextThird = androidx.compose.ui.graphics.Color(0xFF98A0AB)

private val SheetBg = Color.White   // D · 모달 배경 흰색(연회색 카드와 대비)
private val CardBg = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpendingModal(
    spendingToEdit: Spending? = null,
    /** 스마트 기본값·'자주 사는 것'의 근거. 비어 있으면 추론하지 않는다. */
    recentSpendings: List<Spending> = emptyList(),
    // 날짜도 넘긴다(27.52.0) — 지난달 날짜로 추가할 때는 이번 달 예산 경고를 내지 않는다.
    nudgeMessage: (game: Game, amount: Long, dateMillis: Long) -> String? = { _, _, _ -> null },
    /** 같은 판정의 간결한 한 줄("평소보다 큰 지출" 등) — 게임 칩 옆에 늘 서 있는 표시가 쓴다. 경고할 것이 없으면 null. */
    nudgeBadge: (game: Game, amount: Long, dateMillis: Long) -> String? = { _, _, _ -> null },
    /** 내 게임(계정 데이터 — 비어 있으면 전부). 게임 목록에서 위로 올린다. */
    myGames: Set<String> = emptySet(),
    /** 지금 열린 픽업 배너 — 「지금 픽업 중」 섹션의 근거. 비어 있으면 그 섹션을 그리지 않는다. */
    activeBanners: List<GachaBanner> = emptyList(),
    onDismiss: () -> Unit,
    /** 저장됐으면 true. false(검증 실패 — 안내는 VM 이 띄운다)면 페이지를 그대로 둔다. */
    onSave: (Spending) -> Boolean,
) {
    val editing = spendingToEdit != null

    // spendingToEdit 가 바뀌면(수정 진입/대상 변경) 상태를 다시 채운다.
    //
    // 추가 진입이면 **앱이 아는 값으로 시작**한다(추론은 공유 SpendingDefaults 가 한다).
    // 기록이 없으면 추론하지 않고 예전 기본값(원신·카드·빈칸) 그대로 간다.
    //
    // 폼 값은 전부 rememberSaveable 이다(27.52.0) — remember 일 때는 적다가 다른 앱에 다녀온 사이 화면이
    // 다시 만들어지면 입력이 통째로 사라졌다. 번들에 못 넣는 값은 문자열로 풀어 둔다(게임 = 키 · 상품 = 이름).
    var gameKey by rememberSaveable(spendingToEdit) {
        mutableStateOf(
            // 미선택 상태의 표시는 gameChosen 이 맡는다. 이 값은 고르기 전까지 쓰이지 않는다.
            (GameData.byNameOrNull(spendingToEdit?.gameName ?: "원신") ?: Game.GENSHIN).key,
        )
    }
    val game = Game.entries.firstOrNull { it.key == gameKey } ?: Game.GENSHIN
    // 목록에 없는 게임명의 지출(27.52.0) — 위 game 은 원신으로 떨어져 있다. 예전엔 그대로 저장해서, 금액만 고쳐도
    // 게임명 · 게임색이 원신으로 덮였다. 사용자가 게임을 **직접 바꾸기 전까지는** 원래 이름 · 색을 들고 간다.
    val unknownGameName = spendingToEdit?.gameName?.takeIf { GameData.byNameOrNull(it) == null }
    var gameChanged by rememberSaveable(spendingToEdit) { mutableStateOf(false) }
    val keepOriginalGame = unknownGameName != null && !gameChanged
    var amount by rememberSaveable(spendingToEdit) { mutableStateOf(spendingToEdit?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var dateMillis by rememberSaveable(spendingToEdit) { mutableLongStateOf(spendingToEdit?.dateMillis ?: System.currentTimeMillis()) }
    var paymentMethod by rememberSaveable(spendingToEdit) {
        mutableStateOf(
            spendingToEdit?.paymentMethod
                ?: SpendingDefaults.topPaymentMethod(recentSpendings)
                ?: "카드",
        )
    }
    var chargePlatform by rememberSaveable(spendingToEdit) {
        // 게임이 정해져야 고를 수 있어 초기엔 비운다 — 게임 선택 시 onGameChange 에서 채운다.
        mutableStateOf(spendingToEdit?.chargePlatform ?: "")
    }
    var itemName by rememberSaveable(spendingToEdit) { mutableStateOf(spendingToEdit?.itemName ?: "") }
    var memo by rememberSaveable(spendingToEdit) { mutableStateOf(spendingToEdit?.memo ?: "") }
    // 수정 진입 시 기존 태그를 둘로 가른다(27.52.0, iOS 와 같은 규칙) — 추천 목록에 있는 것은 칩으로,
    // **없는 것은 직접 입력 칸에** 채운다. 예전엔 전부 selectedTags 에 담고 칩은 추천 목록만 그려서, 직접 입력했던
    // 태그는 보이지도 않고 지울 길도 없었다(저장하면 그대로 다시 붙었다). 칸에 있으면 거기서 지우는 것으로 빠진다.
    // 가르는 규칙 · 이어 붙이는 구분자는 공용 [SpendingEditLogic.splitTags] 가 정한다 — 저장 때 이 칸을 쪼개는
    // [SpendingEditLogic.mergeTags] 와 한 파일에 있어 둘이 어긋나지 않는다.
    val tagSplit = remember(spendingToEdit) { SpendingEditLogic.splitTags(spendingToEdit?.tags.orEmpty()) }
    var customTags by rememberSaveable(spendingToEdit) { mutableStateOf(tagSplit.custom) }
    val selectedTags = rememberSaveable(
        spendingToEdit,
        // listSaver 를 쓰지 않는다 — 빈 목록은 저장하지 않아서, 칩을 전부 끈 뒤 복원되면 처음 태그가 되살아난다.
        saver = Saver<SnapshotStateList<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toMutableStateList() }),
    ) { tagSplit.selected.toMutableStateList() }
    // 픽업 태그(10/13) — 「지금 픽업 중」에서 고른 이름들. 저장할 때 표식을 붙여 태그 목록 맨 뒤에 들어간다([PickupTag]).
    val selectedPickups = rememberSaveable(
        spendingToEdit,
        saver = Saver<SnapshotStateList<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toMutableStateList() }),
    ) { tagSplit.pickups.toMutableStateList() }
    // 수정 진입 시 저장된 항목명("창세의 결정 300 ×3")에서 상품·구매 횟수를 복원 → 스텝퍼가 그대로 노출.
    // 되읽는 규칙은 공용 [SpendingEditLogic.restoreItem] 이다(27.52.0) — 목록에 없는 게임명이면 상품을 찾지 않는다.
    // 고른 상품은 **이름**으로 들고 있다(상품명은 한 게임 안에서 겹치지 않는다).
    val restoredItem = remember(spendingToEdit) {
        spendingToEdit?.let { SpendingEditLogic.restoreItem(it.gameName, it.itemName) }
    }
    var selectedPackageName by rememberSaveable(spendingToEdit) { mutableStateOf(restoredItem?.packageName) }
    // 재화 목록은 어드민이 정한다(10/13) — 화면을 열 때 한 번 받아 보고, 목록이 갈리면([PackageCatalog.revision])
    // 열려 있는 채로 다시 읽는다. 못 받으면 직전에 받은 값 · 앱 내장값 그대로다.
    val catalogRev by PackageCatalog.revision.collectAsState()
    LaunchedEffect(Unit) { PackageCatalogApi.load() }
    val selectedPackage = remember(selectedPackageName, game, catalogRev) {
        selectedPackageName?.let { name -> GameData.packagesFor(game).firstOrNull { it.name == name } }
    }
    // 한 번에 같은 상품을 여러 번 산 경우 — 횟수만큼 금액·재화를 곱해 한 건으로 기록.
    var quantity by rememberSaveable(spendingToEdit) {
        mutableIntStateOf(restoredItem?.quantity ?: SpendingEditLogic.MIN_QUANTITY)
    }
    val showDatePicker = remember { mutableStateOf(false) }
    // N6 과소비 넛지 — 저장 직전 경고 메시지(있으면 확인 다이얼로그 노출 후 그래도 추가 시 저장).
    var nudgeMsg by remember { mutableStateOf<String?>(null) }
    // '자세히'(결제·플랫폼·태그·메모·구독) — **수정 진입은 펼친 채 시작**한다(무엇을 고치러 왔는지 모른다).
    var detailsExpanded by rememberSaveable(spendingToEdit) { mutableStateOf(editing) }

    // 사용자가 게임을 **직접 골랐는가.**
    //
    // 추가 진입은 게임을 미리 정해두지 않는다. 마지막에 기록한 게임을 자동으로 넣으면
    // 다른 게임을 기록하러 온 사람이 **못 알아채고 엉뚱한 게임에 저장**한다 —
    // 지출은 게임별 집계·예산의 기준이라 그 오기록은 나중에 찾기 어렵다.
    // 결제수단·플랫폼과 달리 게임은 "틀려도 티가 안 나는" 값이 아니다. 수정 진입은 이미 정해져 있다.
    var gameChosen by rememberSaveable(spendingToEdit) { mutableStateOf(editing) }

    // 히어로에 바로 뜨는 과소비 경고 — 저장을 누른 뒤가 아니라 금액이 정해지는 순간에 알린다.
    // 입력 도중 매 글자마다 뜨면 방해가 되므로 손이 멈춘 뒤에만 평가한다.
    var inlineNudge by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(amount, game, dateMillis) {
        kotlinx.coroutines.delay(450)
        inlineNudge = nudgeBadge(game, amount.toLongOrNull() ?: 0L, dateMillis)
    }

    // 같은 게임에서 2회 이상 산 것만, 많이 산 순. 수정 진입에는 쓰지 않는다.
    val frequent = remember(recentSpendings, game, editing) {
        if (editing) emptyList() else SpendingDefaults.frequentItems(recentSpendings, game.displayName, limit = 3)
    }

    // 과소비 경고를 **지금** 다시 잰다 — 상품을 고르거나 횟수를 바꿔 금액이 한 번에 정해졌을 때(10/13, iOS 와 같다).
    // 금액을 치는 동안에는 손이 멈춘 뒤(위 450ms)에 재지만, 고른 금액은 기다릴 이유가 없다. 기다리면 히어로에
    // 상품명 · 환산 줄이 먼저 서고 한 박자 뒤 경고 줄이 끼어들어, 방금 누른 재화 칸이 뒤늦게 한 번 더 밀려 내려간다.
    fun refreshNudgeNow() {
        inlineNudge = nudgeBadge(game, amount.toLongOrNull() ?: 0L, dateMillis)
    }

    // 금액 굴림 신호(10/13 지시) — 상품을 고르거나 횟수를 바꿔 금액이 한 번에 정해질 때마다 올린다.
    // 히어로가 이 값이 바뀐 것을 보고 큰 숫자를 이전 금액에서 새 금액까지 굴린다(직접 치는 동안에는 굴리지 않는다).
    var amountRollTick by remember { mutableIntStateOf(0) }

    fun applyPackage(pkg: GamePackage) {
        selectedPackageName = pkg.name
        quantity = 1
        amount = pkg.price.toString()
        itemName = pkg.name
        amountRollTick++
        refreshNudgeNow()
    }

    // 구매 횟수 변경 — 선택된 상품 기준으로 금액·재화명을 N배로 다시 계산.
    // 범위(1~99) · 「×N」을 붙이는 모양은 공용 [SpendingEditLogic] 이 정한다(27.52.0, iOS 와 한 소스).
    fun setQuantity(q: Int) {
        val qty = SpendingEditLogic.clampQuantity(q)
        quantity = qty
        selectedPackage?.let { pkg ->
            val applied = SpendingEditLogic.applyQuantity(pkg.name, pkg.price, qty)
            amount = applied.amount.toString()
            itemName = applied.itemName
            amountRollTick++
            refreshNudgeNow()
        }
    }

    fun buildSpending(): Spending {
        val parsed = amount.toLongOrNull() ?: 0L
        // 칩 + 직접 입력 칸을 한 목록으로 — 쪼개기 · 다듬기 · 중복 제거는 공용 규칙(27.52.0, iOS 와 한 소스).
        val tags = SpendingEditLogic.mergeTags(selectedTags, customTags, selectedPickups)
        val base = spendingToEdit ?: Spending(gameName = game.displayName, amount = parsed)
        return base.copy(
            // 목록에 없는 게임명은 게임을 직접 바꾸지 않았으면 원래 이름 · 색 그대로 둔다(위 keepOriginalGame).
            gameName = if (keepOriginalGame) base.gameName else game.displayName,
            amount = parsed,
            dateMillis = dateMillis,
            paymentMethod = paymentMethod,
            chargePlatform = chargePlatform,
            itemName = itemName,
            memo = memo,
            tags = tags,
            gameColor = if (keepOriginalGame) base.gameColor else game.color,
        )
    }

    // 한 번만 저장한다 — 페이지가 닫히는 애니메이션 동안 버튼이 살아 있어, 빠르게 두 번 누르면 두 건이 들어갔다.
    // 저장이 거절되면(검증 실패) 다시 누를 수 있게 푼다 — 페이지가 안 닫히니 고쳐서 다시 저장한다.
    var saved by remember { mutableStateOf(false) }
    fun save(s: com.gatcha.log.data.Spending) { if (!saved) { saved = true; if (!onSave(s)) saved = false } }

    // 입력 지문 — 처음과 다르면 "고친 게 있다". 뒤로가기 · 취소가 예전엔 묻지 않고 입력을 버렸다(iOS 와 같은 판정).
    // 게임을 고른 것도 입력이다 — 기본값이 원신이라 원신을 고르면 이름만으론 안 바뀐다.
    // 목록에 없는 게임명은 원래 이름으로 센다 — 거기서 원신을 골라도(game 은 이미 원신이다) 바뀐 것으로 잡히게.
    fun fingerprint() = listOf(
        gameChosen, if (keepOriginalGame) unknownGameName else game.displayName, amount, dateMillis, paymentMethod, chargePlatform,
        itemName, memo, customTags, selectedTags.joinToString(","), selectedPickups.joinToString(","),
    ).joinToString("|")
    // 처음 지문도 같이 저장한다 — 복원된 뒤에 다시 뜨면 고친 값이 「처음」이 되어, 고쳐 놓고도 묻지 않고 닫혔을 것이다.
    // 수정 진입 때 직접 입력 칸에 채워 둔 기존 태그도 이 처음 값에 들어 있어 「바뀐 것」으로 잡히지 않는다.
    val initialFingerprint = rememberSaveable(spendingToEdit) { fingerprint() }
    var confirmDiscard by remember { mutableStateOf(false) }
    fun requestDismiss() { if (!saved && fingerprint() != initialFingerprint) confirmDiscard = true else onDismiss() }

    // 저장 시도 — 넛지 메시지가 있으면 확인 다이얼로그를 띄우고, 없으면 즉시 저장.
    fun attemptSave() {
        val parsed = amount.toLongOrNull() ?: 0L
        val msg = nudgeMessage(game, parsed, dateMillis)
        if (msg != null) nudgeMsg = msg else save(buildSpending())
    }

    // 시스템 뒤로가기 처리(풀스크린 페이지처럼 동작) — 시트 외부 dismiss 가 사라졌으므로 명시.
    androidx.activity.compose.BackHandler { requestDismiss() }

    Surface(
        color = SheetBg,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 헤더는 앱 표준 상세 헤더(GlgDetailHeaderOverlay)로 통일한다 —
        // 예전엔 이 화면만 22sp 제목 + 우측 원형 X 라는 자기만의 머리를 갖고 있어,
        // 설정 같은 다른 하위 페이지와 생김새도 뒤로가기 위치도 달랐다.
        Box(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        val scrolled by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // 카드 없이 화면 폭 섹션 + 10 띠(지출 상세 · 마이페이지와 같은 규격). 히어로만 좌우 16 을 둔다.
                contentPadding = PaddingValues(top = glgDetailContentTop()),
            ) {
                // ── 금액 히어로 — 게임·금액·상품·재화 환산을 한 덩어리로 ──
                //
                // 금액은 지출에서 가장 중요한 값인데 예전엔 '빠른 상품' 카드 안쪽, 그리드 아래
                // 일반 필드로 있었다. 목록·상세·인사이트는 전부 금액이 히어로인데 입력할 때만 아니었다.
                item {
                    val chooseGame: (Game) -> Unit = { g ->
                        gameKey = g.key
                        gameChosen = true
                        gameChanged = true
                        selectedPackageName = null
                        quantity = 1
                        itemName = ""
                        selectedPickups.clear()   // 픽업은 게임에 묶인 값이다
                        if (!editing) chargePlatform = SpendingDefaults.lastPlatform(recentSpendings, g.displayName) ?: ""
                    }
                    // 미선택 — 고르기 전에는 금액을 받지 않는다. 히어로 대신 게임 목록이 화면을 연다.
                    if (!gameChosen) {
                        GamePickSection(myGames, chooseGame)
                        return@item
                    }
                    AmountHero(
                        myGames = myGames,
                        game = game,
                        amount = amount,
                        onAmountChange = { input ->
                            // 11자리(999억)까지만 — 더 긴 붙여넣기는 조용히 0 이 됐다. 상한(100억)은 저장할 때 VM 이 막는다.
                            amount = input.filter { it.isDigit() }.take(11)
                            // 금액을 직접 손대면 자동 곱 상태를 해제(스텝퍼 계산과 어긋나지 않게).
                            selectedPackageName = null
                            quantity = 1
                        },
                        gameChosen = gameChosen,
                        onGameChange = chooseGame,
                        itemName = itemName,
                        rollTick = amountRollTick,
                        nudge = inlineNudge,
                        unknownGameName = unknownGameName.takeIf { keepOriginalGame },
                        unknownGameColor = spendingToEdit?.gameColor,
                    )
                }

                // 게임을 고르기 전에는 나머지를 띄우지 않는다 — 상품 목록·기본값이 전부 게임에 묶여 있어
                // 미선택 상태로 보여주면 어느 게임 것인지 알 수 없는 화면이 된다.
                //
                // 게임을 고르면 아래 섹션들이 **제자리에 바로 선다**(10/13 지시, iOS 와 같다) — 예전엔 밀려 올라오며 나타났다.
                // 이 페이지에 남긴 움직임은 넷이다: 「자세히」 접고 펴기 · 화살표 회전 · 섹션 높이 변화 · 과소비 경고가 뜨고 질 때.
                // 페이지가 밀려 들어오고 나가는 전환은 이 바깥(HomeScreen)이라 그대로다.

                // ── 지금 픽업 중(10/13) — 누구를 뽑으려고 썼는지 골라 두면 장식 태그로 남는다 ──
                //
                // 그 게임에서 지금 열린 최고 등급 픽업(캐릭터 · 무기)을 칩으로 늘어놓는다. 여러 개 고를 수 있다.
                // 배너 데이터가 없는 게임(명조 · 엔드필드 · 이환)은 이 섹션이 통째로 빠진다.
                // 이미 붙어 있던 지난 픽업은 뒤에 남겨, 고치는 화면에서 뗄 수 있게 한다([SpendingPickups.options]).
                // 자리는 재화 선택 **위**다(같은 날 지시) — 누구를 위해 쓰는지를 먼저 고른다.
                item {
                    val pickups = remember(activeBanners, game, selectedPickups.toList()) {
                        SpendingPickups.options(activeBanners, game, System.currentTimeMillis(), selectedPickups.toList())
                    }
                    if (gameChosen && pickups.isNotEmpty()) {
                    FormSection {
                        Text("지금 픽업 중", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("고르면 태그로 남아요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp))
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pickups, key = { it.name }) { option ->
                                PickupChip(option, selected = option.name in selectedPickups) {
                                    if (option.name in selectedPickups) selectedPickups.remove(option.name) else selectedPickups.add(option.name)
                                }
                            }
                        }
                    }
                    }
                }

                // ── 상품 ──
                item {
                    if (gameChosen) {
                    FormSection {
                        // 재화 선택(10/13 개편, 시안 C) — 묶음 · 칸에 적는 글자는 공용 [PackagePick] 이 만든다(iOS 와 한 소스).
                        // 목록은 늘 펼쳐 둔다. 예전의 「전체 상품 보기 / 접기」 · 분류 칩은 걷었다 — 묶음 머리가 그 일을 한다.
                        val packages = remember(game, catalogRev) { GameData.packagesFor(game) }
                        val groups = remember(game, packages) { PackagePick.groups(game, packages) }
                        val gameColor = game.color.toColor()
                        // 자주 사는 것 — 기록이 없는 게임은 이 블록이 통째로 빠지고 안내 문구가 선다
                        // (빈도를 모르는데 임의로 셋을 고르면 그건 추천이 아니다).
                        if (frequent.isNotEmpty()) {
                            SectionRowLabel("자주 사는 것")
                            Spacer(Modifier.height(10.dp))
                            PickGrid(frequent) { f, cell ->
                                FrequentTile(
                                    item = f,
                                    selected = PackagePick.isFrequentSelected(f.itemName, selectedPackageName, itemName),
                                    modifier = cell,
                                ) {
                                    selectedPackageName = packages.firstOrNull { p -> p.name == f.itemName }?.name
                                    quantity = 1
                                    itemName = f.itemName
                                    amount = f.amount.toString()
                                    amountRollTick++
                                    refreshNudgeNow()
                                }
                            }
                        } else {
                            SectionRowLabel("빠른 상품 선택")
                            Text("선택하면 금액 · 재화명이 자동 입력돼요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        groups.forEachIndexed { i, group ->
                            // 안내 문구 바로 아래 첫 묶음만 16, 나머지 머리는 18.
                            Spacer(Modifier.height(if (i == 0 && frequent.isEmpty()) 16.dp else 18.dp))
                            PickGroupHead(group.title, group.hint)
                            Spacer(Modifier.height(10.dp))
                            if (group.kind == PickGroupKind.PLAN) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.items.forEach { item ->
                                        PlanRow(item, selected = selectedPackage == item.pkg, gameColor = gameColor) { applyPackage(item.pkg) }
                                    }
                                }
                            } else {
                                PickGrid(group.items) { item, cell ->
                                    CurrencyTile(
                                        item = item,
                                        tier = group.kind == PickGroupKind.TIERS,
                                        selected = selectedPackage == item.pkg,
                                        gameColor = gameColor,
                                        modifier = cell,
                                    ) { applyPackage(item.pkg) }
                                }
                            }
                        }
                        // 요약 면 — 상품을 골랐을 때만. 무엇을 몇 번 사서 얼마 · 재화 얼마인지와 횟수 스텝퍼를 한 면에 둔다.
                        selectedPackage?.let { pkg ->
                            Spacer(Modifier.height(16.dp))
                            PickSummaryFace(
                                summary = PackagePick.summary(game, pkg, quantity, amount.toLongOrNull() ?: 0L),
                                quantity = quantity,
                                onChange = { setQuantity(it) },
                            )
                        }
                        Spacer(Modifier.height(if (selectedPackage != null) 12.dp else 16.dp))
                        GldsTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = "재화명",
                            placeholder = "결정석 60",
                            // 환산이 깨지면 조용히 넘어가지 않고 이유를 말한다(막지는 않는다 — 환산은 편의다).
                            helper = if (itemName.isNotBlank() && currencyAmountOrNull(game.displayName, itemName) == null) {
                                "재화 환산이 안 돼요 — '결정석 60'처럼 이름 뒤에 숫자를 붙이면 뽑기 수까지 계산해요"
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    }
                }

                // ── 날짜 ──
                item {
                    if (gameChosen) {
                    FormSection {
                        GldsTextField(
                            value = DateUtil.labelWithWeekday(dateMillis),
                            onValueChange = {},
                            label = "날짜",
                            readOnly = true,
                            trailingIcon = GldsIcon.Calendar,
                            onClick = { showDatePicker.value = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    }
                }

                // ── 자세히 — 결제수단·플랫폼·태그·메모·구독을 하나로 접는다 ──
                //
                // 매번 바뀌는 값이 아니라 접어 두되, **접힌 채로도 현재 값 요약을 보여준다.**
                // 안 보이면 확인하려고 매번 펴게 되고, 그러면 접은 의미가 없다.
                item {
                    if (gameChosen) {
                    FormSection {
                        // 요약 글자 · 강조 여부는 공용 계산(27.52.0) — iOS 는 공백뿐인 메모도 「메모」로 세고 있었다.
                        val summary = SpendingEditLogic.detailSummary(paymentMethod, chargePlatform, selectedTags, customTags, memo)
                        val detailsScope = rememberCoroutineScope()
                        Row(
                            // clip 을 걸지 않는다. 리플이 없는 앱(PressScaleIndication)이라 모양을 깎을
                            // 이유가 없는데, `clip` 은 **자기 자식까지 깎는다** — 이 줄은 좌우 패딩이
                            // 없어서 "자세히" 의 'ㅈ' 가 x=0 에서 시작하고, 12dp 모서리 곡선이 그
                            // 왼쪽 위를 잘라 먹었다. 밀도가 높은 에뮬레이터에선 몇 px 라 안 보이고
                            // 실기기에서만 보였다.
                            Modifier.fillMaxWidth()
                                .clickable {
                                    val open = !detailsExpanded
                                    detailsExpanded = open
                                    // 펼칠 때는 **화면이 같이 올라간다**(10/13 지시, iOS 와 같다) — 넓어지는 동안 매 프레임 끝까지 따라가
                                    // 펼친 칸이 끝까지 보인다. 접을 때는 목록 끝이 줄어드는 만큼 화면이 저절로 따라 내려온다.
                                    if (open) detailsScope.launch {
                                        val start = withFrameNanos { it }
                                        while (true) {
                                            val now = withFrameNanos { it }
                                            listState.scrollBy(10_000f)
                                            if (now - start > 600_000_000L) break
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 제목·요약 모두 한 줄로 끊되 **말줄임표로** 끊는다. 예전엔 maxLines 만
                            // 걸려 있어 "카드 · 태그 없음" 이 "카드 · 태그" 에서 그냥 잘렸다 —
                            // 글자가 잘린 것인지 원래 그 값인지 구분이 안 된다.
                            Column(Modifier.weight(1f)) {
                                // 제목은 섹션 제목 크기(17 Bold)다(10/13 지시 — 그 전에는 13 이라 섹션 머리로 안 읽혔다).
                                Text(
                                    "자세히",
                                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    summary.text,
                                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    // 기본값과 다른 값이 있으면 강조 — "뭔가 정해져 있다"가 보이게.
                                    color = if (summary.isCustom) LocalAccent.current else TextSecondary,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // 접고 펴는 표시는 **화살표 하나를 돌린다**(iOS 와 같은 모양 — 10/13 지시로 글자 「▾」 대신 아이콘).
                            val arrow by animateFloatAsState(if (detailsExpanded) 180f else 0f, glgStandardSpec(), label = "detailsArrow")
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSecondary, modifier = Modifier.size(18.dp).rotate(arrow))
                        }
                        // 접이식 — 위에서 아래로 펼쳐지고 같은 길로 접힌다(칩 줄과 같은 결).
                        AnimatedVisibility(
                            visible = detailsExpanded,
                            enter = expandVertically(glgStandardSpec()) + fadeIn(glgStandardSpec()),
                            exit = shrinkVertically(glgShortSpec()) + fadeOut(glgShortSpec()),
                        ) {
                        Column {
                        Spacer(Modifier.height(14.dp))
                        SectionRowLabel("결제 수단")
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GameData.paymentMethods) { method ->
                                ChoiceChip(label = method, selected = paymentMethod == method) { paymentMethod = method }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        SectionRowLabel("충전 플랫폼 (선택)")
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GameData.chargePlatforms) { platform ->
                                // 선택 항목 — 선택된 칩 다시 탭하면 해제(빈 값)
                                ChoiceChip(label = platform, selected = chargePlatform == platform) {
                                    chargePlatform = if (chargePlatform == platform) "" else platform
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        SectionRowLabel("태그")
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GameData.suggestedTags) { tag ->
                                ChoiceChip(label = tag, selected = tag in selectedTags) {
                                    if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        GldsTextField(
                            value = customTags,
                            onValueChange = { customTags = it },
                            placeholder = "직접 입력 (쉼표로 구분)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        GldsTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            label = "메모",
                            placeholder = "이벤트 구입",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        }
                        }
                    }
                    }
                }
            }

            // Bottom Actions — 하단(내비 영역까지 흰 띠), 버튼은 내비 위로. 그림자 없는 플랫.
            Surface(color = CardBg, shadowElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    // 토스트의 가리는 것 — 버튼 줄. 토스트는 그 위 12 에 선다(10/15, 예전엔 아래에서 100 이라 버튼을 가렸다).
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).gldsToastObstacle(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 상한도 여기서 막는다 — 넘으면 VM 이 저장을 거절하는데, 버튼이 살아 있으면 누를 때마다 토스트만 뜬다.
                    val parsedAmount = amount.toLongOrNull() ?: 0L
                    val amountTooBig = parsedAmount > Spending.MAX_AMOUNT
                    val amountValid = parsedAmount > 0 && !amountTooBig
                    val canSave = gameChosen && amountValid
                    GldsButton("취소", { requestDismiss() }, Modifier.weight(1f), variant = GldsVariant.Secondary)
                    GldsButton(
                        // 흐린 버튼만 두지 않는다 — 왜 못 누르는지 버튼이 직접 말한다.
                        text = when {
                            !gameChosen -> "게임을 선택하세요"
                            amountTooBig -> "금액이 너무 커요"
                            !amountValid -> "금액을 입력하세요"
                            editing -> "수정하기"
                            else -> "저장하기"
                        },
                        onClick = { attemptSave() },
                        modifier = Modifier.weight(1.5f),
                        enabled = canSave,
                    )
                }
            }
        }
        // 오버레이는 **마지막에** — 콘텐츠 위에 그려져야 한다.
        GlgDetailHeaderOverlay(if (editing) "지출 수정" else "지출 추가", { requestDismiss() }, scrolled)
        }
    }

    if (showDatePicker.value) {
        GlgDatePickerDialog(
            initialMillis = dateMillis,
            onDismiss = { showDatePicker.value = false },
            onConfirm = { dateMillis = it; showDatePicker.value = false },
        )
    }

    if (confirmDiscard) {
        GlgDialog(
            title = "입력한 내용을 버릴까요?",
            onDismiss = { confirmDiscard = false },
            confirmText = "버리기",
            onConfirm = { confirmDiscard = false; onDismiss() },
            dismissText = "계속 입력",
        ) {}
    }

    // N6 과소비 리플렉션 넛지 — 예산·평소치 초과 시 저장 직전 한 번 더 확인(예방형).
    nudgeMsg?.let { msg ->
        GlgDialog(
            title = "잠깐, 다시 한 번 볼까요?",
            onDismiss = { nudgeMsg = null },
            dismissText = "다시 볼게요",
            confirmText = "그래도 추가",
            onConfirm = {
                val s = buildSpending()
                nudgeMsg = null
                save(s)
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Savings, null, tint = LocalAccent.current, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(msg, fontSize = 14.sp, color = TextPrimary)
            }
        }
    }
}

/**
 * 금액 히어로 — 게임·금액·상품·재화 환산·과소비 경고를 한 덩어리로.
 * 지출 상세 히어로와 같은 짜임이라 '기록한 것'과 '나중에 보는 것'이 같은 모양이 된다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountHero(
    myGames: Set<String>,
    game: Game,
    amount: String,
    onAmountChange: (String) -> Unit,
    onGameChange: (Game) -> Unit,
    gameChosen: Boolean,
    itemName: String,
    nudge: String?,
    /** 금액 굴림 신호 — 값이 바뀌면 큰 숫자가 이전 금액에서 지금 금액까지 굴러간다(상품 고르기 · 횟수 바꾸기). */
    rollTick: Int = 0,
    /**
     * 목록에 없는 게임명의 지출을 고치는 중이면 그 이름(게임을 직접 바꾸기 전까지) — 알약에 [game](원신으로
     * 떨어진 값) 대신 이 이름 · [unknownGameColor] 를 보여 준다. 아니면 null.
     */
    unknownGameName: String? = null,
    unknownGameColor: Long? = null,
) {
    val gameColor = when {
        !gameChosen -> TextSecondary
        unknownGameName != null && unknownGameColor != null -> unknownGameColor.toColor()
        else -> game.color.toColor()
    }
    var pickGame by remember { mutableStateOf(false) }
    // 카드 없이 화면 폭 그대로(좌우 20) — 아래 섹션과 10 띠로 갈린다. 게임색은 알약 · 환산 글자가 맡는다.
    Column(
        Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
    ) {
        // 게임 칩과 **과소비 표시**가 한 줄에 선다(10/13 지시, iOS 와 같다) — 왼쪽이 게임, 오른쪽이 표시.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 게임 — 알약을 누르면 **드롭다운 메뉴**로 고른다(iOS Menu 와 같다). 예전엔 칩 줄이 펼쳐졌다.
        Box {
            // 크기는 **GLDS 칩 규격**이다(10/13 지시) — 높이 36 · 좌우 14 · 14 Bold · 화살표 18. 색은 게임색 그대로.
            Row(
                Modifier.height(36.dp)
                    .clip(CircleShape)
                    .background(gameColor.copy(alpha = 0.12f))
                    .clickable { pickGame = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(gameColor))
                Spacer(Modifier.width(6.dp))
                Text(unknownGameName ?: game.shortName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = gameColor, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                // 화살표는 뒤집지 않고 **돌린다** — 모양을 바꾸면 순간이동처럼 보인다.
                val arrow by animateFloatAsState(if (pickGame) 180f else 0f, glgStandardSpec(), label = "gamePickArrow")
                Icon(Icons.Default.KeyboardArrowDown, null, tint = gameColor, modifier = Modifier.size(18.dp).rotate(arrow))
            }
            val pickerOrder = remember(myGames) { GameData.pickerGames(myGames).let { it.first + it.second } }
            GlgDropdownMenu(expanded = pickGame, onDismissRequest = { pickGame = false }) {
                pickerOrder.forEach { g ->
                    GlgDropdownItem(
                        text = g.displayName,
                        // 목록에 없는 게임명이면 고른 것이 없는 상태다 — 원신을 눌러도(game 과 같다) 바꾼 것으로 받는다.
                        selected = unknownGameName == null && g == game,
                        mark = GlgDropdownMark.Radio,
                        onClick = { pickGame = false; if (unknownGameName != null || g != game) onGameChange(g) },
                    )
                }
            }
        }
        // 과소비 표시 — **늘 서 있다.** 큰 지출이면 간결한 경고가 강조되고(Bold · 주황), 아니면 강조 없이 선다.
        // 금액이 비었으면 「금액을 입력해주세요」. 금액 카드 밖이라 문구가 바뀌어도 카드 · 아래 목록이 움직이지 않는다.
        // 자세한 문구는 저장할 때의 확인창이 말한다.
        val statusColor = if (nudge != null) GldsAmountCardDefaults.Warn else TextSecondary
        Row(
            Modifier.weight(1f).padding(start = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 경고일 때만 앞에 **원 안의 느낌표**가 선다(10/13 지시 — 그 전에는 세모 「⚠」 글자였다).
            if (nudge != null) {
                Icon(Icons.Filled.Error, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                when {
                    nudge != null -> nudge
                    amount.isEmpty() -> "금액을 입력해주세요"
                    else -> "평소 범위의 지출"
                },
                fontSize = 12.sp,
                fontWeight = if (nudge != null) FontWeight.Bold else FontWeight.Normal,
                color = statusColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        }
        Spacer(Modifier.height(11.dp))
        // 치는 동안에도 **읽는 모양 그대로** — 세 자리 쉼표를 넣고 뒤에 「원」을 붙인다(iOS 와 같이,
        // 2026-09-21 지시). 쉼표는 보이는 글자에만 넣고([ThousandsTransformation]) 값은 숫자뿐이다.
        //
        // 금액 칸은 GLDS 「예산 금액 입력」 규격의 카드다(10/13 지시 — [GldsAmountCard]). 그 전에는 GLDS 입력필드 모양이었다.
        // **과소비 경고가 떠 있는 동안은 주의 상태**다 — 면이 주황으로 바뀌고 한 번 흔들린 뒤 안쪽 테두리가 깜빡인다.
        // 카드 어디를 눌러도 입력이 시작된다.
        //
        // 상품명 · 재화 환산 · 과소비 문구는 카드에 두지 않는다(같은 날 지시) — 과소비 표시는 위 게임 칩 줄에 선다.
        val amountFocus = remember { FocusRequester() }
        val warn = nudge != null
        // 큰 숫자 굴림(10/13 지시) — 고른 금액은 이전 금액에서 0.45초 동안 굴러가 선다(100원 단위 · 끝에서 감속,
        // 예산 화면의 금액 버튼과 같은 움직임). 직접 치는 동안과 처음 값은 굴리지 않는다. 굴리는 것은 **보이는 숫자뿐**이다 —
        // 값(amount)은 바로 새 금액이라 아래 정보 줄 · 요약 · 경고가 중간 값으로 흔들리지 않는다.
        val amountValue = amount.toLongOrNull() ?: 0L
        var typing by remember { mutableStateOf(true) }
        var seenRollTick by remember { mutableIntStateOf(rollTick) }
        if (rollTick != seenRollTick) { seenRollTick = rollTick; typing = false }
        val rolling = remember { Animatable(amountValue.toFloat()) }
        LaunchedEffect(amountValue, typing) {
            if (typing) rolling.snapTo(amountValue.toFloat())
            else rolling.animateTo(amountValue.toFloat(), tween(450, easing = FastOutSlowInEasing))
        }
        // 굴림이 목표에 닿았으면 값 그대로 보인다 — 중간 값만 100원 단위로 끊는다(끝자리가 있는 금액이 잘리지 않게).
        val shownAmount = if (typing || rolling.value == amountValue.toFloat()) amount else ((rolling.value / 100).toLong() * 100).toString()
        GldsAmountCard(
            label = "금액",
            warn = warn,
            onClick = { amountFocus.requestFocus() },
        ) { color ->
            val numberStyle = GldsAmountCardDefaults.numberStyle(LocalTextStyle.current, color)
            BasicTextField(
                value = shownAmount,
                onValueChange = { typing = true; onAmountChange(it) },
                textStyle = numberStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ThousandsTransformation,
                singleLine = true,
                cursorBrush = SolidColor(color),
                modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 24.dp).focusRequester(amountFocus),
                decorationBox = { inner ->
                    Box {
                        if (amount.isEmpty()) Text("0", style = numberStyle.copy(color = color.copy(alpha = 0.3f)))
                        inner()
                    }
                },
            )
        }
    }
}

/**
 * 입력 섹션 — 카드 없이 화면 폭 그대로, 위에 10 띠를 얹어 앞 묶음과 가른다.
 * 좌우 20 · 위 22 · 아래 20(지출 상세 섹션과 같은 규격). 띠가 섹션 안에 있어 섹션과 함께 나타난다.
 * 안쪽 내용이 바뀌어 높이가 달라지면 늘어나고 줄어드는 모습을 그린다.
 */
@Composable
private fun FormSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        GldsBand()
        GldsSection(content = content)
    }
}

@Composable
private fun SectionRowLabel(text: String) {
    Text(text, fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
}

/**
 * 게임 고르기 — 지출 추가의 첫 화면. 카드 없이 화면 폭 목록(헤어라인 구분)이다.
 *
 * **리스트형**(27.50.0) — 칩을 줄바꿈으로 늘어놓으면 이름 길이가 제각각이라 줄이 들쭉날쭉했다.
 * 가로 스크롤도 쓰지 않는다 — 접어 두면 화면 밖 게임은 있는 줄도 모른다.
 * 예전엔 게임마다 낱개 카드였지만 카드형을 걷으며 지출 목록과 같은 헤어라인 목록으로 바꿨다.
 * 구분은 게임색 배지가 먼저 해 준다.
 * 내 게임(온보딩 ② · 설정 ▸ 내 게임)이 위, 나머지는 「다른 게임」 아래 — 기록할 수 있는 게임은 줄이지 않는다.
 */
@Composable
private fun GamePickSection(myGames: Set<String>, onPick: (Game) -> Unit) {
    val (mine, others) = remember(myGames) { GameData.pickerGames(myGames) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)) {
        Text("어느 게임인가요?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 20.dp))
        Text("게임을 선택해주세요", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 3.dp, bottom = 8.dp))
        GamePickList(mine, onPick)
        if (others.isNotEmpty()) {
            Text("다른 게임", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp))
            GamePickList(others, onPick)
        }
    }
}

@Composable
private fun GamePickList(games: List<Game>, onPick: (Game) -> Unit) {
    games.forEachIndexed { i, g ->
        if (i > 0) Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Color(0xFFEEF0F2)))
        GameSelectRow(g) { onPick(g) }
    }
}

/** 게임 선택 한 줄 — [게임색 배지 · 이름 · 화살표]. 줄 전체가 눌린다. */
@Composable
private fun GameSelectRow(game: Game, onClick: () -> Unit) {
    val c = game.color.toColor()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 배지는 **영어 약칭**(GI · HSR · ZZZ …)이다 — 지출 목록 행([SpendingRow])과 같은 값.
        // 한국어 약칭은 길이가 제각각이라 36dp 칸에서 두 줄로 접히거나 잘렸다.
        // 배지 색은 **게임 대표색** — 강조색을 쓰면 테마에 따라 전부 같은 색이 되어 게임 구분이 사라진다.
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                game.abbr,
                fontSize = 11.sp, fontWeight = FontWeight.Black, color = c,
                textAlign = TextAlign.Center, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        Text(
            game.displayName,
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = TextThird, modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    GldsChip(label, onClick, selected = selected)
}

/**
 * 「지금 픽업 중」의 한 칸 — **GLDS 칩 규격**(높이 36 · 14 Bold · 흰 면 + 1 `#E3E5EA`, 고르면 강조색 1.5 테두리 + deep 글자)에
 * 앞 초상(24 원)을 붙인 모양이다. 초상이 없으면 대체 아이콘(캐릭터 = 사람, 무기 = 별)이 선다.
 */
@Composable
private fun PickupChip(option: PickupOption, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val deep = LocalAccentDeep.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(36.dp)
            .clip(shape)
            .background(Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else Color(0xFFE3E5EA), shape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFF7F8FA)), contentAlignment = Alignment.Center) {
            val fallback: @Composable () -> Unit = {
                Icon(if (option.weapon) Icons.Default.Star else Icons.Default.Person, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            }
            if (option.iconUrl.isBlank()) fallback()
            else SubcomposeAsyncImage(
                model = option.iconUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(), loading = {}, error = { fallback() },
            )
        }
        Text(option.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) deep else TextPrimary, maxLines = 1)
    }
}

// ── 재화 선택(시안 C, 10/13) ──────────────────────────────────────────────
//
// 색 · 치수는 시안 값 그대로다. 강조색은 테마를 따른다 — 면 · 테두리는 [LocalAccent], 글자 · 채운 바닥은 [LocalAccentDeep].

/** 칸의 평소 테두리. */
private val PickRing = Color(0xFFE3E6EA)

/** 자주 사는 것 칸 · 가격 바닥의 채운 면 — GLDS 입력필드와 같은 색. */
private val PickFace = Color(0xFFF5F8F8)

/** 요약 면의 보조 글자. */
private val PickSummarySub = Color(0xFF4A5159)

/** 시안의 선 아이콘(24 격자 · 둥근 끝) — SVG 경로를 그대로 읽어 만든다. 색은 [Icon] 의 tint 로 입힌다. */
private fun pickStrokeIcon(path: String, strokeWidth: Float): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

private val PickIconGem by lazy { pickStrokeIcon("M6 3h12l4 6-10 12L2 9zM2 9h20M9 3l-1.5 6L12 21l4.5-12L15 3", 2f) }
private val PickIconCalendar by lazy { pickStrokeIcon("M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16M8 3v4M16 3v4", 2f) }
private val PickIconTicket by lazy { pickStrokeIcon("M3 9V6h18v3a3 3 0 0 0 0 6v3H3v-3a3 3 0 0 0 0-6zM14 6v12", 2f) }
private val PickIconCheck by lazy { pickStrokeIcon("M20 6L9 17l-5-5", 3f) }
private val PickIconMinus by lazy { pickStrokeIcon("M5 12h14", 2.2f) }
private val PickIconPlus by lazy { pickStrokeIcon("M12 5v14M5 12h14", 2.2f) }

/** 2열 격자(사이 8). 홀수면 마지막 칸은 왼쪽에 선다. */
@Composable
private fun <T> PickGrid(items: List<T>, cell: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { cell(it, Modifier.weight(1f)) }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 묶음 머리 — 왼쪽 이름, 오른쪽 환산("160개 = 1뽑"). */
@Composable
private fun PickGroupHead(title: String, hint: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(1f).alignByBaseline())
        hint?.let { Text(it, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.alignByBaseline()) }
    }
}

/** 칸의 면과 테두리 — 고르면 강조색 8% 면 + 강조색 2 테두리. */
private fun Modifier.pickTile(selected: Boolean, accent: Color, shape: RoundedCornerShape, idleBg: Color, idleRing: Color?): Modifier =
    clip(shape)
        .background(if (selected) accent.copy(alpha = 0.08f) else idleBg)
        .then(
            when {
                selected -> Modifier.border(2.dp, accent, shape)
                idleRing != null -> Modifier.border(1.dp, idleRing, shape)
                else -> Modifier
            },
        )

/** 게임색 12% 원(36) 안의 선 아이콘(20). */
@Composable
private fun PickIconCircle(icon: ImageVector, gameColor: Color) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(gameColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = gameColor, modifier = Modifier.size(20.dp))
    }
}

/** 자주 사는 것 한 칸 — 누르면 상품 · 금액 · 재화명이 한 번에 채워진다. */
@Composable
private fun FrequentTile(item: FrequentItem, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .pickTile(selected, LocalAccent.current, shape, idleBg = PickFace, idleRing = null)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(item.itemName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(won(item.amount), fontSize = 12.sp, color = TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${item.count}회", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalAccentDeep.current, maxLines = 1)
        }
    }
}

/**
 * 재화 한 칸 — [아이콘 · 첫 줄 · 둘째 줄] 아래에 가격 바닥.
 * [tier] 면 첫 줄이 개수(17), 아니면 이름(14 · 한 줄 말줄임). 둘째 줄은 비어도 자리를 남겨 칸 높이를 맞춘다.
 */
@Composable
private fun CurrencyTile(
    item: PackagePickItem,
    tier: Boolean,
    selected: Boolean,
    gameColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val deep = LocalAccentDeep.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .pickTile(selected, LocalAccent.current, shape, idleBg = Color.White, idleRing = PickRing)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            // 칸 안의 글자는 조각나 있다("300" · "추가 +30") — 읽어 줄 때는 상품 이름으로 한 번에 말한다.
            .clearAndSetSemantics {
                contentDescription = listOf(item.pkg.name, item.sub.takeIf { tier }.orEmpty(), item.price, item.pulls.orEmpty())
                    .filter { it.isNotEmpty() }.joinToString(" ")
            }
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PickIconCircle(PickIconGem, gameColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.headline,
                    fontSize = if (tier) 17.sp else 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.height(15.dp), contentAlignment = Alignment.CenterStart) {
                    Text(item.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = deep, maxLines = 1)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) deep else PickFace)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (selected) Icon(PickIconCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(
                item.price, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else TextPrimary, maxLines = 1, modifier = Modifier.weight(1f),
            )
            item.pulls?.let { Text(it, fontSize = 12.sp, color = if (selected) Color.White else TextSecondary, maxLines = 1) }
        }
    }
}

/** 월정액 · 패스 한 줄 — [아이콘 · 이름 / 종류 · 가격]. 고르면 가격 앞에 체크가 서고 가격이 강조색이 된다. */
@Composable
private fun PlanRow(item: PackagePickItem, selected: Boolean, gameColor: Color, onClick: () -> Unit) {
    val deep = LocalAccentDeep.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .pickTile(selected, LocalAccent.current, shape, idleBg = Color.White, idleRing = PickRing)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PickIconCircle(if (item.pkg.kind == PkgCategory.MONTHLY) PickIconCalendar else PickIconTicket, gameColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.headline, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(item.sub, fontSize = 12.sp, color = TextSecondary)
        }
        if (selected) Icon(PickIconCheck, contentDescription = null, tint = deep, modifier = Modifier.size(16.dp))
        Text(item.price, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (selected) deep else TextPrimary, maxLines = 1)
    }
}

/**
 * 고른 상품의 요약 면 — 위 줄에 [무엇을 몇 번 · 재화량] 과 횟수 스텝퍼, 선 아래에 [단가 × 횟수 · 합계].
 * 한 번에 같은 상품을 여러 번 산 경우 횟수만큼 금액 · 재화를 곱해 한 건으로 기록한다.
 */
@Composable
private fun PickSummaryFace(summary: PackagePickSummary, quantity: Int, onChange: (Int) -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (summary.sub.isNotEmpty()) {
                    Text(summary.sub, fontSize = 12.sp, color = PickSummarySub, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepperBtn(PickIconMinus, "횟수 줄이기", enabled = quantity > SpendingEditLogic.MIN_QUANTITY) { onChange(quantity - 1) }
                Text(
                    "$quantity", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 28.dp),
                )
                StepperBtn(PickIconPlus, "횟수 늘리기", enabled = quantity < SpendingEditLogic.MAX_QUANTITY) { onChange(quantity + 1) }
            }
        }
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(accent.copy(alpha = 0.22f)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(summary.unitLine, fontSize = 12.sp, color = PickSummarySub, modifier = Modifier.weight(1f).alignByBaseline())
            Text(summary.total, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

/** 스텝퍼 단추(34 원) — 흰 면 + 강조색 50% 테두리. 못 누르면 반투명 흰 면 + 옅은 테두리 · 흐린 아이콘. */
@Composable
private fun StepperBtn(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.5f))
            .border(1.dp, if (enabled) accent.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.06f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) accent else TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
    }
}

// (27.52.0) 수정 진입 때 항목명에서 구매 횟수 · 상품을 되읽던 detectEditQuantity · detectEditPackage 는
// 공용 SpendingEditLogic.restoreItem 으로 옮겼다 — iOS 가 같은 규칙을 따로 적어 두고 있었다.
