import SwiftUI
import Shared

// 데일리 히어로 — 실시간 노트(레진/개척력/배터리) + 출석체크(7일 스트립 + 월 달력 + 게임별).
// (Compose DailyHeroSection 대응) HoYoLAB 미연동 시 연동 유도 카드.
struct DailyHeroSection: View {
    @ObservedObject var store: SpendingStore
    var filter: String = "all"   // "all" | game.key — Segmented 세그먼트 선택값
    let onConfig: () -> Void
    @Environment(\.glgAccent) private var accent
    @State private var expanded = false

    private var attendanceGames: [Game] { GameData.shared.attendanceGames }
    // 세그먼트로 특정 게임이 선택되면 그 게임만, "all"이면 전체.
    private var shownGames: [Game] {
        filter == "all" ? attendanceGames : attendanceGames.filter { $0.key == filter }
    }
    private var pendingCount: Int { attendanceGames.filter { !store.attendanceToday.contains($0.key) }.count }

    var body: some View {
        if !store.hoyolabConfig.isLinked {
            linkPrompt
        } else if let game = attendanceGames.first(where: { $0.key == filter }) {
            // Segmented — 특정 게임 선택: 목업 2번 지면(게임색 테두리 노트 카드 + 별도 출석 카드)
            focusedGame(game)
        } else {
            // 전체 모드 — 요약 카드 + 게임별 개별 카드 분리 (재디자인)
            VStack(alignment: .leading, spacing: 16) {
                // 요약 카드: 연속·전체출석 + 최근 출석 스트립
                GLGCard(cornerRadius: 20, padding: 16) {
                    VStack(alignment: .leading, spacing: 0) {
                        headerRow
                        Spacer().frame(height: 14)
                        attendanceHeader
                        Spacer().frame(height: 10)
                        WeekAttendanceStrip(history: store.attendanceHistory)
                        if expanded {
                            Spacer().frame(height: 14)
                            MonthAttendanceCalendar(history: store.attendanceHistory)
                        }
                    }
                }
                // 게임별 카드: 실시간 노트 + 출석 (게임당 한 장)
                ForEach(Array(attendanceGames.enumerated()), id: \.offset) { _, game in
                    GLGCard(cornerRadius: 20, padding: 16) {
                        DailyGameRow(game: game,
                                     note: store.liveNotes.first { GameData.shared.byNameOrNull(name: $0.game)?.key == game.key },
                                     uid: uid(for: game.key),
                                     checked: store.attendanceToday.contains(game.key),
                                     inProgress: store.checkingIn == game.key) {
                            store.attemptCheckIn(game.key)
                        }
                    }
                }
            }
        }
    }

    // ── Segmented 선택-게임 지면 (목업 2번) ──
    @ViewBuilder
    private func focusedGame(_ game: Game) -> some View {
        let note = store.liveNotes.first { GameData.shared.byNameOrNull(name: $0.game)?.key == game.key }
        let checked = store.attendanceToday.contains(game.key)
        let inProgress = store.checkingIn == game.key
        let gameColor = Color(argb64: game.color)
        VStack(alignment: .leading, spacing: 16) {
            // 실시간 노트 카드 — 게임색 테두리
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        HStack(spacing: 7) {
                            Circle().fill(gameColor).frame(width: 8, height: 8)
                            Text(game.shortName).font(.system(size: 16, weight: .bold)).foregroundStyle(gameColor)
                        }
                        Spacer()
                        focusedCheckControl(game.key, checked: checked, inProgress: inProgress)
                    }
                    Spacer().frame(height: 12)
                    Divider()
                    Spacer().frame(height: 12)
                    Text("실시간 노트").font(.system(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    Spacer().frame(height: 8)
                    if let n = note, n.maxResin > 0 {
                        HStack(alignment: .firstTextBaseline, spacing: 5) {
                            Text("\(n.currentResin)").font(.system(size: 30, weight: .bold))
                            Text("/ \(n.maxResin) \(n.resinLabel)").font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        }
                        if !n.resinRecoveryTime.isEmpty {
                            HStack(spacing: 3) {
                                Image(systemName: "bolt.fill").font(.system(size: 11)).foregroundStyle(accent.primary)
                                Text("\(n.resinRecoveryTime) 후 가득 참").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                            }
                            .padding(.top, 2)
                        }
                        ProgressView(value: Double(n.resinRatio)).tint(gameColor).padding(.top, 10)
                        if !n.extras.isEmpty {
                            FlowLayout(spacing: 6, lineSpacing: 6) {
                                ForEach(Array(n.extras.enumerated()), id: \.offset) { _, e in focusedNoteChip(e) }
                            }
                            .padding(.top, 10)
                        }
                    } else {
                        Text(uid(for: game.key).isEmpty ? "UID 미등록 — 설정에서 등록하세요" : "실시간 노트 동기화 중…")
                            .font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    }
                }
            }
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(gameColor.opacity(0.4), lineWidth: 1.5))

