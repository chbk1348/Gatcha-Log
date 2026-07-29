import SwiftUI
import Shared

/// 앱 루트 — 온보딩(로그인/게스트) → 네이티브 탭바 화면
///
/// 탭바는 SwiftUI TabView(= UITabBarController) 라서 iOS 26 에서는 시스템
/// 리퀴드 글래스(블러·스크롤 축소·모핑)가 자동 적용되고, iOS 16~18 에서는
/// 해당 버전의 시스템 머티리얼 탭바로 표시된다. 탭 콘텐츠는 전부 네이티브 SwiftUI.
///
/// 지출 추가 버튼 (iOS 26 가이드라인):
///  - iOS 26: 탭바와 같은 높이에 분리된 원형 글래스 버튼 (Mail 컴포즈 버튼 패턴 — Tab role 사용)
///  - iOS 16~25: 탭바 위 우측의 글래스 원형 버튼 (오버레이 폴백)
struct ContentView: View {
    /// Kotlin SpendingViewModel 브리지(공유 VM). 온보딩 게이트·강조색을 SwiftUI 에서 직접 구독.
    @State private var store = SpendingStore.shared
    @State private var selectedTab: Int = 0
    /// 앱 복귀 감지 — 밀린 알림 점검 트리거(BGAppRefreshTask 는 실행 시점이 OS 재량이라 보조가 필요).
    @Environment(\.scenePhase) private var scenePhase

    /// 지출 추가/수정 시트 — **표시 여부와 대상을 한 상태로 합쳤다**(nil = 닫힘).
    ///
    /// 예전엔 showAddSpending(Bool) + editingSpending(Spending?) 두 개였는데, `.sheet(isPresented:)` 는
    /// 표시 시점에 내용을 만들면서 아직 반영되지 않은 editingSpending(=nil)을 집어갈 수 있었다.
    /// 그러면 '수정'을 눌렀는데 빈 '추가' 폼이 뜬다 — 타이밍에 따라 갈려 간헐적으로 재현됐다.
    /// AddSpendingView 의 didInit 가드가 nil 로 한 번 초기화되면 필드를 다시 채우지 않아 증상이 굳었다.
    /// `.sheet(item:)` 은 대상 값을 표시 시점에 확정해 넘기므로 이 경합 자체가 성립하지 않는다.
    @State private var spendingSheet: SpendingSheetTarget? = nil

    /// 지출 시트의 대상. `.sheet(item:)` 에 넘기기 위해 Identifiable.
    private enum SpendingSheetTarget: Identifiable {
        case add
        case edit(Spending)

        var id: String {
            switch self {
            case .add: return "add"
            case .edit(let spending): return "edit-\(spending.id)"
            }
        }

        var spending: Spending? {
            switch self {
            case .add: return nil
            case .edit(let spending): return spending
            }
        }
    }
    /// 서브페이지(연간 리포트·알림 상세 등)가 열린 탭 집합 — 해당 탭에서만 탭바 숨김.
    /// (전역 단일 플래그는 탭 전환 시 상태가 어긋나므로 탭별로 독립 관리)
    @State private var tabsWithSubPage: Set<Int> = []

    /// 초기 클라우드 동기화 게이트(로딩 화면) 활성 여부 — 게이트 동안 탭바·추가 버튼 숨김
    @State private var syncGateActive: Bool = MainViewControllerKt.isSyncGateActive()

    /// 첫 실행 온보딩(앱 소개 4페이지) 필요 여부 — 로그인보다 앞. 기기 단위 플래그라 재설치 전까지 1회만.
    /// (기존 유저는 AppSettings.onboardingDone 기본값이 notifPermAsked 라, 업데이트해도 다시 보지 않는다)
    @State private var needsIntro: Bool = !AppSettings().onboardingDone

    /// 앱 강조색 — Kotlin 테마(accentIndex)와 연동된 탭 아이콘 틴트 (초기값: 민트)
    @State private var accent = Color(red: 0.204, green: 0.820, blue: 0.714)

    /// 루트 상태(온보딩→로그인→로딩→탭) — 크로스페이드 트랜지션 키. NavigationStack/스와이프백은 손대지 않고
    /// 루트 교체만 부드럽게 한다(즉시 교체 → standard 페이드).
    private enum RootPhase { case intro, login, loading, tabs }
    private var rootPhase: RootPhase {
        if needsIntro { return .intro }
        if store.needsLogin { return .login }
        if syncGateActive { return .loading }
        return .tabs
    }

