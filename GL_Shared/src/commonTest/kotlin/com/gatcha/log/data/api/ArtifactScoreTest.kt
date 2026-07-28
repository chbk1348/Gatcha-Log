package com.gatcha.log.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 유물 평가 산식 — **게임마다 커뮤니티 표준이 다르다.**
 *  - 원신: 아카샤 CV(치확×2 + 치피). 유효옵션과 무관.
 *  - 스타레일·젠레스: 유효 롤(부옵션 ÷ 최대 강화량), 캐릭터 유효옵션만 합산.
 *
 * 숫자가 그대로 화면에 뜨고 유저가 외부 사이트와 대조하는 값이라, 경계·예외를 여기 고정한다.
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

    // ── 지표 선택 ───────────────────────────────────────────────────────────

    @Test
    fun metricIsPerGame() {
        assertEquals(ScoreMetric.CRIT_VALUE, metricOf("genshin"))
        assertEquals(ScoreMetric.ROLL_VALUE, metricOf("hsr"))
        assertEquals(ScoreMetric.ROLL_VALUE, metricOf("zzz"))
    }

    // ── 원신: 아카샤 CV ─────────────────────────────────────────────────────

    @Test
    fun critValueIsCritRateTimesTwoPlusCritDmg() {
        // 아카샤 산식. 치확 7.78 × 2 + 치피 15.54 = 31.1
        val s = ArtifactScoring.score(art("치명타 확률" to "7.78%", "치명타 피해" to "15.54%"), giCrit, "genshin")
        assertEquals(31.1, s.value, 0.0001)
        assertEquals(ScoreMetric.CRIT_VALUE, s.metric)
    }

    @Test
    fun critValueIgnoresNonCritSubstats() {
        // 치명타 외 스탯은 CV 에 안 들어간다 — 산식의 성격이지 버그가 아니다.
        val s = ArtifactScoring.score(art("공격력(%)" to "5.83%", "원소 마스터리" to "23.31%"), giCrit, "genshin")
        assertEquals(0.0, s.value)
        assertTrue(s.isEmpty)
    }

    @Test
    fun critValueDoesNotDependOnKeyStats() {
        // 아카샤 CV 는 캐릭터 유효옵션과 무관하다 — 힐러 유효옵션으로 재도 치명타가 붙었으면 점수가 난다.
        // (유효옵션은 원신에서 '화면 빨간 강조' 전용으로만 남는다)
        val healer = setOf(StatTok.HP_PCT, StatTok.HEAL)
        val a = ArtifactScoring.score(art("치명타 피해" to "15.54%"), giCrit, "genshin")
        val b = ArtifactScoring.score(art("치명타 피해" to "15.54%"), healer, "genshin")
        assertEquals(a.value, b.value)
        assertEquals(15.54, b.value, 0.0001)
    }

    @Test
    fun critValueExcludesMainStat() {
        // 메인 옵션은 사용자가 골라서 맞추는 값 → '굴림 운'을 재는 점수에서 제외(아카샤 유물 비교도 동일).
        val s = ArtifactScoring.score(art("효과 저항" to "5.8%", main = "치명타 피해" to "62.2%"), giCrit, "genshin")
        assertEquals(0.0, s.value)
    }

    @Test
    fun critValueSumsRepeatedCritLines() {
        val s = ArtifactScoring.score(art("치명타 확률" to "3.89%", "치명타 확률" to "3.89%"), giCrit, "genshin")
        assertEquals(3.89 * 2 * 2, s.value, 0.0001)
    }

    // ── 스타레일·젠레스: 유효 롤 ────────────────────────────────────────────

    @Test
    fun rollsAreSubstatValueDividedByMaxRoll() {
        // 스타레일 치확 최대 롤 3.24 / 치피 6.48.
        val hunt = setOf(StatTok.CRIT_RATE, StatTok.CRIT_DMG, StatTok.ATK_PCT)
        val s = ArtifactScoring.score(art("치명타 확률" to "6.48%", "치명타 피해" to "12.96%"), hunt, "hsr")
        assertEquals(4.0, s.value, 0.0001)   // 2롤 + 2롤
        assertEquals(ScoreMetric.ROLL_VALUE, s.metric)
    }

    @Test
    fun onlyKeyStatsOfThatCharacterCount() {
        // 힐러(풍요) 유효옵션 = HP%·치유·속도 → 치명타는 아무리 잘 떠도 0점.
        val healer = setOf(StatTok.HP_PCT, StatTok.HEAL, StatTok.SPD)
        val s = ArtifactScoring.score(art("치명타 확률" to "12.9%", "치명타 피해" to "25.9%"), healer, "hsr")
        assertEquals(0.0, s.value)
        assertTrue(s.isEmpty)
    }

    @Test
    fun differentStatUnitsAreComparableAfterRollConversion() {
        // 스타레일 속도(1롤 2.6)와 치명타 피해(1롤 6.48)는 값 크기가 다르지만 롤로는 같은 2롤.
        val harmony = setOf(StatTok.SPD, StatTok.EHR, StatTok.ATK_PCT, StatTok.BREAK)
        val hunt = setOf(StatTok.CRIT_RATE, StatTok.CRIT_DMG, StatTok.ATK_PCT, StatTok.SPD)
        assertEquals(2.0, ArtifactScoring.score(art("속도" to "5.2"), harmony, "hsr").value, 0.0001)
        assertEquals(2.0, ArtifactScoring.score(art("치명타 피해" to "12.96%"), hunt, "hsr").value, 0.0001)
    }

    @Test
    fun sameStatScoresDifferentlyPerGameTable() {
        // 치확 1롤: 스타레일 3.24 · 젠레스 2.4.
        val subs = arrayOf("치명타 확률" to "9.72%")
        assertEquals(3.0, ArtifactScoring.score(art(*subs), giCrit, "hsr").value, 0.0001)
        assertEquals(9.72 / 2.4, ArtifactScoring.score(art(*subs), giCrit, "zzz").value, 0.0001)
    }

    @Test
    fun mainOnlyStatsScoreZeroEvenWhenInKeySet() {
        // 속성 피해·치유는 메인 전용이라 최대 롤값 표에 없다 → 0 (크래시 없이 무시).
        val s = ArtifactScoring.score(art("화염 속성 피해 보너스" to "46.6%"), giCrit, "hsr")
        assertEquals(0.0, s.value)
    }

    @Test
    fun rollValueExcludesMainStat() {
        val hunt = setOf(StatTok.CRIT_RATE, StatTok.CRIT_DMG)
        val s = ArtifactScoring.score(art("효과 저항" to "5.8%", main = "치명타 피해" to "62.2%"), hunt, "hsr")
        assertEquals(0.0, s.value)
    }

    // ── 게임별 라벨 흡수 ─────────────────────────────────────────────────────

    @Test
    fun sameScoreAcrossGameLabelVariants() {
        // ZZZ 는 계정 언어에 따라 영문 라벨이 그대로 올 수 있다(zzzKrStat 미매칭분).
        val ko = ArtifactScoring.score(art("치명타 확률" to "4.8%", "치명타 피해" to "9.6%"), giCrit, "zzz")
        val en = ArtifactScoring.score(art("CRIT Rate" to "4.8%", "CRIT DMG" to "9.6%"), giCrit, "zzz")
        assertEquals(ko.value, en.value)
        assertEquals(4.0, ko.value, 0.0001)   // 치확 2롤 + 치피 2롤
    }

    // ── 유효옵션 판정(UI 빨간 강조와 동일 소스) ───────────────────────────────

    @Test
    fun isEffectiveMatchesKeyStatRules() {
        val harmony = setOf(StatTok.SPD, StatTok.EHR)
        assertTrue(ArtifactScoring.isEffective(harmony, "속도"))
        assertTrue(ArtifactScoring.isEffective(harmony, "효과 명중"))
        // 치명타는 화합 캐릭 유효옵션이 아니다 — 강조에서 빠져야 한다.
        assertTrue(!ArtifactScoring.isEffective(harmony, "치명타 피해"))
        assertTrue(!ArtifactScoring.isEffective(harmony, "알 수 없는 스탯"))
    }

    // ── 등급 밴드 ───────────────────────────────────────────────────────────

    @Test
    fun critValueGradeBands() {
        // 원신 커뮤니티 통상치 — 1장 40 이상이면 최상급.
        val m = ScoreMetric.CRIT_VALUE
        assertEquals(ArtifactGrade.EXCELLENT, ArtifactScoring.gradeOf(40.0, m))
        assertEquals(ArtifactGrade.GOOD, ArtifactScoring.gradeOf(39.9, m))
        assertEquals(ArtifactGrade.GOOD, ArtifactScoring.gradeOf(30.0, m))
        assertEquals(ArtifactGrade.FAIR, ArtifactScoring.gradeOf(20.0, m))
        assertEquals(ArtifactGrade.POOR, ArtifactScoring.gradeOf(10.0, m))
        assertEquals(ArtifactGrade.BAD, ArtifactScoring.gradeOf(9.9, m))
        assertEquals(ArtifactGrade.BAD, ArtifactScoring.gradeOf(0.0, m))
    }

    @Test
    fun rollValueGradeBands() {
        val m = ScoreMetric.ROLL_VALUE
        assertEquals(ArtifactGrade.EXCELLENT, ArtifactScoring.gradeOf(6.0, m))
        assertEquals(ArtifactGrade.GOOD, ArtifactScoring.gradeOf(5.9, m))
        assertEquals(ArtifactGrade.GOOD, ArtifactScoring.gradeOf(4.5, m))
        assertEquals(ArtifactGrade.FAIR, ArtifactScoring.gradeOf(3.0, m))
        assertEquals(ArtifactGrade.POOR, ArtifactScoring.gradeOf(1.5, m))
        assertEquals(ArtifactGrade.BAD, ArtifactScoring.gradeOf(1.4, m))
    }

    @Test
    fun gradeBandsDifferByMetric() {
        // 같은 숫자라도 지표가 다르면 등급이 다르다 — 게임 간 점수 비교가 성립하지 않는 이유.
        assertEquals(ArtifactGrade.EXCELLENT, ArtifactScoring.gradeOf(6.0, ScoreMetric.ROLL_VALUE))
        assertEquals(ArtifactGrade.BAD, ArtifactScoring.gradeOf(6.0, ScoreMetric.CRIT_VALUE))
    }

    // ── 캐릭터 집계 ─────────────────────────────────────────────────────────

    @Test
    fun scoreCharRanksDescendingAndAveragesPerPiece() {
        val hunt = setOf(StatTok.CRIT_RATE, StatTok.CRIT_DMG)
        val low = art("치명타 피해" to "6.48%")     // 1롤
        val high = art("치명타 확률" to "9.72%")    // 3롤
        val mid = art("치명타 피해" to "12.96%")    // 2롤
        val res = ArtifactScoring.scoreChar(listOf(low, high, mid), hunt, "hsr")
        assertEquals(listOf(3.0, 2.0, 1.0), res.ranked.map { (it.score.value * 10).toInt() / 10.0 })
        assertEquals(6.0, res.total, 0.0001)
        // 등급은 합계가 아니라 장당 평균 기준(게임별 칸 수 차이 흡수)
        assertEquals(2.0, res.average, 0.0001)
        assertEquals(ArtifactGrade.POOR, res.grade)
        assertEquals(ScoreMetric.ROLL_VALUE, res.metric)
    }

    @Test
    fun scoreCharUsesCritValueForGenshin() {
        val a = art("치명타 확률" to "10.0%")                          // CV 20
        val b = art("치명타 피해" to "20.0%", "치명타 확률" to "10.0%")  // CV 40
        val res = ArtifactScoring.scoreChar(listOf(a, b), giCrit, "genshin")
        assertEquals(60.0, res.total, 0.0001)
        assertEquals(30.0, res.average, 0.0001)
        assertEquals(ArtifactGrade.GOOD, res.grade)   // 장당 평균 30 → 상
        assertEquals(ScoreMetric.CRIT_VALUE, res.metric)
        assertEquals(40.0, res.ranked.first().score.value, 0.0001)   // 내림차순
    }

    @Test
    fun scoreCharHandlesEmptyList() {
        val res = ArtifactScoring.scoreChar(emptyList(), giCrit, "genshin")
        assertEquals(0.0, res.total)
        assertEquals(0.0, res.average)
        assertTrue(res.ranked.isEmpty())
        assertEquals(ArtifactGrade.BAD, res.grade)
    }

    // ── 파싱·표기 ───────────────────────────────────────────────────────────

    @Test
    fun parseStatValueHandlesDisplayFormats() {
        assertEquals(62.2, ArtifactScoring.parseStatValue("62.2%"))
        assertEquals(5.4, ArtifactScoring.parseStatValue("+5.4%"))
        assertEquals(1234.0, ArtifactScoring.parseStatValue("1,234"))
        assertEquals(7.0, ArtifactScoring.parseStatValue("7"))
        assertEquals(0.0, ArtifactScoring.parseStatValue(""))
        assertEquals(0.0, ArtifactScoring.parseStatValue("—"))
    }

    @Test
    fun scoreLabelKeepsOneDecimal() {
        assertEquals("5.4", ArtifactScoring.scoreLabel(5.44))
        assertEquals("5.5", ArtifactScoring.scoreLabel(5.46))
        assertEquals("6.0", ArtifactScoring.scoreLabel(5.96))
        assertEquals("0.0", ArtifactScoring.scoreLabel(0.0))
    }
}
