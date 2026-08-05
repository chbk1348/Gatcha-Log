package com.gatcha.log.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.Notifier
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * [AlertScheduler] 가 건 알람의 수신부 — **앱 프로세스가 죽어 있어도 여기서 깨어난다.**
 *
 * 하는 일은 두 가지다.
 * 1. 알람에 담아둔 내용을 그대로 알림으로 발송. 발송 시점엔 데이터를 다시 볼 수 없으므로
 *    ([ScheduledAlerts] 가 예약을 만들 때 이미 계산해 뒀다) extra 를 그대로 쓴다.
 * 2. **예약 재충전.** 알람은 한 번 울리면 사라진다. 방금 울린 자리를 비워두면 다음 회차
 *    (데일리 요약의 내일치, 마감 D-3 다음의 D-1)가 영영 안 걸린다. 그래서 발송 직후
 *    현재 데이터로 예약을 통째로 다시 깐다 — 앱을 한 번도 안 열어도 예약이 이어진다.
 */
class AlertAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AlertScheduler.EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(AlertScheduler.EXTRA_TEXT).orEmpty()
        val link = intent.getStringExtra(AlertScheduler.EXTRA_LINK).orEmpty()
        val id = intent.getIntExtra(AlertScheduler.EXTRA_NOTIF_ID, 0)
        if (title.isEmpty() || id == 0) return

        // 알림 발송·재예약은 suspend 다. goAsync 로 브로드캐스트 수명을 늘려 두지 않으면
        // onReceive 반환과 동시에 프로세스가 죽어 둘 다 유실된다(제한시간 약 10초).
        val daily = intent.getBooleanExtra(AlertScheduler.EXTRA_DAILY_SUMMARY, false)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = AppSettings()
                val repo = GatchaRepository(AppSettings.currentAccountId())
                if (daily) {
                    // 데일리 요약만은 예약에 담긴 고정 문구를 쓰지 않는다 — 예약을 만든 시점엔
                    // 그날 수치를 알 수 없어서다. 알람이 우리 프로세스를 깨웠으니 지금 계산한다.
                    // (iOS 는 OS 가 직접 쏘는 구조라 이걸 못 해서 고정 문구로 남는다.)
                    runCatching {
                        NotificationChecker.maybeSendDailySummary(
                            settings, repo, repo.loadHoyolab(), currentTimeMillis(), skipHourCheck = true,
                        )
                    }
                } else {
                    runCatching { Notifier.notify(id, title, text, link) }
                }
                runCatching { ScheduledAlerts.reschedule(settings, repo) }
            } finally {
                pending.finish()
            }
        }
    }
}
