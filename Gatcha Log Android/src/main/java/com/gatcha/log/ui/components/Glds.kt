package com.gatcha.log.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
//  GLDS — 앱 공용 버튼 · 입력필드 규격 (2026-09-30)
//  iOS `DesignSystem/GlassButton.swift` · `PillField.swift` 와 값이 같다. 규격표는 버튼 카탈로그 아티팩트.
// ============================================================

/** 버튼 모양. 색은 전부 테마 강조색에서 나온다(Inverse · Danger 만 고정). */
enum class GldsVariant { Primary, Secondary, Neutral, Inverse, Danger, OnTint, Text }

/** 버튼 크기 — 높이 · 반경 · 글자 · 아이콘 · 좌우 여백(내용 폭일 때). */
enum class GldsSize(val height: Dp, val radius: Dp, val font: TextUnit, val icon: Dp, val gap: Dp, val padH: Dp) {
    L(50.dp, 16.dp, 15.sp, 18.dp, 8.dp, 20.dp),
    M(44.dp, 16.dp, 15.sp, 17.dp, 7.dp, 18.dp),
    S(36.dp, 12.dp, 13.sp, 15.dp, 6.dp, 14.dp),
    XS(28.dp, 9.dp, 12.sp, 14.dp, 4.dp, 12.dp),
}

private val GldsDisabledBg = Color(0xFFD8D8DE)
private val GldsNeutralBg = Color(0xFFECEFF4)

/**
 * GLDS 버튼. 폭은 호출부가 정한다(`fillMaxWidth` · `weight`) — 안 주면 내용 폭 + [GldsSize.padH].
 * 누르면 0.97배로 줄고 면이 한 단 진해진다. [loading] 이면 글자 대신 스피너.
 */
@Composable
fun GldsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GldsVariant = GldsVariant.Primary,
    size: GldsSize = GldsSize.M,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val accent = LocalAccent.current
    val deep = LocalAccentDeep.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val (base, fg) = when (variant) {
        GldsVariant.Primary -> accent to Color.White
        GldsVariant.Secondary -> accent.copy(alpha = 0.12f) to deep
        GldsVariant.Neutral -> GldsNeutralBg to TextPrimary
        GldsVariant.Inverse -> TextPrimary to Color.White
        GldsVariant.Danger -> DangerBackground to DangerText
        GldsVariant.OnTint -> Color.White to deep
        GldsVariant.Text -> Color.Transparent to accent
    }
    val fill = when {
        !enabled && variant != GldsVariant.Text -> GldsDisabledBg
        !pressed -> base
        variant == GldsVariant.Secondary -> accent.copy(alpha = 0.20f)
        variant == GldsVariant.Text -> accent.copy(alpha = 0.08f)
        else -> lerp(base, Color.Black, 0.08f)
    }
    val bg by animateColorAsState(fill, label = "gldsBg")
    val content = if (enabled) fg else TextSecondary
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "gldsScale")
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
            // 글자는 **자르지 않는다**(9/30) — 폭이 좁게 정해진 버튼(호요랜드 히어로 2 : 1 : 1)에서 좌우 여백을 챙기느라
            // 한 글자로 잘렸다. 아이콘까지 안 들어가면 아이콘을 빼고, 그래도 좁으면 여백 쪽으로 넘쳐 가운데를 지킨다(iOS 와 같다).
            val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { size.gap.roundToPx() }
            androidx.compose.ui.layout.Layout(content = {
                if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size.icon))
                Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = size.font, maxLines = 1, softWrap = false)
            }) { measurables, c ->
                val free = c.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
                val placeables = measurables.map { it.measure(free) }
                val label = placeables.last()
                val iconP = if (icon != null) placeables.first() else null
                val withIcon = iconP != null && iconP.width + gapPx + label.width <= c.maxWidth
                val full = if (withIcon) iconP!!.width + gapPx + label.width else label.width
                val w = minOf(full, c.maxWidth)
                val h = placeables.maxOf { it.height }
                layout(w, h) {
                    var x = (w - full) / 2   // 넘치면 음수 — 양쪽 여백으로 고르게 넘친다
                    if (withIcon) { iconP!!.placeRelative(x, (h - iconP.height) / 2); x += iconP.width + gapPx }
                    label.placeRelative(x, (h - label.height) / 2)
                }
            }
        }
    }
}

/** 입력필드 크기 — M 은 폼 기본, S 는 목록 행 안의 짧은 숫자칸. */
enum class GldsFieldSize(val height: Dp, val radius: Dp, val font: TextUnit, val padH: Dp) {
    M(48.dp, 14.dp, 16.sp, 14.dp),
    S(38.dp, 12.dp, 14.sp, 12.dp),
}

