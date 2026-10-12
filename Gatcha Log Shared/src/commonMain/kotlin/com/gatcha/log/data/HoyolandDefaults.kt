package com.gatcha.log.data


// 호요랜드 번들 기본값 — 네트워크 없이도 화면이 비지 않게 하는 폴백. `Hoyoland.kt` 에서 나눴다(27.52.0).

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
}
