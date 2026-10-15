package com.gatcha.log.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * GLDS 금액 카드 — 가이드 「예산 금액 입력」(`BudgetAmountEditor`)의 **면**만 떼어 낸 것(10/13).
 * 지출 추가 · 수정의 금액 칸이 쓴다. 온보딩 ③ · 설정 ▸ 예산 관리의 [com.gatcha.log.ui.onboarding.BudgetAmountEditor] 와 값이 같다.
 *
 * 색은 **틸 고정**이다(테마 강조색을 따르지 않는다).
 *  - 카드: `#EEF8F8 → #DCF0EE` · 반경 22 · 안쪽 20 / 20 / 22.
 *  - 라벨: 13 Bold `#177881`.
 *  - 숫자: 40 Bold `#0F1A33` · 자간 -0.5 · 폭은 내용(최소 24) · 자리표시자는 30%. 글자 모양은 [GldsAmountCardDefaults.numberStyle].
 *  - 「원」: 20 Bold · 숫자와 4 · 아래 정렬 + 아래 8.
 *  - 주의([warn]): `#FFF4E8 → #FFE2C7` · 라벨 · 숫자 · 「원」 `#C2410C` · 한 번 흔들린 뒤 안쪽 테두리 0↔5 `#EA580C` 16% 가 900ms 로 왕복.
 *
 * @param amount 숫자 자리(입력칸). 넘겨받는 색으로 글자 · 커서를 칠한다.
 * @param onClick 카드 어디를 눌러도 입력이 시작되게 할 때.
 */
@Composable
fun GldsAmountCard(
    label: String,
    warn: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    amount: @Composable RowScope.(color: Color) -> Unit,
) {
    // 주의로 바뀌는 순간 한 번 흔들린다.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(warn) {
        if (warn) shake.animateTo(0f, keyframes { durationMillis = 420; -6f at 84; 5f at 168; -3f at 252; 2f at 336 })
    }
    val glow by rememberInfiniteTransition(label = "amountCardGlow")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val shape = RoundedCornerShape(22.dp)
    val brush = if (warn) Brush.linearGradient(listOf(Color(0xFFFFF4E8), Color(0xFFFFE2C7)))
    else Brush.linearGradient(listOf(Color(0xFFEEF8F8), Color(0xFFDCF0EE)))
    val color = if (warn) GldsAmountCardDefaults.Warn else GldsAmountCardDefaults.Ink

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value * density }
            .then(if (warn) Modifier.border(BorderStroke((5 * glow).dp, Color(0xFFEA580C).copy(alpha = 0.16f)), shape) else Modifier)
            .clip(shape)
            .background(brush)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
                else Modifier,
            )
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 22.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (warn) GldsAmountCardDefaults.Warn else GldsAmountCardDefaults.Teal)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            amount(color)
            Text("원", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
    }
}

object GldsAmountCardDefaults {
    val Ink = Color(0xFF0F1A33)
    val Teal = Color(0xFF177881)
    val Warn = Color(0xFFC2410C)

    /** 숫자 글자 모양 — 40 Bold · 자간 -0.5. 자리표시자는 같은 모양에 색만 30%. */
    fun numberStyle(base: TextStyle, color: Color): TextStyle =
        base.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = (-0.5).sp)
}
