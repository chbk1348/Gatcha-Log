//
//  HoyolandShopSection.swift
//  GL_IOS
//
// ── 호요랜드 하위 페이지 세 장 — 굿즈 목록 · 장바구니 · 부스 체험 ─────────────
//
// 상세(HoyolandDetailView) 본문에 목록으로 펼치면 무대 시간표만큼 길어져 이 페이지의
// 본론(언제·어디서)을 밀어낸다. 현장에서 **돈과 시간을 쓰는 두 가지**라 각각 페이지를 준다.
//
// 목업: `Gatcha Log MD/design_hoyoland_goods_mockup.html` — 굿즈 목록 A 안 · 장바구니 D 안.
// (Android `HoyolandGoodsContent`·`HoyolandCartContent` 와 파리티)

import SwiftUI
import Shared

/// 목업 색 — 장바구니 줄 바탕과 '가격 미정' 안내.
private let GLGCartRowBg = Color(hex: 0xFFF7F8FA)
private let GLGWarnBg = Color(hex: 0xFFFFF6E0)
private let GLGWarnText = Color(hex: 0xFF8A6A1E)
private let GLGTextThird = Color(hex: 0xFF98A0AB)
private let GLGDanger = Color(hex: 0xFFD8574A)
/// 보상 면 — **게임색이 아니라 한 가지 색으로 통일한다.** 부스 목록을 훑을 때 "받는 게 있는 곳" 이
/// 한눈에 걸려야 하는데, 게임색을 쓰면 그 줄이 게임 배지와 섞여 보상인지 소속인지 흐려진다.
private let GLGGiftText = Color(hex: 0xFFE0557B)
private let GLGGiftBg = Color(hex: 0x14E0557B)

/**
 굿즈 목록 — 품목과 **가격**.

 이 앱은 지출을 다루는 앱이라, 굿즈 목록의 본론은 "얼마 들고 가야 하나"다. 그래서
 ① 맨 위에 가격대를 세우고 ② 행을 눌러 담으면 ③ 하단 고정 바가 합계를 계속 말한다.

 **싣는 굿즈는 앱이 다루는 세 게임 + 행사 공용뿐이다**(`HoyolandEvent.visibleGoods`).
 */
struct HoyolandGoodsView: View {
    let event: HoyolandEvent
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var gameFilter: String? = nil
    @State private var showCart = false

