package com.gatcha.log.data

import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS 알림 구현 — UNUserNotificationCenter 로컬 알림.
 * :app 의 Notifier 와 동일한 ID 체계 (id 는 identifier 문자열로 변환).
 *
 * 권한 "요청"은 설정 화면(NotificationPermission.request)에서만 1회 수행한다.
 * 여기서는 현재 권한 상태만 확인하고, 허용 상태일 때만 알림을 추가한다(매 알림마다 재요청하던 동작 제거).
 */
actual object Notifier {

    actual val ID_BUDGET: Int = 2001
    actual val ID_ATTEND: Int = 2002
    actual val ID_AUTO_CHECKIN: Int = 2003
    actual val ID_RESIN_BASE: Int = 2100
    actual val ID_BUDGET_GAME_BASE: Int = 3300
    actual val ID_PICKUP_BASE: Int = 3400

    // getNotificationSettings 는 비동기(콜백) → 동기 notificationsEnabled() 용으로 마지막 상태를 캐시.
    private var cachedEnabled: Boolean = false

    actual fun notify(id: Int, title: String, text: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            val enabled = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
            cachedEnabled = enabled
            if (!enabled) return@getNotificationSettingsWithCompletionHandler
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

    actual fun notificationsEnabled(): Boolean {
        // 백그라운드로 캐시 갱신 후 직전 값 반환(다음 호출/렌더에 반영).
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            cachedEnabled = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
        }
        return cachedEnabled
    }
}
