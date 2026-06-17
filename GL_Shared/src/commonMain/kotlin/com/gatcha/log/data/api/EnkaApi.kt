package com.gatcha.log.data.api

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

/** 쇼케이스 캐릭터 (id, 한글명, 레벨, 명좌/성혼 rank, 희귀도, 아이콘 URL, 한글 원소) */
data class EnkaChar(
    val id: Int,
    val name: String,
    val level: Int,
    val rank: Int,
    val rarity: Int,
    val iconUrl: String? = null,
    val element: String = "",
    /** 스타레일 운명의 길(파멸·수렵 등). 다른 게임은 빈 문자열. */
    val path: String = "",
    /** 캐릭터 상세 공개 시에만 채워짐(풀 스탯시트). 비공개면 false → 로스터만 표시. */
    val detailed: Boolean = false,
    val stats: List<EnkaStatLine> = emptyList(),
    val weapon: EnkaWeapon? = null,
    val artifacts: List<EnkaArtifact> = emptyList(),
    val sets: List<EnkaSet> = emptyList(),
)

/** 스탯 한 줄(라벨+표시값). [crit]=치명타 계열(UI 강조). */
data class EnkaStatLine(val label: String, val value: String, val crit: Boolean = false)

/** 무기/광추. */
data class EnkaWeapon(
    val name: String,
    val level: Int,
    val refinement: Int,
    val main: EnkaStatLine?,
    val sub: EnkaStatLine?,
)

/** 성유물/유물 슬롯 1개. setName 은 v1 미해결 시 빈 문자열. */
data class EnkaArtifact(
    val slot: String,
    val setName: String,
    val level: Int,
    val main: EnkaStatLine,
    val subs: List<EnkaStatLine>,
)

/** 세트 효과(성유물/유물/드라이브 디스크). [count]=장착 수, [effects]=활성 세트 보너스 텍스트. */
data class EnkaSet(val name: String, val count: Int, val effects: List<String> = emptyList())

/** Yatta 아바타 메타(한글명·희귀도·아이콘 URL·한글 원소). id 매핑용 캐시 값. */
private data class AvatarMeta(val name: String, val rarity: Int, val iconUrl: String, val element: String)

/** Enka 프로필 (닉네임/모험/세계 레벨/서명 + 쇼케이스 캐릭터) */
data class EnkaProfile(
    val nickname: String,
    val level: Int,
    val worldLevel: Int,
    val signature: String,
    val chars: List<EnkaChar>,
)

