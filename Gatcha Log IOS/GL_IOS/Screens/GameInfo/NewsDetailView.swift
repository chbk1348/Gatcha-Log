import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 공지 상세 — 목록에서 공지를 탭하면 외부 브라우저 대신 앱 안에서 본문을 보여준다.
//
// 본문은 HoYoLab 아티클 API(NewsApi.article)에서 받는다. 목록 API 도 본문 평문을 주긴 하지만
// 줄바꿈이 전부 날아가 있어 통짜 문단이 되고 이미지도 없다 — 그래서 본문은 따로 받고,
// 실패했을 때만 그 평문(summary)으로 폴백한다. 어느 쪽이든 '브라우저에서 보기'는 항상 제공한다.
//
// (Compose 패리티: GL_Android/ui/game/NewsDetailContent.kt)
// ════════════════════════════════════════════════════════════════════════════

struct NewsDetailView: View {
    var store: SpendingStore
    let item: NewsItem
    /// iPad 좌/우 분할의 **오른쪽**으로 들어온 경우. 상단 바 제목을 달지 않는다 —
    /// 왼쪽 목록 페이지의 바와 위아래로 겹쳐 제목이 두 줄로 보인다.
    /// (공유 버튼은 그대로 둔다. 그건 제목이 아니라 이 화면의 유일한 액션이다.)
    var embedded: Bool = false

    @Environment(\.openURL) private var openURL
    @Environment(\.glgAccent) private var accent

    /// 본문 이미지 탭 → 전체화면 뷰어(확대·저장). 공지 이미지는 대개 표·수치라 본문 폭에선 안 읽힌다.
    @State private var viewerUrl: String? = nil

    /// 본문 제목이 헤더 위로 스크롤돼 사라졌는지 — true 면 네비 바에 제목을 노출.
    @State private var showBarTitle = false

