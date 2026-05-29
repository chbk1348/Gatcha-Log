package com.gatcha.log.ui.spending

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won
import java.util.Calendar

/**
 * N3 통합 캘린더 — 월 달력 한 화면에 일별 지출 합계·출석 체크·픽업 배너 시작/종료를 합성.
 * 지출(Spending)·출석(attendanceHistory)·활성 배너(activeBanners)를 모두 "yyyy-MM-dd" 키로 매칭한다.
 */
@Composable
fun CalendarScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    val spendings by viewModel.spendings.collectAsState()
    val attendance by viewModel.attendanceHistory.collectAsState()
    val banners by viewModel.activeBanners.collectAsState()

    // 표시 중인 연·월 (기본: 이번 달). month 는 1-base.
    var year by remember { mutableIntStateOf(viewModel.displayYear) }
    var month by remember { mutableIntStateOf(viewModel.displayMonth) }

    fun shift(delta: Int) {
        val c = Calendar.getInstance().apply { set(year, month - 1, 1); add(Calendar.MONTH, delta) }
        year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH) + 1
    }

    // 이번 달 일자 집계 — day(1..N) → 값. 모두 "yyyy-MM-dd" 키 기준.
    val data = remember(spendings, attendance, banners, year, month) {
        buildMonthData(spendings, attendance, banners, year, month)
    }
    val todayKey = remember { DateUtil.dayKey(System.currentTimeMillis()) }

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
            // 월 요약(총 지출·출석일수)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryPill("총 지출", won(data.monthSpendTotal), accent, Modifier.weight(1f))
                SummaryPill("출석", "${data.attendedDayCount}일", accent, Modifier.weight(1f))
            }
        }
        item {
            GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    WeekdayHeader()
                    Spacer(Modifier.height(4.dp))
                    CalendarGrid(year, month, data, todayKey, accent)
                }
            }
        }
        item { Spacer(Modifier.height(14.dp)); CalendarLegend(accent) }
    }
}

/** 달력 격자 — 첫 주 빈칸 + 1..말일. 한 줄(7칸)씩 Row 로 렌더. */
@Composable
private fun CalendarGrid(year: Int, month: Int, data: MonthData, todayKey: String, accent: Color) {
    val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
    val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=일 .. 6=토
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // 앞쪽 빈칸 + 날짜를 7칸 단위로 끊어 주(週) 리스트로.
    val cells = buildList {
        repeat(firstWeekday) { add(0) }
        for (d in 1..daysInMonth) add(d)
    }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until 7) {
                val day = week.getOrElse(i) { 0 }
                Box(Modifier.weight(1f)) {
                    if (day > 0) {
                        val key = "%04d-%02d-%02d".format(year, month, day)
                        DayCell(
                            day = day,
                            weekdayIndex = i,
                            isToday = key == todayKey,
                            spend = data.spendByDay[day] ?: 0L,
                            attended = data.attendByDay[day].orEmpty(),
                            bannerStart = data.bannerStartByDay[day].orEmpty(),
                            bannerEnd = data.bannerEndByDay[day].orEmpty(),
                            accent = accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    weekdayIndex: Int,
    isToday: Boolean,
    spend: Long,
    attended: Set<String>,
    bannerStart: List<GachaBanner>,
    bannerEnd: List<GachaBanner>,
    accent: Color,
) {
    val dayColor = when {
        isToday -> Color.White
        weekdayIndex == 0 -> DangerText        // 일요일
        else -> TextPrimary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (spend > 0) accent.copy(alpha = 0.07f) else Color.Transparent)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 배너 시작(▲)·종료(▼) 마커 — 게임색
        if (bannerStart.isNotEmpty() || bannerEnd.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                bannerStart.take(2).forEach { Text("▲", fontSize = 7.sp, color = it.gameColor) }
                bannerEnd.take(2).forEach { Text("▼", fontSize = 7.sp, color = it.gameColor) }
            }
        } else {
            Spacer(Modifier.height(9.dp))
        }
        // 날짜 숫자 (오늘이면 강조색 원 배경)
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(if (isToday) accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text("$day", fontSize = 12.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium, color = dayColor)
        }
        // 출석 게임 색 점 (최대 4)
        if (attended.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                attended.take(4).forEach { gk ->
                    val c = GameData.games.firstOrNull { it.key == gk }?.color ?: TextSecondary
                    Box(Modifier.size(4.dp).clip(CircleShape).background(c))
                }
            }
        }
        // 지출 합계 (컴팩트)
        if (spend > 0) {
            Text(compactAmount(spend), fontSize = 8.sp, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { i, l ->
            Text(
                l,
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (i == 0) DangerText else TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun CalendarLegend(accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.18f))); Text(" 지출", fontSize = 11.sp, color = TextSecondary) }
        LegendItem { Box(Modifier.size(6.dp).clip(CircleShape).background(Game.GENSHIN.color)); Text(" 출석", fontSize = 11.sp, color = TextSecondary) }
        LegendItem { Text("▲", fontSize = 9.sp, color = accent); Text(" 배너 시작", fontSize = 11.sp, color = TextSecondary) }
        LegendItem { Text("▼", fontSize = 9.sp, color = accent); Text(" 종료", fontSize = 11.sp, color = TextSecondary) }
    }
}

@Composable
private fun LegendItem(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { content() }
}

// ----------------------------------------------------------------- 집계 모델

private data class MonthData(
    val spendByDay: Map<Int, Long>,
    val attendByDay: Map<Int, Set<String>>,
    val bannerStartByDay: Map<Int, List<GachaBanner>>,
    val bannerEndByDay: Map<Int, List<GachaBanner>>,
    val monthSpendTotal: Long,
    val attendedDayCount: Int,
)

/** day-of-month (1..31) 추출 — millis 가 [year]/[month] 에 속하면 일자, 아니면 null. */
private fun dayInMonth(millis: Long, year: Int, month: Int): Int? {
    if (millis <= 0L) return null
    if (!DateUtil.isSameMonth(millis, year, month)) return null
    return Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)
}

