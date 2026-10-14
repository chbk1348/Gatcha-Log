import SwiftUI

#if DEBUG
/// **개발자 전용** — GLDS 버튼을 몇 가지 테마로 한 화면에 늘어놓는다.
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
                            GldsButton(title: "취소", variant: .secondary) {}
                            GldsButton(title: "저장하기") {}
                        }
                        GldsButton(title: "지출 추가", size: .l) {}
                        HStack(spacing: 8) {
                            GldsButton(title: "교환", variant: .secondary, size: .xs, fullWidth: false) {}
                            GldsButton(title: "재연동", size: .s, fullWidth: false) {}
                            GldsButton(title: "삭제", variant: .danger, size: .s, fullWidth: false) {}
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
// GLDS — 앱 공용 버튼 규격 (2026-09-30). Android `ui/components/Glds.kt` 와 값이 같다.
// 두 플랫폼을 픽셀까지 맞추려고 시스템 스타일 대신 직접 그린다(글자도 Pretendard).
// ════════════════════════════════════════════════════════════════════════════

enum GldsVariant { case primary, secondary, neutral, inverse, danger, onTint, text }

/// 높이 · 반경 · 글자 · 아이콘 · 간격 · 좌우 여백(내용 폭일 때).
enum GldsSize {
    case l, m, s, xs
    var height: CGFloat { switch self { case .l: 50; case .m: 44; case .s: 36; case .xs: 28 } }
    var radius: CGFloat { switch self { case .l, .m: 16; case .s: 12; case .xs: 9 } }
    var font: CGFloat { switch self { case .l, .m: 15; case .s: 13; case .xs: 12 } }
    var icon: CGFloat { switch self { case .l: 16; case .m: 15; case .s: 13; case .xs: 12 } }  // SF Symbol pt ≈ Android 18/17/15/14dp
    var gap: CGFloat { switch self { case .l: 8; case .m: 7; case .s: 6; case .xs: 4 } }
    var padH: CGFloat { switch self { case .l: 20; case .m: 18; case .s: 14; case .xs: 12 } }
}

/// GLDS 버튼. `fullWidth` 면 가로 전체, 아니면 내용 폭 + 좌우 여백. 누르면 0.97배 + 면이 한 단 진해진다.
struct GldsButton: View {
    let title: String
    var variant: GldsVariant = .primary
    var size: GldsSize = .m
    var systemImage: String? = nil
    var fullWidth: Bool = true
    var loading: Bool = false
    let action: () -> Void

    @Environment(\.isEnabled) private var enabled

    var body: some View {
        Button(action: action) {
            Group {
                if loading {
                    GldsSpinner(size: size.icon + 3, lineWidth: 2, inheritForeground: true)
                } else {
                    // 글자는 **자르지 않는다**(9/30) — 폭이 좁게 정해진 버튼(듀오의 호요랜드 히어로)에서 좌우 여백을
                    // 챙기느라 한 글자로 잘렸다. 아이콘까지 안 들어가면 아이콘을 빼고, 그래도 좁으면 여백 쪽으로 넘쳐
                    // 가운데를 지킨다(Android 와 같다).
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: size.gap) {
                            if let systemImage { Image(systemName: systemImage).font(.system(size: size.icon, weight: .semibold)) }
                            Text(title).font(.pretendard(size: size.font, weight: .bold)).lineLimit(1)
                        }
                        .fixedSize()
                        Text(title).font(.pretendard(size: size.font, weight: .bold)).lineLimit(1).fixedSize()
                    }
                }
            }
            .padding(.horizontal, size.padH)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .frame(height: size.height)
        }
        .buttonStyle(GldsButtonStyle(variant: variant, size: size))
        .disabled(loading)
    }
}

