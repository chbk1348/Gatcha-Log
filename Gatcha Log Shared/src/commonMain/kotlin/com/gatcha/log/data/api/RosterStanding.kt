package com.gatcha.log.data.api

import com.gatcha.log.util.fixed
import kotlin.math.roundToInt

/**
 * 로스터 안에서의 상대 위치 — "이 캐릭터 잘 컸나"에 답하기 위한 순위·백분위.
 *
 * 유물 점수([ArtifactScoring.scoreChar])는 지금까지 **그 캐릭터 안에서만** 쓰고 버렸다.
 * 같은 계산을 로스터 전체로 펼치면 순위가 새 데이터 없이 나온다.
 *
 * ## 모수를 좁히는 이유
 *
 * 그냥 세면 순위가 거짓말을 한다.
 * - **게임을 섞으면 안 된다.** 원신은 CV, 스타레일·젠레스는 유효 롤이라 스케일이 다르다
 *   ([ArtifactScore] 문서). 47명을 한 줄로 세우는 순간 지표가 뒤섞인다.
 * - **상세가 없는 캐릭터는 셀 수 없다.** HoYoLAB 미연동이면 Enka 는 인게임에서 공개한
 *   쇼케이스분만 준다([EnkaApi] `avatarInfoList`). 유물이 없으니 점수가 0이고,
 *   0점 40명 위에 서서 "1위"가 된다.
 * - **육성 안 한 캐릭터도 셀 수 없다.** 레벨 1짜리가 분모에 있으면 순위가 부풀려진다.
 *
 * 그래서 모수는 **같은 게임 · [DETAIL 있음] · 유물 장착 · [MIN_LEVEL] 이상**이고,
 * 그마저 [MIN_POOL] 명이 안 되면 순위를 내지 않는다([hasRank] = false).
 * "3명 중 1위"는 알려 줄 값어치가 없다.
 *
 * @param rank 1부터. 동점은 같은 순위(경쟁 순위). [hasRank] 가 false 면 0
 * @param pool 순위 계산에 실제로 들어간 인원 — 화면에 **반드시 함께 밝힌다**
 * @param percentile 상위 N%(1~100). [hasRank] 가 false 면 0
 * @param detailedCount 이 게임에서 상세를 읽을 수 있었던 인원(모수 미달 사유 안내용)
 */
data class RosterStanding(
    val rank: Int,
    val pool: Int,
    val percentile: Int,
    val hasRank: Boolean,
    val detailedCount: Int,
) {
    companion object {
        /** 순위를 그리기 위한 최소 모수. 이보다 적으면 비교가 아니라 조롱이 된다. */
        const val MIN_POOL = 10

        /** 모수에 넣을 최소 레벨 — 육성을 시작한 캐릭터만 센다. */
        const val MIN_LEVEL = 70

        /** 순위 없음. */
        val NONE = RosterStanding(0, 0, 0, false, 0)
    }
}

/**
 * 다음 한 걸음 — 진단만 하고 끝내지 않기 위한 한 줄.
 *
 * **목표 순위를 약속하지 않는다.** "치확을 5% 올리면 상위 10%" 같은 문장은 다른 캐릭터가
 * 그대로 멈춰 있다는 전제에서만 참이고, 게임마다 지표가 달라 %p 환산도 원신에서만 성립한다.
 * 대신 **지금 가장 약한 유효옵션**과 **가장 먼저 손댈 유물**만 짚는다 — 둘 다 지금 값으로
 * 확정할 수 있는 사실이다.
 *
 * 조사(-이/-가)를 피해 조각을 그대로 이어 붙일 수 있게 만들었다. 양 플랫폼이 같은 순서로
 * 조립한다: "가장 약한 곳은 **[statLabel]**([valueLabel]) — **[slotLabel]** 부터 …"
 */
data class NextStep(
    val statLabel: String,
    val valueLabel: String,
    val slotLabel: String,
)

object RosterStandings {

    /**
     * 모수에 넣을 수 있는 캐릭터인가 — 상세가 있고, 유물을 꼈고, 레벨이 [RosterStanding.MIN_LEVEL] 이상.
     */
    fun isRankable(c: EnkaChar): Boolean =
        c.detailed && c.artifacts.isNotEmpty() && c.level >= RosterStanding.MIN_LEVEL

