import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 계정 데이터 로딩 화면 — 기존 로그인 유저 진입 시 클라우드 동기화 중 표시.
// 디자인: design_loading_mockup.html(A) — 브랜드 위시 스타 + 회전 링 + 3단계 진행 도트.
// loading=false 되면 마지막 단계(완료)로 넘기고 onFinished() 호출.
// (Compose AccountLoadingScreen 과 패리티)
// ════════════════════════════════════════════════════════════════════════════

struct AccountLoadingView: View {
    /// 동기화 진행 중 여부 (false 가 되면 완료 처리 후 종료).
    let loading: Bool
    let onFinished: () -> Void

    @Environment(\.glgAccent) private var accent
    @State private var spin: Double = 0
    @State private var pulse: CGFloat = 1
    @State private var done = false   // 동기화 완료 → 마지막 단계 점등

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(hex: 0xFFEAFBF6), Color(hex: 0xFFF2F3F7)],
                center: UnitPoint(x: 0.5, y: 0.18), startRadius: 0, endRadius: 380
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // 회전 링 + 브랜드 위시 스타(펄스 글로우)
                ZStack {
                    Circle().stroke(accent.primary.opacity(0.18), lineWidth: 3).frame(width: 84, height: 84)
                    Circle().trim(from: 0, to: 0.25)
                        .stroke(accent.primary, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                        .frame(width: 84, height: 84)
                        .rotationEffect(.degrees(spin))
                    WishStarLogo(boxSize: 48)
                        .scaleEffect(pulse)
                        .shadow(color: accent.primary.opacity(0.55), radius: 16)
                }
                Spacer().frame(height: 26)
                (Text("Gatcha ") + Text("LOG").foregroundColor(accent.primary))
                    .font(.pretendard(size: 22, weight: .bold))
                Spacer().frame(height: 10)
                Text(done ? "동기화 완료" : "계정 데이터를 불러오는 중…")
                    .font(.pretendard(size: 13))
                    .foregroundStyle(GLGColor.textSecondary)

                // 3단계 진행 도트 — 연동 확인 · 클라우드 불러오기 · 완료
                Spacer().frame(height: 28)
                HStack(spacing: 8) {
                    stepDot(active: true, current: false)
                    stepBar(filled: true)
                    stepDot(active: true, current: !done)
                    stepBar(filled: done)
                    stepDot(active: done, current: false)
                }
                Spacer().frame(height: 12)
                (Text("연동 확인 · ")
                    + Text("클라우드 불러오기").fontWeight(.bold).foregroundColor(done ? GLGColor.textSecondary : accent.primary)
                    + Text(" · ")
                    + Text("완료").fontWeight(done ? .bold : .regular).foregroundColor(done ? accent.primary : GLGColor.textSecondary))
                    .font(.pretendard(size: 11))
                    .foregroundStyle(GLGColor.textSecondary)
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
            withAnimation(.linear(duration: 1).repeatForever(autoreverses: false)) { spin = 360 }
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { pulse = 1.08 }
            if !loading { finish() }
        }
        // iOS 16 호환 1-파라미터 onChange.
        .onChange(of: loading) { _, isLoading in
            if !isLoading { finish() }
        }
    }

    private func stepDot(active: Bool, current: Bool) -> some View {
        Circle()
            .fill(active ? accent.primary : GLGColor.divider)
            .frame(width: 8, height: 8)
            .overlay(current ? Circle().stroke(accent.primary.opacity(0.2), lineWidth: 4).frame(width: 16, height: 16) : nil)
    }

    private func stepBar(filled: Bool) -> some View {
        Capsule().fill(filled ? accent.secondary : GLGColor.divider).frame(width: 34, height: 2)
    }

    private func finish() {
        withAnimation(.easeOut(duration: 0.3)) { done = true }
        Task {
            try? await Task.sleep(nanoseconds: 420_000_000)
            onFinished()
        }
    }
}
