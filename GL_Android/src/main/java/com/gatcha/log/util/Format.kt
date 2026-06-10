package com.gatcha.log.util

/** 통화 표기 — 1,234원 (콤마 + 한국 "원") */
fun won(n: Long): String = "%,d원".format(n)
fun won(n: Int): String = "%,d원".format(n)

/** 천 단위 구분 숫자 — 1,234 */
fun num(n: Long): String = "%,d".format(n)
fun num(n: Int): String = "%,d".format(n)
