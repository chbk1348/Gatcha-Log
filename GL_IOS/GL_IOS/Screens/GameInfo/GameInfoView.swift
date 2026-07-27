import SwiftUI
import Shared

// 게임 정보 탭 — 데일리(노트+출석)·배너/전투/일지·패치·위시·천장·이벤트·정기콘텐츠.
// (Compose GameInfoScreen 대응) ⚠️ chunk ② — 가챠 계산기/리포트/대시보드/프로필/확률표·리딤코드 다이얼로그는 chunk ③.
// HoYoLAB 연동은 네이티브 HoyolabLinkView 를 시트로 호스팅.
struct GameInfoView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var showHoyolab = false
    @State private var showGift = false
    @State private var showDashboard = false
    // 페이지로 분류된 섹션(계산기·리포트·프로필) — 진입 카드 탭 시 푸시.
    @State private var showCalc = false
    @State private var showReport = false
    @State private var showSchedule = false
    @State private var showNews = false
    @State private var showPickups = false
    @State private var showHoyoland = false
    @State private var statChar: EnkaChar? = nil
    @State private var statGame = "genshin"
    @State private var showStats = false
    @State private var rosterGame = "genshin"
    @State private var showRoster = false
    // 공지 상세 — 뉴스 행 탭 시 선택 후 push(destination형 NavigationLink 혼용 버그 회피, 파일 내 다른 페이지와 동일 패턴).
    @State private var selectedNews: NewsItem? = nil
    @State private var showNewsDetail = false
    // Segmented 레이아웃 — 상단 게임 세그먼트 선택값("all" | game.key). 하위 섹션들이 이 값으로 필터된다.
    @State private var gameFilter = "all"

    var body: some View {
        ScrollViewReader { proxy in
        ScrollView {
            // LazyVStack — 화면 밖 섹션(주년·뉴스·게임탭·계산기/리포트 진입)은 스크롤 시 지연 생성.
            // (VStack 이면 탭 전환 순간 7개 섹션 전부를 한꺼번에 빌드해 전환이 버벅였음)
            LazyVStack(alignment: .leading, spacing: 0) {
                // 홈 카드 딥링크 스크롤 앵커 — id 문자열은 Kotlin GameInfoAnchor 의 .name(NOTES/SCHEDULE/NEWS)과 일치해야 함.
                DailyHeroSection(store: store, filter: gameFilter, onConfig: { showHoyolab = true }).id("NOTES")
                // 내 캐릭터(보유 전체 로스터) — 데일리 다음 핵심 콘텐츠로 상단 배치
                // 미연동이면 섹션·상단 여백까지 통째 생략(빈 여백 방지).
                if store.hoyolabConfig.isLinked {
                    section {
                        EnkaCharSection(store: store, filter: gameFilter,
                                        onOpen: { c, g in statChar = c; statGame = g; showStats = true },
                                        onOpenAll: { g in rosterGame = g; showRoster = true },
                                        onOpenHoyolab: { showHoyolab = true })
                    }
                }
                // 통합 게임 일정 — 패치·이벤트·정기 콘텐츠. 게임 분리는 상단 헤더 드롭다운(gameFilter)으로 필터.
                let schedule = ScheduleLogic.shared.buildSchedule(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
                if !schedule.isEmpty {
                    section { GameScheduleSection(entries: schedule, banners: store.activeBanners, filter: gameFilter, onSeeAll: { showSchedule = true }, onSeePickups: { showPickups = true }) }.id("SCHEDULE")
                }
                // 게임 주년 — 지원 게임의 다가오는 주년(임박 순).
                section { AnniversarySection() }
                // 호요랜드 — 호요버스 한국 오프라인 행사(플레이스홀더). 탭하면 예상 장소·지난 행사 상세로.
                section { HoyolandSection(onOpen: { showHoyoland = true }) }
                // 공지·뉴스 — 게임별 최신 공지(탭하면 HoYoLab 열기). 더보기로 전체 페이지.
                section { NewsSection(store: store, filter: gameFilter, onSeeAll: { showNews = true }, onOpenNews: { selectedNews = $0; showNewsDetail = true }) }.id("NEWS")
                if store.hoyolabConfig.isLinked {
                    section { GameTabbedSection(store: store, filter: gameFilter) }.id("COMBAT")
                }
                section { navEntry(icon: "function", title: "가챠 계산기", sub: "재화 환산 · 확률 · 시나리오") { showCalc = true } }
                section { navEntry(icon: "chart.bar.xaxis", title: "가챠 효율 리포트", sub: "UIGF/SRGF 분석 · 단가 · 천장 분포") { showReport = true } }
                Color.clear.frame(height: 12)
            }
            .padding(.horizontal, 16)
            // 넓은 화면(iPad)에서 섹션이 끝까지 늘어나지 않도록 최대폭 제한+중앙정렬(iPhone 영향 없음).
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        // 홈 카드 딥링크 — 진입 시점(onAppear)·이미 떠 있는 상태에서 재요청(onChange) 모두 처리.
        .onAppear { scrollToPendingAnchor(proxy) }
        .onChange(of: store.pendingGameInfoAnchor) { _, _ in scrollToPendingAnchor(proxy) }
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
            // 버튼마다 ToolbarItem 을 따로 두고 사이에 ToolbarSpacer 를 넣는다 —
            // iOS 26 은 인접한 툴바 아이템을 하나의 글래스 캡슐로 묶어버리므로, 스페이서로 갈라야
            // 버튼이 각각 독립된 원형으로 떨어진다. (지출 탭 헤더와 동일)
            if store.hoyolabConfig.isLinked {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showGift = true } label: { Image(systemName: "gift") }
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer(.fixed, placement: .topBarTrailing)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { store.refreshGameInfo(force: true) } label: { Image(systemName: "arrow.clockwise") }
                    .disabled(store.isRefreshing)
            }
            if #available(iOS 26.0, *) {
                ToolbarSpacer(.fixed, placement: .topBarTrailing)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { showHoyolab = true } label: { Image(systemName: "gearshape") }
            }
        }
        .navigationDestination(isPresented: $showHoyolab) {
            HoyolabLinkView(store: store) { showHoyolab = false }
        }
        .navigationDestination(isPresented: $showCalc) { sectionPage("계산기") { GachaCalculatorSection() } }
        .navigationDestination(isPresented: $showReport) { sectionPage("가챠 리포트") { GachaReportSection(store: store, onOpenDashboard: { showDashboard = true }) } }
        .navigationDestination(isPresented: $showGift) { GiftCodePage(store: store) }
        .navigationDestination(isPresented: $showDashboard) { GachaDashboardView(store: store) }
        .navigationDestination(isPresented: $showSchedule) { GameSchedulePage(store: store, filter: gameFilter) }
        .navigationDestination(isPresented: $showNews) { NewsPage(store: store, filter: gameFilter) }
        .navigationDestination(isPresented: $showNewsDetail) {
            if let n = selectedNews { NewsDetailView(store: store, item: n) }
        }
        .navigationDestination(isPresented: $showPickups) { GamePickupPage(store: store, filter: gameFilter) }
        .navigationDestination(isPresented: $showHoyoland) { HoyolandDetailView() }
        .navigationDestination(isPresented: $showStats) { if let c = statChar { EnkaStatPage(char: c, game: statGame) } }
        .navigationDestination(isPresented: $showRoster) {
            EnkaRosterPage(store: store, game: rosterGame)
        }
        }  // ScrollViewReader
    }

    // 대기 중인 앵커가 있으면 해당 섹션으로 스크롤 후 소비(1회성). 탭 전환 직후 레이아웃 완료를 위해 다음 런루프에 실행.
    private func scrollToPendingAnchor(_ proxy: ScrollViewProxy) {
        guard let anchor = store.pendingGameInfoAnchor else { return }
        DispatchQueue.main.async {
            withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(anchor.name, anchor: .top) }
            store.consumeGameInfoAnchor()
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
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // 페이지로 분류된 섹션을 감싸는 페이지 래퍼 — 섹션 자체 헤더를 그대로 쓰고 시스템 뒤로가기 제공.
    @ViewBuilder private func sectionPage<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) { content() }
                .padding(16)
                .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// ── 통합 게임 일정 (패치·이벤트·정기 콘텐츠) ──
