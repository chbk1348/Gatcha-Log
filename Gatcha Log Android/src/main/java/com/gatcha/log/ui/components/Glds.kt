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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
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

/**
 * 입력필드 크기 — M 은 폼 기본, S 는 목록 행 안의 짧은 숫자칸.
 * GLDS 2.1 입력필드(1-C) 에서 L 은 두지 않기로 했다 — 큰 입력은 금액 카드([GldsAmountCard])가 맡는다.
 * 값의 이름은 `size/field/m` · `radius/field/m` · `type/field/m` · `space/field/pad-m`(S 도 같은 꼴).
 */
enum class GldsFieldSize(val height: Dp, val radius: Dp, val font: TextUnit, val padH: Dp) {
    M(48.dp, 14.dp, 16.sp, 14.dp),
    S(38.dp, 12.dp, 14.sp, 12.dp),
}

/**
 * 입력필드 값 — GLDS 2.1 입력필드(7-A) 의 역할 경로 이름을 그대로 옮겼다(`color/field/bg` → [Bg]). iOS `GldsField` 와 이름 · 값이 같다.
 * 크기마다 다른 값은 [GldsFieldSize] 가, 글자 · 오류 · 강조색(`color/text/primary` · `secondary` · `color/danger/text` ·
 * `color/accent/primary`)은 앱 공용 색([TextPrimary] · [TextSecondary] · [DangerText] · [LocalAccent])이 갖는다.
 */
object GldsField {
    /** `color/field/bg` — 평소 면. */
    val Bg = Color(0xFFF5F8F8)
    /** `color/field/bg-focused` — 포커스 · 오류 때 면. */
    val BgFocused = Color.White
    /** Disabled 면(2-B) — 버튼 Neutral 면과 같은 색이라 「면이 바뀌면 못 누른다」가 버튼과 같은 말로 읽힌다. 7-A 표에는 이름이 없다. */
    val BgDisabled = GldsNeutralBg
    /** `color/field/placeholder` — 자리표시자. Disabled 아이콘 · 지우기 ✕ 도 이 색이다(2-B · 5-A). */
    val Placeholder = Color(0xFFA7B1AE)
    /** `space/field/label` — 라벨 아래. */
    val SpaceLabel = 6.dp
    /** `space/field/fix` — Prefix 뒤 · Suffix 앞. */
    val SpaceFix = 4.dp
    /** `space/field/icon` — Leading Icon 뒤 · 지우기 ✕ · Trailing Icon 앞. */
    val SpaceIcon = 8.dp
    /** `type/field/label` — 13 SemiBold. */
    val TypeLabel = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    /** `type/field/helper` — 12. */
    val TypeHelper = TextStyle(fontSize = 12.sp)
}

/**
 * 입력필드 안에 넣을 수 있는 아이콘 목록(GLDS 2.1 입력필드 6-A) — 피그마 Instance swap 과 같은 이름을 쓴다.
 * 그림은 플랫폼 것(Material ↔ iOS SF Symbol)이고, 목록 밖 아이콘은 가이드에 먼저 올린 뒤 여기에 더한다. iOS `GldsIcon` 과 같다.
 */
enum class GldsIcon(val vector: ImageVector) {
    Calendar(Icons.Default.CalendarToday),   // iOS calendar
    Search(Icons.Default.Search),            // iOS magnifyingglass
    Clear(Icons.Default.Cancel),             // iOS xmark.circle.fill — 지우기 ✕(5-A)
}

