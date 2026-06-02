package com.gatcha.log.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

// Android 는 네이티브 리퀴드 글래스(UIGlassEffect) 미지원 — 항상 Compose 폴백 사용.
// isNativeGlassSupported() 가 false 이므로 아래 actual 들은 호출되지 않는다.

actual fun isNativeGlassSupported(): Boolean = false

@Composable
actual fun NativeGlassButton(
    text: String,
    tint: Color?,
    textColor: Color,
    cornerRadius: Dp,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // 호출되지 않음 (isNativeGlassSupported = false)
}

@Composable
actual fun NativeGlassIconButton(
    sfSymbol: String,
    tint: Color?,
    iconColor: Color,
    size: Dp,
    enabled: Boolean,
    badgeCount: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // 호출되지 않음 (isNativeGlassSupported = false)
}
