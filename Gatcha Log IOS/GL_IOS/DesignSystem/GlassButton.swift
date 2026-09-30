import SwiftUI

#if DEBUG
/// **개발자 전용** — ODS 버튼을 몇 가지 테마로 한 화면에 늘어놓는다.
/// 버튼은 대부분 하위 화면(모달 · 설정)에 있어 시뮬레이터로는 거기까지 갈 수 없다.
/// 여는 법: 실행 인자 `-uiPreview buttons`.
struct GLGButtonPreviewSheet: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("버튼 미리보기").font(.system(size: 15, weight: .bold))
                ForEach([5, 6, 0, 1, 8], id: \.self) { idx in
                    let a = GLGTheme.accent(idx)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(a.label).font(.system(size: 11)).foregroundStyle(.gray)
                        HStack(spacing: 10) {
                            OdsButton(title: "취소", variant: .secondary) {}
                            OdsButton(title: "저장하기") {}
                        }
                        OdsButton(title: "지출 추가", size: .l) {}
                        HStack(spacing: 8) {
                            OdsButton(title: "교환", variant: .secondary, size: .xs, fullWidth: false) {}
                            OdsButton(title: "재연동", size: .s, fullWidth: false) {}
                            OdsButton(title: "삭제", variant: .danger, size: .s, fullWidth: false) {}
                        }
                    }
                    .padding(12)
                    .background(a.tint, in: RoundedRectangle(cornerRadius: 16))
                    .environment(\.glgAccent, a)
                }
            }
            .padding(16)
        }
    }
}
#endif

// ════════════════════════════════════════════════════════════════════════════
// ODS — 앱 공용 버튼 규격 (2026-09-30). Android `ui/components/Ods.kt` 와 값이 같다.
// 두 플랫폼을 픽셀까지 맞추려고 시스템 스타일 대신 직접 그린다(글자도 Pretendard).
// ════════════════════════════════════════════════════════════════════════════

enum OdsVariant { case primary, secondary, neutral, inverse, danger, onTint, text }

/// 높이 · 반경 · 글자 · 아이콘 · 간격 · 좌우 여백(내용 폭일 때).
enum OdsSize {
    case l, m, s, xs
    var height: CGFloat { switch self { case .l: 50; case .m: 44; case .s: 36; case .xs: 28 } }
    var radius: CGFloat { switch self { case .l, .m: 16; case .s: 12; case .xs: 9 } }
    var font: CGFloat { switch self { case .l, .m: 15; case .s: 13; case .xs: 12 } }
    var icon: CGFloat { switch self { case .l: 16; case .m: 15; case .s: 13; case .xs: 12 } }  // SF Symbol pt ≈ Android 18/17/15/14dp
    var gap: CGFloat { switch self { case .l: 8; case .m: 7; case .s: 6; case .xs: 4 } }
    var padH: CGFloat { switch self { case .l: 20; case .m: 18; case .s: 14; case .xs: 12 } }
}

/// ODS 버튼. `fullWidth` 면 가로 전체, 아니면 내용 폭 + 좌우 여백. 누르면 0.97배 + 면이 한 단 진해진다.
struct OdsButton: View {
    let title: String
    var variant: OdsVariant = .primary
    var size: OdsSize = .m
    var systemImage: String? = nil
    var fullWidth: Bool = true
    var loading: Bool = false
    let action: () -> Void

    @Environment(\.isEnabled) private var enabled

    var body: some View {
        Button(action: action) {
            Group {
                if loading {
                    ProgressView().controlSize(.small)
                } else {
                    HStack(spacing: size.gap) {
                        if let systemImage { Image(systemName: systemImage).font(.system(size: size.icon, weight: .semibold)) }
                        Text(title).font(.pretendard(size: size.font, weight: .bold)).lineLimit(1)
                    }
                }
            }
            .padding(.horizontal, size.padH)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .frame(height: size.height)
        }
        .buttonStyle(OdsButtonStyle(variant: variant, size: size))
        .disabled(loading)
    }
}

struct OdsButtonStyle: ButtonStyle {
    let variant: OdsVariant
    let size: OdsSize
    @Environment(\.glgAccent) private var accent
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed && enabled
        let (base, fg): (Color, Color) = switch variant {
        case .primary: (accent.primary, .white)
        case .secondary: (accent.primary.opacity(pressed ? 0.20 : 0.12), accent.deep)
        case .neutral: (Color(hex: 0xFFECEFF4), GLGColor.textPrimary)
        case .inverse: (GLGColor.textPrimary, .white)
        case .danger: (GLGColor.dangerBackground, GLGColor.dangerText)
        case .onTint: (.white, accent.deep)
        case .text: (pressed ? accent.primary.opacity(0.08) : .clear, accent.primary)
        }
        let dims = pressed && variant != .secondary && variant != .text
        let bg: Color = !enabled && variant != .text ? Color(hex: 0xFFD8D8DE) : base
        return configuration.label
            .foregroundStyle(enabled ? fg : GLGColor.textSecondary)
            .background(bg, in: RoundedRectangle(cornerRadius: size.radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: size.radius, style: .continuous).fill(.black.opacity(dims ? 0.08 : 0)))
            .contentShape(RoundedRectangle(cornerRadius: size.radius, style: .continuous))
            .scaleEffect(pressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.12), value: pressed)
    }
}
