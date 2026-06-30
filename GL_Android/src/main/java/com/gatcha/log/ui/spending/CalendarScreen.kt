package com.gatcha.log.ui.spending

import com.gatcha.log.data.SpendingViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won
import java.util.Calendar

/**
 * 통합 캘린더 — **타임라인 형식**. 선택한 달에서 활동(지출·픽업 배너 시작/종료)이 있는 날은 노드로,
 * 활동이 없는 연속 구간은 하나의 '활동 없음' 노드로 묶어 노출한다. (월 그리드 → 타임라인 대개편)
 */
@Composable
fun CalendarScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val spendings by viewModel.spendings.collectAsState()
    val banners by viewModel.activeBanners.collectAsState()

    // 표시 중인 연·월 (기본: 이번 달). month 는 1-base.
    var year by remember { mutableIntStateOf(viewModel.displayYear) }
    var month by remember { mutableIntStateOf(viewModel.displayMonth) }

    fun shift(delta: Int) {
        val c = Calendar.getInstance().apply { set(year, month - 1, 1); add(Calendar.MONTH, delta) }
        year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH) + 1
    }

    val todayKey = remember { DateUtil.dayKey(System.currentTimeMillis()) }
    val entries = remember(spendings, banners, year, month) {
        buildEntries(spendings, banners, year, month, todayKey)
    }
    val monthTotal = remember(entries) { entries.filterIsInstance<ActiveDay>().sumOf { it.spendTotal } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { GlgScreenHeader("캘린더", onBack) }
        item {
            // 월 이동 헤더
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthNavButton(Icons.Default.ChevronLeft, "이전 달") { shift(-1) }
                Text("${year}년 ${month}월", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                MonthNavButton(Icons.Default.ChevronRight, "다음 달") { shift(1) }
            }
        }
        item {
            // 월 요약(총 지출)
            SummaryPill("이번 달 총 지출", won(monthTotal), accent, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp))
        }
        if (entries.isEmpty()) {
            item { EmptyTimeline() }
        } else {
            itemsIndexed(entries, key = { _, e -> if (e is ActiveDay) "a${e.day}" else "g${(e as GapDays).highDay}" }) { idx, e ->
                when (e) {
                    is ActiveDay -> TimelineDayItem(e, isFirst = idx == 0, isLast = idx == entries.lastIndex, accent = accent)
                    is GapDays -> GapDaysItem(e, isLast = idx == entries.lastIndex)
                }
            }
        }
    }
}

/** 타임라인 한 노드 — 왼쪽 날짜/레일(점·선) + 오른쪽 그날 활동 카드. */
@Composable
private fun TimelineDayItem(day: ActiveDay, isFirst: Boolean, isLast: Boolean, accent: Color) {
    val weekdayKo = arrayOf("일", "월", "화", "수", "목", "금", "토")[day.weekdayIndex]
    val dateColor = when {
        day.isToday -> accent
        day.weekdayIndex == 0 -> DangerText
        else -> TextPrimary
    }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 날짜
        Column(
            modifier = Modifier.width(34.dp).padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${day.day}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dateColor)
            Text(weekdayKo, fontSize = 10.sp, color = if (day.isToday) accent else TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        TimelineRail(isFirst = isFirst, isLast = isLast, accent = accent, today = day.isToday)
        Spacer(Modifier.width(10.dp))
        // 활동 카드
        Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
            GlassCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    // 지출
                    if (day.spendings.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("지출 ${day.spendings.size}건", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Text(won(day.spendTotal), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)
                        }
                        Spacer(Modifier.height(8.dp))
                        day.spendings.forEachIndexed { i, sp ->
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            SpendLine(sp)
                        }
                    }
                    // 배너 시작/종료
                    if (day.bannerStart.isNotEmpty() || day.bannerEnd.isNotEmpty()) {
                        if (day.spendings.isNotEmpty()) { Spacer(Modifier.height(10.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(10.dp)) }
                        day.bannerStart.forEach { BannerLine("▲", "${it.name} 픽업 시작", it.gameColor.toColor()) }
                        day.bannerEnd.forEach { BannerLine("▼", "${it.name} 픽업 종료", it.gameColor.toColor()) }
                    }
                }
            }
        }
    }
}

/** 활동 없는 연속 구간을 한 노드로 — 흐린 점 + "활동 없음" 텍스트. */
@Composable
private fun GapDaysItem(gap: GapDays, isLast: Boolean) {
    val label = if (gap.lowDay == gap.highDay) "${gap.lowDay}일 · 활동 없음"
    else "${gap.lowDay}일–${gap.highDay}일 · 활동 없음 (${gap.highDay - gap.lowDay + 1}일)"
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Spacer(Modifier.width(34.dp))
        Spacer(Modifier.width(8.dp))
        // 레일 — 흐린 작은 점(활동 없는 구간은 노드 사이라 선은 위/아래 모두 연결)
        Box(
            modifier = Modifier.width(16.dp).fillMaxHeight().drawBehind {
                val cx = size.width / 2
                drawLine(DividerColor, Offset(cx, 0f), Offset(cx, if (isLast) 11.dp.toPx() else size.height), strokeWidth = 2.dp.toPx())
            },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(Color.LightGray))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = 14.dp)) {
            Text(label, fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Medium)
        }
    }
}

