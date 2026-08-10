package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 버전 특별 방송 **예상** 일정 — [BroadcastSchedule].
 *
 * 이 값은 화면에 날짜로 나가므로 조용히 틀리면 알아채기 어렵다. 특히 "이미 지난 회차를
 * 다음 방송이라고 내놓는" 실수는 화면상 멀쩡해 보인다(날짜가 그럴듯하다). 여기서 못 박는다.
 */
@OptIn(ExperimentalTime::class)
class BroadcastScheduleTest {

    private val tz = TimeZone.currentSystemDefault()
    private val day = 86_400_000L

    private fun at(year: Int, month: Int, d: Int, hour: Int = 12): Long =
        LocalDateTime(year, month, d, hour, 0).toInstant(tz).toEpochMilliseconds()

    private fun local(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)

    /**
     * 원신 배너 — 이 페이즈 종료일이 곧 다음 버전 시작일이다.
     *
     * `isEndUnknown` 은 `endMillis <= 0` 에서 파생되는 값이라 직접 넣을 수 없다 — 0 을 준다.
     */
    private fun giBanner(endMillis: Long, endUnknown: Boolean = false) = GachaBanner(
        game = Game.GENSHIN.displayName, name = "픽업", type = "character",
        endMillis = if (endUnknown) 0L else endMillis,
        startMillis = endMillis - 21 * day, version = "7.0",
    )

    @Test
    fun `버전 시작 12일 전 저녁으로 잡는다`() {
        // 실제 사례: 원신 7.0 은 2026-08-12 시작, 특별 방송은 2026-07-31 21:00 이었다.
        val versionStart = at(2026, 8, 12, hour = 11)   // 점검 후 오전 시작
        val b = BroadcastSchedule.next(listOf(giBanner(versionStart)), nowMillis = at(2026, 7, 20))
            .firstOrNull { it.gameKey == Game.GENSHIN.key }
        assertNotNull(b)
        val dt = local(b.targetMillis)
        assertEquals(7, dt.month.number)
        assertEquals(31, dt.day)
        // 점검 시각(오전)을 그대로 12일 전으로 옮기면 안 된다 — 방송 시각은 따로 세운다.
        assertEquals(21, dt.hour)
    }

    @Test
    fun `이번 회차가 지났으면 다음 버전 회차를 낸다`() {
        val versionStart = at(2026, 8, 12, hour = 11)
        // 8월 5일 = 7/31 방송이 이미 지난 시점. 그대로 내놓으면 '다음 방송'이 과거가 된다.
        val now = at(2026, 8, 5)
        val b = BroadcastSchedule.next(listOf(giBanner(versionStart)), nowMillis = now)
            .firstOrNull { it.gameKey == Game.GENSHIN.key }
        assertNotNull(b)
        assertTrue(b.targetMillis > now, "다음 방송은 항상 미래여야 한다")
        // 버전 길이(6주) 뒤 회차 → 9월 11일
        val dt = local(b.targetMillis)
        assertEquals(9, dt.month.number)
        assertEquals(11, dt.day)
    }

    @Test
    fun `배너가 없는 게임은 내놓지 않는다`() {
        // 버전 시작일을 모르면 역산의 근거가 없다 — 추측해서 만들어내지 않는다.
        assertTrue(BroadcastSchedule.next(emptyList(), nowMillis = at(2026, 8, 5)).isEmpty())
    }

    @Test
    fun `종료 미정 픽업만 있으면 근거가 못 된다`() {
        // 콜라보처럼 종료가 미공지인 배너는 버전 경계를 만들지 못한다.
        val only = giBanner(at(2026, 8, 12), endUnknown = true)
        assertTrue(BroadcastSchedule.next(listOf(only), nowMillis = at(2026, 8, 5)).isEmpty())
    }

    @Test
    fun `가장 늦은 페이즈 종료를 버전 시작으로 본다`() {
        // 전반(3주 뒤)·후반(6주 뒤)이 있으면 버전이 끝나는 건 후반 종료 시점이다.
        val first = giBanner(at(2026, 7, 22, hour = 11))
        val second = giBanner(at(2026, 8, 12, hour = 11))
        val b = BroadcastSchedule.next(listOf(first, second), nowMillis = at(2026, 7, 20))
            .firstOrNull { it.gameKey == Game.GENSHIN.key }
        assertNotNull(b)
        val dt = local(b.targetMillis)
        assertEquals(7, dt.month.number)
        assertEquals(31, dt.day)   // 8/12 기준. 7/22 를 썼다면 7/10 이라 이미 지났다.
    }

    @Test
    fun `예상이라는 사실이 값에 남는다`() {
        val b = BroadcastSchedule.next(listOf(giBanner(at(2026, 8, 12))), nowMillis = at(2026, 7, 20)).first()
        assertTrue(b.isEstimate, "지금은 전부 역산값이다 — 화면이 '예상'을 표시하는 근거")
        assertTrue(b.liveUrl.startsWith("https://www.youtube.com/channel/"))
    }

    @Test
    fun `방송은 일정 타임라인에 섞이지 않는다`() {
        // 타임라인은 '언제 끝나나'를 읽는 자리다. 예상값인 방송이 확정된 마감들 사이에 끼면
        // 같은 무게로 읽힌다 — 별도 탭으로 뺐고, 그 경계를 여기서 지킨다.
        val entries = ScheduleLogic.buildSchedule(
            banners = listOf(giBanner(at(2026, 8, 12))),
            events = emptyList(),
            challenges = emptyList(),
        )
        assertTrue(entries.none { it.kind == "방송" })
    }

    @Test
    fun `방송은 요약 숫자에도 잡히지 않는다`() {
        val banners = listOf(giBanner(at(2026, 8, 12)))
        val now = at(2026, 7, 26)   // 방송(7/31)이 일주일 안쪽인 시점
        val entries = ScheduleLogic.buildSchedule(banners, emptyList(), emptyList())
        val s = ScheduleLogic.summarize(banners, entries, "all", now)
        assertEquals(0, s.weekDeadlines)
        assertEquals(0, s.extras)
    }
}
