package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HomeLogic
import com.gatcha.log.util.currentTimeMillis

/**
 * **확정 시각 알림 사전 예약** — 앱을 한동안 안 열어도 오게 만드는 장치.
 *
 * [NotificationChecker] 는 '지금 상태를 보고 즉시 쏘는' 방식이라 실행 기회가 있어야 한다.
 * iOS 의 BGAppRefreshTask 는 실행 시점이 OS 재량이고 앱이 강제 종료돼 있으면 아예 돌지 않아,
 * 장시간 앱을 안 열면 알림이 통째로 밀렸다.
 *
 * 그런데 픽업 마감·시즌 마감·정기결제·데일리 요약은 **울릴 시각을 미리 계산할 수 있다.**
 * 그 시각들을 OS 알림 센터에 미리 넣어두면(iOS UNCalendarNotificationTrigger) 앱 실행과 무관하게
 * 정시에 발송된다. 출석 리마인더(AttendanceReminder)가 이미 쓰던 방식을 나머지로 넓힌 것이다.
 *
 * 예약 대상이 못 되는 것(새 공지·예산 초과)은 그때그때 데이터를 봐야 알 수 있어 여전히
 * [NotificationChecker] 담당이다. 반면 재화 가득참은 '남은 초'로 시각을 계산할 수 있어 예약 대상이다.
 *
 * 원본 데이터는 전부 **로컬 캐시**(배너·전투 진행도·구독·실시간 노트)라 네트워크 없이 계산된다.
 */

/** 예약할 알림 한 건. [whenMillis] 는 발송 시각(로컬). */
data class ScheduledAlert(
    /** 예약 식별자 — 재예약 시 같은 키는 덮어쓴다. */
    val key: String,
    val title: String,
    val text: String,
    val whenMillis: Long,
    /** 탭 시 이동할 딥링크(비어 있으면 앱만 연다). */
    val link: String = "",
    /** 매일 반복 여부(데일리 요약). true 면 [whenMillis] 의 시:분만 쓴다. */
    val repeatsDaily: Boolean = false,
)

object ScheduledAlerts {

    /** iOS 는 앱당 대기 알림 64건 한도 — 여유를 두고 자른다. */
    const val MAX_PENDING = 48

    /** 마감 알림을 띄울 로컬 시각(시). 마감 시각이 새벽이어도 사람이 볼 시간에 울린다. */
    private const val ALERT_HOUR = 9

    /** 마감 며칠 전에 알릴지 — [NotificationChecker] 의 D-3/D-1 레벨과 맞춘다. */
    private val LEAD_DAYS = listOf(3, 1)

    private const val DAY_MS = 86_400_000L

