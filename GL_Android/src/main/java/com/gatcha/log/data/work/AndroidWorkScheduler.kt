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

/**
 * 자동 출석·알림 점검용 주기 작업 스케줄링. 토글 변화·앱 시작 시 [apply] 로 동기화한다.
 *
 * NOTE: GL_Shared 에도 KMP expect/actual `NativeScheduler`(no-op 스텁)가 있어 FQN 이
 * 겹치면 R8(release) 가 "defined multiple times" 로 실패한다. Android 실구현은 이 이름으로 분리한다.
 */
object AndroidWorkScheduler {
    private const val PERIODIC = "gatcha_periodic_work"

    // 알림 점검은 로컬 데이터(예산 등)도 다루므로 네트워크를 요구하지 않는다.
    // 네트워크가 필요한 작업(자동출석·재화 note)은 워커 내부에서 실패 시 graceful 하게 스킵된다.
    // → 오프라인이어도 예산 알림이 누락되지 않음.
    private val noNetworkConstraint =
        Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()

    /** 설정 상태에 맞춰 주기 작업을 켜거나 끈다. */
    fun apply(context: Context) {
        // 자동 출석 일일 알람도 함께 동기화 — 주기 작업만으론 Doze·절전에서 며칠씩 밀린다.
        // (토글이 꺼져 있으면 내부에서 알람을 취소한다)
        runCatching { DailyCheckInAlarm.apply() }
        val wm = WorkManager.getInstance(context)
        if (AppSettings().needsPeriodicWork()) {
            // 6h→4h: "18시 이후" 같은 시각 조건 슬롯 확보 확률을 높인다(배터리 trade-off 수용).
            val req = PeriodicWorkRequestBuilder<GatchaWorker>(4, TimeUnit.HOURS)
                .setConstraints(noNetworkConstraint)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(PERIODIC)
        }
    }

    /** 즉시 1회 실행(설정 켠 직후 바로 출석 시도). */
    fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<GatchaWorker>()
            .setConstraints(noNetworkConstraint)
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