    /// 로그인 전(온보딩·로그인) 화면의 강조색 — **사용자 테마를 따르지 않고 브랜드 민트로 고정**(index 0).
    ///
    /// 테마는 계정에 딸린 설정이라 로그인 이후에 불러오는 게 맞다. 그런데 iOS 는 앱을 지워도 Keychain 이
    /// 남아 재설치 후 자동 로그인되고, 클라우드에서 강조색까지 복원된다 — 그러면 아직 로그인 화면인데
    /// 남의 테마 색이 칠해지고, 테마를 읽을 수 없는 런치스크린(항상 아이콘 민트)과도 어긋난다.
    /// 로그인 전 구간은 앱 아이콘의 색으로 통일한다.
    private let preLoginAccent = 0

    var body: some View {
        Group {
            if needsIntro {
                // 첫 실행 온보딩 — 앱 아이콘의 게이지 링을 페이지마다 다른 의미로 변주해 소개하고,
                // 마지막 페이지에서 맥락과 함께 알림 권한을 요청한다.
                OnboardingView { requestNotification in
                    finishOnboarding(requestNotification: requestNotification)
                }
                .glgAccent(index: preLoginAccent)
                .transition(.opacity)
            } else if store.needsLogin {
                // Phase 1 — SwiftUI 네이티브 로그인 (구 ComposeView LoginViewController 대체).
                // 로그인 완료 시 공유 VM 의 account 가 바뀌어 자동으로 탭 화면으로 전환.
                LoginView(store: store)
                    .glgAccent(index: preLoginAccent)
                    .transition(.opacity)
            } else if syncGateActive {
                // 로그인 유저 초기 클라우드 동기화 게이트 — 완료 전 로컬 편집이 클라우드를 덮어쓰는 레이스 방지.
                // 완료 시 syncLoadingDone 설정 → observeSyncGate 가 syncGateActive=false 로 전환해 탭 화면 진입.
                AccountLoadingView(loading: store.initialSyncing) {
                    store.markSyncLoadingDone()
                    syncGateActive = false
                }
                .glgAccent(index: store.accentIndex)
                .transition(.opacity)
            } else {
                // 로그인 후 메인 — iPad 는 사이드바 분할뷰, iPhone 은 하단 탭바.
                authenticatedRoot
                    // 지출 추가/수정 — Phase 6: SwiftUI 네이티브 폼 (구 ComposeView AddSpendingViewController 대체)
                    .sheet(item: $spendingSheet) { target in
                        AddSpendingView(store: store, editing: target.spending) { spendingSheet = nil }
                            .presentationDragIndicator(.visible)
                    }
                    .transition(.opacity)
            }
        }
        // 로그아웃 진행 오버레이 — Firebase signOut 네트워크 대기 동안 피드백이 없던 문제.
        // 앱 루트에 한 번만(토스트와 동일 원칙). Android SignOutOverlay 와 대응.
        .overlay {
            if store.signingOut {
                ZStack {
                    Color.black.opacity(0.4).ignoresSafeArea()
                    VStack(spacing: 14) {
                        ProgressView().controlSize(.large).tint(accent)
                        Text("로그아웃 중").font(.pretendard(size: 15, weight: .bold))
                    }
                    .padding(.horizontal, 32).padding(.vertical, 24)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
                }
                .transition(.opacity)
            }
        }
        .animation(GLGMotion.standard(), value: store.signingOut)
        // 강제 업데이트 — 현재 버전이 최소 지원 버전 미만이면 앱 전체를 덮는 닫히지 않는 화면.
        // (데이터 꼬임 방지·구버전 유지보수 종료. iOS 는 사이드로딩이라 '지금 업데이트'가 릴리스 페이지를 연다.)
        .overlay {
            if store.forceUpdate {
                ZStack {
                    Color(.systemBackground).ignoresSafeArea()
                    VStack(spacing: 16) {
                        Image(systemName: "arrow.down.circle.fill")
                            .font(.system(size: 52, weight: .semibold)).foregroundStyle(accent)
                        Text("필수 업데이트").font(.pretendard(size: 22, weight: .heavy))
                            .foregroundStyle(GLGColor.textPrimary)
                        Text("데이터 꼬임을 막기 위해 이전 버전 지원이 종료됐어요.\n계속하려면 업데이트가 필요해요.")
                            .font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
                            .multilineTextAlignment(.center).lineSpacing(3)
                        if !store.updateVersionName.isEmpty {
                            Text("최신 버전 v\(store.updateVersionName)")
                                .font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(accent)
                        }
                        Button { store.startInAppUpdate() } label: {
                            Text("지금 업데이트").font(.pretendard(size: 16, weight: .bold))
                                .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 15)
                                .background(accent, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .padding(.top, 6)
                    }
                    .padding(.horizontal, 36)
                }
                .transition(.opacity)
            }
        }
        .animation(GLGMotion.standard(), value: store.forceUpdate)
        // 루트 상태 전환(로그인→로딩→탭)만 standard 크로스페이드 — 네이티브 내비 UX 보존.
        .animation(GLGMotion.standard(), value: rootPhase)
        .onAppear {
            // 테마(액센트) 변경 구독 — 마이페이지에서 테마를 바꾸면 탭바 틴트도 즉시 반영
            MainViewControllerKt.observeAccentColor { argb in
                accent = Color(argb: argb.int64Value)
            }
            // 초기 동기화 게이트 구독 — 로딩 화면 동안 탭바·추가 버튼 숨김
            MainViewControllerKt.observeSyncGate { active in
                syncGateActive = active.boolValue
            }
        }
        // 실행 시 원격 매니페스트로 강제 업데이트 여부 확인(구버전이면 store.forceUpdate=true).
        .task { store.checkForUpdate(manual: false) }
        // 현재 탭 인덱스를 Kotlin 과 동기화 — 토스트를 보이는 탭에서만 컴포즈하기 위함
        // (탭 4(추가 버튼)는 실제 탭이 아니므로 제외)
        // iOS 16 호환을 위해 1-파라미터 onChange 사용 (2-파라미터 버전은 iOS 17+)
        .onChange(of: selectedTab) { _, newValue in
            if newValue != 4 {
                MainViewControllerKt.setSelectedTab(tab: Int32(newValue))
            }
        }
        // 알림 탭 → 해당 탭으로 이동(AppDelegate.didReceive 가 glgOpenTab 으로 탭 인덱스 전달).
        .onReceive(NotificationCenter.default.publisher(for: .glgOpenTab)) { note in
            if let tab = note.object as? Int, !syncGateActive, !needsIntro, !store.needsLogin {
                selectedTab = tab
            }
        }
        // 전역 단일 토스트 — 화면마다 붙이면 탭/페이지마다 중복 표시되므로 앱 루트에서 한 번만 노출·소비.
        .glgToast(message: store.statusMessage, bottomPadding: 64) { store.clearStatus() }
        // 네트워크 미연결 — 앱 진입·로딩·새로고침 공통 얼럿 모달(앱 루트에 한 번만).
        .alert("인터넷 연결 없음", isPresented: Binding(
            get: { store.networkAlert != nil },
            set: { if !$0 { store.clearNetworkAlert() } }
        )) {
            Button("확인", role: .cancel) { store.clearNetworkAlert() }
        } message: {
            Text(store.networkAlert ?? "")
        }
        // 강조색을 **최상위에서** 주입한다. 예전엔 탭 콘텐츠에만 걸어서, 탭 바깥에 붙는 것들
        // (지출 추가/수정 시트·전역 토스트·네트워크 얼럿)이 주입이 없는 환경을 물려받아
        // 사용자가 무슨 테마를 골랐든 항상 기본 민트로 떴다.
        // 로그인 전 화면은 더 안쪽에서 preLoginAccent 로 덮어쓰므로(가까운 쪽이 이긴다) 그대로 민트로 남는다.
        .glgAccent(index: store.accentIndex)
        // 앱으로 돌아올 때마다 밀린 알림 1회 점검 — BGAppRefreshTask 는 실행 시점이 OS 재량이고
        // 앱이 강제 종료돼 있으면 아예 안 돌아, 그것만으론 알림이 토글 켤 때만 오는 것처럼 보였다.
        // (실제 실행 여부·최소 간격은 공유 VM 이 판단한다.)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { store.onAppForeground() }
        }
        // 알림 딥링크 — AppDelegate 가 던진 링크를 공유 VM 에 넘긴다(상세 진입은 각 탭이 이어받음).
        .onReceive(NotificationCenter.default.publisher(for: .glgDeepLink)) { note in
            if let link = note.object as? String { store.handleNotificationLink(link) }
        }
        .onChange(of: store.pendingTab) { _, tab in
            if let tab { selectedTab = tab; store.consumePendingTab() }
        }
    }

