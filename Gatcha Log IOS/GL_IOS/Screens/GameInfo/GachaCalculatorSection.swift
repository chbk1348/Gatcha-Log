import SwiftUI
import Shared

// 계산기 — "이번 픽업 뽑을 수 있나"에 답하는 화면(B안: 픽업 먼저).
//
// 예전 계산기는 앱이 이미 아는 천장·확정을 다시 물었다(이 뷰는 인자조차 받지 않았다).
// 지금은 앱 기록으로 채운 채 시작하고, 상단은 계산 입력이 아니라 진행 중 픽업과 판정이 차지한다.
//
// ⚠️ 위젯은 반드시 **독립 View 구조체**로 유지한다. computed `some View` 로 부모 body 에 인라인하면
//    전부 하나의 초대형 제네릭 타입이 되어, 상태가 바뀔 때마다 Swift 제네릭 메타데이터를
//    재인스턴스화하며 버벅인다(계산기 2.0 때 Time Profiler 로 확인한 원인).
struct GachaCalculatorSection: View {
    var store: SpendingStore
    /// 가챠 효율 리포트(대시보드) — 가져온 기록의 실제 천장 분포.
    var onOpenDashboard: () -> Void

    @State private var gameKey = "genshin"
    @State private var bannerType = "character"
    /// 사용자가 고친 값(nil = 앱 기록 그대로). 덮어써도 앱 기록은 바뀌지 않는다.
    @State private var pityEdit: String? = nil
    @State private var guaranteedEdit: Bool? = nil
    @State private var heldInput = ""
    @State private var qty = 1
    @State private var includePass = false
    @State private var editing = false

    private var game: GachaGameRate { GachaRateData.shared.byKey(key: gameKey) ?? GachaRateData.shared.games[0] }
    private var banner: GachaBannerRate { game.banner(type: bannerType) ?? game.character ?? game.standard ?? game.calc_firstBanner }

    private var prefill: CalcPrefill {
        GachaCalcContextKt.calcPrefill(
            gameKey: gameKey,
            pity: store.pity,
            held: store.savingsHeld.mapValues { KotlinInt(value: Int32($0)) }
        )
    }
    private var effPity: Int { pityEdit.flatMap { Int($0) } ?? Int(prefill.pity) }
    private var effGuaranteed: Bool { guaranteedEdit ?? prefill.guaranteed }
    private var heldCurrency: Int { Int(heldInput) ?? 0 }

    /// 진행 중 픽업 — 종료 미정이면 남은 일수를 모르니 무료 수급을 셀 수 없어 제외한다.
    private var pickup: GachaBanner? {
        let now = nowMs()
        return store.activeBanners.first {
            !$0.isEndUnknown && $0.type == bannerType && GameData.shared.byNameOrNull(name: $0.game)?.key == gameKey
        }.flatMap { $0.dDay(nowMillis: now) >= 0 ? $0 : nil }
    }
    private var daysLeft: Int { max(0, pickup.map { Int($0.dDay(nowMillis: nowMs())) } ?? 0) }

    private var free: FreeIncome? {
        guard daysLeft > 0 else { return nil }
        return GachaCalcContextKt.freeIncome(game: game, banner: banner, days: Int32(daysLeft), includePass: includePass)
    }
    private var outcome: CalcOutcome {
        GachaCalcContextKt.calcOutcome(
            banner: banner, heldCurrency: Int32(heldCurrency), freeCurrency: free?.total ?? 0,
            pity: Int32(effPity), guaranteed: effGuaranteed, qty: Int32(qty)
        )
    }
    private var quantiles: PullQuantiles {
        GachaCalcContextKt.pullQuantiles(banner: banner, startPity: Int32(effPity), guaranteed: effGuaranteed)
    }

    var body: some View {
        stack
            .onChange(of: gameKey) {
                if game.banner(type: bannerType) == nil { bannerType = "character" }
                resetForGame()
            }
            .onAppear { resetForGame() }
    }

    private func resetForGame() {
        pityEdit = nil
        guaranteedEdit = nil
        includePass = false
        let held = store.savingsHeld[gameKey] ?? 0
        heldInput = held > 0 ? "\(held)" : ""
    }

