package com.gatcha.log.data

import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.data.api.GiftCodeApi
import kotlin.test.Test
import kotlin.test.assertEquals

/** 공방 묶음 추정 — [GiftCodeApi.spreadLivestream]. 보상이 늦게 채워지는 코드도 같은 묶음이면 처음부터 공방이다. */
class GiftCodeLivestreamTest {

    @Test
    fun `같은 묶음의 보상 없는 코드도 공방이 된다`() {
        val codes = listOf(
            GiftCode("NEEIJB764CA3", "원석 ×100", highlight = true),
            GiftCode("NEEI8A8341OW", "", highlight = false),
            GiftCode("6LS3F3LS5K87", "원석 ×20", highlight = false),
        )
        val out = GiftCodeApi.spreadLivestream(codes).associate { it.code to it.highlight }
        assertEquals(true, out["NEEI8A8341OW"])
        assertEquals(false, out["6LS3F3LS5K87"])
    }

    @Test
    fun `이름 붙은 코드는 묶지 않는다`() {
        val codes = listOf(
            GiftCode("GENSHINGIFT1", "원석 ×100", highlight = true),
            GiftCode("GENSHINGIFT", "원석 ×50", highlight = false),
            GiftCode("GENS12345678", "", highlight = false),
        )
        val out = GiftCodeApi.spreadLivestream(codes).associate { it.code to it.highlight }
        assertEquals(false, out["GENSHINGIFT"])
        // 12자리 영숫자끼리는 앞 4자리가 같으면 묶인다 — 이름 붙은 12자리 코드도 꼴이 같으면 묶인다.
        assertEquals(true, out["GENS12345678"])
    }

    @Test
    fun `공방이 없으면 그대로`() {
        val codes = listOf(GiftCode("NEEI8A8341OW", ""), GiftCode("NEEIJB764CA3", "원석 ×60"))
        assertEquals(codes, GiftCodeApi.spreadLivestream(codes))
    }
}
