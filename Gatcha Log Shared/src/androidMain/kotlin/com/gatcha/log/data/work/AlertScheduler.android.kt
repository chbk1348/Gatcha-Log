package com.gatcha.log.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.Notifier
import com.gatcha.log.storage.AppContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android 예약형 알림 — **앱 프로세스가 죽어 있어도 정시에 발송한다.**
 *
 * 예전엔 [schedulesAhead] 가 false 라 WorkManager 주기 작업(4시간, [AndroidWorkScheduler])만 믿었다.
 * 그러면 앱을 안 열어도 알림이 오긴 하지만 두 가지가 걸린다.
 * - **최대 4시간 지연.** 재화 가득참처럼 시각이 정해진 알림이 한참 지나서 왔다.
 * - **워커가 아예 안 도는 구간.** 강제 중지나 제조사 절전(삼성 앱 절전·샤오미 등)에 걸리면
 *   주기 작업이 영구히 밀린다.
 *
 * 지금은 iOS 와 같은 구조다 — 시각을 미리 계산할 수 있는 알림([ScheduledAlerts.plan])은 OS 알람으로
 * 꽂아두고, 알람이 프로세스를 깨워 [AlertAlarmReceiver] 가 알림을 쏜다. WorkManager 는 여전히 돌면서
 * ⓐ예약을 다시 깔고 ⓑ예약이 담지 못하는 알림(새 공지·예산 초과)을 맡는다.
 *
 * **비정확 알람**([AlarmManager.setAndAllowWhileIdle])을 쓴다. Doze 를 뚫으면서도 별도 권한이
 * 필요 없고, 오차는 최대 15분 남짓이다. 마감 D-3 아침 9시·재화 가득참에는 충분하다.
 * (setExactAndAllowWhileIdle 은 Android 12+ 에서 알람 권한을 따로 받아야 한다.)
 *
 * **재부팅·앱 업데이트는 알람을 지운다** → [AlertBootReceiver] 가 다시 깐다.
 */
