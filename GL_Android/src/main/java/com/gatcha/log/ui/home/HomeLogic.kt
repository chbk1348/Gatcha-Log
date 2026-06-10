package com.gatcha.log.ui.home

import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.GachaRateData
import com.gatcha.log.data.GameData
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.PityState
import com.gatcha.log.data.pityTierOf

/**
 * 홈 화면(HomeContent)의 순수 파생 계산 모음 — @Composable 본문에서 분리해 단위 테스트 가능하게 함.
 *
 * 입력은 ViewModel 상태 스냅샷, 출력은 홈 표시 모델([PityHighlight]·[GameSpend]·[BannerPlan]·[ResinAlert]).
 * 기존 inline `remember { ... }` 블록을 동작·결과 동일하게 그대로 옮긴 것이라 UI 변화 없음.
 */

/** 게임별 한도 초과 게임(이번 달)의 짧은 이름 목록 — 알림센터 표시용. */
internal fun computeGameOverBudget(
    gameBudgets: Map<String, Long>,
    totalsByGame: Map<String, Long>,
): List<String> {
    if (gameBudgets.isEmpty()) return emptyList()
    return GameData.games.mapNotNull { g ->
        val limit = gameBudgets[g.key] ?: 0L
        if (limit > 0 && (totalsByGame[g.key] ?: 0L) > limit) g.shortName else null
    }
}

/** 상황별 절약 팁(M 카드 '절약 팁' 칩이 토스트로 노출). */
internal fun savingTipFor(budget: Long, monthlyTotal: Long, gameOverBudget: List<String>): String =
    when {
        budget > 0 && monthlyTotal > budget -> "이번 달은 예산을 넘겼어요. 다음 픽업까지 무·저과금으로 천장을 모아보세요."
        gameOverBudget.isNotEmpty() -> "${gameOverBudget.first()} 한도를 넘었어요. 게임별 예산을 점검해보세요."
        budget <= 0 -> "월 예산을 정하면 페이스를 알려드려요. 보통 한 달 결제액의 80% 선이 적당해요."
        else -> "천장이 가까운 게임부터 모으면 50/50 손해를 줄일 수 있어요."
    }

/** 가장 임박한(티어 높고 카운트 큰) 천장 1종 (M 요약·K 토널 공유). */
internal fun computeTopPity(pity: Map<String, PityState>): PityHighlight? =
    GameData.games.mapNotNull { g ->
        val st = pity[g.key] ?: return@mapNotNull null
        if (st.count <= 0) return@mapNotNull null
        val banner = GachaRateData.byKey(g.key)?.character ?: return@mapNotNull null
        PityHighlight(g, st.count, banner.softPity, banner.hardPity, pityTierOf(st.count, banner))
    }.maxWithOrNull(compareBy({ it.tier.ordinal }, { it.count }))

/** 게임별 이번 달 지출/한도 (D 섹션) — 지출 있거나 한도 설정된 게임만, 지출 내림차순. */
internal fun computePerGameSpend(
    totalsByGame: Map<String, Long>,
    gameBudgets: Map<String, Long>,
): List<GameSpend> =
    GameData.games.mapNotNull { g ->
        val spent = totalsByGame[g.key] ?: 0L
        val limit = gameBudgets[g.key] ?: 0L
        if (spent <= 0L && limit <= 0L) null else GameSpend(g, spent, limit)
    }.sortedByDescending { it.spent }

/** 임박 픽업 배너 — 7일 이내 종료, D-day 오름차순, 최대 4 (상시/종료 배너는 dDay 범위 밖으로 자연 제외). */
internal fun computeSoonBanners(banners: List<GachaBanner>): List<GachaBanner> =
    banners.filter { it.dDay() in 0..7 }.sortedBy { it.dDay() }.take(4)

/** 다음 픽업 확정 비용(가챠×지출) — 천장 누적·확률·1뽑 단가로 산출. */
internal fun computeNextBannerPlan(nextBanner: GachaBanner?, pity: Map<String, PityState>): BannerPlan? =
    nextBanner?.let { b ->
        val g = GameData.byNameOrNull(b.game) ?: return@let null
        val rate = GachaRateData.byKey(g.key)?.character ?: return@let null
        val st = pity[g.key]
        val pulls = GachaRateData.maxPullsToSecure(st?.count ?: 0, st?.guaranteed ?: false, rate)
        BannerPlan(pulls, pulls.toLong() * rate.wonPerPull)
    }

/** 재화 임박 경보 — 85% 이상(가득 직전)인 게임 '전부', 가장 찬 순. */
internal fun computeResinAlerts(liveNotes: List<LiveNote>): List<ResinAlert> =
    liveNotes.filter { it.maxResin > 0 && it.resinRatio >= 0.85f }
        .sortedByDescending { it.resinRatio }
        .map {
            ResinAlert(
                GameData.byName(it.game).shortName, it.resinLabel,
                it.currentResin, it.maxResin, it.resinRecoveryTime,
                it.currentResin >= it.maxResin,
            )
        }
