import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// iPad(넓은 화면) 적응 레이아웃 헬퍼
//
// iPhone(컴팩트 가로폭)에서는 아무것도 바꾸지 않는다 — 기존 세로 1열 모바일 UI 그대로.
// iPad(레귤러 가로폭, 세로·가로 모두)에서만 다단 그리드/최대폭 제한을 적용해
// 콘텐츠가 넓은 화면 끝까지 늘어나 보이지 않게 한다.
//
// 판정 기준은 horizontalSizeClass == .regular. NavigationSplitView 의 detail 컬럼은
// iPad 세로·가로 모두 .regular 이고, iPhone(세로 잠금)은 항상 .compact 다.
//
// 높이가 제각각인 카드 목록(홈 대시보드·날짜별 지출)은 LazyVGrid(행 정렬)로 깔면 짧은
// 카드 아래에 빈 영역이 생긴다 → 메이슨리(GLGColumnMasonry)로 짧은 열을 우선 채운다.
// 균일한 폼·읽기 콘텐츠는 최대폭 제한(glgReadableWidth)만으로 충분하다.
// ════════════════════════════════════════════════════════════════════════════

// ── 창 폭을 기준으로 삼는다 ─────────────────────────────────────────────────
//
// 판정을 `horizontalSizeClass` 로만 하면 **펼친 iPhone Duo 를 놓친다.** 951×669pt 짜리
// 화면인데도 사이즈 클래스는 compact 로 오고(폰 계열이라 그렇다), 그러면 iPad 용 다단
// 레이아웃이 하나도 걸리지 않아 가운데 한 줄만 남고 옆이 통째로 빈다(2026-09-21 실측).
//
// 그래서 루트가 **창 실제 폭**을 재서 환경으로 내려보내고, 아래 도구들이 그 값을 먼저 본다.
// 값이 없을 때만(0) 예전처럼 사이즈 클래스로 판단한다.

private struct GLGCanvasWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat = 0
}

extension EnvironmentValues {
    /// 앱 창의 실제 가로(pt). 루트(ContentView)가 재서 넣는다. 0 = 아직 재기 전.
    var glgCanvasWidth: CGFloat {
        get { self[GLGCanvasWidthKey.self] }
        set { self[GLGCanvasWidthKey.self] = newValue }
    }
}

// ── 폼팩터 — iPhone · iPhone Duo · iPad ────────────────────────────────────
//
// **기기가 무엇인가**와 **지금 창이 넓은가**는 다른 질문이다. 레이아웃(다단·최대폭)은 창 폭으로
// 가르고([glgIsWideCanvas]), 기기마다 자리가 정해진 것 — '추가' 버튼 위치·회전 허용·경첩 — 은
// 이 폼팩터로 가른다. 예전엔 `userInterfaceIdiom` 과 창 폭을 곳곳에서 섞어 쓰다
// Duo 가 어디선 iPhone, 어디선 iPad 로 취급됐다(2026-09-21 지적).

/// 폼팩터를 가르는 화면 짧은 변(pt) — iPhone 최대폭(440)과 iPhone Duo 접은 화면(466) 사이.
let GLGWideScreenMinSide: CGFloat = 460

enum GLGFormFactor {
    /// 보통 iPhone — 세로 고정, 하단 탭바.
    case phone
    /// iPhone Duo(접는 폰) — 접으면 iPhone 처럼, 펼치면 옆에 세로로 선 바 + 넓은 창.
    case duo
    /// iPad — 위쪽 탭바, 창 크기는 자유(분할·Stage Manager).
    case pad

    /// 이 화면을 가진 기기의 폼팩터. Duo 는 접든 펴든 **화면 짧은 변**이 460pt 를 넘는다.
    @MainActor
    static func of(screen: UIScreen?) -> GLGFormFactor {
        if UIDevice.current.userInterfaceIdiom == .pad { return .pad }
        guard let b = screen?.bounds else { return .phone }
        return min(b.width, b.height) >= GLGWideScreenMinSide ? .duo : .phone
    }

    /// 지금 실행 중인 기기의 폼팩터.
    @MainActor
    static var current: GLGFormFactor {
        of(screen: UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.screen }.first)
    }
}

/// 다단 레이아웃을 켜는 창 폭 — iPhone 최대(440)·듀오 접은 화면(466)보다 위, 듀오 펼친 화면(951)·iPad 아래.
let GLGWideCanvasMinWidth: CGFloat = 700

/// 이 창을 넓게 볼 것인가 — 잰 폭이 있으면 그 값으로, 없으면 사이즈 클래스로.
@MainActor
func glgIsWideCanvas(width: CGFloat, sizeClass: UserInterfaceSizeClass?) -> Bool {
    width > 0 ? width >= GLGWideCanvasMinWidth : sizeClass == .regular
}

