package com.gatcha.log.util

// :app 의 Format.kt 와 동일한 API/출력.
// 단, String.format("%,d") 는 JVM 전용이라 KMP 공통 코드용 순수 Kotlin 구현으로 대체.

/** 통화 표기 — 1,234원 (콤마 + 한국 "원") */
fun won(n: Long): String = "${n.withCommas()}원"
fun won(n: Int): String = won(n.toLong())

/** 천 단위 구분 숫자 — 1,234 */
fun num(n: Long): String = n.withCommas()
fun num(n: Int): String = num(n.toLong())

/**
 * 값 목록을 **합이 정확히 100이 되는 정수 퍼센트**로 바꾼다 (최대 잔여법, Largest Remainder).
 *
 * 각 조각을 따로 내림하면 조각 수만큼 오차가 쌓여 합이 100에 못 미친다 —
 * 게임별 지출 도넛에서 조각 3개일 때 97%로 보이던 게 이것이다.
 * 내림한 뒤 남은 몫을 **소수부가 큰 순서**로 1씩 나눠 준다.
 *
 * 입력 순서 그대로 돌려준다. 합이 0이거나 목록이 비면 전부 0.
 */
fun percentShares(values: List<Long>): List<Int> {
    val total = values.sumOf { if (it > 0) it else 0L }
    if (total <= 0L) return List(values.size) { 0 }
    val exact = values.map { (if (it > 0) it else 0L) * 100.0 / total }
    val floors = exact.map { it.toInt() }.toMutableList()
    var remainder = 100 - floors.sum()
    if (remainder > 0) {
        // 소수부 큰 순서로 1씩. 같으면 앞선(=금액이 큰) 조각이 먼저 받는다.
        exact.indices.sortedByDescending { exact[it] - floors[it] }
            .take(remainder)
            .forEach { floors[it]++ }
        remainder = 0
    }
    return floors
}

/**
 * 고정 소수점 표기 — JVM 의 "%.Nf".format(value) 대체 (KMP 공통).
 * 반올림(half-up) 후 소수 [digits]자리까지 0 패딩. 예: fixed(12.3456, 2) → "12.35".
 */
fun fixed(value: Double, digits: Int): String {
    if (value.isNaN()) return "NaN"
    val negative = value < 0
    val abs = if (negative) -value else value
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val scaled = kotlin.math.round(abs * factor).toLong()
    val intPart = scaled / factor
    val sign = if (negative && scaled != 0L) "-" else ""
    if (digits == 0) return "$sign$intPart"
    val fracPart = (scaled % factor).toString().padStart(digits, '0')
    return "$sign$intPart.$fracPart"
}

/**
 * 축약 통화 — 10,000 이상은 "1.3만"(반올림), 미만은 "5,000원".
 * 가챠 리포트 '5성 단가' 등 좁은 칸용. 양 플랫폼이 이 함수를 공유한다(표기 갈림 방지).
 */
fun wonShort(v: Long): String = if (v >= 10_000) "${fixed(v / 10_000.0, 1)}만" else won(v)

/** 가챠 리포트 게임 키(genshin/hsr/starrail/zzz) → 약칭(GI/HSR/ZZZ). */
fun gachaAbbr(key: String): String = when (key) {
    "genshin" -> "GI"
    "hsr", "starrail" -> "HSR"
    "zzz" -> "ZZZ"
    else -> key.uppercase()
}

/** 천 단위 콤마 삽입 (예: -1234567 → "-1,234,567") */
private fun Long.withCommas(): String {
    val negative = this < 0
    val digits = (if (negative) -this else this).toString()
    val sb = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return if (negative) "-$sb" else sb.toString()
}
