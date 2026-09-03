package com.gatcha.log.data.api

import com.gatcha.log.util.fixed

/**
 * 유물/성유물/드라이브 디스크 평가 — 순수 함수·오프라인.
 *
 * **게임마다 커뮤니티 표준이 다르다.** 하나로 통일하면 어느 한쪽이 반드시 그 게임 표준에서
 * 멀어지므로, 각 게임 유저가 실제로 대조하는 사이트에 맞춘다([ScoreMetric]).
 *
 * - **원신** — [akasha.cv](https://akasha.cv) 의 `CV = 치확×2 + 치피`
 * - **스타레일·젠레스** — Otameta·ScoreMyRelic·ZenlessGrid 계열의 **유효 롤**
 *   (부옵션 ÷ 최대 강화량 × 캐릭터 적합도). 우리 [KeyStatRules] 유효옵션 필터가 그 '적합도'다.
 *
 * 공통 규칙:
 * - **서브 옵션만 계산한다.** 메인 옵션(성배 치확·모래 공퍼 등)은 사용자가 골라서 맞추는 값이라
 *   '굴림 운'을 재는 취지에 안 맞는다(양쪽 표준 모두 동일).
 * - 라벨 정규화는 [normStat] 재사용 — 게임별·언어별 표기 차이를 흡수한다.
 * - 값은 "62.2%" 같은 표시 문자열이라 숫자만 파싱한다(천 단위 콤마·선행 기호 허용).
 *
 * ⚠️ 게임마다 지표가 다르므로 **게임 간 점수 비교는 성립하지 않는다.** 화면에는 [ScoreMetric.label]
 * 과 [ScoreMetric.hint] 로 무엇으로 쟀는지 반드시 함께 밝힌다.
 */

/**
 * 점수 산식 — 게임별 커뮤니티 표준.
 * 숫자만 보여주면 무엇과 비교할 수 있는 값인지 알 수 없어서, 지표 자체를 결과에 실어 나른다.
 */
enum class ScoreMetric(val label: String, val hint: String) {
    /** 원신 — 아카샤 기준. 치확 1%를 치피 2%와 같게 보는 값(인게임 굴림 비율이 1:2라서). */
    CRIT_VALUE("CV", "아카샤 기준 · 치확×2 + 치피"),

    /** 스타레일·젠레스 — 부옵션을 최대 강화량으로 나눈 '몇 롤' 환산. 캐릭터 유효옵션만 합산. */
    ROLL_VALUE("유효 롤", "캐릭터 유효옵션 기준"),
}

/** 이 게임이 쓰는 지표. */
fun metricOf(gameKey: String): ScoreMetric =
    if (gameKey == "genshin") ScoreMetric.CRIT_VALUE else ScoreMetric.ROLL_VALUE

/** 등급 밴드 — 유물 1장 기준. 실제 구간값은 지표마다 다르다([ArtifactScoring.gradeOf]). */
enum class ArtifactGrade(val label: String) {
    EXCELLENT("최상"),
    GOOD("상"),
    FAIR("중"),
    POOR("하"),
    BAD("최하"),
}

/** 유물 1장의 점수. [value] 의 의미는 [metric] 에 달렸다(CV 또는 유효 롤). */
data class ArtifactScore(
    val value: Double,
    val grade: ArtifactGrade,
    val metric: ScoreMetric,
) {
    /** 점수가 0이면 true — UI 에서 순위 배지 대신 비워둘 때 사용. */
    val isEmpty: Boolean get() = value <= 0.0
}

/** 유물 1장 + 그 점수(랭킹 표시용). */
data class RankedArtifact(val artifact: EnkaArtifact, val score: ArtifactScore)

/**
 * 캐릭터 1인의 유물 세트 집계.
 * [grade] 는 합계가 아니라 **장당 평균 롤** 기준 — 장수가 다른 게임(원신 5칸·HSR 6칸·ZZZ 6칸)을
 * 같은 잣대로 비교하기 위함이다.
 */
data class CharArtifactScore(
    val total: Double,
    val average: Double,
    val grade: ArtifactGrade,
    val metric: ScoreMetric,
    /** 점수 내림차순 — 1위가 가장 잘 뽑힌 유물, 마지막이 교체 1순위 후보. */
    val ranked: List<RankedArtifact>,
)

object ArtifactScoring {

    /**
     * 스탯별 **최대 1회 강화량**(최고 등급 기준). 값 출처는 각 게임 커뮤니티 표준표.
     * 여기 없는 토큰은 서브 옵션으로 안 붙는 스탯(원소 피해·치유·충격력 등 메인 전용).
     */
    private val GI_MAX_ROLL: Map<StatTok, Double> = mapOf(
        StatTok.HP to 298.75, StatTok.ATK to 19.45, StatTok.DEF to 23.15,
        StatTok.HP_PCT to 5.83, StatTok.ATK_PCT to 5.83, StatTok.DEF_PCT to 7.29,
        StatTok.EM to 23.31, StatTok.ER to 6.48,
        StatTok.CRIT_RATE to 3.89, StatTok.CRIT_DMG to 7.77,
    )

    private val HSR_MAX_ROLL: Map<StatTok, Double> = mapOf(
        StatTok.HP to 42.34, StatTok.ATK to 21.17, StatTok.DEF to 21.17,
        StatTok.HP_PCT to 4.32, StatTok.ATK_PCT to 4.32, StatTok.DEF_PCT to 5.40,
        StatTok.SPD to 2.6, StatTok.BREAK to 5.83,
        StatTok.EHR to 4.32, StatTok.RES to 4.32,
        StatTok.CRIT_RATE to 3.24, StatTok.CRIT_DMG to 6.48,
    )

