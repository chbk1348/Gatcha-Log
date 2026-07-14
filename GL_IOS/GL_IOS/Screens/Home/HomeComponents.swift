import SwiftUI
import Shared

// 홈 서브컴포넌트 — (Compose HomeRedesign/HomeScreen 대응)

private let warnText = Color(hex: 0xFFB37400)
private let dangerText = Color(hex: 0xFFD0021B)

// ── 이번 달 한눈에 (요약 — 3부 구조: MoM·예산/페이스·천장/픽업. 랜덤 변주는 생략) ──
struct HomeSummaryCard: View {
    let monthlyTotal: Int64; let prevTotal: Int64
    let nextBanner: GachaBanner?; let gameOverCount: Int
    let onBudget: () -> Void; let onTip: () -> Void
    @ObservedObject private var holder = Holder()  // store 없이 budget 접근 위해 — 아래 init 에서 주입
    @Environment(\.glgAccent) private var accent
    // budget 은 별도 주입이 번거로워 environment 대신 파생에서 받음 → 간단히 외부 계산값으로 처리
    var budget: Int64 { holder.budget }
    final class Holder: ObservableObject { var budget: Int64 = 0 }

    var body: some View {
        GLGCard(cornerRadius: 28, padding: 18) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles").font(.pretendard(size: 16)).foregroundStyle(accent.primary)
                    Text("이번 달 한눈에").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                }
                summaryText.padding(.top, 10)
                HStack(spacing: 8) {
                    chip(budget > 0 ? "예산 점검" : "예산 세우기", onBudget)
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
        // ② 픽업/예산
        if let b = nextBanner {
            t = t + Text(" ") + Text(b.name).bold().foregroundColor(accent.secondary) + Text(" 픽업 진행 중이에요.")
        } else if gameOverCount > 0 {
            t = t + Text(" ") + Text("\(gameOverCount)개 게임").bold().foregroundColor(dangerText) + Text("이 한도를 넘었어요.")
        }
        return t.font(.pretendard(size: 14)).foregroundColor(GLGColor.textPrimary)
    }

