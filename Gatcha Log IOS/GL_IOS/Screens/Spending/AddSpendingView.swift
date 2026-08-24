import SwiftUI
import Shared

// 지출 추가/수정 폼 — 게임·빠른상품·금액·재화명·날짜·결제수단·태그·메모·구독 + 과소비 넛지.
// (Compose AddSpendingModal 대응) ⚠️ 빠른상품 카테고리 필터는 생략(전체 표시). Spending 생성은 Kotlin saveSpending.
struct AddSpendingView: View {
    var store: SpendingStore
    let editing: Spending?
    /// 네비게이션 스택에 **밀려 들어온** 상태(상세 페이지 형식)인가.
    ///
    /// true 면 자기 `NavigationStack` 을 만들지 않는다 — 이미 스택 안이라 중첩하면 뒤로가기가
    /// 두 겹이 된다. '닫기' 버튼도 달지 않는다(왼쪽 위 뒤로가기가 그 일을 한다).
    var pushed: Bool = false
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
    // 한 번에 같은 상품을 여러 번 산 경우 — 횟수만큼 금액·재화를 곱해 한 건으로 기록.
    @State private var quantity: Int = 1
    @State private var showDate = false
    @State private var nudgeMsg: String? = nil
    @State private var didInit = false
    /// '자주 사는 것' 아래 전체 상품 그리드를 폈는가.
    @State private var showAllPackages = false
    /// '자세히'(결제·플랫폼·태그·메모·구독)를 폈는가. **수정 진입은 펼친 채 시작**한다.
    @State private var detailsExpanded = false
    /// 히어로에 바로 뜨는 과소비 경고 — 저장을 누른 뒤가 아니라 금액이 정해지는 순간에 알린다.
    @State private var inlineNudge: String? = nil
    /// 사용자가 게임을 **직접 골랐는가.**
    ///
    /// 추가 진입은 게임을 미리 정해두지 않는다. 마지막에 기록한 게임을 자동으로 넣으면
    /// 다른 게임을 기록하러 온 사람이 **못 알아채고 엉뚱한 게임에 저장**한다 —
    /// 지출은 게임별 집계·예산의 기준이라 그 오기록은 나중에 찾기 어렵다.
    /// 결제수단·플랫폼과 달리 게임은 "틀려도 티가 안 나는" 값이 아니다.
    @State private var gameChosen = false

    private var game: Game { GameData.shared.byName(name: gameName) }
    private var amountValid: Bool { (Int64(amount) ?? 0) > 0 }
    private var canSave: Bool { gameChosen && amountValid }
    /// 못 누르는 이유를 버튼이 직접 말한다 — 게임 먼저, 그다음 금액.
    private var saveTitle: String {
        if !gameChosen { return "게임을 선택하세요" }
        if !amountValid { return "금액을 입력하세요" }
        return editing == nil ? "저장하기" : "수정하기"
    }

