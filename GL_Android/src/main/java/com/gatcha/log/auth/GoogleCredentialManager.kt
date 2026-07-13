package com.gatcha.log.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Credential Manager 기반 구글 로그인 — 네이티브 계정 시트(브라우저 없음).
 *
 * **폴백 전제:** 이 경로는 Google Play 서비스와 콘솔에 등록된 앱 서명 SHA-1 에 의존한다.
 * 둘 중 하나라도 어긋나면 [Outcome.Unavailable] 을 돌려주고, 호출부가 웹 OAuth([GoogleWebOAuth])로
 * 넘어간다. 예전에 Credential Manager 를 전면 제거했던 이유가 이 의존성이었으므로(BadAuthentication),
 * 이번에는 **지우지 않고 폴백을 남긴 채** 1순위로만 쓴다.
 *
 * 사용자가 시트를 직접 닫은 경우([Outcome.Cancelled])는 폴백하지 않는다 — 취소했는데 브라우저가
 * 뜨면 유저 입장에선 "닫았는데 또 뜬다"가 된다.
 */
object GoogleCredentialManager {

    /**
     * Credential Manager 의 serverClientId 는 **Web 클라이언트 ID** 다(Android 클라이언트 ID 가 아니다).
     * 발급되는 id_token 의 aud 가 이 값이 되며, 같은 Firebase 프로젝트(gatcha-log)의 클라이언트라
     * Firebase 인증이 그대로 수용한다. google-services.json 의 client_type=3 항목.
     */
    private const val WEB_CLIENT_ID =
        "711708512022-08ktppivtd4940bggspjtqglkrlie1e4.apps.googleusercontent.com"

    private const val TAG = "GoogleCM"

    data class Tokens(val idToken: String, val email: String?, val name: String?, val picture: String?)

    sealed interface Outcome {
        data class Success(val tokens: Tokens) : Outcome

        /** 사용자가 시트를 닫음 → **폴백 금지**(그대로 로그인 취소). */
        data object Cancelled : Outcome

        /** GMS 부재·SHA 미등록·계정 없음 등 → 웹 OAuth 로 폴백. */
        data object Unavailable : Outcome
    }

    suspend fun signIn(activity: Activity): Outcome {
        val cm = CredentialManager.create(activity)

        // 1) 이미 이 앱에 인증된 계정만 (조용한 재로그인에 가까운 경험)
        val authorized = googleIdRequest(filterByAuthorized = true)
        when (val r = attempt(cm, activity, authorized)) {
            is Outcome.Success, Outcome.Cancelled -> return r
            Outcome.Unavailable -> Unit // 아래로
        }

        // 2) 인증 이력이 없으면 기기의 모든 구글 계정을 보여준다(최초 로그인).
        val anyAccount = googleIdRequest(filterByAuthorized = false)
        when (val r = attempt(cm, activity, anyAccount)) {
            is Outcome.Success, Outcome.Cancelled -> return r
            Outcome.Unavailable -> Unit
        }

        // 3) 시트가 안 뜨는 상황(계정 없음 등)에서의 마지막 CM 경로 — "Google로 로그인" 버튼 플로우.
        val button = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build())
            .build()
        return attempt(cm, activity, button)
    }

    /** 로그아웃 시 호출 — 다음 로그인에서 계정 선택 화면이 다시 뜨게 한다. */
    suspend fun clearState(activity: Activity) {
        runCatching {
            CredentialManager.create(activity)
                .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        }.onFailure { Log.w(TAG, "clearCredentialState 실패(무시)", it) }
    }

    private fun googleIdRequest(filterByAuthorized: Boolean): GetCredentialRequest =
        GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(filterByAuthorized)
                    // 인증 이력이 있는 계정이 하나뿐이면 탭 없이 자동 선택.
                    .setAutoSelectEnabled(filterByAuthorized)
                    // nonce 는 설정하지 않는다 — Firebase 는 Google id_token 의 nonce 를 검증하지
                    // 않으므로 얻는 게 없고, 실패 지점만 하나 늘어난다.
                    .build(),
            )
            .build()

    private suspend fun attempt(
        cm: CredentialManager,
        activity: Activity,
        request: GetCredentialRequest,
    ): Outcome = try {
        val response = cm.getCredential(activity, request)
        val cred = response.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            Outcome.Success(
                Tokens(
                    idToken = g.idToken,
                    email = g.id, // GoogleIdTokenCredential.id = 계정 이메일
                    name = g.displayName,
                    picture = g.profilePictureUri?.toString(),
                ),
            )
        } else {
            Log.w(TAG, "예상치 못한 자격증명 타입: ${cred.type}")
            Outcome.Unavailable
        }
    } catch (e: GetCredentialCancellationException) {
        Log.i(TAG, "사용자가 계정 시트를 닫음 — 폴백하지 않음")
        Outcome.Cancelled
    } catch (e: NoCredentialException) {
        Log.i(TAG, "사용 가능한 자격증명 없음 → 다음 경로")
        Outcome.Unavailable
    } catch (e: GetCredentialException) {
        // GMS 부재·SHA-1 미등록·기기 계정 문제 등이 여기로 온다 → 웹 OAuth 폴백.
        Log.w(TAG, "Credential Manager 실패(${e::class.simpleName}) → 웹 OAuth 폴백: ${e.message}")
        Outcome.Unavailable
    }
}
