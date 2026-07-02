import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 설정 다이얼로그/시트 — 예산·넛지 기준·업데이트 로그·출처. (Compose BudgetDialog/SettingsDialogs 대응)
// ════════════════════════════════════════════════════════════════════════════

// ── 예산 관리 ────────────────────────────────────────────────────────────────

struct BudgetSheet: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent

    @State private var overall: String = ""
    @State private var perGame: [String: String] = [:]

    private let games = GameData.shared.games
    private var monthlyTotals: [String: Int64] { store.monthlyTotalsByGame() }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // 전체 월 예산 — 섹션 카드(지출 추가 모달과 동일 규격: 연회색 카드)
                    budgetSection("전체 월 예산") {
                        TextField("예산 (원)", text: $overall)
                            .textFieldStyle(.plain)
                            .keyboardType(.numberPad)
                            .glgPillField()
                            .onChange(of: overall) { _, newValue in overall = newValue.filter(\.isNumber) }
                    }
                    // 게임별 한도 — 섹션 카드
                    budgetSection("게임별 한도 (선택)", footer: "비워두면 한도 없음 · 이번 달 사용액 함께 표시") {
                        VStack(spacing: 12) {
                            ForEach(games, id: \.key) { game in
                                let spent = monthlyTotals[game.key] ?? 0
                                let limit = Int64(perGame[game.key] ?? "") ?? 0
                                let over = limit > 0 && spent > limit
                                HStack(spacing: 10) {
                                    Circle().fill(Color(argb64: game.color)).frame(width: 10, height: 10)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(game.shortName).font(.pretendard(size: 14, weight: .medium))
                                        Text("이번 달 \(won(spent))")
                                            .font(.pretendard(size: 11))
                                            .foregroundStyle(over ? GLGColor.dangerText : GLGColor.textSecondary)
                                            .fontWeight(over ? .bold : .regular)
                                    }
                                    Spacer()
                                    TextField("한도", text: bindGame(game.key))
                                        .textFieldStyle(.plain)
                                        .keyboardType(.numberPad)
                                        .multilineTextAlignment(.trailing)
                                        .glgPillField()
                                        .frame(width: 120)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 16)
            }
            .scrollIndicators(.hidden)
            .background(Color.white)
            .navigationTitle("예산 관리")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("저장") { save() } }
            }
            .onAppear(perform: load)
        }
    }

    // 예산 섹션 카드 — 제목(카드 위) + 연회색 카드(지출 추가 모달 sectionCard 와 동일 규격). 선택적 footer.
    @ViewBuilder
    private func budgetSection<C: View>(_ title: String, footer: String? = nil, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary).padding(.leading, 4)
            content()
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            if let footer {
                Text(footer).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.leading, 4)
            }
        }
    }

    private func bindGame(_ key: String) -> Binding<String> {
        Binding(get: { perGame[key] ?? "" }, set: { perGame[key] = $0.filter(\.isNumber) })
    }
    private func load() {
        overall = store.budget > 0 ? "\(store.budget)" : ""
        for g in games {
            let v = store.gameBudgets[g.key] ?? 0
            perGame[g.key] = v > 0 ? "\(v)" : ""
        }
    }
    private func save() {
        var per: [String: Int64] = [:]
        for (k, v) in perGame { if let n = Int64(v), n > 0 { per[k] = n } }
        store.setBudgets(overall: Int64(overall) ?? 0, perGame: per)
        dismiss()
    }
}

// 넛지 기준 금액 — 단일 입력이라 SettingsView 에서 네이티브 alert(중앙 모달)로 직접 노출(별도 시트 폐기).

// ── 출처 · 저작권 ─────────────────────────────────────────────────────────────

struct CreditsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("본 앱은 개인이 만든 비상업·비공식 팬 프로젝트로 HoYoverse와 무관하며 공식 서비스가 아닙니다.")
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    creditRow("게임 콘텐츠 · 아이콘 저작권",
                              "© HoYoverse (miHoYo / Cognosphere) — 원신 · 붕괴: 스타레일 · 젠레스 존 제로\n© Kuro Games — 명조: 워더링 웨이브\n© Hypergryph / Yostar — 명일방주: 엔드필드")
                    creditRow("데이터 · 에셋 출처",
                              "enka.network · HoYoLAB · ennead.cc\nProject Amber (yatta.moe) · Hakush.in")
                    Text("모든 게임 콘텐츠의 권리는 각 권리자에게 있으며, 권리자의 요청이 있을 경우 즉시 해당 자료를 삭제합니다.")
                        .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
                .padding(20)
            }
            .background(GLGBackground { Color.clear })
            .navigationTitle("출처 · 저작권")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("확인") { dismiss() } } }
        }
    }

    private func creditRow(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
            Text(value).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
        }
    }
}

// ── 업데이트 로그 ─────────────────────────────────────────────────────────────

/// 업데이트 로그 — 06_ChangeLog.html 목업 디자인(히어로·필터칩·featured·마일스톤 타임라인·분류 뱃지).
/// 데이터는 공통 정본 `ChangeLog`(KMP)에서 읽어 Android와 동일하다.
struct UpdateLogPage: View {
    let version: String
    @Environment(\.glgAccent) private var accent
    @State private var filter: String? = nil   // ChangeKind.key("new"/"imp"/"fix"/"sec"), nil=전체

    private let cText = Color(hex: 0xFF15181C)
    private let cItem = Color(hex: 0xFF2A2E34)
    private let cLine = Color(hex: 0xFFE3E5EA)

