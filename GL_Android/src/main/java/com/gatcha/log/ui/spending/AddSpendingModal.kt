package com.gatcha.log.ui.spending

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.gatcha.log.data.currencyAmountOrNull
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgChipVariant
import com.gatcha.log.ui.components.GlgDatePickerDialog
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgFieldLabel
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DividerColor
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
    nudgeMessage: (game: Game, amount: Long) -> String? = { _, _ -> null },
    onDismiss: () -> Unit,
    onSave: (Spending) -> Unit,
) {
    val editing = spendingToEdit != null

    // spendingToEdit 가 바뀌면(수정 진입/대상 변경) 상태를 다시 채운다.
    var game by remember(spendingToEdit) { mutableStateOf(GameData.byName(spendingToEdit?.gameName ?: "원신")) }
    var amount by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var dateMillis by remember(spendingToEdit) { mutableLongStateOf(spendingToEdit?.dateMillis ?: System.currentTimeMillis()) }
    var paymentMethod by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.paymentMethod ?: "카드") }
    var chargePlatform by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.chargePlatform ?: "") }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (editing) "지출 수정" else "지출 추가", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE6E6EB)).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", modifier = Modifier.size(16.dp), tint = TextSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── 게임 (항상 노출) ──
                item {
                    SectionCard {
                        SectionRowLabel("게임")
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GameData.games) { g ->
                                GameSelectItem(game = g, isSelected = game == g) {
                                    game = g
                                    selectedPackage = null
                                }
                            }
                        }
                    }
                }

                // ── 금액 + 상품 ──
                item {
                    SectionCard {
                        SectionRowLabel("빠른 상품 선택")
                        Text("선택하면 금액·재화명이 자동 입력돼요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
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
                        Spacer(Modifier.height(14.dp))
                        GlgTextField(
                            value = amount,
                            onValueChange = { input ->
                                amount = input.filter { it.isDigit() }
                                // 금액을 직접 손대면 자동 곱 상태를 해제(스텝퍼 계산과 어긋나지 않게).
                                selectedPackage = null
                                quantity = 1
                            },
                            label = "금액 (원)",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        GlgTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = "재화명",
                            placeholder = "결정석 60",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ── 날짜 + 결제수단 ──
                item {
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
                    }
                }

                // ── 태그 + 메모 + 구독 ──
                item {
                    SectionCard {
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

            // Bottom Actions — 시트 하단(내비 영역까지 흰 띠), 버튼은 내비 위로. 그림자 없는 플랫.
            Surface(color = CardBg, shadowElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val amountValid = (amount.toLongOrNull() ?: 0L) > 0
                    GlgOutlineButton("취소", onDismiss, Modifier.weight(1f), height = 54.dp)
                    GlgButton(
                        text = if (editing) "수정하기" else "저장하기",
                        onClick = { attemptSave() },
                        modifier = Modifier.weight(1.5f),
                        enabled = amountValid,
                        height = 54.dp,
                    )
                }
            }
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
                Text(msg, fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp)
            }
        }
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

