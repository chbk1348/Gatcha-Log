import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 앱 디자인 시스템 — 색상 토큰 / 강조색 팔레트 / 환경값
//
// Kotlin(commonMain) 의 theme/Color.kt·Theme.kt 와 1:1로 맞춘다.
// SwiftUI 재작성 화면들은 Compose 의 LocalAccent 대신 Environment(\.glgAccent) 로 강조색을 읽는다.
// ════════════════════════════════════════════════════════════════════════════

extension Color {
    /// 0xAARRGGBB / 0xRRGGBB 16진수로 Color 생성 (Kotlin Color(0x..) 토큰과 동일하게).
    init(hex: UInt32) {
        let hasAlpha = hex > 0xFF_FF_FF
        let a = hasAlpha ? Double((hex >> 24) & 0xFF) / 255.0 : 1.0
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

/// Color.kt 토큰 미러. (값은 Kotlin 정의와 동일하게 유지할 것)
enum GLGColor {
    static let textPrimary = Color(hex: 0xFF1A1C1E)
    static let textSecondary = Color(hex: 0xFF6C727A)

    static let backgroundGradientStart = Color(hex: 0xFFF0F7F6)
    static let backgroundGradientEnd = Color(hex: 0xFFFFFFFF)

    // 글래스모피즘 토큰 (Liquid Glass 미지원 폴백에서 사용)
    static let cardBackground = Color(hex: 0xB3FFFFFF)   // 흰색 70%
    static let glassBorder = Color(hex: 0x99FFFFFF)      // 흰색 60% 가장자리 하이라이트
    static let glassStrong = Color(hex: 0xD9FFFFFF)      // 흰색 85% — 시트/다이얼로그
    static let navUnselected = Color(hex: 0xFF8E8E93)

    static let warningBackground = Color(hex: 0xFFFFF4E5)
    static let warningText = Color(hex: 0xFFB37400)
    static let dangerBackground = Color(hex: 0xFFFFE5E5)
    static let dangerText = Color(hex: 0xFFD0021B)
    static let progressEmpty = Color(hex: 0xFFE0E0E0)
    static let divider = Color(hex: 0xFFF0F0F0)

    // 스켈레톤 시머 토큰 (Android SkeletonBase/SkeletonHighlight 와 패리티)
    static let skeletonBase = Color(hex: 0xFFEAEAF0)
    static let skeletonHighlight = Color(hex: 0xFFF6F6FA)

    // 게임 색상 (웹앱 GAMES 정의와 동일)
    static let genshin = Color(hex: 0xFF4F8EF7)
    static let hsr = Color(hex: 0xFFB06BFF)
    static let zzz = Color(hex: 0xFFF5A623)
}

/// 강조색 팔레트 (Color.kt 의 AccentPalette 와 동일 순서·색상).
///
/// **3톤을 구분해 쓴다** — `primary` 면·게이지·버튼(흰 바탕 대비 3.90) /
/// `deep` **글자·아이콘**(5.20, 본문 크기 AA 통과) / `tint` 아주 옅은 면(명도 96.8 · 채도 20 고정) /
/// `secondary` 그라데이션 끝단(1.90).
///
/// 글자에 `primary` 를 쓰면 테마에 따라 흐려진다 — 그 자리는 `deep` 이다.
/// 자세한 근거는 Kotlin 쪽 `AccentOption` 문서에 있다.
struct GLGAccent: Identifiable {
    let index: Int
    let label: String
    let primary: Color
    let secondary: Color
    let deep: Color
    let tint: Color
    var id: Int { index }
}

enum GLGTheme {
    /// AccentPalette — 민트·퍼플·인디고·블루·로즈 (Color.kt 와 동일).
    /// AccentPalette — 색조 36도 균등 10색(Color.kt 와 동일 순서·색상).
    /// 기본값은 틸(index 5) — 앱 아이콘 색조에 가장 가까운 슬롯이다.
    static let palette: [GLGAccent] = [
        GLGAccent(index: 0, label: "레드", primary: Color(hex: 0xFFDE5145), secondary: Color(hex: 0xFFEFABA5), deep: Color(hex: 0xFFCC3224), tint: Color(hex: 0xFFF8F5F5)),
        GLGAccent(index: 1, label: "머스터드", primary: Color(hex: 0xFFAC7704), secondary: Color(hex: 0xFFF9AE0C), deep: Color(hex: 0xFF916404), tint: Color(hex: 0xFFF8F7F5)),
        GLGAccent(index: 2, label: "올리브", primary: Color(hex: 0xFF668D04), secondary: Color(hex: 0xFF95CD05), deep: Color(hex: 0xFF567703), tint: Color(hex: 0xFFF8F8F5)),
        GLGAccent(index: 3, label: "그린", primary: Color(hex: 0xFF1C950C), secondary: Color(hex: 0xFF29D912), deep: Color(hex: 0xFF187E0A), tint: Color(hex: 0xFFF6F8F5)),
        GLGAccent(index: 4, label: "에메랄드", primary: Color(hex: 0xFF159452), secondary: Color(hex: 0xFF1FD778), deep: Color(hex: 0xFF127D46), tint: Color(hex: 0xFFF5F8F7)),
        GLGAccent(index: 5, label: "틸", primary: Color(hex: 0xFF1B8E99), secondary: Color(hex: 0xFF38CEDC), deep: Color(hex: 0xFF177881), tint: Color(hex: 0xFFF5F8F8)),
        GLGAccent(index: 6, label: "블루", primary: Color(hex: 0xFF507EE0), secondary: Color(hex: 0xFFA5BCEF), deep: Color(hex: 0xFF3066DA), tint: Color(hex: 0xFFF5F6F8)),
        GLGAccent(index: 7, label: "바이올렛", primary: Color(hex: 0xFF8E6BE5), secondary: Color(hex: 0xFFC4B3F2), deep: Color(hex: 0xFF7950E0), tint: Color(hex: 0xFFF6F5F8)),
        GLGAccent(index: 8, label: "마젠타", primary: Color(hex: 0xFFCB42DE), secondary: Color(hex: 0xFFE7A6EF), deep: Color(hex: 0xFFB523C8), tint: Color(hex: 0xFFF8F5F8)),
        GLGAccent(index: 9, label: "핑크", primary: Color(hex: 0xFFDE4594), secondary: Color(hex: 0xFFEFA7CC), deep: Color(hex: 0xFFCA247A), tint: Color(hex: 0xFFF8F5F7)),
    ]

    /// 기본 강조색 인덱스 — 틸. Kotlin `DEFAULT_ACCENT_INDEX` 와 같은 값이어야 한다.
    static let defaultIndex: Int = 5

    /// 인덱스 → 강조색 (범위를 벗어나면 기본값으로 폴백 — Kotlin getOrElse 동작과 동일).
    static func accent(_ index: Int) -> GLGAccent {
        palette.indices.contains(index) ? palette[index] : palette[defaultIndex]
    }
}

// ── Environment 주입 — 화면들이 강조색을 읽는 경로 ──────────────────────────

private struct GLGAccentKey: EnvironmentKey {
    static let defaultValue: GLGAccent = GLGTheme.palette[GLGTheme.defaultIndex]
}

extension EnvironmentValues {
    /// 현재 강조색. 루트에서 `.environment(\.glgAccent, ...)` 로 주입한다.
    var glgAccent: GLGAccent {
        get { self[GLGAccentKey.self] }
        set { self[GLGAccentKey.self] = newValue }
    }
}

extension View {
    /**
     강조색 인덱스를 **환경값으로만** 주입한다.

     ⚠️ 예전엔 여기서 `.tint(accent.primary)` 를 전역으로 깔았다. 그러면 앱이 그리지 않는
     시스템 UI — 특히 **얼럿 버튼** — 까지 테마색으로 물든다. 민트·라임 같은 밝은 강조색은
     얼럿의 흰 배경에서 대비가 낮아 글씨가 잘 안 보였다.

     얼럿만 예외로 두는 방법이 없다(버튼마다 `.tint()` 를 걸어 봐도 전역 tint 를 못 이긴다.
     `UIView.appearance(whenContainedInInstancesOf: [UIAlertController.self])` 도 SwiftUI
     `.alert` 에는 닿지 않는다 — 둘 다 실제로 적용해 보고 확인). 그래서 전역 주입을 걷어낸다.

     강조색이 필요한 시스템 컨트롤(Toggle·ProgressView·세그먼티드 등)에는 **그 자리에서**
     `.tint(accent.primary)` 를 건다 — 앱 안 30여 곳이 이미 그렇게 하고 있었다.
     커스텀 컴포넌트는 `@Environment(\.glgAccent)` 로 읽으므로 영향이 없다.
     */
    func glgAccent(index: Int) -> some View {
        let accent = GLGTheme.accent(index)
        return self
            .environment(\.glgAccent, accent)
            // ⚠️ `.tint(nil)` 은 지우다 만 코드가 아니라 **상속을 끊는 장치**다.
            // 탭바 아이콘 색을 주려고 `TabView` 에 건 `.tint(accent)` 가 탭 콘텐츠 전체로
            // 흘러내려, 그 안에서 뜨는 얼럿까지 테마색으로 물들였다(전역 주입을 걷어낸 뒤에도
            // '지출 전체 삭제' 얼럿이 그대로였던 이유). 여기서 기본값으로 되돌린다.
            .tint(nil)
    }

    /**
     얼럿 버튼 색 — 앱 테마 강조색이 아니라 **시스템 파랑**.

     앱 루트의 [glgAccent] 가 `.tint(강조색)` 를 전역으로 깔아서, 테마를 바꾸면 '취소'·'계속'
     같은 얼럿 버튼까지 그 색으로 물든다. 얼럿은 OS 가 그리는 표준 UI 라 앱 테마를 따를 이유가
     없고, 파괴적 동작(`role: .destructive`)의 빨강과 테마색이 나란히 놓이면 무엇이 위험한
     선택인지 흐려진다.

     `UIView.appearance(whenContainedInInstancesOf: [UIAlertController.self])` 로는 안 된다 —
     SwiftUI 의 `.alert` 은 UIAlertController 를 거치지 않아 appearance proxy 가 닿지 않는다
     (실제로 적용해 보고 확인). 버튼마다 직접 거는 수밖에 없다.

     `role: .destructive` 버튼과 얼럿 안 TextField 에도 붙인다. destructive 는 시스템이 빨강으로
     덮으므로 대개 무의미하지만, 얼럿 안의 것은 **하나도 빠짐없이** 거는 편이 낫다 —
     빠진 하나가 강조색으로 남으면 그것만 눈에 띈다.
     */
    func glgAlertTint() -> some View { tint(Color(.systemBlue)) }
}
