import SwiftUI
import Shared

// 지출 상세 — 전체 정보 + 수정/삭제. (Compose SpendingDetailScreen 대응)
struct SpendingDetailView: View {
    var store: SpendingStore
    let spendingId: String
    let onEdit: (Spending) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent
    @State private var confirmDelete = false

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
        .modifier(GLGHiddenToolbarBackground())
        // 그라데이션이 짙어 뒤로가기·수정·삭제 아이콘이 묻힌다 — 상단만 밝은 요소로 뒤집는다.
        .toolbarColorScheme(.dark, for: .navigationBar)
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
                // 같은 항목 또 기록 — 가챠 상품 재구매는 흔한 흐름인데 지금은 처음부터 다시 입력해야 했다.
                Button { onEdit(prefillTemplate(s)) } label: {
                    Text("같은 항목 또 기록")
                        .font(.pretendard(size: 14, weight: .bold))
                        .frame(maxWidth: .infinity).frame(height: 48)
                        .foregroundStyle(.white)
                        .background(accent.primary, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                Color.clear.frame(height: 24)
            }
            .padding(.bottom, 8)
        }
        .scrollIndicators(.hidden)
        // 히어로 색이 상태바까지 이어지도록 스크롤 영역을 위로 올린다. 대신 히어로가
        // `topInset` 만큼 스스로 내려가므로 글자는 안전 영역 안에 남는다.
        .ignoresSafeArea(.container, edges: .top)
        .alert("이 지출을 삭제할까요?", isPresented: $confirmDelete) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("삭제", role: .destructive) { store.deleteSpending(s.id); dismiss() }.glgAlertTint()
        } message: { Text("삭제하면 되돌릴 수 없어요.") }
        // 수정·삭제를 네비 헤더 우측 아이콘으로. 각각 별도 ToolbarItem + ToolbarSpacer 로 분리 —
        // iOS 26 은 인접 툴바 아이템을 하나의 글래스 캡슐로 묶으므로 스페이서로 갈라 독립 원형 버튼으로.
        .toolbar {
            // 수정 시 상세페이지를 닫지 않음 — 편집 시트를 위에 띄우고, 닫으면 상세로 복귀(갱신 내용 표시)
            ToolbarItem(placement: .topBarTrailing) {
                Button { onEdit(s) } label: { Image(systemName: "pencil") }
            }
            if #available(iOS 26.0, *) {
                ToolbarSpacer(.fixed, placement: .topBarTrailing)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(role: .destructive) { confirmDelete = true } label: { Image(systemName: "trash") }
            }
        }
    }

    /**
     히어로 — 금액·재화 환산·비중을 한 덩어리로.

     **배경만 상태바까지 올린다**(`ignoresSafeArea(edges: .top)`). 콘텐츠까지 올리면 글자가
     상태바와 겹치므로, 색은 위로 이어지고 텍스트는 안전 영역 안에 남는 구조다.
     네비게이션 바는 배경을 숨기고(`toolbarBackground(.hidden)`) 아이콘만 밝게 뒤집었다.
     */
    @ViewBuilder
    private func hero(_ s: Spending, topInset: CGFloat) -> some View {
        let base = Color(argb64: s.gameColor)

        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(s.gameName).font(.pretendard(size: 14, weight: .bold))
                Text(s.isSubscription ? "정기" : "일반")
                    .font(.pretendard(size: 10, weight: .bold))
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(Color.white.opacity(0.22), in: Capsule())
            }
            .foregroundStyle(.white.opacity(0.95))

            Text(won(s.amount))
                .font(.pretendard(size: 36, weight: .bold))
                .foregroundStyle(.white)
                .padding(.top, 12)

            // 재화 환산을 금액 바로 아래로 — 가챠 앱에서 이 둘은 한 쌍이다(예전엔 상세 7행 중 2번째였다).
            if let amt = GameDataKt.currencyAmountOrNull(gameName: s.gameName, itemName: s.itemName) {
                let pulls = GameDataKt.currencyPullsOrNull(gameName: s.gameName, itemName: s.itemName)
                Text(pulls == nil ? amt : "\(amt) · \(pulls!)")
                    .font(.pretendard(size: 14, weight: .bold))
                    .foregroundStyle(.white.opacity(0.96))
                    .padding(.top, 3)
            }

            Text(heroSubtitle(s))
                .font(.pretendard(size: 12))
                .foregroundStyle(.white.opacity(0.82))
                .padding(.top, 9)

        }
        .padding(.horizontal, 20)
        .padding(.bottom, 20)
        // 상태바(topInset) + 네비게이션 바(44) 만큼 내려서 시작한다.
        // 스크롤 영역이 위로 올라가 있으므로 이 패딩이 없으면 글자가 상태바에 잘린다.
        .padding(.top, topInset + 44)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            UnevenRoundedRectangle(bottomLeadingRadius: 28, bottomTrailingRadius: 28, style: .continuous)
                .fill(LinearGradient(
                    colors: [base, base.mix(with: .black, by: 0.30)],
                    startPoint: .topLeading, endPoint: .bottomTrailing))
        )
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

    /// 히어로 부제 — 날짜만. 결제 수단·플랫폼은 아래 상세 카드가 맡는다(중복 표시 방지).
    private func heroSubtitle(_ s: Spending) -> String { s.dateLabel }

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

    /// '같은 항목 또 기록' 용 프리필 — 금액·항목·결제 경로는 잇고 **날짜는 오늘**, id 는 새로 받는다.
    private func prefillTemplate(_ s: Spending) -> Spending {
        Spending(
            id: "", gameName: s.gameName, amount: s.amount, dateMillis: nowMs(),
            paymentMethod: s.paymentMethod, chargePlatform: s.chargePlatform,
            itemName: s.itemName, memo: "", tags: s.tags,
            isSubscription: s.isSubscription, gameColor: s.gameColor)
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
