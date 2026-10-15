import SwiftUI
import UIKit

// ════════════════════════════════════════════════════════════════════════════
// iOS 18 아이폰 헤더 — GLDS 헤더 C 「묶음 캡슐」(10/15 「IOS 18 > And 헤더 규칙, 디자인 따라갈것」).
//
// iOS 18 시스템 내비 바는 버튼에 면이 없어(아이콘 · 글자만 덩그러니) Android 의 흰 캡슐 헤더와 모양이 갈렸다.
// 떠 있는 탭 툴바([GLGFloatingTabBar])처럼 **시스템 바는 숨기고 직접 그린다.** 값은 Android
// `GlgComponents.kt` 의 GlgHeader* 와 같다 — 한쪽을 고치면 다른 쪽도 고친다.
//
//  - 요소 높이 48 · 아이콘 24(뒤로 ‹ 만 22) · 본문색. 강조색 아이콘은 쓰지 않는다.
//  - 따로 떨어진 버튼은 48 흰 원, 붙어 있는 버튼 여럿은 캡슐 하나(칸 48 × 48 · 안쪽 4 · 칸 사이 세로선 1 × 20 #E3E5EA).
//  - 면은 떠 있는 탭 툴바와 같다 — 흰 바탕 · 안쪽 테두리 1 [GLGColor.divider] · 그림자 검정 6% 반경 10 아래 2(바탕 도형에만).
//  - 줄: 위 12 + 48 + 아래 12(탭 뿌리, 72) · 아래 4(하위 페이지, 64). 좌우 16 · 요소 사이 8. Android 도 같은 날
//    아래 여백을 16/8 → 12/4 로 줄였다(요소가 44 → 48 로 커진 만큼 덜어 예전 높이로).
//  - 하위 페이지 왼쪽은 [‹ | 제목] 캡슐 하나 — 제목은 남은 폭을 받고 길면 말줄임. 제목을 숨긴 페이지(히어로)는 ‹ 원 하나.
//  - 헤더는 콘텐츠 **위에 떠 있다** — 본문이 그 아래로 지나간다. 버튼은 불투명이라 가릴 것은 상태바 글자뿐이어서
//    상태바 자리에만 흰 스크림(88% → 투명)을 깐다. 스크롤하기 전(맨 위)에는 숨긴다(Android 와 같다).
//
// **iOS 26 이상 · iPad · iPhone Duo 는 쓰지 않는다**([GldsHeader18.isActive]) — 거기서는 아무것도 하지 않아
// 기존 `.toolbar` (Liquid Glass) 가 그대로 산다. 그래서 화면들은 `.toolbar` 를 지우지 않고 헤더 쪽 버튼을 따로 넘긴다.
// ════════════════════════════════════════════════════════════════════════════

enum GldsHeader18 {
    /// 이 헤더를 쓰는가 — 떠 있는 탭 툴바와 같은 조건(iOS 26 미만 아이폰). 실행 중에 바뀌지 않는 값이라
    /// 수정자를 이 값으로 갈라도 내비게이션 구조가 재구성되지 않는다.
    @MainActor static var isActive: Bool { GLGFloatingTabBar.isActive }

