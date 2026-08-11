import SwiftUI
import Shared

// 데일리 히어로 2.0 — 급한 하나가 지면을 지배하고, 나머지는 아래로.
// (Compose DailyHeroSection 대응) 판단 규칙은 전부 공유 DailyLogic 에 있다.
//
// 1.0 은 "지금 상태가 어떤가"에 답했다 — 게임 셋의 행동력·출석을 같은 무게로 늘어놓고,
// 무엇부터 할지는 사용자가 세 줄을 읽고 판단하게 했다. 2.0 은 판단을 앞으로 당긴다.

struct DailyHeroSection: View {
    var store: SpendingStore
    let onConfig: () -> Void
    /// 출석 상세로 — 기록·달력은 매일 볼 게 아니라 페이지로 뺐다.
    var onOpenAttendance: () -> Void = {}
    var onOpenGameContent: (() -> Void)? = nil
    var onOpenClears: (() -> Void)? = nil
    @Environment(\.glgAccent) private var accent

    private var tasks: [DailyTask] {
        DailyLogic.shared.tasks(notes: store.liveNotes, attendanceToday: Set(store.attendanceToday), nowMillis: nowMs())
    }

    /// 출석 집계 — 숫자는 공유 로직이 만든다(두 플랫폼이 각자 세면 값이 갈린다).
    /// `todayKey` 는 기본 인자가 Swift 로 안 넘어와 직접 넘긴다.
    private var attendanceSummary: AttendanceSummary {
        AttendanceLogic.shared.summary(history: store.attendanceHistory,
                                       today: Set(store.attendanceToday),
                                       streak: Int32(store.attendanceStreak),
                                       todayKey: DateUtil.shared.hoyoDayKey(millis: nowMs()))
    }

