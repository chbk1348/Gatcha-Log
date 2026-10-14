import SwiftUI
import Shared

// 지출 추가/수정 폼 — 게임·빠른상품·금액·재화명·날짜·결제수단·태그·메모·구독 + 과소비 넛지.
// (Compose AddSpendingModal 대응) Spending 생성은 Kotlin saveSpending.
//
// **화면 전체를 덮는 페이지다**(10/13 — 예전엔 시트였다). ContentView 가 탭 화면 위에 오른쪽에서 밀어 넣고,
// 여기는 자기 머리(뒤로 · 제목 · 저장)를 갖는다. 어느 탭의 스택에도 들어가지 않으므로 뒤 화면은 그대로 남는다.
// 나가는 길은 셋 — 왼쪽 위 뒤로, 왼쪽 끝에서 쓸기, 저장. 고친 것이 있으면 앞의 둘은 한 번 묻는다.
struct AddSpendingView: View {
    var store: SpendingStore
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
    /// 픽업 태그(10/13) — 「지금 픽업 중」에서 고른 이름들. 저장할 때 표식을 붙여 태그 목록 맨 뒤에 들어간다(PickupTag).
    @State private var selectedPickups: [String] = []
    @State private var selectedPkg: String? = nil
    // 한 번에 같은 상품을 여러 번 산 경우 — 횟수만큼 금액·재화를 곱해 한 건으로 기록.
    @State private var quantity: Int = 1
    @State private var showDate = false
    @State private var nudgeMsg: String? = nil
    /// 한 번만 저장한다 — 페이지가 닫히는 동안 버튼이 살아 있어, 빠르게 두 번 누르면 두 건이 들어갔다.
    @State private var saved = false
    /// 처음 채운 입력의 지문 — 바뀌었으면 뒤로 · 쓸어 나가기에서 한 번 묻는다(입력이 경고 없이 날아갔다).
    @State private var initialFingerprint: String? = nil
    @State private var confirmDiscard = false
    @State private var didInit = false
    /// 왼쪽 끝에서 쓸어 나가는 중에 페이지가 손가락을 따라 밀린 거리.
    @State private var backDrag: CGFloat = 0
    /// 재화 목록이 갈린 횟수 — 어드민 값을 새로 받으면 올려서 상품 칸을 다시 그린다(10/13).
    @State private var catalogRev = 0
    /// '자세히'(결제·플랫폼·태그·메모·구독)를 폈는가. **수정 진입은 펼친 채 시작**한다.
    @State private var detailsExpanded = false
    /// 히어로에 바로 뜨는 과소비 경고(간결한 한 줄) — 저장을 누른 뒤가 아니라 금액이 정해지는 순간에 알린다. 경고할 것이 없으면 nil.
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

    /// 금액 굴림(아래 setAmount) — 도는 동안 보이는 글자는 **중간 값**이라 입력으로 받지 않는다(onChange 가 비켜 선다).
    @State private var amountRolling = false
    @State private var rollTask: Task<Void, Never>? = nil

