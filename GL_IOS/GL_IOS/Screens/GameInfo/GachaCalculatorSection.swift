import SwiftUI
import Shared

// 계산기 2.0 — B 대시보드 레이아웃. 탭 제거, 입력→확률→재화→시나리오 위젯 세로 나열.
// 게임/배너 칩은 커스텀 글래스 글로우 칩(S4).
struct GachaCalculatorSection: View {
    @Environment(\.glgAccent) private var accent
    @State private var gameKey = "genshin"
    @State private var bannerType = "character"
    @State private var currency = ""
    @State private var pityStr = ""
    @State private var guaranteed = false
    @State private var qty = 1

    private var game: GachaGameRate { GachaRateData.shared.byKey(key: gameKey) ?? GachaRateData.shared.games[0] }
    private var banner: GachaBannerRate { game.banner(type: bannerType) ?? game.character ?? game.standard ?? game.games_firstBanner }

    // 파생 계산 묶음 (CalcResult 는 파일 스코프 — 서브뷰에 값으로 전달)
    private var calc: CalcResult {
        let cur = Int(currency) ?? 0
        let perPull = Int(banner.perPull)
        let pity = min(max(Int(pityStr) ?? 0, 0), Int(banner.hardPity) - 1)
        let possible = perPull > 0 ? cur / perPull : 0
        let pullsToHard = max(Int(banner.hardPity) - pity, 0)
        let currencyToHard = pullsToHard * perPull
        let additionalNeeded = max(currencyToHard - cur, 0)
        let additionalPulls = perPull > 0 ? Int(ceil(Double(additionalNeeded) / Double(perPull))) : 0
        let estCost = Int64(additionalPulls) * Int64(banner.wonPerPull)
        let p = GachaRateData.shared.pickupProb(n: Int32(possible), startPity: Int32(pity), b: banner, guaranteed: guaranteed)
        return CalcResult(cur: cur, pity: pity, possible: possible, pullsToHard: pullsToHard, currencyToHard: currencyToHard,
                          additionalNeeded: additionalNeeded, prob: Int((p * 100).rounded()), estCost: estCost)
    }

    var body: some View {
        stack
            .onChange(of: gameKey) { if game.banner(type: bannerType) == nil { bannerType = "character" } }
    }

    // 거대 단일 body 가 게임 탭마다 제네릭 메타데이터를 재해석하던 문제 → 위젯을 독립 View 구조체로 분리해
    // 타입을 잘게 쪼개고(메타데이터 특수화·캐시), 변경된 서브뷰만 갱신되게 함.
    private var stack: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 페이지 제목은 네비게이션 바(sectionPage)에서 표시 — 인라인 중복 제거
            // 컨텍스트: 게임 + 배너 (커스텀 글래스 글로우 칩 — 경량 솔리드, 실시간 블러 없음)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GachaRateData.shared.games, id: \.key) { g in
                        GlowChip(label: g.shortName, glow: Color(argb64: g.color), selected: g.key == gameKey, enabled: true) { gameKey = g.key }
                    }
                }
                .padding(8) // 글로우 그림자가 ScrollView 상하·좌우 경계에 잘리지 않도록 여백 확보
            }
            HStack(spacing: 8) {
                ForEach(Array(GachaRateData.shared.bannerTypes.enumerated()), id: \.offset) { _, pair in
                    let key = (pair.first as? String) ?? ""
                    let label = (pair.second as? String) ?? ""
                    GlowChip(label: label, glow: accent.primary, selected: key == bannerType, enabled: game.banner(type: key) != nil) { bannerType = key }
                }
            }
            .padding(.horizontal, 8) // 게임 칩 행(ScrollView 내부 padding 8)과 좌측 시작점 정렬
            .padding(.vertical, 6)

            InputCard(banner: banner, currency: $currency, pityStr: $pityStr, guaranteed: $guaranteed, qty: $qty).padding(.top, 13)
            ResultsCard(c: calc, banner: banner, qty: qty, guaranteed: guaranteed).padding(.top, 13)
        }
    }
}

// 파생 계산 결과 — 서브뷰에 값으로 전달 (부모 body 제네릭 타입 축소용 파일 스코프 분리)
private struct CalcResult { let cur, pity, possible, pullsToHard, currencyToHard, additionalNeeded, prob: Int; let estCost: Int64 }

// S4 커스텀 글래스 글로우 칩 — 반투명 고스트, 선택 시 컬러 보더+글로우. (실시간 블러 없는 경량 솔리드)
private struct GlowChip: View {
    let label: String
    let glow: Color
    let selected: Bool
    let enabled: Bool
    let action: () -> Void
    var body: some View {
        GLGChip(label: label, selected: selected, enabled: enabled, color: glow, action: action)
    }
}

// ── 입력 카드 ──
private struct InputCard: View {
    let banner: GachaBannerRate
    @Binding var currency: String
    @Binding var pityStr: String
    @Binding var guaranteed: Bool
    @Binding var qty: Int
    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 8) {
                    NumField(label: "보유 \(banner.currency)", placeholder: "0", text: $currency).frame(maxWidth: .infinity)
                    NumField(label: "현재 천장", placeholder: "0", text: $pityStr).frame(maxWidth: .infinity)
                }
                if banner.has5050 && !banner.no5050 {
                    CalcToggleRow(label: "확정(픽업 보장) 보유", isOn: $guaranteed).padding(.top, 10)
                }
                QtyRow(qty: $qty).padding(.top, 8)
            }
        }
    }
}