private val GldsFieldBg = Color(0xFFF5F8F8)
private val GldsFieldPlaceholder = Color(0xFFA7B1AE)

/**
 * GLDS 입력필드 — 채운 면(#F5F8F8), 포커스 때 흰 면 + 강조색 1.5 테두리, 오류면 빨강 테두리 + 아래 문구.
 * [suffix] 는 값 뒤에 붙는 단위(「원」), [trailingIcon] 은 오른쪽 끝 아이콘, [onClick] 을 주면 누르는 필드(날짜 등).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GldsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    size: GldsFieldSize = GldsFieldSize.M,
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
        when { error != null -> DangerText; focused -> accent; else -> Color.Transparent }, label = "gldsFieldBorder",
    )
    val bg by animateColorAsState(if (focused || error != null) Color.White else GldsFieldBg, label = "gldsFieldBg")
    val shape = RoundedCornerShape(size.radius)
    // 포커스 · 키보드가 뜨면 **입력칸 전체**(라벨 · 테두리 · 도움말)를 보이는 곳까지 스크롤한다(9/30).
    // 텍스트필드 기본 동작은 커서가 있는 글자 줄만 보이게 해서, 칸 테두리가 하단 바에 붙거나 가렸다.
    val bring = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(focused, imeVisible) {
        if (focused) {
            delay(120)   // 키보드가 자리를 잡고 스크롤 영역이 줄어든 뒤
            bring.bringIntoView()
        }
    }
    val style = LocalTextStyle.current.copy(
        color = TextPrimary, fontSize = size.font, textAlign = textAlign,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )

    Column(modifier.bringIntoViewRequester(bring)) {
        label?.let { GldsFieldLabel(it) }
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
                    Text(placeholder, style = style.copy(color = GldsFieldPlaceholder), modifier = Modifier.fillMaxWidth())
                }
                // 누르는 필드(날짜 등)는 입력 위젯을 두지 않는다 — 비활성 텍스트필드가 탭을 먹어 필드가 안 눌렸다.
                if (onClick != null) Text(value, style = style, maxLines = 1, modifier = Modifier.fillMaxWidth())
                else BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
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
fun GldsFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = modifier.padding(bottom = 6.dp))
}

/**
 * 키보드가 올라온 채 입력칸 밖을 누르면 포커스를 풀어 내린다. 앱 루트와 별도 창(다이얼로그)에 한 번씩 건다.
 * 버튼 · 입력칸처럼 자기 탭을 먹는 요소 위에서는 발동하지 않는다(iOS 는 iOSApp.swift 의 KeyboardDismissTap).
 */
fun Modifier.dismissKeyboardOnTap(): Modifier = composed {
    val focus = LocalFocusManager.current
    pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
}

/**
 * GLDS 칩(9/30) — 필터 · 선택용 알약. iOS `GldsChip` · `GldsChipLabel` 과 같은 값.
 * 높이 32 · 좌우 12 · 13 Bold. 기본 흰 면 + 1 #E3E5EA, **선택은 면을 채우지 않고** 강조색 1.5 테두리 + deep 글자
 * (iOS 메뉴 모프 때 채운 면이 번져 보였다 — 두 플랫폼 같은 모양으로 맞춘다).
 * [dropdown] 은 ▾(메뉴가 열리는 칩), [removable] 은 ✕(눌러 해제).
 */
@Composable
fun GldsChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dropdown: Boolean = false,
    removable: Boolean = false,
    /** 선택색 — 게임 칩처럼 칸 자체가 색을 갖는 자리. null 이면 강조색(글자는 deep). */
    color: Color? = null,
) {
    val accent = color ?: LocalAccent.current
    val deep = color ?: LocalAccentDeep.current
    val border by animateColorAsState(if (selected) accent else GldsChipLine, label = "gldsChipBorder")
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(32.dp)
            .clip(shape)
            .background(Color.White)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val fg = if (selected) deep else TextPrimary
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
        if (dropdown) Icon(Icons.Default.KeyboardArrowDown, null, tint = if (selected) deep else TextSecondary, modifier = Modifier.size(16.dp))
        if (removable) Icon(Icons.Default.Close, null, tint = fg, modifier = Modifier.size(14.dp))
    }
}

private val GldsChipLine = Color(0xFFE3E5EA)

/**
 * 다이얼로그 · 바텀시트 창에서도 상단 · 하단 시스템 바 아이콘을 어둡게(9/30) — 별도 창은 액티비티 설정을 물려받지 않아
 * 다이얼로그가 뜨면 상단바 아이콘이 흰색으로 바뀌었다. 창 content 안에서 한 번 부른다.
 */
@Composable
fun LightSystemBarsInWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}
