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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgDatePickerDialog
import com.gatcha.log.ui.components.GlgFieldLabel
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

private val SheetBg = Color(0xFFF2F2F7)   // iOS 인셋-그룹 회색 배경
private val CardBg = Color.White
private val ChipIdleBg = Color(0xFFF2F2F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpendingModal(
    spendingToEdit: Spending? = null,
    onDismiss: () -> Unit,
    onSave: (Spending) -> Unit,
) {
    val editing = spendingToEdit != null

    // spendingToEdit 가 바뀌면(수정 진입/대상 변경) 상태를 다시 채운다.
    var game by remember(spendingToEdit) { mutableStateOf(GameData.byName(spendingToEdit?.gameName ?: "원신")) }
    var amount by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var dateMillis by remember(spendingToEdit) { mutableLongStateOf(spendingToEdit?.dateMillis ?: System.currentTimeMillis()) }
    var paymentMethod by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.paymentMethod ?: "신용카드") }
    var itemName by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.itemName ?: "") }
    var memo by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.memo ?: "") }
    var customTags by remember(spendingToEdit) { mutableStateOf("") }
    val selectedTags = remember(spendingToEdit) {
        mutableStateListOf<String>().apply { spendingToEdit?.tags?.let { addAll(it) } }
    }
    var isSubscription by remember(spendingToEdit) { mutableStateOf(spendingToEdit?.isSubscription ?: false) }
    var selectedPackage by remember(spendingToEdit) { mutableStateOf<GamePackage?>(null) }
    val showDatePicker = remember { mutableStateOf(false) }

    fun applyPackage(pkg: GamePackage) {
        selectedPackage = pkg
        amount = pkg.price.toString()
        itemName = pkg.name
        isSubscription = pkg.bonus == "월정액"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).background(Color(0x22000000), RoundedCornerShape(2.dp)))
            }
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
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
                        GlgTextField(
                            value = amount,
                            onValueChange = { input -> amount = input.filter { it.isDigit() } },
                            label = "금액 (원)",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
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

            // Bottom Actions — 시트 하단(내비 영역까지 흰 띠), 버튼은 내비 위로
            Surface(color = CardBg, shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val amountValid = (amount.toLongOrNull() ?: 0L) > 0
                    GlgOutlineButton("취소", onDismiss, Modifier.weight(1f), height = 54.dp)
                    GlgButton(
                        text = if (editing) "수정하기" else "저장하기",
                        onClick = {
                            val parsed = amount.toLongOrNull() ?: 0L
                            val tags = (selectedTags + customTags.split(",", " "))
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinct()
                            val base = spendingToEdit ?: Spending(gameName = game.displayName, amount = parsed)
                            onSave(
                                base.copy(
                                    gameName = game.displayName,
                                    amount = parsed,
                                    dateMillis = dateMillis,
                                    paymentMethod = paymentMethod,
                                    itemName = itemName,
                                    memo = memo,
                                    tags = tags,
                                    isSubscription = isSubscription,
                                    gameColor = game.color,
                                )
                            )
                        },
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
}

/** 흰색 그룹 섹션 카드 (회색 시트 위, 소프트 섀도). */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = Color(0x12000000), spotColor = Color(0x12000000))
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .animateContentSize()
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionRowLabel(text: String) {
    Text(text, fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
}

@Composable
private fun GameSelectItem(game: Game, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) game.color else ChipIdleBg,
        border = if (isSelected) null else BorderStroke(1.dp, DividerColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (isSelected) Color.White else game.color))
            Spacer(Modifier.width(8.dp))
            Text(
                game.shortName,
                fontSize = 13.sp,
                color = if (isSelected) Color.White else TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PackageCard(pkg: GamePackage, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accent.copy(alpha = 0.1f) else ChipIdleBg,
        border = BorderStroke(1.dp, if (isSelected) accent else DividerColor),
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
                Text("₩%,d".format(pkg.price), fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accent else ChipIdleBg,
        border = BorderStroke(1.dp, if (selected) accent else DividerColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, fontSize = 14.sp, color = if (selected) Color.White else TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}