    var body: some View {
        if !store.hoyolabConfig.isLinked {
            linkPrompt
        } else {
            let list = tasks
            let headline = DailyLogic.shared.headline(tasks: list)
            // 행동력은 위 카드가 전담 — 목록에는 일일·주간·출석만, 그것도 게임당 한 줄로 묶는다.
            let grouped = DailyLogic.shared.byGame(tasks: list, stats: store.taskStats)
            // 행동력 카드는 3게임을 나란히 놓고 비교하는 게 쓸모다 — 게임을 골라 좁히지 않는다.
            let summaries = DailyLogic.shared
                .summaries(notes: store.liveNotes, attendanceToday: Set(store.attendanceToday), tasks: list)

            VStack(alignment: .leading, spacing: 0) {
                headlineHero(headline)

                VStack(alignment: .leading, spacing: 12) {
                    resinCard(summaries)
                    if !grouped.isEmpty {
                        GLGCard(cornerRadius: 20, padding: 16) {
                            VStack(alignment: .leading, spacing: 0) {
                                Text("오늘 할 일")
                                    .font(.pretendard(size: 12, weight: .bold))
                                    .foregroundStyle(GLGColor.textSecondary)
                                    .padding(.bottom, 4)
                                ForEach(Array(grouped.enumerated()), id: \.offset) { i, g in
                                    if i > 0 { Divider() }
                                    gameTaskRow(g)
                                }
                            }
                        }
                    }
                    // 출석 · 전투 진행도 · 클리어 편성 — 한 줄 3칸.
                    // 셋 다 '들어가서 보는 기록'이라 성격이 같은데, 예전엔 접히는 카드 하나와
                    // 두 줄짜리 카드 하나로 갈라져 세로로 세 덩어리를 잡아먹고 있었다.
                    DailyEntryTiles(summary: attendanceSummary,
                                    checkingIn: store.checkingIn,
                                    onCheckInAll: { store.checkInAll() },
                                    onOpenAttendance: onOpenAttendance,
                                    onOpenGameContent: onOpenGameContent,
                                    onOpenClears: onOpenClears)
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

    /// 히어로 — 색면도 없고, **게임에도 치우치지 않는다.**
    ///
    /// 예전엔 가장 급한 한 건(주로 원신 레진)을 크게 올렸다. 데일리는 3게임을 함께 관리하는
    /// 화면이라 한 게임이 제목을 차지하면 편향돼 보인다 — 히어로는 "오늘 전체"만 말하고,
    /// 어느 게임의 무엇인지는 아래 목록이 맡는다.
    @ViewBuilder
    private func headlineHero(_ h: DailyHeadline) -> some View {
        let mark = h.urgent ? Color(hex: 0xFFD0021B) : accent.primary
        heroFrame {
            heroKicker("오늘의 데일리", mark)
            Text(h.title)
                .font(.pretendard(size: 27, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .tracking(-0.7)
                .padding(.top, 12)
            if !h.subtitle.isEmpty {
                Text(h.subtitle).font(.pretendard(size: 13))
                    .foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
            }
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

    /// 행동력 카드 — 3게임을 **한 카드에 나란히**. 세로로 쌓으면 세 덩어리로 읽힌다.
    @ViewBuilder
    private func resinCard(_ items: [DailyGameSummary]) -> some View {
        GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                Text("행동력").font(.pretendard(size: 12, weight: .bold))
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

    /// 게임 하나의 남은 할 일 — 한 줄.
    ///
    /// 낱개로 늘어놓으면 3게임 × 최대 4종이라 목록이 금세 열 줄을 넘는다.
    /// 게임당 한 줄로 묶으면 세 줄로 끝난다.
    @ViewBuilder
    private func gameTaskRow(_ g: DailyGameTasks) -> some View {
        HStack(spacing: 10) {
            Capsule().fill(Color(argb64: g.colorArgb)).frame(width: 3, height: 16)
            Text(g.gameShort).font(.pretendard(size: 13, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
            Text(g.summary).font(.pretendard(size: 12.5))
                .foregroundStyle(GLGColor.textSecondary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            // 완주율 — 기록이 없으면(-1) 아예 안 쓴다. 근거 없는 퍼센트를 띄우지 않는다.
            if g.rate >= 0 {
                Text("\(g.rate)%").font(.pretendard(size: 12, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary)
            }
            if g.canCheckIn {
                if store.checkingIn == g.gameKey {
                    ProgressView().controlSize(.small)
                } else {
                    Button { store.attemptCheckIn(g.gameKey) } label: {
                        Text("출석").font(.pretendard(size: 11.5, weight: .bold))
                            .foregroundStyle(accent.primary)
                            .padding(.horizontal, 14).padding(.vertical, 7)
                            .background(accent.primary.opacity(0.14),
                                        in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    }.buttonStyle(.plain)
                }
            }
        }
        .padding(.vertical, 13)
    }

    // ── 미연동 안내 — 좌측 정렬(중앙정렬 4단 스택은 빈 상태의 기본 슬롭이다) ──
    private var linkPrompt: some View {
        heroFrame {
            heroKicker("오늘의 데일리", accent.primary)
            Text("HoYoLAB 을 연동해 주세요")
                .font(.pretendard(size: 25, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary).padding(.top, 12)
            Text("연동하면 행동력·일일 숙제·출석을 한곳에서 볼 수 있어요.")
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

/**
 출석 · 전투 진행도 · 클리어 편성 — 한 줄 3칸.

 셋 다 "들어가서 보는 기록"이라 성격이 같다. 예전엔 출석이 접히는 카드, 나머지 둘이
 두 줄짜리 카드로 갈라져 있어 같은 부류가 세로로 세 덩어리를 잡아먹었다.

 타일 안에 다시 버튼을 넣으므로 **탭 영역을 겹치지 않게** 나눈다 — 진입은 위쪽 본문,
 출석은 아래 버튼. `Button` 안에 `Button` 을 넣으면 SwiftUI 는 바깥 것만 먹인다.
 */
private struct DailyEntryTiles: View {
    let summary: AttendanceSummary
    let checkingIn: String?
    let onCheckInAll: () -> Void
    let onOpenAttendance: () -> Void
    var onOpenGameContent: (() -> Void)? = nil
    var onOpenClears: (() -> Void)? = nil

    var body: some View {
        // 타일 높이를 서로 맞춘다 — 출석 타일만 버튼이 붙어 길어지면 세 칸이 어긋나 보인다.
        HStack(alignment: .top, spacing: 10) {
            EntryTile(icon: "calendar.badge.checkmark",
                      title: "출석 체크",
                      value: "\(summary.todayDone)/\(summary.todayTotal)",
                      sub: summary.allDone ? "오늘 완료" : "\(summary.pending)개 남음",
                      highlight: !summary.allDone,
                      onTap: onOpenAttendance) {
                // 아직 안 한 게 있으면 여기서 바로 끝낸다 — 상세까지 들어갔다 나올 일이 아니다.
                if !summary.allDone {
                    TileButton(label: summary.pending == 1 ? "출석" : "전체 출석",
                               inProgress: checkingIn != nil,
                               onTap: onCheckInAll)
                }
            }
            if let onOpenGameContent {
                EntryTile(icon: "medal", title: "전투 진행도", value: "주간", sub: "수입 일지",
                          onTap: onOpenGameContent) { EmptyView() }
            }
            if let onOpenClears {
                EntryTile(icon: "person.3.fill", title: "클리어 편성", value: "편성", sub: "나선 · 혼돈",
                          onTap: onOpenClears) { EmptyView() }
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// 3칸 타일 하나 — 아이콘 · 제목 · 값 · 부제, 그 아래 `action` 슬롯(출석 버튼).
private struct EntryTile<Action: View>: View {
    let icon: String
    let title: String
    let value: String
    let sub: String
    var highlight: Bool = false
    let onTap: () -> Void
    @ViewBuilder let action: () -> Action
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let mark = highlight ? GLGColor.dangerText : accent.primary
        VStack(alignment: .center, spacing: 0) {
            // 진입 영역 — 아래 버튼과 겹치지 않도록 여기까지만 탭을 받는다.
            // 가운데 정렬 — 타일이 좁아 글자 길이가 제각각이라, 좌측 정렬이면 세 칸의
            // 글자가 서로 다른 지점에서 끝나 줄이 삐뚤어져 보인다.
            Button(action: onTap) {
                VStack(alignment: .center, spacing: 0) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10, style: .continuous).fill(mark.opacity(0.12))
                        Image(systemName: icon).font(.pretendard(size: 15, weight: .semibold)).foregroundStyle(mark)
                    }
                    .frame(width: 30, height: 30)
                    Text(title).font(.pretendard(size: 11.5, weight: .bold))
                        .foregroundStyle(GLGColor.textSecondary).lineLimit(1).padding(.top, 9)
                    Text(value).font(.pretendard(size: 15, weight: .bold))
                        .foregroundStyle(highlight ? GLGColor.dangerText : GLGColor.textPrimary)
                        .lineLimit(1).padding(.top, 4)
                    Text(sub).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
                        .lineLimit(1).minimumScaleFactor(0.85).multilineTextAlignment(.center).padding(.top, 2)
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 10).padding(.top, 13).padding(.bottom, 11)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            action().padding(.horizontal, 10).padding(.bottom, 12)
        }
        .frame(maxWidth: .infinity)
        .glgGlass(in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

/// 타일 바닥에 붙는 작은 채움 버튼.
private struct TileButton: View {
    let label: String
    let inProgress: Bool
    let onTap: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        Button(action: onTap) {
            Group {
                if inProgress { ProgressView().controlSize(.mini).tint(.white) }
                else { Text(label).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(.white).lineLimit(1) }
            }
            .frame(maxWidth: .infinity).padding(.vertical, 7)
            .background(accent.primary, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(inProgress)
    }
}

// ============================================================ 출석 상세 페이지
/**
 출석 체크 상세 — 오늘 상태 · 게임별 · 최근 7일 · 월 달력.

 예전엔 데일리의 접히는 카드 안에 7일 스트립과 달력만 있었다. 그 안에서 할 수 있는 건
 '전체 출석' 하나뿐이라, 한 게임만 빠졌을 때도 세 게임을 통째로 다시 돌려야 했다.
 페이지로 꺼내면서 **게임별 줄에 각자 버튼**을 달고, 이번 달 누계를 함께 보여준다.
 */
struct AttendanceDetailView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var summary: AttendanceSummary {
        AttendanceLogic.shared.summary(history: store.attendanceHistory,
                                       today: Set(store.attendanceToday),
                                       streak: Int32(store.attendanceStreak),
                                       todayKey: DateUtil.shared.hoyoDayKey(millis: nowMs()))
    }

    var body: some View {
        let s = summary
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                todayCard(s)
                GLGCard(cornerRadius: 20, padding: 16) {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(s.games.enumerated()), id: \.offset) { i, g in
                            if i > 0 { Divider() }
                            gameRow(g, elapsed: Int(s.monthElapsedDays))
                        }
                    }
                }
                GLGCard(cornerRadius: 20, padding: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("최근 7일").font(.pretendard(size: 12, weight: .bold))
                            .foregroundStyle(GLGColor.textSecondary)
                        WeekAttendanceStrip(history: store.attendanceHistory)
                        MonthAttendanceCalendar(history: store.attendanceHistory)
                    }
                }
            }
            .padding(16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("출석 체크")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// 오늘 요약 — 큰 숫자 + 연속·이번 달.
    @ViewBuilder
    private func todayCard(_ s: AttendanceSummary) -> some View {
        GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                Text("오늘").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                HStack(alignment: .bottom, spacing: 0) {
                    Text("\(s.todayDone)").font(.pretendard(size: 34, weight: .bold))
                        .foregroundStyle(s.allDone ? accent.primary : GLGColor.dangerText)
                    Text(" / \(s.todayTotal) 게임").font(.pretendard(size: 14, weight: .bold))
                        .foregroundStyle(GLGColor.textSecondary).padding(.bottom, 5)
                    Spacer(minLength: 8)
                    if !s.allDone {
                        Button { store.checkInAll() } label: {
                            Group {
                                if store.checkingIn != nil { ProgressView().controlSize(.small).tint(.white) }
                                else { Text("전체 출석").font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(.white) }
                            }
                            .padding(.horizontal, 16).padding(.vertical, 9)
                            .background(accent.primary, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .disabled(store.checkingIn != nil)
                    }
                }
                .padding(.top, 10)
                HStack(spacing: 10) {
                    statBox("연속 기록", s.streak > 0 ? "\(s.streak)일" : "—")
                    statBox("이번 달 전체 출석", "\(s.monthFullDays)일 / \(s.monthElapsedDays)일")
                }
                .padding(.top, 14)
            }
        }
    }

    @ViewBuilder
    private func statBox(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            Text(value).font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Color(hex: 0xFFF7F8FA), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    /// 게임 한 줄 — 오늘 상태 + 이번 달 누계, 안 했으면 그 자리에서 출석.
    @ViewBuilder
    private func gameRow(_ g: AttendanceGameStat, elapsed: Int) -> some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 2).fill(Color(argb64: g.colorArgb)).frame(width: 3, height: 20)
            VStack(alignment: .leading, spacing: 2) {
                Text(g.gameShort).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("이번 달 \(g.monthCount)일" + (elapsed > 0 ? " / \(elapsed)일" : ""))
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            Spacer(minLength: 8)
            if store.checkingIn == g.gameKey {
                ProgressView().controlSize(.mini).tint(accent.primary)
            } else if g.checkedToday {
                HStack(spacing: 5) {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 15)).foregroundStyle(accent.primary)
                    Text("완료").font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(accent.primary)
                }
            } else {
                Button { store.attemptCheckIn(g.gameKey) } label: {
                    Text("출석").font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(accent.primary)
                        .padding(.horizontal, 14).padding(.vertical, 7)
                        .background(accent.primary.opacity(0.14),
                                    in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 13)
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
