package com.gatcha.log.data.api

import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GameEvent
import com.gatcha.log.util.currentTimeMillis
import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

data class EnneadResult(
    val banners: List<GachaBanner>,
    val events: List<GameEvent>,
    val challenges: List<GameChallenge> = emptyList(),
)

/**
 * ennead.cc 캘린더 API — 픽업 배너 / 이벤트 (원신·스타레일).
 * 인증 불필요. **mihoyo 경로 우선, 404 시 hoyoverse 경로로 폴백.**
 *
 * 2026-07-29 확인: `api.ennead.cc` 의 hoyoverse 경로가 전 게임 404 로 응답한다(호스트·TLS 는
 * 정상이고 mihoyo 경로는 200). 원래는 hoyoverse 를 먼저 불렀는데, 그러면 새로고침마다 게임당
 * 404 를 한 번씩 맞고(응답 ~1초) 다시 부르게 돼 순수한 낭비였다. 폴백은 지우지 않는다 — 경로가
 * 되돌아올 수 있고, 남겨두는 비용이 0 이다(우선 경로가 200 이면 두 번째 요청은 아예 없다).
 *
 * (KDoc 에 `/` + `*` 조합을 쓰지 말 것 — Kotlin 블록 주석은 중첩돼서 주석이 안 닫힌다.)
 */
object EnneadApi {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 종료 미정 배너를 시작일로부터 몇 일까지 살려둘지 — 통상 버전 주기(6주)보다 넉넉하게. */
    private const val UNKNOWN_END_MAX_DAYS = 60L

    /**
     * @return 성공 시 캘린더, **네트워크·파싱 실패 시 null**([NewsApi.notices] 와 같은 규약).
     *
     * 빈 결과와 실패를 반드시 구분한다 — 호출부가 둘을 같게 보면, 한 게임이 타임아웃 났을 때
     * '그 게임은 진행 중인 배너·이벤트가 없다'로 읽혀 화면에서 통째로 사라진다.
     */
    suspend fun fetch(game: Game): EnneadResult? {
        val key = game.enneadKey ?: return EnneadResult(emptyList(), emptyList())

        var res = Net.get("https://api.ennead.cc/mihoyo/$key/calendar?lang=ko-kr")
        if (res.code == 404) {
            res = Net.get("https://api.ennead.cc/hoyoverse/$key/calendar?lang=ko-kr")
        }
        if (!res.isOk) return null

        return runCatching { parse(game, JSONObject(res.body)) }.getOrNull()
    }

    /**
     * 젠레스 존 제로 일정 — ennead `mihoyo/zenless/calendar` 의 **이벤트 + 도전만**(픽업 배너 기능 제거).
     * ennead ZZZ 데이터는 ko-kr 요청에도 영문이라 이벤트명에 [ZzzEventNames] 한국어 매핑(빌트인+원격) 적용,
     * 매핑 없으면 원문 유지. 보상 폴리크롬은 숫자 필드([rewardOf] 처리).
     *
     * @return [fetch] 와 동일 — 실패 시 null.
     */
    suspend fun fetchZzz(): EnneadResult? {
        val res = Net.get("https://api.ennead.cc/mihoyo/zenless/calendar?lang=ko-kr")
        if (!res.isOk) return null
        return runCatching {
            val r = parse(Game.ZZZ, JSONObject(res.body))
            val ko = ZzzEventNames.map() // 이벤트명 en→ko
            EnneadResult(
                emptyList(), // 픽업 배너 제외(기능 제거)
                r.events.map { it.copy(name = ko[it.name] ?: it.name) },
                r.challenges.map { it.copy(name = ko[it.name] ?: it.name) },
            )
        }.getOrNull()
    }

