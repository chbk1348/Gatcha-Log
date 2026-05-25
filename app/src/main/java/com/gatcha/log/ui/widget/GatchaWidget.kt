package com.gatcha.log.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gatcha.log.MainActivity
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository

/** 홈 위젯 — 이번 달 지출·예산 사용률·오늘 출석 현황. 탭하면 앱 실행. */
class GatchaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = GatchaRepository(context, AppSettings.currentAccountId(context))
        val now = System.currentTimeMillis()
        val y = DateUtil.year(now); val m = DateUtil.month(now)
        val budget = repo.loadBudget()
        val monthTotal = repo.loadSpendings()
            .filter { DateUtil.isSameMonth(it.dateMillis, y, m) }.sumOf { it.amount }
        val attCount = (repo.loadAttendance()[DateUtil.hoyoDayKey()] ?: emptySet()).size
        val total = GameData.attendanceGames.size
        val pct = if (budget > 0) (monthTotal * 100 / budget).toInt() else -1

        provideContent { WidgetContent(monthTotal, pct, attCount, total) }
    }
}

private val White = ColorProvider(Color.White)
private val Faint = ColorProvider(Color(0xCCFFFFFF))

@Composable
private fun WidgetContent(monthTotal: Long, pct: Int, attCount: Int, attTotal: Int) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(Color(0xFF14B8A6))
            .cornerRadius(18.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text("Gatcha LOG", style = TextStyle(color = Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(6.dp))
        Text("이번 달 ₩%,d".format(monthTotal), style = TextStyle(color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold))
        if (pct >= 0) {
            Spacer(GlanceModifier.height(2.dp))
            Text("예산 ${pct}% 사용", style = TextStyle(color = Faint, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        Text("오늘 출석 $attCount/$attTotal", style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

/** 위젯 등록 리시버 (매니페스트에 선언). */
class GatchaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GatchaWidget()
}
