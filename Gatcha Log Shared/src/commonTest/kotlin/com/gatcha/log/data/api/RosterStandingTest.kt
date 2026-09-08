package com.gatcha.log.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 순위는 유저가 검증할 수 없는 숫자라, 틀리면 앱이 자신 있게 거짓말을 한다.
 * 모수 규칙과 경계값을 여기서 고정한다.
 */
class RosterStandingTest {

    // 치확/치피만 있는 유물 한 장 — 원신 CV = 치확×2 + 치피.
    private fun artifact(slot: String, critRate: Double, critDmg: Double) = EnkaArtifact(
        slot = slot,
        setName = "테스트 세트",
        level = 20,
        main = EnkaStatLine("HP", "4780"),
        subs = listOf(
            EnkaStatLine("치명타 확률", "$critRate%"),
            EnkaStatLine("치명타 피해", "$critDmg%"),
        ),
    )

    private fun char(
        id: Int,
        cv: Double,
        level: Int = 90,
        detailed: Boolean = true,
        artifacts: List<EnkaArtifact> = listOf(artifact("생의 꽃", cv / 4, cv / 2)),
    ) = EnkaChar(
        id = id,
        name = "캐릭터$id",
        level = level,
        rank = 0,
        rarity = 5,
        element = "물",
        detailed = detailed,
        artifacts = artifacts,
    )

    /** id=1 이 가장 높고 내려가는 로스터 n명. */
    private fun roster(n: Int, level: Int = 90, detailed: Boolean = true) =
        (1..n).map { char(it, cv = (n - it + 1) * 4.0, level = level, detailed = detailed) }

    @Test
    fun 모수가_하한_미만이면_순위를_내지_않는다() {
        val r = roster(RosterStanding.MIN_POOL - 1)
        val s = RosterStandings.of(r.first(), r, "genshin")
        assertFalse(s.hasRank, "9명이면 순위를 그리면 안 된다")
        assertEquals(0, s.rank)
        assertEquals(0, s.percentile)
    }

    @Test
    fun 모수가_하한이면_순위가_나온다() {
        val r = roster(RosterStanding.MIN_POOL)
        val s = RosterStandings.of(r.first(), r, "genshin")
        assertTrue(s.hasRank)
        assertEquals(1, s.rank)
        assertEquals(RosterStanding.MIN_POOL, s.pool)
    }

    @Test
    fun 상세가_없는_캐릭터는_모수에서_빠진다() {
        // 상세 9명 + 비상세 20명 → 모수는 9명이라 순위 없음.
        val detailed = roster(9)
        val hidden = (100..119).map { char(it, cv = 1.0, detailed = false) }
        val s = RosterStandings.of(detailed.first(), detailed + hidden, "genshin")
        assertFalse(s.hasRank, "쇼케이스 밖 캐릭터를 세면 순위가 부풀려진다")
        assertEquals(9, s.detailedCount)
    }

    @Test
    fun 육성_전_캐릭터는_모수에서_빠진다() {
        val grown = roster(9)
        val low = (200..219).map { char(it, cv = 1.0, level = RosterStanding.MIN_LEVEL - 1) }
        val s = RosterStandings.of(grown.first(), grown + low, "genshin")
        assertFalse(s.hasRank, "레벨 1짜리가 분모에 있으면 순위가 부풀려진다")
    }

    @Test
    fun 유물을_안_낀_캐릭터는_모수에서_빠진다() {
        val equipped = roster(9)
        val naked = (300..319).map { char(it, cv = 0.0, artifacts = emptyList()) }
        val s = RosterStandings.of(equipped.first(), equipped + naked, "genshin")
        assertFalse(s.hasRank)
    }

    @Test
    fun 동점은_같은_순위다() {
        // 20·20·10 → 앞의 둘이 공동 1위, 마지막이 3위.
        val tie = listOf(char(1, 20.0), char(2, 20.0)) + (3..12).map { char(it, 10.0) }
        val a = RosterStandings.of(tie[0], tie, "genshin")
        val b = RosterStandings.of(tie[1], tie, "genshin")
        val c = RosterStandings.of(tie[2], tie, "genshin")
        assertEquals(1, a.rank)
        assertEquals(1, b.rank, "같은 점수인데 순위가 갈리면 안 된다")
        assertEquals(3, c.rank)
    }

    @Test
    fun 백분위는_1_아래로_내려가지_않는다() {
        val r = roster(200)
        val s = RosterStandings.of(r.first(), r, "genshin")
        assertEquals(1, s.rank)
        assertTrue(s.percentile >= 1, "1위가 상위 0% 로 보이면 안 된다")
    }

    @Test
    fun 꼴찌는_상위_100퍼센트다() {
        val r = roster(20)
        val s = RosterStandings.of(r.last(), r, "genshin")
        assertEquals(20, s.rank)
        assertEquals(100, s.percentile)
    }

    @Test
    fun 모수에_못_드는_캐릭터_본인은_순위가_없다() {
        val r = roster(20)
        val rookie = char(999, cv = 4.0, level = RosterStanding.MIN_LEVEL - 1)
        val s = RosterStandings.of(rookie, r + rookie, "genshin")
        assertFalse(s.hasRank, "본인이 모수 밖이면 순위를 말할 수 없다")
    }

    @Test
    fun 다음_한_걸음은_가장_약한_유효옵션과_최하위_유물을_짚는다() {
        // 모래: 치확만 많이, 성배: 둘 다 적게 → 성배가 최하위, 치피가 약점.
        val c = char(
            1, cv = 0.0,
            artifacts = listOf(
                artifact("시간의 모래", critRate = 15.0, critDmg = 20.0),
                artifact("공간의 성배", critRate = 3.0, critDmg = 3.0),
            ),
        )
        val step = RosterStandings.nextStep(c, "genshin")
        assertNotNull(step)
        assertEquals("공간의 성배", step.slotLabel)
        assertEquals("치명타 피해", step.statLabel, "CV 기준 롤이 더 적은 쪽을 짚어야 한다")
    }

    @Test
    fun 유물이_없으면_다음_한_걸음도_없다() {
        assertNull(RosterStandings.nextStep(char(1, 0.0, artifacts = emptyList()), "genshin"))
    }
}
