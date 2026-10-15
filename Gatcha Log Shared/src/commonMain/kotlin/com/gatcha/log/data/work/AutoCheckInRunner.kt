package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.AttendanceBus
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.Notifier
import com.gatcha.log.data.api.CheckInResult
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.util.currentTimeMillis

/**
 * 자동 출석 시도 1회 + 결과 집계. 백그라운드 작업과 UI(토글 ON 즉시)에서 모두 호출한다.
 * :app 의 AutoCheckInRunner 와 동일 — Context 파라미터만 제거됨 (Notifier 가 expect/actual 로 처리).
 */
object AutoCheckInRunner {

    /** 출석 시도 결과(게임별 분류). null 반환 = HoYoLAB 미연동 · 토큰 비어있음 · 게임 UID 없음. */
    data class Outcome(
        val newSuccess: List<String>,         // 이번 시도에 새로 출석 성공한 게임 shortName
        val alreadyDone: List<String>,        // 시도 전부터 오늘 이미 출석되어 있던 게임
        val authFails: List<String>,          // 쿠키 만료 등 인증 실패 게임
        val netFails: List<String>,           // 네트워크 오류 게임
        val otherFails: List<Pair<String, String>>, // (게임명, 메시지) 기타 실패
    ) {
        val hasAnyFail: Boolean get() = authFails.isNotEmpty() || netFails.isNotEmpty() || otherFails.isNotEmpty()

        /** 토글 ON 직후 사용자에게 보여줄 짧은 토스트 메시지. */
        fun toToastMessage(): String {
            if (authFails.isNotEmpty()) return "쿠키가 만료된 것 같아요 — HoYoLAB 재연동이 필요해요"
            if (netFails.isNotEmpty() && newSuccess.isEmpty()) return "네트워크 오류 — 잠시 후 자동 재시도할게요"
            if (otherFails.isNotEmpty() && newSuccess.isEmpty()) {
                val (name, msg) = otherFails.first()
                return "$name 출석 실패 — $msg"
            }
            if (newSuccess.isNotEmpty()) {
                val ok = newSuccess.joinToString("·")
                return if (hasAnyFail) "출석 완료: $ok (일부는 자동 재시도)" else "출석 완료 — $ok"
            }
            if (alreadyDone.isNotEmpty()) return "이미 오늘 출석을 완료했어요"
            return "출석할 게임이 없어요"
        }
    }