extension View {
    /// 창 폭을 재서 환경으로 내려보낸다 — 앱 루트에 한 번만 붙인다.
    func glgMeasureCanvas(_ width: Binding<CGFloat>) -> some View {
        // **애니메이션을 태우지 않는다.** 접었다 펴는 동안 이 값이 천천히 따라가면, 화면은 이미
        // 좁아졌는데 레이아웃 판정은 아직 "넓다" 라서 내용이 한 번 좁게 몰렸다가 다시 펴진다
        // (2026-09-21 — 모든 지면에서 같은 증상). 접힘 자체의 결은 OS 가 이미 만든다.
        onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width.wrappedValue = $0 }
            .environment(\.glgCanvasWidth, width.wrappedValue)
    }
}

/// 메이슨리(벽돌쌓기) 카드 하나 — id·가중치(높이 추정)·뷰.
///
/// LazyVGrid 는 같은 행의 셀들을 가장 큰 카드 높이에 맞춰 정렬해, 높이가 제각각인 카드
/// 목록(예: 날짜별 지출)에서는 짧은 카드 아래에 빈 공간이 생긴다. 메이슨리는 각 열을
/// 독립 세로 스택으로 두고 '가장 짧은 열'에 다음 카드를 넣어 그 빈틈을 없앤다.
///
/// 뷰는 **클로저로 보관**한다. 즉시 `AnyView(view())` 로 만들면 카드 배열을 map 하는 시점에 전 카드의
/// 뷰 트리가 한꺼번에 생성돼, 아래 LazyVStack 의 laziness 가 무의미해진다(지출 300건이면 300장을 미리 만든다).
struct GLGMasonryCard: Identifiable {
    let id: AnyHashable
    /// 높이 추정치(상대값). 지출=행 수, 홈=카드별 대략치. 열 균형 배분에 쓴다.
    let weight: Double
    private let make: () -> AnyView

    init<V: View>(id: AnyHashable, weight: Double = 1, @ViewBuilder view: @escaping () -> V) {
        self.id = id
        self.weight = weight
        self.make = { AnyView(view()) }
    }

    /// 실제 뷰 — 렌더 시점에만 만들어진다.
    var view: AnyView { make() }
}

/// 넓은 화면에서 카드들을 메이슨리(2열, 짧은 열 우선 채움)로, 컴팩트에서는 기존 세로 1열로.
///
/// 카드는 입력 순서를 열 안에서 유지하되(각 카드는 항상 앞 카드들 뒤에 append), 매번 누적
/// 높이가 가장 작은 열에 넣어 좌우 높이를 맞춘다 → 중간에 비는 영역이 생기지 않는다.
/// iPhone(컴팩트)에서는 laziness 를 위해 LazyVStack 그대로.
struct GLGColumnMasonry: View {
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.glgCanvasWidth) private var canvasWidth
    let cards: [GLGMasonryCard]
    /// 넓은 화면 열 수(iPad 세로·가로 모두 2열이 적당)
    var columns: Int = 2
    /// 열 사이 가로 간격 + 열 안 카드 세로 간격(레귤러)
    var spacing: CGFloat = 12
    /// 컴팩트(iPhone) 세로 간격 — 기존 화면 spacing 을 그대로 넘겨 iPhone 레이아웃 보존
    var stackSpacing: CGFloat = 12

    var body: some View {
        if glgIsWideCanvas(width: canvasWidth, sizeClass: hSize) {
            let cols = distribute(cards, into: max(1, columns))
            HStack(alignment: .top, spacing: spacing) {
                ForEach(0..<cols.count, id: \.self) { ci in
                    VStack(spacing: spacing) {
                        ForEach(cols[ci]) { $0.view }
                    }
                    .frame(maxWidth: .infinity, alignment: .top)
                }
            }
        } else {
            LazyVStack(alignment: .leading, spacing: stackSpacing) {
                ForEach(cards) { $0.view }
            }
        }
    }

    /// 입력 순서대로, 매번 누적 높이가 가장 작은 열에 카드를 넣는다(동률이면 왼쪽부터).
    private func distribute(_ cards: [GLGMasonryCard], into n: Int) -> [[GLGMasonryCard]] {
        var cols = Array(repeating: [GLGMasonryCard](), count: n)
        var heights = Array(repeating: 0.0, count: n)
        for c in cards {
            var idx = 0
            for i in 1..<n where heights[i] < heights[idx] { idx = i }
            cols[idx].append(c)
            heights[idx] += c.weight
        }
        return cols
    }
}

