package com.gatcha.log.data

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 로그인 계정. isGuest=true 면 비로그인(로컬 전용). */
data class Account(
    val id: String,
    val name: String,
    val email: String,
    val photoUrl: String,
    val isGuest: Boolean,
) {
    companion object {
        val GUEST = Account(id = "guest", name = "게스트", email = "", photoUrl = "", isGuest = true)
    }
}

/** 로그인 시도 결과. */
sealed interface SignInOutcome {
    data class Success(val account: Account) : SignInOutcome
    /** 자동선택 가능한(인가된) 구글 계정이 없음 — 무탭 시도 실패, 사용자 선택 UI 필요. */
    data object NoCredential : SignInOutcome
    data class Error(val message: String) : SignInOutcome
}

/**
 * 구글 로그인 + 계정 상태 영속화 — AndroidX **Credential Manager**(원탭/자동선택) 기반.
 *
 * 구식 GoogleSignIn 을 대체한다. google-services.json 의 web client id 를 serverClientId 로 사용해
 * Firebase 인증용 Google ID 토큰을 받는다. 바텀시트 UI 표시를 위해 [signIn] 은 **Activity 컨텍스트**가 필요하다.
 *
 * 자동선택([signIn] autoSelectOnly=true)은 인가 계정을 구글 서버 기준으로 조회하므로,
 * 레거시 silentSignIn 과 달리 재설치 후에도 무탭 복귀가 가능할 수 있다(가능 여부는 계정/기기 정책에 따름).
 */
class AuthManager(context: Context) {

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
     * Credential Manager 로 구글 로그인.
     * @param activityContext 바텀시트 UI 표시용 Activity 컨텍스트.
     * @param autoSelectOnly true → 이미 인가된 계정만 + 자동선택(가능하면 무탭). 없으면 [SignInOutcome.NoCredential].
     *                       false → 기기의 모든 구글 계정을 보여주고 원탭 선택.
     */
    suspend fun signIn(activityContext: Context, autoSelectOnly: Boolean): SignInOutcome {
        val serverId = webClientId() ?: return SignInOutcome.Error("Firebase가 설정되지 않았어요")
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverId)
            .setFilterByAuthorizedAccounts(autoSelectOnly)
            .setAutoSelectEnabled(autoSelectOnly)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = credentialManager.getCredential(activityContext, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val gid = GoogleIdTokenCredential.createFrom(cred.data)
                lastIdToken = gid.idToken
                val account = Account(
                    id = gid.id,
                    name = gid.displayName ?: gid.givenName ?: gid.id.substringBefore("@"),
                    email = gid.id,
                    photoUrl = gid.profilePictureUri?.toString() ?: "",
                    isGuest = false,
                )
                _account.value = account
                persist(account)
                SignInOutcome.Success(account)
            } else {
                SignInOutcome.Error("지원하지 않는 로그인 응답이에요")
            }
        } catch (e: NoCredentialException) {
            // 자동선택: 인가 계정 없음 / 수동: 보여줄 계정 없음
            SignInOutcome.NoCredential
        } catch (e: GetCredentialCancellationException) {
            SignInOutcome.Error("로그인이 취소되었어요")
        } catch (e: GetCredentialException) {
            Log.e("GatchaAuth", "credential sign-in failed", e)
            SignInOutcome.Error("로그인에 실패했어요")
        }
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
