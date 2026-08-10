import SwiftUI
import Shared

// 게임 세그먼트 컨트롤 (게임정보 2.0 — Segmented 레이아웃).
// 디자인(참고 이미지): 단일 글래스 캡슐 + 선택 칸은 옅은 흰 알약 + 어두운 글자(시스템 기본 세그먼티드 룩).
// iOS 는 시스템 Picker(.segmented) 기본 외형 그대로 — iOS 26 은 자동 글래스.
// (Android 는 동일 룩을 커스텀으로 구현) 선택값: "all" | game.key.
struct GameSegmentControl: View {
    @Binding var selected: String
    /// 노출할 게임. 기본은 출석 지원 3게임(게임정보 헤더용) — 공지처럼 대상이 다르면 넘겨서 쓴다.
    var games: [Game] = GLGGames.attendance
    /**
     라벨을 약칭(GI·HSR·EF)으로. 세그먼티드는 **항목 폭을 균등 분할**하므로 칸이 늘면
     "스타레일"·"엔드필드" 같은 이름이 뭉개진다. 5칸을 넘으면 켜는 쪽이 낫다
     (목록 행의 게임 배지와 같은 표기라 눈으로도 이어진다).
     */
    var useAbbr: Bool = false

    var body: some View {
        Picker("게임 선택", selection: $selected) {
            Text(useAbbr ? "ALL" : "전체").tag("all")
            ForEach(Array(games.enumerated()), id: \.offset) { _, g in
                Text(useAbbr ? g.abbr : g.shortName).tag(g.key)
            }
        }
        .pickerStyle(.segmented)
    }
}
