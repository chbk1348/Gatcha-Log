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
// 값이 바뀌어도 화면이 갱신되지 않는다. 파생값은 Kotlin 쪽 StateFlow 로 만들어 bind 로 받는다.
//
// ⚠️ 확장 정책: 이 스토어는 Phase 마다 "그 화면이 실제로 쓰는 Flow 만" 추가하며 자란다.
// 40여 개 StateFlow 를 한 번에 미러링하지 않는다(검증되지 않은 대량 코드 방지).
// 현재 커버: 인증·테마·상태메시지·프로필 (Phase 1~2).
// ════════════════════════════════════════════════════════════════════════════

@MainActor
@Observable
final class SpendingStore {

    /// 앱 전체가 공유하는 단일 스토어.
    ///
    /// ContentView 가 `@State` 로 들고 있는데, `@State` 의 초기값 표현식은 **뷰 struct 가 다시 만들어질
    /// 때마다** 평가된다(SwiftUI 가 기존 상태를 유지하더라도 새로 만든 값은 버려질 뿐 생성 자체는 일어난다).
    /// 이 스토어는 생성 시 Flow 구독 Task 를 77개 띄우므로 그때마다 만들었다 버리면 순수한 낭비다.
    /// 어차피 VM 이 싱글톤이라 스토어도 하나면 충분하다.
    static let shared = SpendingStore()

    /// 모든 화면이 공유하는 단일 Kotlin VM.
    @ObservationIgnored let vm = IosAppState.shared.viewModel

    // ── 미러링된 상태 ──────────────────────────────────────────────────────
    private(set) var account: Account
    private(set) var accentIndex: Int
    private(set) var profile: UserProfile
    private(set) var statusMessage: String?
    /// 네트워크 미연결 경고 — 얼럿 모달용(nil 이 아니면 표시). 토스트(statusMessage)와 분리.
    private(set) var networkAlert: String?
    private(set) var initialSyncing: Bool
    /// 로그아웃 진행 중 — 네트워크 대기 동안 오버레이를 띄운다(Android SignOutOverlay 대응).
    private(set) var signingOut: Bool = false

    /// 강제 업데이트 — 현재 버전이 최소 지원 버전 미만. true 면 앱 위에 닫히지 않는 업데이트 화면을 덮는다.
    private(set) var forceUpdate: Bool = false
    /// 업데이트 대상 버전명(강제 업데이트 화면 표시용).
    private(set) var updateVersionName: String = ""

    // ── Phase 2 (마이페이지/설정) ──
    private(set) var spendings: [Spending] = []
    private(set) var budget: Int64 = 0
    private(set) var gameBudgets: [String: Int64] = [:]
    private(set) var hoyolabConfig: HoyolabConfig
    private(set) var attendanceStreak: Int = 0
    private(set) var gachaStats: GachaStats?
    private(set) var autoCheckIn: Bool = false
    private(set) var notifyBudget: Bool = false
    private(set) var notifyAttendance: Bool = false
    private(set) var notifyResin: Bool = false
    private(set) var notifyPickup: Bool = false
    private(set) var nudgeOverspend: Bool = false
    private(set) var spendingCompact: Bool = false
    /// 홈 히어로 글로우 애니메이션 사용 여부 — 끄면 그라데이션만 남는다.
    private(set) var heroGlow: Bool = true
    private(set) var nudgeThreshold: Int64 = 0
    private(set) var pendingOpenHoyolabLink: Bool = false
    /// 홈 카드 → 게임 정보 탭 진입 시 스크롤할 섹션 앵커(1회성). nil 이면 없음.
    private(set) var pendingGameInfoAnchor: GameInfoAnchor? = nil
    /// 알림 딥링크가 요청한 탭·공지(각각 ContentView / GameInfoView 가 소비).
    private(set) var pendingTab: Int? = nil
    private(set) var pendingNewsId: String? = nil

    // ── Phase 3 (지출) ──
    private(set) var isRefreshing: Bool = false
    private(set) var subscriptions: [Shared.Subscription] = []
    private(set) var attendanceHistory: [String: Set<String>] = [:]
    private(set) var activeBanners: [GachaBanner] = []

