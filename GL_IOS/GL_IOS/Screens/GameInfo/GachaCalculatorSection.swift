import SwiftUI
import Shared

// 계산기 2.0 — B 대시보드 레이아웃. 탭 제거, 입력→확률→재화→시나리오 위젯 세로 나열.
// 게임/배너 칩은 커스텀 글래스 글로우 칩(S4). 시뮬·플래너는 시트로 진입.
struct GachaCalculatorSection: View {
    @Environment(\.glgAccent) private var accent
    @State private var gameKey = "genshin"
    @State private var bannerType = "character"
    @State private var currency = ""
    @State private var pityStr = ""
    @State private var guaranteed = false
    @State private var qty = 1
    @State private var sheet: CalcSheet? = nil

    enum CalcSheet: Int, Identifiable { case sim, plan; var id: Int { rawValue } }

    private var game: GachaGameRate { GachaRateData.shared.byKey(key: gameKey) ?? GachaRateData.shared.games[0] }
    private var banner: GachaBannerRate { game.banner(type: bannerType) ?? game.character ?? game.standard ?? game.games_firstBanner }

    // 파생 계산 묶음 (CalcResult 는 파일 스코프 — 서브뷰에 값으로 전달)
    private var calc: CalcResult {
        let cur = Int(currency) ?? 0
        let perPull = Int(banner.perPull)
        let pity = min(max(Int(pityStr) ?? 0, 0), Int(banner.hardPity) - 1)
        let possible = perPull > 0 ? cur / perPull : 0
        let pullsToHard = max(Int(banner.hardPity) - pity, 0)
        let currencyToHard = pullsToHard * perPull
        let additionalNeeded = max(currencyToHard - cur, 0)
        let additionalPulls = perPull > 0 ? Int(ceil(Double(additionalNeeded) / Double(perPull))) : 0
        let estCost = Int64(additionalPulls) * Int64(banner.wonPerPull)
        let p = GachaRateData.shared.pickupProb(n: Int32(possible), startPity: Int32(pity), b: banner, guaranteed: guaranteed)
        return CalcResult(cur: cur, pity: pity, possible: possible, pullsToHard: pullsToHard, currencyToHard: currencyToHard,
                          additionalNeeded: additionalNeeded, prob: Int((p * 100).rounded()), estCost: estCost)
    }

    var body: some View {
        stack
            .onChange(of: gameKey) { if game.banner(type: bannerType) == nil { bannerType = "character" } }
            .sheet(item: $sheet) { s in
                NavigationStack {
                    ScrollView {
                        Group {
                            if s == .sim { Simulator(game: game, banner: banner) } else { Planner(game: game, banner: banner) }
                        }.padding(16)
                    }
                    .navigationTitle(s == .sim ? "뽑기 시뮬" : "목표 플래너")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("닫기") { sheet = nil } } }
                }
                .presentationDetents([.large])
            }
    }

    // 거대 단일 body 가 게임 탭마다 제네릭 메타데이터를 재해석하던 문제 → 위젯을 독립 View 구조체로 분리해
    // 타입을 잘게 쪼개고(메타데이터 특수화·캐시), 변경된 서브뷰만 갱신되게 함.
    private var stack: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 페이지 제목은 네비게이션 바(sectionPage)에서 표시 — 인라인 중복 제거
            // 컨텍스트: 게임 + 배너 (커스텀 글래스 글로우 칩 — 경량 솔리드, 실시간 블러 없음)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GachaRateData.shared.games, id: \.key) { g in
                        GlowChip(label: g.shortName, glow: Color(argb64: g.color), selected: g.key == gameKey, enabled: true) { gameKey = g.key }
                    }
                }
                .padding(8) // 글로우 그림자가 ScrollView 상하·좌우 경계에 잘리지 않도록 여백 확보
            }
            HStack(spacing: 8) {
                ForEach(Array(GachaRateData.shared.bannerTypes.enumerated()), id: \.offset) { _, pair in
                    let key = (pair.first as? String) ?? ""
                    let label = (pair.second as? String) ?? ""
                    GlowChip(label: label, glow: accent.primary, selected: key == bannerType, enabled: game.banner(type: key) != nil) { bannerType = key }
                }
            }
            .padding(.horizontal, 8) // 게임 칩 행(ScrollView 내부 padding 8)과 좌측 시작점 정렬
            .padding(.vertical, 6)

            InputCard(banner: banner, currency: $currency, pityStr: $pityStr, guaranteed: $guaranteed, qty: $qty).padding(.top, 13)
            ResultsCard(c: calc, banner: banner, qty: qty, guaranteed: guaranteed).padding(.top, 13)
            ToolsRow(onSim: { sheet = .sim }, onPlan: { sheet = .plan }).padding(.top, 13)
        }
    }
}

