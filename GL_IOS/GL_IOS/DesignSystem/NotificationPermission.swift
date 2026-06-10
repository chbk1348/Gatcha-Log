import Foundation
import UserNotifications

// ════════════════════════════════════════════════════════════════════════════
// iOS 로컬 알림 권한 — 알림 토글을 켤 때 요청.
// (Compose 경로의 ensureNotifPerm 은 Android 전용이었음; iOS 는 명시적 요청 필요)
// ════════════════════════════════════════════════════════════════════════════

enum NotificationPermission {
    /// 알림 권한 요청. 아직 미결정이면 시스템 프롬프트, 이미 결정됐으면 즉시 반환(무해).
    static func request() {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            guard settings.authorizationStatus == .notDetermined else { return }
            center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        }
    }
}
