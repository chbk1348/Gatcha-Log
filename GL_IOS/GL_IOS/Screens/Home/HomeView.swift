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

    /// iPad = 분할뷰 detail 안이라 상단바 처리 방식이 다르다(HomeTopBarStyle).
    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    var body: some View {
      // 홈 body 가 몇 번 평가되는지 세는 계측점. 홈은 관측 필드 ~25개를 읽어 재평가가 잦고,
      // body 안에서 alerts·todayTasks 를 계산하므로 "몇 번 도는가"가 곧 비용이다.
      // Instruments → Points of Interest 에서 확인한다(GLGPerf).
      let _ = GLGPerf.event("homeBody")
      GeometryReader { geo in
        ScrollView {
            if isPad {
                // iPad — 히어로 섹션 이전(재구성 전) 홈으로 리버트: 지출 카드·오늘 할 일·일정·소식·저축.
                legacyHomeContent
            } else {
                // iPhone — Figma Make 재구성 홈(그라데이션 히어로 + 퀵액션 + 최근 지출).
                newHomeContent(topInset: geo.safeAreaInsets.top)
            }
        }
        .scrollIndicators(.hidden)
        // iPhone: 스크롤을 상단바 뒤까지 확장해 '투명해진 내비바' 뒤로 실제 그라데이션을 노출.
        // iPad: 그라데이션을 아예 끄므로(흰 히어로) 기본 내비바와 자연스럽게 어울린다 — 특별 처리 없음.
        .modifier(HomeTopBarStyle(isPad: isPad))
        // 히어로 그라데이션 = ScrollView 고정 배경(스크롤 콘텐츠가 아님) → PTR 당김·스크롤에도 그대로 고정.
        // 하단은 라운드 클립 대신 '완전 투명'으로 페이드해 흰 배경과의 경계선을 없앤다(부드럽게 사라짐).
        .background(alignment: .top) {
            if !isPad {
                AmbientHeroGradient(secondary: accent.secondary, primary: accent.primary, glow: store.heroGlow)
                    .frame(height: geo.safeAreaInsets.top + 254)
                    .clipped()   // 글로우가 그라데이션 영역 밖(흰 콘텐츠)으로 새지 않게
                    .ignoresSafeArea(edges: .top)
            }
        }
        .background(GLGBackground { Color.clear })
        .refreshable { store.refreshGameInfo(force: true) }
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
                    NotificationDetailView(alerts: alerts,
                                           onBudget: { showBudget = true },
                                           onGameInfo: { onSwitchTab(2) },
                                           onDismiss: { store.dismissAlert($0.key) },
                                           onDismissAll: { store.dismissAlerts(alerts.map { $0.key }) })
                } label: {
                    Image(systemName: unreadCount > 0 ? "bell.badge" : "bell")
                }
                .simultaneousGesture(TapGesture().onEnded { store.markAlertsRead(alerts.map { $0.key }) })
            }
        }
        .sheet(isPresented: $showBudget) { BudgetSheet(store: store) }
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
    }

    // iPhone — Figma Make 재구성 홈(그라데이션 히어로 + 퀵액션 + 최근 지출 + 일정/소식 + 나를 위한).
    @ViewBuilder
    private func newHomeContent(topInset: CGFloat) -> some View {
        VStack(spacing: 16) {
            // 히어로 — 이번 달 지출 / 예산 현황 캐러셀. 그라데이션을 상태바·내비바 뒤까지 확장.
            // 그라데이션은 히어로 자체가 아니라 ScrollView '고정' 배경으로 그린다(PTR·스크롤에도 안 움직이게).
            HeroBalanceCard(monthlyTotal: monthlyTotal, prevTotal: prevTotal, budget: store.budget,
                            onBudget: { showBudget = true }, topPad: topInset, showGradient: false)

            VStack(alignment: .leading, spacing: 16) {
                if store.hoyoTokenExpired {
                    TokenExpiredBanner { store.requestOpenHoyolabLink(); onSwitchTab(3) }
                }
                if !store.gameInfoReady || !todayTasks.isEmpty {
                    todayTaskView(titleOutside: true)
                }
                RecentSpendCard(spendings: store.spendings, onSeeAll: { onSwitchTab(1) })
                dashboardSlots(titleOutside: true)
                HomeSectionHeader(title: "나를 위한")
                NavigationLink { SavingsPlannerView(store: store) } label: { PickupPlannerHomeCard(store: store) }
                    .buttonStyle(.plain)
                NavigationLink { SavingsChallengeView(store: store) } label: { SavingsChallengeHomeCard(store: store) }
                    .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
            .glgReadableWidth(600)
        }
    }

    // iPad — 히어로 섹션 이전(재구성 전) 대시보드 홈. 지출 카드·오늘 할 일·이번주 일정·게임 소식·저축.
    @ViewBuilder
    private var legacyHomeContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            if store.hoyoTokenExpired {
                TokenExpiredBanner { store.requestOpenHoyolabLink(); onSwitchTab(3) }
            }
            DashboardSpendCard(monthlyTotal: monthlyTotal, budget: store.budget, onTap: { onSwitchTab(1) })
            if !store.gameInfoReady || !todayTasks.isEmpty {
                todayTaskView(titleOutside: false)
            }
            dashboardSlots(titleOutside: false)
            NavigationLink { SavingsPlannerView(store: store) } label: { PickupPlannerHomeCard(store: store) }
                .buttonStyle(.plain)
            NavigationLink { SavingsChallengeView(store: store) } label: { SavingsChallengeHomeCard(store: store) }
                .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .padding(.bottom, 16)
        .glgReadableWidth(600)
    }

    /// 이번 주 일정 · 게임 소식 — **카드마다 자기 데이터가 올 때까지 스켈레톤.**
    ///
    /// 예전엔 `gameInfoReady` 하나로 두 카드를 같이 묶었다. 배너·노트가 디스크 캐시로 즉시 차면서
    /// 스켈레톤이 곧바로 걷히는데, 정작 이 두 카드는 데이터가 없으면 아무것도 안 그려서
    /// 자리를 비웠다가 응답이 온 뒤 튀어나왔다. 출처가 다르니 게이트도 따로 본다.
    @ViewBuilder
    private func dashboardSlots(titleOutside: Bool) -> some View {
        if store.scheduleReady && store.newsReady {
            dashboardSlotBodies(titleOutside: titleOutside)
        } else {
            // 스켈레톤이 여러 개 동시에 뜨는 구간 — 시머 클럭을 하나만 돌린다.
            GLGShimmerClock { dashboardSlotBodies(titleOutside: titleOutside) }
        }
    }

    @ViewBuilder
    private func dashboardSlotBodies(titleOutside: Bool) -> some View {
        if store.scheduleReady {
            DashboardScheduleCard(events: store.gameEvents, challenges: store.challenges,
                                  onTap: { store.requestGameInfoAnchor(.schedule); onSwitchTab(2) },
                                  titleOutside: titleOutside)
        } else {
            DashCardSkeleton(rows: 3)
        }
        if store.newsReady {
            DashboardNewsCard(news: store.gameNews,
                              anniversaries: GameAnniversary.shared.upcoming(nowMillis: nowMs()),
                              onTap: { store.requestGameInfoAnchor(.news); onSwitchTab(2) },
                              titleOutside: titleOutside)
        } else {
            DashCardSkeleton(rows: 2)
        }
    }

    @ViewBuilder
    private func todayTaskView(titleOutside: Bool) -> some View {
        if !store.gameInfoReady {
            TodayTaskSkeleton(titleOutside: titleOutside)
        } else {
            TodayTaskCard(tasks: todayTasks, inProgress: store.checkingIn != nil, titleOutside: titleOutside)
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
                                     monthKey: "\(store.displayYear)-\(store.displayMonth)", nowMillis: nowMs())
            .filter { !store.dismissedAlerts.contains($0.key) }
    }
    private var unreadCount: Int { alerts.filter { !store.readAlerts.contains($0.key) }.count }
    private var todayTasks: [TodayItem] {
        // 픽업은 '이번주 일정' 카드·게임 정보 페이지에서 확인 — 오늘 할 일에서는 제외(urgentBanner: nil)
        HomeLogic.shared.resolveTodayTasks(
            pendingAttendance: HomeLogic.shared.pendingAttendanceCount(attendanceToday: store.attendanceToday),
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