    /// 헤더 요소 높이 — 원 버튼 · 캡슐 · 글자 알약 전부(Android `GlgHeaderElementHeight`).
    static let element: CGFloat = 48
    /// 헤더 아이콘 — 본문색 24(Android `GlgHeaderIconSize`).
    static let icon: CGFloat = 24
    /// 뒤로(‹)만 한 단 작은 22 — 획이 단순해 같은 크기면 혼자 커 보인다(Android `GlgHeaderBackIconSize`).
    static let backIcon: CGFloat = 22
    /// 묶음 캡슐 한 칸 — 높이와 같아 정사각이다.
    static let cell: CGFloat = 48
    /// 묶음 캡슐 안쪽 좌우 — 끝 칸 아이콘이 둥근 끝에 붙어 보이지 않게.
    static let groupInset: CGFloat = 4
    /// 헤더 요소 사이 · 좌우 여백.
    static let gap: CGFloat = 8
    static let side: CGFloat = 16
    static let top: CGFloat = 12
    /// 아래 여백 — 탭 뿌리 12 · 하위 페이지 4(10/15, Android `GlgTabHeaderBottom` · `GlgDetailHeaderBottom`).
    static let tabBottom: CGFloat = 12
    static let pageBottom: CGFloat = 4
    /// 탭 뿌리 헤더 높이(72) · 하위 페이지 헤더 높이(64). 상태바는 빼고 센다.
    static var tabHeight: CGFloat { top + element + tabBottom }
    static var pageHeight: CGFloat { top + element + pageBottom }
    /// 칸 사이 세로선 — GLDS 칩 · 탭 테두리와 같은 #E3E5EA. 헤어라인색은 흰 캡슐 위에서 너무 옅었다(Android 와 같다).
    static let dividerColor = Color(hex: 0xFFE3E5EA)
    /// 색이 깔린 히어로 위의 면 — 히어로 사실 박스와 같은 흰색 55%(지출 상세 · 캐릭터 상세, Android 와 같다).
    static let heroFill = Color.white.opacity(0.55)
    /// 상태바 스크림이 상태바 아래로 더 내려가는 높이 — 딱 맞추면 페이드가 급히 끊겨 가로선처럼 보인다(Android `GlgTopScrimFadeExtra`).
    static let scrimFade: CGFloat = 20

    /// 창의 위쪽 안전 영역(상태바 · 다이내믹 아일랜드). 헤더는 그 바로 아래에 서므로 스크림을 이만큼 위로 올린다.
    /// 아이폰은 세로 고정이라 한 번 잰 값이 바뀌지 않는다.
    @MainActor static var statusBarHeight: CGFloat {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let window = scene?.windows.first(where: \.isKeyWindow) ?? scene?.windows.first
        return window?.safeAreaInsets.top ?? 0
    }
}

// ── 면 ───────────────────────────────────────────────────────────────────────

extension View {
    /// 헤더 요소의 면 — 흰 바탕 · 안쪽 테두리 1 · 들뜸 그림자(바탕 도형에만 — 통째로 주면 아이콘 선마다 그림자가 진다).
    ///
    /// [fill] 을 넘기면(색이 깔린 히어로 위) 그 면만 깐다 — 히어로 면은 반투명이라 그림자 · 테두리를 두면 비쳐 탁해지고,
    /// 히어로와 한 층으로 읽혀야 하는 자리라 띄우지도 않는다(Android `glgHeaderSurface(fill)` 와 같다).
    /// 히어로를 벗어나며 [fill] 이 nil 로 바뀌어도 뷰 구조는 그대로 두고 값만 바꾼다 — 버튼이 다시 만들어지며 깜빡이지 않게.
    ///
    /// 한 캡슐로 합친 헤더 줄 안([gldsHeaderMerged])에서는 면을 그리지 않는다 — 합친 캡슐이 한 장으로 깐다.
    func gldsHeaderSurface<S: InsettableShape>(_ shape: S, fill: Color? = nil) -> some View {
        modifier(GldsHeaderSurfaceModifier(shape: shape, fill: fill))
    }

    /// 따로 떨어진 헤더 원 버튼 모양(48) — `NavigationLink` · `Menu` 라벨에도 쓴다.
    func gldsHeaderCircle(fill: Color? = nil) -> some View {
        self
            .frame(width: GldsHeader18.element, height: GldsHeader18.element)
            .contentShape(Circle())
            .gldsHeaderSurface(Circle(), fill: fill)
    }

    /// 묶음 캡슐 안 칸(기본 48 × 48) — 칸에는 면이 없다. 면은 캡슐이 한 장으로 깐다.
    func gldsHeaderCell(width: CGFloat = GldsHeader18.cell) -> some View {
        self
            .frame(width: width, height: GldsHeader18.element)
            .contentShape(Rectangle())
    }

    /// 글자 알약(저장 · 모두 지우기 · 필터 메뉴 …) — 높이 48 · 좌우 16 · 흰 캡슐 면.
    func gldsHeaderPill(fill: Color? = nil) -> some View {
        self
            .padding(.horizontal, 16)
            .frame(height: GldsHeader18.element)
            .contentShape(Capsule())
            .gldsHeaderSurface(Capsule(), fill: fill)
    }
}

