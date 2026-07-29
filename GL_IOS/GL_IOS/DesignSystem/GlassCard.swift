import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 카드 — Compose 의 GlassCard 대응. 둥근 글래스 패널 + 콘텐츠 패딩.
// ════════════════════════════════════════════════════════════════════════════

struct GLGCard<Content: View>: View {
    var cornerRadius: CGFloat = 22
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glgGlass(in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

/// 섹션 제목 + 카드 (마이페이지/설정 등에서 반복되는 패턴).
struct GLGSection<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                // 시맨틱 스타일(.subheadline)은 시스템 서체 + Dynamic Type 스케일이라 쓰지 않는다.
                .font(.pretendard(size: 15, weight: .semibold))
                .foregroundStyle(GLGColor.textSecondary)
                .padding(.leading, 4)
            GLGCard { content }
        }
    }
}
