package com.gatcha.log.data

/**
 * 클라우드 동기화(Firestore) — :app 의 CloudSync 와 동일한 API 표면.
 *
 * 4단계 현재: 미설정(로컬 모드) 스텁. :app 도 google-services.json 이 없으면 동일하게 동작한다.
 * 5단계에서 GitLive firebase-kotlin-sdk 로 실제 구현 예정.
 */
object CloudSync {

    /** Firebase 가 설정되어 있는지. 스텁에서는 항상 false (로컬 모드). */
    fun isConfigured(): Boolean = false

    fun currentUid(): String? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun signInWithGoogle(idToken: String): String? = null

    fun signOut() {}

    @Suppress("UNUSED_PARAMETER")
    suspend fun pull(uid: String): String? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun push(uid: String, json: String): Boolean = false
}
