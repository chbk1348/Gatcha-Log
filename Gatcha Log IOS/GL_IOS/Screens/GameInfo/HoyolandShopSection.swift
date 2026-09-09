//
//  HoyolandShopSection.swift
//  GL_IOS
//
// ── 호요랜드 하위 페이지 두 장 — 굿즈샵 · 부스 체험 ─────────────────────────
//
// 상세(HoyolandDetailView) 본문에 목록으로 펼치면 무대 시간표만큼 길어져 이 페이지의
// 본론(언제·어디서)을 밀어낸다. 현장에서 **돈과 시간을 쓰는 두 가지**라 각각 페이지를 준다.
// (Android `HoyolandGoodsContent`·`HoyolandBoothContent` 와 파리티)

import SwiftUI
import Shared

/**
 굿즈샵 — 품목과 **가격**.

 이 앱은 지출을 다루는 앱이라, 굿즈 목록의 본론은 "얼마 들고 가야 하나"다. 그래서
 ① 맨 위에 가격대를 한 줄로 세우고 ② 행을 눌러 **담아 보면 합계**가 아래에 뜬다.
 담은 것은 이 화면 안에서만 산다(저장하지 않는다) — 예산을 가늠하는 계산기지 장바구니가 아니다.
 */
struct HoyolandGoodsView: View {
    let event: HoyolandEvent
    @Environment(\.glgAccent) private var accent
    @State private var gameFilter: String? = nil
    @State private var picked: Set<String> = []

