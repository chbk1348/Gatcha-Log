import SwiftUI
import UniformTypeIdentifiers
import ComposeApp

// ════════════════════════════════════════════════════════════════════════════
// 설정 — 계정·테마·예산/연동·자동화·알림·데이터·백업·정보. (Compose SettingsScreen 대응)
// 네이티브 List+Section + Toggle + .alert/.sheet/.fileExporter/.fileImporter.
// HoYoLAB 연동은 Phase 4(WKWebView) 전까지 기존 Compose 화면을 시트로 호스팅.
// ════════════════════════════════════════════════════════════════════════════

struct SettingsView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @Environment(\.dismiss) private var dismiss

    // 시트/다이얼로그 상태
    @State private var showBudget = false
    @State private var showNudge = false
    @State private var showHoyolab = false
    @State private var showUplog = false
    @State private var showCredits = false
    @State private var confirmClearGacha = false
    @State private var confirmClearSpend = false
    @State private var confirmImport = false
    // 파일 내보내기/가져오기
    @State private var exportCsv = false
    @State private var exportBackup = false
    @State private var importBackup = false
    @State private var exportDoc = TextDocument("")
    @State private var exportName = "export.txt"
    @State private var exportType: UTType = .plainText

    private var account: Account { store.account }
    private var isGuest: Bool { account.isGuest }
    private var version: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "—"
    }

    var body: some View {
        List {
            accountSection
            themeSection
            budgetLinkSection
            automationSection
            notificationSection
            dataSection
            backupSection
            infoSection
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("설정")
        .navigationBarTitleDisplayMode(.inline)
        .glgToast(message: store.statusMessage) { store.clearStatus() }
        .onAppear {
            // 홈 만료 배너 CTA → 설정 → HoYoLAB 연동 자동 진입
            if store.pendingOpenHoyolabLink {
                showHoyolab = true
                store.consumePendingOpenHoyolabLink()
            }
        }
        .sheet(isPresented: $showBudget) { BudgetSheet(store: store) }
        .sheet(isPresented: $showNudge) { NudgeThresholdSheet(store: store) }
        .sheet(isPresented: $showUplog) { UpdateLogSheet(version: version) }
        .sheet(isPresented: $showCredits) { CreditsSheet() }
        .sheet(isPresented: $showHoyolab) {
            HoyolabLinkView(store: store) { showHoyolab = false }
        }
        .alert("가챠 기록 초기화", isPresented: $confirmClearGacha) {
            Button("취소", role: .cancel) {}
            Button("초기화", role: .destructive) { store.clearGachaRecords() }
        } message: { Text("가져온 모든 가챠 기록을 삭제할까요? 이 작업은 되돌릴 수 없어요.") }
        .alert("지출 전체 삭제", isPresented: $confirmClearSpend) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive) { store.clearSpendings() }
        } message: { Text("모든 지출 기록(\(store.spendings.count)건)을 삭제할까요? 이 작업은 되돌릴 수 없어요.") }
        .alert("백업 파일에서 복원", isPresented: $confirmImport) {
            Button("취소", role: .cancel) {}
            Button("파일 선택") { importBackup = true }
        } message: { Text("백업 파일을 선택해 복원할까요? 백업에 들어 있는 항목은 현재 데이터를 덮어씁니다.") }
        .fileExporter(isPresented: $exportCsv, document: TextDocument(store.buildCsv()),
                      contentType: .commaSeparatedText, defaultFilename: "gatchalog-spending") { _ in }
        .fileExporter(isPresented: $exportBackup, document: TextDocument(store.exportBackupContent() ?? ""),
                      contentType: .json, defaultFilename: "gatchalog-backup") { _ in }
        .fileImporter(isPresented: $importBackup, allowedContentTypes: [.json]) { result in
            if case .success(let url) = result { readBackup(url) }
        }
    }

    // ── 계정 ──
    private var accountSection: some View {
        Section {
            VStack(spacing: 16) {
                HStack(spacing: 14) {
                    ProfileAvatarView(photoUrl: isGuest ? nil : account.photoUrl, size: 56)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(isGuest ? "게스트" : account.name).font(.system(size: 18, weight: .bold)).lineLimit(1)
                        if !isGuest && !account.email.isEmpty {
                            Text(account.email).font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                        }
                        HStack(spacing: 4) {
                            Image(systemName: isGuest ? "icloud.slash.fill" : "checkmark.icloud.fill").font(.system(size: 10, weight: .bold))
                            Text(isGuest ? "동기화 꺼짐" : "구글 계정 동기화 켜짐").font(.system(size: 11, weight: .semibold))
                        }
                        .foregroundStyle(isGuest ? GLGColor.textSecondary : accent.primary)
                        .padding(.horizontal, 9).padding(.vertical, 3)
                        .background((isGuest ? Color.gray : accent.primary).opacity(0.14), in: Capsule())
                        .padding(.top, 2)
                    }
                    Spacer(minLength: 0)
                }
                if isGuest {
                    GLGButton(title: "Google로 로그인") { store.signIn() }
                } else {
                    GLGOutlineButton(title: "로그아웃") { store.signOut() }
                }
            }
            .padding(.vertical, 8)
            .listRowInsets(EdgeInsets(top: 14, leading: 16, bottom: 14, trailing: 16))
        }
    }

    // ── 테마 ──
    private var themeSection: some View {
        Section("테마 색상") {
            HStack(spacing: 16) {
                ForEach(GLGTheme.palette) { opt in
                    VStack(spacing: 4) {
                        ZStack {
                            Circle().fill(opt.primary).frame(width: 40, height: 40)
                            if opt.index == store.accentIndex {
                                Image(systemName: "checkmark").font(.system(size: 18, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                        }
                        Text(opt.label).font(.system(size: 10))
                            .foregroundStyle(opt.index == store.accentIndex ? opt.primary : GLGColor.textSecondary)
                    }
                    .onTapGesture { store.setAccentIndex(opt.index) }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
        }
    }

    // ── 예산·연동 ──
    private var budgetLinkSection: some View {
        Section("예산·연동") {
            navRow(icon: "banknote", title: "월 예산",
                   value: store.budget > 0 ? won(store.budget) : "미설정") { showBudget = true }
            Toggle(isOn: bind(\.nudgeOverspend, store.setNudgeOverspend)) {
                rowLabel(icon: "brain.head.profile", title: "과소비 예방 넛지",
                         subtitle: "지출 추가 시 예산·평소치를 넘으면 한 번 더 확인해요")
            }.tint(accent.primary)
            if store.nudgeOverspend {
                navRow(icon: "checkmark.circle", title: "넛지 기준 금액",
                       value: won(store.nudgeThreshold)) { showNudge = true }
            }
            navRow(icon: "link", title: "HoYoLAB 계정 연동",
                   value: store.hoyolabConfig.isLinked ? "연동됨" : "미연동") { showHoyolab = true }
        }
    }

    // ── 자동화 ──
    private var automationSection: some View {
        Section("자동화") {
            Toggle(isOn: Binding(
                get: { store.hoyolabConfig.isLinked && store.autoCheckIn },
                set: { on in
                    if store.hoyolabConfig.isLinked { store.setAutoCheckIn(on) } else { showHoyolab = true }
                }
            )) {
                rowLabel(icon: "calendar.badge.checkmark", title: "자동 출석체크",
                         subtitle: store.hoyolabConfig.isLinked
                            ? "켜두면 매일 자동으로 출석을 챙겨드려요 (지금 한 번 바로 시도)"
                            : "HoYoLAB을 연동하면 사용할 수 있어요")
            }.tint(accent.primary)
        }
    }

    // ── 알림 ──
    private var notificationSection: some View {
        Section("알림") {
            toggleRow("banknote", "예산 알림", "이번 달 예산 90%·초과 시 알려줘요",
                      notifyBind(\.notifyBudget, store.setNotifyBudget))
            toggleRow("calendar.badge.checkmark", "출석 리마인더", "저녁까지 미출석이면 알려줘요",
                      notifyBind(\.notifyAttendance, store.setNotifyAttendance))
            toggleRow("bolt.fill", "재화 가득참 알림", "레진·개척력·배터리가 가득 차면 알려줘요",
                      notifyBind(\.notifyResin, store.setNotifyResin))
            toggleRow("star.fill", "위시 픽업 알림", "위시리스트 캐릭터가 픽업 배너에 등장하면 알려줘요",
                      notifyBind(\.notifyWish, store.setNotifyWish))
        }
    }

    // ── 데이터 ──
    private var dataSection: some View {
        Section("데이터") {
            navRow(icon: "square.and.arrow.down", title: "지출 내역 내보내기 (CSV)") { exportCsv = true }
            navRow(icon: "trash", title: "가챠 기록 초기화",
                   value: store.gachaStats.map { "\($0.total)건" } ?? "없음") {
                if store.gachaStats != nil { confirmClearGacha = true }
            }
            navRow(icon: "trash.fill", title: "지출 전체 삭제", value: "\(store.spendings.count)건") {
                if !store.spendings.isEmpty { confirmClearSpend = true }
            }
        }
    }

    // ── 백업·복원 ──
    private var backupSection: some View {
        Section {
            navRow(icon: "arrow.up.doc", title: "백업 파일 내보내기", value: "전체 데이터") { exportBackup = true }
            navRow(icon: "arrow.down.doc", title: "백업 파일에서 복원") { confirmImport = true }
        } header: { Text("백업·복원") } footer: {
            Text("구글 로그인 없이도 전체 데이터(가챠 기록 포함)를 파일로 저장해 두면, 앱을 재설치하거나 기기를 바꿔도 복원할 수 있어요.")
        }
    }

    // ── 정보 ──
    private var infoSection: some View {
        Section("정보") {
            // iOS 앱은 업데이트 확인 기능 제거(IPA 사이드로드 배포 — 원격 버전 확인 부적합). 업데이트 로그만 유지.
            navRow(icon: "sparkles", title: "업데이트 로그") { showUplog = true }
            navRow(icon: "c.circle", title: "출처 · 저작권") { showCredits = true }
            HStack {
                rowLabel(icon: "info.circle", title: "앱 버전")
                Spacer()
                Text("v\(version)").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
        }
    }

    // ── 행 헬퍼 ──
    private func rowLabel(icon: String, title: String, subtitle: String? = nil) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 18)).foregroundStyle(accent.primary).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 14, weight: .medium))
                if let subtitle {
                    Text(subtitle).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
            }
        }
    }

    private func navRow(icon: String, title: String, value: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                rowLabel(icon: icon, title: title)
                Spacer()
                if let value { Text(value).font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary) }
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color(.tertiaryLabel))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func toggleRow(_ icon: String, _ title: String, _ subtitle: String, _ binding: Binding<Bool>) -> some View {
        Toggle(isOn: binding) { rowLabel(icon: icon, title: title, subtitle: subtitle) }.tint(accent.primary)
    }

    /// store 의 읽기전용 @Published + setter 를 Toggle 용 Binding 으로.
    private func bind(_ keyPath: KeyPath<SpendingStore, Bool>, _ setter: @escaping (Bool) -> Void) -> Binding<Bool> {
        Binding(get: { store[keyPath: keyPath] }, set: { setter($0) })
    }

    /// 알림 토글용 — 켤 때 iOS 알림 권한을 요청한다.
    private func notifyBind(_ keyPath: KeyPath<SpendingStore, Bool>, _ setter: @escaping (Bool) -> Void) -> Binding<Bool> {
        Binding(get: { store[keyPath: keyPath] }, set: { on in
            if on { NotificationPermission.request() }
            setter(on)
        })
    }

    private func readBackup(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        if let text = try? String(contentsOf: url, encoding: .utf8) {
            store.importBackupFromContent(text)
        }
    }
}

// ── 텍스트 파일 문서 (fileExporter/Importer 용) ──────────────────────────────

struct TextDocument: FileDocument {
    static var readableContentTypes: [UTType] = [.json, .commaSeparatedText, .plainText]
    var text: String
    init(_ text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        text = String(data: configuration.file.regularFileContents ?? Data(), encoding: .utf8) ?? ""
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: text.data(using: .utf8) ?? Data())
    }
}
