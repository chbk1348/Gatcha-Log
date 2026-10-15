package com.gatcha.log.data.api

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// [EnkaApi.cleanName] 이 쓰는 정규식 — 파일 레벨에서 한 번만 컴파일한다.
// 예전엔 함수 안에 있어서, 메타 맵을 만들 때 **항목 수만큼**(수백~수천) 두 개를 다시 컴파일했다.

/**
 * Enka.Network 프로필 쇼케이스 직접 조회(서버 프록시 없이).
 * 캐릭터 id→한글명/희귀도는 Yatta(ambr) 아바타 목록으로 매핑(메모리 캐시).
 * Enka 는 User-Agent 헤더가 없으면 403/429 가 날 수 있어 반드시 붙인다.
 */
object EnkaApi {

    private const val UA = "Gatcha-LOG-Android/1.0"
    private val headers = mapOf("User-Agent" to UA, "Accept" to "application/json")

    // id -> 아바타 메타. 최초 1회 로드 후 캐시.
    private var giMeta: Map<Int, AvatarMeta>? = null
    private var hsrMeta: Map<Int, AvatarMeta>? = null

    // HSR 세트 메타(StarRailRes). HoYoLAB 로스터(쇼케이스 밖) 캐릭터 세트 효과 계산용. 최초 1회 로드 후 캐시.
    internal var hsrSetMetaCache: Map<String, Pair<String, List<String>>>? = null // set_id → (세트명, desc[2/4세트])
    internal var hsrRelicSetCache: Map<String, String>? = null // relic 조각 item id → set_id
    internal var hsrSetNameCache: Map<String, String>? = null  // 세트명·조각명 → set_id (폴백 매칭)

    /**
     * [ltuid]/[ltoken] 은 HSR 전용 — 본인 계정 연동 시 HoYoLAB 공식 KR 캐릭터명을 받아
     * mihomo 가 비워두는 신규 캐릭터 이름을 보완한다(§5). 미연동이면 빈 문자열 → mihomo 이름 폴백.
     */
    /**
     * @param onPartial 최종 결과보다 **먼저 만들 수 있는 목록**을 건넨다(없으면 안 부른다). 연동된 계정에서만 온다.
     *   스타레일 — 느린 mihomo 를 기다리지 않고 HoYoLAB 보유 목록으로. 원신 — 전원 상세를 기다리지 않고 보유 목록으로.
     *   젠레스 — 1명당 1요청인 상세를 기다리지 않고 보유 목록으로(스탯 · 장비는 최종 결과에서 채워진다).
     *   부르는 쪽은 보여 줄 것이 아직 없을 때만 넘긴다. 최종 결과는 예전처럼 반환값으로 온다.
     */
    suspend fun fetchProfile(
        game: String, uid: String, ltuid: String = "", ltoken: String = "",
        onPartial: ((EnkaResult) -> Unit)? = null,
    ): EnkaResult {
        val u = uid.trim()
        if (u.isBlank() || u.any { !it.isDigit() }) return EnkaResult(null, "UID는 숫자만 입력하세요")
        return when (game) {
            "hsr", "starrail" -> fetchHsr(u, ltuid, ltoken, onPartial)
            "zzz" -> fetchZzz(u, ltuid, ltoken, onPartial)
            else -> fetchGenshin(u, ltuid, ltoken, onPartial)
        }
    }

