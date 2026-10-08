package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import com.gatcha.log.platformName
import com.gatcha.log.util.SafeUrl
import com.gatcha.log.util.currentTimeMillis
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

/** 공지의 무게 — 홈 배너의 색만 가른다(안내 = 강조색 · 주의 = 경고색 · 긴급 = 급한 경고색). */
enum class AppNoticeLevel { INFO, WARN, URGENT }

/**
 * 홈 맨 위에 서는 운영 공지 한 건. 점검 · 장애 · 행사 안내처럼 **앱을 업데이트하지 않고** 알려야 하는 것.
 *
 * @property url 비어 있지 않으면 배너에 버튼이 붙는다. 글자는 [cta](비면 「자세히」).
 */
data class AppNotice(
    val level: AppNoticeLevel,
    val title: String,
    val body: String,
    val url: String,
    val cta: String,
)

/**
 * 앱 공지 — 운영 어드민(`Gatcha Log Admin/`)이 쓰고 홈이 읽는다.
 *
 * 출처와 순서는 다른 운영 문서와 같다([LiveConfig]): Firestore `config/notices` →
 * raw `config/notices.json`. **번들 기본값은 없다** — 공지가 없는 것이 기본 상태다.
 *
 * **실패는 "공지 없음" 이다.** 네트워크가 없다고 홈에 오류를 세울 이유가 없다. 다만 한 번 받은
 * 목록은 다음 실패 때도 붙들고 있는다 — 점검 공지가 떠 있다가 지하철에서 사라지면 점검이
 * 끝난 것으로 읽힌다.
 */
object AppNoticeApi {

    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/notices.json"

    /** 운영 어드민이 쓰는 라이브 문서 이름 — [LiveConfig] 참고. */
    private const val CONFIG_DOC = "notices"

    /** [HoyolandApi] 와 같은 값 · 같은 이유 — 이 시간이 곧 공지가 앱에 닿는 속도다. */
    private const val FRESH_MS = 15_000L

    private val seoulTz = TimeZone.of("Asia/Seoul")

    /** 마지막으로 받은 본문. 파싱한 목록이 아니라 본문을 붙드는 이유는 [current] 참고. */
    private var cachedBody: String? = null
    private var cachedAtMillis = 0L

    /**
     * 지금 띄울 공지 — **네트워크를 타지 않는다.** 첫 프레임과 재구성이 쓴다.
     *
     * 받아 둔 본문을 **부를 때마다 지금 시각으로 다시 거른다.** 목록을 붙들어 두면 앱을 켜 둔 채
     * 종료 시각을 넘겨도 공지가 남는다.
     */
    val current: List<AppNotice>
        get() = (cachedBody ?: restored())?.let { parse(it, currentTimeMillis(), platformKey()) } ?: emptyList()

    /**
     * 디스크에 남은 마지막 공지 본문(27.51.1) — 첫 프레임에 배너가 **없다가 잠시 뒤 생기는** 것을 없앤다.
     * [HoyolandApi] 의 디스크 보관과 같은 이유 · 같은 방식이다. 신선도는 주지 않는다([cachedAtMillis] = 0) —
     * 다음 [load] 가 바로 원격을 다시 훑는다. 끝난 공지는 [current] 가 부를 때마다 지금 시각으로 걸러 낸다.
     */
    private val settings by lazy { com.gatcha.log.data.AppSettings() }
    private var restoreTried = false
    private fun restored(): String? {
        if (restoreTried) return null
        restoreTried = true
        val raw = runCatching { settings.appNoticesRaw }.getOrNull().orEmpty()
        return raw.takeIf { it.isNotBlank() && isReadable(it) }?.also { cachedBody = it }
    }

    /**
     * 라이브 → 정본 순으로 받아 지금 띄울 공지를 돌려준다. 실패하면 직전 값([current]).
     *
     * 앞 단계가 깨진 JSON 이면 다음으로 내려간다 — 어드민이 잘못 쓴 문서 하나로 정본의 공지까지
     * 사라지면 안 된다. `notices: []` 는 깨진 것이 아니라 **"공지 없음"** 이라 내려가지 않는다.
     */
    suspend fun load(force: Boolean = false): List<AppNotice> {
        if (!force && cachedBody != null && currentTimeMillis() - cachedAtMillis < FRESH_MS) return current
        // 라이브가 늦으면 정본을 같이 받는다([LiveConfig.getOrRaw]).
        val body = LiveConfig.getOrRaw(CONFIG_DOC, URL, ::isReadable)
        if (body != null) {
            cachedBody = body
            cachedAtMillis = currentTimeMillis()
            // 다음 실행의 첫 프레임이 이 공지로 서도록 남긴다.
            runCatching { settings.appNoticesRaw = body }
        }
        return current
    }

    private fun isReadable(body: String): Boolean = parse(body, 0L, "") != null

    /** 이 기기의 플랫폼 키 — 공지의 `platform` 과 견준다. */
    private fun platformKey(): String = if (platformName().startsWith("iOS", ignoreCase = true)) "ios" else "android"

    /** "yyyy-MM-dd HH:mm" (Asia/Seoul) → epoch millis. 못 읽으면 null. */
    @OptIn(ExperimentalTime::class)
    private fun millis(s: String): Long? = runCatching {
        LocalDateTime.parse(s.trim().replace(" ", "T")).toInstant(seoulTz).toEpochMilliseconds()
    }.getOrNull()

    /**
     * 본문 → [nowMillis] 에 [platform] 기기에서 띄울 공지. **깨진 JSON 이면 null**, 공지가 없으면 빈 목록.
     *
     * 줄 하나가 버려지는 경우(어드민 검증이 같은 지점을 짚는다):
     *  - `title` 이 비었다.
     *  - `start` · `end` 가 **적혀 있는데 못 읽는다** — 기간을 모르는 공지를 무기한 띄우느니 안 띄운다.
     *  - 지금이 `start` 전이거나 `end` 이후다. 빈 칸은 각각 "지금부터" · "내릴 때까지" 다.
     *  - `platform` 이 이 기기가 아니다. 빈 칸 · `all` 은 모두에게, 모르는 값은 아무에게도 안 띄운다.
     *
     * `level` 을 모르면 [AppNoticeLevel.INFO] — 색만 다를 뿐 공지는 떠야 한다.
     */
    internal fun parse(body: String, nowMillis: Long, platform: String): List<AppNotice>? = runCatching {
        val arr = JSONObject(body).optJSONArray("notices") ?: return@runCatching emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isEmpty()) return@mapNotNull null

            val startRaw = o.optString("start").trim()
            val endRaw = o.optString("end").trim()
            val start = if (startRaw.isEmpty()) Long.MIN_VALUE else millis(startRaw) ?: return@mapNotNull null
            val end = if (endRaw.isEmpty()) Long.MAX_VALUE else millis(endRaw) ?: return@mapNotNull null
            if (nowMillis < start || nowMillis >= end) return@mapNotNull null

            val target = o.optString("platform").trim().lowercase()
            if (target.isNotEmpty() && target != "all" && target != platform) return@mapNotNull null

            AppNotice(
                level = when (o.optString("level").trim().lowercase()) {
                    "warn" -> AppNoticeLevel.WARN
                    "urgent" -> AppNoticeLevel.URGENT
                    else -> AppNoticeLevel.INFO
                },
                title = title,
                body = o.optString("body").trim(),
                url = SafeUrl.https(o.optString("url")),   // https 가 아니면 버린다 — 배너의 「자세히」가 사라질 뿐이다
                cta = o.optString("cta").trim().ifEmpty { "자세히" },
            )
        }
    }.getOrNull()
}
