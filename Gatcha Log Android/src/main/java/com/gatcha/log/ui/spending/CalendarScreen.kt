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
import com.gatcha.log.data.Spending
import com.gatcha.log.data.SpendingCalendarDay
import com.gatcha.log.data.SpendingCalendarLogic
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

    // 달 넘기기 · 타임라인 조립 · 월 합계는 공용 [SpendingCalendarLogic] 이 한다(27.52.0, iOS 와 한 소스).
    // 예전엔 여기서 java.util.Calendar 로, iOS 는 기기 달력으로 따로 셌다.
    fun shift(delta: Int) {
        val next = SpendingCalendarLogic.shiftMonth(year, month, delta)
        year = next.year; month = next.month
    }

    // 「오늘」의 기준 시각 — 화면이 떠 있는 동안은 처음 값 그대로 둔다(예전 todayKey 와 같다).
    val nowMillis = remember { System.currentTimeMillis() }
    val timeline = remember(spendings, banners, year, month) {
        SpendingCalendarLogic.buildMonth(spendings, banners, year, month, nowMillis)
    }
    val entries = timeline.entries
    val monthTotal = timeline.total
    // 총 지출 줄의 이름은 **보고 있는 달**을 따른다(27.52.0) — 예전엔 어느 달로 넘겨도 「이번 달 총 지출」이었다.
    // 이번 달이면 그대로, 같은 해의 다른 달은 「N월」, 해가 다르면 「YYYY년 N월」.
    val totalLabel = when {
        year == viewModel.displayYear && month == viewModel.displayMonth -> "이번 달 총 지출"
        year == viewModel.displayYear -> "${month}월 총 지출"
        else -> "${year}년 ${month}월 총 지출"
    }

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
                    Text(totalLabel, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
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
            itemsIndexed(entries, key = { _, e -> e.key }) { idx, e ->
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (idx > 0) GiHairline()
                    // 활동이 있는 날이면 그날 줄, 아니면 「활동 없음」 구간 줄.
                    val day = e.active
                    if (day != null) DayRow(day, accent = accent) else GapRow(e.gapLowDay, e.gapHighDay)
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
private fun DayRow(day: SpendingCalendarDay, accent: Color) {
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
private fun GapRow(lowDay: Int, highDay: Int) {
    val label = if (lowDay == highDay) "${lowDay}일 · 활동 없음"
    else "${lowDay}일–${highDay}일 · 활동 없음 (${highDay - lowDay + 1}일)"
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

// (27.52.0) 집계 모델(TimelineEntry · ActiveDay · GapDays)과 buildEntries 는 공용
// SpendingCalendarLogic.buildMonth 로 옮겼다 — iOS CalendarView 가 같은 조립을 따로 적어 두고 있었다.
