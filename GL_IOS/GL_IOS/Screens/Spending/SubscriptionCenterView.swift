import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 정기결제 관리 센터 (27.33.0 신규) — 목업 design_subscription_center_mockup.html.
// ① 히어로(월 합계·구독 수·다음 갱신 D-day) ② 다가오는 갱신 리스트(dDay 오름차순)
// ③ 갱신일 알림 토글(notifySubscription) ④ 게임별 월 합계 미니바 ⑤ +추가/항목 탭→편집 시트.
// 추가/수정/삭제는 기존 VM(store.addSubscription/updateSubscription/deleteSubscription) 재사용.
// 진입: 지출 인사이트 "정기결제 요약" 카드 → [관리] 버튼.
// ════════════════════════════════════════════════════════════════════════════

struct SubscriptionCenterView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    /// 편집 시트 상태 — nil 이면 닫힘, .add 면 신규, .edit 면 기존 항목.
    @State private var editing: EditTarget? = nil

    private let amber = Color(hex: 0xFFF59E0B)

    /// dDay 오름차순 정렬된 구독 목록.
    /// 마감 임박순 목록.
    ///
    /// **한 번만 정렬한다.** 예전엔 computed 라 히어로·목록·행마다(`sorted.count`) 다시 정렬됐고,
    /// 비교자가 `dDay(nowMillis:)` 라 비교 한 번이 브리지 호출 두 번이었다 — 목록이 길수록 급격히 나빠졌다.
    /// 비교 시점마다 `nowMs()` 를 새로 읽던 것도 없앤다(자정 경계에서 정렬이 흔들릴 수 있었다).
    @State private var sorted: [Shared.Subscription] = []

    private func resort() {
        let now = nowMs()
        sorted = store.subscriptions.sorted { $0.dDay(nowMillis: now) < $1.dDay(nowMillis: now) }
    }
    private var monthlyTotal: Int64 { store.subscriptions.reduce(0) { $0 + $1.amount } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if store.unlinkedSubCount > 0 { importBanner }
                if store.subscriptions.isEmpty {
                    emptyState
                } else {
                    heroCard
                    upcomingCard
                    notifyToggleCard
                    perGameCard
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("정기결제 관리")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { editing = .add } label: { Image(systemName: "plus") }
            }
        }
        .sheet(item: $editing) { target in
            SubscriptionEditSheet(store: store, initial: target.subscription) { editing = nil }
        }
        .task(id: store.subscriptions) { resort() }
    }

    // ── 빈 상태 ──
    private var emptyState: some View {
        VStack(spacing: 14) {
            Text("월정액·패스 등 정기결제를 등록하면\n월 합계와 다음 결제일을 한곳에서 관리해요.")
                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                .multilineTextAlignment(.center)
            GLGButton(title: "정기결제 추가", systemImage: "plus", fullWidth: false) { editing = .add }
        }
        .frame(maxWidth: .infinity).padding(.top, 50)
    }

    // ── 지출에서 가져오기 배너 ──
    // 지출의 '구독으로 기록'(isSubscription) 중 아직 정기결제로 등록 안 된 건이 있을 때만 노출.
    // 가져오면 unlinkedSubCount 가 0 이 되어(subscriptions 갱신→재렌더) 배너가 사라진다.
    private var importBanner: some View {
        GLGCard(cornerRadius: 20, padding: 14) {
            HStack(spacing: 12) {
                Image(systemName: "arrow.down.doc.fill")
                    .font(.pretendard(size: 18, weight: .semibold))
                    .foregroundStyle(accent.primary)
                    .frame(width: 40, height: 40)
                    .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("지출에서 가져오기").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text("지출 내역에 정기결제로 표시된 \(store.unlinkedSubCount)건이 있어요")
                        .font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(2)
                }
                Spacer(minLength: 8)
                Button { store.importSubscriptionsFromSpendings() } label: {
                    Text("가져오기").font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(accent.primary, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                }.buttonStyle(.plain)
            }
        }
    }

    // ── ① 히어로 ──
    private var heroCard: some View {
        let next = sorted.first
        return GLGCard(cornerRadius: 24, padding: 18) {
            VStack(alignment: .leading, spacing: 0) {
                Text("월 정기결제 합계").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text(won(monthlyTotal)).font(.pretendard(size: 30, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text("/ 월").font(.pretendard(size: 14, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                }.padding(.top, 3)
                HStack(spacing: 8) {
                    heroPill(label: "구독 수", value: "\(store.subscriptions.count)건", highlight: false)
                    if let next {
                        heroPill(label: "다음 갱신", value: "\(dLabel(next)) · \(next.name)", highlight: true)
                    }
                }.padding(.top, 13)
            }
        }
    }

    private func heroPill(label: String, value: String, highlight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.pretendard(size: 11, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
            Text(value).font(.pretendard(size: 15, weight: .bold))
                .foregroundStyle(highlight ? amber : GLGColor.textPrimary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(highlight ? amber.opacity(0.10) : Color.black.opacity(0.03),
                    in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // ── ② 다가오는 갱신 ──
    private var upcomingCard: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("다가오는 갱신").font(.pretendard(size: 14, weight: .bold))
                    Spacer()
                    Button { editing = .add } label: {
                        Text("+ 추가").font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(accent.primary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                    }.buttonStyle(.plain)
                }.padding(.bottom, 4)
                let rows = sorted
                ForEach(Array(rows.enumerated()), id: \.element.id) { idx, sub in
                    Button { editing = .edit(sub) } label: { subRow(sub) }.buttonStyle(.plain)
                    if idx < rows.count - 1 { Divider() }
                }
            }
        }
    }

    private func subRow(_ sub: Shared.Subscription) -> some View {
        let d = sub.dDay(nowMillis: nowMs())
        return HStack(spacing: 11) {
            Circle().fill(Color(argb64: sub.gameColor)).frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 1) {
                Text(sub.name.isEmpty ? "구독" : sub.name).font(.pretendard(size: 14, weight: .semibold))
                    .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                Text("\(GameData.shared.byName(name: sub.gameName).shortName) · 매월 \(sub.billingDay)일")
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text(won(sub.amount)).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                ddBadge(d)
            }
            Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                .foregroundStyle(Color(.tertiaryLabel))
        }
        .contentShape(Rectangle())
        .padding(.vertical, 12)
    }

    /// D-day 뱃지 — 오늘=accent / D-1=앰버 / 그외 회색.
    private func ddBadge(_ d: Int32) -> some View {
        let (text, color): (String, Color) =
            d == 0 ? ("오늘", accent.primary)
            : d == 1 ? ("D-1", amber)
            : ("D-\(d)", GLGColor.textSecondary)
        return Text(text).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(color)
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
    }

    private func dLabel(_ sub: Shared.Subscription) -> String {
        let d = sub.dDay(nowMillis: nowMs())
        return d == 0 ? "오늘" : "D-\(d)"
    }

    // ── ③ 갱신일 알림 토글 ──
    private var notifyToggleCard: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            Toggle(isOn: Binding(
                get: { store.notifySubscription },
                set: { on in if on { NotificationPermission.request() }; store.setNotifySubscription(on) }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("갱신일 알림").font(.pretendard(size: 14, weight: .semibold))
                    Text("결제 하루 전(D-1) 푸시로 안내해요").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
            }.tint(accent.primary)
        }
    }

    // ── ④ 게임별 월 합계 미니바 ──
    @ViewBuilder private var perGameCard: some View {
        let rows = perGameRows
        if rows.count > 1 {
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("게임별 월 합계").font(.pretendard(size: 14, weight: .bold))
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        let frac = monthlyTotal > 0 ? Double(row.amount) / Double(monthlyTotal) : 0
                        VStack(spacing: 5) {
                            HStack {
                                Text(row.game).font(.pretendard(size: 13, weight: .medium))
                                Spacer()
                                Text(won(row.amount)).font(.pretendard(size: 13, weight: .bold))
                                Text("\(Int(frac*100))%").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                                    .frame(minWidth: 34, alignment: .trailing)
                            }
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(GLGColor.progressEmpty)
                                    Capsule().fill(Color(argb64: row.color)).frame(width: geo.size.width * frac)
                                }
                            }.frame(height: 5)
                        }.padding(.top, 12)
                    }
                }
            }
        }
    }

    private var perGameRows: [(game: String, amount: Int64, color: Int64)] {
        var m: [String: Int64] = [:]
        for s in store.subscriptions { m[s.gameName, default: 0] += s.amount }
        return m.sorted { $0.value > $1.value }
            .map { (game: GameData.shared.byName(name: $0.key).shortName,
                    amount: $0.value,
                    color: GameData.shared.colorFor(name: $0.key)) }
    }

    // 편집 시트 식별자.
    enum EditTarget: Identifiable {
        case add
        case edit(Shared.Subscription)
        var id: String {
            switch self {
            case .add: return "__add__"
            case .edit(let s): return s.id
            }
        }
        var subscription: Shared.Subscription? {
            switch self {
            case .add: return nil
            case .edit(let s): return s
            }
        }
    }
}

