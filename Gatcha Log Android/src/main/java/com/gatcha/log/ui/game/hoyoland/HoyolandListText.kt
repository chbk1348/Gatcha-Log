package com.gatcha.log.ui.game.hoyoland

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.gatcha.log.data.HoyolandText
import com.gatcha.log.data.HoyolandTextKind

/** 목록 줄의 머리 칸 — 굵은 점이 서는 폭이자, 하위 항목 · 부연이 한 단 들어가는 폭이다. iOS `HoyolandListText` 와 같은 값. */
private val HoyolandListIndent = 12.dp

/**
 * 목록 점 — 머리 칸([HoyolandListIndent]) 안에, 첫 줄 높이의 가운데에 선다.
 *
 * **글자가 아니라 도형으로 그린다**(10/6). 글꼴의 「•」는 13sp 에서 지름이 2dp 가 안 돼 가운뎃점과 구별이 안 됐다
 * (기기에서 확인). 노션처럼 굵게 보이도록 지름을 글자 크기의 0.38 로 잡는다 — 13sp 면 약 5dp.
 * 깊이마다 모양이 다르다: 채운 원 → 빈 원 → 채운 네모, 그 아래는 다시 처음부터([HoyolandText.dot] 과 같은 순서).
 * iOS `HoyolandListDot` 와 같은 값.
 */
@Composable
internal fun HoyolandListDot(level: Int, fontSize: TextUnit, color: Color, lineHeight: TextUnit) {
    val density = LocalDensity.current
    val line = with(density) { lineHeight.toDp() }
    val d = with(density) { (fontSize * 0.38f).toDp() }
    Box(Modifier.width(HoyolandListIndent).height(line), contentAlignment = Alignment.CenterStart) {
        when (level.coerceAtLeast(0) % 3) {
            0 -> Box(Modifier.size(d).background(color, CircleShape))
            1 -> Box(Modifier.size(d).border(d * 0.24f, color, CircleShape))
            // 네모는 같은 지름의 원보다 커 보인다 — 한 단 작게 그려 무게를 맞춘다.
            else -> Box(Modifier.size(d * 0.86f).background(color))
        }
    }
}

/**
 * 어드민에서 적은 **여러 줄 글** — 「· 항목」 · 「1. 항목」 줄을 목록으로 그린다(10/6).
 *
 * 설명 · 공지 · 받는 것처럼 `Text` 하나에 통째로 넣던 자리가 쓴다. 통째로 넣으면 목록 줄이 길어 다음 줄로
 * 넘어갈 때 **점 아래로 글자가 들어가** 어디서 항목이 바뀌는지 안 보인다. 점은 노션처럼 깊이마다 다르다 — 굵은 점 → 빈 동그라미 → 네모([HoyolandListDot]). 줄 규칙은 공유 모듈
 * [HoyolandText.lines] 가 정한다(어드민 글 칸 · iOS 와 같은 규칙).
 *
 * 글자 크기 · 색 · 줄간은 **부르는 자리의 것 그대로**다 — 목록이 생겼다고 그 자리의 글 모양이 바뀌지 않는다.
 * 목록 줄이 하나도 없으면 예전처럼 `Text` 하나로 그린다.
 *
 * 값을 오른쪽에 붙여 강조하는 줄(예매 안내 · 푸드 메뉴의 「· 이름 — 값」)은 이것이 아니라 `HoyolandRichText` 가 그린다.
 */
@Composable
internal fun HoyolandListText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    if (!HoyolandText.hasList(text)) {
        Text(text, fontSize = fontSize, fontWeight = fontWeight, color = color, lineHeight = lineHeight, modifier = modifier)
        return
    }
    val lines = remember(text) { HoyolandText.lines(text) }
    Column(modifier) {
        lines.forEach { l ->
            when (l.kind) {
                HoyolandTextKind.PARA ->
                    Text(l.text, fontSize = fontSize, fontWeight = fontWeight, color = color, lineHeight = lineHeight)
                // 빈 줄은 한 줄 높이 — `Text` 하나에 넣었을 때와 같은 간격이다.
                HoyolandTextKind.BLANK ->
                    Text(" ", fontSize = fontSize, lineHeight = lineHeight)
                HoyolandTextKind.ITEM -> Row(Modifier.padding(start = HoyolandListIndent * l.level)) {
                    HoyolandListDot(l.level, fontSize, color, lineHeight)
                    Text(
                        l.text, fontSize = fontSize, fontWeight = fontWeight, color = color, lineHeight = lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 번호 줄 — 번호는 적은 그대로("1."), 글은 번호 뒤에서 줄을 맞춘다.
                HoyolandTextKind.NUM -> Row(Modifier.padding(start = HoyolandListIndent * l.level)) {
                    Text("${l.mark} ", fontSize = fontSize, fontWeight = fontWeight, color = color, lineHeight = lineHeight)
                    Text(
                        l.text, fontSize = fontSize, fontWeight = fontWeight, color = color, lineHeight = lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
                HoyolandTextKind.SUB -> Text(
                    l.text, fontSize = fontSize, fontWeight = fontWeight, color = color, lineHeight = lineHeight,
                    modifier = Modifier.padding(start = HoyolandListIndent * l.level),
                )
            }
        }
    }
}
