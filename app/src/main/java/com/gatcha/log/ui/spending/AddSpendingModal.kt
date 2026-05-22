package com.gatcha.log.ui.spending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GamePackage
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgDatePickerDialog
import com.gatcha.log.ui.components.GlgFieldLabel
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpendingModal(
    spendingToEdit: Spending? = null,
    onDismiss: () -> Unit,
    onSave: (Spending) -> Unit,
) {
    val accent = LocalAccent.current
    val editing = spendingToEdit != null

    var game by remember { mutableStateOf(GameData.byName(spendingToEdit?.gameName ?: "원신")) }
    var amount by remember { mutableStateOf(spendingToEdit?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var dateMillis by remember { mutableLongStateOf(spendingToEdit?.dateMillis ?: System.currentTimeMillis()) }
    var paymentMethod by remember { mutableStateOf(spendingToEdit?.paymentMethod ?: "신용카드") }
    var itemName by remember { mutableStateOf(spendingToEdit?.itemName ?: "") }
    var memo by remember { mutableStateOf(spendingToEdit?.memo ?: "") }
    var customTags by remember { mutableStateOf("") }
    val selectedTags = remember {
        mutableStateListOf<String>().apply { spendingToEdit?.tags?.let { addAll(it) } }
    }
    var isSubscription by remember { mutableStateOf(spendingToEdit?.isSubscription ?: false) }
    var selectedPackage by remember { mutableStateOf<GamePackage?>(null) }
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
        containerColor = Color.White,
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
                .fillMaxHeight(0.92f)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (editing) "지출 수정" else "지출 추가", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).background(Color(0xFFF5F5F5), CircleShape),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", modifier = Modifier.size(16.dp))
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
            ) {
                // 게임 선택
                item {
                    SectionLabel("게임 선택")
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GameData.games) { g ->
                            GameSelectItem(
                                game = g,
                                isSelected = game == g,
                            ) {
                                game = g
                                selectedPackage = null
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // 상품 선택
                item {
                    SectionLabel("상품 선택")
                    Text(
                        "패키지를 선택하면 금액·재화명이 자동 입력돼요",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    GameData.packagesFor(game).chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems.forEach { pkg ->
                                Box(Modifier.weight(1f)) {
                                    PackageCard(
                                        pkg = pkg,
                                        isSelected = selectedPackage == pkg,
                                        accent = accent,
                                        onClick = { applyPackage(pkg) },
                                    )
                                }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // 날짜 + 금액
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlgTextField(
                            value = DateUtil.labelWithWeekday(dateMillis),
                            onValueChange = {},
                            label = "날짜",
                            readOnly = true,
                            trailingIcon = Icons.Default.CalendarToday,
                            onClick = { showDatePicker.value = true },
                            modifier = Modifier.weight(1.3f),
                        )
                        GlgTextField(
                            value = amount,
                            onValueChange = { input -> amount = input.filter { it.isDigit() } },
                            label = "금액 (원)",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 결제 수단
                item {
                    GlgFieldLabel("결제 수단")
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GameData.paymentMethods) { method ->
                            ChoiceChip(
                                label = method,
                                selected = paymentMethod == method,
                                accent = accent,
                                onClick = { paymentMethod = method },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 재화명 + 메모
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlgTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = "재화명",
                            placeholder = "결정석 60",
                            modifier = Modifier.weight(1f),
                        )
                        GlgTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            label = "메모",
                            placeholder = "이벤트 구입",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 태그
                item {
                    GlgFieldLabel("태그")
                    Spacer(Modifier.height(2.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GameData.suggestedTags) { tag ->
                            ChoiceChip(
                                label = tag,
                                selected = tag in selectedTags,
                                accent = accent,
                                onClick = {
                                    if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GlgTextField(
                        value = customTags,
                        onValueChange = { customTags = it },
                        placeholder = "직접 입력 (쉼표로 구분)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 구독 토글
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("구독(월정액·패스)으로 기록", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("정기 결제 항목으로 분류됩니다", fontSize = 11.sp, color = TextSecondary)
                        }
                        GlgSwitch(checked = isSubscription, onCheckedChange = { isSubscription = it })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Bottom Actions
            Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
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

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
}

@Composable
private fun GameSelectItem(game: Game, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) game.color else Color.Transparent,
        border = if (isSelected) null else BorderStroke(1.dp, game.color.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(if (isSelected) Color.White else game.color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                game.shortName,
                fontSize = 12.sp,
                color = if (isSelected) Color.White else game.color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PackageCard(pkg: GamePackage, isSelected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accent.copy(alpha = 0.1f) else Color.White,
        border = BorderStroke(1.dp, if (isSelected) accent else DividerColor),
    ) {
        Column(
            modifier = Modifier.padding(8.dp).heightIn(min = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(pkg.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            pkg.bonus?.let { Text(it, fontSize = 9.sp, color = accent, fontWeight = FontWeight.Bold) }
            Text("₩%,d".format(pkg.price), fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accent else Color.White,
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
            Text(label, fontSize = 12.sp, color = if (selected) Color.White else Color.DarkGray, fontWeight = FontWeight.Medium)
        }
    }
}