import SwiftUI
import Shared

// 지출 상세 — 전체 정보 + 수정/삭제. (Compose SpendingDetailScreen 대응)
struct SpendingDetailView: View {
    var store: SpendingStore
    let spendingId: String
    let onEdit: (Spending) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDelete = false
    /// 히어로 실제 높이 — 스크롤이 이걸 넘어가면 헤더를 밝은 배경 모드로 되돌린다.
    @State private var heroHeight: CGFloat = 0
    /// 히어로를 지나쳤는가. 헤더 아이콘 색·바 배경이 여기에 달려 있다.
    @State private var pastHero = false

    /// 편집 반영 위해 라이브 목록에서 재조회.
    private var spending: Spending? { store.spendings.first { $0.id == spendingId } }

    var body: some View {
        // 안전 영역 높이를 **여기서** 읽는다. 아래 ScrollView 는 `ignoresSafeArea` 로 상단까지
        // 올라가 있어 그 안에서는 inset 이 0 으로 보고된다 — 히어로가 얼마나 내려가야 하는지
        // 알 수 없어 글자가 상태바에 잘렸다.
        GeometryReader { proxy in
            Group {
                if let s = spending {
                    content(s, topInset: proxy.safeAreaInsets.top)
                } else {
                    // 삭제됨 — 종료
                    Color.clear.onAppear { dismiss() }
                }
            }
        }
        .background(GLGBackground { Color.clear }.ignoresSafeArea())
        // 히어로가 상태바까지 올라가야 하므로 타이틀을 비운다 — 글자가 그라데이션 위에 겹친다.
        // 어느 화면인지는 히어로의 게임명·금액이 말해 준다.
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        // 네비게이션 바 배경을 걷어내 히어로 색이 상태바 영역까지 이어지게 한다.
        // iOS 26 은 `toolbarBackground(_:for:)` 가 더 이상 바의 유리를 걷어내지 못한다 —
        // 그대로 두면 히어로 그라데이션 위에 바 배경이 한 겹 더 얹혀 **헤더만 색이 달라 보인다**.
        // 새 API(`toolbarBackgroundVisibility`)로 확실히 숨긴다.
        .modifier(GLGHiddenToolbarBackground(hidden: !pastHero))
        // 히어로가 파스텔이라 아이콘은 히어로 위에서도 **기본(어두운) 색**이 읽힌다 —
        // 원색이었을 땐 흰색으로 뒤집어야 했고, 그러면 스크롤 후 흰 배경에서 아이콘이 사라졌다.
        .animation(.easeInOut(duration: 0.18), value: pastHero)
    }