    suspend fun run(
        settings: AppSettings,
        repo: GatchaRepository,
        cfg: HoyolabConfig,
        postFailureNotification: Boolean,
    ): Outcome? {
        if (!cfg.isLinked || cfg.ltuid.isBlank() || cfg.ltoken.isBlank()) return null
        // 게임 UID 가 하나도 없으면 돌지 않는다(27.52.0) — 예전엔 「UID 를 모르면 전부」 규칙으로 세 게임에 체크인을 쏴,
        // 게임이 없는 계정에도 출석이 기록되고 「출석 대신 해 뒀어요」 알림까지 나갔다.
        if (!cfg.canCheckIn) return null

        val today = DateUtil.hoyoDayKey()
        var attendance = repo.loadAttendance()
        var changed = false
        val newSuccess = mutableListOf<String>()
        val alreadyDone = mutableListOf<String>()
        val authFails = mutableListOf<String>()
        val netFails = mutableListOf<String>()
        val otherFails = mutableListOf<Pair<String, String>>()

        // 「내 게임」에서 뺀 게임은 대신 출석하지 않는다(27.52.0) — 화면이 세는 게임과 같은 목록이다.
        for (game in GameData.trackedAttendanceGames(cfg, repo.loadMyGames())) {
            if (game.key in (attendance[today] ?: emptySet())) {
                alreadyDone += game.shortName
                continue
            }
            val r = HoyolabApi.checkIn(cfg.ltuid, cfg.ltoken, game.key)
            if (r.success) {
                val set = (attendance[today] ?: emptySet()) + game.key
                attendance = attendance.toMutableMap().apply { put(today, set) }
                changed = true
                if (r.already) alreadyDone += game.shortName else newSuccess += game.shortName
            } else when (r.reason) {
                CheckInResult.Reason.AUTH -> authFails += game.shortName
                CheckInResult.Reason.NETWORK -> netFails += game.shortName
                else -> otherFails += game.shortName to r.message
            }
        }
        if (changed) {
            // 저장 직전에 다시 읽어 오늘 몫만 얹는다. 체크인 왕복이 수 초라 그 사이 화면에서 한 출석을
            // 처음 읽은 [attendance] 로 통째로 덮으면 지워진다.
            val latest = repo.loadAttendance()
            val mine = attendance[today].orEmpty()
            repo.saveAttendance(latest + (today to latest[today].orEmpty() + mine))
            // 화면은 자기 메모리로 출석을 들고 있다. 여기서 알리지 않으면 출석은 끝났는데
            // 출석 체크 페이지가 계속 "0/3" 으로 남는다([AttendanceBus] 주석 참고).
            AttendanceBus.notifyChanged()
        }

        val outcome = Outcome(newSuccess, alreadyDone, authFails, netFails, otherFails)
        // 토큰 만료 플래그 동기화 — 홈 상단 배너 표시에 사용.
        // AUTH 실패가 있으면 set, AUTH 없고 새로 성공한 게 있으면 clear(재연동 후 회복 케이스).
        when {
            authFails.isNotEmpty() -> settings.hoyoTokenExpired = true
            newSuccess.isNotEmpty() -> settings.hoyoTokenExpired = false
        }
        if (postFailureNotification) notifyResult(settings, repo, outcome, today)
        return outcome
    }

    /** 성공 알림 · 실패 알림의 하루 1회 표시. 값은 호요 날짜 — 실패가 풀리면 [RESOLVED] 를 붙여 둔다. */
    private const val OK_TAG = "auto_checkin_ok"
    private const val FAIL_TAG = "auto_checkin_fail"

    /**
     * 출석 알림의 「오늘 보냄」 표시를 지운다 — HoYoLAB 계정이 바뀌었을 때(27.52.0).
     *
     * 표시는 구글 계정마다 따로지만 HoYoLAB 계정은 가리지 않는다. 앞 계정 몫으로 오늘 리마인더 · 자동 출석 알림이 나갔으면
     * 새 계정은 그날 같은 알림을 받지 못했다. 방해금지로 미뤄 둔 앞 계정의 성공 알림도 함께 버린다.
     */
    fun forgetTodayNotices(settings: AppSettings) {
        listOf(
            OK_TAG, FAIL_TAG, "attend", NotificationChecker.ATTEND_SCHEDULED_TAG, ScheduledAlerts.CHECKIN_DEFERRED_TAG,
        ).forEach { settings.setLastNotified(it, "") }
    }
    private const val RESOLVED = ":resolved"