// 모델(ScheduleEntry·VersionGroup)과 산출 로직(buildSchedule·filteredPickups·buildVersionGroups)은
// GL_Shared ScheduleLogic 이 단일 소스 — 전반/후반 페이즈 판정이 Android 와 갈리지 않도록.
// 여기엔 SwiftUI 렌더링과 ARGB→Color 변환만 남는다.

/// ForEach 식별자 — (종류, 제목, 대상시각) 조합으로 일정 한 줄을 구분한다.
extension ScheduleEntry: @retroactive Identifiable {
    public var id: String { "\(kind)|\(title)|\(target)" }
}

private func scheduleKindColor(_ kind: String) -> Color {
    Color(argb64: ScheduleLogic.shared.kindColorArgb(kind: kind))
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
private let glCollab = Color(hex: 0xFF6D5AE6)

// 콜라보 배너 표식 — 이름 옆 작은 알약. (스타레일 × Fate 등)
private struct CollabChip: View {
    var body: some View {
        Text("콜라보").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(.white)
            .padding(.horizontal, 6).padding(.vertical, 1).background(glCollab, in: Capsule())
    }
}

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
            .contentShape(Rectangle())
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
                        HStack(spacing: 5) {
                            Text(banner.name).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                            if GameInfoKt.isCollabBanner(banner: banner) { CollabChip() }
                        }
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

private func filteredPickups(_ banners: [GachaBanner], filter: String) -> [GachaBanner] {
    ScheduleLogic.shared.filteredPickups(banners: banners, filter: filter)
}

private func filteredEntries(_ entries: [ScheduleEntry], filter: String) -> [ScheduleEntry] {
    ScheduleLogic.shared.filteredEntries(entries: entries, filter: filter)
}

private struct ScheduleEntryRow: View {
    let e: ScheduleEntry
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let d = Int((e.target - nowMs()) / 86_400_000)
        let urgent = d >= 0 && d <= 3
        let ddColor = urgent ? Color(hex: 0xFFE8634A) : accent.primary
        HStack(spacing: 12) {
            // 게임 태그 — 예전엔 컬러 닷뿐이라 색을 외우지 않으면 어느 게임인지 알 수 없었다.
            GLGGameTag(game: e.gameShort, size: .small)
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

// D-day 링 — 진행률만큼 채운 원형 스트로크 + 중앙 잔여(일/시간). (design_version_card_mockup.html ④)
private struct DdayRing: View {
    let days: Int; let hours: Int; let frac: Double; let color: Color
    var body: some View {
        ZStack {
            Circle().stroke(glTrack, lineWidth: 8)
            Circle().trim(from: 0, to: max(0, min(frac, 1)))
                .stroke(color, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text(days > 0 ? "\(days)일" : "\(hours)시간").font(.pretendard(size: 15, weight: .heavy)).foregroundStyle(color).lineLimit(1)
                if days > 0 { Text("\(hours)시간").font(.pretendard(size: 8)).foregroundStyle(GLGColor.textSecondary).lineLimit(1) }
            }
        }
        .frame(width: 70, height: 70)
    }
}

// 피처드 버전 카드 — 섹션 최상단, 가장 임박한 버전. D-day 링 + 대표 캐릭터(+동반무기) + 경과. (④ 섹션 요약)
private struct FeaturedVersionCard: View {
    let vg: VersionGroup
    var body: some View {
        let c = Color(argb64: vg.game.color)
        let d = Int((vg.nearestEnd - nowMs()) / 86_400_000)
        let urgent = (0...3).contains(d)
        let ddColor = urgent ? glUrgent : c
        let totalH = max((vg.nearestEnd - nowMs()) / 3_600_000, 0)
        let days = Int(totalH / 24); let hours = Int(totalH % 24)
        let lead = vg.pickups.min { $0.endMillis < $1.endMillis }
        let frac: Double = {
            if let lead, lead.startMillis > 0, lead.endMillis > lead.startMillis {
                return min(max(Double(nowMs() - lead.startMillis) / Double(lead.endMillis - lead.startMillis), 0), 1)
            }
            return 0
        }()
        let chars = vg.pickups.filter { $0.type != "weapon" }
        let items = chars.isEmpty ? vg.pickups : chars
        return GLGCard(cornerRadius: 20, padding: 15) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    GLGGameTag(game: vg.game.displayName, size: .small)
                    Text(vg.version.isEmpty ? vg.game.displayName : "버전 \(vg.version)").font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    Spacer(minLength: 8)
                    if urgent {
                        Text("막바지").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 8).padding(.vertical, 3).background(glUrgent, in: Capsule())
                    }
                }
                .padding(.bottom, 12)
                HStack(spacing: 14) {
                    DdayRing(days: days, hours: hours, frac: frac, color: ddColor)
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(items.prefix(2).enumerated()), id: \.offset) { i, it in
                            if i > 0 { Spacer().frame(height: 8) }
                            let isW = it.type == "weapon"
                            let comp = isW ? nil : GameInfoKt.companionWeapons(character: it, all: vg.pickups).first
                            HStack(spacing: 9) {
                                ZStack {
                                    if isW { RoundedRectangle(cornerRadius: 10, style: .continuous).fill(c) } else { Circle().fill(c) }
                                    if isW { SwordShape().fill(.white).frame(width: 15, height: 17) }
                                    else { Text(String(it.name.prefix(1))).font(.pretendard(size: 15, weight: .heavy)).foregroundStyle(.white) }
                                }
                                .frame(width: 34, height: 34)
                                VStack(alignment: .leading, spacing: 1) {
                                    HStack(spacing: 5) {
                                        Text(it.name).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                                        if GameInfoKt.isCollabBanner(banner: it) { CollabChip() }
                                    }
                                    if let comp { Text("＋ \(comp.name)").font(.pretendard(size: 10, weight: .semibold)).foregroundStyle(glWeap).lineLimit(1) }
                                }
                                Spacer(minLength: 0)
                            }
                        }
                        if items.count > 2 {
                            Spacer().frame(height: 6)
                            Text("외 \(items.count - 2)").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
                        }
                        if vg.start > 0 {
                            let vFrac = vg.end > vg.start ? min(max(Double(nowMs() - vg.start) / Double(vg.end - vg.start), 0), 1) : 0
                            Spacer().frame(height: 8)
                            Text("\(DateUtil.shared.shortDate(millis: vg.start)) ~ \(DateUtil.shared.shortDate(millis: vg.end)) · \(Int((vFrac * 100).rounded()))% 경과")
                                .font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                        }
                    }
                }
            }
        }
    }
}

