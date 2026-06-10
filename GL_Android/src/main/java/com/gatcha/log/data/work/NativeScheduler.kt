package com.gatcha.log.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gatcha.log.data.AppSettings
import java.util.concurrent.TimeUnit

/** 자동 출석·알림 점검용 주기 작업 스케줄링. 토글 변화·앱 시작 시 [apply] 로 동기화한다. */
object NativeScheduler {
    private const val PERIODIC = "gatcha_periodic_work"

    /** 설정 상태에 맞춰 주기 작업을 켜거나 끈다. */
    fun apply(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (AppSettings(context).needsPeriodicWork()) {
            val req = PeriodicWorkRequestBuilder<GatchaWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(PERIODIC)
        }
    }

    /** 즉시 1회 실행(설정 켠 직후 바로 출석 시도). */
    fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<GatchaWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