            // 출석 카드
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text("\(game.shortName) 출석").font(.system(size: 16, weight: .bold))
                        Spacer()
                        Button { withAnimation { expanded.toggle() } } label: {
                            HStack(spacing: 2) {
                                Text(expanded ? "접기" : "한 달 보기").font(.system(size: 11, weight: .bold))
                                Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.system(size: 11))
                            }
                            .foregroundStyle(accent.primary)
                        }
                        .buttonStyle(.plain)
                    }
                    Spacer().frame(height: 12)
                    WeekAttendanceStrip(history: store.attendanceHistory)
                    if expanded {
                        Spacer().frame(height: 14)
                        MonthAttendanceCalendar(history: store.attendanceHistory)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func focusedCheckControl(_ key: String, checked: Bool, inProgress: Bool) -> some View {
        if inProgress {
            HStack(spacing: 6) { ProgressView().controlSize(.mini).tint(accent.primary); Text("처리 중").font(.system(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
        } else if checked {
            HStack(spacing: 4) { Image(systemName: "checkmark.circle.fill").font(.system(size: 16)).foregroundStyle(accent.primary); Text("출석완료").font(.system(size: 12, weight: .bold)).foregroundStyle(accent.primary) }
        } else {
            Button { store.attemptCheckIn(key) } label: {
                Text("출석").font(.system(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                    .padding(.horizontal, 16).padding(.vertical, 7)
                    .background(accent.primary.opacity(0.12), in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private func focusedNoteChip(_ stat: NoteStat) -> some View {
        HStack(spacing: 4) {
            Text(stat.label).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary)
            Text(stat.value).font(.system(size: 11, weight: .bold)).foregroundStyle(stat.highlight ? accent.primary : GLGColor.textPrimary)
        }
        .lineLimit(1).fixedSize()
        .padding(.horizontal, 8).padding(.vertical, 4)
        .background(stat.highlight ? accent.primary.opacity(0.14) : Color(hex: 0xFFF2F2F6), in: RoundedRectangle(cornerRadius: 8))
    }

    private func uid(for key: String) -> String {
        switch key {
        case "genshin": return store.hoyolabConfig.genshinUid
        case "hsr": return store.hoyolabConfig.hsrUid
        case "zzz": return store.hoyolabConfig.zzzUid
        default: return ""
        }
    }

    private var headerRow: some View {
        HStack {
            HStack(spacing: 6) {
                Image(systemName: "bolt.fill").font(.system(size: 16)).foregroundStyle(accent.primary)
                Text("오늘의 데일리").font(.system(size: 16, weight: .bold)).lineLimit(1)
                if store.attendanceStreak > 0 {
                    Text("🔥 \(store.attendanceStreak)일 연속").font(.system(size: 11, weight: .bold))
                        .foregroundStyle(accent.primary)
                        .padding(.horizontal, 7).padding(.vertical, 2)
                        .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                }
            }
            Spacer()
            if pendingCount > 0 {
                Button { store.checkInAll() } label: {
                    HStack(spacing: 5) {
                        if store.checkingIn != nil {
                            ProgressView().controlSize(.mini).tint(accent.primary)
                        } else {
                            Image(systemName: "checkmark.circle").font(.system(size: 14))
                        }
                        Text("전체 출석").font(.system(size: 12, weight: .bold))
                    }
                    .foregroundStyle(accent.primary)
                    .padding(.horizontal, 11).padding(.vertical, 6)
                    .background(accent.primary.opacity(0.12), in: Capsule())
                }
                .buttonStyle(.plain).disabled(store.checkingIn != nil)
            }
        }
    }

    private var attendanceHeader: some View {
        HStack {
            Text("최근 출석").font(.system(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            Spacer()
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 2) {
                    Text(expanded ? "접기" : "한 달 보기").font(.system(size: 11, weight: .bold))
                    Image(systemName: expanded ? "chevron.up" : "chevron.down").font(.system(size: 11))
                }
                .foregroundStyle(accent.primary)
            }
            .buttonStyle(.plain)
        }
    }

    private var linkPrompt: some View {
        GLGCard(cornerRadius: 24, padding: 24) {
            VStack(spacing: 0) {
                ZStack {
                    Circle().fill(accent.primary.opacity(0.12)).frame(width: 56, height: 56)
                    Image(systemName: "link").font(.system(size: 26)).foregroundStyle(accent.primary)
                }
                Text("HoYoLAB 연동이 필요해요").font(.system(size: 16, weight: .bold)).padding(.top, 12)
                Text("연동하면 실시간 노트(레진·개척력·배터리)와\n출석체크를 한곳에서 관리할 수 있어요.")
                    .font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .multilineTextAlignment(.center).padding(.top, 6)
                GLGButton(title: "HoYoLAB 연동하기", action: onConfig).padding(.top, 18)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// 출석 완료도
enum AttendLevel { case none, partial, full }
func attendLevel(_ count: Int) -> AttendLevel {
    let total = Int(GameData.shared.attendanceGames.count)
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
                    Text(dow).font(.system(size: 10, weight: isToday ? .bold : .regular))
                        .foregroundStyle(isToday ? accent.primary : GLGColor.textSecondary)
                    ZStack {
                        Circle().fill(fillColor(level))
                            .frame(width: 34, height: 34)
                            .overlay(isToday ? Circle().stroke(accent.primary, lineWidth: 2) : nil)
                        if level == .full {
                            Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                        } else {
                            Text("\(dayNum)").font(.system(size: 12, weight: .bold))
                                .foregroundStyle(level == .partial ? accent.primary : GLGColor.textSecondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
            }
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
                Text("\(year)년 \(monthNum)월").font(.system(size: 15, weight: .bold))
                Spacer()
                Button { if monthOffset < 0 { monthOffset += 1 } } label: {
                    Image(systemName: "chevron.right").foregroundStyle(monthOffset < 0 ? GLGColor.textSecondary : Color(.systemGray3))
                }.buttonStyle(.plain).disabled(monthOffset >= 0)
            }
            .padding(.bottom, 12)
            HStack { ForEach(["일","월","화","수","목","금","토"], id: \.self) { Text($0).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).frame(maxWidth: .infinity) } }
            .padding(.bottom, 6)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, week in
                HStack {
                    ForEach(0..<7, id: \.self) { i in
                        if i < week.count, week[i] > 0 {
                            let day = week[i]
                            let key = String(format: "%04d-%02d-%02d", year, monthNum, day)
                            let level = attendLevel(history[key]?.count ?? 0)
                            let isToday = key == todayKey
                            Text("\(day)").font(.system(size: 12, weight: level != .none || isToday ? .bold : .regular))
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
        HStack(spacing: 5) { Circle().fill(c).frame(width: 12, height: 12); Text(label).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary) }
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
                Text(game.abbr).font(.system(size: 11, weight: .bold)).foregroundStyle(Color(argb64: game.color))
                    .frame(width: 40, height: 40)
                    .background(Color(argb64: game.color).opacity(0.15), in: RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 2) {
                    Text(game.shortName).font(.system(size: 14, weight: .bold))
                    if let n = note, n.maxResin > 0 {
                        HStack(spacing: 3) {
                            Image(systemName: "bolt.fill").font(.system(size: 11)).foregroundStyle(accent.primary)
                            Text("\(n.resinLabel) \(n.currentResin)/\(n.maxResin)").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                            if !n.resinRecoveryTime.isEmpty {
                                Text("· \(n.resinRecoveryTime)").font(.system(size: 11)).foregroundStyle(Color(.systemGray3)).lineLimit(1)
                            }
                        }
                    } else {
                        Text(uid.isEmpty ? "UID 미등록 — 설정에서 등록하세요" : "실시간 노트 동기화 중…")
                            .font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
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
            HStack(spacing: 6) { ProgressView().controlSize(.mini).tint(accent.primary); Text("처리 중").font(.system(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
        } else if checked {
            HStack(spacing: 4) { Image(systemName: "checkmark.circle.fill").font(.system(size: 18)).foregroundStyle(accent.primary); Text("완료").font(.system(size: 12, weight: .bold)).foregroundStyle(accent.primary) }
        } else {
            Button(action: onCheckIn) {
                Text("출석").font(.system(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                    .padding(.horizontal, 16).padding(.vertical, 7)
                    .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }

    private func noteChip(_ stat: NoteStat) -> some View {
        HStack(spacing: 4) {
            Text(stat.label).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary)
            Text(stat.value).font(.system(size: 11, weight: .bold)).foregroundStyle(stat.highlight ? accent.primary : GLGColor.textPrimary)
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