/**
 * GLDS 입력필드 — 채운 면(#F5F8F8), 포커스 때 흰 면 + 강조색 1.5 테두리, 오류면 빨강 테두리 + 아래 문구.
 * 한 줄 순서는 [leadingIcon] → [prefix] → 값 → [suffix] → 지우기 ✕ → [trailingIcon].
 * [prefix] · [suffix] 는 값 앞뒤에 붙는 글자(「#」 · 「원」)로 값이 있을 때만 보인다. [onClick] 을 주면 누르는 필드(날짜 등).
 * [clearable] 이면 포커스 중이고 값이 있을 때 ✕ 를 띄워 값을 비운다(5-A).
 * [enabled] 가 false 면 면 #ECEFF4 · 값 #6C727A · 아이콘 #A7B1AE 로 바뀌고, [error] 보다 먼저라 오류 대신 [helper] 를 보인다(2-B).
 * [secure] 면 값을 점으로 가린다(토큰) — iOS `GldsTextField(secure:)` 와 같다.
 * [visualTransformation] 은 값은 그대로 두고 보이는 글자만 바꾼다(금액 쉼표 — [ThousandsTransformation]). [secure] 가 먼저다.
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
    leadingIcon: GldsIcon? = null,
    prefix: String? = null,
    suffix: String? = null,
    clearable: Boolean = false,
    trailingIcon: GldsIcon? = null,
    helper: String? = null,
    error: String? = null,
    textAlign: TextAlign = TextAlign.Start,
    bold: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    secure: Boolean = false,
    onClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value && enabled
    // Disabled 가 오류보다 먼저다(2-B) — 고칠 수 없는 칸에 오류를 띄우면 할 수 있는 일이 없다.
    val shownError = error?.takeIf { enabled }
    val borderColor by animateColorAsState(
        when { shownError != null -> DangerText; focused -> accent; else -> Color.Transparent }, label = "gldsFieldBorder",
    )
    val bg by animateColorAsState(
        when { !enabled -> GldsField.BgDisabled; focused || shownError != null -> GldsField.BgFocused; else -> GldsField.Bg },
        label = "gldsFieldBg",
    )
    val iconTint = if (enabled) TextSecondary else GldsField.Placeholder
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
        color = if (enabled) TextPrimary else TextSecondary, fontSize = size.font, textAlign = textAlign,
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
                .then(if (onClick != null) Modifier.clickable(enabled = enabled) { onClick() } else Modifier)
                .padding(horizontal = size.padH, vertical = if (singleLine) 0.dp else 12.dp),
        ) {
            leadingIcon?.let { GldsFieldIcon(it, iconTint, Modifier.padding(end = GldsField.SpaceIcon)) }
            // Prefix 는 Suffix 의 거울이다(4-A) — 값이 비면 숨겨 placeholder 와 한 문장처럼 붙지 않게 한다.
            if (prefix != null && value.isNotEmpty()) {
                Text(prefix, style = style.copy(color = TextSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(end = GldsField.SpaceFix))
            }
            Box(Modifier.weight(1f), contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = style.copy(color = GldsField.Placeholder), modifier = Modifier.fillMaxWidth())
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
                    // 가리는 칸은 키보드도 비밀번호용으로 — 자동 완성 · 학습에 값이 남지 않게 한다.
                    keyboardOptions = if (secure) keyboardOptions.copy(keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else keyboardOptions,
                    visualTransformation = if (secure) PasswordVisualTransformation() else visualTransformation,
                    textStyle = style,
                    cursorBrush = SolidColor(accent),
                    interactionSource = interaction,
                )
            }
            if (suffix != null && value.isNotEmpty()) {
                Text(suffix, style = style.copy(color = TextSecondary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = GldsField.SpaceFix))
            }
            // ✕ 는 Trailing Icon 앞이다(5-A) — 나타나도 오른쪽 끝 아이콘 자리가 흔들리지 않는다.
            if (clearable && focused && value.isNotEmpty()) {
                Box(
                    Modifier
                        .padding(start = GldsField.SpaceIcon)
                        // 누름 영역은 44(버튼 M 높이)인데 자리는 그림(18)만큼만 차지한다 — 44 상자를 18 자리 가운데에 겹쳐 놓는다.
                        .layout { measurable, _ ->
                            val hit = 44.dp.roundToPx()
                            val slot = 18.dp.roundToPx()
                            val placeable = measurable.measure(Constraints.fixed(hit, hit))
                            layout(slot, slot) { placeable.place((slot - hit) / 2, (slot - hit) / 2) }
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onValueChange("") },
                    contentAlignment = Alignment.Center,
                ) { GldsFieldIcon(GldsIcon.Clear, GldsField.Placeholder) }
            }
            trailingIcon?.let { GldsFieldIcon(it, iconTint, Modifier.padding(start = GldsField.SpaceIcon)) }
        }
        (shownError ?: helper)?.let {
            Text(
                it, style = LocalTextStyle.current.merge(GldsField.TypeHelper), color = if (shownError != null) DangerText else TextSecondary,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }
    }
}

/** 칸 안 아이콘 — Leading · Trailing · 지우기 ✕ 모두 18(GLDS 2.1 입력필드 8-A). iOS 는 SF Symbol 15pt 를 18 틀에 담는다. */
@Composable
private fun GldsFieldIcon(icon: GldsIcon, tint: Color, modifier: Modifier = Modifier) {
    Icon(icon.vector, contentDescription = null, tint = tint, modifier = modifier.size(18.dp))
}

