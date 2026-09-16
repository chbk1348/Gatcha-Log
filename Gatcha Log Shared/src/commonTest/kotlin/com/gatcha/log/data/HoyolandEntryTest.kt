package com.gatcha.log.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 내 입장권 — 날짜마다 한 조. 나흘 다 갈 수도, 하루만 갈 수도 있다.
 *
 * 지키는 것: 안 가는 날은 **키가 없고**, 같은 조를 다시 누르면 해제되며, 저장·복원이 왕복한다.
 * 조 편성이 바뀌어 모르는 조가 남아도 화면이 설 수 있어야 한다(시각만 빈다).
 */
class HoyolandEntryTest {

    private val event = HoyolandDefaults.event.copy(
        startYmd = "2026-10-02",
        endYmd = "2026-10-05",
    )

    @Test
    fun 그날의_조를_정하고_같은_조를_다시_누르면_해제된다() {
        var entry = HoyolandEntry().withGroup("2026-10-02", "A")
        assertEquals("A", entry.groupOn("2026-10-02"))
        entry = entry.withGroup("2026-10-02", "A")
        assertTrue(entry.isEmpty, "같은 조를 다시 누르면 안 가는 날로 돌아가야 한다")
    }

    @Test
    fun 다른_조를_누르면_그날_조가_바뀐다() {
        val entry = HoyolandEntry().withGroup("2026-10-02", "A").withGroup("2026-10-02", "C")
        assertEquals("C", entry.groupOn("2026-10-02"))
        assertEquals(1, entry.dayCount, "하루에는 한 조뿐이라 날이 늘면 안 된다")
    }

    @Test
    fun 안_가는_날은_키가_없다() {
        val entry = HoyolandEntry().withGroup("2026-10-02", "A").withGroup("2026-10-02", "")
        assertTrue(entry.isEmpty)
        assertFalse(entry.isGoing("2026-10-02"))
        assertEquals("", entry.groupOn("2026-10-02"))
    }

    @Test
    fun 나흘을_따로_들고_있는다() {
        val entry = HoyolandEntry()
            .withGroup("2026-10-02", "A")
            .withGroup("2026-10-04", "E")
        assertEquals(2, entry.dayCount)
        assertEquals("A", entry.groupOn("2026-10-02"))
        assertEquals("E", entry.groupOn("2026-10-04"))
        assertEquals("", entry.groupOn("2026-10-03"), "고르지 않은 날은 비어 있어야 한다")
    }

    @Test
    fun 가는_날은_날짜_순으로_나온다() {
        val entry = HoyolandEntry()
            .withGroup("2026-10-05", "F")
            .withGroup("2026-10-02", "A")
            .withGroup("2026-10-03", "C")
        assertEquals(listOf("2026-10-02", "2026-10-03", "2026-10-05"), entry.goingYmds)
    }

    @Test
    fun 저장하고_복원하면_같다() {
        val entry = HoyolandEntry()
            .withGroup("2026-10-02", "A")
            .withGroup("2026-10-05", "F")
        assertEquals(entry.groups, HoyolandEntry.parse(entry.serialize()).groups)
    }

    @Test
    fun 깨진_저장값은_조용히_버린다() {
        val parsed = HoyolandEntry.parse("2026-10-02\tA\n쓰레기줄\n\t B\n2026-10-03\t")
        assertEquals(mapOf("2026-10-02" to "A"), parsed.groups)
    }

    @Test
    fun 빈_저장값은_빈_입장권이다() {
        assertTrue(HoyolandEntry.parse("").isEmpty)
        assertTrue(HoyolandEntry.parse("   ").isEmpty)
    }

    @Test
    fun 조의_입장_시각을_config_에서_읽는다() {
        assertEquals("10:00", event.entryTimeOf("A"))
        assertEquals("12:00", event.entryTimeOf("F"))
    }

    @Test
    fun 모르는_조는_시각이_비고_조_이름만_남는다() {
        // 어드민에서 편성을 갈아엎으면 이미 고른 사람에게 옛 조가 남는다 — 그래도 화면은 서야 한다.
        val entry = HoyolandEntry().withGroup("2026-10-02", "Z")
        assertEquals("", event.entryTimeOf("Z"))
        assertEquals("10.2(금) Z조", event.entryLine(entry, "2026-10-02"))
    }

    @Test
    fun 한_줄_표기는_날짜와_조와_시각을_담는다() {
        val entry = HoyolandEntry().withGroup("2026-10-02", "A")
        assertEquals("10.2(금) A조 · 10:00", event.entryLine(entry, "2026-10-02"))
    }

    @Test
    fun 안_가는_날의_한_줄_표기는_비어_있다() {
        assertEquals("", event.entryLine(HoyolandEntry(), "2026-10-03"))
    }

    @Test
    fun 다음에_가는_날은_오늘을_포함하고_지난_날은_건너뛴다() {
        val entry = HoyolandEntry()
            .withGroup("2026-10-02", "A")
            .withGroup("2026-10-04", "E")
        // 10.3 에 서 있으면 지난 10.2 는 건너뛰고 10.4 가 잡힌다.
        assertEquals("2026-10-04", event.nextEntryYmd(entry, ymdMillis("2026-10-03")))
        // 10.2 당일에는 오늘이 먼저다 — `>=` 라 오늘이 걸러지지 않는다.
        assertEquals("2026-10-02", event.nextEntryYmd(entry, ymdMillis("2026-10-02")))
        // 다 지나면 빈 문자열.
        assertEquals("", event.nextEntryYmd(entry, ymdMillis("2026-10-05")))
    }

    @Test
    fun 조_편성이_비면_기능이_꺼진다() {
        assertTrue(event.hasEntryGroups)
        assertFalse(event.copy(entryGroups = emptyList()).hasEntryGroups)
    }

    @Test
    fun 조_편성을_내리면_히어로_줄도_사라진다() {
        // 어드민에서 편성을 비우면 고르는 시트가 닫힌다 — 줄만 남으면 지울 수 없는 값이 된다.
        val entry = HoyolandEntry().withGroup("2026-10-02", "A")
        val noGroups = event.copy(entryGroups = emptyList())
        assertTrue(noGroups.entryLines(entry).isEmpty())
        assertEquals("", noGroups.nextEntryYmd(entry, ymdMillis("2026-10-01")))
    }

    @Test
    fun 기간_밖으로_밀려난_날은_화면에서_거른다() {
        // 기간이 바뀌어도 저장값은 그대로 두고(되돌아오면 되살아난다) 화면에서만 거른다.
        val entry = HoyolandEntry()
            .withGroup("2026-10-02", "A")
            .withGroup("2026-10-09", "B")
        assertEquals(listOf("10.2(금) A조 · 10:00"), event.entryLines(entry))
        assertEquals(2, entry.dayCount, "저장된 값은 건드리지 않는다")
        assertEquals("2026-10-02", event.nextEntryYmd(entry, ymdMillis("2026-10-01")))
    }

    /** 그 날짜 정오(KST)의 epoch millis — 날짜 경계에 걸리지 않게 한낮을 쓴다. */
    private fun ymdMillis(ymd: String): Long {
        val p = ymd.split("-").map { it.toInt() }
        return LocalDateTime(p[0], p[1], p[2], 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }
}
