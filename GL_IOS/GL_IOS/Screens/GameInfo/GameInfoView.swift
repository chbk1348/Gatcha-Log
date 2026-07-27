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

    /// 알림으로 요청된 공지가 목록에 도착했으면 상세를 연다.
    private func openPendingNewsIfReady() {
        guard let id = store.pendingNewsId,
              let target = store.gameNews.first(where: { $0.id == id }) else { return }
        store.consumePendingNews()
        selectedNews = target
        showNewsDetail = true
    }

    var body: some View {
        ScrollViewReader { proxy in
        ScrollView {
            // LazyVStack — 화면 밖 섹션(주년·뉴스·게임탭·계산기/리포트 진입)은 스크롤 시 지연 생성.
            // (VStack 이면 탭 전환 순간 7개 섹션 전부를 한꺼번에 빌드해 전환이 버벅였음)
            LazyVStack(alignment: .leading, spacing: 0) {
                // 홈 카드 딥링크 스크롤 앵커 — id 문자열은 Kotlin GameInfoAnchor 의 .name(NOTES/SCHEDULE/NEWS)과 일치해야 함.
                DailyHeroSection(store: store, filter: gameFilter, onConfig: { showHoyolab = true }).id("NOTES")
                // 숙제 완주율 — 데일리 바로 아래(같은 '오늘 뭐 했나' 맥락). 기록이 없으면 섹션 자체가 안 뜬다.
                if !store.taskStats.isEmpty {
                    section { TaskCompletionSection(stats: store.taskStats) }
                }
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
                    section { GameScheduleSection(entries: schedule, banners: store.activeBanners, filter: gameFilter, onSeeAll: { showSchedule = true }) }.id("SCHEDULE")
                }
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
        // 공지 알림 딥링크 — 알림에 실린 id 로 목록에서 글을 찾아 상세를 연다.
        // 콜드 스타트면 목록이 아직 비어 있어, 도착(gameNews 갱신)할 때까지 기다렸다 연다.
        .onAppear { openPendingNewsIfReady() }
        .onChange(of: store.pendingNewsId) { _, _ in openPendingNewsIfReady() }
        .onChange(of: store.gameNews) { _, _ in openPendingNewsIfReady() }
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
// ── 섹션 진입 카드 ──────────────────────────────────────────────────────────

/// 게임 정보 탭의 '게임 일정' 섹션 — 호요랜드 카드와 같은 규격의 진입 카드 한 장.
/// 게임당 한 줄만 남기고(색 바 + 게임명 + 요약 + 잔여), 자세한 목록은 탭해서 상세로 간다.
struct GameScheduleSection: View {
    let entries: [ScheduleEntry]
    let banners: [GachaBanner]
    let filter: String
    let onSeeAll: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let lines = ScheduleLogic.shared.gameLines(banners: banners, filter: filter, nowMillis: nowMs())
        let summary = ScheduleLogic.shared.summarize(banners: banners, entries: entries, filter: filter, nowMillis: nowMs())
        VStack(alignment: .leading, spacing: 0) {
            Text("게임 일정").font(.pretendard(size: 16, weight: .bold)).padding(.bottom, 4)
            Text("픽업 배너와 이벤트 마감을 한곳에서.")
                .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 12)
            Button(action: onSeeAll) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 13, style: .continuous).fill(accent.primary.opacity(0.12))
                            Image(systemName: "calendar").font(.pretendard(size: 20, weight: .semibold))
                                .foregroundStyle(accent.primary)
                        }
                        .frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("픽업 · 이벤트 · 정기 콘텐츠")
                                .font(.pretendard(size: 14.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                            Text(summaryLabel(summary))
                                .font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                        }
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                            .foregroundStyle(GLGColor.textSecondary)
                    }
                    .padding(.horizontal, 16).padding(.top, 15)

                    if lines.isEmpty {
                        Text("진행 중인 픽업이 없어요.")
                            .font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                            .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 15)
                    } else {
                        Spacer().frame(height: 13)
                        ForEach(Array(lines.enumerated()), id: \.offset) { i, line in
                            Divider().opacity(i > 0 ? 0.6 : 1)
                            GameLineRow(line: line)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }
}

/// 진입 카드 부제 — "이번 주 마감 2건 · 진행 중 픽업 6".
private func summaryLabel(_ s: ScheduleSummary) -> String {
    "이번 주 마감 \(s.weekDeadlines)건 · 진행 중 픽업 \(s.activePickups)"
}

// 게임 한 줄 — 색 바 + 게임명(+콜라보) + 요약 + 잔여.
private struct GameLineRow: View {
    let line: GameScheduleLine
    var body: some View {
        let c = Color(argb64: line.colorArgb)
        HStack(spacing: 9) {
            RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 3, height: 26)
            Text(line.shortName).font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(c).lineLimit(1)
            if line.hasCollab { CollabChip() }
            Text(line.summary).font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
            Text(line.remainLabel).font(.pretendard(size: 12.5, weight: .bold))
                .foregroundStyle(line.urgent ? glUrgent : GLGColor.textPrimary).lineLimit(1)
        }
        .padding(.horizontal, 16).padding(.vertical, 11)
    }
}