struct GldsButtonStyle: ButtonStyle {
    let variant: GldsVariant
    let size: GldsSize
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

// ════════════════════════════════════════════════════════════════════════════
// GLDS 칩(9/30) — 필터 · 선택용 알약. Android `GldsChip` 과 같은 값.
// 높이 36 · 좌우 14 · 14 Bold(10/12 에 32 · 12 · 13 에서 키웠다 — 높이는 GLDS 버튼 S 와 같다). 기본 흰 면 + 1 #E3E5EA, 선택은 **면을 채우지 않고** 강조색 1.5 테두리 + deep 글자
// (Menu 라벨의 글래스 모프 때 채운 면이 번져 보였다). dropdown 은 ▾, removable 은 ✕.
// ════════════════════════════════════════════════════════════════════════════

/// 모양만 — `Menu` 라벨처럼 버튼이 따로 있는 자리에서 쓴다.
struct GldsChipLabel: View {
    let label: String
    var selected: Bool = false
    var dropdown: Bool = false
    var removable: Bool = false
    /// 선택색 — 게임 칩처럼 칸 자체가 색을 갖는 자리. nil 이면 강조색(글자는 deep).
    var color: Color? = nil
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let line = color ?? accent.primary
        let fg = selected ? (color ?? accent.deep) : GLGColor.textPrimary
        HStack(spacing: 4) {
            Text(label).font(.pretendard(size: 14, weight: .bold)).lineLimit(1)
            if dropdown { Image(systemName: "chevron.down").font(.system(size: 11, weight: .bold)).foregroundStyle(selected ? fg : GLGColor.textSecondary) }
            if removable { Image(systemName: "xmark").font(.system(size: 11, weight: .bold)) }
        }
        .foregroundStyle(fg)
        .padding(.horizontal, 14)
        .frame(height: 36)
        .background(Color.white, in: Capsule())
        .overlay(Capsule().strokeBorder(selected ? line : Color(hex: 0xFFE3E5EA), lineWidth: selected ? 1.5 : 1))
        .contentShape(Capsule())
        .animation(.easeOut(duration: 0.15), value: selected)
    }
}

struct GldsChip: View {
    let label: String
    var selected: Bool = false
    var dropdown: Bool = false
    var removable: Bool = false
    var color: Color? = nil
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            GldsChipLabel(label: label, selected: selected, dropdown: dropdown, removable: removable, color: color)
        }
        .buttonStyle(GldsChipPress())
    }
}

private struct GldsChipPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.95 : 1).animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// GLDS 드롭다운(10/12) — 칩을 눌러 여는 선택 목록. Android `GlgDropdownMenu` · `GlgDropdownItem` 과 같은 값.
// 흰 면 · 모서리 20 · 강조색 30% 1.5 테두리 · 위아래 6 · 폭 160~280. 항목은 좌우 16 · 위아래 11 · 14 Medium,
// 선택은 강조색 SemiBold + 오른쪽 체크 18.
//
// 시스템 `Menu` 를 쓰지 않는 이유: 항목 글자가 시스템 크기(17)로 고정이라 앱의 다른 글자(12~15)보다 크게 뜨고
// 바꿀 방법이 없다(「지출 필터 글자가 너무 크다」, 10/12). 화면이 직접 그려 Android 와 같은 크기 · 모양으로 맞춘다.
//
// 쓰는 법: 칩에 `.gldsMenuAnchor("id")` 를 달고, 그 칩들을 품은 화면(스크롤뷰)에 `.gldsDropdownHost(open:) { id in … }`
// 를 단다. 열린 메뉴는 화면 맨 위 층에 그려져 목록에 가리거나 잘리지 않고, 바깥을 누르면 닫힌다.
// ════════════════════════════════════════════════════════════════════════════

/// 칩의 자리 — 메뉴를 그 아래에 붙이려고 화면 쪽으로 올려 보낸다.
struct GldsMenuAnchorKey: PreferenceKey {
    static let defaultValue: [String: Anchor<CGRect>] = [:]
    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}

