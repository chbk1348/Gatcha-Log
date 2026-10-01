import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 절약 챌린지 2.0 — 무지출 스트릭 · 이번 달 챌린지 · 배지 컬렉션. (Compose SavingsChallengeScreen 대응)
// 카드 없이 화면 폭 섹션 + 10 띠(마이페이지 · 지출 화면과 같은 규격). 계산은 전부 결정형(SavingsChallenge, AI 없음).
// 배지를 누르면 얻는 방법 모달 — 문구는 공유 BadgeState.howTo(판정 규칙과 한 곳).
// 진입: 마이페이지 「절약 챌린지」 섹션 → NavigationLink.
// ════════════════════════════════════════════════════════════════════════════

private let warnAmber = Color(hex: 0xFFF59E0B)
private let goldEarn = Color(hex: 0xFFF2B441)
private let spentRed = Color(hex: 0xFFEF6A6A)
private let bandColor = Color(hex: 0xFFF2F4F6)
private let hairColor = Color(hex: 0xFFEEF0F2)
private let lockedIcon = Color(hex: 0xFFB8BDC6)

/// 배지 id → SF Symbol. 앱 전역 아이콘 톤과 통일(이모지 대신). id 는 Challenge.kt 상수와 동일. Android badgeIcon 과 짝.
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

/// 연속 무지출 일수로 판정하는 배지 — 설명 모달에 지금 기록을 함께 보여 준다.
private let streakBadges: Set<String> = ["first_save", "nospend_7", "nospend_30"]

