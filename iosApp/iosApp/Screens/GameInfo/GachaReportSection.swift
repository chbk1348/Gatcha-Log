import SwiftUI
import UniformTypeIdentifiers
import ComposeApp

// 가챠 효율 리포트 — UIGF/SRGF JSON 가져오기 + 게임별 단가·출현율·풀별·최근5성. (Compose GachaReportSection 대응)
struct GachaReportSection: View {
    @ObservedObject var store: SpendingStore
    let onOpenDashboard: () -> Void
    @Environment(\.glgAccent) private var accent
    @State private var importing = false

    private var stats: GachaStats? { store.gachaStats }
    private var spend: [String: Int64] { store.gachaSpendByGame() }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 6) {
                    Text("가챠 효율 리포트").font(.system(size: 16, weight: .bold))
                    Text("Beta").font(.system(size: 9, weight: .bold)).foregroundStyle(accent.primary)
                        .padding(.horizontal, 6).padding(.vertical, 1)
                        .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
                }
                Spacer()
                if stats != nil {
                    Button { store.clearGachaRecords() } label: {
                        Text("초기화").font(.system(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    }.buttonStyle(.plain)
                }
            }
            .padding(.bottom, 12)
            GLGCard(cornerRadius: 24, padding: 16) {
                if let s = stats { reportContent(s) } else { emptyState }
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
                Image(systemName: "square.and.arrow.up").font(.system(size: 24)).foregroundStyle(accent.primary) }
            Text("아직 가챠 기록이 없어요").font(.system(size: 14, weight: .bold)).padding(.top, 12)
            Text("UIGF(원신·젠레스) / SRGF·UIGF(스타레일) 표준 JSON을 가져오면\n5성 단가 · 평균 천장 · 획득 히스토리를 분석해 드려요.")
                .font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).multilineTextAlignment(.center).padding(.top, 6)
            GLGButton(title: "가챠 기록 JSON 가져오기") { importing = true }.padding(.top, 16)
        }
        .frame(maxWidth: .infinity)
    }

    private func reportContent(_ s: GachaStats) -> some View {
        // 요약 집계
        var totalPulls = 0, totalFive = 0, totalFour = 0, totalSpend: Int64 = 0, fiveForCost = 0
        for (gk, g) in s.byGame {
            totalPulls += Int(g.total); totalFive += Int(g.five); totalFour += Int(g.four)
            let sp = spend[gk] ?? 0
            if sp > 0 && g.five > 0 { totalSpend += sp; fiveForCost += Int(g.five) }
        }
        let overallCost = fiveForCost > 0 ? totalSpend / Int64(fiveForCost) : 0
        let order = GachaReport.shared.gameOrder
        let games = s.byGame.keys.sorted { (order.firstIndex(of: $0) ?? 99) < (order.firstIndex(of: $1) ?? 99) }
        let poolLabels = GachaReport.shared.poolLabels
        let poolOrder = GachaReport.shared.poolOrder

        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                summaryStat(num(totalPulls), "총 뽑기", nil)
                summaryStat(num(totalFive), "획득 5성", "4성 \(num(totalFour))")
                summaryStat(overallCost > 0 ? won(overallCost) : "—", "5성 평균 단가", "월정액 제외", accent.primary)
            }
            .padding(.bottom, 14)
            ForEach(Array(games.enumerated()), id: \.offset) { idx, gk in
                if let g = s.byGame[gk] {
                    if idx > 0 { Divider().padding(.vertical, 12) }
                    gameReport(gk, g, poolLabels[gk] ?? [:], poolOrder[gk] ?? Array(g.pools.keys))
                }
            }
            GLGOutlineButton(title: "📊 통계 대시보드 열기") { onOpenDashboard() }.padding(.top, 14)
            GLGButton(title: "기록 추가 가져오기") { importing = true }.padding(.top, 10)
        }
    }

    private func gameReport(_ gk: String, _ g: GachaGameStat, _ labels: [String: String], _ pOrder: [String]) -> some View {
        let info = gachaGameInfo(gk)
        let sp = spend[gk] ?? 0
        let cost = (sp > 0 && g.five > 0) ? sp / Int64(g.five) : 0
        let fiveRate = g.total > 0 ? Double(g.five) * 100.0 / Double(g.total) : 0
        let pools = g.pools.keys.sorted { (pOrder.firstIndex(of: $0) ?? 99) < (pOrder.firstIndex(of: $1) ?? 99) }
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Circle().fill(info.color).frame(width: 9, height: 9)
                Text(info.short).font(.system(size: 15, weight: .bold))
                Text("\(num(Int(g.total)))뽑 · 5성 \(num(Int(g.five)))").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            }
            .padding(.bottom, 8)
            HStack(spacing: 14) {
                if cost > 0 { metaItem("5성 단가", won(cost)) }
                metaItem("5성 출현율", "\(fixed(fiveRate, 2))%")
                if g.avgPity > 0 { metaItem("평균 천장", "\(g.avgPity)") }
            }
            .padding(.bottom, 10)
            ForEach(Array(pools.enumerated()), id: \.offset) { _, pk in
                if let p = g.pools[pk] {
                    HStack {
                        Text(labels[pk] ?? pk).font(.system(size: 12, weight: .medium)).lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                        Text("\(p.total)뽑 · 5성 \(p.five)" + (p.avgPity > 0 ? " · 평균 \(p.avgPity)" : ""))
                            .font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                        Text("천장 \(p.pity)").font(.system(size: 10, weight: .bold)).foregroundStyle(info.color)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(info.color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
                    }
                    .padding(.vertical, 5)
                }
            }
            if !g.recentFive.isEmpty {
                Text("최근 5성").font(.system(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
                let chips = g.recentFive.map { "\($0.name) (\(labels[$0.pool] ?? "") \($0.pity))" }
                FlexibleRow(Array(chips.enumerated().map { IdxStr(i: $0.offset, s: $0.element) })) { item in
                    Text(item.s).font(.system(size: 11)).foregroundStyle(GLGColor.textPrimary)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Color.black.opacity(0.03), in: RoundedRectangle(cornerRadius: 8))
                }
                .padding(.top, 6)
            }
        }
    }

    private func summaryStat(_ value: String, _ label: String, _ sub: String?, _ color: Color = GLGColor.textPrimary) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 16, weight: .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            if let sub { Text(sub).font(.system(size: 9)).foregroundStyle(Color(.systemGray3)) }
        }
        .frame(maxWidth: .infinity).padding(.vertical, 11).padding(.horizontal, 8)
        .background(Color.black.opacity(0.03), in: RoundedRectangle(cornerRadius: 12))
    }
    private func metaItem(_ label: String, _ value: String) -> some View {
        HStack(spacing: 4) {
            Text(label).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
            Text(value).font(.system(size: 12, weight: .bold))
        }
    }
}

