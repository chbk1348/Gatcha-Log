package com.gatcha.log.data

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject
import com.gatcha.log.storage.KeyValueStore
import com.gatcha.log.storage.SecureKeyValueStore
import com.gatcha.log.util.currentTimeMillis

/**
 * 로컬 영속성 저장소 — :app 의 GatchaRepository 를 KMP 로 이식.
 * SharedPreferences → KeyValueStore, EncryptedSharedPreferences → SecureKeyValueStore(iOS Keychain).
 * 저장 키·JSON 직렬화 형식은 :app 과 완전히 동일 (클라우드 스냅샷 상호 호환).
 *
 * [accountId] 별로 별도 저장소 파일을 사용해 계정마다 데이터가 완전히 분리된다.
 */
class GatchaRepository(accountId: String = "guest") {

    private val safeId = accountId.ifBlank { "guest" }.replace(Regex("[^A-Za-z0-9]"), "_")
    private val prefs = KeyValueStore("gatcha_log_$safeId")

    /**
     * 인증 토큰 전용 암호화 저장소 (Android Keystore / iOS Keychain).
     * 평문 [prefs] 와 분리해 토큰만 암호화 보관하며, 스냅샷(클라우드/백업)에는 절대 포함하지 않는다.
     */
    private val securePrefs: SecureKeyValueStore by lazy { SecureKeyValueStore("gatcha_sec_$safeId") }

