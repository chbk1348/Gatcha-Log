package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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

/** 패널의 프로스티드 글래스 스타일. Haze 는 backgroundColor 필수. */
private fun glassStyle(tintAlpha: Float = 0.30f, blur: Dp = 24.dp) =
    HazeStyle(
        backgroundColor = Color.White,
        tint = HazeTint(Color.White.copy(alpha = tintAlpha)),
        blurRadius = blur,
    )

/**
 * 앱 배경 — 선택된 테마(강조색) 기반의 블러 배경.
 * 밝은 베이스 위에 강조색 블러 블롭을 깔며, [hazeState] 소스로 등록되어
 * 위에 떠 있는 카드/하단 내비가 이 배경을 진짜로 블러한다(글래스모피즘).
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
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.06f),
                            Color.White,
                            accent2.copy(alpha = 0.07f),
                        )
                    )
                ),
        ) {
            GlassBlob(300.dp, Alignment.TopEnd, 90.dp, (-80).dp, accent.copy(alpha = 0.45f), 120.dp)
            GlassBlob(260.dp, Alignment.TopStart, (-80).dp, 30.dp, accent2.copy(alpha = 0.50f), 110.dp)
            GlassBlob(320.dp, Alignment.BottomStart, (-60).dp, 100.dp, accent.copy(alpha = 0.38f), 130.dp)
            GlassBlob(240.dp, Alignment.BottomEnd, 80.dp, 60.dp, accent2.copy(alpha = 0.42f), 110.dp)
        }
        content()
    }
}

@Composable
private fun BoxScope.GlassBlob(
    size: Dp,
    align: Alignment,
    offsetX: Dp,
    offsetY: Dp,
    color: Color,
    blurRadius: Dp,
) {
    Box(
        Modifier
            .size(size)
            .align(align)
            .offset(x = offsetX, y = offsetY)
            .blur(blurRadius)
            .background(color, CircleShape),
    )
}

/**
 * 카드 — 배경을 진짜로 블러하는 프로스티드 글래스 카드(연한 보더 + 부드러운 그림자).
 * [LocalHazeState] 가 있으면 `hazeChild` 로 backdrop-blur 적용.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val haze = LocalHazeState.current
    val styled = modifier
        .shadow(2.dp, shape, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
        .clip(shape)
        .then(
            if (haze != null) {
                Modifier.hazeChild(state = haze, style = glassStyle())
            } else {
                Modifier.background(Color.White)
            }
        )
        .border(1.dp, DividerColor, shape)
    Box(styled, content = content)
}

/** 임의의 표면을 글래스 패널로: 배경 블러 + 틴트. (Haze 소스 필요) */
fun Modifier.glassPanel(state: HazeState, shape: Shape, tintAlpha: Float = 0.30f, blur: Dp = 24.dp): Modifier =
    this.clip(shape).hazeChild(
        state = state,
        style = HazeStyle(
            backgroundColor = Color.White,
            tint = HazeTint(Color.White.copy(alpha = tintAlpha)),
            blurRadius = blur,
        ),
    )
