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
 * 앱 공지 파싱 — [AppNoticeApi.parse].
 *
 * 공지는 **떠야 할 때 안 뜨는 것**과 **내려야 할 때 남는 것** 둘 다 사고다. 어드민 검증
 * (`admin.js` 의 NOTICES.validate)이 짚는 지점과 여기가 버리는 지점이 같아야 한다.
 */
class AppNoticeApiTest {

    @OptIn(ExperimentalTime::class)
    private fun at(kst: String): Long =
        LocalDateTime.parse(kst.replace(" ", "T")).toInstant(TimeZone.of("Asia/Seoul")).toEpochMilliseconds()

    private val now = at("2026-10-06 12:00")

    private fun titles(json: String, platform: String = "android"): List<String> =
        assertNotNull(AppNoticeApi.parse(json, now, platform)).map { it.title }

    @Test
    fun 기간_안의_공지만_뜬다() {
        val json = """
            { "notices": [
              { "title": "진행 중", "start": "2026-10-06 09:00", "end": "2026-10-06 18:00" },
              { "title": "아직",   "start": "2026-10-07 09:00", "end": "2026-10-07 18:00" },
              { "title": "끝남",   "start": "2026-10-05 09:00", "end": "2026-10-06 12:00" }
            ] }
        """
        assertEquals(listOf("진행 중"), titles(json), "종료 시각 정각에는 이미 내려가 있어야 한다")
    }

    @Test
    fun 빈_기간은_지금부터_내릴_때까지() {
        val json = """{ "notices": [ { "title": "상시" }, { "title": "끝만", "end": "2026-10-07 00:00" } ] }"""
        assertEquals(listOf("상시", "끝만"), titles(json))
    }

    @Test
    fun 적혀_있는데_못_읽는_기간은_띄우지_않는다() {
        // 기간을 모르는 공지를 무기한 띄우느니 안 띄운다 — 어드민은 이 줄을 오류로 잡는다.
        val json = """
            { "notices": [
              { "title": "꼴이 다름", "end": "10월 7일" },
              { "title": "없는 날짜", "start": "2026-02-30 00:00" }
            ] }
        """
        assertTrue(titles(json).isEmpty())
    }

    @Test
    fun 제목이_없으면_버린다() {
        assertTrue(titles("""{ "notices": [ { "title": "  ", "body": "본문만" } ] }""").isEmpty())
    }

    @Test
    fun 플랫폼이_맞는_기기에만_뜬다() {
        val json = """
            { "notices": [
              { "title": "모두" },
              { "title": "모두2", "platform": "all" },
              { "title": "안드", "platform": "Android" },
              { "title": "아이폰", "platform": "ios" },
              { "title": "오타", "platform": "andriod" }
            ] }
        """
        assertEquals(listOf("모두", "모두2", "안드"), titles(json, "android"))
        assertEquals(listOf("모두", "모두2", "아이폰"), titles(json, "ios"))
    }

    @Test
    fun 무게를_모르면_안내로_띄운다() {
        val list = assertNotNull(AppNoticeApi.parse(
            """{ "notices": [ { "title": "a", "level": "warn" }, { "title": "b", "level": "URGENT" }, { "title": "c", "level": "??" }, { "title": "d" } ] }""",
            now, "android",
        ))
        assertEquals(
            listOf(AppNoticeLevel.WARN, AppNoticeLevel.URGENT, AppNoticeLevel.INFO, AppNoticeLevel.INFO),
            list.map { it.level },
        )
    }

    @Test
    fun 버튼_글자가_비면_자세히() {
        val list = assertNotNull(AppNoticeApi.parse(
            """{ "notices": [ { "title": "a", "url": " https://example.com " }, { "title": "b", "url": "https://example.com", "cta": "공지 보기" } ] }""",
            now, "android",
        ))
        assertEquals("https://example.com", list[0].url)
        assertEquals("자세히", list[0].cta)
        assertEquals("공지 보기", list[1].cta)
    }

    @Test
    fun 빈_목록은_공지_없음이고_깨진_JSON_만_null() {
        // 둘을 가르지 않으면 어드민에서 공지를 다 내려도 앱이 정본으로 내려가 옛 공지를 다시 띄운다.
        assertEquals(emptyList(), AppNoticeApi.parse("""{ "notices": [] }""", now, "android"))
        assertEquals(emptyList(), AppNoticeApi.parse("""{}""", now, "android"))
        assertNull(AppNoticeApi.parse("""{ "notices": [ """, now, "android"))
    }
}
