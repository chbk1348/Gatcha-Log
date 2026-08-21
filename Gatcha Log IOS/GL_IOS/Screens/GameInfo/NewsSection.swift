import SwiftUI
import Shared

/// 게임 칩 규칙: "all"=전체, 그 외는 게임 키 → 해당 게임 displayName 매칭.
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
private func newsRow(_ n: NewsItem, selected: Bool = false, onOpen: @escaping (NewsItem) -> Void) -> some View {
    Button { onOpen(n) } label: {
        HStack(spacing: 10) {
            GLGGameTag(game: n.game, size: .small)
            VStack(alignment: .leading, spacing: 1) {
                Text(n.title).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(2).multilineTextAlignment(.leading)
                Text(DateUtil.shared.shortDate(millis: n.createdAtMillis)).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading) // 가로 폭 제약 — 긴 제목이 행을 넘쳐 좌우 스크롤되던 문제 방지
            // 썸네일 — 목록에서 **글을 고르는 단서**로 쓴다. 상류가 `tabBanner`/`banner` 로 이미
            // 내려주는데(NewsItem.bannerUrl) 지금까지는 상세 페이지의 **본문 로드 실패 폴백**에서만
            // 그려서, 받아 놓고 안 쓰는 값이었다.
            //
            // 없는 항목이 섞여 온다(배너를 안 붙인 공지) — 그때는 자리도 비운다. 빈 회색 상자를
            // 대신 세우면 목록에 구멍이 뚫린 것처럼 보인다.
            //
            // `AsyncImage` 를 쓰지 않는다 — **디코딩 결과를 들고 있지 않아서** 셀이 스크롤로 화면 밖에
            // 나갔다 들어올 때마다 처음부터 다시 그린다(공지 상세는 이미 옮겨 놓고 목록만 남아 있었다).
            // `GLGRemoteImage` 는 디코딩본을 캐시하고, 52pt 자리에 맞춰 **축소본을 받아** 온다.
            if !n.bannerUrl.isEmpty {
                GLGRemoteImage(url: URL(string: n.bannerUrl), side: 52)
                    .frame(width: 52, height: 36)
                    .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
            }
            // 앱 안에서 열리므로 외부링크 아이콘이 아니라 상세로 들어가는 셰브론.
            Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                .foregroundStyle(Color(.tertiaryLabel))
        }
        .padding(.vertical, 11)
        .contentShape(Rectangle()) // 태그·날짜 사이 빈 여백까지 탭 되게(행 전체가 탭 타깃)
        // iPad 분할에서 "지금 오른쪽에 뜬 공지"를 표시. iPhone(push)에서는 항상 false 라 변화 없음.
        //
        // 배경만 좌우로 16 넓힌다 — 행은 카드(padding 16) 안에 있어서 그냥 깔면 양옆에 흰 띠가
        // 남아 **행 전체가 아니라 가운데만 칠해진 것처럼** 보인다.
        .background(
            (selected ? GLGColor.textPrimary.opacity(0.05) : Color.clear)
                .padding(.horizontal, -16)
        )
    }
    .buttonStyle(.plain)
}

/// 공지·뉴스 섹션 — 게임별 최신 공지(상위 maxCount), 더 있으면 '더보기'로 전체 페이지.
struct NewsSection: View {
    var store: SpendingStore
    var onSeeAll: () -> Void = {}
    var onOpenNews: (NewsItem) -> Void = { _ in }
    var maxCount: Int = 5
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let all = store.gameNews
        if !all.isEmpty {
            // 그냥 prefix 하면 공지를 많이 올리는 게임(엔드필드)이 목록을 다 먹는다 —
            // 게임을 번갈아 뽑는 로직은 commonMain 단일 소스(Android 와 같은 함수).
            let items = NewsLogic.shared.previewTop(news: all, max: Int32(maxCount))
            VStack(alignment: .leading, spacing: 10) {
                // 더보기는 **타이틀 줄 우측**. 카드 맨 아래에 두면 목록 다섯 줄을 다 지나야
                // 보이는데, "전체를 보겠다"는 판단은 목록을 읽기 **전에** 서는 쪽이 많다.
                // 제목 옆이면 섹션에 눈이 닿는 순간 같이 읽힌다.
                HStack(spacing: 0) {
                    Text("공지·뉴스").font(.pretendard(size: 16, weight: .bold))
                    Spacer(minLength: 8)
                    if all.count > maxCount {
                        Button { onSeeAll() } label: {
                            HStack(spacing: 1) {
                                Text("더보기 (\(all.count))").font(.pretendard(size: 13, weight: .bold))
                                Image(systemName: "chevron.right").font(.system(size: 11, weight: .bold))
                            }
                            .foregroundStyle(accent.primary)
                            // 글자만 두면 손가락이 닿는 자리가 너무 작다.
                            .padding(.leading, 10).padding(.trailing, 6)
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                        }.buttonStyle(.plain)
                    }
                }
                GLGCard(cornerRadius: 24, padding: 0) {
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.offset) { i, n in
                            if i > 0 { Divider() }
                            newsRow(n, onOpen: onOpenNews)
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 16)
                }
            }
        }
    }
}

/// 공지·뉴스 전체 페이지.
struct NewsPage: View {
    var store: SpendingStore
    // 상세 push 는 navigationDestination 으로 — NewsSection 과 동일한 이유(destination형 NavigationLink 회피).
    @State private var selectedNews: NewsItem? = nil
    @State private var showDetail = false
    /// 페이지 안 게임 칩 — 게임별로 좁혀 보는 건 여기서만 한다(Compose 쪽과 동일).
    @State private var chip = "all"
    /// 지금 좌/우로 갈려 있는가 — GLGSplitDetail 이 돌려주는 값(폭 기준, iPadOS 26 자유 창 대응).
    @State private var isWide = false