// 파생 계산 결과 — 서브뷰에 값으로 전달 (부모 body 제네릭 타입 축소용 파일 스코프 분리)
private struct CalcResult { let cur, pity, possible, pullsToHard, currencyToHard, additionalNeeded, prob: Int; let estCost: Int64 }

// S4 커스텀 글래스 글로우 칩 — 반투명 고스트, 선택 시 컬러 보더+글로우. (실시간 블러 없는 경량 솔리드)
private struct GlowChip: View {
    let label: String
    let glow: Color
    let selected: Bool
    let enabled: Bool
    let action: () -> Void
    var body: some View {
        Button(action: { if enabled { action() } }) {
            HStack(spacing: 6) {
                Circle().fill(enabled ? glow : Color(.systemGray3)).frame(width: 7, height: 7)
                Text(label).font(.system(size: 12.5, weight: .bold))
                    .foregroundStyle(selected ? glow : (enabled ? GLGColor.textSecondary : Color(.systemGray3)))
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(selected ? glow.opacity(0.12) : Color.white.opacity(0.4), in: Capsule())
            .overlay(Capsule().stroke(selected ? glow : Color.white.opacity(0.6), lineWidth: selected ? 1.5 : 1))
            .shadow(color: selected ? glow.opacity(0.35) : .clear, radius: selected ? 6 : 0, y: selected ? 2 : 0)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

// ── 입력 카드 ──
private struct InputCard: View {
    let banner: GachaBannerRate
    @Binding var currency: String
    @Binding var pityStr: String
    @Binding var guaranteed: Bool
    @Binding var qty: Int
    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 8) {
                    NumField(label: "보유 \(banner.currency)", placeholder: "0", text: $currency).frame(maxWidth: .infinity)
                    NumField(label: "현재 천장", placeholder: "0", text: $pityStr).frame(maxWidth: .infinity)
                }
                if banner.has5050 && !banner.no5050 {
                    CalcToggleRow(label: "확정(픽업 보장) 보유", isOn: $guaranteed).padding(.top, 10)
                }
                QtyRow(qty: $qty).padding(.top, 8)
            }
        }
    }
}

// ── 결과 카드 (확률·재화·시나리오 통합) ──
private struct ResultsCard: View {
    let c: CalcResult
    let banner: GachaBannerRate
    let qty: Int
    let guaranteed: Bool

