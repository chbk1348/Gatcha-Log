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
    case "king": return "crown.fill"
    default: return "star.fill"
    }
}

/// SavingsPlan(Kotlin) 은 Identifiable 이 아니라 .sheet(item:) 용 래퍼.
private struct PlanEdit: Identifiable {
    let plan: SavingsPlan
    var id: String { plan.key }
}

// ══════════════════════════════════════════════════════════════ A. 저축 플래너

struct SavingsPlannerView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var editing: PlanEdit? = nil
    @State private var showHidden = false

    private var plans: [SavingsPlan] { store.savingsPlans }
    private var hidden: [SavingsPlan] { store.hiddenSavingsPlans }
    private var hero: SavingsPlan? { plans.first { !$0.secured } ?? plans.first }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if plans.isEmpty {
                    emptyState
                } else {
                    if let h = hero { heroCard(h) }
                    upcomingCard
                    Text("필요 뽑기는 현재 천장·50/50을 반영한 최악 기준이에요. 확률·요율 기반 계산(AI 아님).")
                        .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        .padding(.horizontal, 4)
                }
                if showHidden && !hidden.isEmpty { hiddenCard }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("저축 플래너")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !hidden.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showHidden.toggle() } label: {
                        Image(systemName: showHidden ? "eye.slash" : "eye")
                            .overlay(alignment: .topTrailing) {
                                if !showHidden {
                                    Text("\(hidden.count)").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(.white)
                                        .padding(3).background(accent.primary, in: Circle()).offset(x: 9, y: -9)
                                }
                            }
                    }
                    .accessibilityLabel(showHidden ? "숨긴 목표 접기" : "숨긴 목표 보기")
                }
            }
        }
        .sheet(item: $editing) { target in
            PlanInputSheet(store: store, plan: target.plan) { editing = nil }
        }
    }

    private var hiddenCard: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Image(systemName: "eye.slash").font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    Text("숨긴 목표").font(.pretendard(size: 14, weight: .bold))
                    Spacer()
                    Text("\(hidden.count)개").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                }
                ForEach(Array(hidden.enumerated()), id: \.offset) { idx, p in
                    HStack(spacing: 11) {
                        Circle().fill(Color(argb64: p.gameColor)).frame(width: 9, height: 9)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(p.pickupName).font(.pretendard(size: 13.5, weight: .semibold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                            Text("\(p.game) · \(p.type == "weapon" ? "무기" : "캐릭터")").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        }
                        Spacer()
                        Button { store.setSavingsHidden(key: p.key, hidden: false) } label: {
                            Text("다시 표시").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                                .padding(.horizontal, 11).padding(.vertical, 6)
                                .background(accent.primary.opacity(0.14), in: RoundedRectangle(cornerRadius: 9))
                        }.buttonStyle(.plain)
                    }.padding(.vertical, 11)
                    if idx < hidden.count - 1 { Divider() }
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Text("진행 중인 픽업 목표가 없어요.\n새 픽업이 시작되면 '하루 얼마 모으면 확보'인지 계산해 드려요.")
                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.top, 50)
    }

    private func heroCard(_ p: SavingsPlan) -> some View {
        Button { editing = PlanEdit(plan: p) } label: {
            GLGCard(cornerRadius: 24, padding: 18) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 8) {
                        Circle().fill(Color(argb64: p.gameColor)).frame(width: 9, height: 9)
                        Text("\(p.pickupName) · \(p.game)").font(.pretendard(size: 12.5, weight: .semibold)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                        Spacer()
                        pill(ddLabel(p.dDay), warnAmber)
                    }
                    if p.secured {
                        Text("이미 확보 가능해요 🎉").font(.pretendard(size: 24, weight: .bold)).foregroundStyle(GLGColor.textPrimary).padding(.top, 9)
                        Text("보유 재화만으로 천장까지 도달해요.").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                    } else {
                        HStack(alignment: .firstTextBaseline, spacing: 3) {
                            Text("하루 \(won(p.dailyGoal))").font(.pretendard(size: 30, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("씩").font(.pretendard(size: 14, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                        }.padding(.top, 9)
                        Text("지금부터 매일 이만큼이면 \(p.neededPulls)뽑까지 안전해요").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(accent.primary)
                    }
                    progressBar(Double(p.progressPercent) / 100.0, accent.primary, height: 9).padding(.top, 13)
                    HStack {
                        Text("모음 \(won(p.savedWon))").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                        Spacer()
                        Text("목표 \(won(p.neededWonTotal))").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    }.padding(.top, 6)
                    HStack(spacing: 8) {
                        infoPill("필요 뽑기", "\(p.neededPulls)뽑")
                        infoPill("남은 \(p.currency)", p.secured ? "0" : commaInt(p.remainingCurrency))
                    }.padding(.top, 13)
                }
            }
        }.buttonStyle(.plain)
    }

    private var upcomingCard: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                Text("다가오는 픽업 목표").font(.pretendard(size: 14, weight: .bold))
                ForEach(Array(plans.enumerated()), id: \.offset) { idx, p in
                    Button { editing = PlanEdit(plan: p) } label: { planRow(p) }.buttonStyle(.plain)
                    if idx < plans.count - 1 { Divider() }
                }
            }
        }
    }

    private func planRow(_ p: SavingsPlan) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 11) {
                Circle().fill(Color(argb64: p.gameColor)).frame(width: 9, height: 9)
                VStack(alignment: .leading, spacing: 1) {
                    Text(p.pickupName).font(.pretendard(size: 14, weight: .semibold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    Text("\(p.game) · \(p.type == "weapon" ? "무기" : "캐릭터") · \(p.neededPulls)뽑 필요").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
                Spacer()
                if p.secured { pill("✓ 확보", accent.primary) } else { pill(ddLabel(p.dDay), (0...7).contains(Int(p.dDay)) ? warnAmber : GLGColor.textSecondary) }
                Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(Color(.tertiaryLabel))
            }
            HStack(spacing: 12) {
                Text("필요 \(won(p.neededWonTotal))").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                Text("천장 \(p.currentPity)\(p.guaranteed ? " (확정)" : "")").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }.padding(.top, 8)
            progressBar(Double(p.progressPercent) / 100.0, accent.primary).padding(.top, 7)
            HStack {
                Text(p.secured ? "추가 저축 불필요" : "하루 \(won(p.dailyGoal))").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                Spacer()
                Text("\(p.progressPercent)%").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            }.padding(.top, 6)
        }
        .contentShape(Rectangle())
        .padding(.vertical, 13)
    }

    private func pill(_ t: String, _ c: Color) -> some View {
        Text(t).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(c)
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(c.opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
    }
    private func infoPill(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.pretendard(size: 11, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
            Text(value).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Color.black.opacity(0.03), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

// ── 내 상태 입력 시트 ──
struct PlanInputSheet: View {
    var store: SpendingStore
    let plan: SavingsPlan
    let onClose: () -> Void
    @Environment(\.glgAccent) private var accent

    @State private var pity: String = ""
    @State private var held: String = ""
    @State private var guaranteed = false
    @State private var didInit = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("\(plan.pickupName) · \(plan.game)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 5) {
                            label("현재 천장")
                            TextField("0", text: $pity).textFieldStyle(.plain).keyboardType(.numberPad)
                                .font(.pretendard(size: 15)).glgPillField()
                                .onChange(of: pity) { _, v in pity = v.filter(\.isNumber) }
                        }
                        VStack(alignment: .leading, spacing: 5) {
                            label("보유 \(plan.currency)")
                            TextField("0", text: $held).textFieldStyle(.plain).keyboardType(.numberPad)
                                .font(.pretendard(size: 15)).glgPillField()
                                .onChange(of: held) { _, v in held = v.filter(\.isNumber) }
                        }
                    }
                    label("50/50 상태")
                    HStack(spacing: 8) {
                        GLGChip(label: "50:50 (미확정)", selected: !guaranteed, color: accent.primary) { guaranteed = false }
                        GLGChip(label: "픽업 확정", selected: guaranteed, color: accent.primary) { guaranteed = true }
                    }
                    Divider().padding(.top, 4)
                    Button {
                        store.setSavingsHidden(key: plan.key, hidden: true)
                        onClose()
                    } label: {
                        HStack(spacing: 7) {
                            Image(systemName: "eye.slash").font(.system(size: 14)).foregroundStyle(GLGColor.textSecondary)
                            Text("이 픽업은 안 뽑아요 · 목록에서 숨기기").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                            Spacer()
                        }.contentShape(Rectangle())
                    }.buttonStyle(.plain)
                }
                .padding(16)
            }
            .background(Color.white)
            .navigationTitle("내 상태 입력")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { onClose() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("계산") {
                        store.setPityCount(gameKey: plan.gameKey, value: Int(pity) ?? 0)
                        store.setPityGuaranteed(gameKey: plan.gameKey, guaranteed)
                        store.setHeldCurrency(gameKey: plan.gameKey, value: Int(held) ?? 0)
                        onClose()
                    }
                }
            }
        }
        .onAppear {
            guard !didInit else { return }; didInit = true
            pity = "\(plan.currentPity)"
            held = plan.heldCurrency > 0 ? "\(plan.heldCurrency)" : ""
            guaranteed = plan.guaranteed
        }
    }

    private func label(_ t: String) -> some View {
        Text(t).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
    }
}

