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
 *  - **원격**: `config/hoyoland_v2.json` ([com.gatcha.log.data.api.HoyolandApi]) — 앱 업데이트 없이 바뀐다.
 *  - **번들**: [HoyolandDefaults] — 네트워크가 없거나 JSON 이 깨져도 화면이 비지 않게 하는 폴백.
 *
 * 표시 문자열(기간 라벨·D-day)은 전부 이 파일의 파생값으로 만든다. 플랫폼이 각자 조립하면
 * "2026.10.2(금) ~ 10.5(월)" 같은 표기가 또 갈라진다.
 */

/**
 * 행사 진행 단계 — 배지·D-day 문구가 이 값으로 갈린다.
 *
 * 예전에는 `BEFORE / ONGOING / ENDED` 셋이었다. 개막 하루 전([TOMORROW])과 개막 당일([TODAY])은
 * **화면이 달라야 하는 날**인데 각각 BEFORE·ONGOING 에 묻혀 있어 구분할 수가 없었다.
 *
 * ⚠️ 늘어난 만큼 **`== ONGOING` 같은 직접 비교는 위험하다** — 개막 당일이 TODAY 로 갈라져 나가
 * 그 조건이 조용히 false 가 된다. 기간을 묻는 자리는 [isBeforeEvent] · [isEventLive] 를 쓴다.
 * (Swift 는 exhaustive switch 가 아니라 컴파일러가 안 잡아 주므로 더더욱.)
 */
enum class HoyolandPhase {
    /** 개막 이틀 이상 남음. */
    UPCOMING,
    /** 개막 하루 전. */
    TOMORROW,
    /** 개막 당일(= 1일차). */
    TODAY,
    /** 2일차 이후 ~ 폐막일. */
    ONGOING,
    /** 폐막일이 지났다. */
    ENDED,
    /**
     * 다음 회차가 발표만 됐고 **날짜가 아직 없다**(개막일 · 폐막일이 비었거나 못 읽는다).
     * 정상 상태다 — 지난 회차가 끝나고 다음 회차 일정이 나오기 전까지 늘 이 자리다.
     * 셀 날짜가 없으니 D-day · 게이지 · 예매 · 알림이 전부 서지 않는다.
     */
    TBA;

    /**
     * 행사 기간 바깥이라 **할 일이 없는 단계**(종료 · 일정 미정). 예매 · 내 입장권 · 액션 줄 ·
     * 알림이 이 값 하나로 같이 빠진다.
     */
    val isOffSeason: Boolean get() = this == ENDED || this == TBA

    /** 개막 전인가 — 옛 `BEFORE` 자리. */
    val isBeforeEvent: Boolean get() = this == UPCOMING || this == TOMORROW

    /** 행사 기간 중인가 — 옛 `ONGOING` 자리. */
    val isEventLive: Boolean get() = this == TODAY || this == ONGOING
}

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
 * 행사장 배치도의 **구역 한 칸** — 공식 BOOTH MAP 을 앱에서 다시 그리는 단위.
 *
 * 좌표를 **비율(0~100)** 로 두는 이유: 화면 폭이 기기마다 다르고 가로/세로도 바뀌는데, px 로
 * 두면 배치도가 기기마다 깨진다. 비율이면 어떤 폭에서도 같은 그림이 나온다.
 *
 * 공식 배치도는 이미지 한 장으로만 공개된다. 그걸 그대로 띄우면 확대해서 보는 것 말고는 할 수
 * 있는 게 없는데, 구역을 **데이터로** 들고 있으면 누른 구역의 굿즈·부스·무대로 곧장 갈 수 있다
 * (앱이 그 목록을 이미 들고 있다). 지도가 목록의 입구가 되는 셈이다.
 *
 * @param id 구역 식별자. [kind] 가 같은 구역이 여럿일 때 구분한다(입장 접수 좌/우).
 * @param label 화면에 쓰는 이름.
 * @param kind `goods` · `stage` · `booth` · `food` · `game` · `entry` · `etc`.
 *   누르면 어디로 가는지와 색이 이 값으로 갈린다.
 * @param game [kind] 가 `game` 일 때 그 게임 이름 — 색을 게임색으로 칠하고 목록을 그 게임으로 좁힌다.
 * @param x 왼쪽 위 X(0~100). @param y 왼쪽 위 Y(0~100).
 * @param w 폭(0~100). @param h 높이(0~100).
 * @param accent true 면 강조 구역(공식 배치도에서 흰 면으로 띄운 칸).
 */
data class HoyolandMapZone(
    val id: String,
    val label: String,
    val kind: String = "etc",
    val game: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
    val accent: Boolean = false,
) {
    /** 그릴 수 있는 칸인가 — 폭이나 높이가 0 이면 화면에 자리를 못 잡는다. */
    val isDrawable: Boolean get() = w > 0f && h > 0f && label.isNotBlank()
}

/**
 * 행사장 배치도 — 구역 목록 + 안내 문구.
 *
 * 비어 있으면 **「맵스」 칸이 통째로 사라진다.** 배치도는 개막 몇 주 전에야 공개된다.
 */
data class HoyolandMap(
    val title: String = "",
    val note: String = "",
    /**
     * 배치도 판의 **가로÷세로** 비율. 공식 도면이 가로로 길어 기본 1.64 다.
     *
     * 좌표가 비율이라 판의 비율까지 맞아야 도면이 안 눌린다 — 정사각 판에 그리면 가로로 긴
     * 도면이 세로로 늘어난다.
     */
    val ratio: Float = 1.64f,
    val zones: List<HoyolandMapZone> = emptyList(),
) {
    val isEmpty: Boolean get() = zones.none { it.isDrawable }

    /** 그릴 수 있는 구역만 — 좌표가 빠진 줄은 조용히 버린다. */
    val drawable: List<HoyolandMapZone> get() = zones.filter { it.isDrawable }
}