private struct GldsHeaderSurfaceModifier<S: InsettableShape>: ViewModifier {
    let shape: S
    let fill: Color?
    @Environment(\.gldsHeaderMerged) private var merged

    func body(content: Content) -> some View {
        // 합쳐졌을 때도 뷰 구조는 그대로 두고 값만 0 으로 — 넓어졌다 좁아질 때 버튼이 다시 만들어지지 않게.
        let lifted = fill == nil && !merged
        content
            .background(
                shape.fill(merged ? Color.clear : (fill ?? Color.white))
                    .shadow(color: .black.opacity(lifted ? 0.06 : 0), radius: 10, y: 2)
            )
            .overlay(shape.strokeBorder(GLGColor.divider.opacity(lifted ? 1 : 0), lineWidth: 1).allowsHitTesting(false))
    }
}

private struct GldsHeaderMergedKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// 헤더 줄이 **한 캡슐로 합쳐졌는가**(10/15 「양 옆 캡슐이 가까워지면 하나의 캡슐로」). 참이면 안쪽 원 · 알약 · 묶음이
    /// 제 면을 그리지 않고 합친 캡슐의 칸이 된다([GldsHeaderMergeRow]).
    var gldsHeaderMerged: Bool {
        get { self[GldsHeaderMergedKey.self] }
        set { self[GldsHeaderMergedKey.self] = newValue }
    }
}

/// 헤더 버튼 눌림 — 내용만 살짝 작아진다(Android 전역 인디케이션과 같은 느낌). 칸에 회색 면을 따로 깔지 않는다.
/// 못 누르는 버튼은 흐리게 — 시스템 툴바가 하던 비활성 표시를 대신한다.
struct GldsHeaderButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        GldsHeaderButtonBody(configuration: configuration)
    }

    private struct GldsHeaderButtonBody: View {
        let configuration: ButtonStyleConfiguration
        @Environment(\.isEnabled) private var enabled
        var body: some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.9 : 1)
                .opacity(enabled ? 1 : 0.35)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }
}

// ── 아이콘 · 버튼 ─────────────────────────────────────────────────────────────

/// 헤더 아이콘 — GLDS 라인 아이콘 24. [loading] 이면 같은 자리 · 같은 색 스피너(18).
/// 아이콘과 스피너를 **겹쳐 두고 보이기만** 바꾼다 — 내용을 갈아 끼우면 버튼이 지웠다 다시 그려져 깜빡였다(9/30 툴바와 같은 이유).
struct GldsHeaderIcon: View {
    let name: String
    var label: String? = nil
    var size: CGFloat = GldsHeader18.icon
    var loading: Bool = false

    var body: some View {
        ZStack {
            GLGLineIcon(name, size: size, label: label).opacity(loading ? 0 : 1)
            if loading { GldsSpinner(size: 18, lineWidth: 2, inheritForeground: true) }
        }
    }
}

/// 따로 떨어진 헤더 버튼 하나 — 48 흰 원 · 아이콘 24 본문색(Android `GlgCircleIconButton`).
/// [tint] · [fill] 은 색이 깔린 히어로 위에서만 넘긴다.
struct GldsHeaderCircleButton: View {
    let icon: String
    let label: String
    var iconSize: CGFloat = GldsHeader18.icon
    var loading: Bool = false
    var tint: Color? = nil
    var fill: Color? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            GldsHeaderIcon(name: icon, label: label, size: iconSize, loading: loading)
                .frame(width: GldsHeader18.element, height: GldsHeader18.element)
                .contentShape(Circle())
        }
        .buttonStyle(GldsHeaderButtonStyle())
        .foregroundStyle(tint ?? GLGColor.textPrimary)
        .gldsHeaderSurface(Circle(), fill: fill)
    }
}

/// 묶음 캡슐 안의 아이콘 칸 — 48 × 48 · 아이콘 24 본문색(Android `GlgHeaderGroupItem`).
struct GldsHeaderCellButton: View {
    let icon: String
    let label: String
    var width: CGFloat = GldsHeader18.cell
    var iconSize: CGFloat = GldsHeader18.icon
    var loading: Bool = false
    var tint: Color? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            GldsHeaderIcon(name: icon, label: label, size: iconSize, loading: loading)
                .gldsHeaderCell(width: width)
        }
        .buttonStyle(GldsHeaderButtonStyle())
        .foregroundStyle(tint ?? GLGColor.textPrimary)
    }
}

