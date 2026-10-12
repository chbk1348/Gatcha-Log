package com.gatcha.log.data

import com.gatcha.log.data.api.HtmlNews
import com.gatcha.log.data.api.NewsBlock
import com.gatcha.log.util.fixed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 27.52.0 에서 고친 계산들 — 전부 "경계에서 한 칸 틀리던" 것이라 리뷰로는 다시 잡기 어렵다.
 * 시각은 기기 시간대에 기대지 않도록 [DateUtil.localTimeOnDay] 로 "그날 10시"를 만들어 쓴다.
 */
class CalcFixes2752Test {
    private val hour = 3_600_000L
    private val ten = DateUtil.localTimeOnDay(1_800_000_000_000L, 10)

    // ── D-day: 달력 날짜 차이 ────────────────────────────────────────────────

    @Test
    fun 오늘_저녁_마감은_아침에_봐도_D_DAY() {
        // 예전엔 8시간 남은 것을 24시간으로 나눠 올려 D-1 이었고, 알림이 "내일"이라고 했다.
        assertEquals(0, DateUtil.daysLeft(ten + 8 * hour, ten))
    }

    @Test
    fun 내일_저녁_마감은_D_1() {
        // 32시간 남음 — 예전엔 올림이라 D-2 였다.
        assertEquals(1, DateUtil.daysLeft(DateUtil.startOfDayPlus(ten, 1) + 18 * hour, ten))
    }

    @Test
    fun 끝난_것은_같은_날이어도_음수다() {
        // 예전엔 ceil(-0.04) = 0 이라 끝난 뒤 하루 동안 D-DAY 로 남았다.
        assertTrue(DateUtil.daysLeft(ten - hour, ten) < 0)
        val ended = GachaBanner(game = Game.GENSHIN.displayName, name = "지남", endMillis = ten - hour)
        assertEquals("종료", ended.dDayLabel(ten))
        assertTrue(HomeLogic.soonBanners(listOf(ended), ten).isEmpty())
    }

    @Test
    fun 한_시간이_안_남으면_분으로_말한다() {
        assertEquals("40분", dhLabel(ten + 40 * 60_000L, ten))
        assertEquals("1시간", dhLabel(ten + hour, ten))
    }

    // ── 행동력 ──────────────────────────────────────────────────────────────

    @Test
    fun 가득_찰_시각이_지났으면_가득으로_본다() {
        val note = LiveNote(game = Game.GENSHIN.displayName, currentResin = 152, maxResin = 160, resinFullAtMillis = ten)
        assertEquals(152, note.resinNow(ten - hour))
        assertEquals(160, note.resinNow(ten + hour))
        // 가득 찬 채로 받은 노트(fullAt = 0)는 받은 값 그대로다.
        assertEquals(160, note.copy(currentResin = 160, resinFullAtMillis = 0L).resinNow(ten))
    }

    // ── 가챠: 계정마다 천장을 따로 센다 ───────────────────────────────────────

    @Test
    fun 두_계정의_기록을_가져와도_천장이_섞이지_않는다() {
        fun rec(uid: String, id: Int, rarity: Int) = GachaRecord(
            game = "genshin", pool = "character", gachaType = "301", itemId = "", name = "아이템$id",
            itemType = "", rarity = rarity, time = "2026-01-01 00:00:00", uid = uid, id = id.toString(),
        )
        // A: 70번 뽑고 5성 없음(id 1..70, 100). B: 10번째에 5성(id 71..80) — 시각 순으로는 A 사이에 끼어 있다.
        val a = (1..70).map { rec("A", it, 3) } + rec("A", 100, 3)
        val b = (71..79).map { rec("B", it, 3) } + rec("B", 80, 5)
        val stat = GachaReport.computeStats(a + b)!!.byGame.getValue("genshin")
        assertEquals(1, stat.five)
        assertEquals(10, stat.avgPity, "B 의 5성은 B 의 10번째 뽑기다 — A 의 70번이 더해지면 안 된다")
        // 현재 천장은 가장 최근에 뽑은 계정(A)의 것 — 71번째까지 5성이 없다.
        assertEquals(71, stat.pools.getValue("character").pity)
    }

    // ── 표기 ────────────────────────────────────────────────────────────────

    @Test
    fun 고정_소수점은_동률을_올린다() {
        assertEquals("3", fixed(2.5, 0))
        assertEquals("15", fixed(14.5, 0))
        assertEquals("-3", fixed(-2.5, 0))
    }

    @Test
    fun 공지_엔티티는_한_번만_풀고_이모지를_깨뜨리지_않는다() {
        fun text(html: String) = (HtmlNews.toBlocks(html).single() as NewsBlock.Text).text
        assertEquals("😀", text("<p>&#128512;</p>"))
        assertEquals("&lt;b&gt;", text("<p>&amp;lt;b&amp;gt;</p>"))
        val img = HtmlNews.toBlocks("""<img src="https://a.example/x.png?x=1&amp;y=2">""").single() as NewsBlock.Image
        assertEquals("https://a.example/x.png?x=1&y=2", img.url)
    }
}
