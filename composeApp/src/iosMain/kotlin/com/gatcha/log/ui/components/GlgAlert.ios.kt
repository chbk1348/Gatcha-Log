package com.gatcha.log.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

// iOS 는 네이티브 시스템 알럿(UIAlertController) —
// 리퀴드 글래스·다크모드·다이나믹 타입·접근성·버튼 배치가 모두 시스템 기준으로 자동 적용된다.
@Composable
actual fun GlgAlert(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String?,
    destructive: Boolean,
) {
    // 콜백은 항상 최신 람다를 참조 (리컴포지션으로 람다가 바뀌어도 안전)
    val confirm by rememberUpdatedState(onConfirm)
    val dismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(title, message, confirmText, dismissText, destructive) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        // 취소 — Cancel 스타일 (시스템이 굵은 글씨·배치 자동 처리)
        if (dismissText != null) {
            alert.addAction(
                UIAlertAction.actionWithTitle(dismissText, UIAlertActionStyleCancel) { _ -> dismiss() },
            )
        }
        // 확인 — 기본 또는 파괴적(레드) 스타일
        alert.addAction(
            UIAlertAction.actionWithTitle(
                confirmText,
                if (destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault,
            ) { _ -> confirm() },
        )
        topMostViewController()?.presentViewController(alert, animated = true, completion = null)

        onDispose {
            // 호출부 상태 변화로 알럿이 컴포지션에서 사라질 때(화면 이탈 등) 아직 떠 있으면 닫는다.
            // 버튼 탭으로 이미 닫힌 경우엔 presentingViewController 가 nil 이라 중복 dismiss 없음.
            if (alert.presentingViewController != null) {
                alert.dismissViewControllerAnimated(false, completion = null)
            }
        }
    }
}

/**
 * 최상위 뷰컨트롤러 — connectedScenes 의 keyWindow 에서 presentedViewController 체인을 따라간다.
 * (FilePicker.ios.kt 와 동일 패턴: keyWindow deprecated 대응)
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