    private var stack: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 게임 칩 — 좌우 여백 없음. 예전 글로우 칩의 그림자 잘림을 막던 8pt 가
            // 글로우가 사라진 뒤로는 아래 카드들보다 밀리는 어긋남만 남겼다.
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GachaRateData.shared.games, id: \.key) { g in
                        GLGChip(label: g.shortName, selected: g.key == gameKey, color: Color(argb64: g.color)) { gameKey = g.key }
                    }
                }
                .padding(.vertical, 8)
            }

            VerdictHero(
                pickup: pickup,
                gameColor: Color(argb64: game.color),
                bannerLabel: bannerLabel,
                outcome: heldCurrency > 0 ? outcome : nil,
                currency: banner.currency
            )

            SummaryRow(
                pulls: banner.perPull > 0 ? heldCurrency / Int(banner.perPull) : 0,
                hasHeld: heldCurrency > 0,
                pity: effPity,
                hasPityRecord: prefill.hasPityRecord || pityEdit != nil,
                guaranteed: effGuaranteed,
                expanded: editing
            ) { withAnimation(.easeInOut(duration: 0.2)) { editing.toggle() } }
                .padding(.top, 12)

            if editing {
                InputCard(
                    banner: banner, prefill: prefill, currency: banner.currency,
                    heldInput: $heldInput, pityEdit: $pityEdit, guaranteedEdit: $guaranteedEdit, qty: $qty,
                    effGuaranteed: effGuaranteed,
                    onHeldCommit: { store.setHeldCurrency(gameKey: gameKey, value: Int(heldInput) ?? 0) },
                    onOpenSavings: { store.requestSavingsPlanner() }
                )
                .padding(.top, 10)
            }

            if let free {
                FreeIncomeCard(free: free, currency: banner.currency, includePass: $includePass).padding(.top, 12)
            }

            QuantileCard(q: quantiles, banner: banner).padding(.top, 12)

            HStack(spacing: 8) {
                ActionButton(text: "저축 계획", primary: true) { store.requestSavingsPlanner() }
                ActionButton(text: "가챠 기록", primary: false, action: onOpenDashboard)
            }
            .padding(.top, 14)
            .padding(.bottom, 8)
        }
    }

    private var bannerLabel: String {
        GachaRateData.shared.bannerTypes
            .compactMap { $0 as? KotlinPair<NSString, NSString> }
            .first { ($0.first as String?) == bannerType }
            .flatMap { $0.second as String? } ?? "캐릭터"
    }
}

private extension GachaGameRate {
    /// character/standard 모두 nil 인 비정상 케이스 폴백(컴파일 안전용).
    var calc_firstBanner: GachaBannerRate { character ?? standard ?? weapon! }
}

// ── 픽업 히어로 ──
private struct VerdictHero: View {
    let pickup: GachaBanner?
    let gameColor: Color
    let bannerLabel: String
    let outcome: CalcOutcome?
    let currency: String

    var body: some View {
        let color = verdictColor
        VStack(alignment: .leading, spacing: 0) {
            if let pickup {
                HStack(spacing: 9) {
                    Circle().fill(gameColor).frame(width: 8, height: 8)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(pickup.name).font(.pretendard(size: 14, weight: .bold))
                        Text(pickup.version.isEmpty ? "\(bannerLabel) 픽업" : "\(pickup.version) · \(bannerLabel) 픽업")
                            .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    }
                    Spacer(minLength: 6)
                    Text(pickup.dDayLabel(nowMillis: nowMs()))
                        .font(.pretendard(size: 11, weight: .bold)).foregroundStyle(gameColor)
                        .padding(.horizontal, 9).padding(.vertical, 4)
                        .background(gameColor.opacity(0.10), in: RoundedRectangle(cornerRadius: 8))
                }
                .padding(.bottom, 13)
            }
            if let outcome {
                HStack(spacing: 9) {
                    Text(verdictIcon).font(.system(size: 16))
                    Text(outcome.headline).font(.pretendard(size: 19, weight: .black)).foregroundStyle(color)
                }
                Text(detailLine(outcome))
                    .font(.pretendard(size: 13, weight: .semibold)).padding(.top, 9)
                ProgressView(value: Double(outcome.progressPercent) / 100).tint(color).padding(.top, 11)
                HStack {
                    Text("\(num(Int(outcome.availableCurrency))) / \(num(Int(outcome.neededCurrency))) \(currency)")
                    Spacer()
                    Text("\(outcome.progressPercent)%")
                }
                .font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary).padding(.top, 7)
                if outcome.shortfallWon > 0 {
                    Text("충전 시 약 \(won(outcome.shortfallWon))")
                        .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 6)
                }
            } else {
                Text("보유 \(currency)을 넣으면\n뽑을 수 있는지 알려드려요")
                    .font(.pretendard(size: 15, weight: .bold)).lineSpacing(3)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.vertical, 18)
        .background(color.opacity(0.07), in: RoundedRectangle(cornerRadius: 24))
    }

    private func detailLine(_ o: CalcOutcome) -> String {
        o.shortfallCurrency > 0
            ? "\(num(Int(o.shortfallCurrency))) \(currency) 부족 · \(o.shortfallPulls)뽑치"
            : "확정까지 \(o.neededPulls)뽑 · 여유 \(num(Int(o.availableCurrency - o.neededCurrency))) \(currency)"
    }
    private var verdictColor: Color {
        switch outcome?.verdict {
        case .secured: return okGreen
        case .short: return badRed
        default: return warnAmber
        }
    }
    private var verdictIcon: String {
        switch outcome?.verdict {
        case .secured: return "🟢"
        case .short: return "🔴"
        default: return "🟡"
        }
    }
}

