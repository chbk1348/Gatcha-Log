package com.gatcha.log.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 유효 점수(유효 롤) 산식 — 캐릭터별 유효옵션만, 스탯별 최대 강화량으로 나눠 환산한다.
 * 3게임(원신·스타레일·젠레스) 라벨 표기와 강화 단위가 달라도 한 축에서 비교돼야 한다.
 */
class ArtifactScoreTest {

    private fun art(vararg subs: Pair<String, String>, main: Pair<String, String> = "공격력(%)" to "46.6%") =
        EnkaArtifact(
            slot = "성배",
            setName = "테스트 세트",
            level = 20,
            main = EnkaStatLine(main.first, main.second),
            subs = subs.map { EnkaStatLine(it.first, it.second) },
        )

    private val giCrit = setOf(StatTok.CRIT_RATE, StatTok.CRIT_DMG, StatTok.ATK_PCT, StatTok.ELEM_DMG)

    // ── 산식 ────────────────────────────────────────────────────────────────

    @Test
    fun rollsAreSubstatValueDividedByMaxRoll() {
        // 원신 치확 최대 롤 3.89 / 치피 최대 롤 7.77.
        val s = ArtifactScoring.score(art("치명타 확률" to "7.78%", "치명타 피해" to "15.54%"), giCrit, "genshin")
        assertEquals(4.0, s.rolls, 0.0001)   // 2롤 + 2롤
    }

    @Test
    fun onlyKeyStatsOfThatCharacterCount() {
        // 힐러(풍요) 유효옵션 = HP%·치유·속도 → 치명타는 아무리 잘 떠도 0점.
        val healer = setOf(StatTok.HP_PCT, StatTok.HEAL, StatTok.SPD)
        val s = ArtifactScoring.score(art("치명타 확률" to "12.9%", "치명타 피해" to "25.9%"), healer, "hsr")
        assertEquals(0.0, s.rolls)
        assertTrue(s.isEmpty)
    }

    @Test
    fun differentStatUnitsAreComparableAfterRollConversion() {
        // 스타레일 속도(1롤 2.6)와 치명타 피해(1롤 6.48)는 값 크기가 다르지만 롤로는 같은 2롤.
        val harmony = setOf(StatTok.SPD, StatTok.EHR, StatTok.ATK_PCT, StatTok.BREAK)
        val hunt = setOf(StatTok.CRIT_RATE, StatTok.CRIT_DMG, StatTok.ATK_PCT, StatTok.SPD)
        val spd = ArtifactScoring.score(art("속도" to "5.2"), harmony, "hsr")
        val cd = ArtifactScoring.score(art("치명타 피해" to "12.96%"), hunt, "hsr")
        assertEquals(2.0, spd.rolls, 0.0001)
        assertEquals(2.0, cd.rolls, 0.0001)
    }

    @Test
    fun sameStatScoresDifferentlyPerGameTable() {
        // 치확 1롤: 원신 3.89 · 스타레일 3.24 · 젠레스 2.4.
        val subs = arrayOf("치명타 확률" to "9.72%")
        val gi = ArtifactScoring.score(art(*subs), giCrit, "genshin").rolls
        val hsr = ArtifactScoring.score(art(*subs), giCrit, "hsr").rolls
        val zzz = ArtifactScoring.score(art(*subs), giCrit, "zzz").rolls
        assertEquals(9.72 / 3.89, gi, 0.0001)
        assertEquals(3.0, hsr, 0.0001)
        assertEquals(9.72 / 2.4, zzz, 0.0001)
    }

    @Test
    fun mainStatIsExcludedFromScore() {
        // 메인 옵션은 사용자가 고르는 값 → '굴림 운'을 재는 점수에서 제외(커뮤니티 표준).
        val s = ArtifactScoring.score(art("효과 저항" to "5.8%", main = "치명타 피해" to "62.2%"), giCrit, "genshin")
        assertEquals(0.0, s.rolls)
        assertTrue(s.isEmpty)
    }

    @Test
    fun mainOnlyStatsScoreZeroEvenWhenInKeySet() {
        // 원소 피해·치유는 메인 전용이라 최대 롤값 표에 없다 → 0 (크래시 없이 무시).
        val s = ArtifactScoring.score(art("화염 원소 피해 보너스" to "46.6%"), giCrit, "genshin")
        assertEquals(0.0, s.rolls)
    }

    @Test
    fun repeatedLinesOfSameStatAreSummed() {
        val s = ArtifactScoring.score(art("치명타 확률" to "3.89%", "치명타 확률" to "3.89%"), giCrit, "genshin")
        assertEquals(2.0, s.rolls, 0.0001)
    }

