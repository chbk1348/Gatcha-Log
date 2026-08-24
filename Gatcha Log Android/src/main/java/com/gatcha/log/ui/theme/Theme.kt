package com.gatcha.log.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.gatcha.log.R
import kotlinx.coroutines.launch

/**
 * 전역 **행간** — Pretendard 가 정한 값 그대로(ascent 1950 + descent 494 ÷ upem 2048).
 *
 * iOS(SwiftUI·CoreText)는 글꼴이 정한 이 값을 그냥 쓴다. Compose 는 지정이 없으면 같은 값을
 * 쓰지만, 중간에 M3 타이포그래피 스타일이 한 번이라도 끼면 거기 박힌 절대값(bodyLarge 24sp 등)이
 * 상속돼 **글자 크기와 무관하게** 줄이 벌어진다. 화면마다 `lineHeight` 를 손으로 눌러 상쇄하던
 * 자리가 앱 전체에 열댓 곳 있었고, 그 값들이 1.35~1.5em 로 제각각이라 iOS 보다 눈에 띄게
 * 넓었다(2026-08-18 지적).
 *
 * `em` 으로 두면 글자 크기를 따라가므로 크기가 바뀌어도 다시 어긋나지 않는다.
 * 여기보다 넓혀야 하는 곳(공지 본문처럼 iOS 가 `lineSpacing` 을 주는 자리)만 개별 지정한다.
 */
val GlgLineHeight = 1.193.em

/** 전역 글꼴 — Pretendard. 모든 Text 가 LocalTextStyle 로 상속(개별 fontFamily 미지정). */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/**
 * M3 컴포넌트용 타이포그래피 — 기본 스타일에 서체만 Pretendard 로 바꾼다.
 *
 * [LocalTextStyle] 은 스타일을 상속하는 `Text` 만 덮는다. M3 컴포넌트 일부(DatePickerDialog·
 * TextButton 등)는 내부에서 `MaterialTheme.typography` 를 직접 깔기 때문에, 이걸 넘기지 않으면
 * 그 경로가 `FontFamily.Default` 로 떨어져 **기기의 글꼴 스타일 설정(삼성 '글꼴' 등)이 그대로 들어온다.**
 * 크기·자간·행간은 M3 기본값을 그대로 두고 서체와 **폰트 패딩 해제**만 고정한다
 * (해제 이유는 [GatchaLogTheme] 의 `LocalTextStyle` 주석 참고 — 이 경로만 빠지면
 * M3 컴포넌트 글자만 혼자 아래로 처진다).
 */
private fun TextStyle.pretendard(): TextStyle = copy(
    fontFamily = Pretendard,
    // M3 기본 스타일에는 절대 행간(bodyLarge 24sp 등)이 박혀 있다 — 이 경로로 들어온 글자만
    // 혼자 벌어지지 않게 여기서도 [GlgLineHeight] 로 바꾼다.
    lineHeight = GlgLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val PretendardTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.pretendard(),
        displayMedium = displayMedium.pretendard(),
        displaySmall = displaySmall.pretendard(),
        headlineLarge = headlineLarge.pretendard(),
        headlineMedium = headlineMedium.pretendard(),
        headlineSmall = headlineSmall.pretendard(),
        titleLarge = titleLarge.pretendard(),
        titleMedium = titleMedium.pretendard(),
        titleSmall = titleSmall.pretendard(),
        bodyLarge = bodyLarge.pretendard(),
        bodyMedium = bodyMedium.pretendard(),
        bodySmall = bodySmall.pretendard(),
        labelLarge = labelLarge.pretendard(),
        labelMedium = labelMedium.pretendard(),
        labelSmall = labelSmall.pretendard(),
    )
}

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
        primary = accent.color.toColor(),
        secondary = accent.secondary.toColor(),
        background = Color.White,
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
    )

    // 기기 글꼴 크기(접근성 폰트 스케일)와 무관하게 앱 자체 크기를 유지 → 모든 화면 레이아웃 일관성 확보.
    val systemDensity = LocalDensity.current
    val fixedDensity = remember(systemDensity.density) { Density(systemDensity.density, fontScale = 1f) }

    // 저사양·접근성·절전 모션 감속 + 전역 공유 시머 클럭(스켈레톤 박스 1클럭 공유)
    val reduceMotion = rememberReduceMotion()
    val shimmerPhase = rememberShimmerPhase(reduceMotion)

    CompositionLocalProvider(
        LocalAccent provides accent.color.toColor(),
        LocalAccentSecondary provides accent.secondary.toColor(),
        LocalReduceMotion provides reduceMotion,
        LocalShimmerPhase provides shimmerPhase,
        // 회색 박스/리플 대신 "눌린 느낌"(축소) 인디케이션을 전역 적용
        LocalIndication provides PressScaleIndication,
        LocalRippleConfiguration provides null,
        // 기기 폰트 크기 영향 제거(고정 1.0)
        LocalDensity provides fixedDensity,
        // 전역 글꼴 Pretendard — 개별 fontFamily 미지정 Text 전부 상속
        //
        // 폰트 패딩도 여기서 끈다. Pretendard 는 한글용 ascent·descent 가 커서, Compose 기본값인
        // `includeFontPadding = true` 가 그 위에 여백을 **한 겹 더** 얹는다. 그러면 같은 크기·같은
        // 여백을 지정해도 Android 쪽만 글자 상자가 높아진다 — iOS(SwiftUI)는 이 여백이 없다.
        //
        // 이 차이가 이번까지 다섯 번 같은 증상으로 돌아왔다: 칩이 커 보임 · 세트 효과 숫자가 처짐 ·
        // 주간 날짜칸이 튀어나옴 · D-day 알약이 두꺼움 · 카드 안 문장 간격이 벌어짐. 자리마다
        // 눈대중으로 패딩을 깎아 맞추면 새 화면을 만들 때마다 같은 자리에서 또 어긋난다.
        // 원인 한 곳에서 끊는다.
        LocalTextStyle provides LocalTextStyle.current.copy(
            fontFamily = Pretendard,
            lineHeight = GlgLineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            // Trim 은 하지 않는다 — 문단(여러 줄)에서 첫 줄 위·끝 줄 아래를 깎으면 오히려
            // iOS 보다 좁아진다. 필요한 건 '패딩 제거'까지고, 줄 높이 자체는 글꼴 값을 쓴다.
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            // M3 컴포넌트가 LocalTextStyle 을 우회해 시스템 글꼴로 떨어지는 것을 막는다.
            typography = PretendardTypography,
            content = content,
        )
    }
}
