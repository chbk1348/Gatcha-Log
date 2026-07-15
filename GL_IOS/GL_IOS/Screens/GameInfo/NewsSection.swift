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
///
/// ⚠️ NavigationLink { destination } 형식을 쓰면 안 된다 — 상위 GameInfoView 가 하위 이동 전부를
/// `.navigationDestination(isPresented:)` 로 처리하는데, 같은 NavigationStack 에서 두 방식을 섞으면
/// destination형 NavigationLink 가 조용히 안 먹히는 SwiftUI 버그가 있다(게임정보 탭 뉴스 행이 안 눌리던 원인).
/// 그래서 여기선 Button 으로 선택만 올려보내고, 실제 push 는 호스트가 navigationDestination 으로 한다.
@ViewBuilder
private func newsRow(_ n: NewsItem, onOpen: @escaping (NewsItem) -> Void) -> some View {
    Button { onOpen(n) } label: {
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
        .contentShape(Rectangle()) // 태그·날짜 사이 빈 여백까지 탭 되게(행 전체가 탭 타깃)
    }
    .buttonStyle(.plain)
}

/// 공지·뉴스 섹션 — 게임별 최신 공지(상위 maxCount), 더 있으면 '더보기'로 전체 페이지.
struct NewsSection: View {
    @ObservedObject var store: SpendingStore
    let filter: String
    var onSeeAll: () -> Void = {}
    var onOpenNews: (NewsItem) -> Void = { _ in }
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
                            newsRow(n, onOpen: onOpenNews)
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
    // 상세 push 는 navigationDestination 으로 — NewsSection 과 동일한 이유(destination형 NavigationLink 회피).
    @State private var selectedNews: NewsItem? = nil
    @State private var showDetail = false

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
                                newsRow(n, onOpen: { selectedNews = $0; showDetail = true })
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
        .navigationDestination(isPresented: $showDetail) {
            if let n = selectedNews { NewsDetailView(store: store, item: n) }
        }
    }
}
