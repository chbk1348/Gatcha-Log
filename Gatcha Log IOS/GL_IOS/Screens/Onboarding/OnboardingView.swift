import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 첫 실행 온보딩 B안(개인화 설정) — 로그인보다 앞. 아티팩트 「온보딩 2.0」 B① ~ B⑥ + 구현 명세가 정본.
//
//   ① 환영 → ② 게임 → ③ 예산 → ④ HoYoLAB(호요버스 게임을 골랐을 때만) → ⑤ 알림 → ⑥ 완료
//
// 고른 값은 **설정 화면과 같은 저장소**에 쓴다(store.applyOnboarding) — 내 게임 · 월 예산 ·
// 알림 4종 · 방해 금지. HoYoLAB 은 설정 ▸ HoYoLAB 연동과 같은 로그인 창 · 같은 저장(updateHoyolabConfig).
// (Compose 패리티: GL_Android/ui/onboarding/OnboardingScreen.kt)
// ════════════════════════════════════════════════════════════════════════════

private enum OB {
    static let ink = Color(hex: 0xFF0F1A33)
    static let sub = Color(hex: 0xFF5E6B68)
    static let teal = Color(hex: 0xFF177881)
    static let tealBright = Color(hex: 0xFF1B8E99)
    static let tealTint = Color(hex: 0xFFEEF8F8)
    static let tealSoft = Color(hex: 0xFFE3F2F1)
    static let line = Color(hex: 0xFFE3E8E6)
    static let ground = Color(hex: 0xFFF5F8F8)
    static let warn = Color(hex: 0xFFC2410C)
    static let hoyoKeys: Set<String> = ["genshin", "hsr", "zzz"]
    static let presets: [(Int64, String)] = [(50_000, "5만원"), (100_000, "10만원"), (150_000, "15만원"), (300_000, "30만원")]
    static let warnBudget: Int64 = 500_000
    /// 온보딩 ② 게임 — 설정 ▸ 내 게임과 같은 6게임(9/29 이환 추가). GameData.onboardingGames 와 같다.
    static let games: [Game] = GameData.shared.onboardingGames
}

/// 완료 요약 한 줄 — 짧은 이름으로, 넷 이상이면 「앞 셋 외 N」(오른쪽 정렬 값이 여러 줄로 꺾이지 않게, 9/29).
private func shortList(_ names: [String], empty: String = "—") -> String {
    if names.isEmpty { return empty }
    if names.count <= 3 { return names.joined(separator: " · ") }
    return names.prefix(3).joined(separator: " · ") + " 외 \(names.count - 3)"
}

private func obWon(_ n: Int64) -> String {
    let f = NumberFormatter(); f.numberStyle = .decimal
    return f.string(from: NSNumber(value: n)) ?? "\(n)"
}

private struct GlyphTile {
    let key: String
    let asset: String
    let label: String
    let from: Color
    let to: Color
    var labelColor: Color = .white
}

private let obTiles: [GlyphTile] = [
    GlyphTile(key: "genshin", asset: "GlyphGenshin", label: "원신", from: Color(hex: 0xFF6FA5FA), to: Color(hex: 0xFF3E76E0)),
    GlyphTile(key: "hsr", asset: "GlyphHsr", label: "스타레일", from: Color(hex: 0xFFC48CFF), to: Color(hex: 0xFF9350F0)),
    GlyphTile(key: "zzz", asset: "GlyphZzz", label: "젠레스", from: Color(hex: 0xFFFFC15A), to: Color(hex: 0xFFE8931A), labelColor: Color(hex: 0xFF3B2600)),
    GlyphTile(key: "wuwa", asset: "GlyphWuwa", label: "명조", from: Color(hex: 0xFFF0479B), to: Color(hex: 0xFFC8006E)),
    GlyphTile(key: "endfield", asset: "GlyphEndfield", label: "엔드필드", from: Color(hex: 0xFF3FD3C4), to: Color(hex: 0xFF139C8F)),
    GlyphTile(key: "nte", asset: "GlyphNte", label: "이환", from: Color(hex: 0xFF8E80F0), to: Color(hex: 0xFF6C5CE7)),
]

struct OnboardingView: View {
    var store: SpendingStore
    /// 온보딩을 마친 뒤 로그아웃 등으로 로그인이 필요할 때 — 옛 로그인 화면(온보딩 1.0) 대신
    /// ⑥ 복원 화면(「로그인하면 불러와요」 + 구글 로그인 버튼)만 띄운다(9/29).
    var loginOnly: Bool = false
    /// 온보딩 종료. requestNotification=true 면 호출부가 OS 알림 권한을 요청한다(「알림 켜고 시작하기」).
    let onFinish: (_ requestNotification: Bool, _ signIn: Bool) -> Void

