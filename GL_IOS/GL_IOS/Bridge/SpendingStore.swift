import Foundation
import Combine
import Shared

// ════════════════════════════════════════════════════════════════════════════
// Kotlin SpendingViewModel ↔ SwiftUI 브리지
//
// SwiftUI 는 Kotlin StateFlow 를 직접 관찰하지 못한다. SKIE 가 StateFlow 를
// Swift AsyncSequence(SkieSwiftStateFlow)로 변환해 주므로, 각 Flow 를 for-await 로
// 구독해 @Published 프로퍼티에 미러링한다 → SwiftUI 가 변경을 자동 반영.
//
// VM 인스턴스는 IosAppState.shared.viewModel 싱글톤을 공유한다 — 모든 SwiftUI 화면이 단일 상태를 본다.
//
// ⚠️ 확장 정책: 이 스토어는 Phase 마다 "그 화면이 실제로 쓰는 Flow 만" 추가하며 자란다.
// 40여 개 StateFlow 를 한 번에 미러링하지 않는다(검증되지 않은 대량 코드 방지).
// 현재 커버: 인증·테마·상태메시지·프로필 (Phase 1~2).
// ════════════════════════════════════════════════════════════════════════════

@MainActor
final class SpendingStore: ObservableObject {

    /// 모든 화면이 공유하는 단일 Kotlin VM.
    let vm = IosAppState.shared.viewModel

    // ── 미러링된 상태 ──────────────────────────────────────────────────────
    @Published private(set) var account: Account
    @Published private(set) var accentIndex: Int
    @Published private(set) var profile: UserProfile
    @Published private(set) var statusMessage: String?
    /// 네트워크 미연결 경고 — 얼럿 모달용(nil 이 아니면 표시). 토스트(statusMessage)와 분리.
    @Published private(set) var networkAlert: String?
    @Published private(set) var initialSyncing: Bool

    // ── Phase 2 (마이페이지/설정) ──
    @Published private(set) var spendings: [Spending] = []
    @Published private(set) var budget: Int64 = 0
    @Published private(set) var gameBudgets: [String: Int64] = [:]
    @Published private(set) var hoyolabConfig: HoyolabConfig
    @Published private(set) var attendanceStreak: Int = 0
    @Published private(set) var gachaStats: GachaStats?
    @Published private(set) var autoCheckIn: Bool = false
    @Published private(set) var notifyBudget: Bool = false
    @Published private(set) var notifyAttendance: Bool = false
    @Published private(set) var notifyResin: Bool = false
    @Published private(set) var notifyPickup: Bool = false
    @Published private(set) var nudgeOverspend: Bool = false
    @Published private(set) var spendingCompact: Bool = false
    @Published private(set) var nudgeThreshold: Int64 = 0
    @Published private(set) var pendingOpenHoyolabLink: Bool = false
    /// 홈 카드 → 게임 정보 탭 진입 시 스크롤할 섹션 앵커(1회성). nil 이면 없음.
    @Published private(set) var pendingGameInfoAnchor: GameInfoAnchor? = nil

    // ── Phase 3 (지출) ──
    @Published private(set) var isRefreshing: Bool = false
    @Published private(set) var subscriptions: [Shared.Subscription] = []
    @Published private(set) var attendanceHistory: [String: Set<String>] = [:]
    @Published private(set) var activeBanners: [GachaBanner] = []

