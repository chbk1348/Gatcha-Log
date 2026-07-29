package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 홈 히어로 문구의 계약을 고정한다.
 *
 * ① **개수** 약속 — 묶음을 손대다 보면 개수는 조용히 어긋난다.
 * ② **결정형** — 같은 날·같은 상태면 항상 같은 문구여야 한다(열 때마다 바뀌면 화면이 산만하다).
 * ③ **상태 판정** — 예산 초과인데 "여유로워요"가 뜨면 안 된다.
 */
class HeroMessagesTest {

    private fun ctx(
        hour: Int = 9,
        monthly: Long = 0,
        prev: Long = 0,
        budget: Long = 0,
        day: String = "2026-07-29",
    ) = HeroMessageContext(hourOfDay = hour, monthlyTotal = monthly, prevTotal = prev, budget = budget, dayKey = day)

    @Test
    fun hasExpectedPresetCount() {
        // 일반 100 + 검증된 캐릭터 대사(현재 0건 — 인게임 표기 확인 전까지 비워 둔다).
        assertEquals(100 + HeroMessages.quotes.size, HeroMessages.all.size)
    }

    @Test
    fun quotesCarryTheirSpeaker() {
        // 출처 없이 대사만 띄우면 누구 말인지 알 수 없다 — 형식은 `대사 — 이름` 으로 고정.
        HeroMessages.quotes.forEach {
            assertTrue(it.contains(" — "), "출처 표기 없음: $it")
            assertTrue(it.substringAfterLast(" — ").isNotBlank(), "이름이 비어 있음: $it")
        }
    }

    @Test
    fun presetsAreUniqueAndNonBlank() {
        assertTrue(HeroMessages.all.none { it.isBlank() }, "빈 문구가 있으면 히어로에 빈 줄이 뜬다")
        assertEquals(HeroMessages.all.size, HeroMessages.all.toSet().size, "중복 문구")
    }

    @Test
    fun sameDayAndStateGivesSameLine() {
        val c = ctx(monthly = 50_000, budget = 100_000)
        assertEquals(HeroMessages.pick(c), HeroMessages.pick(c))
    }

    @Test
    fun budgetStateDecidesTheStatusPool() {
        assertEquals(HeroMessages.budgetOver, HeroMessages.status(ctx(monthly = 120_000, budget = 100_000)))
        assertEquals(HeroMessages.budgetNear, HeroMessages.status(ctx(monthly = 95_000, budget = 100_000)))
        assertEquals(HeroMessages.budgetHalf, HeroMessages.status(ctx(monthly = 60_000, budget = 100_000)))
        assertEquals(HeroMessages.budgetRoom, HeroMessages.status(ctx(monthly = 10_000, budget = 100_000)))
    }

    @Test
    fun withoutBudgetItFallsBackToTrend() {
        assertEquals(HeroMessages.trendUp, HeroMessages.status(ctx(monthly = 90_000, prev = 50_000)))
        assertEquals(HeroMessages.trendDown, HeroMessages.status(ctx(monthly = 30_000, prev = 50_000)))
        assertEquals(HeroMessages.trendFlat, HeroMessages.status(ctx(monthly = 50_000, prev = 50_000)))
    }

    @Test
    fun noSpendingBeatsEverything() {
        // 지출이 0이면 예산이 있든 없든 '아직 안 썼다'가 맞는 말이다.
        assertEquals(HeroMessages.noSpend, HeroMessages.status(ctx(monthly = 0, budget = 100_000)))
    }

    @Test
    fun greetingFollowsTheClock() {
        assertEquals(HeroMessages.morning, HeroMessages.greeting(7))
        assertEquals(HeroMessages.afternoon, HeroMessages.greeting(13))
        assertEquals(HeroMessages.evening, HeroMessages.greeting(19))
        assertEquals(HeroMessages.night, HeroMessages.greeting(23))
        assertEquals(HeroMessages.night, HeroMessages.greeting(3))
    }

    @Test
    fun everyPickComesFromThePresets() {
        // 어떤 상태·시각 조합이든 프리셋 밖의 문구가 나오면 안 된다.
        val days = listOf("2026-07-27", "2026-07-28", "2026-07-29", "2026-08-01")
        for (day in days) for (hour in 0..23) {
            for (c in listOf(
                ctx(hour, 0, 0, 0, day),
                ctx(hour, 50_000, 0, 0, day),
                ctx(hour, 50_000, 90_000, 0, day),
                ctx(hour, 150_000, 90_000, 100_000, day),
            )) {
                assertTrue(HeroMessages.pick(c) in HeroMessages.all, "프리셋 밖 문구: ${HeroMessages.pick(c)}")
            }
        }
    }
}