/// 넓은 화면에서 콘텐츠 최대폭을 제한하고 중앙 정렬 — 폼·설정·읽기 콘텐츠가 끝까지 늘어나지 않게.
/// 컴팩트(iPhone)에서는 제한 없이 그대로.
/// 페이지 제목 — **넓은 창(iPad)에서는 감춘다.**
///
/// iPad 는 탭바가 화면 **상단**이라, 그 바로 밑에 제목 줄이 한 겹 더 붙는다.
/// 탭 이름("게임 정보")과 페이지 제목("게임 일정")이 위아래로 나란히 놓여 두 번 읽히고,
/// 세로 공간도 한 줄을 통째로 먹는다. 좁은 창(iPhone)은 탭바가 하단이라 겹치지 않으므로 그대로 둔다.
///
/// **네비게이션 바를 통째로 숨기지 않는다** — 뒤로가기가 그 바에 있어서 같이 사라진다.
/// 제목 문자열만 비우면 바는 남고 줄만 걷힌다.
///
/// `navigationBarTitleDisplayMode` 는 건드리지 않는다. 화면마다 large/inline 이 다르게 잡혀 있어
/// 여기서 통일하면 iPhone 쪽 모양이 같이 바뀐다.
private struct GLGPageTitle: ViewModifier {
    let title: String
    @Environment(\.horizontalSizeClass) private var hSizeClass

    func body(content: Content) -> some View {
        // **제목은 어느 폭에서든 남긴다.** 넓은 화면에서 비우던 자리다 — 위쪽 탭 이름과 두 줄로
        // 겹쳐 읽힌다는 이유였는데, 이 수정자를 쓰는 곳은 전부 **push 된 하위 페이지**라
        // (굿즈 목록 · 보유 캐릭터 · 게임 일정 …) 탭 이름과 같은 말이 아니다. 제목이 없으면
        // 넓은 화면에서 "지금 어느 페이지인가"를 화면이 답하지 않고, 빈 바만 남아 여백으로
        // 보인다(2026-09-21 점검). 탭 **뿌리** 화면은 탭 이름이 이미 답하므로 거기서만 지운다.
        content.navigationTitle(title)
    }
}

extension View {
    /// 하위(push) 페이지의 제목. iPad 에서는 자동으로 감춘다 — [GLGPageTitle] 참고.
    func glgPageTitle(_ title: String) -> some View { modifier(GLGPageTitle(title: title)) }
}

private struct GLGReadableWidth: ViewModifier {
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.glgCanvasWidth) private var canvasWidth
    var maxWidth: CGFloat
    func body(content: Content) -> some View {
        if glgIsWideCanvas(width: canvasWidth, sizeClass: hSize) {
            content
                .frame(maxWidth: maxWidth)
                .frame(maxWidth: .infinity)
        } else {
            content
        }
    }
}

