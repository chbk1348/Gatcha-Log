import SwiftUI
import ComposeApp
import FirebaseCore
import GoogleSignIn

/// 앱 델리게이트 — Firebase 초기화 + 백그라운드 태스크 등록 + 구글 로그인 브리지.
/// (BGTaskScheduler 등록은 앱 launch 완료 전에 해야 하므로 didFinishLaunching 에서 처리)
class AppDelegate: NSObject, UIApplicationDelegate {
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

        // 3. Kotlin(AuthManager)에 구글 로그인 플로우 등록 — 로그인 버튼 탭 시 GIDSignIn 실행
        IosGoogleSignIn.shared.provider = { callback in
            DispatchQueue.main.async {
                // 최상위 ViewController 탐색 (모달 위에서도 동작)
                guard let rootVC = UIApplication.shared.connectedScenes
                    .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
                    .first?.rootViewController else {
                    callback(nil, nil, nil, nil)
                    return
                }
                var topVC = rootVC
                while let presented = topVC.presentedViewController { topVC = presented }

                GIDSignIn.sharedInstance.signIn(withPresenting: topVC) { result, error in
                    let user = result?.user
                    callback(
                        user?.idToken?.tokenString,
                        user?.profile?.email,
                        user?.profile?.name,
                        user?.profile?.imageURL(withDimension: 200)?.absoluteString
                    )
                }
            }
        }
        return true
    }

    /// 구글 로그인 OAuth 콜백 URL 처리
    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        return GIDSignIn.sharedInstance.handle(url)
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                // SwiftUI 라이프사이클에서의 OAuth 콜백 URL 처리 (AppDelegate 경로의 보완)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
