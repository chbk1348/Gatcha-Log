import SwiftUI
import Shared

// 통합 게임 탭 — 3게임의 전투 진행도·수입 일지. (픽업 배너는 상단 '게임 일정'으로 통합돼 여기선 제외)
struct GameTabbedSection: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var games: [Game] { GLGGames.attendance }

    var body: some View {
        // 같은 카드 섹션끼리 묶기 — 게임별이 아니라 섹션 타입(배너/전투/일지)별로 그룹화.
        // 각 카드가 자체 게임 헤더를 가지므로 전체 보기에서 게임 구분이 유지된다.
        let combatGames = games.compactMap { g -> (Game, [CombatMode])? in
            let c = store.combat.filter { $0.game == g.displayName }; return c.isEmpty ? nil : (g, c)
        }
        let ledgers = games.compactMap { g in store.ledgers.first { $0.game == g.displayName } }
        let allEmpty = combatGames.isEmpty && ledgers.isEmpty
        let linked = store.hoyolabConfig.isLinked
        // 카드 없이 섹션을 쌓는다(10/1) — 섹션 사이는 GiBand, 반복되던 게임 카드는 헤어라인 목록으로.
        return VStack(alignment: .leading, spacing: 0) {
            if allEmpty && !linked {
                EmptyView()   // 호요랩 미연동: 전투/일지 데이터가 없어 빈 상태도 미노출
            } else if allEmpty && store.isRefreshing {
                // 불러오는 중엔 뼈대(10/1) — Android GameContentSkeleton 과 같다(전엔 iOS 만 빈 문구가 먼저 떴다).
                skeleton
            } else if allEmpty {
                GiPageSection {
                    Text("표시할 게임 정보가 아직 없어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
            } else {
                if !combatGames.isEmpty {
                    GiPageSection("전투 콘텐츠 진행도") {
                        // 여기 있던 '클리어 편성' 진입 행은 걷어냈다 — 데일리 카드로 꺼내면서
                        // 이 줄을 그대로 두는 바람에 **같은 진입점이 두 화면에 나란히** 보였다.
                        // 진입은 데일리 카드 한 곳(DailyHeroSection 의 GameContentEntry)으로 모은다.
                        ForEach(Array(combatGames.enumerated()), id: \.offset) { i, p in
                            if i > 0 { GiHairline() }
                            CombatCard(game: p.0, modes: p.1)
                                .padding(.top, i > 0 ? 14 : 0)
                                .padding(.bottom, 4)
                        }
                    }
                }
                if !ledgers.isEmpty {
                    if !combatGames.isEmpty { GiBand() }
                    GiPageSection("이번 달 수입 일지") {
                        ForEach(Array(ledgers.enumerated()), id: \.offset) { i, l in
                            if i > 0 { GiHairline() }
                            LedgerCard(ledger: l)
                                .padding(.top, i > 0 ? 18 : 0)
                                .padding(.bottom, i < ledgers.count - 1 ? 18 : 0)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 전투 · 수입 일지 로딩 뼈대(10/1) — 카드 없이 섹션 제목 + 게임 블록 2개. Android GameContentSkeleton 과 같다.
    private var skeleton: some View {
        GLGShimmerClock {
            GiPageSection {
                GLGSkeleton().frame(width: 140, height: 18)
                ForEach(0..<2, id: \.self) { i in
                    if i > 0 { GiHairline() }
                    VStack(alignment: .leading, spacing: 0) {
                        GLGSkeleton().frame(width: 110, height: 16)
                        ForEach(0..<2, id: \.self) { _ in
                            HStack {
                                GLGSkeleton().frame(width: 90, height: 14)
                                Spacer()
                                GLGSkeleton().frame(width: 44, height: 14)
                            }
                            .padding(.top, 14)
                        }
                    }
                    .padding(.vertical, 14)
                }
            }
        }
    }
}

/// 게임정보 하위 페이지 섹션(10/1) — 카드 없이 화면 폭, 좌우 20 · 위 22 · 아래 20. 섹션 사이는 GiBand.
/// Android `GiPageSection` 과 같다.
struct GiPageSection<Content: View>: View {
    let title: String?
    let content: Content

    init(_ title: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .padding(.bottom, 12)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.top, 22)
        .padding(.bottom, 20)
    }
}

/// 줄 사이 헤어라인(10/1) — 마이페이지 · 지출과 같은 1 · #EEF0F2. Android `GiHairline` 과 같다.
struct GiHairline: View {
    var body: some View { Color(hex: 0xFFEEF0F2).frame(height: 1).frame(maxWidth: .infinity) }
}

private struct CombatCard: View {
    let game: Game
    let modes: [CombatMode]
    @Environment(\.glgAccent) private var accent
    // 카드 면은 걷었다(10/1) — 게임 사이 구분은 부르는 쪽의 헤어라인이 한다.
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) { GLGGameTag(game: game.displayName, size: .small); Text(game.shortName).font(.pretendard(size: 15, weight: .bold)) }
                .padding(.bottom, 2)
            ForEach(Array(modes.enumerated()), id: \.offset) { i, m in
                combatRow(m)
                if i < modes.count - 1 { GiHairline() }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    private func combatRow(_ m: CombatMode) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(m.name).font(.pretendard(size: 15, weight: .bold)).lineLimit(1)
                    // 보조 글자 11 → 13(10/1) — 카드를 걷은 흰 바탕에서 11 은 흐렸다.
                    Text(m.detail).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    if !m.badge.isEmpty {
                        // 평가 모드(시유 방어전) — 별 대신 등급. 막대는 점수/만점으로 아래에서 그대로 그린다.
                        Text(m.badge).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(Color(argb64: m.gameColor))
                    } else if m.maxStars > 0 {
                        StarCount(label: "\(m.stars)/\(m.maxStars)",
                                  description: "별 \(m.stars) / \(m.maxStars)",
                                  size: 13)
                            .foregroundStyle(Color(argb64: m.gameColor))
                    } else if m.hasData {
                        Text("메달 \(m.stars)").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(Color(argb64: m.gameColor))
                    }
                    if let d = m.dDay(now: nowMs())?.int32Value, d >= 0 {
                        Text("D-\(d)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
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

/// 별 획득 수 — 아이콘 + 숫자. 이모지(⭐)는 기기·OS 폰트에 따라 모양과 크기가 제각각이라
/// 옆 숫자와 기준선이 어긋난다. SF Symbol 은 색을 게임색으로 물들일 수 있다는 이점도 있다.
/// (Android `StarCount` 와 패리티)
///
/// 아이콘에 개별 접근성 라벨을 주지 않고 요소를 합쳐 하나만 주는 이유: 이모지일 때는 VoiceOver 가
/// "별"을 읽어 줬는데, 심볼로 바꾸면 숫자만 남아 무엇의 개수인지 알 수 없다.
struct StarCount: View {
    let label: String
    let description: String
    let size: CGFloat

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: "star.fill").font(.system(size: size - 2))
            Text(label).font(.pretendard(size: size, weight: .bold))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(description)
    }
}

struct LedgerCard: View {
    let ledger: MonthlyLedger
    @Environment(\.glgAccent) private var accent
    // 카드 면은 걷었다(10/1) — 게임 사이 구분은 부르는 쪽의 헤어라인이 한다.
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                GLGGameTag(game: ledger.game, size: .small)
                Text(GameData.shared.byName(name: ledger.game).shortName).font(.pretendard(size: 15, weight: .bold))
                if ledger.month > 0 { Text("\(ledger.month)월").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary) }
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
                Text("\(ledger.goldLabel) \(num(ledger.gold))").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
            }
            if !ledger.breakdown.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(ledger.breakdown.prefix(5).enumerated()), id: \.offset) { _, e in
                        HStack {
                            // 내역 글자 12/11 → 13/12(10/1) — 흰 바탕 전폭에서 읽기 쉽게.
                            Text(e.action).font(.pretendard(size: 13)).lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                            Text(num(e.num)).font(.pretendard(size: 13, weight: .medium))
                            Text("\(e.percent)%").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).frame(width: 44, alignment: .trailing)
                        }
                        .padding(.top, 8).padding(.bottom, 4)
                        ProgressView(value: min(max(Double(e.percent)/100.0, 0), 1)).tint(Color(argb64: ledger.gameColor))
                    }
                }
                .padding(.top, 14)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