    var body: some View {
        let probColor = c.prob >= 70 ? okGreen : (c.prob >= 40 ? warnAmber : badRed)
        let scn = scenario()
        let perPull = Int(banner.perPull)
        return GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                head("🎯 확보 확률")
                if c.cur > 0 {
                    (Text("보유분 \(c.possible)회로 ").foregroundStyle(GLGColor.textPrimary)
                        + Text("\(c.prob)%").bold().foregroundStyle(probColor)
                        + Text(" 확보 가능").foregroundStyle(GLGColor.textPrimary))
                        .font(.system(size: 14, weight: .semibold)).padding(.top, 4)
                    ProgressView(value: Double(c.prob) / 100).tint(probColor).padding(.top, 11)
                } else {
                    Text("재화를 입력하면 확보 확률을 계산해요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4)
                }
                divider
                head("💎 필요 재화")
                HStack(spacing: 8) {
                    ResultBox(label: "하드 천장까지", value: "\(c.pullsToHard)회", sub: "\(num(c.currencyToHard)) \(banner.currency)")
                    ResultBox(label: "부족분", value: c.additionalNeeded > 0 ? num(c.additionalNeeded) : "0",
                              sub: c.additionalNeeded > 0 ? "\(won(c.estCost)) 충전" : "충전 불필요")
                }
                divider
                head("📊 시나리오 (\(qty)개 기준)")
                HStack(spacing: 8) {
                    ScenarioBox(title: "최선", sub: scn.bestSub, pulls: "\(scn.bestPulls)회", currency: "≈ \(num(scn.bestPulls * perPull)) \(banner.currency)", color: okGreen)
                    ScenarioBox(title: "최악", sub: scn.worstSub, pulls: "\(scn.worstPulls)회", currency: "≈ \(num(scn.worstPulls * perPull)) \(banner.currency)", color: badRed)
                }
            }
        }
    }

    private func head(_ t: String) -> some View {
        Text(t).font(.system(size: 13, weight: .bold)).frame(maxWidth: .infinity, alignment: .leading)
    }
    private var divider: some View {
        Divider().overlay(Color.black.opacity(0.06)).padding(.vertical, 14)
    }
    private func scenario() -> (bestPulls: Int, worstPulls: Int, bestSub: String, worstSub: String) {
        let hp = Int(banner.hardPity), sp = Int(banner.softPity), pityVal = c.pity
        if banner.no5050 || !banner.has5050 {
            return (Int((Double(sp) * 0.7).rounded()) * qty, hp * qty, "조기 획득", "천장 도달")
        }
        let avgSingle = Int((Double(hp) * 0.83).rounded())
        let bestSingle = guaranteed ? max(1, avgSingle - pityVal) : max(1, Int((Double(avgSingle) * 0.6).rounded()) - pityVal)
        let worstSingle = guaranteed ? hp - pityVal : (hp - pityVal) + hp
        return (max(1, bestSingle) * qty, max(1, worstSingle) * qty,
                guaranteed ? "보장 + 빠른 획득" : "50/50 성공", "50/50 실패 → 천장")
    }
}

// ── 보조 도구 진입 (솔리드 타일) ──
private struct ToolsRow: View {
    let onSim: () -> Void
    let onPlan: () -> Void
    var body: some View {
        HStack(spacing: 8) {
            tile("🎲", "뽑기 시뮬", onSim)
            tile("🗓️", "목표 플래너", onPlan)
        }
    }
    private func tile(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Text(icon).font(.system(size: 22))
                Text(label).font(.system(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 14)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Color.black.opacity(0.08), lineWidth: 1))
        }.buttonStyle(.plain)
    }
}

private extension GachaGameRate {
    /// character/standard 모두 nil 인 비정상 케이스 폴백(컴파일 안전용).
    var games_firstBanner: GachaBannerRate { character ?? standard ?? weapon! }
}

// 색
private let okGreen = Color(hex: 0xFF16A34A)
private let warnAmber = Color(hex: 0xFFD97706)
private let badRed = Color(hex: 0xFFDC2626)
private let gold5 = Color(hex: 0xFFE0A93B)
private let purple4 = Color(hex: 0xFF9B59B6)
private let gray3 = Color(hex: 0xFFB6B9C0)

// 천장 티어 — 시뮬레이터 진행도 색/라벨 판단용. (천장 카운터(WishPitySection) 제거 후 이관)
enum PityTierS { case safe, caution, imminent, reached }
func pityTier(count: Int, soft: Int, hard: Int) -> PityTierS {
    if count >= hard { return .reached }
    if count >= soft { return .imminent }
    if count >= soft - 10 { return .caution }
    return .safe
}

