import SwiftUI
import UIKit

// ════════════════════════════════════════════════════════════════════════════
// 선택 가능한 본문 텍스트 — 길게 눌러 **원하는 구간만** 드래그 선택 → 복사.
//
// SwiftUI 의 .textSelection(.enabled) 는 Text 블록 전체가 한 덩어리로 잡혀 부분 선택이 뻑뻑하다.
// 공지 본문은 코드·일정·수치를 한 줄만 뽑아 쓰는 일이 잦아, 네이티브 UITextView 로 내린다
// (드래그 핸들·확대 루페·복사 메뉴가 전부 시스템 기본 동작).
//
// (Compose 쪽은 SelectionContainer 가 같은 동작을 제공한다 — NewsDetailContent.kt)
// ════════════════════════════════════════════════════════════════════════════

struct GLGSelectableText: UIViewRepresentable {
    let text: String
    var size: CGFloat = 14
    var lineSpacing: CGFloat = 5

    func makeUIView(context: Context) -> UITextView {
        let tv = UITextView()
        tv.isEditable = false
        tv.isSelectable = true       // 부분 선택·복사
        tv.isScrollEnabled = false   // 스크롤은 상위 ScrollView 가 맡는다(여기서 켜면 스크롤이 서로 먹는다)
        tv.backgroundColor = .clear
        tv.textContainerInset = .zero
        tv.textContainer.lineFragmentPadding = 0
        tv.dataDetectorTypes = [.link] // 공지 본문의 URL 은 탭으로 열리게
        tv.setContentCompressionResistancePriority(.required, for: .vertical)
        tv.setContentHuggingPriority(.required, for: .vertical)
        return tv
    }

    func updateUIView(_ tv: UITextView, context: Context) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = lineSpacing
        tv.attributedText = NSAttributedString(
            string: text,
            attributes: [
                .font: UIFont(name: "Pretendard-Regular", size: size) ?? .systemFont(ofSize: size),
                .foregroundColor: UIColor(GLGColor.textPrimary),
                .paragraphStyle: paragraph,
            ]
        )
    }

    /// 폭은 부모가 제안한 값을 그대로 쓰고, 높이는 그 폭에서 실제로 필요한 만큼 계산한다.
    /// (isScrollEnabled=false 라 intrinsic 높이가 나오지만, 부모 폭을 반영하려면 여기서 맞춰줘야 한다)
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        let width = proposal.width ?? UIScreen.main.bounds.width
        let fitted = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: fitted.height)
    }
}
