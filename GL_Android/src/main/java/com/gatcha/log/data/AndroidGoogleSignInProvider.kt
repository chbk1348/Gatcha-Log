package com.gatcha.log.data

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.gatcha.log.auth.GoogleWebOAuth
import com.gatcha.log.storage.ActivityHolder
import com.gatcha.log.storage.AppContext
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Android 구글 로그인 실구현 — Shared 의 [AndroidGoogleSignIn.provider] 백엔드.
 *
 * AndroidX **Credential Manager** 네이티브 원탭(바텀시트) + (시스템 계정 없을 때) 브라우저 웹 OAuth 폴백.
 * Shared [AuthManager] 가 계정 상태·영속·Firebase 인증을 담당하므로, 여기서는 **토큰/프로필만** 돌려준다.
 *
 * 반환 규약 ([platformGoogleSignIn] 계약):
 *  - 성공 → [PlatformSignInResult] (accessToken 은 Android 미사용 → 빈 문자열)
 *  - 자동선택 계정 없음 / 사용자 취소 → null (Shared 에서 NoCredential 처리)
 *  - 하드 에러 → throw (Shared 에서 Error 처리)
 */
object AndroidGoogleSignInProvider {

    suspend fun signIn(autoSelectOnly: Boolean): PlatformSignInResult? {
        val activity = ActivityHolder.current ?: return null
        val serverClientId = webClientId() ?: throw IllegalStateException("로그인 설정이 없어요")
        val credentialManager = CredentialManager.create(AppContext.appContext)

        val option = if (autoSelectOnly) {
            // 무탭 복귀: 이미 인가된 계정만 자동선택(없으면 NoCredential → UI 로그인 버튼 노출).
            GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
        } else {
            // 명시적 로그인: 기기의 모든 구글 계정 + 계정 추가 노출.
            GetSignInWithGoogleOption.Builder(serverClientId).build()
        }
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val result = credentialManager.getCredential(activity, request)
            parseCredential(result.credential)
        } catch (e: NoCredentialException) {
            // 시스템 계정에 구글 계정 없음 — 무탭 복귀면 null, 명시적 로그인이면 웹 OAuth 폴백.
            if (autoSelectOnly) null else webOAuthFallback(activity)
        } catch (e: GetCredentialCancellationException) {
            null // 사용자 취소 → NoCredential 로 수렴(로그인 화면 유지)
        } catch (e: GetCredentialException) {
            Log.e("GatchaAuth", "credential sign-in failed", e)
            if (autoSelectOnly) throw e else webOAuthFallback(activity)
        }
    }

    /** Credential Manager 에서 시스템 구글 계정을 못 찾을 때 — 브라우저 웹 OAuth 로 로그인. */
    private suspend fun webOAuthFallback(activityContext: Context): PlatformSignInResult? {
        val tokens = GoogleWebOAuth.signIn(activityContext) ?: return null
        val email = tokens.email.orEmpty()
        return PlatformSignInResult(
            idToken = tokens.idToken,
            accessToken = "",
            email = email,
            name = tokens.name ?: email.substringBefore("@"),
            photoUrl = tokens.picture.orEmpty(),
        )
    }

    private fun parseCredential(credential: Credential): PlatformSignInResult? {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val email = google.id // GoogleIdTokenCredential.id = 계정 이메일
            return PlatformSignInResult(
                idToken = google.idToken,
                accessToken = "",
                email = email,
                name = google.displayName ?: email.substringBefore("@"),
                photoUrl = google.profilePictureUri?.toString().orEmpty(),
            )
        }
        return null
    }

    /** google-services.json 적용 시 생성되는 OAuth Web client id (없으면 null → Firebase 미설정). */
    private fun webClientId(): String? {
        val ctx = AppContext.appContext
        val resId = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        return if (resId != 0) ctx.getString(resId) else null
    }
}
