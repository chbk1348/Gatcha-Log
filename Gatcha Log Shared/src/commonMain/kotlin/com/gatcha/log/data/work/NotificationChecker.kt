package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HomeLogic
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.Notifier
import com.gatcha.log.data.TaskCompletion
import com.gatcha.log.data.subscriptionNotificationId
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.data.api.NewsApi
import com.gatcha.log.util.currentTimeMillis

/**
 * 로컬 알림 점검 — 예산(전체+게임별)/출석 리마인더/재화 가득참/픽업 마감/정기결제 갱신.
 *
 * 기존 :GL_Android 의 GatchaWorker.checkNotifications 로직을 commonMain 으로 끌어올린 것.
 * → Android(WorkManager)·iOS(BGTaskScheduler) 양쪽에서 동일하게 호출(패리티).
 * 각 토글이 켜져 있을 때만, dedup 키로 중복 발송을 막는다(키 문자열·포맷은 구버전과 동일 — 발송 이력 호환).
 *
 * 27.33.0:
 * - 데일리 요약 ON → 개별 알림을 억제하고, 정한 시각에 그날 상태를 1건으로 묶어 발송.
 * - 방해금지(DnD) ON → 조용한 시간대엔 개별 알림 보류(dedup 미설정 → 시간대 벗어나면 다음 주기에 발송).
 */
object NotificationChecker {

    suspend fun run(settings: AppSettings, repo: GatchaRepository, cfg: HoyolabConfig) {
        val now = currentTimeMillis()

        // 실시간 노트는 **알림 토글·방해금지와 무관하게** 먼저 받는다.
        //
        // 예전엔 재화 알림 토글(notifyResin) 안에서만 조회했다. 그런데 이 노트는 재화 알림만의
        // 재료가 아니다 — 재화 완충 예약([ScheduledAlerts])의 근거이자 숙제 완주율의 유일한
        // 관측 기회다. 토글 하나를 끄면 그것들까지 같이 죽었고, 방해금지 시간대·데일리 요약
        // 모드에서는 아래 return 에 걸려 아예 조회가 안 됐다.
        val notes = fetchLiveNotes(repo, cfg)

        // 숙제 완주율 관측 — 앱을 안 열어도 기록이 쌓인다(예전엔 포그라운드 ViewModel 전용이었다).
        runCatching { TaskCompletion.recordAll(repo, notes, now) }

        // 데일리 요약 모드: 개별 알림 억제, 정한 시각에 1건 통합 발송(하루 1회).
        // 사전 예약 플랫폼(iOS)에서는 요약도 예약이 담당하므로 여기선 아무것도 하지 않는다
        // — 둘 다 쏘면 같은 날 요약이 두 번 온다.
        if (settings.notifyDailySummary) {
            if (!AlertScheduler.schedulesAhead) maybeSendDailySummary(settings, repo, cfg, now)
            return
        }

        // 방해금지: 조용한 시간대엔 개별 알림 보류(다음 주기에 재평가).
        if (isQuietNow(settings, now)) return

        runIndividualChecks(settings, repo, cfg, notes)
    }

    /**
     * 연동된 게임의 실시간 노트를 받아 캐시에 병합 저장하고, 이번에 받은 것만 돌려준다.
     *
     * 캐시에 남기는 이유: 이 직후 [ScheduledAlerts] 가 '가득 차는 시각'을 미리 예약하는데,
     * 예전엔 받은 걸 쓰고 버려서 예약이 **항상 직전 세션의 캐시**로 만들어졌다
     * (캐시를 쓰는 곳은 포그라운드 화면 로드뿐이었다) → 백그라운드에서는 예약이 갱신되지 않아
     * "앱을 열어야만 알림이 오는" 상태가 됐다.
     */
    private suspend fun fetchLiveNotes(repo: GatchaRepository, cfg: HoyolabConfig): List<LiveNote> {
        if (!cfg.isLinked) return emptyList()
        val uids = mapOf("genshin" to cfg.genshinUid, "hsr" to cfg.hsrUid, "zzz" to cfg.zzzUid)
        val fresh = mutableListOf<LiveNote>()
        for (game in GameData.attendanceGames) {
            val uid = uids[game.key].orEmpty()
            if (uid.isBlank()) continue
            val note = HoyolabApi.getLiveNote(cfg.ltuid, cfg.ltoken, game.key, uid).note ?: continue
            fresh += note
        }
        if (fresh.isNotEmpty()) {
            runCatching { repo.saveLiveNotes(mergeLiveNotes(repo.loadLiveNotes(), fresh)) }
        }
        return fresh
    }

