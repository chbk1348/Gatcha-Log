import SwiftUI
import Shared
import FirebaseCore
import UserNotifications

/// 앱 델리게이트 — Firebase 초기화 + 백그라운드 태스크 등록 + 구글 로그인 브리지 + 알림 델리게이트.
/// (BGTaskScheduler 등록은 앱 launch 완료 전에 해야 하므로 didFinishLaunching 에서 처리)
/// `@preconcurrency` — UNUserNotificationCenterDelegate 는 액터 표시가 없는 옛 프로토콜인데,
/// 이 델리게이트 구현은 UI·공유 VM 을 만져 메인 액터에 묶여 있다. Swift 6 는 그 교차를 막으므로
/// 프로토콜 쪽을 사전 동시성으로 받아들인다(호출은 실제로 메인에서 온다).
class AppDelegate: NSObject, UIApplicationDelegate, @preconcurrency UNUserNotificationCenterDelegate {
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

        // 1-1. 온보딩 노출 판정을 굳힌다(기존 유저 = 온보딩 완료로 확정). 로그아웃하면 계정 id 가 지워지므로,
        //      매번 계산하면 기존 유저가 로그아웃하는 순간 온보딩이 다시 뜬다. 첫 실행에 한 번 파일에 박는다.
        AppSettings().freezeOnboardingVerdict()

        // 2. 자동 출석 백그라운드 태스크 등록 (Kotlin BGTaskScheduler 핸들러)
        NativeScheduler_iosKt.registerBackgroundTask()

        // 2-0. 출석 리마인더 예약형 갱신 — 앱 열 때마다 다음 베이징 18:00(로컬 환산) 1회 예약.
        //      BGTask 정시 비보장을 보완(UNCalendarNotificationTrigger 가 OS 정시 발송 보장).
        //
        //      **첫 프레임 이후로 미룬다.** 이 한 줄이 저장소를 새로 열고 출석 이력·배너·전투·정기결제·
        //      실시간 노트 JSON 을 파싱한다. 예약 시각은 '다음 18:00' 이라 몇 백 ms 늦어도 결과가 같은데,
        //      그 비용을 런치스크린 뒤 공백에 얹을 이유가 없다.
        //      (2026-07-29 이후 Kotlin 쪽이 그 파싱·등록을 백그라운드 코루틴에서 처리하고 즉시 반환한다.
        //       메인 스레드 비용은 사라졌지만, 호출 자체를 첫 프레임 뒤로 미루는 건 그대로 둔다.)
        DispatchQueue.main.async {
            AttendanceReminder_iosKt.rescheduleAttendanceReminder()
        }

        // 2-1. 네트워크 연결 감지 시작 — Kotlin NetworkMonitor.online 을 NWPathMonitor 로 지속 갱신.
        NetworkReachability.shared.start()

        // 2-2. 이미지 캐시 — 기본 URLCache 는 메모리 512KB / 디스크 10MB 수준이라 캐릭터 초상·성유물
        //      아이콘이 화면을 오갈 때마다 재요청된다. 디스크만 넉넉히 키운다.
        //
        //      메모리는 일부러 작게 잡는다 — 목록 이미지는 GLGImageCache(디코딩본 48MB)가 이미 들고 있어서,
        //      URLCache 메모리까지 크면 같은 이미지를 압축본·디코딩본으로 두 번 상주시키게 된다.
        //      디스크 캐시는 앱 재시작 후 첫 로드에 여전히 유효하므로 그대로 둔다.
        URLCache.shared = URLCache(memoryCapacity: 8 * 1024 * 1024,
                                   diskCapacity: 128 * 1024 * 1024)

        // 3. 알림 델리게이트 — 이게 없으면 앱이 포그라운드일 때 발생한 로컬 알림
        //    (예산 초과·출석 완료·재화 넛지)이 배너로 표시되지 않고 무음으로 사라진다.
        UNUserNotificationCenter.current().delegate = self

        // 3-1. 앱 시작 시 알림 권한 자동 요청은 없앴다(v27.38.0) — 켜자마자 맥락 없이 뜨던 팝업이었다.
        //      신규 유저는 온보딩 ④에서 맥락과 함께 요청하고, 기존 유저는 이미 물어본 적이 있으며,
        //      그 외에는 알림 설정 화면의 안내 배너에서 직접 허용할 수 있다.

        // 4. Kotlin(AuthManager)에 구글 로그인 플로우 등록 — 웹 OAuth(ASWebAuthenticationSession+PKCE).
        //    SDK(GIDSignIn) 대신 사파리 웹 로그인으로 id_token/access_token 을 받아 그대로 전달.
        IosGoogleSignIn.shared.provider = { callback in
            // Kotlin 함수 타입에는 Sendable 표시가 없어 클로저 경계를 못 넘는다. 상자에 담아 나른다 —
            // 실제 호출은 아래 두 DispatchQueue.main 안이라 **항상 메인**이다.
            let box = GLGUncheckedBox(callback)
            DispatchQueue.main.async {
                GoogleWebOAuth.shared.signIn { tokens in
                    DispatchQueue.main.async {
                        _ = box.value(tokens?.idToken, tokens?.accessToken, tokens?.email, tokens?.name, tokens?.picture)
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
        // 딥링크가 실려 있으면 그쪽이 우선 — 탭 전환 + 상세 진입까지 공유 VM 이 처리한다.
        if let link = response.notification.request.content.userInfo[Notifier.shared.KEY_LINK] as? String,
           !link.isEmpty {
            NotificationCenter.default.post(name: .glgDeepLink, object: link)
            completionHandler()
            return
        }
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

/// Sendable 표시가 없는 값(주로 Kotlin 함수 타입)을 클로저 경계 너머로 나르는 상자.
/// **호출부가 실제 스레드를 보장할 때만** 쓴다 — 컴파일러 검사를 끄는 것이지 안전해지는 게 아니다.
struct GLGUncheckedBox<T>: @unchecked Sendable {
    let value: T
    init(_ value: T) { self.value = value }
}

extension Notification.Name {
    /// 알림 탭으로 특정 탭 열기 — object 에 탭 인덱스(Int). ContentView 가 구독해 selectedTab 갱신.
    static let glgOpenTab = Notification.Name("glgOpenTab")
    /// 알림 딥링크 — object 에 링크 문자열("news:<id>"). ContentView 가 공유 VM 에 넘긴다.
    static let glgDeepLink = Notification.Name("glgDeepLink")
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
