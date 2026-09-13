package com.gatcha.log.data.api

import com.gatcha.log.util.currentTimeMillis
import com.gatcha.log.data.HoyolandDefaults
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandBooth
import com.gatcha.log.data.HoyolandGoods
import com.gatcha.log.data.HoyolandFact
import com.gatcha.log.data.HoyolandGstar
import com.gatcha.log.data.HoyolandLineup
import com.gatcha.log.data.HoyolandDay
import com.gatcha.log.data.HoyolandPastEvent
import com.gatcha.log.data.HoyolandProgram
import com.gatcha.log.data.HoyolandSlot
import com.gatcha.log.data.HoyolandTicket
import com.gatcha.log.data.HoyolandTicketStatus
import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

/**
 * 호요랜드 정보 원격 갱신.
 *
 * 행사 정보는 개최 전까지 **순차로 공개된다** — 지금은 예매만 미정이지만, 공개되는 순간
 * 앱을 업데이트하지 않고도 바뀌어야 한다. 같은 저장소의 `config/hoyoland.json` 을 raw 로 읽는다.
 * ([UpdateChecker] 의 `version.json` 은 **루트에 남겨 둔다** — 이미 설치된 앱이 새 버전을 찾는
 * 유일한 통로라 경로를 옮기면 구버전이 업데이트를 영영 못 본다.)
 *
 * **출처는 두 곳이고 순서가 있다.**
 *  1. Firestore `config/hoyoland` — 운영 어드민(`Gatcha Log Admin/`)이 쓰는 자리. 커밋 없이 즉시 반영된다.
 *     행사 당일 현장에서 시간표가 바뀌는 상황을 위한 것이다.
 *  2. raw `config/hoyoland.json` — git 에 남는 정본. Firestore 가 비었거나 못 읽으면 여기로 내려온다.
 *  3. 번들 [HoyolandDefaults] — 둘 다 실패했을 때.
 *
 * 어드민은 1번에 쓰면서 2번용 JSON 도 함께 뽑아 준다. 즉 **Firestore 는 캐시가 아니라 앞선 정본**이고,
 * git 은 이력과 최종 폴백을 맡는다. 둘이 어긋나면 앱은 Firestore 를 믿는다.
 *
 * **실패는 조용히 폴백한다.** 이 화면은 로그인·인증과 무관한 읽기 전용 소개 페이지라,
 * 네트워크가 없다고 빈 화면을 보여 줄 이유가 없다 — 번들된 [HoyolandDefaults] 로 그린다.
 * (리딤코드([GiftCodeApi])가 실패를 null 로 구분하는 것과 반대다. 저긴 "코드가 없다"와
 * "못 불러왔다"가 사용자에게 다른 의미지만, 여기선 둘 다 "확정된 정보를 보여준다"로 같다.)
 */
object HoyolandApi {

    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/hoyoland.json"

    /** 운영 어드민이 쓰는 라이브 문서 이름 — [LiveConfig] 참고. */
    private const val CONFIG_DOC = "hoyoland"

    /**
     * 받아온 값을 짧게 재사용한다 — 게임정보 탭·홈·일정 탭이 각자 부르는데, 한 번 화면을
     * 오가는 동안 같은 요청을 세 번 태울 이유가 없다.
     *
     * **프로세스 내내 붙들지는 않는다.** 예전엔 캐시가 있으면 무조건 재사용해서, 어드민에서
     * 라이브 반영을 해도 **앱을 완전히 껐다 켜기 전까지 바뀌지 않았다**(2026-09-10 확인).
     * 커밋 없이 즉시 고치자고 만든 구조인데 정작 앱이 그 즉시성을 잡아먹고 있었다.
     * [FRESH_MS] 가 지나면 화면에 다시 들어오는 것만으로 새로 읽는다.
     */
    private var cached: HoyolandEvent? = null
    private var cachedAtMillis = 0L

    /**
     * 개발자 목업이 얹혀 있는지 — 얹힌 동안에는 [FRESH_MS] 가 지나도 원격으로 덮지 않는다.
     * 목업을 보려고 켜 뒀는데 잠깐 다른 화면 갔다 오면 풀려 버리면 쓸모가 없다.
     */
    private var stageMockOn = false

    /**
     * 캐시를 신선하다고 보는 시간. 짧게 잡은 이유는 이 값이 **현장 대응의 반응 속도**이기
     * 때문이다 — 무대 편성이 바뀌어 어드민에서 고쳤는데 앱이 1분을 기다리면 늦다.
     * 그렇다고 0 으로 두면 홈↔게임정보를 오갈 때마다 같은 요청이 겹친다.
     */
    private const val FRESH_MS = 15_000L

    /** 캐시된 값 또는 번들 폴백 — **네트워크를 타지 않는다.** 첫 프레임을 그릴 때 쓴다. */
    val current: HoyolandEvent get() = cached ?: HoyolandDefaults.event

