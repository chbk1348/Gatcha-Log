package com.gatcha.log.data.vm

import com.gatcha.log.data.CloudSync
import com.gatcha.log.data.ErrorBus
import com.gatcha.log.data.GatchaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * **한 계정의 클라우드 동기화** — 올리기 예약 · 밀어내기 · 「받은 것 합치기 → 올리기」. 뷰모델에서 떼어 낸 덩어리
 * ([FeedbackController] 머리말 참고, 27.52.0).
 *
 * **계정마다 새로 만든다.** 예전엔 올리기 상태가 뷰모델의 필드라 계정을 바꿀 때 하나씩 되돌려야 했고, 실제로 하나만
 * 되돌렸다 — 앞 계정에서 쌓인 실패 횟수 · 「이미 알림」 표시가 새 계정으로 넘어와, 새 계정의 첫 실패가 바로 얼럿이 되거나
 * 반대로 알려야 할 실패가 묻혔다. 이제 계정이 바뀌면 뷰모델이 앞의 것을 [cancel] 하고 새 저장소로 다시 만든다.
 *
 * 로딩 게이트 · 오프라인 안내 · 언제 받을지는 뷰모델에 남아 있다 — 여기는 **한 저장소와 그 문서 사이**만 맡는다.
 *
 * @param repo 이 계정의 저장소. 여기서 읽고 여기에만 쓴다 — 예전엔 올리는 도중 저장소를 따로따로 읽어, 그 사이 계정이
 *   바뀌면 서로 다른 계정의 것이 섞일 수 있었다.
 * @param syncUid 지금 클라우드에 닿아도 되는 uid — 게스트거나 설정이 없으면 null.
 * @param isCurrent 뷰모델이 아직 이 저장소를 쓰는가. 계정이 바뀌었으면 false — 그 뒤로는 아무것도 쓰지 않는다.
 * @param awaitPersist 대기 중인 지출 저장이 끝날 때까지 기다린다 — 늦게 끝난 옛 목록 저장이 병합 결과를 덮지 않게.
 * @param beforeReload 합친 직후 · 다시 읽기 전(메인) — 게스트의 호요랩 연동을 계정으로 옮기는 자리.
 * @param reloadSynced 스냅샷이 바꿀 수 있는 화면 상태를 저장소에서 다시 읽는다(메인).
 */
