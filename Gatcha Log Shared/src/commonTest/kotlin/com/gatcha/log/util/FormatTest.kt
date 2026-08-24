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

    // ── 퍼센트 배분 ─────────────────────────────────────────────────────────
    // 각자 내림하면 조각 수만큼 오차가 쌓여 합이 100에 못 미친다(도넛이 97%로 보이던 버그).

    @Test
    fun percentSharesAlwaysSumTo100() {
        val cases = listOf(
            listOf(1L, 1L, 1L),                 // 33.3 × 3
            listOf(100L, 100L, 100L, 100L, 100L, 100L, 100L),
            listOf(5900L, 12000L, 25000L),
            listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L),
            listOf(999999L, 1L),
        )
        for (v in cases) {
            assertEquals(100, percentShares(v).sum(), "합이 100이 아님: $v → ${percentShares(v)}")
        }
    }

    @Test
    fun percentSharesGivesRemainderToLargestFraction() {
        // 33.33 × 3 → 33·33·33 = 99. 남은 1은 소수부가 가장 큰(=동률이면 앞선) 조각으로.
        assertEquals(listOf(34, 33, 33), percentShares(listOf(1L, 1L, 1L)))
    }

    @Test
    fun percentSharesKeepsInputOrder() {
        // 순서를 뒤섞지 않는다 — 화면의 조각 순서와 1:1로 맞아야 한다.
        val out = percentShares(listOf(50L, 30L, 20L))
        assertEquals(listOf(50, 30, 20), out)
    }

    @Test
    fun percentSharesHandlesZeroAndEmpty() {
        assertEquals(emptyList(), percentShares(emptyList()))
        assertEquals(listOf(0, 0), percentShares(listOf(0L, 0L)))
        // 음수는 0으로 본다(있어선 안 되지만 터지지는 않게)
        assertEquals(100, percentShares(listOf(-5L, 10L)).sum())
    }
}
