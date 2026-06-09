package com.gatcha.log.data.work

/**
 * 백그라운드 주기 작업(자동 출석·알림 점검) 스케줄러 (expect/actual).
 * - Android: WorkManager (5단계에서 구현 — 현재 :app 의 NativeScheduler 와 동일 역할)
 * - iOS: BGTaskScheduler (5단계에서 구현)
 *
 * 4단계 현재: 양쪽 다 no-op. 포그라운드 동작(수동 출석·토글 즉시 실행)은 정상 동작한다.
 */
expect object NativeScheduler {
    /** 설정 상태에 맞춰 주기 작업 등록/해제 동기화 */
    fun apply()

    /** 주기와 무관하게 즉시 1회 실행 */
    fun runNow()
}
