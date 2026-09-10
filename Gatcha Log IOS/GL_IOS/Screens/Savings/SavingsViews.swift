import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 저축 플래너 · 절약 챌린지 (27.35.0 신규) — 목업 design_savings_planner_mockup.html /
// design_savings_challenge_mockup.html. 계산은 전부 결정형(SavingsPlanner·SavingsChallenge, AI 없음).
// 진입: 홈 허브의 두 카드 → NavigationLink.
// ════════════════════════════════════════════════════════════════════════════

private let warnAmber = Color(hex: 0xFFF59E0B)
private let goldEarn = Color(hex: 0xFFF2B441)
private let spentRed = Color(hex: 0xFFEF6A6A)

private func commaInt(_ v: Int32) -> String {
    let s = String(v); var out = ""; let n = s.count
    for (i, c) in s.enumerated() { if i > 0 && (n - i) % 3 == 0 { out += "," }; out.append(c) }
    return out
}
private func ddLabel(_ d: Int32) -> String { d > 0 ? "D-\(d)" : (d == 0 ? "D-DAY" : "종료") }

/// 배지 id → SF Symbol. 앱 전역 아이콘 톤과 통일(이모지 대신). id 는 Challenge.kt 상수와 동일.
private func badgeSymbol(_ id: String) -> String {
    switch id {
    case "first_save": return "leaf.fill"
    case "nospend_7": return "flame.fill"
    case "budget_hit": return "target"
    case "nospend_30": return "diamond.fill"
    case "budget_3mo": return "trophy.fill"
    case "nospend_month": return "snowflake"
    case "save_3mo": return "chart.line.downtrend.xyaxis"
    case "game_budget": return "gamecontroller.fill"
    case "king": return "crown.fill"
    default: return "star.fill"
    }
}

/// SavingsPlan(Kotlin) 은 Identifiable 이 아니라 .sheet(item:) 용 래퍼.
// 저축 플래너(SavingsPlannerView·PlanInputSheet)는 여기 있었다 —
// 기능째 걷어냈다(2026-09-09).

