package com.gatcha.log.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.URLByAppendingPathComponent
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypeText
import platform.darwin.NSObject

/**
 * iOS 파일 선택/저장 — UIDocumentPickerViewController 기반.
 * Android SAF(rememberLauncherForActivityResult)와 동일한 동작:
 *   - 열기: 다중 선택 → 각 파일 텍스트 내용을 onResult 로 콜백
 *   - 저장: contentProvider 결과를 임시 파일에 쓴 뒤 export 피커로 사용자가 위치 선택
 *
 * 델리게이트(NSObject)는 ARC 가 즉시 해제하지 않도록 remember 로 참조를 유지한다.
 */

/**
 * 키 윈도우 → rootViewController 에서 presentedViewController 체인을 따라 최상위 VC 를 찾는다.
 *
 * UIApplication.keyWindow 는 scene 기반(SwiftUI @main) 앱에서 nil 일 수 있어(deprecated) 쓰지 않는다 —
 * connectedScenes 의 keyWindow 를 찾는다 (iOSApp.swift 의 구글 로그인 presenting VC 탐색과 동일 패턴).
 */
private fun topMostViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstNotNullOfOrNull { scene ->
            scene.keyWindow ?: scene.windows.filterIsInstance<UIWindow>().firstOrNull()
        }
        ?: return null
    var vc = window.rootViewController ?: return null
    while (true) {
        val presented = vc.presentedViewController ?: break
        vc = presented
    }
    return vc
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFileOpenLauncher(onResult: (contents: List<String>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)

    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val contents = didPickDocumentsAtURLs.mapNotNull { item ->
                    val url = item as? NSURL ?: return@mapNotNull null
                    val accessed = url.startAccessingSecurityScopedResource()
                    try {
                        NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)
                    } finally {
                        if (accessed) url.stopAccessingSecurityScopedResource()
                    }
                }
                callback.value(contents)
            }

            // 단일 선택 콜백(구형 시그니처)도 다중 콜백으로 위임
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentAtURL: NSURL,
            ) {
                documentPicker(controller, listOf(didPickDocumentAtURL))
            }
        }
    }

    return remember(delegate) {
        {
            val types = listOf(UTTypeJSON, UTTypeText, UTTypeData)
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
            picker.allowsMultipleSelection = true
            picker.delegate = delegate
            topMostViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFileSaveLauncher(defaultName: String, contentProvider: () -> String?): () -> Unit {
    val provider = rememberUpdatedState(contentProvider)

    // export 모드 피커는 별도 콜백이 필요 없지만, 델리게이트를 유지해 dismiss 처리를 안정화한다.
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {}
    }

    return remember(delegate, defaultName) {
        {
            val content = provider.value()
            if (content != null) {
                val tempDir = NSURL.fileURLWithPath(NSTemporaryDirectory())
                val fileUrl = tempDir.URLByAppendingPathComponent(defaultName)
                if (fileUrl != null) {
                    val ok = (content as NSString).writeToURL(
                        fileUrl,
                        atomically = true,
                        encoding = NSUTF8StringEncoding,
                        error = null,
                    )
                    if (ok) {
                        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(fileUrl))
                        picker.delegate = delegate
                        topMostViewController()?.presentViewController(picker, animated = true, completion = null)
                    }
                }
            }
        }
    }
}

actual fun openUrl(url: String) {
    NSURL.URLWithString(url)?.let { nsUrl ->
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
