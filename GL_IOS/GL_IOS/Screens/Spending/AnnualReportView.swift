import SwiftUI
import Shared

// 연간 리포트 — 연도 선택 + 총/평균/기록 + 월별 막대 + 게임별 분석.
// 지출 인사이트의 '연간' 탭에 임베드되는 콘텐츠(스크롤·네비타이틀은 부모 제공).
struct AnnualReportContent: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var selectedYear: Int = 0

    // ── 집계 ────────────────────────────────────────────────────────────────
    //
    // 예전엔 years/yearItems/total/monthly/months/avg/byGame 이 전부 computed 였다.
    // computed 는 읽을 때마다 다시 계산되는데 서로가 서로를 읽어서(avg→months→monthly→yearItems,
    // 게임별 행마다 total) 한 번 그릴 때 지출 전체를 수십 번 훑었다. 연도 칩을 누를 때마다 그게 반복됐다.
    // 지출·연도가 바뀔 때만 한 번 계산해 둔다.
    private struct AnnualStats {
        var years: [Int] = []
        var year: Int = 0
        var count: Int = 0
        var total: Int64 = 0
        var monthly: [Int64] = Array(repeating: 0, count: 12)
        var avg: Int64 = 0
        var byGame: [(String, Int64)] = []
    }

    @State private var stats = AnnualStats()

    /// 재계산 트리거 — 지출 목록과 선택 연도.
    ///
    /// ⚠️ **개수로 잡으면 안 된다.** 예전엔 `"\(store.spendings.count)|…"` 였는데, 그러면
    /// 지출의 **금액·날짜만 수정했을 때 개수가 그대로라 재계산이 안 일어난다** — 연간 리포트가
    /// 옛 수치를 그대로 들고 있게 된다(1건 삭제 + 1건 추가, 일괄 편집도 같은 함정).
    ///
    /// 목록 자체를 키로 쓴다. Swift `Array.==` 는 버퍼가 같으면 O(1)로 끝나므로, 변화가 없는
    /// 대부분의 평가에서는 비용이 사실상 없다. `SubscriptionCenterView` 가 이미 같은 방식이다
    /// (`.task(id: store.subscriptions)`).
    private struct StatsKey: Equatable {
        let spendings: [Spending]
        let selectedYear: Int
        let displayYear: Int
        let displayMonth: Int
    }

    private var statsKey: StatsKey {
        StatsKey(spendings: store.spendings, selectedYear: selectedYear,
                 displayYear: store.displayYear, displayMonth: store.displayMonth)
    }

    private var year: Int { stats.year }

    private static func compute(spendings: [Spending], selectedYear: Int,
                                displayYear: Int, displayMonth: Int) -> AnnualStats {
        var s = AnnualStats()
        // 연도 목록·연도별 항목을 **한 번의 순회**로 만든다(예전엔 각각 전체를 훑었다).
        var yearSet = Set<Int>([displayYear])
        for sp in spendings { yearSet.insert(DateMillis.comps(sp.dateMillis).year) }
        s.years = yearSet.sorted(by: >)
        s.year = selectedYear == 0 ? (s.years.first ?? displayYear) : selectedYear

        var byGame: [String: Int64] = [:]
        for sp in spendings {
            let c = DateMillis.comps(sp.dateMillis)
            guard c.year == s.year else { continue }
            s.count += 1
            s.total += sp.amount
            if c.month >= 1 && c.month <= 12 { s.monthly[c.month - 1] += sp.amount }
            byGame[sp.gameName, default: 0] += sp.amount
        }
        s.byGame = byGame.map { ($0.key, $0.value) }.sorted { $0.1 > $1.1 }

        let months = s.year == displayYear ? displayMonth : max(s.monthly.filter { $0 > 0 }.count, 1)
        s.avg = months > 0 ? s.total / Int64(months) : 0
        return s
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if stats.years.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(stats.years, id: \.self) { y in
                            GamePill(label: "\(y)년", selected: y == year, accent: accent.primary) { selectedYear = y }
                        }
                    }
                }
            }
            GLGCard(cornerRadius: 24, padding: 16) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            infoCol(won(stats.total), "총 지출")
                            infoCol(won(stats.avg), "월 평균")
                            infoCol("\(stats.count)회", "총 기록")
                        }
                        Text("월별 지출").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.top, 18)
                        MonthlyBars(monthly: stats.monthly, currentMonth: year == store.displayYear ? store.displayMonth : nil)
                            .padding(.top, 10)
                        if !stats.byGame.isEmpty {
                            Text("게임별 지출").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.top, 18)
                            let total = stats.total
                            ForEach(stats.byGame, id: \.0) { (game, amt) in
                                GameBreakdownRow(game: game, amount: amt, frac: total > 0 ? Double(amt)/Double(total) : 0)
                                    .padding(.top, 10)
                            }
                        }
                        if stats.count == 0 {
                            Text("이 해의 지출 기록이 없어요").font(.pretendard(size: 12)).foregroundStyle(Color(.systemGray3)).padding(.top, 8)
                        }
                    }
                }
        }
        .task(id: statsKey) {
            stats = Self.compute(spendings: store.spendings, selectedYear: selectedYear,
                                 displayYear: store.displayYear, displayMonth: store.displayMonth)
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
