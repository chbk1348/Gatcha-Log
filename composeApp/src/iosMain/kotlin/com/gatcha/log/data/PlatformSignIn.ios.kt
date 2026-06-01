package com.gatcha.log.data

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS 구글 로그인 브리지.
 *
 * Kotlin 은 GoogleSignIn iOS SDK(Swift Package)를 직접 호출할 수 없으므로,
 * Swift 가 앱 시작 시 [IosGoogleSignIn.provider] 에 로그인 플로우를 등록한다:
 *
 * ```swift
 * // iOSApp.swift
 * IosGoogleSignIn.shared.provider = { callback in
 *     GIDSignIn.sharedInstance.signIn(withPresenting: rootVC) { result, error in
 *         let user = result?.user
 *         callback(user?.idToken?.tokenString,
 *                  user?.profile?.email,
 *                  user?.profile?.name,
 *                  user?.profile?.imageURL(withDimension: 200)?.absoluteString)
 *     }
 * }
 * ```
 */
object IosGoogleSignIn {
    /**
     * Swift 가 등록하는 로그인 플로우.
     * 파라미터: 완료 콜백(idToken, accessToken, email, name, photoUrl) — 실패/취소 시 idToken = null.
     * accessToken 은 iOS Firebase 인증(GoogleAuthProvider.credential)에 필수.
     */
    var provider: ((callback: (String?, String?, String?, String?, String?) -> Unit) -> Unit)? = null
}

internal actual suspend fun platformGoogleSignIn(autoSelectOnly: Boolean): PlatformSignInResult? {
    val provider = IosGoogleSignIn.provider ?: return null
    return suspendCancellableCoroutine { cont ->
        provider { idToken, accessToken, email, name, photoUrl ->
            if (cont.isActive) {
                cont.resume(
                    if (idToken.isNullOrBlank()) null
                    else PlatformSignInResult(
                        idToken = idToken,
                        accessToken = accessToken.orEmpty(),
                        email = email.orEmpty(),
                        name = name.orEmpty(),
                        photoUrl = photoUrl.orEmpty(),
                    ),
                )
            }
        }
    }
}
