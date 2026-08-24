package com.gatcha.log.data

import com.gatcha.log.data.api.NewsItem
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
 * @param isEstimate 계산으로 낸 **예상**인가. 화면은 이 값을 보고 '예상'/'확정'을 가른다.
 *   공지나 예약 라이브에서 일시를 얻었으면 false 다.
 * @param liveUrl 방송을 여는 주소. 예약된 라이브를 찾았으면 **영상 직링크**, 못 찾았으면
 *   게임 공식 한국 채널의 라이브 탭.
 */
data class LiveBroadcast(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val version: String,
    val targetMillis: Long,
    val isEstimate: Boolean,
    val liveUrl: String,
    /** 확정 공지 주소. 확정일 때만 채워진다 — 화면이 '공지 보기'로 연결한다. */
    val noticeUrl: String = "",
)

/**
 * **확정** 방송. 근거는 둘 중 하나다 — 공지 본문([parseConfirmed]) 또는 예약된 라이브
 * ([com.gatcha.log.data.api.BroadcastApi]). 둘을 합치는 규칙은 [mergeConfirmed].
 *
 * @param version "4.5" 처럼 제목에서 뽑은 버전. 못 뽑으면 빈 문자열
 * @param noticeUrl 근거가 된 공지 주소. 예약 라이브에서 온 확정은 비어 있다
 * @param videoUrl 예약된 라이브 영상 주소. 예약 라이브에서 온 확정만 채워진다 —
 *   공지 본문에는 영상 주소가 없다. 채워져 있으면 [LiveBroadcast.liveUrl] 이 채널 대신 이걸 쓴다
 */
data class ConfirmedBroadcast(
    val gameKey: String,
    val version: String,
    val targetMillis: Long,
    val noticeUrl: String,
    val videoUrl: String = "",
)

