package com.gatcha.log.util

/** 통화 표기 — ₩1,234 */
fun won(n: Long): String = "₩%,d".format(n)
fun won(n: Int): String = "₩%,d".format(n)

/** 천 단위 구분 숫자 — 1,234 */
fun num(n: Long): String = "%,d".format(n)
fun num(n: Int): String = "%,d".format(n)
