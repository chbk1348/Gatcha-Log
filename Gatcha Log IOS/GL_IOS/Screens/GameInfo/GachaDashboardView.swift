import SwiftUI
import Shared

// 가챠 통계 대시보드 — 요약·등급비율·천장분포·월별추이·픽업vs상시·5성 타임라인. (Compose GachaDashboardScreen 대응)
struct GachaDashboardView: View {
    var store: SpendingStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent
    /// 보여 줄 게임 — 리포트에서 누른 게임 블록. 없거나 기록에 없는 게임이면 첫 게임.
    /// 게임은 리포트에서 고르고 들어온다 — 여기서 바꾸는 칩은 걷었다(10/6). 어느 게임인지는 제목이 말한다. (Android 와 같다)
    var initialGame: String? = nil

    private let gold = Color(hex: 0xFFF5B301)
    private let purple = Color(hex: 0xFF9C6ADE)
    private let blue = Color(hex: 0xFF6E8BB5)

    private var games: [String] {
        guard let d = store.gachaDashboard else { return [] }
        let order = GachaReport.shared.gameOrder
        return d.byGame.keys.sorted { (order.firstIndex(of: $0) ?? 99) < (order.firstIndex(of: $1) ?? 99) }
    }
    private var sel: String? {
        if let s = initialGame, games.contains(s) { return s }
        return games.first
    }

    var body: some View {
        Group {
            if let gk = sel, let d = store.gachaDashboard?.byGame[gk] {
                content(gk, d)
            } else {
                Text("가챠 기록을 가져오면\n천장 분포·월별 추이·픽업 비율을 분석해 드려요.")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        // 카드 없이 흰 바탕(10/1) — 섹션이 스스로 좌우 20, 섹션 사이는 GiBand. (Android 와 같다)
        .background(Color.white)
        .glgPageTitle(sel.map { "\(gachaGameInfo($0).short) 가챠 통계" } ?? "가챠 통계")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func content(_ gk: String, _ d: GachaGameDash) -> some View {
        let info = gachaGameInfo(gk)
        let spend = store.gachaSpendByGame()[gk] ?? 0
        let cost = (spend > 0 && d.five > 0) ? spend / Int64(d.five) : 0
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 요약 — 페이지 맨 위 첫 섹션(위 띠 없음)
                dashCard(0) {
                    // 지표는 타일 면 없이 값 15 굵게 · 라벨 12(10/1) — 지출 인사이트와 같은 규격.
                    HStack(spacing: 8) {
                        tile(num(Int(d.total)), "총 뽑기")
                        tile(num(Int(d.five)), "획득 5성")
                        tile(d.avgPity > 0 ? "\(d.avgPity)" : "—", "평균 천장", accent.primary)
                        tile(cost > 0 ? won(cost) : "—", "5성 단가", accent.primary)
                    }
                }
                // 등급 비율
                GiBand()
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
                    GiBand()
                    dashCard(2) {
                        cardTitle("5성 천장 분포", "최소 \(d.minPity) · 평균 \(d.avgPity) · 최대 \(d.maxPity)")
                        barRow(d.pityBuckets.map { Int(truncating: $0) }, ["10","20","30","40","50","60","70","80","90"], info.color).padding(.top, 14)
                        Text("가로축 = 5성이 나온 뽑기 횟수(천장) 구간").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 6)
                    }
                }
                // 월별 추이
                if !d.monthly.isEmpty {
                    GiBand()
                    dashCard(3) {
                        cardTitle("월별 뽑기 추이", "최근 \(d.monthly.count)개월")
                        barRow(d.monthly.map { Int(truncating: ($0.second as NSNumber?) ?? 0) },
                               d.monthly.map { String(((($0.first as? String) ?? "")).suffix(2)) }, accent.primary).padding(.top, 14)
                    }
                }
                // 픽업 vs 상시
                if d.limited + d.standard > 0 {
                    GiBand()
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
                    GiBand()
                    dashCard(5) {
                        cardTitle("5성 타임라인", "최근 획득 순")
                        let shown = Array(d.fiveStars.prefix(30))
                        VStack(spacing: 0) {
                            ForEach(Array(shown.enumerated()), id: \.offset) { i, f in
                                if i > 0 { Rectangle().fill(Color(hex: 0xFFEEF0F2)).frame(height: 1) }
                                fiveRow(f, gk)
                            }
                        }
                        .padding(.top, 10)
                        if d.fiveStars.count > shown.count {
                            Text("외 \(d.fiveStars.count - shown.count)건").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
                        }
                    }
                }
                // 맨 아래 여분 없음 — 마지막 섹션이 아래 20 을 둔다(GLDS 2.0, 10/1).
            }
            // 넓은 창(iPad)에서는 가운데 640 폭으로 모은다 — 설정 · 마이페이지와 같은 규칙.
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
    }

    /// 화면 폭 섹션 — 카드 면은 걷었다(10/1). 좌우 20 · 위 22 · 아래 20, 사이는 GiBand.
    private func dashCard<C: View>(_ index: Int, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 20)
    }
    /// 섹션 제목 17 굵게 · 보조 12(10/1).
    private func cardTitle(_ t: String, _ s: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(t).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            if let s { Text(s).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary) }
        }
    }
    /// 요약 지표 — 타일 면 없이 값 15 굵게 · 라벨 12(10/1).
    private func tile(_ value: String, _ label: String, _ color: Color = GLGColor.textPrimary) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
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
                Text("\(label) \(num(value))").font(.pretendard(size: 13, weight: .bold)).lineLimit(1)
                Text("\(fixed(pct, 1))%").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    private func barRow(_ values: [Int], _ labels: [String], _ color: Color) -> some View {
        let maxV = max(values.max() ?? 0, 1)
        return HStack(alignment: .bottom, spacing: 3) {
            ForEach(Array(values.enumerated()), id: \.offset) { i, v in
                VStack(spacing: 2) {
                    // 차트 글자는 최소 11(10/1) — 8 은 읽히지 않았다.
                    Text(v > 0 ? "\(v)" : "").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1).minimumScaleFactor(0.7)
                    ZStack(alignment: .bottom) {
                        Color.clear.frame(height: 84)
                        let h = v > 0 ? min(max(Double(v)/Double(maxV), 0.04), 1) : 0
                        if h > 0 {
                            RoundedRectangle(cornerRadius: 3).fill(color).frame(height: 84 * h).frame(maxWidth: .infinity).padding(.horizontal, 3)
                        }
                    }
                    Text(i < labels.count ? labels[i] : "").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
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
                Text(poolLabel + (f.time.isEmpty ? "" : " · \(String(f.time.prefix(10)))")).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            }
            Spacer(minLength: 8)
            Text("천장 \(f.pity)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(lc)
                .padding(.horizontal, 8).padding(.vertical, 4).background(lc.opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
        }
        .padding(.vertical, 7)
    }
}