    @State private var step = 0
    @State private var forward = true
    @State private var busy = false
    @State private var games: Set<String> = []
    @State private var budget: Int64 = 150_000
    @State private var custom = false
    @State private var attend = true
    @State private var resin = true
    @State private var pickup = true
    @State private var budgetAlert = false
    @State private var alerts = true
    @State private var restored = false
    @State private var settled = false   // ② 첫 진입 차례 등장은 한 번만
    @State private var leaving = false   // ① → ② 타일 흩어짐
    @State private var exiting = false   // ⑥ → 홈 연타 방지
    @State private var loginRequested = false
    @State private var applied = false   // 완료 버튼을 다시 눌러도(로그인 재시도) 설정은 한 번만 쓴다

    private var noHoyo: Bool { games.isDisjoint(with: OB.hoyoKeys) }
    private var total: Int { noHoyo ? 3 : 4 }
    private var shown: Int { noHoyo && step == 4 ? 3 : step }

    var body: some View {
        VStack(spacing: 0) {
            topBar
                .padding(.horizontal, 24)
                .frame(maxWidth: 480)
            // iPad · 듀오 대응(9/29): 폭은 폰 크기(480)로 가운데에 모은다. 480 제한은 **페이지 안쪽**에만 —
            // 바깥 틀에 걸면 옆으로 밀리는 전환이 틀 가장자리에서 잘렸다. 버튼 틀은 하단에 상시 고정,
            // 세로가 모자라면 스크롤 대신 촘촘한 배치로(StepBody).
            ZStack {
                page(step)
                    .padding(.horizontal, 24)
                    .frame(maxWidth: 480, maxHeight: .infinity)
                    .id(step)
                    .transition(.asymmetric(
                        insertion: .offset(x: forward ? 36 : -36).combined(with: .opacity),
                        removal: .offset(x: forward ? -28 : 28).combined(with: .opacity)
                    ))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
        }
        .frame(maxWidth: .infinity)
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            games = store.myGames.intersection(Set(OB.games.map { $0.key }))
            if loginOnly { step = 5; restored = true }
        }
        // 완료 화면에서 띄운 구글 로그인이 끝나면 온보딩을 마친다(로그인 화면을 거치지 않는다).
        .onChange(of: store.needsLogin) { _, needs in
            if !needs && (step == 5 || loginRequested) && !exiting {
                exiting = true
                onFinish(false, false)
            }
        }
    }

