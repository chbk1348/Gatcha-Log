import SwiftUI
import ComposeApp

// 홈 서브컴포넌트 — (Compose HomeRedesign/HomeScreen 대응)

private let warnText = Color(hex: 0xFFB37400)
private let dangerText = Color(hex: 0xFFD0021B)

// ── 이번 달 한눈에 (요약 — 3부 구조: MoM·예산/페이스·천장/픽업. 랜덤 변주는 생략) ──
struct HomeSummaryCard: View {
    let monthlyTotal: Int64; let prevTotal: Int64; let topPity: PityHighlight?
    let nextBanner: GachaBanner?; let gameOverCount: Int
    let onBudget: () -> Void; let onPity: () -> Void; let onTip: () -> Void
    @ObservedObject private var holder = Holder()  // store 없이 budget 접근 위해 — 아래 init 에서 주입
    @Environment(\.glgAccent) private var accent
    // budget 은 별도 주입이 번거로워 environment 대신 파생에서 받음 → 간단히 외부 계산값으로 처리
    var budget: Int64 { holder.budget }
    final class Holder: ObservableObject { var budget: Int64 = 0 }

    var body: some View {
        GLGCard(cornerRadius: 28, padding: 18) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles").font(.system(size: 16)).foregroundStyle(accent.primary)
                    Text("이번 달 한눈에").font(.system(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                }
                summaryText.padding(.top, 10)
                HStack(spacing: 8) {
                    chip(budget > 0 ? "예산 점검" : "예산 세우기", onBudget)
                    chip("천장 보기", onPity)
                    chip("절약 팁", onTip)
                }
                .padding(.top, 14)
            }
        }
    }

    private var summaryText: Text {
        var t = Text("")
        // ① MoM
        if monthlyTotal <= 0 {
            t = t + Text("이번 달은 아직 지출이 없어요.")
        } else if prevTotal > 0 {
            let diff = Int((monthlyTotal - prevTotal) * 100 / prevTotal)
            if diff >= 5 { t = t + Text("지난달보다 ") + Text("\(diff)% 더").bold().foregroundColor(dangerText) + Text(" 쓰고 있어요.") }
            else if diff <= -5 { t = t + Text("지난달보다 ") + Text("\(-diff)% 덜").bold().foregroundColor(accent.secondary) + Text(" 아꼈어요.") }
            else { t = t + Text("지난달과 비슷한 페이스예요.") }
        } else {
            t = t + Text("이번 달 ") + Text(won(monthlyTotal)).bold() + Text(" 쓰고 있어요.")
        }
        // ② 천장/픽업
        if let p = topPity, p.tier != .safe {
            t = t + Text(" ") + Text("\(p.game.shortName) 천장 \(p.count)/\(p.hard)").bold().foregroundColor(accent.secondary)
                + Text(p.tier == .reached ? ", 다음 보장 확정이에요." : ", 곧 보장이에요.")
        } else if let b = nextBanner {
            t = t + Text(" ") + Text(b.name).bold().foregroundColor(accent.secondary) + Text(" 픽업 진행 중이에요.")
        } else if gameOverCount > 0 {
            t = t + Text(" ") + Text("\(gameOverCount)개 게임").bold().foregroundColor(dangerText) + Text("이 한도를 넘었어요.")
        }
        return t.font(.system(size: 14)).foregroundColor(GLGColor.textPrimary)
    }

    private func chip(_ text: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.system(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                .padding(.horizontal, 13).padding(.vertical, 7)
                .background(accent.primary.opacity(0.10), in: Capsule())
                .overlay(Capsule().stroke(accent.primary.opacity(0.3), lineWidth: 1))
        }.buttonStyle(.plain)
    }
}

