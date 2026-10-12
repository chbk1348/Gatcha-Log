package com.gatcha.log.data.api

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

// [EnkaApi.cleanName] 이 쓰는 정규식 — 파일 레벨에서 한 번만 컴파일한다.
// 예전엔 함수 안에 있어서, 메타 맵을 만들 때 **항목 수만큼**(수백~수천) 두 개를 다시 컴파일했다.

// 원신 응답 파서 — Enka 쇼케이스(fightPropMap · equipList)와 HoYoLAB character/detail. `EnkaApi.kt` 에서 나눴다(27.52.0).
// 숫자 표기 도우미(rnd · comma · pct…)도 여기 있고 세 게임 파서가 같이 쓴다.

/** 원신 원소 영문 → 한글. Yatta(Fire/Water…) + HoYoLAB(Pyro/Hydro…) 키 모두 지원. */
internal fun giElementKo(e: String): String = when (e) {
    "Fire", "Pyro" -> "불"
    "Water", "Hydro" -> "물"
    "Electric", "Electro" -> "번개"
    "Ice", "Cryo" -> "얼음"
    "Wind", "Anemo" -> "바람"
    "Rock", "Geo" -> "바위"
    "Grass", "Dendro" -> "풀"
    else -> ""
}

internal fun rnd(v: Double): Int = (v + 0.5).toInt() // 스탯은 음수 없음 → 단순 반올림

/** 천 단위 콤마 — 구현은 공용 한 벌([com.gatcha.log.util.num])을 쓴다(27.52.0). */
internal fun comma(n: Int): String = com.gatcha.log.util.num(n)

internal fun pctRatio(v: Double): String = "${rnd(v * 1000) / 10.0}%" // 비율(0.714)→71.4%

internal fun pctVal(v: Double): String = "${rnd(v * 10) / 10.0}%"      // 이미 퍼센트값(62.2)→62.2%

internal fun fmtStat(value: Double, percent: Boolean): String =
    if (percent) pctVal(value) else comma(rnd(value))

/** fightPropMap(숫자키, 비율) → 핵심 8스탯. */
internal fun giStats(fp: JSONObject): List<EnkaStatLine> {
    val elemKey = listOf("40", "41", "42", "43", "44", "45", "46", "30")
        .maxByOrNull { fp.optDouble(it, 0.0) } ?: "30"
    // 보너스가 전부 0 이면 어느 원소라고 말할 근거가 없다 — 예전엔 목록의 첫째라 「불 원소 피해 보너스 0.0%」가 됐다.
    val elemLabel = if (fp.optDouble(elemKey, 0.0) > 0.0) giElemDmgLabel(elemKey) else "원소 피해 보너스"
    return listOf(
        // HP: 인게임 공식은 "생명력"이나 앱 일관성을 위해 두 게임 모두 "HP"로 통일(문서 §2 결정)
        EnkaStatLine("HP", comma(rnd(fp.optDouble("2000")))),
        EnkaStatLine("공격력", comma(rnd(fp.optDouble("2001")))),
        EnkaStatLine("방어력", comma(rnd(fp.optDouble("2002")))),
        EnkaStatLine("원소 마스터리", rnd(fp.optDouble("28")).toString()),
        EnkaStatLine("치명타 확률", pctRatio(fp.optDouble("20")), crit = true),
        EnkaStatLine("치명타 피해", pctRatio(fp.optDouble("22")), crit = true),
        EnkaStatLine("원소 충전 효율", pctRatio(fp.optDouble("23"))),
        EnkaStatLine(elemLabel, pctRatio(fp.optDouble(elemKey, 0.0))),
    )
}

internal fun giElemDmgLabel(key: String): String = when (key) {
    "40" -> "불 원소 피해 보너스"; "41" -> "번개 원소 피해 보너스"; "42" -> "물 원소 피해 보너스"
    "43" -> "풀 원소 피해 보너스"; "44" -> "바람 원소 피해 보너스"; "45" -> "바위 원소 피해 보너스"
    "46" -> "얼음 원소 피해 보너스"; else -> "물리 피해 보너스"
}

