package com.gatcha.log.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/**
 * 앱 전체 강조색. MyPage 테마 선택에 따라 바뀌며, 화면들은 [LocalAccent] 를 통해 읽는다.
 */
val LocalAccent = staticCompositionLocalOf { MintPrimary }
val LocalAccentSecondary = staticCompositionLocalOf { MintSecondary }

/**
 * 회색 박스/리플 대신, 누르는 동안 콘텐츠가 살짝 작아졌다(0.95) 떼면 돌아오는 "눌린 느낌" 인디케이션.
 * 모든 `Modifier.clickable` 에 전역 적용된다.
 */
object PressScaleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressScaleNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -2
}

private const val PRESSED_SCALE = 0.95f

private class PressScaleNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {

    private val scaleAnim = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                }
                val target = if (presses.isNotEmpty()) PRESSED_SCALE else 1f
                launch {
                    scaleAnim.animateTo(target, tween(durationMillis = if (target < 1f) 70 else 140))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = scaleAnim.value
        if (s >= 0.999f) {
            drawContent()
        } else {
            val content = this
            scale(s, s) { content.drawContent() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatchaLogTheme(
    accentIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val accent = AccentPalette.getOrElse(accentIndex) { AccentPalette[0] }

    val colorScheme = lightColorScheme(
        primary = accent.color,
        secondary = accent.secondary,
        background = Color.White,
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
    )

    CompositionLocalProvider(
        LocalAccent provides accent.color,
        LocalAccentSecondary provides accent.secondary,
        // 회색 박스/리플 대신 "눌린 느낌"(축소) 인디케이션을 전역 적용
        LocalIndication provides PressScaleIndication,
        LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