    // ── 상단: 뒤로 + 진행 막대(②~⑤) ──
    private var topBar: some View {
        HStack(spacing: 10) {
            if (1...4).contains(step) {
                Button { go(step - 1) } label: {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .semibold)).foregroundStyle(OB.ink)
                        .frame(width: 32, height: 32).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("뒤로")
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color(hex: 0xFFE1EDEA))
                        Capsule().fill(OB.tealBright).frame(width: geo.size.width * CGFloat(shown) / CGFloat(total))
                    }
                }
                .frame(height: 4)
                .animation(.easeInOut(duration: 0.4), value: shown)
                Text("\(shown)/\(total)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(OB.sub)
            }
        }
        .frame(height: 64)
    }

    /// 페이지 이동 — 전환 중엔 입력 무시. ④는 호요버스 게임이 없으면 건너뛴다.
    private func go(_ target: Int) {
        guard !busy else { return }
        let t = (noHoyo && target == 3) ? (step < 3 ? 4 : 2) : target
        forward = t >= step
        busy = true
        withAnimation(.easeOut(duration: 0.3)) { step = t }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.26) { busy = false }
    }

    @ViewBuilder
    private func page(_ s: Int) -> some View {
        switch s {
        case 0: welcome
        case 1: gamesPage
        case 2: BudgetPage(budget: $budget, custom: $custom, onNext: { go(3) }, onSkip: { budget = -1; go(3) })
        case 3: HoyolabPage(store: store, games: games, onNext: { go(4) })
        case 4: notifyPage
        default: donePage
        }
    }

    // ── ① 환영 ──
    private var welcome: some View {
        VStack(spacing: 0) {
            StepBody(center: true) { _ in
                TileFan(leaving: leaving)
                Spacer().frame(height: 28)
                Text("하는 게임에 맞춘\n나만의 게임 가계부")
                    .font(.pretendard(size: 26, weight: .bold)).foregroundStyle(OB.ink)
                    .multilineTextAlignment(.center).lineSpacing(6)
                Text("게임을 고르고 예산 · 연동 · 알림을 정하면,\n홈이 첫날부터 내 기록으로 채워져요.")
                    .font(.pretendard(size: 14)).foregroundStyle(OB.sub)
                    .multilineTextAlignment(.center).lineSpacing(4).padding(.top, 10)
                HStack(spacing: 6) {
                    ForEach(Array(["게임", "예산", "연동", "알림"].enumerated()), id: \.offset) { i, c in
                        Text("\(i + 1) \(c)").font(.pretendard(size: 12, weight: .bold))
                            .foregroundStyle(i == 0 ? .white : OB.sub)
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(i == 0 ? OB.ink : OB.ground, in: Capsule())
                    }
                }
                .padding(.top, 24)
                Text("약 1분 · 게임 고르기 말고는 전부 건너뛸 수 있어요")
                    .font(.pretendard(size: 12)).foregroundStyle(OB.sub).padding(.top, 12)
            }
            CtaButton(title: "시작하기", primary: true) {
                guard !busy, !leaving else { return }
                // 타일이 차례로 위로 흩어진 뒤 넘어간다(0.32초)
                leaving = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.32) {
                    settled = false; restored = false
                    go(1)
                    leaving = false
                }
            }
            // 「구글 로그인 하기」 — 설정 단계 없이 바로 구글 로그인(기존 사용자 복원). 성공하면 onChange 가 마친다.
            CtaButton(title: "구글 로그인 하기", primary: false) { loginRequested = true; store.signIn() }
            Spacer().frame(height: 16)
        }
    }

    // ── ② 게임 ──
    private var gamesPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            StepBody { h in
                PageTitle(title: "어떤 게임을 하세요?", sub: "고른 게임이 지출 입력 맨 위에 오고, 출석도 고른 게임만 챙겨요.")
                    .enterUp(delay: settled ? nil : 0.04)
                VStack(spacing: 8) {
                    ForEach(Array(OB.games.enumerated()), id: \.element.key) { i, g in
                        let on = games.contains(g.key)
                        Button {
                            withAnimation(.easeOut(duration: 0.18)) {
                                if on { games.remove(g.key) } else { games.insert(g.key) }
                            }
                        } label: {
                            HStack {
                                Text(g.displayName).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(OB.ink)
                                Spacer()
                                ZStack {
                                    Circle().fill(on ? OB.tealBright : OB.line).frame(width: 22, height: 22)
                                    if on { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white) }
                                }
                                .scaleEffect(on ? 1 : 0.75)
                            }
                            .padding(.horizontal, 16).frame(height: h < 520 ? 46 : 58)
                            .background(on ? OB.tealTint : .white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(on ? OB.tealBright : OB.line, lineWidth: on ? 2 : 1))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .enterUp(delay: settled ? nil : 0.16 + Double(i) * 0.06)
                    }
                }
                .padding(.top, h < 520 ? 16 : 24)
            }
            CtaButton(title: games.isEmpty ? "게임을 하나 이상 골라 주세요" : "\(games.count)개 선택 · 다음",
                      primary: true, enabled: !games.isEmpty, delay: settled ? 0.14 : 0.46) {
                settled = true; go(2)
            }
            Spacer().frame(height: 16)
        }
    }

    // ── ⑤ 알림 ──
    private var notifyPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            StepBody { h in
                PageTitle(title: "어떤 알림을 받을까요?", sub: "필요한 것만 켜 두세요. 설정 ▸ 알림에서 언제든 바꿀 수 있어요.")
                // 화면이 낮으면 미리보기 카드는 건너뛴다 — 고를 스위치가 먼저다.
                if h >= 560 {
                    NotifyPreviewCard(title: noHoyo ? "이번 달 예산의 90% 를 썼어요" : "레진이 곧 가득 차요",
                                      detail: noHoyo ? "150,000원 중 135,000원 · 남은 15,000원" : "원신 190 / 200 · 20분 뒤 가득")
                        .padding(.top, 20)
                }
                VStack(spacing: 0) {
                    // 호요버스 게임이 없으면 출석 · 행동력은 쓸 데가 없어 숨긴다.
                    if !noHoyo {
                        notifyRow("calendar.badge.checkmark", .teal, "출석", "자동 출석 결과 · 저녁까지 미출석이면", $attend)
                        SetDivider()
                        notifyRow("clock", .blue, "행동력 가득", "가득 차기 전 한 번", $resin)
                        SetDivider()
                    }
                    notifyRow("star", .purple, "픽업 마감", "D-3 · D-1", $pickup)
                    SetDivider()
                    notifyRow("creditcard", .orange, "예산 초과", "90% · 100% 넘을 때", $budgetAlert)
                }
                .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(OB.line, lineWidth: 1))
                .padding(.top, 14)
                HStack(spacing: 12) {
                    Image(systemName: "moon").font(.system(size: 16, weight: .semibold)).foregroundStyle(Color(hex: 0xFF4F5C59))
                    Text("방해 금지 시간").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(OB.ink)
                    Spacer()
                    Text(String(format: "%02d:00 ~ %02d:00", store.notifyDndStartHour, store.notifyDndEndHour))
                        .font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(Color(hex: 0xFF4F5C59))
                        .padding(.horizontal, 12).padding(.vertical, 5)
                        .background(Color.white, in: Capsule()).overlay(Capsule().strokeBorder(OB.line, lineWidth: 1))
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(OB.ground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .padding(.top, 10)
            }
            // OS 알림 권한을 그 자리에서 묻고, 답하면 ⑥ 완료로(9/29). 완료 화면은 구글 로그인만.
            CtaButton(title: "알림 켜고 시작하기", primary: true) {
                alerts = true; restored = false
                AppSettings().notifPermAsked = true
                NotificationPermission.request { _ in go(5) }
            }
            CtaButton(title: "알림 없이 시작", primary: false) { alerts = false; restored = false; go(5) }
            Spacer().frame(height: 16)
        }
    }

    private func notifyRow(_ symbol: String, _ tint: SetTint, _ title: String, _ desc: String, _ isOn: Binding<Bool>) -> some View {
        Button { withAnimation(.easeOut(duration: 0.18)) { isOn.wrappedValue.toggle() } } label: {
            HStack(spacing: 12) {
                SetIcon(symbol: symbol, tint: tint)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(OB.ink)
                    Text(desc).font(.pretendard(size: 12)).foregroundStyle(OB.sub)
                }
                Spacer()
                ObToggle(on: isOn.wrappedValue)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue(isOn.wrappedValue ? "켜짐" : "꺼짐")
    }

    // ── ⑥ 완료 ──
    private var summary: [(String, String, String)] {
        // 로그인 유도 화면(restored) — 로그인하면 좋은 점 세 줄(9/29 목적 변경).
        if restored {
            return [("icloud", "기기를 바꿔도 그대로", "지출 · 예산 · 설정이 구글 계정에 저장돼요"),
                    ("clock.arrow.circlepath", "쓰던 계정이면 기록 복원", "로그인만 하면 이전 기록이 돌아와요"),
                    ("person", "가입 없이 구글 계정 하나로", "따로 만들 계정도, 비밀번호도 없어요")]
        }
        var rows: [(String, String, String)] = []
        rows.append(("gamecontroller", "게임", shortList(OB.games.filter { games.contains($0.key) }.map { $0.shortName })))
        rows.append(("wallet.pass", "월 예산", budget > 0 ? "\(obWon(budget))원" : "없음"))
        if !noHoyo { rows.append(("link", "HoYoLAB", store.hoyolabConfig.isLinked ? "연결됨" : "나중에")) }
        var on: [String] = []
        if !noHoyo && attend { on.append("출석") }
        if !noHoyo && resin { on.append("행동력 가득") }
        if pickup { on.append("픽업 마감") }
        if budgetAlert { on.append("예산 초과") }
        rows.append(("bell", "알림", alerts ? shortList(on, empty: "모두 끔") : "받지 않음"))
        return rows
    }

    private func finishDone() {
        guard !exiting else { return }
        // 설정값은 누르는 순간 저장한다(게스트여도 — 예산은 로그인 직후 계정에 적용된다).
        if !restored && !applied {
            applied = true
            // 호요버스 게임이 없으면 숨긴 두 알림은 켜지 않는다(쓸 데가 없다).
            store.applyOnboarding(
                games: games, budget: budget > 0 ? budget : -1, alerts: alerts,
                attendance: !noHoyo && attend, resin: !noHoyo && resin, pickup: pickup, budgetAlert: budgetAlert
            )
        }
        // 로그인 전이면 온보딩을 **띄운 채로** 구글 로그인만 띄운다 — 먼저 끝내면 뒤에 옛 로그인 화면이 깔려 보였다(9/29).
        // 로그인이 끝나면(needsLogin → false) 아래 onChange 가 온보딩을 마친다. 취소하면 이 화면에 그대로 남는다.
        if store.needsLogin {
            store.signIn()
            return
        }
        // 퇴장 연출은 ContentView 루트 전환이 맡는다 — 여기서 먼저 지우면 다음 화면이 오기 전 빈 화면이 스쳤다(9/29).
        exiting = true
        onFinish(false, false)   // 알림 권한은 ⑤에서 이미 물었다
    }

    private var donePage: some View {
        VStack(spacing: 0) {
            StepBody(center: true) { _ in
                DoneBurst(symbol: restored ? "person.fill" : "checkmark")
                Text(restored ? "로그인하고 시작해요" : "준비 끝!")
                    .font(.pretendard(size: 26, weight: .bold)).foregroundStyle(OB.ink).padding(.top, 10)
                Text(restored ? "Gatcha Log 는 기록을 구글 계정에 안전하게 저장해요"
                     : (store.needsLogin ? "Google 계정으로 로그인하면 바로 시작해요" : "이제 홈에서 내 기록을 볼 수 있어요"))
                    .font(.pretendard(size: 13.5)).foregroundStyle(OB.sub).multilineTextAlignment(.center).padding(.top, 6)
                VStack(spacing: 0) {
                    ForEach(Array(summary.enumerated()), id: \.offset) { i, row in
                        if i > 0 { Rectangle().fill(Color(hex: 0xFFE6ECEA)).frame(height: 1) }
                        HStack(spacing: 12) {
                            Image(systemName: row.0).font(.system(size: 14, weight: .semibold)).foregroundStyle(OB.teal)
                                .frame(width: 30, height: 30).background(Color.white, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            if restored {
                                // 좋은 점 — 제목(굵게) + 설명
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(row.1).font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(OB.ink)
                                    Text(row.2).font(.pretendard(size: 12)).foregroundStyle(OB.sub)
                                }
                                Spacer(minLength: 0)
                            } else {
                                Text(row.1).font(.pretendard(size: 13)).foregroundStyle(OB.sub)
                                Spacer(minLength: 12)
                                Text(row.2).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(OB.ink).lineLimit(1).truncationMode(.tail)
                            }
                        }
                        .padding(.vertical, 12)
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 6)
                .background(OB.ground, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .padding(.top, 22)
                Text(restored ? "구글 로그인만 써요 · 게스트 모드는 없어요"
                     : (store.needsLogin ? "고른 설정은 구글 계정에 저장돼 기기를 바꿔도 그대로예요" : "설정 ▸ 내 설정에서 언제든 바꿀 수 있어요"))
                    .font(.pretendard(size: 12)).foregroundStyle(Color(hex: 0xFF7A8784)).multilineTextAlignment(.center).padding(.top, 22)
            }
            // 로그인 전(첫 사용자 · 복원)이면 구글 로그인 버튼(9/29) — 누르면 로그인 화면을 거치지 않고 바로 구글 로그인이 뜬다.
            Group {
                if store.needsLogin {
                    GoogleSignInButton(title: restored ? "Google로 로그인하기" : "Google로 로그인하고 시작하기") { finishDone() }
                        .padding(.horizontal, 8).enterUp(delay: 0.14)
                } else {
                    CtaButton(title: "홈으로 이동하기", primary: true) { finishDone() }
                }
            }
            Spacer().frame(height: 16)
        }
    }
}

// ── 공통 조각 ─────────────────────────────────────────────────────────────

/// 단계 내용 칸 — 버튼 틀 위의 남는 높이를 다 쓰고, 그 높이를 내용에 넘긴다(스크롤 없음, 9/29).
/// 화면이 낮으면(작은 폰 · 듀오 · 가로 iPad) 각 단계가 이 높이를 보고 촘촘한 배치로 바꿔 한 화면에 넣는다.
/// 버튼 틀은 이 칸 밖(아래)이라 하단에 상시 고정된다. center 면 가운데(환영 · 완료), 아니면 위에서부터.
private struct StepBody<Content: View>: View {
    var center: Bool = false
    @ViewBuilder var content: (_ height: CGFloat) -> Content
    var body: some View {
        GeometryReader { geo in
            VStack(alignment: center ? .center : .leading, spacing: 0) { content(geo.size.height) }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: center ? .center : .topLeading)
        }
    }
}

private struct PageTitle: View {
    let title: String
    var sub: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.pretendard(size: 24, weight: .bold)).foregroundStyle(OB.ink)
            if let sub { Text(sub).font(.pretendard(size: 13.5)).foregroundStyle(OB.sub).lineSpacing(3) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }
}

