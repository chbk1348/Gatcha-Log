package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 카드 표면색 — 거의 불투명한 흰색(가독성·성능). 카드는 backdrop-blur 를 쓰지 않는다. */
private val CardSurface = Color(0xFFFCFCFE)

/** 앱 배경 — 흰색 단색(전역). 강조색 그라데이션 제거. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Color.White))
        content()
    }
}

/**
 * 카드 — 솔리드(거의 불투명) 흰색 카드(연한 보더, 평평).
 * 가독성·스크롤 성능을 위해 backdrop-blur 를 쓰지 않는다(다중 블러로 인한 스크롤 끊김 방지).
 * iOS(glgGlass: 흰 배경 + 아웃라인, 그림자 없음)와 동일하게 그림자를 두지 않는다(파리티).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val styled = modifier
        .clip(shape)
        .background(CardSurface)
        .border(1.dp, Color.Black.copy(alpha = 0.08f), shape)
    Box(styled, content = content)
}
