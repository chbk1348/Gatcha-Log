package com.gatcha.log.data.vm

import com.gatcha.log.data.ErrorAlert
import com.gatcha.log.data.ErrorBus
import com.gatcha.log.data.StatusToast
import com.gatcha.log.data.ToastKind
import com.gatcha.log.data.api.Net
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 토스트와 오류 얼럿 — [com.gatcha.log.data.SpendingViewModel] 에서 떼어 낸 덩어리(27.51.1).
 *
 * 뷰모델이 3,600줄이 되도록 한 클래스에 모든 화면의 상태가 쌓였다. 화면과 iOS 브리지를 건드리지 않고
 * 줄이려고, 덩어리마다 이런 클래스로 옮기고 뷰모델은 **같은 이름의 상태 · 함수를 그대로 내보내며 넘겨주기만** 한다.
 * 그래서 `internal` 이다 — 화면이 직접 잡는 것은 여전히 뷰모델뿐이다.
 */
internal class FeedbackController(private val scope: CoroutineScope) {

    /** 일회성 토스트 메시지 (UI 가 소비 후 clearStatus 호출) */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** 토스트 한 건 — 상태 · 제목 · 설명([StatusToast]). 화면은 이것을 그린다(GLDS 2.1 토스트, 10/15). */
    private val _statusToast = MutableStateFlow<StatusToast?>(null)
    val statusToast: StateFlow<StatusToast?> = _statusToast.asStateFlow()
    private var toastSeq = 0L

    fun clearStatus() {
        _statusMessage.value = null
        _statusToast.value = null
    }

    fun status(msg: String, kind: ToastKind = ToastKind.INFO) {
        val (title, detail) = StatusToast.split(msg)
        _statusMessage.value = msg
        _statusToast.value = StatusToast(++toastSeq, title, detail, kind)
    }

    /**
     * 오류 얼럿 모달 — 토스트가 아니라 모달로 세우는 것들(null 이 아니면 노출). UI 가 확인 후 [clearErrorAlert].
     *
     * 예전엔 네트워크 미연결 전용이라 제목이 UI 에 "인터넷 연결 없음" 으로 박혀 있었다.
     * 인증 만료·클라우드 백업 실패도 같은 자리에 세워야 해서 제목까지 함께 든다([ErrorAlert]).
     */
    private val _errorAlert = MutableStateFlow<ErrorAlert?>(null)
    val errorAlert: StateFlow<ErrorAlert?> = _errorAlert.asStateFlow()
    fun clearErrorAlert() { _errorAlert.value = null }

    /** 이미 얼럿이 떠 있으면 덮어쓰지 않는다 — 먼저 뜬 쪽이 대개 원인에 더 가깝다. */
    fun showErrorAlert(alert: ErrorAlert) {
        if (_errorAlert.value == null) _errorAlert.value = alert
    }

    fun networkAlert() = showErrorAlert(
        ErrorAlert("인터넷 연결 없음", "인터넷에 연결되어 있지 않아요.\n연결 상태를 확인한 뒤 다시 시도해주세요."),
    )

    /** 같은 (종류·출처) 오류를 다시 알리기까지의 최소 간격. 배경 점검이 반복 실패해도 도배되지 않는다. */
    private val errorNotifiedAt = mutableMapOf<String, Long>()

    /**
     * [ErrorBus] 를 화면으로 잇는 단일 지점 — 데이터 계층이 삼키던 실패가 여기서 토스트·얼럿이 된다.
     *
     * 종류마다 **사용자가 할 일이 있는지**로 표시 수단을 가른다. 연결·서버·형식 오류는 잠시 뒤 풀리거나
     * 우리가 할 게 없으니 토스트로 알리고 지나가지만, 인증 만료와 클라우드 실패는 재연동·정리를 하기
     * 전까지 계속 실패하므로 모달로 세운다.
     */
    /** 연동 만료 모달을 이번 실행에서 이미 보여줬는가 — 재연동 전까지 계속 실패해서 반복 노출된다. */
    private var authAlertShown = false

    /** 최근에 연결 실패를 낸 출처들 — 값은 시각(ms). [networkDownConfirmed] 가 쓴다. */
    private val networkFailedAt = mutableMapOf<String, Long>()

    /**
     * 지금 실패한 출처를 기록하고, **연결이 정말 끊긴 것으로 볼 수 있는지** 답한다.
     *
     * 판단 기준은 "서로 다른 출처 둘이 [NETWORK_CORROBORATE_MS] 안에 실패" 다. 한 곳만
     * 실패하는 건 그 상류의 문제이지 기기의 연결 문제가 아니다.
     */
    private fun networkDownConfirmed(source: String, now: Long): Boolean {
        networkFailedAt.entries.removeAll { now - it.value > NETWORK_CORROBORATE_MS }
        networkFailedAt[source] = now
        return networkFailedAt.size >= 2
    }