    // ----------------------------------------------------------------- 원신
    private suspend fun fetchGenshin(
        uid: String, ltuid: String = "", ltoken: String = "", onPartial: ((EnkaResult) -> Unit)? = null,
    ): EnkaResult {
        // Enka(프로필)와 HoYoLAB(보유 전체)은 **서로를 기다릴 이유가 없다** — 예전엔 Enka 응답을 다
        // 받은 뒤에야 HoYoLAB 요청이 나가서 왕복이 그대로 두 배였다. HoYoLAB 쪽은 내부에서 다시
        // list→detail 2연속이라 체인이 실제로는 3단이었다.
        val linked = ltuid.isNotBlank() && ltoken.isNotBlank()
        // 본인 계정 연동 시: HoYoLAB character/detail 로 보유 전체(쇼케이스 밖 포함). 미연동/실패 → Enka 쇼케이스.
        val (res, hoyoData) = coroutineScope {
            val enkaD = async { Net.get("https://enka.network/api/uid/$uid", headers) }
            // 보유 목록(1단계)이 오면 **상세를 기다리지 않고 먼저 그린다**(27.51.1) — 상세는 전원의 스탯 · 무기 · 성유물이라 무겁다.
            val hoyoD = if (linked) async {
                HoyolabApi.fetchGenshinCharDetail(ltuid, ltoken, uid) { list -> onPartial?.let { cb -> giListResult(list)?.let(cb) } }
            } else null
            enkaD.await() to hoyoD?.await()
        }
        // Enka 가 죽어도(점검 · 429) **HoYoLAB 보유 목록이 왔으면 그것으로 선다**(27.51.1) — 예전엔 Enka 실패 하나로
        // 이미 받은 전원 상세를 버리고 「조회 실패」를 띄웠다. Enka 에서만 오는 닉네임 · 모험 등급은 빈다.
        val enkaErr = errorFor(res.code)
        if (enkaErr != null && hoyoData == null) return EnkaResult(null, enkaErr)
        return runCatching {
            val json = if (enkaErr == null) JSONObject(res.body) else JSONObject("{}")
            val p = if (hoyoData != null) json.optJSONObject("playerInfo") ?: JSONObject("{}") else json.getJSONObject("playerInfo")
            val chars: List<EnkaChar> = if (hoyoData != null) {
                val propMap = hsrPropMap(hoyoData.optJSONObject("property_map")) // property_type → KR명
                val gl = hoyoData.optJSONArray("list") ?: JSONArray()
                (0 until gl.length()).mapNotNull { i -> gl.optJSONObject(i)?.let { giCharFromHoyo(it, propMap) } }
            } else {
                val show = p.optJSONArray("showAvatarInfoList") ?: JSONArray()
                // 상세 스탯(fightPropMap·equipList)은 "캐릭터 상세 공개" 시에만 avatarInfoList 에 존재.
                val detailed = json.optJSONArray("avatarInfoList")
                val meta = avatarMeta(false)
                if (detailed != null && detailed.length() > 0) {
                    val wnames = weaponMeta(false)
                    (0 until detailed.length()).mapNotNull { i ->
                        val a = detailed.optJSONObject(i) ?: return@mapNotNull null
                        val id = a.optInt("avatarId")
                        val m = meta[id]
                        val fp = a.optJSONObject("fightPropMap")
                        val equip = a.optJSONArray("equipList")
                        val lvl = a.optJSONObject("propMap")?.optJSONObject("4001")?.optString("val")?.toIntOrNull() ?: 0
                        EnkaChar(
                            id = id,
                            name = m?.name ?: "#$id",
                            level = lvl,
                            rank = a.optJSONArray("talentIdList")?.length() ?: 0,
                            rarity = m?.rarity ?: 5,
                            iconUrl = m?.iconUrl?.ifBlank { null },
                            element = m?.element ?: "",
                            detailed = true,
                            stats = fp?.let { giStats(it) } ?: emptyList(),
                            weapon = equip?.let { giWeapon(it, wnames) },
                            artifacts = equip?.let { giArtifacts(it) } ?: emptyList(),
                        )
                    }
                } else {
                    // 상세 비공개 → 로스터만(스탯 없음)
                    (0 until show.length()).map { i ->
                        val a = show.getJSONObject(i)
                        val id = a.optInt("avatarId")
                        val m = meta[id]
                        EnkaChar(
                            id = id, name = m?.name ?: "#$id", level = a.optInt("level"),
                            rank = -1, rarity = m?.rarity ?: 5,
                            iconUrl = m?.iconUrl?.ifBlank { null }, element = m?.element ?: "",
                        )
                    }
                }
            }
            EnkaResult(
                EnkaProfile(
                    nickname = p.optString("nickname"),
                    level = p.optInt("level"),
                    worldLevel = p.optInt("worldLevel"),
                    signature = p.optString("signature"),
                    chars = chars,
                ),
                null,
                // 연동된 계정인데 HoYoLAB 보유 목록이 못 왔다 — 쇼케이스 8~12명뿐이다.
                // 이름 메타를 못 받아 「#1234」 로 선 캐릭터가 있어도 덜 받은 것이다 — 5분을 채우지 않고 곧 다시 받는다.
                partial = (linked && hoyoData == null) || chars.any { it.name.startsWith("#") },
            )
        }.getOrElse { EnkaResult(null, "응답을 해석하지 못했어요") }
    }

