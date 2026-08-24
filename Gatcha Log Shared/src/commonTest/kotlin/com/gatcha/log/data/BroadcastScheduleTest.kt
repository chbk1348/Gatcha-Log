package com.gatcha.log.data

import com.gatcha.log.data.api.BroadcastApi
import com.gatcha.log.data.api.NewsItem
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
     * 배너 — **종료일은 버전 시작 전날**이다(픽업은 점검 직전 화요일에 끝나고 버전은 수요일 시작).
     *
     * `isEndUnknown` 은 `endMillis <= 0` 에서 파생되는 값이라 직접 넣을 수 없다 — 0 을 준다.
     */
    private fun banner(endMillis: Long, game: Game = Game.GENSHIN, endUnknown: Boolean = false) = GachaBanner(
        game = game.displayName, name = "픽업", type = "character",
        endMillis = if (endUnknown) 0L else endMillis,
        startMillis = endMillis - 21 * day, version = "7.0",
    )

    private fun giBanner(endMillis: Long, endUnknown: Boolean = false) =
        banner(endMillis, Game.GENSHIN, endUnknown)

    @Test
    fun `원신 7_0 실제 사례와 맞는다`() {
        // ennead 가 준 실제 값: 배너 종료 2026-08-11 15:59(화) → 버전 시작 08-12(수)
        //                      → 실제 방송 2026-07-31(금) 21:00
        val bannerEnd = at(2026, 8, 11, hour = 15)
        val b = BroadcastSchedule.next(listOf(giBanner(bannerEnd)), nowMillis = at(2026, 7, 20))
            .firstOrNull { it.gameKey == Game.GENSHIN.key }
        assertNotNull(b)
        val dt = local(b.targetMillis)
        assertEquals(7, dt.month.number)
        assertEquals(31, dt.day)
        assertEquals(21, dt.hour)   // 점검 시각(오전)을 그대로 옮기면 안 된다 — 시각은 따로 세운다
        assertEquals(0, dt.minute)
    }

    @Test
    fun `스타레일 4_5 확정 공지와 맞는다`() {
        // 확정 공지: 2026/08/14 20:30 (한국 시간). 배너 종료는 2026-08-25 23:00(화).
        //
        // 이 검사가 이 파일에서 제일 값어치 있다 — 예전엔 배너 종료를 그대로 버전 시작으로 써서
        // 하루 앞당겨진 **목요일**이 나왔는데, 화면상으론 그럴듯해 확정 공지가 나오기 전엔 몰랐다.
        val bannerEnd = at(2026, 8, 25, hour = 23)
        val b = BroadcastSchedule.next(listOf(banner(bannerEnd, Game.HSR)), nowMillis = at(2026, 8, 10))
            .firstOrNull { it.gameKey == Game.HSR.key }
        assertNotNull(b)
        val dt = local(b.targetMillis)
        assertEquals(8, dt.month.number)
        assertEquals(14, dt.day)
        assertEquals(20, dt.hour)
        assertEquals(30, dt.minute)
    }

    @Test
    fun `역산이 흔들려도 금요일로 맞춘다`() {
        // 점검이 하루 밀려 버전 시작이 목요일이 됐다고 치자. 요일 관례는 안 바뀌므로
        // 그대로 두면 목요일 방송이라는 없는 날짜가 나간다.
        val bannerEnd = at(2026, 8, 26, hour = 23)   // +1일 → 08-27(목) 시작
        val b = BroadcastSchedule.next(listOf(giBanner(bannerEnd)), nowMillis = at(2026, 8, 1)).first()
        assertEquals(DayOfWeek.FRIDAY, local(b.targetMillis).dayOfWeek)
    }

    @Test
    fun `이번 회차가 지났으면 다음 버전 회차를 낸다`() {
        val bannerEnd = at(2026, 8, 11, hour = 15)
        // 8월 5일 = 7/31 방송이 이미 지난 시점. 그대로 내놓으면 '다음 방송'이 과거가 된다.
        val now = at(2026, 8, 5)
        val b = BroadcastSchedule.next(listOf(giBanner(bannerEnd)), nowMillis = now)
            .firstOrNull { it.gameKey == Game.GENSHIN.key }
        assertNotNull(b)
        assertTrue(b.targetMillis > now, "다음 방송은 항상 미래여야 한다")
        // 버전 길이(6주) 뒤 회차 → 9월 11일
        val dt = local(b.targetMillis)
        assertEquals(9, dt.month.number)
        assertEquals(11, dt.day)
        assertEquals(DayOfWeek.FRIDAY, dt.dayOfWeek)
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
        val s = ScheduleLogic.summarize(banners, entries, now)
        assertEquals(0, s.weekDeadlines)
        assertEquals(0, s.extras)
    }

    // ── 확정 공지 파싱 ────────────────────────────────────────────────────────

    private fun news(title: String, summary: String, game: Game = Game.HSR) = NewsItem(
        game = game.displayName, id = "1", title = title,
        createdAtMillis = 0L, bannerUrl = "", url = "https://www.hoyolab.com/article/1",
        summary = summary,
    )

    @Test
    fun `확정 공지에서 일시를 읽는다`() {
        // 실제 공지(2026-08-10 게시): 제목·본문 형식을 그대로 옮겼다.
        val item = news(
            "〈붕괴: 스타레일〉 4.5 버전 「천성에 던진 승부수」 프리뷰 스페셜 프로그램",
            "🕙 2026/08/14 20:30 (한국 시간) 〈붕괴: 스타레일〉 4.5 버전 「천성에 던진 승부수」 프리뷰 스페셜 프로그램이 방영됩니다.",
        )
        val c = BroadcastSchedule.parseConfirmed(listOf(item), nowMillis = at(2026, 8, 10)).singleOrNull()
        assertNotNull(c)
        assertEquals(Game.HSR.key, c.gameKey)
        assertEquals("4.5", c.version)
        val dt = local(c.targetMillis)
        assertEquals(8, dt.month.number)
        assertEquals(14, dt.day)
        assertEquals(20, dt.hour)
        assertEquals(30, dt.minute)
    }

    @Test
    fun `확정이 있으면 역산을 밀어낸다`() {
        val item = news(
            "〈붕괴: 스타레일〉 4.5 버전 프리뷰 스페셜 프로그램",
            "🕙 2026/08/14 20:30 (한국 시간)",
        )
        val confirmed = BroadcastSchedule.parseConfirmed(listOf(item), nowMillis = at(2026, 8, 10))
        val b = BroadcastSchedule.next(
            banners = listOf(banner(at(2026, 8, 25, hour = 23), Game.HSR)),
            confirmed = confirmed,
            nowMillis = at(2026, 8, 10),
        ).first { it.gameKey == Game.HSR.key }
        assertTrue(!b.isEstimate, "확정 공지가 있으면 '예상'이 아니다")
        assertEquals("4.5", b.version)
        assertTrue(b.noticeUrl.isNotEmpty(), "확정이면 근거 공지로 갈 수 있어야 한다")
    }

    @Test
    fun `버전 없는 스페셜은 확정으로 보지 않는다`() {
        // 젠레스 「연례 대공개」 — 제목에 '스페셜 프로그램'은 있지만 버전 방송이 아니다.
        val item = news(
            "🎁「파에톤 연례 대공개」 스페셜 프로그램 진행 중!",
            "🕙 2026/08/20 20:00 (한국 시간)", game = Game.ZZZ,
        )
        assertTrue(BroadcastSchedule.parseConfirmed(listOf(item), nowMillis = at(2026, 8, 10)).isEmpty())
    }

    @Test
    fun `방송이 아닌 버전 글은 거른다`() {
        // 「4.4 버전 프리뷰 토론실」 — 버전 번호는 있지만 방송이 아니라 부대 이벤트다.
        val item = news("[보상 이벤트] 4.4 버전 프리뷰 토론실! 기대되는 점은?", "🕙 2026/08/14 20:30")
        assertTrue(BroadcastSchedule.parseConfirmed(listOf(item), nowMillis = at(2026, 8, 10)).isEmpty())
    }

    @Test
    fun `일시가 없으면 확정하지 않는다`() {
        // 확정할 값이 없으면 역산에 맡긴다 — 제목만 보고 날짜를 지어내지 않는다.
        val item = news("〈붕괴: 스타레일〉 4.5 버전 프리뷰 스페셜 프로그램", "곧 방영됩니다. 많은 시청 바랍니다.")
        assertTrue(BroadcastSchedule.parseConfirmed(listOf(item), nowMillis = at(2026, 8, 10)).isEmpty())
    }

    @Test
    fun `지난 방송 공지는 다음 방송이 아니다`() {
        // 방송이 끝나도 공지는 목록에 한동안 남는다.
        val item = news("〈붕괴: 스타레일〉 4.4 버전 프리뷰 스페셜 프로그램", "🕙 2026/07/03 20:30 (한국 시간)")
        assertTrue(BroadcastSchedule.parseConfirmed(listOf(item), nowMillis = at(2026, 8, 10)).isEmpty())
    }

    // ── 예약 라이브(BroadcastApi) 합류 ────────────────────────────────────────

    @Test
    fun `예약 라이브만 있으면 그대로 확정이 된다`() {
        val api = listOf(
            ConfirmedBroadcast(
                gameKey = Game.HSR.key, version = "4.5",
                targetMillis = at(2026, 8, 14, hour = 20), noticeUrl = "",
                videoUrl = "https://www.youtube.com/watch?v=abc123",
            ),
        )
        val merged = BroadcastSchedule.mergeConfirmed(api, emptyList())
        val b = BroadcastSchedule.next(
            banners = listOf(banner(at(2026, 8, 25, hour = 23), Game.HSR)),
            confirmed = merged,
            nowMillis = at(2026, 8, 10),
        ).first { it.gameKey == Game.HSR.key }
        assertTrue(!b.isEstimate)
        // 영상 주소를 알면 채널 목록을 거치지 않는다 — 카드가 곧장 그 방송을 연다.
        assertEquals("https://www.youtube.com/watch?v=abc123", b.liveUrl)
    }

    @Test
    fun `영상 주소를 모르면 채널로 폴백한다`() {
        // 공지 파싱만 성공한 상태 — 예약 라이브가 아직 안 올라왔을 때가 이 모양이다.
        val notice = BroadcastSchedule.parseConfirmed(
            listOf(news("〈붕괴: 스타레일〉 4.5 버전 프리뷰 스페셜 프로그램", "🕙 2026/08/14 20:30 (한국 시간)")),
            nowMillis = at(2026, 8, 10),
        )
        val b = BroadcastSchedule.next(
            banners = listOf(banner(at(2026, 8, 25, hour = 23), Game.HSR)),
            confirmed = BroadcastSchedule.mergeConfirmed(emptyList(), notice),
            nowMillis = at(2026, 8, 10),
        ).first { it.gameKey == Game.HSR.key }
        assertTrue(b.liveUrl.contains("/streams"), "영상을 모르면 채널 라이브 탭으로 보낸다")
    }

    @Test
    fun `둘 다 있으면 영상 주소와 공지 주소를 함께 갖는다`() {
        // 예약 라이브가 시각의 기준이 되고, 공지는 자기만 아는 값(공지 주소)을 얹는다.
        val api = listOf(
            ConfirmedBroadcast(
                gameKey = Game.HSR.key, version = "", targetMillis = at(2026, 8, 14, hour = 20),
                noticeUrl = "", videoUrl = "https://www.youtube.com/watch?v=abc123",
            ),
        )
        val notice = listOf(
            ConfirmedBroadcast(
                gameKey = Game.HSR.key, version = "4.5",
                targetMillis = at(2026, 8, 14, hour = 21), noticeUrl = "https://hoyo/notice/1",
            ),
        )
        val m = BroadcastSchedule.mergeConfirmed(api, notice).single()
        assertEquals(at(2026, 8, 14, hour = 20), m.targetMillis, "시각은 예약 라이브가 기준이다")
        assertEquals("4.5", m.version, "영상 제목에서 버전을 못 뽑으면 공지가 메운다")
        assertEquals("https://hoyo/notice/1", m.noticeUrl)
        assertEquals("https://www.youtube.com/watch?v=abc123", m.videoUrl)
    }

    @Test
    fun `한 경로가 비어도 다른 게임 확정은 살아남는다`() {
        val api = listOf(
            ConfirmedBroadcast(Game.HSR.key, "4.5", at(2026, 8, 14, hour = 20), "", "https://yt/1"),
        )
        val notice = listOf(
            ConfirmedBroadcast(Game.GENSHIN.key, "7.1", at(2026, 9, 11, hour = 21), "https://hoyo/2"),
        )
        val m = BroadcastSchedule.mergeConfirmed(api, notice)
        assertEquals(2, m.size)
        assertEquals(Game.HSR.key, m.first().gameKey, "임박순으로 나온다")
    }

    @Test
    fun `예약 라이브 JSON 을 읽는다`() {
        val json = """
            {"broadcasts":[
              {"game":"hsr","version":"4.5","startMillis":1786000000000,
               "startAtKst":"2026-08-14 20:30","videoId":"abc123","title":"4.5 특별 방송"}
            ]}
        """.trimIndent()
        val list = assertNotNull(BroadcastApi.parse(json))
        val b = list.single()
        assertEquals("hsr", b.gameKey)
        assertEquals("4.5", b.version)
        assertEquals(1786000000000L, b.targetMillis)
        assertEquals("https://www.youtube.com/watch?v=abc123", b.videoUrl)
    }

    @Test
    fun `망가진 JSON 은 null 로 갈라진다`() {
        // 빈 목록(예정 방송 없음)과 실패는 다르다 — 실패면 화면이 직전 값을 지켜야 한다.
        assertNull(BroadcastApi.parse("<html>404</html>"))
        assertEquals(0, assertNotNull(BroadcastApi.parse("""{"broadcasts":[]}""")).size)
    }

    @Test
    fun `시각 없는 항목은 버린다`() {
        val json = """{"broadcasts":[{"game":"hsr","version":"4.5","videoId":"x"}]}"""
        assertTrue(assertNotNull(BroadcastApi.parse(json)).isEmpty())
    }

    @Test
    fun `본문 뒤쪽 날짜는 방송 일시로 오해하지 않는다`() {
        // 앞머리에 일시가 없고 한참 뒤에 이벤트 기간이 적힌 글 — 그 날짜를 집으면 안 된다.
        val filler = "안녕하세요 개척자님. 이번 버전에서는 다양한 콘텐츠가 준비되어 있습니다. ".repeat(3)
        val item = news("〈붕괴: 스타레일〉 4.5 버전 프리뷰 스페셜 프로그램", filler + "이벤트 기간: 2026/09/01 05:00 ~")
        assertTrue(BroadcastSchedule.parseConfirmed(listOf(item), nowMillis = at(2026, 8, 10)).isEmpty())
    }
}
