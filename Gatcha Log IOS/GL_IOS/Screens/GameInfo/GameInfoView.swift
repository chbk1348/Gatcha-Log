import SwiftUI
import Shared

// 게임 정보 탭 — 데일리(노트+출석)·배너/전투/일지·패치·위시·천장·이벤트·정기콘텐츠.
// (Compose GameInfoScreen 대응) ⚠️ chunk ② — 가챠 계산기/리포트/대시보드/프로필/확률표·리딤코드 다이얼로그는 chunk ③.
// HoYoLAB 연동은 네이티브 HoyolabLinkView 를 시트로 호스팅.
struct GameInfoView: View {
    var store: SpendingStore
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
    /// 출석 체크 상세(데일리 타일에서 진입).
    @State private var showAttendance = false
    /// 전투 진행도·수입 일지 상세(데일리에서 진입).
    @State private var showGameContent = false
    /// '어떤 캐릭터로 깼는지'(층·간별 편성) — 전투 진행도에서 한 단계 더 들어간다.
    @State private var showCombatClears = false
    @State private var statChar: EnkaChar? = nil
    @State private var statGame = "genshin"
    @State private var showStats = false
    @State private var rosterGame = "genshin"
    @State private var showRoster = false
    // 공지 상세 — 뉴스 행 탭 시 선택 후 push(destination형 NavigationLink 혼용 버그 회피, 파일 내 다른 페이지와 동일 패턴).
    @State private var selectedNews: NewsItem? = nil
    @State private var showNewsDetail = false
    /// 통합 일정 집계 — 원본 3종이 바뀔 때만 만든다(아래 `.task`).
    /// 예전엔 LazyVStack 본문에서 계산해, 이 탭이 다시 그려질 때마다(구독 30개가 물려 있다) 반복됐다.
    @State private var schedule: [ScheduleEntry] = []

    private struct HomeScheduleKey: Equatable {
        let banners: [GachaBanner]
        let events: [GameEvent]
        let challenges: [GameChallenge]
    }

