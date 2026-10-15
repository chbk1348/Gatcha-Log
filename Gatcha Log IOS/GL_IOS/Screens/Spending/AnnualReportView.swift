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
        var months: [AnnualMonthBar] = []
        var avg: Int64 = 0
        var byGame: [AnnualGameShare] = []
    }

    @State private var stats = AnnualStats()

    /// 재계산 트리거 — 지출 목록과 선택 연도.
    ///
    /// ⚠️ **개수로 잡으면 안 된다.** 예전엔 `"\(store.spendings.count)|…"` 였는데, 그러면
    /// 지출의 **금액·날짜만 수정했을 때 개수가 그대로라 재계산이 안 일어난다** — 연간 리포트가
    /// 옛 수치를 그대로 들고 있게 된다(1건 삭제 + 1건 추가, 일괄 편집도 같은 함정).
    ///
    /// 목록 자체를 키로 쓴다. Swift `Array.==` 는 버퍼가 같으면 O(1)로 끝나므로, 변화가 없는
    /// 대부분의 평가에서는 비용이 사실상 없다.
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

    /// 집계는 전부 공용 `AnnualReportLogic` — Android 와 같은 함수다(27.52.0). 여기서는 Swift 타입으로 옮겨 담기만 한다.
    ///
    /// 예전엔 같은 계산을 Swift 로 따로 갖고 있었다. 숫자는 같았지만 게임별 합계를 Dictionary 로 모아 정렬해서,
    /// **금액이 같은 게임의 순서**가 실행마다 달라질 수 있었다(Android 는 먼저 기록된 게임이 위).
    /// `selectedYear` 0(아직 안 고름)이나 기록이 다 사라진 해는 공용 쪽이 가장 최근 해로 바꿔 준다.
    private static func compute(spendings: [Spending], selectedYear: Int,
                                displayYear: Int, displayMonth: Int) -> AnnualStats {
        let r = AnnualReportLogic.shared.report(spendings: spendings, selectedYear: Int32(selectedYear),
                                                displayYear: Int32(displayYear), displayMonth: Int32(displayMonth))
        return AnnualStats(years: r.years.map { $0.intValue }, year: Int(r.year), count: Int(r.count),
                           total: r.total, months: r.months, avg: r.monthlyAverage, byGame: r.byGame)
    }

    // 카드 없이 화면 폭 섹션 + 10 띠 — 월간 인사이트와 같은 규격(InsightSection, Android 와 같다).
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if stats.years.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(stats.years, id: \.self) { y in
                            GamePill(label: "\(y)년", selected: y == year, accent: accent.primary) { selectedYear = y }
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.top, 18)
            }
            InsightSection(title: "\(String(year))년 요약") {
                InsightStatGrid(cells: [(won(stats.total), "총 지출"), (won(stats.avg), "월 평균"), ("\(stats.count)회", "총 기록")], cols: 3)
                if stats.count == 0 {
                    Text("이 해의 지출 기록이 없어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.top, 12)
                }
            }
            if stats.count > 0 {
                InsightBand()
                InsightSection(title: "월별 지출") {
                    MonthlyBars(months: stats.months, currentMonth: year == store.displayYear ? store.displayMonth : nil)
                }
                if !stats.byGame.isEmpty {
                    InsightBand()
                    InsightSection(title: "게임별 지출") {
                        ForEach(stats.byGame, id: \.game) { share in
                            let color = Color(argb64: GameData.shared.colorFor(name: share.game))
                            InsightShareRow(name: share.game, amount: share.amount, frac: share.ratio, color: color, dot: color)
                        }
                    }
                }
            }
        }
        .task(id: statsKey) {
            stats = Self.compute(spendings: store.spendings, selectedYear: selectedYear,
                                 displayYear: store.displayYear, displayMonth: store.displayMonth)
        }
    }
}

/// 월별 막대 12칸 — 높이 비율(최소 높이 포함)은 공용 `AnnualReportLogic` 이 정해 준다.
struct MonthlyBars: View {
    let months: [AnnualMonthBar]
    let currentMonth: Int?
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack(alignment: .bottom, spacing: 4) {
            ForEach(months, id: \.month) { bar in
                let month = Int(bar.month)
                let isCur = currentMonth == month
                VStack(spacing: 4) {
                    ZStack(alignment: .bottom) {
                        Color.clear.frame(height: 100)
                        let h = bar.barRatio
                        if h > 0 {
                            UnevenRoundedRectangle(topLeadingRadius: 3, topTrailingRadius: 3)
                                .fill(isCur ? accent.primary : accent.primary.opacity(0.3))
                                .frame(height: 100 * h)
                                .frame(maxWidth: .infinity).padding(.horizontal, 2)
                        }
                    }
                    Text("\(month)").font(.pretendard(size: 11, weight: isCur ? .bold : .regular))
                        .foregroundStyle(isCur ? GLGColor.textPrimary : GLGColor.textSecondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}
