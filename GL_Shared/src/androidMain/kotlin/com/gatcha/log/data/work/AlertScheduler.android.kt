package com.gatcha.log.data.work

/**
 * Android 는 사전 예약이 필요 없다.
 *
 * WorkManager 주기 작업(4시간, [AndroidWorkScheduler])이 앱 실행과 무관하게 돌면서
 * [NotificationChecker] 로 그때그때 상태를 보고 알림을 쏜다. iOS 처럼 실행 자체가 막히는
 * 구조가 아니라, 같은 알림을 미리 예약하면 오히려 중복 발송이 된다.
 *
 * (AlarmManager 로 정확 시각을 잡을 수도 있지만, Android 12+ 는 정확 알람이 별도 권한이라
 *  이득 대비 비용이 크다. 주기 작업의 최대 지연은 배터리 최적화 상황에서도 몇 시간 수준.)
 */
actual object AlertScheduler {
    actual val schedulesAhead: Boolean = false
    actual fun replaceAll(alerts: List<ScheduledAlert>) = Unit
}
