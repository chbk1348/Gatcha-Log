package com.gatcha.log.data.api

import com.gatcha.log.data.Game
import com.gatcha.log.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 명조 게임 일정 — 공지 피드(`notice.json`)에서 무엇을 일정으로 볼지.
 *
 * 이 피드는 **일정표가 아니라 공지 목록**이다. 운영 공지·상시 시스템·이미 끝난 것이 한데 섞여
 * 오는데 전부 `endTimeMs` 를 갖고 있어서, 날짜가 있다는 이유로 다 올리면 주간 보드가 4년 뒤
 * 마감으로 채워진다. 그 경계를 여기 고정한다.
 */
class WuwaEventsTest {

    private val now = 1_755_000_000_000L   // 2026-08-12 즈음

    private fun feed(vararg items: String) = JSONObject(
        """{"game":[${items.joinToString(",")}],"activity":[]}"""
    )

    private fun item(
        title: String = "[이벤트] 테스트",
        category: Int = 2,
        permanent: Int = 0,
        start: Long = now - 5L * 24 * 3600 * 1000,
        end: Long = now + 10L * 24 * 3600 * 1000,
    ) = """{"id":"x","category":$category,"permanent":$permanent,
           "startTimeMs":$start,"endTimeMs":$end,
           "tabTitle":{"ko":"$title"}}"""

    @Test
    fun 게임_내_이벤트만_올린다() {
        val out = WuwaNewsApi.parseEvents(
            feed(item(title = "[수집] 이벤트", category = 2), item(title = "점검 사전 공지", category = 1)),
            now,
        )
        assertEquals(1, out.size)
        assertEquals("[수집] 이벤트", out[0].name)
        assertEquals(Game.WUWA.displayName, out[0].game)
    }

    @Test
    fun 상시_고정은_뺀다() {
        assertTrue(WuwaNewsApi.parseEvents(feed(item(permanent = 1)), now).isEmpty())
    }

    @Test
    fun 이미_끝난_것은_뺀다() {
        val past = now - 1L * 24 * 3600 * 1000
        assertTrue(WuwaNewsApi.parseEvents(feed(item(start = past - 100, end = past)), now).isEmpty())
    }

    @Test
    fun 너무_긴_것은_상시_시스템으로_보고_뺀다() {
        val start = now - 100L * 24 * 3600 * 1000
        val end = now + 200L * 24 * 3600 * 1000     // 300일
        assertTrue(WuwaNewsApi.parseEvents(feed(item(start = start, end = end)), now).isEmpty())
    }

    @Test
    fun 경계_150일은_남긴다() {
        val start = now
        val end = now + 150L * 24 * 3600 * 1000
        assertEquals(1, WuwaNewsApi.parseEvents(feed(item(start = start, end = end)), now).size)
    }

    @Test
    fun 한국어_번역이_없으면_뺀다() {
        val en = """{"id":"y","category":2,"permanent":0,
                     "startTimeMs":${now - 1000},"endTimeMs":${now + 1000},
                     "tabTitle":{"en":"Limited Event"}}"""
        assertTrue(WuwaNewsApi.parseEvents(feed(en), now).isEmpty())
    }

    @Test
    fun 제목의_줄바꿈은_한_줄로_편다() {
        val out = WuwaNewsApi.parseEvents(feed(item(title = "[무무기획] \\n기간 한정 의뢰")), now)
        assertEquals(1, out.size)
        assertTrue('\n' !in out[0].name, "줄바꿈이 남았다: ${out[0].name}")
    }

    @Test
    fun 마감이_빠른_순으로_정렬한다() {
        val d = 24L * 3600 * 1000
        val out = WuwaNewsApi.parseEvents(
            feed(item(title = "늦게", end = now + 20 * d), item(title = "빨리", end = now + 3 * d)),
            now,
        )
        assertEquals(listOf("빨리", "늦게"), out.map { it.name })
    }

    @Test
    fun 시작_시각을_그대로_싣는다() {
        val start = now - 3L * 24 * 3600 * 1000
        val out = WuwaNewsApi.parseEvents(feed(item(start = start)), now)
        assertEquals(start, out[0].startMillis)
    }
}