// ── 오늘 할 일 ──
struct TodayTaskCard: View {
    let tasks: [TodayItem]; let inProgress: Bool
    @Environment(\.glgAccent) private var accent
    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Image(systemName: "checklist").font(.system(size: 15)).foregroundStyle(accent.primary)
                    Text("오늘 할 일").font(.system(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                    if !tasks.isEmpty {
                        Text("\(tasks.count)").font(.system(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                            .padding(.horizontal, 7).padding(.vertical, 1).background(accent.primary.opacity(0.14), in: Capsule())
                    }
                }
                .padding(.bottom, 12)
                if tasks.isEmpty {
                    Text("오늘 챙길 건 다 끝냈어요 🎉 여유롭게 즐기세요").font(.system(size: 14))
                } else {
                    ForEach(Array(tasks.enumerated()), id: \.element.id) { i, t in
                        if i > 0 { Divider().padding(.vertical, 10) }
                        row(t)
                    }
                }
            }
        }
    }
    private func row(_ t: TodayItem) -> some View {
        let tint = t.urgent ? warnText : accent.primary
        let busy = t.busyable && inProgress
        return Button(action: t.action) {
            HStack(spacing: 10) {
                Image(systemName: t.icon).font(.system(size: 18)).foregroundStyle(tint)
                Text(t.message).font(.system(size: 14)).foregroundStyle(GLGColor.textPrimary).frame(maxWidth: .infinity, alignment: .leading).lineLimit(2)
                if busy { ProgressView().controlSize(.mini).tint(tint) }
                else {
                    HStack(spacing: 2) {
                        Text(t.cta).font(.system(size: 11, weight: .bold)).foregroundStyle(tint)
                        Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(tint)
                    }
                    .padding(.leading, 10).padding(.trailing, 7).padding(.vertical, 4)
                    .background(tint.opacity(0.12), in: Capsule())
                }
            }
        }.buttonStyle(.plain).disabled(busy)
    }
}

struct TodayTaskSkeleton: View {
    @Environment(\.glgAccent) private var accent
    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 6) {
                    Image(systemName: "checklist").font(.system(size: 15)).foregroundStyle(accent.primary.opacity(0.5))
                    Text("오늘 할 일").font(.system(size: 13, weight: .bold)).foregroundStyle(accent.primary.opacity(0.5))
                }
                ForEach(0..<3, id: \.self) { i in
                    if i > 0 { Divider() }
                    HStack(spacing: 10) {
                        Circle().fill(Color.black.opacity(0.06)).frame(width: 18, height: 18)
                        RoundedRectangle(cornerRadius: 4).fill(Color.black.opacity(0.06)).frame(height: 13).frame(maxWidth: .infinity)
                        RoundedRectangle(cornerRadius: 999).fill(Color.black.opacity(0.06)).frame(width: 56, height: 22)
                    }
                }
            }
        }
    }
}

