package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameData
import com.gatcha.log.data.MonthlyLedger
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.num

/** 게임별 이번 달 재화 수입 카드 (여행자의 일지 / 폴리크롬 일지). */
@Composable
internal fun LedgerCard(ledger: MonthlyLedger) {
    val accent = LocalAccent.current
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(ledger.gameColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(GameData.byName(ledger.game).shortName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (ledger.month > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("${ledger.month}월", fontSize = 12.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(num(ledger.premium), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.width(6.dp))
                Text(ledger.premiumLabel, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                ledger.premiumDelta?.let { d ->
                    Spacer(Modifier.width(10.dp))
                    val up = d >= 0
                    Text(
                        (if (up) "▲ " else "▼ ") + num(kotlin.math.abs(d)),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (up) Color(0xFF1FB16B) else Color(0xFFE5484D),
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
            if (ledger.gold > 0) {
                Spacer(Modifier.height(2.dp))
                Text("${ledger.goldLabel} ${num(ledger.gold)}", fontSize = 12.sp, color = TextSecondary)
            }
            if (ledger.breakdown.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                ledger.breakdown.take(5).forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.action, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(num(e.num), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Text("${e.percent}%", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                    }
                    LinearProgressIndicator(
                        progress = { (e.percent / 100f).coerceIn(0f, 1f) },
                        color = ledger.gameColor, trackColor = ProgressEmpty,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    )
                }
            }
        }
    }
}
