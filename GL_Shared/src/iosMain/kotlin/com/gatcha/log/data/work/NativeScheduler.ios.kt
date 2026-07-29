package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository
import kotlin.concurrent.AtomicInt
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

/**
 * 자동 출석 + 로컬 알림 점검 1회 — 백그라운드/즉시 실행 공용. HoYoLAB 미연동이면 출석은 내부에서 no-op.
 * 알림 점검([NotificationChecker])은 Android(GatchaWorker)와 동일 로직으로 패리티 유지.
 */
private suspend fun runCheckIn() {
    val settings = AppSettings()
    val repo = GatchaRepository(AppSettings.currentAccountId())
    val cfg = repo.loadHoyolab()
    AutoCheckInRunner.run(
        settings = settings,
        repo = repo,
        cfg = cfg,
        postFailureNotification = true,
    )
    runCatching { NotificationChecker.run(settings, repo, cfg) }
    // 출석 리마인더 예약형 갱신(다음 18:00) — BGTask 정시 비보장을 보완.
    runCatching { AttendanceReminder.reschedule(settings, repo, cfg) }
    // 확정 시각 알림(픽업·시즌 마감·정기결제·재화 가득참·데일리 요약) 재예약 —
    // 앱을 오래 안 열어도 OS 가 정시에 쏘도록.
    // 이 호출은 **등록이 끝날 때까지 중단한다**. 호출자(BGTask 핸들러)가 완료 보고를 하는 순간
    // OS 가 앱을 서스펜드하므로, 기다리지 않으면 예약이 걸리기 전에 프로세스가 멈춘다.
    runCatching { ScheduledAlerts.reschedule(settings, repo) }
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

        // setTaskCompleted 는 정확히 1회만 호출되어야 한다 (이중 호출은 Apple 문서상 undefined behavior).
        // job 완료(성공)와 expirationHandler(만료)가 서로 다른 스레드에서 경합할 수 있으므로 CAS 가드.
        val completed = AtomicInt(0)
        fun completeOnce(success: Boolean) {
            if (completed.compareAndSet(0, 1)) {
                refreshTask.setTaskCompletedWithSuccess(success)
            }
        }

        val scope = CoroutineScope(Dispatchers.Default)
        val job = scope.launch {
            runCatching { runCheckIn() }
            completeOnce(true)
        }
        // OS 가 시간 초과로 작업을 회수할 때 코루틴 취소 + 실패 보고.
        refreshTask.expirationHandler = {
            job.cancel()
            completeOnce(false)
        }
    }
}
