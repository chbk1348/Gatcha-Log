package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.serialization.Serializable

/**
 * Firebase 기반 "구글 계정 귀속" 클라우드 저장(Firestore) + 인증 — GitLive firebase-kotlin-sdk (KMP).
 *
 * :app 의 CloudSync 와 동일한 저장 구조를 사용하므로 Android ↔ iOS 간 클라우드 데이터가 호환된다:
 *   Firestore `users/{uid}` 문서에 전체 스냅샷 JSON 한 덩어리(`data`, **평문**) + 갱신 시각(`updatedAt`).
 *
 * 동작 조건:
 *  - iOS: 네이티브 Firebase iOS SDK 가 앱에 링크되고 FirebaseApp.configure() 가 호출된 경우
 *  - Android(composeApp): google-services 미적용 → [isConfigured] = false → 로컬 모드 (:app 과 동일한 폴백)
 *
 * 주의: `data` 는 **평문 JSON** 으로 저장한다 (압축 시 구버전 호환 깨짐 — :app 주석 참고).
 */
object CloudSync {

    private const val COLLECTION = "users"
    private const val FIELD_DATA = "data"

    /** Firestore users/{uid} 문서 구조 — 필드명은 :app 과 동일 (data / updatedAt) */
    @Serializable
    private data class SnapshotDoc(val data: String, val updatedAt: Long)

    /** FirebaseApp 이 초기화되었는가 (iOS: FirebaseApp.configure() 호출됨 / Android composeApp: 항상 false). */
    fun isConfigured(): Boolean = runCatching { Firebase.auth; true }.getOrDefault(false)

    /** 현재 Firebase 로그인 uid (없으면 null). */
    fun currentUid(): String? = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

    /** Google ID 토큰으로 Firebase 인증 → uid 반환(실패 시 null). */
    suspend fun signInWithGoogle(idToken: String): String? = runCatching {
        val cred = GoogleAuthProvider.credential(idToken, null)
        Firebase.auth.signInWithCredential(cred).user?.uid
    }.getOrNull()

    /** Firebase 로그아웃. 실패해도 로컬 로그아웃은 진행되도록 예외를 삼킨다. */
    suspend fun signOut() {
        runCatching { Firebase.auth.signOut() }
    }

    /** uid 문서의 스냅샷 JSON(평문) 로드(없거나 실패 시 null). */
    suspend fun pull(uid: String): String? = runCatching {
        Firebase.firestore.collection(COLLECTION).document(uid).get().get<String?>(FIELD_DATA)
    }.getOrNull()

    /**
     * uid 문서에 스냅샷 JSON(평문) 저장. 실패 시 false 반환(set 미적용 → 기존 문서 보존, 손상 없음).
     * 1MB 초과 등으로 실패해도 클라우드 데이터를 비우지 않는다.
     */
    suspend fun push(uid: String, json: String): Boolean = runCatching {
        Firebase.firestore.collection(COLLECTION).document(uid)
            .set(SnapshotDoc(data = json, updatedAt = currentTimeMillis()))
        true
    }.getOrDefault(false)
}
