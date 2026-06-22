package com.gatcha.log.ui.theme

/**
 * 공통 모션 토큰 — 양 플랫폼(Android Compose · iOS SwiftUI) 동일 스펙 참조.
 *
 * 네이티브 UX(iOS 스와이프백·NavigationStack, Android predictive back)는 각 플랫폼이 유지하고,
 * 우리 커스텀 전환(탭·서브페이지·로드인 스태거·루트 크로스페이드)의 **시간·곡선만** 이 토큰으로 통일한다.
 *
 * commonMain 순수 Kotlin(Compose/SwiftUI 타입 비의존). 각 플랫폼이 이 값으로
 * Compose AnimationSpec / SwiftUI Animation 을 구성한다.
 * Android: theme/Motion.kt 가 [GlgMotion] → CubicBezierEasing 변환.
 * iOS: DesignSystem/Motion.swift 의 GLGMotion 이 동일 값 미러(값 동기화 유지).
 */
object GlgMotion {
    /** 짧은 전환 — 페이드아웃·소형 상태 변화. 밀리초. */
    const val DurationShort: Int = 180

    /** 기본 전환 — 탭·서브페이지 이동. 밀리초. */
    const val DurationStandard: Int = 260

    /** 긴 전환 — 루트 상태·강조 모핑. 밀리초. */
    const val DurationLong: Int = 360

    /** 스켈레톤 시머 1회 주기(리니어 반복). 양 플랫폼 동일. 밀리초. */
    const val ShimmerPeriod: Int = 1100

    // ── Easing — cubic-bezier 제어점 (x1, y1, x2, y2) ──────────────────────────
    // standard: 진입·이동 기본(가속 후 감속). Material standard 와 동일 곡선.
    const val EasingStandardX1: Float = 0.4f
    const val EasingStandardY1: Float = 0.0f
    const val EasingStandardX2: Float = 0.2f
    const val EasingStandardY2: Float = 1.0f

    // emphasis: 강조 전환(더 강한 감속).
    const val EasingEmphasisX1: Float = 0.2f
    const val EasingEmphasisY1: Float = 0.0f
    const val EasingEmphasisX2: Float = 0.0f
    const val EasingEmphasisY2: Float = 1.0f
}
