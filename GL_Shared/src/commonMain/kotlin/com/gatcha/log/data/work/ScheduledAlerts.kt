package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.Notifier
import com.gatcha.log.data.subscriptionNotificationId
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
    /**
     * 이 예약이 발송을 **책임지는** 즉시 점검 dedup 키. 비어 있지 않으면 예약 등록 성공 직후
     * [dedupValue] 로 선점해, [NotificationChecker] 가 같은 건을 또 쏘지 않게 한다.
     * (선례: `AttendanceReminder` 의 `attend` 키)
     */
    val dedupTag: String = "",
    val dedupValue: String = "",
)

/**
 * **지금 당장 보내야 하는 알림** — 예약으로는 담을 수 없는 것.
 *
 * 알릴 시각(마감 D-3/D-1 아침 9시 등)이 **이미 지났는데 마감은 아직 남은** 구간이 있다.
 * 그 구간은 예약이 안 만들어지고, iOS 는 [NotificationChecker] 가 해당 종류를 통째로
 * 건너뛰므로(`schedulesAhead`) 알림이 **한 건도 안 갔다** — 마감 하루 전인데 조용한 상태.
 * 그 구멍을 메우려고, 재예약을 도는 이 순간 직접 발송한다.
 */
data class ImmediateAlert(
    val id: Int,
    val title: String,
    val text: String,
    /** 중복 발송 방지 — 발송 직후 이 키를 [dedupValue] 로 찍는다. */
    val dedupTag: String,
    val dedupValue: String,
)

/** 재예약 1회분의 계산 결과. */
data class AlertPlan(
    val scheduled: List<ScheduledAlert>,
    val immediate: List<ImmediateAlert> = emptyList(),
)

object ScheduledAlerts {

    /** iOS 는 앱당 대기 알림 64건 한도 — 여유를 두고 자른다. */
    const val MAX_PENDING = 48

    /**
     * 데일리 요약 예약의 키. 이 예약만은 **문구가 발송 시점에야 정해진다**(그날 수치).
     * Android 는 알람이 우리 코드를 깨우므로 [AlertScheduler] 가 이 키를 알아보고
     * 고정 문구 대신 실제 요약을 만들어 보낸다.
     */
    const val KEY_DAILY_SUMMARY = "daily_summary"

    /** 마감 알림을 띄울 로컬 시각(시). 마감 시각이 새벽이어도 사람이 볼 시간에 울린다. */
    private const val ALERT_HOUR = 9

    /** 마감 며칠 전에 알릴지 — [NotificationChecker] 의 D-3/D-1 레벨과 맞춘다. */
    private val LEAD_DAYS = listOf(3, 1)

    private const val DAY_MS = 86_400_000L