    // ── Phase 4 (게임 정보) ──
    private(set) var liveNotes: [LiveNote] = []
    private(set) var gameEvents: [GameEvent] = []
    private(set) var challenges: [GameChallenge] = []
    private(set) var gameNews: [NewsItem] = []
    /// 공지에서 확인된 확정 방송. 비어 있으면 방송 탭이 역산 예상값을 쓴다.
    private(set) var confirmedBroadcasts: [ConfirmedBroadcast] = []
    /// 게임별 일일·주간 숙제 완주율(관측 기록 파생).
    private(set) var taskStats: [TaskStats] = []
    /// 캐릭터별 유효옵션 사용자 설정(키=keyStatOverrideKey).
    private(set) var keyStatOverrides: [String: Set<String>] = [:]
    /// 공지 본문(상세 페이지) — 실패해도 화면을 비우지 않고 NewsItem.summary 로 폴백한다.
    private(set) var newsArticle: NewsArticle? = nil
    private(set) var newsArticleLoading: Bool = false
    private(set) var newsArticleFailed: Bool = false
    private(set) var ledgers: [MonthlyLedger] = []
    private(set) var combat: [CombatMode] = []
    /// 엔드 콘텐츠 클리어 편성(층·간별로 어떤 캐릭터를 썼는지).
    private(set) var combatClears: [CombatClear] = []
    private(set) var combatClearsLoading: Bool = false
    private(set) var attendanceToday: Set<String> = []
    private(set) var checkingIn: String? = nil
    private(set) var pity: [String: PityState] = [:]
    private(set) var savingsPlans: [SavingsPlan] = []
    private(set) var hiddenSavingsPlans: [SavingsPlan] = []
    private(set) var challenge: ChallengeSummary? = nil
    // Phase 4 chunk ③ (가챠 도구)
    private(set) var enkaGiUid: String = ""
    private(set) var enkaHsrUid: String = ""
    private(set) var enkaResult: EnkaResult? = nil
    private(set) var enkaLoading: Bool = false
    // '내 캐릭터' 섹션(헤더 필터 연동) — 게임별 결과/로딩 동시 보관
    private(set) var enkaResults: [String: EnkaResult] = [:]
    private(set) var enkaLoadingGames: Set<String> = []
    // 도감(nanoka) — 신규 콘텐츠·방부
    private(set) var newContent: [NewContentGame] = []
    private(set) var gameVersions: [GameVersionLine] = []
    private(set) var versionBanner: NewVersionBanner? = nil
    private(set) var newContentUnseen: Bool = false
    private(set) var newContentLoading: Bool = false
    private(set) var weaponRefinement: [String: WeaponRefinement] = [:]
    private(set) var gachaDashboard: GachaDashboard? = nil
    private(set) var redeemState: RedeemState = RedeemStateIdle.shared
    private(set) var activeCodes: [GiftCode] = []
    private(set) var codesLoading: Bool = false
    /// 코드 수집 실패 — '활성 코드 없음'과 구분(재시도 UI 표시용).
    private(set) var codesFailed: Bool = false
    private(set) var redeemedCodes: Set<String> = []

    // Phase 5 (홈)
    private(set) var gameInfoReady: Bool = false
    /// 일정·소식 카드 각각의 표출 준비 상태 — 출처가 달라 `gameInfoReady` 와 따로 둔다(VM 주석 참고).
    private(set) var scheduleReady: Bool = false
    private(set) var newsReady: Bool = false
    private(set) var hoyoTokenExpired: Bool = false
    private(set) var readAlerts: Set<String> = []
    private(set) var dismissedAlerts: Set<String> = []

    // ── Phase 6 (27.33.0 알림 설정 — 정기결제 갱신·방해금지·데일리 요약) ──
    private(set) var notifySubscription: Bool = false
    private(set) var notifyNews: Bool = false
    private(set) var notifyCombat: Bool = true
    private(set) var notifyDndEnabled: Bool = false
    private(set) var notifyDndStartHour: Int = 23
    private(set) var notifyDndEndHour: Int = 8
    private(set) var notifyDailySummary: Bool = false
    private(set) var notifyDailySummaryHour: Int = 21

    // ── 파생값(Kotlin 에서 한 번 계산해 내려온다) ─────────────────────────────
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
    /// 아직 정기결제로 등록 안 된 '구독 표시' 지출 건수.
    private(set) var unlinkedSubCount: Int = 0
    /// 최근 6개월 총 지출(오래된 달 → 이번 달 순) — 마이페이지 월별 추이 차트.
    private(set) var recentMonthlyTotals: [Int64] = []

    @ObservationIgnored private var tasks: [Task<Void, Never>] = []

