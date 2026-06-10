package com.gatcha.log.data

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class Spending(
    val id: String = UUID.randomUUID().toString(),
    val gameName: String,
    val amount: Long,
    /** 결제 시각(epoch millis). 월/연 필터·날짜 그룹핑의 기준. */
    val dateMillis: Long = System.currentTimeMillis(),
    val paymentMethod: String = "신용카드",
    val itemName: String = "",
    val memo: String = "",
    val tags: List<String> = emptyList(),
    val isSubscription: Boolean = false,
    val gameColor: Color = GameData.colorFor(gameName),
) {
    /** "2026년 5월 20일" */
    val dateLabel: String get() = DateUtil.label(dateMillis)

    /** 날짜 그룹핑 키 */
    val dayKey: String get() = DateUtil.dayKey(dateMillis)
}