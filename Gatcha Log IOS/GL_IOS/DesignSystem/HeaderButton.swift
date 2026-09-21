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

extension View {
    /// 콘텐츠 **위에 떠 있는** 원형 아이콘 버튼 — 지출 리스트의 '맨 위로'처럼 스크롤되는
    /// 배경 위에 얹히는 자리에 쓴다. (툴바 안에 앉는 헤더 버튼은 `glgGlassButton` 쪽)
    ///
    /// iOS 26+ 는 시스템 글래스를 그대로 쓴다 — 굴절·명암·다크모드를 OS 가 잡아 준다.
    ///
    /// iOS 18 은 `glgGlassButton` 을 쓸 수 없다. `.bordered` 는 **틴트를 옅게 깐 캡슐**이라
    ///   ① `circle` 인자를 줘도 알약으로 나오고(레거시 분기가 `.capsule` 고정),
    ///   ② 채움이 너무 옅어 밑을 지나가는 카드·금액이 그대로 비쳐 읽히지 않는다.
    /// 툴바 위에서는 배경이 단색이라 문제가 안 되지만, 스크롤 콘텐츠 위에서는 드러난다.
    /// 그래서 이 구간만 **불투명 흰 원 + 아웃라인 + 그림자**로 직접 그린다.
    @ViewBuilder
    func glgFloatingCircleButton(tint: Color, diameter: CGFloat = 48) -> some View {
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.large)
                .tint(tint)
        } else {
            self.buttonStyle(.plain)
                .foregroundStyle(tint)
                .frame(width: diameter, height: diameter)
                .background {
                    Circle()
                        .fill(GLGColor.backgroundGradientEnd)
                        .overlay(Circle().strokeBorder(GLGColor.glassBorder, lineWidth: 0.5))
                        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
                }
        }
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

/**
 시트 닫기 버튼 — **시스템 규격**.

 iOS 26 이 닫기를 버튼 역할로 정식화했다(`Button(role: .close)`). 원형 ✕ 와 유리 재질을
 OS 가 그리므로, 글자 「닫기」를 직접 놓던 때와 달리 다른 앱의 시트와 같은 모양·같은 자리가
 된다. role 이 없는 그 아래 버전에서는 글자 버튼으로 떨어진다.

 **읽기 전용 시트만** 쓴다(내 입장권 · 예매 안내 · 점수 기준). 입력 폼 시트는 취소/저장 쌍을
 그대로 둔다 — 거기서 왼쪽 버튼이 하는 일은 닫기가 아니라 **입력을 버리는 취소**이고,
 시스템 규격도 그쪽은 `cancellationAction` 이다.
 */
struct GLGSheetCloseButton: View {
    private let action: () -> Void

    init(action: @escaping () -> Void) { self.action = action }

    var body: some View {
        // **시스템 닫기 역할에 맡긴다**(2026-09-21 지시 — 시트 버튼은 시스템 가이드라인을 따른다).
        //
        // 한때 ✕ 를 직접 그렸다. `Button(role: .close)` 가 툴바 밖에서는 「닫기」 글자로 떨어져
        // 같은 앱에 두 모양이 생긴다는 이유였는데(2026-09-17), 이 버튼은 **쓰는 자리가 전부
        // 툴바**다(시트 여섯 곳 모두 `ToolbarItem`). 그 조건에서는 시스템이 원형 ✕ 를 그려
        // 다른 앱 시트와 같은 모양·같은 자리가 된다.
        //
        // 면은 씌우지 않는다 — 툴바가 버튼 배경(유리 원)을 스스로 그린다.
        if #available(iOS 26.0, *) {
            Button(role: .close, action: action)
        } else {
            Button(action: action) {
                Image(systemName: "xmark").font(.system(size: 15, weight: .semibold))
            }
            .accessibilityLabel("닫기")
        }
    }
}

