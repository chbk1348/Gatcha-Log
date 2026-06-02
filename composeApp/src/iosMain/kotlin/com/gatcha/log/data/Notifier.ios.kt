package com.gatcha.log.data

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS 알림 구현 — UNUserNotificationCenter 로컬 알림.
 * :app 의 Notifier 와 동일한 ID 체계 (id 는 identifier 문자열로 변환).
 * 권한 미허용 시 조용히 무시 (Android 구현과 동일한 동작).
 */
actual object Notifier {

    actual val ID_BUDGET: Int = 2001
    actual val ID_ATTEND: Int = 2002
    actual val ID_AUTO_CHECKIN: Int = 2003
    actual val ID_RESIN_BASE: Int = 2100
    actual val ID_WISH_PICKUP_BASE: Int = 2200
    actual val ID_BUDGET_GAME_BASE: Int = 3300

    actual fun notify(id: Int, title: String, text: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        // 권한 요청 (이미 허용/거부된 경우 시스템이 즉시 콜백 — 거부면 알림이 표시되지 않을 뿐 크래시 없음)
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions
            val content = UNMutableNotificationContent().apply {
                setTitle(title)
                setBody(text)
            }
            // trigger=null → 즉시 표시. 같은 identifier 는 갱신(누적 안 됨) — Android 와 동일한 동작.
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = "gatcha_alert_$id",
                content = content,
                trigger = null,
            )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }
}