// ── 가챠 현황 (천장 + 다음 픽업) ──
struct GachaStatusCard: View {
    let topPity: PityHighlight?; let nextBanner: GachaBanner?; let nextBannerPlan: BannerPlan?
    let onOpen: () -> Void; let onImport: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    HStack(spacing: 6) { Image(systemName: "die.face.5.fill").font(.system(size: 16)).foregroundStyle(accent.primary); Text("가챠 현황").font(.system(size: 16, weight: .bold)) }
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 14)).foregroundStyle(GLGColor.textSecondary)
                }
                .padding(.bottom, 14)
                if topPity == nil && nextBanner == nil {
                    Text("가챠 기록을 가져오면 천장·픽업이 표시돼요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    Button(action: onImport) {
                        HStack(spacing: 6) { Image(systemName: "square.and.arrow.down").font(.system(size: 14)); Text("가챠 기록 가져오기").font(.system(size: 12, weight: .bold)) }
                            .foregroundStyle(accent.primary).padding(.horizontal, 13).padding(.vertical, 7).background(accent.primary.opacity(0.10), in: Capsule())
                    }.buttonStyle(.plain).padding(.top, 12)
                } else {
                    HStack(spacing: 10) {
                        pityMini.frame(maxWidth: .infinity)
                        nextMini.frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .contentShape(Rectangle()).onTapGesture { onOpen() }
    }
    private var pityMini: some View {
        miniCard {
            Text("천장").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
            if let p = topPity {
                let c = pityTierColor(p.tier)
                Text(p.game.shortName).font(.system(size: 13, weight: .bold)).lineLimit(1)
                HStack(alignment: .bottom, spacing: 0) {
                    Text("\(p.count)").font(.system(size: 20, weight: .bold)).foregroundStyle(c)
                    Text("/\(p.hard)").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
                ProgressView(value: min(max(Double(p.count)/Double(p.hard), 0), 1)).tint(c)
                Text(pityShortLabel(p.tier)).font(.system(size: 10, weight: .bold)).foregroundStyle(c)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(c.opacity(0.12), in: Capsule())
            } else { Text("기록 없음").font(.system(size: 15, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
        }
    }
    private var nextMini: some View {
        miniCard {
            Text("다음 픽업").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
            if let b = nextBanner {
                let urgent = b.dDay(nowMillis: nowMs()) <= 3
                HStack(spacing: 6) { Circle().fill(Color(argb64: b.gameColor)).frame(width: 8, height: 8); Text(b.name).font(.system(size: 13, weight: .bold)).lineLimit(1) }
                Text(GameData.shared.byNameOrNull(name: b.game)?.shortName ?? b.game).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                Text(b.endShortLabel(nowMillis: nowMs())).font(.system(size: 10, weight: .bold)).foregroundStyle(urgent ? warnText : accent.primary)
                    .padding(.horizontal, 8).padding(.vertical, 2).background((urgent ? warnText : accent.primary).opacity(0.14), in: Capsule())
                if let plan = nextBannerPlan {
                    Text("확정 최대 \(plan.maxPulls)연").font(.system(size: 11, weight: .bold)).lineLimit(1)
                    Text("약 \(won(plan.wonCost))").font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
            } else { Text("예정 없음").font(.system(size: 15, weight: .bold)).foregroundStyle(GLGColor.textSecondary) }
        }
    }
    private func miniCard<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 4) { content() }
            .frame(maxWidth: .infinity, alignment: .leading).padding(13)
            .background(.white, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(GLGColor.divider, lineWidth: 1))
    }
}

private func pityTierColor(_ t: PityTierS) -> Color {
    switch t { case .reached: return Color(hex: 0xFFE53935); case .imminent: return Color(hex: 0xFFFB8C00); case .caution: return Color(hex: 0xFFF59E0B); case .safe: return Color(hex: 0xFF9AA0A6) }
}
private func pityShortLabel(_ t: PityTierS) -> String {
    switch t { case .reached: return "보장 확정"; case .imminent: return "곧 보장"; case .caution: return "주의"; case .safe: return "모으는 중" }
}

// ── 실시간 노트 ──
struct GameStatusSection: View {
    @ObservedObject var store: SpendingStore
    let onConfig: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        GLGCard(cornerRadius: 26, padding: 18) {
            if !store.hoyolabConfig.isLinked {
                VStack(spacing: 0) {
                    ZStack { Circle().fill(accent.primary.opacity(0.12)).frame(width: 48, height: 48); Image(systemName: "link").font(.system(size: 22)).foregroundStyle(accent.primary) }
                    Text("HoYoLAB 연동이 필요해요").font(.system(size: 14, weight: .bold)).padding(.top, 10)
                    Text("실시간 노트를 보려면 연동하세요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4)
                    GLGButton(title: "HoYoLAB 연동하러 가기", action: onConfig).padding(.top, 14)
                }
                .frame(maxWidth: .infinity)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    Text("실시간 노트").font(.system(size: 14, weight: .bold))
                    if !store.liveNotes.isEmpty {
                        VStack(spacing: 8) { ForEach(Array(store.liveNotes.enumerated()), id: \.offset) { _, n in NoteCapsule(note: n) } }.padding(.top, 12)
                    } else if store.isRefreshing {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text("실시간 노트 불러오는 중…").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        }.padding(.top, 10)
                    } else {
                        Text("표시할 노트가 없어요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
                    }
                }
            }
        }
    }
}

struct NoteCapsule: View {
    let note: LiveNote
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let full = note.maxResin > 0 && note.currentResin >= note.maxResin
        HStack(spacing: 10) {
            Image(systemName: "bolt.fill").font(.system(size: 18)).foregroundStyle(full ? dangerText : accent.primary)
            VStack(alignment: .leading, spacing: 0) {
                Text(GameData.shared.byName(name: note.game).shortName).font(.system(size: 13, weight: .bold)).lineLimit(1)
                Text(note.resinLabel).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            }
            Spacer()
            Text("\(note.currentResin)/\(note.maxResin)").font(.system(size: 14, weight: .bold)).foregroundStyle(full ? dangerText : GLGColor.textPrimary)
            if full {
                Text("가득참").font(.system(size: 11, weight: .bold)).foregroundStyle(dangerText)
                    .padding(.horizontal, 9).padding(.vertical, 3).background(dangerText.opacity(0.12), in: Capsule())
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 11)
        .background(full ? Color(hex: 0xFFFFE5E5) : .white, in: Capsule())
        .overlay(Capsule().stroke(full ? Color(hex: 0xFFFFE5E5) : GLGColor.divider, lineWidth: 1))
    }
}

struct BannerCapsule: View {
    let banner: GachaBanner
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let urgent = banner.dDay(nowMillis: nowMs()) <= 3
        let chipColor = urgent ? warnText : accent.primary
        return HStack(spacing: 10) {
            Circle().fill(Color(argb64: banner.gameColor)).frame(width: 8, height: 8)
            VStack(alignment: .leading, spacing: 0) {
                Text(banner.name).font(.system(size: 13, weight: .bold)).lineLimit(1)
                Text("\(GameData.shared.byNameOrNull(name: banner.game)?.shortName ?? banner.game) · 픽업").font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            }
            Spacer()
            Text(banner.endShortLabel(nowMillis: nowMs())).font(.system(size: 11, weight: .bold)).foregroundStyle(chipColor)
                .padding(.horizontal, 9).padding(.vertical, 3).background(chipColor.opacity(0.14), in: Capsule())
        }
        .padding(.horizontal, 16).padding(.vertical, 11)
        .glgGlass(in: Capsule())
    }
}

// ── 지출 + 게임별 예산 ──
struct SpendingBudgetSection: View {
    let monthlyTotal: Int64; let budget: Int64; let perGame: [GameSpend]; let onEdit: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let ratio = budget > 0 ? min(max(Double(monthlyTotal)/Double(budget), 0), 1) : 0
        let pct = budget > 0 ? Int(monthlyTotal * 100 / budget) : 0
        let over = budget > 0 && monthlyTotal > budget
        return GLGCard(cornerRadius: 26, padding: 20) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("이번 달 지출").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        Text("\(store_year)년 \(store_month)월").font(.system(size: 14, weight: .medium))
                    }
                    Spacer()
                    Button(action: onEdit) { Image(systemName: "pencil").font(.system(size: 16)).foregroundStyle(GLGColor.textSecondary) }.buttonStyle(.plain)
                }
                Text(won(monthlyTotal)).font(.system(size: 32, weight: .bold)).padding(.top, 4)
                if budget > 0 {
                    budgetBar(ratio, over).padding(.top, 16)
                    HStack {
                        Text(over ? "\(won(monthlyTotal - budget)) 초과" : "\(won(budget - monthlyTotal)) 남음").font(.system(size: 11)).foregroundStyle(over ? dangerText : GLGColor.textSecondary)
                        Spacer()
                        Text("예산 \(pct)% 사용").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    }.padding(.top, 8)
                } else {
                    Button(action: onEdit) {
                        HStack(spacing: 8) { Image(systemName: "banknote").font(.system(size: 16)).foregroundStyle(accent.primary); Text("월 예산 미설정 — 탭하여 설정하면 사용률이 표시돼요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary) }
                            .padding(12).frame(maxWidth: .infinity, alignment: .leading).background(accent.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain).padding(.top, 16)
                }
                if !perGame.isEmpty {
                    Divider().padding(.top, 18)
                    HStack { Text("게임별 예산").font(.system(size: 13, weight: .bold)); Spacer(); Text("한도 설정 ›").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).onTapGesture { onEdit() } }.padding(.top, 14)
                    VStack(spacing: 11) { ForEach(Array(perGame.enumerated()), id: \.offset) { _, gs in gameBudgetRow(gs) } }.padding(.top, 12)
                }
            }
        }
    }
    private var store_year: Int { Calendar.current.component(.year, from: Date()) }
    private var store_month: Int { Calendar.current.component(.month, from: Date()) }
    private func budgetBar(_ ratio: Double, _ over: Bool) -> some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(GLGColor.progressEmpty)
                Capsule().fill(over ? LinearGradient(colors: [Color(hex: 0xFFFF7A7A), dangerText], startPoint: .leading, endPoint: .trailing)
                                    : LinearGradient(colors: [accent.secondary, accent.primary], startPoint: .leading, endPoint: .trailing))
                    .frame(width: geo.size.width * (over ? 1 : ratio))
            }
        }.frame(height: 9)
    }
    private func gameBudgetRow(_ gs: GameSpend) -> some View {
        let hasLimit = gs.limit > 0
        let over = hasLimit && gs.spent > gs.limit
        let ratio = hasLimit ? min(max(Double(gs.spent)/Double(gs.limit), 0), 1) : 0
        return VStack(spacing: 5) {
            HStack {
                HStack(spacing: 7) { Circle().fill(Color(argb64: gs.game.color)).frame(width: 8, height: 8); Text(gs.game.shortName).font(.system(size: 13)) }
                Spacer()
                Text(hasLimit ? "\(won(gs.spent)) / \(won(gs.limit))" : "\(won(gs.spent)) · 한도 없음")
                    .font(.system(size: 12, weight: over ? .bold : .regular)).foregroundStyle(over ? dangerText : GLGColor.textSecondary)
            }
            if hasLimit {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(GLGColor.progressEmpty)
                        Capsule().fill(over ? LinearGradient(colors: [Color(hex: 0xFFFF7A7A), dangerText], startPoint: .leading, endPoint: .trailing)
                                            : LinearGradient(colors: [accent.secondary, accent.primary], startPoint: .leading, endPoint: .trailing))
                            .frame(width: geo.size.width * (over ? 1 : ratio))
                    }
                }.frame(height: 7)
            } else {
                Capsule().fill(GLGColor.progressEmpty.opacity(0.5)).frame(height: 7)
            }
        }
    }
}

