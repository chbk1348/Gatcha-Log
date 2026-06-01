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
struct ContentView: View {
    @State private var showOnboarding: Bool = MainViewControllerKt.needsOnboarding()
    @State private var selectedTab: Int = 0
    @State private var showAddSpending: Bool = false
    /// 서브페이지(연간 리포트·알림 상세 등)가 열리면 탭바 숨김 — 기존 앱 UX 유지
    @State private var hideTabBar: Bool = false

    /// 앱 강조색 (민트) — 탭 아이콘 틴트
    private let accent = Color(red: 0.204, green: 0.820, blue: 0.714)

    /// 지출 추가 버튼 — 무색 리퀴드 글래스 (색상 없음, 유리 + 아이콘만)
    @ViewBuilder
    private var addSpendingButton: some View {
        if #available(iOS 26.0, *) {
            // iOS 26 네이티브 리퀴드 글래스 버튼 (무색 — 시스템이 블러·굴절·모핑 처리)
            Button(action: { showAddSpending = true }) {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .semibold))
                    .frame(width: 56, height: 56)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
        } else {
            // iOS 16~18 폴백: 반투명 시스템 머티리얼 (무색 유리)
            Button(action: { showAddSpending = true }) {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(.primary)
                    .frame(width: 56, height: 56)
                    .background(.ultraThinMaterial, in: Circle())
                    .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
            }
        }
    }

    var body: some View {
        if showOnboarding {
            ComposeView(factory: {
                MainViewControllerKt.LoginViewController(onComplete: { showOnboarding = false })
            })
            .ignoresSafeArea(.all)
        } else {
            TabView(selection: $selectedTab) {
                // ── 홈 ──────────────────────────────────────────────
                ComposeView(factory: {
                    MainViewControllerKt.HomeTabViewController(
                        onSwitchTab: { tab in selectedTab = tab.intValue },
                        onAddSpending: { showAddSpending = true },
                        onSubPageChange: { active in hideTabBar = active.boolValue }
                    )
                })
                .ignoresSafeArea(.all)
                .toolbar(hideTabBar ? .hidden : .visible, for: .tabBar)
                .tabItem { Label("홈", systemImage: "house.fill") }
                .tag(0)

                // ── 지출 ────────────────────────────────────────────
                ComposeView(factory: {
                    MainViewControllerKt.SpendingTabViewController(
                        onAddSpending: { showAddSpending = true },
                        onSubPageChange: { active in hideTabBar = active.boolValue }
                    )
                })
                .ignoresSafeArea(.all)
                .toolbar(hideTabBar ? .hidden : .visible, for: .tabBar)
                .tabItem { Label("지출", systemImage: "creditcard.fill") }
                .tag(1)

                // ── 게임 정보 ────────────────────────────────────────
                ComposeView(factory: {
                    MainViewControllerKt.GameInfoTabViewController(
                        onSubPageChange: { active in hideTabBar = active.boolValue }
                    )
                })
                .ignoresSafeArea(.all)
                .toolbar(hideTabBar ? .hidden : .visible, for: .tabBar)
                .tabItem { Label("게임 정보", systemImage: "gamecontroller.fill") }
                .tag(2)

                // ── 마이페이지 ───────────────────────────────────────
                ComposeView(factory: {
                    MainViewControllerKt.MyPageTabViewController(
                        onSubPageChange: { active in hideTabBar = active.boolValue }
                    )
                })
                .ignoresSafeArea(.all)
                .toolbar(hideTabBar ? .hidden : .visible, for: .tabBar)
                .tabItem { Label("마이페이지", systemImage: "person.fill") }
                .tag(3)
            }
            .tint(accent)
            // ── 지출 추가 FAB — iOS 네이티브 버튼 (홈·지출 탭에서만, 서브페이지에선 숨김) ──
            // iOS 26: 시스템 리퀴드 글래스 버튼 / iOS 16~18: 동일 디자인의 일반 버튼 폴백
            .overlay(alignment: .bottomTrailing) {
                if selectedTab <= 1 && !hideTabBar {
                    addSpendingButton
                        .padding(.trailing, 20)
                        .padding(.bottom, 64) // 네이티브 탭바 위
                }
            }
            // 지출 추가/수정 — 네이티브 풀스크린 커버 (탭바를 자연스럽게 덮음)
            .fullScreenCover(isPresented: $showAddSpending) {
                ComposeView(factory: {
                    MainViewControllerKt.AddSpendingViewController(onClose: { showAddSpending = false })
                })
                .ignoresSafeArea(.all)
            }
        }
    }
}
