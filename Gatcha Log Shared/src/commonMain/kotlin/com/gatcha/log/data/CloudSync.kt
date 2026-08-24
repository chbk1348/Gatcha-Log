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
 *  - Android(shared): google-services 미적용 → [isConfigured] = false → 로컬 모드 (:app 과 동일한 폴백)
 *
 * 주의: `data` 는 **평문 JSON** 으로 저장한다 (압축 시 구버전 호환 깨짐 — :app 주석 참고).
 */
object CloudSync {

    private const val COLLECTION = "users"
    private const val FIELD_DATA = "data"

    /**
     * Firestore users/{uid} 문서 구조.
     * - `data`: 전체 스냅샷 JSON(평문) — **읽기의 단일 소스**(구버전 호환, dual-write).
     * - `userInfo`/`spending`/`gameInfo`: 콘솔 가독성·향후 부분 동기화를 위한 섹션 분리 맵(키→JSON문자열).
     */
    @Serializable
    private data class SnapshotDoc(
        val data: String,
        val userInfo: Map<String, String> = emptyMap(),
        val spending: Map<String, String> = emptyMap(),
        val gameInfo: Map<String, String> = emptyMap(),
        val updatedAt: Long,
    )

    /**
     * FirebaseApp 이 초기화되었는가 (iOS: FirebaseApp.configure() 호출됨 / Android shared: 항상 false).
     *
     * `Firebase.auth` 접근으로 판정하지 않는 이유: iOS 에서 FirebaseApp 미구성 상태로 FIRAuth.auth() 를
     * 호출하면 ObjC NSException 이 발생하는데, 이는 Kotlin 의 runCatching 으로 잡히지 않고 프로세스가
     * 종료될 수 있다. FIRApp.allApps 조회는 예외 없이 빈 목록을 돌려준다.
     *
     * 플랫폼별 actual: Android 는 GitLive `Firebase.apps(null)` 의 null 컨텍스트 캐스트 실패로 항상 false 가
     * 되므로 실제 Application Context 를 넘겨야 한다(이게 없으면 Firebase 가 떠 있어도 로컬 모드로 오판). [firebaseAppExists]
     */
    fun isConfigured(): Boolean = runCatching { firebaseAppExists() }.getOrDefault(false)

    /** 현재 Firebase 로그인 uid (없으면 null). */
    fun currentUid(): String? = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

    /**
     * Google ID 토큰으로 Firebase 인증 → uid 반환(실패 시 null).
     * [accessToken]: iOS Firebase SDK 는 필수, Android 는 null 허용 — 항상 넘기는 것이 안전.
     */
    suspend fun signInWithGoogle(idToken: String, accessToken: String? = null): String? = runCatching {
        // 웹 OAuth(Android)는 accessToken 이 빈 문자열 → idToken 만으로 인증해야 하므로 빈 값은 null 로.
        val cred = GoogleAuthProvider.credential(idToken, accessToken?.ifBlank { null })
        Firebase.auth.signInWithCredential(cred).user?.uid
    }.onFailure {
        // 진단용 — Xcode 콘솔/시스템 로그에서 "GatchaCloudSync" 로 검색
        println("GatchaCloudSync: Firebase 인증 실패 — ${it::class.simpleName}: ${it.message}")
    }.getOrNull()

    /** Firebase 로그아웃. 실패해도 로컬 로그아웃은 진행되도록 예외를 삼킨다. */
    suspend fun signOut() {
        runCatching { Firebase.auth.signOut() }
    }

    /** uid 문서의 스냅샷 JSON(평문) 로드(없거나 실패 시 null). */
    suspend fun pull(uid: String): String? = runCatching {
        Firebase.firestore.collection(COLLECTION).document(uid).get().get<String?>(FIELD_DATA)
    }.onFailure {
        println("GatchaCloudSync: pull 실패 — ${it::class.simpleName}: ${it.message}")
    }.getOrNull()

    /**
     * pull 결과 — **성공(문서 없음 포함)과 실패(네트워크/에러)를 구분**한다.
     * 방어 목적: 네트워크 오류로 pull 이 실패했을 때 그것을 "빈 클라우드"로 오인해 로컬을 push 하면
     * 멀쩡한 클라우드 데이터를 빈 값으로 덮어쓰는 사고가 난다. [Loaded]=진짜 상태(신규 유저면 json=null),
     * [Failed]=불확실 → **호출부는 절대 push 하지 말 것**.
     */
    sealed class PullOutcome {
        /** 문서를 확실히 읽음. json=null 이면 문서/필드 부재(신규 유저) → seed 가능. */
        data class Loaded(val json: String?) : PullOutcome()
        /** 네트워크/에러로 읽지 못함 → 상태 불명. push 금지. */
        object Failed : PullOutcome()
    }

    /** [pull] 의 실패-구분 버전. 문서 부재(exists=false)는 Loaded(null), 예외는 Failed. */
    suspend fun pullOutcome(uid: String): PullOutcome = runCatching {
        val snap = Firebase.firestore.collection(COLLECTION).document(uid).get()
        if (!snap.exists) PullOutcome.Loaded(null)
        else PullOutcome.Loaded(snap.get<String?>(FIELD_DATA))
    }.getOrElse {
        println("GatchaCloudSync: pullOutcome 실패 — ${it::class.simpleName}: ${it.message}")
        PullOutcome.Failed
    }

    /**
     * uid 문서에 스냅샷 JSON(평문) 저장. 실패 시 false 반환(set 미적용 → 기존 문서 보존, 손상 없음).
     * 1MB 초과 등으로 실패해도 클라우드 데이터를 비우지 않는다.
     */
    suspend fun push(
        uid: String,
        json: String,
        userInfo: Map<String, String> = emptyMap(),
        spending: Map<String, String> = emptyMap(),
        gameInfo: Map<String, String> = emptyMap(),
    ): Boolean = runCatching {
        Firebase.firestore.collection(COLLECTION).document(uid)
            .set(SnapshotDoc(data = json, userInfo = userInfo, spending = spending, gameInfo = gameInfo, updatedAt = currentTimeMillis()))
        true
    }.getOrDefault(false)
}

/**
 * FirebaseApp 초기화 여부. 플랫폼별 컨텍스트 차이 때문에 expect/actual.
 * - Android: GitLive `Firebase.apps(null)` 은 null 컨텍스트 캐스트 실패로 false → 실제 Application Context 필요.
 * - iOS: 컨텍스트 불필요(`Firebase.apps(null)`).
 */
internal expect fun firebaseAppExists(): Boolean
