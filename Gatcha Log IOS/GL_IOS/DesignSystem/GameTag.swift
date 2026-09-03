import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 게임 태그 — 앱 전역에서 "이건 어느 게임인가"를 나타내는 **단 하나의 표기**.
//
// 예전엔 화면마다 제각각이었다: 8·9·10pt 컬러 닷 / 컬러 바 / 솔리드 게임색 + 흰 약칭 /
// 12% 틴트 뱃지 / 심지어 게임색이 아니라 앱 강조색 닷(= 모든 게임이 같은 색이라 구분 불가).
// 텍스트도 shortName·displayName·하드코딩이 섞여 있었다.
//
// 규격은 **지출 내역 로우의 태그**를 정본으로 삼는다 — 게임색 14% 배경 + 게임색 약칭(abbr).
// 색으로만 구분하지 않고 약칭을 함께 쓰는 게 핵심이다(색만으로는 무엇인지 알 수 없다).
//
// (Compose 패리티: GL_Android/ui/components/GameTag.kt)
// ════════════════════════════════════════════════════════════════════════════

enum GameTagSize {
    /// 조밀한 리스트 행용 — 28pt.
    case small
    /// 기본 — 40pt. 지출 내역 로우와 동일.
    case medium

    var box: CGFloat { self == .small ? 28 : 40 }
    var radius: CGFloat { self == .small ? 9 : 12 }
    var fontSize: CGFloat { self == .small ? 10 : 13 }
}

/// - Parameter game: 게임 식별 문자열 — displayName·shortName·key 아무거나 받는다.
///                   매칭 실패 시 앞 2글자를 약칭으로 쓰고 폴백 색을 적용한다.
/// - Parameter abbrOverride: 앱이 다루지 않는 IP 를 같은 규격으로 그릴 때의 약칭.
///                   GameData 폴백은 앞 2글자("붕괴")라 약칭이 안 되고, 색도 전부 원신 색으로 뭉개진다
///                   (colorFor 의 폴백이 GENSHIN). 그런 자리에서만 쓴다 — 앱 게임에는 넘기지 말 것.
/// - Parameter colorOverride: 위와 같은 목적의 색(ARGB Int64).
struct GLGGameTag: View {
    let game: String
    var size: GameTagSize = .medium
    var abbrOverride: String? = nil
    var colorOverride: Int64? = nil

    var body: some View {
        // 예전엔 목록 전체를 브리지로 가져와 훑고(약칭), 색을 또 따로 조회해서 **태그 하나에 조회가 두 번**
        // 일어났다. 지출 목록은 행마다 이 태그를 그린다. 이제 게임을 한 번만 찾아 둘 다 꺼낸다.
        // (공유 쪽 byNameOrNull 은 이름 인덱스라 O(1) — displayName·shortName·key 를 모두 받는다)
        let g = GameData.shared.byNameOrNull(name: game)
        let abbr = abbrOverride ?? g?.abbr ?? String(game.prefix(2))
        let color = Color(argb64: colorOverride ?? g?.color ?? GameData.shared.colorFor(name: game))

        Text(abbr)
            .font(.pretendard(size: size.fontSize, weight: .heavy))
            .foregroundStyle(color)
            .frame(width: size.box, height: size.box)
            .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: size.radius, style: .continuous))
    }
}
