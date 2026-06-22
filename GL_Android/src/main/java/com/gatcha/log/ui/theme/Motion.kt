package com.gatcha.log.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * 공통 모션 토큰의 Compose 표현 — shared [GlgMotion] 미러 변환.
 *
 * duration 은 [GlgMotion.DurationShort]/[GlgMotion.DurationStandard]/[GlgMotion.DurationLong] (Int ms) 를
 * 그대로 `tween(...)` 에 넣어 쓰고, easing 은 아래 [GlgEasingStandard]/[GlgEasingEmphasis] 를 사용한다.
 * 하드코딩 ms·임의 easing 대신 이 토큰을 참조해 양 플랫폼 곡선·시간을 통일한다.
 */
val GlgEasingStandard: Easing = CubicBezierEasing(
    GlgMotion.EasingStandardX1, GlgMotion.EasingStandardY1,
    GlgMotion.EasingStandardX2, GlgMotion.EasingStandardY2,
)

val GlgEasingEmphasis: Easing = CubicBezierEasing(
    GlgMotion.EasingEmphasisX1, GlgMotion.EasingEmphasisY1,
    GlgMotion.EasingEmphasisX2, GlgMotion.EasingEmphasisY2,
)
