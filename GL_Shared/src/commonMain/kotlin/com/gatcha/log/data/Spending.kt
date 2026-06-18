package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import com.gatcha.log.util.randomUuid

data class Spending(
    val id: String = randomUuid(),
    val gameName: String,
    val amount: Long,
    /** 결제 시각(epoch millis). 월/연 필터·날짜 그룹핑의 기준. */
    val dateMillis: Long = currentTimeMillis(),
    val paymentMethod: String = "카드",
    /** 충전 플랫폼 — 인게임 재화를 구입한 경로(스토어/충전소). **선택 항목**(빈 문자열 = 미선택). 결제수단과 분리. */
    val chargePlatform: String = "",
    val itemName: String = "",
    val memo: String = "",
    val tags: List<String> = emptyList(),
    val isSubscription: Boolean = false,
    val gameColor: Long = GameData.colorFor(gameName),
) {
    /** "2026년 5월 20일" */
    val dateLabel: String get() = DateUtil.label(dateMillis)

    /** 날짜 그룹핑 키 */
    val dayKey: String get() = DateUtil.dayKey(dateMillis)
}