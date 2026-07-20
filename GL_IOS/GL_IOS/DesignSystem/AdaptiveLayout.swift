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
struct GLGMasonryCard: Identifiable {
    let id: AnyHashable
    /// 높이 추정치(상대값). 지출=행 수, 홈=카드별 대략치. 열 균형 배분에 쓴다.
    let weight: Double
    let view: AnyView

    init<V: View>(id: AnyHashable, weight: Double = 1, @ViewBuilder view: () -> V) {
        self.id = id
        self.weight = weight
        self.view = AnyView(view())
    }
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