    /**
     * 지금 시점에서 **예약할 것**과 **즉시 보낼 것**을 함께 산출한다.
     * 토글이 꺼진 종류는 아예 만들지 않는다(= 재예약 시 자연히 사라진다).
     *
     * 순수 함수다 — 저장소를 읽기만 하고 아무것도 쓰지 않는다. 발송·dedup 기록은 [reschedule] 몫.
     */
    fun plan(
        settings: AppSettings,
        repo: GatchaRepository,
        nowMillis: Long = currentTimeMillis(),
    ): AlertPlan {
        val out = mutableListOf<ScheduledAlert>()
        val now = mutableListOf<ImmediateAlert>()

        // 데일리 요약 모드는 개별 알림을 억제하고 하루 1건으로 합친다([NotificationChecker] 와 동일 정책).
        // 예약 시점엔 그날 수치를 알 수 없어 문구는 고정 — 자세한 내용은 앱에서 본다.
        if (settings.notifyDailySummary) {
            return AlertPlan(
                listOf(
                    ScheduledAlert(
                        key = KEY_DAILY_SUMMARY,
                        title = "오늘의 가챠 요약",
                        text = "출석·일일 임무·재화 상태를 확인할 시간이에요",
                        whenMillis = DateUtil.localTimeOnDay(nowMillis, settings.notifyDailySummaryHour),
                        repeatsDaily = true,
                    ),
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
                    var scheduledAny = false
                    LEAD_DAYS.forEach { lead ->
                        val at = shiftOutOfQuiet(settings, DateUtil.localTimeOnDay(end - lead * DAY_MS, ALERT_HOUR))
                        if (at > nowMillis) {
                            scheduledAny = true
                            out += ScheduledAlert(
                                key = "pickup:${game?.key ?: gameName}:$lead",
                                title = "$shortName 픽업 마감 임박",
                                text = "$names — D-$lead 예요. 마지막 기회를 놓치지 마세요",
                                whenMillis = at,
                            )
                        }
                    }
                    // 알림 시각이 전부 지났는데 마감은 남았다 → 예약이 못 담는다. 지금 보낸다.
                    if (!scheduledAny) {
                        val minD = list.minOf { it.dDay(nowMillis) }
                        levelOf(minD)?.let { level ->
                            val tag = "pickup:$gameName"
                            if (settings.lastNotified(tag) != level) {
                                val urgent = list.filter { it.dDay(nowMillis) <= 3 }
                                    .joinToString(", ") { it.name }
                                val whenLabel = if (minD <= 1) "오늘·내일 종료" else "D-$minD 종료"
                                now += ImmediateAlert(
                                    id = Notifier.ID_PICKUP_BASE + (game?.ordinal ?: 0),
                                    title = "$shortName 픽업 마감 임박",
                                    text = "$urgent — $whenLabel 전 마지막 기회예요",
                                    dedupTag = tag,
                                    dedupValue = level,
                                )
                            }
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
                    var scheduledAny = false
                    LEAD_DAYS.forEach { lead ->
                        val at = shiftOutOfQuiet(settings, DateUtil.localTimeOnDay(c.endMillis - lead * DAY_MS, ALERT_HOUR))
                        if (at > nowMillis) {
                            scheduledAny = true
                            out += ScheduledAlert(
                                key = "combat:${game?.key ?: c.game}:${c.name}:$lead",
                                title = "$shortName ${c.name} 마감 임박",
                                text = "${c.stars}/${c.maxStars} — D-$lead 예요. 시즌이 끝나면 보상이 사라져요",
                                whenMillis = at,
                            )
                        }
                    }
                    if (!scheduledAny) {
                        val d = c.dDay(nowMillis) ?: return@forEach
                        levelOf(d)?.let { level ->
                            // dedup 키 형식은 [NotificationChecker] ⑥ 과 동일하게 맞춘다(shortName:모드).
                            val tag = "combat:$shortName:${c.name}"
                            if (settings.lastNotified(tag) != level) {
                                val whenLabel = when {
                                    d <= 0 -> "오늘 마감"
                                    d == 1 -> "내일 마감"
                                    else -> "D-$d"
                                }
                                now += ImmediateAlert(
                                    id = Notifier.ID_COMBAT_BASE + (game?.ordinal ?: 0),
                                    title = "$shortName ${c.name} 마감 임박",
                                    text = "${c.stars}/${c.maxStars} — $whenLabel 이에요. 시즌이 끝나면 보상이 사라져요",
                                    dedupTag = tag,
                                    dedupValue = level,
                                )
                            }
                        }
                    }
                }
        }

        // ③ 정기결제 갱신 — 결제 전날 아침.
        if (settings.notifySubscription) {
            val ym = "${DateUtil.year(nowMillis)}-${DateUtil.month(nowMillis)}"
            repo.loadSubscriptions().forEach { sub ->
                val d = sub.dDay(nowMillis)
                // 결제일(오늘로부터 d일 뒤)의 전날 09:00.
                val at = shiftOutOfQuiet(settings, DateUtil.localTimeOnDay(nowMillis + (d - 1) * DAY_MS, ALERT_HOUR))
                if (at > nowMillis) {
                    out += ScheduledAlert(
                        key = "sub:${sub.id}",
                        title = "정기결제 갱신 내일",
                        text = "${sub.name} ₩${comma(sub.amount)} 결제 예정이에요",
                        whenMillis = at,
                    )
                } else if (d <= 1 && settings.lastNotified("sub:${sub.id}") != ym) {
                    // 결제 당일이거나, D-1 인데 09시를 넘겨 확인한 경우 — 예약 시각이 이미 지났다.
                    now += ImmediateAlert(
                        id = subscriptionNotificationId(sub.id),
                        title = "정기결제 갱신 ${if (d <= 0) "오늘" else "내일"}",
                        text = "${sub.name} ₩${comma(sub.amount)} 결제 예정이에요",
                        dedupTag = "sub:${sub.id}",
                        dedupValue = ym,
                    )
                }
            }
        }

        // ④ 재화(레진·개척력·배터리) 가득참 — 상류가 '남은 초'를 주므로 가득 차는 시각을 정확히 안다.
        //    실시간으로 와야 하는 알림이지만, 시각을 계산할 수 있으니 미리 예약해 앱 실행과 무관하게 보낸다.
        //
        //    **dedup 키를 선점**한다 — 예약이 발송되면 코드가 도는 게 아니라 OS 가 쏘는 것이라
        //    발송 이력이 안 남는다. 그 뒤 앱을 열면 [NotificationChecker] ③ 이 '아직 안 쐈다'고 보고
        //    같은 알림을 또 쐈다(같은 날 두 번). 예약이 책임지는 날짜를 미리 찍어 막는다.
        if (settings.notifyResin) {
            repo.loadLiveNotes().filter { it.resinFullAtMillis > nowMillis && it.maxResin > 0 }.forEach { n ->
                val game = GameData.byNameOrNull(n.game)
                val gameKey = game?.key ?: n.game
                // 새벽에 가득 차는 경우가 흔하다 — 방해금지면 해제 시각으로 민다.
                // dedup 값도 **민 뒤의 시각** 기준이어야 한다(예약이 책임지는 날짜와 어긋나면 중복 발송).
                val at = shiftOutOfQuiet(settings, n.resinFullAtMillis)
                out += ScheduledAlert(
                    key = "resin:$gameKey",
                    title = "${game?.shortName ?: n.game} 재화 가득참",
                    text = "${n.resinLabel}가 가득 찼어요 (${n.maxResin}/${n.maxResin})",
                    whenMillis = at,
                    dedupTag = "resin:$gameKey",
                    dedupValue = DateUtil.hoyoDayKey(at),
                )
            }
        }

        return AlertPlan(out.sortedBy { it.whenMillis }.take(MAX_PENDING), now)
    }

    /**
     * 방해금지 시간대에 떨어지는 예약을 해제 시각으로 민다.
     *
     * 예약 알림은 **발송 순간에 우리 코드가 돌지 않는다**(iOS 는 OS 가, Android 는 알람이 쏜다).
     * [NotificationChecker] 처럼 발송 직전에 걸러낼 수 없으므로 만들 때 미뤄 두는 수밖에 없다.
     * 새벽에 가득 차는 재화 알림이 대표적이다 — 그대로 두면 자는 사람을 깨운다.
     *
     * 데일리 요약은 사용자가 시각을 직접 고른 것이므로 밀지 않는다(호출부에서 제외).
     */
    private fun shiftOutOfQuiet(settings: AppSettings, at: Long): Long {
        if (!NotificationChecker.isQuietNow(settings, at)) return at
        val end = DateUtil.localTimeOnDay(at, settings.notifyDndEndHour)
        // 23~8 처럼 자정을 넘기는 설정에서 at 이 23:30 이면 같은 날 08:00 은 이미 지났다 → 다음 날.
        return if (end > at) end else end + DAY_MS
    }

    /** D-day → [NotificationChecker] 와 동일한 알림 레벨. 3일보다 멀면 알릴 단계가 아니다. */
    private fun levelOf(dDay: Int): String? = when {
        dDay <= 1 -> "d1"
        dDay <= 3 -> "d3"
        else -> null
    }

    /**
     * 지금 상태로 예약을 통째로 갈아끼우고, 예약이 담지 못하는 건은 즉시 발송한다.
     * 데이터가 바뀔 때마다(배너·전투 캐시 갱신, 구독 편집, 알림 토글) 호출하면 된다
     * — 오래된 예약은 [AlertScheduler] 가 지운다.
     *
     * **suspend 인 이유**: 등록·발송이 끝나야 반환한다. iOS 백그라운드(BGTask)에서 이걸 기다리지
     * 않으면 완료 보고 → 앱 서스펜드가 등록보다 먼저 일어나 전부 유실된다([AlertScheduler.replaceAll]).
     */
    suspend fun reschedule(
        settings: AppSettings,
        repo: GatchaRepository,
        nowMillis: Long = currentTimeMillis(),
    ) {
        // 사전 예약을 안 쓰는 플랫폼(Android)에서는 계산 자체가 낭비고, dedup 선점이 주기 워커의
        // 정상 알림을 오히려 막는다. 그쪽은 [NotificationChecker] 가 전담한다.
        if (!AlertScheduler.schedulesAhead) return

        val plan = runCatching { plan(settings, repo, nowMillis) }.getOrNull() ?: return
        val registered = runCatching { AlertScheduler.replaceAll(plan.scheduled) }.isSuccess
        if (!registered) return

        // 등록에 성공한 예약만 dedup 을 선점한다(실패했는데 찍으면 알림이 영영 안 온다).
        plan.scheduled.forEach {
            if (it.dedupTag.isNotEmpty()) settings.setLastNotified(it.dedupTag, it.dedupValue)
        }
        plan.immediate.forEach { a ->
            runCatching { Notifier.notify(a.id, a.title, a.text) }
            settings.setLastNotified(a.dedupTag, a.dedupValue)
        }
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

    /**
     * 기존 예약을 모두 지우고 [alerts] 로 교체. **등록이 실제로 끝난 뒤에 반환한다.**
     *
     * iOS 의 UNUserNotificationCenter API 는 전부 콜백형이라, 예전엔 등록 요청만 걸어두고 즉시
     * 반환했다. 그런데 BGTask 는 `setTaskCompletedWithSuccess` 직후 앱을 서스펜드하므로,
     * 콜백이 돌기 전에 프로세스가 멈춰 **예약이 등록되지 않은 채 백그라운드 실행이 끝났다**
     * (앱이 살아 있는 포그라운드에서만 성공 → "앱을 열어야 알림이 온다").
     */
    suspend fun replaceAll(alerts: List<ScheduledAlert>)
}
