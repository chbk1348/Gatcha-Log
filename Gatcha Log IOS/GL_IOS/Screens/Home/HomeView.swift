import SwiftUI
import UniformTypeIdentifiers
import Shared

// 홈 — 헤더·지출/예산·오늘 할 일·이번주 일정·게임 소식·알림. (실시간 노트는 오늘 할 일과 중복이라 제거)
// (Compose HomeContent + HomeRedesign 대응) VM 의존 최다. 시작 시 refreshGameInfo 트리거 보존.
struct HomeView: View {
    var store: SpendingStore
    let onSwitchTab: (Int) -> Void
    @Environment(\.glgAccent) private var accent

    @State private var showBudget = false
    @State private var importingGacha = false
    @State private var didStart = false
    /// 호요랜드 상세 — **홈에서 바로 연다.** 예전엔 게임정보 탭으로 옮긴 뒤 그 탭의 앵커가
    /// 상세를 열어, 한 번 탭에 화면이 두 번 바뀌었다(탭 전환이 눈에 보였다).
    @State private var showHoyoland = false
    /// 운영 공지 — 어드민이 올린 것. 첫 프레임은 받아 둔 값으로 서고, 돌아올 때마다 다시 묻는다(`AppNoticeAutoLoad`).
    @State private var notices: [AppNotice] = AppNoticeApi.shared.current

    @Environment(\.horizontalSizeClass) private var hSizeClass

    /**
     넓은 화면인가 — 홈 레이아웃과 상단바 처리가 갈리는 기준.

     **기기 종류(`userInterfaceIdiom`)로 판단하면 안 된다.** 예전엔 `.pad` 인지로 갈랐는데
     같은 iPad 라도 분할뷰에서 좁아지면 iPhone 레이아웃이 맞고, 반대로 폴더블(iPhone Duo)은
     펼쳐도 idiom 이 `.phone` 이라 7.6" 화면에 iPhone 레이아웃이 늘어난다.
     Apple 도 방향·기기 대신 **size class** 로 판단하라고 안내한다.

     내부 디스플레이는 가로·세로 모두 `.regular` 이므로 이 값 하나로 둘 다 잡힌다.
     */
    private var isWide: Bool { hSizeClass == .regular }

