package com.gatcha.log.data

import com.gatcha.log.data.api.EnkaResult
import com.gatcha.log.data.api.NewsItem
import com.gatcha.log.data.api.NewsSource
import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject
import com.gatcha.log.storage.KeyValueStore
import com.gatcha.log.storage.KvStore
import com.gatcha.log.storage.SecureKeyValueStore
import com.gatcha.log.storage.SecureStore
import com.gatcha.log.storage.asKvStore
import com.gatcha.log.storage.asSecureStore
import com.gatcha.log.util.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Enka 프로필 결과 디스크 캐시 엔트리 — 타임스탬프 + 직렬화된 결과. */
@Serializable
private data class EnkaCacheEntry(val ts: Long, val result: EnkaResult)

/**
 * 계정 id → 저장소 파일명 정규화. 함수 안에서 컴파일하면 repo 생성마다(= 앱 시작·계정 전환·
 * 백그라운드 워커마다) 패턴 파싱을 다시 한다 — 정규식은 매칭보다 컴파일이 비싸다.
 */
private val SAFE_ID_RE = Regex("[^A-Za-z0-9]")

/**
 * 로컬 영속성 저장소 — :app 의 GatchaRepository 를 KMP 로 이식.
 * SharedPreferences → KeyValueStore, EncryptedSharedPreferences → SecureKeyValueStore(iOS Keychain).
 * 저장 키·JSON 직렬화 형식은 :app 과 완전히 동일 (클라우드 스냅샷 상호 호환).
 *
 * [accountId] 별로 별도 저장소 파일을 사용해 계정마다 데이터가 완전히 분리된다.
 */