/// 헤더 글자 버튼 — 흰 캡슐 · 48 · 좌우 16 · Pretendard 15 Bold. 툴바에 있던 글자 버튼(저장 · 모두 지우기 …)의 자리.
/// 글자색 · 역할 · 비활성은 툴바 때 그대로 받는다(비활성은 [GldsHeaderButtonStyle] 이 흐리게).
struct GldsHeaderTextButton: View {
    let title: String
    var color: Color = GLGColor.textPrimary
    var role: ButtonRole? = nil
    let action: () -> Void

    var body: some View {
        Button(role: role, action: action) {
            Text(title)
                .font(.pretendard(size: 15, weight: .bold))
                .foregroundStyle(color)
                .lineLimit(1)
                .fixedSize()
                .gldsHeaderPill()
        }
        .buttonStyle(GldsHeaderButtonStyle())
    }
}

/// 묶음 캡슐 칸 사이 세로선 — 1 × 20.
private struct GldsHeaderGroupDivider: View {
    var body: some View {
        GldsHeader18.dividerColor.frame(width: 1, height: 20)
    }
}

/**
 헤더 **묶음 캡슐** — 붙어 있는 버튼 여럿을 캡슐 하나에 담고 칸 사이에 세로선을 긋는다(Android `GlgHeaderButtonGroup`).

 세로선은 **실제로 그려진 칸 사이에만** 선다 — 칸을 `if` 로 빼면(게임 정보의 리딤코드 — 연동했을 때만) 세로선도 같이 빠진다.
 호출부가 선을 직접 넣으면 조건마다 선을 챙겨야 해서 빠뜨리기 쉽다(iOS 18 `Group(subviews:)` 로 실제 칸을 센다).

 글자 칸(홈 프로필 · 하위 페이지 제목)도 그냥 넣으면 된다 — 줄이 모자라면 글자 칸이 줄어 말줄임이 된다.
 [inset] 은 캡슐 안쪽 좌우. 끝 칸이 스스로 여백을 갖는 묶음(홈 프로필 · [‹ | 제목])은 0.
 */
struct GldsHeaderGroup<Content: View>: View {
    var fill: Color? = nil
    var inset: CGFloat = GldsHeader18.groupInset
    @ViewBuilder var content: Content
    /// 합친 캡슐 안이면 안쪽 여백도 두지 않는다 — 칸들이 합친 캡슐의 칸으로 바로 이어진다.
    @Environment(\.gldsHeaderMerged) private var merged

    var body: some View {
        GldsHeaderDividedCells { content }
        .padding(.horizontal, merged ? 0 : inset)
        .frame(height: GldsHeader18.element)
        .gldsHeaderSurface(Capsule(), fill: fill)
    }
}

/// 칸들을 가로로 잇고 **실제로 그려진 칸 사이에만** 세로선을 긋는다 — 묶음 캡슐 · 합친 캡슐이 같이 쓴다.
private struct GldsHeaderDividedCells<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        HStack(spacing: 0) {
            Group(subviews: content) { cells in
                ForEach(cells) { cell in
                    if cell.id != cells.first?.id { GldsHeaderGroupDivider() }
                    cell
                }
            }
        }
    }
}

/**
 헤더 **드롭다운 알약** — 열면 시스템 `Menu` 가 붙는 자리(공지 게임 필터, Android `GlgHeaderDropdownPill`).

 고르지 않은 상태는 흰 면 · 본문색. [selected] 면 [color] 로 면을 채우고(테두리 없음 · 그림자는 그대로 — 떠 있는 건 같다)
 글자와 ▾ 를 희게 뒤집는다 — 지금 무엇에 좁혀져 있는지가 색만으로 읽힌다. 글자 15 Bold · ▾ 12 Black(흰 90% · 본문 75%) · 좌우 16.
 합친 캡슐 안에서는 흰 면은 걷되, 고른 색은 남긴다 — 그 색이 「걸려 있다」 는 정보다.
 */