/** 입력필드 위 라벨 — 13 SemiBold #6C727A, 아래 6. Disabled 여도 색은 그대로다(2-B). */
@Composable
fun GldsFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = LocalTextStyle.current.merge(GldsField.TypeLabel), color = TextSecondary,
        modifier = modifier.padding(bottom = GldsField.SpaceLabel),
    )
}

/**
 * 숫자만 든 금액에 세 자리 쉼표를 **보이는 글자에만** 넣는다 — 값(숫자)은 그대로다.
 *
 * 커서 위치도 옮겨 준다. 쉼표가 끼어든 만큼 앞뒤로 밀어야 지우기·중간 입력이 제자리에서 된다.
 *
 * 금액 칸은 전부 이것을 쓴다(27.52.0 — 지출 금액 · 온보딩 ③/예산 관리의 월 예산 · 게임별 한도). 쉼표를 넣은
 * 문자열을 값으로 되돌려 넣으면 자리수가 늘 때 커서가 쉼표만큼 밀려 숫자가 섞였다(1·2·3·4·5 → 12,354).
 * 값은 **숫자만** 넘겨야 한다.
 */
object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val n = digits.length
        // 원본 i 번째 글자 앞에 쉼표가 붙는가 — 끝에서 세 자리마다.
        fun commaBefore(i: Int) = i > 0 && (n - i) % 3 == 0
        val out = StringBuilder()
        digits.forEachIndexed { i, c -> if (commaBefore(i)) out.append(','); out.append(c) }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + (1 until offset).count { commaBefore(it) }
            override fun transformedToOriginal(offset: Int): Int =
                (offset - out.substring(0, offset.coerceAtMost(out.length)).count { it == ',' })
                    .coerceIn(0, n)
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
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
 * 높이 36 · 좌우 14 · 14 Bold(10/12 에 32 · 12 · 13 에서 키웠다 — 높이는 GLDS 버튼 S 와 같다). 기본 흰 면 + 1 #E3E5EA, **선택은 면을 채우지 않고** 강조색 1.5 테두리 + deep 글자
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
            .height(36.dp)
            .clip(shape)
            .background(Color.White)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val fg = if (selected) deep else TextPrimary
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
        if (dropdown) Icon(Icons.Default.KeyboardArrowDown, null, tint = if (selected) deep else TextSecondary, modifier = Modifier.size(18.dp))
        if (removable) Icon(Icons.Default.Close, null, tint = fg, modifier = Modifier.size(15.dp))
    }
}

private val GldsChipLine = Color(0xFFE3E5EA)

/**
 * 다이얼로그 · 바텀시트 창에서도 상단 · 하단 시스템 바 아이콘을 어둡게(9/30) — 별도 창은 액티비티 설정을 물려받지 않아
 * 다이얼로그가 뜨면 상단바 아이콘이 흰색으로 바뀌었다. 창 content 안에서 한 번 부른다.
 */
/**
 * 이 창을 화면 캡처 · 화면 녹화 · 최근 앱 미리보기에서 가린다(FLAG_SECURE).
 * **토큰이 보일 수 있는 화면**(HoYoLAB 연동 · 로그인)에서만 쓴다 — 다이얼로그 안에서 부르면 그 창에,
 * 아니면 액티비티 창에 걸리고 화면을 떠나면 푼다.
 */
@Composable
fun SecureWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        var ctx = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: (ctx as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

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
