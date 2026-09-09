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

/**
 * 자동 출석 시도 1회 + 결과 집계. 백그라운드 작업과 UI(토글 ON 즉시)에서 모두 호출한다.
 * :app 의 AutoCheckInRunner 와 동일 — Context 파라미터만 제거됨 (Notifier 가 expect/actual 로 처리).
 */
object AutoCheckInRunner {

    /** 출석 시도 결과(게임별 분류). null 반환 = HoYoLAB 미연동 또는 토큰 비어있음. */
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

        val today = DateUtil.hoyoDayKey()
        var attendance = repo.loadAttendance()
        var changed = false
        val newSuccess = mutableListOf<String>()
        val alreadyDone = mutableListOf<String>()
        val authFails = mutableListOf<String>()
        val netFails = mutableListOf<String>()
        val otherFails = mutableListOf<Pair<String, String>>()

        for (game in GameData.attendanceGames) {
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
            repo.saveAttendance(attendance)
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
        if (postFailureNotification) {
            maybeNotifySuccess(settings, outcome, today)
            maybeNotifyFailure(settings, outcome, today)
        }
        return outcome
    }

    /**
     * 새로 출석한 게임이 있으면 하루 1회 알림.
     *
     * 예전엔 **실패했을 때만** 알렸다. 성공하면 아무 흔적이 없으니 "자동 출석이 도는 게 맞나"를
     * 확인할 방법이 앱을 열어 보는 것뿐이었다(2026-09-08 제보). 매일 챙겨주겠다고 한 기능은
     * 챙겼다는 사실도 같이 말해야 한다.
     *
     * 출석 알림 토글([AppSettings.notifyAttendance])을 끈 사람에게는 보내지 않는다 —
     * 그건 "출석 관련 알림을 원하지 않는다"는 뜻이다. 자동 출석 자체는 계속 돈다.
     */
    private suspend fun maybeNotifySuccess(settings: AppSettings, o: Outcome, today: String) {
        if (o.newSuccess.isEmpty()) return
        if (!settings.notifyAttendance) return
        if (settings.lastNotified("auto_checkin_ok") == today) return
        settings.setLastNotified("auto_checkin_ok", today)
        val games = o.newSuccess.joinToString("·")
        val body = if (o.hasAnyFail) "$games 완료. 남은 게임은 잠시 후 다시 해 볼게요."
        else "$games 완료. 오늘 보상 챙기세요."
        Notifier.notify(Notifier.ID_AUTO_CHECKIN, "출석 대신 해 뒀어요", body)
    }

    /** 실패가 있으면 하루 1회 알림. AUTH 가 있으면 재연동 안내, 그 외엔 자동 재시도 안내. */
    private suspend fun maybeNotifyFailure(settings: AppSettings, o: Outcome, today: String) {
        if (!o.hasAnyFail) return
        if (settings.lastNotified("auto_checkin_fail") == today) return
        settings.setLastNotified("auto_checkin_fail", today)

        if (o.authFails.isNotEmpty()) {
            val games = (o.authFails + o.netFails + o.otherFails.map { it.first }).joinToString("·")
            val body = "$games 로그인이 풀렸어요.\n설정 ▸ HoYoLAB 연동에서 다시 연결하면 내일부터 이어서 해 드릴게요."
            Notifier.notify(Notifier.ID_AUTO_CHECKIN, "자동 출석이 멈췄어요", body)
            return
        }
        val lines = buildList {
            if (o.netFails.isNotEmpty()) add("${o.netFails.joinToString("·")} — 서버에 닿지 못했어요")
            o.otherFails.forEach { (name, msg) -> add("$name: $msg") }
        }
        val body = lines.joinToString("\n") + "\n\n잠시 후 다시 해 볼게요. 급하면 앱에서 직접 출석해도 돼요."
        Notifier.notify(Notifier.ID_AUTO_CHECKIN, "출석 일부를 못 했어요", body)
    }
}
