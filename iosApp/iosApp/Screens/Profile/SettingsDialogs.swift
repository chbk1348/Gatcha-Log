import SwiftUI
import ComposeApp

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
            Form {
                Section("전체 월 예산") {
                    TextField("예산 (원)", text: $overall)
                        .keyboardType(.numberPad)
                        .onChange(of: overall) { overall = $0.filter(\.isNumber) }
                }
                Section {
                    ForEach(games, id: \.key) { game in
                        let spent = monthlyTotals[game.key] ?? 0
                        let limit = Int64(perGame[game.key] ?? "") ?? 0
                        let over = limit > 0 && spent > limit
                        HStack(spacing: 10) {
                            Circle().fill(Color(argb64: game.color)).frame(width: 10, height: 10)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(game.shortName).font(.system(size: 14, weight: .medium))
                                Text("이번 달 \(won(spent))")
                                    .font(.system(size: 11))
                                    .foregroundStyle(over ? GLGColor.dangerText : GLGColor.textSecondary)
                                    .fontWeight(over ? .bold : .regular)
                            }
                            Spacer()
                            TextField("한도", text: bindGame(game.key))
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 110)
                        }
                    }
                } header: { Text("게임별 한도 (선택)") }
                footer: { Text("비워두면 한도 없음 · 이번 달 사용액 함께 표시") }
            }
            .navigationTitle("예산 관리")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("저장") { save() } }
            }
            .onAppear(perform: load)
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

// ── 넛지 기준 금액 ────────────────────────────────────────────────────────────

struct NudgeThresholdSheet: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.dismiss) private var dismiss
    @State private var text: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("기준 금액 (원)", text: $text)
                        .keyboardType(.numberPad)
                        .onChange(of: text) { text = $0.filter(\.isNumber) }
                } footer: {
                    Text("단건 지출이 이 금액 이상이면 추가 전 한 번 더 확인해요.")
                }
            }
            .navigationTitle("넛지 기준 금액")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") { store.setNudgeThreshold(Int64(text) ?? 0); dismiss() }
                }
            }
            .onAppear { text = store.nudgeThreshold > 0 ? "\(store.nudgeThreshold)" : "" }
        }
    }
}

// ── 출처 · 저작권 ─────────────────────────────────────────────────────────────

struct CreditsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("본 앱은 개인이 만든 비상업·비공식 팬 프로젝트로 HoYoverse와 무관하며 공식 서비스가 아닙니다.")
                        .font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    creditRow("게임 콘텐츠 · 아이콘 저작권",
                              "© HoYoverse (miHoYo / Cognosphere) — 원신 · 붕괴: 스타레일 · 젠레스 존 제로\n© Kuro Games — 명조: 워더링 웨이브\n© Hypergryph / Yostar — 명일방주: 엔드필드")
                    creditRow("데이터 · 에셋 출처",
                              "enka.network · Project Amber (yatta.moe)\nHoYoLAB · ennead.cc")
                    Text("모든 게임 콘텐츠의 권리는 각 권리자에게 있으며, 권리자의 요청이 있을 경우 즉시 해당 자료를 삭제합니다.")
                        .font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
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
            Text(label).font(.system(size: 13, weight: .bold)).foregroundStyle(accent.primary)
            Text(value).font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
        }
    }
}

// ── 업데이트 로그 ─────────────────────────────────────────────────────────────

struct UpdateLogSheet: View {
    let version: String
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ForEach(Array(UpdateLog.entries(currentVersion: version).enumerated()), id: \.offset) { _, entry in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(entry.version).font(.system(size: 14, weight: .bold)).foregroundStyle(accent.primary)
                            ForEach(Array(entry.items.enumerated()), id: \.offset) { _, item in
                                HStack(alignment: .top, spacing: 2) {
                                    Text("· ").font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                                    Text(item).font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                                }
                            }
                        }
                    }
                }
                .padding(20)
            }
            .background(GLGBackground { Color.clear })
            .navigationTitle("업데이트 로그")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("확인") { dismiss() } } }
        }
    }
}

private enum UpdateLog {
    struct Entry { let version: String; let items: [String] }

