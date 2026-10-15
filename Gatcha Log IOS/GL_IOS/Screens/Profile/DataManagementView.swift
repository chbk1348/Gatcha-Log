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
    // 가챠 초기화의 2차 확인은 공용 수정자(gachaClearConfirm)가 갖는다 — 가챠 리포트의 「초기화」와 같은 창(27.52.0).
    @State private var confirmClearGacha = false
    @State private var confirmClearSpend = false
    @State private var confirmClearSpend2 = false
    @State private var confirmImport = false
    // 파일 내보내기/가져오기
    @State private var exportCsv = false
    @State private var exportBackup = false
    @State private var importBackup = false
    /// 내보낼 문자열 — **버튼을 누른 순간에** 만들어 담는다(27.52.0).
    ///
    /// 예전엔 `.fileExporter(document: TextDocument(store.buildCsv()))` 처럼 수정자 인자에서 바로 만들었다.
    /// 수정자 인자는 body 가 평가될 때마다 다시 계산되므로, 이 화면이 그려질 때마다(진입 · 알림창 열고 닫기 ·
    /// 지출 건수 변화) 전체 백업 스냅샷 JSON 과 CSV 를 메인 스레드에서 통째로 만들고 버렸다.
    @State private var csvDocument = TextDocument("")
    @State private var backupDocument = TextDocument("")

    /// 위험 구역 강조용 빨강.
    private let dangerRed = Color(hex: 0xFFD32F2F)

    var body: some View {
        ScrollView {
            // 설정 메인과 같은 결 — GLDS 2.0 화면 폭 섹션 + 회색 띠. Android DataManagementScreen 파리티.
            VStack(alignment: .leading, spacing: 0) {
                SetSection(title: "백업 · 복원", caption: "재설치 · 기기 변경 대비", bottom: 20) {
                    SetNavRow(symbol: "arrow.up.doc", tint: .teal, title: "백업 파일 내보내기", value: "전체 데이터") { startBackupExport() }
                    SetDivider()
                    SetNavRow(symbol: "arrow.down.doc", tint: .blue, title: "백업 파일에서 복원") { confirmImport = true }
                    SetFootnote(text: "구글 로그인 없이도 전체 데이터(가챠 기록 포함)를 파일로 저장해 두면, 앱을 재설치하거나 기기를 바꿔도 복원할 수 있어요.")
                }
                SetBand()
                SetSection(title: "내보내기", caption: "CSV") {
                    SetNavRow(symbol: "square.and.arrow.down", tint: .slate, title: "지출 내역 내보내기", value: "CSV") {
                        csvDocument = TextDocument(store.buildCsv())
                        exportCsv = true
                    }
                }
                SetBand()
                SetSection(title: "위험 구역", caption: "되돌릴 수 없어요", bottom: 20) {
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
        // 가챠 초기화 — 1단계(백업 권장) → 2단계(최종 확인). 문구는 공용 수정자 한 곳에 있다.
        .gachaClearConfirm(isPresented: $confirmClearGacha, store: store)
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
        // 문서는 누른 순간에 만들어 둔 것을 넘긴다 — 여기서 만들면 body 평가마다 다시 만든다(csvDocument 주석).
        // 결과는 버리지 않고 알린다(27.52.0) — 예전엔 `{ _ in }` 라 저장이 됐는지 안 됐는지 알 길이 없었다.
        .fileExporter(isPresented: $exportCsv, document: csvDocument,
                      contentType: .commaSeparatedText, defaultFilename: "gatchalog-spending") {
            reportExport($0, saved: "파일을 저장했어요", failed: "파일을 저장하지 못했어요 — 다시 시도해 주세요")
        }
        .fileExporter(isPresented: $exportBackup, document: backupDocument,
                      contentType: .json, defaultFilename: "gatchalog-backup") {
            reportExport($0, saved: "백업 파일을 저장했어요", failed: "백업 파일을 저장하지 못했어요 — 다시 시도해 주세요")
        }
        .fileImporter(isPresented: $importBackup, allowedContentTypes: [.json]) { pickedBackup($0) }
    }

    /// 내보내기 결과 안내(27.52.0). 저장 창을 그냥 닫은 것(취소)은 실패가 아니라 알리지 않는다.
    private func reportExport(_ result: Result<URL, Error>, saved: String, failed: String) {
        switch result {
        case .success: store.showToast(saved, .success)
        case .failure(let error): if !isUserCancel(error) { store.showToast(failed, .error) }
        }
    }

    /// 복원할 파일을 고른 결과(27.52.0) — 고르지 못한 갈래도 알린다. 예전엔 성공만 받아 실패가 조용히 묻혔다.
    private func pickedBackup(_ result: Result<URL, Error>) {
        switch result {
        case .success(let url): readBackup(url)
        case .failure(let error): if !isUserCancel(error) { store.showToast("파일을 읽지 못했어요", .error) }
        }
    }

    /// 사용자가 파일 창을 닫았다 — 시스템이 이를 오류로 넘길 때를 걸러 낸다.
    private func isUserCancel(_ error: Error) -> Bool {
        let e = error as NSError
        return e.domain == NSCocoaErrorDomain && e.code == NSUserCancelledError
    }

    /// 백업 파일 내보내기 — 스냅샷을 못 만들면(nil) 빈 파일을 내보내지 않고 알린다.
    /// 예전엔 `?? ""` 로 0바이트 파일이 저장돼, 백업해 둔 줄 알았다가 복원할 때에야 빈 파일인 걸 알게 됐다.
    private func startBackupExport() {
        guard let json = store.exportBackupContent() else {
            store.showToast("백업 파일을 만들지 못했어요", .error)
            return
        }
        backupDocument = TextDocument(json)
        exportBackup = true
    }

    private func readBackup(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        // 못 읽으면 알린다(27.52.0) — 예전엔 아무 일도 일어나지 않아, 복원이 된 건지 안 된 건지 알 수 없었다.
        // 읽은 뒤의 안내(형식 오류 · 복원 완료)는 공용 importBackupFromContent 가 낸다 — 여기서 겹쳐 내지 않는다.
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            store.showToast("파일을 읽지 못했어요", .error)
            return
        }
        store.importBackupFromContent(text)
    }
}

// ── 가챠 기록 초기화 확인 — 데이터 관리 · 가챠 리포트 공용 ─────────────────────────

/// 가챠 기록 초기화의 2단계 확인: 1차(백업 권장) → 2차(최종 확인) → 삭제.
///
/// 가챠 리포트 제목 줄의 「초기화」는 확인 없이 바로 전부 지웠다(27.52.0). 같은 동작이 들어온 곳에 따라
/// 다르게 굴지 않도록 창 두 개와 문구를 여기 한 곳에 두고, 두 화면이 그대로 쓴다.
private struct GachaClearConfirm: ViewModifier {
    @Binding var isPresented: Bool
    let store: SpendingStore
    /// 가챠 리포트에서 띄웠는가 — 그 화면에는 백업 버튼이 없어 **어디에 있는지**를 문구에 적는다(10/12 결정, Android 와 같다).
    var fromReport: Bool = false
    @State private var confirmFinal = false

    func body(content: Content) -> some View {
        content
            // 1단계(백업 권장)
            .alert("가챠 기록 초기화", isPresented: $isPresented) {
                Button("취소", role: .cancel) {}.glgAlertTint()
                Button("계속") { confirmFinal = true }.glgAlertTint()
            } message: {
                Text(fromReport
                     ? "가져온 모든 가챠 기록을 삭제합니다. 되돌릴 수 없으니, 먼저 설정 ▸ 데이터 관리의 ‘백업 파일 내보내기’로 백업을 권장해요."
                     : "가져온 모든 가챠 기록을 삭제합니다. 되돌릴 수 없으니, 먼저 ‘백업 파일 내보내기’로 백업을 권장해요.")
            }
            // 2단계(최종 확인)
            .alert("정말 초기화할까요?", isPresented: $confirmFinal) {
                Button("취소", role: .cancel) {}.glgAlertTint()
                Button("초기화", role: .destructive) { store.clearGachaRecords() }.glgAlertTint()
            } message: { Text("이 작업은 되돌릴 수 없어요. 가챠 기록을 모두 삭제합니다.") }
    }
}

extension View {
    /// 가챠 기록 초기화 확인 창을 건다 — [isPresented] 를 켜면 1차 안내부터 뜬다.
    func gachaClearConfirm(isPresented: Binding<Bool>, store: SpendingStore, fromReport: Bool = false) -> some View {
        modifier(GachaClearConfirm(isPresented: isPresented, store: store, fromReport: fromReport))
    }
}
