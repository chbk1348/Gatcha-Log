package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 호요랜드의 시각은 **한국 시간**이다(27.52.0) — 행사가 한국에서 열리고 예매 · 입장 · 무대 시각도 한국 시각으로 공지된다.
 * 예전엔 기기 시간대로 읽어, 해외 시간대의 기기에서는 예매 알림과 무대 LIVE 판정이 그 시차만큼 밀렸다.
 */
internal val HoyolandTimeZone = DateUtil.SEOUL

/**
 * 행사 1회차 전체.
 *
 * 날짜는 `yyyy-MM-dd` 문자열로 들고 있다가 파생값에서만 [LocalDate] 로 판다 —
 * 원격 JSON 과 모양을 맞추고, 파싱 실패가 화면 전체를 무너뜨리지 않게 하기 위해서다.
 */
data class HoyolandEvent(
    val edition: String,
    val startYmd: String,
    val endYmd: String,
    val venueName: String,
    /** 홀 표기(예: "7·8홀 · 후면광장"). 확정 전이면 빈 문자열. */
    val venueHall: String,
    val venueAddress: String,
    val mapUrl: String,
    /** 네이버 지도가 안 열릴 때 폴백 — 어느 기기에나 있는 브라우저로 열리는 구글 지도 검색. */
    val mapFallbackUrl: String,
    val officialUrl: String,
    /**
     * 개최 발표일(`yyyy-MM-dd`). 카운트다운 진행 바의 **출발점**이다 —
     * "얼마 남았나"만으로는 얼마나 왔는지를 알 수 없어, 잰 구간의 시작이 필요하다.
     */
    val announceYmd: String,
    val ticket: HoyolandTicket,
    val lineup: List<HoyolandLineup>,
    val programs: List<HoyolandProgram>,
    /** 페이지 하단 안내문 — 지금 무엇이 확정이고 무엇이 남았는지 한 줄로. */
    val notice: String,
    /**
     * 일자별 프로그램. **비어 있는 게 정상인 기간이 있다** — 공식 시간표는 개막 2~3주 전에야
     * 공개된다(2025 기준). 그때까지 화면은 날짜 탭만 세우고 "공개 전"이라고 말한다.
     */
    val days: List<HoyolandDay>,
    /**
     * 지난 행사(최신순). 회차 문서의 것이 아니라 **따로 둔 문서**(`config/hoyolandPast`)의 목록이 얹힌다
     * ([com.gatcha.log.data.api.HoyolandApi.withPast], 2026-10-07) — 어드민이 회차와 무관하게 한 곳에서 관리한다.
     * 그 문서를 못 받았거나 비어 있을 때만 회차 문서의 `past`, 그것도 비면 번들 기본값이다.
     */
    val past: List<HoyolandPastEvent>,
    /**
     * 굿즈 품목. **비어 있는 게 정상인 기간이 있다** — 판매 목록은 시간표만큼 늦게 나온다.
     * 그때까지 화면은 "공개 전"이라고 말한다.
     */
    val goods: List<HoyolandGoods> = emptyList(),
    /** 게임별 부스 체험. 위와 같은 이유로 비어 있을 수 있다. */
    val booths: List<HoyolandBooth> = emptyList(),
    /**
     * 굿즈존 공통 이용 안내(주문·결제·수령·사은품·교환) — 굿즈 목록 맨 위 카드. 비면 카드가 없다.
     *
     * 품목이 아니라 **굿즈존 운영 규칙**이라 굿즈 행에 흩을 수 없다. 줄 규칙은 예매 안내와 같다.
     */
    val goodsGuide: String = "",
    /**
     * 대표 이미지(키 비주얼) — 히어로 맨 위에 깔린다. **비어 있는 게 정상**이고, 그러면 카드는
     * 지금처럼 글자로만 선다. 행사 키아트는 보통 개막 몇 주 전에야 나온다.
     *
     * 굿즈·푸드 사진과 **같은 규칙**이다 — 상대 경로(`event/key-2026.webp`)면 [hoyolandAssetUrl]
     * 이 raw 주소를 붙이고, `http` 로 시작하면 외부 주소로 그대로 쓴다.
     */
    val keyImage: String = "",
    /**
     * 행사명 영문 표기 — 히어로 머리줄이 쓴다. **비어 있으면 [edition] 을 그대로** 쓴다.
     *
     * 한글 행사명을 그대로 걸면 바로 아래 큰 숫자 · 영문 단계 배지와 결이 갈린다. 영문은
     * 대문자로 짧게 서서 머리줄 노릇을 한다(`HOYOLAND 2026`).
     */
    val editionEn: String = "",
    /**
     * 입장 조 편성 — 「내 입장권」이 고르게 할 목록이다. **비어 있으면 그 기능이 통째로 사라진다**
     * (조가 뭔지 모르는 채로 고르게 할 수는 없다).
     *
     * 예매 안내문([HoyolandTicket.note])에 같은 내용이 글로도 있지만 그쪽은 **읽는 자리**고,
     * 이쪽은 고르는 자리다. 조 편성이 바뀌면 둘 다 고쳐야 한다.
     */
    val entryGroups: List<HoyolandEntryGroup> = emptyList(),
    /**
     * 행사장 배치도 — 「둘러보기」의 맵스 칸이 연다. **비어 있으면 그 칸이 사라진다**
     * (배치도는 개막 몇 주 전에야 공개된다).
     */
    val map: HoyolandMap = HoyolandMap(),
    /**
     * 프로그램 섹션의 제목. 비면 「프로그램」([programSectionTitle]).
     *
     * 웰컴 키트가 「입장 특전」 섹션으로 빠진 뒤(10/6) 이 섹션에는 전시존 · 공모 같은 프로그램만 남는다
     * (예전 이름 「응모 · 특전」의 '특전'이 나갔다).
     * 지난 회차 화면은 같은 자리가 그 회차의 기록(게임별 구성 · 무대 · 부대 시설)이라 「행사 구성」이다 —
     * [com.gatcha.log.data.api.HoyolandApi.loadArchive] 가 채운다. 원격 문서에는 없는 값이다(파서가 읽지 않는다).
     */
    val programsTitle: String = "",
    /**
     * 프로그램 줄 앞에 게임 배지를 세울지. 지금 회차는 웰컴 키트가 게임마다 나란히 서서 색으로 내 것을 찾는다.
     * 지난 회차 화면은 줄 제목에 게임 이름이 이미 있어 배지가 같은 말을 두 번 한다 — 끈다.
     */
    val programGameTags: Boolean = true,
    /**
     * 지난 회차 화면에 그리는 값인가 — [com.gatcha.log.data.api.HoyolandApi.loadArchive] 가 세운다.
     * 살 수도 담을 수도 없는 회차라, 장바구니를 전제로 한 문구(「아직 안 담았어요」)를 내지 않는다.
     */
    val archived: Boolean = false,
) {

    /** 프로그램 섹션 제목 — [programsTitle] 이 비면 「프로그램」. */
    val programSectionTitle: String get() = programsTitle.ifBlank { "프로그램" }

    /** 대표 이미지 전체 주소. 없으면 빈 문자열이라 호출부는 `isNotEmpty()` 로 가른다. */
    val keyImageUrl: String get() = hoyolandAssetUrl(keyImage)

    /** 히어로 머리줄에 걸 행사명 — 영문이 있으면 영문, 없으면 한글. */
    val editionLabel: String get() = editionEn.ifBlank { edition }

    /** 배치도를 열 수 있는가 — 그릴 수 있는 구역이 하나라도 있어야 한다. */
    val hasMap: Boolean get() = !map.isEmpty

    /** 맵스 칸 부제 — 구역이 몇 곳인지. */
    fun onsiteMapLine(): String =
        if (map.isEmpty) "배치도 공개 전" else "${map.drawable.size}개 구역"

    /** 「내 입장권」을 열 수 있는가 — 조 편성이 있어야 고를 것이 있다. */
    val hasEntryGroups: Boolean get() = entryGroups.isNotEmpty()

    /** 그 조의 입장 시각. 모르는 조(편성이 바뀐 뒤 남은 옛 값)면 빈 문자열이다. */
    fun entryTimeOf(group: String): String =
        entryGroups.firstOrNull { it.name == group }?.time.orEmpty()

    private val start: LocalDate? get() = runCatching { LocalDate.parse(startYmd) }.getOrNull()
    private val end: LocalDate? get() = runCatching { LocalDate.parse(endYmd) }.getOrNull()

    /** 기간 일수(4 = 4일). 날짜를 못 읽으면 0. */
    val dayCount: Int
        get() {
            val s = start ?: return 0
            val e = end ?: return 0
            return s.daysUntil(e) + 1
        }

    /**
     * "2026.10.2(금) ~ 10.5(월)" — 끝 날짜는 같은 해면 월/일만 쓴다.
     *
     * 연·월·일 숫자는 [LocalDate] 가 아니라 원본 `yyyy-MM-dd` 문자열에서 뽑는다. 요일만 날짜로
     * 계산하면 되는데, 월·일까지 날짜 타입을 거치면 kotlinx-datetime 버전마다 다른
     * `month`/`monthNumber` 접근자에 묶인다 — 표기 하나 때문에 그럴 이유가 없다.
     */
    val periodLabel: String
        get() {
            val s = start ?: return ""
            val e = end ?: return ""
            val sp = startYmd.split("-")
            val ep = endYmd.split("-")
            if (sp.size < 3 || ep.size < 3) return ""
            val head = "${sp[0]}.${sp[1].trimStart('0')}.${sp[2].trimStart('0')}(${s.dayOfWeek.koLabel})"
            val tailDate = "${ep[1].trimStart('0')}.${ep[2].trimStart('0')}(${e.dayOfWeek.koLabel})"
            val tail = if (sp[0] == ep[0]) tailDate else "${ep[0]}.$tailDate"
            return "$head ~ $tail"
        }

    /** 기간 + 일수 — 상세 페이지처럼 폭이 넉넉한 자리에서 쓴다. */
    val periodLongLabel: String
        get() = if (dayCount > 0) "$periodLabel (${dayCount}일)" else periodLabel

    /** "일산 킨텍스 제2전시장 7·8홀 · 후면광장" — 상세 페이지용 전체 표기. */
    val venueFull: String get() = if (venueHall.isBlank()) venueName else "$venueName $venueHall"

    /**
     * 한 줄에 들어가야 하는 자리(진입 카드·홈 카드)용 축약 —
     * 후면광장 같은 부속 장소를 떼고 홀 표기까지만 남긴다.
     */
    val venueShort: String
        get() {
            if (venueHall.isBlank()) return venueName
            val firstHall = venueHall.split(" · ").firstOrNull().orEmpty()
            return if (firstHall.isBlank()) venueName else "$venueName $firstHall"
        }

    // ── 입장권 배너(홈 · 게임정보 탭) 전용 짧은 표기 — 폭이 [venueShort] 보다도 좁다.

    /** "10.2(금) ~ 10.5(월)" — [periodLabel] 에서 연도만 뗀다. */
    val periodNoYearLabel: String
        get() = periodLabel.substringAfter('.', periodLabel)

    /** "10.2 – 10.5" — 입장권 조각(D-day 칸) 아래 한 줄. 요일도 뗀다. */
    val periodDotsLabel: String
        get() {
            val sp = startYmd.split("-")
            val ep = endYmd.split("-")
            if (sp.size < 3 || ep.size < 3) return ""
            fun md(p: List<String>) = "${p[1].trimStart('0')}.${p[2].trimStart('0')}"
            return "${md(sp)} – ${md(ep)}"
        }

    /** 첫 홀 표기에서 괄호 부연을 뗀 값 — "7·8홀(실내) · 후면광장(야외)" → "7·8홀". */
    private val hallCore: String
        get() = venueHall.split(" · ").firstOrNull().orEmpty()
            .replace(Regex("\\(.*?\\)"), "").trim()

    /** 장소명 앞의 지역명("일산")을 뗀 단어들. 두 단어 이하면 그대로 둔다(뗄 지역명이 없다). */
    private val venueCoreWords: List<String>
        get() {
            val words = venueName.split(" ").filter { it.isNotBlank() }
            return if (words.size >= 3) words.drop(1) else words
        }

    /** "킨텍스 제2전시장 7·8홀" — 게임정보 카드의 장소 줄. */
    val venueRowLabel: String
        get() = (venueCoreWords.joinToString(" ") + " " + hallCore).trim()

    /** "킨텍스 7·8홀" — 홈 입장권 배너의 한 줄(기간과 나란히 선다). */
    val venueTicketLabel: String
        get() = ((venueCoreWords.firstOrNull() ?: venueName) + " " + hallCore).trim()

    @OptIn(ExperimentalTime::class)
    private fun today(nowMillis: Long): LocalDate =
        Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(HoyolandTimeZone).date

    /**
     * 지금이 개최 전인지·중인지·끝났는지.
     *
     * 판정 순서가 곧 우선순위다 — 폐막을 먼저 걸러야 기간이 하루인 행사에서 TODAY 와 ENDED 가
     * 겹치지 않는다. 날짜가 비었거나 못 읽으면 [HoyolandPhase.TBA](일정 미정)다 — 예전엔 UPCOMING 으로
     * 봐서 「D-0」 이 섰다.
     */
    fun phase(nowMillis: Long = currentTimeMillis()): HoyolandPhase {
        val s = start ?: return HoyolandPhase.TBA
        val e = end ?: return HoyolandPhase.TBA
        val t = today(nowMillis)
        return when {
            t > e -> HoyolandPhase.ENDED
            t == s -> HoyolandPhase.TODAY
            t > s -> HoyolandPhase.ONGOING
            t.daysUntil(s) == 1 -> HoyolandPhase.TOMORROW
            else -> HoyolandPhase.UPCOMING
        }
    }

    /** 개막 전인가(UPCOMING·TOMORROW). Swift 에서도 쓰라고 함수로 둔다. */
    fun isBeforeEvent(nowMillis: Long = currentTimeMillis()): Boolean = phase(nowMillis).isBeforeEvent

    /** 행사 기간 중인가(TODAY·ONGOING). Swift 에서도 쓰라고 함수로 둔다. */
    fun isEventLive(nowMillis: Long = currentTimeMillis()): Boolean = phase(nowMillis).isEventLive

    /** 개막까지 남은 일수(0 = 오늘 개막). 이미 시작했으면 0. */
    fun daysUntilStart(nowMillis: Long = currentTimeMillis()): Int {
        val s = start ?: return 0
        val d = today(nowMillis).daysUntil(s)
        return if (d < 0) 0 else d
    }

    /** 진행 중일 때 오늘이 몇 일차인지(1 = 첫날). 진행 중이 아니면 0. */
    fun dayOrdinal(nowMillis: Long = currentTimeMillis()): Int {
        if (!phase(nowMillis).isEventLive) return 0
        val s = start ?: return 0
        return s.daysUntil(today(nowMillis)) + 1
    }

    /**
     * 배지에 들어가는 한 마디 — "D-29" / "오늘 개막" / "2일차" / "종료".
     * 진입 카드·홈 카드·일정 탭이 전부 이 값을 쓴다(자리마다 다르게 조립하면 또 갈라진다).
     */
    fun statusLabel(nowMillis: Long = currentTimeMillis()): String = when (phase(nowMillis)) {
        HoyolandPhase.UPCOMING -> "D-${daysUntilStart(nowMillis)}"
        HoyolandPhase.TOMORROW -> "내일 개막"
        HoyolandPhase.TODAY, HoyolandPhase.ONGOING -> "${dayOrdinal(nowMillis)}일차"
        HoyolandPhase.ENDED -> "종료"
        HoyolandPhase.TBA -> "일정 미정"
    }

    /**
     * 행사 기간의 날짜들(`yyyy-MM-dd`, 개막일부터 폐막일까지).
     *
     * [days] 가 아니라 **기간에서 만든다.** 시간표가 아직 없어도 날짜 탭은 서야 하고,
     * 원격 JSON 이 하루치만 채워 보내도 탭이 하나로 줄면 안 된다.
     */
    val dayYmds: List<String>
        get() {
            val s = start ?: return emptyList()
            val n = dayCount
            if (n <= 0) return emptyList()
            return (0 until n).map { s.plus(it, DateTimeUnit.DAY).toString() }
        }

    /** 날짜 탭 라벨 — "10.2(금)". 한 줄로 써야 하는 자리(알림 문구 등)에서 쓴다. */
    fun dayTabLabel(ymd: String): String {
        val date = dayTabDate(ymd)
        val week = dayTabWeekday(ymd)
        return if (week.isBlank()) date else "$date(${week.removeSuffix("요일")})"
    }

    /** 날짜 탭 윗줄 — "10.2". */
    fun dayTabDate(ymd: String): String {
        val p = ymd.split("-")
        if (p.size < 3) return ymd
        return "${p[1].trimStart('0')}.${p[2].trimStart('0')}"
    }

    /**
     * 날짜 탭 아랫줄 — "금요일".
     *
     * "(금)" 처럼 괄호 한 글자로 붙이면 날짜에 딸린 기호처럼 읽힌다. 줄을 나눠 온말로 쓰면
     * "무슨 요일에 갈까"가 날짜와 같은 무게로 읽힌다(주말이 언제인지가 이 화면의 첫 질문이다).
     */
    fun dayTabWeekday(ymd: String): String {
        val d = runCatching { LocalDate.parse(ymd) }.getOrNull() ?: return ""
        return "${d.dayOfWeek.koLabel}요일"
    }

    /** 그날의 프로그램. 없으면 빈 목록. */
    fun slotsFor(ymd: String): List<HoyolandSlot> =
        days.firstOrNull { it.ymd == ymd }?.slots.orEmpty()

    /** 한 칸이라도 공개된 시간표가 있는지 — 없으면 화면이 '공개 전' 안내로 갈린다. */
    val hasTimetable: Boolean get() = days.any { it.slots.isNotEmpty() }

    /**
     * 「둘러보기」에 채울 게 하나라도 있는가 — 시간표 · 굿즈 · 부스 · 푸드 · 맵스.
     * 일정 미정(TBA) 회차는 넷이 다 비어 「공개되면…」 칸만 남으므로, 그땐 섹션째 뺀다.
     */
    val hasOnsiteContent: Boolean
        get() = hasTimetable || visibleGoods.isNotEmpty() || booths.isNotEmpty() ||
            foodPrograms.isNotEmpty() || hasMap

    /**
     * 그날 무대 편성 + **지금 기준 상태**.
     *
     * 오늘이 아닌 날은 전부 [StageState.UPCOMING] — 지나간 날을 흐리게 칠하지 않는다.
     * "어제 걸 왜 보나" 싶지만, 놓친 무대를 나중에 찾아보는 사람이 있고 그때 회색 목록은
     * 읽히지 않는다.
     *
     * 끝나는 시각은 [HoyolandSlot.minutes] 로 잡되, 없으면 **다음 편 시작 전까지**로 본다.
     * 마지막 편이면서 길이도 없으면 [FALLBACK_STAGE_MIN] 을 쓴다 — 무한정 "진행 중"으로
     * 남는 것보다 낫다.
     */
    @OptIn(ExperimentalTime::class)
    fun stageSlots(ymd: String, nowMillis: Long = currentTimeMillis()): List<StageSlot> {
        val slots = slotsFor(ymd)
        if (slots.isEmpty()) return emptyList()
        val nowLocal = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(HoyolandTimeZone)
        val isToday = nowLocal.date.toString() == ymd
        val nowMin = nowLocal.hour * 60 + nowLocal.minute
        val starts = slots.map { minutesOfDay(it.time) }
        return slots.mapIndexed { i, slot ->
            val start = starts[i]
            // 다음 편 = 이 편보다 **늦게 시작하는 것 중 가장 이른 것**. 예전엔 "목록에서 뒤에 적힌 편"을 다음 편으로 봐서,
            // 메인 · 서브 무대가 섞여 시각 순이 아니거나 두 편이 같은 시각이면 끝 시각이 시작보다 앞서 시작 전에 「끝남」이 됐다(27.52.0).
            val nextStart = if (start == null) null else starts.filterNotNull().filter { it > start }.minOrNull()
            val end = when {
                start == null -> null
                slot.minutes > 0 -> start + slot.minutes
                nextStart != null -> nextStart
                else -> start + FALLBACK_STAGE_MIN
            }
            val label = if (start != null && end != null && slot.minutes > 0) {
                "${slot.time} ~ ${hhmm(end)}"
            } else {
                slot.time
            }
            val endMin = end ?: 0
            when {
                !isToday || start == null || end == null ->
                    StageSlot(slot, StageState.UPCOMING, rangeLabel = label, endMin = endMin)
                nowMin >= end -> StageSlot(slot, StageState.DONE, rangeLabel = label, endMin = endMin)
                nowMin >= start -> StageSlot(
                    slot,
                    StageState.LIVE,
                    remainMin = end - nowMin,
                    progress = if (end > start) (nowMin - start).toFloat() / (end - start) else 0f,
                    rangeLabel = label,
                    endMin = endMin,
                )
                else -> StageSlot(slot, StageState.UPCOMING, rangeLabel = label, endMin = endMin)
            }
        }
    }

    /**
     * 무대 배지·띠에 쓸 색(ARGB).
     *
     * ① 앱이 아는 게임이면 `GameData` 의 대표색 — 앱 전체(지출 행·일정 줄)와 같은 색이어야
     *    "이 색은 이 게임"이라는 규칙이 화면마다 어긋나지 않는다.
     * ② 앱 밖 IP(붕괴3rd·미해결사건부)는 참가 목록의 `colorArgb`.
     * ③ 둘 다 없으면 0 — 화면이 회색('전 IP')으로 떨어뜨린다.
     */
    fun stageColor(game: String): Long {
        if (game.isBlank()) return 0L
        GameData.byNameOrNull(game)?.let { return it.color }
        return lineup.firstOrNull { it.game == game }?.colorArgb ?: 0L
    }

    /**
     * 배지·탭 글자 — **짧은 쪽부터** 고른다. 둘 다 폭이 좁은 자리라(배지 한 칸, 탭 한 칸)
     * "붕괴: 스타레일" 이 그대로 들어가면 잘린다.
     *
     * ① `GameData` 약칭("스타레일") ② 참가 목록의 `abbr`("HI3") ③ 게임 이름 그대로.
     */
    fun stageLabel(game: String): String {
        if (game.isBlank()) return "전 IP"
        GameData.byNameOrNull(game)?.let { return it.shortName }
        val item = lineup.firstOrNull { it.game == game } ?: return game
        return item.abbr.ifBlank { item.game }
    }

    /**
     * 라이브 카드가 쓰는 **온이름** — "붕괴: 스타레일". 카드는 한 장에 하나만 뜨고 폭도 넉넉해서
     * 줄여 쓸 이유가 없다(목록·탭은 자리가 좁아 [stageLabel]·[stageAbbr] 를 쓴다).
     */
    fun stageFullName(game: String): String {
        if (game.isBlank()) return "전 IP"
        return GameData.byNameOrNull(game)?.displayName ?: game
    }

    /**
     * 시간표 **진입 카드 한 줄** — "지금 진행 중 · 다음 15:30" / "오늘 3편" / "무대 편성 공개 전".
     *
     * 페이지를 열지 않고도 지금 볼 값이 있는지 알아야 한다. 카드에 "일자별 시간표" 라고만
     * 적혀 있으면 들어가 보기 전까지 편성이 공개됐는지도 모른다.
     */
    fun stageEntryLine(nowMillis: Long = currentTimeMillis()): String {
        if (!hasTimetable) return "무대 편성 공개 전"
        val today = todayYmd(nowMillis)
        val list = if (today != null) stageSlots(today, nowMillis) else emptyList()
        if (list.isEmpty()) {
            val total = days.sumOf { it.slots.size }
            return if (total > 0) "무대 ${total}편 · 날짜별로 보기" else "무대 편성 공개 전"
        }
        val live = list.firstOrNull { it.state == StageState.LIVE }
        val next = list.firstOrNull { it.state == StageState.UPCOMING }
        return when {
            live != null && next != null -> "지금 진행 중 · 다음 ${next.slot.time}"
            live != null -> "지금 진행 중 · 오늘 마지막"
            next != null -> "다음 ${next.slot.time} · 오늘 ${list.size}편"
            else -> "오늘 편성 종료 · 날짜별로 보기"
        }
    }

    /**
     * 내 입장권에서 **다음에 가는 날**(오늘 포함). 가는 날이 없거나 다 지났으면 빈 문자열.
     *
     * 히어로가 묻는 말이 단계마다 다르다 — 개막 전에는 "첫날 몇 시에 들어가나", 행사 중에는
     * "오늘 내가 가나"다. 둘 다 답이 이 값 하나라 한 자리에 모은다(오늘이 가는 날이면 오늘이
     * 먼저 잡힌다 — `>=` 비교라 오늘이 걸러지지 않는다).
     */
    fun nextEntryYmd(entry: HoyolandEntry, nowMillis: Long = currentTimeMillis()): String {
        if (!hasEntryGroups) return ""
        val t = today(nowMillis).toString()
        val inRange = dayYmds.toSet()
        return entry.goingYmds.firstOrNull { it in inRange && it >= t }.orEmpty()
    }

    /**
     * 히어로에 걸 한 줄 — "10.2(금) A조 · 10:00". 그날 안 가면 빈 문자열.
     *
     * 시각은 config 에서 온다. 조 편성이 바뀌어 시각을 못 찾으면 **조까지만** 적는다 —
     * 모르는 값을 "미정" 같은 말로 채우면 그게 확정 정보처럼 읽힌다.
     */
    fun entryLine(entry: HoyolandEntry, ymd: String): String {
        val group = entry.groupOn(ymd)
        if (group.isBlank()) return ""
        val time = entryTimeOf(group)
        val head = "${dayTabLabel(ymd)} ${group}조"
        return if (time.isBlank()) head else "$head · $time"
    }

    /**
     * 고른 날 **전부**의 줄 목록 — 히어로 `MY ENTRY` 가 통째로 건다.
     *
     * 한 줄(다음에 가는 날)만 걸었더니, 나흘 중 이틀을 고른 사람이 나머지 하루를 확인하려면
     * 시트를 다시 열어야 했다. 내 입장권은 **나흘을 한눈에 보는 값**이라 고른 만큼 다 건다.
     * 안 가는 날은 줄이 없으므로 하루만 가면 한 줄이다.
     */
    fun entryLines(entry: HoyolandEntry): List<String> {
        // 조 편성을 내리면(어드민에서 `entryGroups` 를 비우면) 고르는 시트도 같이 사라진다 —
        // 그때 줄만 남겨 두면 **지울 방법이 없는 값**이 히어로에 박힌다.
        if (!hasEntryGroups) return emptyList()
        // 기간이 바뀌면 이미 고른 날이 행사 밖으로 밀려난다. 시트는 기간 안 날짜만 보여 주므로
        // 그 줄도 고칠 수가 없다 — 화면에서는 거르고, 저장된 값은 건드리지 않는다(기간이
        // 되돌아오면 그대로 되살아난다).
        val inRange = dayYmds.toSet()
        return entry.goingYmds.filter { it in inRange }
            .map { entryLine(entry, it) }
            .filter { it.isNotEmpty() }
    }

    /** 내 입장권의 가는 날 수 — **이 행사 기간 안 날짜만** 센다(헤더 「n일」 · 시트 「4일 중 n일」). */
    fun entryDayCount(entry: HoyolandEntry): Int = dayYmds.count { entry.isGoing(it) }

    /**
     * 회차 식별자 — 장바구니 · 입장권이 **어느 행사 것인지** 적어 두는 값.
     * **개최 연도**다 — 개막일에서 읽고, 날짜가 아직 없으면 행사명 속 연도, 그것도 없으면 행사명.
     *
     * 예전엔 개막일 전체("2026-10-01")였다. 개막일을 하루 정정하거나 일정 미정(행사명)에서 날짜가 확정되면
     * 키가 바뀌어 담아 둔 장바구니가 비워졌다(27.52.0). 예전 형식으로 저장된 값은 [sameEdition] 이 받아 준다.
     */
    val editionKey: String get() = editionKeyOf(startYmd.ifBlank { edition })

    /** 저장된 회차 값이 이 회차를 가리키는가 — 예전 형식(개막일 · 행사명)도 연도로 읽어 견준다. */
    fun sameEdition(savedEdition: String): Boolean = editionKeyOf(savedEdition) == editionKey

    /**
     * 일정 미정 안내 — 직전 회차가 마무리됐으니 다음 행사를 기대해 달라는 말.
     * 상세 히어로와 게임정보 탭 한 줄 카드가 같은 문장을 쓴다.
     * 직전 회차는 지난 행사 맨 앞에서 읽는다. 지난 행사가 없으면 빈 문자열(화면이 줄째 뺀다).
     */
    val nextEditionNotice: String
        get() = past.firstOrNull()?.let { "${it.title} 행사가 마무리되었어요.\n다음 행사를 기대해 주세요." }.orEmpty()

    /**
     * 화면 **상단**에 서는 한 줄 — 어드민의 공지 문구([notice])가 있으면 그것, 없으면 일정 미정일 때만
     * [nextEditionNotice]. 둘 다 없으면 빈 문자열(화면이 줄째 뺀다).
     *
     * 예전엔 공지 문구가 페이지 맨 아래(지난 행사 끝)에 붙고 상단에는 자동 문구가 따로 섰다. 일정 미정에
     * 공지를 적으면 비슷한 말이 위아래로 두 번 나왔다(2026-10-06 지적). 공지는 상단 한 곳에만 세우고,
     * 적어 둔 공지가 자동 문구를 대신한다. 상세 히어로와 게임정보 탭 한 줄이 같은 값을 쓴다.
     */
    fun topNotice(nowMillis: Long = currentTimeMillis()): String =
        notice.trim().ifEmpty { if (phase(nowMillis) == HoyolandPhase.TBA) nextEditionNotice else "" }

    /** 저장된 장바구니를 이 회차로 읽는다 — 다른 회차 것이면 비운다(작년 굿즈 이름이 남지 않게). */
    fun cartForEdition(cart: HoyolandCart, savedEdition: String): HoyolandCart =
        if (sameEdition(savedEdition)) cart else HoyolandCart()

    /** 저장된 입장권을 이 회차로 읽는다 — 다른 회차 것이면 **기간 안 날짜만** 남긴다. */
    fun entryForEdition(entry: HoyolandEntry, savedEdition: String): HoyolandEntry {
        if (sameEdition(savedEdition)) return entry
        val inRange = dayYmds.toSet()
        return HoyolandEntry(entry.groups.filterKeys { it in inRange })
    }

    /**
     * 「응모 · 특전」 마감 안내가 이미 지났는가 — 지났으면 배지를 회색으로 내린다.
     *
     * 안내는 "모집 9.13(일) 23:59 마감 · 결과 9.15(화) 발표" 같은 자유 문장이라, 안에 든 `월.일` 중
     * **가장 늦은 날**이 오늘보다 앞이면 지난 것으로 본다(연도는 개막 연도). 날짜를 못 찾으면
     * 행사가 끝났는지로 가른다.
     * ponytail: 개막 연도 고정 — 연말 마감 · 연초 개막처럼 해를 넘기는 안내는 틀린다.
     */
    fun isDeadlinePast(deadline: String, nowMillis: Long = currentTimeMillis()): Boolean {
        val year = start?.year ?: return false
        val last = MonthDayRegex.findAll(deadline).mapNotNull { m ->
            runCatching { LocalDate(year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
        }.maxOrNull() ?: return phase(nowMillis) == HoyolandPhase.ENDED
        return last < today(nowMillis)
    }

    // ── 「현장에서」 네 칸의 부제 ─────────────────────────────────────────────
    //
    // 한 곳에 모으는 이유가 둘이다. 하나는 **양 플랫폼이 같은 말을 해야** 해서고, 다른 하나는
    // 이 줄들이 "들어가기 전에 볼 값이 있는지" 를 답하는 자리라 규칙이 한 벌이어야 해서다.

    /** 시간표 칸 — 행사 중이면 **지금 몇 편이 도는지**, 아니면 며칠 몇 편인지. */
    fun onsiteStageLine(nowMillis: Long = currentTimeMillis()): String {
        if (!hasTimetable) return "무대 편성 공개 전"
        val today = todayYmd(nowMillis)
        if (today != null) {
            val live = stageSlots(today, nowMillis).count { it.state == StageState.LIVE }
            if (live > 0) return "지금 ${live}편 진행 중"
            val next = nextStageSlot(nowMillis)
            if (next != null) return "다음 ${next.slot.time} · 오늘 ${slotsFor(today).size}편"
        }
        val total = days.sumOf { it.slots.size }
        return if (dayCount > 0) "${dayCount}일 · ${total}편" else "${total}편"
    }

    /** 지금 무대가 돌고 있는가 — 시간표 칸을 빨갛게 세울지 가른다. */
    fun isStageLiveNow(nowMillis: Long = currentTimeMillis()): Boolean =
        liveStageSlot(nowMillis) != null

    /**
     * 굿즈 칸 — **담은 게 있으면 담은 것**이 답이다.
     *
     * 목록에 몇 종이 있는지는 한 번 보면 끝이지만, 담은 금액은 행사가 다가올수록 계속 바뀐다.
     * 이 앱이 지출을 다루는 앱이라 "얼마 들고 가야 하나" 가 굿즈 칸이 답할 질문이다.
     */
    fun onsiteGoodsLine(cart: HoyolandCart): String {
        // 목록 · 가격대 · 장바구니와 같은 [visibleGoods] 로 센다 — 앱이 다루지 않는 IP 의 굿즈까지 세면
        // 칸은 "2종" 인데 들어가면 1종만 보였다(27.52.0).
        val shown = visibleGoods
        if (shown.isEmpty()) return "판매 목록 공개 전"
        val picked = cartLines(cart)
        // 지난 회차는 담을 수 없다 — 종수만 말한다.
        if (picked.isEmpty()) return if (archived) "${shown.size}종" else "${shown.size}종 · 아직 안 담았어요"
        val total = cartTotal(cart)
        val kinds = "담은 ${picked.size}종"
        return if (total > 0) "$kinds · ${wonLabel(total)}" else kinds
    }

    /** 부스 칸. */
    fun onsiteBoothLine(): String =
        if (booths.isEmpty()) "부스 정보 공개 전" else "${booths.size}곳"

    /** 푸드 칸 — 몇 곳에 메뉴가 몇 종인지. 가격대는 하위 페이지가 말한다. */
    fun onsiteFoodLine(): String {
        val list = foodPrograms
        if (list.isEmpty()) return "푸드존 정보 공개 전"
        val menus = list.sumOf { p ->
            p.desc.split("\n").count { it.startsWith("· ") }
        }
        return if (menus > 0) "${list.size}곳 · ${menus}종" else "${list.size}곳"
    }

    /**
     * 지금 무대(LIVE). 없으면 null — 행사 전 · 쉬는 시간 · 편성 미공개.
     *
     * 히어로가 행사 기간에 카운트다운 대신 **이 무대를 통째로** 건다. [stageEntryLine] 이
     * 같은 값을 한 줄로 줄여 쓰는데, 히어로는 제목 · 시각 · 남은 시간을 따로 놓아야 해서
     * 슬롯 자체가 필요하다.
     */
    fun liveStageSlot(nowMillis: Long = currentTimeMillis()): StageSlot? {
        val today = todayYmd(nowMillis) ?: return null
        return stageSlots(today, nowMillis).firstOrNull { it.state == StageState.LIVE }
    }

    /** 오늘 다음 무대. 없으면 null — 오늘 편성이 끝났다는 뜻이다. */
    fun nextStageSlot(nowMillis: Long = currentTimeMillis()): StageSlot? {
        val today = todayYmd(nowMillis) ?: return null
        return stageSlots(today, nowMillis).firstOrNull { it.state == StageState.UPCOMING }
    }

    /** 지금 무대에 올라 있는 게임. 없으면 빈 문자열(행사 전·쉬는 시간·편성 미공개). */
    fun liveStageGame(nowMillis: Long = currentTimeMillis()): String {
        val today = todayYmd(nowMillis) ?: return ""
        return stageSlots(today, nowMillis).firstOrNull { it.state == StageState.LIVE }
            ?.slot?.game.orEmpty()
    }

    /**
     * 라인업 목록에 걸 **오늘 그 게임의 무대 상태** — "지금 메인 무대" / "15:30 다음 무대" /
     * "무대 종료" / "오늘 무대 없음".
     *
     * 빈 문자열이면 화면은 대신 [HoyolandLineup.theme](게임별 테마)을 쓴다. 행사 전에는 테마가
     * 답할 질문("어느 게임이 뭘 들고 오나")이고, 행사 중에는 이 줄이 답할 질문("지금 어디로
     * 갈까")으로 바뀐다 — 같은 자리가 단계에 따라 다른 말을 한다.
     */
    fun lineupStatusOf(game: String, nowMillis: Long = currentTimeMillis()): String {
        if (game.isBlank()) return ""
        val today = todayYmd(nowMillis) ?: return ""
        val mine = stageSlots(today, nowMillis).filter { it.slot.game == game }
        if (mine.isEmpty()) return "오늘 무대 없음"
        mine.firstOrNull { it.state == StageState.LIVE }?.let { return "지금 무대" }
        mine.firstOrNull { it.state == StageState.UPCOMING }?.let { return "${it.slot.time} 다음 무대" }
        return "오늘 무대 종료"
    }

    /** 오늘이 행사 기간 안이면 그 `yyyy-MM-dd`, 아니면 null. */
    @OptIn(ExperimentalTime::class)
    private fun todayYmd(nowMillis: Long): String? {
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(HoyolandTimeZone).date.toString()
        return today.takeIf { it in dayYmds }
    }

    /** 그날 무대에 오르는 게임들(원본 순서, 중복 제거) — 필터 칩이 쓴다. */
    fun stageGames(ymd: String): List<String> =
        slotsFor(ymd).map { it.game }.filter { it.isNotBlank() }.distinct()

    /**
     * 프로그램 제목에 든 게임 이름. 없으면 빈 문자열.
     *
     * 웰컴 키트처럼 **게임별로 갈리는 항목**이 프로그램 목록에 섞여 든다("웰컴 키트 — 원신").
     * 카드에 게임 배지를 달아 주면 넷이 나란히 서도 내 것이 한눈에 걸린다.
     *
     * [HoyolandProgram] 에 game 필드를 새로 두지 않는 이유: 이 목록은 **앱 업데이트 없이 갱신되는
     * 원격 설정**이다. 필드를 늘리면 그 값을 읽는 빌드가 깔리기 전까지는 배지가 안 나오는데,
     * 제목에서 가려내면 오늘 올린 config 가 오늘 그대로 걸린다.
     *
     * 제목만 본다 — 설명까지 뒤지면 '2차 창작물 전시존' 처럼 본문에 세 게임을 나열하는 항목이
     * 엉뚱한 색을 얻는다. [lineup] 에 있는 이름만 찾으므로 행사에 없는 게임은 걸리지 않는다.
     */
    fun programGame(title: String): String =
        lineup.map { it.game }.firstOrNull { it.isNotBlank() && it in title } ?: ""

    /**
     * 푸드존 — 프로그램 목록에서 **먹는 것만** 따로 뽑는다(별도 페이지가 쓴다).
     *
     * 게임마다 메뉴가 10줄 가까이 되어, 프로그램 목록에 그대로 두면 웰컴 키트·전시존이
     * 메뉴판 사이에 파묻혔다. 성격도 다르다 — 나머지는 "신청·수령"이고 이건 **현장에서
     * 골라 사는 것**이라 굿즈와 같은 줄에 있어야 한다.
     *
     * 가르는 기준은 [HoyolandProgram.isFood] — 제목 머리(푸드존 · 푸드트럭)이거나 메뉴 사진이 걸린 줄이다.
     */
    val foodPrograms: List<HoyolandProgram>
        get() = programs.filter { it.isFood }

    /** 입장 특전(웰컴 키트) — 「입장 특전」 섹션이 쓴다. 예매 바로 아래에 선다([HoyolandProgram.isPerk]). */
    val perkPrograms: List<HoyolandProgram>
        get() = programs.filter { it.isPerk }

    /** 푸드존 · 입장 특전을 뺀 나머지 프로그램 — 프로그램 섹션이 쓴다. */
    val otherPrograms: List<HoyolandProgram>
        get() = programs.filterNot { it.isFood || it.isPerk }

    /**
     * 푸드존 입구 줄 — "3곳 · 3,000원 ~ 13,500원".
     *
     * 값은 메뉴 본문에서 긁는다. 가격은 원격 config 의 설명 글에만 있고 별도 필드가 없다 —
     * 굿즈처럼 숫자 필드로 올리면 메뉴가 바뀔 때마다 두 군데를 고쳐야 한다.
     *
     * **메뉴 줄(`· 이름 — 7,000원`)만 센다.** 설명 글 전체에서 "원"을 긁으면 "(우유·펄 추가
     * 시 1,000원)" · "따로 사면 13,000원" 같은 문장 속 숫자가 섞여 최저가가 1,000원이 됐다.
     * 화면이 메뉴로 그리는 줄([HoyolandText.isMenuLine])과 같은 것만 본다.
     */
    fun foodEntryLine(): String {
        val list = foodPrograms
        if (list.isEmpty()) return "푸드존 정보 공개 전"
        val prices = list.flatMap { p ->
            p.desc.split("\n")
                .filter { HoyolandText.isMenuLine(it) }
                .mapNotNull { PriceRegex.find(it.substringAfterLast(" — "))?.groupValues?.get(1) }
                .mapNotNull { it.replace(",", "").toIntOrNull() }
        }
        val count = "${list.size}곳"
        if (prices.isEmpty()) return "$count · 게임별 메뉴"
        val lo = prices.min()
        val hi = prices.max()
        return if (lo == hi) "$count · ${wonLabel(lo)}" else "$count · ${wonLabel(lo)} ~ ${wonLabel(hi)}"
    }

    /**
     * 화면에 싣는 굿즈 — **앱이 다루는 게임 것과 행사 공용만.**
     *
     * 호요랜드에는 붕괴3rd·미해결사건부처럼 이 앱이 기록을 다루지 않는 IP 도 나온다. 그 굿즈까지
     * 실으면 "내 게임 굿즈가 얼마인지"를 보러 온 사람의 목록이 두 배가 된다 — 여기서 걸러
     * 낸다(무대 시간표는 그대로 다 싣는다. 거기서는 무대가 겹치는지가 정보다).
     *
     * `game` 이 빈 것은 행사 공용(아트북·에코백)이라 남긴다.
     */
    val visibleGoods: List<HoyolandGoods>
        get() = goods.filter { it.game.isBlank() || GameData.byNameOrNull(it.game) != null }
            // 「전체」에서는 게임 순서(원신 → 스타레일 → 젠레스 …)로 묶어 보여 준다. 원격 JSON 은
            // 공개된 순서대로 쌓이므로 그대로 두면 게임이 뒤섞인다. sortedBy 는 안정 정렬이라
            // 같은 게임 안에서는 JSON 에 적힌 순서가 그대로 남는다(공식 표의 배열이 정보다).
            // 게임 없는 행사 공용은 맨 뒤로 — 어느 게임에도 속하지 않아 사이에 끼면 경계가 흐려진다.
            .sortedBy { GameData.byNameOrNull(it.game)?.ordinal ?: Int.MAX_VALUE }

    /** 굿즈 목록에 등장하는 게임들(원본 순서, 중복 제거) — 게임 탭이 쓴다. */
    val goodsGames: List<String>
        get() = visibleGoods.map { it.game }.filter { it.isNotBlank() }.distinct()

    /**
     * 부스 목록에 등장하는 게임들(원본 순서, 중복 제거) — 부스 페이지의 게임 탭이 쓴다.
     *
     * `game` 이 빈 부스(전 IP 공통 포토존 같은 것)는 탭을 만들지 않는다. 탭은 "그 게임 것만
     * 보기" 라서, 소속이 없는 자리는 「전체」에만 있으면 된다.
     */
    val boothGames: List<String>
        get() = booths.map { it.game }.filter { it.isNotBlank() }.distinct()

    /** 체험 부스(전체 · 게임 탭) — DIY존 · 파트너사 자리는 각자 탭으로 빠진다. */
    val experienceBooths: List<HoyolandBooth> get() = booths.filter { !it.isDiy && !it.isPartner }

    /** 파트너사 부스(구글 플레이 · 갤럭시 스토어 …) — 부스 페이지의 파트너사 탭. */
    val partnerBooths: List<HoyolandBooth> get() = booths.filter { it.isPartner }

    /** 배치도 칸 이름에 맞는 파트너사 부스 — 띄어쓰기를 무시하고 부스 이름과 견준다("구글플레이" = "구글 플레이"). */
    fun partnerForZone(label: String): HoyolandBooth? {
        val key = label.replace(" ", "")
        return partnerBooths.firstOrNull { it.title.replace(" ", "") == key }
    }

    /** DIY 탭을 세울 만큼 DIY 자리가 있는가. */
    val hasDiy: Boolean get() = booths.any { it.isDiy }

    /** DIY존 이용 안내 — DIY 자리 중 **참가비 없는** 한 건(만들기가 아니라 안내문이다). */
    val diyGuide: HoyolandBooth? get() = booths.firstOrNull { it.isDiy && !it.isPaid }

    /** DIY 만들기 목록 — 참가비가 있는 DIY 자리(에코백 · 키링 …). */
    val diyItems: List<HoyolandBooth> get() = booths.filter { it.isDiy && it.isPaid }

    /**
     * 굿즈 가격대 한 줄 — "8,000원 ~ 89,000원 · 45종".
     *
     * 목록 맨 위에 **얼마를 들고 가야 하는지**를 먼저 말한다. 값을 못 받은 품목(0)은 범위 계산에서
     * 빼되 개수에는 넣는다 — "가격 미정 3점"이 숨으면 예산을 잘못 잡는다.
     */
    fun goodsPriceRange(): String {
        val list = visibleGoods
        if (list.isEmpty()) return ""
        val priced = list.mapNotNull { it.price.takeIf { p -> p > 0 } }
        // 단위는 **종** — 장바구니가 "3종 · 4개" 로 세므로 같은 말을 쓴다.
        // ("점" 은 점수로 읽힌다는 지적이 있었다 — 2026-09-10)
        val count = "${list.size}종"
        if (priced.isEmpty()) return "가격 공개 전 · $count"
        val lo = priced.min()
        val hi = priced.max()
        val range = if (lo == hi) wonLabel(lo) else "${wonLabel(lo)} ~ ${wonLabel(hi)}"
        val unpriced = list.size - priced.size
        val tail = if (unpriced > 0) " · 가격 미정 ${unpriced}종" else ""
        return "$range · $count$tail"
    }

    /**
     * 장바구니에 담긴 줄 — **지금 목록에 있는 굿즈만.**
     *
     * 이름이 바뀌거나 판매 목록에서 내려간 굿즈는 조용히 빠진다. 옛 이름으로 담아 둔 값을
     * 그대로 합계에 넣으면 이미 없는 물건의 가격을 세는 셈이다(원격 JSON 에 안정적인 id 가
     * 없어 이름으로 담는다 — [HoyolandCart] 참고).
     */
    fun cartLines(cart: HoyolandCart): List<HoyolandCartLine> =
        visibleGoods.mapNotNull { g ->
            cart.quantityOf(g.name).takeIf { it > 0 }?.let { HoyolandCartLine(g, it) }
        }

    /**
     * 담은 종류 수 · 개수 — **지금 목록 기준**([cartLines]).
     * [HoyolandCart.kindCount] 는 목록에서 빠진 이름까지 세서 「담은 3종 · 0원」 같은 유령 줄을 만든다.
     */
    fun cartKindCount(cart: HoyolandCart): Int = cartLines(cart).size

    fun cartItemCount(cart: HoyolandCart): Int = cartLines(cart).sumOf { it.quantity }

    /** 장바구니 합계(원). 가격 미정 품목은 0 으로 더해진다. */
    fun cartTotal(cart: HoyolandCart): Int = cartLines(cart).sumOf { it.subtotal }

    /**
     * 장바구니에서 **가격을 모르는 품목 수** — 합계 옆에 따로 말해 줘야 한다.
     * 숨기면 "이 값이 전부"로 읽혀 예산을 잘못 잡는다.
     */
    fun cartUnpricedCount(cart: HoyolandCart): Int = cartLines(cart).count { it.goods.price <= 0 }

    /**
     * 장바구니를 **게임별로 묶는다** — 굿즈 목록의 원본 순서를 그대로 따른다(정렬하지 않는다).
     * 화면에서 목록과 장바구니의 순서가 달라지면 같은 물건을 두 번 찾게 된다.
     * 행사 공용(`game` 이 빈 것)은 맨 뒤로 — 게임 부스를 다 돈 뒤에 들르는 자리다.
     */
    fun cartGroups(cart: HoyolandCart): List<HoyolandCartGroup> {
        val lines = cartLines(cart)
        if (lines.isEmpty()) return emptyList()
        val order = lines.map { it.goods.game }.distinct().sortedBy { if (it.isBlank()) 1 else 0 }
        return order.map { game ->
            val mine = lines.filter { it.goods.game == game }
            HoyolandCartGroup(
                game = game,
                lines = mine,
                subtotal = mine.sumOf { it.subtotal },
                unpriced = mine.count { it.goods.price <= 0 },
            )
        }
    }

    /**
     * "12,000원" — 천 단위 콤마. 굿즈 목록·합계·부스 참가비가 같은 표기를 쓰도록 여기 하나만 둔다.
     *
     * ₩ 기호를 앞에 붙이던 것을 뒤의 "원" 으로 바꿨다. 공식 굿즈표가 전부 "24,000원" 으로 적고,
     * 앱의 다른 금액 표기도 원 단위라 기호만 이 화면에서 튀었다.
     */
    fun wonLabel(v: Int): String {
        val sb = StringBuilder()
        val digits = v.toString()
        for (i in digits.indices) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append(digits[i])
        }
        return "${sb}원"
    }

    /**
     * 처음 열었을 때 선택돼 있을 날짜 칸. 행사 중이면 **오늘**, 아니면 첫날.
     * 현장에서 꺼냈을 때 오늘이 아닌 날이 선택돼 있으면 매번 한 번 더 눌러야 한다.
     */
    fun defaultDayIndex(nowMillis: Long = currentTimeMillis()): Int {
        val i = dayOrdinal(nowMillis) - 1
        return if (i in dayYmds.indices) i else 0
    }

    /**
     * 발표 → 개막 구간에서 지금 어디까지 왔는지(0f ~ 1f). 카운트다운 진행 바가 쓴다.
     * 발표일을 못 읽으면 0 — 바가 비어 있을지언정 틀린 자리를 가리키진 않는다.
     */
    fun progress(nowMillis: Long = currentTimeMillis()): Float {
        val s = start ?: return 0f
        val a = runCatching { LocalDate.parse(announceYmd) }.getOrNull() ?: return 0f
        val total = a.daysUntil(s)
        if (total <= 0) return 1f
        val done = a.daysUntil(today(nowMillis))
        return (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * 알림 예약이 쓰는 시각들 — 화면 표기와 달리 **밀리초**가 필요하다.
     * 날짜를 못 읽으면 0 을 돌려주고, 호출부는 0 을 "예약 대상 아님"으로 다룬다.
     */
    @OptIn(ExperimentalTime::class)
    private fun millisAt(date: LocalDate?, hour: Int): Long =
        // 시(時)가 범위 밖이면 0 — `atTime(24, 0)` 은 예외를 던지고, 그 예외가 예약 알림 계획 전체를 버리게 했다(27.52.0).
        if (hour !in 0..23) 0L
        else date?.atTime(hour, 0)?.toInstant(HoyolandTimeZone)?.toEpochMilliseconds() ?: 0L

    /** 개막일 [hour] 시의 로컬 시각(밀리초). 날짜를 못 읽으면 0. */
    fun startAtMillis(hour: Int): Long = millisAt(start, hour)

    /**
     * 그날 **내 입장 시각**(자정부터의 분). 안 가는 날이거나 시각을 모르면 0.
     *
     * 시간표가 "내가 못 보는 편"을 가리는 데 쓴다 — 10시 입장인데 9시 편이 목록 맨 위에
     * 서 있으면, 그날 무대를 훑을 때마다 볼 수 없는 편부터 읽게 된다.
     */
    fun entryMinutesOn(ymd: String, entry: HoyolandEntry): Int {
        val group = entry.groupOn(ymd)
        if (group.isBlank()) return 0
        return minutesOfDay(entryTimeOf(group)) ?: 0
    }

    /**
     * 시간표 머리에 걸 한 줄 — "A조 10:00 입장 · 그 전 2편은 흐리게". 안 가는 날이면 빈 문자열.
     *
     * 못 보는 편이 없으면 셈을 빼고 입장 시각만 말한다 — 없는 것을 "0편" 으로 적으면
     * 그 줄을 한 번 더 읽게 된다.
     */
    fun entryStageNote(ymd: String, entry: HoyolandEntry, nowMillis: Long = currentTimeMillis()): String {
        val at = entryMinutesOn(ymd, entry)
        if (at <= 0) return ""
        val head = "${entry.groupOn(ymd)}조 ${entryTimeOf(entry.groupOn(ymd))} 입장"
        val missed = stageSlots(ymd, nowMillis).count { it.isBeforeEntry(at) }
        return if (missed <= 0) head else "$head · 그 전 ${missed}편은 흐리게"
    }

    /**
     * 그날 **내 입장 시각**(밀리초) — [entryTimeOf] 의 "HH:mm" 을 그 날짜에 얹는다.
     *
     * [millisAt] 은 시 단위인데 조 편성은 분까지 있을 수 있어([HoyolandEntryGroup.time])
     * 자정에 분을 더한다. 조 편성이 바뀌어 시각을 못 찾거나 날짜를 못 읽으면 0 —
     * 호출부는 0 을 "예약 대상 아님" 으로 다룬다.
     */
    fun entryAtMillis(ymd: String, group: String): Long {
        val date = runCatching { LocalDate.parse(ymd) }.getOrNull() ?: return 0L
        val minutes = minutesOfDay(entryTimeOf(group)) ?: return 0L
        return millisAt(date, 0) + minutes * 60_000L
    }

    /** 예매 오픈 시각(밀리초). 미정이면 0. */
    fun ticketOpenMillis(): Long =
        if (ticket.openYmd.isBlank()) 0L
        else millisAt(runCatching { LocalDate.parse(ticket.openYmd) }.getOrNull(), ticket.openHour)

    /**
     * 홈·일정 탭에 노출할 값어치가 있는 기간인지 — 개막 60일 전부터 폐막일까지.
     *
     * 날짜가 없으면([HoyolandPhase.TBA]) false 다 — 셀 날짜가 없는데 띄우면 「D-0」 배너가 선다.
     */
    fun isFeatured(nowMillis: Long = currentTimeMillis()): Boolean = when (phase(nowMillis)) {
        HoyolandPhase.UPCOMING, HoyolandPhase.TOMORROW -> daysUntilStart(nowMillis) <= FEATURE_WINDOW_DAYS
        HoyolandPhase.TODAY, HoyolandPhase.ONGOING -> true
        HoyolandPhase.ENDED, HoyolandPhase.TBA -> false
    }

    companion object {
        /** 푸드존 메뉴 글에서 "7,000원" 같은 값을 긁는다 — [foodEntryLine] 의 가격대. */
        private val PriceRegex = Regex("""([\d,]+)원""")

        /** 마감 안내 속 "9.13" — 앞뒤에 숫자 · 점이 붙은 것(2026.9.13)과 배수 · 비율(1.25배 · 1.5%)은 건너뛴다. */
        private val MonthDayRegex = Regex("""(?<![\d.])(\d{1,2})\.(\d{1,2})(?![\d.]|\s*[배%])""")

        private val YearRegex = Regex("""20\d{2}""")

        /** 회차 값에서 개최 연도를 읽는다 — "2026-10-01" · "호요랜드 2026" 모두 "2026". 못 읽으면 값 그대로. */
        internal fun editionKeyOf(raw: String): String = YearRegex.find(raw)?.value ?: raw

        /** 홈·일정 탭 노출을 시작하는 시점(개막 D-60). 그 전엔 게임정보 탭에서만 보인다. */
        const val FEATURE_WINDOW_DAYS = 60

        /** 길이도 다음 편도 없는 마지막 무대의 기본 길이(분). */
        const val FALLBACK_STAGE_MIN = 40

        /** "14:00" → 840. "종일"·"수시"처럼 못 읽는 칸은 null(목록에만 남고 라이브 판정에서 빠진다). */
        internal fun minutesOfDay(time: String): Int? {
            val m = HHMM.find(time.trim()) ?: return null
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val min = m.groupValues[2].toIntOrNull() ?: return null
            if (h !in 0..47 || min !in 0..59) return null
            return h * 60 + min
        }

        /** 840 → "14:00". 자정을 넘긴 값(1500)도 그날 시각 표기로 돌린다. */
        internal fun hhmm(minutes: Int): String {
            val h = (minutes / 60) % 24
            val m = minutes % 60
            return "${if (h < 10) "0" else ""}$h:${if (m < 10) "0" else ""}$m"
        }

        /** 문자열 맨 앞의 "H:mm"/"HH:mm". */
        private val HHMM = Regex("^(\\d{1,2}):(\\d{2})")
    }
}
