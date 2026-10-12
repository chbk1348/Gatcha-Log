package com.gatcha.log.data.vm

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.NotificationCatalog
import com.gatcha.log.data.NotifyKey
import com.gatcha.log.data.work.NativeScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 알림 토글과 방해금지 — 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고).
 *
 * @param rescheduleTimedAlerts 토글이 바뀐 뒤 확정 시각 알림을 다시 건다(뷰모델이 저장소를 읽어 예약한다).
 */
internal class NotifySettingsController(
    private val settings: AppSettings,
    private val rescheduleTimedAlerts: () -> Unit,
) {
    // 로컬 알림 토글 (예산·출석·재화)
    private val _notifyBudget = MutableStateFlow(settings.notifyBudget)
    val notifyBudget: StateFlow<Boolean> = _notifyBudget.asStateFlow()
    private val _notifyAttendance = MutableStateFlow(settings.notifyAttendance)
    val notifyAttendance: StateFlow<Boolean> = _notifyAttendance.asStateFlow()
    private val _notifyResin = MutableStateFlow(settings.notifyResin)
    val notifyResin: StateFlow<Boolean> = _notifyResin.asStateFlow()
    private val _notifyPickup = MutableStateFlow(settings.notifyPickup)
    val notifyPickup: StateFlow<Boolean> = _notifyPickup.asStateFlow()
    private val _notifyNews = MutableStateFlow(settings.notifyNews)
    val notifyNews: StateFlow<Boolean> = _notifyNews.asStateFlow()
    private val _notifyCombat = MutableStateFlow(settings.notifyCombat)
    val notifyCombat: StateFlow<Boolean> = _notifyCombat.asStateFlow()
    private val _notifyHoyoland = MutableStateFlow(settings.notifyHoyoland)
    val notifyHoyoland: StateFlow<Boolean> = _notifyHoyoland.asStateFlow()
    private val _notifyAppUpdate = MutableStateFlow(settings.notifyAppUpdate)
    val notifyAppUpdate: StateFlow<Boolean> = _notifyAppUpdate.asStateFlow()

    // 방해금지(DnD) — 조용한 시간대 알림 보류
    private val _notifyDndEnabled = MutableStateFlow(settings.notifyDndEnabled)
    val notifyDndEnabled: StateFlow<Boolean> = _notifyDndEnabled.asStateFlow()
    private val _notifyDndStartHour = MutableStateFlow(settings.notifyDndStartHour)
    val notifyDndStartHour: StateFlow<Int> = _notifyDndStartHour.asStateFlow()
    private val _notifyDndEndHour = MutableStateFlow(settings.notifyDndEndHour)
    val notifyDndEndHour: StateFlow<Int> = _notifyDndEndHour.asStateFlow()

    fun setNotifyBudget(v: Boolean) { settings.notifyBudget = v; _notifyBudget.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyAttendance(v: Boolean) { settings.notifyAttendance = v; _notifyAttendance.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyResin(v: Boolean) { settings.notifyResin = v; _notifyResin.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyPickup(v: Boolean) { settings.notifyPickup = v; _notifyPickup.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyNews(v: Boolean) { settings.notifyNews = v; _notifyNews.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyCombat(v: Boolean) { settings.notifyCombat = v; _notifyCombat.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyHoyoland(v: Boolean) { settings.notifyHoyoland = v; _notifyHoyoland.value = v; applyNativeAfterNotifyChange(v) }
    fun setNotifyAppUpdate(v: Boolean) { settings.notifyAppUpdate = v; _notifyAppUpdate.value = v; applyNativeAfterNotifyChange(v) }

    // 방해금지를 켜거나 시간을 바꾸면 **이미 걸린 예약도 다시 건다**(27.52.0) — 예약은 만들 때 조용한 시간을 피해 두는
    // 방식이라, 안 그러면 22시에 방해금지를 켜도 03시에 걸려 있던 완충 알림이 그대로 울린다.
    fun setNotifyDndEnabled(v: Boolean) {
        settings.notifyDndEnabled = v; _notifyDndEnabled.value = v; NativeScheduler.apply(); rescheduleTimedAlerts()
    }
    fun setNotifyDndStartHour(v: Int) {
        settings.notifyDndStartHour = v; _notifyDndStartHour.value = settings.notifyDndStartHour; rescheduleTimedAlerts()
    }
    fun setNotifyDndEndHour(v: Int) {
        settings.notifyDndEndHour = v; _notifyDndEndHour.value = settings.notifyDndEndHour; rescheduleTimedAlerts()
    }

    /**
     * OS 알림 권한을 **처음 허용**했을 때 — 항목별 알림([NotificationCatalog.items])을 전부 한꺼번에 켠다.
     *
     * 권한만 받고 토글이 꺼져 있으면 알림이 한 건도 오지 않는다. 예전엔 온보딩에서 세 개만 켰던 탓에
     * "알림 켜고 시작하기"를 누른 사람도 정기결제·전투 시즌·공지는 조용했다.
     *
     * **데일리 요약·방해금지는 건드리지 않는다.** 둘은 개별 알림이 아니라 *발송 방식*을 바꾸는 설정이라
     * (요약은 하루 1건으로 묶고, 방해금지는 시간대별로 막는다) 임의로 켜면 오히려 알림이 줄거나 늦는다.
     * 그래서 카탈로그에도 들어 있지 않다.
     *
     * 세터를 일곱 번 부르지 않는다 — 그러면 [applyNativeAfterNotifyChange] 가 일곱 번 돌아
     * 워커 재등록·즉시 실행이 그만큼 중복된다. 값만 쓰고 반영은 **마지막에 한 번**.
     *
     * `when` 은 [NotifyKey] 에 대해 exhaustive 라, 항목이 늘면 여기서 컴파일 에러로 잡힌다(누락 방지).
     */
    fun enableAllNotifyItems() {
        NotifyKey.entries.forEach { key ->
            when (key) {
                NotifyKey.BUDGET -> { settings.notifyBudget = true; _notifyBudget.value = true }
                NotifyKey.RESIN -> { settings.notifyResin = true; _notifyResin.value = true }
                NotifyKey.ATTENDANCE -> { settings.notifyAttendance = true; _notifyAttendance.value = true }
                NotifyKey.PICKUP -> { settings.notifyPickup = true; _notifyPickup.value = true }
                NotifyKey.COMBAT -> { settings.notifyCombat = true; _notifyCombat.value = true }
                NotifyKey.NEWS -> { settings.notifyNews = true; _notifyNews.value = true }
                // 행사가 끝나 목록에서 빠진 토글은 건드리지 않는다 — 켜 두면 끌 자리가 없는 값이 된다.
                NotifyKey.HOYOLAND -> if (NotificationCatalog.hoyolandAlertsActive) {
                    settings.notifyHoyoland = true; _notifyHoyoland.value = true
                }
                // iOS 에는 없는 항목이다 — 같은 이유로 목록에 있을 때만 켠다.
                NotifyKey.APP_UPDATE -> if (NotificationCatalog.appUpdateAlertsActive) {
                    settings.notifyAppUpdate = true; _notifyAppUpdate.value = true
                }
            }
        }
        applyNativeAfterNotifyChange(true)
    }

    private fun applyNativeAfterNotifyChange(enabled: Boolean) {
        NativeScheduler.apply()
        rescheduleTimedAlerts()
        if (enabled) NativeScheduler.runNow()
    }

    /** 온보딩에서 「알림 켜고 시작하기」를 골랐을 때 — 네 항목을 스위치 값 그대로 쓰고 방해 금지를 켠다. */
    fun applyOnboarding(attendance: Boolean, resin: Boolean, pickup: Boolean, budgetAlert: Boolean) {
        // 세터를 네 번 부르면 워커 재등록이 네 번 돈다 — 값만 쓰고 반영은 한 번([enableAllNotifyItems] 와 같은 이유).
        settings.notifyAttendance = attendance; _notifyAttendance.value = attendance
        settings.notifyResin = resin; _notifyResin.value = resin
        settings.notifyPickup = pickup; _notifyPickup.value = pickup
        settings.notifyBudget = budgetAlert; _notifyBudget.value = budgetAlert
        settings.notifyDndEnabled = true; _notifyDndEnabled.value = true
        applyNativeAfterNotifyChange(attendance || resin || pickup || budgetAlert)
    }
}
