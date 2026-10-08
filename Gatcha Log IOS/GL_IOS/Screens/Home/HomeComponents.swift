import SwiftUI
import Shared

// 홈 서브컴포넌트 — (Compose HomeRedesign/HomeScreen 대응)

private let warnText = Color(hex: 0xFFB37400)

// ── 오늘 할 일 ──
struct TodayTaskCard: View {
    let tasks: [TodayItem]; let inProgress: Bool
    @Environment(\.glgAccent) private var accent
    var body: some View {
        // 카드 없이 섹션 머리 + 헤어라인 목록(10/1, Android 와 같다).
        VStack(alignment: .leading, spacing: 2) {
            HomeSectionHeader(title: "오늘 할 일", count: tasks.isEmpty ? nil : tasks.count)
            content
        }
    }
    @ViewBuilder private var content: some View {
        if tasks.isEmpty {
            Text("오늘 챙길 건 다 끝냈어요 🎉 여유롭게 즐기세요").font(.pretendard(size: 15))
                .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 12)
        } else {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(tasks.enumerated()), id: \.element.id) { i, t in
                    if i > 0 { homeHair.frame(height: 1) }
                    row(t)
                }
            }
        }
    }
    private func row(_ t: TodayItem) -> some View {
        let tint = t.urgent ? warnText : accent.primary
        let busy = t.busyable && inProgress
        return Button(action: t.action) {
            // 태그 자리 — 할 일은 게임이 없는 줄(전체 출석 · 예산)이 있어 종류 아이콘을 둔다.
            HStack(spacing: 12) {
                Image(systemName: t.icon).font(.pretendard(size: 18)).foregroundStyle(tint)
                Text(t.message).font(.pretendard(size: 15)).foregroundStyle(GLGColor.textPrimary).frame(maxWidth: .infinity, alignment: .leading).lineLimit(2)
                if busy { GldsSpinner(size: 13, lineWidth: 2, color: tint) }
                else {
                    Text(t.cta).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(tint)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }.buttonStyle(.plain).disabled(busy)
    }
}

struct TodayTaskSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HomeSectionHeader(title: "오늘 할 일")
            rows
        }
    }
    private var rows: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(0..<3, id: \.self) { i in
                if i > 0 { homeHair.frame(height: 1) }
                HStack(spacing: 12) {
                    Circle().fill(Color.black.opacity(0.06)).frame(width: 18, height: 18)
                    RoundedRectangle(cornerRadius: 4).fill(Color.black.opacity(0.06)).frame(height: 15).frame(maxWidth: .infinity)
                    RoundedRectangle(cornerRadius: 14).fill(Color.black.opacity(0.06)).frame(width: 64, height: 28)
                }
                .padding(.vertical, 12)
            }
        }
    }
}

/// 대시보드 리스트 카드 로딩 스켈레톤 — 헤더 + 행 N개. '이번 주 일정'·'게임 소식' 카드와 동일 형태. (Android DashCardSkeleton 패리티)
struct DashCardSkeleton: View {
    var rows: Int = 3
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            GLGSkeleton().frame(width: 90, height: 17)
            ForEach(0..<rows, id: \.self) { _ in
                HStack(spacing: 9) {
                    GLGSkeleton(cornerRadius: 9).frame(width: 28, height: 28)
                    GLGSkeleton().frame(maxWidth: .infinity).frame(height: 13)
                    GLGSkeleton().frame(width: 34, height: 12)
                }.padding(.top, 13)
            }
        }
        // 아래 12 — 목록 섹션 아래 8 과 합쳐 보이는 20(홈 3.0).
        .padding(.bottom, 12)
    }
}

/// 홈 최상단 만료 배너 — 자동 출석에서 HoYoLAB 쿠키 만료가 감지되면 노출. (Android `TokenExpiredBanner`)
struct TokenExpiredBanner: View {
    let onReconnect: () -> Void
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HomeTopBanner(icon: "exclamationmark.triangle.fill", tint: accent.primary,
                      title: "HoYoLAB 토큰이 만료된 것 같아요", message: "재연동하지 않으면 자동 출석이 안 돼요",
                      cta: "재연동", action: onReconnect)
    }
}

/// 운영 공지 한 건 — 만료 배너와 **같은 모양**이고 색만 무게를 따른다(안내 = 강조색 · 주의 = 경고색 · 긴급 = 급한 경고색).
/// 주소가 있으면 버튼이 붙어 브라우저로 연다. Android `AppNoticeBanner` 와 같은 값.
struct AppNoticeBanner: View {
    let notice: AppNotice
    @Environment(\.glgAccent) private var accent
    @Environment(\.openURL) private var openURL
    private var tint: Color {
        switch notice.level {
        case .warn: return GLGColor.warningText
        case .urgent: return GLGColor.urgent
        default: return accent.primary
        }
    }
    var body: some View {
        HomeTopBanner(icon: notice.level == .info ? "info.circle.fill" : "exclamationmark.triangle.fill", tint: tint,
                      title: notice.title, message: notice.body,
                      cta: notice.url.isEmpty ? nil : notice.cta,
                      action: { if let url = URL(string: notice.url) { openURL(url) } })
    }
}

