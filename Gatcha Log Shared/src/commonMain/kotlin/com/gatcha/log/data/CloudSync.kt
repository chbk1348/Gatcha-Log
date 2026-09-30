package com.gatcha.log.data

import kotlinx.coroutines.CancellationException
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
     * Firestore users/{uid} 문서 구조 — `data` 한 덩어리 + 갱신 시각.
     *
     * 예전엔 콘솔 가독성·향후 부분 동기화를 위해 `userInfo`/`spending`/`gameInfo` 섹션 맵을
     * 같이 썼다(dual-write). **읽는 쪽이 끝내 생기지 않았고**([pull]·[pullOutcome] 둘 다 `data` 만
     * 꺼낸다), 세 섹션의 키를 합치면 `data` 의 키 전부와 같아 문서가 **정확히 두 배**였다.
     * 1MiB 한도의 절반을 아무도 읽지 않는 사본이 쓰고 있었다 — 뽑기 약 3,800건에서 백업이 멈췄다.
     *
     * `set` 은 문서를 통째로 교체하므로 기존 문서의 섹션 필드도 다음 push 에서 사라진다(마이그레이션 불필요).
     */
    @Serializable
    private data class SnapshotDoc(
        val data: String,
        val updatedAt: Long,
        /**
         * 쓸 때마다 1씩 오르는 판 번호([pushMerged]). 지금은 기록만 한다 — 옛 버전이 set 하면 필드째 사라져
         * 0 부터 다시 센다. 옛 버전을 강제 업데이트로 걷어낸 뒤 보안 규칙에서 역행을 막는 데 쓴다.
         */
        val rev: Long = 0,
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
        ErrorBus.report(ErrorBus.Kind.API, "Google 로그인", it.message ?: it::class.simpleName.orEmpty())
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
        // pull 실패는 push 와 달리 데이터를 잃지 않는다(로컬 유지) → 모달이 아니라 토스트.
        ErrorBus.report(ErrorBus.Kind.SERVER, "클라우드", "불러오기 실패")
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
        if (it is CancellationException) throw it   // 취소는 실패가 아니다 — 오류 토스트를 띄우지 않는다
        println("GatchaCloudSync: pullOutcome 실패 — ${it::class.simpleName}: ${it.message}")
        ErrorBus.report(ErrorBus.Kind.SERVER, "클라우드", "불러오기 실패")
        PullOutcome.Failed
    }

    /**
     * **원격을 읽고 합친 결과를** 트랜잭션으로 쓴다. 성공하면 원격에 실제로 남은 스냅샷(=병합 결과)을,
     * 실패하면 null 을 돌려준다.
     *
     * 예전 push 는 원격을 보지 않고 이 기기 스냅샷으로 문서를 통째로 덮어써서, 다른 기기가 방금 올린
     * 예산 · 가챠가 사라졌다. 트랜잭션은 읽은 뒤 누가 먼저 쓰면 [merge] 를 다시 돌린다(최대 5회) —
     * 그래서 [merge] 는 부작용이 없어야 한다. 오프라인이면 실패한다(오프라인 큐에 옛 set 이 쌓였다가
     * 나중에 반영되는 일도 함께 사라진다).
     *
     * 병합 결과가 원격과 같으면 쓰지 않는다(읽기 1회로 끝).
     */
    suspend fun pushMerged(uid: String, merge: (remote: String?) -> String): String? = runCatching {
        val ref = Firebase.firestore.collection(COLLECTION).document(uid)
        // 블록 안 예외는 **블록 안에서 잡아 값으로** 내보낸다(9/30 iOS 크래시). GitLive 의 iOS 트랜잭션은 네이티브
        // 콜백 안에서 runBlocking 으로 블록을 돌려, 거기서 던진 예외가 바깥 runCatching 까지 못 오고 앱을 죽였다
        // (SIGABRT · terminateWithUnhandledException). Android 는 같은 예외를 트랜잭션 실패로 넘겨 멀쩡했다.
        // 잡으면 아무것도 쓰지 않은 채 끝나 네이티브 트랜잭션은 빈 커밋 — 실패는 아래 getOrThrow 가 바깥으로 넘긴다.
        Firebase.firestore.runTransaction {
            runCatching {
                val snap = get(ref)
                val remote = if (snap.exists) snap.get<String?>(FIELD_DATA) else null
                val rev = if (snap.exists) runCatching { snap.get<Long?>("rev") }.getOrNull() ?: 0L else 0L
                val merged = merge(remote)
                if (merged != remote) set(ref, SnapshotDoc(data = merged, updatedAt = currentTimeMillis(), rev = rev + 1))
                merged
            }
        }.getOrThrow()
    }.getOrElse {
        if (it is CancellationException) throw it
        println("GatchaCloudSync: pushMerged 실패 — ${it::class.simpleName}: ${it.message}")
        null
    }
}

/**
 * FirebaseApp 초기화 여부. 플랫폼별 컨텍스트 차이 때문에 expect/actual.
 * - Android: GitLive `Firebase.apps(null)` 은 null 컨텍스트 캐스트 실패로 false → 실제 Application Context 필요.
 * - iOS: 컨텍스트 불필요(`Firebase.apps(null)`).
 */
internal expect fun firebaseAppExists(): Boolean
