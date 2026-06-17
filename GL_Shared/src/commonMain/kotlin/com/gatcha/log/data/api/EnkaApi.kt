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
    /** 캐릭터 상세 공개 시에만 채워짐(풀 스탯시트). 비공개면 false → 로스터만 표시. */
    val detailed: Boolean = false,
    val stats: List<EnkaStatLine> = emptyList(),
    val weapon: EnkaWeapon? = null,
    val artifacts: List<EnkaArtifact> = emptyList(),
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

    suspend fun fetchProfile(game: String, uid: String): EnkaResult {
        val u = uid.trim()
        if (u.isBlank() || u.any { !it.isDigit() }) return EnkaResult(null, "UID는 숫자만 입력하세요")
        return if (game == "hsr" || game == "starrail") fetchHsr(u) else fetchGenshin(u)
    }

    // ----------------------------------------------------------------- 원신
    private suspend fun fetchGenshin(uid: String): EnkaResult {
        val res = Net.get("https://enka.network/api/uid/$uid", headers)
        errorFor(res.code)?.let { return EnkaResult(null, it) }
        return runCatching {
            val json = JSONObject(res.body)
            val p = json.getJSONObject("playerInfo")
            val show = p.optJSONArray("showAvatarInfoList") ?: JSONArray()
            // 상세 스탯(fightPropMap·equipList)은 "캐릭터 상세 공개" 시에만 avatarInfoList 에 존재.
            val detailed = json.optJSONArray("avatarInfoList")
            val meta = avatarMeta(false)
            val chars = if (detailed != null && detailed.length() > 0) {
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

    private suspend fun fetchHsr(uid: String): EnkaResult {
        val res = Net.get("https://api.mihomo.me/sr_info_parsed/$uid?lang=kr", headers)
        errorFor(res.code)?.let { return EnkaResult(null, it) }
        return runCatching {
            val json = JSONObject(res.body)
            val player = json.optJSONObject("player")
            val list = json.optJSONArray("characters") ?: JSONArray()
            val chars = (0 until list.length()).mapNotNull { i ->
                val a = list.optJSONObject(i) ?: return@mapNotNull null
                EnkaChar(
                    id = a.optString("id").toIntOrNull() ?: 0,
                    name = a.optString("name"),
                    level = a.optInt("level"),
                    rank = a.optInt("rank"), // 성혼
                    rarity = a.optInt("rarity", 5),
                    iconUrl = mihomoIcon(a.optString("icon")),
                    element = a.optJSONObject("element")?.optString("name").orEmpty(),
                    detailed = true,
                    stats = hsrStats(a),
                    weapon = hsrLightCone(a.optJSONObject("light_cone")),
                    artifacts = hsrRelics(a.optJSONArray("relics")),
                )
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

    private fun hsrLightCone(lc: JSONObject?): EnkaWeapon? {
        lc ?: return null
        val main = lc.optJSONArray("attributes")?.optJSONObject(0)?.let {
            EnkaStatLine(it.optString("name"), it.optString("display"), false)
        }
        return EnkaWeapon(
            name = lc.optString("name"),
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

    /** Yatta 원신 원소 영문 → 한글 */
    private fun giElementKo(e: String): String = when (e) {
        "Fire" -> "불"
        "Water" -> "물"
        "Electric" -> "번개"
        "Ice" -> "얼음"
        "Wind" -> "바람"
        "Rock" -> "바위"
        "Grass" -> "풀"
        else -> ""
    }

    /** Yatta 스타레일 전투속성 영문 → 한글 */
    private fun hsrElementKo(e: String): String = when (e) {
        "Fire" -> "화염"
        "Ice" -> "얼음"
        "Thunder" -> "번개"
        "Wind" -> "바람"
        "Physical" -> "물리"
        "Quantum" -> "양자"
        "Imaginary" -> "허수"
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
