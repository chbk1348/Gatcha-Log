package com.gatcha.log.data


// 호요랜드의 값 모델 — 단계 · 예매 · 배치도 · 라인업 · 지난 행사. 본체는 `Hoyoland.kt`(HoyolandEvent)다.
// 2,000줄짜리 한 파일을 주제별로 나눴다(27.52.0) — 같은 패키지 안에서 옮기기만 해서 부르는 쪽은 그대로다.

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

    /**
     * 입장 특전 줄인가 — 제목이 「웰컴 키트」나 「입장 특전」으로 시작한다(10/6, 「입장 특전」 섹션으로 분리).
     *
     * 푸드존과 같은 방식으로 **같은 programs 배열에서 제목으로** 가른다 — 문서 모양이 그대로라 이 값을 모르는
     * 옛 빌드는 지금처럼 프로그램 섹션에 그린다. 어드민 `isPerkProgram` 과 **같아야 한다**. 푸드가 먼저다.
     */
    val isPerk: Boolean get() = !isFood && (title.startsWith("웰컴 키트") || title.startsWith("입장 특전"))
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
