import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 헤더 버튼 — 시스템 버튼 스타일을 따른다 (iOS 26 = Liquid Glass, 18~25 = bordered).
// 시스템 디자인 원칙: 커스텀 배경/테두리 대신 .glass / .bordered 시스템 버튼 스타일 사용.
// ════════════════════════════════════════════════════════════════════════════

extension View {
    /// 시스템 글래스 버튼 스타일. circle=true 면 원형(아이콘 전용), 아니면 캡슐(아이콘+라벨).
    @ViewBuilder
    func glgGlassButton(circle: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glass)
                .buttonBorderShape(circle ? .circle : .capsule)
                .controlSize(.small)
        } else {
            self.buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                .controlSize(.small)
        }
    }
}

/// 헤더용 시스템 아이콘 버튼 (원형 글래스).
struct GLGHeaderIconButton: View {
    let systemImage: String
    var disabled: Bool = false
    let action: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage).font(.system(size: 15, weight: .semibold))
        }
        .glgGlassButton(circle: true)
        .tint(accent.primary)
        .disabled(disabled)
        .opacity(disabled ? 0.5 : 1)
    }
}

/// 헤더용 시스템 알약 버튼 (아이콘 + 라벨 글래스).
struct GLGHeaderPillButton: View {
    let title: String
    var systemImage: String? = nil
    var prominent: Bool = false
    let action: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Button(action: action) {
            if let systemImage { Label(title, systemImage: systemImage) }
            else { Text(title) }
        }
        .font(.system(size: 12, weight: .bold))
        .glgGlassButton(circle: false)
        .tint(accent.primary)
    }
}
