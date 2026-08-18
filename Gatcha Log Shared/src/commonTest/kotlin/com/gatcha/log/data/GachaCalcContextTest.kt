package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 계산기 2.0 개선의 파생 계산 — 분위수·판정 경계를 테스트로 굳힌다.
 *
 * 특히 [pickupCdf] 는 예전 `pickupProb` 근사가 "90% 지점이 최악 뽑기 수를 넘는" 모순을 냈던 자리라,
 * **마지막 원소가 정확히 최악 지점에서 1.0** 이라는 성질을 여러 배너로 확인한다.
 */
class GachaCalcContextTest {

    private val genshin = GachaRateData.byKey("genshin")!!
    private val giChar = genshin.character!!
    private val giWeapon = genshin.weapon!!
    private val nteChar = GachaRateData.byKey("nte")!!.character!!   // no5050(픽뚫 없음)

    // ---------------------------------------------------------------- 프리필

    @Test
    fun 프리필은_앱_기록을_그대로_가져온다() {
        val p = calcPrefill(
            gameKey = "genshin",
            pity = mapOf("genshin" to PityState(count = 47, guaranteed = true)),
            held = mapOf("genshin" to 12_960),
        )
        assertEquals(47, p.pity)
        assertTrue(p.guaranteed)
        assertEquals(12_960, p.held)
        assertTrue(p.hasPityRecord)
    }

    @Test
    fun 천장_기록이_없으면_hasPityRecord_가_false() {
        // 0 을 채우면 "천장 0"이라는 틀린 사실을 앱이 주장하게 되므로, 화면은 이 플래그로 빈 칸을 띄운다.
        val p = calcPrefill("genshin", emptyMap(), emptyMap())
        assertEquals(0, p.pity)
        assertFalse(p.hasPityRecord)
        assertEquals(0, p.held)
    }

    // ---------------------------------------------------------------- 무료 수급

    @Test
    fun 무료수급은_월정액이_꺼진_것이_기본() {
        val inc = freeIncome(genshin, giChar, days = 12, includePass = false)
        // 데일리 60×12 = 720, 위클리 60×1 = 60 (12/7 = 1회, 올림하지 않는다)
        assertEquals(780, inc.total)
        assertEquals("4.9뽑", inc.pullsLabel)
        // 월정액 줄 자체는 목록에 남아 화면이 토글로 보여줄 수 있다.
        assertTrue(inc.lines.any { it.optional })
    }

    @Test
    fun 월정액을_켜면_합계에_들어간다() {
        val inc = freeIncome(genshin, giChar, days = 12, includePass = true)
        assertEquals(780 + 90 * 12, inc.total)   // 1,860
    }

    @Test
    fun 남은_일수가_0이면_수급도_0() {
        val inc = freeIncome(genshin, giChar, days = 0, includePass = true)
        assertEquals(0, inc.total)
    }

    // ---------------------------------------------------------------- 누적확률

    @Test
    fun cdf_는_최악_지점에서_정확히_1이_된다() {
        // 예전 pickupProb 근사가 깨뜨렸던 성질 — p90 이 최악보다 커지면 화면이 앞뒤가 안 맞는다.
        listOf(
            Triple(giChar, 47, false),
            Triple(giChar, 0, false),
            Triple(giChar, 89, false),
            Triple(giChar, 47, true),
            Triple(giWeapon, 30, false),
            Triple(nteChar, 10, false),
        ).forEach { (banner, pity, guaranteed) ->
            val cdf = pickupCdf(banner, pity, guaranteed)
            val worst = GachaRateData.maxPullsToSecure(pity, guaranteed, banner)
            assertEquals(worst + 1, cdf.size, "배너 상한 불일치 (pity=$pity, guaranteed=$guaranteed)")
            assertEquals(0.0, cdf[0])
            assertEquals(1.0, cdf[cdf.size - 1], 1e-9)
        }
    }

    @Test
    fun cdf_는_단조증가한다() {
        val cdf = pickupCdf(giChar, startPity = 47, guaranteed = false)
        for (n in 1 until cdf.size) {
            assertTrue(cdf[n] >= cdf[n - 1] - 1e-12, "n=$n 에서 확률이 감소했다")
        }
    }

    @Test
    fun 보장_보유면_한_사이클_안에_확보된다() {
        val cdf = pickupCdf(giChar, startPity = 47, guaranteed = true)
        assertEquals(90 - 47 + 1, cdf.size)
        assertEquals(1.0, cdf[90 - 47], 1e-9)
    }

    @Test
    fun 픽뚫이_없는_배너는_첫_최고등급이_곧_확보() {
        // 이환: no5050 — 50/50 이 없으니 두 번째 사이클이 붙지 않는다.
        val cdf = pickupCdf(nteChar, startPity = 0, guaranteed = false)
        assertEquals(nteChar.hardPity + 1, cdf.size)
    }