// 슬림 버전 행 — 피처드 아래 나머지 버전(요약 1줄). abbr + "vN · 이름들" + 잔여. (④ 세컨더리)
private struct SlimVersionRow: View {
    let vg: VersionGroup
    var body: some View {
        let c = Color(argb64: vg.game.color)
        let d = Int((vg.nearestEnd - nowMs()) / 86_400_000)
        let ddColor = (0...3).contains(d) ? glUrgent : c
        let chars = vg.pickups.filter { $0.type != "weapon" }
        let weaps = GameInfoKt.unpairedWeapons(all: vg.pickups)
        let names0 = chars.map { $0.name }.joined(separator: " · ")
        let names = names0.isEmpty ? weaps.map { $0.name }.joined(separator: " · ") : names0
        let title = vg.version.isEmpty ? names : "v\(vg.version) · \(names)"
        let counts: String = {
            var p: [String] = []
            if !chars.isEmpty { p.append("캐릭터 \(chars.count)") }
            if !weaps.isEmpty { p.append("무기 \(weaps.count)") }
            return p.joined(separator: " · ")
        }()
        return GLGCard(cornerRadius: 14, padding: 0) {
            HStack(spacing: 10) {
                GLGGameTag(game: vg.game.displayName, size: .small)
                if vg.pickups.contains(where: { GameInfoKt.isCollabBanner(banner: $0) }) { CollabChip() }
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    Text("\(vg.game.displayName) · \(counts)").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 1) {
                    Text(GameInfoKt.dhLabel(targetMillis: vg.nearestEnd, nowMillis: nowMs())).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(ddColor).lineLimit(1)
                    if vg.start > 0 { Text("\(DateUtil.shared.shortDate(millis: vg.start))~\(DateUtil.shared.shortDate(millis: vg.end))").font(.pretendard(size: 9)).foregroundStyle(GLGColor.textSecondary).lineLimit(1) }
                }
            }
            .padding(.horizontal, 13).padding(.vertical, 11)
        }
    }
}

