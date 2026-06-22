import SwiftUI
import Shared

/// 공지·뉴스 — 게임별 최신 공지(제목·날짜), 탭하면 HoYoLab 아티클 열기. Compose NewsSection 대응.
struct NewsSection: View {
    @ObservedObject var store: SpendingStore
    let filter: String?
    var maxCount: Int = 5
    @Environment(\.openURL) private var openURL

    var body: some View {
        let items = Array((filter == nil ? store.gameNews : store.gameNews.filter { $0.game == filter }).prefix(maxCount))
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("공지·뉴스").font(.pretendard(size: 16, weight: .bold))
                GLGCard(cornerRadius: 24, padding: 16) {
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.offset) { i, n in
                            if i > 0 { Divider() }
                            Button {
                                if !n.url.isEmpty, let u = URL(string: n.url) { openURL(u) }
                            } label: {
                                HStack(spacing: 10) {
                                    Circle().fill(Color(argb64: GameData.shared.colorFor(name: n.game)))
                                        .frame(width: 8, height: 8)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(n.title).font(.pretendard(size: 13, weight: .medium))
                                            .foregroundStyle(GLGColor.textPrimary).lineLimit(2).multilineTextAlignment(.leading)
                                        Text(DateUtil.shared.shortDate(millis: n.createdAtMillis))
                                            .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                                    }
                                    Spacer(minLength: 8)
                                    Image(systemName: "arrow.up.right.square").font(.pretendard(size: 14))
                                        .foregroundStyle(GLGColor.textSecondary)
                                }
                                .padding(.vertical, 11)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
}