struct GldsHeaderDropdownPillLabel: View {
    let label: String
    var selected: Bool = false
    var color: Color = GLGColor.textPrimary
    @Environment(\.gldsHeaderMerged) private var merged

    var body: some View {
        HStack(spacing: 5) {
            Text(label)
                .font(.pretendard(size: 15, weight: .bold))
                .foregroundStyle(selected ? Color.white : GLGColor.textPrimary)
                .lineLimit(1)
                .fixedSize()
            Text("▾")
                .font(.pretendard(size: 12, weight: .black))
                .foregroundStyle(selected ? Color.white.opacity(0.9) : GLGColor.textPrimary.opacity(0.75))
        }
        .padding(.horizontal, 16)
        .frame(height: GldsHeader18.element)
        .contentShape(Capsule())
        .background(
            Capsule().fill(selected ? color : Color.clear)
                .shadow(color: .black.opacity(selected && !merged ? 0.06 : 0), radius: 10, y: 2)
        )
        // 고르지 않은 상태만 흰 헤더 면(합친 캡슐 안이면 그마저 걷힌다).
        .gldsHeaderSurface(Capsule(), fill: selected ? Color.clear : nil)
    }
}

/**
 헤더 줄 — 왼쪽 · 오른쪽을 **따로 두거나, 가까우면 한 캡슐로 합친다**(10/15 「양 옆 캡슐이 가까워지면 하나의 캡슐로 표시되게」).

 두 쪽을 제 크기로 놓았을 때 사이 빈 자리가 헤더 요소 하나(48)보다 좁으면 합친다 — 사용자가 짚은 「보유 캐릭터 · 원신」 은
 사이가 27 남짓이라 두 캡슐이 붙어 보였다. 합친 모양은 면 하나에 [왼쪽 칸들 | 세로선 | 오른쪽 칸들]. 안쪽 원 · 알약 · 묶음은
 [gldsHeaderMerged] 를 보고 제 면을 걷어 칸이 된다. 왼쪽 글자 칸(제목)은 남은 폭을 받아 말줄임이 된다.

 판정은 `ViewThatFits` 가 한다 — 첫 후보(따로)는 사이에 최소 48 을 둔 이상 폭이라, 그 폭이 줄에 들어갈 때만 고른다.
 */
private struct GldsHeaderMergeRow<Leading: View, Trailing: View>: View {
    var fill: Color? = nil
    /// 합친 캡슐 안쪽 왼쪽 여백 — 왼쪽 끝 칸이 스스로 여백을 가지면(뒤로 칸 · 프로필 칸) 0.
    var leadingInset: CGFloat = 0
    @ViewBuilder var leading: Leading
    @ViewBuilder var trailing: Trailing

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 0) {
                HStack(spacing: GldsHeader18.gap) { leading }
                Spacer(minLength: GldsHeader18.element)
                HStack(spacing: GldsHeader18.gap) { trailing }
            }
            merged
        }
    }

    private var merged: some View {
        HStack(spacing: 0) {
            GldsHeaderDividedCells {
                leading
                // 오른쪽 칸은 줄지 않는다 — 모자라면 왼쪽 글자 칸(제목)이 말줄임으로 준다. Group 에 건 수정자는 칸마다 걸린다.
                Group { trailing }.fixedSize().layoutPriority(1)
            }
            Spacer(minLength: 0)
        }
        .environment(\.gldsHeaderMerged, true)
        // 오른쪽 끝은 묶음 캡슐과 같은 안쪽 4 — 끝 칸 아이콘이 둥근 끝에 붙어 보이지 않게.
        .padding(.leading, leadingInset).padding(.trailing, GldsHeader18.groupInset)
        .frame(height: GldsHeader18.element)
        .gldsHeaderSurface(Capsule(), fill: fill)
    }
}

// ── 줄 ───────────────────────────────────────────────────────────────────────

