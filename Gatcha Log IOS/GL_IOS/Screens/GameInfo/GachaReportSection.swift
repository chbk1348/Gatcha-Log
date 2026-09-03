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

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 6) {
                    Text("가챠 효율 리포트").font(.pretendard(size: 16, weight: .bold))
                    Text("Beta").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(accent.primary)
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
            .padding(.bottom, 12)
            if let s = stats { reportContent(s) } else { GLGCard(cornerRadius: 24, padding: 16) { emptyState } }
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
            GLGButton(title: "가챠 기록 JSON 가져오기") { importing = true }.padding(.top, 16)
        }
        .frame(maxWidth: .infinity)
    }

    // design_gachareport_mockup.html(B) — 게임별 카드(배지+4통계+운분포 바+최근5성), 첫 카드에 대시보드 진입.
    private let lucky = Color(hex: 0xFF2BB673)
    private let gold = Color(hex: 0xFFE0A93B)
    private let unluckyC = Color(hex: 0xFFE8634A)

    private func reportContent(_ s: GachaStats) -> some View {
        let order = GachaReport.shared.gameOrder
        let games = s.byGame.keys.sorted { (order.firstIndex(of: $0) ?? 99) < (order.firstIndex(of: $1) ?? 99) }
        let poolLabels = GachaReport.shared.poolLabels
        return VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(games.enumerated()), id: \.offset) { idx, gk in
                if let g = s.byGame[gk] {
                    gameCard(gk, g, labels: poolLabels[gk] ?? [:], showDash: idx == 0)
                }
            }
            GLGButton(title: "기록 추가 가져오기") { importing = true }
        }
    }

    private func gameCard(_ gk: String, _ g: GachaGameStat, labels: [String: String], showDash: Bool) -> some View {
        let info = gachaGameInfo(gk)
        let sp = spend[gk] ?? 0
        let cost = (sp > 0 && g.five > 0) ? sp / Int64(g.five) : 0
        let dist = g.luckDist.map { Int(truncating: $0) }
        let distTotal = max(dist.reduce(0, +), 1)
        return GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                // 헤더 — 배지 + 게임명
                HStack(spacing: 10) {
                    Text(reportAbbr(gk)).font(.pretendard(size: 11, weight: .heavy)).foregroundStyle(.white)
                        .padding(.horizontal, 7).padding(.vertical, 4)
                        .background(info.color, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                    Text(info.short).font(.pretendard(size: 15, weight: .bold))
                    Spacer()
                }
                .padding(.bottom, 10)
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
                        Text("운 분포 (천장 구간)").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        Spacer()
                        Text("5성 \(num(Int(g.five)))개").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
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
                            Text(item.name).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("\(item.pity)").font(.pretendard(size: 10, weight: .heavy)).foregroundStyle(c)
                        }
                        .padding(.horizontal, 9).padding(.vertical, 5)
                        .background(Color(hex: 0xFFF3F4F8), in: Capsule())
                    }
                }
                // 대시보드 진입 (첫 카드)
                if showDash {
                    Divider().padding(.top, 13)
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
            Text(value).font(.pretendard(size: 20, weight: .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
    private func legendItem(_ c: Color, _ text: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 8, height: 8)
            Text(text).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
        }
    }
    // 약칭·축약통화는 commonMain(util/Format.kt) 공유 — Android 와 같은 소스.
    private func reportAbbr(_ gk: String) -> String { FormatKt.gachaAbbr(key: gk) }
    private func wonShort(_ v: Int64) -> String { FormatKt.wonShort(v: v) }
}

private struct IdxFive: Hashable { let i: Int; let name: String; let pity: Int }