    @Test
    fun 분위수는_최악을_넘지_않고_순서가_유지된다() {
        val cdf = pickupCdf(giChar, startPity = 47, guaranteed = false)
        val p50 = pullsAtQuantile(cdf, 0.5)
        val p90 = pullsAtQuantile(cdf, 0.9)
        val worst = cdf.size - 1
        assertTrue(p50 <= p90, "p50($p50) 이 p90($p90) 보다 크다")
        assertTrue(p90 <= worst, "p90($p90) 이 최악($worst) 을 넘었다")
        // 50/50 미보장이면 첫 최고등급만으로 이미 확률 절반이 차므로, 중앙값은
        // 하드 천장까지 남은 43뽑을 넘지 않는다(첫 5★가 사실상 확정되는 지점에서 0.5 를 지난다).
        assertTrue(p50 <= 90 - 47, "p50($p50) 이 하드 천장까지 남은 43뽑을 넘었다")
        assertEquals(36, p50)
        assertEquals(111, p90)
    }

    @Test
    fun 확률게이지도_같은_배열에서_읽는다() {
        val cdf = pickupCdf(giChar, startPity = 47, guaranteed = false)
        assertEquals(0.0, pickupProbAt(cdf, 0))
        assertEquals(1.0, pickupProbAt(cdf, 9_999))    // 상한을 넘으면 1.0
        assertTrue(pickupProbAt(cdf, 81) in 0.0..1.0)
    }

    // ---------------------------------------------------------------- 판정

    @Test
    fun 최악까지_충분하면_확보() {
        // 천장 47·미보장 → 최악 133뽑 = 21,280 원석
        val o = calcOutcome(giChar, heldCurrency = 21_280, freeCurrency = 0, pity = 47, guaranteed = false, qty = 1)
        assertEquals(CalcVerdict.Secured, o.verdict)
        assertEquals(133, o.neededPulls)
        assertEquals(21_280, o.neededCurrency)
        assertEquals(0, o.shortfallCurrency)
        assertEquals(100, o.progressPercent)
    }

    @Test
    fun 하드천장은_넘고_최악은_못_미치면_아슬아슬() {
        // 하드 천장까지 43뽑 = 6,880 원석. 보유 12,960 + 무료 780 = 13,740 → 그 사이.
        val o = calcOutcome(giChar, heldCurrency = 12_960, freeCurrency = 780, pity = 47, guaranteed = false, qty = 1)
        assertEquals(CalcVerdict.Tight, o.verdict)
        assertEquals(13_740, o.availableCurrency)
        assertEquals(21_280 - 13_740, o.shortfallCurrency)          // 7,540
        assertEquals(48, o.shortfallPulls)                          // ceil(7540 / 160)
        assertEquals(48L * giChar.wonPerPull, o.shortfallWon)       // 595원/뽑
        assertEquals(64, o.progressPercent)                         // 13,740 / 21,280
    }

    @Test
    fun 하드천장에도_못_미치면_부족() {
        val o = calcOutcome(giChar, heldCurrency = 1_000, freeCurrency = 0, pity = 47, guaranteed = false, qty = 1)
        assertEquals(CalcVerdict.Short, o.verdict)
        assertEquals("충전이 필요해요", o.headline)
    }

    @Test
    fun 무료수급이_판정을_뒤집을_수_있다() {
        // 보유만으론 하드 천장(6,880)에 못 미치지만 마감까지 모으면 넘는다.
        val held = 6_500
        assertEquals(CalcVerdict.Short, calcOutcome(giChar, held, 0, 47, false, 1).verdict)
        assertEquals(CalcVerdict.Tight, calcOutcome(giChar, held, 780, 47, false, 1).verdict)
    }

    @Test
    fun 목표가_여러개면_첫개만_현재_천장에서_센다() {
        // 전체를 qty 배 하면 이미 쌓인 천장을 개수만큼 중복으로 깎게 된다.
        val one = calcOutcome(giChar, 0, 0, pity = 47, guaranteed = false, qty = 1).neededPulls
        val two = calcOutcome(giChar, 0, 0, pity = 47, guaranteed = false, qty = 2).neededPulls
        assertEquals(133, one)                  // (90-47) + 90
        assertEquals(133 + 180, two)            // 두 번째는 천장 0·보장 없음에서 다시
    }

    @Test
    fun 보장을_켜면_한_사이클이_빠진다() {
        val off = calcOutcome(giChar, 0, 0, pity = 47, guaranteed = false, qty = 1).neededPulls
        val on = calcOutcome(giChar, 0, 0, pity = 47, guaranteed = true, qty = 1).neededPulls
        assertEquals(90, off - on)   // 원신 기준 90뽑 차이 — 켜는 걸 잊으면 그만큼 과다 계산된다
    }
}
