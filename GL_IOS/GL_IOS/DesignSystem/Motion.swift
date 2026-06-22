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

/// 콘텐츠가 **처음 표시될 때 1회** opacity 0→1 + 살짝 위(12pt)에서 내려오며 등장.
/// [appeared] 에 [index] 를 기록해, LazyVStack 재생성으로 스크롤 재진입해도 다시 애니메이션하지 않는다.
/// 호출부에서 `@State var appeared: Set<Int> = []` 를 하나 만들어 모든 항목에 공유 전달.
struct GLGLoadIn: ViewModifier {
    let index: Int
    @Binding var appeared: Set<Int>
    @State private var shown: Bool

    init(index: Int, appeared: Binding<Set<Int>>) {
        self.index = index
        self._appeared = appeared
        // 이미 등장한 항목이면 처음부터 표시(애니메이션·플리커 없음) — Android remember{index in set} 과 동일.
        self._shown = State(initialValue: appeared.wrappedValue.contains(index))
    }

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 12)
            .onAppear {
                guard !appeared.contains(index) else { return }
                appeared.insert(index)
                let delay = min(Double(index) * GLGMotion.staggerStep, GLGMotion.staggerMax)
                withAnimation(GLGMotion.standard().delay(delay)) { shown = true }
            }
    }
}

extension View {
    /// 콘텐츠 로드인 스태거 적용([GLGLoadIn]). [appeared] 는 호출부에서 하나 만들어 모든 항목에 공유.
    func glgLoadIn(_ index: Int, appeared: Binding<Set<Int>>) -> some View {
        modifier(GLGLoadIn(index: index, appeared: appeared))
    }
}
