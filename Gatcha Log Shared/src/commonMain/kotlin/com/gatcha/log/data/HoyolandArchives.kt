package com.gatcha.log.data


// 호요랜드 지난 회차(2024 · 2025) 보관본. `Hoyoland.kt` 에서 나눴다(27.52.0).

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
