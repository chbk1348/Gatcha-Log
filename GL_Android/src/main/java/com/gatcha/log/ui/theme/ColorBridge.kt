package com.gatcha.log.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * GL_Shared 모델의 색상값(ARGB Long, 0xAARRGGBB)을 Compose [Color] 로 변환.
 * P2a de-Color 로 shared(KMP) 모델은 Compose Color 의존을 버리고 Long 으로 색을 보관하므로,
 * Android UI 소비 지점에서 이 브리지로 감싼다. (값 자체는 기존 Color(0xFF…) 와 동일)
 */
fun Long.toColor(): Color = Color(this)
