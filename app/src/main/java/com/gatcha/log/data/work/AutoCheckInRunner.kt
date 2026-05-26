package com.gatcha.log.data.work

import android.content.Context
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.Notifier
import com.gatcha.log.data.api.CheckInResult
import com.gatcha.log.data.api.HoyolabApi

/**
 * 자동 출석 시도 1회 + 결과 집계. 워커(6h 주기)와 UI(토글 ON 즉시)에서 모두 호출한다.
 * 실패 시 알림 발송은 [postFailureNotification] 로 제어 — 워커에서만 알림 띄우고
 * UI 에서 부를 때는 토스트로 즉시 피드백하므로 알림 중복을 피한다.
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
        ctx: Context,
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
        if (changed) repo.saveAttendance(attendance)

        val outcome = Outcome(newSuccess, alreadyDone, authFails, netFails, otherFails)
        if (postFailureNotification) maybeNotifyFailure(ctx, settings, outcome, today)
        return outcome
    }

    /** 실패가 있으면 하루 1회 알림. AUTH 가 있으면 재연동 안내, 그 외엔 자동 재시도 안내. */
    private fun maybeNotifyFailure(ctx: Context, settings: AppSettings, o: Outcome, today: String) {
        if (!o.hasAnyFail) return
        if (settings.lastNotified("auto_checkin_fail") == today) return
        settings.setLastNotified("auto_checkin_fail", today)

        if (o.authFails.isNotEmpty()) {
            val games = (o.authFails + o.netFails + o.otherFails.map { it.first }).joinToString("·")
            val body = "${games}: HoYoLAB 쿠키가 만료된 것 같아요.\n설정 ▸ HoYoLAB 연동에서 다시 연동해주세요."
            Notifier.notify(ctx, Notifier.ID_AUTO_CHECKIN, "자동 출석 — 재연동 필요", body)
            return
        }
        val lines = buildList {
            if (o.netFails.isNotEmpty()) add("${o.netFails.joinToString("·")}: 네트워크 오류")
            o.otherFails.forEach { (name, msg) -> add("$name: $msg") }
        }
        val body = lines.joinToString("\n") + "\n\n잠시 후 자동으로 다시 시도해요."
        Notifier.notify(ctx, Notifier.ID_AUTO_CHECKIN, "자동 출석 일부 실패", body)
    }

}
