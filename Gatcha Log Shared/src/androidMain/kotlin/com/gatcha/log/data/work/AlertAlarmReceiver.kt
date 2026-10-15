package com.gatcha.log.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.Notifier
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
        val key = intent.getStringExtra(AlertScheduler.EXTRA_KEY).orEmpty()
        if (title.isEmpty() || id == 0) return

        // 알림 발송·재예약은 suspend 다. goAsync 로 브로드캐스트 수명을 늘려 두지 않으면
        // onReceive 반환과 동시에 프로세스가 죽어 둘 다 유실된다(제한시간 약 10초).

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = AppSettings()
                val repo = GatchaRepository(AppSettings.currentAccountId())
                // 출석 리마인더는 **지금도 남은 출석이 있을 때만** 보낸다 — 예약은 어제 걸렸고, 그사이 자동 출석이나
                // 직접 출석으로 끝났을 수 있다(예약이 고쳐지지 않은 채 울리는 경우를 여기서 한 번 더 거른다).
                val stale = key == ScheduledAlerts.ATTEND_KEY &&
                    runCatching { !ScheduledAlerts.attendanceStillPending(repo) }.getOrDefault(false)
                if (!stale) runCatching { Notifier.notify(id, title, text, link) }
                // 배달됐다고 적어 둔다 — 안 적으면 바로 아래 재예약이 「지났는데 안 온 알람」으로 보고 한 번 더 보낸다.
                AlertScheduler.markDelivered(id)
                BackgroundPass.rescheduleOnly(settings, repo)
            } finally {
                pending.finish()
            }
        }
    }
}
