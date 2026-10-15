package com.gatcha.log.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 백그라운드 주기 작업 — 깨우는 일만 맡는다. 무엇을 어떤 순서로 할지는 공용 [BackgroundPass] 가 정한다.
 */
class GatchaWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 자동 출석 → 알림 점검 → 예약 다시 걸기. 순서와 단계별 예외 처리는 공용 [BackgroundPass] 한 곳에 있다
        // (iOS 백그라운드 작업도 같은 함수를 부른다 — 예전엔 두 곳이 따로 적혀 토글 조건이 어긋났다).
        BackgroundPass.run()
        // 자동 출석 일일 알람도 자가 복구 — 알람이 유실됐거나(강제 종료 복귀) 아직 안 걸린 경우를 메운다.
        runCatching { DailyCheckInAlarm.apply() }
        return Result.success()
    }
}
