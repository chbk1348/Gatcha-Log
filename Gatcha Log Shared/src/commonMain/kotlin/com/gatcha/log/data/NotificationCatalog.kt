package com.gatcha.log.data

/**
 * 항목별 알림 목록 — **Android·iOS 가 이 한 소스를 공유한다.**
 *
 * 예전에는 제목·설명이 Kotlin(`SettingsScreen.kt`)과 Swift(`NotificationSettingsView.swift`)에
 * 문자열로 두 벌 있었고, 이미 어긋나 있었다("정기결제 갱신 알림" vs "정기결제 갱신").
 * 항목을 하나 늘릴 때 한쪽만 고치면 조용히 갈라지는 자리다.
 *
 * 아이콘만 플랫폼이 정한다(SF Symbols ↔ Material Icons 는 이름 체계가 달라 공유할 수 없다).
 */

/** 알림 항목 식별자 — 화면이 토글 상태·설정 함수를 이 키로 연결한다. */
enum class NotifyKey { BUDGET, SUBSCRIPTION, RESIN, ATTENDANCE, PICKUP, COMBAT, NEWS, HOYOLAND }

/**
 * 알림 묶음. 일곱 개를 한 덩어리로 늘어놓으면 훑을 수가 없어서 성격으로 나눴다 —
 * **돈이 나가는 것 / 놓치면 손해인 것 / 그냥 소식**은 켜고 끄는 판단 기준이 서로 다르다.
 */
enum class NotifyGroup(val title: String, val caption: String) {
    MONEY("돈", "결제·예산"),
    PLAY("플레이", "놓치면 손해인 것"),
    NEWS("소식", "알아두면 좋은 것"),
}

/**
 * 항목 하나. [desc] 는 **한 줄에 들어가는 길이로 짧게** 쓴다 —
 * 예전 설명("나선 비경·혼돈의 기억 등을 못 깬 채 시즌이 끝나기 전에 알려줘요")은 두 줄로 넘어가
 * 행 높이가 제각각이 됐고, 그게 목록을 훑기 어렵게 만든 주된 이유였다.
 */
data class NotifyItem(
    val key: NotifyKey,
    val group: NotifyGroup,
    val title: String,
    val desc: String,
)

object NotificationCatalog {

    /** 전체 항목 — 묶음 순서대로. */
    val items: List<NotifyItem> = listOf(
        NotifyItem(NotifyKey.BUDGET, NotifyGroup.MONEY, "예산", "예산 90% · 초과 시"),
        NotifyItem(NotifyKey.SUBSCRIPTION, NotifyGroup.MONEY, "정기결제 갱신", "결제 하루 전(D-1)"),

        NotifyItem(NotifyKey.RESIN, NotifyGroup.PLAY, "행동력 가득참", "레진·개척력·배터리가 가득 차면"),
        NotifyItem(NotifyKey.ATTENDANCE, NotifyGroup.PLAY, "출석 리마인더", "저녁까지 미출석이면"),
        NotifyItem(NotifyKey.PICKUP, NotifyGroup.PLAY, "픽업 마감", "진행 중인 픽업이 끝나기 전"),
        NotifyItem(NotifyKey.COMBAT, NotifyGroup.PLAY, "전투 시즌 마감", "못 깬 콘텐츠가 남은 채 시즌이 끝나기 전"),

        NotifyItem(NotifyKey.NEWS, NotifyGroup.NEWS, "새 공지", "게임에 새 공지가 올라오면"),
        NotifyItem(NotifyKey.HOYOLAND, NotifyGroup.NEWS, "호요랜드", "예매 오픈 · 개막 전에"),
    )

    /**
     * 항목이 있는 묶음만, 표시 순서대로. 화면은 이 순서로 카드를 쌓는다.
     *
     * `List<Pair<...>>` 로 묶어 내보내지 않는다 — Swift 에서 `KotlinPair` 를 벗겨 써야 해서
     * 호출부가 지저분해진다. 묶음 목록과 [itemsIn] 두 개로 나눠 양쪽이 같은 모양으로 쓰게 한다.
     */
    val groups: List<NotifyGroup> = NotifyGroup.entries.filter { g -> items.any { it.group == g } }

    /** 그 묶음의 항목들. */
    fun itemsIn(group: NotifyGroup): List<NotifyItem> = items.filter { it.group == group }

    /** 켜진 개수 요약("7개 중 4개 켜짐") — 헤더에서 지금 상태를 한눈에 보여주려고 쓴다. */
    fun enabledLabel(onCount: Int): String = "${items.size}개 중 ${onCount}개 켜짐"
}