/// 드롭다운의 항목 한 줄.
struct GldsDropdownItem: View {
    let text: String
    var selected: Bool = false
    let action: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Text(text)
                    .font(.pretendard(size: 14, weight: selected ? .semibold : .medium))
                    .foregroundStyle(selected ? accent.primary : GLGColor.textPrimary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if selected {
                    Image(systemName: "checkmark").font(.system(size: 13, weight: .bold))
                        .foregroundStyle(accent.primary).frame(width: 18, height: 18)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 11)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

extension View {
    /// 이 칩 아래에 [id] 의 드롭다운이 붙는다.
    func gldsMenuAnchor(_ id: String) -> some View {
        anchorPreference(key: GldsMenuAnchorKey.self, value: .bounds) { [id: $0] }
    }

    /// 드롭다운이 그려질 자리 — [open] 이 가리키는 칩 바로 아래(간격 6), 칩의 왼쪽 끝에 맞춰.
    /// 화면 오른쪽을 넘으면 넘지 않는 데까지만 왼쪽으로 민다. 바깥을 누르면 닫는다.
    func gldsDropdownHost<Items: View>(open: Binding<String?>, @ViewBuilder items: @escaping (String) -> Items) -> some View {
        overlayPreferenceValue(GldsMenuAnchorKey.self) { anchors in
            GeometryReader { proxy in
                if let id = open.wrappedValue, let anchor = anchors[id] {
                    let chip = proxy[anchor]
                    ZStack(alignment: .topLeading) {
                        Color.clear.contentShape(Rectangle())
                            .onTapGesture { open.wrappedValue = nil }
                            .accessibilityHidden(true)
                        HStack(spacing: 0) {
                            // 칩의 왼쪽 끝까지 — 자리가 모자라면 이 칸이 먼저 줄어 메뉴가 화면 안에 남는다.
                            Color.clear.frame(maxWidth: max(0, chip.minX), maxHeight: 0).layoutPriority(1)
                            GldsDropdownPanel { items(id) }.layoutPriority(2)
                            Spacer(minLength: 12)
                        }
                        .padding(.top, chip.maxY + 6)
                    }
                    .transition(.opacity)
                }
            }
            .animation(GLGMotion.standard(GLGMotion.durationShort), value: open.wrappedValue)
        }
    }
}

/// 드롭다운의 면 — 가장 긴 항목에 폭을 맞춘다(160~280).
private struct GldsDropdownPanel<Items: View>: View {
    @ViewBuilder var items: Items
    @Environment(\.glgAccent) private var accent

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { items }
            .padding(.vertical, 6)
            .frame(minWidth: 160, maxWidth: 280)
            .fixedSize()
            .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(accent.primary.opacity(0.30), lineWidth: 1.5))
            .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: 8)
            .accessibilityAddTraits(.isModal)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// GLDS 로딩 스피너(9/30) — Android `CircularProgressIndicator`(Material 원형 무한 로딩)와 같은 모양.
// 강조색 호가 돌면서 늘었다 줄었다 한다. 시스템 ProgressView(톱니 모양)를 쓰지 않는다 — 두 플랫폼 로딩 모양을 하나로.
// ════════════════════════════════════════════════════════════════════════════

struct GldsSpinner: View {
    var size: CGFloat = 20
    var lineWidth: CGFloat = 2.5
    /// nil 이면 강조색. `inheritForeground` 면 둘러싼 글자색(버튼 안 등)을 따른다.
    var color: Color? = nil
    var inheritForeground: Bool = false
    @Environment(\.glgAccent) private var accent

    var body: some View {
        TimelineView(.animation) { ctx in
            let arc = Self.arc(ctx.date.timeIntervalSinceReferenceDate)
            let ring = Circle().trim(from: 0, to: arc.sweep / 360)
                .rotation(.degrees(arc.start - 90))
            let style = StrokeStyle(lineWidth: lineWidth, lineCap: .round)
            if inheritForeground { ring.stroke(style: style) }
            else { ring.stroke(color ?? accent.primary, style: style) }
        }
        .frame(width: size, height: size)
        .accessibilityLabel("불러오는 중")
    }