    private suspend fun runIndividualChecks(
        settings: AppSettings,
        repo: GatchaRepository,
        cfg: HoyolabConfig,
        notes: List<LiveNote>,
    ) {
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

            // 게임별 한도 — 게임마다 별도 알림 ID·dedup 키.
            // 게임별 합계는 **한 번의 순회**로 만든다. 예전엔 한도가 걸린 게임마다 이번 달 지출을
            // 통째로 다시 필터해서 O(게임수 × 지출수) 였다.
            val monthByGame = monthSpendings
                .groupingBy { GameData.byNameOrNull(it.gameName)?.key ?: it.gameName }
                .fold(0L) { acc, s -> acc + s.amount }
            repo.loadGameBudgets().forEach { (gameKey, limit) ->
                if (limit <= 0) return@forEach
                val game = GameData.byNameOrNull(gameKey) ?: return@forEach
                val total = monthByGame[gameKey] ?: 0L
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

        // ③ 행동력 가득참 — 게임별 하루 1회. 조회·캐시는 [fetchLiveNotes] 가 이미 했고 여기선 판정만 한다.
        if (settings.notifyResin) {
            val today = DateUtil.hoyoDayKey()
            notes.forEach { note ->
                val game = GameData.byNameOrNull(note.game) ?: return@forEach
                if (note.maxResin <= 0 || note.currentResin < note.maxResin) return@forEach
                val tag = "resin:${game.key}"
                if (settings.lastNotified(tag) == today) return@forEach
                settings.setLastNotified(tag, today)
                Notifier.notify(
                    Notifier.ID_RESIN_BASE + game.ordinal,
                    "${game.shortName} 행동력 가득참",
                    "${note.resinLabel}가 가득 찼어요 (${note.currentResin}/${note.maxResin})",
                )
            }
        }

        // ④ 픽업 마감 임박 (로컬 배너 캐시) — 게임별 1회, D-3/D-1 레벨.
        //    iOS 는 [ScheduledAlerts] 가 같은 알림을 미리 예약하므로 여기선 건너뛴다(중복 방지).
        if (settings.notifyPickup && !AlertScheduler.schedulesAhead) {
            val now = currentTimeMillis()
            repo.loadActiveBanners().filter { it.endMillis > now }
                .groupBy { it.game }
                .forEach { (gameName, list) ->
                    // dDay 를 배너당 한 번만 — 예전엔 minOf 와 filter 에서 각각 다시 계산했다.
                    val withD = list.map { it to it.dDay(now) }
                    val minD = withD.minOf { it.second }
                    val level = when { minD <= 1 -> "d1"; minD <= 3 -> "d3"; else -> null } ?: return@forEach
                    val tag = "pickup:$gameName"
                    if (settings.lastNotified(tag) != level) {
                        settings.setLastNotified(tag, level)
                        val game = GameData.byNameOrNull(gameName)
                        val shortName = game?.shortName ?: gameName
                        val nid = Notifier.ID_PICKUP_BASE + (game?.ordinal ?: 0)
                        val names = withD.filter { it.second <= 3 }.joinToString(", ") { it.first.name }
                        val whenLabel = if (minD <= 1) "오늘·내일 종료" else "D-$minD 종료"
                        Notifier.notify(nid, "$shortName 픽업 마감 임박", "$names — $whenLabel 전 마지막 기회예요")
                    }
                }
        }

        // ⑤ 정기결제 갱신 임박 (로컬 구독 목록) — D-1/오늘, 구독별 월 1회.
        if (settings.notifySubscription && !AlertScheduler.schedulesAhead) {
            val now = currentTimeMillis()
            val ym = "${DateUtil.year(now)}-${DateUtil.month(now)}"
            repo.loadSubscriptions().forEach { sub ->
                val d = sub.dDay(now)
                if (d <= 1) {
                    val tag = "sub:${sub.id}"
                    if (settings.lastNotified(tag) != ym) {
                        settings.setLastNotified(tag, ym)
                        val whenLabel = if (d <= 0) "오늘" else "내일"
                        Notifier.notify(
                            subscriptionNotificationId(sub.id),
                            "정기결제 갱신 $whenLabel",
                            "${sub.name} ₩${won(sub.amount)} 결제 예정이에요",
                        )
                    }
                }
            }
        }

        // ⑥ 전투 콘텐츠 시즌 마감 임박 (로컬 진행도 캐시) — 게임+모드별 1회, D-3/D-1 레벨.
        //    놓치면 그 시즌 보상은 복구가 안 되므로 미클리어일 때만 알린다(판정은 HomeLogic 공유 룰).
        if (settings.notifyCombat && !AlertScheduler.schedulesAhead) {
            val now = currentTimeMillis()
            HomeLogic.combatDeadlines(repo.loadCombatModes(), now).forEach { c ->
                val level = if (c.dDay <= 1) "d1" else "d3"
                val tag = "combat:${c.gameShort}:${c.mode}"
                if (settings.lastNotified(tag) != level) {
                    settings.setLastNotified(tag, level)
                    val game = GameData.byNameOrNull(c.gameShort)   // 이름 인덱스 — shortName 도 키다
                    val nid = Notifier.ID_COMBAT_BASE + (game?.ordinal ?: 0)
                    val whenLabel = if (c.dDay <= 0) "오늘 마감" else if (c.dDay == 1) "내일 마감" else "D-${c.dDay}"
                    Notifier.notify(
                        nid,
                        "${c.gameShort} ${c.mode} 마감 임박",
                        "${c.stars}/${c.maxStars} — $whenLabel 이에요. 시즌이 끝나면 보상이 사라져요",
                    )
                }
            }
        }

        // ⑦ 새 게임 공지 — 게임별로 '마지막으로 알린 공지 시각'보다 새 글이 올라왔을 때만.
        if (settings.notifyNews) {
            GameData.games.filter { it.newsSource != null }.forEach { game ->
                // 실패(null)면 조용히 건너뛴다 — 네트워크 오류를 '새 공지 없음'으로 오해하지 않는다.
                val notices = NewsApi.notices(game) ?: return@forEach
                val latest = notices.maxByOrNull { it.createdAtMillis } ?: return@forEach
                val tag = "news:${game.key}"
                val lastSeen = settings.lastNotified(tag).toLongOrNull()

                if (lastSeen == null) {
                    // 최초 1회는 기준선만 잡는다 — 안 그러면 켜자마자 과거 공지로 알림이 쏟아진다.
                    settings.setLastNotified(tag, latest.createdAtMillis.toString())
                    return@forEach
                }
                if (latest.createdAtMillis > lastSeen) {
                    // **앱을 보고 있으면 아무것도 하지 않는다 — 기준선도 그대로 둔다.**
                    //
                    // 이 점검은 앱을 여는 순간에도 돈다(백그라운드 실행이 못 미더워 보조 트리거를
                    // 둔다 — [AppVisibility] 참고). 보고 있는 사람에게 화면의 '게임 소식' 카드에
                    // 이미 떠 있는 것을 알림으로 또 보내지 않으려고 여기서 걸러낸다.
                    //
                    // 예전엔 **거르면서 기준선은 올렸다.** 그랬더니 새 공지가 거의 전부 이 포그라운드
                    // 점검에서 소진돼 버렸다 — 백그라운드 점검은 4시간에 한 번인데(iOS 는 OS 재량)
                    // 그 사이 앱을 한 번만 열어도 기준선이 최신이 되어, 정작 알림으로 나갈 것이
                    // 남지 않았다. '새 공지 알림이 안 온다'가 여기서 나왔다.
                    //
                    // 이제는 기준선을 그대로 두고 다음 백그라운드 점검에 넘긴다. 대가로 앱에서 이미
                    // 본 공지가 나중에 알림으로 한 번 더 올 수 있는데, 알림이 아예 안 오는 것보다 낫다.
                    if (AppVisibility.isForeground) return@forEach
                    settings.setLastNotified(tag, latest.createdAtMillis.toString())
                    val newCount = notices.count { it.createdAtMillis > lastSeen }
                    val more = if (newCount > 1) " 외 ${newCount - 1}건" else ""
                    Notifier.notify(
                        Notifier.ID_NEWS_BASE + game.ordinal,
                        "${game.shortName} 새 공지",
                        latest.title + more,
                        link = "news:${latest.id}",
                    )
                }
            }
        }
    }

    /**
     * 실시간 노트 캐시 병합 — [fresh] 로 덮어쓰되, **이번에 못 받은 게임은 [cached] 값을 유지한다.**
     *
     * 통째로 교체하면 조회에 실패한 게임(네트워크 오류·UID 미등록)의 '가득 차는 시각'이 사라지고,
     * [ScheduledAlerts] 가 그 게임 예약을 만들지 못해 알림이 조용히 없어진다.
     */
    internal fun mergeLiveNotes(cached: List<LiveNote>, fresh: List<LiveNote>): List<LiveNote> {
        if (fresh.isEmpty()) return cached
        val byGame = cached.associateByTo(LinkedHashMap()) { it.game }
        fresh.forEach { byGame[it.game] = it }
        return byGame.values.toList()
    }

    /**
     * 방해금지 시간대 내인지(기기 로컬 시각 기준). start>end면 자정 넘김(예: 23~8)으로 처리.
     *
     * [ScheduledAlerts] 도 쓴다 — 사전 예약은 발송 순간에 코드가 돌지 않아(OS·알람이 쏜다)
     * 그때 걸러낼 수 없으므로, 예약을 만드는 시점에 이 판정으로 시각을 밀어둔다.
     */
    internal fun isQuietNow(settings: AppSettings, now: Long): Boolean {
        if (!settings.notifyDndEnabled) return false
        val h = DateUtil.localHour(now)
        val start = settings.notifyDndStartHour
        val end = settings.notifyDndEndHour
        if (start == end) return false
        return if (start < end) h in start until end else h >= start || h < end
    }

    /**
     * 데일리 요약 — 정한 시각 이후 하루 1회, 그날 상태를 재계산해 1건으로 발송.
     *
     * [skipHourCheck] 는 **예약 알람이 정시에 깨워서 부른 경우**다(Android). 예약은 이미 사용자가
     * 정한 시각에 울리므로 시각 조건을 다시 볼 이유가 없고, 알람이 15분쯤 일찍 울리면
     * (비정확 알람) 조건에 걸려 그날 요약이 통째로 날아간다.
     *
     * iOS 는 OS 가 직접 쏘는 구조라 발송 순간에 코드가 못 돌아 고정 문구뿐이다. Android 는
     * 알람이 우리 프로세스를 깨우므로 여기서 **그날 실제 수치**를 계산해 보낼 수 있다.
     */
    internal suspend fun maybeSendDailySummary(
        settings: AppSettings,
        repo: GatchaRepository,
        cfg: HoyolabConfig,
        now: Long,
        skipHourCheck: Boolean = false,
    ) {
        if (!skipHourCheck && DateUtil.localHour(now) < settings.notifyDailySummaryHour) return
        val dayKey = DateUtil.dayKey(now)
        if (settings.lastNotified("summary") == dayKey) return
        val lines = buildSummaryLines(settings, repo, cfg, now)
        settings.setLastNotified("summary", dayKey) // 빈 내용이어도 오늘은 더 띄우지 않음
        if (lines.isEmpty()) return
        Notifier.notify(Notifier.ID_DAILY_SUMMARY, "오늘의 가챠 요약", lines.joinToString("\n• ", prefix = "• "))
    }

    /** 요약 본문 줄 — 켜진 토글에 한해 그날 상태를 한 줄씩 모은다(개별 알림과 동일 데이터 소스). */
    private suspend fun buildSummaryLines(settings: AppSettings, repo: GatchaRepository, cfg: HoyolabConfig, now: Long): List<String> {
        val lines = mutableListOf<String>()
        val y = DateUtil.year(now); val m = DateUtil.month(now)

        if (settings.notifyBudget) {
            val budget = repo.loadBudget()
            if (budget > 0) {
                val total = repo.loadSpendings().filter { DateUtil.isSameMonth(it.dateMillis, y, m) }.sumOf { it.amount }
                val pct = (total * 100 / budget).toInt()
                if (pct >= 90) {
                    lines += if (total > budget) "이번 달 예산 초과 (${pct}%)"
                    else "이번 달 예산 ${pct}% 사용 · ₩${won((budget - total).coerceAtLeast(0))} 남음"
                }
            }
        }
        if (settings.notifyAttendance && cfg.isLinked && DateUtil.hoyoHour(now) >= 18) {
            val done = repo.loadAttendance()[DateUtil.hoyoDayKey(now)] ?: emptySet()
            val pending = GameData.attendanceGames.filter { it.key !in done }
            if (pending.isNotEmpty()) lines += "미출석 ${pending.size}개 · ${pending.joinToString(", ") { it.shortName }}"
        }
        if (settings.notifyResin && cfg.isLinked) {
            // 캐시를 읽는다 — [run] 이 이 직전에 [fetchLiveNotes] 로 갱신해 뒀다.
            // 예전엔 여기서 3게임을 다시 조회해서, 요약 1건 만드는 데 왕복이 두 배로 났다.
            val full = repo.loadLiveNotes()
                .filter { it.maxResin > 0 && it.currentResin >= it.maxResin }
                .mapNotNull { GameData.byNameOrNull(it.game)?.shortName }
            if (full.isNotEmpty()) lines += "행동력 가득참 · ${full.joinToString(", ")}"
        }
        if (settings.notifyPickup) {
            repo.loadActiveBanners().filter { it.endMillis > now }
                .groupBy { it.game }
                .forEach { (gameName, list) ->
                    val minD = list.minOf { it.dDay(now) }
                    if (minD <= 3) {
                        val shortName = GameData.byNameOrNull(gameName)?.shortName ?: gameName
                        lines += "픽업 마감 ${if (minD <= 1) "임박" else "D-$minD"} · $shortName"
                    }
                }
        }
        if (settings.notifySubscription) {
            // dDay 를 구독당 한 번만(필터·문구에서 각각 계산하던 것). dDay 는 날짜 연산이라 공짜가 아니다.
            repo.loadSubscriptions().forEach { sub ->
                val d = sub.dDay(now)
                if (d <= 1) lines += "${if (d <= 0) "오늘" else "내일"} 결제 · ${sub.name} ₩${won(sub.amount)}"
            }
        }
        if (settings.notifyCombat) {
            HomeLogic.combatDeadlines(repo.loadCombatModes(), now).forEach { c ->
                lines += "${c.mode} 마감 ${if (c.dDay <= 0) "오늘" else "D-${c.dDay}"} · ${c.gameShort} ${c.stars}/${c.maxStars}"
            }
        }
        return lines
    }

    /** 천 단위 콤마(비음수 금액). */
    private fun won(v: Long): String {
        val s = v.toString()
        val sb = StringBuilder()
        val n = s.length
        for (i in 0 until n) {
            if (i > 0 && (n - i) % 3 == 0) sb.append(',')
            sb.append(s[i])
        }
        return sb.toString()
    }
}