/**
 하위 페이지 헤더 줄 — 왼쪽 [‹ | 제목] 캡슐 + 오른쪽 액션(Android `GlgScreenHeader`).

 - 캡슐은 글자 길이만큼만 커진다. 오른쪽과의 사이가 48 보다 좁아지면 한 캡슐로 합치고([GldsHeaderMergeRow]),
   그래도 모자라면 제목만 말줄임 — 뒤로 칸 · 오른쪽 버튼은 밀리지 않는다.
 - 제목을 숨긴 페이지(히어로가 제목을 대신하는 상세)는 뒤로 원 하나만 선다.
 - 뒤로가 없으면(스택 뿌리 — 개발자 미리보기 등) 제목만 흰 알약으로 선다(Android `GlgHeaderTitlePill`).
 */
struct GldsPageHeader18<Trailing: View>: View {
    let title: String
    var hidesTitle: Bool = false
    var onBack: (() -> Void)?
    var tint: Color? = nil
    var fill: Color? = nil
    @ViewBuilder var trailing: Trailing

    var body: some View {
        // 사이가 48 보다 좁아지면(제목이 길거나 오른쪽 버튼이 많으면) [‹ | 제목 | 세로선 | 오른쪽 칸들] 한 캡슐로 합친다(10/15).
        GldsHeaderMergeRow(fill: fill) { leading } trailing: { trailing }
        .padding(.horizontal, GldsHeader18.side)
        .padding(.top, GldsHeader18.top)
        .padding(.bottom, GldsHeader18.pageBottom)
    }

