package com.gatcha.log.data.work

import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Notifier
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS 예약형 알림 — **앱이 떠 있지 않아도 OS 가 정시에 발송한다.**
 *
 * BGAppRefreshTask 는 실행 시점이 OS 재량이고 앱이 강제 종료돼 있으면 아예 돌지 않아,
 * 오래 앱을 안 열면 알림이 통째로 밀렸다. 시각을 미리 알 수 있는 알림은 여기서 캘린더 트리거로
 * 등록해 그 의존을 끊는다(출석 리마인더가 쓰던 방식과 동일).
 *
 * 식별자에 [PREFIX] 를 붙여, 재예약 때 우리 예약만 골라 지운다(즉시 알림·출석 리마인더는 보존).
 *
 * **전 구간이 suspend 다.** UNUserNotificationCenter 는 전부 콜백형이라, 예전엔 조회·등록을
 * 걸어두고 즉시 반환했다 → BGTask 가 `setTaskCompletedWithSuccess` 로 앱을 서스펜드시키면
 * 콜백이 돌기 전에 프로세스가 멈춰 **예약이 하나도 안 걸린 채 백그라운드 실행이 끝났다.**
 * 이제 마지막 등록의 완료 핸들러까지 기다린 뒤에 반환한다.
 */
@OptIn(ExperimentalForeignApi::class)
actual object AlertScheduler {

    private const val PREFIX = "gatcha_sched_"

    actual val schedulesAhead: Boolean = true

    actual suspend fun replaceAll(alerts: List<ScheduledAlert>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        // 이전 예약 정리 — 데이터가 바뀌면(픽업 종료·구독 삭제) 옛 예약이 남아 헛알림이 된다.
        val stale = ourPendingRequests(center).map { it.identifier }
        if (stale.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(stale)
        alerts.forEach { schedule(center, it) }
        logPending(center)
    }

    /** 우리가 건 대기 예약만 — 조회는 콜백형이라 결과를 받을 때까지 중단한다. */
    private suspend fun ourPendingRequests(center: UNUserNotificationCenter): List<UNNotificationRequest> =
        suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                cont.resume(
                    requests.orEmpty()
                        .mapNotNull { it as? UNNotificationRequest }
                        .filter { it.identifier.startsWith(PREFIX) },
                )
            }
        }

    /**
     * 진단 로그 — **등록을 요청한 것**이 아니라 **OS 가 실제로 들고 있는 것**을 찍는다.
     *
     * "재화 완충 알림이 정시에 온 적이 없다"를 조사할 때, 예약이 애초에 안 걸리는 것인지
     * 걸렸는데 발송이 안 되는 것인지 구분할 방법이 없었다. 기기 콘솔에서 이 줄로 확인한다.
     * 이 조회까지 기다려야 백그라운드 실행에서도 로그가 남는다(서스펜드 전에 찍힌다).
     */
    private suspend fun logPending(center: UNUserNotificationCenter) {
        val ours = ourPendingRequests(center)
        val detail = ours.joinToString { r ->
            val next = (r.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate()
            "${r.identifier.removePrefix(PREFIX)}->$next"
        }
        println("[GLG][sched] pending=${ours.size} $detail")
    }

    private suspend fun schedule(center: UNUserNotificationCenter, a: ScheduledAlert) {
        val content = UNMutableNotificationContent().apply {
            setTitle(a.title)
            setBody(a.text)
            if (a.link.isNotBlank()) setUserInfo(mapOf(Notifier.KEY_LINK to a.link))
        }
        val comps = NSDateComponents().apply {
            hour = DateUtil.hourOf(a.whenMillis).toLong()
            minute = DateUtil.minuteOf(a.whenMillis).toLong()
            // 매일 반복이면 시:분만 지정한다(연·월·일을 넣으면 그 날 1회로 굳는다).
            if (!a.repeatsDaily) {
                year = DateUtil.year(a.whenMillis).toLong()
                month = DateUtil.month(a.whenMillis).toLong()
                day = DateUtil.dayOfMonth(a.whenMillis).toLong()
            }
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(comps, a.repeatsDaily)
        val request = UNNotificationRequest.requestWithIdentifier(PREFIX + a.key, content, trigger)
        // 실패를 삼키지 않는다 — 예전엔 null 핸들러라 OS 가 요청을 거부해도 아무 흔적이 없었다.
        // 등록이 끝나야 다음으로 넘어간다(중간에 앱이 서스펜드되면 남은 예약이 유실된다).
        suspendCancellableCoroutine { cont ->
            center.addNotificationRequest(request) { error ->
                if (error != null) println("[GLG][sched] add failed ${a.key}: ${error.localizedDescription}")
                cont.resume(Unit)
            }
        }
    }
}
