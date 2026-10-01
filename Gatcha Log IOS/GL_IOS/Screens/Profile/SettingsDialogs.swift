import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 설정 시트 — 업데이트 로그 · 출처. 예산은 BudgetSettingsView 페이지, 넛지 기준은 SettingsView 의 NudgeThresholdSheet.
// ════════════════════════════════════════════════════════════════════════════

// ── 출처 · 저작권 ─────────────────────────────────────────────────────────────

struct CreditsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent

    var body: some View {
        NavigationStack {
            ScrollView {
                // 문구는 공유 정본(Credits)에서 읽는다 — Android 와 한 글자도 다르지 않아야 한다.
                // 예전엔 양쪽에 따로 박혀 있어, 출처를 늘릴 때 한쪽만 고쳐질 위험이 있었다.
                VStack(alignment: .leading, spacing: 14) {
                    Text(Credits.shared.disclaimer)
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    ForEach(Credits.shared.sections, id: \.label) { creditRow($0.label, $0.body) }
                    Text(Credits.shared.notice)
                        .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
                .padding(20)
            }
            .background(GLGBackground { Color.clear })
            .navigationTitle("출처 · 저작권")
            .navigationBarTitleDisplayMode(.inline)
            // 「확인」은 하단 GLDS 버튼(9/30) — Android CreditsDialog 와 같은 자리.
            .safeAreaInset(edge: .bottom, spacing: 0) {
                GldsButton(title: "확인") { dismiss() }.padding(.horizontal, 20).padding(.vertical, 12)
            }
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

/// 업데이트 로그 — 06_ChangeLog.html 목업 디자인(필터칩·featured·마일스톤·분류 뱃지).
/// GLDS 2.0(10/1) — 카드를 걷고 버전 하나 = 화면 폭 섹션, 사이는 10 띠(SetBand).
/// 데이터는 공통 정본 `ChangeLog`(KMP)에서 읽어 Android와 동일하다.
struct UpdateLogPage: View {
    let version: String
    @Environment(\.glgAccent) private var accent
    @State private var filter: String? = nil   // ChangeKind.key("new"/"imp"/"fix"/"sec"), nil=전체
    @State private var showOld = false          // 지원 종료(강제 업데이트 대상) 버전 펼침 여부

    private let cText = Color(hex: 0xFF15181C)
    private let cItem = Color(hex: 0xFF2A2E34)
    private let cLine = Color(hex: 0xFFEEF0F2) // GLDS 2.0 헤어라인

    private var entries: [ChangeEntry] {
        let all = ChangeLog.shared.entries
        guard let f = filter else { return all }
        return all.filter { e in e.items.contains { $0.kind.key == f } }
    }
    // 강제 업데이트 지원 버전(minSupportedVersionCode 이상)은 펼쳐서, 그 미만(지원 종료)은 접기/펼치기.
    private var supported: [ChangeEntry] { entries.filter { $0.versionCode >= ChangeLog.shared.minSupportedVersionCode } }
    private var unsupported: [ChangeEntry] { entries.filter { $0.versionCode < ChangeLog.shared.minSupportedVersionCode } }

    var body: some View {
        ScrollView {
            // 히어로(큰 '업데이트 기록' 제목 + 부제 + 메타 2칸)는 걷어냈다 — 네비게이션 바 제목과 같은 말을
            // 반복하면서 첫 화면의 절반을 차지해, 정작 봐야 할 최신 버전이 스크롤 아래로 밀려 있었다.
            LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                Section {
                    // 버전 하나 = 섹션 하나, 사이는 띠(첫 섹션 위엔 없다).
                    ForEach(Array(supported.enumerated()), id: \.element.version) { i, entry in
                        if i > 0 { SetBand() }
                        releaseSection(entry)
                    }
                    // 지원 종료 버전 — 기본 접힘, '펼치기'로 열람.
                    if !unsupported.isEmpty {
                        if !supported.isEmpty { SetBand() }
                        Button { withAnimation(GLGMotion.standard()) { showOld.toggle() } } label: {
                            HStack(spacing: 2) {
                                Text("지원 종료 버전 \(unsupported.count)개")
                                    .font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                                Spacer()
                                Text(showOld ? "접기" : "펼치기")
                                    .font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(accent.primary)
                                Image(systemName: showOld ? "chevron.up" : "chevron.down")
                                    .font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                            }
                            .padding(.horizontal, 20).padding(.vertical, 20)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if showOld {
                            ForEach(unsupported, id: \.version) { entry in
                                SetBand()
                                releaseSection(entry)
                            }
                        }
                    }
                    if entries.isEmpty {
                        Text("해당 분류의 변경 사항이 없어요")
                            .font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
                            .frame(maxWidth: .infinity).padding(.vertical, 40).padding(.horizontal, 18)
                    }
                } header: { filterBar }
            }
            // 맨 아래 여백은 마지막 섹션(아래 20)이 가진다 — 예전 카드 12 + 여기 40 = 52 로 떠 보였다(10/1).
        }
        .background(Color.white)
        // 헤더 타이틀 = 설정 메뉴 항목과 같은 "업데이트 로그"(아래 히어로 제목과 역할이 다르다).
        .glgPageTitle("업데이트 로그")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        // 반투명 네비바로 스크롤 콘텐츠가 필터바 위로 비치는 것 방지 — 불투명 흰 배경 고정.
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarBackground(Color.white, for: .navigationBar)
    }

    // ── 스티키 필터칩 ──
    /// 분류 필터 — iOS 시스템 세그먼트 컨트롤(가로 스크롤 칩에서 교체).
    /// 항목이 다섯뿐이라 한 화면에 들어가고, 시스템 컨트롤이라 위치·크기·동작이 OS 표준을 따른다.
    private var filterBar: some View {
        GldsTabs(labels: ["전체", "신규", "개선", "수정", "보안"], selection: Binding(
            get: { ["", "new", "imp", "fix", "sec"].firstIndex(of: filter ?? "") ?? 0 },
            set: { i in let k = ["", "new", "imp", "fix", "sec"][i]; filter = k.isEmpty ? nil : k }
        ))
        .padding(.horizontal, 20)
        .padding(.top, 4).padding(.bottom, 10)
        // 흰 면을 **위로 넉넉히** 늘린다(9/30) — 제목 바를 걷은 듀오에서는 붙어 있는 탭 줄 위가 비어,
        // 스크롤한 내용이 탭 위로 비쳐 보였다.
        .background(Color.white.padding(.top, -400))
        .overlay(alignment: .bottom) { Rectangle().fill(cLine).frame(height: 1) }
    }

    // ── 릴리스 섹션 ──
    /// 버전 하나 = 화면 폭 섹션(좌우 20 · 위 22 · 아래 20 — 마지막 항목 글자가 자체 아래 여백이 없어 20 그대로).
    @ViewBuilder
    private func releaseSection(_ entry: ChangeEntry) -> some View {
        // 카드 안을 분류별 묶음으로(9/29 개편) — 신규 기능 · 수정 사항 · 개선 사항. Android ReleaseCard 파리티.
        let kinds: [ChangeKind] = filter == nil ? entry.groupKinds : entry.groupKinds.filter { $0.key == filter }
        if !kinds.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                if entry.featured {
                    Text("최신 버전").font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(Color(hex: 0xFF15C7A8), in: Capsule()).padding(.bottom, 10)
                }
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    (Text(entry.milestone && !entry.featured ? "★ " : "").foregroundColor(accent.primary)
                        + Text("v\(entry.version)").foregroundColor(cText))
                        .font(.pretendard(size: 17, weight: .bold))
                    Text(entry.date).font(.pretendard(size: 12.5, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
                    Spacer()
                    if let pill = entry.pill { pillView(pill, false) }
                    if entry.securityPill { pillView("보안 필수", true) }
                }.padding(.bottom, 10)
                ForEach(Array(kinds.enumerated()), id: \.offset) { gi, kind in
                    let list = entry.itemsOf(kind: kind)
                    let c = kindColors(kind.key)
                    HStack(spacing: 6) {
                        Circle().fill(c.0).frame(width: 6, height: 6)
                        Text(kind.groupLabel).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(c.2)
                        Text("\(list.count)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(c.2.opacity(0.7))
                    }
                    .padding(.horizontal, 9).padding(.vertical, 4)
                    .background(c.1, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                    .padding(.top, gi == 0 ? 0 : 16).padding(.bottom, 4)
                    ForEach(Array(list.enumerated()), id: \.offset) { _, item in
                        HStack(alignment: .top, spacing: 10) {
                            Circle().fill(c.0).frame(width: 4, height: 4).padding(.top, 8)
                            Text(item.text).font(.pretendard(size: 14)).foregroundStyle(cItem).lineSpacing(3)
                            Spacer(minLength: 0)
                        }
                        .padding(.leading, 2).padding(.top, 6)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 20)
        }
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