/// 처음 그려질 때 아래(16pt)에서 올라오며 나타남. delay 가 nil 이면 효과 없음.
private struct EnterUp: ViewModifier {
    let delay: Double?
    @State private var shown = false
    func body(content: Content) -> some View {
        if let delay {
            content
                .opacity(shown ? 1 : 0)
                .offset(y: shown ? 0 : 16)
                .onAppear { withAnimation(.easeOut(duration: 0.42).delay(delay)) { shown = true } }
        } else {
            content
        }
    }
}

private extension View {
    func enterUp(delay: Double?) -> some View { modifier(EnterUp(delay: delay)) }
}

/// 주 버튼 50 · 보조 42, 좌우 8 들여씀. 눌림(0.96배) + 아래에서 올라오는 등장.
private struct CtaButton: View {
    let title: String
    let primary: Bool
    var enabled: Bool = true
    var bg: Color? = nil
    var fg: Color? = nil
    var delay: Double? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.pretendard(size: primary ? 15 : 13, weight: .bold))
                .foregroundStyle(fg ?? (primary ? .white : OB.teal))
                .frame(maxWidth: .infinity)
                .frame(height: primary ? 50 : 42)
                .background(enabled ? (bg ?? (primary ? OB.teal : OB.tealSoft)) : Color(hex: 0xFFB8C4C1),
                            in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PressScale())
        .disabled(!enabled)
        .padding(.horizontal, 8)
        .padding(.top, primary ? 0 : 8)
        .enterUp(delay: delay ?? (primary ? 0.14 : 0.2))
    }
}

