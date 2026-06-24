package com.gatcha.log.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig

/**
 * 백그라운드 주기 작업 — 자동 출석체크 + 로컬 알림 점검(각 토글이 켜져 있을 때만).
 * 알림 점검 로직은 commonMain [NotificationChecker] 로 공통화되어 iOS 와 동일 동작을 보장한다.
 */
class GatchaWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = AppSettings()
        val repo = GatchaRepository(AppSettings.currentAccountId())
        val cfg = repo.loadHoyolab()
        if (settings.autoCheckIn) runCatching { autoCheckIn(settings, repo, cfg) }
        runCatching { NotificationChecker.run(settings, repo, cfg) }
        return Result.success()
    }

    /** 출석 시도·결과 집계·실패 알림은 [AutoCheckInRunner] 가 담당(UI 호출과 동일 흐름). */
    private suspend fun autoCheckIn(settings: AppSettings, repo: GatchaRepository, cfg: HoyolabConfig) {
        AutoCheckInRunner.run(settings, repo, cfg, postFailureNotification = true)
    }
}
