import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 포맷 헬퍼 — 금액(원) 표기, 게임 ARGB(Long) → Color.
// ════════════════════════════════════════════════════════════════════════════

private let wonFormatter: NumberFormatter = {
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.groupingSeparator = ","
    f.maximumFractionDigits = 0
    return f
}()

/// 금액을 "1,234원" 형식으로. (Kotlin util.won 과 동일 표기)
func won(_ amount: Int64) -> String {
    let n = wonFormatter.string(from: NSNumber(value: amount)) ?? "\(amount)"
    return "\(n)원"
}

func won(_ amount: Int) -> String { won(Int64(amount)) }

/// 천 단위 콤마 숫자(원 없음) — Kotlin util.num 대응.
func num(_ v: Int64) -> String { wonFormatter.string(from: NSNumber(value: v)) ?? "\(v)" }
func num(_ v: Int) -> String { num(Int64(v)) }

/// 현재 epoch millis — Kotlin dDay/dDayLabel(nowMillis:) 호출용.
func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

/// 소수 n자리 포맷 — Kotlin util.fixed 대응.
func fixed(_ v: Double, _ n: Int) -> String { String(format: "%.\(n)f", v) }

/// 게임 목록 — **앱 수명 동안 한 번만** Kotlin 에서 받아 둔다.
///
/// `GameData.shared.games` 는 접근할 때마다 브리지를 건너간다. 그런데 이걸 `ForEach` 본문이나
/// computed 프로퍼티에 두는 화면이 많아서(칩 목록·게임 세그먼트·출석 스트립) body 평가마다 반복됐다.
/// 목록 자체는 컴파일 타임 고정이라 변하지 않는다.
enum GLGGames {
    static let all: [Game] = GameData.shared.games
    static let keys: [String] = all.map { $0.key }
    /// 출석/실시간 노트 지원 게임(호요버스).
    static let attendance: [Game] = GameData.shared.attendanceGames
}

/// 가챠 리포트 게임 키(genshin/starrail/zzz) → (약칭, 색).
func gachaGameInfo(_ key: String) -> (short: String, color: Color) {
    switch key {
    case "genshin": return ("원신", Color(hex: 0xFF4F8EF7))
    case "starrail", "hsr": return ("스타레일", Color(hex: 0xFFB06BFF))
    case "zzz": return ("젠레스", Color(hex: 0xFFF5A623))
    default: return (key, Color(hex: 0xFF888888))
    }
}

extension Color {
    /// Kotlin Game.color / Spending.gameColor → Color.
    /// 데이터 모델 색상은 평범한 ARGB Long(0xAARRGGBB, 하위 32비트) — SKIE 로 Int64 로 전달됨.
    /// (구 Compose `Color.value` 상위 32비트 패킹은 de-Color 작업으로 제거됨)
    init(argb64: Int64) {
        self.init(hex: UInt32(truncatingIfNeeded: argb64))
    }
}

/// epoch millis(Int64) → 로컬 연/월/일.
/// 계산 자체는 commonMain(DateUtil.kt)이 단일 소스 — Swift 는 Int32 변환만 감싼다.
/// (예전엔 Calendar.current 로 따로 구현해 두 플랫폼의 월 경계 판정이 갈릴 수 있었다)
enum DateMillis {
    /// 브리지 **1회** + 시각→로컬 변환 1회.
    ///
    /// 예전엔 `year`·`month`·`dayOfMonth` 를 따로 불러 각각 3회였다. 이 함수는 지출 전체를 도는
    /// 루프 안에서 불리므로(캘린더 타임라인·연간 리포트) 지출 1,000건이면 브리지가 3,000회였다.
    /// 공유 쪽 `DateUtil.ymd` 가 연·월·일을 한 값(yyyyMMdd)으로 돌려준다.
    static func comps(_ millis: Int64) -> (year: Int, month: Int, day: Int) {
        let k = Int(DateUtil.shared.ymd(millis: millis))
        return (k / 10_000, k / 100 % 100, k % 100)
    }
    static func isSameMonth(_ millis: Int64, _ year: Int, _ month: Int) -> Bool {
        DateUtil.shared.isSameMonth(millis: millis, year: Int32(year), month: Int32(month))
    }
    static func isSameYear(_ millis: Int64, _ year: Int) -> Bool {
        DateUtil.shared.isSameYear(millis: millis, year: Int32(year))
    }
}

// (제거) compactAmount — 호출부 없는 사문화 코드였고, 만 단위를 버림 처리해
// Android 의 반올림 표기(util/Format.kt wonShort)와도 어긋나 있었다. 축약 표기가 필요하면
// 공유 소스인 FormatKt.wonShort(v:) 를 쓸 것.
