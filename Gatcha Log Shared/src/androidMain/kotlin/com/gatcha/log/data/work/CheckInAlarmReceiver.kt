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
 * [DailyCheckInAlarm] 수신부 — 앱이 떠 있지 않아도 여기서 깨어나 자동 출석을 시도한다.
 *
 * 끝나면 반드시 다음 알람을 다시 건다. 알람은 한 번 울리면 사라지므로, 재예약을 빠뜨리면
 * "딱 하루만 자동 출석되고 그 뒤로 조용한" 상태가 된다.
 */
class CheckInAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 출석은 네트워크 왕복이라 onReceive 안에서 끝나지 않는다 — goAsync 로 수명을 늘린다(약 10초).
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = AppSettings()
                val repo = GatchaRepository(AppSettings.currentAccountId())
                val outcome = runCatching {
                    AutoCheckInRunner.run(settings, repo, repo.loadHoyolab(), postFailureNotification = true)
                }.getOrNull()

                // 네트워크 같은 일시적 실패면 몇 시간 뒤 한 번 더. 쿠키 만료(authFails)는 다시 해도
                // 결과가 같으므로 재시도하지 않고 다음 날로 넘긴다(사용자 재연동이 필요하다).
                val retryable = outcome != null && outcome.hasAnyFail && outcome.authFails.isEmpty()
                if (retryable) DailyCheckInAlarm.scheduleRetry() else DailyCheckInAlarm.apply()
            } finally {
                pending.finish()
            }
        }
    }
}