    private func content(_ s: Spending, topInset: CGFloat) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                hero(s, topInset: topInset)
                shareCard(s)
                sameItemCard(s)
                // 상세 정보 — 히어로가 금액·재화·날짜·결제를 흡수했으므로 남은 것만.
                GLGCard(cornerRadius: 24, padding: 20) {
                    VStack(spacing: 0) {
                        // 항목은 남긴다 — 히어로의 재화 환산이 어디서 나온 값인지 알려 주는 근거다.
                        // '구분'은 뺐다(히어로 배지가 이미 정기/일반을 말한다).
                        detailRow("항목", s.itemName.isEmpty ? "—" : s.itemName)
                        Divider()
                        detailRow("결제 수단", s.paymentMethod.isEmpty ? "—" : s.paymentMethod)
                        if !s.chargePlatform.isEmpty {
                            Divider()
                            detailRow("충전 플랫폼", s.chargePlatform)
                        }
                        if !s.memo.isEmpty { Divider(); detailRow("메모", s.memo) }
                        if !s.tags.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 8) {
                                Text("태그").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                                HStack(spacing: 6) { ForEach(s.tags, id: \.self) { TagChip(tag: $0) } }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 12)
                        }
                    }
                }
                .padding(.horizontal, 16)
                Color.clear.frame(height: 24)
            }
            .padding(.bottom, 8)
        }
        .scrollIndicators(.hidden)
        // 히어로 색이 상태바까지 이어지도록 스크롤 영역을 위로 올린다. 대신 히어로가
        // `topInset` 만큼 스스로 내려가므로 글자는 안전 영역 안에 남는다.
        .ignoresSafeArea(.container, edges: .top)
        // 히어로 하단이 네비게이션 바 아래로 사라지는 순간을 경계로 삼는다.
        // 바 높이(44)를 빼는 이유는, 히어로 끝이 바에 **가려지기 시작할 때** 색이 바뀌어야
        // 아이콘이 흰 배경 위에 흰색으로 남는 한 프레임이 생기지 않기 때문이다.
        .onScrollGeometryChange(for: Bool.self) { geo in
            heroHeight > 0 && geo.contentOffset.y > heroHeight - topInset - 44
        } action: { _, newValue in
            pastHero = newValue
        }
        .alert("이 지출을 삭제할까요?", isPresented: $confirmDelete) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("삭제", role: .destructive) { store.deleteSpending(s.id); dismiss() }.glgAlertTint()
        } message: { Text("삭제하면 되돌릴 수 없어요.") }
        // 수정·삭제를 네비 헤더 우측 아이콘으로. 각각 별도 ToolbarItem + ToolbarSpacer 로 분리 —
        // iOS 26 은 인접 툴바 아이템을 하나의 글래스 캡슐로 묶으므로 스페이서로 갈라 독립 원형 버튼으로.
        .toolbar {
            // 수정 — 경로에 한 칸 더 쌓는다(호출부가 path 에 .edit 를 append).
            //
            // ⚠️ **이 화면 안에서 `.navigationDestination` 을 선언하면 안 된다.** 이 화면 자체가
            // 이미 목적지라, 목적지 안에서 목적지를 또 등록하면 스택이 초기화돼 목록으로 튕긴다.
            // 실제로 그 구조로 만들었다가 수정 버튼이 목록으로 빠지는 버그를 냈다.
            ToolbarItem(placement: .topBarTrailing) {
                Button { onEdit(s) } label: { Image(systemName: "pencil") }
            }
            if #available(iOS 26.0, *) {
                ToolbarSpacer(.fixed, placement: .topBarTrailing)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(role: .destructive) { confirmDelete = true } label: { Image(systemName: "trash") }
                    // 삭제만 위험색으로 가른다 — `role: .destructive` 는 툴바에서 색까지 바꿔 주지
                    // 않아서, 수정과 똑같은 색으로 나란히 놓여 있었다.
                    .tint(needsBarTint ? GLGColor.dangerText : nil)
            }
        }
        // 뒤로가기(시스템 back)와 수정 아이콘 색 — 툴바 전체에 건다.
        // 이 화면 본문은 색을 전부 명시(ink·GLGColor)해서 쓰므로 여기 tint 는 바 아이콘에만 걸린다.
        .tint(needsBarTint ? heroInk(s) : nil)
    }

    /**
     히어로 — 금액·재화 환산·비중을 한 덩어리로.

     **배경만 상태바까지 올린다**(`ignoresSafeArea(edges: .top)`). 콘텐츠까지 올리면 글자가
     상태바와 겹치므로, 색은 위로 이어지고 텍스트는 안전 영역 안에 남는 구조다.
     네비게이션 바는 배경을 숨기고(`toolbarBackground(.hidden)`) 아이콘만 밝게 뒤집었다.
     */
    /// 툴바 아이콘 색을 **직접 정해야 하는 구간인가**(iOS 18).
    ///
    /// iOS 26+ 는 툴바 아이콘이 유리 캡슐 위에 앉아 배경과 분리되므로 강조색 그대로도 읽힌다.
    /// iOS 18 은 캡슐이 없어 아이콘만 파스텔 히어로 위에 덩그러니 놓이고, 그때 대비가 무너진다.
    private var needsBarTint: Bool {
        if #available(iOS 26.0, *) { return false }
        return true
    }

    /// 파스텔 히어로 위에 얹는 색 — 게임색을 검정 쪽으로 눌러 같은 계열을 유지한다.
    /// 순수 검정으로 쓰면 배경과 따로 놀고, 원색 그대로면 옅은 배경에서 뭉갠다.
    ///
    /// **툴바 아이콘도 이걸 쓴다.** 예전엔 툴바만 이 규칙 밖에 있어서 상위(`ContentView`)가 건
    /// 앱 강조색이 그대로 내려왔고, 옅은 파스텔 위에서 대비가 약한 데다 게임색과 따로 놀았다.
    /// 항상 충분히 어두운 값이라 스크롤해서 흰 배경으로 넘어가도 그대로 읽힌다.
    private func heroInk(_ s: Spending) -> Color {
        Color(argb64: s.gameColor).mix(with: .black, by: 0.62)
    }

    @ViewBuilder
    private func hero(_ s: Spending, topInset: CGFloat) -> some View {
        let base = Color(argb64: s.gameColor)
        let ink = heroInk(s)

        // 세 줄로 끝낸다 — ① 무엇(게임·영문 표식) ② 얼마 ③ 환산·언제.
        // 정보의 무게가 위에서 아래로 줄고, 마지막 줄만 좌우로 갈라 단조로움을 깬다.
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: 7) {
                Text(s.gameName)
                    .font(.pretendard(size: 13, weight: .bold))
                    .foregroundStyle(ink)
                Text(s.isSubscription ? "정기" : "일반")
                    .font(.pretendard(size: 10, weight: .bold))
                    .foregroundStyle(ink)
                    .padding(.horizontal, 8).padding(.vertical, 2.5)
                    .background(Color.white.opacity(0.75), in: Capsule())
                    .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 4 }
                Spacer(minLength: 8)
                // 게임 코드 — 읽으라고 넣은 글자가 아니라 **여백을 채우는 표식**이다.
                // 우측 상단이 비어 히어로가 왼쪽으로 쏠려 보였다.
                //
                // ⚠️ 여기만 시스템 폰트를 쓴다(앱 전역은 Pretendard). 고정폭 자체가 디자인이라
                // 자간이 일정해야 하는데 Pretendard 에는 monospaced 계열이 없다.
                Text(gameCode(s.gameName))
                    .font(.system(size: 11.5, weight: .semibold, design: .monospaced))
                    .tracking(0.6)
                    .foregroundStyle(ink.opacity(0.45))
                    .lineLimit(1).minimumScaleFactor(0.7)
            }

            Text(won(s.amount))
                .font(.pretendard(size: 34, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                // 금액은 숫자가 커서 위쪽 여백이 실제보다 넓어 보인다 — 조금 당겨 붙인다.
                .padding(.top, 10)

            heroFacts(s, ink: ink).padding(.top, 14)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 22)
        // 상태바(topInset) 아래에서 시작한다. 뒤에 더하는 값은 네비게이션 바 높이(44)가 아니라
        // **뒤로가기 버튼과 겹치지 않을 만큼**이다 — 44 를 그대로 주면 아이콘과 게임명 사이가
        // 한 줄 비어 보인다. 좌측 상단이 비는 만큼 히어로가 위로 붙는다.
        .padding(.top, topInset + 26)
        .frame(maxWidth: .infinity, alignment: .leading)
        // `padding(.top, -800)` — 스크롤을 아래로 당겼을 때(바운스) 히어로 위가 드러나
        // 흰 배경이 비치던 걸 막는다. 배경만 위로 크게 빼므로 하단 라운드는 그대로다.
        .background(
            UnevenRoundedRectangle(bottomLeadingRadius: 28, bottomTrailingRadius: 28, style: .continuous)
                // 파스텔 — 게임색을 흰색과 섞어 옅게 깐다. 원색을 그대로 쓰면 카드 위 흰 화면과
                // 대비가 너무 세서 상세가 배너처럼 읽힌다. 색은 '어느 게임인가'만 말하면 된다.
                .fill(LinearGradient(
                    colors: [base.mix(with: .white, by: 0.80), base.mix(with: .white, by: 0.66)],
                    startPoint: .topLeading, endPoint: .bottomTrailing))
                .padding(.top, -800)
        )
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { heroHeight = $0 }
    }

    /**
     '이 지출은' — 월·게임 대비 비중을 **진행바**로, 평소 대비는 한 줄 문장으로.

     히어로 안에 세 숫자를 나란히 넣어 봤는데, 색 위에 작은 글자가 겹쳐 읽는 부담이 컸다.
     비중은 막대로 보면 숫자를 읽지 않아도 대략이 잡힌다.
     */
    @ViewBuilder
    private func shareCard(_ s: Spending) -> some View {
        let share = SpendingDetailStats.shared.share(target: s, all: store.spendings)
        let typical = SpendingDetailStats.shared.vsTypical(
            target: s, all: store.spendings, nowMillis: nowMs(), months: SpendingDetailStats.shared.TYPICAL_MONTHS)
        let base = Color(argb64: s.gameColor)
        GLGCard(cornerRadius: 24, padding: 20) {
            VStack(alignment: .leading, spacing: 0) {
                Text("이 지출은")
                    .font(.pretendard(size: 11.5, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary)
                    .padding(.bottom, 12)

                bar("\(DateUtil.shared.month(millis: s.dateMillis))월 지출에서",
                    percent: share.monthPercent, color: base)
                bar("\(GameData.shared.byName(name: s.gameName).shortName) \(DateUtil.shared.month(millis: s.dateMillis))월 지출에서",
                    percent: share.gamePercent, color: base)
                    .padding(.top, 12)

                // 표본이 모자라면(3건 미만) 이 줄 자체가 없다 — 근거 없는 '평소'를 말하지 않는다.
                if let t = typical {
                    (Text("평소 단건보다 ")
                        + Text(t.ratioLabel).foregroundColor(t.isNotable ? Color(hex: 0xFFE8634A) : GLGColor.textPrimary).bold()
                        + Text(t.isNotable ? " 큽니다" : " 수준입니다"))
                        .font(.pretendard(size: 12))
                        .foregroundStyle(GLGColor.textSecondary)
                        .padding(.top, 14)
                    Text("중앙값 \(won(t.median)) · 최근 \(Int(SpendingDetailStats.shared.TYPICAL_MONTHS))개월 \(t.sampleSize)건")
                        .font(.pretendard(size: 10.5))
                        .foregroundStyle(GLGColor.textSecondary)
                        .padding(.top, 3)
                } else if s.isSubscription {
                    Text("정기 결제는 매달 같은 금액이라 평소와 견주지 않아요.")
                        .font(.pretendard(size: 11.5))
                        .foregroundStyle(GLGColor.textSecondary)
                        .padding(.top, 14)
                }
            }
        }
        .padding(.horizontal, 16)
    }

    private func bar(_ label: String, percent: Int32, color: Color) -> some View {
        VStack(spacing: 6) {
            HStack {
                Text(label).font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                Spacer()
                Text("\(Int(percent))%").font(.pretendard(size: 12, weight: .bold))
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(hex: 0xFFEDEFF3))
                    Capsule().fill(color)
                        .frame(width: max(0, min(1, Double(Int(percent)) / 100)) * geo.size.width)
                }
            }
            .frame(height: 7)
        }
    }

    /**
     히어로 하단 사실 박스 — 재화 · 뽑기 · 결제일 세 칸.

     금액 아래에 흩어져 있던 값들을 한 판에 모은다. 라벨과 값을 위아래로 쌓아 두면
     "무엇의 숫자인가"를 읽는 데 시선이 덜 든다.

     재화 정보가 없는 지출(월정액·패스처럼 개수가 없는 상품)에서는 두 칸이 비어 판이 헐거워지므로,
     그때는 박스를 접고 날짜만 한 줄로 둔다.
     */
    @ViewBuilder
    private func heroFacts(_ s: Spending, ink: Color) -> some View {
        let amt = GameDataKt.currencyAmountOrNull(gameName: s.gameName, itemName: s.itemName)
        let pulls = GameDataKt.currencyPullsOrNull(gameName: s.gameName, itemName: s.itemName)
        // 재화 개수를 못 구하는 상품(월정액·패스처럼 개수가 없는 것)이 흔하다. 그때 박스를 통째로
        // 감췄더니 **대부분의 지출에서 박스가 안 보였다** — 칸의 내용을 바꿔서라도 판은 유지한다.
        let cells: [(String, String)] = {
            if let amt {
                let parts = splitCurrency(amt)
                return [parts, ("뽑기", pulls.map(shortPulls) ?? "—")]
            }
            return [("구분", s.isSubscription ? "정기" : "일반"),
                    ("결제", s.paymentMethod.isEmpty ? "—" : s.paymentMethod)]
        }()

        HStack(spacing: 0) {
            factCell(cells[0].0, cells[0].1, ink: ink)
            factDivider(ink)
            factCell(cells[1].0, cells[1].1, ink: ink)
            factDivider(ink)
            factCell("결제일", DateUtil.shared.shortDate(millis: s.dateMillis), ink: ink)
        }
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func factCell(_ label: String, _ value: String, ink: Color) -> some View {
        VStack(spacing: 3) {
            Text(label)
                .font(.pretendard(size: 10.5))
                .foregroundStyle(ink.opacity(0.62))
            Text(value)
                .font(.pretendard(size: 13, weight: .bold))
                .foregroundStyle(ink)
                .lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
    }

    private func factDivider(_ ink: Color) -> some View {
        Rectangle().fill(ink.opacity(0.14)).frame(width: 1, height: 24)
    }

    /// "원석 12,960" → ("원석", "12,960"). 마지막 공백을 기준으로 가른다 —
    /// 재화명에 공백이 들어가는 상품이 있어(예: "오리지늄 세트") 앞쪽은 통째로 이름으로 둔다.
    private func splitCurrency(_ label: String) -> (String, String) {
        guard let idx = label.lastIndex(of: " ") else { return ("재화", label) }
        return (String(label[label.startIndex..<idx]), String(label[label.index(after: idx)...]))
    }

    /// "약 81뽑 가능" → "약 81뽑". 칸이 좁아 뒤의 '가능'은 뺀다(라벨이 이미 '뽑기'다).
    private func shortPulls(_ text: String) -> String {
        text.replacingOccurrences(of: " 가능", with: "")
    }

    /// 히어로 우측 상단 표식 — 영문 정식 명칭(GENSHIN IMPACT · HONKAI: STAR RAIL …).
    /// 값은 공유 모델(`Game.englishName`)이 들고 있다 — Android 포팅 때 같은 값을 쓴다.
    private func gameCode(_ gameName: String) -> String {
        GameData.shared.byName(name: gameName).englishName
    }

    /**
     같은 항목을 산 이력 — 횟수·누적·평균 간격 + 목록.

     1건뿐이면 **카드를 통째로 감춘다**. "1번 샀어요"는 알려 줄 값어치가 없고,
     빈 카드를 남기면 화면만 길어진다.
     */
    @ViewBuilder
    private func sameItemCard(_ s: Spending) -> some View {
        if let h = SpendingDetailStats.shared.sameItemHistory(target: s, all: store.spendings), h.count > 1 {
            GLGCard(cornerRadius: 24, padding: 20) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("같은 항목을 산 적")
                        .font(.pretendard(size: 11.5, weight: .bold))
                        .foregroundStyle(GLGColor.textSecondary)
                        .padding(.bottom, 12)
                    ForEach(Array(h.entries.prefix(5).enumerated()), id: \.offset) { i, e in
                        if i > 0 { Divider() }
                        HStack {
                            Text(DateUtil.shared.shortDate(millis: e.dateMillis))
                                .font(.pretendard(size: 12.5, weight: e.id == s.id ? .bold : .regular))
                                .foregroundStyle(e.id == s.id ? GLGColor.textPrimary : GLGColor.textSecondary)
                            Spacer()
                            Text(won(e.amount))
                                .font(.pretendard(size: 12.5, weight: e.id == s.id ? .bold : .medium))
                            if e.id == s.id {
                                Text("이번").font(.pretendard(size: 10, weight: .bold))
                                    .foregroundStyle(Color(argb64: s.gameColor))
                            }
                        }
                        .padding(.vertical, 9)
                    }
                    // A안처럼 요약은 목록 **아래** 한 줄로 — 통계 박스를 위에 세우면
                    // 정작 읽어야 할 날짜·금액보다 눈이 먼저 그리로 간다.
                    Divider()
                    HStack(spacing: 4) {
                        Text("\(h.ordinal)번째").font(.pretendard(size: 12, weight: .bold))
                        Text("· 누적 \(won(h.totalAmount))").font(.pretendard(size: 12))
                        if let d = h.averageIntervalDays?.intValue {
                            Text("· 평균 \(d)일 간격").font(.pretendard(size: 12))
                        }
                    }
                    .foregroundStyle(GLGColor.textSecondary)
                    .padding(.top, 11)
                }
            }
            .padding(.horizontal, 16)
        }
    }


    private func detailRow(_ label: String, _ value: String, sub: String? = nil) -> some View {
        HStack(alignment: .top) {
            Text(label).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).frame(width: 80, alignment: .leading)
            Spacer(minLength: 12)
            VStack(alignment: .trailing, spacing: 2) {
                Text(value).font(.pretendard(size: 14, weight: .medium)).multilineTextAlignment(.trailing)
                // 재화양 아래 작게 — 환산 뽑기 수.
                if let sub { Text(sub).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary) }
            }
        }
        .padding(.vertical, 12)
    }
}
