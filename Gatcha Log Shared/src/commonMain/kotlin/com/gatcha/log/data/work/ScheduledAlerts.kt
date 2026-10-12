package com.gatcha.log.data.work

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.NotificationCatalog
import com.gatcha.log.data.NotificationRoutes
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolandEntry
import com.gatcha.log.data.Josa
import com.gatcha.log.data.Notifier
import com.gatcha.log.data.api.HoyolandApi
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
 * 건너뛰므로 알림이 **한 건도 안 갔다** — 마감 하루 전인데 조용한 상태.
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

        // ① 픽업 마감 — 게임별로 가장 임박한 종료 기준 D-3/D-1.
        if (settings.notifyPickup) {
            repo.loadActiveBanners().filter { !it.isEndUnknown && it.endMillis > nowMillis }
                .groupBy { it.game }
                .forEach { (gameName, list) ->
                    val end = list.minOf { it.endMillis }
                    val game = GameData.byNameOrNull(gameName)
                    val shortName = game?.shortName ?: gameName
                    val names = list.filter { it.endMillis == end }.joinToString(", ") { it.name }
                    // 이 마감을 누가 알렸는지 적는 표시. 값에 **마감 시각**이 들어간다(27.52.0) — 예전 값은 d1 · d3 뿐이라
                    // 한 번 찍히면 다음 회차의 같은 단계가 막혔다.
                    val tag = "pickup:$gameName"
                    var scheduledAny = false
                    LEAD_DAYS.forEach { lead ->
                        val at = shiftOutOfQuiet(settings, DateUtil.localTimeOnDay(end - lead * DAY_MS, ALERT_HOUR))
                        if (at > nowMillis) {
                            scheduledAny = true
                            out += ScheduledAlert(
                                key = "pickup:${game?.key ?: gameName}:$lead",
                                title = "$shortName 픽업이 곧 끝나요",
                                text = "$names · D-$lead 남았어요. 뽑을 거면 지금이에요",
                                whenMillis = at,
                                link = NotificationRoutes.GAME_SCHEDULE,
                                // 예약이 이 마감을 맡았다고 적어 둔다 — 안 적으면 마지막 예약이 울린 뒤 다시 계획할 때
                                // 「예약할 시각이 다 지났다」고 보고 같은 건을 한 번 더 보냈다.
                                dedupTag = tag,
                                dedupValue = end.toString(),
                            )
                        }
                    }
                    // 알림 시각이 전부 지났는데 마감은 남았다 → 예약이 못 담는다. 지금(방해금지면 해제 시각에) 보낸다.
                    if (!scheduledAny) {
                        val sendAt = lateSendAt(
                            settings.lastNotified(tag), end, nowMillis, shiftOutOfQuiet(settings, nowMillis),
                        ) ?: return@forEach
                        val minD = list.minOf { it.dDay(sendAt) }
                        if (levelOf(minD) == null) return@forEach
                        val urgent = list.filter { it.dDay(sendAt) <= 3 }.joinToString(", ") { it.name }
                        val whenLabel = if (minD <= 1) "오늘·내일까지예요" else "D-$minD 남았어요"
                        val title = "$shortName 픽업이 곧 끝나요"
                        val text = "$urgent · $whenLabel. 뽑을 거면 지금이에요"
                        if (sendAt <= nowMillis) now += ImmediateAlert(
                            id = Notifier.ID_PICKUP_BASE + (game?.ordinal ?: 0),
                            title = title, text = text, dedupTag = tag, dedupValue = end.toString(),
                        ) else out += ScheduledAlert(
                            key = "pickup:${game?.key ?: gameName}:late", title = title, text = text,
                            whenMillis = sendAt, link = NotificationRoutes.GAME_SCHEDULE,
                            dedupTag = tag, dedupValue = lateValue(end, sendAt),
                        )
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
                    // 표시 이름은 [NotificationChecker] ⑥ 과 같다(shortName:모드). 값은 픽업과 같이 마감 시각이다.
                    val tag = "combat:$shortName:${c.name}"
                    var scheduledAny = false
                    LEAD_DAYS.forEach { lead ->
                        val at = shiftOutOfQuiet(settings, DateUtil.localTimeOnDay(c.endMillis - lead * DAY_MS, ALERT_HOUR))
                        if (at > nowMillis) {
                            scheduledAny = true
                            out += ScheduledAlert(
                                key = "combat:${game?.key ?: c.game}:${c.name}:$lead",
                                title = "$shortName ${Josa.subj(c.name)} 곧 끝나요",
                                text = "${c.stars}/${c.maxStars} 에서 멈춰 있어요. D-$lead 에 끝나면 남은 보상은 사라져요",
                                whenMillis = at,
                                link = NotificationRoutes.GAME_COMBAT,
                                dedupTag = tag,
                                dedupValue = c.endMillis.toString(),
                            )
                        }
                    }
                    if (!scheduledAny) {
                        val sendAt = lateSendAt(
                            settings.lastNotified(tag), c.endMillis, nowMillis, shiftOutOfQuiet(settings, nowMillis),
                        ) ?: return@forEach
                        val d = c.dDay(sendAt) ?: return@forEach
                        if (levelOf(d) == null) return@forEach
                        val whenLabel = when {
                            d <= 0 -> "오늘"
                            d == 1 -> "내일"
                            else -> "D-$d 에"
                        }
                        val title = "$shortName ${Josa.subj(c.name)} 곧 끝나요"
                        val text = "${c.stars}/${c.maxStars} 에서 멈춰 있어요. $whenLabel 끝나면 남은 보상은 사라져요"
                        if (sendAt <= nowMillis) now += ImmediateAlert(
                            id = Notifier.ID_COMBAT_BASE + (game?.ordinal ?: 0),
                            title = title, text = text, dedupTag = tag, dedupValue = c.endMillis.toString(),
                        ) else out += ScheduledAlert(
                            key = "combat:${game?.key ?: c.game}:${c.name}:late", title = title, text = text,
                            whenMillis = sendAt, link = NotificationRoutes.GAME_COMBAT,
                            dedupTag = tag, dedupValue = lateValue(c.endMillis, sendAt),
                        )
                    }
                }
        }

        // ③ 정기결제 갱신 예약은 여기 있었다 — 기능째 걷어냈다(2026-09-09).

        // ④ 행동력(레진·개척력·배터리) 가득참 — 상류가 '남은 초'를 주므로 가득 차는 시각을 정확히 안다.
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
                    title = "${game?.shortName ?: n.game} ${Josa.subj(n.resinLabel)} 가득 찼어요",
                    text = "${n.maxResin}/${n.maxResin} 이에요. 지금부터는 더 쌓이지 않아요",
                    whenMillis = at,
                    link = NotificationRoutes.GAME_NOTES,
                    dedupTag = "resin:$gameKey",
                    dedupValue = DateUtil.hoyoDayKey(at),
                )
            }
        }

        // ⑤ 호요랜드 — 예매 오픈과 개막. **날짜가 못 박힌 유일한 오프라인 일정**이라
        //    예약과 가장 잘 맞는다(원본도 네트워크 없이 읽는 번들/캐시값이다).
        //    즉시 발송 갈래를 두지 않는 이유: 놓쳐서 아쉬운 건 '예매 시작'과 '개막'이라는 시점 자체지,
        //    지나간 뒤 뒤늦게 알리는 건 알림이 아니라 소음이다.
        // TODO(호요랜드 종료): 행사가 끝나면 예약하지 않는다 — 다음 업데이트에서 이 갈래째 제거 예정.
        if (settings.notifyHoyoland && NotificationCatalog.hoyolandAlertsActive) {
            val event = HoyolandApi.current

            // 예매 오픈 — 하루 전 아침과 **오픈 1시간 전**. 예매는 분 단위 경쟁이라 당일 알림이 본론이다.
            val openAt = event.ticketOpenMillis()
            if (openAt > nowMillis) {
                val dayBefore = shiftOutOfQuiet(settings, DateUtil.localTimeOnDay(openAt - DAY_MS, ALERT_HOUR))
                if (dayBefore > nowMillis) {
                    out += ScheduledAlert(
                        key = "hoyoland:ticket:d1",
                        title = "${event.edition} 예매가 내일이에요",
                        text = "${event.ticket.openLabel} 에 열려요. 미리 로그인해 두세요",
                        whenMillis = dayBefore,
                    )
                }
                // 이 한 건은 방해금지로 밀지 않는다 — 밀면 예매가 이미 시작된 뒤에 도착한다.
                val hourBefore = openAt - 3_600_000L
                if (hourBefore > nowMillis) {
                    out += ScheduledAlert(
                        key = "hoyoland:ticket:h1",
                        title = "${event.edition} 예매 1시간 남았어요",
                        text = "${event.ticket.openLabel}${if (event.ticket.vendor.isBlank()) "" else " · ${event.ticket.vendor}"}",
                        whenMillis = hourBefore,
                        // 누르면 게임 정보 탭의 호요랜드 자리로 간다 — 예매 버튼이 거기 있다. 예전엔 예매 주소를 그대로
                        // 실었는데 앱은 news · update 두 종류만 처리해서 눌러도 앱만 열렸다.
                        link = HOYOLAND_LINK,
                    )
                }
            }

            // 개막 — D-7/D-1 아침과 개막 당일 아침.
            listOf(7, 1, 0).forEach { lead ->
                val at = shiftOutOfQuiet(settings, event.startAtMillis(ALERT_HOUR) - lead * DAY_MS)
                if (event.startAtMillis(ALERT_HOUR) > 0L && at > nowMillis) {
                    out += ScheduledAlert(
                        key = "hoyoland:open:$lead",
                        title = if (lead == 0) "${event.edition} 오늘 열려요" else "${event.edition} ${lead}일 남았어요",
                        text = "${event.periodLabel} · ${event.venueShort}",
                        whenMillis = at,
                    )
                }
            }

            // 내 입장권 — **고른 날에만** 간다(「내 입장권」에서 조를 정한 날). 나흘 내내 여는
            // 행사라도 가는 날은 사람마다 다르고, 안 고른 사람에게는 한 건도 만들지 않는다.
            //
            // 위 개막 알림과 겹치지 않는다: 저건 "행사가 열린다", 이건 "내가 몇 시에 들어간다"
            // 다. 개막일 하루만 가는 사람에게는 같은 날 아침에 둘이 오지만, 조·입장 시각은
            // 개막 알림이 답하지 않는 값이라 묶어서 한 건으로 줄이지 않았다.
            val entry = HoyolandEntry.parse(settings.hoyolandEntryRaw)
            event.dayYmds.filter { entry.isGoing(it) }.forEach { ymd ->
                val group = entry.groupOn(ymd)
                val at = event.entryAtMillis(ymd, group)
                // 조 편성이 바뀌어 시각을 잃으면 예약하지 않는다 — 시각 없는 입장 알림은
                // "오늘 갑니다" 밖에 말하지 못하는데 그건 개막 알림이 이미 한다.
                if (at <= 0L) return@forEach
                val timeLabel = event.entryTimeOf(group)

                // ── 가는 날 아침 — 그날 채비. 무대 편성이 있으면 편수와 첫 무대까지 싣는다
                //    (현장에서 첫 무대를 보려면 입장 시각을 그쪽에 맞춰 잡아야 한다).
                //    입장이 아침 알림 시각보다 이르면(이른 조) 건너뛴다 — 들어간 뒤 도착한다.
                val morning = shiftOutOfQuiet(settings, DateUtil.localTimeOnDay(at, ALERT_HOUR))
                if (morning > nowMillis && morning < at) {
                    val slots = event.slotsFor(ymd)
                    val first = slots.firstOrNull()?.time.orEmpty()
                    out += ScheduledAlert(
                        key = "hoyoland:entry:$ymd:day",
                        title = "오늘 ${group}조 $timeLabel 입장이에요",
                        text = if (slots.isEmpty()) "${event.venueShort} · ${event.edition}"
                               else "무대 ${slots.size}편" +
                                   (if (first.isBlank()) "" else " · 첫 무대 $first") +
                                   " · ${event.venueShort}",
                        whenMillis = morning,
                    )
                }

                // ── 입장 1시간 전. 예매 오픈과 같은 이유로 **방해금지로 밀지 않는다** —
                //    밀면 이미 들어간 뒤에 도착한다.
                val hourBefore = at - 3_600_000L
                if (hourBefore > nowMillis) {
                    out += ScheduledAlert(
                        key = "hoyoland:entry:$ymd:h1",
                        title = "${group}조 입장 1시간 전이에요",
                        text = "$timeLabel 입장 · ${event.venueShort}",
                        whenMillis = hourBefore,
                    )
                }
            }
        }

        // ⑦ 출석 리마인더 — **베이징 18시**(출석 초기화 6시간 전). 두 플랫폼이 같은 예약을 쓴다(27.52.0).
        //    예전엔 iOS 만 예약형이었고 Android 는 주기 작업이 저녁 시간대에 깨어야만 왔다(안 깨면 그날은 없음).
        //    오늘 남은 출석이 있고 18시 전이면 오늘, 아니면 내일 것을 건다. 출석 · 토글 · 연동 · 계정이 바뀌면
        //    [reschedule] 이 다시 돌아 고쳐 건다. Android 는 울리는 순간에도 한 번 더 확인한다([attendanceStillPending]).
        val cfg = repo.loadHoyolab()
        if (settings.notifyAttendance && cfg.isLinked) {
            val today18 = hoyoDayStart(nowMillis) + ATTEND_REMIND_HOUR * 3_600_000L
            val at = shiftOutOfQuiet(settings, if (attendanceStillPending(repo, nowMillis) && nowMillis < today18) today18 else today18 + DAY_MS)
            out += ScheduledAlert(
                key = ATTEND_KEY,
                title = "오늘 출석 아직이에요",
                text = "탭하면 바로 출석할 수 있어요. 1분이면 끝나요",
                whenMillis = at,
                link = NotificationRoutes.TAB_GAME_INFO,
                dedupTag = NotificationChecker.ATTEND_SCHEDULED_TAG,
                dedupValue = DateUtil.hoyoDayKey(at),
            )
        }

        // ⑥ 방해금지 시간에 끝난 자동 출석의 성공 알림 — 해제 시각에 한 번([deferCheckInSuccess]).
        deferredCheckIn(settings.lastNotified(CHECKIN_DEFERRED_TAG))?.let { (at, title, text) ->
            if (at > nowMillis) out += ScheduledAlert(
                key = "auto_checkin", title = title, text = text, whenMillis = at, link = NotificationRoutes.TAB_GAME_INFO,
            )
        }

        return AlertPlan(out.sortedBy { it.whenMillis }.take(MAX_PENDING), now)
    }

    /** 출석 리마인더 예약의 키 — Android 수신부가 울리기 직전에 이 키를 보고 한 번 더 확인한다. */
    const val ATTEND_KEY = "attend"
    private const val ATTEND_REMIND_HOUR = 18
    private const val HOYO_SHIFT_MS = 8L * 3_600_000L

    /** 출석 기준일(베이징 자정)의 시작 시각. */
    private fun hoyoDayStart(millis: Long): Long = (millis + HOYO_SHIFT_MS) / DAY_MS * DAY_MS - HOYO_SHIFT_MS

    /** 오늘(베이징 기준) 아직 출석하지 않은 게임이 있는가 — 세는 게임은 화면 · 자동 출석과 같은 목록이다. */
    fun attendanceStillPending(repo: GatchaRepository, nowMillis: Long = currentTimeMillis()): Boolean {
        val done = repo.loadAttendance()[DateUtil.hoyoDayKey(nowMillis)].orEmpty()
        return GameData.trackedAttendanceGames(repo.loadHoyolab(), repo.loadMyGames()).any { it.key !in done }
    }

    /** 호요랜드 알림의 딥링크 — [com.gatcha.log.data.SpendingViewModel.handleNotificationLink] 가 받는다. */
    const val HOYOLAND_LINK = "hoyoland"

    private const val CHECKIN_DEFERRED_TAG = "auto_checkin_deferred"
    private const val FIELD_SEP = '\u001F'

    private fun deferredCheckIn(raw: String): Triple<Long, String, String>? {
        val parts = raw.split(FIELD_SEP)
        val at = parts.getOrNull(0)?.toLongOrNull() ?: return null
        return Triple(at, parts.getOrNull(1).orEmpty(), parts.getOrNull(2).orEmpty()).takeIf { it.second.isNotEmpty() }
    }

    /**
     * 방해금지 시간에 끝난 자동 출석의 성공 알림을 **해제 시각**으로 넘긴다([AutoCheckInRunner] 가 부른다).
     * 예약은 [plan] 이 이 표시를 읽어 만든다 — 그래서 그사이 다른 일로 예약을 통째로 갈아도 사라지지 않는다.
     */
    suspend fun deferCheckInSuccess(
        settings: AppSettings, repo: GatchaRepository, title: String, text: String, nowMillis: Long,
    ) {
        val at = shiftOutOfQuiet(settings, nowMillis)
        settings.setLastNotified(CHECKIN_DEFERRED_TAG, "$at$FIELD_SEP$title$FIELD_SEP$text")
        runCatching { reschedule(settings, repo, nowMillis) }
    }

    /**
     * 예약 시각이 다 지난 뒤에야 알게 된 마감을 **언제 보낼지.** null 이면 보내지 않는다.
     *
     * [last] 는 그 마감 표시에 적힌 값이다.
     * - `"<마감>"` — 예약이 맡았거나 이미 보냈다.
     * - `"<마감>@<시각>"` — 방해금지라 그 시각으로 미뤄 예약해 뒀다([lateValue]). 그 시각이 지났으면 울린 것이다.
     *
     * @param quietEnd 지금이 방해금지면 해제 시각, 아니면 [nowMillis] 그대로([shiftOutOfQuiet]).
     *   해제가 마감보다 늦으면 보내지 않는다 — 끝난 뒤에 오는 마감 알림은 소음이다.
     */
    internal fun lateSendAt(last: String, deadline: Long, nowMillis: Long, quietEnd: Long): Long? {
        if (last == deadline.toString()) return null
        val deferredTo = last.substringAfter("$deadline@", "").toLongOrNull()
        if (deferredTo != null && deferredTo <= nowMillis) return null
        return quietEnd.takeIf { it < deadline }
    }

    internal fun lateValue(deadline: Long, sendAt: Long): String = "$deadline@$sendAt"

    /**
     * 방해금지 시간대에 떨어지는 예약을 해제 시각으로 민다.
     *
     * 예약 알림은 **발송 순간에 우리 코드가 돌지 않는다**(iOS 는 OS 가, Android 는 알람이 쏜다).
     * [NotificationChecker] 처럼 발송 직전에 걸러낼 수 없으므로 만들 때 미뤄 두는 수밖에 없다.
     * 새벽에 가득 차는 재화 알림이 대표적이다 — 그대로 두면 자는 사람을 깨운다.
     *
     * 데일리 요약은 사용자가 시각을 직접 고른 것이므로 밀지 않는다(호출부에서 제외).
     */
    internal fun shiftOutOfQuiet(settings: AppSettings, at: Long): Long {
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
        // 예전 iOS 전용 리마인더 예약을 걷는다 — 이제 아래 계획 안에 들어 있다([plan] ⑦). Android 는 할 일이 없다.
        runCatching { AlertScheduler.refreshAttendanceReminder(settings, repo) }

        // 오늘 몫으로 걸어 둔 출석 리마인더가 있었고 18시가 지났다 = 이미 울렸다. 주기 점검([NotificationChecker] ②)이
        // 같은 날 또 보내지 않게 넘겨 적는다 — 아래에서 표시가 내일 날짜로 바뀌기 전에.
        val today = DateUtil.hoyoDayKey(nowMillis)
        if (settings.lastNotified(NotificationChecker.ATTEND_SCHEDULED_TAG) == today &&
            DateUtil.hoyoHour(nowMillis) >= ATTEND_REMIND_HOUR
        ) settings.setLastNotified("attend", today)

        val plan = runCatching { plan(settings, repo, nowMillis) }.getOrNull() ?: return
        val registered = runCatching { AlertScheduler.replaceAll(plan.scheduled) }.isSuccess
        if (!registered) return

        // 등록에 성공한 예약만 dedup 을 선점한다(실패했는데 찍으면 알림이 영영 안 온다).
        plan.scheduled.forEach {
            if (it.dedupTag.isNotEmpty()) settings.setLastNotified(it.dedupTag, it.dedupValue)
        }
        // 출석 리마인더가 계획에 없으면(토글 끔 · 연동 해제) 표시도 지운다 — 남아 있으면 주기 점검이 「예약이 맡았다」고 본다.
        if (plan.scheduled.none { it.key == ATTEND_KEY }) settings.setLastNotified(NotificationChecker.ATTEND_SCHEDULED_TAG, "")
        // 실제로 게시됐을 때만 적는다 — 권한이 꺼져 못 보낸 것을 '보냄'으로 적으면 권한을 켠 뒤에도 오지 않는다.
        plan.immediate.forEach { a ->
            val posted = runCatching { Notifier.notify(a.id, a.title, a.text) }.getOrDefault(false)
            if (posted) settings.setLastNotified(a.dedupTag, a.dedupValue)
        }
    }
}

