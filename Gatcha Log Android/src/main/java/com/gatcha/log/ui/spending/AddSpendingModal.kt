package com.gatcha.log.ui.spending

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import com.gatcha.log.ui.components.GlassCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GamePackage
import com.gatcha.log.data.PkgCategory
import com.gatcha.log.data.category
import com.gatcha.log.data.FrequentItem
import com.gatcha.log.data.SpendingDefaults
import com.gatcha.log.data.currencyAmountOrNull
import com.gatcha.log.data.currencyPullsOrNull
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgChipVariant
import com.gatcha.log.ui.components.GlgDatePickerDialog
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgFieldLabel
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.GlgCardReveal
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.glgShortSpec
import com.gatcha.log.ui.theme.glgStandardSpec
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

private val SheetBg = Color.White   // D · 모달 배경 흰색(연회색 카드와 대비)
private val CardBg = Color.White
private val ChipIdleBg = Color(0xFFF2F2F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpendingModal(
    spendingToEdit: Spending? = null,
    /** 스마트 기본값·'자주 사는 것'의 근거. 비어 있으면 추론하지 않는다. */
    recentSpendings: List<Spending> = emptyList(),
    nudgeMessage: (game: Game, amount: Long) -> String? = { _, _ -> null },
    onDismiss: () -> Unit,
    onSave: (Spending) -> Unit,
) {
    val editing = spendingToEdit != null

    // spendingToEdit 가 바뀌면(수정 진입/대상 변경) 상태를 다시 채운다.
    //
    // 추가 진입이면 **앱이 아는 값으로 시작**한다(추론은 공유 SpendingDefaults 가 한다).
    // 기록이 없으면 추론하지 않고 예전 기본값(원신·카드·빈칸) 그대로 간다.
    var game by remember(spendingToEdit) {
        mutableStateOf(
            // 미선택 상태의 표시는 gameChosen 이 맡는다. 이 값은 고르기 전까지 쓰이지 않는다.
            GameData.byName(spendingToEdit?.gameName ?: "원신"),
        )
    }
    var amount by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var dateMillis by remember(spendingToEdit) { mutableLongStateOf(spendingToEdit?.dateMillis ?: System.currentTimeMillis()) }
    var paymentMethod by remember(spendingToEdit) {
        mutableStateOf(
            spendingToEdit?.paymentMethod
                ?: SpendingDefaults.topPaymentMethod(recentSpendings)
                ?: "카드",
        )
    }
    var chargePlatform by remember(spendingToEdit) {
        // 게임이 정해져야 고를 수 있어 초기엔 비운다 — 게임 선택 시 onGameChange 에서 채운다.
        mutableStateOf(spendingToEdit?.chargePlatform ?: "")
    }
    var itemName by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.itemName ?: "") }
    var memo by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.memo ?: "") }
    var customTags by remember(spendingToEdit) { mutableStateOf("") }
    val selectedTags = remember(spendingToEdit) {
        mutableStateListOf<String>().apply { spendingToEdit?.tags?.let { addAll(it) } }
    }
    var isSubscription by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.isSubscription ?: false) }
    // 수정 진입 시 저장된 항목명("창세의 결정 300 ×3")에서 상품·구매 횟수를 복원 → 스텝퍼가 그대로 노출.
    var selectedPackage by remember(spendingToEdit) { mutableStateOf(detectEditPackage(spendingToEdit)) }
    // 한 번에 같은 상품을 여러 번 산 경우 — 횟수만큼 금액·재화를 곱해 한 건으로 기록.
    var quantity by remember(spendingToEdit) { mutableIntStateOf(detectEditQuantity(spendingToEdit)) }
    val showDatePicker = remember { mutableStateOf(false) }
    // N6 과소비 넛지 — 저장 직전 경고 메시지(있으면 확인 다이얼로그 노출 후 그래도 추가 시 저장).
    var nudgeMsg by remember { mutableStateOf<String?>(null) }
    /// '자주 사는 것' 아래 전체 상품 그리드를 폈는가.
    var showAllPackages by remember(game) { mutableStateOf(false) }
    // '자세히'(결제·플랫폼·태그·메모·구독) — **수정 진입은 펼친 채 시작**한다(무엇을 고치러 왔는지 모른다).
    var detailsExpanded by remember(spendingToEdit) { mutableStateOf(editing) }

    // 사용자가 게임을 **직접 골랐는가.**
    //
    // 추가 진입은 게임을 미리 정해두지 않는다. 마지막에 기록한 게임을 자동으로 넣으면
    // 다른 게임을 기록하러 온 사람이 **못 알아채고 엉뚱한 게임에 저장**한다 —
    // 지출은 게임별 집계·예산의 기준이라 그 오기록은 나중에 찾기 어렵다.
    // 결제수단·플랫폼과 달리 게임은 "틀려도 티가 안 나는" 값이 아니다. 수정 진입은 이미 정해져 있다.
    var gameChosen by remember(spendingToEdit) { mutableStateOf(editing) }

    // 히어로에 바로 뜨는 과소비 경고 — 저장을 누른 뒤가 아니라 금액이 정해지는 순간에 알린다.
    // 입력 도중 매 글자마다 뜨면 방해가 되므로 손이 멈춘 뒤에만 평가한다.
    var inlineNudge by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(amount, game) {
        kotlinx.coroutines.delay(450)
        inlineNudge = nudgeMessage(game, amount.toLongOrNull() ?: 0L)
    }

    // 같은 게임에서 2회 이상 산 것만, 많이 산 순. 수정 진입에는 쓰지 않는다.
    val frequent = remember(recentSpendings, game, editing) {
        if (editing) emptyList() else SpendingDefaults.frequentItems(recentSpendings, game.displayName, limit = 3)
    }

    fun applyPackage(pkg: GamePackage) {
        selectedPackage = pkg
        quantity = 1
        amount = pkg.price.toString()
        itemName = pkg.name
        isSubscription = pkg.bonus == "월정액"
    }

    // 구매 횟수 변경 — 선택된 상품 기준으로 금액·재화명을 N배로 다시 계산.
    fun setQuantity(q: Int) {
        val qty = q.coerceIn(1, 99)
        quantity = qty
        selectedPackage?.let { pkg ->
            amount = (pkg.price * qty).toString()
            itemName = if (qty > 1) "${pkg.name} ×$qty" else pkg.name
        }
    }

    fun buildSpending(): Spending {
        val parsed = amount.toLongOrNull() ?: 0L
        val tags = (selectedTags + customTags.split(",", " "))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val base = spendingToEdit ?: Spending(gameName = game.displayName, amount = parsed)
        return base.copy(
            gameName = game.displayName,
            amount = parsed,
            dateMillis = dateMillis,
            paymentMethod = paymentMethod,
            chargePlatform = chargePlatform,
            itemName = itemName,
            memo = memo,
            tags = tags,
            isSubscription = isSubscription,
            gameColor = game.color,
        )
    }

    // 저장 시도 — 넛지 메시지가 있으면 확인 다이얼로그를 띄우고, 없으면 즉시 저장.
    fun attemptSave() {
        val parsed = amount.toLongOrNull() ?: 0L
        val msg = nudgeMessage(game, parsed)
        if (msg != null) nudgeMsg = msg else onSave(buildSpending())
    }

    // 시스템 뒤로가기 처리(풀스크린 페이지처럼 동작) — 시트 외부 dismiss 가 사라졌으므로 명시.
    androidx.activity.compose.BackHandler { onDismiss() }

    Surface(
        color = SheetBg,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 헤더는 앱 표준 상세 헤더(GlgDetailHeaderOverlay)로 통일한다 —
        // 예전엔 이 화면만 22sp 제목 + 우측 원형 X 라는 자기만의 머리를 갖고 있어,
        // 설정·저축 플래너·정기결제 같은 다른 하위 페이지와 생김새도 뒤로가기 위치도 달랐다.
        Box(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        val scrolled by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = glgDetailContentTop(), bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── 금액 히어로 — 게임·금액·상품·재화 환산을 한 덩어리로 ──
                //
                // 금액은 지출에서 가장 중요한 값인데 예전엔 '빠른 상품' 카드 안쪽, 그리드 아래
                // 일반 필드로 있었다. 목록·상세·인사이트는 전부 금액이 히어로인데 입력할 때만 아니었다.
                item {
                    AmountHero(
                        game = game,
                        amount = amount,
                        onAmountChange = { input ->
                            amount = input.filter { it.isDigit() }
                            // 금액을 직접 손대면 자동 곱 상태를 해제(스텝퍼 계산과 어긋나지 않게).
                            selectedPackage = null
                            quantity = 1
                        },
                        gameChosen = gameChosen,
                        onGameChange = { g ->
                            game = g
                            gameChosen = true
                            selectedPackage = null
                            quantity = 1
                            itemName = ""
                            isSubscription = false
                            if (!editing) chargePlatform = SpendingDefaults.lastPlatform(recentSpendings, g.displayName) ?: ""
                        },
                        itemName = itemName,
                        isSubscription = isSubscription,
                        nudge = inlineNudge,
                    )
                }

                // 게임을 고르기 전에는 나머지를 띄우지 않는다 — 상품 목록·기본값이 전부 게임에 묶여 있어
                // 미선택 상태로 보여주면 어느 게임 것인지 알 수 없는 화면이 된다.
                // 게임을 고르면 아래 카드가 **위에서부터 한 장씩** 밀려 내려온다(GlgCardReveal).
                // 예전엔 `if (!gameChosen) return@LazyColumn` 으로 통째로 잘라내 세 장이 한꺼번에
                // 튀어나왔고, 방금 누른 칩과 새로 생긴 입력란이 이어져 있다는 게 안 읽혔다.

                // ── 상품 ──
                item {
                    GlgCardReveal(visible = gameChosen, order = 0) {
                    SectionCard {
                        // 자주 사는 것 — 기록이 없는 게임은 이 블록이 통째로 빠지고 전체 그리드가 바로 열린다
                        // (빈도를 모르는데 임의로 셋을 고르면 그건 추천이 아니다).
                        if (frequent.isNotEmpty()) {
                            SectionRowLabel("자주 사는 것")
                            Spacer(Modifier.height(10.dp))
                            frequent.forEach { f ->
                                FrequentItemRow(
                                    item = f,
                                    selected = itemName == f.itemName,
                                ) {
                                    selectedPackage = GameData.packagesFor(game).firstOrNull { p -> p.name == f.itemName }
                                    quantity = 1
                                    itemName = f.itemName
                                    amount = f.amount.toString()
                                    selectedPackage?.let { isSubscription = it.bonus == "월정액" }
                                }
                                Spacer(Modifier.height(7.dp))
                            }
                            Text(
                                if (showAllPackages) "접기 ⌃" else "전체 상품 보기 ▾",
                                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showAllPackages = !showAllPackages }
                                    .padding(vertical = 8.dp),
                            )
                        } else {
                            SectionRowLabel("빠른 상품 선택")
                            Text("선택하면 금액·재화명이 자동 입력돼요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        }
                        // '자주 사는 것'이 없는 게임은 그리드가 처음부터 열려 있어 전환이 없다.
                        AnimatedVisibility(
                            visible = frequent.isEmpty() || showAllPackages,
                            enter = expandVertically(glgStandardSpec()) + fadeIn(glgStandardSpec()),
                            exit = shrinkVertically(glgShortSpec()) + fadeOut(glgShortSpec()),
                        ) {
                        Column {
                        val allPackages = GameData.packagesFor(game)
                        // 카테고리 칩(월정액/패스/재화) — 게임이 가진 분류만 노출, 게임 바뀌면 전체로 리셋
                        var pkgFilter by remember(game) { mutableStateOf(PkgCategory.ALL) }
                        val pkgCategories = listOf(PkgCategory.ALL) +
                            listOf(PkgCategory.MONTHLY, PkgCategory.PASS, PkgCategory.CURRENCY)
                                .filter { c -> allPackages.any { it.category == c } }
                        if (pkgCategories.size > 2) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 10.dp),
                            ) {
                                items(pkgCategories) { cat ->
                                    ChoiceChip(label = cat.label, selected = pkgFilter == cat) { pkgFilter = cat }
                                }
                            }
                        }
                        val packages = if (pkgFilter == PkgCategory.ALL) allPackages
                            else allPackages.filter { it.category == pkgFilter }
                        // 2열 — 카드를 넓혀 긴 상품명도 한 줄에 들어가고 가격이 안 잘리게
                        val cols = 2
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            packages.chunked(cols).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowItems.forEach { pkg ->
                                        PackageCard(
                                            pkg = pkg,
                                            isSelected = selectedPackage == pkg,
                                            modifier = Modifier.weight(1f),
                                        ) { applyPackage(pkg) }
                                    }
                                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        }
                        }
                        // 구매 횟수 — 상품을 골랐을 때만. 한 번에 여러 번 산 경우 금액·재화를 곱한다.
                        selectedPackage?.let { pkg ->
                            Spacer(Modifier.height(14.dp))
                            QuantityStepper(
                                quantity = quantity,
                                unitPrice = pkg.price,
                                currencyTotal = currencyAmountOrNull(game.displayName, itemName),
                                onChange = { setQuantity(it) },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        GlgTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = "재화명",
                            placeholder = "결정석 60",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // 환산이 깨지면 조용히 넘어가지 않고 이유를 말한다(막지는 않는다 — 환산은 편의다).
                        if (itemName.isNotBlank() && currencyAmountOrNull(game.displayName, itemName) == null) {
                            Text(
                                "재화 환산이 안 돼요 — '결정석 60'처럼 이름 뒤에 숫자를 붙이면 뽑기 수까지 계산해요",
                                fontSize = 10.5.sp, color = TextSecondary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    }
                }

                // ── 날짜 ──
                item {
                    GlgCardReveal(visible = gameChosen, order = 1) {
                    SectionCard {
                        GlgTextField(
                            value = DateUtil.labelWithWeekday(dateMillis),
                            onValueChange = {},
                            label = "날짜",
                            readOnly = true,
                            trailingIcon = Icons.Default.CalendarToday,
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
                    GlgCardReveal(visible = gameChosen, order = 2) {
                    SectionCard {
                        val tagCount = selectedTags.size + customTags.split(",", " ").count { it.isNotBlank() }
                        val custom = chargePlatform.isNotBlank() || selectedTags.isNotEmpty() ||
                            customTags.isNotBlank() || memo.isNotBlank() || isSubscription
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { detailsExpanded = !detailsExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("자세히", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    buildList {
                                        add(paymentMethod.ifBlank { "카드" })
                                        if (chargePlatform.isNotBlank()) add(chargePlatform)
                                        add(if (tagCount > 0) "태그 $tagCount" else "태그 없음")
                                        if (isSubscription) add("정기")
                                        if (memo.isNotBlank()) add("메모")
                                    }.joinToString(" · "),
                                    fontSize = 11.5.sp, maxLines = 1,
                                    // 기본값과 다른 값이 있으면 강조 — "뭔가 정해져 있다"가 보이게.
                                    color = if (custom) LocalAccent.current else TextSecondary,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            val arrow by animateFloatAsState(if (detailsExpanded) 180f else 0f, glgStandardSpec(), label = "detailsArrow")
                            Text("▾", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.rotate(arrow))
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
                        GlgTextField(
                            value = customTags,
                            onValueChange = { customTags = it },
                            placeholder = "직접 입력 (쉼표로 구분)",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        GlgTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            label = "메모",
                            placeholder = "이벤트 구입",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("구독(월정액·패스)으로 기록", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("정기 결제 항목으로 분류됩니다", fontSize = 12.sp, color = TextSecondary)
                            }
                            GlgSwitch(checked = isSubscription, onCheckedChange = { isSubscription = it })
                        }
                        }
                        }
                    }
                    }
                }
            }

            // Bottom Actions — 하단(내비 영역까지 흰 띠), 버튼은 내비 위로. 그림자 없는 플랫.
            Surface(color = CardBg, shadowElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val amountValid = (amount.toLongOrNull() ?: 0L) > 0
                    val canSave = gameChosen && amountValid
                    GlgOutlineButton("취소", onDismiss, Modifier.weight(1f), height = 54.dp)
                    GlgButton(
                        // 흐린 버튼만 두지 않는다 — 왜 못 누르는지 버튼이 직접 말한다.
                        text = when {
                            !gameChosen -> "게임을 선택하세요"
                            !amountValid -> "금액을 입력하세요"
                            editing -> "수정하기"
                            else -> "저장하기"
                        },
                        onClick = { attemptSave() },
                        modifier = Modifier.weight(1.5f),
                        enabled = canSave,
                        height = 54.dp,
                    )
                }
            }
        }
        // 오버레이는 **마지막에** — 콘텐츠 위에 그려져야 한다.
        GlgDetailHeaderOverlay(if (editing) "지출 수정" else "지출 추가", onDismiss, scrolled)
        }
    }

    if (showDatePicker.value) {
        GlgDatePickerDialog(
            initialMillis = dateMillis,
            onDismiss = { showDatePicker.value = false },
            onConfirm = { dateMillis = it; showDatePicker.value = false },
        )
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
                onSave(s)
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
    game: Game,
    amount: String,
    onAmountChange: (String) -> Unit,
    onGameChange: (Game) -> Unit,
    gameChosen: Boolean,
    itemName: String,
    isSubscription: Boolean,
    nudge: String?,
) {
    val gameColor = if (gameChosen) game.color.toColor() else TextSecondary
    val accent = LocalAccent.current
    var pickGame by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gameColor.copy(alpha = 0.07f))
            .border(1.dp, gameColor.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        if (!gameChosen) {
            // 미선택 — 고르기 전에는 금액을 받지 않는다. 첫 할 일이 무엇인지 화면이 말한다.
            Text("어느 게임인가요?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("게임을 선택해주세요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.height(12.dp))
            // 가로 스크롤이 아니라 **줄바꿈**이다 — 첫 할 일이 "게임 고르기"인데 스크롤로 접어 두면
            // 화면 밖 게임은 있는 줄도 모른다(고를 수 있는 게 몇 개인지조차 안 보인다).
            // 카드 폭 안에서 전부 한눈에 들어와야 고르는 화면 구실을 한다. (iOS FlowLayout 과 파리티)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GameData.games.forEach { g ->
                    // 게임 칩은 **게임 대표색**을 쓴다 — ChoiceChip 은 색을 넘기지 않아
                    // 전부 강조색(민트)으로 떨어진다. 게임 구분이 색으로 읽혀야 한다.
                    GameSelectItem(game = g, isSelected = false) { onGameChange(g) }
                }
            }
            return@Column
        }
        // 게임 — 칩 줄을 늘어놓지 않고 접었다(히어로가 금액을 가리면 안 된다).
        Row(
            Modifier.clip(CircleShape)
                .background(gameColor.copy(alpha = 0.12f))
                .clickable { pickGame = !pickGame }
                .padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(gameColor))
            Spacer(Modifier.width(6.dp))
            Text(game.shortName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = gameColor)
            Spacer(Modifier.width(4.dp))
            // 화살표는 뒤집지 않고 **돌린다** — 글자를 바꾸면 순간이동처럼 보인다.
            val arrow by animateFloatAsState(if (pickGame) 180f else 0f, glgStandardSpec(), label = "gamePickArrow")
            Text(
                "▾", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = gameColor,
                modifier = Modifier.rotate(arrow),
            )
        }
        // 칩 줄은 위에서 아래로 펼쳐지고 같은 길로 접힌다 — 갑자기 나타나면 아래 금액이 튄다.
        AnimatedVisibility(
            visible = pickGame,
            enter = expandVertically(glgStandardSpec()) + fadeIn(glgStandardSpec()),
            exit = shrinkVertically(glgShortSpec()) + fadeOut(glgShortSpec()),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GameData.games) { g ->
                        GameSelectItem(game = g, isSelected = g == game) { onGameChange(g); pickGame = false }
                    }
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        BasicTextField(
            value = amount,
            onValueChange = onAmountChange,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 34.sp, fontWeight = FontWeight.Black, color = TextPrimary,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (amount.isEmpty()) {
                    Text("0", fontSize = 34.sp, fontWeight = FontWeight.Black, color = TextSecondary.copy(alpha = 0.35f))
                }
                inner()
            },
        )
        if (itemName.isNotBlank()) {
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(itemName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                // 월정액/패스를 고르면 구독이 **자동으로 켜진다**. 그 상태가 접힌 '자세히' 안에만
                // 있으면 켜진 줄 모르므로 히어로가 대신 말한다.
                if (isSubscription) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "정기", fontSize = 10.sp, fontWeight = FontWeight.Black, color = accent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        // 재화 환산은 사는 순간에 보여야 의미가 있다.
        val cur = currencyAmountOrNull(game.displayName, itemName)
        if (cur != null) {
            val pulls = currencyPullsOrNull(game.displayName, itemName)
            Text(
                if (pulls != null) "$cur · $pulls" else cur,
                fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (nudge != null) {
            Text(
                "⚠ $nudge",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 자주 사는 것 한 줄 — 누르면 상품·금액·재화명이 한 번에 채워진다. */
@Composable
private fun FrequentItemRow(item: FrequentItem, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.White)
            .border(1.dp, if (selected) accent else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.itemName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text(won(item.amount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.width(7.dp))
        Text(
            "${item.count}회", fontSize = 10.sp, fontWeight = FontWeight.Black, color = accent,
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.13f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** 그룹 섹션 카드 — 앱 표준 [GlassCard](흰 배경·아웃라인·평면, 22dp)와 동일하게 통일. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionRowLabel(text: String) {
    Text(text, fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
}

@Composable
private fun GameSelectItem(game: Game, isSelected: Boolean, onClick: () -> Unit) {
    GlgChip(label = game.shortName, selected = isSelected, color = game.color.toColor(), onClick = onClick)
}

@Composable
private fun PackageCard(pkg: GamePackage, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accent.copy(alpha = 0.1f) else Color.White,
        border = BorderStroke(1.dp, if (isSelected) accent else Color.Black.copy(alpha = 0.08f)),
    ) {
        // 컴팩트·깔끔 — 내용에 딱 맞는 높이(2열이라 이름 1줄, 모든 카드 구조 동일 → 자동 통일)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                pkg.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                pkg.bonus?.let {
                    Text(it, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                }
                Text(won(pkg.price), fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    GlgChip(label = label, selected = selected, onClick = onClick)
}

/** 구매 횟수 스텝퍼 — 단가·재화 총량을 함께 보여줘 '몇 번 사서 얼마·재화 얼마인지' 검증 가능하게. */
@Composable
private fun QuantityStepper(quantity: Int, unitPrice: Long, currencyTotal: String?, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SectionRowLabel("구매 횟수")
            if (quantity > 1) {
                Text(
                    "${won(unitPrice)} × $quantity = ${won(unitPrice * quantity)}",
                    fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // 재화 양도 확인 — 상품명 끝의 개수를 N배해 총 재화량을 명시.
                currencyTotal?.let {
                    Text("재화 총 $it", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 1.dp))
                }
            } else {
                Text("한 번에 여러 번 샀다면 횟수를 올리세요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepperBtn(Icons.Default.Remove, enabled = quantity > 1) { onChange(quantity - 1) }
            Text(
                "$quantity", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 28.dp),
            )
            StepperBtn(Icons.Default.Add, enabled = quantity < 99) { onChange(quantity + 1) }
        }
    }
}

@Composable
private fun StepperBtn(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        modifier = Modifier.size(34.dp).clickable(enabled = enabled) { onClick() },
        shape = CircleShape,
        color = if (enabled) accent.copy(alpha = 0.10f) else ChipIdleBg,
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.06f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (enabled) accent else TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}

/** 수정 진입 시 항목명 끝의 "×N" 에서 구매 횟수 복원. 없으면 1. */
private fun detectEditQuantity(s: Spending?): Int {
    val name = s?.itemName?.trim() ?: return 1
    val m = Regex("×\\s*(\\d+)\\s*$").find(name) ?: return 1
    return m.groupValues[1].toIntOrNull()?.coerceIn(1, 99) ?: 1
}

/** 수정 진입 시 항목명(×N 제거)이 해당 게임의 상품과 일치하면 그 상품을 복원 → 스텝퍼 노출. */
private fun detectEditPackage(s: Spending?): GamePackage? {
    val name = s?.itemName?.trim() ?: return null
    val base = name.replace(Regex("\\s*×\\s*\\d+\\s*$"), "").trim()
    return GameData.packagesFor(GameData.byName(s.gameName)).firstOrNull { it.name == base }
}

