import Network
import Shared

/// NWPathMonitor 로 연결 상태를 감지해 공유(Kotlin) [NetworkMonitor] 에 반영한다.
/// 앱 시작 시 [start] 1회 호출(AppDelegate). VM 의 오프라인 분기(refreshGameInfo·로딩 게이트 등)가 이 값을 읽는다.
///
/// `@unchecked Sendable` 인 근거: 저장 프로퍼티가 둘 다 `let` 이고, 상태 변화는 전부
/// [NWPathMonitor] 가 자기 직렬 큐(`queue`)에서 돌리는 핸들러 안에서만 일어난다.
/// 핸들러 등록([start])은 앱 시작 시 1회뿐이라 경쟁 지점이 없다.
final class NetworkReachability: @unchecked Sendable {
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
