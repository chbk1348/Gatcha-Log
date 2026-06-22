import SwiftUI
import Shared

// 충전 가성비 비교 — 호요 3종(원신·스타레일·젠레스) 한국 충전표를 단가(원/개)순으로 비교.
// 섹션이 자체 게임 탭을 소유(외부 필터와 무관)하고, 첫구매 2배 토글을 들고 있다.
// 디자인: 계산기 2.0 톤(흰 카드·아웃라인·게임색 글로우 칩) — 글래스 제거 후 흰 배경+아웃라인 기준.
struct RechargeValueSection: View {
    @State private var gameKey = "genshin"
    @State private var firstBuy = true

    // 지원 게임(genshin·hsr·zzz)만 노출. 표시명/색/재화/1뽑당 재화는 GachaRateData 재사용.
    private var games: [GachaGameRate] {
        GachaRateData.shared.games.filter { RechargeData.shared.isSupported(gameKey: $0.key) }
    }
    private var game: GachaGameRate {
        GachaRateData.shared.byKey(key: gameKey) ?? games.first ?? GachaRateData.shared.games[0]
    }
    private var gameColor: Color { Color(argb64: game.color) }
    private var currency: String { game.character?.currency ?? "재화" }
    private var costPerPull: Int { game.character?.costPerPull.map { Int(truncating: $0) } ?? 160 }

    private var sorted: [RechargePackage] {
        RechargeData.shared.sortedByValue(gameKey: gameKey, firstBuy: firstBuy)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 게임 탭 — 섹션 소유(커스텀 글로우 칩, 계산기 2.0 패리티)
            HStack(spacing: 8) {
                ForEach(games, id: \.key) { g in
                    GameTab(label: g.shortName, glow: Color(argb64: g.color), selected: g.key == gameKey) {
                        gameKey = g.key
                    }
                }
            }
            .padding(.top, 2)

            // 첫구매 2배 토글
            FirstBuyRow(isOn: $firstBuy, glow: gameColor).padding(.top, 12)

            // 추천 배너 — 현재 firstBuy 기준 단가 최저 패키지
            if let best = sorted.first {
                RecoBanner(pkg: best, firstBuy: firstBuy, currency: currency,
                           costPerPull: costPerPull, glow: gameColor).padding(.top, 14)
            }

            // 정렬 라벨
            (Text("정렬 ") + Text("1개당 단가 ↓").bold().foregroundColor(GLGColor.textPrimary)
                + Text(" · \(currency) 1뽑 = \(costPerPull)개 기준"))
                .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                .padding(.top, 16).padding(.bottom, 8).padding(.horizontal, 2)

            // 패키지 리스트 (단가 오름차순)
            VStack(spacing: 9) {
                ForEach(Array(sorted.enumerated()), id: \.offset) { idx, pkg in
                    PackageRow(pkg: pkg, rank: idx, count: sorted.count, firstBuy: firstBuy,
                               currency: currency, costPerPull: costPerPull, glow: gameColor)
                }
            }

            // 푸터
            Text("가격은 한국 공식 인앱결제 기준(플랫폼·할인 미반영) · 단가 = 가격 ÷ 받는 재화\n창세의 결정·별옥·모노크롬은 게임 내 원석·성옥·폴리크롬으로 1:1 전환")
                .font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
                .multilineTextAlignment(.center).lineSpacing(3)
                .frame(maxWidth: .infinity).padding(.top, 14)
        }
    }
}

// ── 게임 탭 (커스텀 글로우 칩, 선택 시 게임색 그라데이션) ──
private struct GameTab: View {
    let label: String
    let glow: Color
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.pretendard(size: 13, weight: .bold))
                .foregroundStyle(selected ? .white : GLGColor.textSecondary)
                .frame(maxWidth: .infinity).padding(.vertical, 9)
                .background {
                    if selected {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(LinearGradient(colors: [glow, glow.opacity(0.75).blended(black: 0.25)],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing))
                    } else {
                        RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.white)
                    }
                }
                .overlay {
                    if !selected {
                        RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(cardLine, lineWidth: 1)
                    }
                }
                .shadow(color: selected ? glow.opacity(0.4) : .clear, radius: selected ? 8 : 0, y: selected ? 4 : 0)
        }.buttonStyle(.plain)
    }
}

// ── 첫구매 2배 토글 행 ──
private struct FirstBuyRow: View {
    @Binding var isOn: Bool
    let glow: Color
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("첫 구매 2배 보너스 반영").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("계정당 패키지별 1회 · 버전마다 초기화").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: $isOn).labelsHidden().tint(glow)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(cardLine, lineWidth: 1))
    }
}

