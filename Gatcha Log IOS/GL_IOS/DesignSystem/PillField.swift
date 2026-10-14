import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// GLDS 입력필드 (2026-09-30 · 2.1 10/14) — Android `GldsTextField` 와 값이 같다.
// 채운 면 #F5F8F8, 포커스 때 흰 면 + 강조색 1.5 테두리, 오류면 빨강 테두리 + 아래 문구.
// ════════════════════════════════════════════════════════════════════════════

/// 입력필드 크기 — M 은 폼 기본, S 는 목록 행 안의 짧은 숫자칸.
/// GLDS 2.1 입력필드(1-C) 에서 L 은 두지 않기로 했다 — 큰 입력은 금액 카드(`GldsAmountCard`)가 맡는다.
/// 값의 이름은 `size/field/m` · `radius/field/m` · `type/field/m` · `space/field/pad-m`(S 도 같은 꼴).
enum GldsFieldSize {
    case m, s
    var height: CGFloat { self == .m ? 48 : 38 }
    var radius: CGFloat { self == .m ? 14 : 12 }
    var font: CGFloat { self == .m ? 16 : 14 }
    var padH: CGFloat { self == .m ? 14 : 12 }
}

/// 입력필드 값 — GLDS 2.1 입력필드(7-A) 의 역할 경로 이름을 그대로 옮겼다(`color/field/bg` → `bg`). Android `GldsField` 와 이름 · 값이 같다.
/// 크기마다 다른 값은 `GldsFieldSize` 가, 글자 · 오류 · 강조색(`color/text/primary` · `secondary` · `color/danger/text` ·
/// `color/accent/primary`)은 앱 공용 색(`GLGColor.textPrimary` · `textSecondary` · `dangerText` · `accent.primary`)이 갖는다.
enum GldsField {
    /// `color/field/bg` — 평소 면.
    static let bg = Color(hex: 0xFFF5F8F8)
    /// `color/field/bg-focused` — 포커스 · 오류 때 면.
    static let bgFocused = Color.white
    /// Disabled 면(2-B) — 버튼 Neutral 면과 같은 색이라 「면이 바뀌면 못 누른다」가 버튼과 같은 말로 읽힌다. 7-A 표에는 이름이 없다.
    static let bgDisabled = Color(hex: 0xFFECEFF4)
    /// `color/field/placeholder` — 자리표시자. Disabled 아이콘 · 지우기 ✕ 도 이 색이다(2-B · 5-A).
    static let placeholder = Color(hex: 0xFFA7B1AE)
    /// `space/field/label` — 라벨 아래.
    static let spaceLabel: CGFloat = 6
    /// `space/field/fix` — Prefix 뒤 · Suffix 앞.
    static let spaceFix: CGFloat = 4
    /// `space/field/icon` — Leading Icon 뒤 · 지우기 ✕ · Trailing Icon 앞.
    static let spaceIcon: CGFloat = 8
    /// `type/field/label` — 13 SemiBold.
    static let typeLabel = Font.pretendard(size: 13, weight: .semibold)
    /// `type/field/helper` — 12.
    static let typeHelper = Font.pretendard(size: 12)
}

/// 입력필드 안에 넣을 수 있는 아이콘 목록(GLDS 2.1 입력필드 6-A) — 피그마 Instance swap 과 같은 이름을 쓴다.
/// 그림은 플랫폼 것(SF Symbol ↔ Android Material)이고, 목록 밖 아이콘은 가이드에 먼저 올린 뒤 여기에 더한다. Android `GldsIcon` 과 같다.
enum GldsIcon {
    case calendar, search, clear
    var systemName: String {
        switch self {
        case .calendar: "calendar"            // Android CalendarToday
        case .search: "magnifyingglass"       // Android Search
        case .clear: "xmark.circle.fill"      // Android Cancel — 지우기 ✕(5-A)
        }
    }
}

