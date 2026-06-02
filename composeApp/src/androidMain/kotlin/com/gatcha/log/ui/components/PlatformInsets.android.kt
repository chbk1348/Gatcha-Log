package com.gatcha.log.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Android 는 제스처 내비 바 패딩 유지 — 기존 동작 그대로.
@Composable
actual fun Modifier.subPageBottomInset(): Modifier = this.navigationBarsPadding()