/** FIGHT_PROP_* → (한글 라벨, 퍼센트여부, 치명타여부). 무기·성유물 공용. */
internal fun giProp(id: String): Triple<String, Boolean, Boolean> = when (id) {
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

internal fun giSlot(equipType: String): String = when (equipType) {
    "EQUIP_BRACER" -> "꽃"; "EQUIP_NECKLACE" -> "깃털"; "EQUIP_SHOES" -> "모래"
    "EQUIP_RING" -> "성배"; "EQUIP_DRESS" -> "왕관"; else -> "성유물"
}

internal fun giWeapon(equipList: JSONArray, names: Map<Int, String>): EnkaWeapon? {
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
        val wIcon = flat.optString("icon")
        return EnkaWeapon(
            names[e.optInt("itemId")] ?: "무기", w.optInt("level"), refine, main, sub,
            id = e.optInt("itemId"),
            iconUrl = if (wIcon.isBlank()) null else "https://enka.network/ui/$wIcon.png",
        )
    }
    return null
}

internal fun giArtifacts(equipList: JSONArray): List<EnkaArtifact> = buildList {
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
        val icon = flat.optString("icon")
        add(
            EnkaArtifact(
                slot = giSlot(flat.optString("equipType")),
                setName = "", // v1 미해결(로컬라이즈 텍스트맵 필요) — 후속
                level = (e.optJSONObject("reliquary")?.optInt("level") ?: 1) - 1, // Enka +1 보정
                main = EnkaStatLine(mLabel, fmtStat(ms.optDouble("statValue"), mPct), mCrit),
                subs = subs,
                iconUrl = if (icon.isBlank()) null else "https://enka.network/ui/$icon.png",
            ),
        )
    }
}

// ---------------- HoYoLAB 원신 character/detail(보유 전체) 파싱 — property_map(type→KR명) 사용 ----------------
internal fun giCharFromHoyo(o: JSONObject, propMap: Map<Int, String>): EnkaChar {
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

/**
 * 원신 성유물 set{name, affixes[activation_number, effect]} → 세트 효과.
 * 미발동 tier(예: 2개만 껴서 4세트 미발동)도 active=false 로 포함. 발동 효과가 1개 이상인 세트만 노출.
 */
internal fun giHoyoSets(arr: JSONArray?): List<EnkaSet> = buildList {
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
                if (n >= 1) EnkaSetEffect(n, EnkaApi.cleanName(a.optString("effect")), n <= c) else null
            }.sortedBy { it.pieces }
        }.orEmpty()
        if (eff.any { it.active }) add(EnkaSet(name, c, eff))
    }
}

/** base_properties + element_properties(0 제외, 중복 type 제거) → 핵심 스탯. */
internal fun giHoyoStats(o: JSONObject, propMap: Map<Int, String>): List<EnkaStatLine> = buildList {
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

internal fun giHoyoWeapon(w: JSONObject?, propMap: Map<Int, String>): EnkaWeapon? {
    w ?: return null
    val main = w.optJSONObject("main_property")?.let {
        EnkaStatLine(propMap[it.optInt("property_type")]?.ifBlank { null } ?: "기초 공격력", hsrFmt(it.optString("final")), false)
    }
    val sub = w.optJSONObject("sub_property")?.takeIf { it.optString("final").isNotBlank() }?.let {
        val n = propMap[it.optInt("property_type")].orEmpty()
        EnkaStatLine(n, hsrFmt(it.optString("final")), hsrCrit(n))
    }
    // ⚠️ id 를 빠뜨리면 **장비 특성(정련 효과)이 영영 안 뜬다** — 화면이 id > 0 일 때만
    // 도감을 조회한다. HoYoLAB 연동 계정은 이 경로를 타는데 여기만 비어 있었다.
    return EnkaWeapon(
        w.optString("name"), w.optInt("level"), w.optInt("affix_level"), main, sub,
        id = w.optInt("id"),
        iconUrl = hoyoIcon(w.optString("icon")),
        // ⚠️ `desc` 는 **무기 소개문(플레이버)** 이지 정련 효과가 아니다. 특성 자리에 넣었더니
        // 효과 설명과 배경 이야기가 나란히 떴다. 원신 특성은 도감([NanokaApi])만 출처다.
    )
}

internal fun giHoyoRelics(arr: JSONArray?, propMap: Map<Int, String>): List<EnkaArtifact> = buildList {
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
                iconUrl = r.optString("icon").takeIf { it.startsWith("http") },
            ),
        )
    }
}
