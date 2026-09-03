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
 * 호요랜드(호요버스 한국 오프라인 행사) 정본 — **Android·iOS 가 이 한 소스를 공유한다.**
 *
 * 예전에는 일정·장소·라인업이 `HoyolandSection.kt` 와 `HoyolandSection.swift` 에 문자열 상수로
 * 두 벌 있었다. 행사 정보는 개최 전까지 계속 바뀌는데(예매·프로그램이 순차 공개된다),
 * 두 벌을 손으로 맞추면 한쪽만 고쳐 갈라진다 — [NotificationCatalog] 와 같은 이유로 여기에 모은다.
 *
 * 갱신 경로는 두 갈래다:
 *  - **원격**: `hoyoland.json` ([com.gatcha.log.data.api.HoyolandApi]) — 앱 업데이트 없이 바뀐다.
 *  - **번들**: [HoyolandDefaults] — 네트워크가 없거나 JSON 이 깨져도 화면이 비지 않게 하는 폴백.
 *
 * 표시 문자열(기간 라벨·D-day)은 전부 이 파일의 파생값으로 만든다. 플랫폼이 각자 조립하면
 * "2026.10.2(금) ~ 10.5(월)" 같은 표기가 또 갈라진다.
 */

/** 행사 진행 단계 — 배지·D-day 문구가 이 값으로 갈린다. */
enum class HoyolandPhase { BEFORE, ONGOING, ENDED }

/**
 * 예매 상태. 2026 은 일정·장소·라인업이 모두 확정됐는데 **예매만 미공개**라,
 * 이 한 항목의 상태를 따로 들고 다녀야 화면이 "무엇이 안 정해졌는지"를 정확히 말할 수 있다.
 */
enum class HoyolandTicketStatus { UNDECIDED, ANNOUNCED, ON_SALE, SOLD_OUT }

/**
 * 라벨 + 값 한 쌍. `Pair` 를 쓰지 않는 이유는 Swift 에서 `KotlinPair` 를 벗겨 써야 해서
 * 호출부가 지저분해지기 때문이다([NotificationCatalog] 의 `groups`/`itemsIn` 분리와 같은 이유).
 */
data class HoyolandFact(val label: String, val value: String)

/**
 * 참여 게임 한 줄.
 *
 * @param abbr 게임 태그 약칭. **비어 있으면** 플랫폼이 `GameData` 기본값을 쓴다.
 * @param colorArgb 태그 색(0xAARRGGBB). **0 이면** 플랫폼이 `GameData` 기본값을 쓴다.
 *   붕괴3rd·미해결사건부는 앱이 가챠를 다루는 게임이 아니라 `GameData` 에 없다 —
 *   폴백에 맡기면 약칭이 앞 2글자("붕괴")가 되고 색은 둘 다 원신 색이 되므로 여기서 직접 준다.
 *   `Long?` 대신 0 을 센티널로 쓰는 건 Swift 에서 `KotlinLong?` 언박싱을 피하려는 것이다.
 */
data class HoyolandLineup(
    val game: String,
    val theme: String,
    val abbr: String = "",
    val colorArgb: Long = 0L,
)

/**
 * 부대 프로그램 한 건. 행사 본편과 별개로 **참여 마감이 따로 있는** 것들이 있어서
 * (2차 창작물 전시 모집처럼) 마감 안내를 값으로 들고 다닌다.
 */
data class HoyolandProgram(
    val title: String,
    val desc: String,
    /** 참여 마감 안내. 없으면 빈 문자열. */
    val deadline: String = "",
)

/** 예매 정보. [status] 가 [HoyolandTicketStatus.UNDECIDED] 면 [note] 만 보여 준다. */
data class HoyolandTicket(
    val status: HoyolandTicketStatus,
    /** 예매처 이름(예: "티켓링크"). 미정이면 빈 문자열. */
    val vendor: String = "",
    /** 오픈 일시 표기(예: "2026.09.22(월) 19:00"). 미정이면 빈 문자열. */
    val openLabel: String = "",
    /**
     * 오픈 날짜(`yyyy-MM-dd`)와 시(24시간). **알림 예약이 읽는 값**이라 [openLabel] 과 따로 둔다 —
     * 표기 문자열을 파싱해 시각을 얻으려 들면 "19:00"·"오후 7시" 같은 표기 변화에 알림이 깨진다.
     * 미정이면 [openYmd] 가 빈 문자열이고, 그러면 예약을 만들지 않는다.
     */
    val openYmd: String = "",
    val openHour: Int = 0,
    /** 가격 표기(예: "30,000원"). 미정이면 빈 문자열. */
    val priceLabel: String = "",
    val url: String = "",
    /** 화면에 그대로 나가는 한 줄 안내. */
    val note: String = "",
) {
    val isUndecided: Boolean get() = status == HoyolandTicketStatus.UNDECIDED

    /** 값 칸에 들어갈 한 마디 — "미정" / "오픈 예정" / "판매 중" / "매진". */
    val statusLabel: String
        get() = when (status) {
            HoyolandTicketStatus.UNDECIDED -> "미정"
            HoyolandTicketStatus.ANNOUNCED -> "오픈 예정"
            HoyolandTicketStatus.ON_SALE -> "판매 중"
            HoyolandTicketStatus.SOLD_OUT -> "매진"
        }
}