/// 홈 맨 위 띠 배너 — 카드 없이 화면 폭 `tint` 10% 면(Android `HomeTopBanner` 와 같다).
/// 아이콘 22 · 제목 14 Bold · 내용 12 회색 · 버튼은 GLDS S.
struct HomeTopBanner: View {
    let icon: String
    let tint: Color
    let title: String
    let message: String
    let cta: String?
    let action: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.pretendard(size: 22)).foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(.pretendard(size: 14, weight: .bold))
                if !message.isEmpty {
                    Text(message).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
            }
            Spacer()
            if let cta {
                GldsButton(title: cta, size: .s, fullWidth: false, action: action)
            }
        }
        .padding(.horizontal, 20).padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(tint.opacity(0.10))
    }
}

/**
 지금 띄울 운영 공지를 받아 온다 — 진입할 때 + **앱으로 돌아올 때마다** 다시 묻는다.

 호요랜드 배너(`HoyolandAutoLoad`)와 같은 이유다. 홈은 앱을 켜 두는 내내 살아 있어 한 번만 물으면
 어드민에서 공지를 올려도 재실행 전까지 안 뜬다. 매번 네트워크를 타지는 않는다 — `AppNoticeApi.load` 가
 15초 캐시로 막는다. Android 는 ON_RESUME 마다 다시 묻는다(`rememberAppNotices`).
 */
struct AppNoticeAutoLoad: ViewModifier {
    @Binding var notices: [AppNotice]
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .task { await reload() }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { Task { await reload() } }
            }
    }

    private func reload() async {
        if let fresh = try? await AppNoticeApi.shared.load(force: false) { notices = fresh }
    }
}

// ── 알림 상세 (push) ──
struct NotificationDetailView: View {
    var store: SpendingStore
    let alerts: [HomeAlert]; let onGameInfo: () -> Void
    let onDismiss: (HomeAlert) -> Void; let onDismissAll: () -> Void
    /// 예산 관리는 **알림의 하위 페이지**로 쌓는다(9/30) — 뒤로 가면 알림으로 돌아온다(시스템 push · pop 애니메이션).
    @State private var showBudget = false
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
                // GLDS 2.0(10/6) — 흰 바탕, 알림 하나 = 헤어라인으로 나눈 한 줄(카드 없음). Android NotificationDetailScreen 과 같다.
                // 줄이 위아래 12 를 스스로 가져 위 10 · 아래 8 → 눈에 22 · 20.
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(Array(alerts.enumerated()), id: \.element.id) { i, a in
                            if i > 0 { GiHairline().padding(.horizontal, 20) }
                            row(a)
                        }
                    }
                    .padding(.top, 10).padding(.bottom, 8)
                    // 넓은 창(iPad)에서는 가운데 640 폭으로 모은다 — 설정 · 마이페이지와 같은 규칙.
                    .glgReadableWidth(640)
                }
            }
        }
        .background(Color.white)
        .glgPageTitle("알림").navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $showBudget) { BudgetSettingsView(store: store) }
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
    private func row(_ a: HomeAlert) -> some View {
        let (icon, tint, hint): (String, Color, String) = {
            switch a.kind {
            case .budgetOver: return ("banknote", GLGColor.urgent, "예산 설정하기")
            case .budgetNear: return ("banknote", warnText, "예산 설정하기")
            case .budgetGameOver: return ("banknote", GLGColor.urgent, "예산 설정하기")
            case .banner: return ("bolt.fill", accent.primary, "게임 정보 보기")
            case .attendance: return ("checkmark.circle", accent.primary, "출석하러 가기")
            }
        }()
        // 카드 없이 한 줄 — 좌 20 · 위아래 12. 우측은 X 버튼(36) 안쪽 여백이 있어 12(Android 와 같다).
        return HStack(spacing: 0) {
            // 본문 탭 → 관련 화면 이동
            Button { switch a.kind { case .banner, .attendance: onGameInfo(); default: showBudget = true } } label: {
                HStack(spacing: 12) {
                    ZStack { Circle().fill(tint.opacity(0.12)).frame(width: 38, height: 38); Image(systemName: icon).font(.pretendard(size: 18)).foregroundStyle(tint) }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(a.message).font(.pretendard(size: 15, weight: .medium)).foregroundStyle(GLGColor.textPrimary)
                        Text(hint).font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(accent.primary)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }.buttonStyle(.plain)
            // 삭제(X) — 이 알림만 지움(다시 안 뜸). 본문 탭(이동)과 분리.
            Button { onDismiss(a) } label: {
                Image(systemName: "xmark").font(.pretendard(size: 14, weight: .semibold))
                    .foregroundStyle(Color(.systemGray3)).frame(width: 36, height: 36)
            }.buttonStyle(.plain)
        }
        .padding(.leading, 20).padding(.trailing, 12).padding(.vertical, 12)
    }
}

