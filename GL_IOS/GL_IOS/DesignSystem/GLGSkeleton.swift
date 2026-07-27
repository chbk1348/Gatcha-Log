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

struct GLGSkeleton: View {
    var cornerRadius: CGFloat = 6

    @Environment(\.glgReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(GLGColor.skeletonBase)
                .overlay {
                    if !reduceMotion {
                        // .animation(디스플레이 리프레시 = ProMotion 120Hz)이 아니라 30Hz 고정.
                        // 시머는 1.1초 주기 저주파 스윕이라 30fps 로도 육안 차이가 없는데, 로딩 구간에는
                        // 스켈레톤이 10여 개 동시에 떠 네트워크·파싱과 프레임 예산을 다툰다.
                        TimelineView(.periodic(from: .now, by: 1.0 / 30.0)) { ctx in
                            // 절대 시간에서 0→1 위상 산출(모든 박스 동일 클럭). -1.6w→+1.6w 스윕.
                            let t = ctx.date.timeIntervalSinceReferenceDate
                            let p = CGFloat((t.truncatingRemainder(dividingBy: GLGMotion.shimmerPeriod)) / GLGMotion.shimmerPeriod)
                            LinearGradient(
                                colors: [GLGColor.skeletonBase, GLGColor.skeletonHighlight, GLGColor.skeletonBase],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                            .frame(width: w * 0.6)
                            .offset(x: (p * 2 - 1) * w * 1.6)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
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
