import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 홈 재구성(Figma Make 참고) — 그라데이션 히어로 + 원형 퀵액션 + 최근 내역 리스트
//
// 참고 목업은 은행 앱(잔액·계좌·송금)이라, 가챠 지출 트래커로 의미를 옮겼다:
//   중앙 큰 잔액       → 이번 달 지출 총액(캐러셀로 예산 현황 전환)
//   원형 퀵액션 4개     → 지출 추가 · 출석 · 예산 · 게임 정보
//   Transaction 리스트  → 최근 지출 내역 + 전체보기
// 색은 사용자 강조색(accent)에 맞춰 파생 — 목업의 파랑 대신 테마색 그라데이션.
// ════════════════════════════════════════════════════════════════════════════

private let dangerRed = Color(hex: 0xFFEF4444)

/// 그라데이션 히어로 — 페이지 1) 이번 달 지출, 페이지 2) 예산 현황. 하단 커스텀 도트.
struct HeroBalanceCard: View {
    let monthlyTotal: Int64
    let prevTotal: Int64
    let budget: Int64
    let onBudget: () -> Void
    /// 상단 안전영역(상태바+내비바) 높이 — 그라데이션은 이 위(헤더 뒤)까지 채우고, 안쪽 콘텐츠는 이만큼 내려
    /// 시스템 툴바(프로필·알림)와 겹치지 않게 한다.
    var topPad: CGFloat = 0
    @Environment(\.glgAccent) private var accent
    @State private var page = 0

    var body: some View {
        let month = Calendar.current.component(.month, from: Date())
        VStack(spacing: 14) {
            TabView(selection: $page) {
                spendPage(month: month).tag(0)
                budgetPage.tag(1)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 154)   // 콘텐츠(숫자+델타+버튼)가 잘리지 않도록 여유 확보

            // 캐러셀 도트 — 현재 페이지는 캡슐로 늘어난다(목업의 페이지 인디케이터).
            HStack(spacing: 6) {
                ForEach(0..<2, id: \.self) { i in
                    Capsule()
                        .fill(i == page ? accent.primary : accent.primary.opacity(0.22))
                        .frame(width: i == page ? 18 : 6, height: 6)
                        .animation(GLGMotion.standard(), value: page)
                }
            }
        }
        .padding(.top, topPad + 8)   // 콘텐츠를 상태바+내비바 아래로 내림(그라데이션은 그 위까지 채움)
        .padding(.bottom, 26)
        .frame(maxWidth: .infinity)
    }

    // 페이지 1 — 이번 달 지출 + 지난달 대비 + 예산 관리 버튼
    private func spendPage(month: Int) -> some View {
        let diff = monthlyTotal - prevTotal
        return VStack(spacing: 7) {
            Text("\(month)월 지출")
                .font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
            Text(won(monthlyTotal))
                .font(.pretendard(size: 38, weight: .heavy)).foregroundStyle(GLGColor.textPrimary)
                .lineLimit(1).minimumScaleFactor(0.5)
            if monthlyTotal > 0 || prevTotal > 0 {
                HStack(spacing: 3) {
                    Image(systemName: diff > 0 ? "arrow.up.right" : (diff < 0 ? "arrow.down.right" : "equal"))
                        .font(.system(size: 10, weight: .bold))
                    Text(diff == 0 ? "지난달과 동일" : "지난달 대비 \(diff > 0 ? "+" : "-")\(won(abs(diff)))")
                        .font(.pretendard(size: 12, weight: .semibold))
                }
                .foregroundStyle(diff > 0 ? dangerRed : (diff < 0 ? accent.primary : GLGColor.textSecondary))
            }
        }
        .frame(maxWidth: .infinity)
    }