    var body: some View {
        let games = event.goodsGames
        let shown = event.goods.filter { gameFilter == nil || $0.game == gameFilter }
        let total = event.goods.filter { picked.contains($0.name) }.reduce(Int32(0)) { $0 + $1.price }

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if event.goods.isEmpty {
                    emptyCard
                } else {
                    // ── 가격대 — 목록보다 먼저. 얼마를 들고 갈지가 첫 질문이다.
                    GLGCard(cornerRadius: 24, padding: 16) {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("가격대").font(.pretendard(size: 11.5, weight: .bold))
                                .foregroundStyle(GLGColor.textSecondary)
                            Text(event.goodsPriceRange())
                                .font(.pretendard(size: 15, weight: .bold))
                                .foregroundStyle(GLGColor.textPrimary)
                            Spacer(minLength: 0)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    if games.count > 1 {
                        GLGSegmentedTabs(
                            labels: ["전체"] + games.map { event.stageLabel(game: $0) },
                            selectedColors: [accent.primary] + games.map { gameColor($0) },
                            selection: Binding(
                                get: { gameFilter.flatMap { games.firstIndex(of: $0).map { $0 + 1 } } ?? 0 },
                                set: { gameFilter = $0 == 0 ? nil : games[$0 - 1] }
                            )
                        )
                        .padding(.top, 14)
                    }
                    GLGCard(cornerRadius: 24, padding: 0) {
                        VStack(spacing: 0) {
                            ForEach(Array(shown.enumerated()), id: \.offset) { i, item in
                                if i > 0 { Divider() }
                                goodsRow(item)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .padding(.top, 12)

                    // ── 담은 합계 — 고른 게 있을 때만 나타난다. 예산을 가늠하는 자리다.
                    if !picked.isEmpty {
                        HStack {
                            Text("담은 \(picked.count)개").font(.pretendard(size: 12.5, weight: .bold))
                                .foregroundStyle(GLGColor.textPrimary)
                            Spacer(minLength: 8)
                            Text(event.wonLabel(v: total)).font(.pretendard(size: 16, weight: .black))
                                .foregroundStyle(accent.primary)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 13)
                        .background(accent.primary.opacity(0.10),
                                    in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .padding(.top, 12)
                        Text("골라 본 것을 더한 값이에요. 저장되지 않아요.")
                            .font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
                            .padding(.top, 6).padding(.horizontal, 4)
                    }
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("굿즈샵")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func gameColor(_ game: String) -> Color {
        let raw = event.stageColor(game: game)
        return raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
    }

    /// 굿즈 한 줄 — 담기 표시 + 이름·갈래·조건 + 가격.
    @ViewBuilder private func goodsRow(_ item: HoyolandGoods) -> some View {
        let c = gameColor(item.game)
        let isPicked = picked.contains(item.name)
        let meta = [item.game.isEmpty ? nil : event.stageLabel(game: item.game),
                    item.category.isEmpty ? nil : item.category,
                    item.soldOut ? "품절" : nil,
                    item.note.isEmpty ? nil : item.note]
                    .compactMap { $0 }.joined(separator: " · ")
        Button {
            if isPicked { picked.remove(item.name) } else { picked.insert(item.name) }
        } label: {
            HStack(spacing: 0) {
                // 담기 표식 — 체크박스를 따로 두지 않는다. 행 전체가 누를 자리라 원 하나면 충분하다.
                ZStack {
                    Circle().fill(isPicked ? accent.primary : .clear)
                    Circle().stroke(isPicked ? accent.primary : .black.opacity(0.10), lineWidth: 1.5)
                    if isPicked {
                        Image(systemName: "checkmark").font(.system(size: 10, weight: .black))
                            .foregroundStyle(.white)
                    }
                }
                .frame(width: 20, height: 20)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name).font(.pretendard(size: 13, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    if !meta.isEmpty {
                        Text(meta).font(.pretendard(size: 11))
                            .foregroundStyle(item.soldOut ? GLGColor.textSecondary : c)
                    }
                }
                .padding(.leading, 12)
                Spacer(minLength: 10)
                Text(item.price > 0 ? event.wonLabel(v: item.price) : "미정")
                    .font(.pretendard(size: 13, weight: .black)).monospacedDigit()
                    .foregroundStyle(item.price > 0 ? GLGColor.textPrimary : GLGColor.textSecondary)
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .contentShape(Rectangle())
            .opacity(item.soldOut ? 0.45 : 1)
        }
        .buttonStyle(.plain)
        .disabled(item.soldOut)
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
 게임별 부스 체험.

 무대와 달리 **시각이 없다** — 상시 운영이고 대신 줄을 서거나 예약을 잡는다. 그래서
 시간표가 아니라 게임별 카드로 그리고, 현장에서 먼저 찾는 값(위치)을 카드 안에 세운다.
 */
struct HoyolandBoothView: View {
    let event: HoyolandEvent
    @Environment(\.glgAccent) private var accent

    var body: some View {
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
                    ForEach(Array(event.booths.enumerated()), id: \.offset) { _, b in
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
        let raw = event.stageColor(game: b.game)
        let c = raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text(event.stageLabel(game: b.game))
                        .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(c)
                        .padding(.horizontal, 6).padding(.vertical, 3)
                        .background(c.opacity(0.14), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    Text(b.title).font(.pretendard(size: 15, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 6)
                    // 예약이 필요한 곳은 **가서 줄만 서면 되는 곳과 다른 준비**가 든다.
                    if b.needsReservation { GLGBadge(label: "예약 필요", color: c) }
                }
                if !b.desc.isEmpty {
                    Text(b.desc).font(.pretendard(size: 12.5))
                        .foregroundStyle(GLGColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 6)
                }
                Divider().padding(.vertical, 12)
                if !b.location.isEmpty { boothFact("위치", b.location) }
                if !b.duration.isEmpty { boothFact("소요", b.duration) }
                if !b.capacity.isEmpty { boothFact("정원", b.capacity) }
                // 보상은 줄 설 이유가 되는 값이라 목록 끝이 아니라 **눈에 띄는 자리**에 둔다.
                if !b.reward.isEmpty {
                    Text(b.reward).font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(c)
                        .padding(.horizontal, 10).padding(.vertical, 7)
                        .background(c.opacity(0.10), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                }
            }
        }
    }

    @ViewBuilder private func boothFact(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: 0) {
            Text(label).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                .frame(width: 44, alignment: .leading)
            Text(value).font(.pretendard(size: 12.5, weight: .medium))
                .foregroundStyle(GLGColor.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.bottom, 8)
    }
}
