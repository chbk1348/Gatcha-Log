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
                HStack(spacing: 10) {
                    ZStack {
                        Circle().fill(accent.primary.opacity(0.16)).frame(width: 26, height: 26)
                        Image(systemName: "checkmark").font(.system(size: 12, weight: .heavy)).foregroundStyle(accent.primary)
                    }
                    Text(text)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(GLGColor.textPrimary)
                        .lineLimit(2)
                }
                .padding(.leading, 10)
                .padding(.trailing, 18)
                .padding(.vertical, 10)
                .background(.regularMaterial, in: Capsule())
                .overlay(Capsule().stroke(Color.white.opacity(0.55), lineWidth: 0.5))
                .shadow(color: .black.opacity(0.14), radius: 18, y: 6)
                .padding(.horizontal, 24)
                .padding(.bottom, bottomPadding)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        // message 변경 시 재실행(이전 task 취소 — Compose LaunchedEffect(message) 와 동일 의미).
        .task(id: message) {
            guard let m = message, !m.isEmpty else {
                withAnimation(.easeOut(duration: 0.2)) { visible = false }
                return
            }
            text = m
            withAnimation(.spring(response: 0.4, dampingFraction: 0.78)) { visible = true }
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.25)) { visible = false }
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
