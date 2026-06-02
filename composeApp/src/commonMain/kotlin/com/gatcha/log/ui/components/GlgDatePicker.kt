package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val pickerTz: TimeZone get() = TimeZone.currentSystemDefault()

/** 일요일을 0 으로 두는 인덱스 (java.util.Calendar.DAY_OF_WEEK - 1 과 동일). */
private val DayOfWeek.sundayBasedIndex: Int
    get() = when (this) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        else -> 0
    }

/** 해당 연·월(1-base month)의 일수. */
private fun daysInMonth(year: Int, month: Int): Int =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day

/**
 * 커스텀 달력 날짜 선택 다이얼로그 (Material DatePicker 대체).
 */
@OptIn(ExperimentalTime::class)
@Composable
fun GlgDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val accent = LocalAccent.current
    val initDateTime = remember {
        Instant.fromEpochMilliseconds(initialMillis).toLocalDateTime(pickerTz)
    }

    var viewYear by remember { mutableIntStateOf(initDateTime.year) }
    var viewMonth by remember { mutableIntStateOf(initDateTime.month.number) } // 1-base
    var selYear by remember { mutableIntStateOf(initDateTime.year) }
    var selMonth by remember { mutableIntStateOf(initDateTime.month.number) }
    var selDay by remember { mutableIntStateOf(initDateTime.day) }

    fun shiftMonth(delta: Int) {
        val base = LocalDate(viewYear, viewMonth, 1)
        val d = if (delta >= 0) base.plus(delta, DateTimeUnit.MONTH)
        else base.minus(-delta, DateTimeUnit.MONTH)
        viewYear = d.year
        viewMonth = d.month.number
    }

    GlgDialog(
        title = "날짜 선택",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = {
            val out = LocalDateTime(LocalDate(selYear, selMonth, selDay), LocalTime(12, 0, 0))
                .toInstant(pickerTz)
                .toEpochMilliseconds()
            onConfirm(out)
        },
    ) {
        // 월 이동 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowBox(Icons.Default.ChevronLeft, "이전 달") { shiftMonth(-1) }
            Text("${viewYear}년 ${viewMonth}월", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            ArrowBox(Icons.Default.ChevronRight, "다음 달") { shiftMonth(1) }
        }
        Spacer(Modifier.height(12.dp))

        // 요일 헤더
        Row(Modifier.fillMaxWidth()) {
            val labels = listOf("일", "월", "화", "수", "목", "금", "토")
            labels.forEachIndexed { i, d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (i) {
                        0 -> Color(0xFFE5484D)
                        6 -> Color(0xFF4F8EF7)
                        else -> TextSecondary
                    },
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // 날짜 그리드
        val firstDow = LocalDate(viewYear, viewMonth, 1).dayOfWeek.sundayBasedIndex // 0=일
        val daysInMonth = daysInMonth(viewYear, viewMonth)
        val cells = firstDow + daysInMonth
        val rows = (cells + 6) / 7

        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstDow + 1
                    if (day in 1..daysInMonth) {
                        val isSelected = (viewYear == selYear && viewMonth == selMonth && day == selDay)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) accent else Color.Transparent)
                                .clickable {
                                    selYear = viewYear; selMonth = viewMonth; selDay = day
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFF1A1C1E),
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrowBox(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFFF2F2F6))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}