    private var entries: [ChangeEntry] {
        let all = ChangeLog.shared.entries
        guard let f = filter else { return all }
        return all.filter { e in e.items.contains { $0.kind.key == f } }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                hero
                Section {
                    Color.clear.frame(height: 14)
                    ForEach(entries, id: \.version) { entry in releaseCard(entry) }
                    if entries.isEmpty {
                        Text("해당 분류의 변경 사항이 없어요")
                            .font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
                            .frame(maxWidth: .infinity).padding(.vertical, 40).padding(.horizontal, 18)
                    }
                } header: { filterBar }
            }
            .padding(.bottom, 40)
        }
        .background(Color.white)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        // 반투명 네비바로 스크롤 콘텐츠가 필터바 위로 비치는 것 방지 — 불투명 흰 배경 고정.
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarBackground(Color.white, for: .navigationBar)
    }

    // ── 히어로 ──
    private var hero: some View {
        VStack(alignment: .leading, spacing: 0) {
            (Text("업데이트 ").foregroundColor(cText) + Text("기록").foregroundColor(accent.primary))
                .font(.pretendard(size: 28, weight: .heavy)).padding(.top, 12)
            Text("사용자 관점으로 정리한 전체 변경 이력")
                .font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textSecondary).padding(.top, 6)
            HStack(spacing: 20) {
                metaCol("\(ChangeLog.shared.entries.count)개", "전체 버전")
                metaCol(ChangeLog.shared.periodLabel, "업데이트 기간")
            }.padding(.top, 14)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 18)
        .padding(.bottom, 14)
    }

    private func metaCol(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(cText)
            Text(label).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
        }
    }

    // ── 스티키 필터칩 ──
    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                GLGChip(label: "전체", selected: filter == nil) { filter = nil }
                GLGChip(label: "신규", selected: filter == "new", color: kindColors("new").0) { filter = "new" }
                GLGChip(label: "개선", selected: filter == "imp", color: kindColors("imp").0) { filter = "imp" }
                GLGChip(label: "수정", selected: filter == "fix", color: kindColors("fix").0) { filter = "fix" }
                GLGChip(label: "보안", selected: filter == "sec", color: kindColors("sec").0) { filter = "sec" }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
        }
        .background(Color.white)
        .overlay(alignment: .bottom) { Rectangle().fill(cLine).frame(height: 1) }
    }

    // ── 릴리스 카드 ──
    @ViewBuilder
    private func releaseCard(_ entry: ChangeEntry) -> some View {
        let items = filter == nil ? entry.orderedItems : entry.orderedItems.filter { $0.kind.key == filter }
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                if entry.featured {
                    Text("최신 버전").font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(Color(hex: 0xFF15C7A8), in: Capsule()).padding(.bottom, 10)
                }
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    (Text(entry.milestone && !entry.featured ? "★ " : "").foregroundColor(accent.primary)
                        + Text("v\(entry.version)").foregroundColor(cText))
                        .font(.pretendard(size: entry.featured ? 24 : 18, weight: .heavy))
                    Text(entry.date).font(.pretendard(size: 12.5, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
                    Spacer()
                    if let pill = entry.pill { pillView(pill, false) }
                    if entry.securityPill { pillView("보안 필수", true) }
                }.padding(.bottom, 10)
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    let c = kindColors(item.kind.key)
                    HStack(alignment: .top, spacing: 10) {
                        Text(item.kind.label).font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(c.2)
                            .frame(minWidth: 34).padding(.horizontal, 7).padding(.vertical, 2)
                            .background(c.1, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                        Text(item.text).font(.pretendard(size: 14)).foregroundStyle(cItem)
                        Spacer(minLength: 0)
                    }.padding(.vertical, 5)
                }
            }
            .padding(entry.featured ? 22 : 18)
            .background(cardBackground(entry))
            .overlay(cardBorder(entry))
            .padding(.horizontal, 18)
            .padding(.bottom, 12)
        }
    }

    @ViewBuilder
    private func cardBackground(_ e: ChangeEntry) -> some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        if e.featured {
            shape.fill(LinearGradient(colors: [Color(hex: 0xFFF1FBF9), .white], startPoint: .topLeading, endPoint: .bottomTrailing))
        } else if e.milestone {
            shape.fill(Color(hex: 0xFFF6F7F9))
        } else {
            shape.fill(Color.white)
        }
    }

    @ViewBuilder
    private func cardBorder(_ e: ChangeEntry) -> some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        if e.featured { shape.stroke(Color(hex: 0xFFE5F8F4), lineWidth: 1) }
        else if !e.milestone { shape.stroke(cLine, lineWidth: 1) }
    }

    private func pillView(_ text: String, _ sec: Bool) -> some View {
        Text(text).font(.pretendard(size: 11, weight: .bold))
            .foregroundStyle(sec ? Color(hex: 0xFFD43A3A) : Color(hex: 0xFF0E9C84))
            .padding(.horizontal, 9).padding(.vertical, 3)
            .background(sec ? Color(hex: 0xFFFDECEC) : Color(hex: 0xFFE5F8F4), in: Capsule())
    }

    // 분류별 색(점, 뱃지 배경, 뱃지 글자) — 목업 고정값.
    private func kindColors(_ key: String) -> (Color, Color, Color) {
        if key == "imp" { return (Color(hex: 0xFF3B82F6), Color(hex: 0xFFE8F0FE), Color(hex: 0xFF2563EB)) }
        if key == "fix" { return (Color(hex: 0xFFF59E0B), Color(hex: 0xFFFEF3DD), Color(hex: 0xFFB45309)) }
        if key == "sec" { return (Color(hex: 0xFFEF4444), Color(hex: 0xFFFDECEC), Color(hex: 0xFFD43A3A)) }
        return (Color(hex: 0xFF15C7A8), Color(hex: 0xFFE5F8F4), Color(hex: 0xFF0E9C84)) // new
    }
}
