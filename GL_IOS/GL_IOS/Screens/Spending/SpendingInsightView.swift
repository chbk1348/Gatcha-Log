import SwiftUI
import Shared

// 지출 인사이트 — 예산 페이스 예측 + 게임별 월 추이 + 결제수단·태그 비중. (Compose SpendingInsightScreen 대응)
struct SpendingInsightView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var tab = 0   // 0=월간 인사이트, 1=연간 리포트

    private var spendings: [Spending] { store.spendings }
    private var monthTotal: Int64 { store.monthlyTotal() }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if spendings.isEmpty {
                    Text("지출 기록이 쌓이면\n예산 페이스·게임별 추이·카테고리 비중을 분석해 드려요.")
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.top, 40)
                } else {
                    insightToggle
                    if tab == 0 {
                        budgetPaceCard
                        momCard
                        paymentStatsCard
                        MonthlyTrendCard(spendings: spendings, year: store.displayYear)
                        breakdownCard("결제수단별 비중", nil, paymentRows)
                        breakdownCard("충전 플랫폼별 비중", nil, platformRows)
                        breakdownCard("태그별 지출", "여러 태그가 달린 지출은 중복 집계돼요", tagRows)
                        subscriptionCard
                    } else {
                        AnnualReportContent(store: store)
                    }
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
        // 월말 예상: 워밍업(7일) 완화 — 경과일이 7일 미만이면 분모를 7로 하한 처리해 월초 run-rate 과대추정을 억제.
        // (배율 = daysInMonth / max(dayOfMonth, 7) ≥ 1 이라 예상값은 항상 현재 지출 이상. 7일 경과 후엔 순수 run-rate와 동일)
        let effectiveDays = max(dayOfMonth, min(daysInMonth, 7))
        let projected = dayOfMonth > 0 ? monthTotal * Int64(daysInMonth) / Int64(effectiveDays) : monthTotal
        let dailyAvg = dayOfMonth > 0 ? monthTotal / Int64(dayOfMonth) : 0
        let remainingDays = max(daysInMonth - dayOfMonth, 0)
        return GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                cardTitle("\(store.displayMonth)월 예산 페이스", "\(dayOfMonth)일 경과 · \(remainingDays)일 남음")
                HStack(alignment: .bottom, spacing: 8) {
                    Text("월말 예상").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    Text(won(projected)).font(.pretendard(size: 24, weight: .bold)).foregroundStyle(accent.primary)
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
                        .font(.pretendard(size: 12, weight: .medium))
                        .foregroundStyle(over ? GLGColor.dangerText : accent.primary).padding(.top, 8)
                } else {
                    Text("예산을 설정하면 초과 여부를 예측해 드려요")
                        .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 10)
                }
            }
        }
    }

    private func insightTile(_ value: String, _ label: String) -> some View {
        VStack(spacing: 3) {
            Text(value).font(.pretendard(size: 14, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10)
        .background(accent.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
    }

    // ── 월간 인사이트 / 연간 리포트 세그먼트 토글 ──
    private var insightToggle: some View {
        HStack(spacing: 4) {
            ForEach(Array(["월간 인사이트", "연간 리포트"].enumerated()), id: \.offset) { i, label in
                let sel = i == tab
                Text(label)
                    .font(.pretendard(size: 13, weight: .bold))
                    .foregroundStyle(sel ? GLGColor.textPrimary : GLGColor.textSecondary)
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .background(sel ? Color.white : Color.clear, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .contentShape(Rectangle())
                    .onTapGesture { tab = i }
            }
        }
        .padding(4)
        .background(Color(hex: 0xFFF1F1F4), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
    }

    // ── 신규) 전월 대비 ──
    private var momCard: some View {
        let mom = SpendingInsightStats.shared.momComparison(spendings: spendings, year: Int32(store.displayYear), month: Int32(store.displayMonth))
        let up = mom.delta > 0
        let warn = Color(hex: 0xFFF59E0B)
        return GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                cardTitle("전월 대비", "이번 달 vs 지난 달 지출")
                HStack(alignment: .bottom, spacing: 10) {
                    Text(won(mom.thisMonth)).font(.pretendard(size: 24, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    if mom.percent >= 0 {
                        Text("\(up ? "▲" : "▼") \(abs(Int(mom.percent)))% · \(up ? "+" : "-")\(won(abs(mom.delta)))")
                            .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(up ? warn : accent.primary)
                            .padding(.horizontal, 9).padding(.vertical, 4)
                            .background((up ? warn : accent.primary).opacity(0.12), in: RoundedRectangle(cornerRadius: 9))
                    } else {
                        Text("지난달 기록 없음").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    }
                    Spacer(minLength: 0)
                }.padding(.top, 12)
                if !mom.topGame.isEmpty && mom.topGameDelta != 0 {
                    Text("증감 가장 큰 게임 · \(mom.topGame) \(mom.topGameDelta > 0 ? "+" : "-")\(won(abs(mom.topGameDelta)))")
                        .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 12)
                }
            }
        }
    }

    // ── 신규) 결제 통계 ──
    @ViewBuilder private var paymentStatsCard: some View {
        let stats = SpendingInsightStats.shared.paymentStats(spendings: spendings, year: Int32(store.displayYear), month: Int32(store.displayMonth))
        if stats.count > 0 {
            GLGCard(cornerRadius: 20, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    cardTitle("결제 통계", "\(store.displayMonth)월 기준")
                    HStack(spacing: 10) {
                        statTile("\(stats.count)건", "결제 건수")
                        statTile(won(stats.average), "평균 결제액")
                    }.padding(.top, 12)
                    HStack(spacing: 10) {
                        statTile(won(stats.maxAmount), "최고 단건")
                        statTile(stats.topWeekday.isEmpty ? "—" : "\(stats.topWeekday)요일", "최다 결제")
                    }.padding(.top, 8)
                }
            }
        }
    }

    private func statTile(_ value: String, _ label: String) -> some View {
        VStack(spacing: 3) {
            Text(value).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
            Text(label).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 11)
        .background(Color.black.opacity(0.03), in: RoundedRectangle(cornerRadius: 12))
    }

    // ── 신규) 충전 플랫폼별 비중 ──
    private var platformRows: [(String, Int64, Double)] {
        SpendingInsightStats.shared.platformBreakdown(spendings: spendings).map {
            ($0.name, $0.amount, $0.total > 0 ? Double($0.amount) / Double($0.total) : 0)
        }
    }

    // ── 신규) 정기결제 요약 ──
    @ViewBuilder private var subscriptionCard: some View {
        let subs = store.subscriptions
        let total = subs.reduce(Int64(0)) { $0 + $1.amount }
        GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    cardTitle("정기결제 요약", nil)
                    Spacer()
                    NavigationLink {
                        SubscriptionCenterView(store: store)
                    } label: {
                        Text("관리").font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(accent.primary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                    }.buttonStyle(.plain)
                }
                if subs.isEmpty {
                    Text("월정액·패스를 등록하고 갱신일을 관리하세요")
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .padding(.top, 10)
                } else {
                    HStack(alignment: .bottom) {
                        Text("월 정기결제 \(subs.count)건").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        Spacer()
                        Text("\(won(total)) / 월").font(.pretendard(size: 16, weight: .bold)).foregroundStyle(accent.primary)
                    }.padding(.top, 10)
                    ForEach(Array(subs.prefix(5).enumerated()), id: \.offset) { _, s in
                        HStack(spacing: 9) {
                            Circle().fill(Color(argb64: s.gameColor)).frame(width: 8, height: 8)
                            Text(s.name).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                            Spacer()
                            Text(won(s.amount)).font(.pretendard(size: 13, weight: .bold))
                            Text("D-\(s.dDay(nowMillis: nowMs()))").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        }.padding(.top, 11)
                    }
                    if subs.count > 5 {
                        Text("+\(subs.count - 5)건").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
                    }
                }
            }
        }
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
            Text(title).font(.pretendard(size: 14, weight: .bold))
            if let sub { Text(sub).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary) }
        }
    }
}

struct BreakdownRow: View {
    let name: String; let amount: Int64; let frac: Double
    @Environment(\.glgAccent) private var accent
    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(name).font(.pretendard(size: 13, weight: .medium)).lineLimit(1)
                Spacer()
                Text(won(amount)).font(.pretendard(size: 13, weight: .bold))
                Text("\(Int(frac*100))%").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
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
                        Text("게임별 월 추이").font(.pretendard(size: 14, weight: .bold))
                        Text(verbatim: "\(year)년 · 누적 막대").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
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
                                Text("\(m+1)").font(.pretendard(size: 8)).foregroundStyle(GLGColor.textSecondary)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.top, 14)
                    // 범례
                    FlexibleRow(legend) { g in
                        HStack(spacing: 5) {
                            Circle().fill(colorOf(g)).frame(width: 8, height: 8)
                            Text(g).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
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
