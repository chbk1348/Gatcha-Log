import SwiftUI
import Shared

// 홈 서브컴포넌트 — (Compose HomeRedesign/HomeScreen 대응)

private let warnText = Color(hex: 0xFFB37400)
private let dangerText = Color(hex: 0xFFD0021B)

// ── 오늘 할 일 ──
struct TodayTaskCard: View {
    let tasks: [TodayItem]; let inProgress: Bool
    /// true 면 제목을 카드 바깥 위(큰 헤더)로. false(iPad 레거시)면 카드 안 헤더 유지.
    var titleOutside: Bool = false
    @Environment(\.glgAccent) private var accent
    var body: some View {
        if titleOutside {
            VStack(alignment: .leading, spacing: 10) {
                HomeSectionHeader(title: "오늘 할 일", count: tasks.isEmpty ? nil : tasks.count)
                GLGCard(cornerRadius: 24, padding: 16) { content }
            }
        } else {
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
                    content
                }
            }
        }
    }
    @ViewBuilder private var content: some View {
        if tasks.isEmpty {
            Text("오늘 챙길 건 다 끝냈어요 🎉 여유롭게 즐기세요").font(.pretendard(size: 14))
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(tasks.enumerated()), id: \.element.id) { i, t in
                    if i > 0 { Divider().padding(.vertical, 10) }
                    row(t)
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
    var titleOutside: Bool = false
    @Environment(\.glgAccent) private var accent
    var body: some View {
        if titleOutside {
            VStack(alignment: .leading, spacing: 10) {
                HomeSectionHeader(title: "오늘 할 일")
                GLGCard(cornerRadius: 24, padding: 16) { rows }
            }
        } else {
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 6) {
                        Image(systemName: "checklist").font(.pretendard(size: 15)).foregroundStyle(accent.primary.opacity(0.5))
                        Text("오늘 할 일").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary.opacity(0.5))
                    }
                    rows
                }
            }
        }
    }
    private var rows: some View {
        VStack(alignment: .leading, spacing: 12) {
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
    var store: SpendingStore
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

/// 이번 주 게임 일정 — 이벤트·정기콘텐츠 마감 임박(픽업과 별개).
struct DashboardScheduleCard: View {
    let events: [GameEvent]; let challenges: [GameChallenge]; let onTap: () -> Void
    var titleOutside: Bool = false
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let now = nowMs()
        let raw: [(String, String, Int64, String)] =
            events.map { ($0.game, $0.name, $0.endMillis, $0.dDayLabel(nowMillis: now)) }
            + challenges.map { ($0.game, $0.name, $0.endMillis, $0.dDayLabel(nowMillis: now)) }
        let items = Array(raw.filter { $0.2 > now }.sorted { $0.2 < $1.2 }.prefix(3))
        return Group {
            if !items.isEmpty {
                if titleOutside {
                    VStack(alignment: .leading, spacing: 10) {
                        HomeSectionHeader(title: "이번 주 일정", actionTitle: "전체", action: onTap)
                        GLGCard(cornerRadius: 22, padding: 16) { rows(items) }
                            .contentShape(Rectangle()).onTapGesture { onTap() }
                    }
                } else {
                    GLGCard(cornerRadius: 22, padding: 16) {
                        VStack(alignment: .leading, spacing: 0) {
                            HStack {
                                Text("이번 주 일정").font(.pretendard(size: 14, weight: .bold))
                                Spacer()
                                Text("전체 ›").font(.pretendard(size: 11.5, weight: .semibold)).foregroundStyle(accent.primary)
                            }
                            rows(items).padding(.top, 11)
                        }
                    }
                    .contentShape(Rectangle()).onTapGesture { onTap() }
                }
            }
        }
    }
    @ViewBuilder private func rows(_ items: [(String, String, Int64, String)]) -> some View {
        VStack(alignment: .leading, spacing: 11) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, it in
                HStack(spacing: 9) {
                    GLGGameTag(game: it.0, size: .small)
                    Text(it.1).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    Spacer(minLength: 6)
                    Text(it.3).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(accent.primary)
                }
            }
        }
    }
}

/// 게임 소식 — 다가오는 주년 + 최신 공지.
struct DashboardNewsCard: View {
    let news: [NewsItem]; let anniversaries: [AnniversaryInfo]; let onTap: () -> Void
    var titleOutside: Bool = false
    @Environment(\.glgAccent) private var accent
    var body: some View {
        let anni = anniversaries.first { $0.daysUntil <= 60 }
        let topNews = Array(news.sorted { $0.createdAtMillis > $1.createdAtMillis }.prefix(2))
        return Group {
            if anni != nil || !topNews.isEmpty {
                if titleOutside {
                    VStack(alignment: .leading, spacing: 10) {
                        HomeSectionHeader(title: "게임 소식", actionTitle: "전체", action: onTap)
                        GLGCard(cornerRadius: 22, padding: 16) { newsBody(anni: anni, topNews: topNews) }
                            .contentShape(Rectangle()).onTapGesture { onTap() }
                    }
                } else {
                    GLGCard(cornerRadius: 22, padding: 16) {
                        VStack(alignment: .leading, spacing: 0) {
                            HStack {
                                Text("게임 소식").font(.pretendard(size: 14, weight: .bold))
                                Spacer()
                                Text("전체 ›").font(.pretendard(size: 11.5, weight: .semibold)).foregroundStyle(accent.primary)
                            }
                            newsBody(anni: anni, topNews: topNews).padding(.top, 12)
                        }
                    }
                    .contentShape(Rectangle()).onTapGesture { onTap() }
                }
            }
        }
    }
    @ViewBuilder private func newsBody(anni: AnniversaryInfo?, topNews: [NewsItem]) -> some View {
        let amber = Color(hex: 0xFFF59E0B)
        VStack(alignment: .leading, spacing: 11) {
            if let a = anni {
                HStack(spacing: 8) {
                    Image(systemName: "party.popper.fill").font(.system(size: 13)).foregroundStyle(amber)
                    Text("\(a.game.shortName) \(a.ordinal)주년").font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 6)
                    Text(a.daysUntil == 0 ? "오늘" : "D-\(a.daysUntil)").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(amber)
                }
                .padding(11).frame(maxWidth: .infinity)
                .background(amber.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            ForEach(Array(topNews.enumerated()), id: \.offset) { _, n in
                HStack(spacing: 9) {
                    GLGGameTag(game: n.game, size: .small)
                    Text(n.title).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                }
            }
        }
    }
}
