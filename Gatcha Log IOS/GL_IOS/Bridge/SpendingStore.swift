import Foundation
import Combine
import Shared

// ════════════════════════════════════════════════════════════════════════════
// Kotlin SpendingViewModel ↔ SwiftUI 브리지
//
// SwiftUI 는 Kotlin StateFlow 를 직접 관찰하지 못한다. SKIE 가 StateFlow 를
// Swift AsyncSequence(SkieSwiftStateFlow)로 변환해 주므로, 각 Flow 를 for-await 로
// 구독해 프로퍼티에 미러링한다 → SwiftUI 가 변경을 자동 반영.
//
// VM 인스턴스는 IosAppState.shared.viewModel 싱글톤을 공유한다 — 모든 SwiftUI 화면이 단일 상태를 본다.
//
// ⚠️ @Observable 이지 ObservableObject 가 아니다(2026-07-27 전환).
// ObservableObject + @Published 시절엔 프로퍼티 하나가 바뀌면 이 스토어를 잡은 화면 전체가 다시 그려졌다.
// 상태가 75개, 구독 화면이 27곳이라 checkingIn·enkaLoadingGames 같은 국소 플래그 하나에 4개 탭이 전부
// 재평가됐다. @Observable 은 **각 body 가 실제로 읽은 프로퍼티만** 추적하므로 그 연쇄가 끊긴다.
//
// 그래서 여기에 `vm` 을 직접 읽는 계산 프로퍼티/메서드를 두면 안 된다 — 관찰 대상이 아니라서
// 값이 바뀌어도 화면이 갱신되지 않는다. 파생값은 Kotlin 쪽 StateFlow 로 만들어 mirror 로 받는다.
//
// ⚠️ 확장 정책: 이 스토어는 Phase 마다 "그 화면이 실제로 쓰는 Flow 만" 추가하며 자란다.
// 40여 개 StateFlow 를 한 번에 미러링하지 않는다(검증되지 않은 대량 코드 방지).
// 읽는 화면이 없어진 미러는 지운다 — 구독 한 건이 Task 하나다.
//
// ── 상태 하나를 더할 때 (27.52.0) ──────────────────────────────────────────
// 손댈 곳은 **두 군데**다.
//   ① 아래 구획(Kotlin 컨트롤러 경계와 같은 묶음)에 `private(set) var` 선언 — @Observable 매크로가 요구한다.
//   ② `mirrorFirstFrame()` 또는 `mirrorAfterFirstFrame()` 에 `mirror(vm.x, to: \.x, <시점>)` 한 줄.
// 예전엔 ① 선언 ② init 의 초기값 ③ bind 구독이 따로 있어, 초기값만 빠뜨리는 실수가 되풀이됐다.
// 지금은 초기값을 넣을지 말지가 mirror 의 **필수 인자**라 빠뜨릴 자리가 없다.
// ════════════════════════════════════════════════════════════════════════════

@MainActor
@Observable
final class SpendingStore {

    /// 앱 전체가 공유하는 단일 스토어.
    ///
    /// ContentView 가 `@State` 로 들고 있는데, `@State` 의 초기값 표현식은 **뷰 struct 가 다시 만들어질
    /// 때마다** 평가된다(SwiftUI 가 기존 상태를 유지하더라도 새로 만든 값은 버려질 뿐 생성 자체는 일어난다).
    /// 이 스토어는 생성 시 Flow 구독 Task 를 80개 넘게 띄우므로 그때마다 만들었다 버리면 순수한 낭비다.
    /// 어차피 VM 이 싱글톤이라 스토어도 하나면 충분하다.
    static let shared = SpendingStore()

    /// 모든 화면이 공유하는 단일 Kotlin VM.
    @ObservationIgnored let vm = IosAppState.shared.viewModel

    @ObservationIgnored private var tasks: [Task<Void, Never>] = []
    /// 첫 프레임 뒤로 미뤄 둔 구독 — `mirror(…, .later / .laterUnseeded)` 가 쌓고 `startDeferredSubscriptions()` 가 건다.
    @ObservationIgnored private var deferredSubscriptions: [@MainActor () -> Void] = []

    init() {
        // 기본값을 둘 수 없는 셋(Kotlin 객체)만 여기서 직접 채운다 — 빠뜨리면 컴파일이 안 되므로 놓칠 일이 없다.
        // 나머지 초기값은 전부 아래 mirror 한 줄이 구독과 함께 맡는다.
        let vm = IosAppState.shared.viewModel
        account = vm.account.value
        profile = vm.profile.value
        hoyolabConfig = vm.hoyolabConfig.value
        mirrorFirstFrame()
        mirrorAfterFirstFrame()
        // 첫 프레임이 그려진 뒤에 나머지를 건다.
        Task { @MainActor in
            await Task.yield()
            startDeferredSubscriptions()
        }
    }

    deinit { tasks.forEach { $0.cancel() } }

    // MARK: - Flow 미러링 (초기값 + 구독)
    //
    // 구독을 **첫 화면에 필요한 것 / 나중에 필요한 것**으로 나눈다.
    //
    // mirror 한 건이 Swift Task 1개 + SKIE AsyncSequence 어댑터(Kotlin 쪽 collector 포함)를 만들고,
    // 그 Task 들이 전부 MainActor 큐에 쌓인다. 80여 개를 한꺼번에 걸면 첫 프레임을 그리려는 body 평가가
    // 그 뒤에 줄을 선다. 홈이 읽지 않는 도메인(가챠·선물코드·알림설정·저축 상세·공지 본문 등)은
    // 첫 프레임을 그린 뒤에 걸어도 화면이 달라지지 않는다.
    //
    // 초기값(StateFlow 의 현재 값)은 **넣는 것과 안 넣는 것을 일부러 가른다.**
    // · 넣는다 — 첫 프레임이 읽는 값(구독 콜백은 한 런루프 뒤라, 안 넣으면 홈 히어로가 0원으로 한 번 그려진다)과
    //   설정 토글 · 숫자(늦게 도착하면 '꺼짐'으로 한 번 그려져 오해를 부른다). 값 읽기(`.value`)는 Task 를
    //   만들지 않아 구독보다 훨씬 싸다.
    // · 안 넣는다 — 화면이 `.onChange` 로 소비하는 1회성 신호(pendingTab · onboardingReplay 등 — 처음부터
    //   들어 있으면 '변화'가 아니라서 놓친다)와, 비어 있는 상태가 자연스러운 목록형(스켈레톤 · 빈 목록).
    //
    // ⚠️ 아래 두 함수 안의 **줄 순서가 곧 구독을 거는 순서**다. 구획을 맞추려고 섞지 않는다 —
    // 값 여러 개가 같은 박자에 들어와야 하는 화면(홈의 준비 플래그와 목록 등)이 이 순서에 기대고 있을 수 있다.

