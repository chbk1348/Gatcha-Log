package com.gatcha.log.data.work

/**
 * Android 스케줄러 — 5단계에서 WorkManager 로 구현 예정.
 * (composeApp 의 androidMain 은 현재 :app 에서 사용되지 않으므로 no-op 이어도 영향 없음)
 */
actual object NativeScheduler {
    actual fun apply() { /* 5단계: WorkManager 주기 작업 등록 */ }
    actual fun runNow() { /* 5단계: WorkManager 즉시 실행 */ }
}