/**
 * 플랫폼 알림 예약기.
 * - iOS: UNCalendarNotificationTrigger 로 미리 등록 → 앱 실행과 무관하게 정시 발송.
 * - Android: AlarmManager 비정확 알람(setAndAllowWhileIdle)으로 미리 등록 → 수신부가 깨어나 발송.
 *   WorkManager 주기 작업은 예약을 다시 깔고, 예약이 담지 못하는 알림(새 공지 · 예산 초과)을 맡는다.
 */
expect object AlertScheduler {
    /**
     * 기존 예약을 모두 지우고 [alerts] 로 교체. **등록이 실제로 끝난 뒤에 반환한다.**
     *
     * iOS 의 UNUserNotificationCenter API 는 전부 콜백형이라, 예전엔 등록 요청만 걸어두고 즉시
     * 반환했다. 그런데 BGTask 는 `setTaskCompletedWithSuccess` 직후 앱을 서스펜드하므로,
     * 콜백이 돌기 전에 프로세스가 멈춰 **예약이 등록되지 않은 채 백그라운드 실행이 끝났다**
     * (앱이 살아 있는 포그라운드에서만 성공 → "앱을 열어야 알림이 온다").
     */
    suspend fun replaceAll(alerts: List<ScheduledAlert>)

    /**
     * 27.51 까지의 iOS 전용 출석 리마인더 예약을 걷는다. 출석 리마인더는 이제 [ScheduledAlerts.plan] 의 한 건이라
     * [replaceAll] 이 같이 건다. Android 는 할 일이 없다.
     */
    suspend fun refreshAttendanceReminder(settings: AppSettings, repo: GatchaRepository)
}
