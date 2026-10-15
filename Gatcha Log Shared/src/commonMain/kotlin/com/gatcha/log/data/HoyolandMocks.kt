package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// `Hoyoland.kt` 에서 나눴다(27.52.0).

/**
 * 호요랜드 **개발자용 목업** — 개막 전 · 진행 중 · 종료와 「오늘 무대가 도는 날」을 날짜만 옮겨 만든다.
 *
 * 예전엔 번들 기본값([HoyolandDefaults]) 안에 같이 있었다. 정본 값과 개발자 화면용 가짜 값이 한 객체에 섞여 있어
 * 떼어 냈다(27.52.0). 출시 화면은 이 객체를 부르지 않는다 — 개발자 메뉴가 [com.gatcha.log.data.api.HoyolandApi] 를 거쳐 쓴다.
 */
object HoyolandMocks {
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
        source: HoyolandEvent = HoyolandDefaults.event,
        nowMillis: Long = currentTimeMillis(),
    ): HoyolandEvent? {
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(HoyolandTimeZone).date
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
        source: HoyolandEvent = HoyolandDefaults.event,
        nowMillis: Long = currentTimeMillis(),
    ): HoyolandEvent {
        val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(HoyolandTimeZone)
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
