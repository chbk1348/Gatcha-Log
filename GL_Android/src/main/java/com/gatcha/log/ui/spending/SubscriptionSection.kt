package com.gatcha.log.ui.spending

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

private val DueAmber = Color(0xFFF59E0B)

/**
 * 정기결제 관리 센터 — 지출 인사이트 "정기결제 요약" 카드의 [관리] 진입점.
 * 목업(design_subscription_center_mockup.html) 구성: 히어로(월 합계·구독 수·다음 갱신) ·
 * 다가오는 갱신 리스트(dDay 오름차순) · 갱신일 알림 토글 · 게임별 월 합계 미니바.
 * 추가/수정/삭제는 기존 SubscriptionDialog 재사용.
 */
@Composable
fun SubscriptionCenterScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val subscriptions by viewModel.subscriptions.collectAsState()
    val spendings by viewModel.spendings.collectAsState()
    val notifySub by viewModel.notifySubscription.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Subscription?>(null) }

    val importableCount = remember(subscriptions, spendings) { viewModel.unlinkedSubscriptionSpendingCount() }
    val sorted = remember(subscriptions) { subscriptions.sortedBy { it.dDay() } }
    val monthlyTotal = subscriptions.sumOf { it.amount }
    val next = sorted.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        GlgScreenHeader("정기결제 관리", onBack, Modifier.padding(horizontal = 16.dp))
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ① 히어로 — 월 합계 + 구독 수 + 다음 갱신
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("월 정기결제 합계", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(won(monthlyTotal), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("/ 월", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroPill("구독 수", "${subscriptions.size}건", Modifier.weight(1f))
                        if (next != null) {
                            val d = next.dDay()
                            HeroPill(
                                "다음 갱신",
                                "${if (d == 0) "오늘" else "D-$d"} · ${next.name.ifBlank { "구독" }}",
                                Modifier.weight(1f),
                                highlight = true,
                            )
                        } else {
                            HeroPill("다음 갱신", "—", Modifier.weight(1f))
                        }
                    }
                }
            }

            // ①-b 지출에서 가져오기 — '구독 표시' 지출 중 아직 정기결제로 등록 안 된 건이 있을 때만
            if (importableCount > 0) {
                ImportFromSpendingBanner(importableCount, accent) {
                    viewModel.importSubscriptionsFromSpendings()
                }
            }

            // ② 다가오는 갱신 리스트
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).animateContentSize()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("다가오는 갱신", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        Surface(
                            color = accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable { showAdd = true },
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("추가", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                            }
                        }
                    }
                    if (sorted.isEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("월정액·패스 등 정기결제를 등록하면 월 합계와 다음 결제일을 관리해요.", fontSize = 12.sp, color = TextSecondary)
                    } else {
                        Spacer(Modifier.height(4.dp))
                        sorted.forEachIndexed { i, sub ->
                            UpcomingRow(sub, accent) { editTarget = sub }
                            if (i < sorted.lastIndex) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                            }
                        }
                    }
                }
            }

            // ③ 갱신일 알림 토글 (notifySubscription)
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("갱신일 알림", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("결제 하루 전(D-1) 푸시로 안내", fontSize = 11.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlgSwitch(notifySub) { viewModel.setNotifySubscription(it) }
                }
            }

            // ④ 게임별 월 합계 미니바
            if (subscriptions.isNotEmpty()) {
                val byGame = remember(subscriptions) {
                    subscriptions.groupBy { it.gameName }
                        .map { (g, list) -> Triple(g, list.sumOf { it.amount }, list.first().gameColor) }
                        .sortedByDescending { it.second }
                }
                GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("게임별 월 합계", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        byGame.forEach { (game, amt, color) ->
                            val frac = if (monthlyTotal > 0) amt.toFloat() / monthlyTotal else 0f
                            Column(Modifier.padding(top = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(GameData.byName(game).shortName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Spacer(Modifier.weight(1f))
                                    Text(won(amt), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${(frac * 100).toInt()}%", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.widthIn(min = 34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                                Spacer(Modifier.height(5.dp))
                                Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(ProgressEmpty)) {
                                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color.toColor()))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAdd) {
        SubscriptionDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { viewModel.addSubscription(it); showAdd = false },
            onDelete = null,
        )
    }
    editTarget?.let { target ->
        SubscriptionDialog(
            initial = target,
            onDismiss = { editTarget = null },
            onSave = { viewModel.updateSubscription(it); editTarget = null },
            onDelete = { viewModel.deleteSubscription(target.id); editTarget = null },
        )
    }
}

@Composable
private fun HeroPill(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val bg = if (highlight) DueAmber.copy(alpha = 0.12f) else Color(0xFFF6F7F9)
    val border = if (highlight) DueAmber.copy(alpha = 0.35f) else DividerColor
    val valueColor = if (highlight) DueAmber else TextPrimary
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = bg, border = BorderStroke(1.dp, border)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        }
    }
}

