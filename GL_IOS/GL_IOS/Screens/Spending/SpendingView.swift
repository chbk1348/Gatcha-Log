import SwiftUI
import UIKit
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 지출 분석 — 월 요약 + 필터(게임·기간·결제·구분·정렬) + 날짜 그룹 내역 + 분석 진입.
// (Compose SpendingScreen 대응) 분석 서브페이지는 NavigationStack push(시스템 back).
// ════════════════════════════════════════════════════════════════════════════

/// 지출 탭 스택의 경로. **하나의 NavigationStack(path:)** 이 이 값들로 밀고 당긴다.
///
/// 뷰 기반 `NavigationLink { ... }` 와 목적지 안의 `navigationDestination` 을 섞어 쓰다
/// 스택이 교체되거나 루트로 튕기는 버그를 반복해서 냈다 — 경로 한 곳에서만 관리한다.
enum SpendingRoute: Hashable {
    case detail(String)   // 지출 id
    case edit(String)     // 지출 id
    case add
}

private enum PeriodFilter: String, CaseIterable { case all="전체", thisMonth="이번 달", lastMonth="지난 달", thisYear="올해", custom="기간 지정" }
private enum TypeFilter: String, CaseIterable { case all="전체", normal="일반", subscription="구독" }
private enum SortOrder: String, CaseIterable { case dateDesc="최신순", dateAsc="오래된순", amountDesc="금액 높은순" }

struct SpendingView: View {
    var store: SpendingStore
    /// 지출 수정 진입 — ContentView 가 편집 대상 설정 + AddSpending 모달을 연다.
    let onEdit: (Spending) -> Void
    @Environment(\.glgAccent) private var accent
    /// 지금 좌/우로 갈려 있는가 — GLGSplitDetail 이 돌려주는 값(폭 기준, iPadOS 26 자유 창 대응).
    @State private var isWide = false
    /// 우측 상세에 띄울 지출. iPhone 에서는 쓰지 않는다(기존대로 push).
    @State private var selectedId: String? = nil

    @State private var gameFilters: Set<String> = []
    @State private var period: PeriodFilter = .all
    // 기간 지정(직접 범위) — 기본값은 '최근 한 달'. 시작>끝이면 판정에서 뒤집어 쓴다.
    @State private var customStart: Date = Calendar.current.date(byAdding: .month, value: -1, to: Date()) ?? Date()
    @State private var customEnd: Date = Date()
    @State private var showStartPicker = false
    @State private var showEndPicker = false
    @State private var showScrollTop = false
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

    /// iPad = 좌 목록 / 우 상세. iPhone = 기존 단일 목록(누르면 push).
    var body: some View {
        GLGSplitDetail(isSplit: $isWide) { listContent } detail: { detailPane }
            // 우측에 띄운 지출이 지워지면(상세의 삭제·일괄 삭제) 빈 화면이 남는다 → 선택 해제.
            .onChange(of: store.spendings) { _, new in
                if let id = selectedId, !new.contains(where: { $0.id == id }) { selectedId = nil }
            }
    }

    /// 우측 상세 — 고른 게 없으면 안내만.
    @ViewBuilder
    private var detailPane: some View {
        if let id = selectedId, store.spendings.contains(where: { $0.id == id }) {
            NavigationStack {
                SpendingDetailView(store: store, spendingId: id, onEdit: onEdit)
            }
            .id(id)   // 다른 지출을 고르면 상세를 새로 세운다(스크롤·히어로 상태가 남지 않게)
        } else {
            GLGSplitPlaceholder(systemImage: "creditcard", text: "왼쪽에서 지출을 선택하세요")
        }
    }

