package com.gatcha.log.data.api

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

// [EnkaApi.cleanName] 이 쓰는 정규식 — 파일 레벨에서 한 번만 컴파일한다.
// 예전엔 함수 안에 있어서, 메타 맵을 만들 때 **항목 수만큼**(수백~수천) 두 개를 다시 컴파일했다.

// 젠레스 응답 파서 — HoYoLAB avatar/info(응답의 라벨을 쓴다, property_map 없음). `EnkaApi.kt` 에서 나눴다(27.52.0).

internal fun zzzCrit(name: String): Boolean = name.contains("치명") || name.contains("CRIT", ignoreCase = true)

/** ZZZ element_type(int) → KR 속성. (200 물리·201 화염·202/206 얼음·203 전기·204 바람·205/207 에테르) */
/**
 * ZZZ element_type(int) → KR 속성.
 *
 * ⚠️ **신규 캐릭터가 나오면 여기 없는 번호가 들어온다.** 그러면 속성이 빈 문자열이 되어
 * 히어로의 속성 pill·색·연출이 통째로 빠진다(2026-09-08 레미엘 제보). 모르는 값은
 * 로그로 남겨 번호를 확인할 수 있게 한다 — "GatchaEnka: ZZZ 미지 속성" 으로 검색.
 */
internal fun zzzElementKo(type: Int): String = when (type) {
    200 -> "물리"
    201 -> "화염"
    202, 206 -> "얼음"
    203 -> "전기"
    204 -> "바람"
    205, 207 -> "에테르"
    else -> {
        if (type != 0) println("GatchaEnka: ZZZ 미지 속성 element_type=$type")
        ""
    }
}

/**
 * 공허 사냥꾼 셋의 **특수 속성**. 이름으로 직접 준다.
 *
 * 이 셋은 기본 속성에서 갈라져 나온 고유 속성을 쓰는데, 응답은 기본 속성만 주거나
 * (레미엘처럼) 아예 빈 값을 준다. 빈 값이 오면 색·pill·연출이 통째로 빠져
 * 캐릭터 하나만 망가진 화면이 된다.
 *
 * - 미야비 — 얼음 계열의 **서리**
 * - 엽빛나 — 물리 계열의 **서슬**
 * - 레미엘 — **루멘**. 에테르가 아니라 광선속(빛의 양)의 단위다.
 */
internal fun zzzElementByName(name: String): String = when (name) {
    "미야비" -> "서리"
    "엽빛나" -> "서슬"
    "레미엘" -> "루멘"
    else -> ""
}

/** ZZZ avatar_profession(int) → KR 직업. (1 강공·2 격파·3 이상·4 지원·5 방어) 미상=빈 문자열. */
internal fun zzzSpecialtyKo(t: Int): String = when (t) {
    1 -> "강공"
    2 -> "격파"
    3 -> "이상"
    4 -> "지원"
    5 -> "방어"
    else -> ""
}

