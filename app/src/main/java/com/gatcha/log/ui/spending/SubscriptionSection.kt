package com.gatcha.log.ui.spending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Subscription
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

@Composable
fun SubscriptionSection(
    subscriptions: List<Subscription>,
    onAdd: (Subscription) -> Unit,
    onUpdate: (Subscription) -> Unit,
    onDelete: (String) -> Unit,
) {
    val accent = LocalAccent.current
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Subscription?>(null) }
    val monthlyTotal = subscriptions.sumOf { it.amount }

    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Autorenew, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("구독 관리", fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable { showAdd = true },
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("추가", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
            }

            if (subscriptions.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("월정액·패스 등 정기결제를 등록하면 월 합계와 다음 결제일을 관리해요.", fontSize = 12.sp, color = TextSecondary)
            } else {
                Spacer(Modifier.height(6.dp))
                Text("월 합계 ₩%,d".format(monthlyTotal), fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                subscriptions.forEachIndexed { i, sub ->
                    SubscriptionRow(sub) { editTarget = sub }
                    if (i < subscriptions.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                    }
                }
            }
        }
    }

    if (showAdd) {
        SubscriptionDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { onAdd(it); showAdd = false },
            onDelete = null,
        )
    }
    editTarget?.let { target ->
        SubscriptionDialog(
            initial = target,
            onDismiss = { editTarget = null },
            onSave = { onUpdate(it); editTarget = null },
            onDelete = { onDelete(target.id); editTarget = null },
        )
    }
}

@Composable
private fun SubscriptionRow(sub: Subscription, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val d = sub.dDay()
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(sub.gameColor))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(sub.name.ifBlank { "구독" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Text("매월 ${sub.billingDay}일 · ${GameData.byName(sub.gameName).shortName}", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("₩%,d".format(sub.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(if (d == 0) "오늘 결제" else "D-$d", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SubscriptionDialog(
    initial: Subscription?,
    onDismiss: () -> Unit,
    onSave: (Subscription) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val accent = LocalAccent.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var game by remember { mutableStateOf(initial?.gameName ?: "원신") }
    var amount by remember { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var day by remember { mutableStateOf(initial?.billingDay?.toString() ?: "1") }

    val valid = name.isNotBlank() && (amount.toLongOrNull() ?: 0L) > 0 && (day.toIntOrNull() ?: 0) in 1..31

    GlgDialog(
        title = if (initial == null) "구독 추가" else "구독 수정",
        onDismiss = onDismiss,
        confirmText = if (initial == null) "추가" else "저장",
        confirmEnabled = valid,
        onConfirm = {
            onSave(
                Subscription(
                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    gameName = game,
                    amount = amount.toLongOrNull() ?: 0L,
                    billingDay = (day.toIntOrNull() ?: 1).coerceIn(1, 31),
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlgTextField(name, { name = it }, label = "이름", placeholder = "공월의 축복", modifier = Modifier.fillMaxWidth())
            Text("게임", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameData.games.forEach { g ->
                    val sel = g.displayName == game
                    Surface(
                        modifier = Modifier.clickable { game = g.displayName },
                        shape = RoundedCornerShape(20.dp),
                        color = if (sel) g.color else Color.White,
                        border = BorderStroke(1.dp, if (sel) g.color else DividerColor),
                    ) {
                        Text(g.shortName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else g.color)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    GlgTextField(amount, { amount = it.filter(Char::isDigit) }, label = "월 금액(원)", placeholder = "4900", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Box(Modifier.weight(1f)) {
                    GlgTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = "결제일(1~31)", placeholder = "1", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
            if (onDelete != null) {
                Text(
                    "이 구독 삭제",
                    fontSize = 13.sp, color = DangerText, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDelete() }.padding(top = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
}