/**
 * 입장 조 한 줄 — 예매할 때 고르는 **회차**다(2026 은 A~F 여섯 조).
 *
 * 조마다 입장 시각이 다른데, 지금까지 이 값은 예매 안내문 **문장 속에만** 있었다
 * (`· A·B조 — 오전 10시`). 글로만 있으면 「내 입장권」이 내 조 시각을 짚어 줄 수가 없어
 * config 에 구조로 따로 둔다 — 안내문은 읽는 자리로 그대로 남는다.
 *
 * @param name 조 이름. `A` 처럼 **조 글자만** 담는다("A조"가 아니다 — 붙이는 건 화면 몫이다).
 * @param time 입장 시각 `10:00`. 비어 있어도 조는 고를 수 있다(시각만 안 보인다).
 */
data class HoyolandEntryGroup(val name: String, val time: String = "")

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
    /**
     * 그 게임의 행사 공지 주소(카페 글·하나링크 등). 비면 칩이 눌리지 않는다.
     *
     * 공지는 **게임마다 다른 곳에 올라온다** — 원신·스타레일은 네이버 카페, 젠레스는 게임라운지,
     * 붕괴3rd·미해결사건부는 hoyo.link 다. 앱이 하나의 공식 주소([HoyolandEvent.officialUrl])만
     * 들고 있으면 "내 게임 공지" 로는 못 간다. 참여 게임 칩이 이미 게임별로 서 있으므로
     * 그 칩을 그대로 문으로 쓴다.
     */
    val url: String = "",
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
    /**
     * 푸드 메뉴 사진 — **메뉴 줄 이름 → 파일 경로**(`food/hsr-06.webp`).
     *
     * 메뉴는 설명글(`· 이름 — 7,000원`)에만 있고 항목 필드가 없어서, 사진을 줄 이름에 건다.
     * 설명글 모양을 바꾸지 않으므로 이 칸을 모르는 옛 빌드는 지금 화면 그대로다.
     */
    val menuImages: Map<String, String> = emptyMap(),
) {
    /** 메뉴 줄 이름에 걸린 사진의 전체 주소. 없으면 빈 문자열. */
    fun menuImageUrl(name: String): String = hoyolandAssetUrl(menuImages[name.trim()].orEmpty())

    /**
     * 푸드존 줄인가 — 제목이 「푸드」로 시작하거나(푸드존 · 푸드트럭), **메뉴 사진이 걸려 있다**.
     *
     * 메뉴 사진은 어드민의 푸드존 탭에서만 걸 수 있어, 그 줄이 푸드존 탭에서 만든 것이라는 표시가 된다(10/6).
     * 제목만 보던 때는 제목 머리가 빠진 문서(「푸드존 — 원신」 → 「원신」) 하나로 메뉴판이 통째로 프로그램 섹션
     * (「행사 구성」)에 섰다. 어드민 `isFoodProgram` 과 **같아야 한다**.
     */
    val isFood: Boolean get() = title.startsWith("푸드") || menuImages.isNotEmpty()
}

/**
 * 호요랜드 사진 파일 경로 → 전체 주소.
 *
 * 사진은 `config/hoyoland_v2.json` 옆(`config/goods/` · `config/food/`)에 두고 **정본과 같은 raw 주소**로
 * 읽는다. JSON 에는 짧은 상대 경로만 적는다 — 저장소 주소가 바뀌면 여기 한 곳만 고친다.
 * `http` 로 시작하면 외부 주소로 보고 그대로 쓴다. 비면 빈 문자열(= 사진 없음).
 */
fun hoyolandAssetUrl(path: String): String {
    val p = path.trim()
    if (p.isEmpty()) return ""
    if (p.startsWith("http://") || p.startsWith("https://")) return p
    return HOYOLAND_ASSET_BASE + p.removePrefix("/")
}