    /// '+' (지출 추가) 모달 열기 — 신규 추가(편집 대상 없음).
    private func openAddSpending() {
        spendingSheet = .add
    }

    /**
     온보딩 종료 — 다시 뜨지 않도록 플래그를 굳히고, 알림을 켜기로 했으면 실제로 켠다.

     [requestNotification] 은 OS 권한만이 아니라 **앱 내부 알림 토글까지** 함께 켠다.
     권한만 받고 토글이 전부 꺼진 채로 두면 "알림 켜고 시작하기"를 눌러도 알림이 한 건도 오지 않는다.
     켜는 항목은 온보딩 ④에서 약속한 것과 같다 — 픽업 마감·예산 초과·재화(레진) 가득 참.
     (VM 세터가 네이티브 스케줄 갱신까지 처리)

     "나중에 할게요"면 프롬프트를 띄우지 않으므로 notifPermAsked 도 건드리지 않는다 — 그 플래그는
     "OS 프롬프트를 실제로 띄운 적 있는가"라서, 안 띄우고 true 로 만들면 이후 '영구 거부' 판별이 틀어진다.
     */
    private func finishOnboarding(requestNotification: Bool) {
        AppSettings().onboardingDone = true
        if requestNotification {
            store.setNotifyPickup(true)
            store.setNotifyBudget(true)
            store.setNotifyResin(true)
            AppSettings().notifPermAsked = true
            NotificationPermission.request()
        }
        needsIntro = false
    }

