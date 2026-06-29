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

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                if store.hoyoTokenExpired {
                    TokenExpiredBanner { store.requestOpenHoyolabLink(); onSwitchTab(3) }
                        .glgLoadIn(0, appeared: $appeared)
                }
                // 홈 허브 — 정보 중복 없이: 지출/예산 · 오늘 할 일 · 이번주 일정 · 게임 소식
                DashboardSpendCard(monthlyTotal: monthlyTotal, budget: store.budget, onTap: { onSwitchTab(1) })
                    .glgLoadIn(1, appeared: $appeared)
                if !store.gameInfoReady || !todayTasks.isEmpty {
                    todayTask.glgLoadIn(2, appeared: $appeared)
                }
                if !store.gameInfoReady {
                    // 게임 정보 로딩 중 — 일정·소식 카드 자리에 스켈레톤(빈 화면 대신 골격 노출)
                    DashCardSkeleton(rows: 3).glgLoadIn(3, appeared: $appeared)
                    DashCardSkeleton(rows: 2).glgLoadIn(4, appeared: $appeared)
                } else {
                    DashboardScheduleCard(events: store.gameEvents, challenges: store.challenges, onTap: { store.requestGameInfoAnchor(.schedule); onSwitchTab(2) })
                        .glgLoadIn(3, appeared: $appeared)
                    DashboardNewsCard(news: store.gameNews, anniversaries: GameAnniversary.shared.upcoming(nowMillis: nowMs()), onTap: { store.requestGameInfoAnchor(.news); onSwitchTab(2) })
                        .glgLoadIn(4, appeared: $appeared)
                }
                Color.clear.frame(height: 12)
            }
            .padding(.horizontal, 16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .refreshable { store.refreshGameInfo(force: true) }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // 프로필 사진만 헤더 좌측에 노출 (탭하면 마이페이지). 시스템 글래스 squircle 크롬 없이 순수 원형.
            ToolbarItem(placement: .topBarLeading) {
                Button { onSwitchTab(3) } label: {
                    ProfileAvatarView(photoUrl: store.account.isGuest ? nil : store.account.photoUrl, size: 32)
                }
                .buttonStyle(.plain)
            }
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    NotificationDetailView(alerts: alerts, onBudget: { showBudget = true }, onGameInfo: { onSwitchTab(2) })
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


    private var todayTask: some View {
        Group {
            if !store.gameInfoReady {
                TodayTaskSkeleton()
            } else {
                TodayTaskCard(tasks: todayTasks, inProgress: store.checkingIn != nil)
            }
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
    private var greeting: String {
        let h = Calendar.current.component(.hour, from: Date())
        switch h { case 5...10: return "좋은 아침이에요"; case 11...16: return "좋은 오후예요"; case 17...21: return "좋은 저녁이에요"; default: return "오늘도 수고했어요" }
    }
    private var monthlyTotal: Int64 { store.monthlyTotal() }
    private var prevTotal: Int64 { store.prevMonthTotal() }
    private var gameOverBudget: [String] {
        if store.gameBudgets.isEmpty { return [] }
        let totals = store.monthlyTotalsByGame()
        return GameData.shared.games.compactMap { g in
            let limit = store.gameBudgets[g.key] ?? 0
            return (limit > 0 && (totals[g.key] ?? 0) > limit) ? g.shortName : nil
        }
    }
    private var perGameSpend: [GameSpend] {
        let totals = store.monthlyTotalsByGame()
        return GameData.shared.games.compactMap { g -> GameSpend? in
            let spent = totals[g.key] ?? 0
            let limit = store.gameBudgets[g.key] ?? 0
            return (spent <= 0 && limit <= 0) ? nil : GameSpend(game: g, spent: spent, limit: limit)
        }.sorted { $0.spent > $1.spent }
    }
    private var savingTip: String {
        if store.budget > 0 && monthlyTotal > store.budget { return "이번 달은 예산을 넘겼어요. 다음 픽업까지 무·저과금으로 천장을 모아보세요." }
        if !gameOverBudget.isEmpty { return "\(gameOverBudget[0]) 한도를 넘었어요. 게임별 예산을 점검해보세요." }
        if store.budget <= 0 { return "월 예산을 정하면 페이스를 알려드려요. 보통 한 달 결제액의 80% 선이 적당해요." }
        return "천장이 가까운 게임부터 모으면 50/50 손해를 줄일 수 있어요."
    }
    private var alerts: [HomeAlert] { buildAlerts(monthlyTotal: monthlyTotal, budget: store.budget, gameOver: gameOverBudget, banners: store.activeBanners, attendanceToday: store.attendanceToday, monthKey: "\(store.displayYear)-\(store.displayMonth)") }
    private var unreadCount: Int { alerts.filter { !store.readAlerts.contains($0.key) }.count }
    private var todayTasks: [TodayItem] {
        let resins = store.liveNotes.filter { $0.maxResin > 0 && $0.resinRatio >= 0.85 }
            .sorted { $0.resinRatio > $1.resinRatio }
            .map { ResinAlert(gameShort: GameData.shared.byName(name: $0.game).shortName, label: $0.resinLabel, cur: Int($0.currentResin), max: Int($0.maxResin), full: $0.currentResin >= $0.maxResin) }
        // 픽업은 '이번주 일정' 카드·게임 정보 페이지에서 확인 — 오늘 할 일에서는 제외
        return resolveTodayTasks(
            pendingAttendance: GameData.shared.attendanceGames.filter { !store.attendanceToday.contains($0.key) }.count,
            resins: resins, urgentBanner: nil, budget: store.budget, monthlyTotal: monthlyTotal,
            onCheckInAll: { store.checkInAll() }, onResin: { store.requestGameInfoAnchor(.notes); onSwitchTab(2) }, onBanner: { store.requestGameInfoAnchor(.schedule); onSwitchTab(2) }, onBudget: { showBudget = true })
    }
}

// ── 데이터 ──
struct GameSpend { let game: Game; let spent: Int64; let limit: Int64 }
struct BannerPlan { let maxPulls: Int; let wonCost: Int64 }
struct ResinAlert { let gameShort: String; let label: String; let cur: Int; let max: Int; let full: Bool }
struct TodayItem: Identifiable { let id = UUID(); let icon: String; let message: String; let cta: String; let urgent: Bool; let busyable: Bool; let action: () -> Void }
enum AlertKind { case budgetOver, budgetNear, budgetGameOver, banner, attendance }
struct HomeAlert: Identifiable { let id = UUID(); let kind: AlertKind; let message: String; let key: String }

func resolveTodayTasks(pendingAttendance: Int, resins: [ResinAlert], urgentBanner: GachaBanner?, budget: Int64, monthlyTotal: Int64,
                       onCheckInAll: @escaping () -> Void, onResin: @escaping () -> Void, onBanner: @escaping () -> Void, onBudget: @escaping () -> Void) -> [TodayItem] {
    var items: [TodayItem] = []
    let pct = budget > 0 ? Int(monthlyTotal * 100 / budget) : 0
    if pendingAttendance > 0 { items.append(TodayItem(icon: "checkmark.circle", message: "출석 안 한 게임 \(pendingAttendance)개", cta: "한 번에 출석", urgent: false, busyable: true, action: onCheckInAll)) }
    for r in resins { items.append(TodayItem(icon: "bolt.fill", message: r.full ? "\(r.gameShort) \(r.label) 가득 참" : "\(r.gameShort) \(r.label) \(r.cur)/\(r.max) 곧 넘침", cta: "게임 정보", urgent: true, busyable: false, action: onResin)) }
    if let b = urgentBanner { items.append(TodayItem(icon: "die.face.5", message: "\(b.name) 픽업 \(GameInfoKt.dhLabel(targetMillis: b.endMillis, nowMillis: nowMs())) 막바지", cta: "픽업 계획", urgent: true, busyable: false, action: onBanner)) }
    if budget > 0 && monthlyTotal > budget { items.append(TodayItem(icon: "banknote", message: "예산 \(pct - 100)% 초과", cta: "예산 점검", urgent: true, busyable: false, action: onBudget)) }
    else if budget > 0 && pct >= 90 { items.append(TodayItem(icon: "banknote", message: "예산 \(pct)% 사용", cta: "예산 점검", urgent: true, busyable: false, action: onBudget)) }
    return items
}

func buildAlerts(monthlyTotal: Int64, budget: Int64, gameOver: [String], banners: [GachaBanner], attendanceToday: Set<String>, monthKey: String) -> [HomeAlert] {
    var r: [HomeAlert] = []
    if budget > 0 {
        let pct = Int(monthlyTotal * 100 / budget)
        if monthlyTotal > budget { r.append(HomeAlert(kind: .budgetOver, message: "이번 달 예산을 초과했어요 (\(pct)%)", key: "budget_over:\(monthKey)")) }
        else if pct >= 90 { r.append(HomeAlert(kind: .budgetNear, message: "이번 달 예산의 \(pct)%를 사용했어요", key: "budget_near:\(monthKey)")) }
    }
    for name in gameOver { r.append(HomeAlert(kind: .budgetGameOver, message: "\(name) 이번 달 한도를 초과했어요", key: "budget_game_over:\(name):\(monthKey)")) }
    for b in banners where (0...3).contains(Int(b.dDay(nowMillis: nowMs()))) {
        let d = Int(b.dDay(nowMillis: nowMs()))
        r.append(HomeAlert(kind: .banner, message: "\(b.name) 픽업 배너 종료 \(d == 0 ? "D-DAY" : "D-\(d)")", key: "banner:\(b.name)"))
    }
    let pending = GameData.shared.attendanceGames.filter { !attendanceToday.contains($0.key) }.count
    if pending > 0 { r.append(HomeAlert(kind: .attendance, message: "오늘 출석체크가 \(pending)개 남아있어요", key: "attendance:\(DateUtil.shared.hoyoDayKey(millis: nowMs()))")) }
    return r
}
