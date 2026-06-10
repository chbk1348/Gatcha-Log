package com.gatcha.log.data

import android.content.Context

/**
 * 기기 단위 네이티브 설정(계정 무관) — 자동 출석체크·로컬 알림 토글.
 * WorkManager 워커·위젯 등 백그라운드 컴포넌트에서도 동일하게 접근한다.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var autoCheckIn: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECKIN, false)
        set(v) { prefs.edit().putBoolean(KEY_AUTO_CHECKIN, v).apply() }

    var notifyResin: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_RESIN, false)
        set(v) { prefs.edit().putBoolean(KEY_NOTIFY_RESIN, v).apply() }

    var notifyAttendance: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_ATTEND, false)
        set(v) { prefs.edit().putBoolean(KEY_NOTIFY_ATTEND, v).apply() }

    var notifyBudget: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_BUDGET, false)
        set(v) { prefs.edit().putBoolean(KEY_NOTIFY_BUDGET, v).apply() }

    var notifyWish: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_WISH, false)
        set(v) { prefs.edit().putBoolean(KEY_NOTIFY_WISH, v).apply() }

    /** 과소비 리플렉션 넛지(지출 추가 시점) — 예산·평소치 초과면 저장 직전 한 번 더 확인. 기본 ON. */
    var nudgeOverspend: Boolean
        get() = prefs.getBoolean(KEY_NUDGE, true)
        set(v) { prefs.edit().putBoolean(KEY_NUDGE, v).apply() }

    /** 넛지 평소치 기준액(원). 단건 지출이 이 금액 이상이면 충동 결제로 보고 확인. 기본 10만원. */
    var nudgeThreshold: Long
        get() = prefs.getLong(KEY_NUDGE_THRESHOLD, 100_000L)
        set(v) { prefs.edit().putLong(KEY_NUDGE_THRESHOLD, v).apply() }

    /**
     * HoYoLAB 토큰 만료 감지 플래그.
     * 자동 출석에서 AUTH 실패(쿠키 만료) 발생 시 [AutoCheckInRunner] 가 true 로 세팅하고,
     * 재연동(토큰 새로 저장)·자동 출석 재성공 시 false 로 클리어. 홈 상단 배너 표시에 사용.
     */
    var hoyoTokenExpired: Boolean
        get() = prefs.getBoolean(KEY_HOYO_EXPIRED, false)
        set(v) { prefs.edit().putBoolean(KEY_HOYO_EXPIRED, v).apply() }

    /** 백그라운드 주기 작업이 필요한지(하나라도 켜져 있으면 스케줄 유지). */
    fun needsPeriodicWork(): Boolean = autoCheckIn || notifyResin || notifyAttendance || notifyBudget || notifyWish

    /** 알림 중복 방지용 마지막 발송 키 저장/조회 (예: "budget:2026-05"). */
    fun lastNotified(tag: String): String = prefs.getString("notif_last_$tag", "") ?: ""
    fun setLastNotified(tag: String, value: String) { prefs.edit().putString("notif_last_$tag", value).apply() }

    companion object {
        private const val PREFS = "gatcha_settings"
        private const val KEY_AUTO_CHECKIN = "auto_checkin"
        private const val KEY_NOTIFY_RESIN = "notify_resin"
        private const val KEY_NOTIFY_ATTEND = "notify_attendance"
        private const val KEY_NOTIFY_BUDGET = "notify_budget"
        private const val KEY_NOTIFY_WISH = "notify_wish_pickup"
        private const val KEY_HOYO_EXPIRED = "hoyo_token_expired"
        private const val KEY_NUDGE = "nudge_overspend"
        private const val KEY_NUDGE_THRESHOLD = "nudge_threshold"

        /** 현재 로그인 계정 id(gatcha_auth). 비로그인=guest. 백그라운드 컴포넌트가 계정별 저장소를 열 때 사용. */
        fun currentAccountId(context: Context): String =
            context.applicationContext.getSharedPreferences("gatcha_auth", Context.MODE_PRIVATE)
                .getString("account_id", null) ?: "guest"
    }
}
