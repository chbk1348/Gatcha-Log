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
    @ObservedObject var store: SpendingStore
    let item: NewsItem

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

                        // 원문 링크 — 본문을 잘 받았더라도 항상 남겨둔다(표·동영상 등 앱이 못 살리는 요소가 있다).
                        if !item.url.isEmpty {
                            Divider()
                            Button {
                                if let u = URL(string: item.url) { openURL(u) }
                            } label: {
                                HStack(spacing: 6) {
                                    Text("브라우저에서 보기").font(.pretendard(size: 13, weight: .bold))
                                    Image(systemName: "arrow.up.right.square").font(.pretendard(size: 13))
                                }
                                .foregroundStyle(accent.primary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 2)
                            }
                            .buttonStyle(.plain)
                        }
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
            ToolbarItem(placement: .principal) {
                // 상단에선 "공지", 스크롤로 본문 제목이 사라지면 그 제목을 헤더에 표시.
                Text(showBarTitle ? item.title : "공지")
                    .font(.pretendard(size: 16, weight: .semibold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.tail)
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
    private func bodyImage(_ url: String) -> some View {
        AsyncImage(url: URL(string: url)) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFit()
            case .failure:
                EmptyView()
            default:
                GLGSkeleton().frame(height: 160)
            }
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
    static var defaultValue: CGFloat = .greatestFiniteMagnitude
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = min(value, nextValue())
    }
}
