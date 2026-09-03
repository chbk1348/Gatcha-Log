package com.gatcha.log.data.api

import com.gatcha.log.data.HoyolandDefaults
import com.gatcha.log.data.HoyolandEvent
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
 * 앱을 업데이트하지 않고도 바뀌어야 한다. `version.json` 과 같은 저장소에 `hoyoland.json` 을
 * 두고 raw 로 읽는다([UpdateChecker] 의 매니페스트와 같은 경로 규칙).
 *
 * **실패는 조용히 폴백한다.** 이 화면은 로그인·인증과 무관한 읽기 전용 소개 페이지라,
 * 네트워크가 없다고 빈 화면을 보여 줄 이유가 없다 — 번들된 [HoyolandDefaults] 로 그린다.
 * (리딤코드([GiftCodeApi])가 실패를 null 로 구분하는 것과 반대다. 저긴 "코드가 없다"와
 * "못 불러왔다"가 사용자에게 다른 의미지만, 여기선 둘 다 "확정된 정보를 보여준다"로 같다.)
 */
object HoyolandApi {

    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/hoyoland.json"

    /**
     * 한 번 받아온 값은 프로세스가 살아 있는 동안 재사용한다 — 게임정보 탭·홈·일정 탭이
     * 각자 부르는데, 하루에 몇 번 바뀔 정보가 아니라 매번 네트워크를 태울 이유가 없다.
     */
    private var cached: HoyolandEvent? = null

    /** 캐시된 값 또는 번들 폴백 — **네트워크를 타지 않는다.** 첫 프레임을 그릴 때 쓴다. */
    val current: HoyolandEvent get() = cached ?: HoyolandDefaults.event

    /**
     * 원격 갱신 시도. 실패하면 [HoyolandDefaults] 를 그대로 돌려주므로 **호출부는 널을 다루지 않는다.**
     *
     * @param force true 면 캐시를 무시하고 다시 받는다(당겨서 새로고침).
     */
    suspend fun load(force: Boolean = false): HoyolandEvent {
        cached?.let { if (!force) return it }
        val res = Net.get(URL)
        if (!res.isOk) return current
        val parsed = runCatching { parse(JSONObject(res.body)) }.getOrNull() ?: return current
        cached = parsed
        return parsed
    }

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
            if (title.isBlank()) null else HoyolandSlot(o.optString("time"), title, o.optString("desc"))
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