private struct PressScale: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

private struct ObToggle: View {
    let on: Bool
    var body: some View {
        ZStack(alignment: on ? .trailing : .leading) {
            Capsule().fill(on ? OB.tealBright : Color(hex: 0xFFCBD5D3)).frame(width: 44, height: 26)
            Circle().fill(.white).frame(width: 20, height: 20).shadow(color: .black.opacity(0.2), radius: 1, y: 1).padding(3)
        }
        .frame(width: 44, height: 26)
    }
}

/// ① 6게임 타일 — 62 · 10씩 겹친 부채꼴. 위에서 차례로 떨어지고, 떠날 때 위로 흩어진다.
private struct TileFan: View {
    let leaving: Bool
    @State private var dropped = false
    private let rot: [Double] = [-10, -6, -2, 2, 6, 10]
    private let lift: [CGFloat] = [0, 8, 14, 14, 8, 0]
    private let z: [Double] = [1, 2, 3, 3, 2, 1]

    var body: some View {
        HStack(spacing: -10) {
            ForEach(Array(obTiles.enumerated()), id: \.offset) { i, t in
                VStack(spacing: 2) {
                    Image(t.asset).resizable().scaledToFit().frame(width: i == 0 ? 29 : (i == 5 ? 34 : 31), height: i == 0 ? 29 : (i == 5 ? 34 : 31))
                    Text(t.label).font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(t.labelColor)
                }
                .frame(width: 57, height: 57)
                .background(LinearGradient(colors: [t.from, t.to], startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding(2.5)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .shadow(color: OB.ink.opacity(0.14), radius: 7, y: 6)
                .rotationEffect(.degrees(rot[i]))
                .offset(y: -lift[i] / 2)
                .zIndex(z[i])
                .opacity(dropped && !leaving ? 1 : 0)
                .scaleEffect(leaving ? 0.8 : (dropped ? 1 : 0.8))
                .offset(y: leaving ? -44 : (dropped ? 0 : -28))
                .animation(leaving ? .easeIn(duration: 0.3).delay(Double(i) * 0.03)
                                   : .spring(response: 0.5, dampingFraction: 0.62).delay(Double(i) * 0.08),
                           value: dropped && !leaving)
            }
        }
        .frame(height: 112)
        .onAppear { dropped = true }
    }
}

/// ⑥ 체크 원 + 게임 색 점.
private struct DoneBurst: View {
    var symbol: String = "checkmark"
    @State private var pop = false
    private let dots: [(CGFloat, CGFloat, CGFloat, UInt32)] = [
        (8, 22, 10, 0xFF6FA5FA), (22, 70, 7, 0xFFC48CFF), (104, 14, 8, 0xFFFFC15A),
        (116, 60, 11, 0xFFF0479B), (94, 84, 6, 0xFF3FD3C4), (34, 4, 5, 0xFF1FA0AB),
    ]
    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(dots.enumerated()), id: \.offset) { _, d in
                Circle().fill(Color(hex: d.3)).frame(width: d.2, height: d.2).offset(x: d.0, y: d.1).opacity(pop ? 1 : 0)
            }
            Image(systemName: symbol).font(.system(size: 32, weight: .bold)).foregroundStyle(.white)
                .frame(width: 72, height: 72)
                .background(LinearGradient(colors: [Color(hex: 0xFF1FA0AB), Color(hex: 0xFF146E77)],
                                           startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                .shadow(color: OB.teal.opacity(0.3), radius: 12, y: 10)
                .scaleEffect(pop ? 1 : 0.4).opacity(pop ? 1 : 0)
                .offset(x: 30, y: 12)
        }
        .frame(width: 132, height: 96, alignment: .topLeading)
        .onAppear { withAnimation(.spring(response: 0.42, dampingFraction: 0.6)) { pop = true } }
    }
}

// ── ③ 예산 ─────────────────────────────────────────────────────────────────

private struct BudgetPage: View {
    @Binding var budget: Int64
    @Binding var custom: Bool
    let onNext: () -> Void
    let onSkip: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            StepBody { _ in
                PageTitle(title: "한 달에 얼마까지 쓸까요?", sub: "넘기기 전에 알려 드려요. 게임별 한도는 나중에 정해도 돼요.")
                BudgetAmountEditor(budget: $budget, custom: $custom).padding(.top, 28)
            }
            CtaButton(title: "다음", primary: true) { hideKeyboard(); onNext() }
            CtaButton(title: "예산 없이 쓸게요", primary: false) { hideKeyboard(); onSkip() }
            Spacer().frame(height: 16)
        }
    }
}

