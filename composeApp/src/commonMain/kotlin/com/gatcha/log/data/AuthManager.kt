package com.gatcha.log.data

import com.gatcha.log.storage.KeyValueStore
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

/** 플랫폼 구글 로그인 결과 (idToken = Firebase 인증용) */
data class PlatformSignInResult(
    val idToken: String,
    val email: String,
    val name: String,
    val photoUrl: String,
)

/**
 * 플랫폼별 구글 로그인 (expect/actual).
 * - iOS: GoogleSignIn SDK (Swift 브리지 — IosGoogleSignIn.provider)
 * - Android(composeApp): 미구현(null) — Android 는 :app 의 Credential Manager 가 담당
 * 반환 null = 로그인 불가/취소.
 */
internal expect suspend fun platformGoogleSignIn(autoSelectOnly: Boolean): PlatformSignInResult?

/**
 * 계정 상태 영속화 — :app 의 AuthManager 를 KMP 로 이식.
 *
 * 4단계 현재: 게스트(로컬) 모드만 동작. 저장 키는 :app 과 동일("gatcha_auth").
 * 실제 플랫폼 로그인(Android Credential Manager / iOS Sign in with Apple·Google)은
 * 5단계에서 [platformSignIn] 을 expect/actual 로 연결한다.
 */
class AuthManager {

    private val prefs = KeyValueStore("gatcha_auth")

    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account> = _account.asStateFlow()

    /** 게스트로 시작하기를 명시적으로 선택했는지(로그인 화면 통과 여부). */
    private val _guestChosen = MutableStateFlow(prefs.getBoolean(KEY_GUEST, false))
    val guestChosen: StateFlow<Boolean> = _guestChosen.asStateFlow()

    fun continueAsGuest() {
        prefs.putBoolean(KEY_GUEST, true)
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

    /**
     * 구글 로그인 — 플랫폼 구현(platformGoogleSignIn)에 위임.
     * iOS: GoogleSignIn SDK / Android(composeApp): 미지원(:app 이 담당).
     */
    suspend fun signIn(autoSelectOnly: Boolean): SignInOutcome {
        val result = runCatching { platformGoogleSignIn(autoSelectOnly) }
            .getOrElse { return SignInOutcome.Error("로그인에 실패했어요") }
            ?: return SignInOutcome.NoCredential

        lastIdToken = result.idToken
        val account = Account(
            id = result.email.ifBlank { result.idToken.take(32) },
            name = result.name.ifBlank { result.email.substringBefore("@") },
            email = result.email,
            photoUrl = result.photoUrl,
            isGuest = false,
        )
        _account.value = account
        persist(account)
        return SignInOutcome.Success(account)
    }

    /** 로그아웃 → 게스트(로컬) 계정으로 전환. */
    suspend fun signOut() {
        runCatching { CloudSync.signOut() }
        lastIdToken = null
        listOf(KEY_ID, KEY_NAME, KEY_EMAIL, KEY_PHOTO, KEY_GUEST).forEach { prefs.remove(it) }
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
        prefs.putString(KEY_ID, a.id)
        prefs.putString(KEY_NAME, a.name)
        prefs.putString(KEY_EMAIL, a.email)
        prefs.putString(KEY_PHOTO, a.photoUrl)
    }

    private companion object {
        const val KEY_ID = "account_id"
        const val KEY_NAME = "account_name"
        const val KEY_EMAIL = "account_email"
        const val KEY_PHOTO = "account_photo"
        const val KEY_GUEST = "guest_chosen"
    }
}
