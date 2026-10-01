import SwiftUI
import UniformTypeIdentifiers
import Shared

// 가챠 효율 리포트 — UIGF/SRGF JSON 가져오기 + 게임별 단가·출현율·풀별·최근5성. (Compose GachaReportSection 대응)
struct GachaReportSection: View {
    var store: SpendingStore
    let onOpenDashboard: () -> Void
    @Environment(\.glgAccent) private var accent
    @State private var importing = false

    private var stats: GachaStats? { store.gachaStats }
    private var spend: [String: Int64] { store.gachaSpendByGame() }

    // 카드는 걷었다(10/1) — 화면 폭 섹션(좌우 20 · 위 22 · 아래 20), 게임 블록 사이는 GiBand.
    // 틀(sectionPage flat)이 좌우 0 을 주므로 좌우 20 은 섹션이 스스로 둔다. 첫 섹션 위엔 띠가 없다. (Android 와 같다)
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            let games = sortedGames
            reportSection {
                HStack {
                    HStack(spacing: 6) {
                        Text("가챠 효율 리포트").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Text("Beta").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                            .padding(.horizontal, 6).padding(.vertical, 1)
                            .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
                    }
                    Spacer()
                    if stats != nil {
                        Button { store.clearGachaRecords() } label: {
                            Text("초기화").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                        }.buttonStyle(.plain)
                    }
                }
                .padding(.bottom, 18)
                if let s = stats {
                    // 첫 게임은 제목과 같은 섹션 — 대시보드 진입은 여기에만(기존과 같다).
                    if let gk = games.first, let g = s.byGame[gk] { gameCard(gk, g, showDash: true) }
                } else {
                    emptyState
                }
            }
            if let s = stats {
                ForEach(Array(games.dropFirst()), id: \.self) { gk in
                    if let g = s.byGame[gk] {
                        GiBand()
                        reportSection { gameCard(gk, g, showDash: false) }
                    }
                }
                GiBand()
                reportSection { GldsButton(title: "기록 추가 가져오기") { importing = true } }
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json], allowsMultipleSelection: true) { result in
            if case .success(let urls) = result {
                let contents: [String] = urls.compactMap { url in
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    return try? String(contentsOf: url, encoding: .utf8)
                }
                if !contents.isEmpty { store.importGachaFromContents(contents) }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 0) {
            ZStack { Circle().fill(accent.primary.opacity(0.12)).frame(width: 52, height: 52)
                Image(systemName: "square.and.arrow.up").font(.pretendard(size: 24)).foregroundStyle(accent.primary) }
            Text("아직 가챠 기록이 없어요").font(.pretendard(size: 14, weight: .bold)).padding(.top, 12)
            Text("UIGF(원신·젠레스) / SRGF·UIGF(스타레일) 표준 JSON을 가져오면\n5성 단가 · 평균 천장 · 획득 히스토리를 분석해 드려요.")
                .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).multilineTextAlignment(.center).padding(.top, 6)
            GldsButton(title: "가챠 기록 JSON 가져오기") { importing = true }.padding(.top, 16)
        }
        .frame(maxWidth: .infinity)
    }

    // design_gachareport_mockup.html(B) — 게임별 카드(배지+4통계+운분포 바+최근5성), 첫 카드에 대시보드 진입.
    private let lucky = Color(hex: 0xFF2BB673)
    private let gold = Color(hex: 0xFFE0A93B)
    private let unluckyC = Color(hex: 0xFFE8634A)

    private var sortedGames: [String] {
        guard let s = stats else { return [] }
        let order = GachaReport.shared.gameOrder
        return s.byGame.keys.sorted { (order.firstIndex(of: $0) ?? 99) < (order.firstIndex(of: $1) ?? 99) }
    }

    /// 화면 폭 섹션 — 좌우 20 · 위 22 · 아래 20(10/1, Android ReportSection 과 같다).
    private func reportSection<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 20)
    }

    // 카드 면은 걷었다(10/1) — 섹션(reportSection) 안에 그대로 그린다.
    private func gameCard(_ gk: String, _ g: GachaGameStat, showDash: Bool) -> some View {
        let info = gachaGameInfo(gk)
        let sp = spend[gk] ?? 0
        let cost = (sp > 0 && g.five > 0) ? sp / Int64(g.five) : 0
        let dist = g.luckDist.map { Int(truncating: $0) }
        let distTotal = max(dist.reduce(0, +), 1)
        return Group {
            VStack(alignment: .leading, spacing: 0) {
                // 헤더 — 배지 + 게임명(섹션 제목 17)
                HStack(spacing: 10) {
                    Text(reportAbbr(gk)).font(.pretendard(size: 11, weight: .heavy)).foregroundStyle(.white)
                        .padding(.horizontal, 7).padding(.vertical, 4)
                        .background(info.color, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                    Text(info.short).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Spacer()
                }
                .padding(.bottom, 14)
                // 4 통계
                HStack(spacing: 0) {
                    statCol("\(num(Int(g.total)))", "총 뽑기")
                    statCol("\(num(Int(g.five)))", "5성")
                    statCol(g.avgPity > 0 ? "\(g.avgPity)" : "—", "평균 천장", accent.primary)
                    statCol(cost > 0 ? wonShort(cost) : "—", "5성 단가", accent.primary)
                }
                .padding(.bottom, 12)
                // 운 분포 바
                if Int(g.five) > 0 {
                    HStack {
                        Text("운 분포 (천장 구간)").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        Spacer()
                        Text("5성 \(num(Int(g.five)))개").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    }
                    .padding(.bottom, 6)
                    GeometryReader { geo in
                        HStack(spacing: 0) {
                            lucky.frame(width: geo.size.width * CGFloat(dist[0]) / CGFloat(distTotal))
                            gold.frame(width: geo.size.width * CGFloat(dist[1]) / CGFloat(distTotal))
                            unluckyC.frame(width: geo.size.width * CGFloat(dist[2]) / CGFloat(distTotal))
                        }
                    }
                    .frame(height: 8).clipShape(Capsule())
                    HStack(spacing: 12) {
                        legendItem(lucky, "~40 행운"); legendItem(gold, "41~74 평균"); legendItem(unluckyC, "75+ 불운")
                    }
                    .padding(.top, 8)
                }
                // 최근 5성
                if !g.recentFive.isEmpty {
                    Text("최근 5성").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.top, 14).padding(.bottom, 8)
                    FlexibleRow(Array(g.recentFive.enumerated().map { IdxFive(i: $0.offset, name: $0.element.name, pity: Int($0.element.pity)) })) { item in
                        let c: Color = item.pity <= 40 ? lucky : (item.pity >= 75 ? unluckyC : GLGColor.textPrimary)
                        HStack(spacing: 5) {
                            Text(item.name).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("\(item.pity)").font(.pretendard(size: 12, weight: .heavy)).foregroundStyle(c)
                        }
                        .padding(.horizontal, 9).padding(.vertical, 5)
                        .background(Color(hex: 0xFFF3F4F8), in: Capsule())
                    }
                }
                // 대시보드 진입 (첫 블록) — 구분선은 헤어라인 #EEF0F2(10/1)
                if showDash {
                    Rectangle().fill(Color(hex: 0xFFEEF0F2)).frame(height: 1).padding(.top, 13)
                    Button { onOpenDashboard() } label: {
                        HStack {
                            Text("상세 대시보드 (월별·풀별 추이)").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                            Spacer()
                            Image(systemName: "chevron.right").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                        }
                        .padding(.vertical, 12)
                    }.buttonStyle(.plain)
                }
            }
        }
    }

    private func statCol(_ value: String, _ label: String, _ color: Color = GLGColor.textPrimary) -> some View {
        VStack(spacing: 2) {
            // 값 15 굵게 · 라벨 12(10/1) — 지출 인사이트 지표와 같은 규격.
            Text(value).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
    private func legendItem(_ c: Color, _ text: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 8, height: 8)
            Text(text).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
        }
    }
    // 약칭·축약통화는 commonMain(util/Format.kt) 공유 — Android 와 같은 소스.
    private func reportAbbr(_ gk: String) -> String { FormatKt.gachaAbbr(key: gk) }
    private func wonShort(_ v: Int64) -> String { FormatKt.wonShort(v: v) }
}

private struct IdxFive: Hashable { let i: Int; let name: String; let pity: Int }
