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

        // 2-1. 네트워크 연결 감지 시작 — Kotlin NetworkMonitor.online 을 NWPathMonitor 로 지속 갱신.
        NetworkReachability.shared.start()

        // 3. 알림 델리게이트 — 이게 없으면 앱이 포그라운드일 때 발생한 로컬 알림
        //    (예산 초과·출석 완료·재화 넛지)이 배너로 표시되지 않고 무음으로 사라진다.
        UNUserNotificationCenter.current().delegate = self

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
