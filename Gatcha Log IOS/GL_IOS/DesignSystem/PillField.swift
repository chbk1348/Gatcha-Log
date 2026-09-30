import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// GLDS 입력필드 (2026-09-30) — Android `GldsTextField` 와 값이 같다.
// 채운 면 #F5F8F8, 포커스 때 흰 면 + 강조색 1.5 테두리, 오류면 빨강 테두리 + 아래 문구.
// ════════════════════════════════════════════════════════════════════════════

enum GldsFieldSize {
    case m, s
    var height: CGFloat { self == .m ? 48 : 38 }
    var radius: CGFloat { self == .m ? 14 : 12 }
    var font: CGFloat { self == .m ? 16 : 14 }
    var padH: CGFloat { self == .m ? 14 : 12 }
}

struct GldsTextField: View {
    var label: String? = nil
    let placeholder: String
    @Binding var text: String
    var size: GldsFieldSize = .m
    var suffix: String? = nil
    var trailingSystemImage: String? = nil
    var helper: String? = nil
    var error: String? = nil
    var alignment: TextAlignment = .leading
    var bold: Bool = false
    var keyboard: UIKeyboardType = .default
    var secure: Bool = false
    var axis: Axis = .horizontal
    /// 바깥에서 포커스를 걸고 풀 때(모달이 열리자마자 키보드 등). 없으면 필드 자체 상태를 쓴다.
    var focus: FocusState<Bool>.Binding? = nil
    /// 누르는 필드(날짜 등) — 입력 대신 이 동작을 한다. Android `onClick` 과 같다.
    var onTap: (() -> Void)? = nil

    @Environment(\.glgAccent) private var accent
    @FocusState private var ownFocus: Bool
    private var focusBinding: FocusState<Bool>.Binding { focus ?? $ownFocus }
    private var focused: Bool { focusBinding.wrappedValue }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let label { GldsFieldLabel(text: label) }
            HStack(spacing: 0) {
                field
                    .font(.pretendard(size: size.font, weight: bold ? .bold : .regular))
                    .foregroundStyle(GLGColor.textPrimary)
                    .multilineTextAlignment(alignment)
                    .keyboardType(keyboard)
                    .focused(focusBinding)
                    .tint(accent.primary)
                if let suffix, !text.isEmpty {
                    Text(suffix).font(.pretendard(size: size.font, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.leading, 4)
                }
                if let trailingSystemImage {
                    Image(systemName: trailingSystemImage).font(.system(size: 15)).foregroundStyle(GLGColor.textSecondary).padding(.leading, 8)
                }
            }
            .padding(.horizontal, size.padH)
            .padding(.vertical, axis == .vertical ? 12 : 0)
            .frame(minHeight: size.height)
            .background(focused || error != nil ? Color.white : Color(hex: 0xFFF5F8F8), in: RoundedRectangle(cornerRadius: size.radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: size.radius, style: .continuous)
                .strokeBorder(error != nil ? GLGColor.dangerText : (focused ? accent.primary : .clear), lineWidth: 1.5))
            .contentShape(Rectangle())
            .onTapGesture { if let onTap { onTap() } else { focusBinding.wrappedValue = true } }
            .animation(.easeOut(duration: 0.15), value: focused)
            if let msg = error ?? helper {
                Text(msg).font(.pretendard(size: 12)).foregroundStyle(error != nil ? GLGColor.dangerText : GLGColor.textSecondary)
                    .padding(.top, 6).padding(.leading, 2)
            }
        }
    }

    @ViewBuilder private var field: some View {
        let prompt = Text(placeholder).foregroundStyle(Color(hex: 0xFFA7B1AE))
        if onTap != nil {
            Text(text.isEmpty ? placeholder : text)
                .foregroundStyle(text.isEmpty ? Color(hex: 0xFFA7B1AE) : GLGColor.textPrimary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: alignment == .trailing ? .trailing : .leading)
        } else if secure {
            SecureField("", text: $text, prompt: prompt)
        } else {
            TextField("", text: $text, prompt: prompt, axis: axis)
        }
    }
}

/// 입력필드 위 라벨 — 13 SemiBold #6C727A, 아래 6.
struct GldsFieldLabel: View {
    let text: String
    var body: some View {
        Text(text).font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 6)
    }
}
