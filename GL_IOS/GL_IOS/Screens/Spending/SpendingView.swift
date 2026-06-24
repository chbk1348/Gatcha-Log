import SwiftUI
import UIKit
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 지출 분석 — 월 요약 + 필터(게임·기간·결제·구분·정렬) + 날짜 그룹 내역 + 분석 진입.
// (Compose SpendingScreen 대응) 분석 서브페이지는 NavigationStack push(시스템 back).
// ════════════════════════════════════════════════════════════════════════════

private enum PeriodFilter: String, CaseIterable { case all="전체", thisMonth="이번 달", lastMonth="지난 달", thisYear="올해" }
private enum TypeFilter: String, CaseIterable { case all="전체", normal="일반", subscription="구독" }
private enum SortOrder: String, CaseIterable { case dateDesc="최신순", dateAsc="오래된순", amountDesc="금액 높은순" }

struct SpendingView: View {
    @ObservedObject var store: SpendingStore
    /// 지출 수정 진입 — ContentView 가 편집 대상 설정 + AddSpending 모달을 연다.
    let onEdit: (Spending) -> Void
    @Environment(\.glgAccent) private var accent

    @State private var gameFilters: Set<String> = []
    @State private var period: PeriodFilter = .all
    @State private var paymentFilter: String? = nil
    @State private var typeFilter: TypeFilter = .all
    @State private var sortOrder: SortOrder = .dateDesc
    @State private var showFilter = false
    // 선택 모드(다중 선택) — 일괄 편집/삭제.
    @State private var selectionMode = false
    @State private var selectedIds: Set<String> = []
    @State private var showBulkEdit = false
    // 히어로 축소 — 하부 UIScrollView 의 '아래로 스크롤한 양'(0=최상단). ScrollOffsetReader 가 KVO로 갱신.
    @State private var scrolledDown: CGFloat = 0
    // 펼친 히어로 높이(콜랩스 0일 때 측정). 콘텐츠 상단 자리(고정)로 써서 히어로 축소가 maxOffset 을
    // 바꾸지 않게 한다 → 최하단 떨림(피드백) 방지. 측정 전 추정 기본값.
    @State private var heroExpandedHeight: CGFloat = 132
    // 로드인 스태거 — 행이 처음 보일 때 1회 등장(인덱스=정렬 리스트 내 위치).
    @State private var appeared: Set<Int> = []

    private var activeFilterCount: Int {
        [!gameFilters.isEmpty, period != .all, paymentFilter != nil, typeFilter != .all, sortOrder != .dateDesc]
            .filter { $0 }.count
    }

