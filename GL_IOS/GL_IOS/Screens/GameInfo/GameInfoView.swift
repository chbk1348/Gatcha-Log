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
    /// 도감 — 새로 나온 것 · 방부.
    @State private var showNewContent = false
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
        .navigationDestination(isPresented: $showCalc) { sectionPage("계산기") { GachaCalculatorSection() } }
        .navigationDestination(isPresented: $showReport) { sectionPage("가챠 리포트") { GachaReportSection(store: store, onOpenDashboard: { showDashboard = true }) } }
        .navigationDestination(isPresented: $showGift) { GiftCodePage(store: store) }
        .navigationDestination(isPresented: $showDashboard) { GachaDashboardView(store: store) }
        .navigationDestination(isPresented: $showSchedule) { GameSchedulePage(store: store) }
        .navigationDestination(isPresented: $showNews) { NewsPage(store: store) }
        .navigationDestination(isPresented: $showNewsDetail) {
            if let n = selectedNews { NewsDetailView(store: store, item: n) }
        }
        .navigationDestination(isPresented: $showHoyoland) { HoyolandDetailView() }
        .navigationDestination(isPresented: $showNewContent) { NewContentPage(store: store) }
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
                // 새 버전 알림 — 데일리 바로 아래. 이번 버전에 신규 캐릭터가 있으면 상시로 뜬다(닫기 없음).
                if let b = store.versionBanner {
                    section {
                        NewVersionBannerCard(banner: b, onOpen: { showNewContent = true })
                    }
                }
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
                // 진입 카드 — 도감(무엇이 있는가) + 도구.
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
        // 신규 콘텐츠 — 진입 카드의 점 표시를 위해 목록까지 미리 받는다(내부에서 1회만 실제로 돈다).
        .task { store.loadNewContent() }
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

    /// 하단 진입 카드 석 장(도감 1 + 도구 2).
    ///
    /// ⚠️ 본문에 늘어놓지 않고 여기 모은다 — LazyVStack 자식이 늘수록 타입 추론 비용이 커져
    /// "unable to type-check" 가 나는데, 에러는 손대지도 않은 줄에 찍혀 원인을 가린다.
    @ViewBuilder private var entryCards: some View {
        section {
            navEntry(icon: "sparkles", title: "새로 나온 것", sub: "이번 버전 신규 캐릭터 · 무기 · 방부",
                     badge: store.newContentUnseen) { showNewContent = true }
        }
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
    /// - Parameter badge: 안 본 게 있으면 제목 옆에 점 하나. 숫자 배지는 과하다 — 몇 개인지가 중요하지 않다.
    @ViewBuilder private func navEntry(icon: String, title: String, sub: String, badge: Bool = false,
                                       action: @escaping () -> Void) -> some View {
        Button(action: action) {
            GLGCard(cornerRadius: 20, padding: 16) {
                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous).fill(accent.primary.opacity(0.12)).frame(width: 44, height: 44)
                        Image(systemName: icon).font(.pretendard(size: 18, weight: .semibold)).foregroundStyle(accent.primary)
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(title).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            if badge { Circle().fill(GLGColor.dangerText).frame(width: 6, height: 6) }
                        }
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
        Text("마감이 가까운 순서로 정리했어요.")
            .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 14)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 페이지 타이틀은 네비게이션 바(뒤로가기 + 타이틀)로 — Android 상세 헤더와 동일 형식.
                Picker("보기", selection: $tab.animation(.easeInOut(duration: 0.2))) {
                    Text("일정").tag(0)
                    Text("타임라인").tag(1)
                    Text("방송").tag(2)
                    Text("주년").tag(3)
                }
                .pickerStyle(.segmented)
                .padding(.bottom, 14)

                if tab == 1 {
                    TimelineContent(store: store)
                } else if tab == 2 {
                    BroadcastContent(banners: store.activeBanners, confirmed: store.confirmedBroadcasts)
                } else if tab == 3 {
                    AnniversaryContent()
                } else {
                scheduleTitle
                let days = sched.days
                let undated = sched.undated
                let summary = sched.summary
                if days.isEmpty && undated.isEmpty {
                    Text("예정된 일정이 없어요.")
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
                } else {
                    if let summary { SummaryStrip(s: summary) }
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
        // 일정 집계는 필터/원본이 바뀔 때만. 예전엔 body 첫 줄에서 5종을 조건 없이 계산해,
        // '주년' 탭을 보고 있어도(그때는 하나도 안 쓰는데) 세그먼트를 누를 때마다 전부 다시 돌았다.
        .task(id: scheduleKey) { sched = Self.buildSchedule(store: store) }
    }

    /// 일정 탭이 쓰는 집계 묶음.
    struct SchedulePageData {
        var days: [ScheduleDay] = []
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
    }

    private var scheduleKey: ScheduleKey {
        ScheduleKey(banners: store.activeBanners,
                    events: store.gameEvents, challenges: store.challenges)
    }

    private static func buildSchedule(store: SpendingStore) -> SchedulePageData {
        let now = nowMs()
        let entries = ScheduleLogic.shared.buildSchedule(
            banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
        return SchedulePageData(
            days: ScheduleLogic.shared.buildDays(entries: entries, nowMillis: now),
            undated: ScheduleLogic.shared.undatedPickups(banners: store.activeBanners),
            summary: ScheduleLogic.shared.summarize(banners: store.activeBanners, entries: entries, nowMillis: now)
        )
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

// ── 타임라인 탭 ────────────────────────────────────────────────────────────

/**
 간트형 가로 타임라인 — 게임별 한 행, 픽업 기간을 막대로. (Compose TimelineContent 대응)

 일정 탭(마감일 세로 목록)과 답하는 질문이 다르다. 저쪽은 "다음에 뭐가 끝나나"지만
 여기는 **기간과 겹침**이다 — 두 게임 픽업이 같은 주에 몰렸는지, 이번 픽업이 끝나고
 다음이 시작할 때까지 빈 구간이 있는지는 막대를 나란히 놓아야 보인다.

 좌표는 전부 `TimelineLogic` 이 준 비율(0~1)이고 여기서는 폭만 곱한다.
 */
private struct TimelineContent: View {
    var store: SpendingStore
    @State private var timeline: Timeline? = nil

    private struct TimelineKey: Equatable {
        let banners: [GachaBanner]
        let events: [GameEvent]
        let challenges: [GameChallenge]
    }

    private var key: TimelineKey {
        TimelineKey(banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("픽업 기간을 나란히 놓고 봅니다. 이벤트·정기 콘텐츠는 상류가 시작 시각을 주지 않아 마감 지점만 찍혀요.")
                .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, 14)
            if let t = timeline, !t.isEmpty {
                GLGCard(cornerRadius: 20, padding: 14) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("앞으로 \(t.days)일")
                            .font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                            .padding(.leading, timelineLabelWidth).padding(.bottom, 8)
                        timelineAxis(t)
                        Spacer().frame(height: 6)
                        ForEach(Array(t.rows.enumerated()), id: \.offset) { i, row in
                            if i > 0 { Spacer().frame(height: 4) }
                            timelineRow(row, nowFraction: CGFloat(t.nowFraction))
                        }
                        timelineLegend().padding(.top, 12)
                    }
                }
            } else if timeline != nil {
                Text("표시할 일정이 없어요.")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
            }
        }
        // 집계는 원본 3종이 바뀔 때만 — body 평가마다 다시 만들면 스크롤이 무거워진다.
        .task(id: key) {
            let entries = ScheduleLogic.shared.buildSchedule(
                banners: store.activeBanners, events: store.gameEvents, challenges: store.challenges)
            timeline = TimelineLogic.shared.build(entries: entries, banners: store.activeBanners, nowMillis: nowMs())
        }
    }

    /// 게임 이름이 들어가는 좌측 고정 폭 — 축과 행이 같은 값을 써야 눈금과 막대가 맞는다.
    private var timelineLabelWidth: CGFloat { 46 }
    private var rowHeight: CGFloat { 30 }
    private var barHeight: CGFloat { 18 }

    /// 날짜 눈금 줄.
    @ViewBuilder
    private func timelineAxis(_ t: Timeline) -> some View {
        HStack(spacing: 0) {
            Spacer().frame(width: timelineLabelWidth)
            GeometryReader { geo in
                ZStack(alignment: .topLeading) {
                    ForEach(Array(t.ticks.enumerated()), id: \.offset) { _, tick in
                        // 마지막 눈금은 라벨이 오른쪽으로 넘치므로 당겨 붙인다.
                        let atEnd = tick.fraction > 0.92
                        Text(tick.label).font(.pretendard(size: 9.5)).foregroundStyle(GLGColor.textSecondary)
                            .lineLimit(1)
                            .offset(x: geo.size.width * CGFloat(tick.fraction) - (atEnd ? 22 : 0))
                    }
                }
            }
            .frame(height: 14)
        }
    }

    /// 게임 한 행 — 좌측 이름 + 막대들 + 마감 표식.
    @ViewBuilder
    private func timelineRow(_ row: TimelineRow, nowFraction: CGFloat) -> some View {
        let color = Color(argb64: row.colorArgb)
        HStack(spacing: 0) {
            Text(row.gameShort).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(color)
                .lineLimit(1).minimumScaleFactor(0.8)
                .frame(width: timelineLabelWidth, alignment: .leading).padding(.trailing, 6)
            GeometryReader { geo in
                let w = geo.size.width
                ZStack(alignment: .leading) {
                    // 바닥 트랙 — 막대가 없는 구간도 '아무것도 없는 기간'으로 읽히게 한다.
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .fill(Color(hex: 0xFFF2F3F6))
                        .frame(height: barHeight)
                    // 오늘 선
                    Rectangle().fill(glUrgent.opacity(0.35))
                        .frame(width: 1, height: rowHeight)
                        .offset(x: w * nowFraction)
                    ForEach(Array(row.bars.enumerated()), id: \.offset) { _, bar in
                        timelineBar(bar, color: color, width: w)
                    }
                    ForEach(Array(row.marks.enumerated()), id: \.offset) { _, mark in
                        Circle().fill(scheduleKindColor(mark.kind))
                            .frame(width: 6, height: 6)
                            .offset(x: w * CGFloat(mark.fraction) - 3, y: rowHeight / 2 - 3)
                    }
                }
                .frame(height: rowHeight)
            }
            .frame(height: rowHeight)
        }
        .frame(height: rowHeight)
    }

    /// 기간 막대 하나. 진행 중이면 채우고, 예정이면 옅게.
    @ViewBuilder
    private func timelineBar(_ bar: TimelineBar, color: Color, width: CGFloat) -> some View {
        // 아주 짧은 기간(하루)도 보이도록 최소 폭을 준다 — 안 그러면 선으로 사라진다.
        let barWidth = max(width * CGFloat(bar.widthFraction), 6)
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
                .fill(bar.ongoing ? color : color.opacity(0.28))
            // 종료 미공지는 테두리를 둘러 '여기서 끝난 게 아니다'를 알린다.
            if bar.endUnknown {
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .stroke(color.opacity(0.55), lineWidth: 1)
            }
            // 라벨은 막대가 글자를 담을 만큼 넓을 때만 — 좁은 막대에 글자를 우겨넣으면 둘 다 못 읽는다.
            if barWidth >= 44 {
                Text(bar.title).font(.pretendard(size: 9.5, weight: .bold))
                    .foregroundStyle(bar.ongoing ? Color.white : GLGColor.textPrimary)
                    .lineLimit(1).padding(.horizontal, 5)
            }
        }
        .frame(width: barWidth, height: barHeight)
        .offset(x: width * CGFloat(bar.startFraction))
    }

    /// 범례 — 색이 무엇을 뜻하는지 한 줄.
    @ViewBuilder
    private func timelineLegend() -> some View {
        HStack(spacing: 12) {
            legendItem(GLGColor.textSecondary.opacity(0.5), "예정")
            legendItem(scheduleKindColor("이벤트"), "이벤트 마감")
            legendItem(scheduleKindColor("콘텐츠"), "콘텐츠 마감")
            HStack(spacing: 5) {
                Rectangle().fill(glUrgent).frame(width: 1, height: 10)
                Text("오늘").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
            }
        }
    }

    @ViewBuilder
    private func legendItem(_ color: Color, _ label: String) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
        }
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
        return Link(destination: URL(string: b.liveUrl) ?? URL(string: "https://www.youtube.com")!) {
            VStack(alignment: .leading, spacing: 0) {
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
                        Text("공식 채널에서 생중계").font(.pretendard(size: 10.5))
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
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(gc.opacity(0.35), lineWidth: 1))
        }
        .buttonStyle(.plain)
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
            // 남은 시간 — 날짜 노드의 D-N 은 '며칠 남았나'만 알려 주지만, 마감 당일엔 몇 시간이
            // 남았는지가 실제로 필요한 정보다(D-DAY 만으로는 지금 해야 하는지 판단이 안 된다).
            // 24시간 안쪽이면 초까지 세고 사이렌처럼 명멸시킨다.
            //
            // 갱신 주기를 항목마다 나눈다 — 며칠 남은 일정에 초를 세어 봐야 화면은 그대로인데
            // 다시 그리는 횟수만 60배가 된다. TimelineView 는 시스템이 라이프사이클을 관리하므로
            // 화면을 벗어나면 알아서 멈춘다.
            TimelineView(.periodic(from: .now, by: isImminent(targetMillis: e.target, nowMillis: nowMS()) ? 1 : 60)) { ctx in
                let now = Int64(ctx.date.timeIntervalSince1970 * 1000)
                let imminent = isImminent(targetMillis: e.target, nowMillis: now)
                let remain = imminent
                    ? hmsLabel(targetMillis: e.target, nowMillis: now)
                    : dhLabel(targetMillis: e.target, nowMillis: now)
                HStack(spacing: 6) {
                    if !e.sub.isEmpty {
                        Text(e.sub).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    Text(remain == "종료" ? remain : "\(remain) 남음")
                        .font(.pretendard(size: 10.5, weight: .bold))
                        .foregroundStyle(imminent ? glUrgent : GLGColor.textSecondary)
                        .lineLimit(1)
                        .sirenPulse(active: imminent)
                }
                .padding(.top, 4)
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

/// 픽업 칩 — 캐릭터 먼저, 무기(광추·W-엔진)는 그다음. 둘 다 **이름 그대로** 노출한다.
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
