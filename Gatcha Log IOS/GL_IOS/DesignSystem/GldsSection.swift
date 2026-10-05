import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// GLDS 2.0 — 카드 없는 레이아웃의 섹션 · 띠 · 헤어라인 (10/6 통합)
// Android `ui/components/GldsSection.kt` 와 값이 같다.
// 섹션: 좌우 20 · 위 22 · 아래 20 / 섹션 사이 10 띠 / 줄 사이 1 헤어라인.
// bottom 은 20 − 마지막 요소의 자체 아래 여백 — 보이는 끝 → 띠 간격을 20 으로 맞춘다.
// ════════════════════════════════════════════════════════════════════════════

let gldsBandColor = Color(hex: 0xFFF2F4F6)
let gldsHairColor = Color(hex: 0xFFEEF0F2)

/// 섹션 사이 10 띠.
struct GldsBand: View {
    var body: some View { gldsBandColor.frame(height: 10).frame(maxWidth: .infinity) }
}

/// 줄 사이 1 헤어라인 — `inset` 만큼 좌우를 들인다(줄 글자 시작선과 맞출 때 20).
struct GldsHairline: View {
    var inset: CGFloat = 0
    var body: some View { gldsHairColor.frame(height: 1).frame(maxWidth: .infinity).padding(.horizontal, inset) }
}

/// 화면 폭 섹션 — 제목 17 Bold + 오른쪽 `trailing`(보조 문구 · 액션). 제목이 없으면 머리 없이 내용만.
/// `titleGap` 은 제목 → 내용 간격(기본 14).
struct GldsSection<Content: View, Trailing: View>: View {
    let title: String?
    let top: CGFloat
    let bottom: CGFloat
    let horizontal: CGFloat
    let titleGap: CGFloat
    let content: Content
    let trailing: Trailing

    init(_ title: String? = nil, top: CGFloat = 22, bottom: CGFloat = 20, horizontal: CGFloat = 20, titleGap: CGFloat = 14,
         @ViewBuilder content: () -> Content, @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.top = top
        self.bottom = bottom
        self.horizontal = horizontal
        self.titleGap = titleGap
        self.content = content()
        self.trailing = trailing()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                HStack {
                    Text(title).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 8)
                    trailing
                }
                .padding(.bottom, titleGap)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, horizontal).padding(.top, top).padding(.bottom, bottom)
    }
}

extension GldsSection where Trailing == EmptyView {
    init(_ title: String? = nil, top: CGFloat = 22, bottom: CGFloat = 20, horizontal: CGFloat = 20, titleGap: CGFloat = 14,
         @ViewBuilder content: () -> Content) {
        self.init(title, top: top, bottom: bottom, horizontal: horizontal, titleGap: titleGap, content: content, trailing: { EmptyView() })
    }
}