const val HOYOLAND_ASSET_BASE = "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/"

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
    /**
     * 예매처 앱의 안드로이드 패키지명(예: 티켓링크 `kr.co.ticketlink.cne`). 비면 평소대로 연다.
     *
     * [url] 을 **이 앱으로 먼저** 보내 본다. 커스텀 스킴을 쓰지 않는 이유는 공개 문서가 없어
     * 지어내야 하는데, 틀리면 앱이 깔려 있어도 영영 안 열리기 때문이다. 패키지만 지정하면
     * 앱이 자기 주소로 등록해 둔 화면을 스스로 고르고, 못 고르면 브라우저로 떨어진다.
     *
     * iOS 에는 대응 값이 없다 — 유니버설 링크라 같은 https 주소를 열면 시스템이 앱으로 보낸다.
     */
    val appPackage: String = "",
    /**
     * 예매처 앱의 **커스텀 스킴** 주소(예: `ticketlink://product/65564`). 비면 평소대로 웹으로 연다.
     *
     * iOS 전용이다. 안드로이드는 [appPackage] 로 같은 https 주소를 앱에 보내면 되지만, iOS 에는
     * 그런 방법이 없고 **유니버설 링크뿐인데 티켓링크는 그걸 지원하지 않는다**
     * (2026-09-13 확인 — `/.well-known/apple-app-site-association` 404). 그래서 https 주소로는
     * 앱이 절대 안 열리고, 스킴 말고는 길이 없다.
     *
     * **지금은 비어 있다.** 공개 문서가 없고 기기·페이지·JS 번들 어디에서도 찾지 못했다.
     * 지어내면 틀려도 티가 안 나므로(앱이 있어도 조용히 웹으로 떨어진다) 확인된 값만 넣는다.
     * 채우면 앱 업데이트 없이 바로 동작한다 — 받아 줄 앱이 없으면 웹으로 되돌아간다.
     */
    val appScheme: String = "",
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
    /**
     * 끝나는 시각(자정부터의 분). 못 읽으면 0.
     *
     * [rangeLabel] 에 이미 글자로 들어 있지만, **내 입장 시각과 견주려면 숫자가 필요하다**
     * ([HoyolandEvent.entryMinutesOn]). 화면에서 다시 세면 여기 끝 시각 규칙(길이 없으면
     * 다음 편 시작까지)을 두 번 구현하게 된다.
     */
    val endMin: Int = 0,
) {
    /**
     * 내가 들어가기 **전에 이미 끝나는** 편인가 — [entryMin] 은 그날 내 입장 시각(분).
     *
     * 시작이 아니라 **끝**을 본다. 시작으로 가르면 입장하는 순간 진행 중인 편까지 못 보는
     * 것으로 접히는데, 그건 늦게라도 들어가서 뒷부분을 볼 수 있는 편이다.
     */
    fun isBeforeEntry(entryMin: Int): Boolean = entryMin > 0 && endMin in 1..entryMin
}

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
    /** 사진 파일 경로(`goods/hsr-036.webp`). 없으면 빈 문자열 — 화면은 게임 자리표시로 그린다. */
    val image: String = "",
) {
    /** 사진의 전체 주소([hoyolandAssetUrl]). 없으면 빈 문자열. */
    val imageUrl: String get() = hoyolandAssetUrl(image)

    /**
     * 같은 물건의 다른 디자인을 묶는 이름 — "아크릴 스탠드 - 펄" 의 "아크릴 스탠드".
     *
     * 크게 보기에서 형제 디자인을 좌우로 넘길 때 쓴다. 공식 표가 디자인을 " - " 뒤에 붙여 내고,
     * 캐릭터별 상품("봉제인형 키링 - 종려")도 같은 꼴이라 **같은 게임 · 같은 가격**까지 맞아야 형제로 본다.
     */
    val designGroup: String get() = name.substringBeforeLast(" - ").trim()

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
 * 예약 필드는 두지 않는다 — **호요랜드 부스는 예약제가 없다**(2026-09-10 확인 ·
 * 2026-09-13 공식 체험존 페이지 3장에서 재확인).
 * 있지도 않은 갈래를 화면에 세우면 "예약이 있는 곳도 있나" 로 읽힌다.
 *
 * **정원 · 회차 필드도 두지 않는다**(2026-09-10 확인 · 2026-09-13 재확인). 부스는 회차로 끊어
 * 돌리지 않아서 "회차당 몇 명" 이 성립하지 않고, 현장이 지금 얼마나 붐비는지는 알 길도 없다.
 * 공식 페이지가 내는 제약도 참여 횟수("연속 2회")·수령 횟수("1인 1회")뿐이고 둘 다 desc·reward
 * 로 들어간다. 이 화면은 공지된 정보를 그대로 옮기는 자리다.
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
    /**
     * 로고 이미지 경로(저장소 `config/` 기준, 예: "partner/googleplay.png") — 파트너사 부스가 이름 옆에 쓴다.
     * 없으면 빈 문자열. 옛 빌드는 모르는 칸을 무시한다.
     */
    val logo: String = "",
) {
    /** 로고의 전체 주소([hoyolandAssetUrl]). 없으면 빈 문자열. */
    val logoUrl: String get() = hoyolandAssetUrl(logo)

    /** 참가비가 있는 부스인가 — 화면이 값 대신 이 술어로 갈린다. */
    val isPaid: Boolean get() = price > 0

    /**
     * DIY존 자리인가 — 소속 게임 없이 「DIY존」에 선 부스. 부스 페이지의 **DIY 탭**이 모은다.
     * 게임 체험과 성격이 달라(사서 만드는 곳) 게임 탭 · 전체 목록에서 빼고 따로 읽힌다.
     */
    val isDiy: Boolean get() = game.isBlank() && location.contains("DIY")

    /**
     * 파트너사 부스인가 — 호요버스가 아니라 제휴사(구글 플레이 · 갤럭시 스토어)가 여는 자리.
     * 위치를 「파트너사 · 제2전시장 8홀」 꼴로 적는다. 부스 페이지의 **파트너사 탭**이 모은다.
     */
    val isPartner: Boolean get() = game.isBlank() && location.startsWith("파트너사")

    /** 파트너사 부스의 실제 위치 — 위치 칸에서 「파트너사 · 」 머리를 뗀 값("제2전시장 8홀"). */
    val partnerHall: String get() = location.removePrefix("파트너사").trimStart(' ', '·').trim()
}

/** 지난 행사 1건 — 다음 행사 규모를 가늠하는 참고 자료. 보관본이 있는 회차는 상세도 열 수 있다. */
data class HoyolandPastEvent(val title: String, val facts: List<HoyolandFact>) {
    /**
     * 회차 키 — 제목에서 읽은 연도("호요랜드 2026" → "2026"). 못 읽으면 빈 문자열.
     *
     * 어드민이 회차를 가르는 방식과 같다(회차 = 연도, 보관본 = `config/hoyoland/editions/{연도}.json`).
     * 지난 행사 항목에 키 칸을 따로 두지 않는다 — 제목과 키가 따로 놀 자리를 만들지 않으려는 것이고,
     * 옛 빌드가 읽는 문서 모양도 그대로다. 보관본이 **실제로 있는지**는 [com.gatcha.log.data.api.HoyolandApi.archiveKeyOf] 가 가른다.
     */
    val editionYear: String get() = YEAR.find(title)?.value.orEmpty()