    /// 스크롤 진행에 따른 히어로 축소 정도(0=펼침, 1=접힘). 상단 64pt 스크롤 동안 보간.
    private var collapse: CGFloat { min(max(scrolledDown / 64, 0), 1) }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // 히어로 자리(고정 높이) — 콜랩스해도 콘텐츠 maxOffset 불변(최하단 떨림 방지). 위에 히어로를 오버레이.
                Color.clear.frame(height: heroExpandedHeight)
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: []) {
                    let items = filtered
                    if items.isEmpty {
                        emptyState
                    } else if sortOrder == .amountDesc {
                        let byAmount = items.sorted { $0.amount > $1.amount }
                        ForEach(Array(byAmount.enumerated()), id: \.element.id) { i, s in
                            historyLink(s).glgLoadIn(i, appeared: $appeared)
                        }
                    } else {
                        let sorted = sortOrder == .dateAsc ? items.sorted { $0.dateMillis < $1.dateMillis }
                                                           : items.sorted { $0.dateMillis > $1.dateMillis }
                        let groups = groupByDay(sorted)
                        ForEach(groups, id: \.key) { group in
                            DateHeader(date: group.items.first?.dateLabel ?? "",
                                       total: group.items.reduce(0) { $0 + $1.amount })
                            ForEach(group.items, id: \.id) { s in
                                historyLink(s).glgLoadIn(sorted.firstIndex(where: { $0.id == s.id }) ?? 0, appeared: $appeared)
                            }
                        }
                    }
                    Color.clear.frame(height: 8)
                }
                .padding(.horizontal, 16)
            }
            // 하부 UIScrollView contentOffset 직접 추적(KVO) — GeometryReader가 이 레이아웃서 오프셋을 안정적으로 보고하지 못해 폴백.
            .background(ScrollOffsetReader { scrolledDown = $0 })
        }
        .scrollIndicators(.hidden)
        .refreshable { store.refreshSpending() }
        // 월 지출 히어로 — 스크롤 위 오버레이(자리는 위 Spacer 가 고정). 스크롤하면 축소. 탭 요소 없어 hitTest 통과.
        .overlay(alignment: .top) {
            monthHero
                .padding(.horizontal, 16).padding(.top, 4).padding(.bottom, 10 - 4 * collapse)
                .background(
                    GeometryReader { g in
                        Color.clear.onChange(of: g.size.height) { h in if collapse == 0 { heroExpandedHeight = h } }
                            .onAppear { if collapse == 0 { heroExpandedHeight = g.size.height } }
                    }
                )
                // 당겨서 새로고침 — 당겨내릴 때(overscroll) 히어로를 같이 내려 PTR 스피너가 헤더 바로 밑에 자연스럽게 드러나게.
                .offset(y: max(0, -scrolledDown))
                .allowsHitTesting(false)
        }
        .background(GLGBackground { Color.clear })
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if selectionMode {
                    Button("취소") { selectionMode = false; selectedIds = [] }
                } else {
                    NavigationLink { CalendarView(store: store) } label: { Image(systemName: "calendar") }
                    NavigationLink { SpendingInsightView(store: store) } label: { Image(systemName: "chart.line.uptrend.xyaxis") }
                    NavigationLink { AnnualReportView(store: store) } label: { Image(systemName: "chart.bar.doc.horizontal") }
                    Button { selectionMode = true; selectedIds = [] } label: { Image(systemName: "checklist") }
                    // 필터 버튼 — 헤더(툴바)로 이동. 활성 시 채움 아이콘.
                    Button { showFilter = true } label: {
                        Image(systemName: activeFilterCount > 0 ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                    }
                }
            }
        }
        .overlay(alignment: .bottom) { if selectionMode { selectionBar } }
        .sheet(isPresented: $showFilter) { filterSheet }
        .sheet(isPresented: $showBulkEdit) {
            BulkEditSheet(store: store, count: selectedIds.count) { game, dateMillis, tags in
                store.bulkEditSpendings(ids: selectedIds, gameName: game, dateMillis: dateMillis, addTags: tags)
                showBulkEdit = false; selectionMode = false; selectedIds = []
            }
        }
    }

    // 월 지출 히어로 — 이번 달 총 지출을 큰 숫자로 강조 + 지난달 대비. 스크롤 시 [collapse] 로 축소.
    private var monthHero: some View {
        let total = store.monthlyTotal()
        let diff = total - store.prevMonthTotal()
        let c = collapse
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "chart.pie.fill").font(.pretendard(size: 13)).foregroundStyle(accent.primary)
                Text("\(store.displayMonth)월 지출").font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
            }
            Text(won(total)).font(.pretendard(size: 34 - 14 * c, weight: .heavy)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
            if total > 0 || store.prevMonthTotal() > 0 {
                Text("지난달 " + (diff == 0 ? "동일" : (diff > 0 ? "+" : "-") + won(abs(diff))))
                    .font(.pretendard(size: 13, weight: .semibold))
                    .foregroundStyle(diff > 0 ? GLGColor.dangerText : (diff < 0 ? accent.primary : GLGColor.textSecondary))
                    .opacity(1 - c)
                    .frame(height: (1 - c) * 18, alignment: .top)
                    .clipped()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20).padding(.vertical, 18 - 7 * c)
        .glgGlass(in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    @ViewBuilder
    private func historyLink(_ s: Spending) -> some View {
        if selectionMode {
            Button {
                if selectedIds.contains(s.id) { selectedIds.remove(s.id) } else { selectedIds.insert(s.id) }
            } label: {
                HistoryItem(spending: s, selectionMode: true, selected: selectedIds.contains(s.id))
            }
            .buttonStyle(.plain)
        } else {
            NavigationLink {
                SpendingDetailView(store: store, spendingId: s.id, onEdit: onEdit)
            } label: {
                HistoryItem(spending: s)
            }
            .buttonStyle(.plain)
        }
    }

    // 선택 모드 하단 액션 바 — 선택 개수 + 삭제/일괄 편집.
    private var selectionBar: some View {
        HStack(spacing: 8) {
            Text("\(selectedIds.count)건 선택").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            Spacer()
            Button("삭제") {
                if !selectedIds.isEmpty { store.deleteSpendings(selectedIds); selectionMode = false; selectedIds = [] }
            }
            .buttonStyle(.bordered).tint(GLGColor.dangerText)
            Button("일괄 편집") { if !selectedIds.isEmpty { showBulkEdit = true } }
                .buttonStyle(.borderedProminent).tint(accent.primary)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Color.white.shadow(.drop(color: .black.opacity(0.12), radius: 8, y: -2)))
    }


    // 게임 칩(필터 시트) — 다중 선택. '전체'=선택 해제, 게임=토글(선택 색은 게임별 대표색).
    @ViewBuilder private func gameChip(_ key: String) -> some View {
        if key.isEmpty {
            GamePill(label: "전체", selected: gameFilters.isEmpty, accent: accent.primary) { gameFilters = [] }
        } else if let g = GameData.shared.games.first(where: { $0.key == key }) {
            GamePill(label: g.shortName, selected: gameFilters.contains(g.displayName), accent: Color(argb64: g.color)) {
                if gameFilters.contains(g.displayName) { gameFilters.remove(g.displayName) }
                else { gameFilters.insert(g.displayName) }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 6) {
            Image(systemName: "doc.text").font(.pretendard(size: 44)).foregroundStyle(Color(.systemGray3))
            Text("아직 기록된 지출이 없어요").font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
            Text("+ 버튼으로 첫 지출을 기록해보세요").font(.pretendard(size: 12)).foregroundStyle(Color(.systemGray3))
        }
        .frame(maxWidth: .infinity).padding(.vertical, 48)
    }

    // ── 필터 시트 ──
    private var filterSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // 게임 — 다중 선택(헤더 필터버튼 → 시트). 인라인 게임필터 행 폐지.
                    filterSection("게임") {
                        FlexibleRow([""] + GameData.shared.games.map { $0.key }) { key in gameChip(key) }
                    }
                    filterSection("기간") { pillWrap(PeriodFilter.allCases, period) { period = $0 } label: { $0.rawValue } }
                    filterSection("결제 수단") {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack { GamePill(label: "전체", selected: paymentFilter == nil, accent: accent.primary) { paymentFilter = nil }; Spacer() }
                            pillWrapStr(GameData.shared.paymentMethods, paymentFilter) { paymentFilter = $0 }
                        }
                    }
                    filterSection("구분") { pillWrap(TypeFilter.allCases, typeFilter) { typeFilter = $0 } label: { $0.rawValue } }
                    filterSection("정렬") { pillWrap(SortOrder.allCases, sortOrder) { sortOrder = $0 } label: { $0.rawValue } }
                }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 16)
            }
            .scrollIndicators(.hidden)
            .background(Color.white)
            .navigationTitle("상세 필터")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("초기화") {
                        gameFilters = []; period = .all; paymentFilter = nil; typeFilter = .all; sortOrder = .dateDesc
                    }
                }
                ToolbarItem(placement: .confirmationAction) { Button("적용") { showFilter = false } }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationBackground(Color.white)
    }

    // 필터 섹션 카드 — 제목(카드 위) + 연회색 카드(지출 추가 모달 sectionCard·Android FilterGroup 과 동일 규격).
    @ViewBuilder
    private func filterSection<C: View>(_ title: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary).padding(.leading, 4)
            content()
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
    }

    private func pillWrap<T: Equatable & Hashable>(_ all: [T], _ sel: T, _ set: @escaping (T) -> Void,
                                                   label: @escaping (T) -> String) -> some View {
        FlexibleRow(all) { item in
            GamePill(label: label(item), selected: item == sel, accent: accent.primary) { set(item) }
        }
    }
    private func pillWrapStr(_ all: [String], _ sel: String?, _ set: @escaping (String) -> Void) -> some View {
        FlexibleRow(all) { item in
            GamePill(label: item, selected: item == sel, accent: accent.primary) { set(item) }
        }
    }

    // ── 필터링 ──
    private var filtered: [Spending] {
        store.spendings.filter { s in
            (gameFilters.isEmpty || gameFilters.contains(s.gameName)) &&
            (paymentFilter == nil || s.paymentMethod == paymentFilter) &&
            (typeFilter == .all || (typeFilter == .normal ? !s.isSubscription : s.isSubscription)) &&
            periodMatch(s)
        }
    }
    private func periodMatch(_ s: Spending) -> Bool {
        switch period {
        case .all: return true
        case .thisMonth: return DateMillis.isSameMonth(s.dateMillis, store.displayYear, store.displayMonth)
        case .lastMonth:
            let (y, m) = prevYM(store.displayYear, store.displayMonth)
            return DateMillis.isSameMonth(s.dateMillis, y, m)
        case .thisYear: return DateMillis.isSameYear(s.dateMillis, store.displayYear)
        }
    }

    private struct DayGroup { let key: String; let items: [Spending] }
    private func groupByDay(_ list: [Spending]) -> [DayGroup] {
        var order: [String] = []
        var map: [String: [Spending]] = [:]
        for s in list {
            if map[s.dayKey] == nil { order.append(s.dayKey) }
            map[s.dayKey, default: []].append(s)
        }
        return order.map { DayGroup(key: $0, items: map[$0] ?? []) }
    }
}