// ── 접힌 입력 요약 ──
private struct SummaryRow: View {
    let pulls: Int
    let hasHeld: Bool
    let pity: Int
    let hasPityRecord: Bool
    let guaranteed: Bool
    let expanded: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack {
                Text(label).font(.pretendard(size: 12.5, weight: .semibold)).foregroundStyle(GLGColor.textPrimary)
                Spacer()
                Text(expanded ? "접기 ⌃" : "고치기 ›").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
            .padding(.horizontal, 14).padding(.vertical, 13)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    private var label: String {
        var s = hasHeld ? "\(pulls)뽑 보유" : "보유 재화 미입력"
        s += " · 천장 " + (hasPityRecord ? "\(pity)" : "미기록")
        if guaranteed { s += " · 확정" }
        return s
    }
}

// ── 입력 카드 ──
private struct InputCard: View {
    let banner: GachaBannerRate
    let prefill: CalcPrefill
    let currency: String
    @Binding var heldInput: String
    @Binding var pityEdit: String?
    @Binding var guaranteedEdit: Bool?
    @Binding var qty: Int
    let effGuaranteed: Bool
    let onHeldCommit: () -> Void
    let onOpenSavings: () -> Void

    @Environment(\.glgAccent) private var accent

    private var edited: Bool { pityEdit != nil || guaranteedEdit != nil }
    private var pityText: Binding<String> {
        Binding(
            get: { pityEdit ?? (prefill.hasPityRecord ? "\(prefill.pity)" : "") },
            set: { pityEdit = $0.filter(\.isNumber) }
        )
    }

    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 8) {
                    NumField(label: "보유 \(currency)", placeholder: "0", text: $heldInput, badge: false)
                        .frame(maxWidth: .infinity)
                        .onChange(of: heldInput) { onHeldCommit() }
                    NumField(label: "현재 천장", placeholder: "0", text: pityText,
                             badge: pityEdit == nil && prefill.hasPityRecord)
                        .frame(maxWidth: .infinity)
                }
                if !prefill.hasPityRecord && pityEdit == nil {
                    // 기록이 없는데 0 을 채우면 "천장 0"이라는 틀린 사실을 앱이 주장하게 된다.
                    HintRow(text: "천장을 기록하면 자동으로 채워요", action: "천장 입력", onTap: onOpenSavings)
                }
                if banner.has5050 && !banner.no5050 {
                    HStack {
                        Text("확정(픽업 보장) 보유").font(.pretendard(size: 13))
                        if guaranteedEdit == nil && prefill.hasPityRecord { RecordBadge() }
                        Spacer()
                        Toggle("", isOn: Binding(get: { effGuaranteed }, set: { guaranteedEdit = $0 }))
                            .labelsHidden().tint(accent.primary)
                    }
                    .padding(.top, 10)
                }
                QtyRow(qty: $qty).padding(.top, 10)
                if edited {
                    HintRow(text: "고친 값은 이 계산에만 반영돼요", action: "기록값으로") {
                        pityEdit = nil; guaranteedEdit = nil
                    }
                }
            }
        }
    }
}