// ── 가챠 요약 ──
struct GachaSummarySection: View {
    let stats: GachaStats?; let onOpen: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        GLGCard(cornerRadius: 24, padding: 20) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    HStack(spacing: 6) { Image(systemName: "die.face.5.fill").font(.system(size: 16)).foregroundStyle(accent.primary); Text("가챠 요약").font(.system(size: 16, weight: .bold)) }
                    Spacer(); Image(systemName: "chevron.right").font(.system(size: 14)).foregroundStyle(GLGColor.textSecondary)
                }
                if let s = stats {
                    let totalFive = s.byGame.values.reduce(0) { $0 + Int($1.five) }
                    HStack {
                        infoCol(num(Int(s.total)), "총 뽑기"); infoCol(num(totalFive), "획득 5성"); infoCol("\(s.byGame.count)", "게임")
                    }.padding(.top, 16)
                    let order = GachaReport.shared.gameOrder
                    let games = s.byGame.keys.sorted { (order.firstIndex(of: $0) ?? 99) < (order.firstIndex(of: $1) ?? 99) }
                    ForEach(Array(games.enumerated()), id: \.offset) { _, gk in
                        if let g = s.byGame[gk] {
                            HStack(spacing: 8) {
                                Circle().fill(gachaGameInfo(gk).color).frame(width: 8, height: 8)
                                Text(gachaGameInfo(gk).short).font(.system(size: 13, weight: .medium)); Spacer()
                                Text("\(num(Int(g.total)))뽑 · 5성 \(g.five)" + (g.avgPity > 0 ? " · 평균천장 \(g.avgPity)" : "")).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                            }.padding(.top, 10)
                        }
                    }
                } else {
                    Text("가챠 기록을 가져오면 요약이 표시돼요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 16)
                }
            }
        }
        .contentShape(Rectangle()).onTapGesture { onOpen() }
    }
    private func infoCol(_ v: String, _ l: String) -> some View {
        VStack(spacing: 2) { Text(v).font(.system(size: 15, weight: .bold)); Text(l).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary) }.frame(maxWidth: .infinity)
    }
}

