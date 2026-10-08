import SwiftUI
import Shared

// 지출 인사이트 — 예산 페이스 예측 + 게임별 월 추이 + 결제수단·태그 비중. (Compose SpendingInsightScreen 대응)
struct SpendingInsightView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var tab = 0   // 0=월간 인사이트, 1=연간 리포트

    private var spendings: [Spending] { store.spendings }
    private var monthTotal: Int64 { store.monthlyTotal }

    // ── 집계 캐시 ───────────────────────────────────────────────────────────
    //
    // 예전엔 전월 대비·결제 통계·결제수단/플랫폼/태그 비중·월 추이가 **전부 computed** 였다.
    // body 를 한 번 평가할 때마다 지출 전체가 Kotlin 으로 여섯 번 넘어가고 그 안에서 전체 순회가
    // 여섯 번 돌았다 — 세그먼트 토글을 누를 때마다 그게 반복됐다.
    // Android 는 같은 카드들이 이미 remember 로 캐시돼 있다(SpendingInsightScreen) — 파리티가
    // 깨진 쪽이 iOS 였다. 지출·연월이 바뀔 때만 한 번 계산한다.

    /// 월간 인사이트 카드들이 쓰는 집계 묶음.
    struct InsightStats {
        var mom: MoMComparison? = nil
        var payment: PaymentStats? = nil
        var paymentRows: [(String, Int64, Double)] = []
        var platformRows: [(String, Int64, Double)] = []
        var tagRows: [(String, Int64, Double)] = []
        var trend: MonthlyTrend? = nil
    }

    @State private var stats = InsightStats()

    /// 재계산 트리거. 개수가 아니라 **목록 자체**를 키로 쓴다 — 금액만 수정해도 다시 계산돼야 한다.
    private struct InsightKey: Equatable {
        let spendings: [Spending]
        let year: Int
        let month: Int
    }

    private var insightKey: InsightKey {
        InsightKey(spendings: store.spendings, year: store.displayYear, month: store.displayMonth)
    }

    /// 집계는 전부 GL_Shared `SpendingInsightStats` 단일 소스 — Android 와 같은 수치여야 한다.
    private static func compute(spendings: [Spending], year: Int, month: Int) -> InsightStats {
        guard !spendings.isEmpty else { return InsightStats() }
        let s = SpendingInsightStats.shared
        func rows(_ list: [BreakdownSlice], prefix: String = "") -> [(String, Int64, Double)] {
            list.map { (prefix + $0.name, $0.amount, $0.total > 0 ? Double($0.amount) / Double($0.total) : 0) }
        }
        return InsightStats(
            mom: s.momComparison(spendings: spendings, year: Int32(year), month: Int32(month)),
            payment: s.paymentStats(spendings: spendings, year: Int32(year), month: Int32(month)),
            paymentRows: rows(s.paymentBreakdown(spendings: spendings)),
            platformRows: rows(s.platformBreakdown(spendings: spendings)),
            // 태그는 중복 집계라 합계 비율이 100%를 넘을 수 있어, 막대 분모는 전체합이 아닌 최대 태그 금액(total).
            tagRows: rows(s.tagBreakdown(spendings: spendings), prefix: "#"),
            trend: s.monthlyTrend(spendings: spendings, year: Int32(year))
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if spendings.isEmpty {
                    Text("지출 기록이 쌓이면\n예산 페이스·게임별 추이·카테고리 비중을 분석해 드려요.")
                        .font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
                        .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                        .padding(.horizontal, 20).padding(.top, 40)
                } else {
                    insightToggle.padding(.horizontal, 16).padding(.top, 8)
                    if tab == 0 {
                        monthlySections
                    } else {
                        AnnualReportContent(store: store)
                    }
                }
            }
            // 넓은 창(iPad)에서는 가운데 640 폭으로 모은다 — 설정 · 마이페이지와 같은 규칙.
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        // 카드 없이 흰 바탕 — 섹션 사이는 10 띠(마이페이지 · 지출 상세와 같은 규격, Android 와 같다).
        .background(Color.white)
        .glgPageTitle("지출 인사이트")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: insightKey) {
            stats = Self.compute(spendings: store.spendings, year: store.displayYear, month: store.displayMonth)
        }
    }

    /// 월간: 이번 달 요약(옛 「N월 지출」 + 「전월 대비」 합침) → 예산 페이스 → 결제 통계 → 게임별 월 추이
    /// → 「전체 기간」 묶음(결제수단 · 충전 플랫폼 · 태그). 조건부 섹션 사이에만 띠가 들어간다.
    @ViewBuilder private var monthlySections: some View {
        monthSummary
        InsightBand()
        budgetPace
        if let ps = stats.payment, ps.count > 0 {
            InsightBand()
            InsightSection(title: "결제 통계", sub: "\(store.displayMonth)월 기준") {
                InsightStatGrid(cells: [
                    ("\(ps.count)건", "결제 건수"),
                    (won(ps.average), "평균 결제액"),
                    (won(ps.maxAmount), "최고 단건"),
                    (ps.topWeekday.isEmpty ? "—" : "\(ps.topWeekday)요일", "최다 결제"),
                ], cols: 2)
            }
        }
        if let trend = stats.trend {
            InsightBand()
            MonthlyTrendSection(trend: trend, year: store.displayYear)
        }
        // 이 아래는 **전체 기간** 값이다 — 위쪽 월간 섹션과 기준이 달라 섞여 읽혔다. 첫 섹션 위에 묶음 머리.
        let groups: [(String, String?, [(String, Int64, Double)])] = [
            ("결제수단별", nil, stats.paymentRows),
            ("충전 플랫폼별", nil, stats.platformRows),
            ("태그별", "태그가 여럿이면 중복 집계", stats.tagRows),
        ].filter { !$0.2.isEmpty }
        ForEach(Array(groups.enumerated()), id: \.offset) { i, g in
            InsightBand()
            if i == 0 {
                Text("전체 기간 · 지금까지 쓴 돈의 구성")
                    .font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.horizontal, 20).padding(.top, 22)
            }
            InsightSection(title: g.0, sub: g.1, top: i == 0 ? 14 : 22, bottom: i == groups.count - 1 ? 20 : 12) {
                ForEach(Array(g.2.enumerated()), id: \.offset) { _, row in
                    InsightShareRow(name: row.0, amount: row.1, frac: row.2, color: accent.primary)
                }
            }
        }
    }

    // ── 1) 이번 달 요약 ──
    @ViewBuilder private var monthSummary: some View {
        let warn = Color(hex: 0xFFF59E0B)
        InsightSection(title: nil) {
            Text("\(store.displayMonth)월 지출").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            Text(won(stats.mom?.thisMonth ?? monthTotal)).font(.pretendard(size: 32, weight: .black))
                .foregroundStyle(GLGColor.textPrimary).lineLimit(1).minimumScaleFactor(0.6).padding(.top, 2)
            if let mom = stats.mom {
                let up = mom.delta > 0
                // 지난달 기록 유무는 금액으로 가른다 — 줄어든 달은 증감률이 음수라 `percent >= 0` 으로는 "기록 없음" 이 떴다.
                if mom.lastMonth > 0 {
                    Text("지난달보다 \(up ? "▲" : "▼") \(abs(Int(mom.percent)))% · \(up ? "+" : "-")\(won(abs(mom.delta)))")
                        .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(up ? warn : accent.primary).padding(.top, 6)
                } else {
                    Text("지난달 기록 없음").font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary).padding(.top, 6)
                }
                if !mom.topGame.isEmpty && mom.topGameDelta != 0 {
                    Text("증감 가장 큰 게임 · \(mom.topGame) \(mom.topGameDelta > 0 ? "+" : "-")\(won(abs(mom.topGameDelta)))")
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4)
                }
            }
        }
    }

    // ── 2) 예산 페이스 ──
    private var budgetPace: some View {
        let cal = Calendar.current
        let now = Date()
        let dayOfMonth = cal.component(.day, from: now)
        let daysInMonth = cal.range(of: .day, in: .month, for: now)?.count ?? 30
        let budget = store.budget
        // 월말 예상(워밍업 7일 완화 포함)은 GL_Shared SpendingInsightStats 가 단일 소스 — Android 와 동일 수치.
        let pace = SpendingInsightStats.shared.budgetPace(monthTotal: monthTotal, dayOfMonth: Int32(dayOfMonth), daysInMonth: Int32(daysInMonth))
        let projected = pace.projected
        return InsightSection(title: "예산 페이스", sub: "\(dayOfMonth)일 경과 · \(Int(pace.remainingDays))일 남음") {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("월말 예상").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                Text(won(projected)).font(.pretendard(size: 24, weight: .bold)).foregroundStyle(accent.primary)
            }
            if budget > 0 {
                let over = projected > budget
                let frac = min(max(Double(projected) / Double(budget), 0), 1)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color(hex: 0xFFEDEFF3))
                        Capsule().fill(over ? GLGColor.urgent : accent.primary).frame(width: geo.size.width * frac)
                    }
                }
                .frame(height: 8).padding(.top, 12)
                let diff = abs(projected - budget)
                Text(over ? "이 페이스면 예산을 \(won(diff)) 초과할 것 같아요"
                          : "이 페이스면 예산 안에서 \(won(diff)) 여유가 생겨요")
                    .font(.pretendard(size: 14, weight: .medium))
                    .foregroundStyle(over ? GLGColor.urgent : accent.primary).padding(.top, 8)
            } else {
                Text("예산을 설정하면 초과 여부를 예측해 드려요")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
            }
            InsightStatGrid(cells: [
                (won(monthTotal), "현재 지출"),
                (won(pace.dailyAvg), "하루 평균"),
                (budget > 0 ? won(budget) : "—", "이번 달 예산"),
            ], cols: 3)
            .padding(.top, 18)
        }
    }

    // ── 월간 인사이트 / 연간 리포트 세그먼트 토글 ──
    private var insightToggle: some View {
        // GLDS 탭 neutral — 같은 데이터의 보기 방식 전환(9/30).
        GldsTabs(labels: ["월간 인사이트", "연간 리포트"], selection: $tab, variant: .neutral)
    }
}