private func prevYM(_ y: Int, _ m: Int) -> (Int, Int) { m == 1 ? (y - 1, 12) : (y, m - 1) }

// ── 재사용 컴포넌트 ──────────────────────────────────────────────────────────


struct GamePill: View {
    let label: String; let selected: Bool; let accent: Color; let action: () -> Void
    var body: some View {
        GLGChip(label: label, selected: selected, color: accent, action: action)
    }
}

struct DateHeader: View {
    let date: String; let total: Int64
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack {
            Text(date).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            Spacer()
            Text(won(total)).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
        }
        .padding(.vertical, 8)
    }
}

struct HistoryItem: View {
    let spending: Spending
    var selectionMode: Bool = false
    var selected: Bool = false
    @Environment(\.glgAccent) private var accent

    private var gameColor: Color { Color(argb64: spending.gameColor) }
    private var abbr: String {
        GameData.shared.byNameOrNull(name: spending.gameName)?.abbr ?? String(spending.gameName.prefix(2))
    }
    private var subtitle: String {
        [spending.itemName.isEmpty ? nil : spending.itemName, spending.paymentMethod.isEmpty ? nil : spending.paymentMethod]
            .compactMap { $0 }.joined(separator: " · ")
    }

    var body: some View {
        GLGCard(cornerRadius: 20, padding: 14) {
            HStack(spacing: 13) {
                if selectionMode {
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                        .font(.pretendard(size: 22)).foregroundStyle(selected ? accent.primary : Color(.systemGray3))
                }
                // 게임 색 배지 (약칭)
                Text(abbr)
                    .font(.pretendard(size: 13, weight: .heavy)).foregroundStyle(gameColor)
                    .frame(width: 44, height: 44)
                    .background(gameColor.opacity(0.14), in: RoundedRectangle(cornerRadius: 13, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(spending.gameName).font(.pretendard(size: 15, weight: .bold)).lineLimit(1)
                        if spending.isSubscription {
                            GLGBadge(label: "정기", color: gameColor)
                        }
                    }
                    if !subtitle.isEmpty {
                        Text(subtitle).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                    }
                    if !spending.tags.isEmpty {
                        HStack(spacing: 5) {
                            ForEach(spending.tags.prefix(3), id: \.self) { TagChip(tag: $0) }
                        }
                        .padding(.top, 1)
                    }
                }

                Spacer(minLength: 8)

                HStack(spacing: 4) {
                    Text(won(spending.amount)).font(.pretendard(size: 16, weight: .bold)).lineLimit(1)
                    if !selectionMode {
                        Image(systemName: "chevron.right").font(.pretendard(size: 12, weight: .semibold))
                            .foregroundStyle(Color(.tertiaryLabel))
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

struct TagChip: View {
    let tag: String
    var body: some View {
        GLGChip(label: tag, variant: .tag)
    }
}

/// 지출 일괄 편집 시트 — 게임/날짜 변경 + 태그 추가. ‘변경 안 함’으로 둔 항목은 미변경.
private struct BulkEditSheet: View {
    @ObservedObject var store: SpendingStore
    let count: Int
    let onApply: (String?, Int64?, [String]) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent
    @State private var game: String? = nil
    @State private var date: Date? = nil
    @State private var tags: Set<String> = []
    @State private var showDate = false

    private func label(_ d: Date) -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일"
        return f.string(from: d)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("선택한 \(count)건만 바뀌고, ‘변경 안 함’으로 둔 항목은 그대로예요.")
                        .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    section("게임") {
                        FlexibleRow([""] + GameData.shared.games.map { $0.key }) { key in
                            if key.isEmpty {
                                GamePill(label: "변경 안 함", selected: game == nil, accent: accent.primary) { game = nil }
                            } else if let g = GameData.shared.games.first(where: { $0.key == key }) {
                                GamePill(label: g.shortName, selected: game == g.displayName, accent: Color(argb64: g.color)) { game = g.displayName }
                            }
                        }
                    }
                    section("날짜") {
                        HStack(spacing: 8) {
                            GamePill(label: date.map { label($0) } ?? "변경 안 함", selected: date != nil, accent: accent.primary) { showDate = true }
                            if date != nil { GamePill(label: "지우기", selected: false, accent: accent.primary) { date = nil } }
                        }
                    }
                    section("태그 추가") {
                        FlexibleRow(GameData.shared.suggestedTags) { t in
                            GamePill(label: t, selected: tags.contains(t), accent: accent.primary) {
                                if tags.contains(t) { tags.remove(t) } else { tags.insert(t) }
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color.white)
            .navigationTitle("일괄 편집").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("적용") { onApply(game, date.map { Int64($0.timeIntervalSince1970 * 1000) }, Array(tags)) }
                }
            }
            .sheet(isPresented: $showDate) {
                NavigationStack {
                    DatePicker("날짜", selection: Binding(get: { date ?? Date() }, set: { date = $0 }), displayedComponents: .date)
                        .datePickerStyle(.graphical).padding()
                        .navigationTitle("날짜 선택").navigationBarTitleDisplayMode(.inline)
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("확인") { showDate = false } } }
                }
                .presentationDetents([.medium])
                .presentationBackground(Color.white)
            }
        }
        .presentationDetents([.medium, .large])
        .presentationBackground(Color.white)
    }

    @ViewBuilder
    private func section<C: View>(_ title: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary).padding(.leading, 4)
            content()
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
    }
}

/// 간단한 가로 래핑 행(칩 그룹). iOS16 호환 — 고정 줄바꿈 없이 가로 스크롤로 대체.
struct FlexibleRow<Data: RandomAccessCollection, Content: View>: View where Data.Element: Hashable {
    let data: Data
    @ViewBuilder let content: (Data.Element) -> Content
    init(_ data: Data, @ViewBuilder content: @escaping (Data.Element) -> Content) {
        self.data = data; self.content = content
    }
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) { ForEach(Array(data), id: \.self) { content($0) } }
        }
    }
}

