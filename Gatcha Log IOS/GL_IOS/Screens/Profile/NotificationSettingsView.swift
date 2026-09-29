import SwiftUI
import UserNotifications
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 알림 설정 — 항목별 알림 · 방해금지를 한 곳에 모은 하위 페이지.
// 개편(아티팩트 S1): 온보딩 ⑤와 같은 결 — 미리보기 알림 + 묶음별 색 아이콘 줄 + 보내는 방식.
// 데일리 요약은 9/29 제거.
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
            VStack(alignment: .leading, spacing: 0) {
                NotifyPreviewCard(status: NotificationCatalog.shared.enabledLabel(onCount: Int32(onCount)))
                permissionBanner
                notificationSection
                dndSection
            }
            .padding(16)
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("알림 설정")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { refreshNotifBlocked() }
        }
    }

    // ── 알림 — 항목별 토글 ──
    //
    // 일곱 개를 **성격별 세 묶음**으로 가른다(돈·플레이·소식). 켜고 끄는 판단 기준이 서로 달라서다.
    // 항목 정의(제목·설명·묶음)는 공유 소스 NotificationCatalog 하나뿐이다
    // (호요랜드는 행사가 끝나면 목록에서 빠진다 — NotificationCatalog.hoyolandAlertsActive).

    /// 지금 켜져 있는 항목.
    private var notifyState: [NotifyKey: Bool] {
        [.budget: store.notifyBudget,
         .resin: store.notifyResin,
         .attendance: store.notifyAttendance,
         .pickup: store.notifyPickup,
         .combat: store.notifyCombat,
         .news: store.notifyNews,
         .hoyoland: store.notifyHoyoland]
    }
    private var anyNotifyOn: Bool { notifyState.values.contains(true) }
    /// 보이는 항목 중 켜진 개수(행사가 끝난 호요랜드는 세지 않는다).
    private var onCount: Int { NotificationCatalog.shared.items.filter { notifyState[$0.key] == true }.count }

    private func notifyBinding(_ key: NotifyKey) -> Binding<Bool> {
        switch key {
        case .budget: return notifyBind(\.notifyBudget, store.setNotifyBudget)
        case .resin: return notifyBind(\.notifyResin, store.setNotifyResin)
        case .attendance: return notifyBind(\.notifyAttendance, store.setNotifyAttendance)
        case .pickup: return notifyBind(\.notifyPickup, store.setNotifyPickup)
        case .combat: return notifyBind(\.notifyCombat, store.setNotifyCombat)
        case .news: return notifyBind(\.notifyNews, store.setNotifyNews)
        case .hoyoland: return notifyBind(\.notifyHoyoland, store.setNotifyHoyoland)
        }
    }

    /// 아이콘만 플랫폼이 정한다(SF Symbols ↔ Material 은 이름 체계가 달라 공유할 수 없다).
    private func notifyIcon(_ key: NotifyKey) -> String {
        switch key {
        case .budget: return "creditcard"
        case .resin: return "clock"
        case .attendance: return "calendar.badge.checkmark"
        case .pickup: return "star"
        case .combat: return "trophy"
        case .news: return "megaphone"
        case .hoyoland: return "party.popper"
        }
    }

    /// 항목별 아이콘 색 — Android notifyTint 파리티.
    private func notifyTint(_ key: NotifyKey) -> SetTint {
        switch key {
        case .budget: return .orange
        case .resin: return .blue
        case .attendance: return .teal
        case .pickup: return .purple
        case .combat: return .amber
        case .news: return .slate
        case .hoyoland: return .pink
        }
    }

    @ViewBuilder
    private var notificationSection: some View {
        ForEach(Array(NotificationCatalog.shared.groups.enumerated()), id: \.offset) { _, group in
            SetGroupTitle(title: group.title, caption: group.caption)
            let entries = NotificationCatalog.shared.itemsIn(group: group)
            SetCard {
                ForEach(Array(entries.enumerated()), id: \.offset) { i, entry in
                    if i > 0 { SetDivider() }
                    SetToggleRow(symbol: notifyIcon(entry.key), tint: notifyTint(entry.key),
                                 title: entry.title, desc: entry.desc, isOn: notifyBinding(entry.key))
                }
            }
        }
    }

    /// 토글은 켰는데 시스템 알림 권한이 꺼져 있을 때만 뜨는 안내.
    @ViewBuilder
    private var permissionBanner: some View {
        Group {
            if notifBlocked && anyNotifyOn {
                // 아직 프롬프트를 띄울 수 있으면(.notDetermined) 시스템 설정으로 보내지 말고 여기서 바로 요청한다.
                WarnBanner(
                    text: canPromptNotifPerm ? "알림 권한이 꺼져 있어요. 허용해야 알림이 와요."
                                             : "권한이 막혀 있어 알림이 표시되지 않아요. 설정에서 켜 주세요.",
                    action: canPromptNotifPerm ? "허용" : "설정"
                ) {
                    if canPromptNotifPerm {
                        // 이 배너의 '허용'은 "알림을 받고 싶다"는 뜻이므로, 처음 허용한 순간
                        // 항목 일곱 개를 전부 켠다. 개별 토글 경로(notifyBind)와 구분해야 한다 —
                        // 거긴 그 항목만 켜려던 것이라 전부 켜면 의도와 어긋난다.
                        NotificationPermission.request { newlyGranted in
                            if newlyGranted { store.enableAllNotifyItems() }
                            refreshNotifBlocked()
                        }
                    } else {
                        openSystemSettings()
                    }
                }
            }
        }
        .onAppear(perform: refreshNotifBlocked)
    }

    // ── 보내는 방식 — 방해금지(시간대 억제) ──
    @ViewBuilder
    private var dndSection: some View {
        SetGroupTitle(title: "보내는 방식", caption: "언제 · 어떻게")
        SetCard {
            SetToggleRow(symbol: "moon.fill", tint: .gray, title: "방해 금지 시간", desc: "이 시간대엔 알림을 보내지 않아요",
                         isOn: notifyBind(\.notifyDndEnabled, store.setNotifyDndEnabled))
            if store.notifyDndEnabled {
                HStack(spacing: 8) {
                    TimePillMenu(hour: store.notifyDndStartHour) { store.setNotifyDndStartHour($0) }
                    Text("~").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(Color(hex: 0xFF7A8784))
                    TimePillMenu(hour: store.notifyDndEndHour) { store.setNotifyDndEndHour($0) }
                    Text("기기 시각").font(.pretendard(size: 11.5)).foregroundStyle(Color(hex: 0xFF7A8784)).padding(.leading, 2)
                    Spacer()
                }
                .padding(.leading, 60).padding(.trailing, 14).padding(.bottom, 14)
            }
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

    /// 알림 토글용 — 켤 때 iOS 알림 권한을 요청한다.
    private func notifyBind(_ keyPath: KeyPath<SpendingStore, Bool>, _ setter: @escaping (Bool) -> Void) -> Binding<Bool> {
        Binding(get: { store[keyPath: keyPath] }, set: { on in
            // 여기서 처음 허용해도 **그 항목만** 켠다 — 일괄 ON 은 배너의 '허용'과 온보딩 전용.
            if on { NotificationPermission.request { _ in refreshNotifBlocked() } }
            setter(on)
        })
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 설정 줄 키트 — 아티팩트 S0 · S1 · S2(설정 · 알림 설정 · 내 게임) 공용. Android SettingsScreen 의
// NotifyGroupTitle · NotifyCard · NotifyRow · SettingsNavRow · WarnBanner 파리티.
// ════════════════════════════════════════════════════════════════════════════

/// 아이콘 색 짝(글자색, 옅은 바탕).
struct SetTint {
    let fg: Color
    let bg: Color
    static let teal = SetTint(fg: Color(hex: 0xFF177881), bg: Color(hex: 0xFFE3F2F1))
    static let purple = SetTint(fg: Color(hex: 0xFF9350F0), bg: Color(hex: 0xFFF1E8FD))
    static let orange = SetTint(fg: Color(hex: 0xFFC2410C), bg: Color(hex: 0xFFFFF1E6))
    static let amber = SetTint(fg: Color(hex: 0xFFB45309), bg: Color(hex: 0xFFFEF3C7))
    static let navy = SetTint(fg: Color(hex: 0xFF0F1A33), bg: Color(hex: 0xFFECEFF4))
    static let slate = SetTint(fg: Color(hex: 0xFF475569), bg: Color(hex: 0xFFEEF1F5))
    static let pink = SetTint(fg: Color(hex: 0xFFDB2777), bg: Color(hex: 0xFFFCE7F3))
    static let blue = SetTint(fg: Color(hex: 0xFF3E76E0), bg: Color(hex: 0xFFE8F0FD))
    static let red = SetTint(fg: Color(hex: 0xFFB91C1C), bg: Color(hex: 0xFFFEE2E2))
    static let gray = SetTint(fg: Color(hex: 0xFF4F5C59), bg: Color(hex: 0xFFF5F8F8))
}

/// 묶음 제목 + 설명.
struct SetGroupTitle: View {
    let title: String
    let caption: String
    /// 페이지 첫 묶음이면 위 여백을 줄인다 — 페이지 여백(16)에 20 이 더해져 헤더 아래가 36 으로 떠 보였다(9/29 지적).
    var first: Bool = false
    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 6) {
            Text(title).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            Text(caption).font(.pretendard(size: 11.5)).foregroundStyle(Color(hex: 0xFF7A8784))
        }
        .padding(.horizontal, 4).padding(.top, first ? 4 : 20).padding(.bottom, 8)
    }
}

/// 흰 카드 + 옅은 테두리.
struct SetCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(spacing: 0) { content }
            .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Color(hex: 0xFFE3E8E6), lineWidth: 1))
    }
}

