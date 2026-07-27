package com.gatcha.log.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 치명 점수(CV) 산식 — 3게임(원신·스타레일·젠레스) 라벨 표기가 달라도 같은 점수가 나와야 한다.
 * 값이 표시 문자열("62.2%")이라 파싱 경계도 함께 고정한다.
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

    // ── 산식 ────────────────────────────────────────────────────────────────

    @Test
    fun cvWeightsCritRateTwiceCritDamage() {
        val s = ArtifactScoring.score(art("치명타 확률" to "7.8%", "치명타 피해" to "14.0%"))
        assertEquals(7.8, s.critRate)
        assertEquals(14.0, s.critDmg)
        assertEquals(7.8 * 2 + 14.0, s.cv)
    }

    @Test
    fun cvSumsRepeatedCritLines() {
        // 같은 스탯이 여러 줄로 오는 응답도 합산해야 한다.
        val s = ArtifactScoring.score(art("치명타 확률" to "3.9%", "치명타 확률" to "3.9%"))
        assertEquals(7.8, s.critRate)
        assertEquals(15.6, s.cv)
    }

    @Test
    fun mainStatCritIsExcludedFromCv() {
        // 메인 옵션은 사용자가 고르는 값 → '굴림 운'을 재는 CV 에서 제외(커뮤니티 표준).
        val s = ArtifactScoring.score(art("공격력(%)" to "5.8%", main = "치명타 피해" to "62.2%"))
        assertEquals(0.0, s.cv)
        assertTrue(s.isEmpty)
    }

    @Test
    fun nonCritSubstatsAreIgnored() {
        val s = ArtifactScoring.score(art("HP(고정)" to "1,234", "원소 마스터리" to "40", "속도" to "5"))
        assertEquals(0.0, s.cv)
        assertTrue(s.isEmpty)
    }

    // ── 게임별 라벨 흡수 ─────────────────────────────────────────────────────

    @Test
    fun sameScoreAcrossGameLabelVariants() {
        // ZZZ 는 계정 언어에 따라 영문 라벨이 그대로 올 수 있다(zzzKrStat 미매칭분).
        val ko = ArtifactScoring.score(art("치명타 확률" to "6.0%", "치명타 피해" to "12.0%"))
        val en = ArtifactScoring.score(art("CRIT Rate" to "6.0%", "CRIT DMG" to "12.0%"))
        assertEquals(ko.cv, en.cv)
        assertEquals(24.0, ko.cv)
    }

    // ── 등급 밴드 ───────────────────────────────────────────────────────────

    @Test
    fun gradeBandsAreInclusiveAtBoundaries() {
        assertEquals(ArtifactGrade.EXCELLENT, ArtifactScoring.gradeOf(40.0))
        assertEquals(ArtifactGrade.GOOD, ArtifactScoring.gradeOf(39.9))
        assertEquals(ArtifactGrade.GOOD, ArtifactScoring.gradeOf(30.0))
        assertEquals(ArtifactGrade.FAIR, ArtifactScoring.gradeOf(20.0))
        assertEquals(ArtifactGrade.POOR, ArtifactScoring.gradeOf(10.0))
        assertEquals(ArtifactGrade.BAD, ArtifactScoring.gradeOf(9.9))
        assertEquals(ArtifactGrade.BAD, ArtifactScoring.gradeOf(0.0))
    }

    // ── 캐릭터 집계 ─────────────────────────────────────────────────────────

    @Test
    fun scoreCharRanksDescendingAndAveragesPerPiece() {
        val low = art("치명타 피해" to "5.0%")     // cv 5
        val high = art("치명타 확률" to "10.0%")   // cv 20
        val mid = art("치명타 피해" to "12.0%")    // cv 12
        val res = ArtifactScoring.scoreChar(listOf(low, high, mid))
        assertEquals(listOf(20.0, 12.0, 5.0), res.ranked.map { it.score.cv })
        assertEquals(37.0, res.totalCv)
        // 등급은 합계가 아니라 장당 평균 기준(게임별 칸 수 차이 흡수)
        assertEquals(37.0 / 3, res.averageCv)
        assertEquals(ArtifactGrade.POOR, res.grade)
    }

    @Test
    fun scoreCharHandlesEmptyList() {
        val res = ArtifactScoring.scoreChar(emptyList())
        assertEquals(0.0, res.totalCv)
        assertEquals(0.0, res.averageCv)
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
    fun cvLabelKeepsOneDecimal() {
        assertEquals("23.4", ArtifactScoring.cvLabel(23.44))
        assertEquals("23.5", ArtifactScoring.cvLabel(23.46))
        assertEquals("24.0", ArtifactScoring.cvLabel(23.96))
        assertEquals("0.0", ArtifactScoring.cvLabel(0.0))
    }
}
