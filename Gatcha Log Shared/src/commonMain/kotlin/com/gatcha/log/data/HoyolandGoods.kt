package com.gatcha.log.data


// 호요랜드 굿즈 · 부스. `Hoyoland.kt` 에서 나눴다(27.52.0).

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