struct SavingsChallengeView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var summary: ChallengeSummary? { store.challenge }

    /// 지출이 있었던 날짜 키 집합 — 최근 7일 스트립 판정용.
    ///
    /// 예전엔 [weekStrip] 안에서 만들었다. `dayKey` 는 게터라 항목마다 브리지 + 날짜 변환 +
    /// 문자열 조립이 붙는데, 7칸을 칠하려고 **지출 전체를 body 평가마다** 훑고 있었다.
    @State private var spentDays: Set<String> = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                heroCard
                if let s = summary, !s.challenges.isEmpty { challengeCard(s) }
                if let s = summary { badgeCard(s) }
                Text("무지출 스트릭·예산 달성은 지출 기록에서 자동 판정돼요. 배지는 한번 얻으면 유지됩니다.")
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.horizontal, 4)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("절약 챌린지")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: store.spendings) { spentDays = Set(store.spendings.map { $0.dayKey }) }
    }

    private var heroCard: some View {
        GLGCard(cornerRadius: 24, padding: 18) {
            VStack(alignment: .leading, spacing: 0) {
                Text("연속 무지출").font(.pretendard(size: 12.5, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Image(systemName: "flame.fill").font(.system(size: 24)).foregroundStyle(accent.primary)
                    Text("\(summary?.noSpendStreak ?? 0)").font(.pretendard(size: 34, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text("일째").font(.pretendard(size: 15, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                }
                Text("최고 기록 \(summary?.bestStreak ?? 0)일").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                weekStrip.padding(.top, 14)
            }
        }
    }

    private var weekStrip: some View {
        HStack(spacing: 7) {
            ForEach((0...6).reversed(), id: \.self) { ago in
                let key = DateUtil.shared.localDayKeyAgo(daysAgo: Int32(ago), nowMillis: nowMs())
                let spent = spentDays.contains(key)
                let isToday = ago == 0
                VStack(spacing: 5) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 11)
                            .fill(isToday ? accent.primary : (spent ? spentRed.opacity(0.14) : accent.primary.opacity(0.14)))
                        Text(isToday ? "오늘" : (spent ? "₩" : "✓"))
                            .font(.pretendard(size: isToday ? 12 : 15, weight: .bold))
                            .foregroundStyle(isToday ? .white : (spent ? spentRed : accent.primary))
                    }.frame(height: 34)
                    Text(DateUtil.shared.weekdayKo(millis: nowMs() - Int64(ago) * 86_400_000))
                        .font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                }.frame(maxWidth: .infinity)
            }
        }
    }

    private func challengeCard(_ s: ChallengeSummary) -> some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("이번 달 챌린지").font(.pretendard(size: 14, weight: .bold))
                    Spacer()
                    Text("\(s.challenges.filter { $0.reached }.count) / \(s.challenges.count) 달성").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                }
                ForEach(Array(s.challenges.enumerated()), id: \.offset) { idx, c in
                    challengeRow(c)
                    if idx < s.challenges.count - 1 { Divider() }
                }
            }
        }
    }

    private func challengeRow(_ c: ChallengeProgress) -> some View {
        // 게임별 챌린지는 **게임색**으로 진행바와 점을 칠한다(27.50.0 고도화).
        // 전부 강조색이면 "이게 어느 게임 것인가" 를 제목 글자로만 읽어야 한다.
        let gameColor: Color? = c.game.isEmpty ? nil
            : GameData.shared.byNameOrNull(name: c.game).map { Color(argb64: $0.color) }
        let tone = gameColor ?? accent.primary
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                if let gameColor {
                    Circle().fill(gameColor).frame(width: 7, height: 7).padding(.top, 5)
                    Spacer().frame(width: 7)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(c.title).font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text(c.desc).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
                Spacer()
                Text(c.reached ? "달성 ✓" : "\(c.current) / \(c.target)")
                    .font(.pretendard(size: 13, weight: .bold))
                    .foregroundStyle(c.reached ? tone : GLGColor.textPrimary)
            }
            progressBar(Double(c.ratio), c.warn ? warnAmber : tone).padding(.top, 9)
        }.padding(.vertical, 13)
    }

    private func badgeCard(_ s: ChallengeSummary) -> some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Text("획득 배지").font(.pretendard(size: 14, weight: .bold))
                    Text("\(s.earnedBadgeCount) / \(s.totalBadgeCount)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                }
                Text("챌린지·스트릭을 달성하면 배지를 모을 수 있어요").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4)
                let cols = Array(repeating: GridItem(.flexible(), spacing: 6), count: 4)
                LazyVGrid(columns: cols, spacing: 12) {
                    ForEach(Array(s.badges.enumerated()), id: \.offset) { _, b in badgeCell(b) }
                }.padding(.top, 14)
            }
        }
    }

    private func badgeCell(_ b: BadgeState) -> some View {
        VStack(spacing: 6) {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(b.earned ? goldEarn.opacity(0.16) : Color(hex: 0xFFF6F7F9))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(b.earned ? goldEarn.opacity(0.5) : Color(hex: 0xFFE3E5EA), lineWidth: 1))
                Image(systemName: b.earned ? badgeSymbol(b.id) : "lock.fill")
                    .font(.system(size: b.earned ? 24 : 17, weight: .semibold))
                    .foregroundStyle(b.earned ? goldEarn : GLGColor.progressEmpty)
            }.frame(width: 56, height: 56)
            Text(b.title).font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(b.earned ? GLGColor.textPrimary : GLGColor.textSecondary)
                .multilineTextAlignment(.center)
        }.frame(maxWidth: .infinity)
    }
}

// ══════════════════════════════════════════════════════════════ 홈 진입 카드
struct SavingsChallengeHomeCard: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    var body: some View {
        GLGCard(cornerRadius: 18, padding: 15) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Image(systemName: "flame.fill").font(.system(size: 16)).foregroundStyle(accent.primary).frame(width: 30, height: 30)
                        .background(accent.primary.opacity(0.14), in: RoundedRectangle(cornerRadius: 10))
                    Text("절약 챌린지").font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Spacer()
                    Text("열기 ›").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                }
                HStack(spacing: 5) {
                    Image(systemName: "flame.fill").font(.system(size: 17)).foregroundStyle(accent.primary)
                    Text("\(store.challenge?.noSpendStreak ?? 0)일").font(.pretendard(size: 20, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text("연속 무지출").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    Spacer()
                    Text("배지 \(store.challenge?.earnedBadgeCount ?? 0)/\(store.challenge?.totalBadgeCount ?? 8)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                }.padding(.top, 11)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════ 공용 소품

@ViewBuilder
private func progressBar(_ ratio: Double, _ color: Color, height: CGFloat = 6) -> some View {
    GeometryReader { geo in
        ZStack(alignment: .leading) {
            Capsule().fill(GLGColor.progressEmpty)
            Capsule().fill(color).frame(width: geo.size.width * max(0, min(1, ratio)))
        }
    }.frame(height: height)
}
