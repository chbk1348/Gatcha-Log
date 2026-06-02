package com.gatcha.log.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ============================================================
//  탭바가 보이지 않는 서브 화면(지출 상세·달력·연간 리포트·설정 등)의 하단 인셋
//
//  - Android: 제스처 내비 바 패딩 — 콘텐츠가 시스템 바에 가리지 않도록 (기존 동작)
//  - iOS: 패딩 없음 — 콘텐츠가 화면 끝까지 차지 (홈 인디케이터 아래로 흐르는 게 iOS 표준,
//          하드 인셋을 주면 하단에 빈 띠가 생긴다)
// ============================================================

@Composable
expect fun Modifier.subPageBottomInset(): Modifier