actual object AlertScheduler {

    actual val schedulesAhead: Boolean = true

    /**
     * PendingIntent 요청 코드 베이스. 예약은 **슬롯 번호(0..MAX_PENDING-1)로 식별**한다.
     *
     * 예약 키로 식별하려면 어떤 키를 깔아뒀는지 따로 저장해야 취소할 수 있다. 슬롯을 쓰면
     * "0번부터 끝까지 취소하고 처음부터 다시 깐다"로 끝나 저장할 상태가 없다.
     */
    private const val REQ_BASE = 7000

    /** 알림 ID 기준값 — 기존 ID 대역(2000 대·3300~3700)과 겹치지 않는 4000 대를 쓴다. */
    private const val NOTIF_ID_BASE = 4000

    private const val DAY_MS = 86_400_000L

    const val EXTRA_TITLE = "gl_alert_title"
    const val EXTRA_TEXT = "gl_alert_text"
    const val EXTRA_LINK = "gl_alert_link"
    const val EXTRA_NOTIF_ID = "gl_alert_notif_id"

    /** 데일리 요약 예약 표식 — 수신부가 고정 문구 대신 그날 수치를 계산해 보낸다. */

    /**
     * 예약 키 → 알림 ID. 같은 논리 알림은 재예약해도 같은 ID 를 받아야 알림창에 중복으로 쌓이지 않는다.
     * (슬롯 번호는 재예약 때마다 바뀌므로 ID 로 쓸 수 없다.)
     */
    private fun notificationId(key: String): Int = NOTIF_ID_BASE + key.hashCode().mod(1000)

    /**
     * 걸어 둔 알람의 장부 — **시각은 지났는데 아직 배달되지 않은 알람**을 재예약이 지워 버리지 않게 하려고 둔다(27.52.0).
     *
     * 비정확 알람은 몇 분씩 늦는다(도즈에서는 더). 그사이 재예약이 돌면 슬롯을 전부 취소하는데, 계획은 「앞으로 울릴 것」만
     * 담으므로 방금 지난 그 알람은 다시 걸리지 않고 사라졌다. 이제 지우기 전에 장부를 보고, 지난 지 [LATE_GRACE_MS] 안이면
     * 여기서 대신 보낸다. 배달된 것은 [markDelivered] 가 장부에서 지워 두 번 가지 않는다.
     */
    private const val LEDGER_PREFS = "gatcha_alert_schedule"
    private const val LEDGER_KEY = "registered"
    private const val LATE_GRACE_MS = 30 * 60_000L

    private class Registered(val id: Int, val at: Long, val title: String, val text: String, val link: String)

    private val ledgerLock = Mutex()

    private fun ledger(ctx: Context) = ctx.getSharedPreferences(LEDGER_PREFS, Context.MODE_PRIVATE)

    private fun readLedger(ctx: Context): List<Registered> = runCatching {
        val arr = JSONArray(ledger(ctx).getString(LEDGER_KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Registered(o.getInt("id"), o.getLong("at"), o.optString("title"), o.optString("text"), o.optString("link"))
        }
    }.getOrDefault(emptyList())

    private fun writeLedger(ctx: Context, items: List<Registered>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("at", it.at).put("title", it.title).put("text", it.text).put("link", it.link))
        }
        ledger(ctx).edit().putString(LEDGER_KEY, arr.toString()).apply()
    }

    /** 알람이 배달됐다 — 수신부([AlertAlarmReceiver])가 알림을 보낸 직후, 재예약 전에 부른다. */
    fun markDelivered(notificationId: Int) {
        val ctx = AppContext.appContext
        val now = System.currentTimeMillis()
        // 같은 알림의 **다음 회차**(아직 안 지난 것)는 남긴다.
        runCatching { writeLedger(ctx, readLedger(ctx).filterNot { it.id == notificationId && it.at <= now + 60_000L }) }
    }

    // suspend 는 iOS 콜백을 기다리기 위한 것(expect 문서 참고) — 여기선 알림 게시 말고 중단 지점이 없다.
    actual suspend fun replaceAll(alerts: List<ScheduledAlert>) = ledgerLock.withLock {
        val ctx = AppContext.appContext
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return@withLock

        val now = System.currentTimeMillis()
        val overdue = readLedger(ctx).filter { it.at <= now && now - it.at <= LATE_GRACE_MS }

        // 슬롯을 전부 비운다 — 픽업이 끝나거나 구독을 지우면 옛 예약이 헛알림이 된다.
        for (slot in 0 until ScheduledAlerts.MAX_PENDING) {
            existingPendingIntent(ctx, slot)?.let { am.cancel(it); it.cancel() }
        }
        // 방금 취소한 것 가운데 시각이 이미 지난 알람은 이제 아무도 보내지 않는다 — 여기서 보낸다.
        overdue.forEach { runCatching { Notifier.notify(it.id, it.title, it.text, it.link) } }

        val registered = mutableListOf<Registered>()
        alerts.take(ScheduledAlerts.MAX_PENDING).forEachIndexed { slot, a ->
            val at = nextTriggerMillis(a, now) ?: return@forEachIndexed
            val pi = newPendingIntent(ctx, slot, a) ?: return@forEachIndexed
            // 실패해도(제조사 제한 등) 나머지 예약은 계속 건다. 주기 워커가 백업으로 남아 있다.
            val set = runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }.isSuccess
            if (set) registered += Registered(notificationId(a.key), at, a.title, a.text, a.link)
        }
        runCatching { writeLedger(ctx, registered) }
        Unit
    }

    /** 출석 리마인더는 주기 점검([NotificationChecker] ②)이 맡는다 — 예약형은 iOS 만 쓴다. */
    actual suspend fun refreshAttendanceReminder(settings: AppSettings, repo: GatchaRepository) = Unit

    /**
     * 실제로 알람을 걸 시각. 이미 지난 예약은 버린다.
     * 매일 반복(데일리 요약)은 [ScheduledAlert.whenMillis] 의 시:분만 의미가 있으므로 다음 회차로 민다.
     */
    private fun nextTriggerMillis(a: ScheduledAlert, now: Long): Long? {
        if (!a.repeatsDaily) return a.whenMillis.takeIf { it > now }
        var t = a.whenMillis
        while (t <= now) t += DAY_MS
        return t
    }

    private fun intentFor(ctx: Context): Intent = Intent(ctx, AlertAlarmReceiver::class.java)

    /** 취소용 — 이미 걸린 게 없으면 null(FLAG_NO_CREATE). */
    private fun existingPendingIntent(ctx: Context, slot: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx,
            REQ_BASE + slot,
            intentFor(ctx),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun newPendingIntent(ctx: Context, slot: Int, a: ScheduledAlert): PendingIntent? =
        runCatching {
            PendingIntent.getBroadcast(
                ctx,
                REQ_BASE + slot,
                intentFor(ctx).apply {
                    putExtra(EXTRA_TITLE, a.title)
                    putExtra(EXTRA_TEXT, a.text)
                    putExtra(EXTRA_LINK, a.link)
                    putExtra(EXTRA_NOTIF_ID, notificationId(a.key))
                },
                // extra 는 PendingIntent 동등성에 안 들어가므로 UPDATE_CURRENT 로 내용만 갈아끼운다.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()
}