struct SetDivider: View {
    var body: some View { Rectangle().fill(Color(hex: 0xFFF0F3F2)).frame(height: 1) }
}

/// 색 아이콘 칸(34). SF Symbol 또는 에셋.
struct SetIcon: View {
    var symbol: String? = nil
    var asset: String? = nil
    let tint: SetTint
    var body: some View {
        Group {
            if let asset {
                Image(asset).renderingMode(.template).resizable().scaledToFit().frame(width: 17, height: 17)
            } else if let symbol {
                Image(systemName: symbol).font(.system(size: 16, weight: .semibold))
            }
        }
        .foregroundStyle(tint.fg)
        .frame(width: 34, height: 34)
        .background(tint.bg, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
    }
}

/// 색 아이콘 + 제목/설명 + 스위치.
struct SetToggleRow: View {
    let symbol: String
    let tint: SetTint
    let title: String
    let desc: String
    @Binding var isOn: Bool
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Toggle(isOn: $isOn) {
            HStack(spacing: 12) {
                SetIcon(symbol: symbol, tint: tint)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text(desc).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
            }
        }
        .tint(accent.primary)
        .padding(.horizontal, 14).padding(.vertical, 12)
    }
}

/// 색 아이콘 + 제목 + 값 + 화살표.
struct SetNavRow<Trailing: View>: View {
    var symbol: String? = nil
    var asset: String? = nil
    let tint: SetTint
    let title: String
    var value: String? = nil
    var chevron: String? = "chevron.right"
    var titleColor: Color = GLGColor.textPrimary
    @ViewBuilder var trailing: Trailing
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                SetIcon(symbol: symbol, asset: asset, tint: tint)
                Text(title).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(titleColor)
                Spacer(minLength: 8)
                trailing
                if let value { Text(value).font(.pretendard(size: 12.5)).foregroundStyle(GLGColor.textSecondary) }
                if let chevron {
                    Image(systemName: chevron).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color(hex: 0xFFB8C4C1))
                }
            }
            .contentShape(Rectangle())
            .padding(.horizontal, 14).padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }
}

