package com.gatcha.log.ui.components

// iOS 는 글래스 룩 버튼 사용 (iOS 26 리퀴드 글래스 디자인 언어).
// 렌더링은 commonMain GlassButtons.kt 의 Compose 구현 — 화면 전환 애니메이션과 완벽 동기화.
actual fun isGlassButtonsSupported(): Boolean = true
