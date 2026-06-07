import SwiftUI
import ComposeApp

// 게임 정보 탭 — 데일리(노트+출석)·배너/전투/일지·패치·위시·천장·이벤트·정기콘텐츠.
// (Compose GameInfoScreen 대응) ⚠️ chunk ② — 가챠 계산기/리포트/대시보드/프로필/확률표·리딤코드 다이얼로그는 chunk ③.
// HoYoLAB 연동은 기존 Compose 화면을 시트로 호스팅(interim).
struct GameInfoView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var showHoyolab = false
    @State private var showRate = false
    @State private var showGift = false
    @State private var showDashboard = false
    // 페이지로 분류된 섹션(계산기·리포트·프로필) — 진입 카드 탭 시 푸시.
    @State private var showCalc = false
    @State private var showReport = false
    @State private var showProfile = false
    // Segmented 레이아웃 — 상단 게임 세그먼트 선택값("all" | game.key). 하위 섹션들이 이 값으로 필터된다.
    @State private var gameFilter = "all"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                DailyHeroSection(store: store, filter: gameFilter, onConfig: { showHoyolab = true })
                section { GameTabbedSection(store: store, filter: gameFilter) }
                if !(store.activeBanners.isEmpty && store.isRefreshing) {
                    section { PatchSection(banners: store.activeBanners) }
                }
                section { PitySection(store: store) }
                section { navEntry(icon: "function", title: "가챠 계산기", sub: "재화 환산 · 확률 · 시뮬레이터 · 플래너") { showCalc = true } }
                section { navEntry(icon: "person.crop.square", title: "프로필 쇼케이스", sub: "Enka.Network UID로 캐릭터 조회") { showProfile = true } }
                section { navEntry(icon: "chart.bar.xaxis", title: "가챠 효율 리포트", sub: "UIGF/SRGF 분석 · 단가 · 천장 분포") { showReport = true } }
                if !store.challenges.isEmpty { section { ChallengeSection(challenges: store.challenges) } }
                if !store.gameEvents.isEmpty { section { EventSection(events: store.gameEvents) } }
                Color.clear.frame(height: 12)
            }
            .padding(.horizontal, 16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .refreshable { store.refreshGameInfo(force: true) }
        // 초기 진입 시 로드 + HoYoLAB 연동(config)이 늦게 링크되면 그 순간 강제 갱신(실시간 노트 표출)
        .task { store.refreshGameInfo() }
        .onChange(of: store.hoyolabConfig.isLinked) { _, linked in
            if linked { store.refreshGameInfo(force: true) }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Menu {
                    Picker("게임 선택", selection: $gameFilter) {
                        Text("전체").tag("all")
                        ForEach(Array(GameData.shared.attendanceGames.enumerated()), id: \.offset) { _, g in
                            Text(g.shortName).tag(g.key)
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(gameFilterLabel).font(.system(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Image(systemName: "chevron.down").font(.system(size: 12, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                    }
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { showRate = true } label: { Image(systemName: "percent") }
                if store.hoyolabConfig.isLinked { Button { showGift = true } label: { Image(systemName: "gift") } }
                Button { store.refreshGameInfo(force: true) } label: { Image(systemName: "arrow.clockwise") }
                    .disabled(store.isRefreshing)
                Button { showHoyolab = true } label: { Image(systemName: "gearshape") }
            }
        }
        .glgToast(message: store.statusMessage, bottomPadding: 14) { store.clearStatus() }
        .navigationDestination(isPresented: $showHoyolab) {
            HoyolabLinkView(store: store) { showHoyolab = false }
        }
        .navigationDestination(isPresented: $showRate) { GachaRatePage() }
        .navigationDestination(isPresented: $showCalc) { sectionPage { GachaCalculatorSection(store: store) } }
        .navigationDestination(isPresented: $showProfile) { sectionPage { ProfileShowcaseSection(store: store) } }
        .navigationDestination(isPresented: $showReport) { sectionPage { GachaReportSection(store: store, onOpenDashboard: { showDashboard = true }) } }
        .navigationDestination(isPresented: $showGift) { GiftCodePage(store: store) }
        .navigationDestination(isPresented: $showDashboard) { GachaDashboardView(store: store) }
    }

    // 헤더 좌측 게임 드롭다운 라벨
    private var gameFilterLabel: String {
        gameFilter == "all" ? "전체" : (GameData.shared.attendanceGames.first { $0.key == gameFilter }?.shortName ?? "전체")
    }

    @ViewBuilder private func section<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        Spacer().frame(height: 20)
        content()
    }

    // 페이지 진입 카드 — 아이콘 + 제목 + 설명 + 셰브론(글래스 카드).
    @ViewBuilder private func navEntry(icon: String, title: String, sub: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            GLGCard(cornerRadius: 20, padding: 16) {
                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous).fill(accent.primary.opacity(0.12)).frame(width: 44, height: 44)
                        Image(systemName: icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(accent.primary)
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(title).font(.system(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Text(sub).font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1).minimumScaleFactor(0.85)
                    }
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                }
            }
        }
        .buttonStyle(.plain)
    }

    // 페이지로 분류된 섹션을 감싸는 페이지 래퍼 — 섹션 자체 헤더를 그대로 쓰고 시스템 뒤로가기 제공.
    @ViewBuilder private func sectionPage<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) { content() }
                .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationBarTitleDisplayMode(.inline)
    }
}

