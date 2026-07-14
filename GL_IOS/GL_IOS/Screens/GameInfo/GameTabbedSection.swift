import SwiftUI
import Shared

// 통합 게임 탭 — 선택 게임의 전투 진행도·수입 일지. (픽업 배너는 상단 '게임 일정'으로 통합돼 여기선 제외)
struct GameTabbedSection: View {
    @ObservedObject var store: SpendingStore
    var filter: String = "all"   // 상단 게임 세그먼트 선택값 — 자체 칩 제거, 이 값으로 노출 게임 결정
    @Environment(\.glgAccent) private var accent

    private var games: [Game] { GameData.shared.attendanceGames }
    private var shownGames: [Game] { filter == "all" ? games : games.filter { $0.key == filter } }

    var body: some View {
        // 같은 카드 섹션끼리 묶기 — 게임별이 아니라 섹션 타입(배너/전투/일지)별로 그룹화.
        // 각 카드가 자체 게임 헤더를 가지므로 전체 보기에서 게임 구분이 유지된다.
        let combatGames = shownGames.compactMap { g -> (Game, [CombatMode])? in
            let c = store.combat.filter { $0.game == g.displayName }; return c.isEmpty ? nil : (g, c)
        }
        let ledgers = shownGames.compactMap { g in store.ledgers.first { $0.game == g.displayName } }
        let allEmpty = combatGames.isEmpty && ledgers.isEmpty
        let linked = store.hoyolabConfig.isLinked
        return VStack(alignment: .leading, spacing: 20) {
            if allEmpty && !linked {
                EmptyView()   // 호요랩 미연동: 전투/일지 데이터가 없어 빈 상태 카드도 미노출
            } else if allEmpty {
                GLGCard(cornerRadius: 20, padding: 28) {
                    Text("표시할 게임 정보가 아직 없어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity)
                }
            } else {
                if !combatGames.isEmpty {
                    contentBlock("전투 콘텐츠 진행도") {
                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(Array(combatGames.enumerated()), id: \.offset) { _, p in CombatCard(game: p.0, modes: p.1) }
                        }
                    }
                }
                if !ledgers.isEmpty {
                    contentBlock("이번 달 수입 일지") {
                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(Array(ledgers.enumerated()), id: \.offset) { _, l in LedgerCard(ledger: l) }
                        }
                    }
                }
            }
        }
    }

    private func contentBlock<C: View>(_ label: String, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.pretendard(size: 16, weight: .bold)).padding(.leading, 2)
            content()
        }
    }
}

private struct CombatCard: View {
    let game: Game
    let modes: [CombatMode]
    @Environment(\.glgAccent) private var accent
    var body: some View {
        GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) { GLGGameTag(game: game.displayName, size: .small); Text(game.shortName).font(.pretendard(size: 15, weight: .bold)) }
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
                    Text(m.name).font(.pretendard(size: 14, weight: .bold)).lineLimit(1)
                    Text(m.detail).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    if m.maxStars > 0 {
                        Text("⭐ \(m.stars)/\(m.maxStars)").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(Color(argb64: m.gameColor))
                    } else if m.hasData {
                        Text("메달 \(m.stars)").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(Color(argb64: m.gameColor))
                    }
                    if let d = m.dDay(now: nowMs())?.int32Value, d >= 0 {
                        Text("D-\(d)").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(accent.primary)
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
        GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    GLGGameTag(game: ledger.game, size: .small)
                    Text(GameData.shared.byName(name: ledger.game).shortName).font(.pretendard(size: 15, weight: .bold))
                    if ledger.month > 0 { Text("\(ledger.month)월").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary) }
                }
                HStack(alignment: .bottom, spacing: 6) {
                    Text(num(ledger.premium)).font(.pretendard(size: 28, weight: .bold)).foregroundStyle(accent.primary).lineLimit(1)
                    Text(ledger.premiumLabel).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 4)
                    if let d = ledger.premiumDelta?.int64Value {
                        let up = d >= 0
                        Text((up ? "▲ " : "▼ ") + num(abs(d))).font(.pretendard(size: 12, weight: .bold))
                            .foregroundStyle(up ? Color(hex: 0xFF1FB16B) : Color(hex: 0xFFE5484D)).padding(.bottom, 5)
                    }
                }
                .padding(.top, 12)
                if ledger.gold > 0 {
                    Text("\(ledger.goldLabel) \(num(ledger.gold))").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
                }
                if !ledger.breakdown.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(ledger.breakdown.prefix(5).enumerated()), id: \.offset) { _, e in
                            HStack {
                                Text(e.action).font(.pretendard(size: 12)).lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                                Text(num(e.num)).font(.pretendard(size: 12, weight: .medium))
                                Text("\(e.percent)%").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).frame(width: 44, alignment: .trailing)
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
