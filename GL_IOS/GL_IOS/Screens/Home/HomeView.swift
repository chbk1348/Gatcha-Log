import SwiftUI
import UniformTypeIdentifiers
import Shared

// 홈 — 헤더·지출/예산·오늘 할 일·이번주 일정·게임 소식·알림. (실시간 노트는 오늘 할 일과 중복이라 제거)
// (Compose HomeContent + HomeRedesign 대응) VM 의존 최다. 시작 시 refreshGameInfo 트리거 보존.
struct HomeView: View {
    @ObservedObject var store: SpendingStore
    let onSwitchTab: (Int) -> Void
    @Environment(\.glgAccent) private var accent

    @State private var showBudget = false
    @State private var showHomeEdit = false
    @State private var importingGacha = false
    @State private var didStart = false
    /// 콘텐츠 로드인 스태거 — 첫 표시 1회만 등장(스크롤 재진입 시 재애니메이션 방지용 인덱스 보관).
    @State private var appeared: Set<Int> = []

    /// iPad = 분할뷰 detail 안이라 상단바 처리 방식이 다르다(HomeTopBarStyle).
    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    var body: some View {
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
                AmbientHeroGradient(secondary: accent.secondary, primary: accent.primary)
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
        .sheet(isPresented: $showHomeEdit) { HomeCardEditSheet(store: store) }
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
                .glgLoadIn(1, appeared: $appeared)

            VStack(alignment: .leading, spacing: 16) {
                if store.hoyoTokenExpired {
                    TokenExpiredBanner { store.requestOpenHoyolabLink(); onSwitchTab(3) }
                        .glgLoadIn(0, appeared: $appeared)
                }
                if !store.gameInfoReady || !todayTasks.isEmpty {
                    todayTaskView(titleOutside: true).glgLoadIn(3, appeared: $appeared)
                }
                RecentSpendCard(spendings: store.spendings, onSeeAll: { onSwitchTab(1) })
                    .glgLoadIn(4, appeared: $appeared)
                if !store.gameInfoReady {
                    DashCardSkeleton(rows: 3).glgLoadIn(5, appeared: $appeared)
                    DashCardSkeleton(rows: 2).glgLoadIn(6, appeared: $appeared)
                } else {
                    DashboardScheduleCard(events: store.gameEvents, challenges: store.challenges, onTap: { store.requestGameInfoAnchor(.schedule); onSwitchTab(2) }, titleOutside: true)
                        .glgLoadIn(5, appeared: $appeared)
                    DashboardNewsCard(news: store.gameNews, anniversaries: GameAnniversary.shared.upcoming(nowMillis: nowMs()), onTap: { store.requestGameInfoAnchor(.news); onSwitchTab(2) }, titleOutside: true)
                        .glgLoadIn(6, appeared: $appeared)
                }
                HomeSectionHeader(title: "나를 위한").glgLoadIn(7, appeared: $appeared)
                NavigationLink { SavingsPlannerView(store: store) } label: { PickupPlannerHomeCard(store: store) }
                    .buttonStyle(.plain).glgLoadIn(8, appeared: $appeared)
                NavigationLink { SavingsChallengeView(store: store) } label: { SavingsChallengeHomeCard(store: store) }
                    .buttonStyle(.plain).glgLoadIn(9, appeared: $appeared)
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
                    .glgLoadIn(0, appeared: $appeared)
            }
            DashboardSpendCard(monthlyTotal: monthlyTotal, budget: store.budget, onTap: { onSwitchTab(1) })
                .glgLoadIn(1, appeared: $appeared)
            if !store.gameInfoReady || !todayTasks.isEmpty {
                todayTaskView(titleOutside: false).glgLoadIn(2, appeared: $appeared)
            }
            if !store.gameInfoReady {
                DashCardSkeleton(rows: 3).glgLoadIn(3, appeared: $appeared)
                DashCardSkeleton(rows: 2).glgLoadIn(4, appeared: $appeared)
            } else {
                DashboardScheduleCard(events: store.gameEvents, challenges: store.challenges, onTap: { store.requestGameInfoAnchor(.schedule); onSwitchTab(2) })
                    .glgLoadIn(3, appeared: $appeared)
                DashboardNewsCard(news: store.gameNews, anniversaries: GameAnniversary.shared.upcoming(nowMillis: nowMs()), onTap: { store.requestGameInfoAnchor(.news); onSwitchTab(2) })
                    .glgLoadIn(4, appeared: $appeared)
            }
            NavigationLink { SavingsPlannerView(store: store) } label: { PickupPlannerHomeCard(store: store) }
                .buttonStyle(.plain).glgLoadIn(5, appeared: $appeared)
            NavigationLink { SavingsChallengeView(store: store) } label: { SavingsChallengeHomeCard(store: store) }
                .buttonStyle(.plain).glgLoadIn(6, appeared: $appeared)
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .padding(.bottom, 16)
        .glgReadableWidth(600)
    }

    @ViewBuilder
    private func todayTaskView(titleOutside: Bool) -> some View {
        if !store.gameInfoReady {
            TodayTaskSkeleton(titleOutside: titleOutside)
        } else {
            TodayTaskCard(tasks: todayTasks, inProgress: store.checkingIn != nil, titleOutside: titleOutside)
        }
    }

    private var homeEditButton: some View {
        Button { showHomeEdit = true } label: {
            HStack(spacing: 6) {
                Image(systemName: "slider.horizontal.3").font(.pretendard(size: 16)).foregroundStyle(GLGColor.textSecondary)
                Text("홈 카드 편집").font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 12)
        }.buttonStyle(.plain)
    }

    // ── 파생 ──
    /// 헤더 닉네임 — 게스트/빈 값 폴백.
    private var nickname: String {
        if store.account.isGuest { return "게스트" }
        return store.profile.name.isEmpty ? "회원" : store.profile.name
    }
    private var monthlyTotal: Int64 { store.monthlyTotal() }
    private var prevTotal: Int64 { store.prevMonthTotal() }
    // 아래 파생값은 전부 GL_Shared HomeLogic 이 단일 소스 — Android 와 문구·우선순위가 갈리지 않도록.
    private var gameOverBudget: [String] {
        HomeLogic.shared.gameOverBudget(gameBudgets: store.gameBudgets.mapValues { KotlinLong(value: $0) },
                                        totalsByGame: store.monthlyTotalsByGame().mapValues { KotlinLong(value: $0) })
    }
    private var perGameSpend: [GameSpend] {
        HomeLogic.shared.perGameSpend(totalsByGame: store.monthlyTotalsByGame().mapValues { KotlinLong(value: $0) },
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
struct TodayItem: Identifiable { let id = UUID(); let icon: String; let message: String; let cta: String; let urgent: Bool; let busyable: Bool; let action: () -> Void }

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
            return TodayItem(icon: icon, message: t.message, cta: t.ctaLabel, urgent: t.urgent, busyable: t.busyable, action: action)
        }
    }
}

/// ForEach 식별자 — 알림 키는 종류+기간으로 이미 고유하다.
extension HomeAlert: @retroactive Identifiable {
    public var id: String { key }
}
