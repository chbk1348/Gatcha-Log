import SwiftUI
import Shared

// 지출 추가/수정 폼 — 게임·빠른상품·금액·재화명·날짜·결제수단·태그·메모·구독 + 과소비 넛지.
// (Compose AddSpendingModal 대응) ⚠️ 빠른상품 카테고리 필터는 생략(전체 표시). Spending 생성은 Kotlin saveSpending.
struct AddSpendingView: View {
    @ObservedObject var store: SpendingStore
    let editing: Spending?
    let onClose: () -> Void
    @Environment(\.glgAccent) private var accent

    @State private var gameName: String = "원신"
    @State private var amount: String = ""
    @State private var dateMillis: Int64 = 0
    @State private var paymentMethod: String = "카드"
    @State private var chargePlatform: String = ""
    @State private var itemName: String = ""
    @State private var memo: String = ""
    @State private var customTags: String = ""
    @State private var selectedTags: [String] = []
    @State private var isSubscription = false
    @State private var selectedPkg: String? = nil
    @State private var showDate = false
    @State private var nudgeMsg: String? = nil
    @State private var didInit = false

    private var game: Game { GameData.shared.byName(name: gameName) }
    private var amountValid: Bool { (Int64(amount) ?? 0) > 0 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    gameCard
                    packageCard
                    dateCard
                    tagCard
                }
                .padding(16)
            }
            .background(Color(hex: 0xFFF2F2F7))
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(editing == nil ? "지출 추가" : "지출 수정")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) { bottomBar }
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { onClose() } } }
        }
        .onAppear(perform: prefill)
        .sheet(isPresented: $showDate) {
            NavigationStack {
                DatePicker("날짜", selection: Binding(
                    get: { Date(timeIntervalSince1970: Double(dateMillis) / 1000) },
                    set: { dateMillis = Int64($0.timeIntervalSince1970 * 1000) }), displayedComponents: .date)
                    .datePickerStyle(.graphical).padding()
                    .environment(\.locale, Locale(identifier: "ko_KR"))
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("확인") { showDate = false } } }
            }
            .presentationDetents([.medium])
        }
        .alert("잠깐, 다시 한 번 볼까요?", isPresented: Binding(get: { nudgeMsg != nil }, set: { if !$0 { nudgeMsg = nil } })) {
            Button("다시 볼게요", role: .cancel) { nudgeMsg = nil }
            Button("그래도 추가") { doSave() }
        } message: { Text(nudgeMsg ?? "") }
    }

    // ── 섹션들 ──
    private var gameCard: some View {
        sectionCard {
            label("게임")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GameData.shared.games, id: \.key) { g in
                        let sel = g.displayName == gameName
                        Button { gameName = g.displayName; selectedPkg = nil } label: {
                            HStack(spacing: 8) {
                                Circle().fill(sel ? .white : Color(argb64: g.color)).frame(width: 7, height: 7)
                                Text(g.shortName).font(.system(size: 13, weight: .bold)).foregroundStyle(sel ? .white : GLGColor.textPrimary)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 9)
                            .background(sel ? Color(argb64: g.color) : Color(hex: 0xFFF2F2F7), in: RoundedRectangle(cornerRadius: 12))
                            .overlay(sel ? nil : RoundedRectangle(cornerRadius: 12).stroke(GLGColor.divider, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                }
            }
            .padding(.top, 10)
        }
    }

    private var packageCard: some View {
        sectionCard {
            label("빠른 상품 선택")
            Text("선택하면 금액·재화명이 자동 입력돼요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
            let packages = GameData.shared.packagesFor(game: game)
            let cols = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]
            LazyVGrid(columns: cols, spacing: 8) {
                ForEach(Array(packages.enumerated()), id: \.offset) { _, pkg in
                    let sel = selectedPkg == pkg.name
                    Button {
                        selectedPkg = pkg.name; amount = "\(pkg.price)"; itemName = pkg.name; isSubscription = (pkg.bonus == "월정액")
                    } label: {
                        VStack(spacing: 3) {
                            Text(pkg.name).font(.system(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                            HStack(spacing: 5) {
                                if let b = pkg.bonus { Text(b).font(.system(size: 10, weight: .bold)).foregroundStyle(accent.primary) }
                                Text(won(pkg.price)).font(.system(size: 11, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
                            }
                        }
                        .frame(maxWidth: .infinity).padding(.horizontal, 12).padding(.vertical, 9)
                        .background(sel ? accent.primary.opacity(0.1) : Color.white, in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(sel ? accent.primary : Color.black.opacity(0.08), lineWidth: 1))
                    }.buttonStyle(.plain)
                }
            }
            .padding(.top, 10)
            field("금액 (원)", "0", $amount, number: true).padding(.top, 14)
            field("재화명", "결정석 60", $itemName).padding(.top, 12)
        }
    }

    private var dateCard: some View {
        sectionCard {
            Button { showDate = true } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("날짜").font(.system(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                    HStack {
                        Text(DateUtil.shared.labelWithWeekday(millis: dateMillis)).foregroundStyle(GLGColor.textPrimary)
                        Spacer(); Image(systemName: "calendar").foregroundStyle(accent.primary)
                    }
                    .font(.system(size: 15)).glgPillField()
                }
            }.buttonStyle(.plain)
            label("결제 수단").padding(.top, 14)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) { ForEach(GameData.shared.paymentMethods, id: \.self) { m in chip(m, paymentMethod == m) { paymentMethod = m } } }
            }
            .padding(.top, 8)
            label("충전 플랫폼 (선택)").padding(.top, 14)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) { ForEach(GameData.shared.chargePlatforms, id: \.self) { p in chip(p, chargePlatform == p) { chargePlatform = (chargePlatform == p ? "" : p) } } }
            }
            .padding(.top, 8)
        }
    }

    private var tagCard: some View {
        sectionCard {
            label("태그")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GameData.shared.suggestedTags, id: \.self) { t in
                        chip(t, selectedTags.contains(t)) {
                            if let idx = selectedTags.firstIndex(of: t) { selectedTags.remove(at: idx) } else { selectedTags.append(t) }
                        }
                    }
                }
            }
            .padding(.top, 8)
            field("", "직접 입력 (쉼표로 구분)", $customTags).padding(.top, 10)
            field("메모", "이벤트 구입", $memo).padding(.top, 14)
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text("구독(월정액·패스)으로 기록").font(.system(size: 15, weight: .medium))
                    Text("정기 결제 항목으로 분류됩니다").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
                Spacer()
                Toggle("", isOn: $isSubscription).labelsHidden().tint(accent.primary)
            }
            .padding(.top, 16)
        }
    }

    private var bottomBar: some View {
        HStack(spacing: 12) {
            GLGOutlineButton(title: "취소") { onClose() }
            GLGButton(title: editing == nil ? "저장하기" : "수정하기") { attemptSave() }
                .opacity(amountValid ? 1 : 0.5).disabled(!amountValid)
        }
        .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 6)
        .background(Color(hex: 0xFFF2F2F7))
    }

    // ── 로직 ──
    private func prefill() {
        guard !didInit else { return }; didInit = true
        if let e = editing {
            gameName = e.gameName; amount = e.amount > 0 ? "\(e.amount)" : ""; dateMillis = e.dateMillis
            paymentMethod = e.paymentMethod.isEmpty ? "카드" : e.paymentMethod
            chargePlatform = e.chargePlatform
            itemName = e.itemName; memo = e.memo; selectedTags = e.tags; isSubscription = e.isSubscription
        } else {
            dateMillis = nowMs()
        }
    }

    private func attemptSave() {
        let parsed = Int64(amount) ?? 0
        if let msg = store.overspendNudge(game: game, amount: parsed, editingId: editing?.id) { nudgeMsg = msg }
        else { doSave() }
    }

    private func doSave() {
        nudgeMsg = nil
        let parsed = Int64(amount) ?? 0
        let extra = customTags.components(separatedBy: CharacterSet(charactersIn: ", "))
        var tags: [String] = []
        for t in (selectedTags + extra) { let tt = t.trimmingCharacters(in: .whitespaces); if !tt.isEmpty && !tags.contains(tt) { tags.append(tt) } }
        store.saveSpending(editingId: editing?.id, gameName: gameName, amount: parsed, dateMillis: dateMillis,
                           paymentMethod: paymentMethod, chargePlatform: chargePlatform, itemName: itemName, memo: memo, tags: tags, isSubscription: isSubscription)
        onClose()
    }

    // ── 공통 ──
    private func sectionCard<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
    private func label(_ t: String) -> some View { Text(t).font(.system(size: 14, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
    private func field(_ label: String, _ ph: String, _ text: Binding<String>, number: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            if !label.isEmpty { Text(label).font(.system(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary) }
            TextField(ph, text: text)
                .textFieldStyle(.plain)
                .font(.system(size: 15))
                .keyboardType(number ? .numberPad : .default)
                .glgPillField()
                .onChange(of: text.wrappedValue) { _, newValue in if number { text.wrappedValue = newValue.filter(\.isNumber) } }
        }
    }
    private func chip(_ label: String, _ selected: Bool, _ action: @escaping () -> Void) -> some View {
        GLGChip(label: label, selected: selected, action: action)
    }
}
