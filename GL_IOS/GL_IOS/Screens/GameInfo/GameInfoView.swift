import SwiftUI
import Shared

// 게임 정보 탭 — 데일리(노트+출석)·배너/전투/일지·패치·위시·천장·이벤트·정기콘텐츠.
// (Compose GameInfoScreen 대응) ⚠️ chunk ② — 가챠 계산기/리포트/대시보드/프로필/확률표·리딤코드 다이얼로그는 chunk ③.
// HoYoLAB 연동은 네이티브 HoyolabLinkView 를 시트로 호스팅.
struct GameInfoView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var showHoyolab = false
    @State private var showRate = false
    @State private var showGift = false
    @State private var showDashboard = false
    // 페이지로 분류된 섹션(계산기·리포트·프로필) — 진입 카드 탭 시 푸시.
    @State private var showCalc = false
    @State private var showRecharge = false
    @State private var showReport = false
    @State private var showSchedule = false
    @State private var showPickups = false
    @State private var statChar: EnkaChar? = nil
    @State private var statGame = "genshin"
    @State private var showStats = false
    @State private var rosterGame = "genshin"
    @State private var showRoster = false
    // Segmented 레이아웃 — 상단 게임 세그먼트 선택값("all" | game.key). 하위 섹션들이 이 값으로 필터된다.
    @State private var gameFilter = "all"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                DailyHeroSection(store: store, filter: gameFilter, onConfig: { showHoyolab = true })
                // 내 캐릭터(보유 전체 로스터) — 데일리 다음 핵심 콘텐츠로 상단 배치
                section {
                    EnkaCharSection(store: store, filter: gameFilter,
                                    onOpen: { c, g in statChar = c; statGame = g; showStats = true },
                                    onOpenAll: { g in rosterGame = g; showRoster = true },
                                    onOpenHoyolab: { showHoyolab = true })
                }
                // 통합 게임 일정 — 패치·이벤트·정기 콘텐츠. 게임 분리는 상단 헤더 드롭다운(gameFilter)으로 필터.
                let schedule = buildSchedule(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
                if !schedule.isEmpty {
                    section { GameScheduleSection(entries: schedule, banners: store.activeBanners, filter: gameFilter, onSeeAll: { showSchedule = true }, onSeePickups: { showPickups = true }) }
                }
                section { GameTabbedSection(store: store, filter: gameFilter) }
                section { navEntry(icon: "function", title: "가챠 계산기", sub: "재화 환산 · 확률 · 시뮬레이터 · 플래너") { showCalc = true } }
                section { navEntry(icon: "wonsign.circle", title: "충전 가성비", sub: "충전 패키지 단가 비교 · 첫구매 반영") { showRecharge = true } }
                section { navEntry(icon: "chart.bar.xaxis", title: "가챠 효율 리포트", sub: "UIGF/SRGF 분석 · 단가 · 천장 분포") { showReport = true } }
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
                        Text(gameFilterLabel).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Image(systemName: "chevron.down").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
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
        .navigationDestination(isPresented: $showHoyolab) {
            HoyolabLinkView(store: store) { showHoyolab = false }
        }
        .navigationDestination(isPresented: $showRate) { GachaRatePage() }
        .navigationDestination(isPresented: $showCalc) { sectionPage("계산기") { GachaCalculatorSection() } }
        .navigationDestination(isPresented: $showRecharge) { sectionPage("충전 가성비") { RechargeValueSection() } }
        .navigationDestination(isPresented: $showReport) { sectionPage("가챠 리포트") { GachaReportSection(store: store, onOpenDashboard: { showDashboard = true }) } }
        .navigationDestination(isPresented: $showGift) { GiftCodePage(store: store) }
        .navigationDestination(isPresented: $showDashboard) { GachaDashboardView(store: store) }
        .navigationDestination(isPresented: $showSchedule) { GameSchedulePage(store: store, filter: gameFilter) }
        .navigationDestination(isPresented: $showPickups) { GamePickupPage(store: store, filter: gameFilter) }
        .navigationDestination(isPresented: $showStats) { if let c = statChar { EnkaStatPage(char: c, game: statGame) } }
        .navigationDestination(isPresented: $showRoster) {
            EnkaRosterPage(store: store, game: rosterGame)
        }
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
                        Image(systemName: icon).font(.pretendard(size: 18, weight: .semibold)).foregroundStyle(accent.primary)
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(title).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Text(sub).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1).minimumScaleFactor(0.85)
                    }
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.right").font(.pretendard(size: 14, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                }
            }
        }
        .buttonStyle(.plain)
    }

    // 페이지로 분류된 섹션을 감싸는 페이지 래퍼 — 섹션 자체 헤더를 그대로 쓰고 시스템 뒤로가기 제공.
    @ViewBuilder private func sectionPage<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) { content() }
                .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// ── 통합 게임 일정 (패치·이벤트·정기 콘텐츠) ──
