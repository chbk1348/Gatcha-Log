package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.Game
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

/**
 * 별 획득 수 — 아이콘 + 숫자. 이모지(⭐)를 쓰면 기기·OS 폰트에 따라 모양과 크기가 제각각이라
 * 옆 숫자와 기준선이 어긋난다. 벡터 아이콘은 색을 게임색으로 물들일 수 있다는 이점도 있다.
 *
 * 아이콘 자체엔 [contentDescription] 을 주지 않고 행 전체에 하나만 준다 — 이모지일 때는
 * 스크린리더가 "별"을 읽어 줬는데, 아이콘으로 바꾸면 숫자만 남아 무엇의 개수인지 알 수 없다.
 */
@Composable
private fun StarCount(label: String, color: Color, description: String, size: TextUnit = 13.sp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(with(LocalDensity.current) { size.toDp() }),
        )
        Text(label, fontSize = size, fontWeight = FontWeight.Bold, color = color)
    }
}

/** 게임별 전투 콘텐츠 진행도 카드 (나선 비경·현실 속 환상극 / 혼돈의 기억·허구 이야기·종말의 환영). */
@Composable
internal fun CombatGameCard(game: Game, modes: List<CombatMode>) {
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                GlgGameTag(game.displayName, size = GameTagSize.Small)
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
                    m.maxStars > 0 -> StarCount(
                        label = "${m.stars}/${m.maxStars}",
                        color = m.gameColor.toColor(),
                        description = "별 ${m.stars} / ${m.maxStars}",
                    )
                    m.hasData -> Text("메달 ${m.stars}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = m.gameColor.toColor())
                }
                val d = m.dDay()
                if (d != null && d >= 0) Text("D-$d", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
            }
        }
        if (m.hasData && m.maxStars > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { m.ratio },
                color = m.gameColor.toColor(), trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
    }
}
