import OSLog

/// 성능 계측용 signpost.
///
/// ## 왜 필요한가
///
/// iOS 성능 작업 1·2라운드는 전부 코드 판독 기반이었고 Instruments 실측이 **0건**이다.
/// 어디가 느린지 추정만 있고, 그래서 "가장 큰 항목"의 순서조차 근거가 없었다.
///
/// signpost 는 Instruments(Points of Interest)에서 구간으로 보이고, `XCTOSSignpostMetric` 으로
/// XCTest 측정 대상이 될 수도 있다. 릴리스에도 남기지만 **활성화되지 않은 signpost 는
/// 사실상 공짜다**(로그 시스템이 구독자가 없으면 즉시 반환) — 그래서 `#if DEBUG` 로 가리지 않는다.
/// 계측을 조건부로 두면 정작 재고 싶은 릴리스 빌드에서 못 재게 된다.
///
/// ## 쓰는 법
///
/// ```swift
/// GLGPerf.interval("loadAll") { store.reload() }
/// ```
///
/// Instruments → Points of Interest 에서 `com.gatcha.log.ios` / `perf` 를 본다.
enum GLGPerf {
    static let log = OSSignposter(subsystem: "com.gatcha.log.ios", category: "perf")

    /// 이름 붙은 구간을 잰다. 반환값·throw 는 그대로 통과시킨다.
    @inline(__always)
    static func interval<T>(_ name: StaticString, _ body: () throws -> T) rethrows -> T {
        let state = log.beginInterval(name)
        defer { log.endInterval(name, state) }
        return try body()
    }

    /// 한 점(순간) 표시 — 구간이 아니라 "여기를 지나갔다"를 남길 때.
    @inline(__always)
    static func event(_ name: StaticString) {
        log.emitEvent(name)
    }
}
