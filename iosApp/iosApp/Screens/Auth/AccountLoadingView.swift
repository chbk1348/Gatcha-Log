import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 계정 데이터 로딩 화면 — 기존 로그인 유저 진입 시 클라우드 동기화 중 표시.
// 0→90% 천천히 차오르다, loading=false 되면 100%로 채운 뒤 onFinished() 호출.
// (Compose LoginScreen.kt 의 AccountLoadingScreen 대응)
// ════════════════════════════════════════════════════════════════════════════

struct AccountLoadingView: View {
    /// 동기화 진행 중 여부 (false 가 되면 100% 채우고 종료).
    let loading: Bool
    let onFinished: () -> Void

    @Environment(\.glgAccent) private var accent
    @State private var progress: Double = 0

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [accent.primary.opacity(0.20), .white, accent.secondary.opacity(0.14)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                WishStarLogo(boxSize: 100)
                Spacer().frame(height: 26)
                Text("Gatcha LOG").font(.system(size: 24, weight: .bold))
                Spacer().frame(height: 6)
                Text("계정 정보를 불러오는 중…")
                    .font(.system(size: 13))
                    .foregroundStyle(GLGColor.textSecondary)

                Spacer().frame(height: 30)
                // 라운드 그라데이션 프로그레스 바
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(GLGColor.progressEmpty)
                        Capsule()
                            .fill(LinearGradient(
                                colors: [accent.secondary, accent.primary],
                                startPoint: .leading, endPoint: .trailing))
                            .frame(width: geo.size.width * progress)
                    }
                }
                .frame(height: 10)

                Spacer().frame(height: 10)
                Text("\(Int((progress * 100).rounded()))%")
                    // 고정폭 숫자 — 애니메이션 중 자릿수/글자폭 변화로 가운데정렬이 좌우로 떨리는 것 방지
                    .font(.system(size: 13, weight: .bold).monospacedDigit())
                    .foregroundStyle(accent.primary)
                    .frame(minWidth: 44)  // "100%" 폭 확보 → 자릿수 늘어도 위치 안 밀림
            }
            .padding(.horizontal, 36)
        }
        .onAppear {
            // 진입하면 90%까지 천천히
            withAnimation(.easeOut(duration: 1.1)) { progress = 0.9 }
            if !loading { finish() }
        }
        // iOS 16 호환 1-파라미터 onChange (2-파라미터는 17+). 최소 타깃 iOS 18 상향 시 정리 가능.
        .onChange(of: loading) { isLoading in
            if !isLoading { finish() }
        }
    }

    private func finish() {
        withAnimation(.easeOut(duration: 0.35)) { progress = 1 }
        // 채움 애니메이션 후 종료 콜백
        Task {
            try? await Task.sleep(nanoseconds: 380_000_000)
            onFinished()
        }
    }
}
