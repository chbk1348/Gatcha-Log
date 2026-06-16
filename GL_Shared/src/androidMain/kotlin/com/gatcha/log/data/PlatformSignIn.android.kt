package com.gatcha.log.data

/**
 * Android 구글 로그인 브리지.
 *
 * Credential Manager / 웹 OAuth 는 AndroidX 의존성과 **Activity 컨텍스트**가 필요해
 * shared(KMP) androidMain 으로 끌어올리지 않고, :app 이 실제 로그인 플로우를
 * [AndroidGoogleSignIn.provider] 에 등록한다(iOS 의 [IosGoogleSignIn.provider] 와 동일 패턴).
 *
 * ```kotlin
 * // GatchaApp.onCreate
 * AndroidGoogleSignIn.provider = { autoSelectOnly ->
 *     AndroidGoogleSignInProvider.signIn(autoSelectOnly) // PlatformSignInResult?
 * }
 * ```
 */
object AndroidGoogleSignIn {
    /**
     * :app 이 등록하는 로그인 플로우.
     * 반환 null = 로그인 불가/취소(자동선택 계정 없음 포함). provider 미등록 시에도 null.
     */
    var provider: (suspend (autoSelectOnly: Boolean) -> PlatformSignInResult?)? = null
}

internal actual suspend fun platformGoogleSignIn(autoSelectOnly: Boolean): PlatformSignInResult? =
    AndroidGoogleSignIn.provider?.invoke(autoSelectOnly)
