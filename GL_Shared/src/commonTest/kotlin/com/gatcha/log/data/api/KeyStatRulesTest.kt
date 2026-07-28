package com.gatcha.log.data.api

import com.gatcha.log.data.api.StatTok.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyStatRulesTest {

    @Test
    fun normStat_maps_labels_across_games() {
        // 원신
        assertEquals(ATK_PCT, normStat("공격력(%)"))
        assertEquals(ATK, normStat("공격력"))
        assertEquals(HP, normStat("HP(고정)"))
        assertEquals(HP_PCT, normStat("HP(%)"))
        assertEquals(ELEM_DMG, normStat("불 원소 피해 보너스"))
        assertEquals(PHYS_DMG, normStat("물리 피해 보너스"))
        assertEquals(EM, normStat("원소 마스터리"))
        assertEquals(ER, normStat("원소 충전 효율"))
        assertEquals(HEAL, normStat("치유 보너스"))
        // HSR
        assertEquals(ELEM_DMG, normStat("화염 피해"))
        assertEquals(BREAK, normStat("격파 특화"))
        assertEquals(EHR, normStat("효과 명중"))
        assertEquals(EHR, normStat("효과 적중"))   // 옛 표기 호환
        assertEquals(SPD, normStat("속도"))
        // ZZZ
        assertEquals(ELEM_DMG, normStat("냉기 속성 피해 보너스"))
        assertEquals(ANOM_PROF, normStat("이상 장악력"))
        assertEquals(ANOM_MASTERY, normStat("이상 숙련"))
        assertEquals(IMPACT, normStat("충격력"))
        assertEquals(ENERGY, normStat("에너지 자동 회복"))
        assertEquals(PEN, normStat("관통률"))
        // 치명
        assertEquals(CRIT_RATE, normStat("치명타 확률"))
        assertEquals(CRIT_DMG, normStat("치명타 피해"))
        // 미매핑
        assertEquals(OTHER, normStat("알 수 없는 스탯"))
    }

    @Test
    fun hsr_path_drives_role() {
        val destruction = KeyStatRules.keyStats("hsr", "화염", path = "파멸")
        assertTrue(CRIT_DMG in destruction && BREAK in destruction)
        assertFalse(HEAL in destruction)
        // 풍요(힐러)는 치명 대신 HP·치유 — 과잉강조 회귀 방지
        val abundance = KeyStatRules.keyStats("hsr", "물리", path = "풍요")
        assertTrue(HP_PCT in abundance && HEAL in abundance)
        assertFalse(CRIT_RATE in abundance)
    }

    @Test
    fun zzz_specialty_drives_role() {
        val anomaly = KeyStatRules.keyStats("zzz", "화염", specialty = "이상")
        assertTrue(ANOM_MASTERY in anomaly)
        assertFalse(CRIT_RATE in anomaly)
        // 직업 미상 → **판정 불가**. 예전엔 치명타 2종으로 넘겨짚어, 치명타를 안 쓰는 캐릭터도
        // 치명타가 유효옵션으로 잡히고 그게 그대로 유효 점수가 됐다.
        assertNull(KeyStatRules.keyStatsOrNull("zzz", "화염", specialty = ""))
        assertTrue(KeyStatRules.keyStats("zzz", "화염", specialty = "").isEmpty())
    }

    @Test
    fun gi_default_and_exceptions() {
        val default = KeyStatRules.keyStats("genshin", "불", charId = 999999)
        assertTrue(CRIT_RATE in default && ATK_PCT in default && ELEM_DMG in default)
        // 종려(HP 스케일) 예외 — HP%, 공격%는 아님
        val zhongli = KeyStatRules.keyStats("genshin", "바위", charId = 10000030)
        assertTrue(HP_PCT in zhongli)
        assertFalse(ATK_PCT in zhongli)
    }

    @Test
    fun isKey_integration() {
        val destruction = KeyStatRules.keyStats("hsr", "화염", path = "파멸")
        assertTrue(KeyStatRules.isKey(destruction, "치명타 피해"))
        val abundance = KeyStatRules.keyStats("hsr", "물리", path = "풍요")
        assertFalse(KeyStatRules.isKey(abundance, "공격력(%)"))
    }

    // ── 표시 순서 ────────────────────────────────────────────────────────────
    // stats 는 Set 이라 순서가 없다. iOS 로 브리지되면 Swift Set 이 되면서 캐릭터 상세에 들어갈
    // 때마다 칩이 뒤죽박죽으로 나왔다 — 순서를 여기서 고정하고 양 플랫폼이 같은 배열을 쓴다.

    @Test
    fun orderedKeyStats_followsSelectableOrder() {
        val stats = setOf(ATK_PCT, CRIT_DMG, CRIT_RATE)   // 일부러 뒤섞어 넣는다
        // genshin selectable = [치확, 치피, 공%, HP%, 방%, 원마, 원충]
        assertEquals(listOf(CRIT_RATE, CRIT_DMG, ATK_PCT), orderedKeyStats("genshin", stats))
    }

    @Test
    fun orderedKeyStats_isStableAcrossCalls() {
        val stats = setOf(SPD, CRIT_RATE, BREAK, ATK_PCT, CRIT_DMG)
        assertEquals(orderedKeyStats("hsr", stats), orderedKeyStats("hsr", stats))
    }

    @Test
    fun orderedKeyStats_appendsUnknownTokensLast() {
        // 후보에 없는 토큰(OTHER 등)도 빠지면 안 되고 뒤에 붙어야 한다.
        val out = orderedKeyStats("genshin", setOf(OTHER, CRIT_RATE))
        assertEquals(listOf(CRIT_RATE, OTHER), out)
    }

    @Test
    fun selectableStats_coverEveryTokenTheRulesCanProduce() {
        // 룰이 뽑는 유효옵션이 후보 목록에 없으면, '바꾸기' 화면에 **안 보이면서 선택된 채로 남아**
        // 사용자가 해제할 수 없다. 실제로 원신 원소 피해 보너스·HSR 속도/격파·ZZZ 이상 마스터리가 그랬다.
        val cases = listOf(
            "genshin" to KeyStatRules.keyStats("genshin", charId = 999999),
            "hsr" to KeyStatRules.keyStats("hsr", path = "파멸"),
            "hsr" to KeyStatRules.keyStats("hsr", path = "풍요"),
            "zzz" to KeyStatRules.keyStats("zzz", specialty = "이상"),
            "zzz" to KeyStatRules.keyStats("zzz", specialty = "강공"),
        )
        for ((game, stats) in cases) {
            val selectable = KeyStatRules.selectableStats(game).toSet()
            val missing = stats - selectable
            assertTrue(missing.isEmpty(), "$game 후보 목록에 없는 유효옵션: $missing")
        }
    }

    @Test
    fun orderedKeyStats_emptyStaysEmpty() {
        assertTrue(orderedKeyStats("zzz", emptySet()).isEmpty())
    }

    // ── 인게임 옵션명 ────────────────────────────────────────────────────────

    @Test
    fun statLabel_usesPerGameInGameNames() {
        // 같은 토큰이라도 게임마다 인게임 표기가 다르다.
        assertEquals("치유 보너스", statLabel(HEAL, "genshin"))
        assertEquals("치유량 보너스", statLabel(HEAL, "hsr"))
        assertEquals("원소 피해 보너스", statLabel(ELEM_DMG, "genshin"))
        assertEquals("속성 피해 보너스", statLabel(ELEM_DMG, "hsr"))
        assertEquals("속성 피해 보너스", statLabel(ELEM_DMG, "zzz"))
        assertEquals("에너지 자동 회복", statLabel(ENERGY, "zzz"))
        assertEquals("에너지 회복 효율", statLabel(ENERGY, "hsr"))
        assertEquals("이상 마스터리", statLabel(ANOM_MASTERY, "zzz"))
        assertEquals("효과 명중", statLabel(EHR, "hsr"))   // '적중' 아님
        assertEquals("관통률", statLabel(PEN, "zzz"))
    }

    @Test
    fun normStat_readsZzzAnomalyMasteryNotElementalMastery() {
        // '이상 마스터리'(ZZZ)가 '마스터리' 규칙에 먼저 걸려 EM(원신 원소 마스터리)으로
        // 잡히던 버그. EM 은 젠레스 후보에 없어서 강조·유효 점수에서 통째로 빠졌다.
        assertEquals(ANOM_MASTERY, normStat("이상 마스터리"))
        assertEquals(ANOM_MASTERY, normStat("이상 숙련"))   // 옛 표기 호환
        assertEquals(EM, normStat("원소 마스터리"))
    }
}
