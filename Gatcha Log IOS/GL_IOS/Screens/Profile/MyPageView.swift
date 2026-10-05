import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 마이페이지 3.0 — 카드를 걷고 화면 폭 전체 섹션 + 회색 띠 구분(목업 A안, Compose MyPageScreen 대응).
// 섹션: ① 프로필 ② 이번 달 지출 ③ 게임별 지출 ④ 지출 기록 ⑤ 활동 ⑥ 절약 챌린지.
// 게임 정보(가챠·천장·UID)는 게임정보 탭 몫이라 여기 두지 않는다.
// 계정 전환·내보내기·테마 등 관리 항목은 ⚙ 설정에서 처리.
// ════════════════════════════════════════════════════════════════════════════

/// id 는 연-월로 고정한다 — UUID 를 쓰면 body 평가마다 새 id 가 생겨 차트 막대가 전부 재생성된다.
private struct MonthPoint: Identifiable { let id: String; let month: Int; let amount: Int64 }

private let hairColor = Color(hex: 0xFFEEF0F2)
private let upColor = Color(hex: 0xFFDC2626)
private let downColor = Color(hex: 0xFF15803D)

struct MyPageView: View {
    var store: SpendingStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ProfileSection(store: store)
                Band()
                MonthSection(monthly: store.monthlyTotal, prevMonthly: store.prevMonthTotal, budget: store.budget,
                             dailyAvg: dailyAvg, total: totals.amount, monthCount: totals.monthCount, trend: monthlyTrend)
                Band()
                GameSpendSection(spendings: store.spendings)
                Band()
                RecordSection(trend: monthlyTrend)
                Band()
                ActivitySection(history: store.attendanceHistory, tracked: store.trackedAttendanceGames,
                                streak: store.attendanceStreak, taskStats: store.taskStats, spendCount: store.spendings.count)
                Band()
                NavigationLink { SavingsChallengeView(store: store) } label: {
                    ChallengeSection(challenge: store.challenge)
                }
                .buttonStyle(.plain)
            }
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(Color.white)
        // 제목은 막대에 안 보인다(10/1 요청) — 다만 제목 자체는 채운다. 비우면 뒤로가기 길게 누르기 메뉴가 공백 줄이 된다.
        // 툴바에 글자 뷰를 직접 세우면 자리가 모자랄 때 「…」 로 접혔다 — 그래서 시스템 제목을 걷는 방식으로 숨긴다.
        .navigationTitle("마이페이지")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(removing: .title)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { settingsButton } }
        // 설정은 마이페이지의 하위 페이지. 다른 화면이 HoYoLAB 연동을 요청하면(홈 만료 배너 「재연동」) 설정까지
        // 자동으로 들어간다 — 설정이 onAppear 에서 요청을 소비해 연동 페이지를 연다(9/30, Android 와 같은 흐름).
        .navigationDestination(isPresented: $openSettings) { SettingsView(store: store) }
        .onAppear { if store.pendingOpenHoyolabLink { openSettings = true } }
        .onChange(of: store.pendingOpenHoyolabLink) { _, v in if v { openSettings = true } }
        .task(id: store.spendings) { totals = Self.computeTotals(store.spendings) }
    }

    private var settingsButton: some View {
        Button { openSettings = true } label: { Image(systemName: "gearshape") }
    }

    // 누적 합계·이번 달 건수는 지출 전체를 훑는다 — 지출이 바뀔 때만 계산한다.
    private struct Totals: Equatable { var amount: Int64 = 0; var monthCount: Int = 0 }
    @State private var totals = Totals()
    @State private var openSettings = false

    private static func computeTotals(_ spendings: [Spending]) -> Totals {
        let du = DateUtil.shared
        let ym = du.yearMonthKey(millis: Int64(Date().timeIntervalSince1970 * 1000))
        var sum: Int64 = 0
        var n = 0
        for s in spendings {                                   // 순회 1회
            sum += s.amount
            if du.yearMonthKey(millis: s.dateMillis) == ym { n += 1 }
        }
        return Totals(amount: sum, monthCount: n)
    }

    private var dailyAvg: Int64 {
        let day = Calendar.current.component(.day, from: Date())
        return store.monthlyTotal / Int64(max(day, 1))
    }
    /// 월별 추이 — 합계는 Kotlin 이 지출을 한 번만 훑어 만들어 둔 값을 그대로 쓴다(오래된 달 → 이번 달 순).
    private var monthlyTrend: [MonthPoint] {
        let totals = store.recentMonthlyTotals
        return totals.indices.map { i in
            let ym = Self.yearMonth(monthsAgo: totals.count - 1 - i)
            return MonthPoint(id: "\(ym.0)-\(ym.1)", month: Int(ym.1), amount: totals[i])
        }
    }
    private static func yearMonth(monthsAgo: Int) -> (Int32, Int32) {
        let cal = Calendar.current
        let date = cal.date(byAdding: .month, value: -monthsAgo, to: Date()) ?? Date()
        let c = cal.dateComponents([.year, .month], from: date)
        return (Int32(c.year ?? 2026), Int32(c.month ?? 1))
    }
}