    private var listContent: some View {
        ScrollViewReader { proxy in
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 맨 위로 버튼이 되돌아올 지점.
                Color.clear.frame(height: 0).id(Self.topAnchor)
                // 퀵필터 — 시트를 열지 않고 기간을 바꾸고, 걸린 필터를 바로 뗀다.
                // 리스트가 비어도 **먼저** 그린다 — 필터 때문에 비었을 때 되돌릴 손잡이가 있어야 한다.
                //
                // 선택 모드에서도 **치우지 않는다.** 예전엔 숨겼는데, 선택을 켜는 순간 이 줄이 사라지며
                // 리스트 전체가 위로 훅 밀려 올라가 화면이 튀었다(선택하려던 항목이 손가락 아래에서 이동).
                quickFilters
                // "N월 지출" 요약 헤더는 지출 인사이트 '월간' 탭으로 이동(MonthSummaryHeader).
                if listIsEmpty {
                    emptyState
                } else {
                    // 날짜 카드 목록. **iPad 도 1열**이다 — 좌측이 목록 컬럼이 되면서 폭이 좁아졌고,
                    // 좁은 폭에서 2열 메이슨리는 카드가 잘게 쪼개져 오히려 읽기 어렵다.
                    // (iPhone 과 같은 한 줄 배치를 유지한다.)
                    // 미리 계산된 displayGroups 순회만(매 프레임 재필터·재정렬·재그룹 없음).
                    GLGColumnMasonry(
                        cards: displayGroups.enumerated().map { gi, group in
                            GLGMasonryCard(id: group.key, weight: Double(group.items.count) + 1) {
                                dayCard(dateLabel: group.dateLabel, total: group.total, items: group.items)
                            }
                        },
                        columns: 1,
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
        .onChange(of: customStart) { _, _ in if period == .custom { recompute(store.spendings) } }
        .onChange(of: customEnd) { _, _ in if period == .custom { recompute(store.spendings) } }
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
        // 맨 위로 — 한참 내려간 뒤에만 뜬다. 지출은 날짜 카드가 길어 아래로 많이 내려가는데,
        // 되돌아오려면 계속 쓸어올려야 했다.
        // 스크롤량 = contentOffset.y 를 **상단 인셋 기준으로 0 에 맞춘 값**.
        // (최상단에서 contentOffset.y == -contentInsets.top 이므로 더하면 0 이 된다 →
        //  내비바 높이가 기기마다 달라도 임계값이 흔들리지 않는다)
        //
        // ⚠️ 이 수정자는 **하위의 모든 스크롤뷰**에 걸린다. 퀵필터의 가로 ScrollView 도 걸려서
        // 그쪽 contentOffset.y(항상 0)가 값을 덮어써 버튼이 영영 안 떴다.
        // 세로로 넘치는 스크롤뷰만 취하고(가로 줄은 세로로 안 넘친다) 나머지는 NaN 으로 버린다.
        .onScrollGeometryChange(for: CGFloat.self) { geo in
            geo.contentSize.height > geo.containerSize.height + 1
                ? geo.contentOffset.y + geo.contentInsets.top
                : .nan
        } action: { _, travelled in
            guard !travelled.isNaN else { return }
            let show = travelled > 240
            if show != showScrollTop { withAnimation(GLGMotion.standard()) { showScrollTop = show } }
        }
        .overlay(alignment: .bottomTrailing) {
            if showScrollTop && !selectionMode {
                Button {
                    withAnimation(GLGMotion.standard()) { proxy.scrollTo(Self.topAnchor, anchor: .top) }
                } label: {
                    // 콘텐츠 위에 떠 있는 단독 버튼이라 헤더 아이콘보다 크게 잡는다(누르기 쉬워야 한다).
                    Image(systemName: "chevron.up")
                        .font(.pretendard(size: 18, weight: .bold))
                        .frame(width: 26, height: 26)
                }
                .glgGlassButton(circle: true, size: .large)
                .tint(accent.primary)
                .padding(.trailing, 16)
                .padding(.bottom, 12)
                .transition(.opacity.combined(with: .scale(scale: 0.8)))
            }
        }
        }
    }

    /// 맨 위로 버튼이 되돌아갈 앵커 id.
    private static let topAnchor = "spendingTop"


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
        } else if isWide {
            // iPad — 밀어 넣지 않고 **오른쪽 상세를 갈아 끼운다**. 지금 보고 있는 행은 배경으로 표시.
            Button { selectedId = s.id } label: {
                SpendingRow(spending: s, compact: store.spendingCompact)
                    .background(selectedId == s.id ? accent.primary.opacity(0.10) : Color.clear)
            }
            .buttonStyle(.plain)
        } else {
            // 값 기반 링크 — 목적지는 스택 루트가 한 번만 등록한다(ContentView).
            NavigationLink(value: SpendingRoute.detail(s.id)) {
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

    // ── 퀵필터 (리스트 상단) ──
    //
    // **드롭다운 3개**(기간·게임·정렬) + 걸린 나머지 필터 해제 줄.
    // 칩을 축마다 늘어놓으면 기간만 5개라 줄이 금방 넘쳤다. 축 하나당 알약 하나로 접고, 열면
    // 시스템 메뉴에서 고른다. 알약 라벨은 **기본값이면 축 이름, 아니면 고른 값** — 접힌 상태에서도
    // 지금 뭐가 걸렸는지 읽힌다.
    //
    // 결제 수단·구분은 드롭다운으로 두지 않는다(자주 안 바뀐다). 걸려 있으면 아랫줄에 해제 칩으로 뜬다.
    private var quickFilters: some View {
        VStack(alignment: .leading, spacing: 6) {
            ScrollView(.horizontal) {
                HStack(spacing: 6) {
                    // 기간 — 단일 선택.
                    quickMenu(label: period == .all ? "기간" : period.rawValue, active: period != .all) {
                        ForEach(PeriodFilter.allCases, id: \.self) { p in
                            Button { period = p } label: {
                                if period == p { Label(p.rawValue, systemImage: "checkmark") } else { Text(p.rawValue) }
                            }
                        }
                    }
                    // 날짜 두 칸은 이 줄에 두지 않는다 — '기간 지정'일 때만 아랫줄로 펼친다.
                    // 게임 — 다중 선택. iOS 메뉴는 고르면 닫히므로 여러 개는 다시 열어 켠다(시스템 동작).
                    quickMenu(label: gameMenuLabel, active: !gameFilters.isEmpty) {
                        Button { gameFilters = [] } label: {
                            if gameFilters.isEmpty { Label("전체", systemImage: "checkmark") } else { Text("전체") }
                        }
                        ForEach(GLGGames.keys, id: \.self) { key in
                            if let g = GameData.shared.byNameOrNull(name: key) {
                                Button {
                                    if gameFilters.contains(g.displayName) { gameFilters.remove(g.displayName) }
                                    else { gameFilters.insert(g.displayName) }
                                } label: {
                                    if gameFilters.contains(g.displayName) { Label(g.shortName, systemImage: "checkmark") }
                                    else { Text(g.shortName) }
                                }
                            }
                        }
                    }
                    // 정렬 — 거르는 게 아니라 늘어놓는 방식이지만, 기간만큼 자주 바꾼다.
                    quickMenu(label: sortOrder == .dateDesc ? "정렬" : sortOrder.rawValue, active: sortOrder != .dateDesc) {
                        ForEach(SortOrder.allCases, id: \.self) { s in
                            Button { sortOrder = s } label: {
                                if sortOrder == s { Label(s.rawValue, systemImage: "checkmark") } else { Text(s.rawValue) }
                            }
                        }
                    }
                }
                // 글래스 버튼은 글자 상자보다 크게 그려진다(하이라이트·그림자·눌림 효과).
                // 여백이 빠듯하면 스크롤뷰가 그 바깥을 잘라 위아래가 깎여 보인다.
                .padding(.vertical, 6)
                .padding(.horizontal, 1)
            }
            .scrollIndicators(.hidden)
            // 내용이 한 줄에 다 들어가면 좌우로 안 밀린다 — 안 넘칠 때도 스와이프가 먹으면
            // 리스트를 만지려던 손가락이 헛돈다.
            .scrollBounceBehavior(.basedOnSize, axes: .horizontal)

            // '기간 지정'을 고르면 아래로 펼쳐진다 — 평소엔 줄 자체가 없다.
            //
            // 닫힘은 **페이드만** 준다. 슬라이드로 닫으면 강조색으로 채워진 날짜 알약이 위쪽 줄을
            // 타고 올라가며 색 덩어리로 스쳐 보였다(자리는 이미 접혔는데 그림만 남아 지나감).
            if period == .custom {
                customRangeRow.transition(
                    .asymmetric(
                        insertion: .move(edge: .top).combined(with: .opacity),
                        removal: .opacity,
                    ),
                )
            }

            // 드롭다운에 없는 필터가 걸려 있으면 해제 칩. 없으면 줄 자체가 사라져 공간을 안 먹는다.
            if hasOtherFilters {
                ScrollView(.horizontal) {
                    HStack(spacing: 6) {
                        if let m = paymentFilter {
                            GLGGlassChip(label: "\(m)  ✕", selected: true) { paymentFilter = nil }
                        }
                        if typeFilter != .all {
                            GLGGlassChip(label: "\(typeFilter.rawValue)  ✕", selected: true) { typeFilter = .all }
                        }
                    }
                    .padding(.vertical, 6)
                    .padding(.horizontal, 1)
                }
                .scrollIndicators(.hidden)
                .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
            }
        }
        .padding(.bottom, 4)
        .animation(GLGMotion.standard(), value: period)
        // clipped() 는 쓰지 않는다 — 글래스 버튼의 하이라이트까지 잘려 위아래가 깎여 보인다.
    }

    /// 기간 지정 줄 — 시작 ~ 종료.
    ///
    /// 시스템 compact DatePicker 를 그대로 쓰면 **자기 크기를 스스로 정해서** 옆의 글래스 알약보다
    /// 크게 뜬다(한 줄에 두 높이가 섞임). 날짜도 같은 알약으로 만들고 탭하면 달력을 팝오버로
    /// 띄운다 — 크기가 구조적으로 같아진다.
    private var customRangeRow: some View {
        HStack(spacing: 6) {
            GLGGlassChip(label: dateChipLabel(customStart), selected: true) { showStartPicker = true }
                .popover(isPresented: $showStartPicker) { datePopover($customStart) }
            Text("~").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            GLGGlassChip(label: dateChipLabel(customEnd), selected: true) { showEndPicker = true }
                .popover(isPresented: $showEndPicker) { datePopover($customEnd) }
        }
        .padding(.vertical, 4)
        .padding(.horizontal, 1)
    }

    /// 달력 팝오버 — iPhone 도 시트가 아니라 팝오버로 붙인다(칩 바로 아래에서 고르고 닫는다).
    private func datePopover(_ selection: Binding<Date>) -> some View {
        DatePicker("", selection: selection, displayedComponents: .date)
            .datePickerStyle(.graphical)
            .labelsHidden()
            .tint(accent.primary)
            // 달력은 자기 고유 크기가 꽤 작아서, 그대로 두면 팝오버가 쪼그라들어 날짜를 찍기 어렵다.
            // 시스템 달력이 편하게 보이는 최소치를 준다(가장 좁은 iPhone 폭에도 들어간다).
            .frame(width: 320, height: 340)
            .padding(12)
            .presentationCompactAdaptation(.popover)
    }

    private static let dateChipFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "yyyy.MM.dd"
        return f
    }()
    private func dateChipLabel(_ d: Date) -> String { Self.dateChipFormatter.string(from: d) }

    /// 퀵필터 알약 하나 + 시스템 드롭다운 메뉴.
    ///
    /// ⚠️ **걸린 필터를 `.glassProminent`(강조색 채움)로 그리지 말 것.** 2026-08-03 실기기 확인:
    /// iOS 26+ 는 메뉴를 소스 버튼에서 뽑아내듯 모프시키고 닫을 때 역재생하는데, 소스가 강조색으로
    /// 꽉 찬 캡슐이면 **닫히는 내내 색 덩어리가 스쳐 보인다**. 시스템 애니메이션이라 우리 트랜잭션
    /// (`.transaction { $0.animation = nil }`)으로는 못 막는다 — 실제로 시도했고 효과 없었다.
    /// 채움을 빼자 즉시 사라졌다.
    ///
    /// 그래서 걸림은 **채움이 아니라 색**으로 알린다 — 강조색 글자 + 앞의 점. 둘 다 값 변경이라
    /// 뷰 교체가 없고, 모프가 통째로 스냅샷을 떠도 번질 색 면적이 없다.
    /// (`GLGGlassChip` 은 `.glassProminent` 를 계속 쓰지만 **`Menu` 가 아니라 `Button`** 이라
    ///  모프 대상이 아니다 — 그래서 ✕ 해제 칩·날짜 알약은 멀쩡하다)
    private func quickMenu<C: View>(label: String, active: Bool, @ViewBuilder content: () -> C) -> some View {
        Menu {
            content()
        } label: {
            HStack(spacing: 0) {
                // 걸림 표시 점 — 안 걸렸으면 **폭 0** 이라 글자 왼쪽에 빈 자리가 남지 않는다.
                //
                // 점을 `if active` 로 넣었다 뺐다 하지 않는 이유: 그건 뷰 교체라 SwiftUI 가 지웠다
                // 새로 만들고, 그 자리에 전환 애니메이션이 붙을 여지가 생긴다. 폭·여백을 **값으로**
                // 0 과 5 사이에서 바꾸면 같은 뷰가 그대로 남아 그럴 일이 없다.
                Circle()
                    .fill(active ? accent.primary : Color.clear)
                    .frame(width: active ? 6 : 0, height: 6)
                    .padding(.trailing, active ? 5 : 0)
                Text("\(label)  ▾")
            }
        }
        .font(.pretendard(size: 13, weight: .bold))
        .glgGlassChipStyle(selected: false)
        .tint(active ? accent.primary : GLGColor.textSecondary)
    }

    /// 접힌 상태에서도 몇 개가 걸렸는지 보이게 — 0개=축 이름, 1개=게임 약칭, 그 이상=개수.
    private var gameMenuLabel: String {
        if gameFilters.isEmpty { return "게임" }
        if gameFilters.count == 1, let n = gameFilters.first {
            return GameData.shared.byNameOrNull(name: n)?.shortName ?? n
        }
        return "게임 \(gameFilters.count)"
    }

    /// 드롭다운이 안 다루는 필터가 걸려 있는가 — 없으면 해제 줄을 아예 그리지 않는다.
    private var hasOtherFilters: Bool {
        paymentFilter != nil || typeFilter != .all
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
                    filterSection("기간") {
                        VStack(alignment: .leading, spacing: 10) {
                            pillWrap(PeriodFilter.allCases, period) { period = $0 } label: { $0.rawValue }
                            // '기간 지정'을 고른 경우에만 날짜 두 칸을 편다 — 평소엔 시트가 길어지지 않는다.
                            if period == .custom {
                                Divider().overlay(GLGColor.divider)
                                customRangePickers
                            }
                        }
                    }
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
                        customStart = Calendar.current.date(byAdding: .month, value: -1, to: Date()) ?? Date()
                        customEnd = Date()
                    }
                }
                ToolbarItem(placement: .confirmationAction) { Button("적용") { showFilter = false } }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationBackground(Color.white)
    }

