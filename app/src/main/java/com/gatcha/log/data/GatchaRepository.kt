package com.gatcha.log.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/**
 * 로컬 영속성 저장소. 추가 의존성 없이 SharedPreferences + org.json 으로 직렬화한다.
 * (Room/KSP 미사용 — 빌드 플러그인 추가 없이 동작)
 *
 * [accountId] 별로 별도 prefs 파일을 사용해 계정마다 데이터가 완전히 분리된다.
 */
class GatchaRepository(context: Context, accountId: String = "guest") {

    private val safeId = accountId.ifBlank { "guest" }.replace(Regex("[^A-Za-z0-9]"), "_")
    private val prefs = context.applicationContext
        .getSharedPreferences("gatcha_log_$safeId", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 지출
    fun loadSpendings(): List<Spending> {
        val raw = prefs.getString(KEY_SPENDINGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.getJSONObject(i).toSpending() }
        }.getOrDefault(emptyList())
    }

    fun saveSpendings(list: List<Spending>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SPENDINGS, arr.toString()).apply()
    }

    private fun Spending.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("gameName", gameName)
        put("amount", amount)
        put("dateMillis", dateMillis)
        put("paymentMethod", paymentMethod)
        put("itemName", itemName)
        put("memo", memo)
        put("isSubscription", isSubscription)
        put("gameColor", gameColor.toArgb())
        put("tags", JSONArray(tags))
    }

    private fun JSONObject.toSpending(): Spending {
        val tagsArr = optJSONArray("tags") ?: JSONArray()
        val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }
        val gameName = optString("gameName", "원신")
        val color = if (has("gameColor")) Color(getInt("gameColor")) else GameData.colorFor(gameName)
        return Spending(
            id = optString("id"),
            gameName = gameName,
            amount = optLong("amount", 0L),
            dateMillis = optLong("dateMillis", System.currentTimeMillis()),
            paymentMethod = optString("paymentMethod", "신용카드"),
            itemName = optString("itemName", ""),
            memo = optString("memo", ""),
            tags = tags,
            isSubscription = optBoolean("isSubscription", false),
            gameColor = color,
        )
    }

    // ---------------------------------------------------------------- 예산
    fun loadBudget(): Long = prefs.getLong(KEY_BUDGET, 300_000L)
    fun saveBudget(value: Long) = prefs.edit().putLong(KEY_BUDGET, value).apply()

    // ---------------------------------------------------------------- 프로필
    fun loadProfile(): UserProfile = UserProfile(
        name = prefs.getString(KEY_PROFILE_NAME, "유키냥") ?: "유키냥",
        email = prefs.getString(KEY_PROFILE_EMAIL, "yukinyang@example.com") ?: "yukinyang@example.com",
    )

    fun saveProfile(profile: UserProfile) = prefs.edit()
        .putString(KEY_PROFILE_NAME, profile.name)
        .putString(KEY_PROFILE_EMAIL, profile.email)
        .apply()

    // ---------------------------------------------------------------- HoYoLAB
    fun loadHoyolab(): HoyolabConfig = HoyolabConfig(
        ltuid = prefs.getString(KEY_HOYO_LTUID, "") ?: "",
        ltoken = prefs.getString(KEY_HOYO_LTOKEN, "") ?: "",
        genshinUid = prefs.getString(KEY_HOYO_GI, "") ?: "",
        hsrUid = prefs.getString(KEY_HOYO_HSR, "") ?: "",
        zzzUid = prefs.getString(KEY_HOYO_ZZZ, "") ?: "",
    )

    fun saveHoyolab(config: HoyolabConfig) = prefs.edit()
        .putString(KEY_HOYO_LTUID, config.ltuid)
        .putString(KEY_HOYO_LTOKEN, config.ltoken)
        .putString(KEY_HOYO_GI, config.genshinUid)
        .putString(KEY_HOYO_HSR, config.hsrUid)
        .putString(KEY_HOYO_ZZZ, config.zzzUid)
        .apply()

    // ---------------------------------------------------------------- 테마 강조색
    fun loadAccentIndex(): Int = prefs.getInt(KEY_ACCENT, 0)
    fun saveAccentIndex(index: Int) = prefs.edit().putInt(KEY_ACCENT, index).apply()

    // ---------------------------------------------------------------- 출석 (dayKey -> set<gameKey>)
    fun loadAttendance(): Map<String, Set<String>> {
        val raw = prefs.getString(KEY_ATTENDANCE, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { day ->
                    val arr = obj.getJSONArray(day)
                    put(day, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveAttendance(map: Map<String, Set<String>>) {
        val obj = JSONObject()
        map.forEach { (day, set) -> obj.put(day, JSONArray(set.toList())) }
        prefs.edit().putString(KEY_ATTENDANCE, obj.toString()).apply()
    }

    // ---------------------------------------------------------------- 위시리스트 (gameKey -> names)
    fun loadWishlist(): Map<String, List<String>> {
        val raw = prefs.getString(KEY_WISHLIST, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { g ->
                    val arr = obj.getJSONArray(g)
                    put(g, (0 until arr.length()).map { arr.getString(it) })
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveWishlist(map: Map<String, List<String>>) {
        val obj = JSONObject()
        map.forEach { (g, list) -> obj.put(g, JSONArray(list)) }
        prefs.edit().putString(KEY_WISHLIST, obj.toString()).apply()
    }

    // ---------------------------------------------------------------- 천장 카운터 (gameKey -> PityState)
    fun loadPity(): Map<String, PityState> {
        val raw = prefs.getString(KEY_PITY, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { g ->
                    val o = obj.getJSONObject(g)
                    put(g, PityState(o.optInt("count"), o.optBoolean("guaranteed")))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun savePity(map: Map<String, PityState>) {
        val obj = JSONObject()
        map.forEach { (g, s) -> obj.put(g, JSONObject().put("count", s.count).put("guaranteed", s.guaranteed)) }
        prefs.edit().putString(KEY_PITY, obj.toString()).apply()
    }

    // ---------------------------------------------------------------- 이벤트 체크리스트
    fun loadEventChecks(): Set<String> {
        val raw = prefs.getString(KEY_EVENT_CHECKS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveEventChecks(checks: Set<String>) {
        prefs.edit().putString(KEY_EVENT_CHECKS, JSONArray(checks.toList()).toString()).apply()
    }

    private companion object {
        const val KEY_WISHLIST = "wishlist"
        const val KEY_PITY = "pity"
        const val KEY_EVENT_CHECKS = "event_checks"
        const val KEY_SPENDINGS = "spendings"
        const val KEY_BUDGET = "budget"
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_PROFILE_EMAIL = "profile_email"
        const val KEY_HOYO_LTUID = "hoyo_ltuid"
        const val KEY_HOYO_LTOKEN = "hoyo_ltoken"
        const val KEY_HOYO_GI = "hoyo_gi"
        const val KEY_HOYO_HSR = "hoyo_hsr"
        const val KEY_HOYO_ZZZ = "hoyo_zzz"
        const val KEY_ACCENT = "accent_index"
        const val KEY_ATTENDANCE = "attendance"
    }
}