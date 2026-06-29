import SwiftUI
import Shared

// 연간 리포트 — 연도 선택 + 총/평균/기록 + 월별 막대 + 게임별 분석.
// 지출 인사이트의 '연간' 탭에 임베드되는 콘텐츠(스크롤·네비타이틀은 부모 제공).
struct AnnualReportContent: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var selectedYear: Int = 0

    private var years: [Int] {
        let ys = Set(store.spendings.map { DateMillis.comps($0.dateMillis).year } + [store.displayYear])
        return ys.sorted(by: >)
    }
    private var year: Int { selectedYear == 0 ? (years.first ?? store.displayYear) : selectedYear }
    private var yearItems: [Spending] { store.spendings.filter { DateMillis.isSameYear($0.dateMillis, year) } }
    private var total: Int64 { yearItems.reduce(0) { $0 + $1.amount } }
    private var monthly: [Int64] {
        var arr = [Int64](repeating: 0, count: 12)
        for s in yearItems { let m = DateMillis.comps(s.dateMillis).month; if m >= 1 && m <= 12 { arr[m-1] += s.amount } }
        return arr
    }
    private var months: Int {
        year == store.displayYear ? store.displayMonth : max(monthly.filter { $0 > 0 }.count, 1)
    }
    private var avg: Int64 { months > 0 ? total / Int64(months) : 0 }
    private var byGame: [(String, Int64)] {
        Dictionary(grouping: yearItems, by: { $0.gameName })
            .map { ($0.key, $0.value.reduce(0) { $0 + $1.amount }) }
            .sorted { $0.1 > $1.1 }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if years.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(years, id: \.self) { y in
                            GamePill(label: "\(y)년", selected: y == year, accent: accent.primary) { selectedYear = y }
                        }
                    }
                }
            }
            GLGCard(cornerRadius: 24, padding: 16) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            infoCol(won(total), "총 지출")
                            infoCol(won(avg), "월 평균")
                            infoCol("\(yearItems.count)회", "총 기록")
                        }
                        Text("월별 지출").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.top, 18)
                        MonthlyBars(monthly: monthly, currentMonth: year == store.displayYear ? store.displayMonth : nil)
                            .padding(.top, 10)
                        if !byGame.isEmpty {
                            Text("게임별 지출").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.top, 18)
                            ForEach(byGame, id: \.0) { (game, amt) in
                                GameBreakdownRow(game: game, amount: amt, frac: total > 0 ? Double(amt)/Double(total) : 0)
                                    .padding(.top, 10)
                            }
                        }
                        if yearItems.isEmpty {
                            Text("이 해의 지출 기록이 없어요").font(.pretendard(size: 12)).foregroundStyle(Color(.systemGray3)).padding(.top, 8)
                        }
                    }
                }
        }
    }

    private func infoCol(_ value: String, _ label: String) -> some View {
        VStack(spacing: 3) {
            Text(value).font(.pretendard(size: 15, weight: .bold)).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

struct MonthlyBars: View {
    let monthly: [Int64]
    let currentMonth: Int?
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let maxM = max(monthly.max() ?? 0, 1)
        HStack(alignment: .bottom, spacing: 3) {
            ForEach(0..<12, id: \.self) { m in
                VStack(spacing: 3) {
                    ZStack(alignment: .bottom) {
                        Color.clear.frame(height: 56)
                        let frac = min(max(Double(monthly[m]) / Double(maxM), 0), 1)
                        let h = monthly[m] > 0 ? max(frac, 0.05) : 0
                        if h > 0 {
                            RoundedRectangle(cornerRadius: 3)
                                .fill(currentMonth == m+1 ? accent.primary : accent.primary.opacity(0.45))
                                .frame(height: 56 * h)
                                .frame(maxWidth: .infinity).padding(.horizontal, 2)
                        }
                    }
                    Text("\(m+1)").font(.pretendard(size: 8)).foregroundStyle(GLGColor.textSecondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

struct GameBreakdownRow: View {
    let game: String; let amount: Int64; let frac: Double
    var body: some View {
        let color = Color(argb64: GameData.shared.colorFor(name: game))
        VStack(spacing: 4) {
            HStack {
                Circle().fill(color).frame(width: 8, height: 8)
                Text(game).font(.pretendard(size: 13, weight: .medium)).lineLimit(1)
                Spacer()
                Text(won(amount)).font(.pretendard(size: 13, weight: .bold))
                Text("\(Int(frac*100))%").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(GLGColor.progressEmpty)
                    Capsule().fill(color).frame(width: geo.size.width * min(max(frac,0),1))
                }
            }
            .frame(height: 5)
        }
    }
}
