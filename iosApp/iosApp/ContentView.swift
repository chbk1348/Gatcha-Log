import SwiftUI
import ComposeApp

/// Kotlin(Compose) ViewController 를 SwiftUI 안에 호스팅하는 공용 래퍼
struct ComposeView: UIViewControllerRepresentable {
    let factory: () -> UIViewController
    func makeUIViewController(context: Context) -> UIViewController { factory() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// 앱 루트 — 온보딩(로그인/게스트) → 네이티브 탭바 화면
///
/// 탭바는 SwiftUI TabView(= UITabBarController) 라서 iOS 26 에서는 시스템
/// 리퀴드 글래스(블러·스크롤 축소·모핑)가 자동 적용되고, iOS 16~18 에서는
/// 해당 버전의 시스템 머티리얼 탭바로 표시된다. 탭 콘텐츠는 전부 Compose 공유 코드.
///
/// 지출 추가 버튼 (iOS 26 가이드라인):
///  - iOS 26: 탭바와 같은 높이에 분리된 원형 글래스 버튼 (Mail 컴포즈 버튼 패턴 — Tab role 사용)
///  - iOS 16~25: 탭바 위 우측의 글래스 원형 버튼 (오버레이 폴백)
struct ContentView: View {
    @State private var showOnboarding: Bool = MainViewControllerKt.needsOnboarding()
    @State private var selectedTab: Int = 0
    @State private var showAddSpending: Bool = false
    /// 서브페이지(연간 리포트·알림 상세 등)가 열리면 탭바 숨김 — 기존 앱 UX 유지
    @State private var hideTabBar: Bool = false

    /// 앱 강조색 (민트) — 탭 아이콘 틴트
    private let accent = Color(red: 0.204, green: 0.820, blue: 0.714)

    var body: some View {
        if showOnboarding {
            ComposeView(factory: {
                MainViewControllerKt.LoginViewController(onComplete: { showOnboarding = false })
            })
            .ignoresSafeArea(.all)
        } else {
            mainTabs
                // 지출 추가/수정 — 네이티브 풀스크린 커버 (탭바를 자연스럽게 덮음)
                .fullScreenCover(isPresented: $showAddSpending) {
                    ComposeView(factory: {
                        MainViewControllerKt.AddSpendingViewController(onClose: { showAddSpending = false })
                    })
                    .ignoresSafeArea(.all)
                }
        }
    }

    // ── 탭 구성 ─────────────────────────────────────────────────────────

    @ViewBuilder
    private var mainTabs: some View {
        if #available(iOS 26.0, *) {
            // iOS 26: Tab API — "추가" 버튼은 .search 역할로 탭바에서 분리된 원형 글래스 버튼
            // (Mail 의 컴포즈 버튼과 동일한 시스템 배치 — 탭바 캡슐 우측, 같은 높이)
            TabView(selection: $selectedTab) {
                Tab("홈", systemImage: "house.fill", value: 0) { homeTabContent }
                Tab("지출", systemImage: "creditcard.fill", value: 1) { spendingTabContent }
                Tab("게임 정보", systemImage: "gamecontroller.fill", value: 2) { gameInfoTabContent }
                Tab("마이페이지", systemImage: "person.fill", value: 3) { myPageTabContent }
                // 분리된 원형 버튼 — 탭 전환 대신 지출 추가 모달을 연다 (onChange 에서 가로챔)
                Tab(value: 4, role: .search) { Color.clear } label: {
                    Label("추가", systemImage: "plus")
                }
            }
            .tint(accent)
            .tabBarMinimizeBehavior(.onScrollDown) // iOS 26: 스크롤 시 탭바 자동 축소
            .onChange(of: selectedTab) { oldValue, newValue in
                if newValue == 4 {
                    // "추가" 는 탭이 아니라 액션 — 모달 열고 이전 탭으로 복귀
                    selectedTab = oldValue
                    showAddSpending = true
                }
            }
        } else {
            // iOS 16~25: 기존 tabItem API + 오버레이 글래스 FAB
            TabView(selection: $selectedTab) {
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
                if selectedTab <= 1 && !hideTabBar {
                    legacyAddButton
                        .padding(.trailing, 20)
                        .padding(.bottom, 64)
                }
            }
        }
    }

    // ── 탭 콘텐츠 (Compose 공유 코드) ──────────────────────────────────

    private var homeTabContent: some View {
        ComposeView(factory: {
            MainViewControllerKt.HomeTabViewController(
                onSwitchTab: { tab in selectedTab = tab.intValue },
                onAddSpending: { showAddSpending = true },
                onSubPageChange: { active in hideTabBar = active.boolValue }
            )
        })
        .ignoresSafeArea(.all)
        .toolbar(hideTabBar ? .hidden : .visible, for: .tabBar)
    }

    private var spendingTabContent: some View {
        ComposeView(factory: {
            MainViewControllerKt.SpendingTabViewController(
                onAddSpending: { showAddSpending = true },
                onSubPageChange: { active in hideTabBar = active.boolValue }
            )
        })
        .ignoresSafeArea(.all)
        .toolbar(hideTabBar ? .hidden : .visible, for: .tabBar)
    }

    private var gameInfoTabContent: some View {
        ComposeView(factory: {
            MainViewControllerKt.GameInfoTabViewController(
                onSubPageChange: { active in hideTabBar = active.boolValue }
            )
        })
        .ignoresSafeArea(.all)
        .toolbar(hideTabBar ? .hidden : .visible, for: .tabBar)
    }

    private var myPageTabContent: some View {
        ComposeView(factory: {
            MainViewControllerKt.MyPageTabViewController(
                onSubPageChange: { active in hideTabBar = active.boolValue }
            )
        })
        .ignoresSafeArea(.all)
        .toolbar(hideTabBar ? .hidden : .visible, for: .tabBar)
    }

    // ── iOS 16~25 폴백 버튼 (무색 글래스) ───────────────────────────────

    private var legacyAddButton: some View {
        Button(action: { showAddSpending = true }) {
            Image(systemName: "plus")
                .font(.system(size: 19, weight: .semibold))
                .foregroundColor(.primary)
                .frame(width: 48, height: 48)
                .background(.ultraThinMaterial, in: Circle())
                .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
        }
    }
}
