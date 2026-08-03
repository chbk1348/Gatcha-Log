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
            .font(.pretendard(size: 13, weight: .bold))
            .glgGlassChipStyle(selected: selected)
            .tint(tint ?? accent.primary)
    }
}

extension View {
    /// 시스템 글래스 칩 스타일. `controlSize(.regular)` — 손가락으로 집는 줄이라 `.small` 로는
    /// 작다는 지적(2026-08-03)이 있어 한 단계 키웠다. 폭·높이는 시스템이 정한다.
    ///
    /// ⚠️ **`Menu` 라벨에는 `selected: true` 를 주지 말 것**(2026-08-03 실기기 확인).
    /// iOS 26+ 는 메뉴를 소스 버튼에서 뽑아내듯 모프시키고 닫을 때 역재생하는데, 소스가
    /// `.glassProminent`(강조색 채움) 캡슐이면 닫히는 내내 색 덩어리가 스쳐 보인다.
    /// 시스템 애니메이션이라 `.transaction { $0.animation = nil }` 로도 못 막는다(시도·실패).
    /// 메뉴 라벨에서 선택 상태를 알려야 하면 채움 말고 **색**으로 — 지출 퀵필터(`quickMenu`) 참고.
    /// `Button` 은 모프 대상이 아니라 `selected: true` 를 그대로 써도 된다(✕ 해제 칩·날짜 알약).
    @ViewBuilder
    func glgGlassChipStyle(selected: Bool) -> some View {
        if #available(iOS 26.0, *) {
            if selected {
                self.buttonStyle(.glassProminent).buttonBorderShape(.capsule).controlSize(.regular)
            } else {
                self.buttonStyle(.glass).buttonBorderShape(.capsule).controlSize(.regular)
            }
        } else {
            if selected {
                self.buttonStyle(.borderedProminent).buttonBorderShape(.capsule).controlSize(.regular)
            } else {
                self.buttonStyle(.bordered).buttonBorderShape(.capsule).controlSize(.regular)
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