    private var homeScheduleKey: HomeScheduleKey {
        HomeScheduleKey(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
    }

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
            // ⚠️ 본문과 화면 이동을 **두 식으로 나눈다.** 한 줄로 이어 붙이면 수정자 사슬 전체가
            // 하나의 식이 되어 타입 추론이 폭발하고("unable to type-check"), 에러는 마지막에
            // 붙인 줄이 아니라 사슬 중간 아무 데나 찍힌다 — 이 파일에서 세 번 겪었다.
            mainScroll(proxy)
        .navigationDestination(isPresented: $showHoyolab) {
            HoyolabLinkView(store: store) { showHoyolab = false }
        }
        .navigationDestination(isPresented: $showCalc) {
            sectionPage("계산기") { GachaCalculatorSection(store: store, onOpenDashboard: { showDashboard = true }) }
        }
        .navigationDestination(isPresented: $showReport) { sectionPage("가챠 리포트") { GachaReportSection(store: store, onOpenDashboard: { showDashboard = true }) } }
        .navigationDestination(isPresented: $showGift) { GiftCodePage(store: store) }
        .navigationDestination(isPresented: $showDashboard) { GachaDashboardView(store: store) }
        .navigationDestination(isPresented: $showSchedule) { GameSchedulePage(store: store) }
        .navigationDestination(isPresented: $showNews) { NewsPage(store: store) }
        .navigationDestination(isPresented: $showNewsDetail) {
            if let n = selectedNews { NewsDetailView(store: store, item: n) }
        }
        .navigationDestination(isPresented: $showHoyoland) { HoyolandDetailView() }
        .navigationDestination(isPresented: $showAttendance) { AttendanceDetailView(store: store) }
        .navigationDestination(isPresented: $showGameContent) {
            sectionPage("전투 · 수입 일지") {
                GameTabbedSection(store: store)
            }
        }
        // ⚠️ 이 destination 은 **최상위에** 있어야 한다. 예전엔 위 `showGameContent` 안에 중첩돼
        // 있어서, 일지 페이지에 들어가 있을 때만 등록됐다 — 데일리 카드에서 눌러도 아무 일이
        // 일어나지 않던 원인이다(상태만 true 로 바뀌고 push 할 destination 이 없었다).
        .navigationDestination(isPresented: $showCombatClears) {
            sectionPage("클리어 편성") { CombatClearSection(store: store) }
        }
        .navigationDestination(isPresented: $showStats) {
            if let c = statChar {
                EnkaStatPage(char: c, game: statGame,
                             overrides: store.keyStatOverrides,
                             onSetOverride: { k, v in store.setKeyStatOverride(k, v) },
                             refinement: store.weaponRefinement[refinementKey(c)],
                             onNeedRefinement: { id, lv in
                                 store.loadWeaponRefinement(game: statGame, weaponId: id, level: lv)
                             })
                    // 캐릭터가 바뀌면 **다른 뷰**로 취급한다.
                    // navigationDestination 은 같은 자리의 목적지를 재사용해서, A 를 보고 나온 뒤 B 로
                    // 들어가면 A 의 유효옵션 칩이 한 번 그려졌다가 B 로 바뀌었다. id 를 걸면 새로 만든다.
                    .id(c.id)
            }
        }
        .navigationDestination(isPresented: $showRoster) {
            EnkaRosterPage(store: store, game: rosterGame)
        }
        }  // ScrollViewReader
    }

    /// 정련 캐시 키. **본문 안에서 문자열을 조립하지 않는다** — SwiftUI 뷰 빌더 안의 보간은
    /// 타입 추론을 폭발시켜 "unable to type-check in reasonable time" 을 내고, 에러는 엉뚱한
    /// 줄에 찍힌다(이 파일에서 두 번째다).
    private func refinementKey(_ c: EnkaChar) -> String {
        let id: Int32 = c.weapon?.id ?? 0
        let lv: Int32 = c.weapon?.refinement ?? 0
        return "\(statGame):\(id):\(lv)"
    }

    // 대기 중인 앵커가 있으면 해당 섹션으로 스크롤 후 소비(1회성). 탭 전환 직후 레이아웃 완료를 위해 다음 런루프에 실행.
    private func scrollToPendingAnchor(_ proxy: ScrollViewProxy) {
        guard let anchor = store.pendingGameInfoAnchor else { return }
        // 전투 진행도는 본문 섹션이 아니라 데일리에서 들어가는 상세 페이지로 옮겼다 → 스크롤 대신 페이지 진입.
        if anchor == .combat {
            showGameContent = true
            store.consumeGameInfoAnchor()
            return
        }
        DispatchQueue.main.async {
            withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(anchor.name, anchor: .top) }
            store.consumeGameInfoAnchor()
        }
    }

    /// 헤더 버튼 — **본문에서 뺀다.** body 가 길어질수록 타입 추론이 폭발해
    /// "unable to type-check" 가 나는데, 에러는 손대지도 않은 줄에 찍혀 원인을 가린다.
    @ToolbarContentBuilder private var toolbarContent: some ToolbarContent {
            // 버튼마다 ToolbarItem 을 따로 두고 사이에 ToolbarSpacer 를 넣는다 —
            // iOS 26 은 인접한 툴바 아이템을 하나의 글래스 캡슐로 묶어버리므로, 스페이서로 갈라야
            // 버튼이 각각 독립된 원형으로 떨어진다. (지출 탭 헤더와 동일)
            // 순서: 새로고침 → 리딤코드 → 설정.
            ToolbarItem(placement: .topBarTrailing) {
                // 새로고침 중에는 아이콘 자리를 스피너로 바꾼다 — 당겨서 새로고침과 달리
                // 버튼을 눌렀을 때는 화면 어디에도 진행 표시가 없어, 눌린 건지 알 수 없었다.
                Button { store.refreshGameInfo(force: true) } label: {
                    if store.isRefreshing { ProgressView().controlSize(.small) }
                    else { Image(systemName: "arrow.clockwise") }
                }
                .disabled(store.isRefreshing)
            }
            if #available(iOS 26.0, *) {
                ToolbarSpacer(.fixed, placement: .topBarTrailing)
            }
            if store.hoyolabConfig.isLinked {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showGift = true } label: { Image(systemName: "gift") }
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer(.fixed, placement: .topBarTrailing)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { showHoyolab = true } label: { Image(systemName: "gearshape") }
            }
    }

    /// 탭 본문 — 스크롤 + 섹션 + 수집 트리거 + 헤더.
    @ViewBuilder private func mainScroll(_ proxy: ScrollViewProxy) -> some View {
        ScrollView {
            // LazyVStack — 화면 밖 섹션(주년·뉴스·게임탭·계산기/리포트 진입)은 스크롤 시 지연 생성.
            // (VStack 이면 탭 전환 순간 7개 섹션 전부를 한꺼번에 빌드해 전환이 버벅였음)
            LazyVStack(alignment: .leading, spacing: 0) {
                // 홈 카드 딥링크 스크롤 앵커 — id 문자열은 Kotlin GameInfoAnchor 의 .name(NOTES/SCHEDULE/NEWS)과 일치해야 함.
                // 히어로는 section 래퍼 없이 전폭 — 배경색이 화면 가장자리까지 닿는다.
                DailyHeroSection(store: store,
                                 onConfig: { showHoyolab = true },
                                 onOpenAttendance: { showAttendance = true },
                                 onOpenGameContent: { showGameContent = true },
                                 onOpenClears: { showCombatClears = true }).id("NOTES")
                // 숙제 완주율은 별도 섹션을 두지 않는다 — 데일리의 게임 줄에 완주율까지 들어간다.
                // 내 캐릭터(보유 전체 로스터) — 데일리 다음 핵심 콘텐츠로 상단 배치
                // 미연동이면 섹션·상단 여백까지 통째 생략(빈 여백 방지).
                if store.hoyolabConfig.isLinked {
                    section {
                        EnkaCharSection(store: store,
                                        onOpen: { c, g in statChar = c; statGame = g; showStats = true },
                                        onOpenAll: { g in rosterGame = g; showRoster = true },
                                        onOpenHoyolab: { showHoyolab = true })
                    }
                }
                // 통합 게임 일정 — 패치·이벤트·정기 콘텐츠. 게임 구분 없이 전부 싣는다.
                // 집계는 원본 3종이 바뀔 때만(아래 .task) — 예전엔 여기서 body 평가마다 다시 만들었다.
                if !schedule.isEmpty {
                    section { GameScheduleSection(entries: schedule, banners: store.activeBanners, onSeeAll: { showSchedule = true }) }.id("SCHEDULE")
                }
                // 호요랜드 — 호요버스 한국 오프라인 행사(플레이스홀더). 탭하면 예상 장소·지난 행사 상세로.
                section { HoyolandSection(onOpen: { showHoyoland = true }) }
                // 공지·뉴스 — 게임별 최신 공지(탭하면 HoYoLab 열기). 더보기로 전체 페이지.
                section { NewsSection(store: store, onSeeAll: { showNews = true }, onOpenNews: { selectedNews = $0; showNewsDetail = true }) }.id("NEWS")
                // 진입 카드 — 가챠 도구.
                entryCards
                Color.clear.frame(height: 12)
            }
            // 좌우 여백은 **섹션마다** 준다(section 헬퍼). 통짜로 걸면 데일리 히어로가
            // 화면 끝까지 못 간다 — 히어로는 색이 가장자리에 닿아야 한다.
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
        // 내 캐릭터(Enka) — 탭에 들어오는 순간 시작한다. 섹션에 맡기면 스크롤로 그 항목이
        // 만들어질 때까지 조회가 시작되지 않는다(디스크 캐시 적중분은 즉시 표시).
        .task(id: store.hoyolabConfig.isLinked) {
            if store.hoyolabConfig.isLinked {
                store.autoLoadEnkaSection(games: ["genshin", "hsr", "zzz"], force: false)
            }
        }
        // 현재 게임 버전 — 데일리 타일 아래 한 줄(내부에서 1회만 실제로 돈다).
        .task { store.loadGameVersions() }
        .task(id: homeScheduleKey) {
            schedule = ScheduleLogic.shared.buildSchedule(
                banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
        }
        .onChange(of: store.hoyolabConfig.isLinked) { _, linked in
            if linked { store.refreshGameInfo(force: true) }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbarContent }
    }

    /// 하단 진입 카드 두 장(가챠 도구).
    ///
    /// ⚠️ 본문에 늘어놓지 않고 여기 모은다 — LazyVStack 자식이 늘수록 타입 추론 비용이 커져
    /// "unable to type-check" 가 나는데, 에러는 손대지도 않은 줄에 찍혀 원인을 가린다.
    @ViewBuilder private var entryCards: some View {
        section {
            navEntry(icon: "function", title: "가챠 계산기", sub: "재화 환산 · 확률 · 시나리오") { showCalc = true }
        }
        section {
            navEntry(icon: "chart.bar.xaxis", title: "가챠 효율 리포트",
                     sub: "UIGF/SRGF 분석 · 단가 · 천장 분포") { showReport = true }
        }
    }

    @ViewBuilder private func section<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        Spacer().frame(height: 20)
        content().padding(.horizontal, 16)
    }

    // 페이지 진입 카드 — 아이콘 + 제목 + 설명 + 셰브론(글래스 카드).
    @ViewBuilder private func navEntry(icon: String, title: String, sub: String,
                                       action: @escaping () -> Void) -> some View {
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
        .glgPageTitle(title)
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
/// 확정 배지 — 예상(회색)과 확실히 갈라야 해서 채운 색을 쓴다. Android ConfirmedGreen 과 같은 값.
private let glConfirmed = Color(hex: 0xFF2BB673)

/// 현재 시각(ms) — 공유 로직(`isImminent`·`hmsLabel`)과 같은 단위로 맞춘다.
private func nowMS() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

private extension View {
    /**
     마감 임박 표시의 '사이렌' — 투명도를 천천히 오가게 해 시선을 끈다.

     색을 번갈아 칠하거나 크기를 키우는 방법도 있지만, 매초 숫자가 바뀌는 글자에 그걸 얹으면
     흔들려 읽기 어렵다. 투명도만 움직이면 글자 위치·폭이 그대로라 카운트다운을 읽는 데 방해가 없다.
     0.45 아래로는 내리지 않는다 — 사라졌다 나타나는 것처럼 보이면 경고가 아니라 결함처럼 읽힌다.

     ⚠️ `active` 가 false 면 애니메이션을 아예 걸지 않는다. `repeatForever` 는 값이 안 바뀌어도
     계속 돌기 때문에, 임박하지 않은 카드까지 붙이면 화면 전체가 쉬지 않고 다시 그려진다.
     */
    @ViewBuilder
    func sirenPulse(active: Bool) -> some View {
        if active {
            self.modifier(GLGSirenPulse())
        } else {
            self
        }
    }
}

private struct GLGSirenPulse: ViewModifier {
    @State private var dim = false
    func body(content: Content) -> some View {
        content
            .opacity(dim ? 0.45 : 1)
            .animation(.easeInOut(duration: 0.65).repeatForever(autoreverses: true), value: dim)
            .onAppear { dim = true }
    }
}
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
    let onSeeAll: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let lines = ScheduleLogic.shared.gameLines(banners: banners, entries: entries, nowMillis: nowMs())
        let summary = ScheduleLogic.shared.summarize(banners: banners, entries: entries, nowMillis: nowMs())
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

/// 진입 카드 한 줄에 세울 얼굴 수 — Android `LINE_FACE_MAX` 와 같이 고쳐야 한다.
private let lineFaceMax = 3

// 게임 한 줄 — 색 바 + 게임명(+콜라보) + 픽업 얼굴·이름 + 잔여.
private struct GameLineRow: View {
    let line: GameScheduleLine
    var body: some View {
        let c = Color(argb64: line.colorArgb)
        HStack(spacing: 9) {
            RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 3, height: 26)
            Text(line.shortName).font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(c).lineLimit(1)
            if line.hasCollab { CollabChip() }
            if line.faces.isEmpty {
                // 픽업이 없는 게임(젠존제·명조)은 얼굴이 없다 — 일정 건수 요약("이벤트 5")이 그 자리를 지킨다.
                Text(line.summary).font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                    .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
            } else {
                HStack(spacing: 6) {
                    // 얼굴끼리는 겹쳐 세운다 — 석 장을 따로 놓으면 이름 자리가 남지 않는다.
                    HStack(spacing: -7) {
                        ForEach(Array(line.faces.prefix(lineFaceMax).enumerated()), id: \.offset) { _, b in
                            LineFace(banner: b)
                        }
                    }
                    Text(line.pickupNames).font(.pretendard(size: 11.5))
                        .foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Text(line.remainLabel).font(.pretendard(size: 12.5, weight: .bold))
                .foregroundStyle(line.urgent ? glUrgent : GLGColor.textPrimary).lineLimit(1)
        }
        .padding(.horizontal, 16).padding(.vertical, 11)
    }
}

/// 진입 카드 줄의 얼굴 한 장.
///
/// 겹쳐 세우므로 **흰 테두리**로 뒤 장과 경계를 만든다(테두리가 없으면 어두운 초상끼리
/// 한 덩어리로 뭉쳐 보인다). 지름은 상세의 `PickupSlot`(38)보다 작다 —
/// 여기서는 한 줄에 딸린 부가 정보다. Android `LineFace`(22dp)와 같이 고쳐야 한다.
private struct LineFace: View {
    let banner: GachaBanner
    private let side: CGFloat = 22

    var body: some View {
        let isWeapon = banner.type == "weapon"
        ZStack {
            Circle().fill(glPickupGold.opacity(0.14))
            if banner.iconUrl.isEmpty {
                fallback(isWeapon)
            } else {
                AsyncImage(url: URL(string: banner.iconUrl)) { phase in
                    switch phase {
                    case .success(let img): img.resizable().aspectRatio(contentMode: .fill)
                    case .failure: fallback(isWeapon)
                    default: Color.clear
                    }
                }
                .frame(width: side, height: side)
                .clipShape(Circle())
            }
        }
        .frame(width: side, height: side)
        .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
    }

    /// 초상을 못 받았을 때 세우는 실루엣 — 무기 픽업이면 사람 대신 별.
    @ViewBuilder
    private func fallback(_ isWeapon: Bool) -> some View {
        Image(systemName: isWeapon ? "star.fill" : "person.fill")
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(glPickupGold)
    }
}

// ── 상세 페이지: 마감 날짜 타임라인 ─────────────────────────────────────────

/// 전체 게임 일정 페이지 — [일정 | 방송 | 주년] 세그먼트 탭.
/// 일정=요약 3칸 + 종료 미정 카드 + 마감일 타임라인, 방송=버전 특별 방송 예상, 주년=다가오는 게임 주년.
///
/// 주년은 원래 게임 정보 탭 본문의 독립 섹션이었다. 1년에 몇 번 볼 정보가 상시 자리를 차지하고 있었고,
/// 성격도 '언제 뭐가 있나'라 일정과 같아서 여기 탭으로 합쳤다.
struct GameSchedulePage: View {
    var store: SpendingStore
    @State private var tab = 0
    @State private var sched = SchedulePageData()

    private var scheduleTitle: some View {
        Text("시작 · 종료 · 예상(방송 역산)")
            .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 14)
    }

    var body: some View {
        ScrollView {
            // `pinnedViews` 를 쓰려면 Lazy 컨테이너여야 한다 — 주차 헤더(라벨 + 일~토 그리드)를
            // 상단에 붙여 두려는 것. 그 주 항목을 훑는 동안 **지금 몇 주차의 무엇을 보고 있는지**가
            // 화면에서 사라지지 않는다(주가 넘어가면 다음 헤더가 밀어 올린다).
            LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                // 페이지 타이틀은 네비게이션 바(뒤로가기 + 타이틀)로 — Android 상세 헤더와 동일 형식.
                Picker("보기", selection: $tab.animation(.easeInOut(duration: 0.2))) {
                    Text("일정").tag(0)
                    Text("방송").tag(1)
                    Text("주년").tag(2)
                }
                .pickerStyle(.segmented)
                .padding(.bottom, 14)

                if tab == 1 {
                    BroadcastContent(banners: store.activeBanners, confirmed: store.confirmedBroadcasts)
                } else if tab == 2 {
                    AnniversaryContent()
                } else {
                // 콜라보는 **맨 위**. 종료 시각이 미공지라 시간 축에 못 올리는데, 맨 아래에 두면
                // 진행 중인 한정 콜라보를 스크롤 끝까지 내려야 본다 — 놓치면 되돌릴 수 없는
                // 일정이 가장 늦게 읽혔다.
                if !sched.undated.isEmpty {
                    CollabPromoBanner(pickups: sched.undated, expanded: store.collabBannerExpanded) {
                        withAnimation(.easeInOut(duration: 0.22)) {
                            store.setCollabBannerExpanded(!store.collabBannerExpanded)
                        }
                    }
                    Spacer().frame(height: 16)
                }
                scheduleTitle
                if let summary = sched.summary { SummaryStrip(s: summary) }
                Spacer().frame(height: 16)
                ForEach(Array(sched.weeks.enumerated()), id: \.offset) { _, w in
                    Section {
                        WeekEntries(week: w)
                        Spacer().frame(height: 18)
                    } header: {
                        WeekHeader(week: w)
                    }
                }
                }
            }
            .padding(16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("게임 일정")
        .navigationBarTitleDisplayMode(.inline)
        // 일정 집계는 필터/원본이 바뀔 때만. 예전엔 body 첫 줄에서 5종을 조건 없이 계산해,
        // '주년' 탭을 보고 있어도(그때는 하나도 안 쓰는데) 세그먼트를 누를 때마다 전부 다시 돌았다.
        .task(id: scheduleKey) { sched = Self.buildSchedule(store: store) }
    }

    /// 일정 탭이 쓰는 집계 묶음.
    struct SchedulePageData {
        var weeks: [ScheduleWeek] = []
        var undated: [GachaBanner] = []
        var summary: ScheduleSummary? = nil
    }

    /// 재계산 트리거 — 원본 3종이 바뀔 때만 다시 만든다.
    ///
    /// ⚠️ 개수가 아니라 **목록 자체**를 키로 쓴다. 개수로 잡으면 새로고침이 같은 **개수의 새 배너**를
    /// 내려줬을 때(종료 시각만 바뀐 경우 등) 일정 탭이 옛 값을 그대로 유지한다.
    /// Swift `Array.==` 는 버퍼가 같으면 O(1)이라 변화 없는 평가에서는 비용이 없다.
    private struct ScheduleKey: Equatable {
        let banners: [GachaBanner]
        let events: [GameEvent]
        let challenges: [GameChallenge]
        let broadcasts: [ConfirmedBroadcast]
    }

    private var scheduleKey: ScheduleKey {
        ScheduleKey(banners: store.activeBanners,
                    events: store.gameEvents, challenges: store.challenges,
                    broadcasts: store.confirmedBroadcasts)
    }

    private static func buildSchedule(store: SpendingStore) -> SchedulePageData {
        let now = nowMs()
        // 마감 + **시작** + 방송을 한 축에. 예전엔 종료만 모아서 다음 픽업이 언제 시작하는지 알 수 없었다.
        let entries = (
            ScheduleLogic.shared.buildSchedule(
                banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
            + ScheduleLogicKt.buildStartEntries(banners: store.activeBanners, nowMillis: now)
            + ScheduleLogicKt.buildBroadcastEntries(
                banners: store.activeBanners, confirmed: store.confirmedBroadcasts, nowMillis: now)
        ).sorted { $0.target < $1.target }
        return SchedulePageData(
            weeks: ScheduleLogicKt.buildWeeks(entries: entries, nowMillis: now, weeks: 4),
            undated: ScheduleLogic.shared.undatedPickups(banners: store.activeBanners),
            summary: ScheduleLogic.shared.summarize(banners: store.activeBanners, entries: entries, nowMillis: now)
        )
    }
}

/// 한 주의 **머리** — 라벨·기간·건수 + 일~토 7칸 그리드. 스크롤 중 상단에 고정된다.
///
/// 칸이 좁아 제목은 못 담는다. 그리드는 **어느 날이 바쁜지**만 점으로 알리고,
/// 무엇인지는 아래 목록([WeekEntries])이 말한다. 목록이 길어도 이 표가 붙어 있어야
/// "지금 보는 줄이 몇 일자인지"를 되짚으러 위로 올라가지 않는다.
private struct WeekHeader: View {
    let week: ScheduleWeek
    @Environment(\.glgAccent) private var accent

    /// 지금 **고정돼 있는가.** 붙어 있는 동안만 아웃라인에 강조색을 준다 —
    /// 흐르는 카드와 붙어 있는 카드를 색 하나로 구분한다.
    @State private var pinned = false

    /// 카드 모양 — 배경·아웃라인·고정 판정이 같은 도형을 써야 어긋나지 않는다.
    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: 18, style: .continuous) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .bottom, spacing: 7) {
                Text(week.label).font(.pretendard(size: 13, weight: .black))
                    .foregroundStyle(GLGColor.textPrimary)
                Text("\(week.rangeLabel) · \(week.entries.isEmpty ? "일정 없음" : "\(week.entries.count)건")")
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            .padding(.bottom, 8)

            HStack(spacing: 4) {
                ForEach(Array(week.days.enumerated()), id: \.offset) { _, d in
                    WeekCell(day: d)
                }
            }
        }
        // 고정되면 **카드로 떠 있는다.** 헤더바 재질을 따라가려던 것을 접었다 —
        // 두 OS 의 바가 서로 다른 물건이라(iOS 26+ 유리 / iOS 18 크롬 머티리얼) 어느 한 값으로도
        // 양쪽에서 이어져 보이질 않았고, 그러느니 앱의 카드 규격을 그대로 쓰는 게 낫다.
        //
        // 흰 배경 + 아웃라인(`glgGlassStrong`)이라 아래 목록이 비치지 않고, 좌우 여백으로
        // 스크롤이 지나가는 게 보여 **떠 있다는 것**이 오히려 분명해진다.
        // 폭을 꽉 채우려고 넣었던 음수 패딩·하단 구분선은 카드형에서는 필요 없다.
        .padding(.vertical, 10)
        .padding(.horizontal, 14)
        // `glgGlassStrong` 을 직접 펼쳐 쓴다 — 아웃라인 색을 고정 여부에 따라 바꿔야 해서
        // (그 모디파이어는 검정 10% 고정이다). 흰 배경 + 1px 아웃라인 규격은 그대로다.
        .background(Color.white, in: shape)
        .overlay(
            shape.stroke(pinned ? accent.primary : Color.black.opacity(0.10),
                         lineWidth: pinned ? 1.5 : 1)
                .allowsHitTesting(false)
        )
        // 카드 **바깥** 여백 — 고정됐을 때 헤더바에 딱 붙지 않고 한 칸 떨어져 뜬다.
        // (배경 안쪽에 주면 카드만 두꺼워지고 간격은 안 생긴다)
        .padding(.top, 10)
        .background { pinDetector }
    }

    /// 고정 여부 감지 — 스크롤뷰 좌표계에서 이 헤더의 윗변이 0 까지 올라왔으면 붙은 것이다.
    ///
    /// SwiftUI 는 "이 Section 헤더가 지금 pin 됐는가"를 알려주지 않는다. 스크롤 오프셋 하나로
    /// 재려 해도 주마다 위치가 달라 못 쓴다 — **헤더 자신의 위치**를 봐야 한다.
    /// 주는 넷뿐이라 헤더마다 하나씩 둬도 부담이 없다.
    private var pinDetector: some View {
        GeometryReader { geo in
            Color.clear.onChange(of: geo.frame(in: .scrollView(axis: .vertical)).minY, initial: true) { _, y in
                let now = y <= 0.5
                if now != pinned { withAnimation(.easeInOut(duration: 0.18)) { pinned = now } }
            }
        }
    }
}

/// 한 주의 **몸** — 그 주 항목 목록. 헤더가 고정된 채 이쪽만 흐른다.
private struct WeekEntries: View {
    let week: ScheduleWeek

    var body: some View {
        if !week.entries.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 10)
                ForEach(Array(week.entries.enumerated()), id: \.offset) { _, e in
                    ScheduleRow(entry: e)
                    Spacer().frame(height: 7)
                }
            }
        }
    }
}