    var body: some View {
        // 홈 body 가 몇 번 평가되는지 세는 계측점. 홈은 관측 필드 ~25개를 읽어 재평가가 잦고,
        // body 안에서 alerts·todayTasks 를 계산하므로 "몇 번 도는가"가 곧 비용이다.
        // Instruments → Points of Interest 에서 확인한다(GLGPerf).
        let _ = GLGPerf.event("homeBody")
        ScrollView {
            // 홈 3.0(10/1) — iPhone · iPad 같은 구성.
            homeContent
        }
        .scrollIndicators(.hidden)
        // 좁은 화면만 내비바 바탕을 숨긴다(HomeTopBarStyle) — 본문은 시스템이 바 아래에서 시작시킨다.
        .modifier(HomeTopBarStyle(isWide: isWide))
        // 흰 바탕(10/1) — 히어로 뒤 강조색 그라데이션을 걷었다.
        .background(Color.white)
        // 당겨서 새로고침 — Android 홈(GlgPullToRefreshBox)과 같은 갱신.
        // 갱신이 끝날 때까지 스피너를 붙잡는다 — 바로 돌아오면 당기자마자 스피너가 걷혀 아무 일도 없어 보였다.
        .refreshable { await store.refreshGameInfoAndWait() }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // 프로필 사진(좌) — 탭하면 마이페이지.
            ToolbarItem(placement: .topBarLeading) {
                // 네이티브 Menu 드롭다운 — 예전 .popover 는 iPhone(TabView 위)에서 하단 탭바 아이콘이
                // 사라지는 SwiftUI 버그가 있었다. Menu 는 그 문제가 없다. .tint 로 라벨이 강조색으로
                // 틴트돼 닉네임이 안 보이던 문제도 방지.
                Menu {
                    if store.account.isGuest {
                        Button { store.signIn() } label: {
                            Label("로그인", systemImage: "person.crop.circle.badge.plus")
                        }
                    } else {
                        Button(role: .destructive) { store.signOut() } label: {
                            Label("로그아웃", systemImage: "rectangle.portrait.and.arrow.right")
                        }
                    }
                } label: {
                    HStack(spacing: 8) {
                        ProfileAvatarView(photoUrl: store.account.isGuest ? nil : store.account.photoUrl, size: 32)
                        Text(nickname)
                            .font(.pretendard(size: 15, weight: .bold))
                            .foregroundStyle(GLGColor.textPrimary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)   // 툴바가 폭을 0으로 압축하지 않게
                            .padding(.trailing, 8)
                    }
                    .contentShape(Rectangle())
                }
                .tint(GLGColor.textPrimary)
            }
            // 알림(우).
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    NotificationDetailView(store: store, alerts: alerts,
                                           onGameInfo: { onSwitchTab(2) },
                                           onDismiss: { store.dismissAlert($0.key) },
                                           onDismissAll: { store.dismissAlerts(alerts.map { $0.key }) })
                } label: {
                    Image(systemName: unreadCount > 0 ? "bell.badge" : "bell")
                }
                .simultaneousGesture(TapGesture().onEnded { store.markAlertsRead(alerts.map { $0.key }) })
            }
        }
        .navigationDestination(isPresented: $showHoyoland) { HoyolandDetailView(store: store) }
        // 호요랜드가 열려 있는 동안 iOS 18 의 '+' 를 감춘다 — 행사 페이지에서 지출 추가는 할 일이 아니고,
        // 떠 있는 버튼이 목록 · 배치도를 가렸다(2026-09-28 지적).
        .onChange(of: showHoyoland) { _, _ in store.homeSubpageOpen = showHoyoland || showBudget }
        // 예산 — 설정 ▸ 예산 관리와 같은 페이지를 홈의 하위 페이지로 push(9/30). 알림에서 열 때는 알림의 하위(NotificationDetailView).
        .navigationDestination(isPresented: $showBudget) { BudgetSettingsView(store: store) }
        .onChange(of: showBudget) { _, _ in store.homeSubpageOpen = showHoyoland || showBudget }
        .fileImporter(isPresented: $importingGacha, allowedContentTypes: [.json], allowsMultipleSelection: true) { result in
            if case .success(let urls) = result {
                let contents = urls.compactMap { url -> String? in
                    let s = url.startAccessingSecurityScopedResource(); defer { if s { url.stopAccessingSecurityScopedResource() } }
                    return try? String(contentsOf: url, encoding: .utf8)
                }
                if !contents.isEmpty { store.importGachaFromContents(contents) }
            }
        }
        .task {
            guard !didStart else { return }; didStart = true
            store.refreshGameInfo()       // HomeScreen 시작 로직 보존 (iOS 진입점)
            store.refreshHoyoTokenExpired()
        }
        // HoYoLAB 연동(config)이 늦게 링크되면 그 순간 강제 갱신 — 실시간 노트가 첫 진입에서 누락되는 문제 방지
        .onChange(of: store.hoyolabConfig.isLinked) { _, linked in
            if linked { store.refreshGameInfo(force: true) }
        }
    }

    /// 홈 3.0 — 「지출」 묶음 → 10 띠 → 「게임」 묶음. 묶음 안 섹션 사이는 좌우 20 헤어라인(Android HomeContent 와 같다).
    @ViewBuilder
    private var homeContent: some View {
        VStack(alignment: .leading, spacing: 0) {
            if store.hoyoTokenExpired {
                TokenExpiredBanner { store.requestOpenHoyolabLink(); onSwitchTab(3) }
                    .padding(.bottom, 8)
            }
            // 운영 공지 — 기간이 지나면 스스로 빠진다(AppNoticeApi). 아래 8 은 다음 배너 · 묶음 머리와의 간격(Android 와 같다).
            ForEach(Array(notices.enumerated()), id: \.offset) { _, notice in
                AppNoticeBanner(notice: notice)
                    .padding(.bottom, 8)
            }
            // ── 지출 ──
            groupHeader("지출", "\(Calendar.current.component(.month, from: Date()))월", top: 4)
            homeSection {
                MonthSpendSection(monthlyTotal: monthlyTotal, prevTotal: prevTotal, budget: store.budget,
                                  onBudget: { showBudget = true })
            }
            sectionLine
            homeSection(bottom: 8) { RecentSpendCard(spendings: store.spendings, onSeeAll: { onSwitchTab(1) }) }
            GldsBand()
            // ── 게임 ──
            groupHeader("게임", "오늘 · 이번 주", top: 26)
            // 호요랜드 — 개막 D-60 이내에만 끼어드는 한시 배너(끝나면 스스로 빠진다). 위 18 · 양옆 12 · 아래 4 는
            // 카드 안에서 준다(숨은 날 빈 여백이 남지 않게). 스켈레톤을 두지 않는 건 폴백이 늘 유효해서다.
            HoyolandHomeCard(onTap: { showHoyoland = true })
            // 헤어라인은 앞에 섹션이 있을 때만 — 묶음 머리 · 입장권 바로 다음엔 긋지 않는다(빠진 섹션이 선을 남기지 않게).
            let showToday = !store.gameInfoReady || !todayTasks.isEmpty
            if showToday {
                homeSection(bottom: 8) { todayTaskView() }
            }
            dashboardSlots(lineBefore: showToday)
            // 절약 챌린지는 **마이페이지**로 옮겼다(27.50.0) — 홈은 "지금 무엇을 할까" 를
            // 말하는 자리고, 스트릭·배지는 "내가 얼마나 해왔나" 라 성격이 다르다.
        }
        .glgReadableWidth(640)
        .modifier(AppNoticeAutoLoad(notices: $notices))
    }

    /// 묶음 머리 — 이름 22 Black + 보조 12 회색. 위 `top`(첫 묶음 4 · 띠 다음 26) · 좌우 20.
    private func groupHeader(_ title: String, _ sub: String, top: CGFloat) -> some View {
        HStack(spacing: 8) {
            Text(title).font(.pretendard(size: 22, weight: .black)).foregroundStyle(GLGColor.textPrimary)
            Text(sub).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20).padding(.top, top)
        .background(Color.white)
    }

    /// 묶음 안 섹션 사이 — 좌우 20 들여 쓴 1 헤어라인.
    private var sectionLine: some View {
        GldsHairline(inset: 20).background(Color.white)
    }

    /// 이번 주 일정 · 게임 소식 — **카드마다 자기 데이터가 올 때까지 스켈레톤.**
    ///
    /// 예전엔 `gameInfoReady` 하나로 두 카드를 같이 묶었다. 배너·노트가 디스크 캐시로 즉시 차면서
    /// 스켈레톤이 곧바로 걷히는데, 정작 이 두 카드는 데이터가 없으면 아무것도 안 그려서
    /// 자리를 비웠다가 응답이 온 뒤 튀어나왔다. 출처가 다르니 게이트도 따로 본다.
    @ViewBuilder
    private func dashboardSlots(lineBefore: Bool) -> some View {
        if store.scheduleReady && store.newsReady {
            dashboardSlotBodies(lineBefore: lineBefore)
        } else {
            // 스켈레톤이 여러 개 동시에 뜨는 구간 — 시머 클럭을 하나만 돌린다.
            GLGShimmerClock { dashboardSlotBodies(lineBefore: lineBefore) }
        }
    }

    @ViewBuilder
    private func dashboardSlotBodies(lineBefore: Bool) -> some View {
        if lineBefore { sectionLine }
        homeSection(bottom: 8) {
            if store.scheduleReady {
                DashboardScheduleCard(events: store.gameEvents, challenges: store.challenges,
                                      onTap: { store.requestGameInfoAnchor(.schedule); onSwitchTab(2) })
            } else {
                DashCardSkeleton(rows: 3)
            }
        }
        // 소식이 하나도 없으면 DashboardNewsCard 가 아무것도 안 그리므로 섹션 · 헤어라인째 뺀다.
        let anniversaries = GameAnniversary.shared.upcoming(nowMillis: nowMs())
        if !store.newsReady || !store.gameNews.isEmpty || anniversaries.contains(where: { $0.daysUntil <= 60 }) {
            sectionLine
            homeSection(bottom: 8) {
                if store.newsReady {
                    DashboardNewsCard(news: store.gameNews, anniversaries: anniversaries,
                                      onTap: { store.requestGameInfoAnchor(.news); onSwitchTab(2) })
                } else {
                    DashCardSkeleton(rows: 2)
                }
            }
        }
    }

    /// 홈 섹션(iPhone · iPad, 홈 3.0) — 좌우 20 · 위 18 · 아래 `bottom`(Android HomeSection 과 같다).
    /// `bottom` 은 20 − 마지막 요소의 자체 아래 여백(목록 줄은 vertical 12 라 8).
    private func homeSection<C: View>(bottom: CGFloat = 20, @ViewBuilder _ content: () -> C) -> some View {
        GldsSection(top: 18, bottom: bottom, content: content)
            .background(Color.white)
    }

    @ViewBuilder
    private func todayTaskView() -> some View {
        if !store.gameInfoReady {
            TodayTaskSkeleton()
        } else {
            TodayTaskCard(tasks: todayTasks, inProgress: store.checkingIn != nil)
        }
    }

    // ── 파생 ──
    /// 헤더 닉네임 — 게스트/빈 값 폴백.
    private var nickname: String {
        if store.account.isGuest { return "게스트" }
        return store.profile.name.isEmpty ? "회원" : store.profile.name
    }
    private var monthlyTotal: Int64 { store.monthlyTotal }
    private var prevTotal: Int64 { store.prevMonthTotal }
    // 아래 파생값은 전부 GL_Shared HomeLogic 이 단일 소스 — Android 와 문구·우선순위가 갈리지 않도록.
    private var gameOverBudget: [String] {
        HomeLogic.shared.gameOverBudget(gameBudgets: store.gameBudgets.mapValues { KotlinLong(value: $0) },
                                        totalsByGame: store.monthlyTotalsByGame.mapValues { KotlinLong(value: $0) })
    }
    private var perGameSpend: [GameSpend] {
        HomeLogic.shared.perGameSpend(totalsByGame: store.monthlyTotalsByGame.mapValues { KotlinLong(value: $0) },
                                      gameBudgets: store.gameBudgets.mapValues { KotlinLong(value: $0) })
    }
    private var savingTip: String {
        HomeLogic.shared.savingTip(budget: store.budget, monthlyTotal: monthlyTotal, gameOverBudget: gameOverBudget)
    }
    // 사용자가 삭제(dismiss)한 알림은 제외하고 노출(계산형 알림이라 dismiss 키로 재노출 차단)
    private var alerts: [HomeAlert] {
        HomeLogic.shared.buildAlerts(monthlyTotal: monthlyTotal, budget: store.budget, gameOverBudget: gameOverBudget,
                                     banners: store.activeBanners, attendanceToday: store.attendanceToday,
                                     monthKey: "\(store.displayYear)-\(store.displayMonth)", nowMillis: nowMs(),
                                     attendanceGames: store.trackedAttendanceGames)
            .filter { !store.dismissedAlerts.contains($0.key) }
    }
    private var unreadCount: Int { alerts.filter { !store.readAlerts.contains($0.key) }.count }
    private var todayTasks: [TodayItem] {
        // 픽업은 '이번주 일정' 카드·게임 정보 페이지에서 확인 — 오늘 할 일에서는 제외(urgentBanner: nil)
        HomeLogic.shared.resolveTodayTasks(
            pendingAttendance: HomeLogic.shared.pendingAttendanceCount(attendanceToday: store.attendanceToday, games: store.trackedAttendanceGames),
            resins: HomeLogic.shared.resinAlerts(liveNotes: store.liveNotes),
            urgentBanner: nil, budget: store.budget, monthlyTotal: monthlyTotal,
            combats: HomeLogic.shared.combatDeadlines(combats: store.combat, nowMillis: nowMs()),
            nowMillis: nowMs()
        ).toTodayItems(
            onCheckInAll: { store.checkInAll() },
            onResin: { store.requestGameInfoAnchor(.notes); onSwitchTab(2) },
            onCombat: { store.requestGameInfoAnchor(.combat); onSwitchTab(2) },
            onBanner: { store.requestGameInfoAnchor(.schedule); onSwitchTab(2) },
            onBudget: { showBudget = true })
    }
}

