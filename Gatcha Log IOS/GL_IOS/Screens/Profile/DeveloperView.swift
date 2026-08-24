import SwiftUI
import Shared

/// 개발자 메뉴 — **디버그 빌드에서만** 설정에 나타난다(`#if DEBUG`).
///
/// 이 화면이 필요한 이유는 하나다. 어떤 UI 는 **특정 상태에서만 나타나서**, 그 상태가 실제로
/// 오기 전에는 눈으로 확인할 방법이 없다 — 3게임 모두 행동력 가득일 때의 비상벨, 하드 천장
/// 직전의 경고색, 예약이 실제로 잡혔는지 같은 것들. 여기서 그 상태를 만들고 들여다본다.
///
/// 판단·계산은 하나도 하지 않는다. 전부 공유 VM 의 `debug*` 를 부르고 결과를 그대로 그린다 —
/// 개발용 화면이 별도 로직을 갖기 시작하면 그것부터 실제와 어긋나 거짓말을 한다.
/// (Android `DeveloperScreen.kt` 대응 — 항목·문구를 같게 유지한다.)
struct DeveloperView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    /// 위 천장 버튼에 함께 적용. 같은 천장이라도 이것 하나로 필요 뽑기가 한 사이클(원신 90뽑) 갈린다.
    @State private var pityGuaranteed = false
    /// 진단 결과는 누른 시점의 스냅샷이다 — 계속 갱신되면 무엇을 보고 있는지 알 수 없다.
    @State private var reportTitle: String? = nil
    @State private var reportLines: [String] = []

    private var version: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "—"
    }
    private var build: String {
        (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "—"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Text("디버그 빌드에서만 보이는 화면이에요. 여기서 만든 값은 저장되지 않고, 다음 새로고침에 서버 값으로 덮어써집니다.")
                    .font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                makeStateSection
                diagnosticsSection
                if let reportTitle { reportSection(reportTitle) }
                buildSection
            }
            .padding(16)
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("개발자 메뉴")
        .navigationBarTitleDisplayMode(.inline)
    }

    // ── 상태 만들기 — "그 화면"을 지금 보고 싶을 때 ──
    private var makeStateSection: some View {
        sectionCard("상태 만들기") {
            devRow("bolt.fill", "행동력 3게임 가득", "행동력 카드의 비상벨이 뜨는 조건을 만든다") {
                store.debugFillAllResin()
            }
            Divider()
            devRow("bell.fill", "천장 하드 직전 (89)", "계산기 경고색·임박 토스트 확인") {
                store.debugSetPityAll(count: 89, guaranteed: pityGuaranteed)
            }
            Divider()
            devRow("exclamationmark.triangle", "천장 소프트 직전 (64)", "'주의' 단계 판정 확인") {
                store.debugSetPityAll(count: 64, guaranteed: pityGuaranteed)
            }
            Divider()
            devRow("arrow.counterclockwise", "천장 초기화 (0)", "전 게임 천장·확정 해제") {
                store.debugSetPityAll(count: 0, guaranteed: false)
            }
            Divider()
            Button { pityGuaranteed.toggle() } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("확정 보유로 설정").font(.pretendard(size: 14, weight: .medium))
                            .foregroundStyle(GLGColor.textPrimary)
                        Text("위 천장 버튼에 함께 적용").font(.pretendard(size: 11))
                            .foregroundStyle(GLGColor.textSecondary)
                    }
                    Spacer()
                    Text(pityGuaranteed ? "ON" : "OFF")
                        .font(.pretendard(size: 12, weight: .bold))
                        .foregroundStyle(pityGuaranteed ? accent.primary : GLGColor.textSecondary)
                }
                .padding(16)
            }
            .buttonStyle(.plain)
            Divider()
            devRow("arrow.clockwise", "온보딩 초기화", "앱을 다시 시작하면 온보딩이 나온다") {
                store.debugResetOnboarding()
            }
        }
    }

    // ── 진단 — "왜 안 나오지"를 볼 때 ──
    private var diagnosticsSection: some View {
        sectionCard("진단") {
            devRow("alarm", "예약될 알림 보기", "지금 설정으로 잡히는 예약을 시각 순으로") {
                show("예약될 알림", store.debugScheduledAlerts())
            }
            Divider()
            devRow("square.stack.3d.up", "게임별 데이터 도착", "한 게임만 비어 있는 부분 실패를 잡는다") {
                show("게임별 데이터", store.debugPerGameData())
            }
            Divider()
            devRow("hourglass", "로딩 게이트 상태", "스켈레톤이 안 걷힐 때") {
                show("로딩 게이트", [store.debugReadyStates()])
            }
            Divider()
            devRow("person.crop.circle", "계정·데이터 요약", "계정이 갈렸는지, 데이터가 실렸는지") {
                show("계정·데이터", [store.debugAccountSummary()])
            }
            Divider()
            devRow("arrow.triangle.2.circlepath", "캐시 무시하고 전체 재조회", "게임 정보·일정·소식을 강제로 다시 받는다") {
                store.refreshGameInfo(force: true)
            }
        }
    }

    // 진단 결과 — 누른 것만 보여준다
    @ViewBuilder
    private func reportSection(_ title: String) -> some View {
        sectionCard(title) {
            VStack(alignment: .leading, spacing: 9) {
                ForEach(Array(reportLines.enumerated()), id: \.offset) { _, line in
                    Text(line).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                Button("닫기") { reportTitle = nil; reportLines = [] }
                    .font(.pretendard(size: 12, weight: .bold))
                    .foregroundStyle(accent.primary)
                    .buttonStyle(.plain)
                    .padding(.top, 5)
            }
            .padding(16)
        }
    }

    private var buildSection: some View {
        sectionCard("빌드") {
            VStack(alignment: .leading, spacing: 9) {
                fact("버전", "\(version) (\(build))")
                fact("빌드 타입", buildTypeLabel)
                fact("번들 ID", Bundle.main.bundleIdentifier ?? "—")
            }
            .padding(16)
        }
    }

    private var buildTypeLabel: String {
        #if EXPERIMENT
        return "EXPERIMENT"
        #elseif DEBUG
        return "DEBUG"
        #else
        return "RELEASE"
        #endif
    }

    private func show(_ title: String, _ lines: [String]) {
        reportTitle = title
        reportLines = lines.isEmpty ? ["표시할 내용이 없습니다"] : lines
    }

    // ── 공용 서브뷰 (SettingsView 규격과 동일) ──

    @ViewBuilder
    private func sectionCard<C: View>(_ title: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.pretendard(size: 13, weight: .bold))
                .foregroundStyle(GLGColor.textSecondary).padding(.leading, 4)
            VStack(spacing: 0) { content() }
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
    }

    /// 아이콘 + 제목/설명 한 줄 — 누르면 바로 실행된다(확인 단계 없음, 되돌릴 수 있는 것만 둔다).
    @ViewBuilder
    private func devRow(_ icon: String, _ title: String, _ subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 17))
                    .foregroundStyle(accent.primary).frame(width: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.pretendard(size: 14, weight: .medium))
                        .foregroundStyle(GLGColor.textPrimary)
                    Text(subtitle).font(.pretendard(size: 11))
                        .foregroundStyle(GLGColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func fact(_ label: String, _ value: String) -> some View {
        HStack(spacing: 0) {
            Text(label).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                .frame(width: 76, alignment: .leading)
            Text(value).font(.pretendard(size: 12, weight: .medium))
                .foregroundStyle(GLGColor.textPrimary)
            Spacer(minLength: 0)
        }
    }
}
