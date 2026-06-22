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
struct GLGAccent: Identifiable {
    let index: Int
    let label: String
    let primary: Color
    let secondary: Color
    var id: Int { index }
}

enum GLGTheme {
    /// AccentPalette — 민트·퍼플·인디고·블루·로즈 (Color.kt 와 동일).
    static let palette: [GLGAccent] = [
        GLGAccent(index: 0, label: "민트", primary: Color(hex: 0xFF34D1B6), secondary: Color(hex: 0xFF7FE3D0)),
        GLGAccent(index: 1, label: "퍼플", primary: Color(hex: 0xFF8B5CF6), secondary: Color(hex: 0xFFC4B5FD)),
        GLGAccent(index: 2, label: "인디고", primary: Color(hex: 0xFF4F46E5), secondary: Color(hex: 0xFFA5B4FC)),
        GLGAccent(index: 3, label: "블루", primary: Color(hex: 0xFF3B82F6), secondary: Color(hex: 0xFF93C5FD)),
        GLGAccent(index: 4, label: "로즈", primary: Color(hex: 0xFFF43F5E), secondary: Color(hex: 0xFFFDA4AF)),
    ]

    /// 인덱스 → 강조색 (범위를 벗어나면 민트로 폴백 — Kotlin getOrElse 동작과 동일).
    static func accent(_ index: Int) -> GLGAccent {
        palette.indices.contains(index) ? palette[index] : palette[0]
    }
}

// ── Environment 주입 — 화면들이 강조색을 읽는 경로 ──────────────────────────

private struct GLGAccentKey: EnvironmentKey {
    static let defaultValue: GLGAccent = GLGTheme.palette[0]
}

extension EnvironmentValues {
    /// 현재 강조색. 루트에서 `.environment(\.glgAccent, ...)` 로 주입한다.
    var glgAccent: GLGAccent {
        get { self[GLGAccentKey.self] }
        set { self[GLGAccentKey.self] = newValue }
    }
}

extension View {
    /// 강조색 인덱스로 환경값 + 시스템 tint 를 한 번에 주입한다.
    func glgAccent(index: Int) -> some View {
        let accent = GLGTheme.accent(index)
        return self
            .environment(\.glgAccent, accent)
            .tint(accent.primary)
    }
}
