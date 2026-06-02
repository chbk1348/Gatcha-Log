package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.Game
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextSecondary

/** 게임별 전투 콘텐츠 진행도 카드 (나선 비경·현실 속 환상극 / 혼돈의 기억·허구 이야기·종말의 환영). */
@Composable
internal fun CombatGameCard(game: Game, modes: List<CombatMode>) {
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                Box(Modifier.size(10.dp).background(game.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(game.shortName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            modes.forEachIndexed { i, m ->
                CombatRow(m)
                if (i < modes.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

@Composable
private fun CombatRow(m: CombatMode) {
    val accent = LocalAccent.current
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(m.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(m.detail, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                when {
                    m.maxStars > 0 -> Text("⭐ ${m.stars}/${m.maxStars}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = m.gameColor)
                    m.hasData -> Text("메달 ${m.stars}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = m.gameColor)
                }
                val d = m.dDay()
                if (d != null && d >= 0) Text("D-$d", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        }
        if (m.hasData && m.maxStars > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { m.ratio },
                color = m.gameColor, trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
    }
}
