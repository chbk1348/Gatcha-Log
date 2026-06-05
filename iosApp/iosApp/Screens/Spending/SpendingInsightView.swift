import SwiftUI
import ComposeApp

// 지출 인사이트 — 예산 페이스 예측 + 게임별 월 추이 + 결제수단·태그 비중. (Compose SpendingInsightScreen 대응)
struct SpendingInsightView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var spendings: [Spending] { store.spendings }
    private var monthTotal: Int64 { store.monthlyTotal() }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if spendings.isEmpty {
                    Text("지출 기록이 쌓이면\n예산 페이스·게임별 추이·카테고리 비중을 분석해 드려요.")
                        .font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.top, 40)
                } else {
                    budgetPaceCard
                    MonthlyTrendCard(spendings: spendings, year: store.displayYear)
                    breakdownCard("결제수단별 비중", nil, paymentRows)
                    breakdownCard("태그별 지출", "여러 태그가 달린 지출은 중복 집계돼요", tagRows)
                }
                Color.clear.frame(height: 8)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("지출 인사이트")
        .navigationBarTitleDisplayMode(.inline)
    }

    // ── 1) 예산 페이스 ──
    private var budgetPaceCard: some View {
        let cal = Calendar.current
        let now = Date()
        let dayOfMonth = cal.component(.day, from: now)
        let daysInMonth = cal.range(of: .day, in: .month, for: now)?.count ?? 30
        let budget = store.budget
        let projected = dayOfMonth > 0 ? monthTotal * Int64(daysInMonth) / Int64(dayOfMonth) : monthTotal
        let dailyAvg = dayOfMonth > 0 ? monthTotal / Int64(dayOfMonth) : 0
        let remainingDays = max(daysInMonth - dayOfMonth, 0)
        return GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                cardTitle("\(store.displayMonth)월 예산 페이스", "\(dayOfMonth)일 경과 · \(remainingDays)일 남음")
                HStack(alignment: .bottom, spacing: 8) {
                    Text("월말 예상").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    Text(won(projected)).font(.system(size: 24, weight: .bold)).foregroundStyle(accent.primary)
                }
                .padding(.top, 14)
                HStack(spacing: 8) {
                    insightTile(won(monthTotal), "현재 지출")
                    insightTile(won(dailyAvg), "하루 평균")
                    insightTile(budget > 0 ? won(budget) : "—", "이번 달 예산")
                }
                .padding(.top, 12)
                if budget > 0 {
                    let over = projected > budget
                    let frac = min(max(Double(projected)/Double(budget), 0), 1)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(GLGColor.progressEmpty)
                            Capsule().fill(over ? GLGColor.dangerText : accent.primary).frame(width: geo.size.width * frac)
                        }
                    }
                    .frame(height: 8).padding(.top, 14)
                    let diff = abs(projected - budget)
                    Text(over ? "이 페이스면 예산을 \(won(diff)) 초과할 것 같아요"
                              : "이 페이스면 예산 안에서 \(won(diff)) 여유가 생겨요")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(over ? GLGColor.dangerText : accent.primary).padding(.top, 8)
                } else {
                    Text("예산을 설정하면 초과 여부를 예측해 드려요")
                        .font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 10)
                }
            }
        }
    }

    private func insightTile(_ value: String, _ label: String) -> some View {
        VStack(spacing: 3) {
            Text(value).font(.system(size: 14, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10)
        .background(accent.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
    }

    // ── 3·4) 결제수단·태그 비중 ──
    private var paymentRows: [(String, Int64, Double)] {
        let total = spendings.reduce(0) { $0 + $1.amount }
        return Dictionary(grouping: spendings, by: { $0.paymentMethod.isEmpty ? "기타" : $0.paymentMethod })
            .map { ($0.key, $0.value.reduce(0) { $0 + $1.amount }) }
            .sorted { $0.1 > $1.1 }
            .map { ($0.0, $0.1, total > 0 ? Double($0.1)/Double(total) : 0) }
    }
    private var tagRows: [(String, Int64, Double)] {
        var m: [String: Int64] = [:]
        for s in spendings { for t in s.tags { m[t, default: 0] += s.amount } }
        let sorted = m.sorted { $0.value > $1.value }.prefix(8)
        let maxTag = max(sorted.first?.value ?? 1, 1)
        return sorted.map { ("#\($0.key)", $0.value, Double($0.value)/Double(maxTag)) }
    }

    @ViewBuilder
    private func breakdownCard(_ title: String, _ sub: String?, _ rows: [(String, Int64, Double)]) -> some View {
        if !rows.isEmpty {
            GLGCard(cornerRadius: 20, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    cardTitle(title, sub)
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        BreakdownRow(name: row.0, amount: row.1, frac: row.2).padding(.top, 10)
                    }
                }
            }
        }
    }

    private func cardTitle(_ title: String, _ sub: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.system(size: 14, weight: .bold))
            if let sub { Text(sub).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary) }
        }
    }
}

