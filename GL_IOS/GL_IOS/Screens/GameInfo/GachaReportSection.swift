import SwiftUI
import UniformTypeIdentifiers
import Shared

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
                Image(systemName: "square.and.arrow.up").font(.system(size: 24)).foregroundStyle(accent.primary) }
            Text("아직 가챠 기록이 없어요").font(.system(size: 14, weight: .bold)).padding(.top, 12)
            Text("UIGF(원신·젠레스) / SRGF·UIGF(스타레일) 표준 JSON을 가져오면\n5성 단가 · 평균 천장 · 획득 히스토리를 분석해 드려요.")
                .font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).multilineTextAlignment(.center).padding(.top, 6)
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
                    Text(reportAbbr(gk)).font(.system(size: 11, weight: .heavy)).foregroundStyle(.white)
                        .padding(.horizontal, 7).padding(.vertical, 4)
                        .background(info.color, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                    Text(info.short).font(.system(size: 15, weight: .bold))
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
                        Text("운 분포 (천장 구간)").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        Spacer()
                        Text("5성 \(num(Int(g.five)))개").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
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
                    Text("최근 5성").font(.system(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.top, 14).padding(.bottom, 8)
                    FlexibleRow(Array(g.recentFive.enumerated().map { IdxFive(i: $0.offset, name: $0.element.name, pity: Int($0.element.pity)) })) { item in
                        let c: Color = item.pity <= 40 ? lucky : (item.pity >= 75 ? unluckyC : GLGColor.textPrimary)
                        HStack(spacing: 5) {
                            Text(item.name).font(.system(size: 11, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("\(item.pity)").font(.system(size: 10, weight: .heavy)).foregroundStyle(c)
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
                            Text("상세 대시보드 (월별·풀별 추이)").font(.system(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                            Spacer()
                            Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                        }
                        .padding(.vertical, 12)
                    }.buttonStyle(.plain)
                }
            }
        }
    }

    private func statCol(_ value: String, _ label: String, _ color: Color = GLGColor.textPrimary) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 20, weight: .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
    private func legendItem(_ c: Color, _ text: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 8, height: 8)
            Text(text).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary)
        }
    }
    private func reportAbbr(_ gk: String) -> String {
        switch gk { case "genshin": return "GI"; case "hsr", "starrail": return "HSR"; case "zzz": return "ZZZ"; default: return gk.uppercased() }
    }
    private func wonShort(_ v: Int64) -> String {
        v >= 10000 ? "\(fixed(Double(v) / 10000, 1))만" : won(v)
    }
}

private struct IdxFive: Hashable { let i: Int; let name: String; let pity: Int }

private struct IdxStr: Hashable { let i: Int; let s: String }