class GatchaRepository(
    accountId: String = "guest",
    /**
     * 저장소 생성자. 기본값은 플랫폼 구현([KeyValueStore])이고, **테스트에서만** 인메모리로 갈아끼운다.
     * 이 이음매가 없으면 Android actual 이 `AppContext` 전역을 읽어 호스트 테스트에서 repo 를
     * 만들 수조차 없다 — 스냅샷 바이트 동일성 같은 핵심 불변식이 무검증으로 남는다.
     */
    storeFactory: (String) -> KvStore = { KeyValueStore(it).asKvStore() },
    secureFactory: (String) -> SecureStore = { SecureKeyValueStore(it).asSecureStore() },
) {

    private val safeId = accountId.ifBlank { "guest" }.replace(SAFE_ID_RE, "_")
    private val prefs: KvStore = storeFactory("gatcha_log_$safeId")

    /**
     * 인증 토큰 전용 암호화 저장소 (Android Keystore / iOS Keychain).
     * 평문 [prefs] 와 분리해 토큰만 암호화 보관하며, 스냅샷(클라우드/백업)에는 절대 포함하지 않는다.
     */
    private val securePrefs: SecureStore by lazy { secureFactory("gatcha_sec_$safeId") }

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
        put("chargePlatform", chargePlatform)
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
            // 레거시 정규화: 신용/체크카드 → 카드. 옛 결제수단의 구글플레이/앱스토어는
            // 의미상 '충전 플랫폼'이므로 paymentMethod 는 카드로 두고 chargePlatform 으로 이동.
            paymentMethod = optString("paymentMethod", "카드").let { m ->
                when (m) {
                    "신용카드", "체크카드", "구글 플레이", "앱스토어" -> "카드"
                    else -> m
                }
            },
            // 충전 플랫폼은 선택 항목 → 모르면 빈 값 유지. 옛 스토어 결제만 플랫폼으로 이동.
            chargePlatform = optString("chargePlatform", "").ifBlank {
                when (optString("paymentMethod", "")) {
                    "구글 플레이" -> "구글플레이스토어"
                    "앱스토어" -> "앱스토어"
                    else -> ""
                }
            },
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
    /**
     * [loadHoyolab] 결과 캐시.
     *
     * 토큰 4개가 보안 저장소(iOS Keychain / Android EncryptedSharedPreferences)에 있는데,
     * Keychain 읽기는 건당 securityd IPC 라 싸지 않다. loadAll 한 번이 4회를 왕복하고
     * 당겨서 새로고침이면 계정 승계까지 얽혀 20회를 넘기기도 했다.
     * 이 저장소 인스턴스는 계정마다 새로 만들어지므로(계정 전환 = 새 인스턴스) 인스턴스 캐시로 충분하다.
     */
    private var hoyolabCache: HoyolabConfig? = null

    fun loadHoyolab(): HoyolabConfig = hoyolabCache ?: HoyolabConfig(
        ltuid = securePrefs.getString(KEY_HOYO_LTUID, "") ?: "",
        ltoken = securePrefs.getString(KEY_HOYO_LTOKEN, "") ?: "",
        genshinUid = prefs.getString(KEY_HOYO_GI, "") ?: "",
        hsrUid = prefs.getString(KEY_HOYO_HSR, "") ?: "",
        zzzUid = prefs.getString(KEY_HOYO_ZZZ, "") ?: "",
        cookieToken = securePrefs.getString(KEY_HOYO_COOKIETOKEN, "") ?: "",
        webCookie = securePrefs.getString(KEY_HOYO_WEBCOOKIE, "") ?: "",
    ).also { hoyolabCache = it }

    /**
     * @return 토큰이 암호화 저장소에 실제로 들어갔는지. false 면 보안 저장소를 못 써서
     * **토큰이 저장되지 않았다**(평문 폴백 없음) — 호출부는 [secureStorageError] 를 사용자에게 알려야 한다.
     */
    fun saveHoyolab(config: HoyolabConfig): Boolean {
        val tokens = listOf(
            KEY_HOYO_LTUID to config.ltuid,
            KEY_HOYO_LTOKEN to config.ltoken,
            KEY_HOYO_COOKIETOKEN to config.cookieToken,
            KEY_HOYO_WEBCOOKIE to config.webCookie,
        )
        val hasToken = tokens.any { (_, v) -> v.isNotBlank() }
        val secureOk = tokens.map { (k, v) -> securePrefs.putString(k, v) }.all { it }

        prefs.putString(KEY_HOYO_GI, config.genshinUid)
        prefs.putString(KEY_HOYO_HSR, config.hsrUid)
        prefs.putString(KEY_HOYO_ZZZ, config.zzzUid)
        hoyolabCache = if (secureOk) config else null   // 저장 실패 시엔 캐시하지 않고 다음에 다시 읽는다
        changed()
        return !hasToken || secureOk
    }

    /** 보안 저장소(EncryptedSharedPreferences/Keychain)를 못 쓰는 사유. 정상이면 null. */
    val secureStorageError: String? get() = securePrefs.lastError

    /** 평문 prefs 에 남아있던 기존 토큰을 최초 1회 암호화 저장소로 이전하고 평문 키는 삭제한다. */
    private fun migrateLegacyTokens() {
        val legacyKeys = listOf(KEY_HOYO_LTUID, KEY_HOYO_LTOKEN, KEY_HOYO_COOKIETOKEN, KEY_HOYO_WEBCOOKIE)
        if (legacyKeys.none { prefs.contains(it) }) return
        // 암호화 저장에 실패하면 평문 키를 지우지 않는다 — 지우면 토큰이 통째로 증발한다.
        val moved = legacyKeys.all { k ->
            val v = prefs.getString(k, null) ?: return@all true
            securePrefs.putString(k, v)
        }
        if (moved) legacyKeys.forEach { prefs.remove(it) }
    }

    // ---------------------------------------------------------------- 테마 강조색
    fun loadAccentIndex(): Int = prefs.getInt(KEY_ACCENT, 0)
    fun saveAccentIndex(index: Int) { prefs.putInt(KEY_ACCENT, index); changed() }

    // 홈 카드 구성(표시·순서)은 27.43.0 에서 제거했다 — 27.32.0 홈 대시보드 개편(`9779244`) 때
    // 양 플랫폼 렌더 루프가 사라져 저장만 되고 화면엔 반영되지 않는 상태였다.
    // KEY_HOME_CARDS 는 **스냅샷 통과용으로만** 남긴다(구버전 앱과 클라우드 공존).

    // ---------------------------------------------------------------- Enka 프로필 UID (게임별)
    fun loadEnkaGiUid(): String = prefs.getString(KEY_ENKA_GI, "") ?: ""
    fun loadEnkaHsrUid(): String = prefs.getString(KEY_ENKA_HSR, "") ?: ""
    fun saveEnkaUids(gi: String, hsr: String) {
        prefs.putString(KEY_ENKA_GI, gi)
        prefs.putString(KEY_ENKA_HSR, hsr)
        changed()
    }

    // ---------------------------------------------------------------- Enka 프로필 결과 디스크 캐시
    // 앱 재시작 시 '내 캐릭터'를 즉시 표시(stale-while-revalidate)하기 위한 로컬 전용 캐시.
    // 클라우드 스냅샷 비포함(SECTION_* 미등록) · changed() 호출 안 함.
    private val enkaJson = Json { ignoreUnknownKeys = true }

    /** "game:uid" → (타임스탬프, 결과). [maxAgeMs] 보다 오래된 항목은 제외. */
    fun loadEnkaCache(maxAgeMs: Long): Map<String, Pair<Long, EnkaResult>> {
        val raw = prefs.getString(KEY_ENKA_CACHE, null) ?: return emptyMap()
        return runCatching {
            val now = currentTimeMillis()
            enkaJson.decodeFromString<Map<String, EnkaCacheEntry>>(raw)
                .filterValues { now - it.ts < maxAgeMs }
                .mapValues { it.value.ts to it.value.result }
        }.getOrDefault(emptyMap())
    }

    fun saveEnkaCache(cache: Map<String, Pair<Long, EnkaResult>>) {
        val map = cache.mapValues { EnkaCacheEntry(it.value.first, it.value.second) }
        runCatching { prefs.putString(KEY_ENKA_CACHE, enkaJson.encodeToString(map)) }
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

    /**
     * 삭제된 지출 id(tombstone). 지출은 import 시 id 합집합으로 병합하므로(구/스테일 스냅샷이 최신 항목을
     * 지우지 못하게), 실제 삭제는 이 tombstone 으로만 전파한다. 단조 누적, 상한 2000(오래된 것부터 폐기).
     */
    fun loadDeletedSpendingIds(): Set<String> {
        val raw = prefs.getString(KEY_DELETED_SPENDINGS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    /** tombstone 합집합 추가(단조). changed() 는 호출부(saveSpendings)가 트리거하므로 여기선 생략. */
    fun addDeletedSpendingIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        val merged = (loadDeletedSpendingIds() + ids).toList().let { if (it.size > 2000) it.takeLast(2000) else it }
        prefs.putString(KEY_DELETED_SPENDINGS, JSONArray(merged).toString())
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

    // ---------------------------------------------------------------- 삭제(dismiss)한 홈 알림 키 — 로컬 전용(기기별 UI 상태)
    // 계산형 알림이라 조건이 유지되면 다시 뜨므로, 사용자가 지운 알림은 키로 영구 보관해 재노출을 막는다.
    // 키에 기간(월·날짜·배너명)이 들어 있어 다음 기간엔 자연 만료된다(readAlerts 와 동일 규칙).
    fun loadDismissedAlerts(): Set<String> {
        val raw = prefs.getString(KEY_DISMISSED_ALERTS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveDismissedAlerts(keys: Set<String>) {
        // 스냅샷 미포함(로컬 전용) → changed() 미호출
        prefs.putString(KEY_DISMISSED_ALERTS, JSONArray(keys.toList()).toString())
    }

    // ---------------------------------------------------------------- 구독 관리 (정기결제)
    fun loadSubscriptions(): List<Subscription> = Subscriptions.fromJsonArray(prefs.getString(KEY_SUBS, null))
    fun saveSubscriptions(list: List<Subscription>) {
        prefs.putString(KEY_SUBS, Subscriptions.toJsonArray(list))
        changed()
    }

    // ---------------------------------------------------------------- 저축 플래너 · 절약 챌린지 (27.35)
    /** 게임별 보유 재화 입력(gameKey → 재화량). 저축 플래너 필요분 차감용. */
    fun loadSavingsHeld(): Map<String, Int> {
        val raw = prefs.getString(KEY_SAVINGS_HELD, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> o.optInt(k, 0).takeIf { it > 0 }?.let { put(k, it) } } }
        }.getOrDefault(emptyMap())
    }

    fun saveSavingsHeld(map: Map<String, Int>) {
        val o = JSONObject()
        map.forEach { (k, v) -> if (v > 0) o.put(k, v) }
        prefs.putString(KEY_SAVINGS_HELD, o.toString())
        changed()
    }

    /** "안 뽑는" 픽업 목표 숨김 키 집합(SavingsPlan.key). 저축 플래너에서 미노출 처리용. */
    fun loadSavingsHidden(): Set<String> {
        val raw = prefs.getString(KEY_SAVINGS_HIDDEN, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveSavingsHidden(keys: Set<String>) {
        prefs.putString(KEY_SAVINGS_HIDDEN, JSONArray(keys.toList()).toString())
        changed()
    }

    /** 최고 무지출 스트릭(일) — 단조 증가 기록. */
    fun loadBestNoSpend(): Int = prefs.getInt(KEY_BEST_NOSPEND, 0)
    fun saveBestNoSpend(days: Int) { prefs.putInt(KEY_BEST_NOSPEND, days); changed() }

    /** 획득한 절약 배지 id 집합 — 단조 증가. */
    fun loadEarnedBadges(): Set<String> {
        val raw = prefs.getString(KEY_BADGES, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveEarnedBadges(ids: Set<String>) {
        prefs.putString(KEY_BADGES, JSONArray(ids.toList()).toString())
        changed()
    }

    // ---------------------------------------------------------------- 가챠 기록 (UIGF/SRGF)
    fun loadGachaRecords(): List<GachaRecord> = GachaReport.fromJsonArray(prefs.getString(KEY_GACHA, null))
    fun saveGachaRecords(records: List<GachaRecord>) {
        prefs.putString(KEY_GACHA, GachaReport.toJsonArray(records))
        changed() // 스냅샷(클라우드/파일 백업)에 포함되므로 변경 시 동기화 트리거
    }

    // ---------------------------------------------------------------- 픽업 배너 캐시 (로컬 전용 — 백그라운드 마감 알림 점검용)
    /** 최근 로드한 활성 픽업 배너. 백그라운드(NotificationChecker)가 네트워크 없이 마감 임박을 판정하도록 캐시. */
    fun loadActiveBanners(): List<GachaBanner> {
        val raw = prefs.getString(KEY_BANNERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GachaBanner(
                    game = o.optString("game", ""),
                    name = o.optString("name", ""),
                    type = o.optString("type", "character"),
                    endMillis = o.optLong("endMillis", 0L),
                    startMillis = o.optLong("startMillis", 0L),
                    version = o.optString("version", ""),
                    // 옛 캐시엔 없다 → 빈 문자열(아이콘 없이 이름만 그린다).
                    iconUrl = o.optString("iconUrl", ""),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 배너 캐시 저장. 로컬 전용이라 클라우드 동기화([changed])를 트리거하지 않는다. */
    fun saveActiveBanners(list: List<GachaBanner>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("game", b.game); put("name", b.name); put("type", b.type)
                put("endMillis", b.endMillis); put("startMillis", b.startMillis); put("version", b.version)
                put("iconUrl", b.iconUrl)
            })
        }
        prefs.putString(KEY_BANNERS, arr.toString())
    }

    // ---------------------------------------------------------------- 전투 진행도 캐시 (로컬 전용 — 시즌 마감 알림 점검용)
    /** 최근 로드한 전투 콘텐츠 진행도. 백그라운드가 네트워크 없이 시즌 마감 임박을 판정하도록 캐시. */
    fun loadCombatModes(): List<CombatMode> {
        val raw = prefs.getString(KEY_COMBAT, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CombatMode(
                    game = o.optString("game", ""),
                    name = o.optString("name", ""),
                    stars = o.optInt("stars", 0),
                    maxStars = o.optInt("maxStars", 0),
                    detail = o.optString("detail", ""),
                    endMillis = o.optLong("endMillis", 0L),
                    hasData = o.optBoolean("hasData", true),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 전투 진행도 캐시 저장. 배너 캐시와 동일하게 로컬 전용(클라우드 동기화 미트리거). */
    fun saveCombatModes(list: List<CombatMode>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("game", c.game); put("name", c.name)
                put("stars", c.stars); put("maxStars", c.maxStars)
                put("detail", c.detail); put("endMillis", c.endMillis); put("hasData", c.hasData)
            })
        }
        prefs.putString(KEY_COMBAT, arr.toString())
    }

    // ------------------------------------------------- 엔드 콘텐츠 클리어 편성 캐시 (로컬 전용)
    /**
     * 층·간별로 어떤 캐릭터를 썼는지. **시즌 2개치를 3게임 × 3모드까지 받는 무거운 호출**이라
     * 화면에 들어갈 때마다 부를 수 없다 — 받은 걸 캐시에 두고 화면은 캐시부터 그린다.
     */
    fun loadCombatClears(): List<CombatClear> {
        val raw = prefs.getString(KEY_COMBAT_CLEAR, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CombatClear(
                    game = o.optString("game", ""),
                    mode = o.optString("mode", ""),
                    season = o.optString("season", ""),
                    current = o.optBoolean("current", true),
                    rooms = o.optJSONArray("rooms").let { rs ->
                        (0 until (rs?.length() ?: 0)).mapNotNull { ri ->
                            val r = rs?.optJSONObject(ri) ?: return@mapNotNull null
                            CombatRoom(
                                name = r.optString("name", ""),
                                stars = r.optInt("stars", 0),
                                maxStars = r.optInt("maxStars", 0),
                                detail = r.optString("detail", ""),
                                firstHalf = avatarsFrom(r.optJSONArray("first")),
                                secondHalf = avatarsFrom(r.optJSONArray("second")),
                            )
                        }
                    },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun avatarsFrom(arr: JSONArray?): List<CombatAvatar> =
        (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val a = arr?.optJSONObject(i) ?: return@mapNotNull null
            CombatAvatar(
                id = a.optInt("id", 0),
                name = a.optString("name", ""),
                iconUrl = a.optString("icon", ""),
                level = a.optInt("level", 0),
                rarity = a.optInt("rarity", 0),
            )
        }

    private fun avatarsTo(list: List<CombatAvatar>): JSONArray {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id); put("name", a.name); put("icon", a.iconUrl)
                put("level", a.level); put("rarity", a.rarity)
            })
        }
        return arr
    }

    /** 클리어 편성 캐시 저장. 전투 진행도 캐시와 동일하게 로컬 전용(클라우드 동기화 미트리거). */
    fun saveCombatClears(list: List<CombatClear>) {
        val arr = JSONArray()
        list.forEach { c ->
            val rooms = JSONArray()
            c.rooms.forEach { r ->
                rooms.put(JSONObject().apply {
                    put("name", r.name); put("stars", r.stars); put("maxStars", r.maxStars)
                    put("detail", r.detail)
                    put("first", avatarsTo(r.firstHalf)); put("second", avatarsTo(r.secondHalf))
                })
            }
            arr.put(JSONObject().apply {
                put("game", c.game); put("mode", c.mode); put("season", c.season)
                put("current", c.current); put("rooms", rooms)
            })
        }
        prefs.putString(KEY_COMBAT_CLEAR, arr.toString())
    }

    // ---------------------------------------------------------------- 실시간 노트 캐시 (로컬 전용 — 예약 알림 계산용)
    /** 최근 받아온 실시간 노트. '재화가 가득 차는 시각'을 앱 실행 없이 예약하는 데 쓴다. */
    fun loadLiveNotes(): List<LiveNote> {
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LiveNote(
                    game = o.optString("game", ""),
                    currentResin = o.optInt("cur", 0),
                    maxResin = o.optInt("max", 0),
                    resinFullAtMillis = o.optLong("fullAt", 0L),
                    dailyTaskCount = o.optInt("daily", 0),
                    maxDailyTaskCount = o.optInt("dailyMax", 0),
                    weeklyDone = o.optInt("weekly", 0),
                    weeklyTotal = o.optInt("weeklyMax", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 실시간 노트 캐시 저장. 배너·전투 캐시와 동일하게 로컬 전용(클라우드 동기화 미트리거). */
    fun saveLiveNotes(list: List<LiveNote>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("game", n.game)
                put("cur", n.currentResin); put("max", n.maxResin)
                put("fullAt", n.resinFullAtMillis)
                put("daily", n.dailyTaskCount); put("dailyMax", n.maxDailyTaskCount)
                put("weekly", n.weeklyDone); put("weeklyMax", n.weeklyTotal)
            })
        }
        prefs.putString(KEY_NOTES, arr.toString())
    }

    // ---------------------------------------------------------------- 일정·공지 캐시 (로컬 전용 — 홈 즉시 표출용)
    /**
     * 홈의 '이번주 일정'·'게임 소식' 카드는 배너·노트와 달리 **디스크 캐시가 없어서**, 앱을 켤 때마다
     * ennead 응답이 올 때까지 빈 채로 있었다(배너·오늘 할 일은 캐시로 즉시 차는데 그 둘만 늦게 나타남).
     * 배너 캐시와 같은 stale-while-revalidate 로 맞춘다 — 지난 값을 먼저 그리고 응답이 오면 교체.
     */
    fun loadGameEvents(): List<GameEvent> = readList(KEY_EVENTS) { o ->
        GameEvent(
            game = o.optString("game", ""),
            name = o.optString("name", ""),
            endMillis = o.optLong("endMillis", 0L),
            reward = o.optString("reward", ""),
            // 옛 캐시엔 이 키가 없다 → 0(모름). 타임라인이 0 을 '시작 미상'으로 따로 그린다.
            startMillis = o.optLong("startMillis", 0L),
        )
    }

    fun saveGameEvents(list: List<GameEvent>) = writeList(KEY_EVENTS, list) { e ->
        JSONObject().apply {
            put("game", e.game); put("name", e.name)
            put("endMillis", e.endMillis); put("reward", e.reward)
            put("startMillis", e.startMillis)
        }
    }

    fun loadChallenges(): List<GameChallenge> = readList(KEY_CHALLENGES) { o ->
        GameChallenge(
            game = o.optString("game", ""),
            name = o.optString("name", ""),
            typeName = o.optString("typeName", ""),
            endMillis = o.optLong("endMillis", 0L),
            reward = o.optString("reward", ""),
            startMillis = o.optLong("startMillis", 0L),
        )
    }

    fun saveChallenges(list: List<GameChallenge>) = writeList(KEY_CHALLENGES, list) { c ->
        JSONObject().apply {
            put("game", c.game); put("name", c.name); put("typeName", c.typeName)
            put("endMillis", c.endMillis); put("reward", c.reward)
            put("startMillis", c.startMillis)
        }
    }

    fun loadGameNews(): List<NewsItem> = readList(KEY_NEWS) { o ->
        NewsItem(
            game = o.optString("game", ""),
            id = o.optString("id", ""),
            title = o.optString("title", ""),
            createdAtMillis = o.optLong("createdAt", 0L),
            bannerUrl = o.optString("banner", ""),
            url = o.optString("url", ""),
            summary = o.optString("summary", ""),
            // 출처를 잃으면 캐시에서 연 공지가 본문을 못 찾는다(호요 경로로 잘못 간다).
            // 구버전 캐시엔 이 키가 없으므로 ENNEAD 로 떨어뜨린다 — 그때는 호요 3게임뿐이었다.
            source = runCatching { NewsSource.valueOf(o.optString("source", "")) }.getOrDefault(NewsSource.ENNEAD),
            bodyRef = o.optString("bodyRef", ""),
        )
    }

    /**
     * 공지 캐시 저장 — **최신 [NEWS_CACHE_MAX] 건만, [summary] 는 [NEWS_SUMMARY_MAX] 자로 잘라서.**
     * 목록 API 가 주는 `description` 은 본문 전문(공지 하나가 수만 자)이라 그대로 담으면 prefs 에
     * 메가바이트가 들어간다. 캐시의 목적은 홈 카드·목록을 먼저 그리는 것뿐이고, 상세 본문은
     * 어차피 [com.gatcha.log.data.api.NewsApi.article] 로 따로 받는다.
     */
    fun saveGameNews(list: List<NewsItem>) =
        writeList(KEY_NEWS, list.sortedByDescending { it.createdAtMillis }.take(NEWS_CACHE_MAX)) { n ->
            JSONObject().apply {
                put("game", n.game); put("id", n.id); put("title", n.title)
                put("createdAt", n.createdAtMillis); put("banner", n.bannerUrl); put("url", n.url)
                put("summary", n.summary.take(NEWS_SUMMARY_MAX))
                put("source", n.source.name); put("bodyRef", n.bodyRef)
            }
        }

    /** 로컬 전용 리스트 캐시 읽기 — 형식이 깨졌으면 조용히 빈 목록(캐시는 없어도 그만이다). */
    private fun <T> readList(key: String, item: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> item(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    /** 로컬 전용 리스트 캐시 쓰기 — 클라우드 동기화([changed])는 트리거하지 않는다. */
    private fun <T> writeList(key: String, list: List<T>, item: (T) -> JSONObject) {
        val arr = JSONArray()
        list.forEach { arr.put(item(it)) }
        prefs.putString(key, arr.toString())
    }

    // ---------------------------------------------------------------- 유효옵션 사용자 설정
    /**
     * 캐릭터별 유효옵션 직접 설정. 키=[keyStatOverrideKey]("genshin:10000030"), 값=StatTok 이름 집합.
     * 앱 룰은 추정일 뿐이라(특히 원신 기본값·미매핑 캐릭) 사용자가 덮어쓸 수 있어야 한다.
     */
    fun loadKeyStatOverrides(): Map<String, Set<String>> {
        val raw = prefs.getString(KEY_KEYSTAT_OVERRIDE, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { k ->
                    val arr = root.optJSONArray(k) ?: return@forEach
                    put(k, (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet())
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveKeyStatOverrides(map: Map<String, Set<String>>) {
        val root = JSONObject()
        map.forEach { (k, v) ->
            if (v.isEmpty()) return@forEach   // 빈 집합 = 설정 해제 → 저장하지 않는다(룰로 되돌아감)
            root.put(k, JSONArray().apply { v.forEach { put(it) } })
        }
        prefs.putString(KEY_KEYSTAT_OVERRIDE, root.toString())
    }

    // ---------------------------------------------------------------- 숙제 관측 기록 (로컬 전용 — 완주율 계산)
    /**
     * 게임별 일일·주간 숙제 완료 기록. HoYoLAB 은 '지금 상태'만 주므로 완주율을 내려면
     * 앱이 노트를 받을 때마다 그날 결과를 여기 적어야 한다.
     * **기기에서 관측한 기록**이라 클라우드 스냅샷에는 넣지 않는다(기기마다 켠 시점이 달라 병합이 무의미).
     */
    fun loadTaskLogs(): Map<String, GameTaskLog> {
        val raw = prefs.getString(KEY_TASK_LOG, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { gameKey ->
                    val o = root.optJSONObject(gameKey) ?: return@forEach
                    put(gameKey, GameTaskLog(daily = boolMap(o.optJSONObject("d")), weekly = boolMap(o.optJSONObject("w"))))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveTaskLogs(logs: Map<String, GameTaskLog>) {
        val root = JSONObject()
        logs.forEach { (gameKey, log) ->
            root.put(gameKey, JSONObject().apply {
                put("d", JSONObject().apply { log.daily.forEach { (k, v) -> put(k, v) } })
                put("w", JSONObject().apply { log.weekly.forEach { (k, v) -> put(k, v) } })
            })
        }
        prefs.putString(KEY_TASK_LOG, root.toString())
    }

    private fun boolMap(o: JSONObject?): Map<String, Boolean> {
        o ?: return emptyMap()
        return buildMap { o.keys().forEach { k -> put(k, o.optBoolean(k, false)) } }
    }

    // ---------------------------------------------------------------- 스냅샷 (전체 데이터 직렬화 — 클라우드/파일 백업 공용)
    /**
     * 계정의 모든 데이터를 단일 JSON 객체로 모은다.
     *
     * 문자열이 아니라 [JSONObject] 를 돌려주는 형태를 따로 둔 이유 — 클라우드 push 는 같은 스냅샷을
     * 전체 문서용(문자열)과 섹션별 분해용(객체) 두 가지로 쓴다. 예전엔 [exportCloudSections] 가
     * `JSONObject(exportSnapshotJson())` 로 시작해서, **스냅샷을 통째로 한 번 더 만들고 그 결과를 다시
     * 전량 파싱**했다. 여기서 한 번만 만들어 양쪽에 넘긴다.
     */
    fun exportSnapshot(): JSONObject {
        val o = JSONObject()
        prefs.getString(KEY_SPENDINGS, null)?.let { o.put(KEY_SPENDINGS, JSONArray(it)) }
        prefs.getString(KEY_DELETED_SPENDINGS, null)?.let { o.put(KEY_DELETED_SPENDINGS, JSONArray(it)) }
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
        prefs.getString(KEY_PITY, null)?.let { o.put(KEY_PITY, JSONObject(it)) }
        prefs.getString(KEY_EVENT_CHECKS, null)?.let { o.put(KEY_EVENT_CHECKS, JSONArray(it)) }
        prefs.getString(KEY_SUBS, null)?.let { o.put(KEY_SUBS, JSONArray(it)) }
        prefs.getString(KEY_GACHA, null)?.let { o.put(KEY_GACHA, JSONArray(it)) }
        prefs.getString(KEY_HOME_CARDS, null)?.let { o.put(KEY_HOME_CARDS, JSONArray(it)) }
        prefs.getString(KEY_REDEEMED, null)?.let { o.put(KEY_REDEEMED, JSONArray(it)) }
        prefs.getString(KEY_SAVINGS_HELD, null)?.let { o.put(KEY_SAVINGS_HELD, JSONObject(it)) }
        prefs.getString(KEY_SAVINGS_HIDDEN, null)?.let { o.put(KEY_SAVINGS_HIDDEN, JSONArray(it)) }
        o.put(KEY_BEST_NOSPEND, loadBestNoSpend())
        prefs.getString(KEY_BADGES, null)?.let { o.put(KEY_BADGES, JSONArray(it)) }
        return o
    }

    /** 계정의 모든 데이터를 단일 JSON 문자열로 직렬화(Firestore 저장·파일 백업용). */
    fun exportSnapshotJson(): String = exportSnapshot().toString()

    /** Firestore/백업 파일에서 받은 스냅샷 JSON 을 로컬에 반영. (onChange 미발생 → 푸시 루프 방지) */
    fun importSnapshotJson(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        // 지출: id 기준 합집합 병합 + 삭제 tombstone 적용 — 구/스테일 스냅샷이 최신 로컬 지출을 덮어 삭제하지
        // 못하게 한다. 실제 삭제는 tombstone(deleted_spendings)으로만 전파. (이번 유실 사고 재발 방지)
        run {
            val incomingTomb: Set<String> = if (o.has(KEY_DELETED_SPENDINGS)) {
                val t = o.getJSONArray(KEY_DELETED_SPENDINGS)
                (0 until t.length()).map { t.getString(it) }.toSet()
            } else emptySet()
            if (incomingTomb.isNotEmpty()) addDeletedSpendingIds(incomingTomb) // tombstone 합집합 영속(삭제 전파)
            val tomb = loadDeletedSpendingIds()
            if (o.has(KEY_SPENDINGS) || tomb.isNotEmpty()) {
                val byId = LinkedHashMap<String, JSONObject>()
                prefs.getString(KEY_SPENDINGS, null)?.let { localRaw ->
                    val local = JSONArray(localRaw)
                    for (i in 0 until local.length()) { val obj = local.getJSONObject(i); byId[obj.getString("id")] = obj }
                }
                if (o.has(KEY_SPENDINGS)) {
                    val incoming = o.getJSONArray(KEY_SPENDINGS)
                    for (i in 0 until incoming.length()) { val obj = incoming.getJSONObject(i); byId[obj.getString("id")] = obj } // 같은 id 는 원격 우선
                }
                val result = JSONArray()
                byId.forEach { (id, obj) -> if (id !in tomb) result.put(obj) }
                prefs.putString(KEY_SPENDINGS, result.toString())
            }
        }
        if (o.has(KEY_BUDGET)) prefs.putLong(KEY_BUDGET, o.getLong(KEY_BUDGET))
        if (o.has(KEY_BUDGET_GAMES)) prefs.putString(KEY_BUDGET_GAMES, o.getJSONObject(KEY_BUDGET_GAMES).toString())
        if (o.has(KEY_PROFILE_NAME)) prefs.putString(KEY_PROFILE_NAME, o.getString(KEY_PROFILE_NAME))
        if (o.has(KEY_PROFILE_EMAIL)) prefs.putString(KEY_PROFILE_EMAIL, o.getString(KEY_PROFILE_EMAIL))
        // 토큰 키는 스냅샷에서 의도적으로 제외 — 구버전 클라우드/백업에 토큰이 남아 있어도 가져오지 않는다.
        if (o.has(KEY_HOYO_GI)) prefs.putString(KEY_HOYO_GI, o.getString(KEY_HOYO_GI))
        if (o.has(KEY_HOYO_HSR)) prefs.putString(KEY_HOYO_HSR, o.getString(KEY_HOYO_HSR))
        if (o.has(KEY_HOYO_ZZZ)) prefs.putString(KEY_HOYO_ZZZ, o.getString(KEY_HOYO_ZZZ))
        hoyolabCache = null   // 스냅샷이 UID 를 덮어썼을 수 있다 — 다음 읽기에서 다시 만든다
        if (o.has(KEY_ACCENT)) prefs.putInt(KEY_ACCENT, o.getInt(KEY_ACCENT))
        if (o.has(KEY_ENKA_GI)) prefs.putString(KEY_ENKA_GI, o.getString(KEY_ENKA_GI))
        if (o.has(KEY_ENKA_HSR)) prefs.putString(KEY_ENKA_HSR, o.getString(KEY_ENKA_HSR))
        if (o.has(KEY_ATTENDANCE)) prefs.putString(KEY_ATTENDANCE, o.getJSONObject(KEY_ATTENDANCE).toString())
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
        if (o.has(KEY_SAVINGS_HELD)) prefs.putString(KEY_SAVINGS_HELD, o.getJSONObject(KEY_SAVINGS_HELD).toString())
        if (o.has(KEY_SAVINGS_HIDDEN)) prefs.putString(KEY_SAVINGS_HIDDEN, o.getJSONArray(KEY_SAVINGS_HIDDEN).toString())
        // 최고 스트릭·배지는 **단조 증가**로 병합(스냅샷이 로컬 기록을 되돌리지 않도록).
        if (o.has(KEY_BEST_NOSPEND)) prefs.putInt(KEY_BEST_NOSPEND, maxOf(loadBestNoSpend(), o.getInt(KEY_BEST_NOSPEND)))
        if (o.has(KEY_BADGES)) {
            val arr = o.getJSONArray(KEY_BADGES)
            val incoming = (0 until arr.length()).map { arr.getString(it) }
            prefs.putString(KEY_BADGES, JSONArray((loadEarnedBadges() + incoming).toList()).toString())
        }
    }

    /**
     * 클라우드용 섹션 분리 — 전체 스냅샷을 유저정보/지출/게임정보 3맵으로 나눈다(각 키→값 JSON 문자열).
     * Firestore `users/{uid}` 의 userInfo/spending/gameInfo 필드로 저장돼 콘솔 가독성↑.
     * (읽기는 기존 `data` 전체 스냅샷 사용 — dual-write 호환)
     */
    fun exportCloudSections(snapshot: JSONObject = exportSnapshot()): CloudSections {
        val o = snapshot
        fun valueString(k: String): String = when (k) {
            KEY_BUDGET -> o.getLong(k).toString()
            KEY_ACCENT, KEY_BEST_NOSPEND -> o.getInt(k).toString()
            in OBJECT_KEYS -> o.getJSONObject(k).toString()
            in ARRAY_KEYS -> o.getJSONArray(k).toString()
            else -> o.getString(k)
        }
        fun section(keys: List<String>): Map<String, String> =
            buildMap { keys.forEach { k -> if (o.has(k)) put(k, valueString(k)) } }
        return CloudSections(section(SECTION_USER_INFO), section(SECTION_SPENDING), section(SECTION_GAME_INFO))
    }

    private companion object {
        const val KEY_PITY = "pity"
        const val KEY_EVENT_CHECKS = "event_checks"
        const val KEY_REDEEMED = "redeemed_codes"
        const val KEY_READ_ALERTS = "read_alerts"
        const val KEY_DISMISSED_ALERTS = "dismissed_alerts"
        const val KEY_SPENDINGS = "spendings"
        const val KEY_DELETED_SPENDINGS = "deleted_spendings" // 삭제된 지출 id tombstone(합집합 병합 방어 — 삭제 전파용)
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
        const val KEY_ENKA_CACHE = "enka_cache"   // 로컬 전용(클라우드 스냅샷 비포함)
        const val KEY_BANNERS = "active_banners"  // 로컬 전용(픽업 마감 알림 점검 캐시)
        const val KEY_COMBAT = "combat_modes"     // 로컬 전용(전투 시즌 마감 알림 점검 캐시)
        const val KEY_COMBAT_CLEAR = "combat_clears" // 로컬 전용(엔드 콘텐츠 클리어 편성 캐시)
        const val KEY_NOTES = "live_notes"        // 로컬 전용(재화 가득참 예약 알림 계산 캐시)
        const val KEY_EVENTS = "game_events"      // 로컬 전용(홈 '이번주 일정' 즉시 표출 캐시)
        const val KEY_CHALLENGES = "game_challenges" // 로컬 전용(홈 '이번주 일정' 즉시 표출 캐시)
        const val KEY_NEWS = "game_news"          // 로컬 전용(홈 '게임 소식' 즉시 표출 캐시)

        /** 공지 캐시 상한 — 홈 카드 3건 + 게임 정보 목록 첫 화면을 덮으면 충분하다. */
        private const val NEWS_CACHE_MAX = 30
        /** 공지 요약 캐시 길이 — 목록 카드에 보이는 만큼만(본문 전문은 상세에서 따로 받는다). */
        private const val NEWS_SUMMARY_MAX = 200
        const val KEY_TASK_LOG = "task_logs"      // 로컬 전용(일일·주간 숙제 완주율 관측 기록)
        const val KEY_KEYSTAT_OVERRIDE = "keystat_override"  // 캐릭터별 유효옵션 직접 설정
        const val KEY_GACHA = "gacha_records"
        const val KEY_SUBS = "subscriptions"
        const val KEY_HOME_CARDS = "home_cards"
        const val KEY_SAVINGS_HELD = "savings_held"   // 저축 플래너 보유 재화(gameKey→Int)
        const val KEY_SAVINGS_HIDDEN = "savings_hidden" // 저축 플래너 숨긴 목표 키 집합(SavingsPlan.key)
        const val KEY_BEST_NOSPEND = "best_nospend"    // 최고 무지출 스트릭(일)
        const val KEY_BADGES = "badges"                // 획득 절약 배지 id 집합

        // 클라우드 섹션 분리 — 스냅샷 키를 유저정보/지출/게임정보로 분배(토큰·read_alerts 는 스냅샷 비포함).
        val SECTION_USER_INFO = listOf(KEY_PROFILE_NAME, KEY_PROFILE_EMAIL, KEY_ACCENT, KEY_HOME_CARDS)
        val SECTION_SPENDING = listOf(KEY_SPENDINGS, KEY_DELETED_SPENDINGS, KEY_BUDGET, KEY_BUDGET_GAMES, KEY_SUBS, KEY_BEST_NOSPEND, KEY_BADGES)
        val SECTION_GAME_INFO = listOf(
            KEY_HOYO_GI, KEY_HOYO_HSR, KEY_HOYO_ZZZ, KEY_ENKA_GI, KEY_ENKA_HSR,
            KEY_ATTENDANCE, KEY_PITY, KEY_EVENT_CHECKS, KEY_GACHA, KEY_REDEEMED, KEY_SAVINGS_HELD, KEY_SAVINGS_HIDDEN,
        )
        // 값 타입 분류(섹션 맵의 문자열 변환용) — 나머지 키는 문자열.
        private val OBJECT_KEYS = setOf(KEY_BUDGET_GAMES, KEY_ATTENDANCE, KEY_PITY, KEY_SAVINGS_HELD)
        private val ARRAY_KEYS = setOf(KEY_SPENDINGS, KEY_DELETED_SPENDINGS, KEY_EVENT_CHECKS, KEY_SUBS, KEY_GACHA, KEY_HOME_CARDS, KEY_REDEEMED, KEY_BADGES, KEY_SAVINGS_HIDDEN)
    }
}

/** 클라우드 섹션 분리 결과 — Firestore `users/{uid}` 의 userInfo/spending/gameInfo 맵(키→JSON문자열 값). */
data class CloudSections(
    val userInfo: Map<String, String>,
    val spending: Map<String, String>,
    val gameInfo: Map<String, String>,
)