    private func chip(_ text: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
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
                    Image(systemName: "checklist").font(.pretendard(size: 15)).foregroundStyle(accent.primary)
                    Text("오늘 할 일").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                    if !tasks.isEmpty {
                        Text("\(tasks.count)").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                            .padding(.horizontal, 7).padding(.vertical, 1).background(accent.primary.opacity(0.14), in: Capsule())
                    }
                }
                .padding(.bottom, 12)
                if tasks.isEmpty {
                    Text("오늘 챙길 건 다 끝냈어요 🎉 여유롭게 즐기세요").font(.pretendard(size: 14))
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
                Image(systemName: t.icon).font(.pretendard(size: 18)).foregroundStyle(tint)
                Text(t.message).font(.pretendard(size: 14)).foregroundStyle(GLGColor.textPrimary).frame(maxWidth: .infinity, alignment: .leading).lineLimit(2)
                if busy { ProgressView().controlSize(.mini).tint(tint) }
                else {
                    HStack(spacing: 2) {
                        Text(t.cta).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(tint)
                        Image(systemName: "chevron.right").font(.pretendard(size: 11)).foregroundStyle(tint)
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
                    Image(systemName: "checklist").font(.pretendard(size: 15)).foregroundStyle(accent.primary.opacity(0.5))
                    Text("오늘 할 일").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary.opacity(0.5))
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

/// 대시보드 리스트 카드 로딩 스켈레톤 — 헤더 + 행 N개. '이번 주 일정'·'게임 소식' 카드와 동일 형태. (Android DashCardSkeleton 패리티)
struct DashCardSkeleton: View {
    var rows: Int = 3
    var body: some View {
        GLGCard(cornerRadius: 22, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                GLGSkeleton().frame(width: 90, height: 15)
                ForEach(0..<rows, id: \.self) { _ in
                    HStack(spacing: 9) {
                        GLGSkeleton(cornerRadius: 9).frame(width: 28, height: 28)
                        GLGSkeleton().frame(maxWidth: .infinity).frame(height: 13)
                        GLGSkeleton().frame(width: 34, height: 12)
                    }.padding(.top, 13)
                }
            }
        }
    }
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
                    ZStack { Circle().fill(accent.primary.opacity(0.12)).frame(width: 48, height: 48); Image(systemName: "link").font(.pretendard(size: 22)).foregroundStyle(accent.primary) }
                    Text("HoYoLAB 연동이 필요해요").font(.pretendard(size: 14, weight: .bold)).padding(.top, 10)
                    Text("실시간 노트를 보려면 연동하세요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4)
                    GLGButton(title: "HoYoLAB 연동하러 가기", action: onConfig).padding(.top, 14)
                }
                .frame(maxWidth: .infinity)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    Text("실시간 노트").font(.pretendard(size: 14, weight: .bold))
                    if !store.liveNotes.isEmpty {
                        VStack(spacing: 8) { ForEach(Array(store.liveNotes.enumerated()), id: \.offset) { _, n in NoteCapsule(note: n) } }.padding(.top, 12)
                    } else if store.isRefreshing {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text("실시간 노트 불러오는 중…").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        }.padding(.top, 10)
                    } else {
                        Text("표시할 노트가 없어요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 8)
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
            Image(systemName: "bolt.fill").font(.pretendard(size: 18)).foregroundStyle(full ? dangerText : accent.primary)
            VStack(alignment: .leading, spacing: 0) {
                Text(GameData.shared.byName(name: note.game).shortName).font(.pretendard(size: 13, weight: .bold)).lineLimit(1)
                Text(note.resinLabel).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            }
            Spacer()
            Text(verbatim: "\(note.currentResin)/\(note.maxResin)").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(full ? dangerText : GLGColor.textPrimary)
            if full {
                Text("가득참").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(dangerText)
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
            GLGGameTag(game: banner.game, size: .small)
            VStack(alignment: .leading, spacing: 0) {
                Text(banner.name).font(.pretendard(size: 13, weight: .bold)).lineLimit(1)
                Text("\(GameData.shared.byNameOrNull(name: banner.game)?.shortName ?? banner.game) · 픽업").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            }
            Spacer()
            Text(GameInfoKt.dhLabel(targetMillis: banner.endMillis, nowMillis: nowMs())).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(chipColor).lineLimit(1)
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
                        Text("이번 달 지출").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        Text(verbatim: "\(store_year)년 \(store_month)월").font(.pretendard(size: 14, weight: .medium))
                    }
                    Spacer()
                    Button(action: onEdit) { Image(systemName: "pencil").font(.pretendard(size: 16)).foregroundStyle(GLGColor.textSecondary) }.buttonStyle(.plain)
                }
                Text(won(monthlyTotal)).font(.pretendard(size: 32, weight: .bold)).padding(.top, 4)
                if budget > 0 {
                    budgetBar(ratio, over).padding(.top, 16)
                    HStack {
                        Text(over ? "\(won(monthlyTotal - budget)) 초과" : "\(won(budget - monthlyTotal)) 남음").font(.pretendard(size: 11)).foregroundStyle(over ? dangerText : GLGColor.textSecondary)
                        Spacer()
                        Text("예산 \(pct)% 사용").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    }.padding(.top, 8)
                } else {
                    Button(action: onEdit) {
                        HStack(spacing: 8) { Image(systemName: "banknote").font(.pretendard(size: 16)).foregroundStyle(accent.primary); Text("월 예산 미설정 — 탭하여 설정하면 사용률이 표시돼요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary) }
                            .padding(12).frame(maxWidth: .infinity, alignment: .leading).background(accent.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain).padding(.top, 16)
                }
                if !perGame.isEmpty {
                    Divider().padding(.top, 18)
                    HStack { Text("게임별 예산").font(.pretendard(size: 13, weight: .bold)); Spacer(); Text("한도 설정 ›").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).onTapGesture { onEdit() } }.padding(.top, 14)
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
                HStack(spacing: 8) { GLGGameTag(game: gs.game.displayName, size: .small); Text(gs.game.shortName).font(.pretendard(size: 13)) }
                Spacer()
                Text(hasLimit ? "\(won(gs.spent)) / \(won(gs.limit))" : "\(won(gs.spent)) · 한도 없음")
                    .font(.pretendard(size: 12, weight: over ? .bold : .regular)).foregroundStyle(over ? dangerText : GLGColor.textSecondary)
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

struct TokenExpiredBanner: View {
    let onReconnect: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill").font(.pretendard(size: 22)).foregroundStyle(accent.primary)
            VStack(alignment: .leading, spacing: 0) {
                Text("HoYoLAB 토큰이 만료된 것 같아요").font(.pretendard(size: 13, weight: .bold))
                Text("재연동하지 않으면 자동 출석이 안 돼요").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            Spacer()
            Button(action: onReconnect) { Text("재연동").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(.white).padding(.horizontal, 14).padding(.vertical, 9).background(accent.primary, in: RoundedRectangle(cornerRadius: 10)) }.buttonStyle(.plain)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(accent.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 20))
    }
}

// ── 알림 상세 (push) ──
struct NotificationDetailView: View {
    let alerts: [HomeAlert]; let onBudget: () -> Void; let onGameInfo: () -> Void
    let onDismiss: (HomeAlert) -> Void; let onDismissAll: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Group {
            if alerts.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "bell.slash").font(.pretendard(size: 44)).foregroundStyle(Color(.systemGray3))
                    Text("새로운 알림이 없어요 🎉").font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
                    Text("예산·픽업 배너·출석 알림이 여기에 모여요").font(.pretendard(size: 12)).foregroundStyle(Color(.systemGray3))
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
        .toolbar {
            // 모두 지우기 — 한 번에 전체 dismiss
            if !alerts.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("모두 지우기") { onDismissAll() }
                        .font(.pretendard(size: 13, weight: .semibold))
                        .tint(GLGColor.textSecondary)
                }
            }
        }
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
        return GLGCard(cornerRadius: 18, padding: 16) {
            HStack(spacing: 12) {
                // 본문 탭 → 관련 화면 이동
                Button { switch a.kind { case .banner, .attendance: onGameInfo(); default: onBudget() } } label: {
                    HStack(spacing: 12) {
                        ZStack { Circle().fill(tint.opacity(0.12)).frame(width: 38, height: 38); Image(systemName: icon).font(.pretendard(size: 18)).foregroundStyle(tint) }
                        VStack(alignment: .leading, spacing: 3) {
                            Text(a.message).font(.pretendard(size: 13, weight: .medium))
                            Text(hint).font(.pretendard(size: 11, weight: .semibold)).foregroundStyle(accent.primary)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }.buttonStyle(.plain)
                // 삭제(X) — 이 알림만 지움(다시 안 뜸). 본문 탭(이동)과 분리.
                Button { onDismiss(a) } label: {
                    Image(systemName: "xmark").font(.pretendard(size: 14, weight: .semibold))
                        .foregroundStyle(Color(.systemGray3)).frame(width: 28, height: 28)
                }.buttonStyle(.plain)
            }
        }
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
                        Text(HomeCards.shared.labels[c.id] ?? c.id).font(.pretendard(size: 14, weight: .medium))
                        Spacer()
                        Toggle("", isOn: Binding(get: { c.visible }, set: { v in list[i] = HomeCardItem(id: c.id, visible: v) })).labelsHidden().tint(GLGTheme.accent(store.accentIndex).primary)
                    }
                }
                .onMove { from, to in list.move(fromOffsets: from, toOffset: to) }
                Text("프로필·게임 현황 카드는 항상 표시돼요.").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
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

// ════════════════════════════════════════════════════════════════════════════
// 홈 대시보드 개편(27.32.0) — 깔끔한 KPI 중심 레이아웃
// ════════════════════════════════════════════════════════════════════════════

/// 히어로 — 이번 달 지출/예산 게이지.
struct DashboardSpendCard: View {
    let monthlyTotal: Int64
    let budget: Int64
    let onTap: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let cal = Calendar.current
        let now = Date()
        let month = cal.component(.month, from: now)
        let day = cal.component(.day, from: now)
        let days = cal.range(of: .day, in: .month, for: now)?.count ?? 30
        let remain = max(days - day, 0)
        let pct = budget > 0 ? Int(monthlyTotal * 100 / budget) : 0
        let frac = budget > 0 ? min(Double(monthlyTotal) / Double(budget), 1) : 0
        let over = budget > 0 && monthlyTotal > budget
        let danger = Color(hex: 0xFFEF4444)
        return GLGCard(cornerRadius: 22, padding: 18) {
            VStack(alignment: .leading, spacing: 0) {
                Text("\(month)월 지출").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text(won(monthlyTotal)).font(.pretendard(size: 28, weight: .bold))
                    if budget > 0 {
                        Text("/ 예산 \(won(budget))").font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                    }
                }.padding(.top, 2)
                if budget > 0 {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(GLGColor.progressEmpty)
                            Capsule().fill(over ? danger : accent.primary).frame(width: geo.size.width * frac)
                        }
                    }.frame(height: 9).padding(.top, 12)
                    HStack {
                        Text(over ? "예산 \(pct - 100)% 초과" : "예산의 \(pct)% 사용")
                            .font(.pretendard(size: 11.5, weight: .semibold)).foregroundStyle(over ? danger : accent.primary)
                        Spacer()
                        Text("남은 \(remain)일").font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                    }.padding(.top, 8)
                } else {
                    Text("예산을 정하면 페이스를 알려드려요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 10)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }
}

/// KPI 2×2 그리드.
struct DashboardKpiGrid: View {
    let nextDDay: Int?
    let pityValue: String
    let pityLabel: String
    let todayCount: Int
    let unread: Int

    var body: some View {
        let cols = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]
        return LazyVGrid(columns: cols, spacing: 10) {
            KpiTile(icon: "target", value: nextDDay != nil ? "D-\(nextDDay!)" : "없음", label: "다음 픽업", tint: Color(hex: 0xFF15C7A8))
            KpiTile(icon: "speedometer", value: pityValue, label: pityLabel, tint: Color(hex: 0xFFF59E0B))
            KpiTile(icon: "checklist", value: "\(todayCount)건", label: "오늘 할 일", tint: Color(hex: 0xFF3B82F6))
            KpiTile(icon: "bell.fill", value: "\(unread)건", label: "안 읽은 알림", tint: Color(hex: 0xFFEF4444))
        }
    }
}

struct KpiTile: View {
    let icon: String; let value: String; let label: String; let tint: Color
    var body: some View {
        GLGCard(cornerRadius: 16, padding: 13) {
            HStack(spacing: 11) {
                Image(systemName: icon).font(.system(size: 16, weight: .semibold)).foregroundStyle(tint)
                    .frame(width: 38, height: 38)
                    .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(value).font(.pretendard(size: 17, weight: .bold)).lineLimit(1).minimumScaleFactor(0.7)
                    Text(label).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
        }
    }
}

/// 빠른 실행 3열.
struct DashboardQuickRow: View {
    let onSpend: () -> Void; let onCalc: () -> Void; let onInsight: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            QuickBtn(icon: "plus", label: "지출 추가", action: onSpend)
            QuickBtn(icon: "function", label: "가챠 계산기", action: onCalc)
            QuickBtn(icon: "chart.line.uptrend.xyaxis", label: "인사이트", action: onInsight)
        }
    }
}

struct QuickBtn: View {
    let icon: String; let label: String; let action: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(accent.primary)
                Text(label).font(.pretendard(size: 11.5, weight: .semibold)).foregroundStyle(GLGColor.textPrimary)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 14)
            .glgGlass(in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }.buttonStyle(.plain)
    }
}

/// 이번 주 게임 일정 — 이벤트·정기콘텐츠 마감 임박(픽업과 별개).
struct DashboardScheduleCard: View {
    let events: [GameEvent]; let challenges: [GameChallenge]; let onTap: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let now = nowMs()
        let raw: [(String, String, Int64, String)] =
            events.map { ($0.game, $0.name, $0.endMillis, $0.dDayLabel(nowMillis: now)) }
            + challenges.map { ($0.game, $0.name, $0.endMillis, $0.dDayLabel(nowMillis: now)) }
        let items = Array(raw.filter { $0.2 > now }.sorted { $0.2 < $1.2 }.prefix(3))
        return Group {
            if !items.isEmpty {
                GLGCard(cornerRadius: 22, padding: 16) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("이번 주 일정").font(.pretendard(size: 14, weight: .bold))
                            Spacer()
                            Text("전체 ›").font(.pretendard(size: 11.5, weight: .semibold)).foregroundStyle(accent.primary)
                        }
                        ForEach(Array(items.enumerated()), id: \.offset) { _, it in
                            HStack(spacing: 9) {
                                GLGGameTag(game: it.0, size: .small)
                                Text(it.1).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                                Spacer(minLength: 6)
                                Text(it.3).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                            }.padding(.top, 11)
                        }
                    }
                }
                .contentShape(Rectangle()).onTapGesture { onTap() }
            }
        }
    }
}

