import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 앱 로고 — 다크 네이비 스퀘어클 + 민트 위시 스타(SF Symbol sparkles).
// 진입 바운스(스케일·페이드) + 무한 호흡 펄스 + 글로우 헤일로. 로그인·로딩 화면 공용.
// (Compose LoginScreen.kt 의 WishStarLogo 대응 — R.drawable 대신 SF Symbol)
// ════════════════════════════════════════════════════════════════════════════

struct WishStarLogo: View {
    var boxSize: CGFloat = 84

    @Environment(\.glgAccent) private var accent
    @State private var entered = false
    @State private var pulsing = false

    var body: some View {
        ZStack {
            // 뒤에서 번지는 글로우 헤일로
            Circle()
                .fill(accent.primary.opacity(pulsing ? 0.32 : 0.12))
                .frame(width: boxSize * 1.4, height: boxSize * 1.4)
                .scaleEffect(pulsing ? 1.2 : 0.9)
                .opacity(entered ? 1 : 0)

            // 스퀘어클 네이비 배경 + 위시 스타
            RoundedRectangle(cornerRadius: boxSize * 0.27, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [Color(hex: 0xFF2B3F70), Color(hex: 0xFF0F1A33)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: boxSize, height: boxSize)
                .overlay(
                    Image(systemName: "sparkles")
                        .font(.pretendard(size: boxSize * 0.42, weight: .semibold))
                        .foregroundStyle(Color(hex: 0xFF34D1B6))
                )
                .scaleEffect(entered ? 1 : 0)
                .scaleEffect(pulsing ? 1.05 : 1.0)
                .opacity(entered ? 1 : 0)
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.5)) { entered = true }
            withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) { pulsing = true }
        }
    }
}