/// 하부 UIScrollView 의 contentOffset.y(+adjustedContentInset.top) 를 KVO로 직접 읽어 콜백.
/// SwiftUI GeometryReader 가 이 레이아웃에서 스크롤 오프셋을 안정적으로 보고하지 못해 사용하는 폴백.
/// 0 = 최상단, 양수 = 아래로 스크롤한 양.
private struct ScrollOffsetReader: UIViewRepresentable {
    let onChange: (CGFloat) -> Void

    func makeUIView(context: Context) -> UIView {
        let v = UIView(frame: .zero)
        v.backgroundColor = .clear
        v.isUserInteractionEnabled = false
        DispatchQueue.main.async { context.coordinator.attach(from: v) }
        return v
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        if context.coordinator.scrollView == nil {
            DispatchQueue.main.async { context.coordinator.attach(from: uiView) }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(onChange: onChange) }

    final class Coordinator: NSObject {
        let onChange: (CGFloat) -> Void
        weak var scrollView: UIScrollView?
        private var obs: NSKeyValueObservation?
        // 최초(최상단) contentOffset.y 를 기준으로 정규화. adjustedContentInset 은 히어로 축소로
        // 레이아웃이 바뀔 때마다 재계산돼 피드백 진동을 유발하므로 쓰지 않는다(raw offset 은 스크롤로만 변함).
        private var baseline: CGFloat?
        init(onChange: @escaping (CGFloat) -> Void) { self.onChange = onChange }

        func attach(from view: UIView) {
            var v: UIView? = view.superview
            while let cur = v, !(cur is UIScrollView) { v = cur.superview }
            guard let sv = v as? UIScrollView else { return }
            scrollView = sv
            obs = sv.observe(\.contentOffset, options: [.new, .initial]) { [weak self] sv, _ in
                guard let self else { return }
                let y = sv.contentOffset.y
                if self.baseline == nil { self.baseline = y }
                self.onChange(y - (self.baseline ?? y))
            }
        }

        deinit { obs?.invalidate() }
    }
}
