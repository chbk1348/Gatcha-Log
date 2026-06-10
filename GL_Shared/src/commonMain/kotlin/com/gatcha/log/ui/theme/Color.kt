package com.gatcha.log.ui.theme

/**
 * 강조색 팔레트 (웹앱 테마 색상: 민트·퍼플·인디고·블루·로즈).
 * 색상은 플랫폼 중립 ARGB Long(0xAARRGGBB) — iOS 는 SKIE 로 Int64 로 받아 SwiftUI Color 로 변환.
 */
data class AccentOption(val label: String, val color: Long, val secondary: Long)

val AccentPalette: List<AccentOption> = listOf(
    AccentOption("민트", 0xFF34D1B6L, 0xFF7FE3D0L),
    AccentOption("퍼플", 0xFF8B5CF6L, 0xFFC4B5FDL),
    AccentOption("인디고", 0xFF4F46E5L, 0xFFA5B4FCL),
    AccentOption("블루", 0xFF3B82F6L, 0xFF93C5FDL),
    AccentOption("로즈", 0xFFF43F5EL, 0xFFFDA4AFL),
)