// 패치(게임별 다음 시작/종료)·진행 이벤트·정기 콘텐츠를 하나의 모델로 합쳐 날짜순(임박순)으로 정렬한다.
struct ScheduleEntry: Identifiable {
    let id = UUID()
    let gameKey: String
    let gameShort: String
    let color: Color
    let kind: String          // "패치" | "이벤트" | "콘텐츠"
    let title: String
    let sub: String
    let target: Int64         // 정렬 기준(밀리초): 패치=시작/종료, 이벤트·콘텐츠=종료
    let isStart: Bool         // 패치 시작 여부(라벨·날짜 접두 분기)
}

func buildSchedule(banners: [GachaBanner], events: [GameEvent], challenges: [GameChallenge]) -> [ScheduleEntry] {
    var out: [ScheduleEntry] = []
    // ① 픽업 페이즈 — 게임별로 종료일 기준 페이즈(전반/후반) 분리해 'v6.6 전반 픽업 종료'처럼 표기.
    // (ennead가 버전 종료 시각을 안 줘서 '버전' 대신 '픽업 페이즈' 기준. 전반/후반 판별 = 구 GameBannerCard 로직)
    for game in GameData.shared.games where game.enneadKey != nil {
        let gb = banners.filter { $0.game == game.displayName }
        if gb.isEmpty { continue }
        let color = Color(argb64: game.color)
        let phases = Dictionary(grouping: gb, by: { $0.endMillis }).sorted { $0.key < $1.key }
        let versions = phases.map { $0.value.first?.version ?? "" }
        let lastVersion = versions.last
        var totalByVer: [String: Int] = [:]; for v in versions { totalByVer[v, default: 0] += 1 }
        var seen: [String: Int] = [:]
        for (idx, ph) in phases.enumerated() {
            let v = versions[idx]
            let pos = seen[v] ?? 0; seen[v] = pos + 1
            let phaseLabel: String
            if (totalByVer[v] ?? 1) >= 2 { phaseLabel = pos == 0 ? "전반" : (pos == 1 ? "후반" : "\(pos + 1)페이즈") }
            else { phaseLabel = (v == lastVersion) ? "전반" : "후반" }
            let title = v.isEmpty ? "\(phaseLabel) 픽업 종료" : "v\(v) \(phaseLabel) 픽업 종료"
            out.append(ScheduleEntry(gameKey: game.key, gameShort: game.shortName, color: color, kind: "패치", title: title, sub: "", target: ph.key, isStart: false))
        }
    }
    // ② 진행 중인 이벤트
    for ev in events {
        let g = GameData.shared.byNameOrNull(name: ev.game)
        out.append(ScheduleEntry(gameKey: g?.key ?? ev.game, gameShort: g?.shortName ?? ev.game,
                                 color: Color(argb64: ev.gameColor), kind: "이벤트",
                                 title: ev.name, sub: ev.reward, target: ev.endMillis, isStart: false))
    }
    // ③ 정기 콘텐츠
    for ch in challenges {
        let g = GameData.shared.byNameOrNull(name: ch.game)
        out.append(ScheduleEntry(gameKey: g?.key ?? ch.game, gameShort: g?.shortName ?? ch.game,
                                 color: Color(argb64: ch.gameColor), kind: "콘텐츠",
                                 title: ch.name, sub: ch.reward, target: ch.endMillis, isStart: false))
    }
    return out.sorted { $0.target < $1.target }
}

