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

    /// 위 천장 버튼에 함께 적용. 같은 천장이라도 이것 하나로 필요 뽑기가 한 사이클(원신 90뽑) 갈린다.
    @State private var pityGuaranteed = false
    /// 목업 상태는 캐시에 얹히는 것이라 화면을 다시 열면 읽어 온다(onAppear).
    @State private var stageMock = false
    /// 호요랜드 행사 단계 목업 키(`""` = 없음).
    @State private var hoyoPhase = ""
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
            // 설정과 같은 결(9/30) — 묶음 제목 + 흰 카드 + 색 아이콘 줄.
            VStack(alignment: .leading, spacing: 0) {
                Text("디버그 빌드에서만 보이는 화면이에요. 여기서 만든 값은 저장되지 않고, 다음 새로고침에 서버 값으로 덮어써집니다.")
                    .font(.pretendard(size: 11.5)).foregroundStyle(Color(hex: 0xFF7A8784))
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 4).padding(.top, 4)

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
        .onAppear {
            stageMock = store.debugStageMockOn()
            hoyoPhase = store.debugHoyolandPhaseKey()
        }
    }

    // ── 상태 만들기 — "그 화면"을 지금 보고 싶을 때 ──
    private var makeStateSection: some View {
        sectionCard("상태 만들기", "그 화면을 지금 보고 싶을 때") {
            devRow("bolt.fill", .amber, "행동력 3게임 가득", "행동력 카드의 비상벨이 뜨는 조건을 만든다") {
                store.debugFillAllResin()
            }
            SetDivider()
            // 실제 무대 편성이 공개되기 전에 라이브 카드·게임 레인·필터를 보는 자리.
            // 기간이 오늘부터 4일로 옮겨져 늘 진행 중인 무대가 하나 잡힌다.
            devRow("theatermasks.fill", .purple, "호요랜드 무대 시간표 목업",
                   stageMock ? "켜짐 — 다시 누르면 원래 데이터로" : "라이브 카드·게임 레인 확인용") {
                stageMock.toggle()
                store.debugStageMock(stageMock)
                hoyoPhase = store.debugHoyolandPhaseKey()
            }
            SetDivider()
            // 이 화면은 단계마다 답하는 말이 통째로 바뀐다 — 카운트다운이 일차로, 게이지가
            // 사라지고, 예매와 「현장에서」 순서가 뒤집히고, 라인업 부제가 테마에서 무대 상태로
            // 간다. 개막일을 기다리지 않고 셋을 돌려 본다.
            devRow("calendar.badge.clock", .pink, "호요랜드 행사 단계",
                   hoyoPhase.isEmpty
                     ? "누를 때마다 개막 전 → 진행 중 → 종료 → 끔"
                     : "\(store.debugHoyolandPhaseLabel(hoyoPhase)) — 다시 누르면 다음 단계") {
                hoyoPhase = store.debugCycleHoyolandPhase()
                stageMock = store.debugStageMockOn() && hoyoPhase.isEmpty
            }
            SetDivider()
            devRow("bell.fill", .red, "천장 하드 직전 (89)", "계산기 경고색·임박 토스트 확인") {
                store.debugSetPityAll(count: 89, guaranteed: pityGuaranteed)
            }
            SetDivider()
            devRow("exclamationmark.triangle", .orange, "천장 소프트 직전 (64)", "'주의' 단계 판정 확인") {
                store.debugSetPityAll(count: 64, guaranteed: pityGuaranteed)
            }
            SetDivider()
            devRow("arrow.counterclockwise", .slate, "천장 초기화 (0)", "전 게임 천장·확정 해제") {
                store.debugSetPityAll(count: 0, guaranteed: false)
            }
            SetDivider()
            SetToggleRow(symbol: "checkmark.seal.fill", tint: .teal, title: "확정 보유로 설정",
                         desc: "위 천장 버튼에 함께 적용", isOn: $pityGuaranteed)
            SetDivider()
            devRow("arrow.clockwise", .navy, "온보딩 초기화", "누르면 바로 온보딩으로 돌아간다") {
                store.debugResetOnboarding()
            }
        }
    }

    // ── 진단 — "왜 안 나오지"를 볼 때 ──
    private var diagnosticsSection: some View {
        sectionCard("진단", "왜 안 나오는지 볼 때") {
            devRow("alarm", .teal, "예약될 알림 보기", "지금 설정으로 잡히는 예약을 시각 순으로") {
                show("예약될 알림", store.debugScheduledAlerts())
            }
            SetDivider()
            devRow("square.stack.3d.up", .blue, "게임별 데이터 도착", "한 게임만 비어 있는 부분 실패를 잡는다") {
                show("게임별 데이터", store.debugPerGameData())
            }
            SetDivider()
            devRow("hourglass", .amber, "로딩 게이트 상태", "스켈레톤이 안 걷힐 때") {
                show("로딩 게이트", [store.debugReadyStates()])
            }
            SetDivider()
            devRow("person.crop.circle", .purple, "계정·데이터 요약", "계정이 갈렸는지, 데이터가 실렸는지") {
                show("계정·데이터", [store.debugAccountSummary()])
            }
            SetDivider()
            // 붙이기 전에 실제 응답 구조를 본다 — 경로 · 필드가 공개 라이브러리 기준 추정이다.
            devRow("shield.lefthalf.filled", .navy, "젠레스 전투 API 확인", "시유 방어전 · 위험 구역 응답 구조") {
                show("젠레스 전투 API", ["불러오는 중…"])
                store.debugProbeZzzCombat { show("젠레스 전투 API", $0) }
            }
            SetDivider()
            devRow("arrow.triangle.2.circlepath", .slate, "캐시 무시하고 전체 재조회", "게임 정보·일정·소식을 강제로 다시 받는다") {
                store.refreshGameInfo(force: true)
            }
        }
    }

    // 진단 결과 — 누른 것만 보여준다
    @ViewBuilder
    private func reportSection(_ title: String) -> some View {
        sectionCard(title, "누른 시점의 결과") {
            VStack(alignment: .leading, spacing: 9) {
                ForEach(Array(reportLines.enumerated()), id: \.offset) { _, line in
                    Text(line).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                OdsButton(title: "닫기", variant: .secondary, size: .s, fullWidth: false) { reportTitle = nil; reportLines = [] }
                    .padding(.top, 5)
            }
            .padding(14)
        }
    }

    private var buildSection: some View {
        sectionCard("빌드", "이 기기에 깔린 앱") {
            fact("info.circle", "버전", "\(version) (\(build))")
            SetDivider()
            fact("hammer.fill", "빌드 타입", buildTypeLabel)
            SetDivider()
            fact("shippingbox.fill", "번들 ID", Bundle.main.bundleIdentifier ?? "—")
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

    // ── 공용 서브뷰 (설정 줄 키트 SetGroupTitle · SetCard · SetIcon 과 같은 규격) ──

    @ViewBuilder
    private func sectionCard<C: View>(_ title: String, _ caption: String, @ViewBuilder content: () -> C) -> some View {
        SetGroupTitle(title: title, caption: caption)
        SetCard { content() }
    }

    /// 색 아이콘 + 제목/설명 한 줄 — 누르면 바로 실행된다(확인 단계 없음, 되돌릴 수 있는 것만 둔다).
    @ViewBuilder
    private func devRow(_ icon: String, _ tint: SetTint, _ title: String, _ subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                SetIcon(symbol: icon, tint: tint)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text(subtitle).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14).padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func fact(_ icon: String, _ label: String, _ value: String) -> some View {
        HStack(spacing: 12) {
            SetIcon(symbol: icon, tint: .gray)
            Text(label).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            Spacer(minLength: 8)
            Text(value).font(.pretendard(size: 12.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
    }
}
