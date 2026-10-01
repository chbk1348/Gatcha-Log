import SwiftUI
import UniformTypeIdentifiers
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 설정 — 계정·테마·예산/연동·자동화·알림·데이터·백업·정보. (Compose SettingsScreen 대응)
// 네이티브 List+Section + Toggle + .alert/.sheet/.fileExporter/.fileImporter.
// HoYoLAB 연동은 네이티브 HoyolabLinkView(WKWebView) 를 페이지 푸시로 호스팅(앱 내 다른 진입점·Android와 통일).
// ════════════════════════════════════════════════════════════════════════════

struct SettingsView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    /// 프로젝트 저장소 홈. OTA·릴리즈는 같은 저장소의 raw/releases 경로를 쓴다.
    private static let githubRepoURL = "https://github.com/chbk1348/Gatcha-Log"

    // 시트/다이얼로그 상태
    @State private var showBudget = false
    @State private var showNudge = false
    @State private var showHoyolab = false
    @State private var showNotifSettings = false
    @State private var showDataManagement = false
    @State private var showUplog = false
    @State private var showCredits = false
    @State private var showTheme = false
    @State private var showMyGames = false
    #if DEBUG
    @State private var showDeveloper = false
    #endif

    private var version: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "—"
    }

    /// 빌드 종류 구분 태그 — 어떤 빌드가 설치됐는지 한눈에(Android BuildVariantChip 파리티).
    ///
    /// **EXPERIMENT(빨강)가 최우선**이다. 실험 빌드는 릴리스 구성으로 말아도 릴리스가 아니므로,
    /// RELEASE 로 보이면 배포본과 헷갈린다. 표식은 project.yml 의
    /// `SWIFT_ACTIVE_COMPILATION_CONDITIONS` 가 정한다.
    private var buildVariantChip: some View {
        #if EXPERIMENT
        let label = "EXPERIMENT"
        let color = Color(hex: 0xFFE5342A) // 빨강 — 실험 빌드 경고
        #elseif DEBUG
        let label = "DEBUG"
        let color = Color(hex: 0xFFFF7A45)
        #else
        let label = "RELEASE"
        let color = accent.primary
        #endif
        return Text(label)
            .font(.pretendard(size: 10, weight: .bold))
            .foregroundStyle(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.15), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    var body: some View {
        ScrollView {
            // GLDS 2.0 — 카드를 걷고 화면 폭 섹션 + 회색 띠(마이페이지 3.0 과 같은 규격).
            // (계정은 마이페이지 히어로로 일원화 — 중복 카드 제거)
            VStack(alignment: .leading, spacing: 0) {
                notificationLinkSection
                SetBand()
                budgetLinkSection
                SetBand()
                automationSection
                SetBand()
                displaySection
                SetBand()
                dataManagementLinkSection
                // 개발자 메뉴 — 릴리스 빌드에는 이 섹션 자체(띠 포함)가 컴파일되지 않는다.
                #if DEBUG
                SetBand()
                developerLinkSection
                #endif
                SetBand()
                infoSection
            }
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .glgPageTitle("설정")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            // 홈 만료 배너 CTA → 설정 → HoYoLAB 연동 자동 진입
            if store.pendingOpenHoyolabLink {
                showHoyolab = true
                store.consumePendingOpenHoyolabLink()
            }
        }
        // 예산 관리 — 팝업에서 페이지로(아티팩트 S3).
        .navigationDestination(isPresented: $showBudget) { BudgetSettingsView(store: store) }
        .sheet(isPresented: $showCredits) { CreditsSheet() }
        // 넛지 기준 금액 — iOS 26+ 는 작은 **시스템 시트**(9/30). 글래스 알림창은 입력칸이 오른쪽으로 넘쳐
        // (앱과 무관한 OS 버그, 27.1 시뮬에서 재현) 시트 + 헤더 취소 · 저장. iOS 18 은 알림창 유지. Android 는 GlgDialog.
        .sheet(isPresented: $showNudge) {
            NudgeThresholdSheet(text: store.nudgeThreshold > 0 ? "\(store.nudgeThreshold)" : "") { v in
                store.setNudgeThreshold(Int64(String(v.filter(\.isNumber).prefix(9))) ?? 0)
            }
        }
        .navigationDestination(isPresented: $showHoyolab) {
            HoyolabLinkView(store: store) { showHoyolab = false }
        }
        .navigationDestination(isPresented: $showNotifSettings) {
            NotificationSettingsView(store: store)
        }
        .navigationDestination(isPresented: $showDataManagement) {
            DataManagementView(store: store)
        }
        .navigationDestination(isPresented: $showUplog) {
            UpdateLogPage(version: version)
        }
        .navigationDestination(isPresented: $showTheme) {
            ThemeView(store: store)
        }
        .navigationDestination(isPresented: $showMyGames) {
            MyGamesView(store: store)
        }
        #if DEBUG
        .navigationDestination(isPresented: $showDeveloper) {
            DeveloperView(store: store)
        }
        #endif
    }

    #if DEBUG
    @ViewBuilder
    private var developerLinkSection: some View {
        SetSection(title: "개발자", caption: "디버그 빌드 전용") {
            SetNavRow(symbol: "ladybug", tint: .red, title: "개발자 메뉴", value: "상태 만들기 · 진단") { showDeveloper = true }
        }
    }
    #endif

    // ── 화면 — 표시(컴팩트 · 연출) + 테마 ──
    @ViewBuilder
    private var displaySection: some View {
        SetSection(title: "화면", caption: "표시 · 테마") {
            SetToggleRow(symbol: "list.bullet", tint: .slate, title: "지출 내역 컴팩트 보기",
                         desc: "지출 목록을 한 줄로 빽빽하게 (태그 · 결제수단 숨김)",
                         isOn: bind(\.spendingCompact, store.setSpendingCompact))
            SetDivider()
            SetToggleRow(symbol: "bolt.fill", tint: .pink, title: "캐릭터 속성 연출",
                         desc: "캐릭터 상세에 들어갈 때 속성 효과를 한 번 재생",
                         isOn: bind(\.charElementFx, store.setCharElementFx))
            SetDivider()
            // 20색이 되어 카드 안 그리드로는 길어져 전용 페이지로 옮겼다.
            SetNavRow(symbol: "paintpalette", tint: .purple, title: "테마",
                      value: GLGTheme.accent(store.accentIndex).label) { showTheme = true }
        }
    }

    // ── 내 게임 · 예산 ──
    @ViewBuilder
    private var budgetLinkSection: some View {
        SetSection(title: "내 게임 · 예산", caption: "온보딩에서 고른 값과 같아요") {
            SetNavRow(symbol: "gamecontroller", tint: .purple, title: "내 게임", value: myGamesLabel) { showMyGames = true }
            SetDivider()
            SetNavRow(symbol: "banknote", tint: .orange, title: "월 예산",
                      value: store.budget > 0 ? won(store.budget) : "미설정") { showBudget = true }
            SetDivider()
            SetToggleRow(symbol: "brain.head.profile", tint: .amber, title: "과소비 예방 넛지",
                         desc: "예산 · 평소치를 넘으면 저장 전에 한 번 더 확인",
                         isOn: bind(\.nudgeOverspend, store.setNudgeOverspend))
            if store.nudgeOverspend {
                SetDivider()
                SetNavRow(symbol: "checkmark.circle", tint: .amber, title: "넛지 기준 금액",
                          value: won(store.nudgeThreshold)) {
                    // iOS 26+ 는 시스템 시트, 그 아래(iOS 18)는 시스템 알림창 — 알림창 입력칸 넘침은 새 글래스 알림창에서만 난다.
                    if #available(iOS 26.0, *) {
                        showNudge = true
                    } else {
                        SystemTextAlert.present(
                            title: "넛지 기준 금액", message: "단건 지출이 이 금액 이상이면 추가 전 한 번 더 확인해요.",
                            text: store.nudgeThreshold > 0 ? "\(store.nudgeThreshold)" : "", placeholder: "100000"
                        ) { v in store.setNudgeThreshold(Int64(String(v.filter(\.isNumber).prefix(9))) ?? 0) }
                    }
                }
            }
        }
    }

    /// 「원신 · 스타레일 외 1」 — 비어 있으면 전체. Android myGamesLabel 파리티.
    private var myGamesLabel: String {
        let names = GLGGames.all.filter { store.myGames.contains($0.key) }.map { $0.shortName }
        if names.isEmpty { return "전체" }
        if names.count <= 2 { return names.joined(separator: " · ") }
        return names.prefix(2).joined(separator: " · ") + " 외 \(names.count - 2)"
    }

    // ── 연동 · 자동화 ──
    @ViewBuilder
    private var automationSection: some View {
        SetSection(title: "연동 · 자동화", caption: "HoYoLAB") {
            SetNavRow(symbol: "link", tint: .navy, title: "HoYoLAB 계정 연동",
                      value: store.hoyolabConfig.isLinked ? "연동됨" : "미연동") { showHoyolab = true }
            SetDivider()
            SetToggleRow(symbol: "calendar.badge.checkmark", tint: .teal, title: "자동 출석체크",
                         desc: store.hoyolabConfig.isLinked
                            ? "매일 자동으로 출석을 챙겨요 (켜면 지금 한 번 바로 시도)"
                            : "HoYoLAB을 연동하면 사용할 수 있어요",
                         isOn: Binding(
                            get: { store.hoyolabConfig.isLinked && store.autoCheckIn },
                            set: { on in
                                if store.hoyolabConfig.isLinked { store.setAutoCheckIn(on) } else { showHoyolab = true }
                            }
                         ))
        }
    }

    // ── 알림 — 항목별 알림 · 방해금지를 모은 하위 페이지로 진입 ──
    @ViewBuilder
    private var notificationLinkSection: some View {
        SetSection(title: "알림", caption: "받을 알림 · 방해 금지") {
            SetNavRow(symbol: "bell", tint: .teal, title: "알림 설정",
                      value: NotificationCatalog.shared.enabledLabel(onCount: Int32(notifyOnCount))) { showNotifSettings = true }
        }
    }

    /// 보이는 알림 항목 중 켜진 개수(행사가 끝난 호요랜드는 세지 않는다).
    private var notifyOnCount: Int {
        let on: [NotifyKey: Bool] = [.budget: store.notifyBudget, .resin: store.notifyResin, .attendance: store.notifyAttendance,
                                      .pickup: store.notifyPickup, .combat: store.notifyCombat, .news: store.notifyNews,
                                      .hoyoland: store.notifyHoyoland]
        return NotificationCatalog.shared.items.filter { on[$0.key] == true }.count
    }

    // ── 데이터 관리 — 백업·복원/내보내기/위험 구역을 모은 하위 페이지로 진입 ──
    @ViewBuilder
    private var dataManagementLinkSection: some View {
        SetSection(title: "데이터", caption: "백업 · 복원 · 초기화") {
            SetNavRow(symbol: "externaldrive", tint: .slate, title: "데이터 관리", value: "백업 · 복원 · 초기화") { showDataManagement = true }
        }
    }

    // ── 정보 ──
    @ViewBuilder
    private var infoSection: some View {
        SetSection(title: "앱 정보", caption: "v\(version)") {
            // iOS 앱은 업데이트 확인 기능 제거(IPA 사이드로드 배포 — 원격 버전 확인 부적합). 업데이트 로그만 유지.
            SetNavRow(symbol: "sparkles", tint: .blue, title: "업데이트 로그") { showUplog = true }
            SetDivider()
            SetNavRow(symbol: "c.circle", tint: .slate, title: "출처 · 저작권") { showCredits = true }
            SetDivider()
            SetNavRow(asset: "GitHubMark", tint: .navy, title: "GitHub", chevron: "arrow.up.right") {
                if let u = URL(string: Self.githubRepoURL) { openURL(u) }
            }
            SetDivider()
            SetNavRow(symbol: "info.circle", tint: .slate, title: "앱 버전", value: "v\(version)", chevron: nil,
                      trailing: { buildVariantChip }, action: {})
            // 서명(프로비저닝) 만료 — 무료 계정 7일 서명. 만료 시각(초 단위) + 남은 시간 라이브 카운트다운.
            if let exp = SigningInfo.expirationDate {
                SetDivider()
                signingExpiryRow(exp)
            }
        }
    }

    /// 서명 만료 행 — 1초마다 갱신되는 남은 시간 표시.
    private func signingExpiryRow(_ exp: Date) -> some View {
        TimelineView(.periodic(from: .now, by: 1)) { ctx in
            // 아이콘 칸(34) 가운데에 맞춘다 — .top 이면 제목이 위로 붙어 보였다(9/29 지적).
            HStack(alignment: .center, spacing: 12) {
                SetIcon(symbol: "checkmark.seal", tint: .teal)
                Text("서명 만료").font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(SigningInfo.absFormatter.string(from: exp))
                        .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    Text(remainingText(exp, now: ctx.date))
                        .font(.pretendard(size: 12, weight: .semibold).monospacedDigit())
                        .foregroundStyle(exp.timeIntervalSince(ctx.date) < 86_400 ? .red : accent.primary)
                }
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
    }

    /// 남은 시간 "N일 HH:MM:SS 남음" (시·분·초 단위). 만료 시 "만료됨".
    private func remainingText(_ exp: Date, now: Date) -> String {
        let secs = Int(exp.timeIntervalSince(now))
        if secs <= 0 { return "만료됨" }
        let d = secs / 86_400, h = (secs % 86_400) / 3600, m = (secs % 3600) / 60, s = secs % 60
        return String(format: "%d일 %02d:%02d:%02d 남음", d, h, m, s)
    }

    /// store 의 읽기전용 @Published + setter 를 Toggle 용 Binding 으로.
    private func bind(_ keyPath: KeyPath<SpendingStore, Bool>, _ setter: @escaping (Bool) -> Void) -> Binding<Bool> {
        Binding(get: { store[keyPath: keyPath] }, set: { setter($0) })
    }
}

