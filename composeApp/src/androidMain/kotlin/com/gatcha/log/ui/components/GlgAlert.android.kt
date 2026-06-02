package com.gatcha.log.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.theme.TextSecondary

// Android 는 기존 커스텀 다이얼로그(GlgDialog) 그대로 — 기존 디자인 유지.
@Composable
actual fun GlgAlert(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String?,
    destructive: Boolean,
) {
    GlgDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
    ) {
        Text(message, fontSize = 13.sp, color = TextSecondary)
    }
}
