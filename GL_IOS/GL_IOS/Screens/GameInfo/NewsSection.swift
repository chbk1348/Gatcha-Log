import SwiftUI
import Shared

/// 헤더 드롭다운 규칙: "all"=전체, 그 외는 게임 키 → 해당 게임 displayName 매칭(일정 섹션과 동일).
private func filterNews(_ news: [NewsItem], _ filter: String) -> [NewsItem] {
    if filter == "all" { return news }
    if let g = GameData.shared.games.first(where: { $0.key == filter }) {
        return news.filter { $0.game == g.displayName }
    }
    return news
}

/// 공지 행 — 탭하면 외부 브라우저가 아니라 앱 안의 상세 페이지로 push 한다.
@ViewBuilder
private func newsRow(_ n: NewsItem, _ store: SpendingStore) -> some View {
    NavigationLink {
        NewsDetailView(store: store, item: n)
    } label: {
        HStack(spacing: 10) {
            GLGGameTag(game: n.game, size: .small)
            VStack(alignment: .leading, spacing: 1) {
                Text(n.title).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(2).multilineTextAlignment(.leading)
                Text(DateUtil.shared.shortDate(millis: n.createdAtMillis)).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading) // 가로 폭 제약 — 긴 제목이 행을 넘쳐 좌우 스크롤되던 문제 방지
            // 앱 안에서 열리므로 외부링크 아이콘이 아니라 상세로 들어가는 셰브론.
            Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                .foregroundStyle(Color(.tertiaryLabel))
        }
        .padding(.vertical, 11)
    }
    .buttonStyle(.plain)
}

/// 공지·뉴스 섹션 — 게임별 최신 공지(상위 maxCount), 더 있으면 '더보기'로 전체 페이지.
struct NewsSection: View {
    @ObservedObject var store: SpendingStore
    let filter: String
    var onSeeAll: () -> Void = {}
    var maxCount: Int = 5
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let all = filterNews(store.gameNews, filter)
        if !all.isEmpty {
            let items = Array(all.prefix(maxCount))
            VStack(alignment: .leading, spacing: 10) {
                Text("공지·뉴스").font(.pretendard(size: 16, weight: .bold))
                GLGCard(cornerRadius: 24, padding: 0) {
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.offset) { i, n in
                            if i > 0 { Divider() }
                            newsRow(n, store)
                        }
                        if all.count > maxCount {
                            Divider()
                            Button { onSeeAll() } label: {
                                Text("더보기 (\(all.count))").font(.pretendard(size: 13, weight: .bold))
                                    .foregroundStyle(accent.primary).frame(maxWidth: .infinity).padding(.vertical, 12)
                            }.buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 16)
                    // 더보기일 때 하단 여백 최소화(버튼 자체 패딩만), 없으면 기본 16.
                    .padding(.bottom, all.count > maxCount ? 0 : 16)
                }
            }
        }
    }
}

/// 공지·뉴스 전체 페이지.
struct NewsPage: View {
    @ObservedObject var store: SpendingStore
    let filter: String

    var body: some View {
        let all = filterNews(store.gameNews, filter)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if all.isEmpty {
                    Text("공지가 없어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 24)
                } else {
                    GLGCard(cornerRadius: 24, padding: 16) {
                        VStack(spacing: 0) {
                            ForEach(Array(all.enumerated()), id: \.offset) { i, n in
                                if i > 0 { Divider() }
                                newsRow(n, store)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("공지·뉴스")
        .navigationBarTitleDisplayMode(.inline)
    }
}