// ── 설정 ▸ 내 게임(아티팩트 S2) — 온보딩 ②와 같은 값(myGames). 6게임 전부(이환 포함), 비우면 전부. ──
struct MyGamesView: View {
    var store: SpendingStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("고른 게임이 지출 입력 맨 위에 오고, 출석도 고른 게임만 챙겨요. 기록은 거르지 않아요.")
                    .font(.pretendard(size: 13.5)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.horizontal, 4).padding(.bottom, 8)
                ForEach(GLGGames.all, id: \.key) { g in
                    let on = store.myGames.contains(g.key)
                    Button {
                        var next = store.myGames
                        if on { next.remove(g.key) } else { next.insert(g.key) }
                        store.setMyGames(next)
                    } label: {
                        HStack(spacing: 12) {
                            Circle().fill(Color(argb64: g.color)).frame(width: 10, height: 10)
                            Text(g.displayName).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Spacer()
                            ZStack {
                                Circle().fill(on ? Color(hex: 0xFF1B8E99) : Color(hex: 0xFFE3E8E6)).frame(width: 22, height: 22)
                                if on { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white) }
                            }
                        }
                        .padding(.horizontal, 16)
                        .frame(height: 58)
                        .background(on ? Color(hex: 0xFFEEF8F8) : .white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(on ? Color(hex: 0xFF1B8E99) : Color(hex: 0xFFE3E8E6), lineWidth: on ? 2 : 1))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                Text("하나도 고르지 않으면 전부 보여요. 온보딩에서 고른 게임과 같은 값이에요.")
                    .font(.pretendard(size: 11.5)).foregroundStyle(Color(hex: 0xFF7A8784))
                    .padding(.horizontal, 4).padding(.top, 4)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .glgPageTitle("내 게임")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// ── 테마 — 미리보기 카드 + 선명 · 차분 두 벌 (Android ThemeScreen 파리티 · 목업 B안) ──────────
struct ThemeView: View {
    var store: SpendingStore

    /// 환경값 대신 store 에서 바로 읽는다 — 고르는 즉시 이 페이지부터 바뀌어야 미리보기가 된다.
    private var accent: GLGAccent { GLGTheme.accent(store.accentIndex) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                SetSection { preview.padding(.horizontal, 20) }
                SetBand()
                group("선명", Array(0..<GLGTheme.vividCount))
                SetBand()
                group("차분", Array(GLGTheme.vividCount..<GLGTheme.palette.count),
                      footer: "두 벌은 같은 색조 · 다른 채도예요. 게임별 색상과 속성 연출은 테마와 상관없이 그대로예요.")
            }
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .glgPageTitle("테마")
        .navigationBarTitleDisplayMode(.inline)
        .environment(\.glgAccent, accent)
    }

    /// 금액(deep) · 게이지(primary) · 칩(옅은 면) · 버튼 쌍. 누르는 곳이 아니라 보여주는 곳이다.
    private var preview: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("미리보기 · \(accent.label)").font(.pretendard(size: 11, weight: .bold))
                .foregroundStyle(GLGColor.textSecondary)
            Text("428,000원").font(.pretendard(size: 26, weight: .black))
                .foregroundStyle(accent.deep).padding(.top, 4)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(hex: 0xFFEEEFF3))
                    Capsule().fill(accent.primary).frame(width: geo.size.width * 0.62)
                }
            }
            .frame(height: 8).padding(.vertical, 10)
            HStack(spacing: 6) {
                ForEach(Array(["전체", "원신", "스타레일"].enumerated()), id: \.offset) { i, label in
                    Text(label).font(.pretendard(size: 11, weight: .bold))
                        .foregroundStyle(i == 0 ? accent.deep : GLGColor.textSecondary)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(i == 0 ? accent.primary.opacity(0.14) : Color(hex: 0xFFF4F5F8), in: Capsule())
                }
            }
            // 문구는 **버튼 이름이 아니라 모양 이름**이다. 「취소 · 저장하기」로 두었더니 테마 고른 걸
            // 저장하거나 되돌리는 진짜 버튼으로 읽혔다(2026-09-21 지적 — 누르지 못하는 미리보기다).
            HStack(spacing: 8) {
                GldsButton(title: "보조 버튼", variant: .secondary) {}
                GldsButton(title: "강조 버튼") {}
            }
            .allowsHitTesting(false)
            .padding(.top, 12)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func group(_ title: String, _ indices: [Int], footer: String? = nil) -> some View {
        let cols = Array(repeating: GridItem(.flexible(), spacing: 12), count: 5)
        return SetSection(title: title, caption: "\(indices.count)색") {
            LazyVGrid(columns: cols, spacing: 16) {
                ForEach(indices, id: \.self) { i in
                    let opt = GLGTheme.palette[i]
                    let selected = i == store.accentIndex
                    VStack(spacing: 4) {
                        ZStack {
                            Circle().fill(opt.primary).frame(width: 40, height: 40)
                            if selected {
                                Image(systemName: "checkmark").font(.pretendard(size: 18, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                        }
                        Text(opt.label).font(.pretendard(size: 10)).lineLimit(1).minimumScaleFactor(0.8)
                            .foregroundStyle(selected ? opt.deep : GLGColor.textSecondary)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { store.setAccentIndex(i) }
                }
            }
            // Android ThemeColorGrid 여백(위 8 · 아래 16)과 맞춘다.
            .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 16)
            if let footer {
                SetFootnote(text: footer, color: GLGColor.textSecondary, top: 0)
            }
        }
    }
}

// ── 서명(프로비저닝) 만료 정보 ───────────────────────────────────────────────
// 앱 번들의 embedded.mobileprovision(CMS 서명된 plist)에서 ExpirationDate 를 1회 파싱해 캐시.
// 무료 Apple 계정은 7일마다 서명이 만료되므로, 설정에서 남은 시간을 확인해 재빌드 시점을 가늠한다.
enum SigningInfo {
    static let expirationDate: Date? = {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              // 바이트 위치 보존(round-trip)을 위해 isoLatin1 로 디코드 — 내부 plist 는 ASCII.
              let raw = String(data: data, encoding: .isoLatin1) else { return nil }
        guard let start = raw.range(of: "<?xml") ?? raw.range(of: "<plist"),
              let end = raw.range(of: "</plist>") else { return nil }
        let plistStr = String(raw[start.lowerBound..<end.upperBound])
        guard let pData = plistStr.data(using: .isoLatin1),
              let plist = try? PropertyListSerialization.propertyList(from: pData, format: nil) as? [String: Any]
        else { return nil }
        return plist["ExpirationDate"] as? Date
    }()

    static let absFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        f.locale = Locale(identifier: "ko_KR")
        return f
    }()
}

// ── 텍스트 파일 문서 (fileExporter/Importer 용) ──────────────────────────────

struct TextDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.json, .commaSeparatedText, .plainText]
    var text: String
    init(_ text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        text = String(data: configuration.file.regularFileContents ?? Data(), encoding: .utf8) ?? ""
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: text.data(using: .utf8) ?? Data())
    }
}

