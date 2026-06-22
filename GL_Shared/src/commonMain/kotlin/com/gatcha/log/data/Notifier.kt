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

    fun notify(id: Int, title: String, text: String)
}