private func scheduleKindColor(_ kind: String) -> Color {
    switch kind {
    case "패치": return Color(hex: 0xFF6C8AE4)
    case "이벤트": return Color(hex: 0xFFE0A93B)
    default: return Color(hex: 0xFF2BB673)
    }
}

// 무기(검) 아이콘 — SF Symbols에 검 심볼이 없어 커스텀 Shape로 정의(Android SwordIcon과 동일 24×24 좌표).
struct SwordShape: Shape {
    func path(in r: CGRect) -> Path {
        let s = min(r.width, r.height)
        func px(_ v: CGFloat) -> CGFloat { r.minX + v / 24 * s }
        func py(_ v: CGFloat) -> CGFloat { r.minY + v / 24 * s }
        var p = Path()
        // 칼날
        p.move(to: CGPoint(x: px(12), y: py(2)))
        p.addLines([CGPoint(x: px(13.2), y: py(5)), CGPoint(x: px(13.2), y: py(14)), CGPoint(x: px(10.8), y: py(14)), CGPoint(x: px(10.8), y: py(5))])
        p.closeSubpath()
        // 코등이
        p.addRect(CGRect(x: px(8), y: py(14), width: px(16) - px(8), height: py(16) - py(14)))
        // 손잡이
        p.addRect(CGRect(x: px(11.1), y: py(16), width: px(12.9) - px(11.1), height: py(20) - py(16)))
        // 폼멜
        p.addRect(CGRect(x: px(10.4), y: py(20), width: px(13.6) - px(10.4), height: py(22) - py(20)))
        return p
    }
}

private let glChar = Color(hex: 0xFF5B8DEF)
private let glWeap = Color(hex: 0xFFE0883B)
private let glUrgent = Color(hex: 0xFFE8634A)
private let glTrack = Color(hex: 0xFFEDEFF3)
private let glLine = Color(hex: 0xFFE6E7EC)

// 픽업 그룹 헤더 — 종류 배지(캐릭터=블루 / 무기=앰버) + 개수.
private struct PickupGroupHeader: View {
    let isWeapon: Bool
    let count: Int
    var body: some View {
        HStack(spacing: 7) {
            Text(isWeapon ? "무기" : "캐릭터").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(.white)
                .padding(.horizontal, 8).padding(.vertical, 3).background(isWeapon ? glWeap : glChar, in: Capsule())
            Text("\(count)").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
        }
        .padding(.top, 2).padding(.bottom, 10)
    }
}

// "{종류} N개 더보기 ›" 푸터 — 전체 픽업 페이지로.
private struct PickupMoreFooter: View {
    let label: String
    let more: Int
    let onMore: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Button(action: onMore) {
            HStack(spacing: 4) {
                Spacer()
                Text("\(label) \(more)개 더보기").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                Image(systemName: "chevron.right").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                Spacer()
            }
            .padding(.vertical, 9)
        }.buttonStyle(.plain)
    }
}