// ── 추가/수정 시트 (Android SubscriptionDialog 파리티: 상품명·게임칩·금액·결제일·삭제) ──
struct SubscriptionEditSheet: View {
    var store: SpendingStore
    let initial: Shared.Subscription?
    let onClose: () -> Void
    @Environment(\.glgAccent) private var accent

    @State private var name: String = ""
    @State private var gameName: String = "원신"
    @State private var amount: String = ""
    @State private var day: String = "1"
    @State private var didInit = false
    @State private var confirmDelete = false

    private var valid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
            && (Int64(amount) ?? 0) > 0
            && (1...31).contains(Int(day) ?? 0)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sectionCard {
                        label("상품명")
                        TextField("공월의 축복", text: $name).textFieldStyle(.plain)
                            .font(.pretendard(size: 15)).glgPillField().padding(.top, 6)
                    }
                    sectionCard {
                        label("게임")
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(GLGGames.all, id: \.key) { g in
                                    GLGChip(label: g.shortName, selected: g.displayName == gameName,
                                            color: Color(argb64: g.color)) { gameName = g.displayName }
                                }
                            }
                        }.padding(.top, 8)
                    }
                    sectionCard {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 5) {
                                label("월 금액 (원)")
                                TextField("4900", text: $amount).textFieldStyle(.plain).keyboardType(.numberPad)
                                    .font(.pretendard(size: 15)).glgPillField()
                                    .onChange(of: amount) { _, v in amount = v.filter(\.isNumber) }
                            }
                            VStack(alignment: .leading, spacing: 5) {
                                label("결제일 (1~31)")
                                TextField("1", text: $day).textFieldStyle(.plain).keyboardType(.numberPad)
                                    .font(.pretendard(size: 15)).glgPillField()
                                    .onChange(of: day) { _, v in day = String(v.filter(\.isNumber).prefix(2)) }
                            }
                        }
                    }
                    if initial != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "trash")
                                Text("이 구독 삭제").font(.pretendard(size: 14, weight: .bold))
                            }
                            .foregroundStyle(GLGColor.dangerText)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .background(GLGColor.dangerBackground, in: RoundedRectangle(cornerRadius: 14))
                        }.buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(Color.white)
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(initial == nil ? "정기결제 추가" : "정기결제 수정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { onClose() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(initial == nil ? "추가" : "저장") { save() }.disabled(!valid)
                }
            }
            .alert("이 구독을 삭제할까요?", isPresented: $confirmDelete) {
                Button("취소", role: .cancel) {}
                Button("삭제", role: .destructive) {
                    if let id = initial?.id { store.deleteSubscription(id) }
                    onClose()
                }
            }
        }
        .onAppear(perform: prefill)
    }

    private func prefill() {
        guard !didInit else { return }; didInit = true
        if let s = initial {
            name = s.name
            gameName = s.gameName
            amount = s.amount > 0 ? "\(s.amount)" : ""
            day = "\(s.billingDay)"
        }
    }

    private func save() {
        guard valid else { return }
        let sub = Shared.Subscription(
            id: initial?.id ?? UUID().uuidString,
            name: name.trimmingCharacters(in: .whitespaces),
            gameName: gameName,
            amount: Int64(amount) ?? 0,
            billingDay: Int32((Int(day) ?? 1).clamped(1, 31))
        )
        if initial == nil { store.addSubscription(sub) } else { store.updateSubscription(sub) }
        onClose()
    }

    private func sectionCard<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
    private func label(_ t: String) -> some View {
        Text(t).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
    }
}

private extension Int {
    func clamped(_ lo: Int, _ hi: Int) -> Int { Swift.min(Swift.max(self, lo), hi) }
}