/// 게임 소식 — 다가오는 주년 + 최신 공지.
struct DashboardNewsCard: View {
    let news: [NewsItem]; let anniversaries: [AnniversaryInfo]; let onTap: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let anni = anniversaries.first { $0.daysUntil <= 60 }
        let topNews = Array(news.sorted { $0.createdAtMillis > $1.createdAtMillis }.prefix(2))
        let amber = Color(hex: 0xFFF59E0B)
        return Group {
            if anni != nil || !topNews.isEmpty {
                GLGCard(cornerRadius: 22, padding: 16) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("게임 소식").font(.pretendard(size: 14, weight: .bold))
                            Spacer()
                            Text("전체 ›").font(.pretendard(size: 11.5, weight: .semibold)).foregroundStyle(accent.primary)
                        }
                        if let a = anni {
                            HStack(spacing: 8) {
                                Image(systemName: "party.popper.fill").font(.system(size: 13)).foregroundStyle(amber)
                                Text("\(a.game.shortName) \(a.ordinal)주년").font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textPrimary)
                                Spacer(minLength: 6)
                                Text(a.daysUntil == 0 ? "오늘" : "D-\(a.daysUntil)").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(amber)
                            }
                            .padding(11).frame(maxWidth: .infinity)
                            .background(amber.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .padding(.top, 12)
                        }
                        ForEach(Array(topNews.enumerated()), id: \.offset) { _, n in
                            HStack(spacing: 9) {
                                GLGGameTag(game: n.game, size: .small)
                                Text(n.title).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                            }.padding(.top, 11)
                        }
                    }
                }
                .contentShape(Rectangle()).onTapGesture { onTap() }
            }
        }
    }
}