    private fun parse(game: Game, root: JSONObject): EnneadResult {
        val now = currentTimeMillis()

        val banners = mutableListOf<GachaBanner>()
        val bannersArr = root.optJSONArray("banners") ?: JSONArray()
        for (i in 0 until bannersArr.length()) {
            val b = bannersArr.optJSONObject(i) ?: continue
            val endMillis = b.optLong("end_time") * 1000
            val startMillis = b.optLong("start_time") * 1000
            // 종료 시각 미공지(end_time=0)인 배너는 버리지 않고 '종료 미정'으로 살린다.
            // 실제 사례: 스타레일 4.4 Fate 콜라보 — 시작만 뜨고 종료가 0 이라 통째로 필터링되고 있었다.
            // 다만 상류가 끝내 안 채우면 영원히 남으므로, 시작 후 [UNKNOWN_END_MAX_DAYS] 지나면 내린다.
            if (endMillis in 1..now) continue
            if (endMillis <= 0L && (startMillis <= 0L || now - startMillis > UNKNOWN_END_MAX_DAYS * DAY_MS)) continue
            val version = b.optString("version")

            val (items, isWeapon) = firstItems(b)
            val names = fiveStarNames(items).ifEmpty {
                b.optString("name").takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }
            names.forEach { name ->
                banners += GachaBanner(
                    game = game.displayName,
                    name = name,
                    type = if (isWeapon) "weapon" else "character",
                    endMillis = endMillis,
                    startMillis = startMillis,
                    version = version,
                )
            }
        }

        val events = mutableListOf<GameEvent>()
        val eventsArr = root.optJSONArray("events") ?: JSONArray()
        for (i in 0 until eventsArr.length()) {
            val e = eventsArr.optJSONObject(i) ?: continue
            val endMillis = e.optLong("end_time") * 1000
            if (endMillis <= now) continue
            events += GameEvent(game.displayName, e.optString("name"), endMillis, rewardOf(e))
        }
        events.sortBy { it.endMillis }

        val challenges = mutableListOf<GameChallenge>()
        val chArr = root.optJSONArray("challenges") ?: JSONArray()
        for (i in 0 until chArr.length()) {
            val c = chArr.optJSONObject(i) ?: continue
            val endMillis = c.optLong("end_time") * 1000
            if (endMillis <= now) continue
            val reward = rewardOf(c)
            challenges += GameChallenge(
                game = game.displayName,
                name = c.optString("name"),
                typeName = c.optString("type_name"),
                endMillis = endMillis,
                reward = reward,
            )
        }
        challenges.sortBy { it.endMillis }

        return EnneadResult(banners, events, challenges)
    }

    /** 이벤트/도전 보상 표기 — special_reward(원신·스타레일) 우선, 없으면 polychrome(젠레스 정수 필드). */
    private fun rewardOf(o: JSONObject): String {
        o.optJSONObject("special_reward")?.let { r ->
            val n = r.optString("name")
            val amt = r.optInt("amount", 0)
            if (n.isNotBlank()) return if (amt > 0) "$n ×$amt" else n
        }
        val poly = o.optInt("polychrome", 0)
        if (poly > 0) return "폴리크롬 ×$poly"
        return ""
    }

    /** 캐릭터(characters/agents/items) 우선, 없으면 무기(weapons=원신 / light_cones=스타레일 광추) + 무기 여부 */
    private fun firstItems(b: JSONObject): Pair<JSONArray, Boolean> {
        for (key in listOf("characters", "agents", "items")) {
            val arr = b.optJSONArray(key)
            if (arr != null && arr.length() > 0) return arr to false
        }
        for (key in listOf("weapons", "light_cones", "w_engines")) {
            val arr = b.optJSONArray(key)
            if (arr != null && arr.length() > 0) return arr to true
        }
        return JSONArray() to false
    }

    /** 5성(또는 S급) 아이템 이름. 없으면 첫 아이템. */
    private fun fiveStarNames(items: JSONArray): List<String> {
        if (items.length() == 0) return emptyList()
        val fiveStar = mutableListOf<String>()
        val all = mutableListOf<String>()
        for (i in 0 until items.length()) {
            val c = items.optJSONObject(i) ?: continue
            val name = c.optString("name")
            if (name.isBlank()) continue
            all += name
            val r = (c.opt("rarity") ?: c.opt("rank") ?: c.opt("grade") ?: "").toString().uppercase()
            if (r == "5" || r == "S" || (r.toIntOrNull() ?: 0) >= 5) fiveStar += name
        }
        return when {
            fiveStar.isNotEmpty() -> fiveStar
            all.isNotEmpty() -> listOf(all.first())
            else -> emptyList()
        }
    }
}