private fun buildMonthData(
    spendings: List<Spending>,
    attendance: Map<String, Set<String>>,
    banners: List<GachaBanner>,
    year: Int,
    month: Int,
): MonthData {
    val prefix = "%04d-%02d".format(year, month) // "2026-05"
    val spendByDay = HashMap<Int, Long>()
    spendings.forEach { s ->
        dayInMonth(s.dateMillis, year, month)?.let { d -> spendByDay[d] = (spendByDay[d] ?: 0L) + s.amount }
    }
    // 출석 키는 "yyyy-MM-dd" → 이번 달 것만 골라 일자 추출.
    val attendByDay = HashMap<Int, Set<String>>()
    attendance.forEach { (key, games) ->
        if (key.startsWith(prefix) && games.isNotEmpty()) {
            key.substringAfterLast('-').toIntOrNull()?.let { d -> attendByDay[d] = games }
        }
    }
    val bannerStartByDay = HashMap<Int, MutableList<GachaBanner>>()
    val bannerEndByDay = HashMap<Int, MutableList<GachaBanner>>()
    banners.forEach { b ->
        dayInMonth(b.startMillis, year, month)?.let { d -> bannerStartByDay.getOrPut(d) { mutableListOf() }.add(b) }
        dayInMonth(b.endMillis, year, month)?.let { d -> bannerEndByDay.getOrPut(d) { mutableListOf() }.add(b) }
    }
    return MonthData(
        spendByDay = spendByDay,
        attendByDay = attendByDay,
        bannerStartByDay = bannerStartByDay,
        bannerEndByDay = bannerEndByDay,
        monthSpendTotal = spendByDay.values.sum(),
        attendedDayCount = attendByDay.size,
    )
}

/** 칸에 들어갈 컴팩트 금액 — "1.2만", "3천", "500". */
private fun compactAmount(v: Long): String = when {
    v >= 10_000 -> {
        val man = v / 10_000
        val rem = (v % 10_000) / 1_000
        if (rem == 0L) "${man}만" else "$man.${rem}만"
    }
    v >= 1_000 -> "${v / 1_000}천"
    else -> "$v"
}
