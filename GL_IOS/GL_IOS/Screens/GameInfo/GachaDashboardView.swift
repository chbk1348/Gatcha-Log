import SwiftUI
import Shared

// 가챠 통계 대시보드 — 요약·등급비율·천장분포·월별추이·픽업vs상시·5성 타임라인. (Compose GachaDashboardScreen 대응)
struct GachaDashboardView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent
    @State private var selected: String? = nil
    @State private var appeared: Set<Int> = []

    private let gold = Color(hex: 0xFFF5B301)
    private let purple = Color(hex: 0xFF9C6ADE)
    private let blue = Color(hex: 0xFF6E8BB5)

    private var games: [String] {
        guard let d = store.gachaDashboard else { return [] }
        let order = GachaReport.shared.gameOrder
        return d.byGame.keys.sorted { (order.firstIndex(of: $0) ?? 99) < (order.firstIndex(of: $1) ?? 99) }
    }
    private var sel: String? { selected ?? games.first }

    var body: some View {
        Group {
            if let gk = sel, let d = store.gachaDashboard?.byGame[gk] {
                content(gk, d)
            } else {
                Text("가챠 기록을 가져오면\n천장 분포·월별 추이·픽업 비율을 분석해 드려요.")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(GLGBackground { Color.clear })
        .navigationTitle("가챠 통계")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func content(_ gk: String, _ d: GachaGameDash) -> some View {
        let info = gachaGameInfo(gk)
        let spend = store.gachaSpendByGame()[gk] ?? 0
        let cost = (spend > 0 && d.five > 0) ? spend / Int64(d.five) : 0
        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // 공통 칩 단일 규격 — 게임 선택, 선택색은 게임별 대표색.
                HStack(spacing: 8) {
                    ForEach(games, id: \.self) { g in
                        let gColor = GameData.shared.games.first(where: { $0.key == g }).map { Color(argb64: $0.color) } ?? accent.primary
                        GLGChip(label: gachaGameInfo(g).short, selected: g == sel, color: gColor) { selected = g }
                    }
                    Spacer(minLength: 0)
                }
                // 요약
                dashCard(0) {
                    HStack(spacing: 8) {
                        tile(num(Int(d.total)), "총 뽑기")
                        tile(num(Int(d.five)), "획득 5성")
                        tile(d.avgPity > 0 ? "\(d.avgPity)" : "—", "평균 천장", accent.primary)
                        tile(cost > 0 ? won(cost) : "—", "5성 단가", accent.primary)
                    }
                }
                // 등급 비율
                dashCard(1) {
                    cardTitle("등급 비율", "총 \(num(Int(d.total)))뽑")
                    stackBar([(Int(d.five), gold), (Int(d.four), purple), (Int(d.three), blue)]).padding(.top, 12)
                    HStack(spacing: 8) {
                        legend("5성", Int(d.five), Int(d.total), gold)
                        legend("4성", Int(d.four), Int(d.total), purple)
                        legend("3성", Int(d.three), Int(d.total), blue)
                    }.padding(.top, 12)
                }
                // 천장 분포
                if d.five > 0 {
                    dashCard(2) {
                        cardTitle("5성 천장 분포", "최소 \(d.minPity) · 평균 \(d.avgPity) · 최대 \(d.maxPity)")
                        barRow(d.pityBuckets.map { Int(truncating: $0) }, ["10","20","30","40","50","60","70","80","90"], info.color).padding(.top, 14)
                        Text("가로축 = 5성이 나온 뽑기 횟수(천장) 구간").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).padding(.top, 6)
                    }
                }
                // 월별 추이
                if !d.monthly.isEmpty {
                    dashCard(3) {
                        cardTitle("월별 뽑기 추이", "최근 \(d.monthly.count)개월")
                        barRow(d.monthly.map { Int(truncating: ($0.second as NSNumber?) ?? 0) },
                               d.monthly.map { String(((($0.first as? String) ?? "")).suffix(2)) }, accent.primary).padding(.top, 14)
                    }
                }
                // 픽업 vs 상시
                if d.limited + d.standard > 0 {
                    dashCard(4) {
                        cardTitle("픽업 vs 상시", "한정 풀과 상시 풀 비중")
                        stackBar([(Int(d.limited), accent.primary), (Int(d.standard), Color(hex: 0xFFB8BDC6))]).padding(.top, 12)
                        HStack(spacing: 8) {
                            legend("픽업", Int(d.limited), Int(d.limited + d.standard), accent.primary)
                            legend("상시", Int(d.standard), Int(d.limited + d.standard), Color(hex: 0xFFB8BDC6))
                        }.padding(.top, 12)
                    }
                }
                // 5성 타임라인
                if !d.fiveStars.isEmpty {
                    dashCard(5) {
                        cardTitle("5성 타임라인", "최근 획득 순")
                        let shown = Array(d.fiveStars.prefix(30))
                        VStack(spacing: 0) {
                            ForEach(Array(shown.enumerated()), id: \.offset) { i, f in
                                if i > 0 { Divider().opacity(0.5) }
                                fiveRow(f, gk)
                            }
                        }
                        .padding(.top, 10)
                        if d.fiveStars.count > shown.count {
                            Text("외 \(d.fiveStars.count - shown.count)건").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
                        }
                    }
                }
                Color.clear.frame(height: 8)
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
    }

    private func dashCard<C: View>(_ index: Int, @ViewBuilder _ content: () -> C) -> some View {
        GLGCard(cornerRadius: 20, padding: 16) { VStack(alignment: .leading, spacing: 0) { content() } }
            .glgLoadIn(index, appeared: $appeared)
    }
    private func cardTitle(_ t: String, _ s: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(t).font(.pretendard(size: 14, weight: .bold))
            if let s { Text(s).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary) }
        }
    }
    private func tile(_ value: String, _ label: String, _ color: Color = GLGColor.textPrimary) -> some View {
        VStack(spacing: 3) {
            Text(value).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10).background(accent.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
    }
    private func stackBar(_ segs: [(Int, Color)]) -> some View {
        GeometryReader { geo in
            let total = max(segs.reduce(0) { $0 + $1.0 }, 1)
            HStack(spacing: 0) {
                ForEach(Array(segs.enumerated()), id: \.offset) { _, s in
                    if s.0 > 0 { Rectangle().fill(s.1).frame(width: geo.size.width * Double(s.0) / Double(total)) }
                }
            }
        }
        .frame(height: 14).clipShape(Capsule())
    }
    private func legend(_ label: String, _ value: Int, _ total: Int, _ color: Color) -> some View {
        let pct = total > 0 ? Double(value) * 100 / Double(total) : 0
        return HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(label) \(num(value))").font(.pretendard(size: 11, weight: .bold)).lineLimit(1)
                Text("\(fixed(pct, 1))%").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    private func barRow(_ values: [Int], _ labels: [String], _ color: Color) -> some View {
        let maxV = max(values.max() ?? 0, 1)
        return HStack(alignment: .bottom, spacing: 3) {
            ForEach(Array(values.enumerated()), id: \.offset) { i, v in
                VStack(spacing: 2) {
                    Text(v > 0 ? "\(v)" : "").font(.pretendard(size: 8)).foregroundStyle(GLGColor.textSecondary)
                    ZStack(alignment: .bottom) {
                        Color.clear.frame(height: 84)
                        let h = v > 0 ? min(max(Double(v)/Double(maxV), 0.04), 1) : 0
                        if h > 0 {
                            RoundedRectangle(cornerRadius: 3).fill(color).frame(height: 84 * h).frame(maxWidth: .infinity).padding(.horizontal, 3)
                        }
                    }
                    Text(i < labels.count ? labels[i] : "").font(.pretendard(size: 8)).foregroundStyle(GLGColor.textSecondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
    private func fiveRow(_ f: DashFive, _ gk: String) -> some View {
        let poolLabel = GachaReport.shared.poolLabels[gk]?[f.pool] ?? f.pool
        let lc: Color = f.pity <= 40 ? Color(hex: 0xFF2BB673) : (f.pity >= 75 ? Color(hex: 0xFFE8634A) : accent.primary)
        return HStack {
            VStack(alignment: .leading, spacing: 0) {
                Text(f.name.isEmpty ? "(이름 없음)" : f.name).font(.pretendard(size: 13, weight: .medium)).lineLimit(1)
                Text(poolLabel + (f.time.isEmpty ? "" : " · \(String(f.time.prefix(10)))")).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            }
            Spacer(minLength: 8)
            Text("천장 \(f.pity)").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(lc)
                .padding(.horizontal, 8).padding(.vertical, 4).background(lc.opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
        }
        .padding(.vertical, 7)
    }
}