    /** 젠레스는 강화 1회 값이 고정이라 나눈 몫이 곧 정수 롤 수가 된다. */
    private val ZZZ_MAX_ROLL: Map<StatTok, Double> = mapOf(
        StatTok.HP to 112.0, StatTok.ATK to 19.0, StatTok.DEF to 15.0,
        StatTok.HP_PCT to 3.0, StatTok.ATK_PCT to 3.0, StatTok.DEF_PCT to 4.8,
        StatTok.CRIT_RATE to 2.4, StatTok.CRIT_DMG to 4.8,
        StatTok.PEN to 9.0, StatTok.ANOM_PROF to 9.0,
    )

    /** 게임별 최대 1회 강화량. 미지원 스탯이면 null. */
    fun maxRollOf(gameKey: String, tok: StatTok): Double? = when (gameKey) {
        "hsr", "starrail" -> HSR_MAX_ROLL[tok]
        "zzz" -> ZZZ_MAX_ROLL[tok]
        else -> GI_MAX_ROLL[tok]
    }

    /**
     * 이 스탯 줄이 해당 캐릭터의 유효옵션인가.
     * UI 강조(빨간색)와 점수가 어긋나지 않도록 **양쪽이 이 함수 하나만 본다.**
     */
    fun isEffective(keySet: Set<StatTok>, label: String): Boolean =
        KeyStatRules.isKey(keySet, label)

    /** 유물 1장의 점수(서브 옵션 기준). 산식은 게임이 정한다 — [metricOf]. */
    fun score(artifact: EnkaArtifact, keySet: Set<StatTok>, gameKey: String): ArtifactScore {
        val metric = metricOf(gameKey)
        val value = when (metric) {
            ScoreMetric.CRIT_VALUE -> critValue(artifact)
            ScoreMetric.ROLL_VALUE -> rollValue(artifact, keySet, gameKey)
        }
        return ArtifactScore(value, gradeOf(value, metric), metric)
    }

    /**
     * 아카샤 CV — 치확×2 + 치피. **유효옵션과 무관하다**(치명타만 보는 지표라서).
     * 서브 옵션만 센다: 메인의 치확/치피는 사용자가 고른 것이라 굴림 운이 아니다.
     */
    private fun critValue(artifact: EnkaArtifact): Double {
        var cv = 0.0
        artifact.subs.forEach { s ->
            when (normStat(s.label)) {
                StatTok.CRIT_RATE -> cv += parseStatValue(s.value) * 2
                StatTok.CRIT_DMG -> cv += parseStatValue(s.value)
                else -> Unit
            }
        }
        return cv
    }

    /** 유효 롤 — 부옵션 값 ÷ 최대 1회 강화량. **캐릭터 유효옵션에 해당하는 스탯만** 합산. */
    private fun rollValue(artifact: EnkaArtifact, keySet: Set<StatTok>, gameKey: String): Double {
        var rolls = 0.0
        artifact.subs.forEach { s ->
            val tok = normStat(s.label)
            if (tok == StatTok.OTHER || tok !in keySet) return@forEach
            val max = maxRollOf(gameKey, tok) ?: return@forEach
            if (max > 0.0) rolls += parseStatValue(s.value) / max
        }
        return rolls
    }

    /** 캐릭터가 착용한 유물 전체를 채점하고 점수 내림차순으로 정렬. 빈 목록이면 0점. */
    fun scoreChar(artifacts: List<EnkaArtifact>, keySet: Set<StatTok>, gameKey: String): CharArtifactScore {
        val metric = metricOf(gameKey)
        val ranked = artifacts.map { RankedArtifact(it, score(it, keySet, gameKey)) }
            .sortedByDescending { it.score.value }
        val total = ranked.sumOf { it.score.value }
        val avg = if (ranked.isEmpty()) 0.0 else total / ranked.size
        return CharArtifactScore(total, avg, gradeOf(avg, metric), metric, ranked)
    }

    /**
     * 점수 → 등급 밴드. **지표마다 스케일이 달라 구간도 다르다.**
     *  - CV: 원신 커뮤니티 통상치(1장 40 이상이면 최상급).
     *  - 유효 롤: 1장 최대 9롤이라 6롤 이상이면 유효옵션이 대부분 붙은 것.
     */
    fun gradeOf(value: Double, metric: ScoreMetric): ArtifactGrade = when (metric) {
        ScoreMetric.CRIT_VALUE -> when {
            value >= 40.0 -> ArtifactGrade.EXCELLENT
            value >= 30.0 -> ArtifactGrade.GOOD
            value >= 20.0 -> ArtifactGrade.FAIR
            value >= 10.0 -> ArtifactGrade.POOR
            else -> ArtifactGrade.BAD
        }
        ScoreMetric.ROLL_VALUE -> when {
            value >= 6.0 -> ArtifactGrade.EXCELLENT
            value >= 4.5 -> ArtifactGrade.GOOD
            value >= 3.0 -> ArtifactGrade.FAIR
            value >= 1.5 -> ArtifactGrade.POOR
            else -> ArtifactGrade.BAD
        }
    }

    /** 소수 1자리 표기 — "5.4". 양 플랫폼이 같은 표기를 쓰도록 공유 [fixed] 를 재사용한다. */
    fun scoreLabel(value: Double): String = fixed(value, 1)

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