    init { migrateLegacyTokens() }

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
        prefs.putString(KEY_SPENDINGS, arr.toString())
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
        // gameColor 는 gameName 으로 항상 재계산 가능 → 저장 안 함(용량 절감, 로드 시 복원)
        put("tags", JSONArray(tags))
    }

    private fun JSONObject.toSpending(): Spending {
        val tagsArr = optJSONArray("tags") ?: JSONArray()
        val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }
        val gameName = optString("gameName", "원신")
        val color = if (has("gameColor")) (getInt("gameColor").toLong() and 0xFFFFFFFFL) else GameData.colorFor(gameName)
        return Spending(
            id = optString("id"),
            gameName = gameName,
            amount = optLong("amount", 0L),
            dateMillis = optLong("dateMillis", currentTimeMillis()),
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
    fun saveBudget(value: Long) { prefs.putLong(KEY_BUDGET, value); changed() }

    /** 게임별 월 한도(gameKey → 금액). 한도 없는 게임은 키 자체가 없음. 전체 예산[loadBudget]과 별개. */
    fun loadGameBudgets(): Map<String, Long> {
        val raw = prefs.getString(KEY_BUDGET_GAMES, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> o.optLong(k, 0L).takeIf { it > 0 }?.let { put(k, it) } } }
        }.getOrDefault(emptyMap())
    }

    fun saveGameBudgets(map: Map<String, Long>) {
        val o = JSONObject()
        map.forEach { (k, v) -> if (v > 0) o.put(k, v) }
        prefs.putString(KEY_BUDGET_GAMES, o.toString())
        changed()
    }

    // ---------------------------------------------------------------- 프로필
    fun loadProfile(): UserProfile = UserProfile(
        name = prefs.getString(KEY_PROFILE_NAME, "게스트") ?: "게스트",
        email = prefs.getString(KEY_PROFILE_EMAIL, "") ?: "",
    )

    fun saveProfile(profile: UserProfile) {
        prefs.putString(KEY_PROFILE_NAME, profile.name)
        prefs.putString(KEY_PROFILE_EMAIL, profile.email)
        changed()
    }

    // ---------------------------------------------------------------- HoYoLAB
    // 토큰(ltuid/ltoken/cookieToken/webCookie)은 암호화 저장소[securePrefs]에, UID 는 평문 [prefs]에 둔다.
    // 토큰은 스냅샷(클라우드/백업)에 포함되지 않으므로 기기 밖으로 나가지 않는다.
    fun loadHoyolab(): HoyolabConfig = HoyolabConfig(
        ltuid = securePrefs.getString(KEY_HOYO_LTUID, "") ?: "",
        ltoken = securePrefs.getString(KEY_HOYO_LTOKEN, "") ?: "",
        genshinUid = prefs.getString(KEY_HOYO_GI, "") ?: "",
        hsrUid = prefs.getString(KEY_HOYO_HSR, "") ?: "",
        zzzUid = prefs.getString(KEY_HOYO_ZZZ, "") ?: "",
        cookieToken = securePrefs.getString(KEY_HOYO_COOKIETOKEN, "") ?: "",
        webCookie = securePrefs.getString(KEY_HOYO_WEBCOOKIE, "") ?: "",
    )

    fun saveHoyolab(config: HoyolabConfig) {
        securePrefs.putString(KEY_HOYO_LTUID, config.ltuid)
        securePrefs.putString(KEY_HOYO_LTOKEN, config.ltoken)
        securePrefs.putString(KEY_HOYO_COOKIETOKEN, config.cookieToken)
        securePrefs.putString(KEY_HOYO_WEBCOOKIE, config.webCookie)
        prefs.putString(KEY_HOYO_GI, config.genshinUid)
        prefs.putString(KEY_HOYO_HSR, config.hsrUid)
        prefs.putString(KEY_HOYO_ZZZ, config.zzzUid)
        changed()
    }

    /** 평문 prefs 에 남아있던 기존 토큰을 최초 1회 암호화 저장소로 이전하고 평문 키는 삭제한다. */
    private fun migrateLegacyTokens() {
        val legacyKeys = listOf(KEY_HOYO_LTUID, KEY_HOYO_LTOKEN, KEY_HOYO_COOKIETOKEN, KEY_HOYO_WEBCOOKIE)
        if (legacyKeys.none { prefs.contains(it) }) return
        legacyKeys.forEach { k -> prefs.getString(k, null)?.let { securePrefs.putString(k, it) } }
        legacyKeys.forEach { prefs.remove(it) }
    }

    // ---------------------------------------------------------------- 테마 강조색
    fun loadAccentIndex(): Int = prefs.getInt(KEY_ACCENT, 0)
    fun saveAccentIndex(index: Int) { prefs.putInt(KEY_ACCENT, index); changed() }

    // ---------------------------------------------------------------- 홈 카드 구성(표시·순서)
    fun loadHomeCards(): List<HomeCardItem> {
        val s = prefs.getString(KEY_HOME_CARDS, null) ?: return HomeCards.default
        val parsed = runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).map { val o = arr.getJSONObject(it); HomeCardItem(o.optString("id"), o.optBoolean("v", true)) }
        }.getOrDefault(HomeCards.default)
        return HomeCards.normalize(parsed)
    }
    fun saveHomeCards(list: List<HomeCardItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("v", it.visible)) }
        prefs.putString(KEY_HOME_CARDS, arr.toString())
        changed()
    }

    // ---------------------------------------------------------------- Enka 프로필 UID (게임별)
    fun loadEnkaGiUid(): String = prefs.getString(KEY_ENKA_GI, "") ?: ""
    fun loadEnkaHsrUid(): String = prefs.getString(KEY_ENKA_HSR, "") ?: ""
    fun saveEnkaUids(gi: String, hsr: String) {
        prefs.putString(KEY_ENKA_GI, gi)
        prefs.putString(KEY_ENKA_HSR, hsr)
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
        prefs.putString(KEY_ATTENDANCE, obj.toString())
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
        prefs.putString(KEY_WISHLIST, obj.toString())
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
        prefs.putString(KEY_PITY, obj.toString())
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
        prefs.putString(KEY_EVENT_CHECKS, JSONArray(checks.toList()).toString())
        changed()
    }

    // ---------------------------------------------------------------- 교환한 선물코드 (자동수집 목록에서 '받음' 표시)
    // 스냅샷 동기화 대상 — 재설치·기기변경·계정전환에도 '받음'이 유지되도록(미동기화 시 받은 코드가 다시 '받기 가능'으로 노출됨).
    fun loadRedeemedCodes(): Set<String> {
        val raw = prefs.getString(KEY_REDEEMED, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveRedeemedCodes(codes: Set<String>) {
        prefs.putString(KEY_REDEEMED, JSONArray(codes.toList()).toString())
        changed() // 스냅샷 포함 → 변경 시 클라우드 동기화 트리거
    }

    // ---------------------------------------------------------------- 읽은 홈 알림 넛징 키 — 로컬 전용(기기별 UI 상태)
    fun loadReadAlerts(): Set<String> {
        val raw = prefs.getString(KEY_READ_ALERTS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveReadAlerts(keys: Set<String>) {
        // 스냅샷 미포함(로컬 전용) → changed() 미호출
        prefs.putString(KEY_READ_ALERTS, JSONArray(keys.toList()).toString())
    }

    // ---------------------------------------------------------------- 구독 관리 (정기결제)
    fun loadSubscriptions(): List<Subscription> = Subscriptions.fromJsonArray(prefs.getString(KEY_SUBS, null))
    fun saveSubscriptions(list: List<Subscription>) {
        prefs.putString(KEY_SUBS, Subscriptions.toJsonArray(list))
        changed()
    }

    // ---------------------------------------------------------------- 가챠 기록 (UIGF/SRGF)
    fun loadGachaRecords(): List<GachaRecord> = GachaReport.fromJsonArray(prefs.getString(KEY_GACHA, null))
    fun saveGachaRecords(records: List<GachaRecord>) {
        prefs.putString(KEY_GACHA, GachaReport.toJsonArray(records))
        changed() // 스냅샷(클라우드/파일 백업)에 포함되므로 변경 시 동기화 트리거
    }

    // ---------------------------------------------------------------- 스냅샷 (전체 데이터 직렬화 — 클라우드/파일 백업 공용)
    /** 계정의 모든 데이터를 단일 JSON 으로 직렬화(Firestore 저장·파일 백업용). */
    fun exportSnapshotJson(): String {
        val o = JSONObject()
        prefs.getString(KEY_SPENDINGS, null)?.let { o.put(KEY_SPENDINGS, JSONArray(it)) }
        o.put(KEY_BUDGET, loadBudget())
        prefs.getString(KEY_BUDGET_GAMES, null)?.let { o.put(KEY_BUDGET_GAMES, JSONObject(it)) }
        prefs.getString(KEY_PROFILE_NAME, null)?.let { o.put(KEY_PROFILE_NAME, it) }
        prefs.getString(KEY_PROFILE_EMAIL, null)?.let { o.put(KEY_PROFILE_EMAIL, it) }
        // 토큰(ltuid/ltoken/cookieToken/webCookie)은 보안상 스냅샷에 절대 포함하지 않는다(암호화 저장소 전용).
        // 게임 UID 만 포함 — 토큰이 아니므로 기기 간 동기화에 필요.
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
        prefs.getString(KEY_GACHA, null)?.let { o.put(KEY_GACHA, JSONArray(it)) }
        prefs.getString(KEY_HOME_CARDS, null)?.let { o.put(KEY_HOME_CARDS, JSONArray(it)) }
        prefs.getString(KEY_REDEEMED, null)?.let { o.put(KEY_REDEEMED, JSONArray(it)) }
        return o.toString()
    }

    /** Firestore/백업 파일에서 받은 스냅샷 JSON 을 로컬에 반영. (onChange 미발생 → 푸시 루프 방지) */
    fun importSnapshotJson(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        if (o.has(KEY_SPENDINGS)) prefs.putString(KEY_SPENDINGS, o.getJSONArray(KEY_SPENDINGS).toString())
        if (o.has(KEY_BUDGET)) prefs.putLong(KEY_BUDGET, o.getLong(KEY_BUDGET))
        if (o.has(KEY_BUDGET_GAMES)) prefs.putString(KEY_BUDGET_GAMES, o.getJSONObject(KEY_BUDGET_GAMES).toString())
        if (o.has(KEY_PROFILE_NAME)) prefs.putString(KEY_PROFILE_NAME, o.getString(KEY_PROFILE_NAME))
        if (o.has(KEY_PROFILE_EMAIL)) prefs.putString(KEY_PROFILE_EMAIL, o.getString(KEY_PROFILE_EMAIL))
        // 토큰 키는 스냅샷에서 의도적으로 제외 — 구버전 클라우드/백업에 토큰이 남아 있어도 가져오지 않는다.
        if (o.has(KEY_HOYO_GI)) prefs.putString(KEY_HOYO_GI, o.getString(KEY_HOYO_GI))
        if (o.has(KEY_HOYO_HSR)) prefs.putString(KEY_HOYO_HSR, o.getString(KEY_HOYO_HSR))
        if (o.has(KEY_HOYO_ZZZ)) prefs.putString(KEY_HOYO_ZZZ, o.getString(KEY_HOYO_ZZZ))
        if (o.has(KEY_ACCENT)) prefs.putInt(KEY_ACCENT, o.getInt(KEY_ACCENT))
        if (o.has(KEY_ENKA_GI)) prefs.putString(KEY_ENKA_GI, o.getString(KEY_ENKA_GI))
        if (o.has(KEY_ENKA_HSR)) prefs.putString(KEY_ENKA_HSR, o.getString(KEY_ENKA_HSR))
        if (o.has(KEY_ATTENDANCE)) prefs.putString(KEY_ATTENDANCE, o.getJSONObject(KEY_ATTENDANCE).toString())
        if (o.has(KEY_WISHLIST)) prefs.putString(KEY_WISHLIST, o.getJSONObject(KEY_WISHLIST).toString())
        if (o.has(KEY_PITY)) prefs.putString(KEY_PITY, o.getJSONObject(KEY_PITY).toString())
        if (o.has(KEY_EVENT_CHECKS)) prefs.putString(KEY_EVENT_CHECKS, o.getJSONArray(KEY_EVENT_CHECKS).toString())
        if (o.has(KEY_SUBS)) prefs.putString(KEY_SUBS, o.getJSONArray(KEY_SUBS).toString())
        if (o.has(KEY_GACHA)) prefs.putString(KEY_GACHA, o.getJSONArray(KEY_GACHA).toString())
        if (o.has(KEY_HOME_CARDS)) prefs.putString(KEY_HOME_CARDS, o.getJSONArray(KEY_HOME_CARDS).toString())
        // 교환한 코드는 **합집합 병합**(덮어쓰기 금지) — 오래된/빈 스냅샷이 로컬 '받음'을 되돌리지 않도록(받음은 단조 증가).
        if (o.has(KEY_REDEEMED)) {
            val arr = o.getJSONArray(KEY_REDEEMED)
            val incoming = (0 until arr.length()).map { arr.getString(it) }
            val merged = loadRedeemedCodes() + incoming
            prefs.putString(KEY_REDEEMED, JSONArray(merged.toList()).toString())
        }
    }

    private companion object {
        const val KEY_WISHLIST = "wishlist"
        const val KEY_PITY = "pity"
        const val KEY_EVENT_CHECKS = "event_checks"
        const val KEY_REDEEMED = "redeemed_codes"
        const val KEY_READ_ALERTS = "read_alerts"
        const val KEY_SPENDINGS = "spendings"
        const val KEY_BUDGET = "budget"
        const val KEY_BUDGET_GAMES = "budget_games"
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_PROFILE_EMAIL = "profile_email"
        const val KEY_HOYO_LTUID = "hoyo_ltuid"
        const val KEY_HOYO_LTOKEN = "hoyo_ltoken"
        const val KEY_HOYO_GI = "hoyo_gi"
        const val KEY_HOYO_HSR = "hoyo_hsr"
        const val KEY_HOYO_ZZZ = "hoyo_zzz"
        const val KEY_HOYO_COOKIETOKEN = "hoyo_cookietoken"
        const val KEY_HOYO_WEBCOOKIE = "hoyo_webcookie"
        const val KEY_ACCENT = "accent_index"
        const val KEY_ATTENDANCE = "attendance"
        const val KEY_ENKA_GI = "enka_gi"
        const val KEY_ENKA_HSR = "enka_hsr"
        const val KEY_GACHA = "gacha_records"
        const val KEY_SUBS = "subscriptions"
        const val KEY_HOME_CARDS = "home_cards"
    }
}
