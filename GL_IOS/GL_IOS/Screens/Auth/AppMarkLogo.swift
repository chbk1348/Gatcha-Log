import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 앱 마크 — 화이트 스퀘어클 + 민트 글로우 위에 천장 게이지 링 + 네이비 별.
// 런처/홈화면 아이콘(v27.38.0 개편)과 동일한 디자인을 SwiftUI 로 그린다.
// 진입 바운스(스케일·페이드) + 무한 호흡 펄스 + 글로우 헤일로. 로그인·로딩 화면 공용.
// (Compose LoginScreen.kt 의 AppMarkLogo 대응 — 그쪽은 ic_launcher_foreground 벡터를 그대로 씀)
//
// 비율은 아이콘 벡터(viewport 108)를 그대로 옮긴 것:
//   링 반지름 28/108 · 선 굵기 8.5/108 · 별 폭 32/108 · 게이지 270°(12시에서 시계방향)
// ════════════════════════════════════════════════════════════════════════════

struct AppMarkLogo: View {
    var boxSize: CGFloat = 84

    @Environment(\.glgAccent) private var accent
    @State private var entered = false
    @State private var pulsing = false

    private let mint = Color(hex: 0xFF34D1B6)
    private let mintLight = Color(hex: 0xFF7FFBE6)
    private let mintDeep = Color(hex: 0xFF14B8A6)
    private let navy = Color(hex: 0xFF0F1A33)
    private let track = Color(hex: 0xFFE1EDEA)

    var body: some View {
        ZStack {
            // 뒤에서 번지는 글로우 헤일로
            Circle()
                .fill(accent.primary.opacity(pulsing ? 0.32 : 0.12))
                .frame(width: boxSize * 1.4, height: boxSize * 1.4)
                .scaleEffect(pulsing ? 1.2 : 0.9)
                .opacity(entered ? 1 : 0)

            // 스퀘어클 — 화이트 바탕 + 민트 글로우 (아이콘 배경과 동일)
            RoundedRectangle(cornerRadius: boxSize * 0.27, style: .continuous)
                .fill(.white)
                .overlay(
                    RadialGradient(
                        colors: [mint.opacity(0.18), mint.opacity(0)],
                        center: .center,
                        startRadius: 0,
                        endRadius: boxSize * 0.55
                    )
                )
                .overlay(mark)
                .clipShape(RoundedRectangle(cornerRadius: boxSize * 0.27, style: .continuous))
                .frame(width: boxSize, height: boxSize)
                .scaleEffect(entered ? 1 : 0)
                .scaleEffect(pulsing ? 1.05 : 1.0)
                .opacity(entered ? 1 : 0)
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.5)) { entered = true }
            withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) { pulsing = true }
        }
    }

    /// 게이지 링 + 중앙 별.
    private var mark: some View {
        let ring = boxSize * (56.0 / 108.0)   // 링 지름
        let line = boxSize * (8.5 / 108.0)    // 선 굵기
        let star = boxSize * (32.0 / 108.0)   // 별 폭

        return ZStack {
            Circle()
                .stroke(track, lineWidth: line)
                .frame(width: ring, height: ring)

            // 12시에서 시계방향 270° — 남은 1/4 이 "아직 채우는 중"
            Circle()
                .trim(from: 0, to: 0.75)
                .stroke(
                    LinearGradient(colors: [mintLight, mintDeep], startPoint: .topLeading, endPoint: .bottomTrailing),
                    style: StrokeStyle(lineWidth: line, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .frame(width: ring, height: ring)

            FourPointStar()
                .fill(navy)
                .frame(width: star, height: star)
        }
    }
}

/// 4각 별 — 아이콘 벡터의 별(M54,38 l5,11 11,5 …)과 동일 비율.
private struct FourPointStar: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        let cx = rect.midX, cy = rect.midY
        let outer = w / 2                 // 꼭짓점까지
        let inner = w * (5.0 / 32.0)      // 오목한 지점 (벡터의 5/32 비율)

        var p = Path()
        p.move(to: CGPoint(x: cx, y: cy - outer))            // 위
        p.addLine(to: CGPoint(x: cx + inner, y: cy - inner))
        p.addLine(to: CGPoint(x: cx + outer, y: cy))         // 오른쪽
        p.addLine(to: CGPoint(x: cx + inner, y: cy + inner))
        p.addLine(to: CGPoint(x: cx, y: cy + outer))         // 아래
        p.addLine(to: CGPoint(x: cx - inner, y: cy + inner))
        p.addLine(to: CGPoint(x: cx - outer, y: cy))         // 왼쪽
        p.addLine(to: CGPoint(x: cx - inner, y: cy - inner))
        p.closeSubpath()
        _ = h
        return p
    }
}
