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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary

/** 카드 표면색 — 거의 불투명한 흰색(가독성·성능). 카드는 backdrop-blur 를 쓰지 않는다. */
private val CardSurface = Color(0xFFFCFCFE)

/**
 * 앱 배경 — 선택된 테마(강조색) 기반의 단순 그라데이션.
 * 도형(블롭)·라이브 블러 없이 부드러운 테마색만 깔아 스크롤이 가볍다.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.12f),
                            Color.White,
                            accent2.copy(alpha = 0.08f),
                        )
                    )
                ),
        )
        content()
    }
}

/**
 * 카드 — 솔리드(거의 불투명) 흰색 카드(연한 보더 + 부드러운 그림자).
 * 가독성·스크롤 성능을 위해 backdrop-blur 를 쓰지 않는다(다중 블러로 인한 스크롤 끊김 방지).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val styled = modifier
        .shadow(3.dp, shape, clip = false, ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000))
        .clip(shape)
        .background(CardSurface)
        .border(1.dp, DividerColor, shape)
    Box(styled, content = content)
}