    private suspend fun fetchHsr(
        uid: String, ltuid: String = "", ltoken: String = "", onPartial: ((EnkaResult) -> Unit)? = null,
    ): EnkaResult {
        // 셋 다 서로를 안 기다린다 — mihomo(쇼케이스+풀스탯) · 세트 메타(StarRailRes 2~3파일) ·
        // HoYoLAB 로스터. 예전엔 이 순서대로 **직렬**이라 왕복이 그대로 누적됐다(실측 메타만 0.5초).
        val linked = ltuid.isNotBlank() && ltoken.isNotBlank()
        val (res, hoyoData) = coroutineScope {
            val mihomoD = async { Net.get("https://api.mihomo.me/sr_info_parsed/$uid?lang=kr", headers) }
            val setD = async { ensureHsrSetData() }   // 로스터(쇼케이스 밖) 세트 효과 계산용 메타
            // 본인 계정 연동 시: HoYoLAB avatar/info 로 보유 전체 캐릭터(쇼케이스 밖 포함)
            val hoyoD = if (linked) async { HoyolabApi.fetchHsrAvatarInfo(ltuid, ltoken, uid) } else null
            // **mihomo 를 기다리지 않고 먼저 그린다**(27.51.1). mihomo 는 한 번에 1.4초, 느리면 3초가 걸리는데(실측 10/7)
            // HoYoLAB 의 보유 목록은 0.2초면 온다. 연동된 계정이면 그 목록만으로 먼저 만들어 건네고, mihomo 가 오면
            // 쇼케이스 캐릭터를 더 풍부한 값으로 갈아끼운 최종 결과를 돌려준다. mihomo 가 이미 와 있으면 건너뛴다.
            if (hoyoD != null && onPartial != null) {
                val hoyo = hoyoD.await()
                setD.await()                          // 세트 효과 계산이 메타를 읽는다
                if (!mihomoD.isCompleted) {
                    hsrResult(null, hoyo, linked).takeIf { it.profile != null }?.let(onPartial)
                }
            }
            setD.await()                              // 파싱이 메타를 읽으므로 여기서 완료를 보장한다
            mihomoD.await() to hoyoD?.await()
        }
        return hsrResult(res, hoyoData, linked)
    }

