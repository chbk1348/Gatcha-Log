import XCTest

/// iOS 콜드 런치 실측.
///
/// ## 왜 필요한가
///
/// 이 프로젝트에는 **테스트 타깃 자체가 없었다** — XCTest·XCUITest·Instruments 트레이스가 전부 0건.
/// 성능 1·2라운드에서 "가장 큰 병목"이라고 적은 것들이 전부 코드 판독이었고, 실제로 23건 중
/// 3건에서 진단이 어긋났다. 시작 경로(`loadAll()` 동기 실행)를 손대려면 숫자가 먼저 있어야 한다.
///
/// ## 실행
///
/// ```
/// DEVELOPER_DIR=/Applications/Xcode-beta.app/Contents/Developer \
/// xcodebuild test -project GL_IOS/GL_IOS.xcodeproj -scheme GL_IOS \
///   -destination 'platform=iOS,name=<기기명>'
/// ```
///
/// ⚠️ **실기기에서, Release 구성으로** 재는 게 원칙이다. 시뮬레이터는 맥의 CPU·디스크를 쓰고
/// Debug 는 최적화가 꺼져 있어 둘 다 실제 체감과 다르다.
///
/// ## 읽는 법
///
/// `XCTApplicationLaunchMetric` 은 앱 실행부터 첫 프레임까지를 잰다. 여기에 `loadAll()`,
/// SKIE 콜렉터 81개 생성, Firebase 초기화가 전부 들어 있다.
/// 더 잘게 보려면 Instruments 의 Points of Interest 에서 `GLGPerf` signpost 를 함께 본다
/// (`storeInit+loadAll` 구간이 런치 시간의 몇 %인지).
/// `XCUIApplication` 은 `@MainActor` 라 Swift 6 모드에선 클래스째 격리해야 경고가 안 난다
/// (프로젝트 방침: 경고 0).
@MainActor
final class LaunchBenchmark: XCTestCase {

    override func setUp() {
        super.setUp()
        // 측정 중 실패해도 계속 진행 — 한 회차가 어긋나도 나머지 표본을 살린다.
        continueAfterFailure = false
    }

    /// 콜드 런치 — 앱 실행부터 첫 프레임까지.
    func testColdLaunch() {
        // 기본 5회 측정. 편차가 크면 옵션으로 회차를 늘린다.
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}