    // ── Phase 4 (게임 정보) ──
    @Published private(set) var liveNotes: [LiveNote] = []
    @Published private(set) var gameEvents: [GameEvent] = []
    @Published private(set) var challenges: [GameChallenge] = []
    @Published private(set) var gameNews: [NewsItem] = []
    @Published private(set) var ledgers: [MonthlyLedger] = []
    @Published private(set) var combat: [CombatMode] = []
    @Published private(set) var attendanceToday: Set<String> = []
    @Published private(set) var checkingIn: String? = nil
    @Published private(set) var pity: [String: PityState] = [:]
    @Published private(set) var savingsPlans: [SavingsPlan] = []
    @Published private(set) var challenge: ChallengeSummary? = nil
    // Phase 4 chunk ③ (가챠 도구)
    @Published private(set) var enkaGiUid: String = ""
    @Published private(set) var enkaHsrUid: String = ""
    @Published private(set) var enkaResult: EnkaResult? = nil
    @Published private(set) var enkaLoading: Bool = false
    // '내 캐릭터' 섹션(헤더 필터 연동) — 게임별 결과/로딩 동시 보관
    @Published private(set) var enkaResults: [String: EnkaResult] = [:]
    @Published private(set) var enkaLoadingGames: Set<String> = []
    @Published private(set) var gachaDashboard: GachaDashboard? = nil
    @Published private(set) var redeemState: RedeemState = RedeemStateIdle.shared
    @Published private(set) var activeCodes: [GiftCode] = []
    @Published private(set) var codesLoading: Bool = false
    @Published private(set) var redeemedCodes: Set<String> = []

    // Phase 5 (홈)
    @Published private(set) var homeCards: [HomeCardItem] = []
    @Published private(set) var gameInfoReady: Bool = false
    @Published private(set) var hoyoTokenExpired: Bool = false
    @Published private(set) var readAlerts: Set<String> = []
    @Published private(set) var dismissedAlerts: Set<String> = []

    // ── Phase 6 (27.33.0 알림 설정 — 정기결제 갱신·방해금지·데일리 요약) ──
    @Published private(set) var notifySubscription: Bool = false
    @Published private(set) var notifyDndEnabled: Bool = false
    @Published private(set) var notifyDndStartHour: Int = 23
    @Published private(set) var notifyDndEndHour: Int = 8
    @Published private(set) var notifyDailySummary: Bool = false
    @Published private(set) var notifyDailySummaryHour: Int = 21

    private var tasks: [Task<Void, Never>] = []

    init() {
        // 초기값(StateFlow 의 현재 값) — UI 첫 프레임이 즉시 올바른 상태로 그려지도록.
        let vm = IosAppState.shared.viewModel
        account = vm.account.value
        accentIndex = Int(vm.accentIndex.value.int32Value)
        profile = vm.profile.value
        statusMessage = vm.statusMessage.value
        initialSyncing = vm.initialSyncing.value.boolValue
        hoyolabConfig = vm.hoyolabConfig.value
        observe()
    }

    deinit { tasks.forEach { $0.cancel() } }

