package com.gatcha.log.data.work

/**
 * Android 백그라운드 스케줄러 브리지.
 *
 * WorkManager 실구현(GatchaWorker 포함)은 AndroidX 의존성이 필요해 shared(KMP)androidMain 으로
 * 끌어올리지 않고, :app 이 [AndroidNativeScheduler] 에 동작을 등록한다(provider 위임).
 * provider 미등록(예: 단위 테스트)이면 no-op.
 */
object AndroidNativeScheduler {
    /** 설정 상태에 맞춰 주기 작업 등록/해제 — :app 의 AndroidWorkScheduler.apply 위임. */
    var applyProvider: (() -> Unit)? = null

    /** 즉시 1회 실행 — :app 의 AndroidWorkScheduler.runNow 위임. */
    var runNowProvider: (() -> Unit)? = null
}

actual object NativeScheduler {
    actual fun apply() {
        AndroidNativeScheduler.applyProvider?.invoke()
    }

    actual fun runNow() {
        AndroidNativeScheduler.runNowProvider?.invoke()
    }
}
