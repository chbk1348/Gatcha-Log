import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 마이페이지 3.0 — 카드를 걷고 화면 폭 전체 섹션 + 회색 띠 구분(목업 A안, Compose MyPageScreen 대응).
// 섹션: ① 프로필 ② 이번 달 지출 ③ 게임별 지출 ④ 지출 기록 ⑤ 활동 ⑥ 절약 챌린지.
// 게임 정보(가챠·천장·UID)는 게임정보 탭 몫이라 여기 두지 않는다.
// 계정 전환·내보내기·테마 등 관리 항목은 ⚙ 설정에서 처리.
// ════════════════════════════════════════════════════════════════════════════

// 통계 계산은 전부 공용(Kotlin) `MyPageStats` — Android 와 같은 함수다(27.52.0). 이 파일은 그리기만 한다.
// 예전엔 같은 계산을 Swift 로 따로 갖고 있었고, 「지난달보다 N%」와 금액이 같은 게임의 순서가 Android 와 갈렸다.

private let hairColor = Color(hex: 0xFFEEF0F2)
private let upColor = Color(hex: 0xFFDC2626)
private let downColor = Color(hex: 0xFF15803D)

struct MyPageView: View {
    var store: SpendingStore

    var body: some View {
        // 막대(②)와 지출 기록(④)이 같은 추이를 쓴다 — 한 번만 만든다.
        let trend = monthlyTrend
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ProfileSection(store: store)
                Band()
                MonthSection(monthly: store.monthlyTotal, prevMonthly: store.prevMonthTotal, budget: store.budget,
                             dailyAvg: dailyAvg, total: totals.amount, monthCount: totals.monthCount, trend: trend.points)
                Band()
                GameSpendSection(spendings: store.spendings)
                Band()
                RecordSection(trend: trend)
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
        Button { openSettings = true } label: { GLGLineIcon("IconSettings", label: "설정") }
    }

    // 누적 합계·이번 달 건수는 지출 전체를 훑는다 — 지출이 바뀔 때만 계산한다.
    private struct Totals: Equatable { var amount: Int64 = 0; var monthCount: Int = 0 }
    @State private var totals = Totals()
    @State private var openSettings = false

    private static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    private static func computeTotals(_ spendings: [Spending]) -> Totals {
        let t = MyPageStats.shared.totals(spendings: spendings, nowMillis: nowMillis)   // 순회 1회
        return Totals(amount: t.total, monthCount: Int(t.monthCount))
    }

    private var dailyAvg: Int64 {
        MyPageStats.shared.dailyAverage(monthTotal: store.monthlyTotal, nowMillis: Self.nowMillis)
    }
    /// 월별 추이 — 합계는 Kotlin 이 지출을 한 번만 훑어 만들어 둔 값을 그대로 쓴다(오래된 달 → 이번 달 순).
    /// 월 번호 · 막대 비율 · 월 평균 · 가장 많이 쓴 달은 공용 `MyPageStats.trend` 가 붙인다.
    private var monthlyTrend: MyPageTrend {
        MyPageStats.shared.trend(recentTotals: store.recentMonthlyTotals.map { KotlinLong(value: $0) },
                                 nowMillis: Self.nowMillis)
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
    let trend: [MyPageMonthPoint]
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
            // 공용 정수 계산 — 예전엔 Double 로 나눠 버려 Android(Float)와 1% 씩 갈렸다(27.52.0).
            let delta = Int(SpendingInsightStats.shared.momPercent(thisMonth: monthly, lastMonth: prevMonthly))
            let down = delta <= 0
            Text("\(down ? "▼" : "▲") \(abs(delta))% 지난달보다")
                .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(down ? downColor : GLGColor.urgent)
        }
    }

    private var budgetBlock: some View {
        let progress = MyPageStats.shared.budget(monthTotal: monthly, budget: budget)
        let over = progress.over
        let pct = Int(progress.percent)
        let frac = CGFloat(progress.ratio)
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
                if over { subText("\(won(progress.gap)) 초과", color: GLGColor.urgent) }
                else { subText("\(won(progress.gap)) 남음") }
            }
        }
        .padding(.top, 12)
    }

    /// 최근 6개월 막대 — 막대 최대 70 · 라벨 12, 이번 달만 강조색.
    private var monthBars: some View {
        // id 는 연-월로 고정한다 — 값이 바뀔 때마다 새 id 가 생기면 차트 막대가 전부 재생성된다.
        HStack(alignment: .bottom, spacing: 10) {
            ForEach(Array(trend.enumerated()), id: \.element.yearMonth) { idx, p in
                let isCurrent = idx == trend.count - 1
                let frac = CGFloat(p.barRatio)
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
    /// 묶기 · 정렬 · 퍼센트(합이 정확히 100, 최대 잔여법)는 공용 `MyPageStats.gameShare` 가 한다(27.52.0).
    /// 예전엔 Dictionary 로 모아 정렬해서 금액이 같은 게임의 순서가 실행마다 달라질 수 있었다.
    private static func compute(_ spendings: [Spending]) -> (slices: [Slice], total: Int64, pcts: [Int]) {
        let share = MyPageStats.shared.gameShare(spendings: spendings)   // 순회 1회
        let list = share.slices.map {
            Slice(game: $0.game, amount: $0.amount, color: $0.isEtc ? etcSliceColor : Color(argb64: $0.color))
        }
        return (list, share.total, share.slices.map { Int($0.percent) })
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
    let trend: MyPageTrend

    var body: some View {
        let avg: Int64 = trend.monthlyAverage
        let peak = trend.peak
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

private struct ActivitySection: View {
    let history: [String: Set<String>]
    let tracked: [Game]
    let streak: Int
    let taskStats: [TaskStats]
    let spendCount: Int
    @Environment(\.glgAccent) private var accent

    /// 오래된 날 → 오늘 순 30칸. 출석한 게임 수로 칸 농도를 가른다(전부 = 진하게 · 일부 = 옅게).
    ///
    /// 판정은 공유 `AttendanceLogic.dayLevel` — 출석 상세의 달력 · 7일 스트립 · 이번 달 누계와 같은 함수다(27.52.0).
    /// 화면마다 따로 판정하던 때는 같은 날이 여기서는 '일부', 달력에서는 '전체'로 갈릴 수 있었다.
    private var days: [AttendanceDayLevel] {
        let du = DateUtil.shared
        return (0..<30).reversed().map { ago in
            let done = history[du.hoyoDayKeyAgoKey(daysAgo: Int32(ago))] ?? []
            return AttendanceLogic.shared.dayLevel(done: done, games: tracked)
        }
    }

    /// 일일 숙제 완주 — 기록이 있는 게임들의 30일 완주율 평균.
    private var taskRate: Int? {
        MyPageStats.shared.dailyTaskRate(taskStats: taskStats)?.intValue
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

    private func cell(_ level: AttendanceDayLevel, isToday: Bool) -> some View {
        RoundedRectangle(cornerRadius: 3)
            .fill(level == .full ? accent.primary : level == .partial ? accent.primary.opacity(0.5) : hairColor)
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
