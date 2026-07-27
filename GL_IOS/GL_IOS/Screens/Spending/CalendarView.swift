import SwiftUI
import Shared

// 통합 캘린더 — **타임라인 형식**. 활동(지출·픽업 배너 시작/종료)이 있는 날은 노드로, 활동 없는 연속 구간은
// 하나의 '활동 없음' 노드로 묶어 노출. (월 그리드 → 타임라인 대개편, Compose CalendarScreen 대응)
struct CalendarView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var year = 0
    @State private var month = 0

    private var y: Int { year == 0 ? store.displayYear : year }
    private var m: Int { month == 0 ? store.displayMonth : month }

    var body: some View {
        let entries = buildEntries(spendings: store.spendings, banners: store.activeBanners, year: y, month: m)
        let monthTotal = entries.reduce(Int64(0)) { acc, e in if case .active(let d) = e { return acc + d.spendTotal }; return acc }
        ScrollView {
            VStack(spacing: 0) {
                // 월 이동
                HStack {
                    monthNav("chevron.left") { shift(-1) }
                    Spacer()
                    Text(verbatim: "\(y)년 \(m)월").font(.pretendard(size: 18, weight: .bold))
                    Spacer()
                    monthNav("chevron.right") { shift(1) }
                }
                .padding(.vertical, 4)
                // 요약(총 지출)
                summaryPill("이번 달 총 지출", won(monthTotal))
                    .padding(.top, 4).padding(.bottom, 14)
                // 타임라인
                if entries.isEmpty {
                    emptyTimeline
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(entries.enumerated()), id: \.element.id) { idx, e in
                            switch e {
                            case .active(let d):
                                TimelineDayItem(day: d, isFirst: idx == 0, isLast: idx == entries.count - 1, accent: accent.primary)
                            case .gap(let low, let high):
                                GapItem(lowDay: low, highDay: high, isLast: idx == entries.count - 1, accent: accent.primary)
                            }
                        }
                    }
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("캘린더")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func shift(_ delta: Int) {
        var c = DateComponents(); c.year = y; c.month = m + delta; c.day = 1
        if let d = Calendar.current.date(from: c) {
            let comps = Calendar.current.dateComponents([.year, .month], from: d)
            year = comps.year ?? y; month = comps.month ?? m
        }
    }

    private func monthNav(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.pretendard(size: 18)).foregroundStyle(accent.primary)
                .frame(width: 36, height: 36)
                .background(accent.primary.opacity(0.10), in: Circle())
        }
        .buttonStyle(.plain)
    }

    private func summaryPill(_ label: String, _ value: String) -> some View {
        GLGCard(cornerRadius: 16, padding: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                Text(value).font(.pretendard(size: 18, weight: .bold)).foregroundStyle(accent.primary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14).padding(.vertical, 12)
        }
    }

    private var emptyTimeline: some View {
        VStack(spacing: 6) {
            Image(systemName: "doc.text").font(.pretendard(size: 44)).foregroundStyle(Color(.systemGray3))
            Text("이번 달 활동이 없어요").font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
            Text("지출·픽업 일정이 이 타임라인에 모여요").font(.pretendard(size: 12)).foregroundStyle(Color(.systemGray3))
        }
        .frame(maxWidth: .infinity).padding(.vertical, 56)
    }
}

// ── 타임라인 한 노드 — 왼쪽 날짜/레일(점·선) + 오른쪽 그날 활동 카드 ──
private struct TimelineDayItem: View {
    let day: TimelineDay
    let isFirst: Bool
    let isLast: Bool
    let accent: Color

    private let weekdays = ["일", "월", "화", "수", "목", "금", "토"]

    var body: some View {
        let dateColor: Color = day.isToday ? accent : (day.weekdayIndex == 0 ? GLGColor.dangerText : GLGColor.textPrimary)
        HStack(alignment: .top, spacing: 0) {
            VStack(spacing: 0) {
                Text("\(day.day)").font(.pretendard(size: 18, weight: .bold)).foregroundStyle(dateColor)
                Text(weekdays[day.weekdayIndex]).font(.pretendard(size: 10)).foregroundStyle(day.isToday ? accent : GLGColor.textSecondary)
            }
            .frame(width: 34)
            .padding(.top, 2)
            Spacer().frame(width: 8)
            // 레일
            VStack(spacing: 0) {
                Rectangle().fill(isFirst ? Color.clear : GLGColor.divider).frame(width: 2, height: 10)
                Circle().fill(day.isToday ? accent : Color.white)
                    .frame(width: 12, height: 12)
                    .overlay(Circle().stroke(accent, lineWidth: 2))
                Rectangle().fill(isLast ? Color.clear : GLGColor.divider).frame(width: 2).frame(maxHeight: .infinity)
            }
            .frame(width: 16)
            Spacer().frame(width: 10)
            content.padding(.bottom, 14)
        }
        .frame(maxWidth: .infinity)
    }

