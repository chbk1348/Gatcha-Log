package com.gatcha.log.data

import android.content.Context
import com.gatcha.log.auth.GoogleWebOAuth
import com.gatcha.log.storage.ActivityHolder

/**
 * Android 구글 로그인 실구현 — Shared 의 [AndroidGoogleSignIn.provider] 백엔드.
 *
 * **브라우저 기반 웹 OAuth(PKCE) 단일 경로** (Credential Manager 원탭 제거).
 * Credential Manager 는 기기 GMS 계정 상태(BadAuthentication·"Long live credential not available")와
 * 앱 SHA-1 등록에 의존해 기기에 따라 토큰 발급이 실패했다 → 기기 계정/SHA 와 무관하게 동일하게 동작하는
 * 웹 OAuth([GoogleWebOAuth])로 전면 전환. id_token 은 Firebase(GoogleAuthProvider) 인증에 그대로 전달.
 *
 * 반환 규약 ([platformGoogleSignIn] 계약):
 *  - 성공 → [PlatformSignInResult] (accessToken 은 Android 미사용 → 빈 문자열)
 *  - 사용자 취소 / 실패 → null (Shared 에서 NoCredential 처리)
 */
object AndroidGoogleSignInProvider {

    suspend fun signIn(autoSelectOnly: Boolean): PlatformSignInResult? {
        // 무탭(silent) 재로그인은 브라우저 OAuth 로 불가 — 현재 호출되지 않으나 방어적으로 무시.
        if (autoSelectOnly) return null
        val activity = ActivityHolder.current ?: return null
        val tokens = GoogleWebOAuth.signIn(activity) ?: return null
        val email = tokens.email.orEmpty()
        return PlatformSignInResult(
            idToken = tokens.idToken,
            accessToken = "", // Android 는 Firebase 인증에 accessToken 불필요
            email = email,
            name = tokens.name ?: email.substringBefore("@"),
            photoUrl = tokens.picture.orEmpty(),
        )
    }
}