    // 기간 지정 — 시작·종료 날짜. 종료일은 **그날 전체를 포함**한다(자정 경계에서 하루가 빠져 보이지 않게).
    private var customRangePickers: some View {
        VStack(alignment: .leading, spacing: 8) {
            DatePicker(selection: $customStart, displayedComponents: .date) {
                Text("시작").font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            }
            DatePicker(selection: $customEnd, displayedComponents: .date) {
                Text("종료").font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            }
        }
        .datePickerStyle(.compact)
        .tint(accent.primary)
    }

    /// 기간 지정 범위를 밀리초 [시작, 끝) 으로. 시작>끝이면 뒤집어 준다(빈 결과 대신 의도대로).
    private var customRangeMillis: (Int64, Int64) {
        let cal = Calendar.current
        let a = cal.startOfDay(for: customStart)
        let b = cal.startOfDay(for: customEnd)
        let lo = min(a, b)
        let hiDay = max(a, b)
        let hi = cal.date(byAdding: .day, value: 1, to: hiDay) ?? hiDay
        return (Int64(lo.timeIntervalSince1970 * 1000), Int64(hi.timeIntervalSince1970 * 1000))
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
        // 기간 지정 범위도 항목 밖에서 한 번만 — 항목마다 Calendar 연산을 반복할 이유가 없다.
        let range = period == .custom ? customRangeMillis : (Int64(0), Int64(0))
        return list.filter { s in
            (gameFilters.isEmpty || gameFilters.contains(s.gameName)) &&
            (paymentFilter == nil || s.paymentMethod == paymentFilter) &&
            (typeFilter == .all || (typeFilter == .normal ? !s.isSubscription : s.isSubscription)) &&
            periodMatch(s, dy, dm, ly, lm, range)
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
    private func periodMatch(_ s: Spending, _ dy: Int, _ dm: Int, _ ly: Int, _ lm: Int, _ range: (Int64, Int64)) -> Bool {
        switch period {
        case .all: return true
        case .thisMonth: return DateMillis.isSameMonth(s.dateMillis, dy, dm)
        case .lastMonth: return DateMillis.isSameMonth(s.dateMillis, ly, lm)
        case .thisYear: return DateMillis.isSameYear(s.dateMillis, dy)
        case .custom: return s.dateMillis >= range.0 && s.dateMillis < range.1
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

