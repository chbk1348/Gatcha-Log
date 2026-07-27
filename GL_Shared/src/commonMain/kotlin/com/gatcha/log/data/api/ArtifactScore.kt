package com.gatcha.log.data.api

import com.gatcha.log.util.fixed

/**
 * 유물/성유물/드라이브 디스크의 **치명 점수(CV, Crit Value)** 평가 — 순수 함수·오프라인.
 *
 * CV = 치명타 확률 × 2 + 치명타 피해 (커뮤니티 표준 산식).
 * 치확 1%가 치피 2%와 같은 가치라는 전제라, 두 스탯을 한 축으로 비교할 수 있다.
 *
 * 규칙:
 * - **서브 옵션만 계산한다.** 메인 옵션(성배 치확/치피 등)은 사용자가 고르는 값이라
 *   '굴림 운'을 재는 CV 의 취지에 맞지 않는다(커뮤니티 표준도 동일).
 * - 라벨 정규화는 [normStat] 재사용 — 원신 "치명타 확률", HSR/ZZZ 표기 차이를 흡수한다.
 * - 값은 "62.2%" 같은 표시 문자열이라 숫자만 파싱한다(천 단위 콤마·선행 기호 허용).
 *
 * 3게임(원신·스타레일·젠레스) 공통으로 동작하며, 게임별 분기가 없다.
 */

/** 치명 점수 등급 밴드. 최대 강화 기준의 통상적인 체감 구간. */
enum class ArtifactGrade(val label: String) {
    EXCELLENT("최상"),
    GOOD("상"),
    FAIR("중"),
    POOR("하"),
    BAD("최하"),
}

/** 유물 1장의 치명 점수. [critRate]/[critDmg] 는 서브 옵션 합(%). */
data class ArtifactScore(
    val critRate: Double,
    val critDmg: Double,
    val cv: Double,
    val grade: ArtifactGrade,
) {
    /** 치명 서브 옵션이 하나도 없으면 true — UI 에서 점수 대신 '치명 없음'을 보여줄 때 사용. */
    val isEmpty: Boolean get() = critRate <= 0.0 && critDmg <= 0.0
}

/** 유물 1장 + 그 점수(랭킹 표시용). */
data class RankedArtifact(val artifact: EnkaArtifact, val score: ArtifactScore)

/**
 * 캐릭터 1인의 유물 세트 집계.
 * [grade] 는 합계가 아니라 **장당 평균 CV** 기준 — 장수가 다른 게임(원신 5칸·HSR 6칸·ZZZ 6칸)을
 * 같은 잣대로 비교하기 위함이다.
 */
data class CharArtifactScore(
    val totalCv: Double,
    val averageCv: Double,
    val grade: ArtifactGrade,
    /** CV 내림차순 — 1위가 가장 잘 뽑힌 유물, 마지막이 교체 1순위 후보. */
    val ranked: List<RankedArtifact>,
)

object ArtifactScoring {

    /** 치명타 확률 가중치 — 치피 1% 대비 2배 가치. */
    const val CRIT_RATE_WEIGHT = 2.0

    /** 유물 1장의 치명 점수(서브 옵션 기준). */
    fun score(artifact: EnkaArtifact): ArtifactScore {
        var rate = 0.0
        var dmg = 0.0
        artifact.subs.forEach { s ->
            when (normStat(s.label)) {
                StatTok.CRIT_RATE -> rate += parseStatValue(s.value)
                StatTok.CRIT_DMG -> dmg += parseStatValue(s.value)
                else -> Unit
            }
        }
        val cv = rate * CRIT_RATE_WEIGHT + dmg
        return ArtifactScore(rate, dmg, cv, gradeOf(cv))
    }

    /** 캐릭터가 착용한 유물 전체를 채점하고 CV 내림차순으로 정렬. 빈 목록이면 0점. */
    fun scoreChar(artifacts: List<EnkaArtifact>): CharArtifactScore {
        val ranked = artifacts.map { RankedArtifact(it, score(it)) }.sortedByDescending { it.score.cv }
        val total = ranked.sumOf { it.score.cv }
        val avg = if (ranked.isEmpty()) 0.0 else total / ranked.size
        return CharArtifactScore(total, avg, gradeOf(avg), ranked)
    }

    /** CV → 등급 밴드. */
    fun gradeOf(cv: Double): ArtifactGrade = when {
        cv >= 40.0 -> ArtifactGrade.EXCELLENT
        cv >= 30.0 -> ArtifactGrade.GOOD
        cv >= 20.0 -> ArtifactGrade.FAIR
        cv >= 10.0 -> ArtifactGrade.POOR
        else -> ArtifactGrade.BAD
    }

    /** 소수 1자리 표기 — "23.4". 양 플랫폼이 같은 표기를 쓰도록 공유 [fixed] 를 재사용한다. */
    fun cvLabel(cv: Double): String = fixed(cv, 1)

    /**
     * 표시용 스탯 문자열에서 숫자만 뽑는다. "62.2%" → 62.2, "1,234" → 1234.0, "+5.4%" → 5.4.
     * 파싱 실패는 0.0 — 새 표기가 들어와도 점수만 낮아질 뿐 크래시나지 않는다.
     */
    internal fun parseStatValue(raw: String): Double {
        val sb = StringBuilder()
        var started = false
        for (ch in raw) {
            when {
                ch.isDigit() || ch == '.' -> { sb.append(ch); started = true }
                ch == ',' && started -> Unit                       // 천 단위 구분자
                started -> return sb.toString().toDoubleOrNull() ?: 0.0  // 숫자 구간 종료
                else -> Unit                                        // 선행 기호(+ 등)
            }
        }
        return sb.toString().toDoubleOrNull() ?: 0.0
    }
}