// ── 시뮬레이터 ──
private struct Simulator: View {
    let game: GachaGameRate; let banner: GachaBannerRate
    @Environment(\.glgAccent) private var accent
    @State private var pity5 = 0
    @State private var pity4 = 0
    @State private var guaranteed = false
    @State private var total = 0
    @State private var fiveCount = 0
    @State private var pickupCount = 0
    @State private var fourCount = 0
    @State private var lastBatch: [PullResult] = []

    struct PullResult: Identifiable { let id = UUID(); let tier: Int; let pickup: Bool }

    var body: some View {
        let hp = Int(banner.hardPity)
        let tier = pityTier(count: pity5, soft: Int(banner.softPity), hard: hp)
        let pityColor: Color = tier == .reached ? badRed : (tier == .imminent ? warnAmber : (tier == .caution ? gold5 : accent.primary))
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("천장 \(pity5) / \(hp)").font(.system(size: 13, weight: .bold))
                Spacer()
                if banner.has5050 && !banner.no5050 {
                    Text(guaranteed ? "다음 5★ 픽업 확정" : "50/50").font(.system(size: 10, weight: .bold))
                        .foregroundStyle(guaranteed ? okGreen : GLGColor.textSecondary)
                        .padding(.horizontal, 7).padding(.vertical, 3)
                        .background((guaranteed ? okGreen : GLGColor.textSecondary).opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                }
            }
            ProgressView(value: min(max(Double(pity5)/Double(hp), 0), 1)).tint(pityColor).padding(.top, 6)
            if !lastBatch.isEmpty {
                LazyVGrid(columns: Array(repeating: GridItem(.adaptive(minimum: 38), spacing: 6), count: 1), alignment: .leading, spacing: 6) {
                    ForEach(lastBatch) { resultChip($0) }
                }
                .padding(.top, 14)
            }
            HStack(spacing: 8) {
                pullButton("1회 뽑기") { pull(1) }
                pullButton("10연차") { pull(10) }
            }
            .padding(.top, 14)
            let avgPer = fiveCount > 0 ? fixed(Double(total)/Double(fiveCount), 1) : "—"
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    ResultBox(label: "총 뽑기", value: "\(total)회", sub: "≈ \(num(total * Int(banner.perPull))) \(banner.currency)")
                    ResultBox(label: "5★ 획득", value: "\(fiveCount)개", sub: banner.has5050 && !banner.no5050 ? "픽업 \(pickupCount) · 픽뚫 \(fiveCount - pickupCount)" : "픽업 \(pickupCount)")
                }
                HStack(spacing: 8) {
                    ResultBox(label: "4★ 획득", value: "\(fourCount)개", sub: "")
                    ResultBox(label: "평균 천장", value: avgPer == "—" ? "—" : "\(avgPer)회", sub: "5★ 1개당")
                }
                ResultBox(label: "누적 추정 비용", value: won(Int64(total) * Int64(banner.wonPerPull)), sub: "현금 충전 환산").frame(maxWidth: .infinity)
            }
            .padding(.top, 14)
            Button(action: reset) {
                Text("초기화").font(.system(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    .frame(maxWidth: .infinity).padding(.vertical, 11).background(Color(hex: 0xFFF2F2F6), in: RoundedRectangle(cornerRadius: 12))
            }.buttonStyle(.plain).padding(.top, 12)
            Text("실제 확률·소프트/하드 천장 기반 시뮬레이션이에요. 결과는 체험용이며 실제 뽑기와 무관해요.")
                .font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4)
        }
        .onChange(of: game.key) { reset() }
    }

    private func rollOnce() -> PullResult {
        let p5 = GachaRateData.shared.rateAt(pity: Int32(pity5), b: banner)
        if Double.random(in: 0..<1) < p5 {
            let pickup: Bool
            if banner.no5050 || !banner.has5050 { pickup = true }
            else if guaranteed { guaranteed = false; pickup = true }
            else if Double.random(in: 0..<1) < 0.5 { pickup = true }
            else { guaranteed = true; pickup = false }
            pity5 = 0; pity4 = 0; fiveCount += 1; if pickup { pickupCount += 1 }
            return PullResult(tier: 5, pickup: pickup)
        }
        pity5 += 1; pity4 += 1
        if pity4 >= 10 || Double.random(in: 0..<1) < 0.051 { pity4 = 0; fourCount += 1; return PullResult(tier: 4, pickup: false) }
        return PullResult(tier: 3, pickup: false)
    }
    private func pull(_ n: Int) {
        var r: [PullResult] = []; for _ in 0..<n { r.append(rollOnce()) }
        total += n; lastBatch = r
    }
    private func reset() {
        pity5 = 0; pity4 = 0; guaranteed = false; total = 0; fiveCount = 0; pickupCount = 0; fourCount = 0; lastBatch = []
    }
    private func resultChip(_ r: PullResult) -> some View {
        let color = r.tier == 5 ? gold5 : (r.tier == 4 ? purple4 : gray3)
        return VStack(spacing: 0) {
            Text("\(r.tier)★").font(.system(size: r.tier >= 4 ? 13 : 11, weight: .bold)).foregroundStyle(r.tier == 3 ? GLGColor.textSecondary : color)
            if r.tier == 5 { Text(r.pickup ? "픽업" : "픽뚫").font(.system(size: 7, weight: .bold)).foregroundStyle(r.pickup ? okGreen : badRed) }
        }
        .frame(width: r.tier >= 4 ? 40 : 34, height: r.tier >= 4 ? 40 : 34)
        .background(color.opacity(r.tier == 3 ? 0.18 : 0.16), in: RoundedRectangle(cornerRadius: 10))
    }
    private func pullButton(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                .frame(maxWidth: .infinity).padding(.vertical, 13).background(accent.primary, in: RoundedRectangle(cornerRadius: 12))
        }.buttonStyle(.plain)
    }
}

