package com.gatcha.log.data.api

import com.gatcha.log.data.firebaseAppExists
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

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
        // 상한을 둔다 — 오프라인이면 Firestore 가 캐시 확인 뒤 한참 매달려, 정본(GitHub raw)으로 내려가는
        // 것까지 그만큼 늦었다. 라이브 문서는 없으면 정본을 쓰면 되는 보조 경로다.
        return kotlinx.coroutines.withTimeoutOrNull(4_000) {
            runCatching {
                val snap = Firebase.firestore.collection(COLLECTION).document(doc).get()
                if (snap.exists) snap.get<String?>(FIELD_DATA) else null
            }.getOrNull()
        }?.takeIf { it.isNotBlank() }
    }

    /** 라이브가 이 시간 안에 답하면 정본은 아예 받지 않는다 — 평소의 길(라이브 응답은 보통 0.1~0.3초다). */
    private const val HEDGE_AFTER_MS = 700L

    /** 정본이 먼저 와도 라이브를 여기까지는 기다린다 — 「라이브가 정본을 이긴다」는 규칙을 지키는 선. */
    private const val LIVE_WINS_WITHIN_MS = 1_500L

    /**
     * 라이브 문서 → 정본(GitHub raw) 순으로 받되, **라이브가 늦으면 기다리지 않고 정본을 같이 받는다**(27.51.1).
     *
     * 예전엔 호출부마다 `LiveConfig.get(doc) ?: Net.get(raw)` 로 줄을 세웠다. 라이브 읽기가 늦는 날(약한 망 · Firestore 첫 연결)에는
     * 상한 4초를 다 쓰고 나서야 정본 요청이 나가, 설정 하나가 4초 + 정본 왕복만큼 늦었다.
     *
     *  - 라이브가 [HEDGE_AFTER_MS] 안에 오면 → 라이브. 정본 요청은 나가지 않는다.
     *  - 라이브가 값 없이 끝나면(문서 없음 · [valid] 탈락) → 정본.
     *  - 라이브가 늦으면 → 정본을 같이 받는다. 라이브가 [LIVE_WINS_WITHIN_MS] 안에 오면 라이브, 아니면 먼저 온 쪽.
     *
     * 정본은 어드민이 라이브에 반영할 때 같이 커밋하는 값이라, 드물게 정본이 이겨도 내용은 같거나 한 판 전이다.
     * 다음 조회(캐시 15초)에서 라이브가 제때 오면 그 값으로 돌아온다.
     *
     * @param valid 쓸 수 있는 본문인가 — 깨진 JSON · 빈 목록처럼 「다음 단계로 내려가야 하는」 값을 거른다.
     */
    suspend fun getOrRaw(doc: String, rawUrl: String, valid: (String) -> Boolean): String? = coroutineScope {
        val startedAt = currentTimeMillis()
        suspend fun raw(): String? =
            Net.get("$rawUrl?t=${currentTimeMillis()}").takeIf { it.isOk }?.body?.takeIf(valid)

        val live = async { get(doc)?.takeIf(valid) }
        withTimeoutOrNull(HEDGE_AFTER_MS) { live.await() }?.let { return@coroutineScope it }
        if (live.isCompleted) return@coroutineScope raw()   // 라이브가 금방 끝났는데 쓸 값이 없다 — 정본으로

        val canon = async { raw() }
        // 먼저 끝난 쪽을 본다(true = 라이브).
        val liveFirst = select<Boolean> { live.onAwait { true }; canon.onAwait { false } }
        if (liveFirst) {
            live.await()?.let { canon.cancel(); return@coroutineScope it }
            return@coroutineScope canon.await()
        }
        // 정본이 먼저 왔다 — 라이브에 남은 시간을 준다.
        val left = LIVE_WINS_WITHIN_MS - (currentTimeMillis() - startedAt)
        val late = if (left > 0) withTimeoutOrNull(left) { live.await() } else null
        if (late != null) return@coroutineScope late
        val fromCanon = canon.await()
        if (fromCanon != null) { live.cancel(); return@coroutineScope fromCanon }
        live.await()   // 정본도 못 받았다 — 라이브를 끝까지 기다린다
    }
}
