package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.NoteStat
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.theme.*

// ============================================================ 데일리 히어로 (실시간 노트 + 출석체크 통합)
/**
 * 최상단 히어로 카드. HoYoLAB 미연동 시에는 출석·실시간 노트를 숨기고 연동을 유도하고,
 * 연동되면 게임별 실시간 노트(레진/개척력/배터리)와 출석체크를 한 카드에 통합해 보여준다.
 */
@Composable
internal fun DailyHeroSection(
    notes: List<LiveNote>,
    attendanceToday: Set<String>,
    attendanceHistory: Map<String, Set<String>>,
    hoyolab: HoyolabConfig,
    checkingIn: String?,
    streak: Int,
    onCheckIn: (String) -> Unit,
    onConfigClick: () -> Unit,
) {
    val accent = LocalAccent.current
    var showCalendar by remember { mutableStateOf(false) }

    if (!hoyolab.isLinked) {
        GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Link, null, tint = accent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("HoYoLAB 연동이 필요해요", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "연동하면 실시간 노트(레진·개척력·배터리)와\n출석체크를 한곳에서 관리할 수 있어요.",
                    fontSize = 12.sp, color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(18.dp))
                GlgButton("HoYoLAB 연동하기", onClick = onConfigClick, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("오늘의 데일리", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (streak > 0) {
                        Spacer(Modifier.width(8.dp))
                        StreakChip(streak)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f))
                            .clickable { showCalendar = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.CalendarMonth, "출석 달력", tint = accent, modifier = Modifier.size(16.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onConfigClick() },
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("연동됨", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
            }
            // 최근 7일 출석 스트립
            Spacer(Modifier.height(14.dp))
            WeekAttendanceStrip(attendanceHistory)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(4.dp))
            GameData.attendanceGames.forEachIndexed { i, game ->
                val note = notes.firstOrNull { GameData.byNameOrNull(it.game)?.key == game.key }
                val uid = when (game.key) {
                    "genshin" -> hoyolab.genshinUid
                    "hsr" -> hoyolab.hsrUid
                    "zzz" -> hoyolab.zzzUid
                    else -> ""
                }
                DailyGameRow(game, note, uid, game.key in attendanceToday, checkingIn == game.key) { onCheckIn(game.key) }
                if (i < GameData.attendanceGames.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }

    if (showCalendar) {
        MonthAttendanceDialog(attendanceHistory) { showCalendar = false }
    }
}

/** 출석 완료도: 모든 게임 출석=full, 일부=partial, 없음=none */
private enum class AttendLevel { NONE, PARTIAL, FULL }

private fun attendLevel(count: Int): AttendLevel = when {
    count <= 0 -> AttendLevel.NONE
    count >= GameData.attendanceGames.size -> AttendLevel.FULL
    else -> AttendLevel.PARTIAL
}

private fun dowKo(cal: java.util.Calendar): String =
    arrayOf("일", "월", "화", "수", "목", "금", "토")[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]

/** 최근 7일 출석 스트립 (오늘 = 맨 오른쪽). */
@Composable
private fun WeekAttendanceStrip(history: Map<String, Set<String>>) {
    val accent = LocalAccent.current
    val days = remember(history) {
        (6 downTo 0).map { offset ->
            val cal = DateUtil.hoyoCalendar().apply { add(java.util.Calendar.DAY_OF_YEAR, -offset) }
            Triple(cal.get(java.util.Calendar.DAY_OF_MONTH), dowKo(cal), history[DateUtil.hoyoDayKey(cal.timeInMillis)]?.size ?: 0)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, (dayNum, dow, count) ->
            val isToday = i == 6
            val level = attendLevel(count)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dow, fontSize = 10.sp, color = if (isToday) accent else TextSecondary, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            when (level) {
                                AttendLevel.FULL -> accent
                                AttendLevel.PARTIAL -> accent.copy(alpha = 0.30f)
                                AttendLevel.NONE -> Color(0xFFF0F0F4)
                            },
                        )
                        .then(if (isToday) Modifier.border(2.dp, accent, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (level == AttendLevel.FULL) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            "$dayNum",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (level == AttendLevel.PARTIAL) accent else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** 월간 출석 달력 다이얼로그. 일자별 출석 완료도 표시 + 이전/이번 달 이동. */
@Composable
private fun MonthAttendanceDialog(history: Map<String, Set<String>>, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    var monthOffset by remember { mutableIntStateOf(0) } // 0 = 이번 달
    val base = remember(monthOffset) {
        DateUtil.hoyoCalendar().apply { add(java.util.Calendar.MONTH, monthOffset); set(java.util.Calendar.DAY_OF_MONTH, 1) }
    }
    val year = base.get(java.util.Calendar.YEAR)
    val month = base.get(java.util.Calendar.MONTH) // 0-based
    val firstDow = base.get(java.util.Calendar.DAY_OF_WEEK) // 1=일
    val daysInMonth = base.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val todayKey = DateUtil.hoyoDayKey()

    GlgDialog(title = "출석 현황", onDismiss = onDismiss, confirmText = "확인", onConfirm = onDismiss, dismissText = null) {
        Column {
            // 월 이동
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { monthOffset-- }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronLeft, "이전 달", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Text("${year}년 ${month + 1}월", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).then(if (monthOffset < 0) Modifier.clickable { monthOffset++ } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ChevronRight, "다음 달", tint = if (monthOffset < 0) TextSecondary else Color.LightGray, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            // 요일 헤더
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(6.dp))
            // 날짜 그리드 (선행 빈칸 + 1..말일)
            val cells: List<Int?> = List(firstDow - 1) { null } + (1..daysInMonth).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val key = "%04d-%02d-%02d".format(year, month + 1, day)
                                val level = attendLevel(history[key]?.size ?: 0)
                                val isToday = key == todayKey
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (level) {
                                                AttendLevel.FULL -> accent
                                                AttendLevel.PARTIAL -> accent.copy(alpha = 0.30f)
                                                AttendLevel.NONE -> Color.Transparent
                                            },
                                        )
                                        .then(if (isToday) Modifier.border(1.5.dp, accent, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$day",
                                        fontSize = 12.sp,
                                        fontWeight = if (level != AttendLevel.NONE || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            level == AttendLevel.FULL -> Color.White
                                            level == AttendLevel.PARTIAL -> accent
                                            isToday -> accent
                                            else -> TextSecondary
                                        },
                                    )
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 범례
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(accent, "전체 출석")
                LegendDot(accent.copy(alpha = 0.30f), "일부")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun StreakChip(streak: Int) {
    val accent = LocalAccent.current
    Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
        Text(
            "🔥 ${streak}일 연속",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyGameRow(game: Game, note: LiveNote?, uid: String, checked: Boolean, inProgress: Boolean, onCheckIn: () -> Unit) {
    val accent = LocalAccent.current
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = game.color.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(game.abbr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = game.color)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(game.shortName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (note != null && note.maxResin > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${note.resinLabel} ${note.currentResin}/${note.maxResin}", fontSize = 12.sp, color = TextSecondary)
                        if (note.resinRecoveryTime.isNotBlank()) {
                            Text(" · ${note.resinRecoveryTime}", fontSize = 11.sp, color = Color.LightGray, maxLines = 1)
                        }
                    }
                } else {
                    Text(
                        if (uid.isBlank()) "UID 미등록 — 설정에서 등록하세요" else "실시간 노트 동기화 중…",
                        fontSize = 11.sp, color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                inProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("처리 중", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                }
                checked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "완료", tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("완료", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                }
                else -> Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .clickable { onCheckIn() }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("출석", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (note != null && note.maxResin > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { note.resinRatio },
                color = accent, trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
        if (note != null && note.extras.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note.extras.forEach { NoteStatChip(it) }
            }
        }
    }
}

/** 실시간 노트 부가 통계 칩 (탐사 파견·주간 보스·세진 등). highlight 항목은 강조색으로 채운다. */
@Composable
private fun NoteStatChip(stat: NoteStat) {
    val accent = LocalAccent.current
    val bg = if (stat.highlight) accent.copy(alpha = 0.14f) else Color(0xFFF2F2F6)
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stat.label, fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.width(4.dp))
            Text(
                stat.value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (stat.highlight) accent else TextPrimary,
            )
        }
    }
}