    /**
     * 개발자 화면 전용 — 무대 시간표 목업을 **캐시에 얹는다.**
     *
     * 실제 편성이 공개되기 전에 라이브 카드·게임 레인·필터를 확인하려는 것이다. 캐시에 넣으므로
     * [load] 가 `force` 없이 불리는 한(화면 진입 경로 전부) 목업이 유지되고,
     * [debugClearStageMock] 이나 당겨서 새로고침 한 번이면 원래대로 돌아온다.
     */
    fun debugInjectStageMock() {
        cached = HoyolandDefaults.stageMockEvent()
        cachedAtMillis = currentTimeMillis()
        stageMockOn = true
    }

    /** 목업 해제 — 다음 조회에서 원격/번들 값을 다시 잡는다. */
    fun debugClearStageMock() {
        cached = null
        cachedAtMillis = 0L
        stageMockOn = false
    }

    /** 지금 목업이 얹혀 있는지 — 개발자 화면 토글 표시에 쓴다. */
    val isStageMock: Boolean get() = stageMockOn

    /**
     * 원격 갱신 시도. 실패하면 [HoyolandDefaults] 를 그대로 돌려주므로 **호출부는 널을 다루지 않는다.**
     *
     * @param force true 면 캐시를 무시하고 다시 받는다(당겨서 새로고침).
     */
    suspend fun load(force: Boolean = false): HoyolandEvent {
        // 목업은 당겨서 새로고침(force)으로만 걷힌다 — 시간이 지났다고 풀리면 안 된다.
        if (stageMockOn && !force) return current
        cached?.let { if (!force && currentTimeMillis() - cachedAtMillis < FRESH_MS) return it }
        // 라이브 → 정본 순으로 내려온다. 앞 단계가 깨진 JSON 이어도 다음 단계로 넘어간다 —
        // 어드민이 잘못 쓴 문서 하나로 화면이 비어 버리면 안 된다.
        val parsed = fetchLive()?.let(::parseOrNull) ?: fetchRaw()?.let(::parseOrNull)
        if (parsed != null) {
            cached = parsed
            cachedAtMillis = currentTimeMillis()
            stageMockOn = false
        }
        return parsed ?: current
    }

    private fun parseOrNull(body: String): HoyolandEvent? =
        runCatching { parse(JSONObject(body)) }.getOrNull()

    private suspend fun fetchLive(): String? = LiveConfig.get(CONFIG_DOC)

    /**
     * `?t=` 로 CDN 캐시를 우회한다 — raw.githubusercontent 는 커밋 뒤에도 몇 분간 옛 내용을
     * 준다. 정본을 고쳐 커밋했는데 앱이 안 바뀌면 라이브 반영과 구분이 안 된다
     * ([ZzzBannerApi] 도 같은 이유로 같은 방식을 쓴다).
     */
    private suspend fun fetchRaw(): String? =
        Net.get("$URL?t=${currentTimeMillis()}").takeIf { it.isOk }?.body

    /**
     * JSON → 모델. **빠진 키는 전부 번들 기본값으로 메운다** — 원격 파일이 일부만 갱신돼도
     * (예: 예매 항목만 채워 넣어도) 나머지가 비어 버리지 않게 하려는 것이다.
     */
    private fun parse(o: JSONObject): HoyolandEvent {
        val d = HoyolandDefaults.event
        return HoyolandEvent(
            edition = o.optString("edition", d.edition),
            startYmd = o.optString("startYmd", d.startYmd),
            endYmd = o.optString("endYmd", d.endYmd),
            venueName = o.optString("venueName", d.venueName),
            venueHall = o.optString("venueHall", d.venueHall),
            venueAddress = o.optString("venueAddress", d.venueAddress),
            mapUrl = o.optString("mapUrl", d.mapUrl),
            mapFallbackUrl = o.optString("mapFallbackUrl", d.mapFallbackUrl),
            officialUrl = o.optString("officialUrl", d.officialUrl),
            announceYmd = o.optString("announceYmd", d.announceYmd),
            ticket = o.optJSONObject("ticket")?.let { parseTicket(it) } ?: d.ticket,
            lineup = o.optJSONArray("lineup")?.let { parseLineup(it) }?.takeIf { it.isNotEmpty() } ?: d.lineup,
            programs = o.optJSONArray("programs")?.let { parsePrograms(it) } ?: d.programs,
            notice = o.optString("notice", d.notice),
            // days 는 **빈 배열도 유효한 값**이라 takeIf 로 걸러내지 않는다 —
            // 시간표를 내렸다가 다시 올리는 상황에서 번들 기본값이 되살아나면 안 된다.
            days = o.optJSONArray("days")?.let { parseDays(it) } ?: d.days,
            gstar = o.optJSONObject("gstar")?.let { parseGstar(it, d.gstar) } ?: d.gstar,
            past = o.optJSONArray("past")?.let { parsePast(it) }?.takeIf { it.isNotEmpty() } ?: d.past,
            // goods·booths 도 days 와 같다 — **빈 배열이 유효한 값**이라 걸러내지 않는다.
            goods = o.optJSONArray("goods")?.let { parseGoods(it) } ?: d.goods,
            booths = o.optJSONArray("booths")?.let { parseBooths(it) } ?: d.booths,
        )
    }

