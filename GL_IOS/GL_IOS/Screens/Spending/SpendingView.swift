import SwiftUI
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

    @State private var gameFilter: String? = nil
    @State private var period: PeriodFilter = .all
    @State private var paymentFilter: String? = nil
    @State private var typeFilter: TypeFilter = .all
    @State private var sortOrder: SortOrder = .dateDesc
    @State private var showFilter = false

    private var activeFilterCount: Int {
        [gameFilter != nil, period != .all, paymentFilter != nil, typeFilter != .all, sortOrder != .dateDesc]
            .filter { $0 }.count
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0, pinnedViews: []) {
                // 게임별 필터(스크롤) + 우측 고정 필터 버튼
                HStack(spacing: 8) {
                    gameFilterRow
                    filterButton
                }
                .padding(.top, 2)

                let items = filtered
                if items.isEmpty {
                    emptyState
                } else if sortOrder == .amountDesc {
                    ForEach(items.sorted { $0.amount > $1.amount }, id: \.id) { historyLink($0) }
                } else {
                    let sorted = sortOrder == .dateAsc ? items.sorted { $0.dateMillis < $1.dateMillis }
                                                       : items.sorted { $0.dateMillis > $1.dateMillis }
                    let groups = groupByDay(sorted)
                    ForEach(groups, id: \.key) { group in
                        DateHeader(date: group.items.first?.dateLabel ?? "",
                                   total: group.items.reduce(0) { $0 + $1.amount })
                        ForEach(group.items, id: \.id) { historyLink($0) }
                    }
                }
                Color.clear.frame(height: 8)
            }
            .padding(.horizontal, 16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .refreshable { store.refreshSpending() }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // 월 지출 내역 — 우측 버튼 직전까지 가로로 꽉 채워 노출
            ToolbarItem(placement: .principal) { monthHeaderItem }
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink { CalendarView(store: store) } label: { Image(systemName: "calendar") }
                NavigationLink { SpendingInsightView(store: store) } label: { Image(systemName: "chart.line.uptrend.xyaxis") }
                NavigationLink { AnnualReportView(store: store) } label: { Image(systemName: "chart.bar.doc.horizontal") }
            }
        }
        .sheet(isPresented: $showFilter) { filterSheet }
    }

    // 월 지출 내역 — 헤더에서 우측 버튼 직전까지 가로 꽉 채움(합계 좌측 · 지난달 대비 우측).
    private var monthHeaderItem: some View {
        let total = store.monthlyTotal()
        let diff = total - store.prevMonthTotal()
        return HStack(spacing: 5) {
            Image(systemName: "chart.pie.fill").font(.pretendard(size: 12)).foregroundStyle(accent.primary)
            Text("\(store.displayMonth)월").font(.pretendard(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            Text(won(total)).font(.pretendard(size: 15, weight: .bold))
            Spacer(minLength: 10)
            if total > 0 || store.prevMonthTotal() > 0 {
                Text("지난달 " + (diff == 0 ? "동일" : (diff > 0 ? "+" : "-") + won(abs(diff))))
                    .font(.pretendard(size: 11, weight: .semibold))
                    .foregroundStyle(diff > 0 ? GLGColor.dangerText : (diff < 0 ? accent.primary : GLGColor.textSecondary))
            }
        }
        .lineLimit(1)
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func historyLink(_ s: Spending) -> some View {
        NavigationLink {
            SpendingDetailView(store: store, spendingId: s.id, onEdit: onEdit)
        } label: {
            HistoryItem(spending: s)
        }
        .buttonStyle(.plain)
    }


    // 공통 칩(GLGChip)과 동일 규격 — 14pt 라운드·흰 배경+옅은 아웃라인, 선택(필터 활성)=accent 채움. 슬라이더 아이콘 유지.
    private var filterButton: some View {
        let active = activeFilterCount > 0
        return Button { showFilter = true } label: {
            HStack(spacing: 5) {
                Image(systemName: "slider.horizontal.3").font(.pretendard(size: 12, weight: .semibold))
                Text(active ? "필터 \(activeFilterCount)" : "필터").font(.pretendard(size: 13, weight: .semibold))
            }
            .foregroundStyle(active ? .white : Color(hex: 0xFF4A5159))
            .padding(.horizontal, 14).padding(.vertical, 9)
            .background(active ? accent.primary : Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(active ? Color.clear : Color(hex: 0xFFE3E5EA), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var gameFilterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                GamePill(label: "전체", selected: gameFilter == nil, accent: accent.primary) { gameFilter = nil }
                ForEach(GameData.shared.games, id: \.key) { g in
                    // 게임 칩은 단일 규격 유지, 선택됨 색만 게임별 대표색으로.
                    GamePill(label: g.shortName, selected: gameFilter == g.displayName, accent: Color(argb64: g.color)) {
                        gameFilter = g.displayName
                    }
                }
            }
            .padding(.vertical, 8)
        }
        // 필터 버튼 좌측(스크롤 우측 가장자리) 페이드 — 칩이 자연스럽게 사라지도록
        .mask(
            LinearGradient(stops: [
                .init(color: .black, location: 0),
                .init(color: .black, location: 0.9),
                .init(color: .clear, location: 1.0),
            ], startPoint: .leading, endPoint: .trailing)
        )
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
                        gameFilter = nil; period = .all; paymentFilter = nil; typeFilter = .all; sortOrder = .dateDesc
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
            (gameFilter == nil || s.gameName == gameFilter) &&
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
                    Image(systemName: "chevron.right").font(.pretendard(size: 12, weight: .semibold))
                        .foregroundStyle(Color(.tertiaryLabel))
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
