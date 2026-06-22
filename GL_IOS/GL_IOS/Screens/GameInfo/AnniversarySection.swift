import SwiftUI
import Shared

/// 게임 주년 — 지원 게임의 다가오는 주년(임박 순, 회차 + D-day). Compose AnniversarySection 대응.
struct AnniversarySection: View {
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let list = GameAnniversary.shared.upcoming(nowMillis: nowMs())
        if !list.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("게임 주년").font(.pretendard(size: 16, weight: .bold))
                GLGCard(cornerRadius: 24, padding: 16) {
                    VStack(spacing: 0) {
                        ForEach(Array(list.enumerated()), id: \.offset) { i, a in
                            if i > 0 { Divider() }
                            HStack(spacing: 10) {
                                Circle().fill(Color(argb64: a.game.color)).frame(width: 10, height: 10)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(a.game.displayName).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                                    Text("\(a.ordinal)주년").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                                }
                                Spacer(minLength: 8)
                                if a.daysUntil == 0 {
                                    Text("오늘 🎉").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
                                } else {
                                    Text("D-\(a.daysUntil)").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(accent.primary)
                                }
                            }
                            .padding(.vertical, 11)
                        }
                    }
                }
            }
        }
    }
}