    /// 금액을 **두 벌 다** 맞춘다 — 저장값(숫자)과 보이는 글자(쉼표).
    ///
    /// `roll` 이면 보이는 숫자가 이전 금액에서 새 금액까지 **0.45초 동안 굴러간다**(끝에서 감속, 100원 단위 —
    /// 예산 화면의 금액 버튼과 같은 움직임, 10/13 지시). 상품을 고르거나 횟수를 바꿔 금액이 한 번에 정해질 때 쓴다.
    /// 저장값은 굴리지 않고 바로 새 금액이다 — 아래 정보 줄 · 요약 · 경고가 중간 값으로 흔들리지 않는다.
    private func setAmount(_ value: Int64, roll: Bool = false) {
        let from = Int64(amount) ?? 0
        amount = value > 0 ? "\(value)" : ""
        rollTask?.cancel()
        guard roll, value > 0, from != value else {
            amountRolling = false
            amountText = value > 0 ? grouped(value) : ""
            return
        }
        amountRolling = true
        rollTask = Task { @MainActor in
            let start = Date()
            while !Task.isCancelled {
                let p = min(1, Date().timeIntervalSince(start) / 0.45)
                let e = 1 - pow(1 - p, 3)
                let v = Int64((Double(from) + Double(value - from) * e) / 100) * 100
                amountText = p < 1 ? grouped(v) : grouped(value)
                if p >= 1 { amountRolling = false; break }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
        }
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

    /// 폼의 스크롤 위치 — 「자세히」를 접고 펼 때 화면이 내용의 끝을 따라가게 한다(toggleDetails).
    @State private var formScroll = ScrollPosition()

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
                // 섹션들은 제자리에 바로 선다(10/13 지시 — 예전엔 살짝 밀려 올라오며 나타났다).
                // 이 페이지에 남긴 움직임은 셋이다: 「자세히」 접고 펴기, 금액 숫자 굴림, 금액 카드의 주의 표시(색 · 흔들림 · 깜빡임).
                // 과소비 경고는 카드 안에서만 바뀐다 — 화면 전체에 애니메이션을 걸면 같은 순간의 고른 표시까지 늦게 번졌다.
                // 페이지가 밀려 들어오고 나가는 전환은 이 바깥(ContentView)이라 그대로다.
                if gameChosen {
                    // 「지금 픽업 중」은 재화 선택 **위**다(10/13 지시) — 누구를 위해 쓰는지를 먼저 고른다.
                    pickupCard
                    productCard
                    dateCard
                    detailsCard
                }
            }
            .glgReadableWidth(640)
        }
        .scrollPosition($formScroll)
        .background(Color.white)
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(editing == nil ? "지출 추가" : "지출 수정")
        .navigationBarTitleDisplayMode(.inline)
        // 왼쪽 끝에서 오른쪽으로 쓸면 뒤로 간다 — 밀려 들어온 페이지라 시스템 뒤로 쓸기와 같은 손짓을 받는다.
        .overlay(alignment: .leading) { backSwipeStrip }
        // iOS 는 저장을 헤더 시스템 버튼으로(9/30 사용자 지정) — 뒤로(‹) · 저장/수정. 못 누르면 시스템 비활성.
        // 왼쪽은 ✕ 가 아니라 **뒤로**다(10/13) — 시트가 아니라 앞 화면에서 밀려 들어온 하위 페이지다.
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button { requestDismiss() } label: { Image(systemName: "chevron.backward") }
                    .accessibilityLabel("뒤로")
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
        // 입력 화면에서는 하단 탭바 · '추가' 버튼이 보이지 않는다 — 폼을 채우다 탭을 눌러 나가면 입력이 날아간다.
        // (Android 는 루트를 스왑해 애초에 탭바가 없다.)
        //
        // 숨김은 **여기서 선언하지 않는다.** ContentView 가 상태(spendingEditor)로 들고 있다 —
        // 이 페이지가 화면 전체를 덮고, 그 아래 탭바도 같은 상태로 감췄다가 페이지를 벗어나면 다시 보인다.
    }

    /// 왼쪽 끝 띠 — 여기서 시작한 가로 끌기만 「뒤로」로 받는다(가로 칩 줄 · 세로 스크롤과 겹치지 않게).
    private var backSwipeStrip: some View {
        Color.clear
            .frame(width: 16)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 8, coordinateSpace: .global)
                    .onChanged { v in backDrag = max(0, v.translation.width) }
                    .onEnded { v in
                        let leave = v.translation.width > 100 || v.predictedEndTranslation.width > 240
                        // 고친 것이 없으면 그대로 나간다 — 남은 거리는 ContentView 의 전환이 밀어낸다.
                        if leave && !isDirty { onClose(); return }
                        withAnimation(GLGMotion.standard()) { backDrag = 0 }
                        if leave { confirmDiscard = true }
                    }
            )
            .accessibilityHidden(true)
    }

    var body: some View {
        NavigationStack { form }
        // 페이지가 화면 끝까지 흰 면으로 덮는다 — 뒤의 탭 화면 · 탭바가 비치지 않게.
        .background(Color.white.ignoresSafeArea())
        .offset(x: backDrag)
        // 뒤 화면이 그대로 살아 있으므로, 읽어 주는 범위를 이 페이지로 묶는다.
        .accessibilityAddTraits(.isModal)
        .onAppear {
            // 처음 채우는 값은 움직이지 않게 넣는다 — 페이지가 밀려 들어오는 전환에 실리면 수정 진입의 섹션들이 따로 움직인다.
            var still = Transaction()
            still.disablesAnimations = true
            withTransaction(still) { prefill() }
            if initialFingerprint == nil { initialFingerprint = fingerprint }
        }
        // 재화 목록은 어드민이 정한다(10/13) — 화면을 열 때 한 번 받아 보고, 바뀌었으면 상품 칸을 다시 그린다.
        // 못 받으면 직전에 받은 값 · 앱 내장값 그대로다(PackageCatalog).
        .task {
            if (try? await PackageCatalogApi.shared.load(force: false))?.boolValue == true { catalogRev += 1 }
        }
        // 날짜 선택 — Android `GlgDatePickerDialog` 와 같은 가운데 모달(시스템 그래픽 달력 시트 대신).
        .overlay {
            if showDate {
                GLGDatePickerDialog(
                    initialMillis: dateMillis,
                    onDismiss: { showDate = false },
                    onConfirm: { dateMillis = $0; showDate = false }
                )
            }
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
    /// 지출 입력 게임 목록 — 내 게임이 앞, 나머지는 뒤. 내 게임이 비어 있으면 전부 앞(GameData.pickerGames 파리티).
    private var pickerMine: [Game] { store.myGames.isEmpty ? GLGGames.all : GLGGames.all.filter { store.myGames.contains($0.key) } }
    private var pickerOthers: [Game] { store.myGames.isEmpty ? [] : GLGGames.all.filter { !store.myGames.contains($0.key) } }

    private var amountHero: some View {
        let gameColor = gameChosen ? Color(argb64: game.color) : GLGColor.textSecondary
        return VStack(alignment: .leading, spacing: 0) {
            if gameChosen {
                // 게임 칩과 **과소비 표시**가 한 줄에 선다(10/13 지시) — 왼쪽이 게임, 오른쪽이 표시(spendStatus).
                HStack(spacing: 8) {
                // 게임 — 칩 줄을 늘어놓지 않고 메뉴로 접었다(히어로가 금액을 가리면 안 된다).
                Menu {
                    ForEach(pickerMine + pickerOthers, id: \.key) { g in
                        Button(g.displayName) { selectGame(g.displayName) }
                    }
                } label: {
                    // 크기는 **GLDS 칩 규격**이다(10/13 지시) — 높이 36 · 좌우 14 · 14 Bold · 화살표 11. 색은 게임색 그대로.
                    HStack(spacing: 4) {
                        Circle().fill(gameColor).frame(width: 7, height: 7).padding(.trailing, 2)
                        Text(GameData.shared.byName(name: gameName).shortName)
                            .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(gameColor).lineLimit(1)
                        Image(systemName: "chevron.down").font(.system(size: 11, weight: .bold)).foregroundStyle(gameColor)
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .background(gameColor.opacity(0.12), in: Capsule())
                }
                Spacer(minLength: 0)
                spendStatus
                }
            }

            if gameChosen {
            // 금액 — 히어로 안에서 바로 고친다(별도 필드로 내려보내지 않는다).
            //
            // 치는 동안에도 **읽는 모양 그대로** 보여 준다 — 세 자리 쉼표를 넣고 뒤에 「원」을
            // 붙인다(2026-09-21 지시). 저장하는 값은 숫자뿐이라, 넣을 때 쉼표를 넣고 받을 때
            // 숫자만 거른다.
            //
            // 금액 칸은 GLDS 「예산 금액 입력」 규격의 카드다(10/13 지시 — `GldsAmountCard`). 그 전에는 GLDS 입력필드 모양이었다.
            // **과소비 경고가 떠 있는 동안은 주의 상태**다 — 면이 주황으로 바뀌고 한 번 흔들린 뒤 안쪽 테두리가 깜빡인다.
            // 카드 어디를 눌러도 입력이 시작된다(Android 와 같다).
            GldsAmountCard(label: "금액", warn: inlineNudge != nil) { color in
                TextField("", text: $amountText, prompt: Text("0").foregroundStyle(color.opacity(0.3)))
                    .textFieldStyle(.plain)
                    .font(GldsAmountCardStyle.numberFont)
                    .foregroundStyle(color)
                    .keyboardType(.numberPad)
                    .focused($amountFocused)
                    .tint(color)
                    .fixedSize()
                    .onChange(of: amountText) { _, newValue in
                        // 굴러가는 중의 글자는 우리가 쓴 중간 값이다 — 입력으로 받으면 금액이 그 값으로 바뀌고 고른 상품이 풀린다.
                        guard !amountRolling else { return }
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
            }
            .contentShape(Rectangle())
            .onTapGesture { amountFocused = true }
            .padding(.top, 11)
            }   // gameChosen
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // 카드 없이 화면 폭 그대로(좌우 20) — 아래 섹션과 10 띠로 갈린다. 게임색은 알약 · 환산 글자가 맡는다.
        .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 20)
        // 입력 도중 매 글자마다 뜨면 방해가 된다 → 손이 멈춘 뒤에만 평가한다.
        //
        // **금액과 게임** 둘 중 하나라도 바뀌면 다시 계산한다(27.52.0, Android `LaunchedEffect(amount, game)` 와 같다).
        // 예전엔 금액만 봐서, 금액을 적어 둔 채 게임을 바꾸면 앞 게임의 한도 경고가 그대로 남았다
        // (한도는 게임마다 다르다 — 넘지도 않은 게임에 경고가 붙거나, 넘은 게임에 경고가 안 떴다).
        .task(id: nudgeKey) {
            try? await Task.sleep(nanoseconds: 450_000_000)
            guard !Task.isCancelled else { return }
            inlineNudge = store.overspendBadge(game: game, amount: Int64(amount) ?? 0, editingId: editing?.id, dateMillis: dateMillis)
        }
    }

    /// 과소비 표시 — 게임 칩 오른쪽에 **늘 서 있는** 한 줄(10/13 지시).
    ///
    ///  - 큰 지출이면 간결한 경고가 **강조**된다(Bold · 주황) — 「평소보다 큰 지출」 · 「이번 달 예산 초과」 · 「원신 한도 초과」.
    ///  - 아니면 같은 자리에 강조 없이(Regular · 흐린 색) 선다. 금액이 비었으면 「금액을 입력해주세요」.
    ///
    /// 금액 카드 밖에 있어 문구가 바뀌어도 카드 높이 · 아래 목록이 움직이지 않는다. 자세한 문구는 저장할 때의 확인창이 말한다.
    private var spendStatus: some View {
        let warn = inlineNudge != nil
        let text = inlineNudge ?? (amount.isEmpty ? "금액을 입력해주세요" : "평소 범위의 지출")
        return HStack(spacing: 3) {
            // 경고일 때만 앞에 **원 안의 느낌표**가 선다(10/13 지시 — 그 전에는 세모 「⚠」 글자였다).
            if warn {
                Image(systemName: "exclamationmark.circle.fill").font(.system(size: 13, weight: .semibold))
                    .accessibilityHidden(true)
            }
            Text(text)
                .font(.pretendard(size: 12, weight: warn ? .bold : .regular))
                .lineLimit(1)
        }
        .foregroundStyle(warn ? GldsAmountCardStyle.warn : GLGColor.textSecondary)
        .accessibilityElement(children: .combine)
    }

    /// 과소비 경고를 다시 계산할 계기 — 금액 · 게임. 본문 밖에서 조립한다(뷰 빌더 안의 보간은 타입 추론이 무겁다).
    private var nudgeKey: String { "\(gameName)|\(amount)|\(dateMillis)" }

    /// "원석 3,280 · 약 20뽑" — 재화 환산은 사는 순간에 보여야 의미가 있다.
    private var currencyLine: String? {
        guard let amountLabel = GameDataKt.currencyAmountOrNull(gameName: gameName, itemName: itemName) else { return nil }
        if let pulls = GameDataKt.currencyPullsOrNull(gameName: gameName, itemName: itemName) {
            return "\(amountLabel) · \(pulls)"
        }
        return amountLabel
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
                    .buttonStyle(StillButtonStyle())
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

    /// 게임 변경 — 상품·수량은 게임에 묶인 값이라 함께 초기화하고, 플랫폼 기본값을 다시 고른다.
    private func selectGame(_ name: String) {
        let first = !gameChosen
        guard name != gameName || first else { return }
        gameName = name
        selectedPkg = nil; quantity = 1; itemName = ""
        selectedPickups = []   // 픽업은 게임에 묶인 값이다
        // 자주 사는 것은 **게임을 고르는 이 자리에서** 센다(10/13). 예전엔 섹션이 나타난 뒤(onChange)에 세어,
        // 첫 그림은 안내 문구로 섰다가 한 박자 뒤 「자주 사는 것」이 끼어들며 아래 재화 칸이 밀려 내려갔다.
        // 수정 진입은 보여 주지 않는다(빈 목록 그대로). body 안에서 세지 않는 것은 그대로다 — 글자 하나 칠 때마다 전체 지출을 훑게 된다.
        frequent = editing == nil
            ? SpendingDefaults.shared.frequentItems(spendings: store.spendings, gameName: name, limit: 3)
            : []
        if editing == nil {
            chargePlatform = SpendingDefaults.shared.lastPlatform(spendings: store.spendings, gameName: name) ?? ""
        }
        // 아래 섹션들은 움직이지 않고 바로 선다(10/13) — 예전엔 살짝 밀려 올라오며 나타났다.
        if first { gameChosen = true }
    }

    // ── 재화 선택(10/13 개편, 시안 C) ──
    //
    // 묶음 · 칸에 적는 글자는 공용 PackagePick 이 만든다(Android 와 한 소스). 목록은 늘 펼쳐 둔다 —
    // 예전의 「전체 상품 보기 / 접기」는 걷었다. 색 · 치수는 시안 값 그대로이고, 강조색만 테마를 따른다
    // (면 · 테두리는 primary, 글자 · 채운 바닥은 deep).
    private var productCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            // catalogRev 를 읽어 두면 목록이 갈릴 때 이 칸이 다시 그려진다.
            let _ = catalogRev
            let packages = GameData.shared.packagesFor(game: game)
            let groups = PackagePick.shared.groups(game: game, packages: packages)
            let gameColor = Color(argb64: game.color)

            // 자주 사는 것 — 같은 게임에서 2회 이상 산 것만, 많이 산 순.
            // 기록이 없는 게임은 이 블록이 통째로 빠지고 안내 문구가 선다
            // (빈도를 모르는데 임의로 셋을 고르면 그건 추천이 아니다).
            if !frequent.isEmpty {
                label("자주 사는 것")
                pickGrid(frequent) { f in frequentTile(f, packages: packages) }
                    .padding(.top, 10)
            } else {
                label("빠른 상품 선택")
                Text("선택하면 금액 · 재화명이 자동 입력돼요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
            }

            ForEach(Array(groups.enumerated()), id: \.offset) { i, group in
                // 안내 문구 바로 아래 첫 묶음만 16, 나머지 머리는 18.
                pickGroupHead(group).padding(.top, i == 0 && frequent.isEmpty ? 16 : 18)
                if group.kind == .plan {
                    VStack(spacing: 8) {
                        ForEach(Array(group.items.enumerated()), id: \.offset) { _, item in
                            planRow(item, gameColor: gameColor)
                        }
                    }
                    .padding(.top, 10)
                } else {
                    pickGrid(group.items) { item in currencyTile(item, tier: group.kind == .tiers, gameColor: gameColor) }
                        .padding(.top, 10)
                }
            }

            // 요약 면 — 상품을 골랐을 때만. 무엇을 몇 번 사서 얼마 · 재화 얼마인지와 횟수 스텝퍼를 한 면에 둔다.
            if let pkg = selectedPackage {
                summaryFace(pkg).padding(.top, 16)
            }
            // 환산이 깨지면 조용히 넘어가지 않고 이유를 말한다(막지는 않는다 — 환산은 편의다).
            GldsTextField(label: "재화명", placeholder: "결정석 60", text: $itemName,
                          helper: !itemName.isEmpty && currencyLine == nil
                              ? "재화 환산이 안 돼요 — '결정석 60'처럼 이름 뒤에 숫자를 붙이면 뽑기 수까지 계산해요" : nil)
                .padding(.top, selectedPackage != nil ? 12 : 16)
        }
        .formSection()
    }

    /// 2열 격자(사이 8). 홀수면 마지막 칸은 왼쪽에 선다.
    ///
    /// `LazyVGrid` 를 쓰지 않는다(10/13). 게임을 골라 섹션이 펼쳐지는 전환 안에서 칸이 한 박자 늦게 놓여,
    /// 칸마다 제자리로 날아 들어오는 것처럼 보였다. 칸 수가 많지 않아 줄을 그냥 쌓는다(Android 와 같은 짜임).
    private func pickGrid<Item, Cell: View>(_ items: [Item], @ViewBuilder cell: @escaping (Item) -> Cell) -> some View {
        VStack(spacing: 8) {
            ForEach(Array(stride(from: 0, to: items.count, by: 2)), id: \.self) { i in
                HStack(alignment: .top, spacing: 8) {
                    cell(items[i]).frame(maxWidth: .infinity)
                    if i + 1 < items.count {
                        cell(items[i + 1]).frame(maxWidth: .infinity)
                    } else {
                        Color.clear.frame(maxWidth: .infinity).frame(height: 0)
                    }
                }
            }
        }
    }
    /// 칸의 평소 테두리.
    private static let pickRing = Color(hex: 0xFFE3E6EA)
    /// 자주 사는 것 칸 · 가격 바닥의 채운 면 — GLDS 입력필드와 같은 색.
    private static let pickFace = Color(hex: 0xFFF5F8F8)
    /// 요약 면의 보조 글자.
    private static let pickSummarySub = Color(hex: 0xFF4A5159)

    /// 상품을 고른다 — 금액 · 재화명을 채우고 횟수를 1 로 되돌린다.
    private func applyPackage(_ pkg: GamePackage) {
        selectedPkg = pkg.name; quantity = 1; setAmount(pkg.price, roll: true); itemName = pkg.name
        refreshNudgeNow()
    }

    /// 과소비 경고를 **지금** 다시 잰다 — 상품을 고르거나 횟수를 바꿔 금액이 한 번에 정해졌을 때(10/13).
    ///
    /// 금액을 치는 동안에는 손이 멈춘 뒤(0.45초)에 재지만, 고른 금액은 기다릴 이유가 없다. 기다리면 히어로에
    /// 상품명 · 환산 줄이 먼저 서고 **한 박자 뒤 경고 줄이 끼어들어**, 방금 누른 재화 칸이 뒤늦게 한 번 더 밀려 내려갔다.
    private func refreshNudgeNow() {
        inlineNudge = store.overspendBadge(game: game, amount: Int64(amount) ?? 0, editingId: editing?.id, dateMillis: dateMillis)
    }

    /// 묶음 머리 — 왼쪽 이름, 오른쪽 환산("160개 = 1뽑").
    private func pickGroupHead(_ group: PackagePickGroup) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            label(group.title).frame(maxWidth: .infinity, alignment: .leading)
            if let hint = group.hint {
                Text(hint).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
        }
    }

    /// 게임색 12% 원(36) 안의 선 아이콘(20).
    private func pickIconCircle(_ icon: PickIcon, _ gameColor: Color) -> some View {
        PickIconView(icon: icon, size: 20, lineWidth: 2, color: gameColor)
            .frame(width: 36, height: 36)
            .background(gameColor.opacity(0.12), in: Circle())
    }

    /// 자주 사는 것 한 칸 — 누르면 상품 · 금액 · 재화명이 한 번에 채워진다.
    private func frequentTile(_ f: FrequentItem, packages: [GamePackage]) -> some View {
        let sel = PackagePick.shared.isFrequentSelected(itemName: f.itemName, selectedPackageName: selectedPkg, currentItemName: itemName)
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        return Button {
            // 목록에 있는 상품이면 그 상품을 고른 것으로 본다(요약 면 · 스텝퍼가 선다). 없으면 재화명만 채운다.
            selectedPkg = packages.first { $0.name == f.itemName }?.name
            quantity = 1
            itemName = f.itemName
            setAmount(f.amount, roll: true)
            refreshNudgeNow()
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(f.itemName).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1).truncationMode(.tail)
                HStack(spacing: 0) {
                    Text(won(f.amount)).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(f.count)회").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.deep).lineLimit(1)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(sel ? accent.primary.opacity(0.08) : Self.pickFace, in: shape)
            .overlay(shape.strokeBorder(sel ? accent.primary : .clear, lineWidth: 2))
            .contentShape(shape)
        }
        .buttonStyle(StillButtonStyle())
        .accessibilityAddTraits(sel ? .isSelected : [])
    }

    /// 재화 한 칸 — [아이콘 · 첫 줄 · 둘째 줄] 아래에 가격 바닥.
    /// `tier` 면 첫 줄이 개수(17), 아니면 이름(14 · 한 줄 말줄임). 둘째 줄은 비어도 자리를 남겨 칸 높이를 맞춘다.
    private func currencyTile(_ item: PackagePickItem, tier: Bool, gameColor: Color) -> some View {
        let sel = selectedPkg == item.pkg.name
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return Button { applyPackage(item.pkg) } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    pickIconCircle(.gem, gameColor)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.headline).font(.pretendard(size: tier ? 17 : 14, weight: .bold))
                            .foregroundStyle(GLGColor.textPrimary).lineLimit(1).truncationMode(.tail)
                        Text(item.sub).font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.deep)
                            .lineLimit(1).frame(height: 15, alignment: .leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 4)
                HStack(spacing: 5) {
                    if sel { PickIconView(icon: .check, size: 14, lineWidth: 3, color: .white) }
                    Text(item.price).font(.pretendard(size: 13, weight: .bold))
                        .foregroundStyle(sel ? Color.white : GLGColor.textPrimary)
                        .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                    if let pulls = item.pulls {
                        Text(pulls).font(.pretendard(size: 12)).foregroundStyle(sel ? Color.white : GLGColor.textSecondary).lineLimit(1)
                    }
                }
                .padding(.horizontal, 10)
                .frame(height: 30)
                .background(sel ? accent.deep : Self.pickFace, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            }
            .padding(.top, 12).padding(.horizontal, 8).padding(.bottom, 8)
            .background(sel ? accent.primary.opacity(0.08) : Color.white, in: shape)
            .overlay(shape.strokeBorder(sel ? accent.primary : Self.pickRing, lineWidth: sel ? 2 : 1))
            .contentShape(shape)
        }
        .buttonStyle(StillButtonStyle())
        // 칸 안의 글자는 조각나 있다("300" · "추가 +30") — 읽어 줄 때는 상품 이름으로 한 번에 말한다.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([item.pkg.name, tier ? item.sub : "", item.price, item.pulls ?? ""].filter { !$0.isEmpty }.joined(separator: " "))
        .accessibilityAddTraits(sel ? [.isButton, .isSelected] : .isButton)
    }

    /// 월정액 · 패스 한 줄 — [아이콘 · 이름 / 종류 · 가격]. 고르면 가격 앞에 체크가 서고 가격이 강조색이 된다.
    private func planRow(_ item: PackagePickItem, gameColor: Color) -> some View {
        let sel = selectedPkg == item.pkg.name
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return Button { applyPackage(item.pkg) } label: {
            HStack(spacing: 10) {
                pickIconCircle(item.pkg.kind == .monthly ? .calendar : .ticket, gameColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.headline).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text(item.sub).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if sel { PickIconView(icon: .check, size: 16, lineWidth: 3, color: accent.deep) }
                Text(item.price).font(.pretendard(size: 15, weight: .bold))
                    .foregroundStyle(sel ? accent.deep : GLGColor.textPrimary).lineLimit(1)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(sel ? accent.primary.opacity(0.08) : Color.white, in: shape)
            .overlay(shape.strokeBorder(sel ? accent.primary : Self.pickRing, lineWidth: sel ? 2 : 1))
            .contentShape(shape)
        }
        .buttonStyle(StillButtonStyle())
        .accessibilityAddTraits(sel ? .isSelected : [])
    }

    /// 고른 상품의 요약 면 — 위 줄에 [무엇을 몇 번 · 재화량] 과 횟수 스텝퍼, 선 아래에 [단가 × 횟수 · 합계].
    /// 한 번에 같은 상품을 여러 번 산 경우 횟수만큼 금액 · 재화를 곱해 한 건으로 기록한다.
    private func summaryFace(_ pkg: GamePackage) -> some View {
        let s = PackagePick.shared.summary(game: game, pkg: pkg, quantity: Int32(quantity), amount: Int64(amount) ?? 0)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(s.title).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    if !s.sub.isEmpty {
                        Text(s.sub).font(.pretendard(size: 12)).foregroundStyle(Self.pickSummarySub)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 6) {
                    stepperBtn(.minus, "횟수 줄이기", enabled: quantity > Int(SpendingEditLogic.shared.MIN_QUANTITY)) { setQuantity(quantity - 1) }
                    Text("\(quantity)").font(.pretendard(size: 16, weight: .bold)).foregroundStyle(GLGColor.textPrimary).frame(minWidth: 28)
                    stepperBtn(.plus, "횟수 늘리기", enabled: quantity < Int(SpendingEditLogic.shared.MAX_QUANTITY)) { setQuantity(quantity + 1) }
                }
            }
            accent.primary.opacity(0.22).frame(height: 1).padding(.vertical, 10)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(s.unitLine).font(.pretendard(size: 12)).foregroundStyle(Self.pickSummarySub)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(s.total).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(accent.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// 스텝퍼 단추(34 원) — 흰 면 + 강조색 50% 테두리. 못 누르면 반투명 흰 면 + 옅은 테두리 · 흐린 아이콘.
    private func stepperBtn(_ icon: PickIcon, _ label: String, enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            PickIconView(icon: icon, size: 18, lineWidth: 2.2, color: enabled ? accent.primary : GLGColor.textSecondary.opacity(0.4))
                .frame(width: 34, height: 34)
                .background(Color.white.opacity(enabled ? 1 : 0.5), in: Circle())
                .overlay(Circle().strokeBorder(enabled ? accent.primary.opacity(0.5) : Color.black.opacity(0.06), lineWidth: 1))
                .contentShape(Circle())
        }
        .buttonStyle(StillButtonStyle()).disabled(!enabled)
        .accessibilityLabel(label)
    }

    private var dateCard: some View {
        sectionCard {
            // 누르는 필드 — 모양은 GLDS 입력필드, 탭하면 날짜 시트(Android GldsTextField(onClick) 와 같이).
            GldsTextField(label: "날짜", placeholder: "", text: .constant(DateUtil.shared.labelWithWeekday(millis: dateMillis)),
                         trailingIcon: .calendar, onTap: { showDate = true })
        }
    }

    // ── 지금 픽업 중(10/13) — 누구를 뽑으려고 썼는지 골라 두면 장식 태그로 남는다 ──
    //
    // 그 게임에서 지금 열린 최고 등급 픽업(캐릭터 · 무기)을 칩으로 늘어놓는다. 여러 개 고를 수 있다.
    // 배너 데이터가 없는 게임(명조 · 엔드필드 · 이환)은 이 섹션이 통째로 빠진다.
    // 이미 붙어 있던 지난 픽업은 뒤에 남겨, 고치는 화면에서 뗄 수 있게 한다(SpendingPickups.options).
    @ViewBuilder private var pickupCard: some View {
        let options = SpendingPickups.shared.options(banners: store.activeBanners, game: game, nowMillis: nowMs(), selected: selectedPickups)
        if !options.isEmpty {
            sectionCard {
                Text("지금 픽업 중").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("고르면 태그로 남아요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 3)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(options, id: \.name) { option in pickupChip(option) }
                    }
                }
                .padding(.top, 12)
            }
        }
    }

    /// 「지금 픽업 중」의 한 칸 — **GLDS 칩 규격**(높이 36 · 14 Bold · 흰 면 + 1 `#E3E5EA`, 고르면 강조색 1.5 테두리 + deep 글자)에
    /// 앞 초상(24 원)을 붙인 모양이다. 초상이 없으면 대체 아이콘(캐릭터 = 사람, 무기 = 별)이 선다.
    private func pickupChip(_ option: PickupOption) -> some View {
        let sel = selectedPickups.contains(option.name)
        let fallback = Image(systemName: option.weapon ? "star.fill" : "person.fill")
            .font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
        return Button {
            if let i = selectedPickups.firstIndex(of: option.name) { selectedPickups.remove(at: i) } else { selectedPickups.append(option.name) }
        } label: {
            HStack(spacing: 6) {
                Group {
                    if option.iconUrl.isEmpty {
                        fallback
                    } else {
                        AsyncImage(url: URL(string: option.iconUrl)) { phase in
                            if let image = phase.image { image.resizable().scaledToFill() } else { fallback }
                        }
                    }
                }
                .frame(width: 24, height: 24)
                .background(Color(hex: 0xFFF7F8FA), in: Circle())
                .clipShape(Circle())
                Text(option.name).font(.pretendard(size: 14, weight: .bold))
                    .foregroundStyle(sel ? accent.deep : GLGColor.textPrimary).lineLimit(1)
            }
            .padding(.leading, 6).padding(.trailing, 14)
            .frame(height: 36)
            .background(Color.white, in: Capsule())
            .overlay(Capsule().strokeBorder(sel ? accent.primary : Color(hex: 0xFFE3E5EA), lineWidth: sel ? 1.5 : 1))
            .contentShape(Capsule())
        }
        .buttonStyle(StillButtonStyle())
        .accessibilityAddTraits(sel ? .isSelected : [])
    }

    // ── 자세히 — 결제수단·플랫폼·태그·메모·구독을 하나로 접는다 ──
    //
    // 매번 바뀌는 값이 아니라 접어 두되, **접힌 채로도 현재 값 요약을 보여준다.**
    // 안 보이면 확인하려고 매번 펴게 되고, 그러면 접은 의미가 없다.
    // 기본값과 다른 값이 하나라도 있으면 요약을 강조색으로 — "뭔가 정해져 있다"가 보이게.
    private var detailsCard: some View {
        let summary = detailSummary
        return VStack(alignment: .leading, spacing: 0) {
            Button { toggleDetails() } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        // 제목은 섹션 제목 크기(17 Bold)다(10/13 지시 — 그 전에는 13 이라 섹션 머리로 안 읽혔다).
                        Text("자세히").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Text(summary.text)
                            .font(.pretendard(size: 12))
                            .foregroundStyle(summary.isCustom ? accent.primary : GLGColor.textSecondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    // 접고 펴는 표시는 **화살표 하나를 돌린다**(10/13 지시, Android 와 같다) — 그 전에는 「▾」 · 「⌃」 글자를 바꿔 끼웠다.
                    Image(systemName: "chevron.down").font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(GLGColor.textSecondary)
                        .rotationEffect(.degrees(detailsExpanded ? 180 : 0))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(StillButtonStyle())

            if detailsExpanded { detailFields.padding(.top, 14) }
        }
        .formSection()
    }

    /// 「자세히」 접고 펴기 — **화면이 같이 움직인다**(10/13 지시).
    ///
    ///  - 펼칠 때: 내용이 넓어지는 만큼 화면이 따라 올라가, 펼친 칸이 끝까지 보인다.
    ///  - 접을 때: 영역이 닫히는 것과 함께 화면이 접힌 끝으로 내려온다.
    ///
    /// 「자세히」는 폼의 마지막 섹션이라 두 경우 모두 **내용의 끝**으로 가면 된다. 접고 펴는 것과 스크롤을 **한 애니메이션**에 싣는다 —
    /// 스크롤 위치를 「끝」으로 적어 두면 같은 순서에서 새 높이로 계산된다. 예전엔 표식까지 스크롤을 따로 불렀는데,
    /// 처음 펼칠 때는 배치가 늦어 빠졌고, 여러 번 다시 부르게 하니 애니메이션이 끊겨 뚝뚝 열렸다(같은 날 지적 두 번).
    private func toggleDetails() {
        withAnimation(.easeInOut(duration: 0.25)) {
            detailsExpanded.toggle()
            formScroll.scrollTo(edge: .bottom)
        }
    }

    /// 접힌 상태에서 보여줄 한 줄("카카오페이 · 구글플레이 · 태그 2")과, 기본값에서 벗어난 값이 있는지(요약 강조 여부).
    ///
    /// 공용 계산이다(27.52.0, Android 와 한 소스). 예전엔 여기서 따로 조립했는데 「값이 있다」의 기준이 달랐다 —
    /// Android 는 공백을 뺀 글자로 봤고 여기는 빈 문자열만 봐서, 스페이스만 친 메모가 「메모」로 세어지고 요약이 강조됐다.
    private var detailSummary: SpendingDetailSummary {
        SpendingEditLogic.shared.detailSummary(paymentMethod: paymentMethod, chargePlatform: chargePlatform,
                                               selectedTags: selectedTags, customTags: customTags, memo: memo)
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
    // 범위(1~99) · 「×N」을 붙이는 모양은 공용 SpendingEditLogic 이 정한다(27.52.0, Android 와 한 소스).
    // 저장된 항목명에서 횟수 · 상품을 되읽던 detectQuantity · stripMult 도 그쪽(restoreItem)으로 옮겼다.
    private func setQuantity(_ q: Int) {
        let qty = Int(SpendingEditLogic.shared.clampQuantity(quantity: Int32(q)))
        quantity = qty
        if let pkg = selectedPackage {
            let applied = SpendingEditLogic.shared.applyQuantity(baseName: pkg.name, unitPrice: pkg.price, quantity: Int32(qty))
            setAmount(applied.amount, roll: true)
            itemName = applied.itemName
            refreshNudgeNow()
        }
    }

    private func prefill() {
        guard !didInit else { return }; didInit = true
        if let e = editing {
            gameName = e.gameName; setAmount(e.amount); dateMillis = e.dateMillis
            paymentMethod = e.paymentMethod.isEmpty ? "카드" : e.paymentMethod
            chargePlatform = e.chargePlatform
            itemName = e.itemName; memo = e.memo
            // 기존 태그는 **추천 목록에 있는 것만** 칩 선택으로, 나머지는 직접 입력 칸으로 가른다(27.52.0, Android 와 같은 규칙).
            // 예전엔 전부 선택 집합에 담았는데 칩은 추천 태그만 그리고 직접 입력 칸은 비어 시작해서,
            // 직접 적어 넣었던 태그는 수정 화면에서 보이지도 않고 지울 방법도 없었다(저장하면 그대로 다시 붙었다).
            // 가르는 규칙 · 이어 붙이는 구분자는 공용 SpendingEditLogic.splitTags 가 정한다 — 저장할 때 이 칸을 쪼개는
            // mergeTags(doSave)와 한 파일에 있다. 빈 태그는 칸에 넣지 않는다(예전엔 여기만 안 걸러 구분자만 남았다).
            // 지문(initialFingerprint)은 이 뒤에 뜨므로 이 초깃값이 「고친 것」으로 잡히지 않는다.
            let tagSplit = SpendingEditLogic.shared.splitTags(tags: e.tags)
            selectedTags = tagSplit.selected
            customTags = tagSplit.custom
            selectedPickups = tagSplit.pickups
            // 저장된 항목명("창세의 결정 300 ×3")에서 상품·구매 횟수 복원 → 스텝퍼 노출.
            // 공용 restoreItem(27.52.0) — **목록에 없는 게임명이면 상품을 찾지 않는다.** 예전엔 여기서 byName 으로
            // 원신에 떨어뜨려 맞춰 봐서, 다른 게임의 지출에 원신 단가로 곱하는 스텝퍼가 붙을 수 있었다(Android 는 안 붙였다).
            let restored = SpendingEditLogic.shared.restoreItem(gameName: e.gameName, itemName: e.itemName)
            quantity = Int(restored.quantity)
            selectedPkg = restored.packageName
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
        if let msg = store.overspendNudge(game: game, amount: parsed, editingId: editing?.id, dateMillis: dateMillis) { nudgeMsg = msg }
        else { doSave() }
    }

    /// 입력 지문 — 이 값이 처음과 다르면 "고친 게 있다".
    private var fingerprint: String {
        [gameName, amount, "\(dateMillis)", paymentMethod, chargePlatform, itemName, memo, customTags,
         selectedTags.joined(separator: ","), selectedPickups.joined(separator: ",")].joined(separator: "|")
    }
    private var isDirty: Bool { initialFingerprint != nil && fingerprint != initialFingerprint }
    private func requestDismiss() { if isDirty { confirmDiscard = true } else { onClose() } }

    private func doSave() {
        guard !saved else { return }
        saved = true
        nudgeMsg = nil
        let parsed = Int64(amount) ?? 0
        // 칩 + 직접 입력 칸을 한 목록으로 — 쪼개기 · 다듬기 · 중복 제거는 공용 규칙(27.52.0, Android 와 한 소스).
        // 예전엔 여기서 공백 · 탭만 떼어 내, 붙여 넣은 글자에 줄바꿈이 있으면 태그에 남을 수 있었다(Android 는 떼어 냈다).
        let tags = SpendingEditLogic.shared.mergeTags(selected: selectedTags, custom: customTags, pickups: selectedPickups)
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

/// 지출 추가 · 수정 페이지의 단추 모양 — 누르는 동안만 살짝 흐려지고, **제 애니메이션이 없다**(10/13).
///
/// 시스템 `.plain` 은 손을 떼는 순간 제 라벨에 애니메이션을 건다. 그 순간에 고른 상품이 바뀌어 화면이 다시 짜이면
/// (금액 영역의 줄 수가 달라져 목록이 위아래로 밀린다) **방금 누른 칸만** 그 애니메이션에 실려, 다른 칸은 바로 옮겨 갔는데
/// 혼자 0.4초쯤 늦게 따라왔다 — 고른 표시도 그동안 서서히 떴다(Duo 화면 녹화로 확인).
///
/// 여기서 애니메이션을 **끊지는 않는다** — 바깥에서 건 움직임(과소비 경고가 나타날 때)은 다른 칸과 똑같이 따라가야 한다.
private struct StillButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// 「재화 선택」의 선 아이콘 — 시안의 SVG 경로(24 격자 · 둥근 끝)를 그대로 옮겼다. Android 는 같은 경로 글자를 읽어 그린다.
private enum PickIcon { case gem, calendar, ticket, check, minus, plus }

private struct PickIconShape: Shape {
    let icon: PickIcon

    func path(in rect: CGRect) -> Path {
        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }
        var p = Path()
        switch icon {
        case .gem:
            // M6 3h12l4 6-10 12L2 9z  M2 9h20  M9 3l-1.5 6L12 21l4.5-12L15 3
            p.move(to: pt(6, 3)); p.addLine(to: pt(18, 3)); p.addLine(to: pt(22, 9)); p.addLine(to: pt(12, 21)); p.addLine(to: pt(2, 9)); p.closeSubpath()
            p.move(to: pt(2, 9)); p.addLine(to: pt(22, 9))
            p.move(to: pt(9, 3)); p.addLine(to: pt(7.5, 9)); p.addLine(to: pt(12, 21)); p.addLine(to: pt(16.5, 9)); p.addLine(to: pt(15, 3))
        case .calendar:
            // M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z  M4 10h16  M8 3v4  M16 3v4
            p.addRoundedRect(in: CGRect(x: 4, y: 5, width: 16, height: 16), cornerSize: CGSize(width: 1, height: 1), style: .circular)
            p.move(to: pt(4, 10)); p.addLine(to: pt(20, 10))
            p.move(to: pt(8, 3)); p.addLine(to: pt(8, 7))
            p.move(to: pt(16, 3)); p.addLine(to: pt(16, 7))
        case .ticket:
            // M3 9V6h18v3a3 3 0 0 0 0 6v3H3v-3a3 3 0 0 0 0-6z  M14 6v12 — 양옆이 안쪽으로 반원(반지름 3)만큼 파인다.
            p.move(to: pt(3, 9)); p.addLine(to: pt(3, 6)); p.addLine(to: pt(21, 6)); p.addLine(to: pt(21, 9))
            p.addRelativeArc(center: pt(21, 12), radius: 3, startAngle: .degrees(-90), delta: .degrees(-180))
            p.addLine(to: pt(21, 18)); p.addLine(to: pt(3, 18)); p.addLine(to: pt(3, 15))
            p.addRelativeArc(center: pt(3, 12), radius: 3, startAngle: .degrees(90), delta: .degrees(-180))
            p.closeSubpath()
            p.move(to: pt(14, 6)); p.addLine(to: pt(14, 18))
        case .check:
            // M20 6L9 17l-5-5
            p.move(to: pt(20, 6)); p.addLine(to: pt(9, 17)); p.addLine(to: pt(4, 12))
        case .minus:
            // M5 12h14
            p.move(to: pt(5, 12)); p.addLine(to: pt(19, 12))
        case .plus:
            // M12 5v14  M5 12h14
            p.move(to: pt(12, 5)); p.addLine(to: pt(12, 19))
            p.move(to: pt(5, 12)); p.addLine(to: pt(19, 12))
        }
        let scale = min(rect.width, rect.height) / 24
        return p.applying(CGAffineTransform(scaleX: scale, y: scale))
    }
}

/// 선 아이콘 한 개 — `lineWidth` 는 24 격자 기준 굵기라 크기에 맞춰 함께 줄인다(SVG 와 같다).
private struct PickIconView: View {
    let icon: PickIcon
    let size: CGFloat
    let lineWidth: CGFloat
    let color: Color

    var body: some View {
        PickIconShape(icon: icon)
            .stroke(color, style: StrokeStyle(lineWidth: lineWidth * size / 24, lineCap: .round, lineJoin: .round))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// 입력 섹션 — 카드 없이 화면 폭 그대로, 위에 10 띠를 얹어 앞 묶음과 가른다.
/// 좌우 20 · 위 22 · 아래 20(지출 상세 섹션과 같은 규격). 띠가 섹션 안에 있어 섹션과 함께 나타난다.
private extension View {

    func formSection() -> some View {
        VStack(spacing: 0) {
            GldsBand()
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
            // 한 장으로 합친 뒤에 그림자를 준다 — 그냥 주면 카드 안의 입력칸 · 버튼 · 글자마다 그림자가 따로 생긴다(10/14 지적).
            .compositingGroup()
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
        .buttonStyle(StillButtonStyle())
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
                    .buttonStyle(StillButtonStyle())
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