    /// 스크롤 위치 추적용 좌표공간 이름.
    private static let scrollSpace = "newsDetailScroll"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 머리말 — 게임 배지 · 게시일 · 제목
                HStack(spacing: 8) {
                    GLGGameTag(game: item.game, size: .small)
                    Text(DateUtil.shared.shortDate(millis: item.createdAtMillis))
                        .font(.pretendard(size: 11))
                        .foregroundStyle(GLGColor.textSecondary)
                }
                Spacer().frame(height: 10)
                Text(item.title)
                    .font(.pretendard(size: 20, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    // 본문 제목이 헤더 위로 사라지는 순간을 감지 → 그때 네비 바에 제목을 띄운다.
                    .background(GeometryReader { geo in
                        Color.clear.preference(
                            key: NewsTitleOffsetKey.self,
                            value: geo.frame(in: .named(Self.scrollSpace)).maxY
                        )
                    })
                Spacer().frame(height: 16)

                GLGCard(cornerRadius: 24, padding: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        if store.newsArticleLoading {
                            bodySkeleton
                        } else if let article = store.newsArticle {
                            // 본문 로드 성공 — 문단과 이미지를 원문 순서대로.
                            ForEach(Array(article.blocks.enumerated()), id: \.offset) { _, block in
                                if let text = block as? NewsBlockText {
                                    // 길게 눌러 원하는 구간만 드래그 선택 → 복사.
                                    GLGSelectableText(text: text.text)
                                } else if let image = block as? NewsBlockImage {
                                    bodyImage(image.url)
                                }
                            }
                        } else {
                            // 폴백 — 본문을 못 받았을 때. 배너 + 줄바꿈 없는 평문이라도 보여준다(빈 화면보다 낫다).
                            if !item.bannerUrl.isEmpty { bodyImage(item.bannerUrl) }
                            if !item.summary.isEmpty {
                                GLGSelectableText(text: item.summary)
                                Text("본문 전체는 브라우저에서 볼 수 있어요.")
                                    .font(.pretendard(size: 11))
                                    .foregroundStyle(GLGColor.textSecondary)
                            } else {
                                Text("본문을 불러오지 못했어요. 브라우저에서 확인해 주세요.")
                                    .font(.pretendard(size: 13))
                                    .foregroundStyle(GLGColor.textSecondary)
                            }
                        }

                        // 원문 링크·공유는 헤더 버튼으로 옮겼다 — 본문 끝까지 스크롤해야 보이던 걸 항상 닿는 자리로.
                    }
                }
            }
            .padding(16)
        }
        .coordinateSpace(name: Self.scrollSpace)
        .onPreferenceChange(NewsTitleOffsetKey.self) { maxY in
            // 제목 하단이 헤더(스크롤 영역 상단) 위로 올라가면 노출. 8pt 여유로 살짝 겹칠 때 자연스럽게 전환.
            let shouldShow = maxY < 8
            if shouldShow != showBarTitle {
                withAnimation(.easeInOut(duration: 0.2)) { showBarTitle = shouldShow }
            }
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !embedded {
                ToolbarItem(placement: .principal) {
                    // 상단에선 "공지", 스크롤로 본문 제목이 사라지면 그 제목을 헤더에 표시.
                    Text(showBarTitle ? item.title : "공지")
                        .font(.pretendard(size: 16, weight: .semibold))
                        .foregroundStyle(GLGColor.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
            }
            // 링크에 `lang=ko-kr` 을 붙인다 — 안 붙이면 받는 쪽에서 그 사람 기본 언어(대개 영문)로 열린다.
            if let u = URL(string: NewsLogic.shared.shareUrl(item: item)), !item.url.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    // ⚠️ **공유 대상은 URL 이어야 한다.** 제목까지 담으려고 String 을 넘겼더니
                    // `public.plain-text` 로 나가서 카카오톡이 "공유할 수 없는 형식"으로 거부했다.
                    // URL(`public.url`)은 어느 메신저든 받는다.
                    // 제목은 message 로 딸려 보낸다 — 받는 앱이 쓰면 본문에 들어가고, 무시해도
                    // 링크 공유 자체는 깨지지 않는다.
                    ShareLink(item: u, message: Text(item.title),
                              preview: SharePreview(item.title)) {
                        Image(systemName: "square.and.arrow.up")
                    }
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer(.fixed, placement: .topBarTrailing)
                }
                // 브라우저 — 표·동영상처럼 앱이 못 살리는 요소는 원문에서 봐야 한다.
                ToolbarItem(placement: .topBarTrailing) {
                    Button { openURL(u) } label: { Image(systemName: "safari") }
                }
            }
        }
        // 같은 글이면 loadNewsArticle 이 조기 반환하므로 탭 전환 후 복귀해도 재요청·스켈레톤 없음.
        // (일부러 clearNewsArticle 를 onDisappear 에 걸지 않는다 — 탭 전환 때 발동해 본문이 비워지던 원인)
        .task(id: item.id) { store.loadNewsArticle(item) }
        .fullScreenCover(isPresented: Binding(
            get: { viewerUrl != nil },
            set: { if !$0 { viewerUrl = nil } }
        )) {
            if let u = viewerUrl {
                GLGImageViewer(url: u, onDismiss: { viewerUrl = nil }, onSaved: { store.showStatus($0) })
            }
        }
    }

    /// 본문 이미지 — 폭에 맞춰 늘리고, 받아오는 동안엔 자리만 잡아 레이아웃이 튀지 않게.
    ///
    /// `AsyncImage` 를 쓰지 않는다 — **디코딩 결과를 들고 있지 않아서** 스크롤로 화면 밖에 나갔다
    /// 들어오면 스켈레톤부터 다시 시작한다. 공지 본문은 이미지가 여러 장 이어지는 구조라 그게 눈에 띈다.
    /// (전체화면 뷰어는 그대로 둔다 — 확대해서 보는 화면이라 축소 디코딩이 손해다)
    private func bodyImage(_ url: String) -> some View {
        // 본문은 화면 폭이 상한이다. 디코딩 목표를 폭 기준으로 잡는다(원본이 작으면 그대로 둔다).
        GLGRemoteImage(url: URL(string: url), side: UIScreen.main.bounds.width, contentMode: .fit) {
            GLGSkeleton().frame(height: 160)
        }
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture { viewerUrl = url }
    }

    /// 본문 로딩 — 문단 모양 스켈레톤(문단 끝줄만 짧게 해서 진짜 텍스트처럼 보이게).
    private var bodySkeleton: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(0..<3, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(0..<3, id: \.self) { line in
                        GLGSkeleton()
                            .frame(height: 13)
                            .frame(maxWidth: line == 2 ? 200 : .infinity, alignment: .leading)
                    }
                }
            }
        }
    }
}

/// 본문 제목의 스크롤 위치(좌표공간 기준 maxY)를 body 밖으로 전달 — 헤더 제목 노출 판정용.
private struct NewsTitleOffsetKey: PreferenceKey {
    static let defaultValue: CGFloat = .greatestFiniteMagnitude
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = min(value, nextValue())
    }
}
