package com.gatcha.log.ui.theme

import androidx.compose.ui.graphics.Color

// 강조색(기본: 민트). 테마 선택 시 LocalAccent 로 대체된다.
val MintPrimary = Color(0xFF2EC1A6)
val MintSecondary = Color(0xFF7FE3D0)

val BackgroundGradientStart = Color(0xFFF0F7F6)
val BackgroundGradientEnd = Color(0xFFFFFFFF)

// 글래스모피즘(iOS26 스타일) 토큰 — 반투명 프로스티드 패널 + 밝은 가장자리
val CardBackground = Color(0xB3FFFFFF)        // 흰색 70% — 카드/패널 프로스티드
val GlassBorder = Color(0x99FFFFFF)           // 흰색 60% — 유리 가장자리 하이라이트
val GlassStrong = Color(0xD9FFFFFF)           // 흰색 85% — 가독성이 필요한 패널(시트/다이얼로그)
val GlassNav = Color(0x99FFFFFF)              // 하단 내비 알약
val NavUnselected = Color(0xFF8E8E93)         // 미선택 내비 아이템 (글래스 위 가독성 확보)
val TextPrimary = Color(0xFF1A1C1E)
val TextSecondary = Color(0xFF6C727A)
val WarningBackground = Color(0xFFFFF4E5)
val WarningText = Color(0xFFB37400)
val DangerBackground = Color(0xFFFFE5E5)
val DangerText = Color(0xFFD0021B)
val ProgressEmpty = Color(0xFFE0E0E0)
val DividerColor = Color(0xFFF0F0F0)

// 게임 색상 — 웹앱 GAMES 정의와 동일
val GIColor = Color(0xFF4F8EF7)
val HSRColor = Color(0xFFB06BFF)
val ZZZColor = Color(0xFFF5A623)

/** 강조색 팔레트 (웹앱 테마 색상: 민트·퍼플·인디고·블루·로즈) */
data class AccentOption(val label: String, val color: Color, val secondary: Color)

val AccentPalette: List<AccentOption> = listOf(
    AccentOption("민트", Color(0xFF2EC1A6), Color(0xFF7FE3D0)),
    AccentOption("퍼플", Color(0xFF8B5CF6), Color(0xFFC4B5FD)),
    AccentOption("인디고", Color(0xFF4F46E5), Color(0xFFA5B4FC)),
    AccentOption("블루", Color(0xFF3B82F6), Color(0xFF93C5FD)),
    AccentOption("로즈", Color(0xFFF43F5E), Color(0xFFFDA4AF)),
)