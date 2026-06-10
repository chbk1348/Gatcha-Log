package com.gatcha.log.data.api

import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameChallenge
import com.gatcha.log.data.GameEvent
import org.json.JSONArray
import org.json.JSONObject

data class EnneadResult(
    val banners: List<GachaBanner>,
    val events: List<GameEvent>,
    val challenges: List<GameChallenge> = emptyList(),
)

/**
 * ennead.cc 캘린더 API — 픽업 배너 / 이벤트 (원신·스타레일).
 * 인증 불필요. hoyoverse 경로 우선, 404 시 mihoyo 경로로 폴백 (웹앱 _fetchEnneadCalendar_ 와 동일).
 */
object EnneadApi {

    suspend fun fetch(game: Game): EnneadResult {
        val key = game.enneadKey ?: return EnneadResult(emptyList(), emptyList())

        var res = Net.get("https://api.ennead.cc/hoyoverse/$key/calendar?lang=ko-kr")
        if (res.code == 404) {
            res = Net.get("https://api.ennead.cc/mihoyo/$key/calendar?lang=ko-kr")
        }
        if (!res.isOk) return EnneadResult(emptyList(), emptyList())

        return runCatching { parse(game, JSONObject(res.body)) }
            .getOrDefault(EnneadResult(emptyList(), emptyList()))
    }

    private fun parse(game: Game, root: JSONObject): EnneadResult {
        val now = System.currentTimeMillis()

        val banners = mutableListOf<GachaBanner>()
        val bannersArr = root.optJSONArray("banners") ?: JSONArray()
        for (i in 0 until bannersArr.length()) {
            val b = bannersArr.optJSONObject(i) ?: continue
            val endMillis = b.optLong("end_time") * 1000
            if (endMillis <= now) continue
            val startMillis = b.optLong("start_time") * 1000
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
            val reward = e.optJSONObject("special_reward")?.let { r ->
                val n = r.optString("name")
                val amt = r.optInt("amount", 0)
                if (n.isNotBlank()) (if (amt > 0) "$n ×$amt" else n) else ""
            } ?: ""
            events += GameEvent(game.displayName, e.optString("name"), endMillis, reward)
        }
        events.sortBy { it.endMillis }

        val challenges = mutableListOf<GameChallenge>()
        val chArr = root.optJSONArray("challenges") ?: JSONArray()
        for (i in 0 until chArr.length()) {
            val c = chArr.optJSONObject(i) ?: continue
            val endMillis = c.optLong("end_time") * 1000
            if (endMillis <= now) continue
            val reward = c.optJSONObject("special_reward")?.let { r ->
                val n = r.optString("name")
                val amt = r.optInt("amount", 0)
                if (n.isNotBlank()) (if (amt > 0) "$n ×$amt" else n) else ""
            } ?: ""
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

    /** characters / agents / items / weapons 중 첫 번째 비어있지 않은 배열 + 무기 여부 */
    private fun firstItems(b: JSONObject): Pair<JSONArray, Boolean> {
        for (key in listOf("characters", "agents", "items")) {
            val arr = b.optJSONArray(key)
            if (arr != null && arr.length() > 0) return arr to false
        }
        val weapons = b.optJSONArray("weapons")
        if (weapons != null && weapons.length() > 0) return weapons to true
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