    init() {
        // 초기값(StateFlow 의 현재 값) — UI 첫 프레임이 즉시 올바른 상태로 그려지도록.
        let vm = IosAppState.shared.viewModel
        account = vm.account.value
        accentIndex = Int(vm.accentIndex.value.int32Value)
        profile = vm.profile.value
        statusMessage = vm.statusMessage.value
        initialSyncing = vm.initialSyncing.value.boolValue
        hoyolabConfig = vm.hoyolabConfig.value
        // 파생값도 초기값을 미리 넣는다 — bind 콜백은 한 런루프 뒤라, 안 넣으면 홈 히어로가 0원으로 한 번 그려진다.
        spendings = vm.spendings.value
        monthlyTotal = vm.currentMonthTotal.value.int64Value
        prevMonthTotal = vm.previousMonthTotal.value.int64Value
        monthlyTotalsByGame = vm.currentMonthTotalsByGame.value.mapValues { $0.int64Value }
        unlinkedSubCount = Int(vm.unlinkedSubCount.value.int32Value)
        recentMonthlyTotals = vm.recentMonthlyTotals.value.map { $0.int64Value }
        seedDeferredScalars()
        observe()
    }

    deinit { tasks.forEach { $0.cancel() } }

    // ── Flow 구독 ───────────────────────────────────────────────────────────
    //
    // 구독을 **첫 화면에 필요한 것 / 나중에 필요한 것**으로 나눈다.
    //
    // bind 한 건이 Swift Task 1개 + SKIE AsyncSequence 어댑터(Kotlin 쪽 collector 포함)를 만들고,
    // 그 Task 들이 전부 MainActor 큐에 쌓인다. 78개를 한꺼번에 걸면 첫 프레임을 그리려는 body 평가가
    // 그 뒤에 줄을 선다. 홈이 읽지 않는 도메인(가챠·선물코드·알림설정·저축 상세·공지 본문 등)은
    // 첫 프레임을 그린 뒤에 걸어도 화면이 달라지지 않는다.
    //
    // 다만 **토글류(Bool·숫자 설정)는 늦게 도착하면 '꺼짐'으로 한 번 그려져 오해를 부른다.**
    // 그래서 지연 대상 중 스칼라 값은 init 에서 현재 값을 동기로 한 번 읽어 채워 둔다([seedDeferred]).
    // 목록형은 비어 있는 상태가 자연스러우므로(스켈레톤·빈 목록) 그대로 둔다.
    private func observe() {
        observeCritical()
        // 첫 프레임이 그려진 뒤에 나머지를 건다.
        Task { @MainActor in
            await Task.yield()
            observeDeferred()
        }
    }

    /// 지연 구독 대상 중 **설정 토글·숫자**의 현재 값을 미리 채운다.
    ///
    /// 구독은 첫 프레임 뒤에 걸리는데, 그 전에 설정 화면을 열면 스위치가 전부 '꺼짐'으로 한 번 그려진다.
    /// 값 읽기(`.value`)는 Task 를 만들지 않아 구독보다 훨씬 싸므로, 오해를 부를 수 있는 스칼라만 시드한다.
    /// 목록형(공지·원장·캐릭터 등)은 비어 있는 상태가 자연스러워 시드하지 않는다.
    private func seedDeferredScalars() {
        autoCheckIn = vm.autoCheckIn.value.boolValue
        notifyBudget = vm.notifyBudget.value.boolValue
        notifyAttendance = vm.notifyAttendance.value.boolValue
        notifyResin = vm.notifyResin.value.boolValue
        notifyPickup = vm.notifyPickup.value.boolValue
        nudgeOverspend = vm.nudgeOverspend.value.boolValue
        spendingCompact = vm.spendingCompact.value.boolValue
        heroGlow = vm.heroGlow.value.boolValue
        nudgeThreshold = vm.nudgeThreshold.value.int64Value
        notifySubscription = vm.notifySubscription.value.boolValue
        notifyNews = vm.notifyNews.value.boolValue
        notifyCombat = vm.notifyCombat.value.boolValue
        notifyDndEnabled = vm.notifyDndEnabled.value.boolValue
        notifyDndStartHour = Int(vm.notifyDndStartHour.value.int32Value)
        notifyDndEndHour = Int(vm.notifyDndEndHour.value.int32Value)
        notifyDailySummary = vm.notifyDailySummary.value.boolValue
        notifyDailySummaryHour = Int(vm.notifyDailySummaryHour.value.int32Value)
    }