/**
 * 일자별 프로그램 한 줄.
 *
 * [time] 은 표기 그대로 쓴다("10:00", "13:00 ~ 14:30"). 시각을 파싱하지 않는 이유는
 * 시간표가 "종일"·"수시" 같은 칸을 섞어 내기 때문이다 — 정렬은 원본 순서를 그대로 따른다.
 */
data class HoyolandSlot(val time: String, val title: String, val desc: String = "")

/** 행사 하루치. [ymd] 는 `yyyy-MM-dd`. */
data class HoyolandDay(val ymd: String, val slots: List<HoyolandSlot>)

/** 지난 행사 1건 — 다음 행사 규모를 가늠하는 참고 자료로만 쓴다. */
data class HoyolandPastEvent(val title: String, val facts: List<HoyolandFact>)

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
    val past: List<HoyolandPastEvent>,
) {

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

    @OptIn(ExperimentalTime::class)
    private fun today(nowMillis: Long): LocalDate =
        Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(DateUtil.timeZone).date   // 캐시된 타임존(시스템 조회 우회 금지)

    /** 지금이 개최 전인지·중인지·끝났는지. */
    fun phase(nowMillis: Long = currentTimeMillis()): HoyolandPhase {
        val s = start ?: return HoyolandPhase.BEFORE
        val e = end ?: return HoyolandPhase.BEFORE
        val t = today(nowMillis)
        return when {
            t < s -> HoyolandPhase.BEFORE
            t > e -> HoyolandPhase.ENDED
            else -> HoyolandPhase.ONGOING
        }
    }

    /** 개막까지 남은 일수(0 = 오늘 개막). 이미 시작했으면 0. */
    fun daysUntilStart(nowMillis: Long = currentTimeMillis()): Int {
        val s = start ?: return 0
        val d = today(nowMillis).daysUntil(s)
        return if (d < 0) 0 else d
    }

    /** 진행 중일 때 오늘이 몇 일차인지(1 = 첫날). 진행 중이 아니면 0. */
    fun dayOrdinal(nowMillis: Long = currentTimeMillis()): Int {
        if (phase(nowMillis) != HoyolandPhase.ONGOING) return 0
        val s = start ?: return 0
        return s.daysUntil(today(nowMillis)) + 1
    }

    /**
     * 배지에 들어가는 한 마디 — "D-29" / "오늘 개막" / "2일차" / "종료".
     * 진입 카드·홈 카드·일정 탭이 전부 이 값을 쓴다(자리마다 다르게 조립하면 또 갈라진다).
     */
    fun statusLabel(nowMillis: Long = currentTimeMillis()): String = when (phase(nowMillis)) {
        HoyolandPhase.BEFORE -> daysUntilStart(nowMillis).let { if (it == 0) "오늘 개막" else "D-$it" }
        HoyolandPhase.ONGOING -> "${dayOrdinal(nowMillis)}일차"
        HoyolandPhase.ENDED -> "종료"
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

    /** 날짜 탭 라벨 — "10.2(금)". */
    fun dayTabLabel(ymd: String): String {
        val p = ymd.split("-")
        if (p.size < 3) return ymd
        val d = runCatching { LocalDate.parse(ymd) }.getOrNull() ?: return ymd
        return "${p[1].trimStart('0')}.${p[2].trimStart('0')}(${d.dayOfWeek.koLabel})"
    }

    /** 그날의 프로그램. 없으면 빈 목록. */
    fun slotsFor(ymd: String): List<HoyolandSlot> =
        days.firstOrNull { it.ymd == ymd }?.slots.orEmpty()

    /** 한 칸이라도 공개된 시간표가 있는지 — 없으면 화면이 '공개 전' 안내로 갈린다. */
    val hasTimetable: Boolean get() = days.any { it.slots.isNotEmpty() }

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
        date?.atTime(hour, 0)?.toInstant(DateUtil.timeZone)?.toEpochMilliseconds() ?: 0L

    /** 개막일 [hour] 시의 로컬 시각(밀리초). 날짜를 못 읽으면 0. */
    fun startAtMillis(hour: Int): Long = millisAt(start, hour)

    /** 예매 오픈 시각(밀리초). 미정이면 0. */
    fun ticketOpenMillis(): Long =
        if (ticket.openYmd.isBlank()) 0L
        else millisAt(runCatching { LocalDate.parse(ticket.openYmd) }.getOrNull(), ticket.openHour)

    /** 홈·일정 탭에 노출할 값어치가 있는 기간인지 — 개막 60일 전부터 폐막일까지. */
    fun isFeatured(nowMillis: Long = currentTimeMillis()): Boolean = when (phase(nowMillis)) {
        HoyolandPhase.BEFORE -> daysUntilStart(nowMillis) <= FEATURE_WINDOW_DAYS
        HoyolandPhase.ONGOING -> true
        HoyolandPhase.ENDED -> false
    }

    companion object {
        /** 홈·일정 탭 노출을 시작하는 시점(개막 D-60). 그 전엔 게임정보 탭에서만 보인다. */
        const val FEATURE_WINDOW_DAYS = 60
    }
}

/**
 * 번들 폴백 — 2026-08-31 개최 발표 + 2026-09-03 확인 기준.
 *
 * **예매만 미정이고 나머지는 전부 확정이다.** 원격 JSON 이 이 값을 덮어쓰므로,
 * 여기는 "네트워크 없이 앱을 처음 켰을 때 보여도 틀리지 않은 내용"만 둔다.
 */
object HoyolandDefaults {

    val event: HoyolandEvent = HoyolandEvent(
        edition = "호요랜드 2026",
        // 개천절(10.3 토) 대체공휴일 10.5(월)까지 이어지는 연휴 4일.
        startYmd = "2026-10-02",
        endYmd = "2026-10-05",
        // 지난 2024·2025 와 같은 곳. 2026 은 후면광장까지 쓴다.
        venueName = "일산 킨텍스 제2전시장",
        venueHall = "7·8홀 · 후면광장",
        venueAddress = "경기도 고양시 일산서구 킨텍스로 217-60",
        mapUrl = "https://map.naver.com/p/search/%ED%82%A8%ED%85%8D%EC%8A%A4%20%EC%A0%9C2%EC%A0%84%EC%8B%9C%EC%9E%A5",
        mapFallbackUrl = "https://www.google.com/maps/search/%EC%9D%BC%EC%82%B0+%ED%82%A8%ED%85%8D%EC%8A%A4+%EC%A0%9C2%EC%A0%84%EC%8B%9C%EC%9E%A5",
        officialUrl = "https://www.hoyolab.com/",
        announceYmd = "2026-08-31",
        ticket = HoyolandTicket(
            status = HoyolandTicketStatus.UNDECIDED,
            note = "예매 일정·가격은 아직 공개 전입니다. 공개되면 여기에서 바로 업데이트됩니다.",
        ),
        lineup = listOf(
            HoyolandLineup("원신", "달빛에 전하는 세레나데"),
            HoyolandLineup("붕괴: 스타레일", "환락, 상상 그 이상으로"),
            HoyolandLineup("젠레스 존 제로", "구름 너머로 내려앉은 시"),
            // 색은 원신 파랑·스타레일 보라와 섞이지 않게 고른 시안/로즈.
            HoyolandLineup("붕괴3rd", "환야의 숨바꼭질", abbr = "HI3", colorArgb = 0xFF30C6E8L),
            HoyolandLineup("미해결사건부", "미림 장터·사계절의 러브레터", abbr = "ToT", colorArgb = 0xFFE0557BL),
        ),
        programs = listOf(
            HoyolandProgram(
                title = "2차 창작물 전시존",
                desc = "원신 · 붕괴: 스타레일 · 젠레스 존 제로 대상 팬아트 전시",
                deadline = "모집 9.13(일) 23:59 마감 · 결과 9.15(화) 발표",
            ),
        ),
        notice = "일정 · 장소 · 참여 게임이 모두 확정됐습니다. 예매와 일자별 시간표는 아직 공개 전입니다.",
        // 공식 시간표 미공개 — 날짜 탭은 기간에서 만들어지므로 여기는 비워 둔다.
        // 공개되면 hoyoland.json 의 days 를 채우는 것만으로 화면이 찬다(앱 업데이트 불필요).
        days = emptyList(),
        past = listOf(
            HoyolandPastEvent(
                "호요랜드 2025",
                listOf(
                    HoyolandFact("기간", "2025.10.9 ~ 10.12 (4일)"),
                    HoyolandFact("장소", "일산 킨텍스 제2전시장 9·10홀"),
                    HoyolandFact("규모", "약 26,000㎡ · 티켓 3만 6천 장 완판"),
                    HoyolandFact("관람객", "약 3만 2천 명 (4일)"),
                    HoyolandFact("티켓", "30,000원 · 예매 게임별 웰컴키트"),
                    HoyolandFact("참여 IP", "원신 · 붕괴3rd · 스타레일 · 젠레스 · 미해결사건부"),
                    HoyolandFact("구성", "체험존 · 굿즈 · 푸드 · 창작전시/DIY · 무대"),
                ),
            ),
            HoyolandPastEvent(
                "호요랜드 2024 (첫 개최)",
                listOf(
                    HoyolandFact("기간", "2024.10.31 ~ 11.3 (4일)"),
                    HoyolandFact("장소", "일산 킨텍스 제2전시장 7·8홀"),
                    HoyolandFact("관람객", "5만 명 이상 (4일)"),
                    HoyolandFact("티켓", "13,000원 · 회차당 1인 1매"),
                    HoyolandFact("참여 IP", "원신 · 붕괴3rd · 스타레일 · 젠레스 · 미해결사건부"),
                    HoyolandFact("구성", "미니게임 · 포토존 · 코스프레 퍼레이드 · 팬사인회 · 무대"),
                ),
            ),
        ),
    )
}
