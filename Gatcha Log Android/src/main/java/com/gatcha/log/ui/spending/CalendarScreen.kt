package com.gatcha.log.ui.spending

import com.gatcha.log.data.SpendingViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.game.GiBand
import com.gatcha.log.ui.game.GiHairline
import com.gatcha.log.ui.game.GiPageSection
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.toColor
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
    val spendings by viewModel.spendings.collectAsStateWithLifecycle()
    val banners by viewModel.activeBanners.collectAsStateWithLifecycle()

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

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    // GLDS 2.0(10/6) — 흰 바탕, 카드 없이 화면 폭 섹션 둘(월 · 총 지출 / 날짜별 활동), 사이는 띠.
    // 날짜별 활동은 헤어라인으로 나눈 목록이다. iOS CalendarView 와 같은 수치.
    Box(Modifier.fillMaxSize().background(Color.White)) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(top = glgDetailContentTop()),
    ) {
        item(key = "month") {
            // 헤더 바로 아래 첫 섹션은 위 12(전투 진행도와 같다).
            GiPageSection(top = 12.dp) {
                // 월 이동
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthNavButton(Icons.Default.ChevronLeft, "이전 달") { shift(-1) }
                    Text("${year}년 ${month}월", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    MonthNavButton(Icons.Default.ChevronRight, "다음 달") { shift(1) }
                }
                Spacer(Modifier.height(16.dp))
                // 월 요약(총 지출) — 줄 제목 15 + 값 15 Bold
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("이번 달 총 지출", fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(won(monthTotal), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)
                }
            }
            GiBand()
        }
        item(key = "title") {
            // 마지막 줄이 아래 12 를 가져 8 + 12 = 눈에 20. 빈 상태는 자체 여백이 커서 20 그대로.
            GiPageSection("날짜별 활동", bottom = 0.dp) {}
        }
        if (entries.isEmpty()) {
            item(key = "empty") { Box(Modifier.padding(horizontal = 20.dp)) { EmptyTimeline() } }
        } else {
            itemsIndexed(entries, key = { _, e -> if (e is ActiveDay) "a${e.day}" else "g${(e as GapDays).highDay}" }) { idx, e ->
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (idx > 0) GiHairline()
                    when (e) {
                        is ActiveDay -> DayRow(e, accent = accent)
                        is GapDays -> GapRow(e)
                    }
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(if (entries.isEmpty()) 20.dp else 8.dp)) }
    }
    GlgDetailHeaderOverlay("캘린더", onBack, scrolled)
    }
}

/** 날짜 칸 폭 + 사이 — 활동 없음 줄도 이만큼 들여 내용 칸에 맞춘다. */
private val DateColumnWidth = 34.dp
private val DateContentGap = 14.dp

/** 날짜별 활동 한 줄 — 왼쪽 날짜 + 오른쪽 그날 지출 · 픽업 시작/종료. 카드 없이 위아래 12. */
@Composable
private fun DayRow(day: ActiveDay, accent: Color) {
    val weekdayKo = arrayOf("일", "월", "화", "수", "목", "금", "토")[day.weekdayIndex]
    val dateColor = when {
        day.isToday -> accent
        day.weekdayIndex == 0 -> DangerText
        else -> TextPrimary
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        // 날짜
        Column(Modifier.width(DateColumnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.day}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = dateColor)
            Text(weekdayKo, fontSize = 11.sp, color = if (day.isToday) accent else TextSecondary)
        }
        Spacer(Modifier.width(DateContentGap))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            // 지출
            if (day.spendings.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("지출 ${day.spendings.size}건", fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(won(day.spendTotal), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)
                }
                day.spendings.forEach { sp ->
                    Spacer(Modifier.height(6.dp))
                    SpendLine(sp)
                }
            }
            // 배너 시작/종료
            if (day.bannerStart.isNotEmpty() || day.bannerEnd.isNotEmpty()) {
                if (day.spendings.isNotEmpty()) Spacer(Modifier.height(8.dp))
                day.bannerStart.forEach { BannerLine("▲", "${it.name} 픽업 시작", it.gameColor.toColor()) }
                day.bannerEnd.forEach { BannerLine("▼", "${it.name} 픽업 종료", it.gameColor.toColor()) }
            }
        }
    }
}

/** 활동 없는 연속 구간을 한 줄로 — 흐린 "활동 없음" 문구, 내용 칸에 맞춰 들인다. */
@Composable
private fun GapRow(gap: GapDays) {
    val label = if (gap.lowDay == gap.highDay) "${gap.lowDay}일 · 활동 없음"
    else "${gap.lowDay}일–${gap.highDay}일 · 활동 없음 (${gap.highDay - gap.lowDay + 1}일)"
    Text(
        label, fontSize = 13.sp, color = Color.LightGray, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = DateColumnWidth + DateContentGap, top = 12.dp, bottom = 12.dp),
    )
}

/** 지출 한 줄 — 게임색 점 + 게임·아이템 + 금액 (first-end). */
@Composable
private fun SpendLine(sp: Spending) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(sp.gameColor.toColor()))
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(sp.gameName, sp.itemName.ifBlank { null }).joinToString(" · "),
            fontSize = 13.sp, color = TextSecondary,
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
        Text(text, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    // 시각→로컬 변환 **1회**로 연·월·일을 한꺼번에 얻는다(yyyyMMdd).
    // 예전엔 isSameMonth 로 한 번 변환하고, 일자를 얻으려고 지출 1건마다 Calendar 를 새로 만들었다
    // — Calendar.getInstance() 는 단순 할당이 아니라 로케일·타임존을 조회한다.
    val ymd = DateUtil.ymd(millis)
    if (ymd / 100 != year * 100 + month) return null
    return ymd % 100
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
