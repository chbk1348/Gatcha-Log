package com.gatcha.log.data

import com.gatcha.log.auth.GoogleCredentialManager
import com.gatcha.log.auth.GoogleWebOAuth
import com.gatcha.log.storage.ActivityHolder

/**
 * Android 구글 로그인 실구현 — Shared 의 [AndroidGoogleSignIn.provider] 백엔드.
 *
 * **하이브리드 (v27.38.0)**
 * ```
 * 1순위  Credential Manager (네이티브 계정 시트)
 *          ├─ 성공        → 끝
 *          ├─ 사용자 취소  → 끝 (폴백 안 함 — 닫았는데 브라우저가 또 뜨면 안 된다)
 *          └─ 사용 불가    ↓  (GMS 부재 · SHA-1 미등록 · 기기에 계정 없음)
 * 2순위  웹 OAuth (PKCE + Custom Tabs)  ← 기기 계정·GMS·서명키와 무관한 최종 안전망
 * ```
 * 예전에는 Credential Manager 를 붙였다가 기기별 실패(BadAuthentication)로 **전면 제거**하고 웹 OAuth
 * 단일 경로로 되돌렸었다. 이번에는 웹 OAuth 를 지우지 않고 폴백으로 남긴 채 CM 을 1순위로만 얹어,
 * 특정 기기에서 CM 이 죽어도 **로그인 자체가 막히지는 않는다**.
 *
 * 반환 규약 ([platformGoogleSignIn] 계약):
 *  - 성공 → [PlatformSignInResult] (accessToken 은 Android 미사용 → 빈 문자열)
 *  - 사용자 취소 / 실패 → null (Shared 에서 NoCredential 처리)
 */
object AndroidGoogleSignInProvider {

    suspend fun signIn(autoSelectOnly: Boolean): PlatformSignInResult? {
        // 무탭(silent) 재로그인 경로는 쓰지 않는다 — 앱 진입 직후 계정 시트·브라우저가 튀어나오면 안 된다.
        if (autoSelectOnly) return null
        val activity = ActivityHolder.current ?: return null

        return when (val outcome = GoogleCredentialManager.signIn(activity)) {
            is GoogleCredentialManager.Outcome.Success ->
                outcome.tokens.let { result(it.idToken, it.email.orEmpty(), it.name, it.picture) }

            GoogleCredentialManager.Outcome.Cancelled -> null

            GoogleCredentialManager.Outcome.Unavailable -> {
                val tokens = GoogleWebOAuth.signIn(activity) ?: return null
                result(tokens.idToken, tokens.email.orEmpty(), tokens.name, tokens.picture)
            }
        }
    }

    /** 로그아웃 — 다음 로그인에서 계정 선택 화면이 다시 뜨도록 CM 상태를 비운다. */
    suspend fun signOut() {
        val activity = ActivityHolder.current ?: return
        GoogleCredentialManager.clearState(activity)
    }

    private fun result(idToken: String, email: String, name: String?, picture: String?) =
        PlatformSignInResult(
            idToken = idToken,
            accessToken = "", // Android 는 Firebase 인증에 accessToken 불필요
            email = email,
            name = name ?: email.substringBefore("@"),
            photoUrl = picture.orEmpty(),
        )
}
