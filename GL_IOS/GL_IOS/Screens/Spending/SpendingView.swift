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
    var store: SpendingStore
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
    // 성능: 필터/정렬/그룹 결과를 캐시 — 스크롤(콜랩스)로 body 가 매 프레임 재평가돼도 리스트를
    // 다시 필터·정렬·그룹하지 않는다. 데이터/필터/정렬이 바뀔 때만 recompute 로 갱신.
    @State private var displayGroups: [DayGroup] = []
    @State private var listIsEmpty = true

    private var activeFilterCount: Int {
        [!gameFilters.isEmpty, period != .all, paymentFilter != nil, typeFilter != .all, sortOrder != .dateDesc]
            .filter { $0 }.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // "N월 지출" 요약 헤더는 지출 인사이트 '월간' 탭으로 이동(MonthSummaryHeader).
                if listIsEmpty {
                    emptyState
                } else {
                    // 넓은 화면(iPad)=날짜 카드 메이슨리(짧은 열 우선 채움 → 빈 영역 없음), iPhone=기존 세로 1열.
                    // 가중치=그 날짜 카드의 지출 건수(+헤더) → 높이 비례로 좌우 균형.
                    // 미리 계산된 displayGroups 순회만(매 프레임 재필터·재정렬·재그룹 없음).
                    GLGColumnMasonry(
                        cards: displayGroups.enumerated().map { gi, group in
                            GLGMasonryCard(id: group.key, weight: Double(group.items.count) + 1) {
                                dayCard(dateLabel: group.dateLabel, total: group.total, items: group.items)
                            }
                        },
                        spacing: 8, stackSpacing: 0
                    )
                }
                Color.clear.frame(height: 8)
            }
            .padding(.horizontal, 16)
        }
        .scrollIndicators(.hidden)
        .refreshable { store.refreshSpending() }
        // 그룹 재계산 — 데이터/필터/정렬 변화 시에만(스크롤과 무관). $spendings 는 새 값을 전달받아 사용.
        .onAppear { recompute(store.spendings) }
        .onChange(of: store.spendings) { _, new in recompute(new) }
        .onChange(of: gameFilters) { _, _ in recompute(store.spendings) }
        .onChange(of: period) { _, _ in recompute(store.spendings) }
        .onChange(of: paymentFilter) { _, _ in recompute(store.spendings) }
        .onChange(of: typeFilter) { _, _ in recompute(store.spendings) }
        .onChange(of: sortOrder) { _, _ in recompute(store.spendings) }
        .background(GLGBackground { Color.clear })
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        // 좌측 = 보기 전환(캘린더·인사이트), 우측 = 목록 조작(선택·필터).
        // 성격이 다른 버튼 4개가 우측에 뭉쳐 있어 무엇이 무엇인지 구분되지 않던 걸 갈랐다.
        //
        // 버튼마다 ToolbarItem 을 따로 두고 그 사이에 ToolbarSpacer 를 넣는다 —
        // iOS 26 은 인접한 툴바 아이템을 **하나의 글래스 캡슐로 묶어** 버리므로, 스페이서로 갈라야
        // 버튼이 각각 독립된 원형으로 떨어진다. (iOS 25 이하는 원래 묶이지 않아 스페이서가 불필요)
        .toolbar {
            if !selectionMode {
                ToolbarItem(placement: .topBarLeading) {
                    NavigationLink { CalendarView(store: store) } label: { Image(systemName: "calendar") }
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer(.fixed, placement: .topBarLeading)
                }
                ToolbarItem(placement: .topBarLeading) {
                    NavigationLink { SpendingInsightView(store: store) } label: { Image(systemName: "chart.line.uptrend.xyaxis") }
                }
            }

            if selectionMode {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("취소") { selectionMode = false; selectedIds = [] }
                }
            } else {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { selectionMode = true; selectedIds = [] } label: { Image(systemName: "checklist") }
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer(.fixed, placement: .topBarTrailing)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    // 필터 — 활성 시 채움 아이콘.
                    Button { showFilter = true } label: {
                        Image(systemName: activeFilterCount > 0 ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                    }
                }
            }
        }
        // 선택 하단바 — overlay 가 아닌 safeAreaInset 으로 배치해 스크롤 콘텐츠를 그만큼 위로 인셋한다.
        // (overlay 는 콘텐츠를 안 밀어 최하단 항목이 바에 가려져 선택 불가였음)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if selectionMode {
                selectionBar.transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(GLGMotion.standard(), value: selectionMode)
        .sheet(isPresented: $showFilter) { filterSheet }
        .sheet(isPresented: $showBulkEdit) {
            BulkEditSheet(store: store, count: selectedIds.count) { game, dateMillis, tags in
                store.bulkEditSpendings(ids: selectedIds, gameName: game, dateMillis: dateMillis, addTags: tags)
                showBulkEdit = false; selectionMode = false; selectedIds = []
            }
        }
    }


    /// 같은 날짜 지출을 한 카드로 묶은 그룹 카드 — 상단 날짜·합계 헤더(first-end) + 구분선 + 지출 행들.
    /// dateLabel 이 nil 이면 헤더 없이 행만(금액순 평면 리스트의 단일 항목 카드).
    private func dayCard(dateLabel: String?, total: Int64, items: [Spending]) -> some View {
        GLGCard(cornerRadius: 18, padding: 0) {
            VStack(spacing: 0) {
                if let dateLabel {
                    HStack {
                        Text(dateLabel).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                        Spacer()
                        Text(won(total)).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                    }
                    .padding(.horizontal, 16).padding(.top, 13).padding(.bottom, 9)
                    Divider()
                }
                ForEach(Array(items.enumerated()), id: \.element.id) { idx, s in
                    if idx > 0 { Divider() }
                    historyRow(s)
                }
            }
        }
        .padding(.vertical, store.spendingCompact ? 4 : 6)
    }

    /// 카드 안에 들어가는 지출 한 건 행 — 일반 모드=상세로 NavigationLink, 선택 모드=선택 토글.
    @ViewBuilder
    private func historyRow(_ s: Spending) -> some View {
        if selectionMode {
            Button {
                if selectedIds.contains(s.id) { selectedIds.remove(s.id) } else { selectedIds.insert(s.id) }
            } label: {
                SpendingRow(spending: s, selectionMode: true, selected: selectedIds.contains(s.id), compact: store.spendingCompact)
            }
            .buttonStyle(.plain)
        } else {
            NavigationLink {
                SpendingDetailView(store: store, spendingId: s.id, onEdit: onEdit)
            } label: {
                SpendingRow(spending: s, compact: store.spendingCompact)
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
                if selectedIds.isEmpty { store.showStatus("선택된 항목이 없어요") }
                else { store.deleteSpendings(selectedIds); selectionMode = false; selectedIds = [] }
            }
            .buttonStyle(.bordered).tint(GLGColor.dangerText)
            Button("일괄 편집") {
                if selectedIds.isEmpty { store.showStatus("선택된 항목이 없어요") } else { showBulkEdit = true }
            }
            .buttonStyle(.borderedProminent).tint(accent.primary)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        // 시스템 글래스(iOS26 Liquid Glass, 폴백 ultraThinMaterial) — 떠 있는 라운드 바(레이아웃 유지).
        .modifier(SystemGlassBar())
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }


    // 게임 칩(필터 시트) — 다중 선택. '전체'=선택 해제, 게임=토글(선택 색은 게임별 대표색).
    @ViewBuilder private func gameChip(_ key: String) -> some View {
        if key.isEmpty {
            GamePill(label: "전체", selected: gameFilters.isEmpty, accent: accent.primary) { gameFilters = [] }
        } else if let g = GameData.shared.byNameOrNull(name: key) {
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
                        FlexibleRow([""] + GLGGames.keys) { key in gameChip(key) }
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
    private func filteredList(_ list: [Spending]) -> [Spending] {
        // 기간 판정에 쓸 연/월을 **항목 밖에서 한 번만** 읽는다.
        // store.displayYear·displayMonth 는 KMP 게터라(시계 읽기 + 시각→로컬 변환) 항목마다 부르면
        // 브리지 2회 + 변환 2회가 isSameMonth 비용 위에 그대로 얹힌다.
        let dy = store.displayYear
        let dm = store.displayMonth
        let (ly, lm) = prevYM(dy, dm)
        return list.filter { s in
            (gameFilters.isEmpty || gameFilters.contains(s.gameName)) &&
            (paymentFilter == nil || s.paymentMethod == paymentFilter) &&
            (typeFilter == .all || (typeFilter == .normal ? !s.isSubscription : s.isSubscription)) &&
            periodMatch(s, dy, dm, ly, lm)
        }
    }
    // 필터→정렬→그룹→합계를 한 번에 계산해 캐시(displayGroups). 스크롤이 아니라 데이터/필터 변화 때만 호출.
    private func recompute(_ list: [Spending]) {
        let items = filteredList(list)
        listIsEmpty = items.isEmpty
        switch sortOrder {
        case .amountDesc:
            displayGroups = items.sorted { $0.amount > $1.amount }
                .map { DayGroup(key: $0.id, items: [$0], dateLabel: nil, total: 0) }
        case .dateAsc:
            displayGroups = groupByDay(items.sorted { $0.dateMillis < $1.dateMillis })
        default:
            displayGroups = groupByDay(items.sorted { $0.dateMillis > $1.dateMillis })
        }
    }
    private func periodMatch(_ s: Spending, _ dy: Int, _ dm: Int, _ ly: Int, _ lm: Int) -> Bool {
        switch period {
        case .all: return true
        case .thisMonth: return DateMillis.isSameMonth(s.dateMillis, dy, dm)
        case .lastMonth: return DateMillis.isSameMonth(s.dateMillis, ly, lm)
        case .thisYear: return DateMillis.isSameYear(s.dateMillis, dy)
        }
    }

    private struct DayGroup { let key: String; let items: [Spending]; let dateLabel: String?; let total: Int64 }
    private func groupByDay(_ list: [Spending]) -> [DayGroup] {
        var order: [String] = []
        var map: [String: [Spending]] = [:]
        for s in list {
            // dayKey 는 게터라 읽을 때마다 브리지 + 날짜 변환 + 문자열 조립이다. 항목당 한 번만 읽는다.
            let key = s.dayKey
            if map[key] == nil { order.append(key) }
            map[key, default: []].append(s)
        }
        return order.map { key in
            let its = map[key] ?? []
            return DayGroup(key: key, items: its, dateLabel: its.first?.dateLabel, total: its.reduce(0) { $0 + $1.amount })
        }
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

/// 지출 한 건 행(first-end) — 좌측: 게임색 배지 + 게임명·아이템·태그, 우측: 금액·셰브론. 카드(SpendingDayCard) 안에 들어가는 행.
struct SpendingRow: View {
    let spending: Spending
    var selectionMode: Bool = false
    var selected: Bool = false
    var compact: Bool = false
    @Environment(\.glgAccent) private var accent

    private var gameColor: Color { Color(argb64: spending.gameColor) }
    private var abbr: String {
        GameData.shared.byNameOrNull(name: spending.gameName)?.abbr ?? String(spending.gameName.prefix(2))
    }
    private var subtitle: String {
        [spending.itemName.isEmpty ? nil : spending.itemName, spending.paymentMethod.isEmpty ? nil : spending.paymentMethod]
            .compactMap { $0 }.joined(separator: " · ")
    }
    /// 컴팩트: 게임명 · 아이템 한 줄.
    private var compactTitle: String {
        let item = spending.itemName.isEmpty ? nil : spending.itemName
        return spending.gameName + (item != nil ? "  ·  \(item!)" : "")
    }

    var body: some View {
        HStack(spacing: compact ? 10 : 13) {
            if selectionMode {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.pretendard(size: 22)).foregroundStyle(selected ? accent.primary : Color(.systemGray3))
            }
            // 게임 색 배지 (약칭)
            Text(abbr)
                .font(.pretendard(size: compact ? 11 : 13, weight: .heavy)).foregroundStyle(gameColor)
                .frame(width: compact ? 30 : 40, height: compact ? 30 : 40)
                .background(gameColor.opacity(0.14), in: RoundedRectangle(cornerRadius: compact ? 9 : 12, style: .continuous))

            if compact {
                // 한 줄(태그·결제수단·정기뱃지 숨김)
                Text(compactTitle).font(.pretendard(size: 13, weight: .bold)).lineLimit(1)
            } else {
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
            }

            Spacer(minLength: 8)

            HStack(spacing: 4) {
                Text(won(spending.amount)).font(.pretendard(size: compact ? 14 : 16, weight: .bold)).lineLimit(1)
                if !selectionMode {
                    Image(systemName: "chevron.right").font(.pretendard(size: 12, weight: .semibold))
                        .foregroundStyle(Color(.tertiaryLabel))
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, compact ? 11 : 14)
        .contentShape(Rectangle())
    }
}

struct TagChip: View {
    let tag: String
    var body: some View {
        GLGChip(label: tag, variant: .tag)
    }
}

/// 시스템 글래스 배경 — iOS 26 Liquid Glass(.glassEffect), 그 이하 ultraThinMaterial 폴백.
private struct SystemGlassBar: ViewModifier {
    func body(content: Content) -> some View {
        let shape = Capsule(style: .continuous)
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular, in: shape)
        } else {
            content.background(.ultraThinMaterial, in: shape)
        }
    }
}

/// 지출 일괄 편집 시트 — 게임/날짜 변경 + 태그 추가. ‘변경 안 함’으로 둔 항목은 미변경.
private struct BulkEditSheet: View {
    var store: SpendingStore
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
                        FlexibleRow([""] + GLGGames.keys) { key in
                            if key.isEmpty {
                                GamePill(label: "변경 안 함", selected: game == nil, accent: accent.primary) { game = nil }
                            } else if let g = GameData.shared.byNameOrNull(name: key) {
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