    /// 첫 화면(루트 판정 + 홈 탭)이 실제로 읽는 것들.
    private func observeCritical() {
        bind(vm.account) { [weak self] in self?.account = $0 }
        bind(vm.accentIndex) { [weak self] in self?.accentIndex = Int($0.int32Value) }
        bind(vm.profile) { [weak self] in self?.profile = $0 }
        bind(vm.statusMessage) { [weak self] in self?.statusMessage = $0 }
        bind(vm.networkAlert) { [weak self] in self?.networkAlert = $0 }
        bind(vm.initialSyncing) { [weak self] in self?.initialSyncing = $0.boolValue }
        bind(vm.signingOut) { [weak self] in self?.signingOut = $0.boolValue }
        bind(vm.forceUpdate) { [weak self] in self?.forceUpdate = $0.boolValue }
        bind(vm.updateInfo) { [weak self] in self?.updateVersionName = $0?.versionName ?? "" }

        // 파생값
        bind(vm.currentMonthTotal) { [weak self] in self?.monthlyTotal = $0.int64Value }
        bind(vm.previousMonthTotal) { [weak self] in self?.prevMonthTotal = $0.int64Value }
        bind(vm.currentMonthTotalsByGame) { [weak self] in self?.monthlyTotalsByGame = $0.mapValues { $0.int64Value } }

        // Phase 2
        bind(vm.spendings) { [weak self] in self?.spendings = $0 }
        bind(vm.budget) { [weak self] in self?.budget = $0.int64Value }
        bind(vm.gameBudgets) { [weak self] in self?.gameBudgets = $0.mapValues { $0.int64Value } }
        bind(vm.hoyolabConfig) { [weak self] in self?.hoyolabConfig = $0 }
        bind(vm.attendanceStreak) { [weak self] in self?.attendanceStreak = Int($0.int32Value) }

        // 홈 카드·오늘 할 일·딥링크
        bind(vm.isRefreshing) { [weak self] in self?.isRefreshing = $0.boolValue }
        bind(vm.activeBanners) { [weak self] in self?.activeBanners = $0 }
        bind(vm.liveNotes) { [weak self] in self?.liveNotes = $0 }
        bind(vm.gameEvents) { [weak self] in self?.gameEvents = $0 }
        bind(vm.challenges) { [weak self] in self?.challenges = $0 }
        bind(vm.gameNews) { [weak self] in self?.gameNews = $0 }
        bind(vm.confirmedBroadcasts) { [weak self] in self?.confirmedBroadcasts = $0 }
        bind(vm.combat) { [weak self] in self?.combat = $0 }
        bind(vm.combatClears) { [weak self] in self?.combatClears = $0 }
        bind(vm.combatClearsLoading) { [weak self] in self?.combatClearsLoading = $0.boolValue }
        bind(vm.attendanceToday) { [weak self] in self?.attendanceToday = $0 }
        bind(vm.checkingIn) { [weak self] in self?.checkingIn = $0 }
        bind(vm.gameInfoReady) { [weak self] in self?.gameInfoReady = $0.boolValue }
        bind(vm.scheduleReady) { [weak self] in self?.scheduleReady = $0.boolValue }
        bind(vm.newsReady) { [weak self] in self?.newsReady = $0.boolValue }
        bind(vm.hoyoTokenExpired) { [weak self] in self?.hoyoTokenExpired = $0.boolValue }
        bind(vm.readAlerts) { [weak self] in self?.readAlerts = $0 }
        bind(vm.dismissedAlerts) { [weak self] in self?.dismissedAlerts = $0 }
        bind(vm.savingsPlans) { [weak self] in self?.savingsPlans = $0 }
        bind(vm.challenge) { [weak self] in self?.challenge = $0 }
        bind(vm.pendingTab) { [weak self] in self?.pendingTab = $0?.intValue }
        bind(vm.pendingNewsId) { [weak self] in self?.pendingNewsId = $0 }
        bind(vm.pendingGameInfoAnchor) { [weak self] in self?.pendingGameInfoAnchor = $0 }
    }

