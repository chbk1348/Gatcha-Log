import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 공통 스켈레톤 — Android Skeleton.kt(SkeletonBox + shimmerBrush) 패리티.
//
// 시머 사양: base→highlight→base 가로 그라데이션이 좌→우로 흐름,
// GLGMotion.shimmerPeriod(1100ms) 리니어 무한 반복. 콘텐츠 첫 로딩 플레이스홀더용.
// (액션 스피너 — 출석·체크인 — 는 대상 아님: ProgressView 유지.)
//
// 위상은 뷰마다 repeatForever 애니메이션 컨트롤러를 두는 대신 TimelineView(.animation)
// 가 시스템 타임라인에서 1개 클럭을 샘플링 → 모든 스켈레톤이 동기화되고 저사양 부하↓.
// 모션 감속(접근성 '동작 줄이기'·저전력) 시엔 시머를 멈춘 정적 회색 면.
//
// 사용: `GLGSkeleton().frame(width: 140, height: 18)` 처럼 크기는 호출부에서 frame 으로 지정.
// ════════════════════════════════════════════════════════════════════════════

// 시머 위상(0→1)을 환경으로 내려보낸다.
//
// 예전엔 GLGSkeleton 하나하나가 자기 TimelineView 를 들고 있었다. DashCardSkeleton(rows:3) 한 장이
// GLGSkeleton 10개라, 홈 로딩 중엔 TimelineView 가 20개 넘게 동시에 돌며 각자 GeometryReader 레이아웃
// 패스까지 유발했다. 위상은 절대 시간에서 나오는 **같은 값**이므로 위에서 한 번만 계산해 내려주면 된다.
private struct GLGShimmerPhaseKey: EnvironmentKey {
    static let defaultValue: CGFloat? = nil
}

extension EnvironmentValues {
    /// 상위 [GLGShimmerClock] 이 내려준 시머 위상. nil 이면 각 스켈레톤이 자기 클럭을 쓴다.
    var glgShimmerPhase: CGFloat? {
        get { self[GLGShimmerPhaseKey.self] }
        set { self[GLGShimmerPhaseKey.self] = newValue }
    }
}

/// 스켈레톤이 여러 개 뜨는 화면을 이걸로 감싸면 시머 클럭을 하나만 돌린다.
/// 감싸지 않아도 동작은 같다(각자 클럭) — 성능만 달라진다.
struct GLGShimmerClock<Content: View>: View {
    @Environment(\.glgReduceMotion) private var reduceMotion
    @ViewBuilder var content: Content

    var body: some View {
        if reduceMotion {
            content
        } else {
            TimelineView(.periodic(from: .now, by: 1.0 / 30.0)) { ctx in
                content.environment(\.glgShimmerPhase, GLGSkeleton.phase(at: ctx.date))
            }
        }
    }
}

struct GLGSkeleton: View {
    var cornerRadius: CGFloat = 6

    @Environment(\.glgReduceMotion) private var reduceMotion
    @Environment(\.glgShimmerPhase) private var sharedPhase

    /// 절대 시간에서 0→1 위상 — 모든 박스가 같은 값을 쓰므로 어디서 계산해도 결과가 같다.
    static func phase(at date: Date) -> CGFloat {
        let t = date.timeIntervalSinceReferenceDate
        return CGFloat((t.truncatingRemainder(dividingBy: GLGMotion.shimmerPeriod)) / GLGMotion.shimmerPeriod)
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(GLGColor.skeletonBase)
                .overlay {
                    if !reduceMotion {
                        if let sharedPhase {
                            sweep(width: w, phase: sharedPhase)
                        } else {
                            // 상위에 GLGShimmerClock 이 없을 때만 자기 클럭을 돈다.
                            // .animation(= ProMotion 120Hz)이 아니라 30Hz 고정 — 1.1초 주기 저주파
                            // 스윕이라 육안 차이가 없는데 로딩 구간엔 프레임 예산을 네트워크·파싱과 다툰다.
                            TimelineView(.periodic(from: .now, by: 1.0 / 30.0)) { ctx in
                                sweep(width: w, phase: Self.phase(at: ctx.date))
                            }
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
    }

    /// base→highlight→base 가로 그라데이션이 -1.6w → +1.6w 로 흐른다.
    private func sweep(width w: CGFloat, phase p: CGFloat) -> some View {
        LinearGradient(
            colors: [GLGColor.skeletonBase, GLGColor.skeletonHighlight, GLGColor.skeletonBase],
            startPoint: .leading,
            endPoint: .trailing
        )
        .frame(width: w * 0.6)
        .offset(x: (p * 2 - 1) * w * 1.6)
    }
}

/// 기프트코드 자동수집 로딩 스켈레톤 — 코드행(코드/보상 + 교환 버튼) N행. (Android GiftCodeSkeleton 패리티)
struct GLGGiftCodeSkeleton: View {
    var rows: Int = 3

    var body: some View {
        VStack(spacing: 14) {
            ForEach(0..<rows, id: \.self) { _ in
                HStack {
                    VStack(alignment: .leading, spacing: 7) {
                        GLGSkeleton().frame(width: 110, height: 14)
                        GLGSkeleton().frame(width: 160, height: 11)
                    }
                    Spacer(minLength: 8)
                    GLGSkeleton(cornerRadius: 14).frame(width: 52, height: 28)
                }
            }
        }
        .padding(.vertical, 6)
    }
}