// 컴팩트 버전 섹션(전체 페이지) — 버전 소제목(좌측 틱) + 촘촘한 픽업 행. (③ 전체 페이지)
private struct CompactVersionSection: View {
    let vg: VersionGroup
    var body: some View {
        let c = Color(argb64: vg.game.color)
        let d = Int((vg.nearestEnd - nowMs()) / 86_400_000)
        let ddColor = (0...3).contains(d) ? glUrgent : c
        let chars = vg.pickups.filter { $0.type != "weapon" }
        let weaps = GameInfoKt.unpairedWeapons(all: vg.pickups)
        return GLGCard(cornerRadius: 20, padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 3, height: 14)
                    Text(vg.version.isEmpty ? "\(vg.game.abbr) · \(vg.game.displayName)" : "\(vg.game.abbr) · v\(vg.version)")
                        .font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    Spacer(minLength: 8)
                    VStack(alignment: .trailing, spacing: 1) {
                        Text(GameInfoKt.dhLabel(targetMillis: vg.nearestEnd, nowMillis: nowMs())).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(ddColor).lineLimit(1)
                        if vg.start > 0 { Text("\(DateUtil.shared.shortDate(millis: vg.start))~\(DateUtil.shared.shortDate(millis: vg.end))").font(.pretendard(size: 9)).foregroundStyle(GLGColor.textSecondary).lineLimit(1) }
                    }
                }
                .padding(.bottom, 7)
                ForEach(Array(chars.enumerated()), id: \.offset) { _, b in
                    CompactPickupRow(banner: b, companions: GameInfoKt.companionWeapons(character: b, all: vg.pickups))
                }
                ForEach(Array(weaps.enumerated()), id: \.offset) { _, b in
                    CompactPickupRow(banner: b, companions: [])
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
        }
    }
}

