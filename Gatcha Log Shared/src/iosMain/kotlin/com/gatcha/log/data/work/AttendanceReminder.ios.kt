package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.util.currentTimeMillis
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS 출석 리마인더 — 예약형(UNCalendarNotificationTrigger).
 *
 * BGTaskScheduler 는 OS 재량이라 "매일 저녁 정시"를 보장하지 못한다.
 * 따라서 출석 리마인더는 OS 가 정시 발송을 보장하는 캘린더 트리거로 예약하고,
 * BGTask 점검([NotificationChecker])과의 중복은 dedup 키("attend")로 막는다.
 *
 * 동작: 다음 베이징 18:00(로컬 시각 환산) 1회 예약(repeats=false).
 *  - 오늘 아직 미출석 & 베이징 18시 전 → 오늘 18:00
 *  - 이미 전부 출석했거나 18시 지남 → 내일 18:00
 *  예약한 날짜의 "attend" dedup 키를 미리 찍어 BGTask 즉시 알림과 중복되지 않게 한다.
 *  재예약은 [reschedule] 호출 시점(앱 시작·토글 변경·BGTask 실행)마다 갱신된다.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)
object AttendanceReminder {
    private const val ID = "gatcha_attend_reminder"
    private val beijingTz = TimeZone.of("UTC+8")

    suspend fun reschedule(settings: AppSettings, repo: GatchaRepository, cfg: HoyolabConfig) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID))
        if (!settings.notifyAttendance || !cfg.isLinked) return

        val nowInstant = Instant.fromEpochMilliseconds(currentTimeMillis())
        val nowBeijing = nowInstant.toLocalDateTime(beijingTz)
        val today = "${nowBeijing.year}-${pad2(nowBeijing.month.number)}-${pad2(nowBeijing.day)}"
        val done = repo.loadAttendance()[today] ?: emptySet()
        val allDoneToday = GameData.attendanceGames.all { it.key in done }

        // 오늘 미완료 & 18시 전이면 오늘, 아니면 내일.
        val dayOffset = if (!allDoneToday && nowBeijing.hour < 18) 0 else 1
        val targetBeijingDate = nowBeijing.date.plus(dayOffset, DateTimeUnit.DAY)
        val targetKey = "${targetBeijingDate.year}-${pad2(targetBeijingDate.month.number)}-${pad2(targetBeijingDate.day)}"

        // 베이징 18:00 → 로컬 시각으로 환산(디바이스 타임존 기준 정확한 발송).
        val targetInstant = LocalDateTime(
            targetBeijingDate.year, targetBeijingDate.month, targetBeijingDate.day, 18, 0,
        ).toInstant(beijingTz)
        val localDt = targetInstant.toLocalDateTime(DateUtil.timeZone)   // 캐시된 타임존

        val comps = NSDateComponents().apply {
            year = localDt.year.toLong()
            month = localDt.month.number.toLong()
            day = localDt.day.toLong()
            hour = localDt.hour.toLong()
            minute = localDt.minute.toLong()
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(comps, repeats = false)

        val content = UNMutableNotificationContent().apply {
            setTitle("출석 체크 알림")
            setBody("오늘 출석 체크 잊지 마세요 — 아직 안 한 게임이 있으면 출석하세요.")
        }
        val request = UNNotificationRequest.requestWithIdentifier(ID, content, trigger)
        // 등록이 끝날 때까지 기다린다 — 예전엔 null 핸들러로 걸어두고 즉시 반환했다.
        // BGTask 는 완료 보고 직후 앱을 서스펜드하므로, 기다리지 않으면 등록 전에 프로세스가 멈춰
        // 리마인더가 통째로 유실될 수 있다([AlertScheduler.replaceAll] 과 같은 이유).
        suspendCancellableCoroutine { cont ->
            center.addNotificationRequest(request) { error ->
                if (error != null) println("[GLG][sched] attend add failed: ${error.localizedDescription}")
                cont.resume(Unit)
            }
        }

        // 예약한 날짜는 BGTask 즉시 알림과 겹치지 않도록 dedup 키를 선점.
        settings.setLastNotified("attend", targetKey)
    }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"
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
        runCatching { AttendanceReminder.reschedule(settings, repo, repo.loadHoyolab()) }
        // 앱 시작 시점에 확정 시각 알림도 함께 갱신 — 예약이 비어 있거나 낡았을 수 있다.
        runCatching { ScheduledAlerts.reschedule(settings, repo) }
    }
}
