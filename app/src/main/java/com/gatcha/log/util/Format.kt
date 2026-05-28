package com.gatcha.log.util

/** 통화 표기: ₩1,234 */
fun won(value: Long): String = "₩%,d".format(value)

fun won(value: Int): String = won(value.toLong())

/** 천 단위 구분 숫자: 1,234 */
fun num(value: Long): String = "%,d".format(value)

fun num(value: Int): String = num(value.toLong())