private struct WeekCell: View {
    let day: WeekDay
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let dim = day.isPast && !day.isToday
        VStack(spacing: 0) {
            Text("\(day.day)").font(.pretendard(size: 11, weight: .bold))
                .foregroundStyle(dim ? GLGColor.textSecondary.opacity(0.45) : GLGColor.textPrimary)
            Text(day.weekdayKo).font(.pretendard(size: 8.5))
                .foregroundStyle(GLGColor.textSecondary.opacity(dim ? 0.35 : 1))
            Spacer().frame(height: 3)
            HStack(spacing: 2) {
                ForEach(Array(day.dotColors.enumerated()), id: \.offset) { _, c in
                    Circle().fill(Color(argb64: c.int64Value)).frame(width: 5, height: 5)
                }
            }
            .frame(height: 5)
        }
        .frame(maxWidth: .infinity)
        // 높이는 **못 박는다.** 내용에 맡기면 글꼴 줄 상자 높이가 Android 와 달라 칸 크기가
        // 어긋난다(2026-08-18 지적). Android `WeekCellHeight` 와 **같이 고쳐야 한다.**
        .frame(height: 46)
        .background(day.isToday ? accent.primary.opacity(0.08) : Color(hex: 0xFFF6F7F9),
                    in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10)
            .stroke(day.isToday ? accent.primary.opacity(0.45) : Color(hex: 0xFFE0E0E0), lineWidth: 1))
    }
}