    /// iPad = 좌 목록 / 우 본문. iPhone = 기존 push.
    ///
    /// 공지는 본문이 이미지 섞인 긴 글이라 넓은 우측 패널의 이득이 크고, 여러 게임 공지를
    /// **훑는 것**이 이 페이지의 용도다 — 제목만 보고 들어갔다 나오기를 반복하지 않게 한다.
    var body: some View {
        GLGSplitDetail(isSplit: $isWide) { listContent } detail: { detailPane }
            // 새로고침으로 목록이 갈리면 우측이 사라진 공지를 붙들고 있을 수 있다 → 선택 해제.
            .onChange(of: store.gameNews) { _, new in
                if let n = selectedNews, !new.contains(where: { $0.id == n.id }) { selectedNews = nil }
            }
    }

    /// 우측 본문 — 고른 게 없으면 안내만.
    @ViewBuilder
    private var detailPane: some View {
        if let n = selectedNews {
            NavigationStack { NewsDetailView(store: store, item: n, embedded: true) }
                .id(n.id)   // 다른 공지를 고르면 본문을 새로 세운다(스크롤이 남지 않게)
        } else {
            GLGSplitPlaceholder(systemImage: "megaphone", text: "왼쪽에서 공지를 선택하세요")
        }
    }

    // 본문 앞에 let 바인딩이 있어 @ViewBuilder 가 필요하다(예전엔 body 라 암묵 적용됐다).
    @ViewBuilder
    private var listContent: some View {
        let all = filterNews(store.gameNews, chip)
        // 칩은 실제로 공지가 있는 게임만 — 소식이 없는 게임 칩을 눌러 빈 화면을 보게 두지 않는다.
        let chipGames = GLGGames.all.filter { g in store.gameNews.contains { $0.game == g.displayName } }
        ScrollView {
            // 필터는 본문이 아니라 **툴바 메뉴**에 있다(`filterMenu`). 본문 위 세그먼티드였다가
            // 고정줄로 옮겼는데, 고정하려면 뒤에 배경판이 필요하고 그 판이 목록 위에 얹힌
            // 표면처럼 읽혔다(2026-08-18). 헤더로 올리면 본문 자리를 아예 안 쓰면서도
            // 어디까지 내리든 늘 손 닿는 곳에 있다.
            // LazyVStack — 화면 밖 행은 스크롤할 때 만든다. 행에 썸네일이 붙은 뒤로는 이게
            // 성능이 아니라 **데이터** 문제이기도 하다(공지는 게임 수 × 30건까지 커진다).
            LazyVStack(alignment: .leading, spacing: 0) {
                if all.isEmpty {
                    Text("공지가 없어요").font(.pretendard(size: 13))
                        .foregroundStyle(GLGColor.textSecondary).padding(.vertical, 24)
                } else {
                    GLGCard(cornerRadius: 24, padding: 16) {
                        VStack(spacing: 0) {
                            ForEach(Array(all.enumerated()), id: \.offset) { i, n in
                                if i > 0 { Divider() }
                                // iPad 는 밀어 넣지 않고 우측 본문을 갈아 끼운다.
                                newsRow(n, selected: isWide && selectedNews?.id == n.id) {
                                    selectedNews = $0
                                    if !isWide { showDetail = true }
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        // 갈린 상태에선 타이틀을 비운다 — 오른쪽 본문이 자기 바("공지" + 공유)를 갖고 있어
        // 바가 두 줄로 겹쳐 보이고, 왼쪽은 어차피 목록인 게 한눈에 보인다. 뒤로가기는 남는다.
        .navigationTitle(isWide ? "" : "공지·뉴스")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { filterMenu }
        }
        .navigationDestination(isPresented: $showDetail) {
            if let n = selectedNews { NewsDetailView(store: store, item: n) }
        }
    }

    /**
     게임 필터 — **툴바 우측 메뉴**.

     본문 상단 세그먼티드 → 고정줄 → 여기로 왔다. 세그먼티드는 게임이 6개라 칸이 좁았고,
     고정줄은 배경판이 목록 위에 얹힌 표면처럼 읽혔다. 메뉴는 본문 자리를 안 쓰면서
     목록을 어디까지 내리든 늘 같은 곳에 있다.

     선택 중인 게임을 라벨로 드러낸다 — 아이콘만 두면 지금 걸린 필터를 열어 봐야 안다.

     ⚠️ 라벨에 `.glassProminent`(채움)를 쓰지 않는다. 메뉴가 닫힐 때 색 덩어리가 스치는
     잔상이 남고 애니메이션 차단으로도 막지 못한다(GL 기록). 선택 표시는 색으로만 한다.
     */
    @ViewBuilder
    private var filterMenu: some View {
        // 실제로 공지가 있는 게임만 — 소식이 없는 게임을 골라 빈 화면을 보게 두지 않는다.
        let chipGames = GLGGames.all.filter { g in store.gameNews.contains { $0.game == g.displayName } }
        if chipGames.count > 1 {
            let selected = chipGames.first { $0.key == chip }
            Menu {
                Picker("게임 선택", selection: $chip.animation(.easeInOut(duration: 0.2))) {
                    Text("전체").tag("all")
                    ForEach(chipGames, id: \.key) { g in
                        Text(g.shortName).tag(g.key)
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    if let selected {
                        Circle().fill(Color(argb64: selected.color)).frame(width: 8, height: 8)
                    }
                    Text(selected?.shortName ?? "전체")
                        .font(.pretendard(size: 13, weight: .semibold))
                    Image(systemName: "chevron.down").font(.system(size: 10, weight: .bold))
                }
                .foregroundStyle(selected != nil ? GLGColor.textPrimary : GLGColor.textSecondary)
            }
        }
    }
}