    private var form: some View {
        ScrollView {
            VStack(spacing: 12) {
                amountHero
                // 게임을 고르기 전에는 나머지를 띄우지 않는다 — 상품 목록·기본값이 전부 게임에 묶여 있어
                // 미선택 상태로 보여주면 어느 게임 것인지 알 수 없는 화면이 된다.
                if gameChosen {
                    productCard.transition(cardReveal)
                    dateCard.transition(cardReveal)
                    detailsCard.transition(cardReveal)
                }
            }
            .padding(16)
            .glgReadableWidth(640)
        }
        .background(Color.white)
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(editing == nil ? "지출 추가" : "지출 수정")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) { bottomBar }
        .toolbar {
            if !pushed {
                ToolbarItem(placement: .cancellationAction) { Button("닫기") { onClose() } }
            }
        }
        // 입력 화면에서는 하단 탭바를 감춘다 — 저장/취소 바가 이미 하단을 쓰고 있어 두 겹이 되고,
        // 폼을 채우다 탭을 눌러 나가면 입력이 날아간다. (Android 는 루트를 스왑해 애초에 탭바가 없다.)
        //
        // 추가(탭 스택 루트에서 push)든 수정(지출 상세 위로 push)이든 같은 자리에서 처리된다 —
        // 화면이 자기 상태를 선언하므로 어느 경로로 들어와도 새는 곳이 없다.
        // 숨김은 **여기서 선언하지 않는다.** 화면이 직접 걸면 pop 되는 순간 선언이 사라져
        // 탭바가 애니메이션 없이 튀어나온다("짠" 하고 등장).
        // ContentView 가 상태(spendingSheet·spendingEditorOpen)로 들고 있어야
        // 값 변화가 전환에 실려 사라질 때와 같은 결로 돌아온다.
    }

    var body: some View {
        Group {
            if pushed { form } else { NavigationStack { form } }
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
            Button("다시 볼게요", role: .cancel) { nudgeMsg = nil }.glgAlertTint()
            Button("그래도 추가") { doSave() }.glgAlertTint()
        } message: { Text(nudgeMsg ?? "") }
    }

    // ── 섹션들 ──
    // ── 금액 히어로 — 게임·금액·상품·재화 환산을 한 덩어리로 ──
    //
    // 금액은 지출에서 가장 중요한 값인데 예전엔 '빠른 상품' 카드 **안쪽**, 그리드 아래
    // 일반 필드로 있었다. 목록·상세·인사이트에서는 전부 금액이 히어로인데 입력할 때만 아니었다.
    // 지출 상세 히어로와 같은 짜임이라 '기록한 것'과 '나중에 보는 것'이 같은 모양이 된다.
    private var amountHero: some View {
        let gameColor = gameChosen ? Color(argb64: game.color) : GLGColor.textSecondary
        return VStack(alignment: .leading, spacing: 0) {
            if gameChosen {
                // 게임 — 칩 줄을 늘어놓지 않고 메뉴로 접었다(히어로가 금액을 가리면 안 된다).
                Menu {
                    ForEach(GLGGames.all, id: \.key) { g in
                        Button(g.displayName) { selectGame(g.displayName) }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Circle().fill(gameColor).frame(width: 7, height: 7)
                        Text(GameData.shared.byName(name: gameName).shortName)
                            .font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(gameColor)
                        Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold)).foregroundStyle(gameColor)
                    }
                    .padding(.horizontal, 11).padding(.vertical, 5)
                    .background(gameColor.opacity(0.12), in: Capsule())
                }
            } else {
                // 미선택 — 고르기 전에는 금액을 받지 않는다. 첫 할 일이 무엇인지 화면이 말한다.
                Text("어느 게임인가요?")
                    .font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("게임을 선택해주세요")
                    .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.top, 3)
                // 가로 스크롤이 아니라 **줄바꿈**이다 — 첫 할 일이 "게임 고르기"인데 스크롤로 접어 두면
                // 화면 밖 게임은 있는 줄도 모른다(고를 수 있는 게 몇 개인지조차 안 보인다).
                // 카드 폭 안에서 전부 한눈에 들어와야 고르는 화면 구실을 한다.
                FlowLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(GLGGames.all, id: \.key) { g in
                        GLGChip(label: g.shortName, selected: false, color: Color(argb64: g.color)) {
                            selectGame(g.displayName)
                        }
                        .fixedSize()  // 칩 안 텍스트는 줄바꿈 없이 고유 너비 — 줄바꿈은 FlowLayout 이 칩 단위로 한다
                    }
                }
                .padding(.top, 12)
            }

            if gameChosen {
            // 금액 — 히어로 안에서 바로 고친다(별도 필드로 내려보내지 않는다).
            TextField("0", text: Binding(
                get: { amount },
                set: { newValue in
                    amount = newValue.filter(\.isNumber)
                    selectedPkg = nil; quantity = 1   // 직접 고치면 자동 곱 상태 해제
                }
            ))
            .textFieldStyle(.plain)
            .font(.pretendard(size: 34, weight: .black))
            .keyboardType(.numberPad)
            .padding(.top, 11)

            if !itemName.isEmpty {
                HStack(spacing: 6) {
                    Text(itemName).font(.pretendard(size: 13, weight: .bold)).lineLimit(1)
                    // 월정액/패스를 고르면 구독이 **자동으로 켜진다**. 그 상태가 접힌 '자세히' 안에만
                    // 있으면 켜진 줄 모르므로 히어로가 대신 말한다.
                    if isSubscription {
                        Text("정기").font(.pretendard(size: 10, weight: .black))
                            .foregroundStyle(accent.primary)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(accent.primary.opacity(0.14), in: RoundedRectangle(cornerRadius: 6))
                    }
                }
                .padding(.top, 3)
            }
            if let conv = currencyLine {
                Text(conv).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 3)
            }
            if let nudge = inlineNudge {
                Text("⚠ \(nudge)")
                    .font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(Color(hex: 0xFFD97706))
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 12)
            }
            }   // gameChosen
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.vertical, 18)
        .background(gameColor.opacity(0.07), in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(gameColor.opacity(0.18), lineWidth: 1))
        // 입력 도중 매 글자마다 뜨면 방해가 된다 → 손이 멈춘 뒤에만 평가한다.
        .task(id: amount) {
            try? await Task.sleep(nanoseconds: 450_000_000)
            guard !Task.isCancelled else { return }
            inlineNudge = store.overspendNudge(game: game, amount: Int64(amount) ?? 0, editingId: editing?.id)
        }
    }

    /// "원석 3,280 · 약 20뽑" — 재화 환산은 사는 순간에 보여야 의미가 있다.
    private var currencyLine: String? {
        guard let amountLabel = GameDataKt.currencyAmountOrNull(gameName: gameName, itemName: itemName) else { return nil }
        if let pulls = GameDataKt.currencyPullsOrNull(gameName: gameName, itemName: itemName) {
            return "\(amountLabel) · \(pulls)"
        }
        return amountLabel
    }

    /// 게임 변경 — 상품·수량은 게임에 묶인 값이라 함께 초기화하고, 플랫폼 기본값을 다시 고른다.
    /// 게임을 고른 뒤 아래로 펼쳐지는 카드의 등장 — 살짝 아래에서 밀려 올라오며 나타난다.
    ///
    /// ⚠️ **`transition` 안에 `.animation(_:)` 을 붙이지 말 것.** 카드마다 `staggerStep` 만큼
    /// 지연을 줘 순서를 만들려고 그렇게 했더니, 지연이 그대로 걸리지 않고 첫 카드가 1초쯤
    /// 늦게 내려왔다. 타이밍은 바깥 `withAnimation` 하나가 정하고, 여기서는 **모양만** 정한다.
    private var cardReveal: AnyTransition {
        .asymmetric(insertion: .opacity.combined(with: .offset(y: 14)), removal: .opacity)
    }

    private func selectGame(_ name: String) {
        let first = !gameChosen
        guard name != gameName || first else { return }
        // 값 변경은 **애니메이션 밖**에서 한다.
        //
        // 한때 이걸 통째로 `withAnimation` 에 넣었더니, 카드가 펼쳐지는 전환에 **카드 안쪽
        // 레이아웃 변화까지 딸려 들어갔다.** 상품 그리드가 게임에 따라 줄 수가 달라지는데,
        // 그 크기 변화가 전환에 실려 안쪽 버튼이 한참 뒤에 따라왔다.
        gameName = name
        selectedPkg = nil; quantity = 1; itemName = ""; isSubscription = false
        if editing == nil {
            chargePlatform = SpendingDefaults.shared.lastPlatform(spendings: store.spendings, gameName: name) ?? ""
        }
        // 애니메이션은 **카드가 생겼다 사라지는 것**에만 건다(cardReveal). 이미 펼쳐진 뒤
        // 게임만 바꾸는 경우엔 `gameChosen` 이 그대로라 아무것도 움직이지 않는다.
        if first { withAnimation(GLGMotion.standard()) { gameChosen = true } }
    }

    private var productCard: some View {
        sectionCard {
            let frequent = editing == nil
                ? SpendingDefaults.shared.frequentItems(spendings: store.spendings, gameName: gameName, limit: 3)
                : []
            let packages = GameData.shared.packagesFor(game: game)

            // 자주 사는 것 — 같은 게임에서 2회 이상 산 것만, 많이 산 순.
            // 기록이 없는 게임은 이 블록이 통째로 빠지고 예전처럼 전체 그리드가 바로 열린다
            // (빈도를 모르는데 임의로 셋을 고르면 그건 추천이 아니다).
            if !frequent.isEmpty {
                label("자주 사는 것")
                VStack(spacing: 7) {
                    ForEach(Array(frequent.enumerated()), id: \.offset) { _, f in
                        frequentRow(f, packages: packages)
                    }
                }
                .padding(.top, 10)
                Button { withAnimation(.easeInOut(duration: 0.2)) { showAllPackages.toggle() } } label: {
                    Text(showAllPackages ? "접기 ⌃" : "전체 상품 보기 ▾")
                        .font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity).padding(.top, 10)
                }
                .buttonStyle(.plain)
            } else {
                label("빠른 상품 선택")
                Text("선택하면 금액·재화명이 자동 입력돼요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
            }

            if frequent.isEmpty || showAllPackages {
                packageGrid(packages)
            }
            if let pkg = selectedPackage {
                quantityStepper(pkg).padding(.top, 14)
            }
            field("재화명", "결정석 60", $itemName).padding(.top, 12)
            // 환산이 깨지면 조용히 넘어가지 않고 이유를 말한다(막지는 않는다 — 환산은 편의다).
            if !itemName.isEmpty && currencyLine == nil {
                Text("재화 환산이 안 돼요 — '결정석 60'처럼 이름 뒤에 숫자를 붙이면 뽑기 수까지 계산해요")
                    .font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true).padding(.top, 6)
            }
        }
    }

    /// 자주 사는 것 한 줄 — 누르면 상품·금액·재화명이 한 번에 채워진다.
    private func frequentRow(_ f: FrequentItem, packages: [GamePackage]) -> some View {
        let sel = selectedPkg == f.itemName
        return Button {
            selectedPkg = f.itemName
            quantity = 1
            itemName = f.itemName
            amount = "\(f.amount)"
            // 상품 정의에 있으면 월정액 여부를 따라간다(없으면 사용자가 직접 적은 항목).
            if let pkg = packages.first(where: { $0.name == f.itemName }) { isSubscription = (pkg.bonus == "월정액") }
        } label: {
            HStack(spacing: 9) {
                Text(f.itemName).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1)
                Spacer(minLength: 6)
                Text(won(f.amount)).font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                Text("\(f.count)회").font(.pretendard(size: 10, weight: .black))
                    .foregroundStyle(accent.primary)
                    .padding(.horizontal, 7).padding(.vertical, 2)
                    .background(accent.primary.opacity(0.13), in: RoundedRectangle(cornerRadius: 6))
            }
            .padding(.horizontal, 13).padding(.vertical, 11)
            .background(sel ? accent.primary.opacity(0.10) : Color.white, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(sel ? accent.primary : Color.black.opacity(0.08), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func packageGrid(_ packages: [GamePackage]) -> some View {
        Group {
            let cols = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]
            LazyVGrid(columns: cols, spacing: 8) {
                ForEach(Array(packages.enumerated()), id: \.offset) { _, pkg in
                    let sel = selectedPkg == pkg.name
                    Button {
                        selectedPkg = pkg.name; quantity = 1; amount = "\(pkg.price)"; itemName = pkg.name; isSubscription = (pkg.bonus == "월정액")
                    } label: {
                        VStack(spacing: 3) {
                            Text(pkg.name).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                            HStack(spacing: 5) {
                                if let b = pkg.bonus { Text(b).font(.pretendard(size: 10, weight: .bold)).foregroundStyle(accent.primary) }
                                Text(won(pkg.price)).font(.pretendard(size: 11, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
                            }
                        }
                        .frame(maxWidth: .infinity).padding(.horizontal, 12).padding(.vertical, 9)
                        .background(sel ? accent.primary.opacity(0.1) : Color.white, in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(sel ? accent.primary : Color.black.opacity(0.08), lineWidth: 1))
                    }.buttonStyle(.plain)
                }
            }
            .padding(.top, 10)
        }
    }

    // 구매 횟수 스텝퍼 — 단가·재화 총량을 함께 보여줘 '몇 번 사서 얼마·재화 얼마인지' 검증 가능하게.
    private func quantityStepper(_ pkg: GamePackage) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("구매 횟수").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                if quantity > 1 {
                    Text("\(won(pkg.price)) × \(quantity) = \(won(pkg.price * Int64(quantity)))")
                        .font(.pretendard(size: 12, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
                    // 재화 양도 확인 — 상품명 끝의 개수 × 횟수 + 보너스 재화까지 총량 명시.
                    if let cur = GameDataKt.currencyAmountOrNull(gameName: gameName, itemName: itemName) {
                        Text("재화 총 \(cur)").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    }
                } else {
                    Text("한 번에 여러 번 샀다면 횟수를 올리세요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
            }
            Spacer(minLength: 8)
            HStack(spacing: 6) {
                stepperBtn("minus", enabled: quantity > 1) { setQuantity(quantity - 1) }
                Text("\(quantity)").font(.pretendard(size: 16, weight: .bold)).foregroundStyle(GLGColor.textPrimary).frame(minWidth: 28)
                stepperBtn("plus", enabled: quantity < 99) { setQuantity(quantity + 1) }
            }
        }
    }

    private func stepperBtn(_ symbol: String, enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 14, weight: .semibold))
                .foregroundStyle(enabled ? accent.primary : GLGColor.textSecondary.opacity(0.4))
                .frame(width: 34, height: 34)
                .background(enabled ? accent.primary.opacity(0.10) : Color(.systemGray6), in: Circle())
                .overlay(Circle().stroke(enabled ? accent.primary.opacity(0.5) : Color.black.opacity(0.06), lineWidth: 1))
        }
        .buttonStyle(.plain).disabled(!enabled)
    }

    private var dateCard: some View {
        sectionCard {
            Button { showDate = true } label: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("날짜").font(.pretendard(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                    HStack {
                        Text(DateUtil.shared.labelWithWeekday(millis: dateMillis)).foregroundStyle(GLGColor.textPrimary)
                        Spacer(); Image(systemName: "calendar").foregroundStyle(accent.primary)
                    }
                    .font(.pretendard(size: 15)).glgPillField()
                }
            }.buttonStyle(.plain)
        }
    }

    // ── 자세히 — 결제수단·플랫폼·태그·메모·구독을 하나로 접는다 ──
    //
    // 매번 바뀌는 값이 아니라 접어 두되, **접힌 채로도 현재 값 요약을 보여준다.**
    // 안 보이면 확인하려고 매번 펴게 되고, 그러면 접은 의미가 없다.
    // 기본값과 다른 값이 하나라도 있으면 요약을 강조색으로 — "뭔가 정해져 있다"가 보이게.
    private var detailsCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation(.easeInOut(duration: 0.2)) { detailsExpanded.toggle() } } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("자세히").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Text(detailSummary)
                            .font(.pretendard(size: 11.5))
                            .foregroundStyle(detailIsCustom ? accent.primary : GLGColor.textSecondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    Text(detailsExpanded ? "⌃" : "▾").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if detailsExpanded { detailFields.padding(.top, 14) }
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    /// 접힌 상태에서 보여줄 한 줄 — "카카오페이 · 구글플레이 · 태그 2".
    private var detailSummary: String {
        var parts: [String] = [paymentMethod.isEmpty ? "카드" : paymentMethod]
        if !chargePlatform.isEmpty { parts.append(chargePlatform) }
        let tagCount = selectedTags.count + customTags.split(whereSeparator: { $0 == "," || $0 == " " }).count
        parts.append(tagCount > 0 ? "태그 \(tagCount)" : "태그 없음")
        if isSubscription { parts.append("정기") }
        if !memo.isEmpty { parts.append("메모") }
        return parts.joined(separator: " · ")
    }

    /// 기본값에서 벗어난 값이 있는가 — 요약을 강조할지 정한다.
    private var detailIsCustom: Bool {
        !chargePlatform.isEmpty || !selectedTags.isEmpty || !customTags.isEmpty || !memo.isEmpty || isSubscription
    }

    private var detailFields: some View {
        VStack(alignment: .leading, spacing: 0) {
            label("결제 수단")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) { ForEach(GameData.shared.paymentMethods, id: \.self) { m in chip(m, paymentMethod == m) { paymentMethod = m } } }
            }
            .padding(.top, 8)
            label("충전 플랫폼 (선택)").padding(.top, 14)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) { ForEach(GameData.shared.chargePlatforms, id: \.self) { p in chip(p, chargePlatform == p) { chargePlatform = (chargePlatform == p ? "" : p) } } }
            }
            .padding(.top, 8)
            label("태그").padding(.top, 14)
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
                    Text("구독(월정액·패스)으로 기록").font(.pretendard(size: 15, weight: .medium))
                    Text("정기 결제 항목으로 분류됩니다").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
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
            // 흐린 버튼만 두지 않는다 — 왜 못 누르는지 버튼이 직접 말한다.
            GLGButton(title: saveTitle) { attemptSave() }
                .opacity(canSave ? 1 : 0.5).disabled(!canSave)
        }
        .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 6)
        .background(Color.white)
    }

    // ── 로직 ──
    private var selectedPackage: GamePackage? {
        guard let name = selectedPkg else { return nil }
        return GameData.shared.packagesFor(game: game).first { $0.name == name }
    }

    // 구매 횟수 변경 — 선택된 상품 기준으로 금액·재화명을 N배로 다시 계산.
    private func setQuantity(_ q: Int) {
        let qty = min(max(q, 1), 99)
        quantity = qty
        if let pkg = selectedPackage {
            amount = "\(pkg.price * Int64(qty))"
            itemName = qty > 1 ? "\(pkg.name) ×\(qty)" : pkg.name
        }
    }

    // 수정 진입 시 항목명 끝 "×N" 에서 구매 횟수 복원. 없으면 1.
    private func detectQuantity(_ name: String) -> Int {
        guard let r = name.range(of: "×\\s*\\d+\\s*$", options: .regularExpression) else { return 1 }
        let digits = name[r].filter(\.isNumber)
        return min(max(Int(digits) ?? 1, 1), 99)
    }

    // 항목명에서 끝의 "×N" 을 떼어낸 기본 상품명.
    private func stripMult(_ name: String) -> String {
        if let r = name.range(of: "\\s*×\\s*\\d+\\s*$", options: .regularExpression) {
            return String(name[..<r.lowerBound]).trimmingCharacters(in: .whitespaces)
        }
        return name.trimmingCharacters(in: .whitespaces)
    }

    private func prefill() {
        guard !didInit else { return }; didInit = true
        if let e = editing {
            gameName = e.gameName; amount = e.amount > 0 ? "\(e.amount)" : ""; dateMillis = e.dateMillis
            paymentMethod = e.paymentMethod.isEmpty ? "카드" : e.paymentMethod
            chargePlatform = e.chargePlatform
            itemName = e.itemName; memo = e.memo; selectedTags = e.tags; isSubscription = e.isSubscription
            // 저장된 항목명("창세의 결정 300 ×3")에서 상품·구매 횟수 복원 → 스텝퍼 노출.
            quantity = detectQuantity(e.itemName)
            let base = stripMult(e.itemName)
            if GameData.shared.packagesFor(game: GameData.shared.byName(name: e.gameName)).contains(where: { $0.name == base }) {
                selectedPkg = base
            }
            // 수정은 기록된 게임이 이미 있다 → 곧바로 전체 폼을 보여준다.
            gameChosen = true
            // 수정은 **무엇을 고치러 왔는지 모른다** → 자세히를 펼친 채로 시작한다.
            detailsExpanded = true
        } else {
            dateMillis = nowMs()
            // 스마트 기본값 — 앱이 아는 값은 묻지 않는다(추론은 공유 SpendingDefaults 가 한다).
            // **게임은 넣지 않는다**(gameChosen 주석 참고). 충전 플랫폼도 게임이 정해져야 고를 수 있어
            // selectGame 에서 채운다. 여기서는 게임과 무관한 결제수단만.
            // **기록이 없으면 추론하지 않는다** — 그때는 기존 기본값(카드) 그대로.
            if let p = SpendingDefaults.shared.topPaymentMethod(spendings: store.spendings, window: SpendingDefaults.shared.RECENT_WINDOW) {
                paymentMethod = p
            }
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
    private func label(_ t: String) -> some View { Text(t).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
    private func field(_ label: String, _ ph: String, _ text: Binding<String>, number: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            if !label.isEmpty { Text(label).font(.pretendard(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary) }
            TextField(ph, text: text)
                .textFieldStyle(.plain)
                .font(.pretendard(size: 15))
                .keyboardType(number ? .numberPad : .default)
                .glgPillField()
                .onChange(of: text.wrappedValue) { _, newValue in if number { text.wrappedValue = newValue.filter(\.isNumber) } }
        }
    }
    private func chip(_ label: String, _ selected: Bool, _ action: @escaping () -> Void) -> some View {
        GLGChip(label: label, selected: selected, action: action)
    }
}