// ── 플래너 ──
private struct Planner: View {
    let game: GachaGameRate; let banner: GachaBannerRate
    @Environment(\.glgAccent) private var accent
    @State private var date: Date? = nil
    @State private var showPicker = false
    @State private var currentPulls = ""
    @State private var passOn = false
    @State private var qty = 1

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { showPicker = true } label: {
                HStack {
                    Text(date.map { DateUtil.shared.label(millis: Int64($0.timeIntervalSince1970 * 1000)) } ?? "목표 날짜 선택")
                        .foregroundStyle(date == nil ? Color(.placeholderText) : GLGColor.textPrimary)
                    Spacer()
                    Image(systemName: "calendar").foregroundStyle(accent.primary)
                }
                .font(.system(size: 14)).padding(12)
                .background(Color(hex: 0xFFF2F2F6), in: RoundedRectangle(cornerRadius: 10))
            }.buttonStyle(.plain)
            NumField(label: "현재 보유 뽑기 수", placeholder: "0", text: $currentPulls).padding(.top, 10)
            if game.pass != nil {
                CalcToggleRow(label: "\(game.pass?.name ?? "패스") 적용", isOn: $passOn).padding(.top, 10)
            }
            QtyRow(qty: $qty).padding(.top, 8)
            if let d = date {
                plannerResult(d)
            } else {
                Text("목표 날짜를 선택하면 무료 재화로 모을 수 있는 뽑기 수와 달성 가능 여부를 계산해요.")
                    .font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 14)
            }
        }
        .sheet(isPresented: $showPicker) {
            NavigationStack {
                DatePicker("목표 날짜", selection: Binding(get: { date ?? Date() }, set: { date = $0 }), displayedComponents: .date)
                    .datePickerStyle(.graphical).padding()
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("확인") { showPicker = false } } }
            }
            .presentationDetents([.medium])
        }
    }

    @ViewBuilder private func plannerResult(_ d: Date) -> some View {
        let days = Int(DateUtil.shared.daysBetween(fromMillis: nowMs(), toMillis: Int64(d.timeIntervalSince1970 * 1000)))
        let weeks = days / 7
        let dailyPerDay = Double(game.dailyFree) / Double(banner.perPull)
        let weeklyPerWeek = Double(game.weeklyFree) / Double(banner.perPull)
        let passPerDay = (passOn && game.pass != nil) ? Double(game.pass!.dailyCrystal) / Double(banner.perPull) : 0
        let freePulls = Int(Double(days) * (dailyPerDay + passPerDay) + Double(weeks) * weeklyPerWeek)
        let totalAvailable = (Int(currentPulls) ?? 0) + freePulls
        let totalNeeded = Int(banner.hardPity) * qty
        let (msg, color): (String, Color) = totalAvailable >= totalNeeded ? ("확보 가능 — 여유 \(totalAvailable - totalNeeded)회", okGreen)
            : (Double(totalAvailable) >= Double(totalNeeded) * 0.7 ? ("뽑기 부족 — \(totalNeeded - totalAvailable)회 모자람", warnAmber)
               : ("달성 불가 — \(totalNeeded - totalAvailable)회 부족", badRed))
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ResultBox(label: "남은 일수", value: "\(days)일", sub: "주 \(weeks)회 보너스")
                ResultBox(label: "무료 확보 뽑기", value: "\(freePulls)회", sub: "데일리+주간 누적")
            }
            ResultBox(label: "필요 뽑기 (\(qty)개·천장 기준)", value: "\(totalNeeded)회", sub: "보유+무료 \(totalAvailable)회").frame(maxWidth: .infinity)
        }
        .padding(.top, 14)
        Text(msg).font(.system(size: 13, weight: .bold)).foregroundStyle(color)
            .frame(maxWidth: .infinity, alignment: .leading).padding(14)
            .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 12)).padding(.top, 12)
    }
}