// ── 패치 일정 ──
struct PatchSection: View {
    let banners: [GachaBanner]
    @Environment(\.glgAccent) private var accent

    private struct PatchItem: Identifiable {
        let id = UUID(); let game: String; let version: String; let target: Int64; let isStart: Bool; let color: Color
    }
    private var patches: [PatchItem] {
        let now = nowMs()
        return GameData.shared.games.filter { $0.enneadKey != nil }.compactMap { game in
            let gb = banners.filter { $0.game == game.displayName }
            if gb.isEmpty { return nil }
            let future = gb.compactMap { $0.startMillis > now ? $0.startMillis : nil }.min()
            if let f = future {
                let v = gb.first { $0.startMillis == f }?.version ?? ""
                return PatchItem(game: game.displayName, version: v, target: f, isStart: true, color: Color(argb64: game.color))
            } else {
                let end = gb.map { $0.endMillis }.max() ?? 0
                let v = gb.first { $0.endMillis == end }?.version ?? ""
                return PatchItem(game: game.displayName, version: v, target: end, isStart: false, color: Color(argb64: game.color))
            }
        }
    }

    var body: some View {
        let items = patches
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Text("패치 일정").font(.system(size: 16, weight: .bold)).padding(.bottom, 4)
                Text("게임 버전 업데이트의 시작·종료까지 남은 기간이에요.").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 12)
                GLGCard(cornerRadius: 20, padding: 16) {
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.element.id) { i, p in
                            let d = Int((p.target - nowMs()) / 86_400_000)
                            HStack {
                                HStack(spacing: 8) {
                                    Circle().fill(p.color).frame(width: 8, height: 8)
                                    VStack(alignment: .leading, spacing: 0) {
                                        Text("\(GameData.shared.byName(name: p.game).shortName) " +
                                             (p.version.isEmpty ? "" : "v\(p.version) ") + (p.isStart ? "새 버전 시작" : "버전 종료"))
                                            .font(.system(size: 14, weight: .bold)).lineLimit(1)
                                        Text(DateUtil.shared.shortLabelWithWeekday(millis: p.target)).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                                    }
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 0) {
                                    Text(d > 0 ? "D-\(d)" : (d == 0 ? "D-DAY" : "—")).font(.system(size: 15, weight: .bold)).foregroundStyle(accent.primary)
                                    Text(p.isStart ? "시작까지" : "종료까지").font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary)
                                }
                            }
                            .padding(.vertical, 10)
                            if i < items.count - 1 { Divider() }
                        }
                    }
                }
            }
        }
    }
}

// ── 이벤트 / 정기 콘텐츠 ──
struct EventSection: View {
    let events: [GameEvent]
    var body: some View {
        ScheduleGrouped(title: "진행 중인 이벤트",
                        byGame: Dictionary(grouping: events, by: { $0.game })) { ev in
            ScheduleRow(name: ev.name, sub: ev.reward, endMillis: ev.endMillis, dday: ev.dDayLabel(nowMillis: nowMs()))
        }
    }
}

struct ChallengeSection: View {
    let challenges: [GameChallenge]
    var body: some View {
        ScheduleGrouped(title: "정기 콘텐츠",
                        byGame: Dictionary(grouping: challenges, by: { $0.game })) { ch in
            ScheduleRow(name: ch.name, sub: ch.reward, endMillis: ch.endMillis, dday: ch.dDayLabel(nowMillis: nowMs()))
        }
    }
}

private struct ScheduleGrouped<T, Row: View>: View {
    let title: String
    let byGame: [String: [T]]
    @ViewBuilder let row: (T) -> Row

    var body: some View {
        let gamesWithData = GameData.shared.games.filter { byGame[$0.displayName] != nil }
        return VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.system(size: 16, weight: .bold)).padding(.bottom, 12)
            GLGCard(cornerRadius: 20, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(gamesWithData.enumerated()), id: \.offset) { gi, game in
                        if gi > 0 { Divider().padding(.vertical, 10) }
                        HStack(spacing: 7) {
                            Circle().fill(Color(argb64: game.color)).frame(width: 8, height: 8)
                            Text(game.shortName).font(.system(size: 13, weight: .bold)).foregroundStyle(Color(argb64: game.color))
                        }
                        .padding(.vertical, 2)
                        ForEach(Array((byGame[game.displayName] ?? []).prefix(6).enumerated()), id: \.offset) { _, item in
                            row(item)
                        }
                    }
                }
            }
        }
    }
}

struct ScheduleRow: View {
    let name: String; let sub: String; let endMillis: Int64; let dday: String
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 0) {
                Text(name).font(.system(size: 13, weight: .medium)).lineLimit(1)
                if !sub.isEmpty { Text(sub).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1) }
            }
            Spacer(minLength: 10)
            VStack(alignment: .trailing, spacing: 0) {
                Text("~ \(DateUtil.shared.shortDate(millis: endMillis))").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                Text(dday).font(.system(size: 13, weight: .bold)).foregroundStyle(accent.primary)
            }
        }
        .padding(.vertical, 9)
    }
}