    /// 첫 프레임 이후에 걸어도 되는 것들 — 해당 화면에 들어가기 전에는 읽히지 않는다.
    private func observeDeferred() {
        bind(vm.gachaStats) { [weak self] in self?.gachaStats = $0 }
        bind(vm.recentMonthlyTotals) { [weak self] in self?.recentMonthlyTotals = $0.map { $0.int64Value } }
        bind(vm.unlinkedSubCount) { [weak self] in self?.unlinkedSubCount = Int($0.int32Value) }
        bind(vm.autoCheckIn) { [weak self] in self?.autoCheckIn = $0.boolValue }
        bind(vm.notifyBudget) { [weak self] in self?.notifyBudget = $0.boolValue }
        bind(vm.notifyAttendance) { [weak self] in self?.notifyAttendance = $0.boolValue }
        bind(vm.notifyResin) { [weak self] in self?.notifyResin = $0.boolValue }
        bind(vm.notifyPickup) { [weak self] in self?.notifyPickup = $0.boolValue }
        bind(vm.nudgeOverspend) { [weak self] in self?.nudgeOverspend = $0.boolValue }
        bind(vm.spendingCompact) { [weak self] in self?.spendingCompact = $0.boolValue }
        bind(vm.heroGlow) { [weak self] in self?.heroGlow = $0.boolValue }
        bind(vm.nudgeThreshold) { [weak self] in self?.nudgeThreshold = $0.int64Value }
        bind(vm.pendingOpenHoyolabLink) { [weak self] in self?.pendingOpenHoyolabLink = $0.boolValue }
        bind(vm.taskStats) { [weak self] in self?.taskStats = $0 }
        bind(vm.keyStatOverrides) { [weak self] map in
            self?.keyStatOverrides = map.reduce(into: [:]) { acc, kv in acc[kv.key] = Set(kv.value) }
        }

        // Phase 3
        bind(vm.subscriptions) { [weak self] in self?.subscriptions = $0 }
        bind(vm.attendanceHistory) { [weak self] in self?.attendanceHistory = $0 }

        // Phase 4
        bind(vm.newsArticle) { [weak self] in self?.newsArticle = $0 }
        bind(vm.newsArticleLoading) { [weak self] in self?.newsArticleLoading = $0.boolValue }
        bind(vm.newsArticleFailed) { [weak self] in self?.newsArticleFailed = $0.boolValue }
        bind(vm.ledgers) { [weak self] in self?.ledgers = $0 }
        bind(vm.pity) { [weak self] in self?.pity = $0 }
        // 저축 플래너 · 절약 챌린지 (27.35)
        bind(vm.hiddenSavingsPlans) { [weak self] in self?.hiddenSavingsPlans = $0 }
        // chunk ③
        bind(vm.enkaGiUid) { [weak self] in self?.enkaGiUid = $0 }
        bind(vm.enkaHsrUid) { [weak self] in self?.enkaHsrUid = $0 }
        bind(vm.enkaResult) { [weak self] in self?.enkaResult = $0 }
        bind(vm.enkaLoading) { [weak self] in self?.enkaLoading = $0.boolValue }
        bind(vm.newContent) { [weak self] in self?.newContent = $0 }
        bind(vm.gameVersions) { [weak self] in self?.gameVersions = $0 }
        bind(vm.versionBanner) { [weak self] in self?.versionBanner = $0 }
        bind(vm.newContentUnseen) { [weak self] in self?.newContentUnseen = $0.boolValue }
        bind(vm.newContentLoading) { [weak self] in self?.newContentLoading = $0.boolValue }
        bind(vm.weaponRefinement) { [weak self] in self?.weaponRefinement = $0 }
        bind(vm.enkaResults) { [weak self] in self?.enkaResults = $0 }
        bind(vm.enkaLoadingGames) { [weak self] in self?.enkaLoadingGames = $0 }
        bind(vm.gachaDashboard) { [weak self] in self?.gachaDashboard = $0 }
        bind(vm.redeemState) { [weak self] in self?.redeemState = $0 }
        bind(vm.activeCodes) { [weak self] in self?.activeCodes = $0 }
        bind(vm.codesLoading) { [weak self] in self?.codesLoading = $0.boolValue }
        bind(vm.codesFailed) { [weak self] in self?.codesFailed = $0.boolValue }
        bind(vm.redeemedCodes) { [weak self] in self?.redeemedCodes = $0 }
        // Phase 5
        // Phase 6 (알림 설정)
        bind(vm.notifySubscription) { [weak self] in self?.notifySubscription = $0.boolValue }
        bind(vm.notifyNews) { [weak self] in self?.notifyNews = $0.boolValue }
        bind(vm.notifyCombat) { [weak self] in self?.notifyCombat = $0.boolValue }
        bind(vm.notifyDndEnabled) { [weak self] in self?.notifyDndEnabled = $0.boolValue }
        bind(vm.notifyDndStartHour) { [weak self] in self?.notifyDndStartHour = Int($0.int32Value) }
        bind(vm.notifyDndEndHour) { [weak self] in self?.notifyDndEndHour = Int($0.int32Value) }
        bind(vm.notifyDailySummary) { [weak self] in self?.notifyDailySummary = $0.boolValue }
        bind(vm.notifyDailySummaryHour) { [weak self] in self?.notifyDailySummaryHour = Int($0.int32Value) }
    }

    /// StateFlow(SKIE AsyncSequence)를 구독해 메인 액터에서 [apply] 로 반영한다.
    private func bind<T>(_ flow: SkieSwiftStateFlow<T>, _ apply: @escaping (T) -> Void) {
        tasks.append(Task { @MainActor in
            for await value in flow { apply(value) }
        })
    }