    /// 첫 화면(루트 판정 + 홈 탭)이 실제로 읽는 것들 — 구독을 init 에서 바로 건다.
    private func mirrorFirstFrame() {
        mirror(vm.account, to: \.account, .now)
        mirror(vm.accentIndex, to: \.accentIndex, .now)
        mirror(vm.onboardingReplay, to: \.onboardingReplay, .nowUnseeded)
        mirror(vm.profile, to: \.profile, .now)
        mirror(vm.statusMessage, to: \.statusMessage, .now)
        mirror(vm.errorAlert, to: \.errorAlert, .nowUnseeded)
        mirror(vm.initialSyncing, to: \.initialSyncing, .now)
        mirror(vm.signingOut, to: \.signingOut, .nowUnseeded)
        mirror(vm.forceUpdate, to: \.forceUpdate, .nowUnseeded)
        mirror(vm.updateInfo, to: \.updateInfo, .nowUnseeded)

        // 파생값
        mirror(vm.currentMonthTotal, to: \.monthlyTotal, .now)
        mirror(vm.previousMonthTotal, to: \.prevMonthTotal, .now)
        mirror(vm.currentMonthTotalsByGame, to: \.monthlyTotalsByGame, .now) { $0.mapValues { $0.int64Value } }

        // Phase 2
        mirror(vm.spendings, to: \.spendings, .now)
        // 홈이 첫 프레임에 읽는 값은 **시작값도 넣는다**(27.52.0) — 예전엔 기본값으로 시작해서, 콜드 스타트 첫 프레임에 예산이
        // 「미설정」으로, 준비된 카드가 스켈레톤으로, 읽은 알림이 안 읽은 것으로, 출석한 게임이 미출석으로 한 번 그려졌다.
        // (예산 · 게임 정보 · 출석 · 알림 읽음 표시가 그 대상이다 — 아래에서 `.now` 인 줄들.)
        mirror(vm.budget, to: \.budget, .now)
        mirror(vm.gameBudgets, to: \.gameBudgets, .now) { $0.mapValues { $0.int64Value } }
        mirror(vm.hoyolabConfig, to: \.hoyolabConfig, .now)
        mirror(vm.attendanceStreak, to: \.attendanceStreak, .nowUnseeded)

        // 홈 카드·오늘 할 일·딥링크
        mirror(vm.isRefreshing, to: \.isRefreshing, .nowUnseeded)
        mirror(vm.activeBanners, to: \.activeBanners, .now)
        mirror(vm.liveNotes, to: \.liveNotes, .now)
        mirror(vm.gameEvents, to: \.gameEvents, .now)
        mirror(vm.challenges, to: \.challenges, .now)
        mirror(vm.gameNews, to: \.gameNews, .now)
        mirror(vm.combat, to: \.combat, .now)
        mirror(vm.combatClears, to: \.combatClears, .nowUnseeded)
        mirror(vm.combatClearsLoading, to: \.combatClearsLoading, .nowUnseeded)
        mirror(vm.combatClearsFailed, to: \.combatClearsFailed, .nowUnseeded)
        mirror(vm.trackedAttendanceGames, to: \.trackedAttendanceGames, .now)
        mirror(vm.attendanceToday, to: \.attendanceToday, .now)
        mirror(vm.checkingIn, to: \.checkingIn, .nowUnseeded)
        mirror(vm.gameInfoReady, to: \.gameInfoReady, .now)
        mirror(vm.scheduleReady, to: \.scheduleReady, .now)
        mirror(vm.newsReady, to: \.newsReady, .now)
        mirror(vm.newsFailed, to: \.newsFailed, .nowUnseeded)
        mirror(vm.hoyoTokenExpired, to: \.hoyoTokenExpired, .now)
        mirror(vm.readAlerts, to: \.readAlerts, .now)
        mirror(vm.dismissedAlerts, to: \.dismissedAlerts, .now)
        mirror(vm.challenge, to: \.challenge, .nowUnseeded) { $0 }
        mirror(vm.pendingTab, to: \.pendingTab, .nowUnseeded) { $0?.intValue }
        mirror(vm.pendingNewsId, to: \.pendingNewsId, .nowUnseeded)
        mirror(vm.pendingGameInfoAnchor, to: \.pendingGameInfoAnchor, .nowUnseeded)
    }

