package com.gatcha.log.util

// :app 의 Format.kt 와 동일한 API/출력.
// 단, String.format("%,d") 는 JVM 전용이라 KMP 공통 코드용 순수 Kotlin 구현으로 대체.

/** 통화 표기 — 1,234원 (콤마 + 한국 "원") */
fun won(n: Long): String = "${n.withCommas()}원"
fun won(n: Int): String = won(n.toLong())

/** 천 단위 구분 숫자 — 1,234 */
fun num(n: Long): String = n.withCommas()
fun num(n: Int): String = num(n.toLong())

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
