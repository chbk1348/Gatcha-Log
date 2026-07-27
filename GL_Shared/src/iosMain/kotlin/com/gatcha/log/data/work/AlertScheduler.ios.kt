package com.gatcha.log.data.work

import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Notifier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS 예약형 알림 — **앱이 떠 있지 않아도 OS 가 정시에 발송한다.**
 *
 * BGAppRefreshTask 는 실행 시점이 OS 재량이고 앱이 강제 종료돼 있으면 아예 돌지 않아,
 * 오래 앱을 안 열면 알림이 통째로 밀렸다. 시각을 미리 알 수 있는 알림은 여기서 캘린더 트리거로
 * 등록해 그 의존을 끊는다(출석 리마인더가 쓰던 방식과 동일).
 *
 * 식별자에 [PREFIX] 를 붙여, 재예약 때 우리 예약만 골라 지운다(즉시 알림·출석 리마인더는 보존).
 */
@OptIn(ExperimentalForeignApi::class)
actual object AlertScheduler {

    private const val PREFIX = "gatcha_sched_"

    actual val schedulesAhead: Boolean = true

    actual fun replaceAll(alerts: List<ScheduledAlert>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        // 이전 예약 정리 — 데이터가 바뀌면(픽업 종료·구독 삭제) 옛 예약이 남아 헛알림이 된다.
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val stale = requests.orEmpty()
                .mapNotNull { (it as? UNNotificationRequest)?.identifier }
                .filter { it.startsWith(PREFIX) }
            if (stale.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(stale)
            alerts.forEach { schedule(center, it) }
        }
    }

    private fun schedule(center: UNUserNotificationCenter, a: ScheduledAlert) {
        val content = UNMutableNotificationContent().apply {
            setTitle(a.title)
            setBody(a.text)
            if (a.link.isNotBlank()) setUserInfo(mapOf(Notifier.KEY_LINK to a.link))
        }
        val comps = NSDateComponents().apply {
            hour = DateUtil.hourOf(a.whenMillis).toLong()
            minute = DateUtil.minuteOf(a.whenMillis).toLong()
            // 매일 반복이면 시:분만 지정한다(연·월·일을 넣으면 그 날 1회로 굳는다).
            if (!a.repeatsDaily) {
                year = DateUtil.year(a.whenMillis).toLong()
                month = DateUtil.month(a.whenMillis).toLong()
                day = DateUtil.dayOfMonth(a.whenMillis).toLong()
            }
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(comps, a.repeatsDaily)
        val request = UNNotificationRequest.requestWithIdentifier(PREFIX + a.key, content, trigger)
        center.addNotificationRequest(request, withCompletionHandler = null)
    }
}
