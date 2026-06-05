import SwiftUI
import ComposeApp

// 통합 계산기 — 재화 환산·확보 확률·뽑기 시뮬레이터·플래너. (Compose GachaCalculatorSection 대응)
struct GachaCalculatorSection: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var gameKey = "genshin"
    @State private var bannerType = "character"
    @State private var tool = "calc"

    private var game: GachaGameRate { GachaRateData.shared.byKey(key: gameKey) ?? GachaRateData.shared.games[0] }
    private var banner: GachaBannerRate { game.banner(type: bannerType) ?? game.character ?? game.standard ?? game.games_firstBanner }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("통합 계산기").font(.system(size: 16, weight: .bold)).padding(.bottom, 12)
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    gameSelector
                    bannerTypeRow.padding(.top, 12)
                    toolTabs.padding(.top, 12)
                    Group {
                        switch tool {
                        case "calc": CurrencyCalc(store: store, game: game, banner: banner)
                        case "prob": ProbCalc(store: store, game: game, banner: banner)
                        case "sim": Simulator(game: game, banner: banner)
                        default: Planner(game: game, banner: banner)
                        }
                    }
                    .padding(.top, 16)
                }
            }
        }
        .onChange(of: gameKey) { _ in if game.banner(type: bannerType) == nil { bannerType = "character" } }
    }

    private var gameSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(GachaRateData.shared.games, id: \.key) { g in
                    let sel = g.key == gameKey
                    Button { gameKey = g.key } label: {
                        HStack(spacing: 6) {
                            Circle().fill(sel ? Color.white.opacity(0.8) : Color(argb64: g.color)).frame(width: 7, height: 7)
                            Text(g.shortName).font(.system(size: 12, weight: .bold)).foregroundStyle(sel ? .white : GLGColor.textSecondary)
                        }
                        .padding(.horizontal, 13).padding(.vertical, 7)
                        .background(sel ? Color(argb64: g.color) : Color(hex: 0xFFF2F2F6), in: Capsule())
                    }.buttonStyle(.plain)
                }
            }
        }
    }

    private var bannerTypeRow: some View {
        HStack(spacing: 8) {
            ForEach(Array(GachaRateData.shared.bannerTypes.enumerated()), id: \.offset) { _, pair in
                let key = (pair.first as? String) ?? ""
                let label = (pair.second as? String) ?? ""
                let available = game.banner(type: key) != nil
                let sel = key == bannerType
                Button { if available { bannerType = key } } label: {
                    Text(label).font(.system(size: 12, weight: .bold))
                        .foregroundStyle(sel ? .white : (available ? GLGColor.textSecondary : Color(.systemGray3)))
                        .padding(.horizontal, 16).padding(.vertical, 7)
                        .background(sel ? accent.primary : Color(hex: 0xFFF2F2F6), in: RoundedRectangle(cornerRadius: 10))
                }.buttonStyle(.plain).disabled(!available)
            }
        }
    }

    private var toolTabs: some View {
        HStack(spacing: 3) {
            ForEach([("calc","환산"),("prob","확률"),("sim","시뮬"),("plan","플래너")], id: \.0) { key, label in
                let sel = key == tool
                Button { tool = key } label: {
                    Text(label).font(.system(size: 12, weight: .bold)).foregroundStyle(sel ? .white : GLGColor.textSecondary)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                        .background(sel ? accent.primary : Color.clear, in: RoundedRectangle(cornerRadius: 9))
                }.buttonStyle(.plain)
            }
        }
        .padding(3).background(Color(hex: 0xFFF2F2F6), in: RoundedRectangle(cornerRadius: 12))
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

// ── 재화 환산 ──
private struct CurrencyCalc: View {
    @ObservedObject var store: SpendingStore
    let game: GachaGameRate; let banner: GachaBannerRate
    @Environment(\.glgAccent) private var accent
    @State private var mode = "calc"
    @State private var qty = 1
    @State private var currency = ""
    @State private var pityStr = ""
    @State private var guaranteed = false
    @State private var targetPulls = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                pill("보유 → 뽑기", mode == "calc") { mode = "calc" }
                pill("목표 → 재화", mode == "reverse") { mode = "reverse" }
            }
            .padding(.bottom, 14)
            if mode == "reverse" {
                let tp = Int(targetPulls) ?? 0
                numField("목표 뽑기 수", "예: 90", $targetPulls)
                HStack(spacing: 8) {
                    resultBox("필요 재화", "\(num(tp * Int(banner.perPull))) \(banner.currency)", "목표 \(tp)회")
                    resultBox("추정 비용", won(Int64(tp) * Int64(banner.wonPerPull)), "현금 충전 기준")
                }
                .padding(.top, 14)
            } else {
                calcMode
            }
        }
        .onAppear { pityStr = "\(Int(store.pity[game.key]?.count ?? 0))"; guaranteed = store.pity[game.key]?.guaranteed ?? false }
    }

    @ViewBuilder private var calcMode: some View {
        let cur = Int(currency) ?? 0
        let pityVal = min(max(Int(pityStr) ?? 0, 0), Int(banner.hardPity) - 1)
        let possiblePulls = banner.perPull > 0 ? cur / Int(banner.perPull) : 0
        let leftCurrency = cur - possiblePulls * Int(banner.perPull)
        let pullsToHard = max(Int(banner.hardPity) - pityVal, 0)
        let currencyToHard = pullsToHard * Int(banner.perPull)
        let additionalNeeded = max(currencyToHard - cur, 0)
        let additionalPulls = banner.perPull > 0 ? Int(ceil(Double(additionalNeeded) / Double(banner.perPull))) : 0
        let estCost = Int64(additionalPulls) * Int64(banner.wonPerPull)
        let pct = currencyToHard > 0 ? min(max(Int((Double(cur)/Double(currencyToHard)*100).rounded()), 0), 100) : 0

        VStack(alignment: .leading, spacing: 0) {
            numField("보유 \(banner.currency)", "0", $currency)
            numField("현재 천장 (천장 카운터 연동)", "0", $pityStr).padding(.top, 10)
            if banner.has5050 && !banner.no5050 {
                toggleRow("확정(픽업 보장) 보유", $guaranteed).padding(.top, 10)
            }
            qtyRow.padding(.top, 8)

            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    resultBox("가능 뽑기 수", "\(possiblePulls)회", cur > 0 ? "남은 \(num(leftCurrency)) \(banner.currency)" : "")
                    resultBox("하드 천장까지", "\(pullsToHard)회", additionalNeeded > 0 ? "추가 \(num(additionalNeeded)) 필요" : "재화 충분")
                }
                resultBox("천장까지 추정 비용", won(estCost), additionalPulls > 0 ? "천장까지 \(additionalPulls)회 부족" : "재화 충분", full: true)
            }
            .padding(.top, 14)
            HStack {
                Text("\(num(cur)) / \(num(currencyToHard)) \(banner.currency)").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                Spacer()
                Text("\(pct)%").font(.system(size: 11, weight: .bold)).foregroundStyle(accent.primary)
            }
            .padding(.top, 12)
            ProgressView(value: Double(pct)/100).tint(accent.primary).padding(.top, 5)
            scenario(pityVal: pityVal).padding(.top, 14)
        }
    }

    private func scenario(pityVal: Int) -> some View {
        let noPickup = banner.no5050 || !banner.has5050
        let hp = Int(banner.hardPity), sp = Int(banner.softPity)
        let bestPulls: Int, worstPulls: Int, bestSub: String, worstSub: String
        if noPickup {
            bestSub = "조기 획득"; worstSub = "천장 도달"
            bestPulls = Int((Double(sp) * 0.7).rounded()) * qty
            worstPulls = hp * qty
        } else {
            bestSub = guaranteed ? "보장 + 빠른 획득" : "50/50 성공"
            worstSub = "50/50 실패 → 천장"
            let avgSingle = Int((Double(hp) * 0.83).rounded())
            let bestSingle = guaranteed ? max(1, avgSingle - pityVal) : max(1, Int((Double(avgSingle) * 0.6).rounded()) - pityVal)
            let worstSingle = guaranteed ? hp - pityVal : (hp - pityVal) + hp
            bestPulls = max(1, bestSingle) * qty
            worstPulls = max(1, worstSingle) * qty
        }
        return VStack(alignment: .leading, spacing: 8) {
            Text("시나리오 (\(qty)개 기준)").font(.system(size: 12, weight: .bold))
            HStack(spacing: 8) {
                scenarioBox("최선의 경우", bestSub, "\(bestPulls)회", "≈ \(num(bestPulls * Int(banner.perPull))) \(banner.currency)", okGreen)
                scenarioBox("최악의 경우", worstSub, "\(worstPulls)회", "≈ \(num(worstPulls * Int(banner.perPull))) \(banner.currency)", badRed)
            }
        }
    }

    private var qtyRow: some View { QtyRow(qty: $qty) }
    private func pill(_ l: String, _ sel: Bool, _ a: @escaping () -> Void) -> some View { PillToggle(label: l, selected: sel, action: a) }
    private func numField(_ label: String, _ ph: String, _ text: Binding<String>) -> some View { NumField(label: label, placeholder: ph, text: text) }
    private func toggleRow(_ l: String, _ b: Binding<Bool>) -> some View { CalcToggleRow(label: l, isOn: b) }
    private func resultBox(_ l: String, _ v: String, _ s: String, full: Bool = false) -> some View { ResultBox(label: l, value: v, sub: s).frame(maxWidth: full ? .infinity : nil) }
    private func scenarioBox(_ t: String, _ s: String, _ p: String, _ c: String, _ color: Color) -> some View { ScenarioBox(title: t, sub: s, pulls: p, currency: c, color: color) }
}

