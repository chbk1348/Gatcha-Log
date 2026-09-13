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
 *  - **원격**: `config/hoyoland.json` ([com.gatcha.log.data.api.HoyolandApi]) — 앱 업데이트 없이 바뀐다.
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
 * 일자별 프로그램 한 줄 — **무대 편성**이다.
 *
 * 한 칸 = 메인/서브 무대의 공연 한 편. 개장·체험존·부대 프로그램은 여기 오지 않는다
 * (그건 [HoyolandEvent.programs] 몫). 그래서 칸의 주인은 거의 언제나 **게임**이다.
 *
 * [time] 은 표기 그대로 쓴다("14:00", "종일"). 정렬도 원본 순서를 그대로 따른다 —
 * 시간표가 "종일"·"수시" 같은 칸을 섞어 낼 수 있어서다. 다만 "HH:mm" 꼴이면
 * [HoyolandEvent.stageSlots] 가 그 칸만 골라 '지금/다음'을 계산한다(못 읽는 칸은 그냥 목록).
 *
 * @param game 어느 게임 무대인지. 비어 있으면 전 IP 공통(합동 무대·인사).
 * @param minutes 공연 길이(분). 0이면 **다음 편 시작 전까지**로 본다.
 * @param cast 출연자 — "성우 OOO · 밴드 OOO" 처럼 **표기 그대로** 받는다. 무대를 고르는 기준이
 *   공연명보다 출연자인 사람이 많아서(성우 무대가 특히 그렇다) 목록에 같이 싣는다.
 */
data class HoyolandSlot(
    val time: String,
    val title: String,
    val desc: String = "",
    val game: String = "",
    val minutes: Int = 0,
    val cast: String = "",
)

/** 무대 한 편이 지금 기준으로 어디쯤인지. */
enum class StageState { DONE, LIVE, UPCOMING }

/**
 * 무대 한 편 + 지금 기준 상태. 화면은 이 목록 하나로 라이브 카드·목록을 다 그린다
 * (같은 판정을 두 곳에서 다시 하면 카드와 목록이 어긋난다).
 */
data class StageSlot(
    val slot: HoyolandSlot,
    val state: StageState,
    /** [StageState.LIVE] 일 때 남은 분. 그 밖엔 0. */
    val remainMin: Int = 0,
    /** [StageState.LIVE] 일 때 진행률 0f~1f. 그 밖엔 0f. */
    val progress: Float = 0f,
    /** "14:00 ~ 14:40" — 길이를 모르면 "14:00". */
    val rangeLabel: String = "",
)

/** 행사 하루치. [ymd] 는 `yyyy-MM-dd`. */
data class HoyolandDay(val ymd: String, val slots: List<HoyolandSlot>)

/**
 * 굿즈 한 점.
 *
 * 이 앱이 지출을 다루는 앱이라 **가격이 본론**이다. 현장에서 "얼마 들고 가야 하나"에 답해야
 * 하므로 값은 원 단위 숫자로 받는다(문자열로 받으면 합계를 못 낸다). 미정이면 0.
 *
 * @param game 어느 게임 굿즈인지. 비면 공용(행사 로고·아트북 등).
 * @param category "아크릴"·"인형"·"의류" 같은 갈래. 목록을 훑는 눈금이 된다.
 * @param note "호요랜드2026 시리즈 · 디자인 2종 · 1인 5개 한정" 처럼 살 때 걸리는 조건. 구매 제한은
 *   [limitLabel] 이 여기서 떼어 따로 보여준다.
 *
 * 품절 필드는 두지 않는다 — **호요랜드 굿즈샵은 품절을 따로 알리지 않는다**(2026-09-10 확인).
 * 팔지 않는 물건은 목록에서 내리면 그만이라, 있지도 않은 상태를 화면에 세울 이유가 없다.
 */
