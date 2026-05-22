package com.gatcha.log.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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

/**
 * 구글 로그인 + 계정 상태 영속화.
 *
 * 백엔드가 없고 "계정별 로컬 데이터 분리"만 필요하므로, ID 토큰/서버 인증을 요구하지 않는
 * **기본 Google Sign-In(이메일·프로필)** 을 사용한다. 이 경우 Google Cloud 콘솔 OAuth
 * 클라이언트/동의 화면 설정이 전혀 필요 없다(28444 같은 콘솔 설정 오류가 발생하지 않음).
 *
 * 로그인 자체는 Activity Result(인텐트)로 진행되므로 [signInIntent] / [handleSignInResult] 사용.
 */
@Suppress("DEPRECATION") // GoogleSignIn 은 deprecated 이나 백엔드 없는 기본 로그인에는 콘솔 설정 없이 동작
class AuthManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("gatcha_auth", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account> = _account.asStateFlow()

    private fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()

    /** 로그인 인텐트 — UI에서 ActivityResultLauncher로 실행한다. */
    fun signInIntent(context: Context): Intent =
        GoogleSignIn.getClient(context, signInOptions()).signInIntent

    /** ActivityResult 처리 → 성공 시 [Account] 반환 + 상태 갱신. */
    fun handleSignInResult(data: Intent?): Result<Account> = try {
        val gsa = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        val account = Account(
            id = gsa.email ?: gsa.id ?: "google_user",
            name = gsa.displayName ?: gsa.givenName ?: (gsa.email?.substringBefore("@") ?: "사용자"),
            email = gsa.email ?: "",
            photoUrl = gsa.photoUrl?.toString() ?: "",
            isGuest = false,
        )
        _account.value = account
        persist(account)
        Result.success(account)
    } catch (e: ApiException) {
        Log.e("GatchaAuth", "Google sign-in failed: code=${e.statusCode}", e)
        Result.failure(e)
    } catch (e: Exception) {
        Log.e("GatchaAuth", "Google sign-in failed", e)
        Result.failure(e)
    }

    /** 로그아웃 → 게스트(로컬) 계정으로 전환. */
    fun signOut() {
        runCatching { GoogleSignIn.getClient(appContext, signInOptions()).signOut() }
        prefs.edit().clear().apply()
        _account.value = Account.GUEST
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
    }
}