// 픽업 배너를 캐릭터/무기 2그룹으로 분류. limit != nil이면 그룹별 상위 N개만 + 더보기 푸터.
private struct PickupGroups: View {
    let pickups: [GachaBanner]
    var limit: Int? = nil
    var onMore: (() -> Void)? = nil
    var body: some View {
        let chars = pickups.filter { $0.type != "weapon" }
        let weapons = GameInfoKt.unpairedWeapons(all: pickups)   // 동반 무기는 캐릭터 카드에 접어 표시 → 독립 목록 제외
        VStack(alignment: .leading, spacing: 0) {
            if !chars.isEmpty {
                PickupGroupHeader(isWeapon: false, count: chars.count)
                ForEach(Array((limit != nil ? Array(chars.prefix(limit!)) : chars).enumerated()), id: \.offset) { _, b in
                    PickupItem(banner: b, companions: GameInfoKt.companionWeapons(character: b, all: pickups))
                }
                if let limit, let onMore, chars.count > limit { PickupMoreFooter(label: "캐릭터", more: chars.count - limit, onMore: onMore) }
            }
            if !weapons.isEmpty {
                if !chars.isEmpty { Spacer().frame(height: 16) }
                PickupGroupHeader(isWeapon: true, count: weapons.count)
                ForEach(Array((limit != nil ? Array(weapons.prefix(limit!)) : weapons).enumerated()), id: \.offset) { _, b in PickupItem(banner: b) }
                if let limit, let onMore, weapons.count > limit { PickupMoreFooter(label: "무기", more: weapons.count - limit, onMore: onMore) }
            }
        }
    }
}

// 픽업 아이템 — 좌측 게임색 바 + 아바타 + 이름/버전 + 잔여(dhLabel) + 진행바. (design_pickup_list_final_mockup.html)
private struct PickupItem: View {
    let banner: GachaBanner
    var companions: [GachaBanner] = []
    var body: some View {
        let c = Color(argb64: banner.gameColor)
        let isWeapon = banner.type == "weapon"
        let urgent = banner.dDay(nowMillis: nowMs()) <= 3
        let ddColor = urgent ? glUrgent : c
        let short = GameData.shared.byNameOrNull(name: banner.game)?.shortName ?? banner.game
        let sub = banner.version.isEmpty ? short : "\(short) · v\(banner.version)"
        let hasProg = banner.startMillis > 0 && banner.endMillis > banner.startMillis
        let frac = hasProg ? min(max(Double(nowMs() - banner.startMillis) / Double(banner.endMillis - banner.startMillis), 0), 1) : 0
        return HStack(spacing: 0) {
            Rectangle().fill(c).frame(width: 3)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 11) {
                    ZStack {
                        if isWeapon { RoundedRectangle(cornerRadius: 10, style: .continuous).fill(c) } else { Circle().fill(c) }
                        if isWeapon { SwordShape().fill(.white).frame(width: 14, height: 16) }
                        else { Text(String(banner.name.prefix(1))).font(.pretendard(size: 14, weight: .heavy)).foregroundStyle(.white) }
                    }
                    .frame(width: 34, height: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(banner.name).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                        Text(sub).font(.pretendard(size: 10, weight: .semibold)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                    }
                    Spacer(minLength: 8)
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(GameInfoKt.dhLabel(targetMillis: banner.endMillis, nowMillis: nowMs())).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(ddColor).lineLimit(1)
                        Text("~" + DateUtil.shared.shortDate(millis: banner.endMillis)).font(.pretendard(size: 9)).foregroundStyle(GLGColor.textSecondary)
                    }
                }
                if !companions.isEmpty {
                    Divider().opacity(0.6).padding(.top, 8)
                    ForEach(Array(companions.enumerated()), id: \.offset) { _, w in
                        HStack(spacing: 6) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 6, style: .continuous).fill(glWeap.opacity(0.14))
                                SwordShape().fill(glWeap).frame(width: 9, height: 11)
                            }
                            .frame(width: 18, height: 18)
                            Text("동반 무기 · \(w.name)").font(.pretendard(size: 11, weight: .medium)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                            Spacer(minLength: 0)
                        }
                        .padding(.top, 7)
                    }
                }
                if hasProg {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(glTrack)
                            Capsule().fill(urgent ? glUrgent : c.opacity(0.35)).frame(width: geo.size.width * frac)
                        }
                    }
                    .frame(height: 5).padding(.top, 10)
                    if urgent {
                        HStack {
                            Text("\(Int((frac * 100).rounded()))% 경과 · 막바지").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(glUrgent)
                            Spacer()
                            Text("\(DateUtil.shared.shortDate(millis: banner.startMillis)) → \(DateUtil.shared.shortDate(millis: banner.endMillis))").font(.pretendard(size: 9)).foregroundStyle(GLGColor.textSecondary)
                        }
                        .padding(.top, 5)
                    }
                }
            }
            .padding(.horizontal, 11).padding(.vertical, 10)
        }
        .background(c.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(glLine, lineWidth: 1))
        .padding(.bottom, 9)
    }
}

