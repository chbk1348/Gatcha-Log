package com.gatcha.log.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.theme.DangerBackground
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

// ============================================================
//  ODS — 앱 공용 버튼 · 입력필드 규격 (2026-09-30)
//  iOS `DesignSystem/GlassButton.swift` · `PillField.swift` 와 값이 같다. 규격표는 버튼 카탈로그 아티팩트.
// ============================================================

/** 버튼 모양. 색은 전부 테마 강조색에서 나온다(Inverse · Danger 만 고정). */
enum class OdsVariant { Primary, Secondary, Neutral, Inverse, Danger, OnTint, Text }

/** 버튼 크기 — 높이 · 반경 · 글자 · 아이콘 · 좌우 여백(내용 폭일 때). */
enum class OdsSize(val height: Dp, val radius: Dp, val font: TextUnit, val icon: Dp, val gap: Dp, val padH: Dp) {
    L(50.dp, 16.dp, 15.sp, 18.dp, 8.dp, 20.dp),
    M(44.dp, 16.dp, 15.sp, 17.dp, 7.dp, 18.dp),
    S(36.dp, 12.dp, 13.sp, 15.dp, 6.dp, 14.dp),
    XS(28.dp, 9.dp, 12.sp, 14.dp, 4.dp, 12.dp),
}

private val OdsDisabledBg = Color(0xFFD8D8DE)
private val OdsNeutralBg = Color(0xFFECEFF4)

/**
 * ODS 버튼. 폭은 호출부가 정한다(`fillMaxWidth` · `weight`) — 안 주면 내용 폭 + [OdsSize.padH].
 * 누르면 0.97배로 줄고 면이 한 단 진해진다. [loading] 이면 글자 대신 스피너.
 */
@Composable
fun OdsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OdsVariant = OdsVariant.Primary,
    size: OdsSize = OdsSize.M,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val accent = LocalAccent.current
    val deep = LocalAccentDeep.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val (base, fg) = when (variant) {
        OdsVariant.Primary -> accent to Color.White
        OdsVariant.Secondary -> accent.copy(alpha = 0.12f) to deep
        OdsVariant.Neutral -> OdsNeutralBg to TextPrimary
        OdsVariant.Inverse -> TextPrimary to Color.White
        OdsVariant.Danger -> DangerBackground to DangerText
        OdsVariant.OnTint -> Color.White to deep
        OdsVariant.Text -> Color.Transparent to accent
    }
    val fill = when {
        !enabled && variant != OdsVariant.Text -> OdsDisabledBg
        !pressed -> base
        variant == OdsVariant.Secondary -> accent.copy(alpha = 0.20f)
        variant == OdsVariant.Text -> accent.copy(alpha = 0.08f)
        else -> lerp(base, Color.Black, 0.08f)
    }
    val bg by animateColorAsState(fill, label = "odsBg")
    val content = if (enabled) fg else TextSecondary
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "odsScale")
    val shape = RoundedCornerShape(size.radius)

    Box(
        modifier = modifier
            .height(size.height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) { onClick() }
            .padding(horizontal = size.padH),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(size.icon))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size.gap)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size.icon))
                Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = size.font, maxLines = 1)
            }
        }
    }
}

/** 입력필드 크기 — M 은 폼 기본, S 는 목록 행 안의 짧은 숫자칸. */
enum class OdsFieldSize(val height: Dp, val radius: Dp, val font: TextUnit, val padH: Dp) {
    M(48.dp, 14.dp, 16.sp, 14.dp),
    S(38.dp, 12.dp, 14.sp, 12.dp),
}

private val OdsFieldBg = Color(0xFFF5F8F8)
private val OdsFieldPlaceholder = Color(0xFFA7B1AE)

/**
 * ODS 입력필드 — 채운 면(#F5F8F8), 포커스 때 흰 면 + 강조색 1.5 테두리, 오류면 빨강 테두리 + 아래 문구.
 * [suffix] 는 값 뒤에 붙는 단위(「원」), [trailingIcon] 은 오른쪽 끝 아이콘, [onClick] 을 주면 누르는 필드(날짜 등).
 */
@Composable
fun OdsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    size: OdsFieldSize = OdsFieldSize.M,
    suffix: String? = null,
    trailingIcon: ImageVector? = null,
    helper: String? = null,
    error: String? = null,
    textAlign: TextAlign = TextAlign.Start,
    bold: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when { error != null -> DangerText; focused -> accent; else -> Color.Transparent }, label = "odsFieldBorder",
    )
    val bg by animateColorAsState(if (focused || error != null) Color.White else OdsFieldBg, label = "odsFieldBg")
    val shape = RoundedCornerShape(size.radius)
    val style = LocalTextStyle.current.copy(
        color = TextPrimary, fontSize = size.font, textAlign = textAlign,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )

    Column(modifier) {
        label?.let { OdsFieldLabel(it) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier.height(size.height) else Modifier)
                .clip(shape)
                .background(bg)
                .border(1.5.dp, borderColor, shape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = size.padH, vertical = if (singleLine) 0.dp else 12.dp),
        ) {
            Box(Modifier.weight(1f), contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = style.copy(color = OdsFieldPlaceholder), modifier = Modifier.fillMaxWidth())
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && onClick == null,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    textStyle = style,
                    cursorBrush = SolidColor(accent),
                    interactionSource = interaction,
                )
            }
            if (suffix != null && value.isNotEmpty()) {
                Text(suffix, style = style.copy(color = TextSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 4.dp))
            }
            trailingIcon?.let {
                Icon(it, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
        }
        (error ?: helper)?.let {
            Text(it, fontSize = 12.sp, color = if (error != null) DangerText else TextSecondary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        }
    }
}

/** 입력필드 위 라벨 — 13 SemiBold #6C727A, 아래 6. */
@Composable
fun OdsFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = modifier.padding(bottom = 6.dp))
}