private struct IdxStr: Hashable { let i: Int; let s: String }

// 프로필 쇼케이스 — Enka.Network UID 조회. (Compose ProfileShowcaseSection 대응)
struct ProfileShowcaseSection: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var game = "genshin"
    @State private var uid = ""

    private let gold = Color(hex: 0xFFD8A12E)
    private let purple = Color(hex: 0xFF9B6BD6)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("프로필 쇼케이스").font(.system(size: 16, weight: .bold)).padding(.bottom, 12)
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 8) {
                        gameTab("원신", game == "genshin", Color(hex: 0xFF4F8EF7)) { game = "genshin"; uid = store.enkaGiUid; store.clearEnkaResult() }
                        gameTab("스타레일", game == "hsr", Color(hex: 0xFFB06BFF)) { game = "hsr"; uid = store.enkaHsrUid; store.clearEnkaResult() }
                    }
                    HStack(spacing: 8) {
                        TextField("UID 입력 (예: 800000000)", text: $uid).keyboardType(.numberPad).textFieldStyle(.plain).glgPillField()
                            .onChange(of: uid) { uid = $0.filter(\.isNumber) }
                        Button("조회") { store.loadEnkaProfile(game: game, uid: uid) }
                            .buttonStyle(.borderedProminent)
                            .tint(accent.primary)
                            .controlSize(.large)
                    }
                    .padding(.top, 12)
                    Text("게임 내 '프로필 표시(쇼케이스)'에 올린 캐릭터만 조회돼요. UID는 이 기기에만 저장됩니다.")
                        .font(.system(size: 11)).foregroundStyle(Color(.systemGray3)).padding(.top, 8)
                    resultView
                }
            }
        }
        .onAppear { uid = game == "genshin" ? store.enkaGiUid : store.enkaHsrUid }
    }

    @ViewBuilder private var resultView: some View {
        if store.enkaLoading {
            ProgressView().tint(accent.primary).frame(maxWidth: .infinity).padding(.vertical, 16)
        } else if let err = store.enkaResult?.error {
            Text(err).font(.system(size: 13)).foregroundStyle(Color(hex: 0xFFDC2626)).padding(.top, 14)
        } else if let p = store.enkaResult?.profile {
            VStack(alignment: .leading, spacing: 0) {
                Divider().padding(.vertical, 14)
                Text(p.nickname.isEmpty ? "이름 없음" : p.nickname).font(.system(size: 17, weight: .bold))
                Text("Lv.\(p.level) · 월드 레벨 \(p.worldLevel)").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
                if !p.signature.isEmpty { Text(p.signature).font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4) }
                if p.chars.isEmpty {
                    Text("쇼케이스에 등록된 캐릭터가 없어요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 14)
                } else {
                    let cols = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]
                    LazyVGrid(columns: cols, spacing: 8) {
                        ForEach(Array(p.chars.enumerated()), id: \.offset) { _, c in charCard(c) }
                    }
                    .padding(.top, 14)
                }
            }
        }
    }

    private func charCard(_ c: EnkaChar) -> some View {
        let color = c.rarity >= 5 ? gold : purple
        let rankLabel: String? = game == "genshin"
            ? (c.rank < 0 ? nil : (c.rank == 0 ? "명함" : "\(c.rank)돌"))
            : (c.rank > 0 ? "\(c.rank)성혼" : nil)
        return VStack(spacing: 6) {
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(color.opacity(0.18)).aspectRatio(1, contentMode: .fit)
                if let icon = c.iconUrl, let u = URL(string: icon) {
                    AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                } else {
                    Text(String(c.name.prefix(1))).font(.system(size: 22, weight: .bold)).foregroundStyle(color)
                }
                VStack {
                    HStack {
                        Spacer()
                        if let r = rankLabel {
                            Text(r).font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                                .padding(.horizontal, 5).padding(.vertical, 1).background(color)
                                .clipShape(RoundedCorner(radius: 8, corners: [.bottomLeft]))
                        }
                    }
                    Spacer()
                    HStack {
                        Text("Lv.\(c.level)").font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 5).padding(.vertical, 1).background(Color.black.opacity(0.8))
                            .clipShape(RoundedCorner(radius: 8, corners: [.topRight]))
                        Spacer()
                    }
                }
            }
            Text(c.name).font(.system(size: 12, weight: .bold)).lineLimit(1)
            HStack(spacing: 0) {
                Text(String(repeating: "★", count: min(max(Int(c.rarity), 1), 5))).font(.system(size: 9)).foregroundStyle(color)
                if !c.element.isEmpty { Text(" · \(c.element)").font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1) }
            }
        }
        .padding(8)
        .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
    }

    private func gameTab(_ label: String, _ selected: Bool, _ color: Color, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 12, weight: .bold)).foregroundStyle(selected ? .white : GLGColor.textSecondary)
                .padding(.horizontal, 14).padding(.vertical, 6)
                .background(selected ? color : Color(hex: 0xFFF2F2F6), in: Capsule())
        }.buttonStyle(.plain)
    }
}

/// 특정 모서리만 둥글게 (배지용).
struct RoundedCorner: Shape {
    var radius: CGFloat = 8
    var corners: UIRectCorner = .allCorners
    func path(in rect: CGRect) -> Path {
        Path(UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius)).cgPath)
    }
}