/** ZZZ 스탯명 영문→KR(응답이 계정 언어라 영문일 수 있음). property_map 부재 보완. 미매칭은 원문 유지. */
internal fun zzzKrStat(en: String): String = when (en.trim()) {
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

internal fun zzzChar(o: JSONObject): EnkaChar {
    val id = o.optInt("id")
    return EnkaChar(
        id = id,
        name = o.optString("name_mi18n").ifBlank { "#$id" },
        level = o.optInt("level"),
        rank = o.optInt("rank"), // 마인드스케이프(시너지)
        rarity = if (o.optString("rarity") == "S") 5 else 4, // S→5★ / A→4★ (색·필터 호환)
        iconUrl = o.optString("role_square_url").ifBlank { o.optString("group_icon_path") }.takeIf { it.startsWith("http") },
        // 특수 속성이 먼저다 — 응답은 이 셋에게 기본 속성만 주거나 아무것도 주지 않는다.
        element = zzzElementByName(o.optString("name_mi18n").ifBlank { o.optString("name") }).ifBlank {
            zzzElementKo(o.optInt("element_type")).ifBlank {
                // 일부 응답은 element_type 대신 이름을 직접 준다.
                val alt = o.optJSONObject("element")?.optString("name").orEmpty()
                if (alt.isBlank()) {
                    println("GatchaEnka: ZZZ 속성 없음 name=${o.optString("name_mi18n")} keys=${o.keys().asSequence().joinToString(",")}")
                }
                alt
            }
        },
        specialty = zzzSpecialtyKo(o.optInt("avatar_profession").takeIf { it != 0 } ?: o.optInt("profession")),
        // 진영 — 응답 키가 버전에 따라 갈려 셋을 순서대로 본다.
        camp = o.optString("camp_name_mi18n")
            .ifBlank { o.optString("camp_name") }
            .ifBlank { o.optJSONObject("camp")?.optString("name").orEmpty() },
        detailed = true,
        stats = zzzStats(o.optJSONArray("properties")),
        weapon = zzzWeapon(o.optJSONObject("weapon")),
        artifacts = zzzDiscs(o.optJSONArray("equip")),
        sets = zzzSets(o.optJSONArray("equip")),
    )
}

/**
 * ZZZ 드라이브 디스크 equip_suit{name, own, desc1/2} → 세트 효과(2/4).
 * 미발동 4세트(2~3개 장착)도 active=false 로 함께 노출. 발동 효과가 1개 이상인 디스크만.
 */
internal fun zzzSets(arr: JSONArray?): List<EnkaSet> = buildList {
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
            suit.optString("desc1").takeIf { it.isNotBlank() }?.let { add(EnkaSetEffect(2, EnkaApi.cleanName(it), own >= 2)) }
            suit.optString("desc2").takeIf { it.isNotBlank() }?.let { add(EnkaSetEffect(4, EnkaApi.cleanName(it), own >= 4)) }
        }
        if (eff.any { it.active }) seen[sid] = EnkaSet(name, own, eff)
    }
    addAll(seen.values)
}

/** ZZZ 패널 properties[] {property_name, final} → 핵심 스탯. */
internal fun zzzStats(props: JSONArray?): List<EnkaStatLine> = buildList {
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

/** W-엔진 — main_properties[0]=기초 공격력, properties[0]=상위 스탯, star=페이즈. */
internal fun zzzWeapon(w: JSONObject?): EnkaWeapon? {
    w ?: return null
    fun line(arr: JSONArray?) = arr?.optJSONObject(0)?.let {
        val n = zzzKrStat(it.optString("property_name"))
        EnkaStatLine(n, hsrFmt(it.optString("base")), zzzCrit(n))
    }
    return EnkaWeapon(
        w.optString("name"), w.optInt("level"), w.optInt("star"),
        line(w.optJSONArray("main_properties")), line(w.optJSONArray("properties")),
        id = w.optInt("id"),
        iconUrl = hoyoIcon(w.optString("icon")),
        // 젠레스는 도감([NanokaApi])이 지원하지 않는다. 응답의 엔진 특성이 유일한 출처다.
        // ⚠️ 게임 텍스트라 `<color=...>` 같은 마크업이 그대로 들어 있다 — 도감 문장에 쓰던
        // 같은 함수로 걷어낸다. 안 걷으면 화면에 태그가 그대로 보인다.
        traitName = NanokaApi.stripMarkup(w.optString("talent_title")),
        traitDesc = NanokaApi.stripMarkup(w.optString("talent_content")),
    )
}

/** 드라이브 디스크 — equipment_type(슬롯 1~6)·main_properties[0]·properties[](부옵션)·equip_suit.name(세트). */
internal fun zzzDiscs(arr: JSONArray?): List<EnkaArtifact> = buildList {
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
                iconUrl = e.optString("icon").takeIf { it.startsWith("http") },
            ),
        )
    }
}
