package com.gatcha.log.ui.components

import androidx.compose.runtime.Composable

// ============================================================
//  단순 확인 알럿 — 제목 + 메시지 + 확인/취소
//
//  시스템 디자인 원칙: 같은 역할의 시스템 컴포넌트가 있으면 커스텀 대신 시스템 것을 쓴다.
//  - iOS: 네이티브 UIAlertController — 리퀴드 글래스·다크모드·다이나믹 타입·접근성이 자동 적용됨
//  - Android: 기존 GlgDialog(웹앱 스타일 커스텀 다이얼로그) — 기존 디자인 유지
//
//  입력 폼·달력처럼 시스템 알럿으로 표현할 수 없는 콘텐츠형 다이얼로그는 GlgDialog 를 그대로 쓴다.
// ============================================================

/**
 * @param destructive 확인 버튼을 파괴적 동작 스타일로 (iOS: 시스템 레드)
 * @param dismissText null 이면 확인 버튼만 표시
 */
@Composable
expect fun GlgAlert(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "취소",
    destructive: Boolean = false,
)
