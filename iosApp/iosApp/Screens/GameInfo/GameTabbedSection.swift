import SwiftUI
import ComposeApp

// 통합 게임 탭 — 선택 게임의 픽업 배너·전투 진행도·수입 일지. (Compose GameTabbedSection 대응)
struct GameTabbedSection: View {
    @ObservedObject var store: SpendingStore
    @State private var selectedKey = "genshin"
    @Environment(\.glgAccent) private var accent

    private var games: [Game] { GameData.shared.attendanceGames }
    private var sel: Game { games.first { $0.key == selectedKey } ?? games[0] }

    var body: some View {
        let selBanners = store.activeBanners.filter { $0.game == sel.displayName }
        let selCombat = store.combat.filter { $0.game == sel.displayName }
        let selLedger = store.ledgers.first { $0.game == sel.displayName }
        let empty = selBanners.isEmpty && selCombat.isEmpty && selLedger == nil
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                ForEach(games, id: \.key) { g in
                    Button { selectedKey = g.key } label: {
                        Text(g.shortName).font(.system(size: 13, weight: .bold))
                            .foregroundStyle(g.key == selectedKey ? .white : Color(argb64: g.color))
                            .padding(.horizontal, 16).padding(.vertical, 8)
                            .background(g.key == selectedKey ? Color(argb64: g.color) : .white, in: Capsule())
                            .overlay(Capsule().stroke(Color(argb64: g.color).opacity(g.key == selectedKey ? 1 : 0.5), lineWidth: 1.5))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.bottom, 16)
            if empty {
                GLGCard(cornerRadius: 24, padding: 28) {
                    Text("\(sel.shortName) 정보가 아직 없어요").font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity)
                }
            } else {
                VStack(alignment: .leading, spacing: 16) {
                    if !selBanners.isEmpty { contentBlock("픽업 배너 D-Day") { BannerCard(game: sel, banners: selBanners) } }
                    if !selCombat.isEmpty { contentBlock("전투 콘텐츠 진행도") { CombatCard(game: sel, modes: selCombat) } }
                    if let l = selLedger { contentBlock("이번 달 수입 일지") { LedgerCard(ledger: l) } }
                }
            }
        }
    }

    private func contentBlock<C: View>(_ label: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.system(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary).padding(.leading, 2)
            content()
        }
    }
}

private struct BannerCard: View {
    let game: Game
    let banners: [GachaBanner]
    var body: some View {
        let phases = phaseGroups(banners)
        let labels = phaseLabels(phases)
        return GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Circle().fill(Color(argb64: game.color)).frame(width: 10, height: 10)
                    Text(game.displayName).font(.system(size: 15, weight: .bold))
                }
                .padding(.bottom, 4)
                ForEach(Array(phases.enumerated()), id: \.offset) { i, phaseBanners in
                    PhaseBlock(phase: labels[i], banners: phaseBanners, gameColor: Color(argb64: game.color))
                    if i < phases.count - 1 { Divider() }
                }
            }
        }
    }
}

private func phaseGroups(_ banners: [GachaBanner]) -> [[GachaBanner]] {
    Dictionary(grouping: banners, by: { $0.endMillis }).sorted { $0.key < $1.key }.map { $0.value }
}
private func phaseLabels(_ phases: [[GachaBanner]]) -> [String] {
    let versions = phases.map { $0.first?.version ?? "" }
    let last = versions.last
    var total: [String: Int] = [:]; for v in versions { total[v, default: 0] += 1 }
    var seen: [String: Int] = [:]
    return versions.map { v in
        let pos = seen[v] ?? 0; seen[v] = pos + 1
        if (total[v] ?? 1) >= 2 { return pos == 0 ? "전반" : (pos == 1 ? "후반" : "\(pos+1)페이즈") }
        return v == last ? "전반" : "후반"
    }
}

