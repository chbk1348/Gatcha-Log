import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 브랜드 게이지 링 — 앱 아이콘(v27.38.0 천장 게이지 링)의 형태를 그대로 쓰는 공용 그래픽.
//
// 아이콘이 게이지 링이 된 이상, 진행률을 보여주는 자리는 전부 이 링이어야 한다.
// 런치스크린·온보딩·로딩이 같은 링을 공유하고, 의미만 바꿔 쓴다:
//   온보딩① 천장 게이지 · 온보딩③ D-day(줄어드는 링) · 로딩 동기화 진행률
//
// 색: 링은 테마 강조색(glgAccent, 기본=아이콘과 같은 민트) 그라디언트, 별은 아이콘과 동일한 네이비 고정.
// (Compose 패리티: GL_Android/ui/components/BrandGauge.kt)
// ════════════════════════════════════════════════════════════════════════════

enum BrandMark {
    /// 아이콘 별 색 — 화이트 바탕에서 대비를 잡는 네이비(ic_launcher_foreground 와 동일값).
    static let navy = Color(hex: 0xFF0F1A33)
    /// 게이지 트랙(아직 채워지지 않은 구간) — 아이콘과 동일한 연회색-민트.
    static let track = Color(hex: 0xFFE1EDEA)

    /// 선 굵기 / 링 지름.
    ///
    /// 아이콘(8.5/56 ≈ 15%)보다 얇은 건 의도다. 아이콘은 32px 로 줄어들어도 형태가 살아야 해서 굵은 획을
    /// 쓰지만, 화면에서 100pt 넘게 키우면 같은 비율이 둔하고 답답해 보인다. 형태는 유지하고 굵기만 얇게 잡는다.
    static let strokeRatio: CGFloat = 0.083
}

/// 게이지 링. [progress] 0…1 만큼 12시에서 시계방향으로 차오른다. 링 안에는 [content] 를 얹는다.
struct BrandGaugeRing<Content: View>: View {
    let progress: Double
    var size: CGFloat
    @ViewBuilder var content: () -> Content

    @Environment(\.glgAccent) private var accent

    var body: some View {
        let line = size * BrandMark.strokeRatio
        let ring = size - line // 획이 캔버스 밖으로 삐져나가지 않도록 지름에서 선 굵기를 뺀다

        ZStack {
            Circle()
                .stroke(BrandMark.track, lineWidth: line)
                .frame(width: ring, height: ring)

            Circle()
                .trim(from: 0, to: max(0, min(1, progress)))
                .stroke(
                    LinearGradient(
                        colors: [accent.secondary, accent.primary],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    style: StrokeStyle(lineWidth: line, lineCap: .round)
                )
                .rotationEffect(.degrees(-90)) // 12시 시작
                .frame(width: ring, height: ring)

            content()
        }
        .frame(width: size, height: size)
    }
}

extension BrandGaugeRing where Content == EmptyView {
    init(progress: Double, size: CGFloat) {
        self.init(progress: progress, size: size) { EmptyView() }
    }
}

/// 아이콘의 4각 별 단독. 링과 따로 두면 별에만 호흡 애니메이션(scale)을 걸 수 있다.
struct BrandStar: View {
    var size: CGFloat

    var body: some View {
        FourPointStar()
            .fill(BrandMark.navy)
            .frame(width: size, height: size)
    }
}

/// 4각 별 — 아이콘 벡터의 별(M54,38 l5,11 11,5 …)과 동일 비율.
struct FourPointStar: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width
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
        return p
    }
}

/// 앱 아이콘과 같은 그라운드 — 화이트 + 중앙 민트 글로우.
/// 런치스크린·온보딩·로딩이 공유해 화면이 바뀌어도 배경이 이어져 보이게 한다.
struct BrandGround: View {
    @Environment(\.glgAccent) private var accent

    var body: some View {
        ZStack {
            Color.white
            RadialGradient(
                colors: [accent.primary.opacity(0.16), accent.primary.opacity(0)],
                center: UnitPoint(x: 0.5, y: 0.42),
                startRadius: 0,
                endRadius: 340
            )
        }
        .ignoresSafeArea()
    }
}

/// 온보딩 하단 페이지 인디케이터 — 현재 페이지만 캡슐로 늘어난다.
struct BrandPageDots: View {
    let count: Int
    let current: Int

    @Environment(\.glgAccent) private var accent

    var body: some View {
        HStack(spacing: 5) {
            ForEach(0..<count, id: \.self) { i in
                Capsule()
                    .fill(i == current ? accent.primary : Color(hex: 0xFFDBE3E0))
                    .frame(width: i == current ? 18 : 6, height: 6)
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: current)
    }
}
