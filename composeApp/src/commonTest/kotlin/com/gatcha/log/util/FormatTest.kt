package com.gatcha.log.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** :app 의 JVM String.format("%,d") 출력과 동일한지 검증 */
class FormatTest {
    @Test
    fun wonFormatsWithCommasAndSuffix() {
        assertEquals("0원", won(0))
        assertEquals("100원", won(100))
        assertEquals("1,234원", won(1234))
        assertEquals("1,234,567원", won(1234567L))
        assertEquals("-1,234원", won(-1234))
    }

    @Test
    fun numFormatsWithCommas() {
        assertEquals("0", num(0))
        assertEquals("999", num(999))
        assertEquals("1,000", num(1000))
        assertEquals("987,654,321", num(987654321L))
        assertEquals("-12,345", num(-12345))
    }
}
