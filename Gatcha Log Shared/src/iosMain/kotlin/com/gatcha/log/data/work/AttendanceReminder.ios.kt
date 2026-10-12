package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.GatchaRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS 출석 리마인더 — **27.52.0 부터 공용 예약 계획의 한 건이다**([ScheduledAlerts.plan] ⑦).
 *
 * 27.51 까지는 여기서 따로 캘린더 트리거를 걸었다(다음 베이징 18:00 한 건). Android 에는 예약형이 없어 두 플랫폼이
 * 다르게 움직였고, 중복 방지 표시를 주기 점검과 같은 키로 써서 번갈아 덮어쓰는 문제도 있었다.
 * 지금 남은 일은 **옛 예약을 걷는 것**뿐이다 — 업데이트 전에 걸린 것이 남아 있으면 새 예약과 두 번 울린다.
 */
@OptIn(ExperimentalForeignApi::class)
object AttendanceReminder {
    private const val LEGACY_ID = "gatcha_attend_reminder"

    fun removeLegacy() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(LEGACY_ID))
    }
}

/**
 * 출석 리마인더 재예약 — Swift 의 didFinishLaunching 등에서 호출(앱 열 때마다 다음 18:00 갱신).
 * `AttendanceReminder_iosKt.rescheduleAttendanceReminder()` 로 노출된다.
 */
fun rescheduleAttendanceReminder() {
    val settings = AppSettings()
    val repo = GatchaRepository(AppSettings.currentAccountId())
    // 두 예약 모두 등록 완료까지 기다리는 suspend 라 코루틴이 필요하다.
    // 여기는 포그라운드(앱 시작)라 앱이 서스펜드되지 않으므로 결과를 기다릴 필요는 없다
    // — 기다려야 하는 쪽은 BGTask(NativeScheduler.ios) 다.
    CoroutineScope(Dispatchers.Default).launch {
        // 확정 시각 알림을 갱신한다 — 예약이 비어 있거나 낡았을 수 있다. 출석 리마인더도 이 안에서 함께 걸린다.
        runCatching { ScheduledAlerts.reschedule(settings, repo) }
    }
}
