import SwiftUI
import Foundation

// ── 저사양·접근성 모션 감속 (Android LocalReduceMotion 패리티) ─────────────────

extension EnvironmentValues {
    /// 모션 감속 — 접근성 '동작 줄이기' 또는 저전력 모드. 스켈레톤 시머·로드인 스태거를 끈다.
    var glgReduceMotion: Bool {
        accessibilityReduceMotion || ProcessInfo.processInfo.isLowPowerModeEnabled
    }
}

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
    static let staggerStep: Double = 0.04        // GlgMotion.StaggerStep 40ms
    static let staggerMax: Double = 0.24         // GlgMotion.StaggerMax 240ms

    /// standard easing — cubic-bezier(0.4, 0.0, 0.2, 1.0). 탭·서브페이지 이동 기본.
    static func standard(_ duration: Double = durationStandard) -> Animation {
        .timingCurve(0.4, 0.0, 0.2, 1.0, duration: duration)
    }

    /// emphasis easing — cubic-bezier(0.2, 0.0, 0.0, 1.0). 강조 전환(더 강한 감속).
    static func emphasis(_ duration: Double = durationLong) -> Animation {
        .timingCurve(0.2, 0.0, 0.0, 1.0, duration: duration)
    }
}

// ── 콘텐츠 로드인 스태거 — Android glgLoadIn 패리티 ────────────────────────────

/// 콘텐츠 로드인 — **비활성(2026-07-09).** 앱 전체 로드인 등장(페이드인+슬라이드업) 애니메이션 제거
/// 요청으로 무력화했다. 호출부(각 화면)를 그대로 두기 위해 시그니처만 보존하고 즉시 표시(변형 없음)로 통과시킨다.
/// 되살리려면 이 커밋 이전 이력의 opacity/offset 스태거 구현 참고.
struct GLGLoadIn: ViewModifier {
    let index: Int
    @Binding var appeared: Set<Int>

    init(index: Int, appeared: Binding<Set<Int>>) {
        self.index = index
        self._appeared = appeared
    }

    func body(content: Content) -> some View { content }
}

extension View {
    /// 콘텐츠 로드인 스태거 적용([GLGLoadIn]). [appeared] 는 호출부에서 하나 만들어 모든 항목에 공유.
    func glgLoadIn(_ index: Int, appeared: Binding<Set<Int>>) -> some View {
        modifier(GLGLoadIn(index: index, appeared: appeared))
    }
}
