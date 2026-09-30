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
// 높이 32 · 좌우 12 · 13 Bold. 기본 흰 면 + 1 #E3E5EA, 선택은 **면을 채우지 않고** 강조색 1.5 테두리 + deep 글자
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
        HStack(spacing: 3) {
            Text(label).font(.pretendard(size: 13, weight: .bold)).lineLimit(1)
            if dropdown { Image(systemName: "chevron.down").font(.system(size: 10, weight: .bold)).foregroundStyle(selected ? fg : GLGColor.textSecondary) }
            if removable { Image(systemName: "xmark").font(.system(size: 10, weight: .bold)) }
        }
        .foregroundStyle(fg)
        .padding(.horizontal, 12)
        .frame(height: 32)
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
