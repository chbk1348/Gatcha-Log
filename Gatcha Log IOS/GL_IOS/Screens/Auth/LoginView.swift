import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 구글 로그인 버튼 — 온보딩 완료 · 로그인 화면(OnboardingView loginOnly)이 쓴다.
// 옛 로그인 화면(LoginView · 온보딩 1.0)은 9/29 제거했다. (Android OnboardingScreen.kt GoogleSignInButton 파리티)
// ════════════════════════════════════════════════════════════════════════════

/// 구글 로그인 버튼 — 구글 로그인 브랜딩 가이드의 밝은 스타일(흰 바탕 · #747775 테두리 · 4색 G 로고 · #1F1F1F 글자).
/// 로그인 화면과 온보딩 복원 화면이 같이 쓴다(9/29). Android GoogleSignInButton 파리티.
struct GoogleSignInButton: View {
    let title: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image("GoogleG").resizable().scaledToFit().frame(width: 20, height: 20)
                Text(title).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(Color(hex: 0xFF1F1F1F))
            }
            .frame(maxWidth: .infinity).frame(height: 50)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color(hex: 0xFF747775), lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