    /// 첫 프레임 이후에 걸어도 되는 것들 — 해당 화면에 들어가기 전에는 읽히지 않는다.
    /// 초기값(`.later`)은 지금 넣고, 구독만 `startDeferredSubscriptions()` 로 미룬다.
    private func mirrorAfterFirstFrame() {
        mirror(vm.gachaStats, to: \.gachaStats, .laterUnseeded)
        // 다른 파생값(이번 달 합계 등)과 같이 초기값은 넣어 둔다 — 구독만 첫 프레임 뒤다.
        mirror(vm.recentMonthlyTotals, to: \.recentMonthlyTotals, .later) { $0.map { $0.int64Value } }
        mirror(vm.autoCheckIn, to: \.autoCheckIn, .later)
        mirror(vm.notifyBudget, to: \.notifyBudget, .later)
        mirror(vm.notifyAttendance, to: \.notifyAttendance, .later)
        mirror(vm.notifyResin, to: \.notifyResin, .later)
        mirror(vm.notifyPickup, to: \.notifyPickup, .later)
        mirror(vm.nudgeOverspend, to: \.nudgeOverspend, .later)
        mirror(vm.spendingCompact, to: \.spendingCompact, .later)
        mirror(vm.charElementFx, to: \.charElementFx, .later)
        mirror(vm.collabBannerExpanded, to: \.collabBannerExpanded, .later)
        mirror(vm.hoyolandCart, to: \.hoyolandCart, .laterUnseeded)
        mirror(vm.hoyolandEntry, to: \.hoyolandEntry, .laterUnseeded)
        mirror(vm.nudgeThreshold, to: \.nudgeThreshold, .later)
        mirror(vm.pendingOpenHoyolabLink, to: \.pendingOpenHoyolabLink, .laterUnseeded)
        mirror(vm.taskStats, to: \.taskStats, .laterUnseeded)
        mirror(vm.keyStatOverrides, to: \.keyStatOverrides, .laterUnseeded)

        // Phase 3
        mirror(vm.attendanceHistory, to: \.attendanceHistory, .laterUnseeded)

        // Phase 4
        mirror(vm.newsArticle, to: \.newsArticle, .laterUnseeded)
        mirror(vm.newsArticleLoading, to: \.newsArticleLoading, .laterUnseeded)
        mirror(vm.ledgers, to: \.ledgers, .laterUnseeded)
        // chunk ③
        mirror(vm.gameVersions, to: \.gameVersions, .laterUnseeded)
        mirror(vm.gameVersionsLoading, to: \.gameVersionsLoading, .laterUnseeded)
        mirror(vm.weaponRefinement, to: \.weaponRefinement, .laterUnseeded)
        mirror(vm.charCamp, to: \.charCamp, .laterUnseeded)
        mirror(vm.enkaResults, to: \.enkaResults, .laterUnseeded)
        mirror(vm.enkaLoadingGames, to: \.enkaLoadingGames, .laterUnseeded)
        mirror(vm.gachaDashboard, to: \.gachaDashboard, .laterUnseeded)
        mirror(vm.redeemState, to: \.redeemState, .laterUnseeded)
        mirror(vm.activeCodes, to: \.activeCodes, .laterUnseeded)
        mirror(vm.noteErrors, to: \.noteErrors, .laterUnseeded)
        mirror(vm.codesLoading, to: \.codesLoading, .laterUnseeded)
        mirror(vm.codesFailed, to: \.codesFailed, .laterUnseeded)
        mirror(vm.redeemedCodes, to: \.redeemedCodes, .laterUnseeded)
        mirror(vm.unusableCodes, to: \.unusableCodes, .laterUnseeded)
        // Phase 6 (알림 설정)
        mirror(vm.notifyNews, to: \.notifyNews, .later)
        // 형제 토글과 같은 규칙으로 초기값을 넣는다(27.52.0) — 이 하나만 빠져 있었다.
        mirror(vm.notifyAppUpdate, to: \.notifyAppUpdate, .later)
        mirror(vm.notifyHoyoland, to: \.notifyHoyoland, .later)
        mirror(vm.notifyCombat, to: \.notifyCombat, .later)
        mirror(vm.notifyDndEnabled, to: \.notifyDndEnabled, .later)
        mirror(vm.myGames, to: \.myGames, .later)
        mirror(vm.notifyDndStartHour, to: \.notifyDndStartHour, .later)
        mirror(vm.notifyDndEndHour, to: \.notifyDndEndHour, .later)
    }

    /// 미뤄 둔 구독을 **등록한 순서대로** 건다 — 첫 프레임 뒤에 한 번.
    private func startDeferredSubscriptions() {
        let pending = deferredSubscriptions
        deferredSubscriptions = []
        pending.forEach { $0() }
    }

    // MARK: - 미러 도우미

    /// mirror 한 줄이 **초기값을 넣는지 · 구독을 언제 거는지.** 고르는 기준은 위 「Flow 미러링」 주석.
    private enum MirrorTiming {
        /// 지금 값을 넣고 곧바로 구독한다 — 첫 프레임이 읽는 값.
        case now
        /// 곧바로 구독만 한다. 첫 값은 콜렉터가 한 런루프 뒤에 준다 — 1회성 신호 · 목록형.
        case nowUnseeded
        /// 지금 값을 넣고, 구독은 첫 프레임 뒤에 건다 — 설정 토글 · 숫자.
        case later
        /// 첫 프레임 뒤에 구독만 건다 — 그 화면에 들어가기 전에는 읽히지 않는 목록 · 진행 상태.
        case laterUnseeded

        var seeds: Bool { self == .now || self == .later }
        var subscribesNow: Bool { self == .now || self == .nowUnseeded }
    }

    /// StateFlow 하나를 프로퍼티 하나에 비춘다 — **초기값 채우기와 구독을 한 번에.**
    /// [map] 은 Kotlin 값 → Swift 프로퍼티 타입 변환. 초기값과 구독이 같은 변환을 타므로 둘이 어긋날 수 없다.
    private func mirror<T, V>(_ flow: SkieSwiftStateFlow<T>, to keyPath: ReferenceWritableKeyPath<SpendingStore, V>,
                              _ timing: MirrorTiming, _ map: @escaping (T) -> V) {
        if timing.seeds { self[keyPath: keyPath] = map(flow.value) }
        subscribe(timing) { [weak self] in
            for await value in flow { self?[keyPath: keyPath] = map(value) }
        }
    }

    /// nullable StateFlow(StateFlow<T?>) 전용 — SKIE 는 SkieSwiftOptionalStateFlow 로 노출한다.
    private func mirror<T, V>(_ flow: SkieSwiftOptionalStateFlow<T>, to keyPath: ReferenceWritableKeyPath<SpendingStore, V>,
                              _ timing: MirrorTiming, _ map: @escaping (T?) -> V) {
        if timing.seeds { self[keyPath: keyPath] = map(flow.value) }
        subscribe(timing) { [weak self] in
            for await value in flow { self?[keyPath: keyPath] = map(value) }
        }
    }

    // 변환이 뻔한 것들은 [map] 을 생략한다 — 같은 타입 그대로, Kotlin 상자(Boolean · Int · Long) → Swift 값.
    private func mirror<T>(_ flow: SkieSwiftStateFlow<T>, to keyPath: ReferenceWritableKeyPath<SpendingStore, T>,
                           _ timing: MirrorTiming) {
        mirror(flow, to: keyPath, timing) { $0 }
    }
    private func mirror<T>(_ flow: SkieSwiftOptionalStateFlow<T>, to keyPath: ReferenceWritableKeyPath<SpendingStore, T?>,
                           _ timing: MirrorTiming) {
        mirror(flow, to: keyPath, timing) { $0 }
    }
    private func mirror(_ flow: SkieSwiftStateFlow<KotlinBoolean>, to keyPath: ReferenceWritableKeyPath<SpendingStore, Bool>,
                        _ timing: MirrorTiming) {
        mirror(flow, to: keyPath, timing) { $0.boolValue }
    }
    private func mirror(_ flow: SkieSwiftStateFlow<KotlinInt>, to keyPath: ReferenceWritableKeyPath<SpendingStore, Int>,
                        _ timing: MirrorTiming) {
        mirror(flow, to: keyPath, timing) { Int($0.int32Value) }
    }
    private func mirror(_ flow: SkieSwiftStateFlow<KotlinLong>, to keyPath: ReferenceWritableKeyPath<SpendingStore, Int64>,
                        _ timing: MirrorTiming) {
        mirror(flow, to: keyPath, timing) { $0.int64Value }
    }

