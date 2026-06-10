import SwiftUI
import Shared

// 통합 캘린더 — 월 달력에 일별 지출 합계·출석·픽업 배너 시작/종료 합성. (Compose CalendarScreen 대응)
struct CalendarView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var year = 0
    @State private var month = 0

    private var y: Int { year == 0 ? store.displayYear : year }
    private var m: Int { month == 0 ? store.displayMonth : month }

    var body: some View {
        let data = MonthData(spendings: store.spendings, attendance: store.attendanceHistory,
                             banners: store.activeBanners, year: y, month: m)
        ScrollView {
            VStack(spacing: 0) {
                // 월 이동
                HStack {
                    monthNav("chevron.left") { shift(-1) }
                    Spacer()
                    Text("\(y)년 \(m)월").font(.system(size: 18, weight: .bold))
                    Spacer()
                    monthNav("chevron.right") { shift(1) }
                }
                .padding(.vertical, 4)
                // 요약
                HStack(spacing: 10) {
                    summaryPill("총 지출", won(data.monthTotal))
                    summaryPill("출석", "\(data.attendedDays)일")
                }
                .padding(.bottom, 10)
                // 달력
                GLGCard(cornerRadius: 24, padding: 12) {
                    VStack(spacing: 4) {
                        weekdayHeader
                        CalendarGrid(year: y, month: m, data: data, accent: accent.primary)
                    }
                }
                legend.padding(.top, 14)
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
            Image(systemName: icon).font(.system(size: 18)).foregroundStyle(accent.primary)
                .frame(width: 36, height: 36)
                .background(accent.primary.opacity(0.10), in: Circle())
        }
        .buttonStyle(.plain)
    }

    private func summaryPill(_ label: String, _ value: String) -> some View {
        GLGCard(cornerRadius: 16, padding: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                Text(value).font(.system(size: 16, weight: .bold)).foregroundStyle(accent.primary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14).padding(.vertical, 10)
        }
    }

    private var weekdayHeader: some View {
        HStack(spacing: 2) {
            ForEach(Array(["일","월","화","수","목","금","토"].enumerated()), id: \.offset) { i, l in
                Text(l).font(.system(size: 11, weight: .bold))
                    .foregroundStyle(i == 0 ? GLGColor.dangerText : GLGColor.textSecondary)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            HStack(spacing: 3) { RoundedRectangle(cornerRadius: 3).fill(accent.primary.opacity(0.18)).frame(width: 10, height: 10); Text("지출").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary) }
            HStack(spacing: 3) { Circle().fill(Color(argb64: GameData.shared.colorFor(name: "원신"))).frame(width: 6, height: 6); Text("출석").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary) }
            HStack(spacing: 3) { Text("▲").font(.system(size: 9)).foregroundStyle(accent.primary); Text("배너 시작").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary) }
            HStack(spacing: 3) { Text("▼").font(.system(size: 9)).foregroundStyle(accent.primary); Text("종료").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary) }
            Spacer()
        }
        .padding(.horizontal, 4)
    }
}

/// 월 집계 — 일별 지출·출석·배너 시작/종료.
private struct MonthData {
    var spendByDay: [Int: Int64] = [:]
    var attendByDay: [Int: Set<String>] = [:]
    var bannerStartByDay: [Int: [Color]] = [:]
    var bannerEndByDay: [Int: [Color]] = [:]
    var monthTotal: Int64 = 0
    var attendedDays: Int = 0

    init(spendings: [Spending], attendance: [String: Set<String>], banners: [GachaBanner], year: Int, month: Int) {
        for s in spendings {
            let c = DateMillis.comps(s.dateMillis)
            if c.year == year && c.month == month { spendByDay[c.day, default: 0] += s.amount }
        }
        let prefix = String(format: "%04d-%02d", year, month)
        for (key, games) in attendance where key.hasPrefix(prefix) && !games.isEmpty {
            if let day = Int(key.split(separator: "-").last ?? "") { attendByDay[day] = games }
        }
        for b in banners {
            let sc = DateMillis.comps(b.startMillis)
            if sc.year == year && sc.month == month { bannerStartByDay[sc.day, default: []].append(Color(argb64: b.gameColor)) }
            let ec = DateMillis.comps(b.endMillis)
            if ec.year == year && ec.month == month { bannerEndByDay[ec.day, default: []].append(Color(argb64: b.gameColor)) }
        }
        monthTotal = spendByDay.values.reduce(0, +)
        attendedDays = attendByDay.count
    }
}

