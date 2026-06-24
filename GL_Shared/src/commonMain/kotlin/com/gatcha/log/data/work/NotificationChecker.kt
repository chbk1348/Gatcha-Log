package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.Notifier
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.util.currentTimeMillis

/**
 * 로컬 알림 점검 — 예산(전체+게임별)/출석 리마인더/재화 가득참.
 *
 * 기존 :GL_Android 의 GatchaWorker.checkNotifications 로직을 commonMain 으로 끌어올린 것.
 * → Android(WorkManager)·iOS(BGTaskScheduler) 양쪽에서 동일하게 호출(패리티).
 * 각 토글이 켜져 있을 때만, dedup 키로 중복 발송을 막는다(키 문자열·포맷은 구버전과 동일 — 발송 이력 호환).
 */
object NotificationChecker {

    suspend fun run(settings: AppSettings, repo: GatchaRepository, cfg: HoyolabConfig) {
        // ① 예산 임박/초과 (로컬 데이터) — 월·레벨 단위 1회. 전체 예산 + 게임별 한도.
        if (settings.notifyBudget) {
            val now = currentTimeMillis()
            val y = DateUtil.year(now); val m = DateUtil.month(now)
            val monthSpendings = repo.loadSpendings().filter { DateUtil.isSameMonth(it.dateMillis, y, m) }

            val budget = repo.loadBudget()
            if (budget > 0) {
                val total = monthSpendings.sumOf { it.amount }
                val pct = (total * 100 / budget).toInt()
                val level = when { total > budget -> "over"; pct >= 90 -> "near"; else -> null }
                if (level != null) {
                    val key = "$y-$m:$level"
                    if (settings.lastNotified("budget") != key) {
                        settings.setLastNotified("budget", key)
                        if (level == "over") Notifier.notify(Notifier.ID_BUDGET, "예산 초과", "이번 달 예산을 초과했어요 (${pct}%)")
                        else Notifier.notify(Notifier.ID_BUDGET, "예산 임박", "이번 달 예산의 ${pct}%를 사용했어요")
                    }
                }
            }

            // 게임별 한도 — 게임마다 별도 알림 ID·dedup 키
            repo.loadGameBudgets().forEach { (gameKey, limit) ->
                if (limit <= 0) return@forEach
                val game = GameData.games.firstOrNull { it.key == gameKey } ?: return@forEach
                val total = monthSpendings.filter { GameData.byNameOrNull(it.gameName)?.key == gameKey }.sumOf { it.amount }
                val pct = (total * 100 / limit).toInt()
                val level = when { total > limit -> "over"; pct >= 90 -> "near"; else -> null }
                if (level != null) {
                    val key = "$y-$m:$level"
                    val tag = "budget_game:$gameKey"
                    if (settings.lastNotified(tag) != key) {
                        settings.setLastNotified(tag, key)
                        val nid = Notifier.ID_BUDGET_GAME_BASE + game.ordinal
                        if (level == "over") Notifier.notify(nid, "${game.shortName} 예산 초과", "${game.shortName} 이번 달 한도를 초과했어요 (${pct}%)")
                        else Notifier.notify(nid, "${game.shortName} 예산 임박", "${game.shortName} 한도의 ${pct}%를 사용했어요")
                    }
                }
            }
        }

        // ② 출석 리마인더 (베이징 저녁 이후 미출석) — 하루 1회
        if (settings.notifyAttendance && cfg.isLinked) {
            if (DateUtil.hoyoHour() >= 18) {
                val today = DateUtil.hoyoDayKey()
                val done = repo.loadAttendance()[today] ?: emptySet()
                val pending = GameData.attendanceGames.filter { it.key !in done }
                if (pending.isNotEmpty() && settings.lastNotified("attend") != today) {
                    settings.setLastNotified("attend", today)
                    Notifier.notify(Notifier.ID_ATTEND, "출석 체크 알림", "${pending.joinToString(", ") { it.shortName }} 아직 출석 안 했어요")
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
                        Notifier.notify(Notifier.ID_RESIN_BASE + game.ordinal, "${game.shortName} 재화 가득참", "재화가 가득 찼어요 (${note.currentResin}/${note.maxResin})")
                    }
                }
            }
        }

        // ④ 픽업 마감 임박 (로컬 배너 캐시) — 게임별 1회, D-3/D-1 레벨.
        if (settings.notifyPickup) {
            val now = currentTimeMillis()
            repo.loadActiveBanners().filter { it.endMillis > now }
                .groupBy { it.game }
                .forEach { (gameName, list) ->
                    val minD = list.minOf { it.dDay(now) }
                    val level = when { minD <= 1 -> "d1"; minD <= 3 -> "d3"; else -> null } ?: return@forEach
                    val tag = "pickup:$gameName"
                    if (settings.lastNotified(tag) != level) {
                        settings.setLastNotified(tag, level)
                        val game = GameData.byNameOrNull(gameName)
                        val shortName = game?.shortName ?: gameName
                        val nid = Notifier.ID_PICKUP_BASE + (game?.ordinal ?: 0)
                        val names = list.filter { it.dDay(now) <= 3 }.joinToString(", ") { it.name }
                        val whenLabel = if (minD <= 1) "오늘·내일 종료" else "D-$minD 종료"
                        Notifier.notify(nid, "$shortName 픽업 마감 임박", "$names — $whenLabel 전 마지막 기회예요")
                    }
                }
        }
    }
}
