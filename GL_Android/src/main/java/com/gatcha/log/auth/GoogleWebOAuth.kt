package com.gatcha.log.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Google 로그인 — 브라우저 기반 웹 OAuth(PKCE). 기존 Credential Manager(기기 구글 계정 필수) 대체.
 *
 * iOS OAuth 클라이언트 ID + 리버스 스킴을 재사용한다(시크릿 불필요·PKCE, Google Console 변경 불필요).
 * 받은 id_token 은 Firebase(GoogleAuthProvider) 인증에 그대로 전달. 동의 화면이 production 이면 모든 유저 로그인 가능.
 */
object GoogleWebOAuth {
    // GoogleService-Info(iOS) 의 CLIENT_ID / REVERSED_CLIENT_ID — 공개값(시크릿 아님).
    private const val CLIENT_ID = "711708512022-6upq632okh78jru6q09o2dol4tekokbn.apps.googleusercontent.com"
    private const val SCHEME = "com.googleusercontent.apps.711708512022-6upq632okh78jru6q09o2dol4tekokbn"
    private const val REDIRECT_URI = "$SCHEME:/oauth2redirect"

    data class Tokens(val idToken: String, val email: String?, val name: String?, val picture: String?)

    private var pending: CompletableDeferred<Uri?>? = null
    private var verifier: String = ""

    /** 리다이렉트 액티비티가 호출 — 콜백 URI 전달. */
    fun onRedirect(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }

    /** 브라우저 웹 로그인 → 토큰. 취소/실패 시 null. */
    suspend fun signIn(context: Context): Tokens? {
        pending?.complete(null) // 잔여 시도 정리
        verifier = randomUrlSafe(64)
        val challenge = b64url(sha256(verifier.toByteArray(Charsets.US_ASCII)))
        val authUrl = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid email profile")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .build()

        val deferred = CompletableDeferred<Uri?>()
        pending = deferred
        runCatching {
            // 인앱 브라우저(Chrome Custom Tabs) — Activity 컨텍스트로 같은 태스크에 띄워 인앱 느낌.
            // Custom Tabs 미지원 브라우저면 launchUrl 이 일반 브라우저로 자동 폴백한다.
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, authUrl)
        }.onFailure { pending = null; return null }

        val callback = deferred.await() ?: return null
        val code = callback.getQueryParameter("code") ?: return null
        return withContext(Dispatchers.IO) { exchange(code) }
    }

    private fun exchange(code: String): Tokens? {
        val body = listOf(
            "client_id" to CLIENT_ID,
            "code" to code,
            "code_verifier" to verifier,
            "grant_type" to "authorization_code",
            "redirect_uri" to REDIRECT_URI,
        ).joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }

        val conn = (URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) {
                Log.e("GoogleWebOAuth", "token exchange HTTP ${conn.responseCode}")
                return null
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val idToken = json.optString("id_token").ifEmpty { return null }
            val claims = decodeJwt(idToken)
            Tokens(
                idToken = idToken,
                email = claims.optString("email").ifEmpty { null },
                name = claims.optString("name").ifEmpty { null },
                picture = claims.optString("picture").ifEmpty { null },
            )
        } catch (e: Exception) {
            Log.e("GoogleWebOAuth", "token exchange failed", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    private fun b64url(b: ByteArray): String = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun randomUrlSafe(n: Int): String = b64url(ByteArray(n).also { SecureRandom().nextBytes(it) })
    private fun decodeJwt(jwt: String): JSONObject {
        val parts = jwt.split(".")
        if (parts.size < 2) return JSONObject()
        return try {
            JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8))
        } catch (e: Exception) {
            JSONObject()
        }
    }
}
