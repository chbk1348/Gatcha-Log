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
                            Text(entry.version).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(accent.primary)
                            ForEach(Array(entry.items.enumerated()), id: \.offset) { _, item in
                                HStack(alignment: .top, spacing: 2) {
                                    Text("· ").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                                    Text(item).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
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
            Entry(version: "v27.30.0", items: [
                "앱 전체 디자인을 새로 단장했어요 — 카드·칩·버튼을 통일하고, 화면이 부드럽게 떠오르는 모션과 로딩 스켈레톤을 더했어요",
                "전역 글꼴을 Pretendard로 적용하고, 기기 글꼴 크기와 무관하게 일관된 레이아웃을 유지해요",
                "지출 내역 상단 ‘이번 달 지출’을 히어로 영역으로 — 스크롤하면 자연스럽게 접혀요",
                "게임 정보에 ‘주년’ 섹션과 ‘공지·뉴스’ 섹션을 추가했어요 (공지는 ‘더보기’로 전체 보기)",
                "젠레스 존 제로 이벤트 일정을 한국어로 보여드려요",
                "캐릭터 상세에 ‘돌파 효과’(운명의 자리·성혼·형상 시네마)를 활성/비활성과 설명까지 추가했어요",
                "예산 관리·설정·상세 필터 화면을 카드형으로 정리했어요",
            ]),
            Entry(version: "v27.28.1", items: [
                "구글 로그인이 더 편해졌어요 — 기기에 구글 계정이 없어도 로그인되고, 로그인 후 브라우저가 자동으로 닫혀요",
                "캐릭터 상세를 새로 단장했어요 — 속성·운명의 길과 세트 효과까지 한눈에",
                "스타레일 ‘환락’ 운명의 길을 새로 표시해요",
                "젠레스 존 제로 3.0에 대응했어요 — ‘바람’ 속성과 W-엔진 표기 추가",
                "보유 캐릭터 목록의 필터·정렬과 동선을 보강했어요",
                "클라우드 동기화 안정성을 개선하고 내부 데이터 구조를 정리했어요",
            ]),
            Entry(version: "v27.28.0", items: [
                "‘내 캐릭터’ — 보유 캐릭터 전체를 스탯·무기·유물까지 한눈에 봐요 (원신·스타레일·젠레스)",
                "젠레스 존 제로 캐릭터를 지원해요 — 음동기·드라이브 디스크까지",
                "HoYoLAB 연동만 하면 캐릭터 UID가 자동으로 설정돼요",
                "캐릭터 목록 등급 필터(전체·5성·4성)와 대표 4명·더보기를 더했어요",
                "캐릭터·광추·음동기 이름과 스탯을 공식 한국어로 표기해요",
                "내부 안정성과 성능을 개선했어요",
            ]),
            Entry(version: "v27.27.0", items: [
                "마이페이지를 대시보드로 새단장했어요 — 이번 달 지출·월별 추이·활동 지표·게임별 비중을 한눈에 봐요",
                "내부 구조를 정리해 안정성과 iOS·안드로이드 동작 일관성을 높였어요",
            ]),
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
