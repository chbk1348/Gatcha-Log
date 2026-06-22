import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 공통 모션 토큰 — commonMain GlgMotion 미러. (값은 Kotlin 정의와 동일하게 유지할 것)
//
// 네이티브 UX(NavigationStack 푸시·스와이프백)는 그대로 두고, 우리 커스텀 전환
// (루트 크로스페이드·로드인 스태거 등)의 시간·곡선만 이 토큰으로 통일한다.
// duration 은 초 단위(SwiftUI), Kotlin 은 ms — 값 0.18↔180 / 0.26↔260 / 0.36↔360 대응.
// ════════════════════════════════════════════════════════════════════════════

enum GLGMotion {
    static let durationShort: Double = 0.18      // GlgMotion.DurationShort 180ms
    static let durationStandard: Double = 0.26   // GlgMotion.DurationStandard 260ms
    static let durationLong: Double = 0.36       // GlgMotion.DurationLong 360ms
    static let shimmerPeriod: Double = 1.1       // GlgMotion.ShimmerPeriod 1100ms

    /// standard easing — cubic-bezier(0.4, 0.0, 0.2, 1.0). 탭·서브페이지 이동 기본.
    static func standard(_ duration: Double = durationStandard) -> Animation {
        .timingCurve(0.4, 0.0, 0.2, 1.0, duration: duration)
    }

    /// emphasis easing — cubic-bezier(0.2, 0.0, 0.0, 1.0). 강조 전환(더 강한 감속).
    static func emphasis(_ duration: Double = durationLong) -> Animation {
        .timingCurve(0.2, 0.0, 0.0, 1.0, duration: duration)
    }
}
