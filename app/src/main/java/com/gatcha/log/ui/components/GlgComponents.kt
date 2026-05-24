package com.gatcha.log.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gatcha.log.ui.theme.LocalAccent

// ============================================================
//  Gatcha LOG 커스텀 디자인 토큰 (웹앱 스타일 이식)
// ============================================================
private val FieldShape = RoundedCornerShape(12.dp)
private val FieldBgIdle = Color(0xFFF6F6FA)
private val FieldBgFocus = Color(0xFFFFFFFF)
private val FieldBorderIdle = Color(0x14000000)   // rgba(0,0,0,0.08)
private val FieldText = Color(0xFF1A1C1E)
private val FieldPlaceholder = Color(0x40000000)  // rgba(0,0,0,0.25)
private val LabelColor = Color(0x66000000)        // rgba(0,0,0,0.4)
private val GhostBorder = Color(0xFFE3E3EA)
private val GhostText = Color(0xFF6C727A)

/** 입력 필드 위 라벨 (대문자 느낌의 작은 라벨) */
@Composable
fun GlgFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = LabelColor,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/**
 * 커스텀 텍스트 필드. 포커스 시 강조색 테두리 + 은은한 글로우 링 (레이아웃 시프트 없음).
 */
@Composable
fun GlgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(if (focused) accent else FieldBorderIdle, label = "border")
    val ringColor by animateColorAsState(if (focused) accent.copy(alpha = 0.12f) else Color.Transparent, label = "ring")
    val bg by animateColorAsState(if (focused) FieldBgFocus else FieldBgIdle, label = "bg")

    Column(modifier) {
        label?.let { GlgFieldLabel(it) }
        // 글로우 링: 항상 3dp 패딩 확보 → 포커스 시 색만 채워 시프트 방지
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .background(ringColor)
                .padding(3.dp),
        ) {
            val fieldModifier = Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .background(bg)
                .border(1.dp, borderColor, FieldShape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = fieldModifier) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = FieldPlaceholder, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled && onClick == null,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        keyboardOptions = keyboardOptions,
                        textStyle = LocalTextStyle.current.copy(color = FieldText, fontSize = 15.sp),
                        cursorBrush = SolidColor(accent),
                        interactionSource = interactionSource,
                    )
                }
                trailingIcon?.let {
                    androidx.compose.material3.Icon(
                        it, contentDescription = null,
                        tint = GhostText,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** 주요 액션 버튼 — 강조색 그라데이션 */
@Composable
fun GlgButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 50.dp,
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, label = "btnScale")
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(accent, lerp(accent, Color.Black, 0.18f)))
    } else {
        SolidColor(Color(0xFFD8D8DE))
    }
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .then(if (enabled) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** 보조/취소 버튼 — 고스트 스타일 */
@Composable
fun GlgOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 50.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "outBtnScale")
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, GhostBorder, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = GhostText, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

/**
 * 커스텀 중앙 다이얼로그 (라운드 카드 + 강조 버튼).
 * [dismissText] 가 null 이면 확인 버튼만 전체폭으로 표시.
 */
@Composable
fun GlgDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "저장",
    onConfirm: () -> Unit,
    dismissText: String? = "취소",
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, com.gatcha.log.ui.theme.DividerColor),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FieldText)
                    Spacer(Modifier.height(16.dp))
                    content()
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (dismissText != null) {
                            GlgOutlineButton(dismissText, onDismiss, Modifier.weight(1f))
                            GlgButton(confirmText, onConfirm, Modifier.weight(1.4f), enabled = confirmEnabled)
                        } else {
                            GlgButton(confirmText, onConfirm, Modifier.fillMaxWidth(), enabled = confirmEnabled)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 헤더용 공통 커스텀 원형 아이콘 버튼 — 강조색 틴트 + 눌림 효과(전역 인디케이션).
 * [loading] 시 스피너, [badgeCount] > 0 이면 우상단 배지.
 */
@Composable
fun GlgCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    loading: Boolean = false,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    /** true 면 강조색 아웃라인(테두리)을 그린다 — 확률표 알약 버튼과 동일한 톤 */
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f))
                .then(if (outlined) Modifier.border(1.5.dp, accent.copy(alpha = 0.30f), CircleShape) else Modifier)
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
            } else {
                Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFA500)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badgeCount > 9) "9+" else "$badgeCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 커스텀 토글 스위치 */
@Composable
fun GlgSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val trackWidth = 50.dp
    val trackHeight = 30.dp
    val thumb = 24.dp
    val trackColor by animateColorAsState(if (checked) accent else Color(0xFFD8D8DE), label = "track")
    val offset by animateDpAsState(if (checked) trackWidth - thumb - 3.dp else 3.dp, label = "thumb")

    Box(
        modifier = Modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = offset)
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