    static func entries(currentVersion: String) -> [Entry] {
        [
            Entry(version: "v\(currentVersion.isEmpty ? "27.11.0" : currentVersion)", items: [
                "홈을 더 똑똑하게 — ‘오늘 할 일’이 출석·재화·픽업·예산을 모아 보여주고, 누르면 해당 화면으로 바로 이동해요",
                "‘이번 달 한눈에’가 지난달 대비·예상 지출·천장 상황까지 분석해 알려드려요",
                "‘가챠 현황’ 카드 — 다음 픽업 확정에 필요한 뽑기 수와 예상 비용을 한눈에",
                "레진·개척력·배터리가 가득 차기 전에 미리 챙기라고 알려드려요 + 픽업 종료 임박 시 ‘몇 시간 남음’ 표시",
                "[중요] 리딤코드 교환이 안 되던 문제 수정 — HoYoLAB 재연동 후 정상 교환돼요",
                "화면 불러오기 속도 개선 + 지출·게임 정보 화면 디자인 정리",
            ]),
            Entry(version: "v27.10.0", items: [
                "홈 화면을 새 디자인으로 개편했어요 — 이번 달 요약·남은 예산·연속 출석·게임별 예산을 한눈에",
                "‘이번 달 한눈에’ 요약 카드 추가 — 지출·예산·천장 상황을 매일 다른 문구로 알려드려요",
                "출석체크에 ‘전체 출석’ 버튼 추가 — 미출석 게임을 한 번에 체크인",
                "게임 정보 출석에서 ‘한 달 보기’로 월간 출석 달력을 바로 펼쳐볼 수 있어요",
                "임박한 픽업 배너를 홈에서 D-day로 안내 + HoYoLAB 불러오기 속도 개선",
            ]),
            Entry(version: "v27.9.0", items: [
                "게임별 월 예산 한도를 따로 정할 수 있어요 — 초과하면 알림·홈에서 안내",
                "충동구매 예방 넛지 — 지출 추가 시 예산이나 평소보다 큰 금액이면 한 번 더 확인해요",
                "통합 캘린더 추가 — 월 달력에서 일별 지출·출석·픽업 배너 일정을 한눈에",
                "가챠 뽑기 시뮬레이터 — 실제 확률·천장으로 '탭해서 뽑기'를 체험해보세요",
                "지출 내역·헤더 버튼 디자인 다듬기",
            ]),
            Entry(version: "v27.8.0", items: [
                "앱 아이콘을 새 디자인(밤하늘 위 반짝이는 위시 스타)으로 단장했어요",
                "로딩·로그인 화면 로고와 앱 색감을 새 아이콘 톤에 맞춰 정리했어요",
                "모든 금액을 1,234원 형식(콤마+원)으로 통일해 읽기 쉽게",
                "알림 배지가 한 번 확인하면 다시 뜨지 않도록 수정",
                "패치 일정에 버전 시작·종료 날짜를 함께 표시",
            ]),
            Entry(version: "v27.7.0", items: [
                "HoYoLAB 토큰 만료를 자동 감지해 홈 상단에서 재연동을 안내",
                "천장 카운터에 임박 단계 강조(주의·임박·도달) + 단계 진입 시 토스트 안내",
                "위시리스트를 전 게임으로 확장 + 위시 캐릭터가 픽업 배너에 뜨면 알림",
                "지출 추가를 풀스크린 페이지로 개편",
            ]),
            Entry(version: "v27.5.0", items: [
                "HoYoLAB 로그인 한 번으로 토큰·게임 UID 자동 입력",
                "HoYoLAB 리딤코드 자동 수집 + 한 번에 교환",
                "HoYoLAB 계정 연동을 전용 페이지로 개편",
            ]),
            Entry(version: "v27.4.0", items: [
                "재설치·기기 변경 후 데이터 복원 안정화",
                "백업 파일 내보내기/복원 추가 (설정 ▸ 백업·복원)",
                "로그인 방식 개선 (Credential Manager)",
                "오프라인에서 앱이 로딩 화면에 멈추던 문제 수정",
            ]),
            Entry(version: "v27.3.0", items: [
                "지출 내역 실제 인게임 재화 아이콘 + 상세 페이지",
                "HoYoLAB 연동 정보 구글 계정 동기화 안정화",
                "출석 기준 시간 베이징 표준시(UTC+8)로 정정",
                "인앱 자동 업데이트, 리딤코드 교환, 젠레스 픽업 배너",
            ]),
        ]
    }
}
