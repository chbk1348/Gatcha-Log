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