    /**
     * mihomo 응답([res]) + HoYoLAB 보유 목록([hoyoData]) → 결과. **[res] 가 null 이면 mihomo 없이** 만든다
     * (아직 안 왔거나 못 받았거나 — 연동된 계정이면 HoYoLAB 목록만으로 선다. 닉네임 · 레벨은 mihomo 에만 있어 빈다).
     */
    internal fun hsrResult(res: NetResult?, hoyoData: JSONObject?, linked: Boolean): EnkaResult {
        val mihomoErr = if (res == null) "아직 받지 못했어요" else errorFor(res.code)
        // mihomo(HSR 파싱 API)가 죽어도(예: 500 장애) 연동돼 있으면 HoYoLAB 로스터로 폴백 → '조회 실패' 대신 목록 유지.
        // 미연동이면 쇼케이스 소스가 mihomo 뿐이라 폴백 불가 → 원래 에러 노출.
        if (mihomoErr != null && !linked) return EnkaResult(null, mihomoErr)
        val hoyoList = hoyoData?.optJSONArray("avatar_list")
        // mihomo 실패 + HoYoLAB 목록도 비면 폴백 불가 → 원래 에러 노출.
        if (mihomoErr != null && (hoyoList == null || hoyoList.length() == 0)) return EnkaResult(null, mihomoErr)
        val propMap = hsrPropMap(hoyoData?.optJSONObject("property_info")) // property_type → KR 스탯명
        return runCatching {
            val json = if (mihomoErr == null && res != null) JSONObject(res.body) else null
            val player = json?.optJSONObject("player")
            val list = json?.optJSONArray("characters") ?: JSONArray()
            // mihomo 쇼케이스 캐릭터(풍부한 KR 파싱) — id 색인
            val showcase = linkedMapOf<Int, EnkaChar>()
            for (i in 0 until list.length()) {
                val a = list.optJSONObject(i) ?: continue
                val id = a.optString("id").toIntOrNull() ?: 0
                showcase[id] = EnkaChar(
                    id = id,
                    name = a.optString("name").ifBlank { "#$id" },
                    level = a.optInt("level"),
                    rank = a.optInt("rank"), // 성혼
                    rarity = a.optInt("rarity", 5),
                    iconUrl = mihomoIcon(a.optString("icon")),
                    element = a.optJSONObject("element")?.optString("name").orEmpty(),
                    path = a.optJSONObject("path")?.optString("name").orEmpty(),
                    detailed = true,
                    stats = hsrStats(a),
                    weapon = hsrLightCone(a.optJSONObject("light_cone")),
                    artifacts = hsrRelics(a.optJSONArray("relics")),
                    sets = hsrSets(a),
                )
            }
            // 로스터: 연동되면 HoYoLAB 전체 목록(쇼케이스=mihomo 리치+공식이름 override, 그 외=HoYoLAB 파싱), 아니면 쇼케이스만
            val chars: List<EnkaChar> = if (hoyoList != null && hoyoList.length() > 0) {
                (0 until hoyoList.length()).mapNotNull { i ->
                    val o = hoyoList.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optInt("id")
                    val officialName = o.optString("name")
                    val officialLc = o.optJSONObject("equip")?.optString("name").orEmpty()
                    showcase[id]?.copy(
                        name = officialName.ifBlank { showcase[id]!!.name },
                        weapon = showcase[id]!!.weapon?.let { w -> w.copy(name = officialLc.ifBlank { w.name }) },
                    ) ?: hsrCharFromHoyo(o, propMap)
                }
            } else {
                showcase.values.toList()
            }
            if (chars.isEmpty() && player == null) {
                return@runCatching EnkaResult(null, "프로필을 찾을 수 없어요 (UID·쇼케이스 공개 확인)")
            }
            EnkaResult(
                EnkaProfile(
                    nickname = player?.optString("nickname").orEmpty(),
                    level = player?.optInt("level") ?: 0,
                    worldLevel = 0,
                    signature = player?.optString("signature").orEmpty(),
                    chars = chars,
                ),
                null,
                // [res] 가 null 이면 먼저 그리는 중간 결과다 — 캐시에 닿지 않으므로 표시하지 않는다.
                partial = res != null && linked && (hoyoList == null || hoyoList.length() == 0),
            )
        }.getOrElse { EnkaResult(null, "응답을 해석하지 못했어요") }
    }

    /**
     * HSR 세트 메타 1회 로드(StarRailRes, KR). 쇼케이스 밖(HoYoLAB) 캐릭터 세트 효과 계산에 필요.
     * relic_sets: set_id → (세트명, desc[2/4세트]) · relics: 조각 item id → set_id. 실패해도 무시(세트 미표시).
     */
    private suspend fun ensureHsrSetData() {
        if (hsrSetMetaCache != null && hsrRelicSetCache != null) return
        runCatching {
            // 세트 메타와 조각 인덱스는 **서로 독립**이다(관계를 맺는 건 받은 뒤의 계산). 같이 받는다 —
            // relics.json 이 210KB 대로 제일 무거워서, 직렬로 두면 그만큼이 통째로 앞에 붙는다.
            val (krMeta, relicsBody) = coroutineScope {
                val metaD = async { loadHsrSetMeta("kr") }
                val relicsD = async { Net.get("https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/index_new/kr/relics.json", headers).body }
                metaD.await() to relicsD.await()
            }
            // 신규 세트(예: 4.4)는 KR 인덱스에 이름·효과가 아직 비어 오는 경우가 있다(Mar-7th 가 EN 을 먼저 채움).
            // 그런 세트만 EN 으로 메워 '세트 효과 없음'을 막는다 — KR 이 채워지면 자동으로 한글로 돌아온다.
            // (EN 폴백은 KR 을 받아 봐야 필요 여부를 알 수 있어 위 병렬에 못 넣는다. 평소엔 안 탄다.)
            val needsFallback = krMeta.any { (_, v) -> v.first.isBlank() || v.second.all { it.isBlank() } }
            val enMeta = if (needsFallback) runCatching { loadHsrSetMeta("en") }.getOrNull().orEmpty() else emptyMap()
            val meta = krMeta.mapValues { (id, kr) ->
                if (kr.first.isBlank() || kr.second.all { it.isBlank() }) enMeta[id] ?: kr else kr
            }
            val nameToSet = mutableMapOf<String, String>()
            meta.forEach { (id, v) -> if (v.first.isNotBlank()) nameToSet[v.first] = id } // 세트명 → set_id

            val relics = JSONObject(relicsBody)
            val r2s = buildMap {
                relics.keys().forEach { k ->
                    val o = relics.optJSONObject(k) ?: return@forEach
                    val sid = o.optString("set_id")
                    if (sid.isBlank()) return@forEach
                    put(k, sid) // 조각 item id → set_id
                    o.optString("name").takeIf { it.isNotBlank() }?.let { nameToSet[it] = sid } // 조각명 → set_id
                }
            }
            hsrSetMetaCache = meta
            hsrRelicSetCache = r2s
            hsrSetNameCache = nameToSet
        }
    }