struct TokenExpiredBanner: View {
    let onReconnect: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 22)).foregroundStyle(accent.primary)
            VStack(alignment: .leading, spacing: 0) {
                Text("HoYoLAB 토큰이 만료된 것 같아요").font(.system(size: 13, weight: .bold))
                Text("재연동하지 않으면 자동 출석이 안 돼요").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            Spacer()
            Button(action: onReconnect) { Text("재연동").font(.system(size: 13, weight: .bold)).foregroundStyle(.white).padding(.horizontal, 14).padding(.vertical, 9).background(accent.primary, in: RoundedRectangle(cornerRadius: 10)) }.buttonStyle(.plain)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(accent.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 20))
    }
}

// ── 알림 상세 (push) ──
struct NotificationDetailView: View {
    let alerts: [HomeAlert]; let onBudget: () -> Void; let onGameInfo: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Group {
            if alerts.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "bell.slash").font(.system(size: 44)).foregroundStyle(Color(.systemGray3))
                    Text("새로운 알림이 없어요 🎉").font(.system(size: 14)).foregroundStyle(GLGColor.textSecondary)
                    Text("예산·픽업 배너·출석 알림이 여기에 모여요").font(.system(size: 12)).foregroundStyle(Color(.systemGray3))
                }.frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(alerts) { a in card(a) }
                    }.padding(16)
                }
            }
        }
        .background(GLGBackground { Color.clear })
        .navigationTitle("알림").navigationBarTitleDisplayMode(.inline)
    }
    private func card(_ a: HomeAlert) -> some View {
        let (icon, tint, hint): (String, Color, String) = {
            switch a.kind {
            case .budgetOver: return ("banknote", dangerText, "예산 설정하기")
            case .budgetNear: return ("banknote", warnText, "예산 설정하기")
            case .budgetGameOver: return ("banknote", dangerText, "예산 설정하기")
            case .banner: return ("bolt.fill", accent.primary, "게임 정보 보기")
            case .attendance: return ("checkmark.circle", accent.primary, "출석하러 가기")
            }
        }()
        return Button { switch a.kind { case .banner, .attendance: onGameInfo(); default: onBudget() } } label: {
            GLGCard(cornerRadius: 18, padding: 16) {
                HStack(spacing: 12) {
                    ZStack { Circle().fill(tint.opacity(0.12)).frame(width: 38, height: 38); Image(systemName: icon).font(.system(size: 18)).foregroundStyle(tint) }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(a.message).font(.system(size: 13, weight: .medium))
                        Text(hint).font(.system(size: 11, weight: .semibold)).foregroundStyle(accent.primary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 14)).foregroundStyle(Color(.systemGray3))
                }
            }
        }.buttonStyle(.plain)
    }
}

// ── 홈 카드 편집 ──
struct HomeCardEditSheet: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.dismiss) private var dismiss
    @State private var list: [HomeCardItem] = []
    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(list.enumerated()), id: \.offset) { i, c in
                    HStack {
                        Text(HomeCards.shared.labels[c.id] ?? c.id).font(.system(size: 14, weight: .medium))
                        Spacer()
                        Toggle("", isOn: Binding(get: { c.visible }, set: { v in list[i] = HomeCardItem(id: c.id, visible: v) })).labelsHidden().tint(GLGTheme.accent(store.accentIndex).primary)
                    }
                }
                .onMove { from, to in list.move(fromOffsets: from, toOffset: to) }
                Text("프로필·게임 현황 카드는 항상 표시돼요.").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            .environment(\.editMode, .constant(.active))
            .navigationTitle("홈 카드 편집").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("저장") { store.setHomeCards(list); dismiss() } }
            }
            .onAppear { list = store.homeCards }
        }
    }
}