struct SavingsChallengeView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var summary: ChallengeSummary? { store.challenge }

    /// 지출이 있었던 날짜 키 집합 — 최근 7일 스트립 판정용. 지출이 바뀔 때만 만든다.
    @State private var spentDays: Set<String> = []
    @State private var openBadge: BadgeState? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                streakSection
                if let s = summary, !s.challenges.isEmpty {
                    band
                    challengeSection(s)
                }
                if let s = summary {
                    band
                    badgeSection(s)
                }
                band
                Text("무지출 스트릭·예산 달성은 지출 기록에서 자동 판정돼요. 배지는 한번 얻으면 유지돼요.")
                    .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.horizontal, 20).padding(.vertical, 14)
            }
            .padding(.bottom, 16)
        }
        .scrollIndicators(.hidden)
        .background(Color.white)
        .glgPageTitle("절약 챌린지")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: store.spendings) { spentDays = Set(store.spendings.map { $0.dayKey }) }
        .overlay {
            if let b = openBadge {
                BadgeInfoDialog(badge: b, streak: Int(summary?.noSpendStreak ?? 0), best: Int(summary?.bestStreak ?? 0)) { openBadge = nil }
                    .transition(.opacity)
            }
        }
        .animation(GLGMotion.standard(), value: openBadge?.id)
    }

    private var band: some View { bandColor.frame(height: 10).frame(maxWidth: .infinity) }

    /// 섹션 — 좌우 20 · 위 22 · 아래 20. 제목 17 굵게 + 오른쪽 보조 12.
    private func section<C: View>(_ title: String?, _ trailing: String? = nil, top: CGFloat = 22, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                HStack {
                    Text(title).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 8)
                    if let trailing { Text(trailing).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
                }
                .padding(.bottom, 14)
            }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20).padding(.top, top).padding(.bottom, 20)
    }

    // ── ① 연속 무지출 + 최근 7일 ──
    private var streakSection: some View {
        section(nil, top: 12) {
            Text("연속 무지출").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Image(systemName: "flame.fill").font(.system(size: 24)).foregroundStyle(accent.primary)
                Text("\(summary?.noSpendStreak ?? 0)").font(.pretendard(size: 34, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("일째").font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            }
            .padding(.top, 2)
            Text("최고 기록 \(summary?.bestStreak ?? 0)일").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
            weekStrip.padding(.top, 16)
        }
    }

    /// 최근 7일 — 칸은 **그날의 결과**(무지출 ✓ · 지출 카트)를 보여 주고, 오늘은 테두리 + 아래 「오늘」로 표시한다.
    /// 예전엔 오늘 칸이 「오늘」 글자로 덮여 오늘 지출 여부가 안 보였다. 기호 글자(₩ · ✓) 대신 아이콘.
    private var weekStrip: some View {
        HStack(spacing: 7) {
            ForEach((0...6).reversed(), id: \.self) { ago in
                let key = DateUtil.shared.localDayKeyAgo(daysAgo: Int32(ago), nowMillis: nowMs())
                let spent = spentDays.contains(key)
                let isToday = ago == 0
                VStack(spacing: 5) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .fill(spent ? spentRed.opacity(0.14) : accent.primary.opacity(0.14))
                        if isToday {
                            RoundedRectangle(cornerRadius: 11, style: .continuous).strokeBorder(accent.primary, lineWidth: 2)
                        }
                        Image(systemName: spent ? "cart.fill" : "checkmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(spent ? spentRed : accent.primary)
                            .accessibilityLabel(spent ? "지출 있음" : "무지출")
                    }
                    .frame(height: 36)
                    Text(isToday ? "오늘" : DateUtil.shared.weekdayKo(millis: nowMs() - Int64(ago) * 86_400_000))
                        .font(.pretendard(size: 12, weight: .bold))
                        .foregroundStyle(isToday ? accent.primary : GLGColor.textSecondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    // ── ② 이번 달 챌린지 ──
    private func challengeSection(_ s: ChallengeSummary) -> some View {
        section("이번 달 챌린지", "\(s.challenges.filter { $0.reached }.count) / \(s.challenges.count) 달성") {
            ForEach(Array(s.challenges.enumerated()), id: \.offset) { idx, c in
                if idx > 0 { hairColor.frame(height: 1) }
                challengeRow(c)
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
            HStack(alignment: .top, spacing: 10) {
                if let gameColor {
                    Circle().fill(gameColor).frame(width: 8, height: 8).padding(.top, 7)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(c.title).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text(c.desc).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                }
                Spacer(minLength: 0)
                if c.reached {
                    HStack(spacing: 2) {
                        Image(systemName: "checkmark").font(.system(size: 13, weight: .bold))
                        Text("달성").font(.pretendard(size: 14, weight: .bold))
                    }
                    .foregroundStyle(tone)
                } else {
                    Text("\(c.current) / \(c.target)").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                }
            }
            progressBar(Double(c.ratio), c.warn ? warnAmber : tone).padding(.top, 10)
        }
        .padding(.vertical, 13)
    }

    // ── ③ 배지 컬렉션 ──
    private func badgeSection(_ s: ChallengeSummary) -> some View {
        section("획득 배지", "\(s.earnedBadgeCount) / \(s.totalBadgeCount)") {
            Text("배지를 누르면 얻는 방법을 볼 수 있어요")
                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                .padding(.top, -6).padding(.bottom, 16)
            let cols = Array(repeating: GridItem(.flexible(), spacing: 0), count: 4)
            LazyVGrid(columns: cols, spacing: 16) {
                ForEach(Array(s.badges.enumerated()), id: \.offset) { _, b in
                    Button { openBadge = b } label: { badgeCell(b) }.buttonStyle(.plain)
                }
            }
        }
    }

    private func badgeCell(_ b: BadgeState) -> some View {
        VStack(spacing: 6) {
            BadgeMedal(badge: b, size: 56)
            Text(b.title).font(.pretendard(size: 12, weight: .bold))
                .foregroundStyle(b.earned ? GLGColor.textPrimary : GLGColor.textSecondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }
}

/// 배지 원형 — 얻은 배지는 금빛, 못 얻은 배지는 **그 배지 아이콘을 흐리게** + 작은 자물쇠(무엇인지 보이게).
private struct BadgeMedal: View {
    let badge: BadgeState
    let size: CGFloat
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle().fill(badge.earned ? goldEarn.opacity(0.16) : bandColor)
                .frame(width: size, height: size)
                .overlay {
                    Image(systemName: badgeSymbol(badge.id))
                        .font(.system(size: size * 0.4, weight: .semibold))
                        .foregroundStyle(badge.earned ? goldEarn : lockedIcon)
                }
            if !badge.earned {
                Circle().fill(Color.white)
                    .overlay(Circle().strokeBorder(Color(hex: 0xFFE3E6EA), lineWidth: 1))
                    .frame(width: size * 0.36, height: size * 0.36)
                    .overlay {
                        Image(systemName: "lock.fill").font(.system(size: size * 0.17, weight: .bold))
                            .foregroundStyle(Color(hex: 0xFF9AA0A6))
                    }
            }
        }
        .frame(width: size, height: size)
    }
}

/// 배지 설명 모달 — Android GlgDialog 와 같은 가운데 카드(모서리 24 · 안쪽 22 · 확인 버튼 하나).
/// 큰 배지 · 이름 · 획득 상태 · 얻는 방법(공유 문구) · 스트릭 배지면 지금 기록.
private struct BadgeInfoDialog: View {
    let badge: BadgeState
    let streak: Int
    let best: Int
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.32).ignoresSafeArea().onTapGesture { onDismiss() }
            VStack(spacing: 0) {
                BadgeMedal(badge: badge, size: 76)
                Text(badge.title).font(.pretendard(size: 19, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .padding(.top, 14)
                Text(badge.earned ? "획득했어요" : "아직 못 얻었어요")
                    .font(.pretendard(size: 12, weight: .bold))
                    .foregroundStyle(badge.earned ? Color(hex: 0xFFB7791F) : GLGColor.textSecondary)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(badge.earned ? goldEarn.opacity(0.16) : bandColor, in: Capsule())
                    .padding(.top, 8)
                Text(badge.howTo).font(.pretendard(size: 14)).foregroundStyle(GLGColor.textPrimary)
                    .multilineTextAlignment(.center).lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 14)
                if streakBadges.contains(badge.id) {
                    Text("지금 \(streak)일째 · 최고 \(best)일").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        .padding(.top, 6)
                }
                GldsButton(title: "확인") { onDismiss() }.padding(.top, 20)
            }
            .frame(maxWidth: .infinity)
            .padding(22)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(GLGColor.divider, lineWidth: 1))
            .shadow(color: .black.opacity(0.18), radius: 24, y: 8)
            .padding(24)
            .frame(maxWidth: 480)
        }
    }
}

// ══════════════════════════════════════════════════════════════ 공용 소품

@ViewBuilder
private func progressBar(_ ratio: Double, _ color: Color, height: CGFloat = 6) -> some View {
    GeometryReader { geo in
        ZStack(alignment: .leading) {
            Capsule().fill(Color(hex: 0xFFEDEFF3))
            Capsule().fill(color).frame(width: geo.size.width * max(0, min(1, ratio)))
        }
    }.frame(height: height)
}