// 컴팩트 픽업 행 — 상단 구분선 + 아바타 30 + 이름(+동반무기) + 잔여. 카드 여백 없이 밀도↑. (③ 전체 페이지)
private struct CompactPickupRow: View {
    let banner: GachaBanner
    var companions: [GachaBanner] = []
    var showCollab: Bool = true
    var body: some View {
        let c = Color(argb64: banner.gameColor)
        let isWeapon = banner.type == "weapon"
        let ddColor = banner.dDay(nowMillis: nowMs()) <= 3 ? glUrgent : c
        let short = GameData.shared.byNameOrNull(name: banner.game)?.shortName ?? banner.game
        return VStack(spacing: 0) {
            Divider()
            HStack(spacing: 10) {
                ZStack {
                    if isWeapon { RoundedRectangle(cornerRadius: 9, style: .continuous).fill(glWeap.opacity(0.16)) } else { Circle().fill(c) }
                    if isWeapon { SwordShape().fill(glWeap).frame(width: 12, height: 14) }
                    else { Text(String(banner.name.prefix(1))).font(.pretendard(size: 13, weight: .heavy)).foregroundStyle(.white) }
                }
                .frame(width: 30, height: 30)
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 4) {
                        Text(banner.name).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                        if showCollab && GameInfoKt.isCollabBanner(banner: banner) { CollabChip() }
                        if let comp = companions.first {
                            Text("＋\(comp.name)").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(glWeap).lineLimit(1)
                        }
                    }
                    Text("\(short) · \(isWeapon ? "무기" : "캐릭터")").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(GameInfoKt.dhLabel(targetMillis: banner.endMillis, nowMillis: nowMs())).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(ddColor).lineLimit(1)
                    Text("~" + DateUtil.shared.shortDate(millis: banner.endMillis)).font(.pretendard(size: 9)).foregroundStyle(GLGColor.textSecondary)
                }
            }
            .padding(.vertical, 7)
        }
    }
}

