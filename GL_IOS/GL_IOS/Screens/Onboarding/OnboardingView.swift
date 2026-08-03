import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 첫 실행 온보딩 — 로그인보다 앞에 오는 3페이지. 재설치 전까지 다시 뜨지 않는다(AppSettings.onboardingDone).
//
//   ① 지출 기록(가계부) → ② 게임 정보 확인 → ③ 알림
//
// 예전엔 천장 게이지가 첫 페이지였다. 뺀 이유는 이 앱의 성격이 **가챠 계산기가 아니라
// 지출 기록·가계부 + 게임 정보**에 가깝기 때문이다. 첫인상이 천장이면 실제로 매일 쓰는 기능
// (지출 기록·게임 정보 확인)이 뒤로 밀린다. 천장 계산은 앱 안 계산기에 그대로 있다.
//
// ①②는 게이지 링을 서로 다른 뜻으로 변주한다(가로 예산 바 → 줄어드는 D-day 링).
//
// 알림 권한은 ③에서 맥락과 함께 요청한다 — 앱 켜자마자 이유 없이 뜨던 팝업을 여기로 옮겼다.
// (Compose 패리티: GL_Android/ui/onboarding/OnboardingScreen.kt)
// ════════════════════════════════════════════════════════════════════════════

struct OnboardingView: View {
    /// 온보딩 종료. requestNotification=true 면 호출부가 OS 알림 권한을 요청한다("알림 켜고 시작하기").
    let onFinish: (_ requestNotification: Bool) -> Void

    // 강조색은 ContentView 가 로그인 전 구간에서 브랜드 민트로 고정해 주입한다(glgAccent(index: 0)).
    @Environment(\.glgAccent) private var accent

    @State private var page = 0

    private let pageCount = 3
    private var isLast: Bool { page == pageCount - 1 }

    var body: some View {
        ZStack {
            BrandGround()

            VStack(spacing: 0) {
                // 건너뛰기 — 마지막(알림) 페이지에는 "나중에 할게요"가 있으므로 숨긴다.
                HStack {
                    Spacer()
                    if !isLast {
                        Button("건너뛰기") { onFinish(false) }
                            .font(.pretendard(size: 12, weight: .semibold))
                            .foregroundStyle(Color(hex: 0xFF9AA5A1))
                    }
                }
                .frame(height: 22)
                .padding(.horizontal, 22)

                TabView(selection: $page) {
                    ForEach(0..<pageCount, id: \.self) { i in
                        VStack(spacing: 0) {
                            ZStack {
                                switch i {
                                case 0: BudgetArt()
                                case 1: ScheduleArt()
                                default: NotificationArt()
                                }
                            }
                            .frame(maxHeight: .infinity)

                            copy(for: i)
                            Spacer().frame(height: 24)
                        }
                        .padding(.horizontal, 32)
                        .tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never)) // 인디케이터는 하단 고정 영역에서 직접 그린다

                // 하단 고정 — 페이지가 넘어가도 인디케이터·버튼은 제자리에 있어야 흔들리지 않는다.
                VStack(spacing: 0) {
                    BrandPageDots(count: pageCount, current: page)
                    Spacer().frame(height: 18)

                    Button {
                        if isLast {
                            onFinish(true)
                        } else {
                            withAnimation(GLGMotion.standard()) { page += 1 }
                        }
                    } label: {
                        // 알약(캡슐) — 앱의 다른 주 버튼(GLGButton = .buttonBorderShape(.capsule))과 같은 모양.
                        Text(isLast ? "알림 켜고 시작하기" : "다음")
                            .font(.pretendard(size: 15, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(
                                LinearGradient(
                                    colors: [accent.secondary, accent.primary],
                                    startPoint: .leading, endPoint: .trailing
                                ),
                                in: Capsule()
                            )
                    }

                    // 마지막 페이지에서만 노출하되, 자리는 항상 차지시켜 버튼이 위아래로 튀지 않게 한다.
                    ZStack {
                        if isLast {
                            Button("나중에 할게요") { onFinish(false) }
                                .font(.pretendard(size: 13, weight: .semibold))
                                .foregroundStyle(Color(hex: 0xFF9AA5A1))
                        }
                    }
                    .frame(height: 40)
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 12)
            }
        }
    }

    @ViewBuilder
    private func copy(for page: Int) -> some View {
        let (title, desc): (String, String) = {
            switch page {
            case 0:
                return ("게임에 쓴 돈,\n가계부처럼 기록해요",
                        "게임별·달별 지출을 남기고 예산을 잡습니다.\n예산을 넘기면 저장 직전에 한 번 더 물어봐요.")
            case 1:
                return ("픽업도 공지도\n한 곳에서 확인",
                        "픽업·이벤트 일정, 공지, 실시간 재화까지 모아 봅니다.\n마감이 다가오면 남은 시간이 링으로 줄어들어요.")
            default:
                return ("중요한 순간에만\n알려드릴게요",
                        "픽업 마감, 예산 초과, 재화 가득 참.\n조용한 시간엔 보내지 않고, 언제든 끌 수 있어요.")
            }
        }()

        VStack(spacing: 10) {
            Text(title)
                .font(.pretendard(size: 21, weight: .bold))
                .multilineTextAlignment(.center)
                .lineSpacing(4)
            Text(desc)
                .font(.pretendard(size: 13))
                .foregroundStyle(GLGColor.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
        }
    }
}

// ── 페이지별 일러스트 — 전부 실제 앱이 보여주는 것의 축약본 ────────────────────

/// ① 지출·예산 — 가계부 성격을 첫 화면에서 보여준다. 초과는 테라코타(민트 하나에 기대지 않는다).
private struct BudgetArt: View {
    var body: some View {
        VStack(spacing: 9) {
            MiniBudgetCard(label: "이번 달 예산", value: "112%", fill: 1.0, over: true,
                           sub: "168,000원 / 150,000원 · 18,000원 초과")
            MiniBudgetCard(label: "원신", value: "92,000원", fill: 0.55, over: false,
                           sub: "창월의 시 · 결정 5회")
            MiniBudgetCard(label: "스타레일", value: "76,000원", fill: 0.45, over: false,
                           sub: "월정액 · 창세의 별")
        }
    }
}

private struct MiniBudgetCard: View {
    let label: String
    let value: String
    let fill: Double
    let over: Bool
    let sub: String

    @Environment(\.glgAccent) private var accent
    @State private var animated: Double = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(label).font(.pretendard(size: 12, weight: .bold))
                Spacer()
                Text(value)
                    .font(.pretendard(size: 12, weight: .bold))
                    .foregroundStyle(over ? Color(hex: 0xFFE2725B) : accent.primary)
            }
            Spacer().frame(height: 8)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(BrandMark.track)
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: over
                                    ? [Color(hex: 0xFFFFB088), Color(hex: 0xFFE2725B)]
                                    : [accent.secondary, accent.primary],
                                startPoint: .leading, endPoint: .trailing
                            )
                        )
                        .frame(width: geo.size.width * animated)
                }
            }
            .frame(height: 5)
            Spacer().frame(height: 6)
            Text(sub)
                .font(.pretendard(size: 10))
                .foregroundStyle(Color(hex: 0xFF97A29E))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(.white, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(Color(hex: 0xFFE8EEEC), lineWidth: 1)
        )
        .onAppear {
            withAnimation(.easeOut(duration: 0.9)) { animated = fill }
        }
    }
}