    /// nullable StateFlow(StateFlow<T?>) 전용 — SKIE 는 SkieSwiftOptionalStateFlow 로 노출한다.
    private func bind<T>(_ flow: SkieSwiftOptionalStateFlow<T>, _ apply: @escaping (T?) -> Void) {
        tasks.append(Task { @MainActor in
            for await value in flow { apply(value) }
        })
    }

    // ── 액션 패스스루 ────────────────────────────────────────────────────────
    /// 구글 로그인(원탭).
    func signIn() { vm.signIn() }
    /// 로그아웃.
    func signOut() { vm.signOut() }
    /// 강조색 변경. 낙관적 즉시 반영 — SKIE StateFlow bind 콜백이 한 런루프 지연될 수 있어,
    /// 미러 프로퍼티를 곧바로 갱신해 설정 화면 체크마크·전역 accent 가 탭 즉시 바뀌게 한다(설정 이탈 없이).
    func setAccentIndex(_ index: Int) {
        accentIndex = index
        vm.setAccentIndex(index: Int32(index))
    }
    /// 프로필 이름 변경.
    func setProfileName(_ name: String) { vm.setProfileName(name: name) }
    /// 상태 토스트 소비.
    func clearStatus() { vm.clearStatus() }
    /// 네트워크 미연결 얼럿 소비.
    func clearNetworkAlert() { vm.clearNetworkAlert() }

    // ── Phase 2 액션 ──────────────────────────────────────────────────────
    /// 전체 예산 + 게임별 한도 저장.
    func setBudgets(overall: Int64, perGame: [String: Int64]) {
        vm.setBudgets(overall: overall, perGame: perGame.mapValues { KotlinLong(value: $0) })
    }
    func setNudgeOverspend(_ v: Bool) { vm.setNudgeOverspend(v: v) }
    func setSpendingCompact(_ v: Bool) { vm.setSpendingCompact(v: v) }
    func setHeroGlow(_ v: Bool) { vm.setHeroGlow(v: v) }
    func setNudgeThreshold(_ v: Int64) { vm.setNudgeThreshold(v: v) }
    /// 공지 상세 진입 — 본문 로드. 이탈 시 clearNewsArticle() 로 정리한다.
    func loadNewsArticle(_ item: NewsItem) { vm.loadNewsArticle(item: item) }
    func clearNewsArticle() { vm.clearNewsArticle() }

    func setNotifyBudget(_ v: Bool) { vm.setNotifyBudget(v: v) }
    func setNotifyAttendance(_ v: Bool) { vm.setNotifyAttendance(v: v) }
    func setNotifyResin(_ v: Bool) { vm.setNotifyResin(v: v) }
    func setNotifyPickup(_ v: Bool) { vm.setNotifyPickup(v: v) }
    // Phase 6 (27.33.0) — 정기결제 갱신·방해금지·데일리 요약
    func setNotifySubscription(_ v: Bool) { vm.setNotifySubscription(v: v) }
    func setNotifyNews(_ v: Bool) { vm.setNotifyNews(v: v) }
    func setNotifyCombat(_ v: Bool) { vm.setNotifyCombat(v: v) }
    func setNotifyDndEnabled(_ v: Bool) { vm.setNotifyDndEnabled(v: v) }
    func setNotifyDndStartHour(_ v: Int) { vm.setNotifyDndStartHour(v: Int32(v)) }
    func setNotifyDndEndHour(_ v: Int) { vm.setNotifyDndEndHour(v: Int32(v)) }
    func setNotifyDailySummary(_ v: Bool) { vm.setNotifyDailySummary(v: v) }
    func setNotifyDailySummaryHour(_ v: Int) { vm.setNotifyDailySummaryHour(v: Int32(v)) }
    func deleteSpendings(_ ids: Set<String>) { vm.deleteSpendings(ids: ids) }
    /// 선택 지출 일괄 변경(게임/날짜/추가 태그). nil·빈값은 미변경.
    func bulkEditSpendings(ids: Set<String>, gameName: String?, dateMillis: Int64?, addTags: [String]) {
        vm.bulkEditSpendings(ids: ids, gameName: gameName, dateMillis: dateMillis.map { KotlinLong(value: $0) }, addTags: addTags)
    }
    func setAutoCheckIn(_ enabled: Bool) { vm.setAutoCheckIn(enabled: enabled) }
    func clearGachaRecords() { vm.clearGachaRecords() }
    func clearSpendings() { vm.clearSpendings() }
    func checkForUpdate(manual: Bool = true) { vm.checkForUpdate(manual: manual) }
    /// 강제 업데이트 화면의 '지금 업데이트' — iOS 는 릴리스 페이지를 연다(사이드로딩).
    func startInAppUpdate() { vm.startInAppUpdate() }
    func updateHoyolabConfig(_ config: HoyolabConfig) { vm.updateHoyolabConfig(config: config) }
    func consumePendingOpenHoyolabLink() { vm.consumePendingOpenHoyolabLink() }
    /// 홈 카드 → 게임 정보 탭 스크롤 앵커 요청/소비.
    func requestGameInfoAnchor(_ anchor: GameInfoAnchor) { vm.requestGameInfoAnchor(anchor: anchor) }
    func consumeGameInfoAnchor() { vm.consumeGameInfoAnchor() }

