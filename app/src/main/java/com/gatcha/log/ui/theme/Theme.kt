package com.gatcha.log.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode

/**
 * 앱 전체 강조색. MyPage 테마 선택에 따라 바뀌며, 화면들은 [LocalAccent] 를 통해 읽는다.
 */
val LocalAccent = staticCompositionLocalOf { MintPrimary }
val LocalAccentSecondary = staticCompositionLocalOf { MintSecondary }

/** 클릭 시 나타나는 기본 회색 인디케이션(회색 박스)을 제거하는 no-op indication. */
object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
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
        // 모든 화면에서 클릭 시 회색 박스/리플 제거
        LocalIndication provides NoIndication,
        LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