data class HoyolandGoods(
    val name: String,
    val price: Int = 0,
    val game: String = "",
    val category: String = "",
    val note: String = "",
) {
    /** `note` 를 " · " 로 끊은 토막들. 공식 표가 조건을 이 구분자로 이어 붙여 낸다. */
    private val noteParts: List<String>
        get() = note.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * "1인 5개 한정" — 구매 제한 한 토막만. 없으면 빈 문자열.
     *
     * 별도 필드로 받지 않는 이유: 이 값은 **원본 표에서 다른 조건과 한 칸에 섞여 나온다**
     * ("호요랜드2026 시리즈 · 디자인 2종 · 1인 5개 한정"). 91건을 손으로 쪼개 두면 다음 갱신 때
     * 누가 다시 쪼개야 하고, 한 건만 빠뜨려도 화면에서 조용히 사라진다. 표기 규칙이 한 가지라
     * 읽는 쪽에서 떼는 편이 싸다.
     */
    val limitLabel: String get() = noteParts.firstOrNull { LimitRegex.matches(it) } ?: ""

    /**
     * "호요랜드2026 시리즈" — **이 행사에서만 파는 물건**이라는 표기. 없으면 빈 문자열.
     *
     * 다음에 사면 되는 상설 굿즈와 달리 여기서 놓치면 끝이라, 갈래 옆 회색 줄에 묻어 두지 않고
     * 배지로 뺀다. '신월의 축복' 같은 값은 상품 라인업 이름이지 행사 한정이 아니므로 걸리지
     * 않는다 — "시리즈" 로 끝나는 조각만 본다.
     */
    val seriesLabel: String get() = noteParts.firstOrNull { it.endsWith("시리즈") } ?: ""

    /** 배지로 빠진 [limitLabel]·[seriesLabel] 을 뺀 나머지 비고 — 같은 값이 두 번 나오지 않게 한다. */
    val noteRest: String
        get() = noteParts.filterNot { LimitRegex.matches(it) || it == seriesLabel }.joinToString(" · ")

    /**
     * "1인 5개 한정" 에서 **5**. 제한이 없거나 숫자를 못 읽으면 0.
     *
     * 표시만 하고 끝내면 장바구니에 열 개를 담아 놓고 현장에서야 못 산다는 걸 안다 — 이 앱이
     * 답하려는 "얼마 들고 가야 하나" 가 그만큼 틀어진다. 담기 수량을 실제로 이 값에서 끊는다
     * (`SpendingViewModel.setGoodsQuantity`).
     */
    val limitPerPerson: Int
        get() = LimitCountRegex.find(limitLabel)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private companion object {
        /** "1인 5개 한정" · "1인 2매 한정" 을 모두 받는다. 수량 단위는 품목마다 다르다. */
        val LimitRegex = Regex("""^1인\s+\S+\s*한정$""")

        /**
         * 수량 자리만 집는다. 숫자를 통째로 긁으면 **'1인' 의 1 이 앞에 붙어** "1인 5개" 가
         * 15 가 된다 — 다섯 개 제한이 열다섯 개로 풀린다.
         */
        val LimitCountRegex = Regex("""^1인\s+(\d+)""")
    }
}

/**
 * 게임별 부스 체험 한 칸.
 *
 * 무대([HoyolandSlot])와 달리 **시각이 없다** — 상시 운영이라 시간표에 얹을 것이 없다.
 * 그래서 시간표가 아니라 게임별 카드로 그린다.
 *
 * @param location "무료 체험존" · "유료 체험존" — 부스가 어느 구역 소속인지. 부스 배치도가 개막
 *   직전에나 나와서 "8홀 A-12" 같은 좌표는 쓸 수 없고, 세 게임이 공통으로 내는 건 이 구분뿐이다.
 *   [price] 와 겹쳐 보이지만 값은 부스 한 건의 참가비고 이쪽은 구역이라, 무료 구역 안의
 *   유료 부스 같은 조합도 표시된다.
 * @param reward "참여 시 아크릴 뱃지 증정" — 부스를 고르는 기준이 되는 값이라 따로 둔다.
 *
 * 예약 필드는 두지 않는다 — **호요랜드 부스는 예약제가 없다**(2026-09-10 확인).
 * 있지도 않은 갈래를 화면에 세우면 "예약이 있는 곳도 있나" 로 읽힌다.
 *
 * **정원 · 회차 필드도 두지 않는다**(2026-09-10 확인). 부스는 회차로 끊어 돌리지 않아서
 * "회차당 몇 명" 이 성립하지 않고, 현장이 지금 얼마나 붐비는지는 알 길도 없다.
 * 이 화면은 공지된 정보를 그대로 옮기는 자리다.
 */