    fun observeErrors() {
        scope.launch {
            ErrorBus.events.collect { r ->
                // ⚠️ **서버·API·형식 오류는 화면에 올리지 않는다**(2026-09-08 지시로 되돌림).
                //
                // 데이터 계층의 실패를 전부 토스트로 올렸더니 정상 사용 중에도 "OO 서버가 응답하지
                // 않아요" 가 계속 떴다. 이 앱은 한 화면에서 상류 여러 곳을 동시에 찌르고(HoYoLAB·
                // Enka·mihomo·ennead·GitHub), 그중 하나가 잠깐 흔들리는 건 흔한 일이다.
                // 대개 다음 갱신에 스스로 낫고, 사용자가 할 수 있는 일도 없다.
                //
                // 남기는 것은 **사용자가 조치할 수 있는 것**뿐이다 —
                //   연결 끊김(NETWORK) · 연동 만료(AUTH) · 클라우드 백업 실패(CLOUD).
                // 나머지는 로그로만 남는다(`GatchaNet` 으로 검색).
                if (r.kind == ErrorBus.Kind.SERVER ||
                    r.kind == ErrorBus.Kind.API ||
                    r.kind == ErrorBus.Kind.PARSE
                ) return@collect

                val modal = true   // 남은 셋은 전부 얼럿이다(토스트를 쓰지 않는다)
                // ⚠️ 쿨다운 키에서 **출처를 뺐다.** 이 앱은 한 화면에서 상류 여러 곳을 동시에
                // 찌른다(HoYoLAB·Enka·mihomo·ennead·GitHub). 출처별로 세면 하나가 죽어도
                // 토스트가 출처 수만큼 줄줄이 떴다. 종류가 같으면 한 번만 알린다.
                val key = r.kind.name
                val now = currentTimeMillis()
                val cooldown = if (modal) ERROR_MODAL_COOLDOWN_MS else ERROR_TOAST_COOLDOWN_MS
                if (now - (errorNotifiedAt[key] ?: 0L) < cooldown) return@collect
                // 연동 만료 모달은 **세션당 한 번**만. 재연동 전까지 계속 실패하는 성격이라
                // 시간 쿨다운만 두면 앱을 켜 두는 내내 반복해서 뜬다.
                if (r.kind == ErrorBus.Kind.AUTH) {
                    if (authAlertShown) return@collect
                    authAlertShown = true
                }
                // 연결 끊김은 **한 곳만 실패해서는 단정할 수 없다.** 이 앱은 한 화면에서 상류
                // 여러 곳을 동시에 찌르는데(HoYoLAB·Enka·mihomo·ennead·GitHub), 그중 하나가
                // DNS 를 못 풀거나 인증서가 어긋나도 나머지는 멀쩡히 오간다. 그걸 그대로 올리면
                // 인터넷이 연결돼 있는데 "연결 없음" 얼럿이 뜬다(실측 제보).
                //
                // **서로 다른 출처 둘**이 짧은 시간 안에 실패했을 때만 진짜 끊김으로 본다.
                if (r.kind == ErrorBus.Kind.NETWORK && !networkDownConfirmed(r.source, now)) return@collect
                errorNotifiedAt[key] = now
                val detail = r.detail.takeIf { it.isNotBlank() }
                when (r.kind) {
                    // 연결 자체가 끊긴 것만 알린다 — 사용자가 확인할 수 있는 상태다.
                    // 얼럿 전에 **진짜 끊겼는지 한 번 확인**한다(9/30) — 복귀 직후 한꺼번에 끊긴 요청들이
                    // 두 출처 조건을 넘겨 멀쩡한데도 떴다. 잠깐 기다렸다 확인 요청이 통하면 띄우지 않는다.
                    ErrorBus.Kind.NETWORK -> launch {
                        delay(NETWORK_RECHECK_DELAY_MS)
                        if (Net.isOnline()) { errorNotifiedAt.remove(key); return@launch }
                        showErrorAlert(
                            ErrorAlert(
                                "인터넷 연결 없음",
                                "네트워크에 연결하지 못했어요.\n연결 상태를 확인한 뒤 다시 시도해주세요.",
                            ),
                        )
                    }
                    ErrorBus.Kind.SERVER, ErrorBus.Kind.API, ErrorBus.Kind.PARSE -> Unit  // 위에서 걸렀다
                    ErrorBus.Kind.AUTH -> showErrorAlert(
                        ErrorAlert(
                            "${r.source} 연동 만료",
                            "로그인 정보가 만료됐어요.\n게임 정보 탭에서 다시 연동해주세요." +
                                (detail?.let { "\n\n($it)" } ?: ""),
                        ),
                    )
                    ErrorBus.Kind.CLOUD -> showErrorAlert(
                        ErrorAlert("클라우드 백업 오류", detail ?: "클라우드 백업에 실패했어요."),
                    )
                }
            }
        }
    }

    private companion object {
        /** 연결 끊김을 단정하기까지 서로 다른 출처의 실패를 모아 보는 창. */
        const val NETWORK_CORROBORATE_MS = 20_000L
        /** 같은 오류 토스트를 다시 띄우기까지의 최소 간격(ms). 배경 점검이 20여 건을 동시에 실패해도 한 번만 뜬다. */
        const val ERROR_TOAST_COOLDOWN_MS = 180_000L
        /** 같은 오류 모달을 다시 세우기까지의 최소 간격(ms). 확인을 눌러 닫은 직후 다시 뜨면 앱을 못 쓴다. */
        const val ERROR_MODAL_COOLDOWN_MS = 30L * 60 * 1000
        /** 연결 오류 뒤 실제 연결을 다시 확인하기까지 기다리는 시간 — 복귀 직후 끊김이 가라앉을 틈. */
        const val NETWORK_RECHECK_DELAY_MS = 1_500L
    }
}
