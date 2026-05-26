package com.gatcha.log.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.Notifier
import com.gatcha.log.data.api.HoyolabApi
import java.util.Calendar

/**
 * 백그라운드 주기 작업 — 자동 출석체크 + 로컬 알림 점검(각 토글이 켜져 있을 때만).
 * 매 실행마다 "오늘(베이징) 아직 안 한 것"만 처리하고, 알림은 중복 방지 키로 하루 1회만 발송한다.
 */
class GatchaWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = AppSettings(ctx)
        val repo = GatchaRepository(ctx, AppSettings.currentAccountId(ctx))
        val cfg = repo.loadHoyolab()
        if (settings.autoCheckIn) runCatching { autoCheckIn(ctx, settings, repo, cfg) }
        runCatching { checkNotifications(ctx, settings, repo, cfg) }
        return Result.success()
    }

    /** 출석 시도·결과 집계·실패 알림은 [AutoCheckInRunner] 가 담당(UI 호출과 동일 흐름). */
    private suspend fun autoCheckIn(ctx: Context, settings: AppSettings, repo: GatchaRepository, cfg: HoyolabConfig) {
        AutoCheckInRunner.run(ctx, settings, repo, cfg, postFailureNotification = true)
    }

    private suspend fun checkNotifications(ctx: Context, settings: AppSettings, repo: GatchaRepository, cfg: HoyolabConfig) {
        // ① 예산 임박/초과 (로컬 데이터) — 월·레벨 단위 1회
        if (settings.notifyBudget) {
            val budget = repo.loadBudget()
            if (budget > 0) {
                val now = System.currentTimeMillis()
                val y = DateUtil.year(now); val m = DateUtil.month(now)
                val total = repo.loadSpendings().filter { DateUtil.isSameMonth(it.dateMillis, y, m) }.sumOf { it.amount }
                val pct = (total * 100 / budget).toInt()
                val level = when { total > budget -> "over"; pct >= 90 -> "near"; else -> null }
                if (level != null) {
                    val key = "$y-$m:$level"
                    if (settings.lastNotified("budget") != key) {
                        settings.setLastNotified("budget", key)
                        if (level == "over") Notifier.notify(ctx, Notifier.ID_BUDGET, "예산 초과", "이번 달 예산을 초과했어요 (${pct}%)")
                        else Notifier.notify(ctx, Notifier.ID_BUDGET, "예산 임박", "이번 달 예산의 ${pct}%를 사용했어요")
                    }
                }
            }
        }

        // ② 출석 리마인더 (베이징 저녁 이후 미출석) — 하루 1회
        if (settings.notifyAttendance && cfg.isLinked) {
            val hour = DateUtil.hoyoCalendar().get(Calendar.HOUR_OF_DAY)
            if (hour >= 18) {
                val today = DateUtil.hoyoDayKey()
                val done = repo.loadAttendance()[today] ?: emptySet()
                val pending = GameData.attendanceGames.filter { it.key !in done }
                if (pending.isNotEmpty() && settings.lastNotified("attend") != today) {
                    settings.setLastNotified("attend", today)
                    Notifier.notify(ctx, Notifier.ID_ATTEND, "출석 체크 알림", "${pending.joinToString(", ") { it.shortName }} 아직 출석 안 했어요")
                }
            }
        }

        // ③ 재화 가득참 (실시간 노트) — 게임별 하루 1회
        if (settings.notifyResin && cfg.isLinked) {
            val today = DateUtil.hoyoDayKey()
            val uids = mapOf("genshin" to cfg.genshinUid, "hsr" to cfg.hsrUid, "zzz" to cfg.zzzUid)
            for (game in GameData.attendanceGames) {
                val uid = uids[game.key].orEmpty()
                if (uid.isBlank()) continue
                val note = HoyolabApi.getLiveNote(cfg.ltuid, cfg.ltoken, game.key, uid).note ?: continue
                if (note.maxResin > 0 && note.currentResin >= note.maxResin) {
                    val tag = "resin:${game.key}"
                    if (settings.lastNotified(tag) != today) {
                        settings.setLastNotified(tag, today)
                        Notifier.notify(ctx, Notifier.ID_RESIN_BASE + game.ordinal, "${game.shortName} 재화 가득참", "재화가 가득 찼어요 (${note.currentResin}/${note.maxResin})")
                    }
                }
            }
        }
    }
}
