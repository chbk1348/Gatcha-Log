import SwiftUI
import Shared
import FirebaseCore
import UserNotifications

/// 앱 델리게이트 — Firebase 초기화 + 백그라운드 태스크 등록 + 구글 로그인 브리지 + 알림 델리게이트.
/// (BGTaskScheduler 등록은 앱 launch 완료 전에 해야 하므로 didFinishLaunching 에서 처리)
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // 1. Firebase 초기화 (GoogleService-Info.plist 필요 — 없으면 클라우드 기능만 비활성, 앱은 로컬로 동작)
        if FileManager.default.fileExists(
            atPath: Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") ?? ""
        ) {
            FirebaseApp.configure()
        }

        // 2. 자동 출석 백그라운드 태스크 등록 (Kotlin BGTaskScheduler 핸들러)
        NativeScheduler_iosKt.registerBackgroundTask()

        // 2-0. 출석 리마인더 예약형 갱신 — 앱 열 때마다 다음 베이징 18:00(로컬 환산) 1회 예약.
        //      BGTask 정시 비보장을 보완(UNCalendarNotificationTrigger 가 OS 정시 발송 보장).
        AttendanceReminder_iosKt.rescheduleAttendanceReminder()

        // 2-1. 네트워크 연결 감지 시작 — Kotlin NetworkMonitor.online 을 NWPathMonitor 로 지속 갱신.
        NetworkReachability.shared.start()

        // 3. 알림 델리게이트 — 이게 없으면 앱이 포그라운드일 때 발생한 로컬 알림
        //    (예산 초과·출석 완료·재화 넛지)이 배너로 표시되지 않고 무음으로 사라진다.
        UNUserNotificationCenter.current().delegate = self

        // 3-1. 앱 시작 시 알림 권한 자동 요청은 없앴다(v27.38.0) — 켜자마자 맥락 없이 뜨던 팝업이었다.
        //      신규 유저는 온보딩 ④에서 맥락과 함께 요청하고, 기존 유저는 이미 물어본 적이 있으며,
        //      그 외에는 알림 설정 화면의 안내 배너에서 직접 허용할 수 있다.

        // 4. Kotlin(AuthManager)에 구글 로그인 플로우 등록 — 웹 OAuth(ASWebAuthenticationSession+PKCE).
        //    SDK(GIDSignIn) 대신 사파리 웹 로그인으로 id_token/access_token 을 받아 그대로 전달.
        IosGoogleSignIn.shared.provider = { callback in
            DispatchQueue.main.async {
                GoogleWebOAuth.shared.signIn { tokens in
                    DispatchQueue.main.async {
                        _ = callback(tokens?.idToken, tokens?.accessToken, tokens?.email, tokens?.name, tokens?.picture)
                    }
                }
            }
        }
        return true
    }

    /// 앱이 포그라운드일 때도 로컬 알림을 배너로 표시 (UNUserNotificationCenterDelegate)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    /// 알림 탭 → 관련 탭으로 이동 (identifier "gatcha_alert_<id>" 의 id 로 라우팅).
    /// Notifier 의 ID 체계: 예산 2001 / 게임별예산 3300+ → 지출(1), 출석 2002·자동출석 2003 / 재화 2100+ → 게임정보(2).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let id = response.notification.request.identifier
            .replacingOccurrences(of: "gatcha_alert_", with: "")
        if let nid = Int(id) {
            let tab: Int
            switch nid {
            case 2001, 3300...3399: tab = 1       // 예산 → 지출
            case 2002, 2003, 2100...2199: tab = 2 // 출석·자동출석·재화 → 게임 정보
            default: tab = 0
            }
            NotificationCenter.default.post(name: .glgOpenTab, object: tab)
        }
        completionHandler()
    }
}

extension Notification.Name {
    /// 알림 탭으로 특정 탭 열기 — object 에 탭 인덱스(Int). ContentView 가 구독해 selectedTab 갱신.
    static let glgOpenTab = Notification.Name("glgOpenTab")
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
