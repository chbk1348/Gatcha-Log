package com.gatcha.log.data

/**
 * Android(composeApp) 구글 로그인 — 미구현.
 * Android 프로덕션은 :app(Credential Manager + Firebase)이 담당하므로 composeApp 의
 * androidMain 은 컴파일 호환만 유지한다. (composeApp 기반 Android 앱 전환 시 :app 구현 이식 예정)
 */
internal actual suspend fun platformGoogleSignIn(autoSelectOnly: Boolean): PlatformSignInResult? = null
