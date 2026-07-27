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
        assertEquals(EHR, normStat("효과 적중"))
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
}
