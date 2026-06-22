import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 공통 스켈레톤 — Android Skeleton.kt(SkeletonBox + shimmerBrush) 패리티.
//
// 시머 사양: base→highlight→base 가로 그라데이션이 좌→우로 흐름,
// GLGMotion.shimmerPeriod(1100ms) 리니어 무한 반복. 콘텐츠 첫 로딩 플레이스홀더용.
// (액션 스피너 — 출석·체크인 — 는 대상 아님: ProgressView 유지.)
//
// 사용: `GLGSkeleton().frame(width: 140, height: 18)` 처럼 크기는 호출부에서 frame 으로 지정.
// ════════════════════════════════════════════════════════════════════════════

struct GLGSkeleton: View {
    var cornerRadius: CGFloat = 6

    @State private var phase: CGFloat = -1

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(GLGColor.skeletonBase)
                .overlay(
                    LinearGradient(
                        colors: [GLGColor.skeletonBase, GLGColor.skeletonHighlight, GLGColor.skeletonBase],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: w * 0.6)
                    // -1.6w → +1.6w 로 쓸어 지나가며 박스를 완전히 통과.
                    .offset(x: phase * w * 1.6)
                )
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
        .onAppear {
            withAnimation(.linear(duration: GLGMotion.shimmerPeriod).repeatForever(autoreverses: false)) {
                phase = 1
            }
        }
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