    var body: some View {
        let all = event.visibleGoods
        let games = event.goodsGames
        let cart = store.hoyolandCart
        let shown = all.filter { gameFilter == nil || $0.game == gameFilter }

        ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if all.isEmpty {
                        emptyCard
                    } else {
                        priceRangeCard
                        if games.count > 1 {
                            GLGSegmentedTabs(
                                labels: ["전체"] + games.map { event.stageLabel(game: $0) },
                                selectedColors: [accent.primary] + games.map { gameColor($0) },
                                selection: Binding(
                                    get: { gameFilter.flatMap { games.firstIndex(of: $0).map { $0 + 1 } } ?? 0 },
                                    set: { gameFilter = $0 == 0 ? nil : games[$0 - 1] }
                                )
                            )
                            .padding(.top, 12)
                        }
                        ForEach(Array(shown.enumerated()), id: \.offset) { _, item in
                            GLGCard(cornerRadius: 24, padding: 0) {
                                goodsCard(item, quantity: Int(cart.quantityOf(name: item.name)))
                            }
                            .padding(.top, 10)
                        }
                    }
                    Color.clear.frame(height: 24)
                }
                .padding(.horizontal, 16)
                .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        // 하단 바 — 지출 선택 모드와 **같은 규격**(safeAreaInset + SystemGlassBar).
        //
        // ⚠️ `ToolbarItem(placement: .bottomBar)` 는 쓸 수 없다. 이 앱은 하위 화면에서도
        // 탭바를 계속 띄우므로(`ContentView.tabBarVisibility`) 하단이 이미 차 있어 그 툴바가
        // 나타나지 않는다(2026-09-10 실기기 확인). 이 페이지만 탭바를 숨기는 것도 안 된다 —
        // pop 될 때 탭바가 튀어나오고 push 애니메이션이 사라진다.
        //
        // overlay 가 아니라 safeAreaInset 인 이유: overlay 는 콘텐츠를 안 밀어 마지막 굿즈가
        // 바에 가려 눌리지 않는다(지출 목록에서 같은 문제를 겪고 고친 자리다).
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if !cart.isEmpty {
                goodsBar(cart).transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(GLGMotion.standard(), value: cart.isEmpty)
        // 하단 바가 떠 있는 동안만 '추가' FAB 를 감춘다 — 자리가 겹쳐 「장바구니」 버튼이
        // '+' 에 가린다(iOS 18~25 는 FAB 가 TabView 바깥 오버레이라 그냥 두면 덮는다).
        // 담은 게 없으면 바가 없으므로 FAB 는 그대로 둔다.
        .onAppear { store.hidesAddButton = !cart.isEmpty }
        .onDisappear { store.hidesAddButton = false }
        .onChange(of: cart.isEmpty) { _, empty in store.hidesAddButton = !empty }
        .background(GLGBackground { Color.clear })
        .glgPageTitle("굿즈 목록")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $showCart) {
            HoyolandCartView(event: event, store: store)
        }
    }

    // ── 가격대 — 목록보다 먼저. 얼마를 들고 갈지가 첫 질문이다.
    private var priceRangeCard: some View {
        let range = event.goodsPriceRange()
        return GLGCard(cornerRadius: 24, padding: 0) {
            HStack(alignment: .bottom, spacing: 8) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("가격대").font(.pretendard(size: 11.5, weight: .bold))
                        .foregroundStyle(GLGColor.textSecondary)
                    Text(range.components(separatedBy: " · ").first ?? range)
                        .font(.pretendard(size: 16, weight: .black)).monospacedDigit()
                        .foregroundStyle(GLGColor.textPrimary)
                }
                Spacer(minLength: 0)
                Text(range.components(separatedBy: " · ").dropFirst().joined(separator: " · "))
                    .font(.pretendard(size: 11)).foregroundStyle(GLGTextThird)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
        }
    }

    /**
     굿즈 한 장 — [썸네일 48 · 이름·갈래 · 가격/수량] + 구매 제한 띠.

     한 장짜리 카드에 줄을 Divider 로 쌓다가 **품목당 카드**로 갈아탔다. 줄 목록은 훑기엔 좋지만
     구매 제한("1인 5개 한정")을 놓을 자리가 없다 — 갈래 옆 회색 줄에 묻으면 현장에서 못 보고
     계산대에서 되돌아온다. 카드 아래를 띠 한 줄로 비워 그 조건만 세운다.

     수량은 **목록에서 바로** 정한다 — 같은 키링을 두 개 사는 일이 흔한데 담기 토글만 있으면
     장바구니까지 들어가야 했다. 담기 전에는 「담기」 버튼, 담은 뒤에는 스테퍼로 바뀐다.
     */
    @ViewBuilder private func goodsCard(_ item: HoyolandGoods, quantity: Int) -> some View {
        let c = gameColor(item.game)
        let label = item.game.isEmpty ? "공용" : event.stageLabel(game: item.game)
        // 갈래(분류)는 싣지 않는다 — '아크릴 스탠드' 처럼 이름과 거의 같은 말이 한 줄 아래 또
        // 나오고, 고를 때 실제로 쓰이는 값은 가격과 한정 여부다. 시리즈·구매 제한은 아래 띠로 뺀다.
        let meta = item.noteRest
        VStack(spacing: 0) {
            HStack(spacing: 0) {
            // 썸네일 자리 — 공식 굿즈 이미지가 나오면 이 칸을 그대로 이미지로 바꾼다.
            Text(label)
                .font(.pretendard(size: 9.5, weight: .black))
                .foregroundStyle(c)
                .multilineTextAlignment(.center).lineLimit(2)
                .padding(.horizontal, 3)
                .frame(width: 48, height: 48)
                .background(c.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(item.name).font(.pretendard(size: 13, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                // 게임 라벨은 왼쪽 48 칸이 이미 말하고 있다 — 여기 칩까지 두면 한 줄에 같은
                // 글자가 두 번 나온다. 갈래·비고만 남긴다.
                if !meta.isEmpty {
                    // 구성품이 긴 품목(테마 패키지)은 note 안에 줄바꿈이 들어 있어 두 줄이 된다.
                    Text(meta).font(.pretendard(size: 11))
                        .foregroundStyle(c)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.leading, 11)
            Spacer(minLength: 11)
            VStack(alignment: .trailing, spacing: 6) {
                Text(item.price > 0 ? event.wonLabel(v: item.price) : "미정")
                    .font(.pretendard(size: 13, weight: item.price > 0 ? .black : .bold)).monospacedDigit()
                    .foregroundStyle(item.price > 0 ? GLGColor.textPrimary : GLGTextThird)
                // 「담기」 ↔ 스테퍼 전환. 값만 갈아 끼우면 버튼이 있던 자리에 스테퍼가 **툭 나타나서**
                // 내가 누른 것이 반영된 것인지, 원래 그랬던 것인지 순간 헷갈린다. 담을 때도 뺄 때도
                // 같은 전환을 태워 "이게 방금 내가 만든 변화" 라는 것을 보이게 한다.
                // 두 상태의 높이가 26 으로 같아 전환 중에도 줄이 흔들리지 않는다.
                Group {
                    if quantity <= 0 {
                        addButton("담기") { store.setGoodsQuantity(item.name, 1) }
                            .transition(.opacity.combined(with: .scale(scale: 0.9)))
                    } else {
                        HStack(spacing: 0) {
                            stepButton("−") { store.setGoodsQuantity(item.name, quantity - 1) }
                            Text("\(quantity)")
                                .font(.pretendard(size: 12, weight: .black)).foregroundStyle(GLGColor.textPrimary)
                                .frame(width: 30)
                            stepButton("+") { store.setGoodsQuantity(item.name, quantity + 1) }
                        }
                        .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .stroke(.black.opacity(0.10), lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                        .transition(.opacity.combined(with: .scale(scale: 0.9)))
                    }
                }
                .animation(GLGMotion.standard(), value: quantity > 0)
            }
            }
            .padding(.horizontal, 16).padding(.vertical, 13)
            // 행사 한정 조건 띠 — 카드 폭을 꽉 채운 한 줄. 사기 전에 걸리는 값이라 '가격 미정'
            // 안내와 같은 경고색을 쓴다. 둘 다 없는 품목은 띠 자체를 세우지 않는다
            // (전부 붙이면 눈이 거른다).
            //
            // '호요랜드2026 시리즈' 는 **이 행사에서만 파는 물건**이라는 뜻이라 배지로 뺀다.
            // 상설 굿즈는 다음에 사면 되지만 이건 놓치면 끝이고, 그 판단이 갈래 옆 회색 줄에
            // 묻혀 있었다.
            if !item.limitLabel.isEmpty || !item.seriesLabel.isEmpty {
                HStack(spacing: 7) {
                    if !item.seriesLabel.isEmpty {
                        Text(item.seriesLabel)
                            .font(.pretendard(size: 10, weight: .black))
                            .foregroundStyle(GLGWarnText)
                            .padding(.horizontal, 7).padding(.vertical, 2)
                            .background(GLGWarnText.opacity(0.14), in: Capsule())
                    }
                    if !item.limitLabel.isEmpty {
                        Text(item.limitLabel)
                            .font(.pretendard(size: 11, weight: .bold))
                            .foregroundStyle(GLGWarnText)
                    }
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(GLGWarnBg)
            }
        }
        // 띠가 카드 아래 모서리에 그대로 닿는다. `glgGlass` 는 배경과 테두리만 둥글게 그리고
        // **자식을 자르지 않으므로**(Android `GlassCard` 는 .clip 이 있어 이 문제가 없다),
        // 여기서 카드와 같은 반경으로 잘라 줘야 띠의 각진 모서리가 삐져나오지 않는다.
        // 반경 24 는 이 카드를 세우는 GLGCard(cornerRadius: 24) 와 같아야 한다.
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    /// 담기 버튼 — 스테퍼와 같은 높이라 담기 전후로 줄 높이가 흔들리지 않는다.
    @ViewBuilder private func addButton(_ label: String, _ onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            Text(label).font(.pretendard(size: 11.5, weight: .bold))
                .foregroundStyle(accent.primary)
                .padding(.horizontal, 12)
                .frame(height: 26)
                .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .stroke(accent.primary, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private func stepButton(_ label: String, _ onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            Text(label).font(.pretendard(size: 14, weight: .bold))
                .foregroundStyle(GLGColor.textSecondary)
                .frame(width: 28, height: 26)
                .background(GLGCartRowBg)
        }
        .buttonStyle(.plain)
    }

    /**
     하단 고정 바 — 담은 종수·개수와 **합계**, 탭하면 장바구니.
     스크롤과 무관하게 늘 보여야 한다. 지금까지 고른 결과가 곧 이 화면의 답이다.
     */
    @ViewBuilder private func goodsBar(_ cart: HoyolandCart) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text("담은 \(cart.kindCount)종 · \(cart.totalCount)개")
                    .font(.pretendard(size: 11.5, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary)
                Text(event.wonLabel(v: event.cartTotal(cart: cart)))
                    .font(.pretendard(size: 17, weight: .black)).monospacedDigit()
                    .foregroundStyle(GLGColor.textPrimary)
            }
            Spacer(minLength: 8)
            Button("장바구니") { showCart = true }
                .buttonStyle(.borderedProminent).tint(accent.primary)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        // 시스템 글래스(iOS26 Liquid Glass, 폴백 ultraThinMaterial) — 떠 있는 라운드 바.
        .modifier(SystemGlassBar())
        .padding(.horizontal, 16)
        // 비켜나는 여백을 두지 않는다 — 바가 뜨는 동안 FAB 자체를 감추기 때문이다.
        // (iOS 18~25 는 FAB 가 TabView 바깥 오버레이라 그냥 두면 바를 덮는다)
        .padding(.bottom, 8)
    }

    private func gameColor(_ game: String) -> Color {
        let raw = event.stageColor(game: game)
        return raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
    }

    private var emptyCard: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 5) {
                Text("판매 목록은 아직 공개 전이에요")
                    .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("품목과 가격이 나오면 이 자리에 채워져요.\n지난 행사는 개막 1~2주 전에 나왔어요.")
                    .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/**
 장바구니 — **게임별 묶음**.

 현장에서는 게임 부스를 하나씩 돈다. 게임별로 묶고 소계를 붙이면 "원신 부스에서 얼마" 가
 보인다. 줄이 촘촘해 수량 스테퍼를 늘 띄우지 않고, **줄을 누르면 그 줄에서 펼친다.**

 결제 버튼은 두지 않는다 — 현장 판매라 앱이 낄 자리가 없다. 여기서 하는 일은 예산 가늠이다.
 */
struct HoyolandCartView: View {
    let event: HoyolandEvent
    var store: SpendingStore
    @State private var expanded: String? = nil

    var body: some View {
        let cart = store.hoyolandCart
        let groups = event.cartGroups(cart: cart)
        let total = event.cartTotal(cart: cart)
        let unpriced = Int(event.cartUnpricedCount(cart: cart))

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if groups.isEmpty {
                    VStack(spacing: 5) {
                        Text("담은 굿즈가 없어요")
                            .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        Text("굿즈 목록에서 사고 싶은 것을 담으면\n여기서 예상 지출을 볼 수 있어요.")
                            .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 52).padding(.bottom, 20)
                } else {
                    summaryCard(cart, total: total, unpriced: unpriced)
                    ForEach(Array(groups.enumerated()), id: \.offset) { _, g in
                        groupHeader(g)
                        ForEach(Array(g.lines.enumerated()), id: \.offset) { _, line in
                            cartRow(line)
                        }
                    }
                    if unpriced > 0 {
                        HStack(alignment: .top, spacing: 7) {
                            Text("⚠️").font(.pretendard(size: 11))
                            Text("가격 미정 \(unpriced)종은 합계에 없어요. 값이 공개되면 자동으로 더해져요.")
                                .font(.pretendard(size: 11)).foregroundStyle(GLGWarnText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(GLGWarnBg, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .padding(.top, 10)
                    }
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("장바구니")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // 비우기는 헤더 우측 — 실수로 누르기 어려운 자리다.
            if !store.hoyolandCart.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { store.clearGoodsCart() } label: {
                        Text("비우기").font(.pretendard(size: 12, weight: .bold))
                            .foregroundStyle(GLGDanger)
                    }
                }
            }
        }
    }

    // ── 합계 — 이 페이지의 답이라 맨 위에 둔다.
    @ViewBuilder private func summaryCard(_ cart: HoyolandCart, total: Int32, unpriced: Int) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text("담은 굿즈").font(.pretendard(size: 12.5)).foregroundStyle(.white.opacity(0.72))
                Spacer(minLength: 8)
                Text("\(cart.kindCount)종 · \(cart.totalCount)개")
                    .font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(.white)
            }
            if unpriced > 0 {
                HStack {
                    Text("가격 미정").font(.pretendard(size: 12.5)).foregroundStyle(.white.opacity(0.72))
                    Spacer(minLength: 8)
                    Text("\(unpriced)종").font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(.white)
                }
                .padding(.top, 9)
            }
            Rectangle().fill(.white.opacity(0.18)).frame(height: 1).padding(.vertical, 11)
            HStack {
                Text("예상 지출").font(.pretendard(size: 13)).foregroundStyle(.white.opacity(0.80))
                Spacer(minLength: 8)
                Text(event.wonLabel(v: total))
                    .font(.pretendard(size: 20, weight: .black)).monospacedDigit()
                    .foregroundStyle(.white)
            }
        }
        .padding(16)
        .background(GLGColor.textPrimary, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    @ViewBuilder private func groupHeader(_ g: HoyolandCartGroup) -> some View {
        let raw = event.stageColor(game: g.game)
        let c = raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
        HStack(spacing: 7) {
            Text(g.game.isEmpty ? "공용" : event.stageLabel(game: g.game))
                .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(c)
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background(c.opacity(0.14), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            Rectangle().fill(.black.opacity(0.06)).frame(height: 1)
            // 게임별 소계가 **부스에서 꺼낼 금액**이다.
            Text(g.allUnpriced ? "미정" : event.wonLabel(v: g.subtotal))
                .font(.pretendard(size: 11.5, weight: .black)).monospacedDigit()
                .foregroundStyle(g.allUnpriced ? GLGTextThird : GLGColor.textSecondary)
        }
        .padding(.top, 16).padding(.bottom, 8)
    }

    /**
     장바구니 한 줄 — 접힌 기본 모습은 [이름 · ×수량 · 소계].
     누르면 그 줄에서 수량 스테퍼가 펼쳐진다(줄이 촘촘해 늘 띄우면 목록이 읽히지 않는다).
     */
    @ViewBuilder private func cartRow(_ line: HoyolandCartLine) -> some View {
        let name = line.goods.name
        let isOpen = expanded == name
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Text(name).font(.pretendard(size: 12.5, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Text("×\(line.quantity)")
                    .font(.pretendard(size: 11, weight: .black))
                    .foregroundStyle(GLGColor.textSecondary).padding(.trailing, 9)
                Text(line.goods.price > 0 ? event.wonLabel(v: line.subtotal) : "—")
                    .font(.pretendard(size: 12.5, weight: .black)).monospacedDigit()
                    .foregroundStyle(line.goods.price > 0 ? GLGColor.textPrimary : GLGTextThird)
            }
            if isOpen {
                HStack(spacing: 0) {
                    stepButton("−") { store.setGoodsQuantity(name, Int(line.quantity) - 1) }
                    Text("\(line.quantity)")
                        .font(.pretendard(size: 12.5, weight: .black)).foregroundStyle(GLGColor.textPrimary)
                        .frame(width: 40)
                    stepButton("+") { store.setGoodsQuantity(name, Int(line.quantity) + 1) }
                    Spacer(minLength: 0)
                    Button { store.setGoodsQuantity(name, 0) } label: {
                        Text("빼기").font(.pretendard(size: 11.5, weight: .bold))
                            .foregroundStyle(GLGDanger)
                            .padding(.horizontal, 10).padding(.vertical, 5)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 10)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(GLGCartRowBg, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .padding(.bottom, 7)
        .contentShape(Rectangle())
        .onTapGesture { withAnimation(.easeInOut(duration: 0.18)) { expanded = isOpen ? nil : name } }
    }

    @ViewBuilder private func stepButton(_ label: String, _ onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            Text(label).font(.pretendard(size: 14, weight: .bold))
                .foregroundStyle(GLGColor.textSecondary)
                .frame(width: 30, height: 26)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous).stroke(.black.opacity(0.06), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/**
 게임별 부스 체험.

 무대와 달리 **시각이 없다** — 상시 운영이라 시간표에 얹을 것이 없다. 그래서
 시간표가 아니라 게임별 카드로 그린다.

 예약제도 정원 · 회차도 다루지 않는다 — 공지된 정보를 그대로 보여줄 뿐이다. 그래서 카드에
 카드가 내는 값은 **참가비 · 보상 · 설명** 셋이고, 그중 **보상을 주인공으로 세운다**(목업 C안):
 예약도 정원도 없는 마당에 부스를 고르는 기준은 결국 받는 것이라서다.
 */
struct HoyolandBoothView: View {
    let event: HoyolandEvent
    @Environment(\.glgAccent) private var accent
    @State private var gameFilter: String? = nil

    var body: some View {
        let games = event.boothGames
        let shown = event.booths.filter { gameFilter == nil || $0.game == gameFilter }

        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if event.booths.isEmpty {
                    GLGCard(cornerRadius: 24, padding: 16) {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("부스 정보는 아직 공개 전이에요")
                                .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("게임별 체험존과 위치가 나오면 이 자리에 채워져요.\n부스 배치도는 보통 개막 직전에 나와요.")
                                .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                } else {
                    // 굿즈 목록과 같은 게임 탭 — 두 화면을 오갈 때 거르는 방법이 달라지면 손이 헷갈린다.
                    if games.count > 1 {
                        GLGSegmentedTabs(
                            labels: ["전체"] + games.map { event.stageLabel(game: $0) },
                            selectedColors: [accent.primary] + games.map { boothColor($0) },
                            selection: Binding(
                                get: { gameFilter.flatMap { games.firstIndex(of: $0).map { $0 + 1 } } ?? 0 },
                                set: { gameFilter = $0 == 0 ? nil : games[$0 - 1] }
                            )
                        )
                    }
                    ForEach(Array(shown.enumerated()), id: \.offset) { _, b in
                        boothCard(b)
                    }
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("부스 체험")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder private func boothCard(_ b: HoyolandBooth) -> some View {
        let c = boothColor(b.game)
        GLGCard(cornerRadius: 24, padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text(event.stageLabel(game: b.game))
                        .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(c)
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .background(c.opacity(0.14), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    Text(b.title).font(.pretendard(size: 14.5, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 6)
                    // 참가비는 제목 줄 오른쪽. 유료 체험존은 회차마다 값이 다르고 무료 부스와
                    // 섞여 있어서, 설명을 읽기 전에 먼저 갈려야 하는 값이다.
                    //
                    // **유료 쪽을 더 세게 칠한다.** 예전에는 무료가 분홍 알약이고 유료는 먹색이라,
                    // 지출을 다루는 앱에서 정작 돈이 드는 칸이 덜 보였다. 유료는 게임색을 꽉 채우고
                    // 흰 글자를 얹고, 무료는 테두리만 남겨 물러세운다.
                    if b.isPaid {
                        Text(event.wonLabel(v: b.price))
                            .font(.pretendard(size: 13, weight: .black)).monospacedDigit()
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(c, in: Capsule())
                            .layoutPriority(1)
                    } else {
                        // 무료는 유료와 **같은 알약 규격**을 쓰되 색을 뺀다. 색을 '돈이 든다' 에만
                        // 쓰면 목록을 훑을 때 유채색 칸만 세면 된다. 분홍은 보상 띠가 가져간다.
                        Text("무료")
                            .font(.pretendard(size: 12, weight: .black))
                            .foregroundStyle(GLGColor.textSecondary)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(GLGTextThird.opacity(0.16), in: Capsule())
                            .layoutPriority(1)
                    }
                }
                .padding(.horizontal, 14).padding(.top, 13).padding(.bottom, 11)
                // 보상은 부스를 고르는 기준이라 카드의 **주인공 자리**를 준다 — 폭을 꽉 채운 한 면.
                if !b.reward.isEmpty {
                    HStack(spacing: 9) {
                        Image(systemName: "gift").font(.system(size: 14, weight: .semibold))
                        Text(b.reward).font(.pretendard(size: 12.5, weight: .bold))
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    .foregroundStyle(GLGGiftText)
                    .padding(.horizontal, 14).padding(.vertical, 11)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(GLGGiftBg)
                } else {
                    // 빈칸으로 두면 **값이 빠진 것처럼** 읽힌다 — 없다고 적는다.
                    Text("받는 것 없음").font(.pretendard(size: 12))
                        .foregroundStyle(GLGTextThird)
                        .padding(.horizontal, 14).padding(.bottom, 11)
                }
                // 설명이 카드 아래 한 면을 통째로 쓴다. 예전엔 제목 밑 회색 한 줄이었고 이 자리에는
                // '구분'(무료/유료 체험존)이 있었는데, 무료·유료는 **우상단 배지가 이미 말한다** —
                // 같은 걸 두 번 적느라 정작 무엇을 하는 체험인지가 눌려 있었다. 자리를 맞바꾼다.
                if !b.desc.isEmpty {
                    Divider()
                    Text(b.desc)
                        .font(.pretendard(size: 13))
                        .foregroundStyle(GLGColor.textSecondary)
                        .lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14).padding(.vertical, 13)
                }
            }
        }
    }

    private func boothColor(_ game: String) -> Color {
        let raw = event.stageColor(game: game)
        return raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
    }
}
