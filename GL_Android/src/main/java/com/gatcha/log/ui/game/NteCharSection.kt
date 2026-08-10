package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.data.Game
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.NteCharacter
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

/**
 * 이환 캐릭터 도감 섹션.
 *
 * 이환만 공지 API 가 없어(공식 사이트가 정적 렌더) '공지·뉴스'에 낄 수가 없다. 대신 hakush CDN 의
 * 캐릭터 데이터를 붙였다. 아이콘은 게임 내부 경로만 와서 텍스트 위주다(자세한 사정은 `NteApi` KDoc).
 *
 * 헤더 드롭다운이 이환·전체가 아닐 땐 섹션 자체를 감춘다 — 다른 게임을 보는 중에 끼어들 이유가 없다.
 */
@Composable
fun NteCharSection(viewModel: SpendingViewModel, gameFilter: String, max: Int = 6) {
    if (gameFilter != "all" && gameFilter != Game.NTE.key) return
    val chars by viewModel.nteCharacters.collectAsStateWithLifecycle()
    // 정적 데이터라 화면이 살아 있는 동안 1회면 된다(중복 호출은 뷰모델이 막는다).
    LaunchedEffect(Unit) { viewModel.loadNteCharacters() }
    if (chars.isEmpty()) return

    // 앞 섹션과의 간격을 여기서 낸다 — 데이터가 없을 때 빈 여백만 남지 않도록(호출부 주석 참고).
    Spacer(Modifier.height(20.dp))
    Text("이환 캐릭터", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            chars.take(max).forEachIndexed { i, c ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                NteCharRow(c)
            }
            if (chars.size > max) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text("외 ${chars.size - max}명", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun NteCharRow(c: NteCharacter) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        GlgGameTag(Game.NTE.displayName, size = GameTagSize.Small)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                if (c.element.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    ElementChip(c.element)
                }
            }
            val sub = listOfNotNull(
                "${c.rarity}성".takeIf { c.rarity > 0 },
                c.tags.joinToString(" · ").ifBlank { null },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = TextSecondary)
            if (c.desc.isNotBlank()) {
                Text(c.desc, fontSize = 11.sp, color = TextSecondary, maxLines = 2)
            }
        }
    }
}

/** 속성 칩 — 게임 대표색을 옅게 깐 작은 배지(가챠 태그 칩과 같은 톤). */
@Composable
private fun ElementChip(element: String) {
    val color = Game.NTE.color.toColor()
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(element, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