private func hideKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}

/// 월 예산 금액 카드 — 직접 입력 + 5만~30만원(숫자가 굴러감) + 직접 입력 버튼 + 50만원 이상 주의.
/// 온보딩 ③과 설정 ▸ 예산 관리(아티팩트 S3)가 같이 쓴다. Android BudgetAmountEditor 파리티.
struct BudgetAmountEditor: View {
    @Binding var budget: Int64
    @Binding var custom: Bool

    @State private var text = ""
    @State private var shake: CGFloat = 0
    @State private var glow = false
    @State private var rollTask: Task<Void, Never>? = nil
    @FocusState private var focused: Bool

    private var value: Int64 { max(budget, 0) }
    private var warn: Bool { value >= OB.warnBudget }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 6) {
                Text("월 예산").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(warn ? OB.warn : OB.teal)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    TextField("0", text: $text)
                        .keyboardType(.numberPad)
                        .focused($focused)
                        .font(.pretendard(size: 40, weight: .bold))
                        .foregroundStyle(warn ? OB.warn : OB.ink)
                        .fixedSize()
                        .onChange(of: text) { _, raw in
                            // 숫자만 · 최대 9자리 · 콤마 자동. 직접 치는 동안은 굴리지 않는다.
                            guard focused else { return }
                            let digits = String(raw.filter(\.isNumber).prefix(9))
                            let n = Int64(digits) ?? 0
                            let formatted = n > 0 ? obWon(n) : ""
                            if formatted != raw { text = formatted }
                            rollTask?.cancel()
                            budget = n; custom = true
                        }
                    Text("원").font(.pretendard(size: 20, weight: .bold)).foregroundStyle(warn ? OB.warn : OB.ink)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 22)
            .background(LinearGradient(colors: warn ? [Color(hex: 0xFFFFF4E8), Color(hex: 0xFFFFE2C7)] : [OB.tealTint, Color(hex: 0xFFDCF0EE)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Color(hex: 0xFFEA580C).opacity(warn ? (glow ? 0.16 : 0) : 0), lineWidth: 5))
            .offset(x: shake)
            .animation(.easeInOut(duration: 0.3), value: warn)

            if warn {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "exclamationmark.triangle").font(.system(size: 13, weight: .semibold)).foregroundStyle(OB.warn)
                    Text("**한 달 50만원 이상이에요.** 무리하지 않는 금액인지 한 번만 더 확인해 주세요. 넘기기 전에 알려 드릴게요.")
                        .font(.pretendard(size: 12.5)).foregroundStyle(Color(hex: 0xFF9A3412)).lineSpacing(2)
                }
                .padding(.horizontal, 14).padding(.vertical, 11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(hex: 0xFFFFF4E8), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Color(hex: 0xFFFED7AA), lineWidth: 1))
                .padding(.top, 10)
                .transition(.opacity.combined(with: .offset(y: -6)))
            }

            HStack(spacing: 8) {
                ForEach(OB.presets, id: \.0) { amt, label in
                    chip(label, on: !custom && value == amt) { pick(amt) }.frame(height: 48)
                }
            }
            .padding(.top, 14)
            let customOn = custom || (value > 0 && !OB.presets.contains { $0.0 == value })
            chip("직접 입력", on: customOn, icon: "pencil", fg: OB.teal) {
                custom = true; focused = true
            }
            .frame(height: 40)
            .padding(.top, 8)
        }
        .animation(.easeOut(duration: 0.3), value: warn)
        .onAppear { text = value > 0 ? obWon(value) : "" }
        .onChange(of: warn) { _, w in
            guard w else { glow = false; return }
            // 한 번 흔들림 + 테두리 깜빡임
            Task { @MainActor in
                for x in [-6.0, 5, -3, 2, 0] as [CGFloat] {
                    withAnimation(.easeInOut(duration: 0.084)) { shake = x }
                    try? await Task.sleep(nanoseconds: 84_000_000)
                }
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { glow = true }
            }
        }
    }

    /// 금액 버튼 — 숫자가 이전 금액에서 새 금액까지 0.45초 동안 굴러간다(끝에서 감속).
    private func pick(_ target: Int64) {
        focused = false
        custom = false
        let from = Double(value)
        let to = Double(target)
        budget = target
        rollTask?.cancel()
        rollTask = Task { @MainActor in
            let start = Date()
            while !Task.isCancelled {
                let p = min(1, Date().timeIntervalSince(start) / 0.45)
                let e = 1 - pow(1 - p, 3)
                let v = Int64((from + (to - from) * e) / 100) * 100
                text = p < 1 ? obWon(v) : obWon(target)
                if p >= 1 { break }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
        }
    }

    private func chip(_ label: String, on: Bool, icon: String? = nil, fg: Color = OB.ink, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Image(systemName: icon).font(.system(size: 12, weight: .semibold)).foregroundStyle(OB.teal) }
                Text(label).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(on ? OB.teal : fg)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(on ? OB.tealTint : OB.ground, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(on ? OB.tealBright : .clear, lineWidth: 2))
            .contentShape(Rectangle())
            .animation(.easeOut(duration: 0.18), value: on)
        }
        .buttonStyle(.plain)
    }
}