    /// 백업 스냅샷 JSON (없으면 nil).
    func exportBackupContent() -> String? { vm.exportBackupContent() }
    /// 백업 JSON 복원.
    func importBackupFromContent(_ json: String) { vm.importBackupFromContent(json: json) }
    /// 지출 CSV 문자열.
    func buildCsv() -> String { vm.buildCsv() }
    /// 지정 연/월 총 지출 (마이페이지 월별 추이 차트용 — 과거 달이라 값이 안 바뀌므로 그때그때 계산).
    func monthlyTotal(year: Int32, month: Int32) -> Int64 { vm.monthlyTotal(year: year, month: month) }

    // ── Phase 3 액션 ──────────────────────────────────────────────────────
    func deleteSpending(_ id: String) { vm.deleteSpending(id: id) }
    func refreshSpending() { vm.refreshSpending() }
    func addSubscription(_ sub: Shared.Subscription) { vm.addSubscription(sub: sub) }
    func updateSubscription(_ sub: Shared.Subscription) { vm.updateSubscription(sub: sub) }
    func deleteSubscription(_ id: String) { vm.deleteSubscription(id: id) }
    /// '구독 표시' 지출을 정기결제로 일괄 등록(중복 제외). subscriptions 는 VM StateFlow bind 로 자동 갱신.
    func importSubscriptionsFromSpendings() { _ = vm.importSubscriptionsFromSpendings() }
    /// 지출 수정 진입 — 편집 대상 설정(모달 열기는 ContentView 가 담당).
    func prepareEdit(_ spending: Spending) { MainViewControllerKt.prepareEditSpending(spending: spending) }
    /// 지출 추가/수정 저장 (Spending 생성은 Kotlin 헬퍼).
    func saveSpending(editingId: String?, gameName: String, amount: Int64, dateMillis: Int64,
                      paymentMethod: String, chargePlatform: String, itemName: String, memo: String, tags: [String], isSubscription: Bool) {
        MainViewControllerKt.saveSpending(editingId: editingId, gameName: gameName, amount: amount, dateMillis: dateMillis,
                                          paymentMethod: paymentMethod, chargePlatform: chargePlatform, itemName: itemName, memo: memo, tags: tags, isSubscription: isSubscription)
    }
    /// N6 과소비 넛지 판정 — 경고 메시지 또는 nil.
    func overspendNudge(game: Game, amount: Int64, editingId: String?) -> String? {
        vm.overspendNudge(game: game, amount: amount, editingId: editingId)
    }
    var displayYear: Int { Int(vm.displayYear) }
    var displayMonth: Int { Int(vm.displayMonth) }