    /** StarRailRes relic_sets.json(지정 언어) → set_id → (세트명, desc[2/4세트]). */
    private suspend fun loadHsrSetMeta(lang: String): Map<String, Pair<String, List<String>>> {
        val sets = JSONObject(Net.get("https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/index_new/$lang/relic_sets.json", headers).body)
        return buildMap {
            sets.keys().forEach { k ->
                val o = sets.optJSONObject(k) ?: return@forEach
                val da = o.optJSONArray("desc")
                val descs = if (da != null) (0 until da.length()).map { da.optString(it) } else emptyList()
                put(k, o.optString("name") to descs)
            }
        }
    }

    // ----------------------------------------------------------------- 메타 매핑 (Yatta: 한글명·희귀도·아이콘·원소)
    /**
     * 캐릭터 id → 한글명 **전체 목록**(yatta). 최초 1회만 받아 캐시한다([avatarMeta]).
     *
     * 전투 편성처럼 HoYoLAB 이 id·아이콘만 주는 응답의 이름을 채우는 데 쓴다.
     * 보유 캐릭터([EnkaChar])로는 못 메운다 — Enka 는 **쇼케이스에 올린 캐릭터만** 주므로
     * 나선 비경에 넣은 캐릭터 대부분이 빠진다(2026-08-05 원신만 이름이 안 나오던 원인).
     */
    suspend fun characterNames(hsr: Boolean): Map<Int, String> =
        runCatching { avatarMeta(hsr).mapValues { it.value.name } }.getOrDefault(emptyMap())

    private suspend fun avatarMeta(hsr: Boolean): Map<Int, AvatarMeta> {
        (if (hsr) hsrMeta else giMeta)?.let { return it }
        val url = if (hsr) "https://sr.yatta.moe/api/v2/kr/avatar" else "https://gi.yatta.moe/api/v2/kr/avatar"
        val res = Net.get(url, headers)
        val map = runCatching {
            val items = JSONObject(res.body).getJSONObject("data").getJSONObject("items")
            buildMap<Int, AvatarMeta> {
                items.keys().forEach { k ->
                    val o = items.getJSONObject(k)
                    val id = k.toIntOrNull() ?: o.optInt("id")
                    val iconRaw = o.optString("icon", "")
                    // 원신: gi.yatta UI 카드 아이콘 / 스타레일: sr.yatta 아바타 아이콘
                    val iconUrl = when {
                        iconRaw.isBlank() -> ""
                        hsr -> "https://sr.yatta.moe/hsr/assets/UI/avatar/$iconRaw.png"
                        else -> "https://gi.yatta.moe/assets/UI/$iconRaw.png"
                    }
                    val element = if (hsr) hsrElementKo(o.optJSONObject("types")?.optString("combatType").orEmpty())
                    else giElementKo(o.optString("element", ""))
                    // Yatta 일부 이름에 <unbreak>…</unbreak> 등 마크업이 섞여 들어옴(예: 은랑) → 제거
                    val name = cleanName(o.optString("name", "")).ifBlank { "#$id" }
                    put(id, AvatarMeta(name, o.optInt("rank", 5), iconUrl, element))
                }
            }
        }.getOrDefault(emptyMap())
        if (map.isNotEmpty()) { if (hsr) hsrMeta = map else giMeta = map }
        return map
    }

    /** Yatta 이름의 마크업 태그(<unbreak> 등) 제거 + 공백 정리 */
    internal fun cleanName(raw: String): String =
        raw.replace(RE_MARKUP_TAG, "").replace(RE_WHITESPACE, " ").trim()