// ── ④ HoYoLAB ─────────────────────────────────────────────────────────────

private struct HoyolabPage: View {
    var store: SpendingStore
    let games: Set<String>
    let onNext: () -> Void

    @State private var showEmailGuide = false
    @State private var showLogin = false
    @State private var working = false

    private var linked: Bool { store.hoyolabConfig.isLinked }
    private var picked: String {
        OB.games.filter { games.contains($0.key) && OB.hoyoKeys.contains($0.key) }.map { $0.shortName }.joined(separator: " · ")
    }

    private let perks: [(String, String, String)] = [
        ("calendar.badge.checkmark", "자동 출석", "3게임 출석 보상을 매일"),
        ("clock", "실시간 재화", "가득 차기 전에 알림"),
        ("checklist", "숙제 현황", "일일 · 주간 · 파견"),
        ("chart.bar", "재화 수입", "이번 달과 지난달 비교"),
        ("ticket", "리딤 코드", "눌러서 바로 교환"),
        ("trophy", "클리어 · 캐릭터", "편성과 육성 현황"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            StepBody { h in
                PageTitle(title: "HoYoLAB을 연결할까요?", sub: "비밀번호는 저장하지 않아요. 언제든 연결을 끊을 수 있어요.")
                // 히어로
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("연결하면").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(Color(hex: 0xFF8FE3DA))
                        Text("매일 할 일 6가지를\n앱이 대신 챙겨요").font(.pretendard(size: 18, weight: .bold)).foregroundStyle(.white).lineSpacing(4)
                    }
                    Spacer()
                    HStack(spacing: -10) {
                        ForEach(Array(obTiles.prefix(3).enumerated()), id: \.offset) { i, t in
                            Image(t.asset).resizable().scaledToFit().frame(width: i == 0 ? 24 : 26, height: i == 0 ? 24 : 26)
                                .frame(width: 36, height: 36)
                                .background(LinearGradient(colors: [t.from, t.to], startPoint: .topLeading, endPoint: .bottomTrailing),
                                            in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                                .padding(2)
                                .background(Color(hex: 0xFF1A2847), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .zIndex(Double(3 - i))
                        }
                    }
                }
                .padding(.horizontal, 20).padding(.vertical, h < 630 ? 12 : 18)
                .background(LinearGradient(colors: [OB.ink, Color(hex: 0xFF23345C)], startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .padding(.top, h < 630 ? 14 : 20)
                // 화면이 낮으면 3열 · 설명 줄 생략(스크롤 없이 한 화면에, 9/29).
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: h < 630 ? 3 : 2), spacing: 8) {
                    ForEach(Array(perks.enumerated()), id: \.offset) { _, p in
                        VStack(alignment: .leading, spacing: h < 630 ? 8 : 10) {
                            Image(systemName: p.0).font(.system(size: 16, weight: .semibold)).foregroundStyle(OB.teal)
                                .frame(width: 34, height: 34)
                                .background(Color.white, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                                .shadow(color: OB.ink.opacity(0.06), radius: 1.5, y: 1)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(p.1).font(.pretendard(size: h < 630 ? 13 : 14, weight: .bold)).foregroundStyle(OB.ink)
                                    .lineLimit(1).minimumScaleFactor(0.8)
                                if h >= 630 { Text(p.2).font(.pretendard(size: 12)).foregroundStyle(OB.sub) }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(h < 630 ? 10 : 14)
                        .background(OB.ground, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                }
                .padding(.top, 12)
                if linked {
                    Text("연결됐어요 · \(picked.isEmpty ? "게임" : picked) UID 를 채웠어요")
                        .font(.pretendard(size: 13, weight: .bold)).foregroundStyle(OB.teal)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14).padding(.vertical, 12)
                        .background(OB.tealTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .padding(.top, 8)
                        .transition(.opacity.combined(with: .offset(y: 8)))
                }
            }
            if linked {
                CtaButton(title: "다음", primary: true, action: onNext)
                CtaButton(title: "연결 해제", primary: false, bg: Color(hex: 0xFFECEFF4), fg: OB.ink) {
                    _ = store.updateHoyolabConfig(HoyolabConfig(ltuid: "", ltoken: "", genshinUid: "", hsrUid: "", zzzUid: "", cookieToken: "", webCookie: ""))
                }
            } else {
                CtaButton(title: working ? "UID 확인 중…" : "HoYoLAB 로그인", primary: true, enabled: !working, bg: OB.ink) { showEmailGuide = true }
                CtaButton(title: "나중에 연결할게요", primary: false, bg: Color(hex: 0xFFECEFF4), fg: OB.ink, action: onNext)
            }
            Spacer().frame(height: 16)
        }
        .animation(.easeOut(duration: 0.3), value: linked)
        // 설정 ▸ HoYoLAB 연동과 같은 안내 · 같은 로그인 창
        .alert("이메일 로그인 필수", isPresented: $showEmailGuide) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("이메일로 로그인") { showLogin = true }.glgAlertTint()
        } message: {
            Text("토큰을 정상적으로 가져오려면 다음 화면에서 반드시 '이메일(비밀번호) 로그인'을 사용하세요.\n\n구글·애플 등 소셜 로그인은 cookie_token 등 일부 정보를 가져오지 못해 리딤코드 교환이 안 될 수 있어요.")
        }
        .sheet(isPresented: $showLogin) {
            NavigationStack {
                HoyolabLoginWebView { u, t, c, raw in
                    showLogin = false
                    working = true
                    Task { @MainActor in
                        let uids = (try? await HoyolabApi.shared.fetchGameUids(ltuid: u, ltoken: t)) ?? [:]
                        _ = store.updateHoyolabConfig(HoyolabConfig(
                            ltuid: u, ltoken: t,
                            genshinUid: uids["genshin"] ?? "", hsrUid: uids["hsr"] ?? "", zzzUid: uids["zzz"] ?? "",
                            cookieToken: c, webCookie: raw
                        ))
                        working = false
                    }
                }
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle("HoYoLAB 로그인")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { GLGSheetCloseButton { showLogin = false } } }
            }
        }
    }
}
