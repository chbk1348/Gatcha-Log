@file:JvmName("AndroidThemeColorKt")  // Shared(commonMain) Color.kt 와 동일 패키지·파일명이라 ColorKt 파사드가 dex 병합 충돌 → 파사드명 분리.

package com.gatcha.log.ui.theme

import androidx.compose.ui.graphics.Color

// 강조색(기본: 민트 — 앱 아이콘 틸과 동일색). 테마 선택 시 LocalAccent 로 대체된다.
val MintPrimary = Color(0xFF34D1B6)
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

// 스켈레톤 시머 토큰 — base(바탕)·highlight(흐르는 밝은 띠). iOS GLGColor.skeletonBase/Highlight 와 패리티.
val SkeletonBase = Color(0xFFEAEAF0)
val SkeletonHighlight = Color(0xFFF6F6FA)

// 게임 색상 — 웹앱 GAMES 정의와 동일
val GIColor = Color(0xFF4F8EF7)
val HSRColor = Color(0xFFB06BFF)
val ZZZColor = Color(0xFFF5A623)

// AccentOption / AccentPalette 는 GL_Shared(commonMain) 정본 사용(color/secondary 는 Long ARGB → toColor()).
// 동일 FQN 중복 정의 시 dex 병합 충돌 → 여기서 재정의하지 않는다.