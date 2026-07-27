import SwiftUI
import UniformTypeIdentifiers
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 데이터 관리 — 백업·복원(안전 우선)을 맨 위, 내보내기 중간, 파괴 작업은 '위험 구역'으로 분리.
// (SettingsView 에서 분리 · Android DataManagementScreen 파리티)
// ════════════════════════════════════════════════════════════════════════════

struct DataManagementView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    // 파괴작업은 2단계 확인: 1차(백업 권장) → 2차(최종 확인)
    @State private var confirmClearGacha = false
    @State private var confirmClearGacha2 = false
    @State private var confirmClearSpend = false
    @State private var confirmClearSpend2 = false
    @State private var confirmImport = false
    // 파일 내보내기/가져오기
    @State private var exportCsv = false
    @State private var exportBackup = false
    @State private var importBackup = false

    /// 위험 구역 강조용 빨강.
    private let dangerRed = Color(hex: 0xFFD32F2F)

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                backupSection
                exportSection
                dangerSection
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("데이터 관리")
        .navigationBarTitleDisplayMode(.inline)
        // 가챠 초기화 — 1단계(백업 권장)
        .alert("가챠 기록 초기화", isPresented: $confirmClearGacha) {
            Button("취소", role: .cancel) {}
            Button("계속") { confirmClearGacha2 = true }
        } message: { Text("가져온 모든 가챠 기록을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘백업 파일 내보내기’로 백업을 권장해요.") }
        // 가챠 초기화 — 2단계(최종 확인)
        .alert("정말 초기화할까요?", isPresented: $confirmClearGacha2) {
            Button("취소", role: .cancel) {}
            Button("초기화", role: .destructive) { store.clearGachaRecords() }
        } message: { Text("이 작업은 되돌릴 수 없어요. 가챠 기록을 모두 삭제합니다.") }
        // 지출 전체 삭제 — 1단계(백업 권장)
        .alert("지출 전체 삭제", isPresented: $confirmClearSpend) {
            Button("취소", role: .cancel) {}
            Button("계속") { confirmClearSpend2 = true }
        } message: { Text("모든 지출 기록(\(store.spendings.count)건)을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘백업 파일 내보내기’로 백업을 권장해요.") }
        // 지출 전체 삭제 — 2단계(최종 확인)
        .alert("정말 삭제할까요?", isPresented: $confirmClearSpend2) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive) { store.clearSpendings() }
        } message: { Text("이 작업은 되돌릴 수 없어요. 지출 기록(\(store.spendings.count)건)을 모두 삭제합니다.") }
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

    // ── 백업·복원 — 데이터 보호가 가장 중요하므로 맨 위에 (재설치·기기 변경 대비) ──
    private var backupSection: some View {
        sectionCard("백업·복원",
                    footer: "구글 로그인 없이도 전체 데이터(가챠 기록 포함)를 파일로 저장해 두면, 앱을 재설치하거나 기기를 바꿔도 복원할 수 있어요.") {
            navRow(icon: "arrow.up.doc", title: "백업 파일 내보내기", value: "전체 데이터") { exportBackup = true }
            Divider()
            navRow(icon: "arrow.down.doc", title: "백업 파일에서 복원") { confirmImport = true }
        }
    }

    // ── 내보내기 ──
    private var exportSection: some View {
        sectionCard("내보내기") {
            navRow(icon: "square.and.arrow.down", title: "지출 내역 내보내기 (CSV)") { exportCsv = true }
        }
    }

    // ── 위험 구역 — 되돌릴 수 없는 파괴 작업은 빨간 톤으로 시각 분리 ──
    private var dangerSection: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text("위험 구역").font(.pretendard(size: 13, weight: .semibold))
                .foregroundStyle(dangerRed).padding(.leading, 4)
            VStack(spacing: 0) {
                dangerRow(icon: "trash", title: "가챠 기록 초기화",
                          value: store.gachaStats.map { "\($0.total)건" } ?? "없음") {
                    if store.gachaStats != nil { confirmClearGacha = true }
                }
                Divider()
                dangerRow(icon: "trash.fill", title: "지출 전체 삭제", value: "\(store.spendings.count)건") {
                    if !store.spendings.isEmpty { confirmClearSpend = true }
                }
            }
            .padding(.horizontal, 16)
            .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            Text("되돌릴 수 없는 작업이에요. 먼저 위 ‘백업 파일 내보내기’로 백업을 권장해요.")
                .font(.pretendard(size: 11)).foregroundStyle(dangerRed).padding(.horizontal, 4)
        }
    }

    // ── 섹션 카드 — SettingsView sectionCard 와 동일 규격(연회색 면 + 헤어라인). 선택적 제목·footer. ──
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

    // ── 행 헬퍼 (SettingsView 와 동일 규격) ──
    private func rowLabel(icon: String, title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.pretendard(size: 18)).foregroundStyle(accent.primary).frame(width: 24)
            Text(title).font(.pretendard(size: 14, weight: .medium))
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

    /// 위험 구역 행 — 빨간 아이콘/제목으로 파괴 작업 강조.
    private func dangerRow(icon: String, title: String, value: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                HStack(spacing: 12) {
                    Image(systemName: icon).font(.pretendard(size: 18)).foregroundStyle(dangerRed).frame(width: 24)
                    Text(title).font(.pretendard(size: 14, weight: .medium)).foregroundStyle(dangerRed)
                }
                Spacer()
                if let value { Text(value).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary) }
                Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                    .foregroundStyle(dangerRed.opacity(0.4))
            }
            .contentShape(Rectangle())
            .padding(.vertical, 13)
        }
        .buttonStyle(.plain)
    }

    private func readBackup(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        if let text = try? String(contentsOf: url, encoding: .utf8) {
            store.importBackupFromContent(text)
        }
    }
}