    // 페이지 2 — 예산 잔여/초과 + 사용률 바
    private var budgetPage: some View {
        let pct = budget > 0 ? Int(monthlyTotal * 100 / budget) : 0
        let over = budget > 0 && monthlyTotal > budget
        let frac = budget > 0 ? min(Double(monthlyTotal) / Double(budget), 1) : 0
        return VStack(spacing: 7) {
            Text("이번 달 예산")
                .font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
            if budget > 0 {
                Text(over ? "\(won(monthlyTotal - budget)) 초과" : "\(won(budget - monthlyTotal)) 남음")
                    .font(.pretendard(size: 34, weight: .heavy)).foregroundStyle(over ? dangerRed : GLGColor.textPrimary)
                    .lineLimit(1).minimumScaleFactor(0.5)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.white.opacity(0.65))
                        Capsule().fill(over ? dangerRed : accent.primary).frame(width: geo.size.width * (over ? 1 : frac))
                    }
                }
                .frame(height: 8).padding(.horizontal, 44).padding(.top, 2)
                Text(over ? "예산 \(pct - 100)% 초과" : "예산의 \(pct)% 사용")
                    .font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(over ? dangerRed : accent.primary)
            } else {
                Text("미설정").font(.pretendard(size: 30, weight: .heavy)).foregroundStyle(GLGColor.textSecondary)
                GldsButton(title: "예산 설정하기", variant: .onTint, size: .s, fullWidth: false, action: onBudget).padding(.top, 3)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// 최근 지출 리스트 — 흰 카드 + 행 N개 + 하단 전체보기(목업 Transaction 리스트).
struct RecentSpendCard: View {
    let spendings: [Spending]
    let onSeeAll: () -> Void

    var body: some View {
        let recent = Array(spendings.prefix(4))   // store.spendings 는 이미 날짜 내림차순(VM loadAll)
        VStack(alignment: .leading, spacing: 6) {
            // 카드 없이 섹션 머리 + 헤어라인 목록(10/1, Android 와 같다).
            HomeSectionHeader(title: "최근 지출", actionTitle: recent.isEmpty ? nil : "전체보기", action: onSeeAll)
            Group {
                VStack(spacing: 0) {
                    if recent.isEmpty {
                        VStack(spacing: 6) {
                            Image(systemName: "doc.text").font(.system(size: 30)).foregroundStyle(Color(.systemGray3))
                            Text("아직 기록된 지출이 없어요").font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
                            Text("+ 지출 추가로 첫 기록을 남겨보세요").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 22)
                    } else {
                        ForEach(Array(recent.enumerated()), id: \.element.id) { i, s in
                            if i > 0 { Color(hex: 0xFFEEF0F2).frame(height: 1).padding(.leading, 48) }
                            RecentSpendRow(spending: s)
                        }
                    }
                }
            }
        }
    }
}

private struct RecentSpendRow: View {
    let spending: Spending
    private var gameColor: Color { Color(argb64: spending.gameColor) }
    private var abbr: String {
        GameData.shared.byNameOrNull(name: spending.gameName)?.abbr ?? String(spending.gameName.prefix(2))
    }
    private var subtitle: String {
        [spending.dateLabel, spending.itemName.isEmpty ? nil : spending.itemName]
            .compactMap { $0 }.joined(separator: " · ")
    }
    var body: some View {
        HStack(spacing: 12) {
            Text(abbr)
                .font(.pretendard(size: 12, weight: .heavy)).foregroundStyle(gameColor)
                .frame(width: 36, height: 36)
                .background(gameColor.opacity(0.14), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(spending.gameName).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                if !subtitle.isEmpty {
                    Text(subtitle).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            Text(won(spending.amount)).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
        }
        .padding(.vertical, 12)
    }
}

/// 섹션 헤더 — 카드 '바깥' 위에 놓는 큰 제목(+옵션 카운트/전체보기 액션). 홈 재구성 공통.
struct HomeSectionHeader: View {
    let title: String
    var count: Int? = nil
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil
    @Environment(\.glgAccent) private var accent
    var body: some View {
        HStack(spacing: 7) {
            Text(title).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            if let count {
                Text("\(count)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(accent.primary.opacity(0.14), in: Capsule())
            }
            Spacer(minLength: 8)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle).font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(accent.primary)
                }.buttonStyle(.plain)
            }
        }
    }
}

/// 홈 상단바(내비게이션 바) 처리.
///
/// - iPhone: 히어로 그라데이션이 상태바까지 이어지도록, 내비바 배경을 숨기고 스크롤을 바 뒤까지 확장한다.
/// - 넓은 화면: 홈이 NavigationSplitView 의 detail 컬럼 안이라 바 뒤 확장이 먹지 않아 흰 바가 남았다.
///         → 히어로 그라데이션 자체를 끄고(흰 히어로), 기본 내비바와 자연스럽게 어울리게 둔다(특별 처리 없음).
struct HomeTopBarStyle: ViewModifier {
    /// 넓은 화면인가 — **기기 종류가 아니라 size class 로 판단한 값**을 받는다(HomeView.isWide).
    let isWide: Bool
    func body(content: Content) -> some View {
        if isWide {
            content
        } else {
            content
                .ignoresSafeArea(.container, edges: .top)
                .toolbarBackground(.hidden, for: .navigationBar)
        }
    }
}
