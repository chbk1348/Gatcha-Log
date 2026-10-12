package com.gatcha.log.data.api

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

// [EnkaApi.cleanName] 이 쓰는 정규식 — 파일 레벨에서 한 번만 컴파일한다.
// 예전엔 함수 안에 있어서, 메타 맵을 만들 때 **항목 수만큼**(수백~수천) 두 개를 다시 컴파일했다.

// 스타레일 응답 파서 — mihomo(쇼케이스 풀스탯)와 HoYoLAB avatar/info. `EnkaApi.kt` 에서 나눴다(27.52.0).
// 세트 메타 캐시와 받아 오는 일은 `EnkaApi` 에 남아 있다.

// ----------------------------------------------------------------- 스타레일 (Mihomo parsed)
// Enka HSR 은 원시 데이터만 줘 최종 스탯 계산이 불가 → Mihomo parsed API 사용
// (KR 표시문자열·세트명까지 계산해서 반환). 로스터+풀스탯 동일 응답에서 파싱.
internal val hsrSlots = listOf("머리", "핸드", "바디", "신발", "차원 구체", "연결 매듭")

// ---------------- HoYoLAB avatar/info(보유 전체) 파싱 — property_info(type→KR명) + 응답 value 사용 ----------------
internal fun hoyoIcon(p: String): String? = p.takeIf { it.startsWith("http") }

/** property_info {"53":{name:"치명타 피해",...}} → property_type → KR 스탯명. */
internal fun hsrPropMap(info: JSONObject?): Map<Int, String> {
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
internal fun hsrFmt(v: String): String =
    if (v.isNotEmpty() && v.all { it.isDigit() }) comma(v.toInt()) else v

internal fun isZeroValue(v: String): Boolean =
    v.removeSuffix("%").trim().toDoubleOrNull()?.let { it == 0.0 } ?: false

internal fun hsrCharFromHoyo(o: JSONObject, propMap: Map<Int, String>): EnkaChar {
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
            EnkaWeapon(
                e.optString("name"), e.optInt("level"), e.optInt("rank"), null, null,
                id = e.optInt("id"),
                iconUrl = hoyoIcon(e.optString("icon")),
                // `desc` 는 광추 소개문이라 특성이 아니다(원신과 동일).
            )
        },
        artifacts = hsrHoyoRelics(o.optJSONArray("relics"), o.optJSONArray("ornaments"), propMap),
        sets = hsrHoyoSets(o.optJSONArray("relics"), o.optJSONArray("ornaments")),
    )
}

/**
 * HoYoLAB 로스터(쇼케이스 밖) relics+ornaments → 세트 효과. StarRailRes 메타로 set_id 묶음.
 * set_id 식별: relic.set_id → 조각 item id 매핑 → 이름 매핑 순 폴백. 발동 tier(2/4세트)만 표시(쇼케이스와 동일).
 */
internal fun hsrHoyoSets(relics: JSONArray?, ornaments: JSONArray?): List<EnkaSet> {
    val meta = EnkaApi.hsrSetMetaCache ?: return emptyList()
    val r2s = EnkaApi.hsrRelicSetCache.orEmpty()
    val n2s = EnkaApi.hsrSetNameCache.orEmpty()
    val count = linkedMapOf<String, Int>()
    fun tally(arr: JSONArray?) {
        arr ?: return
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val id = p.optString("id").takeIf { it.isNotBlank() } ?: p.optInt("id").takeIf { it != 0 }?.toString()
            val sid = p.optString("set_id").takeIf { it.isNotBlank() }
                ?: id?.let { r2s[it] }
                ?: n2s[p.optString("name")]
                ?: continue
            count[sid] = (count[sid] ?: 0) + 1
        }
    }
    tally(relics); tally(ornaments)
    return count.mapNotNull { (sid, c) ->
        if (c < 2) return@mapNotNull null
        val (name, descs) = meta[sid] ?: return@mapNotNull null
        val effects = descs.mapIndexedNotNull { idx, text ->
            val pieces = (idx + 1) * 2
            if (c >= pieces && text.isNotBlank()) EnkaSetEffect(pieces, EnkaApi.cleanName(text), true) else null
        }
        if (effects.isEmpty()) null
        else EnkaSet(name, c, effects, kind = if ((sid.toIntOrNull() ?: 0) >= 300) "장신구" else "유물")
    }
}

/**
 * mihomo relic_sets → 세트 효과.
 * mihomo 는 같은 세트를 tier 별 행(num=2 / num=4)으로 분리해 반환하므로 set_id 로 묶어 카드 1장에 모은다.
 * 반환되는 tier 는 모두 발동 상태(active=true) — 미발동 tier 는 응답에 없음.
 * 종류(kind): set_id ≥ 300 = "장신구"(차원 구체·연결 매듭), 그 외 = "유물".
 */
