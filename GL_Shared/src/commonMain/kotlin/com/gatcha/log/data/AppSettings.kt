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

    /** 새 게임 공지 알림. 기본 OFF — 공지는 잦을 수 있어 원하는 사람만 켠다. */
    var notifyNews: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_NEWS, false)
        set(v) { prefs.putBoolean(KEY_NOTIFY_NEWS, v) }

    /** 정기결제 갱신일 알림(결제 하루 전). 기본 ON — 새는 고정비 안내. */
    var notifySubscription: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_SUB, true)
        set(v) { prefs.putBoolean(KEY_NOTIFY_SUB, v) }

    /**
     * 전투 콘텐츠 시즌 마감 알림(나선 비경·혼돈의 기억 등, D-3/D-1).
     * 기본 ON — 놓치면 그 시즌 보상은 복구 불가라 사후 만회가 안 된다.
     */
    var notifyCombat: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_COMBAT, true)
        set(v) { prefs.putBoolean(KEY_NOTIFY_COMBAT, v) }

    // ── 방해금지(DnD) — 조용한 시간대엔 알림 보류. 기준 기기 로컬 시각(출석 베이징과 별개). ──
    var notifyDndEnabled: Boolean
        get() = prefs.getBoolean(KEY_DND_ENABLED, false)
        set(v) { prefs.putBoolean(KEY_DND_ENABLED, v) }

    /** 방해금지 시작 시(0~23). 기본 23시. */
    var notifyDndStartHour: Int
        get() = prefs.getInt(KEY_DND_START, 23)
        set(v) { prefs.putInt(KEY_DND_START, v.coerceIn(0, 23)) }

    /** 방해금지 종료 시(0~23). 기본 8시. start>end면 자정 넘김으로 처리. */
    var notifyDndEndHour: Int
        get() = prefs.getInt(KEY_DND_END, 8)
        set(v) { prefs.putInt(KEY_DND_END, v.coerceIn(0, 23)) }

    // ── 데일리 요약 — 흩어진 알림을 정한 시각에 1건으로 묶어 발송(opt-in). ──
    var notifyDailySummary: Boolean
        get() = prefs.getBoolean(KEY_SUMMARY_ENABLED, false)
        set(v) { prefs.putBoolean(KEY_SUMMARY_ENABLED, v) }

    /** 데일리 요약 발송 시각(0~23). 기본 21시. */
    var notifyDailySummaryHour: Int
        get() = prefs.getInt(KEY_SUMMARY_HOUR, 21)
        set(v) { prefs.putInt(KEY_SUMMARY_HOUR, v.coerceIn(0, 23)) }

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

    /**
     * OS 알림 권한 프롬프트를 **실제로 띄운 적** 있는지.
     *
     * Android 의 '영구 거부' 판별에 쓴다: shouldShowRequestPermissionRationale 은 "한 번도 안 물어봄"과
     * "두 번 거부해서 영구 차단됨"을 똑같이 false 로 답한다. 이 플래그로 둘을 가른다 —
     * 물어본 적 있는데 rationale 도 false 면 영구 거부(= 프롬프트가 더는 안 뜨므로 시스템 설정으로만 켤 수 있음).
     *
     * 그러므로 프롬프트를 띄우지 않은 경로(온보딩 "나중에 할게요")에서는 절대 true 로 만들면 안 된다.
     */
    var notifPermAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PERM_ASKED, false)
        set(v) { prefs.putBoolean(KEY_NOTIF_PERM_ASKED, v) }

    /**
     * 첫 실행 온보딩(앱 소개 4페이지)을 마쳤는지. 로그인보다 앞에 오는 기기 단위 게이트.
     *
     * 기본값이 [hasUsedAppBefore] 인 이유: 이 키가 없던 버전에서 업데이트한 기존 유저에게 온보딩이
     * 새삼 뜨면 안 된다. 저장된 키가 없으면 "앱을 써본 적 있는가"로 판정한다.
     */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, hasUsedAppBefore())
        set(v) { prefs.putBoolean(KEY_ONBOARDING_DONE, v) }

    /**
     * 온보딩 키가 생기기(v27.38.0) 전부터 앱을 써온 유저인지 — 온보딩 재노출 방지용 마이그레이션 판정.
     *
     * **로그인 이력**이 1순위 기준이다. 이 앱은 구글 로그인이 필수라 계정이 저장돼 있으면 확실한 기존 유저다.
     * [notifPermAsked] 는 Android 에서만 기록됐고 iOS 는 OS 권한 상태에만 의존했어서, 이것만 보면
     * **기존 iOS 유저 전원이 온보딩을 다시 보게 된다**(전부 false). 보조 기준으로만 쓴다.
     */
    private fun hasUsedAppBefore(): Boolean = currentAccountId() != "guest" || notifPermAsked

    /**
     * 앱 시작 시 1회 호출 — [hasUsedAppBefore] 판정을 파일에 **굳힌다**.
     *
     * 굳히지 않으면 판정이 매번 다시 계산되는데, 그 근거인 계정 id 는 **로그아웃하면 지워진다**
     * (AuthManager.signOut). 그러면 기존 유저가 로그아웃하는 순간 "앱을 써본 적 없는 사람"이 되어
     * 온보딩이 다시 뜬다. iOS 는 notifPermAsked 를 기록한 적이 없어(전부 false) 곧바로 이 함정에 빠진다.
     *
     * 온보딩을 마친 유저는 [onboardingDone] 에 true 가 명시 저장되므로 영향받지 않는다.
     * 진짜 신규 설치는 hasUsedAppBefore()=false 라 아무것도 쓰지 않고 정상적으로 온보딩을 탄다.
     */
    fun freezeOnboardingVerdict() {
        if (!prefs.getBoolean(KEY_ONBOARDING_DONE, false) && hasUsedAppBefore()) {
            prefs.putBoolean(KEY_ONBOARDING_DONE, true)
        }
    }

    /** 지출 내역 목록을 컴팩트(한 줄)로 표시. 기본 false(기존 — 아이템·결제수단·태그 노출). */
    var spendingCompact: Boolean
        get() = prefs.getBoolean(KEY_SPENDING_COMPACT, false)
        set(v) { prefs.putBoolean(KEY_SPENDING_COMPACT, v) }

    /** 백그라운드 주기 작업이 필요한지(하나라도 켜져 있으면 스케줄 유지). */
    fun needsPeriodicWork(): Boolean =
        autoCheckIn || notifyResin || notifyAttendance || notifyBudget || notifyPickup ||
            notifySubscription || notifyDailySummary

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
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_SPENDING_COMPACT = "spending_compact"
        private const val KEY_NOTIFY_SUB = "notify_subscription"
        private const val KEY_NOTIFY_NEWS = "notify_news"
        private const val KEY_NOTIFY_COMBAT = "notify_combat"
        private const val KEY_DND_ENABLED = "notify_dnd_enabled"
        private const val KEY_DND_START = "notify_dnd_start"
        private const val KEY_DND_END = "notify_dnd_end"
        private const val KEY_SUMMARY_ENABLED = "notify_daily_summary"
        private const val KEY_SUMMARY_HOUR = "notify_daily_summary_hour"

        /** 현재 로그인 계정 id(gatcha_auth). 비로그인=guest. 백그라운드 컴포넌트가 계정별 저장소를 열 때 사용. */
        fun currentAccountId(): String =
            KeyValueStore("gatcha_auth").getString("account_id", null) ?: "guest"
    }
}