// ── 공용 소품 — 목업 A안 규격 ─────────────────────────────────────────────────
// 섹션: 좌우 20 · 위 22 · 아래 20 / 섹션 사이 10pt 회색 띠 / 줄 사이 1pt 헤어라인

private struct Band: View {
    var body: some View { GldsBand() }
}

private struct Hair: View {
    var body: some View { GldsHairline() }
}

private struct MPSection<Content: View>: View {
    var top: CGFloat = 22
    /// 20 − 마지막 요소의 자체 아래 여백 — 목록 줄(ListRow vertical 12)로 끝나면 8. 눈에 보이는 끝 → 띠 = 20.
    /// 페이지 맨 아래 섹션은 줄이지 않는다(안전 영역 위 숨 쉴 여백).
    var bottom: CGFloat = 20
    @ViewBuilder var content: Content
    var body: some View {
        GldsSection(top: top, bottom: bottom) { content }
            .contentShape(Rectangle())
    }
}

/// 섹션 머리 — 제목 17 + 오른쪽 보조 문구.
private struct SectionHead<Trailing: View>: View {
    let title: String
    @ViewBuilder var trailing: Trailing
    var body: some View {
        HStack {
            Text(title).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            Spacer(minLength: 8)
            trailing
        }
        .padding(.bottom, 14)
    }
}

private extension SectionHead where Trailing == EmptyView {
    init(title: String) { self.title = title; self.trailing = EmptyView() }
}

private func moreText(_ s: String) -> some View {
    Text(s).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
}

private func subText(_ s: String, color: Color = GLGColor.textSecondary, bold: Bool = false) -> some View {
    Text(s).font(.pretendard(size: 12, weight: bold ? .bold : .regular)).foregroundStyle(color).lineLimit(1)
}

private func numText(_ s: String) -> some View {
    Text(s).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
}

private func labelText(_ s: String) -> some View {
    Text(s).font(.pretendard(size: 14)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
}

/// 목록 한 줄 — 위아래 12(GLDS 기본 줄) · 요소 사이 12.
private struct ListRow<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        HStack(spacing: 12) { content }.padding(.vertical, 12)
    }
}