// 헤더 드롭다운(filter)에 맞춘 픽업 배너 — "all"이면 전체, 특정 게임이면 그 게임만. 종료 임박순.
private func filteredPickups(_ banners: [GachaBanner], filter: String) -> [GachaBanner] {
    let list: [GachaBanner]
    if filter == "all" {
        list = banners
    } else if let g = GameData.shared.games.first(where: { $0.key == filter }) {
        list = banners.filter { $0.game == g.displayName }
    } else {
        list = []
    }
    return list.sorted { $0.endMillis < $1.endMillis }
}

// 헤더 드롭다운(filter)에 맞춘 일정 — "all"이면 전체, 특정 게임이면 그 게임만.
private func filteredEntries(_ entries: [ScheduleEntry], filter: String) -> [ScheduleEntry] {
    filter == "all" ? entries : entries.filter { $0.gameKey == filter }
}

private struct ScheduleEntryRow: View {
    let e: ScheduleEntry
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let d = Int((e.target - nowMs()) / 86_400_000)
        let urgent = d >= 0 && d <= 3
        let ddColor = urgent ? Color(hex: 0xFFE8634A) : accent.primary
        HStack(spacing: 12) {
            Circle().fill(e.color).frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 3) {
                Text(e.kind).font(.pretendard(size: 9, weight: .bold)).foregroundStyle(scheduleKindColor(e.kind))
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(scheduleKindColor(e.kind).opacity(0.13), in: Capsule())
                Text(e.title).font(.pretendard(size: 14, weight: .semibold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                if !e.sub.isEmpty {
                    Text(e.sub).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(GameInfoKt.dhLabel(targetMillis: e.target, nowMillis: nowMs())).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(ddColor).lineLimit(1)
                Text((e.isStart ? "" : "~") + DateUtil.shared.shortDate(millis: e.target)).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
            }
        }
        .padding(.vertical, 11)
    }
}

// 통합 일정 — 헤더 드롭다운(filter)으로 게임 분리. 픽업 배너(단독 디자인) + 상위 3개 일정, 초과 시 전체 페이지로.
struct GameScheduleSection: View {
    let entries: [ScheduleEntry]
    let banners: [GachaBanner]
    let filter: String
    let onSeeAll: () -> Void
    let onSeePickups: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let items = filteredEntries(entries, filter: filter)
        let pickups = filteredPickups(banners, filter: filter)   // 픽업 배너는 전부 노출(압축하지 않음)
        let top = Array(items.prefix(3))
        let hasMore = items.count > 3
        VStack(alignment: .leading, spacing: 0) {
            Text("게임 일정").font(.pretendard(size: 16, weight: .bold)).padding(.bottom, 4)
            Text("픽업 배너와 다가오는 패치·이벤트·콘텐츠를 모았어요.").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 12)
            // 픽업 배너 + 일정을 하나의 통합 카드에 담는다. 픽업 배너는 카드 안에서 단독 디자인(틴트 카드)으로 구분.
            GLGCard(cornerRadius: 20, padding: 16) {
                VStack(spacing: 0) {
                    if !pickups.isEmpty {
                        PickupGroups(pickups: pickups, limit: 3, onMore: onSeePickups)
                        if !top.isEmpty { Divider().padding(.vertical, 12) }
                    }
                    if !top.isEmpty {
                        ForEach(Array(top.enumerated()), id: \.element.id) { i, e in
                            ScheduleEntryRow(e: e)
                            if i < top.count - 1 { Divider() }
                        }
                    } else if pickups.isEmpty {
                        Text("예정된 일정이 없어요.").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8)
                    }
                    if hasMore {
                        Divider()
                        Button(action: onSeeAll) {
                            HStack(spacing: 6) {
                                Text("전체 일정 보기").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                                Text("\(items.count + pickups.count)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary.opacity(0.6))
                                Spacer()
                                Image(systemName: "chevron.right").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                            }
                            .padding(.top, 12)
                        }.buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

// ── 전체 게임 일정 페이지 (헤더 드롭다운 필터 연동, 게임별 그룹 분리) ──
struct GameSchedulePage: View {
    @ObservedObject var store: SpendingStore
    let filter: String

    var body: some View {
        let entries = filteredEntries(buildSchedule(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges), filter: filter)
        let pickups = filteredPickups(store.activeBanners, filter: filter)
        // 게임별로 묶어 표시 — 색상만으로는 구분이 어려워 게임 배지 헤더로 분리.
        let games = GameData.shared.games.filter { g in
            entries.contains { $0.gameKey == g.key } || pickups.contains { $0.game == g.displayName }
        }
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("게임 일정").font(.pretendard(size: 22, weight: .bold)).padding(.bottom, 4)
                Text("픽업 배너와 패치·이벤트·정기 콘텐츠를 게임별로 모았어요.").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 6)
                if games.isEmpty {
                    Text("예정된 일정이 없어요.").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
                } else {
                    ForEach(Array(games.enumerated()), id: \.offset) { _, g in
                        GameScheduleGroup(game: g,
                                          entries: entries.filter { $0.gameKey == g.key },
                                          pickups: pickups.filter { $0.game == g.displayName })
                    }
                }
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationBarTitleDisplayMode(.inline)
    }
}

// ── 픽업 전용 전체 페이지 — 캐릭터/무기 그룹으로만 분리, 종료 임박순. (design_pickup_list_final_mockup.html ②) ──
struct GamePickupPage: View {
    @ObservedObject var store: SpendingStore
    let filter: String

    var body: some View {
        let pickups = filteredPickups(store.activeBanners, filter: filter)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("전체 픽업").font(.pretendard(size: 22, weight: .bold)).padding(.bottom, 4)
                Text("진행 중인 캐릭터·무기 픽업을 종료 임박순으로 모았어요.").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 14)
                if pickups.isEmpty {
                    Text("진행 중인 픽업이 없어요.").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
                } else {
                    GLGCard(cornerRadius: 20, padding: 16) { PickupGroups(pickups: pickups) }
                }
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationBarTitleDisplayMode(.inline)
    }
}

// 게임 단위 그룹 — 게임 배지(약어 색칩 + 게임명) 헤더 + 해당 게임의 픽업/일정 카드.
private struct GameScheduleGroup: View {
    let game: Game
    let entries: [ScheduleEntry]
    let pickups: [GachaBanner]

    var body: some View {
        let c = Color(argb64: game.color)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Text(game.abbr).font(.pretendard(size: 12, weight: .heavy)).foregroundStyle(.white)
                    .frame(minWidth: 30).padding(.horizontal, 8).padding(.vertical, 5)
                    .background(c, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                Text(game.displayName).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            }
            .padding(.top, 16).padding(.bottom, 10)
            GLGCard(cornerRadius: 20, padding: 16) {
                VStack(spacing: 0) {
                    if !pickups.isEmpty {
                        PickupGroups(pickups: pickups)
                        if !entries.isEmpty { Divider().padding(.vertical, 12) }
                    }
                    ForEach(Array(entries.enumerated()), id: \.element.id) { i, e in
                        ScheduleEntryRow(e: e)
                        if i < entries.count - 1 { Divider() }
                    }
                }
            }
        }
    }
}