// 프로필 쇼케이스 — Enka.Network UID 조회. (Compose ProfileShowcaseSection 대응)
struct ProfileShowcaseSection: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var game = "genshin"
    @State private var uid = ""
    @State private var showHoyolab = false
    @State private var loadTask: Task<Void, Never>? = nil

    private let gold = Color(hex: 0xFFD8A12E)
    private let purple = Color(hex: 0xFF9B6BD6)
    private let maxRetries = 2

    // 저장된 enka 조회 UID(과거 조회 시 KMP가 자동 저장) — 1순위.
    private func savedUid(_ g: String) -> String { g == "genshin" ? store.enkaGiUid : store.enkaHsrUid }
    // HoYoLAB 연동으로 가져온 게임 UID — 2순위(저장된 enka UID가 없을 때).
    private func hoyoUid(_ g: String) -> String { g == "genshin" ? store.hoyolabConfig.genshinUid : store.hoyolabConfig.hsrUid }
    private func defaultUid(_ g: String) -> String { let s = savedUid(g); return s.isEmpty ? hoyoUid(g) : s }

    // UID가 있으면 자동 조회. (clearEnkaResult는 비동기 반영이라 enkaResult==nil 가드를 쓰면
    //  탭 전환 시 직전 게임 결과가 아직 남아 조회가 스킵되는 레이스가 생긴다 → 무조건 조회)
    private func autoLoad(_ g: String) {
        let d = defaultUid(g)
        uid = d
        if !d.isEmpty { load(game: g, uid: d) }
    }

    // 타임아웃/네트워크 오류는 일시적이므로 자동 새로고침(재시도) 대상.
    private func isRetriable(_ err: String) -> Bool { err.contains("네트워크") || err.contains("요청이 많") }

    /// Enka 조회 + 타임아웃 워치독 — 조회가 타임아웃/네트워크 오류로 끝나면 자동 재시도(최대 maxRetries).
    private func load(game g: String, uid u: String, attempt: Int = 0) {
        store.loadEnkaProfile(game: g, uid: u)
        loadTask?.cancel()
        loadTask = Task {
            // 조회가 끝날 때까지(로딩 false) 대기 — Net 타임아웃(12s) + 여유.
            for _ in 0..<80 {
                try? await Task.sleep(nanoseconds: 200_000_000)
                if Task.isCancelled { return }
                if !store.enkaLoading { break }
            }
            if Task.isCancelled { return }
            // 타임아웃/네트워크 오류 + 재시도 여유가 있으면 잠시 후 자동 새로고침.
            if let err = store.enkaResult?.error, isRetriable(err), attempt < maxRetries {
                try? await Task.sleep(nanoseconds: 1_200_000_000)
                if Task.isCancelled { return }
                load(game: g, uid: u, attempt: attempt + 1)
            }
        }
    }

    var body: some View {
        let hasUid = !defaultUid(game).isEmpty || !uid.isEmpty
        return VStack(alignment: .leading, spacing: 0) {
            Text("프로필 쇼케이스").font(.system(size: 16, weight: .bold)).padding(.bottom, 12)
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 8) {
                        gameTab("원신", game == "genshin", Color(hex: 0xFF4F8EF7)) { switchGame("genshin") }
                        gameTab("스타레일", game == "hsr", Color(hex: 0xFFB06BFF)) { switchGame("hsr") }
                    }
                    // UID가 없으면 HoYoLAB 연동 진입을 노출(연동하면 UID 자동 확보).
                    if !hasUid {
                        hoyolabPrompt.padding(.top, 14)
                    }
                    HStack(spacing: 8) {
                        TextField("UID 입력 (예: 800000000)", text: $uid).keyboardType(.numberPad).textFieldStyle(.plain).glgPillField()
                            .onChange(of: uid) { uid = $0.filter(\.isNumber) }
                        Button("조회") { load(game: game, uid: uid) }
                            .buttonStyle(.borderedProminent)
                            .tint(accent.primary)
                            .controlSize(.large)
                            .disabled(uid.isEmpty)
                    }
                    .padding(.top, 12)
                    Text("게임 내 '프로필 표시(쇼케이스)'에 올린 캐릭터만 조회돼요. 조회한 UID는 이 기기에 저장돼 다음엔 자동으로 불러와요.")
                        .font(.system(size: 11)).foregroundStyle(Color(.systemGray3)).padding(.top, 8)
                    resultView
                }
            }
        }
        .navigationDestination(isPresented: $showHoyolab) { HoyolabLinkView(store: store) { showHoyolab = false } }
        // 진입 시 저장된/연동 UID가 있으면 자동 조회. (연동 페이지에서 돌아올 때도 재실행)
        .onAppear { autoLoad(game) }
        .onDisappear { loadTask?.cancel() }
    }

    private func switchGame(_ g: String) {
        game = g
        store.clearEnkaResult()
        autoLoad(g)
    }

    // ── UID 없음: HoYoLAB 연동 진입 CTA ──
    private var hoyolabPrompt: some View {
        Button { showHoyolab = true } label: {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous).fill(accent.primary.opacity(0.12)).frame(width: 38, height: 38)
                    Image(systemName: "link").font(.system(size: 16, weight: .semibold)).foregroundStyle(accent.primary)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("HoYoLAB 연동하고 UID 자동 가져오기").font(.system(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text("연동하면 UID 입력 없이 바로 조회돼요").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
                Spacer(minLength: 6)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            }
            .padding(12)
            .background(accent.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(accent.primary.opacity(0.18), lineWidth: 1))
        }.buttonStyle(.plain)
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