// ── 상세 페이지: 마감 날짜 타임라인 ─────────────────────────────────────────

/// 전체 게임 일정 페이지 — [일정 | 주년] 세그먼트 탭.
/// 일정=요약 3칸 + 종료 미정 카드 + 마감일 타임라인, 주년=다가오는 게임 주년.
///
/// 주년은 원래 게임 정보 탭 본문의 독립 섹션이었다. 1년에 몇 번 볼 정보가 상시 자리를 차지하고 있었고,
/// 성격도 '언제 뭐가 있나'라 일정과 같아서 여기 탭으로 합쳤다.
struct GameSchedulePage: View {
    @ObservedObject var store: SpendingStore
    let filter: String
    @State private var tab = 0

    private var scheduleTitle: some View {
        Text("마감이 가까운 순서로 정리했어요.")
            .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 14)
    }

    var body: some View {
        let all = ScheduleLogic.shared.buildSchedule(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
        let entries = ScheduleLogic.shared.filteredEntries(entries: all, filter: filter)
        let days = ScheduleLogic.shared.buildDays(entries: entries, nowMillis: nowMs())
        let undated = ScheduleLogic.shared.undatedPickups(banners: store.activeBanners, filter: filter)
        let summary = ScheduleLogic.shared.summarize(banners: store.activeBanners, entries: all, filter: filter, nowMillis: nowMs())
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 페이지 타이틀은 네비게이션 바(뒤로가기 + 타이틀)로 — Android 상세 헤더와 동일 형식.
                Picker("보기", selection: $tab) {
                    Text("일정").tag(0)
                    Text("주년").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.bottom, 14)

                if tab == 1 {
                    AnniversaryContent()
                } else {
                scheduleTitle
                if days.isEmpty && undated.isEmpty {
                    Text("예정된 일정이 없어요.")
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
                } else {
                    SummaryStrip(s: summary)
                    Spacer().frame(height: 16)
                    if !undated.isEmpty {
                        UndatedPinCard(pickups: undated)
                        Spacer().frame(height: 20)
                    }
                    if !days.isEmpty {
                        TodayMarker()
                        Spacer().frame(height: 14)
                        ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                            DayNode(d: d, isLast: i == days.count - 1)
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

// 요약 3칸 — 이번 주 마감 / 진행 중 픽업 / 이벤트·콘텐츠.
private struct SummaryStrip: View {
    let s: ScheduleSummary
    var body: some View {
        HStack(spacing: 0) {
            cell(s.weekDeadlines, "이번 주 마감")
            Rectangle().fill(glLine).frame(width: 1)
            cell(s.activePickups, "진행 중 픽업")
            Rectangle().fill(glLine).frame(width: 1)
            cell(s.extras, "이벤트 · 콘텐츠")
        }
        .fixedSize(horizontal: false, vertical: true)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(glLine, lineWidth: 1))
    }

    private func cell(_ value: Int32, _ label: String) -> some View {
        VStack(spacing: 1) {
            Text("\(value)").font(.pretendard(size: 19, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                .monospacedDigit()
            Text(label).font(.pretendard(size: 10.5, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 11)
    }
}

/// 종료 시각이 미공지라 타임라인에 못 올리는 픽업 — 상단 고정.
/// 지금 스타레일 × Fate 콜라보가 정확히 이 상태다(상류 ennead 가 end_time 을 안 채움).
private struct UndatedPinCard: View {
    let pickups: [GachaBanner]
    var body: some View {
        let title = pickups.compactMap { GameInfoKt.collabTitle(banner: $0) }.first ?? "종료 미정 픽업"
        let started = pickups.filter { $0.startMillis > 0 }.map { $0.startMillis }.min()
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 7) {
                if pickups.contains(where: { GameInfoKt.isCollabBanner(banner: $0) }) { CollabChip() }
                Text(title).font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                Spacer(minLength: 0)
            }
            if let started {
                Text("\(DateUtil.shared.shortDate(millis: started)) 시작")
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 3)
            }
            PickupChips(pickups: pickups).padding(.top, 10)
            Text("종료 시각 미공지 — 확정되면 타임라인에 올라갑니다")
                .font(.pretendard(size: 11, weight: .bold)).foregroundStyle(glCollab).padding(.top, 9)
        }
        .padding(.horizontal, 14).padding(.vertical, 13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(glCollab.opacity(0.06), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(glCollab.opacity(0.3), lineWidth: 1))
    }
}

// 오늘 마커 — 타임라인 시작점.
private struct TodayMarker: View {
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let now = nowMs()
        HStack(spacing: 8) {
            Circle().fill(accent.primary).frame(width: 8, height: 8).padding(.leading, 19)
            Text("오늘 · \(DateUtil.shared.month(millis: now))월 \(DateUtil.shared.dayOfMonth(millis: now))일")
                .font(.pretendard(size: 11, weight: .bold)).foregroundStyle(accent.primary)
            Rectangle().fill(accent.primary.opacity(0.25)).frame(height: 1)
        }
    }
}

// 날짜 노드 — 좌측 날짜(일/월·요일/D-N) + 세로 연결선, 우측에 그날 끝나는 항목들.
private struct DayNode: View {
    let d: ScheduleDay
    let isLast: Bool
    var body: some View {
        HStack(alignment: .top, spacing: 13) {
            VStack(spacing: 0) {
                Text("\(d.day)").font(.pretendard(size: 20, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary).monospacedDigit()
                Text("\(d.month)월 \(d.weekdayKo)").font(.pretendard(size: 10, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary)
                Text(d.dDay == 0 ? "D-DAY" : "D-\(d.dDay)")
                    .font(.pretendard(size: 9.5, weight: .bold))
                    .foregroundStyle(d.urgent ? glUrgent : GLGColor.textSecondary)
                    .padding(.horizontal, 6).padding(.vertical, 1.5)
                    .background(d.urgent ? glUrgent.opacity(0.14) : glTrack, in: Capsule())
                    .padding(.top, 5)
                // 다음 날짜로 이어지는 세로선(마지막 노드는 생략).
                if !isLast {
                    Rectangle().fill(glLine).frame(width: 1).frame(maxHeight: .infinity).padding(.top, 6)
                }
            }
            .frame(width: 46)
            VStack(spacing: 8) {
                ForEach(d.entries) { e in EntryCard(e: e) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.bottom, 18)
    }
}

// 일정 카드 — 종류 배지 + 제목 + 게임 태그, 픽업이면 캐릭터 칩까지.
private struct EntryCard: View {
    let e: ScheduleEntry
    var body: some View {
        let gc = Color(argb64: e.colorArgb)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 7) {
                Text(e.kind == "패치" ? "픽업" : e.kind)
                    .font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 7).padding(.vertical, 2)
                    .background(scheduleKindColor(e.kind), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                Text(e.title).font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                Text(e.gameShort).font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 7).padding(.vertical, 2)
                    .background(gc, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            if !e.sub.isEmpty {
                Text(e.sub).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
                    .lineLimit(1).padding(.top, 4)
            }
            if !e.pickups.isEmpty {
                PickupChips(pickups: e.pickups).padding(.top, 9)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        // 아웃라인에 게임색 — 타임라인에서 어느 게임 일정인지 배지를 읽기 전에 구분된다.
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(gc.opacity(0.35), lineWidth: 1))
    }
}

/// 픽업 칩 — 캐릭터 먼저, 무기(광추·음동기)는 그다음. 둘 다 **이름 그대로** 노출한다.
/// ("무기 2종"처럼 개수로 뭉치면 정작 뭐가 픽업인지 알 수 없어 칩의 쓸모가 없다.)
/// 2개씩 줄바꿈 — Android PickupChips 와 동일 규칙.
private struct PickupChips: View {
    let pickups: [GachaBanner]
    var body: some View {
        let chars = pickups.filter { $0.type != "weapon" }
        let weapons = pickups.filter { $0.type == "weapon" }
        VStack(alignment: .leading, spacing: 6) {
            chipRows(chars)
            // 캐릭터와 무기는 종류가 다르니 구분선으로 끊는다(둘 다 있을 때만).
            if !chars.isEmpty && !weapons.isEmpty {
                Divider().opacity(0.7).padding(.vertical, 1)
            }
            chipRows(weapons)
        }
    }

    @ViewBuilder
    private func chipRows(_ list: [GachaBanner]) -> some View {
        let rows = stride(from: 0, to: list.count, by: 2).map { Array(list[$0..<min($0 + 2, list.count)]) }
        ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
            HStack(spacing: 6) {
                ForEach(Array(row.enumerated()), id: \.offset) { _, b in PickupChip(banner: b) }
                Spacer(minLength: 0)
            }
        }
    }
}

private struct PickupChip: View {
    let banner: GachaBanner
    var body: some View {
        HStack(spacing: 6) {
            // 무기도 캐릭터와 같은 원형 아바타 — 칩이 한 줄에 섞여도 형태가 어긋나지 않는다.
            if banner.type == "weapon" {
                ZStack {
                    Circle().fill(glWeap.opacity(0.16))
                    SwordShape().fill(glWeap).frame(width: 10, height: 12)
                }
                .frame(width: 20, height: 20)
            } else {
                ZStack {
                    Circle().fill(Color(argb64: banner.gameColor))
                    Text(String(banner.name.prefix(1))).font(.pretendard(size: 10, weight: .heavy))
                        .foregroundStyle(.white)
                }
                .frame(width: 20, height: 20)
            }
            Text(banner.name).font(.pretendard(size: 11.5, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
        }
        .padding(.leading, 3).padding(.trailing, 10).padding(.vertical, 3)
        .background(Color.white, in: Capsule())
        .overlay(Capsule().stroke(glLine, lineWidth: 1))
    }
}
