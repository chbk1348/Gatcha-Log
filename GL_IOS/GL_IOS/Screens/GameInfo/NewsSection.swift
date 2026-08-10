import SwiftUI
import Shared

/// 헤더 드롭다운 규칙: "all"=전체, 그 외는 게임 키 → 해당 게임 displayName 매칭(일정 섹션과 동일).
private func filterNews(_ news: [NewsItem], _ filter: String) -> [NewsItem] {
    if filter == "all" { return news }
    if let g = GameData.shared.byNameOrNull(name: filter) {
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
///
/// `@MainActor` — 파일 스코프 함수라 표시가 없으면 비격리다. 그러면 Kotlin 타입([NewsItem], non-Sendable)과
/// 콜백을 메인 액터인 Button 액션으로 넘기는 게 데이터 레이스로 잡힌다(Swift 6). 뷰를 만드는 함수이니
/// 메인 액터가 맞다.
@MainActor
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
    var store: SpendingStore
    let filter: String
    var onSeeAll: () -> Void = {}
    var onOpenNews: (NewsItem) -> Void = { _ in }
    var maxCount: Int = 5
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let all = filterNews(store.gameNews, filter)
        if !all.isEmpty {
            // 그냥 prefix 하면 공지를 많이 올리는 게임(엔드필드)이 목록을 다 먹는다 —
            // 게임을 번갈아 뽑는 로직은 commonMain 단일 소스(Android 와 같은 함수).
            let items = NewsLogic.shared.previewTop(news: all, max: Int32(maxCount))
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
    var store: SpendingStore
    let filter: String
    // 상세 push 는 navigationDestination 으로 — NewsSection 과 동일한 이유(destination형 NavigationLink 회피).
    @State private var selectedNews: NewsItem? = nil
    @State private var showDetail = false
    /// 페이지 안 게임 칩 — 헤더 드롭다운과 별개로 이 페이지에서만 좁혀 본다(Compose 쪽과 동일).
    @State private var chip = "all"

    var body: some View {
        let headerFiltered = filterNews(store.gameNews, filter)
        // 헤더가 이미 한 게임으로 좁힌 상태면 칩이 무의미하므로 그때는 감춘다.
        let showChips = filter == "all"
        let all = showChips ? filterNews(headerFiltered, chip) : headerFiltered
        // 칩은 실제로 공지가 있는 게임만 — 소식이 없는 게임 칩을 눌러 빈 화면을 보게 두지 않는다.
        let chipGames = GLGGames.all.filter { g in headerFiltered.contains { $0.game == g.displayName } }
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if showChips && chipGames.count > 1 {
                    gameSegments(chipGames).padding(.bottom, 14)
                }
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

    /**
     게임 필터 — 본문 상단의 시스템 세그먼티드([Picker] `.segmented`).

     툴바로 올리고 캡슐을 직접 그리는 등 여러 배치를 시도했지만, 게임일정 페이지와 같은
     **기본형(본문 최상단 세그먼티드)** 으로 통일했다. 화면 폭을 그대로 쓰므로 칸이 늘어도
     여유가 있고, 툴바 공유 글래스와 겹칠 일도 없다.

     게임 라벨은 [Game.abbr] 다. `shortName`(원신·스타레일·젠레스·명조·엔드필드)은 6칸에
     넣으면 너무 길고, 그보다 짧은 한국어 표기가 없다. 약칭은 **목록 행의 게임 배지와 같은
     글자**라 필터와 결과가 눈으로 이어진다. '전체'만 한국어로 둔다.
     Compose 쪽은 기존 GlgChip 을 유지한다 — 각 플랫폼 네이티브 UX 를 따른다.
     */
    @ViewBuilder
    private func gameSegments(_ games: [Game]) -> some View {
        Picker("게임 선택", selection: $chip.animation(.easeInOut(duration: 0.2))) {
            Text("전체").tag("all")
            ForEach(games, id: \.key) { g in
                Text(g.abbr).tag(g.key)
            }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
    }
}
