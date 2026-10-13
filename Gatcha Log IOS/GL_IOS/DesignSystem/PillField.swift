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

// ════════════════════════════════════════════════════════════════════════════
// GLDS 금액 카드 (2026-10-13) — Android `GldsAmountCard` 와 값이 같다.
// 가이드 「예산 금액 입력」(`BudgetAmountEditor`)의 **면**만 떼어 낸 것으로, 지출 추가 · 수정의 금액 칸이 쓴다.
// 온보딩 ③ · 설정 ▸ 예산 관리의 `BudgetAmountEditor` 와 값이 같다.
// ════════════════════════════════════════════════════════════════════════════

/// 색은 **틸 고정**이다(테마 강조색을 따르지 않는다).
///  - 카드: `#EEF8F8 → #DCF0EE` · 반경 22 · 안쪽 20 / 20 / 22.
///  - 라벨: 13 Bold `#177881`.
///  - 숫자: 40 Bold `#0F1A33` · 폭은 내용 · 자리표시자는 30%(`GldsAmountCardStyle.numberFont`).
///  - 「원」: 20 Bold · 숫자와 4 · 글자 밑선 맞춤.
///  - 주의(`warn`): `#FFF4E8 → #FFE2C7` · 라벨 · 숫자 · 「원」 `#C2410C` · 한 번 흔들린 뒤 안쪽 테두리(5 · `#EA580C` 16%)가 900ms 로 깜빡인다.
///
/// `amount` 는 숫자 자리(입력칸)다. 넘겨받는 색으로 글자를 칠한다.
struct GldsAmountCard<Amount: View>: View {
    let label: String
    let warn: Bool
    @ViewBuilder var amount: (Color) -> Amount

    @State private var shake: CGFloat = 0
    @State private var glow = false

    var body: some View {
        let color = warn ? GldsAmountCardStyle.warn : GldsAmountCardStyle.ink
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(warn ? GldsAmountCardStyle.warn : GldsAmountCardStyle.teal)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                amount(color)
                Text("원").font(.pretendard(size: 20, weight: .bold)).foregroundStyle(color)
            }
            .padding(.top, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 22)
        .background(LinearGradient(colors: warn ? [Color(hex: 0xFFFFF4E8), Color(hex: 0xFFFFE2C7)] : [Color(hex: 0xFFEEF8F8), Color(hex: 0xFFDCF0EE)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: shape)
        .overlay(shape.strokeBorder(Color(hex: 0xFFEA580C).opacity(warn && glow ? 0.16 : 0), lineWidth: 5))
        .offset(x: shake)
        .animation(.easeInOut(duration: 0.3), value: warn)
        .onChange(of: warn) { _, w in
            guard w else { glow = false; return }
            // 한 번 흔들림 + 테두리 깜빡임
            Task { @MainActor in
                for x in [-6.0, 5, -3, 2, 0] as [CGFloat] {
                    withAnimation(.easeInOut(duration: 0.084)) { shake = x }
                    try? await Task.sleep(nanoseconds: 84_000_000)
                }
                // 흔들리는 사이 주의가 풀렸으면 깜빡이지 않는다.
                guard warn else { return }
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { glow = true }
            }
        }
    }
}

/// `GldsAmountCard` 의 색 · 글자 모양.
enum GldsAmountCardStyle {
    static let ink = Color(hex: 0xFF0F1A33)
    static let teal = Color(hex: 0xFF177881)
    static let warn = Color(hex: 0xFFC2410C)
    /// 숫자 글자 모양 — 40 Bold.
    static let numberFont = Font.pretendard(size: 40, weight: .bold)
}