internal class CloudSyncController(
    private val scope: CoroutineScope,
    val repo: GatchaRepository,
    private val syncUid: () -> String?,
    private val isCurrent: () -> Boolean,
    private val awaitPersist: suspend () -> Unit,
    private val beforeReload: () -> Unit,
    private val reloadSynced: () -> Unit,
    private val emitStatus: (String) -> Unit,
) {
    private var syncJob: Job? = null

    /** 마지막으로 Firestore 에 성공적으로 push 한 스냅샷(중복 쓰기 생략용). */
    private var lastPushedSnapshot: String? = null
    /** 용량 경고를 이미 띄웠는가 — 디바운스 push 마다 같은 토스트가 반복되지 않도록(임계 아래로 내려가면 해제). */
    private var docSizeWarned = false
    /** push 실패를 이미 알렸는가 — 실패가 이어질 때 토스트가 쌓이지 않도록(성공하면 해제). */
    private var failureNotified = false
    /** 연달아 실패한 push 횟수(성공하면 0). 순간 끊김 한 번으로 얼럿을 띄우지 않으려고 센다. */
    private var failStreak = 0

    /** 아직 올라가지 않은 로컬 변경이 있는가 — 디바운스 대기 중이거나, 재시도가 다 끝나 「못 올린 변경」 표시만 남았거나. */
    val hasPendingLocal: Boolean get() = syncJob?.isActive == true || repo.hasUnsynced

    /** 데이터 변경 시 디바운스(1.5s) 후 Firestore 에 전체 스냅샷 푸시. */
    fun schedule() {
        // 게스트 데이터는 절대 올리지 않는다([syncUid]). 계정을 게스트로 바꾸면 게스트 저장소에도 변경 알림이 다시
        // 붙는데, 로그아웃이 타임아웃돼 Firebase uid 가 아직 살아있으면 게스트 스냅샷이 직전 계정 문서를 덮어쓸 수 있다.
        val uid = syncUid() ?: return
        // 올라갈 때까지 저장소에 표시해 둔다 — 재시도가 다 끝나 이 작업이 사라져도 「못 올린 변경」이 남는다.
        repo.markUnsynced()
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(1500)
            // 순간 끊김은 조용히 다시 올린다(9/30) — 9/29 부터 push 가 트랜잭션이라 오프라인 · 연결 전환 중이면
            // 바로 실패했고(예전 set 은 오프라인 큐에 쌓였다) 그때마다 백업 실패 얼럿이 떠 잦아졌다.
            // 얼럿은 [push] 가 연달아 PUSH_FAIL_ALERT_STREAK 번 실패했을 때만 띄운다.
            for (wait in PUSH_RETRY_DELAYS_MS) {
                if (wait > 0) delay(wait)
                if (push(uid)) break
            }
        }
    }

    /** 대기 중인 올리기를 끊는다 — 받기 전에(빈 스냅샷으로 덮지 않게), 그리고 계정이 바뀔 때. 「못 올린 변경」 표시는 남는다. */
    fun cancel() {
        syncJob?.cancel()
        syncJob = null
    }

    /** 원격 스냅샷을 받는다. 응답이 없으면([SYNC_TIMEOUT_MS]) null — 실패와 같이 다룬다(상태 불명이라 올리지 않는다). */
    suspend fun pull(uid: String): CloudSync.PullOutcome? =
        withTimeoutOrNull(SYNC_TIMEOUT_MS) { CloudSync.pullOutcome(uid) }

    /** [push] 를 [SYNC_TIMEOUT_MS] 안에서 — 오프라인 등으로 응답이 없을 때 호출부가 갇히지 않게. */
    suspend fun pushNow(uid: String): Boolean = withTimeoutOrNull(SYNC_TIMEOUT_MS) { push(uid) } == true

    /**
     * **받은 스냅샷을 합치고 → 화면을 다시 읽고 → 합친 결과를 올린다.** 시작 동기화와 당겨서 새로고침이 같이 쓴다
     * (27.52.0) — 예전엔 두 곳에 조금씩 다르게 복제돼 있었다(한쪽만 메인에서 돌았고, 한쪽만 쓰기 직전에 계정을 다시 봤다).
     *
     * 저장소를 읽고-합치고-쓰는 구간은 **메인에서** 돈다 — 지출 추가 · 수정 · 삭제가 메인에서 저장소를 쓰므로 한 줄로
     * 세워야 서로 끼어들지 않는다. 예전엔 IO 에서 돌아 그사이 추가한 지출이 병합 결과에서 빠지고 다시 읽기가 그 목록으로
     * 덮었다(2026-09-28 점검).
     * ponytail: 스냅샷 파싱까지 메인에서 돈다 — 지출이 수천 건이 되어 복귀가 버벅이면 파싱만 IO 로 떼고 쓰기만 메인에 둔다.
     *
     * @param json 받은 스냅샷. null 이면 원격에 문서가 없다 — 합칠 것은 없고 이 기기의 값을 올린다.
     * @param onMerged 합친 직후(올리기 전) 메인에서 한 번.
     * @return 합쳤으면 true. 그사이 계정이 바뀌었으면 아무것도 하지 않고 false.
     */
    suspend fun mergePulledAndPush(uid: String, json: String?, onMerged: () -> Unit = {}): Boolean {
        val merged = withContext(Dispatchers.Main) {
            awaitPersist()   // 늦게 끝난 옛 목록 저장이 병합 결과를 덮지 않게
            // 기다리는 사이에도 계정이 바뀔 수 있다 — **쓰기 직전에 다시 보고, 잡아 둔 저장소에만 쓴다.**
            if (!isCurrent() || syncUid() != uid) return@withContext false
            json?.let { repo.importSnapshotJson(it) }
            // 원격/계정에 호요랩 연동이 없고 게스트에 있으면 계정으로 승계(귀속 누락 복구)
            beforeReload()
            reloadSynced()
            onMerged()
            true
        }
        // 병합 결과를 다시 업로드 → 유실됐던 호요랩 토큰 등을 클라우드에 자가 복구
        if (merged) pushNow(uid)
        return merged
    }

    /**
     * 대기 중인 디바운스 push 를 **즉시** 밀어낸다 — 로그아웃처럼 인증이 끊기기 직전에 호출한다.
     *
     * [schedule] 은 1.5초 디바운스라, 마지막 편집 직후 로그아웃하면 아직 delay 중인 작업이 인증 해제와 함께
     * 무의미해져 **그 1.5초 안의 변경분이 유실**됐다. (2026-07-03 지출 유실 사고와 같은 계열의 구멍)
     *
     * 디바운스 대기는 취소하되 push 자체는 지금 수행한다 — job.join() 으로 기다리면 남은 delay 만큼
     * 로그아웃이 늦어지므로, 대기를 건너뛰고 곧바로 올리는 편이 빠르고 결과도 같다.
     *
     * @return 올릴 것이 없었거나 올라갔으면 true. false 면 못 올린 변경이 이 기기에 남아 있다.
     */
    suspend fun flushPending(): Boolean {
        // 대기 중인 작업이 없어도 **못 올린 변경 표시**가 있으면 올린다(27.52.0) — 재시도 세 번이 끝난 뒤에는 작업이 없어서,
        // 그대로 로그아웃하면 그 변경은 올라가지 않고 안내도 없었다.
        if (!hasPendingLocal) return true
        cancel()                           // 남은 delay 는 버리고
        val uid = syncUid() ?: return true
        return pushNow(uid)                // 지금 올린다
    }

    /**
     * UTF-8 인코딩 바이트 수.
     *
     * Firestore 문서 한도는 **바이트** 기준인데 [String.length] 는 UTF-16 단위라, 한글이 섞이면
     * 실제 크기를 과소평가한다(한글 1자 = 1 단위 = 3 바이트). `encodeToByteArray().size` 로도 잴 수
     * 있지만 스냅샷이 수백 KB 급이라 그 크기만큼을 push 마다 새로 할당하게 된다 — 세기만 한다.
     */
    private fun utf8Bytes(s: String): Int {
        var n = 0
        for (c in s) {
            val v = c.code
            n += when {
                v < 0x80 -> 1
                v < 0x800 -> 2
                v in 0xD800..0xDFFF -> 2   // 서로게이트 쌍(2문자)이 합쳐 4바이트
                else -> 3
            }
        }
        return n
    }

    /**
     * 전체 스냅샷을 Firestore 에 push 하는 단일 경로.
     *  - 중복 방지: 직전 성공 push 와 내용이 같으면 쓰기를 생략(테마 변경·재로딩 등 무변화 churn 절감).
     *  - 1MB 한도 경고: 문서 실제 크기(UTF-8 바이트)가 한도에 근접하면 미리 안내한다.
     *  - 실패 안내: set 이 실패하면 사용자에게 알린다 — 알리지 않으면 백업이 멈춘 줄 모른다.
     *  - 실패 시 lastPushedSnapshot 을 갱신하지 않아 다음 변경에서 재시도된다.
     */
    suspend fun push(uid: String): Boolean {
        // **이 작업 전체가 IO 로 간다.** 스냅샷 생성은 저장된 JSON 문자열을 전부 파싱하고
        // 결과를 다시 직렬화하는 일이라 수백 KB 급인데, 뷰모델 스코프가 Main.immediate 라
        // 지출을 저장할 때마다 1.5초 뒤 그게 UI 스레드에서 돌고 있었다.
        // (직렬화 형식 자체는 건드리지 않는다 — lastPushedSnapshot 비교와 Firestore 중복 쓰기
        //  생략이 바이트 동일성에 걸려 있다.)
        // 출석 원문은 스냅샷보다 **먼저** 뜬다. 그 사이에 출석이 바뀌면 둘이 어긋나 표시가 남는
        // 쪽으로 기운다(안전한 쪽) — 거꾸로 뜨면 올리지 않은 출석의 보호를 풀 수 있다.
        awaitPersist()   // 대기 중인 지출 저장이 끝난 뒤의 저장소를 올린다
        // 게스트이거나 세션의 주인이 바뀌었거나 뷰모델이 다른 계정으로 넘어갔으면 올리지 않는다.
        if (syncUid() != uid || !isCurrent()) return false
        val attendanceAtExport = repo.attendanceRaw()
        val unsyncedAtExport = repo.unsyncedMark()   // 스냅샷보다 먼저 — 올리는 사이의 변경은 표시가 남게
        val json = withContext(Dispatchers.IO) { repo.exportSnapshotJson() }
        if (json == lastPushedSnapshot) {
            // 올릴 것이 없다 = 원격이 이미 로컬과 같다 → 출석 보호 표시도 풀어 준다.
            repo.clearAttendanceDirtyIfUnchanged(attendanceAtExport)
            repo.clearUnsyncedIf(unsyncedAtExport)
            return true   // 변경 없음 → write 생략
        }
        // 문서의 **실제** 크기로 잰다 — 한도는 UTF-16 단위가 아니라 UTF-8 바이트 기준이다.
        // (섹션 dual-write 를 걷어내기 전에는 여기에 ×2 가 더 붙었다. 지금은 `data` 한 벌뿐이다.)
        val docBytes = utf8Bytes(json)
        if (docBytes > CLOUD_DOC_WARN_BYTES) {
            if (!docSizeWarned) {
                docSizeWarned = true
                emitStatus("클라우드 백업 용량이 한계에 근접했어요 (${docBytes / 1024}KB / ${CLOUD_DOC_LIMIT_BYTES / 1024}KB) — 오래된 뽑기 기록 정리를 권장해요")
            }
        } else docSizeWarned = false
        // 원격을 읽고 합쳐서 쓴다(트랜잭션) — 다른 기기가 방금 올린 변경을 덮지 않는다.
        val merged = withContext(Dispatchers.IO) {
            CloudSync.pushMerged(uid) { remote -> repo.mergeForPush(json, remote) }
        }
        val ok = merged != null
        if (merged != null) {
            if (merged != json) {
                // 원격에 이 기기가 모르던 변경이 있었다 — 합친 결과를 로컬에도 싣는다. 저장 큐가 끝난 뒤,
                // 여전히 같은 계정일 때만(병합 결과가 이 기기의 최신 수정을 되돌리지는 않는다 — 수정 시각 비교).
                withContext(Dispatchers.Main) {
                    awaitPersist()
                    if (isCurrent()) { repo.importSnapshotJson(merged); reloadSynced() }
                }
            }
            // 원격과 같다고 확신할 수 있을 때만 중복 생략 기준으로 삼는다. 합쳐졌으면 다음 push 가 한 번 더
            // 읽어 보고(대개 쓰기 없이) 끝난다.
            lastPushedSnapshot = if (merged == json) json else null
            failureNotified = false
            failStreak = 0
            // 올라갔으니 다음 pull 은 출석을 그대로 받아도 된다 — 단 푸시 도중 자동 출석이 새로
            // 쓰지 않았을 때만(→ [GatchaRepository.clearAttendanceDirtyIfUnchanged]).
            repo.clearAttendanceDirtyIfUnchanged(attendanceAtExport)
            repo.clearUnsyncedIf(unsyncedAtExport)
        } else if (++failStreak >= PUSH_FAIL_ALERT_STREAK || docBytes > CLOUD_DOC_LIMIT_BYTES) {
            // 용량 초과는 다시 해도 안 풀리니 바로, 그 밖은 연달아 실패했을 때만 알린다.
            if (failureNotified) return ok
            // 실패를 삼키면 로컬만 계속 쌓이고 클라우드는 멈춘 채로, 기기를 바꾸는 순간에야 발견된다.
            // 할 일이 원인마다 다르므로 용량 초과와 그 외를 나눠 안내한다.
            failureNotified = true
            // 토스트로는 지나쳐 버린다 — 백업이 멈춘 건 모르고 지나가면 기기 교체 때 데이터를 잃는다.
            ErrorBus.report(
                ErrorBus.Kind.CLOUD, "클라우드",
                if (docBytes > CLOUD_DOC_LIMIT_BYTES)
                    "백업이 용량 한도를 넘어 멈췄어요 (${docBytes / 1024}KB / ${CLOUD_DOC_LIMIT_BYTES / 1024}KB).\n오래된 뽑기 기록을 정리해주세요."
                else
                    "백업에 여러 번 실패했어요.\n연결을 확인해주세요 — 다음 변경 때 다시 시도해요.",
            )
        }
        return ok
    }

    companion object {
        /** 클라우드 pull/push 최대 대기(ms). 오프라인 등으로 응답 없을 때 로딩 화면 갇힘 방지. */
        const val SYNC_TIMEOUT_MS = 8_000L

        /** Firestore 문서 크기 한도(바이트). 이 값을 넘기면 set 이 실패한다. */
        private const val CLOUD_DOC_LIMIT_BYTES = 1_048_576
        /** push 재시도 간격 — 첫 시도(0) · 15초 · 1분. 순간 끊김은 이 안에서 조용히 풀린다. */
        private val PUSH_RETRY_DELAYS_MS = longArrayOf(0L, 15_000L, 60_000L)
        /** 연달아 이만큼 실패해야 백업 실패 얼럿(재시도 3회를 다 쓴 뒤). */
        private const val PUSH_FAIL_ALERT_STREAK = 3
        /** 한도 근접 경고 임계치(바이트, 한도의 약 86%). 초과 시 set 이 실패해 백업이 조용히 멈추므로 미리 안내. */
        private const val CLOUD_DOC_WARN_BYTES = 900_000
    }
}
