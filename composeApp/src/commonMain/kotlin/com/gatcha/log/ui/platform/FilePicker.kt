package com.gatcha.log.ui.platform

import androidx.compose.runtime.Composable

/**
 * 플랫폼 파일 선택/저장 추상화 (expect/actual).
 * :app 의 ActivityResultContracts(SAF) 기반 파일 가져오기/내보내기를 대체한다.
 *
 * - Android: rememberLauncherForActivityResult + ContentResolver
 * - iOS: UIDocumentPickerViewController (5단계 구현 예정 — 현재 no-op)
 *
 * 파일 내용은 문자열(JSON)로 주고받는다 — ViewModel 은 Uri 대신 내용만 다룬다.
 */

/** 파일 열기(다중 선택). 선택된 각 파일의 텍스트 내용을 [onResult] 로 전달. 반환값 = 선택기 띄우는 함수. */
@Composable
expect fun rememberFileOpenLauncher(onResult: (contents: List<String>) -> Unit): () -> Unit

/** 파일 저장. [contentProvider] 가 만든 텍스트를 [defaultName] 파일로 저장. 반환값 = 저장 다이얼로그 띄우는 함수. */
@Composable
expect fun rememberFileSaveLauncher(defaultName: String, contentProvider: () -> String?): () -> Unit

/** 외부 브라우저로 URL 열기 (릴리스 페이지 등) */
expect fun openUrl(url: String)