internal fun hsrSets(c: JSONObject): List<EnkaSet> = buildList {
    // 신규 세트(4.4 등)는 mihomo 응답의 이름·효과가 KR 미번역으로 비어 올 수 있다 → StarRailRes 메타(EN 폴백 포함)로 보완.
    val meta = EnkaApi.hsrSetMetaCache.orEmpty()
    val rs = c.optJSONArray("relic_sets") ?: return@buildList
    val grouped = linkedMapOf<String, MutableList<JSONObject>>()
    for (i in 0 until rs.length()) {
        val s = rs.optJSONObject(i) ?: continue
        val id = s.optString("id")
        if (id.isBlank()) continue // 이름이 비어도 메타로 채우므로 id 만 있으면 통과
        grouped.getOrPut(id) { mutableListOf() }.add(s)
    }
    grouped.forEach { (id, rows) ->
        val metaDescs = meta[id]?.second.orEmpty()
        val effects = rows.mapNotNull { s ->
            val num = s.optInt("num")
            val text = EnkaApi.cleanName(s.optString("desc")).takeIf { it.isNotBlank() }
                ?: metaDescs.getOrNull(num / 2 - 1)?.let { EnkaApi.cleanName(it) }?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            EnkaSetEffect(num, text, true)
        }.sortedBy { it.pieces }
        if (effects.isEmpty()) return@forEach
        val name = rows.first().optString("name").takeIf { it.isNotBlank() } ?: meta[id]?.first.orEmpty()
        val kind = if ((id.toIntOrNull() ?: 0) >= 300) "장신구" else "유물"
        add(EnkaSet(name, rows.maxOf { it.optInt("num") }, effects, kind))
    }
}

/** HoYoLAB properties[] {property_type, final} → 핵심 스탯(0 값 제외). */
internal fun hsrHoyoStats(props: JSONArray?, propMap: Map<Int, String>): List<EnkaStatLine> = buildList {
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
internal fun hsrHoyoRelics(relics: JSONArray?, ornaments: JSONArray?, propMap: Map<Int, String>): List<EnkaArtifact> = buildList {
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
                    iconUrl = r.optString("icon").takeIf { it.startsWith("http") },
                ),
            )
        }
    }
    addAll(relics)
    addAll(ornaments)
}

// Mihomo 응답의 상대 아이콘 경로는 StarRailRes 에셋 → raw.githubusercontent 가 정본(api.mihomo.me 는 404)
internal fun mihomoIcon(path: String): String? = when {
    path.isBlank() -> null
    path.startsWith("http") -> path
    else -> "https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/$path"
}

internal fun hsrCrit(name: String): Boolean = name.contains("치명")

/** Mihomo attributes(기본) + additions(유물·세트·성흔) 합산 → 핵심 스탯. */
internal fun hsrStats(c: JSONObject): List<EnkaStatLine> {
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
        line("break_dmg", "격파 특수효과"),
        // 폴백은 속성별로 — 신규 캐릭터라 mihomo 가 name 을 비워 보내도 '화염 피해'처럼 나온다.
        // 통짜 '속성 피해'면 physical_dmg 까지 그렇게 찍혀 normStat 이 PHYS_DMG 대신 ELEM_DMG 로 오분류했다.
        elem?.let { line(it, hsrElementKo(it.removeSuffix("_dmg")) + " 피해") },
    )
}

internal fun hsrLightCone(lc: JSONObject?, officialName: String? = null): EnkaWeapon? {
    lc ?: return null
    val lcId = lc.optString("id").toIntOrNull() ?: lc.optInt("id")
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
        iconUrl = mihomoIcon(lc.optString("icon")),
        id = lcId,
    )
}

internal fun hsrRelics(arr: JSONArray?): List<EnkaArtifact> = buildList {
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
        // relic.type 1~6 = 머리/핸드/바디/신발/차원 구체/연결 매듭 (int 또는 string 대응)
        val slotType = r.optInt("type", 0).takeIf { it in 1..6 } ?: r.optString("type").toIntOrNull() ?: (i + 1)
        add(
            EnkaArtifact(
                slot = hsrSlots.getOrElse(slotType - 1) { "유물" },
                setName = r.optString("set_name"),
                level = r.optInt("level"),
                main = main,
                subs = subs,
                iconUrl = mihomoIcon(r.optString("icon")),
            ),
        )
    }
}

/** HoYoLAB HSR base_type(int) → 운명의 길 KR. */
internal fun hsrPathKo(t: Int): String = when (t) {
    1 -> "파멸"; 2 -> "수렵"; 3 -> "지식"; 4 -> "화합"
    5 -> "공허"; 6 -> "보존"; 7 -> "풍요"; 8 -> "기억"
    9 -> "환락"
    else -> ""
}

/** 스타레일 전투속성 영문 → 한글. Yatta(대문자) + HoYoLAB(소문자, quantum 등) 모두 지원. */
internal fun hsrElementKo(e: String): String = when (e.lowercase()) {
    "fire" -> "화염"
    "ice" -> "얼음"
    "thunder", "lightning" -> "번개"
    "wind" -> "바람"
    "physical" -> "물리"
    "quantum" -> "양자"
    "imaginary" -> "허수"
    else -> ""
}
