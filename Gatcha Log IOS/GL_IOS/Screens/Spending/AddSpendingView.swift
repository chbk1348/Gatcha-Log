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
    @State private var selectedPkg: String? = nil
    // 한 번에 같은 상품을 여러 번 산 경우 — 횟수만큼 금액·재화를 곱해 한 건으로 기록.
    @State private var quantity: Int = 1
    @State private var showDate = false
    @State private var nudgeMsg: String? = nil
    /// 한 번만 저장한다 — 시트가 닫히는 동안 버튼이 살아 있어, 빠르게 두 번 누르면 두 건이 들어갔다.
    @State private var saved = false
    /// 처음 채운 입력의 지문 — 바뀌었으면 쓸어 닫기를 막고 취소에서 한 번 묻는다(입력이 경고 없이 날아갔다).
    @State private var initialFingerprint: String? = nil
    @State private var confirmDiscard = false
    @State private var didInit = false
    /// '자주 사는 것' 아래 전체 상품 그리드를 폈는가.
    @State private var showAllPackages = false
    /// '자세히'(결제·플랫폼·태그·메모·구독)를 폈는가. **수정 진입은 펼친 채 시작**한다.
    @State private var detailsExpanded = false
    /// 히어로에 바로 뜨는 과소비 경고 — 저장을 누른 뒤가 아니라 금액이 정해지는 순간에 알린다.
    @State private var inlineNudge: String? = nil
    /// 자주 사는 것(productCard) 캐시 — gameName 이 바뀔 때만 다시 계산한다.
    @State private var frequent: [FrequentItem] = []
    /// 사용자가 게임을 **직접 골랐는가.**
    ///
    /// 추가 진입은 게임을 미리 정해두지 않는다. 마지막에 기록한 게임을 자동으로 넣으면
    /// 다른 게임을 기록하러 온 사람이 **못 알아채고 엉뚱한 게임에 저장**한다 —
    /// 지출은 게임별 집계·예산의 기준이라 그 오기록은 나중에 찾기 어렵다.
    /// 결제수단·플랫폼과 달리 게임은 "틀려도 티가 안 나는" 값이 아니다.
    @State private var gameChosen = false

    private var game: Game { GameData.shared.byName(name: gameName) }
    private var amountValid: Bool { (Int64(amount) ?? 0) > 0 }
    /// 금액 입력칸에 **보이는 글자**(쉼표 포함). 저장값 [amount] 은 숫자만 남긴다.
    ///
    /// 계산 바인딩(`get` 에서 쉼표를 넣는 방식)으로 하면 쉼표가 **한 글자 늦게** 붙는다 —
    /// 필드가 제 글자를 먼저 그리고 그 뒤에야 우리 값이 돌아오기 때문이다(2026-09-21 지적).
    /// 필드가 쥐는 상태를 따로 두고, 바뀔 때마다 그 자리에서 다시 써 넣는다.
    @State private var amountText: String = ""
    @FocusState private var amountFocused: Bool

    /// 금액을 **두 벌 다** 맞춘다 — 저장값(숫자)과 보이는 글자(쉼표).
    private func setAmount(_ value: Int64) {
        amount = value > 0 ? "\(value)" : ""
        amountText = value > 0 ? grouped(value) : ""
    }
    /// 상한도 버튼에서 막는다(Android 와 같이) — 넘으면 store 가 거절해 누를 때마다 토스트만 뜬다.
    private var amountTooBig: Bool { (Int64(amount) ?? 0) > Spending.companion.MAX_AMOUNT }
    private var canSave: Bool { gameChosen && amountValid && !amountTooBig }
    /// 못 누르는 이유를 버튼이 직접 말한다 — 게임 먼저, 그다음 금액.
    private var saveTitle: String {
        if !gameChosen { return "게임을 선택하세요" }
        if amountTooBig { return "금액이 너무 커요" }
        if !amountValid { return "금액을 입력하세요" }
        return editing == nil ? "저장하기" : "수정하기"
    }

    private var form: some View {
        ScrollView {
            // 카드 없이 화면 폭 섹션 + 10 띠(지출 상세 · 마이페이지와 같은 규격). 히어로만 좌우 16 을 둔다.
            VStack(spacing: 0) {
                // 미선택 — 고르기 전에는 금액을 받지 않는다. 히어로 대신 게임 목록이 화면을 연다.
                if gameChosen {
                    amountHero
                } else {
                    gamePickSection
                }
                // 게임을 고르기 전에는 나머지를 띄우지 않는다 — 상품 목록·기본값이 전부 게임에 묶여 있어
                // 미선택 상태로 보여주면 어느 게임 것인지 알 수 없는 화면이 된다.
                if gameChosen {
                    productCard.transition(cardReveal)
                    dateCard.transition(cardReveal)
                    detailsCard.transition(cardReveal)
                }
            }
            .glgReadableWidth(640)
        }
        .background(Color.white)
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(editing == nil ? "지출 추가" : "지출 수정")
        .navigationBarTitleDisplayMode(.inline)
        // iOS 는 저장을 헤더 시스템 버튼으로(9/30 사용자 지정) — 닫기(X) · 저장/수정. 못 누르면 시스템 비활성.
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button { requestDismiss() } label: { Image(systemName: "xmark") }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(editing == nil ? "저장" : "수정") { attemptSave() }.disabled(!canSave || saved)
            }
        }
        // 가운데 알림창(9/30) — 액션 시트는 iPad · Duo 에서 누른 자리 옆 팝오버로 떠 위치가 기기마다 달랐다.
        // 버튼은 Android GlgDialog 와 같은 「계속 입력」 · 「버리기」.
        .alert("입력한 내용을 버릴까요?", isPresented: $confirmDiscard) {
            Button("계속 입력", role: .cancel) {}.glgAlertTint()
            Button("버리기", role: .destructive) { onClose() }
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
        .onAppear {
            prefill()
            if initialFingerprint == nil { initialFingerprint = fingerprint }
        }
        .interactiveDismissDisabled(isDirty)
        // 날짜 선택 — Android `GlgDatePickerDialog` 와 같은 가운데 모달(시스템 그래픽 달력 시트 대신).
        .overlay {
            if showDate {
                GLGDatePickerDialog(
                    initialMillis: dateMillis,
                    onDismiss: { showDate = false },
                    onConfirm: { dateMillis = $0; showDate = false }
                )
                .transition(.opacity)
            }
        }
        .animation(GLGMotion.standard(), value: showDate)
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
    /// 지출 입력 게임 목록 — 내 게임이 앞, 나머지는 뒤. 내 게임이 비어 있으면 전부 앞(GameData.pickerGames 파리티).
    private var pickerMine: [Game] { store.myGames.isEmpty ? GLGGames.all : GLGGames.all.filter { store.myGames.contains($0.key) } }
    private var pickerOthers: [Game] { store.myGames.isEmpty ? [] : GLGGames.all.filter { !store.myGames.contains($0.key) } }

    private var amountHero: some View {
        let gameColor = gameChosen ? Color(argb64: game.color) : GLGColor.textSecondary
        return VStack(alignment: .leading, spacing: 0) {
            if gameChosen {
                // 게임 — 칩 줄을 늘어놓지 않고 메뉴로 접었다(히어로가 금액을 가리면 안 된다).
                Menu {
                    ForEach(pickerMine + pickerOthers, id: \.key) { g in
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
            }

            if gameChosen {
            // 금액 — 히어로 안에서 바로 고친다(별도 필드로 내려보내지 않는다).
            //
            // 치는 동안에도 **읽는 모양 그대로** 보여 준다 — 세 자리 쉼표를 넣고 뒤에 「원」을
            // 붙인다(2026-09-21 지시). 저장하는 값은 숫자뿐이라, 넣을 때 쉼표를 넣고 받을 때
            // 숫자만 거른다.
            //
            // 카드를 걷고 나니 **입력하는 곳인지** 안 읽혔다 — GLDS 입력필드 모양으로 둔다
            // (라벨 · 채운 면 #F5F8F8 · 모서리 14 · 입력 중 흰 면 + 강조색 1.5 테두리). 큰 숫자라 높이만 내용에 맞춘다.
            // 칸 어디를 눌러도 입력이 시작된다(Android 와 같다).
            GldsFieldLabel(text: "금액").padding(.top, 11)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                TextField("", text: $amountText, prompt: Text("0").foregroundStyle(Color(hex: 0xFFA7B1AE)))
                    .textFieldStyle(.plain)
                    .font(.pretendard(size: 30, weight: .black))
                    .keyboardType(.numberPad)
                    .focused($amountFocused)
                    .tint(accent.primary)
                    .fixedSize(horizontal: true, vertical: false)
                    .onChange(of: amountText) { _, newValue in
                        // 11자리(999억)까지만 — 더 긴 붙여넣기는 조용히 0 이 됐다. 상한(100억)은 저장할 때 VM 이 막는다.
                        let digits = String(newValue.filter(\.isNumber).prefix(11))
                        if digits != amount {
                            amount = digits
                            selectedPkg = nil; quantity = 1   // 직접 고치면 자동 곱 상태 해제
                        }
                        // 친 그 자리에서 쉼표를 다시 박는다.
                        let shown = digits.isEmpty ? "" : grouped(Int64(digits) ?? 0)
                        if shown != newValue { amountText = shown }
                    }
                if !amount.isEmpty {
                    Text("원").font(.pretendard(size: 22, weight: .black))
                        .foregroundStyle(GLGColor.textSecondary)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(amountFocused ? Color.white : Color(hex: 0xFFF5F8F8), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(amountFocused ? accent.primary : .clear, lineWidth: 1.5))
            .contentShape(Rectangle())
            .onTapGesture { amountFocused = true }
            .animation(.easeOut(duration: 0.15), value: amountFocused)
            // 칸과 아래 안내 · 상품명 · 재화 줄 사이.
            .padding(.bottom, 6)

            // 비어 있으면 **무엇을 넣어야 하는지** 한 줄로 말한다. 자리표시자 「0」만으로는
            // 이미 0 원을 적어 둔 것처럼 읽힌다(2026-09-21 지시).
            if amount.isEmpty {
                Text("금액을 입력해주세요")
                    .font(.pretendard(size: 12))
                    .foregroundStyle(GLGColor.textSecondary)
                    .padding(.top, 3)
            }

            if !itemName.isEmpty {
                HStack(spacing: 6) {
                    Text(itemName).font(.pretendard(size: 13, weight: .bold)).lineLimit(1)
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
        // 카드 없이 화면 폭 그대로(좌우 20) — 아래 섹션과 10 띠로 갈린다. 게임색은 알약 · 환산 글자가 맡는다.
        .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 20)
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

    /// 게임 고르기 — 지출 추가의 첫 화면. 카드 없이 화면 폭 목록(헤어라인 구분)이다. Compose `GamePickSection` 과 패리티.
    ///
    /// **리스트형**(27.50.0) — 칩을 줄바꿈으로 늘어놓으면 이름 길이가 제각각이라 줄이 들쭉날쭉했다.
    /// 가로 스크롤도 쓰지 않는다 — 접어 두면 화면 밖 게임은 있는 줄도 모른다.
    /// 예전엔 게임마다 낱개 카드였지만 카드형을 걷으며 지출 목록과 같은 헤어라인 목록으로 바꿨다.
    /// 내 게임(온보딩 ② · 설정 ▸ 내 게임)이 위, 나머지는 「다른 게임」 아래 — 기록할 수 있는 게임은 줄이지 않는다.
    private var gamePickSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("어느 게임인가요?")
                .font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                .padding(.horizontal, 20)
            Text("게임을 선택해주세요")
                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                .padding(.horizontal, 20).padding(.top, 3).padding(.bottom, 8)
            gamePickList(pickerMine)
            if !pickerOthers.isEmpty {
                Text("다른 게임").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 2)
                gamePickList(pickerOthers)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 12).padding(.bottom, 20)
    }

    private func gamePickList(_ games: [Game]) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(games.enumerated()), id: \.element.key) { i, g in
                if i > 0 { Color(hex: 0xFFEEF0F2).frame(height: 1).padding(.horizontal, 20) }
                Button { selectGame(g.displayName) } label: { gameSelectRow(g) }
                    .buttonStyle(.plain)
            }
        }
    }

    /// 게임 선택 한 줄 — [게임색 배지 · 이름 · 화살표]. 줄 전체가 눌린다.
    @ViewBuilder private func gameSelectRow(_ g: Game) -> some View {
        let c = Color(argb64: g.color)
        HStack(spacing: 0) {
            // 배지는 **영어 약칭**(GI · HSR · ZZZ …) — 지출 목록 행과 같은 값이다.
            // 한국어 약칭은 길이가 제각각이라 36 칸에서 두 줄로 접혔다.
            // 배지 색은 **게임 대표색** — 강조색을 쓰면 테마에 따라 전부 같은 색이 되어 게임 구분이 사라진다.
            Text(g.abbr)
                .font(.pretendard(size: 11, weight: .black))
                .foregroundStyle(c)
                .multilineTextAlignment(.center)
                .lineLimit(1)
                .padding(.horizontal, 2)
                .frame(width: 36, height: 36)
                .background(c.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            Text(g.displayName)
                .font(.pretendard(size: 15, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .padding(.leading, 12)
            Spacer(minLength: 6)
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(GLGColor.textSecondary.opacity(0.6))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
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
        selectedPkg = nil; quantity = 1; itemName = ""
        if editing == nil {
            chargePlatform = SpendingDefaults.shared.lastPlatform(spendings: store.spendings, gameName: name) ?? ""
        }
        // 애니메이션은 **카드가 생겼다 사라지는 것**에만 건다(cardReveal). 이미 펼쳐진 뒤
        // 게임만 바꾸는 경우엔 `gameChosen` 이 그대로라 아무것도 움직이지 않는다.
        if first { withAnimation(GLGMotion.standard()) { gameChosen = true } }
    }

    private var productCard: some View {
        sectionCard {
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
        // 자주 사는 것은 게임이 바뀔 때만 다시 센다 — body 안에서 세면 글자 하나 칠 때마다 전체 지출을 훑었다.
        // initial: true — 첫 그리기 전에 채워 전체 그리드가 한 프레임 떴다 접히지 않게(.task 는 한 박자 늦다).
        .onChange(of: gameName, initial: true) {
            frequent = editing == nil
                ? SpendingDefaults.shared.frequentItems(spendings: store.spendings, gameName: gameName, limit: 3)
                : []
        }
    }

    /// 자주 사는 것 한 줄 — 누르면 상품·금액·재화명이 한 번에 채워진다.
    private func frequentRow(_ f: FrequentItem, packages: [GamePackage]) -> some View {
        let sel = selectedPkg == f.itemName
        return Button {
            selectedPkg = f.itemName
            quantity = 1
            itemName = f.itemName
            setAmount(f.amount)
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
                        selectedPkg = pkg.name; quantity = 1; setAmount(pkg.price); itemName = pkg.name
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
            // 누르는 필드 — 모양은 GLDS 입력필드, 탭하면 날짜 시트(Android GldsTextField(onClick) 와 같이).
            GldsTextField(label: "날짜", placeholder: "", text: .constant(DateUtil.shared.labelWithWeekday(millis: dateMillis)),
                         trailingSystemImage: "calendar", onTap: { showDate = true })
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
        .formSection()
    }

    /// 접힌 상태에서 보여줄 한 줄 — "카카오페이 · 구글플레이 · 태그 2".
    private var detailSummary: String {
        var parts: [String] = [paymentMethod.isEmpty ? "카드" : paymentMethod]
        if !chargePlatform.isEmpty { parts.append(chargePlatform) }
        let tagCount = selectedTags.count + customTags.split(whereSeparator: { $0 == "," || $0 == " " }).count
        parts.append(tagCount > 0 ? "태그 \(tagCount)" : "태그 없음")
        if !memo.isEmpty { parts.append("메모") }
        return parts.joined(separator: " · ")
    }

    /// 기본값에서 벗어난 값이 있는가 — 요약을 강조할지 정한다.
    private var detailIsCustom: Bool {
        !chargePlatform.isEmpty || !selectedTags.isEmpty || !customTags.isEmpty || !memo.isEmpty
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
        }
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
            setAmount(pkg.price * Int64(qty))
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
            gameName = e.gameName; setAmount(e.amount); dateMillis = e.dateMillis
            paymentMethod = e.paymentMethod.isEmpty ? "카드" : e.paymentMethod
            chargePlatform = e.chargePlatform
            itemName = e.itemName; memo = e.memo; selectedTags = e.tags
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

    /// 입력 지문 — 이 값이 처음과 다르면 "고친 게 있다".
    private var fingerprint: String {
        [gameName, amount, "\(dateMillis)", paymentMethod, chargePlatform, itemName, memo, customTags,
         selectedTags.joined(separator: ",")].joined(separator: "|")
    }
    private var isDirty: Bool { initialFingerprint != nil && fingerprint != initialFingerprint }
    private func requestDismiss() { if isDirty { confirmDiscard = true } else { onClose() } }

    private func doSave() {
        guard !saved else { return }
        saved = true
        nudgeMsg = nil
        let parsed = Int64(amount) ?? 0
        let extra = customTags.components(separatedBy: CharacterSet(charactersIn: ", "))
        var tags: [String] = []
        for t in (selectedTags + extra) { let tt = t.trimmingCharacters(in: .whitespaces); if !tt.isEmpty && !tags.contains(tt) { tags.append(tt) } }
        // 거절(금액 상한 초과 등)이면 폼을 닫지 않는다 — 닫으면 입력한 게 통째로 사라졌다.
        guard store.saveSpending(editingId: editing?.id, gameName: gameName, amount: parsed, dateMillis: dateMillis,
                                 paymentMethod: paymentMethod, chargePlatform: chargePlatform, itemName: itemName, memo: memo, tags: tags)
        else { saved = false; return }
        onClose()
    }

    // ── 공통 ──
    private func sectionCard<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) { content() }.formSection()
    }
    private func label(_ t: String) -> some View { Text(t).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
    private func field(_ label: String, _ ph: String, _ text: Binding<String>) -> some View {
        GldsTextField(label: label.isEmpty ? nil : label, placeholder: ph, text: text)
    }
    private func chip(_ label: String, _ selected: Bool, _ action: @escaping () -> Void) -> some View {
        GldsChip(label: label, selected: selected, action: action)
    }
}

/// 입력 섹션 — 카드 없이 화면 폭 그대로, 위에 10 띠를 얹어 앞 묶음과 가른다.
/// 좌우 20 · 위 22 · 아래 20(지출 상세 섹션과 같은 규격). 띠가 섹션 안에 있어 펼쳐 내려올 때 함께 나타난다.
private extension View {
    func formSection() -> some View {
        VStack(spacing: 0) {
            Color(hex: 0xFFF2F4F6).frame(height: 10).frame(maxWidth: .infinity)
            self.padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 20)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// 커스텀 달력 날짜 선택 — Android `GlgDatePickerDialog`(GlgDialog 규격) 와 같은 모양 · 동작.
/// 딤 + 가운데 흰 카드(모서리 24 · 안쪽 22) · 제목 17 · 월 이동 원형 버튼 36 · 요일(일 빨강 · 토 파랑) · 선택일 강조색 원.
/// 확인하면 고른 날 **낮 12시**로 저장한다(Android 와 같다 — 시간대 경계에서 날짜가 밀리지 않게).
struct GLGDatePickerDialog: View {
    let initialMillis: Int64
    let onDismiss: () -> Void
    let onConfirm: (Int64) -> Void

    @Environment(\.glgAccent) private var accent
    @State private var viewMonth: Date
    @State private var selected: DateComponents

    private static var cal: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.locale = Locale(identifier: "ko_KR")
        return c
    }

    init(initialMillis: Int64, onDismiss: @escaping () -> Void, onConfirm: @escaping (Int64) -> Void) {
        self.initialMillis = initialMillis
        self.onDismiss = onDismiss
        self.onConfirm = onConfirm
        let d = Date(timeIntervalSince1970: Double(initialMillis) / 1000)
        let c = Self.cal
        _selected = State(initialValue: c.dateComponents([.year, .month, .day], from: d))
        _viewMonth = State(initialValue: c.date(from: c.dateComponents([.year, .month], from: d)) ?? d)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.32).ignoresSafeArea()
                .onTapGesture { onDismiss() }
            VStack(alignment: .leading, spacing: 0) {
                Text("날짜 선택").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .padding(.bottom, 16)
                monthHeader.padding(.bottom, 12)
                weekdayRow.padding(.bottom, 4)
                dayGrid
                GeometryReader { geo in
                    let w = geo.size.width - 10
                    HStack(spacing: 10) {
                        GldsButton(title: "취소", variant: .secondary) { onDismiss() }
                            .frame(width: w / 2.4)
                        GldsButton(title: "확인") { confirm() }
                            .frame(width: w * 1.4 / 2.4)
                    }
                }
                .frame(height: 44)
                .padding(.top, 20)
            }
            .padding(22)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(GLGColor.divider, lineWidth: 1))
            .shadow(color: .black.opacity(0.18), radius: 24, y: 8)
            .padding(24)
            .frame(maxWidth: 480)
        }
    }

    private var monthHeader: some View {
        let c = Self.cal.dateComponents([.year, .month], from: viewMonth)
        return HStack {
            arrow("chevron.left", "이전 달") { shift(-1) }
            Spacer()
            // String(...) 으로 넘긴다 — Text 보간은 숫자에 세 자리 쉼표를 찍어 「2,026년」이 됐다.
            Text(verbatim: "\(String(c.year ?? 0))년 \(String(c.month ?? 0))월").font(.pretendard(size: 15, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
            Spacer()
            arrow("chevron.right", "다음 달") { shift(1) }
        }
    }

    private func arrow(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 14, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                .frame(width: 36, height: 36)
                .background(Color(hex: 0xFFF2F2F6), in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    /// 요일 · 날짜는 **같은 7열 격자**에 올린다 — 칸마다 `aspectRatio` 로 크기를 맡겼더니
    /// 글자 칸과 빈 칸의 폭이 달라져 요일과 날짜 열이 어긋났다(10/1 지적). 칸 높이는 40 고정.
    private static let gridCols = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)

    private var weekdayRow: some View {
        LazyVGrid(columns: Self.gridCols, spacing: 0) {
            ForEach(Array(["일", "월", "화", "수", "목", "금", "토"].enumerated()), id: \.offset) { i, d in
                Text(d).font(.pretendard(size: 11, weight: .semibold))
                    .foregroundStyle(i == 0 ? Color(hex: 0xFFE5484D) : i == 6 ? Color(hex: 0xFF4F8EF7) : GLGColor.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: 20)
            }
        }
    }

    private var dayGrid: some View {
        let c = Self.cal
        let firstDow = c.component(.weekday, from: viewMonth) - 1          // 0 = 일
        let days = c.range(of: .day, in: .month, for: viewMonth)?.count ?? 30
        let ym = c.dateComponents([.year, .month], from: viewMonth)
        let cells = (firstDow + days + 6) / 7 * 7
        return LazyVGrid(columns: Self.gridCols, spacing: 0) {
            ForEach(0..<cells, id: \.self) { idx in
                let day = idx - firstDow + 1
                if day >= 1 && day <= days {
                    let isSel = selected.year == ym.year && selected.month == ym.month && selected.day == day
                    Button {
                        selected = DateComponents(year: ym.year, month: ym.month, day: day)
                    } label: {
                        Text("\(day)")
                            .font(.pretendard(size: 14, weight: isSel ? .bold : .regular))
                            .foregroundStyle(isSel ? Color.white : GLGColor.textPrimary)
                            .frame(width: 36, height: 36)
                            .background(isSel ? accent.primary : Color.clear, in: Circle())
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                } else {
                    Color.clear.frame(maxWidth: .infinity, minHeight: 40)
                }
            }
        }
    }

    private func shift(_ delta: Int) {
        if let d = Self.cal.date(byAdding: .month, value: delta, to: viewMonth) { viewMonth = d }
    }

    private func confirm() {
        var comps = selected
        comps.hour = 12; comps.minute = 0; comps.second = 0
        let d = Self.cal.date(from: comps) ?? Date(timeIntervalSince1970: Double(initialMillis) / 1000)
        onConfirm(Int64(d.timeIntervalSince1970 * 1000))
    }
}
