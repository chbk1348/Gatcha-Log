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
            Form {
                Section("전체 월 예산") {
                    TextField("예산 (원)", text: $overall)
                        .keyboardType(.numberPad)
                        .onChange(of: overall) { _, newValue in overall = newValue.filter(\.isNumber) }
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
                        .onChange(of: text) { _, newValue in text = newValue.filter(\.isNumber) }
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

    // iOS 전용 업데이트 로그 — Android 와 별도 작성/관리. (iOS 릴리스 태그: vX.Y.Z-ios)
    static func entries(currentVersion: String) -> [Entry] {
        [
            Entry(version: "v27.26.0", items: [
                "충전 가성비 비교를 추가했어요 — 호요 3종 패키지의 단가·뽑 환산을 한눈에 비교해요",
                "게임 정보에서 스타레일 광추(돌파) 픽업 배너도 무기처럼 표시해요",
                "구글 로그인 안정성을 다듬었어요 — 인증 도중 종료돼도 계정 저장소가 어긋나지 않아요",
                "내부 구조를 정리해 안정성과 동작 속도를 개선했어요",
            ]),
            Entry(version: "v27.25.0", items: [
                "통합 계산기를 새 대시보드로 개편했어요 — 게임·배너 선택부터 확보 확률·필요 재화·시나리오까지 한 화면에서",
                "가챠 효율 리포트를 개편했어요 — 게임별 카드와 운(행운) 분포를 한눈에",
                "게임 정보 탭 2.0 — 페이지 구성과 상단 게임 드롭다운으로 정리하고 디자인을 통일했어요",
                "게임 일정을 통합했어요 — 픽업 배너·게임별 그룹·패치 전반/후반 구분을 상단에 모았어요",
                "프로필 쇼케이스 — 캐릭터 정보를 자동으로 불러오고, HoYoLAB 연동·타임아웃 재시도를 더했어요",
                "로딩 화면을 개편했어요 — 동기화가 끝날 때까지 깔끔하게 대기해요",
                "구글 로그인을 웹 로그인 방식으로 바꿨어요 — 기기에 구글 계정이 없어도 로그인할 수 있어요",
                "카드 디자인을 깔끔한 흰 배경으로 다듬어 화면 전환·스크롤을 더 가볍게 했어요",
                "세부 화면 상단에 제목을 표시하고, 입력 시트 배경을 통일했어요",
                "천장 카운터·위시리스트를 정리하고, 알림 토스트가 화면마다 중복되던 문제를 고쳤어요",
                "클라우드 동기화 안정성과 속도를 개선했어요 (중복 전송 생략·용량 경고)",
                "iOS를 네이티브(SwiftUI)로 전환하는 작업을 마무리했어요",
            ]),
            Entry(version: "v27.20.0", items: [
                "마이페이지를 새로 정리했어요 — 계정 정보를 상단 프로필 카드 한 곳으로 모았어요 (로그인·로그아웃도 여기서)",
                "설정을 ‘자주 쓰는 설정 · 데이터·계정 · 앱 정보’ 순으로 정리했어요",
                "실수 방지 — 가챠 기록 초기화·지출 전체 삭제는 2단계로 확인하고, 삭제 전 백업을 권장해드려요",
                "입력창을 알약형 디자인으로 깔끔하게 다듬었어요",
            ]),
        ]
    }
}
