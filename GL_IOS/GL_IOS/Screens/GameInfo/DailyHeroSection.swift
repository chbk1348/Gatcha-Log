import SwiftUI
import Shared

// 데일리 히어로 2.0 — 급한 하나가 지면을 지배하고, 나머지는 아래로.
// (Compose DailyHeroSection 대응) 판단 규칙은 전부 공유 DailyLogic 에 있다.
//
// 1.0 은 "지금 상태가 어떤가"에 답했다 — 게임 셋의 재화·출석을 같은 무게로 늘어놓고,
// 무엇부터 할지는 사용자가 세 줄을 읽고 판단하게 했다. 2.0 은 판단을 앞으로 당긴다.

struct DailyHeroSection: View {
    var store: SpendingStore
    var filter: String = "all"
    let onConfig: () -> Void
    var onOpenGameContent: (() -> Void)? = nil
    var onOpenClears: (() -> Void)? = nil
    @Environment(\.glgAccent) private var accent

    private var allTasks: [DailyTask] {
        DailyLogic.shared.tasks(notes: store.liveNotes, attendanceToday: Set(store.attendanceToday), nowMillis: nowMs())
    }
    private var tasks: [DailyTask] {
        filter == "all" ? allTasks : allTasks.filter { $0.gameKey == filter }
    }

