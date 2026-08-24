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
        // 예약 알림(픽업 마감·재화 가득참 등) 재충전 — Android 도 사전 예약을 쓰게 되면서
        // (AlertScheduler.schedulesAhead=true) [NotificationChecker] 는 그 종류를 건너뛴다.
        // 여기서 다시 깔아주지 않으면 울린 예약이 그대로 비어 다음 회차가 안 걸린다.
        // NotificationChecker 뒤에 두는 이유: 방금 갱신한 노트·배너 캐시로 예약을 계산하려고.
        runCatching { ScheduledAlerts.reschedule(settings, repo) }
        // 자동 출석 일일 알람도 자가 복구 — 알람이 유실됐거나(강제 종료 복귀) 아직 안 걸린 경우를 메운다.
        runCatching { DailyCheckInAlarm.apply() }
        return Result.success()
    }

    /** 출석 시도·결과 집계·실패 알림은 [AutoCheckInRunner] 가 담당(UI 호출과 동일 흐름). */
    private suspend fun autoCheckIn(settings: AppSettings, repo: GatchaRepository, cfg: HoyolabConfig) {
        AutoCheckInRunner.run(settings, repo, cfg, postFailureNotification = true)
    }
}
