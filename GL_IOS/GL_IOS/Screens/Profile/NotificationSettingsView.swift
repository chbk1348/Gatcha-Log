import SwiftUI
import UserNotifications
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 알림 설정 — 항목별 알림 · 방해금지 · 데일리 요약을 한 곳에 모은 하위 페이지.
// (SettingsView 에서 분리 · Android NotificationSettingsScreen 파리티)
// ════════════════════════════════════════════════════════════════════════════

struct NotificationSettingsView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    /// 시스템 설정에서 알림을 켜고 돌아오면 배너가 사라져야 한다. 권한은 SwiftUI 상태가 아니라 OS 상태라
    /// 앱이 다시 활성화될 때 재조회한다 — 안 그러면 켜고 와도 "알림 권한이 꺼져 있어요"가 남는다.
    @Environment(\.scenePhase) private var scenePhase
    // 알림 토글은 켰는데 시스템 알림 권한이 거부된 상태(안내 표시용). 비동기 조회라 @State 로 캐시.
    @State private var notifBlocked = false
    /// 권한 상태 — .notDetermined 면 아직 OS 프롬프트를 띄울 수 있으므로 시스템 설정으로 보내지 않고 바로 요청한다.
    /// (.denied 는 프롬프트가 다시 뜨지 않아 시스템 설정 말고는 방법이 없다)
    @State private var authStatus: UNAuthorizationStatus = .notDetermined
    private var canPromptNotifPerm: Bool { authStatus == .notDetermined }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                notificationSection
                dndSection
                dailySummarySection
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("알림 설정")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { refreshNotifBlocked() }
        }
    }

    // ── 알림 — 항목별 토글 ──
    private var notificationSection: some View {
        sectionCard("알림") {
            toggleRow("banknote", "예산 알림", "이번 달 예산 90%·초과 시 알려줘요",
                      notifyBind(\.notifyBudget, store.setNotifyBudget))
            Divider()
            toggleRow("calendar.badge.checkmark", "출석 리마인더", "저녁까지 미출석이면 알려줘요",
                      notifyBind(\.notifyAttendance, store.setNotifyAttendance))
            Divider()
            toggleRow("bolt.fill", "재화 가득참 알림", "레진·개척력·배터리가 가득 차면 알려줘요",
                      notifyBind(\.notifyResin, store.setNotifyResin))
            Divider()
            toggleRow("calendar.badge.clock", "픽업 마감 알림", "진행 중인 픽업이 끝나기 전에 알려줘요",
                      notifyBind(\.notifyPickup, store.setNotifyPickup))
            Divider()
            toggleRow("medal", "전투 시즌 마감 알림", "나선 비경·혼돈의 기억 등을 못 깬 채 시즌이 끝나기 전에 알려줘요",
                      notifyBind(\.notifyCombat, store.setNotifyCombat))
            Divider()
            toggleRow("arrow.triangle.2.circlepath", "정기결제 갱신", "구독 결제 하루 전(D-1)에 알려줘요",
                      notifyBind(\.notifySubscription, store.setNotifySubscription))
            Divider()
            toggleRow("megaphone.fill", "새 공지 알림", "게임에 새 공지가 올라오면 알려줘요",
                      notifyBind(\.notifyNews, store.setNotifyNews))
            if notifBlocked && (store.notifyBudget || store.notifyAttendance || store.notifyResin || store.notifyPickup || store.notifyCombat || store.notifySubscription || store.notifyNews) {
                Divider()
                // 아직 프롬프트를 띄울 수 있으면(.notDetermined) 시스템 설정으로 보내지 말고 여기서 바로 요청한다.
                Button {
                    if canPromptNotifPerm {
                        NotificationPermission.request { refreshNotifBlocked() }
                    } else {
                        openSystemSettings()
                    }
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "bell.slash.fill").font(.pretendard(size: 18))
                            .foregroundStyle(Color(hex: 0xFFFB8C00)).frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("알림 권한이 꺼져 있어요").font(.pretendard(size: 14, weight: .medium))
                                .foregroundStyle(Color(hex: 0xFFFB8C00))
                            Text(canPromptNotifPerm
                                 ? "알림을 받으려면 권한을 허용해주세요."
                                 : "권한이 막혀 있어 알림이 표시되지 않아요. 설정에서 알림을 켜주세요.")
                                .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        }
                        Spacer()
                        Text(canPromptNotifPerm ? "허용" : "설정")
                            .font(.pretendard(size: 13, weight: .semibold))
                            .foregroundStyle(accent.primary)
                    }
                    .contentShape(Rectangle()).padding(.vertical, 10)
                }
                .buttonStyle(.plain)
            }
        }
        .onAppear(perform: refreshNotifBlocked)
    }

    // ── 방해금지 (27.33.0 신규) — 목업 design_notification_dnd_summary_mockup.html ──
    private var dndSection: some View {
        sectionCard("방해금지", footer: "자정을 넘는 시간대도 지원 · 기준은 기기 로컬 시각이에요 (출석=베이징과 별개)") {
            Toggle(isOn: notifyBind(\.notifyDndEnabled, store.setNotifyDndEnabled)) {
                rowLabel(icon: "moon.fill", title: "방해금지 시간",
                         subtitle: "이 시간대엔 알림을 보내지 않아요")
            }.tint(accent.primary).padding(.vertical, 10)
            if store.notifyDndEnabled {
                Divider()
                HStack(spacing: 10) {
                    hourBox(label: "시작", hour: store.notifyDndStartHour) { store.setNotifyDndStartHour($0) }
                    Text("~").font(.pretendard(size: 16, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    hourBox(label: "종료", hour: store.notifyDndEndHour) { store.setNotifyDndEndHour($0) }
                }
                .padding(.vertical, 12)
            }
        }
    }

    // ── 데일리 요약 (27.33.0 신규) ──
    private var dailySummarySection: some View {
        sectionCard("데일리 요약",
                    footer: "요약 ON이면 그날 개별 알림은 묶어 한 건으로 보내요. OFF면 기존처럼 개별 발송돼요.") {
            Toggle(isOn: notifyBind(\.notifyDailySummary, store.setNotifyDailySummary)) {
                rowLabel(icon: "envelope.fill", title: "하루 한 번 요약",
                         subtitle: "흩어진 알림을 묶어 1건으로 보내요")
            }.tint(accent.primary).padding(.vertical, 10)
            if store.notifyDailySummary {
                Divider()
                HStack {
                    Text("요약 보낼 시각").font(.pretendard(size: 14, weight: .medium))
                    Spacer()
                    hourMenu(hour: store.notifyDailySummaryHour) { store.setNotifyDailySummaryHour($0) }
                }
                .padding(.vertical, 13)
            }
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

    /// 시(0~23) 선택 박스 — 라벨 + "HH:00" 큰 글자 (DnD 시작/종료용).
    private func hourBox(label: String, hour: Int, _ onPick: @escaping (Int) -> Void) -> some View {
        Menu {
            Picker("", selection: Binding(get: { hour }, set: { onPick($0) })) {
                ForEach(0..<24, id: \.self) { h in Text(String(format: "%02d:00", h)).tag(h) }
            }
        } label: {
            VStack(spacing: 2) {
                Text(label).font(.pretendard(size: 10.5, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                Text(String(format: "%02d:00", hour)).font(.pretendard(size: 20, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 11)
            .background(Color.black.opacity(0.03), in: RoundedRectangle(cornerRadius: 14))
        }
    }

    /// 시(0~23) 선택 메뉴 — "HH:00" 필 (요약 시각용).
    private func hourMenu(hour: Int, _ onPick: @escaping (Int) -> Void) -> some View {
        Menu {
            Picker("", selection: Binding(get: { hour }, set: { onPick($0) })) {
                ForEach(0..<24, id: \.self) { h in Text(String(format: "%02d:00", h)).tag(h) }
            }
        } label: {
            Text(String(format: "%02d:00", hour)).font(.pretendard(size: 16, weight: .bold)).foregroundStyle(accent.primary)
                .padding(.horizontal, 14).padding(.vertical, 7)
                .background(Color.black.opacity(0.04), in: RoundedRectangle(cornerRadius: 12))
        }
    }

    /// 시스템 알림 권한 상태를 조회해 notifBlocked·authStatus 갱신(거부/미결정이면 차단으로 간주).
    private func refreshNotifBlocked() {
        NotificationPermission.status { status in
            authStatus = status
            notifBlocked = !(status == .authorized || status == .provisional)
        }
    }

    /// 이 앱의 시스템 설정 화면 열기(권한 직접 변경 유도).
    private func openSystemSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    // ── 행 헬퍼 (SettingsView 와 동일 규격) ──
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

    private func toggleRow(_ icon: String, _ title: String, _ subtitle: String, _ binding: Binding<Bool>) -> some View {
        Toggle(isOn: binding) { rowLabel(icon: icon, title: title, subtitle: subtitle) }
            .tint(accent.primary).padding(.vertical, 10)
    }

    /// 알림 토글용 — 켤 때 iOS 알림 권한을 요청한다.
    private func notifyBind(_ keyPath: KeyPath<SpendingStore, Bool>, _ setter: @escaping (Bool) -> Void) -> Binding<Bool> {
        Binding(get: { store[keyPath: keyPath] }, set: { on in
            if on { NotificationPermission.request { refreshNotifBlocked() } }
            setter(on)
        })
    }
}
