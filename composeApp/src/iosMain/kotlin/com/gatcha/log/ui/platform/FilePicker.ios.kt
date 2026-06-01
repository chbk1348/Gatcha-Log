package com.gatcha.log.ui.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS 파일 선택/저장 — 5단계에서 UIDocumentPickerViewController 로 구현 예정.
 * 현재는 no-op (버튼을 눌러도 동작하지 않음 — UI 는 정상 표시).
 */

@Composable
actual fun rememberFileOpenLauncher(onResult: (contents: List<String>) -> Unit): () -> Unit {
    return { /* 5단계: UIDocumentPickerViewController */ }
}

@Composable
actual fun rememberFileSaveLauncher(defaultName: String, contentProvider: () -> String?): () -> Unit {
    return { /* 5단계: UIDocumentPickerViewController (export 모드) */ }
}

actual fun openUrl(url: String) {
    NSURL.URLWithString(url)?.let { nsUrl ->
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
