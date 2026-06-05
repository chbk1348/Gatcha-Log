import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 버튼 — 시스템 버튼 "모양"을 따른다 (iOS 26 = 캡슐 Liquid Glass, 그 이하 = 캡슐 폴백).
//
// Compose 의 GlgButton(채움 강조색) / GlgOutlineButton(테두리) 대응.
// 시스템 디자인 원칙: 커스텀 드로잉이 아니라 .glass / .glassProminent 버튼 스타일을 쓴다.
// ════════════════════════════════════════════════════════════════════════════

/// 주 버튼 (강조색 채움) — 로그인·저장 등 1차 액션.
struct GLGButton: View {
    let title: String
    var systemImage: String? = nil
    var fullWidth: Bool = true
    let action: () -> Void

    @Environment(\.glgAccent) private var accent

    var body: some View {
        Button(action: action) {
            label
        }
        .modifier(GLGProminentStyle(tint: accent.primary))
    }

    @ViewBuilder private var label: some View {
        HStack(spacing: 8) {
            if let systemImage { Image(systemName: systemImage) }
            Text(title).fontWeight(.semibold)
        }
        .frame(maxWidth: fullWidth ? .infinity : nil)
        .frame(minHeight: 30)
    }
}

/// 보조 버튼 (테두리/글래스) — 취소·게스트 시작 등 2차 액션.
struct GLGOutlineButton: View {
    let title: String
    var systemImage: String? = nil
    var fullWidth: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title).fontWeight(.medium)
            }
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .frame(minHeight: 30)
        }
        .modifier(GLGGlassStyle())
    }
}

// ── 버전 분기 버튼 스타일 ──────────────────────────────────────────────────

/// 1차 액션 — iOS 26 .glassProminent, 이하 .borderedProminent (둘 다 캡슐).
private struct GLGProminentStyle: ViewModifier {
    let tint: Color
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .buttonStyle(.glassProminent)
                .tint(tint)
                .buttonBorderShape(.capsule)
                .controlSize(.large)
        } else {
            content
                .buttonStyle(.borderedProminent)
                .tint(tint)
                .buttonBorderShape(.capsule)
                .controlSize(.large)
        }
    }
}

/// 2차 액션 — iOS 26 .glass, 이하 .bordered (둘 다 캡슐).
private struct GLGGlassStyle: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .buttonStyle(.glass)
                .buttonBorderShape(.capsule)
                .controlSize(.large)
        } else {
            content
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                .controlSize(.large)
        }
    }
}