    private companion object {
        val YEAR = Regex("20\\d{2}")
    }
}

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
     * 프로그램 섹션의 제목. 비면 「응모 · 특전」([programSectionTitle]).
     *
     * 지금 회차의 그 섹션에는 미리 신청하거나 받는 것(전시존 · 웰컴 키트)만 남아 그렇게 부른다.
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

    /** 프로그램 섹션 제목 — [programsTitle] 이 비면 「응모 · 특전」. */
    val programSectionTitle: String get() = programsTitle.ifBlank { "응모 · 특전" }

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
            .toLocalDateTime(DateUtil.timeZone).date   // 캐시된 타임존(시스템 조회 우회 금지)

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
     * 개막일이 회차마다 다르고 어드민이 손으로 고칠 일도 없어 이걸 쓴다. 비면 행사명.
     */
    val editionKey: String get() = startYmd.ifBlank { edition }

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
        if (savedEdition == editionKey) cart else HoyolandCart()

    /** 저장된 입장권을 이 회차로 읽는다 — 다른 회차 것이면 **기간 안 날짜만** 남긴다. */
    fun entryForEdition(entry: HoyolandEntry, savedEdition: String): HoyolandEntry {
        if (savedEdition == editionKey) return entry
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
        if (goods.isEmpty()) return "판매 목록 공개 전"
        val picked = cartLines(cart)
        // 지난 회차는 담을 수 없다 — 종수만 말한다.
        if (picked.isEmpty()) return if (archived) "${goods.size}종" else "${goods.size}종 · 아직 안 담았어요"
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

    /** 푸드존을 뺀 나머지 프로그램 — 프로그램 섹션이 쓴다. */
    val otherPrograms: List<HoyolandProgram>
        get() = programs.filterNot { it.isFood }

    /**
     * 푸드존 입구 줄 — "3곳 · 3,000원 ~ 13,500원".
     *
     * 값은 메뉴 본문에서 긁는다. 가격은 원격 config 의 설명 글에만 있고 별도 필드가 없다 —
     * 굿즈처럼 숫자 필드로 올리면 메뉴가 바뀔 때마다 두 군데를 고쳐야 한다.
     *
     * **메뉴 줄(`· 이름 — 7,000원`)만 센다.** 설명 글 전체에서 "원"을 긁으면 "(우유·펄 추가
     * 시 1,000원)" · "따로 사면 13,000원" 같은 문장 속 숫자가 섞여 최저가가 1,000원이 됐다.
     * 화면이 값으로 인정하는 줄([HoyolandRichText] 규칙)과 같은 것만 본다.
     */
    fun foodEntryLine(): String {
        val list = foodPrograms
        if (list.isEmpty()) return "푸드존 정보 공개 전"
        val prices = list.flatMap { p ->
            p.desc.split("\n")
                .filter { it.startsWith("· ") && " — " in it }
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
        date?.atTime(hour, 0)?.toInstant(DateUtil.timeZone)?.toEpochMilliseconds() ?: 0L

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

        /** 마감 안내 속 "9.13" — 앞뒤에 숫자 · 점이 붙은 것(1.25배 · 2026.9.13)은 건너뛴다. */
        private val MonthDayRegex = Regex("""(?<![\d.])(\d{1,2})\.(\d{1,2})(?![\d.])""")

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


/** 여러 줄 글의 한 덩이가 무엇인가 — [HoyolandText.lines] 가 가른다. */
enum class HoyolandTextKind {
    /** 문단 — 이어진 여러 줄이 한 덩이다(줄바꿈이 들어 있다). */
    PARA,
    /** 목록 줄 — 「· 항목」. [HoyolandTextLine.level] 은 들여쓴 깊이다(0 부터) — 점 모양이 깊이마다 다르다([HoyolandText.dot]). */
    ITEM,
    /** 번호 줄 — 「1. 항목」. 번호는 [HoyolandTextLine.mark]("1.")에, 깊이는 [HoyolandTextLine.level] 에 있다. */
    NUM,
    /** 목록 줄에 딸린 부연 — 들여쓴 줄. [HoyolandTextLine.level] 은 들여쓴 깊이라, 한 단 들이면 위 항목의 글자 아래에 선다. */
    SUB,
    /** 빈 줄. */
    BLANK,
}

data class HoyolandTextLine(val kind: HoyolandTextKind, val text: String, val level: Int = 0, val mark: String = "")

/**
 * 어드민에서 적는 **여러 줄 글의 목록 규칙**(10/6) — 설명 · 메뉴 · 공지 · 안내에 공통이다.
 *
 * 목록 줄의 머리는 가운뎃점 + 띄어쓰기(`· `)다. 어드민의 글 칸은 줄 머리에 `- ` · `* ` · `+ ` 를 치면
 * 가운뎃점으로 바꿔 주지만(ui.js `glArea`), 붙여넣은 글이나 손으로 고친 JSON 에는 마크다운 머리가 그대로
 * 남을 수 있다. 그래서 **읽을 때 한 번 더** 가운뎃점으로 맞춘다([normalize]) — 그 뒤의 화면 규칙
 * (예매 안내 · 푸드 메뉴 · 굿즈존 안내)은 가운뎃점 하나만 알면 된다.
 */
object HoyolandText {
    /** 목록 줄의 머리 — **저장되는 글자**. 어드민 `GL_BULLET` 과 같다(U+00B7 + 띄어쓰기). */
    const val BULLET = "· "

    /**
     * 화면에 그리는 목록 점 — 노션처럼 **굵은 점**(U+2022)이다(10/6 지시). 저장되는 글자는 가운뎃점 그대로다 —
     * 가운뎃점은 작아서 목록으로 안 읽힌다. 깊이가 없는 자리(예매 안내의 항목 줄)가 쓴다.
     */
    const val DOT = "•"

    /**
     * 깊이마다 다른 목록 점 — 굵은 점 → 빈 동그라미 → 네모, 그 아래는 다시 처음부터(10/6 지시, 노션과 같다).
     * 어드민 글 칸이 보여 주는 점(`GL_BULLETS`)과 같은 글자 · 같은 순서다. 어느 것이든 저장되는 글자는 가운뎃점이다.
     */
    private val DOTS = listOf(DOT, "◦", "▪")

    /** 그 깊이의 목록 점. */
    fun dot(level: Int): String = DOTS[level.coerceAtLeast(0) % DOTS.size]

    /**
     * 들여쓰기의 깊이 — 탭 하나, 또는 띄어쓰기 셋까지가 한 단이다. 띄어쓰기 하나는 들여쓰기가 아니다.
     * 어드민 `glDepth` 와 **같아야 한다** — 어긋나면 어드민에서 본 점과 앱의 점이 달라진다.
     */
    fun depth(line: String): Int {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val tabs = indent.count { it == '\t' }
        val spaces = indent.length - tabs
        return tabs + if (spaces >= 2) (spaces + 2) / 3 else 0
    }

    // 마크다운 머리(- * +)와 **화면용 점**(• ∘ ◦ ▪). 화면용 점은 어드민 글 칸이 보여 주는 글자라 값에는 없어야 하지만,
    // 손으로 고친 JSON 에 섞여 들어오면 가운뎃점 줄과 똑같이 목록으로 읽는다(어드민 `GL_BULLET_ANY`).
    private const val MARKS = "-*+•∘◦▪"
    private val MD_BULLET = Regex("""^([ \t]*)[-*+•∘◦▪] (.*)$""")

    /** 번호 줄 — 「1. 항목」. 세 자리까지만 번호로 본다(「2026. 10. 2.」 같은 날짜로 시작하는 줄을 번호로 읽지 않는다). */
    private val NUM_LINE = Regex("""^(\d{1,3})\. (.+)$""")

    /** 줄 머리의 `- ` · `* ` · `+ ` 와 화면용 점을 `· ` 로 — 들여쓰기는 그대로 둔다. 줄 가운데의 것(「10:00 - 11:00」)은 안 건드린다. */
    fun normalize(text: String): String {
        if (text.none { it in MARKS }) return text
        return text.split("\n").joinToString("\n") { line ->
            MD_BULLET.matchEntire(line)?.let { m -> m.groupValues[1] + BULLET + m.groupValues[2] } ?: line
        }
    }

    /** 목록 줄 · 번호 줄이 하나라도 있는가 — 없으면 화면은 글을 예전처럼 통째로 그린다. */
    fun hasList(text: String): Boolean =
        text.split("\n").any { it.trim().let { b -> b.startsWith(BULLET) || NUM_LINE.matches(b) } }

    /**
     * 글을 화면이 그릴 덩이로 가른다. 위에서부터 먼저 맞는 것:
     *  - 빈 줄 → [HoyolandTextKind.BLANK]. 목록이 여기서 끝난다.
     *  - `· …` → 목록 줄. 들여쓴 깊이([depth])가 단이다 — 단마다 점 모양이 다르다([dot]).
     *  - `1. …` → 번호 줄. 깊이는 목록 줄과 같이 센다.
     *  - 목록 안의 들여쓴 줄 → 위 항목의 부연. 들여쓴 깊이만큼 들어간다.
     *  - 그 외 → 문단. 이어진 줄은 한 덩이로 묶는다.
     * 맨 앞 · 맨 뒤의 빈 줄은 버린다.
     */
    fun lines(text: String): List<HoyolandTextLine> {
        val out = mutableListOf<HoyolandTextLine>()
        val para = mutableListOf<String>()
        var inList = false
        fun flush() {
            if (para.isNotEmpty()) { out += HoyolandTextLine(HoyolandTextKind.PARA, para.joinToString("\n")); para.clear() }
        }
        for (raw in text.split("\n")) {
            val line = raw.trimEnd()
            val body = line.trim()
            val depth = if (body.isEmpty()) 0 else depth(line)
            when {
                body.isEmpty() -> { flush(); out += HoyolandTextLine(HoyolandTextKind.BLANK, ""); inList = false }
                body.startsWith(BULLET) -> {
                    flush()
                    out += HoyolandTextLine(HoyolandTextKind.ITEM, body.removePrefix(BULLET).trim(), depth)
                    inList = true
                }
                NUM_LINE.matches(body) -> {
                    flush()
                    val m = NUM_LINE.matchEntire(body)!!
                    out += HoyolandTextLine(HoyolandTextKind.NUM, m.groupValues[2].trim(), depth, mark = m.groupValues[1] + ".")
                    inList = true
                }
                depth > 0 && inList -> out += HoyolandTextLine(HoyolandTextKind.SUB, body, depth)
                else -> { para += line; inList = false }
            }
        }
        flush()
        return out.dropWhile { it.kind == HoyolandTextKind.BLANK }.dropLastWhile { it.kind == HoyolandTextKind.BLANK }
    }
}

/**
 * **앱에 내장한 지난 회차 상세**(2025 · 2024) — 「지난 행사 ▸ 상세 보기」가 연다.
 *
 * 어드민으로 운영한 회차는 2026 부터다. 그 전 회차는 여기에 박고(10/6), **같은 내용을 저장소에도 둔다** —
 * `config/hoyoland/editions/2025.json` · `2024.json`. 앱은 원격 문서를 먼저 읽고 못 받았을 때 이 값을 쓴다
 * ([com.gatcha.log.data.api.HoyolandApi.loadArchive]). 내용을 고칠 때는 어드민(원격)에서 고치면 되고, 여기는 오프라인용이다.
 *
 * **확인된 값만 적는다.** 출처: 킨텍스 행사 안내 · 티켓링크 예매 공지 · 호요버스 보도자료(상세 공개 · 성료),
 * 일자별 무대 편성과 구역 이름은 나무위키 「호요랜드 2024」·「호요랜드 2025」. 출처끼리 어긋난 값은 공식 쪽을 따랐고
 * (2024 입장료 13,000원), 끝내 못 맞춘 값(2025 요일별 운영 시간 · 일자별 무대 편성 · 굿즈 · 푸드 · 배치도)은 비웠다 —
 * 빈 칸은 화면이 섹션째 뺀다. 굿즈 · 부스 · 푸드 · 배치도는 두 회차 모두 없다.
 */
object HoyolandArchives {

    private const val KINTEX2 = "일산 킨텍스 제2전시장"
    private const val KINTEX2_ADDRESS = "경기도 고양시 일산서구 킨텍스로 217-60"
    private const val KINTEX2_MAP =
        "https://map.naver.com/p/search/%ED%82%A8%ED%85%8D%EC%8A%A4%20%EC%A0%9C2%EC%A0%84%EC%8B%9C%EC%9E%A5"
    private const val KINTEX2_MAP_FALLBACK =
        "https://www.google.com/maps/search/%EC%9D%BC%EC%82%B0+%ED%82%A8%ED%85%8D%EC%8A%A4+%EC%A0%9C2%EC%A0%84%EC%8B%9C%EC%9E%A5"

    // 붕괴3rd · 미해결사건부는 GameData 에 없는 게임이라 약칭 · 색을 직접 준다(2026 보관본과 같은 값).
    private fun hi3(theme: String) = HoyolandLineup("붕괴3rd", theme, abbr = "HI3", colorArgb = 0xFF30C6E8)
    private fun tot(theme: String) = HoyolandLineup("미해결사건부", theme, abbr = "ToT", colorArgb = 0xFFE0557B)

    private fun slot(time: String, title: String, game: String = "", cast: String = "") =
        HoyolandSlot(time = time, title = title, game = game, minutes = 60, cast = cast)

    private val y2025 = HoyolandEvent(
        edition = "호요랜드 2025",
        editionEn = "HOYOLAND 2025",
        startYmd = "2025-10-09",
        endYmd = "2025-10-12",
        venueName = KINTEX2,
        venueHall = "9·10홀(실내) · 후면광장(야외)",
        venueAddress = KINTEX2_ADDRESS,
        mapUrl = KINTEX2_MAP,
        mapFallbackUrl = KINTEX2_MAP_FALLBACK,
        officialUrl = "",
        announceYmd = "",
        ticket = HoyolandTicket(
            status = HoyolandTicketStatus.SOLD_OUT,
            vendor = "티켓링크",
            openLabel = "9월 22일(월) 19:00",
            priceLabel = "29,000원 (예매수수료 별도)",
        ),
        lineup = listOf(
            HoyolandLineup("원신", "나타가 부른다, 준비됐나?"),
            HoyolandLineup("붕괴: 스타레일", "이름 없는 기억의 축가"),
            HoyolandLineup("젠레스 존 제로", "뉴에리두 특별수행"),
            hi3("이상한 나라의 다과회"),
            tot("달콤달콤 메모리"),
        ),
        // 게임부터 — 줄 제목이 게임 이름이다. 구역 이름은 위 라인업이 말하므로 여기엔 한 일만 적는다.
        programs = listOf(
            HoyolandProgram("원신", "· 파도수련 · 명중수련 체험"),
            HoyolandProgram("붕괴: 스타레일", "· 레이저 미션\n· OST 퀴즈"),
            HoyolandProgram("젠레스 존 제로", "· 미니게임 「달려라 BANGBOO!」"),
            HoyolandProgram(
                "붕괴3rd",
                "· 동화 속 다과회 콘셉트의 야외 부스(후면광장)\n· 입장권은 윗치폼에서 따로 팔았어요",
                deadline = "별도 입장권 9.23(화) 19:00 판매 시작",
            ),
            HoyolandProgram("미해결사건부", "· 3m 크기 풍선 인형(후면광장)"),
            HoyolandProgram(
                "메인 무대",
                "· 원신 라이브 인 티바트 무지갯빛 투어 호요랜드 편\n" +
                    "· 선율이 흐르는 음악회\n" +
                    "· Star Symphony at 호요랜드\n   붕괴: 스타레일 OST 참여 가수 Chevy · NIDA\n" +
                    "· 코스프레 런웨이\n   진행 도티 · 샘웨 · 레나\n" +
                    "· 일러스트 퀴즈쇼",
            ),
            HoyolandProgram(
                "웰컴 키트",
                "예매할 때 고른 게임의 것을 받았어요.\n" +
                    "· 고른 게임의 한정 굿즈 1종\n   원신 2026 달력 · 붕괴: 스타레일 티셔츠 · 젠레스 존 제로 누들스토퍼\n" +
                    "· 그 게임의 리딤코드\n· 홀로그램 티켓\n· 행사 리플릿\n· 갤럭시 스토어 쿠폰\n· 부직포백",
            ),
            HoyolandProgram("부대 시설", "· 굿즈존\n· 푸드존\n· 창작전시존 & DIY존"),
            HoyolandProgram(
                "파트너사 부스",
                "· 갤럭시 스토어\n   갤럭시 AI존을 따로 운영\n· 플레이스테이션\n· 맘스피자\n   야외 · 붕괴: 스타레일 콜라보 메뉴",
            ),
        ),
        notice = "",
        days = emptyList(),
        past = emptyList(),
    )

    private val y2024 = HoyolandEvent(
        edition = "호요랜드 2024",
        editionEn = "HOYOLAND 2024",
        startYmd = "2024-10-31",
        endYmd = "2024-11-03",
        venueName = KINTEX2,
        venueHall = "7·8홀(실내) · 후면광장(야외)",
        venueAddress = KINTEX2_ADDRESS,
        mapUrl = KINTEX2_MAP,
        mapFallbackUrl = KINTEX2_MAP_FALLBACK,
        officialUrl = "",
        announceYmd = "",
        ticket = HoyolandTicket(
            status = HoyolandTicketStatus.SOLD_OUT,
            vendor = "티켓링크",
            openLabel = "10월 14일(월) 20:00",
            priceLabel = "13,000원",
        ),
        lineup = listOf(
            HoyolandLineup("원신", "연극! 추리? 폰타인 탐정단"),
            HoyolandLineup("붕괴: 스타레일", "【꿈세계로의 초대】 ~ 황금의 순간~"),
            HoyolandLineup("젠레스 존 제로", "뉴에리두 가든파티"),
            hi3("Welcome! 붕괴학당!"),
            tot("화려한 밤의 축제"),
        ),
        programs = listOf(
            HoyolandProgram(
                "원신",
                "· 무대 — 라이브 퀴즈쇼 · 도전 원신벨 · 폰타인 성우 토크쇼 · 미니 콘서트\n· 현장 이벤트 「가을 축제 여행자 긴급 체포」",
            ),
            HoyolandProgram(
                "붕괴: 스타레일",
                "· 무대 — 밴드공연(with Chevy) · 리듬게임 빅매치 · 뭇별의 워프: 가차타임\n· XR 체험 「페니코니 몰입형 체험」",
            ),
            HoyolandProgram(
                "젠레스 존 제로",
                "· 무대 — 노토리우스 사냥 · 릴레이 그리기 · Bangboo는 알고 있다!\n· 미니게임 — 사격 「별빛 기사」 · 볼링 「휠 스트라이크!」\n· 그래피티 공간",
            ),
            HoyolandProgram("붕괴3rd", "· 무대 — 미니콘서트 · 붕괴학당 2024 공개수업"),
            HoyolandProgram(
                "미해결사건부",
                "· 주말 이틀 후면광장 야외 전시장에서 따로 운영\n· 입장권은 윗치폼에서 따로 팔았어요",
                deadline = "11.2(토) · 11.3(일)",
            ),
            HoyolandProgram(
                "코스프레 퍼레이드 · 드론쇼",
                "주말 이틀 저녁, 야외 후면광장에서 열렸어요.\n드론쇼는 원신 · 붕괴: 스타레일을 주제로 약 10분.",
                deadline = "11.2(토) · 11.3(일)",
            ),
            HoyolandProgram(
                "웰컴 키트",
                "· 부직포백\n· 가이드북\n· 리딤코드\n· 갤럭시 스토어 쿠폰\n· 티켓",
            ),
            HoyolandProgram("부대 시설", "· 굿즈존\n· 푸드존(게임 테마 푸드트럭)\n· 2차 창작 부스\n· DIY존"),
            HoyolandProgram(
                "파트너사",
                "· 갤럭시 스토어\n· 소니인터랙티브엔터테인먼트(플레이스테이션)\n· 호요크리에이터\n· 하나카드\n· IPX\n· 스냅드래곤\n· 달콤커피",
            ),
        ),
        notice = "",
        // 메인 무대 — 나흘 모두 네 편(각 60분).
        days = listOf(
            HoyolandDay("2024-10-31", listOf(
                slot("11:00", "코스프레 런웨이"),
                slot("12:30", "라이브 퀴즈쇼", "원신"),
                slot("14:00", "노토리우스 사냥", "젠레스 존 제로"),
                slot("15:30", "뭇별의 워프: 가차타임", "붕괴: 스타레일"),
            )),
            HoyolandDay("2024-11-01", listOf(
                slot("11:00", "별과 심연을 향해 기원 이벤트", "원신"),
                slot("12:30", "릴레이 그리기", "젠레스 존 제로"),
                slot("14:00", "리듬게임 빅매치", "붕괴: 스타레일"),
                slot("15:30", "도전 원신벨", "원신"),
            )),
            HoyolandDay("2024-11-02", listOf(
                slot("11:00", "밴드공연", "붕괴: 스타레일", cast = "Chevy"),
                slot("12:30", "폰타인 성우 토크쇼", "원신"),
                slot("14:00", "무대 이벤트", "젠레스 존 제로"),
                slot("15:30", "미니콘서트", "붕괴3rd"),
            )),
            HoyolandDay("2024-11-03", listOf(
                slot("11:00", "Bangboo는 알고 있다!", "젠레스 존 제로"),
                slot("12:30", "붕괴학당 2024 공개수업", "붕괴3rd"),
                slot("14:00", "미니 콘서트", "원신"),
                slot("15:30", "밴드공연", "붕괴: 스타레일", cast = "Chevy"),
            )),
        ),
        past = emptyList(),
    )

    /** 회차 키("2025") → 내장 상세. */
    val bundled: Map<String, HoyolandEvent> = mapOf("2025" to y2025, "2024" to y2024)
}

/**
 * 번들 폴백 — **2027 회차 · 일정 미정**(2026-10-06 전환).
 *
 * 2026 회차(10.2~10.5)가 끝나 지난 행사 목록 맨 앞으로 옮겼다. 2027 은 아직 날짜도 장소도 없어서
 * 행사명과 지난 행사만 둔다 — 날짜가 비면 [HoyolandPhase.TBA] 로 읽혀 D-day · 예매 · 알림이 서지 않는다.
 * 원격 JSON 이 이 값을 덮어쓰므로, 여기는 "네트워크 없이 앱을 처음 켰을 때 보여도 틀리지 않은 내용"만 둔다.
 *
 * 라인업 · 조 편성 · 배치도는 **비워 둬야 한다.** 원격에서 빈 배열이 오면 번들로 폴백하는 값이라
 * ([HoyolandApi] 파서), 여기에 2026 값이 남아 있으면 2027 화면에 작년 라인업이 되살아난다.
 */
object HoyolandDefaults {

    /**
     * 보관본(상세)이 있는 지난 회차 — 원격 목록(`config/hoyoland/editions.json` 의 archived)을 받기 전에 쓰는 값.
     * 회차를 보관할 때마다 여기를 고칠 필요는 없다(원격 목록이 덮는다) — 첫 화면이 링크 없이 서지 않게 하는 용도다.
     */
    val archivedEditions: List<String> = listOf("2026")   // 2025 · 2024 는 [HoyolandArchives] 가 앱에 들고 있다

    val event: HoyolandEvent = HoyolandEvent(
        edition = "호요랜드 2027",
        startYmd = "",
        endYmd = "",
        venueName = "",
        venueHall = "",
        venueAddress = "",
        mapUrl = "",
        mapFallbackUrl = "",
        officialUrl = "",
        announceYmd = "",
        ticket = HoyolandTicket(status = HoyolandTicketStatus.UNDECIDED),
        lineup = emptyList(),
        programs = emptyList(),
        editionEn = "HOYOLAND 2027",
        notice = "",
        days = emptyList(),
        past = listOf(
            // 2026 — config 에서 확인되는 값만(관람객 · 규모는 공식 발표가 없어 비운다).
            HoyolandPastEvent(
                "호요랜드 2026",
                listOf(
                    HoyolandFact("기간", "2026.10.2 ~ 10.5 (4일)"),
                    HoyolandFact("장소", "일산 킨텍스 제2전시장 7·8홀 · 후면광장"),
                    HoyolandFact("티켓", "30,000원(수수료 포함) · 매진 · 조별 입장(A~F)"),
                    HoyolandFact("참여 IP", "원신 · 붕괴3rd · 스타레일 · 젠레스 · 미해결사건부"),
                    HoyolandFact("구성", "체험존 · 굿즈 · 푸드 · 창작전시/DIY · 무대"),
                ),
            ),
            HoyolandPastEvent(
                "호요랜드 2025",
                listOf(
                    HoyolandFact("기간", "2025.10.9 ~ 10.12 (4일)"),
                    HoyolandFact("장소", "일산 킨텍스 제2전시장 9·10홀"),
                    HoyolandFact("규모", "약 26,000㎡ · 티켓 3만 6천 장 완판"),
                    HoyolandFact("관람객", "약 3만 2천 명 (4일)"),
                    HoyolandFact("티켓", "29,000원(예매수수료 별도) · 전 회차 매진 · 게임별 웰컴키트"),
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
    /**
     * 행사 **단계** 목업 — 개막 전 · 진행 중 · 종료를 개발자 화면에서 바로 본다.
     *
     * 이 화면은 단계마다 답하는 말이 통째로 바뀐다(카운트다운 → 일차 · 게이지 → 없음 ·
     * 예매와 「현장에서」 순서 뒤집힘 · 라인업 부제가 테마 → 무대 상태). 실제 개막일을
     * 기다리지 않고 그 셋을 확인하려고 **날짜만 옮긴** 이벤트를 만든다.
     *
     * 진행 중은 [stageMockEvent] 를 그대로 쓴다 — 거긴 오늘 편성이 지금 시각 기준이라
     * 진행 중인 무대가 늘 하나 잡히고, 라인업의 "지금 무대" 표시까지 같이 보인다.
     *
     * @param key `before` · `live` · `ended`. 그 밖의 값이면 null(목업 끔).
     */
    fun phaseMockEvent(
        key: String,
        source: HoyolandEvent = event,
        nowMillis: Long = currentTimeMillis(),
    ): HoyolandEvent? {
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(DateUtil.timeZone).date
        val ymd = { d: Int -> today.plus(d, DateTimeUnit.DAY).toString() }
        return when (key) {
            // D-16 — 게이지가 절반쯤 찬 자리(발표는 16일 전으로 당겨 둔다).
            PHASE_MOCK_BEFORE -> source.copy(
                startYmd = ymd(16), endYmd = ymd(19), announceYmd = ymd(-16),
            )
            PHASE_MOCK_LIVE -> stageMockEvent(source, nowMillis)
            // 폐막 이틀 뒤 — 히어로가 EVENT ENDED 로 굳고 액션 줄이 사라지는 자리.
            PHASE_MOCK_ENDED -> source.copy(
                startYmd = ymd(-5), endYmd = ymd(-2), announceYmd = ymd(-40),
            )
            else -> null
        }
    }

    const val PHASE_MOCK_BEFORE = "before"
    const val PHASE_MOCK_LIVE = "live"
    const val PHASE_MOCK_ENDED = "ended"

    fun stageMockEvent(
        source: HoyolandEvent = event,
        nowMillis: Long = currentTimeMillis(),
    ): HoyolandEvent {
        val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(DateUtil.timeZone)
        val today = now.date
        val ymd = { d: Int -> today.plus(d, DateTimeUnit.DAY).toString() }
        // 기준 시각 — 상대 배치가 자정을 넘지 않도록 낮 구간(04:00~19:00)으로 당긴다.
        val base = (now.hour * 60 + now.minute).coerceIn(4 * 60, 19 * 60)
        val at = { offset: Int -> HoyolandEvent.hhmm((base + offset).coerceIn(0, 23 * 60 + 59)) }

        return source.copy(
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
