import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 로그인/온보딩 — Google 로그인 전용(게스트 모드 없음). (Compose LoginScreen.kt 대응)
// 상태/액션은 SpendingStore(= 공유 Kotlin VM) 를 통한다.
// ════════════════════════════════════════════════════════════════════════════

struct LoginView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [accent.primary.opacity(0.18), .white, accent.secondary.opacity(0.12)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                WishStarLogo(boxSize: 84)
                Spacer().frame(height: 20)
                Text("Gatcha LOG")
                    .font(.pretendard(size: 28, weight: .bold))
                Spacer().frame(height: 6)
                Text("가챠 지출을 똑똑하게 관리하세요")
                    .font(.pretendard(size: 14))
                    .foregroundStyle(GLGColor.textSecondary)

                Spacer().frame(height: 36)
                VStack(spacing: 14) {
                    FeatureRow(icon: "bolt.fill", title: "실시간 노트·출석",
                               desc: "레진·개척력·배터리와 출석을 한곳에서")
                    FeatureRow(icon: "percent", title: "확률표·통합 계산기",
                               desc: "천장·확보 확률·뽑기 플래너까지")
                    FeatureRow(icon: "icloud.and.arrow.up.fill", title: "구글 계정 동기화",
                               desc: "기기를 바꿔도 데이터 그대로")
                }

                Spacer().frame(height: 40)
                GLGButton(title: "Google로 로그인", systemImage: "g.circle.fill") {
                    store.signIn()
                }

                Spacer().frame(height: 20)
                Text("로그인하면 데이터가 구글 계정에 안전하게 저장·동기화됩니다.")
                    .font(.pretendard(size: 11))
                    .foregroundStyle(.gray)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 10)
                Text("앱을 다시 설치했나요? 이전에 쓰던 구글 계정으로 로그인하면 클라우드에 저장된 데이터가 복원돼요.")
                    .font(.pretendard(size: 11))
                    .foregroundStyle(accent.primary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 28)
        }
    }
}

private struct FeatureRow: View {
    let icon: String
    let title: String
    let desc: String
    @Environment(\.glgAccent) private var accent

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(accent.primary.opacity(0.12))
                    .frame(width: 40, height: 40)
                Image(systemName: icon)
                    .font(.pretendard(size: 18))
                    .foregroundStyle(accent.primary)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.pretendard(size: 14, weight: .bold))
                Text(desc).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
            Spacer(minLength: 0)
        }
    }
}
