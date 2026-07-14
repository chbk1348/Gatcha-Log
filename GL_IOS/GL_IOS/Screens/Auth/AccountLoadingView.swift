import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 계정 데이터 로딩 화면 — 기존 로그인 유저 진입 시 클라우드 동기화 중 표시.
//
// v27.38.0 개편: 회전 스피너 링을 없애고 **앱 아이콘의 게이지 링이 차오르는 것 자체를 로딩**으로 쓴다.
// (기존엔 바깥 스피너 링 + 마크 안의 게이지 링이 겹쳐 링이 두 겹이었고, 바깥 것은 아무 의미가 없었다)
//
// 진행률: 클라우드 pull 은 실제 퍼센트를 알 수 없으므로 90%까지 천천히 차오르다,
// loading 이 끝나면 100%로 스냅하고 onFinished() 를 호출한다.
// (Compose AccountLoadingScreen 과 패리티)
// ════════════════════════════════════════════════════════════════════════════

struct AccountLoadingView: View {
    /// 동기화 진행 중 여부 (false 가 되면 완료 처리 후 종료).
    let loading: Bool
    let onFinished: () -> Void

    @Environment(\.glgAccent) private var accent
    @State private var progress: Double = 0
    @State private var pulse: CGFloat = 1
    @State private var done = false   // 동기화 완료 → 마지막 단계 점등

    private let stageIdle = Color(hex: 0xFFA3AEAA)

    var body: some View {
        ZStack {
            BrandGround()

            VStack(spacing: 0) {
                // 링 = 진행률, 중앙 별 = 호흡. 스퀘어클 배경을 벗겨 런치스크린에서 이어지는 것처럼 보이게 한다.
                BrandGaugeRing(progress: progress, size: 148) {
                    BrandStar(size: 58)
                        .scaleEffect(pulse)
                }

                Spacer().frame(height: 26)
                (Text("Gatcha ") + Text("LOG").foregroundColor(accent.primary))
                    .font(.pretendard(size: 22, weight: .bold))

                Spacer().frame(height: 10)
                Text(done ? "동기화 완료" : "계정 데이터를 불러오는 중…")
                    .font(.pretendard(size: 13))
                    .foregroundStyle(GLGColor.textSecondary)

                // 진행률은 링이 이미 말해주므로, 단계는 한 줄 텍스트로만 압축(기존 3단계 도트 대체).
                Spacer().frame(height: 22)
                HStack(spacing: 6) {
                    Text("연동 확인")
                        .font(.pretendard(size: 11, weight: .semibold))
                        .foregroundStyle(stageIdle)
                    stageSeparator
                    Text("클라우드 불러오기")
                        .font(.pretendard(size: 11, weight: .bold))
                        .foregroundStyle(done ? stageIdle : accent.primary)
                    stageSeparator
                    Text("완료")
                        .font(.pretendard(size: 11, weight: done ? .bold : .semibold))
                        .foregroundStyle(done ? accent.primary : stageIdle)
                }
            }
            .padding(.horizontal, 36)

            VStack {
                Spacer()
                Text("기기 간 데이터를 안전하게 동기화하고 있어요")
                    .font(.pretendard(size: 11))
                    .foregroundStyle(Color(hex: 0xFFA7ABB5))
                    .padding(.bottom, 30)
            }
        }
        .onAppear {
            // 미완료 구간 — 느리게 90%까지. 완료되면 finish() 가 100%로 이어받는다.
            withAnimation(.easeOut(duration: 2.6)) { progress = 0.9 }
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { pulse = 1.09 }
            if !loading { finish() }
        }
        // iOS 16 호환 1-파라미터 onChange.
        .onChange(of: loading) { _, isLoading in
            if !isLoading { finish() }
        }
    }

    private var stageSeparator: some View {
        Circle().fill(Color(hex: 0xFFD4DCD9)).frame(width: 3, height: 3)
    }

    private func finish() {
        guard !done else { return }
        withAnimation(.easeOut(duration: 0.42)) {
            done = true
            progress = 1
        }
        Task {
            try? await Task.sleep(nanoseconds: 660_000_000) // 링이 100%까지 차오르는 걸 보여주고 넘긴다
            onFinished()
        }
    }
}
