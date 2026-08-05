package com.gatcha.log.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 재부팅·앱 업데이트 후 예약 알림 재설치.
 *
 * [AlarmManager][android.app.AlarmManager] 의 알람은 **재부팅하면 전부 사라지고**, 앱 패키지가
 * 교체돼도(사이드로드 업데이트) 마찬가지다. 다시 깔아주지 않으면 사용자가 앱을 직접 열 때까지
 * 예약 알림이 한 건도 안 온다 — 하필 "앱을 안 열어도 오게 하려고" 만든 장치가 죽는다.
 *
 * WorkManager 주기 작업도 4시간 안에 [GatchaWorker] 로 같은 일을 하지만, 그 사이 구멍을 없앤다.
 */
class AlertBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    ScheduledAlerts.reschedule(AppSettings(), GatchaRepository(AppSettings.currentAccountId()))
                }
                runCatching { DailyCheckInAlarm.apply() }
            } finally {
                pending.finish()
            }
        }
    }
}