/// 세 칸 지표 줄 — 값 15 · 라벨 12, 왼쪽 정렬.
private struct StatTriple: View {
    let cells: [(String, String)]
    var body: some View {
        HStack(spacing: 0) {
            ForEach(cells.indices, id: \.self) { i in
                VStack(alignment: .leading, spacing: 0) {
                    numText(cells[i].0)
                    subText(cells[i].1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.top, 18)
    }
}

// ── ① 프로필 ────────────────────────────────────────────────────────────────

private struct ProfileSection: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var account: Account { store.account }
    private var isGuest: Bool { account.isGuest }

    var body: some View {
        MPSection(top: 12) {
            HStack(spacing: 14) {
                ProfileAvatarView(photoUrl: isGuest ? nil : account.photoUrl, size: 56)
                    .background(accent.primary, in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text(isGuest ? "게스트" : store.profile.name)
                        .font(.pretendard(size: 18, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    subText(isGuest ? "게스트 · 동기화 꺼짐" : "구글 계정 동기화 중",
                            color: isGuest ? GLGColor.textSecondary : downColor, bold: true)
                }
                Spacer(minLength: 8)
                if !isGuest {
                    // 계정 단일화: 로그아웃을 마이페이지 헤더로 일원화 (설정의 중복 계정 카드 제거)
                    GldsButton(title: "로그아웃", variant: .neutral, size: .xs, fullWidth: false) { store.signOut() }
                }
            }
            if isGuest {
                GldsButton(title: "Google로 로그인") { store.signIn() }
                    .padding(.top, 14)
            }
        }
    }
}

// ── ② 이번 달 지출 ───────────────────────────────────────────────────────────

private struct MonthSection: View {
    let monthly: Int64
    let prevMonthly: Int64
    let budget: Int64
    let dailyAvg: Int64
    let total: Int64
    let monthCount: Int
    let trend: [MonthPoint]
    @Environment(\.glgAccent) private var accent

    var body: some View {
        MPSection {
            SectionHead(title: "이번 달 지출") { trendText }
            Text(won(monthly)).font(.pretendard(size: 32, weight: .black))
                .foregroundStyle(GLGColor.textPrimary).lineLimit(1).minimumScaleFactor(0.6)
            // 예산은 설정했을 때만 — 초과 문구는 홈 예산 카드와 같은 규칙.
            if budget > 0 { budgetBlock }
            StatTriple(cells: [
                (won(dailyAvg), "일 평균"),
                (won(total), "누적 지출"),
                ("\(monthCount)건", "이번 달 기록"),
            ])
            monthBars
        }
    }

    /// 지난달 0 이면 비교하지 않는다(예전 추세 알약과 같은 규칙).
    @ViewBuilder private var trendText: some View {
        if prevMonthly > 0 {
            let delta = Int((Double(monthly - prevMonthly) / Double(prevMonthly)) * 100)
            let down = delta <= 0
            Text("\(down ? "▼" : "▲") \(abs(delta))% 지난달보다")
                .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(down ? downColor : GLGColor.urgent)
        }
    }

    private var budgetBlock: some View {
        let over = monthly > budget
        let pct = Int(monthly * 100 / budget)
        let frac = over ? 1 : CGFloat(Double(monthly) / Double(budget))
        return VStack(spacing: 6) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    hairColor
                    (over ? GLGColor.urgent : accent.primary).frame(width: geo.size.width * frac)
                }
            }
            .frame(height: 8)
            .clipShape(RoundedRectangle(cornerRadius: 4))
            HStack {
                subText("예산 \(won(budget)) 중 \(pct)%")
                Spacer(minLength: 8)
                if over { subText("\(won(monthly - budget)) 초과", color: GLGColor.urgent) }
                else { subText("\(won(budget - monthly)) 남음") }
            }
        }
        .padding(.top, 12)
    }

    /// 최근 6개월 막대 — 막대 최대 70 · 라벨 12, 이번 달만 강조색.
    private var monthBars: some View {
        let maxAmt = max(trend.map { $0.amount }.max() ?? 0, 1)
        return HStack(alignment: .bottom, spacing: 10) {
            ForEach(Array(trend.enumerated()), id: \.element.id) { idx, p in
                let isCurrent = idx == trend.count - 1
                let frac = CGFloat(Double(p.amount) / Double(maxAmt))
                VStack(spacing: 6) {
                    UnevenRoundedRectangle(topLeadingRadius: 5, topTrailingRadius: 5)
                        .fill(isCurrent ? accent.primary : accent.primary.opacity(0.2))
                        .frame(height: max(70 * frac, 3))
                    subText("\(p.month)월", color: isCurrent ? GLGColor.textPrimary : GLGColor.textSecondary, bold: isCurrent)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 96, alignment: .bottom)
        .padding(.top, 22)
    }
}

// ── ③ 게임별 지출 ────────────────────────────────────────────────────────────

/// '기타'(상위 5개 밖) 조각 색 — 게임별 월 추이 카드와 동일 회색.
private let etcSliceColor = Color(hex: 0xFFB8BDC6)

private struct GameSpendSection: View {
    let spendings: [Spending]

    /// id 는 게임명으로 고정 — 그룹 키라 이미 고유하다.
    private struct Slice: Identifiable {
        var id: String { game }
        let game: String; let amount: Int64; let color: Color
    }

    // 띠·목록이 같은 집계를 쓴다 — 지출이 바뀔 때만 한 번 만든다.
    @State private var slices: [Slice] = []
    @State private var total: Int64 = 0
    @State private var pcts: [Int] = []

    /// 6개 이상이면 나머지를 '기타'로 묶는다(게임별 월 추이 카드와 같은 규칙).
    private static func compute(_ spendings: [Spending]) -> (slices: [Slice], total: Int64, pcts: [Int]) {
        var sums: [String: Int64] = [:]
        var colors: [String: Int64] = [:]
        var sum: Int64 = 0
        for s in spendings {                       // 순회 1회
            sums[s.gameName, default: 0] += s.amount
            if colors[s.gameName] == nil { colors[s.gameName] = s.gameColor }
            sum += s.amount
        }
        var list = sums.map { Slice(game: $0.key, amount: $0.value,
                                    color: Color(argb64: colors[$0.key] ?? 0xFF8E8E93)) }
            .sorted { $0.amount > $1.amount }
        if list.count > 5 {
            let etc = list.dropFirst(5).reduce(Int64(0)) { $0 + $1.amount }
            list = Array(list.prefix(5)) + [Slice(game: "기타", amount: etc, color: etcSliceColor)]
        }
        // 퍼센트는 **합이 정확히 100이 되도록** 공유 로직으로 배분한다(최대 잔여법).
        let pcts = FormatKt.percentShares(values: list.map { KotlinLong(value: $0.amount) }).map { $0.intValue }
        return (list, sum, pcts)
    }

    var body: some View {
        MPSection(bottom: slices.isEmpty || total <= 0 ? 20 : 8) {
            SectionHead(title: "게임별 지출") { moreText("전체 기간") }
            if slices.isEmpty || total <= 0 {
                subText("아직 지출 기록이 없어요")
            } else {
                shareBar
                Spacer().frame(height: 6)
                ForEach(Array(slices.enumerated()), id: \.element.id) { i, s in
                    if i > 0 { Hair() }
                    ListRow {
                        RoundedRectangle(cornerRadius: 3).fill(s.color).frame(width: 10, height: 10)
                        labelText(s.game).frame(maxWidth: .infinity, alignment: .leading)
                        numText(won(s.amount))
                        subText("\(i < pcts.count ? pcts[i] : 0)%").frame(width: 34, alignment: .trailing)
                    }
                }
            }
        }
        .task(id: spendings) { (slices, total, pcts) = Self.compute(spendings) }
    }

    /// 누적 비중 띠 — 조각 사이 2 틈.
    private var shareBar: some View {
        let parts = slices.filter { $0.amount > 0 }
        return GeometryReader { geo in
            let avail = geo.size.width - CGFloat(max(parts.count - 1, 0)) * 2
            HStack(spacing: 2) {
                ForEach(parts) { s in
                    s.color.frame(width: avail * CGFloat(Double(s.amount) / Double(total)))
                }
            }
        }
        .frame(height: 12)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

// ── ④ 지출 기록 ──────────────────────────────────────────────────────────────

private struct RecordSection: View {
    let trend: [MonthPoint]

    var body: some View {
        let avg: Int64 = trend.isEmpty ? 0 : trend.reduce(Int64(0)) { $0 + $1.amount } / Int64(trend.count)
        let peak = trend.max { $0.amount < $1.amount }.flatMap { $0.amount > 0 ? $0 : nil }
        MPSection(bottom: 8) {
            SectionHead(title: "지출 기록")
            ListRow {
                labelWithPeriod("월 평균")
                numText(won(avg))
            }
            Hair()
            ListRow {
                labelWithPeriod("가장 많이 쓴 달")
                if let p = peak {
                    numText("\(p.month)월")
                    subText(won(p.amount))
                } else {
                    numText("—")
                }
            }
        }
    }

    private func labelWithPeriod(_ label: String) -> some View {
        HStack(spacing: 4) {
            labelText(label)
            subText("최근 6개월")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// ── ⑤ 활동 ───────────────────────────────────────────────────────────────────

private enum MyPageAttendLevel { case none, some, all }

private struct ActivitySection: View {
    let history: [String: Set<String>]
    let tracked: [Game]
    let streak: Int
    let taskStats: [TaskStats]
    let spendCount: Int
    @Environment(\.glgAccent) private var accent

    /// 오래된 날 → 오늘 순 30칸. 출석한 게임 수로 칸 농도를 가른다(전부 = 진하게 · 일부 = 옅게).
    private var days: [MyPageAttendLevel] {
        let du = DateUtil.shared
        let keys = tracked.map { $0.key }
        return (0..<30).reversed().map { ago in
            let done = history[du.hoyoDayKeyAgoKey(daysAgo: Int32(ago))] ?? []
            if done.isEmpty { return .none }
            if !keys.isEmpty && keys.allSatisfy({ done.contains($0) }) { return .all }
            return .some
        }
    }

    /// 일일 숙제 완주 — 기록이 있는 게임들의 30일 완주율 평균.
    private var taskRate: Int? {
        let l = taskStats.filter { $0.dailyDays > 0 }
        return l.isEmpty ? nil : l.reduce(0) { $0 + Int($1.dailyRate) } / l.count
    }

    var body: some View {
        let d = days
        MPSection(bottom: taskStats.isEmpty ? 20 : 8) {
            SectionHead(title: "활동") { moreText("최근 30일") }
            VStack(spacing: 4) {
                ForEach(0..<2, id: \.self) { r in
                    HStack(spacing: 4) {
                        ForEach(0..<15, id: \.self) { c in
                            cell(d[r * 15 + c], isToday: r == 1 && c == 14)
                        }
                    }
                }
            }
            subText("진한 칸 = 모든 게임 출석 · 옅은 칸 = 일부").padding(.top, 8)
            StatTriple(cells: [
                ("\(streak)일", "연속 출석"),
                (taskRate.map { "\($0)%" } ?? "—", "일일 숙제 완주"),
                ("\(spendCount)건", "지출 기록"),
            ])
            if !taskStats.isEmpty {
                Hair().padding(.top, 18)
                ForEach(Array(taskStats.enumerated()), id: \.offset) { i, s in
                    if i > 0 { Hair() }
                    ListRow {
                        labelText("\(s.gameShort) 숙제").frame(maxWidth: .infinity, alignment: .leading)
                        // 주간은 주간 기록을 주는 게임만(게임정보 숙제 완주율과 같은 규칙).
                        let week = s.weeklyWeeks > 0 ? " · 주간 \(s.weekDone ? "완료" : "미완")" : ""
                        subText("오늘 \(s.todayDone ? "완료" : "미완")\(week)",
                                color: s.todayDone ? GLGColor.textSecondary : upColor)
                        numText(s.isEmpty ? "—" : "\(s.dailyRate)%").frame(width: 44, alignment: .trailing)
                    }
                }
            }
        }
    }

    private func cell(_ level: MyPageAttendLevel, isToday: Bool) -> some View {
        RoundedRectangle(cornerRadius: 3)
            .fill(level == .all ? accent.primary : level == .some ? accent.primary.opacity(0.5) : hairColor)
            .aspectRatio(1, contentMode: .fit)
            .frame(maxWidth: .infinity)
            // 오늘 칸 — 1pt 띄운 2pt 테두리(목업 outline).
            .overlay {
                if isToday {
                    RoundedRectangle(cornerRadius: 5).stroke(GLGColor.textPrimary, lineWidth: 2).padding(-2)
                }
            }
    }
}

// ── ⑥ 절약 챌린지 ────────────────────────────────────────────────────────────

private struct ChallengeSection: View {
    let challenge: ChallengeSummary?

    var body: some View {
        let list = challenge?.challenges ?? []
        MPSection {
            SectionHead(title: "절약 챌린지") { moreText("전체 보기 ›") }
            ListRow {
                labelText("무지출 스트릭").frame(maxWidth: .infinity, alignment: .leading)
                numText("\(challenge?.noSpendStreak ?? 0)일")
                subText("최고 \(challenge?.bestStreak ?? 0)일")
            }
            Hair()
            ListRow {
                labelText("진행 중 챌린지").frame(maxWidth: .infinity, alignment: .leading)
                numText("\(list.count)개")
                subText("달성 \(list.filter { $0.reached }.count)개")
            }
            Hair()
            ListRow {
                labelText("획득 배지").frame(maxWidth: .infinity, alignment: .leading)
                numText("\(challenge?.earnedBadgeCount ?? 0) / \(challenge?.totalBadgeCount ?? 0)")
            }
        }
    }
}


// ── 프로필 아바타 (네트워크 이미지 / 폴백) ──────────────────────────────────

struct ProfileAvatarView: View {
    let photoUrl: String?
    var size: CGFloat = 44

    var body: some View {
        Group {
            if let url = photoUrl, !url.isEmpty, let u = URL(string: url) {
                // 툴바 프로필은 홈이 다시 그려질 때마다 만들어진다 — 디코딩 캐시가 없으면 매번 다시 불러온다.
                GLGRemoteImage(url: u, side: size)
                    .background(placeholder)
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var placeholder: some View {
        ZStack {
            Circle().fill(GLGColor.progressEmpty)
            Image(systemName: "person.fill")
                .font(.pretendard(size: size * 0.5))
                .foregroundStyle(GLGColor.navUnselected)
        }
    }
}
