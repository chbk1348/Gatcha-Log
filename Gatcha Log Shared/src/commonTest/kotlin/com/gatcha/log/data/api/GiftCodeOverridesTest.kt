package com.gatcha.log.data.api

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * 리딤코드 어드민 보정 — [GiftCodeApi.parseOverrides] · [GiftCodeApi.merge].
 *
 * 보정은 자동 수집이 틀렸을 때 쓰는 마지막 손이라, 여기가 어긋나면 고칠 길이 앱 업데이트뿐이다.
 * 어드민 검증(`admin.js` 의 GIFT_CODES.validate)이 짚는 지점과 여기가 버리는 지점이 같아야 한다.
 */
class GiftCodeOverridesTest {

    @OptIn(ExperimentalTime::class)
    private fun at(kst: String): Long =
        LocalDateTime.parse(kst.replace(" ", "T")).toInstant(TimeZone.of("Asia/Seoul")).toEpochMilliseconds()

    private val now = at("2026-10-06 12:00")

    private fun parse(json: String) = assertNotNull(GiftCodeApi.parseOverrides(json, now))

    @Test
    fun 게임별로_묶이고_코드는_대문자로_맞춘다() {
        val o = parse("""
            { "codes": [
              { "game": "genshin", "code": " abc123 ", "rewards": "원석 ×60", "highlight": true },
              { "game": "HSR", "code": "STAR1", "rewards": "성옥 ×50" },
              { "game": "genshin", "code": "GI2" }
            ] }
        """)
        assertEquals(listOf("ABC123", "GI2"), o.manual["genshin"]?.map { it.code })
        assertEquals(GiftCode("ABC123", "원석 ×60", true), o.manual["genshin"]?.first())
        assertEquals(listOf("STAR1"), o.manual["hsr"]?.map { it.code })
        assertNull(o.manual["zzz"])
    }

    @Test
    fun 모르는_게임_빈_코드_지난_코드는_버린다() {
        val o = parse("""
            { "codes": [
              { "game": "wuwa", "code": "X1" },
              { "game": "zzz", "code": "  " },
              { "game": "zzz", "code": "OLD", "end": "2026-10-06 12:00" },
              { "game": "zzz", "code": "BAD", "end": "내일까지" },
              { "game": "zzz", "code": "LIVE", "end": "2026-10-06 12:01" },
              { "game": "zzz", "code": "OPEN", "end": "" }
            ] }
        """)
        assertEquals(setOf("zzz"), o.manual.keys)
        assertEquals(listOf("LIVE", "OPEN"), o.manual["zzz"]?.map { it.code })
    }

    @Test
    fun 숨김은_대문자로_모으고_빈_줄은_뺀다() {
        assertEquals(setOf("DEAD1", "DEAD2"), parse("""{ "hidden": [" dead1 ", "", "DEAD2"] }""").hidden)
    }

    @Test
    fun 빈_문서는_보정_없음이고_깨진_JSON_만_null() {
        val o = parse("{}")
        assertTrue(o.manual.isEmpty() && o.hidden.isEmpty())
        assertNull(GiftCodeApi.parseOverrides("""{ "codes": [ """, now))
    }

    // ── 병합 ───────────────────────────────────────────────────────────────

    private val auto = listOf(GiftCode("AUTO1", "원석 ×60"), GiftCode("SAME", "외 1종"))

    @Test
    fun 직접_넣은_코드가_앞에_서고_같은_코드는_어드민_표기가_이긴다() {
        val o = GiftCodeOverrides(manual = mapOf("genshin" to listOf(GiftCode("SAME", "원석 ×300", true), GiftCode("MAN1", ""))))
        assertEquals(
            listOf(GiftCode("SAME", "원석 ×300", true), GiftCode("MAN1", ""), GiftCode("AUTO1", "원석 ×60")),
            GiftCodeApi.merge(auto, o, "genshin"),
        )
        assertEquals(auto, GiftCodeApi.merge(auto, o, "hsr"), "다른 게임의 보정이 섞였다")
    }

    @Test
    fun 숨긴_코드는_어느_쪽에서_왔든_빠진다() {
        val o = GiftCodeOverrides(manual = mapOf("genshin" to listOf(GiftCode("MAN1", ""))), hidden = setOf("AUTO1", "MAN1"))
        assertEquals(listOf("SAME"), GiftCodeApi.merge(auto, o, "genshin")?.map { it.code })
    }

    @Test
    fun 수집이_실패해도_직접_넣은_코드가_있으면_목록이_선다() {
        val o = GiftCodeOverrides(manual = mapOf("genshin" to listOf(GiftCode("MAN1", "원석 ×60"))))
        assertEquals(listOf("MAN1"), GiftCodeApi.merge(null, o, "genshin")?.map { it.code })
        // 둘 다 없으면 실패로 남긴다 — 화면이 「못 불러왔어요」 와 재시도를 세우는 신호다.
        assertNull(GiftCodeApi.merge(null, o, "hsr"))
        assertNull(GiftCodeApi.merge(null, GiftCodeOverrides(), "genshin"))
    }

    @Test
    fun 수집은_됐는데_코드가_없으면_빈_목록이다() {
        assertEquals(emptyList(), GiftCodeApi.merge(emptyList(), GiftCodeOverrides(), "genshin"))
    }
}