data class EnkaResult(val profile: EnkaProfile?, val error: String?)

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

    /**
     * [ltuid]/[ltoken] 은 HSR 전용 — 본인 계정 연동 시 HoYoLAB 공식 KR 캐릭터명을 받아
     * mihomo 가 비워두는 신규 캐릭터 이름을 보완한다(§5). 미연동이면 빈 문자열 → mihomo 이름 폴백.
     */
    suspend fun fetchProfile(game: String, uid: String, ltuid: String = "", ltoken: String = ""): EnkaResult {
        val u = uid.trim()
        if (u.isBlank() || u.any { !it.isDigit() }) return EnkaResult(null, "UID는 숫자만 입력하세요")
        return when (game) {
            "hsr", "starrail" -> fetchHsr(u, ltuid, ltoken)
            "zzz" -> fetchZzz(u, ltuid, ltoken)
            else -> fetchGenshin(u, ltuid, ltoken)
        }
    }

    // ----------------------------------------------------------------- 원신
    private suspend fun fetchGenshin(uid: String, ltuid: String = "", ltoken: String = ""): EnkaResult {
        val res = Net.get("https://enka.network/api/uid/$uid", headers)
        errorFor(res.code)?.let { return EnkaResult(null, it) }
        // 본인 계정 연동 시: HoYoLAB character/detail 로 보유 전체(쇼케이스 밖 포함). 미연동/실패 → Enka 쇼케이스.
        val hoyoData = if (ltuid.isNotBlank() && ltoken.isNotBlank()) HoyolabApi.fetchGenshinCharDetail(ltuid, ltoken, uid) else null
        return runCatching {
            val json = JSONObject(res.body)
            val p = json.getJSONObject("playerInfo")
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
            )
        }.getOrElse { EnkaResult(null, "응답을 해석하지 못했어요") }
    }

    // ----------------------------------------------------------------- 스타레일 (Mihomo parsed)
    // Enka HSR 은 원시 데이터만 줘 최종 스탯 계산이 불가 → Mihomo parsed API 사용
    // (KR 표시문자열·세트명까지 계산해서 반환). 로스터+풀스탯 동일 응답에서 파싱.
    private val hsrSlots = listOf("머리", "손", "몸통", "발", "행성구", "연결로프")

    private suspend fun fetchHsr(uid: String, ltuid: String = "", ltoken: String = ""): EnkaResult {
        val res = Net.get("https://api.mihomo.me/sr_info_parsed/$uid?lang=kr", headers)
        errorFor(res.code)?.let { return EnkaResult(null, it) }
        // 본인 계정 연동 시: HoYoLAB avatar/info 로 보유 전체 캐릭터(쇼케이스 밖 포함)
        val hoyoData = HoyolabApi.fetchHsrAvatarInfo(ltuid, ltoken, uid)
        val hoyoList = hoyoData?.optJSONArray("avatar_list")
        val propMap = hsrPropMap(hoyoData?.optJSONObject("property_info")) // property_type → KR 스탯명
        return runCatching {
            val json = JSONObject(res.body)
            val player = json.optJSONObject("player")
            val list = json.optJSONArray("characters") ?: JSONArray()
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
            )
        }.getOrElse { EnkaResult(null, "응답을 해석하지 못했어요") }
    }

    // ---------------- HoYoLAB avatar/info(보유 전체) 파싱 — property_info(type→KR명) + 응답 value 사용 ----------------
    private fun hoyoIcon(p: String): String? = p.takeIf { it.startsWith("http") }

    /** property_info {"53":{name:"치명타 피해",...}} → property_type → KR 스탯명. */
    private fun hsrPropMap(info: JSONObject?): Map<Int, String> {
        info ?: return emptyMap()
        return buildMap {
            val it = info.keys()
            while (it.hasNext()) {
                val k = it.next()
                val type = k.toIntOrNull() ?: continue
                val name = info.optJSONObject(k)?.optString("name").orEmpty()
                if (name.isNotBlank()) put(type, name)
            }
        }
    }

    /** 정수 문자열이면 천 단위 콤마, %·소수 등은 그대로. */
    private fun hsrFmt(v: String): String =
        if (v.isNotEmpty() && v.all { it.isDigit() }) comma(v.toInt()) else v

    private fun isZeroValue(v: String): Boolean =
        v.removeSuffix("%").trim().toDoubleOrNull()?.let { it == 0.0 } ?: false

    private fun hsrCharFromHoyo(o: JSONObject, propMap: Map<Int, String>): EnkaChar {
        val id = o.optInt("id")
        return EnkaChar(
            id = id,
            name = o.optString("name").ifBlank { "#$id" },
            level = o.optInt("level"),
            rank = o.optInt("rank"), // 성흔
            rarity = o.optInt("rarity", 5),
            iconUrl = hoyoIcon(o.optString("icon").ifBlank { o.optString("image") }),
            element = hsrElementKo(o.optString("element")),
            path = hsrPathKo(o.optInt("base_type")),
            detailed = true,
            stats = hsrHoyoStats(o.optJSONArray("properties"), propMap),
            weapon = o.optJSONObject("equip")?.let { e ->
                EnkaWeapon(e.optString("name"), e.optInt("level"), e.optInt("rank"), null, null)
            },
            artifacts = hsrHoyoRelics(o.optJSONArray("relics"), o.optJSONArray("ornaments"), propMap),
        )
    }

    /** mihomo relic_sets → 활성 세트 효과. */
    private fun hsrSets(c: JSONObject): List<EnkaSet> = buildList {
        val rs = c.optJSONArray("relic_sets") ?: return@buildList
        for (i in 0 until rs.length()) {
            val s = rs.optJSONObject(i) ?: continue
            val name = s.optString("name")
            if (name.isBlank()) continue
            val desc = cleanName(s.optString("desc")).takeIf { it.isNotBlank() }
            add(EnkaSet(name, s.optInt("num"), listOfNotNull(desc)))
        }
    }

    /** HoYoLAB properties[] {property_type, final} → 핵심 스탯(0 값 제외). */
    private fun hsrHoyoStats(props: JSONArray?, propMap: Map<Int, String>): List<EnkaStatLine> = buildList {
        props ?: return@buildList
        for (i in 0 until props.length()) {
            val p = props.optJSONObject(i) ?: continue
            val name = propMap[p.optInt("property_type")] ?: continue
            val value = p.optString("final").ifBlank { p.optString("value") }
            if (value.isBlank() || isZeroValue(value)) continue
            add(EnkaStatLine(name, hsrFmt(value), hsrCrit(name)))
        }
    }

    /** HoYoLAB relics[]+ornaments[] → 유물 슬롯. main_property/properties 의 property_type 를 propMap 으로 매핑. */
    private fun hsrHoyoRelics(relics: JSONArray?, ornaments: JSONArray?, propMap: Map<Int, String>): List<EnkaArtifact> = buildList {
        fun addAll(arr: JSONArray?) {
            arr ?: return
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val mainObj = r.optJSONObject("main_property") ?: continue
                val mainName = propMap[mainObj.optInt("property_type")].orEmpty()
                val mainVal = mainObj.optString("value")
                val subs = r.optJSONArray("properties")?.let { sa ->
                    (0 until sa.length()).mapNotNull { j ->
                        val s = sa.optJSONObject(j) ?: return@mapNotNull null
                        val sn = propMap[s.optInt("property_type")].orEmpty()
                        EnkaStatLine(sn, hsrFmt(s.optString("value")), hsrCrit(sn))
                    }
                }.orEmpty()
                val pos = r.optInt("pos", 0).takeIf { it in 1..6 } ?: (size + 1)
                add(
                    EnkaArtifact(
                        slot = hsrSlots.getOrElse(pos - 1) { "유물" },
                        setName = r.optString("name"),
                        level = r.optInt("level"),
                        main = EnkaStatLine(mainName, hsrFmt(mainVal), hsrCrit(mainName)),
                        subs = subs,
                    ),
                )
            }
        }
        addAll(relics)
        addAll(ornaments)
    }

    // Mihomo 응답의 상대 아이콘 경로는 StarRailRes 에셋 → raw.githubusercontent 가 정본(api.mihomo.me 는 404)
    private fun mihomoIcon(path: String): String? = when {
        path.isBlank() -> null
        path.startsWith("http") -> path
        else -> "https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/$path"
    }

    private fun hsrCrit(name: String): Boolean = name.contains("치명")

    /** Mihomo attributes(기본) + additions(유물·세트·성흔) 합산 → 핵심 스탯. */
    private fun hsrStats(c: JSONObject): List<EnkaStatLine> {
        val acc = linkedMapOf<String, Triple<Double, Boolean, String>>() // field → (합산값, percent, 이름)
        fun merge(arr: JSONArray?) {
            arr ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val f = o.optString("field")
                if (f.isBlank()) continue
                val prev = acc[f]
                acc[f] = Triple((prev?.first ?: 0.0) + o.optDouble("value", 0.0), o.optBoolean("percent"), o.optString("name"))
            }
        }
        merge(c.optJSONArray("attributes"))
        merge(c.optJSONArray("additions"))
        fun line(field: String, fallback: String, crit: Boolean = false): EnkaStatLine? {
            val t = acc[field] ?: return null
            val value = if (t.second) pctVal(t.first * 100) else comma(rnd(t.first))
            return EnkaStatLine(t.third.ifBlank { fallback }, value, crit)
        }
        val elem = listOf("physical_dmg", "fire_dmg", "ice_dmg", "lightning_dmg", "wind_dmg", "quantum_dmg", "imaginary_dmg")
            .maxByOrNull { acc[it]?.first ?: 0.0 }
        return listOfNotNull(
            line("hp", "HP"),
            line("atk", "공격력"),
            line("def", "방어력"),
            line("spd", "속도"),
            line("crit_rate", "치명타 확률", crit = true),
            line("crit_dmg", "치명타 피해", crit = true),
            line("break_dmg", "격파 특화"),
            elem?.let { line(it, "속성 피해") },
        )
    }

    private fun hsrLightCone(lc: JSONObject?, officialName: String? = null): EnkaWeapon? {
        lc ?: return null
        val main = lc.optJSONArray("attributes")?.optJSONObject(0)?.let {
            EnkaStatLine(it.optString("name"), it.optString("display"), false)
        }
        return EnkaWeapon(
            // §5 폴백: HoYoLAB 공식 KR 광추명 → mihomo
            name = officialName?.ifBlank { null } ?: lc.optString("name"),
            level = lc.optInt("level"),
            refinement = lc.optInt("rank"), // 중첩
            main = main,
            sub = null,
        )
    }

    private fun hsrRelics(arr: JSONArray?): List<EnkaArtifact> = buildList {
        arr ?: return@buildList
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val mainAffix = r.optJSONObject("main_affix") ?: continue
            val main = EnkaStatLine(mainAffix.optString("name"), mainAffix.optString("display"), hsrCrit(mainAffix.optString("name")))
            val subs = r.optJSONArray("sub_affix")?.let { sa ->
                (0 until sa.length()).mapNotNull { j ->
                    val s = sa.optJSONObject(j) ?: return@mapNotNull null
                    EnkaStatLine(s.optString("name"), s.optString("display"), hsrCrit(s.optString("name")))
                }
            }.orEmpty()
            // relic.type 1~6 = 머리/손/몸통/발/행성구/연결로프 (int 또는 string 대응)
            val slotType = r.optInt("type", 0).takeIf { it in 1..6 } ?: r.optString("type").toIntOrNull() ?: (i + 1)
            add(
                EnkaArtifact(
                    slot = hsrSlots.getOrElse(slotType - 1) { "유물" },
                    setName = r.optString("set_name"),
                    level = r.optInt("level"),
                    main = main,
                    subs = subs,
                ),
            )
        }
    }

    // ----------------------------------------------------------------- 메타 매핑 (Yatta: 한글명·희귀도·아이콘·원소)
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
    private fun cleanName(raw: String): String =
        raw.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()

    /** 원신 원소 영문 → 한글. Yatta(Fire/Water…) + HoYoLAB(Pyro/Hydro…) 키 모두 지원. */
    private fun giElementKo(e: String): String = when (e) {
        "Fire", "Pyro" -> "불"
        "Water", "Hydro" -> "물"
        "Electric", "Electro" -> "번개"
        "Ice", "Cryo" -> "얼음"
        "Wind", "Anemo" -> "바람"
        "Rock", "Geo" -> "바위"
        "Grass", "Dendro" -> "풀"
        else -> ""
    }

    /** HoYoLAB HSR base_type(int) → 운명의 길 KR. */
    private fun hsrPathKo(t: Int): String = when (t) {
        1 -> "파멸"; 2 -> "수렵"; 3 -> "지식"; 4 -> "화합"
        5 -> "공허"; 6 -> "보존"; 7 -> "풍요"; 8 -> "기억"
        else -> ""
    }

    /** 스타레일 전투속성 영문 → 한글. Yatta(대문자) + HoYoLAB(소문자, quantum 등) 모두 지원. */
    private fun hsrElementKo(e: String): String = when (e.lowercase()) {
        "fire" -> "화염"
        "ice" -> "얼음"
        "thunder", "lightning" -> "번개"
        "wind" -> "바람"
        "physical" -> "물리"
        "quantum" -> "양자"
        "imaginary" -> "허수"
        else -> ""
    }

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

    private fun rnd(v: Double): Int = (v + 0.5).toInt() // 스탯은 음수 없음 → 단순 반올림
    private fun comma(n: Int): String {
        val s = n.toString(); val sb = StringBuilder()
        s.forEachIndexed { i, c -> if (i > 0 && (s.length - i) % 3 == 0) sb.append(','); sb.append(c) }
        return sb.toString()
    }
    private fun pctRatio(v: Double): String = "${rnd(v * 1000) / 10.0}%" // 비율(0.714)→71.4%
    private fun pctVal(v: Double): String = "${rnd(v * 10) / 10.0}%"      // 이미 퍼센트값(62.2)→62.2%
    private fun fmtStat(value: Double, percent: Boolean): String =
        if (percent) pctVal(value) else comma(rnd(value))

    /** fightPropMap(숫자키, 비율) → 핵심 8스탯. */
    private fun giStats(fp: JSONObject): List<EnkaStatLine> {
        val elemKey = listOf("40", "41", "42", "43", "44", "45", "46", "30")
            .maxByOrNull { fp.optDouble(it, 0.0) } ?: "30"
        return listOf(
            // HP: 인게임 공식은 "생명력"이나 앱 일관성을 위해 두 게임 모두 "HP"로 통일(문서 §2 결정)
            EnkaStatLine("HP", comma(rnd(fp.optDouble("2000")))),
            EnkaStatLine("공격력", comma(rnd(fp.optDouble("2001")))),
            EnkaStatLine("방어력", comma(rnd(fp.optDouble("2002")))),
            EnkaStatLine("원소 마스터리", rnd(fp.optDouble("28")).toString()),
            EnkaStatLine("치명타 확률", pctRatio(fp.optDouble("20")), crit = true),
            EnkaStatLine("치명타 피해", pctRatio(fp.optDouble("22")), crit = true),
            EnkaStatLine("원소 충전 효율", pctRatio(fp.optDouble("23"))),
            EnkaStatLine(giElemDmgLabel(elemKey), pctRatio(fp.optDouble(elemKey))),
        )
    }

    private fun giElemDmgLabel(key: String): String = when (key) {
        "40" -> "불 원소 피해 보너스"; "41" -> "번개 원소 피해 보너스"; "42" -> "물 원소 피해 보너스"
        "43" -> "풀 원소 피해 보너스"; "44" -> "바람 원소 피해 보너스"; "45" -> "바위 원소 피해 보너스"
        "46" -> "얼음 원소 피해 보너스"; else -> "물리 피해 보너스"
    }

    /** FIGHT_PROP_* → (한글 라벨, 퍼센트여부, 치명타여부). 무기·성유물 공용. */
    private fun giProp(id: String): Triple<String, Boolean, Boolean> = when (id) {
        // 문서 §3-2: HP/공격력/방어력은 고정값/퍼센트를 라벨에 명시. (BASE_ATTACK 은 무기 메인 → giWeapon 에서 "기초 공격력"으로 덮어씀)
        "FIGHT_PROP_HP" -> Triple("HP(고정)", false, false)
        "FIGHT_PROP_HP_PERCENT" -> Triple("HP(%)", true, false)
        "FIGHT_PROP_ATTACK", "FIGHT_PROP_BASE_ATTACK" -> Triple("공격력(고정)", false, false)
        "FIGHT_PROP_ATTACK_PERCENT" -> Triple("공격력(%)", true, false)
        "FIGHT_PROP_DEFENSE" -> Triple("방어력(고정)", false, false)
        "FIGHT_PROP_DEFENSE_PERCENT" -> Triple("방어력(%)", true, false)
        "FIGHT_PROP_ELEMENT_MASTERY" -> Triple("원소 마스터리", false, false)
        "FIGHT_PROP_CRITICAL" -> Triple("치명타 확률", true, true)
        "FIGHT_PROP_CRITICAL_HURT" -> Triple("치명타 피해", true, true)
        "FIGHT_PROP_CHARGE_EFFICIENCY" -> Triple("원소 충전 효율", true, false)
        "FIGHT_PROP_HEAL_ADD" -> Triple("치유 보너스", true, false)
        "FIGHT_PROP_PHYSICAL_ADD_HURT" -> Triple("물리 피해 보너스", true, false)
        "FIGHT_PROP_FIRE_ADD_HURT" -> Triple("불 원소 피해 보너스", true, false)
        "FIGHT_PROP_ELEC_ADD_HURT" -> Triple("번개 원소 피해 보너스", true, false)
        "FIGHT_PROP_WATER_ADD_HURT" -> Triple("물 원소 피해 보너스", true, false)
        "FIGHT_PROP_GRASS_ADD_HURT" -> Triple("풀 원소 피해 보너스", true, false)
        "FIGHT_PROP_WIND_ADD_HURT" -> Triple("바람 원소 피해 보너스", true, false)
        "FIGHT_PROP_ROCK_ADD_HURT" -> Triple("바위 원소 피해 보너스", true, false)
        "FIGHT_PROP_ICE_ADD_HURT" -> Triple("얼음 원소 피해 보너스", true, false)
        else -> Triple("기타", false, false)
    }

    private fun giSlot(equipType: String): String = when (equipType) {
        "EQUIP_BRACER" -> "꽃"; "EQUIP_NECKLACE" -> "깃털"; "EQUIP_SHOES" -> "모래"
        "EQUIP_RING" -> "성배"; "EQUIP_DRESS" -> "왕관"; else -> "성유물"
    }

    private fun giWeapon(equipList: JSONArray, names: Map<Int, String>): EnkaWeapon? {
        for (i in 0 until equipList.length()) {
            val e = equipList.optJSONObject(i) ?: continue
            val w = e.optJSONObject("weapon") ?: continue
            val flat = e.optJSONObject("flat") ?: continue
            val refine = w.optJSONObject("affixMap")?.let { am ->
                val it = am.keys(); if (it.hasNext()) am.optInt(it.next()) + 1 else 1
            } ?: 1
            var main: EnkaStatLine? = null
            var sub: EnkaStatLine? = null
            flat.optJSONArray("weaponStats")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val s = arr.optJSONObject(j) ?: continue
                    val pid = s.optString("appendPropId")
                    val (label, percent, crit) = giProp(pid)
                    val line = EnkaStatLine(
                        if (pid == "FIGHT_PROP_BASE_ATTACK") "기초 공격력" else label,
                        fmtStat(s.optDouble("statValue"), percent), crit,
                    )
                    if (pid == "FIGHT_PROP_BASE_ATTACK") main = line else sub = line
                }
            }
            return EnkaWeapon(names[e.optInt("itemId")] ?: "무기", w.optInt("level"), refine, main, sub)
        }
        return null
    }

    private fun giArtifacts(equipList: JSONArray): List<EnkaArtifact> = buildList {
        for (i in 0 until equipList.length()) {
            val e = equipList.optJSONObject(i) ?: continue
            val flat = e.optJSONObject("flat") ?: continue
            val ms = flat.optJSONObject("reliquaryMainstat") ?: continue // 성유물만 보유
            val (mLabel, mPct, mCrit) = giProp(ms.optString("mainPropId"))
            val subs = flat.optJSONArray("reliquarySubstats")?.let { arr ->
                (0 until arr.length()).mapNotNull { j ->
                    val s = arr.optJSONObject(j) ?: return@mapNotNull null
                    val (l, p, c) = giProp(s.optString("appendPropId"))
                    EnkaStatLine(l, fmtStat(s.optDouble("statValue"), p), c)
                }
            }.orEmpty()
            add(
                EnkaArtifact(
                    slot = giSlot(flat.optString("equipType")),
                    setName = "", // v1 미해결(로컬라이즈 텍스트맵 필요) — 후속
                    level = (e.optJSONObject("reliquary")?.optInt("level") ?: 1) - 1, // Enka +1 보정
                    main = EnkaStatLine(mLabel, fmtStat(ms.optDouble("statValue"), mPct), mCrit),
                    subs = subs,
                ),
            )
        }
    }

    // ---------------- HoYoLAB 원신 character/detail(보유 전체) 파싱 — property_map(type→KR명) 사용 ----------------
    private fun giCharFromHoyo(o: JSONObject, propMap: Map<Int, String>): EnkaChar {
        val base = o.optJSONObject("base") ?: o
        val id = base.optInt("id")
        return EnkaChar(
            id = id,
            name = base.optString("name").ifBlank { "#$id" },
            level = base.optInt("level"),
            rank = base.optInt("actived_constellation_num"), // 명좌
            rarity = base.optInt("rarity", 5),
            iconUrl = hoyoIcon(base.optString("icon").ifBlank { base.optString("image") }),
            element = giElementKo(base.optString("element")),
            detailed = true,
            stats = giHoyoStats(o, propMap),
            weapon = giHoyoWeapon(o.optJSONObject("weapon"), propMap),
            artifacts = giHoyoRelics(o.optJSONArray("relics"), propMap),
            sets = giHoyoSets(o.optJSONArray("relics")),
        )
    }

    /** 원신 성유물 set{name, affixes[activation_number, effect]} → 활성 세트 효과(장착 수 ≥ 발동 수). */
    private fun giHoyoSets(arr: JSONArray?): List<EnkaSet> = buildList {
        arr ?: return@buildList
        val count = linkedMapOf<String, Int>()
        val affixes = mutableMapOf<String, JSONArray?>()
        for (i in 0 until arr.length()) {
            val set = arr.optJSONObject(i)?.optJSONObject("set") ?: continue
            val name = set.optString("name")
            if (name.isBlank()) continue
            count[name] = (count[name] ?: 0) + 1
            affixes[name] = set.optJSONArray("affixes")
        }
        count.forEach { (name, c) ->
            val eff = affixes[name]?.let { af ->
                (0 until af.length()).mapNotNull { i ->
                    val a = af.optJSONObject(i) ?: return@mapNotNull null
                    val n = a.optInt("activation_number")
                    if (n in 1..c) "${n}세트 ${cleanName(a.optString("effect"))}" else null
                }
            }.orEmpty()
            add(EnkaSet(name, c, eff))
        }
    }

    /** base_properties + element_properties(0 제외, 중복 type 제거) → 핵심 스탯. */
    private fun giHoyoStats(o: JSONObject, propMap: Map<Int, String>): List<EnkaStatLine> = buildList {
        val seen = mutableSetOf<Int>()
        fun addFrom(arr: JSONArray?) {
            arr ?: return
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val type = p.optInt("property_type")
                if (type in seen) continue
                val name = propMap[type] ?: continue
                val value = p.optString("final").ifBlank { p.optString("value") }
                if (value.isBlank() || isZeroValue(value)) continue
                seen.add(type)
                add(EnkaStatLine(name, hsrFmt(value), hsrCrit(name)))
            }
        }
        addFrom(o.optJSONArray("base_properties"))   // HP·공격력·방어력·원소 마스터리
        addFrom(o.optJSONArray("extra_properties"))  // 치명타 확률·피해·원소 충전 효율·치유 보너스 등
        addFrom(o.optJSONArray("element_properties")) // 원소/물리 피해 보너스
    }

    private fun giHoyoWeapon(w: JSONObject?, propMap: Map<Int, String>): EnkaWeapon? {
        w ?: return null
        val main = w.optJSONObject("main_property")?.let {
            EnkaStatLine(propMap[it.optInt("property_type")]?.ifBlank { null } ?: "기초 공격력", hsrFmt(it.optString("final")), false)
        }
        val sub = w.optJSONObject("sub_property")?.takeIf { it.optString("final").isNotBlank() }?.let {
            val n = propMap[it.optInt("property_type")].orEmpty()
            EnkaStatLine(n, hsrFmt(it.optString("final")), hsrCrit(n))
        }
        return EnkaWeapon(w.optString("name"), w.optInt("level"), w.optInt("affix_level"), main, sub)
    }

    private fun giHoyoRelics(arr: JSONArray?, propMap: Map<Int, String>): List<EnkaArtifact> = buildList {
        arr ?: return@buildList
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val mainObj = r.optJSONObject("main_property") ?: continue
            val mainName = propMap[mainObj.optInt("property_type")].orEmpty()
            val subs = r.optJSONArray("sub_property_list")?.let { sa ->
                (0 until sa.length()).mapNotNull { j ->
                    val s = sa.optJSONObject(j) ?: return@mapNotNull null
                    val sn = propMap[s.optInt("property_type")].orEmpty()
                    EnkaStatLine(sn, hsrFmt(s.optString("value")), hsrCrit(sn))
                }
            }.orEmpty()
            add(
                EnkaArtifact(
                    slot = r.optString("pos_name").ifBlank { "성유물" },
                    setName = r.optJSONObject("set")?.optString("name").orEmpty(),
                    level = r.optInt("level"),
                    main = EnkaStatLine(mainName, hsrFmt(mainObj.optString("value")), hsrCrit(mainName)),
                    subs = subs,
                ),
            )
        }
    }

    // ---------------- 젠레스(ZZZ) avatar/info 파싱 — 응답 라벨(property_name) 사용, property_map 없음 ----------------
    private suspend fun fetchZzz(uid: String, ltuid: String, ltoken: String): EnkaResult {
        val list = HoyolabApi.fetchZzzAvatars(ltuid, ltoken, uid)
            ?: return EnkaResult(null, HoyolabApi.zzzLastError ?: "젠레스 정보를 불러오지 못했어요")
        val chars = list.map { zzzChar(it) }
        if (chars.isEmpty()) return EnkaResult(null, "표시할 에이전트가 없어요")
        return EnkaResult(EnkaProfile("", 0, 0, "", chars), null)
    }

    private fun zzzCrit(name: String): Boolean = name.contains("치명") || name.contains("CRIT", ignoreCase = true)

    /** ZZZ element_type(int) → KR 속성. (200 물리·201 화염·202/206 얼음·203 전기·205/207 에테르) */
    private fun zzzElementKo(type: Int): String = when (type) {
        200 -> "물리"
        201 -> "화염"
        202, 206 -> "얼음"
        203 -> "전기"
        205, 207 -> "에테르"
        else -> ""
    }

    /** ZZZ 스탯명 영문→KR(응답이 계정 언어라 영문일 수 있음). property_map 부재 보완. 미매칭은 원문 유지. */
    private fun zzzKrStat(en: String): String = when (en.trim()) {
        "HP" -> "HP"
        "ATK" -> "공격력"
        "Base ATK" -> "기초 공격력"
        "DEF" -> "방어력"
        "Impact" -> "충격력"
        "CRIT Rate" -> "치명타 확률"
        "CRIT DMG" -> "치명타 피해"
        "Anomaly Mastery" -> "이상 숙련"
        "Anomaly Proficiency" -> "이상 장악력"
        "PEN Ratio" -> "관통률"
        "PEN", "Flat PEN" -> "관통값"
        "Energy Regen" -> "에너지 자동 회복"
        "Sheer Force" -> "실효 능력"
        "Physical DMG Bonus" -> "물리 속성 피해 보너스"
        "Fire DMG Bonus" -> "화염 속성 피해 보너스"
        "Ice DMG Bonus" -> "냉기 속성 피해 보너스"
        "Electric DMG Bonus" -> "전기 속성 피해 보너스"
        "Ether DMG Bonus" -> "에테르 속성 피해 보너스"
        else -> en
    }

    private fun zzzChar(o: JSONObject): EnkaChar {
        val id = o.optInt("id")
        return EnkaChar(
            id = id,
            name = o.optString("name_mi18n").ifBlank { "#$id" },
            level = o.optInt("level"),
            rank = o.optInt("rank"), // 마인드스케이프(시너지)
            rarity = if (o.optString("rarity") == "S") 5 else 4, // S→5★ / A→4★ (색·필터 호환)
            iconUrl = o.optString("role_square_url").ifBlank { o.optString("group_icon_path") }.takeIf { it.startsWith("http") },
            element = zzzElementKo(o.optInt("element_type")),
            detailed = true,
            stats = zzzStats(o.optJSONArray("properties")),
            weapon = zzzWeapon(o.optJSONObject("weapon")),
            artifacts = zzzDiscs(o.optJSONArray("equip")),
            sets = zzzSets(o.optJSONArray("equip")),
        )
    }

    /** ZZZ 드라이브 디스크 equip_suit{name, own, desc1/2} → 활성 세트 효과(2/4). */
    private fun zzzSets(arr: JSONArray?): List<EnkaSet> = buildList {
        arr ?: return@buildList
        val seen = linkedMapOf<Int, EnkaSet>()
        for (i in 0 until arr.length()) {
            val suit = arr.optJSONObject(i)?.optJSONObject("equip_suit") ?: continue
            val sid = suit.optInt("suit_id")
            if (sid == 0 || sid in seen) continue
            val name = suit.optString("name")
            if (name.isBlank()) continue
            val own = suit.optInt("own")
            val eff = buildList {
                if (own >= 2) suit.optString("desc1").takeIf { it.isNotBlank() }?.let { add("2세트 ${cleanName(it)}") }
                if (own >= 4) suit.optString("desc2").takeIf { it.isNotBlank() }?.let { add("4세트 ${cleanName(it)}") }
            }
            seen[sid] = EnkaSet(name, own, eff)
        }
        addAll(seen.values)
    }

    /** ZZZ 패널 properties[] {property_name, final} → 핵심 스탯. */
    private fun zzzStats(props: JSONArray?): List<EnkaStatLine> = buildList {
        props ?: return@buildList
        for (i in 0 until props.length()) {
            val p = props.optJSONObject(i) ?: continue
            val name = p.optString("property_name")
            val value = p.optString("final").ifBlank { p.optString("base") }
            if (name.isBlank() || value.isBlank()) continue
            val kr = zzzKrStat(name)
            add(EnkaStatLine(kr, hsrFmt(value), zzzCrit(kr)))
        }
    }

    /** 음동기(W-Engine) — main_properties[0]=기초 공격력, properties[0]=상위 스탯, star=페이즈. */
    private fun zzzWeapon(w: JSONObject?): EnkaWeapon? {
        w ?: return null
        fun line(arr: JSONArray?) = arr?.optJSONObject(0)?.let {
            val n = zzzKrStat(it.optString("property_name"))
            EnkaStatLine(n, hsrFmt(it.optString("base")), zzzCrit(n))
        }
        return EnkaWeapon(w.optString("name"), w.optInt("level"), w.optInt("star"), line(w.optJSONArray("main_properties")), line(w.optJSONArray("properties")))
    }

    /** 드라이브 디스크 — equipment_type(슬롯 1~6)·main_properties[0]·properties[](부옵션)·equip_suit.name(세트). */
    private fun zzzDiscs(arr: JSONArray?): List<EnkaArtifact> = buildList {
        arr ?: return@buildList
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val mainObj = e.optJSONArray("main_properties")?.optJSONObject(0) ?: continue
            val mainName = zzzKrStat(mainObj.optString("property_name"))
            val subs = e.optJSONArray("properties")?.let { sa ->
                (0 until sa.length()).mapNotNull { j ->
                    val s = sa.optJSONObject(j) ?: return@mapNotNull null
                    val sn = zzzKrStat(s.optString("property_name"))
                    EnkaStatLine(sn, hsrFmt(s.optString("base")), zzzCrit(sn))
                }
            }.orEmpty()
            add(
                EnkaArtifact(
                    slot = "${e.optInt("equipment_type")}번",
                    setName = e.optJSONObject("equip_suit")?.optString("name").orEmpty(),
                    level = e.optInt("level"),
                    main = EnkaStatLine(mainName, hsrFmt(mainObj.optString("base")), zzzCrit(mainName)),
                    subs = subs,
                ),
            )
        }
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