/// 5성 픽업 바탕색 — '내 캐릭터' 로스터와 같은 값.
private let glPickupGold = Color(hex: 0xFFD8A12E)

/// 픽업 한 칸 — 원형 초상 + 이름 한 줄. '내 캐릭터' `RosterSlot` 과 같은 형식이되,
/// 일정 줄 안에 들어가야 하므로 초상만 44 → 32 로 줄인다.
///
/// 이름은 **좌측 정렬**이다. 로스터는 칸 폭이 고정이라 가운데 정렬이 맞지만, 여기서는
/// 칸이 글자 길이만큼만 커져서 가운데로 두면 초상과 이름의 축이 이름마다 어긋난다.
/// 젠존제 픽업 줄 우상단의 안내 — **W-엔진이 왜 안 보이는지**.
///
/// 카드 안에 글로 적지 않고 **툴팁(팝오버)** 으로 두는 이유: 이 설명은 한 번 읽으면 그만인데
/// 픽업 줄마다 두 줄씩 붙으면 정작 얼굴과 마감을 밀어낸다.
///
/// iPhone 에서 `.popover` 는 기본이 시트로 바뀐다 — `presentationCompactAdaptation(.popover)`
/// 로 말풍선을 유지해야 Android 툴팁과 같은 모양이 된다.
private struct WEngineInfoTip: View {
    @State private var open = false

