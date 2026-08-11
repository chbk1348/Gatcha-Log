package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 버전 특별 방송 한 건.
 *
 * @param isEstimate 계산으로 낸 **예상**인가. 지금은 항상 true — 화면은 이 값을 보고 '예상'을 표시한다.
 *   나중에 실제 공지에서 일시를 얻는 경로가 생기면 그때 false 가 붙는다.
 * @param liveUrl 게임 공식 한국 채널의 라이브 탭. 방송 영상 자체의 주소는 알 수 없어 채널로 보낸다.
 */
data class LiveBroadcast(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val version: String,
    val targetMillis: Long,
    val isEstimate: Boolean,
    val liveUrl: String,
)

/**
 * 버전 특별 방송 **예상** 일정.
 *
 * ## 왜 예상인가
 *
 * 방송 일시를 구조화해서 주는 API 가 없다. ennead 의 calendar 에는 이벤트·배너·챌린지만 있고,
 * 공지 목록에 '특별 방송 예고' 글이 뜨긴 하지만 일시·링크가 **본문 텍스트 안에만** 있는 데다
 * 목록이 15건이라 며칠이면 밀려 사라진다. YouTube 는 RSS 로 예정 라이브를 주지 않고, 예정
 * 라이브를 조회하는 Data API 는 키가 필요한데 이 앱은 사이드로드라 키를 숨길 데가 없다.
 *
 * 대신 방송 관례가 규칙적이다 — 호요버스 3게임 모두 **버전 시작(수요일 점검) 12일 전 금요일** 저녁에 한다.
 * 앱은 픽업 페이즈 종료일을 알고 있고, 그 **다음 날**이 버전 시작이므로 거기서 역산한다.
 *
 * 확인된 사례(둘 다 배너 종료가 화요일이고, +1일 → −12일 이 실제 방송일과 정확히 맞는다):
 * - 원신 7.0 — 배너 종료 2026-08-11(화) → 시작 08-12(수) → 방송 2026-07-31(금) 21:00
 * - 스타레일 4.5 — 배너 종료 2026-08-25(화) → 시작 08-26(수) → 방송 2026-08-14(금) 20:30
 *
 * 어긋날 수 있다. 그래서 [LiveBroadcast.isEstimate] 로 표시하고 화면이 '예상'이라고 밝힌다.
 */
@OptIn(ExperimentalTime::class)
object BroadcastSchedule {

    /** 버전 시작 며칠 전에 방송하는가. 3게임 공통 관례. */
    const val DAYS_BEFORE_VERSION = 12

    /** 한 버전의 길이(일) — 6주. 다음다음 방송을 짚을 때 쓴다. */
    private const val VERSION_DAYS = 42

    /**
     * 게임별 방송 시각(로컬 24시간제)과 공식 한국 채널.
     *
     * 채널은 핸들이 아니라 **채널 ID** 로 건다 — 핸들은 바뀔 수 있지만 ID 는 안 바뀐다.
     */
    private data class GameBroadcast(val hour: Int, val minute: Int, val channelId: String)

    private val byGame: Map<String, GameBroadcast> = mapOf(
        // 원신 — 공식 한국 채널(@Genshinimpact_KR). 7.0 방송이 21:00 이었다.
        Game.GENSHIN.key to GameBroadcast(21, 0, "UCcum1rCJ5GJeQ_xv0xrohqg"),
        // 붕괴: 스타레일 — 공식 한국 채널(@HonkaiStarRail_KR).
        // 4.5 방송 확정 공지가 20:30 이었다(2026-08-14). 20:00 으로 잡아 뒀던 걸 맞춘다.
        Game.HSR.key to GameBroadcast(20, 30, "UCH33CJMcI0XZUpIhWRHiUuw"),
        // 젠레스 존 제로 — 공식 한국 채널(@ZZZ_KO). 확정 사례를 아직 못 봐서 20:00 으로 둔다.
        Game.ZZZ.key to GameBroadcast(20, 0, "UCmry1hfaRHI_iTfxUMhC8mA"),
    )

    private fun liveUrl(channelId: String) = "https://www.youtube.com/channel/$channelId/streams"