/** 타임라인 레일 — 점(노드) + 연결선(점 위/아래로 이어 연속). */
@Composable
private fun TimelineRail(isFirst: Boolean, isLast: Boolean, accent: Color, today: Boolean) {
    Box(
        modifier = Modifier.width(16.dp).fillMaxHeight().drawBehind {
            val cx = size.width / 2
            val dotY = 10.dp.toPx()
            drawLine(
                color = DividerColor,
                start = Offset(cx, if (isFirst) dotY else 0f),
                end = Offset(cx, if (isLast) dotY else size.height),
                strokeWidth = 2.dp.toPx(),
            )
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(if (today) accent else Color.White)
                .drawBehind {
                    drawCircle(color = accent, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                },
        )
    }
}

/** 지출 한 줄 — 게임색 점 + 게임·아이템 + 금액 (first-end). */
@Composable
private fun SpendLine(sp: Spending) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(sp.gameColor.toColor()))
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(sp.gameName, sp.itemName.ifBlank { null }).joinToString(" · "),
            fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(won(sp.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

/** 배너 시작/종료 한 줄 — ▲/▼ 마커(게임색) + 문구. */
@Composable
private fun BannerLine(marker: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(marker, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MonthNavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SummaryPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    GlassCard(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun EmptyTimeline() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = Color.LightGray, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text("이번 달 활동이 없어요", color = TextSecondary, fontSize = 14.sp)
        Text("지출·픽업 일정이 이 타임라인에 모여요", color = Color.LightGray, fontSize = 12.sp)
    }
}

// ----------------------------------------------------------------- 집계 모델

private sealed interface TimelineEntry
private data class ActiveDay(
    val day: Int,
    val weekdayIndex: Int,
    val isToday: Boolean,
    val spendings: List<Spending>,
    val spendTotal: Long,
    val bannerStart: List<GachaBanner>,
    val bannerEnd: List<GachaBanner>,
) : TimelineEntry
/** 활동이 없는 연속 일 구간 [lowDay..highDay] (한 노드로 묶어 노출). */
private data class GapDays(val lowDay: Int, val highDay: Int) : TimelineEntry

/** day-of-month (1..31) 추출 — millis 가 [year]/[month] 에 속하면 일자, 아니면 null. */
private fun dayInMonth(millis: Long, year: Int, month: Int): Int? {
    if (millis <= 0L) return null
    if (!DateUtil.isSameMonth(millis, year, month)) return null
    return Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)
}

/** 활동(지출·배너)이 있는 날은 노드로, 그 사이 빈 구간은 GapDays 로 묶어 최신순 타임라인 엔트리 리스트로. */
private fun buildEntries(
    spendings: List<Spending>,
    banners: List<GachaBanner>,
    year: Int,
    month: Int,
    todayKey: String,
): List<TimelineEntry> {
    val spendByDay = HashMap<Int, MutableList<Spending>>()
    spendings.forEach { s ->
        dayInMonth(s.dateMillis, year, month)?.let { d -> spendByDay.getOrPut(d) { mutableListOf() }.add(s) }
    }
    val bannerStartByDay = HashMap<Int, MutableList<GachaBanner>>()
    val bannerEndByDay = HashMap<Int, MutableList<GachaBanner>>()
    banners.forEach { b ->
        dayInMonth(b.startMillis, year, month)?.let { d -> bannerStartByDay.getOrPut(d) { mutableListOf() }.add(b) }
        dayInMonth(b.endMillis, year, month)?.let { d -> bannerEndByDay.getOrPut(d) { mutableListOf() }.add(b) }
    }

    val activeDays = (spendByDay.keys + bannerStartByDay.keys + bannerEndByDay.keys).toSortedSet().sortedDescending()
    if (activeDays.isEmpty()) return emptyList()

    val cal = Calendar.getInstance()
    fun activeDay(d: Int): ActiveDay {
        cal.set(year, month - 1, d)
        val daySpendings = (spendByDay[d] ?: emptyList()).sortedByDescending { it.amount }
        return ActiveDay(
            day = d,
            weekdayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1,
            isToday = "%04d-%02d-%02d".format(year, month, d) == todayKey,
            spendings = daySpendings,
            spendTotal = daySpendings.sumOf { it.amount },
            bannerStart = bannerStartByDay[d].orEmpty(),
            bannerEnd = bannerEndByDay[d].orEmpty(),
        )
    }

    val entries = mutableListOf<TimelineEntry>()
    activeDays.forEachIndexed { i, d ->
        entries.add(activeDay(d))
        if (i < activeDays.lastIndex) {
            val next = activeDays[i + 1] // d 보다 작은 다음 활동일
            if (d - next > 1) entries.add(GapDays(lowDay = next + 1, highDay = d - 1))
        }
    }
    return entries
}