/// ② 게임 정보 — 줄어드는 D-day 링 + 임박 항목. 예산 바와 같은 형태를 다른 뜻으로 쓴다.
private struct ScheduleArt: View {
    @State private var progress: Double = 0

    var body: some View {
        VStack(spacing: 0) {
            BrandGaugeRing(progress: progress, size: 118) {
                VStack(spacing: 0) {
                    Text("D-3")
                        .font(.pretendard(size: 24, weight: .bold))
                        .foregroundStyle(BrandMark.navy)
                    Text("픽업 마감")
                        .font(.pretendard(size: 10, weight: .semibold))
                        .foregroundStyle(GLGColor.textSecondary)
                }
            }
            Spacer().frame(height: 18)
            MiniAlertChip(title: "에스코피에 픽업", trailing: "3일 남음")
            Spacer().frame(height: 7)
            MiniAlertChip(title: "레진 가득 참", trailing: "2시간 뒤")
        }
        .onAppear {
            withAnimation(.easeOut(duration: 1.1)) { progress = 0.26 }
        }
    }
}

private struct MiniAlertChip: View {
    let title: String
    let trailing: String

    @Environment(\.glgAccent) private var accent

    var body: some View {
        HStack(spacing: 8) {
            Circle().fill(accent.primary).frame(width: 6, height: 6)
            Text(title).font(.pretendard(size: 11, weight: .semibold))
            Spacer()
            Text(trailing)
                .font(.pretendard(size: 10))
                .foregroundStyle(Color(hex: 0xFF97A29E))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(.white, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color(hex: 0xFFE8EEEC), lineWidth: 1)
        )
    }
}

/// ③ 알림 — 링 대신 종. 여기서 OS 권한을 요청하므로 일러스트가 권한 팝업의 예고편이 된다.
private struct NotificationArt: View {
    @Environment(\.glgAccent) private var accent
    @State private var swinging = false

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    RadialGradient(
                        colors: [accent.primary.opacity(0.22), accent.primary.opacity(0)],
                        center: .center, startRadius: 0, endRadius: 66
                    )
                )
                .frame(width: 132, height: 132)

            Image(systemName: "bell.badge.fill")
                .font(.system(size: 58))
                .foregroundStyle(BrandMark.navy, accent.primary)
                .rotationEffect(.degrees(swinging ? 9 : -9), anchor: .top)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                swinging = true
            }
        }
    }
}