    // ── Flow 구독 ───────────────────────────────────────────────────────────
    private func observe() {
        bind(vm.account) { [weak self] in self?.account = $0 }
        bind(vm.accentIndex) { [weak self] in self?.accentIndex = Int($0.int32Value) }
        bind(vm.profile) { [weak self] in self?.profile = $0 }
        bind(vm.statusMessage) { [weak self] in self?.statusMessage = $0 }
        bind(vm.networkAlert) { [weak self] in self?.networkAlert = $0 }
        bind(vm.initialSyncing) { [weak self] in self?.initialSyncing = $0.boolValue }

        // Phase 2
        bind(vm.spendings) { [weak self] in self?.spendings = $0 }
        bind(vm.budget) { [weak self] in self?.budget = $0.int64Value }
        bind(vm.gameBudgets) { [weak self] in self?.gameBudgets = $0.mapValues { $0.int64Value } }
        bind(vm.hoyolabConfig) { [weak self] in self?.hoyolabConfig = $0 }
        bind(vm.attendanceStreak) { [weak self] in self?.attendanceStreak = Int($0.int32Value) }
        bind(vm.gachaStats) { [weak self] in self?.gachaStats = $0 }
        bind(vm.autoCheckIn) { [weak self] in self?.autoCheckIn = $0.boolValue }
        bind(vm.notifyBudget) { [weak self] in self?.notifyBudget = $0.boolValue }
        bind(vm.notifyAttendance) { [weak self] in self?.notifyAttendance = $0.boolValue }
        bind(vm.notifyResin) { [weak self] in self?.notifyResin = $0.boolValue }
        bind(vm.notifyPickup) { [weak self] in self?.notifyPickup = $0.boolValue }
        bind(vm.nudgeOverspend) { [weak self] in self?.nudgeOverspend = $0.boolValue }
        bind(vm.spendingCompact) { [weak self] in self?.spendingCompact = $0.boolValue }
        bind(vm.nudgeThreshold) { [weak self] in self?.nudgeThreshold = $0.int64Value }
        bind(vm.pendingOpenHoyolabLink) { [weak self] in self?.pendingOpenHoyolabLink = $0.boolValue }
        bind(vm.pendingGameInfoAnchor) { [weak self] in self?.pendingGameInfoAnchor = $0 }

        // Phase 3
        bind(vm.isRefreshing) { [weak self] in self?.isRefreshing = $0.boolValue }
        bind(vm.subscriptions) { [weak self] in self?.subscriptions = $0 }
        bind(vm.attendanceHistory) { [weak self] in self?.attendanceHistory = $0 }
        bind(vm.activeBanners) { [weak self] in self?.activeBanners = $0 }

        // Phase 4
        bind(vm.liveNotes) { [weak self] in self?.liveNotes = $0 }
        bind(vm.gameEvents) { [weak self] in self?.gameEvents = $0 }
        bind(vm.challenges) { [weak self] in self?.challenges = $0 }
        bind(vm.gameNews) { [weak self] in self?.gameNews = $0 }
        bind(vm.ledgers) { [weak self] in self?.ledgers = $0 }
        bind(vm.combat) { [weak self] in self?.combat = $0 }
        bind(vm.attendanceToday) { [weak self] in self?.attendanceToday = $0 }
        bind(vm.checkingIn) { [weak self] in self?.checkingIn = $0 }
        bind(vm.pity) { [weak self] in self?.pity = $0 }
        // 저축 플래너 · 절약 챌린지 (27.35)
        bind(vm.savingsPlans) { [weak self] in self?.savingsPlans = $0 }
        bind(vm.challenge) { [weak self] in self?.challenge = $0 }
        // chunk ③
        bind(vm.enkaGiUid) { [weak self] in self?.enkaGiUid = $0 }
        bind(vm.enkaHsrUid) { [weak self] in self?.enkaHsrUid = $0 }
        bind(vm.enkaResult) { [weak self] in self?.enkaResult = $0 }
        bind(vm.enkaLoading) { [weak self] in self?.enkaLoading = $0.boolValue }
        bind(vm.enkaResults) { [weak self] in self?.enkaResults = $0 }
        bind(vm.enkaLoadingGames) { [weak self] in self?.enkaLoadingGames = $0 }
        bind(vm.gachaDashboard) { [weak self] in self?.gachaDashboard = $0 }
        bind(vm.redeemState) { [weak self] in self?.redeemState = $0 }
        bind(vm.activeCodes) { [weak self] in self?.activeCodes = $0 }
        bind(vm.codesLoading) { [weak self] in self?.codesLoading = $0.boolValue }
        bind(vm.redeemedCodes) { [weak self] in self?.redeemedCodes = $0 }
        // Phase 5
        bind(vm.homeCards) { [weak self] in self?.homeCards = $0 }
        bind(vm.gameInfoReady) { [weak self] in self?.gameInfoReady = $0.boolValue }
        bind(vm.hoyoTokenExpired) { [weak self] in self?.hoyoTokenExpired = $0.boolValue }
        bind(vm.readAlerts) { [weak self] in self?.readAlerts = $0 }
        bind(vm.dismissedAlerts) { [weak self] in self?.dismissedAlerts = $0 }
        // Phase 6 (알림 설정)
        bind(vm.notifySubscription) { [weak self] in self?.notifySubscription = $0.boolValue }
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
    /// 강조색 변경.
    func setAccentIndex(_ index: Int) { vm.setAccentIndex(index: Int32(index)) }
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
    func setNudgeThreshold(_ v: Int64) { vm.setNudgeThreshold(v: v) }
    func setNotifyBudget(_ v: Bool) { vm.setNotifyBudget(v: v) }
    func setNotifyAttendance(_ v: Bool) { vm.setNotifyAttendance(v: v) }
    func setNotifyResin(_ v: Bool) { vm.setNotifyResin(v: v) }
    func setNotifyPickup(_ v: Bool) { vm.setNotifyPickup(v: v) }
    // Phase 6 (27.33.0) — 정기결제 갱신·방해금지·데일리 요약
    func setNotifySubscription(_ v: Bool) { vm.setNotifySubscription(v: v) }
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
    /// 이번 달 총 지출. (기본 인자가 인스턴스 상태 기반이라 SKIE 미보존 → 현재 연/월 명시)
    func monthlyTotal() -> Int64 { vm.monthlyTotal(year: vm.displayYear, month: vm.displayMonth) }
    /// 지정 연/월 총 지출 (마이페이지 월별 추이 차트용).
    func monthlyTotal(year: Int32, month: Int32) -> Int64 { vm.monthlyTotal(year: year, month: month) }
    /// 이번 달 게임별 지출 합계.
    func monthlyTotalsByGame() -> [String: Int64] {
        vm.monthlyTotalsByGame(year: vm.displayYear, month: vm.displayMonth).mapValues { $0.int64Value }
    }

    // ── Phase 3 액션 ──────────────────────────────────────────────────────
    func deleteSpending(_ id: String) { vm.deleteSpending(id: id) }
    func refreshSpending() { vm.refreshSpending() }
    func addSubscription(_ sub: Shared.Subscription) { vm.addSubscription(sub: sub) }
    func updateSubscription(_ sub: Shared.Subscription) { vm.updateSubscription(sub: sub) }
    func deleteSubscription(_ id: String) { vm.deleteSubscription(id: id) }
    /// 아직 정기결제로 등록 안 된 '구독 표시' 지출 건수.
    var unlinkedSubCount: Int { Int(vm.unlinkedSubscriptionSpendingCount()) }
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
    /// 전월 총 지출(MoM 비교용).
    func prevMonthTotal() -> Int64 { vm.prevMonthTotal() }

    // ── Phase 4 액션 ──────────────────────────────────────────────────────
    func refreshGameInfo(force: Bool = false) { vm.refreshGameInfo(force: force) }
    func attemptCheckIn(_ gameKey: String) { vm.attemptCheckIn(gameKey: gameKey) }
    func checkInAll() { vm.checkInAll() }
    func adjustPity(gameKey: String, delta: Int) { vm.adjustPity(gameKey: gameKey, delta: Int32(delta)) }
    func setPityCount(gameKey: String, value: Int) { vm.setPityCount(gameKey: gameKey, value: Int32(value)) }
    func resetPity(gameKey: String) { vm.resetPity(gameKey: gameKey) }
    func setPityGuaranteed(gameKey: String, _ g: Bool) { vm.setPityGuaranteed(gameKey: gameKey, g: g) }
    func setHeldCurrency(gameKey: String, value: Int) { vm.setHeldCurrency(gameKey: gameKey, value: Int32(value)) }
    // chunk ③
    func loadEnkaProfile(game: String, uid: String) { vm.loadEnkaProfile(game: game, uid: uid) }
    func autoLoadEnka(game: String, force: Bool = false) { vm.autoLoadEnka(game: game, force: force) }
    func autoLoadEnkaSection(games: [String], force: Bool = false) { vm.autoLoadEnkaSection(games: games, force: force) }
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
    func setHomeCards(_ list: [HomeCardItem]) { vm.setHomeCards(list: list) }
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
    /// 온보딩 필요 여부 — 미로그인(게스트 모드 없음, 구글 로그인 필수).
    var needsOnboarding: Bool { account.isGuest }
    /// 현재 강조색.
    var accent: GLGAccent { GLGTheme.accent(accentIndex) }
}
