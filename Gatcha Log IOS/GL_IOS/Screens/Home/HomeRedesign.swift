import SwiftUI
import Shared

// 홈 3.0(10/1) — 이번 달 지출 · 최근 지출 · 섹션 머리 · 게임 태그. (Compose HomeRedesign 대응)

/// 이번 달 지출(홈 3.0 — 히어로 대신 일반 섹션). 금액 · 지난달 대비 · 예산 막대.
/// 예산이 없으면 옛 히어로처럼 「미설정」 + 예산 설정하기. (Android `MonthSpendSection` 과 같은 값)
struct MonthSpendSection: View {
    let monthlyTotal: Int64
    let prevTotal: Int64
    let budget: Int64
    let onBudget: () -> Void
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let diff = monthlyTotal - prevTotal
        VStack(alignment: .leading, spacing: 0) {
            HomeSectionHeader(title: "이번 달 지출", actionTitle: "예산 관리 ›", action: onBudget)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(won(monthlyTotal)).font(.pretendard(size: 26, weight: .black)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                if monthlyTotal > 0 || prevTotal > 0 {
                    Text(diff == 0 ? "지난달과 동일" : "지난달보다 \(won(abs(diff))) \(diff > 0 ? "↑" : "↓")")
                        .font(.pretendard(size: 13, weight: .bold)).lineLimit(1)
                        .foregroundStyle(diff > 0 ? GLGColor.urgent : (diff < 0 ? accent.primary : GLGColor.textSecondary))
                }
            }
            .padding(.top, 10)
            if budget > 0 {
                let over = monthlyTotal > budget
                let pct = Int(monthlyTotal * 100 / budget)
                let frac = min(Double(monthlyTotal) / Double(budget), 1)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 4).fill(Color(hex: 0xFFEDEFF3))
                        RoundedRectangle(cornerRadius: 4).fill(over ? GLGColor.urgent : accent.primary).frame(width: geo.size.width * frac)
                    }
                }
                .frame(height: 8).padding(.top, 12)
                HStack {
                    Text("예산 \(won(budget))의 \(pct)%").foregroundStyle(GLGColor.textSecondary)
                    Spacer(minLength: 8)
                    Text(over ? "\(won(monthlyTotal - budget)) 초과" : "\(won(budget - monthlyTotal)) 남음")
                        .foregroundStyle(over ? GLGColor.urgent : GLGColor.textSecondary)
                }
                .font(.pretendard(size: 13)).lineLimit(1)
                .padding(.top, 8)
            } else {
                HStack {
                    Text("미설정").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    Spacer(minLength: 8)
                    GldsButton(title: "예산 설정하기", variant: .secondary, size: .s, fullWidth: false, action: onBudget)
                }
                .padding(.top, 12)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 홈 목록의 게임 태그 — 11 Black · 폭 38 · 위아래 2 · 반경 6. 색은 게임색 · 바탕은 14%.
///
/// **폭은 고정이다**(10/7). 예전엔 글자에 좌우 7 을 붙여 약칭 길이만큼 자랐다 — 「GI」 와 「HSR」 의 태그 폭이 달라
/// 줄마다 제목이 시작하는 자리가 어긋났다. 38 은 세 글자 약칭(HSR · ZZZ · NTE)에 좌우 7 을 준 폭이다. Android `HomeGameTag` 와 같은 값.
struct HomeGameTag: View {
    let game: String
    var body: some View {
        let g = GameData.shared.byNameOrNull(name: game)
        let color = Color(argb64: g?.color ?? GameData.shared.colorFor(name: game))
        Text(g?.abbr ?? String(game.prefix(2)))
            .font(.pretendard(size: 11, weight: .black)).foregroundStyle(color).lineLimit(1)
            .padding(.vertical, 2)
            .frame(width: 38)
            .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}

/// 홈 목록 헤어라인 색 — 다른 화면 목록 구분선과 같은 값.
let homeHair = Color(hex: 0xFFEEF0F2)

/// 최근 지출 리스트 — 흰 카드 + 행 N개 + 하단 전체보기(목업 Transaction 리스트).
struct RecentSpendCard: View {
    let spendings: [Spending]
    let onSeeAll: () -> Void

    var body: some View {
        let recent = Array(spendings.prefix(3))   // store.spendings 는 이미 날짜 내림차순(VM loadAll)
        VStack(alignment: .leading, spacing: 2) {
            // 카드 없이 섹션 머리 + 헤어라인 목록(10/1, Android 와 같다).
            HomeSectionHeader(title: "최근 지출", actionTitle: recent.isEmpty ? nil : "전체 ›", action: onSeeAll)
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
                            if i > 0 { homeHair.frame(height: 1).padding(.leading, 52) }
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
    private var game: Game? { GameData.shared.byNameOrNull(name: spending.gameName) }
    private var abbr: String { game?.abbr ?? String(spending.gameName.prefix(2)) }
    // 홈 3.0 — 제목은 아이템, 보조는 「날짜 · 게임」. 아이템이 비면 게임 이름을 제목으로 올린다.
    private var title: String { spending.itemName.isEmpty ? spending.gameName : spending.itemName }
    private var subtitle: String {
        spending.itemName.isEmpty ? spending.dateLabel : "\(spending.dateLabel) · \(game?.shortName ?? spending.gameName)"
    }
    var body: some View {
        HStack(spacing: 12) {
            Text(abbr)
                .font(.pretendard(size: 12, weight: .black)).foregroundStyle(gameColor)
                .frame(width: 40, height: 40)
                .background(gameColor.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                if !subtitle.isEmpty {
                    Text(subtitle).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
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
/// - iPhone: 내비바 배경을 숨긴다 — 흰 바탕이 상태바까지 이어진다.
///   예전엔 히어로 그라데이션을 바 뒤로 깔려고 스크롤뷰를 안전 영역 밖(`ignoresSafeArea(.top)`)까지 늘렸는데,
///   그러면 당겨서 새로고침 스피너가 화면 맨 위(상태바 · 다이내믹 아일랜드 뒤)에 서서 보이지 않았다.
///   그라데이션은 10/1 에 걷었으므로 늘릴 이유가 없다 — 스크롤뷰는 시스템 기본대로 둔다(스피너가 바 아래에 선다).
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
                .toolbarBackground(.hidden, for: .navigationBar)
        }
    }
}
