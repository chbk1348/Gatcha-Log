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
            // 설정 메인과 같은 결 — GLDS 2.0 화면 폭 섹션 + 회색 띠. Android DataManagementScreen 파리티.
            VStack(alignment: .leading, spacing: 0) {
                SetSection(title: "백업 · 복원", caption: "재설치 · 기기 변경 대비") {
                    SetNavRow(symbol: "arrow.up.doc", tint: .teal, title: "백업 파일 내보내기", value: "전체 데이터") { exportBackup = true }
                    SetDivider()
                    SetNavRow(symbol: "arrow.down.doc", tint: .blue, title: "백업 파일에서 복원") { confirmImport = true }
                    SetFootnote(text: "구글 로그인 없이도 전체 데이터(가챠 기록 포함)를 파일로 저장해 두면, 앱을 재설치하거나 기기를 바꿔도 복원할 수 있어요.")
                }
                SetBand()
                SetSection(title: "내보내기", caption: "CSV") {
                    SetNavRow(symbol: "square.and.arrow.down", tint: .slate, title: "지출 내역 내보내기", value: "CSV") { exportCsv = true }
                }
                SetBand()
                SetSection(title: "위험 구역", caption: "되돌릴 수 없어요") {
                    SetNavRow(symbol: "trash", tint: .red, title: "가챠 기록 초기화",
                              value: store.gachaStats.map { "\($0.total)건" } ?? "없음", titleColor: dangerRed) {
                        if store.gachaStats != nil { confirmClearGacha = true }
                    }
                    SetDivider()
                    SetNavRow(symbol: "trash.fill", tint: .red, title: "지출 전체 삭제",
                              value: "\(store.spendings.count)건", titleColor: dangerRed) {
                        if !store.spendings.isEmpty { confirmClearSpend = true }
                    }
                    SetFootnote(text: "되돌릴 수 없는 작업이에요. 먼저 위 ‘백업 파일 내보내기’로 백업을 권장해요.", color: dangerRed)
                }
            }
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .glgPageTitle("데이터 관리")
        .navigationBarTitleDisplayMode(.inline)
        // 가챠 초기화 — 1단계(백업 권장)
        .alert("가챠 기록 초기화", isPresented: $confirmClearGacha) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("계속") { confirmClearGacha2 = true }.glgAlertTint()
        } message: { Text("가져온 모든 가챠 기록을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘백업 파일 내보내기’로 백업을 권장해요.") }
        // 가챠 초기화 — 2단계(최종 확인)
        .alert("정말 초기화할까요?", isPresented: $confirmClearGacha2) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("초기화", role: .destructive) { store.clearGachaRecords() }.glgAlertTint()
        } message: { Text("이 작업은 되돌릴 수 없어요. 가챠 기록을 모두 삭제합니다.") }
        // 지출 전체 삭제 — 1단계(백업 권장)
        .alert("지출 전체 삭제", isPresented: $confirmClearSpend) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("계속") { confirmClearSpend2 = true }.glgAlertTint()
        } message: { Text("모든 지출 기록(\(store.spendings.count)건)을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘백업 파일 내보내기’로 백업을 권장해요.") }
        // 지출 전체 삭제 — 2단계(최종 확인)
        .alert("정말 삭제할까요?", isPresented: $confirmClearSpend2) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("삭제", role: .destructive) { store.clearSpendings() }.glgAlertTint()
        } message: { Text("이 작업은 되돌릴 수 없어요. 지출 기록(\(store.spendings.count)건)을 모두 삭제합니다.") }
        .alert("백업 파일에서 복원", isPresented: $confirmImport) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("파일 선택") { importBackup = true }.glgAlertTint()
        } message: { Text("백업 파일을 선택해 복원할까요? 백업에 들어 있는 항목은 현재 데이터를 덮어씁니다.") }
        .fileExporter(isPresented: $exportCsv, document: TextDocument(store.buildCsv()),
                      contentType: .commaSeparatedText, defaultFilename: "gatchalog-spending") { _ in }
        .fileExporter(isPresented: $exportBackup, document: TextDocument(store.exportBackupContent() ?? ""),
                      contentType: .json, defaultFilename: "gatchalog-backup") { _ in }
        .fileImporter(isPresented: $importBackup, allowedContentTypes: [.json]) { result in
            if case .success(let url) = result { readBackup(url) }
        }
    }

    private func readBackup(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        if let text = try? String(contentsOf: url, encoding: .utf8) {
            store.importBackupFromContent(text)
        }
    }
}