extension View {
    /// 넓은 화면에서 최대폭 제한 + 중앙 정렬(폼·설정·읽기 콘텐츠용). iPhone 은 영향 없음.
    func glgReadableWidth(_ maxWidth: CGFloat = 640) -> some View {
        modifier(GLGReadableWidth(maxWidth: maxWidth))
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 좌 목록 / 우 상세 (iPad)
// ════════════════════════════════════════════════════════════════════════════

/// 넓은 화면(iPad)에서 **좌 목록 / 우 상세**로 가르고, 컴팩트(iPhone)에서는 목록만 그대로 둔다.
///
/// `NavigationSplitView` 를 쓰지 않는다 — 이 앱의 화면들은 이미 탭마다 `NavigationStack` 안에 있어
/// 중첩하면 툴바·타이틀이 어느 쪽 것인지 흐려진다. 대신 호출부가 **우측 상세만 자기
/// `NavigationStack` 으로 감싸서**, 상세의 수정·삭제 같은 툴바가 오른쪽 바에 붙게 한다
/// (왼쪽 바에는 목록 조작만 남는다).
///
/// 행을 눌렀을 때 push 할지 우측을 갈아 끼울지는 호출부가 정하는데, 그 판정을 호출부가 따로
/// 하면 컨테이너와 어긋난다. 그래서 컨테이너가 [isSplit] 바인딩으로 **자기 판정을 돌려준다.**
///
/// (환경값으로 내려보내는 방법은 쓸 수 없다 — `.environment` 는 자식 서브트리에만 닿아서,
/// 컨테이너를 **소유한** 뷰가 자기 스코프에서 읽으면 언제나 기본값이다. 실제로 그렇게 만들었다가
/// iPad 에서 행이 계속 push 되는 버그가 났다.)
///
/// ⚠️ **iPadOS 26 자유 창 대비.** 창 크기를 사용자가 마음대로 줄일 수 있게 되면서
/// `horizontalSizeClass == .regular` 는 더 이상 "넓다"는 뜻이 아니다 — 창을 절반으로 줄여도
/// `.regular` 인 채로 폭만 600pt 대로 떨어질 수 있다. 그 상태에서 목록 392 + 상세를 가르면
/// 상세가 200pt 대가 되어 둘 다 못 읽는다. 그래서 **실제 폭**을 재서 가른다.
struct GLGSplitDetail<L: View, D: View>: View {
    @Environment(\.horizontalSizeClass) private var hSize

    /// 좌측 목록 폭. 목록이 한 줄 행이면 392 로 충분하고, 2열 그리드면 더 넓게 준다.
    var listWidth: CGFloat = 392
    /// 이 폭 미만이면 가르지 않고 목록만 둔다(= iPhone 과 같은 동작).
    /// 392(목록) + 최소 상세 폭이 나와야 가르는 의미가 있다.
    var minSplitWidth: CGFloat = 700
    /// 지금 갈렸는지를 호출부에 돌려준다. 행 탭 동작·타이틀 노출을 여기에 맞춘다.
    @Binding var isSplit: Bool
    @ViewBuilder var list: () -> L
    @ViewBuilder var detail: () -> D

    /// 펼친 iPhone Duo 의 경첩 — 있으면 **가르는 자리를 거기에 맞춘다**([glgHinge]).
    @State private var hinge: GLGHinge? = nil

    var body: some View {
        GeometryReader { geo in
            let split = glgIsSplit(width: geo.size.width, sizeClass: hSize, minSplitWidth: minSplitWidth)
            // 목록 폭은 세 갈래다.
            //  ① 시스템이 접힘선을 알려 주면 **그 앞까지**(실기기 iPhone Duo).
            //  ② 접히는 기기인데 보고가 없으면(시뮬레이터) 펼친 화면의 **절반** — 크리스가 정중앙이다.
            //  ③ 그 밖(iPad · 좁은 창)은 고정 폭.
            //
            // ②를 상태로 들고 있지 않고 **여기서 폭으로 바로 계산**한다. 상태로 두면 접는 순간
            // 갈림 여부와 갱신 시점이 어긋나 목록이 좁아졌다 다시 펴진다(2026-09-21 지적).
            let isFoldable = GLGFormFactor.current == .duo
            let listW: CGFloat = {
                if let hinge { return max(hinge.midX - hinge.width / 2, 240) }
                if isFoldable && split { return geo.size.width / 2 }
                return min(listWidth, geo.size.width * 0.42)
            }()
            Group {
                if split {
                    HStack(spacing: 0) {
                        list().frame(width: listW)
                        if let hinge {
                            // 접힘선 자리는 **비워 둔다.** 물리적으로 이미 갈라진 자리라 선을 더
                            // 그으면 두 겹이 된다.
                            Color.clear.frame(width: hinge.width)
                        } else {
                            // 목록과 상세 사이 선 — 앱의 다른 구분선과 **같은 색·같은 두께**다.
                            // 시스템 `Divider()` 는 회색이 더 진해 여기만 선이 굵어 보였다(2026-09-21 지적).
                            Rectangle().fill(GLGColor.divider).frame(width: 1)
                        }
                        detail().frame(maxWidth: .infinity)
                    }
                } else {
                    list()
                }
            }
            .glgHinge($hinge)
            // 레이아웃 도중에 상태를 쓰면 "Modifying state during view update" 가 된다 → 반영은 밖에서.
            .onAppear { if isSplit != split { isSplit = split } }
            .onChange(of: split) { _, now in isSplit = now }
        }
    }
}

/// 좌/우로 가를 만한 폭인가. 컨테이너와 호출부(행 탭 동작)가 **같은 판정**을 쓰게 하려고 밖에 뺐다.
///
/// 창 크기가 바뀌면 이 값도 바뀐다 — 갈라진 상태에서 창을 좁히면 목록 단독으로 접히고,
/// 그때는 행을 누르면 다시 push 로 동작한다(선택 자체는 남아 있어 창을 넓히면 상세가 돌아온다).
@MainActor
func glgIsSplit(width: CGFloat, sizeClass: UserInterfaceSizeClass?, minSplitWidth: CGFloat = 700) -> Bool {
    // 사이즈 클래스는 **조건이 아니다.** 펼친 iPhone Duo 는 951pt 를 주면서도 compact 로 오므로
    // 그걸 요구하면 접히는 기기에서 영영 갈리지 않는다. 폭만 보면 iPad 좁은 창도 함께 걸러진다.
    _ = sizeClass
    return width >= minSplitWidth
}

/// 우측에 아직 고른 게 없을 때의 빈 자리.
///
/// 첫 항목을 자동으로 열지 않는다 — 사용자가 고르지 않은 것을 펼쳐 두면
/// "이건 왜 열려 있지"가 된다.
struct GLGSplitPlaceholder: View {
    let systemImage: String
    let text: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(GLGColor.textSecondary.opacity(0.45))
            Text(text)
                .font(.pretendard(size: 13))
                .foregroundStyle(GLGColor.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(GLGBackground { Color.clear })
    }
}

// ════════════════════════════════════════════════════════════════════════════
// iPhone Duo — 경첩(접힘선) 대응
//
// 펼친 듀오는 화면 한가운데를 경첩이 가른다. 시스템은 그 자리를 **예약 영역**
// (`ReservedRegion(kind: .division)`, iOS 27.1)으로 알려 준다 — 화면 좌표의 사각형과
// 그 둘레 여백이다. 여기에 카드나 글자를 걸치면 접힌 선 위에서 잘려 읽힌다.
//
// 그래서 두 열로 가르는 화면은 **열 사이 빈틈을 경첩 위에 맞춘다.** 내용은 양쪽 판에
// 온전히 한 덩이씩 놓이고, 갈라지는 자리는 원래 갈라져 있는 자리가 된다.
// ════════════════════════════════════════════════════════════════════════════

/// 지금 화면을 가르는 경첩 — 펼친 iPhone Duo 에서만 값이 있다(그 밖에는 nil).
struct GLGHinge: Equatable {
    /// 경첩 한가운데의 x — 재는 뷰의 좌표계 기준.
    var midX: CGFloat
    /// 경첩 폭 + 좌우 여백. 이만큼은 비워 둔다.
    var width: CGFloat
}

extension View {
    /**
     경첩을 재서 [hinge] 에 담는다 — 두 열로 가르는 페이지의 **루트**에 붙인다.

     `onGeometryChange` 로 재는 이유는 `GeometryReader` 가 자리를 통째로 먹기 때문이다.
     변환 클로저가 받는 프록시에서 예약 영역을 그대로 물어볼 수 있다.

     iOS 27.1 미만(그리고 듀오가 아닌 기기)에서는 늘 nil 이라 호출부는 한 갈래만 더 쓰면 된다.
     */
    func glgHinge(_ hinge: Binding<GLGHinge?>) -> some View {
        onGeometryChange(for: GLGHinge?.self) { proxy in
            guard #available(iOS 27.1, *) else { return nil }
            // 세로로 선 경첩만 본다 — 좌우로 가르는 화면에서 열 사이를 맞추는 것이 목적이다.
            if let region = proxy.reservedRegions(kind: .division)
                .first(where: { $0.isActive && $0.frame.height >= $0.frame.width }) {
                let f = region.frame
                return GLGHinge(midX: f.midX,
                                width: f.width + region.margins.leading + region.margins.trailing)
            }
            // 보고가 없으면 **여기서 지어내지 않는다.** 폭에서 유추한 값(절반)을 상태로 들고 있으면,
            // 접는 순간 갈림 여부와 이 값이 **서로 다른 프레임에** 갱신돼 목록이 한 번 좁아졌다
            // 다시 펴지는 것이 보인다(2026-09-21 지적). 유추가 필요한 쪽은 폭을 이미 알고 있으므로
            // 거기서 그 자리에 계산한다(→ [GLGSplitDetail]).
            return nil
        } action: { hinge.wrappedValue = $0 }
    }
}

/**
 경첩 왼쪽까지를 한 열로 잡는다 — 펼친 iPhone Duo 에서 두 열을 접힘선에 맞출 때 쓴다.

 [GLGHinge.midX] 는 **페이지 루트 좌표**라, 열이 그 안에서 좌우 여백([contentInset])만큼
 들어와 있으면 그만큼 빼야 왼쪽 판의 끝과 맞는다. 경첩이 없으면 반반(`maxWidth: .infinity`)이다.
 */
struct GLGHingeColumnWidth: ViewModifier {
    let hinge: GLGHinge?
    /// 페이지가 콘텐츠에 준 좌측 여백.
    var contentInset: CGFloat = 0

    func body(content: Content) -> some View {
        if let hinge {
            content.frame(width: max(hinge.midX - hinge.width / 2 - contentInset, 240),
                          alignment: .topLeading)
        } else {
            content.frame(maxWidth: .infinity, alignment: .topLeading)
        }
    }
}
