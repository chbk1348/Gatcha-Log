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
    let cards: [GLGMasonryCard]
    /// 넓은 화면 열 수(iPad 세로·가로 모두 2열이 적당)
    var columns: Int = 2
    /// 열 사이 가로 간격 + 열 안 카드 세로 간격(레귤러)
    var spacing: CGFloat = 12
    /// 컴팩트(iPhone) 세로 간격 — 기존 화면 spacing 을 그대로 넘겨 iPhone 레이아웃 보존
    var stackSpacing: CGFloat = 12

    var body: some View {
        if hSize == .regular {
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
        content.navigationTitle(hSizeClass == .compact ? title : "")
    }
}

extension View {
    /// 하위(push) 페이지의 제목. iPad 에서는 자동으로 감춘다 — [GLGPageTitle] 참고.
    func glgPageTitle(_ title: String) -> some View { modifier(GLGPageTitle(title: title)) }
}

private struct GLGReadableWidth: ViewModifier {
    @Environment(\.horizontalSizeClass) private var hSize
    var maxWidth: CGFloat
    func body(content: Content) -> some View {
        if hSize == .regular {
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

    var body: some View {
        GeometryReader { geo in
            let split = glgIsSplit(width: geo.size.width, sizeClass: hSize, minSplitWidth: minSplitWidth)
            Group {
                if split {
                    HStack(spacing: 0) {
                        // 창이 애매하게 좁으면 목록도 같이 줄여 상세가 지나치게 눌리지 않게 한다.
                        list().frame(width: min(listWidth, geo.size.width * 0.42))
                        Divider()
                        detail().frame(maxWidth: .infinity)
                    }
                } else {
                    list()
                }
            }
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
    sizeClass == .regular && width >= minSplitWidth
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
