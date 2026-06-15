import Network
import Shared

/// NWPathMonitor 로 연결 상태를 감지해 공유(Kotlin) [NetworkMonitor] 에 반영한다.
/// 앱 시작 시 [start] 1회 호출(AppDelegate). VM 의 오프라인 분기(refreshGameInfo·로딩 게이트 등)가 이 값을 읽는다.
final class NetworkReachability {
    static let shared = NetworkReachability()
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.gatcha.log.netmon")

    func start() {
        monitor.pathUpdateHandler = { path in
            NetworkMonitor.shared.online = (path.status == .satisfied)
        }
        monitor.start(queue: queue)
    }
}