private struct CalendarGrid: View {
    let year: Int; let month: Int; let data: MonthData; let accent: Color

    var body: some View {
        let first = firstWeekdayIndex()
        let days = daysInMonth()
        var cells: [Int] = Array(repeating: 0, count: first) + Array(1...days)
        let rows = stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<min($0+7, cells.count)]) }
        let todayKey = todayKeyString()
        return VStack(spacing: 2) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, week in
                HStack(spacing: 2) {
                    ForEach(0..<7, id: \.self) { i in
                        let day = i < week.count ? week[i] : 0
                        if day > 0 {
                            DayCell(day: day, weekdayIndex: i,
                                    isToday: String(format: "%04d-%02d-%02d", year, month, day) == todayKey,
                                    spend: data.spendByDay[day] ?? 0,
                                    attended: data.attendByDay[day] ?? [],
                                    bannerStart: data.bannerStartByDay[day] ?? [],
                                    bannerEnd: data.bannerEndByDay[day] ?? [],
                                    accent: accent)
                            .frame(maxWidth: .infinity)
                        } else {
                            Color.clear.frame(maxWidth: .infinity, minHeight: 56)
                        }
                    }
                }
            }
        }
    }

    private func firstWeekdayIndex() -> Int {
        var c = DateComponents(); c.year = year; c.month = month; c.day = 1
        guard let d = Calendar.current.date(from: c) else { return 0 }
        return Calendar.current.component(.weekday, from: d) - 1 // 1=Sun → 0
    }
    private func daysInMonth() -> Int {
        var c = DateComponents(); c.year = year; c.month = month; c.day = 1
        guard let d = Calendar.current.date(from: c),
              let r = Calendar.current.range(of: .day, in: .month, for: d) else { return 30 }
        return r.count
    }
    private func todayKeyString() -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }
}

private struct DayCell: View {
    let day: Int; let weekdayIndex: Int; let isToday: Bool
    let spend: Int64; let attended: Set<String>
    let bannerStart: [Color]; let bannerEnd: [Color]
    let accent: Color

    var body: some View {
        VStack(spacing: 0) {
            // 배너 마커
            if !bannerStart.isEmpty || !bannerEnd.isEmpty {
                HStack(spacing: 1) {
                    ForEach(Array(bannerStart.prefix(2).enumerated()), id: \.offset) { _, c in Text("▲").font(.system(size: 7)).foregroundStyle(c) }
                    ForEach(Array(bannerEnd.prefix(2).enumerated()), id: \.offset) { _, c in Text("▼").font(.system(size: 7)).foregroundStyle(c) }
                }
            } else { Color.clear.frame(height: 9) }
            // 날짜
            Text("\(day)").font(.system(size: 12, weight: isToday ? .bold : .medium))
                .foregroundStyle(isToday ? .white : (weekdayIndex == 0 ? GLGColor.dangerText : GLGColor.textPrimary))
                .frame(width: 20, height: 20)
                .background(isToday ? accent : .clear, in: Circle())
            // 출석 점
            if !attended.isEmpty {
                HStack(spacing: 2) {
                    ForEach(Array(attended.prefix(4)), id: \.self) { gk in
                        Circle().fill(Color(argb64: GameData.shared.games.first { $0.key == gk }?.color ?? 0xFF8E8E93)).frame(width: 4, height: 4)
                    }
                }
                .padding(.top, 2)
            }
            // 지출
            if spend > 0 {
                Text(compactAmount(spend)).font(.system(size: 8, weight: .bold)).foregroundStyle(accent).padding(.top, 1)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 56)
        .padding(.vertical, 4).padding(.horizontal, 2)
        .background(spend > 0 ? accent.opacity(0.07) : .clear, in: RoundedRectangle(cornerRadius: 8))
    }
}