// ── 설정 ▸ 예산 관리(아티팩트 S3) — 위는 온보딩 ③과 같은 금액 카드, 아래는 게임별 한도. Android BudgetScreen 파리티. ──
struct BudgetSettingsView: View {
    var store: SpendingStore
    @Environment(\.dismiss) private var dismiss
    @State private var amount: Int64 = 0
    @State private var custom = false
    @State private var limits: [String: String] = [:]
    @State private var loaded = false

    /// 내 게임이 위(GameData.pickerGames 파리티).
    private var order: [Game] {
        store.myGames.isEmpty ? GLGGames.all
            : GLGGames.all.filter { store.myGames.contains($0.key) } + GLGGames.all.filter { !store.myGames.contains($0.key) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 금액 편집기는 입력 컨트롤(온보딩 ③ 공용)이라 그대로 두고, 섹션 여백만 준다.
                BudgetAmountEditor(budget: $amount, custom: $custom)
                    .padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 20)
                SetBand()
                SetSection(title: "게임별 한도", caption: "선택 · 비워 두면 한도 없음") {
                    ForEach(Array(order.enumerated()), id: \.element.key) { i, g in
                        if i > 0 { SetDivider() }
                        limitRow(g)
                    }
                    SetFootnote(text: "내 게임이 위에 와요. 이번 달 사용액이 한도를 넘으면 주황으로 표시돼요.")
                }
            }
            .glgReadableWidth(640)
        }
        // iOS 는 저장을 헤더 시스템 버튼으로(9/30 사용자 지정) — 「월 예산 끄기」 · 「저장」.
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { Button("월 예산 끄기") { save(0) } }
            // iOS 26 은 붙은 아이템을 한 캡슐로 묶는다 — 끄기 · 저장은 성격이 달라 떼어 둔다(지출 헤더와 같은 처리).
            if #available(iOS 26.0, *) { ToolbarSpacer(.fixed, placement: .topBarTrailing) }
            ToolbarItem(placement: .topBarTrailing) { Button("저장") { save(amount) }.fontWeight(.bold) }
        }
        .scrollIndicators(.hidden)
        .scrollDismissesKeyboard(.interactively)
        .background(Color.white.ignoresSafeArea())
        .glgPageTitle("예산 관리")
        .navigationBarTitleDisplayMode(.inline)
        // 입력 페이지라 하단 탭바를 숨긴다(업데이트 로그와 같은 처리).
        .toolbar(.hidden, for: .tabBar)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            amount = store.budget
            custom = store.budget > 0 && ![50_000, 100_000, 150_000, 300_000].contains(store.budget)
            for g in GLGGames.all {
                let v = store.gameBudgets[g.key] ?? 0
                limits[g.key] = v > 0 ? won0(v) : ""
            }
        }
    }

    private func limitRow(_ g: Game) -> some View {
        let spent = store.monthlyTotalsByGame[g.key] ?? 0
        let limit = Int64((limits[g.key] ?? "").filter(\.isNumber)) ?? 0
        let over = limit > 0 && spent > limit
        return HStack(spacing: 12) {
            Circle().fill(Color(argb64: g.color)).frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 2) {
                Text(g.displayName).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("이번 달 \(won(spent))" + (over ? " · 한도 초과" : ""))
                    .font(.pretendard(size: 12, weight: over ? .bold : .regular))
                    .foregroundStyle(over ? Color(hex: 0xFFC2410C) : GLGColor.textSecondary)
            }
            Spacer(minLength: 8)
            GldsTextField(placeholder: "한도 없음", text: Binding(
                get: { limits[g.key] ?? "" },
                set: { raw in
                    let n = Int64(String(raw.filter(\.isNumber).prefix(9))) ?? 0
                    limits[g.key] = n > 0 ? won0(n) : ""
                }), size: .s, suffix: "원", alignment: .trailing, bold: true, keyboard: .numberPad)
                .frame(width: 118)
        }
        .padding(.horizontal, 20).padding(.vertical, 11)
    }

    private func won0(_ n: Int64) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: n)) ?? "\(n)"
    }

    private func save(_ overall: Int64) {
        var per: [String: Int64] = [:]
        for (k, v) in limits { if let n = Int64(v.filter(\.isNumber)), n > 0 { per[k] = n } }
        store.setBudgets(overall: max(overall, 0), perGame: per)
        dismiss()
    }


}

