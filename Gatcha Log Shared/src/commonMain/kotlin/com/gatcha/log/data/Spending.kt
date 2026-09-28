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
    val gameColor: Long = GameData.colorFor(gameName),
    /**
     * 마지막으로 추가 · 수정한 시각(epoch millis). 0 = 모름(이 칸이 생기기 전 기록).
     * 클라우드 병합에서 같은 id 가 양쪽에 있으면 **더 최신인 쪽**을 남기는 데 쓴다 — 예전엔 원격이
     * 무조건 이겨, 동기화 직전의 수정이나 오프라인 수정이 옛 클라우드 값에 덮였다(2026-09-28 점검).
     */
    val updatedAt: Long = 0L,
) {
    companion object {
        /** 한 건 금액 상한 — 100억 원. 오타(0 몇 개 더)와 합계 오버플로를 막는 선. */
        const val MAX_AMOUNT: Long = 10_000_000_000L
    }
    /** "2026년 5월 20일" */
    val dateLabel: String get() = DateUtil.label(dateMillis)

    /** 날짜 그룹핑 키 */
    val dayKey: String get() = DateUtil.dayKey(dateMillis)
}