data class HoyolandBooth(
    val game: String,
    val title: String,
    val desc: String = "",
    val location: String = "",
    val reward: String = "",
    /**
     * 1회 참가비(원). **0 이면 무료**다.
     *
     * 설명에 묻어 두면 "얼마 들고 가야 하나"를 문장에서 캐내야 한다 — 유료 체험존은 회차마다
     * 값이 다르고(2,000 / 3,000 / 4,000 / 8,000 / 10,000) 무료 부스와 섞여 있어서, 훑을 때
     * 바로 갈려야 하는 값이다. 굿즈의 `price` 와 같은 이유로 숫자로 받는다.
     */
    val price: Int = 0,
) {
    /** 참가비가 있는 부스인가 — 화면이 값 대신 이 술어로 갈린다. */
    val isPaid: Boolean get() = price > 0
}

/**
 * 지스타(G-STAR) — 호요랜드와 **별개 행사**지만, 호요버스가 나오는 국내 오프라인 자리라
 * 같은 페이지에서 다룬다. 출처와 갱신 주기(참가사 명단이 순차 공개된다)도 호요랜드와 같아
 * 원격 JSON 한 파일에 함께 둔다.
 *
 * @param lineup 호요버스 출품작. [HoyolandLineup.theme] 자리에 **무엇을 하는지**가 들어간다
 *   ("한국 첫 오프라인 시연", "무대"). 호요랜드의 게임별 테마와 같은 줄 규격을 쓰려는 것이다.
 */