// ── 공통 규격 — 연간 리포트도 쓴다 ─────────────────────────────────────────────

/// 섹션 사이 10 띠.
struct InsightBand: View {
    var body: some View { GldsBand() }
}

/// 섹션 — 좌우 20 · 위 22 · 아래 20. 제목 17 굵게 + 오른쪽 보조 12.
/// `bottom` 은 20 − 마지막 요소의 자체 아래 여백(비중 줄은 vertical 8 이라 12). 페이지 맨 아래 섹션은 20 그대로.
struct InsightSection<Content: View>: View {
    let title: String?
    var sub: String? = nil
    var top: CGFloat = 22
    var bottom: CGFloat = 20
    @ViewBuilder var content: Content

    var body: some View {
        GldsSection(title, top: top, bottom: bottom) {
            content
        } trailing: {
            if let sub { Text(sub).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary) }
        }
    }
}

/// 값 15 굵게 · 라벨 12 — 타일 면 없이. cols 칸씩 줄바꿈.
struct InsightStatGrid: View {
    let cells: [(String, String)]
    let cols: Int
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ForEach(Array(stride(from: 0, to: cells.count, by: cols)), id: \.self) { start in
                HStack(alignment: .top, spacing: 0) {
                    ForEach(start..<start + cols, id: \.self) { i in
                        if i < cells.count {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(cells[i].0).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                                    .lineLimit(1).minimumScaleFactor(0.7)
                                Text(cells[i].1).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                            Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                        }
                    }
                }
            }
        }
    }
}