// ── 결과 카드 (확률·재화·시나리오 통합) ──
private struct ResultsCard: View {
    let c: CalcResult
    let banner: GachaBannerRate
    let qty: Int
    let guaranteed: Bool

    var body: some View {
        let probColor = c.prob >= 70 ? okGreen : (c.prob >= 40 ? warnAmber : badRed)
        let scn = scenario()
        let perPull = Int(banner.perPull)
        return GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                head("🎯 확보 확률")
                if c.cur > 0 {
                    (Text("보유분 \(c.possible)회로 ").foregroundStyle(GLGColor.textPrimary)
                        + Text("\(c.prob)%").bold().foregroundStyle(probColor)
                        + Text(" 확보 가능").foregroundStyle(GLGColor.textPrimary))
                        .font(.pretendard(size: 14, weight: .semibold)).padding(.top, 4)
                    ProgressView(value: Double(c.prob) / 100).tint(probColor).padding(.top, 11)
                } else {
                    Text("재화를 입력하면 확보 확률을 계산해요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4)
                }
                divider
                head("💎 필요 재화")
                HStack(spacing: 8) {
                    ResultBox(label: "하드 천장까지", value: "\(c.pullsToHard)회", sub: "\(num(c.currencyToHard)) \(banner.currency)")
                    ResultBox(label: "부족분", value: c.additionalNeeded > 0 ? num(c.additionalNeeded) : "0",
                              sub: c.additionalNeeded > 0 ? "\(won(c.estCost)) 충전" : "충전 불필요")
                }
                divider
                head("📊 시나리오 (\(qty)개 기준)")
                HStack(spacing: 8) {
                    ScenarioBox(title: "최선", sub: scn.bestSub, pulls: "\(scn.bestPulls)회", currency: "≈ \(num(scn.bestPulls * perPull)) \(banner.currency)", color: okGreen)
                    ScenarioBox(title: "최악", sub: scn.worstSub, pulls: "\(scn.worstPulls)회", currency: "≈ \(num(scn.worstPulls * perPull)) \(banner.currency)", color: badRed)
                }
            }
        }
    }

    private func head(_ t: String) -> some View {
        Text(t).font(.pretendard(size: 13, weight: .bold)).frame(maxWidth: .infinity, alignment: .leading)
    }
    private var divider: some View {
        Divider().overlay(Color.black.opacity(0.06)).padding(.vertical, 14)
    }
    private func scenario() -> (bestPulls: Int, worstPulls: Int, bestSub: String, worstSub: String) {
        let hp = Int(banner.hardPity), sp = Int(banner.softPity), pityVal = c.pity
        if banner.no5050 || !banner.has5050 {
            return (Int((Double(sp) * 0.7).rounded()) * qty, hp * qty, "조기 획득", "천장 도달")
        }
        let avgSingle = Int((Double(hp) * 0.83).rounded())
        let bestSingle = guaranteed ? max(1, avgSingle - pityVal) : max(1, Int((Double(avgSingle) * 0.6).rounded()) - pityVal)
        let worstSingle = guaranteed ? hp - pityVal : (hp - pityVal) + hp
        return (max(1, bestSingle) * qty, max(1, worstSingle) * qty,
                guaranteed ? "보장 + 빠른 획득" : "50/50 성공", "50/50 실패 → 천장")
    }
}

private extension GachaGameRate {
    /// character/standard 모두 nil 인 비정상 케이스 폴백(컴파일 안전용).
    var games_firstBanner: GachaBannerRate { character ?? standard ?? weapon! }
}

// 색
private let okGreen = Color(hex: 0xFF16A34A)
private let warnAmber = Color(hex: 0xFFD97706)
private let badRed = Color(hex: 0xFFDC2626)

// ── 공용 작은 컴포넌트 ──
private struct NumField: View {
    let label: String; let placeholder: String; @Binding var text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.pretendard(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            TextField(placeholder, text: $text).keyboardType(.numberPad).textFieldStyle(.plain).glgPillField()
                .onChange(of: text) { _, newValue in text = newValue.filter(\.isNumber) }
        }
    }
}

private struct CalcToggleRow: View {
    let label: String; @Binding var isOn: Bool
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Toggle(isOn: $isOn) { Text(label).font(.pretendard(size: 13)) }.tint(accent.primary)
    }
}

private struct QtyRow: View {
    @Binding var qty: Int
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack(spacing: 10) {
            Text("목표 개수").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            HStack(spacing: 6) {
                ForEach(1...3, id: \.self) { q in
                    Button { qty = q } label: {
                        Text("\(q)").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(q == qty ? .white : GLGColor.textSecondary)
                            .frame(width: 34, height: 34).background(q == qty ? accent.primary : Color(hex: 0xFFF2F2F6), in: Circle())
                    }.buttonStyle(.plain)
                }
            }
        }
    }
}

private struct ResultBox: View {
    let label: String; let value: String; let sub: String
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.pretendard(size: 10, weight: .semibold)).foregroundStyle(Color.black.opacity(0.35))
            Text(value).font(.pretendard(size: 17, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
            if !sub.isEmpty { Text(sub).font(.pretendard(size: 10)).foregroundStyle(Color.black.opacity(0.35)) }
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 12).padding(.vertical, 11)
        .background(Color.black.opacity(0.03), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct ScenarioBox: View {
    let title: String; let sub: String; let pulls: String; let currency: String; let color: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(color)
            Text(sub).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            Text(pulls).font(.pretendard(size: 16, weight: .bold)).padding(.top, 6)
            Text(currency).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 12).padding(.vertical, 11)
        .background(color.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
    }
}