// ══════════════════════════════════════════════════════════════ H. 절약 챌린지

struct SavingsChallengeView: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var summary: ChallengeSummary? { store.challenge }

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
        .navigationTitle("절약 챌린지")
        .navigationBarTitleDisplayMode(.inline)
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
        let spentDays = Set(store.spendings.map { $0.dayKey })
        return HStack(spacing: 7) {
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
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(c.title).font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Text(c.desc).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
                Spacer()
                Text(c.reached ? "달성 ✓" : "\(c.current) / \(c.target)")
                    .font(.pretendard(size: 13, weight: .bold))
                    .foregroundStyle(c.reached ? accent.primary : GLGColor.textPrimary)
            }
            progressBar(Double(c.ratio), c.warn ? warnAmber : accent.primary).padding(.top, 9)
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

struct PickupPlannerHomeCard: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    private var top: SavingsPlan? { store.savingsPlans.first { !$0.secured } ?? store.savingsPlans.first }

    var body: some View {
        GLGCard(cornerRadius: 18, padding: 15) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Image(systemName: "banknote").font(.system(size: 16)).foregroundStyle(accent.primary).frame(width: 30, height: 30)
                        .background(accent.primary.opacity(0.14), in: RoundedRectangle(cornerRadius: 10))
                    Text("픽업 대비 저축 계획").font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    Spacer()
                    Text("열기 ›").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                }
                if let t = top {
                    if t.secured {
                        Text("\(t.pickupName) — 이미 확보 가능 🎉").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).padding(.top, 11)
                    } else {
                        HStack {
                            Text(t.pickupName).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                            Spacer()
                            Text("하루 \(won(t.dailyGoal)) · \(ddLabel(t.dDay))").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                        }.padding(.top, 11)
                        progressBar(Double(t.progressPercent) / 100.0, accent.primary).padding(.top, 9)
                    }
                } else {
                    Text("진행 중인 픽업 목표가 없어요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 11)
                }
            }
        }
    }
}

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