    var body: some View {
        if !store.hoyolabConfig.isLinked {
            linkPrompt
        } else {
            let all = allTasks
            let list = tasks
            let hero = DailyLogic.shared.hero(tasks: list)
            let rest = list.filter { $0 !== hero }
            // 재화 카드는 **필터와 무관하게 3게임 전부** — 나란히 놓고 비교하는 게 이 카드의 쓸모다.
            let summaries = DailyLogic.shared
                .summaries(notes: store.liveNotes, attendanceToday: Set(store.attendanceToday), tasks: all)

            VStack(alignment: .leading, spacing: 0) {
                if let hero { urgentHero(hero) } else { calmHero(list.count) }

                VStack(alignment: .leading, spacing: 12) {
                    resinCard(summaries)
                    if !rest.isEmpty {
                        GLGCard(cornerRadius: 20, padding: 16) {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(hero != nil ? "그다음" : "오늘 할 일")
                                    .font(.pretendard(size: 12, weight: .bold))
                                    .foregroundStyle(GLGColor.textSecondary)
                                    .padding(.bottom, 4)
                                ForEach(Array(rest.enumerated()), id: \.offset) { i, t in
                                    if i > 0 { Divider() }
                                    taskRow(t)
                                }
                            }
                        }
                    }
                    AttendanceFold(history: store.attendanceHistory,
                                   pending: all.filter { $0.kind == "출석" }.count,
                                   onCheckInAll: { store.checkInAll() })
                    if let onOpenGameContent {
                        GameContentEntry(onTap: onOpenGameContent, onTapClears: onOpenClears)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // ── 히어로 — 색면을 쓰지 않는다 ──
    //
    // 지출 상세 히어로가 게임색 파스텔을 상태바까지 깔아 지면을 지배하는 형태인데,
    // 데일리까지 같은 판을 쓰면 두 화면이 구분되지 않는다. 여기는 글자와 여백만으로 세운다 —
    // 색면이 없으니 아래 흰 카드와 층이 겹치지 않아 화면도 가벼워진다.

    @ViewBuilder
    private func urgentHero(_ t: DailyTask) -> some View {
        heroFrame {
            heroKicker("\(t.gameShort) · 지금", Color(argb64: t.colorArgb))
            Text(t.label)
                .font(.pretendard(size: 30, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .tracking(-0.8)
                .padding(.top, 12)
            if !t.detail.isEmpty {
                Text(t.detail).font(.pretendard(size: 13))
                    .foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
            }
        }
    }

    /// 급한 일이 없을 때 — 같은 자리, 낮은 목소리. 막대는 브랜드 민트.
    @ViewBuilder
    private func calmHero(_ remaining: Int) -> some View {
        heroFrame {
            heroKicker("오늘의 데일리", accent.primary)
            Text(remaining > 0 ? "할 일 \(remaining)건 남았어요" : "오늘 할 일 끝났어요")
                .font(.pretendard(size: 27, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .tracking(-0.6)
                .padding(.top, 12)
            Text(remaining > 0 ? "급한 건 없어요 — 아래에서 하나씩" : "재화도 넉넉하고 출석도 다 했어요")
                .font(.pretendard(size: 13))
                .foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
        }
    }

    /// 히어로 공통 틀 — 배경 없음. 좌우는 섹션과 같은 16.
    @ViewBuilder
    private func heroFrame<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.top, 8).padding(.bottom, 22)
    }

    /// 히어로 첫 줄 — 짧은 색 막대 + 무엇에 대한 이야기인가 + 연속 기록.
    @ViewBuilder
    private func heroKicker(_ title: String, _ color: Color) -> some View {
        HStack(spacing: 8) {
            Capsule().fill(color).frame(width: 18, height: 3)
            Text(title).font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(color)
            Spacer(minLength: 8)
            if store.attendanceStreak > 0 {
                Text("연속 \(store.attendanceStreak)일")
                    .font(.pretendard(size: 11, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary)
            }
        }
    }

    /// 재화 카드 — 3게임을 **한 카드에 나란히**. 세로로 쌓으면 세 덩어리로 읽힌다.
    @ViewBuilder
    private func resinCard(_ items: [DailyGameSummary]) -> some View {
        GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                Text("재화").font(.pretendard(size: 12, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary)
                HStack(alignment: .top, spacing: 10) {
                    ForEach(items, id: \.gameKey) { resinCell($0) }
                }
            }
        }
    }

    @ViewBuilder
    private func resinCell(_ s: DailyGameSummary) -> some View {
        let color = Color(argb64: s.colorArgb)
        VStack(alignment: .leading, spacing: 0) {
            Text(s.gameShort).font(.pretendard(size: 11, weight: .bold))
                .foregroundStyle(color).lineLimit(1)
            Text(s.hasNote ? s.resinValue : "—")
                .font(.pretendard(size: 14, weight: .bold))
                .foregroundStyle(s.hasNote ? (s.resinFull ? Color(hex: 0xFFD0021B) : GLGColor.textPrimary)
                                           : GLGColor.textSecondary)
                .lineLimit(1).padding(.top, 5)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(hex: 0xFFE0E0E0))
                    if s.hasNote {
                        Capsule().fill(s.resinFull ? Color(hex: 0xFFD0021B) : color)
                            .frame(width: max(0, min(1, Double(s.resinRatio))) * geo.size.width)
                    }
                }
            }
            .frame(height: 4).padding(.top, 7)
            Text(s.hasNote ? (s.resinFull ? "가득" : (s.resinRecovery.isEmpty ? "—" : s.resinRecovery)) : "노트 없음")
                .font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
                .lineLimit(1).padding(.top, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // ── 목록 ──

    /// 할 일 한 줄 — 앱이 대신 할 수 있는 것(출석)에만 버튼이 붙는다.
    @ViewBuilder
    private func taskRow(_ t: DailyTask) -> some View {
        HStack(spacing: 10) {
            GLGGameTag(game: t.gameShort, size: .small)
            Text(t.label).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
            if !t.detail.isEmpty {
                Text(t.detail).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
            if t.actionable {
                if store.checkingIn == t.gameKey {
                    ProgressView().controlSize(.small)
                } else {
                    Button { store.attemptCheckIn(t.gameKey) } label: {
                        Text("출석").font(.pretendard(size: 11.5, weight: .bold))
                            .foregroundStyle(accent.primary)
                            .padding(.horizontal, 14).padding(.vertical, 7)
                            .background(accent.primary.opacity(0.14),
                                        in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    }.buttonStyle(.plain)
                }
            }
        }
        .padding(.vertical, 12)
    }

    // ── 미연동 안내 — 좌측 정렬(중앙정렬 4단 스택은 빈 상태의 기본 슬롭이다) ──
    private var linkPrompt: some View {
        heroFrame {
            heroKicker("오늘의 데일리", accent.primary)
            Text("HoYoLAB 을 연동해 주세요")
                .font(.pretendard(size: 25, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary).padding(.top, 12)
            Text("연동하면 재화·일일 숙제·출석을 한곳에서 볼 수 있어요.")
                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
            Button(action: onConfig) {
                Text("연동하기").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 11)
                    .background(accent.primary, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.top, 16)
        }
    }
}

/// 출석 기록 — 기본 접힘. 자동 출석이 도는 이상 매일 볼 정보가 아니다.
private struct AttendanceFold: View {
    let history: [String: Set<String>]
    let pending: Int
    let onCheckInAll: () -> Void
    @Environment(\.glgAccent) private var accent
    @State private var open = false

    var body: some View {
        GLGCard(cornerRadius: 20, padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text("출석 기록").font(.pretendard(size: 12.5, weight: .bold))
                        .foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if pending > 0 {
                        Button(action: onCheckInAll) {
                            Text("전체 출석").font(.pretendard(size: 11, weight: .bold))
                                .foregroundStyle(accent.primary)
                                .padding(.horizontal, 10).padding(.vertical, 5)
                                .background(accent.primary.opacity(0.14),
                                            in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                        }.buttonStyle(.plain)
                    }
                    Image(systemName: open ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(GLGColor.textSecondary)
                }
                .padding(.horizontal, 16).padding(.vertical, 13)
                .contentShape(Rectangle())
                .onTapGesture { withAnimation(.easeInOut(duration: 0.22)) { open.toggle() } }

                if open {
                    VStack(alignment: .leading, spacing: 14) {
                        WeekAttendanceStrip(history: history)
                        MonthAttendanceCalendar(history: history)
                    }
                    .padding(.horizontal, 16).padding(.bottom, 16)
                }
            }
        }
    }
}


// 출석 완료도
enum AttendLevel { case none, partial, full }
func attendLevel(_ count: Int) -> AttendLevel {
    let total = GLGGames.attendance.count
    if count <= 0 { return .none }
    if count >= total { return .full }
    return .partial
}

private struct WeekAttendanceStrip: View {
    let history: [String: Set<String>]
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let du = DateUtil.shared
        HStack {
            ForEach(Array((0...6).reversed().enumerated()), id: \.offset) { idx, offset in
                let off = Int32(offset)
                let dayNum = du.hoyoDayOfMonthAgo(daysAgo: off)
                let dow = du.hoyoWeekdayKoAgo(daysAgo: off)
                let count = history[du.hoyoDayKeyAgoKey(daysAgo: off)]?.count ?? 0
                let isToday = idx == 6
                let level = attendLevel(count)
                VStack(spacing: 5) {
                    Text(dow).font(.pretendard(size: 10, weight: isToday ? .bold : .regular))
                        .foregroundStyle(isToday ? accent.primary : GLGColor.textSecondary)
                    // 날짜는 **항상** 보여준다 — 예전엔 전체 출석한 날을 체크 아이콘으로 덮어버려
                    // 정작 며칠인지 알 수 없었다. 완료 표시는 채움색 + 우상단 작은 체크로 한다.
                    ZStack(alignment: .topTrailing) {
                        ZStack {
                            Circle().fill(fillColor(level))
                                .overlay(isToday ? Circle().stroke(accent.primary, lineWidth: 2) : nil)
                            Text("\(dayNum)").font(.pretendard(size: 12, weight: .bold))
                                .foregroundStyle(dayNumColor(level))
                        }
                        .frame(width: 34, height: 34)
                        if level == .full {
                            ZStack {
                                Circle().fill(Color.white)
                                Image(systemName: "checkmark").font(.pretendard(size: 8, weight: .black))
                                    .foregroundStyle(accent.primary)
                            }
                            .frame(width: 13, height: 13)
                            .offset(x: 1, y: -1)
                        }
                    }
                    .frame(width: 34, height: 34)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
    /// 날짜 숫자 색 — 채움이 진한 '전체 출석'만 흰색.
    private func dayNumColor(_ l: AttendLevel) -> Color {
        switch l {
        case .full: return .white
        case .partial: return accent.primary
        default: return GLGColor.textSecondary
        }
    }

    private func fillColor(_ l: AttendLevel) -> Color {
        switch l {
        case .full: return accent.primary
        case .partial: return accent.primary.opacity(0.30)
        case .none: return Color(hex: 0xFFF0F0F4)
        }
    }
}

private struct MonthAttendanceCalendar: View {
    let history: [String: Set<String>]
    @Environment(\.glgAccent) private var accent
    @State private var monthOffset: Int32 = 0
    var body: some View {
        let du = DateUtil.shared
        let year = du.hoyoMonthYear(monthOffset: monthOffset)
        let monthNum = du.hoyoMonthNumber(monthOffset: monthOffset)
        let firstDow = Int(du.hoyoMonthFirstDow(monthOffset: monthOffset))
        let days = Int(du.hoyoMonthDays(monthOffset: monthOffset))
        let todayKey = du.hoyoDayKey(millis: nowMs())
        let cells: [Int] = Array(repeating: 0, count: firstDow) + Array(1...days)
        let rows = stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<min($0+7, cells.count)]) }
        return VStack(spacing: 0) {
            HStack {
                Button { monthOffset -= 1 } label: { Image(systemName: "chevron.left").foregroundStyle(GLGColor.textSecondary) }.buttonStyle(.plain)
                Spacer()
                Text(verbatim: "\(year)년 \(monthNum)월").font(.pretendard(size: 15, weight: .bold))
                Spacer()
                Button { if monthOffset < 0 { monthOffset += 1 } } label: {
                    Image(systemName: "chevron.right").foregroundStyle(monthOffset < 0 ? GLGColor.textSecondary : Color(.systemGray3))
                }.buttonStyle(.plain).disabled(monthOffset >= 0)
            }
            .padding(.bottom, 12)
            HStack { ForEach(["일","월","화","수","목","금","토"], id: \.self) { Text($0).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).frame(maxWidth: .infinity) } }
            .padding(.bottom, 6)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, week in
                HStack {
                    ForEach(0..<7, id: \.self) { i in
                        if i < week.count, week[i] > 0 {
                            let day = week[i]
                            let key = String(format: "%04d-%02d-%02d", year, monthNum, day)
                            let level = attendLevel(history[key]?.count ?? 0)
                            let isToday = key == todayKey
                            Text("\(day)").font(.pretendard(size: 12, weight: level != .none || isToday ? .bold : .regular))
                                .foregroundStyle(dayColor(level, isToday))
                                .frame(width: 32, height: 32)
                                .background(bgColor(level), in: Circle())
                                .overlay(isToday ? Circle().stroke(accent.primary, lineWidth: 1.5) : nil)
                                .frame(maxWidth: .infinity)
                        } else {
                            Color.clear.frame(maxWidth: .infinity, minHeight: 32)
                        }
                    }
                }
                .padding(.vertical, 2)
            }
            HStack(spacing: 12) {
                legendDot(accent.primary, "전체 출석")
                legendDot(accent.primary.opacity(0.30), "일부")
                Spacer()
            }
            .padding(.top, 12)
        }
        .padding(14)
        .background(Color(hex: 0xFFF7F8FA), in: RoundedRectangle(cornerRadius: 16))
    }
    private func dayColor(_ l: AttendLevel, _ today: Bool) -> Color {
        if l == .full { return .white }
        if l == .partial || today { return accent.primary }
        return GLGColor.textSecondary
    }
    private func bgColor(_ l: AttendLevel) -> Color {
        switch l { case .full: return accent.primary; case .partial: return accent.primary.opacity(0.30); case .none: return .clear }
    }
    private func legendDot(_ c: Color, _ label: String) -> some View {
        HStack(spacing: 5) { Circle().fill(c).frame(width: 12, height: 12); Text(label).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary) }
    }
}

private struct DailyGameRow: View {
    let game: Game
    let note: LiveNote?
    let uid: String
    let checked: Bool
    let inProgress: Bool
    let onCheckIn: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Text(game.abbr).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(Color(argb64: game.color))
                    .frame(width: 40, height: 40)
                    .background(Color(argb64: game.color).opacity(0.15), in: RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 2) {
                    Text(game.shortName).font(.pretendard(size: 14, weight: .bold))
                    if let n = note, n.maxResin > 0 {
                        HStack(spacing: 3) {
                            Image(systemName: "bolt.fill").font(.pretendard(size: 11)).foregroundStyle(accent.primary)
                            Text(verbatim: "\(n.resinLabel) \(n.currentResin)/\(n.maxResin)").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                            if !n.resinRecoveryTime.isEmpty {
                                Text("· \(n.resinRecoveryTime)").font(.pretendard(size: 11)).foregroundStyle(Color(.systemGray3)).lineLimit(1)
                            }
                        }
                    } else {
                        Text(uid.isEmpty ? "UID 미등록 — 설정에서 등록하세요" : "실시간 노트 동기화 중…")
                            .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    }
                }
                Spacer(minLength: 8)
                checkInControl
            }
            if let n = note, n.maxResin > 0 {
                ProgressView(value: Double(n.resinRatio)).tint(accent.primary).padding(.top, 8)
                if !n.extras.isEmpty {
                    // 칩이 한 줄에 안 들어가면 텍스트를 쪼개지 말고 칩 단위로 다음 줄로 흘린다.
                    FlowLayout(spacing: 6, lineSpacing: 6) {
                        ForEach(Array(n.extras.enumerated()), id: \.offset) { _, e in noteChip(e) }
                    }
                    .padding(.top, 8)
                }
            }
        }
        .padding(.vertical, 10)
    }

    @ViewBuilder private var checkInControl: some View {
        if inProgress {
            HStack(spacing: 6) { ProgressView().controlSize(.mini).tint(accent.primary); Text("처리 중").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
        } else if checked {
            HStack(spacing: 4) { Image(systemName: "checkmark.circle.fill").font(.pretendard(size: 18)).foregroundStyle(accent.primary); Text("완료").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary) }
        } else {
            Button(action: onCheckIn) {
                Text("출석").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                    .padding(.horizontal, 16).padding(.vertical, 7)
                    .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }

    private func noteChip(_ stat: NoteStat) -> some View {
        HStack(spacing: 4) {
            Text(stat.label).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
            Text(stat.value).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(stat.highlight ? accent.primary : GLGColor.textPrimary)
        }
        .lineLimit(1)
        .fixedSize()  // 칩 내부 텍스트는 줄바꿈 없이 고유 너비 유지 — 줄바꿈은 FlowLayout 이 칩 단위로 처리
        .padding(.horizontal, 8).padding(.vertical, 4)
        .background(stat.highlight ? accent.primary.opacity(0.14) : Color(hex: 0xFFF2F2F6), in: RoundedRectangle(cornerRadius: 8))
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 칩/태그를 가로로 채우다 넘치면 다음 줄로 흘려보내는 플로우 레이아웃.
// (HStack 은 넘칠 때 자식 텍스트를 줄바꿈해 깨지므로, 칩 단위 래핑이 필요할 때 사용)
// ════════════════════════════════════════════════════════════════════════════
struct FlowLayout: Layout {
    var spacing: CGFloat = 6      // 같은 줄 칩 사이 가로 간격
    var lineSpacing: CGFloat = 6  // 줄 사이 세로 간격

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x > 0, x + size.width > maxWidth {
                x = 0; y += rowHeight + lineSpacing; rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            maxX = max(maxX, x - spacing)
        }
        return CGSize(width: maxWidth.isFinite ? maxWidth : maxX, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        var x: CGFloat = bounds.minX, y: CGFloat = bounds.minY, rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX; y += rowHeight + lineSpacing; rowHeight = 0
            }
            sub.place(at: CGPoint(x: x, y: y), anchor: .topLeading, proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

/// 전투 진행도·수입 일지 진입 행 — 데일리 바로 아래.
///
/// 예전엔 게임 정보 탭 본문에 큰 섹션 두 개로 펼쳐져 있었다. 매일 보는 정보가 아닌데
/// 화면을 길게 잡아먹어, 같은 '오늘 뭐 했나' 맥락인 데일리에서 들어가도록 접었다.
private struct GameContentEntry: View {
    let onTap: () -> Void
    /// 클리어 편성으로 — 같은 카드의 두 번째 줄. nil 이면 줄 자체가 안 뜬다.
    var onTapClears: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            GameContentRow(icon: "medal", title: "전투 진행도 · 수입 일지",
                           sub: "주간 클리어 현황과 이번 달 재화 수입", onTap: onTap)
            // 클리어 편성은 예전에 이 페이지 안쪽 2단계라 못 찾았다. 같은 카드의 두 번째 줄로 꺼낸다 —
            // 별도 카드로 띄우면 같은 맥락의 진입점이 화면에서 갈라진다.
            if let onTapClears {
                Divider().padding(.horizontal, 16)
                GameContentRow(icon: "person.3.fill", title: "클리어 편성",
                               sub: "나선 비경 · 혼돈의 기억을 깬 캐릭터", onTap: onTapClears)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// `GameContentEntry` 의 한 줄. 카드를 공유하므로 탭 영역은 줄 단위다.
private struct GameContentRow: View {
    let icon: String
    let title: String
    let sub: String
    let onTap: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous).fill(accent.primary.opacity(0.12))
                    Image(systemName: icon).font(.pretendard(size: 18, weight: .semibold))
                        .foregroundStyle(accent.primary)
                }
                .frame(width: 38, height: 38)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    Text(sub)
                        .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                    .foregroundStyle(GLGColor.textSecondary)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
