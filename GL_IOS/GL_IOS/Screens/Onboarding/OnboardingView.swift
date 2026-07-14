import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 첫 실행 온보딩 — 로그인보다 앞에 오는 4페이지. 재설치 전까지 다시 뜨지 않는다(AppSettings.onboardingDone).
//
// 매 페이지가 앱 아이콘의 게이지 링을 **다른 의미로** 변주한다:
//   ① 천장 게이지(차오름) → ② 예산 게이지(초과) → ③ D-day 링(줄어듦) → ④ 알림
// 같은 형태를 세 번 다른 뜻으로 보여주면, 홈에 들어갔을 때 링을 이미 읽을 줄 알게 된다.
//
// 알림 권한은 ④에서 맥락과 함께 요청한다 — 앱 켜자마자 이유 없이 뜨던 팝업을 여기로 옮겼다.
// (Compose 패리티: GL_Android/ui/onboarding/OnboardingScreen.kt)
// ════════════════════════════════════════════════════════════════════════════

struct OnboardingView: View {
    /// 온보딩 종료. requestNotification=true 면 호출부가 OS 알림 권한을 요청한다("알림 켜고 시작하기").
    let onFinish: (_ requestNotification: Bool) -> Void

    // 강조색은 ContentView 가 로그인 전 구간에서 브랜드 민트로 고정해 주입한다(glgAccent(index: 0)).
    @Environment(\.glgAccent) private var accent

    @State private var page = 0

    private let pageCount = 4
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
                                case 0: PityArt()
                                case 1: BudgetArt()
                                case 2: ScheduleArt()
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
                                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
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
                return ("천장까지 몇 번 남았는지\n한눈에",
                        "게임별 천장 규칙을 알아서 계산합니다.\n확정 픽업까지 남은 뽑기 수와 필요한 금액까지.")
            case 1:
                return ("얼마나 썼는지\n솔직하게 마주보기",
                        "게임별·달별 지출과 예산을 기록합니다.\n예산을 넘기면 저장 직전에 한 번 더 물어봐요.")
            case 2:
                return ("픽업 마감을\n놓치지 않게",
                        "픽업·이벤트·레진 회복 일정을 모아 보여줍니다.\n마감이 다가오면 남은 시간이 링으로 줄어들어요.")
            default:
                return ("중요한 순간에만\n알려드릴게요",
                        "픽업 마감, 예산 초과, 레진 가득 참.\n조용한 시간엔 보내지 않고, 언제든 끌 수 있어요.")
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

/// ① 천장 게이지 — 아이콘의 링을 그대로 확대. 아이콘이 무슨 뜻인지 첫 화면에서 알려준다.
private struct PityArt: View {
    @State private var progress: Double = 0

    var body: some View {
        BrandGaugeRing(progress: progress, size: 148) {
            VStack(spacing: 0) {
                HStack(alignment: .lastTextBaseline, spacing: 1) {
                    Text("67").font(.pretendard(size: 30, weight: .bold))
                    Text("회").font(.pretendard(size: 15, weight: .bold))
                }
                .foregroundStyle(BrandMark.navy)
                Text("천장까지 23회")
                    .font(.pretendard(size: 10, weight: .semibold))
                    .foregroundStyle(GLGColor.textSecondary)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 1.1)) { progress = 0.87 }
        }
    }
}

/// ② 예산 게이지 — 같은 게이지를 가로 바로 변주. 초과는 테라코타(민트 하나에 기대지 않는다).
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

/// ③ D-day 링 — 세 번째 변주. 이번엔 '줄어드는' 링. 같은 형태, 다른 의미.
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

/// ④ 알림 — 링 대신 종. 여기서 OS 권한을 요청하므로 일러스트가 권한 팝업의 예고편이 된다.
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
