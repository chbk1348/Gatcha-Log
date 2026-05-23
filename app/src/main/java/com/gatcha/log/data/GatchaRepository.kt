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

    /** 데이터가 저장될 때마다 호출(클라우드 동기화 트리거용). 스냅샷 import 시에는 호출되지 않는다. */
    var onChange: (() -> Unit)? = null
    private fun changed() = onChange?.invoke()

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
        changed()
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
    fun loadBudget(): Long = prefs.getLong(KEY_BUDGET, 0L) // 0 = 미설정(사용자가 지정해야 함)
    fun saveBudget(value: Long) { prefs.edit().putLong(KEY_BUDGET, value).apply(); changed() }

    // ---------------------------------------------------------------- 프로필
    fun loadProfile(): UserProfile = UserProfile(
        name = prefs.getString(KEY_PROFILE_NAME, "게스트") ?: "게스트",
        email = prefs.getString(KEY_PROFILE_EMAIL, "") ?: "",
    )

    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_PROFILE_NAME, profile.name)
            .putString(KEY_PROFILE_EMAIL, profile.email)
            .apply()
        changed()
    }

    // ---------------------------------------------------------------- HoYoLAB
    fun loadHoyolab(): HoyolabConfig = HoyolabConfig(
        ltuid = prefs.getString(KEY_HOYO_LTUID, "") ?: "",
        ltoken = prefs.getString(KEY_HOYO_LTOKEN, "") ?: "",
        genshinUid = prefs.getString(KEY_HOYO_GI, "") ?: "",
        hsrUid = prefs.getString(KEY_HOYO_HSR, "") ?: "",
        zzzUid = prefs.getString(KEY_HOYO_ZZZ, "") ?: "",
    )

    fun saveHoyolab(config: HoyolabConfig) {
        prefs.edit()
            .putString(KEY_HOYO_LTUID, config.ltuid)
            .putString(KEY_HOYO_LTOKEN, config.ltoken)
            .putString(KEY_HOYO_GI, config.genshinUid)
            .putString(KEY_HOYO_HSR, config.hsrUid)
            .putString(KEY_HOYO_ZZZ, config.zzzUid)
            .apply()
        changed()
    }

    // ---------------------------------------------------------------- 테마 강조색
    fun loadAccentIndex(): Int = prefs.getInt(KEY_ACCENT, 0)
    fun saveAccentIndex(index: Int) { prefs.edit().putInt(KEY_ACCENT, index).apply(); changed() }

    // ---------------------------------------------------------------- Enka 프로필 UID (게임별)
    fun loadEnkaGiUid(): String = prefs.getString(KEY_ENKA_GI, "") ?: ""
    fun loadEnkaHsrUid(): String = prefs.getString(KEY_ENKA_HSR, "") ?: ""
    fun saveEnkaUids(gi: String, hsr: String) {
        prefs.edit().putString(KEY_ENKA_GI, gi).putString(KEY_ENKA_HSR, hsr).apply()
        changed()
    }

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
        changed()
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
        changed()
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
        changed()
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
        changed()
    }

    // ---------------------------------------------------------------- 구독 관리 (정기결제)
    fun loadSubscriptions(): List<Subscription> = Subscriptions.fromJsonArray(prefs.getString(KEY_SUBS, null))
    fun saveSubscriptions(list: List<Subscription>) {
        prefs.edit().putString(KEY_SUBS, Subscriptions.toJsonArray(list)).apply()
        changed()
    }

    // ---------------------------------------------------------------- 가챠 기록 (UIGF/SRGF) — 로컬 전용(용량상 클라우드 스냅샷 제외)
    fun loadGachaRecords(): List<GachaRecord> = GachaReport.fromJsonArray(prefs.getString(KEY_GACHA, null))
    fun saveGachaRecords(records: List<GachaRecord>) {
        prefs.edit().putString(KEY_GACHA, GachaReport.toJsonArray(records)).apply()
    }

    // ---------------------------------------------------------------- 클라우드 스냅샷 (전체 데이터 직렬화)
    /** 계정의 모든 데이터를 단일 JSON 으로 직렬화(Firestore 저장용). */
    fun exportSnapshotJson(): String {
        val o = JSONObject()
        prefs.getString(KEY_SPENDINGS, null)?.let { o.put(KEY_SPENDINGS, JSONArray(it)) }
        o.put(KEY_BUDGET, loadBudget())
        prefs.getString(KEY_PROFILE_NAME, null)?.let { o.put(KEY_PROFILE_NAME, it) }
        prefs.getString(KEY_PROFILE_EMAIL, null)?.let { o.put(KEY_PROFILE_EMAIL, it) }
        prefs.getString(KEY_HOYO_LTUID, null)?.let { o.put(KEY_HOYO_LTUID, it) }
        prefs.getString(KEY_HOYO_LTOKEN, null)?.let { o.put(KEY_HOYO_LTOKEN, it) }
        prefs.getString(KEY_HOYO_GI, null)?.let { o.put(KEY_HOYO_GI, it) }
        prefs.getString(KEY_HOYO_HSR, null)?.let { o.put(KEY_HOYO_HSR, it) }
        prefs.getString(KEY_HOYO_ZZZ, null)?.let { o.put(KEY_HOYO_ZZZ, it) }
        o.put(KEY_ACCENT, loadAccentIndex())
        prefs.getString(KEY_ENKA_GI, null)?.let { o.put(KEY_ENKA_GI, it) }
        prefs.getString(KEY_ENKA_HSR, null)?.let { o.put(KEY_ENKA_HSR, it) }
        prefs.getString(KEY_ATTENDANCE, null)?.let { o.put(KEY_ATTENDANCE, JSONObject(it)) }
        prefs.getString(KEY_WISHLIST, null)?.let { o.put(KEY_WISHLIST, JSONObject(it)) }
        prefs.getString(KEY_PITY, null)?.let { o.put(KEY_PITY, JSONObject(it)) }
        prefs.getString(KEY_EVENT_CHECKS, null)?.let { o.put(KEY_EVENT_CHECKS, JSONArray(it)) }
        prefs.getString(KEY_SUBS, null)?.let { o.put(KEY_SUBS, JSONArray(it)) }
        return o.toString()
    }

    /** Firestore 등에서 받은 스냅샷 JSON 을 로컬에 반영. (onChange 미발생 → 푸시 루프 방지) */
    fun importSnapshotJson(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        prefs.edit().apply {
            if (o.has(KEY_SPENDINGS)) putString(KEY_SPENDINGS, o.getJSONArray(KEY_SPENDINGS).toString())
            if (o.has(KEY_BUDGET)) putLong(KEY_BUDGET, o.getLong(KEY_BUDGET))
            if (o.has(KEY_PROFILE_NAME)) putString(KEY_PROFILE_NAME, o.getString(KEY_PROFILE_NAME))
            if (o.has(KEY_PROFILE_EMAIL)) putString(KEY_PROFILE_EMAIL, o.getString(KEY_PROFILE_EMAIL))
            if (o.has(KEY_HOYO_LTUID)) putString(KEY_HOYO_LTUID, o.getString(KEY_HOYO_LTUID))
            if (o.has(KEY_HOYO_LTOKEN)) putString(KEY_HOYO_LTOKEN, o.getString(KEY_HOYO_LTOKEN))
            if (o.has(KEY_HOYO_GI)) putString(KEY_HOYO_GI, o.getString(KEY_HOYO_GI))
            if (o.has(KEY_HOYO_HSR)) putString(KEY_HOYO_HSR, o.getString(KEY_HOYO_HSR))
            if (o.has(KEY_HOYO_ZZZ)) putString(KEY_HOYO_ZZZ, o.getString(KEY_HOYO_ZZZ))
            if (o.has(KEY_ACCENT)) putInt(KEY_ACCENT, o.getInt(KEY_ACCENT))
            if (o.has(KEY_ENKA_GI)) putString(KEY_ENKA_GI, o.getString(KEY_ENKA_GI))
            if (o.has(KEY_ENKA_HSR)) putString(KEY_ENKA_HSR, o.getString(KEY_ENKA_HSR))
            if (o.has(KEY_ATTENDANCE)) putString(KEY_ATTENDANCE, o.getJSONObject(KEY_ATTENDANCE).toString())
            if (o.has(KEY_WISHLIST)) putString(KEY_WISHLIST, o.getJSONObject(KEY_WISHLIST).toString())
            if (o.has(KEY_PITY)) putString(KEY_PITY, o.getJSONObject(KEY_PITY).toString())
            if (o.has(KEY_EVENT_CHECKS)) putString(KEY_EVENT_CHECKS, o.getJSONArray(KEY_EVENT_CHECKS).toString())
            if (o.has(KEY_SUBS)) putString(KEY_SUBS, o.getJSONArray(KEY_SUBS).toString())
        }.apply()
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
        const val KEY_ENKA_GI = "enka_gi"
        const val KEY_ENKA_HSR = "enka_hsr"
        const val KEY_GACHA = "gacha_records"
        const val KEY_SUBS = "subscriptions"
    }
}