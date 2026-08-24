package com.gatcha.log.data

/**
 * 인터넷 연결 가능 여부 best-effort 감지 — 오프라인 시 사전 경고 + 불필요한 타임아웃(12초) 회피용.
 *
 * Android: ConnectivityManager(즉시 질의), iOS: NWPathMonitor(백그라운드 캐시).
 * **불확실하면 true(낙관)로 반환**해 거짓 오프라인 경고를 피한다 — 실제 호출은 어차피 실패 시
 * [api.Net] 에서 code=-1 로 처리된다. 즉 이 체크는 "확실히 오프라인일 때 빠르게 안내"가 목적.
 */
expect object NetworkMonitor {
    fun isOnline(): Boolean
}