data class HoyolandGstar(
    val title: String,
    /** 제목 옆 배지 — 호요버스가 어느 규모로 나오는지 한 마디. */
    val badge: String,
    val facts: List<HoyolandFact>,
    val lineup: List<HoyolandLineup>,
    val url: String,
    val notice: String,
) {
    /** 내용이 하나도 없으면 섹션을 통째로 접는다(원격에서 비워 내릴 수 있게). */
    val isEmpty: Boolean get() = title.isBlank() || (facts.isEmpty() && lineup.isEmpty())

    /**
     * 배너 아래 칸에 들어가는 **한 줄 요약** — "G-STAR 2026 · D-71 · 11.19~11.22 · 부산 벡스코".
     *
     * 지스타는 호요랜드보다 한 달 반 뒤라 배너의 주인공이 될 수 없다. 그렇다고 상세 페이지에만
     * 두면 "호요랜드 말고 또 뭐가 있나"를 아무도 모른다 — 배너 밑단의 작은 줄이 그 자리다.
     *
     * 상세용 값을 그대로 쓰지 않고 줄인다. [facts] 의 기간은 `"2026.11.19(목) ~ 11.22(일) (4일)"`,
     * 장소는 `"부산 벡스코(BEXCO)"` 로 **한 줄에 안 들어간다.** 요일·연도·괄호를 떼는 건 여기서만
     * 하고(상세는 원본 그대로), 양 플랫폼이 같은 문구를 쓰도록 공유 계층에 둔다.
     *
     * 남은 날짜는 [facts] 의 기간 문자열에서 읽는다. 지스타는 호요랜드와 달리 날짜 필드가 따로
     * 없고 원격 JSON 이 사람이 읽는 문장으로 내려주는데, 그 한 줄을 위해 스키마를 늘리기보다
     * 여기서 앞머리 `yyyy.M.d` 만 읽는 편이 원격 갱신을 막지 않는다.
     * 행사가 끝났으면 `null` — 지난 일정을 홈에 남겨 둘 이유가 없다.
     */
    fun homeLine(nowMillis: Long = currentTimeMillis()): String? =
        homeBrief(nowMillis)?.let { "${it.title} · ${it.dday} · ${it.detail}" }

    /**
     * 같은 값을 **조각으로** — 배너 밑단은 한 덩어리 문장이 아니라 호요랜드 위 칸과 같은 짜임
     * (남은 날짜 · 이름 · 나머지)으로 그린다. 그리는 쪽이 문자열을 다시 자르지 않게 여기서 나눈다.
     */
    fun homeBrief(nowMillis: Long = currentTimeMillis()): HoyolandGstarBrief? {
        if (isEmpty) return null
        val period = factValue("기간") ?: return null
        val dday = ddayLabel(period, nowMillis) ?: return null   // 이미 끝난 행사
        val detail = listOfNotNull(shorten(period), factValue("장소")?.let { shorten(it) })
            .joinToString(" · ")
        if (title.isBlank() || detail.isBlank()) return null
        return HoyolandGstarBrief(dday = dday, title = title, detail = detail)
    }

    private fun factValue(label: String): String? =
        facts.firstOrNull { it.label == label }?.value?.takeIf { it.isNotBlank() }

    // ── 상세 화면이 쓰는 조각들 ────────────────────────────────────────────
    //
    // [facts] 는 원격이 내려주는 **자유 목록**이다(라벨이 늘거나 바뀔 수 있다). 화면이 라벨을
    // 하나하나 찾아 쓰면 원격에서 항목을 더했을 때 그 항목만 어디에도 안 나온다. 그래서
    // "아는 라벨은 제자리에, 모르는 라벨은 [otherFacts] 로" 흘려보낸다.

    /** 히어로 아래 3칸 — 기간(일수) · 장소 · 규모. 없는 칸은 "—". */
    val periodShort: String get() = factValue("기간")?.let { shorten(it) } ?: "—"

    /** "4일" — 기간 문자열의 "(4일)" 을 그대로 읽는다. 없으면 빈 문자열. */
    val dayCountLabel: String
        get() = factValue("기간")?.let { DAY_COUNT.find(it)?.groupValues?.get(1) }?.let { "${it}일" } ?: ""

    /** "부산 벡스코" — 영문 병기 괄호를 뗀다. */
    val venueShort: String get() = factValue("장소")?.let { shorten(it) } ?: "—"

    val scaleLabel: String get() = factValue("규모") ?: badge.ifBlank { "—" }

    /**
     * 함께 참가하는 곳 — 한 줄에 "·" 로 이어 붙은 값을 낱개로 가른다.
     * 이름이 일곱 개씩 이어진 한 줄은 읽히지 않는다(칩으로 흩어 놓으면 눈이 하나씩 짚는다).
     */
    val partners: List<String>
        get() = factValue("함께")?.split("·")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** 히어로·3칸·칩이 이미 쓴 라벨을 뺀 나머지 — 라벨/값 목록으로 그대로 그린다. */
    val otherFacts: List<HoyolandFact>
        get() = facts.filter { it.label !in HERO_LABELS }


    /**
     * "D-71" / "오늘 개막" / "진행 중" — 폐막일이 지났으면 null.
     *
     * 폐막일에는 연도가 없다("~ 11.22"). 개막 연도를 그대로 쓴다 — 해를 넘겨 이어지는 행사는
     * 지스타에 없다.
     */
    @OptIn(ExperimentalTime::class)
    private fun ddayLabel(period: String, nowMillis: Long): String? {
        val head = DATE_HEAD.find(period) ?: return null
        val year = head.groupValues[1].toInt()
        val start = runCatching {
            LocalDate(year, head.groupValues[2].toInt(), head.groupValues[3].toInt())
        }.getOrNull() ?: return null
        val tail = DATE_TAIL.find(period)
        val end = tail?.let {
            runCatching { LocalDate(year, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull()
        } ?: start
        val today = Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(DateUtil.timeZone).date
        val d = today.daysUntil(start)
        return when {
            d > 0 -> "D-$d"
            today <= end -> if (d == 0) "오늘 개막" else "진행 중"
            else -> null
        }
    }

    /** 괄호(요일·영문 표기·"(4일)")를 떼고 공백을 정리한다. "11.19 ~ 11.22" 는 "11.19~11.22" 로. */
    private fun shorten(v: String): String =
        v.replace(PAREN, "")
            .replace(YEAR, "")
            .replace(SPACES, " ")
            .trim()
            .replace(" ~ ", "~")

    private companion object {
        val PAREN = Regex("\\([^)]*\\)")
        val YEAR = Regex("(^|\\s)\\d{4}\\.")
        val SPACES = Regex("\\s+")
        /** "2026.11.19(목) ~ …" 앞머리. */
        val DATE_HEAD = Regex("(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})")
        /** "~ 11.22(일)" 꼬리 — 연도는 개막과 같다고 본다. */
        val DATE_TAIL = Regex("~\\s*(\\d{1,2})\\.(\\d{1,2})")
        /** "(4일)" 의 숫자. */
        val DAY_COUNT = Regex("\\((\\d+)일\\)")
        /** 히어로·3칸·칩이 이미 소비하는 라벨 — [otherFacts] 에서 뺀다. */
        val HERO_LABELS = setOf("기간", "장소", "규모", "함께")
    }
}

/**
 * 배너 밑단 한 칸에 들어가는 지스타 조각 — "D-71" · "G-STAR 2026" · "11.19~11.22 · 부산 벡스코".
 * 조립은 [HoyolandGstar.homeBrief] 가 한다(양 플랫폼이 같은 값을 그리게).
 */
data class HoyolandGstarBrief(val dday: String, val title: String, val detail: String)

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
    val gstar: HoyolandGstar,
    val past: List<HoyolandPastEvent>,
    /**
     * 굿즈 품목. **비어 있는 게 정상인 기간이 있다** — 판매 목록은 시간표만큼 늦게 나온다.
     * 그때까지 화면은 "공개 전"이라고 말한다.
     */
    val goods: List<HoyolandGoods> = emptyList(),
    /** 게임별 부스 체험. 위와 같은 이유로 비어 있을 수 있다. */
    val booths: List<HoyolandBooth> = emptyList(),
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
        val nowLocal = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(DateUtil.timeZone)
        val isToday = nowLocal.date.toString() == ymd
        val nowMin = nowLocal.hour * 60 + nowLocal.minute
        val starts = slots.map { minutesOfDay(it.time) }
        return slots.mapIndexed { i, slot ->
            val start = starts[i]
            val nextStart = starts.drop(i + 1).firstOrNull { it != null }
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
            when {
                !isToday || start == null || end == null ->
                    StageSlot(slot, StageState.UPCOMING, rangeLabel = label)
                nowMin >= end -> StageSlot(slot, StageState.DONE, rangeLabel = label)
                nowMin >= start -> StageSlot(
                    slot,
                    StageState.LIVE,
                    remainMin = end - nowMin,
                    progress = if (end > start) (nowMin - start).toFloat() / (end - start) else 0f,
                    rangeLabel = label,
                )
                else -> StageSlot(slot, StageState.UPCOMING, rangeLabel = label)
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

    /** 오늘이 행사 기간 안이면 그 `yyyy-MM-dd`, 아니면 null. */
    @OptIn(ExperimentalTime::class)
    private fun todayYmd(nowMillis: Long): String? {
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(DateUtil.timeZone).date.toString()
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
        // 공개되면 config/hoyoland.json 의 days 를 채우는 것만으로 화면이 찬다(앱 업데이트 불필요).
        days = emptyList(),
        // 2026-09-03 1차 참가사 발표 기준. 부스 규모는 **호요버스를 포함한 100부스**다 —
        // 호요버스 단독 규모로 읽히지 않게 라벨을 "규모"로 둔다.
        gstar = HoyolandGstar(
            title = "G-STAR 2026",
            badge = "호요버스 포함 100부스",
            facts = listOf(
                HoyolandFact("기간", "2026.11.19(목) ~ 11.22(일) (4일)"),
                HoyolandFact("장소", "부산 벡스코(BEXCO)"),
                HoyolandFact("전시", "BTC 11.19 ~ 11.22 · BTB 11.19 ~ 11.21"),
                HoyolandFact("규모", "호요버스 포함 100부스"),
                HoyolandFact("함께", "크래프톤 · 구글플레이 · 웹젠 · 팀42 · 넷이즈게임즈 · 빌리빌리게임즈 · 센추리게임즈"),
                HoyolandFact("스폰서", "크랙(뤼튼) — 게임사가 아닌 AI 기업의 첫 메인 스폰서"),
                HoyolandFact("G-CON", "11.19 ~ 11.20 · 벡스코 · 1,500석 · 주제 '내러티브'"),
            ),
            lineup = listOf(
                HoyolandLineup("젠레스 존 제로", "체험 부스"),
                // 넥서스 아니마·쁘띠플래닛은 앱이 가챠를 다루는 게임이 아니라 GameData 에 없다.
                // 색은 이미 쓰는 다섯(파랑·보라·주황·시안·로즈)과 겹치지 않게 초록 계열로 고른다.
                HoyolandLineup("붕괴: 넥서스 아니마", "한국 첫 오프라인 시연", abbr = "NXA", colorArgb = 0xFF3FBF7FL),
                HoyolandLineup("쁘띠플래닛", "한국 첫 오프라인 시연", abbr = "PP", colorArgb = 0xFF9BC53DL),
                HoyolandLineup("원신", "무대"),
                HoyolandLineup("붕괴: 스타레일", "무대"),
            ),
            url = "https://www.gstar.or.kr/",
            notice = "1차 참가사 명단입니다. 넥슨 · 엔씨 · 넷마블 · 카카오게임즈는 현재 명단에 없고, 최종 명단과 부스 배치도는 9월 중 공개됩니다.",
        ),
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

    /**
     * **개발자 화면 전용** 무대 시간표 목업.
     *
     * 실제 편성이 공개되기 전에도 라이브 카드·게임 레인·필터가 실제로 어떻게 보이는지 확인해야
     * 한다. 그런데 번들 기본값([event])에 넣으면 **사용자 화면에 가짜 일정이 뜬다** — 그래서
     * 여기서만 만들어 [com.gatcha.log.data.api.HoyolandApi] 캐시에 얹는다.
     *
     * 기간을 **오늘부터 4일**로 옮긴다. 실제 개막일(10.2)로 두면 오늘이 행사 기간 밖이라
     * 모든 칸이 '예정'이 되어 라이브 카드를 볼 수 없다.
     *
     * 오늘 편성은 **지금 시각을 기준으로 상대 배치**한다 — 언제 눌러도 진행 중인 무대가 하나
     * 잡히도록. 새벽·심야에 눌러도 시각이 자정을 넘지 않게 기준 시각을 낮 구간으로 당긴다.
     */
    @OptIn(ExperimentalTime::class)
    fun stageMockEvent(nowMillis: Long = currentTimeMillis()): HoyolandEvent {
        val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(DateUtil.timeZone)
        val today = now.date
        val ymd = { d: Int -> today.plus(d, DateTimeUnit.DAY).toString() }
        // 기준 시각 — 상대 배치가 자정을 넘지 않도록 낮 구간(04:00~19:00)으로 당긴다.
        val base = (now.hour * 60 + now.minute).coerceIn(4 * 60, 19 * 60)
        val at = { offset: Int -> HoyolandEvent.hhmm((base + offset).coerceIn(0, 23 * 60 + 59)) }

        return event.copy(
            startYmd = ymd(0),
            endYmd = ymd(3),
            days = listOf(
                // 오늘 — 지금 기준 [지난 2편 · 진행 중 1편 · 남은 3편].
                HoyolandDay(
                    ymd(0),
                    listOf(
                        HoyolandSlot(at(-180), "달빛에 전하는 세레나데", "메인 무대", game = "원신", minutes = 40,
                            cast = "오케스트라 · 성우 3인"),
                        HoyolandSlot(at(-90), "환락, 상상 그 이상으로", "메인 무대", game = "붕괴: 스타레일", minutes = 30,
                            cast = "성우 4인 토크"),
                        HoyolandSlot(at(-12), "구름 너머로 내려앉은 시", "메인 무대", game = "젠레스 존 제로", minutes = 40,
                            cast = "밴드 라이브 · 성우 2인"),
                        HoyolandSlot(at(80), "성유물 세팅 클래스", "서브 무대", game = "원신", minutes = 35),
                        HoyolandSlot(at(170), "개발자 인터뷰", "메인 무대", game = "붕괴: 스타레일", minutes = 30,
                            cast = "개발팀 2인"),
                        HoyolandSlot(at(260), "합동 피날레 스테이지", "메인 무대 · 전 IP 인사", minutes = 20,
                            cast = "전 출연진"),
                    ),
                ),
                // 내일 — 고정 편성(오늘이 아닌 날은 전부 '예정'으로 그려지는지 보는 자리).
                HoyolandDay(
                    ymd(1),
                    listOf(
                        HoyolandSlot("11:00", "포토존 토크", "서브 무대", game = "원신", minutes = 25),
                        HoyolandSlot("14:00", "신규 캐릭터 공개", "메인 무대", game = "붕괴: 스타레일", minutes = 45,
                            cast = "성우 2인"),
                        HoyolandSlot("16:30", "시연 하이라이트", "메인 무대", game = "젠레스 존 제로", minutes = 30),
                    ),
                ),
                // 모레 — 한 게임뿐인 날(게임 탭 줄이 사라지는지 보는 자리).
                HoyolandDay(
                    ymd(2),
                    listOf(
                        HoyolandSlot("12:00", "코스프레 스테이지", "메인 무대", game = "원신", minutes = 40),
                        HoyolandSlot("15:00", "팬 아트 시상", "메인 무대", game = "원신", minutes = 20),
                    ),
                ),
                // 글피 — 길이·게임이 없는 칸(폴백이 어떻게 보이는지 보는 자리).
                HoyolandDay(
                    ymd(3),
                    listOf(
                        HoyolandSlot("종일", "굿즈 부스 운영", "7홀"),
                        HoyolandSlot("13:00", "폐막 인사", "메인 무대"),
                    ),
                ),
            ),
            goods = listOf(
                HoyolandGoods("아크릴 스탠드 (푸리나)", 18000, "원신", "아크릴", note = "1인 2개 한정"),
                HoyolandGoods("아크릴 키링 랜덤", 9000, "원신", "아크릴", note = "8종 중 1종"),
                HoyolandGoods("나선 비경 티셔츠", 39000, "원신", "의류"),
                HoyolandGoods("캐스토리스 인형", 45000, "붕괴: 스타레일", "인형"),
                HoyolandGoods("피노코니 머그컵", 22000, "붕괴: 스타레일", "생활"),
                HoyolandGoods("개척 여행 스티커팩", 8000, "붕괴: 스타레일", "문구"),
                HoyolandGoods("에이전트 후드집업", 89000, "젠레스 존 제로", "의류", note = "S·M·L·XL"),
                HoyolandGoods("호요랜드 2026 아트북", 35000, category = "도서"),
                HoyolandGoods("행사 기념 에코백", 15000, category = "생활"),
                HoyolandGoods("한정 뱃지 세트", 0, "젠레스 존 제로", "아크릴", note = "가격 미정"),
            ),
            booths = listOf(
                HoyolandBooth(
                    game = "원신",
                    title = "나타 시연존",
                    desc = "신규 지역을 현장 PC 로 체험",
                    location = "7홀 A-12",
                    reward = "참여 시 아크릴 뱃지 증정",
                ),
                HoyolandBooth(
                    game = "붕괴: 스타레일",
                    title = "개척 사진관",
                    desc = "캐릭터 배경 앞에서 즉석 사진 촬영",
                    location = "8홀 B-03",
                    reward = "인화 사진 1장",
                ),
                HoyolandBooth(
                    game = "젠레스 존 제로",
                    title = "홀로우 챌린지",
                    desc = "제한 시간 안에 스테이지 클리어",
                    location = "8홀 C-07",
                    reward = "클리어 시 키링 증정",
                ),
                HoyolandBooth(
                    game = "",
                    title = "포토존 · 대형 조형물",
                    desc = "전 IP 합동 포토존",
                    location = "후면광장",
                ),
            ),
        )
    }
}
