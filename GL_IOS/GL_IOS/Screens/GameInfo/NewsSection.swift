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
                    gameSegments(chipGames)
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
     게임 필터 — **배치는 커스텀, 재질은 시스템 글래스**(`ALL / GI / HSR / ZZZ`).

     `Picker(.segmented)` 가 아니다. 세그먼티드는 항목 폭을 **균등 분할**하고 높이도 시스템이
     고정해서, 칸이 늘면 글자가 뭉개지고 통이 납작하다. 여기선 폭·높이를 우리가 잡고,
     트랙과 선택 알약에 [glgSystemGlass] 로 시스템 재질만 입힌다 — 유리를 흉내 내지 않으므로
     다크모드·대비 설정이 OS 를 따라온다.

     라벨은 [Game.abbr] — 목록 행의 게임 배지(EF·WW·HSR)와 같은 표기라 눈으로 이어진다.
     Compose 쪽은 기존 GlgChip 을 유지한다 — 각 플랫폼 네이티브 UX 를 따른다.
     */
    @ViewBuilder
    private func gameSegments(_ games: [Game]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                segment(label: "ALL", key: "all", tint: nil)
                ForEach(games, id: \.key) { g in
                    segment(label: g.abbr, key: g.key, tint: Color(argb64: g.color))
                }
            }
            .padding(5)
            .glgSystemGlass(in: Capsule(style: .continuous))
        }
        // 카드 목록과 붙어 답답해 보여서 위아래로 숨통을 틔운다(위는 네비바 아래 여백).
        .padding(.top, 8)
        .padding(.bottom, 20)
    }

    /// 세그먼트 한 칸. 선택된 칸만 알약 배경 + 게임색 글자.
    @ViewBuilder
    private func segment(label: String, key: String, tint: Color?) -> some View {
        let on = chip == key
        Button {
            // 같은 칸을 다시 누르면 전체로 — 'ALL' 까지 스크롤해 되돌아가지 않아도 되게.
            withAnimation(.snappy(duration: 0.2)) { chip = (on && key != "all") ? "all" : key }
        } label: {
            Text(label)
                .font(.pretendard(size: 14, weight: .semibold))
                .foregroundStyle(on ? (tint ?? GLGColor.textPrimary) : GLGColor.textSecondary)
                .padding(.horizontal, 18)
                // 참고 이미지처럼 통을 도톰하게 — 시스템 세그먼티드보다 세로로 넉넉하다.
                .padding(.vertical, 12)
                // 선택 알약은 **불투명**하게 둔다 — 시스템 세그먼티드도 트랙만 반투명하고
                // 선택 칸은 채워서 띄운다. 글래스 위에 글래스를 겹치면 둘 다 흐려진다.
                .background {
                    if on {
                        Capsule(style: .continuous).fill(Color(.secondarySystemGroupedBackground))
                            .shadow(color: .black.opacity(0.10), radius: 2, y: 1)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}
