import SwiftUI
import ComposeApp

// 천장 카운터 — 게임별 count/hard + tier 강조. (Compose PitySection 대응)
struct PitySection: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("천장 카운터").font(.system(size: 16, weight: .bold)).padding(.bottom, 4)
            Text("남은 천장까지의 거리를 게임별로 관리해요.").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 12)
            GLGCard(cornerRadius: 20, padding: 16) {
                VStack(spacing: 0) {
                    let games = GameData.shared.attendanceGames
                    ForEach(Array(games.enumerated()), id: \.offset) { i, game in
                        pityRow(game)
                        if i < games.count - 1 { Divider() }
                    }
                }
            }
        }
    }

    private func pityRow(_ game: Game) -> some View {
        let state = store.pity[game.key]
        let count = Int(state?.count ?? 0)
        let rate = GachaRateData.shared.byKey(key: game.key)
        let character = rate?.character
        let hard = Int(character?.hardPity ?? 90)
        let soft = Int(character?.softPity ?? 74)
        let grade = rate?.grade ?? "5★"
        let tier = pityTier(count: count, soft: soft, hard: hard)
        let tierColor = tier == .safe ? accent.primary : pityColor(tier)
        let tierLabel = pityLabel(tier)
        let remain = max(hard - count, 0)
        let helper: String = {
            switch tier {
            case .safe: return "천장까지 \(remain)연"
            case .caution: return "주의 — 천장까지 \(remain)연 (소프트 \(soft)연)"
            case .imminent: return "임박 — \(remain)연 이내 \(grade) 보장"
            case .reached: return "도달 — 다음 \(grade) 100% 확정"
            }
        }()
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 8) {
                    Circle().fill(Color(argb64: game.color)).frame(width: 8, height: 8)
                    Text(game.displayName).font(.system(size: 14, weight: .bold)).lineLimit(1)
                    if let tl = tierLabel {
                        Text(tl).font(.system(size: 10, weight: .bold)).foregroundStyle(tierColor)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(tierColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
                    }
                }
                Spacer()
                Button { store.resetPity(gameKey: game.key) } label: {
                    Text("리셋").font(.system(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                }.buttonStyle(.plain)
            }
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text("\(count)").font(.system(size: 28, weight: .bold)).foregroundStyle(tier == .safe ? GLGColor.textPrimary : tierColor)
                Text("/ \(hard)").font(.system(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                Spacer()
                HStack(spacing: 8) {
                    pityBtn("−") { store.adjustPity(gameKey: game.key, delta: -1) }
                    pityBtn("+") { store.adjustPity(gameKey: game.key, delta: 1) }
                }
            }
            .padding(.top, 6)
            // 프로그레스바를 직접 드래그해 천장 카운트 조정 (±버튼은 미세조정용으로 유지)
            Slider(
                value: Binding(
                    get: { Double(count) },
                    set: { v in let nv = Int(v.rounded()); if nv != count { store.adjustPity(gameKey: game.key, delta: nv - count) } }
                ),
                in: 0...Double(max(hard, 1)), step: 1
            )
            .tint(tierColor)
            .padding(.top, 4)
            Text(helper).font(.system(size: 11, weight: tier == .reached ? .bold : .regular))
                .foregroundStyle(tier == .safe ? GLGColor.textSecondary : tierColor).padding(.top, 5)
        }
        .padding(.vertical, 10)
    }

    private func pityBtn(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 18, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                .frame(width: 32, height: 32).background(Color(hex: 0xFFF2F2F6), in: Circle())
        }.buttonStyle(.plain)
    }
}

enum PityTierS { case safe, caution, imminent, reached }
func pityTier(count: Int, soft: Int, hard: Int) -> PityTierS {
    if count >= hard { return .reached }
    if count >= soft { return .imminent }
    if count >= soft - 10 { return .caution }
    return .safe
}
private func pityColor(_ t: PityTierS) -> Color {
    switch t {
    case .safe: return GLGTheme.palette[0].primary // 대체로 accent 사용처에서 덮음; safe 는 below
    case .caution: return Color(hex: 0xFFF59E0B)
    case .imminent: return Color(hex: 0xFFFB8C00)
    case .reached: return Color(hex: 0xFFE53935)
    }
}
private func pityLabel(_ t: PityTierS) -> String? {
    switch t { case .safe: return nil; case .caution: return "주의"; case .imminent: return "임박"; case .reached: return "도달" }
}
