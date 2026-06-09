import SwiftUI
import Shared

// 게임 세그먼트 컨트롤 (게임정보 2.0 — Segmented 레이아웃).
// 디자인(참고 이미지): 단일 글래스 캡슐 + 선택 칸은 옅은 흰 알약 + 어두운 글자(시스템 기본 세그먼티드 룩).
// iOS 는 시스템 Picker(.segmented) 기본 외형 그대로 — iOS 26 은 자동 글래스.
// (Android 는 동일 룩을 커스텀으로 구현) 선택값: "all" | game.key.
struct GameSegmentControl: View {
    @Binding var selected: String

    private var games: [Game] { GameData.shared.attendanceGames }

    var body: some View {
        Picker("게임 선택", selection: $selected) {
            Text("전체").tag("all")
            ForEach(Array(games.enumerated()), id: \.offset) { _, g in
                Text(g.shortName).tag(g.key)
            }
        }
        .pickerStyle(.segmented)
    }
}
