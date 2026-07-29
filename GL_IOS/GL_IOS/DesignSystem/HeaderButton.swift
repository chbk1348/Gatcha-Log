import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 헤더 버튼 — 시스템 버튼 스타일을 따른다 (iOS 26 = Liquid Glass, 18~25 = bordered).
// 시스템 디자인 원칙: 커스텀 배경/테두리 대신 .glass / .bordered 시스템 버튼 스타일 사용.
// ════════════════════════════════════════════════════════════════════════════

extension View {
    /// 시스템 글래스 버튼 스타일. circle=true 면 원형(아이콘 전용), 아니면 캡슐(아이콘+라벨).
    ///
    /// [size] 는 시스템 컨트롤 크기 — 헤더·칩은 기본 `.small`, 콘텐츠 위에 떠 있는 단독 버튼
    /// (예: '맨 위로')은 손가락으로 눌러야 하므로 `.large` 를 쓴다.
    @ViewBuilder
    func glgGlassButton(circle: Bool = false, size: ControlSize = .small) -> some View {
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glass)
                .buttonBorderShape(circle ? .circle : .capsule)
                .controlSize(size)
        } else {
            self.buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                .controlSize(size)
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
            Image(systemName: systemImage).font(.pretendard(size: 15, weight: .semibold))
        }
        .glgGlassButton(circle: true)
        .tint(accent.primary)
        .disabled(disabled)
        .opacity(disabled ? 0.5 : 1)
    }
}

/// 시스템 글래스 **칩** — 선택 상태가 있는 작은 알약. 지출 리스트 퀵필터처럼
/// 본문 위에 상시 얹히는 줄에서 쓴다(커스텀 칩 `GLGChip` 은 필터 시트처럼 칩이 주인공인 화면용).
///
/// 선택은 prominent(채움)로 구분한다 — iOS 26 은 `.glassProminent`, 그 이하는 `.borderedProminent`.
struct GLGGlassChip: View {
    let label: String
    var selected: Bool = false
    var tint: Color? = nil
    let action: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        Button(action: action) { Text(label) }
            .font(.pretendard(size: 12, weight: .bold))
            .glgGlassChipStyle(selected: selected)
            .tint(tint ?? accent.primary)
    }
}

extension View {
    /// 시스템 글래스 칩 스타일. `controlSize(.small)` — 리스트 위에 얹히므로 작게 유지한다.
    @ViewBuilder
    func glgGlassChipStyle(selected: Bool) -> some View {
        if #available(iOS 26.0, *) {
            if selected {
                self.buttonStyle(.glassProminent).buttonBorderShape(.capsule).controlSize(.small)
            } else {
                self.buttonStyle(.glass).buttonBorderShape(.capsule).controlSize(.small)
            }
        } else {
            if selected {
                self.buttonStyle(.borderedProminent).buttonBorderShape(.capsule).controlSize(.small)
            } else {
                self.buttonStyle(.bordered).buttonBorderShape(.capsule).controlSize(.small)
            }
        }
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
        .font(.pretendard(size: 12, weight: .bold))
        .glgGlassButton(circle: false)
        .tint(accent.primary)
    }
}
