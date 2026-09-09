package com.gatcha.log.data.api

import com.gatcha.log.data.firebaseAppExists
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore

/**
 * 운영 어드민(`Gatcha Log Admin/`)이 쓰는 **라이브 설정 문서**.
 *
 * 원격 JSON 을 읽는 자리마다 같은 사슬을 쓴다:
 *
 *     LiveConfig.get(doc)  →  raw .json (git 정본)  →  번들 기본값
 *
 * 앞의 것은 커밋 없이 즉시 반영되는 현장 대응용이고, git 은 이력과 최종 폴백을 맡는다.
 * 문서 구조는 `users/{uid}` 와 같다 — JSON 한 덩어리를 `data` 문자열로 둔다. 그래서 호출부는
 * 기존 파서를 그대로 재사용하고, 새 직렬화 규칙이 생기지 않는다.
 *
 * **version.json 은 여기에 태우지 않는다.** `minVersionCode` 는 강제 업데이트를 거는 값이라
 * 오타 하나로 전 사용자를 존재하지 않는 버전으로 밀어 버릴 수 있다. 되돌리려면 또 한 번의
 * 원격 쓰기가 필요한데, 그 사이 앱은 이미 잠긴다. 이 경로만은 git 리뷰와 이력을 거치게 둔다.
 *
 * 규칙: `config/{doc}` 은 **읽기 공개 · 운영자 uid 만 쓰기**(firestore.rules).
 * 그러므로 이 문서에는 비공개 값을 넣지 않는다.
 */
internal object LiveConfig {

    private const val COLLECTION = "config"
    private const val FIELD_DATA = "data"

    /**
     * 라이브 문서의 JSON 문자열. 없거나 비었거나 못 읽으면 null → 호출부는 정본으로 내려간다.
     *
     * Firebase 가 초기화되지 않은 빌드(google-services.json 없는 로컬 모드)에서는 건너뛴다 —
     * 네트워크를 타기 전에 끊어야 로컬 모드 실행이 느려지지 않는다.
     */
    suspend fun get(doc: String): String? {
        if (!firebaseAppExists()) return null
        return runCatching {
            val snap = Firebase.firestore.collection(COLLECTION).document(doc).get()
            if (snap.exists) snap.get<String?>(FIELD_DATA) else null
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