    @ViewBuilder private var leading: some View {
        let showTitle = !hidesTitle && !title.isEmpty
        if let onBack {
            if showTitle {
                // 안쪽 여백 없이 — 뒤로 칸이 캡슐 왼쪽 끝에서 시작하고, 제목 칸이 오른쪽 16 을 스스로 갖는다.
                GldsHeaderGroup(fill: fill, inset: 0) {
                    GldsHeaderCellButton(icon: "IconBack", label: "뒤로", iconSize: GldsHeader18.backIcon,
                                         tint: tint, action: onBack)
                    Text(title)
                        .font(.pretendard(size: 16, weight: .bold))
                        .foregroundStyle(tint ?? GLGColor.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .padding(.leading, 14).padding(.trailing, 16)
                        .accessibilityAddTraits(.isHeader)
                }
            } else {
                // 제목이 없으면 뒤로만 흰 원으로 선다(Android `GlgBackButton`) — 아이콘은 ‹ 규격 22.
                GldsHeaderCircleButton(icon: "IconBack", label: "뒤로", iconSize: GldsHeader18.backIcon,
                                       tint: tint, fill: fill, action: onBack)
            }
        } else if showTitle {
            Text(title)
                .font(.pretendard(size: 16, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .lineLimit(1)
                .gldsHeaderPill()
                .accessibilityAddTraits(.isHeader)
        }
    }
}

/// 탭 뿌리 헤더 줄 — 왼쪽 · 오른쪽 슬롯(Android `GlgTabHeader` · `HomeHeader`). 높이는 늘 72 — 선택 모드처럼
/// 버튼이 다 빠져도 줄이 접히지 않아 목록이 위로 튀지 않는다.
struct GldsTabHeader18<Leading: View, Trailing: View>: View {
    @ViewBuilder var leading: Leading
    @ViewBuilder var trailing: Trailing

    var body: some View {
        // 왼쪽 · 오른쪽이 가까워지면(사이 48 미만) 한 캡슐로 합친다 — 하위 페이지 헤더와 같은 규칙(10/15).
        GldsHeaderMergeRow { leading } trailing: { trailing }
        .padding(.horizontal, GldsHeader18.side)
        .padding(.top, GldsHeader18.top)
        .padding(.bottom, GldsHeader18.tabBottom)
        .frame(height: GldsHeader18.tabHeight)
    }
}

// ── 화면에 붙이기 ─────────────────────────────────────────────────────────────

/// 헤더 종류 — 탭 뿌리(72) 또는 하위 페이지(64).
enum GldsHeader18Kind {
    case tab
    case page(title: String, hidesTitle: Bool = false)
}

/**
 iOS 18 아이폰에서 시스템 내비 바를 숨기고 GLDS 헤더를 얹는다. **그 밖(iOS 26+ · iPad · Duo)에서는 아무것도 하지 않는다.**

 ## 자리
 기본은 `.safeAreaInset(edge: .top)` — 스크롤 본문이 헤더 아래에서 시작하고, 올리면 헤더 밑으로 지나간다(Android 의
 `glgDetailContentTop` 인셋 + 오버레이와 같은 그림). 히어로가 상태바까지 색을 올리는 상세([overlay] = true)는 본문을
 밀지 않고 위에 얹기만 한다 — 히어로가 `상태바 + [GldsHeader18.pageHeight]` 만큼 스스로 내려간다.

 ## 뒤로
 밀려 들어온 화면(`isPresented`)이면 ‹ 를 세우고 `dismiss()` 한다. 지출 입력처럼 스택 밖 페이지는 [onBack] 으로 넘긴다.
 시스템 바를 숨기면 iOS 18 이 가장자리 쓸어 뒤로가기도 꺼 버려 [GLGPopGestureEnabler] 로 되살린다.

 ## 상태바 스크림
 본문을 올린 뒤에만 상태바 자리에 흰 스크림을 깐다(맨 위에선 숨김 — Android `GlgDetailHeaderOverlay` 와 같다).
 [scrim] 을 넘기면 그 값을 쓴다 — 히어로 상세는 히어로를 지난 뒤에만 켠다(히어로 색 위에 흰 띠가 얹히지 않게).
 */
struct GldsHeader18Modifier<Leading: View, Trailing: View>: ViewModifier {
    let kind: GldsHeader18Kind
    var onBack: (() -> Void)? = nil
    var tint: Color? = nil
    var fill: Color? = nil
    var scrim: Bool? = nil
    var overlay: Bool = false
    let leading: Leading
    let trailing: Trailing

    @Environment(\.dismiss) private var dismiss
    @Environment(\.isPresented) private var isPresented
    /// 본문을 올렸는가 — 상태바 스크림을 켠다.
    @State private var scrolled = false

    func body(content: Content) -> some View {
        if GldsHeader18.isActive {
            let base = content
                .toolbar(.hidden, for: .navigationBar)
                // 이 수정자는 **하위의 모든 스크롤뷰**에 걸린다(지출 목록의 '맨 위로'와 같은 사정) — 가로 줄은 세로 위치가
                // 늘 0 이라 값이 바뀌지 않아 끼어들지 않는다.
                .onScrollGeometryChange(for: Bool.self) { geo in
                    geo.contentOffset.y + geo.contentInsets.top > 0.5
                } action: { _, v in
                    if scrolled != v { scrolled = v }
                }
                .background(GLGPopGestureEnabler().frame(width: 0, height: 0))
            if overlay {
                base.overlay(alignment: .top) { header }
            } else {
                base.safeAreaInset(edge: .top, spacing: 0) { header }
            }
        } else {
            content
        }
    }

    private var header: some View {
        Group {
            switch kind {
            case .tab:
                GldsTabHeader18 { leading } trailing: { trailing }
            case .page(let title, let hidesTitle):
                GldsPageHeader18(title: title, hidesTitle: hidesTitle, onBack: backAction,
                                 tint: tint, fill: fill) { trailing }
            }
        }
        // 헤더 줄의 빈 자리(제목 캡슐 옆 · 버튼 사이)를 누르면 **뒤 본문이 눌리지 않게** 삼킨다 — 헤더에 가려 보이지도 않는
        // 칩 · 줄이 눌렸다(Android 2026-09-13 제보와 같은 구조). 버튼은 이 위에 있어 그대로 눌린다.
        .background(Color.white.opacity(0.001))
        .background(alignment: .top) { statusScrim }
    }

    private var backAction: (() -> Void)? {
        if let onBack { return onBack }
        return isPresented ? { dismiss() } : nil
    }

    /// 상태바 자리 스크림 — 헤더 줄 위쪽으로 상태바만큼 올라가 흰 88% → 투명으로 흐려진다. 헤더 버튼 줄은 덮지 않는다.
    private var statusScrim: some View {
        let status = GldsHeader18.statusBarHeight
        return LinearGradient(stops: [
            .init(color: .white, location: 0),
            .init(color: .white, location: 0.35),
            .init(color: .white.opacity(0), location: 1),
        ], startPoint: .top, endPoint: .bottom)
        .frame(height: status + GldsHeader18.scrimFade)
        .offset(y: -status)
        .opacity((scrim ?? scrolled) ? 0.88 : 0)
        .animation(.easeInOut(duration: 0.18), value: scrim ?? scrolled)
        .allowsHitTesting(false)
    }
}

extension View {
    /// 탭 뿌리 헤더 — iOS 18 아이폰에서만. [GldsHeader18Modifier] 참고.
    func gldsTabHeader18<L: View, T: View>(@ViewBuilder leading: () -> L, @ViewBuilder trailing: () -> T) -> some View {
        modifier(GldsHeader18Modifier(kind: .tab, leading: leading(), trailing: trailing()))
    }

    /// 하위 페이지 헤더 — iOS 18 아이폰에서만. [GldsHeader18Modifier] 참고.
    /// 보통은 [glgPageTitle] · [glgHiddenTitle] 이 대신 붙인다. 제목이 바뀌거나(공지 상세) 히어로 색을 받는 화면만 직접 쓴다.
    func gldsPageHeader18<T: View>(
        _ title: String,
        hidesTitle: Bool = false,
        onBack: (() -> Void)? = nil,
        tint: Color? = nil,
        fill: Color? = nil,
        scrim: Bool? = nil,
        overlay: Bool = false,
        @ViewBuilder trailing: () -> T
    ) -> some View {
        modifier(GldsHeader18Modifier(kind: .page(title: title, hidesTitle: hidesTitle), onBack: onBack,
                                      tint: tint, fill: fill, scrim: scrim, overlay: overlay,
                                      leading: EmptyView(), trailing: trailing()))
    }
}

// ── 쓸어서 뒤로 ───────────────────────────────────────────────────────────────

/**
 시스템 내비 바를 숨긴 스택에서 **가장자리 쓸어 뒤로가기**를 되살린다(iOS 18 아이폰만 — 헤더 수정자 안에서만 붙는다).

 iOS 18 은 바를 숨기면 `interactivePopGestureRecognizer` 가 시작하지 못한다. 대리자를 바꿔 **밀려 들어온 화면이 있을 때만**
 시작하게 한다 — 뿌리에서 시작하면 스택이 멈춘다. 전환 중(밀려 들어오는 중)에도 막는다 — 그때 쓸면 화면이 굳는다.

 지출 입력 페이지는 스택 밖에서 자기 `NavigationStack` 뿌리로 서서 화면이 하나뿐이라 여기서 시작하지 않는다 —
 그 페이지의 왼쪽 끝 띠(`backSwipeStrip`)가 그대로 맡는다. iOS 26 은 이 부품이 붙지 않는다.
 */
struct GLGPopGestureEnabler: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> GLGPopGestureProbe { GLGPopGestureProbe() }
    func updateUIViewController(_ vc: GLGPopGestureProbe, context: Context) {}
}

final class GLGPopGestureProbe: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.isUserInteractionEnabled = false
    }

    override func didMove(toParent parent: UIViewController?) {
        super.didMove(toParent: parent)
        install()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        install()
    }

    private func install() {
        guard let nav = navigationController, let pop = nav.interactivePopGestureRecognizer else { return }
        if pop.delegate is GLGPopGestureDelegate { return }
        let delegate = GLGPopGestureDelegate(nav: nav)
        // 대리자는 약하게 잡히므로 스택(내비 컨트롤러)이 들고 있게 한다.
        GLGPopGestureDelegate.retained[ObjectIdentifier(nav)] = delegate
        pop.delegate = delegate
        pop.isEnabled = true
    }
}

final class GLGPopGestureDelegate: NSObject, UIGestureRecognizerDelegate {
    /// 스택마다 하나 — 탭 넷 · 몇 안 되는 스택이라 따로 비우지 않는다.
    static var retained: [ObjectIdentifier: GLGPopGestureDelegate] = [:]
    private weak var nav: UINavigationController?

    init(nav: UINavigationController) { self.nav = nav }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let nav else { return false }
        return nav.viewControllers.count > 1 && nav.transitionCoordinator == nil
    }
}