    /// 구독 Task 를 지금 띄우거나(`.now*`) 첫 프레임 뒤로 미룬다(`.later*`).
    private func subscribe(_ timing: MirrorTiming, _ follow: @escaping @MainActor () async -> Void) {
        let start: @MainActor () -> Void = { [weak self] in
            self?.tasks.append(Task { @MainActor in await follow() })
        }
        if timing.subscribesNow { start() } else { deferredSubscriptions.append(start) }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 아래는 **상태 선언 + 액션 패스스루**를 Kotlin 쪽 묶음별로 나눈 구획이다.
    // 괄호 안 이름은 `data/vm/*Controller.kt` — 괄호가 없으면 SpendingViewModel 본체가 들고 있다.
    // ════════════════════════════════════════════════════════════════════════

    // MARK: - 계정 · 프로필 · 테마 · 앱 생애주기

    private(set) var account: Account
    private(set) var profile: UserProfile
    private(set) var accentIndex: Int = 0
    private(set) var initialSyncing: Bool = false
    /// 로그아웃 진행 중 — 네트워크 대기 동안 오버레이를 띄운다(Android SignOutOverlay 대응).
    private(set) var signingOut: Bool = false
    /** 개발자 메뉴 「온보딩 초기화」 누른 횟수 — 바뀌면 ContentView 가 온보딩을 다시 띄운다. */
    private(set) var onboardingReplay: Int = 0
    /// 내 게임(온보딩 ② · 설정 ▸ 내 게임). 비어 있으면 전부.
    private(set) var myGames: Set<String> = []

    /// 로그인 필요 여부 — 미로그인(게스트 모드 없음, 구글 로그인 필수).
    /// (v27.38.0 에서 needsOnboarding → needsLogin 개명 — 첫 실행 온보딩이 별도로 생겨 이름이 헷갈렸다)
    var needsLogin: Bool { account.isGuest }

    /// 구글 로그인(원탭).
    func signIn() { vm.signIn() }
    /// 로그아웃.
    func signOut() { vm.signOut() }
    /// 강조색 변경. 낙관적 즉시 반영 — SKIE StateFlow 구독 콜백이 한 런루프 지연될 수 있어,
    /// 미러 프로퍼티를 곧바로 갱신해 설정 화면 체크마크·전역 accent 가 탭 즉시 바뀌게 한다(설정 이탈 없이).
    func setAccentIndex(_ index: Int) {
        accentIndex = index
        vm.setAccentIndex(index: Int32(index))
    }
    /// 프로필 이름 변경.
    func setProfileName(_ name: String) { vm.setProfileName(name: name) }
    func setMyGames(_ keys: Set<String>) { vm.setMyGamesList(keys: Array(keys)) }
    /// 온보딩 B안에서 고른 값을 설정과 같은 저장소에 쓴다(Kotlin applyOnboarding). budget -1 = 예산 없이.
    func applyOnboarding(games: Set<String>, budget: Int64, alerts: Bool, attendance: Bool, resin: Bool, pickup: Bool, budgetAlert: Bool) {
        vm.applyOnboarding(games: Array(games), budget: budget, alerts: alerts, attendance: attendance, resin: resin, pickup: pickup, budgetAlert: budgetAlert)
    }
    /// 개발자 메뉴의 테스트용 온보딩이었으면 모드를 풀고 true.
    func finishOnboardingPreview() -> Bool { vm.finishOnboardingPreview() }
    /// 초기 동기화 로딩 게이트 완료 표시 (AccountLoadingView 완료 시).
    func markSyncLoadingDone() { MainViewControllerKt.markSyncLoadingDone() }
    /// 앱 복귀 — 오래 떠나 있었으면 데이터 갱신 + 밀린 알림 1회 점검(BGTask 가 OS 재량이라 그것만으론 구멍이 크다).
    func onAppForeground() { vm.onAppForeground() }
    func setRootReady(_ ready: Bool) { vm.setRootReady(ready: ready) }
    /// 앱이 백그라운드로 내려간 시각 기록 — 복귀 시 '얼마나 떠나 있었는지' 판정에 쓴다.
    func onAppBackground() { vm.onAppBackground() }

    // MARK: - 피드백 (FeedbackController)

    private(set) var statusMessage: String?
    /// 오류 얼럿 모달용(nil 이 아니면 표시). 토스트(statusMessage)와 분리.
    /// 네트워크 미연결·HoYoLAB 연동 만료·클라우드 백업 실패가 모두 이 자리에 뜬다 — 제목이 종류마다 달라
    /// 문자열이 아니라 `ErrorAlert`(title/message)로 받는다.
    private(set) var errorAlert: ErrorAlert?

    func showStatus(_ msg: String) { vm.showStatus(msg: msg) }
    /// 상태 토스트 소비.
    func clearStatus() { vm.clearStatus() }
    /// 네트워크 미연결 얼럿 소비.
    func clearErrorAlert() { vm.clearErrorAlert() }

    // MARK: - 앱 업데이트 (AppUpdateController)

    /// 강제 업데이트 — 현재 버전이 최소 지원 버전 미만. true 면 앱 위에 닫히지 않는 업데이트 화면을 덮는다.
    private(set) var forceUpdate: Bool = false
    /// 받아 온 새 버전 정보(없으면 nil). 화면은 아래 세 값으로 읽는다 — 한 Flow 가 셋을 함께 바꾸므로
    /// 미러는 하나만 두고 나머지는 여기서 꺼낸다(미러된 프로퍼티를 읽는 계산값이라 관찰이 이어진다).
    private var updateInfo: UpdateInfo? = nil
    /// 업데이트 대상 버전명(강제 업데이트 화면 표시용).
    var updateVersionName: String { updateInfo?.versionName ?? "" }
    /// 새 버전이 있다 — 강제가 아니면 앱 루트가 업데이트 창을 띄운다(27.51.1, Android UpdateDialog 파리티).
    var updateAvailable: Bool { updateInfo != nil }
    /// 새 버전의 변경 사항(업데이트 창 본문).
    var updateNotes: [String] { updateInfo?.notes ?? [] }

    func checkForUpdate(manual: Bool = true) { vm.checkForUpdate(manual: manual) }
    /// 강제 업데이트 화면의 '지금 업데이트' — iOS 는 릴리스 페이지를 연다(사이드로딩).
    func startInAppUpdate() { vm.startInAppUpdate() }
    /// 업데이트 창의 「나중에」 — 강제 업데이트면 닫히지 않는다.
    func dismissUpdate() { vm.dismissUpdate() }

    // MARK: - 지출 · 예산 · 백업

    private(set) var spendings: [Spending] = []
    private(set) var budget: Int64 = 0
    private(set) var gameBudgets: [String: Int64] = [:]
    private(set) var nudgeOverspend: Bool = false
    private(set) var nudgeThreshold: Int64 = 0
    private(set) var spendingCompact: Bool = false
    private(set) var challenge: ChallengeSummary? = nil

    // ── 파생값(Kotlin 에서 한 번 계산해 내려온다) ──
    //
    // 예전엔 vm.monthlyTotal(...) 처럼 그리는 도중에 호출하는 메서드였다. 그러면 ① 화면이 한 번
    // 그려질 때마다 지출 전체를 다시 훑고 ② @Observable 이 vm 접근을 추적하지 못해 값이 바뀌어도
    // 화면이 안 바뀐다. 둘 다 StateFlow 미러링으로 해결한다.
    /// 이번 달 총 지출.
    private(set) var monthlyTotal: Int64 = 0
    /// 전월 총 지출(MoM 비교용).
    private(set) var prevMonthTotal: Int64 = 0
    /// 이번 달 게임별 지출 합계(gameKey → 금액).
    private(set) var monthlyTotalsByGame: [String: Int64] = [:]
    /// 최근 6개월 총 지출(오래된 달 → 이번 달 순) — 마이페이지 월별 추이 차트.
    private(set) var recentMonthlyTotals: [Int64] = []

    var displayYear: Int { Int(vm.displayYear) }
    var displayMonth: Int { Int(vm.displayMonth) }

    /// 전체 예산 + 게임별 한도 저장.
    func setBudgets(overall: Int64, perGame: [String: Int64]) {
        vm.setBudgets(overall: overall, perGame: perGame.mapValues { KotlinLong(value: $0) })
    }
    func setNudgeOverspend(_ v: Bool) { vm.setNudgeOverspend(v: v) }
    func setNudgeThreshold(_ v: Int64) { vm.setNudgeThreshold(v: v) }
    func setSpendingCompact(_ v: Bool) { vm.setSpendingCompact(v: v) }
    /// N6 과소비 넛지 판정 — 경고 메시지 또는 nil.
    /// 날짜도 넘긴다(27.52.0) — 지난달 날짜로 추가할 때는 이번 달 예산 경고를 내지 않는다.
    func overspendNudge(game: Game, amount: Int64, editingId: String?, dateMillis: Int64) -> String? {
        vm.overspendNudge(game: game, amount: amount, editingId: editingId, dateMillis: dateMillis)
    }
    /// 같은 판정의 간결한 한 줄("평소보다 큰 지출" 등) — 지출 입력 화면의 게임 칩 옆 표시가 쓴다. 경고할 것이 없으면 nil.
    func overspendBadge(game: Game, amount: Int64, editingId: String?, dateMillis: Int64) -> String? {
        vm.overspendBadge(game: game, amount: amount, editingId: editingId, dateMillis: dateMillis)
    }
    /// 지출 추가/수정 저장 (Spending 생성은 Kotlin 헬퍼).
    @discardableResult
    func saveSpending(editingId: String?, gameName: String, amount: Int64, dateMillis: Int64,
                      paymentMethod: String, chargePlatform: String, itemName: String, memo: String, tags: [String]) -> Bool {
        MainViewControllerKt.saveSpending(editingId: editingId, gameName: gameName, amount: amount, dateMillis: dateMillis,
                                          paymentMethod: paymentMethod, chargePlatform: chargePlatform, itemName: itemName, memo: memo, tags: tags)
    }
    func deleteSpending(_ id: String) { vm.deleteSpending(id: id) }
    func deleteSpendings(_ ids: Set<String>) { vm.deleteSpendings(ids: ids) }
    /// 선택 지출 일괄 변경(게임/날짜/추가 태그). nil·빈값은 미변경.
    func bulkEditSpendings(ids: Set<String>, gameName: String?, dateMillis: Int64?, addTags: [String]) {
        vm.bulkEditSpendings(ids: ids, gameName: gameName, dateMillis: dateMillis.map { KotlinLong(value: $0) }, addTags: addTags)
    }
    func clearSpendings() { vm.clearSpendings() }
    func refreshSpending() { vm.refreshSpending() }
    /// 지정 연/월 총 지출 (마이페이지 월별 추이 차트용 — 과거 달이라 값이 안 바뀌므로 그때그때 계산).
    func monthlyTotal(year: Int32, month: Int32) -> Int64 { vm.monthlyTotal(year: year, month: month) }

    /// 백업 스냅샷 JSON (없으면 nil).
    func exportBackupContent() -> String? { vm.exportBackupContent() }
    /// 백업 JSON 복원.
    func importBackupFromContent(_ json: String) { vm.importBackupFromContent(json: json) }
    /// 지출 CSV 문자열.
    func buildCsv() -> String { vm.buildCsv() }

    // MARK: - HoYoLAB 연동 · 출석

    private(set) var hoyolabConfig: HoyolabConfig
    private(set) var hoyoTokenExpired: Bool = false
    private(set) var autoCheckIn: Bool = false
    private(set) var attendanceStreak: Int = 0
    private(set) var attendanceHistory: [String: Set<String>] = [:]
    /// 출석을 세는 게임(연동 계정에 UID 가 있는 게임만) — 출석 분모·미출석 개수에 쓴다.
    private(set) var trackedAttendanceGames: [Game] = GameData.shared.attendanceGames
    private(set) var attendanceToday: Set<String> = []
    private(set) var checkingIn: String? = nil
    private(set) var pendingOpenHoyolabLink: Bool = false

    /// false 면 검증·저장 실패(안내 토스트는 VM 이 띄운다) — 폼은 닫지 않는다.
    @discardableResult
    func updateHoyolabConfig(_ config: HoyolabConfig) -> Bool { vm.updateHoyolabConfig(input: config) }
    func refreshHoyoTokenExpired() { vm.refreshHoyoTokenExpired() }
    func requestOpenHoyolabLink() { vm.requestOpenHoyolabLink() }
    func consumePendingOpenHoyolabLink() { vm.consumePendingOpenHoyolabLink() }
    func setAutoCheckIn(_ enabled: Bool) { vm.setAutoCheckIn(enabled: enabled) }
    func attemptCheckIn(_ gameKey: String) { vm.attemptCheckIn(gameKey: gameKey) }
    func checkInAll() { vm.checkInAll() }

    // MARK: - 게임 정보 (홈 카드 · 데일리 · 일정 · 소식 목록)

    private(set) var isRefreshing: Bool = false
    private(set) var gameInfoReady: Bool = false
    /// 일정·소식 카드 각각의 표출 준비 상태 — 출처가 달라 `gameInfoReady` 와 따로 둔다(VM 주석 참고).
    private(set) var scheduleReady: Bool = false
    private(set) var newsReady: Bool = false
    /// 소식을 한 게임도 못 받았다 — '소식 없음'과 구분(재시도 UI 표시용).
    private(set) var newsFailed: Bool = false
    private(set) var activeBanners: [GachaBanner] = []
    private(set) var liveNotes: [LiveNote] = []
    /// 게임키 → 실시간 노트가 실패한 이유(사용자가 손쓸 수 있는 것만). 노트 없는 칸에 대신 보여준다.
    private(set) var noteErrors: [String: String] = [:]
    /// 게임별 일일·주간 숙제 완주율(관측 기록 파생).
    private(set) var taskStats: [TaskStats] = []
    private(set) var gameEvents: [GameEvent] = []
    private(set) var challenges: [GameChallenge] = []
    private(set) var gameNews: [NewsItem] = []
    private(set) var ledgers: [MonthlyLedger] = []
    private(set) var combat: [CombatMode] = []
    private(set) var collabBannerExpanded: Bool = false

    // Kotlin 기본 인자는 Swift 로 안 넘어온다 — silent 를 명시해야 한다(사용자가 부른 새로고침이므로 false).
    func refreshGameInfo(force: Bool = false) { vm.refreshGameInfo(force: force, silent: false) }
    /// 당겨서 새로고침용 — 게임 정보 갱신을 걸고 **끝날 때까지** 기다린다(최대 10초).
    /// `.refreshable` 은 이 함수가 돌아오는 순간 스피너를 걷는다 — 바로 돌아오면 당기자마자 걷혀 갱신이 안 된 것처럼 보인다.
    /// 미러링된 `isRefreshing` 이 아니라 **vm 값을 직접** 본다. 미러는 콜렉터를 한 번 거쳐 한 박자 늦을 수 있어,
    /// 그 사이에 보면 아직 false 라 기다리지 않고 빠져나온다.
    func refreshGameInfoAndWait() async {
        vm.refreshGameInfo(force: true, silent: false)
        for _ in 0..<100 {
            try? await Task.sleep(nanoseconds: 100_000_000)
            if !vm.isRefreshing.value.boolValue { break }
        }
    }
    func setCollabBannerExpanded(_ v: Bool) { vm.setCollabBannerExpanded(v: v) }

    // MARK: - 공지 본문 (NewsArticleController)

    /// 공지 본문(상세 페이지) — 실패해도 화면을 비우지 않고 NewsItem.summary 로 폴백한다.
    private(set) var newsArticle: NewsArticle? = nil
    private(set) var newsArticleLoading: Bool = false

    /// 공지 상세 진입 — 본문 로드. 이탈 시 clearNewsArticle() 로 정리한다.
    ///
    /// 부른 뒤 **미러를 그 자리에서 맞춘다**(27.52.0). Kotlin 은 이 호출 안에서 곧바로 앞 글 본문을 비우고
    /// 로딩을 켜는데, 구독 콜백은 한 런루프 뒤에 온다 — 그 사이에 그려지면 새 글 제목 아래에
    /// 앞 글 본문이 비친다. 공지 상세는 이 호출이 끝난 뒤의 슬롯을 「이 글 것」으로 믿고 그린다(NewsDetailView).
    func loadNewsArticle(_ item: NewsItem) {
        vm.loadNewsArticle(item: item)
        newsArticle = vm.newsArticle.value
        newsArticleLoading = vm.newsArticleLoading.value.boolValue
    }
    func clearNewsArticle() { vm.clearNewsArticle() }

    // MARK: - 전투 클리어 (CombatClearsController)

    /// 엔드 콘텐츠 클리어 편성(층·간별로 어떤 캐릭터를 썼는지).
    private(set) var combatClears: [CombatClear] = []
    private(set) var combatClearsLoading: Bool = false
    /// 클리어 편성 조회가 전부 실패 — '기록 없음'과 구분(재시도 UI 표시용).
    private(set) var combatClearsFailed: Bool = false

    /// 클리어 편성 조회 — 전용 페이지 진입 시에만(시즌 2개치라 무겁다).
    func refreshCombatClears(force: Bool = false) { vm.refreshCombatClears(force: force) }

    // MARK: - 로스터 (EnkaRosterController) · 캐릭터 상세 설정

    // '내 캐릭터' 섹션(헤더 필터 연동) — 게임별 결과/로딩 동시 보관
    private(set) var enkaResults: [String: EnkaResult] = [:]
    private(set) var enkaLoadingGames: Set<String> = []
    /// 캐릭터별 유효옵션 사용자 설정(키=keyStatOverrideKey).
    private(set) var keyStatOverrides: [String: Set<String>] = [:]
    /// 캐릭터 상세 속성 연출 — 끄면 움직임 없이 속성 테두리만 남는다.
    private(set) var charElementFx: Bool = true

    func loadEnkaProfile(game: String, uid: String) { vm.loadEnkaProfile(game: game, uid: uid) }
    func autoLoadEnka(game: String, force: Bool = false) { vm.autoLoadEnka(game: game, force: force) }
    // ttlMs 300_000 = VM 의 enkaTtlMs(5분) — Kotlin 기본값이 Swift 에 안 보여 명시한다.
    func autoLoadEnkaSection(games: [String], force: Bool = false) { vm.autoLoadEnkaSection(games: games, force: force, ttlMs: 300_000) }
    func clearEnkaResult() { vm.clearEnkaResult() }
    /// 유효옵션 직접 설정 저장(빈 집합이면 해제 → 앱 룰 추정으로 되돌아간다).
    func setKeyStatOverride(_ key: String, _ stats: Set<String>) { vm.setKeyStatOverride(key: key, stats: stats) }
    func setCharElementFx(_ v: Bool) { vm.setCharElementFx(v: v) }

    // MARK: - 캐릭터 메타 (CharMetaController)

    // 현재 게임 버전(nanoka) — 데일리 타일 아래 한 줄
    private(set) var gameVersions: [GameVersionLine] = []
    /// 그 값을 처음 받아오는 중 — 데일리가 그동안 같은 자리에 스켈레톤을 세운다.
    private(set) var gameVersionsLoading: Bool = false
    private(set) var weaponRefinement: [String: WeaponRefinement] = [:]
    /// 캐릭터 소속 — 원신은 국가, 스타레일은 진영. 키는 "게임키:캐릭터id".
    private(set) var charCamp: [String: String] = [:]

    func loadGameVersions() { vm.loadGameVersions(force: false) }
    func loadCharCamp(_ gameKey: String, _ charId: Int32) { vm.loadCharCamp(gameKey: gameKey, charId: charId) }
    func loadWeaponRefinement(game: String, weaponId: Int32, level: Int32) {
        vm.loadWeaponRefinement(gameKey: game, weaponId: weaponId, level: level)
    }

    // MARK: - 가챠 기록 (GachaRecordsController) · 천장

    private(set) var gachaStats: GachaStats?
    private(set) var gachaDashboard: GachaDashboard? = nil

    func importGachaFromContents(_ contents: [String]) { vm.importGachaFromContents(contents: contents) }
    func clearGachaRecords() { vm.clearGachaRecords() }
    /// 가챠 단가 계산용 게임별 지출 — 키: genshin/starrail/zzz (GachaStats.byGame 키와 일치).
    func gachaSpendByGame() -> [String: Int64] {
        GachaReport.shared.spendByGameKey(spendings: spendings).mapValues { $0.int64Value }
    }
    func adjustPity(gameKey: String, delta: Int) { vm.adjustPity(gameKey: gameKey, delta: Int32(delta)) }
    func resetPity(gameKey: String) { vm.resetPity(gameKey: gameKey) }
    func setPityGuaranteed(gameKey: String, _ g: Bool) { vm.setPityGuaranteed(gameKey: gameKey, g: g) }

    // MARK: - 선물코드 (GiftCodeController)

    private(set) var redeemState: RedeemState = RedeemStateIdle.shared
    private(set) var activeCodes: [GiftCode] = []
    private(set) var codesLoading: Bool = false
    /// 코드 수집 실패 — '활성 코드 없음'과 구분(재시도 UI 표시용).
    private(set) var codesFailed: Bool = false
    private(set) var redeemedCodes: Set<String> = []
    /// 더는 못 받는다고 판정돼 목록에서 가려진 코드. 로컬 전용이라 클라우드 복원으로도 안 풀린다 —
    /// 판정이 틀렸을 때 사용자가 되돌릴 수 있도록 개수를 화면에 노출한다.
    private(set) var unusableCodes: Set<String> = []

    // force = true 면 게임별 10분 캐시를 건너뛴다(재시도·새로고침 버튼용).
    func loadActiveCodes(_ gameKey: String, force: Bool = false) { vm.loadActiveCodes(gameKey: gameKey, force: force) }
    func redeemGiftCode(gameKey: String, code: String) { vm.redeemGiftCode(gameKey: gameKey, code: code) }
    func redeemAllCodes(_ gameKey: String) { vm.redeemAllCodes(gameKey: gameKey) }
    /// 가려 둔 코드를 전부 되살린다(잘못된 판정 복구).
    func restoreUnusableCodes(_ gameKey: String) { vm.restoreUnusableCodes(gameKey: gameKey) }
    func resetRedeem() { vm.resetRedeem() }

    // MARK: - 호요랜드 계획 (HoyolandPlanController)

    /// 호요랜드 굿즈 장바구니 — 담은 것은 공유 계층(AppSettings)에 저장된다.
    private(set) var hoyolandCart: Shared.HoyolandCart = Shared.HoyolandCart(items: [:])
    /// 호요랜드 내 입장권 — 날짜마다 정해 둔 조. 저장은 공유 계층(AppSettings)이 맡는다.
    private(set) var hoyolandEntry: Shared.HoyolandEntry = Shared.HoyolandEntry(groups: [:])

    // ── 굿즈 장바구니 — 예산 가늠용(주문이 아니다) ──
    func toggleGoods(_ name: String) { vm.toggleGoods(name: name) }
    func setGoodsQuantity(_ name: String, _ quantity: Int) { vm.setGoodsQuantity(name: name, quantity: Int32(quantity)) }
    func clearGoodsCart() { vm.clearGoodsCart() }
    /// 그날의 조를 정한다. **같은 조를 다시 누르면 해제**되어 안 가는 날로 돌아간다(공유 계층 규칙).
    func setEntryGroup(_ ymd: String, _ group: String) { vm.setEntryGroup(ymd: ymd, group: group) }
    func clearEntry() { vm.clearEntry() }

    // MARK: - 알림 설정 (NotifySettingsController)

    private(set) var notifyBudget: Bool = false
    private(set) var notifyAttendance: Bool = false
    private(set) var notifyResin: Bool = false
    private(set) var notifyPickup: Bool = false
    // 27.33.0 — 항목 추가 · 방해금지
    private(set) var notifyNews: Bool = false
    private(set) var notifyAppUpdate: Bool = false
    private(set) var notifyHoyoland: Bool = false
    private(set) var notifyCombat: Bool = true
    private(set) var notifyDndEnabled: Bool = false
    private(set) var notifyDndStartHour: Int = 23
    private(set) var notifyDndEndHour: Int = 8

    func setNotifyBudget(_ v: Bool) { vm.setNotifyBudget(v: v) }
    func setNotifyAttendance(_ v: Bool) { vm.setNotifyAttendance(v: v) }
    func setNotifyResin(_ v: Bool) { vm.setNotifyResin(v: v) }
    func setNotifyPickup(_ v: Bool) { vm.setNotifyPickup(v: v) }
    func setNotifyNews(_ v: Bool) { vm.setNotifyNews(v: v) }
    func setNotifyAppUpdate(_ v: Bool) { vm.setNotifyAppUpdate(v: v) }
    func setNotifyHoyoland(_ v: Bool) { vm.setNotifyHoyoland(v: v) }
    func setNotifyCombat(_ v: Bool) { vm.setNotifyCombat(v: v) }
    /// OS 알림 권한을 처음 허용했을 때 — 항목별 알림 일곱 개를 한꺼번에 켠다.
    /// (데일리 요약·방해금지는 발송 방식 설정이라 제외 — `enableAllNotifyItems` 주석 참고)
    func enableAllNotifyItems() { vm.enableAllNotifyItems() }
    func setNotifyDndEnabled(_ v: Bool) { vm.setNotifyDndEnabled(v: v) }
    func setNotifyDndStartHour(_ v: Int) { vm.setNotifyDndStartHour(v: Int32(v)) }
    func setNotifyDndEndHour(_ v: Int) { vm.setNotifyDndEndHour(v: Int32(v)) }

    // MARK: - 홈 알림 · 딥링크

    private(set) var readAlerts: Set<String> = []
    private(set) var dismissedAlerts: Set<String> = []
    /// 홈 카드 → 게임 정보 탭 진입 시 스크롤할 섹션 앵커(1회성). nil 이면 없음.
    private(set) var pendingGameInfoAnchor: GameInfoAnchor? = nil
    /// 알림 딥링크가 요청한 탭·공지(각각 ContentView / GameInfoView 가 소비).
    private(set) var pendingTab: Int? = nil
    private(set) var pendingNewsId: String? = nil

    func markAlertsRead(_ keys: [String]) { vm.markAlertsRead(keys: keys) }
    func dismissAlert(_ key: String) { vm.dismissAlert(key: key) }
    func dismissAlerts(_ keys: [String]) { vm.dismissAlerts(keys: keys) }
    /// 홈 카드 → 게임 정보 탭 스크롤 앵커 요청/소비.
    func requestGameInfoAnchor(_ anchor: GameInfoAnchor) { vm.requestGameInfoAnchor(anchor: anchor) }
    func consumeGameInfoAnchor() { vm.consumeGameInfoAnchor() }
    /// 알림 payload 의 딥링크 처리("news:<공지 id>") — 탭 전환 + 상세 진입 상태를 세운다.
    func handleNotificationLink(_ link: String) { vm.handleNotificationLink(link: link) }
    func consumePendingTab() { vm.consumePendingTab() }
    func consumePendingNews() { vm.consumePendingNews() }

    // MARK: - 화면끼리 주고받는 신호 (Kotlin 미러 아님)

    /**
     하단에 액션 바가 떠 있어 '추가' FAB 를 감춰야 하는가.

     지출 목록은 같은 목적을 `ContentView` 의 `@State` + `Binding` 으로 넘기지만(SpendingView 가
     탭 바로 아래라 가능하다), 굿즈 목록은 **게임정보 탭 ▸ 호요랜드 ▸ 굿즈**로 세 단계 아래라
     바인딩을 줄줄이 내려야 한다. 스토어에 두면 어디서든 같은 신호를 쓸 수 있다.
     */
    var hidesAddButton: Bool = false
    /// 홈에서 호요랜드 페이지가 열려 있는가 — iOS 18(탭바 위 '+' 오버레이)에서 그동안 '추가'를 감춘다.
    /// 호요랜드의 하위 페이지(굿즈 · 부스 · 배치도 …)는 같은 스택 위에 쌓이므로 홈으로 돌아올 때까지 참이다.
    /// 홈에서 하위 페이지(호요랜드 · 예산 관리)가 열려 있는가 — 그동안 '+' 를 감춘다.
    var homeSubpageOpen: Bool = false
    /// 지출 탭에서 캘린더 · 인사이트가 열려 있는가 — 위와 같은 까닭으로 그동안 '+' 를 감춘다(27.52.0).
    /// 둘은 툴바의 뷰 기반 `NavigationLink` 로 열려 ContentView 의 경로(spendingPath)에 안 잡힌다. 상세는 경로로 본다.
    var spendingSubpageOpen: Bool = false

    // MARK: - 개발자 메뉴 (디버그 빌드 전용)
    // 어떤 UI 는 특정 상태에서만 나타나 눈으로 확인할 방법이 없다(3게임 행동력 가득의 비상벨 등).
    // 판단·계산은 전부 공유 VM 의 debug* 가 한다 — 여기는 넘겨주기만.
    func debugFillAllResin() { vm.debugFillAllResin() }
    /// 호요랜드 무대 시간표 목업 — 실제 편성 공개 전에 라이브 카드·게임 레인·필터를 본다.
    func debugStageMock(_ on: Bool) { vm.debugStageMock(on: on) }
    func debugStageMockOn() -> Bool { vm.debugStageMockOn() }
    /// 호요랜드 행사 단계 목업 — 개막 전 → 진행 중 → 종료 → 끔. 바뀐 뒤의 키를 돌려준다.
    func debugCycleHoyolandPhase() -> String { vm.debugCycleHoyolandPhase() }
    func debugHoyolandPhaseKey() -> String { vm.debugHoyolandPhaseKey() }
    func debugHoyolandPhaseLabel(_ key: String) -> String { vm.debugHoyolandPhaseLabel(key: key) }
    func debugSetPityAll(count: Int, guaranteed: Bool) { vm.debugSetPityAll(count: Int32(count), guaranteed: guaranteed) }
    func debugResetOnboarding() { vm.debugResetOnboarding() }
    func debugAccountSummary() -> String { vm.debugAccountSummary() }
    func debugProbeZzzCombat(_ onResult: @escaping ([String]) -> Void) { vm.debugProbeZzzCombat(onResult: onResult) }
    func debugPingApis(_ onResult: @escaping ([String]) -> Void) { vm.debugPingApis(onResult: onResult) }
    func debugPingHistory() -> [String] { vm.debugPingHistory() }
    func debugPingRepeat(_ times: Int) { vm.debugPingRepeat(times: Int32(times)) }
    func debugScheduledAlerts() -> [String] { vm.debugScheduledAlerts() }
    func debugReadyStates() -> String { vm.debugReadyStates() }
    func debugPerGameData() -> [String] { vm.debugPerGameData() }
}