private struct PhaseBlock: View {
    let phase: String
    let banners: [GachaBanner]
    let gameColor: Color
    var body: some View {
        let first = banners[0]
        let version = banners.first { !$0.version.isEmpty }?.version ?? ""
        let start = banners.map { $0.startMillis }.min() ?? 0
        let charNames = banners.filter { $0.type != "weapon" }.map { $0.name }
        let hasWeapon = banners.contains { $0.type == "weapon" }
        let title = (charNames + (hasWeapon ? ["무기 기원"] : [])).joined(separator: " · ")
        let du = DateUtil.shared
        let period = start > 0 ? "\(du.shortDateTime(millis: start)) ~ \(du.shortDateTime(millis: first.endMillis))"
                               : du.shortDateTime(millis: first.endMillis)
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 6) {
                    Text(phase).font(.system(size: 11, weight: .bold)).foregroundStyle(gameColor)
                        .padding(.horizontal, 7).padding(.vertical, 2)
                        .background(gameColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
                    if !version.isEmpty { Text("v\(version)").font(.system(size: 11, weight: .medium)).foregroundStyle(GLGColor.textSecondary) }
                }
                Spacer()
                Text(first.dDayLabel(nowMillis: nowMs())).font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 3)
                    .background(gameColor, in: RoundedRectangle(cornerRadius: 8))
            }
            Text(title).font(.system(size: 14, weight: .bold)).lineLimit(2).padding(.top, 6)
            Text(period).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
        }
        .padding(.vertical, 10)
    }
}

private struct CombatCard: View {
    let game: Game
    let modes: [CombatMode]
    @Environment(\.glgAccent) private var accent
    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) { Circle().fill(Color(argb64: game.color)).frame(width: 10, height: 10); Text(game.shortName).font(.system(size: 15, weight: .bold)) }
                    .padding(.bottom, 2)
                ForEach(Array(modes.enumerated()), id: \.offset) { i, m in
                    combatRow(m)
                    if i < modes.count - 1 { Divider() }
                }
            }
        }
    }
    private func combatRow(_ m: CombatMode) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(m.name).font(.system(size: 14, weight: .bold)).lineLimit(1)
                    Text(m.detail).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    if m.maxStars > 0 {
                        Text("⭐ \(m.stars)/\(m.maxStars)").font(.system(size: 13, weight: .bold)).foregroundStyle(Color(argb64: m.gameColor))
                    } else if m.hasData {
                        Text("메달 \(m.stars)").font(.system(size: 13, weight: .bold)).foregroundStyle(Color(argb64: m.gameColor))
                    }
                    if let d = m.dDay(now: nowMs())?.int32Value, d >= 0 {
                        Text("D-\(d)").font(.system(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                    }
                }
            }
            if m.hasData && m.maxStars > 0 {
                ProgressView(value: Double(m.ratio)).tint(Color(argb64: m.gameColor)).padding(.top, 8)
            }
        }
        .padding(.vertical, 10)
    }
}

struct LedgerCard: View {
    let ledger: MonthlyLedger
    @Environment(\.glgAccent) private var accent
    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Circle().fill(Color(argb64: ledger.gameColor)).frame(width: 10, height: 10)
                    Text(GameData.shared.byName(name: ledger.game).shortName).font(.system(size: 15, weight: .bold))
                    if ledger.month > 0 { Text("\(ledger.month)월").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary) }
                }
                HStack(alignment: .bottom, spacing: 6) {
                    Text(num(ledger.premium)).font(.system(size: 28, weight: .bold)).foregroundStyle(accent.primary).lineLimit(1)
                    Text(ledger.premiumLabel).font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 4)
                    if let d = ledger.premiumDelta?.int64Value {
                        let up = d >= 0
                        Text((up ? "▲ " : "▼ ") + num(abs(d))).font(.system(size: 12, weight: .bold))
                            .foregroundStyle(up ? Color(hex: 0xFF1FB16B) : Color(hex: 0xFFE5484D)).padding(.bottom, 5)
                    }
                }
                .padding(.top, 12)
                if ledger.gold > 0 {
                    Text("\(ledger.goldLabel) \(num(ledger.gold))").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
                }
                if !ledger.breakdown.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(ledger.breakdown.prefix(5).enumerated()), id: \.offset) { _, e in
                            HStack {
                                Text(e.action).font(.system(size: 12)).lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                                Text(num(e.num)).font(.system(size: 12, weight: .medium))
                                Text("\(e.percent)%").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).frame(width: 44, alignment: .trailing)
                            }
                            .padding(.top, 8).padding(.bottom, 4)
                            ProgressView(value: min(max(Double(e.percent)/100.0, 0), 1)).tint(Color(argb64: ledger.gameColor))
                        }
                    }
                    .padding(.top, 14)
                }
            }
        }
    }
}