// ── 추천 배너 (🏆 지금 가장 이득) ──
private struct RecoBanner: View {
    let pkg: RechargePackage
    let firstBuy: Bool
    let currency: String
    let costPerPull: Int
    let glow: Color
    var body: some View {
        let total = Int(pkg.total(firstBuy: firstBuy))
        let pulls = pkg.pulls(firstBuy: firstBuy, costPerPull: Int32(costPerPull))
        let perPullWon = pulls > 0 ? Int64((Double(Int(pkg.priceKrw)) / pulls).rounded()) : 0
        VStack(alignment: .leading, spacing: 0) {
            Text("🏆 지금 가장 이득").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(.white.opacity(0.85))
            Text("\(num(total)) \(currency) · \(won(Int(pkg.priceKrw)))")
                .font(.pretendard(size: 18, weight: .heavy)).foregroundStyle(.white).padding(.top, 5)
            Text("\(firstBuy ? "첫구매 시 " : "")\(num(total))개 = 약 \(fixed(pulls, pulls < 10 ? 1 : 0))뽑 · 1뽑당 \(won(perPullWon))")
                .font(.pretendard(size: 12)).foregroundStyle(.white.opacity(0.9)).padding(.top, 2)
            Text(firstBuy ? "미사용 첫구매 中 단가 최저" : "전체 패키지 中 단가 최저")
                .font(.pretendard(size: 10, weight: .heavy)).foregroundStyle(.white)
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(Color.white.opacity(0.22), in: RoundedRectangle(cornerRadius: 8))
                .padding(.top, 9)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(LinearGradient(colors: [glow, glow.blended(black: 0.3)],
                                     startPoint: .topLeading, endPoint: .bottomTrailing))
        }
        .shadow(color: glow.opacity(0.35), radius: 12, y: 6)
    }
}

// ── 패키지 행 ──
private struct PackageRow: View {
    let pkg: RechargePackage
    let rank: Int
    let count: Int
    let firstBuy: Bool
    let currency: String
    let costPerPull: Int
    let glow: Color

    var body: some View {
        let isBest = rank == 0
        let total = Int(pkg.total(firstBuy: firstBuy))
        let pulls = pkg.pulls(firstBuy: firstBuy, costPerPull: Int32(costPerPull))
        let unit = pkg.unitPrice(firstBuy: firstBuy)
        // 단가 색: 순위 비율로 green→orange→red (목업 느낌). 최저=green, 최고=red.
        let ratio = count > 1 ? Double(rank) / Double(count - 1) : 0
        let unitColor: Color = ratio < 0.5 ? unitGood : (ratio < 0.85 ? unitWarn : unitBad)

        HStack(spacing: 12) {
            // 재화량
            VStack(spacing: 3) {
                Text(num(total)).font(.pretendard(size: 17, weight: .heavy)).foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1).minimumScaleFactor(0.6)
                Text(currency).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
            }
            .frame(width: 64)

            // 가격·보너스·뽑
            VStack(alignment: .leading, spacing: 2) {
                Text(won(Int(pkg.priceKrw))).font(.pretendard(size: 15, weight: .heavy)).foregroundStyle(GLGColor.textPrimary)
                Text(firstBuy ? "첫구매 2배 (+\(num(Int(pkg.base))))" : (pkg.bonus > 0 ? "보너스 +\(num(Int(pkg.bonus)))" : "보너스 없음"))
                    .font(.pretendard(size: 11, weight: .bold)).foregroundStyle(unitGood)
                Text("≈ \(fixed(pulls, pulls < 10 ? 1 : 0))뽑").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // 단가
            VStack(alignment: .trailing, spacing: 1) {
                Text(fixed(unit, 1)).font(.pretendard(size: 15, weight: .heavy)).foregroundStyle(unitColor)
                Text("원/개").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 13)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
            .stroke(isBest ? glow : cardLine, lineWidth: isBest ? 1.5 : 1))
        .shadow(color: isBest ? glow.opacity(0.22) : .clear, radius: isBest ? 10 : 0, y: isBest ? 6 : 0)
        .overlay(alignment: .topLeading) {
            if isBest {
                Text("BEST").font(.pretendard(size: 9, weight: .heavy)).foregroundStyle(.white)
                    .padding(.horizontal, 7).padding(.vertical, 2)
                    .background(glow, in: RoundedRectangle(cornerRadius: 6))
                    .offset(x: 12, y: -7)
            }
        }
    }
}

// 카드 아웃라인 (목업: --line #E7E9EE)
private let cardLine = Color(hex: 0xFFE7E9EE)

// 단가 색 (목업: --good/--warn/--bad)
private let unitGood = Color(hex: 0xFF10B981)
private let unitWarn = Color(hex: 0xFFF59E0B)
private let unitBad = Color(hex: 0xFFEF4444)

private extension Color {
    /// 게임색을 검정과 섞어 그라데이션 끝색 생성 (목업 color-mix 대응).
    func blended(black amount: Double) -> Color {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        let f = 1 - amount
        return Color(red: Double(r) * f, green: Double(g) * f, blue: Double(b) * f, opacity: Double(a))
    }
}
