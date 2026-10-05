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

    // 타임라인은 지출·배너·연월이 바뀔 때만 만든다.
    //
    // 예전엔 body 첫 줄에서 buildEntries 를 통째로 돌렸다. 이 함수는 지출 1건마다 DateMillis.comps 를
    // 부르므로 지출 1,000건이면 브리지·날짜 변환이 각각 수천 회였고, 그게 **body 평가마다** 반복됐다.
    // (Android CalendarScreen 은 같은 함수를 이미 remember 로 캐시하고 있었다 — 파리티 역전)
    @State private var entries: [TimelineEntry] = []

    private struct EntriesKey: Equatable {
        let spendings: [Spending]
        let banners: [GachaBanner]
        let year: Int
        let month: Int
    }

    private var entriesKey: EntriesKey {
        EntriesKey(spendings: store.spendings, banners: store.activeBanners, year: y, month: m)
    }

    var body: some View {
        let monthTotal = entries.reduce(Int64(0)) { acc, e in if case .active(let d) = e { return acc + d.spendTotal }; return acc }
        // GLDS 2.0(10/6) — 흰 바탕, 카드 없이 화면 폭 섹션 둘(월 · 총 지출 / 날짜별 활동), 사이는 띠.
        // 날짜별 활동은 헤어라인으로 나눈 목록이다. Android CalendarScreen 과 같은 수치.
        ScrollView {
            VStack(spacing: 0) {
                // 헤더 바로 아래 첫 섹션은 위 12(전투 진행도와 같다).
                GiPageSection(top: 12) {
                    // 월 이동
                    HStack {
                        monthNav("chevron.left") { shift(-1) }
                        Spacer()
                        Text(verbatim: "\(y)년 \(m)월").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Spacer()
                        monthNav("chevron.right") { shift(1) }
                    }
                    .padding(.bottom, 16)
                    // 월 요약(총 지출) — 줄 제목 15 + 값 15 Bold
                    HStack {
                        Text("이번 달 총 지출").font(.pretendard(size: 15)).foregroundStyle(GLGColor.textPrimary)
                        Spacer(minLength: 8)
                        Text(won(monthTotal)).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(accent.primary)
                    }
                }
                GiBand()
                // 마지막 줄이 아래 12 를 가져 8 + 12 = 눈에 20. 빈 상태는 자체 여백이 커서 20 그대로.
                GiPageSection("날짜별 활동", bottom: entries.isEmpty ? 20 : 8) {
                    if entries.isEmpty {
                        emptyTimeline
                    } else {
                        ForEach(Array(entries.enumerated()), id: \.element.id) { idx, e in
                            if idx > 0 { GiHairline() }
                            switch e {
                            case .active(let d): DayRow(day: d, accent: accent.primary)
                            case .gap(let low, let high): GapRow(lowDay: low, highDay: high)
                            }
                        }
                    }
                }
            }
        }
        .scrollIndicators(.hidden)
        .background(Color.white)
        .glgPageTitle("캘린더")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: entriesKey) {
            entries = buildEntries(spendings: store.spendings, banners: store.activeBanners, year: y, month: m)
        }
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

    private var emptyTimeline: some View {
        VStack(spacing: 6) {
            Image(systemName: "doc.text").font(.pretendard(size: 44)).foregroundStyle(Color(.systemGray3))
            Text("이번 달 활동이 없어요").font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
            Text("지출·픽업 일정이 이 타임라인에 모여요").font(.pretendard(size: 12)).foregroundStyle(Color(.systemGray3))
        }
        .frame(maxWidth: .infinity).padding(.vertical, 56)
    }
}

/// 날짜 칸 폭 + 사이 — 활동 없음 줄도 이만큼 들여 내용 칸에 맞춘다(Android DateColumnWidth · DateContentGap).
private let dateColumnWidth: CGFloat = 34
private let dateContentGap: CGFloat = 14

// ── 날짜별 활동 한 줄 — 왼쪽 날짜 + 오른쪽 그날 지출 · 픽업 시작/종료. 카드 없이 위아래 12 ──
private struct DayRow: View {
    let day: TimelineDay
    let accent: Color

    private let weekdays = ["일", "월", "화", "수", "목", "금", "토"]

    var body: some View {
        let dateColor: Color = day.isToday ? accent : (day.weekdayIndex == 0 ? GLGColor.dangerText : GLGColor.textPrimary)
        HStack(alignment: .top, spacing: 0) {
            VStack(spacing: 0) {
                Text("\(day.day)").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(dateColor)
                Text(weekdays[day.weekdayIndex]).font(.pretendard(size: 11)).foregroundStyle(day.isToday ? accent : GLGColor.textSecondary)
            }
            .frame(width: dateColumnWidth)
            Spacer().frame(width: dateContentGap)
            content.padding(.top, 2)
        }
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var content: some View {
        let hasSpend = !day.spendings.isEmpty
        let hasBanner = !day.bannerStart.isEmpty || !day.bannerEnd.isEmpty
        return VStack(alignment: .leading, spacing: 0) {
            if hasSpend {
                HStack {
                    Text("지출 \(day.spendings.count)건").font(.pretendard(size: 15)).foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 8)
                    Text(won(day.spendTotal)).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(accent)
                }
                ForEach(day.spendings, id: \.id) { sp in
                    spendLine(sp).padding(.top, 6)
                }
            }
            if hasBanner {
                if hasSpend { Spacer().frame(height: 8) }
                ForEach(Array(day.bannerStart.enumerated()), id: \.offset) { _, b in bannerLine("▲", "\(b.name) 픽업 시작", b.color) }
                ForEach(Array(day.bannerEnd.enumerated()), id: \.offset) { _, b in bannerLine("▼", "\(b.name) 픽업 종료", b.color) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func spendLine(_ sp: Spending) -> some View {
        HStack(spacing: 8) {
            Circle().fill(Color(argb64: sp.gameColor)).frame(width: 8, height: 8)
            Text([sp.gameName, sp.itemName.isEmpty ? nil : sp.itemName].compactMap { $0 }.joined(separator: " · "))
                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            Spacer(minLength: 8)
            Text(won(sp.amount)).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
        }
    }

    private func bannerLine(_ marker: String, _ text: String, _ color: Color) -> some View {
        HStack(spacing: 6) {
            Text(marker).font(.pretendard(size: 10, weight: .bold)).foregroundStyle(color)
            Text(text).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 1)
    }
}

// ── 활동 없는 연속 구간을 한 줄로 — 흐린 "활동 없음" 문구, 내용 칸에 맞춰 들인다 ──
private struct GapRow: View {
    let lowDay: Int; let highDay: Int
    private var label: String {
        lowDay == highDay ? "\(lowDay)일 · 활동 없음"
            : "\(lowDay)일–\(highDay)일 · 활동 없음 (\(highDay - lowDay + 1)일)"
    }
    var body: some View {
        Text(label).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(Color(.systemGray3))
            .padding(.leading, dateColumnWidth + dateContentGap).padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
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