/** 지출 내역의 '구독 표시' 건을 정기결제로 일괄 가져오는 안내 배너. */
@Composable
private fun ImportFromSpendingBanner(count: Int, accent: Color, onImport: () -> Unit) {
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Autorenew, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("지출에서 가져오기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("정기결제로 표시된 지출 ${count}건을 등록할 수 있어요.", fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable { onImport() },
            ) {
                Text(
                    "가져오기",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun UpcomingRow(sub: Subscription, accent: Color, onClick: () -> Unit) {
    val d = sub.dDay()
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(sub.gameColor.toColor()))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(sub.name.ifBlank { "구독" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Text("${GameData.byName(sub.gameName).shortName} · 매월 ${sub.billingDay}일", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(won(sub.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            DDayBadge(d, accent)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.LightGray, modifier = Modifier.size(18.dp).padding(start = 2.dp))
    }
}

/** D-day 뱃지 — 오늘=accent / D-1=앰버 / 그 외=회색. */
@Composable
private fun DDayBadge(d: Int, accent: Color) {
    val (bg, fg) = when {
        d == 0 -> accent.copy(alpha = 0.15f) to accent
        d == 1 -> DueAmber.copy(alpha = 0.15f) to DueAmber
        else -> Color(0xFFF6F7F9) to TextSecondary
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            if (d == 0) "오늘" else "D-$d",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

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
    var expanded by remember { mutableStateOf(false) }
    val monthlyTotal = subscriptions.sumOf { it.amount }

    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize()) {
            // 헤더(클릭 시 펼침/접힘) — 접힌 상태에선 개수·월 합계 요약만 노출
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Autorenew, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("구독 관리", fontWeight = FontWeight.Bold)
                    if (subscriptions.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text("${subscriptions.size}개 · 월 " + won(monthlyTotal), fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "접기" else "펼치기",
                    tint = Color.LightGray,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showAdd = true },
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("구독 추가", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
                if (subscriptions.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("월정액·패스 등 정기결제를 등록하면 월 합계와 다음 결제일을 관리해요.", fontSize = 12.sp, color = TextSecondary)
                } else {
                    Spacer(Modifier.height(8.dp))
                    subscriptions.forEachIndexed { i, sub ->
                        SubscriptionRow(sub) { editTarget = sub }
                        if (i < subscriptions.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                        }
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
        Box(Modifier.size(8.dp).clip(CircleShape).background(sub.gameColor.toColor()))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(sub.name.ifBlank { "구독" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Text("매월 ${sub.billingDay}일 · ${GameData.byName(sub.gameName).shortName}", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(won(sub.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                        color = if (sel) g.color.toColor() else Color.White,
                        border = BorderStroke(1.dp, if (sel) g.color.toColor() else DividerColor),
                    ) {
                        Text(g.shortName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else g.color.toColor())
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
