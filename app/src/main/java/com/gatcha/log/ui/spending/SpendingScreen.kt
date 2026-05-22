package com.gatcha.log.ui.spending

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.*
import java.util.Calendar

@Composable
fun SpendingScreen(viewModel: SpendingViewModel, onEditSpending: (Spending) -> Unit) {
    val spendings by viewModel.spendings.collectAsState()
    var selectedGameFilter by remember { mutableStateOf<String?>(null) }

    val monthlyTotal = remember(spendings) { viewModel.monthlyTotal() }
    val prevMonthTotal = remember(spendings) { previousMonthTotal(spendings) }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Text(
                    "지출 분석",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
                )
            }
            item { MonthlySummaryCard(viewModel.displayMonth, monthlyTotal, prevMonthTotal) }
            item { Spacer(Modifier.height(24.dp)) }
            item { AnnualReportSection(viewModel) }
            item { Spacer(Modifier.height(24.dp)) }
            item { GameFilterRow(selectedGameFilter) { selectedGameFilter = it } }

            val filtered = if (selectedGameFilter == null) spendings
            else spendings.filter { it.gameName == selectedGameFilter }

            if (filtered.isEmpty()) {
                item { EmptyState() }
            } else {
                val grouped = filtered.sortedByDescending { it.dateMillis }.groupBy { it.dayKey }
                grouped.forEach { (_, items) ->
                    item { DateHeader(items.first().dateLabel, items.sumOf { it.amount }) }
                    items(items, key = { it.id }) { spending ->
                        HistoryItem(
                            spending = spending,
                            onEdit = { onEditSpending(spending) },
                            onDelete = { viewModel.deleteSpending(spending.id) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

private fun previousMonthTotal(spendings: List<Spending>): Long {
    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    return spendings.filter { DateUtil.isSameMonth(it.dateMillis, y, m) }.sumOf { it.amount }
}

@Composable
fun MonthlySummaryCard(month: Int, total: Long, prevTotal: Long) {
    val accent = LocalAccent.current
    val diff = total - prevTotal
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("${month}월 총 지출", fontWeight = FontWeight.Medium, color = TextSecondary)
            }
            Text("₩%,d".format(total), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = DividerColor)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryItem("지난 달", "₩%,d".format(prevTotal), TextSecondary)
                SummaryItem(
                    "지난 달 대비",
                    (if (diff >= 0) "+" else "-") + "₩%,d".format(kotlin.math.abs(diff)),
                    if (diff > 0) DangerText else accent,
                )
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun AnnualReportSection(viewModel: SpendingViewModel) {
    val accent = LocalAccent.current
    val spendings by viewModel.spendings.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val year = viewModel.displayYear
    val yearlyTotal = remember(spendings) { viewModel.yearlyTotal() }
    val avg = remember(spendings) { yearlyTotal / viewModel.displayMonth.coerceAtLeast(1) }

    GlassCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, null, tint = accent)
                    Spacer(Modifier.width(8.dp))
                    Text("${year}년 연간 리포트", fontWeight = FontWeight.Bold)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoColumn("₩%,d".format(yearlyTotal), "총 지출", Modifier.weight(1f))
                        InfoColumn("₩%,d".format(avg), "월 평균", Modifier.weight(1f))
                        InfoColumn("${spendings.count { DateUtil.isSameYear(it.dateMillis, year) }}회", "총 기록", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun GameFilterRow(selectedGame: String?, onGameSelected: (String?) -> Unit) {
    val accent = LocalAccent.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        item { FilterPill("전체", selectedGame == null, accent) { onGameSelected(null) } }
        items(GameData.games) { game ->
            FilterPill(game.shortName, selectedGame == game.displayName, accent) { onGameSelected(game.displayName) }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accent else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accent else DividerColor),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else Color.DarkGray,
        )
    }
}

@Composable
fun DateHeader(date: String, total: Long) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(date, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
        Text("₩%,d".format(total), fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HistoryItem(spending: Spending, onEdit: () -> Unit, onDelete: () -> Unit) {
    val showMenu = remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onEdit() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(spending.gameColor, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spending.gameName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (spending.isSubscription) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = spending.gameColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                            Text("정기", fontSize = 9.sp, color = spending.gameColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(
                    listOfNotNull(spending.itemName.ifBlank { null }, spending.paymentMethod).joinToString(" · "),
                    fontSize = 11.sp, color = TextSecondary,
                )
                if (spending.tags.isNotEmpty()) {
                    Text("#${spending.tags.joinToString(" #")}", fontSize = 10.sp, color = LocalAccent.current)
                }
            }
            Text("₩%,d".format(spending.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Box {
                IconButton(onClick = { showMenu.value = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "메뉴", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu.value, onDismissRequest = { showMenu.value = false }) {
                    DropdownMenuItem(text = { Text("수정") }, onClick = { showMenu.value = false; onEdit() })
                    DropdownMenuItem(text = { Text("삭제", color = DangerText) }, onClick = { showMenu.value = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("아직 기록된 지출이 없어요", color = TextSecondary, fontSize = 14.sp)
        Text("+ 버튼으로 첫 지출을 기록해보세요", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
fun InfoColumn(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}