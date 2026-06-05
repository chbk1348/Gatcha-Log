import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 상태 토스트 — Compose 의 GlgStatusToast 대응.
// message 가 들어오면 ~2.2초 노출 후 onConsumed() 호출(상태 초기화). 페이드+슬라이드.
// ════════════════════════════════════════════════════════════════════════════

private struct GLGToastModifier: ViewModifier {
    let message: String?
    let onConsumed: () -> Void
    var bottomPadding: CGFloat = 28

    @Environment(\.glgAccent) private var accent
    @State private var text = ""
    @State private var visible = false

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if visible {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(accent.primary)
                    Text(text)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 13)
                .background(
                    Color(hex: 0xF22A2C32),
                    in: RoundedRectangle(cornerRadius: 22, style: .continuous)
                )
                .padding(.bottom, bottomPadding)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        // message 변경 시 재실행(이전 task 취소 — Compose LaunchedEffect(message) 와 동일 의미).
        .task(id: message) {
            guard let m = message, !m.isEmpty else {
                withAnimation { visible = false }
                return
            }
            text = m
            withAnimation { visible = true }
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            guard !Task.isCancelled else { return }
            withAnimation { visible = false }
            onConsumed()
        }
    }
}

extension View {
    /// 하단 중앙 상태 토스트. [message] 가 nil/빈문자가 아니면 표시 후 자동 소비.
    func glgToast(message: String?, bottomPadding: CGFloat = 28, onConsumed: @escaping () -> Void) -> some View {
        modifier(GLGToastModifier(message: message, onConsumed: onConsumed, bottomPadding: bottomPadding))
    }
}