/**
 * 버전 특별 방송 **예상** 일정.
 *
 * ## 왜 예상인가
 *
 * 방송 일시를 구조화해서 주는 API 가 없다. ennead 의 calendar 에는 이벤트·배너·챌린지만 있고,
 * 공지 목록에 '특별 방송 예고' 글이 뜨긴 하지만 일시·링크가 **본문 텍스트 안에만** 있는 데다
 * 목록이 15건이라 며칠이면 밀려 사라진다. YouTube 는 RSS 로 예정 라이브를 주지 않는다.
 *
 * 확정을 얻는 경로는 둘 뿐이고 **둘 다 늦거나 빌 수 있다**:
 * 1. 공지 본문 파싱([parseConfirmed])
 * 2. 예약된 라이브([com.gatcha.log.data.api.BroadcastApi]) — Data API 는 키와 할당량 때문에
 *    앱이 직접 못 부르고, GitHub Actions 가 6시간마다 조회해 둔 JSON 을 읽는다
 *
 * 그래서 역산은 없앨 수 없다. 둘 다 비었을 때 화면을 비워 두지 않으려는 바닥값이다.
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
        // 젠레스 존 제로 — 공식 한국 채널(@ZZZ_KO).
        // 3.2 방송 예고가 20:30 이었다(2026-08-28). 사례가 없어 20:00 으로 뒀던 걸 맞춘다.
        Game.ZZZ.key to GameBroadcast(20, 30, "UCmry1hfaRHI_iTfxUMhC8mA"),
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
    fun next(
        banners: List<GachaBanner>,
        confirmed: List<ConfirmedBroadcast> = emptyList(),
        nowMillis: Long = currentTimeMillis(),
    ): List<LiveBroadcast> =
        byGame.mapNotNull { (key, cfg) ->
            val game = GameData.games.firstOrNull { it.key == key } ?: return@mapNotNull null

            // 확정 공지가 있으면 그게 먼저다 — 역산은 공지가 없을 때 메우는 값일 뿐이다.
            confirmed.firstOrNull { it.gameKey == key && it.targetMillis > nowMillis }?.let { c ->
                return@mapNotNull LiveBroadcast(
                    gameKey = game.key,
                    gameShort = game.shortName,
                    colorArgb = game.color,
                    version = c.version,
                    targetMillis = c.targetMillis,
                    isEstimate = false,
                    // 예약 라이브에서 온 확정은 영상 주소를 안다 — 채널 목록을 거치지 않고 바로 연다.
                    liveUrl = c.videoUrl.ifBlank { liveUrl(cfg.channelId) },
                    noticeUrl = c.noticeUrl,
                )
            }

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
     * 공지 목록에서 **확정 방송**을 골라낸다.
     *
     * 확정으로 인정하는 조건은 셋 다 만족할 때뿐이다:
     * 1. 제목에 버전 번호(`4.5`)가 있다 — 젠레스 「연례 대공개」처럼 버전과 무관한 스페셜을 거른다
     * 2. 제목에 방송을 뜻하는 말이 있다 — 「4.4 버전 프리뷰 토론실」 같은 부대 이벤트를 거른다
     * 3. **본문 앞머리에 일시가 적혀 있다** — 없으면 확정할 값이 없으니 역산에 맡긴다
     *
     * 형식이 바뀌어 못 잡아도 손해가 없다. 그때는 조용히 예상값으로 남는다.
     */
    fun parseConfirmed(items: List<NewsItem>, nowMillis: Long = currentTimeMillis()): List<ConfirmedBroadcast> =
        items.mapNotNull { item ->
            val game = GameData.byNameOrNull(item.game) ?: return@mapNotNull null
            if (game.key !in byGame) return@mapNotNull null
            if (BROADCAST_WORDS.none { it in item.title }) return@mapNotNull null
            val version = VERSION_RE.find(item.title)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            // 앞머리로 한정한다 — 본문 뒤쪽에는 이벤트 기간 같은 다른 날짜가 얼마든지 있다.
            val head = item.summary.take(DATE_SCAN_HEAD)
            // 연도 없는 표기의 해를 정할 기준은 **공지가 올라온 시각**이다(지금이 아니다).
            val postedAt = item.createdAtMillis.takeIf { it > 0 } ?: nowMillis
            val at = parseDateTime(head, postedAt) ?: return@mapNotNull null
            // 지난 방송 공지는 목록에 한동안 남는다 — 그걸 '다음 방송'이라고 내놓으면 안 된다.
            if (at <= nowMillis) return@mapNotNull null
            ConfirmedBroadcast(game.key, version, at, item.url)
        }.sortedBy { it.targetMillis }

    /**
     * 두 확정 경로를 **게임별 한 건**으로 합친다.
     *
     * 경쟁이 아니라 보완이다. 공지는 대개 먼저 뜨지만 영상 주소를 모르고, 예약 라이브는 영상
     * 주소를 주지만 방송 하루 이틀 전에야 올라오는 일이 잦다. 그래서 한쪽만 있으면 그걸 쓰고,
     * 둘 다 있으면 **예약 라이브를 기준으로 삼되** 공지가 아는 값(버전·공지 주소)을 얹는다.
     *
     * 예약 라이브를 시각의 기준으로 두는 이유: 공지에 적힌 일시가 바뀌어도 공지 본문은 그대로인
     * 경우가 있는데, 예약 페이지는 실제로 그 방송이 열릴 시각이라 옮겨지면 같이 따라간다.
     *
     * @param fromApi 예약 라이브에서 온 확정([com.gatcha.log.data.api.BroadcastApi])
     * @param fromNotice 공지에서 뽑은 확정([parseConfirmed])
     * @return 임박순. 지난 방송도 거르지 않는다 — 그 판정은 [next] 가 주입받은 시각으로 한다
     */
    fun mergeConfirmed(
        fromApi: List<ConfirmedBroadcast>,
        fromNotice: List<ConfirmedBroadcast>,
    ): List<ConfirmedBroadcast> =
        (fromApi + fromNotice).map { it.gameKey }.distinct().mapNotNull { key ->
            // 두 목록 모두 임박순이라 firstOrNull 이 곧 그 게임의 가장 가까운 회차다.
            val api = fromApi.firstOrNull { it.gameKey == key }
            val notice = fromNotice.firstOrNull { it.gameKey == key }
            when {
                api == null -> notice
                notice == null -> api
                else -> api.copy(
                    // 영상 제목에서 버전을 못 뽑는 경우가 있다(제목 표기가 게임마다 다르다).
                    version = api.version.ifBlank { notice.version },
                    noticeUrl = notice.noticeUrl,
                )
            }
        }.sortedBy { it.targetMillis }

    /** 제목이 방송 글임을 알리는 말. 게임·번역마다 표기가 달라 넉넉히 둔다. */
    private val BROADCAST_WORDS =
        listOf("스페셜 프로그램", "특별 방송", "특별방송", "특별 생방송", "프리뷰 방송", "생방송", "라이브 스트리밍")

    /**
     * 앞머리에서 방송 일시를 집는다. **표기가 게임마다 다르다.**
     *
     * - 원신·스타레일: `🕙 2026/08/14 20:30 (한국 시간)` — 연도가 있다
     * - 젠레스: `…특별 방송이 8월 28일 20:30(KST)에 시작됩니다!` — 연도가 없고 한국식이다
     *
     * 후자는 연도를 지어내야 하는데, 기준은 **공지가 올라온 시각**이다. '지금'을 쓰면 목록에
     * 며칠 남아 있는 공지를 나중에 읽을 때 해가 어긋날 수 있다.
     *
     * @param aroundMillis 공지가 올라온 시각. 연도 없는 표기의 해를 여기서 정한다
     */
    private fun parseDateTime(head: String, aroundMillis: Long): Long? {
        val tz = DateUtil.timeZone
        DATE_RE.find(head)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            return runCatching {
                LocalDateTime(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
                    .toInstant(tz).toEpochMilliseconds()
            }.getOrNull()
        }
        val m = KO_DATE_RE.find(head) ?: return null
        val (mo, d, h, mi) = m.destructured
        return runCatching {
            val postedYear = Instant.fromEpochMilliseconds(aroundMillis).toLocalDateTime(tz).year
            fun atYear(y: Int) = LocalDateTime(y, mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
                .toInstant(tz).toEpochMilliseconds()
            val sameYear = atYear(postedYear)
            // 방송은 예고 뒤에 있다. 같은 해로 읽어 과거가 나오면 해를 넘긴 예고다
            // (12월 공지가 1월 방송을 알리는 경우).
            if (sameYear >= aroundMillis) sameYear else atYear(postedYear + 1)
        }.getOrNull()
    }

    /** "4.5 버전" · "버전 4.5" 어느 쪽이든 버전 번호를 집는다. */
    private val VERSION_RE = Regex("""(\d+\.\d+)""")

    /** "2026/08/14 20:30" — 구분자는 / . - 를 모두 받는다. */
    private val DATE_RE = Regex("""(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})\s+(\d{1,2}):(\d{2})""")

    /**
     * "8월 28일 20:30" — 연도가 없는 한국식 표기(젠레스).
     *
     * 시각이 날짜에 **바로 붙어 있을 때만** 받는다. 사이를 넉넉히 열어두면 "8월 28일에 오후
     * 8:30" 같은 문장에서 12시간을 틀리게 읽는다 — 못 잡으면 역산이 받아주지만 틀리면 없다.
     */
    private val KO_DATE_RE = Regex("""(\d{1,2})월\s*(\d{1,2})일\s*(\d{1,2}):(\d{2})""")

    /** 본문에서 일시를 찾을 때 훑는 앞부분 길이. */
    private const val DATE_SCAN_HEAD = 80

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
