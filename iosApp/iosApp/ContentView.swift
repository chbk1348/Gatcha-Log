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
    /// 서브페이지(연간 리포트·알림 상세 등)가 열린 탭 집합 — 해당 탭에서만 탭바 숨김.
    /// (전역 단일 플래그는 탭 전환 시 상태가 어긋나므로 탭별로 독립 관리)
    @State private var tabsWithSubPage: Set<Int> = []

    /// 초기 클라우드 동기화 게이트(로딩 화면) 활성 여부 — 게이트 동안 탭바·추가 버튼 숨김
    @State private var syncGateActive: Bool = MainViewControllerKt.isSyncGateActive()

    /// 앱 강조색 — Kotlin 테마(accentIndex)와 연동된 탭 아이콘 틴트 (초기값: 민트)
    @State private var accent = Color(red: 0.204, green: 0.820, blue: 0.714)

    var body: some View {
        Group {
            if showOnboarding {
                ComposeView(factory: {
                    MainViewControllerKt.LoginViewController(onComplete: { showOnboarding = false })
                })
                .ignoresSafeArea(.all)
            } else {
                mainTabs
                    // 지출 추가/수정 — iOS 표준 시트 (드래그 핸들 + 아래로 스와이프 닫기)
                    .sheet(isPresented: $showAddSpending, onDismiss: {
                        // 드래그로 닫힌 경우에도 수정 대상 클리어 (저장/취소 경로는 Kotlin 쪽에서 클리어)
                        MainViewControllerKt.prepareAddSpending()
                    }) {
                        ComposeView(factory: {
                            MainViewControllerKt.AddSpendingViewController(onClose: { showAddSpending = false })
                        })
                        .ignoresSafeArea(edges: .bottom)
                        .presentationDetents([.large])
                        .presentationDragIndicator(.visible)
                    }
            }
        }
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
        .onChange(of: selectedTab, perform: { newValue in
            if newValue != 4 {
                MainViewControllerKt.setSelectedTab(tab: Int32(newValue))
            }
        })
    }

    /// '+' (지출 추가) 모달 열기 — 이전 수정 대상이 남아있지 않게 초기화 후 연다.
    /// (수정 흐름은 Kotlin 쪽이 대상을 설정한 뒤 onAddSpending 콜백으로 열므로 이 함수를 거치지 않는다)
    private func openAddSpending() {
        MainViewControllerKt.prepareAddSpending()
        showAddSpending = true
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
                // 초기 동기화 게이트(로딩 화면) 동안에는 표시하지 않음
                if !syncGateActive {
                    Tab(value: 4, role: .search) { Color.clear } label: {
                        Label("추가", systemImage: "plus")
                    }
                }
            }
            .tint(accent)
            .tabBarMinimizeBehavior(.onScrollDown) // iOS 26: 스크롤 시 탭바 자동 축소
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

    /// 탭바 표시 여부 — 해당 탭의 서브페이지가 열려 있거나 초기 동기화 게이트 중이면 숨김
    private func tabBarVisibility(_ tab: Int) -> Visibility {
        (syncGateActive || tabsWithSubPage.contains(tab)) ? .hidden : .visible
    }

    // ── 탭 콘텐츠 (Compose 공유 코드) ──────────────────────────────────

    private var homeTabContent: some View {
        ComposeView(factory: {
            MainViewControllerKt.HomeTabViewController(
                onSwitchTab: { tab in selectedTab = tab.intValue },
                onSubPageChange: subPageBinding(0)
            )
        })
        .ignoresSafeArea(.all)
        .toolbar(tabBarVisibility(0), for: .tabBar)
    }

    private var spendingTabContent: some View {
        ComposeView(factory: {
            MainViewControllerKt.SpendingTabViewController(
                // 수정 흐름: Kotlin 이 spendingToEdit 을 설정한 뒤 이 콜백으로 모달을 연다 (클리어 없이)
                onAddSpending: { showAddSpending = true },
                onSubPageChange: subPageBinding(1)
            )
        })
        .ignoresSafeArea(.all)
        .toolbar(tabBarVisibility(1), for: .tabBar)
    }

    private var gameInfoTabContent: some View {
        ComposeView(factory: {
            MainViewControllerKt.GameInfoTabViewController(
                onSubPageChange: subPageBinding(2)
            )
        })
        .ignoresSafeArea(.all)
        .toolbar(tabBarVisibility(2), for: .tabBar)
    }

    private var myPageTabContent: some View {
        ComposeView(factory: {
            MainViewControllerKt.MyPageTabViewController(
                onSubPageChange: subPageBinding(3)
            )
        })
        .ignoresSafeArea(.all)
        .toolbar(tabBarVisibility(3), for: .tabBar)
    }

    // ── iOS 16~25 폴백 버튼 (무색 글래스) ───────────────────────────────

    private var legacyAddButton: some View {
        Button(action: { openAddSpending() }) {
            Image(systemName: "plus")
                .font(.system(size: 19, weight: .semibold))
                .foregroundColor(.primary)
                .frame(width: 48, height: 48)
                .background(.ultraThinMaterial, in: Circle())
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
