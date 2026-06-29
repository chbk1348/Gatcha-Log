package com.gatcha.log.data

import com.gatcha.log.storage.KeyValueStore

/**
 * 기기 단위 네이티브 설정(계정 무관) — 자동 출석체크·로컬 알림 토글.
 * :app 의 AppSettings(SharedPreferences)를 KeyValueStore(expect/actual) 위에 동일 API 로 이식.
 */
class AppSettings {
    private val prefs = KeyValueStore(PREFS)

    var autoCheckIn: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECKIN, false)
        set(v) { prefs.putBoolean(KEY_AUTO_CHECKIN, v) }

    var notifyResin: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_RESIN, false)
        set(v) { prefs.putBoolean(KEY_NOTIFY_RESIN, v) }

    var notifyAttendance: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_ATTEND, false)
        set(v) { prefs.putBoolean(KEY_NOTIFY_ATTEND, v) }

    var notifyBudget: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_BUDGET, false)
        set(v) { prefs.putBoolean(KEY_NOTIFY_BUDGET, v) }

    var notifyPickup: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_PICKUP, false)
        set(v) { prefs.putBoolean(KEY_NOTIFY_PICKUP, v) }

    /** 과소비 리플렉션 넛지(지출 추가 시점) — 예산·평소치 초과면 저장 직전 한 번 더 확인. 기본 ON. */
    var nudgeOverspend: Boolean
        get() = prefs.getBoolean(KEY_NUDGE, true)
        set(v) { prefs.putBoolean(KEY_NUDGE, v) }

    /** 넛지 평소치 기준액(원). 단건 지출이 이 금액 이상이면 충동 결제로 보고 확인. 기본 10만원. */
    var nudgeThreshold: Long
        get() = prefs.getLong(KEY_NUDGE_THRESHOLD, 100_000L)
        set(v) { prefs.putLong(KEY_NUDGE_THRESHOLD, v) }

    /**
     * HoYoLAB 토큰 만료 감지 플래그.
     * 자동 출석에서 AUTH 실패(쿠키 만료) 발생 시 [AutoCheckInRunner] 가 true 로 세팅하고,
     * 재연동(토큰 새로 저장)·자동 출석 재성공 시 false 로 클리어. 홈 상단 배너 표시에 사용.
     */
    var hoyoTokenExpired: Boolean
        get() = prefs.getBoolean(KEY_HOYO_EXPIRED, false)
        set(v) { prefs.putBoolean(KEY_HOYO_EXPIRED, v) }

    /** 앱 첫 실행 시 알림 권한을 1회 자동 요청했는지(중복 프롬프트 방지). */
    var notifPermAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PERM_ASKED, false)
        set(v) { prefs.putBoolean(KEY_NOTIF_PERM_ASKED, v) }

    /** 지출 내역 목록을 컴팩트(한 줄)로 표시. 기본 false(기존 — 아이템·결제수단·태그 노출). */
    var spendingCompact: Boolean
        get() = prefs.getBoolean(KEY_SPENDING_COMPACT, false)
        set(v) { prefs.putBoolean(KEY_SPENDING_COMPACT, v) }

    /** 백그라운드 주기 작업이 필요한지(하나라도 켜져 있으면 스케줄 유지). */
    fun needsPeriodicWork(): Boolean = autoCheckIn || notifyResin || notifyAttendance || notifyBudget || notifyPickup

    /** 알림 중복 방지용 마지막 발송 키 저장/조회 (예: "budget:2026-05"). */
    fun lastNotified(tag: String): String = prefs.getString("notif_last_$tag", "") ?: ""
    fun setLastNotified(tag: String, value: String) { prefs.putString("notif_last_$tag", value) }

    companion object {
        private const val PREFS = "gatcha_settings"
        private const val KEY_AUTO_CHECKIN = "auto_checkin"
        private const val KEY_NOTIFY_RESIN = "notify_resin"
        private const val KEY_NOTIFY_ATTEND = "notify_attendance"
        private const val KEY_NOTIFY_BUDGET = "notify_budget"
        private const val KEY_NOTIFY_PICKUP = "notify_pickup"
        private const val KEY_HOYO_EXPIRED = "hoyo_token_expired"
        private const val KEY_NUDGE = "nudge_overspend"
        private const val KEY_NUDGE_THRESHOLD = "nudge_threshold"
        private const val KEY_NOTIF_PERM_ASKED = "notif_perm_asked"
        private const val KEY_SPENDING_COMPACT = "spending_compact"

        /** 현재 로그인 계정 id(gatcha_auth). 비로그인=guest. 백그라운드 컴포넌트가 계정별 저장소를 열 때 사용. */
        fun currentAccountId(): String =
            KeyValueStore("gatcha_auth").getString("account_id", null) ?: "guest"
    }
}
