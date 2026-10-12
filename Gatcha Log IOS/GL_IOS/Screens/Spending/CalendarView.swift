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

    /// 총 지출 줄 제목 — 보고 있는 달이 이번 달일 때만 「이번 달」이라 부른다(27.52.0).
    /// 예전엔 달을 넘겨도 늘 「이번 달 총 지출」이라, 지난달 합계가 이번 달 것으로 읽혔다.
    /// 「이번 달」의 기준은 이 화면이 처음 여는 달과 같은 값(store.displayYear · displayMonth)이다.
    private var totalLabel: String {
        if y == store.displayYear && m == store.displayMonth { return "이번 달 총 지출" }
        return y == store.displayYear ? "\(m)월 총 지출" : "\(y)년 \(m)월 총 지출"
    }

    // 타임라인은 지출·배너·연월이 바뀔 때만 만든다.
    //
    // 예전엔 body 첫 줄에서 buildEntries 를 통째로 돌렸다. 이 함수는 지출 1건마다 DateMillis.comps 를
    // 부르므로 지출 1,000건이면 브리지·날짜 변환이 각각 수천 회였고, 그게 **body 평가마다** 반복됐다.
    // (Android CalendarScreen 은 같은 함수를 이미 remember 로 캐시하고 있었다 — 파리티 역전)
    //
    // 조립 자체는 공용 SpendingCalendarLogic.buildMonth 가 한다(27.52.0, Android 와 한 소스) — 브리지는 한 번이다.
    // 예전엔 여기서 요일 · 「오늘」을 Calendar.current(기기 달력 설정)로 셌는데, 지출을 날에 나누는 쪽은
    // 공용 DateUtil(그레고리력)이라 한 화면에 두 달력이 섞여 있었다.
    @State private var entries: [SpendingCalendarEntry] = []
    /// 그 달 지출 합계 — entries 와 같은 계산에서 함께 나온다.
    @State private var monthTotal: Int64 = 0

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
                        Text(verbatim: totalLabel).font(.pretendard(size: 15)).foregroundStyle(GLGColor.textPrimary)
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
                        ForEach(Array(entries.enumerated()), id: \.element.key) { idx, e in
                            if idx > 0 { GiHairline() }
                            // 활동이 있는 날이면 그날 줄, 아니면 「활동 없음」 구간 줄.
                            if let d = e.active {
                                DayRow(day: d, accent: accent.primary)
                            } else {
                                GapRow(lowDay: Int(e.gapLowDay), highDay: Int(e.gapHighDay))
                            }
                        }
                    }
                }
            }
            // 넓은 창(iPad)에서는 가운데 640 폭으로 모은다 — 설정 · 마이페이지와 같은 규칙.
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(Color.white)
        .glgPageTitle("캘린더")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: entriesKey) {
            let built = SpendingCalendarLogic.shared.buildMonth(
                spendings: store.spendings, banners: store.activeBanners,
                year: Int32(y), month: Int32(m), nowMillis: nowMs()
            )
            entries = built.entries
            monthTotal = built.total
        }
    }

    /// 달 넘기기 — 공용 계산(27.52.0). 예전엔 Calendar.current 로 「그 달 1일」을 만들어 옮겼다.
    private func shift(_ delta: Int) {
        let next = SpendingCalendarLogic.shared.shiftMonth(year: Int32(y), month: Int32(m), delta: Int32(delta))
        year = Int(next.year); month = Int(next.month)
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
    let day: SpendingCalendarDay
    let accent: Color

    private let weekdays = ["일", "월", "화", "수", "목", "금", "토"]

    var body: some View {
        // 공용 모델의 Int 는 Int32 로 넘어온다 — 글자 · 배열 첨자에 쓰기 전에 Int 로 바꾼다.
        let dayNumber = Int(day.day)
        let weekdayIndex = Int(day.weekdayIndex)
        let dateColor: Color = day.isToday ? accent : (weekdayIndex == 0 ? GLGColor.dangerText : GLGColor.textPrimary)
        HStack(alignment: .top, spacing: 0) {
            VStack(spacing: 0) {
                Text("\(dayNumber)").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(dateColor)
                Text(weekdays[weekdayIndex]).font(.pretendard(size: 11)).foregroundStyle(day.isToday ? accent : GLGColor.textSecondary)
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
                ForEach(Array(day.bannerStart.enumerated()), id: \.offset) { _, b in bannerLine("▲", "\(b.name) 픽업 시작", Color(argb64: b.gameColor)) }
                ForEach(Array(day.bannerEnd.enumerated()), id: \.offset) { _, b in bannerLine("▼", "\(b.name) 픽업 종료", Color(argb64: b.gameColor)) }
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

// (27.52.0) 집계 모델(BannerMark · TimelineDay · TimelineEntry)과 buildEntries 는 공용
// SpendingCalendarLogic.buildMonth 로 옮겼다 — Android CalendarScreen 이 같은 조립을 따로 적어 두고 있었다.
