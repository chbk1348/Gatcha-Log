package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlin.math.ceil

/**
 * 픽업 대비 저축 플래너 — 진행 중 픽업까지 "필요 뽑기/재화/원화"와 "하루 저축 목표"를 역산한 결과.
 * 전부 기존 확률·요율 기반 결정형(AI 없음). 계산 소스:
 *  - 필요 뽑기 = [GachaRateData.maxPullsToSecure](현재 천장·50/50 반영, 최악 기준)
 *  - 원화 환산 = neededPulls × [GachaBannerRate.wonPerPull]
 *  - 남은 필요 = 보유 재화([heldCurrency]) 차감 후
 *  - 일일 목표 = ceil(남은필요원화 / max(1, D-day))
 */
data class SavingsPlan(
    val game: String,            // 표시 게임명 (예: "원신")
    val gameKey: String,         // 게임 키 (예: "genshin")
    val gameColor: Long,
    val pickupName: String,
    val type: String,            // "character" | "weapon"
    val endMillis: Long,
    val dDay: Int,
    val currency: String,        // 재화명 (예: "원석")
    val perPull: Int,            // 1뽑당 재화
    val currentPity: Int,
    val guaranteed: Boolean,
    val neededPulls: Int,        // 현재 천장 기준 확정까지 최악 필요 뽑기
    val heldCurrency: Int,       // 사용자 입력 보유 재화(0 = 미입력)
    val neededCurrencyTotal: Int,// neededPulls × perPull
    val remainingCurrency: Int,  // 보유 차감 후 남은 필요 재화
    val remainingPulls: Int,     // 남은 필요 뽑기
    val neededWonTotal: Long,    // neededPulls × wonPerPull (진행률 기준 전체 비용)
    val remainingWon: Long,      // 남은 필요 원화
    val savedWon: Long,          // 보유 재화의 원화 환산 가치(진행바용)
    val progressPercent: Int,    // 0..100
    val dailyGoal: Long,         // 하루 저축 목표(원). secured면 0
    val secured: Boolean,        // 보유만으로 확보 가능
) {
    /** 픽업 식별 키 — "안 뽑는" 목표 숨김 저장/조회용(gameKey|type|pickupName). 세션 간 안정. */
    val key: String get() = "$gameKey|$type|$pickupName"
}

object SavingsPlanner {

    /**
     * 진행 중 픽업 배너 목록에서 저축 계획을 만든다. 요율 정보가 없는 게임/배너는 제외.
     * D-day 오름차순(임박 우선) 정렬.
     */
    fun build(
        banners: List<GachaBanner>,
        pity: Map<String, PityState>,
        held: Map<String, Int> = emptyMap(),
        nowMillis: Long = currentTimeMillis(),
    ): List<SavingsPlan> = banners.mapNotNull { b ->
        val gameKey = GameData.byNameOrNull(b.game)?.key ?: b.game
        val rate = GachaRateData.byKey(gameKey)?.banner(b.type) ?: return@mapNotNull null

        val p = pity[gameKey] ?: PityState()
        val neededPulls = GachaRateData.maxPullsToSecure(p.count, p.guaranteed, rate)
        val perPull = rate.perPull.coerceAtLeast(1)
        val wonPerPull = rate.wonPerPull

        val neededCurrencyTotal = neededPulls * perPull
        val heldClamped = held[gameKey]?.coerceAtLeast(0) ?: 0
        val heldUsed = heldClamped.coerceAtMost(neededCurrencyTotal)
        val remainingCurrency = (neededCurrencyTotal - heldClamped).coerceAtLeast(0)
        val remainingPulls = ceil(remainingCurrency.toDouble() / perPull).toInt()

        val neededWonTotal = neededPulls.toLong() * wonPerPull
        val remainingWon = remainingPulls.toLong() * wonPerPull
        val savedWon = (neededWonTotal - remainingWon).coerceAtLeast(0)
        val progress = if (neededWonTotal > 0) ((savedWon * 100) / neededWonTotal).toInt().coerceIn(0, 100) else 100

        val dDay = b.dDay(nowMillis)
        val secured = remainingCurrency <= 0
        val daysLeft = dDay.coerceAtLeast(1)
        val dailyGoal = if (secured) 0L else ceil(remainingWon.toDouble() / daysLeft).toLong()

        SavingsPlan(
            game = b.game,
            gameKey = gameKey,
            gameColor = b.gameColor,
            pickupName = b.name,
            type = b.type,
            endMillis = b.endMillis,
            dDay = dDay,
            currency = rate.currency,
            perPull = perPull,
            currentPity = p.count,
            guaranteed = p.guaranteed,
            neededPulls = neededPulls,
            heldCurrency = heldUsed,
            neededCurrencyTotal = neededCurrencyTotal,
            remainingCurrency = remainingCurrency,
            remainingPulls = remainingPulls,
            neededWonTotal = neededWonTotal,
            remainingWon = remainingWon,
            savedWon = savedWon,
            progressPercent = progress,
            dailyGoal = dailyGoal,
            secured = secured,
        )
    }.sortedBy { it.dDay }
}