    // ----------------------------------------------------------------- 풀 스탯 파싱 (GI)
    private var giWeaponMeta: Map<Int, String>? = null
    private var hsrWeaponMeta: Map<Int, String>? = null

    /** Yatta 무기/광추 id→한글명 (메모리 캐시). */
    private suspend fun weaponMeta(hsr: Boolean): Map<Int, String> {
        (if (hsr) hsrWeaponMeta else giWeaponMeta)?.let { return it }
        val url = if (hsr) "https://sr.yatta.moe/api/v2/kr/equipment" else "https://gi.yatta.moe/api/v2/kr/weapon"
        val res = Net.get(url, headers)
        val map = runCatching {
            val items = JSONObject(res.body).getJSONObject("data").getJSONObject("items")
            buildMap<Int, String> {
                val it = items.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val id = k.toIntOrNull() ?: continue
                    put(id, cleanName(items.getJSONObject(k).optString("name", "")))
                }
            }
        }.getOrDefault(emptyMap())
        if (map.isNotEmpty()) { if (hsr) hsrWeaponMeta = map else giWeaponMeta = map }
        return map
    }

    /**
     * 원신 보유 **목록**(character/list)만으로 만든 결과 — 상세가 오기 전에 먼저 그리는 용도.
     * 이름 · 레벨 · 명좌 · 속성 · 아이콘은 목록에도 있다. 스탯 · 성유물은 없어 `detailed = false` 다(최종 결과가 채운다).
     */
    internal fun giListResult(list: JSONArray): EnkaResult? {
        val chars = (0 until list.length()).mapNotNull { i ->
            list.optJSONObject(i)?.takeIf { it.optInt("id") != 0 }?.let { giCharFromHoyo(it, emptyMap()).copy(detailed = false) }
        }
        return if (chars.isEmpty()) null else EnkaResult(EnkaProfile("", 0, 0, "", chars), null)
    }

    // ---------------- 젠레스(ZZZ) avatar/info 파싱 — 응답 라벨(property_name) 사용, property_map 없음 ----------------
    private suspend fun fetchZzz(
        uid: String, ltuid: String, ltoken: String, onPartial: ((EnkaResult) -> Unit)? = null,
    ): EnkaResult {
        // 보유 목록이 오면 **에이전트별 상세를 기다리지 않고 먼저 그린다**(27.51.1) — 상세는 1명당 1요청이라
        // 50명이면 50건(동시 4건)이 다 끝나야 목록이 섰다. 캐릭터 목록이 느리던 가장 큰 원인이다.
        val list = HoyolabApi.fetchZzzAvatars(ltuid, ltoken, uid) { basic -> onPartial?.let { cb -> zzzListResult(basic)?.let(cb) } }
            ?: return EnkaResult(null, HoyolabApi.zzzLastError ?: "젠레스 정보를 불러오지 못했어요")
        // 상세를 못 받은 에이전트는 보유 목록의 기본 정보로 남아 있다 — 스탯 · 장비가 없다고 표시해 둔다.
        val missing = HoyolabApi.zzzLastMissing
        val chars = list.map { o -> zzzChar(o).let { c -> if (c.id in missing) c.copy(detailed = false) else c } }
        if (chars.isEmpty()) return EnkaResult(null, "표시할 에이전트가 없어요")
        return EnkaResult(EnkaProfile("", 0, 0, "", chars), null, partial = missing.isNotEmpty())
    }

    /** 젠레스 보유 **목록**(avatar/basic)만으로 만든 결과 — 상세가 오기 전에 먼저 그리는 용도. 스탯 · 장비는 최종 결과가 채운다. */
    internal fun zzzListResult(basic: List<JSONObject>): EnkaResult? {
        val chars = basic.map { zzzChar(it).copy(detailed = false) }
        return if (chars.isEmpty()) null else EnkaResult(EnkaProfile("", 0, 0, "", chars), null)
    }

    private fun errorFor(code: Int): String? = when (code) {
        in 200..299 -> null
        -1 -> "네트워크 오류"
        400 -> "UID 형식이 올바르지 않아요"
        404 -> "프로필을 찾을 수 없어요 (UID·쇼케이스 공개 확인)"
        424 -> "게임 점검 중이에요"
        429 -> "요청이 많아요. 잠시 후 다시 시도해주세요"
        else -> "조회 실패 ($code)"
    }
}