    // ── 메인 셸: 시스템 탭바(iPhone·iPad 공통) ──────────────────────────

    /// 현재 기기가 iPad 인가. iPad 는 가로 회전 지원 + 지출 추가를 우측 하단 FAB 로 제공.
    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    @ViewBuilder
    private var authenticatedRoot: some View {
        mainTabs
    }

    // ── 탭 구성 (iPhone·iPad 공통 시스템 탭바) ────────────────────────────

    @ViewBuilder
    private var mainTabs: some View {
        if #available(iOS 26.0, *) {
            // iOS 26: Tab API — "추가" 버튼은 .search 역할로 탭바에서 분리된 원형 글래스 버튼
            // (Mail 의 컴포즈 버튼과 동일한 시스템 배치 — 탭바 캡슐 우측, 같은 높이)
            TabView(selection: $selectedTab) {
                // 라벨(문구) 노출 — 표준 시스템 탭뷰 (시스템 탭바 높이는 고정이라 아이콘전용 이점이 없어 라벨 복귀)
                Tab("홈", systemImage: "house.fill", value: 0) { homeTabContent }
                Tab("지출", systemImage: "creditcard.fill", value: 1) { spendingTabContent }
                Tab("게임 정보", systemImage: "gamecontroller.fill", value: 2) { gameInfoTabContent }
                Tab("마이페이지", systemImage: "person.fill", value: 3) { myPageTabContent }
                // iPhone: 탭바에서 분리된 원형 '추가' 버튼. iPad 는 우측 하단 FAB 로 대신한다.
                // 초기 동기화 게이트(로딩 화면) 동안에는 표시하지 않음
                if !syncGateActive && !isPad {
                    Tab(value: 4, role: separatedActionRole) { Color.clear } label: {
                        Label("추가", systemImage: "plus")
                    }
                }
            }
            .tint(accent)
            .tabBarMinimizeBehavior(.never) // 스크롤 시 탭바 축소 안 함(항상 전체 크기 유지)
            .onChange(of: selectedTab) { oldValue, newValue in
                if newValue == 4 {
                    // "추가" 는 탭이 아니라 액션 — 모달 열고 이전 탭으로 복귀
                    selectedTab = oldValue
                    openAddSpending()
                }
            }
            // iPad: 지출 추가를 우측 하단 시스템 FAB 로.
            .overlay(alignment: .bottomTrailing) {
                if isPad && !syncGateActive {
                    fabAddButton.padding(.trailing, 24).padding(.bottom, 24)
                }
            }
        } else {
            // iOS 16~25: 기존 tabItem API + 오버레이 글래스 FAB
            TabView(selection: $selectedTab) {
                // 라벨(문구) 노출 — 표준 시스템 탭뷰
                homeTabContent
                    .tabItem { Label("홈", systemImage: "house.fill") }
                    .tag(0)
                spendingTabContent
                    .tabItem { Label("지출", systemImage: "creditcard.fill") }
                    .tag(1)
                gameInfoTabContent
                    .tabItem { Label("게임 정보", systemImage: "gamecontroller.fill") }
                    .tag(2)
                myPageTabContent
                    .tabItem { Label("마이페이지", systemImage: "person.fill") }
                    .tag(3)
            }
            .tint(accent)
            .overlay(alignment: .bottomTrailing) {
                if selectedTab <= 1 && !tabsWithSubPage.contains(selectedTab) && !syncGateActive {
                    legacyAddButton
                        .padding(.trailing, 20)
                        .padding(.bottom, 64)
                }
            }
        }
    }

    /// '추가' 버튼을 탭바 캡슐에서 **분리된 원형 버튼**으로 띄우는 역할.
    ///
    /// iOS 26 에선 `.search` 가 그 배치(Mail 컴포즈 버튼)를 줬는데, iOS 27 SDK 로 링크하면
    /// 검색 탭이 탭바 캡슐 안으로 합쳐져 '추가'가 5번째 탭처럼 붙어 보인다.
    /// 27 부터는 같은 분리 배치를 주는 전용 역할 `.prominent` 가 생겼으므로 그쪽을 쓴다.
    @available(iOS 26.0, *)
    private var separatedActionRole: TabRole {
        if #available(iOS 27.0, *) { return .prominent }
        return .search
    }

    /// 탭별 서브페이지 상태 콜백 — 해당 탭의 탭바 숨김 상태만 갱신
    private func subPageBinding(_ tab: Int) -> (KotlinBoolean) -> Void {
        return { active in
            if active.boolValue {
                tabsWithSubPage.insert(tab)
            } else {
                tabsWithSubPage.remove(tab)
            }
        }
    }

    /// 탭바 표시 여부 — 서브페이지에서도 항상 노출하되, **초기 동기화 게이트(로딩 화면)에서만 숨김**.
    /// (로그인 화면은 TabView 밖 별도 뷰라 자동으로 탭바·FAB 없음)
    private func tabBarVisibility(_ tab: Int) -> Visibility { syncGateActive ? .hidden : .visible }

    // ── 탭 콘텐츠 (네이티브 SwiftUI) ──────────────────────────────────

    // Phase 5 — SwiftUI 네이티브 홈. 시작 로직(refreshGameInfo)은 HomeView.task 에서 트리거.
    private var homeTabContent: some View {
        NavigationStack {
            HomeView(store: store, onSwitchTab: { selectedTab = $0 })
        }
        .glgAccent(index: store.accentIndex)
        .toolbar(tabBarVisibility(0), for: .tabBar)
    }

    // Phase 3 — SwiftUI 네이티브 지출(목록·분석·달력·상세). 수정 시 편집 대상 설정 후 기존 AddSpending 시트(Compose interim)를 연다.
    private var spendingTabContent: some View {
        NavigationStack {
            SpendingView(store: store, onEdit: { spending in
                spendingSheet = .edit(spending)   // 대상 = 표시 여부. 한 번에 확정된다.
            })
        }
        .glgAccent(index: store.accentIndex)
        .toolbar(tabBarVisibility(1), for: .tabBar)
    }

    // Phase 4 chunk ② — SwiftUI 게임정보(데일리·배너/전투/일지·패치·위시·천장·이벤트). 가챠 도구는 chunk ③.
    private var gameInfoTabContent: some View {
        NavigationStack { GameInfoView(store: store) }
            .glgAccent(index: store.accentIndex)
            .toolbar(tabBarVisibility(2), for: .tabBar)
    }

    // Phase 2 — SwiftUI 네이티브 마이페이지/설정 (구 ComposeView MyPageTabViewController 대체).
    // 설정은 NavigationStack push(시스템 슬라이드·뒤로가기), 탭바 숨김은 SettingsView 가 .toolbar(.hidden) 로 처리.
    private var myPageTabContent: some View {
        NavigationStack {
            MyPageView(store: store)
        }
        .glgAccent(index: store.accentIndex)
        // 초기 동기화 게이트(로딩) 중에는 탭바 숨김 — 서브페이지(설정)에서는 노출 유지
        .toolbar(tabBarVisibility(3), for: .tabBar)
    }

    // ── iOS 16~25 폴백 버튼 (무색 글래스) ───────────────────────────────

    private var legacyAddButton: some View {
        Button(action: { openAddSpending() }) {
            Image(systemName: "plus")
                .font(.pretendard(size: 19, weight: .semibold))
                .foregroundColor(.primary)
                .frame(width: 48, height: 48)
                .background { GLGVisualEffectBlur(style: .systemUltraThinMaterial).clipShape(Circle()) }
                .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
        }
    }

    // ── iPad: 우측 하단 지출 추가 FAB (강조색 채움 원형 + 흰 '+') ─────────────
    private var fabAddButton: some View {
        Button(action: { openAddSpending() }) {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 60, height: 60)
                .background(accent, in: Circle())
                .shadow(color: accent.opacity(0.4), radius: 14, y: 6)
        }
        .accessibilityLabel("지출 추가")
    }
}

// ── Kotlin ARGB(Long) → SwiftUI Color ──────────────────────────────────────

private extension Color {
    init(argb: Int64) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255.0,
            green: Double((argb >> 8) & 0xFF) / 255.0,
            blue: Double(argb & 0xFF) / 255.0,
            opacity: Double((argb >> 24) & 0xFF) / 255.0
        )
    }
}