    // ── Phase 4 액션 ──────────────────────────────────────────────────────
    // Kotlin 기본 인자는 Swift 로 안 넘어온다 — silent 를 명시해야 한다(사용자가 부른 새로고침이므로 false).
    func refreshGameInfo(force: Bool = false) { vm.refreshGameInfo(force: force, silent: false) }
    /// 앱 복귀 — 오래 떠나 있었으면 데이터 갱신 + 밀린 알림 1회 점검(BGTask 가 OS 재량이라 그것만으론 구멍이 크다).
    func onAppForeground() { vm.onAppForeground() }
    /// 앱이 백그라운드로 내려간 시각 기록 — 복귀 시 '얼마나 떠나 있었는지' 판정에 쓴다.
    func onAppBackground() { vm.onAppBackground() }
    /// 클리어 편성 조회 — 전용 페이지 진입 시에만(시즌 2개치라 무겁다).
    func refreshCombatClears(force: Bool = false) { vm.refreshCombatClears(force: force) }
    /// 알림 payload 의 딥링크 처리("news:<공지 id>") — 탭 전환 + 상세 진입 상태를 세운다.
    func handleNotificationLink(_ link: String) { vm.handleNotificationLink(link: link) }
    func consumePendingTab() { vm.consumePendingTab() }
    func consumePendingNews() { vm.consumePendingNews() }
    /// 유효옵션 직접 설정 저장(빈 집합이면 해제 → 앱 룰 추정으로 되돌아간다).
    func setKeyStatOverride(_ key: String, _ stats: Set<String>) { vm.setKeyStatOverride(key: key, stats: stats) }
    func attemptCheckIn(_ gameKey: String) { vm.attemptCheckIn(gameKey: gameKey) }
    func checkInAll() { vm.checkInAll() }
    func adjustPity(gameKey: String, delta: Int) { vm.adjustPity(gameKey: gameKey, delta: Int32(delta)) }
    func setPityCount(gameKey: String, value: Int) { vm.setPityCount(gameKey: gameKey, value: Int32(value)) }
    func resetPity(gameKey: String) { vm.resetPity(gameKey: gameKey) }
    func setPityGuaranteed(gameKey: String, _ g: Bool) { vm.setPityGuaranteed(gameKey: gameKey, g: g) }
    func setHeldCurrency(gameKey: String, value: Int) { vm.setHeldCurrency(gameKey: gameKey, value: Int32(value)) }
    func setSavingsHidden(key: String, hidden: Bool) { vm.setSavingsHidden(key: key, hidden: hidden) }
    // chunk ③
    func loadEnkaProfile(game: String, uid: String) { vm.loadEnkaProfile(game: game, uid: uid) }
    func autoLoadEnka(game: String, force: Bool = false) { vm.autoLoadEnka(game: game, force: force) }
    func autoLoadEnkaSection(games: [String], force: Bool = false) { vm.autoLoadEnkaSection(games: games, force: force) }
    func loadNewContent() { vm.loadNewContent(force: false) }
    func markNewContentSeen() { vm.markNewContentSeen() }
    func loadWeaponRefinement(game: String, weaponId: Int32, level: Int32) {
        vm.loadWeaponRefinement(gameKey: game, weaponId: weaponId, level: level)
    }
    func clearEnkaResult() { vm.clearEnkaResult() }
    func importGachaFromContents(_ contents: [String]) { vm.importGachaFromContents(contents: contents) }
    func loadActiveCodes(_ gameKey: String) { vm.loadActiveCodes(gameKey: gameKey) }
    func redeemGiftCode(gameKey: String, code: String) { vm.redeemGiftCode(gameKey: gameKey, code: code) }
    func redeemAllCodes(_ gameKey: String) { vm.redeemAllCodes(gameKey: gameKey) }
    func resetRedeem() { vm.resetRedeem() }
    // Phase 5
    func markAlertsRead(_ keys: [String]) { vm.markAlertsRead(keys: keys) }
    func dismissAlert(_ key: String) { vm.dismissAlert(key: key) }
    func dismissAlerts(_ keys: [String]) { vm.dismissAlerts(keys: keys) }
    func requestOpenHoyolabLink() { vm.requestOpenHoyolabLink() }
    func refreshHoyoTokenExpired() { vm.refreshHoyoTokenExpired() }
    func showStatus(_ msg: String) { vm.showStatus(msg: msg) }
    /// 초기 동기화 로딩 게이트 완료 표시 (AccountLoadingView 완료 시).
    func markSyncLoadingDone() { MainViewControllerKt.markSyncLoadingDone() }
    func maxPullsToSecure(count: Int, guaranteed: Bool, banner: GachaBannerRate) -> Int {
        Int(GachaRateData.shared.maxPullsToSecure(count: Int32(count), guaranteed: guaranteed, b: banner))
    }
    /// 가챠 단가 계산용 게임별(비구독) 지출 — 키: genshin/starrail/zzz (GachaStats.byGame 키와 일치).
    func gachaSpendByGame() -> [String: Int64] {
        var m: [String: Int64] = [:]
        for s in spendings where !s.isSubscription {
            let key: String? = s.gameName == "원신" ? "genshin"
                : (s.gameName == "붕괴: 스타레일" ? "starrail" : (s.gameName == "젠레스 존 제로" ? "zzz" : nil))
            if let k = key { m[k, default: 0] += s.amount }
        }
        return m
    }

    // ── 편의 ────────────────────────────────────────────────────────────────
    /// 로그인 필요 여부 — 미로그인(게스트 모드 없음, 구글 로그인 필수).
    /// (v27.38.0 에서 needsOnboarding → needsLogin 개명 — 첫 실행 온보딩이 별도로 생겨 이름이 헷갈렸다)
    var needsLogin: Bool { account.isGuest }
    /// 현재 강조색.
    var accent: GLGAccent { GLGTheme.accent(accentIndex) }
}
