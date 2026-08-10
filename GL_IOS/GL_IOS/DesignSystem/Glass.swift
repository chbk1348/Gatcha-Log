import SwiftUI
import UIKit

// ════════════════════════════════════════════════════════════════════════════
// Liquid Glass 토큰 — iOS 26 는 진짜 시스템 글래스(.glassEffect), iOS 18~25 는 머티리얼 폴백.
//
// 시스템 디자인 원칙(메모리 ios-system-design-principle): Compose 로 유리 질감을 흉내 내지 않고
// 시스템 머티리얼을 그대로 쓴다 → 다크모드·다이나믹 타입·접근성이 자동으로 따라온다.
// ════════════════════════════════════════════════════════════════════════════

extension View {
    /// 콘텐츠 뒤에 글래스 패널을 깐다. iOS 26 = Liquid Glass, 그 이하 = ultraThinMaterial.
    /// - Parameters:
    ///   - shape: 글래스 패널 모양 (기본: 둥근 사각형)
    ///   - interactive: iOS 26 인터랙티브 글래스(탭 시 반응) 여부
    // D · Soft Modern 카드 — 흰 배경 위 연회색 솔리드 면 + 약간의 아웃라인(헤어라인). Android GlassCard 와 파리티.
    @ViewBuilder
    func glgGlass<S: Shape>(in shape: S, interactive: Bool = false) -> some View {
        self.background(Color(hex: 0xFFF6F7F9), in: shape)
            .overlay(shape.stroke(Color.black.opacity(0.06), lineWidth: 1).allowsHitTesting(false))
    }

    /// 가독성이 더 필요한 패널(시트/다이얼로그 본문)용 — 흰 배경 + 아웃라인(전역 유리 제거).
    @ViewBuilder
    func glgGlassStrong<S: Shape>(in shape: S) -> some View {
        self.background(Color.white, in: shape)
            .overlay(shape.stroke(Color.black.opacity(0.10), lineWidth: 1).allowsHitTesting(false))
    }

}

// ════════════════════════════════════════════════════════════════════════════
// 시스템 머티리얼을 직접 래핑한 블러 뷰 (iOS 18~25 폴백 전용).
//
// SwiftUI 의 `.ultraThinMaterial` 은 앱이 백그라운드로 갔다(잠금화면·앱 스위처·제어센터)
// 돌아올 때, 시스템이 스냅샷을 찍느라 블러 이펙트를 제거했다가 재적용하면서
// 카드가 "사라졌다 나타나는" 깜빡임을 만든다. SwiftUI 머티리얼은 내부 UIVisualEffectView 에
// 접근할 수 없어 이 한 프레임 공백을 막을 수 없으므로, UIVisualEffectView 를 직접 들고
// willEnterForeground 에서 effect 를 곧바로 재설정해 깜빡임을 없앤다.
// (iOS 26 는 .glassEffect 가 자체 라이프사이클을 관리하므로 폴백에서만 사용)
// ════════════════════════════════════════════════════════════════════════════
struct GLGVisualEffectBlur: UIViewRepresentable {
    let style: UIBlurEffect.Style

    func makeUIView(context: Context) -> UIVisualEffectView {
        let view = UIVisualEffectView(effect: UIBlurEffect(style: style))
        context.coordinator.observe(view: view, style: style)
        return view
    }

    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {
        uiView.effect = UIBlurEffect(style: style)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator {
        private var token: NSObjectProtocol?

        func observe(view: UIVisualEffectView, style: UIBlurEffect.Style) {
            // 포그라운드 복귀 시, 시스템이 비워둔 블러 이펙트를 즉시 재설정 → 깜빡임 제거
            token = NotificationCenter.default.addObserver(
                forName: UIApplication.willEnterForegroundNotification,
                object: nil,
                queue: .main
            ) { [weak view] _ in
                // 위에서 queue: .main 으로 등록했으니 실제로 메인에서 온다 — 컴파일러에 그 사실을 알린다.
                MainActor.assumeIsolated { view?.effect = UIBlurEffect(style: style) }
            }
        }

        deinit {
            if let token { NotificationCenter.default.removeObserver(token) }
        }
    }
}

/// 앱 전역 배경 — D · Soft Modern: 솔리드 흰색(연회색 카드 대비 확보). Android GlassBackground 와 파리티.
struct GLGBackground<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            content
        }
    }
}