// ── 공용 작은 컴포넌트 ──
private struct NumField: View {
    let label: String; let placeholder: String; @Binding var text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            TextField(placeholder, text: $text).keyboardType(.numberPad).textFieldStyle(.plain).glgPillField()
                .onChange(of: text) { _, newValue in text = newValue.filter(\.isNumber) }
        }
    }
}

private struct CalcToggleRow: View {
    let label: String; @Binding var isOn: Bool
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Toggle(isOn: $isOn) { Text(label).font(.system(size: 13)) }.tint(accent.primary)
    }
}

private struct QtyRow: View {
    @Binding var qty: Int
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack(spacing: 10) {
            Text("목표 개수").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
            HStack(spacing: 6) {
                ForEach(1...3, id: \.self) { q in
                    Button { qty = q } label: {
                        Text("\(q)").font(.system(size: 13, weight: .bold)).foregroundStyle(q == qty ? .white : GLGColor.textSecondary)
                            .frame(width: 34, height: 34).background(q == qty ? accent.primary : Color(hex: 0xFFF2F2F6), in: Circle())
                    }.buttonStyle(.plain)
                }
            }
        }
    }
}

private struct ResultBox: View {
    let label: String; let value: String; let sub: String
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.system(size: 10, weight: .semibold)).foregroundStyle(Color.black.opacity(0.35))
            Text(value).font(.system(size: 17, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
            if !sub.isEmpty { Text(sub).font(.system(size: 10)).foregroundStyle(Color.black.opacity(0.35)) }
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 12).padding(.vertical, 11)
        .background(Color.black.opacity(0.03), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct ScenarioBox: View {
    let title: String; let sub: String; let pulls: String; let currency: String; let color: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.system(size: 11, weight: .bold)).foregroundStyle(color)
            Text(sub).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            Text(pulls).font(.system(size: 16, weight: .bold)).padding(.top, 6)
            Text(currency).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 12).padding(.vertical, 11)
        .background(color.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
    }
}
