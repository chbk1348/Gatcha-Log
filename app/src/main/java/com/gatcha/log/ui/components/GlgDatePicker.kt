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
import java.util.Calendar

/**
 * 커스텀 달력 날짜 선택 다이얼로그 (Material DatePicker 대체).
 */
@Composable
fun GlgDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val accent = LocalAccent.current
    val initCal = remember { Calendar.getInstance().apply { timeInMillis = initialMillis } }

    var viewYear by remember { mutableIntStateOf(initCal.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(initCal.get(Calendar.MONTH)) } // 0-base
    var selYear by remember { mutableIntStateOf(initCal.get(Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(initCal.get(Calendar.MONTH)) }
    var selDay by remember { mutableIntStateOf(initCal.get(Calendar.DAY_OF_MONTH)) }

    fun shiftMonth(delta: Int) {
        val c = Calendar.getInstance().apply { set(viewYear, viewMonth, 1); add(Calendar.MONTH, delta) }
        viewYear = c.get(Calendar.YEAR)
        viewMonth = c.get(Calendar.MONTH)
    }

    GlgDialog(
        title = "날짜 선택",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = {
            val out = Calendar.getInstance().apply {
                set(selYear, selMonth, selDay, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onConfirm(out.timeInMillis)
        },
    ) {
        // 월 이동 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowBox(Icons.Default.ChevronLeft, "이전 달") { shiftMonth(-1) }
            Text("${viewYear}년 ${viewMonth + 1}월", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
        val firstDow = Calendar.getInstance().apply { set(viewYear, viewMonth, 1) }
            .get(Calendar.DAY_OF_WEEK) - 1 // 0=일
        val daysInMonth = Calendar.getInstance().apply { set(viewYear, viewMonth, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)
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