extension SetNavRow where Trailing == EmptyView {
    init(symbol: String? = nil, asset: String? = nil, tint: SetTint, title: String, value: String? = nil,
         chevron: String? = "chevron.right", titleColor: Color = GLGColor.textPrimary, action: @escaping () -> Void) {
        self.init(symbol: symbol, asset: asset, tint: tint, title: title, value: value, chevron: chevron,
                  titleColor: titleColor, trailing: { EmptyView() }, action: action)
    }
}

/// 주황 안내 띠 + 버튼 — 권한 · 배터리 경고 공용.
struct WarnBanner: View {
    let text: String
    let action: String
    let onTap: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle").font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color(hex: 0xFFC2410C))
            Text(text).font(.pretendard(size: 12.5)).foregroundStyle(Color(hex: 0xFF9A3412))
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onTap) {
                Text(action).font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Color(hex: 0xFFC2410C), in: Capsule())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .background(Color(hex: 0xFFFFF4E8), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Color(hex: 0xFFFED7AA), lineWidth: 1))
        .padding(.top, 12)
    }
}

/// 미리보기 알림 카드 — 켜면 어떤 알림이 오는지 먼저 보여 주고, 오른쪽 위에 켜진 개수.
struct NotifyPreviewCard: View {
    var status: String? = nil
    var title: String = "레진이 곧 가득 차요"
    var detail: String = "원신 190 / 200 · 20분 뒤 가득"
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let status {
                HStack {
                    Text("이렇게 알려 드려요").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(Color(hex: 0xFF177881))
                    Spacer()
                    Text(status).font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(Color(hex: 0xFF177881))
                        .padding(.horizontal, 10).padding(.vertical, 4).background(Color.white, in: Capsule())
                }
                .padding(.bottom, 10)
            }
            UnevenRoundedRectangle(topLeadingRadius: 12, topTrailingRadius: 12)
                .fill(Color.white.opacity(0.55)).frame(height: 12).padding(.horizontal, 10)
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "bell").font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(LinearGradient(colors: [Color(hex: 0xFF1FA0AB), Color(hex: 0xFF146E77)],
                                               startPoint: .topLeading, endPoint: .bottomTrailing),
                                in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text("Gatcha Log").font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(Color(hex: 0xFF4F5C59))
                        Spacer()
                        Text("지금").font(.pretendard(size: 11.5)).foregroundStyle(Color(hex: 0xFF7A8784))
                    }
                    Text(title).font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(Color(hex: 0xFF0F1A33))
                    Text(detail).font(.pretendard(size: 12)).foregroundStyle(Color(hex: 0xFF5E6B68))
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: Color(hex: 0x1A0F1A33), radius: 9, y: 6)
        }
        .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 18)
        .background(LinearGradient(colors: [Color(hex: 0xFFEEF8F8), Color(hex: 0xFFDCF0EE)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// 시각 알약 — 누르면 0~23시 선택.
struct TimePillMenu: View {
    let hour: Int
    let onPick: (Int) -> Void
    var body: some View {
        Menu {
            Picker("", selection: Binding(get: { hour }, set: { onPick($0) })) {
                ForEach(0..<24, id: \.self) { h in Text(String(format: "%02d:00", h)).tag(h) }
            }
        } label: {
            Text(String(format: "%02d:00", hour)).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                .padding(.horizontal, 14).padding(.vertical, 6)
                .background(Color.white, in: Capsule())
                .overlay(Capsule().stroke(Color(hex: 0xFFE3E8E6), lineWidth: 1))
        }
    }
}