/// 비중 한 줄 — 이름 14 · 금액 15 굵게 · 비율 12, 아래 막대 6.
struct InsightShareRow: View {
    let name: String; let amount: Int64; let frac: Double; let color: Color
    var dot: Color? = nil
    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 0) {
                if let dot { Circle().fill(dot).frame(width: 8, height: 8).padding(.trailing, 8) }
                Text(name).font(.pretendard(size: 14)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                Spacer(minLength: 8)
                Text(won(amount)).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("\(Int(frac * 100))%").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .frame(width: 40, alignment: .trailing)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(hex: 0xFFEDEFF3))
                    Capsule().fill(color).frame(width: geo.size.width * min(max(frac, 0), 1))
                }
            }
            .frame(height: 6)
        }
        .padding(.vertical, 8)
    }
}

// 게임별 월 추이 — 올해, 누적 막대 (상위 5 게임 + 기타). 집계(trend)는 부모가 캐시해 넘긴다.
struct MonthlyTrendSection: View {
    let trend: MonthlyTrend
    let year: Int
    private let etcColor = Color(hex: 0xFFB8BDC6)

    var body: some View {
        let legend = trend.legend
        let monthGame = trend.monthGame.map { $0.mapValues { $0.int64Value } }
        let maxMonth = trend.maxMonth
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        func colorOf(_ g: String) -> Color { g == "기타" ? etcColor : Color(argb64: GameData.shared.colorFor(name: g)) }

        return InsightSection(title: "게임별 월 추이", sub: "\(String(year))년 · 누적") {
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(0..<12, id: \.self) { m in
                    let isCur = now.year == year && now.month == m + 1
                    VStack(spacing: 4) {
                        ZStack(alignment: .bottom) {
                            Color.clear.frame(height: 120)
                            VStack(spacing: 0) {
                                ForEach(legend, id: \.self) { g in
                                    let amt = monthGame[m][g] ?? 0
                                    if amt > 0 {
                                        Rectangle().fill(colorOf(g))
                                            .frame(height: 120 * Double(amt) / Double(maxMonth))
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity).padding(.horizontal, 2)
                            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 3, topTrailingRadius: 3))
                        }
                        Text("\(m + 1)").font(.pretendard(size: 11, weight: isCur ? .bold : .regular))
                            .foregroundStyle(isCur ? GLGColor.textPrimary : GLGColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            FlexibleRow(legend) { g in
                HStack(spacing: 5) {
                    Circle().fill(colorOf(g)).frame(width: 8, height: 8)
                    Text(g).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
            }
            .padding(.top, 14)
        }
    }
}