    private fun parseTicket(o: JSONObject): HoyolandTicket = HoyolandTicket(
        status = ticketStatusOf(o.optString("status")),
        vendor = o.optString("vendor"),
        openLabel = o.optString("openLabel"),
        openYmd = o.optString("openYmd"),
        openHour = o.optInt("openHour", 0),
        priceLabel = o.optString("priceLabel"),
        url = o.optString("url"),
        note = o.optString("note"),
    )

    /** 모르는 값은 미정으로 본다 — 오타 하나로 "판매 중"이 뜨면 안 되는 자리다. */
    private fun ticketStatusOf(raw: String): HoyolandTicketStatus = when (raw.lowercase()) {
        "announced" -> HoyolandTicketStatus.ANNOUNCED
        "on_sale", "onsale" -> HoyolandTicketStatus.ON_SALE
        "sold_out", "soldout" -> HoyolandTicketStatus.SOLD_OUT
        else -> HoyolandTicketStatus.UNDECIDED
    }

    private fun parseLineup(arr: JSONArray): List<HoyolandLineup> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val game = o.optString("game").trim()
            if (game.isBlank()) return@mapNotNull null
            HoyolandLineup(
                game = game,
                theme = o.optString("theme"),
                abbr = o.optString("abbr"),
                // 색은 "0xFF30C6E8" 같은 16진 문자열로 적는다 — JSON 숫자로 두면 부호 있는 정수
                // 범위를 넘어가는 값(0xFFxxxxxx)이 파서·에디터마다 다르게 읽힌다.
                colorArgb = parseArgb(o.optString("colorArgb")),
                url = o.optString("url").trim(),
            )
        }

    private fun parseArgb(raw: String): Long {
        val hex = raw.trim().removePrefix("0x").removePrefix("0X").removePrefix("#")
        if (hex.isEmpty()) return 0L
        return hex.toLongOrNull(16) ?: 0L
    }

    private fun parsePrograms(arr: JSONArray): List<HoyolandProgram> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            HoyolandProgram(title, o.optString("desc"), o.optString("deadline"))
        }

    /** 지스타 — 여기도 빠진 키는 번들 기본값으로 메운다(참가사만 갱신하는 일이 잦다). */
    private fun parseGstar(o: JSONObject, d: HoyolandGstar): HoyolandGstar = HoyolandGstar(
        title = o.optString("title", d.title),
        badge = o.optString("badge", d.badge),
        facts = o.optJSONArray("facts")?.let { parseFacts(it) } ?: d.facts,
        lineup = o.optJSONArray("lineup")?.let { parseLineup(it) } ?: d.lineup,
        url = o.optString("url", d.url),
        notice = o.optString("notice", d.notice),
    )

    private fun parseDays(arr: JSONArray): List<HoyolandDay> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ymd = o.optString("ymd").trim()
            if (ymd.isBlank()) return@mapNotNull null
            HoyolandDay(ymd, parseSlots(o.optJSONArray("slots")))
        }

    private fun parseSlots(arr: JSONArray?): List<HoyolandSlot> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) {
                null
            } else {
                HoyolandSlot(
                    time = o.optString("time"),
                    title = title,
                    desc = o.optString("desc"),
                    // 무대 편성이라 칸의 주인은 거의 게임이다. 비면 전 IP 공통(합동 무대).
                    game = o.optString("game").trim(),
                    // 공연 길이(분). 없으면 0 — 화면이 '다음 편 전까지'로 본다.
                    minutes = o.optInt("minutes", 0),
                    // 출연자 — 표기 그대로. 무대를 고르는 기준이 공연명보다 출연자일 때가 많다.
                    cast = o.optString("cast").trim(),
                )
            }
        }
    }

    private fun parseGoods(arr: JSONArray?): List<HoyolandGoods> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            HoyolandGoods(
                name = name,
                // 가격은 숫자로 받는다 — 문자열이면 합계를 못 낸다. 미정이면 0.
                price = o.optInt("price", 0),
                game = o.optString("game").trim(),
                category = o.optString("category").trim(),
                note = o.optString("note").trim(),
            )
        }
    }

    private fun parseBooths(arr: JSONArray?): List<HoyolandBooth> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            HoyolandBooth(
                game = o.optString("game").trim(),
                title = title,
                desc = o.optString("desc").trim(),
                location = o.optString("location").trim(),
                // 참가비도 굿즈 가격과 같이 숫자로 받는다 — 0 이면 무료.
                price = o.optInt("price", 0),
                reward = o.optString("reward").trim(),
            )
        }
    }

    private fun parsePast(arr: JSONArray): List<HoyolandPastEvent> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            HoyolandPastEvent(title, parseFacts(o.optJSONArray("facts")))
        }

    private fun parseFacts(arr: JSONArray?): List<HoyolandFact> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val label = o.optString("label").trim()
            if (label.isBlank()) null else HoyolandFact(label, o.optString("value"))
        }
    }
}