    var body: some View {
        Button { open = true } label: {
            Image(systemName: "info.circle")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(GLGColor.textSecondary.opacity(0.7))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("W-엔진이 안 보이는 이유")
        .popover(isPresented: $open) {
            VStack(alignment: .leading, spacing: 6) {
                Text("W-엔진 픽업 미표시").font(.pretendard(size: 12.5, weight: .bold))
                Text("제공처가 신규 W-엔진의 이름을 비워서 보내거나 원문 그대로 보내옵니다. "
                     + "실제와 다른 이름이 뜨는 것을 막기 위해 캐릭터 픽업만 싣습니다.")
                    .font(.pretendard(size: 11.5))
                    .foregroundStyle(GLGColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
            .frame(width: 260)
            .presentationCompactAdaptation(.popover)
        }
    }
}

/// 표시할 수 있는 최대 픽업 수 — 이보다 많으면 뒤는 자른다.
private let pickupSlots = 5

/// **한 줄**에 세우는 칸 수. 이보다 많으면 줄을 나눈다.
///
/// 5 였다가 3 으로 줄였다 — 다섯 칸이면 칸 폭이 51pt 라, 캐릭터(3~4자)는 몰라도
/// 광추·W-엔진 이름(7~16자)이 3~4줄로 깨져 줄 높이가 들쭉날쭉했다.
/// Android `PICKUP_LINE_SLOTS` 와 같이 고쳐야 한다.
private let pickupLineSlots = 3

/// 픽업 한 줄 — 남은 폭을 **인원수만큼 균등하게** 나눈다(최대 `pickupSlots` 칸).
///
/// 칸마다 폭이 같으므로 픽업이 하나든 넷이든 칸 사이 간격이 일정하고, 초상·이름은 각 칸의
/// 가운데에 선다. 인원이 적으면 칸이 그만큼 넓어져 이름도 덜 접힌다.
private struct PickupRow: View {
    let label: String
    let list: [GachaBanner]
    /// 우측 안내 버튼을 세울지(젠존제 W-엔진 미표시 안내).
    var showInfo: Bool = false

    /// 왼쪽 라벨의 폭 — Android `PickupLabelWidth` 와 같이 고쳐야 한다.
    private let labelWidth: CGFloat = 42

    var body: some View {
        let shown = Array(list.prefix(pickupSlots))
        HStack(alignment: .top, spacing: 6) {
            // 라벨 폭은 **고정**이다("캐릭터"·"광추"·"W-엔진" 길이가 제각각) — 안 그러면
            // 캐릭터 줄과 무기 줄의 초상이 세로로 어긋난다.
            //
            // 라벨도 자르지 않는다. 'W-엔진'처럼 폭에 꽉 차는 말이 있어서 한 줄로 묶으면
            // 게임에 따라 끝이 잘린다 — 무엇을 세운 줄인지 알리는 말이 잘리면 뜻이 없다.
            Text(label).font(.pretendard(size: 9.5, weight: .bold))
                .foregroundStyle(GLGColor.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .frame(width: labelWidth, alignment: .leading)
                .padding(.top, 12)
            // 칸 수에 따라 짜임을 바꾼다 — 균등 분할 하나로 1~5 를 다 감당하면 양끝이 다 무너진다.
            //
            //  1개  : 초상 **옆에** 이름(가로). 폭이 통째로 남는데 38pt 상자에 이름을 접을 이유가 없다.
            //  2~4개: 한 줄 균등 분할. 칸이 66pt 이상이라 이름이 1~2줄에 들어온다.
            //  5개  : **3+2 두 줄.** 한 줄에 다섯이면 칸이 51pt 로 좁아, 광추·W-엔진 이름
            //         (실측 7~16자 — "무지개가 영원히 하늘에 머물길")이 3~4줄로 깨졌다.
            //         두 줄 다 **3칸 기준**으로 놓는다(뒷줄은 빈 칸을 채워 둔다) — 칸 폭을 맞춰야
            //         위아래 얼굴이 같은 세로선에 선다. 뒷줄만 2등분하면 축이 어긋난다.
            if shown.count == 1 {
                PickupSlot(banner: shown[0], style: .wide)
            } else if shown.count > pickupLineSlots {
                VStack(alignment: .leading, spacing: 10) {
                    pickupLine(Array(shown.prefix(pickupLineSlots)))
                    pickupLine(Array(shown.dropFirst(pickupLineSlots)))
                }
            } else {
                pickupLine(shown)
            }
            // 안내 버튼은 **자리를 차지한다**(겹쳐 얹지 않는다). 예전엔 줄 위에 오버레이로
            // 올려서 맨 오른쪽 초상과 겹쳤다 — 칸이 넓어질수록 더 파고들었다.
            if showInfo {
                WEngineInfoTip().padding(.top, 10)
            }
        }
    }

    /// 한 줄 — 언제나 `pickupLineSlots` 칸으로 나눈다.
    ///
    /// 모자란 칸은 **빈 자리로 남긴다**(칸을 넓히지 않는다). 두 줄로 나뉜 뒷줄이 제 수만큼만
    /// 등분하면 앞줄과 칸 폭이 달라져 위아래 얼굴이 어긋난다.
    @ViewBuilder
    private func pickupLine(_ items: [GachaBanner]) -> some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, b in
                PickupSlot(banner: b).frame(maxWidth: .infinity)
            }
            if items.count < pickupLineSlots {
                ForEach(items.count..<pickupLineSlots, id: \.self) { _ in
                    Color.clear.frame(maxWidth: .infinity, maxHeight: 0)
                }
            }
        }
    }
}

private struct PickupSlot: View {
    let banner: GachaBanner

