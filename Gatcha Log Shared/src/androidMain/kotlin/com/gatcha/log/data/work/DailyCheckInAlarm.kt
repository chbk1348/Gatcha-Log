package com.gatcha.log.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import com.gatcha.log.data.AppSettings
import com.gatcha.log.storage.AppContext
import com.gatcha.log.util.currentTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 자동 출석체크 **하루 한 번 알람** — 앱 프로세스가 죽어 있어도 돈다.
 *
 * 예전엔 WorkManager 주기 작업(4시간)이 도는 김에 출석을 시도하는 게 전부였다. 그런데 주기 작업은
 * Doze 에서 밀리고, 강제 중지·제조사 절전에 걸리면 며칠씩 안 돈다. "매일 챙겨준다"고 해 놓고
 * 사용자가 앱을 열어야 출석되는 날이 생겼다.
 *
 * 알람은 OS 가 들고 있다가 프로세스를 깨우므로 앱이 떠 있지 않아도 실행된다.
 * (강제 중지만은 어쩔 수 없다 — 그 상태에선 알람도 워커도 안 돈다. 사용자가 앱을 한 번 열어야 한다.)
 *
 * **베이징 05:00(= 한국 06:00)에 돈다.** 출석은 베이징 자정에 초기화되므로 그 날 안에 아무 때나
 * 하면 되지만, 자정 직후로 잡으면 실패 알림이 새벽에 울린다.
 *
 * 예전엔 베이징 09:00 이었는데 그건 **한국 시각 10:00** 이다. "자고 일어나면 아직 출석이 안 돼
 * 있다"는 제보가 여기서 나왔다(2026-09-08) — 아침에 앱을 열면 알람이 아직 오지 않은 상태였다.
 * 05:00 이면 웬만한 기상 시각보다 앞서고, 실패 알림도 새벽이 아니라 아침에 걸린다.
 *
 * 비정확 알람([AlarmManager.setAndAllowWhileIdle])이라 Doze 를 뚫으면서 권한이 필요 없다.
 * 오차 15분 남짓은 하루 단위 작업에 아무 의미가 없다.
 */
@OptIn(ExperimentalTime::class)
object DailyCheckInAlarm {

    /** [AlertScheduler] 의 예약 슬롯(7000~7047)과 겹치지 않는 요청 코드. */
    private const val REQ = 7100

    private val beijingTz = TimeZone.of("UTC+8")

    /** 베이징 기준 실행 시각(시). 한국 시각으로는 +1 이다. */
    private const val HOUR = 5

    /** 일시적 실패(네트워크 등) 시 재시도 간격. */
    private const val RETRY_MS = 2L * 3600 * 1000

    /** 다음 회차(베이징 09:00)로 예약. 자동 출석이 꺼져 있으면 취소한다. */
    fun apply() = schedule(nextDailyMillis())

    /** 일시적 실패 후 재시도 예약. */
    fun scheduleRetry() = schedule(currentTimeMillis() + RETRY_MS)

    private fun schedule(at: Long) {
        val ctx = AppContext.appContext
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        // 토글이 꺼졌으면 남은 알람을 걷어낸다(재예약을 안 하는 것만으론 이미 걸린 게 남는다).
        if (!AppSettings().autoCheckIn) {
            existing()?.let { am.cancel(it); it.cancel() }
            return
        }
        val pi = PendingIntent.getBroadcast(
            ctx,
            REQ,
            Intent(ctx, CheckInAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    private fun existing(): PendingIntent? {
        val ctx = AppContext.appContext
        return PendingIntent.getBroadcast(
            ctx,
            REQ,
            Intent(ctx, CheckInAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 지금보다 뒤에 오는 첫 베이징 [HOUR]시. */
    private fun nextDailyMillis(now: Long = currentTimeMillis()): Long {
        val nowBeijing = Instant.fromEpochMilliseconds(now).toLocalDateTime(beijingTz)
        val date = if (nowBeijing.hour >= HOUR) nowBeijing.date.plus(1, DateTimeUnit.DAY) else nowBeijing.date
        return LocalDateTime(date.year, date.month.number, date.day, HOUR, 0)
            .toInstant(beijingTz)
            .toEpochMilliseconds()
    }
}
