import Foundation
import UserNotifications

// ════════════════════════════════════════════════════════════════════════════
// iOS 로컬 알림 권한 — 알림 토글을 켤 때 요청.
// (Compose 경로의 ensureNotifPerm 은 Android 전용이었음; iOS 는 명시적 요청 필요)
// ════════════════════════════════════════════════════════════════════════════

enum NotificationPermission {
    /// 알림 권한 요청. 아직 미결정이면 시스템 프롬프트, 이미 결정됐으면 즉시 반환(무해).
    /// [completion] 은 프롬프트가 끝났거나 띄울 필요가 없을 때 **메인 액터에서** 호출된다(UI 갱신용).
    ///
    /// `newlyGranted` 는 **이번 호출로 처음 허용됐는지** — `.notDetermined` 였다가 사용자가 '허용'을
    /// 누른 경우에만 true 다. 이미 허용된 상태에서 다시 불렀거나, 거부했거나, 시스템 설정에서 켜고
    /// 돌아온 경우는 false. 항목 일곱 개 일괄 ON(`enableAllNotifyItems`)의 방아쇠라 이 구분이 중요하다 —
    /// "켤 때마다"로 잡으면 사용자가 **일부러 끈 항목까지 되살아난다.**
    ///
    /// 콜백형 UN API 를 async 로 감싼다 — 완료 핸들러 버전은 non-Sendable 인 `UNNotificationSettings`
    /// 를 큐 너머로 넘기게 돼서 Swift 6 에서 데이터 레이스로 잡힌다. async 판은 값만 건너온다.
    static func request(completion: (@MainActor @Sendable (_ newlyGranted: Bool) -> Void)? = nil) {
        Task { @MainActor in
            let center = UNUserNotificationCenter.current()
            var newlyGranted = false
            if await center.notificationSettings().authorizationStatus == .notDetermined {
                newlyGranted = ((try? await center.requestAuthorization(options: [.alert, .sound, .badge])) == true)
            }
            completion?(newlyGranted)
        }
    }

    /// 현재 권한 상태 조회. 시스템 설정으로 보낼지(.denied) 프롬프트를 띄울지(.notDetermined) 가르는 데 쓴다.
    static func status(_ handler: @escaping @MainActor @Sendable (UNAuthorizationStatus) -> Void) {
        Task { @MainActor in
            handler(await UNUserNotificationCenter.current().notificationSettings().authorizationStatus)
        }
    }
}