    // ── 게임별 라벨 흡수 ─────────────────────────────────────────────────────

    @Test
    fun sameScoreAcrossGameLabelVariants() {
        // ZZZ 는 계정 언어에 따라 영문 라벨이 그대로 올 수 있다(zzzKrStat 미매칭분).
        val ko = ArtifactScoring.score(art("치명타 확률" to "4.8%", "치명타 피해" to "9.6%"), giCrit, "zzz")
        val en = ArtifactScoring.score(art("CRIT Rate" to "4.8%", "CRIT DMG" to "9.6%"), giCrit, "zzz")
        assertEquals(ko.rolls, en.rolls)
        assertEquals(4.0, ko.rolls, 0.0001)   // 치확 2롤 + 치피 2롤
    }

    // ── 유효옵션 판정(UI 빨간 강조와 동일 소스) ───────────────────────────────

    @Test
    fun isEffectiveMatchesKeyStatRules() {
        val harmony = setOf(StatTok.SPD, StatTok.EHR)
        assertTrue(ArtifactScoring.isEffective(harmony, "속도"))
        assertTrue(ArtifactScoring.isEffective(harmony, "효과 적중"))
        // 치명타는 화합 캐릭 유효옵션이 아니다 — 강조도 점수도 빠져야 한다.
        assertTrue(!ArtifactScoring.isEffective(harmony, "치명타 피해"))
        assertTrue(!ArtifactScoring.isEffective(harmony, "알 수 없는 스탯"))
    }

    // ── 등급 밴드 ───────────────────────────────────────────────────────────

    @Test
    fun gradeBandsAreInclusiveAtBoundaries() {
        assertEquals(ArtifactGrade.EXCELLENT, ArtifactScoring.gradeOf(6.0))
        assertEquals(ArtifactGrade.GOOD, ArtifactScoring.gradeOf(5.9))
        assertEquals(ArtifactGrade.GOOD, ArtifactScoring.gradeOf(4.5))
        assertEquals(ArtifactGrade.FAIR, ArtifactScoring.gradeOf(3.0))
        assertEquals(ArtifactGrade.POOR, ArtifactScoring.gradeOf(1.5))
        assertEquals(ArtifactGrade.BAD, ArtifactScoring.gradeOf(1.4))
        assertEquals(ArtifactGrade.BAD, ArtifactScoring.gradeOf(0.0))
    }

    // ── 캐릭터 집계 ─────────────────────────────────────────────────────────

    @Test
    fun scoreCharRanksDescendingAndAveragesPerPiece() {
        val low = art("치명타 피해" to "7.77%")     // 1롤
        val high = art("치명타 확률" to "11.67%")   // 3롤
        val mid = art("치명타 피해" to "15.54%")    // 2롤
        val res = ArtifactScoring.scoreChar(listOf(low, high, mid), giCrit, "genshin")
        assertEquals(listOf(3.0, 2.0, 1.0), res.ranked.map { (it.score.rolls * 10).toInt() / 10.0 })
        assertEquals(6.0, res.totalRolls, 0.0001)
        // 등급은 합계가 아니라 장당 평균 기준(게임별 칸 수 차이 흡수)
        assertEquals(2.0, res.averageRolls, 0.0001)
        assertEquals(ArtifactGrade.POOR, res.grade)
    }

    @Test
    fun scoreCharHandlesEmptyList() {
        val res = ArtifactScoring.scoreChar(emptyList(), giCrit, "genshin")
        assertEquals(0.0, res.totalRolls)
        assertEquals(0.0, res.averageRolls)
        assertTrue(res.ranked.isEmpty())
        assertEquals(ArtifactGrade.BAD, res.grade)
    }

    // ── 값 파싱 ─────────────────────────────────────────────────────────────

    @Test
    fun parsesDisplayStringsTolerantly() {
        assertEquals(62.2, ArtifactScoring.parseStatValue("62.2%"))
        assertEquals(5.4, ArtifactScoring.parseStatValue("+5.4%"))
        assertEquals(1234.0, ArtifactScoring.parseStatValue("1,234"))
        assertEquals(7.0, ArtifactScoring.parseStatValue("7"))
        assertEquals(0.0, ArtifactScoring.parseStatValue(""))
        assertEquals(0.0, ArtifactScoring.parseStatValue("—"))
    }

    @Test
    fun rollLabelKeepsOneDecimal() {
        assertEquals("5.4", ArtifactScoring.rollLabel(5.44))
        assertEquals("5.5", ArtifactScoring.rollLabel(5.46))
        assertEquals("6.0", ArtifactScoring.rollLabel(5.96))
        assertEquals("0.0", ArtifactScoring.rollLabel(0.0))
    }
}