// ── 표시 모델 ──
// 산출 로직과 데이터 모델(GameSpend·ResinAlert·TodayTask·HomeAlert)은 GL_Shared HomeLogic 으로 이관.
// 여기엔 SwiftUI 표현(SF Symbol·탭 이동 클로저)만 남는다.

/// 오늘 할 일 한 줄. busyable=전체출석처럼 진행 중 스피너가 필요한 항목.
///
/// id 는 shared 가 만든 [TodayTask.key] 를 그대로 쓴다 — UUID 를 쓰면 todayTasks 가 computed 라
/// body 평가마다 새 id 가 생겨 ForEach 가 매번 '전부 삭제 + 전부 삽입'으로 처리한다(행 재생성).
/// 종류(kind)로는 안 된다 — 수지·전투 콘텐츠는 해당되는 게임마다 한 줄씩 나와 서로 충돌한다.
struct TodayItem: Identifiable { let id: String; let icon: String; let message: String; let cta: String; let urgent: Bool; let busyable: Bool; let action: () -> Void }

extension Array where Element == TodayTask {
    /// shared [TodayTask] → SwiftUI 표시 모델. 종류별 아이콘·탭 동작 매핑.
    func toTodayItems(onCheckInAll: @escaping () -> Void, onResin: @escaping () -> Void,
                      onCombat: @escaping () -> Void, onBanner: @escaping () -> Void,
                      onBudget: @escaping () -> Void) -> [TodayItem] {
        map { t in
            let icon: String, action: () -> Void
            switch t.kind {
            case .attendance: icon = "checkmark.circle"; action = onCheckInAll
            case .resin:      icon = "bolt.fill";        action = onResin
            case .combat:     icon = "medal";            action = onCombat
            case .banner:     icon = "die.face.5";       action = onBanner
            case .budget:     icon = "banknote";         action = onBudget
            }
            return TodayItem(id: t.key, icon: icon, message: t.message, cta: t.ctaLabel, urgent: t.urgent, busyable: t.busyable, action: action)
        }
    }
}

/// ForEach 식별자 — 알림 키는 종류+기간으로 이미 고유하다.
extension HomeAlert: @retroactive Identifiable {
    public var id: String { key }
}
