package com.gatcha.log.data

/**
 * 로컬 알림 발송 헬퍼 (expect/actual).
 * - Android: NotificationCompat (단일 채널, 탭하면 앱 실행)
 * - iOS: UNUserNotificationCenter
 *
 * :app 의 Notifier 와 동일한 ID 체계. ctx 파라미터만 제거됨 (플랫폼 actual 이 내부 처리).
 */
expect object Notifier {
    // 알림 ID (종류별 고정 → 같은 종류는 갱신, 누적 안 됨)
    val ID_BUDGET: Int
    val ID_ATTEND: Int
    val ID_AUTO_CHECKIN: Int
    val ID_RESIN_BASE: Int        // + game.ordinal
    val ID_BUDGET_GAME_BASE: Int  // 게임별 예산 초과/임박. + game.ordinal
    val ID_PICKUP_BASE: Int       // 픽업 마감 임박. + game.ordinal
    val ID_SUBSCRIPTION_BASE: Int // 정기결제 갱신 임박. + (구독 인덱스)
    val ID_DAILY_SUMMARY: Int     // 데일리 요약(1건 통합)
    val ID_NEWS_BASE: Int         // 새 게임 공지. + game.ordinal
    val ID_COMBAT_BASE: Int       // 전투 콘텐츠 시즌 마감 임박. + game.ordinal

    /**
     * [link] = 알림 탭 시 이동할 딥링크(`"news:<공지 id>"` 형식). 빈 문자열이면 앱만 연다.
     * 처리는 [SpendingViewModel.handleNotificationLink].
     */
    fun notify(id: Int, title: String, text: String, link: String = "")

    /**
     * 시스템 알림이 실제로 표시 가능한 상태인지(권한 허용 + 앱/채널 알림 켜짐).
     * 설정 화면에서 "토글은 켰는데 권한이 꺼져 알림이 안 옴" 안내에 사용.
     * iOS 는 비동기 조회라 직전 캐시값을 반환(호출 시 백그라운드 갱신).
     */
    fun notificationsEnabled(): Boolean
}
