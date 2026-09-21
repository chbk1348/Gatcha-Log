package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeLogTest {

    /**
     * 「최신 버전」 카드는 **가장 높은 버전 하나에만** 붙는다.
     *
     * 손으로 붙이던 시절에는 새 엔트리를 쓸 때 옮기는 걸 잊어 27.50.2 · 27.50.5 가 나간 뒤에도
     * 27.50.0 에 붙어 있었다.
     */
    @Test
    fun onlyHighestVersionIsFeatured() {
        val featured = ChangeLog.entries.filter { it.featured }
        assertEquals(1, featured.size, "「최신 버전」 카드가 ${featured.size}개다")
        assertEquals(ChangeLog.entries.maxOf { it.versionCode }, featured.single().versionCode)
    }
}