/// 한 줄 순서는 `leadingIcon` → `prefix` → 값 → `suffix` → 지우기 ✕ → `trailingIcon`.
/// `prefix` · `suffix` 는 값 앞뒤에 붙는 글자(「#」 · 「원」)로 값이 있을 때만 보인다.
/// `clearable` 이면 포커스 중이고 값이 있을 때 ✕ 를 띄워 값을 비운다(5-A).
/// 못 쓰게 하려면 `.disabled(true)` 를 건다(`GldsButton` 과 같은 방식) — 면 #ECEFF4 · 값 #6C727A · 아이콘 #A7B1AE 로 바뀌고,
/// `error` 보다 먼저라 오류 대신 `helper` 를 보인다(2-B). Android 는 `enabled` 인자다.
struct GldsTextField: View {
    var label: String? = nil
    let placeholder: String
    @Binding var text: String
    var size: GldsFieldSize = .m
    var leadingIcon: GldsIcon? = nil
    var prefix: String? = nil
    var suffix: String? = nil
    var clearable: Bool = false
    var trailingIcon: GldsIcon? = nil
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
    @Environment(\.isEnabled) private var enabled
    @FocusState private var ownFocus: Bool
    private var focusBinding: FocusState<Bool>.Binding { focus ?? $ownFocus }
    private var focused: Bool { focusBinding.wrappedValue && enabled }
    /// Disabled 가 오류보다 먼저다(2-B) — 고칠 수 없는 칸에 오류를 띄우면 할 수 있는 일이 없다.
    private var shownError: String? { enabled ? error : nil }
    private var iconColor: Color { enabled ? GLGColor.textSecondary : GldsField.placeholder }
    private var valueColor: Color { enabled ? GLGColor.textPrimary : GLGColor.textSecondary }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let label { GldsFieldLabel(text: label) }
            HStack(spacing: 0) {
                if let leadingIcon {
                    GldsFieldIcon(icon: leadingIcon, color: iconColor).padding(.trailing, GldsField.spaceIcon)
                }
                // Prefix 는 Suffix 의 거울이다(4-A) — 값이 비면 숨겨 placeholder 와 한 문장처럼 붙지 않게 한다.
                if let prefix, !text.isEmpty {
                    Text(prefix).font(.pretendard(size: size.font, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.trailing, GldsField.spaceFix)
                }
                field
                    .font(.pretendard(size: size.font, weight: bold ? .bold : .regular))
                    .foregroundStyle(valueColor)
                    .multilineTextAlignment(alignment)
                    .keyboardType(keyboard)
                    .focused(focusBinding)
                    .tint(accent.primary)
                if let suffix, !text.isEmpty {
                    Text(suffix).font(.pretendard(size: size.font, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.leading, GldsField.spaceFix)
                }
                // ✕ 는 Trailing Icon 앞이다(5-A) — 나타나도 오른쪽 끝 아이콘 자리가 흔들리지 않는다.
                if clearable, focused, !text.isEmpty {
                    Button { text = ""; focusBinding.wrappedValue = true } label: {
                        GldsFieldIcon(icon: .clear, color: GldsField.placeholder)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    // 누름 영역은 44(버튼 M 높이)인데 자리는 그림(18)만큼만 차지한다 — 44 틀을 18 자리 가운데에 겹쳐 놓는다.
                    .padding(-13)
                    .padding(.leading, GldsField.spaceIcon)
                }
                if let trailingIcon {
                    GldsFieldIcon(icon: trailingIcon, color: iconColor).padding(.leading, GldsField.spaceIcon)
                }
            }
            .padding(.horizontal, size.padH)
            .padding(.vertical, axis == .vertical ? 12 : 0)
            .frame(minHeight: size.height)
            .background(!enabled ? GldsField.bgDisabled : (focused || shownError != nil ? GldsField.bgFocused : GldsField.bg),
                        in: RoundedRectangle(cornerRadius: size.radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: size.radius, style: .continuous)
                .strokeBorder(shownError != nil ? GLGColor.dangerText : (focused ? accent.primary : .clear), lineWidth: 1.5))
            .contentShape(Rectangle())
            .onTapGesture {
                guard enabled else { return }
                if let onTap { onTap() } else { focusBinding.wrappedValue = true }
            }
            .animation(.easeOut(duration: 0.15), value: focused)
            if let msg = shownError ?? helper {
                Text(msg).font(GldsField.typeHelper).foregroundStyle(shownError != nil ? GLGColor.dangerText : GLGColor.textSecondary)
                    .padding(.top, 6).padding(.leading, 2)
            }
        }
    }

    @ViewBuilder private var field: some View {
        let prompt = Text(placeholder).foregroundStyle(GldsField.placeholder)
        if onTap != nil {
            Text(text.isEmpty ? placeholder : text)
                .foregroundStyle(text.isEmpty ? GldsField.placeholder : valueColor)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: alignment == .trailing ? .trailing : .leading)
        } else if secure {
            SecureField("", text: $text, prompt: prompt)
        } else {
            TextField("", text: $text, prompt: prompt, axis: axis)
        }
    }
}

/// 칸 안 아이콘 — Leading · Trailing · 지우기 ✕ 모두 18 틀(GLDS 2.1 입력필드 8-A).
/// SF Symbol 15pt 를 18 × 18 틀에 담는다 — Material 18dp 아이콘도 그림은 틀보다 작게 그려져서 두 플랫폼 그림 크기가 비슷해진다.
private struct GldsFieldIcon: View {
    let icon: GldsIcon
    let color: Color
    var body: some View {
        Image(systemName: icon.systemName).font(.system(size: 15)).foregroundStyle(color).frame(width: 18, height: 18)
    }
}

/// 입력필드 위 라벨 — 13 SemiBold #6C727A, 아래 6. Disabled 여도 색은 그대로다(2-B).
struct GldsFieldLabel: View {
    let text: String
    var body: some View {
        Text(text).font(GldsField.typeLabel).foregroundStyle(GLGColor.textSecondary).padding(.bottom, GldsField.spaceLabel)
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
