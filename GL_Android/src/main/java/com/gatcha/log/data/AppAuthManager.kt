package com.gatcha.log.data

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.gatcha.log.auth.GoogleWebOAuth
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Account / SignInOutcome 은 GL_Shared(commonMain)의 정본을 사용한다(동일 FQN 중복 정의 금지).

/**
 * 구글 로그인 + 계정 상태 영속화 — AndroidX **Credential Manager** 네이티브 원탭 기반(:app 전용).
 *
 * 브라우저 웹 OAuth(GoogleWebOAuth)를 대체한다. 계정 선택은 기기 네이티브 바텀시트로 뜨며
 * (요즘 앱들의 'Google로 로그인' 방식), google-services.json 의 web client id 를 serverClientId 로 써
 * Firebase 인증용 Google ID 토큰을 받는다. 바텀시트 표시를 위해 [signIn] 은 **Activity 컨텍스트**가 필요하다.
 *
 * Shared 의 [AuthManager] 와 역할은 같지만 클래스명을 분리한다 — 동일 FQN 이면 dex 병합 시 충돌해
 * 런타임 크래시(NoSuchMethodError)가 난다.
 */
class AppAuthManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("gatcha_auth", Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(appContext)

    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account> = _account.asStateFlow()

    /** 게스트로 시작하기를 명시적으로 선택했는지(로그인 화면 통과 여부). */
    private val _guestChosen = MutableStateFlow(prefs.getBoolean(KEY_GUEST, false))
    val guestChosen: StateFlow<Boolean> = _guestChosen.asStateFlow()

    fun continueAsGuest() {
        prefs.edit().putBoolean(KEY_GUEST, true).apply()
        _guestChosen.value = true
    }

    /** 계정 식별자를 확정해 영속(예: Firebase uid 로 교체). 로컬/클라우드 키를 일치시킨다. */
    fun setAccount(acc: Account) {
        _account.value = acc
        persist(acc)
    }

    /** 마지막 로그인의 Google ID 토큰(Firebase 인증용). Firebase 미설정 시 null. */
    var lastIdToken: String? = null
        private set

    /** google-services.json 이 적용되면 생성되는 OAuth Web client id (없으면 null → Firebase 미설정). */
    private fun webClientId(): String? {
        val resId = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (resId != 0) appContext.getString(resId) else null
    }

    /**
     * Credential Manager 로 구글 로그인(네이티브 바텀시트).
     * @param activityContext 바텀시트 UI 표시용 Activity 컨텍스트.
     * @param autoSelectOnly true → 이미 인가된 계정만 + 자동선택(가능하면 무탭). 없으면 [SignInOutcome.NoCredential].
     *                       false → 기기의 모든 구글 계정을 보여주고 원탭 선택. 기기에 구글 계정이 전혀 없으면
     *                       [SignInOutcome.NoCredential] (UI 에서 '계정 추가' 안내로 분기).
     */
    suspend fun signIn(activityContext: Context, autoSelectOnly: Boolean): SignInOutcome {
        val serverClientId = webClientId() ?: return SignInOutcome.Error("로그인 설정이 없어요")
        val option = if (autoSelectOnly) {
            // 무탭 복귀: 이미 인가된 계정만 자동선택(없으면 NoCredential → UI 로그인 버튼 노출).
            GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
        } else {
            // 사용자가 'Google 로그인'을 직접 탭 → 표준 'Sign in with Google' 플로우(기기의 모든 구글 계정 + 계정 추가 노출).
            // GetGoogleIdOption(filterByAuthorizedAccounts=false)는 기기에 계정이 있어도 NoCredential 을 던지는
            // 알려진 케이스가 있어, 명시적 로그인에는 GetSignInWithGoogleOption 을 쓴다.
            GetSignInWithGoogleOption.Builder(serverClientId).build()
        }
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val result = credentialManager.getCredential(activityContext, request)
            handleCredential(result.credential)
        } catch (e: NoCredentialException) {
            // OS 시스템 계정에 구글 계정이 없음. autoSelectOnly(무탭 복귀)면 그대로 NoCredential,
            // 사용자가 직접 로그인을 탭한 경우엔 웹 OAuth(브라우저)로 폴백 → 시스템 계정 없는 기기도 로그인 가능.
            if (autoSelectOnly) SignInOutcome.NoCredential else webOAuthFallback(activityContext)
        } catch (e: GetCredentialCancellationException) {
            SignInOutcome.Error("로그인이 취소되었어요")
        } catch (e: GetCredentialException) {
            Log.e("GatchaAuth", "credential sign-in failed", e)
            if (autoSelectOnly) SignInOutcome.Error("로그인에 실패했어요") else webOAuthFallback(activityContext)
        }
    }

    /** Credential Manager 에서 시스템 구글 계정을 못 찾을 때 — 브라우저 웹 OAuth 로 로그인(누구나 가능). */
    private suspend fun webOAuthFallback(activityContext: Context): SignInOutcome {
        return try {
            val tokens = GoogleWebOAuth.signIn(activityContext)
                ?: return SignInOutcome.Error("로그인이 취소되었어요")
            lastIdToken = tokens.idToken
            val email = tokens.email ?: ""
            val account = Account(
                id = email.ifEmpty { "google" },
                name = tokens.name ?: email.substringBefore("@").ifEmpty { "사용자" },
                email = email,
                photoUrl = tokens.picture ?: "",
                isGuest = false,
            )
            _account.value = account  // 영속은 completeSignIn(최종 uid)에서 — 중간 email 키 고착 방지
            SignInOutcome.Success(account)
        } catch (e: Exception) {
            Log.e("GatchaAuth", "web oauth fallback failed", e)
            SignInOutcome.Error("로그인에 실패했어요")
        }
    }

    private fun handleCredential(credential: Credential): SignInOutcome {
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = runCatching { GoogleIdTokenCredential.createFrom(credential.data) }
                .getOrElse { return SignInOutcome.Error("로그인 정보를 읽지 못했어요") }
            lastIdToken = google.idToken
            val email = google.id  // GoogleIdTokenCredential.id = 계정 이메일
            val account = Account(
                id = email.ifEmpty { "google" },                  // completeSignIn 에서 Firebase uid 로 교체됨
                name = google.displayName ?: email.substringBefore("@").ifEmpty { "사용자" },
                email = email,
                photoUrl = google.profilePictureUri?.toString() ?: "",
                isGuest = false,
            )
            // prefs 영속은 completeSignIn 의 setAccount(최종 id=Firebase uid) 에서 수행한다.
            // 여기서 email 키로 persist 하면, Firebase 인증 전 앱이 종료될 때 email 키가 고착돼
            // uid 저장소(실데이터)를 못 찾는 상태가 남는다.
            _account.value = account
            return SignInOutcome.Success(account)
        }
        return SignInOutcome.Error("지원하지 않는 로그인 방식이에요")
    }

    /** 로그아웃 → 게스트(로컬) 계정으로 전환. */
    suspend fun signOut() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        runCatching { CloudSync.signOut() }
        lastIdToken = null
        prefs.edit().clear().apply()
        _account.value = Account.GUEST
        _guestChosen.value = false // 로그아웃 시 다시 로그인 화면으로
    }

    private fun load(): Account {
        val id = prefs.getString(KEY_ID, null) ?: return Account.GUEST
        return Account(
            id = id,
            name = prefs.getString(KEY_NAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            photoUrl = prefs.getString(KEY_PHOTO, "") ?: "",
            isGuest = false,
        )
    }

    private fun persist(a: Account) {
        prefs.edit()
            .putString(KEY_ID, a.id)
            .putString(KEY_NAME, a.name)
            .putString(KEY_EMAIL, a.email)
            .putString(KEY_PHOTO, a.photoUrl)
            .apply()
    }

    private companion object {
        const val KEY_ID = "account_id"
        const val KEY_NAME = "account_name"
        const val KEY_EMAIL = "account_email"
        const val KEY_PHOTO = "account_photo"
        const val KEY_GUEST = "guest_chosen"
    }
}
