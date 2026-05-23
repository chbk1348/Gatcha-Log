package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/** 카드/패널이 참조하는 배경 블러용 HazeState. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 카드 표면색 — 거의 불투명한 흰색(가독성·성능). 카드는 더 이상 backdrop-blur 를 쓰지 않는다. */
private val CardSurface = Color(0xFFFCFCFE)

/**
 * 앱 배경 — 선택된 테마(강조색) 기반의 단순 블러 배경.
 * 도형(블롭) 없이 부드러운 테마색 그라데이션만 깔며, [hazeState] 소스로 등록되어
 * 위에 떠 있는 카드/하단 내비가 이 배경을 블러한다.
 */
@Composable
fun GlassBackground(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .haze(hazeState)
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
 * 가독성·스크롤 성능을 위해 backdrop-blur(hazeChild) 를 쓰지 않는다(다중 블러로 인한 스크롤 끊김 방지).
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

/** 임의의 표면을 글래스 패널로: 배경 블러 + 틴트. (Haze 소스 필요) */
fun Modifier.glassPanel(state: HazeState, shape: Shape, tintAlpha: Float = 0.55f, blur: Dp = 20.dp): Modifier =
    this.clip(shape).hazeChild(
        state = state,
        style = HazeStyle(
            backgroundColor = Color.White,
            tint = HazeTint(Color.White.copy(alpha = tintAlpha)),
            blurRadius = blur,
        ),
    )