extension View {
    /**
     시트·폼의 **주 액션** 버튼 — 시스템 강조 버튼 스타일(iOS 26 = Liquid Glass prominent,
     18~25 = borderedProminent). 담기처럼 그 화면에서 할 일이 하나뿐인 자리에 쓴다.

     커스텀으로 그리던 것을 시스템에 맡긴다 — 눌림·비활성·다크모드·손가락 크기를 OS 가 잡는다.
     전체 폭이 필요하면 라벨에 `.frame(maxWidth: .infinity)` 를 준다(버튼 자체에 주면 탭
     영역만 늘고 면은 글자 크기에 머문다).
     */
    @ViewBuilder
    func glgProminentButton() -> some View {
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glassProminent).buttonBorderShape(.capsule).controlSize(.large)
        } else {
            self.buttonStyle(.borderedProminent).buttonBorderShape(.capsule).controlSize(.large)
        }
    }
}

// ── 내용에 맞춘 시트 높이 ──────────────────────────────────────────────────────

/**
 내용에 맞춘 시트 높이 — **재서 정하되, 되먹임은 끊는다.**

 `presentationDetents([.height(h)])` 의 `h` 를 시트 **안에서** 재면 「잰다 → 시트 높이가
 바뀐다 → 다시 잰다」가 돌 수 있다. 실제로 잰 값에 안전영역이 섞여 패스마다 시트가 커졌고,
 화면이 그대로 멈췄다(2026-09-17 바텀시트 프리징). 그래서 재는 값을 둘로 나눈다.

 - [glgSheetContentHeight] — **내용의 고유 높이.** 시트가 준 높이에 좌우되지 않는 뷰에만
   붙인다(자연 높이 `VStack`). `ScrollView` 처럼 준 높이를 그대로 받는 뷰에 붙이면 그것이
   바로 되먹임이다 — 그때는 스크롤 **안쪽** 내용에 붙인다.
 - [glgSheetChromeHeight] — **네비 바 + 아래 여백.** 시트가 어디까지 올라왔느냐에 따라
   달라지므로 **작아지는 쪽으로만** 따라간다. 늘지 않으니 되먹임이 돌지 않는다.

 시트 높이 = 둘의 합. 상수로 박지 않으니 큰 글씨에서도 맞고, 되풀이해 재지 않으니 돌지 않는다.
 */
extension View {
    /// 시트 **내용의 고유 높이**를 잰다 — 자연 높이 뷰에만 붙인다.
    func glgSheetContentHeight(_ height: Binding<CGFloat>) -> some View {
        onGeometryChange(for: CGFloat.self) { $0.size.height } action: { measured in
            #if DEBUG
            print("[GLG시트] content=\(measured)")
            #endif
            // 1pt 미만 차이는 흘린다 — 반올림 오차만으로 높이를 다시 잡지 않는다.
            if abs(measured - height.wrappedValue) >= 1 { height.wrappedValue = measured }
        }
    }

    /**
     시트 **테두리 높이**(네비 바 + 아래 여백)를 잰다.

     시트가 **올라오는 동안**에는 위쪽 안전영역에 상태바까지 섞여 들어온다. 그 첫 값을 붙들면
     자리 잡은 뒤에도 시트가 그만큼 큰 채로 남아 위아래가 빈다(2026-09-17 실측 124pt). 그래서
     **작아지는 쪽으로만** 따라간다 — 자리 잡은 뒤의 값이 곧 최솟값이고, 네비 바 높이는 시트
     크기와 무관하므로 거기서 멈춘다. 단조 감소라 「잰다 → 커진다 → 다시 잰다」가 성립하지 않는다.
     */
    func glgSheetChromeHeight(_ height: Binding<CGFloat>) -> some View {
        onGeometryChange(for: CGSize.self) {
            CGSize(width: $0.safeAreaInsets.top, height: $0.safeAreaInsets.bottom)
        } action: { insets in
            let total = insets.width + insets.height
            guard total > 0 else { return }
            #if DEBUG
            print("[GLG시트] chrome top=\(insets.width) bottom=\(insets.height)")
            #endif
            if height.wrappedValue == 0 || total < height.wrappedValue - 0.5 {
                height.wrappedValue = total
            }
        }
    }
}
