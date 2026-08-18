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

    /// 실험 빌드 경고 표시 여부 — 배포본에선 항상 false 라 얼럿이 뜨지 않는다.
    #if EXPERIMENT
    @State private var showExperimentAlert = true
    #else
    @State private var showExperimentAlert = false
    #endif

    /// 지출 추가/수정 시트 — **표시 여부와 대상을 한 상태로 합쳤다**(nil = 닫힘).
    ///
    /// 예전엔 showAddSpending(Bool) + editingSpending(Spending?) 두 개였는데, `.sheet(isPresented:)` 는
    /// 표시 시점에 내용을 만들면서 아직 반영되지 않은 editingSpending(=nil)을 집어갈 수 있었다.
    /// 그러면 '수정'을 눌렀는데 빈 '추가' 폼이 뜬다 — 타이밍에 따라 갈려 간헐적으로 재현됐다.
    /// AddSpendingView 의 didInit 가드가 nil 로 한 번 초기화되면 필드를 다시 채우지 않아 증상이 굳었다.
    /// `.sheet(item:)` 은 대상 값을 표시 시점에 확정해 넘기므로 이 경합 자체가 성립하지 않는다.
    @State private var spendingSheet: SpendingSheetTarget? = nil

    /// 지출 편집 페이지의 대상. `.navigationDestination(item:)` 에 넘기기 위해 Identifiable + Hashable.
    ///
    /// 동등성·해시는 **[id] 로만** 판단한다. Kotlin `Spending` 은 Swift 쪽에서 값 동등성이
    /// 보장되지 않아 그대로 해싱하면 같은 대상이 매번 달라 보일 수 있고, 그러면
    /// 네비게이션이 목적지를 새로 밀어 넣는다.
    private enum SpendingSheetTarget: Identifiable, Hashable {
        case add
        case edit(Spending)

        static func == (a: Self, b: Self) -> Bool { a.id == b.id }
        func hash(into hasher: inout Hasher) { hasher.combine(id) }

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

    /// 지출 탭 스택 경로 — 목록 → 상세 → 수정이 여기 쌓인다.
    @State private var spendingPath: [SpendingRoute] = []

    /// 지출 입력 페이지(추가·수정)가 경로에 올라와 있는가.
    ///
    /// 숨김을 그 화면이 스스로 선언하면, pop 되는 순간 선언이 사라져 탭바가 **애니메이션 없이
    /// 즉시** 나타난다(짠 하고 등장). 경로를 보고 상위가 판단하면 값 변화가 전환에 실린다.
    private var spendingEditorOpen: Bool {
        spendingPath.contains {
            if case .detail = $0 { return false }
            return true
        }
    }

    /// 초기 클라우드 동기화 게이트(로딩 화면) 활성 여부 — 게이트 동안 탭바·추가 버튼 숨김
    ///
    /// ⚠️ 이 한 줄이 **첫 프레임 이전에 SpendingViewModel 을 통째로 만든다** —
    /// `IosAppState.viewModel`(lazy) → `init` → `loadAll()`(동기 저장소 읽기 ~20회 + JSON 파싱).
    /// 콜드스타트 비용의 대부분이 여기 들어 있어서 signpost 를 걸었다(GLGPerf → Instruments).
    @State private var syncGateActive: Bool = GLGPerf.interval("storeInit+loadAll") {
        MainViewControllerKt.isSyncGateActive()
    }

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
            Button("확인", role: .cancel) { store.clearNetworkAlert() }.glgAlertTint()
        } message: {
            Text(store.networkAlert ?? "")
        }
        // 실험 빌드 경고 — 앱 시작 시 1회(Android MainActivity 의 GlgDialog 파리티).
        // @State 라 프로세스가 새로 뜨면 다시 나온다 — "앱 시작 시 1회"가 의도다.
        // 배포본에는 EXPERIMENT 조건이 없어 이 블록 자체가 컴파일에서 빠진다.
        .alert("⚠️ 실험 빌드", isPresented: $showExperimentAlert) {
            Button("확인하고 계속", role: .cancel) { showExperimentAlert = false }.glgAlertTint()
        } message: {
            Text("정식 배포본이 아닙니다. 검증되지 않은 UI·라이브러리가 들어 있어 "
                + "예기치 않은 동작이나 종료가 발생할 수 있어요.\n\n"
                + "설정 > 앱 버전에 빨간 EXPERIMENT 표시가 있으면 이 빌드입니다.")
        }
        // 강조색을 **최상위에서** 주입한다. 예전엔 탭 콘텐츠에만 걸어서, 탭 바깥에 붙는 것들
        // (지출 추가/수정 시트·전역 토스트·네트워크 얼럿)이 주입이 없는 환경을 물려받아
        // 사용자가 무슨 테마를 골랐든 항상 기본 민트로 떴다.
        // 로그인 전 화면은 더 안쪽에서 preLoginAccent 로 덮어쓰므로(가까운 쪽이 이긴다) 그대로 민트로 남는다.
        .glgAccent(index: store.accentIndex)
        // 앱으로 돌아올 때마다 밀린 알림 1회 점검 — BGAppRefreshTask 는 실행 시점이 OS 재량이고
        // 앱이 강제 종료돼 있으면 아예 안 돌아, 그것만으론 알림이 토글 켤 때만 오는 것처럼 보였다.
        // (실제 실행 여부·최소 간격은 공유 VM 이 판단한다.)
        // .inactive 는 제어센터·앱 전환기 미리보기에서도 스쳐 지나가므로 이탈로 치지 않는다 — .background 만 쓴다.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { store.onAppForeground() }
            else if phase == .background { store.onAppBackground() }
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
        // 탭을 옮기지 않는다 — **보고 있던 탭 위에** 밀어 넣는다.
        // (예전엔 지출 탭으로 강제 이동시켰는데, 홈에서 '+'를 눌렀다가 닫으면 엉뚱하게 지출 탭에 남았다.)
        //
        // 지출 탭만 경로 기반이라 경로에 쌓고, 나머지 탭은 기존 item 목적지를 쓴다.
        if selectedTab == 1 { spendingPath.append(.add) } else { spendingSheet = .add }
    }

    /**
     지출 편집 페이지를 **지금 보고 있는 탭의 스택**에만 밀어 넣기 위한 바인딩.

     네 탭이 각자 `NavigationStack` 을 갖고 있고 TabView 는 보이지 않는 탭도 살려 둔다.
     같은 상태를 네 스택에 그대로 물리면 **네 곳이 동시에** 목적지를 밀어 넣어, 나중에 다른 탭으로
     가면 거기에도 추가 페이지가 쌓여 있다. 그래서 현재 탭이 아니면 항상 nil 을 돌려준다.
     */
    private func spendingEditorBinding(tab: Int) -> Binding<SpendingSheetTarget?> {
        Binding(
            get: { selectedTab == tab ? spendingSheet : nil },
            set: { newValue in if selectedTab == tab { spendingSheet = newValue } }
        )
    }

    /// 각 탭 스택 루트에 다는 지출 편집 목적지 — 상세 페이지처럼 밀려 들어온다(시트 아님).
    @ViewBuilder
    private func spendingEditorDestination<V: View>(tab: Int, _ content: V) -> some View {
        content.navigationDestination(item: spendingEditorBinding(tab: tab)) { target in
            AddSpendingView(store: store, editing: target.spending, pushed: true) { spendingSheet = nil }
        }
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
    /// 창이 좁은가 — **탭바가 하단에 놓이는 상태**.
    ///
    /// '추가' 버튼 자리를 기기 종류(`isPad`)로 정하면 안 된다. iPadOS 26 자유 창에서는 iPad 도
    /// 창을 줄이면 컴팩트가 되어 탭바가 하단으로 내려오는데, idiom 은 여전히 `.pad` 라
    /// **우측 하단 FAB 가 그 하단 탭바 위에 겹친다.** 폭으로 판단해야 맞다.
    @Environment(\.horizontalSizeClass) private var hSizeClass
    private var isCompactWindow: Bool { hSizeClass == .compact }

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
                // 좁은 창: 탭바에서 분리된 원형 '추가' 버튼(탭바가 하단이라 FAB 를 놓을 자리가 없다).
                // 넓은 창은 아래 우측 하단 FAB 로 대신한다. 초기 동기화 게이트 동안에는 둘 다 미표시.
                if !syncGateActive && isCompactWindow {
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
            // 넓은 창(탭바가 상단): 지출 추가를 우측 하단 FAB 로.
            .overlay(alignment: .bottomTrailing) {
                if !isCompactWindow && !syncGateActive {
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

    /// 탭바 표시 여부 — 서브페이지에서도 항상 노출하되, 초기 동기화 게이트와 **지출 입력 페이지**에서 숨김.
    /// (로그인 화면은 TabView 밖 별도 뷰라 자동으로 탭바·FAB 없음)
    ///
    /// ⚠️ 숨김 판단은 **반드시 여기서** 해야 한다. 이 함수는 탭 콘텐츠에 `.toolbar(_:for:.tabBar)` 로
    /// 붙는데, `.visible` 을 명시하는 순간 **그 안쪽 화면이 건 `.hidden` 을 덮어쓴다** —
    /// 추가 페이지에서 `.toolbar(.hidden, for: .tabBar)` 를 걸어도 탭바가 그대로 남았던 이유다.
    /// 하단 탭바 가시성 — **여기 한 곳에서만 정한다.**
    ///
    /// 두 가지를 지켜야 해서 이 모양이 됐다.
    ///  1. 하위 화면이 각자 `.toolbar(.hidden, for: .tabBar)` 를 걸면 안 된다 — pop 되는 순간
    ///     선언이 사라져 탭바가 애니메이션 없이 튀어나온다("짠" 하고 등장).
    ///  2. 그렇다고 **조건부로 모디파이어를 붙였다 뗐다 하면 안 된다** — 모디파이어의 유무가
    ///     바뀌면 그 아래 `NavigationStack` 의 구조가 재구성돼 **push 애니메이션이 사라진다.**
    ///
    /// 그래서 모디파이어는 **항상 달아 두고 값만 바꾼다.** 값 변화는 전환에 실리고 구조는 그대로다.
    private var tabBarVisibility: Visibility {
        (syncGateActive || spendingSheet != nil || spendingEditorOpen) ? .hidden : .visible
    }

    // ── 탭 콘텐츠 (네이티브 SwiftUI) ──────────────────────────────────

    // Phase 5 — SwiftUI 네이티브 홈. 시작 로직(refreshGameInfo)은 HomeView.task 에서 트리거.
    private var homeTabContent: some View {
        NavigationStack {
            spendingEditorDestination(tab: 0, HomeView(store: store, onSwitchTab: { selectedTab = $0 }))
        }
        .glgAccent(index: store.accentIndex)
        .toolbar(tabBarVisibility, for: .tabBar)
    }

    // Phase 3 — SwiftUI 네이티브 지출(목록·분석·달력·상세). 수정 시 편집 대상 설정 후 기존 AddSpending 시트(Compose interim)를 연다.
    private var spendingTabContent: some View {
        // **경로 기반 스택.** 목록 → 상세 → 수정이 한 경로 위에 쌓인다.
        //
        // 예전엔 뷰 기반 `NavigationLink { 상세 }` 와, 상세 **안에서** 다시 선언한
        // `navigationDestination(수정)` 이 섞여 있었다. 목적지 안에서 목적지를 등록하면
        // SwiftUI 가 스택을 초기화해 **수정 버튼이 목록으로 튕겼다.**
        // 목적지는 여기서 한 번만 등록하고, 각 화면은 경로에 값을 밀어 넣기만 한다.
        NavigationStack(path: $spendingPath) {
            SpendingView(store: store, onEdit: { spending in
                spendingPath.append(.edit(spending.id))
            })
            .navigationDestination(for: SpendingRoute.self) { route in
                switch route {
                case .detail(let id):
                    SpendingDetailView(store: store, spendingId: id, onEdit: { s in
                        spendingPath.append(.edit(s.id))
                    })
                case .edit(let id):
                    AddSpendingView(
                        store: store,
                        editing: store.spendings.first { $0.id == id },
                        pushed: true,
                    ) { popSpendingPath() }
                case .add:
                    AddSpendingView(store: store, editing: nil, pushed: true) { popSpendingPath() }
                }
            }
        }
        .glgAccent(index: store.accentIndex)
        .toolbar(tabBarVisibility, for: .tabBar)
    }

    /// 편집 페이지를 닫는다 — 경로에서 한 칸만 뺀다(상세가 아래 있으면 상세로 돌아간다).
    private func popSpendingPath() {
        if !spendingPath.isEmpty { spendingPath.removeLast() }
    }

    // Phase 4 chunk ② — SwiftUI 게임정보(데일리·배너/전투/일지·패치·위시·천장·이벤트). 가챠 도구는 chunk ③.
    private var gameInfoTabContent: some View {
        NavigationStack { spendingEditorDestination(tab: 2, GameInfoView(store: store)) }
            .glgAccent(index: store.accentIndex)
            .toolbar(tabBarVisibility, for: .tabBar)
    }

    // Phase 2 — SwiftUI 네이티브 마이페이지/설정 (구 ComposeView MyPageTabViewController 대체).
    // 설정은 NavigationStack push(시스템 슬라이드·뒤로가기), 탭바 숨김은 SettingsView 가 .toolbar(.hidden) 로 처리.
    private var myPageTabContent: some View {
        NavigationStack {
            spendingEditorDestination(tab: 3, MyPageView(store: store))
        }
        .glgAccent(index: store.accentIndex)
        // 초기 동기화 게이트(로딩) 중에는 탭바 숨김 — 서브페이지(설정)에서는 노출 유지
        .toolbar(tabBarVisibility, for: .tabBar)
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
                .frame(width: 60, height: 60)
        }
        .accessibilityLabel("지출 추가")
        .modifier(GLGFabStyle(tint: accent))
    }
}

/// 넓은 창(iPad)의 '추가' FAB — iOS 26 은 **시스템 글래스**(.glassProminent), 이하는 솔리드 원.
///
/// 직접 그린 원 + 그림자였는데, 같은 화면 아래쪽 탭바가 이미 시스템 Liquid Glass 라
/// 재질이 서로 달라 FAB 만 앱에서 그린 티가 났다. 시스템 스타일로 넘기면 배경 위에서
/// 굴절·명암이 알아서 잡히고 다크모드·접근성 설정도 따라온다.
///
/// (`Menu` 라벨에는 `.glassProminent` 를 쓰지 않는다 — 닫힐 때 색 덩어리가 스친다.
///  여긴 `Button` 이라 무관하다.)
private struct GLGFabStyle: ViewModifier {
    let tint: Color

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .buttonStyle(.glassProminent)
                .tint(tint)
                .buttonBorderShape(.circle)
        } else {
            content
                .foregroundStyle(.white)
                .background(tint, in: Circle())
                .shadow(color: tint.opacity(0.4), radius: 14, y: 6)
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
