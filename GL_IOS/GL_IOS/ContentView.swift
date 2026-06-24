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
    @StateObject private var store = SpendingStore()
    @State private var selectedTab: Int = 0
    @State private var showAddSpending: Bool = false
    /// 지출 추가/수정 시트의 편집 대상 (nil = 신규 추가). Phase 6 — SwiftUI 폼으로 교체하며 로컬 상태로 관리.
    @State private var editingSpending: Spending? = nil
    /// 서브페이지(연간 리포트·알림 상세 등)가 열린 탭 집합 — 해당 탭에서만 탭바 숨김.
    /// (전역 단일 플래그는 탭 전환 시 상태가 어긋나므로 탭별로 독립 관리)
    @State private var tabsWithSubPage: Set<Int> = []

    /// 초기 클라우드 동기화 게이트(로딩 화면) 활성 여부 — 게이트 동안 탭바·추가 버튼 숨김
    @State private var syncGateActive: Bool = MainViewControllerKt.isSyncGateActive()

    /// 앱 강조색 — Kotlin 테마(accentIndex)와 연동된 탭 아이콘 틴트 (초기값: 민트)
    @State private var accent = Color(red: 0.204, green: 0.820, blue: 0.714)

    /// 루트 상태(로그인→로딩→탭) — 크로스페이드 트랜지션 키. NavigationStack/스와이프백은 손대지 않고
    /// 루트 교체만 부드럽게 한다(즉시 교체 → standard 페이드).
    private enum RootPhase { case login, loading, tabs }
    private var rootPhase: RootPhase {
        if store.needsOnboarding { return .login }
        if syncGateActive { return .loading }
        return .tabs
    }

    var body: some View {
        Group {
            if store.needsOnboarding {
                // Phase 1 — SwiftUI 네이티브 로그인/온보딩 (구 ComposeView LoginViewController 대체).
                // 로그인 완료 시 공유 VM 의 account 가 바뀌어 자동으로 탭 화면으로 전환.
                LoginView(store: store)
                    .glgAccent(index: store.accentIndex)
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
                mainTabs
                    // 지출 추가/수정 — Phase 6: SwiftUI 네이티브 폼 (구 ComposeView AddSpendingViewController 대체)
                    .sheet(isPresented: $showAddSpending, onDismiss: { editingSpending = nil }) {
                        AddSpendingView(store: store, editing: editingSpending) { showAddSpending = false }
                            .presentationDragIndicator(.visible)
                    }
                    .transition(.opacity)
            }
        }
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
            if let tab = note.object as? Int, !syncGateActive, !store.needsOnboarding {
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
    }

    /// '+' (지출 추가) 모달 열기 — 신규 추가(편집 대상 없음).
    private func openAddSpending() {
        editingSpending = nil
        showAddSpending = true
    }

    // ── 탭 구성 ─────────────────────────────────────────────────────────

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
                // 분리된 원형 버튼 — 탭 전환 대신 지출 추가 모달을 연다 (onChange 에서 가로챔)
                // 초기 동기화 게이트(로딩 화면) 동안에는 표시하지 않음
                if !syncGateActive {
                    Tab(value: 4, role: .search) { Color.clear } label: {
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
                editingSpending = spending    // 편집 대상 설정
                showAddSpending = true        // ContentView 의 시트 오픈
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
