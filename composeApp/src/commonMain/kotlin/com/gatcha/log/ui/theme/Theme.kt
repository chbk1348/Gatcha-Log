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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
        // 기기 폰트 크기 영향 제거(고정 1.0) — iOS 다이내믹 타입·Android 글꼴 크기 설정 모두
        FixedFontScale {
            MaterialTheme(
                colorScheme = colorScheme,
                content = content,
            )
        }
    }
}

/**
 * 기기 글꼴 크기(접근성 폰트 스케일)와 무관하게 앱 자체 크기를 유지하는 래퍼 — 폰트 스케일 1.0 고정.
 *
 * [GatchaLogTheme] 이 메인 콘텐츠에 적용하지만, Compose `Dialog`/`ModalBottomSheet` 는 별도
 * 윈도우(레이어)에 그려져 루트에서 시스템 폰트 스케일을 다시 가져온다 — 테마의 고정이 안까지 미치지 않는다.
 * 따라서 다이얼로그/시트류 콘텐츠 루트는 반드시 이 래퍼로 다시 감싸야 한다.
 * (iOS: 설정 ▸ 디스플레이 및 밝기 ▸ 텍스트 크기 / Android: 글꼴 크기 설정 모두 무시)
 */
@Composable
fun FixedFontScale(content: @Composable () -> Unit) {
    val systemDensity = LocalDensity.current
    val fixedDensity = remember(systemDensity.density) { Density(systemDensity.density, fontScale = 1f) }
    CompositionLocalProvider(LocalDensity provides fixedDensity, content = content)
}
