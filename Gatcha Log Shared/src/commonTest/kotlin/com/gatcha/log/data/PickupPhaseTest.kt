package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 픽업 페이즈 판정 — **시작 줄과 종료 줄이 같은 이름을 써야 한다.**
 *
 * 예전엔 종료 줄에만 전반/후반이 붙어서, 같은 페이즈인데 "v6.6 픽업 시작"과
 * "v6.6 후반 픽업 종료"로 갈렸다. 둘이 같은 것을 가리키는지 화면에서 알 수 없었다.
 */
class PickupPhaseTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_760_000_000_000L

    private fun b(name: String, ver: String, start: Long, end: Long) =
        GachaBanner(game = Game.GENSHIN.displayName, name = name, endMillis = end, startMillis = start, version = ver)

    @Test
    fun 한_버전에_페이즈가_둘이면_전반_후반() {
        val list = listOf(
            b("A", "6.6", now, now + 20 * day),
            b("B", "6.6", now + 21 * day, now + 41 * day),
        )
        val ph = pickupPhases(list)
        assertEquals(listOf("전반", "후반"), ph.map { it.label })
    }

    @Test
    fun 페이즈가_하나뿐이면_최신은_전반_이전은_후반() {
        val list = listOf(
            b("A", "6.5", now, now + 20 * day),
            b("B", "6.6", now + 21 * day, now + 41 * day),
        )
        val ph = pickupPhases(list)
        assertEquals(listOf("후반", "전반"), ph.map { it.label })
    }

    @Test
    fun 셋_이상이면_N페이즈() {
        val list = listOf(
            b("A", "6.6", now, now + 10 * day),
            b("B", "6.6", now + 11 * day, now + 20 * day),
            b("C", "6.6", now + 21 * day, now + 30 * day),
        )
        assertEquals(listOf("전반", "후반", "3페이즈"), pickupPhases(list).map { it.label })
    }

    @Test
    fun 제목은_버전과_페이즈를_앞에_붙인다() {
        val ph = pickupPhases(listOf(b("A", "6.6", now, now + 10 * day)))[0]
        assertEquals("v6.6 전반 픽업 시작", ph.title("픽업 시작"))
        assertEquals("v6.6 전반 픽업 종료", ph.title("픽업 종료"))
    }

    @Test
    fun 버전이_없으면_페이즈만() {
        val ph = pickupPhases(listOf(b("A", "", now, now + 10 * day)))[0]
        assertEquals("전반 픽업 시작", ph.title("픽업 시작"))
    }

    @Test
    fun 시작_줄도_종료_줄과_같은_페이즈_이름을_쓴다() {
        val list = listOf(
            b("A", "6.6", now - 5 * day, now + 5 * day),      // 진행 중 = 전반
            b("B", "6.6", now + 6 * day, now + 16 * day),     // 다가올 = 후반
        )
        val starts = buildStartEntries(list, now)
        assertEquals(1, starts.size)
        assertEquals("v6.6 후반 픽업 시작", starts[0].title)

        val ends = ScheduleLogic.buildSchedule(list, emptyList(), emptyList())
            .filter { it.kind == "패치" }
        assertTrue(ends.any { it.title == "v6.6 후반 픽업 종료" }, ends.map { it.title }.toString())
    }

    @Test
    fun 종료_미정_픽업도_시작_줄은_남는다() {
        // 페이즈를 만들 수 없어(끝 날짜 없음) 라벨은 없지만, 줄 자체는 사라지면 안 된다.
        val list = listOf(GachaBanner(
            game = Game.GENSHIN.displayName, name = "콜라보", endMillis = 0L,
            startMillis = now + 3 * day, version = "6.7",
        ))
        val starts = buildStartEntries(list, now)
        assertEquals(1, starts.size)
        assertEquals("v6.7 픽업 시작", starts[0].title)
    }
}