    /// 칸의 짜임.
    enum Style {
        /// 격자 한 칸 — 초상 위, 이름 아래. 칸 폭이 균등하므로 중심축이 저절로 맞는다.
        case grid
        /// 픽업이 **하나뿐일 때** — 초상 오른쪽에 이름을 둔다. 줄 전체가 제 칸이라
        /// 세로로 쌓으면 38pt 상자에 긴 이름이 접히면서 폭은 폭대로 남는다.
        case wide
    }
    var style: Style = .grid

    /// 초상 지름 — '내 캐릭터' 로스터(44)보다 한 단계 작다.
    ///
    /// 로스터는 캐릭터를 **고르는** 화면이라 얼굴이 주인공이지만, 여기서는 일정 한 줄에 딸린
    /// 부가 정보다. 같은 크기로 두니 얼굴이 카드의 주인공이 돼 제목·마감이 뒤로 밀렸다.
    /// Android `PickupAvatarSize` 와 같이 고쳐야 한다.
    private let avatar: CGFloat = 38

    var body: some View {
        switch style {
        case .grid: gridBody
        case .wide: wideBody
        }
    }

    /// 초상 오른쪽에 이름 — 남는 폭을 이름에 준다. 16자짜리 광추 이름도 한 줄에 들어간다.
    private var wideBody: some View {
        HStack(alignment: .center, spacing: 9) {
            avatarView
            Text(banner.name).font(.pretendard(size: 11, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }

    private var gridBody: some View {
        // 초상·이름 모두 **칸의 가운데**에 선다. 칸 폭이 균등하므로 이름 상자와 초상의
        // 중심축이 저절로 같아진다 — 따로 맞출 필요가 없다.
        VStack(alignment: .center, spacing: 5) {
            avatarView
            // 이름은 **끝까지** 보여준다. 자르면 "그림자 사냥꾼의…"처럼 무엇인지 특정할 수 없는
            // 조각만 남는다 — 얼굴 옆 이름은 확인용이라 잘리면 있으나 마나다.
            // 줄 수를 묶지 않는다(광추·W-엔진 이름은 캐릭터명보다 길다). 칸끼리는 위를 맞춘다.
            Text(banner.name).font(.pretendard(size: 9.5, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var avatarView: some View {
        let isWeapon = banner.type == "weapon"
        return Group {
            ZStack {
                Circle().fill(glPickupGold.opacity(0.14))
                // 초상을 못 받는 경우가 여럿이다 — 상류에 아직 이미지가 안 올라온 신규 캐릭터,
                // CDN 오류, 오프라인. 어느 쪽이든 **빈 원**을 남기면 자리만 차지하고 뜻이 없다.
                // 실루엣 아이콘을 세워 "여기 픽업이 하나 있다"까지는 읽히게 한다.
                if banner.iconUrl.isEmpty {
                    pickupFallback(isWeapon)
                } else {
                    AsyncImage(url: URL(string: banner.iconUrl)) { phase in
                        switch phase {
                        case .success(let img): img.resizable().aspectRatio(contentMode: .fill)
                        case .failure: pickupFallback(isWeapon)
                        default: Color.clear
                        }
                    }
                    .frame(width: avatar, height: avatar)
                    .clipShape(Circle())
                }
            }
            .frame(width: avatar, height: avatar)
        }
    }

    /// 초상을 못 받았을 때 세우는 실루엣 — 무기 픽업이면 사람 대신 별.
    @ViewBuilder
    private func pickupFallback(_ isWeapon: Bool) -> some View {
        Image(systemName: isWeapon ? "star.fill" : "person.fill")
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(glPickupGold)
    }
}

/// 일정 한 줄 — 표식(▲▼◆) + 제목·부제 + D-day.
private struct ScheduleRow: View {
    let entry: ScheduleEntry
    @Environment(\.glgAccent) private var accent

    /// **예상** 표식 색. 확정 마감(빨강)·시작(강조색)과 절대 겹치지 않게 주황 계열로 뺀다.
    private let estimate = Color(hex: 0xFFD97706)

    var body: some View {
        let mark = ScheduleLogicKt.scheduleMarkOf(entry: entry)
        VStack(alignment: .leading, spacing: 0) {
        HStack(spacing: 0) {
            // 리딩 — 게임색 약칭 배지(지출 행과 같은 규격). 일정은 여섯 게임이 섞여 흐르므로
            // 어느 게임 건지가 **가장 먼저** 읽혀야 한다.
            GLGGameTag(game: entry.gameShort, size: .small)
            Spacer().frame(width: 10)
            VStack(alignment: .leading, spacing: 1) {
                Text(entry.title).font(.pretendard(size: 12.5, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                // 게임명은 배지가 말한다 — 부제에서 중복하지 않는다.
                // 아이콘이 없는 줄(이벤트·방송)은 부제를 여기 글자로 둔다. 픽업은 카드 아래
                // 별도 단으로 내려간다(아래 참고).
                if entry.pickups.isEmpty && !entry.sub.isEmpty {
                    Text(entry.sub).font(.pretendard(size: 10.5))
                        .foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                        .padding(.top, 2)
                }
            }
            Spacer(minLength: 8)
            // 트레일링 = 남은 시간 + 그 시각의 날짜.
            //
            // D-N 만으로는 **마감 당일에 지금 해야 하는지 판단이 안 된다.** 24시간 안쪽(=D-1)부터는
            // 초까지 세고 사이렌처럼 명멸시킨다. 반대로 며칠 남은 일정에 초를 붙여 봐야 의미가 없어
            // 그때는 D-N 그대로 두고 갱신도 분 단위로 늦춘다(다시 그리는 횟수 60배 차이).
            //
            // 날짜를 아래 붙이는 이유 — "D-3"은 상대값이라 달력을 다시 떠올려야 한다. 픽업 마감일을
            // 실제 날짜로 알아야 저축·천장 계획이 선다.
            VStack(alignment: .trailing, spacing: 3) {
                HStack(spacing: 4) {
                    // 종류는 **남은 시간 바로 앞**에 붙인다. 앞서 게임 배지 옆에 따로 세워 봤는데,
                    // 정작 "무엇까지 얼마 남았나"는 한 덩어리로 읽히는 말이라 줄 양끝으로 갈라
                    // 놓으면 눈이 두 번 움직인다. (그 전엔 ▲▼◆ 였고, 방향만 있고 뜻이 없었다.)
                    Text(markLabel(mark)).font(.pretendard(size: 9, weight: .semibold))
                        .foregroundStyle(markColor(mark))
                    TimelineView(.periodic(from: .now, by: isImminent(targetMillis: entry.target, nowMillis: nowMS()) ? 1 : 60)) { ctx in
                        let now = Int64(ctx.date.timeIntervalSince1970 * 1000)
                        let imminent = isImminent(targetMillis: entry.target, nowMillis: now)
                        let d = Int(entry.dDay(nowMillis: now))
                        let hot = imminent || (d >= 0 && d <= 3)
                        Text(imminent ? hmsLabel(targetMillis: entry.target, nowMillis: now)
                                      : (d <= 0 ? "종료" : "D-\(d)"))
                            .font(.pretendard(size: 10.5, weight: .black))
                            // 임박 색은 **종류 색**을 따른다. 예전엔 무조건 빨강이라, 곧 시작하는 픽업이
                            // 마감 임박과 같은 경고색으로 떴다 — 시작은 다급한 일이 아니다. 앞의
                            // "시작까지" 라벨과도 색이 갈려 한 덩어리로 안 읽혔다.
                            .foregroundStyle(hot ? markColor(mark) : GLGColor.textSecondary)
                            .lineLimit(1)
                            .padding(.horizontal, 7)
                            // 높이는 **못 박는다.** 상하 패딩만 맞추면 줄 상자 높이가 Android 와 달라
                            // 알약 두께가 어긋난다. Android `DDayBadgeHeight` 와 **같이 고쳐야 한다.**
                            .frame(height: 20)
                            .background(hot ? markColor(mark).opacity(0.12) : Color(hex: 0xFFE0E0E0),
                                        in: RoundedRectangle(cornerRadius: 6))
                            .sirenPulse(active: imminent)
                    }
                }
                // 날짜만으로는 부족하다 — D-1 에서 초를 세기 시작하면 "그래서 몇 시에 끝나나"가
                // 바로 다음 질문이 된다. 접속 계획은 시각까지 있어야 세울 수 있다.
                Text(DateUtil.shared.shortDateTime(millis: entry.target))
                    .font(.pretendard(size: 9.5, weight: .semibold))
                    .foregroundStyle(GLGColor.textSecondary.opacity(0.75)).lineLimit(1)
            }
        }
        // 픽업은 **한 단 아래**로 내린다(카드는 하나 그대로다 — 구분선으로만 가른다).
        // 제목 칸 안에 두면 초상 44 가 제목·D-day 와 같은 줄에 얹혀 카드가 세로로 눌린 것처럼
        // 보이고, 얼굴이 글자 사이에 끼여 잘 안 읽힌다.
        //
        // 아이콘 **유무로 거르지 않는다.** 예전엔 URL 이 빈 항목을 통째로 빼서, 하나라도
        // 비면 픽업 줄 전체가 글자로 되돌아갔다(아직 초상이 안 올라온 신규 캐릭터·상류
        // 누락이면 그렇게 된다). 못 받은 칸만 사람 아이콘으로 세운다.
        if !entry.pickups.isEmpty {
            // 구분선 — 일정 한 줄과 픽업은 다른 종류의 정보다. 여백만으로 나누면 초상이
            // 그 줄에 딸린 건지 다음 줄로 넘어간 건지 애매하다.
            Divider().padding(.vertical, 9)
            // 캐릭터와 무기는 **다른 줄**로 가른다. 한 줄에 섞으면 어느 쪽이 캐릭터 픽업인지
            // 초상만 보고는 알 수 없다(무기도 같은 원형 초상으로 온다).
            let chars = entry.pickups.filter { $0.type != "weapon" }
            let weapons = entry.pickups.filter { $0.type == "weapon" }
            // 줄마다 **무엇을 세운 줄인지** 왼쪽에 적는다. 초상만 보면 캐릭터와 무기가
            // 구분되지 않고, 무기는 게임마다 부르는 이름도 다르다(광추·W-엔진).
            let gameOf = GameData.shared.byNameOrNull(name: entry.pickups[0].game)
            // 젠존제는 W-엔진 픽업을 싣지 않는다(EnneadApi.fetchZzz) — 상류가 신규 엔진의
            // 이름을 빈 문자열로 주거나 원문 직역으로 보내서, 그대로 띄우면 없는 이름을
            // 앱이 주장하게 된다. **빠졌다는 사실 자체는 알려야** 해서 안내를 단다.
            if !chars.isEmpty {
                PickupRow(label: "캐릭터", list: chars, showInfo: entry.gameKey == Game.zzz.key)
            }
            if !weapons.isEmpty {
                if !chars.isEmpty { Divider().opacity(0.7).padding(.vertical, 9) }
                PickupRow(label: GameInfoKt.weaponLabelOf(game: gameOf), list: weapons)
            }
        }
        }
        .padding(.horizontal, 11).padding(.vertical, 9)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: 0xFFE0E0E0), lineWidth: 1))
    }

    private func markLabel(_ m: ScheduleMark) -> String {
        switch m {
        case .start: return "시작까지"
        case .estimate: return "예상"
        default: return "종료까지"
        }
    }
    private func markColor(_ m: ScheduleMark) -> Color {
        switch m {
        case .start: return accent.primary
        case .estimate: return estimate
        default: return GLGColor.dangerText
        }
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

/// 진행 중인 콜라보 — 일정 맨 위의 광고형 배너. **접기/펼치기 두 모드.**
///
/// 종료 시각이 미공지라 주간 보드(시간 축)에 못 올린다. 예전엔 그래서 맨 아래 옅은 카드로 밀렸는데,
/// 한정 콜라보는 놓치면 되돌릴 수 없는 일정이라 가장 먼저 읽혀야 한다.
///
/// 기본은 **간략형 한 줄 띠**다. 큰 배너로 세우면 이 페이지의 본론인 주간 보드를 첫 화면에서
/// 밀어낸다 — 눈에 띄어야 하는 것과 자리를 많이 차지하는 것은 다르다. 대신 픽업 목록을 다 보고
/// 싶을 때가 있어 펼치기를 남긴다. 고른 모드는 기기에 남는다(`AppSettings.collabBannerExpanded`)
/// — 한 번 접은 배너가 페이지를 열 때마다 펼쳐져 있으면 접은 의미가 없다.
///
/// (지금 스타레일 × Fate 가 이 상태 — 상류 ennead 가 end_time 을 안 채운다.)
private struct CollabPromoBanner: View {
    let pickups: [GachaBanner]
    let expanded: Bool
    let onToggle: () -> Void

    /// 그라데이션 끝색 — 한 색 평면보다 배너가 앞으로 나와 보인다.
    private let gradientEnd = Color(hex: 0xFF9B5DE5)

    var body: some View {
        let title = pickups.compactMap { GameInfoKt.collabTitle(banner: $0) }.first ?? "종료 미정 픽업"
        let started = pickups.filter { $0.startMillis > 0 }.map { $0.startMillis }.min()
        let names = Array(NSOrderedSet(array: pickups.map { $0.name }.filter { !$0.isEmpty })) as? [String] ?? []
        Button(action: onToggle) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 9) {
                    // 채운 배너 위에서는 배지를 **뒤집는다** — 보라 알약에 흰 글자면 배경에 묻힌다.
                    Text("콜라보").font(.pretendard(size: 9, weight: .black)).foregroundStyle(glCollab)
                        .padding(.horizontal, 7).padding(.vertical, 3)
                        .background(Color.white, in: Capsule())
                    if expanded {
                        Text("진행 중").font(.pretendard(size: 10.5, weight: .bold))
                            .foregroundStyle(.white.opacity(0.85))
                        Spacer(minLength: 0)
                    } else {
                        // 접힌 상태에선 제목·픽업이 한 줄 띠 안으로 들어간다.
                        VStack(alignment: .leading, spacing: 2) {
                            Text(title).font(.pretendard(size: 13, weight: .black))
                                .foregroundStyle(.white).lineLimit(1)
                            // 이름이 길면 잘리되 **종료 미공지는 항상 남긴다** — 종료일을 아는 것처럼 비우면 안 된다.
                            Text((names + ["종료 미정"]).joined(separator: " · "))
                                .font(.pretendard(size: 10, weight: .semibold))
                                .foregroundStyle(.white.opacity(0.82)).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    // 접힘/펼침을 **글자로** 알린다. 화살표만 두면 이게 눌리는 것인지, 눌렀을 때
                    // 무엇이 열리는지가 전달되지 않는다.
                    Text(expanded ? "숨기기" : "보기")
                        .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(.white.opacity(0.2), in: Capsule())
                }
                if expanded {
                    Text(title).font(.pretendard(size: 19, weight: .black)).foregroundStyle(.white)
                        .lineLimit(2).padding(.top, 9)
                    if !names.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(Array(stride(from: 0, to: names.count, by: 2)), id: \.self) { i in
                                HStack(spacing: 6) {
                                    ForEach(names[i..<min(i + 2, names.count)], id: \.self) { n in
                                        Text(n).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(.white)
                                            .lineLimit(1)
                                            .padding(.horizontal, 9).padding(.vertical, 4)
                                            .background(.white.opacity(0.18), in: Capsule())
                                    }
                                }
                            }
                        }
                        .padding(.top, 10)
                    }
                    Text((started.map { "\(DateUtil.shared.shortDate(millis: $0)) 시작 · " } ?? "") + "종료 시각 미공지")
                        .font(.pretendard(size: 10.5, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.8)).padding(.top, 11)
                }
            }
            .padding(.horizontal, expanded ? 16 : 12)
            .padding(.vertical, expanded ? 15 : 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(colors: [glCollab, gradientEnd],
                               startPoint: expanded ? .topLeading : .leading,
                               endPoint: expanded ? .bottomTrailing : .trailing),
                in: RoundedRectangle(cornerRadius: expanded ? 20 : 14, style: .continuous)
            )
        }
        .buttonStyle(.plain)
    }
}

// ── 방송 탭 ────────────────────────────────────────────────────────────────

/**
 버전 특별 방송 — 게임당 다음 한 회. (Compose BroadcastContent 대응)

 일정 타임라인과 나눈 이유: 저쪽은 '언제 끝나나'를 읽는 자리인데 방송은 시작하는 일정이고,
 무엇보다 **역산한 예상**이라 확정된 마감들 사이에 섞이면 같은 무게로 읽힌다.
 */
private struct BroadcastContent: View {
    let banners: [GachaBanner]
    let confirmed: [ConfirmedBroadcast]

    private var list: [LiveBroadcast] {
        BroadcastSchedule.shared.next(banners: banners, confirmed: confirmed, nowMillis: nowMs())
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 안내 문구는 목록 성격에 따라 바꾼다 — 전부 확정인데 '예상'이라고 하면 값을 깎아 읽게 된다.
            Text(list.contains { $0.isEstimate }
                 ? "공식 공지가 뜬 방송은 확정 일시로, 아직 안 뜬 방송은 관례(버전 시작 12일 전 금요일)로 계산한 예상이에요."
                 : "공식 공지로 확정된 일시예요.")
                .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                .padding(.bottom, 14)
            if list.isEmpty {
                // 픽업 배너가 없으면 버전 시작일을 몰라 역산의 근거가 없다 — 추측해서 만들어내지 않는다.
                Text("예상할 수 있는 방송이 없어요.")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
            } else {
                VStack(spacing: 10) {
                    ForEach(list, id: \.gameKey) { BroadcastCard(b: $0) }
                }
            }
        }
    }
}

private struct BroadcastCard: View {
    let b: LiveBroadcast

    var body: some View {
        let gc = Color(argb64: b.colorArgb)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 7) {
                Text(b.gameShort).font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 7).padding(.vertical, 2)
                    .background(gc, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                Text(b.version.isEmpty ? "버전 특별 방송" : "v\(b.version) 특별 방송")
                    .font(.pretendard(size: 13, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                // 예상/확정은 카드마다 붙인다 — 안내 문구를 지나쳐도 여기서 다시 만난다.
                if b.isEstimate {
                    Text("예상").font(.pretendard(size: 9.5, weight: .bold))
                        .foregroundStyle(GLGColor.textSecondary)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(GLGColor.textSecondary.opacity(0.12),
                                    in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                } else {
                    Text("확정").font(.pretendard(size: 9.5, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(glConfirmed, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                }
            }
            Text("\(DateUtil.shared.shortDateTime(millis: b.targetMillis)) (\(DateUtil.shared.weekdayKo(millis: b.targetMillis)))")
                .font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                .padding(.top, 8)
            TimelineView(.periodic(from: .now, by: isImminent(targetMillis: b.targetMillis, nowMillis: nowMS()) ? 1 : 60)) { ctx in
                let now = Int64(ctx.date.timeIntervalSince1970 * 1000)
                let imminent = isImminent(targetMillis: b.targetMillis, nowMillis: now)
                HStack(spacing: 4) {
                    Image(systemName: "play.circle").font(.system(size: 12)).foregroundStyle(gc)
                    Text(b.isLiveVideo ? "예약된 라이브" : "공식 채널에서 생중계")
                        .font(.pretendard(size: 10.5))
                        .foregroundStyle(GLGColor.textSecondary)
                    Spacer(minLength: 0)
                    Text((imminent ? hmsLabel(targetMillis: b.targetMillis, nowMillis: now)
                                   : dhLabel(targetMillis: b.targetMillis, nowMillis: now)) + " 뒤")
                        .font(.pretendard(size: 10.5, weight: .bold))
                        .foregroundStyle(imminent ? glUrgent : GLGColor.textSecondary)
                        .lineLimit(1)
                        .sirenPulse(active: imminent)
                }
                .padding(.top, 6)
            }
            // 갈 곳은 최대 둘 — 근거가 된 공지와 방송 자체. 어느 쪽이 열릴지 이름으로 밝힌다
            // (카드 전체를 누르게 두면 둘 중 뭐가 열릴지 알 수 없다).
            HStack(spacing: 8) {
                if !b.noticeUrl.isEmpty {
                    BroadcastLink(label: "공지 보기", systemImage: "doc.text", tint: gc, url: b.noticeUrl)
                }
                BroadcastLink(
                    label: b.isLiveVideo ? "라이브 보기" : "공식 채널",
                    systemImage: "arrow.up.forward.square", tint: gc, url: b.liveUrl
                )
            }
            .padding(.top, 10)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(gc.opacity(0.35), lineWidth: 1))
    }
}

/// 방송 카드의 링크 한 칸. 둘이 나란히 서도 폭이 같도록 maxWidth 로 늘린다.
/// (Compose BroadcastLink 대응)
private struct BroadcastLink: View {
    let label: String
    let systemImage: String
    let tint: Color
    let url: String

    var body: some View {
        Link(destination: URL(string: url) ?? URL(string: "https://www.youtube.com")!) {
            HStack(spacing: 5) {
                Image(systemName: systemImage).font(.system(size: 11)).foregroundStyle(tint)
                Text(label).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(tint)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 7)
            .background(tint.opacity(0.10), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
