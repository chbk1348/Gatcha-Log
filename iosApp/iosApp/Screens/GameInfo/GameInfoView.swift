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
    @State private var showSchedule = false
    // Segmented 레이아웃 — 상단 게임 세그먼트 선택값("all" | game.key). 하위 섹션들이 이 값으로 필터된다.
    @State private var gameFilter = "all"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                DailyHeroSection(store: store, filter: gameFilter, onConfig: { showHoyolab = true })
                // 통합 게임 일정 — 패치·이벤트·정기 콘텐츠를 합쳐 데일리 바로 아래 첫 섹션에 노출.
                // 게임 분리는 상단 헤더 드롭다운(gameFilter)으로 필터한다.
                let schedule = buildSchedule(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
                if !schedule.isEmpty {
                    section { GameScheduleSection(entries: schedule, banners: store.activeBanners, filter: gameFilter, onSeeAll: { showSchedule = true }) }
                }
                section { GameTabbedSection(store: store, filter: gameFilter) }
                section { navEntry(icon: "function", title: "가챠 계산기", sub: "재화 환산 · 확률 · 시뮬레이터 · 플래너") { showCalc = true } }
                section { navEntry(icon: "person.crop.square", title: "프로필 쇼케이스", sub: "Enka.Network UID로 캐릭터 조회") { showProfile = true } }
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
        .navigationDestination(isPresented: $showHoyolab) {
            HoyolabLinkView(store: store) { showHoyolab = false }
        }
        .navigationDestination(isPresented: $showRate) { GachaRatePage() }
        .navigationDestination(isPresented: $showCalc) { sectionPage { GachaCalculatorSection() } }
        .navigationDestination(isPresented: $showProfile) { sectionPage { ProfileShowcaseSection(store: store) } }
        .navigationDestination(isPresented: $showReport) { sectionPage { GachaReportSection(store: store, onOpenDashboard: { showDashboard = true }) } }
        .navigationDestination(isPresented: $showGift) { GiftCodePage(store: store) }
        .navigationDestination(isPresented: $showDashboard) { GachaDashboardView(store: store) }
        .navigationDestination(isPresented: $showSchedule) { GameSchedulePage(store: store, filter: gameFilter) }
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
    let now = nowMs()
    var out: [ScheduleEntry] = []
    // ① 패치 — 게임별 다음 시작(미래) 또는 마지막 종료
    for game in GameData.shared.games where game.enneadKey != nil {
        let gb = banners.filter { $0.game == game.displayName }
        if gb.isEmpty { continue }
        let color = Color(argb64: game.color)
        if let f = gb.compactMap({ $0.startMillis > now ? $0.startMillis : nil }).min() {
            let v = gb.first { $0.startMillis == f }?.version ?? ""
            out.append(ScheduleEntry(gameKey: game.key, gameShort: game.shortName, color: color, kind: "패치",
                                     title: v.isEmpty ? "새 버전 시작" : "v\(v) 새 버전 시작", sub: "", target: f, isStart: true))
        } else {
            let end = gb.map { $0.endMillis }.max() ?? 0
            let v = gb.first { $0.endMillis == end }?.version ?? ""
            out.append(ScheduleEntry(gameKey: game.key, gameShort: game.shortName, color: color, kind: "패치",
                                     title: v.isEmpty ? "버전 종료" : "v\(v) 버전 종료", sub: "", target: end, isStart: false))
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

// 픽업 배너 — 일정 목록과 구분되는 단독 디자인(게임색 틴트 카드 + 아이콘 타일 + 픽업 칩).
private struct PickupBannerCard: View {
    let banner: GachaBanner
    var body: some View {
        let c = Color(argb64: banner.gameColor)
        let urgent = banner.dDay(nowMillis: nowMs()) <= 3
        let ddColor = urgent ? Color(hex: 0xFFE8634A) : c
        return HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(c.opacity(0.16)).frame(width: 44, height: 44)
                Image(systemName: banner.type == "weapon" ? "scope" : "person.fill").font(.system(size: 18, weight: .semibold)).foregroundStyle(c)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text("픽업").font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 2).background(c, in: Capsule())
                    Text(banner.type == "weapon" ? "무기" : "캐릭터").font(.system(size: 10, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                    if !banner.version.isEmpty { Text("v\(banner.version)").font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary) }
                }
                Text(banner.name).font(.system(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                Text("~ \(DateUtil.shared.shortDate(millis: banner.endMillis)) 종료").font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary)
            }
            Spacer(minLength: 8)
            Text(banner.endShortLabel(nowMillis: nowMs())).font(.system(size: 12, weight: .bold)).foregroundStyle(ddColor)
                .padding(.horizontal, 9).padding(.vertical, 4).background(ddColor.opacity(0.14), in: Capsule())
        }
        .padding(12)
        .background(c.opacity(0.06), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(c.opacity(0.18), lineWidth: 1))
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
                Text(e.kind).font(.system(size: 9, weight: .bold)).foregroundStyle(scheduleKindColor(e.kind))
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(scheduleKindColor(e.kind).opacity(0.13), in: Capsule())
                Text(e.title).font(.system(size: 14, weight: .semibold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                if !e.sub.isEmpty {
                    Text(e.sub).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(d > 0 ? "D-\(d)" : (d == 0 ? "D-DAY" : "—")).font(.system(size: 15, weight: .bold)).foregroundStyle(ddColor)
                Text((e.isStart ? "" : "~") + DateUtil.shared.shortDate(millis: e.target)).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary)
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
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let items = filteredEntries(entries, filter: filter)
        let allPickups = filteredPickups(banners, filter: filter)
        let pickups = Array(allPickups.prefix(3))   // 섹션은 압축: 픽업 최대 3개
        let top = Array(items.prefix(3))
        let hasMore = items.count > 3 || allPickups.count > pickups.count
        VStack(alignment: .leading, spacing: 0) {
            Text("게임 일정").font(.system(size: 16, weight: .bold)).padding(.bottom, 4)
            Text("픽업 배너와 다가오는 패치·이벤트·콘텐츠를 모았어요.").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 12)
            // 픽업 배너 + 일정을 하나의 통합 카드에 담는다. 픽업 배너는 카드 안에서 단독 디자인(틴트 카드)으로 구분.
            GLGCard(cornerRadius: 20, padding: 16) {
                VStack(spacing: 0) {
                    if !pickups.isEmpty {
                        VStack(spacing: 8) {
                            ForEach(Array(pickups.enumerated()), id: \.offset) { _, b in PickupBannerCard(banner: b) }
                        }
                        if !top.isEmpty { Divider().padding(.vertical, 12) }
                    }
                    if !top.isEmpty {
                        ForEach(Array(top.enumerated()), id: \.element.id) { i, e in
                            ScheduleEntryRow(e: e)
                            if i < top.count - 1 { Divider() }
                        }
                    } else if pickups.isEmpty {
                        Text("예정된 일정이 없어요.").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8)
                    }
                    if hasMore {
                        Divider()
                        Button(action: onSeeAll) {
                            HStack(spacing: 6) {
                                Text("전체 일정 보기").font(.system(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                                Text("\(items.count + allPickups.count)").font(.system(size: 12, weight: .bold)).foregroundStyle(accent.primary.opacity(0.6))
                                Spacer()
                                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                            }
                            .padding(.vertical, 12)
                        }.buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

// ── 전체 게임 일정 페이지 (헤더 드롭다운 필터 연동) ──
struct GameSchedulePage: View {
    @ObservedObject var store: SpendingStore
    let filter: String

    var body: some View {
        let entries = filteredEntries(buildSchedule(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges), filter: filter)
        let pickups = filteredPickups(store.activeBanners, filter: filter)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("게임 일정").font(.system(size: 22, weight: .bold)).padding(.bottom, 4)
                Text("픽업 배너와 패치·이벤트·정기 콘텐츠를 모았어요.").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 14)
                if entries.isEmpty && pickups.isEmpty {
                    Text("예정된 일정이 없어요.").font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
                } else {
                    GLGCard(cornerRadius: 20, padding: 16) {
                        VStack(spacing: 0) {
                            if !pickups.isEmpty {
                                VStack(spacing: 8) {
                                    ForEach(Array(pickups.enumerated()), id: \.offset) { _, b in PickupBannerCard(banner: b) }
                                }
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
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationBarTitleDisplayMode(.inline)
    }
}
