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
    //
    // 일곱 개를 한 카드에 늘어놓던 것을 **성격별 세 묶음**으로 갈랐다(돈·플레이·소식).
    // 켜고 끄는 판단 기준이 서로 달라서, 한 덩어리로는 훑어지지 않았다.
    // 항목 정의(제목·설명·묶음)는 공유 소스 NotificationCatalog 하나뿐이다.

    /// 지금 켜져 있는 항목.
    private var notifyState: [NotifyKey: Bool] {
        [.budget: store.notifyBudget,
         .subscription: store.notifySubscription,
         .resin: store.notifyResin,
         .attendance: store.notifyAttendance,
         .pickup: store.notifyPickup,
         .combat: store.notifyCombat,
         .news: store.notifyNews]
    }
    private var anyNotifyOn: Bool { notifyState.values.contains(true) }

    private func notifyBinding(_ key: NotifyKey) -> Binding<Bool> {
        switch key {
        case .budget: return notifyBind(\.notifyBudget, store.setNotifyBudget)
        case .subscription: return notifyBind(\.notifySubscription, store.setNotifySubscription)
        case .resin: return notifyBind(\.notifyResin, store.setNotifyResin)
        case .attendance: return notifyBind(\.notifyAttendance, store.setNotifyAttendance)
        case .pickup: return notifyBind(\.notifyPickup, store.setNotifyPickup)
        case .combat: return notifyBind(\.notifyCombat, store.setNotifyCombat)
        case .news: return notifyBind(\.notifyNews, store.setNotifyNews)
        }
    }

    /// 아이콘만 플랫폼이 정한다(SF Symbols ↔ Material 은 이름 체계가 달라 공유할 수 없다).
    private func notifyIcon(_ key: NotifyKey) -> String {
        switch key {
        case .budget: return "banknote"
        case .subscription: return "arrow.triangle.2.circlepath"
        case .resin: return "bolt.fill"
        case .attendance: return "calendar.badge.checkmark"
        case .pickup: return "calendar.badge.clock"
        case .combat: return "medal"
        case .news: return "megaphone.fill"
        }
    }

    @ViewBuilder
    private var notificationSection: some View {
        // 헤더에 지금 상태를 붙인다 — 목록을 훑지 않고도 몇 개가 살아 있는지 보인다.
        HStack {
            Text("알림").font(.pretendard(size: 13, weight: .semibold))
                .foregroundStyle(GLGColor.textSecondary)
            Spacer()
            Text(NotificationCatalog.shared.enabledLabel(onCount: Int32(notifyState.values.filter { $0 }.count)))
                .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
        }
        .padding(.horizontal, 4)

        ForEach(Array(NotificationCatalog.shared.groups.enumerated()), id: \.offset) { _, group in
            VStack(alignment: .leading, spacing: 7) {
                HStack(alignment: .bottom, spacing: 6) {
                    Text(group.title).font(.pretendard(size: 12, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary)
                    Text(group.caption).font(.pretendard(size: 10.5))
                        .foregroundStyle(GLGColor.textSecondary)
                }
                .padding(.leading, 4)

                let entries = NotificationCatalog.shared.itemsIn(group: group)
                VStack(spacing: 0) {
                    ForEach(Array(entries.enumerated()), id: \.offset) { i, entry in
                        if i > 0 { Divider() }
                        toggleRow(notifyIcon(entry.key), entry.title, entry.desc, notifyBinding(entry.key))
                    }
                }
                .padding(.horizontal, 16)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            }
        }

        permissionBanner
    }

    /// 토글은 켰는데 시스템 알림 권한이 꺼져 있을 때만 뜨는 안내.
    @ViewBuilder
    private var permissionBanner: some View {
        // 묶음 카드 밖으로 나왔으므로 자기 면을 갖는다 — 배경 위에 버튼만 떠 있으면 안내로 안 읽힌다.
        Group {
            if notifBlocked && anyNotifyOn {
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
                    .contentShape(Rectangle()).padding(.vertical, 12)
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
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
