package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateByAddingTimeInterval

/**
 * iOS 스케줄러 — BGTaskScheduler(BGAppRefreshTask) 기반 자동 출석.
 *
 * iOS 는 백그라운드 실행 시점을 OS 가 결정하므로 "매일 정시 출석" 보장은 불가하다.
 * earliestBeginDate(6시간 후)만 힌트로 주고, 실제 실행은 OS 재량 + 앱 포그라운드 보충 실행으로 메운다.
 *
 * 사용 흐름:
 *  - 앱 시작(didFinishLaunching) 직후 Swift 가 [registerBackgroundTask] 를 1회 호출 → 태스크 핸들러 등록
 *  - 설정 토글 변경 시 [apply] 호출 → 필요하면 작업 예약, 아니면 취소
 *  - 토글 ON 즉시 1회 출석은 [runNow]
 */
private const val TASK_ID = "com.gatcha.log.ios.checkin"
private const val REFRESH_INTERVAL_SECONDS = 6.0 * 3600.0

/** 자동 출석 1회 실행 — 백그라운드/즉시 실행 공용. HoYoLAB 미연동이면 내부에서 no-op. */
private suspend fun runCheckIn() {
    val settings = AppSettings()
    val repo = GatchaRepository(AppSettings.currentAccountId())
    AutoCheckInRunner.run(
        settings = settings,
        repo = repo,
        cfg = repo.loadHoyolab(),
        postFailureNotification = true,
    )
}

@OptIn(ExperimentalForeignApi::class)
actual object NativeScheduler {

    actual fun apply() {
        val scheduler = BGTaskScheduler.sharedScheduler
        if (AppSettings().needsPeriodicWork()) {
            val request = BGAppRefreshTaskRequest(TASK_ID).apply {
                earliestBeginDate = NSDate().dateByAddingTimeInterval(REFRESH_INTERVAL_SECONDS)
            }
            // 등록되지 않은 식별자/시뮬레이터 등에서 throw 할 수 있어 예외는 무시한다.
            runCatching { scheduler.submitTaskRequest(request, null) }
        } else {
            scheduler.cancelTaskRequestWithIdentifier(TASK_ID)
        }
    }

    actual fun runNow() {
        CoroutineScope(Dispatchers.Default).launch {
            runCheckIn()
        }
    }
}

/**
 * 백그라운드 태스크 핸들러 등록 — Swift 의 application(didFinishLaunchingWithOptions:) 안에서
 * `NativeScheduler_iosKt.registerBackgroundTask()` 로 호출한다.
 * (BGTaskScheduler.register 는 앱 launch 가 끝나기 전에만 호출 가능하므로 top-level 로 노출)
 */
@OptIn(ExperimentalForeignApi::class)
fun registerBackgroundTask() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
        identifier = TASK_ID,
        usingQueue = null,
    ) { task ->
        val refreshTask = task as? BGAppRefreshTask ?: return@registerForTaskWithIdentifier
        // 다음 주기를 먼저 재예약(이번 실행이 실패해도 체인이 끊기지 않게).
        NativeScheduler.apply()

        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch {
            runCatching { runCheckIn() }
            refreshTask.setTaskCompletedWithSuccess(true)
        }
        // OS 가 시간 초과로 작업을 회수할 때 코루틴 취소 + 실패 보고.
        refreshTask.expirationHandler = {
            job.cancel()
            refreshTask.setTaskCompletedWithSuccess(false)
        }
    }
}
