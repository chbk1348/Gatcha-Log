import SwiftUI
import Shared

/// 게임 주년 — 지원 게임의 다가오는 주년(임박 순, 회차 + D-day). Compose AnniversaryContent 대응.
/// 게임 일정 상세 페이지의 '주년' 탭 본문(제목은 탭이 대신하므로 여기선 목록만).
struct AnniversaryContent: View {
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let list = GameAnniversary.shared.upcoming(nowMillis: nowMs())
        if list.isEmpty {
            Text("예정된 주년이 없어요.")
                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
        } else {
            // 카드 없이 화면 폭 섹션(10/1) — 좌우 20 · 아래 20, 줄 사이는 헤어라인. 위 여백은 탭 아래 22 가 준다.
            VStack(alignment: .leading, spacing: 12) {
                Text("다가오는 순서예요.").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                VStack(spacing: 0) {
                    ForEach(Array(list.enumerated()), id: \.offset) { i, a in
                        if i > 0 { Rectangle().fill(Color(hex: 0xFFEEF0F2)).frame(height: 1) }
                        HStack(spacing: 10) {
                            GLGGameTag(game: a.game.displayName, size: .small)
                            VStack(alignment: .leading, spacing: 1) {
                                // 이름은 shortName 으로 — 다른 섹션은 전부 shortName 인데 여기만 displayName 이었다.
                                Text(a.game.shortName).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                                // 회차만 있으면 어느 날짜 기준인지 알 수 없다 — 근거가 되는 출시일을 함께 둔다. (11 → 13, 10/1)
                                Text("\(a.ordinal)주년 · \(a.launchLabel) 출시")
                                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                                    .lineLimit(1)
                            }
                            Spacer(minLength: 8)
                            if a.daysUntil == 0 {
                                Text("오늘 🎉").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                            } else {
                                Text("D-\(a.daysUntil)").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(accent.primary)
                            }
                        }
                        .padding(.vertical, 14)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20).padding(.bottom, 20)
        }
    }
}
