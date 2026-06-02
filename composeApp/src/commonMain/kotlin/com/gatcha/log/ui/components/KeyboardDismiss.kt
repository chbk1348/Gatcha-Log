package com.gatcha.log.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 입력 필드 바깥을 탭하면 포커스 해제 + 키보드 숨김.
 *
 * iOS 는 Android 의 뒤로가기 버튼 같은 시스템 차원의 키보드 닫기 수단이 없어
 * 앱이 직접 처리하지 않으면 키보드가 화면에 고정된다 (숫자 키패드는 리턴 키도 없음).
 * Android 에서도 "입력 밖 탭으로 키보드 닫기"는 표준 UX 라 함께 적용된다.
 *
 * 자식 clickable·입력 필드가 소비한 탭에는 반응하지 않으므로
 * 화면 루트 컨테이너에 걸어도 기존 버튼/스크롤 동작을 방해하지 않는다.
 *
 * 적용 위치: iOS 탭 호스트(TabPage)·지출 모달 호스트(MainViewController.kt), GlgDialog.
 */
@Composable
fun Modifier.dismissKeyboardOnTapOutside(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                focusManager.clearFocus()
                keyboard?.hide()
            },
        )
    }
}
