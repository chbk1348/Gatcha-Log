package com.gatcha.log.data

import kotlin.concurrent.Volatile

/**
 * iOS 연결 상태는 **Swift(NWPathMonitor)** 가 갱신한다 — K/N 블록 핸들러보다 안정적.
 * 앱 시작 시 Swift 의 NetworkReachability 가 [online] 을 지속 갱신하고, [isOnline] 은 캐시값을 즉시 반환.
 * 초기값 true(낙관) — 첫 path 콜백 전까지 거짓 오프라인 경고 방지.
 */
actual object NetworkMonitor {
    @Volatile
    var online: Boolean = true

    actual fun isOnline(): Boolean = online
}