    /**
     * 지금 시점에서 예약해야 할 알림 목록. 이미 지난 시각은 제외하고 임박한 순으로 자른다.
     * 토글이 꺼진 종류는 아예 만들지 않는다(= 재예약 시 자연히 사라진다).
     */
    fun build(
        settings: AppSettings,
        repo: GatchaRepository,
        nowMillis: Long = currentTimeMillis(),
    ): List<ScheduledAlert> {
        val out = mutableListOf<ScheduledAlert>()

        // 데일리 요약 모드는 개별 알림을 억제하고 하루 1건으로 합친다([NotificationChecker] 와 동일 정책).
        // 예약 시점엔 그날 수치를 알 수 없어 문구는 고정 — 자세한 내용은 앱에서 본다.
        if (settings.notifyDailySummary) {
            return listOf(
                ScheduledAlert(
                    key = "daily_summary",
                    title = "오늘의 가챠 요약",
                    text = "출석·일일 임무·재화 상태를 확인할 시간이에요",
                    whenMillis = DateUtil.localTimeOnDay(nowMillis, settings.notifyDailySummaryHour),
                    repeatsDaily = true,
                ),
            )
        }

        // ① 픽업 마감 — 게임별로 가장 임박한 종료 기준 D-3/D-1.
        if (settings.notifyPickup) {
            repo.loadActiveBanners().filter { !it.isEndUnknown && it.endMillis > nowMillis }
                .groupBy { it.game }
                .forEach { (gameName, list) ->
                    val end = list.minOf { it.endMillis }
                    val game = GameData.byNameOrNull(gameName)
                    val shortName = game?.shortName ?: gameName
                    val names = list.filter { it.endMillis == end }.joinToString(", ") { it.name }
                    LEAD_DAYS.forEach { lead ->
                        val at = DateUtil.localTimeOnDay(end - lead * DAY_MS, ALERT_HOUR)
                        if (at > nowMillis) {
                            out += ScheduledAlert(
                                key = "pickup:${game?.key ?: gameName}:$lead",
                                title = "$shortName 픽업 마감 임박",
                                text = "$names — D-$lead 예요. 마지막 기회를 놓치지 마세요",
                                whenMillis = at,
                            )
                        }
                    }
                }
        }

        // ② 전투 콘텐츠 시즌 마감 — 미클리어인 모드만(클리어했으면 알릴 이유가 없다).
        if (settings.notifyCombat) {
            repo.loadCombatModes()
                .filter { it.hasData && it.maxStars > 0 && it.endMillis > nowMillis && it.stars < it.maxStars }
                .forEach { c ->
                    val game = GameData.byNameOrNull(c.game)
                    val shortName = game?.shortName ?: c.game
                    LEAD_DAYS.forEach { lead ->
                        val at = DateUtil.localTimeOnDay(c.endMillis - lead * DAY_MS, ALERT_HOUR)
                        if (at > nowMillis) {
                            out += ScheduledAlert(
                                key = "combat:${game?.key ?: c.game}:${c.name}:$lead",
                                title = "$shortName ${c.name} 마감 임박",
                                text = "${c.stars}/${c.maxStars} — D-$lead 예요. 시즌이 끝나면 보상이 사라져요",
                                whenMillis = at,
                            )
                        }
                    }
                }
        }

        // ③ 정기결제 갱신 — 결제 전날 아침.
        if (settings.notifySubscription) {
            repo.loadSubscriptions().forEach { sub ->
                val d = sub.dDay(nowMillis)
                // 결제일(오늘로부터 d일 뒤)의 전날 09:00.
                val at = DateUtil.localTimeOnDay(nowMillis + (d - 1) * DAY_MS, ALERT_HOUR)
                if (at > nowMillis) {
                    out += ScheduledAlert(
                        key = "sub:${sub.id}",
                        title = "정기결제 갱신 내일",
                        text = "${sub.name} ₩${comma(sub.amount)} 결제 예정이에요",
                        whenMillis = at,
                    )
                }
            }
        }

        // ④ 재화(레진·개척력·배터리) 가득참 — 상류가 '남은 초'를 주므로 가득 차는 시각을 정확히 안다.
        //    실시간으로 와야 하는 알림이지만, 시각을 계산할 수 있으니 미리 예약해 앱 실행과 무관하게 보낸다.
        if (settings.notifyResin) {
            repo.loadLiveNotes().filter { it.resinFullAtMillis > nowMillis && it.maxResin > 0 }.forEach { n ->
                val game = GameData.byNameOrNull(n.game)
                out += ScheduledAlert(
                    key = "resin:${game?.key ?: n.game}",
                    title = "${game?.shortName ?: n.game} 재화 가득참",
                    text = "${n.resinLabel}가 가득 찼어요 (${n.maxResin}/${n.maxResin})",
                    whenMillis = n.resinFullAtMillis,
                )
            }
        }

        return out.sortedBy { it.whenMillis }.take(MAX_PENDING)
    }

    /**
     * 지금 상태로 예약을 통째로 갈아끼운다. 데이터가 바뀔 때마다(배너·전투 캐시 갱신, 구독 편집,
     * 알림 토글) 호출하면 된다 — 오래된 예약은 [AlertScheduler] 가 지운다.
     */
    fun reschedule(
        settings: AppSettings,
        repo: GatchaRepository,
        nowMillis: Long = currentTimeMillis(),
    ) {
        runCatching { AlertScheduler.replaceAll(build(settings, repo, nowMillis)) }
    }

    private fun comma(v: Long): String {
        val s = v.toString()
        val sb = StringBuilder()
        for (i in s.indices) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(s[i])
        }
        return sb.toString()
    }
}

/**
 * 플랫폼 알림 예약기.
 * - iOS: UNCalendarNotificationTrigger 로 미리 등록 → 앱 실행과 무관하게 정시 발송.
 * - Android: WorkManager 주기 작업(4시간)이 이미 커버하므로 no-op.
 */
expect object AlertScheduler {
    /**
     * 이 플랫폼이 확정 시각 알림을 **사전 예약**으로 처리하는가.
     *
     * true(iOS)면 픽업·시즌 마감·재화 가득참·정기결제·데일리 요약은 예약으로만 나가고,
     * [NotificationChecker] 는 그 종류를 건너뛴다 — 둘 다 쏘면 같은 알림이 두 번 온다.
     * false(Android)면 반대로 주기 워커의 즉시 점검이 전담한다.
     */
    val schedulesAhead: Boolean

    /** 기존 예약을 모두 지우고 [alerts] 로 교체. */
    fun replaceAll(alerts: List<ScheduledAlert>)
}