struct BreakdownRow: View {
    let name: String; let amount: Int64; let frac: Double
    @Environment(\.glgAccent) private var accent
    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(name).font(.system(size: 13, weight: .medium)).lineLimit(1)
                Spacer()
                Text(won(amount)).font(.system(size: 13, weight: .bold))
                Text("\(Int(frac*100))%").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(GLGColor.progressEmpty)
                    Capsule().fill(accent.primary).frame(width: geo.size.width * min(max(frac,0),1))
                }
            }
            .frame(height: 5)
        }
    }
}

// 게임별 월 추이 — 올해, 누적 막대 (상위 5 게임 + 기타)
struct MonthlyTrendCard: View {
    let spendings: [Spending]
    let year: Int
    @Environment(\.glgAccent) private var accent
    private let etcColor = Color(hex: 0xFFB8BDC6)

    var body: some View {
        let yearItems = spendings.filter { DateMillis.isSameYear($0.dateMillis, year) }
        if !yearItems.isEmpty {
            let topGames = Array(Dictionary(grouping: yearItems, by: { $0.gameName })
                .mapValues { $0.reduce(0) { $0 + $1.amount } }
                .sorted { $0.value > $1.value }.prefix(5).map { $0.key })
            let hasEtc = yearItems.contains { !topGames.contains($0.gameName) }
            let legend = topGames + (hasEtc ? ["기타"] : [])
            // month(0..11) -> game -> amount
            var monthGame = [[String: Int64]](repeating: [:], count: 12)
            for s in yearItems {
                let m = DateMillis.comps(s.dateMillis).month - 1
                if m >= 0 && m < 12 {
                    let key = topGames.contains(s.gameName) ? s.gameName : "기타"
                    monthGame[m][key, default: 0] += s.amount
                }
            }
            let monthTotals = monthGame.map { $0.values.reduce(0, +) }
            let maxMonth = max(monthTotals.max() ?? 0, 1)
            func colorOf(_ g: String) -> Color { g == "기타" ? etcColor : Color(argb64: GameData.shared.colorFor(name: g)) }

            return AnyView(GLGCard(cornerRadius: 20, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("게임별 월 추이").font(.system(size: 14, weight: .bold))
                        Text("\(year)년 · 누적 막대").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    }
                    HStack(alignment: .bottom, spacing: 3) {
                        ForEach(0..<12, id: \.self) { m in
                            VStack(spacing: 3) {
                                ZStack(alignment: .bottom) {
                                    Color.clear.frame(height: 110)
                                    VStack(spacing: 0) {
                                        ForEach(legend, id: \.self) { g in
                                            let amt = monthGame[m][g] ?? 0
                                            if amt > 0 {
                                                Rectangle().fill(colorOf(g))
                                                    .frame(height: 110 * Double(amt)/Double(maxMonth))
                                            }
                                        }
                                    }
                                    .frame(maxWidth: .infinity).padding(.horizontal, 2)
                                    .clipShape(RoundedRectangle(cornerRadius: 3))
                                }
                                Text("\(m+1)").font(.system(size: 8)).foregroundStyle(GLGColor.textSecondary)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.top, 14)
                    // 범례
                    FlexibleRow(legend) { g in
                        HStack(spacing: 5) {
                            Circle().fill(colorOf(g)).frame(width: 8, height: 8)
                            Text(g).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        }
                    }
                    .padding(.top, 12)
                }
            })
        } else {
            return AnyView(EmptyView())
        }
    }
}
