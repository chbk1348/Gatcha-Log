package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.TaskCompletion
import com.gatcha.log.data.TaskStats
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

// ============================================================
// 일일·주간 숙제 완주율 — 게임당 한 줄. (iOS TaskCompletionSection 패리티)
//
// HoYoLAB 은 '지금 상태'만 주므로 앱이 노트를 받을 때마다 그날 결과를 로컬에 적어 두고,
// 그 관측 기록에서 완주율·스트릭을 파생한다(계산은 GL_Shared TaskCompletion 단일 소스).
// 앱을 안 켠 날은 관측이 없어 분모에서 빠진다 — 화면에도 "기록 N일 기준"으로 밝힌다.
// ============================================================

private val Streak = Color(0xFFE8634A)
private val DoneGreen = Color(0xFF2BB673)

@Composable
fun TaskCompletionSection(stats: List<TaskStats>) {
    if (stats.isEmpty()) return
    Text("숙제 완주율", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
    Text(
        "앱에서 확인한 날 기준으로 세요. 최근 ${TaskCompletion.WINDOW_DAYS}일.",
        fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp),
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            stats.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = DividerColor.copy(alpha = 0.6f))
                TaskStatRow(s)
            }
        }
    }
}

@Composable
private fun TaskStatRow(s: TaskStats) {
    val c = s.colorArgb.toColor()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(3.dp, 26.dp).clip(RoundedCornerShape(2.dp)).background(c))
            Text(s.gameShort, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1)
            // 오늘·이번 주 완료 여부 — 숫자보다 먼저 눈에 들어와야 하는 정보.
            DoneMark("오늘", s.todayDone)
            if (s.weeklyWeeks > 0) DoneMark("주간", s.weekDone)
            Spacer(Modifier.weight(1f))
            if (s.dailyStreak > 0) {
                Surface(color = Streak.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        "🔥 ${s.dailyStreak}일", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Streak,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (s.isEmpty) "—" else "${s.dailyRate}%",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            subLabel(s), fontSize = 10.5.sp, color = TextSecondary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** 분모를 숨기지 않는다 — "87%"만 보면 앱을 며칠 안 켠 게 반영됐는지 알 수 없다. */
private fun subLabel(s: TaskStats): String {
    if (s.isEmpty) return "기록을 모으는 중이에요 — 앱을 열 때마다 쌓여요"
    val daily = "일일 ${s.dailyDays}일 기록"
    val weekly = if (s.weeklyWeeks > 0) " · 주간 ${s.weeklyRate}%(${s.weeklyWeeks}주)" else ""
    val best = if (s.dailyBest > s.dailyStreak) " · 최고 ${s.dailyBest}일" else ""
    return daily + weekly + best
}

@Composable
private fun DoneMark(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) DoneGreen else TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(13.dp),
        )
        Text(
            label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
            color = if (done) DoneGreen else TextSecondary,
        )
    }
}