    /**
     * [target] 이 [roster] 안에서 몇 위인지. [roster] 는 **같은 게임**의 캐릭터만 넘긴다.
     *
     * 각 캐릭터의 점수는 그 캐릭터의 유효옵션으로 매긴다([resolveKeyStats]) — 유효옵션이
     * 다르면 같은 유물도 점수가 다르므로, 하나의 기준으로 몰아 재면 비교가 어긋난다.
     * (원신 CV 는 유효옵션과 무관해 결과가 같지만, 스타레일·젠레스는 달라진다.)
     */
    fun of(
        target: EnkaChar,
        roster: List<EnkaChar>,
        gameKey: String,
        overrides: Map<String, Set<String>> = emptyMap(),
    ): RosterStanding {
        // 점수를 안 쓰는 게임은 순위도 없다 — 순위는 점수를 줄 세운 것이라 같이 무너진다.
        if (!usesArtifactScore(gameKey)) return RosterStanding.NONE
        val detailed = roster.count { it.detailed }
        val pool = roster.filter { isRankable(it) }
        if (!isRankable(target) || pool.size < RosterStanding.MIN_POOL) {
            return RosterStanding.NONE.copy(detailedCount = detailed)
        }

        val myScore = totalOf(target, gameKey, overrides)
        // 동점은 같은 순위 — 나보다 **높은** 점수만 센다.
        val above = pool.count { it.id != target.id && totalOf(it, gameKey, overrides) > myScore }
        val rank = above + 1
        // 상위 N% — 1위가 0% 로 보이지 않게 올림한다. 100 을 넘지 않는다.
        val percentile = ((rank.toDouble() / pool.size) * 100).roundToInt().coerceIn(1, 100)
        return RosterStanding(rank, pool.size, percentile, true, detailed)
    }

    /** 캐릭터 1인의 유물 총점 — 그 캐릭터의 유효옵션 기준. */
    private fun totalOf(c: EnkaChar, gameKey: String, overrides: Map<String, Set<String>>): Double {
        val keys = resolveKeyStats(gameKey, c, overrides).stats
        return ArtifactScoring.scoreChar(c.artifacts, keys, gameKey).total
    }

    /**
     * 다음 한 걸음. 유물이 없거나 유효옵션을 판정할 수 없으면 **null** — 근거 없는 조언은 하지 않는다.
     *
     * - **가장 약한 유효옵션**: 착용 유물의 서브 옵션에서 그 스탯이 몇 롤 붙었는지 세어 최솟값.
     *   원신은 CV 지표라 유효옵션과 무관하게 **치확·치피**만 본다(점수를 그것만으로 매기므로).
     * - **먼저 손댈 유물**: 점수 최하위 한 장([CharArtifactScore.ranked] 의 마지막).
     */
    fun nextStep(
        c: EnkaChar,
        gameKey: String,
        overrides: Map<String, Set<String>> = emptyMap(),
    ): NextStep? {
        if (c.artifacts.isEmpty()) return null
        val keys = resolveKeyStats(gameKey, c, overrides).stats
        val scored = ArtifactScoring.scoreChar(c.artifacts, keys, gameKey)
        val weakest = scored.ranked.lastOrNull()?.artifact ?: return null

        val candidates = if (metricOf(gameKey) == ScoreMetric.CRIT_VALUE) {
            listOf(StatTok.CRIT_RATE, StatTok.CRIT_DMG)
        } else {
            orderedKeyStats(gameKey, keys)
        }
        if (candidates.isEmpty()) return null

        // 스탯별 누적 롤 수 — 값이 아니라 '몇 번 굴렀나'로 봐야 스탯 간 비교가 성립한다.
        val rolls = candidates.associateWith { tok -> rollsOf(c, tok, gameKey) }
        val target = rolls.minByOrNull { it.value }?.key ?: return null

        return NextStep(
            statLabel = statLabel(target, gameKey),
            valueLabel = "${fixed(rolls[target] ?: 0.0, 1)}롤",
            slotLabel = weakest.slot.ifBlank { weakest.setName },
        )
    }

    /**
     * 캐릭터 **최종 스탯** 기준 치명 효율 — 치확×2 + 치피.
     *
     * 유물 점수와 다른 축이다. 저쪽은 굴림 운(서브 옵션)만 보지만 이건 무기·세트·돌파까지
     * 합쳐진 결과값이다. 순위를 낼 수 없을 때 사실 칸을 비우지 않고 채우는 데 쓴다 —
     * 지출 상세가 재화 개수를 못 구할 때 칸 내용을 바꾸는 것과 같은 방식이다.
     *
     * 치확·치피를 스탯에서 못 찾으면 null. 없는 값을 0 으로 보여주면 "치명 효율 0" 이 된다.
     */
    fun critEfficiency(c: EnkaChar): Double? {
        var rate: Double? = null
        var dmg: Double? = null
        c.stats.forEach { line ->
            when (normStat(line.label)) {
                StatTok.CRIT_RATE -> rate = ArtifactScoring.parseStatValue(line.value)
                StatTok.CRIT_DMG -> dmg = ArtifactScoring.parseStatValue(line.value)
                else -> Unit
            }
        }
        val r = rate
        val d = dmg
        if (r == null && d == null) return null
        return (r ?: 0.0) * 2 + (d ?: 0.0)
    }

    /** 착용 유물 전체에서 [tok] 이 몇 롤 붙었는지. 최대 강화량을 모르는 스탯은 0. */
    private fun rollsOf(c: EnkaChar, tok: StatTok, gameKey: String): Double {
        val max = ArtifactScoring.maxRollOf(gameKey, tok) ?: return 0.0
        if (max <= 0.0) return 0.0
        var sum = 0.0
        c.artifacts.forEach { a ->
            a.subs.forEach { s ->
                if (normStat(s.label) == tok) sum += ArtifactScoring.parseStatValue(s.value) / max
            }
        }
        return sum
    }
}
