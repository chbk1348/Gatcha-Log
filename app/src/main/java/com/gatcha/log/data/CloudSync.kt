package com.gatcha.log.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Firebase 기반 "구글 계정 귀속" 클라우드 저장(Firestore) + 인증.
 *
 * `google-services.json` 이 추가되어 FirebaseApp 이 초기화된 경우에만 동작한다.
 * 미설정 시 [isConfigured] = false → 모든 메서드가 안전하게 no-op 이며 앱은 로컬로 동작한다.
 *
 * 저장 구조: Firestore `users/{uid}` 문서에 전체 스냅샷 JSON 한 덩어리(`data`) + 갱신 시각(`updatedAt`).
 * (Firestore 규칙 권장: `allow read, write: if request.auth != null && request.auth.uid == uid`)
 */
object CloudSync {

    private const val TAG = "CloudSync"
    private const val COLLECTION = "users"
    private const val FIELD_DATA = "data"
    private const val FIELD_UPDATED = "updatedAt"

    /** FirebaseApp 이 초기화되었는가(= google-services.json 적용됨). */
    fun isConfigured(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    /** 현재 Firebase 로그인 uid (없으면 null). */
    fun currentUid(): String? = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()

    /** Google ID 토큰으로 Firebase 인증 → uid 반환(실패 시 null). */
    suspend fun signInWithGoogle(idToken: String): String? = runCatching {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(cred).await().user?.uid
    }.onFailure { Log.e(TAG, "firebase sign-in failed", it) }.getOrNull()

    fun signOut() {
        runCatching { FirebaseAuth.getInstance().signOut() }
    }

    /** uid 문서의 스냅샷 JSON 로드(없거나 실패 시 null). */
    suspend fun pull(uid: String): String? = runCatching {
        FirebaseFirestore.getInstance().collection(COLLECTION).document(uid).get().await()
            .getString(FIELD_DATA)
    }.onFailure { Log.e(TAG, "pull failed", it) }.getOrNull()

    /** uid 문서에 스냅샷 JSON 저장. */
    suspend fun push(uid: String, json: String): Boolean = runCatching {
        val payload = mapOf(FIELD_DATA to json, FIELD_UPDATED to System.currentTimeMillis())
        FirebaseFirestore.getInstance().collection(COLLECTION).document(uid).set(payload).await()
        true
    }.onFailure { Log.e(TAG, "push failed", it) }.getOrDefault(false)
}
