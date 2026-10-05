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
                // 전투 진행도 2.0 A안(10/1) — 요약 머리 섹션 + 게임마다 섹션 하나, 사이는 띠. Android 와 같다.
                // 집계는 화면에 보이는 모드만 넣는다. 셈에 드는 모드가 없으면(메달형·미도전뿐) 머리는 걷는다.
                let summary = GameInfoKt.combatSummary(modes: combatGames.flatMap { $0.1 }, now: nowMs())
                if summary.total > 0 {
                    GiPageSection(top: 12) { CombatSummaryHead(summary: summary) }
                }
                // 여기 있던 '클리어 편성' 진입 행은 걷어냈다 — 데일리 카드로 꺼내면서
                // 이 줄을 그대로 두는 바람에 **같은 진입점이 두 화면에 나란히** 보였다.
                // 진입은 데일리 카드 한 곳(DailyHeroSection 의 GameContentEntry)으로 모은다.
                ForEach(Array(combatGames.enumerated()), id: \.offset) { i, p in
                    let first = i == 0 && summary.total == 0
                    if !first { GiBand() }
                    // 마지막 줄이 아래 12 를 가져 8 + 12 = 띠까지(맨 아래면 끝까지) 보이는 20.
                    GiPageSection(top: first ? 12 : 22, bottom: 8) { CombatCard(game: p.0, modes: p.1) }
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
/// bottom — 마지막 내용 끝 → 띠가 눈에 20 이 되게, 20 − (마지막 줄이 스스로 가진 아래 여백)을 넘긴다(10/1).
/// 페이지 맨 아래 섹션은 20 그대로 둔다. top — 헤더 바로 아래 첫 섹션은 12(전투 진행도 A안).
struct GiPageSection<Content: View>: View {
    let title: String?
    let top: CGFloat
    let bottom: CGFloat
    let content: Content

    init(_ title: String? = nil, top: CGFloat = 22, bottom: CGFloat = 20, @ViewBuilder content: () -> Content) {
        self.title = title
        self.top = top
        self.bottom = bottom
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
        .padding(.top, top)
        .padding(.bottom, bottom)
    }
}

/// 줄 사이 헤어라인(10/1) — 마이페이지 · 지출과 같은 1 · #EEF0F2. Android `GiHairline` 과 같다.
struct GiHairline: View {
    var body: some View { Color(hex: 0xFFEEF0F2).frame(height: 1).frame(maxWidth: .infinity) }
}

// 전투 진행도 2.0 A안(10/1) 색 — 급한 마감 · 만점. Android GameInfoCombat 과 같다.
private let combatUrgent = Color(hex: 0xFFE8634A)
private let combatUrgentBg = Color(hex: 0xFFFDECE8)
private let combatDone = Color(hex: 0xFF0F8C77)
private let combatDoneBg = Color(hex: 0xFFE6F9F5)
private let combatPillGrayBg = Color(hex: 0xFFF2F4F6)
private let combatTrack = Color(hex: 0xFFEDEFF3)

/// 요약 머리 — 「만점까지 n개 남았어요」 + 만점 · 가장 급한 마감 두 칸. 집계는 공유 `combatSummary`.
private struct CombatSummaryHead: View {
    let summary: CombatSummary
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("전투 콘텐츠 진행도").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            Group {
                if summary.remaining > 0 {
                    Text("만점까지 ") + Text("\(summary.remaining)개").foregroundColor(combatUrgent) + Text(" 남았어요")
                } else {
                    Text("모두 만점이에요")
                }
            }
            .font(.pretendard(size: 24, weight: .black)).foregroundStyle(GLGColor.textPrimary)
            .lineSpacing(8)
            .padding(.top, 4)
            HStack(spacing: 10) {
                chip("만점", "\(summary.full) / \(summary.total)", GLGColor.textPrimary)
                if let d = summary.urgentDDay?.int32Value {
                    chip("가장 급한 마감", "D-\(d)", combatUrgent)
                } else {
                    chip("가장 급한 마감", "-", GLGColor.textSecondary)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 16)
        }
    }
    private func chip(_ label: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            Text(value).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(color)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Color(hex: 0xFFF7F8FA), in: RoundedRectangle(cornerRadius: 14))
    }
}

/// 게임별 전투 콘텐츠 진행도 블록. 섹션 하나가 게임 하나다 — 게임 사이는 부르는 쪽의 띠(GiBand)가 가른다.
private struct CombatCard: View {
    let game: Game
    let modes: [CombatMode]
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) { GLGGameTag(game: game.displayName, size: .small); Text(game.shortName).font(.pretendard(size: 17, weight: .bold)) }
                .padding(.bottom, 4)
            ForEach(Array(modes.enumerated()), id: \.offset) { i, m in
                combatRow(m)
                if i < modes.count - 1 { GiHairline() }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    private func combatRow(_ m: CombatMode) -> some View {
        let color = Color(argb64: m.gameColor)
        // 집계(combatSummary)와 같은 기준 — 셈에 드는 모드만 만점 · 마감 강조를 받는다.
        let counted = m.hasData && m.maxStars > 0
        let full = counted && m.stars >= m.maxStars
        let d: Int32? = m.dDay(now: nowMs()).map { $0.int32Value }.flatMap { $0 >= 0 ? $0 : nil }
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(m.name).font(.pretendard(size: 15, weight: .bold)).lineLimit(1)
                    Text(m.detail).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !m.badge.isEmpty {
                    // 평가 모드(시유 방어전) — 별 대신 등급. 막대는 점수/만점으로 아래에서 그대로 그린다.
                    Text(m.badge).font(.pretendard(size: 17, weight: .black)).foregroundStyle(color)
                } else if m.maxStars > 0 {
                    HStack(spacing: 3) {
                        Image(systemName: "star.fill").font(.system(size: 14)).frame(width: 16, height: 16).foregroundStyle(color)
                        Text("\(m.stars)").font(.pretendard(size: 17, weight: .black)).foregroundColor(color)
                            + Text("/\(m.maxStars)").font(.pretendard(size: 13, weight: .medium)).foregroundColor(GLGColor.textSecondary)
                    }
                    // 아이콘엔 라벨을 주지 않고 묶음 하나에 준다 — 숫자만 읽히면 무엇의 개수인지 모른다.
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("별 \(m.stars) / \(m.maxStars)")
                } else if m.hasData {
                    Text("메달 \(m.stars)").font(.pretendard(size: 17, weight: .black)).foregroundStyle(color)
                }
            }
            if counted {
                combatTrack.frame(height: 6)
                    .overlay(alignment: .leading) {
                        GeometryReader { g in color.frame(width: g.size.width * CGFloat(m.ratio)) }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 3))
                    .padding(.top, 10)
            }
            if full || d != nil {
                HStack(spacing: 6) {
                    Spacer(minLength: 0)
                    if full { pill("✓ 만점", combatDone, combatDoneBg) }
                    if let d {
                        if counted && !full && d <= HomeLogic.shared.COMBAT_WARN_DAYS {
                            pill("D-\(d) 마감", combatUrgent, combatUrgentBg)
                        } else {
                            pill("D-\(d)", GLGColor.textSecondary, combatPillGrayBg)
                        }
                    }
                }
                .padding(.top, 8)
            }
        }
        .padding(.vertical, 12)
    }
    private func pill(_ text: String, _ fg: Color, _ bg: Color) -> some View {
        Text(text).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(fg)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(bg, in: RoundedRectangle(cornerRadius: 10))
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