    /**
     * 출석 결과를 **한 건**으로 알린다.
     *
     * 예전엔 성공 알림과 실패 알림을 따로 냈는데 번호가 같아, 일부만 성공한 날은 성공 알림이 뜨자마자 실패 알림으로
     * 덮였고 재시도로 전부 끝나도 실패 알림만 남았다. 지금은 이렇게 한다(27.52.0).
     * - 실패가 섞였으면 성공한 게임까지 한 건에 적는다(하루 1회).
     * - 새로 성공했고 실패가 없으면 성공 알림 — 그날 실패 알림이 나갔다면 그 자리를 이 알림이 바꾼다.
     * - 새로 한 것도 실패도 없는데(전부 끝남) 그날 실패 알림이 남아 있으면 걷는다.
     *
     * **방해금지 시간에는 울리지 않는다.** 실패는 표시를 남기지 않아 다음 시도가 다시 판정하고, 성공은 해제 시각에
     * 오도록 예약으로 넘긴다([ScheduledAlerts.deferCheckInSuccess]) — 새벽에 도는 Android 자동 출석이 매일 울렸다.
     *
     * 성공 알림은 출석 알림 토글([AppSettings.notifyAttendance])을 끈 사람에게 보내지 않는다 — 그건 "출석 관련 알림을
     * 원하지 않는다"는 뜻이다. 자동 출석 자체는 계속 돈다. 실패 알림은 토글과 무관하게 보낸다(멈춘 줄 모르면 안 된다).
     */
    private suspend fun notifyResult(settings: AppSettings, repo: GatchaRepository, o: Outcome, today: String) {
        val failedToday = settings.lastNotified(FAIL_TAG) == today
        val now = currentTimeMillis()
        val quiet = NotificationChecker.isQuietNow(settings, now)
        when {
            o.hasAnyFail -> {
                if (failedToday || quiet) return
                val (title, body) = failureMessage(o)
                if (Notifier.notify(Notifier.ID_AUTO_CHECKIN, title, body)) {
                    settings.setLastNotified(FAIL_TAG, today)
                    // 성공한 게임도 이 한 건에 적었다 — 같은 날 성공 알림을 따로 또 내지 않는다.
                    if (o.newSuccess.isNotEmpty()) settings.setLastNotified(OK_TAG, today)
                }
            }
            o.newSuccess.isNotEmpty() -> {
                // 그날 실패 알림이 나갔다면 이번 성공이 그 뒷이야기다 — 하루 1회 제한을 건너뛰고 그 자리를 바꾼다.
                if (!failedToday && settings.lastNotified(OK_TAG) == today) return
                if (!settings.notifyAttendance) {
                    if (failedToday) resolveFailure(settings, today)
                    return
                }
                val body = "${o.newSuccess.joinToString("·")} 완료. 오늘 보상 챙기세요."
                if (quiet) {
                    if (failedToday) resolveFailure(settings, today)
                    ScheduledAlerts.deferCheckInSuccess(settings, repo, SUCCESS_TITLE, body, now)
                    settings.setLastNotified(OK_TAG, today)
                    return
                }
                if (Notifier.notify(Notifier.ID_AUTO_CHECKIN, SUCCESS_TITLE, body)) {
                    settings.setLastNotified(OK_TAG, today)
                    if (failedToday) settings.setLastNotified(FAIL_TAG, today + RESOLVED)
                }
            }
            // 새로 한 것도 실패도 없다 = 셀 게임이 전부 끝났다. 남아 있는 실패 알림은 이제 틀린 말이다.
            failedToday -> resolveFailure(settings, today)
        }
    }

    private const val SUCCESS_TITLE = "출석 대신 해 뒀어요"

    private fun resolveFailure(settings: AppSettings, today: String) {
        Notifier.cancel(Notifier.ID_AUTO_CHECKIN)
        settings.setLastNotified(FAIL_TAG, today + RESOLVED)
    }

    /** 실패 알림의 (제목, 본문). AUTH 가 있으면 재연동 안내, 그 외엔 자동 재시도 안내. 성공한 게임이 있으면 첫 줄에 적는다. */
    internal fun failureMessage(o: Outcome): Pair<String, String> {
        val done = if (o.newSuccess.isEmpty()) "" else "${o.newSuccess.joinToString("·")} 완료.\n"
        if (o.authFails.isNotEmpty()) {
            val games = (o.authFails + o.netFails + o.otherFails.map { it.first }).joinToString("·")
            return "자동 출석이 멈췄어요" to
                "$done$games 로그인이 풀렸어요.\n설정 ▸ HoYoLAB 연동에서 다시 연결하면 내일부터 이어서 해 드릴게요."
        }
        val lines = buildList {
            if (o.netFails.isNotEmpty()) add("${o.netFails.joinToString("·")} — 서버에 닿지 못했어요")
            o.otherFails.forEach { (name, msg) -> add("$name: $msg") }
        }
        return "출석 일부를 못 했어요" to
            done + lines.joinToString("\n") + "\n\n잠시 후 다시 해 볼게요. 급하면 앱에서 직접 출석해도 돼요."
    }
}