// HomeCardEditSheet 은 27.43.0 에서 제거했다 — 27.32.0 홈 대시보드 개편 때 양 플랫폼 렌더 루프가
// 사라져, 열 방법도 없고 열려도 홈이 설정을 안 읽는 상태였다. 상세는 GL_MD/Debt_27_43_0.md P0-1.

// ════════════════════════════════════════════════════════════════════════════
// 홈 대시보드 개편(27.32.0) — 깔끔한 KPI 중심 레이아웃
// ════════════════════════════════════════════════════════════════════════════

/// 이번 주 게임 일정 — 이벤트·정기콘텐츠 마감 임박(픽업과 별개).
struct DashboardScheduleCard: View {
    let events: [GameEvent]; let challenges: [GameChallenge]; let onTap: () -> Void
    var body: some View {
        let now = nowMs()
        let raw: [(String, String, Int64, String)] =
            events.map { ($0.game, $0.name, $0.endMillis, $0.dDayLabel(nowMillis: now)) }
            + challenges.map { ($0.game, $0.name, $0.endMillis, $0.dDayLabel(nowMillis: now)) }
        let items = Array(raw.filter { $0.2 > now }.sorted { $0.2 < $1.2 }.prefix(3))
        // 일정이 없어도 **카드는 남긴다.** 예전엔 통째로 숨겨서 "이번 주가 한가하다"와
        // "아직 못 불러왔다"가 화면에서 똑같아 보였다(스켈레톤도 같은 자리에 뜬다).
        // (Android `DashScheduleCard` 와 같이 고쳐야 한다)
        return VStack(alignment: .leading, spacing: 2) {
            HomeSectionHeader(title: "이번 주 일정", actionTitle: "전체 ›", action: onTap)
            rows(items, now: now)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle()).onTapGesture { onTap() }
        }
    }
    @ViewBuilder private func rows(_ items: [(String, String, Int64, String)], now: Int64) -> some View {
        if items.isEmpty {
            Text("이번 주 마감 일정이 없어요")
                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                .padding(.vertical, 12)
        } else {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.offset) { i, it in
                    if i > 0 { homeHair.frame(height: 1) }
                    // 마감 임박(D-0~3)만 강조 — 게임 정보 일정(ScheduleLogic.urgent)과 같은 기준 · 같은 색.
                    let dDay = Int((Double(it.2 - now) / 86_400_000).rounded(.up))
                    HStack(spacing: 12) {
                        HomeGameTag(game: it.0)
                        Text(it.1).font(.pretendard(size: 15)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                        Spacer(minLength: 0)
                        Text(it.3).font(.pretendard(size: 13, weight: .bold))
                            .foregroundStyle((0...3).contains(dDay) ? GLGColor.urgent : GLGColor.textPrimary)
                    }
                    .padding(.vertical, 12)
                }
            }
        }
    }
}

/// 게임 소식 — 다가오는 주년 + 최신 공지.
struct DashboardNewsCard: View {
    let news: [NewsItem]; let anniversaries: [AnniversaryInfo]; let onTap: () -> Void
    var body: some View {
        let anni = anniversaries.first { $0.daysUntil <= 60 }
        // 홈은 2건뿐이라 최신순으로 자르면 한 게임이 둘 다 먹기 쉽다 — 게임을 번갈아 뽑는다(공용 로직).
        let topNews = NewsLogic.shared.previewTop(news: news, max: 2)
        return Group {
            if anni != nil || !topNews.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    HomeSectionHeader(title: "게임 소식", actionTitle: "전체 ›", action: onTap)
                    newsBody(anni: anni, topNews: topNews)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle()).onTapGesture { onTap() }
                }
            }
        }
    }
    @ViewBuilder private func newsBody(anni: AnniversaryInfo?, topNews: [NewsItem]) -> some View {
        let amber = Color(hex: 0xFFF59E0B)
        VStack(alignment: .leading, spacing: 0) {
            if let a = anni {
                HStack(spacing: 8) {
                    Image(systemName: "party.popper.fill").font(.system(size: 13)).foregroundStyle(amber)
                    Text("\(a.game.shortName) \(a.ordinal)주년").font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 6)
                    Text(a.daysUntil == 0 ? "오늘" : "D-\(a.daysUntil)").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(amber)
                }
                .padding(11).frame(maxWidth: .infinity)
                .background(amber.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .padding(.vertical, 12)
            }
            ForEach(Array(topNews.enumerated()), id: \.offset) { i, n in
                if i > 0 || anni != nil { homeHair.frame(height: 1) }
                HStack(spacing: 12) {
                    HomeGameTag(game: n.game)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(n.title).font(.pretendard(size: 15)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                        // 「10.01」 — 게시일(공용 dayKey "2026-10-01" 의 월 · 일).
                        if n.createdAtMillis > 0 {
                            Text(String(DateUtil.shared.dayKey(millis: n.createdAtMillis).suffix(5)).replacingOccurrences(of: "-", with: "."))
                                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .padding(.vertical, 12)
            }
        }
    }
}
