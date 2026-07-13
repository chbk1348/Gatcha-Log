import SwiftUI

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

/// epoch millis(Int64) → 로컬 연/월/일. (Kotlin DateUtil 의 system tz 기준 집계와 맞춤)
enum DateMillis {
    static func comps(_ millis: Int64) -> (year: Int, month: Int, day: Int) {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000.0)
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return (c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }
    static func isSameMonth(_ millis: Int64, _ year: Int, _ month: Int) -> Bool {
        let c = comps(millis); return c.year == year && c.month == month
    }
    static func isSameYear(_ millis: Int64, _ year: Int) -> Bool {
        comps(millis).year == year
    }
}

// (제거) compactAmount — 호출부 없는 사문화 코드였고, 만 단위를 버림 처리해
// Android 의 반올림 표기(util/Format.kt wonShort)와도 어긋나 있었다. 축약 표기가 필요하면
// 공유 소스인 FormatKt.wonShort(v:) 를 쓸 것.
