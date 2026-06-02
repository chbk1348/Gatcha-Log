package com.gatcha.log.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iOS 는 하단 인셋 없음 — 탭바가 숨겨진 서브 화면에서 콘텐츠가 화면 끝까지 차지한다.
// (하드 인셋을 주면 홈 인디케이터 높이만큼 빈 띠가 생김 — iOS 표준은 콘텐츠가 그 아래로 흐르는 것)
@Composable
actual fun Modifier.subPageBottomInset(): Modifier = this