    /**
     * 다음 특별 방송 — 게임당 **한 건만**.
     *
     * 회차를 멀리 볼수록 버전 길이 가정(6주)의 오차가 쌓이므로 가장 가까운 하나만 낸다.
     *
     * @param banners 픽업 배너. 마지막 페이즈 종료일 = 다음 버전 시작일로 본다.
     * @return 임박순. 배너가 없어 버전 시작일을 모르는 게임은 빠진다.
     */
    fun next(banners: List<GachaBanner>, nowMillis: Long = currentTimeMillis()): List<LiveBroadcast> =
        byGame.mapNotNull { (key, cfg) ->
            val game = GameData.games.firstOrNull { it.key == key } ?: return@mapNotNull null
            // 종료 미정 픽업(콜라보 등)은 날짜가 없어 버전 경계를 못 만든다.
            val phaseEnds = banners
                .filter { it.game == game.displayName && !it.isEndUnknown }
                .map { it.endMillis }
            // ⚠️ 배너 종료 시각은 **버전 시작이 아니다.** 픽업은 점검 직전(화요일 늦은 시각)에
            // 끝나고 새 버전은 그 다음 날(수요일) 점검 후 시작한다. 종료 시각을 그대로 쓰면
            // 역산 결과가 하루 앞당겨져 금요일이 목요일이 됐다(스타레일 4.5 확정 공지로 발견).
            val versionStart = (phaseEnds.maxOrNull() ?: return@mapNotNull null) + DAY_MS

            val target = nextAfter(versionStart, cfg, nowMillis) ?: return@mapNotNull null
            LiveBroadcast(
                gameKey = game.key,
                gameShort = game.shortName,
                colorArgb = game.color,
                version = "",
                targetMillis = target,
                isEstimate = true,
                liveUrl = liveUrl(cfg.channelId),
            )
        }.sortedBy { it.targetMillis }

    /**
     * [versionStart] 로부터 역산한 방송 시각 중 **아직 오지 않은 첫 회**.
     *
     * 현재 버전이 곧 끝나는 시점이면 그 버전의 방송은 이미 지났다. 그때는 버전 길이만큼
     * 밀어 다음 회차를 본다. 한 해를 넘겨도 못 찾으면 포기한다(가정이 깨진 상태라 추측을 더
     * 얹지 않는다).
     */
    private fun nextAfter(versionStart: Long, cfg: GameBroadcast, nowMillis: Long): Long? {
        var start = versionStart
        repeat(9) {   // 6주 × 9 ≈ 1년
            val at = broadcastAt(start, cfg)
            if (at > nowMillis) return at
            start += VERSION_DAYS * DAY_MS
        }
        return null
    }

    /** 버전 시작 시각 → 그 버전 방송 시각(12일 전 금요일, 게임별 정해진 시:분). */
    private fun broadcastAt(versionStart: Long, cfg: GameBroadcast): Long {
        val tz = DateUtil.timeZone
        val startDate = Instant.fromEpochMilliseconds(versionStart).toLocalDateTime(tz).date
        // 날짜만 뒤로 물리고 시각은 관례값으로 새로 세운다 — 점검 시각(보통 오전)을 그대로
        // 12일 전으로 옮기면 방송이 아침에 열린 것처럼 나온다.
        val day = snapToFriday(startDate.plus(-DAYS_BEFORE_VERSION, DateTimeUnit.DAY))
        return LocalDateTime(day.year, day.month, day.day, cfg.hour, cfg.minute)
            .toInstant(tz).toEpochMilliseconds()
    }

    /**
     * 가장 가까운 금요일로 맞춘다.
     *
     * 버전 시작이 늘 수요일이라 12일 전은 정확히 금요일이 된다. 그래도 스냅을 두는 이유는,
     * 점검이 하루 밀리는 등으로 시작일 추정이 어긋나도 **요일 관례는 안 바뀌기** 때문이다.
     * 여기서 흡수하지 않으면 목요일·토요일 같은 날짜가 그대로 화면에 나간다.
     */
    private fun snapToFriday(date: LocalDate): LocalDate {
        // 월(1)~일(7) 기준 금요일은 5. -3..+3 범위로 옮기면 가장 가까운 금요일이 된다.
        val shift = ((DayOfWeek.FRIDAY.isoDayNumber - date.dayOfWeek.isoDayNumber) + 10) % 7 - 3
        return date.plus(shift, DateTimeUnit.DAY)
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
