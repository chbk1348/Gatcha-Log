import SwiftUI
import UniformTypeIdentifiers
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 설정 — 계정·테마·예산/연동·자동화·알림·데이터·백업·정보. (Compose SettingsScreen 대응)
// 네이티브 List+Section + Toggle + .alert/.sheet/.fileExporter/.fileImporter.
// HoYoLAB 연동은 네이티브 HoyolabLinkView(WKWebView) 를 페이지 푸시로 호스팅(앱 내 다른 진입점·Android와 통일).
// ════════════════════════════════════════════════════════════════════════════

struct SettingsView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @Environment(\.dismiss) private var dismiss

    // 시트/다이얼로그 상태
    @State private var showBudget = false
    @State private var showNudge = false
    @State private var nudgeText = ""
    @State private var showHoyolab = false
    @State private var showNotifSettings = false
    @State private var showDataManagement = false
    @State private var showUplog = false
    @State private var showCredits = false

    private var version: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "—"
    }

    /// 빌드 종류(DEBUG/RELEASE) 구분 태그 — 어떤 빌드가 설치됐는지 한눈에(Android BuildVariantChip 파리티).
    private var buildVariantChip: some View {
        #if DEBUG
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
            VStack(spacing: 18) {
                // 1) 알림  2) UI  3) 예산·연동  4) 데이터 관리  5) 나머지(자동화·테마·정보)
                // (계정은 마이페이지 히어로로 일원화 — 중복 카드 제거)
                notificationLinkSection
                displaySection
                budgetLinkSection
                dataManagementLinkSection
                automationSection
                infoSection
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("설정")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            // 홈 만료 배너 CTA → 설정 → HoYoLAB 연동 자동 진입
            if store.pendingOpenHoyolabLink {
                showHoyolab = true
                store.consumePendingOpenHoyolabLink()
            }
        }
        .sheet(isPresented: $showBudget) { BudgetSheet(store: store) }
        // 넛지 기준 금액 — 단일 입력이라 바텀시트 대신 중앙 모달(네이티브 alert + 입력 필드).
        .alert("넛지 기준 금액", isPresented: $showNudge) {
            TextField("기준 금액 (원)", text: $nudgeText).keyboardType(.numberPad)
            Button("저장") { store.setNudgeThreshold(Int64(nudgeText.filter(\.isNumber)) ?? 0) }
            Button("취소", role: .cancel) { }
        } message: {
            Text("단건 지출이 이 금액 이상이면 추가 전 한 번 더 확인해요.")
        }
        .sheet(isPresented: $showCredits) { CreditsSheet() }
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
    }

    // ── 섹션 카드 — D · Soft Modern: 연회색 면 + 헤어라인(지출 추가 모달 sectionCard·Android GlassCard 와 동일 규격). 선택적 제목·footer. ──
    @ViewBuilder
    private func sectionCard<C: View>(_ title: String? = nil, footer: String? = nil, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            if let title {
                Text(title).font(.pretendard(size: 13, weight: .semibold))
                    .foregroundStyle(GLGColor.textSecondary).padding(.leading, 4)
            }
            VStack(spacing: 0) { content() }
                .padding(.horizontal, 16)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            if let footer {
                Text(footer).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.horizontal, 4)
            }
        }
    }

    // ── UI — 표시(컴팩트) + 테마 색상을 한 섹션으로 통합 ──
    private var displaySection: some View {
        // 색상이 늘어 한 줄을 넘기므로 5열 그리드로 래핑(2행).
        let cols = Array(repeating: GridItem(.flexible(), spacing: 12), count: 5)
        return sectionCard("UI") {
            toggleRow("list.bullet", "지출 내역 컴팩트 보기",
                      "지출 목록을 한 줄로 빽빽하게 표시해요 (태그·결제수단 숨김)",
                      bind(\.spendingCompact, store.setSpendingCompact))
            Divider()
            HStack {
                rowLabel(icon: "paintpalette", title: "테마 색상")
                Spacer()
            }
            .padding(.top, 12)
            LazyVGrid(columns: cols, spacing: 16) {
                ForEach(GLGTheme.palette) { opt in
                    VStack(spacing: 4) {
                        ZStack {
                            Circle().fill(opt.primary).frame(width: 40, height: 40)
                            if opt.index == store.accentIndex {
                                Image(systemName: "checkmark").font(.pretendard(size: 18, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                        }
                        Text(opt.label).font(.pretendard(size: 10))
                            .foregroundStyle(opt.index == store.accentIndex ? opt.primary : GLGColor.textSecondary)
                    }
                    .onTapGesture { store.setAccentIndex(opt.index) }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 10)
            .padding(.bottom, 14)
        }
    }

    // ── 예산·연동 ──
    private var budgetLinkSection: some View {
        sectionCard("예산·연동") {
            navRow(icon: "banknote", title: "월 예산",
                   value: store.budget > 0 ? won(store.budget) : "미설정") { showBudget = true }
            Divider()
            Toggle(isOn: bind(\.nudgeOverspend, store.setNudgeOverspend)) {
                rowLabel(icon: "brain.head.profile", title: "과소비 예방 넛지",
                         subtitle: "지출 추가 시 예산·평소치를 넘으면 한 번 더 확인해요")
            }.tint(accent.primary).padding(.vertical, 10)
            if store.nudgeOverspend {
                Divider()
                navRow(icon: "checkmark.circle", title: "넛지 기준 금액",
                       value: won(store.nudgeThreshold)) {
                    nudgeText = store.nudgeThreshold > 0 ? "\(store.nudgeThreshold)" : ""
                    showNudge = true
                }
            }
            Divider()
            navRow(icon: "link", title: "HoYoLAB 계정 연동",
                   value: store.hoyolabConfig.isLinked ? "연동됨" : "미연동") { showHoyolab = true }
        }
    }

    // ── 자동화 ──
    private var automationSection: some View {
        sectionCard("자동화") {
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
            }.tint(accent.primary).padding(.vertical, 10)
        }
    }

    // ── 알림 — 항목별 알림·방해금지·데일리 요약을 모은 하위 페이지로 진입 ──
    private var notificationLinkSection: some View {
        sectionCard("알림") {
            navRow(icon: "bell.badge", title: "알림 설정",
                   value: "방해금지 · 요약 · 항목별") { showNotifSettings = true }
        }
    }

    // ── 데이터 관리 — 백업·복원/내보내기/위험 구역을 모은 하위 페이지로 진입 ──
    private var dataManagementLinkSection: some View {
        sectionCard("데이터 관리") {
            navRow(icon: "externaldrive", title: "데이터 관리",
                   value: "백업·복원 · 초기화") { showDataManagement = true }
        }
    }

    // ── 정보 ──
    private var infoSection: some View {
        sectionCard("정보") {
            // iOS 앱은 업데이트 확인 기능 제거(IPA 사이드로드 배포 — 원격 버전 확인 부적합). 업데이트 로그만 유지.
            navRow(icon: "sparkles", title: "업데이트 로그") { showUplog = true }
            Divider()
            navRow(icon: "c.circle", title: "출처 · 저작권") { showCredits = true }
            Divider()
            HStack {
                rowLabel(icon: "info.circle", title: "앱 버전")
                Spacer()
                buildVariantChip
                Text("v\(version)").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
            .padding(.vertical, 13)
            // 서명(프로비저닝) 만료 — 무료 계정 7일 서명. 만료 시각(초 단위) + 남은 시간 라이브 카운트다운.
            if let exp = SigningInfo.expirationDate {
                Divider()
                signingExpiryRow(exp)
            }
        }
    }

    /// 서명 만료 행 — 1초마다 갱신되는 남은 시간 표시.
    private func signingExpiryRow(_ exp: Date) -> some View {
        TimelineView(.periodic(from: .now, by: 1)) { ctx in
            HStack(alignment: .top) {
                rowLabel(icon: "checkmark.seal", title: "서명 만료")
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(SigningInfo.absFormatter.string(from: exp))
                        .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    Text(remainingText(exp, now: ctx.date))
                        .font(.pretendard(size: 12, weight: .semibold).monospacedDigit())
                        .foregroundStyle(exp.timeIntervalSince(ctx.date) < 86_400 ? .red : accent.primary)
                }
            }
            .padding(.vertical, 11)
        }
    }

    /// 남은 시간 "N일 HH:MM:SS 남음" (시·분·초 단위). 만료 시 "만료됨".
    private func remainingText(_ exp: Date, now: Date) -> String {
        let secs = Int(exp.timeIntervalSince(now))
        if secs <= 0 { return "만료됨" }
        let d = secs / 86_400, h = (secs % 86_400) / 3600, m = (secs % 3600) / 60, s = secs % 60
        return String(format: "%d일 %02d:%02d:%02d 남음", d, h, m, s)
    }

    // ── 행 헬퍼 ──
    private func rowLabel(icon: String, title: String, subtitle: String? = nil) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.pretendard(size: 18)).foregroundStyle(accent.primary).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.pretendard(size: 14, weight: .medium))
                if let subtitle {
                    Text(subtitle).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
            }
        }
    }

    private func navRow(icon: String, title: String, value: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                rowLabel(icon: icon, title: title)
                Spacer()
                if let value { Text(value).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary) }
                Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                    .foregroundStyle(Color(.tertiaryLabel))
            }
            .contentShape(Rectangle())
            .padding(.vertical, 13)
        }
        .buttonStyle(.plain)
    }

    private func toggleRow(_ icon: String, _ title: String, _ subtitle: String, _ binding: Binding<Bool>) -> some View {
        Toggle(isOn: binding) { rowLabel(icon: icon, title: title, subtitle: subtitle) }
            .tint(accent.primary).padding(.vertical, 10)
    }

    /// store 의 읽기전용 @Published + setter 를 Toggle 용 Binding 으로.
    private func bind(_ keyPath: KeyPath<SpendingStore, Bool>, _ setter: @escaping (Bool) -> Void) -> Binding<Bool> {
        Binding(get: { store[keyPath: keyPath] }, set: { setter($0) })
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
