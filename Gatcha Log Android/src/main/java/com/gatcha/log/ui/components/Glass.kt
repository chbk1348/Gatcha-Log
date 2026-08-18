package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 카드 표면색 — D Soft Modern: 흰 배경 위 연회색 솔리드(보더 없이 면으로 구분). */
private val CardSurface = Color(0xFFF6F7F9)

/** 앱 배경 — 흰색 단색(전역). 강조색 그라데이션 제거. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Color.White))
        content()
    }
}

/**
 * 카드 (D · Soft Modern) — 흰 배경 위 연회색 솔리드 면, 큰 라운드(24dp), 보더·그림자 없음.
 * 가독성·스크롤 성능을 위해 backdrop-blur 미사용. iOS(glgGlass: 동일 연회색 면)와 파리티.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val styled = modifier
        .clip(shape)
        .background(CardSurface)
        .border(1.dp, Color.Black.copy(alpha = 0.06f), shape)
    Box(styled, content = content)
}
