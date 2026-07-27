package com.gatcha.log.data.api

import com.gatcha.log.util.fixed

/**
 * 유물/성유물/드라이브 디스크의 **유효 점수** 평가 — 순수 함수·오프라인.
 *
 * 산식 = **유효 롤(Roll Value)**. 서브 옵션 값을 그 스탯의 *최대 1회 강화량*으로 나눠
 * "몇 번 굴러 붙었나"로 환산하고, **그 캐릭터의 유효옵션에 해당하는 스탯만** 합산한다.
 * 스탯마다 강화 단위가 달라(스타레일 속도 2.6 vs 치명타 피해 6.48) 값을 그대로 더하면
 * 비교가 안 되는데, 롤로 환산하면 한 축에서 비교된다.
 *
 * 유효옵션 판정은 [KeyStatRules.keyStats] 재사용 — 원신은 캐릭 예외맵, 스타레일은 운명의 길,
 * 젠레스는 직업으로 유도한다. 그래서 힐러의 치명타나 딜러의 효과 저항은 점수에 안 들어간다.
 *
 * 규칙:
 * - **서브 옵션만 계산한다.** 메인 옵션(성배 치확·모래 공퍼 등)은 사용자가 고르는 값이라
 *   '굴림 운'을 재는 취지에 맞지 않는다(커뮤니티 표준도 동일).
 * - 라벨 정규화는 [normStat] 재사용 — 게임별 표기 차이를 흡수한다.
 * - 값은 "62.2%" 같은 표시 문자열이라 숫자만 파싱한다(천 단위 콤마·선행 기호 허용).
 * - 최대 강화량 표가 없는 스탯(메인 전용인 원소 피해·치유 등)은 0 — 점수만 낮아질 뿐 안 터진다.
 */

/** 유효 점수 등급 밴드. 1장 최대 9롤 기준의 통상적인 체감 구간. */
enum class ArtifactGrade(val label: String) {
    EXCELLENT("최상"),
    GOOD("상"),
    FAIR("중"),
    POOR("하"),
    BAD("최하"),
}

/** 유물 1장의 유효 점수. [rolls]=유효옵션에 붙은 강화 횟수 환산값. */
data class ArtifactScore(
    val rolls: Double,
    val grade: ArtifactGrade,
) {
    /** 유효옵션이 하나도 안 붙었으면 true — UI 에서 순위 배지 대신 비워둘 때 사용. */
    val isEmpty: Boolean get() = rolls <= 0.0
}

/** 유물 1장 + 그 점수(랭킹 표시용). */
data class RankedArtifact(val artifact: EnkaArtifact, val score: ArtifactScore)

/**
 * 캐릭터 1인의 유물 세트 집계.
 * [grade] 는 합계가 아니라 **장당 평균 롤** 기준 — 장수가 다른 게임(원신 5칸·HSR 6칸·ZZZ 6칸)을
 * 같은 잣대로 비교하기 위함이다.
 */
data class CharArtifactScore(
    val totalRolls: Double,
    val averageRolls: Double,
    val grade: ArtifactGrade,
    /** 유효 롤 내림차순 — 1위가 가장 잘 뽑힌 유물, 마지막이 교체 1순위 후보. */
    val ranked: List<RankedArtifact>,
)

object ArtifactScoring {

    /** 1장이 가질 수 있는 최대 강화 횟수 — 초기 서브 4개 + 강화 5회. 3게임 공통. */
    const val MAX_ROLLS_PER_PIECE = 9.0

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

    /** 유물 1장의 유효 점수(서브 옵션 기준). */
    fun score(artifact: EnkaArtifact, keySet: Set<StatTok>, gameKey: String): ArtifactScore {
        var rolls = 0.0
        artifact.subs.forEach { s ->
            val tok = normStat(s.label)
            if (tok == StatTok.OTHER || tok !in keySet) return@forEach
            val max = maxRollOf(gameKey, tok) ?: return@forEach
            if (max > 0.0) rolls += parseStatValue(s.value) / max
        }
        return ArtifactScore(rolls, gradeOf(rolls))
    }

    /** 캐릭터가 착용한 유물 전체를 채점하고 유효 롤 내림차순으로 정렬. 빈 목록이면 0점. */
    fun scoreChar(artifacts: List<EnkaArtifact>, keySet: Set<StatTok>, gameKey: String): CharArtifactScore {
        val ranked = artifacts.map { RankedArtifact(it, score(it, keySet, gameKey)) }
            .sortedByDescending { it.score.rolls }
        val total = ranked.sumOf { it.score.rolls }
        val avg = if (ranked.isEmpty()) 0.0 else total / ranked.size
        return CharArtifactScore(total, avg, gradeOf(avg), ranked)
    }

    /** 유효 롤 → 등급 밴드. 1장 최대 9롤이라 6롤 이상이면 유효옵션이 대부분 붙은 것. */
    fun gradeOf(rolls: Double): ArtifactGrade = when {
        rolls >= 6.0 -> ArtifactGrade.EXCELLENT
        rolls >= 4.5 -> ArtifactGrade.GOOD
        rolls >= 3.0 -> ArtifactGrade.FAIR
        rolls >= 1.5 -> ArtifactGrade.POOR
        else -> ArtifactGrade.BAD
    }

    /** 소수 1자리 표기 — "5.4". 양 플랫폼이 같은 표기를 쓰도록 공유 [fixed] 를 재사용한다. */
    fun rollLabel(rolls: Double): String = fixed(rolls, 1)

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