    /// Material 무한 원형의 근사 — 1.33초마다 호가 10°→260° 로 늘었다가 꼬리가 따라와 다시 줄고,
    /// 전체는 초당 180° 로 돈다. 주기마다 시작점이 250° 씩 앞으로 가 매번 다른 자리에서 늘어난다.
    static func arc(_ t: Double) -> (start: Double, sweep: Double) {
        let period = 1.333
        let cycles = t / period
        let c = cycles.rounded(.down), f = cycles - c
        func ease(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
        let grow = ease(min(1, f * 2)), shrink = ease(max(0, f * 2 - 1))
        let sweep = 10 + 250 * (grow - shrink)
        let start = (c * 250 + 250 * shrink + t * 180).truncatingRemainder(dividingBy: 360)
        return (start, sweep)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// GLDS 다이얼로그(10/14) — Android `GlgDialog` 와 같은 값 · 같은 인자.
// 딤(검정 32%) + 가운데 흰 카드: 모서리 24 · 안쪽 22 · 1 테두리 · 바깥 여백 24. 제목 17 Bold + 아래 16(비면 줄째 생략),
// 본문, 버튼 위 20, 버튼은 GLDS 버튼 M 을 취소(Secondary) 1 : 확인(Primary) 1.4 — 취소 글자가 없으면 확인 하나를 전체 폭으로.
// 바깥을 누르면 닫힌다(`dismissable = false` 면 안 닫힌다).
//
// 띄우는 법: 화면에 `.glgDialogCover(isPresented:) { GlgDialog(…) { 본문 } }` 를 달고, 그 상태는 `glgDialogStill { … }` 안에서
// 바꾼다. 덮개(fullScreenCover)라 내비게이션 바 · 탭 바 · 시트 위까지 덮고, 덮개의 밀려 올라오는 움직임은 끈 채 모달이
// 제자리에서 나타나고 사라진다. 닫을 때 `onDismiss` · `onConfirm` 은 사라진 **뒤에** 불린다.
// ════════════════════════════════════════════════════════════════════════════

struct GlgDialog<Content: View>: View {
    let title: String
    var confirmText: String = "저장"
    var dismissText: String? = "취소"
    var dismissable: Bool = true
    let onDismiss: () -> Void
    let onConfirm: () -> Void
    @ViewBuilder let content: () -> Content

    @State private var shown = false

    var body: some View {
        ZStack {
            Color.black.opacity(0.32).ignoresSafeArea()
                .onTapGesture { if dismissable { close(onDismiss) } }
            VStack(alignment: .leading, spacing: 0) {
                if !title.isEmpty {
                    Text(title).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.bottom, 16)
                }
                content().frame(maxWidth: .infinity, alignment: .leading)
                buttons.padding(.top, 20)
            }
            .padding(22)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(GLGColor.divider, lineWidth: 1))
            // 한 장으로 합친 뒤에 그림자를 준다 — 그냥 주면 카드 안의 버튼 · 글자마다 그림자가 따로 생긴다.
            .compositingGroup()
            .shadow(color: .black.opacity(0.18), radius: 24, y: 8)
            .padding(24)
            .frame(maxWidth: 480)
            .scaleEffect(shown ? 1 : 0.96)
        }
        .opacity(shown ? 1 : 0)
        .task { withAnimation(.easeOut(duration: 0.16)) { shown = true } }
    }

    @ViewBuilder private var buttons: some View {
        if let dismissText {
            GeometryReader { geo in
                let w = geo.size.width - 10
                HStack(spacing: 10) {
                    GldsButton(title: dismissText, variant: .secondary) { close(onDismiss) }
                        .frame(width: w / 2.4)
                    GldsButton(title: confirmText) { close(onConfirm) }
                        .frame(width: w * 1.4 / 2.4)
                }
            }
            .frame(height: 44)
        } else {
            GldsButton(title: confirmText) { close(onConfirm) }
        }
    }

    /// 흐려진 뒤에 닫는다 — 덮개는 움직임 없이 사라지므로 여기서 사라지는 모습을 그린다.
    private func close(_ then: @escaping () -> Void) {
        withAnimation(.easeIn(duration: 0.12)) { shown = false }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(130))
            then()
        }
    }
}

/// 모달 덮개를 띄우거나 닫는 상태 변경을 감싼다 — 덮개가 밀려 올라오는 움직임을 끈다(모달이 제자리에서 나타난다).
func glgDialogStill(_ change: () -> Void) {
    var still = Transaction()
    still.disablesAnimations = true
    withTransaction(still, change)
}

extension View {
    /// 가운데 모달을 **화면 전체 위에** 띄운다 — 내비게이션 바 · 탭 바 · 시트까지 덮는다. 상태는 [glgDialogStill] 안에서 바꾼다.
    func glgDialogCover<D: View>(isPresented: Binding<Bool>, @ViewBuilder dialog: @escaping () -> D) -> some View {
        fullScreenCover(isPresented: isPresented) { dialog().presentationBackground(.clear) }
    }
}
