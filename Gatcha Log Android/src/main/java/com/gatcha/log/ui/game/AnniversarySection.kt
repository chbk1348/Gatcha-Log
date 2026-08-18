package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameAnniversary
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

/**
 * 게임 주년 — 지원 게임의 다가오는 주년을 임박 순으로 표시(회차 + D-day).
 * 게임 일정 상세 페이지의 '주년' 탭 본문(제목은 탭이 대신하므로 여기선 카드만).
 */
@Composable
fun AnniversaryContent() {
    val accent = LocalAccent.current
    val items = remember { GameAnniversary.upcoming() }
    if (items.isEmpty()) {
        Text("예정된 주년이 없어요.", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
        return
    }
    Text("다가오는 순서예요.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            items.forEachIndexed { i, a ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlgGameTag(a.game.displayName, size = GameTagSize.Small)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        // 이름은 shortName 으로 — 다른 섹션은 전부 shortName 인데 여기만 displayName 이었다.
                        Text(a.game.shortName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                        // 회차만 있으면 어느 날짜 기준인지 알 수 없다 — 근거가 되는 출시일을 함께 둔다.
                        Text("${a.ordinal}주년 · ${a.launchLabel} 출시", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                    }
                    if (a.daysUntil == 0) {
                        Text("오늘 🎉", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
                    } else {
                        Text("D-${a.daysUntil}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
            }
        }
    }
}