// ── 확보 확률 ──
private struct ProbCalc: View {
    @ObservedObject var store: SpendingStore
    let game: GachaGameRate; let banner: GachaBannerRate
    @Environment(\.glgAccent) private var accent
    @State private var nFloat: Double = 0
    @State private var pityStr = ""
    @State private var guaranteed = false

    var body: some View {
        let maxPulls = Double(Int(banner.hardPity) * 2)
        let n = min(max(Int(nFloat), 1), Int(maxPulls))
        let startPity = max(Int(pityStr) ?? 0, 0)
        let prob = GachaRateData.shared.pickupProb(n: Int32(n), startPity: Int32(startPity), b: banner, guaranteed: guaranteed)
        let pct = Int((prob * 100).rounded())
        let color = pct >= 70 ? okGreen : (pct >= 40 ? warnAmber : badRed)
        let label = banner.no5050 || !banner.has5050 ? "5★ 확보 확률 (픽뚫 없음)"
            : (guaranteed ? "픽업 확보 확률 (보장 보유)" : "픽업 확보 확률 (50/50 포함)")
        return VStack(alignment: .leading, spacing: 0) {
            VStack(spacing: 0) {
                Text("\(pct)%").font(.system(size: 48, weight: .bold)).foregroundStyle(color)
                Text(label).font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
            .frame(maxWidth: .infinity)
            ProgressView(value: Double(pct)/100).tint(color).padding(.top, 14)
            HStack {
                Text("뽑기 횟수").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                Spacer()
                Text("\(n)회").font(.system(size: 13, weight: .bold))
            }
            .padding(.top, 16)
            Slider(value: $nFloat, in: 1...maxPulls).tint(accent.primary)
            NumField(label: "현재 천장 (천장 카운터 연동)", placeholder: "0", text: $pityStr).padding(.top, 6)
            if banner.has5050 && !banner.no5050 {
                CalcToggleRow(label: "확정(픽업 보장) 보유", isOn: $guaranteed).padding(.top, 10)
            }
        }
        .onAppear {
            if nFloat == 0 { nFloat = Double(banner.hardPity) }
            pityStr = "\(Int(store.pity[game.key]?.count ?? 0))"
            guaranteed = store.pity[game.key]?.guaranteed ?? false
        }
    }
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
        .onChange(of: game.key) { _ in reset() }
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
private struct PillToggle: View {
    let label: String; let selected: Bool; let action: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Button(action: action) {
            Text(label).font(.system(size: 12, weight: .bold)).foregroundStyle(selected ? .white : GLGColor.textSecondary)
                .frame(maxWidth: .infinity).padding(.vertical, 9)
                .background(selected ? accent.primary : Color(hex: 0xFFF2F2F6), in: RoundedRectangle(cornerRadius: 10))
        }.buttonStyle(.plain)
    }
}

private struct NumField: View {
    let label: String; let placeholder: String; @Binding var text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            TextField(placeholder, text: $text).keyboardType(.numberPad).textFieldStyle(.roundedBorder)
                .onChange(of: text) { text = $0.filter(\.isNumber) }
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
