package com.gatcha.log.data.work

/**
 * iOS 스케줄러 — 5단계에서 BGTaskScheduler(BGAppRefreshTask) 로 구현 예정.
 * iOS 는 백그라운드 실행 시점을 OS 가 결정하므로 "매일 정시 출석" 보장은 불가 —
 * 앱 실행 시 보충 실행 + 로컬 알림 유도 방식으로 설계 예정.
 */
actual object NativeScheduler {
    actual fun apply() { /* 5단계: BGTaskScheduler 등록 */ }
    actual fun runNow() { /* 5단계: 즉시 실행 */ }
}