// 콜라보 강조 카드 — 게임 일정 최상단. 활성 콜라보(스타레일×Fate 등)를 일반 버전과 분리해 부각(보라 accent).
private struct CollabScheduleCard: View {
    let groups: [VersionGroup]
    private var title: String {
        for g in groups { for b in g.pickups { if let t = GameInfoKt.collabTitle(banner: b) { return t } } }
        return "콜라보 픽업"
    }
    var body: some View {
        GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    CollabChip()
                    Text(title).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    Spacer(minLength: 0)
                }
                ForEach(Array(groups.enumerated()), id: \.offset) { _, vg in
                    let chars = vg.pickups.filter { $0.type != "weapon" }
                    let weaps = GameInfoKt.unpairedWeapons(all: vg.pickups)
                    let d = Int((vg.nearestEnd - nowMs()) / 86_400_000)
                    let ddColor = (0...3).contains(d) ? glUrgent : glCollab
                    Spacer().frame(height: 12)
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 2).fill(glCollab).frame(width: 3, height: 14)
                        Text(vg.version.isEmpty ? "\(vg.game.abbr) · \(vg.game.displayName)" : "\(vg.game.abbr) · v\(vg.version)")
                            .font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                        Spacer(minLength: 8)
                        VStack(alignment: .trailing, spacing: 1) {
                            Text(GameInfoKt.dhLabel(targetMillis: vg.nearestEnd, nowMillis: nowMs())).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(ddColor).lineLimit(1)
                            if vg.start > 0 { Text("\(DateUtil.shared.shortDate(millis: vg.start))~\(DateUtil.shared.shortDate(millis: vg.end))").font(.pretendard(size: 9)).foregroundStyle(GLGColor.textSecondary).lineLimit(1) }
                        }
                    }
                    .padding(.bottom, 3)
                    ForEach(Array(chars.enumerated()), id: \.offset) { _, b in
                        CompactPickupRow(banner: b, companions: GameInfoKt.companionWeapons(character: b, all: vg.pickups), showCollab: false)
                    }
                    ForEach(Array(weaps.enumerated()), id: \.offset) { _, b in
                        CompactPickupRow(banner: b, companions: [], showCollab: false)
                    }
                }
            }
        }
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
        let allGroups = buildVersionGroups(banners, filter: filter)
        let collabGroups = ScheduleLogic.shared.collabGroups(groups: allGroups)
        let groups = ScheduleLogic.shared.regularGroups(groups: allGroups)
        let extras = filteredEntries(entries, filter: filter).filter { $0.kind != "패치" }.sorted { $0.target < $1.target }
        let topGroups = Array(groups.prefix(3))
        let topExtras = Array(extras.prefix(2))
        let hiddenMore = (groups.count - topGroups.count) + (extras.count - topExtras.count)
        VStack(alignment: .leading, spacing: 0) {
            Text("게임 일정").font(.pretendard(size: 16, weight: .bold)).padding(.bottom, 4)
            Text("픽업 배너를 버전별로 모았어요. 이벤트·정기 콘텐츠도 함께.").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 12)
            if allGroups.isEmpty && extras.isEmpty {
                GLGCard(cornerRadius: 20, padding: 16) {
                    Text("예정된 일정이 없어요.").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                if !collabGroups.isEmpty {
                    CollabScheduleCard(groups: collabGroups)
                    if !topGroups.isEmpty || !topExtras.isEmpty { Spacer().frame(height: 12) }
                }
                if let featured = topGroups.first {
                    FeaturedVersionCard(vg: featured)
                    ForEach(Array(topGroups.dropFirst().enumerated()), id: \.offset) { _, vg in
                        Spacer().frame(height: 9)
                        SlimVersionRow(vg: vg)
                    }
                }
                if !topExtras.isEmpty {
                    if !topGroups.isEmpty { Spacer().frame(height: 12) }
                    GLGCard(cornerRadius: 20, padding: 16) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text("이벤트 · 정기 콘텐츠").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textPrimary).padding(.bottom, 2)
                            ForEach(Array(topExtras.enumerated()), id: \.element.id) { i, e in
                                ScheduleEntryRow(e: e)
                                if i < topExtras.count - 1 { Divider() }
                            }
                        }
                    }
                }
                if hiddenMore > 0 {
                    Spacer().frame(height: 12)
                    Button(action: onSeeAll) {
                        GLGCard(cornerRadius: 20, padding: 16) {
                            HStack(spacing: 6) {
                                Text("전체 일정 보기").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                                Text("+\(hiddenMore)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary.opacity(0.6))
                                Spacer()
                                Image(systemName: "chevron.right").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                            }
                        }
                        .contentShape(Rectangle())
                    }.buttonStyle(.plain)
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
        let allGroups = buildVersionGroups(store.activeBanners, filter: filter)
        let collabGroups = ScheduleLogic.shared.collabGroups(groups: allGroups)
        let groups = ScheduleLogic.shared.regularGroups(groups: allGroups)
        // 버전 없는 일정(이벤트·정기 콘텐츠)은 하단 별도 카드로. 패치 종료는 버전 카드가 대신하므로 제외.
        let extras = filteredEntries(ScheduleLogic.shared.buildSchedule(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges), filter: filter)
            .filter { $0.kind != "패치" }.sorted { $0.target < $1.target }
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 페이지 타이틀은 네비게이션 바(뒤로가기 + 타이틀)로 — Android 상세 헤더와 동일 형식.
                Text("픽업 배너를 버전별로 모으고, 이벤트·정기 콘텐츠를 아래에 정리했어요.").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 6)
                if allGroups.isEmpty && extras.isEmpty {
                    Text("예정된 일정이 없어요.").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
                } else {
                    if !collabGroups.isEmpty {
                        CollabScheduleCard(groups: collabGroups)
                        if !groups.isEmpty { Spacer().frame(height: 12) }
                    }
                    ForEach(Array(groups.enumerated()), id: \.offset) { i, vg in
                        CompactVersionSection(vg: vg).padding(.top, i > 0 ? 12 : 0)
                    }
                    if !extras.isEmpty {
                        HStack(spacing: 8) {
                            Text("이벤트 · 정기 콘텐츠").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("\(extras.count)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                        }
                        .padding(.top, 18).padding(.bottom, 10)
                        GLGCard(cornerRadius: 20, padding: 16) {
                            VStack(spacing: 0) {
                                ForEach(Array(extras.enumerated()), id: \.element.id) { i, e in
                                    ScheduleEntryRow(e: e)
                                    if i < extras.count - 1 { Divider() }
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("게임 일정")
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
                // 페이지 타이틀은 네비게이션 바(뒤로가기 + 타이틀)로 — Android 상세 헤더와 동일 형식.
                Text("진행 중인 캐릭터·무기 픽업을 종료 임박순으로 모았어요.").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 14)
                if pickups.isEmpty {
                    Text("진행 중인 픽업이 없어요.").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
                } else {
                    GLGCard(cornerRadius: 20, padding: 16) { PickupGroups(pickups: pickups) }
                }
            }
            .padding(16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("전체 픽업")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private func buildVersionGroups(_ banners: [GachaBanner], filter: String) -> [VersionGroup] {
    ScheduleLogic.shared.buildVersionGroups(banners: banners, filter: filter)
}