// ── 마감까지 모을 수 있는 양 ──
private struct FreeIncomeCard: View {
    let free: FreeIncome
    let currency: String
    @Binding var includePass: Bool
    @Environment(\.glgAccent) private var accent

    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                Text("마감까지 모을 수 있는 양")
                    .font(.pretendard(size: 13, weight: .bold)).padding(.bottom, 10)
                ForEach(Array(free.lines.enumerated()), id: \.offset) { _, line in
                    let on = includePass || !line.optional
                    HStack {
                        Text(line.label).font(.pretendard(size: 12))
                            .foregroundStyle(GLGColor.textSecondary.opacity(on ? 1 : 0.45))
                        if line.optional {
                            Toggle("", isOn: $includePass).labelsHidden().tint(accent.primary).scaleEffect(0.78)
                        }
                        Spacer()
                        Text(num(Int(line.amount))).font(.pretendard(size: 12, weight: .semibold))
                            .foregroundStyle(GLGColor.textPrimary.opacity(on ? 1 : 0.35))
                    }
                    .padding(.vertical, 4)
                }
                Divider().overlay(Color.black.opacity(0.06)).padding(.vertical, 12)
                HStack {
                    Text("합계").font(.pretendard(size: 13, weight: .bold))
                    Spacer()
                    Text("\(num(Int(free.total))) \(currency) · \(free.pullsLabel)")
                        .font(.pretendard(size: 13, weight: .black)).foregroundStyle(accent.primary)
                }
                Text("매일 빠짐없이 받는 기준이에요.")
                    .font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
            }
        }
    }
}

// ── 몇 뽑이면 되나 ──
private struct QuantileCard: View {
    let q: PullQuantiles
    let banner: GachaBannerRate

    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                Text("몇 뽑이면 되나").font(.pretendard(size: 13, weight: .bold)).padding(.bottom, 10)
                QuantileRow(label: "절반은 이 안에 끝나요", pulls: Int(q.p50), perPull: Int(banner.perPull), color: okGreen)
                divider
                QuantileRow(label: "열에 아홉은 이 안에", pulls: Int(q.p90), perPull: Int(banner.perPull), color: warnAmber)
                divider
                QuantileRow(label: worstLabel, pulls: Int(q.worst), perPull: Int(banner.perPull), color: badRed)
            }
        }
    }
    private var worstLabel: String { banner.has5050 && !banner.no5050 ? "최악 (천장 두 번)" : "최악 (천장 도달)" }
    private var divider: some View { Divider().overlay(Color.black.opacity(0.06)).padding(.vertical, 12) }
}

private struct QuantileRow: View {
    let label: String; let pulls: Int; let perPull: Int; let color: Color
    var body: some View {
        HStack(spacing: 8) {
            Text(label).font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
            Spacer(minLength: 4)
            Text("\(pulls)뽑").font(.pretendard(size: 16, weight: .black)).foregroundStyle(color)
            Text(num(pulls * perPull)).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
        }
    }
}

// 색
private let okGreen = Color(hex: 0xFF16A34A)
private let warnAmber = Color(hex: 0xFFD97706)
private let badRed = Color(hex: 0xFFDC2626)

// ── 공용 작은 컴포넌트 ──
private struct NumField: View {
    let label: String; let placeholder: String; @Binding var text: String; let badge: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Text(label).font(.pretendard(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                if badge { RecordBadge() }
            }
            TextField(placeholder, text: $text).keyboardType(.numberPad).textFieldStyle(.plain).glgPillField()
                .onChange(of: text) { _, newValue in
                    let digits = newValue.filter(\.isNumber)
                    if digits != newValue { text = digits }
                }
        }
    }
}

/// 값의 출처가 앱 기록임을 알리는 배지 — 사용자가 고치면 사라진다.
private struct RecordBadge: View {
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Text("🔗 앱 기록")
            .font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(accent.primary)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(accent.primary.opacity(0.13), in: RoundedRectangle(cornerRadius: 6))
    }
}

private struct HintRow: View {
    let text: String; let action: String; let onTap: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack {
            Text(text).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
            Spacer()
            Button(action: onTap) {
                Text(action).font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(accent.primary)
                    .padding(.horizontal, 10).padding(.vertical, 5)
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 11)
    }
}

private struct QtyRow: View {
    @Binding var qty: Int
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack(spacing: 10) {
            Text("목표 개수").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            HStack(spacing: 6) {
                ForEach(1...3, id: \.self) { q in
                    Button { qty = q } label: {
                        Text("\(q)").font(.pretendard(size: 13, weight: .bold))
                            .foregroundStyle(q == qty ? .white : GLGColor.textSecondary)
                            .frame(width: 34, height: 34)
                            .background(q == qty ? accent.primary : Color(hex: 0xFFF2F2F6), in: Circle())
                    }.buttonStyle(.plain)
                }
            }
        }
    }
}

private struct ActionButton: View {
    let text: String; let primary: Bool; let action: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Button(action: action) {
            Text(text).font(.pretendard(size: 12.5, weight: .bold))
                .foregroundStyle(primary ? .white : GLGColor.textPrimary)
                .frame(maxWidth: .infinity).padding(.vertical, 14)
                .background(primary ? accent.primary : Color.white, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}