/// 넛지 기준 금액 — 시스템 폼 시트. 저장은 헤더 시스템 버튼(iOS 저장류 규칙).
struct NudgeThresholdSheet: View {
    @State var text: String
    let onSave: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("100000", text: $text)
                        .keyboardType(.numberPad)
                        .focused($focused)
                        .onChange(of: text) { _, v in
                            let digits = String(v.filter(\.isNumber).prefix(9))
                            if digits != v { text = digits }
                        }
                } header: {
                    Text("기준 금액 (원)")
                } footer: {
                    Text("단건 지출이 이 금액 이상이면 추가 전 한 번 더 확인해요.")
                }
            }
            .navigationTitle("넛지 기준 금액")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("저장") { onSave(text); dismiss() }.fontWeight(.bold) }
            }
            // 시트가 올라오는 도중에 거는 포커스는 무시돼 키패드가 안 떴다(iOS 18 · 26 · 27) — 올라온 뒤에 건다.
            .task {
                try? await Task.sleep(for: .milliseconds(450))
                focused = true
            }
        }
        .presentationDetents([.height(280)])
    }
}

/// 입력칸 하나짜리 **시스템 알림창**(UIAlertController) — iOS 26 미만의 넛지 기준 금액.
/// iOS 26+ 글래스 알림창은 입력칸이 오른쪽으로 넘쳐(OS 버그) 그쪽은 [NudgeThresholdSheet] 를 쓴다.
@MainActor
enum SystemTextAlert {
    static func present(title: String, message: String, text: String, placeholder: String,
                        onSave: @escaping (String) -> Void) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addTextField { tf in
            tf.text = text
            tf.placeholder = placeholder
            tf.keyboardType = .numberPad
            tf.clearButtonMode = .whileEditing
        }
        alert.addAction(UIAlertAction(title: "취소", style: .cancel))
        alert.addAction(UIAlertAction(title: "저장", style: .default) { _ in onSave(alert.textFields?.first?.text ?? "") })
        alert.view.tintColor = .systemBlue   // 다른 알림창과 같은 시스템 파랑(glgAlertTint)
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.keyWindow }).first?.rootViewController else { return }
        var top = root
        while let next = top.presentedViewController { top = next }
        top.present(alert, animated: true)
    }
}