    private var content: some View {
        let hasSpend = !day.spendings.isEmpty
        let hasBanner = !day.bannerStart.isEmpty || !day.bannerEnd.isEmpty
        return GLGCard(cornerRadius: 16, padding: 14) {
            VStack(alignment: .leading, spacing: 0) {
                if hasSpend {
                    HStack {
                        Text("지출 \(day.spendings.count)건").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                        Spacer()
                        Text(won(day.spendTotal)).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(accent)
                    }
                    .padding(.bottom, 8)
                    ForEach(Array(day.spendings.enumerated()), id: \.element.id) { i, sp in
                        if i > 0 { Spacer().frame(height: 6) }
                        spendLine(sp)
                    }
                }
                if hasBanner {
                    if hasSpend { sectionDivider }
                    ForEach(Array(day.bannerStart.enumerated()), id: \.offset) { _, b in bannerLine("▲", "\(b.name) 픽업 시작", b.color) }
                    ForEach(Array(day.bannerEnd.enumerated()), id: \.offset) { _, b in bannerLine("▼", "\(b.name) 픽업 종료", b.color) }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var sectionDivider: some View {
        VStack(spacing: 0) { Spacer().frame(height: 10); Divider(); Spacer().frame(height: 10) }
    }

    private func spendLine(_ sp: Spending) -> some View {
        HStack(spacing: 8) {
            Circle().fill(Color(argb64: sp.gameColor)).frame(width: 8, height: 8)
            Text([sp.gameName, sp.itemName.isEmpty ? nil : sp.itemName].compactMap { $0 }.joined(separator: " · "))
                .font(.pretendard(size: 13, weight: .medium)).lineLimit(1)
            Spacer(minLength: 8)
            Text(won(sp.amount)).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
        }
    }

    private func bannerLine(_ marker: String, _ text: String, _ color: Color) -> some View {
        HStack(spacing: 6) {
            Text(marker).font(.pretendard(size: 10, weight: .bold)).foregroundStyle(color)
            Text(text).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 1)
    }
}

// ── 활동 없는 연속 구간 노드 — 흐린 점 + "활동 없음" ──
private struct GapItem: View {
    let lowDay: Int; let highDay: Int; let isLast: Bool; let accent: Color
    private var label: String {
        lowDay == highDay ? "\(lowDay)일 · 활동 없음"
            : "\(lowDay)일–\(highDay)일 · 활동 없음 (\(highDay - lowDay + 1)일)"
    }
    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Spacer().frame(width: 34)
            Spacer().frame(width: 8)
            VStack(spacing: 0) {
                Rectangle().fill(GLGColor.divider).frame(width: 2, height: 9)
                Circle().fill(Color(.systemGray3)).frame(width: 7, height: 7)
                Rectangle().fill(isLast ? Color.clear : GLGColor.divider).frame(width: 2).frame(maxHeight: .infinity)
            }
            .frame(width: 16)
            Spacer().frame(width: 10)
            Text(label).font(.pretendard(size: 12, weight: .medium)).foregroundStyle(Color(.systemGray3))
                .padding(.top, 1).padding(.bottom, 14)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
    }
}

// ── 집계 모델 ──
private struct BannerMark { let name: String; let color: Color }

private struct TimelineDay: Identifiable {
    let id: Int
    let day: Int
    let weekdayIndex: Int
    let isToday: Bool
    let spendings: [Spending]
    let spendTotal: Int64
    let bannerStart: [BannerMark]
    let bannerEnd: [BannerMark]
}

private enum TimelineEntry: Identifiable {
    case active(TimelineDay)
    case gap(low: Int, high: Int)
    var id: String {
        switch self {
        case .active(let d): return "a\(d.day)"
        case .gap(_, let h): return "g\(h)"
        }
    }
}

/// 활동(지출·배너)이 있는 날은 노드로, 그 사이 빈 구간은 gap 으로 묶어 최신순 타임라인 엔트리 리스트로.
private func buildEntries(spendings: [Spending], banners: [GachaBanner], year: Int, month: Int) -> [TimelineEntry] {
    var spendByDay: [Int: [Spending]] = [:]
    for s in spendings {
        let c = DateMillis.comps(s.dateMillis)
        if c.year == year && c.month == month { spendByDay[c.day, default: []].append(s) }
    }
    var bannerStartByDay: [Int: [BannerMark]] = [:]
    var bannerEndByDay: [Int: [BannerMark]] = [:]
    for b in banners {
        let sc = DateMillis.comps(b.startMillis)
        if sc.year == year && sc.month == month { bannerStartByDay[sc.day, default: []].append(BannerMark(name: b.name, color: Color(argb64: b.gameColor))) }
        let ec = DateMillis.comps(b.endMillis)
        if ec.year == year && ec.month == month { bannerEndByDay[ec.day, default: []].append(BannerMark(name: b.name, color: Color(argb64: b.gameColor))) }
    }
    let activeDays = Set(spendByDay.keys).union(bannerStartByDay.keys).union(bannerEndByDay.keys).sorted(by: >)
    if activeDays.isEmpty { return [] }

    let todayComps = Calendar.current.dateComponents([.year, .month, .day], from: Date())
    func active(_ d: Int) -> TimelineDay {
        var dc = DateComponents(); dc.year = year; dc.month = month; dc.day = d
        let weekdayIndex = (Calendar.current.date(from: dc).map { Calendar.current.component(.weekday, from: $0) - 1 }) ?? 0
        let daySpendings = (spendByDay[d] ?? []).sorted { $0.amount > $1.amount }
        return TimelineDay(
            id: d, day: d, weekdayIndex: weekdayIndex,
            isToday: todayComps.year == year && todayComps.month == month && todayComps.day == d,
            spendings: daySpendings,
            spendTotal: daySpendings.reduce(Int64(0)) { $0 + $1.amount },
            bannerStart: bannerStartByDay[d] ?? [],
            bannerEnd: bannerEndByDay[d] ?? []
        )
    }

    var entries: [TimelineEntry] = []
    for (i, d) in activeDays.enumerated() {
        entries.append(.active(active(d)))
        if i < activeDays.count - 1 {
            let next = activeDays[i + 1] // d 보다 작은 다음 활동일
            if d - next > 1 { entries.append(.gap(low: next + 1, high: d - 1)) }
        }
    }
    return entries
}
